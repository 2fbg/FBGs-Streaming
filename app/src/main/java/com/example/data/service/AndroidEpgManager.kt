package com.example.data.service

import android.util.Log
import com.example.data.model.PlaylistItem
import com.example.data.parser.XmltvEpgParser
import com.example.data.parser.XmltvIndex
import com.example.data.parser.XmltvProgramme
import com.example.data.parser.XtreamCatalogClient
import com.example.network.StreamHttpClients
import com.example.ui.AndroidRealEpgCache
import com.example.ui.EpgProgramInfo
import com.example.utils.LogSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed class XmltvLoadState {
    data object Idle : XmltvLoadState()
    data object Loading : XmltvLoadState()
    data class Ready(val channelCount: Int, val programmeCount: Int, val updatedAtMs: Long) : XmltvLoadState()
    data class Error(val message: String) : XmltvLoadState()
}

/**
 * Gerenciador central de EPG real (XMLTV completo + Short EPG Xtream) no Android.
 * Mantém índice XMLTV em memória e mapeamento `tvg-id` / `epg_channel_id` sem exigir alteração de schema Room.
 */
object AndroidEpgManager {

    private const val TAG = "MK21_EPG"
    private const val XMLTV_TTL_MS = 2L * 60L * 60L * 1000L // 2 horas
    private const val PUBLIC_EPG_TTL_MS = 15L * 60L * 1000L // 15 min
    const val PUBLIC_EPG_ENDPOINT = "https://bgstreaming.vercel.app/api/epg"

    private val _xmltvIndex = MutableStateFlow<XmltvIndex?>(null)
    val xmltvIndex: StateFlow<XmltvIndex?> = _xmltvIndex.asStateFlow()

    private val _loadState = MutableStateFlow<XmltvLoadState>(XmltvLoadState.Idle)
    val loadState: StateFlow<XmltvLoadState> = _loadState.asStateFlow()

    // Mapeamentos auxiliares preenchidos pelo XtreamCatalogClient e M3UParser
    private val epgIdByStreamUrl = ConcurrentHashMap<String, String>()
    private val epgIdByNormalizedName = ConcurrentHashMap<String, String>()
    private val shortEpgCache = ConcurrentHashMap<String, Pair<Long, List<XmltvProgramme>>>()
    private val publicEpgCache = ConcurrentHashMap<String, Pair<Long, List<XmltvProgramme>>>()

    @Volatile
    private var lastXmltvUrl: String? = null

    @Volatile
    private var lastXmltvLoadedAt: Long = 0L

    @Volatile
    var discoveredM3uXmltvUrl: String? = null

    fun registerChannelEpgId(streamUrl: String, channelName: String, tvgId: String?) {
        val clean = tvgId?.trim().orEmpty()
        if (clean.isEmpty()) return
        epgIdByStreamUrl[streamUrl] = clean
        val norm = XmltvEpgParser.normalizeChannelKey(channelName)
        if (norm.isNotEmpty()) {
            epgIdByNormalizedName[norm] = clean
        }
    }

    fun registerBulkChannelEpgIds(byUrl: Map<String, String>, byName: Map<String, String>) {
        if (byUrl.isNotEmpty()) epgIdByStreamUrl.putAll(byUrl)
        if (byName.isNotEmpty()) epgIdByNormalizedName.putAll(byName)
    }

    fun resolveTvgId(item: PlaylistItem): String? {
        epgIdByStreamUrl[item.url]?.let { return it }
        val norm = XmltvEpgParser.normalizeChannelKey(item.name)
        return epgIdByNormalizedName[norm]
    }

    fun buildXmltvUrl(baseUrl: String, username: String, password: String): String? {
        val cleanBase = XtreamCatalogClient.normalizeBaseUrl(baseUrl)
        if (cleanBase.isEmpty() || username.isBlank() || password.isBlank()) {
            return discoveredM3uXmltvUrl?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
        }
        val encUser = try { URLEncoder.encode(username.trim(), "UTF-8") } catch (_: Exception) { username.trim() }
        val encPass = try { URLEncoder.encode(password.trim(), "UTF-8") } catch (_: Exception) { password.trim() }
        return "$cleanBase/xmltv.php?username=$encUser&password=$encPass"
    }

