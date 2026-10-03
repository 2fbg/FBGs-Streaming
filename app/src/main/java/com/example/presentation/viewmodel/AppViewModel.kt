package com.example.presentation.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.ContentType
import com.example.data.model.PlaylistItem
import com.example.data.model.ServerProfile
import com.example.data.preferences.PreferencesService
import com.example.data.remote.ServerApi
import com.example.data.repository.ServerRepositoryImpl
import com.example.data.utils.ParsedServer
import com.example.domain.repository.ServerRepository
import com.example.domain.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Eventos one-shot emitidos pelo ViewModel para a UI através de SharedFlow.
 */
sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    data class OpenExternalPlayer(
        val url: String,
        val title: String,
        val preferredPlayer: String
    ) : UiEvent()
    data object LoginSuccess : UiEvent()
}

/**
 * ViewModel Principal do MK21 Player na arquitetura MVVM + Clean Architecture.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val serverDao = database.serverDao()
    private val playlistDao = database.playlistItemDao()
    val preferencesService = PreferencesService(application)
    private val serverApi = ServerApi.create()

    val serverRepository: ServerRepository = ServerRepositoryImpl(
        serverDao = serverDao,
        serverApi = serverApi,
        preferencesService = preferencesService
    )

    // Casos de uso do Domínio
    val loginUseCase = LoginUseCase(serverRepository, preferencesService)
    val importServersUseCase = ImportServersFromTextUseCase(serverRepository, preferencesService)
    val deleteServersUseCase = DeleteServersUseCase(serverRepository)
    val loadDynamicServersUseCase = LoadDynamicServersUseCase(serverRepository, preferencesService)

    // SharedFlow para eventos únicos (One-shot)
    private val _uiEvent = MutableSharedFlow<UiEvent>(replay = 0)
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    // Estado dos Servidores no Room
    val servers: StateFlow<List<ServerProfile>> = serverRepository.getAllServers().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val activeServers: StateFlow<List<ServerProfile>> = serverRepository.getActiveServers().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Preferências e Credenciais
    private val _username = MutableStateFlow(preferencesService.username)
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow(preferencesService.password)
    val password: StateFlow<String> = _password.asStateFlow()

    private val _activePlaylistName = MutableStateFlow(preferencesService.activePlaylistName)
    val activePlaylistName: StateFlow<String> = _activePlaylistName.asStateFlow()

    private val _useExternalPlayer = MutableStateFlow(preferencesService.useExternalPlayer)
    val useExternalPlayer: StateFlow<Boolean> = _useExternalPlayer.asStateFlow()

    private val _preferredExternalPlayer = MutableStateFlow(preferencesService.externalPlayerType)
    val preferredExternalPlayer: StateFlow<String> = _preferredExternalPlayer.asStateFlow()

    private val _dynamicServersUrl = MutableStateFlow(preferencesService.dynamicServersUrl)
    val dynamicServersUrl: StateFlow<String> = _dynamicServersUrl.asStateFlow()

    private val _useAmoledMode = MutableStateFlow(preferencesService.useAmoledMode)
    val useAmoledMode: StateFlow<Boolean> = _useAmoledMode.asStateFlow()

    // Estados de UI e Carregamento
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Filtros e Categorias
    private val _selectedContentType = MutableStateFlow(ContentType.LIVE)
    val selectedContentType: StateFlow<ContentType> = _selectedContentType.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Todas")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Item em reprodução
    private val _currentPlayingItem = MutableStateFlow<PlaylistItem?>(null)
    val currentPlayingItem: StateFlow<PlaylistItem?> = _currentPlayingItem.asStateFlow()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    val isPlayerPlaying = MutableStateFlow(true)

    // Aggressive Lazy Loading Page Limit for Memory-Constrained Devices
    private val _displayedItemsLimit = MutableStateFlow(60)
    val displayedItemsLimit: StateFlow<Int> = _displayedItemsLimit.asStateFlow()

    fun loadMoreItems() {
        _displayedItemsLimit.value = (_displayedItemsLimit.value + 60).coerceAtMost(1500)
    }

    fun resetItemsLimit() {
        _displayedItemsLimit.value = 60
    }

    // Flow reativo dos canais do servidor ativo
    val activeItemsList: StateFlow<List<PlaylistItem>> = combine(
        combine(_activePlaylistName, _selectedContentType, _selectedCategory, _searchQuery) { playlist, type, category, query ->
            Quadruple(playlist, type, category, query)
        },
        _displayedItemsLimit
    ) { (playlist, type, category, query), limit ->
        Pair(Quadruple(playlist, type, category, query), limit)
    }.flatMapLatest { (quad, limit) ->
        val (playlist, type, category, query) = quad
        val baseFlow = if (query.isNotBlank()) {
            playlistDao.searchItems(playlist, "%$query%")
        } else if (category == "★ Favoritos") {
            playlistDao.getFavorites(playlist).map { list ->
                list.filter { it.contentType == type.name }
            }
        } else if (category == "Todas") {
            playlistDao.getItemsByType(playlist, type.name)
        } else {
            playlistDao.getItemsByCategoryAndType(playlist, category, type.name)
        }
        baseFlow.map { list -> list.take(limit) }
    }.flowOn(Dispatchers.IO).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Categorias dinâmicas
    val activeCategories: StateFlow<List<String>> = combine(
        _activePlaylistName,
        _selectedContentType
    ) { playlist, type ->
        Pair(playlist, type)
    }.flatMapLatest { (playlist, type) ->
        playlistDao.getCategoriesByType(playlist, type.name).map { list ->
            listOf("Todas", "★ Favoritos") + list
        }
    }.flowOn(Dispatchers.IO).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = listOf("Todas")
    )

    init {
        // Inicializa servidores padrão no Room se banco estiver vazio
        viewModelScope.launch(Dispatchers.IO) {
            serverRepository.loadCachedOrPredefinedServers()
        }
    }

    // ==========================================
    // Autenticação & Login Único
    // ==========================================

    fun login(user: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = loginUseCase(user, pass)
            result.onSuccess {
                _username.value = user.trim()
                _password.value = pass.trim()
                _uiEvent.emit(UiEvent.ShowToast("Login efetuado com sucesso! Sincronizando servidores..."))
                _uiEvent.emit(UiEvent.LoginSuccess)
                loadChannelsForActiveServer()
            }.onFailure { e ->
                _errorMessage.value = e.message ?: "Erro ao realizar login."
                _uiEvent.emit(UiEvent.ShowToast(e.message ?: "Erro ao realizar login."))
            }

            _isLoading.value = false
        }
    }

    // ==========================================
    // Manutenção em Massa de Servidores
    // ==========================================

    fun previewServersFromText(text: String): List<ParsedServer> {
        return importServersUseCase.parsePreview(text)
    }

    fun savePreviewedServers(parsedList: List<ParsedServer>) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = importServersUseCase.saveExtracted(parsedList)
            result.onSuccess { list ->
                _uiEvent.emit(UiEvent.ShowToast("${list.size} servidores importados com sucesso!"))
            }.onFailure { e ->
                _uiEvent.emit(UiEvent.ShowToast("Falha ao salvar servidores: ${e.message}"))
            }
            _isLoading.value = false
        }
    }

    fun deleteServers(serverIds: List<String>) {
        viewModelScope.launch {
            val result = deleteServersUseCase(serverIds)
            result.onSuccess {
                _uiEvent.emit(UiEvent.ShowToast("${serverIds.size} servidor(es) removido(s)."))
            }.onFailure { e ->
                _uiEvent.emit(UiEvent.ShowToast("Erro ao excluir: ${e.message}"))
            }
        }
    }

    fun toggleServerActive(serverId: String, isActive: Boolean) {
        viewModelScope.launch {
            serverRepository.toggleServerActive(serverId, isActive)
        }
    }

    fun loadDynamicServers() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = loadDynamicServersUseCase()
            result.onSuccess { list ->
                _uiEvent.emit(UiEvent.ShowToast("${list.size} servidores atualizados da nuvem!"))
            }.onFailure { e ->
                _uiEvent.emit(UiEvent.ShowToast("Erro ao baixar servidores da nuvem: ${e.message}"))
            }
            _isLoading.value = false
        }
    }

    fun updateDynamicServersUrl(newUrl: String) {
        preferencesService.dynamicServersUrl = newUrl.trim()
        _dynamicServersUrl.value = newUrl.trim()
    }

    // ==========================================
    // Preferências de Reprodução & Player Duplo
    // ==========================================

    fun setUseExternalPlayer(enabled: Boolean) {
        preferencesService.useExternalPlayer = enabled
        _useExternalPlayer.value = enabled
    }

    fun setPreferredExternalPlayer(player: String) {
        preferencesService.externalPlayerType = player
        _preferredExternalPlayer.value = player
    }

    fun onChannelSelected(
        item: PlaylistItem,
        onNavigateToInternalPlayer: (PlaylistItem) -> Unit
    ) {
        if (_useExternalPlayer.value) {
            // Emite evento para abrir player externo selecionado
            viewModelScope.launch {
                _uiEvent.emit(
                    UiEvent.OpenExternalPlayer(
                        url = item.url,
                        title = item.name,
                        preferredPlayer = _preferredExternalPlayer.value
                    )
                )
            }
        } else {
            // Abre no player interno Media3 ExoPlayer
            _currentPlayingItem.value = item
            onNavigateToInternalPlayer(item)
        }
    }

    fun toggleFavorite(itemId: Long, isFavorite: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.setFavorite(itemId, isFavorite)
        }
    }

    // ==========================================
    // Configurações Gerais
    // ==========================================

    fun setUseAmoledMode(enabled: Boolean) {
        preferencesService.useAmoledMode = enabled
        _useAmoledMode.value = enabled
    }

    fun setActivePlaylistName(name: String) {
        resetItemsLimit()
        preferencesService.activePlaylistName = name
        _activePlaylistName.value = name
        loadChannelsForActiveServer()
    }

    fun setContentType(type: ContentType) {
        resetItemsLimit()
        _selectedContentType.value = type
        _selectedCategory.value = "Todas"
    }

    fun setSelectedCategory(category: String) {
        resetItemsLimit()
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        resetItemsLimit()
        _searchQuery.value = query
    }

    fun setIsInPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    fun togglePlayPause() {
        isPlayerPlaying.value = !isPlayerPlaying.value
    }

    fun playNext() {}
    fun playPrevious() {}

    /**
     * Carrega a lista de canais do servidor ativo se ainda não estiver em cache no banco local.
     */
    fun loadChannelsForActiveServer() {
        val serverName = _activePlaylistName.value
        val user = _username.value
        val pass = _password.value

        viewModelScope.launch(Dispatchers.IO) {
            val server = servers.value.find { it.name.equals(serverName, ignoreCase = true) }
            val baseUrl = server?.baseUrl ?: "http://myopbx.beer"
            val targetUser = server?.username ?: user
            val targetPass = server?.password ?: pass

            if (targetUser.isBlank() || targetPass.isBlank()) return@launch

            val m3uUrl = "$baseUrl/get.php?username=$targetUser&password=$targetPass&type=m3u_plus&output=mpegts"
            
            try {
                _isLoading.value = true
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(m3uUrl)
                    .header("User-Agent", "MK21Player/1.0")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val stream = response.body?.byteStream()
                    if (stream != null) {
                        val parsedItems = com.example.data.parser.M3UParser.parse(stream, serverName) { /* progress */ }
                        if (parsedItems.isNotEmpty()) {
                            playlistDao.clearAndInsertPlaylistItems(serverName, parsedItems)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("AppViewModel", "Não foi possível carregar M3U remoto: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
