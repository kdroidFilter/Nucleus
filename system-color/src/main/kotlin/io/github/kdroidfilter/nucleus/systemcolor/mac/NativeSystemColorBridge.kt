package io.github.kdroidfilter.nucleus.systemcolor.mac

import androidx.compose.ui.graphics.Color
import io.github.kdroidfilter.nucleus.systemcolor.debugln
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.logging.Level
import java.util.logging.Logger

private const val TAG = "NativeSystemColorBridge"
private const val RGB_COMPONENTS = 3

@Suppress("TooManyFunctions")
internal object NativeSystemColorBridge {
    private val logger = Logger.getLogger(NativeSystemColorBridge::class.java.simpleName)
    private val accentListeners: MutableSet<Consumer<Color>> = ConcurrentHashMap.newKeySet()
    private val contrastListeners: MutableSet<Consumer<Boolean>> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var loaded = false

    init {
        loadNativeLibrary()
    }

    private fun loadNativeLibrary() {
        if (loaded) return

        try {
            System.loadLibrary("nucleus_systemcolor")
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
            val resourcePath = "/nucleus/native/darwin-$arch/libnucleus_systemcolor.dylib"
            val stream =
                NativeSystemColorBridge::class.java
                    .getResourceAsStream(resourcePath)
                    ?: throw UnsatisfiedLinkError("Native library not found in JAR at $resourcePath")
            val tempDir = Files.createTempDirectory("nucleus-native")
            val tempLib = tempDir.resolve("libnucleus_systemcolor.dylib")
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
    external fun nativeGetAccentColor(out: FloatArray): Boolean

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
        val rgb = FloatArray(RGB_COMPONENTS)
        val success = nativeGetAccentColor(rgb)
        if (!success) {
            debugln(TAG) { "nativeGetAccentColor returned false" }
            return null
        }
        debugln(TAG) { "Accent color: r=${rgb[0]}, g=${rgb[1]}, b=${rgb[2]}" }
        return Color(rgb[0], rgb[1], rgb[2])
    }

    @JvmStatic
    fun onAccentColorChanged(
        r: Float,
        g: Float,
        b: Float,
    ) {
        val color = Color(r, g, b)
        debugln(TAG) { "Accent color changed: $color" }
        accentListeners.forEach { it.accept(color) }
    }

    @JvmStatic
    fun onContrastChanged(isHigh: Boolean) {
        debugln(TAG) { "Contrast mode changed: high=$isHigh" }
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
