package com.fbg.streaming

import com.example.data.parser.XmltvEpgParser
import com.example.data.parser.XmltvProgramme
import com.example.ui.ChannelEpgScheduleHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class ChannelEpgScheduleHelperTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val ptBr = Locale("pt", "BR")

    private fun prog(
        title: String,
        startIso: String,
        stopIso: String,
        desc: String = "",
        category: String = ""
    ): XmltvProgramme {
        return XmltvProgramme(
            channelId = "globo.br",
            title = title,
            description = desc,
            startMs = XmltvEpgParser.parseXmltvTimestamp(startIso),
            stopMs = XmltvEpgParser.parseXmltvTimestamp(stopIso),
            category = category
        )
    }

    @Test
    fun `groupByDay groups XMLTV programmes into yesterday today tomorrow and future days`() {
        val nowMs = XmltvEpgParser.parseXmltvTimestamp("20261127143000 +0000")
        val items = listOf(
            prog("Jornal da Noite", "20261126220000 +0000", "20261126230000 +0000"),
            prog("Jornal Hoje", "20261127140000 +0000", "20261127150000 +0000"),
            prog("Sessão da Tarde", "20261127150000 +0000", "20261127170000 +0000"),
            prog("Bom Dia", "20261128090000 +0000", "20261128100000 +0000"),
            prog("Domingo Espetacular", "20261129200000 +0000", "20261129220000 +0000")
        )

        val groups = ChannelEpgScheduleHelper.groupByDay(
            programmes = items,
            nowMs = nowMs,
            locale = ptBr,
            timeZone = utc
        )

        assertEquals(4, groups.size)
        assertEquals("Ontem", groups[0].label)
        assertFalse(groups[0].isToday)
        assertEquals(1, groups[0].programmes.size)

        assertEquals("Hoje", groups[1].label)
        assertTrue(groups[1].isToday)
        assertEquals(2, groups[1].programmes.size)

        assertEquals("Amanhã", groups[2].label)
        assertEquals(1, groups[2].programmes.size)

        assertEquals("2026-11-29", groups[3].dayKey)
        assertTrue(groups[3].label.contains("29/11"))
    }

    @Test
    fun `filterProgrammes matches title description and category accent-insensitively`() {
        val items = listOf(
            prog("Futebol Ao Vivo", "20261127190000 +0000", "20261127210000 +0000", "Final do Brasileirão", "Esportes"),
            prog("Jornal Nacional", "20261127203000 +0000", "20261127213000 +0000", "Notícias do dia", "Jornalismo"),
            prog("Cinema Especial", "20261127220000 +0000", "20261128000000 +0000", "Ação e aventura", "Filmes")
        )

        assertEquals(1, ChannelEpgScheduleHelper.filterProgrammes(items, "brasileirao").size)
        assertEquals(1, ChannelEpgScheduleHelper.filterProgrammes(items, "noticias").size)
        assertEquals(1, ChannelEpgScheduleHelper.filterProgrammes(items, "acao").size)
        assertEquals(1, ChannelEpgScheduleHelper.filterProgrammes(items, "ESPORTES").size)
        assertEquals(3, ChannelEpgScheduleHelper.filterProgrammes(items, "   ").size)
    }

    @Test
    fun `findFocusIndex returns currently live programme or next upcoming programme`() {
        val items = listOf(
            prog("Passado", "20261127100000 +0000", "20261127110000 +0000"),
            prog("Atual", "20261127120000 +0000", "20261127130000 +0000"),
            prog("Futuro", "20261127140000 +0000", "20261127150000 +0000")
        )

        val duringLive = XmltvEpgParser.parseXmltvTimestamp("20261127123000 +0000")
        assertEquals(1, ChannelEpgScheduleHelper.findFocusIndex(items, duringLive))

        val betweenShows = XmltvEpgParser.parseXmltvTimestamp("20261127133000 +0000")
        assertEquals(2, ChannelEpgScheduleHelper.findFocusIndex(items, betweenShows))
    }

    @Test
    fun `formatDurationMinutes formats minutes and hours cleanly`() {
        val t0 = 1_700_000_000_000L
        assertEquals("45 min", ChannelEpgScheduleHelper.formatDurationMinutes(t0, t0 + 45 * 60_000L))
        assertEquals("1h", ChannelEpgScheduleHelper.formatDurationMinutes(t0, t0 + 60 * 60_000L))
        assertEquals("1h 30min", ChannelEpgScheduleHelper.formatDurationMinutes(t0, t0 + 90 * 60_000L))
        assertEquals("", ChannelEpgScheduleHelper.formatDurationMinutes(t0, t0))
    }
}
