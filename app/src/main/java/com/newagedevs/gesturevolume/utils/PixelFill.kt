package com.newagedevs.gesturevolume.utils

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * The Pixels fill: the Quick panel's track drawn as a grid of square lights, lit up to the level,
 * with a pattern running through the lit ones — a loader's grid of pixels, doing the job of a
 * volume bar.
 *
 * After the pixel animations of MetalForge's Pixel Studio, each written anew here from what it is
 * meant to look like, for a grid that stands on its end: a few columns across the track and as many
 * rows as its length holds, counted from the bottom the way the fill rises.
 *
 * What the user tunes is [Style]: the pattern, how fast it runs, how many pixels across, the gap
 * between them, how round they are, how much they glow, and how much of an unlit pixel still shows.
 *
 * Plain maths, like [SliderFill], so every pattern can be checked without a device — and every one
 * turns a whole number of times a cycle, so the last frame of a loop is the first of the next.
 *
 * Pattern identifiers are a persistence format, never renamed.
 */
object PixelFill {

    // ---- patterns ---------------------------------------------------------------------------------

    /** Every pixel lit, nothing moving: the plainest grid. */
    const val STEADY = "steady"

    /** The whole grid swelling and fading together. */
    const val BREATHE = "breathe"

    /** A bright band climbing the track and starting again from the bottom. */
    const val SWEEP = "sweep"

    /** A light swinging up and down, slow at the ends. */
    const val PENDULUM = "pendulum"

    /** A head with a tail winding through every pixel in turn. */
    const val SNAKE = "snake"

    /** Drops falling down each column, each column at its own pace and in its own hue. */
    const val RAIN = "rain"

    /** Pixels flaring on their own beats. */
    const val TWINKLE = "twinkle"

    /** A checkerboard swapping over and back. */
    const val CHECKERS = "checkers"

    /** An equaliser: each column its own bouncing bar, green through amber to red. */
    const val METER = "meter"

    /** A fixed hue for every row, and a bright wave reading up through them. */
    const val SPECTRUM = "spectrum"

    /** Hue flowing up the track at an even brightness. */
    const val RAINBOW = "rainbow"

    /** Green, teal and violet bands drifting, soft at the edges. */
    const val AURORA = "aurora"

    /** Three sines beating, read as hue. */
    const val PLASMA = "plasma"

    /** A warm ramp, hottest along the bottom, flickering. */
    const val EMBERS = "embers"

    /** An iron ramp over a wave travelling up. */
    const val THERMAL = "thermal"

    /** Every pixel its own hue, popping on its own beat. */
    const val CONFETTI = "confetti"

    /** Red, green and blue heads sweeping a fraction apart. */
    const val CHROMATIC = "chromatic"

    /** Three inks in diagonal stripes, stepping along. */
    const val CANDY = "candy"

    /** Rings opening out of the middle, their hue by how far they have come. */
    const val RIPPLE = "ripple"

    /** In the order they are offered: the single-colour ones first, then the colourful ones. */
    val ALL: List<String> = listOf(
        SPECTRUM, STEADY, BREATHE, SWEEP, PENDULUM, SNAKE, TWINKLE, CHECKERS,
        METER, RAIN, RAINBOW, AURORA, PLASMA, EMBERS, THERMAL, CONFETTI, CHROMATIC, CANDY, RIPPLE,
    )

    fun sanitize(id: String?): String = if (id in ALL) id!! else SPECTRUM

    /** How a pattern colours its pixels. */
    enum class Ink {
        /** In the fill colour the user picked for the panel. */
        FILL,

        /** Round the colour wheel, or round the user's own colours when they have set some. */
        HUE,

        /** Along the pattern's own ramp of colours, or along the user's. */
        RAMP,

        /** Red, green and blue added together. */
        RGB,
    }

    fun ink(id: String): Ink = when (sanitize(id)) {
        STEADY, BREATHE, SWEEP, PENDULUM, SNAKE, TWINKLE, CHECKERS -> Ink.FILL
        RAIN, SPECTRUM, RAINBOW, PLASMA, CONFETTI, RIPPLE -> Ink.HUE
        METER, AURORA, EMBERS, THERMAL, CANDY -> Ink.RAMP
        else -> Ink.RGB
    }

