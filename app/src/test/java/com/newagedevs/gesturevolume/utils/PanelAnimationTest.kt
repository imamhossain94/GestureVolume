package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The entrance animations, checked without a device.
 *
 * One invariant matters more than all the rest: **every animation must end at the identity.** A
 * panel whose last frame is a hundredth of a degree off, or a percent short of full size, is a
 * panel that rests wrong — and because the frosted pane behind it never moved, resting wrong means
 * the panel and its glass no longer line up. Every case below is really that one test.
 */
class PanelAnimationTest {

    @Test
    fun `every animation rests at the identity`() {
        PanelAnimation.ALL.forEach { id ->
            listOf(true, false).forEach { left ->
                val f = PanelAnimation.frameAt(id, 1f, left)
                assertEquals("$id alpha", 1f, f.alpha, 1e-3f)
                assertEquals("$id scaleX", 1f, f.scaleX, 1e-3f)
                assertEquals("$id scaleY", 1f, f.scaleY, 1e-3f)
                assertEquals("$id translationX", 0f, f.translationX, 1e-3f)
                assertEquals("$id translationY", 0f, f.translationY, 1e-3f)
                assertEquals("$id rotationZ", 0f, f.rotationZ, 1e-3f)
                assertEquals("$id rotationX", 0f, f.rotationX, 1e-3f)
                assertEquals("$id rotationY", 0f, f.rotationY, 1e-3f)
                assertEquals("$id revealFrom", 0f, f.revealFrom, 1e-3f)
                assertEquals("$id revealTo", 1f, f.revealTo, 1e-3f)
            }
        }
    }

    @Test
    fun `every animation starts somewhere other than the identity`() {
        // Otherwise it is not an animation, it is a name in a list.
        PanelAnimation.ALL.forEach { id ->
            val f = PanelAnimation.frameAt(id, 0f, towardLeft = false)
            val moved = f.alpha < 0.99f || f.scaleX != 1f || f.scaleY != 1f ||
                f.translationX != 0f || f.translationY != 0f ||
                f.rotationZ != 0f || f.rotationX != 0f || f.rotationY != 0f ||
                f.revealFrom != 0f || f.revealTo != 1f
            assertTrue("$id does nothing at t=0", moved)
        }
    }

    @Test
    fun `progress is clamped at both ends`() {
        PanelAnimation.ALL.forEach { id ->
            val before = PanelAnimation.frameAt(id, -3f, towardLeft = false)
            val start = PanelAnimation.frameAt(id, 0f, towardLeft = false)
            assertEquals("$id before the start", start, before)

            val after = PanelAnimation.frameAt(id, 4f, towardLeft = false)
            val end = PanelAnimation.frameAt(id, 1f, towardLeft = false)
            assertEquals("$id after the end", end, after)
        }
    }

    @Test
    fun `the hinged ones hinge on the side the panel came from`() {
        listOf(PanelAnimation.EXPAND, PanelAnimation.SWING, PanelAnimation.FLIP).forEach { id ->
            val fromLeft = PanelAnimation.frameAt(id, 0f, towardLeft = true)
            val fromRight = PanelAnimation.frameAt(id, 0f, towardLeft = false)
            assertEquals("$id origin on the left", 0f, fromLeft.originX, 1e-3f)
            assertEquals("$id origin on the right", 1f, fromRight.originX, 1e-3f)
        }
    }

    @Test
    fun `the ones that come in from the side reverse with it`() {
        val fromLeft = PanelAnimation.frameAt(PanelAnimation.SLIDE, 0f, towardLeft = true)
        val fromRight = PanelAnimation.frameAt(PanelAnimation.SLIDE, 0f, towardLeft = false)
        assertEquals("mirrored", -fromLeft.translationX, fromRight.translationX, 1e-3f)
        assertTrue("a bar on the left pushes the panel right", fromLeft.translationX < 0f)
    }

    @Test
    fun `a wipe only ever shows a contiguous band`() {
        listOf(PanelAnimation.BLINDS, PanelAnimation.TIDE, PanelAnimation.IRIS).forEach { id ->
            for (i in 0..20) {
                val f = PanelAnimation.frameAt(id, i / 20f, towardLeft = false)
                assertTrue("$id band is inverted at t=${i / 20f}", f.revealTo >= f.revealFrom)
                assertTrue("$id band leaves the panel", f.revealFrom >= -1e-3f && f.revealTo <= 1.001f)
            }
        }
    }

