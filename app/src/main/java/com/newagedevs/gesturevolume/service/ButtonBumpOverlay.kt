package com.newagedevs.gesturevolume.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.WindowManager
import com.newagedevs.gesturevolume.data.local.ButtonBumpStore
import com.newagedevs.gesturevolume.ui.view.ButtonBumpView
import com.newagedevs.gesturevolume.utils.ButtonBump

/**
 * Puts the button animation on the glass: one small window at the edge beside the volume buttons,
 * there only while the bump is out.
 *
 * Drawn through the accessibility service whenever it is bound — its windows are stacked over the
 * status bar, so the bump can come out of the edge there too, as it does in landscape where the
 * buttons are along the top — and as an ordinary overlay otherwise. Never touchable: it is a picture
 * of the press, and the app underneath keeps every touch.
 *
 * @param windowType `TYPE_ACCESSIBILITY_OVERLAY` with the accessibility service as [context], or
 *   `TYPE_APPLICATION_OVERLAY` with the overlay permission.
 */
class ButtonBumpOverlay(
    private val context: Context,
    private val windowType: Int,
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    private var view: ButtonBumpView? = null
    private var params: WindowManager.LayoutParams? = null
    private var lastLimitBuzzMs = 0L

    /** Whether this overlay draws through [context] as [windowType]: for its owner to tell when to make another. */
    fun isFor(context: Context, windowType: Int): Boolean =
        this.context === context && this.windowType == windowType

    /** Whether the bump is on the glass now, or on its way. */
    fun isOut(): Boolean = view?.isOut() == true

    /**
     * A volume button was pressed: brings the bump out beside the buttons, or keeps it out, showing
     * [level] (0..1).
     *
     * @param direction `+1` for volume up, `-1` for down.
     * @param atLimit the level did not move: at an end of its range, or held there by the system.
     */
    fun press(settings: ButtonBumpStore.Settings, direction: Int, level: Float, atLimit: Boolean) {
        val bump = ensureWindow(settings) ?: return
        bump.press(direction, level, atLimit)
        buzz(settings.haptic, atLimit)
    }

    /** The level moved on its own while the bump is out: shows where it went. */
    fun setLevel(level: Float) {
        view?.setLevel(level)
    }

    /** Puts the bump away now. */
    fun dismiss() {
        view?.dismiss()
    }

    /** Takes the window down, whatever the bump was doing. */
    fun destroy() {
        removeNow()
    }

    /**
     * The window, placed for how the screen is turned now and sized for the style, created if there
     * is none. A rotation since the last press moves it to the edge the buttons are on now.
     */
    private fun ensureWindow(settings: ButtonBumpStore.Settings): ButtonBumpView? {
        val wm = windowManager ?: return null
        val frame = HandlerGeometry.read(context, wm) ?: return null
        val density = context.resources.displayMetrics.density
        val lengthPx = settings.lengthDp * density
        val depthPx = settings.depthDp * density
        val placement = ButtonBump.placement(
            left = settings.left,
            along = settings.position,
            turns = HandlerGeometry.quarterTurns(frame),
            screenWidth = frame.displayWidth,
            screenHeight = frame.displayHeight,
            span = ButtonBump.windowSpan(settings.style, lengthPx, density, depthPx),
            reach = ButtonBump.windowReach(settings.style, depthPx, density, settings.showPercent),
        )
        // Laid out in the screen, not in the frame under the status bar (FLAG_LAYOUT_IN_SCREEN), so
        // on every release the window's x and y are the screen's own pixels.
        val originX = 0
        val originY = 0

        val existing = view
        val existingParams = params
        if (existing != null && existingParams != null) {
            existing.configure(settings, placement)
            val x = placement.x - originX
            val y = placement.y - originY
            if (existingParams.x != x || existingParams.y != y ||
                existingParams.width != placement.width || existingParams.height != placement.height
            ) {
                existingParams.x = x
                existingParams.y = y
                existingParams.width = placement.width
                existingParams.height = placement.height
                runCatching { wm.updateViewLayout(existing, existingParams) }
            }
            return existing
        }

        val bump = ButtonBumpView(context).apply {
            configure(settings, placement)
            listener = ButtonBumpView.Listener { removeNow() }
        }
        val lp = WindowManager.LayoutParams(
            placement.width,
            placement.height,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = placement.x - originX
            y = placement.y - originY
            title = "GestureVolume button animation"
            // Not touchable, so a tap on the glass under it goes through to the app — unless the
            // window is too opaque: from Android 12 a tap through another app's opaque overlay is
            // dropped. The accessibility service's windows are trusted and exempt; an ordinary
            // overlay is kept just within the limit, which on the Water style's larger window is
            // the difference between the ripples and a strip of the screen that ignores taps.
            if (windowType == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
                val input = context.getSystemService(android.hardware.input.InputManager::class.java)
                alpha = (input?.maximumObscuringOpacityForTouch ?: 0.8f).coerceIn(0.5f, 1f)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Over the whole glass, into the camera cutout and over the system bars: the
                // buttons are where they are, whatever the screen has put beside them.
                setFitInsetsTypes(0)
                isFitInsetsIgnoringVisibility = true
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        return try {
            wm.addView(bump, lp)
            view = bump
            params = lp
            bump
        } catch (e: Exception) {
            android.util.Log.e("ButtonBumpOverlay", "addView failed", e)
            null
        }
    }

    private fun removeNow() {
        val bump = view ?: return
        bump.listener = null
        view = null
        params = null
        runCatching { windowManager?.removeView(bump) }
    }

    /**
     * A tick for a step, a firmer double one for a press that hit the end — once, not for every
     * repeat of a key held there.
     */
    private fun buzz(haptic: String, atLimit: Boolean) {
        if (haptic == ButtonBumpStore.HAPTIC_OFF) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val now = SystemClock.uptimeMillis()
        if (atLimit) {
            if (now - lastLimitBuzzMs < LIMIT_BUZZ_GAP_MS) return
            lastLimitBuzzMs = now
        }
        val strong = haptic == ButtonBumpStore.HAPTIC_STRONG
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(
                when {
                    atLimit -> VibrationEffect.EFFECT_DOUBLE_CLICK
                    strong -> VibrationEffect.EFFECT_CLICK
                    else -> VibrationEffect.EFFECT_TICK
                }
            )
        } else {
            VibrationEffect.createOneShot(if (strong || atLimit) 22L else 10L, if (strong) 255 else 120)
        }
        runCatching { v.vibrate(effect) }
    }

    private companion object {
        /** The least time between two end-of-range buzzes, so a held key does not rattle. */
        const val LIMIT_BUZZ_GAP_MS = 450L
    }
}
