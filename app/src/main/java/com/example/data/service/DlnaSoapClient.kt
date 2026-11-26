package com.example.data.service

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class DlnaCastResult(
    val success: Boolean,
    val message: String? = null,
    val httpCode: Int = 0
)

class DlnaSoapClient {
    companion object {
        private const val TAG = "DlnaSoapClient"
        private const val AV_TRANSPORT_NS = "urn:schemas-upnp-org:service:AVTransport:1"
        private const val RENDERING_CONTROL_NS = "urn:schemas-upnp-org:service:RenderingControl:1"

        data class MediaProfile(
            val mimeType: String,
            val upnpClass: String,
            val protocolInfo: String
        )

        internal fun escapeXml(input: String): String {
            val sb = StringBuilder(input.length + 16)
            for (ch in input) {
                when (ch) {
                    '&' -> sb.append("&amp;")
                    '<' -> sb.append("&lt;")
                    '>' -> sb.append("&gt;")
                    '"' -> sb.append("&quot;")
                    '\'' -> sb.append("&apos;")
                    else -> {
                        // Filtra caracteres de controle XML 1.0 inválidos
                        if (ch == '\t' || ch == '\n' || ch == '\r' || ch.code >= 0x20) {
                            sb.append(ch)
                        }
                    }
                }
            }
            return sb.toString()
        }

        internal fun detectMediaProfile(streamUrl: String, contentType: String = "LIVE"): MediaProfile {
            val cleanPath = streamUrl.substringBefore('?').substringBefore('#').lowercase(Locale.US)
            val isLive = contentType.equals("LIVE", ignoreCase = true)

            return when {
                cleanPath.endsWith(".m3u8") || cleanPath.endsWith(".m3u") -> MediaProfile(
                    mimeType = "application/vnd.apple.mpegurl",
                    upnpClass = if (isLive) "object.item.videoItem.videoBroadcast" else "object.item.videoItem.movie",
                    protocolInfo = "http-get:*:application/vnd.apple.mpegurl:DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
                )
                cleanPath.endsWith(".ts") || cleanPath.endsWith(".m2ts") -> MediaProfile(
                    mimeType = "video/mp2t",
                    upnpClass = if (isLive) "object.item.videoItem.videoBroadcast" else "object.item.videoItem.movie",
                    protocolInfo = "http-get:*:video/mp2t:DLNA.ORG_PN=MPEG_TS_SD_EU_ISO;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
                )
                cleanPath.endsWith(".mkv") -> MediaProfile(
                    mimeType = "video/x-matroska",
                    upnpClass = "object.item.videoItem.movie",
                    protocolInfo = "http-get:*:video/x-matroska:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
                )
                cleanPath.endsWith(".webm") -> MediaProfile(
                    mimeType = "video/webm",
                    upnpClass = "object.item.videoItem.movie",
                    protocolInfo = "http-get:*:video/webm:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
                )
                cleanPath.endsWith(".mp4") || cleanPath.endsWith(".m4v") -> MediaProfile(
                    mimeType = "video/mp4",
                    upnpClass = "object.item.videoItem.movie",
                    protocolInfo = "http-get:*:video/mp4:DLNA.ORG_PN=AVC_MP4_MP_HD_720p_AAC;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
                )
                isLive -> MediaProfile(
                    mimeType = "video/mp2t",
                    upnpClass = "object.item.videoItem.videoBroadcast",
                    protocolInfo = "http-get:*:video/mp2t:DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
                )
                else -> MediaProfile(
                    mimeType = "video/mp4",
                    upnpClass = "object.item.videoItem.movie",
                    protocolInfo = "http-get:*:video/mp4:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
                )
            }
        }

        internal fun buildDidlLiteXml(streamUrl: String, title: String, contentType: String = "LIVE"): String {
            val profile = detectMediaProfile(streamUrl, contentType)
            val safeTitle = escapeXml(title.ifBlank { "MK21 Stream" })
            val safeUrl = escapeXml(streamUrl)
            return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
                "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
                "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" " +
                "xmlns:dlna=\"urn:schemas-dlna-org:metadata-1-0/\">" +
                "<item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
                "<dc:title>$safeTitle</dc:title>" +
                "<upnp:class>${profile.upnpClass}</upnp:class>" +
                "<res protocolInfo=\"${profile.protocolInfo}\">$safeUrl</res>" +
                "</item></DIDL-Lite>"
        }

        internal fun buildSetAvTransportUriEnvelope(streamUrl: String, didlMetadata: String?): String {
            val escapedUri = escapeXml(streamUrl)
            // UPnP AVTransport exige DIDL-Lite escapado como texto XML (não CDATA) para compatibilidade Samsung/LG
            val escapedMeta = if (didlMetadata.isNullOrEmpty()) "" else escapeXml(didlMetadata)
            return """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:SetAVTransportURI xmlns:u="$AV_TRANSPORT_NS">
                            <InstanceID>0</InstanceID>
                            <CurrentURI>$escapedUri</CurrentURI>
                            <CurrentURIMetaData>$escapedMeta</CurrentURIMetaData>
                        </u:SetAVTransportURI>
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
        }
    }

    /**
     * Backward-compatible 3-arg cast method.
     */
    fun castUrlToDlna(controlUrl: String, streamUrl: String, title: String) {
        castUrlToDlna(
            controlUrl = controlUrl,
            streamUrl = streamUrl,
            title = title,
            contentType = "LIVE",
            onResult = null
        )
    }

    /**
     * Send media URL to TV via UPnP DLNA AVTransport SOAP sequence:
     * Stop (best-effort) -> SetAVTransportURI (with DIDL-Lite, fallback to empty metadata) -> Play (with retry).
     */
    fun castUrlToDlna(
        controlUrl: String,
        streamUrl: String,
        title: String,
        contentType: String = "LIVE",
        onResult: ((DlnaCastResult) -> Unit)? = null
    ) {
        Thread({
            try {
                // 1. Limpa sessão anterior no renderizador (best-effort) para evitar UPnP 701 em Samsung/LG
                sendSoapAction(controlUrl, "$AV_TRANSPORT_NS#Stop", buildStopEnvelope())
                try {
                    Thread.sleep(200)
                } catch (_: InterruptedException) {
                }

                // 2. SetAVTransportURI com DIDL-Lite XML-escaped (compatível com Tizen e webOS)
                val didl = buildDidlLiteXml(streamUrl, title, contentType)
                val setUriEnvelope = buildSetAvTransportUriEnvelope(streamUrl, didl)
                var setRes = sendSoapAction(controlUrl, "$AV_TRANSPORT_NS#SetAVTransportURI", setUriEnvelope)

                // 3. Fallback: muitas TVs LG webOS / Samsung mais antigas rejeitam DIDL-Lite específico (714/500)
                // mas reproduzem perfeitamente quando CurrentURIMetaData é vazio.
                if (!setRes.success && setRes.httpCode in 400..599) {
                    Log.w(TAG, "SetAVTransportURI with DIDL failed (${setRes.httpCode}), retrying with empty metadata")
                    val minimalEnvelope = buildSetAvTransportUriEnvelope(streamUrl, null)
                    setRes = sendSoapAction(controlUrl, "$AV_TRANSPORT_NS#SetAVTransportURI", minimalEnvelope)
                }

                if (!setRes.success) {
                    val reason = setRes.message ?: "A TV recusou o endereço do vídeo (HTTP ${setRes.httpCode})."
                    Log.e(TAG, "SetAVTransportURI failed on $controlUrl: $reason")
                    onResult?.invoke(DlnaCastResult(false, reason, setRes.httpCode))
                    return@Thread
                }

                // 4. Breve pausa para o buffer do renderizador registrar a URI antes do Play
                try {
                    Thread.sleep(350)
                } catch (_: InterruptedException) {
                }

                // 5. Envia Play (com 1 retry caso a TV ainda esteja em TRANSITIONING / erro 701)
                var playRes = sendSoapAction(controlUrl, "$AV_TRANSPORT_NS#Play", buildPlayEnvelope())
                if (!playRes.success && playRes.httpCode in 400..599) {
                    try {
                        Thread.sleep(600)
                    } catch (_: InterruptedException) {
                    }
                    playRes = sendSoapAction(controlUrl, "$AV_TRANSPORT_NS#Play", buildPlayEnvelope())
                }

                if (playRes.success) {
                    Log.d(TAG, "Cast commands succeeded on DLNA target: $controlUrl")
                    onResult?.invoke(DlnaCastResult(true, null, playRes.httpCode))
                } else {
                    val reason = playRes.message ?: "A TV aceitou o link mas falhou ao iniciar a reprodução (HTTP ${playRes.httpCode})."
                    Log.w(TAG, "DLNA Play failed on $controlUrl: $reason")
                    onResult?.invoke(DlnaCastResult(false, reason, playRes.httpCode))
                }
            } catch (e: Exception) {
                Log.e(TAG, "DLNA Cast sequence failed", e)
                onResult?.invoke(DlnaCastResult(false, "Erro de rede ao conectar na TV: ${e.message}"))
            }
        }, "mk21-dlna-cast").start()
    }

    fun pauseDlna(controlUrl: String) {
        Thread({
            val soapPause = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:Pause xmlns:u="$AV_TRANSPORT_NS">
                            <InstanceID>0</InstanceID>
                        </u:Pause>
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
            sendSoapAction(controlUrl, "$AV_TRANSPORT_NS#Pause", soapPause)
        }, "mk21-dlna-pause").start()
    }

    fun resumeDlna(controlUrl: String) {
        Thread({
            sendSoapAction(controlUrl, "$AV_TRANSPORT_NS#Play", buildPlayEnvelope())
        }, "mk21-dlna-play").start()
    }

    fun seekDlna(controlUrl: String, positionSeconds: Long) {
        Thread({
            val safeSeconds = positionSeconds.coerceAtLeast(0L)
            val hh = safeSeconds / 3600
            val mm = (safeSeconds % 3600) / 60
            val ss = safeSeconds % 60
            val timeStr = String.format(Locale.US, "%02d:%02d:%02d", hh, mm, ss)

            val soapSeek = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:Seek xmlns:u="$AV_TRANSPORT_NS">
                            <InstanceID>0</InstanceID>
                            <Unit>REL_TIME</Unit>
                            <Target>$timeStr</Target>
                        </u:Seek>
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
            sendSoapAction(controlUrl, "$AV_TRANSPORT_NS#Seek", soapSeek)
        }, "mk21-dlna-seek").start()
    }

    fun stopDlna(controlUrl: String) {
        Thread({
            sendSoapAction(controlUrl, "$AV_TRANSPORT_NS#Stop", buildStopEnvelope())
        }, "mk21-dlna-stop").start()
    }

    /**
     * Adjust TV volume via UPnP RenderingControl:1#SetVolume (0..100).
     */
    fun setVolumeDlna(renderingControlUrl: String, volumePercent: Int) {
        Thread({
            val clamped = volumePercent.coerceIn(0, 100)
            val soapVolume = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:SetVolume xmlns:u="$RENDERING_CONTROL_NS">
                            <InstanceID>0</InstanceID>
                            <Channel>Master</Channel>
                            <DesiredVolume>$clamped</DesiredVolume>
                        </u:SetVolume>
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
            sendSoapAction(renderingControlUrl, "$RENDERING_CONTROL_NS#SetVolume", soapVolume)
        }, "mk21-dlna-vol").start()
    }

    private fun buildPlayEnvelope(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
            <s:Body>
                <u:Play xmlns:u="$AV_TRANSPORT_NS">
                    <InstanceID>0</InstanceID>
                    <Speed>1</Speed>
                </u:Play>
            </s:Body>
        </s:Envelope>
    """.trimIndent()

    private fun buildStopEnvelope(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
            <s:Body>
                <u:Stop xmlns:u="$AV_TRANSPORT_NS">
                    <InstanceID>0</InstanceID>
                </u:Stop>
            </s:Body>
        </s:Envelope>
    """.trimIndent()

    fun sendSoapAction(controlUrl: String, soapAction: String, xmlPayload: String): DlnaCastResult {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(controlUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3500
                readTimeout = 4500
                doOutput = true
                setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                setRequestProperty("SOAPACTION", "\"$soapAction\"")
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", "MK21-DLNA/3.0 UPnP/1.1")
            }

            val outputBytes = xmlPayload.toByteArray(Charsets.UTF_8)
            conn.setRequestProperty("Content-Length", outputBytes.size.toString())

            conn.outputStream.use { os ->
                os.write(outputBytes)
                os.flush()
            }

            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                Log.d(TAG, "UPnP SOAP success ($code) action=$soapAction")
                DlnaCastResult(true, null, code)
            } else {
                val errBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val upnpErr = Regex("<errorDescription>(.*?)</errorDescription>", RegexOption.IGNORE_CASE)
                    .find(errBody)?.groupValues?.getOrNull(1)?.trim()
                val upnpCode = Regex("<errorCode>(.*?)</errorCode>", RegexOption.IGNORE_CASE)
                    .find(errBody)?.groupValues?.getOrNull(1)?.trim()
                val friendly = when {
                    !upnpErr.isNullOrBlank() && !upnpCode.isNullOrBlank() -> "TV retornou erro UPnP $upnpCode ($upnpErr)."
                    !upnpErr.isNullOrBlank() -> "TV retornou erro: $upnpErr."
                    else -> "TV recusou o comando (HTTP $code)."
                }
                Log.w(TAG, "UPnP SOAP failure ($code) action=$soapAction body=$errBody")
                DlnaCastResult(false, friendly, code)
            }
        } catch (e: Exception) {
            Log.e(TAG, "SOAP HTTP POST failed to $controlUrl: ${e.message}")
            DlnaCastResult(false, "Não foi possível comunicar com a TV (${e.javaClass.simpleName}).", 0)
        } finally {
            conn?.disconnect()
        }
    }
}
