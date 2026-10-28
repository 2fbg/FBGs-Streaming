package com.example.ai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.PlaylistItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "GeminiAiViewModel"

sealed interface GeminiUiState {
    data object Idle : GeminiUiState
    data object Loading : GeminiUiState
    data class Streaming(val text: String) : GeminiUiState
    data class Success(val text: String) : GeminiUiState
    data class Error(val message: String) : GeminiUiState
}

data class CopilotMessage(
    val sender: String, // "user" or "gemini"
    val text: String,
    val suggestedItems: List<PlaylistItem> = emptyList(),
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
                text = "Olá! Sou o MK21 AI Copilot. Posso te dizer onde assistir a qualquer jogo de futebol ao vivo, recomendar filmes ou séries da sua lista e tirar dúvidas sobre fontes e qualidade. Como posso te ajudar?"
            )
        )
    )
    val messages: StateFlow<List<CopilotMessage>> = _messages.asStateFlow()

    private var currentJob: Job? = null

    fun askGemini(
        prompt: String,
        currentPlaying: PlaylistItem?,
        catalog: List<PlaylistItem>,
        relevantCatalogFetcher: (suspend (String) -> List<PlaylistItem>)? = null
    ) {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isEmpty()) return

        // Append user message immediately
        val userMsg = CopilotMessage(sender = "user", text = trimmedPrompt)
        _messages.value = _messages.value + userMsg

        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            _uiState.value = GeminiUiState.Loading
            val accumulated = StringBuilder()

            try {
                // Dynamically fetch targeted items for this prompt (e.g., live sports channels for football matches)
                val targetCatalog = if (relevantCatalogFetcher != null) {
                    try {
                        val fetched = relevantCatalogFetcher(trimmedPrompt)
                        if (fetched.isNotEmpty()) fetched else catalog
                    } catch (e: Exception) {
                        catalog
                    }
                } else {
                    catalog
                }

                // Stream response token-by-token
                aiService.streamCopilotResponse(
                    userPrompt = trimmedPrompt,
                    currentPlaying = currentPlaying,
                    catalogSample = targetCatalog
                ).collect { chunk ->
                    accumulated.append(chunk)
                    _uiState.value = GeminiUiState.Streaming(accumulated.toString())
                }

                val finalOutput = accumulated.toString().trim()
                if (finalOutput.isNotEmpty()) {
                    _uiState.value = GeminiUiState.Success(finalOutput)

                    // Extract clickable recommended items for instant play
                    val lower = trimmedPrompt.lowercase()
                    val isSports = lower.contains("jogo") || lower.contains("futebol") ||
                            lower.contains("onde assistir") || lower.contains("assistir") ||
                            lower.contains("palmeiras") || lower.contains("santos") ||
                            lower.contains("flamengo") || lower.contains("corinthians") ||
                            lower.contains("são paulo") || lower.contains("sao paulo") ||
                            lower.contains("vasco") || lower.contains("botafogo") ||
                            lower.contains("fluminense") || lower.contains("gremio") ||
                            lower.contains("grêmio") || lower.contains("inter") ||
                            lower.contains("cruzeiro") || lower.contains("atletico") ||
                            lower.contains("champions") || lower.contains("libertadores") ||
                            lower.contains("premiere") || lower.contains("sportv") ||
                            lower.contains("espn") || lower.contains("cazé") || lower.contains("caze")

                    val matchedSuggestions = if (isSports) {
                        targetCatalog.filter { item ->
                            val n = item.name.uppercase()
                            val c = item.category.uppercase()
                            n.contains("PREMIERE") || n.contains("SPORTV") || n.contains("ESPN") ||
                            n.contains("GLOBO") || n.contains("CAZÉ") || n.contains("CAZE") ||
                            n.contains("TNT") || n.contains("RECORD") || n.contains("BAND") ||
                            c.contains("ESPORTE") || c.contains("FUTEBOL")
                        }.distinctBy { it.name }.take(5)
                    } else {
                        val words = lower.split(" ", ",", ".", "-").filter { it.length >= 3 }
                        targetCatalog.filter { item ->
                            words.any { kw -> item.name.lowercase().contains(kw) || item.category.lowercase().contains(kw) }
                        }.distinctBy { it.name }.take(4)
                    }

                    _messages.value = _messages.value + CopilotMessage(
                        sender = "gemini",
                        text = finalOutput,
                        suggestedItems = matchedSuggestions
                    )
                } else {
                    _uiState.value = GeminiUiState.Error("O modelo não retornou conteúdo.")
                }
            } catch (e: CancellationException) {
                // Mandatory: re-throw to allow cooperative coroutine cancellation
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
            _uiState.value = GeminiUiState.Loading
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
                text = "Conversa reiniciada. O que gostaria de assistir ou pesquisar agora?"
            )
        )
    }
}
