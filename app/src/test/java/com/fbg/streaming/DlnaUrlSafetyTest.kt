package com.fbg.streaming

import com.example.data.service.DlnaDiscoveryService
import com.example.data.service.LocalHttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DlnaUrlSafetyTest {

    private fun deviceXml(avControl: String, renderingControl: String = "/RenderingControl/1/control.xml", urlBase: String? = null): String {
        val base = if (urlBase != null) "<URLBase>$urlBase</URLBase>" else ""
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              $base
              <device>
                <friendlyName>Sala</friendlyName>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>$avControl</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                    <controlURL>$renderingControl</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()
    }

    private val descUrl = "http://192.168.3.79:1945/description.xml"
    private val ip = "192.168.3.79"

    @Test
    fun `rejects an absolute AVTransport controlURL pointing to another host`() {
        val xml = deviceXml(avControl = "http://attacker.example.com/AVTransport/control")
        assertNull(DlnaDiscoveryService.parseDeviceDescriptionXml(xml, descUrl, ip))
    }

    @Test
    fun `rejects a URLBase pointing to another host`() {
        val xml = deviceXml(avControl = "/AVTransport/control", urlBase = "http://10.9.9.9:8000/")
        assertNull(DlnaDiscoveryService.parseDeviceDescriptionXml(xml, descUrl, ip))
    }

    @Test
    fun `drops a RenderingControl url on another host but keeps the device`() {
        val xml = deviceXml(
            avControl = "/AVTransport/control",
            renderingControl = "http://attacker.example.com/RenderingControl/control"
        )
        val device = DlnaDiscoveryService.parseDeviceDescriptionXml(xml, descUrl, ip)
        assertNotNull(device)
        assertEquals("http://192.168.3.79:1945/AVTransport/control", device!!.controlUrl)
        assertNull(device.renderingControlUrl)
    }

    @Test
    fun `accepts a URLBase with a different port on the same host`() {
        val xml = deviceXml(avControl = "/AVTransport/control", urlBase = "http://192.168.3.79:8009/")
        val device = DlnaDiscoveryService.parseDeviceDescriptionXml(xml, descUrl, ip)
        assertNotNull(device)
        assertEquals("http://192.168.3.79:8009/AVTransport/control", device!!.controlUrl)
    }

    @Test
    fun `LocalHttpServer upstream guard blocks obfuscated loopback and IPv6 forms`() {
        val server = LocalHttpServer()
        listOf(
            "http://0x7f000001/live.ts",
            "http://0177.0.0.1/live.ts",
            "http://[::1]:8080/live.ts",
            "http://[fe80::1]/live.ts",
            "http://[::ffff:127.0.0.1]/live.ts",
            "http://0.0.0.0/live.ts",
            "http://foo.localhost/live.ts",
            "http://metadata.google.internal/computeMetadata/v1/"
        ).forEach { assertFalse("deveria bloquear $it", server.isAllowedUpstreamUrl(it)) }
        assertTrue(server.isAllowedUpstreamUrl("http://192.168.0.10:9981/stream/channel/1"))
        assertTrue(server.isAllowedUpstreamUrl("https://cdn.example.com/live/1.m3u8"))
    }
}
