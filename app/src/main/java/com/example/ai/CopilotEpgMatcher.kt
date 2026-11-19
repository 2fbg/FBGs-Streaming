package com.example.ai

import com.example.data.model.ContentType
import com.example.data.model.PlaylistItem
import com.example.data.parser.XmltvEpgParser
import com.example.data.parser.XmltvProgramme
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Canal da playlist do usuário correlacionado com um programa real do EPG (ao vivo agora ou em breve).
 */
data class CopilotEpgChannelMatch(
    val item: PlaylistItem,
    val programTitle: String,
    val programDescription: String,
    val timeRange: String,
    val isLiveNow: Boolean,
    val progressPercent: Float,
    val remainingMinutes: Int,
    val startMs: Long,
    val qualityBadge: String? = CopilotEpgMatcher.detectQualityBadge(item.name)
)

/**
 * Resultado consolidado da varredura de EPG real para uma consulta no MK21 AI Copilot.
 */
data class CopilotEpgSearchResult(
    val detectedTerms: List<String>,
    val isLiveNowIntent: Boolean,
    val isSportsOrEpgIntent: Boolean,
    val liveNowMatches: List<CopilotEpgChannelMatch>,
    val upcomingMatches: List<CopilotEpgChannelMatch>,
    val scannedChannelsCount: Int,
    val channelsWithEpgCount: Int
) {
    val allMatches: List<CopilotEpgChannelMatch>
        get() = liveNowMatches + upcomingMatches

    val hasMatches: Boolean
        get() = liveNowMatches.isNotEmpty() || upcomingMatches.isNotEmpty()
}

data class CopilotEpgQueryIntent(
    val canonicalTeams: List<String>,
    val searchTerms: List<String>,
    val isLiveNowIntent: Boolean,
    val isSportsIntent: Boolean,
    val isEpgIntent: Boolean
) {
    val shouldSearchEpg: Boolean
        get() = canonicalTeams.isNotEmpty() || isSportsIntent || isEpgIntent
}

/**
 * Motor puro e determinístico para busca de times, jogos e programação ao vivo no EPG real (XMLTV / Short EPG / Público).
 */
object CopilotEpgMatcher {

    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val NON_ALNUM_REGEX = Regex("[^a-z0-9\\s-]")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    // Janela futura de 18h para exibir jogos "EM BREVE" além dos que já estão "NO AR AGORA".
    private const val UPCOMING_WINDOW_MS = 18L * 60L * 60L * 1000L

    private data class TeamAlias(
        val canonical: String,
        val aliases: List<String>
    )

