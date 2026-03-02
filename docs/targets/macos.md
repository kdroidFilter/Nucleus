# macOS Targets

Nucleus supports two macOS installer formats and universal (fat) binaries.

## Formats

| Format | Extension | Auto-Update | Sandboxed |
|--------|-----------|-------------|-----------|
| DMG | `.dmg` | Yes | No |
| PKG | `.pkg` | Yes | Yes (App Sandbox) |

```kotlin
targetFormats(TargetFormat.Dmg, TargetFormat.Pkg)
```

## General macOS Settings

```kotlin
nativeDistributions {
    macOS {
        // Bundle identifier (reverse DNS notation)
        bundleID = "com.example.myapp"

        // Dock display name
        dockName = "MyApp"

        // App Store category
        appCategory = "public.app-category.utilities"

        // Minimum macOS version
        minimumSystemVersion = "12.0"

        // Traditional icon
        iconFile.set(project.file("icons/app.icns"))

        // Layered icon for macOS 26+ (dynamic tilt/depth effects)
        layeredIconDir.set(project.file("icons/MyApp.icon"))

        // Entitlements
        entitlementsFile.set(project.file("entitlements.plist"))
        runtimeEntitlementsFile.set(project.file("runtime-entitlements.plist"))

        // Custom Info.plist entries (raw XML appended to Info.plist)
        infoPlist {
            extraKeysRawXml = """
                <key>NSMicrophoneUsageDescription</key>
                <string>This app requires microphone access.</string>
            """.trimIndent()
        }
    }
}
```

## DMG Customization

### Window Appearance

Control the DMG window title, icon sizes, position, and dimensions:

```kotlin
macOS {
    dmg {
        title = "${productName} ${version}"

        iconSize = 128
        iconTextSize = 12

        window {
            x = 400
            y = 100
            width = 540
            height = 380
        }
    }
}
```

### Background

Set a background image or a solid color for the DMG window:

```kotlin
dmg {
    background.set(project.file("packaging/dmg-background.png"))
    // or use a solid color instead:
    // backgroundColor = "#FFFFFF"
}
```

### Format and Badge Icon

Choose a DMG format and optionally overlay a badge icon on the volume icon:

```kotlin
dmg {
    format = DmgFormat.UDZO // UDRW, UDRO, UDCO, UDZO, UDBZ, ULFO
    badgeIcon.set(project.file("icons/badge.icns"))
}
```

### Content Positioning

Use `content()` to place icons at specific coordinates inside the DMG window. The typical pattern is one entry for the app and one entry for an Applications symlink so the user can drag-and-drop to install:

```kotlin
dmg {
    content(x = 130, y = 220, type = DmgContentType.File, name = "MyApp.app")
    content(x = 410, y = 220, type = DmgContentType.Link, path = "/Applications")
}
```

Each `content()` call adds an entry with an `(x, y)` position and a `DmgContentType`:

| Type | Description |
|------|-------------|
| `DmgContentType.File` | An existing file in the DMG (e.g. the `.app` bundle). Set `name` to match the file. |
| `DmgContentType.Link` | A symlink. Set `path` to the link target (usually `/Applications`). |
| `DmgContentType.Dir` | A directory inside the DMG. |

!!! tip "Mapping from `create-dmg`"
    If you are migrating from a `create-dmg` shell script, the `content()` DSL maps directly to the `--icon` and `--app-drop-link` flags:

    | `create-dmg` flag | Nucleus equivalent |
    |---|---|
    | `--icon "MyApp.app" 130 220` | `content(x = 130, y = 220, type = DmgContentType.File, name = "MyApp.app")` |
    | `--app-drop-link 410 220` | `content(x = 410, y = 220, type = DmgContentType.Link, path = "/Applications")` |

## Layered Icons (macOS 26+)

