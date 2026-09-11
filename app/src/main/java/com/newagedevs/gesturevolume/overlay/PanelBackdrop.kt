package com.newagedevs.gesturevolume.overlay

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import kotlin.math.roundToInt

/**
 * A blur that sits behind one panel and nowhere else.
 *
 * Android has two window blurs and they are not interchangeable:
 *
 *  - `FLAG_BLUR_BEHIND` with `LayoutParams.blurBehindRadius` blurs **the whole screen** behind the
 *    window, exactly like `FLAG_DIM_BEHIND` dims it. The window's own frame does not clip it — a
 *    150x1150 strip on the right edge still blurs the launcher icons in the bottom-left corner.
 *  - `Window.setBackgroundBlurRadius` blurs only **within the window's bounds**, and rounds that
 *    region off to the corner radius of the window's background drawable.
 *
 * The second is the one frosted glass is made of, and it lives on [android.view.Window] — which a
 * `WindowManager.addView` overlay does not have. A [Dialog] does, so this is a dialog: an empty,
 * untouchable, undismissable overlay window whose only job is to be a piece of blurred glass of a
 * given size at a given place.
 *
 * The Deck uses two of these, one under the strip and one under the expanded card, because a
 * single rounded rectangle spanning both would also blur the gap between them.
 *
 * **On moving it while a panel animates.** [setBounds] is a window relayout, and this class used
 * to say that a relayout per frame is what makes a window stutter — which is why the panels were
 * once forbidden from animating anything but their contents, and why their backgrounds sat still
 * while the things on them moved. The alternative was tried and does not exist: a full-screen
 * window whose background drawable is inset to the panel's rectangle blurs the *whole window*
 * regardless of the inset, so the blur region cannot be moved without moving the window. So this
 * is moved per frame, and everything that can be kept out of that path — the drawable, the blur
 * radius, redundant frames — is.
 */
@RequiresApi(Build.VERSION_CODES.S)
class PanelBackdrop(
    private val context: Context,
    private val windowType: Int,
    private val blurRadiusPx: Int,
) {

    private var dialog: Dialog? = null
    private var shownRadius = -1f
    private var bounds = intArrayOf(0, 0, 1, 1)

    /** The blur radius last pushed to the window, so it is pushed only when it changes. */
    private var shownBlurPx = -1

    /**
     * Puts the window up, blurring nothing yet.
     *
     * Shown immediately rather than when the first bounds arrive, because window stacking among
     * overlays follows the order they were added: a backdrop that appeared later than the panel it
     * belongs behind would be drawn in front of it.
     */
    fun show() {
        if (dialog != null) return
        val d = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
        val window = d.window ?: return
        window.setType(windowType)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        )
        window.setDimAmount(0f)
        d.setCancelable(false)
        d.setCanceledOnTouchOutside(false)
        d.setContentView(View(context))
        applyBackground(window, 0f)
        applyBounds(window)
        try {
            d.show()
            dialog = d
            // Again, after the dialog is up, because `Dialog.show()` reapplies its theme's window
            // attributes over the ones set before it.
            applyBounds(window)
        } catch (e: Exception) {
            // No overlay permission, or the window token went away mid-flight.
            android.util.Log.e("PanelBackdrop", "show failed", e)
        }
    }

    /**
     * Moves the blur onto [left], [top], [width] by [height], rounded off by [cornerRadiusPx].
     *
     * **In display coordinates**, measured from the top-left of the screen — not from the usable
     * frame the panels lay themselves out in. A dialog window arrives from its theme with
     * `FLAG_LAYOUT_IN_SCREEN`, and a window laid out in the screen is not moved by fit-insets, so
     * asking for the frame's coordinate space here does nothing at all: the blur simply sits one
     * status bar above the panel it belongs behind. Converting is the caller's job — see
     * `OverlayController.setFrameBounds`.
     *
     * A width or height of zero means "nothing to blur just now" — the expanded card's backdrop
     * spends most of its life there. It stays a 1x1 window with the blur switched off rather than
     * being dismissed and recreated, because recreating it would put it back on top of the Deck.
     *
     * [strength] scales the blur itself, 0 to 1, so glass can leave with a panel that is shrinking
     * or fading instead of standing where the panel was until the panel's window is gone.
     */
    fun setBounds(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        cornerRadiusPx: Float,
        strength: Float = 1f,
    ) {
        val window = dialog?.window ?: return
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val s = strength.coerceIn(0f, 1f)
        val empty = width <= 0 || height <= 0 || s < MIN_STRENGTH
        // Scaled by the strength, and never below a pixel while shown: at zero the platform takes
        // the blur down altogether and rebuilds it on the next non-zero radius, which would put
        // that work on the first frame of every fade.
        val blurPx = if (empty) 0 else (blurRadiusPx * s).roundToInt().coerceAtLeast(1)
        val next = intArrayOf(left, top, w, h)
        val sameBounds = next.contentEquals(bounds)
        val sameRadius = shownRadius == cornerRadiusPx
        val sameBlur = shownBlurPx == blurPx
        if (sameBounds && sameRadius && sameBlur) return

        bounds = next
        // Rebuilt only when the radius really changes. During an animation the rectangle moves
        // every frame and the radius almost never does, and allocating a drawable per frame is
        // the one part of this that would be worth avoiding even if the relayout were free.
        if (!sameRadius) {
            shownRadius = cornerRadiusPx
            applyBackground(window, cornerRadiusPx)
        }
        // Likewise, pushed only when it changes. Cheap between two non-zero radii — the platform
        // re-reads it on the window's next frame — which is what lets the glass fade out with a
        // panel rather than be dropped after it.
        if (!sameBlur) {
            shownBlurPx = blurPx
            window.setBackgroundBlurRadius(blurPx)
        }
        if (!sameBounds) applyBounds(window)
    }

    fun dismiss() {
        val d = dialog ?: return
        dialog = null
        try {
            d.dismiss()
        } catch (_: Exception) {
            // Already gone with the service.
        }
    }

    /**
     * The background is what gives the blur its shape.
     *
     * `setBackgroundBlurRadius` clips its region to the outline of the window's background
     * drawable, so a transparent [GradientDrawable] with a corner radius is the whole mechanism
     * for rounding the blur off — there is no separate corner API to call.
     */
    private fun applyBackground(window: android.view.Window, cornerRadiusPx: Float) {
        window.setBackgroundDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                cornerRadius = cornerRadiusPx
            }
        )
    }

    private fun applyBounds(window: android.view.Window) {
        val params = window.attributes
        // The translucent dialog theme hands this window an alpha of 0.8, which is not a style
        // choice here — it thins the blur to four fifths of the radius that was asked for.
        params.alpha = 1f
        params.gravity = Gravity.TOP or Gravity.START
        params.x = bounds[0]
        params.y = bounds[1]
        params.width = bounds[2]
        params.height = bounds[3]
        window.attributes = params
    }

    private companion object {
        /** Below this much of a blur there is nothing to see, so the blur is switched off. */
        const val MIN_STRENGTH = 0.02f
    }
}
