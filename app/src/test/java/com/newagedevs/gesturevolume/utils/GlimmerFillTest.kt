package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The Glimmer fill's grid, handle, light, loop and settings. */
class GlimmerFillTest {

    @Test
    fun `the glimmer is offered, takes colours, and keeps a clock of its own`() {
        assertTrue(SliderFill.GLIMMER in SliderFill.ALL)
        assertEquals(SliderFill.GLIMMER, SliderFill.sanitize(SliderFill.GLIMMER))
        assertTrue(SliderFill.isAnimated(SliderFill.GLIMMER))
        assertTrue(SliderFill.supportsCustomColors(SliderFill.GLIMMER))
        assertFalse(SliderFill.hasWave(SliderFill.GLIMMER))
        // Drawn over the whole track by its own art, not over the fill with the pictures.
        assertFalse(SliderFill.isPictorial(SliderFill.GLIMMER))
    }

    @Test
    fun `five dots go across the default panel, and more across a wider one`() {
        val density = 2.75f
        assertEquals(5, GlimmerFill.columnsFor(28f * density, density))
        assertTrue(GlimmerFill.columnsFor(48f * density, density) > 5)
        assertEquals(GlimmerFill.MIN_COLUMNS, GlimmerFill.columnsFor(1f, density))
        assertEquals(GlimmerFill.MAX_COLUMNS, GlimmerFill.columnsFor(10_000f, density))
    }

    @Test
    fun `the handle is four fifths of the track's width, within bounds`() {
        val d = 2f
        assertEquals(28f * d * GlimmerFill.HANDLE_RATIO, GlimmerFill.handleLength(28f * d, 600f, d), 1e-3f)
        assertEquals(GlimmerFill.HANDLE_MAX_DP * d, GlimmerFill.handleLength(72f * d, 600f, d), 1e-3f)
        assertEquals(GlimmerFill.HANDLE_MIN_DP * d, GlimmerFill.handleLength(4f * d, 600f, d), 1e-3f)
        // Never more than a third of a track too short for it.
        assertEquals(30f, GlimmerFill.handleLength(72f * d, 90f, d), 1e-3f)
    }

    @Test
    fun `the handle stays whole inside the track, and the finger stays on it`() {
        val length = 600f
        val handle = 60f
        assertEquals(handle / 2f, GlimmerFill.handleCentre(0f, length, handle), 1e-3f)
        assertEquals(length - handle / 2f, GlimmerFill.handleCentre(1f, length, handle), 1e-3f)
        for (i in 0..100) {
            val v = i / 100f
            // A finger picks v at v of the way along the whole track.
            val finger = v * length
            assertTrue("$v", abs(GlimmerFill.handleCentre(v, length, handle) - finger) <= handle / 2f + 1e-3f)
        }
        // Out of range clamps rather than leaving the track.
        assertEquals(handle / 2f, GlimmerFill.handleCentre(-1f, length, handle), 1e-3f)
        assertEquals(length - handle / 2f, GlimmerFill.handleCentre(2f, length, handle), 1e-3f)
    }

    @Test
    fun `six stops, evenly spaced, from the handle at nothing to the handle at full`() {
        val length = 600f
        val handle = 50f
        val stops = (0 until GlimmerFill.STOPS).map { GlimmerFill.stopAt(it, length, handle) }
        assertEquals(6, stops.size)
        assertEquals(GlimmerFill.handleCentre(0f, length, handle), stops.first(), 1e-3f)
        assertEquals(GlimmerFill.handleCentre(1f, length, handle), stops.last(), 1e-3f)
        val gaps = stops.zipWithNext { a, b -> b - a }
        gaps.forEach { assertEquals(gaps.first(), it, 1e-3f) }
    }

    @Test
    fun `the light rises from nothing at the start to all of it at the level`() {
        assertEquals(0f, GlimmerFill.ramp(0f), 0f)
        assertEquals(1f, GlimmerFill.ramp(1f), 0f)
        var last = -1f
        for (i in 0..50) {
            val r = GlimmerFill.ramp(i / 50f)
            assertTrue("dimmer at ${i / 50f}", r >= last)
            last = r
        }
        // Slow to begin, as the slider's first stretch is barely there.
        assertTrue(GlimmerFill.ramp(0.25f) < 0.2f)
        // Out of range clamps.
        assertEquals(0f, GlimmerFill.ramp(-1f), 0f)
        assertEquals(1f, GlimmerFill.ramp(3f), 0f)
    }

