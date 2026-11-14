package com.fbg.streaming.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.fbg.streaming.security.ApkSignatureVerifier
import com.fbg.streaming.security.ApkVerificationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val changelog: String
)

class Mk21UpdateManager(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val versionJsonUrl: String = DEFAULT_VERSION_JSON_URL,
    private val allowedHosts: Set<String> = DEFAULT_ALLOWED_HOSTS,
    private val expectedCertSha256: String? = null,
    private val minApkSizeBytes: Long = DEFAULT_MIN_APK_SIZE_BYTES
) {

    companion object {
        private const val TAG = "Mk21UpdateManager"
        private const val MAX_REDIRECTS = 5
        const val DEFAULT_MIN_APK_SIZE_BYTES = 1_000_000L
        const val DEFAULT_VERSION_JSON_URL =
            "https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/version.json"

        val DEFAULT_ALLOWED_HOSTS: Set<String> = setOf(
            "raw.githubusercontent.com",
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com"
        )

        private val SHA256_HEX_REGEX = Regex("^[a-fA-F0-9]{64}$")
        private val SAFE_VERSION_CHARS = Regex("[^A-Za-z0-9._-]")

        fun isValidSha256(sha256: String): Boolean {
            return SHA256_HEX_REGEX.matches(sha256.trim())
        }
    }

    fun isAllowedUpdateUrl(rawUrl: String): Boolean {
        val parsed = rawUrl.trim().toHttpUrlOrNull() ?: return false
        if (!parsed.scheme.equals("https", ignoreCase = true)) return false
        val host = parsed.host.lowercase()
        return allowedHosts.any { it.equals(host, ignoreCase = true) }
    }

    suspend fun checkForUpdates(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!isAllowedUpdateUrl(versionJsonUrl)) {
            Log.e(TAG, "URL do manifesto de update rejeitada pela política HTTPS/allowlist: $versionJsonUrl")
            return@withContext null
        }

        executeWithValidatedRedirects(versionJsonUrl).use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val remoteVersionCode = json.optInt("versionCode", currentVersionCode)
            if (remoteVersionCode <= currentVersionCode) return@withContext null

            val versionName = json.optString("versionName", json.optString("version", "")).trim()
            val apkUrl = json.optString(
                "apkUrl",
                json.optString("downloadUrl", "https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/mk21.apk")
            ).trim()
            val sha256 = json.optString("apkSha256", json.optString("sha256", "")).trim()
            val changelog = json.optString(
                "changelog",
                json.optString("releaseNotes", "Melhorias de estabilidade e performance.")
            )

            // Falha fechado: não anuncia update sem URL HTTPS confiável ou sem SHA-256 válido.
            if (versionName.isBlank() || !isAllowedUpdateUrl(apkUrl) || !isValidSha256(sha256)) {
                Log.w(TAG, "Manifesto OTA ignorado por campos de segurança ausentes/inválidos.")
                return@withContext null
            }

            UpdateInfo(
                versionCode = remoteVersionCode,
                versionName = versionName,
                apkUrl = apkUrl,
                sha256 = sha256.lowercase(),
                changelog = changelog
            )
        }
    }

    suspend fun downloadAndInstallApk(
        updateInfo: UpdateInfo,
        autoLaunchInstaller: Boolean = true,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val normalizedSha = updateInfo.sha256.trim().lowercase()
        if (!isValidSha256(normalizedSha)) {
            Log.e(TAG, "Atualização abortada: manifesto sem SHA-256 válido.")
            return@withContext false
        }
        if (!isAllowedUpdateUrl(updateInfo.apkUrl)) {
            Log.e(TAG, "Atualização abortada: URL de APK fora da política HTTPS/allowlist.")
            return@withContext false
        }

        val updateDir = File(context.externalCacheDir ?: context.cacheDir, "updates").apply { mkdirs() }
        val safeVersion = SAFE_VERSION_CHARS.replace(updateInfo.versionName, "_").ifBlank { "latest" }
        val partFile = File(updateDir, "mk21_${safeVersion}.apk.part")
        val apkFile = File(updateDir, "mk21_${safeVersion}.apk")
        partFile.delete()
        apkFile.delete()

        try {
            executeWithValidatedRedirects(updateInfo.apkUrl).use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Falha HTTP ao baixar atualização: ${response.code}")
                    return@withContext false
                }
                val body = response.body ?: return@withContext false
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                onProgress(((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
            }

            if (partFile.length() < minApkSizeBytes) {
                Log.e(TAG, "APK baixado abaixo do tamanho mínimo esperado (${partFile.length()} bytes).")
                partFile.delete()
                return@withContext false
            }

            val fileHash = calculateSha256(partFile)
            if (!fileHash.equals(normalizedSha, ignoreCase = true)) {
                Log.e(TAG, "Hash SHA-256 divergente para atualização: esperado=$normalizedSha obtido=$fileHash")
                partFile.delete()
                return@withContext false
            }

            if (!partFile.renameTo(apkFile)) {
                partFile.copyTo(apkFile, overwrite = true)
                partFile.delete()
            }

            when (val sigResult = ApkSignatureVerifier.verifyApkArchiveSignature(context, apkFile, expectedCertSha256)) {
                is ApkVerificationResult.Verified -> Unit
                is ApkVerificationResult.SystemDelegate -> {
                    Log.w(TAG, "getPackageArchiveInfo indisponível; delegando verificação de assinatura ao instalador do Android.")
                }
                is ApkVerificationResult.Rejected -> {
                    Log.e(TAG, "APK rejeitado na verificação de assinatura: ${sigResult.reason}")
                    apkFile.delete()
                    return@withContext false
                }
            }

            if (autoLaunchInstaller) {
                installApk(apkFile)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro durante download/validação da atualização OTA", e)
            partFile.delete()
            apkFile.delete()
            false
        }
    }

    private fun executeWithValidatedRedirects(initialUrl: String): Response {
        val strictClient = httpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        var currentUrl = initialUrl
        var redirects = 0
        while (true) {
            if (!isAllowedUpdateUrl(currentUrl)) {
                throw SecurityException("URL bloqueada pela política de atualização HTTPS: $currentUrl")
            }
            val request = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", "MK21-Android")
                .build()
            val response = strictClient.newCall(request).execute()
            if (response.isRedirect) {
                val location = response.header("Location")
                val baseHttpUrl = currentUrl.toHttpUrlOrNull()
                val resolvedUrl = if (!location.isNullOrBlank() && baseHttpUrl != null) {
                    baseHttpUrl.resolve(location)?.toString()
                } else {
                    null
                }
                response.close()
                if (resolvedUrl.isNullOrBlank() || !isAllowedUpdateUrl(resolvedUrl)) {
                    throw SecurityException("Redirecionamento OTA bloqueado: $location")
                }
                redirects++
                if (redirects > MAX_REDIRECTS) {
                    throw SecurityException("Excesso de redirecionamentos durante atualização OTA.")
                }
                currentUrl = resolvedUrl
                continue
            }
            return response
        }
    }

    private fun installApk(apkFile: File) {
        val authority = "${context.packageName}.fileprovider"
        val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = fis.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}