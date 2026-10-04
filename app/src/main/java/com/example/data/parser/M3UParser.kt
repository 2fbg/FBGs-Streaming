package com.example.data.parser

import com.example.data.model.PlaylistItem
import okio.buffer
import okio.source
import java.io.InputStream
import java.net.URI

object M3UParser {

    /**
     * Parse an M3U playlist from an input stream using Okio for optimized I/O buffering.
     * Extracts tags such as tvg-logo, group-title, tvg-name, and applies content-type heuristics.
     */
    fun parse(inputStream: InputStream, playlistSource: String, onProgress: (Int) -> Unit): List<PlaylistItem> {
        val source = inputStream.source().buffer()
        val items = mutableListOf<PlaylistItem>()

        var currentMetaData: String? = null
        var currentGroup: String? = null
        var processedLines = 0
        var firstLine: String? = null

        try {
            while (true) {
                val line = source.readUtf8Line() ?: break
                var currentLine = line.trim()
                processedLines++

                // Check and strip UTF-8 BOM if present on the very first read lines or general lines
                if (currentLine.startsWith("\uFEFF")) {
                    currentLine = currentLine.substring(1).trim()
                }

                if (currentLine.isEmpty()) continue

                if (firstLine == null) {
                    firstLine = currentLine
                }

                if (currentLine.startsWith("#EXTM3U", ignoreCase = true)) {
                    // Ignore header
                    continue
                } else if (currentLine.startsWith("#EXTINF", ignoreCase = true)) {
                    currentMetaData = currentLine
                } else if (currentLine.startsWith("#EXTGRP:", ignoreCase = true)) {
                    currentGroup = currentLine.substring(8).trim()
                } else if (!currentLine.startsWith("#")) {
                    // This is a stream URL line!
                    val sanitizedUrl = sanitizeStreamUrl(currentLine)
                    if (sanitizedUrl != null) {
                        if (currentMetaData != null) {
                            val item = parseItem(currentMetaData, sanitizedUrl, playlistSource, currentGroup)
                            items.add(item)
                            currentMetaData = null
                            currentGroup = null
                        } else if (isValidUrl(sanitizedUrl)) {
                            // Fallback: parse plain URL without metadata
                            val item = parseUrlOnly(sanitizedUrl, playlistSource)
                            items.add(item)
                        }
                    } else {
                        currentMetaData = null
                        currentGroup = null
                    }

                    // Emitting progress at regular intervals
                    if (items.size % 400 == 0) {
                        val progress = (items.size * 100 / (items.size + 1000)).coerceAtMost(99)
                        onProgress(progress)
                    }
                }
            }
        } finally {
            try {
                source.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
        }

        onProgress(100)

        // If no items were parsed, diagnose why (e.g. server returned an HTML failure portal)
        if (items.isEmpty()) {
            val diagnosis = diagnoseContent(firstLine)
            if (diagnosis != null) {
                throw Exception(diagnosis)
            }
        }

        return items
    }

    /**
     * Sanitizes stream URLs to only permit safe streaming protocols and reject local/file attacks.
     */
    fun sanitizeStreamUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        val isAllowedScheme = lower.startsWith("http://") || 
                              lower.startsWith("https://") || 
                              lower.startsWith("rtmp://") || 
                              lower.startsWith("rtsp://") || 
                              lower.startsWith("mms://")
        return if (isAllowedScheme) trimmed else null
    }

    private fun sanitizeLogoUrl(rawLogo: String?): String? {
        if (rawLogo.isNullOrBlank()) return null
        val trimmed = rawLogo.trim()
        val lower = trimmed.lowercase()
        return if (lower.startsWith("http://") || lower.startsWith("https://")) trimmed else null
    }