    @Test
    fun `every twinkle and the shimmer come back to where they started when the clock wraps`() {
        var worst = 0f
        for (c in 0 until 10) {
            for (r in 0 until 60) {
                val start = GlimmerFill.twinkle(c, r, 0.37f)
                val end = GlimmerFill.twinkle(c, r, GlimmerFill.TIME_WRAP_S + 0.37f)
                worst = maxOf(worst, abs(start - end))
            }
        }
        assertTrue("a twinkle jumps by $worst when the clock wraps", worst < 0.02f)
        for (i in 0..20) {
            val t = i / 20f
            assertEquals(GlimmerFill.shimmer(t, 1.1f), GlimmerFill.shimmer(t, GlimmerFill.TIME_WRAP_S + 1.1f), 0.02f)
        }
        listOf(GlimmerFill.TWINKLE_SECONDS, GlimmerFill.SHIMMER_SECONDS).forEach { period ->
            val turns = GlimmerFill.TIME_WRAP_S / period
            assertEquals("$period", Math.round(turns).toFloat(), turns, 1e-3f)
        }
    }

    @Test
    fun `most dots rest, and a few are caught twinkling`() {
        var caught = 0
        var total = 0
        for (c in 0 until 5) {
            for (r in 0 until 40) {
                for (k in 0 until 24) {
                    val twinkle = GlimmerFill.twinkle(c, r, 7f + k * 0.1f)
                    assertTrue("$twinkle", twinkle in 0f..1f)
                    if (twinkle > 0.25f) caught++
                    total++
                }
            }
        }
        // The slider has something like one dot in five lit up at any moment.
        val share = caught.toFloat() / total
        assertTrue("$share of the dots twinkling", share in 0.1f..0.3f)
    }

    @Test
    fun `the shimmer rises through the lit part and is gone at either end of its pass`() {
        // It crosses the middle once a pass...
        val middle = (0 until 48).map { GlimmerFill.shimmer(0.5f, it * GlimmerFill.SHIMMER_SECONDS / 48f) }
        assertTrue(middle.max() > 0.95f)
        assertTrue(middle.min() < 0.05f)
        // ...and starts below the start and ends past the level, so it never appears on the track.
        assertTrue(GlimmerFill.shimmer(0f, 0f) < 0.05f)
        assertTrue(GlimmerFill.shimmer(1f, GlimmerFill.SHIMMER_SECONDS * 0.9999f) < 0.05f)
    }

    @Test
    fun `it glimmers more the higher the level`() {
        assertTrue(GlimmerFill.energy(1f) > GlimmerFill.energy(0.5f))
        assertTrue(GlimmerFill.energy(0.5f) > GlimmerFill.energy(0f))
        assertTrue(GlimmerFill.energy(0f) > 0f)
    }

    @Test
    fun `the palette is three opaque colours, recoloured by the user's`() {
        val p = GlimmerFill.palette()
        assertEquals(3, p.size)
        p.forEach { assertEquals("opaque", 0xFFL, it ushr 24) }
        assertArrayEquals(IntArray(3) { p[it].toInt() }, GlimmerFill.paletteWith(null))
        val mine = GlimmerFill.paletteWith(intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()))
        assertEquals(3, mine.size)
        assertEquals(0xFF0000, mine.first() and 0xFFFFFF)
        assertEquals(0x0000FF, mine.last() and 0xFFFFFF)
    }

    @Test
    fun `settings are clamped, and out of the box it has the slider's handle and stops`() {
        val wild = GlimmerFill.Style(speed = 99f, handle = false, stops = false).sanitized()
        assertEquals(GlimmerFill.MAX_SPEED, wild.speed, 0f)
        assertFalse(wild.handle)
        assertFalse(wild.stops)
        assertEquals(GlimmerFill.MIN_SPEED, GlimmerFill.Style(speed = 0f).sanitized().speed, 0f)
        assertEquals(GlimmerFill.DEFAULT_SPEED, GlimmerFill.Style(speed = Float.NaN).sanitized().speed, 0f)
        assertTrue(GlimmerFill.Style().handle)
        assertTrue(GlimmerFill.Style().stops)
    }
}
