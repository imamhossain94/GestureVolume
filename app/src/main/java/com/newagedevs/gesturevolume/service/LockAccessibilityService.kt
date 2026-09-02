package com.newagedevs.gesturevolume.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent

/**
 * The lock-screen route that does not cost the user their fingerprint.
 *
 * `DevicePolicyManager.lockNow()` — the Device Admin route this app shipped with — locks the
 * device the way a security policy does, and most OEM builds respond by demanding the PIN or
 * password on the way back in and refusing the biometric sensor. That is a fair thing for a
 * corporate policy to do and a poor thing for a volume app to do, which is why every well-behaved
 * "lock screen" shortcut on Play uses this API instead: [GLOBAL_ACTION_LOCK_SCREEN] locks the
 * screen exactly as the power button does, so the fingerprint reader still works.
 *
 * It performs no gestures, retrieves no window content and subscribes to no events — the flags in
 * `res/xml/lock_accessibility_service.xml` say so, and [onAccessibilityEvent] is deliberately
 * empty. The service exists solely to be *connected*, because `performGlobalAction` is only
 * available to a connected accessibility service.
 *
 * Play requires an accessibility declaration in the Console for any app that ships one of these.
 */
class LockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        /**
         * The connected instance, or null.
         *
         * `@Volatile` because it is written on the accessibility service's own connection callback
         * and read from the overlay service.
         */
        @Volatile
        private var instance: LockAccessibilityService? = null

        /** API 28 is where [GLOBAL_ACTION_LOCK_SCREEN] arrives. Below it, Device Admin is the only way. */
        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        /**
         * Whether the user has switched this service on, read from Settings rather than from
         * [instance].
         *
         * The two disagree for a moment after the user grants it — the system takes its time
         * binding — and for a UI that has to say "granted" the moment the user comes back from the
         * accessibility screen, Settings is the honest answer.
         */
        fun isEnabled(context: Context): Boolean {
            if (!isSupported) return false
            val expected = ComponentName(context, LockAccessibilityService::class.java)
            val enabled = try {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            } catch (_: Exception) {
                null
            } ?: return false

            // A colon-separated list of flattened component names. Split rather than `contains`:
            // one package's name can be a substring of another's.
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            for (entry in splitter) {
                val component = ComponentName.unflattenFromString(entry) ?: continue
                if (component == expected) return true
            }
            return false
        }

        /**
         * Locks the screen.
         *
         * @return false when this route is unavailable — too old a release, not switched on, or
         *   not yet bound — so the caller can fall back to Device Admin.
         */
        fun lockScreen(): Boolean {
            if (!isSupported) return false
            val service = instance ?: return false
            return try {
                service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            } catch (_: Exception) {
                false
            }
        }
    }
}
