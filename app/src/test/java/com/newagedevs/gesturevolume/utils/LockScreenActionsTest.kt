package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the bar on the lock screen may do with the phone in anyone's hand. See HandlerActions.worksWhileLocked. */
class LockScreenActionsTest {

    @Test
    fun `the volume, the Quick panel and the system's own toggles work while locked`() {
        listOf(
            HandlerActions.INCREASE_VOLUME, HandlerActions.DECREASE_VOLUME_UI, HandlerActions.INCREASE_BRIGHTNESS,
            HandlerActions.OPEN_QUICK_SLIDER, HandlerActions.MUTE_OR_UNMUTE, HandlerActions.OPEN_VOLUME_UI,
            HandlerActions.TOGGLE_FLASHLIGHT, HandlerActions.TOGGLE_DND, HandlerActions.RING_VIBRATE,
            HandlerActions.MEDIA_PLAY_PAUSE, HandlerActions.MEDIA_NEXT,
        ).forEach { assertTrue(it, HandlerActions.worksWhileLocked(it)) }
    }

    @Test
    fun `the system actions are left to the lock screen to allow or refuse`() {
        HandlerActions.ACCESSIBILITY_ACTIONS.forEach { assertTrue(it, HandlerActions.worksWhileLocked(it)) }
    }

    @Test
    fun `the Deck, the menu, apps and the bar itself wait for the unlock`() {
        listOf(
            HandlerActions.OPEN_DECK, HandlerActions.OPEN_MENU, HandlerActions.OPEN_NOTES, HandlerActions.OPEN_SEARCH,
            HandlerActions.OPEN_APP, HandlerActions.SCAN_QR, HandlerActions.OPEN_CAMERA, HandlerActions.VOICE_ASSISTANT,
            HandlerActions.WIFI_PANEL, HandlerActions.REPOSITION, HandlerActions.HIDE_HANDLER,
            HandlerActions.STOP_SERVICE, HandlerActions.ACTIVE_MUSIC_OVERLAY, "${HandlerActions.LAUNCH_APP}:com.example",
        ).forEach { assertFalse(it, HandlerActions.worksWhileLocked(it)) }
    }

    @Test
    fun `every Deck shortcut waits for the unlock`() {
        HandlerActions.KNOWN.filter { HandlerActions.deckTileFor(it) != null }
            .forEach { assertFalse(it, HandlerActions.worksWhileLocked(it)) }
    }
}
