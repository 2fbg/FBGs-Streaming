package com.fbg.streaming

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.PlaylistItem
import com.example.data.service.DlnaDiscoveryService
import com.example.data.service.DlnaSoapClient
import com.example.data.service.LocalHttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DlnaCastTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `resolveUpnpUrl omits invalid -1 port when LOCATION has default port`() {
        val resolved = DlnaDiscoveryService.resolveUpnpUrl(
            descUrl = "http://192.168.3.79/dmr/description.xml",
            urlBase = null,
            rawPath = "/AVTransport/control"
        )
        assertEquals("http://192.168.3.79/AVTransport/control", resolved)
        assertFalse(resolved.contains(":-1"))
    }

    @Test
    fun `parseDeviceDescriptionXml extracts friendlyName, AVTransport and RenderingControl`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <device>
                <friendlyName>[LG] webOS TV TL520S</friendlyName>
                <manufacturer>LG Electronics</manufacturer>
                <modelName>webOS TV</modelName>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>/AVTransport/1234/control.xml</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                    <controlURL>/RenderingControl/1234/control.xml</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()

        val device = DlnaDiscoveryService.parseDeviceDescriptionXml(
            xml = xml,
            descUrl = "http://192.168.3.79:1945/description.xml",
            fallbackIp = "192.168.3.79"
        )

        assertNotNull(device)
        assertEquals("[LG] webOS TV TL520S", device!!.friendlyName)
        assertEquals("LG Electronics", device.manufacturer)
        assertEquals("http://192.168.3.79:1945/AVTransport/1234/control.xml", device.controlUrl)
        assertEquals("http://192.168.3.79:1945/RenderingControl/1234/control.xml", device.renderingControlUrl)
    }

    @Test
    fun `parseDeviceDescriptionXml ignores devices without AVTransport service`() {
        val routerXml = """
            <root><device><friendlyName>Wi-Fi Router</friendlyName>
            <serviceList><service><serviceType>urn:schemas-upnp-org:service:WANIPConnection:1</serviceType>
            <controlURL>/ctl/IPConn</controlURL></service></serviceList></device></root>
        """.trimIndent()

        val device = DlnaDiscoveryService.parseDeviceDescriptionXml(
            xml = routerXml,
            descUrl = "http://192.168.3.1:5000/rootDesc.xml",
            fallbackIp = "192.168.3.1"
        )
        assertNull(device)
    }

    @Test
    fun `DlnaSoapClient escapes XML entities and avoids CDATA in SetAVTransportURI`() {
        val streamUrl = "http://192.168.3.141:8585/stream.ts?t=abc123&rev=2"
        val title = "A&E Brasil <HD>"
        val didl = DlnaSoapClient.buildDidlLiteXml(streamUrl, title, "LIVE")
        val envelope = DlnaSoapClient.buildSetAvTransportUriEnvelope(streamUrl, didl)

        assertFalse("DIDL-Lite must not use CDATA for Samsung/LG compatibility", envelope.contains("CDATA"))
        assertTrue(envelope.contains("<CurrentURI>http://192.168.3.141:8585/stream.ts?t=abc123&amp;rev=2</CurrentURI>"))
        assertTrue(envelope.contains("&lt;DIDL-Lite"))
        assertTrue(didl.contains("video/mp2t"))
        assertTrue(didl.contains("object.item.videoItem.videoBroadcast"))
        assertTrue(didl.contains("A&amp;E Brasil &lt;HD&gt;"))
    }

    @Test
    fun `DlnaSoapClient detects HLS and VOD media profiles accurately`() {
        val hls = DlnaSoapClient.detectMediaProfile("http://192.168.3.141:8585/stream.m3u8?t=tok", "LIVE")
        assertEquals("application/vnd.apple.mpegurl", hls.mimeType)
        assertEquals("object.item.videoItem.videoBroadcast", hls.upnpClass)

        val vod = DlnaSoapClient.detectMediaProfile("http://192.168.3.141:8585/stream.mp4?t=tok", "MOVIE")
        assertEquals("video/mp4", vod.mimeType)
        assertEquals("object.item.videoItem.movie", vod.upnpClass)
    }

    @Test
    fun `LocalHttpServer enforces token on stream proxy, blocks SSRF, hides credentials in HTML, and rewrites HLS`() {
        val server = LocalHttpServer()
        val probe = ServerSocket(0)
        val freePort = probe.localPort
        probe.close()
        server.serverPort = freePort

        val item = PlaylistItem(
            id = 10,
            name = "Canal Esportes HD",
            url = "http://iptv.example.com/live/secretUser/secretPass/12345.ts",
            logoUrl = "",
            category = "Esportes",
            contentType = "LIVE",
            playlistSource = "TEST"
        )
        server.startServer(context, item)
        try {
            val token = server.currentSessionToken()

            // SSRF guards
            assertFalse(server.isAllowedUpstreamUrl("file:///etc/passwd"))
            assertFalse(server.isAllowedUpstreamUrl("http://127.0.0.1:$freePort/stream.ts?t=$token"))
            assertFalse(server.isAllowedUpstreamUrl("http://localhost:8080/live.ts"))
            assertFalse(server.isAllowedUpstreamUrl("http://169.254.169.254/latest/meta-data/"))
            assertTrue(server.isAllowedUpstreamUrl("http://iptv.example.com/live/user/pass/1.ts"))

            // /stream.ts sem token -> 403
            Socket("127.0.0.1", freePort).use { socket ->
                socket.soTimeout = 3000
                socket.getOutputStream().write("GET /stream.ts HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
                socket.getOutputStream().flush()
                val status = BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                assertNotNull(status)
                assertTrue(status!!.contains("403"))
            }

            // Página HTML usa /stream.ts local e não vaza credenciais do provedor IPTV
            Socket("127.0.0.1", freePort).use { socket ->
                socket.soTimeout = 3000
                socket.getOutputStream().write("GET /?t=$token HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
                socket.getOutputStream().flush()
                val html = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
                assertTrue(html.startsWith("HTTP/1.1 200 OK"))
                assertTrue(html.contains("/stream.ts?t=$token"))
                assertFalse(html.contains("secretUser"))
                assertFalse(html.contains("secretPass"))
                assertFalse(html.contains("iptv.example.com"))
            }

            // Reescrita de playlist HLS para /hls?u=...&t=...
            val manifest = """
                #EXTM3U
                #EXT-X-KEY:METHOD=AES-128,URI="keys/enc.key"
                #EXTINF:6.0,
                seg001.ts
                #EXTINF:6.0,
                https://cdn.example.com/live/seg002.ts
            """.trimIndent()
            val rewritten = server.rewriteHlsPlaylist(manifest, "https://cdn.example.com/live/index.m3u8", token)
            assertTrue(rewritten.contains("URI=\"/hls?u="))
            assertTrue(rewritten.contains("&t=$token\""))
            assertTrue(rewritten.lines().any { it.startsWith("/hls?u=") && it.endsWith("&t=$token") })
        } finally {
            server.stopServer()
        }
    }
}
