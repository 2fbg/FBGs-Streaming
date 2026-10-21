package com.example.data.utils

import com.example.data.model.ServerProfile
import java.net.URI
import java.util.UUID

/**
 * Resultado da extração de servidor de texto bruto.
 */
data class ParsedServer(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String? = null,
    val password: String? = null,
    val fullUrl: String? = null,
    val rawText: String = ""
)

/**
 * Parser inteligente com Regex para mensagens no formato WhatsApp contendo múltiplos servidores IPTV.
 */
object ServerTextParser {

    private val URL_REGEX = Regex("""(?i)\b(https?://[^\s<>"'{}|\\^`\[\]]+)""")
    private val BASE_URL_REGEX = Regex("""^(https?://[^/?#]+)""", RegexOption.IGNORE_CASE)
    private val USERNAME_REGEX = Regex("""[?&](?:username|user|u)=([^&]+)""", RegexOption.IGNORE_CASE)
    private val PASSWORD_REGEX = Regex("""[?&](?:password|pass|p)=([^&]+)""", RegexOption.IGNORE_CASE)
    private val GLOBAL_USER_REGEX = Regex("""(?i)\*?✅?\s*(?:Usu[aá]rio|User|Login)\s*:\*?\s*([a-zA-Z0-9_\-\.]+)""")
    private val GLOBAL_PASS_REGEX = Regex("""(?i)\*?✅?\s*(?:Senha|Pass(?:word)?)\s*:\*?\s*([a-zA-Z0-9_\-\.]+)""")
    private val EMOJI_CLEANER = Regex("""[🟢🔴🔵⚪🟠🟣🟤🟡⚫✅🔰✔️🌟📱📺🌐🆔💻🔗\*]""")

    /**
     * Extrai servidores a partir de texto colado no formato WhatsApp ou listas de URLs.
     */
    fun parse(text: String): List<ParsedServer> {
        val results = mutableListOf<ParsedServer>()
        val seenUrls = mutableSetOf<String>()

        // Extrai credenciais globais da mensagem caso existam
        val globalUser = GLOBAL_USER_REGEX.find(text)?.groupValues?.getOrNull(1)
        val globalPass = GLOBAL_PASS_REGEX.find(text)?.groupValues?.getOrNull(1)

        val lines = text.split("\n", "\r")
        var pendingLabel = ""
        var counter = 1

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            val urlMatch = URL_REGEX.find(trimmedLine)
            if (urlMatch == null) {
                // Guarda linha potencial de título/nome de servidor (ex: "🟤 *Link MULTT TV (M3U):*")
                if (trimmedLine.contains("Link", ignoreCase = true) ||
                    trimmedLine.contains("TV", ignoreCase = true) ||
                    trimmedLine.endsWith(":") || trimmedLine.endsWith(":*")) {
                    pendingLabel = trimmedLine
                }
                continue
            }

            val fullUrl = urlMatch.value
            val baseMatch = BASE_URL_REGEX.find(fullUrl) ?: continue
            val baseUrl = baseMatch.value.trim().removeSuffix("/")

            val normalizedBaseKey = baseUrl.lowercase()
            if (seenUrls.contains(normalizedBaseKey)) {
                pendingLabel = ""
                continue
            }

            // Ignora links que não são servidores IPTV (ex: lojas de apps, painéis de checkout, encurtadores)
            if (isExcludedDomain(normalizedBaseKey)) {
                pendingLabel = ""
                continue
            }

            // Extrai credenciais se disponíveis na query string ou usa as globais da mensagem
            val username = USERNAME_REGEX.find(fullUrl)?.groupValues?.getOrNull(1) ?: globalUser
            val password = PASSWORD_REGEX.find(fullUrl)?.groupValues?.getOrNull(1) ?: globalPass

            // Extrai o nome do servidor antes da URL ou da linha anterior pendente
            val labelToUse = if (trimmedLine.startsWith("http", ignoreCase = true) && pendingLabel.isNotBlank()) {
                pendingLabel
            } else {
                trimmedLine
            }
            pendingLabel = ""

            var extractedName = extractServerName(labelToUse, fullUrl, baseUrl)
            if (extractedName.isBlank()) {
                extractedName = "Servidor $counter"
            }

            val serverId = "srv_${UUID.randomUUID().toString().take(8)}"
            results.add(
                ParsedServer(
                    id = serverId,
                    name = extractedName,
                    baseUrl = baseUrl,
                    username = username,
                    password = password,
                    fullUrl = fullUrl,
                    rawText = trimmedLine
                )
            )

            seenUrls.add(normalizedBaseKey)
            counter++
        }

