package com.newagedevs.gesturevolume.utils

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wifi
import com.newagedevs.gesturevolume.R

/**
 * The presentation side of [HandlerActions]: the icon, label and grouping that go with each
 * identifier.
 *
 * [HandlerActions] holds the persistence format and the behavioural predicates and deliberately
 * knows nothing about resources. This is the other half, kept separate so the overlay — which
 * builds the long-press menu — and the Compose settings screens can share one list.
 *
 * Three surfaces read from here: the tap/long-press action dialog, the context-menu picker, and
 * the context menu the overlay actually shows. Adding an action in one place adds it to all three,
 * which is the point; when this list lived inside the dialog, nothing else could see it.
 */
object HandlerActionCatalog {

    /** How the picker groups the list. With forty entries a flat grid is a wall. */
    enum class Group(@param:StringRes val labelRes: Int) {
        /** The swipe-up and swipe-down pickers' own head group: see [swipeAdjust]. Never in [ALL]. */
        SWIPE(R.string.action_group_swipe),
        HANDLER(R.string.action_group_handler),
        VOLUME(R.string.action_group_volume),
        DECK(R.string.action_group_deck),
        DEVICE(R.string.action_group_device),
        MEDIA(R.string.action_group_media),
        SYSTEM(R.string.action_group_system)
    }

    data class Entry(
        val action: String,
        val icon: ActionIcon,
        @param:StringRes val labelRes: Int,
        val group: Group
    ) {
        /** Only the accessibility service can perform this one. */
        val needsAccessibility: Boolean get() = HandlerActions.needsAccessibility(action)
    }

    private fun res(id: Int) = ActionIcon.Res(id)

