package com.example.network

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Resolvedor DNS customizado para forçar resolução IPv4 e melhorar 
 * a estabilidade de conexão em redes com IPv6 instável (comum em IPTV).
 */
class CustomDnsResolver : Dns {
    @Throws(UnknownHostException::class)
    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.isEmpty()) {
            throw UnknownHostException("Hostname vazio")
        }
        
        val addresses = Dns.SYSTEM.lookup(hostname)
        val ipv4Addresses = addresses.filterIsInstance<Inet4Address>()
        
        return if (ipv4Addresses.isNotEmpty()) {
            ipv4Addresses
        } else {
            addresses
        }
    }
}
