# Migration from org.jetbrains.compose

Nucleus is a drop-in extension of the official JetBrains Compose Desktop plugin. All existing configuration is preserved — Nucleus only adds new capabilities.

## Step 1: Add the Plugin

```diff
 plugins {
     id("org.jetbrains.kotlin.jvm") version "2.3.10"
     id("org.jetbrains.kotlin.plugin.compose") version "2.3.10"
     id("org.jetbrains.compose") version "1.10.1"
+    id("io.github.kdroidfilter.nucleus") version "1.0.0"
 }
```

> The official `org.jetbrains.compose` plugin remains — Nucleus extends it, not replaces it.

## Step 2: Use the Nucleus DSL

Replace the `compose.desktop.application` block with `nucleus.application`:

```diff
-compose.desktop.application {
+nucleus.application {
     mainClass = "com.example.MainKt"

     nativeDistributions {
         targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
         packageName = "MyApp"
         packageVersion = "1.0.0"

         macOS {
             bundleID = "com.example.myapp"
             iconFile.set(project.file("icons/app.icns"))
         }

         windows {
             iconFile.set(project.file("icons/app.ico"))
         }

         linux {
             iconFile.set(project.file("icons/app.png"))
         }
     }
 }
```

## Step 3: Add Nucleus Features (Optional)

Enable the features you need. All are opt-in:

```kotlin
nucleus.application {
    mainClass = "com.example.MainKt"

    nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Nsis, TargetFormat.Deb)
        packageName = "MyApp"
        packageVersion = "1.0.0"

        // --- New Nucleus features ---
        cleanupNativeLibs = true
        enableAotCache = true
        splashImage = "splash.png"
        compressionLevel = CompressionLevel.Maximum
        artifactName = "${name}-${version}-${os}-${arch}.${ext}"

        // Deep links
        protocol("MyApp", "myapp")

        // File associations
        fileAssociation(
            mimeType = "application/x-myapp",
            extension = "myapp",
            description = "MyApp Document",
        )

        // New Linux targets
        targetFormats(
            TargetFormat.Dmg, TargetFormat.Nsis, TargetFormat.Deb,
            TargetFormat.AppImage, TargetFormat.Snap, TargetFormat.Flatpak, // NEW
        )

        // Publishing
        publish {
            github {
                enabled = true
                owner = "myorg"
                repo = "myapp"
            }
        }

        // NSIS customization
        windows {
            nsis {
                oneClick = false
                allowToChangeInstallationDirectory = true
                createDesktopShortcut = true
            }
        }
    }
}
```

## Step 4: Add Runtime Libraries (Optional)

```kotlin
dependencies {
    implementation(compose.desktop.currentOs)

    // Executable type detection
    implementation("io.github.kdroidfilter:nucleus-core-runtime:1.0.0")

    // AOT cache runtime (if using enableAotCache)
    implementation("io.github.kdroidfilter:nucleus-aot-runtime:1.0.0")

    // Auto-update (if using publish)
    implementation("io.github.kdroidfilter:nucleus-updater-runtime:1.0.0")
}
```

## What Changes

| Feature | Before (compose) | After (nucleus) |
|---------|-------------------|-----------------|
| DSL entry point | `compose.desktop.application` | `nucleus.application` |
| Target formats | DMG, PKG, MSI, EXE, DEB, RPM | + NSIS, AppX, Portable, AppImage, Snap, Flatpak, archives |
| Native lib cleanup | Manual | `cleanupNativeLibs = true` |
| AOT cache | Not available | `enableAotCache = true` |
| Splash screen | Manual | `splashImage = "splash.png"` |
| Deep links | Manual (macOS only via Info.plist) | Cross-platform `protocol("name", "scheme")` |
| File associations | Limited | Cross-platform `fileAssociation()` |
| NSIS config | Not available | Full `nsis { }` DSL |
| AppX config | Not available | Full `appx { }` DSL |
| Snap config | Not available | Full `snap { }` DSL |
| Flatpak config | Not available | Full `flatpak { }` DSL |
| Sandboxing | Not available | Automatic dual pipeline for store formats (PKG, AppX, Flatpak) |
| Auto-update | Not available | Built-in with YML metadata |
| Code signing | macOS only | + Windows PFX / Azure Trusted Signing |
| Artifact naming | Fixed | Template with `artifactName` |

## What Stays the Same

Everything from the official plugin works unchanged:

- `mainClass`, `jvmArgs`
- `nativeDistributions` block (metadata, icons, resources)
- `buildTypes` / ProGuard configuration
- `modules()` / `includeAllModules`
- All existing Gradle tasks (`run`, `packageDmg`, `packageDeb`, etc.)
- `compose.desktop.currentOs` dependency
- Source set configuration
