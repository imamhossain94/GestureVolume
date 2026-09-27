package com.newagedevs.gesturevolume.utils

/**
 * The Surge fill: the Quick panel's track lit to the level by a glowing front — the part of a
 * progress bar that is doing the work. Behind the front the light runs back into the dark; on it,
 * the front burns brightest; past it, the track.
 *
 * After the "syncing" progress bars that have their leading edge as the whole of their design: a
 * smooth curve of light, a crackling electric edge, a honeycomb lit cell by cell, streaks of
 * different lengths, staggered blocks, and a flame. Turned on its end for this track, so the front
 * runs across the panel and climbs it.
 *
 * Drawn by a program on the graphics chip, as the Shaders fill is, and for the same reason: a soft
 * glow and a turbulent edge are one draw there and thousands of shapes anywhere else. So Android 13
 * and later, like [ShaderFill]; before it the fill is not offered, and a panel that somehow has it
 * is drawn plain.
 *
 * What the user tunes is [Style]: the look, and six things every look answers to. Plain values
 * here, so they can be checked without a device. Look identifiers are a persistence format, never
 * renamed.
 */
object SurgeFill {

    // ---- looks ------------------------------------------------------------------------------------

    /** A smooth curve of light, swaying slowly, the light behind it fading into the dark. */
    const val CURVE = "curve"

    /** A crackling, turbulent edge, threads of light echoing it behind. */
    const val ELECTRIC = "electric"

    /** Hexagonal cells lit one by one to the level, each its own shade, shimmering. */
    const val HONEYCOMB = "honeycomb"

    /** Fine streaks of light running up the track, each ending at its own length. */
    const val STREAKS = "streaks"

    /** A few broad blocks, their ends staggered and stepping, a bright cap on each. */
    const val BLOCKS = "blocks"

    /** A soft front of flame, its tongues licking up and falling back. */
    const val FLAME = "flame"

    /** In the order they are offered. */
    val LOOKS: List<String> = listOf(CURVE, ELECTRIC, HONEYCOMB, STREAKS, BLOCKS, FLAME)

    fun sanitize(id: String?): String = if (id in LOOKS) id!! else CURVE

    /** The first Android release that runs an app's own shaders: 13, Tiramisu. See [ShaderFill.MIN_SDK]. */
    const val MIN_SDK = ShaderFill.MIN_SDK

    /**
     * The four colours a look paints with, as `0xAARRGGBB`: the dark the light fades into, the deep
     * light behind the front, the bright light at it, and the hot line on it.
     *
     * The first is dark enough to count as ground in [SliderFill.recolour], so the user's colours
     * recolour the light and the dark it shines out of stays dark.
     */
    fun palette(id: String): LongArray = when (sanitize(id)) {
        ELECTRIC -> longArrayOf(0xFF05081A, 0xFF1C46C8, 0xFF4FA6FF, 0xFFD6F4FF)
        HONEYCOMB -> longArrayOf(0xFF100A22, 0xFF6A4DE6, 0xFFA98BFF, 0xFFEEDDFF)
        STREAKS -> longArrayOf(0xFF170812, 0xFF7A2458, 0xFFE35BAE, 0xFFFFE3F5)
        BLOCKS -> longArrayOf(0xFF0B0E17, 0xFF2C3654, 0xFF9CB5EC, 0xFFF4FAFF)
        FLAME -> longArrayOf(0xFF150604, 0xFF83260A, 0xFFFF7A2C, 0xFFFFE9A8)
        else -> longArrayOf(0xFF050A20, 0xFF173AB4, 0xFF4E8CFF, 0xFFD9F1FF)
    }

    /** [palette] for [id], recoloured with the user's [custom] colours when there are any, as ints. */
    fun paletteWith(id: String, custom: IntArray?): IntArray =
        SliderFill.recolour(palette(id), custom).let { p -> IntArray(p.size) { p[it].toInt() } }

    // ---- the settings -----------------------------------------------------------------------------

    /** Everything the user tunes about the Surge fill, clamped to what can be drawn. */
    data class Style(
        val look: String = CURVE,
        /** How fast it moves: 1 is its own pace, 0 holds it still. */
        val speed: Float = DEFAULT_SPEED,
        /**
         * How big its features are drawn: above 1, more and smaller — more streaks, more blocks,
         * smaller cells, a busier edge — and below 1, fewer and bigger.
         */
        val size: Float = DEFAULT_SIZE,
        /** How uneven the front is, 0..1: from a straight line to deep curves, tongues and steps. */
        val edge: Float = DEFAULT_EDGE,
        /** How bright and wide the light on the front is, 0..1. */
        val glow: Float = DEFAULT_GLOW,
        /** How far behind the front the light reaches before it has faded into the dark, 0..1. */
        val trail: Float = DEFAULT_TRAIL,
        /** How much of it shows above the level, 0..[MAX_REST]. */
        val rest: Float = DEFAULT_REST,
    ) {
        fun sanitized(): Style = Style(
            look = sanitize(look),
            speed = speed.clampOr(DEFAULT_SPEED, 0f, MAX_SPEED),
            size = size.clampOr(DEFAULT_SIZE, MIN_SIZE, MAX_SIZE),
            edge = edge.clampOr(DEFAULT_EDGE, 0f, 1f),
            glow = glow.clampOr(DEFAULT_GLOW, 0f, 1f),
            trail = trail.clampOr(DEFAULT_TRAIL, 0f, 1f),
            rest = rest.clampOr(DEFAULT_REST, 0f, MAX_REST),
        )

        /** Whether anything moves. A still one is drawn once rather than on every frame. */
        val isAnimated: Boolean get() = speed > 0f
    }

    const val DEFAULT_SPEED = 1f
    const val MAX_SPEED = 3f
    const val DEFAULT_SIZE = 1f
    const val MIN_SIZE = 0.5f
    const val MAX_SIZE = 2f
    const val DEFAULT_EDGE = 0.5f
    const val DEFAULT_GLOW = 0.6f
    const val DEFAULT_TRAIL = 0.5f
    const val DEFAULT_REST = 0.1f
    const val MAX_REST = 0.4f

    /** Where the clock wraps, in seconds of its own time: see [ShaderFill.TIME_WRAP_S]. */
    const val TIME_WRAP_S = ShaderFill.TIME_WRAP_S

    private fun Float.clampOr(fallback: Float, low: Float, high: Float): Float =
        if (isNaN()) fallback else coerceIn(low, high)
}