    private val TEAM_ALIASES: List<TeamAlias> = listOf(
        TeamAlias("Flamengo", listOf("flamengo", "mengao", "mengo", "rubro-negro", "fla")),
        TeamAlias("Palmeiras", listOf("palmeiras", "verdao", "alviverde", "pal")),
        TeamAlias("Corinthians", listOf("corinthians", "timao", "cor")),
        TeamAlias("São Paulo", listOf("sao paulo", "spfc", "tricolor paulista")),
        TeamAlias("Santos", listOf("santos", "peixe")),
        TeamAlias("Vasco", listOf("vasco", "vasco da gama", "vascao", "cruzmaltino")),
        TeamAlias("Botafogo", listOf("botafogo", "fogao", "glorioso")),
        TeamAlias("Fluminense", listOf("fluminense", "tricolor das laranjeiras", "flu")),
        TeamAlias("Grêmio", listOf("gremio", "imortal")),
        TeamAlias("Internacional", listOf("internacional", "inter", "colorado")),
        TeamAlias("Cruzeiro", listOf("cruzeiro", "raposa", "cabuloso")),
        TeamAlias("Atlético-MG", listOf("atletico-mg", "atletico mg", "atletico mineiro", "galo")),
        TeamAlias("Athletico-PR", listOf("athletico", "athletico-pr", "atletico-pr", "atletico paranaense", "furacao")),
        TeamAlias("Bahia", listOf("bahia", "esquadrao")),
        TeamAlias("Fortaleza", listOf("fortaleza", "leao do pici")),
        TeamAlias("Vitória", listOf("vitoria")),
        TeamAlias("Bragantino", listOf("bragantino", "rb bragantino", "red bull bragantino")),
        TeamAlias("Ceará", listOf("ceara", "vozao")),
        TeamAlias("Sport", listOf("sport recife", "sport club do recife")),
        TeamAlias("Juventude", listOf("juventude")),
        TeamAlias("Cuiabá", listOf("cuiaba")),
        TeamAlias("Goiás", listOf("goias")),
        TeamAlias("Coritiba", listOf("coritiba", "coxa")),
        TeamAlias("Mirassol", listOf("mirassol")),
        TeamAlias("Criciúma", listOf("criciuma")),
        TeamAlias("América-MG", listOf("america-mg", "america mg", "america mineiro")),
        TeamAlias("Chapecoense", listOf("chapecoense", "chape")),
        TeamAlias("Remo", listOf("remo")),
        TeamAlias("Paysandu", listOf("paysandu", "papao")),
        TeamAlias("Náutico", listOf("nautico")),
        TeamAlias("Santa Cruz", listOf("santa cruz")),
        TeamAlias("Guarani", listOf("guarani")),
        TeamAlias("Ponte Preta", listOf("ponte preta")),
        TeamAlias("Novorizontino", listOf("novorizontino")),
        TeamAlias("Real Madrid", listOf("real madrid")),
        TeamAlias("Barcelona", listOf("barcelona", "barca")),
        TeamAlias("Atlético de Madrid", listOf("atletico de madrid", "atletico madrid")),
        TeamAlias("Manchester City", listOf("manchester city", "man city")),
        TeamAlias("Manchester United", listOf("manchester united", "man united")),
        TeamAlias("Liverpool", listOf("liverpool")),
        TeamAlias("Arsenal", listOf("arsenal")),
        TeamAlias("Chelsea", listOf("chelsea")),
        TeamAlias("Tottenham", listOf("tottenham")),
        TeamAlias("PSG", listOf("psg", "paris saint-germain", "paris saint germain")),
        TeamAlias("Bayern", listOf("bayern", "bayern de munique", "bayern munich")),
        TeamAlias("Borussia Dortmund", listOf("borussia", "dortmund")),
        TeamAlias("Juventus", listOf("juventus")),
        TeamAlias("Milan", listOf("milan", "ac milan")),
        TeamAlias("Inter de Milão", listOf("inter de milao", "inter milan")),
        TeamAlias("Napoli", listOf("napoli")),
        TeamAlias("Benfica", listOf("benfica")),
        TeamAlias("Porto", listOf("fc porto", "porto")),
        TeamAlias("Sporting", listOf("sporting")),
        TeamAlias("Boca Juniors", listOf("boca juniors", "boca")),
        TeamAlias("River Plate", listOf("river plate")),
        TeamAlias("Inter Miami", listOf("inter miami")),
        TeamAlias("Al-Nassr", listOf("al-nassr", "al nassr")),
        TeamAlias("Al-Hilal", listOf("al-hilal", "al hilal")),
        TeamAlias("Seleção Brasileira", listOf("selecao brasileira", "selecao", "brasil")),
        TeamAlias("Argentina", listOf("argentina"))
    )

    private val SPORTS_KEYWORDS = setOf(
        "futebol", "jogo", "jogos", "partida", "partidas", "esporte", "esportes",
        "campeonato", "brasileirao", "serie a", "serie b", "paulistao", "carioca",
        "gaucho", "mineiro", "copa do brasil", "libertadores", "sul-americana",
        "sul americana", "champions", "premier league", "la liga", "bundesliga",
        "nba", "nfl", "ufc", "mma", "formula 1", "f1", "tenis", "volei", "basquete",
        "premiere", "sportv", "espn", "cazetv", "caze", "bandsports", "tnt sports",
        "time", "clube", "classico", "rodada", "gol", "placar"
    )

    private val LIVE_NOW_KEYWORDS = setOf(
        "agora", "ao vivo", "no ar", "passando", "passa", "vai passar", "hoje",
        "epg", "grade", "programacao", "que canal", "qual canal", "onde passa",
        "onde assistir", "sintonizar", "transmissao", "transmite"
    )

    private val STOP_WORDS = setOf(
        "qual", "quais", "canal", "canais", "onde", "passa", "passando", "passar",
        "assistir", "ver", "agora", "hoje", "jogo", "jogos", "time", "times",
        "futebol", "partida", "partidas", "com", "para", "pra", "que", "esta",
        "estao", "vai", "vao", "tem", "dos", "das", "nos", "nas", "pelo", "pela",
        "sobre", "como", "quem", "epg", "grade", "programacao", "vivo", "lista",
        "buscar", "busca", "trazer", "traz", "mostra", "mostrar", "quero", "meu",
        "minha", "aqui", "algum", "alguma", "melhor", "melhores", "proximo",
        "proximos", "filme", "filmes", "serie", "series"
    )

