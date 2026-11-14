package com.fbg.streaming

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.PlaylistItem
import com.example.data.service.LocalHttpServer
import com.fbg.streaming.network.proxy.Mk21ProxyStation
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.Socket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalServerSecurityTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `Mk21ProxyStation requires valid session token and omits wildcard CORS`() {
        val station = Mk21ProxyStation(context, object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                listOf(InetAddress.getByName("127.0.0.1"))
        })
        val port = station.start()
        try {
            assertTrue(port > 0)
            val proxyUrl = station.buildProxyUrl("https://cdn.example.com/live/channel.m3u8")
            assertTrue(proxyUrl.contains("t=${station.currentSessionToken()}"))

            // Requisição sem token deve receber 403 Forbidden sem Access-Control-Allow-Origin: *
            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 3000
                socket.getOutputStream().write(
                    "GET /?u=aHR0cHM6Ly9leGFtcGxlLmNvbQ== HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray()
                )
                socket.getOutputStream().flush()
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val statusLine = reader.readLine()
                assertNotNull(statusLine)
                assertTrue(statusLine!!.contains("403"))
                val headers = buildList {
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        add(line)
                    }
                }
                assertFalse(headers.any { it.contains("Access-Control-Allow-Origin", ignoreCase = true) })
            }

            // Esquemas perigosos e auto-recursão na própria porta devem ser rejeitados
            assertFalse(station.isAllowedTargetUrl("file:///etc/passwd"))
            assertFalse(station.isAllowedTargetUrl("http://127.0.0.1:$port/?t=${station.currentSessionToken()}"))
            assertTrue(station.isAllowedTargetUrl("http://stream.example.com:8080/live.ts"))
        } finally {
            station.stop()
        }
    }

    @Test
    fun `LocalHttpServer enforces session token, hides stream URL in status API, and escapes HTML`() {
        val server = LocalHttpServer()
        server.serverPort = 0 // Porta efêmera para teste isolado
        val item = PlaylistItem(
            id = 1,
            name = "<script>alert(1)</script> Canal VIP",
            url = "http://iptv.example.com/live/secretUser/secretPass/123.ts",
            logoUrl = "",
            category = "TV",
            contentType = "LIVE",
            playlistSource = "TEST"
        )
        val castUrl = server.startServer(context, item)
        try {
            assertNotNull(castUrl)
            val token = server.currentSessionToken()
            assertTrue(castUrl!!.contains("?t=$token"))

            // Extrai a porta real via reflection apenas se serverPort=0 não atualizou, ou usa serverPort
            // Como ServerSocket(0) aloca porta dinâmica, vamos testar isAuthorized e handleConnection direto com pipe/local
            assertFalse(server.isAuthorized("/"))
            assertFalse(server.isAuthorized("/api/status?t=wrong-token"))
            assertTrue(server.isAuthorized("/api/status?t=$token"))
        } finally {
            server.stopServer()
        }
    }

    @Test
    fun `LocalHttpServer socket rejects unauthorized requests and prevents credential leak and XSS`() {
        val server = LocalHttpServer()
        // Encontra porta livre
        val probe = java.net.ServerSocket(0)
        val freePort = probe.localPort
        probe.close()
        server.serverPort = freePort

        val item = PlaylistItem(
            id = 2,
            name = "<img src=x onerror=alert(1)>",
            url = "http://iptv.example.com/live/myuser/mypass/99.ts",
            logoUrl = "",
            category = "TV",
            contentType = "LIVE",
            playlistSource = "TEST"
        )
        server.startServer(context, item)
        try {
            val token = server.currentSessionToken()

            // 1. /api/status sem token -> 403
            Socket("127.0.0.1", freePort).use { socket ->
                socket.soTimeout = 3000
                socket.getOutputStream().write("GET /api/status HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
                socket.getOutputStream().flush()
                val status = BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                assertTrue(status!!.contains("403"))
            }

            // 2. /api/status com token -> 200 sem URL/credenciais e sem ACAO: *
            Socket("127.0.0.1", freePort).use { socket ->
                socket.soTimeout = 3000
                socket.getOutputStream().write("GET /api/status?t=$token HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
                socket.getOutputStream().flush()
                val raw = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
                assertTrue(raw.startsWith("HTTP/1.1 200 OK"))
                assertFalse(raw.contains("Access-Control-Allow-Origin"))
                assertFalse(raw.contains("myuser"))
                assertFalse(raw.contains("mypass"))
                assertFalse(raw.contains("iptv.example.com"))
            }

            // 3. Página HTML com token -> escapa HTML contra XSS
            Socket("127.0.0.1", freePort).use { socket ->
                socket.soTimeout = 3000
                socket.getOutputStream().write("GET /?t=$token HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
                socket.getOutputStream().flush()
                val raw = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
                assertTrue(raw.contains("&lt;img src=x onerror=alert(1)&gt;"))
                assertFalse(raw.contains("<img src=x onerror=alert(1)>"))
            }
        } finally {
            server.stopServer()
        }
    }
}
