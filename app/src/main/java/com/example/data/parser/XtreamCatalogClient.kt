package com.example.data.parser

import android.util.Base64
import com.example.data.model.PlaylistItem
import com.example.utils.LogSanitizer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

data class XtreamCatalogResult(
    val items: List<PlaylistItem>,
    val epgChannelIdsByUrl: Map<String, String>,
    val epgChannelIdsByName: Map<String, String>,
    val liveCount: Int,
    val movieCount: Int,
    val seriesCount: Int
)

/**
 * Cliente Xtream Codes JSON (`player_api.php`) para Android.
 * Suporta ingestão direta de Canais ao Vivo, Filmes (VOD), Séries, Episódios (`get_series_info`)
 * e Short EPG (`get_short_epg`), garantindo compatibilidade total após o encerramento de listas M3U.
 */
object XtreamCatalogClient {

    private fun encodeParam(value: String): String =
        try { URLEncoder.encode(value, "UTF-8") } catch (_: Exception) { value }

    fun normalizeBaseUrl(rawBase: String): String {
        val trimmed = rawBase.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    private fun fetchJsonString(client: OkHttpClient, url: String): String? {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string()?.trim() ?: return null
            if (body.isEmpty() || body.startsWith("<")) return null
            return body
        }
    }

    private fun fetchJsonArray(client: OkHttpClient, url: String): JSONArray? {
        val body = fetchJsonString(client, url) ?: return null
        if (!body.startsWith("[")) return null
        return try { JSONArray(body) } catch (_: Exception) { null }
    }

