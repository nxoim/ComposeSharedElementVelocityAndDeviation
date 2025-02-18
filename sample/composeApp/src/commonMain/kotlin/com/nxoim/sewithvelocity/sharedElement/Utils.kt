package com.nxoim.sewithvelocity.sharedElement

import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext

internal val CoroutineContext.durationScale: Float
    get() {
        val scale = this[MotionDurationScale]?.scaleFactor ?: 1f
        checkPrecondition(scale >= 0f) { "negative scale factor" }
        return scale
    }

// Like Kotlin's check() but without the .toString() call
@Suppress("BanInlineOptIn") // same opt-in as using Kotlin's check()
@OptIn(ExperimentalContracts::class)
internal inline fun checkPrecondition(value: Boolean, lazyMessage: () -> String) {
    contract { returns() implies value }
    if (!value) {
        throw IllegalStateException(lazyMessage())
    }
}

internal infix operator fun Rect.times(other: Float) = Rect(
    left = this.left * other,
    top = this.top * other,
    right = this.right * other,
    bottom = this.bottom * other
)

internal infix operator fun Rect.plus(other: Rect) = Rect(
    left = this.left + other.left,
    top = this.top + other.top,
    right = this.right + other.right,
    bottom = this.bottom + other.bottom
)

internal infix operator fun Rect.minus(other: Rect) = Rect(
    left = this.left - other.left,
    top = this.top - other.top,
    right = this.right - other.right,
    bottom = this.bottom - other.bottom
)