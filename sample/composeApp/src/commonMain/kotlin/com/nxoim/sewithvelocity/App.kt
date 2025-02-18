package com.nxoim.sewithvelocity

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import com.nxoim.sewithvelocity.sharedElement.SharedTransitionLayout
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
    var applyGestureVelocity by remember { mutableStateOf(true) }

    var velocityMultiplier by remember { mutableStateOf(1.3f) }
    var stiffness by remember { mutableStateOf(350f) }
    var dampingRatio by remember { mutableStateOf(0.9f) }
    var visibilityThreshold by remember { mutableStateOf(1.5f) }

    // customized implementation of shared transition things
    SharedTransitionLayout {
        Column {
            Checkbox(
                checked = applyGestureVelocity,
                onCheckedChange = { applyGestureVelocity = it },
                modifier = Modifier.statusBarsPadding()
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    AnimationSettingsContainer(
                        velocityMultiplier = velocityMultiplier,
                        onVelocityMultiplierChange = { velocityMultiplier = it },
                        stiffness = stiffness,
                        onStiffnessChange = { stiffness = it },
                        dampingRatio = dampingRatio,
                        onDampingRatioChange = { dampingRatio = it },
                        visibilityThreshold = visibilityThreshold,
                        onVisibilityThresholdChange = { visibilityThreshold = it }
                    )
                }

                itemsIndexed(colors) { index, color ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1.5f)
                            .fillMaxSize()
                            .padding(8.dp)
                            .sharedElementWithCallerManagedVisibility(
                                rememberSharedContentState(key = "box_$index"),
                                visible = selectedIndex != index,
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
                var dragging by remember { mutableStateOf(false) }
                var velocity by remember { mutableStateOf<Velocity?>(null) }

                Box(
                    modifier = Modifier
                        .offset { offset.round() }
                        .aspectRatio(1f)
                        .fillMaxSize()
                        .sharedElementWithCallerManagedVisibility(
                            rememberSharedContentState(key = "box_$index"),
                            visible = true,
                            initialVelocityProvider = if (applyGestureVelocity) {
                                {
                                    // doing velocity?.let { { /* lambda */ } } caused this
                                    // not to apply for some reason
                                    ((velocity ?: Velocity.Zero) * velocityMultiplier)
                                        .let { Rect(it.x, it.y, it.x, it.y) }
                                }
                            } else null,
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
                            onDragStarted = { dragging = true },
                            onDragStopped = { newVelocity ->
                                velocity = newVelocity * velocityMultiplier
                                dragging = false
                                selectedIndex = null
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun AnimationSettingsContainer(
    velocityMultiplier: Float,
    onVelocityMultiplierChange: (Float) -> Unit,
    stiffness: Float,
    onStiffnessChange: (Float) -> Unit,
    dampingRatio: Float,
    onDampingRatioChange: (Float) -> Unit,
    visibilityThreshold: Float,
    onVisibilityThresholdChange: (Float) -> Unit
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
            valueRange = 0.0f..10f,
            steps = 99
        )
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