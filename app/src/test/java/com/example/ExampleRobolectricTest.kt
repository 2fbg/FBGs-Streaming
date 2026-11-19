package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.service.PreferencesService
import com.example.viewmodel.AppViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MK21 Player", appName)
  }

  @Test
  fun `test preferences service secure username password`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = PreferencesService(context)
    
    // Set a password
    prefs.password = "mypassword123"
    val retrievedPassword = prefs.password
    
    // Verify that retrieved password successfully decrypted and equals original plaintext
    assertEquals("mypassword123", retrievedPassword)
    println("Retrieved Secure Password in Robolectric: '$retrievedPassword'")
  }

  @Test
  fun `test preferences service with email dot username`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = PreferencesService(context)
    
    val testEmail = "FabioGuarniere@gmail.com"
    prefs.username = testEmail
    val retrieved = prefs.username
    
    assertEquals(testEmail, retrieved)
    println("Retrieved Secure Email username in Robolectric: '$retrieved'")
  }

  @Test
  fun `test viewModel initialization`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = AppViewModel(app)
    assertNotNull(viewModel)
  }

  @Test
  fun `test ecdsa license validation in preferences service`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = PreferencesService(context)
    val mac = prefs.virtualMac

    val kpg = java.security.KeyPairGenerator.getInstance("EC")
    kpg.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
    val keyPair = kpg.generateKeyPair()
    val pubB64 = android.util.Base64.encodeToString(keyPair.public.encoded, android.util.Base64.NO_WRAP)

    val now = System.currentTimeMillis() / 1000L
    val urlFlags = android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
    val payloadJson = org.json.JSONObject().apply {
      put("d", mac)
      put("t", "pro")
      put("iat", now)
      put("exp", now + 365 * 86400L)
      put("o", "ORD-ROBO-1")
    }.toString()
    val body = "MKL1." + android.util.Base64.encodeToString(payloadJson.toByteArray(Charsets.UTF_8), urlFlags)
    val sig = java.security.Signature.getInstance("SHA256withECDSA").run {
      initSign(keyPair.private)
      update(body.toByteArray(Charsets.UTF_8))
      sign()
    }
    val validToken = "$body." + android.util.Base64.encodeToString(sig, urlFlags)

    // Initially empty or fake legacy strings must be invalid
    prefs.licenseToken = ""
    org.junit.Assert.assertNull(prefs.currentLicense(pubB64, now))
    org.junit.Assert.assertFalse(prefs.isLicenseValid())

    prefs.licenseToken = "ADMIN2026"
    org.junit.Assert.assertNull(prefs.currentLicense(pubB64, now))

    // Valid ECDSA token unlocks the device
    prefs.licenseToken = validToken
    val license = prefs.currentLicense(pubB64, now)
    assertNotNull(license)
    assertEquals("pro", license?.tier)
    assertEquals("ORD-ROBO-1", license?.orderCode)
  }

  @Test
  @Suppress("DEPRECATION")
  fun `test hmac license fallback works when ecdsa pubkey is not configured`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = PreferencesService(context)

    prefs.licenseToken = ""
    prefs.activationKey = "MK-0000-0000-0000"
    org.junit.Assert.assertFalse(prefs.isLocalHmacKeyValid())
    org.junit.Assert.assertFalse(prefs.isLicenseValid())

    val validHmacKey = prefs.generateValidKeyForDevice(prefs.virtualMac)
    prefs.activationKey = validHmacKey
    org.junit.Assert.assertTrue(prefs.isLocalHmacKeyValid())
    org.junit.Assert.assertFalse(prefs.isEcdsaConfigured("LICENSE_PUBKEY"))
    org.junit.Assert.assertFalse(prefs.isEcdsaConfigured(""))
    if (!prefs.isEcdsaConfigured()) {
      org.junit.Assert.assertTrue(prefs.isLicenseValid())
    }
  }
}
