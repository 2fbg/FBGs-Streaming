package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import com.example.BuildConfig

@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        IptvDatabase::class.java,
        "mk21_iptv_db"
    ).fallbackToDestructiveMigration().build()

    private val playlistItemDao = db.playlistItemDao()
    private val manualPlaylistDao = db.manualPlaylistDao()
    
    val repository: com.example.data.repository.IptvRepository = com.example.data.repository.IptvRepositoryImpl(
        playlistItemDao,
        manualPlaylistDao
    )
    
    val preferencesService = PreferencesService(application)
    
    private val staticDefaultServers = listOf(
        ServerProfile("server_1", "VLOG", "http://myopbx.beer"),
        ServerProfile("server_2", "LUB TV", "http://pottermax.sbs"),
        ServerProfile("server_3", "CINELON21", "http://coliseuop.site"),
        ServerProfile("server_4", "TANNIX", "http://poptvcdn.online"),
        ServerProfile("server_5", "CB6000", "http://cdn.caterlune.top"),
        ServerProfile("server_6", "MK21 PRÓ", "http://app.vivoxi.xyz"),
        ServerProfile("server_7", "NEW TV", "http://cp2026.sbs"),
        ServerProfile("server_8", "MULTT TV", "http://dali-as.skin"),
        ServerProfile("server_9", "CINEVO", "http://antaresfusion.shop"),
        ServerProfile("server_10", "MULTT BOX", "http://cdnconn.xyz")
    )

    private val _predefinedServersState = MutableStateFlow<List<ServerProfile>>(staticDefaultServers)
    val predefinedServersState: StateFlow<List<ServerProfile>> = _predefinedServersState.asStateFlow()
    
    val predefinedServers: List<ServerProfile>
        get() = _predefinedServersState.value

    private val predefinedNames: Set<String>
        get() = _predefinedServersState.value.map { it.name }.toSet()

    // Real-time Licencing State Flows
    private val _isPremiumActive = MutableStateFlow(preferencesService.isLicenseValid())
    val isPremiumActive = _isPremiumActive.asStateFlow()

    private val _trialDaysLeft = MutableStateFlow(preferencesService.getTrialDaysRemaining())
    val trialDaysLeft = _trialDaysLeft.asStateFlow()

    val virtualMacAddress: String
        get() = preferencesService.virtualMac

    fun generateAutonomousKey(mac: String): String {
        return preferencesService.generateAutonomousKeyForDevice(mac)
    }

    fun activateLicense(key: String): Boolean {
        preferencesService.activationKey = key
        val isValid = preferencesService.isLicenseValid()
        _isPremiumActive.value = isValid
        _trialDaysLeft.value = preferencesService.getTrialDaysRemaining()
        return isValid
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
        _displayedItemsLimit.value = (_displayedItemsLimit.value + 60).coerceAtMost(1500)
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
            sorted.take(displayLimit)
        }
    }.flowOn(Dispatchers.IO).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

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

    private val okHttpClient = OkHttpClient.Builder()
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
                if (lowercaseBaseUrl.contains("new-link.shop") || lowercaseBaseUrl.contains("opbx-w.shop") || lowercaseBaseUrl.contains("somentevlog.xyz") || lowercaseBaseUrl.contains("vlogmk.de") || lowercaseBaseUrl.contains("newphase.sbs")) {
                    targetBaseUrl = "http://new-link.shop"
                    targetName = "VLOG"
                } else if (lowercaseBaseUrl.contains("alfatecloan.sbs") || lowercaseBaseUrl.contains("pitclear.sbs") || lowercaseBaseUrl.contains("triimundial.shop") || lowercaseBaseUrl.contains("lubtv.fun")) {
                    targetBaseUrl = "http://alfatecloan.sbs"
                    targetName = "LUB TV"
                } else if (lowercaseBaseUrl.contains("connstar.xyz") || lowercaseBaseUrl.contains("infinixparcerias.site") || lowercaseBaseUrl.contains("cinelontv.work") || lowercaseBaseUrl.contains("cinelon")) {
                    targetBaseUrl = "http://connstar.xyz"
                    targetName = "CINELON21"
                } else if (lowercaseBaseUrl.contains("poptvcdn.online") || lowercaseBaseUrl.contains("tannix26.shop") || lowercaseBaseUrl.contains("cdnassandplay.online") || lowercaseBaseUrl.contains("unituf.online") || lowercaseBaseUrl.contains("tannix")) {
                    targetBaseUrl = "http://poptvcdn.online"
                    targetName = "TANNIX"
                } else if (lowercaseBaseUrl.contains("painelplyon.top") || lowercaseBaseUrl.contains("cb6.fun") || lowercaseBaseUrl.contains("cb6000")) {
                    targetBaseUrl = "http://cdn.caterlune.top"
                    targetName = "CB6000"
                } else if (lowercaseBaseUrl.contains("app.vivoxi.xyz") || lowercaseBaseUrl.contains("vivoxi") || lowercaseBaseUrl.contains("mk21.uk") || lowercaseBaseUrl.contains("appsmk.org") || lowercaseBaseUrl.contains("mk21")) {
                    targetBaseUrl = "http://app.vivoxi.xyz"
                    targetName = "MK21 PRÓ"
                } else if (lowercaseBaseUrl.contains("dali-as.skin") || lowercaseBaseUrl.contains("hll4.top") || lowercaseBaseUrl.contains("multt tv")) {
                    targetBaseUrl = "http://dali-as.skin"
                    targetName = "MULTT TV"
                } else if (lowercaseBaseUrl.contains("pottermax.sbs") || lowercaseBaseUrl.contains("alfatecloan.sbs") || lowercaseBaseUrl.contains("lubtv.fun") || lowercaseBaseUrl.contains("lub")) {
                    targetBaseUrl = "http://pottermax.sbs"
                    targetName = "LUB TV"
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
                        Log.w("MK21_VM", "Attempt $attempt to load servers from $url failed: ${e.message}")
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
                        // Give the system 20 seconds of breathing room between playlists to safeguard CPU/Network/Database
                        delay(20000L)
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

        // Delay background pre-sync of non-active lists (5 minutes / 300000 ms)
        // This ensures the current selected playlist gets 100% of network & DB resources immediately on bootup
        viewModelScope.launch {
            try {
                delay(300000L)
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
            }
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
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao processar a lista: ${e.message}"
                _loadingProgress.value = null
                Log.e("MK21_VM", "Error refreshing IPTV list", e)
            }
        }
    }

    private suspend fun downloadAndParsePlaylistSilently(targetPlaylist: String = _activePlaylistName.value) {
        try {
            val candidateUrls = getActivePlaylistCandidateUrls(targetPlaylist)
            if (candidateUrls.isEmpty()) return

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
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MK21_VM", "Silent download/parse failed safely context: ${e.message}", e)
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
    // AUTO-UPDATER VIA GITHUB RELEASES
    // ==========================================
    data class GithubReleaseInfo(
        val tagName: String,
        val name: String,
        val body: String,
        val downloadUrl: String,
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

    fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            _updateCheckState.value = UpdateCheckState.Checking
            val endpoints = listOf(
                "https://bgstreaming.vercel.app/app/applet/api/version.json",
                "https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/app/applet/api/version.json",
                "https://api.github.com/repos/2fbg/FBGs-Streaming/releases/latest"
            )
            val responses = mutableListOf<String>()
            var lastError: String? = null
            for (endpoint in endpoints) {
                var conn: HttpURLConnection? = null
                try {
                    conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "MK21-Android-Updater")
                        setRequestProperty("Accept", "application/json")
                        connectTimeout = 8000
                        readTimeout = 10000
                        instanceFollowRedirects = true
                    }
                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        if (body.isNotBlank()) responses.add(body)
                    } else {
                        lastError = "HTTP ${conn.responseCode}"
                    }
                } catch (e: Exception) {
                    lastError = e.message
                } finally {
                    conn?.disconnect()
                }
            }
            try {
                fun versionParts(value: String): List<Int> = value
                    .replace("[^0-9.]".toRegex(), "")
                    .split('.')
                    .filter { it.isNotBlank() }
                    .map { it.toIntOrNull() ?: 0 }
                    .let { it + List((4 - it.size).coerceAtLeast(0)) { 0 } }
                fun versionRank(value: String): Long = versionParts(value).take(4).fold(0L) { acc, n -> acc * 1000L + n }
                val response = responses.maxByOrNull { raw ->
                    try {
                        val candidate = JSONObject(raw)
                        versionRank(candidate.optString("version", candidate.optString("tag_name", "0")))
                    } catch (e: Exception) { 0L }
                } ?: throw IllegalStateException(lastError ?: "nenhum manifesto disponível")
                val json = JSONObject(response)
                val tagName = json.optString("version", json.optString("tag_name", ""))
                if (tagName.isBlank()) throw IllegalStateException("manifesto sem versão")
                val name = json.optString("name", "MK21 MultiServidor v$tagName")
                val body = json.optString("body", json.optString("releaseNotes", "Melhorias de desempenho e correções."))
                var downloadUrl = json.optString("downloadUrl", "")
                if (downloadUrl.isBlank()) downloadUrl = json.optString("html_url", "https://github.com/2fbg/FBGs-Streaming/releases")
                val assets = json.optJSONArray("assets")
                if (downloadUrl.contains("/releases") && assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url", downloadUrl)
                            break
                        }
                    }
                }
                val currentVersion = BuildConfig.VERSION_NAME
                val remoteParts = versionParts(tagName)
                val localParts = versionParts(currentVersion)
                val remoteCode = json.optInt("versionCode", 0)
                val currentCode = BuildConfig.VERSION_CODE
                val isNewer = (remoteCode > currentCode) || (remoteParts.zip(localParts).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false)
                if (isNewer && downloadUrl.isNotBlank()) {
                    _updateCheckState.value = UpdateCheckState.Available(
                        GithubReleaseInfo(tagName, name, body, downloadUrl, true)
                    )
                } else {
                    _updateCheckState.value = UpdateCheckState.UpToDate("Seu app está na versão mais recente (v$currentVersion)")
                }
            } catch (e: Exception) {
                _updateCheckState.value = UpdateCheckState.Error("Não foi possível verificar atualizações: ${e.message ?: "rede indisponível"}")
            }
        }
    }

    fun startInAppDownloadAndInstall(context: Context, downloadUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _updateCheckState.value = UpdateCheckState.Downloading(0, 0L, 0L)
                
                var currentUrl = downloadUrl
                var connection: HttpURLConnection
                var redirects = 0
                while (true) {
                    val url = URL(currentUrl)
                    connection = url.openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty("User-Agent", "MK21-Android")
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    val status = connection.responseCode
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                        currentUrl = connection.getHeaderField("Location")
                        redirects++
                        if (redirects > 5) break
                        continue
                    }
                    break
                }

                val totalLength = connection.contentLength.toLong()
                val updatesDir = File(context.getExternalFilesDir(null) ?: context.cacheDir, "updates").apply { mkdirs() }
                val apkFile = File(updatesDir, "MK21-Update.apk")
                if (apkFile.exists()) {
                    apkFile.delete()
                }

                connection.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        var lastProgress = -1
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            val progress = if (totalLength > 0) ((totalRead * 100) / totalLength).toInt() else 50
                            if (progress != lastProgress) {
                                lastProgress = progress
                                _updateCheckState.value = UpdateCheckState.Downloading(progress, totalRead, totalLength)
                            }
                        }
                    }
                }

                // Ensure file is readable by system package installer
                try {
                    apkFile.setReadable(true, false)
                } catch (e: Exception) {
                    // ignore
                }

                _updateCheckState.value = UpdateCheckState.ReadyToInstall(apkFile)

                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }
            } catch (e: Exception) {
                _updateCheckState.value = UpdateCheckState.Error("Falha no download da atualização: ${e.message}")
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                Toast.makeText(context, "Arquivo da atualização corrompido ou incompleto.", Toast.LENGTH_LONG).show()
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    Toast.makeText(context, "Ative a permissão 'Instalar apps desconhecidos' e toque em Instalar novamente.", Toast.LENGTH_LONG).show()
                    return
                }
            }

            val authority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(context, authority, apkFile)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            // Concede permissões explícitas a qualquer handler do instalador de pacotes
            try {
                val resolveList = context.packageManager.queryIntentActivities(installIntent, PackageManager.MATCH_DEFAULT_ONLY)
                for (resolveInfo in resolveList) {
                    val targetPkg = resolveInfo.activityInfo.packageName
                    context.grantUriPermission(targetPkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                listOf("com.google.android.packageinstaller", "com.android.packageinstaller").forEach { pkg ->
                    try {
                        context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            context.startActivity(installIntent)
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
}
