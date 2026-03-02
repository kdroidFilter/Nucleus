package io.github.kdroidfilter.nucleus.window

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import io.github.kdroidfilter.nucleus.window.styling.LocalTitleBarStyle
import io.github.kdroidfilter.nucleus.window.styling.TitleBarStyle

@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun DecoratedDialogScope.DialogTitleBarImpl(
    modifier: Modifier = Modifier,
    gradientStartColor: Color = Color.Unspecified,
    style: TitleBarStyle = LocalTitleBarStyle.current,
    applyTitleBar: (Dp, DecoratedWindowState) -> PaddingValues,
    onPlace: (() -> Unit)? = null,
    backgroundContent: @Composable () -> Unit = {},
    content: @Composable TitleBarScope.(DecoratedDialogState) -> Unit,
) {
    val dialogState = state
    GenericTitleBarImpl(
        window = window,
        state = dialogState.toDecoratedWindowState(),
        modifier = modifier,
        gradientStartColor = gradientStartColor,
        style = style,
        applyTitleBar = applyTitleBar,
        onPlace = onPlace,
        backgroundContent = backgroundContent,
    ) { _ ->
        content(dialogState)
    }
}
