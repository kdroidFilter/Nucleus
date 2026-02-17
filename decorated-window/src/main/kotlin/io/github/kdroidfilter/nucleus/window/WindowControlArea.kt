package io.github.kdroidfilter.nucleus.window

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.nucleus.window.styling.TitleBarStyle
import io.github.kdroidfilter.nucleus.window.utils.linux.linuxTitleBarIcons
import java.awt.Frame
import java.awt.event.WindowEvent

@Suppress("FunctionNaming")
@Composable
internal fun TitleBarScope.WindowControlArea(
    window: java.awt.Window,
    state: DecoratedWindowState,
    style: TitleBarStyle,
    iconHoveredEffect: Boolean = false,
) {
    val icons = linuxTitleBarIcons()

    // Close button (placed first with Alignment.End, so it's rightmost)
    ControlButton(
        onClick = { window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING)) },
        state = state,
        icon = if (state.isActive) icons.close else icons.closeInactive,
        contentDescription = "Close",
        style = style,
        iconHoveredEffect = iconHoveredEffect,
    )

    // Maximize/Restore button (only if resizable)
    val frame = window as? Frame
    if (frame != null && frame.isResizable) {
        if (state.isMaximized) {
            ControlButton(
                onClick = { frame.extendedState = Frame.NORMAL },
                state = state,
                icon = if (state.isActive) icons.restore else icons.restoreInactive,
                contentDescription = "Restore",
                style = style,
                iconHoveredEffect = iconHoveredEffect,
            )
        } else {
            ControlButton(
                onClick = { frame.extendedState = Frame.MAXIMIZED_BOTH },
                state = state,
                icon = if (state.isActive) icons.maximize else icons.maximizeInactive,
                contentDescription = "Maximize",
                style = style,
                iconHoveredEffect = iconHoveredEffect,
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
        icon = if (state.isActive) icons.minimize else icons.minimizeInactive,
        contentDescription = "Minimize",
        style = style,
        iconHoveredEffect = iconHoveredEffect,
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
    iconHoveredEffect: Boolean = false,
) {
    val icons = linuxTitleBarIcons()
    val windowState = state.toDecoratedWindowState()

    ControlButton(
        onClick = { window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING)) },
        state = windowState,
        icon = if (windowState.isActive) icons.close else icons.closeInactive,
        contentDescription = "Close",
        style = style,
        iconHoveredEffect = iconHoveredEffect,
    )
}

@Suppress("FunctionNaming")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TitleBarScope.ControlButton(
    onClick: () -> Unit,
    state: DecoratedWindowState,
    icon: Painter,
    contentDescription: String,
    style: TitleBarStyle,
    iconHoveredEffect: Boolean,
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

        val hoverModifier =
            if (iconHoveredEffect && state.isActive) {
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .drawWithContent {
                        drawContent()
                        if (hovered) {
                            // Lighten only the icon by overlaying a subtle white tint
                            drawRect(Color.White.copy(alpha = 0.02f), blendMode = BlendMode.SrcOver)
                        }
                    }.onPointerEvent(PointerEventType.Enter) { hovered = true }
                    .onPointerEvent(PointerEventType.Exit) { hovered = false }
            } else {
                Modifier
            }

        Image(
            painter = icon,
            contentDescription = contentDescription,
            modifier = hoverModifier,
        )
    }
}
