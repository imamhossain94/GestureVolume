package com.newagedevs.gesturevolume.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.view.View.OnTouchListener
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.room.Room
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.model.AppHandler
import com.newagedevs.gesturevolume.persistence.AppDatabase
import com.newagedevs.gesturevolume.utils.Constants
import com.newagedevs.gesturevolume.utils.SharedData
import com.newagedevs.gesturevolume.view.ui.main.MainActivity
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class OverlayService : Service(), OnTouchListener {

    companion object {
        private const val CHANNEL_ID = "overlay_channel_id"
        private const val CHANNEL_NAME = "Overlay notification"
        private const val TITLE = "Gesture Volume: Easy Control"
        private const val CONTENT = "Control your device volume through gesture-based interactions"

        private const val START_FOREGROUND_ACTION = "START_FOREGROUND_ACTION"
        private const val STOP_FOREGROUND_ACTION = "STOP_FOREGROUND_ACTION"


        fun start(activity: Activity) {
            try{
                val serviceIntent = Intent(activity, OverlayService::class.java).apply {
                    action = START_FOREGROUND_ACTION
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activity.startForegroundService(serviceIntent)
                } else {
                    activity.startService(serviceIntent)
                }
            } catch (_:Exception) { }
        }

        fun stop(activity: Activity) {
            try{
                activity.startService(Intent(activity, OverlayService::class.java).apply {
                    action = STOP_FOREGROUND_ACTION
                })
            } catch (_:Exception) { }
        }

        fun isRunning(activity: Activity): Boolean {
            val manager = activity.getSystemService(ACTIVITY_SERVICE) as ActivityManager
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if ((OverlayService::class.java).name == service.service.className) {
                    return true
                }
            }
            return false
        }

        fun hasPermission(activity: Activity): Boolean {
            if (!Settings.canDrawOverlays(activity)) {
                SharedData.shouldShowAppOpenAds = false
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                )
                activity.startActivityForResult(intent, MainActivity.OVERLAY_REQUEST_CODE)
                return false
            }
            return true
        }
    }

    // Handler Variables
    var windowManager: WindowManager? = null
    private var handleView: FrameLayout? = null
    var lockScreenUtil: LockScreenUtil? = null

    private lateinit var audioManager: AudioManager
    private lateinit var appDatabase: AppDatabase
    private var appHandler: AppHandler? = null
    private var gestureDetector: GestureDetector? = null

    private val looper = Handler(Looper.getMainLooper())

    private var eventX1: Float = 0f
    private var eventX2: Float = 0f

    private var actionDownPoint = PointF(0f, 0f)
    private var previousPoint = PointF(0f, 0f)

    // Music Overlay Variables
    var windowManagerMusic: WindowManager? = null
    private var musicOverlayView: FrameLayout? = null

    override fun onCreate() {
        super.onCreate()
        appDatabase = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            getString(R.string.database)
        ).build()

        lockScreenUtil = LockScreenUtil(this)

        gestureDetector = GestureDetector(this@OverlayService, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Single tap logic
                return this@OverlayService.onSingleTap()
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Double tap logic
                return this@OverlayService.onDoubleTap()
            }

            override fun onLongPress(e: MotionEvent) {
                // Long press logic
                this@OverlayService.onLongPress()
            }

            override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val sensitivityMultiplier = 3.0 // Adjust this value to increase or decrease sensitivity

                val distanceY = (e2.y - e1.y) * sensitivityMultiplier
                val distanceX = (e2.x - e1.x) * sensitivityMultiplier

                return if (abs(distanceX) > abs(distanceY)) {
                    if (distanceX > 0) {
                        // Swipe Left to Right direction to Increase Volume
                        audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    } else {
                        // Swipe Right to Left direction to Decrease Volume
                        audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    }
                    true
                } else {
                    if (distanceY > 0) {
                        // Swipe Up to Down direction to Decrease Volume
                        audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    } else {
                        // Swipe Down to Up direction to Increase Volume
                        audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    }
                    true
                }
            }

        })

    }

    private suspend fun getAppHandler(): AppHandler? {
        return withContext(Dispatchers.IO) {
            appDatabase.handlerDao().getHandler()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (handleView != null) {
            windowManager!!.removeView(handleView)
            handleView = null
        }

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            createOrUpdateHandleView(false)
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            createOrUpdateHandleView(true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //super.onStartCommand(intent, flags, startId)
        if (intent != null) {
            when (intent.action) {
                START_FOREGROUND_ACTION -> {
                    if (handleView != null) {
                        windowManager!!.removeView(handleView)
                        handleView = null
                    }
                    createOrUpdateHandleView(resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                }
                STOP_FOREGROUND_ACTION -> {
                    if (handleView != null) {
                        windowManager!!.removeView(handleView)
                        handleView = null
                    }
                    try{
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_DETACH)
                        }
                        stopSelfResult(startId)
                        stopSelf()
                    } catch (_: IllegalStateException) { }
                    return START_NOT_STICKY
                }
            }
        }

        return START_STICKY
    }

    private fun createOrUpdateHandleView(isPortrait: Boolean) {
        if (Build.VERSION.SDK_INT >= 26) {
            createNotificationChannel()
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        CoroutineScope(Dispatchers.IO).launch {
            appHandler = getAppHandler()
            looper.post {
                if (appHandler != null) {
                    val layoutParams = getLayoutParams(isPortrait)
                    val drawable = getDrawableAndSetGravity(isPortrait, layoutParams)
                    createView(isPortrait, layoutParams, drawable)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME,
            NotificationManager.IMPORTANCE_MIN
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(TITLE)
            .setContentText(CONTENT)
            .setSmallIcon(R.drawable.ic_gesture)
            .build()

        startForeground(1, notification)
    }

    private fun getLayoutParams(isPortrait: Boolean): WindowManager.LayoutParams {
        val displayMetrics = resources.displayMetrics
        val width: Int = when {
            isPortrait -> {
                when (appHandler!!.width) {
                    "Slim" -> Constants.Slim
                    "Regular" -> Constants.Regular
                    "Bold" -> Constants.Bold
                    else -> Constants.Slim
                } * 3
            }
            else -> {
                when (appHandler!!.size) {
                    "Small" -> Constants.Small
                    "Medium" -> Constants.Medium
                    "Large" -> Constants.Large
                    else -> Constants.Small
                }
            }
        }

        val height: Int = if (isPortrait) {
            when (appHandler!!.size) {
                "Small" -> Constants.Small
                "Medium" -> Constants.Medium
                "Large" -> Constants.Large
                else -> Constants.Small
            }
        } else {
            when (appHandler!!.widthLand) {
                "Slim" -> Constants.Slim
                "Regular" -> Constants.Regular
                "Bold" -> Constants.Bold
                else -> Constants.Slim
            } * 3
        }

        val defaultDisplay = getSystemService<DisplayManager>()?.getDisplay(Display.DEFAULT_DISPLAY)

        val isTop = appHandler?.gravityLand == "Top"
        val isBottom = appHandler?.gravityLand == "Bottom"

        val maxDisplayWidthHeight = max(displayMetrics.widthPixels, displayMetrics.heightPixels)
        val minDisplayWidthHeight = min(displayMetrics.widthPixels, displayMetrics.heightPixels)

        val xLand = when (defaultDisplay?.rotation) {
            Surface.ROTATION_0 -> appHandler!!.leftMargin!!.toInt()
            Surface.ROTATION_90 -> if (isBottom) maxDisplayWidthHeight - width - appHandler!!.leftMargin!!.toInt() else appHandler!!.leftMargin!!.toInt()
            Surface.ROTATION_180 -> 0
            Surface.ROTATION_270 -> if (isTop) maxDisplayWidthHeight - width - appHandler!!.leftMargin!!.toInt() else appHandler!!.leftMargin!!.toInt()
            else -> 0
        }

        val yLand = when (defaultDisplay?.rotation) {
            Surface.ROTATION_0 -> appHandler!!.leftMargin!!.toInt()
            Surface.ROTATION_90 -> if (isTop) 0 else minDisplayWidthHeight
            Surface.ROTATION_180 -> appHandler!!.leftMargin!!.toInt()
            Surface.ROTATION_270 -> if (isBottom) 0 else minDisplayWidthHeight
            else -> 0
        }

        return WindowManager.LayoutParams(
            width, height, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            x = if (isPortrait) 0 else xLand
            y = if (isPortrait) appHandler!!.topMargin!!.toInt() else yLand
        }
    }

    private fun getDrawableAndSetGravity(isPortrait: Boolean, layoutParams: WindowManager.LayoutParams): Drawable? {
        val defaultDisplay = getSystemService<DisplayManager>()?.getDisplay(Display.DEFAULT_DISPLAY)

        val gravityStartTop = Gravity.START or Gravity.TOP
        val gravityEndTop = Gravity.END or Gravity.TOP

        return if (isPortrait) {
            val drawableRes = when (appHandler?.gravity) {
                "Left" -> {
                    layoutParams.gravity = gravityStartTop
                    R.drawable.item_handler_left
                }
                "Right", null -> {
                    layoutParams.gravity = gravityEndTop
                    R.drawable.item_handler_right
                }
                else -> {
                    layoutParams.gravity = gravityEndTop
                    R.drawable.item_handler_right
                }
            }
            ContextCompat.getDrawable(this@OverlayService, drawableRes)
        } else {
            val drawableRes = when (val rotation = defaultDisplay?.rotation ?: Surface.ROTATION_0) {
                Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270 -> {
                    val isTop = appHandler?.gravityLand == "Top"
                    val isBottom = appHandler?.gravityLand == "Bottom"
                    when {
                        isTop -> {
                            layoutParams.gravity = gravityStartTop
                            if (rotation == Surface.ROTATION_270) R.drawable.item_handler_top else R.drawable.item_handler_bottom
                        }
                        isBottom -> {
                            layoutParams.gravity = gravityEndTop
                            if (rotation == Surface.ROTATION_270) R.drawable.item_handler_bottom else R.drawable.item_handler_top
                        }
                        else -> {
                            layoutParams.gravity = gravityEndTop
                            if (rotation == Surface.ROTATION_270) R.drawable.item_handler_top else R.drawable.item_handler_bottom
                        }
                    }
                }
                else -> {
                    layoutParams.gravity = gravityEndTop
                    R.drawable.item_handler_bottom
                }
            }
            ContextCompat.getDrawable(this@OverlayService, drawableRes)
        }
    }

    private fun getImageLayoutParams(isPortrait: Boolean): FrameLayout.LayoutParams {
        val width: Int = when {
            isPortrait -> {
                when (appHandler!!.width) {
                    "Slim" -> Constants.Slim
                    "Regular" -> Constants.Regular
                    "Bold" -> Constants.Bold
                    else -> Constants.Slim
                }
            }
            else -> {
                when (appHandler!!.size) {
                    "Small" -> Constants.Small
                    "Medium" -> Constants.Medium
                    "Large" -> Constants.Large
                    else -> Constants.Small
                }
            }
        }

        val height: Int = if (isPortrait) {
            when (appHandler!!.size) {
                "Small" -> Constants.Small
                "Medium" -> Constants.Medium
                "Large" -> Constants.Large
                else -> Constants.Small
            }
        } else {
            when (appHandler!!.width) {
                "Slim" -> Constants.Slim
                "Regular" -> Constants.Regular
                "Bold" -> Constants.Bold
                else -> Constants.Slim
            }
        }

        val defaultDisplay = getSystemService<DisplayManager>()?.getDisplay(Display.DEFAULT_DISPLAY)

        val handlerGravity = if (isPortrait) {
            when (appHandler?.gravity) {
                "Left" -> {
                    Gravity.START or Gravity.LEFT
                }
                "Right", null -> {
                    Gravity.END or Gravity.RIGHT
                }
                else -> {
                    Gravity.END or Gravity.RIGHT
                }
            }
        } else {
            when (val rotation = defaultDisplay?.rotation ?: Surface.ROTATION_0) {
                Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270 -> {
                    val isTop = appHandler?.gravityLand == "Top"
                    val isBottom = appHandler?.gravityLand == "Bottom"
                    when {
                        isTop -> {
                            if (rotation == Surface.ROTATION_270) Gravity.BOTTOM else Gravity.TOP
                        }
                        isBottom -> {
                            if (rotation == Surface.ROTATION_270) Gravity.TOP else Gravity.BOTTOM
                        }
                        else -> {
                            if (rotation == Surface.ROTATION_270) Gravity.BOTTOM else Gravity.TOP
                        }
                    }
                }
                else -> {
                    Gravity.TOP
                }
            }
        }

        return FrameLayout.LayoutParams(
            width, height
        ).apply {
           gravity = handlerGravity
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun createView(isPortrait: Boolean, layoutParams: WindowManager.LayoutParams, drawable: Drawable?) {
        handleView = FrameLayout(this@OverlayService)
        val imageView = ImageView(this@OverlayService)
        imageView.background = drawable
        imageView.background.colorFilter = PorterDuffColorFilter(
            appHandler!!.color ?: try {
                Color.parseColor("#47000000")
            } catch (e: IllegalArgumentException) {
                Color.BLACK
            }, PorterDuff.Mode.MULTIPLY
        )
        handleView?.addView(imageView, getImageLayoutParams(isPortrait))

        handleView?.setOnTouchListener(this@OverlayService)

        windowManager!!.addView(handleView, layoutParams)
    }

    private fun createMusicOverlayView() {
        if (musicOverlayView != null) {
            windowManagerMusic?.removeView(musicOverlayView)
            musicOverlayView = null
        }

        windowManagerMusic = getSystemService(WINDOW_SERVICE) as WindowManager
        musicOverlayView = FrameLayout(this@OverlayService)
        musicOverlayView?.setBackgroundColor(Color.BLACK)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        // This flag allows the window to extend outside the screen. Use it with caution.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Set flags to draw over status bar and navigation bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        // Hide the status bar and navigation bar
        musicOverlayView?.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        // Add a TextView
        val textView = TextView(this@OverlayService)
        textView.text = "The music overlay is activated, allowing you to play video music in the background while applying a black overlay. If your display is Super AMOLED, it can help save battery life. You can unlock the phone by clicking the unlock button."
        textView.setTextColor(Color.argb(150, 128, 128, 128))
        textView.textSize = 14f
        val textParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        textParams.gravity = Gravity.CENTER
        textView.layoutParams = textParams.apply {
            setMargins(150, 150, 150, 150)
        }

        // Add a Button
        val button = Button(this@OverlayService)

        // Set the background drawable to create an outlined button
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.RECTANGLE
        shape.setStroke(2, Color.argb(150, 128, 128, 128)) // Set the border width and color
        shape.cornerRadius = 12f
        shape.setColor(Color.TRANSPARENT) // Set the background color to transparent

        // Set the custom background drawable to the button
        button.background = shape

        // Set other properties of the button
        button.text = "Unlock"
        button.setTextColor(Color.argb(150, 128, 128, 128))
        button.textSize = 14f

        val buttonParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        buttonParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        button.layoutParams = buttonParams.apply {
            setMargins(0, 0, 0, 150)
        }
        button.setOnClickListener {
            if (musicOverlayView != null) {
                windowManagerMusic?.removeView(musicOverlayView)
                musicOverlayView = null
            }
        }

        // Add the TextView and Button to the overlay view
        musicOverlayView?.addView(textView)
        musicOverlayView?.addView(button)

        windowManagerMusic?.addView(musicOverlayView, layoutParams)
    }

    private fun increaseVolume() {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val increasedVolume = currentVolume + 1
        val volumeToSet = if (increasedVolume <= maxVolume) increasedVolume else maxVolume

        when (appHandler!!.upperSwipe) {
            "None" -> {}
            "Increase volume" -> audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeToSet, 0)
            "Increase volume and show UI" -> {
                audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeToSet, 0)
            }
            else -> audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeToSet, 0)
        }
    }

    private fun decreaseVolume() {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val decreasedVolume = currentVolume - 1
        val volumeToSet = if (decreasedVolume >= 0) decreasedVolume else 0

        when (appHandler!!.bottomSwipe) {
            "None" -> {}
            "Decrease volume" -> audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeToSet, 0)
            "Decrease volume and show UI" -> {
                audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeToSet, 0)
            }
            else -> audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeToSet, 0)
        }
    }

    fun onSingleTap(): Boolean {
        when (appHandler!!.clickAction) {
            "None" -> {}
            "Open volume UI" -> audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            "Mute" -> {
                audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                audioManager.adjustVolume(AudioManager.ADJUST_MUTE, 0)
            }
            "Active Music Overlay" -> createMusicOverlayView()
            "Lock" -> lockScreenUtil?.lockScreen()
            "Hide Handler" -> stopSelfService()
            "Open App" -> openApp()
            else -> {}
        }
        return true
    }

    fun onDoubleTap(): Boolean {
        when (appHandler!!.doubleClickAction) {
            "None" -> {}
            "Open volume UI" -> audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            "Mute" -> {
                audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                audioManager.adjustVolume(AudioManager.ADJUST_MUTE, 0)
            }
            "Active Music Overlay" -> createMusicOverlayView()
            "Lock" -> lockScreenUtil?.lockScreen()
            "Hide Handler" -> stopSelfService()
            "Open App" -> openApp()
            else -> {}
        }
        return true
    }

    fun onLongPress() {
        when (appHandler!!.longClickAction) {
            "None" -> {}
            "Open volume UI" -> audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            "Mute" -> {
                audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                audioManager.adjustVolume(AudioManager.ADJUST_MUTE, 0)
            }
            "Active Music Overlay" -> createMusicOverlayView()
            "Lock" -> lockScreenUtil?.lockScreen()
            "Hide Handler" -> stopSelfService()
            "Open App" -> openApp()
            else -> {}
        }
    }

    private fun stopSelfService() {
        if (handleView != null) {
            windowManager!!.removeView(handleView)
            handleView = null
        }
        try{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            }
            stopSelf()
        } catch (_: IllegalStateException) { }
    }

    private fun openApp() {
        val packageManager = applicationContext.packageManager
        val intent = packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            applicationContext.startActivity(intent)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                actionDownPoint = PointF(event.x, event.y)
                previousPoint = PointF(event.x, event.y)
                eventX1 = event.x
            }
            MotionEvent.ACTION_UP ->  { }
            MotionEvent.ACTION_MOVE -> {
                eventX2 = event.x

                val halfHeight = view.height / 2f
                if (event.y in 0f..halfHeight ) {
                    increaseVolume()
                } else if (event.y in halfHeight..view.height.toFloat()) {
                    decreaseVolume()
                }
                previousPoint = PointF(event.x, event.y)
            }
            else -> {}
        }
        gestureDetector?.onTouchEvent(event)
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (handleView != null) {
            windowManager!!.removeView(handleView)
            handleView = null
        }
        if (musicOverlayView != null) {
            windowManagerMusic!!.removeView(musicOverlayView)
            musicOverlayView = null
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

}

