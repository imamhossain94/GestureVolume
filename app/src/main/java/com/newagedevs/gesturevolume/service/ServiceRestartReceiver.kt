package com.newagedevs.gesturevolume.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.newagedevs.gesturevolume.data.local.SharedPref
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ServiceRestartReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preference: SharedPref

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == Intent.ACTION_MY_PACKAGE_REPLACED || 
            action == "com.newagedevs.gesturevolume.RESTART_SERVICE") {
            
            if (preference.isRunning()) {
                val serviceIntent = Intent(context, OverlayService::class.java).apply {
                    this.action = "show"
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    // Android 12+ can reject a background FGS start
                    // (ForegroundServiceStartNotAllowedException). Don't crash the receiver;
                    // the service will be restarted on the next app launch or boot.
                    android.util.Log.e("ServiceRestartReceiver", "Failed to (re)start service", e)
                }
            }
        }
    }
}
