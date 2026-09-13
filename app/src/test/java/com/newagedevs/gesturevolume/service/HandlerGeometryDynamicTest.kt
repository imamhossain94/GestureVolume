package com.newagedevs.gesturevolume.service

import android.view.Surface
import com.newagedevs.gesturevolume.service.HandlerGeometry.Edge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HandlerGeometryDynamicTest {

    private fun frame(width: Int, height: Int, rotation: Int) =
        HandlerGeometry.Frame(width, height, 0, 0, 0, 0, 1f, rotation)

    @Test
    fun `a phone counts turns from portrait`() {
        assertEquals(0, HandlerGeometry.quarterTurns(frame(1080, 2400, Surface.ROTATION_0)))
        assertEquals(1, HandlerGeometry.quarterTurns(frame(2400, 1080, Surface.ROTATION_90)))
        assertEquals(2, HandlerGeometry.quarterTurns(frame(1080, 2400, Surface.ROTATION_180)))
        assertEquals(3, HandlerGeometry.quarterTurns(frame(2400, 1080, Surface.ROTATION_270)))
    }

    @Test
    fun `a landscape tablet counts turns from its portrait rotation`() {
        assertEquals(0, HandlerGeometry.quarterTurns(frame(1600, 2560, Surface.ROTATION_90)))
        assertEquals(3, HandlerGeometry.quarterTurns(frame(2560, 1600, Surface.ROTATION_0)))
        assertEquals(1, HandlerGeometry.quarterTurns(frame(2560, 1600, Surface.ROTATION_180)))
    }

    @Test
    fun `the right edge is the top at rotation 90, over the same stretch`() {
        // Measured on Edge Deck: portrait right edge, y 1081..1319, became top edge, x 1081..1319.
        val (edge, along) = HandlerGeometry.dynamicEdge(uprightIsLeft = false, along = 0.5f, turns = 1)
        assertEquals(Edge.TOP, edge)
        assertEquals(0.5f, along, 1e-6f)
    }

    @Test
    fun `left at the middle is top at the middle turned the other way`() {
        val (edge, along) = HandlerGeometry.dynamicEdge(uprightIsLeft = true, along = 0.5f, turns = 3)
        assertEquals(Edge.TOP, edge)
        assertEquals(0.5f, along, 1e-6f)
    }

    @Test
    fun `turned the other way the distance runs from the other end`() {
        val (edge, along) = HandlerGeometry.dynamicEdge(uprightIsLeft = false, along = 0.2f, turns = 3)
        assertEquals(Edge.BOTTOM, edge)
        assertEquals(0.8f, along, 1e-6f)
    }

    @Test
    fun `every placement comes back to where it was stored`() {
        for (turns in 0..3) for (left in listOf(true, false)) for (stored in listOf(0f, 0.23f, 0.5f, 1f)) {
            val (edge, along) = HandlerGeometry.dynamicEdge(left, stored, turns)
            val back = HandlerGeometry.uprightFromEdge(edge, along, turns)
            assertNotNull(back)
            assertEquals(left, back!!.first)
            assertEquals(stored, back.second, 1e-6f)
        }
    }

    @Test
    fun `the phone's own top is not an edge the bar rests on`() {
        assertNull(HandlerGeometry.uprightFromEdge(Edge.TOP, 0.3f, 0))
        assertNull(HandlerGeometry.uprightFromEdge(Edge.LEFT, 0.3f, 1))
    }
}
