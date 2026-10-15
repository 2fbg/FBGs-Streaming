package com.example.network

import okhttp3.Dns
import java.net.InetAddress
import java.net.URL

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
        if (url.isNullOrEmpty()) return ""
        return try {
            val host = URL(url).host
            lookup(host)
            url
        } catch (e: Exception) {
            url
        }
    }
}
