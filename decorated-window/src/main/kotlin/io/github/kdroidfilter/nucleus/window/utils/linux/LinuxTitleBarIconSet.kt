package io.github.kdroidfilter.nucleus.window.utils.linux

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import io.github.kdroidfilter.nucleus.window.LocalIsDarkTheme

internal data class LinuxTitleBarIconSet(
    val close: Painter,
    val closeInactive: Painter,
    val minimize: Painter,
    val minimizeInactive: Painter,
    val maximize: Painter,
    val maximizeInactive: Painter,
    val restore: Painter,
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
    val suffix = if (isDark) "_dark" else ""
    return LinuxTitleBarIconSet(
        close = painterResource("$prefix/close$suffix.svg"),
        closeInactive = painterResource("$prefix/closeInactive$suffix.svg"),
        minimize = painterResource("$prefix/minimize$suffix.svg"),
        minimizeInactive = painterResource("$prefix/minimizeInactive$suffix.svg"),
        maximize = painterResource("$prefix/maximize$suffix.svg"),
        maximizeInactive = painterResource("$prefix/maximizeInactive$suffix.svg"),
        restore = painterResource("$prefix/restore$suffix.svg"),
        restoreInactive = painterResource("$prefix/restoreInactive$suffix.svg"),
    )
}
