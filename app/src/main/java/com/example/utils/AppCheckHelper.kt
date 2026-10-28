package com.example.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

// App Check contract keys defined by Firebase SDK and runtime test harness
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"
private const val FALLBACK_RECAPTCHA_KEY = "6LdnSQQuAAAAAPjmsf4XKDX7tEzbEQmf5QNx2C65"

/**
 * Configures Firebase App Check for Firebase AI Logic (Gemini API).
 * In debug mode with Intent token, records token synchronously and uses DebugAppCheckProviderFactory.
 * On real physical devices, resolves the reCAPTCHA Enterprise site key across namespace/package
 * and installs RecaptchaAppCheckProviderFactory to attest authenticity with Google Cloud.
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        // 1. Ensure FirebaseApp is initialized
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
        val siteKey = resolveRecaptchaSiteKey(context)

        if (!debugToken.isNullOrEmpty()) {
            // Automated test runner or CI provided an explicit debug token
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val prefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
            // CRITICAL: MUST use commit() instead of apply() so the secret is written
            // synchronously before DebugAppCheckProviderFactory reads it.
            prefs.edit()
                .putString(DEBUG_SECRET_KEY, debugToken)
                .commit()

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed with intent debug token.")
        } else if (siteKey.isNotBlank()) {
            // Real physical Android device (Release or manual install):
            // Use reCAPTCHA Enterprise provider with registered key
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                RecaptchaAppCheckProviderFactory.getInstance(siteKey)
            )
            Log.d(TAG, "RecaptchaAppCheckProviderFactory installed successfully with siteKey: ${siteKey.take(8)}...")
        } else if (BuildConfig.DEBUG) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed for debug build.")
        } else {
            Log.e(TAG, "Unable to install App Check provider: siteKey is empty and not debug.")
        }
    } catch (e: Exception) {
        // Log explicitly to keep App Check attestation failures visible
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

/**
 * Resolves the reCAPTCHA Enterprise site key across R class namespace and runtime package,
 * with guaranteed fallback to the provisioned key from firebase_applet_config.xml.
 */
private fun resolveRecaptchaSiteKey(context: Context): String {
    // 1. Check in R package namespace ("com.example")
    var resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", "com.example")
    if (resId == 0) {
        // 2. Check in application package ("com.aistudio.multiservidor.iptvmk")
        resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
    }
    if (resId != 0) {
        val str = context.getString(resId)
        if (str.isNotBlank()) return str
    }
    // 3. Fallback to provisioned site key
    return FALLBACK_RECAPTCHA_KEY
}
