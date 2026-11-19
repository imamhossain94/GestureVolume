package com.newagedevs.gesturevolume.utils

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The button animation: the edge of the screen beside the volume buttons bulging inward as one is
 * pressed, with the level inside the bulge — the smooth black bump of a phone whose glass seems to
 * give under the button — and a dozen other ways of answering the press.
 *
 * Plain maths, like [LevelFeedback], so the shapes, the timing and the placement can be checked on
 * the host JVM: [com.newagedevs.gesturevolume.ui.view.ButtonBumpView] draws what this decides and
 * [com.newagedevs.gesturevolume.service.ButtonBumpOverlay] puts it on the glass.
 *
 * **Everything is measured as if the bump were on the left edge of an upright phone**: `x` inward
 * from the glass, `y` along the edge towards the bottom of the phone. The physical buttons do not
 * move when the screen turns, so neither does the bump; [placement] turns that one picture onto
 * whichever edge of the screen the buttons are on now.
 */
object ButtonBump {

    // ---- the styles: identifiers are a persistence format, never renamed ------------------------

    /** The smooth black bump, rising with a little give and settling. */
    const val STYLE_LIQUID = "liquid"

    /** A capsule that slides out of the edge beside the buttons, clear of it. */
    const val STYLE_CAPSULE = "capsule"

    /** A shallow rise traced by a lit line, the level running along the line itself. */
    const val STYLE_LINE = "line"

    /** The bump on a softer spring: it overshoots, wobbles, and jiggles at every press. */
    const val STYLE_JELLY = "jelly"

    /** An outline lit like a neon tube, flickering as it strikes. */
    const val STYLE_NEON = "neon"

    /** The bump filled with slow northern lights, the colours drifting as it stays. */
    const val STYLE_AURORA = "aurora"

    /** Rings going out from the button into the screen at every press. */
    const val STYLE_RIPPLE = "ripple"

    /** A burst of sparks thrown in from the button, and stars twinkling on the rim. */
    const val STYLE_SPARKLE = "sparkle"

    /** Molten: two warm blobs that drift and merge inside the bump. */
    const val STYLE_LAVA = "lava"

    /** Frosted glass with a bright rim, a highlight sweeping along it at each press. */
    const val STYLE_GLASS = "glass"

    /** A black bump with the spectrum turning round its rim, the level in rainbow. */
    const val STYLE_SPECTRUM = "spectrum"

    /** A beating heart: two quick throbs at every press, and a trace across it. */
    const val STYLE_HEARTBEAT = "heartbeat"

    /** Stretched towards the button that was pressed, and springing back. */
    const val STYLE_ELASTIC = "elastic"

    /**
     * Water: a glossy drop rises out of the glass at the button, and every press drops a ripple into
     * the screen that runs out, crosses the last one and bounces back off the edge. See [WaterSurface].
     */
    const val STYLE_WATER = "water"

    /**
     * A cherry branch in full bloom reaching in from the edge beside the buttons: each press shakes
     * it, and petals shake loose to flutter down across the screen.
     */
    const val STYLE_BLOSSOM = "blossom"


    /** A rocket from the bump at each press, bursting into a shell: ring, willow, peony, heart. */
    const val STYLE_FIREWORKS = "fireworks"


    /** The bump is the sun, the planets orbiting it; a press flares it and spins them faster. */
    const val STYLE_SOLAR = "solar"

    /** Soap bubbles blown out at each press, rising, shimmering, and popping. */
    const val STYLE_BUBBLES = "bubbles"

    /**
     * Balloons: a press blows one up at the edge and lets it float away, quick presses a bunch of
     * them; holding the key fills one bigger and bigger until it bursts.
     */
    const val STYLE_BALLOON = "balloon"



    /** A volcano in place of the bump, erupting lava bombs and ash at each press. */
    const val STYLE_VOLCANO = "volcano"

    /** A gust: streaks of wind whooshing out and curling, leaves tumbling in them. */
    const val STYLE_WIND = "wind"

    /** A blade's slash, the other way each time, and throwing stars spinning out. */
    const val STYLE_NINJA = "ninja"

    /** Bees buzzing out, looping about on dotted trails, and heading home. */
    const val STYLE_BEES = "bees"

