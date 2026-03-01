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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.nucleus.window.styling.LocalTitleBarStyle
import io.github.kdroidfilter.nucleus.window.styling.TitleBarStyle
import io.github.kdroidfilter.nucleus.window.utils.macos.JniMacTitleBarBridge
import io.github.kdroidfilter.nucleus.window.utils.macos.JniMacWindowUtil
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("FunctionNaming")
@Composable
internal fun DecoratedWindowScope.MacOSTitleBar(
    modifier: Modifier = Modifier,
    gradientStartColor: Color = Color.Unspecified,
    style: TitleBarStyle = LocalTitleBarStyle.current,
    content: @Composable TitleBarScope.(DecoratedWindowState) -> Unit = {},
) {
    DisposableEffect(window) {
        onDispose {
            val ptr = JniMacWindowUtil.getWindowPtr(window)
            if (ptr != 0L) JniMacTitleBarBridge.nativeResetTitleBar(ptr)
        }
    }

    val viewConfig = LocalViewConfiguration.current
    var lastPress = 0L

    TitleBarImpl(
        // Detect double-click to zoom/minimize respecting macOS system preference.
        // Uses Final pass so interactive Compose children (buttons) consume the
        // event first — only unconsumed double-clicks trigger the action.
        // titleBarHitTestHandler must be on the parent modifier (not backgroundContent)
        // so it sees consumed events from children (tabs, buttons) in PointerEventPass.Main.
        // Double-click uses Final pass so interactive children consume the event first.
        modifier =
            modifier
                .titleBarHitTestHandler(window)
                .onPointerEvent(PointerEventType.Press, PointerEventPass.Final) {
                    if (
                        this.currentEvent.button == PointerButton.Primary &&
                        this.currentEvent.changes.any { !it.isConsumed }
                    ) {
                        val now = System.currentTimeMillis()
                        if (now - lastPress in viewConfig.doubleTapMinTimeMillis..viewConfig.doubleTapTimeoutMillis) {
                            val ptr = JniMacWindowUtil.getWindowPtr(window)
                            if (ptr != 0L && JniMacTitleBarBridge.isLoaded) {
                                JniMacTitleBarBridge.nativePerformTitleBarDoubleClickAction(ptr)
                            }
                        }
                        lastPress = now
                    }
                },
        gradientStartColor = gradientStartColor,
        style = style,
        applyTitleBar = { height, titleBarState ->
            JniMacWindowUtil.applyWindowProperties(window)

            val ptr = JniMacWindowUtil.getWindowPtr(window)

            if (titleBarState.isFullscreen) {
                PaddingValues(start = 80.dp)
            } else {
                val leftInset =
                    if (ptr != 0L && JniMacTitleBarBridge.isLoaded) {
                        JniMacTitleBarBridge.nativeApplyTitleBar(ptr, height.value)
                    } else {
                        val shrink = minOf(height.value / 28f, 1f)
                        height.value + 2f * shrink * 20f
                    }
                PaddingValues(start = leftInset.dp)
            }
        },
        onPlace = {
            if (state.isFullscreen) {
                val ptr = JniMacWindowUtil.getWindowPtr(window)
                if (ptr != 0L && JniMacTitleBarBridge.isLoaded) {
                    JniMacTitleBarBridge.nativeUpdateFullScreenButtons(ptr)
                }
            }
        },
        backgroundContent = {
            Spacer(modifier = Modifier.fillMaxSize())
        },
        content = content,
    )
}

/**
 * Mirrors JBR's `customTitleBarMouseEventHandler` / `forceHitTest` approach.
 * Runs on the parent modifier (Main pass, after children have processed events).
 *
 * - Unconsumed Press → marks a pending drag (button down on empty title bar area).
 * - Unconsumed Move while pending → initiates native window drag via JNI.
 * - Consumed Press → enters `inUserControl` (interactive child handles it).
 * - Release → resets state.
 *
 * The native NucleusDragView is a pure pass-through; all drag decisions live here.
 */
internal fun Modifier.titleBarHitTestHandler(window: java.awt.Window): Modifier =
    pointerInput(window) {
        println("[Nucleus-Compose] titleBarHitTestHandler: pointerInput started")
        val ctx = coroutineContext
        awaitPointerEventScope {
            var inUserControl = false
            var pendingDrag = false
            while (ctx.isActive) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                event.changes.forEach {
                    if (!it.isConsumed && !inUserControl) {
                        when (event.type) {
                            PointerEventType.Press -> {
                                println("[Nucleus-Compose] hitTest: UNCONSUMED Press -> pendingDrag=true")
                                pendingDrag = true
                            }
                            PointerEventType.Move -> if (pendingDrag) {
                                println("[Nucleus-Compose] hitTest: UNCONSUMED Move -> startWindowDrag")
                                startWindowDrag(window)
                                pendingDrag = false
                            }
                            PointerEventType.Release -> {
                                println("[Nucleus-Compose] hitTest: UNCONSUMED Release -> reset")
                                pendingDrag = false
                            }
                        }
                    } else {
                        if (event.type == PointerEventType.Press) {
                            println("[Nucleus-Compose] hitTest: CONSUMED Press -> inUserControl=true (child handles)")
                            inUserControl = true
                            pendingDrag = false
                        }
                        if (event.type == PointerEventType.Release) {
                            println("[Nucleus-Compose] hitTest: Release -> inUserControl=false")
                            inUserControl = false
                        }
                    }
                }
            }
        }
    }

private fun startWindowDrag(window: java.awt.Window) {
    val ptr = JniMacWindowUtil.getWindowPtr(window)
    println("[Nucleus-Compose] startWindowDrag: ptr=$ptr isLoaded=${JniMacTitleBarBridge.isLoaded}")
    if (ptr != 0L && JniMacTitleBarBridge.isLoaded) {
        JniMacTitleBarBridge.nativeStartWindowDrag(ptr)
    }
}
