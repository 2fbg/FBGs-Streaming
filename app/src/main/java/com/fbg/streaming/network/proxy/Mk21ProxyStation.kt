package com.fbg.streaming.network.proxy

import android.app.ActivityManager
import android.content.Context
import android.util.Base64
import android.util.LruCache
import kotlinx.coroutines.*
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class Mk21ProxyStation(
    context: Context,
    private val dnsResolver: okhttp3.Dns
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    var localPort: Int = 0
        private set

    // Token criptográfico por sessão para restringir acesso ao proxy local exclusivamente ao player do app
    private var sessionToken: String = generateSessionToken()

    // Ajusta o cache em RAM automaticamente (12 MB em TV Box Low-RAM, 28 MB em aparelhos padrão)
    private val maxCacheBytes: Int = run {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am?.isLowRamDevice == true) 12 * 1024 * 1024 else 28 * 1024 * 1024
    }

    private val segmentCache = object : LruCache<String, ByteArray>(maxCacheBytes) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    private val activePrefetches = ConcurrentHashMap.newKeySet<String>()

    // Cliente HTTP/2 + HTTP/1.1 com pool de conexões persistentes para evitar handshakes TLS repetidos
    private val upstreamClient: OkHttpClient = OkHttpClient.Builder()
        .dns(dnsResolver)
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectionPool(ConnectionPool(12, 5, TimeUnit.MINUTES))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun start(): Int {
        if (isRunning.compareAndSet(false, true)) {
            sessionToken = generateSessionToken()
            serverSocket = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
            localPort = serverSocket?.localPort ?: 0
            scope.launch { acceptLoop() }
        }
        return localPort
    }

    fun buildProxyUrl(remoteUrl: String): String {
        if (localPort == 0) start()
        val encoded = Base64.encodeToString(
            remoteUrl.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
        val encodedToken = URLEncoder.encode(sessionToken, "UTF-8")
        return "http://127.0.0.1:$localPort/?t=$encodedToken&u=$encoded"
    }

    internal fun currentSessionToken(): String = sessionToken

    internal fun isAuthorizedRequest(pathAndQuery: String, headerToken: String? = null): Boolean {
        val candidate = extractQueryParam(pathAndQuery, "t") ?: headerToken ?: return false
        return MessageDigest.isEqual(
            candidate.toByteArray(Charsets.UTF_8),
            sessionToken.toByteArray(Charsets.UTF_8)
        )
    }

    internal fun isAllowedTargetUrl(rawUrl: String): Boolean {
        val uri = try {
            URI(rawUrl.trim())
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        // Bloqueia recursão direta para o próprio socket do proxy local
        val port = if (uri.port > 0) uri.port else if (scheme == "https") 443 else 80
        if ((host == "127.0.0.1" || host == "localhost" || host == "::1") && port == localPort) {
            return false
        }
        return true
    }

    private suspend fun acceptLoop() {
        while (isRunning.get()) {
            val clientSocket = try {
                serverSocket?.accept()
            } catch (_: Exception) {
                null
            } ?: continue

            scope.launch { handleClient(clientSocket) }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 15_000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val pathAndQuery = parts[1]

            // Consome cabeçalhos da requisição local e captura eventual X-MK21-Token
            var headerToken: String? = null
            var line = reader.readLine()
            while (!line.isNullOrEmpty()) {
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val headerName = line.substring(0, colonIdx).trim()
                    if (headerName.equals("X-MK21-Token", ignoreCase = true)) {
                        headerToken = line.substring(colonIdx + 1).trim()
                    }
                }
                line = reader.readLine()
            }

            val output = client.getOutputStream()

            if (method != "GET" && method != "HEAD") {
                writeErrorResponse(output, 405, "Method Not Allowed")
                return
            }

            if (!isAuthorizedRequest(pathAndQuery, headerToken)) {
                writeErrorResponse(output, 403, "Forbidden")
                return
            }

            val encodedUrl = extractQueryParam(pathAndQuery, "u") ?: run {
                writeErrorResponse(output, 400, "Bad Request")
                return
            }
            val targetUrl = try {
                String(
                    Base64.decode(encodedUrl, Base64.URL_SAFE or Base64.NO_WRAP),
                    Charsets.UTF_8
                )
            } catch (_: Exception) {
                writeErrorResponse(output, 400, "Bad Request")
                return
            }

            if (!isAllowedTargetUrl(targetUrl)) {
                writeErrorResponse(output, 403, "Forbidden")
                return
            }

            // 1. Se for um segmento .ts já pré-carregado na RAM, entrega imediatamente (0 ms de rede)
            segmentCache.get(targetUrl)?.let { cachedBytes ->
                writeHttpHeaders(output, "video/mp2t", cachedBytes.size.toLong())
                if (method != "HEAD") {
                    output.write(cachedBytes)
                }
                output.flush()
                return
            }

            // 2. Busca no servidor remoto com política de Auto-Retry transparente
            val response = executeWithRetry(targetUrl, maxAttempts = 3) ?: run {
                writeErrorResponse(output, 502, "Bad Gateway")
                return
            }

            response.use { res ->
                val rawContentType = res.header("Content-Type") ?: guessContentType(targetUrl)
                val contentType = rawContentType.replace("\r", "").replace("\n", "")
                val body = res.body ?: return

                if (targetUrl.contains(".m3u8", ignoreCase = true) ||
                    contentType.contains("mpegurl", ignoreCase = true)
                ) {
                    // Reescreve a playlist HLS (.m3u8) para que todos os segmentos passem pelo Proxy Local autenticado
                    val manifestText = body.string()
                    val rewritten = rewriteHlsManifestAndTriggerPrefetch(targetUrl, manifestText)
                    val bytes = rewritten.toByteArray(Charsets.UTF_8)
                    writeHttpHeaders(output, "application/vnd.apple.mpegurl", bytes.size.toLong())
                    if (method != "HEAD") {
                        output.write(bytes)
                    }
                    output.flush()
                } else {
                    // Fluxo contínuo de segmento .ts ou stream direto com sanitização de pacotes MPEG-TS
                    writeHttpHeaders(output, contentType, -1L)
                    if (method != "HEAD") {
                        streamWithTsSanitizer(body.byteStream(), output, isTsStream = targetUrl.contains(".ts", true))
                    }
                }
            }
        }
    }

    private fun rewriteHlsManifestAndTriggerPrefetch(baseUrl: String, manifest: String): String {
        val baseUri = URI(baseUrl)
        val outLines = ArrayList<String>()
        val segmentsToPrefetch = ArrayList<String>()

        for (rawLine in manifest.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                outLines.add(rawLine)
            } else {
                val resolvedUrl = try {
                    baseUri.resolve(trimmed).toString()
                } catch (_: Exception) {
                    trimmed
                }
                if (segmentsToPrefetch.size < 2 && resolvedUrl.contains(".ts", ignoreCase = true)) {
                    segmentsToPrefetch.add(resolvedUrl)
                }
                outLines.add(buildProxyUrl(resolvedUrl))
            }
        }

        // Dispara pre-fetch em background dos próximos segmentos .ts para o RingBuffer/LruCache
        for (segUrl in segmentsToPrefetch) {
            triggerSegmentPrefetch(segUrl)
        }

        return outLines.joinToString("\n")
    }

    private fun triggerSegmentPrefetch(segmentUrl: String) {
        if (!isAllowedTargetUrl(segmentUrl)) return
        if (segmentCache.get(segmentUrl) != null) return
        if (!activePrefetches.add(segmentUrl)) return

        scope.launch {
            try {
                executeWithRetry(segmentUrl, maxAttempts = 2)?.use { res ->
                    if (res.isSuccessful) {
                        val bytes = res.body?.bytes()
                        if (bytes != null && bytes.size <= 6 * 1024 * 1024) {
                            segmentCache.put(segmentUrl, sanitizeTsByteArray(bytes))
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                activePrefetches.remove(segmentUrl)
            }
        }
    }

    /**
     * Sanitizador MPEG-TS inspirado no TsFileParser nativo:
     * Garante alinhamento no byte de sincronismo 0x47 (pacotes de 188 bytes) para evitar travamentos no decoder.
     */
    private fun sanitizeTsByteArray(input: ByteArray): ByteArray {
        if (input.size < 188) return input
        var firstSync = -1
        for (i in 0 until minOf(input.size - 188, 564)) {
            if (input[i] == 0x47.toByte() && input[i + 188] == 0x47.toByte()) {
                firstSync = i
                break
            }
        }
        return if (firstSync > 0) input.copyOfRange(firstSync, input.size) else input
    }

    private fun streamWithTsSanitizer(input: InputStream, output: OutputStream, isTsStream: Boolean) {
        val buffer = ByteArray(188 * 64) // Múltiplo exato de 188 bytes (12.032 bytes)
        var aligned = !isTsStream

        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            if (!aligned && read >= 376) {
                var offset = 0
                for (i in 0 until read - 188) {
                    if (buffer[i] == 0x47.toByte() && buffer[i + 188] == 0x47.toByte()) {
                        offset = i
                        aligned = true
                        break
                    }
                }
                output.write(buffer, offset, read - offset)
            } else {
                output.write(buffer, 0, read)
            }
            output.flush()
        }
    }

    private suspend fun executeWithRetry(url: String, maxAttempts: Int): okhttp3.Response? {
        var attempt = 0
        var delayMs = 250L
        while (attempt < maxAttempts) {
            attempt++
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "MK21-MultiServidor/2.1 (Android; ExoPlayer)")
                    .header("Accept", "*/*")
                    .build()
                val res = upstreamClient.newCall(req).execute()
                if (res.isSuccessful) return res
                res.close()
            } catch (_: Exception) {
            }
            if (attempt < maxAttempts) delay(delayMs)
            delayMs *= 2
        }
        return null
    }

    private fun writeHttpHeaders(output: OutputStream, contentType: String, contentLength: Long) {
        val headers = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: $contentType\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Connection: close\r\n")
            if (contentLength >= 0) {
                append("Content-Length: $contentLength\r\n")
            }
            append("\r\n")
        }
        output.write(headers.toByteArray(Charsets.UTF_8))
    }

    private fun writeErrorResponse(output: OutputStream, statusCode: Int, reason: String) {
        val bodyBytes = "$statusCode $reason".toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $statusCode $reason\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: ${bodyBytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(bodyBytes)
        output.flush()
    }

    private fun extractQueryParam(pathAndQuery: String, key: String): String? {
        val qIdx = pathAndQuery.indexOf('?')
        if (qIdx == -1) return null
        val query = pathAndQuery.substring(qIdx + 1)
        for (pair in query.split("&")) {
            val kv = pair.split("=", limit = 2)
            if (kv.size == 2 && kv[0] == key) {
                return try {
                    URLDecoder.decode(kv[1], "UTF-8")
                } catch (_: Exception) {
                    null
                }
            }
        }
        return null
    }

    private fun guessContentType(url: String): String = when {
        url.contains(".m3u8", true) -> "application/vnd.apple.mpegurl"
        url.contains(".mp4", true) -> "video/mp4"
        else -> "video/mp2t"
    }

    private fun generateSessionToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        localPort = 0
        segmentCache.evictAll()
        scope.cancel()
    }
}