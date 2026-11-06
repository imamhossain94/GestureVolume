package com.newagedevs.gesturevolume.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.newagedevs.gesturevolume.data.local.QuickSliderStore
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.livedata.LiveDataManager
import com.newagedevs.gesturevolume.utils.BarHost
import com.newagedevs.gesturevolume.utils.HandlerActions
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The accessibility service, back after its 1.3.4 retirement and doing five things, each only when
 * the user asks for it.
 *
 * **It can draw the bar**, when the user chooses it to on the Visibility screen
 * ([BarHost.ACCESSIBILITY]). Its windows are `TYPE_ACCESSIBILITY_OVERLAY`, which Android shows over
 * every screen — Settings, permission dialogs and other system screens, where it hides every app's
 * floating window, and the lock screen — and while it is enabled the system keeps it bound, so no
 * foreground service and no notification is needed. [OverlayRuntime] hands the bar to it and stops
 * [OverlayService]. While the phone is locked it puts that bar away and the lock screen gets the
 * restricted copy below, if the user asked for one. Otherwise the bar is [OverlayService]'s, with the
 * overlay permission and its notification.
 *
 * **It performs the system actions** — lock the screen, take a screenshot, Back, Home, Recents,
 * the notification shade, quick settings, the power menu. Every one of these is a
 * `performGlobalAction`, and there is no other route to any of them for an app that is not the
 * system.
 *
 * **It can catch the volume keys**, when the user sets the Quick panel to open on them instantly.
 * The platform tells an app that is not in the foreground about a volume change half a second after
 * the press; a key filter is handed the press itself. Off unless that is chosen, and even then it
 * takes the two volume keys and hands every other key straight back.
 *
 * **It can see which app is on screen**, when the user has picked apps for the bar to step aside
 * in, or given apps gestures of their own. It reads the package and class of the window that came
 * to the front and nothing inside it.
 *
 * **It can put the bar on the lock screen**, when the user asks for it there. Android draws every
 * app's overlay under the lock screen, the foreground service's bar included; only this service's
 * windows are drawn over it. So while the phone is locked, and only then, this draws a second bar
 * that does what the lock screen lets anyone do, and takes it away at the unlock. See
 * [refreshLockScreenBar].
 *
 * **It never reads what is inside a window**, this app's or any other's. The service declares
 * `canRetrieveWindowContent="false"`, so no window content is available to it at all.
 *
 * Everything it hears is passed to the controller drawing the bar, its own or the foreground
 * service's, through [OverlayRuntime.activeController].
 *
 * It is declared `isAccessibilityTool="false"`: this is a convenience feature, and the Play
 * listing carries the disclosure that says so.
 */