macOS 26 introduced [layered icons](https://developer.apple.com/design/human-interface-guidelines/app-icons#macOS) that support dynamic tilt and depth effects on the Dock and Spotlight.

```kotlin
macOS {
    // Traditional icon (fallback for older macOS)
    iconFile.set(project.file("icons/app.icns"))

    // Layered icon for macOS 26+
    layeredIconDir.set(project.file("icons/MyApp.icon"))
}
```

### Creating a `.icon` directory

A `.icon` directory contains an `icon.json` manifest and image assets:

```
MyApp.icon/
  icon.json
  Assets/
    FrontImage.png
    BackImage.png
```

Create one using **Xcode 26+** or **Apple Icon Composer**:

1. Open Xcode, create or open an Asset Catalog
2. Add a new App Icon asset
3. Configure layers (front, back)
4. Export the `.icon` directory

**Requirements:**
- Xcode Command Line Tools with `actool` 26.0+
- Only effective on macOS build hosts
- If `actool` is missing, a warning is logged and the build continues without layered icons

## macOS 26 Window Appearance

macOS 26 introduces a refreshed window chrome: **larger traffic light buttons** and **more rounded window corners**. These visual changes are applied automatically by AppKit — but only if the application binary is linked against the macOS 26 SDK, which requires **Xcode 26**.

Without Xcode 26, your app will run fine on macOS 26 but will retain the older, smaller traffic lights and sharper corners.

### JVM-based applications

When running on a JVM, the window chrome is determined by the JDK's native libraries. You need a JDK that was **compiled with Xcode 26** to get the new appearance.

**JetBrains Runtime (JBR)** is the recommended JDK for Compose Desktop. However, as of now, no official JBR release has been compiled with Xcode 26. The Nucleus project maintains a [custom JBR fork](https://github.com/kdroidFilter/JetBrainsRuntime) (`v25.0.2b329.66-rtl`) that is built with Xcode 26 and includes an additional RTL layout fix.

Use it in CI by overriding the JBR download URL in the `setup-nucleus` action:

```yaml
- uses: ./.github/actions/setup-nucleus
  with:
    jbr-download-url: 'https://github.com/kdroidFilter/JetBrainsRuntime/releases/download/v25.0.2b329.66-rtl/jdk-macos-aarch64.tar.gz'
```

Available architectures:

| Architecture | URL |
|---|---|
| ARM64 (Apple Silicon) | `.../jdk-macos-aarch64.tar.gz` |
| x64 (Intel) | `.../jdk-macos-x64.tar.gz` |

!!! note
    Once an official JBR release compiled with Xcode 26 becomes available, you should switch to it. This fork is a temporary solution.

### GraalVM Native Image

For applications compiled with GraalVM Native Image, the native binary is linked directly by the system toolchain. Select **Xcode 26** before building:

```yaml
- name: Select Xcode 26
  if: runner.os == 'macOS'
  run: sudo xcode-select -s /Applications/Xcode_26.0.app/Contents/Developer

- name: Build GraalVM native image
  run: ./gradlew :myapp:packageGraalvmNative --no-daemon
```

No custom JDK is needed at runtime since the output is a standalone native binary. Xcode 26 at **build time** is sufficient.

!!! tip "See it in action"
    The [example app CI](./../ci-cd.md) demonstrates both approaches: custom JBR for JVM builds and Xcode 26 selection for GraalVM native image builds. Check `.github/workflows/release-desktop.yaml` and `.github/workflows/test-graalvm.yaml`.

## Universal Binaries

Nucleus supports creating universal (fat) macOS binaries that run natively on both Apple Silicon and Intel. This requires building on both architectures and merging with `lipo`.

See [CI/CD](../ci-cd.md#universal-macos-binaries) for the GitHub Actions workflow.

## App Sandbox (PKG)

PKG targets automatically use the sandboxed build pipeline. The plugin extracts native libraries from JARs, signs them individually, and injects JVM arguments so all native code loads from signed, pre-extracted locations.

Default sandbox entitlements grant network access and user-selected file access. Override them for additional capabilities:

```kotlin
macOS {
    entitlementsFile.set(project.file("packaging/sandbox-entitlements.plist"))
    runtimeEntitlementsFile.set(project.file("packaging/sandbox-runtime-entitlements.plist"))
}
```

For Mac App Store builds, add a provisioning profile:

```kotlin
macOS {
    appStore = true
    provisioningProfile.set(project.file("packaging/MyApp.provisionprofile"))
    runtimeProvisioningProfile.set(project.file("packaging/MyApp_Runtime.provisionprofile"))
}
```

See [Sandboxing](../sandboxing.md#macos-app-sandbox) for full details.

## Signing & Notarization

See [Code Signing](../code-signing.md#macos) for full details.

```kotlin
macOS {
    signing {
        sign.set(true)
        identity.set("Developer ID Application: My Company (TEAMID)")
        keychain.set("/path/to/keychain.keychain-db")
    }

    notarization {
        appleID.set("dev@example.com")
        password.set("@keychain:AC_PASSWORD")
        teamID.set("TEAMID")
    }
}
```

## Installation Path

The `installationPath` property controls where the application is installed on disk. It defaults to `/Applications`.

- **PKG installers** — passed as the `installLocation` to electron-builder and to `productbuild` for App Store builds. When the user chooses the local system domain during installation, the app is placed in `installationPath` (e.g. `/Applications`). When a home directory installation is chosen, the app is placed in `$HOME/Applications` instead.
- **DMG** — used as the symlink target in the native DMG builder, so the drag-and-drop arrow points to the correct directory.

```kotlin
macOS {
    // Default — installs into /Applications
    installationPath = "/Applications"

    // Custom — installs into /Applications/MyCompany
    installationPath = "/Applications/MyCompany"
}
```

!!! note
    This property is macOS-only. Windows and Linux installers do not use it.

## Full macOS DSL Reference

### `macOS { }`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `iconFile` | `RegularFileProperty` | — | `.icns` icon file |
| `bundleID` | `String?` | `null` | macOS bundle identifier |
| `dockName` | `String?` | `null` | Name displayed in the Dock |
| `setDockNameSameAsPackageName` | `Boolean` | `true` | Use `packageName` as dock name |
| `appCategory` | `String?` | `null` | App Store / Finder category |
| `appStore` | `Boolean` | `false` | Build for Mac App Store |
| `minimumSystemVersion` | `String?` | `null` | Minimum macOS version |
| `layeredIconDir` | `DirectoryProperty` | — | `.icon` directory for macOS 26+ |
| `packageName` | `String?` | `null` | Override package name |
| `packageVersion` | `String?` | `null` | Override version |
| `packageBuildVersion` | `String?` | `null` | CFBundleVersion |
| `dmgPackageVersion` | `String?` | `null` | DMG-specific version |
| `dmgPackageBuildVersion` | `String?` | `null` | DMG-specific build version |
| `pkgPackageVersion` | `String?` | `null` | PKG-specific version |
| `pkgPackageBuildVersion` | `String?` | `null` | PKG-specific build version |
| `entitlementsFile` | `RegularFileProperty` | — | Entitlements plist |
| `runtimeEntitlementsFile` | `RegularFileProperty` | — | Runtime entitlements plist |
| `provisioningProfile` | `RegularFileProperty` | — | Provisioning profile |
| `runtimeProvisioningProfile` | `RegularFileProperty` | — | Runtime provisioning profile |
| `installationPath` | `String?` | `/Applications` | The install location used by PKG installers and as the DMG symlink target (see [below](#installation-path)) |

### `macOS { signing { } }`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `sign` | `Property<Boolean>` | `false` | Enable code signing |
| `identity` | `Property<String>` | — | Signing identity |
| `keychain` | `Property<String>` | — | Keychain path |
| `prefix` | `Property<String>` | — | Signing prefix |

### `macOS { notarization { } }`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `appleID` | `Property<String>` | — | Apple ID email |
| `password` | `Property<String>` | — | App-specific password |
| `teamID` | `Property<String>` | — | Developer Team ID |

### `macOS { dmg { } }`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `title` | `String?` | `null` | DMG window title |
| `iconSize` | `Int?` | `null` | Icon size in DMG window |
| `iconTextSize` | `Int?` | `null` | Icon text size |
| `format` | `DmgFormat?` | `null` | DMG format enum (`UDZO`, `UDBZ`, etc.) |
| `size` | `String?` | `null` | DMG size |
| `shrink` | `Boolean?` | `null` | Shrink DMG |
| `sign` | `Boolean` | `false` | Sign the DMG |
| `background` | `RegularFileProperty` | — | Background image |
| `backgroundColor` | `String?` | `null` | Background color (hex) |
| `icon` | `RegularFileProperty` | — | DMG volume icon |
| `badgeIcon` | `RegularFileProperty` | — | Badge overlay icon |

#### `DmgFormat` Enum

`UDRW` (read/write), `UDRO` (read-only), `UDCO` (ADC compressed), `UDZO` (zlib compressed), `UDBZ` (bzip2), `ULFO` (lzfse)

#### `dmg { window { } }`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `x` | `Int?` | `null` | Window x position on screen |
| `y` | `Int?` | `null` | Window y position on screen |
| `width` | `Int?` | `null` | Window width |
| `height` | `Int?` | `null` | Window height |

#### `dmg { content() }`

Adds an icon entry to the DMG window layout. Call multiple times to position several items.

```kotlin
fun content(
    x: Int,
    y: Int,
    type: DmgContentType? = null,
    name: String? = null,
    path: String? = null,
)
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `x` | `Int` | Yes | Horizontal position inside the DMG window |
| `y` | `Int` | Yes | Vertical position inside the DMG window |
| `type` | `DmgContentType?` | No | Kind of content entry (`File`, `Link`, or `Dir`) |
| `name` | `String?` | No | File name to match (used with `File` / `Dir`) |
| `path` | `String?` | No | Target path (used with `Link`, e.g. `/Applications`) |

#### `DmgContentType` Enum

| Value | Serialized ID | Description |
|-------|---------------|-------------|
| `Link` | `link` | A symlink to a target path |
| `File` | `file` | An existing file in the DMG |
| `Dir` | `dir` | A directory in the DMG |
