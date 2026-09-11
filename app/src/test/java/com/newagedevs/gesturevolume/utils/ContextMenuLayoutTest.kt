package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/** The long-press menu's size, lines and pages, as the settings describe them. */
class ContextMenuLayoutTest {

    @Test
    fun `the default width is the three column grid it always was`() {
        assertEquals(3, ContextMenuLayout.columnsFor(ContextMenuLayout.DEFAULT_WIDTH_DP))
    }

    @Test
    fun `columns follow the width and stay usable at both ends`() {
        assertEquals(2, ContextMenuLayout.columnsFor(ContextMenuLayout.WIDTH_RANGE.start))
        assertEquals(5, ContextMenuLayout.columnsFor(ContextMenuLayout.WIDTH_RANGE.endInclusive))
        assertEquals(2, ContextMenuLayout.columnsFor(10f))
        assertEquals(5, ContextMenuLayout.columnsFor(2000f))
    }

    @Test
    fun `each layout keeps its own lines until some are picked`() {
        assertEquals(ContextMenuLayout.LINES_NONE, ContextMenuLayout.linesFor(null, grid = true))
        assertEquals(ContextMenuLayout.LINES_HORIZONTAL, ContextMenuLayout.linesFor(null, grid = false))
        assertEquals(ContextMenuLayout.LINES_NONE, ContextMenuLayout.linesFor("rubbish", grid = true))
    }

    @Test
    fun `a list keeps only the across lines of a grid choice`() {
        assertEquals(ContextMenuLayout.LINES_GRID, ContextMenuLayout.linesFor(ContextMenuLayout.LINES_GRID, grid = true))
        assertEquals(ContextMenuLayout.LINES_HORIZONTAL, ContextMenuLayout.linesFor(ContextMenuLayout.LINES_GRID, grid = false))
        assertEquals(ContextMenuLayout.LINES_NONE, ContextMenuLayout.linesFor(ContextMenuLayout.LINES_VERTICAL, grid = false))
        assertEquals(ContextMenuLayout.LINES_NONE, ContextMenuLayout.linesFor(ContextMenuLayout.LINES_NONE, grid = false))
    }

    @Test
    fun `an unknown page size means every entry on one page`() {
        assertEquals(ContextMenuLayout.PER_PAGE_ALL, ContextMenuLayout.sanitizePerPage(7))
        assertEquals(6, ContextMenuLayout.sanitizePerPage(6))
    }
}
