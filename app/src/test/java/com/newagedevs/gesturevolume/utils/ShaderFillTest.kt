package com.newagedevs.gesturevolume.utils

import com.newagedevs.gesturevolume.ui.view.ShaderSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Shaders fill's effects, settings and programs, as far as they can be checked off a phone. */
class ShaderFillTest {

    @Test
    fun `it is offered as a fill, takes colours, and anything unknown falls back to the first effect`() {
        assertTrue(SliderFill.SHADER in SliderFill.ALL)
        assertEquals(SliderFill.SHADER, SliderFill.sanitize(SliderFill.SHADER))
        assertTrue(SliderFill.supportsCustomColors(SliderFill.SHADER))
        assertFalse(SliderFill.hasWave(SliderFill.SHADER))
        assertEquals(ShaderFill.LAVA_LAMP, ShaderFill.sanitize("bogus"))
        assertEquals(ShaderFill.LAVA_LAMP, ShaderFill.sanitize(null))
        assertEquals(ShaderFill.ALL.size, ShaderFill.ALL.toSet().size)
    }

    @Test
    fun `every effect has four opaque colours, and the user's recolour its light but not its dark`() {
        val custom = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())
        ShaderFill.ALL.forEach { id ->
            val own = ShaderFill.palette(id)
            assertEquals(id, 4, own.size)
            val mixed = ShaderFill.paletteWith(id, custom)
            assertEquals(id, 4, mixed.size)
            mixed.forEach { assertEquals(id, 0xFF, (it ushr 24) and 0xFF) }
            own.forEachIndexed { i, colour ->
                if (SliderFill.isGround(colour)) assertEquals("$id slot $i", colour.toInt(), mixed[i])
            }
            // Something the user picked shows, or the colours setting would do nothing here.
            assertTrue(id, own.indices.any { own[it].toInt() != mixed[it] })
        }
    }

    @Test
    fun `settings are clamped to what can be drawn`() {
        val wild = ShaderFill.Style(
            effect = "nope", speed = 99f, scale = -1f, detail = Float.NaN,
            brightness = 9f, grain = -3f, rest = 1f,
        ).sanitized()
        assertEquals(ShaderFill.LAVA_LAMP, wild.effect)
        assertEquals(ShaderFill.MAX_SPEED, wild.speed)
        assertEquals(ShaderFill.MIN_SCALE, wild.scale)
        assertEquals(ShaderFill.DEFAULT_DETAIL, wild.detail)
        assertEquals(ShaderFill.MAX_BRIGHTNESS, wild.brightness)
        assertEquals(0f, wild.grain)
        assertEquals(ShaderFill.MAX_REST, wild.rest)
        assertFalse(ShaderFill.Style(speed = 0f).isAnimated)
        assertTrue(ShaderFill.Style().isAnimated)
    }

    @Test
    fun `every program declares the uniforms the renderer sets, and has an effect and an entry point`() {
        ShaderFill.ALL.forEach { id ->
            val source = ShaderSources.source(id)
            assertTrue(id, "float3 effect(float2 uv)" in source)
            assertTrue(id, "half4 main(float2 fragCoord)" in source)
            ShaderSources.FLOAT_UNIFORMS.forEach { assertTrue("$id $it", "uniform float $it;" in source) }
            ShaderSources.FLOAT2_UNIFORMS.forEach { assertTrue("$id $it", "uniform float2 $it;" in source) }
            ShaderSources.COLOR_UNIFORMS.forEach { assertTrue("$id $it", "layout(color) uniform half4 $it;" in source) }
        }
        // Each effect its own program, not one of them standing in for the rest.
        assertEquals(ShaderFill.ALL.size, ShaderFill.ALL.map { ShaderSources.source(it) }.toSet().size)
    }

    @Test
    fun `no program leans on GLSL habits AGSL refuses`() {
        ShaderFill.ALL.forEach { id ->
            val source = ShaderSources.source(id)
            assertFalse("$id uses vec types", Regex("""\b[iu]?vec[234]\b""").containsMatchIn(source))
            assertFalse("$id uses texture()", "texture(" in source)
            assertFalse("$id uses gl_FragCoord", "gl_FragCoord" in source)
            // Brackets balance: an unbalanced one is the commonest way to break a program by editing it.
            assertEquals(id, source.count { it == '(' }, source.count { it == ')' })
            assertEquals(id, source.count { it == '{' }, source.count { it == '}' })
        }
    }
}
