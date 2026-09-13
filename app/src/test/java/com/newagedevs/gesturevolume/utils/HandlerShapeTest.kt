package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
    fun `the outline runs tip to tip down the inner face`() {
        val right = HandlerShape.tabOutline(40f, 200f, 0.25f, edgeOnLeft = false)
        assertEquals("starts on the right-hand edge", 40f, right[0], 1e-3f)
        assertEquals("starts at the top", 0f, right[1], 1e-3f)
        assertEquals("ends on the right-hand edge", 40f, right[right.size - 2], 1e-3f)
        assertEquals("ends at the bottom", 200f, right[right.size - 1], 1e-3f)

        val left = HandlerShape.tabOutline(40f, 200f, 0.25f, edgeOnLeft = true)
        assertEquals("starts on the left-hand edge", 0f, left[0], 1e-3f)
        assertEquals("ends on the left-hand edge", 0f, left[left.size - 2], 1e-3f)
    }

    @Test
    fun `the outline reaches the inner face and turns the corner there`() {
        val width = 40f
        val height = 200f
        val flare = 0.25f
        val o = HandlerShape.tabOutline(width, height, flare, edgeOnLeft = false)
        val depth = HandlerShape.tabSweepDepth(height, flare)
        assertEquals(50f, depth, 1e-3f)

        // The two points that land exactly on the face are the ends of the two sweeps, and the
        // straight run between them needs no samples of its own — which is the point of a
        // polyline. Nearby points are *nearly* on the face and deliberately so: a profile that
        // arrived at full width abruptly would be the crease this shape exists to avoid.
        val steps = HandlerShape.OUTLINE_STEPS
        assertEquals("top sweep ends off the face", 0f, o[steps * 2], 1e-4f)
        assertEquals("top sweep ends at the wrong depth", depth, o[steps * 2 + 1], 1e-3f)
        assertEquals("straight run starts off the face", 0f, o[(steps + 1) * 2], 1e-4f)
        assertEquals(
            "straight run starts at the wrong depth",
            height - depth,
            o[(steps + 1) * 2 + 1],
            1e-3f,
        )

        var onFace = 0
        var i = 0
        while (i < o.size) {
            val x = o[i]
            val y = o[i + 1]
            if (x < 1e-4f) onFace++
            assertTrue("outline leaves the bar at y=$y", x >= -0.01f && x <= width + 0.01f)
            assertTrue("outline leaves the bar at x=$x", y >= -0.01f && y <= height + 0.01f)
            i += 2
        }
        assertTrue("the inner face is never reached", onFace >= 2)
    }

    @Test
    fun `an out-of-range flare cannot produce a sweep past the middle`() {
        assertEquals(100f, HandlerShape.tabSweepDepth(200f, 5f), 1e-4f)
    }

    @Test
    fun `the profile runs from nothing to full width without going backwards`() {
        assertEquals(0f, HandlerShape.tabProfile(0f), 1e-4f)
        assertEquals(1f, HandlerShape.tabProfile(1f), 1e-4f)
        var previous = -1f
        for (i in 0..100) {
            val v = HandlerShape.tabProfile(i / 100f)
            assertTrue("profile goes backwards at t=${i / 100f}", v >= previous - 1e-5f)
            assertTrue("profile leaves 0..1 at t=${i / 100f}", v >= -1e-4f && v <= 1.0001f)
            previous = v
        }
    }

    @Test
    fun `the profile meets the straight section without a crease`() {
        /*
         * The whole point of the blended quintic. A sweep can arrive travelling straight down and
         * still crease, because what creases is *curvature* changing abruptly — and the straight
         * section it joins has none. So the profile's second derivative has to die away as it
         * arrives, not merely its slope.
         *
         * A plain cubic smoothstep scores -6 here, which is the crease this replaced.
         */
        val e = 1e-3f
        val t = 1f - 2 * e
        val second =
            (HandlerShape.tabProfile(t + e) - 2 * HandlerShape.tabProfile(t) +
                HandlerShape.tabProfile(t - e)) / (e * e)
        assertTrue("curvature at the join is $second, which is a crease", abs(second) < 0.5f)

        val slope = (HandlerShape.tabProfile(1f) - HandlerShape.tabProfile(1f - e)) / e
        assertTrue("the sweep arrives at an angle (slope $slope)", abs(slope) < 0.05f)
    }

    @Test
    fun `the tip still has some life in it`() {
        // A quintic on its own is seamless at the join and far too timid here — barely two percent
        // wide a seventh of the way down. The shape this answers to is nearer a tenth.
        val atASeventh = HandlerShape.tabProfile(1f / 7f)
        assertTrue("the tip is too flat ($atASeventh)", atASeventh > 0.045f)
    }

    @Test
    fun `both ends of the outline are mirror images`() {
        val height = 200f
        val o = HandlerShape.tabOutline(40f, height, 0.25f, edgeOnLeft = false)
        val steps = HandlerShape.OUTLINE_STEPS
        for (k in 0..steps) {
            val topX = o[k * 2]
            // The bottom sweep is written in reverse, after the single point that carries the
            // straight run.
            val bottomX = o[(steps + 1) * 2 + 2 + (steps - k) * 2]
            assertEquals("ends disagree at step $k", topX, bottomX, 1e-3f)
        }
    }
}
