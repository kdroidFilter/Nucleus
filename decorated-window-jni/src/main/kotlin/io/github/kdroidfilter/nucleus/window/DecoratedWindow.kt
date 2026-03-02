package io.github.kdroidfilter.nucleus.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import io.github.kdroidfilter.nucleus.core.runtime.Platform
import io.github.kdroidfilter.nucleus.window.utils.windows.JniWindowsDecorationBridge

@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun DecoratedWindow(
    onCloseRequest: () -> Unit,
    state: WindowState = rememberWindowState(),
    visible: Boolean = true,
    title: String = "",
    icon: Painter? = null,
    resizable: Boolean = true,
    enabled: Boolean = true,
    focusable: Boolean = true,
    alwaysOnTop: Boolean = false,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    content: @Composable DecoratedWindowScope.() -> Unit,
) {
    // On macOS the native frame is kept and the content is extended into the
    // transparent title bar area via AWT client properties.
    // On Windows, when the native decoration DLL is loaded, we keep the native
    // frame (undecorated = false) and use JNI to subclass WndProc removing just
    // the title bar. This gives us native drag, snap/tile, and maximize animations.
    // Fallback: when the DLL is not loaded, the window is fully undecorated.
    // On Linux the window is always undecorated.
    val undecorated =
        when (Platform.Current) {
            Platform.Windows -> !JniWindowsDecorationBridge.isLoaded
            Platform.Linux -> true
            else -> false
        }

    Window(
        onCloseRequest,
        state,
        visible,
        title,
        icon,
        undecorated,
        transparent = false,
        resizable,
        enabled,
        focusable,
        alwaysOnTop,
        onPreviewKeyEvent,
        onKeyEvent,
    ) {
        DecoratedWindowBody(
            title = title,
            icon = icon,
            undecorated = undecorated,
            content = content,
        )
    }
}
