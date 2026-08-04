package com.newagedevs.gesturevolume.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.data.model.UnlockCondition
import com.newagedevs.gesturevolume.livedata.LiveDataManager
import com.newagedevs.gesturevolume.ui.view.HandlerGestureDetector
import com.newagedevs.gesturevolume.ui.view.HandlerView
import com.newagedevs.gesturevolume.utils.BrightnessController
import com.newagedevs.gesturevolume.utils.HandlerActions
import com.newagedevs.gesturevolume.utils.LockScreenUtil
import com.newagedevs.gesturevolume.utils.safeDrawableIdOrDefault
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
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

    private var overlayView: View? = null
    private var handlerView: HandlerView? = null
    private var handlerParams: WindowManager.LayoutParams? = null
    private var gestureDetector: HandlerGestureDetector? = null

    private var windowManager: WindowManager? = null
    private var audioManager: AudioManager? = null
    private var vibratorService: Vibrator? = null
    private var lockScreenUtil: LockScreenUtil? = null
    private var brightness: BrightnessController? = null

    /** The display frame the handler was last placed into. Refreshed on every geometry pass. */
    private var frame: HandlerGeometry.Frame? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    // ---- swipe-to-adjust state, latched for the duration of one gesture --------------------

    private var adjustIsBrightness = false
    private var adjustShowsUi = false
    private var adjustEnabled = false

    /** Direction the latched swipe settings belong to: `+1` up, `-1` down, `0` nothing resolved. */
    private var adjustDirection = 0

    // ---- drag-to-reposition state ----------------------------------------------------------

    private var dragStartY = 0

    // ---- brightness indicator ---------------------------------------------------------------

    private var indicatorView: View? = null
    private var indicatorLabel: TextView? = null
    private val hideIndicatorRunnable = Runnable { hideIndicator() }

    companion object {
        // v2 channel: low importance (silent, no heads-up). Bumped from the old id so existing
        // installs also move off the intrusive IMPORTANCE_HIGH channel.
        private const val CHANNEL_ID = "gesture_volume_service_v2"
        private const val LEGACY_CHANNEL_ID = "Gesture Volume Channel ID"
        private const val NOTIFICATION_ID = 1
        private const val INDICATOR_VISIBLE_MS = 900L
    }

    private var previousVolume: Int = 1

    // ---- music-overlay touch state (the separate full-screen overlay feature) ---------------

    private val touchMoveFactor: Long by lazy { (20 * resources.displayMetrics.density).toLong() }
    private val touchTimeFactor: Long = 300L
    private val doubleClickTimeDelta: Long = 300L
    private var minSwipeY: Float = 0f
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var actionDownPoint = PointF(0f, 0f)
    private var previousPoint = PointF(0f, 0f)
    private var touchDownTime = 0L
    private var lastClickTime = 0L
    private var isActionMoveEventStored = false
    private var lastActionMoveEventBeforeUpX = 0f
    private var lastActionMoveEventBeforeUpY = 0f
    private var isLongPressHandlerActivated = false
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var eventX1: Float = 0f
    private var eventX2: Float = 0f
    private var startY: Float = 0f
    private val longPressedRunnable = Runnable {
        hideOverlayView()
        createOverlayHandler()
        isLongPressHandlerActivated = true
    }

    /**
     * Rotation is not reliably delivered to a Service through onConfigurationChanged alone on every
     * OEM build, so the display listener is the belt to that braces. Both funnel into one place.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            mainHandler.post { applyHandlerGeometry() }
        }
    }

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        vibratorService = getSystemService(Vibrator::class.java)
        lockScreenUtil = LockScreenUtil(this)
        brightness = BrightnessController(this)

        (getSystemService(DISPLAY_SERVICE) as? DisplayManager)
            ?.registerDisplayListener(displayListener, mainHandler)

        createNotificationChannel()
        startForegroundService()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The rotation fix: recompute placement instead of leaving a stale portrait `y` behind,
        // which is what used to slide the bar down onto the landscape navigation bar.
        applyHandlerGeometry()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // Silent, low-importance channel: the notification is required to keep the foreground
        // service alive, but it should sit quietly in the bar without sound or heads-up.
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Overlay service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
        // Remove the old intrusive channel from app notification settings.
        try {
            manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        } catch (_: Exception) {
            // Channel may not exist; ignore.
        }
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gesture)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_tap_to_manage))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_show, getString(R.string.show), getPendingIntent("show"))
            .addAction(R.drawable.ic_hide, getString(R.string.hide), getPendingIntent("hide"))
            .addAction(R.drawable.ic_power, getString(R.string.stop), getPendingIntent("stop"))
            .build()

        try {
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
        hideIndicator()

        (getSystemService(DISPLAY_SERVICE) as? DisplayManager)
            ?.unregisterDisplayListener(displayListener)

        longPressHandler.removeCallbacks(longPressedRunnable)
        mainHandler.removeCallbacksAndMessages(null)

        // Hand adaptive brightness back if we were the one who turned it off.
        restoreAutoBrightnessIfOurs()

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
                "show" -> {
                    shouldFinish = false
                    createOverlayHandler()
                }
                "hide" -> {
                    hideOverlayView()
                    hideHandlerView()
                }
                "stop" -> {
                    shouldFinish = true
                    preference.setRunning(false)
                    hideOverlayView()
                    hideHandlerView()
                    restoreAutoBrightnessIfOurs()
                    stopForegroundAndSelf()
                }
                "update" -> update()
            }
        } ?: run {
            // Service started without action (including a START_STICKY relaunch): show the handler.
            shouldFinish = false
            createOverlayHandler()
        }
        return START_STICKY
    }

    private fun stopForegroundAndSelf() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun now(): Long = SystemClock.elapsedRealtime()

    // =============================================================================================
    // Handler window
    // =============================================================================================

    private fun createOverlayHandler() {
        if (handlerView == null) {
            val handlerPosition = preference.getHandlerPosition()
            val handlerWidth = preference.getHandlerWidthDp()
            val handlerHeight = preference.getHandlerHeightDp()

            // Appearance settings
            val backgroundColor = preference.getHandlerColor()
            val backgroundAlpha = preference.getHandlerBackgroundAlpha()
            val strokeColor = preference.getHandlerStrokeColor()
            val strokeWidth = preference.getHandlerStrokeWidth()
            val strokeAlpha = preference.getHandlerStrokeAlpha()

            val cornerRadiusTL = preference.getHandlerCornerRadiusTL()
            val cornerRadiusTR = preference.getHandlerCornerRadiusTR()
            val cornerRadiusBL = preference.getHandlerCornerRadiusBL()
            val cornerRadiusBR = preference.getHandlerCornerRadiusBR()

            val iconRes = preference.getHandlerIconRes()
            val iconSize = preference.getHandlerIconSize()
            val iconColor = preference.getHandlerIconColor()
            val showIcon = preference.getHandlerShowIcon()

            val vibrateOnClick = preference.getHandlerVibrateOnClick()

            val gravity = if (handlerPosition == "Left") Gravity.LEFT else Gravity.RIGHT
            val layoutParams = buildHandlerParams(gravity, handlerWidth, handlerHeight)

            val view = HandlerView(this).apply {
                setViewGravity(if (handlerPosition == "Left") Gravity.START else Gravity.END)
                setViewDimensionsDp(handlerWidth, handlerHeight)
                setTranslationYPosition(0f)

                setViewBackgroundColor(backgroundColor, backgroundAlpha)
                setStrokeProperties(strokeColor, strokeWidth, strokeAlpha)
                setCornerRadiiDp(cornerRadiusTL, cornerRadiusTR, cornerRadiusBL, cornerRadiusBR)

                // Resolve through the shared safe-resolver so a stale stored icon id (R.drawable
                // values shift across app updates) falls back to the default instead of throwing.
                val safeDrawable = ContextCompat.getDrawable(
                    this@OverlayService,
                    this@OverlayService.safeDrawableIdOrDefault(iconRes)
                )
                setCenterIcon(safeDrawable, iconSize, iconColor)
                setCenterIconColor(iconColor)
                setCenterIconVisible(showIcon)

                setVibrateOnClick(vibrateOnClick)
            }

            val detector = HandlerGestureDetector(this, gestureHost)
            view.setGestureDetector(detector)

            gestureDetector = detector
            handlerView = view
            handlerParams = layoutParams

            // Resolve the real position BEFORE the window is added. Adding at y = 0 and correcting
            // afterwards makes the bar visibly flash at the top of the screen every time it is
            // recreated — which happens on every settings save and every time the app backgrounds.
            applyHandlerGeometry()

            try {
                windowManager?.addView(view, layoutParams)
            } catch (e: Exception) {
                android.util.Log.e("OverlayService", "addView failed", e)
                handlerView = null
                handlerParams = null
                gestureDetector = null
            }
        }
    }

    @SuppressLint("RtlHardcoded")
    private fun buildHandlerParams(
        absoluteGravity: Int,
        widthDp: Float,
        heightDp: Float
    ): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            dpToPx(widthDp),
            dpToPx(heightDp),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Absolute LEFT/RIGHT, never START/END. The window-frame gravity pass takes no layout
            // direction, so a relative gravity is not resolved — and this app ships an RTL locale
            // (values-ar) with supportsRtl="true", which would put the bar on the wrong side.
            this.gravity = Gravity.TOP or absoluteGravity
            x = dpToPx(preference.getHandlerEdgeMarginDp())
            y = 0

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Shrink the containing frame by the system bars and the cutout, so x = 0 means
                // "flush with the edge the user can actually touch" — in portrait, in landscape
                // where the navigation bar takes a side, and on cutout devices. This is what stops
                // the bar from landing on the navigation bar after a rotation.
                setFitInsetsTypes(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                setFitInsetsSides(
                    WindowInsets.Side.LEFT or WindowInsets.Side.TOP or
                            WindowInsets.Side.RIGHT or WindowInsets.Side.BOTTOM
                )
                isFitInsetsIgnoringVisibility = true
            }
        }

    /**
     * Recomputes the handler's window position from the current display frame.
     *
     * Called on creation, on rotation, on display changes, and after settings are saved. This is
     * the only place that decides where the bar goes.
     */
    private fun applyHandlerGeometry() {
        val params = handlerParams ?: return
        val currentFrame = HandlerGeometry.read(this, windowManager) ?: return
        frame = currentFrame

        val barHeightPx = dpToPx(preference.getHandlerHeightDp())

        preference.migrateHandlerPositionFraction(
            usableHeightPx = currentFrame.usableHeight,
            topInsetPx = currentFrame.insetTop,
            barHeightPx = barHeightPx,
            isPortrait = currentFrame.isPortrait
        )

        params.x = dpToPx(preference.getHandlerEdgeMarginDp())
        params.y = if (preference.hasHandlerPositionFraction()) {
            HandlerGeometry.fractionToY(
                preference.getHandlerPositionFraction(),
                currentFrame.usableHeight,
                barHeightPx
            )
        } else {
            // Only reachable when the service first starts in landscape and the migration deferred.
            val maxY = (currentFrame.usableHeight - barHeightPx).coerceAtLeast(0)
            (preference.getHandlerTranslationY() - currentFrame.insetTop)
                .roundToInt()
                .coerceIn(0, maxY)
        }

        val view = handlerView ?: return
        if (view.isAttachedToWindow) {
            try {
                windowManager?.updateViewLayout(view, params)
            } catch (e: Exception) {
                android.util.Log.e("OverlayService", "updateViewLayout failed", e)
            }
        }
    }

    private fun hideHandlerView() {
        gestureDetector?.cancel()
        handlerView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
                // View already removed
            }
        }
        handlerView = null
        handlerParams = null
        gestureDetector = null
    }

    // =============================================================================================
    // Gestures
    // =============================================================================================

    private val gestureHost = object : HandlerGestureDetector.Host {

        override fun isLongPressReposition(): Boolean =
            preference.getHandlerLongTapAction() == HandlerActions.REPOSITION

        override fun isDoubleTapArmed(): Boolean =
            preference.getHandlerDoubleTapAction() != HandlerActions.NONE

        // Tap actions can tear the handler window down ("Hide Handler", "Open App", the music
        // overlay). Running that inside onTouchEvent would destroy the window from within input
        // dispatch, so every action is posted off the input stack.
        override fun onTap() {
            mainHandler.post { handlerTapActions(preference.getHandlerSingleTapAction()) }
        }

        override fun onDoubleTap() {
            mainHandler.post { handlerTapActions(preference.getHandlerDoubleTapAction()) }
        }

        override fun onLongPress() {
            mainHandler.post { handlerTapActions(preference.getHandlerLongTapAction()) }
        }

        override fun onAdjustBegin(initialDirection: Int) {
            adjustDirection = 0
            resolveAdjustAction(initialDirection)
        }

        override fun onAdjustStep(direction: Int): Boolean {
            // Re-resolved per step, not latched at gesture start: swipe up and swipe down are two
            // independent settings, so reversing mid-gesture has to switch to the other one. Latching
            // meant a swipe that started upward kept driving the swipe-UP action on the way back
            // down — a "Decrease brightness" swipe-down would silently move the volume instead.
            resolveAdjustAction(direction)
            if (!adjustEnabled) return false
            return if (adjustIsBrightness) stepBrightness(direction) else stepVolume(direction)
        }

        override fun onAdjustEnd() {
            adjustEnabled = false
            adjustDirection = 0
        }

        override fun onDragCue(active: Boolean) {
            handlerView?.setDragCue(active)
            if (!active || !preference.getHandlerVibrateOnClick()) return
            // The vibrator rather than View.performHapticFeedback: this view lives in an overlay
            // window, where OEM builds routinely drop view haptics, and this buzz is the only signal
            // that the long press took and the bar is now following the finger.
            vibratorService?.vibrate(
                VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }

        override fun onDragBegin() {
            dragStartY = handlerParams?.y ?: 0
        }

        override fun onDragUpdate(offsetPx: Float) {
            val params = handlerParams ?: return
            val view = handlerView ?: return
            val currentFrame = frame ?: return
            val barHeightPx = params.height
            val maxY = (currentFrame.usableHeight - barHeightPx).coerceAtLeast(0)

            // The WINDOW is moved, not the view. This view is the root of a window sized exactly to
            // the bar, so a translationY would just slide the drawing inside a stationary window
            // and be clipped at its edge.
            params.y = (dragStartY + offsetPx).roundToInt().coerceIn(0, maxY)
            try {
                windowManager?.updateViewLayout(view, params)
            } catch (_: Exception) {
                // Window went away mid-drag.
            }
        }

        override fun onDragEnd(moved: Boolean) {
            if (!moved) return
            val params = handlerParams ?: return
            val currentFrame = frame ?: return
            preference.setHandlerPositionFraction(
                HandlerGeometry.yToFraction(params.y, currentFrame.usableHeight, params.height)
            )
        }
    }

    /**
     * Loads the swipe settings for [direction] — which domain it drives, whether it shows the system
     * volume panel, whether it is switched off at all — and sizes one step accordingly.
     *
     * Cheap and idempotent: it returns immediately while the direction is unchanged, so calling it
     * on every emitted step costs one comparison for all but the reversals.
     */
    private fun resolveAdjustAction(direction: Int) {
        if (direction == adjustDirection || direction == 0) return
        adjustDirection = direction

        val action = if (direction > 0) {
            preference.getHandlerSwipeUpAction()
        } else {
            preference.getHandlerSwipeDownAction()
        }

        adjustIsBrightness = HandlerActions.isBrightnessSwipe(action)
        adjustShowsUi = HandlerActions.showsVolumeUi(action)
        adjustEnabled = !HandlerActions.isDisabled(action)
        if (!adjustEnabled) return

        if (adjustIsBrightness) {
            val controller = brightness
            if (controller == null || !controller.canWrite()) {
                adjustEnabled = false
                mainHandler.post {
                    showIndicatorMessage(getString(R.string.brightness_needs_permission_short))
                }
                return
            }
            if (controller.disableAutoBrightnessIfNeeded()) {
                preference.setBrightnessAutoWasOn(true)
            }
            gestureDetector?.setStepCount(controller.stepCount)
        } else {
            val steps = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
            gestureDetector?.setStepCount(steps)
        }
    }

    private fun stepVolume(direction: Int): Boolean {
        val manager = audioManager ?: return false
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val before = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val target = (before + direction).coerceIn(0, max)
        if (target == before) return false

        manager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            target,
            if (adjustShowsUi) AudioManager.FLAG_SHOW_UI else 0
        )
        return true
    }

    private fun stepBrightness(direction: Int): Boolean {
        val controller = brightness ?: return false
        val fraction = controller.step(direction) ?: return false
        showIndicatorMessage(getString(R.string.brightness_percent, (fraction * 100).roundToInt()))
        return true
    }

    private fun restoreAutoBrightnessIfOurs() {
        if (!preference.getBrightnessAutoWasOn()) return
        // Only forget the flag once the setting actually went back, so a failed write (no
        // permission, OEM ROM silently dropping it) does not leave the user stuck on manual.
        if (brightness?.setAutoBrightness(true) == true) {
            preference.setBrightnessAutoWasOn(false)
        }
    }

    // =============================================================================================
    // Brightness indicator
    // =============================================================================================

    /**
     * A small transient readout, because unlike volume there is no system UI for brightness — the
     * user would otherwise be adjusting it blind.
     */
    private fun showIndicatorMessage(text: String) {
        val wm = windowManager ?: return
        mainHandler.removeCallbacks(hideIndicatorRunnable)

        if (indicatorView == null) {
            val density = resources.displayMetrics.density
            val padH = (20 * density).toInt()
            val padV = (14 * density).toInt()

            val label = TextView(this).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(padH, padV, padH, padV)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 18 * density
                    setColor(Color.argb(220, 24, 24, 24))
                }
            }
            val container = FrameLayout(this).apply { addView(label) }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            try {
                wm.addView(container, params)
            } catch (e: Exception) {
                android.util.Log.e("OverlayService", "indicator addView failed", e)
                return
            }
            indicatorView = container
            indicatorLabel = label
        }

        indicatorLabel?.text = text
        indicatorView?.visibility = View.VISIBLE
        mainHandler.postDelayed(hideIndicatorRunnable, INDICATOR_VISIBLE_MS)
    }

    private fun hideIndicator() {
        mainHandler.removeCallbacks(hideIndicatorRunnable)
        indicatorView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
                // Already gone.
            }
        }
        indicatorView = null
        indicatorLabel = null
    }

    // =============================================================================================
    // Tap actions
    // =============================================================================================

    private fun handlerTapActions(action: String) {
        // Reposition is armed by the gesture engine itself and never runs as an action; reaching
        // here with it would only buzz the phone for nothing.
        if (action == HandlerActions.REPOSITION) return
        if (preference.getHandlerVibrateOnClick()) {
            vibratorService?.vibrate(
                VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
        when (action) {
            HandlerActions.NONE -> {}
            HandlerActions.OPEN_VOLUME_UI -> {
                audioManager?.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            }
            HandlerActions.MUTE -> {
                audioManager?.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                audioManager?.adjustVolume(AudioManager.ADJUST_MUTE, 0)
            }
            HandlerActions.MUTE_OR_UNMUTE -> {
                val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                audioManager?.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)

                if (currentVolume > 0) {
                    previousVolume = currentVolume
                    audioManager?.adjustVolume(AudioManager.ADJUST_MUTE, 0)
                } else {
                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
                }
            }
            HandlerActions.TOGGLE_AUTO_BRIGHTNESS -> toggleAutoBrightness()
            HandlerActions.ACTIVE_MUSIC_OVERLAY -> {
                hideHandlerView()
                createOverlayView()
            }
            HandlerActions.LOCK -> lockScreenUtil?.lockScreen()
            HandlerActions.HIDE_HANDLER -> hideHandlerView()
            HandlerActions.OPEN_APP -> openApp()
        }
    }

    private fun toggleAutoBrightness() {
        val controller = brightness ?: return
        if (!controller.canWrite()) {
            showIndicatorMessage(getString(R.string.brightness_needs_permission_short))
            return
        }
        val turningOn = !controller.isAutoBrightnessOn()
        if (controller.setAutoBrightness(turningOn)) {
            // Once the user asks for adaptive brightness explicitly, it is theirs again.
            if (turningOn) preference.setBrightnessAutoWasOn(false)
            showIndicatorMessage(
                getString(
                    if (turningOn) R.string.auto_brightness_on else R.string.auto_brightness_off
                )
            )
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

    // =============================================================================================
    // Full-screen music overlay (separate feature, unchanged behaviour)
    // =============================================================================================

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

            val overlayViewHolder =
                overlayView?.findViewById<ConstraintLayout>(R.id.overlay_view_holder)
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
                    // `flags =` here used to clobber the flags set above, making the window
                    // focusable and touch-modal.
                    flags = flags or
                            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                }
            }

            try {
                windowManager?.addView(overlayView, layoutParams)
            } catch (e: Exception) {
                android.util.Log.e("OverlayService", "music overlay addView failed", e)
                overlayView = null
            }
        }
    }

    private fun handleOverlayTouchEvent(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressHandler.postDelayed(longPressedRunnable, 500L)
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
            MotionEvent.ACTION_UP -> handleOverlayUp(event)
            MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressedRunnable)
                isActionMoveEventStored = false
                isLongPressHandlerActivated = false
                false
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

            if (abs(distanceY) > abs(event.x - lastX) && abs(minSwipeY) > 30) {
                val manager = audioManager
                if (manager != null) {
                    val maxVol = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val newVolume = (if (distanceY > 0) current - 1 else current + 1)
                        .coerceIn(0, maxVol)
                    if (newVolume != current) {
                        manager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            newVolume,
                            AudioManager.FLAG_SHOW_UI
                        )
                    }
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

    private fun hideOverlayView() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
                // View already removed
            }
        }
        overlayView = null
    }

    // =============================================================================================
    // OverlayServiceInterface
    // =============================================================================================

    override fun show() {
        shouldFinish = false
        createOverlayHandler()
    }

    override fun hide() {
        hideOverlayView()
        hideHandlerView()
    }

    override fun update() {
        // Rebuild the handler so new appearance settings take effect.
        hideHandlerView()
        createOverlayHandler()
    }

    // The service's own resources, not Resources.getSystem(): only these follow the current
    // display configuration, so only these give a correct density after a rotation.
    private fun dpToPx(dp: Float): Int =
        (dp * resources.displayMetrics.density).toInt()
}
