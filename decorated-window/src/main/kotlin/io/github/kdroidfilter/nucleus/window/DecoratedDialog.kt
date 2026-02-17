package io.github.kdroidfilter.nucleus.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.offset
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.DialogWindowScope
import androidx.compose.ui.window.rememberDialogState
import com.jetbrains.JBR
import io.github.kdroidfilter.nucleus.window.internal.insideBorder
import io.github.kdroidfilter.nucleus.window.styling.LocalDecoratedWindowStyle
import io.github.kdroidfilter.nucleus.window.utils.DesktopPlatform
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun DecoratedDialog(
    onCloseRequest: () -> Unit,
    state: DialogState = rememberDialogState(),
    visible: Boolean = true,
    title: String = "",
    icon: Painter? = null,
    resizable: Boolean = false,
    enabled: Boolean = true,
    focusable: Boolean = true,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    content: @Composable DecoratedDialogScope.() -> Unit,
) {
    remember {
        check(JBR.isAvailable()) {
            "DecoratedDialog requires JetBrains Runtime (JBR). " +
                "Please run your application on JBR."
        }
    }

    val undecorated = DesktopPlatform.Linux == DesktopPlatform.Current

    DialogWindow(
        onCloseRequest = onCloseRequest,
        state = state,
        visible = visible,
        title = title,
        icon = icon,
        undecorated = undecorated,
        transparent = false,
        resizable = resizable,
        enabled = enabled,
        focusable = focusable,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
    ) {
        var decoratedDialogState by remember { mutableStateOf(DecoratedDialogState.of(window)) }

        DisposableEffect(window) {
            val adapter =
                object : WindowAdapter(), ComponentListener {
                    override fun windowActivated(e: WindowEvent?) {
                        decoratedDialogState = DecoratedDialogState.of(window)
                    }

                    override fun windowDeactivated(e: WindowEvent?) {
                        decoratedDialogState = DecoratedDialogState.of(window)
                    }

                    override fun componentResized(e: ComponentEvent?) {
                        decoratedDialogState = DecoratedDialogState.of(window)
                    }

                    override fun componentMoved(e: ComponentEvent?) {
                        // No-op: dialog position changes don't affect decorated state
                    }

                    override fun componentShown(e: ComponentEvent?) {
                        // No-op: visibility handled elsewhere
                    }

                    override fun componentHidden(e: ComponentEvent?) {
                        // No-op: visibility handled elsewhere
                    }
                }

            window.addWindowListener(adapter)
            window.addComponentListener(adapter)

            onDispose {
                window.removeWindowListener(adapter)
                window.removeComponentListener(adapter)
            }
        }

        val style = LocalDecoratedWindowStyle.current
        val undecoratedWindowBorder =
            if (undecorated) {
                Modifier.insideBorder(
                    style.metrics.borderWidth,
                    style.colors.borderFor(decoratedDialogState.toDecoratedWindowState()).value,
                )
            } else {
                Modifier
            }

        CompositionLocalProvider(LocalDialogTitleBarInfo provides DialogTitleBarInfo(title, icon)) {
            Layout(
                content = {
                    val scope =
                        object : DecoratedDialogScope {
                            override val state: DecoratedDialogState
                                get() = decoratedDialogState

                            override val window: ComposeDialog
                                get() = this@DialogWindow.window
                        }
                    scope.content()
                },
                modifier = undecoratedWindowBorder,
                measurePolicy = DecoratedDialogMeasurePolicy,
            )
        }
    }
}

@Stable
interface DecoratedDialogScope : DialogWindowScope {
    override val window: ComposeDialog

    val state: DecoratedDialogState
}

private object DecoratedDialogMeasurePolicy : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        if (measurables.isEmpty()) {
            return layout(width = constraints.minWidth, height = constraints.minHeight) {}
        }

        val titleBars = measurables.filter { it.layoutId == TITLE_BAR_LAYOUT_ID }
        if (titleBars.size > 1) {
            error("Dialog can have only one title bar")
        }
        val titleBar = titleBars.firstOrNull()
        val titleBarBorder = measurables.firstOrNull { it.layoutId == TITLE_BAR_BORDER_LAYOUT_ID }

        val contentConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        val titleBarPlaceable = titleBar?.measure(contentConstraints)
        val titleBarHeight = titleBarPlaceable?.height ?: 0

        val titleBarBorderPlaceable = titleBarBorder?.measure(contentConstraints)
        val titleBarBorderHeight = titleBarBorderPlaceable?.height ?: 0

        val measuredPlaceable = mutableListOf<Placeable>()

        for (it in measurables) {
            if (it.layoutId.toString().startsWith(TITLE_BAR_COMPONENT_LAYOUT_ID_PREFIX)) continue
            val offsetConstraints = contentConstraints.offset(vertical = -titleBarHeight - titleBarBorderHeight)
            val placeable = it.measure(offsetConstraints)
            measuredPlaceable += placeable
        }

        return layout(constraints.maxWidth, constraints.maxHeight) {
            titleBarPlaceable?.placeRelative(0, 0)
            titleBarBorderPlaceable?.placeRelative(0, titleBarHeight)

            measuredPlaceable.forEach { it.placeRelative(0, titleBarHeight + titleBarBorderHeight) }
        }
    }
}

@Immutable
@JvmInline
value class DecoratedDialogState(
    val state: ULong,
) {
    val isActive: Boolean
        get() = state and Active != 0UL

    fun copy(active: Boolean = isActive): DecoratedDialogState = of(active = active)

    fun toDecoratedWindowState(): DecoratedWindowState =
        DecoratedWindowState.of(
            fullscreen = false,
            minimized = false,
            maximized = false,
            active = isActive,
        )

    override fun toString(): String = "${javaClass.simpleName}(isActive=$isActive)"

    companion object {
        val Active: ULong = 1UL shl 0

        fun of(active: Boolean = true): DecoratedDialogState =
            DecoratedDialogState(
                if (active) Active else 0UL,
            )

        fun of(window: ComposeDialog): DecoratedDialogState = of(active = window.isActive)
    }
}

internal data class DialogTitleBarInfo(
    val title: String,
    val icon: Painter?,
)

internal val LocalDialogTitleBarInfo: ProvidableCompositionLocal<DialogTitleBarInfo> =
    compositionLocalOf {
        error("LocalDialogTitleBarInfo not provided, DialogTitleBar must be used in DecoratedDialog")
    }
