# Nucleus

<p align="center">
  <img src="assets/header.png" alt="Nucleus" />
</p>

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.kdroidfilter.nucleus?label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/io.github.kdroidfilter.nucleus)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kdroidfilter/nucleus.core-runtime?label=Maven%20Central)](https://central.sonatype.com/search?q=io.github.kdroidfilter.nucleus)
[![Pre Merge Checks](https://github.com/kdroidFilter/Nucleus/actions/workflows/pre-merge.yaml/badge.svg)](https://github.com/kdroidFilter/Nucleus/actions/workflows/pre-merge.yaml)
[![License: MIT](https://img.shields.io/github/license/kdroidFilter/Nucleus)](https://github.com/kdroidFilter/Nucleus/blob/main/LICENSE)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF?logo=kotlin&logoColor=white)
![Platform](https://img.shields.io/badge/Platform-macOS%20%7C%20Windows%20%7C%20Linux-blue)

Nucleus is a toolkit for building production-ready **JVM desktop applications** on macOS, Windows, and Linux. It combines a **Gradle plugin**, **runtime libraries**, and **GitHub Actions** to tackle the three biggest pain points of desktop JVM development: **performance**, **distribution**, and **native look & feel**.

Compatible with any JVM application, optimized for **Compose Desktop**.

## Why Nucleus?

### Performance

- **JDK 25+ AOT cache (Project Leyden)** — Dramatically faster cold startup with ahead-of-time class loading cache, enabled as a simple Gradle flag, no GraalVM required

### Distribution

- **16 packaging formats** — DMG, PKG, NSIS, MSI, AppX, Portable, DEB, RPM, AppImage, Snap, Flatpak, and archives
- **Plug-and-play store distribution** — Store-ready outputs for Mac App Store (PKG), Microsoft Store (AppX), Snapcraft (Snap), and Flathub (Flatpak)
- **Code signing & notarization** — Windows (PFX, Azure Trusted Signing) and macOS (Apple Developer ID) with full notarization support
- **Auto-update built-in** — Integrated update metadata generation and runtime update library; publish directly to GitHub Releases or S3
- **One DSL** — Configure everything from a single `nucleus.application { }` block

### Native Look & Feel

- **Decorated windows** — Custom title bar content (icons, text, gradients) while preserving native window controls and behavior on all platforms
- **Reactive dark mode detection** — OS-level dark mode listener via JNI (no JNA), triggers recomposition instantly when the user changes their theme
- **Platform-accurate Linux rendering** — GNOME Adwaita and KDE Breeze window controls, proper window shape clipping, and focus-aware button states — all drawn with Compose

### CI/CD

- **Reusable GitHub Actions** — `setup-nucleus` composite action + workflows for multi-platform matrix builds, universal macOS binaries, and MSIX bundles
- **Deep links & file associations** — Cross-platform protocol handlers and file type registration

## Quick Example

```kotlin
plugins {
    id("io.github.kdroidfilter.nucleus") version "<version>"
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

## Try the Demo

A pre-built demo application is available on the [GitHub Releases page](https://github.com/kdroidFilter/Nucleus/releases). Download the installer for your platform and see Nucleus features in action.

**On macOS**, install and launch the demo with a single command:

```bash
curl -fsSL https://nucleus.kdroidfilter.com/install.sh | bash
```

This automatically detects your architecture (Apple Silicon or Intel), downloads the latest release, installs it to `/Applications`, and launches the app.

**On other platforms**, download the appropriate installer from the [releases page](https://github.com/kdroidFilter/Nucleus/releases).

Here's what you'll see:

- **AOT Cache** — Near-instant cold startup powered by JDK 25+ ahead-of-time class loading
- **Material Decorated Window** — Custom title bar with native window controls, automatically themed with Material 3
- **Dark Mode Detection** — Toggle your OS theme and watch the app switch in real time
- **Auto-Update** — The app checks for updates on launch, downloads them with progress tracking, and can install & restart in one click

!!! tip "Test auto-update yourself"
    Download an **older release** from the [releases page](https://github.com/kdroidFilter/Nucleus/releases), install it, and launch the app. It will detect the newer version, download the update, and offer to install it — all automatically.

The demo source code is in the [`example/`](https://github.com/kdroidFilter/Nucleus/tree/main/example) directory.

## Requirements

| Requirement | Version | Note |
|-------------|---------|------|
| JDK | 17+ (25+ for AOT cache) | JBR 25 recommended |
| Gradle | 8.0+ | |
| Kotlin | 2.0+ | |

## License

MIT — See [LICENSE](https://github.com/kdroidFilter/Nucleus/blob/main/LICENSE).
