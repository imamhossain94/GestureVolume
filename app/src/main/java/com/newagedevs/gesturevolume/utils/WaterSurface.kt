package com.newagedevs.gesturevolume.utils

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A patch of water beside the buttons, for the button animation's Water style: the glass at the
 * edge behaves like the surface of a pool, so a press drops a ripple into it that runs out across the
 * screen, two presses' ripples cross each other, and each one bounces back off the edge of the glass
 * the way water bounces off the side of a pool.
 *
 * A height field on a grid, stepped by the wave equation — the same few lines every water ripple on
 * a screen has been made of since the demo scene — in the bump's upright terms: a column is a step
 * inward from the glass, a row a step along the edge towards the bottom of the phone. Column 0 is
 * the glass's edge, and waves reflect from it. The other three sides are open water: a sponge along
 * them soaks the waves up, so the window's own edges never show as walls.
 *
 * Nothing here touches Android. [render] lights the surface into ARGB pixels the view hands to a
 * bitmap; everything else is arithmetic on two arrays, and is tested on its own.
 */
class WaterSurface(
    val cols: Int,
    val rows: Int,
    /**
     * Whether all four sides are walls, as a pool's are: for water that fills the whole screen, whose
     * own edges are the pool's sides, so a ripple that reaches one comes back off it. Without, only
     * column 0 — the glass at the button — is a wall, and the other three sides soak the waves up so
     * the edges of a patch of water never show.
     */
    val walls: Boolean = false,
) {

    init {
        require(cols >= 3 && rows >= 3) { "a surface needs at least 3 x 3 cells, not $cols x $rows" }
    }

    private val size = cols * rows
    private var height = FloatArray(size)
    private var previous = FloatArray(size)
    private var next = FloatArray(size)

    /**
     * Per cell, what each step keeps of the water near the open sides: 1 in open water, less and less
     * towards the window's edge, so a wave that reaches it is soaked up instead of bouncing back.
     */
    private val sponge = FloatArray(size)

    /** Per cell, how much of the light shows: none at the open sides, so the patch has no edge. */
    private val fade = FloatArray(size)

    init {
        if (walls) {
            // Walls, but not hard ones: a thin band along the far sides takes some of each wave,
            // so a ripple comes back off the screen's edge as a soft echo rather than a second
            // ripple as strong as the first, crossing the next into a crumple.
            fade.fill(1f)
            val bandCols = (cols * WALL_BAND).coerceAtLeast(2f)
            val bandRows = (rows * WALL_BAND).coerceAtLeast(2f)
            for (r in 0 until rows) {
                val fromEnd = min(r, rows - 1 - r) / bandRows
                for (c in 0 until cols) {
                    val fromFar = (cols - 1 - c) / bandCols
                    val near = (1f - min(fromEnd, fromFar)).coerceIn(0f, 1f)
                    sponge[r * cols + c] = 1f - WALL_SOAK * near * near
                }
            }
        } else for (r in 0 until rows) {
            // Along the edge: open at both ends.
            val fromEnd = min(r, rows - 1 - r).toFloat() / (rows * END_BAND).coerceAtLeast(1f)
            for (c in 0 until cols) {
                // Inward: the glass at column 0 is a wall; the far side is open.
                val fromFar = (cols - 1 - c).toFloat() / (cols * FAR_BAND).coerceAtLeast(1f)
                val open = min(fromEnd, fromFar).coerceIn(0f, 1f)
                val soak = 1f - open
                sponge[r * cols + c] = 1f - SPONGE * soak * soak
                fade[r * cols + c] = ButtonBump.smootherstep(min(fromEnd * 1.4f, fromFar * 1.2f))
            }
        }
    }

    /** Flat, still water. */
    fun clear() {
        height.fill(0f)
        previous.fill(0f)
        next.fill(0f)
    }

    /**
     * How far the surface is from flat and still: the largest height or movement anywhere. Below a
     * few thousandths nothing can be seen, and the view stops drawing.
     */
    fun energy(): Float {
        var most = 0f
        for (i in 0 until size) {
            val h = abs(height[i])
            val v = abs(height[i] - previous[i])
            if (h > most) most = h
            if (v > most) most = v
        }
        return most
    }

    /** The height at a cell: for tests, and for anything that wants to read the water. */
    fun heightAt(col: Int, row: Int): Float = height[row * cols + col]

    /**
     * How strongly a cell belongs to a press at the glass: half an ellipse standing on the edge, as
     * long as the button ([centreRow] ± [halfRows]) and [depthCells] deep, highest at the glass and
     * falling smoothly to nothing at its rim. Round, so the ring a press sends out is round from the
     * start rather than the shape of a box.
     */
    private fun shape(col: Int, row: Int, centreRow: Float, halfRows: Float, depthCells: Float): Float {
        if (halfRows <= 0f || depthCells <= 0f) return 0f
        val dx = col / depthCells
        val dy = (row - centreRow) / halfRows
        val d2 = dx * dx + dy * dy
        if (d2 >= 1f) return 0f
        return 1f - ButtonBump.smootherstep(sqrt(d2))
    }

    /**
     * A press: the water at the button is pushed by [amount] — down for a press, which is what makes
     * the ring that runs out. Added to the surface as it stands, so presses in quick succession
     * stack, and their ripples cross.
     */
    fun impulse(centreRow: Float, halfRows: Float, depthCells: Float, amount: Float) {
        forSource(centreRow, halfRows, depthCells) { i, s -> height[i] += amount * s }
    }

    /**
     * Holds the water at the button towards a mound [level] high, by [pull] of the way each step: the
     * drop that rises out of the glass while the button is pressed and sinks back after. Moved, the
     * mound pushes the water round it, so it rising, wobbling and falling all send out ripples.
     */
    fun hold(centreRow: Float, halfRows: Float, depthCells: Float, level: Float, pull: Float) {
        val k = pull.coerceIn(0f, 1f)
        forSource(centreRow, halfRows, depthCells) { i, s ->
            height[i] += (level * s - height[i]) * k * s
        }
    }

    private inline fun forSource(centreRow: Float, halfRows: Float, depthCells: Float, apply: (Int, Float) -> Unit) {
        val firstRow = (centreRow - halfRows).toInt().coerceIn(0, rows - 1)
        val lastRow = (centreRow + halfRows + 1f).toInt().coerceIn(0, rows - 1)
        val lastCol = (depthCells + 1f).toInt().coerceIn(0, cols - 1)
        for (r in firstRow..lastRow) {
            for (c in 0..lastCol) {
                val s = shape(c, r, centreRow, halfRows, depthCells)
                if (s > 0f) apply(r * cols + c, s)
            }
        }
    }

    /**
     * One step of the wave equation, with a touch of viscosity.
     *
     * The viscosity damps a wave by how short it is: a ripple a few cells long is left alone, and the
     * grid-sized chatter a square grid makes of a wave's tail — the checkerboard that otherwise breaks
     * up the last of the rings — is gone within a few steps.
     *
     * @param speed2 the wave's speed squared, in cells per step. With the viscosity, the grid stays
     *   stable up to [MAX_SPEED2].
     * @param damping how much of its movement the water keeps each step: the ripples die away.
     */
    fun step(speed2: Float = DEFAULT_SPEED2, damping: Float = DEFAULT_DAMPING) {
        val c2 = speed2.coerceIn(0f, MAX_SPEED2)
        val h = height
        val p = previous
        val n = next
        for (r in 0 until rows) {
            val row = r * cols
            for (c in 0 until cols) {
                val i = row + c
                val here = h[i]
                // The glass is a wall: the water beyond it mirrors the water at it, so a wave comes
                // back off it the right way up. The open sides see still water past them, unless
                // they are walls too.
                val left = if (c == 0) here else h[i - 1]
                val right = if (c == cols - 1) (if (walls) here else 0f) else h[i + 1]
                val up = if (r == 0) (if (walls) here else 0f) else h[i - cols]
                val down = if (r == rows - 1) (if (walls) here else 0f) else h[i + cols]
                val laplacian = left + right + up + down - 4f * here
                // The same of the step before, for the viscosity: it acts on how the curvature moves.
                val before = p[i]
                val pl = if (c == 0) before else p[i - 1]
                val pr = if (c == cols - 1) (if (walls) before else 0f) else p[i + 1]
                val pu = if (r == 0) (if (walls) before else 0f) else p[i - cols]
                val pd = if (r == rows - 1) (if (walls) before else 0f) else p[i + cols]
                val laplacianBefore = pl + pr + pu + pd - 4f * before
                val velocity = (here - before) * damping
                n[i] = (here + velocity + c2 * laplacian + VISCOSITY * (laplacian - laplacianBefore)) * sponge[i]
            }
        }
        // The new surface becomes the current one, and the current the previous.
        previous = h
        height = n
        next = p
    }

    /**
     * Lights the surface into [out], one ARGB pixel per cell (not premultiplied, as a bitmap's
     * `setPixels` takes them): the bright side of each ripple in [highlight], the dark side in
     * [shadow], and a breath of [tint] wherever the water is moving, so the ripples read as water on
     * any wallpaper — a refraction's light and dark, without the screen behind having to be read.
     *
     * @param slope how steep a ripple of a given height looks: the strength of the light and dark.
     */
    fun render(out: IntArray, slope: Float, highlight: Int, shadow: Int, tint: Int) {
        require(out.size >= size)
        val h = height
        val hr = (highlight ushr 16 and 0xFF).toFloat()
        val hg = (highlight ushr 8 and 0xFF).toFloat()
        val hb = (highlight and 0xFF).toFloat()
        val sr = (shadow ushr 16 and 0xFF).toFloat()
        val sg = (shadow ushr 8 and 0xFF).toFloat()
        val sb = (shadow and 0xFF).toFloat()
        val tr = (tint ushr 16 and 0xFF).toFloat()
        val tg = (tint ushr 8 and 0xFF).toFloat()
        val tb = (tint and 0xFF).toFloat()
        for (r in 0 until rows) {
            val row = r * cols
            val upRow = if (r == 0) row else row - cols
            val downRow = if (r == rows - 1) row else row + cols
            for (c in 0 until cols) {
                val i = row + c
                val f = fade[i]
                if (f <= 0f) {
                    out[i] = 0
                    continue
                }
                // The slope across the cell, and the normal of the surface there.
                val gx = h[row + min(c + 1, cols - 1)] - h[row + max(c - 1, 0)]
                val gy = h[downRow + c] - h[upRow + c]
                val nx = -gx * slope
                val ny = -gy * slope
                val inv = 1f / sqrt(nx * nx + ny * ny + 1f)
                // Lit side and shaded side, against the flat water's own light.
                val diffuse = (nx * LIGHT_X + ny * LIGHT_Y + LIGHT_Z) * inv - LIGHT_Z
                // The glint: a tight specular highlight, with the flat water's own taken away so
                // still water shows nothing at all.
                var s = ((nx * HALF_X + ny * HALF_Y + HALF_Z) * inv).coerceAtLeast(0f)
                s *= s; s *= s; s *= s; s *= s; s *= s // ^32
                val glint = ((s - FLAT_GLINT) / (1f - FLAT_GLINT)).coerceAtLeast(0f)

                val aHigh = min(1f, glint * GLINT_GAIN + max(diffuse, 0f) * LIT_GAIN) * f
                val aShade = min(SHADE_MAX, max(-diffuse, 0f) * SHADE_GAIN) * f
                val aTint = min(TINT_MAX, abs(h[i]) * TINT_GAIN) * f

                // Tint, then shade over it, then light over that, premultiplied as it goes.
                var a = aTint
                var cr = tr * aTint
                var cg = tg * aTint
                var cb = tb * aTint
                cr = cr * (1f - aShade) + sr * aShade
                cg = cg * (1f - aShade) + sg * aShade
                cb = cb * (1f - aShade) + sb * aShade
                a += aShade * (1f - a)
                cr = cr * (1f - aHigh) + hr * aHigh
                cg = cg * (1f - aHigh) + hg * aHigh
                cb = cb * (1f - aHigh) + hb * aHigh
                a += aHigh * (1f - a)

                if (a < 1f / 255f) {
                    out[i] = 0
                    continue
                }
                val alpha = (a * 255f + 0.5f).toInt().coerceIn(0, 255)
                val red = (cr / a + 0.5f).toInt().coerceIn(0, 255)
                val green = (cg / a + 0.5f).toInt().coerceIn(0, 255)
                val blue = (cb / a + 0.5f).toInt().coerceIn(0, 255)
                out[i] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
    }

    companion object {
        /**
         * The viscosity. The grid stays stable while the speed squared plus twice this is at most a
         * half — the shortest wave it can hold, two cells long, must not grow — hence [MAX_SPEED2].
         */
        const val VISCOSITY = 0.04f
        const val MAX_SPEED2 = 0.5f - 2f * VISCOSITY
        const val DEFAULT_SPEED2 = 0.4f
        const val DEFAULT_DAMPING = 0.988f

        /** How much of each end, and of the far side, is open water that soaks waves up. */
        private const val END_BAND = 0.26f
        private const val FAR_BAND = 0.36f

        /** How much a step takes away at the very edge of the open water. */
        private const val SPONGE = 0.24f

        /** For walls: how much of each side is the band that softens the echo, and how much a step takes at the wall. */
        private const val WALL_BAND = 0.07f
        private const val WALL_SOAK = 0.07f

        // The light: from above, towards the top of the phone and out of the glass.
        private const val LX = -0.42f
        private const val LY = -0.56f
        private const val LZ = 0.72f
        private val L_LEN = sqrt(LX * LX + LY * LY + LZ * LZ)
        private val LIGHT_X = LX / L_LEN
        private val LIGHT_Y = LY / L_LEN
        private val LIGHT_Z = LZ / L_LEN

        // Halfway between the light and the eye, which looks straight down at the glass.
        private val HX = LIGHT_X
        private val HY = LIGHT_Y
        private val HZ = LIGHT_Z + 1f
        private val H_LEN = sqrt(HX * HX + HY * HY + HZ * HZ)
        private val HALF_X = HX / H_LEN
        private val HALF_Y = HY / H_LEN
        private val HALF_Z = HZ / H_LEN

        /** The glint flat water would have, taken away so it shows none. */
        private val FLAT_GLINT = run {
            var s = HALF_Z
            repeat(5) { s *= s }
            s
        }

        private const val GLINT_GAIN = 1.15f
        private const val LIT_GAIN = 0.85f
        private const val SHADE_GAIN = 0.8f
        private const val SHADE_MAX = 0.5f
        private const val TINT_GAIN = 0.4f
        private const val TINT_MAX = 0.16f
    }
}
