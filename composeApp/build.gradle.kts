import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // The Windows app (jvm "desktop" target) is added in the next phase and reuses commonMain.
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.9.2")
            implementation("androidx.media3:media3-exoplayer:1.4.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
            // YouTube Music: search, and the signature solving that makes stream URLs playable (same version as FireTheDJ).
            implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")
        }
    }
}

android {
    namespace = "com.myna.player"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.myna.player"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-alpha"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
