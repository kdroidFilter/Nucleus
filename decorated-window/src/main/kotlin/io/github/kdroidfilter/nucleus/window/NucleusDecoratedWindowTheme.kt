package io.github.kdroidfilter.nucleus.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.nucleus.window.styling.DecoratedWindowColors
import io.github.kdroidfilter.nucleus.window.styling.DecoratedWindowMetrics
import io.github.kdroidfilter.nucleus.window.styling.DecoratedWindowStyle
import io.github.kdroidfilter.nucleus.window.styling.LocalDecoratedWindowStyle
import io.github.kdroidfilter.nucleus.window.styling.LocalTitleBarStyle
import io.github.kdroidfilter.nucleus.window.styling.TitleBarColors
import io.github.kdroidfilter.nucleus.window.styling.TitleBarIcons
import io.github.kdroidfilter.nucleus.window.styling.TitleBarMetrics
import io.github.kdroidfilter.nucleus.window.styling.TitleBarStyle

val LocalIsDarkTheme = androidx.compose.runtime.staticCompositionLocalOf { true }

@Suppress("FunctionNaming")
@Composable
fun NucleusDecoratedWindowTheme(
    isDark: Boolean = true,
    windowStyle: DecoratedWindowStyle =
        if (isDark) {
            DecoratedWindowDefaults.darkWindowStyle()
        } else {
            DecoratedWindowDefaults.lightWindowStyle()
        },
    titleBarStyle: TitleBarStyle =
        if (isDark) {
            DecoratedWindowDefaults.darkTitleBarStyle()
        } else {
            DecoratedWindowDefaults.lightTitleBarStyle()
        },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalIsDarkTheme provides isDark,
        LocalDecoratedWindowStyle provides windowStyle,
        LocalTitleBarStyle provides titleBarStyle,
    ) {
        content()
    }
}

@Suppress("MagicNumber")
object DecoratedWindowDefaults {
    fun lightWindowStyle(): DecoratedWindowStyle =
        DecoratedWindowStyle(
            colors =
                DecoratedWindowColors(
                    border = Color(0xFFB0B0B0),
                    borderInactive = Color(0xFFD0D0D0),
                ),
            metrics = DecoratedWindowMetrics(borderWidth = 1.dp),
        )

    fun darkWindowStyle(): DecoratedWindowStyle =
        DecoratedWindowStyle(
            colors =
                DecoratedWindowColors(
                    border = Color(0xFF555555),
                    borderInactive = Color(0xFF3C3C3C),
                ),
            metrics = DecoratedWindowMetrics(borderWidth = 1.dp),
        )

    fun lightTitleBarStyle(): TitleBarStyle =
        TitleBarStyle(
            colors =
                TitleBarColors(
                    background = Color(0xFFF0F0F0),
                    inactiveBackground = Color(0xFFFAFAFA),
                    content = Color(0xFF1E1E1E),
                    border = Color(0xFFB0B0B0),
                    fullscreenControlButtonsBackground = Color(0xFFF0F0F0),
                    titlePaneButtonHoveredBackground = Color(0x1A000000),
                    titlePaneButtonPressedBackground = Color(0x33000000),
                    titlePaneCloseButtonHoveredBackground = Color(0xFFE81123),
                    titlePaneCloseButtonPressedBackground = Color(0xFFF1707A),
                ),
            metrics =
                TitleBarMetrics(
                    height = 40.dp,
                    titlePaneButtonSize = DpSize(40.dp, 40.dp),
                ),
            icons = TitleBarIcons(),
        )

    fun darkTitleBarStyle(): TitleBarStyle =
        TitleBarStyle(
            colors =
                TitleBarColors(
                    background = Color(0xFF2B2B2B),
                    inactiveBackground = Color(0xFF3C3C3C),
                    content = Color(0xFFE0E0E0),
                    border = Color(0xFF555555),
                    fullscreenControlButtonsBackground = Color(0xFF2B2B2B),
                    titlePaneButtonHoveredBackground = Color(0x1AFFFFFF),
                    titlePaneButtonPressedBackground = Color(0x33FFFFFF),
                    titlePaneCloseButtonHoveredBackground = Color(0xFFE81123),
                    titlePaneCloseButtonPressedBackground = Color(0xFFF1707A),
                ),
            metrics =
                TitleBarMetrics(
                    height = 40.dp,
                    titlePaneButtonSize = DpSize(40.dp, 40.dp),
                ),
            icons = TitleBarIcons(),
        )
}
