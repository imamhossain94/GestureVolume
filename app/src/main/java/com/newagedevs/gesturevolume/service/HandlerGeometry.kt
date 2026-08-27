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
 * Everything about the bar's placement flows through here — the drag position, the edge it snaps
 * to, the rotation re-layout, and the curved-edge inset — so those features cannot drift apart.
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
 *  2. **Insets are read, never assumed.** The usable frame excludes the system bars and the display
 *     cutout, so "the left edge" means the left edge of the area the user can actually touch,
 *     in every rotation.
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
        val density: Float
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
            // ignoringVisibility: a bar that is transiently hidden (immersive video, for example)
            // must not make the handler jump and then jump back.
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            return Frame(
                displayWidth = bounds.width(),
                displayHeight = bounds.height(),
                insetLeft = insets.left,
                insetTop = insets.top,
                insetRight = insets.right,
                insetBottom = insets.bottom,
                density = density
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
            density = density
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
     * Absolute coordinates, so the caller must be using `Gravity.LEFT`. The resting state uses
     * side gravity with `x = edgeMargin` instead; this is for the drag and the snap animation,
     * which need a single continuous axis to interpolate along.
     */
    fun sideToX(isLeft: Boolean, usableWidth: Int, barWidth: Int, edgeMarginPx: Int): Int {
        val maxX = (usableWidth - barWidth).coerceAtLeast(0)
        val x = if (isLeft) edgeMarginPx else usableWidth - barWidth - edgeMarginPx
        return x.coerceIn(0, maxX)
    }

    /**
     * Which edge a bar released at [x] belongs to: whichever one its *centre* is nearer.
     *
     * Centre rather than leading edge, so a wide bar dropped astride the midpoint snaps back to
     * the side the user actually left most of it on.
     */
    fun xToIsLeft(x: Int, usableWidth: Int, barWidth: Int): Boolean {
        if (usableWidth <= 0) return true
        return (x + barWidth / 2f) < usableWidth / 2f
    }

    /** Matches [com.newagedevs.gesturevolume.data.local.SharedPref.getHandlerPositionFraction]. */
    const val DEFAULT_POSITION_FRACTION = 0.5f
}