        return results
    }

    private fun extractServerName(line: String, fullUrl: String, baseUrl: String): String {
        // Tenta pegar a parte antes da URL
        val beforeUrl = if (line.contains(fullUrl)) line.substringBefore(fullUrl) else line
        var cleaned = EMOJI_CLEANER.replace(beforeUrl, "").trim()

        // Remove prefixos conhecidos
        cleaned = cleaned
            .replace(Regex("""(?i)\blink\s*"""), "")
            .replace(Regex("""(?i)\(m3u\)\s*"""), "")
            .replace(Regex("""(?i)\bm3u\b"""), "")
            .replace(Regex("""(?i)url\s*xciptv\s*servidores"""), "")
            .replace(Regex("""(?i)url\s*iptv\s*smarters"""), "")
            .replace(Regex("""(?i)códigos\s*assist\s*plus"""), "")
            .replace(":", "")
            .replace("-", "")
            .replace("_", "")
            .trim()

        val lowerCleaned = cleaned.lowercase()
        val lowerBase = baseUrl.lowercase()

        // Normalização de nomes conhecidos de servidores IPTV
        return when {
            lowerCleaned.contains("vlog") || lowerBase.contains("myopbx") -> "VLOG"
            lowerCleaned.contains("lub") || lowerBase.contains("pottermax") || lowerBase.contains("alfatecloan") -> "LUB TV"
            lowerCleaned.contains("cinelon") || lowerBase.contains("coliseuop") -> "CINELON21"
            lowerCleaned.contains("tannix") || lowerBase.contains("poptvcdn") || lowerBase.contains("gip26") -> "TANNIX"
            lowerCleaned.contains("cb6000") || lowerCleaned.contains("cb6") || lowerBase.contains("caterlune") -> "CB6000"
            lowerCleaned.contains("mk21") || lowerBase.contains("vivoxi") || lowerBase.contains("tigerouro") -> "MK21 PRÓ"
            lowerCleaned.contains("multt tv") || lowerBase.contains("dali-as") || lowerBase.contains("hll4") -> "MULTT TV"
            lowerCleaned.contains("new tv") || lowerBase.contains("cp2026") -> "NEW TV"
            lowerCleaned.contains("cinevo") || lowerBase.contains("antaresfusion") -> "CINEVO"
            lowerCleaned.contains("multbox") || lowerCleaned.contains("multt box") || lowerBase.contains("cdnconn") -> "MULTT BOX"
            cleaned.isNotEmpty() -> cleaned
            else -> {
                try {
                    val uri = URI(baseUrl)
                    val host = uri.host ?: ""
                    host.removePrefix("www.")
                        .substringBefore(".")
                        .uppercase()
                } catch (e: Exception) {
                    "Servidor"
                }
            }
        }
    }

    private fun isExcludedDomain(domain: String): Boolean {
        val exclusions = listOf(
            "play.google.com", "apple.com", "playstore", "is.gd", "t.ly", "bit.ly",
            "da.gd", "wa.me", "api.whatsapp.com", "t.me", "youtube.com",
            "painelmk21.top", "assistpluspainel.com", "playsimpainel.com",
            "magicplayerpainel.com", "boxplayer.app", "webplay.topibo.uk",
            "vocine.appflix.top", "ibopro.xyz", "addmyplaylist.com", "tinu.be",
            "aftvnews.com"
        )
        return exclusions.any { domain.contains(it) }
    }

    /**
     * Converte lista de ParsedServer em entidades ServerProfile prontas para persistência.
     */
    fun toServerProfiles(
        parsedList: List<ParsedServer>,
        fallbackUsername: String? = null,
        fallbackPassword: String? = null
    ): List<ServerProfile> {
        return parsedList.map { parsed ->
            ServerProfile(
                id = parsed.id,
                name = parsed.name,
                baseUrl = parsed.baseUrl,
                username = parsed.username ?: fallbackUsername,
                password = parsed.password ?: fallbackPassword,
                isActive = true
            )
        }
    }
}
