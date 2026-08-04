package com.newagedevs.gesturevolume.utils

/**
 * The action identifiers that are persisted in [com.newagedevs.gesturevolume.data.local.SharedPref]
 * and matched by the overlay service.
 *
 * These strings are a **persistence format**: they are written verbatim into SharedPreferences and
 * read back on every launch. Existing values must never be renamed — only added to. Everything here
 * that is not marked NEW has shipped since before 1.2.8.
 *
 * Swipe direction is decided by the *sign of the gesture*, never by the identifier. The identifier
 * only selects three things: whether the swipe is enabled at all ([NONE]), which domain it drives
 * (volume vs brightness), and whether to show the system volume UI. This matters because
 * `getHandlerSwipeDownAction()` historically defaulted to an "Increase..." string — treating the
 * identifier as authoritative for direction would invert swipe-down for every existing user.
 */
object HandlerActions {

    const val NONE = "None"

    // Volume — swipe
    const val INCREASE_VOLUME = "Increase volume"
    const val INCREASE_VOLUME_UI = "Increase volume and show UI"
    const val DECREASE_VOLUME = "Decrease volume"
    const val DECREASE_VOLUME_UI = "Decrease volume and show UI"

    // Brightness — swipe (NEW in 1.2.9)
    const val INCREASE_BRIGHTNESS = "Increase brightness"
    const val DECREASE_BRIGHTNESS = "Decrease brightness"

    // Tap actions
    const val OPEN_VOLUME_UI = "Open volume UI"
    const val MUTE = "Mute"
    const val MUTE_OR_UNMUTE = "Mute or Unmute"
    const val ACTIVE_MUSIC_OVERLAY = "Active Music Overlay"
    const val LOCK = "Lock"
    const val HIDE_HANDLER = "Hide Handler"
    const val OPEN_APP = "Open App"

    /** NEW in 1.2.9. Assignable to single/double/long tap. Requires WRITE_SETTINGS. */
    const val TOGGLE_AUTO_BRIGHTNESS = "Toggle auto brightness"

    /**
     * NEW in 1.2.9. Long press only, and the default there.
     *
     * Unlike every other identifier this one is not "run something on release" — it arms
     * drag-to-reposition for the rest of the gesture. Binding it to the long press is what lets a
     * plain vertical swipe stay dedicated to volume/brightness: the two no longer share the same
     * gesture, so neither can steal the other.
     */
    const val REPOSITION = "Reposition handler"

    /** True when this swipe identifier drives screen brightness rather than media volume. */
    fun isBrightnessSwipe(action: String): Boolean =
        action == INCREASE_BRIGHTNESS || action == DECREASE_BRIGHTNESS

    /** True when the action needs WRITE_SETTINGS before it can do anything. */
    fun needsWriteSettings(action: String): Boolean =
        isBrightnessSwipe(action) || action == TOGGLE_AUTO_BRIGHTNESS

    /** True when the volume change should surface the system volume panel. */
    fun showsVolumeUi(action: String): Boolean =
        action == INCREASE_VOLUME_UI || action == DECREASE_VOLUME_UI

    /** True when the swipe is configured to do nothing. */
    fun isDisabled(action: String): Boolean = action == NONE
}
