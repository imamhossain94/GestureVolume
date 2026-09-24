package com.newagedevs.gesturevolume.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * Everything tappable gives a little under a finger and springs back past rest when let go.
 *
 * Provided as the app's [androidx.compose.foundation.LocalIndication] in place of the bare ripple,
 * so every `clickable`, `selectable` and `toggleable` that does not name its own indication — the
 * cards, rows, chips and tiles, in the settings and in the overlays alike — has it without being
 * touched. The ripple is still there: this wraps it rather than replacing it.
 *
 * The squeeze is a layer's scale, set as the content is placed, and not a scale in the draw pass.
 * It has to be: a draw node here would stop the ripple beside it from drawing at all, since
 * Compose hands the draw pass to the indication's node or to its delegates, never to both — and
 * as two drawing delegates each would draw the content once. A layer is a separate pass, so the
 * ripple draws as it always has and the content under it is what gets scaled. It also moves where
 * a touch lands with what is drawn.
 */
class PressBounce(private val ripple: IndicationNodeFactory) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressBounceNode(interactionSource, ripple.create(interactionSource))

    override fun equals(other: Any?): Boolean = other is PressBounce && other.ripple == ripple

    override fun hashCode(): Int = ripple.hashCode()
}

/**
 * The same squeeze for a component that brings its own ripple — Material's buttons do, and ignore
 * the app's indication — driven by the presses on [interactionSource], which has to be the one
 * the component itself was given.
 */
fun Modifier.pressBounce(interactionSource: InteractionSource): Modifier = this then SqueezeElement(interactionSource)

private class PressBounceNode(interactions: InteractionSource, ripple: DelegatableNode) : DelegatingNode() {
    init {
        delegate(ripple)
        delegate(SqueezeNode(interactions))
    }
}

private data class SqueezeElement(private val interactions: InteractionSource) : ModifierNodeElement<SqueezeNode>() {
    override fun create() = SqueezeNode(interactions)

    override fun update(node: SqueezeNode) = node.update(interactions)

    override fun InspectorInfo.inspectableProperties() {
        name = "pressBounce"
        properties["interactionSource"] = interactions
    }
}

private class SqueezeNode(private var interactions: InteractionSource) : Modifier.Node(), LayoutModifierNode {

    /** 0 at rest, 1 fully pressed. Below 0 on the way back, which is the bounce. */
    private val depth = Animatable(0f)

    /** How much of its size this one gives at full depth. Worked out at measure, from its size. */
    private var fraction = 0f

    /** Presses still down. More than one finger can be on it; it lets go when the last one does. */
    private var held = 0

    private var watching: Job? = null

    private val layer: GraphicsLayerScope.() -> Unit = {
        val scale = 1f - depth.value * fraction
        scaleX = scale
        scaleY = scale
    }

    override fun onAttach() {
        // At rest whatever it was doing when it went: a list recycles its rows, and one detached
        // mid-squeeze would otherwise come back into view still squeezed.
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { depth.snapTo(0f) }
        watch()
    }

    override fun onDetach() {
        watching = null
        held = 0
    }

    fun update(next: InteractionSource) {
        if (next == interactions) return
        interactions = next
        held = 0
        // A new source: drop the old watch and start again on this one.
        if (isAttached) watch()
    }

    private fun watch() {
        watching?.cancel()
        // Undispatched, so it is listening before this call returns: a clickable makes its
        // indication at the first press, and a dispatched start could miss that very press.
        watching = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            interactions.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        held++
                        launch { depth.animateTo(1f, Springs.PressIn) }
                    }
                    is PressInteraction.Release -> {
                        held = (held - 1).coerceAtLeast(0)
                        if (held == 0) launch { letGo(tapped = true) }
                    }
                    is PressInteraction.Cancel -> {
                        held = (held - 1).coerceAtLeast(0)
                        if (held == 0) launch { letGo(tapped = false) }
                    }
                }
            }
        }
    }

    private suspend fun letGo(tapped: Boolean) {
        // A tap quicker than the squeeze: the press and the release arrive together, and the
        // squeeze has barely begun. Finished first, so a tap bounces as visibly as a hold does.
        // Not for a cancel — that is a press that turned into a scroll, and owes no bounce.
        if (tapped && depth.value < 0.6f) depth.animateTo(0.85f, Springs.PressIn)
        depth.animateTo(0f, Springs.Release)
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        fraction = Squeeze.fraction(placeable.width, placeable.height, density)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0, layerBlock = layer)
        }
    }
}

/** How far a pressed thing gives, by its size. Plain maths, so it can be checked without a device. */
internal object Squeeze {

    /** How far the longer side of a pressed thing comes in, in dp, in all. */
    const val DEPTH_DP = 8f

    /** The least a thing gives, so a wide row still moves visibly. */
    const val MIN_FRACTION = 0.015f

    /** The most, so an icon does not shrink to a dot. */
    const val MAX_FRACTION = 0.08f

    /**
     * Past this on its shorter side a thing is a whole screen or a scrim to tap away, not a
     * control, and a screen that shrinks under a finger looks broken rather than springy.
     */
    const val MAX_SHORT_SIDE_DP = 320f

    /** The fraction of its size a [widthPx] × [heightPx] thing gives at full depth, 0 for none. */
    fun fraction(widthPx: Int, heightPx: Int, density: Float): Float {
        if (widthPx <= 0 || heightPx <= 0 || density <= 0f) return 0f
        if (min(widthPx, heightPx) / density > MAX_SHORT_SIDE_DP) return 0f
        val longSide = max(widthPx, heightPx) / density
        // The same distance for everything, so a big card and a small chip give by the same
        // amount rather than the card by five times as much.
        return (DEPTH_DP / longSide).coerceIn(MIN_FRACTION, MAX_FRACTION)
    }
}
