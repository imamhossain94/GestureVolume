package com.newagedevs.gesturevolume.overlay.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The coin toss's arithmetic: which face shows, that a toss lands on its result, and the tally. */
class CoinTossTest {

    @Test
    fun `the face shown follows the half-turn`() {
        assertTrue(CoinToss.showsHeads(0f))
        assertTrue(CoinToss.showsHeads(89f))
        assertFalse(CoinToss.showsHeads(91f))
        assertFalse(CoinToss.showsHeads(180f))
        assertFalse(CoinToss.showsHeads(269f))
        assertTrue(CoinToss.showsHeads(271f))
        assertTrue(CoinToss.showsHeads(360f))
        assertFalse(CoinToss.showsHeads(540f))
        assertTrue(CoinToss.showsHeads(1260f + 180f))
        assertTrue(CoinToss.showsHeads(-45f))
        assertFalse(CoinToss.showsHeads(-180f))
    }

    @Test
    fun `every toss lands on the face it was decided on`() {
        listOf(true, false).forEach { from ->
            listOf(true, false).forEach { to ->
                val start = CoinToss.restAngle(from)
                val end = start + CoinToss.halfTurns(from, to) * 180f
                assertEquals("from $from to $to", to, CoinToss.showsHeads(end))
                assertTrue(CoinToss.halfTurns(from, to) >= CoinToss.SAME_FACE_HALF_TURNS)
                // And the flight actually ends there.
                assertEquals(end, start + (end - start) * CoinToss.spin(1f), 1e-3f)
            }
        }
    }

    @Test
    fun `the spin starts fast and settles`() {
        assertEquals(0f, CoinToss.spin(0f), 1e-6f)
        assertEquals(1f, CoinToss.spin(1f), 1e-6f)
        assertTrue(CoinToss.spin(0.5f) > 0.5f)
        var last = -1f
        for (i in 0..100) {
            val v = CoinToss.spin(i / 100f)
            assertTrue(v >= last)
            last = v
        }
        // Decelerating: the last tenth covers far less than the first.
        assertTrue(CoinToss.spin(1f) - CoinToss.spin(0.9f) < CoinToss.spin(0.1f) - CoinToss.spin(0f))
    }

    @Test
    fun `the toss rises and comes back down`() {
        assertEquals(0f, CoinToss.lift(0f), 1e-6f)
        assertEquals(1f, CoinToss.lift(0.5f), 1e-6f)
        assertEquals(0f, CoinToss.lift(1f), 1e-6f)
        assertEquals(0f, CoinToss.lift(1.5f), 1e-6f)
    }

    @Test
    fun `the shading is brightest face-on`() {
        assertEquals(1f, CoinToss.facing(0f), 1e-4f)
        assertEquals(1f, CoinToss.facing(180f), 1e-4f)
        assertEquals(0f, CoinToss.facing(90f), 1e-4f)
    }

    @Test
    fun `the duration stays in range at every speed`() {
        listOf(0f, 0.4f, 1f, 2.5f, 10f, Float.NaN).forEach { speed ->
            val ms = CoinToss.flipDurationMs(speed)
            assertTrue("$speed → $ms", ms in CoinToss.MIN_FLIP_MS..CoinToss.MAX_FLIP_MS)
        }
        assertEquals(1100, CoinToss.flipDurationMs(1f))
    }

    @Test
    fun `the tally counts faces and runs`() {
        var tally = CoinTally()
        assertEquals(0, tally.total)
        assertEquals(0, tally.streak)

        listOf(true, true, false, false, false).forEach { tally = tally.record(it) }
        assertEquals(2, tally.heads)
        assertEquals(3, tally.tails)
        assertEquals(5, tally.total)
        assertEquals(false, tally.streakHeads)
        assertEquals(3, tally.streak)

        tally = tally.record(true)
        assertEquals(true, tally.streakHeads)
        assertEquals(1, tally.streak)
    }
}
