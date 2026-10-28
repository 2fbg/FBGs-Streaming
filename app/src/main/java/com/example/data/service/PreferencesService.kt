package com.example.data.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class PreferencesService(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey: MasterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "mk21_secure_pref_store",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Throwable) {
        Log.w("PreferencesService", "Failed to initialize EncryptedSharedPreferences (falling back to standard prefs for testing): ${e.message}")
        context.getSharedPreferences("mk21_secure_pref_store_fallback", Context.MODE_PRIVATE)
    }

    init {
        // Migration: If data exists in old unencrypted SharedPreferences ("mk21_pref_store"),
        // migrate all key-values to EncryptedSharedPreferences and clear old prefs.
        try {
            val oldPrefs = context.getSharedPreferences("mk21_pref_store", Context.MODE_PRIVATE)
            val allOld = oldPrefs.all
            if (allOld.isNotEmpty()) {
                val editor = prefs.edit()
                for ((key, value) in allOld) {
                    when (value) {
                        is String -> {
                            val cleanVal = if (value.startsWith("v1:") || value.startsWith("fallback:")) {
                                // Strip legacy prefix if any legacy format existed
                                value.substringAfter(":")
                            } else {
                                value
                            }
                            editor.putString(key, cleanVal)
                        }
                        is Boolean -> editor.putBoolean(key, value)
                        is Long -> editor.putLong(key, value)
                        is Int -> editor.putInt(key, value)
                        is Float -> editor.putFloat(key, value)
                    }
                }
                editor.apply()
                oldPrefs.edit().clear().apply()
                Log.d("PreferencesService", "Successfully migrated legacy SharedPreferences to EncryptedSharedPreferences")
            }
        } catch (e: Throwable) {
            Log.w("PreferencesService", "Error during legacy prefs migration: ${e.message}")
        }
    }

    companion object {
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_ACTIVE_SERVER_ID = "active_server_id"
        private const val KEY_ACTIVE_PLAYLIST_NAME = "active_playlist_name"
        private const val KEY_ADULT_PIN = "adult_pin"
        private const val KEY_LAST_UPDATE_PREFIX = "last_list_update_"
        private const val KEY_USE_SAME_CREDENTIALS = "use_same_credentials"
    }

    var username: String
        get() = try { prefs.getString(KEY_USERNAME, "") ?: "" } catch (e: Throwable) { "" }
        set(value) { try { prefs.edit().putString(KEY_USERNAME, value).apply() } catch (e: Throwable) {} }

    var password: String
        get() = try { prefs.getString(KEY_PASSWORD, "") ?: "" } catch (e: Throwable) { "" }
        set(value) { try { prefs.edit().putString(KEY_PASSWORD, value).apply() } catch (e: Throwable) {} }

    var activeServerId: String
        get() = try { prefs.getString(KEY_ACTIVE_SERVER_ID, "server_1") ?: "server_1" } catch (e: Throwable) { "server_1" }
        set(value) { try { prefs.edit().putString(KEY_ACTIVE_SERVER_ID, value).apply() } catch (e: Throwable) {} }

    var activePlaylistName: String
        get() = try { prefs.getString(KEY_ACTIVE_PLAYLIST_NAME, "VLOG") ?: "VLOG" } catch (e: Throwable) { "VLOG" }
        set(value) { try { prefs.edit().putString(KEY_ACTIVE_PLAYLIST_NAME, value).apply() } catch (e: Throwable) {} }

    var adultPin: String
        get() = try { prefs.getString(KEY_ADULT_PIN, "0000") ?: "0000" } catch (e: Throwable) { "0000" }
        set(value) { try { prefs.edit().putString(KEY_ADULT_PIN, value).apply() } catch (e: Throwable) {} }

    var useSameCredentialsForServers: Boolean
        get() = try { prefs.getBoolean(KEY_USE_SAME_CREDENTIALS, true) } catch (e: Throwable) { true }
        set(value) { try { prefs.edit().putBoolean(KEY_USE_SAME_CREDENTIALS, value).apply() } catch (e: Throwable) {} }

    var useAmoledMode: Boolean
        get() = try { prefs.getBoolean("use_amoled_mode", false) } catch (e: Throwable) { false }
        set(value) { try { prefs.edit().putBoolean("use_amoled_mode", value).apply() } catch (e: Throwable) {} }

    var hideLiveCategories: Boolean
        get() = try { prefs.getBoolean("hide_live_categories", false) } catch (e: Throwable) { false }
        set(value) { try { prefs.edit().putBoolean("hide_live_categories", value).apply() } catch (e: Throwable) {} }

    var useExternalPlayer: Boolean
        get() = try { prefs.getBoolean("use_external_player", false) } catch (e: Throwable) { false }
        set(value) { try { prefs.edit().putBoolean("use_external_player", value).apply() } catch (e: Throwable) {} }

    var externalPlayerType: String
        get() = try { prefs.getString("external_player_type", "Qualquer Player") ?: "Qualquer Player" } catch (e: Throwable) { "Qualquer Player" }
        set(value) { try { prefs.edit().putString("external_player_type", value).apply() } catch (e: Throwable) {} }

    var deviceType: String
        get() = try { prefs.getString("device_type", "Celular / Tablet") ?: "Celular / Tablet" } catch (e: Throwable) { "Celular / Tablet" }
        set(value) { try { prefs.edit().putString("device_type", value).apply() } catch (e: Throwable) {} }

    var appLanguage: String
        get() = try { prefs.getString("app_language", "Português") ?: "Português" } catch (e: Throwable) { "Português" }
        set(value) { try { prefs.edit().putString("app_language", value).apply() } catch (e: Throwable) {} }

    var timeFormat: String
        get() = try { prefs.getString("time_format", "24 horas") ?: "24 horas" } catch (e: Throwable) { "24 horas" }
        set(value) { try { prefs.edit().putString("time_format", value).apply() } catch (e: Throwable) {} }

    var appLayout: String
        get() = try { prefs.getString("app_layout", "Grid Clássico") ?: "Grid Clássico" } catch (e: Throwable) { "Grid Clássico" }
        set(value) { try { prefs.edit().putString("app_layout", value).apply() } catch (e: Throwable) {} }

    var liveStreamFormat: String
        get() = try { prefs.getString("live_stream_format", "MPEG-TS (.ts)") ?: "MPEG-TS (.ts)" } catch (e: Throwable) { "MPEG-TS (.ts)" }
        set(value) { try { prefs.edit().putString("live_stream_format", value).apply() } catch (e: Throwable) {} }

    var subtitleConfig: String
        get() = try { prefs.getString("subtitle_config", "Média (Padrão)") ?: "Média (Padrão)" } catch (e: Throwable) { "Média (Padrão)" }
        set(value) { try { prefs.edit().putString("subtitle_config", value).apply() } catch (e: Throwable) {} }

    var menuSortOrder: String
        get() = try { prefs.getString("menu_sort_order", "Ordem por adição") ?: "Ordem por adição" } catch (e: Throwable) { "Ordem por adição" }
        set(value) { try { prefs.edit().putString("menu_sort_order", value).apply() } catch (e: Throwable) {} }

    var trialStartDate: Long
        get() = try { prefs.getLong("trial_start_date", 0L) } catch (e: Throwable) { 0L }
        set(value) { try { prefs.edit().putLong("trial_start_date", value).apply() } catch (e: Throwable) {} }

    var activationKey: String
        get() = try { prefs.getString("activation_key", "") ?: "" } catch (e: Throwable) { "" }
        set(value) { try { prefs.edit().putString("activation_key", value).apply() } catch (e: Throwable) {} }

    val deviceId: String by lazy {
        try {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "MK21DEVICEID"
            androidId
        } catch (e: Throwable) {
            "MK21DEVICEID"
        }
    }

    val virtualMac: String by lazy {
        val cleanId = deviceId.replace("[^A-Fa-f0-9]".toRegex(), "").padEnd(12, 'F').take(12).uppercase()
        cleanId.chunked(2).joinToString(":")
    }

    /**
     * Generates valid activation key for a device using HMAC-SHA256 with the app secret from BuildConfig.
     */
    fun generateValidKeyForDevice(deviceMac: String, secret: String = BuildConfig.LICENSE_SECRET): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hashBytes = mac.doFinal(deviceMac.uppercase().trim().toByteArray(Charsets.UTF_8))
        val hexHash = hashBytes.joinToString("") { "%02X".format(it) }
        val p1 = hexHash.take(4)
        val p2 = hexHash.substring(4, 8)
        val p3 = hexHash.substring(8, 12)
        return "MK-$p1-$p2-$p3"
    }

    /**
     * Generates valid autonomous key for a device matching the built-in admin key generator.
     */
    fun generateAutonomousKeyForDevice(deviceMac: String): String {
        val cleanMac = deviceMac.replace("[^A-Fa-f0-9]".toRegex(), "").take(12).uppercase()
        val paddedMac = cleanMac.padEnd(12, 'F')
        val formattedMac = paddedMac.chunked(2).joinToString(":")
        val salt = "MK21_GOLDEN_SALT_2026"
        val rawInput = formattedMac + salt
        return try {
            val md5 = MessageDigest.getInstance("MD5")
            val hashBytes: ByteArray = md5.digest(rawInput.toByteArray(Charsets.UTF_8))
            val fullHash = hashBytes.joinToString("") { b -> "%02X".format(b) }
            val p1 = fullHash.take(4)
            val p2 = fullHash.substring(4, 8)
            val p3 = fullHash.substring(8, 12)
            "MK-$p1-$p2-$p3"
        } catch (e: Exception) {
            "MK-ERR"
        }
    }

    /**
     * Verifies license key validity against both HMAC-SHA256 and Autonomous MD5 keys.
     */
    fun isLicenseValid(): Boolean {
        val key = activationKey.uppercase().trim()
        if (key.isEmpty()) return false

        // 1. Standard 3-segment HMAC-SHA256 check
        val expectedHmac = generateValidKeyForDevice(virtualMac).uppercase().trim()
        if (key == expectedHmac) return true

        // 2. Autonomous Generator MD5 check (MK21_GOLDEN_SALT_2026)
        val expectedAutonomous = generateAutonomousKeyForDevice(virtualMac).uppercase().trim()
        if (key == expectedAutonomous) return true

        // 3. Backward compatibility for 2-segment autonomous key if used
        try {
            val cleanMac = virtualMac.replace("[^A-Fa-f0-9]".toRegex(), "").take(12).uppercase()
            val paddedMac = cleanMac.padEnd(12, 'F')
            val formattedMac = paddedMac.chunked(2).joinToString(":")
            val salt = "MK21_GOLDEN_SALT_2026"
            val md5 = MessageDigest.getInstance("MD5")
            val hashBytes: ByteArray = md5.digest((formattedMac + salt).toByteArray(Charsets.UTF_8))
            val fullHash = hashBytes.joinToString("") { b -> "%02X".format(b) }
            val p1 = fullHash.take(4)
            val p2 = fullHash.takeLast(4)
            if (key == "MK-$p1-$p2".uppercase()) return true
        } catch (_: Exception) {}

        return false
    }

    fun getTrialDaysRemaining(): Int {
        if (isLicenseValid()) return 9999
        val start = trialStartDate
        if (start == 0L) return 5
        val now = System.currentTimeMillis()
        val elapsedMs = now - start
        val fiveDaysMs = 5 * 24 * 60 * 60 * 1000L
        val remainingMs = fiveDaysMs - elapsedMs
        if (remainingMs <= 0) return 0
        return (remainingMs / (24 * 60 * 60 * 1000L)).toInt().coerceIn(0, 5)
    }

    fun setLastPlaylistUpdateTimestamp(playlistName: String, timestamp: Long) {
        try { prefs.edit().putLong(KEY_LAST_UPDATE_PREFIX + playlistName, timestamp).apply() } catch (e: Throwable) {}
    }

    fun getLastPlaylistUpdateTimestamp(playlistName: String): Long {
        return try { prefs.getLong(KEY_LAST_UPDATE_PREFIX + playlistName, 0L) } catch (e: Throwable) { 0L }
    }

    var cachedServersJson: String
        get() = try { prefs.getString("cached_servers_json", "") ?: "" } catch (e: Throwable) { "" }
        set(value) { try { prefs.edit().putString("cached_servers_json", value).apply() } catch (e: Throwable) {} }

    var dynamicServersUrl: String
        get() = try { prefs.getString("dynamic_servers_url", "https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/servers.json") ?: "https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/servers.json" } catch (e: Throwable) { "https://raw.githubusercontent.com/2fbg/FBGs-Streaming/main/servers.json" }
        set(value) { try { prefs.edit().putString("dynamic_servers_url", value).apply() } catch (e: Throwable) {} }

    var loadLiveInForeground: Boolean
        get() = try { prefs.getBoolean("load_live_foreground", true) } catch (e: Throwable) { true }
        set(value) { try { prefs.edit().putBoolean("load_live_foreground", value).apply() } catch (e: Throwable) {} }

    var loadMoviesInForeground: Boolean
        get() = try { prefs.getBoolean("load_movies_foreground", false) } catch (e: Throwable) { false }
        set(value) { try { prefs.edit().putBoolean("load_movies_foreground", value).apply() } catch (e: Throwable) {} }

    var loadSeriesInForeground: Boolean
        get() = try { prefs.getBoolean("load_series_foreground", false) } catch (e: Throwable) { false }
        set(value) { try { prefs.edit().putBoolean("load_series_foreground", value).apply() } catch (e: Throwable) {} }

    var syncIntervalFrequency: String
        get() = try { prefs.getString("sync_interval_frequency", "Uma vez ao dia") ?: "Uma vez ao dia" } catch (e: Throwable) { "Uma vez ao dia" }
        set(value) { try { prefs.edit().putString("sync_interval_frequency", value).apply() } catch (e: Throwable) {} }

    var syncAllListsBackground: Boolean
        get() = try { prefs.getBoolean("sync_all_lists_background", true) } catch (e: Throwable) { true }
        set(value) { try { prefs.edit().putBoolean("sync_all_lists_background", value).apply() } catch (e: Throwable) {} }

    var hideBackgroundProgress: Boolean
        get() = try { prefs.getBoolean("hide_background_progress", false) } catch (e: Throwable) { false }
        set(value) { try { prefs.edit().putBoolean("hide_background_progress", value).apply() } catch (e: Throwable) {} }

    var superResolutionMode: String
        get() = try { prefs.getString("saimo_super_resolution", "off") ?: "off" } catch (e: Throwable) { "off" }
        set(value) { try { prefs.edit().putString("saimo_super_resolution", value).apply() } catch (e: Throwable) {} }

    fun isCredentialsConfigured(predefinedNames: Set<String>): Boolean {
        val isPredefined = predefinedNames.contains(activePlaylistName)
        return activePlaylistName.isNotEmpty() && (
            !isPredefined || (username.isNotEmpty() && password.isNotEmpty())
        )
    }
}
