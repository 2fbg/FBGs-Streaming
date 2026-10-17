package com.example.network
import okhttp3.Dns
import java.net.InetAddress
import java.net.URL

object Mk21DnsResolver : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return try { Dns.SYSTEM.lookup(hostname) } catch (e: Exception) { throw e }
    }
    @JvmStatic
    fun resolveUrlWithFailover(url: Any?): String {
        val input = url?.toString() ?: ""
        return try {
            if (input.isNotEmpty()) { val host = URL(input).host; Dns.SYSTEM.lookup(host) }
            input
        } catch (e: Exception) { input }
    }
}
