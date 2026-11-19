package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The Button looks fill: the ten looks, their colours, their beats and their curves. */
class ButtonLookFillTest {

    @Test
    fun `the button looks are offered, take colours, and keep a clock of their own`() {
        assertTrue(SliderFill.BUTTON_LOOKS in SliderFill.ALL)
        assertEquals(SliderFill.BUTTON_LOOKS, SliderFill.sanitize(SliderFill.BUTTON_LOOKS))
        assertTrue(SliderFill.isAnimated(SliderFill.BUTTON_LOOKS))
        assertTrue(SliderFill.supportsCustomColors(SliderFill.BUTTON_LOOKS))
        assertFalse(SliderFill.hasWave(SliderFill.BUTTON_LOOKS))
        // Drawn over the whole track by its own art, not over the fill with the pictures.
        assertFalse(SliderFill.isPictorial(SliderFill.BUTTON_LOOKS))
    }

    @Test
    fun `the ten looks the user asked for, in their order, each once`() {
        assertEquals(
            listOf("line", "neon", "ripple", "lava", "spectrum", "elastic", "heartbeat", "glass", "aurora", "jelly"),
            ButtonLookFill.LOOKS,
        )
        assertEquals(ButtonLookFill.LOOKS.size, ButtonLookFill.LOOKS.toSet().size)
        // Each one is also one of the button animation's own looks.
        ButtonLookFill.LOOKS.forEach { assertTrue(it, it in ButtonBump.STYLES) }
    }

    @Test
    fun `an unknown look falls back to the first`() {
        listOf(null, "", "water", "dragon").forEach {
            assertEquals("$it", ButtonLookFill.LINE, ButtonLookFill.sanitize(it))
        }
        assertEquals(ButtonLookFill.LINE, ButtonLookFill.Style(look = "nope").sanitized().look)
    }

    @Test
    fun `the settings are clamped to what can be drawn`() {
        val wild = ButtonLookFill.Style(speed = 99f, bounce = -3f).sanitized()
        assertEquals(ButtonLookFill.MAX_SPEED, wild.speed, 0f)
        assertEquals(0f, wild.bounce, 0f)
        val nan = ButtonLookFill.Style(speed = Float.NaN, bounce = Float.NaN).sanitized()
        assertEquals(ButtonLookFill.DEFAULT_SPEED, nan.speed, 0f)
        assertEquals(ButtonLookFill.DEFAULT_BOUNCE, nan.bounce, 0f)
        assertEquals(ButtonLookFill.MIN_SPEED, ButtonLookFill.Style(speed = 0f).sanitized().speed, 0f)
    }

    @Test
    fun `every look has a dark and at least three lights`() {
        ButtonLookFill.LOOKS.forEach { id ->
            val p = ButtonLookFill.palette(id)
            assertTrue("$id has ${p.size} colours", p.size >= 4)
            assertTrue("$id's first colour is its dark", SliderFill.isGround(p[0]))
            for (i in 1 until p.size) assertFalse("$id slot $i is a light", SliderFill.isGround(p[i]))
            p.forEach { assertEquals("$id is opaque", 0xFFL, it ushr 24) }
        }
    }

