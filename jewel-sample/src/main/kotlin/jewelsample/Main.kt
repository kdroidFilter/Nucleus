package jewelsample

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.asComposeFontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.application
import io.github.kdroidfilter.nucleus.darkmodedetector.isSystemInDarkMode
import io.github.kdroidfilter.nucleus.window.DecoratedWindow
import io.github.kdroidfilter.nucleus.window.DecoratedWindowDefaults
import io.github.kdroidfilter.nucleus.window.NucleusDecoratedWindowTheme
import io.github.kdroidfilter.nucleus.window.styling.DecoratedWindowColors
import io.github.kdroidfilter.nucleus.window.styling.DecoratedWindowStyle
import io.github.kdroidfilter.nucleus.window.styling.TitleBarStyle
import jewelsample.view.TitleBarView
import jewelsample.viewmodel.MainViewModel
import jewelsample.viewmodel.MainViewModel.currentView
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToSvgPainter
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.intui.markdown.standalone.ProvideMarkdownStyling
import org.jetbrains.jewel.intui.markdown.standalone.styling.dark
import org.jetbrains.jewel.intui.markdown.standalone.styling.light
import org.jetbrains.jewel.intui.standalone.Inter
import org.jetbrains.jewel.intui.standalone.JetBrainsMono
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.createDefaultTextStyle
import org.jetbrains.jewel.intui.standalone.theme.createEditorTextStyle
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.ui.ComponentStyling
import io.github.kdroidfilter.nucleus.graalvm.GraalVmInitializer
import java.awt.GraphicsEnvironment
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import kotlin.math.roundToInt
import java.awt.Font as AwtFont

// ---------------------------------------------------------------------------
// Font setup: pre-load fonts from classpath bytes so that Font.createFont()
// uses a ByteArrayInputStream (which supports mark/reset) instead of the raw
// classpath resource stream (which may not support mark/reset in GraalVM
// native image, causing "Problem reading font data" IOException).
//
// We also call Font.asComposeFontFamily() to match the Compose FontFamily to
// the *actual* AWT family name, because non-JBR JVMs on Windows read the TTF
// "Full Name" field (e.g. "Inter Regular") instead of the "Preferred Family"
// field ("Inter"), which would create a mismatch between AWT and Skia.
// ---------------------------------------------------------------------------

private data class FontSetup(
    val interFontFamily: FontFamily,
    val jbMonoFontFamily: FontFamily,
    val textLineHeight: TextUnit,
    val editorLineHeight: TextUnit,
)

/** Load an AWT font from a classpath resource using an in-memory byte stream. */
private fun loadAwtFont(resource: String): AwtFont? =
    try {
        ResourceLoader.javaClass.classLoader
            .getResourceAsStream(resource)
            ?.readAllBytes()
            ?.let { AwtFont.createFont(AwtFont.TRUETYPE_FONT, it.inputStream()) }
    } catch (_: Throwable) {
        null
    }

/**
 * Convert this AWT font to the Compose [FontFamily] that correctly matches
 * its AWT family name. Falls back to [fallback] when resolution fails.
 */
@OptIn(ExperimentalTextApi::class)
private fun AwtFont.toComposeFontFamily(fallback: FontFamily): FontFamily =
    try {
        asComposeFontFamily().takeUnless { it == FontFamily.Default } ?: fallback
    } catch (_: Throwable) {
        fallback
    }

/**
 * Load and register all required font variants, compute line heights, and
 * return a [FontSetup] ready for use in [createDefaultTextStyle] /
 * [createEditorTextStyle].
 *
 * Jewel's default [lineHeight] parameters call [Font.createFont] from a raw
 * classpath resource stream, which fails on Windows GraalVM native image
 * because the stream does not support mark/reset. By computing line heights
 * here (from a ByteArrayInputStream) and passing them explicitly, we bypass
 * those crashing default parameter expressions entirely.
 */
