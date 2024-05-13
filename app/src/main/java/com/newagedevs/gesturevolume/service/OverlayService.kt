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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.model.UnlockCondition
import com.newagedevs.gesturevolume.persistence.SharedPrefRepository
import com.newagedevs.gesturevolume.utils.Constants
import com.newagedevs.gesturevolume.view.HandlerView
import org.koin.android.ext.android.inject
import kotlin.math.abs
import kotlin.math.sqrt


interface OverlayServiceInterface {
    fun show()
    fun hide()
    fun update()
}


class OverlayService : Service(), OverlayServiceInterface {

    private val binder: IBinder = LocalBinder()
    private val preference: SharedPrefRepository by inject()

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
    private var maxVolume: Int? = null


    private var lockScreenUtil: LockScreenUtil? = null

    private var minSwipeY: Float = 0f

    companion object {

        private const val CHANNEL_ID = "Gesture Volume Channel ID"
        private const val NOTIFICATION_ID = 1

        private const val TOUCH_MOVE_FACTOR: Long = 20
        private const val TOUCH_TIME_FACTOR: Long = 300
        private const val DOUBLE_CLICK_TIME_DELTA: Long = 300
        private const val LONG_PRESS_TIME_THRESHOLD: Long = 500

        val communicator = MutableLiveData<Any>()

        private var volume: Int = 0
    }
    private var previousVolume: Int = 1

    private var lastX: Float = 0f
    private var lastY: Float = 0f

//    private val handler = Handler(Looper.getMainLooper())
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var singleClickHandler = Handler(Looper.getMainLooper())

    private var eventX1: Float = 0f
    private var eventX2: Float = 0f

    private var startY: Float = 0f

    private var actionDownPoint = PointF(0f, 0f)
    private var previousPoint = PointF(0f, 0f)

    private var touchDownTime = 0L
    private var lastClickTime = 0L

    // Single press
    private var singleClickRunnable = java.lang.Runnable {
        handlerTapActions(preference.getHandlerSingleTapAction())
    }

