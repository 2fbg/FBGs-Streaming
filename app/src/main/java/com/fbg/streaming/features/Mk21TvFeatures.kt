package com.fbg.streaming.features

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Locale

/**
* 1. Busca por Voz Nativa para Controle Remoto Android TV (Leanback) e Mobile
*/
class VoiceSearchManager(private val context: Context) {
   private var recognizer: SpeechRecognizer? = null
   private val _queryFlow = MutableStateFlow("")
   val queryFlow: StateFlow<String> = _queryFlow

   private val _isListening = MutableStateFlow(false)
   val isListening: StateFlow<Boolean> = _isListening

   fun startVoiceSearch(locale: Locale = Locale("pt", "BR")) {
       if (!SpeechRecognizer.isRecognitionAvailable(context)) return
       recognizer?.destroy()
       recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
           setRecognitionListener(object : RecognitionListener {
               override fun onReadyForSpeech(params: Bundle?) { _isListening.value = true }
               override fun onBeginningOfSpeech() {}
               override fun onRmsChanged(rmsdB: Float) {}
               override fun onBufferReceived(buffer: ByteArray?) {}
               override fun onEndOfSpeech() { _isListening.value = false }
               override fun onError(error: Int) { _isListening.value = false }
               override fun onResults(results: Bundle?) {
                   _isListening.value = false
                   val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                   if (!matches.isNullOrEmpty()) {
                       _queryFlow.value = matches.first()
                   }
               }
               override fun onPartialResults(partialResults: Bundle?) {
                   val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                   if (!partial.isNullOrEmpty()) {
                       _queryFlow.value = partial.first()
                   }
               }
               override fun onEvent(eventType: Int, params: Bundle?) {}
           })
       }

       val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
           putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
           putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
           putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
       }
       recognizer?.startListening(intent)
   }

   fun release() {
       recognizer?.destroy()
       recognizer = null
   }
}

/**
* 2. Controle Parental e Filtro de Categorias Adultas / Modo Kids com Hash SHA-256
*/
class ParentalControlManager(context: Context) {
   private val prefs = context.getSharedPreferences("mk21_parental_prefs", Context.MODE_PRIVATE)
   private val adultKeywords = listOf("adult", "xxx", "+18", "18+", "erotico", "erótico", "porn")

   fun isPinConfigured(): Boolean = prefs.contains(KEY_PIN_HASH)

   fun setPin(newPin: String) {
       prefs.edit().putString(KEY_PIN_HASH, hashPin(newPin)).apply()
   }

   fun verifyPin(inputPin: String): Boolean {
       val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
       return stored == hashPin(inputPin)
   }

   fun isRestrictedCategory(categoryName: String): Boolean {
       val normalized = categoryName.lowercase(Locale.ROOT)
       return adultKeywords.any { normalized.contains(it) }
   }

   private fun hashPin(pin: String): String {
       val digest = MessageDigest.getInstance("SHA-256")
       val bytes = digest.digest(("mk21_salt_$pin").toByteArray(Charsets.UTF_8))
       return bytes.joinToString("") { "%02x".format(it) }
   }

   companion object {
       private const val KEY_PIN_HASH = "pin_sha256"
   }
}

/**
* 3. Pareamento Rápido via QR Code na Smart TV / TV Box:
* Permite escanear o QR Code com o celular e enviar a lista M3U ou login Xtream sem usar o controle remoto.
*/
class TvQrPairServer(
   private val onCredentialsReceived: (name: String, m3uOrServerUrl: String, username: String?, password: String?) -> Unit
) {
   private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
   private var serverSocket: ServerSocket? = null

   fun startPairingServer(): String {
       val socket = ServerSocket(0)
       serverSocket = socket
       val port = socket.localPort
       val localIp = getLocalIpv4Address() ?: "127.0.0.1"

       scope.launch {
           while (socket.isClosed.not()) {
               val client = runCatching { socket.accept() }.getOrNull() ?: break
               client.use { s ->
                   val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                   val reqLine = reader.readLine() ?: return@use
                   if (reqLine.startsWith("GET /submit?")) {
                       val query = reqLine.substringAfter("GET /submit?").substringBefore(" ")
                       val params = query.split("&").associate {
                           val kv = it.split("=", limit = 2)
                           kv[0] to URLDecoder.decode(kv.getOrElse(1) { "" }, "UTF-8")
                       }
                       onCredentialsReceived(
                           params["name"].orEmpty().ifBlank { "Lista MK21" },
                           params["url"].orEmpty(),
                           params["user"]?.takeIf { it.isNotBlank() },
                           params["pass"]?.takeIf { it.isNotBlank() }
                       )
                       val okHtml = "<html><body style='background:#121212;color:#4CAF50;font-family:sans-serif;text-align:center;padding:40px;'><h2>Lista enviada para a TV com sucesso!</h2></body></html>"
                       s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n\r\n$okHtml".toByteArray())
                   } else {
                       val formHtml = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                           "<title>MK21 TV Setup</title>" +
                           "<style>body{background:#0f172a;color:#f8fafc;font-family:sans-serif;padding:20px}input,button{width:100%;padding:12px;margin:8px 0;border-radius:8px;border:1px solid #334155;background:#1e293b;color:#fff}button{background:#2563eb;font-weight:bold;border:none}</style>" +
                           "</head><body><h2>Configurar BGs Streaming (MK21) na TV</h2>" +
                           "<form action='/submit' method='GET'>" +
                           "<input name='name' placeholder='Nome da Lista (ex: Principal)' required/>" +
                           "<input name='url' placeholder='URL M3U8 ou Servidor Xtream (http://...)' required/>" +
                           "<input name='user' placeholder='Usuário Xtream (opcional)'/>" +
                           "<input name='pass' type='password' placeholder='Senha Xtream (opcional)'/>" +
                           "<button type='submit'>Enviar para a TV</button>" +
                           "</form></body></html>"
                       s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n\r\n$formHtml".toByteArray())
                   }
               }
           }
       }
       return "http://$localIp:$port"
   }

   fun stop() {
       runCatching { serverSocket?.close() }
   }

   private fun getLocalIpv4Address(): String? {
       val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
       for (intf in interfaces) {
           for (addr in intf.inetAddresses) {
               if (!addr.isLoopbackAddress && addr is Inet4Address) {
                   return addr.hostAddress
               }
           }
       }
       return null
   }
}