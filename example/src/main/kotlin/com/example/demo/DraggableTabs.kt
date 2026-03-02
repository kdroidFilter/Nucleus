package com.example.demo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

private val dropSpring =
    spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

@Suppress("FunctionNaming", "CyclomaticComplexMethod", "LongMethod")
@Composable
internal fun DraggableTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val tabWidths = remember { mutableMapOf<String, Float>() }
    var dragKey by remember { mutableStateOf<String?>(null) }
    val dragOffset = remember { Animatable(0f) }
    var rawDragOffset by remember { mutableStateOf(0f) }

    Row(
        modifier = modifier.height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tabTitle ->
            key(tabTitle) {
                val isTabDragging = dragKey == tabTitle
                val isSelected = index == selectedIndex
                val hoverInteraction = remember { MutableInteractionSource() }
                val isHovered by hoverInteraction.collectIsHoveredAsState()

                // Lift + scale spring
                val elevation by animateDpAsState(
                    if (isTabDragging) 8.dp else 0.dp,
                    spring(stiffness = Spring.StiffnessMediumLow),
                )
                val scale by animateFloatAsState(
                    if (isTabDragging) 1.05f else 1f,
                    spring(stiffness = Spring.StiffnessMediumLow),
                )

                // Background color transition
                val bgColor by animateColorAsState(
                    when {
                        isTabDragging -> MaterialTheme.colorScheme.surfaceContainerHighest
                        isSelected -> MaterialTheme.colorScheme.surfaceContainerHigh
                        isHovered -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                        else -> Color.Transparent
                    },
                    spring(stiffness = Spring.StiffnessMediumLow),
                )

                // Text color transition
                val textColor by animateColorAsState(
                    when {
                        isSelected || isTabDragging -> MaterialTheme.colorScheme.onSurface
                        isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )

                // Selection indicator
                val indicatorAlpha by animateFloatAsState(
                    if (isSelected) 1f else 0f,
                    spring(stiffness = Spring.StiffnessMediumLow),
                )
                val indicatorColor = MaterialTheme.colorScheme.primary

                Box(
                    modifier =
                        Modifier
                            .zIndex(if (isTabDragging) 1f else 0f)
                            .graphicsLayer {
                                translationX = if (isTabDragging) dragOffset.value else 0f
                                scaleX = scale
                                scaleY = scale
                                shadowElevation = elevation.toPx()
                                shape = RoundedCornerShape(6.dp)
                                clip = true
                            }.onGloballyPositioned { tabWidths[tabTitle] = it.size.width.toFloat() }
                            .clip(RoundedCornerShape(6.dp))
                            .background(bgColor)
                            .hoverable(hoverInteraction)
                            .clickable { onSelect(index) }
                            .pointerInput(tabTitle) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragKey = tabTitle
                                        rawDragOffset = 0f
                                        scope.launch { dragOffset.snapTo(0f) }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        rawDragOffset += dragAmount.x
                                        scope.launch { dragOffset.snapTo(rawDragOffset) }

                                        val currentIndex = tabs.indexOf(tabTitle)
                                        if (currentIndex < 0) return@detectDragGestures
                                        val tabWidth = tabWidths[tabTitle] ?: return@detectDragGestures
                                        val threshold = tabWidth * 0.4f

                                        if (rawDragOffset > threshold && currentIndex < tabs.lastIndex) {
                                            onReorder(currentIndex, currentIndex + 1)
                                            rawDragOffset -= tabWidth
                                            scope.launch { dragOffset.snapTo(rawDragOffset) }
                                        } else if (rawDragOffset < -threshold && currentIndex > 0) {
                                            onReorder(currentIndex, currentIndex - 1)
                                            rawDragOffset += tabWidth
                                            scope.launch { dragOffset.snapTo(rawDragOffset) }
                                        }
                                    },
                                    onDragEnd = {
                                        scope.launch {
                                            dragOffset.animateTo(0f, dropSpring)
                                            dragKey = null
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch {
                                            dragOffset.animateTo(0f, dropSpring)
                                            dragKey = null
                                        }
                                    },
                                )
                            }.drawBehind {
                                if (indicatorAlpha > 0f) {
                                    val h = 2.dp.toPx()
                                    drawRoundRect(
                                        color = indicatorColor.copy(alpha = indicatorAlpha),
                                        topLeft = Offset(4.dp.toPx(), size.height - h),
                                        size = Size(size.width - 8.dp.toPx(), h),
                                        cornerRadius = CornerRadius(h / 2),
                                    )
                                }
                            }.padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        tabTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (index < tabs.lastIndex) {
                    Spacer(modifier = Modifier.width(2.dp))
                }
            }
        }
    }
}
