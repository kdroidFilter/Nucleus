# Code Signing

Code signing ensures your application is trusted by the operating system and not flagged as malware. Nucleus supports signing for Windows and macOS.

## Windows

### PFX Certificate

Sign Windows installers (NSIS, MSI, AppX) with a `.pfx` / `.p12` certificate:

```kotlin
windows {
    signing {
        enabled = true
        certificateFile.set(file("certs/certificate.pfx"))
        certificatePassword = "your-password"
        algorithm = SigningAlgorithm.Sha256
        timestampServer = "http://timestamp.digicert.com"
    }
}
```

### Signing DSL Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `Boolean` | `false` | Enable code signing |
| `certificateFile` | `RegularFileProperty` | — | Path to `.pfx` / `.p12` certificate |
| `certificatePassword` | `String?` | `null` | Certificate password |
| `certificateSha1` | `String?` | `null` | SHA-1 thumbprint (for store-installed certs) |
| `certificateSubjectName` | `String?` | `null` | Subject name of the certificate |
| `algorithm` | `SigningAlgorithm` | `Sha256` | Signing algorithm |
| `timestampServer` | `String?` | `null` | Timestamp server URL |

### Signing Algorithms

| Algorithm | Description |
|-----------|-------------|
| `SigningAlgorithm.Sha1` | Legacy, for older Windows |
| `SigningAlgorithm.Sha256` | Recommended |
| `SigningAlgorithm.Sha512` | Strongest |

### Common Timestamp Servers

| Provider | URL |
|----------|-----|
| DigiCert | `http://timestamp.digicert.com` |
| Sectigo | `http://timestamp.sectigo.com` |
| GlobalSign | `http://timestamp.globalsign.com` |

### Azure Trusted Signing

