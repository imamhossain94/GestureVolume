package com.newagedevs.gesturevolume.utils

import com.newagedevs.gesturevolume.ui.view.SurgeSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Surge fill's looks, settings and programs, as far as they can be checked off a phone. */
class SurgeFillTest {

    @Test
    fun `it is offered as a fill, takes colours, and anything unknown falls back to the curve`() {
        assertTrue(SliderFill.SURGE in SliderFill.ALL)
        assertEquals(SliderFill.SURGE, SliderFill.sanitize(SliderFill.SURGE))
        assertTrue(SliderFill.supportsCustomColors(SliderFill.SURGE))
        assertTrue(SliderFill.isAnimated(SliderFill.SURGE))
        assertFalse(SliderFill.isPictorial(SliderFill.SURGE))
        assertEquals(SurgeFill.CURVE, SurgeFill.sanitize("bogus"))
        assertEquals(SurgeFill.CURVE, SurgeFill.sanitize(null))
        assertEquals(6, SurgeFill.LOOKS.size)
        assertEquals(SurgeFill.LOOKS.size, SurgeFill.LOOKS.toSet().size)
        assertEquals(ShaderFill.MIN_SDK, SurgeFill.MIN_SDK)
    }

    @Test
    fun `every look has four opaque colours, and the user's recolour its light but not its dark`() {
        val custom = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())
        SurgeFill.LOOKS.forEach { id ->
            val own = SurgeFill.palette(id)
            assertEquals(id, 4, own.size)
            // The first colour is the dark the light fades into, and has to stay dark.
            assertTrue(id, SliderFill.isGround(own[0]))
            (1 until 4).forEach { assertFalse("$id slot $it", SliderFill.isGround(own[it])) }
            val mixed = SurgeFill.paletteWith(id, custom)
            assertEquals(id, 4, mixed.size)
            mixed.forEach { assertEquals(id, 0xFF, (it ushr 24) and 0xFF) }
            assertEquals(id, own[0].toInt(), mixed[0])
            assertTrue(id, (1 until 4).any { own[it].toInt() != mixed[it] })
        }
    }

    @Test
    fun `settings are clamped to what can be drawn`() {
        val wild = SurgeFill.Style(
            look = "nope", speed = 99f, size = -1f, edge = Float.NaN,
            glow = 9f, trail = -3f, rest = 1f,
        ).sanitized()
        assertEquals(SurgeFill.CURVE, wild.look)
        assertEquals(SurgeFill.MAX_SPEED, wild.speed)
        assertEquals(SurgeFill.MIN_SIZE, wild.size)
        assertEquals(SurgeFill.DEFAULT_EDGE, wild.edge)
        assertEquals(1f, wild.glow)
        assertEquals(0f, wild.trail)
        assertEquals(SurgeFill.MAX_REST, wild.rest)
        assertEquals(SurgeFill.Style(), SurgeFill.Style().sanitized())
        assertFalse(SurgeFill.Style(speed = 0f).isAnimated)
        assertTrue(SurgeFill.Style().isAnimated)
    }

    @Test
    fun `every program declares the uniforms the renderer sets, and has a look and an entry point`() {
        SurgeFill.LOOKS.forEach { id ->
            val source = SurgeSources.source(id)
            assertTrue(id, "float4 look(float2 uv, float lv, float frac)" in source)
            assertTrue(id, "half4 main(float2 fragCoord)" in source)
            SurgeSources.FLOAT_UNIFORMS.forEach { assertTrue("$id $it", "uniform float $it;" in source) }
            SurgeSources.FLOAT2_UNIFORMS.forEach { assertTrue("$id $it", "uniform float2 $it;" in source) }
            SurgeSources.COLOR_UNIFORMS.forEach { assertTrue("$id $it", "layout(color) uniform half4 $it;" in source) }
            // Declared once each: the shared noise must not bring a second set.
            assertEquals(id, 1, Regex("""uniform float2 size;""").findAll(source).count())
        }
        assertEquals(SurgeFill.LOOKS.size, SurgeFill.LOOKS.map { SurgeSources.source(it) }.toSet().size)
    }

    @Test
    fun `no program leans on GLSL habits AGSL refuses`() {
        SurgeFill.LOOKS.forEach { id ->
            val source = SurgeSources.source(id)
            assertFalse("$id uses vec types", Regex("""\b[iu]?vec[234]\b""").containsMatchIn(source))
            assertFalse("$id uses texture()", "texture(" in source)
            assertFalse("$id uses gl_FragCoord", "gl_FragCoord" in source)
            // pow() of a number that can be negative is undefined on a graphics chip; squares are
            // written out instead.
            assertFalse("$id uses pow()", "pow(" in source.substringAfter("float4 look("))
            assertEquals(id, source.count { it == '(' }, source.count { it == ')' })
            assertEquals(id, source.count { it == '{' }, source.count { it == '}' })
        }
    }
}
