# GraalVM Native Image

!!! danger "Experimental — for advanced developers only"
    GraalVM Native Image compilation for Compose Desktop is **highly experimental**. If reflection is not fully resolved at build time, the application **will crash at runtime**. This mode requires significant effort to configure and debug. Proceed only if you are comfortable with native-image internals.

## Why Native Image?

For most Compose Desktop applications, [AOT Cache (Leyden)](runtime/aot-cache.md) is the recommended way to improve startup. It's simple to set up and provides a major boost. But there are cases where even Leyden isn't enough:

- **Background services / system tray apps** — a lightweight app that mostly sits idle in the background will consume **300–400 MB of RAM** on a JVM, versus **100–150 MB** as a native image. For an app that's always running, this matters.
- **Instant-launch expectations** — Leyden brings cold boot down to ~1.5 s, but a native image starts in ~0.5 s. For utilities, launchers, or CLI-like tools where every millisecond counts, native image is the way to go.
- **Bundle size** — no bundled JRE means a much smaller distributable.

GraalVM Native Image compiles your entire application **ahead of time** into a standalone native binary that feels truly native to the OS.

### Trade-offs

Native image is not a free lunch. In addition to significantly more complex configuration (reflection, see below), there is a real **CPU throughput penalty**: the JVM's JIT compiler optimizes hot loops and polymorphic calls at runtime far better than AOT compilation can. For CPU-intensive workloads (heavy computation, real-time rendering, large data processing), a JVM with Leyden AOT cache will outperform a native image in sustained throughput.

| | JVM + Leyden | Native Image |
|---|---|---|
| Cold boot | ~1.5 s | ~0.5 s |
| RAM (idle) | 300–400 MB | 100–150 MB |
| CPU throughput | Excellent (JIT) | Lower (no JIT) |
| Bundle size | Larger (includes JRE) | Smaller |
| Configuration | Simple (`enableAotCache = true`) | Complex (reflection metadata) |
| Stability | Stable | Experimental |

**Choose native image when** startup speed and memory footprint are critical and CPU throughput is secondary. **Choose Leyden when** you want the best balance of performance, simplicity, and stability.

## Requirements

### BellSoft Liberica NIK 25 (Full)

