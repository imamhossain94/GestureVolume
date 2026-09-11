package com.newagedevs.gesturevolume.ui.view

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lift of a long swipe that stopped short of the far threshold: whether the half-grown Quick
 * panel finishes opening or goes back into the bar and leaves the stroke to the Deck.
 */
class EdgePullReleaseTest {

    /** A flick's speed, on a 2.5x screen. */
    private val flick = EdgePullRelease.FLICK_DP_PER_S * 2.5f

    private fun settles(progress: Float, velocity: Float, deckBound: Boolean = true) =
        EdgePullRelease.settles(progress, velocity, flick, deckBound)

    @Test
    fun `a stretch lifted short of halfway goes back`() {
        assertFalse(settles(0.49f, 0f))
        assertFalse(settles(0.2f, 100f, deckBound = false))
    }

    @Test
    fun `a drag lifted past halfway finishes the panel`() {
        assertTrue(settles(0.5f, 0f))
        assertTrue(settles(0.75f, flick * 0.4f))
        assertTrue(settles(0.99f, -flick * 0.4f))
    }

    @Test
    fun `a flick past halfway is still the deck`() {
        assertFalse(settles(0.8f, flick))
        assertFalse(settles(0.95f, flick * 3f))
    }

    @Test
    fun `a flick past halfway is the panel when the short swipe has nothing to do`() {
        assertTrue(settles(0.8f, flick * 3f, deckBound = false))
    }

    @Test
    fun `a stretch thrown back toward the edge is put away`() {
        assertFalse(settles(0.9f, -flick))
        assertFalse(settles(0.9f, -flick, deckBound = false))
    }
}
