package io.github.kdroidfilter.nucleus.systemcolor.windows

import androidx.compose.ui.graphics.Color
import io.github.kdroidfilter.nucleus.systemcolor.debugln
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.logging.Level
import java.util.logging.Logger

private const val TAG = "NativeWindowsSystemColorBridge"
private const val RGB_COMPONENTS = 3
private const val COLOR_MAX = 255f

@Suppress("TooManyFunctions")
internal object NativeWindowsSystemColorBridge {
    private val logger = Logger.getLogger(NativeWindowsSystemColorBridge::class.java.simpleName)
    private val accentListeners: MutableSet<Consumer<Color>> = ConcurrentHashMap.newKeySet()
    private val contrastListeners: MutableSet<Consumer<Boolean>> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var loaded = false

    init {
        loadNativeLibrary()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadNativeLibrary() {
        if (loaded) return

        try {
            System.loadLibrary("nucleus_systemcolor")
            loaded = true
            return
        } catch (_: UnsatisfiedLinkError) {
            // Fall through to JAR extraction
        }

        try {
            val arch =
                System.getProperty("os.arch").let {
                    if (it == "aarch64" || it == "arm64") "aarch64" else "x64"
                }
            val resourcePath = "/nucleus/native/win32-$arch/nucleus_systemcolor.dll"
            val stream =
                NativeWindowsSystemColorBridge::class.java
                    .getResourceAsStream(resourcePath)
                    ?: throw UnsatisfiedLinkError("Native library not found in JAR at $resourcePath")
            val tempDir = Files.createTempDirectory("nucleus-native")
            val tempLib = tempDir.resolve("nucleus_systemcolor.dll")
            stream.use { Files.copy(it, tempLib) }
            tempLib.toFile().deleteOnExit()
            tempDir.toFile().deleteOnExit()
            System.load(tempLib.toAbsolutePath().toString())
            loaded = true
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to load nucleus_systemcolor native library", e)
        }
    }

    val isLoaded: Boolean get() = loaded

    @JvmStatic
    external fun nativeGetAccentColor(out: IntArray): Boolean

    @JvmStatic
    external fun nativeIsHighContrast(): Boolean

    @JvmStatic
    external fun nativeIsAccentColorSupported(): Boolean

    @JvmStatic
    external fun nativeStartObserving()

    @JvmStatic
    external fun nativeStopObserving()

    fun getAccentColor(): Color? {
        if (!loaded) {
            debugln(TAG) { "Native library not loaded, cannot get accent color" }
            return null
        }
        val rgb = IntArray(RGB_COMPONENTS)
        val success = nativeGetAccentColor(rgb)
        if (!success) {
            debugln(TAG) { "nativeGetAccentColor returned false" }
            return null
        }
        debugln(TAG) { "Accent color: r=${rgb[0]}, g=${rgb[1]}, b=${rgb[2]}" }
        return Color(rgb[0] / COLOR_MAX, rgb[1] / COLOR_MAX, rgb[2] / COLOR_MAX)
    }

    @JvmStatic
    fun onAccentColorChanged(
        r: Int,
        g: Int,
        b: Int,
    ) {
        val color = Color(r / COLOR_MAX, g / COLOR_MAX, b / COLOR_MAX)
        debugln(TAG) { "Accent color changed: $color" }
        accentListeners.forEach { it.accept(color) }
    }

    @JvmStatic
    fun onHighContrastChanged(isHigh: Boolean) {
        debugln(TAG) { "High contrast mode changed: $isHigh" }
        contrastListeners.forEach { it.accept(isHigh) }
    }

    fun registerAccentListener(listener: Consumer<Color>) {
        accentListeners.add(listener)
    }

    fun removeAccentListener(listener: Consumer<Color>) {
        accentListeners.remove(listener)
    }

    fun registerContrastListener(listener: Consumer<Boolean>) {
        contrastListeners.add(listener)
    }

    fun removeContrastListener(listener: Consumer<Boolean>) {
        contrastListeners.remove(listener)
    }
}
