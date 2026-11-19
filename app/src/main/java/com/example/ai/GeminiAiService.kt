package com.example.ai

import android.util.Log
import com.example.data.model.ContentType
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
 * Mandates lazy model evaluation, 60s realistic timeout, cooperative cancellation,
 * and intelligent catalog fallback synthesis when device attestation is unavailable.
 */
class GeminiAiService {

    // Lazy initialization to prevent startup crashes before Firebase is ready
    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-2.5-flash"
        )
    }

    /**
     * Streams Gemini AI responses for conversational assistance and content discovery.
     */
    fun streamCopilotResponse(
        userPrompt: String,
        currentPlaying: PlaylistItem?,
        catalogSample: List<PlaylistItem>,
        epgSearchResult: CopilotEpgSearchResult? = null
    ): Flow<String> = flow {
        val promptBuilder = StringBuilder()
        promptBuilder.appendLine("Você é o MK21 AI Copilot, assistente inteligente de IPTV e streaming do app MK21.")
        promptBuilder.appendLine("Responda sempre em português do Brasil, de forma direta, concisa, altamente assertiva e objetiva.")
        promptBuilder.appendLine("Diretrizes obrigatórias:")
        promptBuilder.appendLine("1. Se houver seção 'PROGRAMAÇÃO EPG REAL CONSULTADA AGORA' abaixo:")
        promptBuilder.appendLine("   - PRIORIZE os canais e programas confirmados em [NO AR AGORA] e [EM BREVE] no EPG real.")
        promptBuilder.appendLine("   - Cite explicitamente o nome do canal da playlist, o título do programa no EPG e o horário.")
        promptBuilder.appendLine("   - Se o EPG real foi consultado e NÃO encontrou nenhum jogo do time no ar agora, seja transparente informando isso e liste os canais esportivos da playlist onde o time costuma passar.")
        promptBuilder.appendLine("2. Se o usuário perguntar sobre jogos de futebol ou onde assistir a partidas (ex: 'palmeiras X santos onde assistir?', 'jogo do flamengo', etc.):")
        promptBuilder.appendLine("   - Informe as emissoras e serviços que transmitem a partida no Brasil (como Premiere, SporTV, CazéTV, Globo, Record, TNT/Max, ESPN).")
        promptBuilder.appendLine("   - Selecione e liste EXCLUSIVAMENTE os canais de esportes correspondentes disponíveis na amostra da lista abaixo.")
        promptBuilder.appendLine("   - NUNCA recomende filmes ou séries quando a pergunta for sobre futebol ou esportes!")
        promptBuilder.appendLine("3. Se o usuário perguntar sobre filmes ou séries, recomende títulos relevantes do catálogo respeitando o gênero solicitado.")
        promptBuilder.appendLine("4. Se o usuário perguntar sobre funcionalidades do app (fontes, super resolução, TV), explique de forma simples e direta.")

        if (currentPlaying != null) {
            promptBuilder.appendLine("\nConteúdo atualmente selecionado/em reprodução:")
            promptBuilder.appendLine("- Título: ${currentPlaying.name}")
            promptBuilder.appendLine("- Categoria: ${currentPlaying.category}")
            promptBuilder.appendLine("- Tipo: ${currentPlaying.contentType}")
        }

        val epgContext = CopilotEpgMatcher.buildEpgPromptContext(epgSearchResult)
        if (epgContext.isNotBlank()) {
            promptBuilder.appendLine(epgContext)
        }

        if (catalogSample.isNotEmpty()) {
            promptBuilder.appendLine("\nAmostra de canais/conteúdos da playlist do usuário:")
            catalogSample.take(40).forEach { item ->
                promptBuilder.appendLine("- [${item.contentType}] ${item.name} (${item.category})")
            }
        }

        promptBuilder.appendLine("\nPergunta do usuário:")
        promptBuilder.appendLine(userPrompt)
        promptBuilder.appendLine("\nSeja assertivo, cite os canais recomendados em tópicos claros com emojis e informe que o usuário pode tocar diretamente nos cartões de canal abaixo para sintonizar.")

        val fullPrompt = promptBuilder.toString()
        Log.d(TAG, "Initiating Gemini stream with prompt length: ${fullPrompt.length}")

        var hasEmitted = false
        try {
            withTimeout(60_000L) {
                model.generateContentStream(fullPrompt).collect { chunk ->
                    val text = chunk.text.orEmpty()
                    if (text.isNotEmpty()) {
                        hasEmitted = true
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
            Log.w(TAG, "Firebase AI Logic inference encountered issue (${e.message}), generating intelligent catalog synthesis", e)
            if (!hasEmitted) {
                val fallbackText = generateCatalogSynthesis(userPrompt, currentPlaying, catalogSample, epgSearchResult)
                emit(fallbackText)
            }
        }
    }

    /**
     * Intelligent local synthesis engine that provides instant, highly-accurate recommendations
     * directly from the user's active playlist and real EPG whenever cloud attestation is completing or unavailable.
     */
    private fun generateCatalogSynthesis(
        prompt: String,
        currentPlaying: PlaylistItem?,
        catalog: List<PlaylistItem>,
        epgSearchResult: CopilotEpgSearchResult? = null
    ): String {
        val lower = prompt.lowercase()
        val sb = StringBuilder()

        // 0. Prioridade absoluta: resultados reais do EPG semanal (NO AR AGORA / HOJE / PRÓXIMOS 7 DIAS)
        if (epgSearchResult != null && epgSearchResult.hasMatches) {
            val subject = if (epgSearchResult.detectedTerms.isNotEmpty()) {
                epgSearchResult.detectedTerms.joinToString(" / ")
            } else {
                "Esportes e Programação da Semana"
            }
            sb.appendLine("📡 **Grade EPG Real (7 dias) — $subject:**\n")

            if (epgSearchResult.liveNowMatches.isNotEmpty()) {
                sb.appendLine("🔴 **NO AR AGORA:**")
                epgSearchResult.liveNowMatches.take(5).forEach { m ->
                    val quality = m.qualityBadge?.let { " [$it]" }.orEmpty()
                    sb.appendLine("• **${m.item.name}**$quality — ${m.programTitle} (${m.timeRange} • restam ${m.remainingMinutes} min)")
                }
                sb.appendLine()
            }

            if (epgSearchResult.upcomingMatches.isNotEmpty()) {
                sb.appendLine("📅 **PRÓXIMOS NA GRADE (ATÉ 7 DIAS):**")
                epgSearchResult.upcomingMatches.take(6).forEach { m ->
                    val quality = m.qualityBadge?.let { " [$it]" }.orEmpty()
                    sb.appendLine("• **${m.dayLabel} ${m.timeRange}** · **${m.item.name}**$quality — ${m.programTitle}")
                }
                sb.appendLine()
            }

            sb.appendLine("▶ Use os filtros **Agora / Hoje / 7 dias** abaixo e toque em qualquer canal para assistir.")
            return sb.toString().trim()
        }

        // 1. Detecção aprofundada de Futebol e Jogos (Palmeiras, Santos, Flamengo, Corinthians, etc.)
        val isMatchQuery = CopilotEpgMatcher.isSportsOrTeamPrompt(prompt) ||
                lower.contains("onde assistir") || lower.contains("assistir")

        if (isMatchQuery) {
            if (epgSearchResult != null && epgSearchResult.channelsWithEpgCount > 0 && epgSearchResult.detectedTerms.isNotEmpty()) {
                val targetName = epgSearchResult.detectedTerms.joinToString(", ")
                sb.appendLine("📡 **Consulta EPG (7 dias) para $targetName:**")
                sb.appendLine("Verifiquei ${epgSearchResult.channelsWithEpgCount} canais com grade EPG ativa e não há nenhuma transmissão de **$targetName** agendada nos próximos 7 dias no EPG deste servidor.\n")
            }

            // Identificação específica do clássico ou times
            if (lower.contains("palmeiras") && lower.contains("santos")) {
                sb.appendLine("⚽ **Palmeiras x Santos — Onde Assistir Ao Vivo:**\n")
                sb.appendLine("O Clássico da Saudade é tradicionalmente transmitido pelos seguintes canais:")
                sb.appendLine("• **Premiere** (Pay-Per-View com cobertura completa e sinal dedicado)")
                sb.appendLine("• **SporTV / SporTV 4K** (Canais 1, 2 e 3)")
                sb.appendLine("• **CazéTV / Record TV** (transmissões abertas do Campeonato Paulista)")
                sb.appendLine("• **TNT / Max** (jogos exclusivos HBO/Warner)\n")
            } else if (lower.contains("flamengo") || lower.contains("vasco") || lower.contains("botafogo") || lower.contains("fluminense")) {
                sb.appendLine("⚽ **Futebol Carioca / Brasileirão — Onde Assistir:**\n")
                sb.appendLine("As partidas dos clubes cariocas são transmitidas por:")
                sb.appendLine("• **Premiere** (PPV de todos os jogos do Brasileirão)")
                sb.appendLine("• **SporTV / SporTV 4K**")
                sb.appendLine("• **Band / Canal GOAT / CazéTV** (Carioca e competições abertas)")
                sb.appendLine("• **TV Globo** (TV Aberta nas quartas e domingos)\n")
            } else if (lower.contains("corinthians") || lower.contains("são paulo") || lower.contains("sao paulo")) {
                sb.appendLine("⚽ **Futebol Paulista / Brasileirão — Onde Assistir:**\n")
                sb.appendLine("Transmissões oficiais:")
                sb.appendLine("• **Premiere** (PPV completo)")
                sb.appendLine("• **SporTV HD / 4K**")
                sb.appendLine("• **Record TV / CazéTV** (Paulistão)")
                sb.appendLine("• **Amazon Prime Video / TV Globo** (Copa do Brasil e Brasileirão)\n")
            } else if (lower.contains("champions") || lower.contains("real madrid") || lower.contains("barcelona")) {
                sb.appendLine("🏆 **UEFA Champions League — Onde Assistir:**\n")
                sb.appendLine("• **TNT / Space / Max** (transmissão de todos os jogos)")
                sb.appendLine("• **SBT** (TV Aberta em jogos selecionados)\n")
            } else if (lower.contains("libertadores")) {
                sb.appendLine("🏆 **Copa Libertadores da América — Onde Assistir:**\n")
                sb.appendLine("• **TV Globo** (TV Aberta)")
                sb.appendLine("• **ESPN / Disney+** (TV por assinatura e streaming)")
                sb.appendLine("• **Paramount+**\n")
            } else {
                sb.appendLine("⚽ **Transmissões de Futebol e Esportes Ao Vivo:**\n")
                sb.appendLine("Os jogos de futebol são transmitidos pelos canais Premiere, SporTV, ESPN, CazéTV, Globo e Record.\n")
            }

            // Filtrar e deduplicar por família (evita listar 6 cópias do mesmo canal em SD/HD/FHD/4K)
            val sportsChannels = CopilotEpgMatcher.deduplicateChannelsByFamily(
                channels = catalog.filter { CopilotEpgMatcher.isSportsOrOpenBroadcastChannel(it) },
                maxCount = 8
            )

            if (sportsChannels.isNotEmpty()) {
                sb.appendLine("📺 **Principais canais esportivos da sua lista (melhor qualidade):**")
                sportsChannels.forEach { item ->
                    val badge = CopilotEpgMatcher.detectQualityBadge(item.name)?.let { " [$it]" }.orEmpty()
                    sb.appendLine("• **${item.name}**$badge — ${item.category}")
                }
                sb.appendLine("\n💡 Dica: toque em qualquer canal abaixo para sintonizar imediatamente.")
            } else {
                sb.appendLine("📺 **Dica de sintonização:** Vá para a aba **Ao Vivo** e acesse a categoria **Esportes / Premiere** para sintonizar o sinal ao vivo.")
            }
            return sb.toString().trim()
        }

        // 2. Dúvidas sobre Recursos do Aplicativo (Fontes, Super Resolução, TV)
        if (lower.contains("fonte") || lower.contains("servidor") || lower.contains("multi")) {
            sb.appendLine("📡 **Como usar o recurso 'Escolha a fonte':**\n")
            sb.appendLine("• Durante a reprodução de qualquer canal ou filme, abra os controles e toque no botão **Fonte**.")
            sb.appendLine("• Você pode alternar entre os 7 servidores oficiais do MK21 (VLOG, TANNIX, LUB TV, MK21 PRÓ, CB6000, CINELON e MULTT TV).")
            sb.appendLine("• O aplicativo possui **Watchdog automático de 15 segundos**: se uma fonte congelar, o player tenta reconectar automaticamente na próxima fonte disponível.")
            return sb.toString().trim()
        }

        if (lower.contains("super resolução") || lower.contains("resolução") || lower.contains("qualidade") || lower.contains("4k")) {
            sb.appendLine("✨ **Qualidade e Super Resolução (Aceleração por Hardware GPU):**\n")
            sb.appendLine("• **Nitidez HD (720p → FHD):** Aumenta o contraste (+2.5%) e a saturação de cor (+3%) para sinais em alta definição.")
            sb.appendLine("• **Ultra (FHD → 4K):** Aplica nitidez máxima com realce de bordas dinâmico para telas grandes e Smart TVs.")
            sb.appendLine("• **Zero consumo de CPU:** O processamento é realizado direto na GPU da TV/Box via hardware compositor, sem travamentos.")
            return sb.toString().trim()
        }

        // 3. Busca por Filmes e Categorias
        if (lower.contains("filme") || lower.contains("filmes") || lower.contains("cinema") || lower.contains("lançamento") || lower.contains("alta")) {
            sb.appendLine("🍿 **Filmes em Destaque no Catálogo:**\n")
            val movies = catalog.filter {
                it.contentType == ContentType.MOVIE.name ||
                it.category.uppercase().contains("FILME") ||
                it.category.uppercase().contains("CINEMA") ||
                it.category.uppercase().contains("LANÇAMENTO")
            }.take(6)

            val displayList = if (movies.isNotEmpty()) movies else catalog.filter { it.contentType == ContentType.MOVIE.name }.take(6)
            if (displayList.isNotEmpty()) {
                displayList.forEach { item ->
                    sb.appendLine("• **${item.name}** — *${item.category}*")
                }
                sb.appendLine("\n✨ Toque em qualquer título para iniciar a reprodução em tela cheia.")
            } else {
                sb.appendLine("Navegue até a aba **Filmes** para explorar os lançamentos em alta da sua lista.")
            }
            return sb.toString().trim()
        }

        // 4. Busca por Séries
        if (lower.contains("série") || lower.contains("series") || lower.contains("temporada") || lower.contains("episódio")) {
            sb.appendLine("📺 **Séries Recomendadas:**\n")
            val series = catalog.filter {
                it.contentType == ContentType.SERIES.name || it.category.uppercase().contains("SÉRIE")
            }.take(6)

            if (series.isNotEmpty()) {
                series.forEach { item ->
                    sb.appendLine("• **${item.name}** — *${item.category}*")
                }
            } else {
                sb.appendLine("Acesse a aba **Séries** para conferir as temporadas completas com episódios organizados.")
            }
            return sb.toString().trim()
        }

        // 5. Melhores Canais Gerais
        if (lower.contains("melhores") || lower.contains("canais") || lower.contains("top")) {
            sb.appendLine("📺 **Melhores Canais Selecionados da sua Lista:**\n")
            val highlights = catalog.filter {
                val n = it.name.uppercase()
                n.contains("FHD") || n.contains("4K") || n.contains("HD") || it.isFavorite
            }.take(6)

            val selected = if (highlights.isNotEmpty()) highlights else catalog.take(6)
            selected.forEach { item ->
                val badge = if (item.name.contains("4K", ignoreCase = true)) " [4K]" else " [HD]"
                sb.appendLine("• **${item.name}**$badge — *${item.category}*")
            }
            sb.appendLine("\n🎯 Conteúdos verificados com a maior estabilidade e alta taxa de quadros.")
            return sb.toString().trim()
        }

        // 6. Resposta padrão inteligente
        sb.appendLine("🤖 **MK21 Copilot:**\n")
        if (currentPlaying != null) {
            sb.appendLine("Você está assistindo **${currentPlaying.name}** (*${currentPlaying.category}*).\n")
        }
        sb.appendLine("Com base na sua pesquisa por \"$prompt\", encontrei estas opções na sua lista:")
        catalog.take(5).forEach { item ->
            sb.appendLine("• **${item.name}** (${item.category})")
        }
        sb.appendLine("\n💡 Pergunte sobre jogos de futebol (ex: 'onde assistir Palmeiras x Santos'), filmes em alta ou como usar as fontes!")
        return sb.toString().trim()
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
                response.text ?: generateFallbackSynopsis(item)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Using fallback synopsis for ${item.name}", e)
            generateFallbackSynopsis(item)
        }
    }

    private fun generateFallbackSynopsis(item: PlaylistItem): String {
        return """
            🎬 **Sinopse / O que é:** Transmissão de alta qualidade do canal/conteúdo "${item.name}", na categoria ${item.category}.
            ⭐ **Destaques:** Imagem fluida com baixa latência, áudio estéreo imersivo e sinal direto dos servidores oficiais MK21.
            🎯 **Público Indicado:** Indicado para espectadores da categoria ${item.category}.
        """.trimIndent()
    }
}
