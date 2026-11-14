package com.fbg.streaming.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

sealed class ApkVerificationResult {
    data object Verified : ApkVerificationResult()
    data object SystemDelegate : ApkVerificationResult()
    data class Rejected(val reason: String) : ApkVerificationResult()
}

object ApkSignatureVerifier {

    private val SHA256_HEX_REGEX = Regex("^[a-f0-9]{64}$")

    fun normalizeSha256Fingerprint(value: String): String {
        return value.replace(":", "").replace(" ", "").trim().lowercase()
    }

    fun isValidSha256Hex(value: String): Boolean {
        return SHA256_HEX_REGEX.matches(normalizeSha256Fingerprint(value))
    }

    fun getCurrentSignatureSha256(context: Context): String? {
        return try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pkgInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                pkgInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                val pkgInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
                @Suppress("DEPRECATION")
                pkgInfo.signatures
            }

            val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return null
            val md = MessageDigest.getInstance("SHA-256")
            md.digest(certBytes).joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    fun isSignatureValid(context: Context, expectedSha256Fingerprint: String): Boolean {
        val normalizedExpected = normalizeSha256Fingerprint(expectedSha256Fingerprint)
        if (!SHA256_HEX_REGEX.matches(normalizedExpected)) return false
        val current = getCurrentSignatureSha256(context) ?: return false
        return normalizeSha256Fingerprint(current) == normalizedExpected
    }

    fun verifyApkArchiveSignature(
        context: Context,
        apkFile: File,
        expectedCertSha256: String? = null
    ): ApkVerificationResult {
        if (!apkFile.exists() || !apkFile.isFile) {
            return ApkVerificationResult.Rejected("Arquivo APK não encontrado.")
        }

        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val downloadedInfo = try {
            packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } catch (_: Exception) {
            null
        }

        if (downloadedInfo == null) {
            // Se houver fingerprint explícito fixado, falha fechado; caso contrário delega ao PackageInstaller do sistema.
            return if (!expectedCertSha256.isNullOrBlank()) {
                ApkVerificationResult.Rejected("Não foi possível ler o certificado do APK para validar o pin configurado.")
            } else {
                ApkVerificationResult.SystemDelegate
            }
        }

        val archivePackage = downloadedInfo.packageName
        if (!archivePackage.isNullOrBlank() && archivePackage != context.packageName) {
            return ApkVerificationResult.Rejected("Pacote do APK ($archivePackage) difere de ${context.packageName}.")
        }

        val downloadedCerts = extractSignerByteArrays(downloadedInfo)
        if (downloadedCerts.isEmpty()) {
            return ApkVerificationResult.Rejected("O APK baixado não possui assinatura criptográfica.")
        }

        if (!expectedCertSha256.isNullOrBlank()) {
            val normalizedExpected = normalizeSha256Fingerprint(expectedCertSha256)
            if (!SHA256_HEX_REGEX.matches(normalizedExpected)) {
                return ApkVerificationResult.Rejected("Fingerprint de certificado configurado é inválido.")
            }
            val matchedPin = downloadedCerts.any { certBytes ->
                sha256HexLower(certBytes) == normalizedExpected
            }
            return if (matchedPin) {
                ApkVerificationResult.Verified
            } else {
                ApkVerificationResult.Rejected("Certificado do APK não corresponde ao fingerprint esperado.")
            }
        }

        val installedInfo = try {
            packageManager.getPackageInfo(context.packageName, flags)
        } catch (_: Exception) {
            return ApkVerificationResult.Rejected("Não foi possível obter a assinatura do app instalado.")
        }

        val installedCerts = extractSignerByteArrays(installedInfo)
        if (installedCerts.isEmpty()) {
            return ApkVerificationResult.Rejected("Aplicativo instalado sem assinatura verificável.")
        }

        val matchesInstalled = downloadedCerts.any { down ->
            installedCerts.any { inst -> down.contentEquals(inst) }
        }
        return if (matchesInstalled) {
            ApkVerificationResult.Verified
        } else {
            ApkVerificationResult.Rejected("A assinatura do APK baixado não corresponde à do aplicativo instalado.")
        }
    }

    private fun extractSignerByteArrays(packageInfo: android.content.pm.PackageInfo): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo
            signingInfo?.apkContentsSigners?.forEach { result.add(it.toByteArray()) }
            signingInfo?.signingCertificateHistory?.forEach { result.add(it.toByteArray()) }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures?.forEach { result.add(it.toByteArray()) }
        }
        return result
    }

    private fun sha256HexLower(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}