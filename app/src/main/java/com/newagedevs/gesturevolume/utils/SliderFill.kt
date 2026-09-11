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

    val ALL = listOf(
        SOLID, BATTERY, TIDE_UP, TIDE_DOWN, PIXEL_UP,
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
}
