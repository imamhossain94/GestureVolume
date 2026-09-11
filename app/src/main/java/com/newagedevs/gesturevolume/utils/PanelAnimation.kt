package com.newagedevs.gesturevolume.utils

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * How a floating panel arrives: the long-press menu, the Deck, the Quick panel.
 *
 * The identifiers are a persistence format, like [HandlerActions] and [PanelTheme]: written
 * verbatim into preferences, never renamed.
 *
 * **Why the surface holds still.** Every one of these transforms the panel's *contents*, never the
 * rectangle the panel occupies. That is not a stylistic choice — the frosted pane behind a panel
 * is a separate window, its size and position are window attributes, and pushing new ones at it
 * sixty times a second is a relayout per frame. So a panel that slid in would spend its entrance
 * travelling across a stationary slab of blur. The surface fades; the things on it move. It is
 * also, as it happens, how the platform's own sheets and menus behave.
 *
 * The maths is here, away from anything that draws, so it can be tested without a device: a
 * [Frame] is eight numbers and a clip, and every animation is a function from progress to those.
 */
object PanelAnimation {

    /** Nothing but opacity. The safe one, and the fastest. */
    const val FADE = "fade"

    /** A small scale up. What the menu has done since it grew a backdrop. */
    const val POP = "pop"

    /** A scale up that overshoots and settles. */
    const val SPRING = "spring"

    /** A larger scale, from half size. */
    const val ZOOM = "zoom"

    /** Squashed flat and opening vertically, like a blind being let down. */
    const val UNFOLD = "unfold"

    /** Opening sideways out of the screen edge the bar is on. */
    const val EXPAND = "expand"

    /** Lifted from below. */
    const val RISE = "rise"

    /** Dropped from above, with a bounce at the bottom. */
    const val DROP = "drop"

    /** Nudged in from the bar's side. */
    const val SLIDE = "slide"

    /** Hinged at the bar's side, swinging flat. */
    const val SWING = "swing"

    /** Turning to face you, hinged at the bar's side. */
    const val FLIP = "flip"

    /** Tipping up from lying flat. */
    const val TILT = "tilt"

    /** Wiped on from the top down. */
    const val BLINDS = "blinds"

    /** Filling from the bottom up, the way water arrives. */
    const val TIDE = "tide"

    /** Opening from the middle outward, both ways at once. */
    const val IRIS = "iris"

    /** Arrives high and drops into place, overshooting once. */
    const val SETTLE = "settle"

    val ALL = listOf(
        FADE, POP, SPRING, ZOOM, UNFOLD, EXPAND, RISE, DROP,
        SLIDE, SWING, FLIP, TILT, BLINDS, TIDE, IRIS, SETTLE,
    )

    fun sanitize(value: String?): String = if (value in ALL) value!! else POP

    /** How long the animation runs, in milliseconds. Longer for the ones with further to travel. */
    fun durationMs(id: String): Int = when (sanitize(id)) {
        FADE -> 170
        POP, SLIDE, EXPAND -> 210
        BLINDS, TIDE, IRIS, UNFOLD -> 260
        SPRING, DROP, SETTLE, SWING -> 330
        ZOOM, FLIP, TILT, RISE -> 280
        else -> 220
    }

    /**
     * One frame of an animation: what to hand a draw layer, plus how much of the panel to show.
     *
     * Transforms only. Nothing here changes a measured size, so nothing here can move the panel's
     * rectangle out from under the pane of glass behind it.
     */
    data class Frame(
        val alpha: Float = 1f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        /** In dp. Small by design — see the class note. */
        val translationX: Float = 0f,
        val translationY: Float = 0f,
        val rotationZ: Float = 0f,
        val rotationX: Float = 0f,
        val rotationY: Float = 0f,
        val originX: Float = 0.5f,
        val originY: Float = 0.5f,
        /**
         * The visible band of the panel, top and bottom as fractions of its height.
         *
         * `0f to 1f` is all of it. A wipe narrows this; the panel is *clipped*, not scaled, which
         * is what makes a blind read as a blind rather than as a squash.
         */
        val revealFrom: Float = 0f,
        val revealTo: Float = 1f,
    )

