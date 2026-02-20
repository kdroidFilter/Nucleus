# Configuration

All Nucleus configuration lives inside the `nucleus.application { }` block in your `build.gradle.kts`.

## Overview

```kotlin
nucleus.application {
    mainClass = "com.example.MainKt"
    jvmArgs += listOf("-Xmx512m")

    buildTypes {
        release {
            proguard {
                isEnabled = true
                optimize = true
                obfuscate.set(false)
                configurationFiles.from(project.file("proguard-rules.pro"))
            }
        }
    }

    nativeDistributions {
        // Target formats
        targetFormats(TargetFormat.Dmg, TargetFormat.Nsis, TargetFormat.Deb)

        // Package metadata
        packageName = "MyApp"
        packageVersion = "1.0.0"
        description = "My awesome desktop app"
        vendor = "My Company"
        copyright = "Copyright 2025 My Company"
        homepage = "https://myapp.example.com"
        licenseFile.set(project.file("LICENSE"))

        // JDK modules
        modules("java.sql", "java.net.http")

        // Nucleus features
        cleanupNativeLibs = true
        enableAotCache = true
        splashImage = "splash.png"
        compressionLevel = CompressionLevel.Maximum
        artifactName = "${name}-${version}-${os}-${arch}.${ext}"

        // Deep links & file associations
        protocol("MyApp", "myapp")
        fileAssociation(
            mimeType = "application/x-myapp",
            extension = "myapp",
            description = "MyApp Document",
        )

        // Publishing
        publish { /* ... */ }

        // Platform-specific
        macOS { /* ... */ }
        windows { /* ... */ }
        linux { /* ... */ }
    }
}
```

## Target Formats

All available formats:

| Format | Platform | Backend | Task Name |
|--------|----------|---------|-----------|
| `TargetFormat.Dmg` | macOS | electron-builder | `packageDmg` |
| `TargetFormat.Pkg` | macOS | electron-builder | `packagePkg` |
| `TargetFormat.Exe` | Windows | electron-builder | `packageExe` |
| `TargetFormat.Msi` | Windows | electron-builder | `packageMsi` |
| `TargetFormat.Nsis` | Windows | electron-builder | `packageNsis` |
| `TargetFormat.NsisWeb` | Windows | electron-builder | `packageNsisWeb` |
| `TargetFormat.Portable` | Windows | electron-builder | `packagePortable` |
| `TargetFormat.AppX` | Windows | electron-builder | `packageAppX` |
| `TargetFormat.Deb` | Linux | electron-builder | `packageDeb` |
| `TargetFormat.Rpm` | Linux | electron-builder | `packageRpm` |
| `TargetFormat.AppImage` | Linux | electron-builder | `packageAppImage` |
| `TargetFormat.Snap` | Linux | electron-builder | `packageSnap` |
| `TargetFormat.Flatpak` | Linux | electron-builder | `packageFlatpak` |
| `TargetFormat.Zip` | All | electron-builder | `packageZip` |
| `TargetFormat.Tar` | All | electron-builder | `packageTar` |
| `TargetFormat.SevenZ` | All | electron-builder | `packageSevenZ` |

Target all formats at once:

```kotlin
targetFormats(*TargetFormat.entries.toTypedArray())
```

## Package Metadata

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `packageName` | `String` | Gradle project name | Application display name |
| `packageVersion` | `String` | Gradle project version | Application version |
| `description` | `String?` | `null` | Short application description |
| `vendor` | `String?` | `null` | Publisher / company name |
| `copyright` | `String?` | `null` | Copyright notice |
| `homepage` | `String?` | `null` | Application homepage URL |
| `licenseFile` | `RegularFileProperty` | — | Path to the license file |
| `appResourcesRootDir` | `DirectoryProperty` | — | Root directory for bundled resources |
| `outputBaseDir` | `DirectoryProperty` | `build/compose/binaries` | Output directory for built packages |

### Version Formats

Different platforms have different version requirements:

| Platform | Format | Example |
|----------|--------|---------|
| macOS | `MAJOR.MINOR.PATCH` | `1.2.3` |
| Windows | `MAJOR.MINOR.BUILD` | `1.2.3` |
| Linux DEB | `[EPOCH:]UPSTREAM[-REVISION]` | `1.2.3` |
| Linux RPM | No dashes | `1.2.3` |

## Nucleus-Specific Features

### Native Library Cleanup

Strips `.dll`, `.so`, `.dylib` files for non-target platforms from dependency JARs, reducing package size significantly.

```kotlin
nativeDistributions {
    cleanupNativeLibs = true
}
```

### AOT Cache (JDK 25+)

Generates an ahead-of-time compilation cache for faster startup:

```kotlin
nativeDistributions {
    enableAotCache = true
}
```

