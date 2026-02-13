import io.github.kdroidfilter.composedeskkit.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.compose") version "2.3.10"
    id("io.github.kdroidfilter.composedeskkit")
}

val currentTarget: String by lazy {
    val os = System.getProperty("os.name")
    val arch = System.getProperty("os.arch")
    val osId =
        when {
            os.equals("Mac OS X", ignoreCase = true) -> "macos"
            os.startsWith("Win", ignoreCase = true) -> "windows"
            os.startsWith("Linux", ignoreCase = true) -> "linux"
            else -> error("Unsupported OS: $os")
        }
    val archId =
        when (arch) {
            "x86_64", "amd64" -> "x64"
            "aarch64" -> "arm64"
            else -> error("Unsupported arch: $arch")
        }
    "$osId-$archId"
}

dependencies {
    implementation("org.jetbrains.compose.desktop:desktop-jvm-$currentTarget:1.10.0")
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
}

composeDeskKit.desktop.application {
    mainClass = "com.example.demo.MainKt"

    nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Deb, TargetFormat.Nsis)

        packageName = "ComposeDeskKitDemo"
        packageVersion = "1.0.0"
        description = "Demo application for ComposeDeskKit"
        vendor = "KDroidFilter"

        linux {
            appCategory = "Utility"
        }

        windows {
            menu = true
            shortcut = true
            nsis {
                oneClick = false
                allowElevation = true
                perMachine = true
            }
        }

        macOS {
            bundleID = "io.github.kdroidfilter.composedeskkit.demo"
        }
    }
}
