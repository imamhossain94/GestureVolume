package com.newagedevs.gesturevolume.utils

import com.newagedevs.gesturevolume.data.local.ButtonBumpStore
import com.newagedevs.gesturevolume.utils.ButtonBump.Edge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The button animation is drawn from these numbers, and every one of them is a promise about
 * something on the glass: that the bump meets the edge without a corner, that it lands beside the
 * physical buttons in every rotation, that every style can be chosen, and that the spring settles
 * rather than ringing for ever.
 */
class ButtonBumpTest {

    // ---- styles ------------------------------------------------------------------------------------

    @Test
    fun `every style has a spec, and every one of them is there to choose`() {
        ButtonBump.STYLES.forEach { ButtonBump.spec(it) }
        // Every animation is free: a stored style is used as it is, whichever it is.
        ButtonBump.STYLES.forEach { assertEquals(it, ButtonBump.sanitizeStyle(it)) }
        assertEquals(ButtonBump.STYLES.size, ButtonBump.STYLES.toSet().size)
        assertTrue(ButtonBump.DEFAULT_STYLE in ButtonBump.STYLES)
    }

    @Test
    fun `the styles that throw things across the screen get the room to`() {
        val density = 3f
        val length = 52f * density
        val depth = 18f * density
        val throwers = ButtonBump.STYLES.filter { ButtonBump.spec(it).spread > 0f }
        // Water, and every one of the showpieces: petals, fireworks, the characters and the rest.
        assertTrue(throwers.containsAll(listOf(
            ButtonBump.STYLE_WATER, ButtonBump.STYLE_BLOSSOM, ButtonBump.STYLE_FIREWORKS,
            ButtonBump.STYLE_VOLCANO, ButtonBump.STYLE_ROCKET, ButtonBump.STYLE_CAT, ButtonBump.STYLE_MONKEY,
            ButtonBump.STYLE_FISH, ButtonBump.STYLE_FROG, ButtonBump.STYLE_SWING, ButtonBump.STYLE_BALLOON,
            ButtonBump.STYLE_BUBBLES, ButtonBump.STYLE_SPRAY, ButtonBump.STYLE_WIND,
            ButtonBump.STYLE_NINJA, ButtonBump.STYLE_CRACK, ButtonBump.STYLE_UFO, ButtonBump.STYLE_BEES,
            ButtonBump.STYLE_SOLAR,
        )))
        assertEquals(19, throwers.size)
        for (style in throwers) {
            val spec = ButtonBump.spec(style)
            val span = ButtonBump.windowSpan(style, length, density, depth)
            val reach = ButtonBump.windowReach(style, depth, density, false)
            // As far into the screen as the style says, and as far along the edge both ways from the bump.
            assertTrue("$style reach $reach", reach >= depth * spec.reach)
            assertTrue("$style span $span", span >= length + 2f * depth * spec.spread)
            // Even the characters that only peek round the edge have room to show their heads.
            assertTrue("$style reach $reach", reach > depth * 3.5f)
        }
    }

    @Test
    fun `retired styles fall back to the default`() {
        listOf("dragon", "alien", "atom", "smoke", "woodpecker").forEach {
            assertTrue(it !in ButtonBump.STYLES)
            assertEquals(ButtonBump.DEFAULT_STYLE, ButtonBump.sanitizeStyle(it))
        }
        // The spider became the swing women: whoever chose it keeps them.
        assertTrue("spider" !in ButtonBump.STYLES)
        assertEquals(ButtonBump.STYLE_SWING, ButtonBump.sanitizeStyle("spider"))
        // And the ones that came after them are there, each with its own sound.
        assertEquals("ribbit", ButtonBump.sound(ButtonBump.STYLE_FROG))
        assertEquals("zip", ButtonBump.sound(ButtonBump.STYLE_SWING))
        assertEquals("balloon", ButtonBump.sound(ButtonBump.STYLE_BALLOON))
    }

    @Test
    fun `every style has a sound of its own from the library`() {
        for (style in ButtonBump.STYLES) {
            assertTrue("$style -> ${ButtonBump.sound(style)}", ButtonBump.sound(style) in StepSoundLibrary.IDS)
        }
        // The showpieces each sound like themselves, not like the tick.
        assertEquals("meow", ButtonBump.sound(ButtonBump.STYLE_CAT))
        assertEquals("drop", ButtonBump.sound(ButtonBump.STYLE_WATER))
    }