    private val PRIORITY_SPORTS_CHANNEL_TOKENS = listOf(
        "PREMIERE", "SPORTV", "ESPN", "GLOBO", "TNT", "CAZE", "BANDSPORTS",
        "RECORD", "BAND", "SBT", "SPACE", "COMBATE", "PARAMOUNT", "PRIME",
        "FOX SPORTS", "DAZN", "GOAT", "NSPEED", "FUTEBOL", "ESPORTE", "SPORTS"
    )

    private val BROAD_SPORTS_EPG_MARKERS = listOf(
        " x ", " vs ", "futebol", "brasileir", "libertadores", "sul-americana",
        "copa do brasil", "champions", "premier league", "la liga", "serie a",
        "paulist", "carioca", "gaucho", "mineiro", "campeonato", "nba", "nfl",
        "ufc", "mma", "formula 1", "f1", "volei", "basquete", "tenis",
        "sportscenter", "globo esporte", "troca de passes", "selecao sportv",
        "redacao sportv", "jogo aberto", "donos da bola", "baita amigos",
        "pre-jogo", "pos-jogo", "ao vivo"
    )

    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val decomposed = Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        val stripped = DIACRITICS_REGEX.replace(decomposed, "")
        return MULTI_SPACE_REGEX.replace(NON_ALNUM_REGEX.replace(stripped, " "), " ").trim()
    }

    fun containsWholeTerm(normalizedText: String, normalizedTerm: String): Boolean {
        if (normalizedText.isEmpty() || normalizedTerm.isEmpty()) return false
        val regex = Regex("(?<![a-z0-9])${Regex.escape(normalizedTerm)}(?![a-z0-9])")
        return regex.containsMatchIn(normalizedText)
    }

    private fun firstWholeTermIndex(normalizedText: String, normalizedTerm: String): Int {
        if (normalizedText.isEmpty() || normalizedTerm.isEmpty()) return -1
        val regex = Regex("(?<![a-z0-9])${Regex.escape(normalizedTerm)}(?![a-z0-9])")
        return regex.find(normalizedText)?.range?.first ?: -1
    }

    fun extractIntent(prompt: String): CopilotEpgQueryIntent {
        val norm = normalize(prompt)
        if (norm.isEmpty()) {
            return CopilotEpgQueryIntent(emptyList(), emptyList(), false, false, false)
        }

        val detectedWithPos = ArrayList<Pair<Int, TeamAlias>>()
        for (team in TEAM_ALIASES) {
            val bestIdx = team.aliases
                .map { alias -> firstWholeTermIndex(norm, alias) }
                .filter { it >= 0 }
                .minOrNull()
            if (bestIdx != null) {
                detectedWithPos.add(bestIdx to team)
            }
        }
        detectedWithPos.sortBy { it.first }

        val canonicalTeams = LinkedHashSet<String>()
        val matchedTerms = LinkedHashSet<String>()
        for ((_, team) in detectedWithPos) {
            canonicalTeams.add(team.canonical)
            team.aliases.forEach { matchedTerms.add(it) }
        }

        val isLiveNowIntent = LIVE_NOW_KEYWORDS.any { containsWholeTerm(norm, it) }
        val isSportsIntent = canonicalTeams.isNotEmpty() || SPORTS_KEYWORDS.any { containsWholeTerm(norm, it) }
        val isEpgIntent = isLiveNowIntent || norm.contains("epg") || norm.contains("programacao") || norm.contains("grade")

        // Se o usuário digitou um time não listado (ex: "Novorizontino") ou um programa específico (ex: "Jornal Nacional"),
        // extrai palavras-chave relevantes ignorando stopwords.
        if (canonicalTeams.isEmpty()) {
            norm.split(" ")
                .asSequence()
                .map { it.trim('-') }
                .filter { it.length >= 3 && it !in STOP_WORDS }
                .forEach { matchedTerms.add(it) }
        }

        return CopilotEpgQueryIntent(
            canonicalTeams = canonicalTeams.toList(),
            searchTerms = matchedTerms.toList(),
            isLiveNowIntent = isLiveNowIntent,
            isSportsIntent = isSportsIntent,
            isEpgIntent = isEpgIntent
        )
    }

    fun isSportsOrTeamPrompt(prompt: String): Boolean {
        val intent = extractIntent(prompt)
        return intent.canonicalTeams.isNotEmpty() || intent.isSportsIntent
    }

    fun shouldSearchEpg(prompt: String): Boolean {
        return extractIntent(prompt).shouldSearchEpg
    }

    fun detectQualityBadge(channelName: String): String? {
        val upper = channelName.uppercase(Locale.ROOT)
        return when {
            upper.contains("4K") || upper.contains("UHD") -> "4K"
            upper.contains("FHD") || upper.contains("1080") -> "FHD"
            upper.contains("HD") || upper.contains("720") -> "HD"
            upper.contains("SD") -> "SD"
            else -> null
        }
    }

    fun qualityScore(channelName: String): Int {
        val upper = channelName.uppercase(Locale.ROOT)
        var score = when {
            upper.contains("4K") || upper.contains("UHD") -> 40
            upper.contains("FHD") || upper.contains("1080") -> 30
            upper.contains("HD") || upper.contains("720") -> 20
            upper.contains("SD") -> 10
            else -> 15
        }
        if (upper.contains("H265") || upper.contains("HEVC")) score += 2
        if (upper.contains("BACKUP") || upper.contains("ALT") || upper.contains("BKP")) score -= 6
        return score
    }

    fun isSportsOrOpenBroadcastChannel(item: PlaylistItem): Boolean {
        if (item.contentType != ContentType.LIVE.name) return false
        val upperName = normalize(item.name).uppercase(Locale.ROOT)
        val upperCat = normalize(item.category).uppercase(Locale.ROOT)
        return PRIORITY_SPORTS_CHANNEL_TOKENS.any { token ->
            upperName.contains(token) || upperCat.contains(token)
        }
    }

    /**
     * Seleciona até [maxCount] representantes únicos (por família normalizada de canal) para hidratação rápida de EPG em paralelo.
     */
    fun selectPriorityEpgChannels(
        channels: List<PlaylistItem>,
        maxCount: Int = 14
    ): List<PlaylistItem> {
        val liveOnly = channels.filter { it.contentType == ContentType.LIVE.name }
        if (liveOnly.isEmpty()) return emptyList()

        val sportsFirst = liveOnly.sortedWith(
            compareByDescending<PlaylistItem> { priorityChannelWeight(it) }
                .thenByDescending { qualityScore(it.name) }
        )

        val picked = ArrayList<PlaylistItem>(maxCount)
        val seenKeys = HashSet<String>()
        for (ch in sportsFirst) {
            if (picked.size >= maxCount) break
            if (priorityChannelWeight(ch) <= 0 && picked.size >= 6) break
            val key = canonicalChannelFamily(ch.name)
            if (key.isNotEmpty() && seenKeys.add(key)) {
                picked.add(ch)
            }
        }
        return picked
    }

    private fun priorityChannelWeight(item: PlaylistItem): Int {
        val n = normalize(item.name)
        val c = normalize(item.category)
        return when {
            n.contains("premiere") -> 100
            n.contains("sportv") -> 95
            n.contains("espn") -> 90
            n.contains("globo") && !n.contains("globonews") && !n.contains("globoplay") -> 85
            n.contains("tnt") -> 80
            n.contains("caze") -> 78
            n.contains("bandsports") -> 75
            n.contains("record") && !n.contains("news") -> 70
            n.contains("band") && !n.contains("news") -> 68
            n.contains("sbt") -> 65
            n.contains("space") -> 62
            n.contains("combate") || n.contains("dazn") || n.contains("fox sports") -> 60
            c.contains("esporte") || c.contains("futebol") || c.contains("sport") -> 50
            else -> 0
        }
    }

    private fun canonicalChannelFamily(name: String): String {
        val norm = XmltvEpgParser.normalizeChannelKey(name)
        val stripped = XmltvEpgParser.stripQualityTokens(norm)
        return stripped.ifEmpty { norm }
    }

    /**
     * Varre a lista de canais ao vivo cruzando com a programação real do EPG fornecida por [programmesProvider].
     */
    fun matchProgrammes(
        prompt: String,
        channels: List<PlaylistItem>,
        nowMs: Long = System.currentTimeMillis(),
        maxLiveResults: Int = 6,
        maxUpcomingResults: Int = 6,
        programmesProvider: (PlaylistItem) -> List<XmltvProgramme>
    ): CopilotEpgSearchResult {
        val intent = extractIntent(prompt)
        val liveChannels = channels.filter { it.contentType == ContentType.LIVE.name }
        val hasSpecificTerms = intent.canonicalTeams.isNotEmpty() || intent.searchTerms.isNotEmpty()

        var channelsWithEpg = 0
        val rawMatches = ArrayList<ScoredMatch>()

        for (channel in liveChannels) {
            val programmes = programmesProvider(channel)
            if (programmes.isEmpty()) continue
            channelsWithEpg++

            val normChannelName = normalize(channel.name)
            val isSportsChannel = isSportsOrOpenBroadcastChannel(channel)

            for (prog in programmes) {
                val isLive = prog.isLiveAt(nowMs)
                val isUpcoming = !isLive && prog.startMs > nowMs && prog.startMs <= (nowMs + UPCOMING_WINDOW_MS)
                if (!isLive && !isUpcoming) continue

                val normTitle = normalize(prog.title)
                val normDesc = normalize(prog.description)
                val normCat = normalize(prog.category)

                var relevance = 0

                if (intent.canonicalTeams.isNotEmpty()) {
                    // Busca específica por time(s): o time precisa aparecer no título, descrição ou nome do canal.
                    var matchedTeamCount = 0
                    for (teamCanonical in intent.canonicalTeams) {
                        val aliases = TEAM_ALIASES.firstOrNull { it.canonical == teamCanonical }?.aliases
                            ?: listOf(normalize(teamCanonical))
                        val hitTitle = aliases.any { containsWholeTerm(normTitle, it) }
                        val hitDesc = !hitTitle && aliases.any { containsWholeTerm(normDesc, it) }
                        val hitChannel = !hitTitle && !hitDesc && aliases.any { containsWholeTerm(normChannelName, it) }
                        if (hitTitle) {
                            matchedTeamCount++
                            relevance += 120
                        } else if (hitDesc) {
                            matchedTeamCount++
                            relevance += 80
                        } else if (hitChannel) {
                            matchedTeamCount++
                            relevance += 50
                        }
                    }
                    if (matchedTeamCount == 0) continue
                    // Bônus se ambos os times de um confronto ("Flamengo x Palmeiras") aparecem no programa
                    if (intent.canonicalTeams.size >= 2 && matchedTeamCount >= 2) {
                        relevance += 100
                    }
                    if (isSportsChannel) relevance += 15
                } else if (hasSpecificTerms && !intent.isSportsIntent) {
                    // Busca genérica por programa/palavra-chave no EPG (ex: "Jornal Nacional", "BBB")
                    var hits = 0
                    for (term in intent.searchTerms) {
                        if (containsWholeTerm(normTitle, term)) {
                            hits++
                            relevance += 90
                        } else if (containsWholeTerm(normDesc, term)) {
                            hits++
                            relevance += 50
                        } else if (containsWholeTerm(normChannelName, term)) {
                            hits++
                            relevance += 35
                        }
                    }
                    if (hits == 0) continue
                } else {
                    // Busca ampla de esportes/jogos passando agora ("jogos passando agora", "futebol agora")
                    val combined = "$normTitle $normDesc $normCat"
                    val hasSpecificSportsTerm = intent.searchTerms.any { term ->
                        containsWholeTerm(normTitle, term) || containsWholeTerm(normDesc, term)
                    }
                    val hasSportsMarker = BROAD_SPORTS_EPG_MARKERS.any { marker ->
                        combined.contains(marker)
                    }
                    if (!isSportsChannel && !hasSportsMarker && !hasSpecificSportsTerm) continue
                    if (!hasSportsMarker && !hasSpecificSportsTerm && !isLive) continue

                    relevance = when {
                        hasSpecificSportsTerm -> 100
                        combined.contains(" x ") || combined.contains(" vs ") -> 95
                        hasSportsMarker && isSportsChannel -> 80
                        hasSportsMarker -> 65
                        else -> 40
                    }
                }

                val match = CopilotEpgChannelMatch(
                    item = channel,
                    programTitle = prog.title.trim(),
                    programDescription = prog.description.trim(),
                    timeRange = prog.formattedRange(),
                    isLiveNow = isLive,
                    progressPercent = if (isLive) prog.progressAt(nowMs) else 0f,
                    remainingMinutes = if (isLive) prog.remainingMinutesAt(nowMs) else 0,
                    startMs = prog.startMs
                )
                rawMatches.add(
                    ScoredMatch(
                        match = match,
                        relevance = relevance,
                        quality = qualityScore(channel.name),
                        dedupKey = "${canonicalChannelFamily(channel.name)}|${normTitle}|${prog.startMs}"
                    )
                )
            }
        }

        // Deduplica variantes da mesma família de canal exibindo o mesmo programa (mantendo maior qualidade/relevância)
        val deduped = rawMatches
            .sortedWith(
                compareByDescending<ScoredMatch> { it.match.isLiveNow }
                    .thenByDescending { it.relevance }
                    .thenBy { if (it.match.isLiveNow) 0L else it.match.startMs }
                    .thenByDescending { it.quality }
            )
            .distinctBy { it.dedupKey }

        val liveMatches = deduped
            .filter { it.match.isLiveNow }
            .distinctBy { it.match.item.url }
            .take(maxLiveResults)
            .map { it.match }

        val upcomingMatches = deduped
            .filter { !it.match.isLiveNow }
            .distinctBy { "${it.match.item.url}|${it.match.startMs}" }
            .take(maxUpcomingResults)
            .map { it.match }

        val detectedDisplay = if (intent.canonicalTeams.isNotEmpty()) {
            intent.canonicalTeams
        } else {
            intent.searchTerms.take(4)
        }

        return CopilotEpgSearchResult(
            detectedTerms = detectedDisplay,
            isLiveNowIntent = intent.isLiveNowIntent,
            isSportsOrEpgIntent = intent.isSportsIntent || intent.isEpgIntent,
            liveNowMatches = liveMatches,
            upcomingMatches = upcomingMatches,
            scannedChannelsCount = liveChannels.size,
            channelsWithEpgCount = channelsWithEpg
        )
    }

    /**
     * Formata o resultado real do EPG para injeção no prompt do Gemini.
     */
    fun buildEpgPromptContext(
        result: CopilotEpgSearchResult?,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        if (result == null) return ""
        val nowTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nowMs))
        val sb = StringBuilder()
        sb.appendLine("\n=== PROGRAMAÇÃO EPG REAL CONSULTADA AGORA ($nowTime) ===")
        if (result.detectedTerms.isNotEmpty()) {
            sb.appendLine("Termos/Times detectados: ${result.detectedTerms.joinToString(", ")}")
        }
        sb.appendLine("Canais verificados: ${result.scannedChannelsCount} (${result.channelsWithEpgCount} com grade EPG ativa)")

        if (result.liveNowMatches.isNotEmpty()) {
            sb.appendLine("\n[NO AR AGORA — TRANSMISSÕES CONFIRMADAS NO EPG]:")
            result.liveNowMatches.forEach { m ->
                val quality = m.qualityBadge?.let { " [$it]" }.orEmpty()
                sb.appendLine("- ${m.item.name}$quality (${m.item.category}) → \"${m.programTitle}\" (${m.timeRange}, restam ${m.remainingMinutes} min)")
            }
        }

        if (result.upcomingMatches.isNotEmpty()) {
            sb.appendLine("\n[EM BREVE NA GRADE EPG (próximas 18h)]:")
            result.upcomingMatches.forEach { m ->
                val quality = m.qualityBadge?.let { " [$it]" }.orEmpty()
                sb.appendLine("- ${m.item.name}$quality (${m.item.category}) → \"${m.programTitle}\" (${m.timeRange})")
            }
        }

        if (!result.hasMatches) {
            if (result.channelsWithEpgCount > 0) {
                sb.appendLine("\nNenhuma transmissão com esse termo/time foi encontrada passando agora ou nas próximas 18h na grade EPG consultada.")
            } else {
                sb.appendLine("\nA grade EPG dos canais ainda não retornou programação detalhada para este servidor neste momento.")
            }
        }
        return sb.toString()
    }

    private data class ScoredMatch(
        val match: CopilotEpgChannelMatch,
        val relevance: Int,
        val quality: Int,
        val dedupKey: String
    )
}
