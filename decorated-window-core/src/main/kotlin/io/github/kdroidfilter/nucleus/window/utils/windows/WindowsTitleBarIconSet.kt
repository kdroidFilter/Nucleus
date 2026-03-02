package io.github.kdroidfilter.nucleus.window.utils.windows

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import io.github.kdroidfilter.nucleus.window.LocalIsDarkTheme

internal data class WindowsTitleBarIconSet(
    val close: Painter,
    val closeHover: Painter,
    val closeInactive: Painter,
    val minimize: Painter,
    val minimizeInactive: Painter,
    val maximize: Painter,
    val maximizeInactive: Painter,
    val restore: Painter,
    val restoreInactive: Painter,
)

@Composable
internal fun windowsTitleBarIcons(isDark: Boolean = LocalIsDarkTheme.current): WindowsTitleBarIconSet {
    val prefix = "nucleus/window/icons/windows"
    val suffix = if (isDark) "_dark" else ""

    return WindowsTitleBarIconSet(
        close = painterResource("$prefix/close$suffix.svg"),
        closeHover = painterResource("$prefix/closeHover.svg"),
        closeInactive = painterResource("$prefix/closeInactive$suffix.svg"),
        minimize = painterResource("$prefix/minimize$suffix.svg"),
        minimizeInactive = painterResource("$prefix/minimizeInactive$suffix.svg"),
        maximize = painterResource("$prefix/maximize$suffix.svg"),
        maximizeInactive = painterResource("$prefix/maximizeInactive$suffix.svg"),
        restore = painterResource("$prefix/restore$suffix.svg"),
        restoreInactive = painterResource("$prefix/restoreInactive$suffix.svg"),
    )
}
