package com.newagedevs.gesturevolume.utils

/**
 * Which kind of user the app is set up for: the one who came for a volume button, or the one who
 * wants everything the bar can do.
 *
 * Chosen in the walkthrough and changeable from the home screen. It decides two things: which
 * preset the bar starts as — [HandlerPresets.CLASSIC], the round button the app began with, or
 * [HandlerPresets.DEFAULT], the Dock — and whether the home screen opens its advanced features or
 * keeps them folded away. Nothing is taken away from a regular user; it is only out of the way.
 *
 * The identifiers are a persistence format, never renamed.
 */
object UserMode {

    /** Swipe for volume, tap for the volume panel, hold to move. The app before the Quick panel. */
    const val REGULAR = "regular"

    /** The Dock, the Quick slider, the Deck and the long-press menu. */
    const val ADVANCED = "advanced"

    fun sanitize(value: String?): String = if (value == ADVANCED) ADVANCED else REGULAR

    /** The preset a user of [mode] starts from. */
    fun presetFor(mode: String): HandlerPresets.Preset =
        if (sanitize(mode) == ADVANCED) HandlerPresets.DEFAULT else HandlerPresets.CLASSIC
}