    /** A rocket nosing out and climbing away on its flame and a trail of smoke. */
    const val STYLE_ROCKET = "rocket"

    /** A spray of colour at each press: a jet of fine paint droplets and a drifting, coloured mist. */
    const val STYLE_SPRAY = "spray"



    /** The glass cracking from the button: branching cracks, rings and shards. */
    const val STYLE_CRACK = "crack"

    /** A cat peeking round the edge, paws on the frame, blinking, ears twitching. */
    const val STYLE_CAT = "cat"

    /** Water along the edge, and a clownfish leaping out of it and diving back. */
    const val STYLE_FISH = "fish"


    /** A monkey peeking round the edge, grinning, shrieking at each press. */
    const val STYLE_MONKEY = "monkey"

    /** A frog on a lily pad at the edge, hopping out at each press and croaking as it lands. */
    const val STYLE_FROG = "frog"

    /**
     * Swing women: cute little heroines of the app's own, in suits of their own colours, who shoot a
     * line of silk to the top of the screen and swing across on it — another one at every press.
     * Stored as "swing"; the "spider" it was before is read as this.
     */
    const val STYLE_SWING = "swing"


    /** A flying saucer swooping out, shining its beam down, and zipping away. */
    const val STYLE_UFO = "ufo"



    /** In the order they are offered. */
    val STYLES: List<String> = listOf(
        STYLE_WATER, STYLE_LIQUID, STYLE_BLOSSOM, STYLE_FIREWORKS, STYLE_VOLCANO,
        STYLE_ROCKET, STYLE_FISH, STYLE_FROG, STYLE_SWING, STYLE_CAT, STYLE_MONKEY,
        STYLE_BUBBLES, STYLE_BALLOON, STYLE_SPRAY, STYLE_WIND, STYLE_NINJA, STYLE_CRACK, STYLE_UFO, STYLE_BEES, STYLE_SOLAR,
        STYLE_CAPSULE, STYLE_LINE, STYLE_JELLY, STYLE_NEON, STYLE_AURORA, STYLE_RIPPLE, STYLE_SPARKLE,
        STYLE_LAVA, STYLE_GLASS, STYLE_SPECTRUM, STYLE_HEARTBEAT, STYLE_ELASTIC,
    )

    const val DEFAULT_STYLE = STYLE_WATER

    /** Styles that were renamed: the id they had, and the one they have now. */
    private val RENAMED = mapOf("spider" to STYLE_SWING)

    /** Every style is everyone's: Pro takes the ads away and nothing else. */
    fun sanitizeStyle(id: String?): String = when (id) {
        in STYLES -> id!!
        in RENAMED -> RENAMED.getValue(id!!)
        else -> DEFAULT_STYLE
    }

    // ---- how each style moves -----------------------------------------------------------------------

    /**
     * What a style is made of, apart from how it is painted.
     *
     * @param stiffness the spring the bump rises and falls on, per second squared.
     * @param damping its damping ratio: 1 settles without overshoot, below it the bump overshoots.
     * @param shoulder how much of each end of the bump is the curve down to the glass, 0..1 of its
     *   half-length. Small is a flat-topped slab, large a soft hill.
     * @param reach how far into the screen the style draws, as a multiple of the bump's depth:
     *   ripples and sparks travel well past the bump itself.
     * @param detached whether the shape stands clear of the glass rather than growing out of it.
     * @param spread for the styles that throw things out across the screen, how far along the edge
     *   they go each way from the button, as a multiple of the bump's depth; 0 for the rest.
     */
    data class Spec(
        val stiffness: Float,
        val damping: Float,
        val shoulder: Float,
        val reach: Float,
        val detached: Boolean = false,
        val spread: Float = 0f,
    )

