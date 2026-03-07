package io.github.kdroidfilter.nucleus.systemcolor.mac

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.kdroidfilter.nucleus.systemcolor.debugln
import java.util.function.Consumer

private const val TAG = "MacSystemColorDetector"

internal object MacSystemColorDetector {
    init {
        debugln(TAG) { "Initializing macOS system color observer via JNI" }
        NativeSystemColorBridge.nativeStartObserving()
    }

    fun isAccentColorSupported(): Boolean = NativeSystemColorBridge.nativeIsAccentColorSupported()

    fun getAccentColor(): Color? = NativeSystemColorBridge.getAccentColor()

    fun isHighContrast(): Boolean = NativeSystemColorBridge.nativeIsHighContrast()

    fun registerAccentListener(listener: Consumer<Color>) {
        NativeSystemColorBridge.registerAccentListener(listener)
    }

    fun removeAccentListener(listener: Consumer<Color>) {
        NativeSystemColorBridge.removeAccentListener(listener)
    }

    fun registerContrastListener(listener: Consumer<Boolean>) {
        NativeSystemColorBridge.registerContrastListener(listener)
    }

    fun removeContrastListener(listener: Consumer<Boolean>) {
        NativeSystemColorBridge.removeContrastListener(listener)
    }
}

@Composable
internal fun macOsAccentColor(): Color? {
    val colorState = remember { mutableStateOf(MacSystemColorDetector.getAccentColor()) }
    DisposableEffect(Unit) {
        val listener =
            Consumer<Color> { newColor ->
                colorState.value = newColor
            }
        MacSystemColorDetector.registerAccentListener(listener)
        onDispose {
            MacSystemColorDetector.removeAccentListener(listener)
        }
    }
    return colorState.value
}

@Composable
internal fun isMacOsInHighContrast(): Boolean {
    val contrastState = remember { mutableStateOf(MacSystemColorDetector.isHighContrast()) }
    DisposableEffect(Unit) {
        val listener =
            Consumer<Boolean> { isHigh ->
                contrastState.value = isHigh
            }
        MacSystemColorDetector.registerContrastListener(listener)
        onDispose {
            MacSystemColorDetector.removeContrastListener(listener)
        }
    }
    return contrastState.value
}
