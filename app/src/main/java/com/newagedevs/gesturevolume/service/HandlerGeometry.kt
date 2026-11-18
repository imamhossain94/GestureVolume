package com.newagedevs.gesturevolume.service

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowInsets
import android.view.WindowManager
import com.newagedevs.gesturevolume.utils.HandlerPresets
import kotlin.math.roundToInt

/**
 * The single source of truth for where the floating handler sits.
 *
 * Everything about the bar's placement flows through here — the drag position, the edge-offset
 * clamp, the rotation re-layout and the curved-edge inset — so those features cannot drift apart.
 *
 * Two rules make this correct where the old code was not:
 *
 *  1. **Vertical position is a fraction, not pixels.** The bar's *centre* is stored as a fraction
 *     of the usable height. A raw pixel offset cannot survive rotation: a y of 260 sits near the
 *     top of a ~2200px portrait screen but is halfway down a ~950px landscape one, and when the
 *     value exceeds the frame the window manager slides the bar flush against the bottom of the
 *     display — which is exactly where the navigation bar lives. That is the reported
 *     "overlay jumps to the navigation bar after rotating" bug.
 *
 *  2. **The bar's frame is the whole glass.** From Android 11 the bar's windows are laid out into
 *     the camera cutout and over the system bars (see `OverlayController.fitBarFrame`), so "the
 *     left edge" is the edge of the glass in every rotation. It used to exclude the cutout, which
 *     on a phone turned to the left — the camera then on the left — held a bar on that side a
 *     camera's width off the glass along its whole length, and kept a bar out of the status bar's
 *     strip in portrait. The camera itself is kept clear of by [clearOfCutouts] instead, which
 *     moves the bar only where it would actually be under it. The panels that open beside the bar
 *     still keep out of the cutout: [read] with `fitCutout`.
 *
 *  3. **Horizontal is a fraction too, and it is stored per orientation.** Once the bar can sit
 *     anywhere rather than against one of two edges, x needs the same rotation-proof treatment as
 *     y — and it needs it separately for portrait and landscape, because the usable frame swaps
 *     its axes on rotation and one stored pair cannot describe both. See
 *     [com.newagedevs.gesturevolume.data.local.SharedPref.getHandlerPosXFraction].
 */
object HandlerGeometry {

    /** A snapshot of the display frame the handler is being placed into. */
    data class Frame(
        val displayWidth: Int,
        val displayHeight: Int,
        val insetLeft: Int,
        val insetTop: Int,
        val insetRight: Int,
        val insetBottom: Int,
        val density: Float,
        /**
         * The display's [Surface] rotation. Part of the frame so a half turn — the same size and,
         * often, the same insets — still reads as a change and moves a bar that follows the phone.
         */
        val rotation: Int = Surface.ROTATION_0,
        /**
         * Where the camera cutouts are, in this frame's coordinates: what a bar resting flush on
         * the glass is moved along its edge to keep out from under. See [clearOfCutouts].
         */
        val cutouts: List<Box> = emptyList(),
    ) {
        val usableWidth: Int get() = (displayWidth - insetLeft - insetRight).coerceAtLeast(0)
        val usableHeight: Int get() = (displayHeight - insetTop - insetBottom).coerceAtLeast(0)
        val isPortrait: Boolean get() = displayHeight >= displayWidth
    }

