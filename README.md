# Nucleus Documentation

Nucleus is a Gradle plugin for building, packaging, and distributing **Compose Desktop** applications as native installers on macOS, Windows, and Linux. It uses the Compose Desktop API for app-image generation and [electron-builder](https://www.electron.build/) under the hood to produce final installers with code signing, auto-update metadata, and advanced packaging options.

## Why Nucleus?

- **16 target formats** — DMG, PKG, NSIS, MSI, AppX, Portable, DEB, RPM, AppImage, Snap, Flatpak, and archive formats
- **One DSL** — Configure everything from a single `nucleus.application { }` block
- **Auto-update built-in** — The `publish` DSL generates per-artifact update metadata at build time; the CI then aggregates them into combined `latest-mac.yml`, `latest.yml`, `latest-linux.yml` files covering all architectures per platform
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

| Requirement | Version |
|-------------|---------|
| JDK | 17+ (25+ for AOT cache) |
| Gradle | 8.0+ |
| Kotlin | 2.0+ |
| Compose Multiplatform | 1.7+ |

## License

MIT — See [LICENSE](LICENSE).
