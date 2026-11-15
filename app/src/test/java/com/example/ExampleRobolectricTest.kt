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
  fun `test hmac sha256 license validation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = PreferencesService(context)
    val mac = prefs.virtualMac
    val validKey = prefs.generateValidKeyForDevice(mac)

    // Initially with empty key, license is invalid
    prefs.activationKey = ""
    org.junit.Assert.assertFalse(prefs.isLicenseValid())

    // With invalid or bypass key, license must be invalid
    prefs.activationKey = "ADMIN2026"
    org.junit.Assert.assertFalse(prefs.isLicenseValid())

    prefs.activationKey = "GUARNIERE2026"
    org.junit.Assert.assertFalse(prefs.isLicenseValid())

    prefs.activationKey = "9999"
    org.junit.Assert.assertFalse(prefs.isLicenseValid())

    // With legitimate HMAC-SHA256 generated key, license is valid
    prefs.activationKey = validKey
    org.junit.Assert.assertTrue(prefs.isLicenseValid())

    // Legacy MD5 key is only accepted when ALLOW_LEGACY_LICENSE=true at build time (default: off)
    @Suppress("DEPRECATION")
    val autonomousKey = prefs.generateAutonomousKeyForDevice(mac)
    prefs.activationKey = autonomousKey
    if (BuildConfig.LEGACY_LICENSE_ENABLED) {
      org.junit.Assert.assertTrue(prefs.isLicenseValid())
    } else {
      org.junit.Assert.assertFalse(prefs.isLicenseValid())
    }

    // Verify key from user's device MAC matches autonomous generator
    val sampleMac = "9D:59:2E:00:6A:1F"
    @Suppress("DEPRECATION")
    val sampleGeneratedKey = prefs.generateAutonomousKeyForDevice(sampleMac)
    org.junit.Assert.assertEquals("MK-9F83-B09E-F4D4", sampleGeneratedKey)
  }
}
