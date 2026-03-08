# Energy Manager

The `energy-manager` module provides two capabilities for Compose Desktop applications:

1. **Energy efficiency mode** — signals the OS to run your process (or a specific thread) at reduced power, ideal when minimized or unfocused.
2. **Screen-awake (caffeine)** — prevents the display and system from entering sleep, useful for presentations, media playback, or long-running tasks.

### Platform support

| Feature | Windows | macOS | Linux |
|---------|---------|-------|-------|
| Process efficiency mode | EcoQoS + `IDLE_PRIORITY_CLASS` | `PRIO_DARWIN_BG` + QoS TIER_5 | nice +19, ioprio IDLE, timerslack 100ms |
| Thread efficiency mode | EcoQoS + `THREAD_PRIORITY_IDLE` | `QOS_CLASS_BACKGROUND` | nice +19, ioprio IDLE, timerslack 100ms |
| Screen-awake | `SetThreadExecutionState` | `IOPMAssertion` | DBus (GNOME / logind) or X11 `XScreenSaverSuspend` |

## Installation

```kotlin
dependencies {
    implementation("io.github.kdroidfilter:nucleus.energy-manager:<version>")
}
```

## Usage

### Efficiency mode on minimize / unfocus

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

### Thread-level efficiency for background work

```kotlin
// Run a block on a dedicated low-priority thread
EnergyManager.withEfficiencyMode {
    performBackgroundWork()
}
```

### Keeping the screen awake

```kotlin
// Prevent display sleep (e.g. during a presentation)
EnergyManager.keepScreenAwake()

// Allow sleep again
EnergyManager.releaseScreenAwake()

// Check current state
val active = EnergyManager.isScreenAwakeActive()
```

## API Reference

| Function | Returns | Description |
|----------|---------|-------------|
| `isAvailable()` | `Boolean` | `true` if the platform supports efficiency mode. |
| `enableEfficiencyMode()` | `Result` | Activates process-level energy efficiency mode. |
| `disableEfficiencyMode()` | `Result` | Restores default OS scheduling. |
| `enableThreadEfficiencyMode()` | `Result` | Activates efficiency mode for the calling thread only. |
| `disableThreadEfficiencyMode()` | `Result` | Restores default scheduling for the calling thread. |
| `withEfficiencyMode { }` | `T` | Runs a suspend block on a dedicated efficient thread. |
| `keepScreenAwake()` | `Result` | Prevents display and system sleep. |
| `releaseScreenAwake()` | `Result` | Releases the screen-awake inhibition. |
| `isScreenAwakeActive()` | `Boolean` | `true` if screen-awake is currently active. |

The `Result` data class:

| Field | Type | Description |
|-------|------|-------------|
| `success` | `Boolean` | `true` if the native call succeeded. |
| `errorCode` | `Int` | OS error code on failure, `0` on success. |
| `message` | `String` | Human-readable error description on failure. |

## How It Works

### Process efficiency mode

#### Windows 11+ (full EcoQoS)

1. **`SetProcessInformation(ProcessPowerThrottling)`** — enables EcoQoS: reduced CPU frequency, E-core routing on hybrid processors. Triggers the **green leaf icon** in Task Manager (22H2+).
2. **`SetPriorityClass(IDLE_PRIORITY_CLASS)`** — lowers process base priority to 4.

On Windows 10 1709+, the same calls succeed but EcoQoS only applies on battery ("LowQoS").

#### macOS

1. **`setpriority(PRIO_DARWIN_BG)`** — CPU low priority, I/O throttling, network throttling, E-core confinement on Apple Silicon.
2. **`task_policy_set(TASK_BASE_QOS_POLICY)`** with `LATENCY_QOS_TIER_5` / `THROUGHPUT_QOS_TIER_5` — reinforces via Mach task QoS (timer coalescing, throughput hints).

!!! note "Network throttling scope"
    Network throttling only applies to sockets opened **after** `enableEfficiencyMode()` is called.

#### Linux

1. **`setpriority(PRIO_PROCESS, 0, 19)`** — maximum nice value for lowest CPU priority.
2. **`prctl(PR_SET_TIMERSLACK, 100ms)`** — timer coalescing to reduce wakeups.
3. **`ioprio_set(IOPRIO_CLASS_IDLE)`** — I/O scheduling class idle.

All three are reversible without root on any mainstream distribution.

### Thread efficiency mode

| Platform | Mechanism |
|----------|-----------|
| Windows 11+ | `SetThreadInformation(ThreadPowerThrottling)` EcoQoS + `THREAD_PRIORITY_IDLE` |
| Windows 10 | `THREAD_PRIORITY_IDLE` only (no per-thread EcoQoS) |
| macOS | `pthread_set_qos_class_self_np(QOS_CLASS_BACKGROUND)` |
| Linux | Same as process-level (nice, ioprio, timerslack are per-thread on Linux) |

### Screen-awake (caffeine)

#### Windows

`SetThreadExecutionState(ES_CONTINUOUS | ES_DISPLAY_REQUIRED | ES_SYSTEM_REQUIRED)` — immediate, no setup cost.

#### macOS

`IOPMAssertionCreateWithName(kIOPMAssertPreventUserIdleDisplaySleep)` via IOKit — prevents both display and system idle sleep. Released via `IOPMAssertionRelease`.

#### Linux

A composite backend tries three strategies in order:

1. **GNOME SessionManager** — DBus `Inhibit()` on the session bus with `INHIBIT_IDLE | INHIBIT_SUSPEND` flags. Released via `Uninhibit()` with the returned cookie.
2. **systemd-logind** — DBus `Inhibit("idle")` on the system bus. Stays active as long as the returned file descriptor is kept open.
3. **X11 XScreenSaverSuspend** — suspends the X11 screen saver via `libXss`.

All libraries (`libdbus-1`, `libX11`, `libXss`) are loaded at runtime via `dlopen()` — the module works even when some are not installed. Private DBus connections are used to avoid interference with the JVM's internal AT-SPI accessibility bus.

## Why IDLE_PRIORITY_CLASS / PRIO_DARWIN_BG Are Safe Here

**Windows**: `IDLE_PRIORITY_CLASS` sets process priority to 4 (vs. 8 for normal) — the process only gets CPU when no normal-priority process needs it.

**macOS**: `PRIO_DARWIN_BG` confines the process to E-cores and throttles all I/O.

Both are aggressive, but since this module is designed for **minimized or unfocused** windows, there is no visible UI to render. `disableEfficiencyMode()` restores full priority before any frame is drawn.

## Native Libraries

The module ships pre-built native binaries for:

- **Windows**: `nucleus_energy_manager.dll` (x64 + ARM64) — resolved dynamically via `GetProcAddress`
- **macOS**: `libnucleus_energy_manager.dylib` (x64 + arm64) — linked against IOKit/CoreFoundation
- **Linux**: `libnucleus_energy_manager.so` (x64 + aarch64) — loads `libdbus-1`, `libX11`, `libXss` via `dlopen()`

## ProGuard

When ProGuard is enabled, preserve the native bridge classes:

```proguard
-keep class io.github.kdroidfilter.nucleus.energymanager.** { *; }
```
