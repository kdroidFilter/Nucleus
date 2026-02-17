<p align="center">
  <img src="art/header.png" alt="Nucleus" />
</p>

# Nucleus

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.kdroidfilter.nucleus?label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/io.github.kdroidfilter.nucleus)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kdroidfilter/nucleus-core-runtime?label=Maven%20Central)](https://central.sonatype.com/search?q=io.github.kdroidfilter.nucleus)

Nucleus is a Gradle plugin for building, packaging, and distributing **JVM desktop applications** as native installers on macOS, Windows, and Linux. It is compatible with any JVM application but optimized for **Compose Desktop**. It uses the Compose Desktop API for app-image generation and [electron-builder](https://www.electron.build/) under the hood to produce final installers with code signing, auto-update metadata, and advanced packaging options.

## Why Nucleus?

- **16 target formats** — DMG, PKG, NSIS, MSI, AppX, Portable, DEB, RPM, AppImage, Snap, Flatpak, and archive formats
- **One DSL** — Configure everything from a single `nucleus.application { }` block
- **Auto-update built-in** — The CI `generate-update-yml` action scans all build artifacts and produces combined `latest-mac.yml`, `latest.yml`, `latest-linux.yml` files covering all architectures per platform; the `publish` DSL can also configure electron-builder to publish directly to GitHub Releases or S3
- **Code signing** — Windows (PFX, Azure Trusted Signing) and macOS (Apple Developer ID, notarization)
- **CI/CD ready** — `setup-nucleus` composite action + GitHub Actions workflows for 6-runner multi-platform builds, universal macOS binaries, and MSIX bundles
- **Performance** — Native library cleanup, JDK 25+ AOT cache, splash screen support
- **Deep links & file associations** — Cross-platform protocol handlers and file type registration

## Documentation

| Guide | Description |
|-------|-------------|
| [Getting Started](docs/getting-started.md) | Installation, first build, Gradle tasks |
| [Configuration](docs/configuration.md) | Full DSL reference for `nucleus.application { }` |
| **Targets** | |
| [macOS](docs/targets/macos.md) | DMG, PKG, layered icons, universal binaries |
| [Windows](docs/targets/windows.md) | NSIS, MSI, AppX, Portable, code signing |
| [Linux](docs/targets/linux.md) | DEB, RPM, AppImage, Snap, Flatpak |
| **Features** | |
| [Sandboxing](docs/sandboxing.md) | App Sandbox (macOS), UWP (Windows), Flatpak (Linux) |
| [Code Signing](docs/code-signing.md) | Windows & macOS signing and notarization |
| [Auto Update](docs/auto-update.md) | Update metadata, runtime library, providers |
| [Publishing](docs/publishing.md) | GitHub Releases, S3, release channels |
| [CI/CD](docs/ci-cd.md) | GitHub Actions workflows, matrix builds |
| [Runtime APIs](docs/runtime-apis.md) | Executable type detection, AOT, single instance, deep links |
| [Migration](docs/migration.md) | Migrating from `org.jetbrains.compose` |

## Quick Example

```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10"
    id("org.jetbrains.compose") version "1.10.1"
    id("io.github.kdroidfilter.nucleus") version "1.0.0"
}

nucleus.application {
    mainClass = "com.example.MainKt"

    nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Nsis, TargetFormat.Deb)
        packageName = "MyApp"
        packageVersion = "1.0.0"
    }
}
```

```bash
# Build for current OS
./gradlew packageDistributionForCurrentOS

# Run locally
./gradlew run
```

## Requirements

| Requirement | Version | Note |
|-------------|---------|------|
| JDK | 17+ (25+ for AOT cache) | JBR 25 recommended |
| Gradle | 8.0+ | |
| Kotlin | 2.0+ | |
| Compose Multiplatform | 1.7+ | Optional — required only for Compose Desktop apps |

## License

MIT — See [LICENSE](LICENSE).
