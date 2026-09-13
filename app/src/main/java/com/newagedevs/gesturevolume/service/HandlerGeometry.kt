package com.newagedevs.gesturevolume.service

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowInsets
import android.view.WindowManager
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
 *  2. **Insets are read, never assumed.** The usable frame excludes the display cutout — and only
 *     the cutout, since the bar is drawn over the system bars (see `OverlayController.readFrame`) —
 *     so "the left edge" means the edge of the glass beside the camera, in every rotation.
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
        val rotation: Int = Surface.ROTATION_0
    ) {
        val usableWidth: Int get() = (displayWidth - insetLeft - insetRight).coerceAtLeast(0)
        val usableHeight: Int get() = (displayHeight - insetTop - insetBottom).coerceAtLeast(0)
        val isPortrait: Boolean get() = displayHeight >= displayWidth
    }

    /**
     * @param uiContext must be a context configured for the current display — the Service itself is
     *   fine, since a Service's Resources are reconfigured on a configuration change.
     *   Never [android.content.res.Resources.getSystem], which is documented as not being
     *   configured for the current screen and does not change with orientation.
     */
    @Suppress("DEPRECATION")
    fun read(uiContext: Context, wm: WindowManager?): Frame? {
        if (wm == null) return null
        val density = uiContext.resources.displayMetrics.density

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val bounds = metrics.bounds
            // The cutout alone, matching the only insets the overlay windows fit. Ignoring visibility
            // for the same reason as ever: nothing about the frame should change with a transient bar.
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.displayCutout()
            )
            return Frame(
                displayWidth = bounds.width(),
                displayHeight = bounds.height(),
                insetLeft = insets.left,
                insetTop = insets.top,
                insetRight = insets.right,
                insetBottom = insets.bottom,
                density = density,
                rotation = wm.defaultDisplay.rotation
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

    /** Matches [com.newagedevs.gesturevolume.data.local.SharedPref.getHandlerPositionFraction]. */
    const val DEFAULT_POSITION_FRACTION = 0.12f
}
