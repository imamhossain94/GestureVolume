package com.newagedevs.gesturevolume.ui.viewmodels

import com.newagedevs.gesturevolume.R

data class MainState(
    val isRunning: Boolean = false,
    val isProActivated: Boolean = false,
    val color: Int = 0,
    val clickAction: String = "Open volume UI",
    val clickActionIcon: Int = R.drawable.ic_vol_increase,
    val doubleClickAction: String = "Mute",
    val doubleClickActionIcon: Int = R.drawable.ic_mute,
    val longClickAction: String = "Active Music Overlay",
    val longClickActionIcon: Int = R.drawable.ic_music_ui,
    val swipeUpAction: String = "Increase volume and show UI",
    val swipeUpActionIcon: Int = R.drawable.ic_vol_increase,
    val swipeDownAction: String = "Decrease volume and show UI",
    val swipeDownActionIcon: Int = R.drawable.ic_vol_increase,
    val hasOverlayPermission: Boolean = false,
    val hasWriteSettingsPermission: Boolean = false,
    /**
     * True while a brightness action is waiting on WRITE_SETTINGS.
     *
     * Carried in state rather than through [MainEffect], because the effect channel has a single
     * consumer — a second screen collecting it would swallow toasts intended for the first.
     */
    val pendingWriteSettingsRequest: Boolean = false,
    /** True while the Lock action is waiting on a lock route the user has not granted yet. */
    val pendingLockPermissionRequest: Boolean = false,
    val theme: Int = 0,
    val language: String = "en"
)




