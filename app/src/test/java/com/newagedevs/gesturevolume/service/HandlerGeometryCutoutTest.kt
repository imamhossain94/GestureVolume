package com.newagedevs.gesturevolume.service

import com.newagedevs.gesturevolume.service.HandlerGeometry.Box
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bar rests flush on the glass in every rotation now, so the one thing that can be in its way
 * is the camera — on a phone turned to the left, a punch hole on the left edge, often right where
 * the bar sits. These pin down that it steps along the edge just past the hole, never off the edge,
 * and only when it would actually be under it.
 */
class HandlerGeometryCutoutTest {

    /** A landscape phone, 2400 x 1080, camera on the left edge halfway down. */
    private val hole = Box(left = 30, top = 500, right = 90, bottom = 580)

    @Test
    fun `a bar clear of the camera stays where it is`() {
        val y = HandlerGeometry.clearOfCutouts(
            x = 0, y = 100, width = 84, height = 300,
            cutouts = listOf(hole), lying = false, frameLength = 1080,
        )
        assertEquals(100, y)
    }

    @Test
    fun `a bar on the other side of the screen is not moved by the camera`() {
        val y = HandlerGeometry.clearOfCutouts(
            x = 2316, y = 400, width = 84, height = 300,
            cutouts = listOf(hole), lying = false, frameLength = 1080,
        )
        assertEquals(400, y)
    }

    @Test
    fun `a bar under the camera steps to the nearer side of it`() {
        // Centred a little above the hole: going up is shorter.
        val y = HandlerGeometry.clearOfCutouts(
            x = 0, y = 360, width = 84, height = 300,
            cutouts = listOf(hole), lying = false, frameLength = 1080, gapPx = 10,
        )
        assertEquals(500 - 10 - 300, y)
    }

    @Test
    fun `a bar mostly below the camera steps down`() {
        val y = HandlerGeometry.clearOfCutouts(
            x = 0, y = 540, width = 84, height = 300,
            cutouts = listOf(hole), lying = false, frameLength = 1080, gapPx = 10,
        )
        assertEquals(580 + 10, y)
    }

    @Test
    fun `the nearer side is skipped when the bar would not fit there`() {
        // The hole near the top: above it there is no room for the bar.
        val high = Box(left = 30, top = 120, right = 90, bottom = 200)
        val y = HandlerGeometry.clearOfCutouts(
            x = 0, y = 100, width = 84, height = 300,
            cutouts = listOf(high), lying = false, frameLength = 1080,
        )
        assertEquals(200, y)
    }

    @Test
    fun `a bar too long to fit either side stays rather than leaving the screen`() {
        val y = HandlerGeometry.clearOfCutouts(
            x = 0, y = 300, width = 84, height = 900,
            cutouts = listOf(hole), lying = false, frameLength = 1080,
        )
        assertEquals(300, y)
    }

    @Test
    fun `a bar lying along the top steps along it`() {
        // Portrait, camera top centre, bar lying along the top edge across it.
        val notch = Box(left = 500, top = 0, right = 580, bottom = 90)
        val x = HandlerGeometry.clearOfCutouts(
            x = 450, y = 0, width = 300, height = 84,
            cutouts = listOf(notch), lying = true, frameLength = 1080,
        )
        assertEquals(580, x)
    }

    @Test
    fun `a bar lying along the bottom is not moved by a camera at the top`() {
        val notch = Box(left = 500, top = 0, right = 580, bottom = 90)
        val x = HandlerGeometry.clearOfCutouts(
            x = 450, y = 2316, width = 300, height = 84,
            cutouts = listOf(notch), lying = true, frameLength = 1080,
        )
        assertEquals(450, x)
    }

    @Test
    fun `stepping off one cutout onto another keeps going`() {
        val upper = Box(left = 0, top = 300, right = 60, bottom = 360)
        val lower = Box(left = 0, top = 500, right = 60, bottom = 560)
        val y = HandlerGeometry.clearOfCutouts(
            x = 0, y = 380, width = 84, height = 100,
            cutouts = listOf(upper, lower), lying = false, frameLength = 1080,
        )
        // Between the two there is room (360..500 is 140 > 100): it fits there already.
        assertEquals(380, y)

        val tight = HandlerGeometry.clearOfCutouts(
            x = 0, y = 480, width = 84, height = 100,
            cutouts = listOf(upper, lower), lying = false, frameLength = 1080,
        )
        // Under the lower one: up is 400 (clear of both), down is 560; up is nearer.
        assertEquals(400, tight)
    }

    // ---- carrying a stored place over to the whole glass ----------------------------------------

    @Test
    fun `a place stored below the camera strip stays where it was on the glass`() {
        // Portrait, 2400 tall with a 120px camera strip: the old frame was 2280 tall from y = 120.
        val old = 0.21f
        val glass = HandlerGeometry.toGlassFraction(old, inset = 120, oldLength = 2280, glassLength = 2400)
        // The bar's centre was at 120 + 0.21 * 2280 = 598.8 on the glass, and still is.
        assertEquals(598.8f, glass * 2400, 0.01f)
    }

    @Test
    fun `with no cutout the place is unchanged`() {
        assertEquals(0.37f, HandlerGeometry.toGlassFraction(0.37f, inset = 0, oldLength = 1080, glassLength = 1080), 1e-6f)
    }

    @Test
    fun `a place at the far end stays at the far end`() {
        // Landscape with the camera on the left: a bar on the right edge (1.0) is still on it.
        assertEquals(1f, HandlerGeometry.toGlassFraction(1f, inset = 110, oldLength = 2290, glassLength = 2400), 1e-6f)
    }

    @Test
    fun `a degenerate frame leaves the place alone`() {
        assertEquals(0.5f, HandlerGeometry.toGlassFraction(0.5f, inset = 10, oldLength = 0, glassLength = 100), 0f)
    }
}