    fun spec(style: String): Spec = when (sanitizeStyle(style)) {
        STYLE_CAPSULE -> Spec(stiffness = 420f, damping = 0.82f, shoulder = 0.5f, reach = 2.2f, detached = true)
        STYLE_LINE -> Spec(stiffness = 380f, damping = 0.9f, shoulder = 0.62f, reach = 1.6f)
        STYLE_JELLY -> Spec(stiffness = 300f, damping = 0.24f, shoulder = 0.42f, reach = 1.9f)
        STYLE_NEON -> Spec(stiffness = 420f, damping = 0.7f, shoulder = 0.36f, reach = 2.4f)
        STYLE_AURORA -> Spec(stiffness = 340f, damping = 0.62f, shoulder = 0.4f, reach = 1.7f)
        STYLE_RIPPLE -> Spec(stiffness = 380f, damping = 0.6f, shoulder = 0.36f, reach = 6f)
        STYLE_SPARKLE -> Spec(stiffness = 380f, damping = 0.58f, shoulder = 0.36f, reach = 5f)
        STYLE_LAVA -> Spec(stiffness = 260f, damping = 0.5f, shoulder = 0.46f, reach = 1.8f)
        STYLE_GLASS -> Spec(stiffness = 400f, damping = 0.7f, shoulder = 0.36f, reach = 1.7f)
        STYLE_SPECTRUM -> Spec(stiffness = 400f, damping = 0.6f, shoulder = 0.36f, reach = 2.2f)
        STYLE_HEARTBEAT -> Spec(stiffness = 420f, damping = 0.55f, shoulder = 0.38f, reach = 2.6f)
        STYLE_ELASTIC -> Spec(stiffness = 320f, damping = 0.4f, shoulder = 0.4f, reach = 1.9f)
        // A rounder drop on a softer spring, whose wobble sends out ripples of its own, and the room
        // for them to run well into the screen.
        STYLE_WATER -> Spec(stiffness = 300f, damping = 0.42f, shoulder = 0.9f, reach = WATER_REACH, spread = WATER_REACH * 0.9f)
        STYLE_BLOSSOM -> Spec(stiffness = 340f, damping = 0.6f, shoulder = 0.5f, reach = 8f, spread = 6.5f)
        STYLE_FIREWORKS -> Spec(stiffness = 380f, damping = 0.65f, shoulder = 0.4f, reach = 10f, spread = 7.5f)
        STYLE_SOLAR -> Spec(stiffness = 300f, damping = 0.72f, shoulder = 0.5f, reach = 9f, spread = 8.4f)
        STYLE_BUBBLES -> Spec(stiffness = 320f, damping = 0.55f, shoulder = 0.45f, reach = 7f, spread = 6f)
        STYLE_BALLOON -> Spec(stiffness = 320f, damping = 0.55f, shoulder = 0.45f, reach = 8f, spread = 6f)
        STYLE_VOLCANO -> Spec(stiffness = 340f, damping = 0.6f, shoulder = 0.45f, reach = 8f, spread = 6f)
        STYLE_WIND -> Spec(stiffness = 340f, damping = 0.6f, shoulder = 0.45f, reach = 9f, spread = 6f)
        STYLE_NINJA -> Spec(stiffness = 340f, damping = 0.6f, shoulder = 0.45f, reach = 9f, spread = 6f)
        STYLE_BEES -> Spec(stiffness = 340f, damping = 0.6f, shoulder = 0.45f, reach = 9f, spread = 7f)
        STYLE_ROCKET -> Spec(stiffness = 340f, damping = 0.6f, shoulder = 0.45f, reach = 10f, spread = 7.5f)
        STYLE_SPRAY -> Spec(stiffness = 340f, damping = 0.6f, shoulder = 0.45f, reach = 8f, spread = 6.5f)
        STYLE_CRACK -> Spec(stiffness = 340f, damping = 0.6f, shoulder = 0.45f, reach = 10f, spread = 8f)
        STYLE_CAT -> Spec(stiffness = 340f, damping = 0.6f, shoulder = 0.45f, reach = 5.5f, spread = 3.2f)
        STYLE_FISH -> Spec(stiffness = 340f, damping = 0.6f, shoulder = 0.45f, reach = 8f, spread = 6f)
        STYLE_MONKEY -> Spec(stiffness = 340f, damping = 0.6f, shoulder = 0.45f, reach = 5.5f, spread = 3.2f)
        STYLE_FROG -> Spec(stiffness = 340f, damping = 0.6f, shoulder = 0.45f, reach = 8f, spread = 6f)
        STYLE_SWING -> Spec(stiffness = 340f, damping = 0.6f, shoulder = 0.45f, reach = 8f, spread = 6f)
        STYLE_UFO -> Spec(stiffness = 340f, damping = 0.6f, shoulder = 0.45f, reach = 9f, spread = 6.5f)
        else -> Spec(stiffness = 400f, damping = 0.62f, shoulder = 0.34f, reach = 1.7f)
    }

