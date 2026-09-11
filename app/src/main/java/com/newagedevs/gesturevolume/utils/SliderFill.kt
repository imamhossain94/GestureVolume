package com.newagedevs.gesturevolume.utils

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * How the Quick panel's filled portion behaves.
 *
 * Separate from [PanelAnimation], and it has to be: an entrance plays once, when a panel arrives,
 * and is over. This is what the fill does *while it sits there* — the difference between a bar
 * that has a value and a bar that is doing something. A battery charging, water finding its level,
 * a column of blocks lighting one after another.
 *
 * The identifiers are a persistence format, never renamed.
 *
 * The maths is here, away from the canvas, so the surface a tide draws and the row a pixel wall
 * lights can both be checked without a device.
 */
object SliderFill {

    /** A flat top edge that does not move. What the panel has always drawn. */
    const val SOLID = "solid"

    /** A bright band travels up the fill, over and over, the way a charging battery reads. */
    const val BATTERY = "battery"

    /** The top edge is a wave, and it drifts upward. */
    const val TIDE_UP = "tideUp"

    /** The same wave, drifting the other way. */
    const val TIDE_DOWN = "tideDown"

    /** The fill is a stack of blocks, lighting from the bottom up. */
    const val PIXEL_UP = "pixelUp"

    /** The same stack, lighting from the top down. */
    const val PIXEL_DOWN = "pixelDown"

    /** The whole fill breathes. */
    const val PULSE = "pulse"

    /** A highlight sweeps across the fill and away. */
    const val SHIMMER = "shimmer"

    /** Diagonal bands sliding along it, the way an indeterminate progress bar reads. */
    const val STRIPES = "stripes"

    /** A soft glow gathered at the top edge, rising and falling. */
    const val GLOW = "glow"

    /** A grid of lit dots, with a bright wave passing through it. */
    const val DOT_MATRIX = "dotMatrix"

    /** Soft coloured clouds, drifting and folding through each other. */
    const val NEBULA = "nebula"

    /** Neon scan lines and the occasional glitch band. */
    const val CYBERPUNK = "cyberpunk"

    /** Columns of glyphs falling, bright at the head and fading behind. */
    const val MATRIX_RAIN = "matrixRain"

    /** Carved marks rising through the fill, lit as they pass. */
    const val RUNE = "rune"

    val ALL = listOf(
        SOLID, TIDE_UP, TIDE_DOWN, DOT_MATRIX, NEBULA,
        CYBERPUNK, MATRIX_RAIN, RUNE, BATTERY, PIXEL_UP,
        PIXEL_DOWN, PULSE, SHIMMER, STRIPES, GLOW,
    )

    fun sanitize(value: String?): String = if (value in ALL) value!! else SOLID

    /** Whether this style needs a clock running while the panel is open. */
    fun isAnimated(id: String): Boolean = sanitize(id) != SOLID

    /** How long one cycle takes. The slow ones are the ones you would otherwise notice too much. */
    fun cycleMs(id: String): Int = when (sanitize(id)) {
        BATTERY -> 1800
        TIDE_UP, TIDE_DOWN -> 2600
        PIXEL_UP, PIXEL_DOWN -> 1600
        PULSE -> 2200
        SHIMMER -> 2000
        STRIPES -> 1200
        GLOW -> 2400
        DOT_MATRIX -> 2000
        // Slow. A nebula that hurried would be a lava lamp.
        NEBULA -> 7000
        CYBERPUNK -> 1500
        MATRIX_RAIN -> 2400
        RUNE -> 3200
        else -> 1
    }

    /** Whether the top edge of the fill is a wave rather than a straight line. */
    fun hasWave(id: String): Boolean = sanitize(id) == TIDE_UP || sanitize(id) == TIDE_DOWN

    /** Whether the fill is drawn as a stack of blocks rather than as one solid piece. */
    fun hasBlocks(id: String): Boolean = sanitize(id) == PIXEL_UP || sanitize(id) == PIXEL_DOWN

