package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two reports this exists for are both a write the bar believed and the audio did not keep:
 * "not increasing after 78%" is a refusal read as a success, and "falls back to a fixed level" is a
 * level put back a moment after it landed. These pin down how a read-back is judged, so neither can
 * be mistaken for a success again, and how a level put back is told from a press of the keys.
 */
class VolumeWriteTest {

    // ---- reading a write back ----------------------------------------------------------------------

    @Test
    fun `a write that reads back at its target landed`() {
        assertEquals(VolumeWrite.Outcome.LANDED, VolumeWrite.outcome(before = 10, target = 11, after = 11))
        assertEquals(VolumeWrite.Outcome.LANDED, VolumeWrite.outcome(before = 10, target = 7, after = 7))
    }

    @Test
    fun `an index that has not moved is a refusal, which is the safe-volume limit at the top`() {
        // 78% of a 9-step stream: index 7 asking for 8 and staying at 7.
        assertEquals(VolumeWrite.Outcome.REFUSED, VolumeWrite.outcome(before = 7, target = 8, after = 7))
    }

    @Test
    fun `moving the wrong way is a refusal too`() {
        assertEquals(VolumeWrite.Outcome.REFUSED, VolumeWrite.outcome(before = 7, target = 9, after = 6))
    }

    @Test
    fun `stopping short in the right direction is partial, a limiter clamping it`() {
        assertEquals(VolumeWrite.Outcome.PARTIAL, VolumeWrite.outcome(before = 5, target = 9, after = 7))
        assertEquals(VolumeWrite.Outcome.PARTIAL, VolumeWrite.outcome(before = 9, target = 3, after = 6))
    }

    @Test
    fun `overshooting is not partial`() {
        assertEquals(VolumeWrite.Outcome.REFUSED, VolumeWrite.outcome(before = 5, target = 7, after = 9))
    }

    @Test
    fun `an unreadable level says nothing either way`() {
        assertEquals(VolumeWrite.Outcome.UNKNOWN, VolumeWrite.outcome(before = 5, target = 6, after = null))
    }

    // ---- levels put back -----------------------------------------------------------------------------

    @Test
    fun `a level back where the swipe started has been put back`() {
        assertTrue(VolumeWrite.isRevert(start = 5, end = 9, now = 5))
        assertTrue(VolumeWrite.isRevert(start = 9, end = 4, now = 9))
    }

    @Test
    fun `most of the way back counts as put back`() {
        assertTrue(VolumeWrite.isRevert(start = 4, end = 12, now = 6))
    }

    @Test
    fun `a level still where the swipe left it is fine`() {
        assertFalse(VolumeWrite.isRevert(start = 5, end = 9, now = 9))
    }

    @Test
    fun `a key press after the swipe moves it on, not back, and is left alone`() {
        assertFalse(VolumeWrite.isRevert(start = 5, end = 9, now = 10))
        assertFalse(VolumeWrite.isRevert(start = 9, end = 5, now = 4))
    }

    @Test
    fun `a swipe that changed nothing has nothing to put back`() {
        assertFalse(VolumeWrite.isRevert(start = 6, end = 6, now = 3))
    }

    @Test
    fun `an unreadable level is never taken for a revert`() {
        assertFalse(VolumeWrite.isRevert(start = 5, end = 9, now = null))
    }

    // ---- routes ----------------------------------------------------------------------------------------

    @Test
    fun `a route is the stream, the kind of device and its name`() {
        assertEquals("s3:t8:Buds Pro", VolumeWrite.routeKey(3, 8, " Buds Pro "))
        assertEquals("s3:t2:", VolumeWrite.routeKey(3, 2, null))
    }

    @Test
    fun `two headsets of one kind are two routes`() {
        assertFalse(VolumeWrite.routeKey(3, 8, "Buds") == VolumeWrite.routeKey(3, 8, "Speaker"))
    }

    @Test
    fun `remembering a route moves it to the newest and keeps the list bounded`() {
        val many = (1..VolumeWrite.MAX_ROUTES).map { "r$it" }
        val next = VolumeWrite.remember(many, "new")
        assertEquals(VolumeWrite.MAX_ROUTES, next.size)
        assertEquals("new", next.last())
        assertFalse("r1" in next)

        val again = VolumeWrite.remember(listOf("a", "b", "c"), "a")
        assertEquals(listOf("b", "c", "a"), again)
    }

    // ---- the arithmetic ------------------------------------------------------------------------------

    @Test
    fun `a prediction stays inside the range`() {
        assertEquals(15, VolumeWrite.predict(current = 14, direction = 1, presses = 3, minIndex = 0, maxIndex = 15))
        assertEquals(1, VolumeWrite.predict(current = 2, direction = -1, presses = 5, minIndex = 1, maxIndex = 7))
        assertEquals(9, VolumeWrite.predict(current = 7, direction = 4, presses = 2, minIndex = 0, maxIndex = 15))
    }

    @Test
    fun `percent matches the readout arithmetic the bar always used`() {
        assertEquals(78, VolumeWrite.percentOf(index = 7, minIndex = 0, maxIndex = 9))
        assertEquals(0, VolumeWrite.percentOf(index = 1, minIndex = 1, maxIndex = 7))
        assertEquals(100, VolumeWrite.percentOf(index = 15, minIndex = 0, maxIndex = 15))
        assertEquals(100, VolumeWrite.percentOf(index = 20, minIndex = 0, maxIndex = 15))
    }

    @Test
    fun `an unknown method falls back to automatic`() {
        assertEquals(VolumeWrite.METHOD_AUTO, VolumeWrite.sanitizeMethod(null))
        assertEquals(VolumeWrite.METHOD_AUTO, VolumeWrite.sanitizeMethod("legacy"))
        assertEquals(VolumeWrite.METHOD_KEYS, VolumeWrite.sanitizeMethod(VolumeWrite.METHOD_KEYS))
    }
}
