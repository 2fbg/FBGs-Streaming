package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.utils.ObservabilityLogger
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ObservabilityLoggerTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        ObservabilityLogger.resetForTesting()
        ObservabilityLogger.clear(context)
    }

    @After
    fun tearDown() {
        ObservabilityLogger.resetForTesting()
        ObservabilityLogger.clear(context)
    }

    @Test
    fun `structured jsonl redacts credentials, tokens, cookies, pin and private playlists before persistence and export`() {
        ObservabilityLogger.init(context)
        ObservabilityLogger.logEvent(
            event = "login_error",
            phase = "auth",
            severity = ObservabilityLogger.Severity.ERROR,
            screen = "login",
            message = "Erro em https://user1:mySecretPass@iptv.example.com/get.php?username=user1&password=PassQuery99&token=tokABC Authorization: Bearer jwtToken12345 Cookie: sid=cookieSecret",
            meta = mapOf(
                "password" to "MetaSecret!",
                "pin" to "7890",
                "token" to "tokMeta999",
                "authorization" to "Bearer secretHeader",
                "cookie" to "session=xyz",
                "url" to "https://u:p@portal.example.org/live/u/p/101.ts?password=abc",
                "playlist" to "#EXTM3U\n#EXTINF:-1,Canal\nhttp://u:p@host/1.ts"
            )
        )
        ObservabilityLogger.flushNow()

        val exported = ObservabilityLogger.exportJsonl(context)
        assertTrue(exported.isNotBlank())

        val lines = exported.lines().filter { it.isNotBlank() }
        val loginLine = lines.first { it.contains("\"login_error\"") }
        val json = JSONObject(loginLine)
        assertEquals(ObservabilityLogger.SCHEMA_VERSION, json.getInt("schemaVersion"))
        assertEquals("android", json.getString("platform"))
        assertEquals("login", json.getString("screen"))
        assertEquals("auth", json.getString("phase"))
        assertTrue(json.getString("tsUtc").endsWith("Z"))
        assertTrue(json.getString("sessionId").startsWith("ses_"))

        assertFalse(exported.contains("mySecretPass"))
        assertFalse(exported.contains("PassQuery99"))
        assertFalse(exported.contains("tokABC"))
        assertFalse(exported.contains("jwtToken12345"))
        assertFalse(exported.contains("cookieSecret"))
        assertFalse(exported.contains("MetaSecret!"))
        assertFalse(exported.contains("7890"))
        assertFalse(exported.contains("tokMeta999"))
        assertFalse(exported.contains("secretHeader"))
        assertFalse(exported.contains("#EXTM3U"))
        assertTrue(exported.contains("portal.example.org"))
    }

    @Test
    fun `trace level expires automatically after configured ttl and diagnostic bundle writes redacted file`() {
        var fakeNow = 1_700_000_000_000L
        ObservabilityLogger.wallClockProvider = { fakeNow }
        ObservabilityLogger.init(context)

        ObservabilityLogger.enableTemporaryTrace(45)
        assertEquals(ObservabilityLogger.Level.TRACE, ObservabilityLogger.getLevel())
        assertEquals(45, ObservabilityLogger.getTraceRemainingMinutes())

        // Avança 46 minutos: TRACE deve expirar automaticamente para NORMAL
        fakeNow += 46L * 60_000L
        assertEquals(ObservabilityLogger.Level.NORMAL, ObservabilityLogger.getLevel())
        assertEquals(0, ObservabilityLogger.getTraceRemainingMinutes())

        val diagFile = ObservabilityLogger.exportDiagnosticFile(
            context,
            crashLog = "Falha com token=superSecretToken123"
        )
        assertTrue(diagFile.exists())
        val content = diagFile.readText(Charsets.UTF_8)
        assertTrue(content.contains("MK21 ANDROID DIAGNOSTIC BUNDLE"))
        assertFalse(content.contains("superSecretToken123"))
        diagFile.delete()
    }
}
