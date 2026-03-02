package io.github.kdroidfilter.nucleus.window.utils.linux

import java.nio.file.Files
import java.util.logging.Level
import java.util.logging.Logger

internal object JniLinuxWindowBridge {
    private val logger = Logger.getLogger(JniLinuxWindowBridge::class.java.simpleName)

    @Volatile
    private var loaded = false

    init {
        loadNativeLibrary()
    }

    private fun loadNativeLibrary() {
        if (loaded) return

        // Try system library path first (packaged app)
        try {
            System.loadLibrary("nucleus_linux_jni")
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
            val resourcePath = "/nucleus/native/linux-$arch/libnucleus_linux_jni.so"
            val stream =
                JniLinuxWindowBridge::class.java
                    .getResourceAsStream(resourcePath)
                    ?: throw UnsatisfiedLinkError("Native library not found in JAR at $resourcePath")
            val tempDir = Files.createTempDirectory("nucleus-jni-native")
            val tempLib = tempDir.resolve("libnucleus_linux_jni.so")
            stream.use { Files.copy(it, tempLib) }
            tempLib.toFile().deleteOnExit()
            tempDir.toFile().deleteOnExit()
            System.load(tempLib.toAbsolutePath().toString())
            loaded = true
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to load nucleus_linux_jni native library", e)
        }
    }

    val isLoaded: Boolean get() = loaded

    // Initiates a native window move via _NET_WM_MOVERESIZE.
    // rootX/rootY: absolute mouse coordinates on screen.
    // button: X11 button number (1 = left).
    // Returns true on success.
    @JvmStatic
    external fun nativeStartWindowMove(
        awtWindow: java.awt.Window,
        rootX: Int,
        rootY: Int,
        button: Int,
    ): Boolean

    // Checks if the window manager supports _NET_WM_MOVERESIZE.
    @JvmStatic
    external fun nativeIsWmMoveResizeSupported(awtWindow: java.awt.Window): Boolean
}
