package com.newagedevs.gesturevolume.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The demo's own state: resting, replaying, and the wind-back between passes that is not acting. */
class GestureDemoStateTest {

    private val steps = listOf(DemoGesture.TAP, DemoGesture.SWIPE_UP, DemoGesture.SWIPE_DOWN)

    @Test
    fun `at rest every step reads as done and nothing acts`() {
        val demo = GestureDemoState(steps, passes = 0)
        assertFalse(demo.playing)
        assertFalse(demo.acting)
        steps.forEach { assertEquals(1f, demo.progressOf(it), 0f) }
    }

    @Test
    fun `a replay does not act on the pass before it`() {
        // Where a finished pass leaves it: the last step done.
        val demo = GestureDemoState(steps, passes = 0)
        demo.replay()
        assertTrue(demo.playing)
        // Until the wind-back is over, the old pass still reads as finished, and anything that
        // acted on it would show the end of a gesture before the start of the next: a flicker.
        assertFalse(demo.acting)
    }

    @Test
    fun `the first pass on arrival acts from the start`() {
        val demo = GestureDemoState(steps, passes = 2)
        assertTrue(demo.playing)
        assertTrue(demo.acting)
        assertEquals(0f, demo.progressOf(DemoGesture.TAP), 0f)
        assertEquals(0f, demo.progressOf(DemoGesture.SWIPE_DOWN), 0f)
    }
}
