package com.fbg.streaming.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.utils.LogSanitizer
import com.example.utils.ObservabilityLogger
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
    val changelog: String,
    val expectedSizeBytes: Long = 0L,
    val title: String = "MK21 MultiServidor v$versionName"
)

sealed class UpdateCheckOutcome {
    data class Available(val info: UpdateInfo) : UpdateCheckOutcome()
    data class NotPublishedYet(
        val versionName: String,
        val fallbackReleasesUrl: String = Mk21UpdateManager.RELEASES_LATEST_URL
    ) : UpdateCheckOutcome()
    data class UpToDate(val currentVersionCode: Int) : UpdateCheckOutcome()
    data class Failure(val reason: String) : UpdateCheckOutcome()
}

sealed class DownloadVerificationResult {
    data class Success(val apkFile: File) : DownloadVerificationResult()
    data class Failure(val reason: String) : DownloadVerificationResult()
}

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
        const val RELEASES_LATEST_URL =
            "https://github.com/2fbg/FBGs-Streaming/releases/latest"

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

    fun hasConfiguredCertPin(): Boolean {
        val raw = expectedCertSha256?.trim().orEmpty()
        return raw.isNotBlank() && ApkSignatureVerifier.isValidSha256Hex(raw)
    }

    fun isAllowedUpdateUrl(rawUrl: String): Boolean {
        val parsed = rawUrl.trim().toHttpUrlOrNull() ?: return false
        if (!parsed.scheme.equals("https", ignoreCase = true)) return false
        val host = parsed.host.lowercase()
        return allowedHosts.any { it.equals(host, ignoreCase = true) }
    }

    suspend fun checkForUpdates(currentVersionCode: Int): UpdateInfo? {
        return (checkForUpdatesDetailed(currentVersionCode) as? UpdateCheckOutcome.Available)?.info
    }

    suspend fun checkForUpdatesDetailed(
        currentVersionCode: Int,
        currentVersionName: String = ""
    ): UpdateCheckOutcome = withContext(Dispatchers.IO) {
        if (!isAllowedUpdateUrl(versionJsonUrl)) {
            Log.e(TAG, "URL do manifesto de update rejeitada pela política HTTPS/allowlist: $versionJsonUrl")
            return@withContext UpdateCheckOutcome.Failure("URL do manifesto OTA fora da política HTTPS confiável.")
        }

        val cid = ObservabilityLogger.startTimer("ota_manifest")
        ObservabilityLogger.logEvent(
            event = "ota_manifest_request",
            phase = "ota",
            correlationId = cid,
            meta = mapOf("host" to LogSanitizer.extractSafeHost(versionJsonUrl), "currentVersionCode" to currentVersionCode)
        )

        try {
            executeWithValidatedRedirects(versionJsonUrl).use { response ->
                if (!response.isSuccessful) {
                    ObservabilityLogger.endTimer(
                        key = "ota_manifest",
                        event = "ota_manifest_error",
                        phase = "ota",
                        severity = ObservabilityLogger.Severity.WARN,
                        meta = mapOf("status" to response.code, "host" to LogSanitizer.extractSafeHost(versionJsonUrl))
                    )
                    return@withContext UpdateCheckOutcome.Failure("Servidor de atualização respondeu HTTP ${response.code}.")
                }
                val body = response.body?.string()
                    ?: return@withContext UpdateCheckOutcome.Failure("Manifesto de atualização vazio.")
                val json = JSONObject(body)
                val remoteVersionCode = json.optInt("versionCode", currentVersionCode)
                val versionName = json.optString("versionName", json.optString("version", "")).trim()
                ObservabilityLogger.endTimer(
                    key = "ota_manifest",
                    event = "ota_manifest_received",
                    phase = "ota",
                    meta = mapOf(
                        "remoteVersion" to versionName,
                        "remoteVersionCode" to remoteVersionCode,
                        "host" to LogSanitizer.extractSafeHost(versionJsonUrl)
                    )
                )
                val isNewerByCode = remoteVersionCode > currentVersionCode
                val isNewerByName = currentVersionName.isNotBlank() &&
                    versionName.isNotBlank() &&
                    compareVersionParts(versionName, currentVersionName) > 0

                if (!isNewerByCode && !isNewerByName) {
                    return@withContext UpdateCheckOutcome.UpToDate(currentVersionCode)
                }

                val apkUrl = json.optString(
                    "apkUrl",
                    json.optString("downloadUrl", "")
                ).trim()
                val sha256 = json.optString("apkSha256", json.optString("sha256", "")).trim()
                val expectedSizeBytes = json.optLong("sizeBytes", 0L)
                val title = json.optString("title", json.optString("name", "MK21 MultiServidor v$versionName")).trim()
                    .ifBlank { "MK21 MultiServidor v$versionName" }
                val changelog = json.optString(
                    "changelog",
                    json.optString("releaseNotes", json.optString("body", "Melhorias de estabilidade e performance."))
                )

                // Falha fechado: não anuncia update sem URL HTTPS confiável ou sem SHA-256 válido.
                if (versionName.isBlank() || !isAllowedUpdateUrl(apkUrl) || !isValidSha256(sha256)) {
                    Log.w(TAG, "Manifesto OTA ignorado por campos de segurança ausentes/inválidos.")
                    ObservabilityLogger.logEvent(
                        event = "ota_manifest_rejected",
                        phase = "ota",
                        severity = ObservabilityLogger.Severity.WARN,
                        correlationId = cid,
                        meta = mapOf("reason" to "invalid_sha_or_host")
                    )
                    return@withContext UpdateCheckOutcome.Failure(
                        "Manifesto de atualização inválido ou sem SHA-256/URL confiável."
                    )
                }

                // Confirma que o binário da release já está no ar (evita HTTP 404 enquanto o CI ainda publica o APK).
                if (!probeApkReachable(apkUrl)) {
                    Log.i(TAG, "Release v$versionName declarada no manifesto, mas o APK ainda não está disponível ($apkUrl).")
                    ObservabilityLogger.logEvent(
                        event = "ota_not_published_yet",
                        phase = "ota",
                        severity = ObservabilityLogger.Severity.INFO,
                        correlationId = cid,
                        meta = mapOf("version" to versionName, "host" to LogSanitizer.extractSafeHost(apkUrl))
                    )
                    return@withContext UpdateCheckOutcome.NotPublishedYet(
                        versionName = versionName,
                        fallbackReleasesUrl = RELEASES_LATEST_URL
                    )
                }

                UpdateCheckOutcome.Available(
                    UpdateInfo(
                        versionCode = remoteVersionCode,
                        versionName = versionName,
                        apkUrl = apkUrl,
                        sha256 = sha256.lowercase(),
                        changelog = changelog,
                        expectedSizeBytes = expectedSizeBytes,
                        title = title
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar atualizações OTA", e)
            ObservabilityLogger.endTimer(
                key = "ota_manifest",
                event = "ota_manifest_error",
                phase = "ota",
                severity = ObservabilityLogger.Severity.ERROR,
                message = e.message
            )
            UpdateCheckOutcome.Failure(e.message ?: "rede indisponível")
        }
    }

    suspend fun downloadAndInstallApk(
        updateInfo: UpdateInfo,
        autoLaunchInstaller: Boolean = true,
        onProgress: (Int) -> Unit
    ): Boolean {
        return downloadAndVerifyApkDetailed(
            updateInfo = updateInfo,
            autoLaunchInstaller = autoLaunchInstaller,
            onProgress = { percent, _, _ -> onProgress(percent) }
        ) is DownloadVerificationResult.Success
    }

    suspend fun downloadAndVerifyApkDetailed(
        updateInfo: UpdateInfo,
        autoLaunchInstaller: Boolean = true,
        onProgress: (progressPercent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): DownloadVerificationResult = withContext(Dispatchers.IO) {
        val normalizedSha = updateInfo.sha256.trim().lowercase()
        if (!isValidSha256(normalizedSha)) {
            Log.e(TAG, "Atualização abortada: manifesto sem SHA-256 válido.")
            return@withContext DownloadVerificationResult.Failure("Manifesto de atualização sem um SHA-256 válido.")
        }
        if (!isAllowedUpdateUrl(updateInfo.apkUrl)) {
            Log.e(TAG, "Atualização abortada: URL de APK fora da política HTTPS/allowlist.")
            return@withContext DownloadVerificationResult.Failure("URL de atualização fora da política HTTPS confiável.")
        }
        if (autoLaunchInstaller && !hasConfiguredCertPin()) {
            Log.e(TAG, "Instalação automática bloqueada: EXPECTED_CERT_SHA256 não configurado.")
            return@withContext DownloadVerificationResult.Failure(
                "Pin de certificado de release não configurado; baixe manualmente pelo navegador."
            )
        }

        val updateDir = File(context.externalCacheDir ?: context.cacheDir, "updates").apply { mkdirs() }
        val safeVersion = SAFE_VERSION_CHARS.replace(updateInfo.versionName, "_").ifBlank { "latest" }
        val partFile = File(updateDir, "mk21_${safeVersion}.apk.part")
        val apkFile = File(updateDir, "mk21_${safeVersion}.apk")
        partFile.delete()
        apkFile.delete()

        val downloadCid = ObservabilityLogger.startTimer("ota_download")
        ObservabilityLogger.logEvent(
            event = "ota_download_start",
            phase = "ota",
            correlationId = downloadCid,
            meta = mapOf(
                "version" to updateInfo.versionName,
                "host" to LogSanitizer.extractSafeHost(updateInfo.apkUrl),
                "expectedSizeBytes" to updateInfo.expectedSizeBytes
            )
        )

        try {
            var downloadedBytes = 0L
            executeWithValidatedRedirects(updateInfo.apkUrl).use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Falha HTTP ao baixar atualização: ${response.code}")
                    ObservabilityLogger.endTimer(
                        key = "ota_download",
                        event = "ota_download_error",
                        phase = "ota",
                        severity = ObservabilityLogger.Severity.WARN,
                        meta = mapOf("status" to response.code)
                    )
                    val friendly = if (response.code == 404) {
                        "Arquivo da versão v${updateInfo.versionName} ainda não publicado no GitHub Releases (HTTP 404). Aguarde alguns minutos."
                    } else {
                        "Servidor respondeu HTTP ${response.code}."
                    }
                    return@withContext DownloadVerificationResult.Failure(friendly)
                }
                val contentType = response.header("Content-Type")?.lowercase().orEmpty()
                if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
                    Log.e(TAG, "Resposta OTA rejeitada: servidor retornou HTML ($contentType) em vez de APK.")
                    ObservabilityLogger.endTimer(
                        key = "ota_download",
                        event = "ota_validation_apk_rejected",
                        phase = "ota",
                        severity = ObservabilityLogger.Severity.ERROR,
                        meta = mapOf("reason" to "html_content_type", "contentType" to contentType)
                    )
                    return@withContext DownloadVerificationResult.Failure("Resposta inválida: o servidor retornou página HTML em vez de um arquivo APK.")
                }

                val body = response.body
                    ?: return@withContext DownloadVerificationResult.Failure("Resposta de download vazia.")
                val totalBytes = body.contentLength()
                var lastProgress = -1

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            val progress = if (totalBytes > 0) {
                                ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                50
                            }
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress, downloadedBytes, totalBytes)
                            }
                        }
                    }
                }
            }

            ObservabilityLogger.endTimer(
                key = "ota_download",
                event = "ota_download_end",
                phase = "ota",
                meta = mapOf("bytesReceived" to downloadedBytes, "fileBytes" to partFile.length())
            )

            // Valida cabeçalho binário ZIP/APK (PK\x03\x04) para impedir instalação de HTML/JSON/texto corrompido
            if (!isZipApkMagic(partFile)) {
                Log.e(TAG, "Arquivo baixado não possui cabeçalho binário APK/ZIP válido (possível HTML ou corrupção).")
                partFile.delete()
                ObservabilityLogger.logEvent(
                    event = "ota_validation_apk_rejected",
                    phase = "ota",
                    severity = ObservabilityLogger.Severity.ERROR,
                    correlationId = downloadCid,
                    meta = mapOf("reason" to "invalid_apk_magic", "bytesReceived" to downloadedBytes)
                )
                return@withContext DownloadVerificationResult.Failure("O arquivo baixado não é um APK válido (cabeçalho ZIP ausente ou página HTML).")
            }

            if (partFile.length() < minApkSizeBytes) {
                Log.e(TAG, "APK baixado abaixo do tamanho mínimo esperado (${partFile.length()} bytes).")
                partFile.delete()
                ObservabilityLogger.logEvent(
                    event = "ota_validation_size_failed",
                    phase = "ota",
                    severity = ObservabilityLogger.Severity.ERROR,
                    correlationId = downloadCid,
                    meta = mapOf("actualBytes" to partFile.length(), "minBytes" to minApkSizeBytes)
                )
                return@withContext DownloadVerificationResult.Failure("Download incompleto ou arquivo APK abaixo do mínimo esperado.")
            }

            if (updateInfo.expectedSizeBytes > 0L && partFile.length() != updateInfo.expectedSizeBytes) {
                Log.e(TAG, "Tamanho do APK (${partFile.length()}) diverge do manifesto (${updateInfo.expectedSizeBytes}).")
                partFile.delete()
                ObservabilityLogger.logEvent(
                    event = "ota_validation_size_failed",
                    phase = "ota",
                    severity = ObservabilityLogger.Severity.ERROR,
                    correlationId = downloadCid,
                    meta = mapOf("actualBytes" to partFile.length(), "expectedBytes" to updateInfo.expectedSizeBytes)
                )
                return@withContext DownloadVerificationResult.Failure("O tamanho do APK baixado não corresponde ao manifesto.")
            }
            ObservabilityLogger.logEvent(
                event = "ota_validation_size_ok",
                phase = "ota",
                correlationId = downloadCid,
                meta = mapOf("sizeBytes" to partFile.length())
            )

            val fileHash = calculateSha256(partFile)
            if (!fileHash.equals(normalizedSha, ignoreCase = true)) {
                Log.e(TAG, "Hash SHA-256 divergente para atualização: esperado=$normalizedSha obtido=$fileHash")
                partFile.delete()
                ObservabilityLogger.logEvent(
                    event = "ota_validation_sha256_failed",
                    phase = "ota",
                    severity = ObservabilityLogger.Severity.ERROR,
                    correlationId = downloadCid,
                    meta = mapOf("computedSha256Prefix" to fileHash.take(12))
                )
                return@withContext DownloadVerificationResult.Failure("O SHA-256 do APK baixado não corresponde ao manifesto.")
            }
            ObservabilityLogger.logEvent(
                event = "ota_validation_sha256_ok",
                phase = "ota",
                correlationId = downloadCid,
                meta = mapOf("sha256Prefix" to fileHash.take(12))
            )

            if (!partFile.renameTo(apkFile)) {
                partFile.copyTo(apkFile, overwrite = true)
                partFile.delete()
            }
            runCatching { apkFile.setReadable(true, false) }

            when (val sigResult = ApkSignatureVerifier.verifyApkArchiveSignature(context, apkFile, expectedCertSha256)) {
                is ApkVerificationResult.Verified -> {
                    ObservabilityLogger.logEvent(
                        event = "ota_validation_signature_ok",
                        phase = "ota",
                        correlationId = downloadCid
                    )
                }
                is ApkVerificationResult.SystemDelegate -> {
                    Log.w(TAG, "getPackageArchiveInfo indisponível; delegando verificação de assinatura ao instalador do Android.")
                    ObservabilityLogger.logEvent(
                        event = "ota_validation_signature_delegated",
                        phase = "ota",
                        severity = ObservabilityLogger.Severity.WARN,
                        correlationId = downloadCid
                    )
                }
                is ApkVerificationResult.Rejected -> {
                    Log.e(TAG, "APK rejeitado na verificação de assinatura: ${sigResult.reason}")
                    apkFile.delete()
                    ObservabilityLogger.logEvent(
                        event = "ota_validation_signature_failed",
                        phase = "ota",
                        severity = ObservabilityLogger.Severity.ERROR,
                        correlationId = downloadCid,
                        message = sigResult.reason
                    )
                    return@withContext DownloadVerificationResult.Failure(sigResult.reason)
                }
            }

            if (autoLaunchInstaller) {
                installApk(apkFile)
            }
            DownloadVerificationResult.Success(apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Erro durante download/validação da atualização OTA", e)
            partFile.delete()
            apkFile.delete()
            ObservabilityLogger.endTimer(
                key = "ota_download",
                event = "ota_download_error",
                phase = "ota",
                severity = ObservabilityLogger.Severity.ERROR,
                message = e.message
            )
            DownloadVerificationResult.Failure(e.message ?: "erro inesperado no download OTA")
        }
    }

    private fun probeApkReachable(apkUrl: String): Boolean {
        return try {
            executeWithValidatedRedirects(apkUrl, method = "HEAD").use { headResp ->
                if (headResp.code == 200 || headResp.code == 206) return true
                if (headResp.code != 405 && headResp.code != 501) return false
            }
            executeWithValidatedRedirects(
                apkUrl,
                method = "GET",
                extraHeaders = mapOf("Range" to "bytes=0-0")
            ).use { rangeResp ->
                rangeResp.code == 200 || rangeResp.code == 206
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao validar disponibilidade do APK ($apkUrl): ${e.message}")
            false
        }
    }

    private fun executeWithValidatedRedirects(
        initialUrl: String,
        method: String = "GET",
        extraHeaders: Map<String, String> = emptyMap()
    ): Response {
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
            val requestBuilder = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", "MK21-Android")
                .header("Cache-Control", "no-cache")
            extraHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }
            if (method.equals("HEAD", ignoreCase = true)) {
                requestBuilder.head()
            } else {
                requestBuilder.get()
            }
            val response = strictClient.newCall(requestBuilder.build()).execute()
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

    fun installApk(apkFile: File): Boolean {
        if (!hasConfiguredCertPin()) {
            Log.e(TAG, "Instalação recusada: pin de certificado EXPECTED_CERT_SHA256 não configurado.")
            return false
        }
        val authority = "${context.packageName}.fileprovider"
        val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching {
            val resolveList = context.packageManager.queryIntentActivities(
                installIntent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resolveList) {
                context.grantUriPermission(
                    resolveInfo.activityInfo.packageName,
                    apkUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            listOf("com.google.android.packageinstaller", "com.android.packageinstaller").forEach { pkg ->
                runCatching {
                    context.grantUriPermission(pkg, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }
        return try {
            context.startActivity(installIntent)
            ObservabilityLogger.logEvent(
                event = "ota_installer_launched",
                phase = "ota",
                meta = mapOf("fileSize" to apkFile.length())
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir instalador de pacotes Android", e)
            ObservabilityLogger.logEvent(
                event = "ota_installer_error",
                phase = "ota",
                severity = ObservabilityLogger.Severity.ERROR,
                message = e.message
            )
            false
        }
    }

    private fun isZipApkMagic(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        return try {
            val header = ByteArray(4)
            val read = FileInputStream(file).use { it.read(header) }
            read == 4 &&
                header[0] == 0x50.toByte() && // 'P'
                header[1] == 0x4B.toByte() && // 'K'
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()
        } catch (_: Exception) {
            false
        }
    }

    private fun compareVersionParts(left: String, right: String): Int {
        fun parse(v: String): List<Int> = v
            .replace("[^0-9.]".toRegex(), "")
            .split('.')
            .filter { it.isNotBlank() }
            .map { it.toIntOrNull() ?: 0 }
        val l = parse(left)
        val r = parse(right)
        val max = maxOf(l.size, r.size, 3)
        for (i in 0 until max) {
            val a = l.getOrElse(i) { 0 }
            val b = r.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
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