    @Test
    fun `the bouncy ones actually overshoot`() {
        // If they did not, they would be indistinguishable from the eased ones and the list would
        // be advertising a difference it does not deliver.
        val peak = (0..40).map { PanelAnimation.frameAt(PanelAnimation.SPRING, it / 40f, false).scaleX }.max()
        assertTrue("spring never passes its target (peak $peak)", peak > 1.01f)
    }

    @Test
    fun `a resting frame leaves a rectangle exactly where it was`() {
        // The glass behind a panel is placed from this. If it disagreed with the panel by so much
        // as a pixel once the animation was over, every panel would rest on a misaligned pane.
        PanelAnimation.ALL.forEach { id ->
            val f = PanelAnimation.frameAt(id, 1f, towardLeft = false)
            val box = PanelAnimation.bounds(100f, 200f, 400f, 700f, f, 0f, 0f)
            assertEquals("$id left", 100f, box[0], 1e-3f)
            assertEquals("$id top", 200f, box[1], 1e-3f)
            assertEquals("$id right", 400f, box[2], 1e-3f)
            assertEquals("$id bottom", 700f, box[3], 1e-3f)
        }
    }

    @Test
    fun `a scaled frame shrinks the box about its origin`() {
        val f = PanelAnimation.Frame(scaleX = 0.5f, scaleY = 0.5f, originX = 0f, originY = 0f)
        val box = PanelAnimation.bounds(100f, 200f, 400f, 700f, f, 0f, 0f)
        // Pinned at the top-left corner, half the size.
        assertEquals(100f, box[0], 1e-3f)
        assertEquals(200f, box[1], 1e-3f)
        assertEquals(250f, box[2], 1e-3f)
        assertEquals(450f, box[3], 1e-3f)
    }

    @Test
    fun `a wipe reports only the band that is drawn`() {
        val f = PanelAnimation.Frame(revealFrom = 0.25f, revealTo = 0.75f)
        val box = PanelAnimation.bounds(0f, 0f, 100f, 400f, f, 0f, 0f)
        assertEquals(100f, box[1], 1e-3f)
        assertEquals(300f, box[3], 1e-3f)
    }

    @Test
    fun `translation moves the box with the panel`() {
        val f = PanelAnimation.Frame(translationX = 10f, translationY = -4f)
        val box = PanelAnimation.bounds(0f, 0f, 100f, 100f, f, 25f, -10f)
        assertEquals(25f, box[0], 1e-3f)
        assertEquals(-10f, box[1], 1e-3f)
        assertEquals(125f, box[2], 1e-3f)
        assertEquals(90f, box[3], 1e-3f)
    }

    @Test
    fun `the reported box is never inside out`() {
        PanelAnimation.ALL.forEach { id ->
            for (i in 0..20) {
                val f = PanelAnimation.frameAt(id, i / 20f, towardLeft = true)
                val box = PanelAnimation.bounds(50f, 60f, 350f, 660f, f, f.translationX, f.translationY)
                assertTrue("$id right before left at t=${i / 20f}", box[2] >= box[0])
                assertTrue("$id bottom above top at t=${i / 20f}", box[3] >= box[1])
            }
        }
    }

    @Test
    fun `sanitize rejects what it does not know`() {
        assertEquals(PanelAnimation.POP, PanelAnimation.sanitize(null))
        assertEquals(PanelAnimation.POP, PanelAnimation.sanitize("helicopter"))
        assertEquals(PanelAnimation.TIDE, PanelAnimation.sanitize(PanelAnimation.TIDE))
    }

    @Test
    fun `every animation names a duration a person would notice but not wait for`() {
        PanelAnimation.ALL.forEach { id ->
            val d = PanelAnimation.durationMs(id)
            assertTrue("$id is too quick to see ($d ms)", d >= 120)
            assertTrue("$id outstays its welcome ($d ms)", d <= 450)
        }
    }
}
