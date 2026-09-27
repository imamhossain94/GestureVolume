package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Quick panel's own ink for the number and the icon, picked against what is under them. */
class ContentInkTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val track = 0xFF1C1C20.toInt()

    @Test
    fun `contrast runs from one to twenty-one`() {
        assertEquals(21.0, ContentInk.contrast(white, black), 0.01)
        assertEquals(1.0, ContentInk.contrast(track, track), 0.0001)
    }

    @Test
    fun `the default colours keep their swap`() {
        // White on the dark track, and the dark track on a white fill: what the panel always was.
        assertEquals(white, ContentInk.pick(track, white))
        assertEquals(track, ContentInk.pick(white, track))
    }

    @Test
    fun `a colour that would vanish gives way to a shade that stands out`() {
        // The track's dark on a dark picture — a nebula — and on the deep blue of the liquid.
        for (under in intArrayOf(0xFF140F2E.toInt(), 0xFF4A5CF0.toInt())) {
            val ink = ContentInk.pick(under, track)
            assertTrue(ContentInk.luminance(ink) > ContentInk.luminance(under))
            assertTrue(ContentInk.contrast(ink, under) >= 4.5)
        }
        // And the fill's white on a light track the user picked.
        val light = 0xFFE9E6F2.toInt()
        val ink = ContentInk.pick(light, white)
        assertTrue(ContentInk.luminance(ink) < ContentInk.luminance(light))
        assertTrue(ContentInk.contrast(ink, light) >= 4.5)
    }

    @Test
    fun `whatever is under it, the ink is opaque and reads`() {
        for (r in 0..255 step 51) for (g in 0..255 step 51) for (b in 0..255 step 51) {
            val under = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            val ink = ContentInk.pick(under, under)
            assertEquals(0xFF, (ink ushr 24) and 0xFF)
            // Mid greys are the hardest case there is; even there it clears the bar for large text.
            assertTrue("on #%06X".format(under and 0xFFFFFF), ContentInk.contrast(ink, under) >= 3.0)
        }
    }

    @Test
    fun `alpha is ignored`() {
        assertEquals(ContentInk.pick(track, white), ContentInk.pick(track and 0x80FFFFFF.toInt(), white and 0x40FFFFFF))
    }
}
