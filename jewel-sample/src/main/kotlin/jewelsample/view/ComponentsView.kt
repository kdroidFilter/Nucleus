@file:OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)

package jewelsample.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButton
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.typography

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComponentsView() {
    Column(
        modifier =
            Modifier
                .trackActivation()
                .fillMaxSize()
                .background(JewelTheme.globalColors.panelBackground)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Buttons
        Section("Buttons") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DefaultButton(onClick = {}) { Text("Default") }
                OutlinedButton(onClick = {}) { Text("Outlined") }
                DefaultButton(onClick = {}, enabled = false) { Text("Disabled") }
                OutlinedButton(onClick = {}, enabled = false) { Text("Disabled outlined") }
            }
        }

        Divider(Orientation.Horizontal, Modifier.fillMaxWidth())

        // Checkboxes
        Section("Checkboxes") {
            var checked1 by remember { mutableStateOf(false) }
            var checked2 by remember { mutableStateOf(true) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(checked = checked1, onCheckedChange = { checked1 = it })
                    Text("Unchecked by default")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(checked = checked2, onCheckedChange = { checked2 = it })
                    Text("Checked by default")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(checked = false, onCheckedChange = {}, enabled = false)
                    Text("Disabled")
                }
            }
        }

        Divider(Orientation.Horizontal, Modifier.fillMaxWidth())

        // Radio buttons
        Section("Radio buttons") {
            var selected by remember { mutableIntStateOf(0) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Option A", "Option B", "Option C").forEachIndexed { index, label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = selected == index, onClick = { selected = index })
                        Text(label)
                    }
                }
            }
        }

        Divider(Orientation.Horizontal, Modifier.fillMaxWidth())

        // TextField
        Section("Text fields") {
            val state1 = rememberTextFieldState("Edit me")
            val state2 = rememberTextFieldState("")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(state = state1, modifier = Modifier.width(280.dp))
                TextField(
                    state = state2,
                    modifier = Modifier.width(280.dp),
                    placeholder = { Text("Placeholder text...") },
                )
            }
        }

        Divider(Orientation.Horizontal, Modifier.fillMaxWidth())

        // Dropdown
        Section("Dropdown") {
            val items = listOf("Apple", "Banana", "Cherry", "Date", "Elderberry")
            var selectedIndex by remember { mutableIntStateOf(0) }
            Dropdown(
                menuContent = {
                    items.forEachIndexed { index, item ->
                        selectableItem(
                            selected = selectedIndex == index,
                            onClick = { selectedIndex = index },
                        ) {
                            Text(item)
                        }
                    }
                },
            ) {
                Text(items[selectedIndex])
            }
        }

        Divider(Orientation.Horizontal, Modifier.fillMaxWidth())

        // Slider
        Section("Slider") {
            var value by remember { mutableStateOf(0.5f) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Slider(value = value, onValueChange = { value = it }, modifier = Modifier.width(280.dp))
                Text("Value: ${"%.2f".format(value)}")
            }
        }

        Divider(Orientation.Horizontal, Modifier.fillMaxWidth())

        // Progress
        Section("Progress") {
            CircularProgressIndicator()
        }

        Divider(Orientation.Horizontal, Modifier.fillMaxWidth())

        // Tooltip
        Section("Tooltip") {
            Tooltip(tooltip = { Text("I'm a tooltip!") }) {
                OutlinedButton(onClick = {}) { Text("Hover me") }
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = JewelTheme.typography.h3TextStyle)
        content()
    }
}
