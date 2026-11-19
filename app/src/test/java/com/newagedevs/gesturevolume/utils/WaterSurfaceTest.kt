package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The Water style is a little simulation run on every frame for as long as the ripples last, on the
 * phone's main thread. These pin down what it promises: still water stays still and draws nothing, a
 * press spreads outward the same both ways, the ripples die away, and nothing a user can do — a key
 * held down, pressed as fast as it repeats — makes the water blow up.
 */
class WaterSurfaceTest {

    private fun surface() = WaterSurface(cols = 40, rows = 61)

    @Test
    fun `still water stays still`() {
        val water = surface()
        repeat(200) { water.step() }
        assertEquals(0f, water.energy(), 0f)
    }

    @Test
    fun `flat water draws nothing`() {
        val water = surface()
        val out = IntArray(water.cols * water.rows) { 0x7F7F7F7F }
        water.render(out, slope = 3f, highlight = 0xFFFFFFFF.toInt(), shadow = 0xFF000000.toInt(), tint = 0xFFFFFFFF.toInt())
        assertTrue(out.all { it == 0 })
    }

    @Test
    fun `a press spreads out into the screen`() {
        val water = surface()
        water.impulse(centreRow = 30f, halfRows = 6f, depthCells = 4f, amount = -1f)
        assertEquals(0f, water.heightAt(16, 30), 0f)
        var reached = 0f
        repeat(60) {
            water.step()
            reached = maxOf(reached, abs(water.heightAt(16, 30)))
        }
        assertTrue("the ring never got 16 cells in: $reached", reached > 0.01f)
    }

    @Test
    fun `a press in the middle spreads the same both ways along the edge`() {
        val water = surface()
        water.impulse(centreRow = 30f, halfRows = 6f, depthCells = 4f, amount = -1f)
        repeat(25) { water.step() }
        for (c in 0 until water.cols step 3) {
            for (k in 1..20) {
                assertEquals("col $c, $k rows out", water.heightAt(c, 30 - k), water.heightAt(c, 30 + k), 1e-4f)
            }
        }
    }

    @Test
    fun `the ripples die away`() {
        val water = surface()
        water.impulse(centreRow = 30f, halfRows = 6f, depthCells = 4f, amount = -1f)
        val start = water.energy()
        repeat(1500) { water.step() }
        assertTrue("still ${water.energy()} of $start", water.energy() < start * 0.01f)
    }

    @Test
    fun `a key held down at the fastest speed never blows the water up`() {
        val water = surface()
        repeat(3000) { i ->
            water.hold(centreRow = 30f, halfRows = 6f, depthCells = 4f, level = if (i % 40 < 20) 1.2f else 0f, pull = 0.3f)
            if (i % 9 == 0) water.impulse(centreRow = 30f, halfRows = 7.5f, depthCells = 7f, amount = -1.8f)
            water.step(speed2 = WaterSurface.MAX_SPEED2)
        }
        assertTrue("energy ${water.energy()}", water.energy().isFinite() && water.energy() < 20f)
    }

    @Test
    fun `a single cell knocked, every wave the grid can hold at once, never grows`() {
        // One cell is every wavelength at once, down to the two-cell checkerboard an unstable step
        // would blow up first.
        val water = WaterSurface(cols = 24, rows = 24)
        water.impulse(centreRow = 12f, halfRows = 0.9f, depthCells = 0.9f, amount = 1f)
        assertEquals(1f, water.heightAt(0, 12), 1e-6f)
        var most = 0f
        repeat(600) {
            water.step(speed2 = WaterSurface.MAX_SPEED2)
            most = maxOf(most, water.energy())
        }
        assertTrue("grew to $most", most <= 1f)
        assertTrue("still ${water.energy()}", water.energy() < 0.05f)
    }

    @Test
    fun `holding the drop raises the water at the button`() {
        val water = surface()
        water.hold(centreRow = 30f, halfRows = 6f, depthCells = 4f, level = 1f, pull = 1f)
        assertEquals(1f, water.heightAt(0, 30), 1e-6f)
        // Nothing outside the drop moves.
        assertEquals(0f, water.heightAt(10, 30), 0f)
        assertEquals(0f, water.heightAt(0, 45), 0f)
    }

    @Test
    fun `moving water is drawn, lit on one side and shaded on the other`() {
        val water = surface()
        water.impulse(centreRow = 30f, halfRows = 6f, depthCells = 4f, amount = -1f)
        repeat(12) { water.step() }
        val out = IntArray(water.cols * water.rows)
        water.render(out, slope = 3f, highlight = 0xFFFFFFFF.toInt(), shadow = 0xFF000000.toInt(), tint = 0xFFFFFFFF.toInt())
        val drawn = out.filter { (it ushr 24) > 8 }
        assertTrue(drawn.isNotEmpty())
        assertTrue("no light side", drawn.any { (it and 0xFF) > 200 })
        assertTrue("no dark side", drawn.any { (it and 0xFF) < 60 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a surface too small to step is refused`() {
        WaterSurface(cols = 2, rows = 10)
    }

    @Test
    fun `a pool with walls all round sends a wave back off its far side`() {
        val open = WaterSurface(cols = 40, rows = 41)
        val walled = WaterSurface(cols = 40, rows = 41, walls = true)
        for (pool in listOf(open, walled)) {
            pool.impulse(centreRow = 20f, halfRows = 3f, depthCells = 4f, amount = -1f)
            // Out to the far side and half way back.
            repeat(95) { pool.step() }
        }
        // The echo, coming back through the near half: the walled pool has one, the open pool
        // soaked its wave up at the far side.
        fun nearHalf(pool: WaterSurface): Float {
            var sum = 0f
            for (r in 0 until pool.rows) for (c in 0 until pool.cols / 2) sum += abs(pool.heightAt(c, r))
            return sum
        }
        assertTrue("walled ${nearHalf(walled)} vs open ${nearHalf(open)}", nearHalf(walled) > nearHalf(open) * 1.2f)
    }
}
