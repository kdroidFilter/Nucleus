package io.github.kdroidfilter.nucleus.window.utils.macos

import java.nio.file.Files
import java.util.logging.Level
import java.util.logging.Logger

internal object JniMacTitleBarBridge {
    private val logger = Logger.getLogger(JniMacTitleBarBridge::class.java.simpleName)

    @Volatile
    private var loaded = false

    init {
        loadNativeLibrary()
    }

    private fun loadNativeLibrary() {
        if (loaded) return

        // Try system library path first (packaged app)
        try {
            System.loadLibrary("nucleus_macos_jni")
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
            val resourcePath = "/nucleus/native/darwin-$arch/libnucleus_macos_jni.dylib"
            val stream =
                JniMacTitleBarBridge::class.java
                    .getResourceAsStream(resourcePath)
                    ?: throw UnsatisfiedLinkError("Native library not found in JAR at $resourcePath")
            val tempDir = Files.createTempDirectory("nucleus-jni-native")
            val tempLib = tempDir.resolve("libnucleus_macos_jni.dylib")
            stream.use { Files.copy(it, tempLib) }
            tempLib.toFile().deleteOnExit()
            tempDir.toFile().deleteOnExit()
            System.load(tempLib.toAbsolutePath().toString())
            loaded = true
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to load nucleus_macos_jni native library", e)
        }
    }

    val isLoaded: Boolean get() = loaded

    // Sets up (or updates) the custom title bar and repositions traffic light buttons.
    // heightPt: title bar height in NSPoints (= dp on macOS).
    // Returns the left inset in points to reserve space for the traffic lights.
    @JvmStatic
    external fun nativeApplyTitleBar(
        nsWindowPtr: Long,
        heightPt: Float,
    ): Float

    // Removes all custom constraints, fullscreen observer, and restores AppKit defaults.
    @JvmStatic
    external fun nativeResetTitleBar(nsWindowPtr: Long)

    // Updates the position of the replacement fullscreen buttons (called on layout passes).
    @JvmStatic
    external fun nativeUpdateFullScreenButtons(nsWindowPtr: Long)

    // Performs the macOS title bar double-click action (zoom or minimize)
    // respecting the user's AppleActionOnDoubleClick system preference.
    @JvmStatic
    external fun nativePerformTitleBarDoubleClickAction(nsWindowPtr: Long)

    // Suppresses or enables window drag on the title bar drag view.
    // When suppressed, dragging in the title bar area will not move the window,
    // allowing Compose drag-and-drop to work.
    @JvmStatic
    external fun nativeSetDragSuppressed(nsWindowPtr: Long, suppressed: Boolean)
}
