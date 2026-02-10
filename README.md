# ComposeDeskKit

[![Pre Merge Checks](https://github.com/kdroidfilter/ComposeDeskKit/workflows/Pre%20Merge%20Checks/badge.svg)](https://github.com/kdroidfilter/ComposeDeskKit/actions?query=workflow%3A%22Pre+Merge+Checks%22) [![License](https://img.shields.io/github/license/kdroidfilter/ComposeDeskKit.svg)](LICENSE)

A **fork of the JetBrains Compose Desktop Gradle plugin** (`org.jetbrains.compose`) with additional features for building, packaging, and distributing Compose Desktop applications.

ComposeDeskKit extends the official plugin with native library optimization, AOT cache generation, advanced Linux packaging options, and more.

## Table of Contents

- [Installation](#installation)
- [What's different from the official plugin?](#whats-different-from-the-official-plugin)
  - [1. Native Library Cleanup](#1-native-library-cleanup)
  - [2. JDK 25+ AOT Cache Generation](#2-jdk-25-aot-cache-generation)
  - [3. Splash Screen](#3-splash-screen)
  - [4. Architecture Suffix in Distribution Filenames](#4-architecture-suffix-in-distribution-filenames)
  - [5. Linux Packaging Enhancements](#5-linux-packaging-enhancements)
  - [6. DEB Compression Options](#6-deb-compression-options)
  - [7. RPM Compression Options](#7-rpm-compression-options)
  - [8. `--app-image` jpackage Fix](#8---app-image-jpackage-fix)
  - [9. Improved Skiko Unpacking](#9-improved-skiko-unpacking)
  - [10. MSIX Target for Windows](#10-msix-target-for-windows)
  - [11. macOS Layered Icons (macOS 26+)](#11-macos-layered-icons-macos-26)
  - [12. macOS Universal Binary (arm64 + x64)](#12-macos-universal-binary-arm64--x64)
- [Full DSL Reference](#full-dsl-reference-new-properties-only)
- [Complete Example](#complete-example)
- [Migration from `org.jetbrains.compose`](#migration-from-orgjetbrainscompose)
- [Building from Source](#building-from-source)
- [License](#license)

> **Warning:** This plugin is not yet published to the Gradle Plugin Portal. For now, you must publish it to your local Maven repository and consume it from there:
>
> ```bash
> ./gradlew -p plugin-build publishToMavenLocal
> ```
>
> Then add `mavenLocal()` to your project's `settings.gradle.kts`:
>
> ```kotlin
> pluginManagement {
>     repositories {
>         mavenLocal()
>         gradlePluginPortal()
>     }
> }
> ```

## Installation

```kotlin
plugins {
    id("io.github.kdroidfilter.composedeskkit") version "1.0.0"
}
```

> **Note:** The DSL extension is `composeDeskKit` instead of `compose`:
>
> ```kotlin
> composeDeskKit.desktop.application {
>     mainClass = "com.example.MainKt"
>     nativeDistributions {
>         // ...
>     }
> }
> ```

---

## What's different from the official plugin?

Below is the exhaustive list of features and changes introduced by ComposeDeskKit compared to `org.jetbrains.compose`.

---

### 1. Native Library Cleanup

Strips native libraries (`.dll`, `.so`, `.dylib`) for **non-target platforms** from dependency JARs, significantly reducing the final package size.

```kotlin
nativeDistributions {
    cleanupNativeLibs = true
}
```

**How it works:**
- Registers a Gradle artifact transform that processes JAR files at resolution time.
- Uses **path-based detection** (looking for OS/architecture indicators like `linux-x86-64`, `windows-x64`, `darwin-arm64` in JAR entry paths).
- Falls back to **binary header detection** (PE, ELF, Mach-O) for entries without path indicators.
- Only removes native files for non-matching OS/architecture combinations; Java classes are never touched.

---

### 2. JDK 25+ AOT Cache Generation

Generates an ahead-of-time compilation cache using the JDK 25+ single-step AOT training, improving application startup time.

```kotlin
nativeDistributions {
    enableAotCache = true
}
```

**How it works:**
1. A `generateAotCache` Gradle task runs after `createDistributable`.
2. It launches the packaged application with `-XX:AOTCacheOutput=<path>` to produce an `app.aot` cache file.
3. It then injects `-XX:AOTCache=$APPDIR/app.aot` into the launcher `.cfg` file so the cache is used at runtime.

**Requirements:**
- JDK 25 or newer.
- The application **must self-terminate** during the training run.

If your app is built in this repository, add the runtime helper:

```kotlin
dependencies {
    implementation(project(":aot-runtime"))
}
```

Recommended app-side pattern using the helper API:

```kotlin
 if (AotRuntime.isTraining()) {
    Thread({ Thread.sleep(30_000); System.exit(0) }, "aot-timer")
        .apply { isDaemon = true; start() }
}

if (AotRuntime.isRuntime()) {
    // Optional runtime-only behavior
}
```

The application **must** must return an exit code 0.

**Details:**
- A safety timeout (default **300 seconds**) force-kills the process if it has not exited. Configurable via the task's `safetyTimeoutSeconds` property.
- Auto-provisions a `java` launcher in the bundled runtime if one is missing (Windows: copies essential DLLs).
- On **headless Linux**, automatically starts Xvfb.
- AOT cache file is included in the final installer via `--app-image`.

---

### 3. Splash Screen

Adds a JVM splash screen from an image file in the application resources.

```kotlin
nativeDistributions {
    splashImage = "splash.png" // relative to appResources
}
```

This automatically injects `-splash:$APPDIR/resources/splash.png` into the JVM launcher arguments.

---

### 4. Architecture Suffix in Distribution Filenames

Installer filenames are automatically suffixed with the target architecture (`_x64` or `_arm64`) for clarity:

| Before | After |
|---|---|
| `MyApp-1.0.0.dmg` | `MyApp-1.0.0_arm64.dmg` |
| `MyApp-1.0.0.msi` | `MyApp-1.0.0_x64.msi` |
| Universal DMG | `MyApp-1.0.0_universal.dmg` |

> AppImage format is excluded from this renaming.

---

### 5. Linux Packaging Enhancements

#### StartupWMClass

Override the `StartupWMClass` entry in the `.desktop` file (helps window managers associate windows with the correct desktop entry):

```kotlin
nativeDistributions {
    linux {
        startupWMClass = "com-example-MyApp"
    }
}
```

If left `null`, it is automatically derived from `mainClass` (dots replaced by hyphens).

#### Additional package dependencies

Inject extra dependencies into `.deb` and `.rpm` packages:

```kotlin
nativeDistributions {
    linux {
        debDepends = listOf("libgtk-3-0", "libasound2")
        rpmRequires = listOf("gtk3", "alsa-lib")
    }
}
```

#### Ubuntu 24.04+ t64 compatibility

Automatically rewrites Debian dependencies for the time64 transition on Ubuntu 24.04+:

```kotlin
nativeDistributions {
    linux {
        enableT64AlternativeDeps = true
    }
}
```

This rewrites known libraries to use fallback alternatives, for example:
- `libasound2` becomes `libasound2t64 | libasound2`
- `libfreetype6` becomes `libfreetype6t64 | libfreetype6`
- `libpng16-16` becomes `libpng16-16t64 | libpng16-16`

---

### 6. DEB Compression Options

Control the compression algorithm and level used when building `.deb` packages:

```kotlin
nativeDistributions {
    linux {
        debCompression = DebCompression.ZSTD
        debCompressionLevel = 19
    }
}
```

Available algorithms:

| Algorithm | Max level |
|---|---|
| `DebCompression.GZIP` | 9 |
| `DebCompression.XZ` | 9 |
| `DebCompression.ZSTD` | 22 |
| `DebCompression.NONE` | 0 |

If `null`, the `dpkg-deb` default is used.

---

### 7. RPM Compression Options

Control the compression algorithm and level used when building `.rpm` packages:

```kotlin
nativeDistributions {
    linux {
        rpmCompression = RpmCompression.ZSTD
        rpmCompressionLevel = 19
    }
}
```

Available algorithms:

| Algorithm | Max level | Default level |
|---|---|---|
| `RpmCompression.GZIP` | 9 | 9 |
| `RpmCompression.XZ` | 9 | 6 |
| `RpmCompression.ZSTD` | 22 | 19 |

If `null`, the `rpmbuild` default is used.

---

### 8. `--app-image` jpackage Fix

The official plugin passes the **parent directory** to jpackage's `--app-image` argument. ComposeDeskKit fixes this by passing the **actual platform-specific application directory**:

- **macOS:** `<parent>/MyApp.app`
- **Linux / Windows:** `<parent>/MyApp`

This ensures that files generated in-place (such as the AOT cache) are correctly included in the final installer.

---

### 9. Improved Skiko Unpacking

Handles subdirectory paths when unpacking Skiko native dependencies, preserving correct file names in the output.

---

### 10. MSIX Target for Windows

Adds native `MSIX` packaging support via `TargetFormat.Msix`.

By default, you do **not** need to configure `windows { msix { ... } }`.
MSIX packaging reuses global `nativeDistributions` settings and computes missing manifest values automatically.

```kotlin
nativeDistributions {
    targetFormats(TargetFormat.Msix)

    windows {
        msix {
            // Optional: PNG or SVG (default uses linux.iconFile, then built-in PNG)
            // iconFile.set(project.file("packaging/msix/AppIcon.svg"))

            // Optional signing
            // signingPfxFile.set(project.file("packaging/msix/sign.pfx"))
            // signingPassword = "secret"

            // Optional manifest overrides
            // identityName = "MyCompany.MyApp"
            // publisher = "CN=MyCompany"
        }
    }
}
```

Default behavior (without MSIX overrides):
- `packageVersion`: uses the same version resolution as other formats (`msixPackageVersion` -> OS/global package version fallbacks).
- `iconFile`: fallback order is `windows.msix.iconFile` -> `nativeDistributions.linux.iconFile` -> built-in default PNG icon.
- `displayName`: `nativeDistributions.packageName`, then project name.
- `description`: `nativeDistributions.description`, then package/project name.
- `publisherDisplayName`: `nativeDistributions.vendor`, then project name.
- `appExecutable`: `<packageName>.exe`.
- `identityName`: auto-derived from vendor/package name (sanitized).
- `publisher`: auto-derived as `CN=<...>` from vendor/package name.
- `processorArchitecture`: auto-derived from host architecture (`x64` or `arm64`).
- MSIX manifest version is normalized to 4 segments (`A.B.C` becomes `A.B.C.0`).

Implementation details:
- Uses the existing distributable (`app-image`) then creates an MSIX with `makeappx.exe`.
- Generates `AppxManifest.xml` automatically (overridable via `manifestTemplateFile`).
- Supports optional signing via `signtool.exe` (`signingPfxFile` + `signingPassword`).
- Environment fallback for CI signing is supported:
  - `MSIX_SIGN_PFX_BASE64`
  - `MSIX_SIGN_PFX_PASSWORD`

---

### 11. macOS Layered Icons (macOS 26+)

Adds support for [macOS layered icons](https://developer.apple.com/design/human-interface-guidelines/app-icons#macOS) (`.icon` directory) introduced in macOS 26. Layered icons enable the dynamic tilt/depth effects shown on the Dock and in Spotlight.

```kotlin
nativeDistributions {
    macOS {
        layeredIconDir.set(project.file("icons/MyApp.icon"))
    }
}
```

**How it works:**
1. At packaging time, `xcrun actool` compiles the `.icon` directory into an `Assets.car` file.
2. The `Assets.car` is placed inside `<AppName>.app/Contents/Resources/`.
3. The `Info.plist` is updated with a `CFBundleIconName` entry referencing the compiled asset.
4. The traditional `.icns` icon (`iconFile`) is still used as a fallback for older macOS versions, so you should keep both.

**Creating a `.icon` directory:**

A `.icon` directory is a folder with the `.icon` extension that contains an `icon.json` manifest and image assets. The easiest way to create one is with **Xcode 26+** or **Apple Icon Composer**:

1. Open Xcode and create a new Asset Catalog (or use an existing one).
2. Add a new **App Icon** asset.
3. Configure the layers (front, back, etc.) with your images.
4. Export the `.icon` directory from the asset catalog.

A minimal `.icon` directory structure looks like:

```
MyApp.icon/
  icon.json
  Assets/
    MyImage.png
```

**Requirements:**
- **Xcode Command Line Tools** with `actool` version **26.0 or higher** (ships with Xcode 26+).
- Only effective on **macOS** build hosts. On other platforms the property is ignored.
- If `actool` is missing or too old, a warning is logged and the build continues without layered icon support.

**Full example with both icons:**

```kotlin
nativeDistributions {
    macOS {
        // Traditional icon (required fallback for older macOS)
        iconFile.set(project.file("icons/MyApp.icns"))

        // Layered icon for macOS 26+ dynamic effects
        layeredIconDir.set(project.file("icons/MyApp.icon"))
    }
}
```

**Native Kotlin/Native application:**

Layered icons also work with `nativeApplication` targets:

```kotlin
composeDeskKit.desktop.nativeApplication {
    distributions {
        macOS {
            iconFile.set(project.file("icons/MyApp.icns"))
            layeredIconDir.set(project.file("icons/MyApp.icon"))
        }
    }
}
```

---

### 12. macOS Universal Binary (arm64 + x64)

Builds a **universal (fat) macOS application** that runs natively on both Apple Silicon (arm64) and Intel (x64) Macs, from a single `.app` bundle.

```kotlin
nativeDistributions {
    macOS {
        universalBinary {
            enabled = true
            x64JdkPath = "/path/to/liberica-full-jdk-x64"
        }
    }
}
```

**How it works:**

The plugin uses a **subprocess strategy** to avoid the complexity of cross-compiling within a single Gradle process:

1. The host Gradle process (running on Apple Silicon) builds the **arm64 distributable** normally via `createDistributable`.
2. A **separate Gradle process** is launched under Rosetta 2 with `JAVA_HOME` pointing to the x64 JDK. In this subprocess, `os.arch` returns `x86_64`, so the entire pipeline (dependency resolution, Skiko unpacking, jlink runtime, AOT cache) runs naturally for x64. The output is redirected to a separate `app-x64/` directory.
3. The `mergeUniversalBinary` task merges both distributables:
   - Every **Mach-O binary** (executables, dylibs, shared libraries) found in both builds is combined with `lipo -create` into a fat binary.
   - Files that exist **only in one build** (e.g. `libskiko-macos-arm64.dylib` and `libskiko-macos-x64.dylib`) are copied as-is.
   - Non-binary files (JARs, configs, resources) are taken from the arm64 build.
   - Extended attributes are cleared and the app bundle is **re-signed**.
4. Universal **DMG** and **PKG** installers are created from the merged app.

```
Host (arm64)                          Subprocess (x64 via Rosetta)
createDistributable ─────┐
  (generateAotCache)     │
                         ├── createDistributableX64 ── ./gradlew createDistributable
                         │                             JAVA_HOME=x64JDK
                         │                             -PcomposeDeskKit.x64Build=true
                    mergeUniversalBinary
                         │
              ┌──────────┴──────────┐
     packageUniversalDmg    packageUniversalPkg
              │                     │
     notarizeUniversalDmg   notarizeUniversalPkg
```

**Requirements:**
- **Apple Silicon Mac** (the host must be arm64).
- **Rosetta 2** installed (`softwareupdate --install-rosetta`).
- An **x64 JDK** for macOS. The JDK must match the version used for the arm64 build (e.g. both JDK 21, or both JDK 25).

**Gradle tasks:**

| Task | Description |
|---|---|
| `createDistributableX64` | Runs the x64 build in a subprocess |
| `mergeUniversalBinary` | Merges arm64 + x64 into a universal `.app` |
| `packageUniversalDmg` | Creates a DMG from the universal app |
| `packageUniversalPkg` | Creates a PKG from the universal app |
| `notarizeUniversalDmg` | Notarizes the universal DMG |
| `notarizeUniversalPkg` | Notarizes the universal PKG |
| `packageX64Dmg` | Creates an x64-only DMG via subprocess |
| `packageX64Pkg` | Creates an x64-only PKG via subprocess |
| `notarizeX64Dmg` | Notarizes the x64-only DMG |
| `notarizeX64Pkg` | Notarizes the x64-only PKG |

> For release builds, the tasks are prefixed with `Release` (e.g. `mergeReleaseUniversalBinary`).

**x64-only packaging:**

If you only need an x64 build (without a universal binary), the `packageX64Dmg` / `packageX64Pkg` tasks run the **entire packaging pipeline** (distributable + installer) inside the x64 subprocess. This lets you produce x64 installers directly from an Apple Silicon Mac without merging.

**Output structure:**

```
build/compose/binaries/main/
  app/                    # arm64 distributable
  app-x64/                # x64 distributable (built by subprocess)
  universal/              # merged universal .app
  universal-dmg/          # MyApp-1.0.0_universal.dmg
  universal-pkg/          # MyApp-1.0.0_universal.pkg
  dmg-x64/                # MyApp-1.0.0_x64.dmg  (x64-only)
  pkg-x64/                # MyApp-1.0.0_x64.pkg  (x64-only)
```

**Compatibility with AOT cache:**

When `enableAotCache = true`, the AOT cache is generated for **both architectures** independently. The arm64 cache is generated by the host process, and the x64 cache is generated by the subprocess. Both caches are included in the final universal app.

**Example with all options:**

```kotlin
composeDeskKit.desktop.application {
    mainClass = "com.example.MainKt"

    nativeDistributions {
        targetFormats(TargetFormat.Dmg)
        packageName = "MyApp"
        packageVersion = "1.0.0"
        cleanupNativeLibs = true
        enableAotCache = true

        macOS {
            iconFile.set(project.file("icons/MyApp.icns"))
            bundleID = "com.example.myapp"

            universalBinary {
                enabled = true
                x64JdkPath = "/Users/me/jdks/liberica-full-21-x64"
            }

            signing {
                sign.set(true)
                identity.set("Developer ID Application: My Company")
            }

            notarization {
                appleID.set("me@example.com")
                teamID.set("ABCDEF1234")
                password.set("@keychain:AC_PASSWORD")
            }
        }
    }
}
```

Then build and notarize the universal DMG:

```bash
./gradlew packageUniversalDmg
./gradlew notarizeUniversalDmg
```

---

## Full DSL Reference (new properties only)

### `nativeDistributions { ... }`

| Property | Type | Default | Description |
|---|---|---|---|
| `cleanupNativeLibs` | `Boolean` | `false` | Strip native libs for non-target platforms |
| `splashImage` | `String?` | `null` | Splash image filename (relative to `appResources`) |
| `enableAotCache` | `Boolean` | `false` | Enable JDK 25+ AOT cache generation |

### `nativeDistributions { linux { ... } }`

| Property | Type | Default | Description |
|---|---|---|---|
| `startupWMClass` | `String?` | `null` | Override `StartupWMClass` in `.desktop` file |
| `debDepends` | `List<String>` | `[]` | Additional Debian dependencies |
| `rpmRequires` | `List<String>` | `[]` | Additional RPM requirements |
| `enableT64AlternativeDeps` | `Boolean` | `false` | Ubuntu 24.04+ time64 dep rewriting |
| `debCompression` | `DebCompression?` | `null` | `.deb` compression algorithm |
| `debCompressionLevel` | `Int?` | `null` | `.deb` compression level |
| `rpmCompression` | `RpmCompression?` | `null` | `.rpm` compression algorithm |
| `rpmCompressionLevel` | `Int?` | `null` | `.rpm` compression level |

### `nativeDistributions { macOS { ... } }`

| Property | Type | Default | Description |
|---|---|---|---|
| `layeredIconDir` | `DirectoryProperty` | unset | Path to a `.icon` directory for macOS 26+ layered icons |

### `nativeDistributions { macOS { universalBinary { ... } } }`

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | `Boolean` | `false` | Enable universal binary (arm64 + x64) packaging |
| `x64JdkPath` | `String?` | `null` | Path to a macOS x64 JDK. Required when `enabled = true` |

### `nativeDistributions { windows { ... } }`

| Property | Type | Default | Description |
|---|---|---|---|
| `msixPackageVersion` | `String?` | `null` | Version override for `TargetFormat.Msix` |

### `nativeDistributions { windows { msix { ... } } }`

| Property | Type | Default | Description |
|---|---|---|---|
| `iconFile` | `RegularFileProperty` | `linux.iconFile`, then built-in PNG | Icon source for MSIX logos (PNG or SVG) |
| `signingPfxFile` | `RegularFileProperty` | unset | PFX used for optional MSIX signing |
| `signingPassword` | `String?` | unset | Password for `signingPfxFile` |
| `manifestTemplateFile` | `RegularFileProperty` | built-in template | Optional AppxManifest template override |
| `identityName` | `String?` | derived | MSIX identity name |
| `publisher` | `String?` | derived (`CN=...`) | MSIX publisher |
| `publisherDisplayName` | `String?` | vendor/project | Publisher display name |
| `displayName` | `String?` | package/project name | App display name |
| `description` | `String?` | package description/name | App description |
| `backgroundColor` | `String` | `"transparent"` | Tile background color |
| `appId` | `String` | `"App"` | App ID in manifest |
| `appExecutable` | `String?` | `<packageName>.exe` | Executable entry in manifest |
| `processorArchitecture` | `String?` | host arch | Usually `x64` or `arm64` |
| `targetDeviceFamilyName` | `String` | `"Windows.Desktop"` | Target device family |
| `targetDeviceFamilyMinVersion` | `String` | `"10.0.17763.0"` | Minimum Windows version |
| `targetDeviceFamilyMaxVersionTested` | `String` | `"10.0.22621.2861"` | Maximum tested Windows version |

---

## Complete Example

```kotlin
composeDeskKit.desktop.application {
    mainClass = "com.example.MainKt"

    nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Msix, TargetFormat.Deb, TargetFormat.Rpm)
        packageName = "MyApp"
        packageVersion = "1.0.0"

        // Strip unused native libraries (e.g. remove Linux .so from macOS build)
        cleanupNativeLibs = true

        // Splash screen
        splashImage = "splash.png"

        // JDK 25+ AOT cache for faster startup
        enableAotCache = true

        linux {
            startupWMClass = "com-example-MyApp"
            debDepends = listOf("libgtk-3-0", "libasound2")
            rpmRequires = listOf("gtk3", "alsa-lib")
            enableT64AlternativeDeps = true
            debCompression = DebCompression.ZSTD
            debCompressionLevel = 19
            rpmCompression = RpmCompression.ZSTD
            rpmCompressionLevel = 19
        }

        macOS {
            iconFile.set(project.file("icons/MyApp.icns"))
            layeredIconDir.set(project.file("icons/MyApp.icon"))

            // Universal binary (arm64 + x64)
            universalBinary {
                enabled = true
                x64JdkPath = "/path/to/liberica-full-jdk-x64"
            }
        }

        windows {
            msix {
                identityName = "MyCompany.MyApp"
                publisher = "CN=MyCompany"
                publisherDisplayName = "My Company"
                // iconFile.set(project.file("packaging/msix/AppIcon.svg"))
            }
        }
    }
}
```

---

## Migration from `org.jetbrains.compose`

1. Add the plugin ID:
   ```diff
   + id("io.github.kdroidfilter.composedeskkit") version "1.0.0"
      ```

2. Replace the DSL extension name:
   ```diff
   - compose.desktop.application {
   + composeDeskKit.desktop.application {
   ```

3. All existing configuration from the official plugin is preserved. The new properties are purely additive.

---

## Building from Source

This project uses a [Gradle composite build](https://docs.gradle.org/current/userguide/composite_builds.html). The plugin source is inside the [`plugin-build`](plugin-build) folder.

```bash
# Run all checks
./gradlew preMerge

# Format code
./gradlew reformatAll

# Run a task inside the plugin build
./gradlew -p plugin-build <task-name>
```

## License

This project is forked from the JetBrains Compose Desktop Gradle plugin. See [LICENSE](LICENSE) for details.
