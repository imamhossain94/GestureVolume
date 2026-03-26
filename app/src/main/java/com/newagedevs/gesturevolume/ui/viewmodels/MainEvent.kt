package com.newagedevs.gesturevolume.ui.viewmodels

import android.content.Context

sealed class MainEvent {
    data class ToggleService(val isRunning: Boolean, val context: Context) : MainEvent()
    data class SetServiceRunning(val isRunning: Boolean) : MainEvent()
    data class SetGravity(val gravity: String) : MainEvent()
    data class SetColor(val color: Int) : MainEvent()
    data class SetClickAction(val action: String, val context: Context) : MainEvent()
    data class SetDoubleClickAction(val action: String, val context: Context) : MainEvent()
    data class SetLongClickAction(val action: String, val context: Context) : MainEvent()
    data class SetSwipeUpAction(val action: String) : MainEvent()
    data class SetSwipeDownAction(val action: String) : MainEvent()
    data class UpdatePermissionsStatus(val context: Context) : MainEvent()
    data class SyncServiceState(val context: Context) : MainEvent()
    object ShowProDialog : MainEvent()
}