    @Test
    fun `a held key ratchets the sound on, except for the balloon, which fills`() {
        assertFalse(ButtonBump.soundsWhileHeld(ButtonBump.STYLE_BALLOON))
        ButtonBump.STYLES.filter { it != ButtonBump.STYLE_BALLOON }.forEach { assertTrue(it, ButtonBump.soundsWhileHeld(it)) }
    }

    @Test
    fun `a shallow bump still throws full-size showpieces`() {
        val density = 3f
        val shallow = ButtonBump.windowReach(ButtonBump.STYLE_FIREWORKS, ButtonBumpStore.MIN_DEPTH * density, density, false)
        val usual = ButtonBump.windowReach(ButtonBump.STYLE_FIREWORKS, ButtonBump.EFFECT_DEPTH_DP * density, density, false)
        assertEquals(usual, shallow)
    }

    @Test
    fun `an unknown style is the default`() {
        assertEquals(ButtonBump.DEFAULT_STYLE, ButtonBump.sanitizeStyle("comet"))
        assertEquals(ButtonBump.DEFAULT_STYLE, ButtonBump.sanitizeStyle(null))
    }

    // ---- the shape ----------------------------------------------------------------------------------

    @Test
    fun `the profile is full in the middle and nothing at the ends`() {
        assertEquals(1f, ButtonBump.profile(0f, 0.34f), 0f)
        assertEquals(0f, ButtonBump.profile(1f, 0.34f), 0f)
        assertEquals(0f, ButtonBump.profile(-1.2f, 0.34f), 0f)
    }

    @Test
    fun `the profile only ever falls towards the ends`() {
        var last = 1f
        var u = 0f
        while (u <= 1f) {
            val p = ButtonBump.profile(u, 0.4f)
            assertTrue("rose at $u", p <= last + 1e-6f)
            last = p
            u += 0.01f
        }
    }

    @Test
    fun `the profile is symmetric`() {
        listOf(0.1f, 0.5f, 0.77f, 0.95f).forEach { u ->
            assertEquals(ButtonBump.profile(u, 0.3f), ButtonBump.profile(-u, 0.3f), 0f)
        }
    }

    @Test
    fun `the shoulder meets the glass and the top with no corner`() {
        // Slope at the glass and at the top of the shoulder is flat: equal values a hair either side.
        val h = 1e-3f
        assertEquals(0f, ButtonBump.smootherstep(h) - ButtonBump.smootherstep(0f), 1e-6f)
        assertEquals(0f, ButtonBump.smootherstep(1f) - ButtonBump.smootherstep(1f - h), 1e-6f)
    }

    // ---- the motion -------------------------------------------------------------------------------------

    @Test
    fun `a critically damped spring reaches its target without overshoot`() {
        val s = ButtonBump.Spring()
        var max = 0f
        repeat(120) {
            ButtonBump.stepSpring(s, 1f, stiffness = 400f, damping = 1f, dt = 1f / 60f)
            max = maxOf(max, s.value)
        }
        assertTrue(s.isSettled(1f))
        assertTrue("overshot to $max", max <= 1.001f)
    }

    @Test
    fun `a soft spring overshoots and still settles`() {
        val s = ButtonBump.Spring()
        var max = 0f
        repeat(300) {
            ButtonBump.stepSpring(s, 1f, stiffness = 300f, damping = 0.24f, dt = 1f / 60f)
            max = maxOf(max, s.value)
        }
        assertTrue("never overshot", max > 1.2f)
        assertTrue(s.isSettled(1f, epsilon = 0.01f))
    }

    @Test
    fun `a long frame is clamped rather than thrown`() {
        val a = ButtonBump.Spring()
        val b = ButtonBump.Spring()
        ButtonBump.stepSpring(a, 1f, 400f, 0.6f, dt = 5f)
        ButtonBump.stepSpring(b, 1f, 400f, 0.6f, dt = ButtonBump.MAX_FRAME_S)
        assertEquals(b.value, a.value, 1e-6f)
    }

