package io.github.kdroidfilter.nucleus.window.utils.linux

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import io.github.kdroidfilter.nucleus.core.runtime.LinuxDesktopEnvironment
import io.github.kdroidfilter.nucleus.window.LocalIsDarkTheme

internal data class LinuxTitleBarIconSet(
    val close: Painter,
    val closeHover: Painter,
    val closePressed: Painter,
    val closeHoverFocused: Painter,
    val closePressedFocused: Painter,
    val closeInactive: Painter,
    val minimize: Painter,
    val minimizeHover: Painter,
    val minimizePressed: Painter,
    val minimizeInactive: Painter,
    val maximize: Painter,
    val maximizeHover: Painter,
    val maximizePressed: Painter,
    val maximizeInactive: Painter,
    val restore: Painter,
    val restoreHover: Painter,
    val restorePressed: Painter,
    val restoreInactive: Painter,
)

@Suppress("DEPRECATION")
@Composable
internal fun linuxTitleBarIcons(
    de: LinuxDesktopEnvironment = LinuxDesktopEnvironment.Current,
    isDark: Boolean = LocalIsDarkTheme.current,
): LinuxTitleBarIconSet {
    val prefix =
        when (de) {
            LinuxDesktopEnvironment.KDE -> "nucleus/window/icons/linux/kde"
            else -> "nucleus/window/icons/linux/gnome"
        }
    val isKde = de == LinuxDesktopEnvironment.KDE
    val suffix = if (isDark) "_dark" else ""

    // KDE close hover/pressed icons are the same in light and dark (colored circle + white X)
    val closeHover = painterResource("$prefix/closeHover${if (isKde) "" else suffix}.svg")
    val closePressed = painterResource("$prefix/closePressed${if (isKde) "" else suffix}.svg")

    val minimize = painterResource("$prefix/minimize$suffix.svg")
    val maximize = painterResource("$prefix/maximize$suffix.svg")
    val restore = painterResource("$prefix/restore$suffix.svg")

    return LinuxTitleBarIconSet(
        close = painterResource("$prefix/close$suffix.svg"),
        closeHover = closeHover,
        closePressed = closePressed,
        closeHoverFocused = if (isKde) painterResource("$prefix/closeHoverFocused.svg") else closeHover,
        closePressedFocused = if (isKde) painterResource("$prefix/closePressedFocused.svg") else closePressed,
        closeInactive = painterResource("$prefix/closeInactive$suffix.svg"),
        minimize = minimize,
        minimizeHover = painterResource("$prefix/minimizeHover$suffix.svg"),
        minimizePressed = painterResource("$prefix/minimizePressed$suffix.svg"),
        // KDE dark: inactive icons are identical to normal icons
        minimizeInactive = if (isKde && isDark) minimize else painterResource("$prefix/minimizeInactive$suffix.svg"),
        maximize = maximize,
        maximizeHover = painterResource("$prefix/maximizeHover$suffix.svg"),
        maximizePressed = painterResource("$prefix/maximizePressed$suffix.svg"),
        maximizeInactive = if (isKde && isDark) maximize else painterResource("$prefix/maximizeInactive$suffix.svg"),
        restore = restore,
        restoreHover = painterResource("$prefix/restoreHover$suffix.svg"),
        restorePressed = painterResource("$prefix/restorePressed$suffix.svg"),
        restoreInactive = if (isKde && isDark) restore else painterResource("$prefix/restoreInactive$suffix.svg"),
    )
}
