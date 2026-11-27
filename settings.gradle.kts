pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor (YouTube Music search and streams) is published via JitPack.
        maven("https://jitpack.io")
    }
}
rootProject.name = "Myna"
include(":composeApp")
