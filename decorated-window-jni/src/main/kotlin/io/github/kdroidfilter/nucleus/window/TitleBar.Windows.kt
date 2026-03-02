package io.github.kdroidfilter.nucleus.window

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.nucleus.window.styling.LocalTitleBarStyle
import io.github.kdroidfilter.nucleus.window.styling.TitleBarStyle
import io.github.kdroidfilter.nucleus.window.utils.windows.JniWindowsDecorationBridge
import io.github.kdroidfilter.nucleus.window.utils.windows.JniWindowsWindowUtil
import java.awt.Frame

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("FunctionNaming")
@Composable
internal fun DecoratedWindowScope.WindowsTitleBar(
    modifier: Modifier = Modifier,
    gradientStartColor: Color = Color.Unspecified,
    style: TitleBarStyle = LocalTitleBarStyle.current,
    content: @Composable TitleBarScope.(DecoratedWindowState) -> Unit = {},
) {
    if (JniWindowsDecorationBridge.isLoaded) {
        NativeWindowsTitleBar(modifier, gradientStartColor, style, content)
    } else {
        FallbackWindowsTitleBar(modifier, gradientStartColor, style, content)
    }
}

// Native title bar: uses JNI WndProc subclass for resize borders and DWM shadow.
// All title bar clicks go to Compose. Drag is initiated from Compose via nativeStartDrag
// when a press is unconsumed by interactive elements (buttons, switches, etc.).
// Double-click to maximize is handled in Compose.
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("FunctionNaming")
@Composable
private fun DecoratedWindowScope.NativeWindowsTitleBar(
    modifier: Modifier,
    gradientStartColor: Color,
    style: TitleBarStyle,
    content: @Composable TitleBarScope.(DecoratedWindowState) -> Unit,
) {
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current
    var lastPressTime = 0L

    // Install decoration and clean up on dispose
    DisposableEffect(window) {
        val hwnd = JniWindowsWindowUtil.getHwnd(window)
        if (hwnd != 0L) {
            val heightPx = with(density) { style.metrics.height.roundToPx() }
            JniWindowsDecorationBridge.nativeInstallDecoration(hwnd, heightPx)

            onDispose {
                val h = JniWindowsWindowUtil.getHwnd(window)
                if (h != 0L) JniWindowsDecorationBridge.nativeUninstallDecoration(h)
            }
        } else {
            onDispose { }
        }
    }

    TitleBarImpl(
        modifier = modifier,
        gradientStartColor = gradientStartColor,
        style = style,
        applyTitleBar = { height, _ ->
            val hwnd = JniWindowsWindowUtil.getHwnd(window)
            if (hwnd != 0L) {
                val heightPx = with(density) { height.roundToPx() }
                JniWindowsDecorationBridge.nativeSetTitleBarHeight(hwnd, heightPx)
            }
            PaddingValues(0.dp)
        },
        backgroundContent = {
            // Background handles drag and double-click for the non-interactive
            // area. Interactive elements (buttons, switches) are in the foreground
            // and consume the press, so it never reaches this handler.
            Spacer(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onPointerEvent(PointerEventType.Press, PointerEventPass.Main) {
                            if (
                                this.currentEvent.button == PointerButton.Primary &&
                                this.currentEvent.changes.any { !it.isConsumed }
                            ) {
                                val now = System.currentTimeMillis()
                                val elapsed = now - lastPressTime
                                if (elapsed in viewConfig.doubleTapMinTimeMillis..viewConfig.doubleTapTimeoutMillis) {
                                    // Double-click: toggle maximize
                                    if (state.isMaximized) {
                                        window.extendedState = Frame.NORMAL
                                    } else {
                                        window.extendedState = Frame.MAXIMIZED_BOTH
                                    }
                                } else {
                                    // Single press: initiate native drag
                                    val hwnd = JniWindowsWindowUtil.getHwnd(window)
                                    if (hwnd != 0L) {
                                        JniWindowsDecorationBridge.nativeStartDrag(hwnd)
                                    }
                                }
                                lastPressTime = now
                            }
                        },
            )
        },
    ) { currentState ->
        WindowsWindowControlArea(window, currentState, style)
        content(currentState)
    }
}

// Fallback title bar: Compose-based drag and double-click (no native lib).
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("FunctionNaming")
@Composable
private fun DecoratedWindowScope.FallbackWindowsTitleBar(
    modifier: Modifier,
    gradientStartColor: Color,
    style: TitleBarStyle,
    content: @Composable TitleBarScope.(DecoratedWindowState) -> Unit,
) {
    val viewConfig = LocalViewConfiguration.current
    var lastPress = 0L

    TitleBarImpl(
        modifier =
            modifier.onPointerEvent(PointerEventType.Press, PointerEventPass.Main) {
                if (
                    this.currentEvent.button == PointerButton.Primary &&
                    this.currentEvent.changes.any { !it.isConsumed }
                ) {
                    val now = System.currentTimeMillis()
                    if (now - lastPress in viewConfig.doubleTapMinTimeMillis..viewConfig.doubleTapTimeoutMillis) {
                        if (state.isMaximized) {
                            window.extendedState = Frame.NORMAL
                        } else {
                            window.extendedState = Frame.MAXIMIZED_BOTH
                        }
                    }
                    lastPress = now
                }
            },
        gradientStartColor = gradientStartColor,
        style = style,
        applyTitleBar = { _, _ -> PaddingValues(0.dp) },
        backgroundContent = {
            Spacer(modifier = Modifier.fillMaxSize().windowDragHandler(window))
        },
    ) { currentState ->
        WindowsWindowControlArea(window, currentState, style)
        content(currentState)
    }
}
