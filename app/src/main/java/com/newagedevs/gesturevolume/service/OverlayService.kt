package com.newagedevs.gesturevolume.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.os.Binder
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.livedata.LiveDataManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

interface OverlayServiceInterface {
    fun show()
    fun hide()
    fun update()
    var shouldFinish: Boolean
}

/**
 * The foreground-service host for the bar.
 *
 * Since 1.4.0 everything about the overlay itself — the handler window, its gestures, the
 * long-press menu, the Deck, the indicator — lives in [OverlayController], which this service
 * and [GestureAccessibilityService] share. What is left here is what only a foreground service
 * has: the notification Android requires of one, its channels and buttons, and the service
 * lifecycle that keeps the bar alive across task removal and system restarts.
 */
@AndroidEntryPoint
class OverlayService : Service(), OverlayServiceInterface {

    @Inject
    lateinit var preference: SharedPref

    /**
     * Whether a teardown is the user's doing.
     *
     * `true` means the user explicitly stopped the service and it must stay stopped. `false` — the
     * default — means any teardown was the system's doing and the service should come back.
     *
     * This used to be initialised to `true` and never assigned `false` anywhere, which made the
     * restart path in [onDestroy] permanently unreachable: the app could never recover from being
     * killed, which is what users reported as "the OS keeps killing the app".
     */
    override var shouldFinish: Boolean = false

    private val binder: IBinder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun instance(): OverlayServiceInterface = this@OverlayService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private lateinit var controller: OverlayController

    /**
     * True from a handover to the accessibility service until this instance dies. The controller
     * has been torn down without restoring anything, because the other host carries on.
     */
    private var handedOver = false

    companion object {
        // v2 channel: low importance (silent, no heads-up). Bumped from the old id so existing
        // installs also move off the intrusive IMPORTANCE_HIGH channel.
        private const val CHANNEL_ID = "gesture_volume_service_v2"

        /**
         * The channel used when the user has switched the notification off.
         *
         * A foreground service must post *something* — there is no API that lets one run without a
         * notification — so "off" is served by moving it to its own IMPORTANCE_MIN channel instead.
         * At minimum importance the system drops the status-bar icon and files the row at the
         * bottom of the shade, which is as close to absent as a foreground service is allowed to
         * get. Separate from [CHANNEL_ID] rather than a mutated copy of it: a channel's importance
         * is fixed once created, so one channel cannot be both.
         */
        private const val CHANNEL_MINIMAL_ID = "gesture_volume_service_min"
        private const val LEGACY_CHANNEL_ID = "Gesture Volume Channel ID"
        private const val NOTIFICATION_ID = 1

        /**
         * "Hide, and stop, and touch no preference": the accessibility service is taking over
         * the bar. Distinct from "stop", which is the user switching the whole thing off.
         */
        const val ACTION_HANDOVER = "handover"
    }

    private val controllerHost = object : OverlayController.Host {
        override fun onNotificationStateChanged() = startForegroundService()
        override fun onStopRequested() = stopServiceEntirely()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundService()
        controller = OverlayController(
            context = this,
            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            preference = preference,
            host = controllerHost
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller.onConfigurationChanged()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // Silent, low-importance channel: the notification is required to keep the foreground
        // service alive, but it should sit quietly in the bar without sound or heads-up.
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_controls),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_controls_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)

        val minimal = NotificationChannel(
            CHANNEL_MINIMAL_ID,
            getString(R.string.notification_channel_minimal),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = getString(R.string.notification_channel_minimal_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(minimal)

        // Remove the old intrusive channel from app notification settings.
        try {
            manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        } catch (_: Exception) {
            // Channel may not exist; ignore.
        }
    }

    private fun startForegroundService() {
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Starting the foreground service can be rejected by the OS when launched from the
            // background on Android 12+ (ForegroundServiceStartNotAllowedException). Fail quietly
            // instead of crashing — the service will be retried via START_STICKY / boot receiver.
            android.util.Log.e("OverlayService", "startForeground failed", e)
        }
    }

