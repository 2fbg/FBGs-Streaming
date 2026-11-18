package com.example.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {

    private const val PREF_NAME = "mk21_crash_meta"
    private const val KEY_LAST_CRASH = "last_crash_log"
    private const val KEY_LAST_CRASH_TIME = "last_crash_timestamp"
    private const val LOG_FILE_NAME = "latest_crash.txt"
    private const val HISTORY_FILE_NAME = "crash_history.log"

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(context, thread, throwable)
            } catch (e: Exception) {
                Log.e("CrashLogger", "Failed to record crash", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun recordCrash(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = LogSanitizer.sanitizeText(sw.toString())
        val safeMessage = LogSanitizer.sanitizeText(throwable.message)

        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val memRuntime = Runtime.getRuntime()
        val usedMb = (memRuntime.totalMemory() - memRuntime.freeMemory()) / (1024 * 1024)
        val maxMb = memRuntime.maxMemory() / (1024 * 1024)

        val report = buildString {
            appendLine("=== MK21 PLAYER CRASH LOG ===")
            appendLine("Data/Hora: $timeStr")
            appendLine("Versão App: ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})")
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Memória RAM: ${usedMb}MB em uso / ${maxMb}MB max")
            appendLine("Thread: ${thread.name} (id: ${thread.id})")
            appendLine("Exceção: ${throwable.javaClass.name}: $safeMessage")
            appendLine("\n--- STACK TRACE ---")
            appendLine(stackTrace)
            appendLine("=============================")
        }

        Log.e("MK21_CRASH", report)

        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            file.writeText(report)

            val historyFile = File(context.filesDir, HISTORY_FILE_NAME)
            historyFile.appendText("\n$report\n")

            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAST_CRASH, report)
                .putLong(KEY_LAST_CRASH_TIME, System.currentTimeMillis())
                .commit()
        } catch (e: Exception) {
            Log.e("CrashLogger", "Error saving crash report", e)
        }
    }

    fun getLatestCrashLog(context: Context): String? {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists() && file.length() > 0) {
                return file.readText()
            }
            val prefLog = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_CRASH, null)
            if (!prefLog.isNullOrBlank()) {
                return prefLog
            }
        } catch (e: Exception) {
            Log.e("CrashLogger", "Error reading crash log", e)
        }
        return null
    }

    fun getSystemDiagnosticInfo(context: Context): String {
        val mem = Runtime.getRuntime()
        val usedMb = (mem.totalMemory() - mem.freeMemory()) / (1024 * 1024)
        val maxMb = mem.maxMemory() / (1024 * 1024)

        return buildString {
            appendLine("• Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("• Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("• Versão MK21: v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
            appendLine("• Memória JVM: ${usedMb}MB usado / ${maxMb}MB limite")
            appendLine("• Arquitetura CPU: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        }
    }

    fun clearLogs(context: Context) {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) file.delete()
            val historyFile = File(context.filesDir, HISTORY_FILE_NAME)
            if (historyFile.exists()) historyFile.delete()
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        } catch (e: Exception) {
            Log.e("CrashLogger", "Error clearing logs", e)
        }
    }
}
