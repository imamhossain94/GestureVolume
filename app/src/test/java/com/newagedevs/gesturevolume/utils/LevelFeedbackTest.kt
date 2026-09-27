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

    @Test
    fun `the flourish at the top is one of seven, the burst unless another is chosen`() {
        assertEquals(7, LevelFeedback.MAX_STYLES.size)
        assertEquals(LevelFeedback.MAX_STYLES.size, LevelFeedback.MAX_STYLES.toSet().size)
        assertEquals(LevelFeedback.MAX_BURST, LevelFeedback.Style().max)
        assertEquals(LevelFeedback.MAX_BURST, LevelFeedback.sanitizeMax("bogus"))
        assertEquals(LevelFeedback.MAX_BURST, LevelFeedback.sanitizeMax(null))
        assertEquals(LevelFeedback.MAX_BURST, LevelFeedback.Style(max = "nope").sanitized().max)
        LevelFeedback.MAX_STYLES.forEach { assertEquals(it, LevelFeedback.Style(max = it).sanitized().max) }
    }

    @Test
    fun `every flourish's loop divides the feedback clock's hour, so its wrap is not seen`() {
        listOf(
            LevelFeedback.RIPPLE_LIFE_S, LevelFeedback.SHINE_S, LevelFeedback.SPARKLE_S, LevelFeedback.BEAT_S,
        ).forEach { period ->
            val turns = 3600f / period
            assertEquals("$period", Math.round(turns).toFloat(), turns, 1e-3f)
        }
    }

    @Test
    fun `a heartbeat throbs twice and rests`() {
        val samples = (0 until 240).map { LevelFeedback.heartbeat(it * LevelFeedback.BEAT_S / 240f) }
        samples.forEach { assertTrue(it in 0f..1f) }
        // Two peaks, the first the stronger, and a rest after them.
        val peaks = samples.indices.filter { i ->
            i in 1 until samples.size - 1 && samples[i] > samples[i - 1] && samples[i] >= samples[i + 1] && samples[i] > 0.3f
        }
        assertEquals(2, peaks.size)
        assertTrue(samples[peaks[0]] > samples[peaks[1]])
        assertTrue(samples.subList(160, 240).all { it < 0.05f })
        // The same a beat later.
        assertEquals(LevelFeedback.heartbeat(0.3f), LevelFeedback.heartbeat(0.3f + LevelFeedback.BEAT_S), 1e-4f)
    }

    @Test
    fun `a neon tube stutters as it strikes, then stays lit`() {
        val strike = (0 until 70).map { LevelFeedback.neonStrike(it * LevelFeedback.NEON_STRIKE_S / 70f) }
        assertTrue(strike.any { it < 0.3f })
        assertEquals(1f, LevelFeedback.neonStrike(LevelFeedback.NEON_STRIKE_S), 0f)
        assertEquals(1f, LevelFeedback.neonStrike(5f), 0f)
        // Opened already full: simply lit.
        assertEquals(1f, LevelFeedback.neonStrike(-1f), 0f)
    }

    @Test
    fun `stars twinkle half the time, each on its own beat`() {
        (0 until 7).forEach { star ->
            val samples = (0 until 180).map { LevelFeedback.twinkle(star, it * LevelFeedback.SPARKLE_S / 180f) }
            samples.forEach { assertTrue(it in 0f..1f) }
            val lit = samples.count { it > 0f }
            assertTrue("$star lit $lit", lit in 80..95)
        }
        // Not all at once.
        val now = (0 until 7).map { LevelFeedback.twinkle(it, 0.5f) }
        assertTrue(now.toSet().size > 3)
    }
}
