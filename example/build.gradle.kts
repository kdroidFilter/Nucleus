import io.github.kdroidfilter.composedeskkit.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.compose") version "2.3.10"
    id("io.github.kdroidfilter.composedeskkit")
}

val currentOsId: String by lazy {
    val os = System.getProperty("os.name")
    when {
        os.equals("Mac OS X", ignoreCase = true) -> "macos"
        os.startsWith("Win", ignoreCase = true) -> "windows"
        os.startsWith("Linux", ignoreCase = true) -> "linux"
        else -> error("Unsupported OS: $os")
    }
}
val currentArchId: String by lazy {
    val arch = System.getProperty("os.arch")
    when (arch) {
        "x86_64", "amd64" -> "x64"
        "aarch64" -> "arm64"
        else -> error("Unsupported arch: $arch")
    }
}
val currentTarget: String by lazy { "$currentOsId-$currentArchId" }

val defaultWindowsIcon =
    rootProject.layout.projectDirectory.file(
        "plugin-build/plugin/src/main/resources/default-compose-desktop-icon-windows.ico",
    )
val defaultMacIcon =
    rootProject.layout.projectDirectory.file(
        "plugin-build/plugin/src/main/resources/default-compose-desktop-icon-mac.icns",
    )
val defaultLinuxIcon =
    rootProject.layout.projectDirectory.file(
        "plugin-build/plugin/src/main/resources/default-compose-desktop-icon-linux.png",
    )

val packagingDir = layout.projectDirectory.dir("packaging")
val appResourcesRoot = layout.projectDirectory.dir("app-resources")
val splashImageName = "splash.png"
val packagingLicenseFile = packagingDir.file("license.txt")
val nsisIncludeFile = packagingDir.file("nsis/include.nsh")
val macEntitlements = packagingDir.file("macos/entitlements.plist")
val macRuntimeEntitlements = packagingDir.file("macos/runtime-entitlements.plist")
val macProvisioningProfile = packagingDir.file("macos/embedded.provisionprofile")
val macRuntimeProvisioningProfile = packagingDir.file("macos/runtime-embedded.provisionprofile")
val windowsSigningCert = packagingDir.file("KDroidFilter.pfx")
val appxStoreLogo = packagingDir.file("appx/StoreLogo.png")
val appxSquare44x44Logo = packagingDir.file("appx/Square44x44Logo.png")
val appxSquare150x150Logo = packagingDir.file("appx/Square150x150Logo.png")
val appxWide310x150Logo = packagingDir.file("appx/Wide310x150Logo.png")
val windowsSigningPassword =
    providers.gradleProperty("windowsSigningPassword").orElse("ChangeMe-Temp123!").get()

val nsisInstallerHeaderFile =
    providers.gradleProperty("nsisInstallerHeader").map { layout.projectDirectory.file(it) }
val nsisInstallerSidebarFile =
    providers.gradleProperty("nsisInstallerSidebar").map { layout.projectDirectory.file(it) }
val nsisScriptFile =
    providers.gradleProperty("nsisScript").map { layout.projectDirectory.file(it) }

val windowsInstallDir = """C:\Program Files\ComposeDeskKitDemo"""
val linuxInstallDir = "/opt/composedeskkitdemo"
val macInstallDir = "/Applications"

val windowsUpgradeUuid = "d24e3b8d-3e9b-4cc7-a5d8-5e2d1f0c9f1b"

dependencies {
    implementation("org.jetbrains.compose.desktop:desktop-jvm-$currentTarget:1.10.0")
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
}

