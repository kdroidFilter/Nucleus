// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package jewelsample.showcase.views

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import jewelsample.showcase.ShowcaseIcons
import jewelsample.showcase.components.Banners
import jewelsample.showcase.components.Borders
import jewelsample.showcase.components.BrushesShowcase
import jewelsample.showcase.components.Buttons
import jewelsample.showcase.components.Checkboxes
import jewelsample.showcase.components.ChipsAndTrees
import jewelsample.showcase.components.ComboBoxes
import jewelsample.showcase.components.Icons
import jewelsample.showcase.components.Links
import jewelsample.showcase.components.Menus
import jewelsample.showcase.components.ProgressBar
import jewelsample.showcase.components.RadioButtons
import jewelsample.showcase.components.Scrollbars
import jewelsample.showcase.components.SegmentedControls
import jewelsample.showcase.components.Sliders
import jewelsample.showcase.components.SplitLayouts
import jewelsample.showcase.components.Tabs
import jewelsample.showcase.components.TextAreas
import jewelsample.showcase.components.TextFields
import jewelsample.showcase.components.Tooltips
import jewelsample.showcase.components.TypographyShowcase
import org.jetbrains.jewel.ui.component.SplitLayoutState
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility

class ComponentsViewModel(
    alwaysVisibleScrollbarVisibility: ScrollbarVisibility.AlwaysVisible,
    whenScrollingScrollbarVisibility: ScrollbarVisibility.WhenScrolling,
) {
    private var outerSplitState by mutableStateOf(SplitLayoutState(0.5f))
    private var verticalSplitState by mutableStateOf(SplitLayoutState(0.5f))
    private var innerSplitState by mutableStateOf(SplitLayoutState(0.5f))

    fun getViews(): SnapshotStateList<ViewInfo> = views

    private val views: SnapshotStateList<ViewInfo> =
        mutableStateListOf(
            ViewInfo(title = "Buttons", iconKey = ShowcaseIcons.Components.button, content = { Buttons() }),
            ViewInfo(
                title = "Radio Buttons",
                iconKey = ShowcaseIcons.Components.radioButton,
                content = { RadioButtons() },
            ),
            ViewInfo(title = "Checkboxes", iconKey = ShowcaseIcons.Components.checkbox, content = { Checkboxes() }),
            ViewInfo(title = "Combo Boxes", iconKey = ShowcaseIcons.Components.comboBox, content = { ComboBoxes() }),
            ViewInfo(title = "Menus", iconKey = ShowcaseIcons.Components.menu, content = { Menus() }),
            ViewInfo(title = "Chips and trees", iconKey = ShowcaseIcons.Components.tree, content = { ChipsAndTrees() }),
            ViewInfo(
                title = "Progressbar",
                iconKey = ShowcaseIcons.Components.progressBar,
                content = { ProgressBar() },
            ),
            ViewInfo(title = "Icons", iconKey = ShowcaseIcons.Components.toolbar, content = { Icons() }),
            ViewInfo(title = "Links", iconKey = ShowcaseIcons.Components.links, content = { Links() }),
            ViewInfo(title = "Borders", iconKey = ShowcaseIcons.Components.borders, content = { Borders() }),
            ViewInfo(
                title = "Segmented Controls",
                iconKey = ShowcaseIcons.Components.segmentedControls,
                content = { SegmentedControls() },
            ),
            ViewInfo(title = "Sliders", iconKey = ShowcaseIcons.Components.slider, content = { Sliders() }),
            ViewInfo(title = "Tabs", iconKey = ShowcaseIcons.Components.tabs, content = { Tabs() }),
            ViewInfo(title = "Tooltips", iconKey = ShowcaseIcons.Components.tooltip, content = { Tooltips() }),
            ViewInfo(title = "TextAreas", iconKey = ShowcaseIcons.Components.textArea, content = { TextAreas() }),
            ViewInfo(title = "TextFields", iconKey = ShowcaseIcons.Components.textField, content = { TextFields() }),
            ViewInfo(
                title = "Scrollbars",
                iconKey = ShowcaseIcons.Components.scrollbar,
                content = {
                    Scrollbars(
                        alwaysVisibleScrollbarVisibility = alwaysVisibleScrollbarVisibility,
                        whenScrollingScrollbarVisibility = whenScrollingScrollbarVisibility,
                    )
                },
            ),
            ViewInfo(
                title = "SplitLayout",
                iconKey = ShowcaseIcons.Components.splitlayout,
                content = {
                    SplitLayouts(
                        outerSplitState,
                        verticalSplitState,
                        innerSplitState,
                        onResetState = {
                            outerSplitState = SplitLayoutState(0.5f)
                            verticalSplitState = SplitLayoutState(0.5f)
                            innerSplitState = SplitLayoutState(0.5f)
                        },
                    )
                },
            ),
            ViewInfo(title = "Banners", iconKey = ShowcaseIcons.Components.banners, content = { Banners() }),
            ViewInfo(
                title = "Typography",
                iconKey = ShowcaseIcons.Components.typography,
                content = { TypographyShowcase() },
            ),
            ViewInfo(title = "Brushes", iconKey = ShowcaseIcons.Components.brush, content = { BrushesShowcase() }),
        )

    private var _currentView: ViewInfo by mutableStateOf(views.first())

    fun getCurrentView(): ViewInfo = _currentView

    fun setCurrentView(view: ViewInfo) {
        _currentView = view
    }
}
