package com.example.data.service

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Base64
import android.util.Log
import com.example.data.model.PlaylistItem
import com.example.network.StreamHeadersRegistry
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LocalHttpServer {
    companion object {
        private const val TAG = "LocalHttpServer"
        private const val DEFAULT_IPTV_UA = "VLC/3.0.20 LibVLC/3.0.20"
        private const val MAX_PLAYLIST_BYTES = 4 * 1024 * 1024 // 4 MB
        private val EXT_URI_ATTR_REGEX = Regex("""URI="([^"]+)"""")
    }

    private var serverSocket: ServerSocket? = null
    private var executorService: ExecutorService? = null
    private val streamRevisionCounter = AtomicInteger(1)

    private val relayClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Volatile
    var activeItem: PlaylistItem? = null
        set(value) {
            val changed = field?.url != value?.url
            field = value
            if (changed) {
                streamRevisionCounter.incrementAndGet()
            }
        }

    var serverPort: Int = 8585
    var isRunning: Boolean = false
        private set

    @Volatile
    private var sessionToken: String = generateSessionToken()

    // Remote sync control states
    @Volatile
    var remoteIsPlaying: Boolean = true
    @Volatile
    var remoteSeekRequest: Long = -1L // in milliseconds
    @Volatile
    var remoteVolume: Float = 0.8f
    @Volatile
    var tvCurrentTimeSeconds: Float = 0.0f

    fun currentStreamRevision(): Int = streamRevisionCounter.get()

    @Synchronized
    fun startServer(context: Context, item: PlaylistItem): String? {
        activeItem = item
        if (isRunning && serverSocket != null && serverSocket?.isClosed == false) {
            return getCastUrl(context)
        }

        sessionToken = generateSessionToken()

        try {
            val socket = ServerSocket(serverPort)
            serverSocket = socket
            if (serverPort == 0) {
                serverPort = socket.localPort
            }
            startAcceptLoop()
            return getCastUrl(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting server on primary port $serverPort, trying fallback", e)
            try {
                serverPort = 9015
                val socket = ServerSocket(serverPort)
                serverSocket = socket
                startAcceptLoop()
                return getCastUrl(context)
            } catch (ex: Exception) {
                try {
                    val socket = ServerSocket(0)
                    serverSocket = socket
                    serverPort = socket.localPort
                    startAcceptLoop()
                    return getCastUrl(context)
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed server fallback setup", e2)
                }
            }
        }
        return null
    }

    private fun startAcceptLoop() {
        isRunning = true
        // Cached thread pool evita starvation quando múltiplos sockets de stream ficam abertos junto com polling /api/status
        executorService = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "mk21-cast-http").apply { isDaemon = true }
        }

        Thread({
            while (isRunning) {
                try {
                    val socket = serverSocket?.accept() ?: break
                    // O servidor escuta em todas as interfaces (a TV precisa alcançá-lo): descarta pares
                    // que não são da rede local/loopback/VPN antes de qualquer leitura.
                    if (!CastNetworkPolicy.isLanPeer(socket.inetAddress)) {
                        Log.w(TAG, "Conexão recusada de par fora da rede local: ${socket.inetAddress?.hostAddress}")
                        try { socket.close() } catch (_: Exception) {}
                        continue
                    }
                    executorService?.execute {
                        handleConnection(socket)
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(TAG, "Error accepting socket connection", e)
                    }
                    break
                }
            }
        }, "mk21-cast-accept").start()
        Log.d(TAG, "Cast HTTP server started on port $serverPort")
    }

    internal fun currentSessionToken(): String = sessionToken

    internal fun isAuthorized(pathAndQuery: String): Boolean {
        val candidate = extractQueryParam(pathAndQuery, "t") ?: return false
        return MessageDigest.isEqual(
            candidate.toByteArray(Charsets.UTF_8),
            sessionToken.toByteArray(Charsets.UTF_8)
        )
    }

    internal fun isAllowedUpstreamUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
        if (scheme != "http" && scheme != "https") return false

        val host = uri.host?.lowercase(Locale.US)?.trim() ?: return false
        if (host.isEmpty()) return false
        // Loopback, 0.0.0.0/8, link-local/metadata, IPv6 loopback/link-local e formas ofuscadas de IP.
        if (CastNetworkPolicy.isBlockedUpstreamHost(host)) return false

        // Impede recursão apontando para o próprio servidor local
        val port = if (uri.port != -1) uri.port else if (scheme == "https") 443 else 80
        if (port == serverPort && isOwnLocalAddress(host)) return false

        return true
    }

    private fun isOwnLocalAddress(host: String): Boolean {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false
            while (interfaces.hasMoreElements()) {
                val addrs = interfaces.nextElement().inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr.hostAddress.equals(host, ignoreCase = true)) {
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun handleConnection(socket: Socket) {
        try {
            socket.soTimeout = 15_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val firstLine = reader.readLine() ?: return
            val requestHeaders = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine()
                if (line.isNullOrEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase(Locale.US)
                    val value = line.substring(colon + 1).trim()
                    requestHeaders[key] = value
                }
            }

            val parts = firstLine.split(" ")
            val method = parts.getOrNull(0)?.uppercase(Locale.US) ?: ""
            val path = parts.getOrNull(1) ?: "/"

            val outputStream = BufferedOutputStream(socket.getOutputStream(), 16 * 1024)

            if (method != "GET" && method != "HEAD") {
                sendTextResponse(outputStream, 405, "Method Not Allowed", "Method Not Allowed", method == "HEAD")
                return
            }

            if (!isAuthorized(path)) {
                sendTextResponse(
                    outputStream,
                    403,
                    "Forbidden",
                    "Forbidden: invalid or missing session token",
                    method == "HEAD"
                )
                return
            }

            val routePath = path.substringBefore('?')

            when {
                routePath == "/api/status" || routePath.endsWith("/api/status") -> {
                    serveStatusApi(path, method, outputStream)
                    return
                }
                routePath == "/stream" || routePath.startsWith("/stream.") -> {
                    val item = activeItem
                    if (item == null || item.url.isBlank()) {
                        sendTextResponse(outputStream, 404, "Not Found", "No active stream", method == "HEAD")
                        return
                    }
                    proxyStreamRequest(
                        targetUrl = item.url,
                        contentTypeHint = item.contentType,
                        method = method,
                        requestHeaders = requestHeaders,
                        outputStream = outputStream,
                        socket = socket
                    )
                    return
                }
                routePath == "/hls" -> {
                    val encodedTarget = extractQueryParam(path, "u")
                    val decodedTarget = encodedTarget?.let { decodeBase64Url(it) }
                    if (decodedTarget.isNullOrBlank()) {
                        sendTextResponse(outputStream, 400, "Bad Request", "Missing target", method == "HEAD")
                        return
                    }
                    proxyStreamRequest(
                        targetUrl = decodedTarget,
                        contentTypeHint = activeItem?.contentType ?: "LIVE",
                        method = method,
                        requestHeaders = requestHeaders,
                        outputStream = outputStream,
                        socket = socket
                    )
                    return
                }
                else -> {
                    serveWebReceiverPage(method, outputStream)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "HTTP socket session ended: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun serveStatusApi(path: String, method: String, outputStream: BufferedOutputStream) {
        val timeQuery = extractQueryParam(path, "time")
        if (!timeQuery.isNullOrEmpty()) {
            val actualTime = timeQuery.toFloatOrNull()
            if (actualTime != null && actualTime.isFinite() && actualTime >= 0f) {
                tvCurrentTimeSeconds = actualTime
            }
        }

        val json = """
            {
                "playing": $remoteIsPlaying,
                "seekTo": $remoteSeekRequest,
                "volume": $remoteVolume,
                "revision": ${currentStreamRevision()}
            }
        """.trimIndent()

        if (remoteSeekRequest >= 0) {
            remoteSeekRequest = -1L
        }

        val jsonBytes = json.toByteArray(Charsets.UTF_8)
        val responseHeader = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${jsonBytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Connection: close\r\n\r\n"

        outputStream.write(responseHeader.toByteArray(Charsets.UTF_8))
        if (method != "HEAD") {
            outputStream.write(jsonBytes)
        }
        outputStream.flush()
    }

    private fun proxyStreamRequest(
        targetUrl: String,
        contentTypeHint: String,
        method: String,
        requestHeaders: Map<String, String>,
        outputStream: BufferedOutputStream,
        socket: Socket
    ) {
        if (!isAllowedUpstreamUrl(targetUrl)) {
            sendTextResponse(outputStream, 403, "Forbidden", "Target URL blocked by policy", method == "HEAD")
            return
        }

        val registeredHeaders = StreamHeadersRegistry.get(targetUrl) ?: StreamHeadersRegistry.get(activeItem?.url)
        val userAgent = registeredHeaders?.userAgent ?: DEFAULT_IPTV_UA
        val referrer = registeredHeaders?.referrer
        val clientRange = requestHeaders["range"]

        fun buildRequest(httpMethod: String, extraRange: String? = clientRange): Request {
            val builder = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .header("Connection", "close")
            if (!referrer.isNullOrBlank()) {
                builder.header("Referer", referrer)
            }
            if (!extraRange.isNullOrBlank()) {
                builder.header("Range", extraRange)
            }
            if (httpMethod == "HEAD") {
                builder.head()
            } else {
                builder.get()
            }
            return builder.build()
        }

        val isHead = method == "HEAD"
        val isLive = contentTypeHint.equals("LIVE", ignoreCase = true)

        if (isHead) {
            // Muitos servidores IPTV Xtream recusam HEAD (405) ou travam; tentamos HEAD e fazemos fallback para resposta DLNA padrão
            var resolvedMime = inferMimeType(targetUrl, null, isLive)
            var contentLength = -1L
            var acceptRanges = !isLive
            try {
                relayClient.newCall(buildRequest("HEAD")).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resolvedMime = inferMimeType(resp.request.url.toString(), resp.header("Content-Type"), isLive)
                        contentLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                        val ar = resp.header("Accept-Ranges")
                        if (!ar.isNullOrBlank()) {
                            acceptRanges = !ar.equals("none", ignoreCase = true)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Upstream HEAD probe skipped/failed, returning DLNA defaults: ${e.message}")
            }

            val dlnaFeatures = if (acceptRanges && contentLength > 0) {
                "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
            } else {
                "DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
            }

            val headers = StringBuilder()
                .append("HTTP/1.1 200 OK\r\n")
                .append("Content-Type: ").append(resolvedMime).append("\r\n")
                .append("transferMode.dlna.org: Streaming\r\n")
                .append("contentFeatures.dlna.org: ").append(dlnaFeatures).append("\r\n")
                .append("Cache-Control: no-store\r\n")
                .append("Connection: close\r\n")
            if (acceptRanges) {
                headers.append("Accept-Ranges: bytes\r\n")
            }
            if (contentLength >= 0) {
                headers.append("Content-Length: ").append(contentLength).append("\r\n")
            }
            headers.append("\r\n")
            outputStream.write(headers.toString().toByteArray(Charsets.UTF_8))
            outputStream.flush()
            return
        }

        // Streaming GET: aumenta timeout de escrita para fluxos contínuos ao vivo
        try {
            socket.soTimeout = 0
        } catch (_: Exception) {
        }

        val call = relayClient.newCall(buildRequest("GET"))
        try {
            call.execute().use { response ->
                val code = response.code
                if (code !in 200..299) {
                    sendTextResponse(
                        outputStream,
                        502,
                        "Bad Gateway",
                        "Upstream returned HTTP $code",
                        false
                    )
                    return
                }

                val finalUrl = response.request.url.toString()
                val upstreamContentType = response.header("Content-Type")
                val body = response.body ?: run {
                    sendTextResponse(outputStream, 502, "Bad Gateway", "Empty upstream body", false)
                    return
                }

                val looksLikeHls = isHlsPlaylist(finalUrl, upstreamContentType)
                if (looksLikeHls) {
                    val rawBytes = readBoundedBytes(body.byteStream(), MAX_PLAYLIST_BYTES)
                    val playlistText = String(rawBytes, Charsets.UTF_8)
                    val outputBytes = if (playlistText.trimStart().startsWith("#EXTM3U")) {
                        rewriteHlsPlaylist(playlistText, finalUrl, sessionToken).toByteArray(Charsets.UTF_8)
                    } else {
                        rawBytes
                    }

                    val header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/vnd.apple.mpegurl\r\n" +
                        "Content-Length: ${outputBytes.size}\r\n" +
                        "transferMode.dlna.org: Streaming\r\n" +
                        "contentFeatures.dlna.org: DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000\r\n" +
                        "Cache-Control: no-store\r\n" +
                        "Connection: close\r\n\r\n"
                    outputStream.write(header.toByteArray(Charsets.UTF_8))
                    outputStream.write(outputBytes)
                    outputStream.flush()
                    return
                }

                val mimeType = inferMimeType(finalUrl, upstreamContentType, isLive)
                val statusReason = if (code == 206) "Partial Content" else "OK"
                val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                val contentRange = response.header("Content-Range")
                val supportsRanges = contentLength > 0 || !contentRange.isNullOrBlank()
                val dlnaFeatures = if (supportsRanges) {
                    "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
                } else {
                    "DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
                }

                val headerBuilder = StringBuilder()
                    .append("HTTP/1.1 ").append(code).append(' ').append(statusReason).append("\r\n")
                    .append("Content-Type: ").append(mimeType).append("\r\n")
                    .append("transferMode.dlna.org: Streaming\r\n")
                    .append("contentFeatures.dlna.org: ").append(dlnaFeatures).append("\r\n")
                    .append("Cache-Control: no-store\r\n")
                    .append("Connection: close\r\n")
                if (supportsRanges) {
                    headerBuilder.append("Accept-Ranges: bytes\r\n")
                }
                if (contentLength >= 0) {
                    headerBuilder.append("Content-Length: ").append(contentLength).append("\r\n")
                }
                if (!contentRange.isNullOrBlank()) {
                    headerBuilder.append("Content-Range: ").append(contentRange).append("\r\n")
                }
                headerBuilder.append("\r\n")

                outputStream.write(headerBuilder.toString().toByteArray(Charsets.UTF_8))
                outputStream.flush()

                val buffer = ByteArray(16 * 1024)
                body.byteStream().use { input ->
                    while (isRunning) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        outputStream.write(buffer, 0, read)
                        outputStream.flush()
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Stream relay closed: ${e.message}")
            try {
                call.cancel()
            } catch (_: Exception) {
            }
        }
    }

    internal fun rewriteHlsPlaylist(manifest: String, baseUrl: String, token: String): String {
        val baseHttpUrl = baseUrl.toHttpUrlOrNull() ?: return manifest
        val out = StringBuilder(manifest.length + 256)

        fun proxify(rawUri: String): String {
            val resolved = baseHttpUrl.resolve(rawUri.trim())?.toString() ?: return rawUri
            if (!isAllowedUpstreamUrl(resolved)) return rawUri
            val encoded = encodeBase64Url(resolved)
            return "/hls?u=$encoded&t=$token"
        }

        for (rawLine in manifest.lines()) {
            val line = rawLine.trimEnd('\r')
            when {
                line.isBlank() -> out.append('\n')
                line.startsWith("#") -> {
                    val rewritten = EXT_URI_ATTR_REGEX.replace(line) { match ->
                        val originalUri = match.groupValues[1]
                        """URI="${proxify(originalUri)}""""
                    }
                    out.append(rewritten).append('\n')
                }
                else -> {
                    out.append(proxify(line)).append('\n')
                }
            }
        }
        return out.toString()
    }

    private fun serveWebReceiverPage(method: String, outputStream: BufferedOutputStream) {
        val item = activeItem
        val html = if (item == null) {
            """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <title>MK21 Web Receiver</title>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { margin:0; background:#0b0b0e; color:#fff; font-family:system-ui,-apple-system,sans-serif; display:flex; flex-direction:column; align-items:center; justify-content:center; height:100vh; text-align:center; padding:24px; box-sizing:border-box; }
                    h1 { color:#ffbc0d; font-size:26px; margin:0 0 10px; }
                    p { color:#9ca3af; font-size:15px; max-width:460px; }
                </style>
            </head>
            <body>
                <h1>Nenhuma transmissão ativa</h1>
                <p>Selecione um canal, filme ou série no aplicativo MK21 e abra novamente este endereço.</p>
            </body>
            </html>
            """.trimIndent()
        } else {
            val safeName = escapeHtml(item.name)
            val escapedToken = escapeHtml(sessionToken)
            val ext = detectStreamExtension(item.url, item.contentType)
            val rev = currentStreamRevision()
            val localStreamPath = "/stream.$ext?t=$escapedToken&rev=$rev"
            val streamKind = when (ext) {
                "m3u8" -> "hls"
                "ts" -> "mpegts"
                else -> "native"
            }
            """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="utf-8">
                <title>MK21 TV Receiver — $safeName</title>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    * { box-sizing: border-box; }
                    html, body {
                        margin: 0; padding: 0; width: 100%; height: 100%;
                        background: #000; color: #fff;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        overflow: hidden;
                    }
                    .stage {
                        position: relative; width: 100vw; height: 100vh;
                        display: flex; align-items: center; justify-content: center;
                        background: #000;
                    }
                    video {
                        width: 100%; height: 100%;
                        object-fit: contain; background: #000;
                    }
                    .hud-top {
                        position: absolute; top: 0; left: 0; right: 0;
                        padding: 20px 28px;
                        background: linear-gradient(180deg, rgba(0,0,0,0.88) 0%, rgba(0,0,0,0) 100%);
                        display: flex; align-items: center; justify-content: space-between;
                        transition: opacity 0.35s ease;
                        z-index: 10;
                    }
                    .hud-bottom {
                        position: absolute; bottom: 0; left: 0; right: 0;
                        padding: 20px 28px;
                        background: linear-gradient(0deg, rgba(0,0,0,0.88) 0%, rgba(0,0,0,0) 100%);
                        display: flex; align-items: center; justify-content: space-between; gap: 12px;
                        transition: opacity 0.35s ease;
                        z-index: 10;
                    }
                    .hud-hidden { opacity: 0; pointer-events: none; }
                    .title-wrap { display: flex; align-items: center; gap: 12px; min-width: 0; }
                    .title {
                        font-size: 22px; font-weight: 800; color: #ffbc0d;
                        white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
                    }
                    .badge {
                        display: inline-flex; align-items: center; gap: 6px;
                        background: rgba(255,255,255,0.12); color: #fff;
                        font-size: 12px; font-weight: 700; padding: 5px 12px;
                        border-radius: 999px; flex-shrink: 0;
                    }
                    .dot { width: 8px; height: 8px; border-radius: 50%; background: #22c55e; }
                    .dot.warn { background: #f59e0b; }
                    .dot.err { background: #ef4444; }
                    .controls { display: flex; gap: 10px; flex-wrap: wrap; }
                    .btn {
                        background: rgba(255,255,255,0.14); color: #fff;
                        border: 1px solid rgba(255,255,255,0.22);
                        border-radius: 10px; padding: 10px 16px;
                        font-size: 14px; font-weight: 700; cursor: pointer;
                    }
                    .btn.primary { background: #ffbc0d; color: #000; border-color: #ffbc0d; }
                    .btn:focus { outline: 3px solid #ffbc0d; outline-offset: 2px; }
                    .center-overlay {
                        position: absolute; inset: 0;
                        display: flex; flex-direction: column; align-items: center; justify-content: center;
                        background: rgba(0,0,0,0.55); z-index: 8; text-align: center; padding: 24px;
                    }
                    .center-overlay.hidden { display: none; }
                    .spinner {
                        width: 44px; height: 44px; border-radius: 50%;
                        border: 4px solid rgba(255,188,13,0.22); border-top-color: #ffbc0d;
                        animation: spin 0.9s linear infinite; margin-bottom: 14px;
                    }
                    @keyframes spin { to { transform: rotate(360deg); } }
                    .status-msg { font-size: 16px; color: #f3f4f6; margin-bottom: 14px; }
                </style>
            </head>
            <body>
                <div class="stage" id="stage">
                    <video id="video" playsinline autoplay controls></video>

                    <div class="hud-top" id="hudTop">
                        <div class="title-wrap">
                            <span class="title">$safeName</span>
                        </div>
                        <div class="badge"><span class="dot warn" id="statusDot"></span><span id="statusText">Conectando...</span></div>
                    </div>

                    <div class="center-overlay" id="overlay">
                        <div class="spinner" id="spinner"></div>
                        <div class="status-msg" id="overlayMsg">Carregando transmissão na Smart TV...</div>
                        <button class="btn primary" id="playUnlockBtn" style="display:none">Iniciar Reprodução</button>
                    </div>

                    <div class="hud-bottom" id="hudBottom">
                        <div class="controls">
                            <button class="btn primary" id="btnPlayPause">Pausar</button>
                            <button class="btn" id="btnMute">Áudio</button>
                            <button class="btn" id="btnReload">Reconectar</button>
                            <button class="btn" id="btnFullscreen">Tela Cheia</button>
                        </div>
                        <div style="font-size:12px;color:#9ca3af;">MK21 Cast • Controle pelo App Ativo</div>
                    </div>
                </div>

                <script src="https://cdn.jsdelivr.net/npm/hls.js@1.5.15/dist/hls.min.js"></script>
                <script src="https://cdn.jsdelivr.net/npm/mpegts.js@1.7.3/dist/mpegts.min.js"></script>
                <script>
                    (function() {
                        var castToken = "$escapedToken";
                        var streamUrl = "$localStreamPath";
                        var streamKind = "$streamKind";
                        var activeRev = $rev;
                        var video = document.getElementById('video');
                        var overlay = document.getElementById('overlay');
                        var overlayMsg = document.getElementById('overlayMsg');
                        var spinner = document.getElementById('spinner');
                        var playUnlockBtn = document.getElementById('playUnlockBtn');
                        var statusDot = document.getElementById('statusDot');
                        var statusText = document.getElementById('statusText');
                        var hudTop = document.getElementById('hudTop');
                        var hudBottom = document.getElementById('hudBottom');
                        var btnPlayPause = document.getElementById('btnPlayPause');
                        var btnMute = document.getElementById('btnMute');
                        var btnReload = document.getElementById('btnReload');
                        var btnFullscreen = document.getElementById('btnFullscreen');

                        var hlsInstance = null;
                        var tsPlayer = null;
                        var retryCount = 0;
                        var retryTimer = null;
                        var hudTimer = null;

                        function setStatus(state, text) {
                            statusText.textContent = text;
                            statusDot.className = 'dot' + (state === 'ok' ? '' : (state === 'err' ? ' err' : ' warn'));
                        }

                        function showOverlay(msg, showSpinner, showPlayBtn) {
                            overlay.className = 'center-overlay';
                            overlayMsg.textContent = msg;
                            spinner.style.display = showSpinner ? 'block' : 'none';
                            playUnlockBtn.style.display = showPlayBtn ? 'inline-block' : 'none';
                        }

                        function hideOverlay() {
                            overlay.className = 'center-overlay hidden';
                        }

                        function showHud() {
                            hudTop.className = 'hud-top';
                            hudBottom.className = 'hud-bottom';
                            if (hudTimer) clearTimeout(hudTimer);
                            hudTimer = setTimeout(function() {
                                if (!video.paused) {
                                    hudTop.className = 'hud-top hud-hidden';
                                    hudBottom.className = 'hud-bottom hud-hidden';
                                }
                            }, 4500);
                        }

                        function cleanupPlayers() {
                            if (retryTimer) { clearTimeout(retryTimer); retryTimer = null; }
                            if (hlsInstance) { try { hlsInstance.destroy(); } catch (e) {} hlsInstance = null; }
                            if (tsPlayer) { try { tsPlayer.destroy(); } catch (e) {} tsPlayer = null; }
                        }

                        function scheduleReconnect(reason) {
                            var delayMs = Math.min(12000, 1500 * Math.pow(2, retryCount));
                            retryCount = Math.min(retryCount + 1, 4);
                            setStatus('warn', 'Reconectando...');
                            showOverlay(reason + ' Tentando novamente...', true, false);
                            retryTimer = setTimeout(function() { startPlayback(); }, delayMs);
                        }

                        function tryPlay() {
                            var p = video.play();
                            if (p && typeof p.then === 'function') {
                                p.then(function() {
                                    retryCount = 0;
                                    hideOverlay();
                                    setStatus('ok', 'Reproduzindo');
                                    btnPlayPause.textContent = 'Pausar';
                                }).catch(function() {
                                    // Navegadores de TV bloqueiam autoplay com som sem interação
                                    video.muted = true;
                                    btnMute.textContent = 'Ativar Som';
                                    var p2 = video.play();
                                    if (p2 && typeof p2.then === 'function') {
                                        p2.then(function() {
                                            hideOverlay();
                                            setStatus('ok', 'Sem áudio (clique em Ativar Som)');
                                        }).catch(function() {
                                            showOverlay('Pressione OK ou clique abaixo para iniciar na TV.', false, true);
                                        });
                                    }
                                });
                            }
                        }

                        function startPlayback() {
                            cleanupPlayers();
                            setStatus('warn', 'Carregando...');
                            showOverlay('Conectando ao fluxo de vídeo...', true, false);

                            if (streamKind === 'hls') {
                                if (window.Hls && Hls.isSupported()) {
                                    hlsInstance = new Hls({
                                        enableWorker: true,
                                        lowLatencyMode: false,
                                        maxBufferLength: 20,
                                        backBufferLength: 10
                                    });
                                    hlsInstance.loadSource(streamUrl);
                                    hlsInstance.attachMedia(video);
                                    hlsInstance.on(Hls.Events.MANIFEST_PARSED, function() { tryPlay(); });
                                    hlsInstance.on(Hls.Events.ERROR, function(evt, data) {
                                        if (data && data.fatal) {
                                            scheduleReconnect('Sinal HLS interrompido.');
                                        }
                                    });
                                    return;
                                }
                            } else if (streamKind === 'mpegts') {
                                if (window.mpegts && mpegts.isSupported()) {
                                    tsPlayer = mpegts.createPlayer({
                                        type: 'mpegts',
                                        isLive: true,
                                        url: streamUrl
                                    }, {
                                        enableWorker: false,
                                        liveBufferLatencyChasing: true,
                                        autoCleanupSourceBuffer: true
                                    });
                                    tsPlayer.attachMediaElement(video);
                                    tsPlayer.load();
                                    tsPlayer.on(mpegts.Events.ERROR, function() {
                                        scheduleReconnect('Fluxo ao vivo instável.');
                                    });
                                    tryPlay();
                                    return;
                                }
                            }

                            video.src = streamUrl;
                            video.load();
                            tryPlay();
                        }

                        video.addEventListener('playing', function() {
                            retryCount = 0;
                            hideOverlay();
                            setStatus('ok', 'Reproduzindo');
                            btnPlayPause.textContent = 'Pausar';
                            showHud();
                        });
                        video.addEventListener('waiting', function() {
                            setStatus('warn', 'Carregando buffer...');
                        });
                        video.addEventListener('pause', function() {
                            btnPlayPause.textContent = 'Reproduzir';
                            showHud();
                        });
                        video.addEventListener('error', function() {
                            scheduleReconnect('Não foi possível decodificar o sinal.');
                        });

                        playUnlockBtn.onclick = function() {
                            video.muted = false;
                            btnMute.textContent = 'Silenciar';
                            tryPlay();
                        };
                        btnPlayPause.onclick = function() {
                            if (video.paused) { tryPlay(); } else { video.pause(); }
                        };
                        btnMute.onclick = function() {
                            video.muted = !video.muted;
                            btnMute.textContent = video.muted ? 'Ativar Som' : 'Silenciar';
                        };
                        btnReload.onclick = function() {
                            retryCount = 0;
                            startPlayback();
                        };
                        btnFullscreen.onclick = function() {
                            var el = document.getElementById('stage');
                            if (document.fullscreenElement) {
                                document.exitFullscreen().catch(function() {});
                            } else if (el.requestFullscreen) {
                                el.requestFullscreen().catch(function() {});
                            } else if (video.webkitEnterFullscreen) {
                                try { video.webkitEnterFullscreen(); } catch (e) {}
                            }
                        };

                        document.addEventListener('mousemove', showHud);
                        document.addEventListener('keydown', function(e) {
                            showHud();
                            if (e.keyCode === 13 || e.keyCode === 32) {
                                if (video.paused) { tryPlay(); } else { video.pause(); }
                            }
                        });

                        // Sincronização em tempo real com o aplicativo Android
                        setInterval(function() {
                            var currentPos = isFinite(video.currentTime) ? video.currentTime : 0;
                            fetch('/api/status?t=' + encodeURIComponent(castToken) + '&time=' + encodeURIComponent(currentPos))
                                .then(function(res) { return res.json(); })
                                .then(function(data) {
                                    if (!data) return;
                                    if (typeof data.revision === 'number' && data.revision !== activeRev) {
                                        window.location.reload();
                                        return;
                                    }
                                    if (data.playing && video.paused) {
                                        tryPlay();
                                    } else if (!data.playing && !video.paused) {
                                        video.pause();
                                    }
                                    if (typeof data.volume === 'number' && isFinite(data.volume)) {
                                        video.volume = Math.max(0, Math.min(1, data.volume));
                                    }
                                    if (data.seekTo && data.seekTo >= 0 && isFinite(video.duration)) {
                                        video.currentTime = data.seekTo / 1000.0;
                                    }
                                })
                                .catch(function() {});
                        }, 1000);

                        startPlayback();
                    })();
                </script>
            </body>
            </html>
            """.trimIndent()
        }

        val htmlBytes = html.toByteArray(Charsets.UTF_8)
        val csp = "default-src 'self' https://cdn.jsdelivr.net; " +
            "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
            "style-src 'self' 'unsafe-inline'; " +
            "media-src 'self' blob: data:; connect-src 'self' blob:; img-src 'self' data: blob:; " +
            "frame-ancestors 'none'; base-uri 'self'"
        val responseHeader = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${htmlBytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Referrer-Policy: no-referrer\r\n" +
            "Content-Security-Policy: $csp\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Connection: close\r\n\r\n"

        outputStream.write(responseHeader.toByteArray(Charsets.UTF_8))
        if (method != "HEAD") {
            outputStream.write(htmlBytes)
        }
        outputStream.flush()
    }

    private fun sendTextResponse(
        outputStream: BufferedOutputStream,
        statusCode: Int,
        reason: String,
        body: String,
        isHead: Boolean
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $statusCode $reason\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Connection: close\r\n\r\n"
        outputStream.write(header.toByteArray(Charsets.UTF_8))
        if (!isHead) {
            outputStream.write(bytes)
        }
        outputStream.flush()
    }

    @Synchronized
    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ServerSocket", e)
        }
        try {
            executorService?.shutdownNow()
            executorService = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing executors", e)
        }
        Log.d(TAG, "Server stopped successfully")
    }

    fun getLocalIpAddress(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                @Suppress("DEPRECATION")
                val ipAddress = wifiManager.connectionInfo.ipAddress
                if (ipAddress != 0) {
                    return String.format(
                        Locale.US,
                        "%d.%d.%d.%d",
                        ipAddress and 0xff,
                        ipAddress shr 8 and 0xff,
                        ipAddress shr 16 and 0xff,
                        ipAddress shr 24 and 0xff
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wifi IP fetching error", e)
        }
        try {
            var fallbackSiteLocal: String? = null
            var fallbackAnyIpv4: String? = null
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (!intf.isUp || intf.isLoopback) continue
                val name = intf.name.lowercase(Locale.US)
                val isLanInterface = name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("ap")
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddr = addr.hostAddress ?: continue
                        if (isLanInterface && addr.isSiteLocalAddress) {
                            return hostAddr
                        }
                        if (addr.isSiteLocalAddress && fallbackSiteLocal == null) {
                            fallbackSiteLocal = hostAddr
                        }
                        if (fallbackAnyIpv4 == null) {
                            fallbackAnyIpv4 = hostAddr
                        }
                    }
                }
            }
            if (fallbackSiteLocal != null) return fallbackSiteLocal
            if (fallbackAnyIpv4 != null) return fallbackAnyIpv4
        } catch (e: Exception) {
            Log.e(TAG, "Network adapter IP fetching error", e)
        }
        return "127.0.0.1"
    }

    fun getCastUrl(context: Context): String {
        return "http://${getLocalIpAddress(context)}:$serverPort/?t=$sessionToken"
    }

    fun getStreamProxyUrl(context: Context, item: PlaylistItem): String {
        val ext = detectStreamExtension(item.url, item.contentType)
        return "http://${getLocalIpAddress(context)}:$serverPort/stream.$ext?t=$sessionToken&rev=${currentStreamRevision()}"
    }

    private fun detectStreamExtension(url: String, contentType: String): String {
        val cleanPath = url.substringBefore('?').substringBefore('#').lowercase(Locale.US)
        return when {
            cleanPath.endsWith(".m3u8") || cleanPath.endsWith(".m3u") -> "m3u8"
            cleanPath.endsWith(".ts") || cleanPath.endsWith(".m2ts") -> "ts"
            cleanPath.endsWith(".mp4") || cleanPath.endsWith(".m4v") -> "mp4"
            cleanPath.endsWith(".mkv") -> "mkv"
            cleanPath.endsWith(".webm") -> "webm"
            contentType.equals("LIVE", ignoreCase = true) -> "ts"
            else -> "mp4"
        }
    }

    private fun isHlsPlaylist(url: String, contentType: String?): Boolean {
        val cleanPath = url.substringBefore('?').substringBefore('#').lowercase(Locale.US)
        val ct = contentType?.lowercase(Locale.US).orEmpty()
        return cleanPath.endsWith(".m3u8") ||
            cleanPath.endsWith(".m3u") ||
            ct.contains("mpegurl") ||
            ct.contains("x-mpegurl")
    }

    private fun inferMimeType(url: String, upstreamContentType: String?, isLive: Boolean): String {
        val cleanCt = upstreamContentType?.substringBefore(';')?.trim()?.lowercase(Locale.US)
        if (!cleanCt.isNullOrBlank() && cleanCt != "application/octet-stream" && cleanCt != "binary/octet-stream") {
            return cleanCt
        }
        val cleanPath = url.substringBefore('?').substringBefore('#').lowercase(Locale.US)
        return when {
            cleanPath.endsWith(".m3u8") || cleanPath.endsWith(".m3u") -> "application/vnd.apple.mpegurl"
            cleanPath.endsWith(".ts") || cleanPath.endsWith(".m2ts") -> "video/mp2t"
            cleanPath.endsWith(".mkv") -> "video/x-matroska"
            cleanPath.endsWith(".webm") -> "video/webm"
            cleanPath.endsWith(".mp4") || cleanPath.endsWith(".m4v") -> "video/mp4"
            isLive -> "video/mp2t"
            else -> "video/mp4"
        }
    }

    private fun readBoundedBytes(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (total < maxBytes) {
            val toRead = minOf(buf.size, maxBytes - total)
            val n = input.read(buf, 0, toRead)
            if (n == -1) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    private fun encodeBase64Url(input: String): String {
        return Base64.encodeToString(
            input.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    private fun decodeBase64Url(encoded: String): String? {
        return try {
            String(
                Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                Charsets.UTF_8
            )
        } catch (_: Exception) {
            null
        }
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

    private fun escapeHtml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#x27;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun generateSessionToken(): String {
        val bytes = ByteArray(18)
        SecureRandom().nextBytes(bytes)
        val hex = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            hex.append(String.format(Locale.US, "%02x", b))
        }
        return hex.toString()
    }
}