    /**
     * How far the wave's crest sits above the fill line at [x], 0..1 across the track's width.
     *
     * Returned in units of [WAVE_AMPLITUDE_DP], so the caller multiplies by density and gets
     * pixels. Two sine waves of different frequencies rather than one, because a single sine is a
     * shape nobody reads as water — the interference between two is what makes it look like a
     * surface rather than a ribbon.
     */
    fun waveAt(id: String, phase: Float, x: Float): Float {
        if (!hasWave(id)) return 0f
        val direction = if (sanitize(id) == TIDE_UP) 1f else -1f
        val p = (phase % 1f + 1f) % 1f
        val travel = p * 2f * PI.toFloat() * direction
        val primary = sin(x * 2f * PI.toFloat() + travel)
        val secondary = sin(x * 3.7f * PI.toFloat() - travel * 0.6f) * 0.45f
        return (primary + secondary) / 1.45f
    }

    /** How tall the crest of a tide is, in dp. Small: this is a surface, not a flag. */
    const val WAVE_AMPLITUDE_DP = 3.5f

    /**
     * How bright block [index] of [count] is, 0..1, with the bottom block at index 0.
     *
     * Every block stays lit — the fill is a value, and a block that went dark would be reading as
     * a value that had changed. What travels is a *brightening*, one block at a time, which is the
     * part that says "working" without the part that says "wrong".
     */
    fun blockGlow(id: String, phase: Float, index: Int, count: Int): Float {
        if (!hasBlocks(id)) return 0f
        if (count <= 0) return 0f
        val p = (phase % 1f + 1f) % 1f
        val head = if (sanitize(id) == PIXEL_UP) p else 1f - p
        val here = (index + 0.5f) / count
        // A narrow band around the travelling head, wrapping at both ends so the pass is seamless.
        val raw = abs(here - head)
        val distance = minOf(raw, 1f - raw)
        val reach = 0.22f
        return if (distance >= reach) 0f else 1f - distance / reach
    }

    /** How many blocks a track [heightPx] tall is cut into. */
    fun blockCount(heightPx: Float, densityPx: Float): Int {
        if (heightPx <= 0f || densityPx <= 0f) return 0
        return (heightPx / (BLOCK_PITCH_DP * densityPx)).toInt().coerceIn(3, 40)
    }

    /** Block height plus its gap, in dp. */
    const val BLOCK_PITCH_DP = 11f

    /** The share of a block's pitch that is drawn, the rest being the gap. */
    const val BLOCK_FILL_RATIO = 0.74f

    /**
     * The extra brightness the whole fill carries at [phase], 0..1.
     *
     * For the styles whose effect is the fill itself changing rather than something moving across
     * it. Everything else returns zero and pays nothing.
     */
    fun bodyGlow(id: String, phase: Float): Float {
        val p = (phase % 1f + 1f) % 1f
        return when (sanitize(id)) {
            PULSE -> (sin(p * 2f * PI.toFloat()) + 1f) / 2f * 0.35f
            GLOW -> (sin(p * 2f * PI.toFloat()) + 1f) / 2f * 0.22f
            else -> 0f
        }
    }

    /**
     * Where a travelling highlight sits, as a fraction of the fill's height measured from its top.
     *
     * Negative or greater than one means it is off the fill entirely, which is the gap between
     * passes — a sweep with no rest reads as a loading spinner rather than as a sheen.
     */
    fun sweepAt(id: String, phase: Float): Float {
        val p = (phase % 1f + 1f) % 1f
        return when (sanitize(id)) {
            // Travels bottom to top, then waits out the rest of the cycle off-screen.
            BATTERY -> 1.25f - p * 1.9f
            SHIMMER -> p * 1.9f - 0.45f
            else -> Float.NaN
        }
    }

    /** How tall a travelling highlight is, as a fraction of the fill's height. */
    const val SWEEP_HEIGHT = 0.28f

    /**
     * Whether the style paints its own picture over the fill rather than tinting it.
     *
     * The difference matters to the caller: a tinting style is drawn in the track's colour, so it
     * reads whatever the user has chosen, while these five bring a palette because the palette is
     * the idea. A nebula in one colour is a cloud; a matrix rain that is not green is just rain.
     */
    fun isPictorial(id: String): Boolean = when (sanitize(id)) {
        DOT_MATRIX, NEBULA, CYBERPUNK, MATRIX_RAIN, RUNE -> true
        else -> false
    }

