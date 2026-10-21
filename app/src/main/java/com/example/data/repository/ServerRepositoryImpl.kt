package com.example.data.repository

import android.util.Log
import com.example.data.local.ServerDao
import com.example.data.model.ServerProfile
import com.example.data.preferences.PreferencesService
import com.example.data.remote.ServerApi
import com.example.domain.repository.ServerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Implementação do repositório de servidores IPTV.
 * Gerencia persistência local no Room, cache de preferências e requisições remotas via Retrofit.
 */
class ServerRepositoryImpl(
    private val serverDao: ServerDao,
    private val serverApi: ServerApi,
    private val preferencesService: PreferencesService
) : ServerRepository {

    private val staticDefaultServers = listOf(
        ServerProfile("srv_1", "VLOG", "http://myopbx.beer"),
        ServerProfile("srv_2", "TANNIX", "http://poptvcdn.online"),
        ServerProfile("srv_3", "LUB TV", "http://pottermax.sbs"),
        ServerProfile("srv_4", "MK21 PRÓ", "http://app.vivoxi.xyz"),
        ServerProfile("srv_5", "CB6000", "http://cdn.caterlune.top"),
        ServerProfile("srv_6", "CINELON", "http://coliseuop.site"),
        ServerProfile("srv_7", "MULTT TV", "http://dali-as.skin")
    )

    override fun getAllServers(): Flow<List<ServerProfile>> = serverDao.getAllServers()

    override fun getActiveServers(): Flow<List<ServerProfile>> = serverDao.getActiveServers()

    override suspend fun getServerById(id: String): ServerProfile? = withContext(Dispatchers.IO) {
        serverDao.getServerById(id)
    }

    override suspend fun insertServers(servers: List<ServerProfile>) = withContext(Dispatchers.IO) {
        serverDao.insertServers(servers)
    }

    override suspend fun insertServer(server: ServerProfile) = withContext(Dispatchers.IO) {
        serverDao.insertServer(server)
    }

    override suspend fun updateServer(server: ServerProfile) = withContext(Dispatchers.IO) {
        serverDao.updateServer(server)
    }

    override suspend fun deleteServers(serverIds: List<String>) = withContext(Dispatchers.IO) {
        serverDao.deleteServers(serverIds)
    }

    override suspend fun toggleServerActive(serverId: String, isActive: Boolean) = withContext(Dispatchers.IO) {
        serverDao.toggleServerActive(serverId, isActive)
    }

    override suspend fun applyCredentialsToAllServers(username: String, password: String) = withContext(Dispatchers.IO) {
        serverDao.updateCredentialsForAll(username, password)
    }

    override suspend fun fetchDynamicServers(url: String): Result<List<ServerProfile>> = withContext(Dispatchers.IO) {
        try {
            val response = serverApi.getDynamicServersRaw(url)
            if (response.isSuccessful) {
                val rawJson = response.body()?.string() ?: ""
                if (rawJson.isNotBlank()) {
                    val parsed = parseJson(rawJson)
                    if (parsed.isNotEmpty()) {
                        preferencesService.cachedServersJson = rawJson
                        
                        // Aplica credenciais salvas aos novos servidores
                        val currentUsername = preferencesService.username
                        val currentPassword = preferencesService.password
                        val serversToSave = parsed.map { server ->
                            server.copy(
                                username = server.username ?: currentUsername.ifEmpty { null },
                                password = server.password ?: currentPassword.ifEmpty { null }
                            )
                        }
                        serverDao.insertServers(serversToSave)
                        return@withContext Result.success(serversToSave)
                    }
                }
            }
            Result.failure(Exception("Resposta inválida ou vazia do servidor remoto: ${response.code()}"))
        } catch (e: Exception) {
            Log.e("ServerRepositoryImpl", "Erro ao buscar servidores dinâmicos: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun loadCachedOrPredefinedServers(): List<ServerProfile> = withContext(Dispatchers.IO) {
        val count = serverDao.getServerCount()
        if (count > 0) {
            // Auto-atualização de URLs de servidores padrão que mudaram
            try {
                val existing = serverDao.getAllServersList()
                for (srv in existing) {
                    val updatedUrl = when {
                        srv.name.equals("LUB TV", ignoreCase = true) || srv.baseUrl.contains("alfatecloan.sbs") || srv.baseUrl.contains("lubtv.fun") -> "http://pottermax.sbs"
                        srv.name.equals("TANNIX", ignoreCase = true) || srv.baseUrl.contains("gip26.sbs") || srv.baseUrl.contains("tannix26.shop") -> "http://poptvcdn.online"
                        srv.name.equals("MK21 PRÓ", ignoreCase = true) || srv.name.equals("MK21 TV", ignoreCase = true) || srv.baseUrl.contains("tigerouro.shop") || srv.baseUrl.contains("mk21.uk") -> "http://app.vivoxi.xyz"
                        srv.name.equals("MULTT TV", ignoreCase = true) || srv.baseUrl.contains("hll4.top") -> "http://dali-as.skin"
                        else -> null
                    }
                    if (updatedUrl != null && srv.baseUrl != updatedUrl) {
                        serverDao.updateServer(
                            srv.copy(
                                baseUrl = updatedUrl,
                                name = if (srv.name.equals("MK21 TV", ignoreCase = true)) "MK21 PRÓ" else srv.name
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("ServerRepositoryImpl", "Erro ao migrar URLs de servidores: ${e.message}")
            }
            return@withContext emptyList()
        }

        val cachedJson = preferencesService.cachedServersJson
        val serversToSeed = if (cachedJson.isNotBlank()) {
            val fromCache = parseJson(cachedJson)
            if (fromCache.isNotEmpty()) fromCache else staticDefaultServers
        } else {
            staticDefaultServers
        }

        val currentUsername = preferencesService.username
        val currentPassword = preferencesService.password
        val seeded = serversToSeed.map { server ->
            server.copy(
                username = currentUsername.ifEmpty { null },
                password = currentPassword.ifEmpty { null },
                isActive = true
            )
        }
        serverDao.insertServers(seeded)
        seeded
    }

    private fun parseJson(jsonString: String): List<ServerProfile> {
        val list = mutableListOf<ServerProfile>()
        try {
            val array = JSONArray(jsonString.trim())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", "srv_$i")
                val name = obj.optString("name", "Servidor ${i + 1}")
                val baseUrl = obj.optString("baseUrl", "")
                val username = obj.optString("username", "").ifEmpty { null }
                val password = obj.optString("password", "").ifEmpty { null }
                val isActive = obj.optBoolean("isActive", true)

                if (baseUrl.isNotBlank() && (baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))) {
                    list.add(
                        ServerProfile(
                            id = id,
                            name = name,
                            baseUrl = baseUrl.removeSuffix("/"),
                            username = username,
                            password = password,
                            isActive = isActive
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("ServerRepositoryImpl", "Falha ao analisar JSON de servidores: ${e.message}")
        }
        return list
    }
}
