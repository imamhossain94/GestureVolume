package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Pixels fill's patterns and settings. */
class PixelFillTest {

    private val phases = (0..24).map { it / 24f } + listOf(0.9999f, 1f, 1.37f, -0.2f)

    @Test
    fun `every pattern stays in range on every grid, at every phase`() {
        PixelFill.ALL.forEach { id ->
            for (columns in PixelFill.MIN_COLUMNS..PixelFill.MAX_COLUMNS) {
                val rows = 14
                phases.forEach { phase ->
                    for (row in 0 until rows) for (column in 0 until columns) {
                        val level = PixelFill.level(id, column, row, columns, rows, phase)
                        val tone = PixelFill.tone(id, column, row, columns, rows, phase)
                        assertTrue("$id level $level", level in 0f..1f)
                        assertTrue("$id tone $tone", tone in 0f..1f)
                    }
                }
            }
        }
    }

    @Test
    fun `every colour is opaque, the renderer's alpha being the only one`() {
        val custom = intArrayOf(0x00FF0000, 0x0000FF00, 0x000000FF)
        PixelFill.ALL.forEach { id ->
            listOf(null, custom).forEach { colours ->
                phases.forEach { phase ->
                    val colour = PixelFill.color(id, 1, 3, 3, 10, phase, 0x00123456, colours)
                    assertEquals("$id ${Integer.toHexString(colour)}", 0xFF, (colour ushr 24) and 0xFF)
                }
            }
        }
    }

    @Test
    fun `a phase and the same phase a whole cycle on draw the same frame`() {
        PixelFill.ALL.forEach { id ->
            listOf(0f, 0.25f, 0.61f).forEach { phase ->
                for (row in 0 until 8) for (column in 0 until 3) {
                    assertEquals(
                        id,
                        PixelFill.level(id, column, row, 3, 8, phase),
                        PixelFill.level(id, column, row, 3, 8, phase + 1f),
                        1e-4f,
                    )
                }
            }
        }
    }

    @Test
    fun `steady does not move, and the one-colour patterns are drawn in the fill colour`() {
        assertFalse(PixelFill.isAnimated(PixelFill.STEADY))
        assertEquals(1f, PixelFill.level(PixelFill.STEADY, 0, 0, 3, 5, 0.4f), 0f)
        listOf(PixelFill.STEADY, PixelFill.SNAKE, PixelFill.SWEEP).forEach { id ->
            assertFalse(PixelFill.supportsCustomColors(id))
            assertEquals(0xFF123456.toInt(), PixelFill.color(id, 0, 0, 3, 5, 0.2f, 0x00123456, intArrayOf(0xFF00FF00.toInt())))
        }
        assertTrue(PixelFill.supportsCustomColors(PixelFill.SPECTRUM))
    }

    @Test
    fun `the user's colours replace a colourful pattern's own`() {
        val green = 0xFF00FF00.toInt()
        val colour = PixelFill.color(PixelFill.METER, 0, 0, 3, 10, 0f, 0, intArrayOf(green))
        assertEquals(green, colour)
    }

    @Test
    fun `an unknown pattern reads as spectrum, and settings are clamped`() {
        assertEquals(PixelFill.SPECTRUM, PixelFill.sanitize("no such pattern"))
        assertEquals(PixelFill.SPECTRUM, PixelFill.sanitize(null))
        val wild = PixelFill.Style(
            pattern = "x", speed = 99f, columns = 40, gap = 3f, roundness = -1f, glow = Float.NaN, rest = 2f,
        ).sanitized()
        assertEquals(PixelFill.SPECTRUM, wild.pattern)
        assertEquals(PixelFill.MAX_SPEED, wild.speed, 0f)
        assertEquals(PixelFill.MAX_COLUMNS, wild.columns)
        assertEquals(PixelFill.MAX_GAP, wild.gap, 0f)
        assertEquals(0f, wild.roundness, 0f)
        assertEquals(PixelFill.DEFAULT_GLOW, wild.glow, 0f)
        assertEquals(PixelFill.MAX_REST, wild.rest, 0f)
    }

    @Test
    fun `speed shortens the cycle`() {
        val base = PixelFill.Style(pattern = PixelFill.SPECTRUM)
        assertEquals(PixelFill.cycleMs(PixelFill.SPECTRUM).toLong(), PixelFill.cycleMs(base))
        assertEquals(PixelFill.cycleMs(PixelFill.SPECTRUM) / 2L, PixelFill.cycleMs(base.copy(speed = 2f)))
    }

    @Test
    fun `spectrum is brightest where its wave is`() {
        val rows = 20
        // A quarter of the way through, the wave is a quarter of the way up.
        val atWave = PixelFill.level(PixelFill.SPECTRUM, 0, 4, 3, rows, 0.225f)
        val farAway = PixelFill.level(PixelFill.SPECTRUM, 0, 15, 3, rows, 0.225f)
        assertTrue("$atWave vs $farAway", atWave > 0.8f && farAway < 0.05f)
    }

    @Test
    fun `the Pixels fill is offered and takes the user's colours`() {
        assertTrue(SliderFill.PIXELS in SliderFill.ALL)
        assertEquals(SliderFill.PIXELS, SliderFill.sanitize(SliderFill.PIXELS))
        assertTrue(SliderFill.isAnimated(SliderFill.PIXELS))
        assertTrue(SliderFill.supportsCustomColors(SliderFill.PIXELS))
    }
}
