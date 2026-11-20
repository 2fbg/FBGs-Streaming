package com.example.utils

/**
 * Sanitiza URLs e textos de diagnóstico para impedir exposição acidental de
 * credenciais IPTV (usuário, senha, tokens) ou chaves de licença em logs.
 */
object LogSanitizer {
    private val basicAuthRegex = Regex("(https?://)[^/@\\s:]+:[^/@\\s]+@", RegexOption.IGNORE_CASE)
    private val queryCredRegex = Regex(
        "((?:^|[?&\\s,(])(?:username|user|password|pass|pwd|token|access[_-]?token|refresh[_-]?token|auth|api[_-]?key|pin|secret|cookie|signature|sig|key)=)[^&#\\s\"',;}\\]]*",
        RegexOption.IGNORE_CASE
    )
    private val xtreamPathRegex = Regex(
        "/(live|movie|series)/[^/\\s?\"']+/[^/\\s?\"']+",
        RegexOption.IGNORE_CASE
    )
    private val licenseTokenRegex = Regex("MKL1\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
    private val bearerRegex = Regex("\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]{6,}", RegexOption.IGNORE_CASE)
    private val headerSecretRegex = Regex(
        "\\b(Authorization|Cookie|Set-Cookie|X-Api-Key)\\s*[:=]\\s*[^\\r\\n;,\"\\}]+",
        RegexOption.IGNORE_CASE
    )
    private val jsonSecretRegex = Regex(
        "(\"(?:password|pass|pin|token|access_token|refresh_token|authorization|cookie|secret|apiKey|api_key)\"\\s*:\\s*)\"[^\"]*\"",
        RegexOption.IGNORE_CASE
    )

    fun sanitizeUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return rawUrl
        return sanitizeText(rawUrl)
    }

    fun extractSafeHost(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank()) return "unknown"
        return try {
            val uri = java.net.URI(rawUrl.trim())
            uri.host?.takeIf { it.isNotBlank() } ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    fun sanitizeText(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        if (text.contains("#EXTM3U") || (text.contains("#EXTINF") && text.length > 256)) {
            return "[REDACTED_PLAYLIST len=${text.length}]"
        }
        return text
            .replace(basicAuthRegex, "$1[REDACTED]@")
            .replace(queryCredRegex, "$1[REDACTED]")
            .replace(xtreamPathRegex, "/$1/[REDACTED]/[REDACTED]")
            .replace(licenseTokenRegex, "MKL1.[REDACTED]")
            .replace(bearerRegex, "$1 [REDACTED]")
            .replace(headerSecretRegex, "$1: [REDACTED]")
            .replace(jsonSecretRegex, "$1\"[REDACTED]\"")
    }
}
