import java.util.Base64

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

// Assegura que o google-services.json exista no ambiente de compilação (ex: CI/CD do GitHub Actions)
val googleServicesFile = File(projectDir, "google-services.json")
if (!googleServicesFile.exists()) {
  try {
    googleServicesFile.writeText(
      """
      {
        "project_info": {
          "project_number": "736577956980",
          "project_id": "gen-lang-client-0474959166",
          "storage_bucket": "gen-lang-client-0474959166.firebasestorage.app"
        },
        "client": [
          {
            "client_info": {
              "mobilesdk_app_id": "1:736577956980:android:8b24803d286868ab723521",
              "android_client_info": {
                "package_name": "com.aistudio.multiservidor.iptvmk"
              }
            },
            "oauth_client": [],
            "api_key": [
              {
                "current_key": "AIzaSyDbFO4VGaBNxxphP99Mu6NbKZ1nATzHMmI"
              }
            ],
            "services": {
              "appinvite_service": {
                "other_platform_oauth_client": []
              }
            }
          }
        ],
        "configuration_version": "1"
      }
      """.trimIndent()
    )
    println("Fallback google-services.json gerado automaticamente em ${googleServicesFile.absolutePath}")
  } catch (e: Exception) {
    System.err.println("Erro ao gerar fallback google-services.json: ${e.message}")
  }
}

fun decodeBase64Keystore(
  keystoreFile: java.io.File,
  base64File: java.io.File
): Boolean {
  if (keystoreFile.exists()) return true
  if (!base64File.exists()) return false
  val base64Text = base64File.readText().trim()
  val cleanBase64 = base64Text.replace("\\s".toRegex(), "")
  val decodedBytes = Base64.getDecoder().decode(cleanBase64)
  keystoreFile.parentFile?.mkdirs()
  keystoreFile.writeBytes(decodedBytes)
  println("Decoded keystore from base64 to ${keystoreFile.absolutePath}")
  return true
}

fun ensureDebugKeystore(
  keystoreFile: java.io.File,
  base64File: java.io.File
) {
  if (decodeBase64Keystore(keystoreFile, base64File)) return

  try {
    println("Debug keystore not found. Generating on-the-fly: ${keystoreFile.absolutePath}")
    val storeType = if (keystoreFile.name.endsWith(".jks", ignoreCase = true)) "JKS" else "PKCS12"
    val pb = ProcessBuilder(
      "keytool", "-genkeypair",
      "-noprompt",
      "-keystore", keystoreFile.absolutePath,
      "-storetype", storeType,
      "-keyalg", "RSA",
      "-keysize", "2048",
      "-validity", "10000",
      "-alias", "androiddebugkey",
      "-storepass", "android",
      "-keypass", "android",
      "-dname", "CN=Android Debug, O=Android, C=US"
    )
    val process = pb.start()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
      System.err.println("Keytool failed with exit code: $exitCode")
    } else {
      println("Successfully generated debug keystore: ${keystoreFile.absolutePath}")
    }
  } catch (e: Exception) {
    System.err.println("Error generating debug keystore on-the-fly: ${e.message}")
  }
}

