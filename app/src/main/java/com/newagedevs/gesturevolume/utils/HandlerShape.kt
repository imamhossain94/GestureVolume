package com.newagedevs.gesturevolume.utils

/**
 * The outline the bar is cut to.
 *
 * Until now the bar was always a rectangle with four corner radii, which is one shape with a dial
 * on it rather than a choice. The shapes people actually want from an edge launcher are not all
 * rounded rectangles: the one this file exists for is the *tab* — a bar whose ends sweep back into
 * the screen edge, so it reads as something growing out of the side of the phone rather than a
 * pill parked next to it.
 *
 * The identifiers are a persistence format, like [HandlerActions] and [PanelTheme]: written
 * verbatim into preferences, never renamed.
 *
 * The geometry lives here, away from the drawable that strokes it, so it can be tested without a
 * device — `android.graphics.Path` is a stub in a JVM unit test and would throw on the first call.
 */
object HandlerShape {

    /** Four corner radii on a rectangle. Every bar this app has ever drawn. */
    const val ROUNDED = "rounded"

    /**
     * A tab: the straight inner face runs the length of the bar, and both ends sweep out to meet
     * the screen edge, leaving no corner where the bar meets the side of the phone.
     */
    const val TAB = "tab"

    val ALL = listOf(ROUNDED, TAB)

    fun sanitize(value: String?): String = if (value in ALL) value!! else ROUNDED

    /**
     * How much of the bar's height each end sweep takes, as a fraction.
     *
     * One number turns [TAB] into a whole family rather than a single silhouette. Near the floor
     * it is a crisp tab with a short bevel at each end; around a fifth it is the shape an edge
     * launcher usually wears; at the ceiling the two sweeps meet in the middle and the bar is a
     * leaf with no straight section at all. The ceiling is 0.5 for exactly that reason — beyond it
     * the sweeps would cross and the outline would fold in on itself.
     */
    const val MIN_FLARE = 0.04f
    const val MAX_FLARE = 0.5f
    const val DEFAULT_FLARE = 0.29f

    fun sanitizeFlare(value: Float): Float =
        if (value.isNaN()) DEFAULT_FLARE else value.coerceIn(MIN_FLARE, MAX_FLARE)

    /**
     * How wide the bar is at [t] of the way down one end sweep, 0 at the tip and 1 where the sweep
     * meets the rest of the shape.
     *
     * **Why it is not a cubic.** It was, and a cubic leaves a crease. A sweep drawn as one cubic
     * can be made to *arrive* travelling straight down — that much is easy, and it is what stops
     * the join being a corner — but its curvature at that point is not zero, while the straight
     * section it joins has no curvature at all. Curvature jumping from something to nothing in one
     * place is a crease, and on a shape this size the eye picks it out even though both sides of
     * the join are smooth.
     *
     * So the profile is a fifth-order curve whose first *and* second derivatives are zero at both
     * ends, blended against a third-order one at the tip. The quintic alone is seamless at the
     * join and too timid at the tip — it leaves it barely two percent wide a seventh of the way
     * down, where the shape this is answering to is nearer ten. The blend takes the join from the
     * quintic and the tip from the cubic, which is the only part of each that was any good.
     */
    fun tabProfile(t: Float): Float {
        val p = t.coerceIn(0f, 1f)
        val cubic = p * p * (3f - 2f * p)
        val quintic = p * p * p * (p * (6f * p - 15f) + 10f)
        return cubic * (1f - p) + quintic * p
    }

    /** How many points each end sweep is drawn with. Finer than a phone can resolve. */
    const val OUTLINE_STEPS = 40

    /**
     * The whole of a tab's outline, as a polyline: `[x0, y0, x1, y1, …]`.
     *
     * From the top tip, down whichever side the screen edge is *not* on, to the bottom tip. The
     * caller closes the shape along the screen edge, which is the one straight side a tab has.
     *
     * A polyline rather than curve commands because [tabProfile] is a quintic blend and there is
     * no Bézier of the order the platform draws that reproduces it. At forty steps a sweep's
     * longest straight segment is under a pixel on any phone, so nothing is lost by flattening it,
     * and what is gained is that the shape is exactly the profile rather than an approximation of
     * it that reintroduces the crease.
     *
     * @param edgeOnLeft which side of the bar the screen edge is on. The bar flips sides when it
     *   is carried across the screen, and a sweep that did not flip with it would leave the curve
     *   pointing out into the app and the flat face against the glass.
     */
    fun tabOutline(
        width: Float,
        height: Float,
        flare: Float,
        edgeOnLeft: Boolean,
        steps: Int = OUTLINE_STEPS,
    ): FloatArray {
        val n = steps.coerceAtLeast(2)
        val length = sanitizeFlare(flare) * height
        val edgeX = if (edgeOnLeft) 0f else width
        val innerX = if (edgeOnLeft) width else 0f
        val span = innerX - edgeX

        // Both sweeps, plus the one point that carries the straight section between them. The
        // bottom sweep already finishes back on the screen edge, so there is nothing to add after
        // it — a repeated final point would be a zero-length segment for every renderer to skip.
        val points = FloatArray((n + 1) * 4 + 2)
        var i = 0
        for (step in 0..n) {
            val t = step.toFloat() / n
            points[i++] = edgeX + span * tabProfile(t)
            points[i++] = length * t
        }
        points[i++] = innerX
        points[i++] = height - length
        for (step in n downTo 0) {
            val t = step.toFloat() / n
            points[i++] = edgeX + span * tabProfile(t)
            points[i++] = height - length * t
        }
        return points
    }

    /**
     * How far down one end sweep reaches, in the same pixels the shape was measured in.
     *
     * Its own function because two things that never draw the shape need it: the blur behind an
     * open panel, which has to be pulled in to the part that is full width, and the panel's own
     * contents, which would otherwise be placed in the part that has been swept away.
     */
    fun tabSweepDepth(height: Float, flare: Float): Float = sanitizeFlare(flare) * height
}
