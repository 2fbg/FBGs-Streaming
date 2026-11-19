package com.example

import com.example.ai.CopilotEpgDayBucket
import com.example.ai.CopilotEpgMatcher
import com.example.data.model.ContentType
import com.example.data.model.PlaylistItem
import com.example.data.parser.XmltvProgramme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopilotEpgMatcherTest {

    private val baseNowMs = 1_760_000_000_000L // Timestamp fixo para testes determinísticos

    private fun liveChannel(
        name: String,
        url: String,
        category: String = "ESPORTES"
    ) = PlaylistItem(
        name = name,
        url = url,
        category = category,
        contentType = ContentType.LIVE.name,
        playlistSource = "MK21"
    )

    @Test
    fun extractIntent_recognizesTeamAliasesAndAccents() {
        val intent1 = CopilotEpgMatcher.extractIntent("onde passa o jogo do Mengão agora?")
        assertTrue(intent1.canonicalTeams.contains("Flamengo"))
        assertTrue(intent1.isLiveNowIntent)
        assertTrue(intent1.isSportsIntent)
        assertTrue(intent1.shouldSearchEpg)

        val intent2 = CopilotEpgMatcher.extractIntent("São Paulo x Timão")
        assertEquals(listOf("São Paulo", "Corinthians"), intent2.canonicalTeams)
        assertTrue(intent2.shouldSearchEpg)
    }

    @Test
    fun matchProgrammes_returnsLiveNowBeforeUpcomingAndPrefersHigherQuality() {
        val sportvSd = liveChannel("SporTV SD", "http://stream/sportv-sd")
        val sportv4k = liveChannel("SporTV 4K", "http://stream/sportv-4k")
        val premiere = liveChannel("Premiere Clubes FHD", "http://stream/premiere-fhd")
        val espn = liveChannel("ESPN HD", "http://stream/espn-hd")

        val liveStart = baseNowMs - 30 * 60_000L
        val liveStop = baseNowMs + 60 * 60_000L
        val upcomingStart = baseNowMs + 3 * 60 * 60_000L
        val upcomingStop = baseNowMs + 5 * 60 * 60_000L

        val epgByUrl = mapOf(
            sportvSd.url to listOf(
                XmltvProgramme("sportv", "Brasileirão: Flamengo x Palmeiras", "Ao vivo do Maracanã", liveStart, liveStop)
            ),
            sportv4k.url to listOf(
                XmltvProgramme("sportv", "Brasileirão: Flamengo x Palmeiras", "Ao vivo do Maracanã", liveStart, liveStop)
            ),
            premiere.url to listOf(
                XmltvProgramme("premiere", "Flamengo x Palmeiras - Ao Vivo", "Rodada 28", liveStart, liveStop)
            ),
            espn.url to listOf(
                XmltvProgramme("espn", "SportsCenter - Pós-Jogo Flamengo", "Análise completa", upcomingStart, upcomingStop)
            )
        )

        val result = CopilotEpgMatcher.matchProgrammes(
            prompt = "Flamengo agora",
            channels = listOf(sportvSd, sportv4k, premiere, espn),
            nowMs = baseNowMs
        ) { ch -> epgByUrl[ch.url].orEmpty() }

        assertEquals(listOf("Flamengo"), result.detectedTerms)
        assertEquals(2, result.liveNowMatches.size)
        // SporTV SD e SporTV 4K exibem o mesmo programa: deve deduplicar mantendo 4K
        assertTrue(result.liveNowMatches.any { it.item.name == "SporTV 4K" && it.qualityBadge == "4K" })
        assertFalse(result.liveNowMatches.any { it.item.name == "SporTV SD" })
        assertTrue(result.liveNowMatches.any { it.item.name == "Premiere Clubes FHD" })
        assertTrue(result.liveNowMatches.all { it.isLiveNow && it.remainingMinutes == 60 && it.dayBucket == CopilotEpgDayBucket.LIVE_NOW })

        assertEquals(1, result.upcomingMatches.size)
        assertEquals("ESPN HD", result.upcomingMatches.first().item.name)
        assertFalse(result.upcomingMatches.first().isLiveNow)

        val promptContext = CopilotEpgMatcher.buildEpgPromptContext(result, baseNowMs)
        assertTrue(promptContext.contains("[NO AR AGORA"))
        assertTrue(promptContext.contains("SporTV 4K"))
        assertTrue(promptContext.contains("PRÓXIMOS 7 DIAS"))
        assertTrue(promptContext.contains("ESPN HD"))
    }

    @Test
    fun matchProgrammes_coversFullSevenDayWeekWindowAndExcludesBeyondSevenDays() {
        val premiere = liveChannel("Premiere 1 4K", "http://stream/p1")
        val sportv = liveChannel("SporTV 1 4K", "http://stream/s1")
        val dayMs = 24L * 60L * 60L * 1000L

        val tomorrowStart = baseNowMs + dayMs + 2 * 60 * 60_000L
        val fiveDaysAheadStart = baseNowMs + 5 * dayMs
        val nineDaysAheadStart = baseNowMs + 9 * dayMs

        val epgByUrl = mapOf(
            premiere.url to listOf(
                XmltvProgramme("p1", "Palmeiras x Santos", "Paulistão", tomorrowStart, tomorrowStart + 2 * 60 * 60_000L),
                XmltvProgramme("p1", "Palmeiras x Grêmio", "Brasileirão", nineDaysAheadStart, nineDaysAheadStart + 2 * 60 * 60_000L)
            ),
            sportv.url to listOf(
                XmltvProgramme("s1", "Libertadores: Palmeiras x River Plate", "Quartas", fiveDaysAheadStart, fiveDaysAheadStart + 2 * 60 * 60_000L)
            )
        )

        val result = CopilotEpgMatcher.matchProgrammes(
            prompt = "Palmeiras na semana",
            channels = listOf(premiere, sportv),
            nowMs = baseNowMs
        ) { epgByUrl[it.url].orEmpty() }

        assertEquals(2, result.upcomingMatches.size)
        assertEquals("Palmeiras x Santos", result.upcomingMatches[0].programTitle)
        assertEquals(CopilotEpgDayBucket.TOMORROW, result.upcomingMatches[0].dayBucket)
        assertEquals("AMANHÃ", result.upcomingMatches[0].dayLabel)

        assertEquals("Libertadores: Palmeiras x River Plate", result.upcomingMatches[1].programTitle)
        assertEquals(CopilotEpgDayBucket.THIS_WEEK, result.upcomingMatches[1].dayBucket)
    }

    @Test
    fun deduplicateChannelsByFamily_removesDuplicateGloboVariantsAndKeepsBestQuality() {
        val channels = listOf(
            liveChannel("GLOBO SP SD", "http://1", "GLOBO"),
            liveChannel("GLOBO SP HD [ALT]", "http://2", "GLOBO"),
            liveChannel("GLOBO SP FHD¹", "http://3", "GLOBO"),
            liveChannel("GLOBO SP 4k", "http://4", "CANAIS 4K"),
            liveChannel("SPORTV 1 4k", "http://5", "CANAIS 4K"),
            liveChannel("PREMIERE 1 4K", "http://6", "CANAIS 4K")
        )

        val deduped = CopilotEpgMatcher.deduplicateChannelsByFamily(channels, maxCount = 6)
        assertEquals(3, deduped.size)
        assertEquals("PREMIERE 1 4K", deduped[0].name)
        assertEquals("SPORTV 1 4k", deduped[1].name)
        assertEquals("GLOBO SP 4k", deduped[2].name)
    }

    @Test
    fun matchProgrammes_shortAliasUsesWordBoundaryToAvoidFalsePositives() {
        val globo = liveChannel("Globo SP HD", "http://stream/globo", category = "ABERTOS")
        val liveStart = baseNowMs - 15 * 60_000L
        val liveStop = baseNowMs + 45 * 60_000L

        val result = CopilotEpgMatcher.matchProgrammes(
            prompt = "jogo do Inter",
            channels = listOf(globo),
            nowMs = baseNowMs
        ) {
            listOf(
                XmltvProgramme(
                    "globo",
                    "Entrevista Interessante de Domingo",
                    "Conversa sobre cinema internacionalizado",
                    liveStart,
                    liveStop
                )
            )
        }

        assertFalse(result.hasMatches)
    }

    @Test
    fun matchProgrammes_broadLiveSportsQueryFindsLiveGames() {
        val premiere = liveChannel("Premiere 2 FHD", "http://stream/p2")
        val telecine = liveChannel("Telecine Action HD", "http://stream/tc", category = "FILMES")
        val liveStart = baseNowMs - 20 * 60_000L
        val liveStop = baseNowMs + 70 * 60_000L

        val epg = mapOf(
            premiere.url to listOf(
                XmltvProgramme("p2", "Campeonato Brasileiro: Bahia x Fortaleza", "Ao vivo", liveStart, liveStop)
            ),
            telecine.url to listOf(
                XmltvProgramme("tc", "Velozes e Furiosos", "Ação", liveStart, liveStop)
            )
        )

        val result = CopilotEpgMatcher.matchProgrammes(
            prompt = "Jogos passando agora no EPG",
            channels = listOf(premiere, telecine),
            nowMs = baseNowMs
        ) { epg[it.url].orEmpty() }

        assertEquals(1, result.liveNowMatches.size)
        assertEquals("Premiere 2 FHD", result.liveNowMatches.first().item.name)
    }

    @Test
    fun selectPriorityEpgChannels_deduplicatesByChannelFamilyAndPrefersHigherQuality() {
        val channels = listOf(
            liveChannel("SporTV SD", "http://1"),
            liveChannel("SporTV 4K", "http://2"),
            liveChannel("Premiere HD", "http://3"),
            liveChannel("Premiere FHD", "http://4"),
            liveChannel("ESPN HD", "http://5")
        )

        val picked = CopilotEpgMatcher.selectPriorityEpgChannels(channels, maxCount = 5)
        assertEquals(3, picked.size)
        assertEquals("Premiere FHD", picked[0].name)
        assertEquals("SporTV 4K", picked[1].name)
        assertEquals("ESPN HD", picked[2].name)
    }
}
