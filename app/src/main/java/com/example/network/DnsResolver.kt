package com.example.network

import java.net.URL
import java.net.InetAddress

object DnsResolver {
    /**
     * Tenta resolver a URL. Se falhar, poderia implementar 
     * lógica de failover para DNS do Google ou Cloudflare.
     */
    fun resolveUrlWithFailover(url: String): String {
        return try {
            val uri = URL(url)
            // Tenta uma resolução simples para validar o host
            InetAddress.getByName(uri.host)
            url // Retorna a URL original se estiver OK
        } catch (e: Exception) {
            // Aqui entraria a lógica de troca de DNS (failover)
            // Por enquanto, retorna a URL original para não quebrar o fluxo
            url
        }
    }
}