For cloud-based signing with [Azure Trusted Signing](https://learn.microsoft.com/en-us/azure/trusted-signing/):

```kotlin
windows {
    signing {
        enabled = true
        azureTenantId = "your-tenant-id"
        azureEndpoint = "https://your-region.codesigning.azure.net"
        azureCertificateProfileName = "your-profile"
        azureCodeSigningAccountName = "your-account"
    }
}
```

### CI/CD: Secrets Management

Never commit certificates or passwords to source control. Use environment variables or CI secrets:

```kotlin
windows {
    signing {
        enabled = true
        certificateFile.set(file(System.getenv("WIN_CSC_LINK") ?: "certs/certificate.pfx"))
        certificatePassword = System.getenv("WIN_CSC_KEY_PASSWORD")
        algorithm = SigningAlgorithm.Sha256
        timestampServer = "http://timestamp.digicert.com"
    }
}
```

In GitHub Actions:

```yaml
env:
  WIN_CSC_LINK: ${{ secrets.WIN_CSC_LINK }}
  WIN_CSC_KEY_PASSWORD: ${{ secrets.WIN_CSC_KEY_PASSWORD }}
```

> **Tip:** Base64-encode your `.pfx` file for CI:
> ```bash
> base64 -i certificate.pfx -o certificate.b64
> ```
> Store the content as a GitHub secret, then decode at build time:
> ```yaml
> - name: Decode certificate
>   run: echo "${{ secrets.WIN_CSC_LINK }}" | base64 -d > certificate.pfx
> ```

## macOS

### Prerequisites

macOS signing requires an [Apple Developer ID certificate](https://developer.apple.com/developer-id/):

1. Enroll in the [Apple Developer Program](https://developer.apple.com/programs/)
2. Create a "Developer ID Application" certificate in Xcode or the Apple Developer portal
3. The certificate must be in your local Keychain (or a CI keychain)

### Signing Configuration

```kotlin
macOS {
    signing {
        sign.set(true)
        identity.set("Developer ID Application: My Company (TEAMID)")
        // keychain.set("/path/to/keychain.keychain-db")  // Optional
    }
}
```

### Notarization

Apple notarization is required for distributing outside the Mac App Store on macOS 10.15+:

```kotlin
macOS {
    notarization {
        appleID.set("dev@example.com")
        password.set("@keychain:AC_PASSWORD")
        teamID.set("TEAMID")
    }
}
```

> **Tip:** Use `xcrun notarytool store-credentials` to save credentials in the keychain:
> ```bash
> xcrun notarytool store-credentials "AC_PASSWORD" \
>   --apple-id "dev@example.com" \
>   --team-id "TEAMID" \
>   --password "app-specific-password"
> ```

### CI/CD: macOS Signing

For GitHub Actions, import the certificate into a temporary keychain:

```yaml
- name: Import certificate
  env:
    MACOS_CERTIFICATE: ${{ secrets.MACOS_CERTIFICATE }}
    MACOS_CERTIFICATE_PWD: ${{ secrets.MACOS_CERTIFICATE_PWD }}
    KEYCHAIN_PWD: ${{ secrets.KEYCHAIN_PWD }}
  run: |
    echo "$MACOS_CERTIFICATE" | base64 -d > certificate.p12
    security create-keychain -p "$KEYCHAIN_PWD" build.keychain
    security default-keychain -s build.keychain
    security unlock-keychain -p "$KEYCHAIN_PWD" build.keychain
    security import certificate.p12 -k build.keychain -P "$MACOS_CERTIFICATE_PWD" -T /usr/bin/codesign
    security set-key-partition-list -S apple-tool:,apple: -s -k "$KEYCHAIN_PWD" build.keychain
```

### CI/CD: macOS Signing for Universal Binaries

When building universal (fat) macOS binaries, `lipo` invalidates all code signatures. Signing must happen **after** the universal merge, in the `universal-macos` CI job.

Nucleus provides a `setup-macos-signing` composite action (`.github/actions/setup-macos-signing`) that creates a temporary keychain and imports certificates:

```yaml
- name: Setup macOS signing
  id: signing
  uses: ./.github/actions/setup-macos-signing
  with:
    certificate-base64: ${{ secrets.MAC_CERTIFICATES_P12 }}
    certificate-password: ${{ secrets.MAC_CERTIFICATES_PASSWORD }}
```

#### Required Secrets

| Secret | Description |
|--------|-------------|
| `MAC_CERTIFICATES_P12` | Base64-encoded `.p12` containing all signing certificates |
| `MAC_CERTIFICATES_PASSWORD` | Password for the `.p12` file |
| `MAC_DEVELOPER_ID_APPLICATION` | Developer ID Application identity (e.g. `"Developer ID Application: Company (TEAMID)"`) |
| `MAC_DEVELOPER_ID_INSTALLER` | Developer ID Installer identity (for direct-distribution PKG, if needed) |
| `MAC_APP_STORE_APPLICATION` | App Store application identity (e.g. `"3rd Party Mac Developer Application: Company (TEAMID)"`) |
| `MAC_APP_STORE_INSTALLER` | App Store installer identity (e.g. `"3rd Party Mac Developer Installer: Company (TEAMID)"`) |
| `MAC_PROVISIONING_PROFILE` | Base64-encoded `embedded.provisionprofile` for sandboxed app |
| `MAC_RUNTIME_PROVISIONING_PROFILE` | Base64-encoded runtime provisioning profile for sandboxed app |
| `MAC_NOTARIZATION_APPLE_ID` | Apple ID for notarization |
| `MAC_NOTARIZATION_PASSWORD` | App-specific password for notarization |
| `MAC_NOTARIZATION_TEAM_ID` | Apple Team ID for notarization |

#### Granular Signing (Inside-Out)

The `build-macos-universal` action signs `.app` bundles using a strict inside-out order to satisfy Apple's code signing requirements:

1. `.dylib` files (with runtime entitlements)
2. `.jnilib` files (with runtime entitlements)
3. Main executables in `Contents/MacOS/` (with app entitlements)
4. Runtime executables in `Contents/runtime/Contents/Home/bin/` (with runtime entitlements)
5. `.framework` bundles
6. Runtime bundle (`Contents/runtime`)
7. The `.app` bundle itself (with app entitlements)

All signing uses `--options runtime --timestamp` for hardened runtime and timestamping.

#### Distribution Flows

**DMG + ZIP (Direct Distribution)**:

- Non-sandboxed `.app` signed with **Developer ID Application** identity
- Notarized via `xcrun notarytool` after packaging
- DMG is stapled directly; ZIP is extracted, `.app` is stapled, then re-zipped

**PKG (App Store)**:

- Sandboxed `.app` signed with **3rd Party Mac Developer Application** identity
- Provisioning profiles embedded in `Contents/embedded.provisionprofile`
- PKG built via `productbuild --component` and signed with **3rd Party Mac Developer Installer** identity
- No notarization needed (Apple reviews via Transporter)

#### Backward Compatibility

All signing is conditional. Without the `MAC_*` secrets configured, the workflow falls back to ad-hoc signing and electron-builder PKG generation (identical to the unsigned behavior).

### Entitlements

For apps using certain capabilities (network, file access, JIT), provide entitlements:

```kotlin
macOS {
    entitlementsFile.set(project.file("entitlements.plist"))
    runtimeEntitlementsFile.set(project.file("runtime-entitlements.plist"))
}
```

Minimal `entitlements.plist` for a JVM app:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.security.cs.allow-jit</key>
    <true/>
    <key>com.apple.security.cs.allow-unsigned-executable-memory</key>
    <true/>
    <key>com.apple.security.cs.allow-dyld-environment-variables</key>
    <true/>
</dict>
</plist>
```
