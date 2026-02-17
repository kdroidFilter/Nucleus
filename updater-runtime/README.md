# updater-runtime

Auto-update library for JVM desktop applications packaged with the Nucleus plugin (electron-builder). This module reproduces the [electron-updater](https://www.electron.build/auto-update) flow adapted to the JVM/Kotlin ecosystem.

## Features

- Fetches `latest-*.yml` metadata from GitHub Releases or any HTTP server
- Semver comparison with pre-release support (`1.0.0-beta.1 < 1.0.0`)
- Automatic file selection based on current OS, architecture, and installer format
- Streaming download with progress reporting via Kotlin `Flow`
- SHA-512 integrity verification (base64-encoded, matching electron-builder format)
- Platform-specific installer launch (DEB, RPM, DMG, PKG, EXE/NSIS, MSI)
- Private repository support via GitHub token

## Setup

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":updater-runtime"))
}
```

## Quick start

```kotlin
val updater = NucleusUpdater {
    provider = GitHubProvider(owner = "myorg", repo = "myapp")
}

// Check
when (val result = updater.checkForUpdates()) {
    is UpdateResult.Available -> {
        println("New version: ${result.info.version}")

        // Download with progress
        updater.downloadUpdate(result.info).collect { progress ->
            println("${progress.percent.toInt()}%")
            if (progress.file != null) {
                // Download complete, install
                updater.installAndRestart(progress.file!!)
            }
        }
    }
    is UpdateResult.NotAvailable -> println("Up to date")
    is UpdateResult.Error -> println("Error: ${result.exception.message}")
}
```

## Configuration

```kotlin
NucleusUpdater {
    currentVersion = "1.0.0"              // Defaults to System.getProperty("jpackage.app-version") or "0.1.0"
    provider = GitHubProvider(...)         // Required — update source
    channel = "latest"                     // "latest", "beta", or "alpha"
    allowDowngrade = false                 // Allow installing older versions
    allowPrerelease = false                // Auto-set to true if currentVersion contains "-"
    executableType = null                  // Force format (deb, rpm, dmg...), auto-detected if null
}
```

## Providers

### GitHub Releases

```kotlin
provider = GitHubProvider(
    owner = "myorg",
    repo = "myapp",
    token = "ghp_..."   // Optional, for private repos
)
```

Metadata URL: `https://github.com/{owner}/{repo}/releases/latest/download/latest-{suffix}.yml`
Download URL: `https://github.com/{owner}/{repo}/releases/download/v{version}/{fileName}`

### Generic HTTP server

```kotlin
provider = GenericProvider(
    baseUrl = "https://updates.example.com"
)
```

Metadata URL: `{baseUrl}/latest-{suffix}.yml`
Download URL: `{baseUrl}/{fileName}`

## API reference

### NucleusUpdater

| Method | Description |
|--------|-------------|
| `suspend checkForUpdates(): UpdateResult` | Checks for a newer version |
| `downloadUpdate(info): Flow<DownloadProgress>` | Downloads the binary, emits progress |
| `installAndRestart(file: File)` | Launches the installer, exits the process, and relaunches after install |
| `installAndQuit(file: File)` | Launches the installer and exits without relaunching — update takes effect on next start |

### UpdateResult

```kotlin
sealed class UpdateResult {
    data class Available(val info: UpdateInfo)
    data object NotAvailable
    data class Error(val exception: UpdateException)
}
```

### DownloadProgress

```kotlin
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val percent: Double,       // 0.0 .. 100.0
    val file: File? = null,    // Non-null on the final emission (download complete)
)
```

## Compose Desktop integration

```kotlin
@Composable
fun UpdateBanner() {
    val updater = remember {
        NucleusUpdater {
            currentVersion = "1.0.0"
            provider = GitHubProvider(owner = "myorg", repo = "myapp")
        }
    }

    var status by remember { mutableStateOf("Checking...") }
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

## How it works

1. **Check** — Detects current OS/arch, fetches the appropriate `latest-*.yml` from the provider, parses it, and compares versions
2. **Select** — Picks the right binary from the file list based on OS, architecture, and installer format
3. **Download** — Streams the binary to a temp file, emitting `DownloadProgress` via Flow
4. **Verify** — Computes SHA-512 of the downloaded file and compares against the expected hash
5. **Install** — Launches the platform-specific installer and exits the process

### Installer behavior per platform

| Platform | Format | Command |
|----------|--------|---------|
| Linux | DEB | `sudo dpkg -i <file>` |
| Linux | RPM | `sudo rpm -U <file>` |
| macOS | DMG/PKG | `open <file>` |
| Windows | EXE/NSIS | `<file> /S` (silent) |
| Windows | MSI | `msiexec /i <file> /passive` |

## YML format

The module parses the `latest-*.yml` format produced by electron-builder:

```yaml
version: 1.2.3
files:
  - url: MyApp-1.2.3-linux-amd64.deb
    sha512: VkJl1gDqcBHYbYhMb0HRI...
    size: 68461240
    blockMapSize: 71732
  - url: MyApp-1.2.3-linux-arm64.deb
    sha512: qJ8a5gFDCwv0R2rW6lM3k...
    size: 65432100
path: MyApp-1.2.3-linux-amd64.deb
sha512: VkJl1gDqcBHYbYhMb0HRI...
releaseDate: '2025-01-15T10:30:00.000Z'
```
