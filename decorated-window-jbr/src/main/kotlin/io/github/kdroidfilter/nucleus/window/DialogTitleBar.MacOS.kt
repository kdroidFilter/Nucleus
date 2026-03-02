package io.github.kdroidfilter.nucleus.window

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jetbrains.JBR
import io.github.kdroidfilter.nucleus.window.styling.LocalTitleBarStyle
import io.github.kdroidfilter.nucleus.window.styling.TitleBarStyle

@Suppress("FunctionNaming")
@Composable
internal fun DecoratedDialogScope.MacOSDialogTitleBar(
    modifier: Modifier = Modifier,
    gradientStartColor: Color = Color.Unspecified,
    style: TitleBarStyle = LocalTitleBarStyle.current,
    content: @Composable TitleBarScope.(DecoratedDialogState) -> Unit = {},
) {
    val titleBar = remember { JBR.getWindowDecorations().createCustomTitleBar() }

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    DialogTitleBarImpl(
        modifier = modifier.customTitleBarMouseEventHandler(titleBar),
        gradientStartColor = gradientStartColor,
        style = style,
        applyTitleBar = { height, _ ->
            titleBar.putProperty("controls.rtl", isRtl)
            titleBar.height = height.value
            JBR.getWindowDecorations().setCustomTitleBar(window, titleBar)
            PaddingValues(start = titleBar.leftInset.dp, end = titleBar.rightInset.dp)
        },
        content = content,
    )
}
