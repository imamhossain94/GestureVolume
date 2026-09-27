package com.newagedevs.gesturevolume.utils

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos

/**
 * How every Quick panel fill answers the level it is at, the way the Effort fill always has: calm
 * and faint low down, livelier and brighter as it rises, a flash as it passes each fifth, a breath
 * of light at the level when it is low, and a flourish when it reaches the top.
 *
 * A layer over the fill rather than something each of forty pictures does its own way. What a
 * fill looks like is its own; how it tells the finger where it is on the track is one language
 * for all of them, so it can be learnt once — and switched off once.
 *
 * The Effort fill keeps its own, since this is its idea: its stops, its sheen and its spectrum are
 * the picker it is dressed as.
 *
 * Plain maths, like [EffortFill], so the curves can be checked without a device.
 */
object LevelFeedback {

    /** The track in fifths, as the Effort picker's stops, for the flash as each is passed. */
    const val LEVELS = 5

    /** The fifth [value] 0..1 is in: on a boundary, the one it has filled rather than the next. */
    fun levelAt(value: Float): Int =
        (ceil(value.coerceIn(0f, 1f) * LEVELS - 1e-4f).toInt() - 1).coerceIn(0, LEVELS - 1)

    /** Below this the level is low, and breathes. Zero is off, and breathes at the bottom instead. */
    const val LOW = 0.15f

    /** At or above this the level is full: rounding a finger's way to the top counts. */
    const val FULL = 0.995f

    fun isLow(value: Float): Boolean = value < LOW
    fun isFull(value: Float): Boolean = value >= FULL

    /**
     * How fast the fill's own animation runs at [value], as a multiple of its pace: slower than its
     * own at the bottom, quicker at the top, so a fill is visibly working harder the more it gives.
     */
    fun pace(value: Float, follow: Boolean): Float =
        if (follow) MIN_PACE + (MAX_PACE - MIN_PACE) * value.coerceIn(0f, 1f) else 1f

    const val MIN_PACE = 0.55f
    const val MAX_PACE = 1.6f

    /** One pass of the sheen up the lit part at each fifth, in seconds: quicker as it rises. */
    fun sheenSeconds(level: Int): Float = SHEEN_SECONDS[level.coerceIn(0, LEVELS - 1)]

    private val SHEEN_SECONDS = floatArrayOf(3.4f, 2.7f, 2.1f, 1.6f, 1.2f)

    /** How bright the sheen is at each fifth, out of one. Fainter than the Effort fill's: it is over a picture. */
    fun sheenStrength(level: Int): Float = SHEEN_STRENGTH[level.coerceIn(0, LEVELS - 1)]

    private val SHEEN_STRENGTH = floatArrayOf(0.08f, 0.12f, 0.16f, 0.2f, 0.26f)

    /** How long the flash as a fifth is passed lasts, in seconds. */
    const val STEP_FLASH_S = 0.45f

    /** How long the flourish at the top lasts, in seconds. */
    const val FULL_BURST_S = 1.1f

    /** One breath of the low glow, and of the rim at the top, in seconds. */
    const val BREATH_S = 1.6f

    /** One turn of the rim's colours at the top, in seconds. */
    const val RIM_TURN_S = 3.2f

    /** How much of a flash is left [elapsedS] after it: all of it at once, easing out to none. */
    fun fade(elapsedS: Float, lengthS: Float): Float {
        if (elapsedS < 0f || elapsedS >= lengthS) return 0f
        val left = 1f - elapsedS / lengthS
        return left * left
    }

    /** A breath at [timeS], 0..1 and back, once every [BREATH_S]. */
    fun breath(timeS: Float): Float = 0.5f - 0.5f * cos((timeS / BREATH_S) * 2f * PI.toFloat())

    // ---- the settings -----------------------------------------------------------------------------

    /** Everything the user tunes about how a fill answers the level. */
    data class Style(
        /**
         * How fast the fill moves, for the fills without a speed of their own: 1 is their own pace.
         * The Pixels grid, the shaders and the glimmer keep their own sliders.
         */
        val speed: Float = DEFAULT_SPEED,
        /** Livelier and brighter as the level rises, with a flash at each fifth. */
        val follow: Boolean = true,
        /** A breath of light at the level when it is low, and at the bottom when it is off. */
        val low: Boolean = true,
        /** A burst of light at the top, and a rim of colour while it stays there. */
        val full: Boolean = true,
    ) {
        fun sanitized(): Style = Style(
            speed = if (speed.isNaN()) DEFAULT_SPEED else speed.coerceIn(MIN_SPEED, MAX_SPEED),
            follow = follow,
            low = low,
            full = full,
        )

        /** Whether there is anything to draw over a fill at all. */
        val drawsAnything: Boolean get() = follow || low || full
    }

    const val DEFAULT_SPEED = 1f
    const val MIN_SPEED = 0.25f
    const val MAX_SPEED = 3f
}
