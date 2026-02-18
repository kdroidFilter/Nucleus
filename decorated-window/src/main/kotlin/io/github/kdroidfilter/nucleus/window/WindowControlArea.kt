package io.github.kdroidfilter.nucleus.window

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import io.github.kdroidfilter.nucleus.core.runtime.LinuxDesktopEnvironment
import io.github.kdroidfilter.nucleus.window.styling.TitleBarStyle
import io.github.kdroidfilter.nucleus.window.utils.linux.linuxTitleBarIcons
import java.awt.Frame
import java.awt.event.WindowEvent

private val isKde = LinuxDesktopEnvironment.Current == LinuxDesktopEnvironment.KDE

@Suppress("FunctionNaming")
@Composable
internal fun TitleBarScope.WindowControlArea(
    window: java.awt.Window,
    state: DecoratedWindowState,
    style: TitleBarStyle,
) {
    val icons = linuxTitleBarIcons()

    // Close button (placed first with Alignment.End, so it's rightmost)
    // On KDE, focused windows show a softer pink hover, unfocused show bright red
    val closeHover = if (state.isActive) icons.closeHoverFocused else icons.closeHover
    val closePressed = if (state.isActive) icons.closePressedFocused else icons.closePressed
    ControlButton(
        onClick = { window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING)) },
        state = state,
        icon = icons.close,
        iconHover = closeHover,
        iconPressed = closePressed,
        contentDescription = "Close",
        style = style,
    )

    // Maximize/Restore button (only if resizable)
    val frame = window as? Frame
    if (frame != null && frame.isResizable) {
        if (state.isMaximized) {
            ControlButton(
                onClick = { frame.extendedState = Frame.NORMAL },
                state = state,
                icon = icons.restore,
                iconHover = icons.restoreHover,
                iconPressed = icons.restorePressed,
                contentDescription = "Restore",
                style = style,
            )
        } else {
            ControlButton(
                onClick = { frame.extendedState = Frame.MAXIMIZED_BOTH },
                state = state,
                icon = icons.maximize,
                iconHover = icons.maximizeHover,
                iconPressed = icons.maximizePressed,
                contentDescription = "Maximize",
                style = style,
            )
        }
    }

    // Minimize button (placed last with Alignment.End, so it's leftmost)
    ControlButton(
        onClick = {
            (window as? Frame)?.let {
                it.extendedState = it.extendedState or Frame.ICONIFIED
            }
        },
        state = state,
        icon = icons.minimize,
        iconHover = icons.minimizeHover,
        iconPressed = icons.minimizePressed,
        contentDescription = "Minimize",
        style = style,
    )
}

/**
 * Close button for dialog title bars.
 * Unlike [WindowControlArea], this only shows the close button (no minimize/maximize).
 */
@Suppress("FunctionNaming")
@Composable
internal fun TitleBarScope.DialogCloseButton(
    window: java.awt.Window,
    state: DecoratedDialogState,
    style: TitleBarStyle,
) {
    val icons = linuxTitleBarIcons()
    val windowState = state.toDecoratedWindowState()
    val closeHover = if (windowState.isActive) icons.closeHoverFocused else icons.closeHover
    val closePressed = if (windowState.isActive) icons.closePressedFocused else icons.closePressed

    ControlButton(
        onClick = { window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING)) },
        state = windowState,
        icon = icons.close,
        iconHover = closeHover,
        iconPressed = closePressed,
        contentDescription = "Close",
        style = style,
    )
}

@Suppress("FunctionNaming", "LongParameterList")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TitleBarScope.ControlButton(
    onClick: () -> Unit,
    state: DecoratedWindowState,
    icon: Painter,
    iconHover: Painter,
    iconPressed: Painter,
    contentDescription: String,
    style: TitleBarStyle,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            Modifier
                .align(Alignment.End)
                .focusable(false)
                .size(style.metrics.titlePaneButtonSize)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        var hovered by remember { mutableStateOf(false) }
        var pressed by remember { mutableStateOf(false) }

        val currentIcon =
            when {
                pressed && (state.isActive || isKde) -> iconPressed
                hovered && (state.isActive || isKde) -> iconHover
                else -> icon
            }

        Image(
            painter = currentIcon,
            contentDescription = contentDescription,
            modifier =
                Modifier
                    .onPointerEvent(PointerEventType.Enter) { hovered = true }
                    .onPointerEvent(PointerEventType.Exit) {
                        hovered = false
                        pressed = false
                    }.onPointerEvent(PointerEventType.Press) { pressed = true }
                    .onPointerEvent(PointerEventType.Release) { pressed = false },
        )
    }
}
