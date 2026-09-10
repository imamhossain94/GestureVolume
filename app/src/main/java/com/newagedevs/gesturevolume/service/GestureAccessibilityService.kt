package com.newagedevs.gesturevolume.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.livedata.LiveDataManager
import com.newagedevs.gesturevolume.utils.HandlerActions
import com.newagedevs.gesturevolume.utils.OverlayHostMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The accessibility service, back after its 1.3.4 retirement and doing three things.
 *
 * **It performs the system actions** — lock the screen, take a screenshot, Back, Home, Recents,
 * the notification shade, quick settings, the power menu. Every one of these is a
 * `performGlobalAction`, and there is no other route to any of them for an app that is not the
 * system.
 *
 * **It can draw the bar.** While enabled, the system keeps this service bound, it may add windows
 * of its own without the overlay permission, and no foreground service — and therefore no
 * notification — is involved. That is the "run without a notification" option on the Actions
 * screen: with it on, [OverlayRuntime] hands the overlay to this service and stops
 * [OverlayService]. With it off, this service performs actions and nothing else.
 *
 * **It can read copied text**, when the user turns clipboard capture on, by watching text
 * selection and the Copy button. Off by default; the event subscription is empty until it is
 * switched on, so the service sees nothing it has no reason to.
 *
 * It is declared `isAccessibilityTool="false"`: this is a convenience feature, and the Play
 * listing carries the disclosure that says so.
 */
@AndroidEntryPoint
class GestureAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var preference: SharedPref

    private var controller: OverlayController? = null

    /** True while this service, rather than the foreground service, is drawing the bar. */
    val isHostingOverlay: Boolean get() = controller != null

    private val host = object : OverlayController.Host {
        // No notification to keep in step: that is the point of this host.
        override fun onNotificationStateChanged() = Unit

        override fun onStopRequested() = stopByUser()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        OverlayRuntime.accessibilityService = this
        applyEventSubscription()
        if (preference.isRunning() && preference.getOverlayHostMode() == OverlayHostMode.ACCESSIBILITY) {
            OverlayRuntime.startOverlay(this, preference)
        }
    }

    /** Starts drawing the bar from this service. Idempotent. */
    fun hostOverlay() {
        if (controller != null) return
        controller = OverlayController(
            context = this,
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            preference = preference,
            host = host
        ).also { it.show() }
    }

    /**
     * Stops drawing the bar from this service.
     *
     * @param restoreBrightness false when another host is about to carry on: adaptive brightness
     *   that this app switched off stays off until whichever host is last stops for good.
     */
    fun releaseOverlay(restoreBrightness: Boolean = true) {
        controller?.destroy(restoreBrightness)
        controller = null
    }

    /** The same command vocabulary [OverlayService] answers to, for [OverlayRuntime.sendCommand]. */
    fun handleCommand(action: String) {
        LiveDataManager.sendCommand(action)
        when (action) {
            "stop" -> stopByUser()
            // Notification-only; nothing to do here.
            "refresh_notification" -> Unit
            else -> controller?.handleCommand(action)
        }
    }

    private fun stopByUser() {
        preference.setRunning(false)
        // Stopping is not hiding: the bar should be there again the next time the service is
        // started, or the user would turn it on and get nothing.
        preference.setHandlerHidden(false)
        releaseOverlay()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.onConfigurationChanged()
    }

    /**
     * Subscribes to exactly the events the current settings need.
     *
     * With clipboard capture off that is nothing at all. The XML declaration has to name the
     * event types the service *may* use, so the honest version of "not looking" is to set the
     * live subscription to zero here rather than to receive and discard.
     */
    fun applyEventSubscription() {
        val info = runCatching { serviceInfo }.getOrNull() ?: return
        info.eventTypes = if (preference.getClipboardCaptureEnabled()) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
        } else {
            0
        }
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 100
        runCatching { serviceInfo = info }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!preference.getClipboardCaptureEnabled()) return
        controller?.let { ClipboardCapture.onEvent(this, event, preference) }
            ?: ClipboardCapture.onEvent(this, event, preference)
    }

    override fun onInterrupt() = Unit

    // ---- the system actions ----------------------------------------------------------------

    /**
     * Performs one of [HandlerActions.ACCESSIBILITY_ACTIONS].
     *
     * @return false when the platform refused, or the action does not exist on this Android
     *   version — lock and screenshot arrived in Android 9.
     */
    fun performSystemAction(action: String): Boolean {
        val id = when (action) {
            HandlerActions.BACK -> GLOBAL_ACTION_BACK
            HandlerActions.HOME -> GLOBAL_ACTION_HOME
            HandlerActions.RECENTS -> GLOBAL_ACTION_RECENTS
            HandlerActions.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
            HandlerActions.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
            HandlerActions.POWER_MENU -> GLOBAL_ACTION_POWER_DIALOG
            HandlerActions.LOCK ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_LOCK_SCREEN else return false
            HandlerActions.SCREENSHOT ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_TAKE_SCREENSHOT else return false
            else -> return false
        }
        return runCatching { performGlobalAction(id) }.getOrDefault(false)
    }

    /**
     * Pastes [text] into whatever field has input focus, in whichever app is in front.
     *
     * Two attempts. `ACTION_PASTE` on the focused node is what the platform's own Paste does and
     * keeps the field's undo history intact; when a field refuses it, the text is set outright
     * with the clipboard's content appended to what was there. Needs `canRetrieveWindowContent`,
     * which the service declares for exactly this and the capture above.
     *
     * @return false when nothing has focus, which is the usual reason: the Deck was open, and the
     *   caller should close it and try again once focus has returned to the app underneath.
     */
    fun pasteIntoFocusedField(text: CharSequence): Boolean {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return false
        val focused = runCatching {
            root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
        }.getOrNull() ?: return false
        if (!focused.isEditable) return false
        val pasted = runCatching {
            focused.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_PASTE)
        }.getOrDefault(false)
        if (pasted) return true
        val existing = focused.text?.toString().orEmpty()
        val args = android.os.Bundle().apply {
            putCharSequence(
                android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                existing + text
            )
        }
        return runCatching {
            focused.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }.getOrDefault(false)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        val wasHosting = isHostingOverlay
        // Another host is about to carry on, or nothing is; either way the brightness hand-back
        // belongs to the one that stops for good.
        releaseOverlay(restoreBrightness = !preference.isRunning())
        OverlayRuntime.accessibilityService = null
        if (wasHosting && preference.isRunning()) {
            // The user switched the service off in system settings while it was drawing the bar.
            // Fall back to the notification route. Android 12+ may refuse a foreground start from
            // here; the preference still says running, so the next app launch repairs it.
            try {
                ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
            } catch (e: Exception) {
                android.util.Log.e("GestureA11yService", "fallback start failed", e)
            }
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        releaseOverlay(restoreBrightness = !preference.isRunning())
        if (OverlayRuntime.accessibilityService === this) OverlayRuntime.accessibilityService = null
        super.onDestroy()
    }
}
