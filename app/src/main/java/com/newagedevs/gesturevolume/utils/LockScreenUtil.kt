package com.newagedevs.gesturevolume.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.newagedevs.gesturevolume.service.LockAccessibilityService

/**
 * The Lock action's one route to a locked screen.
 *
 * `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` locks the screen the way the power button does,
 * so the fingerprint sensor still unlocks it.
 *
 * **Device Admin is gone.** It was the app's original route and it still worked, but
 * `DevicePolicyManager.lockNow()` locks the device the way a security *policy* does, and most OEM
 * builds respond by demanding the PIN or password on the way back in and refusing the biometric
 * sensor. That is a fair thing for a corporate policy to do and a poor thing for a volume app to
 * do. Keeping it as a fallback meant keeping a `DeviceAdminReceiver` in the manifest — a
 * privileged component, one Play reviewers ask about, and one that makes the app harder to
 * uninstall while it is active — in exchange for a worse experience on the devices that used it.
 *
 * The cost is Android 8.0 and 8.1, where [LockAccessibilityService.isSupported] is false and there
 * is now no way to lock at all. [canLock] reports that honestly and the action says so rather than
 * appearing to be broken.
 */
class LockScreenUtil(private val context: Context) {

    /** True when the accessibility route is switched on. */
    fun accessibilityActive(): Boolean = LockAccessibilityService.isEnabled(context)

    /** True when the screen can actually be locked right now. */
    fun canLock(): Boolean = accessibilityActive()

    /** True where the Lock action is offerable at all — API 28+. */
    fun accessibilitySupported(): Boolean = LockAccessibilityService.isSupported

    /**
     * Locks the screen.
     *
     * @return false when the accessibility service is not switched on, or the release is too old
     *   to have the API, so the caller can say so rather than appearing to do nothing.
     */
    fun lockScreen(): Boolean = LockAccessibilityService.lockScreen()

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
}
