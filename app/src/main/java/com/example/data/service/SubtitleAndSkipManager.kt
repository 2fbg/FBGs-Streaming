package com.example.data.service

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.roundToLong

data class SubtitleSearchItem(
    val fileId: Long,
    val fileName: String,
    val release: String,
    val language: String,
    val downloads: Int = 0,
    val fps: Double = 0.0
)

data class SubtitleSearchResult(
    val ok: Boolean,
    val items: List<SubtitleSearchItem> = emptyList(),
    val error: String? = null
)

data class SubtitleDownloadResult(
    val ok: Boolean,
    val vttContent: String = "",
    val error: String? = null
)

sealed class VodSkipPrompt(val label: String, val targetMs: Long) {
    class SkipIntro(targetMs: Long) : VodSkipPrompt("Pular Abertura", targetMs)
    class SkipCredits(targetMs: Long) : VodSkipPrompt("Pular Créditos", targetMs)
}

object SubtitleAndSkipManager {
    const val DEFAULT_SUBTITLES_ENDPOINT = "https://bgstreaming.vercel.app/api/subtitles"
    private const val MAX_OFFSET_SEC = 60.0

    private val CUE_TIMESTAMP_LINE = Regex(
        "^\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3}\\s*-->\\s*\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3}"
    )
    private val SRT_COMMA_REGEX = Regex("(\\d{1,2}:\\d{2}:\\d{2}),(\\d{1,3})")
    private val VTT_RANGE_REGEX = Regex(
        "(\\d{1,2}:\\d{2}:\\d{2}\\.\\d{1,3})(\\s*-->\\s*)(\\d{1,2}:\\d{2}:\\d{2}\\.\\d{1,3})"
    )
    private val SINGLE_TS_REGEX = Regex("^(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})$")

    fun clampOffset(offsetSec: Double): Double {
        if (!offsetSec.isFinite()) return 0.0
        val clamped = offsetSec.coerceIn(-MAX_OFFSET_SEC, MAX_OFFSET_SEC)
        return (clamped * 10.0).roundToLong() / 10.0
    }

    fun formatOffsetLabel(offsetSec: Double): String {
        val v = clampOffset(offsetSec)
        return when {
            v > 0.0 -> "+${"%.1f".format(java.util.Locale.US, v)}s"
            v < 0.0 -> "${"%.1f".format(java.util.Locale.US, v)}s"
            else -> "0.0s"
        }
    }

