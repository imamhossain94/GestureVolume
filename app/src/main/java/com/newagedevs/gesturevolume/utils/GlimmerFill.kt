package com.newagedevs.gesturevolume.utils

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Glimmer fill: the Quick panel's track as a field of fine dots that brighten from nothing at
 * the start into a lavender glimmer at the level, twinkling as they go, with a white handle on the
 * level and six faint stops down the track.
 *
 * After Claude Code's effort slider in VS Code, the dotted one, at its top stop. Measured off a
 * capture of it rather than remembered: five dots across, half dot and half gap; the first third
 * of the way plain grey, the colour arriving with the brightness; a muted lavender at the handle;
 * one dot in five or so caught mid-twinkle; the stops a shade brighter than the dots, on the middle
 * line; and a handle the height of the track, four fifths as long as the track is wide.
 *
 * Plain maths, like [EffortFill], so the loop and the handle's travel can be checked without a
 * device. Every period divides [TIME_WRAP_S], so the clock wrapping is not a frame anyone can see.
 */
object GlimmerFill {

    // ---- the grid ---------------------------------------------------------------------------------

    /**
     * The spacing of the dots, in dp. Five across the panel's default width, as the slider has five
     * across its track, and more across a wider panel rather than bigger dots.
     */
    const val PITCH_DP = 5.6f

    const val MIN_COLUMNS = 2

    /** Enough for the widest panel. More would be a frame of several hundred dots for no one to see. */
    const val MAX_COLUMNS = 10

    /** How many dots go across a track [widthPx] wide. */
    fun columnsFor(widthPx: Float, density: Float): Int =
        (widthPx / (PITCH_DP * density)).roundToInt().coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    /** A dot's radius, as a fraction of the spacing: half of the spacing is dot and half is gap. */
    const val DOT = 0.25f

    /** A stop's radius, as a fraction of the spacing: half as big again as a dot. */
    const val STOP_DOT = 0.375f

    // ---- the handle -------------------------------------------------------------------------------

    /** The handle is the track's width across and this much of it along. */
    const val HANDLE_RATIO = 0.8f
    const val HANDLE_MIN_DP = 10f
    const val HANDLE_MAX_DP = 30f

    /** How long the handle is along a track [widthPx] wide and [lengthPx] long. */
    fun handleLength(widthPx: Float, lengthPx: Float, density: Float): Float =
        (widthPx * HANDLE_RATIO)
            .coerceIn(HANDLE_MIN_DP * density, HANDLE_MAX_DP * density)
            .coerceAtMost(lengthPx / 3f)

    /**
     * How far the handle's centre is from the start of a track [length] long, at [value] 0..1.
     *
     * Kept whole inside the track, as a slider's thumb is: at 0 it sits against the start and at 1
     * against the end, and the value runs along the stretch between. The finger is on the handle all
     * the way, off its centre by at most half of it, at either end.
     */
    fun handleCentre(value: Float, length: Float, handle: Float): Float =
        handle / 2f + value.coerceIn(0f, 1f) * (length - handle).coerceAtLeast(0f)

    /** Six stops, as the slider has: one every fifth of the way. */
    const val STOPS = 6

    /** Where stop [k] of [STOPS] is from the start: where the handle's centre sits at k/5. */
    fun stopAt(k: Int, length: Float, handle: Float): Float =
        handleCentre(k.toFloat() / (STOPS - 1), length, handle)

    // ---- the light --------------------------------------------------------------------------------