    /**
     * Every assignable action, in the order they are offered to the user.
     *
     * The order is canonical and load-bearing for the context menu: entries must not move between
     * one long press and the next, or the user's thumb learns the wrong position.
     */
    val ALL: List<Entry> = listOf(
        Entry(HandlerActions.NONE, res(R.drawable.ic_nothing), R.string.action_none, Group.HANDLER),
        Entry(HandlerActions.REPOSITION, res(R.drawable.ic_move), R.string.action_reposition, Group.HANDLER),
        Entry(HandlerActions.OPEN_DECK, ActionIcon.Vector(Icons.Filled.ViewSidebar), R.string.action_open_deck, Group.DECK),
        Entry(HandlerActions.OPEN_MENU, ActionIcon.Vector(Icons.Filled.Menu), R.string.action_open_menu, Group.HANDLER),
        Entry(HandlerActions.OPEN_QUICK_SLIDER, ActionIcon.QuickPanel, R.string.action_open_quick_slider, Group.VOLUME),
        Entry(HandlerActions.OPEN_VOLUME_UI, res(R.drawable.ic_vol_increase), R.string.action_open_volume_ui, Group.VOLUME),
        Entry(HandlerActions.MUTE, res(R.drawable.ic_mute), R.string.action_mute, Group.VOLUME),
        Entry(HandlerActions.MUTE_OR_UNMUTE, res(R.drawable.ic_mute), R.string.action_mute_unmute, Group.VOLUME),
        Entry(HandlerActions.TOGGLE_AUTO_BRIGHTNESS, res(R.drawable.ic_brightness_auto), R.string.action_toggle_auto_brightness, Group.DEVICE),
        Entry(HandlerActions.ACTIVE_MUSIC_OVERLAY, res(R.drawable.ic_music_ui), R.string.action_music_overlay, Group.VOLUME),
        Entry(HandlerActions.OPEN_SEARCH, ActionIcon.Vector(Icons.Filled.Search), R.string.action_open_search, Group.DECK),
        Entry(HandlerActions.OPEN_TIMER, ActionIcon.Vector(Icons.Filled.Timer), R.string.action_open_timer, Group.DECK),
        Entry(HandlerActions.OPEN_CALCULATOR, ActionIcon.Vector(Icons.Filled.Calculate), R.string.action_open_calculator, Group.DECK),
        Entry(HandlerActions.OPEN_NOTES, ActionIcon.Vector(Icons.Filled.EditNote), R.string.action_open_notes, Group.DECK),
        Entry(HandlerActions.OPEN_MEDIA, ActionIcon.Vector(Icons.Filled.MusicNote), R.string.action_open_media, Group.DECK),
        Entry(HandlerActions.COIN_TOSS, ActionIcon.Vector(Icons.Filled.Paid), R.string.action_coin_toss, Group.DECK),
        Entry(HandlerActions.DICE_ROLL, ActionIcon.Vector(Icons.Filled.Casino), R.string.action_dice_roll, Group.DECK),
        Entry(HandlerActions.SCAN_QR, ActionIcon.Vector(Icons.Filled.QrCodeScanner), R.string.action_scan_qr, Group.DECK),
        // The tile that asks which app; what is stored is HandlerActions.launchApp(package).
        Entry(HandlerActions.LAUNCH_APP, ActionIcon.Vector(Icons.Filled.Apps), R.string.action_launch_app, Group.DECK),
        Entry(HandlerActions.TOGGLE_FLASHLIGHT, ActionIcon.Vector(Icons.Filled.FlashlightOn), R.string.action_toggle_flashlight, Group.DEVICE),
        Entry(HandlerActions.TOGGLE_DND, ActionIcon.Vector(Icons.Filled.DoNotDisturbOn), R.string.action_toggle_dnd, Group.DEVICE),
        Entry(HandlerActions.TOGGLE_AUTO_ROTATE, ActionIcon.Vector(Icons.Filled.ScreenRotation), R.string.action_toggle_auto_rotate, Group.DEVICE),
        Entry(HandlerActions.RING_VIBRATE, ActionIcon.Vector(Icons.Filled.Vibration), R.string.action_ring_vibrate, Group.DEVICE),
        Entry(HandlerActions.WIFI_PANEL, ActionIcon.Vector(Icons.Filled.Wifi), R.string.action_wifi_panel, Group.DEVICE),
        Entry(HandlerActions.BLUETOOTH_SETTINGS, ActionIcon.Vector(Icons.Filled.Bluetooth), R.string.action_bluetooth_settings, Group.DEVICE),
        Entry(HandlerActions.INTERNET_PANEL, ActionIcon.Vector(Icons.Filled.NetworkCell), R.string.action_internet_panel, Group.DEVICE),
        Entry(HandlerActions.OPEN_CAMERA, ActionIcon.Vector(Icons.Filled.PhotoCamera), R.string.action_open_camera, Group.DEVICE),
        Entry(HandlerActions.VOICE_ASSISTANT, ActionIcon.Vector(Icons.Filled.KeyboardVoice), R.string.action_voice_assistant, Group.DEVICE),
        Entry(HandlerActions.MEDIA_PLAY_PAUSE, ActionIcon.Vector(Icons.Filled.PlayArrow), R.string.action_media_play_pause, Group.MEDIA),
        Entry(HandlerActions.MEDIA_NEXT, ActionIcon.Vector(Icons.Filled.SkipNext), R.string.action_media_next, Group.MEDIA),
        Entry(HandlerActions.MEDIA_PREVIOUS, ActionIcon.Vector(Icons.Filled.SkipPrevious), R.string.action_media_previous, Group.MEDIA),
        Entry(HandlerActions.LOCK, ActionIcon.Vector(Icons.Filled.Lock), R.string.action_lock, Group.SYSTEM),
        Entry(HandlerActions.SCREENSHOT, ActionIcon.Vector(Icons.Filled.Screenshot), R.string.action_screenshot, Group.SYSTEM),
        Entry(HandlerActions.BACK, ActionIcon.Vector(Icons.AutoMirrored.Filled.ArrowBack), R.string.action_back, Group.SYSTEM),
        Entry(HandlerActions.HOME, ActionIcon.Vector(Icons.Filled.Home), R.string.action_home, Group.SYSTEM),
        Entry(HandlerActions.RECENTS, ActionIcon.Vector(Icons.Filled.ViewCarousel), R.string.action_recents, Group.SYSTEM),
        Entry(HandlerActions.NOTIFICATIONS, ActionIcon.Vector(Icons.Filled.Notifications), R.string.action_notifications, Group.SYSTEM),
        Entry(HandlerActions.QUICK_SETTINGS, ActionIcon.Vector(Icons.Filled.Tune), R.string.action_quick_settings, Group.SYSTEM),
        Entry(HandlerActions.POWER_MENU, ActionIcon.Vector(Icons.Filled.PowerSettingsNew), R.string.action_power_menu, Group.SYSTEM),
        Entry(HandlerActions.HIDE_HANDLER, res(R.drawable.ic_visibility_hide), R.string.action_hide_handler, Group.HANDLER),
        Entry(HandlerActions.STOP_SERVICE, res(R.drawable.ic_power), R.string.action_stop_service, Group.HANDLER),
        Entry(HandlerActions.OPEN_APP, res(R.drawable.ic_app_open), R.string.action_open_app, Group.HANDLER)
    )

