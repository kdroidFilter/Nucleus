package io.github.kdroidfilter.nucleus.window

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.nucleus.core.runtime.LinuxDesktopEnvironment
import io.github.kdroidfilter.nucleus.window.styling.TitleBarStyle
import io.github.kdroidfilter.nucleus.window.utils.linux.JniLinuxWindowBridge
import java.awt.Frame
import java.awt.MouseInfo

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("FunctionNaming")
@Composable
internal fun DecoratedWindowScope.LinuxTitleBar(
    modifier: Modifier = Modifier,
    gradientStartColor: Color = Color.Unspecified,
    style: TitleBarStyle,
    content: @Composable TitleBarScope.(DecoratedWindowState) -> Unit = {},
) {
    if (JniLinuxWindowBridge.isLoaded) {
        NativeLinuxTitleBar(modifier, gradientStartColor, style, content)
    } else {
        FallbackLinuxTitleBar(modifier, gradientStartColor, style, content)
    }
}

// Native title bar: uses JNI to send _NET_WM_MOVERESIZE for native WM drag.
// Double-click to maximize is handled in Compose.
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("FunctionNaming")
@Composable
private fun DecoratedWindowScope.NativeLinuxTitleBar(
    modifier: Modifier,
    gradientStartColor: Color,
    style: TitleBarStyle,
    content: @Composable TitleBarScope.(DecoratedWindowState) -> Unit,
) {
    val linuxStyle = createLinuxTitleBarStyle(style)
    val viewConfig = LocalViewConfiguration.current
    var lastPressTime = 0L

    TitleBarImpl(
        modifier = modifier,
        gradientStartColor = gradientStartColor,
        style = linuxStyle,
        applyTitleBar = { _, _ ->
            if (LinuxDesktopEnvironment.Current == LinuxDesktopEnvironment.KDE) {
                PaddingValues(end = 4.dp)
            } else {
                PaddingValues(0.dp)
            }
        },
        backgroundContent = {
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
                                    // Single press: initiate native WM move
                                    val mouseLocation = MouseInfo.getPointerInfo()?.location
                                    if (mouseLocation != null) {
                                        JniLinuxWindowBridge.nativeStartWindowMove(
                                            window,
                                            mouseLocation.x,
                                            mouseLocation.y,
                                            1,
                                        )
                                    }
                                }
                                lastPressTime = now
                            }
                        },
            )
        },
    ) { currentState ->
        WindowControlArea(window, currentState, linuxStyle)
        content(currentState)
    }
}

// Fallback title bar: Compose-based drag and double-click (no native lib).
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("FunctionNaming")
@Composable
private fun DecoratedWindowScope.FallbackLinuxTitleBar(
    modifier: Modifier,
    gradientStartColor: Color,
    style: TitleBarStyle,
    content: @Composable TitleBarScope.(DecoratedWindowState) -> Unit,
) {
    val linuxStyle = createLinuxTitleBarStyle(style)
    val viewConfig = LocalViewConfiguration.current

    var lastPress = 0L

    TitleBarImpl(
        // Detect double-click to maximize/restore on the title bar area
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
        style = linuxStyle,
        applyTitleBar = { _, _ ->
            if (LinuxDesktopEnvironment.Current == LinuxDesktopEnvironment.KDE) {
                PaddingValues(end = 4.dp)
            } else {
                PaddingValues(0.dp)
            }
        },
        // Compose-based drag replaces JBR.getWindowMove()
        backgroundContent = {
            Spacer(modifier = Modifier.fillMaxSize().windowDragHandler(window))
        },
    ) { currentState ->
        WindowControlArea(window, currentState, linuxStyle)
        content(currentState)
    }
}
