package com.newagedevs.gesturevolume.utils

/**
 * The values the bar's behaviour settings take: how far a swipe moves the volume, what the bar does
 * while the keyboard is open, and how it moves out of the keyboard's way.
 *
 * Out here rather than beside their keys in [com.newagedevs.gesturevolume.data.local.SharedPref],
 * whose constants are private, because the settings screens offer these and the overlay acts on
 * them. Like [HandlerActions], they are a persistence format: written verbatim, never renamed.
 */
object BarBehaviour {

    /** A swipe moves one step per finger's width of travel, as the bar always has. */
    const val SWIPE_STEP_BY_LENGTH = 0

    /** [SWIPE_STEP_BY_LENGTH], then the fixed amounts one swipe may move, in percent of the range. */
    val SWIPE_STEP_PERCENTS: List<Int> = listOf(SWIPE_STEP_BY_LENGTH, 5, 10, 20)

    /** Lift the bar clear of the keyboard while it is open. */
    const val KEYBOARD_MOVE = "move"

    /** Put the bar away while the keyboard is open. */
    const val KEYBOARD_HIDE = "hide"

    /** Leave the bar where it is. */
    const val KEYBOARD_STAY = "stay"

    val KEYBOARD_BEHAVIOURS: List<String> = listOf(KEYBOARD_MOVE, KEYBOARD_HIDE, KEYBOARD_STAY)

    // ---- how the bar moves clear of the keyboard, with [KEYBOARD_MOVE] --------------------------

    /** Eases up and settles, as it always has. */
    const val MOTION_GLIDE = "glide"

    /** Goes a little past and springs back. */
    const val MOTION_SPRING = "spring"

    /** Lands and bounces, like a dropped ball, the other way up. */
    const val MOTION_BOUNCE = "bounce"

    /** The glide, in a blink. */
    const val MOTION_QUICK = "quick"

    /** Fades where it is and fades in where it goes, without travelling. */
    const val MOTION_FADE = "fade"

    /** Simply there. */
    const val MOTION_INSTANT = "instant"

    val KEYBOARD_MOTIONS: List<String> = listOf(
        MOTION_GLIDE, MOTION_SPRING, MOTION_BOUNCE, MOTION_QUICK, MOTION_FADE, MOTION_INSTANT,
    )

    fun sanitizeMotion(id: String?): String = if (id in KEYBOARD_MOTIONS) id!! else MOTION_GLIDE

    /** How long [motion] takes to carry the bar there, in milliseconds. */
    fun motionMs(motion: String): Long = when (sanitizeMotion(motion)) {
        MOTION_SPRING -> 460L
        MOTION_BOUNCE -> 700L
        MOTION_QUICK -> 110L
        MOTION_FADE -> 300L
        MOTION_INSTANT -> 0L
        else -> 180L
    }

    /**
     * How far along its way the bar is, [t] of the way through [motion]: 0 where it was, 1 where it
     * is going, and past 1 for a spring on its way back. One curve for the live bar and for the
     * previews the setting is chosen from, so the two move alike.
     */
    fun motionAt(motion: String, t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return when (sanitizeMotion(motion)) {
            // Android's overshoot at a tension of 1.6: out past the mark by about a tenth, and back.
            MOTION_SPRING -> {
                val u = x - 1f
                u * u * ((SPRING_TENSION + 1f) * u + SPRING_TENSION) + 1f
            }
            MOTION_BOUNCE -> bounce(x)
            // It does not travel: it is where it was until half way, and where it goes after.
            MOTION_FADE -> if (x < 0.5f) 0f else 1f
            MOTION_INSTANT -> 1f
            // Android's decelerate: quick off the mark, easing in to land.
            else -> 1f - (1f - x) * (1f - x)
        }
    }

    /** How much of the bar shows [t] of the way through [motion]: all of it, except for a fade. */
    fun motionAlphaAt(motion: String, t: Float): Float =
        if (sanitizeMotion(motion) == MOTION_FADE) kotlin.math.abs(1f - 2f * t.coerceIn(0f, 1f)) else 1f

    private const val SPRING_TENSION = 1.6f

    /** Android's bounce curve: three landings, each lower than the last. */
    private fun bounce(t: Float): Float {
        fun b(x: Float) = x * x * 8f
        val x = t * 1.1226f
        return when {
            x < 0.3535f -> b(x)
            x < 0.7408f -> b(x - 0.54719f) + 0.7f
            x < 0.9644f -> b(x - 0.8526f) + 0.9f
            else -> b(x - 1.0435f) + 0.95f
        }
    }
}
