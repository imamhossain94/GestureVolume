package com.newagedevs.gesturevolume.utils

import com.newagedevs.gesturevolume.utils.GesturePreview.Effect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Appearance preview acts out for each action, and the levels its swipes leave. */
class GesturePreviewTest {

    private val byLength = BarBehaviour.SWIPE_STEP_BY_LENGTH

    @Test
    fun `a swipe shows what its action does`() {
        assertEquals(Effect.VOLUME, GesturePreview.swipeEffect(HandlerActions.INCREASE_VOLUME))
        assertEquals(Effect.VOLUME_PANEL, GesturePreview.swipeEffect(HandlerActions.INCREASE_VOLUME_UI))
        assertEquals(Effect.VOLUME_PANEL, GesturePreview.swipeEffect(HandlerActions.DECREASE_VOLUME_UI))
        assertEquals(Effect.BRIGHTNESS, GesturePreview.swipeEffect(HandlerActions.INCREASE_BRIGHTNESS))
        assertEquals(Effect.NOTHING, GesturePreview.swipeEffect(HandlerActions.NONE))
        // Anything from the catalog happens once, at the start of the stroke.
        assertEquals(Effect.ONCE, GesturePreview.swipeEffect(HandlerActions.OPEN_QUICK_SLIDER))
        assertEquals(Effect.ONCE, GesturePreview.swipeEffect(HandlerActions.SCREENSHOT))
        assertEquals(Effect.OPENS_PANEL, GesturePreview.swipeEffect(HandlerActions.OPEN_VOLUME_UI))
    }

    @Test
    fun `a tap opens the panel, does something once, or nothing`() {
        assertEquals(Effect.OPENS_PANEL, GesturePreview.tapEffect(HandlerActions.OPEN_VOLUME_UI))
        assertEquals(Effect.ONCE, GesturePreview.tapEffect(HandlerActions.MUTE))
        assertEquals(Effect.NOTHING, GesturePreview.tapEffect(HandlerActions.NONE))
        assertFalse(GesturePreview.tapEffect(HandlerActions.MUTE).steers)
    }

    @Test
    fun `swipe up raises the level and swipe down brings it back`() {
        val start = GesturePreview.levelAt(0f, 0f, upSteers = true, downSteers = true, stepPercent = byLength)
        val raised = GesturePreview.levelAt(1f, 0f, upSteers = true, downSteers = true, stepPercent = byLength)
        val back = GesturePreview.levelAt(1f, 1f, upSteers = true, downSteers = true, stepPercent = byLength)
        assertEquals(GesturePreview.START, start)
        assertTrue("$raised is not above $start", raised > start)
        assertEquals(start, back)
    }

    @Test
    fun `the direction is the finger's, whatever the action is called`() {
        // A swipe-up slot holding a "Decrease" identifier still raises: the live bar reads the
        // direction from the gesture's sign. See HandlerActions.
        val effect = GesturePreview.swipeEffect(HandlerActions.DECREASE_VOLUME)
        assertTrue(effect.steers)
        assertTrue(GesturePreview.levelAt(1f, 0f, effect.steers, false, byLength) > GesturePreview.START)
    }

    @Test
    fun `by length it climbs a step at a time, the first soon after it starts`() {
        assertEquals(0, GesturePreview.stepsAt(0f))
        assertEquals(1, GesturePreview.stepsAt(0.15f))
        assertEquals(GesturePreview.SWIPE_STEPS, GesturePreview.stepsAt(1f))
        var last = -1
        for (i in 0..100) {
            val level = GesturePreview.levelAt(i / 100f, 0f, true, true, byLength)
            assertTrue("fell at $i%", level >= last)
            last = level
        }
        // Each step is one of media's fifteen.
        val one = GesturePreview.levelAt(0.15f, 0f, true, true, byLength) - GesturePreview.START
        assertEquals(100f / GesturePreview.RANGE_STEPS, one.toFloat(), 1f)
    }

    @Test
    fun `a fixed amount moves once, as the swipe begins`() {
        listOf(5, 10, 20).forEach { step ->
            assertEquals(GesturePreview.START, GesturePreview.levelAt(0.05f, 0f, true, true, step))
            assertEquals(GesturePreview.START + step, GesturePreview.levelAt(0.2f, 0f, true, true, step))
            // However long the stroke goes on.
            assertEquals(GesturePreview.START + step, GesturePreview.levelAt(1f, 0f, true, true, step))
            assertEquals(GesturePreview.START, GesturePreview.levelAt(1f, 1f, true, true, step))
        }
    }

    @Test
    fun `a swipe that steers something else leaves this level alone`() {
        assertEquals(GesturePreview.START, GesturePreview.levelAt(1f, 1f, upSteers = false, downSteers = false, stepPercent = byLength))
        val downOnly = GesturePreview.levelAt(1f, 1f, upSteers = false, downSteers = true, stepPercent = byLength)
        assertTrue(downOnly < GesturePreview.START)
        // Never out of range.
        assertTrue(GesturePreview.levelAt(1f, 1f, upSteers = false, downSteers = true, stepPercent = 20) >= 0)
    }
}
