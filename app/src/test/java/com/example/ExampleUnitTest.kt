package com.example

import com.example.data.parser.M3UParser
import com.example.player.PlaybackSourceFallback
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class ExampleUnitTest {
    
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testParseStandardM3U() {
        val m3uData = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="http://logo.com/globo.png" group-title="Abertos",Globo RJ
            http://stream.com/globo.ts
        """.trimIndent()

        val parsed = M3UParser.parse(ByteArrayInputStream(m3uData.toByteArray()), "source_test") { _ -> }
        
        assertEquals(1, parsed.size)
        val item = parsed[0]
        assertEquals("Globo RJ", item.name)
        assertEquals("http://stream.com/globo.ts", item.url)
        assertEquals("http://logo.com/globo.png", item.logoUrl)
        assertEquals("Abertos", item.category)
        assertEquals("LIVE", item.contentType)
    }

    @Test
    fun testParseM3U_WithUTF8_BOM() {
        val m3uData = "\uFEFF#EXTM3U\n" +
                "#EXTINF:-1 group-title=\"Filmes\",Matrix\n" +
                "http://stream.com/matrix.mp4"

        val parsed = M3UParser.parse(ByteArrayInputStream(m3uData.toByteArray()), "source_test") { _ -> }
        
        assertEquals(1, parsed.size)
        val item = parsed[0]
        assertEquals("Matrix", item.name)
        assertEquals("MOVIE", item.contentType)
        assertEquals("Filmes", item.category)
    }

    @Test
    fun testParseM3U_WithSpacesNoColon() {
        // Some providers supply `#EXTINF -1,...` instead of `#EXTINF:-1,...`
        val m3uData = """
            #EXTM3U
            #EXTINF -1 tvg-logo="http://logo.com/espn.png" group-title="Esportes",ESPN Premium
            http://stream.com/espn.ts
        """.trimIndent()

        val parsed = M3UParser.parse(ByteArrayInputStream(m3uData.toByteArray()), "source_test") { _ -> }
        
        assertEquals(1, parsed.size)
        val item = parsed[0]
        assertEquals("ESPN Premium", item.name)
        assertEquals("Esportes", item.category)
    }

    @Test
    fun testParseM3U_WithAdjacentGroupTag() {
        // Some providers use adjacent #EXTGRP: tag for category names
        val m3uData = """
            #EXTM3U
            #EXTINF:-1,HBO HD
            #EXTGRP:Filmes Premium
            http://stream.com/hbo.ts
        """.trimIndent()

        val parsed = M3UParser.parse(ByteArrayInputStream(m3uData.toByteArray()), "source_test") { _ -> }
        
        assertEquals(1, parsed.size)
        val item = parsed[0]
        assertEquals("HBO HD", item.name)
        assertEquals("Filmes Premium", item.category)
    }

    @Test
    fun testParseM3U_WithMixedCasingAttributes() {
        // Check case-insensitive attribute parsing like group-title Group-title GROUP-TITLE etc.
        val m3uData = """
            #EXTM3U
            #EXTINF:-1 TVG-LOGO="http://logo.com/hbo.png" GrOuP-TiTlE="Canais de Filmes",HBO 2
            http://stream.com/hbo2.ts
        """.trimIndent()

        val parsed = M3UParser.parse(ByteArrayInputStream(m3uData.toByteArray()), "source_test") { _ -> }
        
        assertEquals(1, parsed.size)
        val item = parsed[0]
        assertEquals("Canais de Filmes", item.category)
        assertEquals("http://logo.com/hbo.png", item.logoUrl)
    }

    @Test
    fun testParseM3U_MergesLiveStreamsWithSameTvgIdInPlaylistOrder() {
        val m3uData = """
            #EXTM3U
            #EXTINF:-1 tvg-id="news.br" group-title="Notícias",Canal News HD
            https://one.example/live/news.m3u8
            #EXTINF:-1 tvg-id="NEWS.BR" group-title="Alternativos",Canal News FHD
            https://two.example/live/news.m3u8
            #EXTINF:-1 tvg-id="news.br" group-title="Notícias",Canal News 4K
            https://three.example/live/news.m3u8
        """.trimIndent()

        val parsed = M3UParser.parse(ByteArrayInputStream(m3uData.toByteArray()), "source_test") { _ -> }

        assertEquals(1, parsed.size)
        assertEquals("https://one.example/live/news.m3u8", parsed.single().url)
        assertEquals(
            listOf(
                "https://two.example/live/news.m3u8",
                "https://three.example/live/news.m3u8"
            ),
            parsed.single().alternateUrls
        )
    }

    @Test
    fun testParseM3U_DoesNotMergeDifferentTvgIds() {
        val m3uData = """
            #EXTM3U
            #EXTINF:-1 tvg-id="news.one" group-title="Notícias",Canal News
            https://one.example/live/news.m3u8
            #EXTINF:-1 tvg-id="news.two" group-title="Notícias",Canal News
            https://two.example/live/news.m3u8
        """.trimIndent()

        val parsed = M3UParser.parse(ByteArrayInputStream(m3uData.toByteArray()), "source_test") { _ -> }

        assertEquals(2, parsed.size)
        assertTrue(parsed.all { it.alternateUrls.isEmpty() })
    }

    @Test
    fun testParseM3U_MergesNormalizedNamesWhenTvgIdIsMissing() {
        val m3uData = """
            #EXTM3U
            #EXTINF:-1 group-title="Notícias",Canal   News
            https://one.example/live/news.m3u8
            #EXTINF:-1 group-title="notícias",canal news
            https://two.example/live/news.m3u8
        """.trimIndent()

        val parsed = M3UParser.parse(ByteArrayInputStream(m3uData.toByteArray()), "source_test") { _ -> }

        assertEquals(1, parsed.size)
        assertEquals(listOf("https://two.example/live/news.m3u8"), parsed.single().alternateUrls)
    }

    @Test
    fun testPlaybackSourceFallbackSelectsOnlyUntriedSources() {
        assertEquals(1, PlaybackSourceFallback.nextUntriedSource(3, setOf(0)))
        assertEquals(2, PlaybackSourceFallback.nextUntriedSource(3, setOf(0, 1)))
        assertNull(PlaybackSourceFallback.nextUntriedSource(2, setOf(0, 1)))
    }

    @Test
    fun testPlainUrlFallback() {
        // Check if plain lists containing only raw URLs are parsed as defaults
        val m3uData = """
            http://stream.com/live-channel.m3u8
            https://stream.com/movie-file.mp4
        """.trimIndent()

        val parsed = M3UParser.parse(ByteArrayInputStream(m3uData.toByteArray()), "source_test") { _ -> }
        
        assertEquals(2, parsed.size)
        assertEquals("live-channel", parsed[0].name)
        assertEquals("LIVE", parsed[0].contentType)
        
        assertEquals("movie-file", parsed[1].name)
        assertEquals("MOVIE", parsed[1].contentType)
    }

    @Test
    fun testDiagnosticHTML_Error() {
        val htmlPage = """
            <!DOCTYPE html>
            <html>
                <body><h1>401 Unauthorized</h1><p>Invalid Login details</p></body>
            </html>
        """.trimIndent()

        try {
            M3UParser.parse(ByteArrayInputStream(htmlPage.toByteArray()), "source_test") { _ -> }
            fail("Should have thrown an exception on HTML page response")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("O servidor retornou uma página HTML"))
        }
    }

    @Test
    fun testDiagnosticAuthPlain_Error() {
        val plainError = "invalid username or password"

        try {
            M3UParser.parse(ByteArrayInputStream(plainError.toByteArray()), "source_test") { _ -> }
            fail("Should have thrown an exception on auth error response")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("Usuário ou senha inválidos"))
        }
    }
}
