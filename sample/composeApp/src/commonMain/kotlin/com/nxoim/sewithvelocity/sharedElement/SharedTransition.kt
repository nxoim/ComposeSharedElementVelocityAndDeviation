@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.nxoim.sewithvelocity.sharedElement

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.ExperimentalTransitionApi
import androidx.compose.animation.core.Spring.StiffnessMediumLow
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.BottomEnd
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterEnd
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Alignment.Companion.TopCenter
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.Alignment.Companion.TopStart
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastForEach
import com.nxoim.sewithvelocity.sharedElement.SharedTransitionScope.PlaceHolderSize.Companion.contentSize
import com.nxoim.sewithvelocity.sharedElement.SharedTransitionScope.ResizeMode.Companion.ScaleToBounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * [SharedTransitionLayout] creates a layout and a [SharedTransitionScope] for the child layouts in
 * [content]. Any child (direct or indirect) of the [SharedTransitionLayout] can use the receiver
 * scope [SharedTransitionScope] to create shared element or shared bounds transitions.
 *
 * **Note**: [SharedTransitionLayout] creates a new Layout. For use cases where it's preferable to
 * not introduce a new layout between [content] and the parent layout, consider using
 * [SharedTransitionScope] instead.
 *
 * @param modifier Modifiers to be applied to the layout.
 * @param content The children composable to be laid out.
 */

@Composable
public fun SharedTransitionLayout(
    modifier: Modifier = Modifier,
    content: @Composable SharedTransitionScope.() -> Unit
) {
    SharedTransitionScope { sharedTransitionModifier ->
        // Put shared transition modifier *after* user provided modifier to support user provided
        // modifiers to influence the overlay's size, position, clipping, etc.
        Box(modifier.then(sharedTransitionModifier)) { content() }
    }
}

/**
 * [SharedTransitionScope] creates a [SharedTransitionScope] for the child layouts in [content]. Any
 * child (direct or indirect) of the [SharedTransitionLayout] can use the receiver scope
 * [SharedTransitionScope] to create shared element or shared bounds transitions.
 * [SharedTransitionScope] will not creates a new Layout.
 *
 * **IMPORTANT**: It is important to set the [Modifier] provided to the [content] on the first and
 * top-most child, as the [Modifier] both obtains the root coordinates and creates an overlay. If
 * the first child layout in [content] isn't the child with the highest zIndex, consider using
 * [SharedTransitionLayout] instead.
 *
 * @param content The children composable to be laid out.
 */

@Composable
public fun SharedTransitionScope(content: @Composable SharedTransitionScope.(Modifier) -> Unit) {
    LookaheadScope {
        val coroutineScope = rememberCoroutineScope()
        val sharedScope = remember { SharedTransitionScopeImpl(this, coroutineScope) }
        sharedScope.content(
            Modifier.layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                layout(p.width, p.height) {
                    val coords = coordinates
                    if (coords != null) {
                        if (!isLookingAhead) {
                            sharedScope.root = coords
                        } else {
                            sharedScope.nullableLookaheadRoot = coords
                        }
                    }
                    p.place(0, 0)
                }
            }
                .drawWithContent {
                    drawContent()
                    sharedScope.drawInOverlay(this)
                }
        )
        DisposableEffect(Unit) { onDispose { sharedScope.onDispose() } }
    }
}

