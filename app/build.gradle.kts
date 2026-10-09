import java.util.Base64

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

fun decodeBase64OrGenerateKeystore(
  keystoreFile: java.io.File,
  base64File: java.io.File,
  alias: String,
  dname: String,
  storePass: String,
  keyPass: String
) {
  if (keystoreFile.exists()) return

  if (base64File.exists()) {
    try {
      val base64Text = base64File.readText().trim()
      val cleanBase64 = base64Text.replace("\\s".toRegex(), "")
      val decodedBytes = Base64.getDecoder().decode(cleanBase64)
      keystoreFile.writeBytes(decodedBytes)
      println("Decoded keystore from base64 to ${keystoreFile.absolutePath}")
      return
    } catch (e: Exception) {
      println("Failed to decode base64 file ${base64File.name}: ${e.message}")
    }
  }

  try {
    println("Keystore not found. Generating on-the-fly: ${keystoreFile.absolutePath}")
    val storeType = if (keystoreFile.name.endsWith(".jks", ignoreCase = true)) "JKS" else "PKCS12"
    val pb = ProcessBuilder(
      "keytool", "-genkeypair",
      "-noprompt",
      "-keystore", keystoreFile.absolutePath,
      "-storetype", storeType,
      "-keyalg", "RSA",
      "-keysize", "2048",
      "-validity", "10000",
      "-alias", alias,
      "-storepass", storePass,
      "-keypass", keyPass,
      "-dname", dname
    )
    val process = pb.start()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
      System.err.println("Keytool failed with exit code: $exitCode")
    } else {
      println("Successfully generated keystore: ${keystoreFile.absolutePath}")
    }
  } catch (e: Exception) {
    System.err.println("Error generating keystore on-the-fly: ${e.message}")
  }
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.multiservidor.iptvmk"
    minSdk = 24
    targetSdk = 36
    versionCode = 6
    versionName = "1.5"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    val licenseSecret = System.getenv("LICENSE_SECRET") ?: "MK21_DEFAULT_HMAC_SECRET"
    buildConfigField("String", "LICENSE_SECRET", "\"$licenseSecret\"")
  }

  signingConfigs {
    val storePass = System.getenv("STORE_PASSWORD")
    val keyPass = System.getenv("KEY_PASSWORD")
    val hasReleaseCredentials = !storePass.isNullOrEmpty() && !keyPass.isNullOrEmpty()

    create("debugConfig") {
      val keystoreFile = File(rootProject.rootDir, "debug.keystore")
      val base64File = File(rootProject.rootDir, "debug.keystore.base64")
      decodeBase64OrGenerateKeystore(
        keystoreFile,
        base64File,
        "androiddebugkey",
        "CN=Android Debug, O=Android, C=US",
        "android",
        "android"
      )
      storeFile = keystoreFile
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }

    create("release") {
      if (hasReleaseCredentials) {
        val keystorePath = System.getenv("KEYSTORE_PATH")
        val keystoreFile = if (!keystorePath.isNullOrEmpty()) file(keystorePath) else File(rootProject.rootDir, "my-upload-key.jks")
        val base64File = File(rootProject.rootDir, "my-upload-key.base64")
        decodeBase64OrGenerateKeystore(
          keystoreFile,
          base64File,
          "upload",
          "CN=Fabio Guarniere, O=MK21, C=BR",
          storePass,
          keyPass
        )
        storeFile = keystoreFile
        storePassword = storePass
        keyAlias = "upload"
        keyPassword = keyPass
      } else {
        // Fallback to debug keystore for development / preview release builds
        val debugKeystore = File(rootProject.rootDir, "debug.keystore")
        storeFile = debugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.security.crypto)
  implementation(libs.okio)
  implementation(libs.coil.compose)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.ui)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

tasks.register("generateReleaseKeystore") {
  doLast {
    val keystoreFile = file("${rootDir}/my-upload-key.jks")
    if (keystoreFile.exists()) {
      println("Keystore already exists at ${keystoreFile.absolutePath}")
      return@doLast
    }
    val storePass = System.getenv("STORE_PASSWORD")
        ?: throw GradleException("STORE_PASSWORD não definido. Configure no .env ou CI/CD.")
    val pb = ProcessBuilder(
      "keytool", "-genkeypair",
      "-v",
      "-keystore", keystoreFile.absolutePath,
      "-keyalg", "RSA",
      "-keysize", "2048",
      "-validity", "10000",
      "-alias", "upload",
      "-storepass", storePass,
      "-keypass", storePass,
      "-dname", "CN=Fabio Guarniere, O=MK21, C=BR"
    )
    val exitCode = pb.inheritIO().start().waitFor()
    if (exitCode != 0) {
      throw GradleException("Failed to generate keystore with keytool, exit code: $exitCode")
    }
    println("Keystore successfully created at ${keystoreFile.absolutePath}!")
    
    // Now convert it to base64 so it can be committed
    val base64File = file("${rootDir}/my-upload-key.base64")
    val bytes = keystoreFile.readBytes()
    val base64Text = Base64.getEncoder().encodeToString(bytes)
    base64File.writeText(base64Text)
    println("Base64 representation written to ${base64File.absolutePath}!")
  }
}