    @Test
    fun `a press pulse rises, falls and ends`() {
        assertEquals(0f, ButtonBump.pulse(-0.01f), 0f)
        assertEquals(1f, ButtonBump.pulse(ButtonBump.PRESS_S * 0.2f), 1e-4f)
        assertTrue(ButtonBump.pulse(ButtonBump.PRESS_S * 0.6f) in 0.01f..0.99f)
        assertEquals(0f, ButtonBump.pulse(ButtonBump.PRESS_S), 0f)
        assertEquals(0f, ButtonBump.pulse(ButtonBump.PRESS_S * 0.9999f), 0.01f)
    }

    @Test
    fun `a wobble dies away`() {
        val early = (0..20).maxOf { kotlin.math.abs(ButtonBump.wobble(it / 100f)) }
        val late = (100..140).maxOf { kotlin.math.abs(ButtonBump.wobble(it / 100f)) }
        assertTrue(early > 0.5f)
        assertTrue(late < 0.05f)
    }

    // ---- placement --------------------------------------------------------------------------------------

    @Test
    fun `upright, buttons on the right put the bump on the right edge`() {
        val p = ButtonBump.placement(left = false, along = 0.3f, turns = 0, screenWidth = 1080, screenHeight = 2400, span = 600, reach = 120)
        assertEquals(Edge.RIGHT, p.edge)
        assertEquals(1080 - 120, p.x)
        assertEquals(720 - 300, p.y)
        assertEquals(300f, p.centre, 0.5f)
        assertFalse(p.flipped)
    }

    @Test
    fun `turned to the left, buttons on the right are along the top, the same way along`() {
        // ROTATION_90: the phone's top is at the screen's left, its right side along the top edge.
        val p = ButtonBump.placement(left = false, along = 0.3f, turns = 1, screenWidth = 2400, screenHeight = 1080, span = 600, reach = 120)
        assertEquals(Edge.TOP, p.edge)
        assertEquals(0, p.y)
        assertEquals(720 - 300, p.x)
        assertFalse(p.flipped)
    }

    @Test
    fun `turned to the right, buttons on the right are along the bottom, running backwards`() {
        val p = ButtonBump.placement(left = false, along = 0.3f, turns = 3, screenWidth = 2400, screenHeight = 1080, span = 600, reach = 120)
        assertEquals(Edge.BOTTOM, p.edge)
        assertEquals(1080 - 120, p.y)
        // 30% from the phone's top, which is now at the screen's right.
        assertEquals((2400 * 0.7f - 300).toInt(), p.x)
        assertTrue(p.flipped)
    }

    @Test
    fun `upside down, buttons on the left are on the right edge from the bottom`() {
        val p = ButtonBump.placement(left = true, along = 0.25f, turns = 2, screenWidth = 1080, screenHeight = 2400, span = 400, reach = 100)
        assertEquals(Edge.RIGHT, p.edge)
        assertEquals((2400 * 0.75f - 200).toInt(), p.y)
        assertTrue(p.flipped)
    }

    @Test
    fun `a bump near the end of the edge is kept on the screen, its middle where the buttons are`() {
        val p = ButtonBump.placement(left = false, along = 0.02f, turns = 0, screenWidth = 1080, screenHeight = 2400, span = 600, reach = 120)
        assertEquals(0, p.y)
        assertEquals(48f, p.centre, 0.5f)
    }

    @Test
    fun `the upright picture maps onto every edge`() {
        val right = ButtonBump.placement(false, 0.5f, 0, 1080, 2400, 600, 120)
        assertEquals(1080f - 10f to 50f, ButtonBump.toWindow(right, inward = 10f, down = 50f).let { (x, y) -> right.x + x to y })
        val top = ButtonBump.placement(false, 0.5f, 1, 2400, 1080, 600, 120)
        assertEquals(50f to 10f, ButtonBump.toWindow(top, inward = 10f, down = 50f))
        val bottom = ButtonBump.placement(false, 0.5f, 3, 2400, 1080, 600, 120)
        assertEquals(550f to 110f, ButtonBump.toWindow(bottom, inward = 10f, down = 50f))
    }

    @Test
    fun `the level is the index across the range`() {
        assertEquals(0.5f, ButtonBump.levelOf(5, 0, 10), 0f)
        assertEquals(0f, ButtonBump.levelOf(1, 1, 7), 0f)
        assertEquals(1f, ButtonBump.levelOf(30, 0, 25), 0f)
    }
}