    /**
     * The ongoing notification, in whichever of its two forms the user has asked for.
     *
     * **On** — the default — it is the app's remote control: Show/Hide, Settings and Stop, none of
     * which need the app opened. That matters more than it sounds, because a bar put away with
     * "Hide handler" stays away until something explicitly shows it, and while the app is in the
     * foreground the bar is hidden anyway. Without this row the only route back would be the
     * Handler-hidden card on the main screen.
     *
     * **Off** — the same service, re-posted on [CHANNEL_MINIMAL_ID] with no buttons and no detail.
     * Android has no way to run a foreground service with no notification at all, so this is the
     * honest version of "hide it": minimum importance, which costs it the status-bar icon and puts
     * it at the bottom of the shade. The user can finish the job from the channel's own system
     * settings, which the Permissions screen links to — or switch to the accessibility host,
     * which needs no notification at all.
     */
    private fun buildNotification(): Notification {
        val hidden = preference.isHandlerHidden()

        if (!preference.getShowNotification()) {
            return NotificationCompat.Builder(this, CHANNEL_MINIMAL_ID)
                .setSmallIcon(R.drawable.ic_gesture)
                .setContentTitle(getString(R.string.notification_minimal_title))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .setAutoCancel(false)
                .setOngoing(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setContentIntent(getOpenAppIntent())
                .build()
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gesture)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(
                getString(
                    if (hidden) R.string.notification_handler_hidden
                    else R.string.notification_service_active
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(false)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(getOpenAppIntent())
            // Show or Hide, never both: they are the same button in two states, and a notification
            // offering the one that is already true wastes a third of the row.
            .addAction(
                if (hidden) R.drawable.ic_show else R.drawable.ic_visibility_hide,
                getString(if (hidden) R.string.notification_action_show else R.string.notification_action_hide),
                getServiceIntent(if (hidden) "user_show" else "user_hide")
            )
            .addAction(
                R.drawable.ic_app_open,
                getString(R.string.notification_action_settings),
                getOpenAppIntent()
            )
            .addAction(
                R.drawable.ic_x_close,
                getString(R.string.notification_action_stop),
                getServiceIntent("stop")
            )
            .build()
    }

    /**
     * A command to this service, as a pending intent for a notification button.
     *
     * The request code is derived from the action so that Show and Stop get separate
     * PendingIntents. With a shared request code, `FLAG_UPDATE_CURRENT` makes the second one
     * overwrite the first's extras and both buttons run whichever action was built last.
     */
    private fun getServiceIntent(action: String): PendingIntent {
        val intent = Intent(this, OverlayService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * Opens the app from the notification — both from its body and from the Settings button.
     *
     * IMMUTABLE: nothing fills anything in.
     */
    private fun getOpenAppIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ?: Intent()
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // After a handover the other host owns the brightness hand-back; after a stop, this
        // instance does. Between the two — a system kill — restoring is the safe choice, and the
        // restarted instance takes brightness back over on the next swipe.
        if (::controller.isInitialized) controller.destroy(restoreBrightness = !handedOver)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * The single largest cause of "the system killed my app": the user swipes the task out of
     * Recents, and many OEM builds tear the service down with it.
     *
     * The manifest pairs this with `android:stopWithTask="false"`, so on stock Android the service
     * survives outright; this restart is the recovery path for the builds that ignore that.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (shouldFinish || !preference.isRunning()) return
        try {
            val restart = Intent(applicationContext, OverlayService::class.java).apply {
                action = "show"
            }
            ContextCompat.startForegroundService(applicationContext, restart)
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "restart after task removal failed", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            LiveDataManager.sendCommand(action)
            when (action) {
                "stop" -> stopServiceEntirely()
                ACTION_HANDOVER -> handOver()
                // Notification-only. "update" tears the handler window down and rebuilds it, which
                // while the app is in the foreground pops the bar up over the very screen the
                // setting was changed on. Toggling the notification has nothing to do with the
                // bar, so it gets its own command.
                "refresh_notification" -> startForegroundService()
                "update" -> {
                    controller.update()
                    // The notification's action buttons are a setting too, and startForeground on
                    // the same id replaces the existing notification in place.
                    startForegroundService()
                }
                else -> controller.handleCommand(action)
            }
        } ?: run {
            // Service started without action (including a START_STICKY relaunch): show the handler.
            shouldFinish = false
            controller.show()
        }
        return START_STICKY
    }

    private fun stopForegroundAndSelf() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopServiceEntirely() {
        shouldFinish = true
        preference.setRunning(false)
        // Stopping is not hiding: the bar should be there again the next time the service is
        // started, or the user would turn it on and get nothing.
        preference.setHandlerHidden(false)
        controller.hide()
        stopForegroundAndSelf()
    }

    /** The accessibility service is taking the bar over. Leave, and leave every preference alone. */
    private fun handOver() {
        shouldFinish = true
        handedOver = true
        controller.hide()
        stopForegroundAndSelf()
    }

    // =============================================================================================
    // OverlayServiceInterface
    // =============================================================================================

    override fun show() {
        shouldFinish = false
        controller.show()
    }

    override fun hide() = controller.hide()

    override fun update() {
        controller.update()
        startForegroundService()
    }
}
