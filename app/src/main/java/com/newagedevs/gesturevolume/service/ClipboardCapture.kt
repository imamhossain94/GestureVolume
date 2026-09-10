package com.newagedevs.gesturevolume.service

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.newagedevs.gesturevolume.data.local.SharedPref

/**
 * Turns accessibility events into clipboard-history entries, when the user has asked for that.
 *
 * Android 10 closed the clipboard to apps that do not have focus, so a background service cannot
 * simply listen for changes. What it can do, with the user's leave, is notice the same two things
 * the user does when they copy: a piece of text is selected, and then Copy is pressed. The last
 * selection seen before a Copy press is what was copied.
 *
 * Only the selected substring is ever kept, never the field around it, and only once Copy has
 * actually been pressed — a selection on its own is not a copy and is forgotten on the next one.
 */
object ClipboardCapture {

    private var lastSelection: String? = null

    fun onEvent(context: Context, event: AccessibilityEvent, preference: SharedPref) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> rememberSelection(event)
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (isCopyPress(context, event)) {
                    val text = lastSelection?.takeIf { it.isNotBlank() } ?: return
                    lastSelection = null
                    preference.addClipboardEntry(text)
                }
            }
        }
    }

    private fun rememberSelection(event: AccessibilityEvent) {
        val start = event.fromIndex
        val end = event.toIndex
        if (start < 0 || end < 0 || start == end) return
        val text = event.text?.firstOrNull()?.toString() ?: return
        val from = minOf(start, end).coerceIn(0, text.length)
        val to = maxOf(start, end).coerceIn(0, text.length)
        if (to > from) lastSelection = text.substring(from, to)
    }

    /**
     * Whether a click was the Copy button of a text-selection toolbar.
     *
     * Matched by the platform's own label for it, in the device's language, so it works on any
     * app that uses the system toolbar. Apps with their own toolbars are not covered; the Deck's
     * own Copy buttons write the history directly and need none of this.
     */
    private fun isCopyPress(context: Context, event: AccessibilityEvent): Boolean {
        val labels = buildList {
            add(context.getString(android.R.string.copy))
            event.contentDescription?.let { add(it.toString()) }
        }
        val texts = event.text.map { it.toString() } + (event.contentDescription?.toString() ?: "")
        val copyLabel = context.getString(android.R.string.copy)
        return texts.any { it.equals(copyLabel, ignoreCase = true) } ||
            labels.any { l -> texts.any { it.equals(l, ignoreCase = true) } }
    }
}
