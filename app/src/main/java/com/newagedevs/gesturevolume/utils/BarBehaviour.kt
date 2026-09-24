package com.newagedevs.gesturevolume.utils

/**
 * The values two of the bar's behaviour settings take: how far a swipe moves the volume, and what
 * the bar does while the keyboard is open.
 *
 * Out here rather than beside their keys in [com.newagedevs.gesturevolume.data.local.SharedPref],
 * whose constants are private, because the settings screens offer these and the overlay acts on
 * them. Like [HandlerActions], they are a persistence format: written verbatim, never renamed.
 */
object BarBehaviour {

    /** A swipe moves one step per finger's width of travel, as the bar always has. */
    const val SWIPE_STEP_BY_LENGTH = 0

    /** [SWIPE_STEP_BY_LENGTH], then the fixed amounts one swipe may move, in percent of the range. */
    val SWIPE_STEP_PERCENTS: List<Int> = listOf(SWIPE_STEP_BY_LENGTH, 5, 10, 20)

    /** Lift the bar clear of the keyboard while it is open. */
    const val KEYBOARD_MOVE = "move"

    /** Put the bar away while the keyboard is open. */
    const val KEYBOARD_HIDE = "hide"

    /** Leave the bar where it is. */
    const val KEYBOARD_STAY = "stay"

    val KEYBOARD_BEHAVIOURS: List<String> = listOf(KEYBOARD_MOVE, KEYBOARD_HIDE, KEYBOARD_STAY)
}