    @Test
    fun `the user's colours recolour the lights and leave the dark`() {
        val mine = intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt())
        ButtonLookFill.LOOKS.forEach { id ->
            val own = ButtonLookFill.palette(id)
            val recoloured = ButtonLookFill.paletteWith(id, mine)
            assertEquals(id, own.size, recoloured.size)
            assertEquals("$id kept its dark", own[0].toInt(), recoloured[0])
            assertEquals("$id's last light is the last colour", 0x0000FF, recoloured.last() and 0xFFFFFF)
            val plain = ButtonLookFill.paletteWith(id, null)
            own.indices.forEach { assertEquals(own[it].toInt(), plain[it]) }
        }
    }

    @Test
    fun `a beat is every twentieth of the way`() {
        assertEquals(0, ButtonLookFill.beatBucket(0f))
        assertEquals(0, ButtonLookFill.beatBucket(0.049f))
        assertEquals(1, ButtonLookFill.beatBucket(0.05f))
        assertEquals(10, ButtonLookFill.beatBucket(0.5f))
        // Full is in the last twentieth, not one past it.
        assertEquals(ButtonLookFill.BEAT_STEPS - 1, ButtonLookFill.beatBucket(1f))
        assertEquals(0, ButtonLookFill.beatBucket(-1f))
        // A key step on fifteen steps always crosses one.
        for (i in 0 until 15) {
            val a = ButtonLookFill.beatBucket(i / 15f)
            val b = ButtonLookFill.beatBucket((i + 1) / 15f)
            assertTrue("step $i", a != b)
        }
    }

    @Test
    fun `the stretch starts whole, swings back past flat, and settles`() {
        assertEquals(1f, ButtonLookFill.spring(0f), 1e-4f)
        assertEquals(0f, ButtonLookFill.spring(-0.1f), 0f)
        // Somewhere in the first half second it is the other way.
        val swing = (1..50).map { ButtonLookFill.spring(it / 100f) }.minOrNull()!!
        assertTrue("swings back by $swing", swing < -0.1f)
        assertTrue(abs(ButtonLookFill.spring(1.1f)) < 0.01f)
        assertEquals(0f, ButtonLookFill.spring(5f), 0f)
    }

    @Test
    fun `the stretch pulls the middle and leaves the walls`() {
        assertEquals(1f, ButtonLookFill.bow(0.5f), 1e-6f)
        assertEquals(0f, ButtonLookFill.bow(0f), 1e-6f)
        assertEquals(0f, ButtonLookFill.bow(1f), 1e-6f)
        assertEquals(ButtonLookFill.bow(0.2f), ButtonLookFill.bow(0.8f), 1e-6f)
    }

    @Test
    fun `a ring travels out and fades away`() {
        assertEquals(0f, ButtonLookFill.ringTravel(0f), 0f)
        assertEquals(1f, ButtonLookFill.ringTravel(ButtonLookFill.RING_LIFE_S), 1e-6f)
        assertEquals(1f, ButtonLookFill.ringFade(0f), 0f)
        assertEquals(0f, ButtonLookFill.ringFade(ButtonLookFill.RING_LIFE_S), 1e-6f)
        var last = -1f
        for (i in 0..20) {
            val t = ButtonLookFill.ringTravel(i * ButtonLookFill.RING_LIFE_S / 20f)
            assertTrue(t >= last)
            last = t
        }
    }

    @Test
    fun `the heart throbs twice at a step and rests between`() {
        // At a step, the animation's throbs, scaled by the bounce.
        val atStep = ButtonLookFill.throb(time = 0.5f, sinceS = 0.06f, bounce = 1f)
        assertTrue(atStep > 0.5f)
        assertEquals(0f, ButtonLookFill.throb(time = 0.45f, sinceS = 0.06f, bounce = 0f), 0f)
        // Long after a step, a resting pulse, weaker than a step's.
        val resting = (0 until 90).maxOf { ButtonLookFill.throb(time = it / 100f, sinceS = 5f, bounce = 1f) }
        assertTrue("rests at $resting", resting in 0.1f..0.5f)
        // And it repeats exactly.
        assertEquals(
            ButtonLookFill.throb(0.1f, 5f, 1f),
            ButtonLookFill.throb(0.1f + ButtonLookFill.RESTING_BEAT_S, 5f, 1f),
            1e-4f,
        )
    }

    @Test
    fun `the lava's surface is two blobs, within bounds`() {
        for (t in listOf(0f, 1.3f, 7.7f)) {
            for (i in 0..20) {
                val h = ButtonLookFill.lavaSurface(i / 20f, t)
                assertTrue("at $i, $t: $h", h in 0f..1f)
            }
            // A blob is always up somewhere.
            assertTrue((0..20).maxOf { ButtonLookFill.lavaSurface(it / 20f, t) } > 0.6f)
        }
        // Together in the middle at one moment, apart towards the walls at another.
        val together = (0..60).map { it * 0.1f }.maxOf { ButtonLookFill.lavaSurface(0.5f, it) }
        val apart = (0..60).map { it * 0.1f }.minOf { ButtonLookFill.lavaSurface(0.5f, it) }
        assertTrue("together $together", together > 0.6f)
        assertTrue("apart $apart", apart < 0.2f)
        for (k in 0 until 3) {
            assertTrue(ButtonLookFill.lavaRise(k, 12.3f) in 0f..1f)
        }
    }

    @Test
    fun `the ripple's surface stays within its height`() {
        for (i in 0..40) {
            val y = ButtonLookFill.rippleSurface(i / 40f, i * 0.37f)
            assertTrue(abs(y) <= 1.0001f)
        }
    }

    @Test
    fun `the number is written against what the look mostly is`() {
        val track = 0xFF1C1C20.toInt()
        ButtonLookFill.LOOKS.forEach { id ->
            val p = ButtonLookFill.paletteWith(id, null)
            val back = ButtonLookFill.backdrop(id, p, track)
            assertEquals("$id's backdrop is opaque", 0xFF, back ushr 24)
        }
        // Neon is mostly its dark; Line mostly the track showing through its faint light.
        val neon = ButtonLookFill.paletteWith(ButtonLookFill.NEON, null)
        assertEquals(ButtonLookFill.blend(track, neon[0], 0.88f), ButtonLookFill.backdrop(ButtonLookFill.NEON, neon, track))
        assertEquals(track, ButtonLookFill.backdrop(ButtonLookFill.LINE, IntArray(2), track))
    }

    @Test
    fun `blending goes the whole way and no further`() {
        assertEquals(0xFF000000.toInt(), ButtonLookFill.blend(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0f))
        assertEquals(0xFFFFFFFF.toInt(), ButtonLookFill.blend(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 1f))
        assertEquals(0xFF808080.toInt(), ButtonLookFill.blend(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f))
    }
}
