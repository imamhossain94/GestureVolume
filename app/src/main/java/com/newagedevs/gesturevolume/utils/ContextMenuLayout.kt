package com.newagedevs.gesturevolume.utils

/**
 * How the long-press menu is drawn.
 *
 * Its own object rather than constants on `SharedPref`, whose companion is private — and rightly
 * so: these are read by the overlay that draws the menu and by the dialog that configures it,
 * neither of which should have to reach through the storage layer to learn what the two choices
 * are. Like [HandlerActions], the identifiers are a persistence format and are never renamed.
 */
object ContextMenuLayout {

    /** Icons in a three-across grid, read by shape. */
    const val GRID = "grid"

    /** Labelled rows, read by name. */
    const val LIST = "list"

    fun sanitize(value: String?): String = if (value == LIST) LIST else GRID
}
