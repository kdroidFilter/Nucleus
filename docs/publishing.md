# Publishing

Nucleus can publish your installers and update metadata to **GitHub Releases**, **Amazon S3**, or a **generic HTTP server**.

## Configuration

```kotlin
nativeDistributions {
    publish {
        // Publish mode
        publishMode = PublishMode.Auto // Never, Auto, Always

        github {
            enabled = true
            owner = "myorg"
            repo = "myapp"
            token = System.getenv("GITHUB_TOKEN")
            channel = ReleaseChannel.Latest
            releaseType = ReleaseType.Release
        }

        // Or S3
        s3 {
            enabled = true
            bucket = "my-updates-bucket"
            region = "us-east-1"
            path = "releases"
            acl = "public-read"
        }

        // Or generic HTTP server
        generic {
            enabled = true
            url = "https://updates.example.com/releases/"
            channel = ReleaseChannel.Latest
            useMultipleRangeRequest = true
        }
    }
}
```

## GitHub Releases

The most common publishing target. Installers and YML metadata files are uploaded as release assets.

```kotlin
publish {
    github {
        enabled = true
        owner = "myorg"                      // GitHub org or user
        repo = "myapp"                       // Repository name
        token = System.getenv("GITHUB_TOKEN") // Authentication token
        channel = ReleaseChannel.Latest      // Latest, Beta, Alpha
        releaseType = ReleaseType.Release    // Release, Draft, Prerelease
    }
}
```

### Release Structure

A GitHub Release created by Nucleus contains:

```
v1.0.0 (Release)
├── MyApp-1.0.0-macos-arm64.dmg
├── MyApp-1.0.0-macos-amd64.dmg
├── MyApp-1.0.0-macos-universal.dmg
├── MyApp-1.0.0-windows-amd64.exe
├── MyApp-1.0.0-windows-arm64.exe
├── MyApp-1.0.0-windows.msixbundle
├── MyApp-1.0.0-linux-amd64.deb
├── MyApp-1.0.0-linux-arm64.deb
├── MyApp-1.0.0-linux-amd64.rpm
├── MyApp-1.0.0-linux-amd64.AppImage
├── latest-mac.yml          ← Auto-update metadata
├── latest.yml              ← Auto-update metadata (Windows)
└── latest-linux.yml        ← Auto-update metadata
```

### GitHub Token

Use a `GITHUB_TOKEN` with `contents: write` permission:

```yaml
# GitHub Actions — automatic token
permissions:
  contents: write

env:
  GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

## Amazon S3

Publish to an S3 bucket for self-hosted update distribution:

```kotlin
publish {
    s3 {
        enabled = true
        bucket = "my-updates-bucket"
        region = "us-east-1"
        path = "releases/myapp"    // Prefix path in the bucket
        acl = "public-read"        // ACL for uploaded files
    }
}
```

### S3 Bucket Structure

```
s3://my-updates-bucket/releases/myapp/
├── MyApp-1.0.0-macos-arm64.dmg
├── MyApp-1.0.0-windows-amd64.exe
├── MyApp-1.0.0-linux-amd64.deb
├── latest-mac.yml
├── latest.yml
└── latest-linux.yml
```

### S3 Authentication

Set AWS credentials via environment variables:

```bash
export AWS_ACCESS_KEY_ID=AKIA...
export AWS_SECRET_ACCESS_KEY=...
export AWS_REGION=us-east-1
```

## Generic HTTP Server

For self-hosted update distribution without cloud dependencies. The generic provider generates the `latest-*.yml` metadata files and configures the auto-updater to fetch from a base URL. You are responsible for uploading the output to your server.

```kotlin
publish {
    generic {
        enabled = true
        url = "https://updates.example.com/releases/"
        channel = ReleaseChannel.Latest        // Latest, Beta, Alpha
        useMultipleRangeRequest = true         // Differential downloads
    }
}
```

### Server Structure

Upload the installer and YML files to your server:

```
https://updates.example.com/releases/
├── MyApp-1.0.0-macos-arm64.dmg
├── MyApp-1.0.0-windows-amd64.exe
├── MyApp-1.0.0-linux-amd64.deb
├── latest-mac.yml
├── latest.yml
└── latest-linux.yml
```

Any static file server (Nginx, Caddy, Apache, S3 with public access, Cloudflare R2, etc.) works — the auto-updater simply fetches `<url>/latest-<platform>.yml` and downloads the installer from the same base URL.

## Release Channels

Channels allow you to distribute pre-release versions to testers:

| Channel | YML Prefix | Version Pattern | Audience |
|---------|------------|-----------------|----------|
| `ReleaseChannel.Latest` | `latest-` | `1.0.0` | All users |
| `ReleaseChannel.Beta` | `beta-` | `1.0.0-beta.1` | Beta testers |
| `ReleaseChannel.Alpha` | `alpha-` | `1.0.0-alpha.1` | Internal testers |

Users on the `beta` channel receive both `latest` and `beta` updates. Users on the `alpha` channel receive all updates.

Configure the channel in the updater runtime:

```kotlin
NucleusUpdater {
    provider = GitHubProvider(owner = "myorg", repo = "myapp")
    channel = "beta" // Subscribe to beta updates
}
```

## Release Types

| Type | Description |
|------|-------------|
| `ReleaseType.Release` | Visible on the releases page |
| `ReleaseType.Draft` | Hidden until manually published |
| `ReleaseType.Prerelease` | Marked as pre-release |

## Publish Modes

| Mode | Description |
|------|-------------|
| `PublishMode.Never` | Do not publish (build only) |
| `PublishMode.Auto` | Publish if on CI, skip locally |
| `PublishMode.Always` | Always publish |

## DSL Reference

### `publish { }`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `publishMode` | `PublishMode` | `Never` | When to publish |

### `publish { github { } }`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `Boolean` | `false` | Enable GitHub publishing |
| `owner` | `String` | — | GitHub owner/org |
| `repo` | `String` | — | Repository name |
| `token` | `String?` | `null` | GitHub token |
| `channel` | `ReleaseChannel` | `Latest` | Release channel |
| `releaseType` | `ReleaseType` | `Release` | Release type |

### `publish { s3 { } }`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `Boolean` | `false` | Enable S3 publishing |
| `bucket` | `String` | — | S3 bucket name |
| `region` | `String` | — | AWS region |
| `path` | `String?` | `null` | Key prefix |
| `acl` | `String?` | `null` | S3 ACL |

### `publish { generic { } }`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `Boolean` | `false` | Enable generic HTTP publishing |
| `url` | `String` | — | Base URL where update files are hosted |
| `channel` | `ReleaseChannel` | `Latest` | Release channel |
| `useMultipleRangeRequest` | `Boolean` | `true` | Use multiple range requests for differential downloads |
