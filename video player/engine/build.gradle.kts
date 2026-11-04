// OffScroll Engine: a separate, optional app that holds yt-dlp, Python and ffmpeg (about 50 MB).
// The main OffScroll app talks to it through a bound service (see EngineService), so the main
// app stays small and only people who want "Any link" downloads install this one.

plugins {
  alias(libs.plugins.android.application)
}

android {
  namespace = "com.example.engine"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.offscroll.engine"
    minSdk = 24
    targetSdk = 36
    // CI sets GITHUB_RUN_NUMBER, same as the main app, so both APKs of one release share a build number
    versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
    versionName = "1.0"

    // yt-dlp's Python + ffmpeg ship per CPU type (~50 MB each). 64-bit ARM covers practically every
    // phone made since 2017; add "armeabi-v7a" here for very old 32-bit phones.
    ndk { abiFilters.add("arm64-v8a") }
  }

  // Must be the SAME key as the main app: the service permission is signature-level
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      // Not minified: the size is native code, and the yt-dlp library reads JSON by reflection
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  packaging {
    // Python and ffmpeg are stored as native libraries and unpacked at runtime
    jniLibs { useLegacyPackaging = true }
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.youtubedl.android.library)
  implementation(libs.youtubedl.android.ffmpeg)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
}
