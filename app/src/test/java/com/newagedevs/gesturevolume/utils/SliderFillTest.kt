package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Quick panel's fill animations, checked without a device.
 *
 * The invariant that matters most is the one nobody notices until it is broken: **every loop must
 * be seamless.** A fill runs on a repeating clock, and anything in it that moves at a fractional
 * speed is somewhere different on the last frame of a cycle than on the first — so it jumps, once
 * per cycle, forever. On a two-second cycle that is a twitch every two seconds, which reads as the
 * animation being broken rather than as it being busy. Several fills did exactly that; these tests
 * are what stops one doing it again.
 */
class SliderFillTest {

    private val justBeforeTheWrap = 0.99999f

    @Test
    fun `every wave comes back to where it started`() {
        listOf(SliderFill.TIDE_UP, SliderFill.TIDE_DOWN, SliderFill.LIQUID).forEach { id ->
            for (i in 0..40) {
                val x = i / 40f
                val start = SliderFill.waveAt(id, 0f, x)
                val end = SliderFill.waveAt(id, justBeforeTheWrap, x)
                assertEquals("$id has a seam at x=$x", start, end, 2e-3f)
            }
        }
    }

    @Test
    fun `every grid comes back to where it started`() {
        val columns = 6
        val rows = 30
        listOf(SliderFill.DOT_MATRIX, SliderFill.RUNE, SliderFill.MATRIX_RAIN).forEach { id ->
            var worst = 0f
            for (c in 0 until columns) {
                for (r in 0 until rows) {
                    val start = SliderFill.cellGlow(id, 0f, c, r, columns, rows)
                    val end = SliderFill.cellGlow(id, justBeforeTheWrap, c, r, columns, rows)
                    worst = maxOf(worst, kotlin.math.abs(start - end))
                }
            }
            assertTrue("$id jumps by $worst when its cycle wraps", worst < 0.02f)
        }
    }

    @Test
    fun `liquid has a calmer surface than a tide`() {
        var tide = 0f
        var liquid = 0f
        for (i in 0..40) {
            tide = maxOf(tide, kotlin.math.abs(SliderFill.waveAt(SliderFill.TIDE_UP, 0.3f, i / 40f)))
            liquid = maxOf(liquid, kotlin.math.abs(SliderFill.waveAt(SliderFill.LIQUID, 0.3f, i / 40f)))
        }
        assertTrue("liquid ($liquid) is as rough as a tide ($tide)", liquid < tide * 0.7f)
    }

    @Test
    fun `every style is offered exactly once`() {
        assertEquals(SliderFill.ALL.size, SliderFill.ALL.toSet().size)
    }

    @Test
    fun `every animated style runs on a clock slow enough to see`() {
        SliderFill.ALL.filter { SliderFill.isAnimated(it) }.forEach { id ->
            assertTrue("$id cycles every ${SliderFill.cycleMs(id)}ms", SliderFill.cycleMs(id) >= 500)
        }
    }

    @Test
    fun `every pictorial style brings a palette`() {
        SliderFill.ALL.filter { SliderFill.isPictorial(it) }.forEach { id ->
            assertTrue("$id has no colours", SliderFill.palette(id).isNotEmpty())
        }
    }

    @Test
    fun `without custom colours a style keeps its own palette`() {
        SliderFill.ALL.forEach { id ->
            val own = SliderFill.palette(id)
            assertArrayEquals("$id with null", own, SliderFill.paletteWith(id, null))
            assertArrayEquals("$id with none", own, SliderFill.paletteWith(id, IntArray(0)))
        }
    }

