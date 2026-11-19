package com.example.data.service

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

data class SyncFavoriteItem(
    val key: String,
    val name: String,
    val url: String,
    val logo: String = "",
    val group: String = "",
    val contentType: String = "LIVE",
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

data class SyncContinueItem(
    val key: String,
    val name: String,
    val url: String,
    val logo: String = "",
    val group: String = "",
    val contentType: String = "MOVIE",
    val positionSec: Int = 0,
    val durationSec: Int = 0,
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

data class CloudSyncPayload(
    val favorites: List<SyncFavoriteItem> = emptyList(),
    val continueWatching: List<SyncContinueItem> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class CloudSyncResult(
    val ok: Boolean,
    val code: String = "",
    val payload: CloudSyncPayload? = null,
    val updatedAt: Long = 0L,
    val error: String? = null
)

object CloudSyncManager {
    const val DEFAULT_SYNC_ENDPOINT = "https://bgstreaming.vercel.app/api/sync"
    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val MAX_FAVORITES = 500
    private const val MAX_CONTINUE = 200
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val CODE_REGEX = Regex("^MK21-[A-Z0-9]{4}-[A-Z0-9]{4}$")
    private val secureRandom = SecureRandom()

    fun normalizeSyncCode(raw: String?): String {
        val cleaned = (raw ?: "").uppercase().replace(Regex("[^A-Z0-9]"), "")
        val body = if (cleaned.startsWith("MK21")) cleaned.removePrefix("MK21") else cleaned
        if (body.length < 8 || body.length > 16) return ""
        return "MK21-${body.substring(0, 4)}-${body.substring(4, 8)}"
    }

    fun isValidSyncCode(raw: String?): Boolean {
        val normalized = normalizeSyncCode(raw)
        return CODE_REGEX.matches(normalized)
    }

    fun generateSyncCode(): String {
        val sb = StringBuilder(8)
        repeat(8) {
            val idx = secureRandom.nextInt(CODE_ALPHABET.length)
            sb.append(CODE_ALPHABET[idx])
        }
        val body = sb.toString()
        return "MK21-${body.substring(0, 4)}-${body.substring(4, 8)}"
    }

    fun mergePayloads(
        local: CloudSyncPayload,
        remote: CloudSyncPayload,
        nowMs: Long = System.currentTimeMillis()
    ): CloudSyncPayload {
        val favMap = LinkedHashMap<String, SyncFavoriteItem>()
        for (item in remote.favorites) {
            if (item.key.isBlank()) continue
            val prev = favMap[item.key]
            if (prev == null || item.updatedAt >= prev.updatedAt) {
                favMap[item.key] = item
            }
        }
        for (item in local.favorites) {
            if (item.key.isBlank()) continue
            val prev = favMap[item.key]
            if (prev == null || item.updatedAt >= prev.updatedAt) {
                favMap[item.key] = item
            }
        }

        val contMap = LinkedHashMap<String, SyncContinueItem>()
        for (item in remote.continueWatching) {
            if (item.key.isBlank()) continue
            val prev = contMap[item.key]
            if (prev == null || item.updatedAt >= prev.updatedAt) {
                contMap[item.key] = item
            }
        }
        for (item in local.continueWatching) {
            if (item.key.isBlank()) continue
            val prev = contMap[item.key]
            if (prev == null || item.updatedAt >= prev.updatedAt) {
                contMap[item.key] = item
            }
        }

        return CloudSyncPayload(
            favorites = favMap.values.sortedByDescending { it.updatedAt }.take(MAX_FAVORITES),
            continueWatching = contMap.values.sortedByDescending { it.updatedAt }.take(MAX_CONTINUE),
            updatedAt = maxOf(local.updatedAt, remote.updatedAt, nowMs)
        )
    }

    fun buildRequestJson(code: String, payload: CloudSyncPayload): String {
        val favArray = JSONArray()
        payload.favorites.take(MAX_FAVORITES).forEach { f ->
            favArray.put(
                JSONObject().apply {
                    put("key", f.key)
                    put("name", f.name.take(200))
                    put("url", f.url.take(2048))
                    put("logo", f.logo.take(2048))
                    put("group", f.group.take(120))
                    put("contentType", f.contentType.take(24))
                    put("deleted", f.deleted)
                    put("updatedAt", f.updatedAt)
                }
            )
        }

        val contArray = JSONArray()
        payload.continueWatching.take(MAX_CONTINUE).forEach { c ->
            contArray.put(
                JSONObject().apply {
                    put("key", c.key)
                    put("name", c.name.take(200))
                    put("url", c.url.take(2048))
                    put("logo", c.logo.take(2048))
                    put("group", c.group.take(120))
                    put("contentType", c.contentType.take(24))
                    put("positionSec", c.positionSec.coerceAtLeast(0))
                    put("durationSec", c.durationSec.coerceAtLeast(0))
                    put("deleted", c.deleted)
                    put("updatedAt", c.updatedAt)
                }
            )
        }

        return JSONObject().apply {
            put("code", normalizeSyncCode(code))
            put(
                "payload",
                JSONObject().apply {
                    put("favorites", favArray)
                    put("continueWatching", contArray)
                    put("updatedAt", payload.updatedAt)
                }
            )
        }.toString()
    }

    fun parseResponseJson(rawJson: String): CloudSyncResult {
        return try {
            val root = JSONObject(rawJson)
            if (!root.optBoolean("ok", false)) {
                return CloudSyncResult(
                    ok = false,
                    error = root.optString("error", "Falha ao sincronizar com o servidor.")
                )
            }
            val code = normalizeSyncCode(root.optString("code", ""))
            val payloadObj = root.optJSONObject("payload") ?: JSONObject()

            val favorites = mutableListOf<SyncFavoriteItem>()
            val favArr = payloadObj.optJSONArray("favorites")
            if (favArr != null) {
                for (i in 0 until minOf(favArr.length(), MAX_FAVORITES)) {
                    val obj = favArr.optJSONObject(i) ?: continue
                    val key = obj.optString("key", "").trim().ifEmpty { obj.optString("url", "").trim() }
                    if (key.isEmpty()) continue
                    favorites.add(
                        SyncFavoriteItem(
                            key = key,
                            name = obj.optString("name", ""),
                            url = obj.optString("url", ""),
                            logo = obj.optString("logo", ""),
                            group = obj.optString("group", ""),
                            contentType = obj.optString("contentType", "LIVE"),
                            deleted = obj.optBoolean("deleted", false),
                            updatedAt = obj.optLong("updatedAt", 0L)
                        )
                    )
                }
            }

            val continueList = mutableListOf<SyncContinueItem>()
            val contArr = payloadObj.optJSONArray("continueWatching")
            if (contArr != null) {
                for (i in 0 until minOf(contArr.length(), MAX_CONTINUE)) {
                    val obj = contArr.optJSONObject(i) ?: continue
                    val key = obj.optString("key", "").trim().ifEmpty { obj.optString("url", "").trim() }
                    if (key.isEmpty()) continue
                    continueList.add(
                        SyncContinueItem(
                            key = key,
                            name = obj.optString("name", ""),
                            url = obj.optString("url", ""),
                            logo = obj.optString("logo", ""),
                            group = obj.optString("group", ""),
                            contentType = obj.optString("contentType", "MOVIE"),
                            positionSec = obj.optInt("positionSec", 0).coerceAtLeast(0),
                            durationSec = obj.optInt("durationSec", 0).coerceAtLeast(0),
                            deleted = obj.optBoolean("deleted", false),
                            updatedAt = obj.optLong("updatedAt", 0L)
                        )
                    )
                }
            }

            val updatedAt = root.optLong("updatedAt", payloadObj.optLong("updatedAt", System.currentTimeMillis()))
            CloudSyncResult(
                ok = true,
                code = code,
                payload = CloudSyncPayload(
                    favorites = favorites,
                    continueWatching = continueList,
                    updatedAt = updatedAt
                ),
                updatedAt = updatedAt
            )
        } catch (e: Throwable) {
            CloudSyncResult(ok = false, error = "Resposta inválida do servidor de sincronização.")
        }
    }

    fun syncWithServer(
        code: String,
        localPayload: CloudSyncPayload,
        client: OkHttpClient,
        endpoint: String = DEFAULT_SYNC_ENDPOINT
    ): CloudSyncResult {
        val normalized = normalizeSyncCode(code)
        if (!isValidSyncCode(normalized)) {
            return CloudSyncResult(ok = false, error = "Código inválido. Use o formato MK21-XXXX-XXXX.")
        }
        return try {
            val bodyJson = buildRequestJson(normalized, localPayload)
            val request = Request.Builder()
                .url(endpoint)
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val parsedErr = runCatching { JSONObject(rawBody).optString("error", "") }.getOrDefault("")
                    return CloudSyncResult(
                        ok = false,
                        error = parsedErr.ifEmpty { "Servidor de sincronização retornou HTTP ${response.code}." }
                    )
                }
                parseResponseJson(rawBody)
            }
        } catch (e: Throwable) {
            CloudSyncResult(
                ok = false,
                error = "Sem conexão com o servidor de sincronização: ${e.message ?: "erro de rede"}"
            )
        }
    }
}
