package com.newagedevs.gesturevolume.utils

/**
 * What draws the bar, as the user chose it on the Visibility screen.
 *
 * A persistence format like [HandlerActions]: [stored] is written to SharedPreferences by
 * `SharedPref.setBarHost` and must not be renamed. It is a new key with new values rather than
 * the `overlayHostMode` of the short-lived 1.4.0 test builds, so a device that still carries that
 * old answer is not switched to the accessibility service without being asked again.
 */
enum class BarHost(val stored: String) {
    /**
     * "Display over other apps": the foreground service, with the overlay permission and the
     * notification Android requires of a foreground service. Android hides these windows on the
     * lock screen, in Settings, and over some system screens. The default.
     */
    OVERLAY("overlay"),

    /**
     * The accessibility service draws the bar itself, as `TYPE_ACCESSIBILITY_OVERLAY`, which Android
     * shows over every screen: Settings, permission dialogs and other system screens, and the lock
     * screen. It needs no overlay permission and no notification, and only the accessibility service.
     */
    ACCESSIBILITY("accessibility");

    companion object {
        fun fromStored(value: String?): BarHost = entries.firstOrNull { it.stored == value } ?: OVERLAY

        /**
         * The host that would draw the bar right now, given the user's [choice] and what is granted.
         *
         * The accessibility service once it is switched on, whether or not the system has bound it
         * yet: it brings the bar up itself as it connects. Without it, the overlay permission, so a
         * user who chose the accessibility service and later turned it off still has a bar. Null
         * when neither can draw.
         *
         * Pure, so the routing can be tested on the JVM: `OverlayRuntime.effectiveHost` reads the
         * three inputs from the system.
         */
        fun effective(choice: BarHost, accessibilityEnabled: Boolean, canDrawOverlays: Boolean): BarHost? =
            when {
                choice == ACCESSIBILITY && accessibilityEnabled -> ACCESSIBILITY
                canDrawOverlays -> OVERLAY
                else -> null
            }
    }
}
