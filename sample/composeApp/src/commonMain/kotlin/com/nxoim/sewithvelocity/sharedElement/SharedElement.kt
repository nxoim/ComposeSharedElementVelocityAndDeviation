package com.nxoim.sewithvelocity.sharedElement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachReversed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class SharedElement(
    val key: Any,
    val scope: SharedTransitionScopeImpl
) {
    fun isAnimating(): Boolean = states.fastAny { it.boundsAnimation.isRunning } && foundMatch
    private val offsetVelocityTracker = VelocityTracker()
    private val sizeVelocityTracker = VelocityTracker()
    private var velocityMultiplier = DefaultVelocityMultiplier
    private var velocityTrackingJob: Job? = null

    private var _targetBounds: Rect? by mutableStateOf(null)

    /**
     * This should be only read only in the post-lookahead placement pass. It returns null when
     * there's no shared element/bounds becoming visible (i.e. when only exiting shared elements are
     * defined, which is an incorrect state).
     */
    val targetBounds: Rect?
        get() {
            _targetBounds =
                targetBoundsProvider?.run {
                    Rect(calculateLookaheadOffset(), nonNullLookaheadSize)
                }
            return _targetBounds
        }

    fun updateMatch() {
        val hasVisibleContent = hasVisibleContent()
        if (states.size > 1 && hasVisibleContent) {
            foundMatch = true
        } else if (scope.isTransitionActive) {
            // Unrecoverable state when the shared element/bound that is becoming visible
            // is removed.
            if (!hasVisibleContent) {
                foundMatch = false
            }
        } else {
            // Transition not active
            foundMatch = false
        }
        if (states.isNotEmpty()) {
            scope.observeReads(this, updateMatch, observingVisibilityChange)
        }
    }

    var foundMatch: Boolean by mutableStateOf(false)
        private set

    // Tracks current size, should be continuous
    var currentBounds: Rect? by mutableStateOf(null)

    internal var targetBoundsProvider: SharedElementInternalState? = null
        private set

    fun onLookaheadResult(state: SharedElementInternalState, lookaheadSize: Size, topLeft: Offset) {
        if (state.boundsAnimation.isTarget) {
            targetBoundsProvider = state

            // Only update bounds when offset is updated so as to not accidentally fire
            // up animations, only to interrupt them in the same frame later on.
            if (_targetBounds?.topLeft != topLeft || _targetBounds?.size != lookaheadSize) {
                val target = Rect(topLeft, lookaheadSize)
                _targetBounds = target
                states.fastForEach { it.boundsAnimation.animate(currentBounds!!, target) }
            }
        }
    }

    /**
     * Each state comes from a call site of sharedElement/sharedBounds of the same key. In most
     * cases there will be 1 (i.e. no match) or 2 (i.e. match found) states. In the interrupted
     * cases, there may be multiple scenes showing simultaneously, resulting in more than 2 shared
     * element states for the same key to be present. In those cases, we expect there to be only 1
     * state that is becoming visible, which we will use to derive target bounds. If none is
     * becoming visible, then we consider this an error case for the lack of target, and
     * consequently animate none of them.
     */
    val states = mutableStateListOf<SharedElementInternalState>()

    private fun hasVisibleContent(): Boolean = states.fastAny { it.boundsAnimation.isTarget }

    /**
     * This gets called to update the target bounds. The 3 scenarios where
     * [updateTargetBoundsProvider] is needed are: when a shared element is 1) added, 2) removed,
     * or 3) getting a target state change.
     *
     * This is always called from an effect. Assume all compositional changes have been made in this
     * call.
     */
    fun updateTargetBoundsProvider() {
        var targetProvider: SharedElementInternalState? = null
        states.fastForEachReversed {
            if (it.boundsAnimation.isTarget) {
                targetProvider = it
                return@fastForEachReversed
            }
        }

        if (targetProvider == this.targetBoundsProvider) return
        // Update provider
        this.targetBoundsProvider = targetProvider
        _targetBounds = null
    }

    fun onSharedTransitionFinished() {
        foundMatch = states.size > 1 && hasVisibleContent()
        _targetBounds = null
    }

    private val updateMatch: (SharedElement) -> Unit = { updateMatch() }

    private val observingVisibilityChange: () -> Unit = { hasVisibleContent() }

    fun addState(sharedElementState: SharedElementInternalState) {
        states.add(sharedElementState)
        scope.observeReads(this, updateMatch, observingVisibilityChange)
    }

    fun removeState(sharedElementState: SharedElementInternalState) {
        states.remove(sharedElementState)
        if (states.isEmpty()) {
            updateMatch()
            scope.clearObservation(scope = this)
        } else {
            scope.observeReads(scope = this, updateMatch, observingVisibilityChange)
        }
    }

    fun beginVelocityTracking(coroutineScope: CoroutineScope) {
        if (velocityTrackingJob != null) error("Attempting to begin velocity tracking when already tracking.")
        velocityTrackingJob = coroutineScope.launch {
            while (isActive) {
                withFrameMillis { frameMillis ->
                    currentBounds?.run {
                        offsetVelocityTracker.addPosition(frameMillis, currentBounds!!.topLeft)
                        sizeVelocityTracker.addPosition(frameMillis, currentBounds!!.size.run { Offset(width, height) })
                    }
                }
            }
        }
    }

    fun getVelocity() = Rect(
        offset = offsetVelocityTracker.calculateVelocity().run { Offset(x, y) },
        size = sizeVelocityTracker.calculateVelocity().run { Size(x, y) }
    ) * velocityMultiplier

    fun stopVelocityTracking() {
        velocityTrackingJob?.cancel()
        velocityTrackingJob = null
        offsetVelocityTracker.resetTracking()
        sizeVelocityTracker.resetTracking()
    }

    /**
     * @param to When null - will set to the default value
     */
    fun setInitialVelocityMultiplier(to: Float?) { velocityMultiplier = to ?: DefaultVelocityMultiplier}
}

private const val DefaultVelocityMultiplier = 1.3f