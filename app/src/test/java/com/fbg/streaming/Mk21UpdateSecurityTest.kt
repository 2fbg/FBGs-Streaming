package com.fbg.streaming

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.AppViewModel
import com.fbg.streaming.security.ApkSignatureVerifier
import com.fbg.streaming.security.ApkVerificationResult
import com.fbg.streaming.update.Mk21UpdateManager
import com.fbg.streaming.update.UpdateInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
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

    @Test
    fun `AppViewModel checkForUpdates via Mk21UpdateManager rejects untrusted manifest and announces manual download when pin is empty`() = runBlocking {
        val app = context.applicationContext as android.app.Application
        val vmUntrusted = AppViewModel(app)
        vmUntrusted.expectedCertPinOverride = ""
        val badManifest = """
            {
              "versionCode": 9999,
              "versionName": "9.9.9",
              "apkUrl": "https://evil.example.org/mk21.apk",
              "apkSha256": "${"a".repeat(64)}"
            }
        """.trimIndent()
        vmUntrusted.updateManagerFactory = { ctx ->
            Mk21UpdateManager(ctx, httpClient = clientReturning(200, badManifest), minApkSizeBytes = 10L)
        }
        vmUntrusted.checkForUpdates()
        val errState = withTimeout(5_000) {
            vmUntrusted.updateCheckState.first {
                it is AppViewModel.UpdateCheckState.Error ||
                    it is AppViewModel.UpdateCheckState.Available ||
                    it is AppViewModel.UpdateCheckState.UpToDate
            }
        }
        assertTrue(errState is AppViewModel.UpdateCheckState.Error)

        val vmValid = AppViewModel(app)
        vmValid.expectedCertPinOverride = ""
        val validManifest = """
            {
              "versionCode": 9999,
              "versionName": "9.9.9",
              "apkUrl": "https://github.com/2fbg/FBGs-Streaming/releases/download/v9.9.9/mk21.apk",
              "apkSha256": "${"a".repeat(64)}",
              "sizeBytes": 5777533,
              "changelog": "Release de teste"
            }
        """.trimIndent()
        vmValid.updateManagerFactory = { ctx ->
            Mk21UpdateManager(
                ctx,
                httpClient = clientReturning(200, validManifest),
                expectedCertSha256 = vmValid.effectiveExpectedCertPin(),
                minApkSizeBytes = 10L
            )
        }
        vmValid.checkForUpdates()
        val availState = withTimeout(5_000) {
            vmValid.updateCheckState.first {
                it is AppViewModel.UpdateCheckState.Available ||
                    it is AppViewModel.UpdateCheckState.Error
            }
        }
        assertTrue(availState is AppViewModel.UpdateCheckState.Available)
        val info = (availState as AppViewModel.UpdateCheckState.Available).info
        assertTrue(info.body.contains("EXPECTED_CERT_SHA256"))

        // Mesmo com manifesto disponível, instalação in-app falha fechado enquanto pin estiver vazio
        vmValid.startInAppDownloadAndInstall(context, info.downloadUrl, info.expectedSha256, info.expectedSizeBytes)
        val blockedState = withTimeout(5_000) {
            vmValid.updateCheckState.first {
                it is AppViewModel.UpdateCheckState.Error
            }
        }
        assertTrue(
            (blockedState as AppViewModel.UpdateCheckState.Error)
                .error.contains("EXPECTED_CERT_SHA256")
        )
    }

    @Test
    fun `AppViewModel download path enforces allowlist, sha256, cert pin mismatch and blocks installApk without pin`() = runBlocking {
        val app = context.applicationContext as android.app.Application
        val vm = AppViewModel(app)
        val pinnedHex = "f".repeat(64)
        vm.expectedCertPinOverride = pinnedHex

        val payloadBytes = "fake-apk-payload-for-cert-pin-check".toByteArray()
        val payloadSha = MessageDigest.getInstance("SHA-256")
            .digest(payloadBytes)
            .joinToString("") { "%02x".format(it) }

        val apkClient = OkHttpClient.Builder()
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

        vm.updateManagerFactory = { ctx ->
            Mk21UpdateManager(
                context = ctx,
                httpClient = apkClient,
                expectedCertSha256 = vm.effectiveExpectedCertPin(),
                minApkSizeBytes = 10L
            )
        }

        // 1. Host fora da allowlist falha fechado
        vm.startInAppDownloadAndInstall(context, "https://attacker.invalid/mk21.apk", payloadSha, payloadBytes.size.toLong())
        val hostError = withTimeout(5_000) {
            vm.updateCheckState.first {
                it is AppViewModel.UpdateCheckState.Error
            }
        }
        assertTrue(hostError is AppViewModel.UpdateCheckState.Error)

        // 2. SHA-256 inválido falha fechado
        vm.startInAppDownloadAndInstall(
            context,
            "https://github.com/2fbg/FBGs-Streaming/releases/download/v2.8.0/mk21.apk",
            "invalid-sha",
            0L
        )
        val shaError = withTimeout(5_000) {
            vm.updateCheckState.first {
                it is AppViewModel.UpdateCheckState.Error
            }
        }
        assertTrue(shaError is AppViewModel.UpdateCheckState.Error)

        // 3. Pin configurado + payload que não passa na verificação de assinatura deve rejeitar e apagar arquivo
        vm.startInAppDownloadAndInstall(
            context,
            "https://github.com/2fbg/FBGs-Streaming/releases/download/v2.8.0/mk21.apk",
            payloadSha,
            payloadBytes.size.toLong()
        )
        val certError = withTimeout(5_000) {
            vm.updateCheckState.first {
                it is AppViewModel.UpdateCheckState.Error &&
                    it.error.contains("certificado", ignoreCase = true)
            }
        }
        assertTrue(certError is AppViewModel.UpdateCheckState.Error)
        val updatesDir = File(context.externalCacheDir ?: context.cacheDir, "updates")
        assertFalse(File(updatesDir, "mk21_2.8.0.apk").exists())

        // 4. installApk com pin vazio deve recusar sem disparar Intent de instalação
        vm.expectedCertPinOverride = ""
        val tempApk = File(context.cacheDir, "dummy-no-pin.apk").apply { writeBytes(payloadBytes) }
        try {
            vm.installApk(context, tempApk)
            assertTrue(vm.updateCheckState.value is AppViewModel.UpdateCheckState.Error)
            val nextIntent = org.robolectric.Shadows.shadowOf(app).nextStartedActivity
            assertNull(nextIntent)
        } finally {
            tempApk.delete()
        }
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