composeDeskKit.desktop.application {
    mainClass = "com.example.demo.MainKt"
    args("--example", "true")
    jvmArgs("-Ddemo.flag=true")

    buildTypes {
        release {
            proguard {
                version.set("7.7.0")
                maxHeapSize.set("1g")
                configurationFiles.from(layout.projectDirectory.file("proguard-rules.pro"))
                isEnabled.set(false)
                obfuscate.set(false)
                optimize.set(true)
                joinOutputJars.set(false)
            }
        }
    }

    nativeDistributions {
        outputBaseDir.set(layout.buildDirectory.dir("compose/binaries"))
        targetFormats(*TargetFormat.values())

        packageName = "ComposeDeskKitDemo"
        packageVersion = "1.0.0"
        copyright = "Copyright (c) 2025 KDroidFilter"
        description = "Demo application for ComposeDeskKit"
        vendor = "KDroidFilter"
        appResourcesRootDir.set(appResourcesRoot)
        licenseFile.set(packagingLicenseFile)

        cleanupNativeLibs = false
        includeAllModules = false
        enableAotCache = true
        splashImage = splashImageName

        modules(
            "jdk.accessibility",
            "jdk.unsupported",
        )

        compressionLevel = "normal"
        artifactName = $$"${name}-${version}-${os}-${arch}.${ext}"

        protocol("ComposeDeskKitDemo", "composedeskkit")

        fileAssociation(
            mimeType = "application/x-composedeskkit",
            extension = "cdk",
            description = "ComposeDeskKit Document",
            linuxIconFile = defaultLinuxIcon.asFile,
            windowsIconFile = defaultWindowsIcon.asFile,
            macOSIconFile = defaultMacIcon.asFile,
        )

        publish {
            github {
                enabled = true
                owner = "kdroidfilter"
                repo = "ComposeDeskKit"
                token = System.getenv("GITHUB_TOKEN")
                channel = "latest"
                releaseType = "release"
            }
            s3 {
                enabled = false
                bucket = "compose-deskkit-demo"
                region = "us-east-1"
                path = "releases"
                acl = "public-read"
            }
        }

        linux {
            iconFile.set(defaultLinuxIcon)
            packageName = "ComposeDeskKitDemo"
            packageVersion = "1.0.0"
            installationPath = linuxInstallDir
            shortcut = true
            appRelease = "1"
            appCategory = "Utility"
            debMaintainer = "KDroidFilter <dev@kdroidfilter.com>"
            menuGroup = "Utilities"
            rpmLicenseType = "Apache-2.0"
            debPackageVersion = "1.0.0"
            rpmPackageVersion = "1.0.0"
            debDepends = listOf("libfuse2", "libgtk-3-0")
            rpmRequires = listOf("gtk3", "libX11")

            fileAssociation(
                mimeType = "application/x-composedeskkit",
                extension = "cdk",
                description = "ComposeDeskKit Document",
                iconFile = defaultLinuxIcon.asFile,
            )

            appImage {
                category = "Utility"
                genericName = "ComposeDeskKit Demo"
                synopsis = "Demo app using ComposeDeskKit"
                desktopEntries =
                    mapOf(
                        "StartupWMClass" to "ComposeDeskKitDemo",
                    )
            }

            snap {
                confinement = "strict"
                grade = "stable"
                summary = "ComposeDeskKit demo"
                base = "core22"
                plugs =
                    listOf(
                        "desktop",
                        "desktop-legacy",
                        "home",
                        "x11",
                        "wayland",
                        "network",
                    )
                autoStart = false
                compression = "xz"
            }

            flatpak {
                runtime = "org.freedesktop.Platform"
                runtimeVersion = "23.08"
                sdk = "org.freedesktop.Sdk"
                branch = "master"
                finishArgs =
                    listOf(
                        "--share=ipc",
                        "--socket=x11",
                        "--socket=wayland",
                    )
                license.set(packagingLicenseFile)
            }
        }

        windows {
            iconFile.set(defaultWindowsIcon)
            packageVersion = "1.0.0"
            installationPath = windowsInstallDir
            console = false
            dirChooser = true
            perUserInstall = false
            shortcut = true
            menu = true
            menuGroup = "ComposeDeskKit"
            upgradeUuid = windowsUpgradeUuid
            msiPackageVersion = "1.0.0"
            exePackageVersion = "1.0.0"

            fileAssociation(
                mimeType = "application/x-composedeskkit",
                extension = "cdk",
                description = "ComposeDeskKit Document",
                iconFile = defaultWindowsIcon.asFile,
            )

            signing {
                enabled = true
                certificateFile.set(windowsSigningCert)
                certificatePassword = windowsSigningPassword
                certificateSha1 = null
                certificateSubjectName = null
                timestampServer = "http://timestamp.digicert.com"
                algorithm = "sha256"
                azureTenantId = null
                azureEndpoint = null
                azureCertificateProfileName = null
                azureCodeSigningAccountName = null
            }

            nsis {
                oneClick = false
                allowElevation = true
                perMachine = true
                allowToChangeInstallationDirectory = true
                createDesktopShortcut = true
                createStartMenuShortcut = true
                runAfterFinish = true
                deleteAppDataOnUninstall = false
                multiLanguageInstaller = true
                installerLanguages = listOf("en_US", "fr_FR")
                installerIcon.set(defaultWindowsIcon)
                uninstallerIcon.set(defaultWindowsIcon)
                license.set(packagingLicenseFile)
                includeScript.set(nsisIncludeFile)
                installerHeader.set(nsisInstallerHeaderFile)
                installerSidebar.set(nsisInstallerSidebarFile)
                script.set(nsisScriptFile)
            }

            appx {
                applicationId = "ComposeDeskKitDemo"
                publisherDisplayName = "KDroidFilter"
                displayName = "ComposeDeskKit Demo"
                publisher = "CN=D541E802-6D30-446A-864E-2E8ABD2DAA5E"
                identityName = "KDroidFilter.ComposeDeskKitDemo"
                languages = listOf("en-US", "fr-FR")
                addAutoLaunchExtension = false
                if (appxStoreLogo.asFile.exists()) storeLogo.set(appxStoreLogo)
                if (appxSquare44x44Logo.asFile.exists()) square44x44Logo.set(appxSquare44x44Logo)
                if (appxSquare150x150Logo.asFile.exists()) square150x150Logo.set(appxSquare150x150Logo)
                if (appxWide310x150Logo.asFile.exists()) wide310x150Logo.set(appxWide310x150Logo)
            }
        }

        macOS {
            iconFile.set(defaultMacIcon)
            packageName = "ComposeDeskKitDemo"
            packageVersion = "1.0.0"
            packageBuildVersion = "1.0.0"
            dmgPackageVersion = "1.0.0"
            dmgPackageBuildVersion = "1.0.0"
            pkgPackageVersion = "1.0.0"
            pkgPackageBuildVersion = "1.0.0"
            installationPath = macInstallDir
            appCategory = "public.app-category.utilities"
            minimumSystemVersion = "10.13"
            bundleID = "io.github.kdroidfilter.composedeskkit.demo"
            dockName = "ComposeDeskKitDemo"
            setDockNameSameAsPackageName = false
            appStore = false
            entitlementsFile.set(macEntitlements)
            runtimeEntitlementsFile.set(macRuntimeEntitlements)
            provisioningProfile.set(macProvisioningProfile)
            runtimeProvisioningProfile.set(macRuntimeProvisioningProfile)
            layeredIconDir.set(layout.projectDirectory.dir("packaging/macos-layered-icon"))

            signing {
                sign.set(false)
                identity.set(System.getenv("MAC_SIGN_IDENTITY"))
                keychain.set(System.getenv("MAC_SIGN_KEYCHAIN"))
                prefix.set("Developer ID Application")
            }

            notarization {
                appleID.set(System.getenv("MAC_NOTARIZATION_APPLE_ID"))
                password.set(System.getenv("MAC_NOTARIZATION_PASSWORD"))
                teamID.set(System.getenv("MAC_NOTARIZATION_TEAM_ID"))
            }

            infoPlist {
                extraKeysRawXml =
                    """
                    <key>NSMicrophoneUsageDescription</key>
                    <string>ComposeDeskKit demo uses the microphone.</string>
                    """.trimIndent()
            }

            fileAssociation(
                mimeType = "application/x-composedeskkit",
                extension = "cdk",
                description = "ComposeDeskKit Document",
                iconFile = defaultMacIcon.asFile,
            )
        }
    }
}
