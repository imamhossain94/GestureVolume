package com.newagedevs.gesturevolume.utils

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * The Button looks fill: ten of the button animation's looks — Line, Neon, Ripple, Lava, Spectrum,
 * Elastic, Heartbeat, Glass, Aurora and Jelly — worn by the Quick panel, filled to the level and
 * answering every step of it the way the animation answers a press.
 *
 * The Quick slider and the button animation stay two separate things. The animation is what a
 * press of a physical button shows, at the button; this is only the slider borrowing its looks for
 * its fill. Each is the same idea redrawn for a panel rather than a dent in the screen's edge: Line
 * lights the fill's outline to the level, Neon makes that outline a tube, Ripple's rings leave the
 * level, Lava's blobs run along its surface, Spectrum turns round the panel's rim, Elastic and Jelly
 * spring the surface, Heartbeat throbs with a trace up the fill, Glass frosts it and runs a glint
 * round it, and Aurora drifts through it.
 *
 * A "beat" is the panel's press: every twentieth of the way the level moves, and every glide the
 * keys or the nudge buttons start. The looks that answer a press answer a beat.
 *
 * Plain maths here, so the curves can be checked without a device. Look identifiers are a
 * persistence format, never renamed.
 */
object ButtonLookFill {

    // ---- looks ------------------------------------------------------------------------------------

    /** A lit line round the fill, as far as the level, and a bright bar across it. */
    const val LINE = "line"

    /** The fill dark, its outline a neon tube that flickers as it strikes and hums while it stays. */
    const val NEON = "neon"

    /** Water, its surface rippling, a ring leaving the level at every step. */
    const val RIPPLE = "ripple"

    /** Molten rock: blobs running along a glowing surface, hotter towards the top. */
    const val LAVA = "lava"

    /** The spectrum turning round the panel's rim, and the level in rainbow. */
    const val SPECTRUM = "spectrum"

    /** The level's surface stretched by each step, springing back past flat and settling. */
    const val ELASTIC = "elastic"

    /** Two quick throbs at every step, a glow, and a trace running up through the fill. */
    const val HEARTBEAT = "heartbeat"

    /** Frosted glass with a bright rim, a glint running round it at every step. */
    const val GLASS = "glass"

    /** Northern lights drifting up through the fill, curtains swaying across it. */
    const val AURORA = "aurora"

    /** A block of jelly, wobbling at every step. */
    const val JELLY = "jelly"

    /** In the order they are offered: the order the user asked for them in. */
    val LOOKS: List<String> = listOf(LINE, NEON, RIPPLE, LAVA, SPECTRUM, ELASTIC, HEARTBEAT, GLASS, AURORA, JELLY)

    fun sanitize(id: String?): String = if (id in LOOKS) id!! else LINE

    /**
     * The colours a look paints with, as `0xAARRGGBB`: its dark first, then its lights, brightest
     * last. The dark is dark enough to count as ground in [SliderFill.recolour], so the user's own
     * colours recolour the lights and leave the dark the light shines out of.
     *
     * Spectrum and Aurora carry a run of colours rather than one light: the rainbow and the lights
     * of the sky, which the user's colours replace as a run.
     */
    fun palette(id: String): LongArray = when (sanitize(id)) {
        NEON -> longArrayOf(0xFF0D0716, 0xFFFF3FD2, 0xFF45E9FF, 0xFFFFE6FA)
        RIPPLE -> longArrayOf(0xFF04203A, 0xFF1E8FE0, 0xFF6FD3FF, 0xFFE8FBFF)
        LAVA -> longArrayOf(0xFF5A0700, 0xFFD8320B, 0xFFFF7A1A, 0xFFFFC94A)
        SPECTRUM -> longArrayOf(0xFF07070C, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759, 0xFF007AFF, 0xFFAF52DE)
        ELASTIC -> longArrayOf(0xFF141238, 0xFF6C5CFF, 0xFFA99CFF, 0xFFF0EDFF)
        HEARTBEAT -> longArrayOf(0xFF2A0612, 0xFFE0284F, 0xFFFF6F8A, 0xFFFFE3E9)
        GLASS -> longArrayOf(0xFF0C1620, 0xFF9FC7E0, 0xFFE2F2FF, 0xFFFFFFFF)
        AURORA -> longArrayOf(0xFF04101F, 0xFF00E5A8, 0xFF1F7BFF, 0xFFB04BFF, 0xFF00C2FF)
        JELLY -> longArrayOf(0xFF3A0A2A, 0xFFFF4F9E, 0xFFFF9BCB, 0xFFFFFFFF)
        else -> longArrayOf(0xFF0A1424, 0xFF5FA8FF, 0xFFBFE1FF, 0xFFFFFFFF)
    }

