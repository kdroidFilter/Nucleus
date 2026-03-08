package io.github.kdroidfilter.nucleus.darkmodedetector.windows

import io.github.kdroidfilter.nucleus.darkmodedetector.debugln
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.logging.Level
import java.util.logging.Logger

private const val TAG = "NativeWindowsBridge"

internal object NativeWindowsBridge {
    private val logger = Logger.getLogger(NativeWindowsBridge::class.java.simpleName)
    private val listeners: MutableSet<Consumer<Boolean>> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var loaded = false

    init {
        loadNativeLibrary()
    }

    private fun loadNativeLibrary() {
        if (loaded) return

        try {
            System.loadLibrary("nucleus_windows_theme")
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
            val resourcePath = "/nucleus/native/win32-$arch/nucleus_windows_theme.dll"
            val stream =
                NativeWindowsBridge::class.java
                    .getResourceAsStream(resourcePath)
                    ?: throw UnsatisfiedLinkError("Native library not found in JAR at $resourcePath")
            val tempDir = Files.createTempDirectory("nucleus-native")
            val tempLib = tempDir.resolve("nucleus_windows_theme.dll")
            stream.use { Files.copy(it, tempLib) }
            tempLib.toFile().deleteOnExit()
            tempDir.toFile().deleteOnExit()
            System.load(tempLib.toAbsolutePath().toString())
            loaded = true
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to load nucleus_windows_theme native library", e)
        }
    }

    val isLoaded: Boolean get() = loaded

    @JvmStatic
    external fun nativeIsDark(): Boolean

    @JvmStatic
    external fun nativeStartObserving()

    @JvmStatic
    external fun nativeStopObserving()

    @JvmStatic
    fun onThemeChanged(isDark: Boolean) {
        debugln(TAG) { "Theme change detected via JNI. Dark mode: $isDark" }
        listeners.forEach { it.accept(isDark) }
    }

    fun registerListener(listener: Consumer<Boolean>) {
        listeners.add(listener)
    }

    fun removeListener(listener: Consumer<Boolean>) {
        listeners.remove(listener)
    }
}
