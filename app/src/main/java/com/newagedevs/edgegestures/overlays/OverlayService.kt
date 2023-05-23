package com.newagedevs.edgegestures.overlays

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
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
import com.newagedevs.edgegestures.extensions.toast
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
        private const val MIN_DISTANCE = 2
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
                        "Slim" -> 20
                        "Regular" -> 30
                        "Bold" -> 40
                        else -> 20
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

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                eventX1 = event.x
            }
            MotionEvent.ACTION_MOVE -> {}
            MotionEvent.ACTION_UP -> {
                eventX2 = event.x
                val deltaX: Float = eventX2 - eventX1
                val deltaY: Float = event.y - event.y

                val minDistance = MIN_DISTANCE

                if (abs(deltaX) < minDistance && abs(deltaY) < minDistance) {
                    v.performClick()
                } else {
                    if (eventX1 != eventX2) {
                        val halfHeight = v.height / 2f
                        if (event.y < halfHeight) {
                            increaseVolume()
                        } else {
                            decreaseVolume()
                        }
                    }
                }
            }
        }

        return true
    }
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