    /** Whether the pattern moves at all. A steady grid needs no clock. */
    fun isAnimated(id: String): Boolean = sanitize(id) != STEADY

    /** How long one cycle takes at speed 1. */
    fun cycleMs(id: String): Int = when (sanitize(id)) {
        BREATHE -> 3200
        SWEEP -> 1800
        PENDULUM -> 2400
        SNAKE -> 6000
        RAIN -> 2400
        TWINKLE -> 4000
        CHECKERS -> 2400
        METER -> 1600
        SPECTRUM -> 2000
        RAINBOW -> 4000
        AURORA -> 8000
        PLASMA -> 6000
        EMBERS -> 3000
        THERMAL -> 3000
        CONFETTI -> 3000
        CHROMATIC -> 2400
        CANDY -> 1800
        RIPPLE -> 2400
        else -> 1
    }

    /** The colours a [Ink.RAMP] pattern runs along, low to high, as opaque `0xFFRRGGBB`. */
    fun ramp(id: String): IntArray = when (sanitize(id)) {
        METER -> intArrayOf(0xFF3DE68A.toInt(), 0xFFFFC23D.toInt(), 0xFFFF4F61.toInt())
        AURORA -> intArrayOf(0xFF2BFF88.toInt(), 0xFF2BD8FF.toInt(), 0xFF9B6BFF.toInt())
        EMBERS -> intArrayOf(0xFF5A0E00.toInt(), 0xFFE0301E.toInt(), 0xFFFF8A1E.toInt(), 0xFFFFE27A.toInt())
        THERMAL -> intArrayOf(0xFF1A0633.toInt(), 0xFF7A1A8C.toInt(), 0xFFE0301E.toInt(), 0xFFFFB01E.toInt(), 0xFFFFF4D6.toInt())
        CANDY -> intArrayOf(0xFFFF4F8B.toInt(), 0xFFFFD23F.toInt(), 0xFF3FC4FF.toInt())
        else -> intArrayOf(0xFFFFFFFF.toInt())
    }

    /** Whether the user's own animation colours change this pattern. The single-colour ones use the fill colour. */
    fun supportsCustomColors(id: String): Boolean = ink(id) != Ink.FILL

    // ---- what each pixel does ---------------------------------------------------------------------

    private const val TAU = 2f * PI.toFloat()

