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
import com.newagedevs.gesturevolume.utils.OverlayHostMode

/**
 * Which process-level thing is drawing the bar right now, and how to reach it.
 *
 * Since 1.4.0 the overlay has two possible hosts. The foreground service is the original: it
 * needs the overlay permission and, because Android will not run one without a notification,
 * a notification. The accessibility service is the new one: while it is enabled the system keeps
 * it bound, an accessibility service may draw its own windows without the overlay permission,
 * and no notification is involved at all — which is the whole reason it exists.
 *
 * Everything that used to talk to `OverlayService` directly — the Activity's show/hide on
 * resume/pause, the notification buttons, the boot receiver, the ViewModel's toggle — now goes
 * through here, so that not one of those call sites has to know which host is up.
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

    /** True while the accessibility service is bound, whatever it is hosting. */
    val isAccessibilityConnected: Boolean get() = accessibilityService != null

    /** True while the accessibility service is the one drawing the bar. */
    val isAccessibilityHosting: Boolean get() = accessibilityService?.isHostingOverlay == true

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

    /**
     * The host that would actually be used if the overlay were started right now.
     *
     * The accessibility route needs both the preference and the bound service; with either
     * missing the notification route is used, and that route needs the overlay permission.
     */
    fun effectiveHost(context: Context, preference: SharedPref): OverlayHostMode? = when {
        preference.getOverlayHostMode() == OverlayHostMode.ACCESSIBILITY && isAccessibilityConnected ->
            OverlayHostMode.ACCESSIBILITY
        Settings.canDrawOverlays(context) -> OverlayHostMode.NOTIFICATION
        else -> null
    }

    /** True when the bar is on screen, or would be but for being hidden: either host is up. */
    fun isOverlayActive(context: Context): Boolean =
        isAccessibilityHosting || isServiceRunning(context, OverlayService::class.java)

    /**
     * Brings the overlay up on whichever host applies.
     *
     * When the accessibility service takes over from a running foreground service, the latter is
     * told to hand over — hide and stop without touching any preference — rather than stopped
     * with `stopService`, so its own teardown does not read as "the user stopped me".
     */
    fun startOverlay(context: Context, preference: SharedPref) {
        when (effectiveHost(context, preference)) {
            OverlayHostMode.ACCESSIBILITY -> {
                if (isServiceRunning(context, OverlayService::class.java)) {
                    sendToForegroundService(context, OverlayService.ACTION_HANDOVER)
                }
                accessibilityService?.hostOverlay()
            }
            OverlayHostMode.NOTIFICATION -> {
                accessibilityService?.releaseOverlay()
                val intent = Intent(context, OverlayService::class.java)
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (e: Exception) {
                    android.util.Log.e("OverlayRuntime", "startForegroundService failed", e)
                }
            }
            null -> Unit
        }
    }

    /** Takes the overlay down on both hosts. The running preference is the caller's to write. */
    fun stopOverlay(context: Context) {
        accessibilityService?.releaseOverlay()
        try {
            context.stopService(Intent(context, OverlayService::class.java))
        } catch (_: Exception) {
            // Not running.
        }
    }

    /**
     * Delivers one of the overlay commands — show, hide, user_show, user_hide, update, stop,
     * refresh_notification — to whichever host is up. Silently does nothing when neither is,
     * which is what every caller wants: the preference is the durable record, and the next
     * start reads it.
     */
    fun sendCommand(context: Context, action: String) {
        val a11y = accessibilityService
        if (a11y != null && a11y.isHostingOverlay) {
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