    /**
     * The frame at raw progress [t], 0 at the start and 1 when it is over.
     *
     * Easing lives here rather than in an animation spec, so a preview that scrubs this by hand
     * and a panel that plays it on a clock get identical motion.
     *
     * @param towardLeft which way the panel opens: true when the bar is on the left, so the ones
     *   that hinge or slide do it from the side the panel actually grew out of.
     */
    fun frameAt(id: String, t: Float, towardLeft: Boolean): Frame {
        val p = t.coerceIn(0f, 1f)
        // The side the panel is anchored to, as a transform origin.
        val anchor = if (towardLeft) 0f else 1f
        // Which way "outward" points, for the transforms that come in from off to one side.
        val away = if (towardLeft) -1f else 1f

        // Opacity settles well before the movement does on the long ones, so the panel is readable
        // while it is still arriving rather than fading in all the way to the last frame.
        val quickFade = ease(p / 0.6f)

        return when (sanitize(id)) {
            FADE -> Frame(alpha = ease(p))

            POP -> Frame(alpha = quickFade, scaleX = lerp(0.94f, 1f, ease(p)), scaleY = lerp(0.94f, 1f, ease(p)))

            SPRING -> {
                val s = overshoot(p, 1.7f)
                Frame(alpha = quickFade, scaleX = lerp(0.86f, 1f, s), scaleY = lerp(0.86f, 1f, s))
            }

            ZOOM -> {
                val s = ease(p)
                Frame(alpha = quickFade, scaleX = lerp(0.55f, 1f, s), scaleY = lerp(0.55f, 1f, s))
            }

            UNFOLD -> Frame(alpha = quickFade, scaleY = lerp(0.35f, 1f, ease(p)))

            EXPAND -> Frame(
                alpha = quickFade,
                scaleX = lerp(0.4f, 1f, ease(p)),
                originX = anchor,
            )

            RISE -> Frame(alpha = quickFade, translationY = lerp(22f, 0f, ease(p)))

            DROP -> Frame(alpha = quickFade, translationY = lerp(-26f, 0f, overshoot(p, 1.4f)))

            SLIDE -> Frame(alpha = quickFade, translationX = lerp(26f * away, 0f, ease(p)))

            SWING -> Frame(
                alpha = quickFade,
                rotationZ = lerp(-7f * away, 0f, overshoot(p, 1.5f)),
                originX = anchor,
                originY = 0.5f,
            )

            FLIP -> Frame(
                alpha = quickFade,
                rotationY = lerp(62f * away, 0f, ease(p)),
                originX = anchor,
            )

            TILT -> Frame(alpha = quickFade, rotationX = lerp(-55f, 0f, ease(p)), originY = 1f)

            // The wipes keep full opacity: fading one as well turns a clean edge into a smear,
            // and the edge travelling across the panel is the whole of the effect.
            BLINDS -> Frame(revealTo = ease(p))

            TIDE -> Frame(revealFrom = 1f - ease(p))

            IRIS -> {
                val half = ease(p) / 2f
                Frame(revealFrom = 0.5f - half, revealTo = 0.5f + half)
            }

            SETTLE -> {
                val s = overshoot(p, 2.2f)
                Frame(alpha = quickFade, translationY = lerp(-18f, 0f, s), scaleY = lerp(0.96f, 1f, s))
            }

            else -> Frame(alpha = ease(p))
        }
    }

    private fun lerp(from: Float, to: Float, f: Float): Float = from + (to - from) * f

    /** A decelerate curve: quick off the mark, easing into place. */
    private fun ease(t: Float): Float {
        val p = t.coerceIn(0f, 1f)
        return 1f - (1f - p).pow(3f)
    }

    /**
     * A curve that passes its target and comes back, once.
     *
     * A damped sine rather than the usual polynomial overshoot, because the polynomial's single
     * bounce is symmetric and reads as a wobble; this one decays, which reads as weight.
     */
    private fun overshoot(t: Float, tension: Float): Float {
        val p = t.coerceIn(0f, 1f)
        if (p <= 0f) return 0f
        if (p >= 1f) return 1f
        // Normalised by its own value at 1 so it lands exactly on the target. The raw curve is
        // still a percent or two short there, and a panel that finished a percent short would
        // snap the rest of the way on its last frame — the one artefact this whole file exists
        // to avoid.
        return damped(p, tension) / damped(1f, tension)
    }

    private fun damped(p: Float, tension: Float): Float {
        val decay = 2.718281828f.pow(-tension * p * 2f)
        val phase = p * tension * 3.2f
        return 1f - decay * cos(phase) - decay * sin(phase) * 0.25f
    }
}
