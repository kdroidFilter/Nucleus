# Energy Manager

The `energy-manager` module lets your Compose Desktop application signal to Windows that it should run in **energy-efficient mode**. When enabled, the process activates [EcoQoS](https://devblogs.microsoft.com/performance/reduce-process-interference-with-task-manager-efficiency-mode/) (reduced CPU frequency, E-core routing on hybrid processors) and sets `IDLE_PRIORITY_CLASS` — which together trigger the **green leaf icon** in Windows 11 Task Manager.

This is ideal for reducing power consumption when your application is minimized or loses focus, and restoring full performance when the user brings it back.

!!! info "Platform support"
    This module is **Windows-only**. On macOS and Linux, `isAvailable()` returns `false` and all calls are safe no-ops.

## Installation

```kotlin
dependencies {
    implementation("io.github.kdroidfilter:nucleus.energy-manager:<version>")
}
```

## Usage

The typical pattern is to enable efficiency mode when the window is minimized or unfocused, and disable it when the window regains focus:

```kotlin
import io.github.kdroidfilter.nucleus.energymanager.EnergyManager
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener

@Composable
fun App(state: WindowState) {
    DecoratedWindow(state = state, onCloseRequest = ::exitApplication) {
        var isWindowFocused by remember { mutableStateOf(window.isFocused) }

        DisposableEffect(window) {
            val listener = object : WindowFocusListener {
                override fun windowGainedFocus(e: WindowEvent?) {
                    isWindowFocused = true
                }
                override fun windowLostFocus(e: WindowEvent?) {
                    isWindowFocused = false
                }
            }
            window.addWindowFocusListener(listener)
            onDispose { window.removeWindowFocusListener(listener) }
        }

        LaunchedEffect(state.isMinimized, isWindowFocused) {
            if (state.isMinimized || !isWindowFocused) {
                EnergyManager.enableEfficiencyMode()
            } else {
                EnergyManager.disableEfficiencyMode()
            }
        }

        // Your app content
    }
}
```

## API Reference

| Function | Returns | Description |
|----------|---------|-------------|
| `EnergyManager.isAvailable()` | `Boolean` | Returns `true` if the platform supports efficiency mode (Windows 10 1709+). |
| `EnergyManager.enableEfficiencyMode()` | `Result` | Activates EcoQoS + `IDLE_PRIORITY_CLASS`. Shows the green leaf in Task Manager on Windows 11 22H2+. |
| `EnergyManager.disableEfficiencyMode()` | `Result` | Resets to default QoS + `NORMAL_PRIORITY_CLASS`. Removes the green leaf. |

The `Result` data class contains:

| Field | Type | Description |
|-------|------|-------------|
| `success` | `Boolean` | `true` if the native call succeeded. |
| `errorCode` | `Int` | Win32 error code on failure, `0` on success. |
| `message` | `String` | Human-readable error description on failure. |

## How It Works

### Windows 11+ (full EcoQoS)

`enableEfficiencyMode()` makes two Win32 API calls:

1. **`SetProcessInformation(ProcessPowerThrottling)`** — enables EcoQoS, which reduces CPU frequency to the most efficient level and routes threads to E-cores on Intel 12th gen+ hybrid processors. Always active, even on AC power.
2. **`SetPriorityClass(IDLE_PRIORITY_CLASS)`** — lowers the process base priority. Combined with EcoQoS, this triggers the **green leaf icon** in Task Manager (Windows 11 22H2+).

`disableEfficiencyMode()` reverses both: requests HighQoS and restores `NORMAL_PRIORITY_CLASS`.

### Windows 10 1709+

The same API calls succeed, but EcoQoS has a reduced effect ("LowQoS") that only applies when running on battery. The green leaf icon is not available.

### Older Windows / macOS / Linux

`isAvailable()` returns `false`. The `enable`/`disable` calls return a `Result` with `success = false` and an explanatory message. No native library is loaded.

## Why IDLE_PRIORITY_CLASS Is Safe Here

`IDLE_PRIORITY_CLASS` sets the process base priority to 4 (vs. 8 for normal). This means the process only gets CPU time when no other normal-priority process needs it — which would make a visible UI sluggish.

However, since this module is designed to be activated **only when the window is minimized or unfocused**, there is no visible UI to render. When the user brings the window back, `disableEfficiencyMode()` restores `NORMAL_PRIORITY_CLASS` before any frame is drawn.

## Native Libraries

The module ships pre-built native binaries for:

- Windows: `nucleus_energy_manager.dll` (x64 + ARM64)

The library is resolved dynamically via `GetProcAddress` — it compiles and runs on any Windows version. On systems where `SetProcessInformation` is not available (Windows 7/8), the call gracefully returns error code 127.

## ProGuard

When ProGuard is enabled, the native bridge class must be preserved. The Nucleus Gradle plugin includes these rules automatically, but if you need to add them manually:

```proguard
-keep class io.github.kdroidfilter.nucleus.energymanager.NativeEnergyManagerBridge {
    native <methods>;
}
-keep class io.github.kdroidfilter.nucleus.energymanager.** { *; }
```
