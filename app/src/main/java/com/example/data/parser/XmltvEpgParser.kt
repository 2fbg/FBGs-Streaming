package com.example.data.parser

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Representa um programa parseado de uma grade XMLTV ou short-EPG Xtream.
 */
data class XmltvProgramme(
    val channelId: String,
    val title: String,
    val description: String,
    val startMs: Long,
    val stopMs: Long,
    val category: String = ""
) {
    fun isLiveAt(nowMs: Long): Boolean = startMs > 0L && stopMs > startMs && nowMs in startMs until stopMs

    fun progressAt(nowMs: Long): Float {
        if (stopMs <= startMs) return 0f
        if (nowMs <= startMs) return 0f
        if (nowMs >= stopMs) return 1f
        return ((nowMs - startMs).toFloat() / (stopMs - startMs).toFloat()).coerceIn(0.02f, 0.99f)
    }

    fun remainingMinutesAt(nowMs: Long): Int {
        if (stopMs <= nowMs) return 0
        return ((stopMs - nowMs) / 60_000L).toInt().coerceAtLeast(1)
    }

    fun formattedRange(locale: Locale = Locale.getDefault()): String {
        if (startMs <= 0L || stopMs <= 0L) return "Ao Vivo"
        val sdf = SimpleDateFormat("HH:mm", locale)
        return "${sdf.format(Date(startMs))} - ${sdf.format(Date(stopMs))}"
    }
}

/**
 * Índice imutável de EPG XMLTV para buscas O(1) por tvg-id, ID XMLTV ou display-name normalizado.
 */
class XmltvIndex(
    val byChannelId: Map<String, List<XmltvProgramme>>,
    val displayNameToId: Map<String, String>,
    val totalProgrammes: Int
) {
    val channelCount: Int get() = byChannelId.size

    fun findProgrammes(
        channelName: String,
        tvgId: String? = null
    ): List<XmltvProgramme> {
        // 1. Tenta pelo tvg-id / epg_channel_id exato ou normalizado
        if (!tvgId.isNullOrBlank()) {
            byChannelId[tvgId.trim()]?.let { return it }
            val normTvg = XmltvEpgParser.normalizeChannelKey(tvgId)
            byChannelId[normTvg]?.let { return it }
            displayNameToId[normTvg]?.let { mapped ->
                byChannelId[mapped]?.let { return it }
            }
        }

        // 2. Tenta pelo nome do canal normalizado (removendo sufixos HD/FHD/4K/H265/BR)
        val normName = XmltvEpgParser.normalizeChannelKey(channelName)
        if (normName.isEmpty()) return emptyList()

        byChannelId[normName]?.let { return it }
        displayNameToId[normName]?.let { mapped ->
            byChannelId[mapped]?.let { return it }
        }

        // 3. Fallback controlado por nome base sem sufixo numérico/qualidade
        val stripped = XmltvEpgParser.stripQualityTokens(normName)
        if (stripped.isNotEmpty() && stripped != normName) {
            byChannelId[stripped]?.let { return it }
            displayNameToId[stripped]?.let { mapped ->
                byChannelId[mapped]?.let { return it }
            }
        }

        return emptyList()
    }

    fun findNowAndNext(
        channelName: String,
        tvgId: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): Pair<XmltvProgramme?, XmltvProgramme?> {
        val list = findProgrammes(channelName, tvgId)
        if (list.isEmpty()) return null to null
        val currentIdx = list.indexOfFirst { it.isLiveAt(nowMs) }
        if (currentIdx >= 0) {
            return list[currentIdx] to list.getOrNull(currentIdx + 1)
        }
        val next = list.firstOrNull { it.startMs > nowMs }
        return null to next
    }
}

/**
 * Parser XMLTV em streaming leve (sem DOMParser) para evitar picos de memória em TV Boxes/celulares.
 * Aplica janela temporal configurável e teto de programas (250k) alinhado à Smart TV.
 */
object XmltvEpgParser {

    const val MAX_PROGRAMMES = 250_000
    private const val DEFAULT_PAST_WINDOW_MS = 6L * 60L * 60L * 1000L   // 6h atrás
    private const val DEFAULT_FUTURE_WINDOW_MS = 48L * 60L * 60L * 1000L // 48h à frente

