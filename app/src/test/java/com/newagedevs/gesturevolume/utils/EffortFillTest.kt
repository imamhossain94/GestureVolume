package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Effort fill's levels, colours and settings. */
class EffortFillTest {

    @Test
    fun `the effort fill is offered, takes colours, and keeps a clock of its own`() {
        assertTrue(SliderFill.EFFORT in SliderFill.ALL)
        assertEquals(SliderFill.EFFORT, SliderFill.sanitize(SliderFill.EFFORT))
        assertTrue(SliderFill.isAnimated(SliderFill.EFFORT))
        assertTrue(SliderFill.supportsCustomColors(SliderFill.EFFORT))
        assertFalse(SliderFill.hasWave(SliderFill.EFFORT))
    }

    @Test
    fun `the track is five equal levels, the top of it the top level`() {
        assertEquals(0, EffortFill.levelAt(0f))
        assertEquals(0, EffortFill.levelAt(0.19f))
        // On a boundary, the stop below: it is full, and the next has not begun.
        assertEquals(0, EffortFill.levelAt(0.2f))
        assertEquals(2, EffortFill.levelAt(0.6f))
        assertEquals(1, EffortFill.levelAt(0.21f))
        assertEquals(2, EffortFill.levelAt(0.5f))
        assertEquals(3, EffortFill.levelAt(0.79f))
        assertEquals(EffortFill.ULTRA, EffortFill.levelAt(0.81f))
        assertEquals(EffortFill.ULTRA, EffortFill.levelAt(1f))
        // Out of range clamps rather than inventing a level.
        assertEquals(0, EffortFill.levelAt(-0.3f))
        assertEquals(EffortFill.ULTRA, EffortFill.levelAt(1.7f))
    }

    @Test
    fun `every level counts up from the one below it`() {
        var last = -1
        for (step in 0..100) {
            val level = EffortFill.levelAt(step / 100f)
            assertTrue("$step% went from $last to $level", level == last || level == last + 1)
            last = level
        }
        assertEquals(EffortFill.ULTRA, last)
    }

    @Test
    fun `it works harder at every level up`() {
        for (level in 1 until EffortFill.LEVELS) {
            assertTrue(EffortFill.sweepSeconds(level) < EffortFill.sweepSeconds(level - 1))
            assertTrue(EffortFill.sweepStrength(level) > EffortFill.sweepStrength(level - 1))
        }
        assertFalse(EffortFill.glows(0))
        assertTrue(EffortFill.glows(EffortFill.ULTRA))
    }

    @Test
    fun `the palette has a colour for each level and the spectrum after them`() {
        val p = EffortFill.palette()
        assertEquals(EffortFill.SPECTRUM_FIRST + EffortFill.SPECTRUM_COUNT, p.size)
        p.forEach { assertEquals("opaque", 0xFFL, it ushr 24) }
        val mine = EffortFill.paletteWith(intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()))
        assertEquals(p.size, mine.size)
        assertEquals(0xFF0000, mine.first() and 0xFFFFFF)
        assertEquals(0x0000FF, mine.last() and 0xFFFFFF)
    }

    @Test
    fun `the clock's periods divide its wrap`() {
        listOf(EffortFill.GLOW_BREATH_SECONDS, EffortFill.SPECTRUM_SECONDS).forEach { period ->
            val turns = EffortFill.TIME_WRAP_S / period
            assertEquals("$period", Math.round(turns).toFloat(), turns, 1e-3f)
        }
    }

    @Test
    fun `settings are clamped and an unknown look falls back to steps`() {
        val wild = EffortFill.Style(look = "nonsense", speed = 99f, labels = false).sanitized()
        assertEquals(EffortFill.STEPS, wild.look)
        assertEquals(EffortFill.MAX_SPEED, wild.speed, 0f)
        assertFalse(wild.labels)
        assertEquals(EffortFill.MIN_SPEED, EffortFill.Style(speed = 0f).sanitized().speed, 0f)
        assertEquals(EffortFill.DEFAULT_SPEED, EffortFill.Style(speed = Float.NaN).sanitized().speed, 0f)
        EffortFill.LOOKS.forEach { assertEquals(it, EffortFill.sanitize(it)) }
    }
}
