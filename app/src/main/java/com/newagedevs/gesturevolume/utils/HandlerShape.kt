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
     * The three numbers that make the sweep an ogee rather than a quarter-circle.
     *
     * Together they put both control points of the cubic on the two vertical lines the curve runs
     * between — the screen edge and the inner face — which is what makes the width follow a
     * *smoothstep* of the distance down the sweep: flat at the tip, steepest in the middle, flat
     * again where it meets the straight section. That is the profile, and it is not a matter of
     * taste: measured off the shape this preset is answering to, the width is 20% of full a fifth
     * of the way down, 40% at two fifths, 80% at seven tenths. A curve that leaves the tip at any
     * angle other than straight down hits half its width in the first eighth and comes out looking
     * like a corner that has been sanded off.
     *
     * [LEAD] is that "straight down at the tip": zero, so the curve starts with no width at all.
     * [LIFT] and [SETTLE] at a third each are what keep the descent even — pull either toward zero
     * and the curve bunches its width against one end.
     *
     * A proportion is not anyone's property and nothing here is traced from another app's assets;
     * a symmetric S between two parallel lines is the curve anyone fitting this by eye would land
     * on, and these are its textbook control points.
     */
    private const val LEAD = 0f
    private const val LIFT = 1f / 3f
    private const val SETTLE = 1f / 3f

    /**
     * The top sweep of a [TAB], as a single cubic in the bar's own coordinates.
     *
     * Returns `[x0, y0, c1x, c1y, c2x, c2y, x1, y1]`: it starts on the screen edge at the top of
     * the bar and ends on the inner face, `flare * height` down. The bottom sweep is this one
     * mirrored in y, which is the caller's job — expressing it once keeps the two ends identical
     * by construction rather than by two blocks of arithmetic agreeing.
     *
     * @param edgeOnLeft which side of the bar the screen edge is on. The bar flips sides when it
     *   is carried across the screen, and a sweep that did not flip with it would leave the curve
     *   pointing out into the app and the flat face against the glass.
     */
    fun tabSweep(width: Float, height: Float, flare: Float, edgeOnLeft: Boolean): FloatArray {
        val length = sanitizeFlare(flare) * height
        val edgeX = if (edgeOnLeft) 0f else width
        val innerX = if (edgeOnLeft) width else 0f
        val span = innerX - edgeX
        return floatArrayOf(
            edgeX, 0f,
            edgeX + span * LEAD, length * LIFT,
            innerX, length * (1f - SETTLE),
            innerX, length,
        )
    }
}
