# System Color

The `system-color` module provides **reactive** detection of the OS accent color and high contrast mode. It uses native JNI bridges on each platform to register OS-level listeners that trigger recomposition the instant a setting changes — no polling, no restart required.

## Installation

```kotlin
dependencies {
    implementation("io.github.kdroidfilter:nucleus.system-color:<version>")
}
```

## Usage

### Accent Color

```kotlin
@Composable
fun App() {
    val accentColor: Color? = systemAccentColor()

    MaterialTheme(
        colorScheme = if (accentColor != null) {
            // Build a dynamic color scheme from the system accent
            darkColorScheme(primary = accentColor)
        } else {
            darkColorScheme()
        }
    ) {
        // UI automatically recomposes when the accent color changes
    }
}
```

### High Contrast

```kotlin
@Composable
fun App() {
    val highContrast = isSystemInHighContrast()

    if (highContrast) {
        // Use high contrast colors / larger borders
    }
}
```

### Check Support

```kotlin
// Non-composable check — useful for feature gating
val supported = isSystemAccentColorSupported()
```

## API Reference

| Function | Returns | Description |
|----------|---------|-------------|
| `systemAccentColor()` | `Color?` | Composable. Returns the current system accent color, or `null` if unsupported. Recomposes on change. |
| `isSystemInHighContrast()` | `Boolean` | Composable. Returns `true` if the OS is in high contrast / increased contrast mode. Recomposes on change. |
| `isSystemAccentColorSupported()` | `Boolean` | Non-composable. Returns whether the current platform supports accent color detection. |

## Platform Detection Methods

| Platform | Accent Color | High Contrast | Reactive |
|----------|-------------|---------------|----------|
| **macOS** | `NSColor.controlAccentColor` (macOS 10.14+) | `accessibilityDisplayShouldIncreaseContrast` | Yes — `NSSystemColorsDidChangeNotification` / `NSWorkspaceAccessibilityDisplayOptionsDidChangeNotification` |
| **Windows** | `HKCU\SOFTWARE\Microsoft\Windows\DWM\AccentColor` registry key (AABBGGRR) | `SystemParametersInfoW(SPI_GETHIGHCONTRAST)` | Yes — `RegNotifyChangeKeyValue` on background thread |
| **Linux** | XDG Desktop Portal `org.freedesktop.appearance` / `accent-color` — RGB tuple (0.0–1.0) | XDG Desktop Portal `org.freedesktop.appearance` / `contrast` — uint32 (1 = high) | Yes — D-Bus `SettingChanged` signal listener |

All three platforms use **JNI native libraries** bundled inside the JAR. The library is extracted and loaded at runtime automatically.

## Native Libraries

The module ships pre-built native binaries for:

- macOS: `libnucleus_systemcolor.dylib` (arm64 + x64)
- Windows: `nucleus_systemcolor.dll` (x64 + ARM64)
- Linux: `libnucleus_systemcolor.so` (x64 + aarch64)

On Linux, `libdbus-1` must be present at runtime (installed by default on all major desktop distributions).

## Linux Desktop Environment Support

The Linux implementation uses the [XDG Desktop Portal](https://flatpak.github.io/xdg-desktop-portal/) D-Bus interface, which provides a desktop-agnostic abstraction:

- **GNOME 47+** — Full accent color and contrast support
- **KDE Plasma 6+** — Accent color support
- **elementary OS** — Accent color support
- Other DEs — Works if the desktop portal implements `org.freedesktop.appearance`

If the portal is not available or the setting is not configured, `systemAccentColor()` returns `null` and `isSystemInHighContrast()` returns `false`.

## ProGuard

When ProGuard is enabled, the native bridge classes must be preserved. The Nucleus Gradle plugin includes these rules automatically, but if you need to add them manually:

```proguard
# macOS
-keep class io.github.kdroidfilter.nucleus.systemcolor.mac.NativeMacSystemColorBridge {
    native <methods>;
    static void onAccentColorChanged(float, float, float);
    static void onContrastChanged(boolean);
}

# Windows
-keep class io.github.kdroidfilter.nucleus.systemcolor.windows.NativeWindowsSystemColorBridge {
    native <methods>;
    static void onAccentColorChanged(int, int, int);
    static void onHighContrastChanged(boolean);
}

# Linux
-keep class io.github.kdroidfilter.nucleus.systemcolor.linux.NativeLinuxSystemColorBridge {
    native <methods>;
    static void onAccentColorChanged(float, float, float);
    static void onHighContrastChanged(boolean);
}

-keep class io.github.kdroidfilter.nucleus.systemcolor.** { *; }
```

## Logging

Debug messages are logged under the tags `MacSystemColorDetector`, `WindowsSystemColor`, and `LinuxSystemColorDetector`. Logging is off by default. To enable it:

```kotlin
import io.github.kdroidfilter.nucleus.core.runtime.tools.allowNucleusRuntimeLogging

allowNucleusRuntimeLogging = true
```
