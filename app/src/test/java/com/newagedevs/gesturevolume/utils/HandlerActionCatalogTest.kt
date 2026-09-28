package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the pickers offer and what the summary rows can name. */
class HandlerActionCatalogTest {

    @Test
    fun `every identifier a slot can hold has a label and an icon`() {
        // A swipe can now hold anything, so a row naming its binding must be able to name them all.
        HandlerActions.KNOWN.forEach { action ->
            assertNotNull(action, HandlerActionCatalog.displayEntryFor(action))
        }
    }

    @Test
    fun `each swipe picker steers only its own direction`() {
        val up = HandlerActionCatalog.swipeAdjust(isSwipeUp = true).map { it.action }
        val down = HandlerActionCatalog.swipeAdjust(isSwipeUp = false).map { it.action }
        assertEquals(
            listOf(HandlerActions.INCREASE_VOLUME_UI, HandlerActions.INCREASE_VOLUME, HandlerActions.INCREASE_BRIGHTNESS),
            up
        )
        assertEquals(
            listOf(HandlerActions.DECREASE_VOLUME_UI, HandlerActions.DECREASE_VOLUME, HandlerActions.DECREASE_BRIGHTNESS),
            down
        )
        (up + down).forEach { assertTrue(it, HandlerActions.isAdjustSwipe(it)) }
    }

    @Test
    fun `steered bindings stay out of the catalog and the long-press menu`() {
        // They mean nothing on a tap or in a menu: there is no stroke to steer them by.
        HandlerActionCatalog.ALL.forEach { assertFalse(it.action, HandlerActions.isAdjustSwipe(it.action)) }
        HandlerActionCatalog.CONTEXT_MENU_CANDIDATES.forEach {
            assertFalse(it.action, HandlerActions.isAdjustSwipe(it.action))
        }
    }

    @Test
    fun `the new device actions are assignable, in the device group, and need no permission`() {
        listOf(
            HandlerActions.RING_VIBRATE, HandlerActions.WIFI_PANEL, HandlerActions.BLUETOOTH_SETTINGS,
            HandlerActions.INTERNET_PANEL, HandlerActions.OPEN_CAMERA, HandlerActions.VOICE_ASSISTANT,
        ).forEach { action ->
            val entry = HandlerActionCatalog.entryFor(action)
            assertNotNull(action, entry)
            assertEquals(action, HandlerActionCatalog.Group.DEVICE, entry!!.group)
            assertEquals(action, action, HandlerActions.sanitize(action))
            assertFalse(action, HandlerActions.needsAccessibility(action))
            assertFalse(action, HandlerActions.needsWriteSettings(action))
            assertFalse(action, HandlerActions.needsNotificationPolicy(action))
            assertTrue(action, HandlerActionCatalog.CONTEXT_MENU_CANDIDATES.any { it.action == action })
        }
    }

    @Test
    fun `a launch-app action carries its app, and survives being read back`() {
        val action = HandlerActions.launchApp("com.whatsapp")
        assertEquals("com.whatsapp", HandlerActions.launchedPackage(action))
        assertEquals(action, HandlerActions.sanitize(action))
        assertTrue(HandlerActions.isKnown(action))
        // The bare tile is only the picker's question, never a stored answer.
        assertEquals(HandlerActions.NONE, HandlerActions.sanitize(HandlerActions.LAUNCH_APP))
        assertEquals(HandlerActions.NONE, HandlerActions.sanitize(HandlerActions.launchApp(" ")))
        val entry = HandlerActionCatalog.displayEntryFor(action)
        assertEquals(ActionIcon.App("com.whatsapp"), entry?.icon)
        assertFalse(HandlerActionCatalog.CONTEXT_MENU_CANDIDATES.any { it.action == HandlerActions.LAUNCH_APP })
    }

    @Test
    fun `no identifier is offered twice`() {
        val actions = HandlerActionCatalog.ALL.map { it.action }
        assertEquals(actions.size, actions.toSet().size)
    }
}
