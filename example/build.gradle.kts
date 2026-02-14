import io.github.kdroidfilter.nucleus.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10"
    id("org.jetbrains.compose") version "1.10.1"
    id("io.github.kdroidfilter.nucleus")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation(project(":aot-runtime"))
    implementation("io.github.kdroidfilter:composenativetray:1.1.0") // for check clean native libs
}

val releaseVersion =
    System.getenv("RELEASE_VERSION")
        ?.removePrefix("v")
        ?.takeIf { it.isNotBlank() }
        ?: "1.0.0"

nucleus.application {
    mainClass = "com.example.demo.MainKt"

    buildTypes {
        release {
            proguard {
                version = "7.8.1"
                isEnabled = true
                optimize = true
            }
        }
    }

    nativeDistributions {
        targetFormats(*TargetFormat.entries.toTypedArray())
        appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

        packageName = "NucleusDemo"
        packageVersion = releaseVersion

        // ============================================================
        // Nucleus options
        // ============================================================

        // --- Native libs handling ---
        cleanupNativeLibs = true          // Auto cleanup native libraries
        enableAotCache = true              // Enable AOT compilation cache
        splashImage = "splash.png"         // Splash screen image file
        homepage = "https://github.com/KdroidFilter/ComposeDeskKitDemo"

        // --- Compression ---
        // Values: "store", "fast", "normal", "maximum"
        compressionLevel = "maximum"

        // --- Artifact naming ---
        // Variables: ${name}, ${version}, ${os}, ${arch}, ${ext}
        artifactName = $$"${name}-${version}-${os}-${arch}.${ext}"

        // --- Deep links protocol ---
        // Registers custom protocol handler (e.g., nucleus://open)
        protocol("NucleusDemo", "nucleus")

        // --- File associations ---
        fileAssociation(
            mimeType = "application/x-nucleus",
            extension = "cdk",
            description = "Nucleus Document"
        )

        // --- Publish to GitHub/S3 ---
        publish {
            github {
                enabled = true
                owner = "kdroidfilter"
                repo = "ComposeDeskKit"
                channel = "latest"          // or "beta", "alpha"
                releaseType = "release"    // or "draft", "prerelease"
            }
            // s3 { ... }
        }

        // ========== LINUX ==========
        linux {
            // --- DEB package ---
            debMaintainer = "KDroidFilter <dev@kdroidfilter.com>"
            debDepends = listOf("libfuse2", "libgtk-3-0")
            debPackageVersion = releaseVersion

            // --- RPM package ---
            rpmRequires = listOf("gtk3", "libX11")
            rpmPackageVersion = releaseVersion

            // --- AppImage (NEW) ---
            appImage {
                // Category: "AudioVideo", "Development", "Game", "Graphics", "Network", "Office", "Science", "Settings", "System", "Utility"
                category = "Utility"
                genericName = "Nucleus Demo"
                synopsis = "Demo app using Nucleus"
                desktopEntries = mapOf("StartupWMClass" to "NucleusDemo")
            }

            // --- Snap (NEW) ---
            snap {
                // Confinement: "strict", "classic", "devmode"
                confinement = "strict"
                // Grade: "stable", "devel"
                grade = "stable"
                summary = "Nucleus demo"
                base = "core22"
                // Plugs: "desktop", "desktop-legacy", "home", "x11", "wayland", "network", "audio", " removable-media"
                plugs = listOf("desktop", "home", "network")
                autoStart = false
                // Compression: "xz", "gzip"
                compression = "xz"
            }

            // --- Flatpak (NEW) ---
            flatpak {
                runtime = "org.freedesktop.Platform"
                runtimeVersion = "24.08"    // or "24.08", etc.
                sdk = "org.freedesktop.Sdk"
                branch = "master"
                // Finish args: "--share=ipc", "--socket=x11", "--socket=wayland", "--socket=pulseaudio", "--device=dri", "--filesystem=home"
                finishArgs = listOf("--share=ipc", "--socket=x11", "--socket=wayland")
            }
        }

        // ========== WINDOWS ==========
        windows {
            // --- Upgrade UUID ---
            // Used for Windows updates (auto-generated if null)
            upgradeUuid = "d24e3b8d-3e9b-4cc7-a5d8-5e2d1f0c9f1b"

            // --- Code signing (NEW) ---
            signing {
                enabled = true
                certificateFile.set(file("cert.pfx"))
                certificatePassword = "password"
                // Algorithm: "sha256", "sha1", "sha512"
                algorithm = "sha256"
                // Timestamp servers: "http://timestamp.digicert.com", "http://timestamp.sectigo.com", "http://timestamp.globalsign.com"
                timestampServer = "http://timestamp.digicert.com"
            }

            // --- NSIS Installer (NEW) ---
            nsis {
                oneClick = false            // Default: true
                allowElevation = true       // Default: true
                perMachine = true           // Default: false (current user)
                allowToChangeInstallationDirectory = true  // Default: false
                createDesktopShortcut = true
                createStartMenuShortcut = true
                runAfterFinish = true
                deleteAppDataOnUninstall = false  // Default: false
                multiLanguageInstaller = true    // Default: false
                // Languages: "en_US", "fr_FR", "de_DE", "es_ES", "ja_JP", "zh_CN", etc.
                installerLanguages = listOf("en_US", "fr_FR")
            }

            // --- AppX/Windows Store (NEW) ---
            appx {
                applicationId = "NucleusDemo"
                publisherDisplayName = "KDroidFilter"
                displayName = "Nucleus Demo"
                // Publisher: "CN=..."
                publisher = "CN=D541E802-6D30-446A-864E-2E8ABD2DAA5E"
                identityName = "KDroidFilter.NucleusDemo"
                // Languages: "en-US", "fr-FR", "de-DE", etc.
                languages = listOf("en-US", "fr-FR")
            }
        }

        // ========== MACOS ==========
        macOS {
            bundleID = "io.github.kdroidfilter.nucleus.demo"
            appCategory = "public.app-category.utilities"
            dockName = "NucleusDemo"

            // --- Code signing ---
            signing {
                sign = false
                identity.set(System.getenv("MAC_SIGN_IDENTITY"))
            }

            // --- Notarization ---
            notarization {
                appleID.set(System.getenv("MAC_NOTARIZATION_APPLE_ID"))
                password.set(System.getenv("MAC_NOTARIZATION_PASSWORD"))
                teamID.set(System.getenv("MAC_NOTARIZATION_TEAM_ID"))
            }

            // --- Layered Icons (NEW - macOS 26+) ---
            layeredIconDir.set(layout.projectDirectory.dir("packaging/macos-layered-icon"))
        }
    }
}
