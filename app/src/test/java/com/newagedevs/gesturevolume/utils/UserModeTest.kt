package com.newagedevs.gesturevolume.utils

import com.newagedevs.gesturevolume.data.local.QuickSliderStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regular and advanced users, and the Classic preset a regular user starts from. */
class UserModeTest {

    @Test
    fun `each mode starts from its own preset, and anything unknown is regular`() {
        assertEquals("Classic", UserMode.presetFor(UserMode.REGULAR).id)
        assertEquals("Dock", UserMode.presetFor(UserMode.ADVANCED).id)
        assertEquals(UserMode.REGULAR, UserMode.sanitize(null))
        assertEquals(UserMode.REGULAR, UserMode.sanitize("expert"))
        assertEquals(UserMode.ADVANCED, UserMode.sanitize(UserMode.ADVANCED))
    }

    @Test
    fun `the Classic is the original round button`() {
        val classic = HandlerPresets.CLASSIC
        assertEquals(30f, classic.width, 0f)
        assertEquals(100f, classic.height, 0f)
        assertEquals(15f, classic.cornerRadius, 0f)
        assertEquals(128, classic.bgAlpha)
        assertFalse(classic.showIcon)
        assertTrue(classic in HandlerPresets.ALL)
    }

    @Test
    fun `nothing a regular user starts with opens a panel, the volume keys included`() {
        val b = HandlerPresets.CLASSIC.behaviour
        assertEquals(HandlerActions.INCREASE_VOLUME_UI, b.swipeUp)
        assertEquals(HandlerActions.DECREASE_VOLUME_UI, b.swipeDown)
        assertEquals(HandlerActions.OPEN_VOLUME_UI, b.singleTap)
        assertEquals(HandlerActions.REPOSITION, b.longPress)
        val slots = listOf(b.singleTap, b.doubleTap, b.tripleTap, b.longPress, b.swipeUp, b.swipeDown, b.swipeIn, b.swipeOut)
        assertFalse(slots.any { it == HandlerActions.OPEN_QUICK_SLIDER || it == HandlerActions.OPEN_DECK })
        assertEquals(QuickSliderStore.VOLUME_KEYS_OFF, b.slider.volumeKeys)
    }

    @Test
    fun `the advanced default still opens its panels`() {
        val b = HandlerPresets.DEFAULT.behaviour
        assertEquals(HandlerActions.OPEN_QUICK_SLIDER, b.swipeUp)
        assertEquals(HandlerActions.OPEN_DECK, b.swipeIn)
    }
}