    fun buildSubtitleQuery(rawTitle: String?): String {
        var cleaned = (rawTitle ?: "")
            .replace(Regex("\\[[^\\]]*\\]"), " ")
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\b(4K|UHD|FHD|HD|SD|H264|H265|HEVC|1080p|720p|DUB|DUBLADO|LEG|LEGENDADO|DUAL|NACIONAL|CAM|WEB-?DL|BLURAY)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("[._|]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.length > 80) cleaned = cleaned.take(80).trim()
        return cleaned
    }

    fun parseTimestampMs(raw: String): Long? {
        val match = SINGLE_TS_REGEX.matchEntire(raw.trim()) ?: return null
        val hh = match.groupValues[1].toLong()
        val mm = match.groupValues[2].toLong()
        val ss = match.groupValues[3].toLong()
        val ms = match.groupValues[4].padEnd(3, '0').toLong()
        return ((hh * 3600L + mm * 60L + ss) * 1000L) + ms
    }

    fun formatVttTimestamp(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val hours = clamped / 3_600_000L
        val mins = (clamped % 3_600_000L) / 60_000L
        val secs = (clamped % 60_000L) / 1_000L
        val millis = clamped % 1_000L
        return "%02d:%02d:%02d.%03d".format(java.util.Locale.US, hours, mins, secs, millis)
    }

    fun srtToVtt(inputText: String?): String {
        val clean = (inputText ?: "")
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()
        if (clean.isEmpty()) return "WEBVTT\n\n"
        if (clean.startsWith("WEBVTT", ignoreCase = true)) return "$clean\n"

        val out = StringBuilder("WEBVTT\n\n")
        clean.split('\n').forEach { line ->
            if (CUE_TIMESTAMP_LINE.containsMatchIn(line.trim())) {
                out.append(SRT_COMMA_REGEX.replace(line, "$1.$2")).append('\n')
            } else {
                out.append(line).append('\n')
            }
        }
        return out.toString()
    }

    fun shiftVttTimestamps(inputText: String?, offsetSec: Double): String {
        val vtt = srtToVtt(inputText)
        val clamped = clampOffset(offsetSec)
        if (abs(clamped) < 0.001) return vtt
        val deltaMs = (clamped * 1000.0).roundToLong()

        return VTT_RANGE_REGEX.replace(vtt) { match ->
            val startMs = parseTimestampMs(match.groupValues[1]) ?: return@replace match.value
            val endMs = parseTimestampMs(match.groupValues[3]) ?: return@replace match.value
            val newStart = (startMs + deltaMs).coerceAtLeast(0L)
            val newEnd = (endMs + deltaMs).coerceAtLeast(newStart + 200L)
            "${formatVttTimestamp(newStart)}${match.groupValues[2]}${formatVttTimestamp(newEnd)}"
        }
    }

    /**
     * Detecta janelas heurísticas de Abertura (Intro) e Créditos em VOD (Filmes e Séries).
     * Retorna null em canais ao vivo ou mídias muito curtas (< 10 min).
     */
    fun detectSkipPrompt(positionMs: Long, durationMs: Long, contentType: String): VodSkipPrompt? {
        val type = contentType.uppercase()
        if (type != "MOVIE" && type != "SERIES") return null
        if (durationMs < 600_000L || positionMs < 0L) return null

        val isSeries = type == "SERIES"
        val introStartMs = if (isSeries) 5_000L else 10_000L
        val introEndMs = if (isSeries) 95_000L else 110_000L

        if (positionMs in introStartMs until (introEndMs - 5_000L)) {
            return VodSkipPrompt.SkipIntro(targetMs = introEndMs.coerceAtMost(durationMs - 5_000L))
        }

        if (durationMs >= 900_000L) {
            val creditsStartMs = durationMs - 150_000L
            val creditsEndMs = durationMs - 15_000L
            if (positionMs in creditsStartMs until creditsEndMs) {
                return VodSkipPrompt.SkipCredits(targetMs = (durationMs - 3_000L).coerceAtLeast(positionMs + 5_000L))
            }
        }
        return null
    }

    fun searchOpenSubtitles(
        query: String,
        client: OkHttpClient,
        languages: String = "pt-br,pt-pt,en",
        endpoint: String = DEFAULT_SUBTITLES_ENDPOINT
    ): SubtitleSearchResult {
        val cleanQuery = buildSubtitleQuery(query).ifEmpty { query.trim() }
        if (cleanQuery.length < 2) {
            return SubtitleSearchResult(ok = false, error = "Título muito curto para busca de legendas.")
        }
        return try {
            val url = "$endpoint?action=search&query=${URLEncoder.encode(cleanQuery, "UTF-8")}&lang=${URLEncoder.encode(languages, "UTF-8")}"
            val req = Request.Builder().url(url).get().header("Accept", "application/json").build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull()
                if (!resp.isSuccessful || json == null || !json.optBoolean("ok", false)) {
                    val errMsg = json?.optString("error", "")?.trim().orEmpty()
                    return SubtitleSearchResult(
                        ok = false,
                        error = errMsg.ifEmpty { "Erro ao buscar legendas (HTTP ${resp.code})." }
                    )
                }
                val arr = json.optJSONArray("items")
                val list = mutableListOf<SubtitleSearchItem>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val fileId = obj.optLong("fileId", 0L)
                        if (fileId <= 0L) continue
                        list.add(
                            SubtitleSearchItem(
                                fileId = fileId,
                                fileName = obj.optString("fileName", "legenda.srt"),
                                release = obj.optString("release", obj.optString("fileName", cleanQuery)),
                                language = obj.optString("language", "pt-br"),
                                downloads = obj.optInt("downloads", 0),
                                fps = obj.optDouble("fps", 0.0)
                            )
                        )
                    }
                }
                SubtitleSearchResult(ok = true, items = list)
            }
        } catch (e: Throwable) {
            SubtitleSearchResult(ok = false, error = "Falha de rede ao consultar OpenSubtitles.")
        }
    }

    fun downloadSubtitleVtt(
        fileId: Long,
        client: OkHttpClient,
        offsetSec: Double = 0.0,
        endpoint: String = DEFAULT_SUBTITLES_ENDPOINT
    ): SubtitleDownloadResult {
        if (fileId <= 0L) return SubtitleDownloadResult(ok = false, error = "ID de legenda inválido.")
        return try {
            val offsetParam = "%.1f".format(java.util.Locale.US, clampOffset(offsetSec))
            val url = "$endpoint?action=download&file_id=$fileId&offset=$offsetParam"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || body.isBlank()) {
                    val errMsg = runCatching { JSONObject(body).optString("error", "") }.getOrDefault("")
                    return SubtitleDownloadResult(
                        ok = false,
                        error = errMsg.ifEmpty { "Erro ao baixar legenda (HTTP ${resp.code})." }
                    )
                }
                SubtitleDownloadResult(ok = true, vttContent = srtToVtt(body))
            }
        } catch (e: Throwable) {
            SubtitleDownloadResult(ok = false, error = "Falha de rede ao baixar legenda.")
        }
    }
}
