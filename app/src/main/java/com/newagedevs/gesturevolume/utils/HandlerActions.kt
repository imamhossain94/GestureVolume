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
    const val HIDE_HANDLER = "Hide Handler"
    const val OPEN_APP = "Open App"

    /**
     * NEW in 1.3.2. Stops the service outright, rather than only putting the bar away.
     *
     * Added alongside [HIDE_HANDLER] because the long-press menu is now the only route out of the
     * overlay — the floating ✕ that used to appear mid-drag is gone — and "put it away for now"
     * and "turn it off" are different intentions that were sharing one entry.
     */
    const val STOP_SERVICE = "Stop service"

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

    /**
     * What the long-press menu offers before the user has chosen otherwise.
     *
     * Deliberately short. The menu opens on a gesture the user is holding, often one-handed, so
     * four entries that can be hit without looking beat ten that need aiming. Everything else is
     * one toggle away in settings.
     */
    val DEFAULT_CONTEXT_MENU: Set<String> = setOf(
        OPEN_VOLUME_UI,
        MUTE_OR_UNMUTE,
        HIDE_HANDLER,
        STOP_SERVICE,
        OPEN_APP
    )

    /**
     * Menu entries the user cannot end up without.
     *
     * Hiding the bar is now reachable only from this menu, so an install whose menu has been
     * pruned down to colours-and-volume would have no way to put the overlay away at all. These
     * are added back to whatever the user selected rather than made unselectable, so the picker
     * stays a plain list of switches.
     */
    val ALWAYS_IN_CONTEXT_MENU: Set<String> = setOf(HIDE_HANDLER)

    /**
     * Retired in 1.3.4, when the accessibility service that performed it was removed.
     *
     * The only route to a locked screen was `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` on a
     * bound [android.accessibilityservice.AccessibilityService], and shipping one made this app
     * subject to Play's Accessibility API policy — a policy written for apps whose *core*
     * functionality serves people with disabilities, which one optional action out of eleven is
     * not. Device Admin, the other route, was dropped earlier for taking the user's fingerprint
     * unlock away. With neither route left, the action is gone rather than present and inert.
     *
     * The identifier stays here because it is still written in the preferences of every install
     * that used it. [sanitize] is what reads those back out; nothing else should reference it.
     */
    private const val RETIRED_LOCK = "Lock"

    /**
     * Maps a stored action identifier onto one this build still understands.
     *
     * Applied on read in [com.newagedevs.gesturevolume.data.local.SharedPref] rather than as a
     * one-shot migration, because the preferences are also restored from cloud backup: a migration
     * that ran once at upgrade would miss an install that received "Lock" from a device where it
     * still existed. Reading defensively costs a string comparison and cannot be outrun.
     */
    fun sanitize(action: String): String = if (action == RETIRED_LOCK) NONE else action

    /** The set form, for the long-press menu's stored selection. */
    fun sanitize(actions: Set<String>): Set<String> = actions - RETIRED_LOCK

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
