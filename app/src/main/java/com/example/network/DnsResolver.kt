package com.example.network

import android.util.Log
import okhttp3.Dns
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

/**
 * DNS Resolver Resiliente com Failover Automático.
 * Se a resolução padrão do sistema falhar (bloqueio de operadora ou DNS local instável em IPTV),
 * tenta resolver o domínio via DoH (DNS-over-HTTPS) usando Cloudflare (1.1.1.1) e Google (8.8.8.8).
 */
object DnsResolver : Dns {
    private const val TAG = "DnsResolver"
    private val cache = ConcurrentHashMap<String, List<InetAddress>>()

    override fun lookup(hostname: String): List<InetAddress> {
        // 1. Verifica cache local de resolução
        cache[hostname]?.let { return it }

        // 2. Tenta resolução de DNS nativa do Sistema
        try {
            val systemAddresses = Dns.SYSTEM.lookup(hostname)
            if (systemAddresses.isNotEmpty()) {
                cache[hostname] = systemAddresses
                return systemAddresses
            }
        } catch (e: UnknownHostException) {
            Log.w(TAG, "DNS do sistema falhou para $hostname. Tentando DNS Failover DoH (Cloudflare / Google)...")
        } catch (e: Exception) {
            Log.w(TAG, "Exceção no DNS do sistema para $hostname: ${e.message}")
        }

        // 3. Failover DoH: Cloudflare (1.1.1.1)
        try {
            val cloudflareAddresses = resolveViaCloudflareDoH(hostname)
            if (cloudflareAddresses.isNotEmpty()) {
                Log.i(TAG, "DNS Failover resolvido via Cloudflare (1.1.1.1) para $hostname: $cloudflareAddresses")
                cache[hostname] = cloudflareAddresses
                return cloudflareAddresses
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha no DoH Cloudflare para $hostname: ${e.message}")
        }

        // 4. Failover DoH: Google (8.8.8.8)
        try {
            val googleAddresses = resolveViaGoogleDoH(hostname)
            if (googleAddresses.isNotEmpty()) {
                Log.i(TAG, "DNS Failover resolvido via Google (8.8.8.8) para $hostname: $googleAddresses")
                cache[hostname] = googleAddresses
                return googleAddresses
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha no DoH Google para $hostname: ${e.message}")
        }

        // Se todos falharem, lança UnknownHostException padrão
        throw UnknownHostException("Não foi possível resolver o host: $hostname através do DNS do Sistema, Cloudflare DoH ou Google DoH")
    }

    /**
     * Tenta resolver o host da URL com failover caso o host não seja IP.
     * Retorna a URL com o IP resolvido em caso de falha de resolução normal.
     */
    fun resolveUrlWithFailover(originalUrl: String): String {
        return try {
            val uri = java.net.URI(originalUrl)
            val host = uri.host ?: return originalUrl
            if (host.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$"""))) {
                return originalUrl
            }
            val resolved = lookup(host)
            if (resolved.isNotEmpty()) {
                val ip = resolved.first().hostAddress ?: return originalUrl
                Log.i(TAG, "Substituindo host $host por IP resolvido $ip na URL de streaming")
                originalUrl.replaceFirst(host, ip)
            } else {
                originalUrl
            }
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível aplicar DNS Failover na URL: ${e.message}")
            originalUrl
        }
    }

    private fun resolveViaCloudflareDoH(hostname: String): List<InetAddress> {
        val url = URL("https://1.1.1.1/dns-query?name=$hostname&type=A")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3000
            readTimeout = 3000
            setRequestProperty("Accept", "application/dns-json")
            setRequestProperty("User-Agent", "MK21-DnsResolver/1.2.0")
        }
        val ips = mutableListOf<InetAddress>()
        try {
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                val json = JSONObject(response)
                val answers = json.optJSONArray("Answer")
                if (answers != null) {
                    for (i in 0 until answers.length()) {
                        val obj = answers.getJSONObject(i)
                        val type = obj.optInt("type")
                        val data = obj.optString("data")
                        if (type == 1 && data.isNotEmpty()) {
                            ips.add(InetAddress.getByName(data))
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return ips
    }

    private fun resolveViaGoogleDoH(hostname: String): List<InetAddress> {
        val url = URL("https://dns.google/resolve?name=$hostname&type=A")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3000
            readTimeout = 3000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MK21-DnsResolver/1.2.0")
        }
        val ips = mutableListOf<InetAddress>()
        try {
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                val json = JSONObject(response)
                val answers = json.optJSONArray("Answer")
                if (answers != null) {
                    for (i in 0 until answers.length()) {
                        val obj = answers.getJSONObject(i)
                        val type = obj.optInt("type")
                        val data = obj.optString("data")
                        if (type == 1 && data.isNotEmpty()) {
                            ips.add(InetAddress.getByName(data))
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return ips
    }
}
