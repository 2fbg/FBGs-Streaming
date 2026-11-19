package com.example.ai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.PlaylistItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "GeminiAiViewModel"

sealed interface GeminiUiState {
    data object Idle : GeminiUiState
    data class Loading(
        val statusLabel: String = "Consultando Gemini...",
        val epgPreviewMatches: List<CopilotEpgChannelMatch> = emptyList()
    ) : GeminiUiState

    data class Streaming(
        val text: String,
        val epgPreviewMatches: List<CopilotEpgChannelMatch> = emptyList()
    ) : GeminiUiState

    data class Success(val text: String) : GeminiUiState
    data class Error(val message: String) : GeminiUiState
}

data class CopilotMessage(
    val sender: String, // "user" or "gemini"
    val text: String,
    val suggestedItems: List<PlaylistItem> = emptyList(),
    val epgMatches: List<CopilotEpgChannelMatch> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

class GeminiAiViewModel(
    private val aiService: GeminiAiService = GeminiAiService()
) : ViewModel() {

    private val _uiState = MutableStateFlow<GeminiUiState>(GeminiUiState.Idle)
    val uiState: StateFlow<GeminiUiState> = _uiState.asStateFlow()

    private val _messages = MutableStateFlow<List<CopilotMessage>>(
        listOf(
            CopilotMessage(
                sender = "gemini",
                text = "Olá! Sou o MK21 AI Copilot. Digite o nome de um time (ex: **Palmeiras**, **Flamengo**, **Corinthians**) para ver os canais com **EPG real de 7 dias** (agora, hoje e na semana), ou peça recomendações de filmes e séries."
            )
        )
    )
    val messages: StateFlow<List<CopilotMessage>> = _messages.asStateFlow()

    private var currentJob: Job? = null

    fun askGemini(
        prompt: String,
        currentPlaying: PlaylistItem?,
        catalog: List<PlaylistItem>,
        relevantCatalogFetcher: (suspend (String) -> List<PlaylistItem>)? = null,
        epgSearcher: (suspend (String) -> CopilotEpgSearchResult?)? = null
    ) {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isEmpty()) return

        // Append user message immediately
        val userMsg = CopilotMessage(sender = "user", text = trimmedPrompt)
        _messages.value = _messages.value + userMsg

        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            val shouldSearchEpg = epgSearcher != null && CopilotEpgMatcher.shouldSearchEpg(trimmedPrompt)
            _uiState.value = GeminiUiState.Loading(
                statusLabel = if (shouldSearchEpg) "Consultando EPG de 7 dias..." else "Consultando catálogo..."
            )
            val accumulated = StringBuilder()

            try {
                // Executa busca de catálogo e varredura de EPG semanal em paralelo para resposta rápida.
                val (targetCatalog, epgResult) = coroutineScope {
                    val catalogDeferred = async {
                        if (relevantCatalogFetcher != null) {
                            try {
                                val fetched = relevantCatalogFetcher(trimmedPrompt)
                                if (fetched.isNotEmpty()) fetched else catalog
                            } catch (_: Exception) {
                                catalog
                            }
                        } else {
                            catalog
                        }
                    }
                    val epgDeferred = async {
                        if (shouldSearchEpg) {
                            try {
                                epgSearcher?.invoke(trimmedPrompt)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "EPG search failed gracefully: ${e.message}")
                                null
                            }
                        } else {
                            null
                        }
                    }
                    catalogDeferred.await() to epgDeferred.await()
                }

                val instantEpgMatches = epgResult?.allMatches?.take(18).orEmpty()

                // Já exibe imediatamente os cartões de EPG encontrados enquanto o Gemini faz streaming do resumo!
                _uiState.value = GeminiUiState.Loading(
                    statusLabel = if (instantEpgMatches.isNotEmpty()) {
                        "${instantEpgMatches.size} transmissões no EPG • Gerando resumo..."
                    } else {
                        "Gerando resposta..."
                    },
                    epgPreviewMatches = instantEpgMatches
                )

                // Stream response token-by-token com contexto real de EPG semanal
                aiService.streamCopilotResponse(
                    userPrompt = trimmedPrompt,
                    currentPlaying = currentPlaying,
                    catalogSample = targetCatalog,
                    epgSearchResult = epgResult
                ).collect { chunk ->
                    accumulated.append(chunk)
                    _uiState.value = GeminiUiState.Streaming(
                        text = accumulated.toString(),
                        epgPreviewMatches = instantEpgMatches
                    )
                }

                val finalOutput = accumulated.toString().trim()
                if (finalOutput.isNotEmpty()) {
                    _uiState.value = GeminiUiState.Success(finalOutput)

                    val epgUrls = instantEpgMatches.map { it.item.url }.toHashSet()

                    // Deduplica sugestões por família de canal para nunca repetir 6 cópias de GLOBO SP (4K/FHD/HD/SD)
                    val matchedSuggestions = if (instantEpgMatches.isNotEmpty()) {
                        emptyList()
                    } else {
                        val isSports = CopilotEpgMatcher.isSportsOrTeamPrompt(trimmedPrompt)
                        if (isSports) {
                            CopilotEpgMatcher.deduplicateChannelsByFamily(
                                channels = targetCatalog.filter {
                                    CopilotEpgMatcher.isSportsOrOpenBroadcastChannel(it) && it.url !in epgUrls
                                },
                                maxCount = 6
                            )
                        } else {
                            val words = CopilotEpgMatcher.normalize(trimmedPrompt)
                                .split(" ")
                                .filter { it.length >= 3 }
                            CopilotEpgMatcher.deduplicateChannelsByFamily(
                                channels = targetCatalog.filter { item ->
                                    val normName = CopilotEpgMatcher.normalize(item.name)
                                    val normCat = CopilotEpgMatcher.normalize(item.category)
                                    words.any { kw -> normName.contains(kw) || normCat.contains(kw) }
                                },
                                maxCount = 5
                            )
                        }
                    }

                    _messages.value = _messages.value + CopilotMessage(
                        sender = "gemini",
                        text = finalOutput,
                        suggestedItems = matchedSuggestions,
                        epgMatches = instantEpgMatches
                    )
                } else {
                    _uiState.value = GeminiUiState.Error("O modelo não retornou conteúdo.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini copilot query failed", e)
                val rawMsg = e.localizedMessage ?: "Erro na comunicação com o Gemini."
                val userFriendlyMsg = if (rawMsg.contains("App Check", ignoreCase = true)) {
                    "A validação de segurança (Firebase App Check) está sincronizando. Aguarde alguns segundos e toque novamente na pergunta."
                } else {
                    rawMsg
                }
                _uiState.value = GeminiUiState.Error(userFriendlyMsg)
                _messages.value = _messages.value + CopilotMessage(
                    sender = "gemini",
                    text = "⚠️ Não foi possível completar a consulta: $userFriendlyMsg"
                )
            }
        }
    }

    fun enrichContentSynopsis(item: PlaylistItem) {
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            _uiState.value = GeminiUiState.Loading("Analisando conteúdo...")
            try {
                val synopsis = aiService.getEnrichedContentInfo(item)
                _uiState.value = GeminiUiState.Success(synopsis)
                _messages.value = _messages.value + CopilotMessage(
                    sender = "gemini",
                    text = "🎬 **Análise com Gemini:** ${item.name}\n\n$synopsis",
                    suggestedItems = listOf(item)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Content enrichment failed", e)
                _uiState.value = GeminiUiState.Error(e.localizedMessage ?: "Erro ao obter análise.")
            }
        }
    }

    fun clearHistory() {
        currentJob?.cancel()
        _uiState.value = GeminiUiState.Idle
        _messages.value = listOf(
            CopilotMessage(
                sender = "gemini",
                text = "Conversa reiniciada. Digite um time para consultar a grade EPG de 7 dias ou pergunte sobre canais, filmes e séries!"
            )
        )
    }
}