    /**
     * Carrega e indexa o arquivo XMLTV em streaming (sem DOMParser).
     */
    suspend fun loadXmltv(
        baseUrl: String,
        username: String,
        password: String,
        force: Boolean = false,
        client: OkHttpClient = StreamHttpClients.baseClient
    ): XmltvIndex? = withContext(Dispatchers.IO) {
        val targetUrl = buildXmltvUrl(baseUrl, username, password)
        if (targetUrl.isNullOrBlank()) {
            _loadState.value = XmltvLoadState.Error("Servidor sem fonte XMLTV configurada.")
            return@withContext _xmltvIndex.value
        }

        val now = System.currentTimeMillis()
        val cached = _xmltvIndex.value
        if (!force && cached != null && lastXmltvUrl == targetUrl && (now - lastXmltvLoadedAt) < XMLTV_TTL_MS) {
            return@withContext cached
        }

        _loadState.value = XmltvLoadState.Loading
        try {
            val epgClient = client.newBuilder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url(targetUrl).build()
            epgClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = "Servidor XMLTV respondeu HTTP ${resp.code}"
                    _loadState.value = XmltvLoadState.Error(err)
                    return@withContext _xmltvIndex.value
                }
                val stream = resp.body?.byteStream()
                if (stream == null) {
                    _loadState.value = XmltvLoadState.Error("XMLTV vazio.")
                    return@withContext _xmltvIndex.value
                }
                val parsed = XmltvEpgParser.parse(stream, nowMs = now, applyTimeWindow = true)
                _xmltvIndex.value = parsed
                lastXmltvUrl = targetUrl
                lastXmltvLoadedAt = System.currentTimeMillis()
                _loadState.value = XmltvLoadState.Ready(
                    channelCount = parsed.channelCount,
                    programmeCount = parsed.totalProgrammes,
                    updatedAtMs = lastXmltvLoadedAt
                )
                Log.i(TAG, "XMLTV indexado: ${parsed.channelCount} canais, ${parsed.totalProgrammes} programas")
                return@withContext parsed
            }
        } catch (e: Exception) {
            val safeMsg = LogSanitizer.sanitizeText(e.message)
            Log.w(TAG, "Falha ao carregar XMLTV: $safeMsg")
            _loadState.value = XmltvLoadState.Error("Falha ao baixar guia XMLTV.")
            return@withContext _xmltvIndex.value
        }
    }

    /**
     * Faz o parsing do JSON retornado pelo endpoint público `/api/epg`.
     */
    fun parsePublicEpgJson(json: String, channelKey: String): List<XmltvProgramme> {
        if (json.isBlank()) return emptyList()
        return try {
            val root = JSONObject(json)
            val arr = root.optJSONArray("programs") ?: return emptyList()
            val result = ArrayList<XmltvProgramme>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val title = obj.optString("title", "").trim()
                val startMs = obj.optLong("start", 0L)
                val stopMs = obj.optLong("stop", 0L)
                if (title.isEmpty() || startMs <= 0L || stopMs <= startMs) continue
                result.add(
                    XmltvProgramme(
                        channelId = channelKey,
                        title = title,
                        description = obj.optString("desc", "").trim(),
                        startMs = startMs,
                        stopMs = stopMs
                    )
                )
            }
            result.sortedBy { it.startMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Fallback público de EPG brasileiro sem credencial (`/api/epg`), ideal para listas M3U puras
     * ou servidores Xtream que não fornecem XMLTV/Short EPG.
     */
    suspend fun fetchPublicFallbackEpg(
        item: PlaylistItem,
        client: OkHttpClient = StreamHttpClients.baseClient
    ): List<XmltvProgramme> = withContext(Dispatchers.IO) {
        val normName = XmltvEpgParser.normalizeChannelKey(item.name)
        if (normName.isEmpty()) return@withContext emptyList()

        val now = System.currentTimeMillis()
        publicEpgCache[normName]?.let { (ts, cachedList) ->
            if ((now - ts) < PUBLIC_EPG_TTL_MS) return@withContext cachedList
        }

        val encoded = try {
            URLEncoder.encode(item.name.trim(), "UTF-8")
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        val epgClient = client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder()
            .url("$PUBLIC_EPG_ENDPOINT?channel=$encoded")
            .build()

        val list = try {
            epgClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) emptyList()
                else parsePublicEpgJson(resp.body?.string().orEmpty(), normName)
            }
        } catch (_: Exception) {
            emptyList()
        }

        publicEpgCache[normName] = now to list
        if (list.isNotEmpty()) {
            AndroidRealEpgCache.put(item.name, toEpgProgramInfoList(list, now))
        }
        list
    }

    /**
     * Busca Short EPG para um canal específico (usado no Preview e como reforço do Guia).
     * Quando o canal vem de lista M3U pura ou o servidor não retorna Short EPG, consulta o fallback público `/api/epg`.
     */
    suspend fun fetchShortEpgForChannel(
        item: PlaylistItem,
        baseUrl: String,
        username: String,
        password: String,
        client: OkHttpClient = StreamHttpClients.baseClient
    ): List<XmltvProgramme> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cacheKey = item.url
        shortEpgCache[cacheKey]?.let { (ts, cachedList) ->
            if ((now - ts) < 15L * 60L * 1000L && cachedList.isNotEmpty()) {
                return@withContext cachedList
            }
        }

        val streamId = XtreamCatalogClient.extractStreamIdFromUrl(item.url)
        val xtreamList = if (streamId != null && baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
            val epgClient = client.newBuilder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            XtreamCatalogClient.fetchShortEpg(
                client = epgClient,
                baseUrl = baseUrl,
                username = username,
                password = password,
                streamId = streamId,
                limit = 12
            )
        } else {
            emptyList()
        }

        val finalList = if (xtreamList.isNotEmpty()) {
            xtreamList
        } else {
            fetchPublicFallbackEpg(item, client)
        }

        if (finalList.isNotEmpty()) {
            shortEpgCache[cacheKey] = now to finalList
            AndroidRealEpgCache.put(item.name, toEpgProgramInfoList(finalList, now))
        }
        finalList
    }

    /**
     * Obtém a grade real de um canal consultando primeiro o XMLTV indexado,
     * depois o cache de Short EPG Xtream e por fim o cache do fallback público.
     */
    fun getRealProgrammes(item: PlaylistItem, nowMs: Long = System.currentTimeMillis()): List<XmltvProgramme> {
        val tvgId = resolveTvgId(item)
        val xmltvList = _xmltvIndex.value?.findProgrammes(item.name, tvgId).orEmpty()
        if (xmltvList.isNotEmpty()) return xmltvList

        val shortList = shortEpgCache[item.url]?.second.orEmpty()
        if (shortList.isNotEmpty()) return shortList

        val normName = XmltvEpgParser.normalizeChannelKey(item.name)
        val pubList = if (normName.isNotEmpty()) publicEpgCache[normName]?.second.orEmpty() else emptyList()
        if (pubList.isNotEmpty()) return pubList

        return emptyList()
    }

    fun getNowAndNext(item: PlaylistItem, nowMs: Long = System.currentTimeMillis()): Pair<XmltvProgramme?, XmltvProgramme?> {
        val programmes = getRealProgrammes(item, nowMs)
        if (programmes.isEmpty()) return null to null
        val curIdx = programmes.indexOfFirst { it.isLiveAt(nowMs) }
        if (curIdx >= 0) {
            return programmes[curIdx] to programmes.getOrNull(curIdx + 1)
        }
        val next = programmes.firstOrNull { it.startMs > nowMs }
        return null to next
    }

    fun toEpgProgramInfoList(programmes: List<XmltvProgramme>, nowMs: Long = System.currentTimeMillis()): List<EpgProgramInfo> {
        return programmes.map { p ->
            val isLive = p.isLiveAt(nowMs)
            EpgProgramInfo(
                title = p.title,
                timeRange = p.formattedRange(),
                description = p.description,
                isCurrent = isLive,
                progressPercent = p.progressAt(nowMs),
                isReal = true
            )
        }
    }

    fun clearSessionCaches() {
        shortEpgCache.clear()
        publicEpgCache.clear()
        epgIdByStreamUrl.clear()
        epgIdByNormalizedName.clear()
        discoveredM3uXmltvUrl = null
    }
}
