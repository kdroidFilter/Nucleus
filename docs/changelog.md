# Changelog

## 1.2.x → 1.3.x

### Decorated Window: monolithic module split

The `decorated-window` module has been split into three modules:

| Before (1.2.x) | After (1.3.x) |
|-----------------|----------------|
| `nucleus.decorated-window` | `nucleus.decorated-window-core` (shared) |
| | `nucleus.decorated-window-jbr` (JBR implementation) |
| | `nucleus.decorated-window-jni` (JNI implementation, new) |

**Dependency update** — replace:

```kotlin
implementation("io.github.kdroidfilter:nucleus.decorated-window:<version>")
```

With one of:

```kotlin
// JBR-based (same behavior as before)
implementation("io.github.kdroidfilter:nucleus.decorated-window-jbr:<version>")

// JNI-based (no JBR dependency, works with GraalVM)
implementation("io.github.kdroidfilter:nucleus.decorated-window-jni:<version>")
```

**Breaking changes in `TitleBarColors`** — the following fields have been **removed**:

- `titlePaneButtonHoveredBackground`
- `titlePaneButtonPressedBackground`
- `titlePaneCloseButtonHoveredBackground`
- `titlePaneCloseButtonPressedBackground`

These platform-specific button state colors are now handled internally by each module's native implementation. If you were constructing `TitleBarColors` explicitly with these fields, remove them.

**No other code changes required** — all composable APIs (`DecoratedWindow`, `DecoratedDialog`, `TitleBar`, `DialogTitleBar`), scopes, and state types are identical. No import changes needed — the package remains `io.github.kdroidfilter.nucleus.window`.

See [Decorated Window](runtime/decorated-window.md) for full details on choosing between JBR and JNI.

### GraalVM Native Image support (experimental)

Nucleus now supports compiling Compose Desktop applications into standalone native binaries using GraalVM Native Image. This brings instant cold boot (~0.5 s), significantly lower memory usage (~100–150 MB vs ~300–400 MB on JVM), and smaller bundle sizes.

- New `graalvm {}` DSL block in `build.gradle.kts`
- `runWithNativeAgent` task to collect reflection metadata with the GraalVM tracing agent
- `packageGraalvmNative` task to compile and package the native binary
- Full packaging pipeline per platform: `.app` bundle on macOS, `.exe` + DLLs on Windows, ELF + `.so` on Linux
- Pre-configured `reachability-metadata.json` files in the example app for all three platforms
- New **`graalvm-runtime`** module (`nucleus.graalvm-runtime`) — centralizes all native-image bootstrap logic into a single `GraalVmInitializer.initialize()` call: Metal L&F, `java.home`/`java.library.path` setup, charset/fontmanager early init, Linux HiDPI detection, and GraalVM `@TargetClass` font substitutions for Windows and Linux
- Requires [BellSoft Liberica NIK 25](https://bell-sw.com/liberica-native-image-kit/) (full distribution)

!!! danger "Experimental"
    This feature requires significant configuration effort (reflection metadata) and is reserved for advanced developers. See [GraalVM Native Image](graalvm-native-image.md) for the full guide.