private fun setupFonts(fontSize: Float = 13f): FontSetup {
    val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()

    // Inter
    val interRegular = loadAwtFont("fonts/inter/Inter-Regular.ttf")?.also { ge.registerFont(it) }
    for (res in listOf(
        "fonts/inter/Inter-Bold.ttf",
        "fonts/inter/Inter-Italic.ttf",
        "fonts/inter/Inter-SemiBold.ttf",
        "fonts/inter/Inter-Medium.ttf",
        "fonts/inter/Inter-Light.ttf",
    )) {
        loadAwtFont(res)?.let { ge.registerFont(it) }
    }

    // JetBrains Mono
    val jbMonoRegular = loadAwtFont("fonts/jetbrains-mono/JetBrainsMono-Regular.ttf")?.also { ge.registerFont(it) }
    for (res in listOf(
        "fonts/jetbrains-mono/JetBrainsMono-Bold.ttf",
        "fonts/jetbrains-mono/JetBrainsMono-Italic.ttf",
    )) {
        loadAwtFont(res)?.let { ge.registerFont(it) }
    }

    // Compute Inter line height (mirrors Jewel's computeInterLineHeightPx logic)
    val frc = FontRenderContext(AffineTransform(), false, false)
    val interLineHeightPx =
        interRegular?.deriveFont(fontSize)?.let { f ->
            val lm = f.getLineMetrics("Ag", frc)
            (lm.ascent + lm.descent + lm.leading).roundToInt()
        } ?: (fontSize * 1.3f).roundToInt()

    // Editor line height mirrors Jewel's computeJetBrainsMonoLineHeightPx * EditorLineHeightMultiplier
    val editorLineHeight = ((interLineHeightPx * 0.87f).roundToInt() * 1.2f).sp

    return FontSetup(
        interFontFamily = interRegular?.toComposeFontFamily(FontFamily.Inter) ?: FontFamily.Inter,
        jbMonoFontFamily = jbMonoRegular?.toComposeFontFamily(FontFamily.JetBrainsMono) ?: FontFamily.JetBrainsMono,
        textLineHeight = interLineHeightPx.sp,
        editorLineHeight = editorLineHeight,
    )
}

@ExperimentalLayoutApi
fun main() {
    GraalVmInitializer.initialize()

    JewelLogger.getInstance("StandaloneSample").info("Starting Jewel Standalone sample")

    val icon = svgResource("icons/jewel-logo.svg")

    // Pre-load fonts from bytes and compute line heights before entering the
    // composition. This avoids Font.createFont() being called from Jewel's
    // default parameter expressions (computeInterLineHeightPx /
    // computeJetBrainsMonoLineHeightPx) which use raw classpath streams that
    // lack mark/reset support in GraalVM native image on Windows.
    val fontSetup = setupFonts()

    application {
        val textStyle =
            JewelTheme.createDefaultTextStyle(
                fontFamily = fontSetup.interFontFamily,
                lineHeight = fontSetup.textLineHeight,
            )
        val editorStyle =
            JewelTheme.createEditorTextStyle(
                fontFamily = fontSetup.jbMonoFontFamily,
                lineHeight = fontSetup.editorLineHeight,
            )

        val systemIsDark = isSystemInDarkMode()
        val isDark = if (MainViewModel.theme == IntUiThemes.System) systemIsDark else MainViewModel.theme.isDark()

        val themeDefinition =
            if (isDark) {
                JewelTheme.darkThemeDefinition(defaultTextStyle = textStyle, editorTextStyle = editorStyle)
            } else {
                JewelTheme.lightThemeDefinition(defaultTextStyle = textStyle, editorTextStyle = editorStyle)
            }

        IntUiTheme(
            theme = themeDefinition,
            styling = ComponentStyling.default(),
            swingCompatMode = MainViewModel.swingCompat,
        ) {
            val jewelWindowStyle = jewelDecoratedWindowStyle(isDark)
            val jewelTitleBarStyle = jewelTitleBarStyle(isDark)

            NucleusDecoratedWindowTheme(
                isDark = isDark,
                windowStyle = jewelWindowStyle,
                titleBarStyle = jewelTitleBarStyle,
            ) {
                DecoratedWindow(
                    onCloseRequest = { exitApplication() },
                    title = "Jewel standalone sample",
                    icon = icon,
                    onKeyEvent = { keyEvent ->
                        processKeyShortcuts(keyEvent = keyEvent, onNavigateTo = MainViewModel::onNavigateTo)
                    },
                    content = {
                        TitleBarView()
                        // Pass explicit baseTextStyle/editorTextStyle to avoid MarkdownStyling.light/dark()
                        // calling createDefaultTextStyle() with no lineHeight, which triggers
                        // computeInterLineHeightPx() → Font.createFont() crash on Windows GraalVM.
                        @OptIn(ExperimentalJewelApi::class)
                        val markdownStyling =
                            remember(JewelTheme.instanceUuid, isDark) {
                                if (isDark) {
                                    MarkdownStyling.dark(
                                        baseTextStyle = textStyle,
                                        editorTextStyle = editorStyle,
                                    )
                                } else {
                                    MarkdownStyling.light(
                                        baseTextStyle = textStyle,
                                        editorTextStyle = editorStyle,
                                    )
                                }
                            }
                        @OptIn(ExperimentalJewelApi::class)
                        ProvideMarkdownStyling(markdownStyling = markdownStyling) { currentView.content() }
                    },
                )
            }
        }
    }
}

