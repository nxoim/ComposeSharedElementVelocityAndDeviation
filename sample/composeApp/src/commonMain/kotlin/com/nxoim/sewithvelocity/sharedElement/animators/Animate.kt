package com.nxoim.sewithvelocity.sharedElement.animators

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

// unused but might be useful???
fun animateRect(
    scope: CoroutineScope,
    targetValueState: State<Rect>,
    animationSpec: AnimationSpec<Rect> = spring(),
    finishedListener: ((Rect) -> Unit)? = null
) = animateValue(
    scope,
    targetValueState,
    Rect.VectorConverter,
    animationSpec,
    Rect.VisibilityThreshold,
    finishedListener = finishedListener
)


public fun <T, V : AnimationVector> animateValue(
    scope: CoroutineScope,
    targetValueState: State<T>,
    typeConverter: TwoWayConverter<T, V>,
    animationSpec: AnimationSpec<T> = spring(),
    visibilityThreshold: T? = null,
    label: String = "ValueAnimation",
    finishedListener: ((T) -> Unit)? = null
): State<T> {
    val animatable = Animatable(
        targetValueState.value,
        typeConverter,
        visibilityThreshold,
        label
    )
    val animatedValueState = animatable.asState()
    val listener = finishedListener

    val channel = Channel<T>(Channel.CONFLATED)

    scope.launch {
        snapshotFlow { targetValueState.value }.collect { targetValue ->
            channel.trySend(targetValue)
        }
    }

    scope.launch {
        for (target in channel) {
            val newTarget = channel.tryReceive().getOrNull() ?: target

            if (newTarget != animatable.targetValue) {
                animatable.animateTo(
                    newTarget,
                    animationSpec.run {
                        if (
                            visibilityThreshold != null &&
                            this is SpringSpec &&
                            this.visibilityThreshold != visibilityThreshold
                        ) {
                            spring(dampingRatio, stiffness, visibilityThreshold)
                        } else {
                            this
                        }
                    }
                )
                listener?.invoke(animatable.value)
            }
        }
    }

    return animatedValueState
}