Requires JDK 25+ and that the application self-terminates during the training run. See [AOT Cache](runtime/aot-cache.md) for the application-side helper.

### Splash Screen

Displays a splash screen during application startup:

```kotlin
nativeDistributions {
    splashImage = "splash.png" // Relative to appResources
}
```

### Artifact Naming

Customize output filenames with template variables:

```kotlin
nativeDistributions {
    artifactName = "${name}-${version}-${os}-${arch}.${ext}"
}
```

| Variable | Description | Example |
|----------|-------------|---------|
| `${name}` | Package name | `MyApp` |
| `${version}` | Package version | `1.0.0` |
| `${os}` | Operating system | `macos`, `windows`, `linux` |
| `${arch}` | Architecture | `amd64`, `arm64` |
| `${ext}` | File extension | `dmg`, `exe`, `deb` |

### Compression Level

Controls compression for electron-builder formats:

```kotlin
nativeDistributions {
    compressionLevel = CompressionLevel.Maximum
}
```

| Level | Description |
|-------|-------------|
| `CompressionLevel.Store` | No compression |
| `CompressionLevel.Fast` | Fast compression |
| `CompressionLevel.Normal` | Balanced (default) |
| `CompressionLevel.Maximum` | Best compression |

### Trusted CA Certificates

Import custom CA certificates into the bundled JVM's `cacerts` keystore at build time.
Useful for corporate proxies, VPN gateways, or filtering services that use a private root CA.

```kotlin
nativeDistributions {
    trustedCertificates.from(files(
        "certs/company-proxy-ca.pem",
    ))
}
```

Both PEM and DER formats are accepted. See [Trusted CA Certificates](trusted-certificates.md) for full details.

### Deep Links (Protocol Handler)

Register a custom URL protocol across all platforms:

```kotlin
nativeDistributions {
    protocol("MyApp", "myapp")
    // Registers myapp:// protocol
}
```

- **macOS**: `CFBundleURLTypes` in `Info.plist`
- **Windows**: Registry entries via NSIS/MSI
- **Linux**: `MimeType` in `.desktop` file

See [Deep Links](runtime/deep-links.md) for handling incoming deep links.

### File Associations

Register file type associations:

```kotlin
nativeDistributions {
    fileAssociation(
        mimeType = "application/x-myapp",
        extension = "myapp",
        description = "MyApp Document",
    )
}
```

The cross-platform `fileAssociation()` method also accepts per-platform icon files:

```kotlin
fileAssociation(
    mimeType = "application/x-myapp",
    extension = "myapp",
    description = "MyApp Document",
    linuxIconFile = project.file("icons/file.png"),
    windowsIconFile = project.file("icons/file.ico"),
    macOSIconFile = project.file("icons/file.icns"),
)
```

Multiple associations are supported by calling `fileAssociation()` multiple times.

## ProGuard (Release Builds)

```kotlin
buildTypes {
    release {
        proguard {
            version = "7.8.1"
            isEnabled = true
            optimize = true
            obfuscate.set(false)  // Disabled by default
            joinOutputJars.set(true)
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
    }
}
```

Release build tasks are suffixed with `Release`:

```bash
./gradlew packageReleaseDmg
./gradlew packageReleaseNsis
./gradlew packageReleaseDistributionForCurrentOS
```

## Full DSL Tree

```
nucleus.application {
    mainClass
    jvmArgs
    buildTypes {
        release {
            proguard { isEnabled, version, optimize, obfuscate, joinOutputJars, configurationFiles }
        }
    }
    nativeDistributions {
        targetFormats(...)
        packageName, packageVersion, description, vendor, copyright, homepage
        licenseFile, appResourcesRootDir, outputBaseDir
        modules(...), includeAllModules
        cleanupNativeLibs, enableAotCache, splashImage
        compressionLevel, artifactName
        protocol(name, vararg schemes)
        fileAssociation(mimeType, extension, description, linuxIconFile?, windowsIconFile?, macOSIconFile?)
        publish { github { ... }, s3 { ... }, generic { ... } }
        macOS { iconFile, bundleID, dockName, appCategory, layeredIconDir, signing { ... }, notarization { ... }, dmg { ... }, infoPlist { ... } }
        windows { iconFile, upgradeUuid, signing { ... }, nsis { ... }, appx { ... } }
        linux { iconFile, debMaintainer, debDepends, rpmRequires, appImage { ... }, snap { ... }, flatpak { ... } }
    }
}
```

## Next Steps

- Platform-specific configuration: [macOS](targets/macos.md) · [Windows](targets/windows.md) · [Linux](targets/linux.md)
- [Code Signing](code-signing.md) — Sign and notarize your app
- [Publishing](publishing.md) — Distribute via GitHub Releases, S3, or generic HTTP server
