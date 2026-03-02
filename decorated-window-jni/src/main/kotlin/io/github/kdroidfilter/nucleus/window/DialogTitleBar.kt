package io.github.kdroidfilter.nucleus.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.kdroidfilter.nucleus.core.runtime.Platform
import io.github.kdroidfilter.nucleus.window.styling.LocalTitleBarStyle
import io.github.kdroidfilter.nucleus.window.styling.TitleBarStyle

@Suppress("FunctionNaming")
@Composable
fun DecoratedDialogScope.DialogTitleBar(
    modifier: Modifier = Modifier,
    gradientStartColor: Color = Color.Unspecified,
    style: TitleBarStyle = LocalTitleBarStyle.current,
    content: @Composable TitleBarScope.(DecoratedDialogState) -> Unit = {},
) {
    val titleBarInfo = LocalDialogTitleBarInfo.current
    CompositionLocalProvider(
        LocalTitleBarInfo provides TitleBarInfo(titleBarInfo.title, titleBarInfo.icon),
    ) {
        when (Platform.Current) {
            Platform.Linux -> LinuxDialogTitleBar(modifier, gradientStartColor, style, content)
            Platform.Windows -> WindowsDialogTitleBar(modifier, gradientStartColor, style, content)
            Platform.MacOS -> MacOSDialogTitleBar(modifier, gradientStartColor, style, content)
            Platform.Unknown ->
                error("DialogTitleBar is not supported on this platform(${System.getProperty("os.name")})")
        }
    }
}
