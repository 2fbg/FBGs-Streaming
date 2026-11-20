package com.example.utils

import android.content.Context
import android.os.SystemClock
import com.example.BuildConfig
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Logger centralizado estruturado (JSONL) para Android com:
 * - schemaVersion, timestamp UTC + relógio monotônico, sessionId anônimo;
 * - níveis OFF, NORMAL, DIAGNOSTICO e TRACE (expiração automática de 30–60 min);
 * - buffer circular em memória + gravação em lote com rotação de arquivos internos;
 * - redaction estrita ANTES da persistência e ANTES da exportação;
 * - isolamento defensivo: nenhuma falha do logger interrompe o aplicativo.
 */
object ObservabilityLogger {

    const val SCHEMA_VERSION = 1
    private const val PREF_NAME = "mk21_observability_prefs"
    private const val KEY_LEVEL = "obs_level"
    private const val KEY_TRACE_UNTIL = "obs_trace_until_ms"

    private const val LOGS_DIR = "logs"
    private const val CURRENT_LOG_FILE = "mk21-observability.jsonl"
    private const val ROTATED_LOG_FILE = "mk21-observability.1.jsonl"
    private const val MAX_FILE_BYTES = 256 * 1024L // 256 KB por arquivo (máx ~512 KB total)
    private const val MAX_MEMORY_EVENTS = 600
    private const val DEFAULT_TRACE_TTL_MS = 45L * 60L * 1000L
    private const val MIN_TRACE_TTL_MS = 30L * 60L * 1000L
    private const val MAX_TRACE_TTL_MS = 60L * 60L * 1000L

    private val SENSITIVE_KEY_REGEX = Regex(
        "^(pass(word)?|pwd|pin|token|access[_-]?token|refresh[_-]?token|auth(orization)?|cookie|set-cookie|secret|api[_-]?key|credential|cred|session[_-]?token|private[_-]?key|license[_-]?key)$",
        RegexOption.IGNORE_CASE
    )

    enum class Level(val rank: Int) {
        OFF(0),
        NORMAL(1),
        DIAGNOSTICO(2),
        TRACE(3)
    }

    enum class Severity(val minRank: Int) {
        DEBUG(3),
        INFO(1),
        WARN(1),
        ERROR(1)
    }

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var currentLevel: Level = Level.NORMAL

    @Volatile
    private var traceExpiresAtMs: Long = 0L

    @Volatile
    private var currentScreen: String = "boot"

    @Volatile
    internal var wallClockProvider: () -> Long = { System.currentTimeMillis() }

    @Volatile
    internal var monoClockProvider: () -> Long = {
        try {
            SystemClock.elapsedRealtime()
        } catch (_: Throwable) {
            System.nanoTime() / 1_000_000L
        }
    }

