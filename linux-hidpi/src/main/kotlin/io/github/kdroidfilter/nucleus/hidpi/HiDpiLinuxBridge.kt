package io.github.kdroidfilter.nucleus.hidpi

import java.nio.file.Files
import java.util.logging.Level
import java.util.logging.Logger

internal object HiDpiLinuxBridge {
    private val logger = Logger.getLogger(HiDpiLinuxBridge::class.java.simpleName)

    @Volatile
    private var loaded = false

    init {
        loadNativeLibrary()
    }

    private fun loadNativeLibrary() {
        if (loaded) return

        // Try system library path first (packaged app)
        try {
            System.loadLibrary("nucleus_linux_hidpi_jni")
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
            val resourcePath = "/nucleus/native/linux-$arch/libnucleus_linux_hidpi_jni.so"
            val stream =
                HiDpiLinuxBridge::class.java.getResourceAsStream(resourcePath)
                    ?: throw UnsatisfiedLinkError("Native library not found in JAR at $resourcePath")
            val tempDir = Files.createTempDirectory("nucleus-hidpi-native")
            val tempLib = tempDir.resolve("libnucleus_linux_hidpi_jni.so")
            stream.use { Files.copy(it, tempLib) }
            tempLib.toFile().deleteOnExit()
            tempDir.toFile().deleteOnExit()
            System.load(tempLib.toAbsolutePath().toString())
            loaded = true
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to load nucleus_linux_hidpi_jni native library", e)
        }
    }

    val isLoaded: Boolean get() = loaded

    // Returns the native HiDPI scale factor detected from the Linux desktop
    // environment (GSettings, GDK_SCALE, Xft.dpi, …).
    // Returns 0.0 if the scale cannot be determined.
    @JvmStatic
    external fun nativeGetScaleFactor(): Double

    // Sets GDK_SCALE in the process environment so the JDK's native
    // X11GraphicsDevice.getNativeScaleFactor() picks up the scale through
    // the standard detection path. This ensures both rendering AND mouse
    // event coordinates are properly scaled (XWindow.scaleDown).
    // Does not overwrite GDK_SCALE if it is already set by the desktop session.
    @JvmStatic
    external fun nativeApplyScaleToEnv(scale: Int)
}
