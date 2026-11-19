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
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.utils.BarHost

/**
 * What is drawing the bar, and how to reach it and the accessibility service from anywhere in the
 * app.
 *
 * The bar has two possible hosts, and the user picks one on the Visibility screen ([BarHost]):
 *
 *  - **Display over other apps**, the default: the foreground service [OverlayService], which needs
 *    the overlay permission and, because Android will not run one without it, a notification.
 *    Android hides its windows on the lock screen, in Settings and over some system screens, so
 *    there the accessibility service, when it is on, draws a copy of the bar on the lock screen
 *    only: see [GestureAccessibilityService.refreshLockScreenBar].
 *  - **The accessibility service**, [GestureAccessibilityService], which draws the bar itself as
 *    `TYPE_ACCESSIBILITY_OVERLAY`. Android shows those over every screen, Settings and the lock
 *    screen included, and no permission but the service's and no notification is involved. While
 *    the phone is locked it puts its bar away and shows the lock screen's restricted copy instead,
 *    if that is switched on.
 *
 * Only one draws at a time. Starting the bar on one host takes it down on the other, and the
 * accessibility choice falls back to the overlay while the service is off.
 *
 * Everything that talks to the bar — the Activity's show/hide on resume/pause, the notification
 * buttons, the boot receiver, the ViewModel's toggle, the Quick Settings tile — goes through here,
 * so none of them has to know which host is up.
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
     * The controller drawing the bar, whichever host it belongs to.
     *
     * For the callers that have to reach it from outside the host: the accessibility service's key
     * filter, which hears the volume keys, and its window watcher, which hears which app came to the
     * front. Written only by the controller itself, as it starts and as it is torn down. The lock
     * screen's copy never becomes it.
     */
    @Volatile
    var activeController: OverlayController? = null

    /** True while the accessibility service is the one drawing the bar. */
    val isAccessibilityHosting: Boolean get() = accessibilityService?.isHostingBar == true

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

    /** The host that would draw the bar if it were started right now, or null if neither can. */
    fun effectiveHost(context: Context, preference: SharedPref): BarHost? =
        BarHost.effective(
            choice = preference.getBarHost(),
            accessibilityEnabled = isAccessibilityEnabled(context),
            canDrawOverlays = Settings.canDrawOverlays(context),
        )

    /** Whether the bar could be started right now, on the host the user chose or its fallback. */
    fun canHostOverlay(context: Context, preference: SharedPref): Boolean =
        effectiveHost(context, preference) != null

    /** True when the bar is on screen, or would be but for being hidden: either host is up. */
    fun isOverlayActive(context: Context): Boolean =
        isAccessibilityHosting || isServiceRunning(context, OverlayService::class.java)

    /**
     * Brings the bar up on whichever host applies, and takes it down on the other.
     *
     * A running foreground service is told to hand over — hide and stop without touching any
     * preference — rather than stopped with `stopService`, so its teardown does not read as the
     * user switching the bar off. When the accessibility service is switched on but not bound yet
     * (just after boot, or just after the user turned it on) this does nothing for it: it brings
     * the bar up itself as it connects.
     */
    fun startOverlay(context: Context, preference: SharedPref) {
        when (effectiveHost(context, preference)) {
            BarHost.ACCESSIBILITY -> {
                if (isServiceRunning(context, OverlayService::class.java)) {
                    sendToForegroundService(context, OverlayService.ACTION_HANDOVER)
                }
                accessibilityService?.hostBar()
            }
            BarHost.OVERLAY -> {
                // Not handing back brightness: the foreground service is about to carry on.
                accessibilityService?.releaseBar(restoreBrightness = false)
                try {
                    ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java))
                } catch (e: Exception) {
                    android.util.Log.e("OverlayRuntime", "startForegroundService failed", e)
                }
            }
            null -> Unit
        }
    }

    /** Takes the bar down on both hosts. The running preference is the caller's to write. */
    fun stopOverlay(context: Context) {
        accessibilityService?.releaseBar()
        try {
            context.stopService(Intent(context, OverlayService::class.java))
        } catch (_: Exception) {
            // Not running.
        }
    }

    /**
     * Delivers one of the bar's commands — show, hide, user_show, user_hide, update, stop,
     * refresh_notification — to whichever host is up. Silently does nothing when neither is, which
     * is what every caller wants: the preference is the durable record, and the next start reads
     * it.
     */
    fun sendCommand(context: Context, action: String) {
        val a11y = accessibilityService
        if (a11y != null && a11y.isHostingBar) {
            a11y.handleCommand(action)
            return
        }
        if (!isServiceRunning(context, OverlayService::class.java)) return
        sendToForegroundService(context, action)
    }

    private fun sendToForegroundService(context: Context, action: String) {
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
