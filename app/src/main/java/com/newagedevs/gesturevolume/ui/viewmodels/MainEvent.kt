package com.newagedevs.gesturevolume.ui.viewmodels

import android.content.Context

sealed class MainEvent {
    data class ToggleService(val isRunning: Boolean, val context: Context) : MainEvent()
    data class SetClickAction(val action: String, val context: Context) : MainEvent()
    data class SetDoubleClickAction(val action: String, val context: Context) : MainEvent()
    data class SetLongClickAction(val action: String, val context: Context) : MainEvent()
    // Context is needed because the brightness swipe actions are gated on WRITE_SETTINGS.
    data class SetSwipeUpAction(val action: String, val context: Context) : MainEvent()
    data class SetSwipeDownAction(val action: String, val context: Context) : MainEvent()
    data class UpdatePermissionsStatus(val context: Context) : MainEvent()
    data class SyncServiceState(val context: Context) : MainEvent()
    object ShowProDialog : MainEvent()

    /** The user came back from the "Modify system settings" screen. */
    data class WriteSettingsResult(val context: Context) : MainEvent()

    /** The user dismissed the brightness-permission rationale without granting. */
    object CancelPendingBrightnessAction : MainEvent()

    /** Show or hide the bar deliberately, from the app rather than from the notification. */
    data class SetHandlerHidden(val hidden: Boolean, val context: Context) : MainEvent()

    /** Wipe every setting back to factory defaults. Confirmed in the UI before it gets here. */
    data class ResetAllSettings(val context: Context) : MainEvent()
}