    /**
     * What the long-press menu may offer.
     *
     * [HandlerActions.NONE] is excluded because a menu entry that does nothing is not a choice,
     * [HandlerActions.REPOSITION] because the menu is opened by the very gesture that repositions —
     * holding and dragging already moves the bar — and [HandlerActions.OPEN_MENU] because a menu
     * entry that opens the menu is the menu.
     */
    val CONTEXT_MENU_CANDIDATES: List<Entry> = ALL.filterNot {
        it.action == HandlerActions.NONE ||
            it.action == HandlerActions.REPOSITION ||
            it.action == HandlerActions.OPEN_MENU ||
            // The menu stores bare identifiers, and this one needs an app to go with it.
            it.action == HandlerActions.LAUNCH_APP
    }

    private val byAction: Map<String, Entry> = ALL.associateBy { it.action }

    fun entryFor(action: String): Entry? = byAction[action]

    /**
     * What a vertical swipe can do *as it goes*: the volume or brightness bindings, where the
     * length of the stroke is the size of the change, for one direction.
     *
     * Kept out of [ALL] because they only mean something on a vertical swipe — a tap has no length
     * to steer by — and so they head the swipe pickers as a group of their own, above the whole
     * catalog, which a swipe can fire once like any other gesture.
     */
    fun swipeAdjust(isSwipeUp: Boolean): List<Entry> = if (isSwipeUp) SWIPE_UP_ADJUST else SWIPE_DOWN_ADJUST

    private val SWIPE_UP_ADJUST: List<Entry> = listOf(
        Entry(HandlerActions.INCREASE_VOLUME_UI, res(R.drawable.ic_vol_increase), R.string.action_increase_vol_ui, Group.SWIPE),
        Entry(HandlerActions.INCREASE_VOLUME, res(R.drawable.ic_vol_plus), R.string.action_increase_vol, Group.SWIPE),
        Entry(HandlerActions.INCREASE_BRIGHTNESS, res(R.drawable.ic_brightness_up), R.string.action_increase_brightness, Group.SWIPE),
    )

    private val SWIPE_DOWN_ADJUST: List<Entry> = listOf(
        Entry(HandlerActions.DECREASE_VOLUME_UI, res(R.drawable.ic_vol_decrease), R.string.action_decrease_vol_ui, Group.SWIPE),
        Entry(HandlerActions.DECREASE_VOLUME, res(R.drawable.ic_vol_minus), R.string.action_decrease_vol, Group.SWIPE),
        Entry(HandlerActions.DECREASE_BRIGHTNESS, res(R.drawable.ic_brightness_down), R.string.action_decrease_brightness, Group.SWIPE),
    )

    /**
     * The entry for any identifier a slot can hold, the swipe-only ones included — for the rows
     * and icons that show what a slot is set to. [entryFor] stays the catalog proper, which the
     * long-press menu is built from.
     */
    fun displayEntryFor(action: String): Entry? {
        HandlerActions.launchedPackage(action)?.let { pkg ->
            return Entry(action, ActionIcon.App(pkg), R.string.action_launch_app, Group.DECK)
        }
        return byAction[action] ?: (SWIPE_UP_ADJUST + SWIPE_DOWN_ADJUST).firstOrNull { it.action == action }
    }

    /**
     * What a tap, double tap, triple tap, long press or horizontal swipe may be bound to.
     *
     * @param allowReposition true for the long press only: repositioning needs a gesture the user
     *   holds, so it would be meaningless — and unusable — on a tap or a swipe.
     */
    fun assignable(allowReposition: Boolean): List<Entry> =
        ALL.filter { allowReposition || it.action != HandlerActions.REPOSITION }

    /** [assignable], split into its groups, in canonical order. Empty groups are dropped. */
    fun grouped(allowReposition: Boolean): List<Pair<Group, List<Entry>>> =
        Group.entries.mapNotNull { group ->
            val entries = assignable(allowReposition).filter { it.group == group }
            if (entries.isEmpty()) null else group to entries
        }

    /**
     * The candidates the user has switched on, in [ALL] order regardless of set iteration order.
     *
     * [HandlerActions.ALWAYS_IN_CONTEXT_MENU] is unioned in, so the menu can never lose the only
     * way to put the bar away.
     */
    /**
     * The menu's rows, in the user's own order.
     *
     * Entries the user has arranged come first, in their order; anything in
     * [HandlerActions.ALWAYS_IN_CONTEXT_MENU] that they have not placed is appended. Appended
     * rather than inserted at a fixed index, because the pinned entry is the escape hatch — Hide
     * handler — and the bottom of the menu is where an escape hatch is least likely to be hit by
     * a thumb reaching for something else.
     */
    fun contextMenuEntries(order: List<String>): List<Entry> {
        val placed = order.mapNotNull { byAction[it] }.filter { it in CONTEXT_MENU_CANDIDATES }
        val missing = HandlerActions.ALWAYS_IN_CONTEXT_MENU
            .filterNot { pinned -> placed.any { it.action == pinned } }
            .mapNotNull { byAction[it] }
        return placed + missing
    }
}
