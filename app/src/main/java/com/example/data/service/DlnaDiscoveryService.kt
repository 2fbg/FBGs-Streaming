package com.example.data.service

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class DlnaDiscoveryService {
    companion object {
        private const val TAG = "DlnaDiscoveryService"
        private const val SSDP_MULTICAST_ADDR = "239.255.255.250"
        private const val SSDP_PORT = 1900

        private val SEARCH_TARGETS = listOf(
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "urn:schemas-upnp-org:service:AVTransport:1"
        )

        private val MANUAL_COMMON_PORTS = listOf(
            7676, 9197, 52235, 1400, 1500, 1600, 1800, 1901,
            38400, 49152, 49153, 55000, 8008, 8012, 8080, 8200, 50244
        )

        private val MANUAL_DESCRIPTION_PATHS = listOf(
            "/smp_2_",
            "/dmr",
            "/dmr/SamsungMRDesc.xml",
            "/description.xml",
            "/dlna/description.xml",
            "/xml/device_description.xml",
            "/upnp/desc.xml",
            "/dd.xml",
            "/MediaRenderer/desc.xml"
        )

        private val IPV4_REGEX = Regex(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$"
        )

        internal fun resolveUpnpUrl(descUrl: String, urlBase: String?, rawPath: String): String {
            val trimmedPath = rawPath.trim()
            if (trimmedPath.startsWith("http://", ignoreCase = true) ||
                trimmedPath.startsWith("https://", ignoreCase = true)
            ) {
                return trimmedPath
            }
            val parsedDesc = URL(descUrl)
            val baseStr = urlBase?.trim()?.takeIf {
                it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
            }
            val baseUrl = if (baseStr != null) {
                URL(baseStr)
            } else {
                val portPart = if (parsedDesc.port != -1) ":${parsedDesc.port}" else ""
                URL("${parsedDesc.protocol}://${parsedDesc.host}$portPart/")
            }
            return URL(baseUrl, trimmedPath).toString()
        }

        internal fun buildBaseOrigin(url: URL): String {
            val portPart = if (url.port != -1) ":${url.port}" else ""
            return "${url.protocol}://${url.host}$portPart/"
        }

        internal fun parseDeviceDescriptionXml(xml: String, descUrl: String, fallbackIp: String): DLNADevice? {
            if (xml.isBlank()) return null

            val avTransportControl = extractServiceControlUrl(xml, "AVTransport")
            // Só aceita dispositivos que realmente expõem serviço AVTransport
            if (avTransportControl.isNullOrBlank()) {
                return null
            }

            val parsedUrl = try {
                URL(descUrl)
            } catch (_: Exception) {
                return null
            }

            val urlBase = extractTagText(xml, "URLBase")
            val friendlyName = extractTagText(xml, "friendlyName")
                ?.takeIf { it.isNotBlank() }
                ?: "Smart TV ($fallbackIp)"
            val manufacturer = extractTagText(xml, "manufacturer").orEmpty()
            val modelName = extractTagText(xml, "modelName").orEmpty()

            val resolvedControlUrl = try {
                resolveUpnpUrl(descUrl, urlBase, avTransportControl)
            } catch (_: Exception) {
                return null
            }

            val renderingControlRaw = extractServiceControlUrl(xml, "RenderingControl")
            val resolvedRenderingUrl = renderingControlRaw?.let {
                try {
                    resolveUpnpUrl(descUrl, urlBase, it)
                } catch (_: Exception) {
                    null
                }
            }

            val ip = fallbackIp.ifBlank { parsedUrl.host }
            return DLNADevice(
                friendlyName = decodeBasicXmlEntities(friendlyName),
                controlUrl = resolvedControlUrl,
                baseUrl = buildBaseOrigin(parsedUrl),
                ipAddress = ip,
                renderingControlUrl = resolvedRenderingUrl,
                manufacturer = decodeBasicXmlEntities(manufacturer),
                modelName = decodeBasicXmlEntities(modelName)
            )
        }

        private fun extractTagText(xml: String, tagName: String): String? {
            val regex = Regex("<(?:[a-zA-Z0-9_-]+:)?$tagName\\b[^>]*>(.*?)</(?:[a-zA-Z0-9_-]+:)?$tagName>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            return regex.find(xml)?.groupValues?.getOrNull(1)?.trim()
        }

        private fun extractServiceControlUrl(xml: String, serviceName: String): String? {
            val serviceBlockRegex = Regex("<(?:[a-zA-Z0-9_-]+:)?service\\b[^>]*>(.*?)</(?:[a-zA-Z0-9_-]+:)?service>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            for (match in serviceBlockRegex.findAll(xml)) {
                val block = match.groupValues[1]
                val serviceType = extractTagText(block, "serviceType").orEmpty()
                if (serviceType.contains("service:$serviceName:", ignoreCase = true) ||
                    serviceType.contains(serviceName, ignoreCase = true)
                ) {
                    val ctrl = extractTagText(block, "controlURL")
                    if (!ctrl.isNullOrBlank()) {
                        return ctrl
                    }
                }
            }
            return null
        }

        private fun decodeBasicXmlEntities(input: String): String {
            return input
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
        }
    }

    /**
     * Backward-compatible signature without Context or completion callback.
     */
    fun discoverDlnaDevices(onDeviceDiscovered: (DLNADevice) -> Unit) {
        discoverDlnaDevices(
            context = null,
            onDeviceDiscovered = onDeviceDiscovered,
            onScanComplete = null
        )
    }

    /**
     * Scan the local network for DLNA/UPnP MediaRenderers using SSDP M-SEARCH.
     * Acquires a Wi-Fi MulticastLock when Context is provided to avoid Android dropping multicast replies.
     */
    fun discoverDlnaDevices(
        context: Context?,
        onDeviceDiscovered: (DLNADevice) -> Unit,
        onScanComplete: (() -> Unit)? = null
    ) {
        Thread({
            var multicastLock: WifiManager.MulticastLock? = null
            var socket: DatagramSocket? = null
            val fetchExecutor = Executors.newCachedThreadPool()
            try {
                if (context != null) {
                    try {
                        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                        multicastLock = wifiManager?.createMulticastLock("mk21_ssdp_lock")?.apply {
                            setReferenceCounted(false)
                            acquire()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "MulticastLock unavailable: ${e.message}")
                    }
                }

                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(0))
                    soTimeout = 700
                }

                val target = InetAddress.getByName(SSDP_MULTICAST_ADDR)

                fun sendProbes() {
                    for (st in SEARCH_TARGETS) {
                        val query = buildMSearchPayload(SSDP_MULTICAST_ADDR, st)
                        val bytes = query.toByteArray(Charsets.UTF_8)
                        val packet = DatagramPacket(bytes, bytes.size, target, SSDP_PORT)
                        try {
                            socket.send(packet)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed sending SSDP probe for $st: ${e.message}")
                        }
                    }
                }

                // Dispara 2 rajadas SSDP para compensar perda de pacotes UDP em Wi-Fi doméstico
                sendProbes()

                val buffer = ByteArray(8192)
                val responsePacket = DatagramPacket(buffer, buffer.size)
                val seenLocations = ConcurrentHashMap.newKeySet<String>()
                val emittedKeys = ConcurrentHashMap.newKeySet<String>()
                val pendingFetches = AtomicInteger(0)

                val startTime = System.currentTimeMillis()
                var sentSecondWave = false
                val listenWindowMs = 3200L

                while (System.currentTimeMillis() - startTime < listenWindowMs) {
                    if (!sentSecondWave && System.currentTimeMillis() - startTime >= 450L) {
                        sentSecondWave = true
                        sendProbes()
                    }
                    try {
                        responsePacket.length = buffer.size
                        socket.receive(responsePacket)
                        val response = String(responsePacket.data, 0, responsePacket.length, Charsets.UTF_8)
                        val locationUrl = extractLocationHeader(response)
                        val sourceIp = responsePacket.address?.hostAddress.orEmpty()
                        if (!locationUrl.isNullOrBlank() && seenLocations.add(locationUrl)) {
                            pendingFetches.incrementAndGet()
                            fetchExecutor.execute {
                                try {
                                    val device = fetchDeviceDescriptionSync(locationUrl, sourceIp)
                                    if (device != null) {
                                        val dedupeKey = "${device.ipAddress}|${device.controlUrl}"
                                        if (emittedKeys.add(dedupeKey)) {
                                            onDeviceDiscovered(device)
                                        }
                                    }
                                } finally {
                                    pendingFetches.decrementAndGet()
                                }
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        // continua janela de escuta
                    } catch (e: Exception) {
                        Log.w(TAG, "SSDP receive error: ${e.message}")
                        break
                    }
                }

                // Aguarda brevemente as requisições HTTP XML em voo finalizarem (até 1.2s)
                val waitEnd = System.currentTimeMillis() + 1200L
                while (pendingFetches.get() > 0 && System.currentTimeMillis() < waitEnd) {
                    try {
                        Thread.sleep(60)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "SSDP Discovery exception", e)
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
                try {
                    if (multicastLock?.isHeld == true) {
                        multicastLock.release()
                    }
                } catch (_: Exception) {
                }
                fetchExecutor.shutdown()
                onScanComplete?.invoke()
            }
        }, "mk21-ssdp-scan").start()
    }

    /**
     * Fetch XML device description to find FriendlyName, AVTransport and RenderingControl URLs.
     */
    fun fetchDeviceDescription(descUrl: String, ip: String, callback: (DLNADevice?) -> Unit) {
        Thread({
            callback(fetchDeviceDescriptionSync(descUrl, ip))
        }, "mk21-dlna-desc").start()
    }

    private fun fetchDeviceDescriptionSync(descUrl: String, ip: String): DLNADevice? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(descUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "MK21-DLNA/3.0 UPnP/1.1")
            }
            if (conn.responseCode in 200..299) {
                val xml = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseDeviceDescriptionXml(xml, descUrl, ip)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Description fetch failed for $descUrl: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Direct IP probe for Smart TVs:
     * 1) Sends unicast SSDP M-SEARCH directly to <ip>:1900 (works on dynamic Samsung/LG ports).
     * 2) In parallel, probes common UPnP ports & description paths.
     * Returns (false, null) if no real AVTransport renderer responds — never fabricates a fake TV.
     */
    fun probeManualDevice(ip: String, onResult: (Boolean, DLNADevice?) -> Unit) {
        val cleanIp = ip.trim()
        if (!IPV4_REGEX.matches(cleanIp)) {
            onResult(false, null)
            return
        }

        Thread({
            val finished = AtomicBoolean(false)
            val executor = Executors.newFixedThreadPool(16)
            val activeTasks = AtomicInteger(0)

            fun succeed(device: DLNADevice) {
                if (finished.compareAndSet(false, true)) {
                    executor.shutdownNow()
                    onResult(true, device)
                }
            }

            fun completeTask() {
                if (activeTasks.decrementAndGet() == 0 && finished.compareAndSet(false, true)) {
                    executor.shutdown()
                    onResult(false, null)
                }
            }

            // Tarefa 1: Unicast SSDP M-SEARCH direto para o IP da TV (porta 1900)
            activeTasks.incrementAndGet()
            executor.submit {
                var sock: DatagramSocket? = null
                try {
                    sock = DatagramSocket().apply { soTimeout = 1200 }
                    val target = InetAddress.getByName(cleanIp)
                    for (st in SEARCH_TARGETS) {
                        val payload = buildMSearchPayload(cleanIp, st).toByteArray(Charsets.UTF_8)
                        sock.send(DatagramPacket(payload, payload.size, target, SSDP_PORT))
                    }
                    val buf = ByteArray(8192)
                    val pkt = DatagramPacket(buf, buf.size)
                    val deadline = System.currentTimeMillis() + 1800L
                    while (!finished.get() && System.currentTimeMillis() < deadline) {
                        try {
                            pkt.length = buf.size
                            sock.receive(pkt)
                            val response = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                            val locationUrl = extractLocationHeader(response)
                            if (!locationUrl.isNullOrBlank()) {
                                val dev = fetchDeviceDescriptionSync(locationUrl, cleanIp)
                                if (dev != null) {
                                    succeed(dev)
                                    return@submit
                                }
                            }
                        } catch (_: SocketTimeoutException) {
                            break
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    try {
                        sock?.close()
                    } catch (_: Exception) {
                    }
                    completeTask()
                }
            }

            // Tarefa 2: Varredura HTTP paralela nas portas/caminhos mais comuns de Samsung/LG/Sony
            for (port in MANUAL_COMMON_PORTS) {
                activeTasks.incrementAndGet()
                executor.submit {
                    try {
                        if (finished.get()) return@submit
                        for (path in MANUAL_DESCRIPTION_PATHS) {
                            if (finished.get()) return@submit
                            val urlStr = "http://$cleanIp:$port$path"
                            var conn: HttpURLConnection? = null
                            try {
                                conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                                    connectTimeout = 550
                                    readTimeout = 700
                                    instanceFollowRedirects = true
                                }
                                if (conn.responseCode in 200..299) {
                                    val xmlText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                                    val device = parseDeviceDescriptionXml(xmlText, urlStr, cleanIp)
                                    if (device != null) {
                                        succeed(device)
                                        return@submit
                                    }
                                }
                            } catch (e: java.net.ConnectException) {
                                // Porta fechada no IP alvo — não precisa testar outros caminhos nesta porta
                                break
                            } catch (_: SocketTimeoutException) {
                                break
                            } catch (_: Exception) {
                                // tenta próximo caminho nesta porta
                            } finally {
                                conn?.disconnect()
                            }
                        }
                    } finally {
                        completeTask()
                    }
                }
            }
        }, "mk21-dlna-probe").start()
    }

    private fun buildMSearchPayload(host: String, searchTarget: String): String {
        return "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $host:$SSDP_PORT\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: $searchTarget\r\n\r\n"
    }

    private fun extractLocationHeader(ssdpResponse: String): String? {
        for (line in ssdpResponse.lines()) {
            val idx = line.indexOf(':')
            if (idx > 0 && line.substring(0, idx).trim().equals("LOCATION", ignoreCase = true)) {
                val value = line.substring(idx + 1).trim()
                if (value.lowercase(Locale.US).startsWith("http://") ||
                    value.lowercase(Locale.US).startsWith("https://")
                ) {
                    return value
                }
            }
        }
        return null
    }
}
