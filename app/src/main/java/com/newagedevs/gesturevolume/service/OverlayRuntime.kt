package com.newagedevs.gesturevolume.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat

/**
 * How to reach the bar and the accessibility service from anywhere in the app.
 *
 * The bar has one host: the foreground service [OverlayService], which needs the overlay
 * permission and, because Android will not run one without a notification, a notification. The
 * accessibility service performs the system actions and passes the volume keys and the app in front
 * to the controller the foreground service runs, found through [activeController]. The one thing
 * it draws is a copy of the bar on the lock screen, where Android hides the foreground service's,
 * and only while the phone is locked: see [GestureAccessibilityService.refreshLockScreenBar].
 *
 * Everything that talks to the bar — the Activity's show/hide on resume/pause, the notification
 * buttons, the boot receiver, the ViewModel's toggle — goes through here.
 */
object OverlayRuntime {

    /**
     * The accessibility service, for as long as the system has it bound.
     *
     * A static reference to a Service is the documented pattern for accessibility services —
     * nothing binds to them from the app side — and it is written only from the service's own
     * connect/unbind callbacks.
     */
    @Volatile
    var accessibilityService: GestureAccessibilityService? = null

    /**
     * The controller drawing the bar, the one [OverlayService] runs.
     *
     * For the callers that have to reach it from outside that service: the accessibility service's
     * key filter, which hears the volume keys, and its window watcher, which hears which app came
     * to the front. Written only by the controller itself, as it starts and as it is torn down.
     */
    @Volatile
    var activeController: OverlayController? = null

    /**
     * Whether the user has switched the accessibility service on in system settings.
     *
     * Read from the system rather than from [accessibilityService], because the service can be
     * enabled and not yet bound — right after the user flips the switch, and for a moment after
     * boot — and the settings screens should say "on" the instant it is.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, GestureAccessibilityService::class.java)
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val fromManager = runCatching {
            manager
                ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.any { info ->
                    info.resolveInfo?.serviceInfo?.let {
                        ComponentName(it.packageName, it.name) == expected
                    } == true
                } == true
        }.getOrDefault(false)
        if (fromManager) return true
        // The manager list lags a settings flip by a beat; the secure setting is written first.
        val enabled = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        }.getOrNull() ?: return false
        return enabled.split(':').any { entry ->
            ComponentName.unflattenFromString(entry) == expected
        }
    }

    /** The system screen where the accessibility service is switched on. */
    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** Whether the bar could be started right now: the foreground service needs the overlay permission. */
    fun canHostOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** True when the bar is on screen, or would be but for being hidden: the foreground service is up. */
    fun isOverlayActive(context: Context): Boolean =
        isServiceRunning(context, OverlayService::class.java)

    /** Brings the bar up in the foreground service, when the overlay permission allows it. */
    fun startOverlay(context: Context) {
        if (!canHostOverlay(context)) return
        val intent = Intent(context, OverlayService::class.java)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            android.util.Log.e("OverlayRuntime", "startForegroundService failed", e)
        }
    }

    /** Takes the bar down. The running preference is the caller's to write. */
    fun stopOverlay(context: Context) {
        try {
            context.stopService(Intent(context, OverlayService::class.java))
        } catch (_: Exception) {
            // Not running.
        }
    }

    /**
     * Delivers one of the overlay commands — show, hide, user_show, user_hide, update, stop,
     * refresh_notification — to the foreground service. Silently does nothing when it is not
     * running, which is what every caller wants: the preference is the durable record, and the
     * next start reads it.
     */
    fun sendCommand(context: Context, action: String) {
        if (!isServiceRunning(context, OverlayService::class.java)) return
        val intent = Intent(context, OverlayService::class.java).setAction(action)
        try {
            context.startService(intent)
        } catch (_: Exception) {
            // The service went away between the check and the send.
        }
    }

    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        @Suppress("DEPRECATION")
        return try {
            manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == serviceClass.name }
        } catch (_: Exception) {
            false
        }
    }

    /** Android 9 is where the lock and screenshot global actions arrived. */
    val supportsLockAndScreenshot: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
}
