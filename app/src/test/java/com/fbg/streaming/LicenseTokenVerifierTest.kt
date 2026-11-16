package com.fbg.streaming

import android.util.Base64
import com.fbg.streaming.security.LicenseTokenVerifier
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LicenseTokenVerifierTest {

    private val urlFlags = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    private fun generateEcKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        return kpg.generateKeyPair()
    }

    private fun signToken(
        keyPair: KeyPair,
        deviceId: String,
        tier: String = "pro",
        iat: Long = 1_700_000_000L,
        exp: Long = 1_800_000_000L,
        orderCode: String = "MK-TEST-0001"
    ): String {
        val payloadJson = JSONObject().apply {
            put("d", deviceId)
            put("t", tier)
            put("iat", iat)
            put("exp", exp)
            put("o", orderCode)
        }.toString()
        val payloadB64 = Base64.encodeToString(payloadJson.toByteArray(Charsets.UTF_8), urlFlags)
        val body = "MKL1.$payloadB64"
        val sigBytes = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(body.toByteArray(Charsets.UTF_8))
            sign()
        }
        val sigB64 = Base64.encodeToString(sigBytes, urlFlags)
        return "$body.$sigB64"
    }

    @Test
    fun `valid token is accepted and returns license info`() {
        val keyPair = generateEcKeyPair()
        val pubB64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        val deviceId = "AA:BB:CC:DD:EE:FF"
        val now = 1_750_000_000L
        val exp = 1_781_536_000L

        val token = signToken(keyPair, deviceId = deviceId, tier = "pro", iat = now, exp = exp, orderCode = "ORD-42")
        val info = LicenseTokenVerifier.verify(
            token = token,
            deviceId = deviceId,
            publicKeyB64 = pubB64,
            nowEpoch = now
        )

        assertNotNull(info)
        assertEquals(deviceId, info?.deviceId)
        assertEquals("pro", info?.tier)
        assertEquals(now, info?.issuedAtEpoch)
        assertEquals(exp, info?.expiresAtEpoch)
        assertEquals("ORD-42", info?.orderCode)
    }

    @Test
    fun `valid token wrapped as base64 PEM is also accepted`() {
        val keyPair = generateEcKeyPair()
        val derB64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        val pem = "-----BEGIN PUBLIC KEY-----\n$derB64\n-----END PUBLIC KEY-----\n"
        val pemB64 = Base64.encodeToString(pem.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val deviceId = "11:22:33:44:55:66"
        val now = 1_750_000_000L

        val token = signToken(keyPair, deviceId = deviceId, iat = now, exp = now + 86400)
        val info = LicenseTokenVerifier.verify(token, deviceId, pemB64, nowEpoch = now)
        assertNotNull(info)
    }

    @Test
    fun `altered signature is rejected`() {
        val keyPair = generateEcKeyPair()
        val pubB64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        val deviceId = "AA:BB:CC:DD:EE:FF"
        val now = 1_750_000_000L

        val token = signToken(keyPair, deviceId = deviceId, iat = now, exp = now + 86400)
        val parts = token.split('.').toMutableList()
        val sigChars = parts[2].toCharArray()
        sigChars[sigChars.lastIndex] = if (sigChars.last() == 'A') 'B' else 'A'
        parts[2] = String(sigChars)
        val tamperedToken = parts.joinToString(".")

        assertNull(LicenseTokenVerifier.verify(tamperedToken, deviceId, pubB64, nowEpoch = now))
    }

    @Test
    fun `mismatched deviceId is rejected`() {
        val keyPair = generateEcKeyPair()
        val pubB64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        val now = 1_750_000_000L

        val token = signToken(keyPair, deviceId = "AA:BB:CC:DD:EE:FF", iat = now, exp = now + 86400)
        assertNull(LicenseTokenVerifier.verify(token, deviceId = "11:22:33:44:55:66", publicKeyB64 = pubB64, nowEpoch = now))
    }

    @Test
    fun `expired token is rejected`() {
        val keyPair = generateEcKeyPair()
        val pubB64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        val deviceId = "AA:BB:CC:DD:EE:FF"
        val exp = 1_750_000_000L

        val token = signToken(keyPair, deviceId = deviceId, iat = exp - 86400, exp = exp)
        assertNull(LicenseTokenVerifier.verify(token, deviceId = deviceId, publicKeyB64 = pubB64, nowEpoch = exp + 1))
    }
}
