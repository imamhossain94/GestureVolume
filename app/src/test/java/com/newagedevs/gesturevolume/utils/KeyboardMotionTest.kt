package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the bar moves out of the keyboard's way: the choices, their curves and their timing. */
class KeyboardMotionTest {

    @Test
    fun `an unknown motion is the glide it always was`() {
        assertEquals(BarBehaviour.MOTION_GLIDE, BarBehaviour.sanitizeMotion(null))
        assertEquals(BarBehaviour.MOTION_GLIDE, BarBehaviour.sanitizeMotion("warp"))
        BarBehaviour.KEYBOARD_MOTIONS.forEach { assertEquals(it, BarBehaviour.sanitizeMotion(it)) }
        assertEquals(BarBehaviour.KEYBOARD_MOTIONS.size, BarBehaviour.KEYBOARD_MOTIONS.toSet().size)
    }

    @Test
    fun `every motion starts where the bar was and ends where it goes`() {
        BarBehaviour.KEYBOARD_MOTIONS.filter { it != BarBehaviour.MOTION_INSTANT }.forEach {
            assertEquals(it, 0f, BarBehaviour.motionAt(it, 0f), 0.001f)
            assertEquals(it, 1f, BarBehaviour.motionAt(it, 1f), 0.02f)
            assertEquals(it, 1f, BarBehaviour.motionAlphaAt(it, 1f), 0.001f)
            assertEquals(it, 1f, BarBehaviour.motionAlphaAt(it, 0f), 0.001f)
        }
        assertEquals(1f, BarBehaviour.motionAt(BarBehaviour.MOTION_INSTANT, 0f), 0f)
        assertEquals(0L, BarBehaviour.motionMs(BarBehaviour.MOTION_INSTANT))
    }

    @Test
    fun `the spring goes past and comes back, and the bounce never does`() {
        val spring = (0..100).map { BarBehaviour.motionAt(BarBehaviour.MOTION_SPRING, it / 100f) }
        assertTrue(spring.max() > 1.05f)
        val bounce = (0..100).map { BarBehaviour.motionAt(BarBehaviour.MOTION_BOUNCE, it / 100f) }
        assertTrue(bounce.all { it <= 1.001f })
        // It lands and lifts off again at least once on its way.
        assertTrue(bounce.zipWithNext().any { (a, b) -> b < a })
    }

    @Test
    fun `a fade does not travel, it is gone halfway`() {
        assertEquals(0f, BarBehaviour.motionAt(BarBehaviour.MOTION_FADE, 0.4f), 0f)
        assertEquals(1f, BarBehaviour.motionAt(BarBehaviour.MOTION_FADE, 0.6f), 0f)
        assertEquals(0f, BarBehaviour.motionAlphaAt(BarBehaviour.MOTION_FADE, 0.5f), 0.001f)
    }

    @Test
    fun `quick is quicker than the glide, which is quicker than the spring and the bounce`() {
        val ms = BarBehaviour::motionMs
        assertTrue(ms(BarBehaviour.MOTION_QUICK) < ms(BarBehaviour.MOTION_GLIDE))
        assertTrue(ms(BarBehaviour.MOTION_GLIDE) < ms(BarBehaviour.MOTION_SPRING))
        assertTrue(ms(BarBehaviour.MOTION_SPRING) < ms(BarBehaviour.MOTION_BOUNCE))
    }
}
