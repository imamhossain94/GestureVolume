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

    /** The top edge is a wave, and it drifts upward. */
    const val TIDE_UP = "tideUp"

    /** The same wave, drifting the other way. */
    const val TIDE_DOWN = "tideDown"

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

    /** Interfering colour fields, folding through each other. */
    const val PLASMA = "plasma"

    /** Scan bands with the colour split either side of them, flickering. */
    const val HOLOGRAM = "hologram"

    /** Curtains of light, leaning and shifting. */
    const val AURORA = "aurora"

    /** Sparks rising and going out. */
    const val EMBER = "ember"

    /** Rings going out from the fill line, one after another. */
    const val SONAR = "sonar"

    /** Traces on a board, lighting in sequence. */
    const val CIRCUIT = "circuit"

    /** Diagonal bands sliding along it, the way an indeterminate progress bar reads. */
    const val STRIPES = "stripes"

    /**
     * Every style, in the order they are offered.
     *
     * Five earlier ones — a charging band, two block walls, a breath and a sheen — are gone rather
     * than kept for compatibility. They were tints on a bar: technically animations, and nothing
     * anybody would choose twice. A preference holding one of them now sanitises to [SOLID], which
     * is the honest outcome; leaving them in the list to avoid that would have been keeping nine
     * options to protect five that were not worth having.
     */
    val ALL = listOf(
        SOLID, TIDE_UP, TIDE_DOWN, PLASMA, AURORA,
        HOLOGRAM, EMBER, SONAR, CIRCUIT, DOT_MATRIX,
        NEBULA, CYBERPUNK, MATRIX_RAIN, RUNE, STRIPES,
    )

    fun sanitize(value: String?): String = if (value in ALL) value!! else SOLID

    /** Whether this style needs a clock running while the panel is open. */
    fun isAnimated(id: String): Boolean = sanitize(id) != SOLID

    /** How long one cycle takes. The slow ones are the ones you would otherwise notice too much. */
    fun cycleMs(id: String): Int = when (sanitize(id)) {
        TIDE_UP, TIDE_DOWN -> 2600
        STRIPES -> 1200
        DOT_MATRIX -> 2000
        // Slow. A nebula that hurried would be a lava lamp.
        NEBULA -> 7000
        CYBERPUNK -> 1500
        MATRIX_RAIN -> 2400
        RUNE -> 3200
        PLASMA -> 5200
        AURORA -> 6000
        HOLOGRAM -> 1800
        EMBER -> 3000
        SONAR -> 2200
        CIRCUIT -> 2800
        else -> 1
    }

    /** Whether the top edge of the fill is a wave rather than a straight line. */
    fun hasWave(id: String): Boolean = sanitize(id) == TIDE_UP || sanitize(id) == TIDE_DOWN

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
     * Whether the style paints its own picture over the fill rather than tinting it.
     *
     * The difference matters to the caller: a tinting style is drawn in the track's colour, so it
     * reads whatever the user has chosen, while these five bring a palette because the palette is
     * the idea. A nebula in one colour is a cloud; a matrix rain that is not green is just rain.
     */
    fun isPictorial(id: String): Boolean = when (sanitize(id)) {
        DOT_MATRIX, NEBULA, CYBERPUNK, MATRIX_RAIN, RUNE,
        PLASMA, AURORA, HOLOGRAM, EMBER, SONAR, CIRCUIT -> true
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
        PLASMA -> longArrayOf(0xCC4BE3FF, 0xCC7A5CFF, 0xCCFF5CC2, 0xCCFFC15C)
        AURORA -> longArrayOf(0x996BFFC2, 0x8C5CD6FF, 0x73B98CFF, 0x66FFF3A8)
        HOLOGRAM -> longArrayOf(0xFF6FF7FF, 0x99FF4FA8, 0x66FFFFFF)
        EMBER -> longArrayOf(0xFFFFE9A8, 0xE6FF9D3D, 0x99FF5A1E)
        SONAR -> longArrayOf(0xFF7CFFB0, 0x8C2ED67A, 0x3D14803F)
        CIRCUIT -> longArrayOf(0xFF8CFFE0, 0xA62ED6A8, 0x4D147A5E)
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