android {
    sourceSets {
        getByName("main") {
            java.srcDirs("build/generated/ksp/main/java", "build/generated/ksp/main/kotlin")
        }
        getByName("debug") {
            java.srcDirs("build/generated/ksp/debug/java", "build/generated/ksp/debug/kotlin")
        }
        getByName("release") {
            java.srcDirs("build/generated/ksp/release/java", "build/generated/ksp/release/kotlin")
        }
    }
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.multiservidor.iptvmk"
    minSdk = 24
    targetSdk = 36
    versionCode = 280
    versionName = "2.8.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    val licenseSecret = System.getenv("LICENSE_SECRET") ?: "MK21_DEFAULT_HMAC_SECRET"
    buildConfigField("String", "LICENSE_SECRET", "\"$licenseSecret\"")

    val expectedCertSha256 = (
      project.findProperty("EXPECTED_CERT_SHA256")?.toString()
        ?: System.getenv("EXPECTED_CERT_SHA256")
        ?: ""
      )
      .replace("\"", "")
      .trim()
    buildConfigField("String", "EXPECTED_CERT_SHA256", "\"$expectedCertSha256\"")
  }

  val releaseStorePass = System.getenv("STORE_PASSWORD")?.takeIf { it.isNotBlank() }
  val releaseKeyPass = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() }
  val releaseKeystorePath = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
  val defaultReleaseKeystore = File(rootProject.rootDir, "my-upload-key.jks")
  val defaultReleaseBase64 = File(rootProject.rootDir, "my-upload-key.base64")
  val releaseKeystoreFile = if (releaseKeystorePath != null) {
    file(releaseKeystorePath)
  } else {
    defaultReleaseKeystore
  }

  signingConfigs {
    create("debugConfig") {
      enableV1Signing = true
      enableV2Signing = true
      val keystoreFile = File(rootProject.rootDir, "debug.keystore")
      val base64File = File(rootProject.rootDir, "debug.keystore.base64")
      ensureDebugKeystore(keystoreFile, base64File)
      storeFile = keystoreFile
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }

    create("release") {
      enableV1Signing = true
      enableV2Signing = true
      if (releaseStorePass != null && releaseKeyPass != null &&
        (releaseKeystoreFile.exists() || (releaseKeystorePath == null && defaultReleaseBase64.exists()))
      ) {
        if (!releaseKeystoreFile.exists() && releaseKeystorePath == null && defaultReleaseBase64.exists()) {
          decodeBase64Keystore(releaseKeystoreFile, defaultReleaseBase64)
        }
        storeFile = releaseKeystoreFile
        storePassword = releaseStorePass
        keyAlias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "upload"
        keyPassword = releaseKeyPass
      }
    }
  }

  gradle.taskGraph.whenReady {
    val isReleaseTaskRequested = allTasks.any { task ->
      val name = task.name
      (name.contains("Release", ignoreCase = true) || name == "bundle" || name == "assemble") &&
        name != "generateReleaseKeystore"
    }
    if (isReleaseTaskRequested) {
      val missingVars = mutableListOf<String>()
      if (releaseStorePass == null) missingVars.add("STORE_PASSWORD")
      if (releaseKeyPass == null) missingVars.add("KEY_PASSWORD")
      if (releaseKeystorePath == null && !defaultReleaseKeystore.exists() && !defaultReleaseBase64.exists()) {
        missingVars.add("KEYSTORE_PATH")
      }
      if (missingVars.isNotEmpty()) {
        throw GradleException(
          "Assinatura de release obrigatória ausente. Variáveis faltando: ${missingVars.joinToString(", ")}. " +
            "Defina STORE_PASSWORD, KEY_PASSWORD e KEYSTORE_PATH antes de compilar release."
        )
      }
      if (!releaseKeystoreFile.exists()) {
        if (releaseKeystorePath == null && defaultReleaseBase64.exists()) {
          decodeBase64Keystore(releaseKeystoreFile, defaultReleaseBase64)
        }
      }
      if (!releaseKeystoreFile.exists()) {
        throw GradleException(
          "Keystore de release não encontrado em '${releaseKeystoreFile.absolutePath}'. " +
            "Verifique KEYSTORE_PATH ou forneça my-upload-key.base64 existente (nunca gerar em build)."
        )
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
  implementation(libs.firebase.ai)
  implementation(libs.firebase.appcheck.playintegrity)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.okhttp.dnsoverhttps)
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
  group = "signing"
  description = "Gera manualmente my-upload-key.jks e my-upload-key.base64 (requer -PgenerateKeystore=true)."
  val generateOptIn = project.findProperty("generateKeystore")?.toString()?.equals("true", ignoreCase = true) == true
  doFirst {
    if (!generateOptIn) {
      throw GradleException("generateReleaseKeystore exige opt-in explícito: execute com -PgenerateKeystore=true.")
    }
  }
  doLast {
    if (!generateOptIn) {
      throw GradleException("generateReleaseKeystore exige opt-in explícito: execute com -PgenerateKeystore=true.")
    }
    val keystoreFile = file("${rootDir}/my-upload-key.jks")
    if (keystoreFile.exists()) {
      println("Keystore already exists at ${keystoreFile.absolutePath}")
      return@doLast
    }
    val storePass = System.getenv("STORE_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: throw GradleException("STORE_PASSWORD não definido. Configure no .env ou CI/CD.")
    val keyPass = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: storePass
    val pb = ProcessBuilder(
      "keytool", "-genkeypair",
      "-v",
      "-keystore", keystoreFile.absolutePath,
      "-keyalg", "RSA",
      "-keysize", "2048",
      "-validity", "10000",
      "-alias", "upload",
      "-storepass", storePass,
      "-keypass", keyPass,
      "-dname", "CN=Fabio Guarniere, O=MK21, C=BR"
    )
    val exitCode = pb.inheritIO().start().waitFor()
    if (exitCode != 0) {
      throw GradleException("Failed to generate keystore with keytool, exit code: $exitCode")
    }
    println("Keystore successfully created at ${keystoreFile.absolutePath}!")

    val base64File = file("${rootDir}/my-upload-key.base64")
    val bytes = keystoreFile.readBytes()
    val base64Text = Base64.getEncoder().encodeToString(bytes)
    base64File.writeText(base64Text)
    println("Base64 representation written to ${base64File.absolutePath}!")
  }
}
