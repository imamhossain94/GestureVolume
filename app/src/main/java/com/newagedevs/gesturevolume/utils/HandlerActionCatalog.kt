package com.newagedevs.gesturevolume.utils

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.newagedevs.gesturevolume.R

/**
 * The presentation side of [HandlerActions]: the icon and label that go with each identifier.
 *
 * [HandlerActions] holds the persistence format and the behavioural predicates and deliberately
 * knows nothing about resources. This is the other half, kept separate so the service — which
 * builds the long-press menu as plain views — and the Compose settings screens can share one list.
 *
 * Three surfaces read from here: the tap/long-press action dialog, the context-menu picker, and
 * the context menu the overlay actually shows. Adding an action in one place adds it to all three,
 * which is the point; when this list lived inside the dialog, nothing else could see it.
 */
object HandlerActionCatalog {

    data class Entry(
        val action: String,
        @param:DrawableRes val iconRes: Int,
        @param:StringRes val labelRes: Int
    )

    /**
     * Every assignable action, in the order they are offered to the user.
     *
     * The order is canonical and load-bearing for the context menu: entries must not move between
     * one long press and the next, or the user's thumb learns the wrong position.
     */
    val ALL: List<Entry> = listOf(
        Entry(HandlerActions.NONE, R.drawable.ic_nothing, R.string.action_none),
        Entry(HandlerActions.REPOSITION, R.drawable.ic_move, R.string.action_reposition),
        Entry(HandlerActions.OPEN_VOLUME_UI, R.drawable.ic_vol_increase, R.string.action_open_volume_ui),
        Entry(HandlerActions.MUTE, R.drawable.ic_mute, R.string.action_mute),
        Entry(HandlerActions.MUTE_OR_UNMUTE, R.drawable.ic_mute, R.string.action_mute_unmute),
        Entry(HandlerActions.TOGGLE_AUTO_BRIGHTNESS, R.drawable.ic_brightness_auto, R.string.action_toggle_auto_brightness),
        Entry(HandlerActions.ACTIVE_MUSIC_OVERLAY, R.drawable.ic_music_ui, R.string.action_music_overlay),
        Entry(HandlerActions.LOCK, R.drawable.ic_lock, R.string.action_lock),
        Entry(HandlerActions.HIDE_HANDLER, R.drawable.ic_visibility_hide, R.string.action_hide_handler),
        Entry(HandlerActions.OPEN_APP, R.drawable.ic_app_open, R.string.action_open_app)
    )

    /**
     * What the long-press menu may offer.
     *
     * [HandlerActions.NONE] is excluded because a menu entry that does nothing is not a choice, and
     * [HandlerActions.REPOSITION] because the menu is opened by the very gesture that repositions —
     * holding and dragging already moves the bar, so listing it would be a slower second route to
     * the thing the user's finger is mid-way through doing.
     */
    val CONTEXT_MENU_CANDIDATES: List<Entry> = ALL.filterNot {
        it.action == HandlerActions.NONE || it.action == HandlerActions.REPOSITION
    }

    private val byAction: Map<String, Entry> = ALL.associateBy { it.action }

    fun entryFor(action: String): Entry? = byAction[action]

    /** The candidates the user has switched on, in [ALL] order regardless of set iteration order. */
    fun contextMenuEntries(selected: Set<String>): List<Entry> =
        CONTEXT_MENU_CANDIDATES.filter { it.action in selected }
}
