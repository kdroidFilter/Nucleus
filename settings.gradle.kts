pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

plugins {
    id("com.gradle.develocity") version "4.3.2"
}

develocity {
    buildScan.termsOfUseUrl = "https://gradle.com/terms-of-service"
    buildScan.termsOfUseAgree = "yes"
    buildScan.publishing.onlyIf {
        System.getenv("GITHUB_ACTIONS") == "true" &&
            it.buildResult.failures.isNotEmpty()
    }
}

rootProject.name = "Nucleus"

include(":example")
include(":core-runtime")
include(":aot-runtime")
include(":updater-runtime")
include(":darkmode-detector")
include(":native-ssl")
include(":native-http")
include(":native-http-okhttp")
include(":native-http-ktor")
include(":decorated-window")
include(":decorated-window-material")
include(":nucleus-notification")
includeBuild("plugin-build")
