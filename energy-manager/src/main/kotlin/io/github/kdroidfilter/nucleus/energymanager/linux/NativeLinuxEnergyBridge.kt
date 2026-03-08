package io.github.kdroidfilter.nucleus.energymanager.linux

import java.nio.file.Files
import java.util.logging.Level
import java.util.logging.Logger

internal object NativeLinuxEnergyBridge {
    private val logger = Logger.getLogger(NativeLinuxEnergyBridge::class.java.simpleName)

    @Volatile
    private var loaded = false

    init {
        loadNativeLibrary()
    }

    private fun loadNativeLibrary() {
        if (loaded) return

        try {
            System.loadLibrary("nucleus_energy_manager")
            loaded = true
            return
        } catch (_: UnsatisfiedLinkError) {
            // Fall through to JAR extraction
        }

        @Suppress("TooGenericExceptionCaught")
        try {
            val arch =
                System.getProperty("os.arch").let {
                    if (it == "aarch64" || it == "arm64") "aarch64" else "x64"
                }
            val resourcePath = "/nucleus/native/linux-$arch/libnucleus_energy_manager.so"
            val stream =
                NativeLinuxEnergyBridge::class.java
                    .getResourceAsStream(resourcePath)
                    ?: throw UnsatisfiedLinkError("Native library not found in JAR at $resourcePath")
            val tempDir = Files.createTempDirectory("nucleus-native")
            val tempLib = tempDir.resolve("libnucleus_energy_manager.so")
            stream.use { Files.copy(it, tempLib) }
            tempLib.toFile().deleteOnExit()
            tempDir.toFile().deleteOnExit()
            System.load(tempLib.toAbsolutePath().toString())
            loaded = true
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to load nucleus_energy_manager native library", e)
        }
    }

    val isLoaded: Boolean get() = loaded

    @JvmStatic
    external fun nativeIsSupported(): Boolean

    @JvmStatic
    external fun nativeEnableEfficiencyMode(): Int

    @JvmStatic
    external fun nativeDisableEfficiencyMode(): Int

    @JvmStatic
    external fun nativeEnableThreadEfficiencyMode(): Int

    @JvmStatic
    external fun nativeDisableThreadEfficiencyMode(): Int

    @JvmStatic
    external fun nativeKeepScreenAwake(): Int

    @JvmStatic
    external fun nativeReleaseScreenAwake(): Int

    @JvmStatic
    external fun nativeIsScreenAwakeActive(): Boolean
}
