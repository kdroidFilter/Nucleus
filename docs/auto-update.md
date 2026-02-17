# Auto Update

Nucleus provides a complete auto-update solution compatible with the [electron-builder update format](https://www.electron.build/auto-update). The system has two parts:

1. **Build-time**: The plugin generates update metadata files (`latest-*.yml`) alongside your installers
2. **Runtime**: The `nucleus.updater-runtime` library checks for updates, downloads, and installs them

## How It Works

```
Build & Publish          Check & Install
┌─────────────┐         ┌─────────────────┐
│ Gradle build │────────▶│ GitHub Release   │
│ + YML files  │         │ or S3 bucket     │
└─────────────┘         └────────┬────────┘
                                 │
                    ┌────────────▼────────────┐
                    │ App fetches latest-*.yml │
                    │ Compares versions        │
                    │ Downloads new installer  │
                    │ Verifies SHA-512         │
                    │ Launches installer       │
                    └─────────────────────────┘
```

## Updatable Formats

| Platform | Updatable Formats |
|----------|-------------------|
| macOS | DMG, ZIP |
| Windows | EXE/NSIS, NSIS Web, MSI |
| Linux | DEB, RPM, AppImage |

## Update Metadata (YML Files)

The CI generates three YAML files per release:

### `latest-mac.yml`
```yaml
version: 1.2.3
files:
  - url: MyApp-1.2.3-macos-arm64.dmg
    sha512: VkJl1gDqcBHYbYhMb0HRI...
    size: 102400000
  - url: MyApp-1.2.3-macos-amd64.dmg
    sha512: qJ8a5gFDCwv0R2rW6lM3k...
    size: 98765432
releaseDate: '2025-06-15T10:30:00.000Z'
```

### `latest.yml` (Windows)
```yaml
version: 1.2.3
files:
  - url: MyApp-1.2.3-windows-amd64.exe
    sha512: abc123...
    size: 85000000
releaseDate: '2025-06-15T10:30:00.000Z'
```

### `latest-linux.yml`
```yaml
version: 1.2.3
files:
  - url: MyApp-1.2.3-linux-amd64.deb
    sha512: def456...
    size: 68000000
  - url: MyApp-1.2.3-linux-arm64.deb
    sha512: ghi789...
    size: 65000000
releaseDate: '2025-06-15T10:30:00.000Z'
```

## Release Channels

Nucleus supports three release channels. Different YML files are generated for each:

| Channel | YML Files | Tag Pattern |
|---------|-----------|-------------|
| `latest` | `latest-*.yml` | `v1.0.0` |
| `beta` | `beta-*.yml` | `v1.0.0-beta.1` |
| `alpha` | `alpha-*.yml` | `v1.0.0-alpha.1` |

The channel is auto-detected from the version tag in CI.

## Runtime Library

### Installation

```kotlin
dependencies {
    implementation("io.github.kdroidfilter:nucleus.updater-runtime:1.0.0")
}
```

### Quick Start

```kotlin
import io.github.kdroidfilter.nucleus.updater.NucleusUpdater
import io.github.kdroidfilter.nucleus.updater.UpdateResult
import io.github.kdroidfilter.nucleus.updater.provider.GitHubProvider

val updater = NucleusUpdater {
    provider = GitHubProvider(owner = "myorg", repo = "myapp")
}

when (val result = updater.checkForUpdates()) {
    is UpdateResult.Available -> {
        println("Update available: ${result.info.version}")

        updater.downloadUpdate(result.info).collect { progress ->
            println("${progress.percent.toInt()}%")
            if (progress.file != null) {
                updater.installAndRestart(progress.file!!)
            }
        }
    }
    is UpdateResult.NotAvailable -> println("Up to date")
    is UpdateResult.Error -> println("Error: ${result.exception.message}")
}
```

### Configuration

```kotlin
NucleusUpdater {
    // Current app version (auto-detected from jpackage.app-version system property)
    currentVersion = "1.0.0"

    // Update source (required)
    provider = GitHubProvider(owner = "myorg", repo = "myapp")

    // Release channel: "latest", "beta", or "alpha"
    channel = "latest"

    // Allow installing older versions
    allowDowngrade = false

    // Allow pre-release versions (auto-enabled if currentVersion contains "-")
    allowPrerelease = false

    // Force a specific installer format (auto-detected if null)
    executableType = null
}
```

### Providers

#### GitHub Releases

```kotlin
import io.github.kdroidfilter.nucleus.updater.provider.GitHubProvider

provider = GitHubProvider(
    owner = "myorg",
    repo = "myapp",
    token = "ghp_..."  // Optional, for private repos
)
```

#### Generic HTTP Server

```kotlin
import io.github.kdroidfilter.nucleus.updater.provider.GenericProvider

provider = GenericProvider(
    baseUrl = "https://updates.example.com"
)
```

Host your YML files and installers at:
```
https://updates.example.com/latest-mac.yml
https://updates.example.com/latest.yml
https://updates.example.com/latest-linux.yml
https://updates.example.com/MyApp-1.2.3-macos-arm64.dmg
```

### API Reference

#### NucleusUpdater

| Method | Description |
|--------|-------------|
| `isUpdateSupported(): Boolean` | Check if the current executable type supports auto-update |
| `suspend checkForUpdates(): UpdateResult` | Check for a newer version |
| `downloadUpdate(info: UpdateInfo): Flow<DownloadProgress>` | Download the installer with progress |
| `installAndRestart(installerFile: File)` | Launch the installer and exit the current process |

#### DownloadProgress

```kotlin
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val percent: Double,       // 0.0 .. 100.0
    val file: File? = null,    // Non-null on the final emission
)
```

#### UpdateResult

```kotlin
sealed class UpdateResult {
    data class Available(val info: UpdateInfo)
    data object NotAvailable
    data class Error(val exception: UpdateException)
}
```

### Compose Desktop Integration

```kotlin
@Composable
fun UpdateBanner() {
    val updater = remember {
        NucleusUpdater {
            provider = GitHubProvider(owner = "myorg", repo = "myapp")
        }
    }

    var status by remember { mutableStateOf("Checking for updates...") }
    var progress by remember { mutableStateOf(-1.0) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        when (val result = updater.checkForUpdates()) {
            is UpdateResult.Available -> {
                status = "Downloading v${result.info.version}..."
                updater.downloadUpdate(result.info).collect {
                    progress = it.percent
                    if (it.file != null) {
                        downloadedFile = it.file
                        status = "Ready to install v${result.info.version}"
                    }
                }
            }
            is UpdateResult.NotAvailable -> status = "Up to date"
            is UpdateResult.Error -> status = "Error: ${result.exception.message}"
        }
    }

    Column {
        Text(status)
        if (progress in 0.0..99.9) {
            LinearProgressIndicator(progress = { (progress / 100.0).toFloat() })
        }
        downloadedFile?.let { file ->
            Button(onClick = { updater.installAndRestart(file) }) {
                Text("Install & Restart")
            }
        }
    }
}
```

### Installer Behavior

The `installAndRestart()` method launches the platform-specific installer and exits the current process:

| Platform | Format | Command |
|----------|--------|---------|
| Linux | DEB | `sudo dpkg -i <file>` |
| Linux | RPM | `sudo rpm -U <file>` |
| macOS | DMG/PKG | `open <file>` |
| Windows | EXE/NSIS | `<file> /S` (silent) |
| Windows | MSI | `msiexec /i <file> /passive` |

### Security

- All downloads are verified with **SHA-512** checksums (base64-encoded)
- If verification fails, the downloaded file is deleted and an error is returned
- GitHub token is transmitted via `Authorization` header (not URL params) for private repos
