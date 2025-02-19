@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.nxoim.sewithvelocity.sharedElement

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.nxoim.sewithvelocity.sharedElement.animators.DeviationAwareRectAnimatable
import kotlinx.coroutines.CoroutineScope

internal class BoundsAnimation(
    val transitionScope: SharedTransitionScope,
    val coroutineScope: CoroutineScope,
    val initialVelocity: (() -> Rect?),
    boundsTransform: BoundsTransform,
    private val _target: () -> Boolean
) {
    var animationState: DeviationAwareRectAnimatable? = null
    var boundsTransform: BoundsTransform by mutableStateOf(boundsTransform)
    private var isAnimationPending: Boolean by mutableStateOf(false)
    val isRunning get() = animationState?.isRunning == true || isAnimationPending

    private var previousTarget = isTarget
    val isTarget: Boolean
        get() {
            return _target()
                .also {
                    if (it != previousTarget) {
                        isAnimationPending = true
                    }
                    previousTarget = it
                }
        }

    val value: Rect?
        get() = if (transitionScope.isTransitionActive)
            animationState?.currentValue
        else
            null
    // dont use animated value when transition is off
    // even if its not gonna be animated

    fun animate(currentBounds: Rect, targetBounds: Rect) {
        if (transitionScope.isTransitionActive) {
            (animationState ?: DeviationAwareRectAnimatable(coroutineScope, currentBounds))
                .apply {
                    animationState = this

                    deviateOrSnapTo(targetBounds)

                    if (isAnimationPending) {
                        animate(
                            from = currentBounds,
                            to = targetBounds,
                            animationSpec = boundsTransform.transform(currentValue!!, targetBounds),
                            initialVelocity = initialVelocity()
                        )

                        isAnimationPending = false
                    }
                }
        }
    }
}

// THIS is the contunuous implementation that will
// update the value directly rather than adding
// the deviation to the current value

//internal class BoundsAnimation(
//    val transitionScope: SharedTransitionScope,
//    val coroutineScope: CoroutineScope,
//    val initialVelocity: (() -> Rect?),
//    boundsTransform: BoundsTransform,
//    private val _target: (Unit) -> Boolean
//) {
//    var animationState: RectAnimationState? = null
//    var boundsTransform: BoundsTransform by mutableStateOf(boundsTransform)
//    private var isAnimationPending: Boolean by mutableStateOf(false)
//    val isRunning get() = animationState?.isRunning == true || isAnimationPending
//
//    private var previousTarget = isTarget
//    val isTarget: Boolean
//        get() {
//            return _target(Unit)
//                .also {
//                    if (it != previousTarget) {
//                        isAnimationPending = true
//                    }
//                    previousTarget = it
//                }
//        }
//
//    val value: Rect?
//        get() = if (transitionScope.isTransitionActive)
//            animationState?.currentValue
//        else
//            null // dont use animated value when transition is off
//
//    fun animate(currentBounds: Rect, targetBounds: Rect) {
//        (animationState ?: RectAnimationState(coroutineScope, currentBounds))
//            .apply {
//                animationState = this
//
//                if (transitionScope.isTransitionActive) {
//                    updateTarget(
//                        targetBounds,
//                        animationSpec = boundsTransform.transform(currentValue!!, targetBounds),
//                        initialVelocity = initialVelocity()
//                    )
//                    isAnimationPending = false
//                } else {
//                    snapTo(targetBounds)
//                }
//            }
//    }
//}



