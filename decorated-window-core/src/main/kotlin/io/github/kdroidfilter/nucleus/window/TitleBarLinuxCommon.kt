package io.github.kdroidfilter.nucleus.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.kdroidfilter.nucleus.window.styling.TitleBarStyle

@Composable
fun createLinuxTitleBarStyle(style: TitleBarStyle): TitleBarStyle =
    remember(style) {
        style.copy(
            colors =
                style.colors.copy(
                    iconButtonHoveredBackground = Color.Transparent,
                    iconButtonPressedBackground = Color.Transparent,
                ),
        )
    }
