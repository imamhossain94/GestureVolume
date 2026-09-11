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

    /** An LED panel: every light faintly there, a wave lighting them across. */
    const val DOT_MATRIX = "dotMatrix"

    /** Soft coloured clouds, drifting and folding through each other. */
    const val NEBULA = "nebula"

    /** A synthwave sun: sunset colours cut by bands sliding down, a neon rim on the level. */
    const val CYBERPUNK = "cyberpunk"

    /** Digital rain: characters falling in columns, brightest at the head. */
    const val MATRIX_RAIN = "matrixRain"

    /** Runes carved in dark stone, lighting gold in turn, dust in the light. */
    const val RUNE = "rune"

    /** A lava lamp: slow, smooth plasma under glass. */
    const val PLASMA = "plasma"

    /** A hologram: pastel foil drifting over fine scan lines, a beam sweeping up. */
    const val HOLOGRAM = "hologram"

    /** Curtains of light swaying over a night sky. */
    const val AURORA = "aurora"

    /** Sparks rising and going out. */
    const val EMBER = "ember"

    /** Soft rings leaving the level, like the ripples from a drop. */
    const val SONAR = "sonar"

    /** A circuit board, pulses of light running up its traces. */
    const val CIRCUIT = "circuit"

    /** Soft diagonal bands of the track's colour drifting across the fill, under a gloss. */
    const val STRIPES = "stripes"

    /** Water: deep below, bright at a rippling surface that throws light down into it, bubbles rising. */
    const val LIQUID = "liquid"

    /** A level meter: segments lit green to red by height over their own glow, a peak hold over the level. */
    const val VU_METER = "vuMeter"

    /** Three signals crossing up the fill, glowing where they meet. Louder swings wider. */
    const val WAVEFORM = "waveform"

    /** A sun on the level, beams turning slowly down into a dusk sky, dust drifting in the light. */
    const val SUNRISE = "sunrise"

    /** Iridescent foil: pastel colours flowing upward, a glint crossing it now and then. */
    const val SPECTRUM = "spectrum"

    /** Deep space, with stars at three depths drifting past each other. */
    const val GALAXY = "galaxy"

    /** Satin ribbons weaving up the fill, a sheen sliding across each as it turns. */
    const val SILK = "silk"

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
        SOLID, LIQUID, VU_METER, WAVEFORM, SUNRISE,
        SPECTRUM, GALAXY, SILK, TIDE_UP, AURORA,
        PLASMA, HOLOGRAM, NEBULA, EMBER, SONAR,
        CIRCUIT, DOT_MATRIX, CYBERPUNK, MATRIX_RAIN, RUNE,
        TIDE_DOWN, STRIPES,
    )

    fun sanitize(value: String?): String = if (value in ALL) value!! else SOLID

    /** Whether this style needs a clock running while the panel is open. */
    fun isAnimated(id: String): Boolean = sanitize(id) != SOLID

    /** How long one cycle takes. The slow ones are the ones you would otherwise notice too much. */
    fun cycleMs(id: String): Int = when (sanitize(id)) {
        TIDE_UP, TIDE_DOWN -> 2600
        STRIPES -> 2400
        DOT_MATRIX -> 2000
        // Slow. A nebula that hurried would be a lava lamp.
        NEBULA -> 7000
        CYBERPUNK -> 2400
        MATRIX_RAIN -> 3000
        RUNE -> 4200
        PLASMA -> 9000
        AURORA -> 9000
        HOLOGRAM -> 4000
        EMBER -> 3000
        SONAR -> 3600
        CIRCUIT -> 3200
        LIQUID -> 6000
        VU_METER -> 1400
        WAVEFORM -> 2400
        // Slow, and a full turn of the fan is one ray's width: the rays are identical, so moving
        // by exactly one spacing is a seamless loop however long the cycle is.
        SUNRISE -> 9000
        SPECTRUM -> 7000
        // Long, because the stars travel at whole-number speeds (see the note on loops) and the
        // slowest layer needs a cycle this long to drift rather than stream.
        GALAXY -> 24000
        SILK -> 7000
        else -> 1
    }

    /** Whether the top edge of the fill is a wave rather than a straight line. */
    fun hasWave(id: String): Boolean = when (sanitize(id)) {
        TIDE_UP, TIDE_DOWN, LIQUID -> true
        else -> false
    }

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
        val sid = sanitize(id)
        val direction = if (sid == TIDE_DOWN) -1f else 1f
        val p = (phase % 1f + 1f) % 1f
        val travel = p * 2f * PI.toFloat() * direction
        // Both waves travel a whole number of wavelengths per cycle — one forward, one back — so
        // the last frame of a cycle is the first frame of the next. The second used to travel at
        // six tenths of the first, which looked richer and jumped once every cycle: a surface
        // that twitches every couple of seconds is a surface that looks broken.
        val primary = sin(x * 2f * PI.toFloat() + travel)
        val secondary = sin(x * 3.7f * PI.toFloat() - travel) * 0.45f
        // Liquid carries a smaller, calmer surface than a tide: it is meant to look at rest.
        val scale = if (sid == LIQUID) 0.5f else 1f
        return scale * (primary + secondary) / 1.45f
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
        PLASMA, AURORA, HOLOGRAM, EMBER, SONAR, CIRCUIT,
        LIQUID, VU_METER, WAVEFORM, SUNRISE, SPECTRUM, GALAXY, SILK -> true
        else -> false
    }

    /**
     * The colours a pictorial style paints with, brightest first, as `0xAARRGGBB`.
     *
     * Alpha included, because what each of these is really made of is how much of the fill shows
     * through it.
     */
    fun palette(id: String): LongArray = when (sanitize(id)) {
        DOT_MATRIX -> longArrayOf(0xFFB8FBFF, 0xFF3FB7D6, 0x3389E8FF)
        NEBULA -> longArrayOf(0x8C7A5CFF, 0x805CB8FF, 0x66FF6FD8, 0x59FFC46B)
        CYBERPUNK -> longArrayOf(0xFFFFE24B, 0xFFFF9A3D, 0xFFFF2E88, 0xFF5B1A8C, 0xFF1B0B3A, 0xFF00F0FF)
        MATRIX_RAIN -> longArrayOf(0xFFE2FFE8, 0xFF3CFF74, 0xFF0B6E2B)
        RUNE -> longArrayOf(0xFFFFE2B0, 0xFFFFA43D, 0xFF6B4122)
        PLASMA -> longArrayOf(0xFF2B0A5C, 0xFFE0318F, 0xFFFF9F43, 0xFFFFE66B, 0xFF33D1FF)
        AURORA -> longArrayOf(0xFF5CFFB0, 0xFF3DE0FF, 0xFFB77CFF, 0xFF9DFF7C)
        HOLOGRAM -> longArrayOf(0xFF6FF7FF, 0xFFB79CFF, 0xFFFF9AD5)
        EMBER -> longArrayOf(0xFFFFE9A8, 0xE6FF9D3D, 0x99FF5A1E)
        SONAR -> longArrayOf(0xFF8CFFD8, 0xFF2ED6A0)
        CIRCUIT -> longArrayOf(0xFF9CFFE6, 0xFF1F7A5A)
        LIQUID -> longArrayOf(0xFF7FE6FF, 0xFF2D8FE6, 0xFF0B2A6B, 0xFFFFFFFF)
        VU_METER -> longArrayOf(0xFF3DE68A, 0xFFFFC23D, 0xFFFF4F61, 0xFFFFFFFF)
        WAVEFORM -> longArrayOf(0xFF4FE3FF, 0xFFFF5CC8, 0xFF9B7CFF)
        SUNRISE -> longArrayOf(0xFFFFE3A1, 0xFFFF8A3D, 0xFFC2386B, 0xFF3A1450)
        SPECTRUM -> longArrayOf(0xFFFF9AD5, 0xFFB79CFF, 0xFF8CD9FF, 0xFF8CFFD1, 0xFFFFE98C, 0xFFFFB38C)
        GALAXY -> longArrayOf(0xFF120A2E, 0xFF34207A, 0xFFFFFFFF, 0xFFBFD0FF)
        SILK -> longArrayOf(0xFFFF7AA8, 0xFFFFB08A, 0xFFC3A0FF)
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
                (1f - distance / 0.22f).coerceAtLeast(0f)
            }

            MATRIX_RAIN -> {
                // Each column falls on its own offset and at its own speed, or the whole thing
                // descends like a blind rather than like rain.
                val seed = pseudoRandom(column)
                // Whole numbers only, so each head is back where it started when the cycle wraps.
                // A fractional speed made every column jump once a cycle.
                val speed = 1f + (seed * 2f).toInt()
                val head = ((p * speed + seed) % 1f)
                val here = 1f - row.toFloat() / rows
                // Measured up the column from the head, so the head leads the fall and the trail
                // lies where it has been. The other way round, the rain fell tail first.
                val behind = ((head - here) % 1f + 1f) % 1f
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
    private const val TRAIL = 0.4f

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
}