    private val sessionId: String = "ses_" + UUID.randomUUID().toString().replace("-", "").take(16)
    private val memoryRing = ArrayDeque<String>(MAX_MEMORY_EVENTS)
    private val pendingDiskBatch = ArrayList<String>(64)
    private val ringLock = Any()
    private val activeTimers = ConcurrentHashMap<String, Pair<Long, String>>()
    private val flushScheduled = AtomicBoolean(false)
    private val ioExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "mk21-observability-io").apply { isDaemon = true }
    }

    fun init(context: Context) {
        try {
            val ctx = context.applicationContext ?: context
            appContext = ctx
            val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val savedName = prefs.getString(KEY_LEVEL, Level.NORMAL.name) ?: Level.NORMAL.name
            currentLevel = runCatching { Level.valueOf(savedName) }.getOrDefault(Level.NORMAL)
            traceExpiresAtMs = prefs.getLong(KEY_TRACE_UNTIL, 0L)
            enforceTraceExpiration()
            logEvent(
                event = "process_start",
                phase = "boot",
                screen = "boot",
                meta = mapOf("version" to BuildConfig.VERSION_NAME, "build" to BuildConfig.VERSION_CODE)
            )
        } catch (_: Throwable) {
            // Nunca derruba o app se falhar a inicialização do logger
        }
    }

    fun getSessionId(): String = sessionId

    fun getLevel(): Level {
        enforceTraceExpiration()
        return currentLevel
    }

    fun getTraceRemainingMinutes(): Int {
        enforceTraceExpiration()
        if (currentLevel != Level.TRACE || traceExpiresAtMs <= 0L) return 0
        val diff = traceExpiresAtMs - wallClockProvider()
        return if (diff <= 0L) 0 else ((diff + 59_999L) / 60_000L).toInt()
    }

    fun setLevel(level: Level, traceTtlMs: Long = DEFAULT_TRACE_TTL_MS) {
        currentLevel = level
        traceExpiresAtMs = if (level == Level.TRACE) {
            val clamped = traceTtlMs.coerceIn(MIN_TRACE_TTL_MS, MAX_TRACE_TTL_MS)
            wallClockProvider() + clamped
        } else {
            0L
        }
        persistLevelState()
    }

    fun enableTemporaryTrace(minutes: Int = 45) {
        setLevel(Level.TRACE, minutes.toLong() * 60_000L)
        logEvent(
            event = "diagnostics_trace_enabled",
            phase = "diagnostics",
            severity = Severity.INFO,
            meta = mapOf("ttlMinutes" to minutes.coerceIn(30, 60)),
            force = true
        )
    }

    fun setScreen(screen: String) {
        val next = screen.trim().ifBlank { "unknown" }
        val prev = currentScreen
        if (prev != next) {
            logEvent("screen_leave", phase = "navigation", screen = prev, meta = mapOf("to" to next))
        }
        currentScreen = next
        logEvent("screen_enter", phase = "navigation", screen = next, meta = mapOf("from" to prev))
    }

    fun startTimer(key: String, correlationId: String? = null): String {
        val cid = correlationId ?: ("cor_" + UUID.randomUUID().toString().replace("-", "").take(10))
        activeTimers[key] = monoClockProvider() to cid
        return cid
    }

    fun endTimer(
        key: String,
        event: String,
        phase: String = "runtime",
        severity: Severity = Severity.INFO,
        screen: String? = null,
        message: String? = null,
        meta: Map<String, Any?> = emptyMap(),
        minLevel: Level? = null
    ) {
        val started = activeTimers.remove(key)
        val durationMs = started?.let { (monoClockProvider() - it.first).coerceAtLeast(0L) }
        logEvent(
            event = event,
            phase = phase,
            severity = severity,
            screen = screen,
            durationMs = durationMs,
            correlationId = started?.second,
            message = message,
            meta = meta,
            minLevel = minLevel
        )
    }

    fun logEvent(
        event: String,
        phase: String = "runtime",
        severity: Severity = Severity.INFO,
        screen: String? = null,
        durationMs: Long? = null,
        correlationId: String? = null,
        message: String? = null,
        meta: Map<String, Any?> = emptyMap(),
        minLevel: Level? = null,
        force: Boolean = false
    ) {
        try {
            enforceTraceExpiration()
            if (!force) {
                val activeRank = currentLevel.rank
                if (activeRank == 0) return
                val requiredRank = minLevel?.rank ?: severity.minRank
                if (activeRank < requiredRank) return
            }

            if (!screen.isNullOrBlank()) {
                currentScreen = screen
            }

            val json = JSONObject().apply {
                put("schemaVersion", SCHEMA_VERSION)
                put("tsUtc", formatUtcIso(wallClockProvider()))
                put("monoMs", monoClockProvider())
                put("sessionId", sessionId)
                put("platform", "android")
                put("version", BuildConfig.VERSION_NAME)
                put("build", BuildConfig.VERSION_CODE)
                put("screen", LogSanitizer.sanitizeText(currentScreen))
                put("event", LogSanitizer.sanitizeText(event))
                put("phase", LogSanitizer.sanitizeText(phase))
                put("severity", severity.name)
                if (durationMs != null && durationMs >= 0L) put("durationMs", durationMs)
                if (!correlationId.isNullOrBlank()) put("correlationId", LogSanitizer.sanitizeText(correlationId))
                if (!message.isNullOrBlank()) put("message", LogSanitizer.sanitizeText(message).take(1024))
                if (meta.isNotEmpty()) {
                    val safeMeta = sanitizeMeta(meta)
                    if (safeMeta.length() > 0) put("meta", safeMeta)
                }
            }

            // Redaction aplicada antes da persistência
            val safeLine = LogSanitizer.sanitizeText(json.toString())
            synchronized(ringLock) {
                if (memoryRing.size >= MAX_MEMORY_EVENTS) {
                    memoryRing.removeFirst()
                }
                memoryRing.addLast(safeLine)
                pendingDiskBatch.add(safeLine)
            }
            scheduleDiskFlush()
        } catch (_: Throwable) {
            // Isolamento defensivo
        }
    }

    fun flushNow() {
        val ctx = appContext ?: return
        val linesToWrite: List<String>
        synchronized(ringLock) {
            if (pendingDiskBatch.isEmpty()) return
            linesToWrite = pendingDiskBatch.toList()
            pendingDiskBatch.clear()
        }
        try {
            val dir = File(ctx.filesDir, LOGS_DIR).apply { mkdirs() }
            val currentFile = File(dir, CURRENT_LOG_FILE)
            val rotatedFile = File(dir, ROTATED_LOG_FILE)
            if (currentFile.exists() && currentFile.length() >= MAX_FILE_BYTES) {
                if (rotatedFile.exists()) rotatedFile.delete()
                currentFile.renameTo(rotatedFile)
            }
            val payload = linesToWrite.joinToString("\n", postfix = "\n")
            currentFile.appendText(payload, Charsets.UTF_8)
        } catch (_: Throwable) {
            // Falha de I/O nunca deve quebrar o app
        }
    }

    fun getBufferedCount(): Int = synchronized(ringLock) { memoryRing.size }

    fun exportJsonl(context: Context): String {
        if (appContext == null) appContext = context.applicationContext ?: context
        flushNow()
        val lines = LinkedHashSet<String>()
        try {
            val dir = File(context.filesDir, LOGS_DIR)
            listOf(File(dir, ROTATED_LOG_FILE), File(dir, CURRENT_LOG_FILE)).forEach { file ->
                if (file.exists() && file.length() > 0L) {
                    file.readLines(Charsets.UTF_8).forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) lines.add(LogSanitizer.sanitizeText(trimmed))
                    }
                }
            }
        } catch (_: Throwable) {}
        synchronized(ringLock) {
            memoryRing.forEach { lines.add(LogSanitizer.sanitizeText(it)) }
        }
        logEvent(
            event = "diagnostics_exported",
            phase = "diagnostics",
            meta = mapOf("exportedLines" to lines.size),
            force = true
        )
        return lines.joinToString("\n")
    }

    fun exportDiagnosticFile(context: Context, crashLog: String? = CrashLogger.getLatestCrashLog(context)): File {
        val diagDir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(wallClockProvider()))
        val outFile = File(diagDir, "mk21-android-diagnostico-$stamp.txt")
        val content = buildString {
            appendLine("=== MK21 ANDROID DIAGNOSTIC BUNDLE (REDACTED) ===")
            appendLine("SessionId: $sessionId")
            appendLine("Level: ${getLevel().name}")
            appendLine(CrashLogger.getSystemDiagnosticInfo(context))
            appendLine("=== STRUCTURED EVENTS (JSONL) ===")
            val jsonl = exportJsonl(context)
            appendLine(if (jsonl.isBlank()) "(nenhum evento registrado)" else jsonl)
            appendLine()
            appendLine("=== LATEST CRASH REPORT ===")
            appendLine(LogSanitizer.sanitizeText(crashLog ?: "Nenhum relatório de falha capturado."))
        }
        outFile.writeText(LogSanitizer.sanitizeText(content), Charsets.UTF_8)
        return outFile
    }

    fun clear(context: Context) {
        synchronized(ringLock) {
            memoryRing.clear()
            pendingDiskBatch.clear()
        }
        try {
            val dir = File(context.filesDir, LOGS_DIR)
            File(dir, CURRENT_LOG_FILE).delete()
            File(dir, ROTATED_LOG_FILE).delete()
        } catch (_: Throwable) {}
        logEvent(event = "diagnostics_cleared", phase = "diagnostics", force = true)
        flushNow()
    }

    internal fun resetForTesting() {
        synchronized(ringLock) {
            memoryRing.clear()
            pendingDiskBatch.clear()
        }
        activeTimers.clear()
        currentLevel = Level.NORMAL
        traceExpiresAtMs = 0L
        currentScreen = "boot"
        wallClockProvider = { System.currentTimeMillis() }
    }

    private fun enforceTraceExpiration() {
        if (currentLevel == Level.TRACE && traceExpiresAtMs > 0L && wallClockProvider() >= traceExpiresAtMs) {
            currentLevel = Level.NORMAL
            traceExpiresAtMs = 0L
            persistLevelState()
        }
    }

    private fun persistLevelState() {
        try {
            val ctx = appContext ?: return
            ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_LEVEL, currentLevel.name)
                .putLong(KEY_TRACE_UNTIL, traceExpiresAtMs)
                .apply()
        } catch (_: Throwable) {}
    }

    private fun scheduleDiskFlush() {
        if (appContext == null) return
        if (flushScheduled.compareAndSet(false, true)) {
            try {
                ioExecutor.schedule({
                    flushScheduled.set(false)
                    flushNow()
                }, 1200L, TimeUnit.MILLISECONDS)
            } catch (_: Throwable) {
                flushScheduled.set(false)
            }
        }
    }

    private fun sanitizeMeta(meta: Map<String, Any?>): JSONObject {
        val obj = JSONObject()
        for ((rawKey, rawVal) in meta) {
            if (rawVal == null) continue
            val key = rawKey.trim()
            if (key.isEmpty()) continue
            if (SENSITIVE_KEY_REGEX.matches(key)) {
                obj.put(key, "[REDACTED]")
                continue
            }
            val lower = key.lowercase(Locale.ROOT)
            if (lower == "url" || lower == "href" || lower == "endpoint" || lower == "streamurl") {
                obj.put("host", LogSanitizer.extractSafeHost(rawVal.toString()))
                continue
            }
            if (lower == "playlist" || lower == "m3u" || lower == "rawbody" || lower == "body") {
                obj.put(key, "[REDACTED_PAYLOAD]")
                continue
            }
            when (rawVal) {
                is Number, is Boolean -> obj.put(key, rawVal)
                else -> obj.put(key, LogSanitizer.sanitizeText(rawVal.toString()).take(512))
            }
        }
        return obj
    }

    private fun formatUtcIso(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochMs))
    }
}
