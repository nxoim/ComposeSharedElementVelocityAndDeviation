package com.nxoim.sewithvelocity

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.Companion.FullLine
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import com.nxoim.sewithvelocity.sharedElement.SharedTransitionLayout
import com.nxoim.sewithvelocity.sharedElement.SharedTransitionScope
import kotlin.random.Random


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun App() {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val colors = remember {
        List(20) {
            Color(Random.nextInt(0xFFFFFF), Random.nextInt(0xFFFFFF), Random.nextInt(0xFFFFFF))
        }
    }
    var velocityMultiplier by remember { mutableStateOf(1.3f) }
    var stiffness by remember { mutableStateOf(350f) }
    var dampingRatio by remember { mutableStateOf(0.9f) }
    var visibilityThreshold by remember { mutableStateOf(1.5f) }
    var isGrid by remember { mutableStateOf(false) }
    var minGridItemSize by remember { mutableStateOf(94.dp) }

    @Composable
    fun SharedTransitionScope.ColoredBoxItem(index: Int, color: Color, inGrid: Boolean) {
        Box(
            modifier = Modifier
                .aspectRatio(1.5f)
                .fillMaxSize()
                .padding(8.dp)
                .sharedElementWithCallerManagedVisibility(
                    rememberSharedContentState(key = "box_$index"),
                    visible = selectedIndex != index && inGrid == isGrid,
                    boundsTransform = { _, _ ->
                        spring(
                            stiffness = stiffness,
                            dampingRatio = dampingRatio,
                            visibilityThreshold = Rect(
                                visibilityThreshold,
                                visibilityThreshold,
                                visibilityThreshold,
                                visibilityThreshold
                            )
                        )
                    }
                )
                .background(color, RoundedCornerShape(16.dp))
                .clickable { selectedIndex = index }
        )
    }

    // hoisting states outside of animated content
    val lazyListState = rememberLazyListState()
    val lazyStaggeredGridState = rememberLazyStaggeredGridState()

    // customized implementation of shared transition things
    SharedTransitionLayout {
        // AnimatedContent is a requirement for the animation
        // to start as it lets the initial content stay in composition,
        // which is necessary due to how state is managed in
        // shared transition scope, specifically "onStateRemoved"
        // function. Were this to be done using just an if
        // statement - the state would get removed before
        // the grid appeared, causing loss of data about the element.

        // ...and also because new elements will appear nicely on screen
        AnimatedContent(isGrid) {
            if (it) {
                LazyVerticalStaggeredGrid(
                    state = lazyStaggeredGridState,
                    columns = StaggeredGridCells.Adaptive(minGridItemSize),
                ) {
                    item(span = FullLine) {
                        AnimationSettingsContainer(
                            velocityMultiplier = velocityMultiplier,
                            onVelocityMultiplierChange = { velocityMultiplier = it },
                            stiffness = stiffness,
                            onStiffnessChange = { stiffness = it },
                            dampingRatio = dampingRatio,
                            onDampingRatioChange = { dampingRatio = it },
                            visibilityThreshold = visibilityThreshold,
                            onVisibilityThresholdChange = { visibilityThreshold = it },
                            minGridItemSize = minGridItemSize,
                            onMinGridItemSizeChange = { minGridItemSize = it },
                            onSwitchView = { isGrid = false }
                        )
                    }

                    itemsIndexed(colors) { index, color ->
                        ColoredBoxItem(index, color, true)
                    }
                }
            } else {
                LazyColumn(state = lazyListState) {
                    item {
                        AnimationSettingsContainer(
                            velocityMultiplier = velocityMultiplier,
                            onVelocityMultiplierChange = { velocityMultiplier = it },
                            stiffness = stiffness,
                            onStiffnessChange = { stiffness = it },
                            dampingRatio = dampingRatio,
                            onDampingRatioChange = { dampingRatio = it },
                            visibilityThreshold = visibilityThreshold,
                            onVisibilityThresholdChange = { visibilityThreshold = it },
                            minGridItemSize = minGridItemSize,
                            onMinGridItemSizeChange = { minGridItemSize = it },
                            onSwitchView = { isGrid = true },
                        )
                    }

                    itemsIndexed(colors) { index, color ->
                        ColoredBoxItem(index, color, false)
                    }
                }
            }
        }


        val scrimAlpha by animateFloatAsState(
            if (selectedIndex == null) 0f else 1f,
            spring(dampingRatio, stiffness)
        )

        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .graphicsLayer { alpha = scrimAlpha }
                    .fillMaxSize()
                    .background(Color.Black.copy(0.5f))
            )

            selectedIndex?.let { index ->
                var offset by remember { mutableStateOf(Offset.Zero) }
                val draggable2DState = rememberDraggable2DState { offset += it }

                Box(
                    modifier = Modifier
                        .offset { offset.round() }
                        .aspectRatio(1f)
                        .fillMaxSize()
                        .sharedElementWithCallerManagedVisibility(
                            rememberSharedContentState(key = "box_$index"),
                            visible = true,
                            initialVelocityMultiplierOverrider = { velocityMultiplier },
                            boundsTransform = { _, _ ->
                                spring(
                                    stiffness = stiffness,
                                    dampingRatio = dampingRatio,
                                    visibilityThreshold = Rect(
                                        visibilityThreshold,
                                        visibilityThreshold,
                                        visibilityThreshold,
                                        visibilityThreshold
                                    )
                                )
                            },
                        )
                        .background(colors[index], RoundedCornerShape(16.dp))
                        .clickable { selectedIndex = null }
                )

                Box(
                    Modifier
                        .fillMaxSize()
                        .draggable2D(
                            draggable2DState,
                            onDragStopped = { selectedIndex = null
                            }
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AnimationSettingsContainer(
    velocityMultiplier: Float,
    onVelocityMultiplierChange: (Float) -> Unit,
    stiffness: Float,
    onStiffnessChange: (Float) -> Unit,
    dampingRatio: Float,
    onDampingRatioChange: (Float) -> Unit,
    visibilityThreshold: Float,
    onVisibilityThresholdChange: (Float) -> Unit,
    minGridItemSize: Dp,
    onMinGridItemSizeChange: (Dp) -> Unit,
    onSwitchView: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text("Animation Settings", style = MaterialTheme.typography.titleMedium)

        LabeledSlider(
            label = "Velocity Multiplier: $velocityMultiplier",
            value = velocityMultiplier,
            onValueChange = onVelocityMultiplierChange,
            valueRange = 0f..3f,
            steps = 29
        )

        LabeledSlider(
            label = "Stiffness: ${stiffness.toInt()}",
            value = stiffness,
            onValueChange = onStiffnessChange,
            valueRange = 0f..4000f,
            steps = 78
        )

        LabeledSlider(
            label = "Damping Ratio: $dampingRatio",
            value = dampingRatio,
            onValueChange = onDampingRatioChange,
            valueRange = 0f..2f,
            steps = 38
        )

        LabeledSlider(
            label = "Visibility Threshold: $visibilityThreshold",
            value = visibilityThreshold,
            onValueChange = onVisibilityThresholdChange,
            valueRange = 0f..10f,
            steps = 99
        )

        LabeledSlider(
            label = "Minimum Grid Item Size: $minGridItemSize",
            value = minGridItemSize.value,
            onValueChange = { onMinGridItemSizeChange(it.dp) },
            valueRange = 0f ..300f,
            steps = 29
        )

        FilledTonalButton(
            onSwitchView,
            Modifier.fillMaxWidth()
        ) {
            Text("Switch View")
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 10
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}