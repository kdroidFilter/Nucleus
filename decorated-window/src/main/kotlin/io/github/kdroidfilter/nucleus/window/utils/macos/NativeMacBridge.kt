package io.github.kdroidfilter.nucleus.window.utils.macos

import java.nio.file.Files
import java.util.logging.Level
import java.util.logging.Logger

internal object NativeMacBridge {
    private val logger = Logger.getLogger(NativeMacBridge::class.java.simpleName)

    @Volatile
    private var loaded = false

    init {
        loadNativeLibrary()
    }

    private fun loadNativeLibrary() {
        if (loaded) return

        // Try system library path first (packaged app)
        try {
            System.loadLibrary("nucleus_macos")
            loaded = true
            return
        } catch (_: UnsatisfiedLinkError) {
            // Fall through to JAR extraction
        }

        // Fallback: extract from JAR resources
        try {
            val stream = NativeMacBridge::class.java
                .getResourceAsStream("/nucleus/native/darwin-universal/libnucleus_macos.dylib")
                ?: throw UnsatisfiedLinkError("Native library not found in JAR")
            val tempDir = Files.createTempDirectory("nucleus-native")
            val tempLib = tempDir.resolve("libnucleus_macos.dylib")
            stream.use { Files.copy(it, tempLib) }
            tempLib.toFile().deleteOnExit()
            tempDir.toFile().deleteOnExit()
            System.load(tempLib.toAbsolutePath().toString())
            loaded = true
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to load nucleus_macos native library", e)
        }
    }

    val isLoaded: Boolean get() = loaded

    @JvmStatic
    external fun nativeUpdateColors(nsWindowPtr: Long)

    @JvmStatic
    external fun nativeUpdateFullScreenButtons(nsWindowPtr: Long)
}