    /**
     * How bright the pixel at ([column], [row]) is, 0..1, at [phase] 0..1 through the cycle.
     *
     * [row] counts from the bottom. The renderer puts a floor under this for every pixel below the
     * level, so a pattern that goes dark between its lights never hides where the level is.
     */
    fun level(id: String, column: Int, row: Int, columns: Int, rows: Int, phase: Float): Float {
        if (columns <= 0 || rows <= 0) return 0f
        val p = wrap(phase)
        val u = (row + 0.5f) / rows
        val v = (column + 0.5f) / columns
        val value = when (sanitize(id)) {
            STEADY, RAINBOW, CANDY -> 1f
            BREATHE -> 0.5f - 0.5f * cos(TAU * p)
            SWEEP -> {
                // Trailing below the head, so it reads as climbing.
                val behind = wrap(p - u)
                if (behind < 0.35f) (1f - behind / 0.35f).pow(2) else 0f
            }
            PENDULUM -> {
                val head = 0.5f - 0.5f * cos(TAU * p)
                gaussian(u - head, 0.12f)
            }
            SNAKE -> {
                // Up the grid a row at a time, turning at each end.
                val along = if (row % 2 == 0) column else columns - 1 - column
                val index = row * columns + along
                val count = rows * columns
                val behind = wrap((p * count - index) / count) * count
                val tail = max(3f, count * 0.22f)
                if (behind < tail) 1f - behind / tail else 0f
            }
            RAIN -> {
                val seed = seed(column)
                val speed = 1f + floor(seed * 2f)
                val head = wrap(p * speed + seed)
                // Falling, so the trail lies above the head.
                val behind = wrap(head - (1f - u))
                if (behind < 0.4f) (1f - behind / 0.4f).pow(1.5f) else 0f
            }
            TWINKLE -> {
                val seed = seed(column * 97 + row * 31)
                val beats = 1f + floor(seed * 3f)
                sin(PI.toFloat() * wrap(p * beats + seed)).pow(8)
            }
            CHECKERS -> {
                val swap = (0.5f + 2f * sin(TAU * p)).coerceIn(0f, 1f)
                if ((column + row) % 2 == 0) swap else 1f - swap
            }
            METER -> {
                val seed = seed(column * 7 + 3)
                val speed = 1f + floor(seed * 2f)
                val height = 0.15f + 0.85f * (0.5f + 0.35f * sin(TAU * (p * speed + seed)) +
                    0.15f * sin(TAU * (p * speed * 2f + seed * 1.7f)))
                if (u <= height) 1f else 0f
            }
            SPECTRUM -> falloff(ringDistance(u, p), 0.28f).pow(2)
            AURORA -> 0.35f + 0.65f * (0.5f + 0.5f * sin(TAU * (u * 2f + p) + v * 3f))
            PLASMA -> 0.55f + 0.45f * sin(TAU * p + PI.toFloat() * plasma(u, v, p))
            EMBERS -> (heat(column, row, u, p) * 1.2f).coerceIn(0f, 1f)
            THERMAL -> 0.3f + 0.7f * (0.5f + 0.5f * sin(TAU * (u * 1.2f - p)))
            CONFETTI -> {
                val seed = seed(column * 13 + row * 7)
                val beats = 1f + floor(seed * 2f)
                (1f - wrap(p * beats + seed)).pow(3)
            }
            CHROMATIC -> max(channel(u, p, 0f), max(channel(u, p, 0.08f), channel(u, p, 0.16f)))
            RIPPLE -> {
                val front = wrap(rippleDistance(column, row, columns, rows) - p)
                (1f - front / 0.3f).coerceAtLeast(0f).pow(2)
            }
            else -> 1f
        }
        return value.coerceIn(0f, 1f)
    }

    /**
     * Where the pixel's colour sits along its [ink], 0..1: a turn of the wheel for [Ink.HUE], a
     * place on the ramp for [Ink.RAMP]. Meaningless for the other two.
     */
    fun tone(id: String, column: Int, row: Int, columns: Int, rows: Int, phase: Float): Float {
        if (columns <= 0 || rows <= 0) return 0f
        val p = wrap(phase)
        val u = (row + 0.5f) / rows
        val v = (column + 0.5f) / columns
        val value = when (sanitize(id)) {
            RAIN -> seed(column)
            SPECTRUM -> u * 0.85f
            RAINBOW -> wrap(u * 0.8f - p)
            PLASMA -> 0.5f + 0.5f * plasma(u, v, p)
            CONFETTI -> seed(column * 29 + row * 11 + 5)
            RIPPLE -> rippleDistance(column, row, columns, rows)
            METER -> u
            AURORA -> 0.5f + 0.5f * (0.6f * sin(TAU * (u * 1.3f - p) + v * 2.2f) +
                0.4f * sin(TAU * (u * 0.7f + p) + 1.7f + v * 1.3f))
            EMBERS -> heat(column, row, u, p)
            THERMAL -> 0.5f + 0.5f * sin(TAU * (u * 1.2f - p))
            // Stepping a whole stripe at a time, a third of the cycle each, so it lands back where it began.
            CANDY -> (((row + column + floor(p * 3f).toInt()) % 3 + 3) % 3) / 2f
            else -> 0f
        }
        return value.coerceIn(0f, 1f)
    }

