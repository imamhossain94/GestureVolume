package com.newagedevs.gesturevolume.ui.motion

import androidx.compose.animation.core.animate
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Makes every scrolling thing in the app a rubber band at its ends: pulled past the end, the
 * content follows the finger less and less and springs back when let go; flung into the end, it
 * carries on a little past and bounces back.
 *
 * In place of Android's stretch, through [androidx.compose.foundation.LocalOverscrollFactory],
 * which every list, scrolling column, pager and text field reads — so none of them is touched.
 *
 * @param density pixels per dp, for the distances below.
 */
class RubberBandOverscrollFactory(private val density: Float) : OverscrollFactory {

    override fun createOverscrollEffect(): OverscrollEffect = RubberBandOverscroll(density)

    override fun equals(other: Any?): Boolean = other is RubberBandOverscrollFactory && other.density == density

    override fun hashCode(): Int = density.hashCode()
}

/**
 * One scrolling thing's rubber band. The offset is how far its content is held out past an end,
 * in pixels, per axis; the content is drawn moved by it, inside the clip its container already has.
 */
internal class RubberBandOverscroll(private val density: Float) : OverscrollEffect {

    private val reach = RubberBand.REACH_DP * density

    private var offsetX by mutableFloatStateOf(0f)
    private var offsetY by mutableFloatStateOf(0f)

    /** How far the content is held out, in pixels. */
    val offset: Offset get() = Offset(offsetX, offsetY)

    /** A settle or a rebound under way. Stopped by the next finger. */
    private var motion: Job? = null

    private val shift = ShiftNode()

    override val node: DelegatableNode get() = shift

    override val isInProgress: Boolean get() = offsetX != 0f || offsetY != 0f

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        val finger = source == NestedScrollSource.UserInput
        // A finger takes over from whatever the band was doing, from where it is.
        if (finger) motion?.cancel()

        // The part of the movement that points back toward rest undoes the pull first, so the
        // content does not start scrolling while it is still held out past its end.
        val easedX = RubberBand.easing(offsetX, delta.x)
        val easedY = RubberBand.easing(offsetY, delta.y)
        offsetX += easedX
        offsetY += easedY
        val eased = Offset(easedX, easedY)

        val remaining = delta - eased
        val scrolled = performScroll(remaining)
        // Only a finger pulls. What a fling has left when it meets the end comes to
        // applyToFling as velocity, and bounces there.
        if (!finger) return eased + scrolled

        val left = remaining - scrolled
        offsetX = RubberBand.pull(offsetX, left.x, reach)
        offsetY = RubberBand.pull(offsetY, left.y, reach)
        return delta
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        if (isInProgress) {
            // Let go while held out: the content is at its end, so there is nothing to fling. It
            // goes back to rest, a flick that way helping it along.
            performFling(Velocity.Zero)
            spring(velocity.x * RubberBand.SETTLE_CARRY, velocity.y * RubberBand.SETTLE_CARRY, rebound = false)
            return
        }
        val left = velocity - performFling(velocity)
        if (abs(left.x) < RubberBand.MIN_REBOUND_VELOCITY && abs(left.y) < RubberBand.MIN_REBOUND_VELOCITY) return
        spring(left.x * RubberBand.REBOUND_CARRY, left.y * RubberBand.REBOUND_CARRY, rebound = true)
    }

    /**
     * Runs the band back to rest, starting at [vx], [vy] pixels a second.
     *
     * On the node's own scope rather than in the fling: a fling is cancelled by any touch, a tap
     * included, and a bounce cancelled by a tap would leave the content stranded past its end
     * with no drag coming to ease it back.
     */
    private fun spring(vx: Float, vy: Float, rebound: Boolean) {
        motion?.cancel()
        if (!shift.isAttached) {
            offsetX = 0f
            offsetY = 0f
            return
        }
        val cap = RubberBand.MAX_VELOCITY_DP * density
        val spec = if (rebound) Springs.Rebound else Springs.Settle
        motion = shift.coroutineScope.launch {
            val x = launch {
                if (offsetX != 0f || vx != 0f) {
                    animate(offsetX, 0f, vx.coerceIn(-cap, cap), spec) { value, _ -> offsetX = value }
                }
            }
            val y = launch {
                if (offsetY != 0f || vy != 0f) {
                    animate(offsetY, 0f, vy.coerceIn(-cap, cap), spec) { value, _ -> offsetY = value }
                }
            }
            x.join()
            y.join()
            // Exactly at rest, whatever the spring's last frame was.
            offsetX = 0f
            offsetY = 0f
        }
    }

    private inner class ShiftNode : Modifier.Node(), DrawModifierNode {
        override fun ContentDrawScope.draw() {
            val x = offsetX
            val y = offsetY
            if (x == 0f && y == 0f) {
                drawContent()
            } else {
                translate(x, y) { this@draw.drawContent() }
            }
        }

        override fun onDetach() {
            motion = null
            offsetX = 0f
            offsetY = 0f
        }
    }
}

/** The band's maths, apart from the effect so it can be checked without a device. */
internal object RubberBand {

    /** The furthest content can be pulled past its end, in dp. It never quite gets there. */
    const val REACH_DP = 160f

    /** How much of the finger's travel the content follows as it first leaves its end. */
    const val GIVE = 0.55f

    /** How much of a fling's leftover speed carries on past the end. */
    const val REBOUND_CARRY = 0.35f

    /** How much of the speed of a let-go helps the band home. */
    const val SETTLE_CARRY = 0.25f

    /** Below this, in pixels a second, a fling meeting the end is not worth a bounce. */
    const val MIN_REBOUND_VELOCITY = 50f

    /** The fastest the band starts, in dp a second, however hard the fling: a bounce, not a launch. */
    const val MAX_VELOCITY_DP = 1500f

    /**
     * How much of [delta] goes to easing an offset of [current] back toward rest: none unless it
     * points that way, and never past rest.
     */
    fun easing(current: Float, delta: Float): Float = when {
        current > 0f && delta < 0f -> max(delta, -current)
        current < 0f && delta > 0f -> min(delta, -current)
        else -> 0f
    }

    /**
     * An offset of [current] pulled on by [delta]: it follows by [GIVE] at rest and by less the
     * further out it is, reaching nothing at [reach] — so the content can be pulled toward
     * [reach] but never past it.
     */
    fun pull(current: Float, delta: Float, reach: Float): Float {
        if (delta == 0f || reach <= 0f) return current
        val give = GIVE * (1f - abs(current) / reach).coerceIn(0f, 1f)
        return (current + delta * give).coerceIn(-reach, reach)
    }
}
