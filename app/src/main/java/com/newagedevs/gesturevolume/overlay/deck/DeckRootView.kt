package com.newagedevs.gesturevolume.overlay.deck

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * The window's root for the Deck: a plain frame that answers the Back key.
 *
 * The Deck's window is focusable — its text fields need the keyboard — so the Back key comes to
 * it rather than to the app underneath. Handled here, at the root, so it works whichever card is
 * open and whether or not anything inside has focus.
 */
class DeckRootView(
    context: Context,
    private val onBack: () -> Unit,
    private val onInteraction: () -> Unit
) : FrameLayout(context) {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
        onInteraction()
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) onInteraction()
        return super.dispatchTouchEvent(ev)
    }
}
