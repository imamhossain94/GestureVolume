package com.newagedevs.gesturevolume.ui.motion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The press squeeze and the rubber band, as far as they are maths. */
class BouncePhysicsTest {

    private val density = 2.75f

    private fun px(dp: Float): Int = (dp * density).toInt()

    @Test
    fun `things give by about the same distance whatever their size, within limits`() {
        val row = Squeeze.fraction(px(360f), px(56f), density)
        val card = Squeeze.fraction(px(170f), px(120f), density)
        val icon = Squeeze.fraction(px(48f), px(48f), density)
        // A row and a card both come in by about the depth along their longer side.
        assertEquals(Squeeze.DEPTH_DP, row * 360f, 0.3f)
        assertEquals(Squeeze.DEPTH_DP, card * 170f, 0.3f)
        // An icon would give a sixth of itself; it is held to the most.
        assertEquals(Squeeze.MAX_FRACTION, icon, 0f)
        // Something wider than any control still moves visibly.
        assertEquals(Squeeze.MIN_FRACTION, Squeeze.fraction(px(900f), px(60f), density), 0f)
    }

    @Test
    fun `a whole screen or a scrim does not shrink under a finger`() {
        assertEquals(0f, Squeeze.fraction(px(400f), px(800f), density), 0f)
        assertEquals(0f, Squeeze.fraction(0, px(50f), density), 0f)
        assertEquals(0f, Squeeze.fraction(px(50f), px(50f), 0f), 0f)
    }

    @Test
    fun `easing takes back only movement toward rest, and never past it`() {
        assertEquals(0f, RubberBand.easing(0f, 30f), 0f)
        assertEquals(0f, RubberBand.easing(40f, 10f), 0f)
        assertEquals(-10f, RubberBand.easing(40f, -10f), 0f)
        assertEquals(-40f, RubberBand.easing(40f, -100f), 0f)
        assertEquals(25f, RubberBand.easing(-25f, 60f), 0f)
    }

    @Test
    fun `a pull follows less the further out it is, and never reaches its limit`() {
        val reach = 400f
        var offset = 0f
        var lastStep = Float.MAX_VALUE
        repeat(200) {
            val next = RubberBand.pull(offset, 20f, reach)
            val step = next - offset
            assertTrue("gave more as it went out: $step after $lastStep", step <= lastStep + 1e-4f)
            lastStep = step
            offset = next
        }
        assertTrue(offset < reach)
        assertTrue("barely moved: $offset", offset > reach * 0.9f)
        assertEquals(20f * RubberBand.GIVE, RubberBand.pull(0f, 20f, reach), 1e-4f)
        assertEquals(-20f * RubberBand.GIVE, RubberBand.pull(0f, -20f, reach), 1e-4f)
    }

    @Test
    fun `a list at its end is pulled by the finger, not scrolled, and eased back by it`() {
        val effect = RubberBandOverscroll(density)
        // At the top already: the list takes none of a downward drag.
        val consumed = effect.applyToScroll(Offset(0f, 50f), NestedScrollSource.UserInput) { Offset.Zero }
        assertEquals(Offset(0f, 50f), consumed)
        assertEquals(50f * RubberBand.GIVE, effect.offset.y, 1e-3f)
        assertTrue(effect.isInProgress)

        // Dragging back up: the pull comes undone before the list is asked to scroll at all.
        var scrolled = Offset.Zero
        effect.applyToScroll(Offset(0f, -10f), NestedScrollSource.UserInput) { scrolled = it; it }
        assertEquals(Offset.Zero, scrolled)
        assertEquals(50f * RubberBand.GIVE - 10f, effect.offset.y, 1e-3f)

        // Past rest, what is left over scrolls the list, and the band is exactly at rest.
        effect.applyToScroll(Offset(0f, -100f), NestedScrollSource.UserInput) { scrolled = it; it }
        assertEquals(0f, effect.offset.y, 0f)
        assertEquals(-(100f - (50f * RubberBand.GIVE - 10f)), scrolled.y, 1e-3f)
        assertFalse(effect.isInProgress)
    }

    @Test
    fun `a fling's leftover does not pull the band while it scrolls, only a finger does`() {
        val effect = RubberBandOverscroll(density)
        val consumed = effect.applyToScroll(Offset(0f, 80f), NestedScrollSource.SideEffect) { Offset(0f, 30f) }
        assertEquals(Offset(0f, 30f), consumed)
        assertEquals(0f, effect.offset.y, 0f)
    }

    @Test
    fun `the band stays within its reach however hard it is pulled`() {
        val effect = RubberBandOverscroll(density)
        repeat(500) { effect.applyToScroll(Offset(0f, -300f), NestedScrollSource.UserInput) { Offset.Zero } }
        assertTrue(abs(effect.offset.y) < RubberBand.REACH_DP * density)
    }
}