    /** A rectangle in pixels, without the framework's Rect, so the geometry stays host-testable. */
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /**
     * @param uiContext must be a context configured for the current display — the Service itself is
     *   fine, since a Service's Resources are reconfigured on a configuration change.
     *   Never [android.content.res.Resources.getSystem], which is documented as not being
     *   configured for the current screen and does not change with orientation.
     * @param fitCutout the frame less the camera cutout: the one the Deck and the menu are laid out
     *   in, so nothing of theirs ends up under the camera. Without it, from Android 11, the whole
     *   display: the bar's own frame. Below Android 11 the two are the same.
     */
    @Suppress("DEPRECATION")
    fun read(uiContext: Context, wm: WindowManager?, fitCutout: Boolean = false): Frame? {
        if (wm == null) return null
        val density = uiContext.resources.displayMetrics.density

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val bounds = metrics.bounds
            // Ignoring visibility for the same reason as ever: nothing about the frame should
            // change with a transient bar.
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.displayCutout()
            )
            val left = if (fitCutout) insets.left else 0
            val top = if (fitCutout) insets.top else 0
            val cutouts = runCatching {
                metrics.windowInsets.displayCutout?.boundingRects.orEmpty().map { r ->
                    Box(r.left - left, r.top - top, r.right - left, r.bottom - top)
                }
            }.getOrDefault(emptyList())
            return Frame(
                displayWidth = bounds.width(),
                displayHeight = bounds.height(),
                insetLeft = left,
                insetTop = top,
                insetRight = if (fitCutout) insets.right else 0,
                insetBottom = if (fitCutout) insets.bottom else 0,
                density = density,
                rotation = wm.defaultDisplay.rotation,
                cutouts = cutouts,
            )
        }

        // API 26-29: no WindowMetrics. Horizontal placement needs nothing from here — a window
        // without FLAG_LAYOUT_IN_SCREEN is already laid out inside the decor frame on these
        // releases — so only a usable *height* is required, for the fraction<->pixel conversion.
        val dm = DisplayMetrics()
        val display = wm.defaultDisplay
        display.getRealMetrics(dm)
        val rotation = display.rotation
        val landscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
        return Frame(
            displayWidth = dm.widthPixels,
            displayHeight = dm.heightPixels,
            insetLeft = 0,
            insetTop = systemDimen(uiContext, "status_bar_height"),
            insetRight = 0,
            // Which physical side a 3-button nav bar lands on in landscape is OEM-dependent, and
            // pre-R the decor frame already accounts for it, so only the portrait case is modelled.
            insetBottom = if (landscape) 0 else systemDimen(uiContext, "navigation_bar_height"),
            density = density,
            rotation = rotation
        )
    }

    private fun systemDimen(ctx: Context, name: String): Int = try {
        val id = ctx.resources.getIdentifier(name, "dimen", "android")
        if (id > 0) ctx.resources.getDimensionPixelSize(id) else 0
    } catch (_: Exception) {
        0
    }

    /**
     * Converts the stored fraction into a window `y`.
     *
     * @param fraction where the bar's **centre** sits: 0f at the top of the usable area, 1f at the
     *   bottom.
     * @param usableHeight the **full** height of the container. Do not pre-subtract the bar height;
     *   this function already allows for it. (Subtracting it at the call site double-counts and
     *   makes the position drift a little further up on every save.)
     */
    fun fractionToY(fraction: Float, usableHeight: Int, barHeight: Int): Int {
        val maxY = (usableHeight - barHeight).coerceAtLeast(0)
        return (fraction.coerceIn(0f, 1f) * usableHeight - barHeight / 2f)
            .roundToInt()
            .coerceIn(0, maxY)
    }

    /**
     * The inverse of [fractionToY]. Call this once when a drag ends, never per move frame.
     *
     * @param usableHeight the **full** container height, matching [fractionToY].
     */
    fun yToFraction(y: Int, usableHeight: Int, barHeight: Int): Float {
        if (usableHeight <= 0) return DEFAULT_POSITION_FRACTION
        return ((y + barHeight / 2f) / usableHeight).coerceIn(0f, 1f)
    }

    /**
     * Absolute window `x` — measured from the left edge of the *usable* frame — for a bar resting
     * against one side, honouring the user's edge margin.
     *
     * Absolute coordinates, so the caller must be using `Gravity.LEFT`. Used to seed a free
     * position for an install that only ever had a Left/Right side to its name.
     */
    fun sideToX(isLeft: Boolean, usableWidth: Int, barWidth: Int, edgeMarginPx: Int): Int {
        val maxX = (usableWidth - barWidth).coerceAtLeast(0)
        val x = if (isLeft) edgeMarginPx else usableWidth - barWidth - edgeMarginPx
        return x.coerceIn(0, maxX)
    }

    /**
     * Holds [x] to the band the edge margin allows: never closer than [edgeMarginPx] to either
     * side of the usable frame.
     *
     * This is what makes "Edge offset" mean one thing everywhere. It used to apply only where the
     * bar came to rest against a side, so a bar dragged into open screen ignored it and a bar
     * pushed to the edge sat on the gesture strip the setting exists to avoid. Now the same clamp
     * runs on every drag frame, on every rest, and on every rotation, so the gap the user asked
     * for is the gap they always get — and pushing the bar at a side parks it exactly there,
     * which is the whole of what edge snapping used to be for.
     *
     * A margin too large for the frame (a wide bar on a narrow screen) would invert the band and
     * make `coerceIn` throw, so the bar is centred in that case rather than the setting winning an
     * argument it cannot usefully win.
     */
    fun clampX(x: Int, usableWidth: Int, barWidth: Int, edgeMarginPx: Int): Int {
        val maxX = (usableWidth - barWidth).coerceAtLeast(0)
        val margin = edgeMarginPx.coerceAtLeast(0)
        if (margin * 2 > maxX) return maxX / 2
        return x.coerceIn(margin, maxX - margin)
    }

    /**
     * Where a bar released at [x] comes to rest when snapping is on: flush against the nearer
     * side, at exactly [edgeMarginPx] from it.
     *
     * Deliberately expressed as [sideToX] of [xToIsLeft] rather than as its own arithmetic. The
     * resting place of a snapped bar and the seed position of a migrating install are the same
     * question — "where does this side put the bar?" — and answering it twice is how the two
     * drift apart by a pixel or two and start an argument on every rotation.
     */
    fun snapX(x: Int, usableWidth: Int, barWidth: Int, edgeMarginPx: Int): Int =
        sideToX(xToIsLeft(x, usableWidth, barWidth), usableWidth, barWidth, edgeMarginPx)

    /**
     * The horizontal twin of [fractionToY], with identical semantics: [fraction] locates the bar's
     * **centre**, 0f flush left and 1f flush right.
     *
     * Same shape as the vertical conversion on purpose. Free positioning stores a pair of
     * fractions, and a pair whose two halves round or clamp differently drifts diagonally — a few
     * pixels per save, in one direction only, which is exactly the kind of bug that takes a
     * fortnight of "it moved again" reports to pin down.
     */
    fun fractionToX(fraction: Float, usableWidth: Int, barWidth: Int): Int {
        val maxX = (usableWidth - barWidth).coerceAtLeast(0)
        return (fraction.coerceIn(0f, 1f) * usableWidth - barWidth / 2f)
            .roundToInt()
            .coerceIn(0, maxX)
    }

    /** The inverse of [fractionToX]. Call once when a drag ends, never per move frame. */
    fun xToFraction(x: Int, usableWidth: Int, barWidth: Int): Float {
        if (usableWidth <= 0) return 1f
        return ((x + barWidth / 2f) / usableWidth).coerceIn(0f, 1f)
    }

    /**
     * Which side a bar sitting at [x] belongs to: whichever one its *centre* is nearer.
     *
     * Centre rather than leading edge, so a wide bar dropped astride the midpoint is dressed for
     * the side the user actually left most of it on. Nothing moves the bar as a result — this only
     * decides which way its flat edge and icon face.
     */
    fun xToIsLeft(x: Int, usableWidth: Int, barWidth: Int): Boolean {
        if (usableWidth <= 0) return true
        return (x + barWidth / 2f) < usableWidth / 2f
    }

    /**
     * Where along its edge a bar at ([x], [y]), [width] by [height], has to be so that it is not
     * under a camera cutout: where it is, when it is clear, or else the nearer place either side
     * of the cutout that still fits in [frameLength] — the frame's height for a bar standing on a
     * side, its width for one lying along the top or bottom ([lying]).
     *
     * Only along the edge, never off it: the point of resting flush on the glass is that it is on
     * the glass, and the camera is a few millimetres of a long edge. A bar too long to fit either
     * side stays where it was, partly under the camera, rather than being pushed off the screen.
     *
     * @return the new y for an upright bar, the new x for a lying one.
     */
    fun clearOfCutouts(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        cutouts: List<Box>,
        lying: Boolean,
        frameLength: Int,
        gapPx: Int = 0,
    ): Int {
        var along = if (lying) x else y
        val length = if (lying) width else height
        val maxAlong = (frameLength - length).coerceAtLeast(0)
        // Each cutout the bar overlaps across its thickness, as a span along the edge.
        val spans = cutouts.filter { c ->
            if (lying) {
                c.bottom > y && c.top < y + height
            } else {
                c.right > x && c.left < x + width
            }
        }.map { c -> if (lying) (c.left - gapPx) to (c.right + gapPx) else (c.top - gapPx) to (c.bottom + gapPx) }
        if (spans.isEmpty()) return along
        // A couple of passes settle a bar moved off one cutout onto another.
        repeat(spans.size + 1) {
            val hit = spans.firstOrNull { (start, end) -> along < end && along + length > start } ?: return along
            val before = hit.first - length
            val after = hit.second
            val candidates = listOf(before, after).filter { it in 0..maxAlong }
            val best = candidates.minByOrNull { kotlin.math.abs(it - along) } ?: return along
            along = best
        }
        return along
    }

    /**
     * A stored place along one axis, measured in a frame that started [inset] pixels in and was
     * [oldLength] long, as the same place in a frame that starts at the edge of the glass and is
     * [glassLength] long. Where the bar's centre was on the glass, it stays.
     */
    fun toGlassFraction(fraction: Float, inset: Int, oldLength: Int, glassLength: Int): Float {
        if (glassLength <= 0 || oldLength <= 0) return fraction.coerceIn(0f, 1f)
        return ((inset + fraction.coerceIn(0f, 1f) * oldLength) / glassLength).coerceIn(0f, 1f)
    }

    /** A screen edge the bar can rest against. */
    enum class Edge {
        LEFT, TOP, RIGHT, BOTTOM;

        /** Top and bottom, where the bar lies along the edge rather than standing up. */
        val isHorizontal: Boolean get() = this == TOP || this == BOTTOM
    }

    /**
     * Quarter turns from upright, 0..3, for Dynamic position.
     *
     * Upright is portrait, which is rotation 0 on a phone. A device whose natural orientation is
     * landscape — most tablets — is portrait at [Surface.ROTATION_90], so the count starts there.
     * Either way, one more quarter turn of the display is one more step around the edges.
     */
    fun quarterTurns(frame: Frame): Int {
        val sideways = frame.rotation == Surface.ROTATION_90 || frame.rotation == Surface.ROTATION_270
        val naturalPortrait = frame.isPortrait != sideways
        val upright = if (naturalPortrait) Surface.ROTATION_0 else Surface.ROTATION_90
        return Math.floorMod(frame.rotation - upright, 4)
    }

    /**
     * Where a bar stored upright on one side, [along] its length, belongs after [turns] quarter
     * turns: the screen edge that is now the same edge of the phone, and how far along it.
     *
     * Measured against Edge Deck's Dynamic position on a real phone. At [Surface.ROTATION_90] a
     * handle on the right edge in portrait is on the top edge, lying down, over the same stretch of
     * glass: the top of the phone is now at the left of the screen, so the distance along is
     * unchanged. At [Surface.ROTATION_270] the phone is turned the other way, the right edge is the
     * bottom, and the distance runs from the other end. [along] runs top to bottom on a side and
     * left to right on the top or bottom, the way the stored fractions already do.
     */
    fun dynamicEdge(uprightIsLeft: Boolean, along: Float, turns: Int): Pair<Edge, Float> =
        when (Math.floorMod(turns, 4)) {
            1 -> (if (uprightIsLeft) Edge.BOTTOM else Edge.TOP) to along
            2 -> (if (uprightIsLeft) Edge.RIGHT else Edge.LEFT) to 1f - along
            3 -> (if (uprightIsLeft) Edge.TOP else Edge.BOTTOM) to 1f - along
            else -> (if (uprightIsLeft) Edge.LEFT else Edge.RIGHT) to along
        }

    /**
     * The inverse of [dynamicEdge]: the upright side, and the place along it, of a bar resting on
     * [edge]. Null for an edge that is the phone's own top or bottom at this rotation, which a
     * bar that follows the phone never rests on.
     */
    fun uprightFromEdge(edge: Edge, along: Float, turns: Int): Pair<Boolean, Float>? {
        val flipped = Math.floorMod(turns, 4) >= 2
        for (left in booleanArrayOf(true, false)) {
            if (dynamicEdge(left, 0f, turns).first == edge) {
                return left to (if (flipped) 1f - along else along).coerceIn(0f, 1f)
            }
        }
        return null
    }

    /**
     * Matches [com.newagedevs.gesturevolume.data.local.SharedPref.getHandlerPositionFraction]:
     * taken from the presets rather than written out again, which is how it had drifted to a
     * default two changes old.
     */
    const val DEFAULT_POSITION_FRACTION = HandlerPresets.POSITION_FRACTION
}
