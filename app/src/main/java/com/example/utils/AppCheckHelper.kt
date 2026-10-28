package com.example.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

private const val TAG = "AppCheckInit"

// App Check contract keys defined by Firebase SDK and runtime test harness
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

/**
 * Configures Firebase App Check for Firebase AI Logic (Gemini API).
 * Uses PlayIntegrityAppCheckProviderFactory for real Android devices (Google Play Services),
 * and DebugAppCheckProviderFactory for debug builds and automated testing.
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        // 1. Ensure FirebaseApp is initialized
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val appCheck = FirebaseAppCheck.getInstance()
        val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

        if (!debugToken.isNullOrEmpty()) {
            // Automated test runner or CI provided an explicit debug token
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val prefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
            prefs.edit()
                .putString(DEBUG_SECRET_KEY, debugToken)
                .commit()

            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed with intent debug token.")
        } else if (BuildConfig.DEBUG) {
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed for debug build.")
        } else {
            // Real physical Android device (Release):
            // Use Play Integrity which is built-in on Android via Google Play Services
            try {
                appCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "PlayIntegrityAppCheckProviderFactory installed successfully.")
            } catch (e: Throwable) {
                Log.w(TAG, "PlayIntegrity fallback to Debug provider: ${e.message}")
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
            }
        }
    } catch (e: Exception) {
        // Log explicitly to keep App Check attestation failures visible
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