    /**
     * The colours a pictorial style paints with, brightest first, as `0xAARRGGBB`.
     *
     * Alpha included, because what each of these is really made of is how much of the fill shows
     * through it.
     */
    fun palette(id: String): LongArray = when (sanitize(id)) {
        DOT_MATRIX -> longArrayOf(0xFF7CF9FF, 0x996BE8F2, 0x3D2E8F9B)
        NEBULA -> longArrayOf(0x8C7A5CFF, 0x805CB8FF, 0x66FF6FD8, 0x59FFC46B)
        CYBERPUNK -> longArrayOf(0xFF00F0FF, 0xFFFF2E88, 0x66FFE24B)
        MATRIX_RAIN -> longArrayOf(0xFFB9FFC8, 0xE034FF6A, 0x8014C94A, 0x2E0B7A2E)
        RUNE -> longArrayOf(0xFFFFD79B, 0xB3FF9E3D, 0x59A85B1E)
        else -> longArrayOf(0xFFFFFFFF)
    }

    /** How wide one cell of a grid style is, in dp. */
    const val CELL_DP = 7.5f

    /**
     * How lit the cell at ([column], [row]) is, 0..1.
     *
     * One function for the three grid styles, because what separates them is which way the wave
     * runs and what gets drawn in the cell, not how the wave is shaped. [columns] and [rows] are
     * the grid's size; [row] counts from the bottom, the way the fill does.
     */
    fun cellGlow(id: String, phase: Float, column: Int, row: Int, columns: Int, rows: Int): Float {
        if (columns <= 0 || rows <= 0) return 0f
        val p = (phase % 1f + 1f) % 1f
        return when (sanitize(id)) {
            DOT_MATRIX -> {
                // A diagonal wave, so the grid never reads as rows blinking in unison.
                val here = (column.toFloat() / columns) * 0.45f + (1f - row.toFloat() / rows) * 0.55f
                val d = abs(((here - p) % 1f + 1f) % 1f)
                val distance = minOf(d, 1f - d)
                (1f - distance / 0.3f).coerceAtLeast(0f)
            }

            MATRIX_RAIN -> {
                // Each column falls on its own offset and at its own speed, or the whole thing
                // descends like a blind rather than like rain.
                val seed = pseudoRandom(column)
                val speed = 0.6f + seed * 0.9f
                val head = ((p * speed + seed) % 1f)
                val here = 1f - row.toFloat() / rows
                val behind = ((here - head) % 1f + 1f) % 1f
                // Bright at the head, trailing off behind it, dark for most of the column.
                if (behind > TRAIL) 0f else 1f - behind / TRAIL
            }

            RUNE -> {
                val seed = pseudoRandom(column * 31 + row * 7)
                if (seed < 0.72f) return 0f
                val here = 1f - row.toFloat() / rows
                val d = abs(((here - p) % 1f + 1f) % 1f)
                val distance = minOf(d, 1f - d)
                (1f - distance / 0.26f).coerceAtLeast(0f)
            }

            else -> 0f
        }
    }

    /** How much of a rain column trails behind its head, as a fraction of the column. */
    private const val TRAIL = 0.55f

    /**
     * A stable number in 0..1 for [n].
     *
     * Deterministic on purpose: the panel is rebuilt every time it opens, and a rain whose columns
     * were seeded randomly would fall differently on each opening — which sounds harmless until
     * you notice it, at which point it reads as the animation restarting rather than continuing.
     */
    fun pseudoRandom(n: Int): Float {
        var x = n * 374761393 + 668265263
        x = (x xor (x shr 13)) * 1274126177
        return ((x xor (x shr 16)) and 0x7FFFFFFF) / 2147483647f
    }

    /**
     * Where the cyberpunk glitch band sits, as a fraction of the fill, or NaN for none.
     *
     * Present for a fraction of each cycle and absent for the rest. A glitch that is always there
     * is a stripe.
     */
    fun glitchAt(id: String, phase: Float): Float {
        if (sanitize(id) != CYBERPUNK) return Float.NaN
        val p = (phase % 1f + 1f) % 1f
        val window = 0.22f
        if (p > window) return Float.NaN
        val burst = (p / window)
        return pseudoRandom((phase * 3f).toInt() * 977) * 0.8f + burst * 0.1f
    }
}
