package com.newagedevs.edgegestures.overlays

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.view.View.OnTouchListener
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.newagedevs.edgegestures.R
import com.newagedevs.edgegestures.model.AppHandler
import com.newagedevs.edgegestures.persistence.AppDatabase
import kotlinx.coroutines.*
import kotlin.math.abs


class OverlayService : Service(), OnTouchListener, View.OnClickListener {


    companion object {
        private const val CHANNEL_ID = "channel1"
        private const val CHANNEL_NAME = "Overlay notification"
        private const val TITLE = "Overlay notification"
        private const val CONTENT = "Overlay notification"
        private const val TOUCH_MOVE_FACTOR = 5
        private const val TOUCH_TIME_FACTOR = 200
        var running = false
    }

    private lateinit var audioManager: AudioManager
    private lateinit var appDatabase: AppDatabase
    private var appHandler: AppHandler? = null

    private var windowManager: WindowManager? = null
    private val looper = Handler(Looper.getMainLooper())

    private var handleView: ImageView? = null

    private var eventX1: Float = 0f
    private var eventX2: Float = 0f



    private var actionDownPoint = PointF(0f, 0f)
    private var previousPoint = PointF(0f, 0f)
    private var touchDownTime = 0L

    override fun onCreate() {
        super.onCreate()
        appDatabase = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            getString(R.string.database)
        ).build()
        running = true
    }

    private suspend fun getAppHandler(): AppHandler? {
        return withContext(Dispatchers.IO) {
            appDatabase.handlerDao().getHandler()
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(TITLE)
                .setContentText(CONTENT)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()

            startForeground(1, notification)
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        CoroutineScope(Dispatchers.IO).launch {
            appHandler = getAppHandler()
            looper.post {

                if(appHandler != null){
                    val height: Int = when (appHandler!!.size) {
                        "Small" -> 200
                        "Medium" -> 345
                        "Large" -> 490
                        else -> 200
                    }

                    val width: Int = when (appHandler!!.width) {
                        "Slim" -> 30
                        "Regular" -> 40
                        "Bold" -> 50
                        else -> 30
                    }

                    val type =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        else WindowManager.LayoutParams.TYPE_PHONE

                    val params = WindowManager.LayoutParams(
                        width, height, type,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT
                    )

                    val drawable = when(appHandler!!.gravity) {
                        "Left" -> {
                            params.gravity = Gravity.START or Gravity.TOP
                            ContextCompat.getDrawable(this@OverlayService, R.drawable.item_handler_left)
                        }
                        "Right" -> {
                            params.gravity = Gravity.END or Gravity.TOP
                            ContextCompat.getDrawable(this@OverlayService, R.drawable.item_handler_right)
                        }
                        else -> {
                            params.gravity = Gravity.END or Gravity.TOP
                            ContextCompat.getDrawable(this@OverlayService, R.drawable.item_handler_right)
                        }
                    }

                    handleView = ImageView(this@OverlayService)
                    handleView!!.background = drawable
                    handleView!!.setOnClickListener(this@OverlayService)
                    handleView!!.setOnTouchListener(this@OverlayService)

                    handleView!!.background.colorFilter = PorterDuffColorFilter(appHandler!!.color ?: try {
                        Color.parseColor("#47000000")
                    } catch (e: IllegalArgumentException) {
                        Color.BLACK
                    }, PorterDuff.Mode.MULTIPLY)

                    //params.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    params.x = 0
                    params.y = appHandler!!.topMargin!!.toInt()
                    windowManager!!.addView(handleView!!, params)
                }

            }
        }

        return START_NOT_STICKY
    }

    override fun onTouch(view: View, event: MotionEvent) = when (event.action) {

        MotionEvent.ACTION_DOWN -> {
            actionDownPoint = PointF(event.x, event.y)
            previousPoint = PointF(event.x, event.y)
            touchDownTime = now()
            eventX1 = event.x

            true
        }

        MotionEvent.ACTION_UP ->  {

            val isTouchDuration = now() - touchDownTime < TOUCH_TIME_FACTOR
            val isTouchLength = abs(event.x - actionDownPoint.x) + abs(event.y - actionDownPoint.y) < TOUCH_MOVE_FACTOR
            val shouldClick = isTouchLength && isTouchDuration

            if (shouldClick) view.performClick()

            /* Do other stuff related to ACTION_UP you may whant here */

            true
        }

        MotionEvent.ACTION_MOVE -> {

            eventX2 = event.x

            val halfHeight = view.height / 2f
            if (event.y in 0f..halfHeight ) {
                increaseVolume()
            } else if (event.y in halfHeight..view.height.toFloat()) {
                decreaseVolume()
            }

            previousPoint = PointF(event.x, event.y)
            true
        }

        else -> false
    }

    private fun now() = System.currentTimeMillis()

    private fun increaseVolume() {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val increasedVolume = currentVolume + 1
        val volumeToSet = if (increasedVolume <= maxVolume) increasedVolume else maxVolume
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeToSet, 0)
        //toast("(+)Volume: $volumeToSet")
    }

    private fun decreaseVolume() {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val decreasedVolume = currentVolume - 1
        val volumeToSet = if (decreasedVolume >= 0) decreasedVolume else 0
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeToSet, 0)
        //toast("(-)Volume: $volumeToSet")
    }

    override fun onClick(v: View) {
        audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (handleView != null) {
            windowManager!!.removeView(handleView)
            handleView = null
        }
        running = false
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

}