    // ---- the shape ------------------------------------------------------------------------------------

    /**
     * How far the bump stands out at [u], from -1 at one end to 1 at the other, as 0..1 of its
     * depth: a flat top, and at each end a [shoulder] that eases down to the glass with no corner at
     * either end of it — so the bump meets the edge as if it had grown out of it.
     */
    fun profile(u: Float, shoulder: Float): Float {
        val a = abs(u)
        if (a >= 1f) return 0f
        val s = shoulder.coerceIn(0.05f, 1f)
        val edge = 1f - a
        if (edge >= s) return 1f
        return smootherstep(edge / s)
    }

    /** Ken Perlin's smootherstep: 0 to 1 with no slope and no change of slope at either end. */
    fun smootherstep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * x * (x * (x * 6f - 15f) + 10f)
    }

    // ---- the motion ---------------------------------------------------------------------------------------

    /** A value on a spring, moved towards a target a frame at a time by [stepSpring]. */
    class Spring(var value: Float = 0f, var velocity: Float = 0f) {
        fun snapTo(v: Float) {
            value = v
            velocity = 0f
        }

        /** Whether it has come to rest at [target]. */
        fun isSettled(target: Float, epsilon: Float = 0.0015f): Boolean =
            abs(value - target) < epsilon && abs(velocity) < epsilon * 8f
    }

    /**
     * Moves [spring] [dt] seconds towards [target], in small fixed steps so a dropped frame does not
     * change the shape of the motion — only how much of it is skipped.
     */
    fun stepSpring(spring: Spring, target: Float, stiffness: Float, damping: Float, dt: Float) {
        var left = dt.coerceIn(0f, MAX_FRAME_S)
        val k = stiffness.coerceAtLeast(1f)
        val c = 2f * damping.coerceAtLeast(0f) * sqrt(k)
        while (left > 0f) {
            val h = minOf(left, SUBSTEP_S)
            val accel = -k * (spring.value - target) - c * spring.velocity
            spring.velocity += accel * h
            spring.value += spring.velocity * h
            left -= h
        }
    }

    private const val SUBSTEP_S = 1f / 240f

    /** The longest step a frame is allowed to take: past it the animation slows rather than jumps. */
    const val MAX_FRAME_S = 1f / 20f

    /**
     * A press, as a pulse over time: up fast, then down — 0 before it and after [lengthS].
     * Used by the styles that squash, flicker or throb at each press.
     */
    fun pulse(sinceS: Float, lengthS: Float = PRESS_S): Float {
        if (sinceS < 0f || sinceS >= lengthS) return 0f
        val t = sinceS / lengthS
        // A quick attack in the first fifth, then a release that eases out to exactly nothing.
        if (t < 0.2f) return t / 0.2f
        val r = (t - 0.2f) / 0.8f
        val floor = exp(-4f)
        return ((exp(-4f * r) - floor) / (1f - floor)).coerceIn(0f, 1f)
    }

    /** How long a press's pulse lasts, in seconds. */
    const val PRESS_S = 0.42f

    /** Two quick throbs and a rest: the heartbeat style's pulse at [sinceS] after a press. */
    fun heartbeat(sinceS: Float): Float {
        if (sinceS < 0f || sinceS > 0.9f) return 0f
        val first = pulse(sinceS, 0.32f)
        val second = pulse(sinceS - 0.26f, 0.4f) * 0.75f
        return maxOf(first, second)
    }

    /** A wobble that dies away: the jelly style's jiggle, -1..1, [sinceS] after a press. */
    fun wobble(sinceS: Float, hz: Float = 5.2f, decay: Float = 4.2f): Float {
        if (sinceS < 0f) return 0f
        return (sin(2.0 * PI * hz * sinceS).toFloat()) * exp(-decay * sinceS)
    }

    // ---- where it goes ------------------------------------------------------------------------------

    /**
     * How long the window is along the edge for a bump [lengthPx] long: room for the bump to grow,
     * lean and overshoot, and for ripples to spread, in pixels.
     */
    fun windowSpan(style: String, lengthPx: Float, density: Float, depthPx: Float = 0f): Int {
        val id = sanitizeStyle(style)
        @Suppress("NAME_SHADOWING") val depthPx = effectDepth(id, depthPx, density)
        // The styles that throw things across the screen spread along the edge as well as into it,
        // both ways from the button.
        val spread = spec(id).spread
        if (spread > 0f) return (lengthPx + 2f * depthPx * spread + 24f * density).roundToInt()
        val factor = when (id) {
            STYLE_RIPPLE, STYLE_SPARKLE -> 2.1f
            STYLE_ELASTIC, STYLE_JELLY -> 1.8f
            else -> 1.55f
        }
        return (lengthPx * factor + 24f * density).roundToInt()
    }

    /** How far into the screen the water style's ripples run, as a multiple of the drop's depth. */
    const val WATER_REACH = 8f

    /**
     * The depth the showpieces are sized to, in dp: the styles that throw things across the screen
     * throw them as far from a 2dp bump as from a deep one.
     */
    const val EFFECT_DEPTH_DP = 18f

    private fun effectDepth(style: String, depthPx: Float, density: Float): Float =
        if (spec(style).spread > 0f) max(depthPx, EFFECT_DEPTH_DP * density) else depthPx

    /**
     * Whether a style's sound plays again on every step of a held key, as a ratchet: all but the
     * balloon, whose held key fills one balloon, hissing, rather than making a press of each step.
     */
    fun soundsWhileHeld(style: String): Boolean = sanitizeStyle(style) != STYLE_BALLOON

    /** The sound a style makes at each press, from StepSoundLibrary: its own, to go with how it looks. */
    fun sound(style: String): String = when (sanitizeStyle(style)) {
        STYLE_WATER, STYLE_RIPPLE -> "drop"
        STYLE_LIQUID -> "bubble"
        STYLE_BLOSSOM, STYLE_SPARKLE -> "twinkle"
        STYLE_FIREWORKS -> "firework"
        STYLE_VOLCANO -> "rumble"
        STYLE_ROCKET -> "launch"
        STYLE_CAT -> "meow"
        STYLE_MONKEY -> "monkey"
        STYLE_FROG -> "ribbit"
        STYLE_SWING -> "zip"
        STYLE_FISH -> "splash"
        STYLE_BUBBLES -> "bubbles"
        STYLE_BALLOON -> "balloon"
        STYLE_SPRAY -> "spray"
        STYLE_WIND -> "whoosh"
        STYLE_NINJA -> "swish"
        STYLE_CRACK -> "crack"
        STYLE_UFO -> "ufo"
        STYLE_BEES -> "buzz"
        STYLE_SOLAR -> "cosmic"
        STYLE_CAPSULE -> "tap"
        STYLE_LINE -> "tick"
        STYLE_JELLY, STYLE_ELASTIC -> "boing"
        STYLE_NEON -> "blip"
        STYLE_AURORA -> "chime"
        STYLE_LAVA, STYLE_HEARTBEAT -> "thud"
        STYLE_GLASS -> "glass"
        STYLE_SPECTRUM -> "marimba"
        else -> "tick"
    }

    /**
     * How far into the screen the window reaches for a bump [depthPx] deep: the style's own reach,
     * and room for the number beside it when that is shown, in pixels.
     */
    fun windowReach(style: String, depthPx: Float, density: Float, showPercent: Boolean): Int {
        val styleReach = effectDepth(sanitizeStyle(style), depthPx, density) * spec(style).reach + 8f * density
        val numberReach = if (showPercent) depthPx * 1.3f + 64f * density else 0f
        return maxOf(styleReach, numberReach).roundToInt()
    }

    /** The screen edge the buttons are on, after the screen has turned. */
    enum class Edge { LEFT, TOP, RIGHT, BOTTOM }

    /**
     * Where the window for a bump goes on the screen, and where the bump is inside it.
     *
     * @param edge which screen edge the buttons are on now.
     * @param x the window's left, in screen pixels.
     * @param y the window's top, in screen pixels.
     * @param width the window's width.
     * @param height the window's height.
     * @param centre where the bump's middle is along the edge, inside the window, in the bump's own
     *   upright terms: from the end nearer the top of the phone.
     * @param span how long the window is along the edge, in the same terms.
     * @param flipped whether the bump's upright "towards the bottom of the phone" runs backwards
     *   along the window — the phone upside down, or turned so its top is to the right.
     */
    data class Placement(
        val edge: Edge,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val centre: Float,
        val span: Int,
        val reach: Int,
        val flipped: Boolean,
    )

    /**
     * Places a bump for buttons on the [left] or right of an upright phone, centred [along] that
     * side (0 at the top, 1 at the bottom), on a screen [screenWidth] by [screenHeight] that has been
     * turned [turns] quarter turns from upright — the same turning HandlerGeometry.dynamicEdge uses,
     * measured on a phone, so the bump lands where a bar that follows the phone would.
     *
     * @param span the window's length along the edge, room for the bump and its overshoot.
     * @param reach the window's depth into the screen.
     */
    fun placement(
        left: Boolean,
        along: Float,
        turns: Int,
        screenWidth: Int,
        screenHeight: Int,
        span: Int,
        reach: Int,
    ): Placement {
        val t = Math.floorMod(turns, 4)
        // The same mapping as HandlerGeometry.dynamicEdge, kept here so this file stays on its own.
        val edge = when (t) {
            1 -> if (left) Edge.BOTTOM else Edge.TOP
            2 -> if (left) Edge.RIGHT else Edge.LEFT
            3 -> if (left) Edge.TOP else Edge.BOTTOM
            else -> if (left) Edge.LEFT else Edge.RIGHT
        }
        // Distance along the screen edge, from its top or left end.
        val alongScreen = if (t >= 2) 1f - along.coerceIn(0f, 1f) else along.coerceIn(0f, 1f)
        // "Towards the bottom of the phone" runs backwards along the screen edge from a half turn.
        val flipped = t >= 2
        val horizontal = edge == Edge.TOP || edge == Edge.BOTTOM
        val edgeLength = if (horizontal) screenWidth else screenHeight
        val crossLength = if (horizontal) screenHeight else screenWidth
        val win = span.coerceIn(1, edgeLength.coerceAtLeast(1))
        val depth = reach.coerceIn(1, crossLength.coerceAtLeast(1))
        val centreScreen = alongScreen * edgeLength
        val start = (centreScreen - win / 2f).roundToInt().coerceIn(0, (edgeLength - win).coerceAtLeast(0))
        // Where the middle is inside the window, along the screen edge, then in upright terms.
        val inWindow = centreScreen - start
        val centre = if (flipped) win - inWindow else inWindow
        return when (edge) {
            Edge.LEFT -> Placement(edge, 0, start, depth, win, centre, win, depth, flipped)
            Edge.RIGHT -> Placement(edge, screenWidth - depth, start, depth, win, centre, win, depth, flipped)
            Edge.TOP -> Placement(edge, start, 0, win, depth, centre, win, depth, flipped)
            Edge.BOTTOM -> Placement(edge, start, screenHeight - depth, win, depth, centre, win, depth, flipped)
        }
    }

    /**
     * Maps a point in the bump's upright terms — [inward] from the glass, [down] along the edge
     * towards the bottom of the phone, inside a window placed by [p] — to the window's own pixels.
     */
    fun toWindow(p: Placement, inward: Float, down: Float): Pair<Float, Float> {
        val along = if (p.flipped) p.span - down else down
        return when (p.edge) {
            Edge.LEFT -> inward to along
            Edge.RIGHT -> (p.width - inward) to along
            Edge.TOP -> along to inward
            Edge.BOTTOM -> along to (p.height - inward)
        }
    }

    // ---- the level -----------------------------------------------------------------------------------------

    /** An index on a stream as the 0..1 the level bar fills to. */
    fun levelOf(index: Int, minIndex: Int, maxIndex: Int): Float {
        val span = (maxIndex - minIndex).coerceAtLeast(1)
        return ((index - minIndex).toFloat() / span).coerceIn(0f, 1f)
    }
}
