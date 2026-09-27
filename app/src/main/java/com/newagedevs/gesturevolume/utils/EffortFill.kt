package com.newagedevs.gesturevolume.utils

import kotlin.math.ceil

/**
 * The Effort fill: the Quick panel's track as an effort picker, the control AI coding tools put
 * in front of a hard problem — Low, High, Extra high, Max, Ultra max — laid up the track and lit to
 * the level the finger is on.
 *
 * After the pickers themselves: Claude Code's `/effort` slider, whose stops run from faster to
 * smarter, its VS Code picker, which counts the level in lit dots, and the reasoning pickers in
 * Codex. The panel's level is still a volume or a brightness; this is how it is dressed.
 *
 * Two looks, from those two kinds of picker: [STEPS], a stack of segments like the slider's stops,
 * and [DOTS], one bar with a row of dots riding its level. Both name the level, and both work
 * harder as it rises — a sheen that sweeps faster, a glow at Max, the colours of the spectrum
 * running through it at Ultra max.
 *
 * Plain maths, like [PixelFill], so the levels can be checked without a device. Look identifiers
 * are a persistence format, never renamed.
 */
object EffortFill {

    // ---- looks ------------------------------------------------------------------------------------

    /** A segment for each level, stacked up the track, lit to the level: the slider's stops. */
    const val STEPS = "steps"

    /** One bar lit to the level, and the level counted in dots beside its name: the picker's dots. */
    const val DOTS = "dots"

    /** In the order they are offered. */
    val LOOKS: List<String> = listOf(STEPS, DOTS)

    fun sanitize(id: String?): String = if (id in LOOKS) id!! else STEPS

    // ---- levels -----------------------------------------------------------------------------------

    /** Low, High, Extra high, Max, Ultra max: an equal share of the track each, from the bottom. */
    const val LEVELS = 5

    /** The highest level, the one the spectrum runs through. */
    const val ULTRA = LEVELS - 1

    /**
     * The level [value] 0..1 is in: the stop the lit part ends in. A value exactly on a boundary is
     * the stop below, which it has filled, rather than the one above, which it has not begun.
     */
    fun levelAt(value: Float): Int =
        (ceil(value.coerceIn(0f, 1f) * LEVELS - 1e-4f).toInt() - 1).coerceIn(0, LEVELS - 1)

    /**
     * The picker's colours, as `0xAARRGGBB`: one for each level below the top, cool to hot, then the
     * seven the top level runs through. A slate, a blue, a violet and an orange, so that a level can
     * be told from the one below it at a glance, as a picker's stops can.
     */
    fun palette(): LongArray = longArrayOf(
        0xFF8E9AAF, 0xFF4C8DFF, 0xFF8B5CF6, 0xFFFF7A45,
        0xFFEB5F57, 0xFFF58B57, 0xFFFAC35F, 0xFF91C882, 0xFF82AADC, 0xFF9B82C8, 0xFFC882B4,
    )

    /** Where the top level's colours start in [palette]. */
    const val SPECTRUM_FIRST = LEVELS - 1

    /** How many colours the top level runs through. */
    const val SPECTRUM_COUNT = 7

    /** [palette], recoloured with the user's [custom] colours when there are any, as ints. */
    fun paletteWith(custom: IntArray?): IntArray =
        SliderFill.recolour(palette(), custom).let { p -> IntArray(p.size) { p[it].toInt() } }

    /**
     * How long the sheen takes to sweep the lit part, in seconds at speed 1: slower low down and
     * quicker at every level above, the picker working harder the harder it is asked to.
     */
    fun sweepSeconds(level: Int): Float = SWEEP_SECONDS[level.coerceIn(0, LEVELS - 1)]

    private val SWEEP_SECONDS = floatArrayOf(3.2f, 2.5f, 1.9f, 1.4f, 1.05f)

    /** How bright the sheen is at each level, out of one. */
    fun sweepStrength(level: Int): Float = SWEEP_STRENGTH[level.coerceIn(0, LEVELS - 1)]

    private val SWEEP_STRENGTH = floatArrayOf(0.22f, 0.28f, 0.34f, 0.42f, 0.5f)

    /** Whether a level glows around its lit part: Max and above. */
    fun glows(level: Int): Boolean = level >= LEVELS - 2

    /** How long a level announces itself when the finger reaches it, in seconds. */
    const val ARRIVAL_SECONDS = 0.45f

    /** One breath of the glow at Max and above, in seconds. */
    const val GLOW_BREATH_SECONDS = 1.6f

    /** One pass of the spectrum up the track at the top level, in seconds. */
    const val SPECTRUM_SECONDS = 2.4f

    // ---- the settings -----------------------------------------------------------------------------

    /** Everything the user tunes about the Effort fill, clamped to what can be drawn. */
    data class Style(
        val look: String = STEPS,
        /** How fast the sheen, the glow and the spectrum move: 1 is their own pace. */
        val speed: Float = DEFAULT_SPEED,
        /** Whether the level is named on the track, as well as lit. */
        val labels: Boolean = true,
    ) {
        fun sanitized(): Style = Style(
            look = sanitize(look),
            speed = if (speed.isNaN()) DEFAULT_SPEED else speed.coerceIn(MIN_SPEED, MAX_SPEED),
            labels = labels,
        )
    }

    const val DEFAULT_SPEED = 1f
    const val MIN_SPEED = 0.25f
    const val MAX_SPEED = 3f

    /** Where the clock wraps, in seconds. The breath and the spectrum both divide it. */
    const val TIME_WRAP_S = 3600f
}