    /** [palette] for [id], recoloured with the user's [custom] colours when there are any, as ints. */
    fun paletteWith(id: String, custom: IntArray?): IntArray =
        SliderFill.recolour(palette(id), custom).let { p -> IntArray(p.size) { p[it].toInt() } }

    /**
     * What most of the lit part is, for the number and the icon to be written in a colour that
     * stands out from it: the look's body where it has one — over the [track] where the body lets
     * it through — and its lights averaged where it is a run of them.
     */
    fun backdrop(id: String, p: IntArray, track: Int): Int {
        if (p.size < 4) return track
        return when (sanitize(id)) {
            LINE -> blend(track, p[1], 0.26f)
            NEON -> blend(track, p[0], 0.88f)
            GLASS -> blend(blend(blend(track, p[0], 0.4f), 0xFFFFFFFF.toInt(), 0.18f), p[1], 0.22f)
            SPECTRUM, AURORA -> average(p, 1)
            else -> p[1]
        }
    }

    /** [from] moved [f] of the way to [to], as an opaque colour. Plain maths, no framework. */
    fun blend(from: Int, to: Int, f: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * f + 0.5f).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** The average of [p]'s colours from slot [from] on, opaque. */
    private fun average(p: IntArray, from: Int): Int {
        var r = 0
        var g = 0
        var b = 0
        val n = (p.size - from).coerceAtLeast(1)
        for (i in from until p.size) {
            r += (p[i] shr 16) and 0xFF
            g += (p[i] shr 8) and 0xFF
            b += p[i] and 0xFF
        }
        return (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }

    // ---- the settings -----------------------------------------------------------------------------

    /** Everything the user tunes about the Button looks fill, clamped to what can be drawn. */
    data class Style(
        val look: String = LINE,
        /** How fast it moves: 1 is the look's own pace. */
        val speed: Float = DEFAULT_SPEED,
        /** How hard each step is answered, 0..1: from not at all to the animation's full press. */
        val bounce: Float = DEFAULT_BOUNCE,
    ) {
        fun sanitized(): Style = Style(
            look = sanitize(look),
            speed = if (speed.isNaN()) DEFAULT_SPEED else speed.coerceIn(MIN_SPEED, MAX_SPEED),
            bounce = if (bounce.isNaN()) DEFAULT_BOUNCE else bounce.coerceIn(0f, 1f),
        )
    }

    const val DEFAULT_SPEED = 1f
    const val MIN_SPEED = 0.25f
    const val MAX_SPEED = 3f
    const val DEFAULT_BOUNCE = 0.7f

    /**
     * Where the clock wraps, in seconds: an hour of a panel being open, which a panel never is. The
     * loops that repeat exactly — the glint, the sheen, the resting pulse — divide it.
     */
    const val TIME_WRAP_S = 3600f

    // ---- beats --------------------------------------------------------------------------------------

    /** A beat every twentieth of the way: a step of the volume keys on most phones crosses one. */
    const val BEAT_STEPS = 20

    /** Which twentieth [value] is in, so a beat is a change of it. */
    fun beatBucket(value: Float): Int = floor(value.coerceIn(0f, 1f) * BEAT_STEPS).toInt().coerceAtMost(BEAT_STEPS - 1)

    /**
     * The least time between two beats, in seconds of the fill's clock. A finger sweeping the whole
     * track crosses twenty in a moment; answering every one would be a shudder, not a bounce.
     */
    const val MIN_BEAT_GAP_S = 0.09f

    /** How long a ring takes to leave the level and fade, in seconds. */
    const val RING_LIFE_S = 0.9f

    /** How many rings can be out at once. */
    const val MAX_RINGS = 6

    /** How far a ring is on its way, 0..1, [ageS] after it left: fast at first, easing out. */
    fun ringTravel(ageS: Float): Float {
        val a = (ageS / RING_LIFE_S).coerceIn(0f, 1f)
        return 1f - (1f - a) * (1f - a)
    }

    /** How much of a ring shows, [ageS] after it left: all of it, fading to nothing. */
    fun ringFade(ageS: Float): Float {
        val a = (ageS / RING_LIFE_S).coerceIn(0f, 1f)
        return (1f - a) * (1f - a)
    }

    /**
     * The Elastic look's stretch, [sinceS] after a beat: all of it at the beat, back past flat by
     * about a third, and settled in a little over half a second. Signed, -1..1.
     */
    fun spring(sinceS: Float): Float {
        if (sinceS < 0f) return 0f
        if (sinceS > SPRING_S) return 0f
        return (cos(2.0 * PI * SPRING_HZ * sinceS).toFloat()) * exp(-SPRING_DECAY * sinceS)
    }

    private const val SPRING_HZ = 2.4f
    private const val SPRING_DECAY = 5.5f
    private const val SPRING_S = 1.2f

    /**
     * How far across the track the stretch is pulled at [x], 0..1 across: all of it in the middle,
     * none at the sides, which stay where the panel's walls hold them.
     */
    fun bow(x: Float): Float {
        val u = x.coerceIn(0f, 1f) * 2f - 1f
        return 1f - u * u
    }

    /**
     * The Heartbeat look's throb at [time], [sinceS] after the last beat: the animation's two quick
     * throbs at each step, and between steps a resting pulse at a third of the strength, once every
     * [RESTING_BEAT_S], so a panel held still is still alive.
     */
    fun throb(time: Float, sinceS: Float, bounce: Float): Float {
        val step = ButtonBump.heartbeat(sinceS) * bounce
        if (sinceS in 0f..RESTING_AFTER_S) return step
        val rest = ButtonBump.heartbeat(((time % RESTING_BEAT_S) + RESTING_BEAT_S) % RESTING_BEAT_S) * RESTING_STRENGTH
        return maxOf(step, rest)
    }

    /** The resting pulse: about seventy a minute. Divides [TIME_WRAP_S]. */
    const val RESTING_BEAT_S = 0.9f
    private const val RESTING_STRENGTH = 0.35f
    private const val RESTING_AFTER_S = 1.2f

    /**
     * How high the Lava look's surface stands at [x], 0..1 across, at [time] seconds, 0..1: two
     * blobs that drift apart and run back together, as the animation's do, each swelling and
     * shrinking on its own beat.
     */
    fun lavaSurface(x: Float, time: Float): Float {
        val drift = 0.2f * sin(time * 1.3f)
        val c1 = 0.5f - 0.17f - drift
        val c2 = 0.5f + 0.17f + drift
        val s1 = 0.86f + 0.14f * sin(time * 2.1f)
        val s2 = 0.86f + 0.14f * cos(time * 1.7f)
        return maxOf(blob((x - c1) / BLOB_WIDTH) * s1, blob((x - c2) / BLOB_WIDTH) * s2)
    }

    private const val BLOB_WIDTH = 0.34f

    /** A soft hump, 1 in the middle and 0 a width away. */
    private fun blob(u: Float): Float {
        val a = kotlin.math.abs(u)
        if (a >= 1f) return 0f
        val e = 1f - a * a
        return e * e
    }

    /** Where blob [k] of the lava lamp inside is, 0..1 up the lit part, at [time]: rising, and round again. */
    fun lavaRise(k: Int, time: Float): Float {
        val speed = 0.09f + 0.035f * k
        val offset = SliderFill.pseudoRandom(k * 7919 + 13)
        return ((time * speed + offset) % 1f + 1f) % 1f
    }

    /**
     * How far up the Ripple look's surface stands at [x], 0..1 across, in units of its height: two
     * waves crossing, as the panel's tides are drawn, so it reads as water rather than a ribbon.
     */
    fun rippleSurface(x: Float, time: Float): Float {
        val a = sin(x * 2f * PI.toFloat() + time * 2.2f)
        val b = sin(x * 3.4f * PI.toFloat() - time * 1.6f) * 0.45f
        return (a + b) / 1.45f
    }

    /** One drift of the aurora's light up the fill, in seconds. */
    const val AURORA_DRIFT_S = 6f

    /** One trip of the Line look's glint from the bottom to the level, in seconds. */
    const val GLINT_S = 2.4f

    /** One pass of the Glass look's reflection up the fill, in seconds. */
    const val SHEEN_S = 3.6f

    /**
     * How a look's glow passes are laid down: (width, alpha) from the widest and faintest to the
     * core, drawn under one another so every renderer shows a glow without a blur.
     */
    val GLOW: Array<Pair<Float, Float>> = arrayOf(4.2f to 0.08f, 2.6f to 0.16f, 1.6f to 0.36f, 1f to 1f)
}
