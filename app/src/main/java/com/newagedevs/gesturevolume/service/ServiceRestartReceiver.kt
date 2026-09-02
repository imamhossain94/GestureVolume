package com.newagedevs.gesturevolume.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.newagedevs.gesturevolume.data.local.SharedPref
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Brings the overlay back after events that tear it down but leave the user still wanting it:
 * a reboot, and an app update.
 *
 * Note there is deliberately no alarm-driven restart here any more. The previous version rearmed
 * itself from `OverlayService.onDestroy()` through an *inexact* AlarmManager alarm, which cannot
 * work on Android 12+: only an exact alarm earns the exemption that lets a background broadcast
 * start a foreground service, and an app like this does not qualify for the exact-alarm permission
 * under Play policy. That path was also unreachable in practice. Recovery now comes from
 * `START_STICKY`, `onTaskRemoved`, `android:stopWithTask="false"`, and the battery-optimisation
 * exemption offered on the Troubleshoot screen.
 */
@AndroidEntryPoint
class ServiceRestartReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preference: SharedPref

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> Unit
            else -> return
        }

        if (!preference.isRunning()) return

        // "show" is the transient command, which respects a bar the user has put away with
        // "Hide handler" - a reboot is not them asking for it back. See OverlayService.
        val serviceIntent = Intent(context, OverlayService::class.java).apply {
            action = "show"
        }

        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            // Android 12+ can reject a background FGS start
            // (ForegroundServiceStartNotAllowedException). Don't crash the receiver;
            // the service is restarted on the next app launch instead.
            android.util.Log.e("ServiceRestartReceiver", "Failed to (re)start service", e)
        }
    }
}
