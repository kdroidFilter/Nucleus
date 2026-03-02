// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package jewelsample.showcase

import org.jetbrains.jewel.ui.icon.PathIconKey

object ShowcaseIcons {
    val componentsMenu: PathIconKey = PathIconKey("icons/structure.svg", ShowcaseIcons::class.java)
    val gitHub: PathIconKey = PathIconKey("icons/github.svg", ShowcaseIcons::class.java)
    val jewelLogo: PathIconKey = PathIconKey("icons/jewel-logo.svg", ShowcaseIcons::class.java)
    val markdown: PathIconKey = PathIconKey("icons/markdown.svg", ShowcaseIcons::class.java)
    val themeDark: PathIconKey = PathIconKey("icons/darkTheme.svg", ShowcaseIcons::class.java)
    val themeLight: PathIconKey = PathIconKey("icons/lightTheme.svg", ShowcaseIcons::class.java)
    val themeLightWithLightHeader: PathIconKey =
        PathIconKey("icons/lightWithLightHeaderTheme.svg", ShowcaseIcons::class.java)
    val themeSystem: PathIconKey = PathIconKey("icons/systemTheme.svg", ShowcaseIcons::class.java)
    val welcome: PathIconKey = PathIconKey("icons/meetNewUi.svg", ShowcaseIcons::class.java)
    val sunny: PathIconKey = PathIconKey("icons/sunny.svg", ShowcaseIcons::class.java)

    object Components {
        val banners: PathIconKey = PathIconKey("icons/components/banners.svg", ShowcaseIcons::class.java)
        val borders: PathIconKey = PathIconKey("icons/components/borders.svg", ShowcaseIcons::class.java)
        val brush: PathIconKey = PathIconKey("icons/components/brush.svg", ShowcaseIcons::class.java)
        val button: PathIconKey = PathIconKey("icons/components/button.svg", ShowcaseIcons::class.java)
        val checkbox: PathIconKey = PathIconKey("icons/components/checkBox.svg", ShowcaseIcons::class.java)
        val comboBox: PathIconKey = PathIconKey("icons/components/comboBox.svg", ShowcaseIcons::class.java)
        val links: PathIconKey = PathIconKey("icons/components/links.svg", ShowcaseIcons::class.java)
        val menu: PathIconKey = PathIconKey("icons/components/menu.svg", ShowcaseIcons::class.java)
        val progressBar: PathIconKey = PathIconKey("icons/components/progressbar.svg", ShowcaseIcons::class.java)
        val radioButton: PathIconKey = PathIconKey("icons/components/radioButton.svg", ShowcaseIcons::class.java)
        val scrollbar: PathIconKey = PathIconKey("icons/components/scrollbar.svg", ShowcaseIcons::class.java)
        val segmentedControls: PathIconKey =
            PathIconKey("icons/components/segmentedControl.svg", ShowcaseIcons::class.java)
        val slider: PathIconKey = PathIconKey("icons/components/slider.svg", ShowcaseIcons::class.java)
        val splitlayout: PathIconKey = PathIconKey("icons/components/splitLayout.svg", ShowcaseIcons::class.java)
        val tabs: PathIconKey = PathIconKey("icons/components/tabs.svg", ShowcaseIcons::class.java)
        val textArea: PathIconKey = PathIconKey("icons/components/textArea.svg", ShowcaseIcons::class.java)
        val textField: PathIconKey = PathIconKey("icons/components/textField.svg", ShowcaseIcons::class.java)
        val toolbar: PathIconKey = PathIconKey("icons/components/toolbar.svg", ShowcaseIcons::class.java)
        val tooltip: PathIconKey = PathIconKey("icons/components/tooltip.svg", ShowcaseIcons::class.java)
        val tree: PathIconKey = PathIconKey("icons/components/tree.svg", ShowcaseIcons::class.java)
        val typography: PathIconKey = PathIconKey("icons/components/typography.svg", ShowcaseIcons::class.java)
    }

    object ProgrammingLanguages {
        val Kotlin: PathIconKey = PathIconKey("icons/kotlin.svg", ShowcaseIcons::class.java)
    }
}
