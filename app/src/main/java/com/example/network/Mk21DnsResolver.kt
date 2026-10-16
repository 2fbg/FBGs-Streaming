package com.example.network

import android.util.Log
import okhttp3.Dns
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Singleton de resolução DNS com suporte a failover e telemetria silenciosa de stream
 * para o player e clientes HTTP do MK21 Play.
 */
object Mk21DnsResolver : Dns {
    private const val TAG = "Mk21DnsResolver"
    private const val MAX_LOG_HISTORY = 50

    data class StreamErrorInfo(
        val timestamp: Long,
        val url: String,
        val errorCode: Int,
        val message: String?
    )

    private val errorLogs = ConcurrentLinkedDeque<StreamErrorInfo>()

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            throw e
        }
    }

    @JvmStatic
    fun resolveUrlWithFailover(url: String?): String {
        val input = url?.trim() ?: ""
        if (input.isEmpty()) return ""
        return try {
            val host = URL(input).host
            Dns.SYSTEM.lookup(host)
            input
        } catch (e: Exception) {
            input
        }
    }

    @JvmStatic
    fun resolveUrlWithFailover(url: Any?): String = resolveUrlWithFailover(url?.toString())

    /**
     * Telemetria de erro silenciosa (Silent Logging) para auditoria de falhas de streaming.
     */
    @JvmStatic
    @JvmOverloads
    fun logStreamError(url: String?, errorCode: Int, message: String? = null) {
        val safeUrl = url?.trim() ?: "unknown_url"
        val entry = StreamErrorInfo(
            timestamp = System.currentTimeMillis(),
            url = safeUrl,
            errorCode = errorCode,
            message = message
        )
        errorLogs.addLast(entry)
        while (errorLogs.size > MAX_LOG_HISTORY) {
            errorLogs.pollFirst()
        }
        Log.w(TAG, "[Telemetry] Stream error logged (code=$errorCode, msg=${message ?: "none"}): $safeUrl")
    }

    @JvmStatic
    fun logStreamError(url: Any?, errorCode: Int) {
        logStreamError(url?.toString(), errorCode, null)
    }

    @JvmStatic
    fun getRecentErrors(): List<StreamErrorInfo> {
        return errorLogs.toList()
    }
}