    private fun parseCategoriesMap(array: JSONArray?): Map<String, String> {
        if (array == null) return emptyMap()
        val map = HashMap<String, String>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("category_id", "").trim()
            val name = obj.optString("category_name", "").trim()
            if (id.isNotEmpty() && name.isNotEmpty()) {
                map[id] = name
            }
        }
        return map
    }

    /**
     * Valida credenciais no endpoint raiz `player_api.php` antes de baixar o catálogo.
     * Retorna `null` se autenticado ou se o servidor não expor status explícito;
     * retorna mensagem de erro legível se as credenciais forem rejeitadas ou expiradas.
     */
    fun checkAuthError(authJson: String): String? {
        if (!authJson.startsWith("{")) return null
        return try {
            val root = JSONObject(authJson)
            val userInfo = root.optJSONObject("user_info") ?: return null
            val authVal = userInfo.opt("auth")
            val isAuthenticated = when (authVal) {
                is Number -> authVal.toInt() != 0
                is String -> authVal != "0" && authVal.equals("false", ignoreCase = true).not()
                is Boolean -> authVal
                else -> true
            }
            if (!isAuthenticated) {
                return "Usuário ou senha inválidos no servidor Xtream."
            }
            val status = userInfo.optString("status", "").trim().lowercase(Locale.ROOT)
            if (status == "expired" || status == "banned" || status == "disabled") {
                return "Sua conta IPTV está $status no servidor."
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Baixa o catálogo completo via API Xtream JSON (`player_api.php`).
     * Retorna `null` caso o servidor não suporte a API JSON (permitindo fallback limpo para M3U).
     * Lança exceção com mensagem amigável caso as credenciais sejam explicitamente rejeitadas.
     */
    fun fetchCatalog(
        client: OkHttpClient,
        baseUrl: String,
        username: String,
        password: String,
        playlistSource: String,
        liveFormat: String,
        onProgress: (Int) -> Unit = {}
    ): XtreamCatalogResult? {
        val cleanBase = normalizeBaseUrl(baseUrl)
        if (cleanBase.isEmpty() || username.isBlank() || password.isBlank()) return null

        val encUser = encodeParam(username.trim())
        val encPass = encodeParam(password.trim())
        val apiBase = "$cleanBase/player_api.php?username=$encUser&password=$encPass"
        val liveExt = if (liveFormat == "HLS (.m3u8)") "m3u8" else "ts"

        onProgress(15)
        // 1. Handshake/auth probe
        val authBody = try {
            fetchJsonString(client, apiBase)
        } catch (_: Exception) {
            null
        } ?: return null

        checkAuthError(authBody)?.let { authError ->
            throw IllegalStateException(authError)
        }

        val items = ArrayList<PlaylistItem>(4096)
        val epgByUrl = HashMap<String, String>(2048)
        val epgByName = HashMap<String, String>(2048)

        // 2. Live TV
        onProgress(25)
        val liveCats = parseCategoriesMap(fetchJsonArray(client, "$apiBase&action=get_live_categories"))
        val liveStreams = fetchJsonArray(client, "$apiBase&action=get_live_streams")
        val liveCount = parseLiveStreams(
            array = liveStreams,
            categories = liveCats,
            cleanBase = cleanBase,
            encUser = encUser,
            encPass = encPass,
            liveExt = liveExt,
            playlistSource = playlistSource,
            outItems = items,
            outEpgByUrl = epgByUrl,
            outEpgByName = epgByName
        )

        onProgress(55)
        // 3. VOD (Filmes)
        val vodCats = parseCategoriesMap(fetchJsonArray(client, "$apiBase&action=get_vod_categories"))
        val vodStreams = fetchJsonArray(client, "$apiBase&action=get_vod_streams")
        val movieCount = parseVodStreams(
            array = vodStreams,
            categories = vodCats,
            cleanBase = cleanBase,
            encUser = encUser,
            encPass = encPass,
            playlistSource = playlistSource,
            outItems = items
        )

        onProgress(78)
        // 4. Séries
        val seriesCats = parseCategoriesMap(fetchJsonArray(client, "$apiBase&action=get_series_categories"))
        val seriesList = fetchJsonArray(client, "$apiBase&action=get_series")
        val seriesCount = parseSeriesStreams(
            array = seriesList,
            categories = seriesCats,
            cleanBase = cleanBase,
            encUser = encUser,
            encPass = encPass,
            playlistSource = playlistSource,
            outItems = items
        )

        if (items.isEmpty()) return null

        onProgress(95)
        return XtreamCatalogResult(
            items = items,
            epgChannelIdsByUrl = epgByUrl,
            epgChannelIdsByName = epgByName,
            liveCount = liveCount,
            movieCount = movieCount,
            seriesCount = seriesCount
        )
    }

    internal fun parseLiveStreams(
        array: JSONArray?,
        categories: Map<String, String>,
        cleanBase: String,
        encUser: String,
        encPass: String,
        liveExt: String,
        playlistSource: String,
        outItems: MutableList<PlaylistItem>,
        outEpgByUrl: MutableMap<String, String>,
        outEpgByName: MutableMap<String, String>
    ): Int {
        if (array == null) return 0
        var count = 0
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val streamId = obj.optString("stream_id", "").trim()
            if (streamId.isEmpty()) continue
            val name = obj.optString("name", "Canal $streamId").trim().ifEmpty { "Canal $streamId" }
            val catId = obj.optString("category_id", "").trim()
            val category = categories[catId] ?: "Canais Ao Vivo"
            val logo = sanitizeHttpUrl(obj.optString("stream_icon", ""))
            val epgId = obj.optString("epg_channel_id", "").trim()
            val url = "$cleanBase/live/$encUser/$encPass/$streamId.$liveExt"

            if (epgId.isNotEmpty()) {
                outEpgByUrl[url] = epgId
                outEpgByName[XmltvEpgParser.normalizeChannelKey(name)] = epgId
            }

            outItems.add(
                PlaylistItem(
                    name = name,
                    url = url,
                    logoUrl = logo,
                    category = category,
                    contentType = "LIVE",
                    isAdult = M3UParser.isAdultContent(name, category),
                    playlistSource = playlistSource
                )
            )
            count++
        }
        return count
    }

    internal fun parseVodStreams(
        array: JSONArray?,
        categories: Map<String, String>,
        cleanBase: String,
        encUser: String,
        encPass: String,
        playlistSource: String,
        outItems: MutableList<PlaylistItem>
    ): Int {
        if (array == null) return 0
        var count = 0
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val streamId = obj.optString("stream_id", "").trim()
            if (streamId.isEmpty()) continue
            val name = obj.optString("name", "Filme $streamId").trim().ifEmpty { "Filme $streamId" }
            val catId = obj.optString("category_id", "").trim()
            val category = categories[catId] ?: "Filmes"
            val logo = sanitizeHttpUrl(obj.optString("stream_icon", ""))
            val ext = obj.optString("container_extension", "mp4").trim().ifEmpty { "mp4" }
            val url = "$cleanBase/movie/$encUser/$encPass/$streamId.$ext"

            outItems.add(
                PlaylistItem(
                    name = name,
                    url = url,
                    logoUrl = logo,
                    category = category,
                    contentType = "MOVIE",
                    isAdult = M3UParser.isAdultContent(name, category),
                    playlistSource = playlistSource
                )
            )
            count++
        }
        return count
    }

    internal fun parseSeriesStreams(
        array: JSONArray?,
        categories: Map<String, String>,
        cleanBase: String,
        encUser: String,
        encPass: String,
        playlistSource: String,
        outItems: MutableList<PlaylistItem>
    ): Int {
        if (array == null) return 0
        var count = 0
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val seriesId = obj.optString("series_id", "").trim()
            if (seriesId.isEmpty()) continue
            val name = obj.optString("name", "Série $seriesId").trim().ifEmpty { "Série $seriesId" }
            val catId = obj.optString("category_id", "").trim()
            val category = categories[catId] ?: "Séries"
            val cover = sanitizeHttpUrl(obj.optString("cover", ""))
            // URL canônica de container de série Xtream (resolvida via get_series_info ao abrir o modal)
            val url = "$cleanBase/series/$encUser/$encPass/$seriesId"

            outItems.add(
                PlaylistItem(
                    name = name,
                    url = url,
                    logoUrl = cover,
                    category = category,
                    contentType = "SERIES",
                    isAdult = M3UParser.isAdultContent(name, category),
                    playlistSource = playlistSource
                )
            )
            count++
        }
        return count
    }

    /**
     * Detecta se uma URL representa um container de série Xtream (`/series/{user}/{pass}/{seriesId}` sem extensão).
     */
    fun extractXtreamSeriesId(url: String): String? {
        val m = Regex("/series/[^/]+/[^/]+/([0-9]+)$", RegexOption.IGNORE_CASE).find(url.trim())
        return m?.groupValues?.getOrNull(1)
    }

    /**
     * Busca temporadas e episódios reais de uma série via `action=get_series_info`.
     */
    fun fetchSeriesEpisodes(
        client: OkHttpClient,
        baseUrl: String,
        username: String,
        password: String,
        seriesId: String,
        seriesName: String,
        category: String,
        logoUrl: String?,
        playlistSource: String
    ): List<PlaylistItem> {
        val cleanBase = normalizeBaseUrl(baseUrl)
        if (cleanBase.isEmpty() || username.isBlank() || password.isBlank() || seriesId.isBlank()) {
            return emptyList()
        }
        val encUser = encodeParam(username.trim())
        val encPass = encodeParam(password.trim())
        val encId = encodeParam(seriesId.trim())
        val url = "$cleanBase/player_api.php?username=$encUser&password=$encPass&action=get_series_info&series_id=$encId"

        val body = try {
            fetchJsonString(client, url)
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        return parseSeriesInfoJson(
            jsonString = body,
            cleanBase = cleanBase,
            encUser = encUser,
            encPass = encPass,
            seriesName = seriesName,
            category = category,
            logoUrl = logoUrl,
            playlistSource = playlistSource
        )
    }

    internal fun parseSeriesInfoJson(
        jsonString: String,
        cleanBase: String,
        encUser: String,
        encPass: String,
        seriesName: String,
        category: String,
        logoUrl: String?,
        playlistSource: String
    ): List<PlaylistItem> {
        val root = try { JSONObject(jsonString) } catch (_: Exception) { return emptyList() }
        val episodesObj = root.optJSONObject("episodes") ?: return emptyList()
        val result = ArrayList<PlaylistItem>()

        val seasonKeys = mutableListOf<String>()
        val keysIter = episodesObj.keys()
        while (keysIter.hasNext()) {
            seasonKeys.add(keysIter.next())
        }
        seasonKeys.sortBy { it.toIntOrNull() ?: Int.MAX_VALUE }

        for (seasonKey in seasonKeys) {
            val seasonNum = seasonKey.toIntOrNull() ?: 1
            val epArray = episodesObj.optJSONArray(seasonKey) ?: continue
            for (i in 0 until epArray.length()) {
                val ep = epArray.optJSONObject(i) ?: continue
                val epId = ep.optString("id", "").trim()
                if (epId.isEmpty()) continue
                val epNum = ep.optInt("episode_num", i + 1)
                val ext = ep.optString("container_extension", "mp4").trim().ifEmpty { "mp4" }
                val rawTitle = ep.optString("title", "").trim()
                val formattedCode = String.format(Locale.ROOT, "S%02dE%02d", seasonNum, epNum)
                val displayName = if (rawTitle.isNotEmpty() && rawTitle.contains(formattedCode, ignoreCase = true)) {
                    rawTitle
                } else if (rawTitle.isNotEmpty()) {
                    "$seriesName $formattedCode - $rawTitle"
                } else {
                    "$seriesName $formattedCode"
                }
                val epUrl = "$cleanBase/series/$encUser/$encPass/$epId.$ext"
                result.add(
                    PlaylistItem(
                        name = displayName,
                        url = epUrl,
                        logoUrl = logoUrl,
                        category = category,
                        contentType = "SERIES",
                        isAdult = M3UParser.isAdultContent(seriesName, category),
                        playlistSource = playlistSource
                    )
                )
            }
        }
        return result
    }

    /**
     * Busca Short EPG (`get_short_epg`) de um canal Xtream decodificando Base64 com segurança.
     */
    fun fetchShortEpg(
        client: OkHttpClient,
        baseUrl: String,
        username: String,
        password: String,
        streamId: String,
        limit: Int = 12
    ): List<XmltvProgramme> {
        val cleanBase = normalizeBaseUrl(baseUrl)
        if (cleanBase.isEmpty() || username.isBlank() || password.isBlank() || streamId.isBlank()) {
            return emptyList()
        }
        val encUser = encodeParam(username.trim())
        val encPass = encodeParam(password.trim())
        val encStream = encodeParam(streamId.trim())
        val url = "$cleanBase/player_api.php?username=$encUser&password=$encPass&action=get_short_epg&stream_id=$encStream&limit=$limit"

        val body = try {
            fetchJsonString(client, url)
        } catch (e: Exception) {
            LogSanitizer.sanitizeText(e.message)
            null
        } ?: return emptyList()

        return parseShortEpgJson(body, streamId)
    }

    internal fun parseShortEpgJson(jsonString: String, channelId: String): List<XmltvProgramme> {
        val root = try { JSONObject(jsonString) } catch (_: Exception) { return emptyList() }
        val arr = root.optJSONArray("epg_listings") ?: return emptyList()
        val list = ArrayList<XmltvProgramme>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val title = decodeMaybeBase64(obj.optString("title", "Programa")).ifBlank { "Programa" }
            val desc = decodeMaybeBase64(obj.optString("description", ""))
                .ifBlank { "Programação ao vivo transmitida pelo canal." }
            val startSec = obj.optLong("start_timestamp", 0L)
            val stopSec = obj.optLong("stop_timestamp", 0L)
            val startMs = if (startSec > 0L) startSec * 1000L else XmltvEpgParser.parseXmltvTimestamp(obj.optString("start", ""))
            val stopMs = if (stopSec > 0L) stopSec * 1000L else XmltvEpgParser.parseXmltvTimestamp(obj.optString("end", ""))
            if (startMs > 0L) {
                list.add(
                    XmltvProgramme(
                        channelId = channelId,
                        title = title,
                        description = desc,
                        startMs = startMs,
                        stopMs = if (stopMs > startMs) stopMs else startMs + 30L * 60L * 1000L
                    )
                )
            }
        }
        list.sortBy { it.startMs }
        return list
    }

    internal fun decodeMaybeBase64(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.length % 4 != 0 || !trimmed.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
            return trimmed
        }
        return try {
            val bytes = try {
                Base64.decode(trimmed, Base64.DEFAULT)
            } catch (_: Throwable) {
                java.util.Base64.getDecoder().decode(trimmed)
            }
            val decoded = String(bytes, Charsets.UTF_8).trim()
            // Evita substituir texto ASCII comum por bytes inválidos
            if (decoded.isNotEmpty() && decoded.none { it == '\uFFFD' || (it.code < 32 && it != '\n' && it != '\r' && it != '\t') }) {
                decoded
            } else {
                trimmed
            }
        } catch (_: Exception) {
            trimmed
        }
    }

    fun extractStreamIdFromUrl(url: String): String? {
        return Regex("/([0-9]+)(?:\\.[a-zA-Z0-9]+)?$").find(url.trim())?.groupValues?.getOrNull(1)
    }

    private fun sanitizeHttpUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
        return if (lower.startsWith("http://") || lower.startsWith("https://")) trimmed else null
    }
}