    private val CHANNEL_ID_ATTR_REGEX = Regex("""id\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val DISPLAY_NAME_REGEX = Regex("""<display-name\b[^>]*>([\s\S]*?)</display-name>""", RegexOption.IGNORE_CASE)
    private val PROG_START_ATTR_REGEX = Regex("""start\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val PROG_STOP_ATTR_REGEX = Regex("""stop\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val PROG_CHANNEL_ATTR_REGEX = Regex("""channel\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val TITLE_REGEX = Regex("""<title\b[^>]*>([\s\S]*?)</title>""", RegexOption.IGNORE_CASE)
    private val DESC_REGEX = Regex("""<desc\b[^>]*>([\s\S]*?)</desc>""", RegexOption.IGNORE_CASE)
    private val CATEGORY_REGEX = Regex("""<category\b[^>]*>([\s\S]*?)</category>""", RegexOption.IGNORE_CASE)
    private val TIMESTAMP_REGEX = Regex("""^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})?\s*([+-]\d{4}|Z)?$""")
    private val QUALITY_TOKENS_REGEX = Regex(
        """\b(fhd|uhd|4k|hd|sd|h265|hevc|h264|60fps|hdr|backup|bkp|alternativo|alt|br|pt|pt-br|dub|leg|nacional|regional)\b""",
        RegexOption.IGNORE_CASE
    )
    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val NON_ALNUM_REGEX = Regex("[^a-z0-9]+")
    private val NUMERIC_ENTITY_REGEX = Regex("&#(\\d+);")
    private val HEX_ENTITY_REGEX = Regex("&#x([0-9a-fA-F]+);", RegexOption.IGNORE_CASE)

    fun parse(
        inputStream: InputStream,
        nowMs: Long = System.currentTimeMillis(),
        applyTimeWindow: Boolean = true,
        maxProgrammes: Int = MAX_PROGRAMMES
    ): XmltvIndex {
        val byChannel = HashMap<String, MutableList<XmltvProgramme>>(512)
        val displayMap = HashMap<String, String>(1024)
        val minKeepMs = if (applyTimeWindow) nowMs - DEFAULT_PAST_WINDOW_MS else Long.MIN_VALUE
        val maxKeepMs = if (applyTimeWindow) nowMs + DEFAULT_FUTURE_WINDOW_MS else Long.MAX_VALUE

        var totalProgrammes = 0
        val buffer = StringBuilder(4096)
        var mode = 0 // 0=idle, 1=channel, 2=programme

        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 64 * 1024).use { reader ->
            while (totalProgrammes < maxProgrammes) {
                val rawLine = reader.readLine() ?: break
                var remaining = rawLine

                while (remaining.isNotEmpty() && totalProgrammes < maxProgrammes) {
                    if (mode == 0) {
                        val chIdx = remaining.indexOf("<channel", ignoreCase = true)
                        val prIdx = remaining.indexOf("<programme", ignoreCase = true)
                        val nextIdx: Int
                        val nextMode: Int
                        when {
                            chIdx >= 0 && (prIdx < 0 || chIdx < prIdx) -> {
                                nextIdx = chIdx
                                nextMode = 1
                            }
                            prIdx >= 0 -> {
                                nextIdx = prIdx
                                nextMode = 2
                            }
                            else -> break
                        }
                        buffer.setLength(0)
                        mode = nextMode
                        remaining = remaining.substring(nextIdx)
                    }

                    val closeTag = if (mode == 1) "</channel>" else "</programme>"
                    val closeIdx = remaining.indexOf(closeTag, ignoreCase = true)
                    if (closeIdx >= 0) {
                        val endPos = closeIdx + closeTag.length
                        buffer.append(remaining, 0, endPos)
                        val block = buffer.toString()
                        if (mode == 1) {
                            processChannelBlock(block, displayMap)
                        } else {
                            if (processProgrammeBlock(block, byChannel, minKeepMs, maxKeepMs)) {
                                totalProgrammes++
                            }
                        }
                        buffer.setLength(0)
                        mode = 0
                        remaining = remaining.substring(endPos)
                    } else {
                        // Proteção contra blocos malformados gigantes
                        if (buffer.length < 32_768) {
                            buffer.append(remaining).append('\n')
                        } else {
                            buffer.setLength(0)
                            mode = 0
                        }
                        break
                    }
                }
            }
        }

        // Ordena a grade cronologicamente por canal
        val sortedMap = HashMap<String, List<XmltvProgramme>>(byChannel.size)
        for ((key, list) in byChannel) {
            list.sortBy { it.startMs }
            sortedMap[key] = list
        }

        return XmltvIndex(
            byChannelId = sortedMap,
            displayNameToId = displayMap,
            totalProgrammes = totalProgrammes
        )
    }

    fun parseString(
        xml: String,
        nowMs: Long = System.currentTimeMillis(),
        applyTimeWindow: Boolean = true,
        maxProgrammes: Int = MAX_PROGRAMMES
    ): XmltvIndex {
        return parse(
            inputStream = xml.byteInputStream(Charsets.UTF_8),
            nowMs = nowMs,
            applyTimeWindow = applyTimeWindow,
            maxProgrammes = maxProgrammes
        )
    }

    private fun processChannelBlock(block: String, displayMap: MutableMap<String, String>) {
        val rawId = CHANNEL_ID_ATTR_REGEX.find(block)?.groupValues?.getOrNull(1)?.trim() ?: return
        if (rawId.isEmpty()) return
        val normId = normalizeChannelKey(rawId)
        if (normId.isNotEmpty()) {
            displayMap.putIfAbsent(normId, normId)
            val strippedId = stripQualityTokens(normId)
            if (strippedId.isNotEmpty()) {
                displayMap.putIfAbsent(strippedId, normId)
            }
        }
        DISPLAY_NAME_REGEX.findAll(block).forEach { match ->
            val rawName = unescapeXml(match.groupValues[1]).trim()
            val normName = normalizeChannelKey(rawName)
            if (normName.isNotEmpty()) {
                displayMap.putIfAbsent(normName, normId)
                val stripped = stripQualityTokens(normName)
                if (stripped.isNotEmpty()) {
                    displayMap.putIfAbsent(stripped, normId)
                }
            }
        }
    }

    private fun processProgrammeBlock(
        block: String,
        byChannel: MutableMap<String, MutableList<XmltvProgramme>>,
        minKeepMs: Long,
        maxKeepMs: Long
    ): Boolean {
        val rawChannel = PROG_CHANNEL_ATTR_REGEX.find(block)?.groupValues?.getOrNull(1)?.trim() ?: return false
        val rawStart = PROG_START_ATTR_REGEX.find(block)?.groupValues?.getOrNull(1)?.trim() ?: return false
        val rawStop = PROG_STOP_ATTR_REGEX.find(block)?.groupValues?.getOrNull(1)?.trim() ?: ""

        val startMs = parseXmltvTimestamp(rawStart)
        if (startMs <= 0L) return false
        val parsedStop = parseXmltvTimestamp(rawStop)
        val stopMs = if (parsedStop > startMs) parsedStop else startMs + 30L * 60L * 1000L

        if (stopMs < minKeepMs || startMs > maxKeepMs) return false

        val title = TITLE_REGEX.find(block)?.groupValues?.getOrNull(1)
            ?.let(::unescapeXml)?.trim().orEmpty()
        if (title.isEmpty()) return false

        val desc = DESC_REGEX.find(block)?.groupValues?.getOrNull(1)
            ?.let(::unescapeXml)?.trim().orEmpty()
        val category = CATEGORY_REGEX.find(block)?.groupValues?.getOrNull(1)
            ?.let(::unescapeXml)?.trim().orEmpty()

        val normChannel = normalizeChannelKey(rawChannel)
        if (normChannel.isEmpty()) return false

        val programme = XmltvProgramme(
            channelId = rawChannel,
            title = title,
            description = desc.ifEmpty { "Programação ao vivo transmitida pelo canal." },
            startMs = startMs,
            stopMs = stopMs,
            category = category
        )

        val list = byChannel.getOrPut(normChannel) { ArrayList(16) }
        list.add(programme)
        return true
    }

    /**
     * Converte timestamp XMLTV (`20261118193000 -0300`, `20261118193000 +0000`, `20261118193000`) para epoch ms.
     */
    fun parseXmltvTimestamp(raw: String): Long {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return 0L
        val m = TIMESTAMP_REGEX.matchEntire(trimmed) ?: return 0L

        val year = m.groupValues[1].toIntOrNull() ?: return 0L
        val month = (m.groupValues[2].toIntOrNull() ?: return 0L) - 1
        val day = m.groupValues[3].toIntOrNull() ?: return 0L
        val hour = m.groupValues[4].toIntOrNull() ?: return 0L
        val minute = m.groupValues[5].toIntOrNull() ?: return 0L
        val second = m.groupValues[6].toIntOrNull() ?: 0
        val tzPart = m.groupValues[7]

        val tz = when {
            tzPart.isEmpty() -> TimeZone.getDefault()
            tzPart == "Z" -> TimeZone.getTimeZone("UTC")
            else -> TimeZone.getTimeZone("GMT$tzPart")
        }

        val cal = Calendar.getInstance(tz)
        cal.clear()
        cal.set(year, month, day, hour, minute, second)
        return cal.timeInMillis
    }

    fun normalizeChannelKey(raw: String): String {
        if (raw.isBlank()) return ""
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
        val noAccents = DIACRITICS_REGEX.replace(decomposed, "")
        return NON_ALNUM_REGEX.replace(noAccents.lowercase(Locale.ROOT), " ").trim()
    }

    fun stripQualityTokens(normalized: String): String {
        if (normalized.isBlank()) return ""
        val stripped = QUALITY_TOKENS_REGEX.replace(normalized, " ")
        return stripped.replace(Regex("\\s+"), " ").trim()
    }

    fun unescapeXml(raw: String): String {
        if (!raw.contains('&')) return raw
        var out = raw
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
        out = NUMERIC_ENTITY_REGEX.replace(out) { m ->
            val code = m.groupValues[1].toIntOrNull()
            if (code != null && code in 1..0xFFFF) code.toChar().toString() else m.value
        }
        out = HEX_ENTITY_REGEX.replace(out) { m ->
            val code = m.groupValues[1].toIntOrNull(16)
            if (code != null && code in 1..0xFFFF) code.toChar().toString() else m.value
        }
        return out
    }
}
