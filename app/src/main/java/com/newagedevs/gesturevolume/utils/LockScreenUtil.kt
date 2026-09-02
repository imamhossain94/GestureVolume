package com.newagedevs.gesturevolume.utils

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.newagedevs.gesturevolume.service.DeviceAdmin
import com.newagedevs.gesturevolume.service.LockAccessibilityService
import com.newagedevs.gesturevolume.ui.activities.MainActivity

/**
 * The Lock action's two routes to a locked screen, in the order they should be preferred.
 *
 * **Accessibility first.** `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` locks the screen the
 * way the power button does, so the fingerprint sensor still unlocks it. Device Admin's
 * `lockNow()` locks it the way a security policy does, and most OEM builds then insist on the PIN
 * — which is the complaint this ordering answers.
 *
 * **Device Admin second**, unchanged, because it is what every existing install has already
 * granted and it is the only route below API 28. Nobody who has the app working today loses it.
 */
class LockScreenUtil(private val context: Context) {

    private var devicePolicyManager = context.getSystemService(
        Activity.DEVICE_POLICY_SERVICE
    ) as DevicePolicyManager

    private var componentName: ComponentName = ComponentName(context, DeviceAdmin::class.java)

    /** True when Device Admin is granted. Kept as a property for the call sites that had it. */
    val active: () -> Boolean = { devicePolicyManager.isAdminActive(componentName) }

    /** True when the accessibility route is switched on. */
    fun accessibilityActive(): Boolean = LockAccessibilityService.isEnabled(context)

    /** True when *either* route can lock the screen, which is all a caller needs to know. */
    fun canLock(): Boolean = accessibilityActive() || active()

    /** True when the accessibility route is offerable at all — API 28+. */
    fun accessibilitySupported(): Boolean = LockAccessibilityService.isSupported

    /**
     * Locks the screen by whichever route is available, preferring the biometric-friendly one.
     *
     * @return false when neither route is granted, so the caller can say so rather than appearing
     *   to do nothing.
     */
    fun lockScreen(): Boolean {
        if (LockAccessibilityService.lockScreen()) return true
        if (active()) {
            devicePolicyManager.lockNow()
            return true
        }
        return false
    }

    /** Sends the user to the system accessibility list, where they switch the service on. */
    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // No accessibility settings screen on this build; nothing sensible to fall back to.
        }
    }

    fun enableAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Screen lock requires administrator permissions."
            )
        }

        if (!devicePolicyManager.isAdminActive(componentName)) {
            (context as? Activity)?.startActivityForResult(
                intent,
                MainActivity.Companion.DEVICE_ADMIN_REQUEST_CODE
            )
        }
    }

    fun disableAdmin() {
        devicePolicyManager.removeActiveAdmin(componentName)
    }
}