@AndroidEntryPoint
class GestureAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var preference: SharedPref

    // ---- drawing the bar ------------------------------------------------------------------------

    /** The bar this service draws when the user has chosen it to, while it does. */
    private var bar: OverlayController? = null

    /** True while this service, rather than the foreground service, is drawing the bar. */
    val isHostingBar: Boolean get() = bar != null

    private val barHost = object : OverlayController.Host {
        // No notification to keep in step: running without one is one reason to choose this host.
        override fun onNotificationStateChanged() = Unit
        override fun onStopRequested() = stopBarByUser()
        // Accessibility overlays need no "Display over other apps".
        override fun onOverlayPermissionMissing() = Unit
    }

    /**
     * Starts drawing the bar from this service. Idempotent. Called through
     * [OverlayRuntime.startOverlay], which takes the foreground service's bar down first.
     */
    fun hostBar() {
        if (bar != null) return
        val controller = OverlayController(
            context = this,
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            preference = preference,
            host = barHost,
        )
        bar = controller
        // Put away before it is ever drawn if the phone is locked, so it never flashes up there.
        controller.setHiddenWhileLocked(isLockedForBar())
        controller.show()
        refreshLockScreenBar()
        HandlerTileService.refresh(this)
    }

    /**
     * Stops drawing the bar from this service.
     *
     * @param restoreBrightness false when the foreground service is about to carry on: adaptive
     *   brightness this app switched off stays off until whichever host is last stops for good.
     */
    fun releaseBar(restoreBrightness: Boolean = true) {
        val controller = bar ?: return
        bar = null
        controller.destroy(restoreBrightness)
        // The lock screen's copy goes with the bar it copies.
        onBarChanged()
        HandlerTileService.refresh(this)
    }

    /** The command vocabulary [OverlayService] answers to, for [OverlayRuntime.sendCommand]. */
    fun handleCommand(action: String) {
        LiveDataManager.sendCommand(action)
        when (action) {
            "stop" -> stopBarByUser()
            // The notification's; there is none here.
            "refresh_notification" -> Unit
            else -> bar?.handleCommand(action)
        }
        // The lock screen's bar is the same bar: restyled, shown or hidden with it.
        if (action == "update" || action == "user_show" || action == "user_hide") onBarChanged()
    }

    /** The user stopped the bar from the bar itself. The service stays on; only the bar goes. */
    private fun stopBarByUser() {
        preference.setRunning(false)
        // Stopping is not hiding: the bar should be there again the next time it is started, or
        // the user would turn it on and get nothing.
        preference.setHandlerHidden(false)
        releaseBar()
    }

    /**
     * Whether this service's bar should be away for the lock screen: from the moment the screen goes
     * off, so it is never seen on the lock screen as the screen comes back on, until the unlock — or
     * until the screen comes back before the phone actually locked.
     */
    private fun isLockedForBar(): Boolean {
        val power = getSystemService(PowerManager::class.java)
        if (power != null && !power.isInteractive) return true
        val keyguard = getSystemService(KeyguardManager::class.java) ?: return false
        return keyguard.isKeyguardLocked
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        OverlayRuntime.accessibilityService = this
        applyEventSubscription()
        // Screen on and off, and the unlock: what the bar on the lock screen comes and goes with.
        // Only ever sent by the system, to receivers registered at run time. Exported for the
        // reason the controller's volume receiver is: only the system can send these, and some
        // builds do not deliver them to a receiver that is not.
        if (!screenReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_USER_PRESENT)
                },
                ContextCompat.RECEIVER_EXPORTED,
            )
            screenReceiverRegistered = true
        }
        // Chosen to draw the bar, and the user wants the bar up: take it over from the foreground
        // service, or bring it up, now the system has bound this — after boot, after an update, or
        // the moment the user switched this service on.
        if (preference.isRunning() && preference.getBarHost() == BarHost.ACCESSIBILITY) {
            OverlayRuntime.startOverlay(this, preference)
        }
        refreshLockScreenBar()
    }

    // ---- the bar on the lock screen ------------------------------------------------------------

    /** The bar this service draws on the lock screen, while there is one. */
    private var lockScreenBar: OverlayController? = null

    /**
     * The lock screen's bar can neither hide nor stop the bar, so it has nothing to tell. Its
     * windows are accessibility overlays, which need no overlay permission to go missing.
     */
    private val lockScreenHost = object : OverlayController.Host {
        override fun onNotificationStateChanged() = Unit
        override fun onStopRequested() = Unit
        override fun onOverlayPermissionMissing() = Unit
    }

    private var screenReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // This service's own bar first, then the lock screen's copy: away as the screen goes
            // off, back at the unlock.
            bar?.setHiddenWhileLocked(isLockedForBar())
            refreshLockScreenBar()
        }
    }

    /**
     * Puts the bar on the lock screen, or takes it off, for how things stand now.
     *
     * It is there while the user has asked for it ([SharedPref.getShowOnLockScreen]), the bar is
     * up — the foreground service's or this service's own — the screen is on and the phone is
     * locked. It is the same bar, read from the same settings, put up by its own [OverlayController]
     * with `lockScreen` set, so it does only what the lock screen lets anyone do. At the unlock it
     * goes, and the bar it copies is what is left: the foreground service's, which was there under
     * the lock screen all along, or this service's, which comes back from
     * [OverlayController.setHiddenWhileLocked].
     *
     * Asked again as the screen goes on and off and the phone is unlocked, as the setting changes,
     * and as the foreground service's bar starts, stops, or is changed: see [onBarChanged].
     */
    fun refreshLockScreenBar() {
        if (!wantsLockScreenBar()) {
            // Not handing back brightness: the foreground service's bar carries on.
            lockScreenBar?.destroy(restoreBrightness = false)
            lockScreenBar = null
            return
        }
        if (lockScreenBar != null) return
        lockScreenBar = OverlayController(
            context = this,
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            preference = preference,
            host = lockScreenHost,
            lockScreen = true,
        ).also { it.show() }
    }

    /**
     * The foreground service's bar was started, stopped, shown, hidden or restyled. The bar on the
     * lock screen is put up afresh, so it is the same one, or not there at all.
     */
    fun onBarChanged() {
        lockScreenBar?.destroy(restoreBrightness = false)
        lockScreenBar = null
        refreshLockScreenBar()
    }

    private fun wantsLockScreenBar(): Boolean {
        if (!preference.getShowOnLockScreen()) return false
        // A bar is up, on either host: the only controllers ever made the active one. Hidden by the
        // user, it stays hidden here too, since the controller checks that itself.
        if (OverlayRuntime.activeController == null) return false
        val power = getSystemService(PowerManager::class.java) ?: return false
        if (!power.isInteractive) return false
        val keyguard = getSystemService(KeyguardManager::class.java) ?: return false
        return keyguard.isKeyguardLocked
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        bar?.onConfigurationChanged()
        lockScreenBar?.onConfigurationChanged()
    }

    private fun releaseLockScreen() {
        lockScreenBar?.destroy(restoreBrightness = false)
        lockScreenBar = null
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            screenReceiverRegistered = false
        }
    }

    /**
     * Subscribes to exactly the events the current settings need.
     *
     * With no apps for the bar to step aside in and none with gestures of their own, that is
     * nothing at all. The XML declaration has to name the event types the service *may* use, so the
     * honest version of "not looking" is to set the live subscription to zero here rather than to
     * receive and discard.
     */
    fun applyEventSubscription() {
        val info = runCatching { serviceInfo }.getOrNull() ?: return
        var types = 0
        // Which app is in front, and only while the user has picked apps for the bar to step aside
        // in or changed a gesture for one. Nothing about the window is read but its package and
        // class.
        val watchApps = watchingApps()
        // Also, for the moment a Volume down press is waiting, whether a window of the system's own
        // comes up: see [setWatchingSystemWindows].
        val watchWindows = watchApps || watchingSystemWindows
        if (watchWindows) types = types or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.eventTypes = types
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        // A timeout keeps only the last event of each type in a burst, and an app coming forward
        // sends its window change moments before its first dialog or pane sends another. With
        // one, the app itself would be lost, so there is none while watching for the app in front.
        info.notificationTimeout = if (watchWindows) 0L else 100L
        // Keys only while the volume keys are set to Instant. With the flag on, every key press on
        // the device is offered here before anything else sees it; this takes the two volume keys
        // and hands every other straight back, but the honest version of not looking is not to
        // ask, the same as for the events above.
        val filterKeys = preference.slider.getVolumeKeyMode() == QuickSliderStore.VOLUME_KEYS_INSTANT
        info.flags = if (filterKeys) {
            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        } else {
            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        }
        runCatching { serviceInfo = info }
        // No longer watching, so the app the bar was stepping aside for no longer counts.
        if (!watchApps) OverlayRuntime.activeController?.clearForegroundApp()
    }

    /**
     * Whether a Volume down press is waiting to learn if it was half of a screenshot.
     *
     * The controller sets it from the press until it decides what the press was, a quarter of a
     * second or so, and nothing is read from the events it lets in but their package: a window of
     * the system's coming up in that moment is, on a Volume down + Power press, the screenshot.
     * See `OverlayController.onSystemWindowShown`.
     */
    private var watchingSystemWindows = false

    fun setWatchingSystemWindows(on: Boolean) {
        if (watchingSystemWindows == on) return
        watchingSystemWindows = on
        applyEventSubscription()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (watchingSystemWindows && event.packageName?.toString() == SYSTEM_UI_PACKAGE) {
            OverlayRuntime.activeController?.onSystemWindowShown()
        }
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
        if (!watchingApps()) return null
        val pkg = event.packageName?.toString() ?: return null
        val cls = event.className?.toString() ?: return null
        val isActivity = activityClasses.getOrPut("$pkg/$cls") {
            runCatching { packageManager.getActivityInfo(ComponentName(pkg, cls), 0) }.isSuccess
        }
        return if (isActivity) pkg else null
    }

    /**
     * Whether the app in front is worth knowing: some app hides the bar, or has a gesture changed
     * for it. An app listed for gestures with nothing changed yet does not count.
     */
    private fun watchingApps(): Boolean =
        preference.getHandlerHiddenApps().isNotEmpty() || preference.appGestures.hasOverrides()

    override fun onInterrupt() = Unit

    private companion object {
        /** Where the screenshot's own window comes from. */
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }

    /**
     * The two volume keys, before the system acts on them, while the Quick panel is set to open
     * on them instantly. Every other key goes straight back. See [applyEventSubscription] for when keys are asked for at all, and
     * `OverlayController.onVolumeKey` for when one is taken.
     *
     * Passed to the controller drawing the bar, on either host; with the bar not running there is
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
        val wasHosting = isHostingBar
        // Nothing will report the app in front any more, so the bar must not stay away for one.
        OverlayRuntime.activeController?.clearForegroundApp()
        // The foreground service may be about to carry on, so brightness is handed back only if
        // the bar is not wanted at all.
        releaseBar(restoreBrightness = !preference.isRunning())
        releaseLockScreen()
        if (OverlayRuntime.accessibilityService === this) OverlayRuntime.accessibilityService = null
        if (wasHosting && preference.isRunning() && Settings.canDrawOverlays(this)) {
            // Switched off in system settings while it drew the bar: "Display over other apps"
            // carries on, as OverlayRuntime.effectiveHost would choose now. Android 12+ may refuse
            // a foreground start from here; the preference still says running, so the next time
            // the app is opened its repair brings the bar back.
            try {
                ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
            } catch (e: Exception) {
                Log.e("GestureA11yService", "Fallback to the overlay failed", e)
            }
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        releaseBar(restoreBrightness = !preference.isRunning())
        releaseLockScreen()
        if (OverlayRuntime.accessibilityService === this) OverlayRuntime.accessibilityService = null
        super.onDestroy()
    }
}
