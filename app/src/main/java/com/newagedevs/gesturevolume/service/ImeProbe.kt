package com.newagedevs.gesturevolume.service

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.RequiresApi

/**
 * Tells the bar where the on-screen keyboard's top edge is, or that there is no keyboard.
 *
 * **Why it asks rather than listens.** The bar is an overlay, and on current Android the keyboard
 * is stacked directly above the app it is typing into — which puts every overlay *above* the
 * keyboard. A window is only handed the insets of windows above it, so no overlay window is ever
 * told the keyboard is there: the first version of this was a window of its own waiting for those
 * insets, and on a Motorola Edge 50 Fusion running Android 16 it waited for ever, the keyboard up
 * and the bar lying over its keys. (That same stacking is why the bar covered the keys at all.)
 *
 * What does know is the window manager's view of the whole display — the insets
 * [WindowManager.getCurrentWindowMetrics] reports, the keyboard among them, 925px tall on that
 * phone. Nothing announces a change in it, so it is read a few times a second while the screen is
 * on: one call into the window manager each time, and nothing at all while the screen is off.
 *
 * Below Android 11 there are no window metrics to read, and the only other way to find the
 * keyboard is for the accessibility service to read the list of windows — which it does not do,
 * and which would take window-content access it deliberately gave up. So there the bar stays where
 * it is.
 */
@RequiresApi(Build.VERSION_CODES.R)
class ImeProbe(
    context: Context,
    private val windowManager: WindowManager,
    /** The keyboard's top edge in screen pixels, or null when no keyboard is showing. */
    private val onChange: (keyboardTopPx: Int?) -> Unit,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private var running = false

    /** What was last reported, so a read that found nothing new says nothing. */
    private var reported: Int? = null

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            // With the screen off nobody is typing, and the bar is not on show.
            val interactive = power?.isInteractive != false
            if (interactive) checkNow()
            handler.postDelayed(this, if (interactive) POLL_MS else ASLEEP_POLL_MS)
        }
    }

    fun show() {
        if (running) return
        running = true
        handler.post(tick)
    }

    /**
     * Reads the keyboard now rather than on the next beat — for a bar being put up, which should be
     * placed clear of a keyboard that is already open instead of appearing under it first.
     */
    fun checkNow() {
        if (!running) return
        val metrics = runCatching { windowManager.currentWindowMetrics }.getOrNull() ?: return
        val insets = metrics.windowInsets
        val top = if (insets.isVisible(WindowInsets.Type.ime())) {
            val height = insets.getInsets(WindowInsets.Type.ime()).bottom
            // A floating keyboard is visible and insets nothing: there is no edge to keep clear of.
            if (height > 0) metrics.bounds.height() - height else null
        } else {
            null
        }
        if (top == reported) return
        reported = top
        onChange(top)
    }

    fun remove() {
        running = false
        handler.removeCallbacks(tick)
        reported = null
    }

    private companion object {
        /** A keyboard takes about this long to rise, so the bar moves with it rather than after. */
        const val POLL_MS = 300L

        /** How often to look whether the screen has come back on. */
        const val ASLEEP_POLL_MS = 2_000L
    }
}
