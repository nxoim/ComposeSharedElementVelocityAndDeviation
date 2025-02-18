package com.nxoim.sewithvelocity.sharedElement


import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.toSize

internal class SharedElementInternalState(
    sharedElement: SharedElement,
    boundsAnimation: BoundsAnimation,
    placeHolderSize: SharedTransitionScope.PlaceHolderSize,
    renderOnlyWhenVisible: Boolean,
    overlayClip: SharedTransitionScope.OverlayClip,
    renderInOverlayDuringTransition: Boolean,
    userState: SharedTransitionScope.SharedContentState,
    zIndex: Float
) : LayerRenderer, RememberObserver {

    internal var firstFrameDrawn: Boolean = false
    override var zIndex: Float by mutableFloatStateOf(zIndex)

    var renderInOverlayDuringTransition: Boolean by mutableStateOf(renderInOverlayDuringTransition)
    var sharedElement: SharedElement by mutableStateOf(sharedElement)
    var boundsAnimation: BoundsAnimation by mutableStateOf(boundsAnimation)
    var placeHolderSize: SharedTransitionScope.PlaceHolderSize by mutableStateOf(placeHolderSize)
    var renderOnlyWhenVisible: Boolean by mutableStateOf(renderOnlyWhenVisible)
    var overlayClip: SharedTransitionScope.OverlayClip by mutableStateOf(overlayClip)
    var userState: SharedTransitionScope.SharedContentState by mutableStateOf(userState)

    internal var clipPathInOverlay: Path? = null

    override fun drawInOverlay(drawScope: DrawScope) {
        val layer = layer ?: return
        // It is important to check that the first frame is drawn. In some cases shared content may
        // be composed, but never measured, placed or drawn. In those cases, we will not have
        // valid content to draw, therefore we need to skip drawing in overlay.
        if (firstFrameDrawn && shouldRenderInOverlay) {
            with(drawScope) {
                requireNotNull(sharedElement.currentBounds) { "Error: current bounds not set yet." }
                val (x, y) = sharedElement.currentBounds?.topLeft!!
                clipPathInOverlay?.let { clipPath(it) { translate(x, y) { drawLayer(layer) } } }
                    ?: translate(x, y) { drawLayer(layer) }
            }
        }
    }

    val nonNullLookaheadSize: Size
        get() =
            requireNotNull(lookaheadCoords()) {
                "Error: lookahead coordinates is null for ${sharedElement.key}."
            }
                .size
                .toSize()

    var lookaheadCoords: () -> LayoutCoordinates? = { null }
    override var parentState: SharedElementInternalState? = null

    // This can only be accessed during placement
    fun calculateLookaheadOffset(): Offset {
        val c = requireNotNull(lookaheadCoords()) { "Error: lookahead coordinates is null." }
        return sharedElement.scope.lookaheadRoot.localPositionOf(c, Offset.Zero)
    }

    // Delegate the property to a mutable state, so that when layer is updated, the rendering
    // gets invalidated.
    var layer: GraphicsLayer? by mutableStateOf(null)

    private val shouldRenderBasedOnTarget: Boolean
        get() = sharedElement.targetBoundsProvider == this || !renderOnlyWhenVisible

    internal val shouldRenderInOverlay: Boolean
        get() =
            shouldRenderBasedOnTarget && sharedElement.foundMatch && renderInOverlayDuringTransition

    val shouldRenderInPlace: Boolean
        get() = !sharedElement.foundMatch || (!shouldRenderInOverlay && shouldRenderBasedOnTarget)

    override fun onRemembered() {
        sharedElement.scope.onStateAdded(this)
        sharedElement.updateTargetBoundsProvider()
    }

    override fun onForgotten() {
        sharedElement.scope.onStateRemoved(this)
        sharedElement.updateTargetBoundsProvider()
    }

    override fun onAbandoned() { }
}