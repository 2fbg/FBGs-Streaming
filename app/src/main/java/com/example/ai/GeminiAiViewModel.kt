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
                text = "Olá! Sou o MK21 AI Copilot, alimentado pelo Gemini API. Posso recomendar canais, filmes ou séries da sua lista, sugerir o que assistir hoje ou analisar o conteúdo atual. Em que posso te ajudar?"
            )
        )
    )
    val messages: StateFlow<List<CopilotMessage>> = _messages.asStateFlow()

    private var currentJob: Job? = null

    fun askGemini(
        prompt: String,
        currentPlaying: PlaylistItem?,
        catalog: List<PlaylistItem>
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
                // Stream response token-by-token
                aiService.streamCopilotResponse(
                    userPrompt = trimmedPrompt,
                    currentPlaying = currentPlaying,
                    catalogSample = catalog
                ).collect { chunk ->
                    accumulated.append(chunk)
                    _uiState.value = GeminiUiState.Streaming(accumulated.toString())
                }

                val finalOutput = accumulated.toString().trim()
                if (finalOutput.isNotEmpty()) {
                    _uiState.value = GeminiUiState.Success(finalOutput)
                    _messages.value = _messages.value + CopilotMessage(
                        sender = "gemini",
                        text = finalOutput
                    )
                } else {
                    _uiState.value = GeminiUiState.Error("O modelo não retornou conteúdo.")
                }
            } catch (e: CancellationException) {
                // Mandatory: re-throw to allow cooperative coroutine cancellation
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini copilot query failed", e)
                val errMsg = e.localizedMessage ?: "Erro na comunicação com o Gemini."
                _uiState.value = GeminiUiState.Error(errMsg)
                _messages.value = _messages.value + CopilotMessage(
                    sender = "gemini",
                    text = "⚠️ Não foi possível completar a consulta: $errMsg"
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
                    text = "🎬 **Análise com Gemini:** ${item.name}\n\n$synopsis"
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
