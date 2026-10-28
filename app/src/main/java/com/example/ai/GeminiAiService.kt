package com.example.ai

import android.util.Log
import com.example.data.model.PlaylistItem
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "GeminiAiService"

/**
 * Service encapsulating Gemini API inference using Firebase AI Logic.
 * Mandates lazy model evaluation, 60s realistic timeout, and cooperative cancellation.
 */
class GeminiAiService {

    // Lazy initialization to prevent startup crashes before Firebase is ready
    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    /**
     * Streams Gemini AI responses for conversational assistance and content discovery.
     */
    fun streamCopilotResponse(
        userPrompt: String,
        currentPlaying: PlaylistItem?,
        catalogSample: List<PlaylistItem>
    ): Flow<String> = flow {
        val promptBuilder = StringBuilder()
        promptBuilder.appendLine("Você é o MK21 AI Copilot, um assistente inteligente integrado em uma plataforma de streaming e IPTV de alta performance.")
        promptBuilder.appendLine("Responda sempre em português do Brasil, de forma amigável, concisa, objetiva e útil para o usuário.")

        if (currentPlaying != null) {
            promptBuilder.appendLine("Conteúdo atualmente selecionado/em reprodução:")
            promptBuilder.appendLine("- Título: ${currentPlaying.name}")
            promptBuilder.appendLine("- Categoria: ${currentPlaying.category}")
            promptBuilder.appendLine("- Tipo: ${currentPlaying.contentType}")
        }

        if (catalogSample.isNotEmpty()) {
            promptBuilder.appendLine("\nAqui estão alguns canais/filmes/séries disponíveis na playlist atual do usuário (amostra para recomendação):")
            catalogSample.take(30).forEach { item ->
                promptBuilder.appendLine("- [${item.contentType}] ${item.name} (${item.category})")
            }
        }

        promptBuilder.appendLine("\nPergunta ou pedido do usuário:")
        promptBuilder.appendLine(userPrompt)
        promptBuilder.appendLine("\nSe você sugerir itens do catálogo acima, mencione os nomes exatos para que o usuário possa encontrá-los facilmente.")

        val fullPrompt = promptBuilder.toString()
        Log.d(TAG, "Initiating Gemini stream with prompt length: ${fullPrompt.length}")

        try {
            withTimeout(60_000L) {
                model.generateContentStream(fullPrompt).collect { chunk ->
                    val text = chunk.text.orEmpty()
                    if (text.isNotEmpty()) {
                        emit(text)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini stream timed out after 60s", e)
            throw IllegalStateException("A resposta da IA demorou mais de 60 segundos. Tente novamente.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini inference failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Falha ao consultar a API Gemini.")
        }
    }

    /**
     * Generates an enriched synopsis and thematic critique for a channel or VOD.
     */
    suspend fun getEnrichedContentInfo(item: PlaylistItem): String {
        val prompt = """
            Você é um crítico de entretenimento e especialista em IPTV/Streaming.
            Forneça um resumo atrativo, temática principal, classificação indicativa recomendada e 3 destaques sobre:
            - Título: "${item.name}"
            - Tipo: ${item.contentType}
            - Categoria declarada: "${item.category}"

            Formato da resposta (em português, curto e bem formatado com emojis):
            🎬 **Sinopse / O que é:** (2 a 3 frases envolventes)
            ⭐ **Destaques:** (3 tópicos rápidos com o que torna esse conteúdo interessante)
            🎯 **Público Indicado:** (Ex: Fãs de ação, Família, Esportes)
        """.trimIndent()

        return try {
            withTimeout(60_000L) {
                val response = model.generateContent(prompt)
                response.text ?: "Não foi possível obter detalhes adicionais sobre este conteúdo."
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Synopsis generation timed out", e)
            "Tempo limite excedido ao buscar informações com a IA."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error generating synopsis", e)
            "Erro ao consultar IA: ${e.localizedMessage ?: "indisponível no momento"}"
        }
    }
}
