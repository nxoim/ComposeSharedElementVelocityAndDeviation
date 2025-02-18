package com.nxoim.sewithvelocity.sharedElement.animators

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.getVelocityFromNanos
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import com.nxoim.sewithvelocity.sharedElement.durationScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToLong


// probably buggy, but i likely used it improperly
class RectAnimatable(
    private val coroutineScope: CoroutineScope,
) {
    constructor(
        coroutineScope: CoroutineScope,
        initialValue: Rect
    ) : this(coroutineScope) {
        this.currentValue = initialValue
        this.initialValue = initialValue
    }

    var isRunning by mutableStateOf(false)
        private set
    var currentValue by mutableStateOf<Rect?>(null)
        private set
    var currentVelocity by mutableStateOf(Rect.Companion.Zero)
        private set
    var targetValue by mutableStateOf<Rect?>(null)
        private set
    var initialValue by mutableStateOf<Rect?>(null)
        private set

    private var animationJob: Job? = null
    private var animationSpec: AnimationSpec<Rect> by mutableStateOf(spring())

    fun start(
        from: Rect,
        to: Rect,
        animationSpec: AnimationSpec<Rect> = spring(),
        initialVelocity: Rect? = currentVelocity
    ) {
        this.initialValue = from
        this.targetValue = to
        this.animationSpec = animationSpec
        currentVelocity = initialVelocity ?: currentVelocity
        currentValue = from

        if (animationJob?.isActive == true) return

        animationJob?.cancel()
        animationJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            isRunning = true
            var anim = createAnimation()
            var playTimeNanos = 0L
            // remembering the last target so it can be
            // compared against and switched mid animation
            var lastInitial: Rect? = from
            var lastTarget: Rect? = to
            // remembering the frame nanos that the
            // animation starts with
            var lastFrameNanos = withFrameNanos { it }

            while (isActive) {
                withFrameNanos {
                    // reset
                    if (targetValue != lastTarget || lastInitial != initialValue) {
                        anim = createAnimation()
                        playTimeNanos = 0
                        lastTarget = targetValue
                        lastInitial = initialValue
                    }

                    val frameDelta = (it - lastFrameNanos).coerceAtLeast(0)
                    playTimeNanos += (frameDelta / coroutineContext.durationScale).roundToLong()
                    lastFrameNanos = it

                    if (targetValue == null) {
                        stop()
                        return@withFrameNanos
                    }

                    if (playTimeNanos >= anim.durationNanos && !anim.isInfinite) {
                        stop()
                    } else {
                        currentValue = anim.getValueFromNanos(playTimeNanos)
                        currentVelocity = anim.getVelocityFromNanos(playTimeNanos)
                    }
                }
            }
        }
    }

    fun updateTarget(
        to: Rect,
        from: Rect = currentValue ?: error("Attempting updating target with no initial target specified"),
        animationSpec: AnimationSpec<Rect> = this.animationSpec,
        initialVelocity: Rect? = currentVelocity
    ) {
        initialValue = from
        targetValue = to
        this.animationSpec = animationSpec

        // restart
        if (currentValue != to && animationJob?.isActive != true) {
            start(from, to, animationSpec, initialVelocity ?: currentVelocity)
        }
    }

    private fun createAnimation() = TargetBasedAnimation(
        animationSpec = animationSpec,
        typeConverter = Rect.Companion.VectorConverter,
        initialValue = currentValue!!,
        targetValue = targetValue!!,
        initialVelocity = currentVelocity
    )

    fun stop() {
        animationJob?.cancel()
        isRunning = false
        currentVelocity = Rect.Companion.Zero
        targetValue = null
    }

    fun snapTo(to: Rect) {
        stop()
        currentValue = to
        currentVelocity = Rect.Companion.Zero
    }
}
