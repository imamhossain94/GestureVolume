package com.newagedevs.gesturevolume.service

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.newagedevs.gesturevolume.view.ui.main.MainActivity.Companion.DEVICE_ADMIN_REQUEST_CODE

class LockScreenUtil(private val context: Context) {

    private var devicePolicyManager = context.getSystemService(
        Activity.DEVICE_POLICY_SERVICE
    ) as DevicePolicyManager

    private var componentName: ComponentName = ComponentName(context, DeviceAdmin::class.java)


    val active: () -> Boolean = { devicePolicyManager.isAdminActive(componentName) }

    fun lockScreen() {
        if (active()) {
            devicePolicyManager.lockNow()
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
            (context as Activity).startActivityForResult(intent, DEVICE_ADMIN_REQUEST_CODE)
        }

    }

    fun disableAdmin() {
        devicePolicyManager.removeActiveAdmin(componentName)
    }

}
