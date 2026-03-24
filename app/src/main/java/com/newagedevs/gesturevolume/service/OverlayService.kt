package com.newagedevs.gesturevolume.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.data.model.UnlockCondition
import com.newagedevs.gesturevolume.livedata.LiveDataManager
import com.newagedevs.gesturevolume.utils.LockScreenUtil
import com.newagedevs.gesturevolume.ui.view.HandlerView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

interface OverlayServiceInterface {
    fun show()
    fun hide()
    fun update()
    var shouldFinish: Boolean
}

@AndroidEntryPoint
class OverlayService : Service(), OverlayServiceInterface {

    @Inject
    lateinit var preference: SharedPref

    override var shouldFinish: Boolean = true

    private val binder: IBinder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun instance(): OverlayServiceInterface = this@OverlayService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
    }

    private var overlayView: View? = null
    private var handlerView: HandlerView? = null

    private var windowManager: WindowManager? = null
    private var audioManager: AudioManager? = null
    private var vibratorService: Vibrator? = null
    private var maxVolume: Int? = null
    private var lockScreenUtil: LockScreenUtil? = null

    private var minSwipeY: Float = 0f

    companion object {
        private const val CHANNEL_ID = "Gesture Volume Channel ID"
        private const val NOTIFICATION_ID = 1
        private const val LONG_PRESS_TIME_THRESHOLD: Long = 500
        private var volume: Int = 0
    }

    private val touchMoveFactor: Long by lazy { 
        (20 * resources.displayMetrics.density).toLong() 
    }
    private val touchTimeFactor: Long = 300L
    private val doubleClickTimeDelta: Long = 300L

    private var previousVolume: Int = 1
    private var lastX: Float = 0f
    private var lastY: Float = 0f

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var singleClickHandler = Handler(Looper.getMainLooper())

    private var eventX1: Float = 0f
    private var eventX2: Float = 0f
    private var startY: Float = 0f

    private var actionDownPoint = PointF(0f, 0f)
    private var previousPoint = PointF(0f, 0f)

    private var touchDownTime = 0L
    private var lastClickTime = 0L

    private var singleClickRunnable = Runnable {
        handlerTapActions(preference.getHandlerSingleTapAction())
    }

    private var longPressedRunnable = Runnable {
        onLongPress()
        handlerTapActions(preference.getHandlerLongTapAction())
        isLongPressHandlerActivated = true
    }

    private var isLongPressHandlerActivated = false
    private var isActionMoveEventStored = false
    private var lastActionMoveEventBeforeUpX = 0f
    private var lastActionMoveEventBeforeUpY = 0f

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        vibratorService = getSystemService(Vibrator::class.java)
        maxVolume = audioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volume = audioManager!!.getStreamVolume(AudioManager.STREAM_MUSIC)
        lockScreenUtil = LockScreenUtil(this)

        createNotificationChannel()
        startForegroundService()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Overlay notification",
            NotificationManager.IMPORTANCE_HIGH
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gesture)
            .setContentTitle("Gesture Volume")
            .setContentText("Tap to manage overlay")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_show, "Show", getPendingIntent("show"))
            .addAction(R.drawable.ic_hide, "Hide", getPendingIntent("hide"))
            .addAction(R.drawable.ic_power, "Stop", getPendingIntent("stop"))
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, OverlayService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlayView()
        hideHandlerView()

        longPressHandler.removeCallbacks(longPressedRunnable)
        singleClickHandler.removeCallbacks(singleClickRunnable)

        if (!shouldFinish && preference.isRunning()) {
            scheduleServiceRestart()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun scheduleServiceRestart() {
        val restartIntent = Intent(this, ServiceRestartReceiver::class.java).apply {
            action = "com.newagedevs.gesturevolume.RESTART_SERVICE"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            restartIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.set(
            android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 1000,
            pendingIntent
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            LiveDataManager.sendCommand(action)
            when (action) {
                "show" -> createOverlayHandler()
                "hide" -> {
                    hideOverlayView()
                    hideHandlerView()
                }
                "stop" -> {
                    preference.setRunning(false)
                    hideOverlayView()
                    hideHandlerView()
                    stopForegroundAndSelf()
                }
                "update" -> {
                    // Reload and update handler with new settings
                    update()
                }
            }
        } ?: run {
            // Service started without action, show handler
            createOverlayHandler()
        }
        return START_STICKY
    }

    private fun stopForegroundAndSelf() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun now(): Long = SystemClock.elapsedRealtime()

    // Update the createOverlayHandler method in OverlayService.kt

    private fun createOverlayHandler() {
        if (handlerView == null) {
            // Load all settings from preference
            val handlerPosition = preference.getHandlerPosition()
            val handlerWidth = preference.getHandlerWidthDp()
            val handlerHeight = preference.getHandlerHeightDp()

            val translationY = preference.getHandlerTranslationY()

            // Appearance settings
            val backgroundColor = preference.getHandlerColor()
            val backgroundAlpha = preference.getHandlerBackgroundAlpha()
            val strokeColor = preference.getHandlerStrokeColor()
            val strokeWidth = preference.getHandlerStrokeWidth()
            val strokeAlpha = preference.getHandlerStrokeAlpha()

            // Corner radius settings
            val cornerRadiusTL = preference.getHandlerCornerRadiusTL()
            val cornerRadiusTR = preference.getHandlerCornerRadiusTR()
            val cornerRadiusBL = preference.getHandlerCornerRadiusBL()
            val cornerRadiusBR = preference.getHandlerCornerRadiusBR()

            // Icon settings
            val iconRes = preference.getHandlerIconRes()
            val iconSize = preference.getHandlerIconSize()
            val iconColor = preference.getHandlerIconColor() // NEW
            val showIcon = preference.getHandlerShowIcon()

            // Behavior settings
            val vibrateOnClick = preference.getHandlerVibrateOnClick()
            val lockPosition = preference.getHandlerLockPosition()

            val gravity = if (handlerPosition == "Left") Gravity.START else Gravity.END

            val layoutParams = WindowManager.LayoutParams(
                dpToPx(handlerWidth),
                dpToPx(handlerHeight),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                x = 0
                y = translationY.toInt()
                this.gravity = Gravity.TOP or gravity
            }

            handlerView = HandlerView(this).apply {
                // Position and dimensions
                setViewGravity(gravity)
                setViewDimensionsDp(handlerWidth, handlerHeight)
                setTranslationYPosition(0f)

                // Appearance
                setViewBackgroundColor(backgroundColor, backgroundAlpha)
                setStrokeProperties(strokeColor, strokeWidth, strokeAlpha)
                setCornerRadiiDp(cornerRadiusTL, cornerRadiusTR, cornerRadiusBL, cornerRadiusBR)

                // Icon with color support
                val safeDrawable = try {
                    if (resources.getResourceTypeName(iconRes) == "drawable") {
                        ContextCompat.getDrawable(this@OverlayService, iconRes)
                    } else null
                } catch (_: Exception) {
                    null
                } ?: ContextCompat.getDrawable(this@OverlayService, R.drawable.ic_vol_increase)

                // Use overload that accepts Drawable
                setCenterIcon(safeDrawable, iconSize, iconColor)
                setCenterIconColor(iconColor)
                setCenterIconVisible(showIcon)

                // Behavior
                setVibrateOnClick(vibrateOnClick)
                setHandlerPositionLocked(true)

                // Click listener for tap actions
                setHandlerClickListener(object : HandlerView.HandlerClickListener {
                    override fun onSingleClick() {
                        handlerTapActions(preference.getHandlerSingleTapAction())
                    }

                    override fun onDoubleClick() {
                        handlerTapActions(preference.getHandlerDoubleTapAction())
                    }
                })
            }

            handlerViewEvents()
            windowManager?.addView(handlerView, layoutParams)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handlerViewEvents() {
        val handlerHeight = preference.getHandlerHeightDp()
        val handlerWidth = preference.getHandlerWidthDp()

        handlerView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressHandler.postDelayed(longPressedRunnable, LONG_PRESS_TIME_THRESHOLD)
                    actionDownPoint = PointF(event.x, event.y)
                    previousPoint = PointF(event.x, event.y)
                    touchDownTime = now()
                    eventX1 = event.x
                    startY = event.y
                    minSwipeY = 0f
                    lastX = event.x
                    lastY = event.y
                    true
                }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                    if (!isActionMoveEventStored) {
                        isActionMoveEventStored = true
                        lastActionMoveEventBeforeUpX = event.x
                        lastActionMoveEventBeforeUpY = event.y
                    } else {
                        val currentX = event.x
                        val currentY = event.y
                        val firstX = lastActionMoveEventBeforeUpX
                        val firstY = lastActionMoveEventBeforeUpY
                        val distance = sqrt(
                            ((currentY - firstY) * (currentY - firstY) +
                                    (currentX - firstX) * (currentX - firstX)).toDouble()
                        )

                        if (distance > 20) {
                            longPressHandler.removeCallbacks(longPressedRunnable)
                            eventX2 = event.x
                            previousPoint = PointF(event.x, event.y)
                        }

                        val x = event.x
                        val y = event.y
                        val distanceX = x - lastX
                        val distanceY = y - lastY

                        minSwipeY += distanceY

                        val sWidth = dpToPx(handlerWidth)
                        val sHeight = dpToPx(handlerHeight)

                        val border = 1
                        if (event.x < border || event.y < border ||
                            event.x > sWidth - border || event.y > sHeight - border) {
                            return@setOnTouchListener false
                        }

                        if (abs(distanceX) < abs(distanceY) && abs(minSwipeY) > 10) {
                            if (distanceY > 0) {
                                adjustVolume(-1, preference.getHandlerSwipeDownAction())
                            } else {
                                adjustVolume(1, preference.getHandlerSwipeUpAction())
                            }
                            minSwipeY = 0f
                        }
                        lastX = x
                        lastY = y
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    isActionMoveEventStored = false
                    longPressHandler.removeCallbacks(longPressedRunnable)

                    if (isLongPressHandlerActivated) {
                        isLongPressHandlerActivated = false
                        return@setOnTouchListener false
                    }

                    val isTouchDuration = now() - touchDownTime < touchTimeFactor
                    val isTouchLength = abs(event.x - actionDownPoint.x) +
                            abs(event.y - actionDownPoint.y) < touchMoveFactor
                    val shouldClick = isTouchLength && isTouchDuration

                    if (shouldClick) {
                        val currentTime = now()
                        if (currentTime - lastClickTime < doubleClickTimeDelta) {
                            singleClickHandler.removeCallbacks(singleClickRunnable)
                            handlerTapActions(preference.getHandlerDoubleTapAction())
                        } else {
                            singleClickHandler.postDelayed(singleClickRunnable, doubleClickTimeDelta)
                        }
                        lastClickTime = currentTime
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun adjustVolume(change: Int, action: String) {
        volume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val newVolume = (volume + change).coerceIn(0, maxVolume ?: 15)

        if (newVolume != volume) {
            volume = newVolume
            when (action) {
                "Increase volume", "Decrease volume" ->
                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                "Increase volume and show UI", "Decrease volume and show UI" ->
                    audioManager?.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        volume,
                        AudioManager.FLAG_SHOW_UI
                    )
            }
        }
    }

    private fun openApp() {
        val packageManager = applicationContext.packageManager
        val intent = packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        intent?.let {
            hideHandlerView()
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            applicationContext.startActivity(it)
        }
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    @Suppress("DEPRECATION")
    private fun createOverlayView() {
        if (overlayView == null) {
            overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null).apply {
                systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
            }

            val overlayViewHolder = overlayView?.findViewById<ConstraintLayout>(R.id.overlay_view_holder)
            overlayViewHolder?.background = GradientDrawable().apply {
                setColor(Color.BLACK)
            }

            overlayViewHolder?.setOnTouchListener { _, event ->
                handleOverlayTouchEvent(event)
            }

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    flags = WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                }
            }

            windowManager?.addView(overlayView, layoutParams)
        }
    }

    private fun handleOverlayTouchEvent(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                longPressHandler.postDelayed(longPressedRunnable, LONG_PRESS_TIME_THRESHOLD)
                actionDownPoint = PointF(event.x, event.y)
                previousPoint = PointF(event.x, event.y)
                touchDownTime = now()
                eventX1 = event.x
                startY = event.y
                minSwipeY = 0f
                lastX = event.x
                lastY = event.y
                true
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                handleOverlayMove(event)
                false
            }
            MotionEvent.ACTION_UP -> {
                handleOverlayUp(event)
            }
            else -> false
        }
    }

    private fun handleOverlayMove(event: MotionEvent) {
        if (!isActionMoveEventStored) {
            isActionMoveEventStored = true
            lastActionMoveEventBeforeUpX = event.x
            lastActionMoveEventBeforeUpY = event.y
        } else {
            val currentX = event.x
            val currentY = event.y
            val distance = sqrt(
                ((currentY - lastActionMoveEventBeforeUpY) * (currentY - lastActionMoveEventBeforeUpY) +
                        (currentX - lastActionMoveEventBeforeUpX) * (currentX - lastActionMoveEventBeforeUpX)).toDouble()
            )

            if (distance > 20) {
                longPressHandler.removeCallbacks(longPressedRunnable)
                eventX2 = event.x
                previousPoint = PointF(event.x, event.y)
            }

            val distanceY = event.y - lastY
            minSwipeY += distanceY

            val screenWidth = Resources.getSystem().displayMetrics.widthPixels
            val screenHeight = Resources.getSystem().displayMetrics.heightPixels
            val border = (100 * Resources.getSystem().displayMetrics.density).toInt()

            if (event.x < border || event.y < border ||
                event.x > screenWidth - border || event.y > screenHeight - border) {
                return
            }

            if (abs(distanceY) > abs(event.x - lastX) && abs(minSwipeY) > 30) {
                volume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                val newVolume = if (distanceY > 0) volume - 1 else volume + 1

                if (newVolume in 0..maxVol) {
                    volume = newVolume
                    audioManager?.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        volume,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
                minSwipeY = 0f
            }
            lastX = event.x
            lastY = event.y
        }
    }

    private fun handleOverlayUp(event: MotionEvent): Boolean {
        isActionMoveEventStored = false
        longPressHandler.removeCallbacks(longPressedRunnable)

        if (isLongPressHandlerActivated) {
            isLongPressHandlerActivated = false
            return false
        }

        val isTouchDuration = now() - touchDownTime < touchTimeFactor
        val isTouchLength = abs(event.x - actionDownPoint.x) +
                abs(event.y - actionDownPoint.y) < touchMoveFactor
        val shouldClick = isTouchLength && isTouchDuration

        if (shouldClick) {
            val currentTime = now()
            lastClickTime = if (currentTime - lastClickTime < doubleClickTimeDelta) {
                if (UnlockCondition.DOUBLE_TAP.displayText == "Double tap to unlock") {
                    hideOverlayView()
                    createOverlayHandler()
                }
                0
            } else {
                if (UnlockCondition.TAP.displayText == "Tap to unlock") {
                    hideOverlayView()
                    createOverlayHandler()
                }
                currentTime
            }
        }
        return false
    }

    private fun onLongPress() {
        hideOverlayView()
        createOverlayHandler()
    }

    private fun hideOverlayView() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                // View already removed
            }
            overlayView = null
        }
    }

    private fun hideHandlerView() {
        handlerView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                // View already removed
            }
            handlerView = null
        }
    }

    override fun show() {
        createOverlayHandler()
    }

    override fun hide() {
        hideOverlayView()
        hideHandlerView()
    }

    override fun update() {
        // Remove existing handler and recreate with new settings
        hideHandlerView()
        createOverlayHandler()
    }

    private fun handlerTapActions(action: String) {
        if (preference.getHandlerVibrateOnClick()) {
            vibratorService?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        when (action) {
            "None" -> {}
            "Open volume UI" -> {
                audioManager?.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            }
            "Mute" -> {
                audioManager?.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                audioManager?.adjustVolume(AudioManager.ADJUST_MUTE, 0)
            }
            "Mute or Unmute" -> {
                val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                audioManager?.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)

                if (currentVolume > 0) {
                    previousVolume = currentVolume
                    audioManager?.adjustVolume(AudioManager.ADJUST_MUTE, 0)
                } else {
                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
                }
            }
            "Active Music Overlay" -> {
                hideHandlerView()
                createOverlayView()
            }
            "Lock" -> {
                lockScreenUtil?.lockScreen()
            }
            "Hide Handler" -> {
                hideHandlerView()
            }
            "Open App" -> {
                openApp()
            }
        }
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * Resources.getSystem().displayMetrics.density).toInt()
    }
}