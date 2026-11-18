package com.example

import com.example.data.parser.M3UParser
import com.example.network.StreamHeadersRegistry
import com.example.utils.LogSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class M3uAndSanitizerTest {

    @Before
    fun setUp() {
        StreamHeadersRegistry.clear()
    }

    @Test
    fun parsesExtVlcOptHeadersAndPreservesPlaylistItems() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="globo.br" group-title="Canais | Abertos",Globo HD
            #EXTVLCOPT:http-user-agent=VLC/3.0.20 LibVLC/3.0.20
            #EXTVLCOPT:http-referrer=https://portal.example.com/
            http://cdn.example.com/live/alice/secret123/1001.m3u8
            #EXTINF:-1 group-title="Canais | Abertos",SBT HD
            http://cdn.example.com/live/alice/secret123/1002.m3u8
        """.trimIndent()

        val items = M3UParser.parse(
            ByteArrayInputStream(m3u.toByteArray(Charsets.UTF_8)),
            "Teste"
        ) {}

        assertEquals(2, items.size)
        assertEquals("Globo HD", items[0].name)

        val h1 = StreamHeadersRegistry.get("http://cdn.example.com/live/alice/secret123/1001.m3u8")
        assertNotNull(h1)
        assertEquals("VLC/3.0.20 LibVLC/3.0.20", h1?.userAgent)
        assertEquals("https://portal.example.com/", h1?.referrer)

        val h2 = StreamHeadersRegistry.get("http://cdn.example.com/live/alice/secret123/1002.m3u8")
        assertNull(h2)
    }

    @Test
    fun redactsXtreamCredentialsQueryParamsBasicAuthAndTokens() {
        val raw = "Falha em https://bob:myPass99@tv.example.com:8080/live/alice/secret123/42.ts?username=alice&password=p1&token=tok99 MKL1.abc123.sig456"
        val sanitized = LogSanitizer.sanitizeText(raw)

        assertFalse(sanitized.contains("myPass99"))
        assertFalse(sanitized.contains("alice"))
        assertFalse(sanitized.contains("secret123"))
        assertFalse(sanitized.contains("tok99"))
        assertFalse(sanitized.contains("sig456"))
    }
}
