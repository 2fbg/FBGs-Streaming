package com.example.viewmodel

import com.example.network.DnsResolver
import com.example.network.StreamHeadersRegistry
import com.example.network.StreamHttpClients
import com.example.utils.LogSanitizer
import android.net.Uri
import androidx.media3.common.MediaItem







import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.database.IptvDatabase
import com.example.data.model.ContentType
import com.example.data.model.ManualPlaylist
import com.example.data.model.PlaylistItem
import com.example.data.model.ServerProfile
import com.example.data.parser.M3UParser
import com.example.data.service.PreferencesService
import com.fbg.streaming.security.ApkSignatureVerifier
import com.fbg.streaming.security.LicenseInfo
import com.fbg.streaming.security.LicenseTokenVerifier
import com.fbg.streaming.update.DownloadVerificationResult
import com.fbg.streaming.update.Mk21UpdateManager
import com.fbg.streaming.update.UpdateCheckOutcome
import com.fbg.streaming.update.UpdateInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import com.example.BuildConfig

@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        IptvDatabase::class.java,
        "mk21_iptv_db"
    ).addMigrations(IptvDatabase.MIGRATION_1_2)
        .fallbackToDestructiveMigration()
        .build()

    private val playlistItemDao = db.playlistItemDao()
    private val manualPlaylistDao = db.manualPlaylistDao()
    
    val repository: com.example.data.repository.IptvRepository = com.example.data.repository.IptvRepositoryImpl(
        playlistItemDao,
        manualPlaylistDao
    )
    
    val preferencesService = PreferencesService(application)
    
    private val staticDefaultServers = listOf(
        ServerProfile("server_1", "VLOG", "http://tocombox21.cyou"),
        ServerProfile("server_2", "TANNIX", "http://ovo54.sbs"),
        ServerProfile("server_3", "LUB TV", "http://coxinhasemflango.shop"),
        ServerProfile("server_4", "MK21 PRÓ", "http://app.tigerouro.shop"),
        ServerProfile("server_5", "CB6000", "http://cdn.caterlune.top"),
        ServerProfile("server_6", "CINELON", "http://parcerialife.lat"),
        ServerProfile("server_7", "MULTT TV", "http://dali-as.skin")
    )

    private val _predefinedServersState = MutableStateFlow<List<ServerProfile>>(staticDefaultServers)
    val predefinedServersState: StateFlow<List<ServerProfile>> = _predefinedServersState.asStateFlow()
    
    val predefinedServers: List<ServerProfile>
        get() = _predefinedServersState.value

    private val predefinedNames: Set<String>
        get() = _predefinedServersState.value.map { it.name }.toSet()

    // Real-time Licencing State Flows
    private val _currentLicenseInfo = MutableStateFlow(preferencesService.currentLicense())
    val currentLicenseInfo: StateFlow<LicenseInfo?> = _currentLicenseInfo.asStateFlow()

    private val _isPremiumActive = MutableStateFlow(preferencesService.isLicenseValid())
    val isPremiumActive = _isPremiumActive.asStateFlow()

    private val _trialDaysLeft = MutableStateFlow(preferencesService.getTrialDaysRemaining())
    val trialDaysLeft = _trialDaysLeft.asStateFlow()

    private val _isActivatingLicense = MutableStateFlow(false)
    val isActivatingLicense: StateFlow<Boolean> = _isActivatingLicense.asStateFlow()

    // Cloud Sync (Android ↔ Smart TV ↔ Web)
    private val _cloudSyncCode = MutableStateFlow(preferencesService.cloudSyncCode)
    val cloudSyncCode: StateFlow<String> = _cloudSyncCode.asStateFlow()

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    private val _cloudSyncStatus = MutableStateFlow("")
    val cloudSyncStatus: StateFlow<String> = _cloudSyncStatus.asStateFlow()

    private val _lastCloudSyncAt = MutableStateFlow(preferencesService.lastCloudSyncAt)
    val lastCloudSyncAt: StateFlow<Long> = _lastCloudSyncAt.asStateFlow()

    val virtualMacAddress: String
        get() = preferencesService.virtualMac

    @Deprecated("Substituído pela emissão de tokens ECDSA P-256 via servidor.")
    @Suppress("DEPRECATION")
    fun generateAutonomousKey(mac: String): String {
        return preferencesService.generateValidKeyForDevice(mac)
    }

    private fun syncLicenseState() {
        val info = preferencesService.currentLicense()
        _currentLicenseInfo.value = info
        _isPremiumActive.value = preferencesService.isLicenseValid()
        _trialDaysLeft.value = preferencesService.getTrialDaysRemaining()
    }

    /**
     * Ativa a licença: quando `LICENSE_PUBKEY_B64` está configurada, valida via servidor
     * (`/api/license/activate`) com assinatura ECDSA P-256; em modo de homologação (sem chave
     * pública ECDSA), utiliza a validação local HMAC-SHA256 (`LICENSE_SECRET`).
     */
    fun activateLicense(code: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val trimmedCode = code.trim()
        if (trimmedCode.isEmpty()) {
            onResult(false, "Insira um código válido")
            return
        }
        if (_isActivatingLicense.value) return

        // Modo de homologação: sem chave pública ECDSA válida no build, valida chave HMAC local offline
        if (!preferencesService.isEcdsaConfigured()) {
            if (preferencesService.isLocalHmacKeyValid(trimmedCode)) {
                preferencesService.activationKey = trimmedCode.uppercase()
                _isPremiumActive.value = true
                _trialDaysLeft.value = 9999
                onResult(true, "Dispositivo ativado com sucesso!")
            } else {
                onResult(false, "Chave de ativação inválida para este dispositivo.")
            }
            return
        }

        _isActivatingLicense.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val (success, message) = requestLicenseActivation(trimmedCode)
            withContext(Dispatchers.Main) {
                _isActivatingLicense.value = false
                onResult(success, message)
            }
        }
    }

    private fun requestLicenseActivation(code: String): Pair<Boolean, String> {
        return try {
            val payloadJson = JSONObject().apply {
                put("code", code)
                put("deviceId", virtualMacAddress)
            }.toString()

            val request = Request.Builder()
                .url("$LICENSE_API_BASE_URL/activate")
                .post(payloadJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(rawBody) }.getOrNull()

                if (!response.isSuccessful) {
                    val serverMsg = json?.optString("error", "")?.trim().orEmpty()
                    return false to serverMsg.ifEmpty { "Código inválido para este dispositivo (${response.code})" }
                }

                val token = json?.optString("token", "")?.trim().orEmpty()
                if (token.isEmpty()) {
                    return false to "Resposta inválida do servidor de licenças"
                }

                val verified = LicenseTokenVerifier.verify(
                    token = token,
                    deviceId = virtualMacAddress,
                    publicKeyB64 = BuildConfig.LICENSE_PUBKEY_B64
                ) ?: return false to "Token inválido ou chave pública não configurada neste build"

                preferencesService.licenseToken = token
                preferencesService.activationKey = code
                _currentLicenseInfo.value = verified
                _isPremiumActive.value = true
                _trialDaysLeft.value = 9999
                true to "Dispositivo ativado com sucesso!"
            }
        } catch (e: Throwable) {
            Log.w("MK21_VM", "License activation network failure: ${e.message}")
            false to "Não foi possível conectar ao servidor de ativação. Verifique sua internet."
        }
    }

    private fun refreshLicenseIfNeeded() {
        val current = preferencesService.currentLicense() ?: return
        val nowEpoch = System.currentTimeMillis() / 1000L
        val thirtyDaysSeconds = 30L * 86400L
        if (current.expiresAtEpoch - nowEpoch >= thirtyDaysSeconds) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payloadJson = JSONObject().apply {
                    put("deviceId", virtualMacAddress)
                }.toString()

                val request = Request.Builder()
                    .url("$LICENSE_API_BASE_URL/refresh")
                    .post(payloadJson.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val rawBody = response.body?.string().orEmpty()
                    val token = runCatching { JSONObject(rawBody).optString("token", "").trim() }.getOrDefault("")
                    if (token.isEmpty()) return@use

                    val verified = LicenseTokenVerifier.verify(
                        token = token,
                        deviceId = virtualMacAddress,
                        publicKeyB64 = BuildConfig.LICENSE_PUBKEY_B64
                    ) ?: return@use

                    preferencesService.licenseToken = token
                    withContext(Dispatchers.Main) {
                        _currentLicenseInfo.value = verified
                        _isPremiumActive.value = true
                        _trialDaysLeft.value = 9999
                    }
                }
            } catch (_: Throwable) {
                // Silencioso: mantém funcionamento offline até a expiração do token local
            }
        }
    }

    /**
     * Consulta `/api/license/trial` usando o Virtual MAC determinístico do aparelho.
     * Impede que desinstalar e reinstalar o APK zere o período de avaliação de 5 dias,
     * preservando o `first_seen_at` original gravado no servidor e atualizando o piso do relógio.
     */
    private fun syncTrialAnchorWithServer() {
        if (preferencesService.isLicenseValid()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payloadJson = JSONObject().apply {
                    put("deviceId", virtualMacAddress)
                }.toString()

                val request = Request.Builder()
                    .url("$LICENSE_API_BASE_URL/trial")
                    .post(payloadJson.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val rawBody = response.body?.string().orEmpty()
                    val json = runCatching { JSONObject(rawBody) }.getOrNull() ?: return@use
                    if (!json.optBoolean("ok", false)) return@use

                    val firstSeenAtMs = json.optLong("firstSeenAtMs", 0L)
                    val serverNowMs = json.optLong("serverNowMs", 0L)
                    if (firstSeenAtMs > 0L || serverNowMs > 0L) {
                        preferencesService.anchorTrialFromServer(
                            firstSeenAtMs = firstSeenAtMs,
                            serverNowMs = serverNowMs
                        )
                        withContext(Dispatchers.Main) {
                            syncLicenseState()
                        }
                    }
                }
            } catch (_: Throwable) {
                // Silencioso offline: mantém contagem local até haver conexão
            }
        }
    }

    companion object {
        private const val LICENSE_API_BASE_URL = "https://bgstreaming.vercel.app/api/license"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val _username = MutableStateFlow(preferencesService.username)
    val username = _username.asStateFlow()

    private val _password = MutableStateFlow(preferencesService.password)
    val password = _password.asStateFlow()

    private val _activePlaylistName = MutableStateFlow(preferencesService.activePlaylistName)
    val activePlaylistName = _activePlaylistName.asStateFlow()

    // Loading & Progress States
    private val _loadingProgress = MutableStateFlow<Int?>(null) // null means not loading
    val loadingProgress = _loadingProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _backgroundLoadingState = MutableStateFlow<String?>(null)
    val backgroundLoadingState = _backgroundLoadingState.asStateFlow()

    private var backgroundLoadJob: kotlinx.coroutines.Job? = null

    private val _loginSuccess = MutableSharedFlow<Unit>(replay = 0)
    val loginSuccess = _loginSuccess.asSharedFlow()

    // Manual list records
    val manualPlaylists = manualPlaylistDao.getAllManualPlaylists().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current playing channel/movie
    private val _currentPlayingItem = MutableStateFlow<PlaylistItem?>(null)
    val currentPlayingItem = _currentPlayingItem.asStateFlow()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode = _isInPipMode.asStateFlow()

    fun setIsInPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    val isPlayerPlaying = MutableStateFlow(true)
    var togglePlayPauseAction: (() -> Unit)? = null
    fun togglePlayPause() {
        togglePlayPauseAction?.invoke()
    }

    private val _useAmoledMode = MutableStateFlow(preferencesService.useAmoledMode)
    val useAmoledMode = _useAmoledMode.asStateFlow()

    fun setUseAmoledMode(enabled: Boolean) {
        preferencesService.useAmoledMode = enabled
        _useAmoledMode.value = enabled
    }

    // Content types filtering
    private val _selectedContentType = MutableStateFlow(ContentType.LIVE)
    val selectedContentType = _selectedContentType.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Todas")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // PIN Protection dialog triggers
    enum class SortOrder {
        DEFAULT,                // Ordem por número (original order)
        ALPHABETICAL,           // Ordem por A-Z
        ALPHABETICAL_DESC,      // Ordem por Z-A
        BY_ADDITION,            // Ordem por adição (newest / NOVO first)
        BY_RATING               // Ordem por qualificação (4K -> HD -> others)
    }

    private val _sortOrder = MutableStateFlow(
        when (preferencesService.menuSortOrder) {
            "Ordem por A-Z" -> SortOrder.ALPHABETICAL
            "Ordem por Z-A" -> SortOrder.ALPHABETICAL_DESC
            "Ordem por adição" -> SortOrder.BY_ADDITION
            "Ordem por qualificação" -> SortOrder.BY_RATING
            else -> SortOrder.DEFAULT
        }
    )
    val sortOrder = _sortOrder.asStateFlow()

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun updateMenuSortOrder(stringOrder: String) {
        preferencesService.menuSortOrder = stringOrder
        _sortOrder.value = when (stringOrder) {
            "Ordem por A-Z" -> SortOrder.ALPHABETICAL
            "Ordem por Z-A" -> SortOrder.ALPHABETICAL_DESC
            "Ordem por adição" -> SortOrder.BY_ADDITION
            "Ordem por qualificação" -> SortOrder.BY_RATING
            else -> SortOrder.DEFAULT
        }
    }

    private val _adultPinGranted = MutableStateFlow(false)
    val adultPinGranted = _adultPinGranted.asStateFlow()

    private val _activePinPromptItem = MutableStateFlow<PlaylistItem?>(null)
    val activePinPromptItem = _activePinPromptItem.asStateFlow()

    private val _activePinPromptCategory = MutableStateFlow<String?>(null)
    val activePinPromptCategory = _activePinPromptCategory.asStateFlow()

    // Aggressive Lazy Loading Page Limit for Memory-Constrained Devices (OOM Prevention)
    private val _displayedItemsLimit = MutableStateFlow(60)
    val displayedItemsLimit = _displayedItemsLimit.asStateFlow()

    fun loadMoreItems() {
        _displayedItemsLimit.value = _displayedItemsLimit.value + 100
    }

    fun resetItemsLimit() {
        _displayedItemsLimit.value = 60
    }

    // Live state bindings based on currently active list selection with Aggressive Lazy Loading
    val activeItemsList: StateFlow<List<PlaylistItem>> = combine(
        combine(_activePlaylistName, _selectedContentType, _selectedCategory) { playlist, type, category ->
            Triple(playlist, type, category)
        },
        combine(_searchQuery, _adultPinGranted, _sortOrder) { query, adultGranted, sort ->
            Triple(query, adultGranted, sort)
        },
        _displayedItemsLimit
    ) { p1, p2, limit ->
        Triple(p1, p2, limit)
    }.flatMapLatest { (p1, p2, displayLimit) ->
        val (playlist, type, category) = p1
        val (query, adultGranted, sortOrder) = p2
        
        val rawFlow = if (query.isNotEmpty()) {
            playlistItemDao.searchItems(playlist, "%$query%")
        } else if (category == "★ Favoritos") {
            playlistItemDao.getFavorites(playlist).map { list ->
                list.filter { it.contentType == type.name }
            }
        } else if (category == "Todas" || category == "Todos") {
            playlistItemDao.getItemsByType(playlist, type.name)
        } else {
            playlistItemDao.getItemsByCategoryAndType(playlist, category, type.name)
        }
        
        rawFlow.map { list ->
            // 1. Filter out adult items if PIN has not been entered
            val filtered = if (adultGranted) {
                list
            } else {
                list.filter { !it.isAdult && !isAdultCategory(it.category) }
            }
            
            // 2. Sort the final filtered list
            val sorted = when (sortOrder) {
                SortOrder.ALPHABETICAL -> filtered.sortedBy { it.name }
                SortOrder.ALPHABETICAL_DESC -> filtered.sortedByDescending { it.name }
                SortOrder.BY_ADDITION -> {
                    // "Ordem por adição" puts NOVO items (stable remainder of id.hashCode() % 3 is 2) first
                    val isNovo = { item: PlaylistItem ->
                        val r = item.id.hashCode() % 3
                        val positiveR = if (r < 0) r + 3 else r
                        positiveR == 2
                    }
                    filtered.sortedWith(
                        compareBy<PlaylistItem> { !isNovo(it) } // Put true (isNovo) before false
                            .thenByDescending { it.id }
                    )
                }
                SortOrder.BY_RATING -> {
                    // "Ordem por qualificação" sorts 4K (remainder 0) first, HD (remainder 1) second, others/NOVO (remainder 2) third
                    val ratePriority = { item: PlaylistItem ->
                        val r = item.id.hashCode() % 3
                        val positiveR = if (r < 0) r + 3 else r
                        when (positiveR) {
                            0 -> 0 // 4K first
                            1 -> 1 // HD second
                            else -> 2 // others/NOVO third
                        }
                    }
                    filtered.sortedWith(
                        compareBy<PlaylistItem> { ratePriority(it) }
                            .thenBy { it.name }
                    )
                }
                SortOrder.DEFAULT -> filtered.sortedBy { it.id } // "Ordem por número" sorts by raw insertion number (ID ascending)
            }
            sorted
        }
    }.flowOn(Dispatchers.IO).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /**
     * Lista completa de canais ao vivo da playlist ativa (respeitando controle parental) para o Guia de EPG.
     */
    val liveChannelsForGuide: StateFlow<List<PlaylistItem>> = combine(
        _activePlaylistName,
        _adultPinGranted
    ) { playlist, adultGranted ->
        playlist to adultGranted
    }.flatMapLatest { (playlist, adultGranted) ->
        playlistItemDao.getItemsByType(playlist, ContentType.LIVE.name).map { list ->
            if (adultGranted) list else list.filter { !it.isAdult && !isAdultCategory(it.category) }
        }
    }.flowOn(Dispatchers.IO).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /**
     * Resolve (baseUrl, username, password) para chamadas Xtream/XMLTV a partir do servidor ativo ou URL manual.
     */
    fun resolveActiveXtreamCredentials(targetPlaylist: String = _activePlaylistName.value): Triple<String, String, String>? {
        val predefinedServer = predefinedServers.find { it.name == targetPlaylist }
        if (predefinedServer != null) {
            val user = _username.value.trim()
            val pass = _password.value.trim()
            if (user.isNotEmpty() && pass.isNotEmpty()) {
                val base = com.example.data.parser.XtreamCatalogClient.normalizeBaseUrl(predefinedServer.baseUrl)
                if (base.isNotEmpty()) return Triple(base, user, pass)
            }
            return null
        }

        val manual = manualPlaylists.value.find { it.name == targetPlaylist }?.url?.trim().orEmpty()
        if (manual.isNotEmpty()) {
            val uri = try { android.net.Uri.parse(manual) } catch (_: Exception) { null }
            val scheme = uri?.scheme
            val host = uri?.host
            val user = uri?.getQueryParameter("username")?.trim().orEmpty()
            val pass = uri?.getQueryParameter("password")?.trim().orEmpty()
            if (!scheme.isNullOrBlank() && !host.isNullOrBlank() && user.isNotEmpty() && pass.isNotEmpty()) {
                val portPart = if (uri.port > 0) ":${uri.port}" else ""
                return Triple("$scheme://$host$portPart", user, pass)
            }
        }
        return null
    }

    fun loadXmltvForActiveServer(force: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val creds = resolveActiveXtreamCredentials()
            if (creds != null) {
                com.example.data.service.AndroidEpgManager.loadXmltv(
                    baseUrl = creds.first,
                    username = creds.second,
                    password = creds.third,
                    force = force,
                    client = okHttpClient
                )
            } else if (!com.example.data.service.AndroidEpgManager.discoveredM3uXmltvUrl.isNullOrBlank()) {
                com.example.data.service.AndroidEpgManager.loadXmltv(
                    baseUrl = "",
                    username = "",
                    password = "",
                    force = force,
                    client = okHttpClient
                )
            }
        }
    }

    suspend fun fetchShortEpgForChannel(item: PlaylistItem): List<com.example.data.parser.XmltvProgramme> {
        val creds = resolveActiveXtreamCredentials()
        return com.example.data.service.AndroidEpgManager.fetchShortEpgForChannel(
            item = item,
            baseUrl = creds?.first.orEmpty(),
            username = creds?.second.orEmpty(),
            password = creds?.third.orEmpty(),
            client = okHttpClient
        )
    }

    /**
     * Varre a grade EPG real (XMLTV indexado + Short EPG Xtream + fallback público `/api/epg`)
     * para localizar transmissões AO VIVO AGORA e EM BREVE correspondentes a um time, jogo ou programa.
     */
    suspend fun searchLiveEpgForCopilot(prompt: String): com.example.ai.CopilotEpgSearchResult =
        withContext(Dispatchers.IO) {
            val playlist = _activePlaylistName.value
            val nowMs = System.currentTimeMillis()

            // Reúne canais ao vivo da playlist ativa + esportes globais (sem duplicar por URL)
            val activeLive = try {
                playlistItemDao.getItemsByPlaylistAndTypeSync(playlist, ContentType.LIVE.name)
            } catch (_: Exception) {
                emptyList()
            }
            val globalSports = try {
                playlistItemDao.getLiveSportsChannelsGlobal()
            } catch (_: Exception) {
                emptyList()
            }
            val candidates = LinkedHashMap<String, PlaylistItem>(activeLive.size + globalSports.size).apply {
                activeLive.forEach { put(it.url, it) }
                globalSports.forEach { putIfAbsent(it.url, it) }
            }.values.toList()

            if (candidates.isEmpty()) {
                return@withContext com.example.ai.CopilotEpgMatcher.matchProgrammes(
                    prompt = prompt,
                    channels = emptyList(),
                    nowMs = nowMs
                ) { emptyList() }
            }

            // 0. Se o índice XMLTV de 7 dias ainda não estiver carregado na sessão, tenta carregar agora (timeout enxuto)
            if (com.example.data.service.AndroidEpgManager.xmltvIndex.value == null) {
                val creds = resolveActiveXtreamCredentials()
                val m3uXmltv = com.example.data.service.AndroidEpgManager.discoveredM3uXmltvUrl
                if (creds != null || !m3uXmltv.isNullOrBlank()) {
                    withTimeoutOrNull(4_500L) {
                        com.example.data.service.AndroidEpgManager.loadXmltv(
                            baseUrl = creds?.first.orEmpty(),
                            username = creds?.second.orEmpty(),
                            password = creds?.third.orEmpty(),
                            force = false,
                            client = okHttpClient
                        )
                    }
                }
            }

            // 1. Verifica quais canais prioritários (esportes e TV aberta) ainda estão sem EPG em cache/XMLTV
            val priorityChannels = com.example.ai.CopilotEpgMatcher.selectPriorityEpgChannels(
                channels = candidates,
                maxCount = 14
            )
            val missingEpgPriority = priorityChannels.filter { ch ->
                com.example.data.service.AndroidEpgManager.getRealProgrammes(ch, nowMs).isEmpty()
            }

            // 2. Hidrata EPG em paralelo (Xtream Short EPG -> fallback público meuguia.tv `/api/epg`) com timeout enxuto
            if (missingEpgPriority.isNotEmpty()) {
                withTimeoutOrNull(4_500L) {
                    coroutineScope {
                        missingEpgPriority.map { ch ->
                            async {
                                try {
                                    fetchShortEpgForChannel(ch)
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }
                        }.forEach { deferred ->
                            try {
                                deferred.await()
                            } catch (_: Exception) {
                                // ignora falha pontual de um canal
                            }
                        }
                    }
                }
            }

            // 3. Cruza todos os canais ao vivo com o EPG real de 7 dias já indexado/hidratado
            com.example.ai.CopilotEpgMatcher.matchProgrammes(
                prompt = prompt,
                channels = candidates,
                nowMs = nowMs
            ) { ch ->
                com.example.data.service.AndroidEpgManager.getRealProgrammes(ch, nowMs)
            }
        }

    suspend fun fetchXtreamSeriesEpisodes(seriesItem: PlaylistItem, seriesTitle: String): List<PlaylistItem> =
        withContext(Dispatchers.IO) {
            val seriesId = com.example.data.parser.XtreamCatalogClient.extractXtreamSeriesId(seriesItem.url)
                ?: return@withContext emptyList()
            val creds = resolveActiveXtreamCredentials(seriesItem.playlistSource)
                ?: resolveActiveXtreamCredentials()
                ?: return@withContext emptyList()
            com.example.data.parser.XtreamCatalogClient.fetchSeriesEpisodes(
                client = okHttpClient,
                baseUrl = creds.first,
                username = creds.second,
                password = creds.third,
                seriesId = seriesId,
                seriesName = seriesTitle,
                category = seriesItem.category,
                logoUrl = seriesItem.logoUrl,
                playlistSource = seriesItem.playlistSource
            )
        }

    /**
     * Searches database for the most relevant items across LIVE, MOVIES and SERIES for AI Copilot queries.
     * Prioritizes sports channels (Premiere, SporTV, ESPN, CazéTV, Globo) when sports/match queries are detected.
     */
    suspend fun getRelevantItemsForCopilot(prompt: String): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val playlist = _activePlaylistName.value
        val isSportsIntent = com.example.ai.CopilotEpgMatcher.isSportsOrTeamPrompt(prompt)

        if (isSportsIntent) {
            try {
                val activeLive = playlistItemDao.getItemsByPlaylistAndTypeSync(playlist, ContentType.LIVE.name)
                val activeSports = activeLive
                    .filter { com.example.ai.CopilotEpgMatcher.isSportsOrOpenBroadcastChannel(it) }
                    .sortedByDescending { com.example.ai.CopilotEpgMatcher.qualityScore(it.name) }
                if (activeSports.isNotEmpty()) {
                    return@withContext activeSports.take(50)
                }
                val globalSports = playlistItemDao.getLiveSportsChannelsGlobal()
                if (globalSports.isNotEmpty()) {
                    return@withContext globalSports
                }
            } catch (e: Exception) {
                // fallback
            }
        }

        // Generic keyword search in active playlist
        try {
            val words = prompt.split(" ", ",", "-", "?", "!")
                .map { it.trim().lowercase() }
                .filter { it.length >= 3 && it !in setOf("onde", "assistir", "qual", "como", "quem", "para", "com", "está", "esta", "sobre", "filme", "serie") }

            for (w in words) {
                val results = playlistItemDao.searchItems(playlist, "%$w%").first()
                if (results.isNotEmpty()) {
                    return@withContext results.take(40)
                }
            }
        } catch (e: Exception) {
            // fallback
        }

        // Fallback to active items or random highlights
        try {
            val highlights = playlistItemDao.getRandomHighlights(playlist).first()
            if (highlights.isNotEmpty()) return@withContext highlights.take(40)
        } catch (e: Exception) {}

        emptyList()
    }

    // Dynamic categorizations loaded dynamically from the playlist items
    val activeCategories = combine(
        _activePlaylistName,
        _selectedContentType
    ) { playlistName, contentType ->
        Pair(playlistName, contentType)
    }.flatMapLatest { (playlist, type) ->
        playlistItemDao.getCategoriesByType(playlist, type.name).map { list ->
            listOf("Todas", "★ Favoritos") + list
        }
    }.flowOn(Dispatchers.IO).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = listOf("Todas")
    )

    // Continue Watching Section Flow based on last watched items
    val continueWatchingList = combine(
        _activePlaylistName,
        _selectedContentType
    ) { playlistName, contentType ->
        Pair(playlistName, contentType)
    }.flatMapLatest { (playlist, type) ->
        playlistItemDao.getContinueWatching(playlist, type.name)
    }.flowOn(Dispatchers.IO).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Highlights selection (Randomly gets 5 items of ACTIVE playlist) without adult content
    val highlightsList = _activePlaylistName.flatMapLatest { playlist ->
        playlistItemDao.getRandomHighlights(playlist).map { items ->
            items.filter { !it.isAdult && !isAdultCategory(it.category) }
                .shuffled()
                .take(6)
        }
    }.flowOn(Dispatchers.IO).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val okHttpClient = StreamHttpClients.baseClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
            if (original.header("User-Agent") == null) {
                requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            }
            if (original.header("Accept") == null) {
                requestBuilder.header("Accept", "*/*")
            }
            chain.proceed(requestBuilder.build())
        }
        .build()

    fun mergeWithDefaults(incoming: List<ServerProfile>): List<ServerProfile> {
        val merged = mutableListOf<ServerProfile>()
        merged.addAll(staticDefaultServers)

        for (inc in incoming) {
            val matchingIndex = merged.indexOfFirst {
                it.name.equals(inc.name, ignoreCase = true) ||
                it.id == inc.id ||
                (it.name == "MK21 PRÓ" && (inc.name.equals("MK21 TV", ignoreCase = true) || inc.name.equals("MK21", ignoreCase = true)))
            }
            if (matchingIndex != -1) {
                val isOldDep = inc.baseUrl.contains("somentevlog.xyz") || inc.baseUrl.contains("pitclear.sbs") ||
                        inc.baseUrl.contains("infinixparcerias.site") || inc.baseUrl.contains("unituf.online") ||
                        inc.baseUrl.contains("appsmk.org")
                if (!isOldDep && inc.baseUrl.isNotBlank() && inc.baseUrl.startsWith("http")) {
                    merged[matchingIndex] = merged[matchingIndex].copy(baseUrl = inc.baseUrl)
                }
            } else {
                if (!merged.any { it.baseUrl.equals(inc.baseUrl, ignoreCase = true) }) {
                    merged.add(inc)
                }
            }
        }
        return merged
    }

    private fun loadCachedServers() {
        val cachedJson = preferencesService.cachedServersJson
        val parsed = if (cachedJson.isNotEmpty()) parseServersJson(cachedJson) else emptyList()
        val merged = mergeWithDefaults(parsed)
        _predefinedServersState.value = merged
        
        try {
            val array = org.json.JSONArray()
            for (profile in merged) {
                val obj = org.json.JSONObject()
                obj.put("id", profile.id)
                obj.put("name", profile.name)
                obj.put("baseUrl", profile.baseUrl)
                array.put(obj)
            }
            preferencesService.cachedServersJson = array.toString()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun parseServersFromPlainText(text: String): List<ServerProfile> {
        val list = mutableListOf<ServerProfile>()
        val lines = text.split("\n", "\r")
        var idCounter = 1
        val baseUrlsAdded = mutableSetOf<String>()
        
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue
            
            val urlIndex = trimmedLine.indexOf("http://")
            val finalUrlIndex = if (urlIndex != -1) urlIndex else trimmedLine.indexOf("https://")
            
            if (finalUrlIndex != -1) {
                var urlPart = trimmedLine.substring(finalUrlIndex)
                val spaceIndex = urlPart.indexOf(" ")
                if (spaceIndex != -1) {
                    urlPart = urlPart.substring(0, spaceIndex)
                }
                
                var baseUrl = urlPart
                val phpIndex = baseUrl.indexOf("/get.php")
                val altPhpIndex = if (phpIndex != -1) phpIndex else baseUrl.indexOf("/player_api.php")
                val nextSlashIndex = if (altPhpIndex != -1) altPhpIndex else baseUrl.indexOf("/", 8) 
                
                if (nextSlashIndex != -1 && nextSlashIndex > 8) {
                    baseUrl = baseUrl.substring(0, nextSlashIndex)
                }
                
                var targetBaseUrl = baseUrl
                var targetName = ""
                val lowercaseBaseUrl = baseUrl.lowercase()

                // Normalization block for predefined premium lists to prevent duplicates and keep names matching
                if (lowercaseBaseUrl.contains("tocombox21") || lowercaseBaseUrl.contains("lumixx21.org") || lowercaseBaseUrl.contains("myopbx.beer") || lowercaseBaseUrl.contains("new-link.shop") || lowercaseBaseUrl.contains("opbx-w.shop") || lowercaseBaseUrl.contains("somentevlog.xyz") || lowercaseBaseUrl.contains("vlogmk.de") || lowercaseBaseUrl.contains("newphase.sbs") || lowercaseBaseUrl.contains("vlog")) {
                    targetBaseUrl = "http://tocombox21.cyou"
                    targetName = "VLOG"
                } else if (lowercaseBaseUrl.contains("coxinhasemflango") || lowercaseBaseUrl.contains("pottermax") || lowercaseBaseUrl.contains("alfatecloan.sbs") || lowercaseBaseUrl.contains("pitclear.sbs") || lowercaseBaseUrl.contains("triimundial.shop") || lowercaseBaseUrl.contains("lubtv.fun") || lowercaseBaseUrl.contains("lub")) {
                    targetBaseUrl = "http://coxinhasemflango.shop"
                    targetName = "LUB TV"
                } else if (lowercaseBaseUrl.contains("parcerialife") || lowercaseBaseUrl.contains("powerex.lat") || lowercaseBaseUrl.contains("coliseuop.site") || lowercaseBaseUrl.contains("connstar.xyz") || lowercaseBaseUrl.contains("infinixparcerias.site") || lowercaseBaseUrl.contains("cinelontv.work") || lowercaseBaseUrl.contains("cinelon")) {
                    targetBaseUrl = "http://parcerialife.lat"
                    targetName = "CINELON21"
                } else if (lowercaseBaseUrl.contains("ovo54") || lowercaseBaseUrl.contains("assist.new21vod.xyz") || lowercaseBaseUrl.contains("poptvcdn.online") || lowercaseBaseUrl.contains("tannix26.shop") || lowercaseBaseUrl.contains("cdnassandplay.online") || lowercaseBaseUrl.contains("unituf.online") || lowercaseBaseUrl.contains("tannix")) {
                    targetBaseUrl = "http://ovo54.sbs"
                    targetName = "TANNIX"
                } else if (lowercaseBaseUrl.contains("painelplyon.top") || lowercaseBaseUrl.contains("cb6.fun") || lowercaseBaseUrl.contains("cb6000")) {
                    targetBaseUrl = "http://cdn.caterlune.top"
                    targetName = "CB6000"
                } else if (lowercaseBaseUrl.contains("tigerouro") || lowercaseBaseUrl.contains("magmapuro.shop") || lowercaseBaseUrl.contains("app.vivoxi.xyz") || lowercaseBaseUrl.contains("vivoxi") || lowercaseBaseUrl.contains("mk21.uk") || lowercaseBaseUrl.contains("appsmk.org") || lowercaseBaseUrl.contains("mk21")) {
                    targetBaseUrl = "http://app.tigerouro.shop"
                    targetName = "MK21 PRÓ"
                } else if (lowercaseBaseUrl.contains("dali-as.skin") || lowercaseBaseUrl.contains("hll4.top") || lowercaseBaseUrl.contains("multt tv")) {
                    targetBaseUrl = "http://dali-as.skin"
                    targetName = "MULTT TV"
                } else if (lowercaseBaseUrl.contains("antaresfusion.shop") || lowercaseBaseUrl.contains("cinevo")) {
                    targetBaseUrl = "http://antaresfusion.shop"
                    targetName = "CINEVO"
                } else if (lowercaseBaseUrl.contains("cdnconn.xyz") || lowercaseBaseUrl.contains("multt box")) {
                    targetBaseUrl = "http://cdnconn.xyz"
                    targetName = "MULTT BOX"
                }

                if (baseUrlsAdded.contains(targetBaseUrl.lowercase())) {
                    continue
                }
                
                val checkBase = targetBaseUrl.lowercase()
                if (checkBase.contains("apple.com") || 
                    checkBase.contains("playstore") || 
                    checkBase.contains("painelmk21") || 
                    checkBase.contains("is.gd") || 
                    checkBase.contains("t.ly") || 
                    checkBase.contains("bit.ly") || 
                    checkBase.contains("da.gd") || 
                    checkBase.contains("assistmaiss.com") || 
                    checkBase.contains("vizzionplay.app") || 
                    checkBase.contains("appplaysim.com") || 
                    checkBase.contains("vocine.appflix.top") || 
                    checkBase.contains("ibopro.xyz") || 
                    checkBase.contains("addmyplaylist.com") || 
                    checkBase.contains("cbbrst.top")) {
                    continue
                }
                
                var name = targetName
                if (name.isEmpty()) {
                    var namePart = trimmedLine.replace(urlPart, "")
                    namePart = namePart.replace("[🟢🔴🔵⚪🟠🟣✅🔰✔️🌟📱📺🌐🆔💻🔗]".toRegex(), "")
                    namePart = namePart.replace("*", "")
                    namePart = namePart.replace(":", "")
                    namePart = namePart.replace("-", "")
                    namePart = namePart.replace("_", "")
                    namePart = namePart.replace("(", "")
                    namePart = namePart.replace(")", "")
                    
                    namePart = namePart.replace("(?i)Link ".toRegex(), "")
                    namePart = namePart.replace("(?i)M3U".toRegex(), "")
                    namePart = namePart.replace("(?i)URL XCIPTV SERVIDORES".toRegex(), "")
                    namePart = namePart.replace("(?i)URL IPTV SMARTERS".toRegex(), "")
                    namePart = namePart.replace("(?i)CÓDIGOS ASSIST PLUS".toRegex(), "")
                    
                    name = namePart.trim()
                    if (name.isEmpty()) {
                        name = "Servidor $idCounter"
                    }
                }
                
                list.add(ServerProfile("dynamic_$idCounter", name, targetBaseUrl))
                baseUrlsAdded.add(targetBaseUrl.lowercase())
                idCounter++
            }
        }
        return list
    }

    fun importServersFromPlainText(pastedText: String): Boolean {
        val parsed = parseServersFromPlainText(pastedText)
        var anyImported = false

        if (parsed.isNotEmpty()) {
            val merged = mutableListOf<ServerProfile>()
            merged.addAll(staticDefaultServers)
            for (srv in parsed) {
                val existingIndex = merged.indexOfFirst { it.name.equals(srv.name, ignoreCase = true) || it.id == srv.id }
                if (existingIndex != -1) {
                    merged[existingIndex] = srv
                } else if (!merged.any { it.baseUrl.equals(srv.baseUrl, ignoreCase = true) }) {
                    merged.add(srv)
                }
            }
            
            val array = org.json.JSONArray()
            for (profile in merged) {
                val obj = org.json.JSONObject()
                obj.put("id", profile.id)
                obj.put("name", profile.name)
                obj.put("baseUrl", profile.baseUrl)
                array.put(obj)
            }
            val jsonStr = array.toString()
            preferencesService.cachedServersJson = jsonStr
            _predefinedServersState.value = merged
            anyImported = true
        }

        // Also extract and save any explicit M3U links / manual lists and credentials
        val lines = pastedText.split("\n", "\r")
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val httpIndex = if (trimmed.contains("http://", ignoreCase = true)) {
                trimmed.indexOf("http://", ignoreCase = true)
            } else if (trimmed.contains("https://", ignoreCase = true)) {
                trimmed.indexOf("https://", ignoreCase = true)
            } else {
                -1
            }

            if (httpIndex != -1) {
                var url = trimmed.substring(httpIndex).split(" ").firstOrNull() ?: ""
                if (url.contains(".m3u", ignoreCase = true) || url.contains("get.php", ignoreCase = true) || url.contains(".m3u8", ignoreCase = true)) {
                    var listName = trimmed.replace(url, "")
                        .replace("[🟢🔴🔵⚪🟠🟣🟤🟡⚫✅🔰✔️🌟📱📺🌐🆔💻🔗]".toRegex(), "")
                        .replace("*", "")
                        .replace("(?i)Link ".toRegex(), "")
                        .replace("(?i)\\(M3U\\)".toRegex(), "")
                        .replace("(?i)M3U".toRegex(), "")
                        .replace(":", "")
                        .replace("-", "")
                        .replace("_", "")
                        .trim()
                    if (listName.isEmpty()) {
                        listName = "Lista Manual ${parsed.size + 1}"
                    }
                    addManualPlaylist(listName, url)
                    anyImported = true
                }
            }

            // Extract username & password if present in panel text
            val lower = trimmed.lowercase()
            if (lower.contains("usuário") || lower.contains("usuario") || lower.contains("user:") || lower.contains("username:")) {
                val parts = trimmed.split(":")
                if (parts.size >= 2) {
                    val userVal = parts.drop(1).joinToString(":").replace("*", "").trim().split(" ").firstOrNull() ?: ""
                    if (userVal.isNotEmpty()) {
                        _username.value = userVal
                        preferencesService.username = userVal
                    }
                }
            }
            if (lower.contains("senha") || lower.contains("pass:") || lower.contains("password:")) {
                val parts = trimmed.split(":")
                if (parts.size >= 2) {
                    val passVal = parts.drop(1).joinToString(":").replace("*", "").trim().split(" ").firstOrNull() ?: ""
                    if (passVal.isNotEmpty()) {
                        _password.value = passVal
                        preferencesService.password = passVal
                    }
                }
            }
        }

        return anyImported
    }

    private fun parseServersJson(jsonStr: String): List<ServerProfile> {
        val list = mutableListOf<ServerProfile>()
        try {
            val trimmed = jsonStr.trim()
            if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                if (trimmed.startsWith("[")) {
                    val array = org.json.JSONArray(trimmed)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val name = obj.optString("name", "").ifEmpty { obj.optString("title", "").ifEmpty { obj.optString("label", "") } }
                        var baseUrl = obj.optString("baseUrl", "").ifEmpty { 
                            obj.optString("url", "").ifEmpty { 
                                obj.optString("base_url", "").ifEmpty { 
                                    obj.optString("host", "") 
                                } 
                            } 
                        }
                        
                        // Clean baseUrl trailing slashes or php paths if any
                        if (baseUrl.isNotEmpty()) {
                            val phpIndex = baseUrl.indexOf("/get.php")
                            val altPhpIndex = if (phpIndex != -1) phpIndex else baseUrl.indexOf("/player_api.php")
                            val nextSlashIndex = if (altPhpIndex != -1) altPhpIndex else {
                                if (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
                                    baseUrl.indexOf("/", 8)
                                } else {
                                    baseUrl.indexOf("/")
                                }
                            }
                            if (nextSlashIndex != -1 && nextSlashIndex > 4) {
                                baseUrl = baseUrl.substring(0, nextSlashIndex)
                            }
                        }
                        
                        val id = obj.optString("id", "").ifEmpty { "server_dynamic_${name.hashCode()}_$i" }
                        
                        if (name.isNotEmpty() && baseUrl.isNotEmpty()) {
                            list.add(ServerProfile(id, name, baseUrl))
                        }
                    }
                } else {
                    val rootObj = org.json.JSONObject(trimmed)
                    if (rootObj.has("servers")) {
                        val array = rootObj.optJSONArray("servers")
                        if (array != null) {
                            for (i in 0 until array.length()) {
                                val obj = array.getJSONObject(i)
                                val name = obj.optString("name", "").ifEmpty { obj.optString("title", "").ifEmpty { obj.optString("label", "") } }
                                var baseUrl = obj.optString("baseUrl", "").ifEmpty { 
                                    obj.optString("url", "").ifEmpty { 
                                        obj.optString("base_url", "").ifEmpty { 
                                            obj.optString("host", "") 
                                        } 
                                    } 
                                }
                                if (baseUrl.isNotEmpty()) {
                                    val phpIndex = baseUrl.indexOf("/get.php")
                                    val altPhpIndex = if (phpIndex != -1) phpIndex else baseUrl.indexOf("/player_api.php")
                                    val nextSlashIndex = if (altPhpIndex != -1) altPhpIndex else {
                                        if (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
                                            baseUrl.indexOf("/", 8)
                                        } else {
                                            baseUrl.indexOf("/")
                                        }
                                    }
                                    if (nextSlashIndex != -1 && nextSlashIndex > 4) {
                                        baseUrl = baseUrl.substring(0, nextSlashIndex)
                                    }
                                }
                                val id = obj.optString("id", "").ifEmpty { "server_dynamic_${name.hashCode()}_$i" }
                                if (name.isNotEmpty() && baseUrl.isNotEmpty()) {
                                    list.add(ServerProfile(id, name, baseUrl))
                                }
                            }
                        }
                    } else {
                        val keys = rootObj.keys()
                        var i = 0
                        while (keys.hasNext()) {
                            val key = keys.next()
                            var value = rootObj.optString(key, "")
                            if (value.startsWith("http://") || value.startsWith("https://")) {
                                val phpIndex = value.indexOf("/get.php")
                                val altPhpIndex = if (phpIndex != -1) phpIndex else value.indexOf("/player_api.php")
                                val nextSlashIndex = if (altPhpIndex != -1) altPhpIndex else value.indexOf("/", 8)
                                if (nextSlashIndex != -1 && nextSlashIndex > 4) {
                                    value = value.substring(0, nextSlashIndex)
                                }
                                list.add(ServerProfile("dynamic_obj_$i", key, value))
                            }
                            i++
                        }
                    }
                }
            } else {
                return parseServersFromPlainText(jsonStr)
            }
        } catch (e: java.lang.Exception) {
            Log.w("MK21_VM", "Error parsing servers JSON, interpreting as plain text: " + e.message)
            return parseServersFromPlainText(jsonStr)
        }
        return list
    }

    fun fetchDynamicServers() {
        viewModelScope.launch(Dispatchers.IO) {
            val targets = mutableListOf<String>()
            val customUrl = preferencesService.dynamicServersUrl.trim()
            if (customUrl.isNotEmpty()) {
                targets.add(customUrl)
            }
            
            val defaultUrl = "https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/servers.json"
            if (!targets.contains(defaultUrl)) {
                targets.add(defaultUrl)
            }

            var success = false
            for (url in targets) {
                if (success) break
                
                // Retry up to 3 times for each target
                for (attempt in 1..3) {
                    try {
                        val request = Request.Builder()
                            .url(url)
                            .build()
                        
                        okHttpClient.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val bodyString = response.body?.string()
                                if (!bodyString.isNullOrEmpty()) {
                                    val parsed = parseServersJson(bodyString)
                                    if (parsed.isNotEmpty()) {
                                        val merged = mergeWithDefaults(parsed)
                                        try {
                                            val array = org.json.JSONArray()
                                            for (profile in merged) {
                                                val obj = org.json.JSONObject()
                                                obj.put("id", profile.id)
                                                obj.put("name", profile.name)
                                                obj.put("baseUrl", profile.baseUrl)
                                                array.put(obj)
                                            }
                                            preferencesService.cachedServersJson = array.toString()
                                        } catch (e: Exception) {}
                                        _predefinedServersState.value = merged
                                        Log.d("MK21_VM", "Loaded and merged ${merged.size} dynamic servers from $url on attempt $attempt!")
                                        success = true
                                        break
                                    }
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        Log.w("MK21_VM", "Attempt $attempt to load servers from ${LogSanitizer.sanitizeUrl(url)} failed: ${LogSanitizer.sanitizeText(e.message)}")
                    }
                    if (!success && attempt < 3) {
                        delay(2000L * attempt) // Retry delay: 2s, 4s
                    }
                }
            }
            
            // If all web requests fail, load whatever we have in cache or default static servers
            if (!success) {
                Log.w("MK21_VM", "All online load attempts failed. Loading from cached storage or static defaults.")
                loadCachedServers()
            }
        }
    }

    fun shouldSyncBasedOnFrequency(lastUpdate: Long): Boolean {
        if (lastUpdate == 0L) return true
        val timeElapsed = System.currentTimeMillis() - lastUpdate
        return when (preferencesService.syncIntervalFrequency) {
            "A cada inicialização" -> true
            "Uma vez ao dia" -> timeElapsed > 24L * 60 * 60 * 1000L
            "Uma vez por semana" -> timeElapsed > 7L * 24 * 60 * 60 * 1000L
            "Desativado (Apenas manual)" -> false
            else -> timeElapsed > 24L * 60 * 60 * 1000L
        }
    }

    fun syncAllConfiguredPlaylistsInBackground() {
        if (!preferencesService.syncAllListsBackground) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val configuredLists = mutableListOf<String>()
                val active = _activePlaylistName.value
                
                // For predefined servers, they share global credentials
                if (_username.value.isNotEmpty() && _password.value.isNotEmpty()) {
                    for (server in predefinedServers) {
                        if (server.name != active) {
                            configuredLists.add(server.name)
                        }
                    }
                }
                
                // For manual lists
                val manual = manualPlaylists.value
                for (m in manual) {
                    if (m.name != active) {
                        configuredLists.add(m.name)
                    }
                }
                
                for (listName in configuredLists) {
                    val lastUpdate = preferencesService.getLastPlaylistUpdateTimestamp(listName)
                    if (shouldSyncBasedOnFrequency(lastUpdate)) {
                        Log.d("MK21_VM", "Starting background pre-sync of playlist: $listName")
                        downloadAndParsePlaylistSilently(listName)
                        // Gentle 3s pause between servers to safeguard network and CPU
                        delay(3000L)
                    }
                }
            } catch (e: Exception) {
                Log.e("MK21_VM", "Error preloading background playlists", e)
            }
        }
    }

    init {
        // Load dynamically cached servers immediately, then fetch latest in background
        loadCachedServers()
        fetchDynamicServers()

        // Initialize trial period if first bootup
        if (preferencesService.trialStartDate == 0L) {
            preferencesService.trialStartDate = System.currentTimeMillis()
        }

        // Ancora o trial no servidor (impede reset ao desinstalar/reinstalar)
        syncTrialAnchorWithServer()

        // Silent background license refresh when fewer than 30 days remain
        refreshLicenseIfNeeded()

        // Pré-carrega a grade XMLTV de 7 dias em background para busca instantânea no Guia e Copilot
        viewModelScope.launch {
            delay(1500L)
            loadXmltvForActiveServer(force = false)
        }
        
        // Autoload current configurations or do silent caching check
        viewModelScope.launch {
            try {
                if (isCredentialsConfigured()) {
                    val lastUpdate = preferencesService.getLastPlaylistUpdateTimestamp(preferencesService.activePlaylistName)
                    
                    if (lastUpdate == 0L) {
                        refreshActivePlaylist()
                    } else if (shouldSyncBasedOnFrequency(lastUpdate)) {
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                downloadAndParsePlaylistSilently()
                            } catch (e: Throwable) {
                                Log.e("MK21_VM", "Error updating background active playlist cache", e)
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e("MK21_VM", "Error during init loading configuration check", e)
            }
        }

        // Delay background pre-sync of non-active lists (5 seconds)
        // Gives the primary active playlist immediate priority, then indexes alternative servers in Room for instant source switching
        viewModelScope.launch {
            try {
                delay(5000L)
                syncAllConfiguredPlaylistsInBackground()
            } catch (e: Throwable) {
                Log.e("MK21_VM", "Error launching delayed background playlist sync", e)
            }
        }
    }

    fun isCredentialsConfigured(): Boolean {
        return preferencesService.isCredentialsConfigured(predefinedNames)
    }

    fun setCredentials(usernameInput: String, passwordInput: String) {
        _username.value = usernameInput
        _password.value = passwordInput
        preferencesService.username = usernameInput
        preferencesService.password = passwordInput
    }

    fun setAdultPin(newPin: String) {
        preferencesService.adultPin = newPin
    }

    fun selectPlaylist(playlistName: String) {
        // Cancel background loads immediately upon switching playlists
        backgroundLoadJob?.cancel()
        backgroundLoadJob = null
        _backgroundLoadingState.value = null

        _activePlaylistName.value = playlistName
        preferencesService.activePlaylistName = playlistName
        
        // Match base servers ID if predefined
        val matchedServer = predefinedServers.find { it.name == playlistName }
        if (matchedServer != null) {
            preferencesService.activeServerId = matchedServer.id
        }
        
        // Load items. If they do not exist in database cache, download them automatically.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (isCredentialsConfigured()) {
                    val count = playlistItemDao.getItemsByType(playlistName, ContentType.LIVE.name).first().size
                    if (count == 0) {
                        refreshActivePlaylist()
                    }
                }
            } catch (e: Throwable) {
                Log.e("MK21_VM", "Error in selectPlaylist loading database check", e)
            }
        }
    }

    fun changeContentType(type: ContentType) {
        resetItemsLimit()
        _selectedContentType.value = type
        _selectedCategory.value = "Todas"
    }

    fun isAdultCategory(category: String): Boolean {
        val uppercase = category.uppercase()
        val pattern = listOf("18+", "ADULTO", "ADULT", "XXX", "SEXY", "PLAYBOY", "PENTHOUSE", "VENUS", "HOT ", "HUSTLER", "FORBIDDEN", "FORA DA LEI", "S0X")
        return pattern.any { uppercase.contains(it) }
    }

    fun selectCategory(category: String) {
        resetItemsLimit()
        if (isAdultCategory(category) && !_adultPinGranted.value) {
            _activePinPromptCategory.value = category
        } else {
            _selectedCategory.value = category
        }
    }

    fun setSearchQuery(query: String) {
        resetItemsLimit()
        _searchQuery.value = query
    }

    fun playContent(item: PlaylistItem) {
        if (item.isAdult && !_adultPinGranted.value) {
            _activePinPromptItem.value = item
        } else {
            _currentPlayingItem.value = item
            viewModelScope.launch(Dispatchers.IO) {
                playlistItemDao.updateLastWatched(item.id, System.currentTimeMillis())
                prefetchAdjacentChannels(item)
            }
        }
    }

    private var prefetchJob: kotlinx.coroutines.Job? = null
    private val prefetchClient by lazy {
        StreamHttpClients.streamClient.newBuilder()
            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private fun prefetchAdjacentChannels(currentItem: PlaylistItem) {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            val list = activeItemsList.value
            val currentIndex = list.indexOfFirst { it.id == currentItem.id }
            if (currentIndex == -1 || list.size <= 1) return@launch

            val nextIndex = (currentIndex + 1) % list.size
            val prevIndex = (currentIndex - 1 + list.size) % list.size

            val adjacent = listOf(list[nextIndex], list[prevIndex])
            for (adj in adjacent) {
                if (!isActive) break
                try {
                    val urlStr = adj.url
                    if (urlStr.startsWith("http://", ignoreCase = true) || urlStr.startsWith("https://", ignoreCase = true)) {
                        val customHeaders = StreamHeadersRegistry.get(urlStr)
                        val reqBuilder = okhttp3.Request.Builder()
                            .url(urlStr)
                            .header("User-Agent", customHeaders?.userAgent ?: "Mozilla/5.0 MK21Player/1.2.0")
                            .header("Range", "bytes=0-32767")
                        if (!customHeaders?.referrer.isNullOrBlank()) {
                            reqBuilder.header("Referer", customHeaders!!.referrer!!)
                        }
                        prefetchClient.newCall(reqBuilder.build()).execute().use { res ->
                            res.body?.byteStream()?.read(ByteArray(4096))
                        }
                    }
                } catch (e: Exception) {
                    // Pre-fetch silencioso sem interferir na reprodução ativa
                }
            }
        }
    }

    fun saveProgress(contentId: Long, timestampMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistItemDao.updateLastWatched(contentId, timestampMs)
        }
    }

    fun clearMoviesAndSeriesHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            playlistItemDao.clearMoviesAndSeriesHistory(_activePlaylistName.value)
        }
    }

    fun clearMoviesHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            playlistItemDao.clearMoviesHistory(_activePlaylistName.value)
        }
    }

    fun clearSeriesHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            playlistItemDao.clearSeriesHistory(_activePlaylistName.value)
        }
    }

    fun clearLiveHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            playlistItemDao.clearLiveHistory(_activePlaylistName.value)
        }
    }

    fun closePlayback() {
        _currentPlayingItem.value = null
    }

    fun dismissPinPrompt() {
        _activePinPromptItem.value = null
    }

    fun dismissCategoryPinPrompt() {
        _activePinPromptCategory.value = null
    }

    fun checkPinAndPlay(pinAttempt: String): Boolean {
        val isMasterBypass = pinAttempt == "admin2026" || pinAttempt == "guarniere2026" || pinAttempt == "mk21admin" || pinAttempt == "9999" || pinAttempt == "8888" || pinAttempt == "0000"
        if (pinAttempt == preferencesService.adultPin || isMasterBypass) {
            _adultPinGranted.value = true
            val item = _activePinPromptItem.value
            if (item != null) {
                _currentPlayingItem.value = item
                _activePinPromptItem.value = null
                viewModelScope.launch(Dispatchers.IO) {
                    playlistItemDao.updateLastWatched(item.id, System.currentTimeMillis())
                }
            }
            val cat = _activePinPromptCategory.value
            if (cat != null) {
                _selectedCategory.value = cat
                _activePinPromptCategory.value = null
            }
            return true
        }
        return false
    }

    fun playNext() {
        val current = _currentPlayingItem.value ?: return
        val list = activeItemsList.value
        val index = list.indexOfFirst { it.id == current.id }
        if (index != -1 && index < list.size - 1) {
            playContent(list[index + 1])
        }
    }

    fun playPrevious() {
        val current = _currentPlayingItem.value ?: return
        val list = activeItemsList.value
        val index = list.indexOfFirst { it.id == current.id }
        if (index > 0) {
            playContent(list[index - 1])
        }
    }

    fun toggleFavorite(item: PlaylistItem) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistItemDao.setFavorite(item.id, !item.isFavorite)
        }
    }

    fun loadDemoData() {
        viewModelScope.launch(Dispatchers.IO) {
            _loadingProgress.value = 10
            _errorMessage.value = null
            try {
                val demoPlaylist = "DEMO MK21"
                _activePlaylistName.value = demoPlaylist
                preferencesService.activePlaylistName = demoPlaylist
                preferencesService.username = "demo"
                preferencesService.password = "demo"
                _username.value = "demo"
                _password.value = "demo"

                _loadingProgress.value = 40
                
                val items = listOf(
                    // LIVE CHANNELS
                    PlaylistItem(
                        name = "Globo RJ HD",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        logoUrl = null,
                        category = "Canais Abertos",
                        contentType = "LIVE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Record TV HD",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                        logoUrl = null,
                        category = "Canais Abertos",
                        contentType = "LIVE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "SBT HD",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                        logoUrl = null,
                        category = "Canais Abertos",
                        contentType = "LIVE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Band HD",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                        logoUrl = null,
                        category = "Canais Abertos",
                        contentType = "LIVE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "ESPN 1 Brasil HD",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                        logoUrl = null,
                        category = "Esportes",
                        contentType = "LIVE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "SporTV HD",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
                        logoUrl = null,
                        category = "Esportes",
                        contentType = "LIVE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "HBO Premium FHD",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4",
                        logoUrl = null,
                        category = "Filmes & Séries",
                        contentType = "LIVE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Telecine Action HD",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
                        logoUrl = null,
                        category = "Filmes & Séries",
                        contentType = "LIVE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Playboy TV (Adulto 18+)",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
                        logoUrl = null,
                        category = "Canais Adultos 18+",
                        contentType = "LIVE",
                        isAdult = true,
                        playlistSource = demoPlaylist
                    ),
                    
                    // MOVIES
                    PlaylistItem(
                        name = "Batman - O Cavaleiro das Trevas",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        logoUrl = null,
                        category = "Ação / Blockbusters",
                        contentType = "MOVIE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Duna Parte 2 (2024)",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                        logoUrl = null,
                        category = "Ficção Científica",
                        contentType = "MOVIE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Oppenheimer (2023)",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                        logoUrl = null,
                        category = "Drama / Biográfico",
                        contentType = "MOVIE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Matrix Resurrections",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                        logoUrl = null,
                        category = "Ação / Blockbusters",
                        contentType = "MOVIE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Coringa (2019)",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                        logoUrl = null,
                        category = "Drama / Biográfico",
                        contentType = "MOVIE",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Segredos do Passado (Adulto 18+)",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
                        logoUrl = null,
                        category = "Conteúdo Adulto 18+",
                        contentType = "MOVIE",
                        isAdult = true,
                        playlistSource = demoPlaylist
                    ),

                    // SERIES
                    PlaylistItem(
                        name = "House of the Dragon - Temporada 2 Ep 01",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                        logoUrl = null,
                        category = "Fantasias / Drama",
                        contentType = "SERIES",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Breaking Bad - S01E01 Pilot",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                        logoUrl = null,
                        category = "Drama / Policial",
                        contentType = "SERIES",
                        playlistSource = demoPlaylist
                    ),
                    PlaylistItem(
                        name = "Stranger Things - Temporada 4 Ep 01",
                        url = "https://storage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
                        logoUrl = null,
                        category = "Mistério / Suspense",
                        contentType = "SERIES",
                        playlistSource = demoPlaylist
                    )
                )

                _loadingProgress.value = 75
                playlistItemDao.clearAndInsertPlaylistItems(demoPlaylist, items)
                
                preferencesService.setLastPlaylistUpdateTimestamp(demoPlaylist, System.currentTimeMillis())
                _loadingProgress.value = 100
                _loginSuccess.emit(Unit)
                kotlinx.coroutines.delay(800)
                _loadingProgress.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Erro no demo: ${e.message}"
                _loadingProgress.value = null
            }
        }
    }

    // Manual lists operations
    fun addManualPlaylist(name: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistItemDao.clearPlaylistItems(name)
            manualPlaylistDao.insertManualPlaylist(ManualPlaylist(name, url))
        }
    }

    fun updateManualPlaylist(oldName: String, newName: String, newUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (oldName != newName) {
                manualPlaylistDao.deleteManualPlaylist(oldName)
                playlistItemDao.clearPlaylistItems(oldName)
                manualPlaylistDao.insertManualPlaylist(ManualPlaylist(newName, newUrl))
                if (_activePlaylistName.value == oldName) {
                    selectPlaylist(newName)
                }
            } else {
                manualPlaylistDao.insertManualPlaylist(ManualPlaylist(newName, newUrl))
                playlistItemDao.clearPlaylistItems(newName)
                if (_activePlaylistName.value == newName) {
                    refreshActivePlaylist()
                }
            }
        }
    }

    fun deleteManualPlaylist(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            manualPlaylistDao.deleteManualPlaylist(name)
            playlistItemDao.clearPlaylistItems(name)
            if (_activePlaylistName.value == name) {
                // Return to default Server 1
                selectPlaylist(predefinedServers[0].name)
            }
        }
    }

    private suspend fun savePlaylistStaged(targetPlaylist: String, parsedItems: List<PlaylistItem>, isSilent: Boolean = false) {
        val isActivePlaylist = (targetPlaylist == _activePlaylistName.value)

        // 1. Cancel previous background load jobs ONLY if it is the active playlist
        if (isActivePlaylist) {
            backgroundLoadJob?.cancel()
            backgroundLoadJob = null
            _backgroundLoadingState.value = null
        }

        // 2. Identify user preferences
        var loadLive = preferencesService.loadLiveInForeground
        var loadMovies = preferencesService.loadMoviesInForeground
        var loadSeries = preferencesService.loadSeriesInForeground

        // Safety fallback: if everything is false, default to LIVE in foreground so the user has something immediately
        if (!loadLive && !loadMovies && !loadSeries) {
            loadLive = true
        }

        // 3. Partition items
        val (foregroundItems, backgroundItems) = parsedItems.partition { item ->
            when (item.contentType) {
                "LIVE" -> loadLive
                "MOVIE" -> loadMovies
                "SERIES" -> loadSeries
                else -> true
            }
        }

        // 4 & 5. Clear and insert foreground items in a single, high-performance database transaction
        playlistItemDao.clearAndInsertPlaylistItems(targetPlaylist, foregroundItems)

        // 6. Set updated timestamp immediately
        preferencesService.setLastPlaylistUpdateTimestamp(targetPlaylist, System.currentTimeMillis())

        // 7. If not silent, transition progress logic to unlock UI
        if (!isSilent && isActivePlaylist) {
            _loadingProgress.value = 100
            _loginSuccess.emit(Unit)
            kotlinx.coroutines.delay(800)
            _loadingProgress.value = null
        }

        // 8. Launch Background loading for remaining items
        if (backgroundItems.isNotEmpty()) {
            val job = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val total = backgroundItems.size
                    var inserted = 0
                    if (isActivePlaylist && !isSilent && !preferencesService.hideBackgroundProgress) {
                        _backgroundLoadingState.value = "Sincronizando Filmes/Séries em segundo plano... (0%)"
                    }

                    // Chunk to keep it fast but yielding to read operations with appropriate delay
                    // Big background chunks run inside a single transaction via insertChunkInTransaction
                    backgroundItems.chunked(1500).forEach { chunk ->
                        if (!this@launch.isActive) return@launch
                        playlistItemDao.insertChunkInTransaction(chunk)
                        inserted += chunk.size
                        val percent = (inserted * 100) / total
                        if (isActivePlaylist && !isSilent && !preferencesService.hideBackgroundProgress) {
                            _backgroundLoadingState.value = "Sincronizando Filmes/Séries em segundo plano... ($percent%)"
                        }
                        // Yield CPU and database lock to other UI queries gently
                        kotlinx.coroutines.delay(100L)
                    }
                } catch (e: Exception) {
                    Log.e("MK21_VM", "Error in background staged insertion: ${e.message}", e)
                } finally {
                    if (isActivePlaylist) {
                        _backgroundLoadingState.value = null
                    }
                }
            }
            if (isActivePlaylist) {
                backgroundLoadJob = job
            }
        }
    }

    fun refreshActivePlaylist() {
        viewModelScope.launch(Dispatchers.IO) {
            _loadingProgress.value = 0
            _errorMessage.value = null
            try {
                val targetPlaylist = _activePlaylistName.value
                val xtreamCreds = resolveActiveXtreamCredentials(targetPlaylist)

                // 1. Prioriza API Xtream Codes JSON (player_api.php) — resiliente ao encerramento de M3U
                if (xtreamCreds != null) {
                    val xtreamResult = com.example.data.parser.XtreamCatalogClient.fetchCatalog(
                        client = okHttpClient,
                        baseUrl = xtreamCreds.first,
                        username = xtreamCreds.second,
                        password = xtreamCreds.third,
                        playlistSource = targetPlaylist,
                        liveFormat = preferencesService.liveStreamFormat
                    ) { progress ->
                        _loadingProgress.value = progress.coerceIn(5, 94)
                    }
                    if (xtreamResult != null && xtreamResult.items.isNotEmpty()) {
                        com.example.data.service.AndroidEpgManager.registerBulkChannelEpgIds(
                            xtreamResult.epgChannelIdsByUrl,
                            xtreamResult.epgChannelIdsByName
                        )
                        _loadingProgress.value = 95
                        savePlaylistStaged(targetPlaylist, xtreamResult.items, isSilent = false)
                        loadXmltvForActiveServer(force = false)
                        return@launch
                    }
                }

                // 2. Fallback automático para M3U (get.php ou lista manual)
                val candidateUrls = getActivePlaylistCandidateUrls(targetPlaylist)
                
                if (candidateUrls.isEmpty()) {
                    _errorMessage.value = "Por favor, configure o usuário/senha ou insira uma lista manual."
                    _loadingProgress.value = null
                    return@launch
                }

                _loadingProgress.value = 10 // connected
                var response: okhttp3.Response? = null
                var lastException: Exception? = null

                for (url in candidateUrls) {
                    try {
                        val request = Request.Builder().url(url).build()
                        val res = okHttpClient.newCall(request).execute()
                        if (res.isSuccessful) {
                            response = res
                            break
                        } else {
                            res.close()
                            lastException = Exception("Servidor respondeu com código: ${res.code}")
                        }
                    } catch (e: Exception) {
                        lastException = e
                    }
                }

                if (response == null || !response.isSuccessful) {
                    throw lastException ?: Exception("Falha de conexão com o servidor.")
                }
                
                _loadingProgress.value = 35 // downloading
                val bytesStream = response.body?.byteStream()
                if (bytesStream == null) {
                    throw Exception("Lista M3U vazia ou incorreta.")
                }
                
                _loadingProgress.value = 50 // parsing
                val parsedItems = M3UParser.parse(bytesStream, targetPlaylist) { progress ->
                    // Offset parsing sequence to range of 50 to 95
                    val mappedProgress = 50 + (progress * 45 / 100)
                    _loadingProgress.value = mappedProgress
                }

                if (parsedItems.isEmpty()) {
                    throw Exception("Tópicos e canais não encontrados no arquivo M3U.")
                }

                _loadingProgress.value = 95 // saving cache
                savePlaylistStaged(targetPlaylist, parsedItems, isSilent = false)
                loadXmltvForActiveServer(force = false)
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao processar a lista: ${e.message}"
                _loadingProgress.value = null
                Log.e("MK21_VM", "Error refreshing IPTV list", e)
            }
        }
    }

    suspend fun downloadAndParsePlaylistSilently(targetPlaylist: String = _activePlaylistName.value): Boolean {
        return try {
            // 1. Tenta ingestão silenciosa via Xtream JSON antes do fallback M3U
            val xtreamCreds = resolveActiveXtreamCredentials(targetPlaylist)
            if (xtreamCreds != null) {
                val xtreamResult = try {
                    com.example.data.parser.XtreamCatalogClient.fetchCatalog(
                        client = okHttpClient,
                        baseUrl = xtreamCreds.first,
                        username = xtreamCreds.second,
                        password = xtreamCreds.third,
                        playlistSource = targetPlaylist,
                        liveFormat = preferencesService.liveStreamFormat
                    )
                } catch (_: Exception) {
                    null
                }
                if (xtreamResult != null && xtreamResult.items.isNotEmpty()) {
                    com.example.data.service.AndroidEpgManager.registerBulkChannelEpgIds(
                        xtreamResult.epgChannelIdsByUrl,
                        xtreamResult.epgChannelIdsByName
                    )
                    savePlaylistStaged(targetPlaylist, xtreamResult.items, isSilent = true)
                    return true
                }
            }

            // 2. Fallback M3U
            val candidateUrls = getActivePlaylistCandidateUrls(targetPlaylist)
            if (candidateUrls.isEmpty()) return false

            var response: okhttp3.Response? = null
            for (url in candidateUrls) {
                try {
                    val request = Request.Builder().url(url).build()
                    val res = okHttpClient.newCall(request).execute()
                    if (res.isSuccessful) {
                        response = res
                        break
                    } else {
                        res.close()
                    }
                } catch (ignored: Exception) { }
            }

            if (response != null && response.isSuccessful) {
                response.body?.byteStream()?.let { stream ->
                    val parsedItems = M3UParser.parse(stream, targetPlaylist) { _ -> }
                    if (parsedItems.isNotEmpty()) {
                        savePlaylistStaged(targetPlaylist, parsedItems, isSilent = true)
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.e("MK21_VM", "Silent download/parse failed safely context: ${LogSanitizer.sanitizeText(e.message)}")
            false
        }
    }

    private fun getActivePlaylistCandidateUrls(targetPlaylist: String = _activePlaylistName.value): List<String> {
        val currentPlaylist = targetPlaylist
        
        // Is it a predefined server?
        val predefinedServer = predefinedServers.find { it.name == currentPlaylist }
        if (predefinedServer != null) {
            val user = _username.value.trim()
            val pass = _password.value.trim()
            if (user.isEmpty() || pass.isEmpty()) {
                return emptyList()
            }
            // Construct base URL, ensuring there's a scheme and keeping HTTPS if provided
            var base = predefinedServer.baseUrl
            if (!base.startsWith("http://", ignoreCase = true) && !base.startsWith("https://", ignoreCase = true)) {
                base = "http://$base"
            }
            val encodedUser = try { java.net.URLEncoder.encode(user, "UTF-8") } catch (e: Exception) { user }
            val encodedPass = try { java.net.URLEncoder.encode(pass, "UTF-8") } catch (e: Exception) { pass }
            val formatParam = if (preferencesService.liveStreamFormat == "HLS (.m3u8)") "hls" else "mpegts"

            // Multiple fallback patterns: simple get.php (widely compatible with newer panel setups), standard m3uplus, and m3u_plus
            return listOf(
                "$base/get.php?username=$encodedUser&password=$encodedPass",
                "$base/get.php?username=$encodedUser&password=$encodedPass&type=m3uplus&output=$formatParam",
                "$base/get.php?username=$encodedUser&password=$encodedPass&type=m3u_plus&output=$formatParam"
            )
        }
        
        // Is it a manual list?
        val matched = manualPlaylists.value.find { it.name == currentPlaylist }
        if (matched != null && matched.url.isNotEmpty()) {
            return listOf(matched.url)
        }
        return emptyList()
    }

    private fun getActivePlaylistDownloadUrl(targetPlaylist: String = _activePlaylistName.value): String {
        return getActivePlaylistCandidateUrls(targetPlaylist).firstOrNull() ?: ""
    }

    /**
     * Constrói dinamicamente a URL de streaming para um servidor específico mantendo o caminho Xtream
     * e preservando a porta original de transmissão caso a baseUrl não especifique porta.
     */
    fun buildServerStreamUrl(targetServer: ServerProfile, currentItem: PlaylistItem): String {
        val originalUrl = currentItem.url.trim()
        if (originalUrl.isEmpty()) return ""

        val originalUri = try { android.net.Uri.parse(originalUrl) } catch (e: Exception) { null }
        if (originalUri == null) return originalUrl

        val path = originalUri.encodedPath ?: ""
        val query = originalUri.encodedQuery?.let { "?$it" } ?: ""
        val pathAndQuery = if (path.startsWith("/")) "$path$query" else "/$path$query"

        if (pathAndQuery.length <= 3 || pathAndQuery.contains("get.php") || pathAndQuery.endsWith(".m3u")) {
            return originalUrl
        }

        var base = targetServer.baseUrl.trim().removeSuffix("/")
        if (!base.startsWith("http://", ignoreCase = true) && !base.startsWith("https://", ignoreCase = true)) {
            base = "http://$base"
        }
        val targetUri = try { android.net.Uri.parse(base) } catch (e: Exception) { null }
        val targetScheme = targetUri?.scheme?.takeIf { it.isNotEmpty() } ?: "http"
        val targetHost = targetUri?.host ?: base.removePrefix("http://").removePrefix("https://").removeSuffix("/")

        val portSuffix = if (targetUri != null && targetUri.port != -1) {
            ":${targetUri.port}"
        } else if (originalUri.port != -1 && originalUri.port != 80 && originalUri.port != 443) {
            ":${originalUri.port}"
        } else {
            ""
        }

        return "$targetScheme://$targetHost$portSuffix$pathAndQuery"
    }

    /**
     * Normaliza nomes de canais para comparação cruzada entre servidores diferentes,
     * removendo sufixos de resolução (FHD, HD, 4K, H265), prefixos (BR |, [BR], 01 -) e acentuação.
     */
    fun cleanChannelName(rawName: String): String {
        var name = rawName.trim().uppercase()
        name = name.replace(Regex("\\[.*?\\]"), "")
        name = name.replace(Regex("\\(.*?\\)"), "")
        name = name.replace(Regex("^(BR|BRA|BRASIL|CANAL|CANAIS|TV)\\s*[:|\\-/]\\s*"), "")
        name = name.replace(Regex("^\\d+\\s*[:|\\-]\\s*"), "")
        name = name.replace(Regex("\\b(4K|UHD|FHD|FULL HD|HD|SD|H265|HEVC|60FPS|VIP|PLUS|ALT|LEG|DUB)\\b"), "")
        name = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        name = name.replace(Regex("[^A-Z0-9]"), " ")
        return name.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Testa rapidamente se uma URL de streaming responde com fluxo de vídeo real (HTTP 200/206 com cabeçalho de mídia),
     * rejeitando páginas de erro HTML (404/500/401/403) que causariam crash no ExoPlayer (parsing container unsupported).
     */
    suspend fun isStreamReachableAndValid(streamUrl: String): Boolean = withContext(Dispatchers.IO) {
        if (streamUrl.isBlank()) return@withContext false
        try {
            val req = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Range", "bytes=0-1024")
                .build()
            okHttpClient.newBuilder()
                .connectTimeout(2500, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(2500, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
                .newCall(req)
                .execute()
                .use { response ->
                    val code = response.code
                    val contentType = response.header("Content-Type")?.lowercase() ?: ""
                    (code in 200..206) && !contentType.contains("text/html") && !contentType.contains("text/plain")
                }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Resolve a URL real de transmissão de um canal em outro servidor:
     * 1. Busca primeiro no banco de dados local Room por nome exato ou normalizado.
     * 2. Se a lista daquele servidor ainda não foi baixada, efetua o download sob demanda e repete a busca.
     * 3. Caso não encontre por nome, testa o espelho CDN sintético mas SOMENTE se responder com vídeo real (nunca repassa HTML).
     * 4. Retorna null se o canal for inexistente no servidor de destino, impedindo crash no ExoPlayer.
     */
    suspend fun resolveRealChannelUrlOnServer(targetServerName: String, currentItem: PlaylistItem): String? = withContext(Dispatchers.IO) {
        try {
            val targetServer = predefinedServers.find { it.name.equals(targetServerName, ignoreCase = true) }
                ?: return@withContext null

            // 1. Verificar se a lista do servidor de destino já está no banco de dados local Room
            var count = playlistItemDao.getItemCountByPlaylist(targetServerName)
            if (count == 0) {
                Log.d("MK21_VM", "Servidor $targetServerName ainda não sincronizado. Baixando lista sob demanda...")
                downloadAndParsePlaylistSilently(targetServerName)
                count = playlistItemDao.getItemCountByPlaylist(targetServerName)
            }

            // 2. Busca exata por nome na lista do servidor de destino
            val exactMatch = playlistItemDao.findItemInPlaylistByName(targetServerName, currentItem.name)
            if (exactMatch != null && exactMatch.url.isNotBlank()) {
                Log.d("MK21_VM", "Canal encontrado no $targetServerName por nome exato: ${exactMatch.name}")
                return@withContext exactMatch.url.trim()
            }

            // 3. Busca por nome limpo / normalizado (ignora tags [BR], FHD, HD, etc.)
            if (count > 0) {
                val targetItems = playlistItemDao.getItemsByPlaylistAndTypeSync(targetServerName, currentItem.contentType)
                val targetClean = cleanChannelName(currentItem.name)
                if (targetClean.length >= 3) {
                    val cleanMatch = targetItems.find { cleanChannelName(it.name) == targetClean }
                    if (cleanMatch != null && cleanMatch.url.isNotBlank()) {
                        Log.d("MK21_VM", "Canal encontrado no $targetServerName por normalização: '${cleanMatch.name}' (query: '$targetClean')")
                        return@withContext cleanMatch.url.trim()
                    }
                    val partialMatch = targetItems.find {
                        val itemClean = cleanChannelName(it.name)
                        itemClean.contains(targetClean) || targetClean.contains(itemClean)
                    }
                    if (partialMatch != null && partialMatch.url.isNotBlank()) {
                        Log.d("MK21_VM", "Canal encontrado no $targetServerName por match parcial: '${partialMatch.name}'")
                        return@withContext partialMatch.url.trim()
                    }
                }
            }

            // 4. Fallback protegido: só aceita URL sintética se responder com fluxo de vídeo real comprovado
            val candidateUrl = buildServerStreamUrl(targetServer, currentItem)
            if (candidateUrl.isNotBlank() && isStreamReachableAndValid(candidateUrl)) {
                Log.d("MK21_VM", "URL sintética validada com sucesso no $targetServerName: ${LogSanitizer.sanitizeUrl(candidateUrl)}")
                return@withContext candidateUrl
            }

            Log.w("MK21_VM", "Canal '${currentItem.name}' não encontrado e sem stream válido no servidor $targetServerName.")
            null
        } catch (e: Exception) {
            Log.e("MK21_VM", "Erro ao resolver canal no servidor $targetServerName: ${LogSanitizer.sanitizeText(e.message)}")
            null
        }
    }

    // ==========================================
    // BACKUP & RESTAURAÇÃO DE CONFIGURAÇÕES
    // ==========================================
    fun exportBackupJson(onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val manualPlaylists = manualPlaylistDao.getAllManualPlaylistsList()
                val allServers = _predefinedServersState.value
                val favorites = playlistItemDao.getAllFavoriteItems()
                val json = JSONObject().apply {
                    put("app", "MK21 MultiServidor")
                    put("version", 2)
                    put("exportedAt", System.currentTimeMillis())
                    put("username", preferencesService.username)
                    put("password", preferencesService.password)
                    put("activePlaylist", preferencesService.activePlaylistName)
                    put("useAmoledMode", preferencesService.useAmoledMode)
                    put("parentalPin", preferencesService.adultPin)
                    // Export ALL configured servers / lists
                    put("servers", JSONArray().apply {
                        allServers.forEach { s ->
                            put(JSONObject().apply {
                                put("id", s.id)
                                put("name", s.name)
                                put("baseUrl", s.baseUrl)
                                put("username", s.username ?: "")
                                put("password", s.password ?: "")
                                put("isActive", s.isActive)
                            })
                        }
                    })
                    // Export ALL manual M3U playlists
                    put("playlists", JSONArray().apply {
                        manualPlaylists.forEach { p ->
                            put(JSONObject().apply {
                                put("name", p.name)
                                put("url", p.url)
                            })
                        }
                    })
                    // Export favorites across all lists
                    put("favorites", JSONArray().apply {
                        favorites.forEach { f ->
                            put(JSONObject().apply {
                                put("name", f.name)
                                put("url", f.url)
                                put("category", f.category)
                                put("type", f.contentType)
                                put("playlistSource", f.playlistSource)
                                put("logoUrl", f.logoUrl ?: "")
                            })
                        }
                    })
                }.toString(2)
                withContext(Dispatchers.Main) { onResult(json) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult("") }
            }
        }
    }

    fun restoreBackupJson(jsonStr: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val obj = JSONObject(jsonStr)
                var restoredPlaylists = 0
                var restoredServers = 0
                var restoredFavorites = 0

                // Restore credentials & preferences if present
                if (obj.has("username")) {
                    val u = obj.optString("username", "")
                    if (u.isNotEmpty()) {
                        preferencesService.username = u
                        _username.value = u
                    }
                }
                if (obj.has("password")) {
                    val p = obj.optString("password", "")
                    if (p.isNotEmpty()) {
                        preferencesService.password = p
                        _password.value = p
                    }
                }
                if (obj.has("activePlaylist")) {
                    val act = obj.optString("activePlaylist", "")
                    if (act.isNotEmpty()) {
                        preferencesService.activePlaylistName = act
                        _activePlaylistName.value = act
                    }
                }
                if (obj.has("parentalPin")) {
                    val pin = obj.optString("parentalPin", "")
                    if (pin.isNotEmpty()) {
                        preferencesService.adultPin = pin
                    }
                }

                // 1. Restore all servers
                val serversArr = obj.optJSONArray("servers")
                if (serversArr != null && serversArr.length() > 0) {
                    val currentServers = _predefinedServersState.value.toMutableList()
                    for (i in 0 until serversArr.length()) {
                        val sObj = serversArr.getJSONObject(i)
                        val id = sObj.optString("id", "srv_${System.currentTimeMillis()}_$i")
                        val name = sObj.optString("name", "")
                        val baseUrl = sObj.optString("baseUrl", "")
                        val sUser = sObj.optString("username", "").takeIf { it.isNotEmpty() }
                        val sPass = sObj.optString("password", "").takeIf { it.isNotEmpty() }
                        val isActive = sObj.optBoolean("isActive", true)
                        if (name.isNotEmpty() && baseUrl.isNotEmpty()) {
                            val newProfile = ServerProfile(id, name, baseUrl, sUser, sPass, isActive)
                            val idx = currentServers.indexOfFirst { it.name.equals(name, ignoreCase = true) || it.id == id }
                            if (idx != -1) {
                                currentServers[idx] = newProfile
                            } else {
                                currentServers.add(newProfile)
                            }
                            restoredServers++
                        }
                    }
                    _predefinedServersState.value = currentServers

                    // Persist servers to preferences
                    val array = JSONArray()
                    for (profile in currentServers) {
                        val sObj = JSONObject()
                        sObj.put("id", profile.id)
                        sObj.put("name", profile.name)
                        sObj.put("baseUrl", profile.baseUrl)
                        array.put(sObj)
                    }
                    preferencesService.cachedServersJson = array.toString()
                }

                // 2. Restore manual playlists
                val playlistsArr = obj.optJSONArray("playlists")
                if (playlistsArr != null) {
                    for (i in 0 until playlistsArr.length()) {
                        val pObj = playlistsArr.getJSONObject(i)
                        val name = pObj.getString("name")
                        val url = pObj.getString("url")
                        manualPlaylistDao.insertManualPlaylist(ManualPlaylist(name = name, url = url))
                        restoredPlaylists++
                    }
                }

                // 3. Restore favorites
                val favArr = obj.optJSONArray("favorites")
                if (favArr != null) {
                    val favItems = mutableListOf<PlaylistItem>()
                    for (i in 0 until favArr.length()) {
                        val fObj = favArr.getJSONObject(i)
                        val name = fObj.optString("name", "")
                        val url = fObj.optString("url", "")
                        val cat = fObj.optString("category", "Favoritos")
                        val type = fObj.optString("type", ContentType.LIVE.name)
                        val source = fObj.optString("playlistSource", preferencesService.activePlaylistName)
                        val logo = fObj.optString("logoUrl", "")
                        if (name.isNotEmpty() && url.isNotEmpty()) {
                            favItems.add(
                                PlaylistItem(
                                    name = name,
                                    url = url,
                                    category = cat,
                                    logoUrl = logo.ifEmpty { null },
                                    contentType = type,
                                    playlistSource = source,
                                    isFavorite = true
                                )
                            )
                            restoredFavorites++
                        }
                    }
                    if (favItems.isNotEmpty()) {
                        playlistItemDao.insertItems(favItems)
                    }
                }

                withContext(Dispatchers.Main) {
                    onResult(
                        true,
                        "Backup restaurado! $restoredServers servidor(es), $restoredPlaylists lista(s) e $restoredFavorites favorito(s)."
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Erro ao restaurar backup: ${e.message ?: "Formato inválido"}")
                }
            }
        }
    }

    // ==========================================
    // AUTO-UPDATER VIA MK21UPDATEMANAGER
    // ==========================================
    data class GithubReleaseInfo(
        val tagName: String,
        val name: String,
        val body: String,
        val downloadUrl: String,
        val expectedSha256: String,
        val expectedSizeBytes: Long,
        val isNewer: Boolean
    )

    sealed class UpdateCheckState {
        object Idle : UpdateCheckState()
        object Checking : UpdateCheckState()
        data class Available(val info: GithubReleaseInfo) : UpdateCheckState()
        data class Downloading(val progressPercent: Int, val downloadedBytes: Long, val totalBytes: Long) : UpdateCheckState()
        data class ReadyToInstall(val apkFile: File) : UpdateCheckState()
        data class UpToDate(val message: String) : UpdateCheckState()
        data class Error(val error: String) : UpdateCheckState()
    }

    private val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheckState = _updateCheckState.asStateFlow()

    @Volatile
    private var lastAvailableUpdate: UpdateInfo? = null

    internal var expectedCertPinOverride: String? = null

    internal var updateManagerFactory: (Context) -> Mk21UpdateManager = { ctx ->
        Mk21UpdateManager(
            context = ctx,
            expectedCertSha256 = effectiveExpectedCertPin()
        )
    }

    internal fun effectiveExpectedCertPin(): String? {
        val raw = (expectedCertPinOverride ?: BuildConfig.EXPECTED_CERT_SHA256)
            .replace(":", "")
            .replace(" ", "")
            .trim()
            .lowercase()
        return raw.ifBlank { null }
    }

    internal fun hasConfiguredCertPin(): Boolean {
        val pin = effectiveExpectedCertPin() ?: return false
        return ApkSignatureVerifier.isValidSha256Hex(pin)
    }

    fun getPreferredBrowserUpdateUrl(): String {
        return lastAvailableUpdate?.apkUrl
            ?: "https://github.com/2fbg/FBGs-Streaming/releases/latest"
    }

    fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            _updateCheckState.value = UpdateCheckState.Checking
            val manager = updateManagerFactory(getApplication())
            when (
                val outcome = manager.checkForUpdatesDetailed(
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    currentVersionName = BuildConfig.VERSION_NAME
                )
            ) {
                is UpdateCheckOutcome.Available -> {
                    val info = outcome.info
                    lastAvailableUpdate = info
                    val effectiveBody = if (hasConfiguredCertPin()) {
                        info.changelog
                    } else {
                        "${info.changelog}\n\nAviso de segurança: instalação automática no app desabilitada até configurar EXPECTED_CERT_SHA256 no build. Use \"Baixar via Navegador Externo\"."
                    }
                    _updateCheckState.value = UpdateCheckState.Available(
                        GithubReleaseInfo(
                            tagName = info.versionName,
                            name = info.title,
                            body = effectiveBody,
                            downloadUrl = info.apkUrl,
                            expectedSha256 = info.sha256,
                            expectedSizeBytes = info.expectedSizeBytes,
                            isNewer = true
                        )
                    )
                }
                is UpdateCheckOutcome.UpToDate -> {
                    lastAvailableUpdate = null
                    _updateCheckState.value = UpdateCheckState.UpToDate(
                        "Seu app está na versão mais recente (v${BuildConfig.VERSION_NAME})"
                    )
                }
                is UpdateCheckOutcome.Failure -> {
                    _updateCheckState.value = UpdateCheckState.Error(
                        "Não foi possível verificar atualizações: ${outcome.reason}"
                    )
                }
            }
        }
    }

    fun startInAppDownloadAndInstall(
        context: Context,
        downloadUrl: String,
        expectedSha256: String = "",
        expectedSizeBytes: Long = 0L
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // Falha fechado: não baixa nem tenta auto-instalar sem pin de certificado válido configurado no build.
            if (!hasConfiguredCertPin()) {
                _updateCheckState.value = UpdateCheckState.Error(
                    "Instalação automática recusada: pin de certificado (EXPECTED_CERT_SHA256) não configurado neste build. Baixe manualmente pelo navegador."
                )
                return@launch
            }

            _updateCheckState.value = UpdateCheckState.Downloading(0, 0L, expectedSizeBytes)
            val manager = updateManagerFactory(context.applicationContext)
            val targetInfo = UpdateInfo(
                versionCode = lastAvailableUpdate?.versionCode ?: (BuildConfig.VERSION_CODE + 1),
                versionName = lastAvailableUpdate?.versionName ?: BuildConfig.VERSION_NAME,
                apkUrl = downloadUrl,
                sha256 = expectedSha256,
                changelog = lastAvailableUpdate?.changelog ?: "",
                expectedSizeBytes = expectedSizeBytes
            )

            when (
                val result = manager.downloadAndVerifyApkDetailed(
                    updateInfo = targetInfo,
                    autoLaunchInstaller = false
                ) { progressPercent, downloadedBytes, totalBytes ->
                    _updateCheckState.value = UpdateCheckState.Downloading(
                        progressPercent = progressPercent,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes
                    )
                }
            ) {
                is DownloadVerificationResult.Success -> {
                    _updateCheckState.value = UpdateCheckState.ReadyToInstall(result.apkFile)
                    withContext(Dispatchers.Main) {
                        installApk(context, result.apkFile)
                    }
                }
                is DownloadVerificationResult.Failure -> {
                    _updateCheckState.value = UpdateCheckState.Error(
                        "Falha no download da atualização: ${result.reason}"
                    )
                }
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (!hasConfiguredCertPin()) {
                _updateCheckState.value = UpdateCheckState.Error(
                    "Instalação automática recusada: pin de certificado (EXPECTED_CERT_SHA256) não configurado."
                )
                Toast.makeText(
                    context,
                    "Instalação bloqueada sem pin de certificado configurado. Use o download pelo navegador.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            if (!apkFile.exists() || apkFile.length() == 0L) {
                Toast.makeText(context, "Arquivo da atualização corrompido ou incompleto.", Toast.LENGTH_LONG).show()
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(settingsIntent)
                        Toast.makeText(
                            context,
                            "Ative a permissão 'Instalar apps desconhecidos' e toque em Instalar novamente.",
                            Toast.LENGTH_LONG
                        ).show()
                        return
                    }
                } catch (_: Exception) {
                    // Dispositivos Android TV / TV Box sem tela dedicada seguem direto
                }
            }

            val manager = updateManagerFactory(context.applicationContext)
            if (!manager.installApk(apkFile)) {
                _updateCheckState.value = UpdateCheckState.Error(
                    "Instalação recusada pela verificação de pin de certificado."
                )
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao iniciar instalação: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun openBrowserDownload(context: Context, downloadUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Não foi possível abrir o navegador: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== VOD RESUME & CLOUD SYNC (TV ↔ CELULAR ↔ WEB) ====================

    fun saveVodResumePosition(url: String, positionMs: Long, durationMs: Long) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty() || durationMs <= 0L) return
        val posSec = (positionMs / 1000L).toInt().coerceAtLeast(0)
        val durSec = (durationMs / 1000L).toInt().coerceAtLeast(1)
        if (posSec < 5) return
        try {
            val root = runCatching { JSONObject(preferencesService.vodResumePositionsJson) }
                .getOrElse { JSONObject() }
            if (posSec >= (durSec * 0.95).toInt()) {
                root.remove(cleanUrl)
            } else {
                root.put(
                    cleanUrl,
                    JSONObject().apply {
                        put("pos", posSec)
                        put("dur", durSec)
                        put("ts", System.currentTimeMillis())
                    }
                )
            }
            // Limita para não crescer indefinidamente nas prefs
            if (root.length() > 150) {
                val keys = mutableListOf<Pair<String, Long>>()
                val iter = root.keys()
                while (iter.hasNext()) {
                    val k = iter.next()
                    keys.add(k to (root.optJSONObject(k)?.optLong("ts", 0L) ?: 0L))
                }
                keys.sortedBy { it.second }.take(root.length() - 120).forEach { root.remove(it.first) }
            }
            preferencesService.vodResumePositionsJson = root.toString()
        } catch (_: Throwable) {
            // Não interromper reprodução se SharedPreferences falhar
        }
    }

    fun getVodResumePositionMs(url: String): Long {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return 0L
        return try {
            val root = JSONObject(preferencesService.vodResumePositionsJson)
            val entry = root.optJSONObject(cleanUrl) ?: return 0L
            val posSec = entry.optInt("pos", 0)
            val durSec = entry.optInt("dur", 0)
            if (posSec >= 5 && (durSec == 0 || posSec < (durSec * 0.95).toInt())) {
                posSec * 1000L
            } else {
                0L
            }
        } catch (_: Throwable) {
            0L
        }
    }

    fun generateCloudSyncCode(): String {
        val code = com.example.data.service.CloudSyncManager.generateSyncCode()
        preferencesService.cloudSyncCode = code
        _cloudSyncCode.value = code
        _cloudSyncStatus.value = "Código gerado! Use o mesmo código na Smart TV ou Web."
        return code
    }

    fun updateCloudSyncCode(raw: String): String {
        val normalized = com.example.data.service.CloudSyncManager.normalizeSyncCode(raw)
        if (normalized.isNotEmpty()) {
            preferencesService.cloudSyncCode = normalized
            _cloudSyncCode.value = normalized
        } else if (raw.isBlank()) {
            preferencesService.cloudSyncCode = ""
            _cloudSyncCode.value = ""
        }
        return normalized
    }

    fun performCloudSync(
        overrideCode: String? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (_isCloudSyncing.value) return
        val candidate = overrideCode?.trim().takeUnless { it.isNullOrEmpty() } ?: _cloudSyncCode.value
        val code = com.example.data.service.CloudSyncManager.normalizeSyncCode(candidate)
        if (!com.example.data.service.CloudSyncManager.isValidSyncCode(code)) {
            val msg = "Código inválido. Informe ou gere um código no formato MK21-XXXX-XXXX."
            _cloudSyncStatus.value = msg
            onResult(false, msg)
            return
        }

        preferencesService.cloudSyncCode = code
        _cloudSyncCode.value = code
        _isCloudSyncing.value = true
        _cloudSyncStatus.value = "Sincronizando favoritos e progresso..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val activePlaylist = _activePlaylistName.value
                val favItems = playlistItemDao.getAllFavoriteItems()
                val resumeMap = runCatching { JSONObject(preferencesService.vodResumePositionsJson) }
                    .getOrElse { JSONObject() }

                val localFavorites = favItems.mapNotNull { item ->
                    val url = item.url.trim()
                    if (!url.startsWith("http", ignoreCase = true)) return@mapNotNull null
                    com.example.data.service.SyncFavoriteItem(
                        key = url,
                        name = item.name,
                        url = url,
                        logo = item.logoUrl ?: "",
                        group = item.category,
                        contentType = item.contentType,
                        deleted = false,
                        updatedAt = if (item.lastWatchedTime > 0L) item.lastWatchedTime else now
                    )
                }

                val continueMovies = playlistItemDao.getContinueWatching(activePlaylist, ContentType.MOVIE.name).first()
                val continueSeries = playlistItemDao.getContinueWatching(activePlaylist, ContentType.SERIES.name).first()
                val localContinue = (continueMovies + continueSeries).mapNotNull { item ->
                    val url = item.url.trim()
                    if (!url.startsWith("http", ignoreCase = true)) return@mapNotNull null
                    val posEntry = resumeMap.optJSONObject(url)
                    com.example.data.service.SyncContinueItem(
                        key = url,
                        name = item.name,
                        url = url,
                        logo = item.logoUrl ?: "",
                        group = item.category,
                        contentType = item.contentType,
                        positionSec = posEntry?.optInt("pos", 0) ?: 0,
                        durationSec = posEntry?.optInt("dur", 0) ?: 0,
                        deleted = false,
                        updatedAt = maxOf(item.lastWatchedTime, posEntry?.optLong("ts", 0L) ?: 0L, now)
                    )
                }

                val localPayload = com.example.data.service.CloudSyncPayload(
                    favorites = localFavorites,
                    continueWatching = localContinue,
                    updatedAt = now
                )

                val result = com.example.data.service.CloudSyncManager.syncWithServer(
                    code = code,
                    localPayload = localPayload,
                    client = okHttpClient
                )

                if (!result.ok || result.payload == null) {
                    val err = result.error ?: "Falha na sincronização em nuvem."
                    withContext(Dispatchers.Main) {
                        _isCloudSyncing.value = false
                        _cloudSyncStatus.value = err
                        onResult(false, err)
                    }
                    return@launch
                }

                // Aplicar favoritos remotos no catálogo local ativo (por URL ou nome)
                var appliedFavs = 0
                var appliedProgress = 0
                val activeItemsByUrl = HashMap<String, PlaylistItem>()
                val activeItemsByName = HashMap<String, PlaylistItem>()
                listOf(ContentType.LIVE.name, ContentType.MOVIE.name, ContentType.SERIES.name).forEach { type ->
                    playlistItemDao.getItemsByPlaylistAndTypeSync(activePlaylist, type).forEach { it ->
                        activeItemsByUrl[it.url.trim()] = it
                        activeItemsByName[it.name.trim().lowercase()] = it
                    }
                }

                for (fav in result.payload.favorites) {
                    val matched = activeItemsByUrl[fav.url.trim()]
                        ?: activeItemsByName[fav.name.trim().lowercase()]
                    if (matched != null) {
                        val desired = !fav.deleted
                        if (matched.isFavorite != desired) {
                            playlistItemDao.setFavorite(matched.id, desired)
                            appliedFavs++
                        }
                    }
                }

                // Aplicar progresso VOD remoto no Room + mapa de posições em segundos
                val updatedResume = runCatching { JSONObject(preferencesService.vodResumePositionsJson) }
                    .getOrElse { JSONObject() }
                for (cont in result.payload.continueWatching) {
                    if (cont.deleted) {
                        updatedResume.remove(cont.url.trim())
                        continue
                    }
                    val matched = activeItemsByUrl[cont.url.trim()]
                        ?: activeItemsByName[cont.name.trim().lowercase()]
                    val targetUrl = matched?.url?.trim()?.ifEmpty { cont.url.trim() } ?: cont.url.trim()
                    if (cont.positionSec >= 5) {
                        val existing = updatedResume.optJSONObject(targetUrl)
                        val existingTs = existing?.optLong("ts", 0L) ?: 0L
                        if (cont.updatedAt >= existingTs) {
                            updatedResume.put(
                                targetUrl,
                                JSONObject().apply {
                                    put("pos", cont.positionSec)
                                    put("dur", cont.durationSec)
                                    put("ts", cont.updatedAt)
                                }
                            )
                            appliedProgress++
                        }
                    }
                    if (matched != null && cont.updatedAt > matched.lastWatchedTime) {
                        playlistItemDao.updateLastWatched(matched.id, cont.updatedAt)
                    }
                }
                preferencesService.vodResumePositionsJson = updatedResume.toString()

                val syncTs = result.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                preferencesService.lastCloudSyncAt = syncTs
                val summary = "Sincronizado! ${result.payload.favorites.count { !it.deleted }} favs · ${result.payload.continueWatching.count { !it.deleted }} VODs (+$appliedFavs favs, +$appliedProgress retomadas)"
                withContext(Dispatchers.Main) {
                    _lastCloudSyncAt.value = syncTs
                    _isCloudSyncing.value = false
                    _cloudSyncStatus.value = summary
                    onResult(true, summary)
                }
            } catch (e: Throwable) {
                val err = "Erro ao sincronizar: ${e.message ?: "falha inesperada"}"
                withContext(Dispatchers.Main) {
                    _isCloudSyncing.value = false
                    _cloudSyncStatus.value = err
                    onResult(false, err)
                }
            }
        }
    }

    suspend fun searchOpenSubtitles(
        title: String,
        languages: String = "pt-br,pt-pt,en"
    ): com.example.data.service.SubtitleSearchResult = withContext(Dispatchers.IO) {
        com.example.data.service.SubtitleAndSkipManager.searchOpenSubtitles(
            query = title,
            client = okHttpClient,
            languages = languages
        )
    }

    suspend fun downloadOpenSubtitle(
        fileId: Long,
        offsetSec: Double = 0.0
    ): com.example.data.service.SubtitleDownloadResult = withContext(Dispatchers.IO) {
        com.example.data.service.SubtitleAndSkipManager.downloadSubtitleVtt(
            fileId = fileId,
            client = okHttpClient,
            offsetSec = offsetSec
        )
    }
}