GraalVM Native Image compilation **requires [BellSoft Liberica NIK 25](https://bell-sw.com/liberica-native-image-kit/)** (full distribution, not lite). This is the only supported distribution — standard GraalVM CE does not include the AWT/Swing support needed for desktop GUI applications.

!!! failure "Will not work with other distributions"
    Using Oracle GraalVM, GraalVM CE, or Liberica NIK Lite will fail. Desktop GUI applications require the **full** Liberica NIK distribution which includes AWT and Swing native-image support.

### Platform toolchains

| Platform | Required |
|----------|----------|
| **macOS** | Xcode Command Line Tools (Xcode 26 for macOS 26 appearance) |
| **Windows** | MSVC (Visual Studio Build Tools) — `ilammy/msvc-dev-cmd` in CI |
| **Linux** | GCC, `patchelf`, `xvfb` (for headless compilation) |

## Build Configuration

### Gradle DSL

```kotlin
nucleus.application {
    mainClass = "com.example.MainKt"

    graalvm {
        isEnabled = true
        imageName = "my-app"

        // Gradle Java Toolchain: auto-downloads Liberica NIK 25
        // if it's not already installed on the machine.
        // In CI, the JDK is set up by graalvm/setup-graalvm@v1 instead.
        javaLanguageVersion = 25
        jvmVendor = JvmVendorSpec.BELLSOFT

        buildArgs.addAll(
            "-H:+AddAllCharsets",
            "-Djava.awt.headless=false",
            "-Os",
            "-H:-IncludeMethodData",
        )
        nativeImageConfigBaseDir.set(
            layout.projectDirectory.dir(
                when {
                    org.gradle.internal.os.OperatingSystem.current().isMacOsX ->
                        "src/main/resources-macos/META-INF/native-image"
                    org.gradle.internal.os.OperatingSystem.current().isWindows ->
                        "src/main/resources-windows/META-INF/native-image"
                    org.gradle.internal.os.OperatingSystem.current().isLinux ->
                        "src/main/resources-linux/META-INF/native-image"
                    else -> throw GradleException("Unsupported OS")
                },
            ),
        )
    }
}
```

### DSL Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `isEnabled` | `Boolean` | `false` | Enable GraalVM native compilation |
| `javaLanguageVersion` | `Int` | `25` | Gradle toolchain language version — triggers auto-download of the matching JDK if not installed locally |
| `jvmVendor` | `JvmVendorSpec` | — | Gradle toolchain vendor filter — set to `BELLSOFT` to auto-provision Liberica NIK |
| `imageName` | `String` | project name | Output executable name |
| `march` | `String` | `"native"` | CPU architecture target (`native` for current CPU, `compatibility` for broad compatibility) |
| `buildArgs` | `ListProperty<String>` | empty | Extra arguments passed to `native-image` |
| `nativeImageConfigBaseDir` | `DirectoryProperty` | — | Directory containing `reachability-metadata.json` |

### Recommended build arguments

| Argument | Purpose |
|----------|---------|
| `-H:+AddAllCharsets` | Include all character sets (required for text I/O) |
| `-Djava.awt.headless=false` | Enable GUI support (mandatory for desktop apps) |
| `-Os` | Optimize for binary size |
| `-H:-IncludeMethodData` | Reduce binary size by excluding method metadata |

## Reflection Configuration

This is the hardest and most critical part of native-image compilation. GraalVM performs a **closed-world analysis** — any class accessed via reflection must be declared explicitly, otherwise the application crashes at runtime.

### Step 1: Copy the pre-configured metadata

The example app ships with pre-configured `reachability-metadata.json` files for all three platforms. These cover Compose Desktop, `decorated-window-jni`, and standard AWT/Swing classes — representing hundreds of entries that would be tedious to build from scratch. **Always start from these files:**

```bash
# From the Nucleus repository
cp -r example/src/main/resources-macos   your-app/src/main/
cp -r example/src/main/resources-linux   your-app/src/main/
cp -r example/src/main/resources-windows your-app/src/main/
```

This gives you a working baseline for a basic Compose Desktop application. The next steps add entries specific to your own dependencies.

### Step 2: Run the tracing agent

Nucleus provides a Gradle task that runs your application with the GraalVM tracing agent. The agent records all reflection, JNI, resource, and proxy accesses and **merges** the results into your existing configuration (your manual entries are never overwritten):

```bash
./gradlew runWithNativeAgent
```

The goal is not to run the app for a fixed duration — it is to **trigger every code path that loads resources or uses reflection**. This is critical because Compose loads resources (images, fonts, strings) via reflection at runtime. If a resource isn't loaded during the tracing run, it won't be included in the native binary and the app will crash.

During the tracing run, make sure to:

- **Navigate to every screen** of your application
- **Toggle dark/light theme** — if your icons differ between themes, both variants must be loaded
- **Open every dialog**, menu, tooltip, and dropdown
- **Trigger all lazy-loaded content** (expand lists, scroll to bottom, etc.)
- **Exercise all features** that load resources dynamically

!!! warning "Compose resources are loaded by reflection"
    Compose's `painterResource()`, `imageResource()`, and similar functions resolve resources reflectively. If an icon is only shown on a specific screen or only in dark mode, you **must** visit that screen and switch to that mode during the agent run — otherwise the resource will be missing in the native binary.

!!! tip "Prefer Kotlin-generated icons over resource files"
    To avoid resource-loading headaches entirely, convert your icons to Kotlin `ImageVector` definitions (using tools like [Composables](https://composables.com/svgtocompose) or the Material Icons library). Kotlin-generated icons are compiled directly into the binary and require no reflection or resource resolution. This is strongly recommended for native-image builds.

The agent output is automatically merged into your `nativeImageConfigBaseDir`.

### Step 3: Review and fix the configuration

The agent captures most reflection calls, but it **cannot capture code paths that weren't exercised** during the tracing run. You may need to manually add or adjust entries in `reachability-metadata.json`.

For example, the ktor networking library requires manually adding:

```json
{
    "type": "io.ktor.network.selector.InterestSuspensionsMap",
    "allDeclaredFields": true
}
```

The agent might have generated an entry for this class, but without `allDeclaredFields: true` — causing a runtime crash when ktor tries to access fields reflectively.

!!! tip "Debugging missing reflection"
    Run your native binary from the terminal. Reflection failures produce clear error messages like `Class not found` or `No such field`. Add the missing entries and recompile.

### Step 4: Repeat on each platform

The reflection configuration is **platform-specific**. Each platform uses different AWT implementations, security providers, and system classes. You need three separate configurations:

```
src/main/resources-macos/META-INF/native-image/reachability-metadata.json
src/main/resources-linux/META-INF/native-image/reachability-metadata.json
src/main/resources-windows/META-INF/native-image/reachability-metadata.json
```

Run steps 2 and 3 **on each platform separately**. The pre-configured files from step 1 already contain platform-specific entries, but your own dependencies may require additional entries that differ per OS.

## Application Bootstrap

Your `main()` function needs special handling when running as a native image:

```kotlin
private val isNativeImage =
    System.getProperty("org.graalvm.nativeimage.imagecode") != null

fun main() {
    if (isNativeImage) {
        // Use Metal L&F to avoid unsupported platform modules
        System.setProperty("swing.defaultlaf", "javax.swing.plaf.metal.MetalLookAndFeel")

        // Set java.home so Skiko can find jawt
        val execDir = File(
            ProcessHandle.current().info().command().orElse("")
        ).parentFile?.absolutePath ?: "."
        System.setProperty("java.home", execDir)
        System.setProperty(
            "java.library.path",
            "$execDir${File.pathSeparator}$execDir${File.separator}bin"
        )

        // Force early charset and fontmanager initialization
        java.nio.charset.Charset.defaultCharset()
        try { System.loadLibrary("fontmanager") } catch (_: Throwable) {}
    }

    application {
        Window(onCloseRequest = ::exitApplication, title = "MyApp") {
            App()
        }
    }
}
```

### Linux HiDPI

On Linux, the native binary does not have JBR's built-in HiDPI detection. Use the [`linux-hidpi`](runtime/linux-hidpi.md) module to detect and apply the correct scale factor:

```kotlin
if (System.getProperty("sun.java2d.uiScale") == null) {
    val scale = getLinuxNativeScaleFactor()
    if (scale > 0.0) System.setProperty("sun.java2d.uiScale", scale.toString())
}
```

### Decorated Window

The [`decorated-window-jni`](runtime/decorated-window.md) module was specifically designed to work with GraalVM Native Image (no JBR dependency). Use it instead of `decorated-window-jbr` for native-image builds.

## Gradle Tasks

| Task | Description |
|------|-------------|
| `runWithNativeAgent` | Run the app with the GraalVM tracing agent to collect reflection metadata |
| `packageGraalvmNative` | Compile and package the application as a native binary |

```bash
# Collect reflection metadata (run on each platform)
./gradlew runWithNativeAgent

# Build the native image
./gradlew packageGraalvmNative
```

Use `-PnativeMarch=compatibility` for binaries that should run on older CPUs:

```bash
./gradlew packageGraalvmNative -PnativeMarch=compatibility
```

### Output location

The native binary and its companion shared libraries are generated in:

```
<project>/build/compose/tmp/<project>/graalvm/output/
```

| Platform | Output |
|----------|--------|
| **macOS** | `output/MyApp.app/` (full `.app` bundle with `Info.plist`, icons, signed dylibs) |
| **Windows** | `output/my-app.exe` + companion DLLs (`awt.dll`, `fontmanager.dll`, etc.) |
| **Linux** | `output/my-app` + companion `.so` files (`libawt.so`, `libfontmanager.so`, etc.) |

## CI/CD

Native image compilation must happen **on each target platform**. Use a matrix strategy:

```yaml
name: Build GraalVM Native Image

on:
  push:
    tags: ["v*"]

jobs:
  build-natives:
    # Build JNI native libraries first (dylibs, DLLs, .so files)
    uses: ./.github/workflows/build-natives.yaml

  graalvm:
    needs: build-natives
    name: GraalVM - ${{ matrix.name }}
    runs-on: ${{ matrix.os }}
    timeout-minutes: 30
    strategy:
      fail-fast: false
      matrix:
        include:
          - name: Linux x64
            os: ubuntu-latest
          - name: macOS ARM64
            os: macos-latest
          - name: Windows x64
            os: windows-latest

    steps:
      - uses: actions/checkout@v4

      # macOS: select Xcode 26 for macOS 26 window appearance
      - name: Select Xcode 26
        if: runner.os == 'macOS'
        run: sudo xcode-select -s /Applications/Xcode_26.0.app/Contents/Developer

      # Install Liberica NIK 25 (Full)
      - name: Set up BellSoft Liberica NIK 25
        uses: graalvm/setup-graalvm@v1
        with:
          distribution: 'liberica'
          java-version: '25'

      - uses: gradle/actions/setup-gradle@v4

      # Linux: headless display + patchelf for RPATH
      - name: Install Linux dependencies
        if: runner.os == 'Linux'
        run: sudo apt-get update && sudo apt-get install -y xvfb patchelf

      # Windows: MSVC toolchain
      - name: Setup MSVC
        if: runner.os == 'Windows'
        uses: ilammy/msvc-dev-cmd@v1

      - name: Build GraalVM native image
        shell: bash
        run: |
          if [ "$RUNNER_OS" = "Linux" ]; then
            xvfb-run ./gradlew :myapp:packageGraalvmNative \
              -PnativeMarch=compatibility --no-daemon
          else
            ./gradlew :myapp:packageGraalvmNative \
              -PnativeMarch=compatibility --no-daemon
          fi

      - uses: actions/upload-artifact@v4
        with:
          name: native-${{ runner.os }}
          path: myapp/build/compose/tmp/**/graalvm/output/**
```

## Best Practices

### Avoid reflection-heavy libraries

Every library that uses reflection needs manual configuration. Prefer libraries that work without reflection (compile-time code generation, direct method calls). If you must use reflection-heavy libraries (ktor, serialization), expect to spend significant time on configuration.

### Test on all platforms early

Don't wait until the end to test native-image on all three platforms. Each platform has its own set of reflection requirements and quirks. Test early and often.

### Use the Jewel sample as reference

The [`jewel-sample`](https://github.com/kdroidFilter/Nucleus/tree/main/jewel-sample) in the Nucleus repository demonstrates a more complex native-image setup with the Jewel UI library, including custom font resolution patches for Windows and extensive reflection configuration. It is an excellent reference for advanced use cases.

## Further Reading

- [GraalVM Native Image documentation](https://www.graalvm.org/latest/reference-manual/native-image/)
- [BellSoft Liberica NIK](https://bell-sw.com/liberica-native-image-kit/)
- [Nucleus example app](https://github.com/kdroidFilter/Nucleus/tree/main/example) — minimal Compose Desktop + native-image setup
- [Nucleus Jewel sample](https://github.com/kdroidFilter/Nucleus/tree/main/jewel-sample) — advanced setup with reflection-heavy dependencies
