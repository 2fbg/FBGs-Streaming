package com.example.network

import okhttp3.Dns
import java.net.InetAddress
import java.net.URL

/**
 * Singleton de resolução DNS com suporte a failover para o player e clientes HTTP.
 */
object Mk21DnsResolver : Dns {
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
}
