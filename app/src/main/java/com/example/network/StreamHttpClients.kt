package com.example.network

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Clientes OkHttp compartilhados com failover DoH (Cloudflare + Google),
 * pool persistente de conexões e injeção automática de cabeçalhos #EXTVLCOPT.
 */
object StreamHttpClients {
    const val DEFAULT_MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(DnsResolver)
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val streamClient: OkHttpClient by lazy {
        baseClient.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val original = chain.request()
                val custom = StreamHeadersRegistry.get(original.url.toString())
                val builder = original.newBuilder()
                if (!custom?.userAgent.isNullOrBlank()) {
                    builder.header("User-Agent", custom!!.userAgent!!)
                } else if (original.header("User-Agent") == null) {
                    builder.header("User-Agent", DEFAULT_MOBILE_UA)
                }
                if (!custom?.referrer.isNullOrBlank() && original.header("Referer") == null) {
                    builder.header("Referer", custom!!.referrer!!)
                }
                chain.proceed(builder.build())
            }
            .build()
    }
}
