package com.example.utils

/**
 * Sanitiza URLs e textos de diagnóstico para impedir exposição acidental de
 * credenciais IPTV (usuário, senha, tokens) ou chaves de licença em logs.
 */
object LogSanitizer {
    private val basicAuthRegex = Regex("(https?://)[^/@\\s:]+:[^/@\\s]+@", RegexOption.IGNORE_CASE)
    private val queryCredRegex = Regex(
        "([?&](?:username|user|password|pass|token|auth|api[_-]?key)=)[^&#\\s]*",
        RegexOption.IGNORE_CASE
    )
    private val xtreamPathRegex = Regex(
        "/(live|movie|series)/[^/\\s?]+/[^/\\s?]+",
        RegexOption.IGNORE_CASE
    )
    private val licenseTokenRegex = Regex("MKL1\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")

    fun sanitizeUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return rawUrl
        return sanitizeText(rawUrl)
    }

    fun sanitizeText(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        return text
            .replace(basicAuthRegex, "$1[REDACTED]@")
            .replace(queryCredRegex, "$1[REDACTED]")
            .replace(xtreamPathRegex, "/$1/[REDACTED]/[REDACTED]")
            .replace(licenseTokenRegex, "MKL1.[REDACTED]")
    }
}
