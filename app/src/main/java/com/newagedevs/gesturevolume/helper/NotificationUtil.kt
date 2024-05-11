package com.newagedevs.gesturevolume.helper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

class NotificationUtil(context: Context) {

    val isPermissionRequired: () -> Boolean = {
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    }

    val isPermissionGranted: () -> Boolean = {
        if(isPermissionRequired()) (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        else true
    }

    fun requestPermission(launcher: ActivityResultLauncher<String>) {
        if (isPermissionRequired() && !isPermissionGranted()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }


}