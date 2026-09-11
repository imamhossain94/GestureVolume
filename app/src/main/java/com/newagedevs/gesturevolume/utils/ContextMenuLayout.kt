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

    /**
     * How wide the card is, in dp. One width for both layouts: the grid fits as many columns
     * across it as its tiles allow, and the list's rows run the full width.
     */
    const val DEFAULT_WIDTH_DP = 238f
    val WIDTH_RANGE = 180f..360f

    /** The tallest the card grows before its entries scroll, in dp. */
    const val DEFAULT_HEIGHT_DP = 520f
    val HEIGHT_RANGE = 200f..680f

    /** The card's inner padding, in dp. The overlay draws it; the column count has to know it. */
    const val PADDING_DP = 8f

    /** The narrowest a grid tile may get before a column is dropped, in dp. */
    const val GRID_TILE_MIN_DP = 64f

    /** How many columns a grid of this width holds. Three at the default width. */
    fun columnsFor(widthDp: Float): Int =
        ((widthDp - PADDING_DP * 2) / GRID_TILE_MIN_DP).toInt().coerceIn(2, 5)

    /** Which hairlines divide the entries. */
    const val LINES_NONE = "none"
    const val LINES_HORIZONTAL = "horizontal"
    const val LINES_VERTICAL = "vertical"
    const val LINES_GRID = "grid"
    val GRID_LINES = listOf(LINES_NONE, LINES_HORIZONTAL, LINES_VERTICAL, LINES_GRID)

    /** A list has one column, so nothing to draw between columns. */
    val LIST_LINES = listOf(LINES_NONE, LINES_HORIZONTAL)

    /**
     * The lines actually drawn: the stored choice, or each layout's own look for someone who has
     * never picked one — hairlines between the list's rows, nothing between the grid's tiles.
     */
    fun linesFor(stored: String?, grid: Boolean): String = when {
        stored == null || stored !in GRID_LINES -> if (grid) LINES_NONE else LINES_HORIZONTAL
        grid -> stored
        // In one column the across lines are all there is to keep.
        stored == LINES_GRID -> LINES_HORIZONTAL
        stored == LINES_VERTICAL -> LINES_NONE
        else -> stored
    }

    /** Every entry on one page, which scrolls. */
    const val PER_PAGE_ALL = 0
    val PER_PAGE_CHOICES = listOf(PER_PAGE_ALL, 4, 6, 8, 9, 12)

    fun sanitizePerPage(value: Int): Int = if (value in PER_PAGE_CHOICES) value else PER_PAGE_ALL
}

/**
 * How big the long-press menu is, what divides its entries, and how many a grid page holds.
 * Read once when the menu opens, and live on the settings screen's preview.
 */
data class ContextMenuStyle(
    val widthDp: Float = ContextMenuLayout.DEFAULT_WIDTH_DP,
    val maxHeightDp: Float = ContextMenuLayout.DEFAULT_HEIGHT_DP,
    /** A `LINES_` value, or null for the layout's own. See [ContextMenuLayout.linesFor]. */
    val lines: String? = null,
    /** Entries per grid page, or [ContextMenuLayout.PER_PAGE_ALL] for one scrolling page. */
    val perPage: Int = ContextMenuLayout.PER_PAGE_ALL,
)
