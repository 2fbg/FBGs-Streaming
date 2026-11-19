package com.example

import com.example.data.model.PlaylistItem
import com.example.data.parser.XmltvEpgParser
import com.example.data.parser.XtreamCatalogClient
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class XmltvAndXtreamTest {

    @Test
    fun `xmltv parser indexes channels by id and display-name and matches stripped quality suffixes`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="GloboSP.br">
                <display-name>TV Globo São Paulo</display-name>
              </channel>
              <channel id="ESPN.br">
                <display-name>ESPN Brasil</display-name>
              </channel>
              <programme start="20261118190000 -0300" stop="20261118200000 -0300" channel="GloboSP.br">
                <title>Novela &amp; Companhia</title>
                <desc>Capítulo especial &lt;HD&gt;</desc>
                <category>Novela</category>
              </programme>
              <programme start="20261118200000 -0300" stop="20261118210000 -0300" channel="GloboSP.br">
                <title>Jornal Nacional</title>
                <desc>Principais notícias do dia.</desc>
              </programme>
              <programme start="20261118193000 -0300" stop="20261118213000 -0300" channel="ESPN.br">
                <title>SportsCenter</title>
                <desc>Resumo dos esportes.</desc>
              </programme>
            </tv>
        """.trimIndent()

        val nowMs = XmltvEpgParser.parseXmltvTimestamp("20261118193000 -0300")
        val index = XmltvEpgParser.parseString(xml, nowMs = nowMs, applyTimeWindow = true)

        assertEquals(2, index.channelCount)
        assertEquals(3, index.totalProgrammes)

        // Match via tvg-id exato
        val byTvgId = index.findProgrammes("Qualquer Nome", tvgId = "GloboSP.br")
        assertEquals(2, byTvgId.size)
        assertEquals("Novela & Companhia", byTvgId[0].title)
        assertEquals("Capítulo especial <HD>", byTvgId[0].description)

        // Match via display-name normalizado removendo sufixos FHD/4K/H265
        val (nowProg, nextProg) = index.findNowAndNext("TV Globo Sao Paulo FHD H265", nowMs = nowMs)
        assertNotNull(nowProg)
        assertEquals("Novela & Companhia", nowProg?.title)
        assertEquals(0.5f, nowProg!!.progressAt(nowMs), 0.02f)
        assertEquals(30, nowProg.remainingMinutesAt(nowMs))

        assertNotNull(nextProg)
        assertEquals("Jornal Nacional", nextProg?.title)

        // Match ESPN sem acento e com tag 4K
        val espnProgs = index.findProgrammes("ESPN Brasil 4K")
        assertEquals(1, espnProgs.size)
        assertEquals("SportsCenter", espnProgs[0].title)
    }

    @Test
    fun `xmltv parser respects maxProgrammes cap and filters out programmes outside time window`() {
        val xml = """
            <tv>
              <programme start="20261110100000 +0000" stop="20261110110000 +0000" channel="old.ch">
                <title>Antigo</title>
              </programme>
              <programme start="20261118120000 +0000" stop="20261118130000 +0000" channel="cur.ch">
                <title>Atual 1</title>
              </programme>
              <programme start="20261118130000 +0000" stop="20261118140000 +0000" channel="cur.ch">
                <title>Atual 2</title>
              </programme>
            </tv>
        """.trimIndent()

        val nowMs = XmltvEpgParser.parseXmltvTimestamp("20261118123000 +0000")
        val windowed = XmltvEpgParser.parseString(xml, nowMs = nowMs, applyTimeWindow = true)
        assertEquals(2, windowed.totalProgrammes)
        assertTrue(windowed.findProgrammes("old.ch").isEmpty())

        val capped = XmltvEpgParser.parseString(xml, nowMs = nowMs, applyTimeWindow = true, maxProgrammes = 1)
        assertEquals(1, capped.totalProgrammes)
    }

    @Test
    fun `xtream client checks auth errors and parses live, vod, series, and series_info`() {
        assertNotNull(XtreamCatalogClient.checkAuthError("""{"user_info":{"auth":0}}"""))
        assertNotNull(XtreamCatalogClient.checkAuthError("""{"user_info":{"auth":1,"status":"Expired"}}"""))
        assertNull(XtreamCatalogClient.checkAuthError("""{"user_info":{"auth":1,"status":"Active"}}"""))

        val liveArr = JSONArray(
            """
            [
              {"stream_id": 101, "name": "Globo HD", "category_id": "1", "stream_icon": "https://img/globo.png", "epg_channel_id": "Globo.br"},
              {"stream_id": 102, "name": "Canal 18+ VIP", "category_id": "2", "stream_icon": ""}
            ]
            """.trimIndent()
        )
        val items = mutableListOf<PlaylistItem>()
        val epgByUrl = mutableMapOf<String, String>()
        val epgByName = mutableMapOf<String, String>()
        val liveCount = XtreamCatalogClient.parseLiveStreams(
            array = liveArr,
            categories = mapOf("1" to "Abertos", "2" to "Adulto"),
            cleanBase = "http://srv.example:8080",
            encUser = "usr",
            encPass = "pwd",
            liveExt = "ts",
            playlistSource = "Srv1",
            outItems = items,
            outEpgByUrl = epgByUrl,
            outEpgByName = epgByName
        )

        assertEquals(2, liveCount)
        assertEquals("http://srv.example:8080/live/usr/pwd/101.ts", items[0].url)
        assertEquals("Globo.br", epgByUrl[items[0].url])
        assertFalse(items[0].isAdult)
        assertTrue(items[1].isAdult)

        val vodArr = JSONArray(
            """[{"stream_id": 555, "name": "Duna 2", "category_id": "10", "container_extension": "mkv"}]"""
        )
        val vodCount = XtreamCatalogClient.parseVodStreams(
            array = vodArr,
            categories = mapOf("10" to "Lançamentos"),
            cleanBase = "http://srv.example:8080",
            encUser = "usr",
            encPass = "pwd",
            playlistSource = "Srv1",
            outItems = items
        )
        assertEquals(1, vodCount)
        assertEquals("http://srv.example:8080/movie/usr/pwd/555.mkv", items[2].url)
        assertEquals("MOVIE", items[2].contentType)

        val seriesArr = JSONArray(
            """[{"series_id": 900, "name": "Breaking Bad", "category_id": "20", "cover": "https://img/bb.jpg"}]"""
        )
        val seriesCount = XtreamCatalogClient.parseSeriesStreams(
            array = seriesArr,
            categories = mapOf("20" to "Drama"),
            cleanBase = "http://srv.example:8080",
            encUser = "usr",
            encPass = "pwd",
            playlistSource = "Srv1",
            outItems = items
        )
        assertEquals(1, seriesCount)
        assertEquals("900", XtreamCatalogClient.extractXtreamSeriesId(items[3].url))
        assertNull(XtreamCatalogClient.extractXtreamSeriesId("http://srv.example:8080/series/usr/pwd/900.mp4"))

        val seriesInfoJson = """
            {
              "episodes": {
                "2": [{"id": "201", "episode_num": 1, "title": "Seven Thirty-Seven", "container_extension": "mkv"}],
                "1": [{"id": "101", "episode_num": 1, "title": "Pilot", "container_extension": "mp4"}]
              }
            }
        """.trimIndent()
        val episodes = XtreamCatalogClient.parseSeriesInfoJson(
            jsonString = seriesInfoJson,
            cleanBase = "http://srv.example:8080",
            encUser = "usr",
            encPass = "pwd",
            seriesName = "Breaking Bad",
            category = "Drama",
            logoUrl = "https://img/bb.jpg",
            playlistSource = "Srv1"
        )
        assertEquals(2, episodes.size)
        assertTrue(episodes[0].name.contains("S01E01"))
        assertEquals("http://srv.example:8080/series/usr/pwd/101.mp4", episodes[0].url)
        assertTrue(episodes[1].name.contains("S02E01"))
        assertEquals("http://srv.example:8080/series/usr/pwd/201.mkv", episodes[1].url)
    }

    @Test
    fun `xtream short epg decodes base64 titles and descriptions safely`() {
        val encTitle = Base64.getEncoder().encodeToString("Jornal da Noite".toByteArray(Charsets.UTF_8))
        val encDesc = Base64.getEncoder().encodeToString("Cobertura ao vivo com análise.".toByteArray(Charsets.UTF_8))
        val json = """
            {
              "epg_listings": [
                {
                  "title": "$encTitle",
                  "description": "$encDesc",
                  "start_timestamp": 1763500000,
                  "stop_timestamp": 1763503600
                }
              ]
            }
        """.trimIndent()

        val list = XtreamCatalogClient.parseShortEpgJson(json, "101")
        assertEquals(1, list.size)
        assertEquals("Jornal da Noite", list[0].title)
        assertEquals("Cobertura ao vivo com análise.", list[0].description)
        assertEquals(1763500000_000L, list[0].startMs)
        assertEquals(1763503600_000L, list[0].stopMs)
    }

    @Test
    fun `public epg json parser parses valid programs sorted by start and skips malformed entries`() {
        val json = """
            {
              "source": "meuguia.tv",
              "slug": "GRD",
              "programs": [
                {"title": "Novela das Nove", "desc": "Novela", "start": 1763503600000, "stop": 1763507200000},
                {"title": "", "desc": "Invalido", "start": 1763500000000, "stop": 1763503600000},
                {"title": "Jornal Nacional", "desc": "Jornalismo", "start": 1763500000000, "stop": 1763503600000}
              ]
            }
        """.trimIndent()

        val parsed = com.example.data.service.AndroidEpgManager.parsePublicEpgJson(json, "globo")
        assertEquals(2, parsed.size)
        assertEquals("Jornal Nacional", parsed[0].title)
        assertEquals("Jornalismo", parsed[0].description)
        assertEquals("Novela das Nove", parsed[1].title)
        assertTrue(com.example.data.service.AndroidEpgManager.parsePublicEpgJson("not json", "globo").isEmpty())
    }
}

