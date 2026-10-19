package com.fbg.streaming.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
   private val httpClient: OkHttpClient = OkHttpClient()
) {
   // URL oficial do version.json no seu repositório 2fbg/FBGs-Streaming
   private val versionJsonUrl = "https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/version.json"

   suspend fun checkForUpdates(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
       val request = Request.Builder().url(versionJsonUrl).build()
       httpClient.newCall(request).execute().use { response ->
           if (!response.isSuccessful) return@withContext null
           val body = response.body?.string() ?: return@withContext null
           val json = JSONObject(body)
           val remoteVersionCode = json.optInt("versionCode", currentVersionCode)
           if (remoteVersionCode > currentVersionCode) {
               UpdateInfo(
                   versionCode = remoteVersionCode,
                   versionName = json.optString("versionName", "2.1.0"),
                   apkUrl = json.optString("apkUrl", "https://github.com/2fbg/FBGs-Streaming/raw/main/mk21.apk"),
                   sha256 = json.optString("sha256", ""),
                   changelog = json.optString("changelog", "Melhorias de estabilidade e performance.")
               )
           } else {
               null
           }
       }
   }

   suspend fun downloadAndInstallApk(
       updateInfo: UpdateInfo,
       onProgress: (Int) -> Unit
   ): Boolean = withContext(Dispatchers.IO) {
       val updateDir = File(context.externalCacheDir ?: context.cacheDir, "updates").apply { mkdirs() }
       val apkFile = File(updateDir, "mk21_${updateInfo.versionName}.apk")

       val request = Request.Builder().url(updateInfo.apkUrl).build()
       httpClient.newCall(request).execute().use { response ->
           if (!response.isSuccessful) return@withContext false
           val body = response.body ?: return@withContext false
           val totalBytes = body.contentLength()
           var downloadedBytes = 0L

           body.byteStream().use { input ->
               FileOutputStream(apkFile).use { output ->
                   val buffer = ByteArray(8 * 1024)
                   while (true) {
                       val read = input.read(buffer)
                       if (read == -1) break
                       output.write(buffer, 0, read)
                       downloadedBytes += read
                       if (totalBytes > 0) {
                           onProgress(((downloadedBytes * 100) / totalBytes).toInt())
                       }
                   }
               }
           }
       }

       // Verifica integridade SHA-256 caso fornecida no version.json
       if (updateInfo.sha256.isNotBlank()) {
           val fileHash = calculateSha256(apkFile)
           if (!fileHash.equals(updateInfo.sha256, ignoreCase = true)) {
               apkFile.delete()
               return@withContext false
           }
       }

       installApk(apkFile)
       true
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