@Suppress("UNCHECKED_CAST")
@Immutable
internal class SharedTransitionScopeImpl internal constructor(
    lookaheadScope: LookaheadScope,
    val coroutineScope: CoroutineScope
) : SharedTransitionScope, LookaheadScope by lookaheadScope {
    companion object {
        private val SharedTransitionObserver by lazy(LazyThreadSafetyMode.NONE) {
            SnapshotStateObserver { it() }
                .also { it.start() }
        }
    }

    internal var disposed: Boolean = false
        private set

    override var isTransitionActive: Boolean by mutableStateOf(false)
        private set

    override fun Modifier.skipToLookaheadSize(): Modifier = this.then(SkipToLookaheadElement())

    override fun Modifier.renderInSharedTransitionScopeOverlay(
        renderInOverlay: () -> Boolean,
        zIndexInOverlay: Float,
        clipInOverlayDuringTransition: (LayoutDirection, Density) -> Path?
    ): Modifier =
        this.then(
            RenderInTransitionOverlayNodeElement(
                this@SharedTransitionScopeImpl,
                renderInOverlay,
                zIndexInOverlay,
                clipInOverlayDuringTransition
            )
        )

    override fun Modifier.sharedElementWithCallerManagedVisibility(
        sharedContentState: SharedTransitionScope.SharedContentState,
        visible: Boolean,
        boundsTransform: BoundsTransform,
        placeHolderSize: SharedTransitionScope.PlaceHolderSize,
        renderInOverlayDuringTransition: Boolean,
        zIndexInOverlay: Float,
        clipInOverlayDuringTransition: SharedTransitionScope.OverlayClip,
        initialVelocityProvider: (() -> Rect)?,
        acceptIncomingInitialVelocity: Boolean
    ) = this.sharedElementImpl<Unit>(
        sharedContentState = sharedContentState,
        visible = { visible },
        boundsTransform = boundsTransform,
        placeHolderSize = placeHolderSize,
        renderOnlyWhenVisible = true,
        renderInOverlayDuringTransition = renderInOverlayDuringTransition,
        zIndexInOverlay = zIndexInOverlay,
        clipInOverlayDuringTransition = clipInOverlayDuringTransition,
        initialVelocityProvider = initialVelocityProvider,
        acceptIncomingInitialVelocity = acceptIncomingInitialVelocity
    )

    override fun OverlayClip(clipShape: Shape): SharedTransitionScope.OverlayClip = ShapeBasedClip(clipShape)

    @Composable
    override fun rememberSharedContentState(key: Any): SharedTransitionScope.SharedContentState =
        remember(key) { SharedTransitionScope.SharedContentState(key) }

    /** ******** Impl details below **************** */
    private val observeAnimatingBlock: () -> Unit = {
        sharedElements.values.any { it.isAnimating() }
    }

    private val updateTransitionActiveness: (SharedTransitionScope) -> Unit = {
        updateTransitionActiveness()
    }

    private fun updateTransitionActiveness() {
        val isActive = sharedElements.values.any(SharedElement::isAnimating)
        if (isActive != isTransitionActive) {
            isTransitionActive = isActive
            if (!isActive) {
                sharedElements.values.forEach(SharedElement::onSharedTransitionFinished)
            }
        }
        sharedElements.values.forEach(SharedElement::updateMatch)
        this@SharedTransitionScopeImpl.observeIsAnimating()
    }

    @OptIn(ExperimentalTransitionApi::class, ExperimentalSharedTransitionApi::class)
    private fun <T> Modifier.sharedElementImpl(
        sharedContentState: SharedTransitionScope.SharedContentState,
        visible: (T) -> Boolean,
        boundsTransform: BoundsTransform,
        placeHolderSize: SharedTransitionScope.PlaceHolderSize = contentSize,
        renderOnlyWhenVisible: Boolean,
        renderInOverlayDuringTransition: Boolean,
        zIndexInOverlay: Float,
        clipInOverlayDuringTransition: SharedTransitionScope.OverlayClip,
        initialVelocityProvider: (() -> Rect)?,
        acceptIncomingInitialVelocity: Boolean
    ) = composed {
        val key = sharedContentState.key
        val sharedElement = remember { sharedElementsFor(key) }
            .apply {
                // TODO this is nor reliable as there could be more than
                //  2 shared element hosts and the newest recomposed one
                //  will cause this value to be set. may have to move to SharedElementState
                initialVelocityProvider?.let { lastReportedInitialVelocity = it }
            }

        // otherwise some updates wouldnt pass, idk why tbh
        val visibleRemembered by rememberUpdatedState(visible as (Unit) -> Boolean)

        val boundsAnimation = remember {
            BoundsAnimation(
                transitionScope = this@SharedTransitionScopeImpl,
                coroutineScope = coroutineScope,
                initialVelocity = {
                    if (acceptIncomingInitialVelocity)
                        sharedElement.lastReportedInitialVelocity?.invoke()
                    else
                        null
                },
                boundsTransform = boundsTransform,
                _target = { visibleRemembered.invoke(Unit) }
            )
        }.apply {
            this.boundsTransform = boundsTransform
        }

        val sharedElementState = rememberSharedElementState(
            sharedElement = sharedElement,
            boundsAnimation = boundsAnimation,
            placeHolderSize = placeHolderSize,
            renderOnlyWhenVisible = renderOnlyWhenVisible,
            sharedContentState = sharedContentState,
            clipInOverlayDuringTransition = clipInOverlayDuringTransition,
            zIndexInOverlay = zIndexInOverlay,
            renderInOverlayDuringTransition = renderInOverlayDuringTransition
        )

        this then SharedBoundsNodeElement(sharedElementState)
    }


    @Composable
    private fun rememberSharedElementState(
        sharedElement: SharedElement,
        boundsAnimation: BoundsAnimation,
        placeHolderSize: SharedTransitionScope.PlaceHolderSize,
        renderOnlyWhenVisible: Boolean,
        sharedContentState: SharedTransitionScope.SharedContentState,
        clipInOverlayDuringTransition: SharedTransitionScope.OverlayClip,
        zIndexInOverlay: Float,
        renderInOverlayDuringTransition: Boolean
    ): SharedElementInternalState =
        remember {
            SharedElementInternalState(
                sharedElement,
                boundsAnimation,
                placeHolderSize,
                renderOnlyWhenVisible = renderOnlyWhenVisible,
                userState = sharedContentState,
                overlayClip = clipInOverlayDuringTransition,
                zIndex = zIndexInOverlay,
                renderInOverlayDuringTransition = renderInOverlayDuringTransition
            )
        }
            .also {
                sharedContentState.internalState = it
                // Update the properties if any of them changes
                it.sharedElement = sharedElement
                it.renderOnlyWhenVisible = renderOnlyWhenVisible
                it.boundsAnimation = boundsAnimation
                it.placeHolderSize = placeHolderSize
                it.overlayClip = clipInOverlayDuringTransition
                it.zIndex = zIndexInOverlay
                it.renderInOverlayDuringTransition = renderInOverlayDuringTransition
                it.userState = sharedContentState
            }

    internal lateinit var root: LayoutCoordinates
    internal val lookaheadRoot: LayoutCoordinates
        get() =
            requireNotNull(nullableLookaheadRoot) {
                "Error: Uninitialized LayoutCoordinates." +
                        " Please make sure when using the SharedTransitionScope composable function," +
                        " the modifier passed to the child content is being used, or use" +
                        " SharedTransitionLayout instead."
            }

    internal var nullableLookaheadRoot: LayoutCoordinates? = null

    // TODO: Use MutableObjectList and impl sort
    private val renderers = mutableListOf<LayerRenderer>()

    private val sharedElements = mutableMapOf<Any, SharedElement>()

    @OptIn(ExperimentalSharedTransitionApi::class)
    private fun sharedElementsFor(key: Any): SharedElement {
        return sharedElements.getOrPut(key) { SharedElement(key, this) }
    }

    @Stable
    internal fun drawInOverlay(scope: ContentDrawScope) {
        // TODO: Sort while preserving the parent child order
        renderers.sortBy {
            if (it.zIndex == 0f && it is SharedElementInternalState && it.parentState == null) {
                -1f
            } else it.zIndex
        }
        renderers.fastForEach { it.drawInOverlay(drawScope = scope) }
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    internal fun onStateRemoved(sharedElementState: SharedElementInternalState) {
        with(sharedElementState.sharedElement) {
            removeState(sharedElementState)
            updateTransitionActiveness.invoke(this@SharedTransitionScopeImpl)
            scope.observeIsAnimating()
            renderers.remove(sharedElementState)
            if (states.isEmpty()) {
                scope.coroutineScope.launch {
                    if (states.isEmpty()) {
                        scope.sharedElements.remove(key)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    internal fun onStateAdded(sharedElementState: SharedElementInternalState) {
        with(sharedElementState.sharedElement) {
            addState(sharedElementState)
            updateTransitionActiveness.invoke(this@SharedTransitionScopeImpl)
            scope.observeIsAnimating()
            val id =
                renderers.indexOfFirst {
                    (it as? SharedElementInternalState)?.sharedElement ==
                            sharedElementState.sharedElement
                }
            if (id == renderers.size - 1 || id == -1) {
                renderers.add(sharedElementState)
            } else {
                renderers.add(id + 1, sharedElementState)
            }
        }
    }

    internal fun onLayerRendererCreated(renderer: LayerRenderer) {
        renderers.add(renderer)
    }

    internal fun onLayerRendererRemoved(renderer: LayerRenderer) {
        renderers.remove(renderer)
    }

    internal fun onDispose() {
        SharedTransitionObserver.clear(this)
        disposed = true
    }

    // TestOnly
    internal val observerForTest: SnapshotStateObserver
        get() = SharedTransitionObserver

    private fun observeIsAnimating() {
        if (!disposed) {
            SharedTransitionObserver.observeReads(
                this,
                updateTransitionActiveness,
                observeAnimatingBlock
            )
        }
    }

    internal fun observeReads(
        scope: SharedElement,
        onValueChangedForScope: (SharedElement) -> Unit,
        block: () -> Unit
    ) {
        if (!disposed) {
            SharedTransitionObserver.observeReads(scope, onValueChangedForScope, block)
        }
    }

    internal fun clearObservation(scope: Any) {
        SharedTransitionObserver.clear(scope)
    }

    private class ShapeBasedClip(val clipShape: Shape) : SharedTransitionScope.OverlayClip {
        private val path = Path()

        override fun getClipPath(
            sharedContentState: SharedTransitionScope.SharedContentState,
            bounds: Rect,
            layoutDirection: LayoutDirection,
            density: Density
        ): Path {
            path.reset()
            path.addOutline(clipShape.createOutline(bounds.size, layoutDirection, density))
            path.translate(bounds.topLeft)
            return path
        }
    }
}

private val DefaultEnabled: () -> Boolean = { true }

private data class SkipToLookaheadElement(
    val scaleToBounds: ScaleToBoundsImpl? = null,
    val isEnabled: () -> Boolean = DefaultEnabled,
) : ModifierNodeElement<SkipToLookaheadNode>() {
    override fun create(): SkipToLookaheadNode {
        return SkipToLookaheadNode(scaleToBounds, isEnabled)
    }

    override fun update(node: SkipToLookaheadNode) {
        node.scaleToBounds = scaleToBounds
        node.isEnabled = isEnabled
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "skipToLookahead"
        properties["scaleToBounds"] = scaleToBounds
        properties["isEnabled"] = isEnabled
    }
}


private class SkipToLookaheadNode(scaleToBounds: ScaleToBoundsImpl?, isEnabled: () -> Boolean) :
    LayoutModifierNode, Modifier.Node() {
    var lookaheadConstraints: Constraints? = null
    var scaleToBounds: ScaleToBoundsImpl? by mutableStateOf(scaleToBounds)
    var isEnabled: () -> Boolean by mutableStateOf(isEnabled)

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        if (isLookingAhead) {
            lookaheadConstraints = constraints
        }
        val p = measurable.measure(lookaheadConstraints!!)
        val contentSize = IntSize(p.width, p.height)
        val constrainedSize = constraints.constrain(contentSize)
        return layout(constrainedSize.width, constrainedSize.height) {
            val scaleToBounds = scaleToBounds
            if (!isEnabled() || scaleToBounds == null) {
                p.place(0, 0)
            } else {
                val contentScale = scaleToBounds.contentScale
                val resolvedScale =
                    if (contentSize.width == 0 || contentSize.height == 0) {
                        ScaleFactor(1f, 1f)
                    } else
                        contentScale.computeScaleFactor(
                            contentSize.toSize(),
                            constrainedSize.toSize()
                        )

                val (x, y) =
                    scaleToBounds.alignment.align(
                        IntSize(
                            (contentSize.width * resolvedScale.scaleX).roundToInt(),
                            (contentSize.height * resolvedScale.scaleY).roundToInt()
                        ),
                        constrainedSize,
                        layoutDirection
                    )
                p.placeWithLayer(x, y) {
                    scaleX = resolvedScale.scaleX
                    scaleY = resolvedScale.scaleY
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}

@Immutable
internal interface LayerRenderer {
    val parentState: SharedElementInternalState?

    fun drawInOverlay(drawScope: DrawScope)

    val zIndex: Float
}

private val DefaultSpring =
    spring(stiffness = StiffnessMediumLow, visibilityThreshold = Rect.VisibilityThreshold)


private val ParentClip: SharedTransitionScope.OverlayClip =
    object : SharedTransitionScope.OverlayClip {
        override fun getClipPath(
            sharedContentState: SharedTransitionScope.SharedContentState,
            bounds: Rect,
            layoutDirection: LayoutDirection,
            density: Density
        ): Path? {
            return sharedContentState.parentSharedContentState?.clipPathInOverlay
        }
    }

private val DefaultClipInOverlayDuringTransition: (LayoutDirection, Density) -> Path? = { _, _ ->
    null
}


private val DefaultBoundsTransform = BoundsTransform { _, _ -> DefaultSpring }

internal const val VisualDebugging = false

/** Caching immutable ScaleToBoundsImpl objects to avoid extra allocation */

private fun ScaleToBoundsCached(
    contentScale: ContentScale,
    alignment: Alignment
): ScaleToBoundsImpl {
    if (contentScale.shouldCache && alignment.shouldCache) {
        val map = cachedScaleToBoundsImplMap.getOrPut(contentScale) { mutableMapOf() }
        return map.getOrPut(alignment) { ScaleToBoundsImpl(contentScale, alignment) }
    } else {
        // Custom contentScale or alignment. No caching to avoid memory leak. This should be the
        // rare case
        return ScaleToBoundsImpl(contentScale, alignment)
    }
}

private val Alignment.shouldCache: Boolean
    get() =
        this === TopStart ||
                this === TopCenter ||
                this === TopEnd ||
                this === CenterStart ||
                this === Center ||
                this === CenterEnd ||
                this === BottomStart ||
                this === BottomCenter ||
                this === BottomEnd

private val ContentScale.shouldCache: Boolean
    get() =
        this === ContentScale.FillWidth ||
                this === ContentScale.FillHeight ||
                this === ContentScale.FillBounds ||
                this === ContentScale.Fit ||
                this === ContentScale.Crop ||
                this === ContentScale.None ||
                this === ContentScale.Inside


private val cachedScaleToBoundsImplMap =
    mutableMapOf<ContentScale, MutableMap<Alignment, ScaleToBoundsImpl>>()

@Immutable
private class ScaleToBoundsImpl(val contentScale: ContentScale, val alignment: Alignment) :
    SharedTransitionScope.ResizeMode

private object RemeasureImpl : SharedTransitionScope.ResizeMode


/**
 * [SharedTransitionScope] provides a coordinator space in which shared elements/ shared bounds
 * (when matched) will transform their bounds from one to another. Their position animation is
 * always relative to the origin defined by where [SharedTransitionScope] is in the tree.
 *
 * [SharedTransitionScope] also creates an overlay, in which all shared elements and shared bounds
 * are rendered by default, so that they are not subject to their parent's fading or clipping, and
 * can therefore transform the bounds without alpha jumps or being unintentionally clipped.
 *
 * It is also [SharedTransitionScope]'s responsibility to do the [SharedContentState] key match for
 * all the [sharedElement] or [sharedBounds] defined in this scope. Note: key match will not work
 * for [SharedContentState] created in different [SharedTransitionScope]s.
 *
 * [SharedTransitionScope] oversees all the animations in its scope. When any of the animations is
 * active, [isTransitionActive] will be true. Once a bounds transform starts, by default the shared
 * element or shared bounds will render the content in the overlay. The rendering will remain in the
 * overlay until all other animations in the [SharedTransitionScope] are finished (i.e. when
 * [isTransitionActive] == false).
 */
@Stable
public interface SharedTransitionScope : LookaheadScope {

    /**
     * PlaceHolderSize defines the size of the space that was or will be occupied by the exiting or
     * entering [sharedElement]/[sharedBounds].
     */
    public fun interface PlaceHolderSize {
        public companion object {
            /**
             * [animatedSize] is a pre-defined [SharedTransitionScope.PlaceHolderSize] that lets the
             * parent layout of shared elements or shared bounds observe the animated size during an
             * active shared transition. Therefore the layout parent will most likely resize itself
             * and re-layout its children to adjust to the new animated size.
             *
             * @see [contentSize]
             * @see [SharedTransitionScope.PlaceHolderSize]
             */
            public val animatedSize: PlaceHolderSize = PlaceHolderSize { _, animatedSize ->
                animatedSize
            }

            /**
             * [contentSize] is a pre-defined [SharedTransitionScope.PlaceHolderSize] that allows
             * the parent layout of shared elements or shared bounds to see the content size of the
             * shared content during an active shared transition. For outgoing content, this
             * [contentSize] is the initial size before the animation, whereas for incoming content
             * [contentSize] will return the lookahead/target size of the content. This is the
             * default value for shared elements and shared bounds. The effect is that the parent
             * layout does not resize during the shared element transition, hence giving a sense of
             * stability, rather than dynamic motion. If it's preferred to have parent layout
             * dynamically adjust its layout based on the shared element's animated size, consider
             * using [animatedSize].
             *
             * @see [contentSize]
             * @see [SharedTransitionScope.PlaceHolderSize]
             */
            public val contentSize: PlaceHolderSize = PlaceHolderSize { contentSize, _ ->
                contentSize
            }
        }

        /**
         * Returns the size of the place holder based on [contentSize] and [animatedSize]. Note:
         * [contentSize] for exiting content is the size before it starts exiting. For entering
         * content, [contentSize] is the lookahead size of the content (i.e. target size of the
         * shared transition).
         */
        public fun calculateSize(contentSize: IntSize, animatedSize: IntSize): IntSize
    }

    /**
     * There are two different modes to resize child layout of [sharedBounds] during bounds
     * transform: 1) [ScaleToBounds] and 2) [RemeasureToBounds].
     *
     * [ScaleToBounds] first measures the child layout with the lookahead constraints, similar to
     * [skipToLookaheadSize]. Then the child's stable layout will be scaled to fit in the shared
     * bounds.
     *
     * In contrast, [RemeasureToBounds] will remeasure and relayout the child layout of
     * [sharedBounds] with animated fixed constraints based on the size of the bounds transform. The
     * re-measurement is triggered by the bounds size change, which could potentially be every
     * frame.
     *
     * [ScaleToBounds] works best for Texts and bespoke layouts that don't respond well to
     * constraints change. [RemeasureToBounds] works best for background, shared images of different
     * aspect ratios, and other layouts that adjust themselves visually nicely and efficiently to
     * size changes.
     */
    public sealed interface ResizeMode {
        public companion object {
            /**
             * In contrast to [ScaleToBounds], [RemeasureToBounds] is a [ResizeMode] that remeasures
             * and relayouts its child whenever bounds change during the bounds transform. More
             * specifically, when the [sharedBounds] size changes, it creates fixed constraints
             * based on the animated size, and uses the fixed constraints to remeasure the child.
             * Therefore, the child layout of [sharedBounds] will likely change its layout to fit in
             * the animated constraints.
             *
             * [RemeasureToBounds] mode works well for layouts that respond well to constraints
             * change, such as background and Images. It does not work well for layouts with
             * specific size requirements. Such layouts include Text, and bespoke layouts that could
             * result in overlapping children when constrained to too small of a size. In these
             * cases, it's recommended to use [ScaleToBounds] instead.
             */
            public val RemeasureToBounds: ResizeMode = RemeasureImpl

            /**
             * [ScaleToBounds] as a type of [ResizeMode] will measure the child layout with
             * lookahead constraints to obtain the size of the stable layout. This stable layout is
             * the post-animation layout of the child. Then based on the stable size of the child
             * and the animated size of the [sharedBounds], the provided [contentScale] will be used
             * to calculate a scale for both width and height. The resulting effect is that the
             * child layout does not re-layout during the bounds transform, contrary to
             * [RemeasureToBounds] mode. Instead, it will scale the stable layout based on the
             * animated size of the [sharedBounds].
             *
             * [ScaleToBounds] works best for [sharedBounds] when used to animate shared Text.
             *
             * [ContentScale.Companion.FillWidth] is the default value for [contentScale]. [alignment] will be
             * used to calculate the placement of the scaled content. It is [Center] by
             * default.
             */
            public fun ScaleToBounds(
                contentScale: ContentScale = ContentScale.FillWidth,
                alignment: Alignment = Center
            ): ResizeMode = ScaleToBoundsCached(contentScale, alignment)
        }
    }

    /**
     * Indicates whether there is any ongoing transition between matched [sharedElement] or
     * [sharedBounds].
     */
    public val isTransitionActive: Boolean

    /**
     * [skipToLookaheadSize] enables a layout to measure its child with the lookahead constraints,
     * therefore laying out the child as if the transition has finished. This is particularly
     * helpful for layouts where re-flowing content based on animated constraints is undesirable,
     * such as texts.
     *
     * In the sample below, try remove the [skipToLookaheadSize] modifier and observe the
     * difference:
     *
     * @sample androidx.compose.animation.samples.NestedSharedBoundsSample
     */
    public fun Modifier.skipToLookaheadSize(): Modifier

    /**
     * Renders the content in the [SharedTransitionScope]'s overlay, where shared content (i.e.
     * shared elements and shared bounds) is rendered by default. This is useful for rendering
     * content that is not shared on top of shared content to preserve a specific spatial
     * relationship.
     *
     * [renderInOverlay] dynamically controls whether the content should be rendered in the
     * [SharedTransitionScope]'s overlay. By default, it returns the same value as
     * [SharedTransitionScope.isTransitionActive]. This means the default behavior is to render the
     * child layout of this modifier in the overlay only when the transition is active.
     *
     * **IMPORTANT:** When elevating layouts into the overlay, the layout is no longer subjected
     * to 1) its parent's clipping, and 2) parent's layer transform (e.g. alpha, scale, etc).
     * Therefore, it is recommended to create an enter/exit animation (e.g. using
     * [AnimatedVisibilityScope.animateEnterExit]) for the child layout to avoid any abrupt visual
     * changes.
     *
     * [clipInOverlayDuringTransition] supports a custom clip path if clipping is desired. By
     * default, no clipping is applied. Manual management of clipping can often be avoided by
     * putting layouts with clipping as children of this modifier (i.e. to the right side of this
     * modifier).
     *
     * @sample androidx.compose.animation.samples.SharedElementWithFABInOverlaySample
     */
    public fun Modifier.renderInSharedTransitionScopeOverlay(
        renderInOverlay: () -> Boolean = { isTransitionActive },
        zIndexInOverlay: Float = 0f,
        clipInOverlayDuringTransition: (LayoutDirection, Density) -> Path? =
            DefaultClipInOverlayDuringTransition
    ): Modifier

    /**
     * [OverlayClip] defines a specific clipping that should be applied to a [sharedBounds] or
     * [sharedElement] in the overlay.
     */
    public interface OverlayClip {
        /**
         * Creates a clip path based using current animated [bounds] of the [sharedBounds] or
         * [sharedElement], their [sharedContentState] (to query parent state's bounds if needed),
         * and [layoutDirection] and [density]. The topLeft of the [bounds] is the local position of
         * the sharedElement/sharedBounds in the [SharedTransitionScope].
         *
         * **Important**: The returned [Path] needs to be offset-ed as needed such that it is in
         * [lookaheadScopeCoordinates]'s coordinate space. For example, if the
         * path is created using [bounds], it needs to be offset-ed by [bounds].topLeft.
         *
         * It is recommended to modify the same [Path] object and return it here, instead of
         * creating new [Path]s.
         */
        public fun getClipPath(
            sharedContentState: SharedContentState,
            bounds: Rect,
            layoutDirection: LayoutDirection,
            density: Density
        ): Path?
    }

    public fun Modifier.sharedElementWithCallerManagedVisibility(
        sharedContentState: SharedContentState,
        visible: Boolean,
        boundsTransform: BoundsTransform = DefaultBoundsTransform,
        placeHolderSize: PlaceHolderSize = contentSize,
        renderInOverlayDuringTransition: Boolean = true,
        zIndexInOverlay: Float = 0f,
        clipInOverlayDuringTransition: OverlayClip = ParentClip,
        initialVelocityProvider: (() -> Rect)? = null,
        acceptIncomingInitialVelocity: Boolean = true
    ): Modifier

    /** Creates an [OverlayClip] based on a specific [clipShape]. */
    public fun OverlayClip(clipShape: Shape): OverlayClip

    /** Creates and remembers a [SharedContentState] with a given [key]. */
    @Stable
    @Composable
    public fun rememberSharedContentState(key: Any): SharedContentState

    /**
     * [SharedContentState] is designed to allow access of the properties of
     * [sharedBounds]/[sharedElement], such as whether a match of the same [key] has been found in
     * the [SharedTransitionScope], its [clipPathInOverlay] and [parentSharedContentState] if there
     * is a parent [sharedBounds] in the layout tree.
     */
    public class SharedContentState internal constructor(public val key: Any) {
        /**
         * Indicates whether a match of the same [key] has been found. [sharedElement] or
         * [sharedBounds] will not have any animation unless a match has been found.
         *
         * _Caveat_: [isMatchFound] is only set to true _after_ a new [sharedElement]/[sharedBounds]
         * of the same [key] has been composed. If the new [sharedBounds]/[sharedElement] is
         * declared in subcomposition (e.g. a LazyList) where the composition happens as a part of
         * the measure/layout pass, that's when [isMatchFound] will become true.
         */
        public val isMatchFound: Boolean
            get() = internalState?.sharedElement?.foundMatch ?: false

        /**
         * The resolved clip path in overlay based on the [OverlayClip] defined for the shared
         * content. [clipPathInOverlay] is set during Draw phase, before children are drawn. This
         * means it is safe to query [parentSharedContentState]'s [clipPathInOverlay] when the
         * shared content is drawn.
         */
        public val clipPathInOverlay: Path?
            get() = nonNullInternalState.clipPathInOverlay

        /** Returns the [SharedContentState] of a parent [sharedBounds], if any. */
        public val parentSharedContentState: SharedContentState?
            get() = nonNullInternalState.parentState?.userState

        internal var internalState: SharedElementInternalState? by mutableStateOf(null)
        private val nonNullInternalState: SharedElementInternalState
            get() =
                requireNotNull(internalState) {
                    "Error: SharedContentState has not been added to a sharedElement/sharedBounds" +
                            "modifier yet. Therefore the internal state has not bee initialized."
                }
    }
}