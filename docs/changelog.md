# Changelog

## v1.3.6

**Released: 2026-03-02**

### Bug Fixes

- Fix fullscreen button transitions and alignment
- Restore title bar appearance before fullscreen exit animation
- Fallback to default icon for GraalVM native image on Windows
- Update `latest-mac.yml` checksums and file sizes after notarization
- Remove `xvfb-run` from test-graalvm workflow (Xvfb already started by setup-nucleus)

### Documentation

- Add homepage requirement note for electron-builder DEB packaging

---

## v1.3.5

**Released: 2026-03-02**

### Bug Fixes

- Add `homepage` to jewel-sample `nativeDistributions` for electron-builder DEB packaging

---

## v1.3.4

**Released: 2026-03-02**

### Bug Fixes

- Remove `xvfb-run` from graalvm workflow (Xvfb already started by setup-nucleus)

---

## v1.3.3

**Released: 2026-03-02**

### New Features

- Add `graalvm` option to `setup-nucleus` composite action
- Configure Windows code signing for jewel-sample using shared certificate

### Bug Fixes

- Add `libx11-dev` and `libdbus-1-dev` to graalvm release Linux dependencies
- Configure jewel-sample `nativeDistributions` with icons, deb maintainer, and platform settings
- Use `packageGraalvmDeb`/`Dmg`/`Nsis` tasks instead of raw native image output

### CI/CD

- Simplify graalvm workflows with setup-nucleus graalvm option

---

## v1.3.2

**Released: 2026-03-02**

No user-facing changes (tag only).

---

## v1.3.1

**Released: 2026-03-02**

### Bug Fixes

- Use `packageGraalvmDeb`/`Dmg`/`Nsis` tasks instead of raw native image output
- Add missing native artifact downloads and `libx11-dev` to publish-plugin workflow
- Pass repository to `gh release` commands in graalvm workflow
- Remove custom icons from jewel-sample, use default icons instead

### CI/CD

- Configure jewel-sample `nativeDistributions` with icons, deb maintainer, and platform settings

---

## v1.3.0

**Released: 2026-03-02**

### New Features

- **GraalVM Native Image support (experimental)**: compile Compose Desktop apps into standalone native binaries with instant cold boot (~0.5s), lower memory usage, and smaller bundles
- **New `graalvm-runtime` module** (`nucleus.graalvm-runtime`): centralizes native-image bootstrap logic into a single `GraalVmInitializer.initialize()` call
- **Decorated Window module split**: `decorated-window` split into `decorated-window-core`, `decorated-window-jbr`, and `decorated-window-jni`
- **Linux HiDPI scaling support** with native GDK_SCALE handling
- CI workflow to release Jewel Sample as GraalVM native image on tags
- Auto-notarize macOS distributions in `packageDistributionForCurrentOS`

### Bug Fixes

- Replace `OBJC_ASSOCIATION_ASSIGN` with `RETAIN_NONATOMIC` to prevent dangling pointer on macOS
- Use per-platform winCodeSign archives to fix AppX build on Windows
- Resolve fontmanager loading on Linux native image
- Ensure Skiko library is extracted and loaded in GraalVM Native Image
- Use `onlyIf` instead of `enabled` for native build tasks (configuration cache compatibility)

### Documentation

- Comprehensive GraalVM Native Image guide for Compose Desktop
- macOS 26 window appearance guide for JVM and native image
- Linux HiDPI runtime documentation
- AOT cache documentation rewrite with motivation and Project Leyden reference
- Decorated window docs update with changelog and migration guide

### CI/CD

- GraalVM native-image build workflow for PR CI
- Migrate detekt to 2.0.0-alpha.2 for JDK 25 support

---

## Migration Guide: 1.2.x → 1.3.x

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
