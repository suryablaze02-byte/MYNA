import org.jetbrains.compose.desktop.application.dsl.TargetFormat
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
    // The Windows app. It shares commonMain (every screen) and jvmShared (online music, sound spaces) with Android.
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    sourceSets {
        androidMain { kotlin.srcDir("src/jvmShared/kotlin") }
        val desktopMain by getting {
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
                implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")
                implementation("org.json:json:20240303")
                // Plays every format (MP3, FLAC, M4A, Opus, YouTube streams) through FFmpeg.
                implementation("org.bytedeco:javacv:1.5.10") { isTransitive = false }
                implementation("org.bytedeco:javacpp:1.5.10")
                implementation("org.bytedeco:javacpp:1.5.10:windows-x86_64")
                implementation("org.bytedeco:ffmpeg:6.1.1-1.5.10")
                implementation("org.bytedeco:ffmpeg:6.1.1-1.5.10:windows-x86_64")
                // Song tags and covers in music files.
                implementation("net.jthink:jaudiotagger:3.0.1")
            }
        }
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
        versionCode = 4
        versionName = "1.2.1"
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
    // The release APK is MYNA-v1.2.0.apk, not Gradle's default composeApp-release.apk.
    applicationVariants.all {
        val v = versionName
        if (buildType.name == "release") outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = "MYNA-v$v.apk"
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.myna.player.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "MYNA"
            packageVersion = "1.2.1"
            description = "MYNA music player"
            vendor = "Surya"
            modules("java.net.http", "java.sql", "jdk.crypto.ec", "java.naming")
            windows {
                menu = true
                shortcut = true
                perUserInstall = true
                upgradeUuid = "6f1d2c7e-3b8a-4f0e-9a51-7c2d9e4b1a63"
                iconFile.set(project.file("src/desktopMain/resources/myna.ico"))
            }
        }
    }
}
