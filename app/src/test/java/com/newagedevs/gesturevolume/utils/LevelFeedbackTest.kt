package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How every fill answers the level: the fifths, the pace, the flashes and the settings. */
class LevelFeedbackTest {

    @Test
    fun `the track is in fifths, a boundary belonging to the one it fills`() {
        assertEquals(0, LevelFeedback.levelAt(0f))
        assertEquals(0, LevelFeedback.levelAt(0.2f))
        assertEquals(1, LevelFeedback.levelAt(0.21f))
        assertEquals(3, LevelFeedback.levelAt(0.8f))
        assertEquals(4, LevelFeedback.levelAt(0.81f))
        assertEquals(4, LevelFeedback.levelAt(1f))
        assertEquals(4, LevelFeedback.levelAt(7f))
        assertEquals(0, LevelFeedback.levelAt(-1f))
    }

    @Test
    fun `low, full and the stretch between`() {
        assertTrue(LevelFeedback.isLow(0f))
        assertTrue(LevelFeedback.isLow(0.1f))
        assertFalse(LevelFeedback.isLow(0.5f))
        assertTrue(LevelFeedback.isFull(1f))
        assertTrue(LevelFeedback.isFull(0.996f))
        assertFalse(LevelFeedback.isFull(0.98f))
    }

    @Test
    fun `the fill works harder the higher it is, and keeps its own pace when told to`() {
        assertEquals(LevelFeedback.MIN_PACE, LevelFeedback.pace(0f, follow = true), 1e-6f)
        assertEquals(LevelFeedback.MAX_PACE, LevelFeedback.pace(1f, follow = true), 1e-6f)
        var last = 0f
        for (i in 0..20) {
            val pace = LevelFeedback.pace(i / 20f, follow = true)
            assertTrue(pace >= last)
            last = pace
        }
        assertEquals(1f, LevelFeedback.pace(0.1f, follow = false), 0f)
        assertEquals(1f, LevelFeedback.pace(0.9f, follow = false), 0f)
    }

    @Test
    fun `the sheen is quicker and brighter at every fifth`() {
        for (level in 1 until LevelFeedback.LEVELS) {
            assertTrue(LevelFeedback.sheenSeconds(level) < LevelFeedback.sheenSeconds(level - 1))
            assertTrue(LevelFeedback.sheenStrength(level) > LevelFeedback.sheenStrength(level - 1))
        }
    }

    @Test
    fun `a flash is all there at once and gone by its end`() {
        val length = LevelFeedback.STEP_FLASH_S
        assertEquals(1f, LevelFeedback.fade(0f, length), 1e-6f)
        assertTrue(LevelFeedback.fade(length / 2f, length) in 0.01f..0.99f)
        assertEquals(0f, LevelFeedback.fade(length, length), 0f)
        assertEquals(0f, LevelFeedback.fade(-1f, length), 0f)
        assertTrue(LevelFeedback.fade(0.1f, length) > LevelFeedback.fade(0.3f, length))
    }

    @Test
    fun `a breath goes from nothing to all and back, once a breath`() {
        assertEquals(0f, LevelFeedback.breath(0f), 1e-5f)
        assertEquals(1f, LevelFeedback.breath(LevelFeedback.BREATH_S / 2f), 1e-5f)
        assertEquals(0f, LevelFeedback.breath(LevelFeedback.BREATH_S), 1e-4f)
    }

    @Test
    fun `settings are on by default, and clamped`() {
        val d = LevelFeedback.Style()
        assertTrue(d.follow && d.low && d.full && d.drawsAnything)
        assertFalse(LevelFeedback.Style(follow = false, low = false, full = false).drawsAnything)
        assertEquals(LevelFeedback.MAX_SPEED, LevelFeedback.Style(speed = 99f).sanitized().speed, 0f)
        assertEquals(LevelFeedback.MIN_SPEED, LevelFeedback.Style(speed = 0f).sanitized().speed, 0f)
        assertEquals(LevelFeedback.DEFAULT_SPEED, LevelFeedback.Style(speed = Float.NaN).sanitized().speed, 0f)
    }
}
