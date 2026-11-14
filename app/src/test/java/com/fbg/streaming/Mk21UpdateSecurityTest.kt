package com.fbg.streaming

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fbg.streaming.security.ApkSignatureVerifier
import com.fbg.streaming.security.ApkVerificationResult
import com.fbg.streaming.update.Mk21UpdateManager
import com.fbg.streaming.update.UpdateInfo
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Mk21UpdateSecurityTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `isSignatureValid fails closed when expected fingerprint is blank or malformed`() {
        assertFalse(ApkSignatureVerifier.isSignatureValid(context, ""))
        assertFalse(ApkSignatureVerifier.isSignatureValid(context, "   "))
        assertFalse(ApkSignatureVerifier.isSignatureValid(context, "not-a-valid-sha256"))
    }

    @Test
    fun `verifyApkArchiveSignature rejects missing file and unparsable archive when pin is configured`() {
        val missing = File(context.cacheDir, "does-not-exist.apk")
        val missingResult = ApkSignatureVerifier.verifyApkArchiveSignature(context, missing)
        assertTrue(missingResult is ApkVerificationResult.Rejected)

        val fakeApk = File(context.cacheDir, "fake-corrupt.apk").apply {
            writeText("not-a-zip-apk")
        }
        try {
            val pinnedResult = ApkSignatureVerifier.verifyApkArchiveSignature(
                context = context,
                apkFile = fakeApk,
                expectedCertSha256 = "a".repeat(64)
            )
            assertTrue(pinnedResult is ApkVerificationResult.Rejected)
        } finally {
            fakeApk.delete()
        }
    }

    @Test
    fun `isAllowedUpdateUrl enforces https and allowlisted hosts`() {
        val manager = Mk21UpdateManager(context)
        assertTrue(manager.isAllowedUpdateUrl("https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/mk21.apk"))
        assertTrue(manager.isAllowedUpdateUrl("https://github.com/2fbg/FBGs-Streaming/releases/download/v2.7.9/mk21.apk"))
        assertFalse(manager.isAllowedUpdateUrl("http://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/mk21.apk"))
        assertFalse(manager.isAllowedUpdateUrl("https://evil.example.com/mk21.apk"))
        assertFalse(manager.isAllowedUpdateUrl("file:///sdcard/mk21.apk"))
    }

    @Test
    fun `checkForUpdates fails closed when manifest omits sha256 or uses untrusted host`() = runTest {
        val manifestWithoutHash = """
            {
              "versionCode": 999,
              "versionName": "9.9.9",
              "apkUrl": "https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/mk21.apk",
              "apkSha256": ""
            }
        """.trimIndent()
        val clientNoHash = clientReturning(200, manifestWithoutHash)
        val managerNoHash = Mk21UpdateManager(context, httpClient = clientNoHash)
        assertNull(managerNoHash.checkForUpdates(279))

        val validSha = "a".repeat(64)
        val manifestBadHost = """
            {
              "versionCode": 999,
              "versionName": "9.9.9",
              "apkUrl": "https://attacker.invalid/mk21.apk",
              "apkSha256": "$validSha"
            }
        """.trimIndent()
        val managerBadHost = Mk21UpdateManager(context, httpClient = clientReturning(200, manifestBadHost))
        assertNull(managerBadHost.checkForUpdates(279))

        val manifestValid = """
            {
              "versionCode": 999,
              "versionName": "9.9.9",
              "apkUrl": "https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/mk21.apk",
              "apkSha256": "$validSha",
              "changelog": "Correção crítica"
            }
        """.trimIndent()
        val managerValid = Mk21UpdateManager(context, httpClient = clientReturning(200, manifestValid))
        val info = managerValid.checkForUpdates(279)
        assertNotNull(info)
        assertEquals(999, info?.versionCode)
        assertEquals(validSha, info?.sha256)
    }

    @Test
    fun `downloadAndInstallApk rejects empty sha256, unsafe redirects, and sha256 mismatch`() = runTest {
        val manager = Mk21UpdateManager(
            context = context,
            httpClient = clientReturning(200, "dummy"),
            minApkSizeBytes = 10L
        )

        // 1. SHA-256 vazio deve falhar fechado antes de baixar
        val emptyHashResult = manager.downloadAndInstallApk(
            updateInfo = UpdateInfo(
                versionCode = 280,
                versionName = "2.8.0",
                apkUrl = "https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/mk21.apk",
                sha256 = "",
                changelog = ""
            ),
            onProgress = {},
            autoLaunchInstaller = false
        )
        assertFalse(emptyHashResult)

        // 2. Redirect para host fora da allowlist deve ser bloqueado
        val redirectClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Found")
                    .header("Location", "https://evil.example.org/payload.apk")
                    .body("".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()
        val redirectManager = Mk21UpdateManager(
            context = context,
            httpClient = redirectClient,
            minApkSizeBytes = 10L
        )
        val redirectResult = redirectManager.downloadAndInstallApk(
            updateInfo = UpdateInfo(
                versionCode = 280,
                versionName = "2.8.0",
                apkUrl = "https://github.com/2fbg/FBGs-Streaming/releases/download/v2.8.0/mk21.apk",
                sha256 = "b".repeat(64),
                changelog = ""
            ),
            onProgress = {},
            autoLaunchInstaller = false
        )
        assertFalse(redirectResult)

        // 3. Hash divergente deve rejeitar e remover arquivo parcial
        val payloadBytes = "fake-apk-binary-content-123456".toByteArray()
        val mismatchClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(payloadBytes.toResponseBody("application/vnd.android.package-archive".toMediaType()))
                    .build()
            }
            .build()
        val mismatchManager = Mk21UpdateManager(
            context = context,
            httpClient = mismatchClient,
            minApkSizeBytes = 10L
        )
        val mismatchResult = mismatchManager.downloadAndInstallApk(
            updateInfo = UpdateInfo(
                versionCode = 280,
                versionName = "2.8.0",
                apkUrl = "https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/mk21.apk",
                sha256 = "c".repeat(64),
                changelog = ""
            ),
            onProgress = {},
            autoLaunchInstaller = false
        )
        assertFalse(mismatchResult)

        val updatesDir = File(context.externalCacheDir ?: context.cacheDir, "updates")
        assertFalse(File(updatesDir, "mk21_2.8.0.apk").exists())
        assertFalse(File(updatesDir, "mk21_2.8.0.apk.part").exists())

        // 4. Hash válido sem pin de certificado explícito aceita delegação ao sistema quando arquivo não é ZIP Android real
        val realSha = MessageDigest.getInstance("SHA-256")
            .digest(payloadBytes)
            .joinToString("") { "%02x".format(it) }
        val validResult = mismatchManager.downloadAndInstallApk(
            updateInfo = UpdateInfo(
                versionCode = 280,
                versionName = "2.8.0",
                apkUrl = "https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/mk21.apk",
                sha256 = realSha,
                changelog = ""
            ),
            onProgress = {},
            autoLaunchInstaller = false
        )
        assertTrue(validResult)
        File(updatesDir, "mk21_2.8.0.apk").delete()
    }

    private fun clientReturning(code: Int, bodyText: String): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("HTTP $code")
                    .body(bodyText.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
    }
}
