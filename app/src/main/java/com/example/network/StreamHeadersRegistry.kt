package com.example.network

import java.util.concurrent.ConcurrentHashMap

data class StreamHeaders(
    val userAgent: String? = null,
    val referrer: String? = null
)

/**
 * Registro em memória para cabeçalhos HTTP opcionais (#EXTVLCOPT: http-user-agent / http-referrer)
 * sem alterar o schema Room existente.
 */
object StreamHeadersRegistry {
    private const val MAX_ENTRIES = 4096
    private val entries = ConcurrentHashMap<String, StreamHeaders>()

    fun register(url: String, userAgent: String?, referrer: String?) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return
        val ua = userAgent?.trim()?.takeIf { it.isNotEmpty() }
        val ref = referrer?.trim()?.takeIf { it.isNotEmpty() }
        if (ua == null && ref == null) return

        if (entries.size >= MAX_ENTRIES) {
            entries.clear()
        }
        val headers = StreamHeaders(userAgent = ua, referrer = ref)
        entries[cleanUrl] = headers
        val baseKey = stripFragment(cleanUrl)
        if (baseKey != cleanUrl) {
            entries[baseKey] = headers
        }
    }

    fun get(url: String?): StreamHeaders? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        return entries[trimmed] ?: entries[stripFragment(trimmed)]
    }

    fun clear() {
        entries.clear()
    }

    private fun stripFragment(url: String): String {
        val idx = url.indexOf('#')
        return if (idx > 0) url.substring(0, idx) else url
    }
}