/*
   Alt + W -> Welcome
   Alt + M -> Markdown
   Alt + C -> Components
*/
private fun processKeyShortcuts(
    keyEvent: KeyEvent,
    onNavigateTo: (String) -> Unit,
): Boolean {
    if (!keyEvent.isAltPressed || keyEvent.type != KeyEventType.KeyDown) return false
    return when (keyEvent.key) {
        Key.W -> {
            onNavigateTo("Welcome")
            true
        }

        Key.M -> {
            onNavigateTo("Markdown")
            true
        }

        Key.C -> {
            onNavigateTo("Components")
            true
        }

        else -> false
    }
}

@Suppress("MagicNumber")
@Composable
private fun jewelDecoratedWindowStyle(isDark: Boolean): DecoratedWindowStyle {
    val borderColor = JewelTheme.globalColors.borders.normal
    return DecoratedWindowDefaults.run { if (isDark) darkWindowStyle() else lightWindowStyle() }.copy(
        colors =
            DecoratedWindowColors(
                border = borderColor,
                borderInactive = borderColor.copy(alpha = 0.5f),
            ),
    )
}

@Suppress("MagicNumber")
@Composable
private fun jewelTitleBarStyle(isDark: Boolean): TitleBarStyle {
    val background = JewelTheme.globalColors.panelBackground
    val contentColor = JewelTheme.contentColor
    val borderColor = JewelTheme.globalColors.borders.normal
    val defaults = DecoratedWindowDefaults.run { if (isDark) darkTitleBarStyle() else lightTitleBarStyle() }

    val hoverOverlay = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
    val pressOverlay = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f)
    val inactiveBackground =
        if (isDark) {
            background.darken(0.15f)
        } else {
            background.lighten(0.3f)
        }

    return defaults.copy(
        colors =
            defaults.colors.copy(
                background = background,
                inactiveBackground = inactiveBackground,
                content = contentColor,
                border = borderColor,
                fullscreenControlButtonsBackground = background,
                iconButtonHoveredBackground = hoverOverlay,
                iconButtonPressedBackground = pressOverlay,
            ),
    )
}

@Suppress("MagicNumber")
private fun Color.darken(fraction: Float): Color =
    Color(
        red = red * (1f - fraction),
        green = green * (1f - fraction),
        blue = blue * (1f - fraction),
        alpha = alpha,
    )

@Suppress("MagicNumber")
private fun Color.lighten(fraction: Float): Color =
    Color(
        red = red + (1f - red) * fraction,
        green = green + (1f - green) * fraction,
        blue = blue + (1f - blue) * fraction,
        alpha = alpha,
    )

@Suppress("SameParameterValue")
@OptIn(ExperimentalResourceApi::class)
private fun svgResource(resourcePath: String): Painter =
    checkNotNull(ResourceLoader.javaClass.classLoader.getResourceAsStream(resourcePath)) {
        "Could not load resource $resourcePath: it does not exist or can't be read."
    }.readAllBytes()
        .decodeToSvgPainter(Density(1f))

private object ResourceLoader