    /**
     * How lit a dot [t] of the way from the start to the level is, 0..1: nothing at the start, all
     * of it at the level, and slow to begin, as the slider's first third is barely there.
     */
    fun ramp(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /** What shows of a dot above the level, and of one at the very start. */
    const val GHOST = 0.045f

    /** A lit dot at the level, before it twinkles. */
    const val LIT = 0.52f

    /** How much a twinkle adds at its peak, and the shimmer at its crest, before [energy]. */
    const val TWINKLE = 0.55f
    const val SHIMMER = 0.28f

    /** How strongly the lit dots haze the gaps between them. */
    const val HAZE = 0.45f

    /** How much of a stop shows. */
    const val STOP_ALPHA = 0.3f

    /**
     * How hard it works at [value]: the twinkles and the shimmer are three fifths as bright at the
     * bottom as at the top. A louder level glimmers more, never differently.
     */
    fun energy(value: Float): Float = 0.6f + 0.4f * value.coerceIn(0f, 1f)

    /** One base twinkle, in seconds. Each dot twinkles once, twice or three times in it. */
    const val TWINKLE_SECONDS = 2.4f

    /** How much of its own period a dot spends twinkling; the rest it rests. */
    const val TWINKLE_DUTY = 0.32f

    /**
     * How bright dot ([column], [row]) is twinkling at [time] seconds, 0..1.
     *
     * Each dot on its own seeded beat, so the grid glitters rather than blinks. A whole number of
     * twinkles per [TWINKLE_SECONDS], so every dot is where it started whenever the clock wraps.
     * Most dots twinkle faintly and a few brightly: the capture has one in five or so caught lit.
     */
    fun twinkle(column: Int, row: Int, time: Float): Float {
        val rate = 1 + (SliderFill.pseudoRandom(column * 7919 + row * 104729 + 17) * 3f).toInt().coerceAtMost(2)
        val offset = SliderFill.pseudoRandom(column * 104723 + row * 7907 + 5)
        val u = ((time * rate / TWINKLE_SECONDS + offset) % 1f + 1f) % 1f
        if (u >= TWINKLE_DUTY) return 0f
        val s = sin(PI.toFloat() * u / TWINKLE_DUTY)
        val strength = 0.35f + 0.65f * SliderFill.pseudoRandom(column * 31337 + row * 1009 + 3)
        return s * s * strength
    }

    /** One pass of the shimmer from the start into the handle, in seconds. */
    const val SHIMMER_SECONDS = 2.4f

    /** How wide the shimmer's band is, as a fraction of the lit part. */
    const val SHIMMER_WIDTH = 0.16f

    /**
     * The shimmer at a dot [t] of the way from the start to the level, at [time] seconds, 0..1: a
     * soft band rising into the handle once every [SHIMMER_SECONDS]. It starts below the start and
     * ends past the level, so it neither appears nor vanishes anywhere it can be seen.
     */
    fun shimmer(t: Float, time: Float): Float {
        val crest = -0.3f + 1.6f * (((time % SHIMMER_SECONDS) + SHIMMER_SECONDS) % SHIMMER_SECONDS) / SHIMMER_SECONDS
        val d = (t - crest) / SHIMMER_WIDTH
        return exp(-d * d)
    }

    /** Where the clock wraps, in seconds. Every period here divides it. */
    const val TIME_WRAP_S = 3600f

    // ---- the colours ------------------------------------------------------------------------------

    /**
     * The twinkle's peak, the lit dots at the level, and the lit dots where the light begins, as
     * `0xAARRGGBB`. The capture's lavender, a touch brighter for a panel on a darker track.
     */
    fun palette(): LongArray = longArrayOf(0xFFDCD4FF, 0xFF9A89C4, 0xFF8878BE)

    /** [palette], recoloured with the user's [custom] colours when there are any, as ints. */
    fun paletteWith(custom: IntArray?): IntArray =
        SliderFill.recolour(palette(), custom).let { p -> IntArray(p.size) { p[it].toInt() } }

    // ---- the settings -----------------------------------------------------------------------------

    /** Everything the user tunes about the Glimmer fill, clamped to what can be drawn. */
    data class Style(
        /** How fast the dots twinkle and the shimmer rises: 1 is their own pace. */
        val speed: Float = DEFAULT_SPEED,
        /** Whether a handle sits on the level, or the dots simply stop there. */
        val handle: Boolean = true,
        /** Whether the six stops are marked down the track. */
        val stops: Boolean = true,
    ) {
        fun sanitized(): Style = Style(
            speed = if (speed.isNaN()) DEFAULT_SPEED else speed.coerceIn(MIN_SPEED, MAX_SPEED),
            handle = handle,
            stops = stops,
        )
    }

    const val DEFAULT_SPEED = 1f
    const val MIN_SPEED = 0.25f
    const val MAX_SPEED = 3f
}
