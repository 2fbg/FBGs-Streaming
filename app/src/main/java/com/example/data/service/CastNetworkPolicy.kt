package com.example.data.service

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.util.Locale

/**
 * Regras de rede do cast/DLNA. Código puro (sem Android) para poder ser testado na JVM.
 *
 * Ameaças cobertas:
 *  - dispositivo malicioso na LAN anunciando um LOCATION/controlUrl fora da rede local (SSRF e vazamento
 *    da URL do stream com o token de sessão);
 *  - servidor de cast acessado por pares fora da rede local (ex.: IPv6 público);
 *  - proxy HLS usado para alcançar loopback/metadata via formas ofuscadas de IP (decimal, hexadecimal, IPv6).
 */
internal object CastNetworkPolicy {

    /** Parse estrito de IPv4 decimal pontuado (sem zeros à esquerda). */
    private fun parseStrictIpv4(host: String): IntArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val out = IntArray(4)
        for (i in 0..3) {
            val p = parts[i]
            if (p.isEmpty() || p.length > 3 || !p.all { it in '0'..'9' }) return null
            if (p.length > 1 && p[0] == '0') return null
            val v = p.toInt()
            if (v !in 0..255) return null
            out[i] = v
        }
        return out
    }

    /** IPv4 privado RFC 1918 (10/8, 172.16/12, 192.168/16). */
    fun isPrivateLanIpv4(host: String): Boolean {
        val ip = parseStrictIpv4(host) ?: return false
        return ip[0] == 10 ||
            (ip[0] == 172 && ip[1] in 16..31) ||
            (ip[0] == 192 && ip[1] == 168)
    }

    private fun effectivePort(uri: URI): Int =
        if (uri.port != -1) uri.port else if (uri.scheme.equals("https", ignoreCase = true)) 443 else 80

    /**
     * LOCATION do SSDP: precisa ser http, sem credenciais, com host IPv4 privado e, quando o IP de quem
     * respondeu é conhecido e [strictSource] é true, o host deve ser exatamente esse IP.
     * (Se alguma TV com duas interfaces deixar de aparecer, use strictSource = false.)
     */
    fun isTrustedSsdpLocation(location: String, responderIp: String?, strictSource: Boolean = true): Boolean {
        val uri = try { URI(location.trim()) } catch (_: Exception) { return false }
        if (!"http".equals(uri.scheme, ignoreCase = true)) return false
        if (uri.userInfo != null) return false
        val host = uri.host?.lowercase(Locale.US) ?: return false
        if (!isPrivateLanIpv4(host)) return false
        if (uri.port != -1 && uri.port !in 1..65535) return false
        val source = responderIp?.trim().orEmpty()
        if (strictSource && source.isNotEmpty() && source != host) return false
        return true
    }

    /** Mesmo esquema, host e porta (usado para impedir controlUrl apontando para outro host). */
    fun isSameOrigin(a: String, b: String): Boolean {
        val ua = try { URI(a.trim()) } catch (_: Exception) { return false }
        val ub = try { URI(b.trim()) } catch (_: Exception) { return false }
        val ha = ua.host?.lowercase(Locale.US) ?: return false
        val hb = ub.host?.lowercase(Locale.US) ?: return false
        return ua.scheme.equals(ub.scheme, ignoreCase = true) && ha == hb && effectivePort(ua) == effectivePort(ub)
    }

    /** Mesmo host (a porta pode variar: alguns aparelhos anunciam URLBase em outra porta). */
    fun isSameHost(a: String, b: String): Boolean {
        val ha = try { URI(a.trim()).host } catch (_: Exception) { null }?.lowercase(Locale.US) ?: return false
        val hb = try { URI(b.trim()).host } catch (_: Exception) { null }?.lowercase(Locale.US) ?: return false
        return ha == hb
    }

    /**
     * Quem pode conectar no servidor de cast: loopback, rede local (RFC 1918 / link-local / ULA IPv6)
     * e 100.64.0.0/10 (VPN tipo Tailscale). Qualquer outro par é descartado.
     */
    fun isLanPeer(addr: InetAddress?): Boolean {
        if (addr == null) return false
        if (addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress) return true
        val bytes = addr.address
        if (addr is Inet6Address) return (bytes[0].toInt() and 0xFE) == 0xFC // fc00::/7 (ULA)
        return bytes.size == 4 && (bytes[0].toInt() and 0xFF) == 100 && (bytes[1].toInt() and 0xC0) == 0x40
    }

    /**
     * Hosts que o proxy HLS nunca deve alcançar: loopback, 0.0.0.0/8, link-local/metadata,
     * IPv6 loopback/link-local/mapeado e formas numéricas ambíguas (decimal, hex, octal).
     * Redes privadas RFC 1918 continuam permitidas de propósito (IPTV em LAN).
     */
    fun isBlockedUpstreamHost(rawHost: String): Boolean {
        val host = rawHost.trim().lowercase(Locale.US).removePrefix("[").removeSuffix("]").trimEnd('.')
        if (host.isEmpty()) return true
        if (host == "localhost" || host.endsWith(".localhost") || host == "metadata.google.internal") return true
        if (host.contains(':')) { // IPv6 literal
            if (host == "::1" || host == "::" || host.startsWith("fe80:") || host.startsWith("::ffff:")) return true
            return false
        }
        if (host.startsWith("0x") || host.contains(".0x")) return true
        if (host.all { it in '0'..'9' || it == '.' }) {
            val ip = parseStrictIpv4(host) ?: return true // decimal único, octal ou malformado
            return ip[0] == 127 || ip[0] == 0 || (ip[0] == 169 && ip[1] == 254)
        }
        return false
    }
}
