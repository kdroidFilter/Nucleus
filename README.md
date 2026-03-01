<p align="center">
  <img src="art/header.png" alt="Nucleus" />
</p>

# Nucleus

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.kdroidfilter.nucleus?label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/io.github.kdroidfilter.nucleus)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kdroidfilter/nucleus.core-runtime?label=Maven%20Central)](https://central.sonatype.com/search?q=io.github.kdroidfilter.nucleus)
[![Pre Merge Checks](https://github.com/kdroidFilter/Nucleus/actions/workflows/pre-merge.yaml/badge.svg)](https://github.com/kdroidFilter/Nucleus/actions/workflows/pre-merge.yaml)
[![License: MIT](https://img.shields.io/github/license/kdroidFilter/Nucleus)](https://github.com/kdroidFilter/Nucleus/blob/main/LICENSE)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF?logo=kotlin&logoColor=white)
![Platform](https://img.shields.io/badge/Platform-macOS%20%7C%20Windows%20%7C%20Linux-blue)

**The all-in-one toolkit for shipping JVM desktop applications.** Gradle plugin + runtime libraries + GitHub Actions — everything you need to go from `./gradlew run` to a signed, notarized, auto-updating app on every store.

Compatible with any JVM application. Optimized for **Compose Desktop**.

---

## Why Nucleus?

### Fast Cold Start

- **JDK 25+ AOT Cache (Project Leyden)** — Eliminates the JVM cold start penalty with a single Gradle flag. No GraalVM, no native-image, no compromise — your app launches almost instantly
- **ProGuard** — Built-in integration for release builds: obfuscation, optimization, and JAR joining
- **Native library cleanup** — Automatically strips non-target-platform `.dll`/`.so`/`.dylib` from dependency JARs, reducing app size

### Distributable

- **17 packaging formats** — DMG, PKG, NSIS, MSI, AppX, Portable, DEB, RPM, AppImage, Snap, Flatpak, ZIP, TAR, 7z, and more
- **Store-ready outputs** — Sandboxed PKG for the Mac App Store, AppX for the Microsoft Store, Snap for Snapcraft, Flatpak for Flathub — with automatic sandboxing pipelines
- **Code signing & notarization** — macOS (Developer ID, notarization, App Store), Windows (PFX, Azure Trusted Signing)
- **Auto-update** — Runtime library with SHA-512 verification, download progress, and platform-specific silent installation. Supports GitHub Releases and S3
### Native

- **Decorated windows** — Draw anything in the title bar (icons, text, gradients) while keeping native window controls. Fork of [Jewel](https://github.com/JetBrains/intellij-community/tree/master/platform/jewel)'s decorated window, without any Jewel dependency, with full Linux rework (GNOME Adwaita, KDE Breeze) and added `DecoratedDialog` support
- **Reactive dark mode** — OS-level theme listener via JNI that triggers Compose recomposition instantly. Unlike Compose's built-in `isSystemInDarkTheme()` which reads once and never updates
- **Platform detection** — Runtime APIs for OS, desktop environment (GNOME/KDE/XFCE/...), executable type (18 formats), and AOT mode
- **Single instance & deep links** — File-lock-based single instance enforcement with deep link forwarding between instances
- **Deep links & file associations** — Cross-platform protocol handlers and file type registration in one DSL block

### CI/CD Ready

| Action | Description |
|--------|-------------|
| `setup-nucleus` | Sets up JBR + all packaging tools on any runner (Linux, macOS, Windows) |
| `build-macos-universal` | Merges arm64 + x64 into a universal binary with re-signing and notarization |
| `build-windows-appxbundle` | Merges amd64 + arm64 AppX into a signed MSIX bundle |
| `generate-update-yml` | Generates electron-builder-compatible update metadata with SHA-512 checksums |
| `publish-release` | Creates/updates GitHub Releases and uploads all artifacts |

Full 6-runner matrix build (Ubuntu amd64/arm64, Windows amd64/arm64, macOS arm64/Intel) out of the box.

---

## Quick Start

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
./gradlew run                              # Run locally
./gradlew packageDistributionForCurrentOS  # Build installer for your OS
```

---

## Runtime Libraries

Use them independently or together — each module is published to Maven Central.

| Module | Artifact | Description |
|--------|----------|-------------|
| **Core Runtime** | `nucleus.core-runtime` | Platform detection, single instance, deep links, executable type detection |
| **AOT Runtime** | `nucleus.aot-runtime` | AOT cache mode detection (training / runtime / off) |
| **Updater** | `nucleus.updater-runtime` | Auto-update engine with GitHub/S3 providers, progress tracking, SHA-512 verification |
| **Dark Mode Detector** | `nucleus.darkmode-detector` | Reactive OS dark mode via JNI — macOS, Windows, Linux (D-Bus) |
| **Decorated Window** | `nucleus.decorated-window` | Custom title bar with native controls — design-system agnostic |
| **Decorated Window Material** | `nucleus.decorated-window-material` | Material 3 integration for decorated windows and dialogs |
| **Linux HiDPI** | `nucleus.linux-hidpi` | Native HiDPI scale factor detection on Linux (JNI) |
| **GraalVM Runtime** | `nucleus.graalvm-runtime` | Native-image bootstrap + font substitutions (includes linux-hidpi) |

---

## Requirements

| Requirement | Version | Note |
|-------------|---------|------|
| JDK | 17+ (25+ for AOT cache) | JBR recommended |
| Gradle | 8.0+ | |
| Kotlin | 2.0+ | |

---

## Documentation

Full documentation is available at **[nucleus.kdroidfilter.com](https://nucleus.kdroidfilter.com/)**.

## License

MIT — See [LICENSE](LICENSE).
