package com.example.network

import okhttp3.Dns
import java.net.InetAddress
import java.net.URL

/**
 * Mk21DnsResolver v1.2.0 - Estável
 */
object Mk21DnsResolver : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            // Em caso de falha de DNS local, o OkHttp lida com o erro
            throw e
        }
    }

    @JvmStatic
    fun resolveUrlWithFailover(url: String?): String {
        if (url.isNullOrEmpty()) return ""
        return try {
            // Apenas validação básica de host
            val host = URL(url).host
            Dns.SYSTEM.lookup(host)
            url
        } catch (e: Exception) {
            url
        }
    }
}