    /**
     * The pixel's colour, opaque. Brightness is the renderer's: this is only which colour.
     *
     * @param fillColor the panel's fill colour, which the single-colour patterns are drawn in.
     * @param custom the user's own animation colours, or null for the pattern's.
     */
    fun color(
        id: String,
        column: Int,
        row: Int,
        columns: Int,
        rows: Int,
        phase: Float,
        fillColor: Int,
        custom: IntArray?,
    ): Int {
        val own = custom?.takeIf { it.isNotEmpty() }
        return when (ink(id)) {
            Ink.FILL -> fillColor or OPAQUE
            Ink.HUE -> {
                val t = tone(id, column, row, columns, rows, phase)
                if (own != null) cyclic(own, t) else hsv(t * 360f, 0.72f, 1f)
            }
            Ink.RAMP -> along(own ?: ramp(id), tone(id, column, row, columns, rows, phase))
            Ink.RGB -> {
                val u = (row + 0.5f) / rows
                val p = wrap(phase)
                // Over a grey the heads light up from: added from nothing, every pixel away from
                // them was black, and the level vanished with them.
                val r = CHROMATIC_BASE + (1f - CHROMATIC_BASE) * channel(u, p, 0f)
                val g = CHROMATIC_BASE + (1f - CHROMATIC_BASE) * channel(u, p, 0.08f)
                val b = CHROMATIC_BASE + (1f - CHROMATIC_BASE) * channel(u, p, 0.16f)
                if (own != null && own.size >= 3) {
                    add(own[0], r, own[1], g, own[2], b)
                } else {
                    rgb(r, g, b)
                }
            }
        }
    }

    // ---- the settings -----------------------------------------------------------------------------