    private fun isValidUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") || 
               lower.startsWith("https://") || 
               lower.startsWith("rtmp://") || 
               lower.startsWith("rtsp://") || 
               lower.startsWith("mms://")
    }

    private fun parseUrlOnly(streamUrl: String, playlistSource: String): PlaylistItem {
        val finalUrl = streamUrl
        val uri = try {
            URI(finalUrl)
        } catch (e: Exception) {
            null
        }
        val path = uri?.path ?: finalUrl
        val lastSegment = path.substringAfterLast('/')
        val displayName = if (lastSegment.isNotEmpty()) {
            lastSegment.substringBeforeLast('.')
        } else {
            "Canal Manual"
        }
        val category = "Canais Gerais"
        val contentType = determineType(displayName, category, finalUrl)
        return PlaylistItem(
            name = displayName.ifEmpty { "Canal Manual" },
            url = finalUrl,
            logoUrl = null,
            category = category,
            contentType = contentType.name,
            isAdult = isAdultContent(displayName, category),
            playlistSource = playlistSource
        )
    }

    private fun parseItem(metadataLine: String, streamUrl: String, playlistSource: String, fallbackGroup: String? = null): PlaylistItem {
        // Extract display name (last part of metadata after comma)
        val commaIndex = metadataLine.lastIndexOf(',')
        var displayName = if (commaIndex != -1 && commaIndex < metadataLine.length - 1) {
            metadataLine.substring(commaIndex + 1).trim()
        } else {
            "Untitled Stream"
        }

        // Extract attributes case-insensitively
        val logoUrl = extractAttribute(metadataLine, "tvg-logo") ?: extractAttribute(metadataLine, "logo")
        var category = extractAttribute(metadataLine, "group-title") ?: fallbackGroup ?: "Canais Gerais"
        
        // Sanitize category
        if (category.trim().isEmpty()) {
            category = fallbackGroup ?: "Canais Gerais"
        }

        val tvgName = extractAttribute(metadataLine, "tvg-name")
        if (tvgName != null && displayName == "Untitled Stream") {
            displayName = tvgName
        }

        val finalUrl = streamUrl

        // Determine content-type (Ao Vivo, Filmes, Séries)
        val contentType = determineType(displayName, category, finalUrl)

        // Check for adult content
        val isAdult = isAdultContent(displayName, category)

        return PlaylistItem(
            name = displayName,
            url = finalUrl,
            logoUrl = sanitizeLogoUrl(logoUrl),
            category = category,
            contentType = contentType.name,
            isAdult = isAdult,
            playlistSource = playlistSource
        )
    }

    private fun extractAttribute(line: String, attrName: String): String? {
        val lineLower = line.lowercase()
        val attrNameLower = attrName.lowercase()
        
        val target = "$attrNameLower=\""
        var index = lineLower.indexOf(target)
        if (index != -1) {
            val start = index + target.length
            val end = line.indexOf("\"", start)
            if (end != -1) {
                return line.substring(start, end)
            }
        }
        
        // Fallback without quotes (e.g. tvg-logo=http://url)
        val targetFallback = "$attrNameLower="
        index = lineLower.indexOf(targetFallback)
        if (index != -1) {
            val start = index + targetFallback.length
            var end = line.indexOf(" ", start)
            if (end == -1) {
                end = line.indexOf(",", start)
            }
            if (end == -1) {
                end = line.length
            }
            if (end > start) {
                return line.substring(start, end).replace("\"", "").trim()
            }
        }
        return null
    }

    private fun determineType(name: String, category: String, url: String): com.example.data.model.ContentType {
        val uppercaseName = name.uppercase()
        val uppercaseCategory = category.uppercase()
        val uppercaseUrl = url.uppercase()

        // 1. Explicit URL check first for Xtream Codes patterns
        if (uppercaseUrl.contains("/LIVE/")) {
            return com.example.data.model.ContentType.LIVE
        }
        if (uppercaseUrl.contains("/MOVIE/")) {
            return com.example.data.model.ContentType.MOVIE
        }
        if (uppercaseUrl.contains("/SERIES/")) {
            return com.example.data.model.ContentType.SERIES
        }

        // 2. Explicit Live TV categories - channels like 24h, aberta, esportes, noticias
        val isLiveCategory = 
            uppercaseCategory.startsWith("CANAIS") ||
            uppercaseCategory.startsWith("CANAL") ||
            uppercaseCategory.contains("CANAIS |") ||
            uppercaseCategory.contains("CANAL |") ||
            uppercaseCategory.contains("CANAIS:") ||
            uppercaseCategory.contains("CANAL:") ||
            uppercaseCategory.contains("24H") ||
            uppercaseCategory.contains("24 HORAS") ||
            uppercaseCategory.contains("AO VIVO") ||
            uppercaseCategory.contains("AOVIVO") ||
            uppercaseCategory.contains("TV ABERTA") ||
            uppercaseCategory.contains("ABERTOS") ||
            uppercaseCategory.contains("NOTICIAS") ||
            uppercaseCategory.contains("ESPORTES") ||
            uppercaseCategory.contains("PREMIERE") ||
            uppercaseCategory.contains("COMBATE") ||
            uppercaseCategory.contains("DAZN") ||
            uppercaseCategory.contains("TELECINE") ||
            uppercaseCategory.contains("CINESKY") ||
            uppercaseCategory.contains("DISCOVERY") ||
            uppercaseCategory.contains("CANAIS 4K")

        if (isLiveCategory) {
            val isExplicitVodFile = (uppercaseUrl.endsWith(".MP4") || uppercaseUrl.endsWith(".MKV")) && !uppercaseUrl.contains("/LIVE/")
            if (!isExplicitVodFile) {
                return com.example.data.model.ContentType.LIVE
            }
        }

        // 3. VOD file extensions
        val isVodExtension = uppercaseUrl.endsWith(".MP4") || uppercaseUrl.endsWith(".MKV") || uppercaseUrl.endsWith(".AVI")

        // 4. Series detection (seasons, episodes, S01E01, providers)
        val seriesRegex = Regex("(?i)(?:\\b(?:s\\d{1,3}\\s*)?(?:e|ep|ep\\.|episodio|cap|cap\\.|capitulo)\\s*\\d{1,4}\\b|\\b\\d{1,3}x\\d{1,4}\\b|\\bs\\d{1,3}e\\d{1,4}\\b|\\bt\\d{1,3}e\\d{1,4}\\b|\\btemp\\.\\s*\\d+\\s*ep\\.\\s*\\d+\\b|\\btemporada\\s*\\d+\\b)")
        val hasSeriesPattern = seriesRegex.containsMatchIn(name) ||
            uppercaseName.contains("TEMPORADA") || uppercaseName.contains("TEMP.") ||
            uppercaseName.contains("CAPITULO") || uppercaseName.contains("CAPÍTULO") ||
            uppercaseName.contains("EPISODIO") || uppercaseName.contains("EPISÓDIO")

        val isSeriesCategory = 
            uppercaseCategory.startsWith("SERIES |") ||
            uppercaseCategory.startsWith("SÉRIES |") ||
            uppercaseCategory.startsWith("SERIES:") ||
            uppercaseCategory.startsWith("SÉRIES:") ||
            uppercaseCategory.startsWith("SERIE |") ||
            uppercaseCategory.startsWith("SÉRIE |") ||
            uppercaseCategory.contains("NETFLIX") ||
            uppercaseCategory.contains("AMAZON") ||
            uppercaseCategory.contains("PRIME VIDEO") ||
            uppercaseCategory.contains("HBO") ||
            uppercaseCategory.contains("MAX") ||
            uppercaseCategory.contains("DISNEY") ||
            uppercaseCategory.contains("APPLE TV") ||
            uppercaseCategory.contains("PARAMOUNT") ||
            uppercaseCategory.contains("GLOBOPLAY") ||
            uppercaseCategory.contains("OUTRAS PRODUTORAS") ||
            uppercaseCategory.contains("SERIADOS") ||
            uppercaseCategory.contains("NOVELAS") ||
            uppercaseCategory.contains("ANIMES") ||
            uppercaseCategory.contains("DORAMAS") ||
            uppercaseCategory.contains("MINISSERIE") ||
            uppercaseCategory.contains("MINISSÉRIE")

        if (isSeriesCategory || hasSeriesPattern) {
            return com.example.data.model.ContentType.SERIES
        }

        // 5. Movie detection
        val isMovieCategory = 
            uppercaseCategory.startsWith("FILMES |") ||
            uppercaseCategory.startsWith("FILME |") ||
            uppercaseCategory.startsWith("FILMES:") ||
            uppercaseCategory.startsWith("FILME:") ||
            uppercaseCategory.startsWith("VOD |") ||
            uppercaseCategory.startsWith("VOD:") ||
            uppercaseCategory.startsWith("CINEMA |") ||
            uppercaseCategory.contains("LANCAMENTOS") ||
            uppercaseCategory.contains("LANÇAMENTOS") ||
            uppercaseCategory.contains("FILMES 4K") ||
            uppercaseCategory.contains("FILMES DUBLADOS") ||
            uppercaseCategory.contains("FILMES LEGENDADOS") ||
            uppercaseCategory.contains("FILMES ACAO") ||
            uppercaseCategory.contains("FILMES COMEDIA")

        if (isMovieCategory || isVodExtension) {
            return com.example.data.model.ContentType.MOVIE
        }

        // 6. Explicit streaming extensions or default is Ao Vivo
        return com.example.data.model.ContentType.LIVE
    }

    private fun isAdultContent(name: String, category: String): Boolean {
        val pattern = listOf(
            "18+", "ADULTO", "ADULT", "XXX", "SEXY", "PLAYBOY", "PENTHOUSE", "VENUS", "HOT ", "HUSTLER", "FORBIDDEN", "FORA DA LEI", "S0X"
        )
        val upperName = name.uppercase()
        val upperCategory = category.uppercase()
        return pattern.any { upperName.contains(it) || upperCategory.contains(it) }
    }

    private fun diagnoseContent(firstLine: String?): String? {
        if (firstLine == null) return null
        val lower = firstLine.lowercase()
        
        // HTML check
        if (lower.startsWith("<html") || lower.startsWith("<!doc") || lower.contains("<html>")) {
            return "O servidor retornou uma página HTML ao invés da lista. Verifique se o usuário/senha estão corretos ou se o servidor está funcionando."
        }
        
        // JSON check
        if (lower.startsWith("{") || lower.startsWith("[")) {
            if (lower.contains("message") || lower.contains("error") || lower.contains("status")) {
                return "O servidor retornou um erro em formato JSON. Verifique seus dados de acesso."
            }
        }
        
        // Common plain text auth errors
        if (lower.contains("invalid username or password") || 
            lower.contains("authorization failed") || 
            lower.contains("auth failed") || 
            lower.contains("invalid credentials") ||
            lower.contains("usuario invalido") ||
            lower.contains("senha incorreta") ||
            lower.contains("credenciais incorretas") ||
            lower.contains("acesso negado") ||
            lower.contains("unauthorized")) {
            return "Usuário ou senha inválidos no servidor contratado."
        }
        
        if (lower.contains("account expired") || 
            lower.contains("expired") || 
            lower.contains("vencido") || 
            lower.contains("expirou") || 
            lower.contains("vencida")) {
            return "Sua conta de IPTV expirou ou está inativa no servidor."
        }
        
        if (lower.contains("limit reached") || 
            lower.contains("too many connections") || 
            lower.contains("limite de conex") || 
            lower.contains("max connections")) {
            return "Limite de conexões simultâneas atingido no servidor."
        }
        
        return null
    }
}