    // Long press
    private var longPressedRunnable = java.lang.Runnable {
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
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        lockScreenUtil = LockScreenUtil(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay notification",
                NotificationManager.IMPORTANCE_HIGH
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }

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


        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }else {
            startForeground(
                NOTIFICATION_ID,
                notification)
        }

    }


    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, OverlayService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_MUTABLE)
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlayView()
        hideHandlerView()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            try{
                stopForeground(true)
            }catch (_:Exception) { }
        }
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let {
            when (it) {
                "show" -> {
                    if (communicator.hasActiveObservers()) {
                        communicator.postValue("show")
                    }
                    createOverlayHandler()
                }
                "hide" -> {
                    hideOverlayView()
                    hideHandlerView()
                    return START_STICKY
                }
                "stop" -> {
                    if (communicator.hasActiveObservers()) {
                        communicator.postValue("stop")
                    }

                    preference.setRunning(false)
                    hideOverlayView()
                    hideHandlerView()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        try{
                            stopForeground(true)
                        }catch (_:Exception) { }
                    }
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun now(): Long {
        return SystemClock.elapsedRealtime()
    }


    private fun createOverlayHandler() {
        if (handlerView == null) {
            // load prefs
            val handlerPosition = preference.getHandlerPosition()
            val handlerColor = preference.getHandlerColor()
            val handlerSize = preference.getHandlerSize()
            val handlerWidth = preference.getHandlerWidth()
            val translationY = preference.getHandlerTranslationY()

            val layoutParams = WindowManager.LayoutParams(
                Constants.handlerWidthValue(handlerWidth),
                Constants.handlerSizeValue(handlerSize),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                x = 0
                y = translationY.toInt()
                gravity = Gravity.TOP or if (handlerPosition == "Left") Gravity.START else Gravity.END
            }

            handlerView = HandlerView(this)
            handlerView?.setHandlerPositionIsLocked(true)
            handlerView?.setTranslationYPosition(0f)
            handlerView?.setViewGravity(if (handlerPosition == "Left") Gravity.START else Gravity.END)
            handlerView?.setViewColor(handlerColor, (handlerColor shr 24) and 0xFF)
            handlerView?.setViewDimension(Constants.handlerWidthValue(handlerWidth), Constants.handlerSizeValue(handlerSize))
            handlerView?.setHandlerPositionChangeListener(object : HandlerView.HandlerPositionChangeListener {
                override fun onVertical(rawY: Float) { }

                override fun onVertical(rawY: Int) {

                }
            })

            handlerViewEvents()

            windowManager?.addView(handlerView, layoutParams)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handlerViewEvents() {

        val handlerSize = preference.getHandlerSize()
        val handlerWidth = preference.getHandlerWidth()

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
                    return@setOnTouchListener true
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
                        val distance = sqrt(((currentY - firstY) * (currentY - firstY) + (currentX - firstX) * (currentX - firstX)).toDouble())

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

                        val sWidth = Constants.handlerWidthValue(handlerWidth)
                        val sHeight = Constants.handlerSizeValue(handlerSize)

                        val border = 1
                        if(event.x < border || event.y < border || event.x > sWidth - border || event.y > sHeight - border)
                            return@setOnTouchListener false

                        if(abs(distanceX) < abs(distanceY) && abs(minSwipeY) > 10){
                            if (distanceY > 0) {
                                // Swipe Down
                                adjustVolume(-1, preference.getHandlerSwipeDownAction())
                            } else {
                                // Swipe Up
                                adjustVolume(1, preference.getHandlerSwipeUpAction())
                            }
                            minSwipeY = 0f
                        }
                        lastX = x
                        lastY = y
                    }
                }
                MotionEvent.ACTION_UP -> {
                    isActionMoveEventStored = false
                    longPressHandler.removeCallbacks(longPressedRunnable)
                    if(isLongPressHandlerActivated) {
                        isLongPressHandlerActivated = false
                        return@setOnTouchListener false
                    }

                    val isTouchDuration = now() - touchDownTime < TOUCH_TIME_FACTOR
                    val isTouchLength = abs(event.x - actionDownPoint.x) + abs(event.y - actionDownPoint.y) < TOUCH_MOVE_FACTOR
                    val shouldClick = isTouchLength && isTouchDuration

                    if (shouldClick) {
                        val currentTime = now()
                        if (currentTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                            // Cancel the single click action and trigger double click
                            singleClickHandler.removeCallbacks(singleClickRunnable)
                            handlerTapActions(preference.getHandlerDoubleTapAction()) // Double click detected
                        } else {
                            singleClickHandler.postDelayed(singleClickRunnable, DOUBLE_CLICK_TIME_DELTA)
                        }
                        lastClickTime = currentTime
                    }
                }
            }

            return@setOnTouchListener false
        }

    }

    private fun adjustVolume(change: Int, action: String) {
        val newVolume = (volume + change).coerceIn(0, maxVolume)
        if (newVolume != volume) {
            volume = newVolume
            when (action) {
                "Increase volume", "Decrease volume" -> audioManager!!.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                "Increase volume and show UI", "Decrease volume and show UI" -> audioManager!!.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI)
            }
        }
    }

    private fun openApp() {
        val packageManager = applicationContext.packageManager
        val intent = packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        if (intent != null) {
            hideHandlerView()
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            applicationContext.startActivity(intent)
        }
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    @Suppress("DEPRECATION")
    private fun createOverlayView() {
        if (overlayView == null) {
            // load prefs

            overlayView = LayoutInflater.from(this@OverlayService).inflate(R.layout.overlay_layout, null)
            overlayView?.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)

            val overlayViewHolder = overlayView?.findViewById<ConstraintLayout>(R.id.overlay_view_holder)

            // Overlay background d color
            val drawable = GradientDrawable()
            drawable.setColor(Color.BLACK)
            overlayViewHolder?.background = drawable

            overlayViewHolder?.setOnTouchListener { _, event ->
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

                        return@setOnTouchListener true
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
                            val distance = sqrt(((currentY - firstY) * (currentY - firstY) + (currentX - firstX) * (currentX - firstX)).toDouble())

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

                            val sWidth = Resources.getSystem().displayMetrics.widthPixels
                            val sHeight = Resources.getSystem().displayMetrics.heightPixels

                            val border = 100 * Resources.getSystem().displayMetrics.density.toInt()
                            if(event.x < border || event.y < border || event.x > sWidth - border || event.y > sHeight - border)
                                return@setOnTouchListener false

                            if(abs(distanceX) < abs(distanceY) && abs(minSwipeY) > 30){
                                val maxVolume = audioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                                val newValue = if(distanceY > 0) {
                                    volume - 1
                                } else {
                                    volume + 1
                                }

                                if(newValue in 0..maxVolume) volume = newValue
                                audioManager!!.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI)

                                minSwipeY = 0f
                            }
                            lastX = x
                            lastY = y
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        isActionMoveEventStored = false
                        longPressHandler.removeCallbacks(longPressedRunnable)
                        if(isLongPressHandlerActivated) {
                            isLongPressHandlerActivated = false
                            return@setOnTouchListener false
                        }

                        val isTouchDuration = now() - touchDownTime < TOUCH_TIME_FACTOR
                        val isTouchLength = abs(event.x - actionDownPoint.x) + abs(event.y - actionDownPoint.y) < TOUCH_MOVE_FACTOR
                        val shouldClick = isTouchLength && isTouchDuration

                        if (shouldClick) {
                            lastClickTime = if (now() - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                                // Double click
                                if("Tap to unlock" == UnlockCondition.DOUBLE_TAP.displayText) {
                                    hideOverlayView()
                                    createOverlayHandler()
                                }
                                0
                            } else {
                                // Single click
                                if("Tap to unlock" == UnlockCondition.TAP.displayText) {
                                    hideOverlayView()
                                    createOverlayHandler()
                                }
                                now()
                            }
                        }
                    }
                }

                return@setOnTouchListener false
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
                    flags =
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                }
            }

            windowManager!!.addView(overlayView, layoutParams)
        }
    }

    private fun onLongPress() {
        hideOverlayView()
        createOverlayHandler()
    }

    private fun hideOverlayView() {
        overlayView?.let { oView ->
            windowManager?.removeView(oView)
            overlayView = null
        }
    }

    private fun hideHandlerView() {
        handlerView?.let { hView ->
            windowManager?.removeView(hView)
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

    }

//    private fun singleTapAction() {
//        when (preference.getHandlerSingleTapAction()) {
//            "None" -> { }
//            "Open volume UI" -> {
//                audioManager?.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
//            }
//            "Mute" -> {
//                audioManager?.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
//                audioManager?.adjustVolume(AudioManager.ADJUST_MUTE, 0)
//            }
//            "Active Music Overlay" -> {
//                hideHandlerView()
//                createOverlayView()
//            }
//            "Lock" -> {
//                lockScreenUtil?.lockScreen()
//            }
//            "Hide Handler" -> {
//                hideHandlerView()
//            }
//            "Open App" -> {
//                openApp()
//            }
//            else -> { }
//        }
//    }
//
//    private fun doubleTapAction() {
//        when (preference.getHandlerDoubleTapAction()) {
//            "None" -> { }
//            "Open volume UI" -> {
//                audioManager?.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
//            }
//            "Mute" -> {
//                audioManager?.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
//                audioManager?.adjustVolume(AudioManager.ADJUST_MUTE, 0)
//            }
//            "Active Music Overlay" -> {
//                hideHandlerView()
//                createOverlayView()
//            }
//            "Lock" -> {
//                lockScreenUtil?.lockScreen()
//            }
//            "Hide Handler" -> {
//                hideHandlerView()
//            }
//            "Open App" -> {
//                openApp()
//            }
//            else -> { }
//        }
//    }

    private fun handlerTapActions(action:String) {
        when (action) {
            "None" -> { }
            "Open volume UI" -> {
                audioManager?.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            }
            "Mute" -> {
                audioManager?.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                audioManager?.adjustVolume(AudioManager.ADJUST_MUTE, 0)
            }
            "Mute or Unmute" -> {
                val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)

                audioManager?.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)

                if (currentVolume != null) {
                    if (currentVolume > 0) {
                        // If currently unmuted, mute
                        previousVolume = currentVolume
                        audioManager?.adjustVolume(AudioManager.ADJUST_MUTE, 0)
                    } else {
                        // If currently muted, unmute
                        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
                    }
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
            else -> { }
        }
    }





}