    /** Everything the user tunes about the Pixels fill, clamped to what can be drawn. */
    data class Style(
        val pattern: String = SPECTRUM,
        /** How fast the pattern runs: 1 is its own pace. */
        val speed: Float = DEFAULT_SPEED,
        /** How many pixels across the track. The rows follow from the track's length. */
        val columns: Int = DEFAULT_COLUMNS,
        /** The gap between pixels, as a fraction of a pixel's width. */
        val gap: Float = DEFAULT_GAP,
        /** 0 for square pixels, 1 for round. */
        val roundness: Float = DEFAULT_ROUNDNESS,
        /** How much light the lit pixels throw into the gaps around them, 0..1. */
        val glow: Float = DEFAULT_GLOW,
        /** How much of an unlit pixel, above the level, still shows, 0..1. */
        val rest: Float = DEFAULT_REST,
    ) {
        fun sanitized(): Style = Style(
            pattern = sanitize(pattern),
            speed = speed.clampOr(DEFAULT_SPEED, MIN_SPEED, MAX_SPEED),
            columns = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS),
            gap = gap.clampOr(DEFAULT_GAP, 0f, MAX_GAP),
            roundness = roundness.clampOr(DEFAULT_ROUNDNESS, 0f, 1f),
            glow = glow.clampOr(DEFAULT_GLOW, 0f, 1f),
            rest = rest.clampOr(DEFAULT_REST, 0f, MAX_REST),
        )
    }

    const val DEFAULT_SPEED = 1f
    const val MIN_SPEED = 0.25f
    const val MAX_SPEED = 3f
    const val DEFAULT_COLUMNS = 3
    const val MIN_COLUMNS = 2
    const val MAX_COLUMNS = 6
    const val DEFAULT_GAP = 0.18f
    const val MAX_GAP = 0.45f
    const val DEFAULT_ROUNDNESS = 0.35f
    const val DEFAULT_GLOW = 0.6f
    const val DEFAULT_REST = 0.1f
    const val MAX_REST = 0.4f

    /**
     * The least a pixel below the level is lit, whatever its pattern says: enough that the level
     * always reads, while the pattern still has most of the range to move in.
     */
    const val LIT_FLOOR = 0.35f

    /** How long one cycle takes for [style], at its speed. */
    fun cycleMs(style: Style): Long =
        (cycleMs(style.pattern) / style.speed.clampOr(DEFAULT_SPEED, MIN_SPEED, MAX_SPEED)).toLong().coerceAtLeast(1L)

    // ---- the maths --------------------------------------------------------------------------------

    private const val OPAQUE = 0xFF shl 24

    /** How much of each channel [CHROMATIC] shows away from its heads. See [color]. */
    private const val CHROMATIC_BASE = 0.3f

    private fun Float.clampOr(fallback: Float, low: Float, high: Float): Float =
        if (isNaN()) fallback else coerceIn(low, high)

    /** [x] folded into 0..1. */
    private fun wrap(x: Float): Float = ((x % 1f) + 1f) % 1f

    /** How far apart [a] and [b] are on a loop of length one, 0..0.5. */
    private fun ringDistance(a: Float, b: Float): Float {
        val d = abs(wrap(a - b))
        return minOf(d, 1f - d)
    }

    private fun falloff(distance: Float, width: Float): Float = (1f - distance / width).coerceAtLeast(0f)

    private fun gaussian(x: Float, width: Float): Float = exp(-(x / width) * (x / width))

    /** A stable number in 0..1 for [n]. See [SliderFill.pseudoRandom]. */
    private fun seed(n: Int): Float = SliderFill.pseudoRandom(n)

    private fun plasma(u: Float, v: Float, p: Float): Float =
        (sin(TAU * (v + p)) + sin(TAU * (u * 1.5f - p)) + sin(TAU * ((u + v) * 0.8f + 2f * p))) / 3f

    /** How hot an ember is: hottest low down, flickering on its own beat. */
    private fun heat(column: Int, row: Int, u: Float, p: Float): Float {
        val seed = seed(column * 17 + row * 5)
        val beats = 1f + floor(seed * 2f)
        val flicker = 0.25f * (0.5f + 0.5f * sin(TAU * (p * beats + seed)))
        return ((1f - u).pow(1.3f) * 0.85f + flicker).coerceIn(0f, 1f)
    }

    /** One of the three heads of [CHROMATIC], [offset] behind the first. */
    private fun channel(u: Float, p: Float, offset: Float): Float =
        falloff(ringDistance(u, wrap(p - offset)), 0.22f).pow(2)

    /** How far out from the middle of the grid a pixel is, 0 at the middle and 1 at the farthest corner. */
    private fun rippleDistance(column: Int, row: Int, columns: Int, rows: Int): Float {
        val cx = (columns - 1) / 2f
        val cy = (rows - 1) / 2f
        val far = hypot(cx, cy).coerceAtLeast(0.5f)
        return (hypot(column - cx, row - cy) / far).coerceIn(0f, 1f)
    }

    /** [colors] as a ramp, read [t] of the way along it. */
    private fun along(colors: IntArray, t: Float): Int {
        if (colors.size == 1) return colors[0] or OPAQUE
        val g = t.coerceIn(0f, 1f) * (colors.size - 1)
        val k = g.toInt().coerceAtMost(colors.size - 2)
        return blend(colors[k], colors[k + 1], g - k)
    }

    /** [colors] round a loop, read [t] of the way round, so the last blends back into the first. */
    private fun cyclic(colors: IntArray, t: Float): Int {
        if (colors.size == 1) return colors[0] or OPAQUE
        val g = wrap(t) * colors.size
        val k = g.toInt() % colors.size
        return blend(colors[k], colors[(k + 1) % colors.size], g - floor(g))
    }

    private fun blend(from: Int, to: Int, f: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * f + 0.5f).toInt().coerceIn(0, 255)
        }
        return OPAQUE or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun rgb(r: Float, g: Float, b: Float): Int =
        OPAQUE or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)

    /** Three colours added in the amounts given, clamped: the user's own inks for [CHROMATIC]. */
    private fun add(a: Int, fa: Float, b: Int, fb: Float, c: Int, fc: Float): Int {
        fun sum(shift: Int): Int = (
            ((a shr shift) and 0xFF) * fa + ((b shr shift) and 0xFF) * fb + ((c shr shift) and 0xFF) * fc
            ).toInt().coerceIn(0, 255)
        return OPAQUE or (sum(16) shl 16) or (sum(8) shl 8) or sum(0)
    }

    private fun byte(f: Float): Int = (f.coerceIn(0f, 1f) * 255f + 0.5f).toInt()

    /** A colour from hue in degrees, saturation and value in 0..1. Plain maths, no framework. */
    fun hsv(hue: Float, saturation: Float, value: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f / 60f
        val c = value * saturation
        val x = c * (1f - abs(h % 2f - 1f))
        val m = value - c
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return rgb(r + m, g + m, b + m)
    }
}
