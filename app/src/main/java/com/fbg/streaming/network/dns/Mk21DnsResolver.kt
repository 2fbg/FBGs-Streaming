package com.fbg.streaming.network.dns

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class Mk21DnsResolver(context: Context) : Dns {

   private data class CachedDnsEntry(
       val addresses: List<InetAddress>,
       val expiresAtMillis: Long
   )

   private val memoryCache = ConcurrentHashMap<String, CachedDnsEntry>()
   private val prefs: SharedPreferences =
       context.getSharedPreferences("mk21_dns_cache_v2", Context.MODE_PRIVATE)

   private val bootstrapClient = OkHttpClient.Builder()
       .connectTimeout(1500, TimeUnit.MILLISECONDS)
       .readTimeout(1500, TimeUnit.MILLISECONDS)
       .build()

   // Os 4 provedores DoH exatos extraídos da engenharia reversa de libranger-jni.so
   private val dohProviders: List<DnsOverHttps> = listOf(
       DnsOverHttps.Builder().client(bootstrapClient)
           .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
           .bootstrapDnsHosts(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"))
           .build(),
       DnsOverHttps.Builder().client(bootstrapClient)
           .url("https://dns.google/dns-query".toHttpUrl())
           .bootstrapDnsHosts(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("8.8.4.4"))
           .build(),
       DnsOverHttps.Builder().client(bootstrapClient)
           .url("https://dns10.quad9.net/dns-query".toHttpUrl())
           .bootstrapDnsHosts(InetAddress.getByName("9.9.9.10"), InetAddress.getByName("149.112.112.10"))
           .build(),
       DnsOverHttps.Builder().client(bootstrapClient)
           .url("https://dns.alidns.com/dns-query".toHttpUrl())
           .bootstrapDnsHosts(InetAddress.getByName("223.5.5.5"), InetAddress.getByName("223.6.6.6"))
           .build()
   )

   override fun lookup(hostname: String): List<InetAddress> {
       if (hostname == "127.0.0.1" || hostname.equals("localhost", ignoreCase = true)) {
           return listOf(InetAddress.getByName("127.0.0.1"))
       }

       val now = System.currentTimeMillis()

       // 1. Consulta Cache em Memória (0 ms)
       memoryCache[hostname]?.let { cached ->
           if (now < cached.expiresAtMillis) return cached.addresses
       }

       // 2. Consulta Cache Persistente em Disco para Cold Start instantâneo
       loadFromDiskCache(hostname, now)?.let { diskAddresses ->
           memoryCache[hostname] = CachedDnsEntry(diskAddresses, now + TTL_MILLIS)
           return diskAddresses
       }

       // 3. Corrida Paralela (Happy Eyeballs) entre os 4 provedores DoH
       val resolved = raceDohProviders(hostname)
       if (resolved.isNotEmpty()) {
           saveToCache(hostname, resolved, now + TTL_MILLIS)
           return resolved
       }

       // 4. Fallback para DNS do Sistema com filtro Anti-Sinkhole / Anti-Bogon de Operadora
       val systemAddrs = Dns.SYSTEM.lookup(hostname).filterNot { isBogonOrSinkhole(it) }
       if (systemAddrs.isNotEmpty()) {
           saveToCache(hostname, systemAddrs, now + (TTL_MILLIS / 2))
           return systemAddrs
       }

       throw UnknownHostException("Não foi possível resolver com segurança o domínio: $hostname")
   }

   private fun raceDohProviders(hostname: String): List<InetAddress> = runBlocking {
       val resultChannel = Channel<List<InetAddress>>(Channel.CONFLATED)
       val jobs = dohProviders.map { provider ->
           launch(Dispatchers.IO) {
               try {
                   val addrs = provider.lookup(hostname).filterNot { isBogonOrSinkhole(it) }
                   if (addrs.isNotEmpty()) {
                       resultChannel.trySend(addrs)
                   }
               } catch (_: Exception) {
               }
           }
       }

       val winner = withTimeoutOrNull(1800L) {
           resultChannel.receive()
       } ?: emptyList()

       jobs.forEach { it.cancel() }
       winner
   }

   /**
    * Filtra IPs forjados por bloqueio de DNS de operadoras (0.0.0.0, 127.x.x.x, IPs privados para hosts públicos).
    */
   private fun isBogonOrSinkhole(address: InetAddress): Boolean {
       return address.isAnyLocalAddress ||
           address.isLoopbackAddress ||
           address.isLinkLocalAddress ||
           address.isSiteLocalAddress ||
           address.hostAddress == "0.0.0.0"
   }

   private fun saveToCache(hostname: String, addresses: List<InetAddress>, expiresAt: Long) {
       memoryCache[hostname] = CachedDnsEntry(addresses, expiresAt)
       val serialized = addresses.joinToString(",") { it.hostAddress ?: "" }
       prefs.edit()
           .putString("ips_$hostname", serialized)
           .putLong("exp_$hostname", expiresAt)
           .apply()
   }

   private fun loadFromDiskCache(hostname: String, now: Long): List<InetAddress>? {
       val exp = prefs.getLong("exp_$hostname", 0L)
       if (now >= exp) return null
       val raw = prefs.getString("ips_$hostname", null) ?: return null
       val list = raw.split(",").mapNotNull { ip ->
           if (ip.isBlank()) null else runCatching { InetAddress.getByName(ip) }.getOrNull()
       }
       return list.ifEmpty { null }
   }

   companion object {
       private const val TTL_MILLIS = 15 * 60 * 1000L // 15 minutos
   }
}