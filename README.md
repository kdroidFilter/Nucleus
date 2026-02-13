# ComposeDeskKit

[![Pre Merge Checks](https://github.com/kdroidfilter/ComposeDeskKit/workflows/Pre%20Merge%20Checks/badge.svg)](https://github.com/kdroidfilter/ComposeDeskKit/actions?query=workflow%3A%22Pre+Merge+Checks%22) [![License](https://img.shields.io/github/license/kdroidfilter/ComposeDeskKit.svg)](LICENSE)

A **fork of the JetBrains Compose Desktop Gradle plugin** (`org.jetbrains.compose`) with additional features for building, packaging, and distributing Compose Desktop applications.

ComposeDeskKit extends the official plugin with native library optimization, AOT cache generation, advanced Linux packaging options, and more.

## Table of Contents

- [Installation](#installation)
- [What's different from the official plugin?](#whats-different-from-the-official-plugin)
  - [1. Native Library Cleanup](#1-native-library-cleanup)
  - [2. Runtime Executable Type Detection](#2-runtime-executable-type-detection)
  - [3. JDK 25+ AOT Cache Generation](#3-jdk-25-aot-cache-generation)
  - [4. Splash Screen](#4-splash-screen)
  - [5. Architecture Suffix in Distribution Filenames](#5-architecture-suffix-in-distribution-filenames)
  - [6. Linux Packaging Enhancements](#6-linux-packaging-enhancements)
  - [7. DEB Compression Options](#7-deb-compression-options)
  - [8. RPM Compression Options](#8-rpm-compression-options)
  - [9. `--app-image` jpackage Fix](#9---app-image-jpackage-fix)
  - [10. Improved Skiko Unpacking](#10-improved-skiko-unpacking)
  - [11. Windows File Associations (electron-builder)](#11-windows-file-associations-electron-builder)
  - [12. macOS Layered Icons (macOS 26+)](#12-macos-layered-icons-macos-26)
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

### 2. Runtime Executable Type Detection

Launchers now expose the executable/package type to the app with:

- `composedeskkit.executable.type=exe|msi|dmg|pkg|deb|rpm`
- `composedeskkit.executable.type=dev` for `run`/dev mode

Use the runtime helper from `aot-runtime`:

```kotlin
import io.github.kdroidfilter.composedeskkit.aot.runtime.ExecutableRuntime

if (ExecutableRuntime.isMsi()) {
    // MSI-specific behavior
} else if (ExecutableRuntime.isDev()) {
    // run/dev fallback when no installer type is detected
}
```

You can also read the enum directly with `ExecutableRuntime.type()`.

---

### 3. JDK 25+ AOT Cache Generation

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

### 4. Splash Screen

Adds a JVM splash screen from an image file in the application resources.

```kotlin
nativeDistributions {
    splashImage = "splash.png" // relative to appResources
}
```

This automatically injects `-splash:$APPDIR/resources/splash.png` into the JVM launcher arguments.

---

### 5. Architecture Suffix in Distribution Filenames

Installer filenames are automatically suffixed with the target architecture (`_x64` or `_arm64`) for clarity:

| Before | After |
|---|---|
| `MyApp-1.0.0.dmg` | `MyApp-1.0.0_arm64.dmg` |
| `MyApp-1.0.0.msi` | `MyApp-1.0.0_x64.msi` |

> AppImage format is excluded from this renaming.

---

### 6. Linux Packaging Enhancements

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

### 7. DEB Compression Options

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

### 8. RPM Compression Options

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

### 9. `--app-image` jpackage Fix

The official plugin passes the **parent directory** to jpackage's `--app-image` argument. ComposeDeskKit fixes this by passing the **actual platform-specific application directory**:

- **macOS:** `<parent>/MyApp.app`
- **Linux / Windows:** `<parent>/MyApp`

This ensures that files generated in-place (such as the AOT cache) are correctly included in the final installer.

---

### 10. Improved Skiko Unpacking

Handles subdirectory paths when unpacking Skiko native dependencies, preserving correct file names in the output.

---

### 11. Windows File Associations (electron-builder)

Windows file associations configured via `fileAssociation(...)` are now propagated to electron-builder for:
- `TargetFormat.Exe` / `TargetFormat.Nsis`
- `TargetFormat.NsisWeb`
- `TargetFormat.Msi`

```kotlin
nativeDistributions {
    windows {
        fileAssociation(
            mimeType = "application/x-myapp",
            extension = "myapp",
            description = "MyApp Document",
            iconFile = file("icons/file.ico"),
        )
    }
}
```

Notes:
- `AppX` does not currently expose file association registration through this DSL mapping.
- Extension values are normalized (leading `.` is removed).

---

### 12. macOS Layered Icons (macOS 26+)

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

### `nativeDistributions { windows { ... } }`

Use `fileAssociation(...)` to declare document associations. For electron-builder, associations are applied to `Exe`/`Nsis`, `NsisWeb`, and `Msi`.

---

## Complete Example

```kotlin
composeDeskKit.desktop.application {
    mainClass = "com.example.MainKt"

    nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
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
        }

        windows {
            fileAssociation(
                mimeType = "application/x-myapp",
                extension = "myapp",
                description = "MyApp Document",
                iconFile = project.file("icons/file-association.ico"),
            )
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
