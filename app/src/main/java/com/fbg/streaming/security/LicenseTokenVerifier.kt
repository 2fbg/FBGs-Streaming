package com.fbg.streaming.security

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

data class LicenseInfo(
    val deviceId: String,
    val tier: String,
    val issuedAtEpoch: Long,
    val expiresAtEpoch: Long,
    val orderCode: String
)

/**
 * Verificador offline de tokens de licença assinados pelo servidor com ECDSA P-256 (SHA256withECDSA).
 *
 * Formato esperado: `MKL1.<payload_b64url>.<assinatura_der_b64url>`.
 * Apenas a chave pública X.509 é embutida no APK via BuildConfig; a chave privada permanece
 * exclusivamente no servidor (Vercel) para impedir keygens locais.
 */
object LicenseTokenVerifier {

    private const val TOKEN_PREFIX = "MKL1"
    private const val B64_URL_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    private const val PEM_BEGIN = "-----BEGIN PUBLIC KEY-----"
    private const val PEM_END = "-----END PUBLIC KEY-----"

    fun verify(
        token: String,
        deviceId: String,
        publicKeyB64: String,
        nowEpoch: Long = System.currentTimeMillis() / 1000L
    ): LicenseInfo? {
        val trimmedToken = token.trim()
        val trimmedDeviceId = deviceId.trim()
        val trimmedKey = publicKeyB64.trim()
        if (trimmedToken.isEmpty() || trimmedDeviceId.isEmpty() || trimmedKey.isEmpty()) {
            return null
        }

        val parts = trimmedToken.split('.')
        if (parts.size != 3 || parts[0] != TOKEN_PREFIX) return null
        if (parts[1].isEmpty() || parts[2].isEmpty()) return null

        val payloadBytes = decodeBase64Url(parts[1]) ?: return null
        val signatureBytes = decodeBase64Url(parts[2]) ?: return null
        val publicKey = parsePublicKey(trimmedKey) ?: return null

        val signedBytes = "${parts[0]}.${parts[1]}".toByteArray(Charsets.UTF_8)
        val signatureValid = runCatching {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(signedBytes)
                verify(signatureBytes)
            }
        }.getOrDefault(false)

        if (!signatureValid) return null

        return runCatching {
            val json = JSONObject(String(payloadBytes, Charsets.UTF_8))
            val tokenDeviceId = json.optString("d", "").trim()
            val tier = json.optString("t", "pro").trim().ifEmpty { "pro" }
            val iat = json.optLong("iat", 0L)
            val exp = json.optLong("exp", 0L)
            val orderCode = json.optString("o", "").trim()

            if (tokenDeviceId.isEmpty() || !tokenDeviceId.equals(trimmedDeviceId, ignoreCase = true)) {
                return@runCatching null
            }
            if (exp <= nowEpoch) {
                return@runCatching null
            }

            LicenseInfo(
                deviceId = tokenDeviceId,
                tier = tier,
                issuedAtEpoch = iat,
                expiresAtEpoch = exp,
                orderCode = orderCode
            )
        }.getOrNull()
    }

    private fun decodeBase64Url(encoded: String): ByteArray? {
        return runCatching {
            Base64.decode(encoded, B64_URL_FLAGS)
        }.getOrNull()
    }

    /**
     * Aceita tanto a chave X.509 DER em Base64 puro quanto o arquivo PEM inteiro codificado
     * em Base64 (`base64 -w0 license-public.pem`) ou texto PEM direto.
     */
    private fun parsePublicKey(rawKey: String): PublicKey? {
        return runCatching {
            val derBytes = if (rawKey.contains(PEM_BEGIN)) {
                extractDerFromPem(rawKey)
            } else {
                val firstDecode = Base64.decode(rawKey, Base64.DEFAULT)
                val maybePem = String(firstDecode, Charsets.UTF_8)
                if (maybePem.contains(PEM_BEGIN)) {
                    extractDerFromPem(maybePem)
                } else {
                    firstDecode
                }
            }
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(derBytes))
        }.getOrNull()
    }

    private fun extractDerFromPem(pem: String): ByteArray {
        val cleaned = pem
            .replace(PEM_BEGIN, "")
            .replace(PEM_END, "")
            .replace("\\s".toRegex(), "")
        return Base64.decode(cleaned, Base64.DEFAULT)
    }
}