    @Test
    fun `custom colours fill every slot and keep each slot's alpha`() {
        val custom = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())
        SliderFill.ALL.filter { SliderFill.supportsCustomColors(it) }.forEach { id ->
            val own = SliderFill.palette(id)
            val mine = SliderFill.paletteWith(id, custom)
            assertEquals("$id changed length", own.size, mine.size)
            for (i in own.indices) {
                assertEquals("$id slot $i lost its alpha", own[i] ushr 24, mine[i] ushr 24)
            }
            // The ends are the user's first and last colours exactly, unless the style's own slot
            // there is its darkness, which is kept.
            if (!SliderFill.isGround(own.first())) {
                assertEquals("$id first slot", 0xFF0000L, mine.first() and 0xFFFFFFL)
            }
            if (mine.size > 1 && !SliderFill.isGround(own.last())) {
                assertEquals("$id last slot", 0x0000FFL, mine.last() and 0xFFFFFFL)
            }
            own.forEachIndexed { i, c ->
                if (SliderFill.isGround(c)) assertEquals("$id ground slot $i was recoloured", c, mine[i])
            }
        }
    }

    @Test
    fun `slots between the ends are blends of the user's colours`() {
        // Aurora has four slots, all of them lights: t = 0, 1/3, 2/3, 1 across black to white.
        val mine = SliderFill.paletteWith(SliderFill.AURORA, intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()))
        assertEquals(0x000000L, mine[0] and 0xFFFFFFL)
        assertEquals(0x555555L, mine[1] and 0xFFFFFFL)
        assertEquals(0xAAAAAAL, mine[2] and 0xFFFFFFL)
        assertEquals(0xFFFFFFL, mine[3] and 0xFFFFFFL)
        // Three colours over five slots: the middle slot is the middle colour.
        val confetti = SliderFill.paletteWith(
            SliderFill.CONFETTI,
            intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt()),
        )
        assertEquals(0x00FF00L, confetti[2] and 0xFFFFFFL)
        assertEquals(0x808000L, confetti[1] and 0xFFFFFFL)
    }

    @Test
    fun `a style's darkness keeps its colour`() {
        val own = SliderFill.palette(SliderFill.GALAXY)
        val mine = SliderFill.paletteWith(SliderFill.GALAXY, intArrayOf(0xFFFFC0CB.toInt()))
        assertEquals("the sky", own[0], mine[0])
        assertEquals("the stars", 0xFFC0CBL, mine[2] and 0xFFFFFFL)
    }

    @Test
    fun `one custom colour paints every slot`() {
        val mine = SliderFill.paletteWith(SliderFill.OCEAN, intArrayOf(0x8012ABCD.toInt()))
        val own = SliderFill.palette(SliderFill.OCEAN)
        mine.forEachIndexed { i, c ->
            assertEquals(0x12ABCDL, c and 0xFFFFFFL)
            assertEquals(own[i] ushr 24, c ushr 24)
        }
    }

    @Test
    fun `only pictorial styles take custom colours`() {
        listOf(SliderFill.SOLID, SliderFill.TIDE_UP, SliderFill.TIDE_DOWN, SliderFill.STRIPES).forEach {
            assertFalse(it, SliderFill.supportsCustomColors(it))
        }
        listOf(
            SliderFill.FIREFLIES, SliderFill.SNOWFALL, SliderFill.HEARTBEAT, SliderFill.NEON, SliderFill.OCEAN,
            SliderFill.GRADIENT, SliderFill.CONFETTI, SliderFill.WARP, SliderFill.STORM, SliderFill.FIREWORKS,
            SliderFill.GALAXY, SliderFill.NEBULA,
        ).forEach { assertTrue(it, SliderFill.supportsCustomColors(it)) }
    }

    @Test
    fun `a retired style falls back to solid rather than to something else`() {
        listOf("battery", "pixelUp", "pixelDown", "pulse", "shimmer", "glow", null).forEach { old ->
            assertEquals("$old", SliderFill.SOLID, SliderFill.sanitize(old))
        }
    }

    @Test
    fun `the seeded randomness is stable and in range`() {
        for (n in 0..200) {
            val v = SliderFill.pseudoRandom(n)
            assertTrue("pseudoRandom($n) = $v", v in 0f..1f)
            assertEquals("pseudoRandom($n) is not deterministic", v, SliderFill.pseudoRandom(n), 0f)
        }
    }
}
