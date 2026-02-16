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

Customize the DMG window appearance:

```kotlin
macOS {
    dmg {
        // Window title
        title = "${productName} ${version}"

        // Icon size in the DMG window
        iconSize = 128
        iconTextSize = 12

        // Window position and size
        window {
            x = 400
            y = 100
            width = 540
            height = 380
        }

        // Background image or color
        background.set(project.file("packaging/dmg-background.png"))
        // backgroundColor = "#FFFFFF"

        // DMG format
        format = DmgFormat.UDZO // UDRW, UDRO, UDCO, UDZO, UDBZ, ULFO

        // Badge icon (overlays on the DMG volume icon)
        badgeIcon.set(project.file("icons/badge.icns"))

        // Custom content positioning
        content(x = 130, y = 220, type = DmgContentType.File, name = "MyApp.app")
        content(x = 410, y = 220, type = DmgContentType.Link, path = "/Applications")
    }
}
```

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
| `installationPath` | `String?` | `null` | Installation directory |

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
| `internetEnabled` | `Boolean` | `false` | Internet-enabled DMG |
| `sign` | `Boolean` | `false` | Sign the DMG |
| `background` | `RegularFileProperty` | — | Background image |
| `backgroundColor` | `String?` | `null` | Background color (hex) |
| `icon` | `RegularFileProperty` | — | DMG volume icon |
| `badgeIcon` | `RegularFileProperty` | — | Badge overlay icon |

#### `DmgFormat` Enum

`UDRW` (read/write), `UDRO` (read-only), `UDCO` (ADC compressed), `UDZO` (zlib compressed), `UDBZ` (bzip2), `ULFO` (lzfse)

#### `dmg { window { } }`

| Property | Type | Default |
|----------|------|---------|
| `x` | `Int?` | `null` |
| `y` | `Int?` | `null` |
| `width` | `Int?` | `null` |
| `height` | `Int?` | `null` |
