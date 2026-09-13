package com.newagedevs.gesturevolume.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.newagedevs.gesturevolume.data.local.QuickSliderStore
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.utils.HandlerActions
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The accessibility service, back after its 1.3.4 retirement and doing three things. It never
 * draws the bar: that is always [OverlayService], with the overlay permission and its notification.
 *
 * **It performs the system actions** — lock the screen, take a screenshot, Back, Home, Recents,
 * the notification shade, quick settings, the power menu. Every one of these is a
 * `performGlobalAction`, and there is no other route to any of them for an app that is not the
 * system.
 *
 * **It can catch the volume keys**, when the user sets the Quick panel to open on them instantly,
 * or turns on Hide in screenshots. The platform tells an app that is not in the foreground about a
 * volume change half a second after the press; a key filter is handed the press itself. Off unless
 * one of those settings is chosen, and even then it takes the two volume keys and hands every other
 * key straight back.
 *
 * **It can see which app is on screen**, when the user has picked apps for the bar to step aside
 * in. It reads the package and class of the window that came to the front and nothing inside it.
 *
 * **It never reads what is inside a window**, this app's or any other's. The service declares
 * `canRetrieveWindowContent="false"`, so no window content is available to it at all.
 *
 * Everything it hears is passed to the controller the foreground service is running, through
 * [OverlayRuntime.activeController].
 *
 * It is declared `isAccessibilityTool="false"`: this is a convenience feature, and the Play
 * listing carries the disclosure that says so.
 */
@AndroidEntryPoint
class GestureAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var preference: SharedPref

    override fun onServiceConnected() {
        super.onServiceConnected()
        OverlayRuntime.accessibilityService = this
        applyEventSubscription()
    }

    /**
     * Subscribes to exactly the events the current settings need.
     *
     * With no apps for the bar to step aside in, that is nothing at all. The XML declaration has
     * to name the event types the service *may* use, so the honest version of "not looking" is to
     * set the live subscription to zero here rather than to receive and discard.
     */
    fun applyEventSubscription() {
        val info = runCatching { serviceInfo }.getOrNull() ?: return
        var types = 0
        // Which app is in front, and only while the user has picked apps for the bar to step aside
        // in. Nothing about the window is read but its package and class.
        val watchApps = preference.getHandlerHiddenApps().isNotEmpty()
        if (watchApps) types = types or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.eventTypes = types
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        // A timeout keeps only the last event of each type in a burst, and an app coming forward
        // sends its window change moments before its first dialog or pane sends another. With
        // one, the app itself would be lost, so there is none while watching for the app in front.
        info.notificationTimeout = if (watchApps) 0L else 100L
        // Keys only while the volume keys are set to Instant, or the bar is set to step out of
        // screenshots, which it does on the Volume down half of the chord. With the flag on, every
        // key press on the device is offered here before anything else sees it; this takes the two
        // volume keys and hands every other straight back, but the honest version of not looking
        // is not to ask, the same as for the events above.
        val filterKeys =
            preference.slider.getVolumeKeyMode() == QuickSliderStore.VOLUME_KEYS_INSTANT ||
                preference.getHideInScreenshots()
        info.flags = if (filterKeys) {
            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        } else {
            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        }
        runCatching { serviceInfo = info }
        // No longer watching, so the app the bar was stepping aside for no longer counts.
        if (!watchApps) OverlayRuntime.activeController?.clearForegroundApp()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        foregroundAppOf(event)?.let { app ->
            OverlayRuntime.activeController?.onForegroundApp(app)
        }
    }

    /** Whether each window class seen is an activity, remembered so each is asked about once. */
    private val activityClasses = HashMap<String, Boolean>()

    /**
     * The app a window change brought to the front, or null when it was not an app coming forward.
     *
     * Only an activity window counts. Dialogs, the keyboard, the notification shade and toasts all
     * raise the same event, and reading any of them as a change of app would bring the bar back
     * over an app the user is still in.
     */
    private fun foregroundAppOf(event: AccessibilityEvent): String? {
        if (preference.getHandlerHiddenApps().isEmpty()) return null
        val pkg = event.packageName?.toString() ?: return null
        val cls = event.className?.toString() ?: return null
        val isActivity = activityClasses.getOrPut("$pkg/$cls") {
            runCatching { packageManager.getActivityInfo(ComponentName(pkg, cls), 0) }.isSuccess
        }
        return if (isActivity) pkg else null
    }

    override fun onInterrupt() = Unit

    /**
     * The two volume keys, before the system acts on them, while the Quick panel is set to open
     * on them instantly or the bar is set to stay out of screenshots. Every other key goes straight
     * back. See [applyEventSubscription] for when keys are asked for at all, and
     * `OverlayController.onVolumeKey` for when one is taken.
     *
     * Passed to the controller the foreground service is running; with the bar not running there is
     * none, and the key goes on to the system untouched.
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        event ?: return false
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
        ) return false
        val target = OverlayRuntime.activeController ?: return false
        return runCatching { target.onVolumeKey(event) }.getOrDefault(false)
    }

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

    override fun onUnbind(intent: Intent?): Boolean {
        // Nothing will report the app in front any more, so the bar must not stay away for one.
        OverlayRuntime.activeController?.clearForegroundApp()
        if (OverlayRuntime.accessibilityService === this) OverlayRuntime.accessibilityService = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (OverlayRuntime.accessibilityService === this) OverlayRuntime.accessibilityService = null
        super.onDestroy()
    }
}
