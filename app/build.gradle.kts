plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.personal.callrecorder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.personal.callrecorder"
        minSdk = 26          // Android 8.0 — TelephonyCallback (API 31+) is used where available.
        // Target 33 (not 34+) ON PURPOSE for this sideloaded personal app: the
        // Android 14 restriction that blocks starting a microphone foreground
        // service from the background (and its "while-in-use eligibility" check)
        // only applies to apps targeting SDK 34+. Targeting 33 gives legacy FGS
        // behavior, so — combined with the SYSTEM_ALERT_WINDOW exemption — the
        // recording service can start automatically when a call begins, even on
        // Android 15/16. This is not Play-Store-publishable (Play requires 34+),
        // which is fine: we sideload.
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Debuggable, no shrinking — sideloadable APK for personal use.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / lifecycle / activity
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (via BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // DataStore (settings)
    implementation(libs.androidx.datastore.preferences)

    // Media3 (audio playback)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    // Biometric + fragment host
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)

    // Storage Access Framework helper (import OEM call recordings)
    implementation(libs.androidx.documentfile)

    // Serialization (AI/transcription payloads)
    implementation(libs.kotlinx.serialization.json)
}
