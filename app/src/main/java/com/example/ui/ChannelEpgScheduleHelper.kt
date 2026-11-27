package com.example.ui

import com.example.data.parser.XmltvProgramme
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Representa um dia de programação agrupado a partir da lista XMLTV do canal.
 */
data class EpgDayGroup(
    val dayKey: String,
    val label: String,
    val programmes: List<XmltvProgramme>,
    val isToday: Boolean
)

/**
 * Auxiliares puros (testáveis em JVM) para organização cronológica por dia,
 * busca tolerante a acentos e formatação de duração da grade XMLTV de um canal.
 */
object ChannelEpgScheduleHelper {

    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")

    fun normalizeQuery(raw: String): String {
        if (raw.isBlank()) return ""
        val decomposed = Normalizer.normalize(raw.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return DIACRITICS_REGEX.replace(decomposed, "")
    }

    fun dayKeyFor(epochMs: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = epochMs }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(Locale.US, year, month, day)
    }

    fun groupByDay(
        programmes: List<XmltvProgramme>,
        nowMs: Long = System.currentTimeMillis(),
        todayLabel: String = "Hoje",
        tomorrowLabel: String = "Amanhã",
        yesterdayLabel: String = "Ontem",
        locale: Locale = Locale("pt", "BR"),
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<EpgDayGroup> {
        if (programmes.isEmpty()) return emptyList()

        val sorted = programmes.sortedBy { it.startMs }
        val todayCal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMs }
        val todayKey = dayKeyFor(todayCal.timeInMillis, timeZone)

        val tomorrowCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowKey = dayKeyFor(tomorrowCal.timeInMillis, timeZone)

        val yesterdayCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayKey = dayKeyFor(yesterdayCal.timeInMillis, timeZone)

        val dayFormatter = SimpleDateFormat("EEE, dd/MM", locale).apply {
            this.timeZone = timeZone
        }

        val grouped = LinkedHashMap<String, MutableList<XmltvProgramme>>()
        for (prog in sorted) {
            val key = dayKeyFor(prog.startMs, timeZone)
            grouped.getOrPut(key) { ArrayList() }.add(prog)
        }

        return grouped.entries.map { (key, list) ->
            val label = when (key) {
                todayKey -> todayLabel
                tomorrowKey -> tomorrowLabel
                yesterdayKey -> yesterdayLabel
                else -> {
                    val formatted = dayFormatter.format(Date(list.first().startMs))
                        .replace(".", "")
                    formatted.replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString()
                    }
                }
            }
            EpgDayGroup(
                dayKey = key,
                label = label,
                programmes = list,
                isToday = key == todayKey
            )
        }
    }

    fun filterProgrammes(
        programmes: List<XmltvProgramme>,
        query: String
    ): List<XmltvProgramme> {
        val norm = normalizeQuery(query)
        if (norm.isEmpty()) return programmes
        return programmes.filter { prog ->
            normalizeQuery(prog.title).contains(norm) ||
                normalizeQuery(prog.description).contains(norm) ||
                normalizeQuery(prog.category).contains(norm)
        }
    }

    /**
     * Retorna o índice do programa atualmente no ar, ou do próximo a começar se nenhum estiver ativo.
     */
    fun findFocusIndex(programmes: List<XmltvProgramme>, nowMs: Long): Int {
        if (programmes.isEmpty()) return -1
        val liveIdx = programmes.indexOfFirst { it.isLiveAt(nowMs) }
        if (liveIdx >= 0) return liveIdx
        val nextIdx = programmes.indexOfFirst { it.startMs > nowMs }
        return if (nextIdx >= 0) nextIdx else 0
    }

    fun formatDurationMinutes(startMs: Long, stopMs: Long): String {
        if (stopMs <= startMs) return ""
        val totalMinutes = ((stopMs - startMs) / 60_000L).toInt().coerceAtLeast(1)
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}min"
            hours > 0 -> "${hours}h"
            else -> "${mins} min"
        }
    }
}
