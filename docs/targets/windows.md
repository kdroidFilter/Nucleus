# Windows Targets

Nucleus supports five Windows installer formats and a portable mode.

## Formats

| Format | Extension | Auto-Update | Sandboxed |
|--------|-----------|-------------|-----------|
| NSIS | `.exe` | Yes | No |
| NSIS Web | `.exe` | Yes | No |
| MSI | `.msi` | Yes | No |
| AppX | `.appx` | No (Store) | Yes (UWP) |
| Portable | `.exe` | No | No |

```kotlin
targetFormats(
    TargetFormat.Nsis,
    TargetFormat.Msi,
    TargetFormat.AppX,
    TargetFormat.Portable,
)
```

## General Windows Settings

```kotlin
nativeDistributions {
    windows {
        iconFile.set(project.file("icons/app.ico"))

        // Upgrade UUID — used by MSI for updates
        // Auto-generated if null, but should be fixed for production
        upgradeUuid = "d24e3b8d-3e9b-4cc7-a5d8-5e2d1f0c9f1b"

        // Console mode (shows terminal window)
        console = false

        // Per-user install (no admin required)
        perUserInstall = true

        // Start menu group
        menuGroup = "My Company"

        // Installation directory name
        dirChooser = true
    }
}
```

## NSIS Installer

NSIS produces a traditional Windows installer (`.exe`) with full customization.

```kotlin
windows {
    nsis {
        // Installer behavior
        oneClick = false                          // One-click install (no UI)
        allowElevation = true                     // Request admin rights
        perMachine = true                         // Install for all users
        allowToChangeInstallationDirectory = true // Let user pick directory

        // Shortcuts
        createDesktopShortcut = true
        createStartMenuShortcut = true

        // Post-install
        runAfterFinish = true
        deleteAppDataOnUninstall = false

        // Multi-language
        multiLanguageInstaller = true
        installerLanguages = listOf("en_US", "fr_FR", "de_DE", "es_ES", "ja_JP", "zh_CN")

        // Custom icons
        installerIcon.set(project.file("packaging/installer.ico"))
        uninstallerIcon.set(project.file("packaging/uninstaller.ico"))

        // Custom NSIS header/sidebar images
        installerHeader.set(project.file("packaging/header.bmp"))
        installerSidebar.set(project.file("packaging/sidebar.bmp"))

        // Custom NSIS script
        includeScript.set(project.file("packaging/custom.nsi"))
        // or full custom script:
        // script.set(project.file("packaging/installer.nsi"))

        // License agreement
        license.set(project.file("LICENSE"))
    }
}
```

### NSIS DSL Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `oneClick` | `Boolean` | `true` | Silent one-click install |
| `allowElevation` | `Boolean` | `false` | Request admin privileges |
| `perMachine` | `Boolean` | `false` | Install for all users |
| `allowToChangeInstallationDirectory` | `Boolean` | `false` | Show directory chooser |
| `createDesktopShortcut` | `Boolean` | `true` | Create desktop shortcut |
| `createStartMenuShortcut` | `Boolean` | `true` | Create Start Menu shortcut |
| `runAfterFinish` | `Boolean` | `true` | Launch app after install |
| `deleteAppDataOnUninstall` | `Boolean` | `false` | Remove app data on uninstall |
| `multiLanguageInstaller` | `Boolean` | `false` | Multi-language installer UI |
| `installerLanguages` | `List<String>` | `[]` | NSIS language identifiers |
| `installerIcon` | `RegularFileProperty` | — | Installer `.ico` |
| `uninstallerIcon` | `RegularFileProperty` | — | Uninstaller `.ico` |
| `installerHeader` | `RegularFileProperty` | — | Header bitmap |
| `installerSidebar` | `RegularFileProperty` | — | Sidebar bitmap |
| `license` | `RegularFileProperty` | — | License file shown during install |
| `includeScript` | `RegularFileProperty` | — | Extra NSIS script to include |
| `script` | `RegularFileProperty` | — | Full custom NSIS script |

## AppX (Windows Store / MSIX)

AppX packages run inside a UWP sandbox container and are used for the Microsoft Store and sideloading. They use the [sandboxed build pipeline](../sandboxing.md#windows-appx-sandbox) automatically.

```kotlin
windows {
    appx {
        applicationId = "MyApp"
        publisherDisplayName = "My Company"
        displayName = "My App"
        publisher = "CN=D541E802-6D30-446A-864E-2E8ABD2DAA5E"
        identityName = "MyCompany.MyApp"

        // Languages
        languages = listOf("en-US", "fr-FR")

        // Visual
        backgroundColor = "#001F3F"
        showNameOnTiles = true

        // Tile logos (PNG)
        storeLogo.set(project.file("packaging/appx/StoreLogo.png"))
        square44x44Logo.set(project.file("packaging/appx/Square44x44Logo.png"))
        square150x150Logo.set(project.file("packaging/appx/Square150x150Logo.png"))
        wide310x150Logo.set(project.file("packaging/appx/Wide310x150Logo.png"))

        // Store build options
        addAutoLaunchExtension = false
        setBuildNumber = true
    }
}
```

### AppX Logo Requirements

| Asset | Size | Description |
|-------|------|-------------|
| `StoreLogo.png` | 50x50 | Store listing icon |
| `Square44x44Logo.png` | 44x44 | Taskbar icon |
| `Square150x150Logo.png` | 150x150 | Start Menu tile |
| `Wide310x150Logo.png` | 310x150 | Wide Start Menu tile |

### MSIX Bundle (Multi-Architecture)

Create an `.msixbundle` containing both amd64 and arm64 `.appx` files. See [CI/CD](../ci-cd.md#windows-msix-bundle) for the GitHub Actions workflow.

## Code Signing

See [Code Signing](../code-signing.md#windows) for full details on PFX certificates and Azure Trusted Signing.

```kotlin
windows {
    signing {
        enabled = true
        certificateFile.set(file("certs/certificate.pfx"))
        certificatePassword = providers.environmentVariable("WIN_CSC_KEY_PASSWORD").orNull
        algorithm = SigningAlgorithm.Sha256
        timestampServer = "http://timestamp.digicert.com"
    }
}
```

## File Associations

```kotlin
nativeDistributions {
    // Cross-platform file association
    fileAssociation(
        mimeType = "application/x-myapp",
        extension = "myapp",
        description = "MyApp Document",
    )

    // Windows-specific with icon
    windows {
        fileAssociation(
            mimeType = "application/x-myapp",
            extension = "myapp",
            description = "MyApp Document",
            iconFile = project.file("icons/file.ico"),
        )
    }
}
```

File associations are propagated to NSIS, NSIS Web, MSI, and AppX formats.
