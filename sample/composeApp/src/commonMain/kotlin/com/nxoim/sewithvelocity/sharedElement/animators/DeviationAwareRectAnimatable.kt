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
import com.nxoim.sewithvelocity.sharedElement.minus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

class DeviationAwareRectAnimatable(
    private val coroutineScope: CoroutineScope,
) {
    constructor(
        coroutineScope: CoroutineScope,
        initialValue: Rect
    ) : this(coroutineScope) {
        this.initialValue = initialValue
        this.nonDeviatedValue = initialValue
    }

    var isRunning by mutableStateOf(false)
        private set
    var currentVelocity by mutableStateOf(Rect.Companion.Zero)
        private set
    var targetValue by mutableStateOf<Rect?>(null)
        private set
    var initialValue by mutableStateOf<Rect?>(null)
        private set
    private var nonDeviatedValue by mutableStateOf<Rect?>(null)
    private var deviation by mutableStateOf(Rect.Companion.Zero)
    val currentValue get() = nonDeviatedValue?.minus(deviation)

    private var animationJob: Job? = null
    private var animationSpec: AnimationSpec<Rect> by mutableStateOf(spring())

    fun animate(
        from: Rect,
        to: Rect,
        animationSpec: AnimationSpec<Rect> = spring(),
        initialVelocity: Rect? = currentVelocity
    ) {
        this.initialValue = from
        this.targetValue = to
        this.animationSpec = animationSpec
        currentVelocity = initialVelocity ?: currentVelocity
        nonDeviatedValue = from
        deviation = Rect.Companion.Zero

        if (animationJob?.isActive == true) return

        animationJob?.cancel()
        animationJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            isRunning = true
            var anim = createAnimation()
            var playTimeNanos = 0L
            var lastFrameNanos = withFrameNanos { it }
            var lastTarget = targetValue

            while (isActive) {
                withFrameNanos { frameTime ->
                    // handle changes mid animation here
                    // rather than restarting a job because restarting
                    // a job will cause a looot of lags
                    if (targetValue != lastTarget) {
                        anim = createAnimation()
                        playTimeNanos = 0
                        lastTarget = targetValue
                    }

                    val frameDelta = (frameTime - lastFrameNanos).coerceAtLeast(0)
                    playTimeNanos += (frameDelta / coroutineContext.durationScale).roundToLong()
                    lastFrameNanos = frameTime

                    if (targetValue == null) {
                        stop()
                        return@withFrameNanos
                    }

                    if (playTimeNanos >= anim.durationNanos && !anim.isInfinite) {
                        stop()
                    } else {
                        nonDeviatedValue = anim.getValueFromNanos(playTimeNanos)
                        currentVelocity = anim.getVelocityFromNanos(playTimeNanos)
                    }
                }
            }
        }
    }

    fun stop() {
        animationJob?.cancel()
        isRunning = false
        currentVelocity = Rect.Companion.Zero
        targetValue = null
    }

    fun snapTo(to: Rect) {
        stop()
        nonDeviatedValue = to
        deviation = Rect.Companion.Zero
    }

    private fun createAnimation() = TargetBasedAnimation(
        animationSpec = animationSpec,
        typeConverter = Rect.Companion.VectorConverter,
        initialValue = nonDeviatedValue!!,
        targetValue = targetValue!!,
        initialVelocity = currentVelocity
    )

    fun deviateOrSnapTo(to: Rect) {
        if (isRunning)
            deviation = targetValue!! - to
        else {
            nonDeviatedValue = to
            deviation = Rect.Companion.Zero
        }
    }
}