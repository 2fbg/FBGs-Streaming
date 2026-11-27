package com.fbg.streaming

import com.example.data.service.CastNetworkPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class CastNetworkPolicyTest {

    @Test
    fun `isPrivateLanIpv4 accepts only RFC1918 literals`() {
        assertTrue(CastNetworkPolicy.isPrivateLanIpv4("192.168.3.79"))
        assertTrue(CastNetworkPolicy.isPrivateLanIpv4("10.0.0.5"))
        assertTrue(CastNetworkPolicy.isPrivateLanIpv4("172.16.0.1"))
        assertTrue(CastNetworkPolicy.isPrivateLanIpv4("172.31.255.254"))
        assertFalse(CastNetworkPolicy.isPrivateLanIpv4("172.32.0.1"))
        assertFalse(CastNetworkPolicy.isPrivateLanIpv4("8.8.8.8"))
        assertFalse(CastNetworkPolicy.isPrivateLanIpv4("169.254.169.254"))
        assertFalse(CastNetworkPolicy.isPrivateLanIpv4("127.0.0.1"))
        assertFalse(CastNetworkPolicy.isPrivateLanIpv4("192.168.003.079"))
        assertFalse(CastNetworkPolicy.isPrivateLanIpv4("tv.example.com"))
    }

    @Test
    fun `isTrustedSsdpLocation requires http, private host and matching responder`() {
        val ok = "http://192.168.3.79:1945/description.xml"
        assertTrue(CastNetworkPolicy.isTrustedSsdpLocation(ok, "192.168.3.79"))
        assertTrue(CastNetworkPolicy.isTrustedSsdpLocation(ok, ""))
        assertFalse(CastNetworkPolicy.isTrustedSsdpLocation(ok, "192.168.3.80"))
        assertTrue(CastNetworkPolicy.isTrustedSsdpLocation(ok, "192.168.3.80", strictSource = false))
        assertFalse(CastNetworkPolicy.isTrustedSsdpLocation("http://169.254.169.254/latest/meta-data", "169.254.169.254"))
        assertFalse(CastNetworkPolicy.isTrustedSsdpLocation("http://8.8.8.8/desc.xml", "8.8.8.8"))
        assertFalse(CastNetworkPolicy.isTrustedSsdpLocation("https://192.168.3.79/desc.xml", "192.168.3.79"))
        assertFalse(CastNetworkPolicy.isTrustedSsdpLocation("file:///etc/passwd", ""))
        assertFalse(CastNetworkPolicy.isTrustedSsdpLocation("http://user:pw@192.168.3.79/desc.xml", "192.168.3.79"))
        assertFalse(CastNetworkPolicy.isTrustedSsdpLocation("not a url", ""))
    }

    @Test
    fun `isSameOrigin compares scheme host and effective port`() {
        assertTrue(CastNetworkPolicy.isSameOrigin("http://192.168.3.79/a.xml", "http://192.168.3.79:80/b/control"))
        assertTrue(CastNetworkPolicy.isSameOrigin("http://192.168.3.79:1945/a", "http://192.168.3.79:1945/b"))
        assertFalse(CastNetworkPolicy.isSameOrigin("http://192.168.3.79:1945/a", "http://192.168.3.79:1946/b"))
        assertFalse(CastNetworkPolicy.isSameOrigin("http://192.168.3.79/a", "http://192.168.3.80/a"))
        assertFalse(CastNetworkPolicy.isSameOrigin("http://192.168.3.79/a", "http://attacker.example/a"))
    }

    @Test
    fun `isSameHost ignores the port but not the host`() {
        assertTrue(CastNetworkPolicy.isSameHost("http://192.168.3.79:1945/a", "http://192.168.3.79:8009/b"))
        assertFalse(CastNetworkPolicy.isSameHost("http://192.168.3.79/a", "http://192.168.3.80/a"))
        assertFalse(CastNetworkPolicy.isSameHost("http://192.168.3.79/a", "not a url"))
    }

    @Test
    fun `isLanPeer accepts loopback and local ranges but not public addresses`() {
        assertTrue(CastNetworkPolicy.isLanPeer(InetAddress.getByName("127.0.0.1")))
        assertTrue(CastNetworkPolicy.isLanPeer(InetAddress.getByName("192.168.1.20")))
        assertTrue(CastNetworkPolicy.isLanPeer(InetAddress.getByName("10.1.2.3")))
        assertTrue(CastNetworkPolicy.isLanPeer(InetAddress.getByName("172.20.0.9")))
        assertTrue(CastNetworkPolicy.isLanPeer(InetAddress.getByName("169.254.10.10")))
        assertTrue(CastNetworkPolicy.isLanPeer(InetAddress.getByName("100.101.102.103")))
        assertTrue(CastNetworkPolicy.isLanPeer(InetAddress.getByName("fd12:3456:789a::1")))
        assertTrue(CastNetworkPolicy.isLanPeer(InetAddress.getByName("fe80::1")))
        assertFalse(CastNetworkPolicy.isLanPeer(InetAddress.getByName("8.8.8.8")))
        assertFalse(CastNetworkPolicy.isLanPeer(InetAddress.getByName("100.128.0.1")))
        assertFalse(CastNetworkPolicy.isLanPeer(InetAddress.getByName("2001:4860:4860::8888")))
        assertFalse(CastNetworkPolicy.isLanPeer(null))
    }

    @Test
    fun `isBlockedUpstreamHost blocks loopback metadata and obfuscated forms but allows LAN and public hosts`() {
        listOf(
            "localhost", "LOCALHOST.", "foo.localhost", "127.0.0.1", "127.1.2.3", "0.0.0.0", "169.254.169.254",
            "2130706433", "0x7f000001", "0x7f.0.0.1", "0177.0.0.1", "[::1]", "::1", "[fe80::1]",
            "[::ffff:127.0.0.1]", "metadata.google.internal", ""
        ).forEach { assertTrue("deveria bloquear: $it", CastNetworkPolicy.isBlockedUpstreamHost(it)) }

        listOf(
            "iptv.example.com", "192.168.0.10", "10.0.0.2", "172.16.5.5", "8.8.8.8", "cdn.example.org"
        ).forEach { assertFalse("deveria permitir: $it", CastNetworkPolicy.isBlockedUpstreamHost(it)) }
    }
}
