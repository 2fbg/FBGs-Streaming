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
    fun resolveUrlWithFailover(url: Any?): String {
        val inputUrl = url?.toString() ?: ""
        if (inputUrl.isEmpty()) return ""
        return try {
            val host = URL(inputUrl).host
            Dns.SYSTEM.lookup(host)
            inputUrl
        } catch (e: Exception) {
            inputUrl
        }
    }
}
