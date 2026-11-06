package com.example

import com.example.data.utils.ExternalPlayerHelper
import com.example.data.utils.ServerTextParser
import org.junit.Assert.*
import org.junit.Test

class ServerTextParserTest {

    @Test
    fun testParseWhatsAppFormattedServers() {
        val sampleWhatsAppText = """
🟢 *Link VLOG(M3U):* http://lumixx21.org/get.php?username=demo-user&password=demo-pass&type=m3u_plus&output=mpegts
⚪ *Link LUB TV (M3U):* http://pottermax.sbs/get.php?username=demo-user&password=demo-pass&type=m3u_plus&output=mpegts
🔴 *Link CINELON21 (M3U):* http://powerex.lat/get.php?username=demo-user&password=demo-pass&type=m3u_plus&output=mpegts
        """.trimIndent()

        val parsed = ServerTextParser.parse(sampleWhatsAppText)

        assertEquals(3, parsed.size)

        // Verificação Servidor 1 (VLOG)
        assertEquals("VLOG", parsed[0].name)
        assertEquals("http://lumixx21.org", parsed[0].baseUrl)
        assertEquals("demo-user", parsed[0].username)
        assertEquals("demo-pass", parsed[0].password)

        // Verificação Servidor 2 (LUB TV)
        assertEquals("LUB TV", parsed[1].name)
        assertEquals("http://pottermax.sbs", parsed[1].baseUrl)
        assertEquals("demo-user", parsed[1].username)
        assertEquals("demo-pass", parsed[1].password)

        // Verificação Servidor 3 (CINELON21)
        assertEquals("CINELON21", parsed[2].name)
        assertEquals("http://powerex.lat", parsed[2].baseUrl)
        assertEquals("demo-user", parsed[2].username)
        assertEquals("demo-pass", parsed[2].password)
    }

    @Test
    fun testConversionToServerProfiles() {
        val sampleWhatsAppText = "🟢 *Link MK21 PRÓ:* http://app.tigerouro.shop/get.php"
        val parsed = ServerTextParser.parse(sampleWhatsAppText)

        assertEquals(1, parsed.size)
        val profiles = ServerTextParser.toServerProfiles(parsed, "globalUser", "globalPass")

        assertEquals(1, profiles.size)
        assertEquals("MK21 PRÓ", profiles[0].name)
        assertEquals("http://app.tigerouro.shop", profiles[0].baseUrl)
        assertEquals("globalUser", profiles[0].username)
        assertEquals("globalPass", profiles[0].password)
        assertTrue(profiles[0].isActive)
    }

    @Test
    fun testExtractsUsernameAndPasswordFromQueryString() {
        val message = """
            *🌐Bem vindo a MK21 Plataformas.*
            *✅ Usuário:* demo-user
            *✅ Senha:* demo-pass

            *🆔URL XCIPTV SERVIDORES:*
            *VLOG:* http://lumixx21.org/get.php?username=demo-user&password=demo-pass
        """.trimIndent()

        val parsed = ServerTextParser.parse(message)
        assertEquals(1, parsed.size)
        assertEquals("VLOG", parsed[0].name)
        assertEquals("demo-user", parsed[0].username)
        assertEquals("demo-pass", parsed[0].password)
    }

    @Test
    fun testIgnoresNonServerUrls() {
        val textWithIrrelevantLinks = """
Baixe o app na Play Store: https://play.google.com/store/apps/details?id=com.app
Entre no grupo: https://wa.me/5511999999999
🟢 *Link VLOG:* http://lumixx21.org
        """.trimIndent()

        val parsed = ServerTextParser.parse(textWithIrrelevantLinks)

        assertEquals(1, parsed.size)
        assertEquals("VLOG", parsed[0].name)
        assertEquals("http://lumixx21.org", parsed[0].baseUrl)
    }

    @Test
    fun testParseFullWhatsAppMessageWithNewUrls() {
        val message = """
*🌐Bem vindo a MK21 Plataformas.*
*✅ Usuário:* demo-user
*✅ Senha:* demo-pass

*🆔URL XCIPTV SERVIDORES:* 
*VLOG:* http://lumixx21.org
*TANNIX:*  http://assist.new21vod.xyz
*LUB TV:*  http://pottermax.sbs
*MK21 PRÓ:*  http://magmapuro.shop
*CB6000:*  http://cdn.caterlune.top
*CINELON:*  http://powerex.lat
*MULTT TV:*  http://dali-as.skin
        """.trimIndent()

        val parsed = ServerTextParser.parse(message)
        assertEquals(7, parsed.size)

        assertEquals("VLOG", parsed[0].name)
        assertEquals("http://lumixx21.org", parsed[0].baseUrl)
        assertEquals("demo-user", parsed[0].username)
        assertEquals("demo-pass", parsed[0].password)

        assertEquals("TANNIX", parsed[1].name)
        assertEquals("http://assist.new21vod.xyz", parsed[1].baseUrl)

        assertEquals("LUB TV", parsed[2].name)
        assertEquals("http://pottermax.sbs", parsed[2].baseUrl)

        assertEquals("MK21 PRÓ", parsed[3].name)
        assertEquals("http://magmapuro.shop", parsed[3].baseUrl)

        assertEquals("CB6000", parsed[4].name)
        assertEquals("http://cdn.caterlune.top", parsed[4].baseUrl)

        assertEquals("CINELON21", parsed[5].name)
        assertEquals("http://powerex.lat", parsed[5].baseUrl)

        assertEquals("MULTT TV", parsed[6].name)
        assertEquals("http://dali-as.skin", parsed[6].baseUrl)
    }

    @Test
    fun testInfersNamesFromUpdatedServerHosts() {
        val text = """
http://lumixx21.org/get.php
http://assist.new21vod.xyz/get.php
http://magmapuro.shop/get.php
http://powerex.lat/get.php
        """.trimIndent()

        val parsed = ServerTextParser.parse(text)

        assertEquals(listOf("VLOG", "TANNIX", "MK21 PRÓ", "CINELON21"), parsed.map { it.name })
    }

    @Test
    fun testExternalPlayerPackages() {
        assertEquals("org.videolan.vlc", ExternalPlayerHelper.getPackageName(ExternalPlayerHelper.PLAYER_VLC))
        assertEquals("com.mxtech.videoplayer.ad", ExternalPlayerHelper.getPackageName(ExternalPlayerHelper.PLAYER_MX))
        assertEquals("com.mxtech.videoplayer.pro", ExternalPlayerHelper.getPackageName(ExternalPlayerHelper.PLAYER_MX_PRO))
        assertEquals("com.brouken.player", ExternalPlayerHelper.getPackageName(ExternalPlayerHelper.PLAYER_JUST_PLAYER))
        assertEquals("org.xbmc.kodi", ExternalPlayerHelper.getPackageName(ExternalPlayerHelper.PLAYER_KODI))
        assertNull(ExternalPlayerHelper.getPackageName(ExternalPlayerHelper.PLAYER_ANY))
    }
}
