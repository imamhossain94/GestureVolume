package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tab's geometry, checked without a device.
 *
 * `android.graphics.Path` is a stub in a JVM unit test and throws on the first call, which is the
 * reason the curve is a list of numbers in [HandlerShape] and only becomes a path inside the
 * drawable. What is worth asserting is the shape of those numbers: a sweep that starts on the
 * screen edge, ends on the inner face, and widens along a smoothstep rather than along a corner.
 */
class HandlerShapeTest {

    @Test
    fun `sanitize keeps known ids and rejects everything else`() {
        assertEquals(HandlerShape.ROUNDED, HandlerShape.sanitize(HandlerShape.ROUNDED))
        assertEquals(HandlerShape.TAB, HandlerShape.sanitize(HandlerShape.TAB))
        assertEquals(HandlerShape.ROUNDED, HandlerShape.sanitize(null))
        assertEquals(HandlerShape.ROUNDED, HandlerShape.sanitize("Tab"))
        assertEquals(HandlerShape.ROUNDED, HandlerShape.sanitize("hexagon"))
    }

    @Test
    fun `flare is clamped into a drawable range`() {
        assertEquals(HandlerShape.MIN_FLARE, HandlerShape.sanitizeFlare(-1f), 1e-6f)
        // The ceiling matters: past a half the two sweeps would cross and the outline would fold.
        assertEquals(HandlerShape.MAX_FLARE, HandlerShape.sanitizeFlare(0.9f), 1e-6f)
        assertEquals(0.2f, HandlerShape.sanitizeFlare(0.2f), 1e-6f)
        assertEquals(HandlerShape.DEFAULT_FLARE, HandlerShape.sanitizeFlare(Float.NaN), 1e-6f)
    }

    @Test
    fun `sweep runs from the screen edge to the inner face`() {
        val right = HandlerShape.tabSweep(40f, 200f, 0.25f, edgeOnLeft = false)
        assertEquals("starts on the right-hand edge", 40f, right[0], 1e-4f)
        assertEquals("starts at the top", 0f, right[1], 1e-4f)
        assertEquals("ends on the inner face", 0f, right[6], 1e-4f)
        assertEquals("ends one flare down", 50f, right[7], 1e-4f)

        val left = HandlerShape.tabSweep(40f, 200f, 0.25f, edgeOnLeft = true)
        assertEquals("starts on the left-hand edge", 0f, left[0], 1e-4f)
        assertEquals("ends on the inner face", 40f, left[6], 1e-4f)
        assertEquals("the same sweep, mirrored", right[7], left[7], 1e-4f)
    }

    @Test
    fun `an out-of-range flare cannot produce a sweep past the middle`() {
        val sweep = HandlerShape.tabSweep(40f, 200f, 5f, edgeOnLeft = false)
        assertEquals(100f, sweep[7], 1e-4f)
    }

    @Test
    fun `both control points sit on the two vertical sides`() {
        // This is what makes the width a smoothstep of the depth instead of a rounded corner:
        // the curve leaves the tip travelling straight down the edge and arrives travelling
        // straight down the inner face, so it blends into the straight section with no kink.
        val s = HandlerShape.tabSweep(40f, 200f, 0.25f, edgeOnLeft = false)
        assertEquals("first control on the edge", s[0], s[2], 1e-4f)
        assertEquals("second control on the inner face", s[6], s[4], 1e-4f)
    }

    @Test
    fun `the width follows a smoothstep down the sweep`() {
        val width = 40f
        val height = 200f
        val flare = 0.25f
        val s = HandlerShape.tabSweep(width, height, flare, edgeOnLeft = false)

        // Reference numbers, taken by measuring the silhouette an edge tab is expected to have:
        // a fifth of the way down it is a fifth as wide, and it is symmetric about the middle.
        // Tolerance is in percentage points of the full width.
        listOf(0.2f to 0.104f, 0.5f to 0.5f, 0.8f to 0.896f).forEach { (t, expected) ->
            assertEquals("width at t=$t", expected, widthFractionAt(s, t, width), 0.02f)
        }

        // Monotonic: the bar never narrows on its way out to full width.
        var previous = -1f
        for (i in 0..20) {
            val w = widthFractionAt(s, i / 20f, width)
            assertTrue("width goes backwards at t=${i / 20f}", w >= previous - 1e-4f)
            previous = w
        }
    }

    /** How wide the bar is, 0..1, at parameter [t] along the sweep's cubic. */
    private fun widthFractionAt(sweep: FloatArray, t: Float, width: Float): Float {
        val u = 1 - t
        val x = u * u * u * sweep[0] +
            3 * u * u * t * sweep[2] +
            3 * u * t * t * sweep[4] +
            t * t * t * sweep[6]
        // The edge is at x = width and the inner face at x = 0, so width grows as x falls.
        return (width - x) / width
    }
}
