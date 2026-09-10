package com.newagedevs.gesturevolume.utils

/**
 * The action identifiers that are persisted in [com.newagedevs.gesturevolume.data.local.SharedPref]
 * and matched by the overlay controller.
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

    // ---- 1.4.0: the Deck, and everything that opens inside it ----------------------------------

    /** Opens the slide-out Deck panel beside the bar. */
    const val OPEN_DECK = "Open deck"

    /** Opens the long-press menu, from any gesture. */
    const val OPEN_MENU = "Open menu"

    /** Opens the Deck straight onto its search card. */
    const val OPEN_SEARCH = "Open search"
    const val OPEN_TIMER = "Open timer"
    const val OPEN_CALCULATOR = "Open calculator"
    const val OPEN_NOTES = "Open notes"
    const val OPEN_CLIPBOARD = "Open clipboard"
    const val OPEN_MEDIA = "Open media controls"
    const val COIN_TOSS = "Coin toss"
    const val DICE_ROLL = "Dice roll"
    const val SCAN_QR = "Scan QR code"
    const val SONG_SEARCH = "Identify song"

    // ---- 1.4.0: device toggles -----------------------------------------------------------------

    const val TOGGLE_FLASHLIGHT = "Toggle flashlight"

    /** Needs Do Not Disturb access, asked for at the moment the action is chosen. */
    const val TOGGLE_DND = "Toggle Do Not Disturb"

    /** Needs WRITE_SETTINGS, like the brightness actions. */
    const val TOGGLE_AUTO_ROTATE = "Toggle auto-rotate"
    const val MEDIA_PLAY_PAUSE = "Play or pause"
    const val MEDIA_NEXT = "Next track"
    const val MEDIA_PREVIOUS = "Previous track"

    // ---- 1.4.0: system actions, performed by the accessibility service ------------------------

    /**
     * Back from 1.3.4's retirement, and deliberately the same identifier it had before.
     *
     * Every install that chose it kept the string in its preferences — [sanitize] only ever
     * masked it on read — so bringing the identifier back is what gives those users the action
     * back without them touching a setting.
     */
    const val LOCK = "Lock"
    const val SCREENSHOT = "Screenshot"
    const val BACK = "Back"
    const val HOME = "Home"
    const val RECENTS = "Recent apps"
    const val NOTIFICATIONS = "Open notifications"
    const val QUICK_SETTINGS = "Open quick settings"
    const val POWER_MENU = "Power menu"

    /**
     * What the long-press menu offers before the user has chosen otherwise.
     *
     * Deliberately short. The menu opens on a gesture the user is holding, often one-handed, so
     * a handful of entries that can be hit without looking beat ten that need aiming. Everything
     * else is one toggle away in settings.
     */
    val DEFAULT_CONTEXT_MENU: Set<String> = setOf(
        OPEN_DECK,
        OPEN_VOLUME_UI,
        MUTE_OR_UNMUTE,
        HIDE_HANDLER,
        STOP_SERVICE,
        OPEN_APP
    )

    /**
     * Menu entries the user cannot end up without.
     *
     * Hiding the bar is reachable only from this menu, so an install whose menu has been pruned
     * down to colours-and-volume would have no way to put the overlay away at all. These are added
     * back to whatever the user selected rather than made unselectable, so the picker stays a plain
     * list of switches.
     */
    val ALWAYS_IN_CONTEXT_MENU: Set<String> = setOf(HIDE_HANDLER)

    /** Every identifier this build understands, swipe directions included. */
    val KNOWN: Set<String> = setOf(
        NONE,
        INCREASE_VOLUME, INCREASE_VOLUME_UI, DECREASE_VOLUME, DECREASE_VOLUME_UI,
        INCREASE_BRIGHTNESS, DECREASE_BRIGHTNESS,
        OPEN_VOLUME_UI, MUTE, MUTE_OR_UNMUTE, ACTIVE_MUSIC_OVERLAY, HIDE_HANDLER, OPEN_APP,
        STOP_SERVICE, TOGGLE_AUTO_BRIGHTNESS, REPOSITION,
        OPEN_DECK, OPEN_MENU, OPEN_SEARCH, OPEN_TIMER, OPEN_CALCULATOR, OPEN_NOTES, OPEN_CLIPBOARD,
        OPEN_MEDIA, COIN_TOSS, DICE_ROLL, SCAN_QR, SONG_SEARCH,
        TOGGLE_FLASHLIGHT, TOGGLE_DND, TOGGLE_AUTO_ROTATE,
        MEDIA_PLAY_PAUSE, MEDIA_NEXT, MEDIA_PREVIOUS,
        LOCK, SCREENSHOT, BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, POWER_MENU
    )

    /** The actions the accessibility service performs. Nothing else can. */
    val ACCESSIBILITY_ACTIONS: Set<String> = setOf(
        LOCK, SCREENSHOT, BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, POWER_MENU
    )

    /**
     * Maps a stored action identifier onto one this build still understands.
     *
     * Applied on read in [com.newagedevs.gesturevolume.data.local.SharedPref] rather than as a
     * one-shot migration, because the preferences are also restored from cloud backup: a value
     * written by a newer build can arrive on an older one long after any migration would have
     * run. Reading defensively costs a set lookup and cannot be outrun.
     */
    fun sanitize(action: String): String = if (action in KNOWN) action else NONE

    /** The set form, for the long-press menu's stored selection. */
    fun sanitize(actions: Set<String>): Set<String> = actions.filterTo(mutableSetOf()) { it in KNOWN }

    /** True when this swipe identifier drives screen brightness rather than media volume. */
    fun isBrightnessSwipe(action: String): Boolean =
        action == INCREASE_BRIGHTNESS || action == DECREASE_BRIGHTNESS

    /** True when the action needs WRITE_SETTINGS before it can do anything. */
    fun needsWriteSettings(action: String): Boolean =
        isBrightnessSwipe(action) || action == TOGGLE_AUTO_BRIGHTNESS || action == TOGGLE_AUTO_ROTATE

    /** True when the action can only be carried out by the accessibility service. */
    fun needsAccessibility(action: String): Boolean = action in ACCESSIBILITY_ACTIONS

    /** True when the action needs Do Not Disturb access. */
    fun needsNotificationPolicy(action: String): Boolean = action == TOGGLE_DND

    /** True when the volume change should surface the system volume panel. */
    fun showsVolumeUi(action: String): Boolean =
        action == INCREASE_VOLUME_UI || action == DECREASE_VOLUME_UI

    /** True when the swipe is configured to do nothing. */
    fun isDisabled(action: String): Boolean = action == NONE

    /**
     * The Deck tile an action opens, or null when the action is not a Deck shortcut.
     *
     * The identifiers are [com.newagedevs.gesturevolume.overlay.deck.DeckTiles] ids. Kept here as
     * plain strings so this object stays free of overlay imports.
     */
    fun deckTileFor(action: String): String? = when (action) {
        OPEN_SEARCH -> "search"
        OPEN_TIMER -> "timer"
        OPEN_CALCULATOR -> "calculator"
        OPEN_NOTES -> "notes"
        OPEN_CLIPBOARD -> "clipboard"
        OPEN_MEDIA -> "media"
        COIN_TOSS -> "coin"
        DICE_ROLL -> "dice"
        else -> null
    }
}
