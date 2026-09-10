package com.newagedevs.gesturevolume.utils

/**
 * Which process draws the floating bar.
 *
 * A persistence format like [HandlerActions]: the names are written to SharedPreferences by
 * [com.newagedevs.gesturevolume.data.local.SharedPref.setOverlayHostMode] and must not be renamed.
 */
enum class OverlayHostMode {
    /** The foreground service, with the ongoing notification Android requires of one. */
    NOTIFICATION,

    /**
     * The accessibility service, which the system keeps alive for as long as it is enabled and
     * which may draw over other apps without the overlay permission — and without a notification.
     */
    ACCESSIBILITY;

    companion object {
        fun fromStored(value: String?): OverlayHostMode =
            entries.firstOrNull { it.name == value } ?: NOTIFICATION
    }
}
