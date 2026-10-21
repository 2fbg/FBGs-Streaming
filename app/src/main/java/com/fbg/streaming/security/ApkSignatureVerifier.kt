package com.fbg.streaming.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object ApkSignatureVerifier {

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
       if (expectedSha256Fingerprint.isBlank()) return true
       val current = getCurrentSignatureSha256(context) ?: return false
       return current.equals(expectedSha256Fingerprint, ignoreCase = true)
   }
}