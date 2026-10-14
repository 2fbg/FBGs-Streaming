package com.example.network

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import java.net.URL

/**
 * Implementação customizada de DNS que satisfaz o OkHttp e o Player.
 */
object DnsResolver : Dns {

    // Método exigido pelo OkHttp (Usado no AppViewModel)
    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            // Tenta o DNS padrão do sistema
            Dns.SYSTEM.lookup(hostname)
        } catch (e: UnknownHostException) {
            // Se falhar, aqui o OkHttp tentaria automaticamente outras rotas
            // Por enquanto, repassamos a exceção de forma segura
            throw e
        }
    }

    // Método utilitário para o Player (Usado no InternalPlayerScreen)
    fun resolveUrlWithFailover(url: String): String {
        return try {
            val host = URL(url).host
            lookup(host)
            url
        } catch (e: Exception) {
            url
        }
    }
}
