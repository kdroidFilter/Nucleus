package io.github.kdroidfilter.nucleus.window.utils.windows

import java.nio.file.Files
import java.util.logging.Level
import java.util.logging.Logger

internal object JniWindowsDecorationBridge {
    private val logger = Logger.getLogger(JniWindowsDecorationBridge::class.java.simpleName)

    @Volatile
    private var loaded = false

    init {
        loadNativeLibrary()
    }

    private fun loadNativeLibrary() {
        if (loaded) return

        // Try system library path first (packaged app)
        try {
            System.loadLibrary("nucleus_windows_decoration")
            loaded = true
            return
        } catch (_: UnsatisfiedLinkError) {
            // Fall through to JAR extraction
        }

        // Fallback: extract from JAR resources
        @Suppress("TooGenericExceptionCaught")
        try {
            val arch =
                System.getProperty("os.arch").let {
                    if (it == "aarch64" || it == "arm64") "aarch64" else "x64"
                }
            val resourcePath = "/nucleus/native/win32-$arch/nucleus_windows_decoration.dll"
            val stream =
                JniWindowsDecorationBridge::class.java
                    .getResourceAsStream(resourcePath)
                    ?: throw UnsatisfiedLinkError("Native library not found in JAR at $resourcePath")
            val tempDir = Files.createTempDirectory("nucleus-jni-native")
            val tempLib = tempDir.resolve("nucleus_windows_decoration.dll")
            stream.use { Files.copy(it, tempLib) }
            tempLib.toFile().deleteOnExit()
            tempDir.toFile().deleteOnExit()
            System.load(tempLib.toAbsolutePath().toString())
            loaded = true
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to load nucleus_windows_decoration native library", e)
        }
    }

    val isLoaded: Boolean get() = loaded

    // Installs the custom decoration (subclasses WndProc, sets up DWM shadow).
    // Idempotent: if already installed, updates the title bar height.
    @JvmStatic
    external fun nativeInstallDecoration(
        hwnd: Long,
        titleBarHeightPx: Int,
    )

    // Removes the custom decoration and restores the original WndProc.
    @JvmStatic
    external fun nativeUninstallDecoration(hwnd: Long)

    // Toggles the forceHitTestClient flag. When true, WM_NCHITTEST returns
    // HTCLIENT in the title bar area so Compose handles the click.
    @JvmStatic
    external fun nativeSetForceHitTestClient(
        hwnd: Long,
        force: Boolean,
    )

    // Updates the title bar height used by the hit-test logic.
    @JvmStatic
    external fun nativeSetTitleBarHeight(
        hwnd: Long,
        heightPx: Int,
    )

    // Initiates a native window drag (with snap/tile support).
    // Called from Compose when an unconsumed press occurs in the title bar.
    @JvmStatic
    external fun nativeStartDrag(hwnd: Long)

    // Extracts the HWND from an AWT Window via JNI (bypasses JPMS restrictions).
    // Returns 0 if the handle cannot be obtained.
    @JvmStatic
    external fun nativeGetHwnd(awtWindow: java.awt.Window): Long

    // Applies rounded corners and DWM shadow to an undecorated dialog window (WS_POPUP).
    // Uses DWMWA_WINDOW_CORNER_PREFERENCE = DWMWCP_ROUND (Windows 11+, no-op on older).
    @JvmStatic
    external fun nativeApplyDialogStyle(hwnd: Long)

    // Returns debug counters as a string (temporary).
    @JvmStatic
    external fun nativeGetDebugInfo(hwnd: Long): String
}
