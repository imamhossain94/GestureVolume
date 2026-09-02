package com.newagedevs.gesturevolume.service

import android.annotation.SuppressLint
import android.app.Notification
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
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.data.model.UnlockCondition
import com.newagedevs.gesturevolume.livedata.LiveDataManager
import com.newagedevs.gesturevolume.ui.view.HandlerGestureDetector
import com.newagedevs.gesturevolume.ui.view.HandlerView
import com.newagedevs.gesturevolume.utils.BrightnessController
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog
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

    /** Where the bar sat when the drag began, in the window's absolute (`Gravity.LEFT`) space. */
    private var dragStartX = 0
    private var dragStartY = 0

    /** The edge offset in pixels, read once per drag rather than on every move frame. */
    private var dragEdgeMarginPx = 0

    /**
     * Which side the bar is currently dressed for, or null when it has not been decided yet.
     *
     * Cached so the dressing is only re-applied when it actually changes. `setViewGravity`
     * rebuilds the background drawable and requests a layout pass; running it on every move frame
     * of a drag is a new GradientDrawable per frame.
     */
    private var handlerDressedLeft: Boolean? = null

    // ---- long-press context menu ---------------------------------------------------------------

    private var contextMenuView: View? = null

    // ---- brightness indicator ---------------------------------------------------------------

    private var indicatorView: View? = null
    private var indicatorLabel: TextView? = null
    private val hideIndicatorRunnable = Runnable { hideIndicator() }

    private val clearVolumePercentRunnable = Runnable { handlerView?.setVolumePercent(null) }

    companion object {
        // v2 channel: low importance (silent, no heads-up). Bumped from the old id so existing
        // installs also move off the intrusive IMPORTANCE_HIGH channel.
        private const val CHANNEL_ID = "gesture_volume_service_v2"
        private const val LEGACY_CHANNEL_ID = "Gesture Volume Channel ID"
        private const val NOTIFICATION_ID = 1
        private const val INDICATOR_VISIBLE_MS = 900L

        /** Long enough to read as movement, short enough not to delay the next gesture. */
        private const val ANIM_DURATION_MS = 180L

        /** How long the level lingers on the bar after the last step of a swipe. */
        private const val VOLUME_PERCENT_VISIBLE_MS = 700L

        /** Menu chrome. Fixed dark surface: the overlay has no theme of its own to follow. */
        private val MENU_SURFACE = Color.argb(247, 30, 30, 34)
        private val MENU_STROKE = Color.argb(38, 255, 255, 255)
        private val MENU_ON_SURFACE = Color.argb(240, 255, 255, 255)

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
        // Android will not run a foreground service without a notification, so this cannot be
        // removed outright. It is instead left unpostable: the app does not declare
        // POST_NOTIFICATIONS, and on Android 13+ an ungranted notification permission keeps a
        // foreground service's notification out of the shade and the status bar entirely while
        // the service keeps running. Verified on a physical Android 16 device — isForeground
        // stays true with zero NotificationRecords.
        //
        // Where it *is* visible — Android 12 and below, and any install where the user has turned
        // notifications on for this app themselves — it now carries the controls that make it
        // worth having: a bar put away with "Hide handler" can be brought back without opening
        // the app, which is the one thing hiding it used to leave no route back from.
        val hidden = preference.isHandlerHidden()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
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
                // "show"/"hide" are the *transient* pair: the app sends them as it comes to the
                // foreground and leaves again, and the boot/update receiver sends "show" too.
                // None of that is the user asking for the bar, so neither touches the hidden
                // state — otherwise closing the app would resurrect a bar put away on purpose.
                "show" -> show()
                "hide" -> hide()
                // The deliberate pair, from the notification's buttons. These are the user
                // speaking, so they do change it.
                "user_show" -> showByUser()
                "user_hide" -> hideByUser()
                "stop" -> stopServiceEntirely()
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

    /**
     * Builds the handler window, unless the user has put the bar away.
     *
     * The hidden state is a *preference*, not just the absence of a window, because almost
     * everything that touches this service ends up here: a settings save rebuilds the handler, a
     * `START_STICKY` relaunch recreates it, and so does the restart after the task is swiped out
     * of Recents. Without somewhere durable to record "the user hid this", every one of those
     * brought the bar back on its own — which is exactly the "it reappears by itself" report.
     * Only an explicit Show — from the app, from the notification, or from the service command —
     * clears it.
     */
    private fun createOverlayHandler() {
        if (preference.isHandlerHidden()) return
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

            val layoutParams = buildHandlerParams(handlerWidth, handlerHeight)

            handlerDressedLeft = handlerPosition == "Left"
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
            // TOP|LEFT is the origin for an absolute coordinate pair, not a side: since the bar
            // can now rest anywhere, x and y are always literal offsets from the top-left of the
            // usable frame. LEFT rather than START because the window-frame gravity pass takes no
            // layout direction, so a relative gravity is never resolved — and this app ships an
            // RTL locale (values-ar) with supportsRtl="true", which would flip the origin.
            this.gravity = Gravity.TOP or Gravity.LEFT
            x = 0
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
     * Writes the bar's current place into this orientation's stored fractions.
     *
     * Per orientation, because portrait and landscape keep independent positions — see
     * [com.newagedevs.gesturevolume.data.local.SharedPref.getHandlerPosXFraction]. The Left/Right
     * value is kept in step as well; it is no longer a setting the user picks, only a record of
     * which side the bar is nearer, which is what decides the way its flat edge faces.
     */
    private fun persistPosition() {
        val params = handlerParams ?: return
        val currentFrame = frame ?: return
        val isPortrait = currentFrame.isPortrait

        preference.setHandlerPosXFraction(
            isPortrait,
            HandlerGeometry.xToFraction(params.x, currentFrame.usableWidth, params.width)
        )
        preference.setHandlerPosYFraction(
            isPortrait,
            HandlerGeometry.yToFraction(params.y, currentFrame.usableHeight, params.height)
        )
        preference.setHandlerPosition(
            if (HandlerGeometry.xToIsLeft(params.x, currentFrame.usableWidth, params.width)) {
                "Left"
            } else {
                "Right"
            }
        )
    }

    /**
     * Points the bar's flat edge and icon at the nearer screen edge.
     *
     * Idempotent by design: `setViewGravity` rebuilds the background drawable and requests a
     * layout, so this is guarded rather than called blindly from the per-frame drag path.
     */
    private fun dressHandlerFor(isLeft: Boolean) {
        if (handlerDressedLeft == isLeft) return
        handlerDressedLeft = isLeft
        handlerView?.setViewGravity(if (isLeft) Gravity.START else Gravity.END)
    }

    private fun updateHandlerLayout(params: WindowManager.LayoutParams) {
        val view = handlerView ?: return
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (_: Exception) {
            // Window went away mid-drag.
        }
    }

    /**
     * Recomputes the handler's window position from the current display frame.
     *
     * Called on creation, on rotation, on display changes, and after settings are saved. This is
     * the only place that decides where the bar goes.
     */
    @SuppressLint("RtlHardcoded")
    private fun applyHandlerGeometry() {
        val params = handlerParams ?: return
        val currentFrame = HandlerGeometry.read(this, windowManager) ?: return
        frame = currentFrame

        val barHeightPx = dpToPx(preference.getHandlerHeightDp())
        val barWidthPx = dpToPx(preference.getHandlerWidthDp())
        val isPortrait = currentFrame.isPortrait

        // Legacy vertical migration first: the free-position seed below reads the fraction it
        // writes, so the order here is what lets an upgrading user keep the height they had.
        preference.migrateHandlerPositionFraction(
            usableHeightPx = currentFrame.usableHeight,
            topInsetPx = currentFrame.insetTop,
            barHeightPx = barHeightPx,
            isPortrait = isPortrait
        )

        // First time in this orientation, place the bar where the old Left/Right setting had it —
        // edge margin included — rather than at a default the user never chose.
        if (!preference.hasHandlerPosXFraction(isPortrait) ||
            !preference.hasHandlerPosYFraction(isPortrait)
        ) {
            val legacyX = HandlerGeometry.sideToX(
                isLeft = preference.getHandlerPosition() == "Left",
                usableWidth = currentFrame.usableWidth,
                barWidth = barWidthPx,
                edgeMarginPx = dpToPx(preference.getHandlerEdgeMarginDp())
            )
            preference.migrateHandlerFreePosition(
                isPortrait,
                HandlerGeometry.xToFraction(legacyX, currentFrame.usableWidth, barWidthPx)
            )
        }

        // Clamped as well as converted: the edge offset can have been raised since the bar was
        // last put down, and it is a promise about the gap on both sides rather than only a
        // starting position.
        params.x = HandlerGeometry.clampX(
            HandlerGeometry.fractionToX(
                preference.getHandlerPosXFraction(isPortrait),
                currentFrame.usableWidth,
                barWidthPx
            ),
            currentFrame.usableWidth,
            barWidthPx,
            dpToPx(preference.getHandlerEdgeMarginDp())
        )
        params.y = HandlerGeometry.fractionToY(
            preference.getHandlerPosYFraction(isPortrait),
            currentFrame.usableHeight,
            barHeightPx
        )

        // Which way the bar dresses follows where it ended up, not a stored side.
        dressHandlerFor(HandlerGeometry.xToIsLeft(params.x, currentFrame.usableWidth, barWidthPx))

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
        // The menu is anchored to a bar that is about to stop existing, and the pending clear
        // would fire against a view that is gone.
        hideContextMenu()
        mainHandler.removeCallbacks(clearVolumePercentRunnable)
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
        // The next bar is a new view with its own dressing; a stale cache here would skip the
        // setViewGravity that gives it one.
        handlerDressedLeft = null
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
            val params = handlerParams
            dragStartX = params?.x ?: 0
            dragStartY = params?.y ?: 0
            dragEdgeMarginPx = dpToPx(preference.getHandlerEdgeMarginDp())
        }

        override fun onDragUpdate(offsetXPx: Float, offsetYPx: Float) {
            val params = handlerParams ?: return
            val currentFrame = frame ?: return
            val maxY = (currentFrame.usableHeight - params.height).coerceAtLeast(0)

            // The WINDOW is moved, not the view. This view is the root of a window sized exactly to
            // the bar, so a translation would just slide the drawing inside a stationary window
            // and be clipped at its edge.
            params.y = (dragStartY + offsetYPx).roundToInt().coerceIn(0, maxY)
            // Horizontal is clamped by the edge offset rather than by the bare frame, so the
            // finger cannot push the bar into the gesture strip the setting exists to keep it out
            // of — and a bar shoved at a side comes to rest exactly on that offset.
            params.x = HandlerGeometry.clampX(
                (dragStartX + offsetXPx).roundToInt(),
                currentFrame.usableWidth,
                params.width,
                dragEdgeMarginPx
            )
            updateHandlerLayout(params)

            // Dressed for the nearer side *during* the drag, not on release: carrying the bar
            // across the screen and having it change costume only once the finger lifts reads as
            // a second, unasked-for movement.
            dressHandlerFor(
                HandlerGeometry.xToIsLeft(params.x, currentFrame.usableWidth, params.width)
            )
        }

        /**
         * The bar stays exactly where it was let go — always, on both axes, with no setting in
         * between. Placement is free; the only thing that constrains it is the edge offset, which
         * was already applied on every frame of the drag.
         */
        override fun onDragEnd(moved: Boolean) {
            if (!moved) return
            persistPosition()
        }

        override fun onContextMenuOpen() {
            mainHandler.post { showContextMenu() }
        }

        override fun onContextMenuDismiss() {
            // The finger has committed to a drag, so the menu it was also offered gets out of the
            // way. Nothing takes its place: hiding the bar is a menu entry, not a target that has
            // to sit on screen covering whatever the user is dragging over.
            mainHandler.post { hideContextMenu() }
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
        showVolumePercentOnHandler(target, max)
        return true
    }

    /**
     * Puts the level on the bar itself while a swipe is moving it.
     *
     * Only while adjusting: the number is already in hand at this point, so it costs nothing,
     * whereas keeping it permanently accurate would mean listening for every volume change the
     * rest of the system makes — hardware keys, other apps — for a readout nobody is looking at.
     */
    private fun showVolumePercentOnHandler(volume: Int, max: Int) {
        if (!preference.getShowVolumePercent()) return
        if (max <= 0) return
        val view = handlerView ?: return
        mainHandler.removeCallbacks(clearVolumePercentRunnable)
        view.setVolumePercent((volume * 100f / max).roundToInt())
        mainHandler.postDelayed(clearVolumePercentRunnable, VOLUME_PERCENT_VISIBLE_MS)
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
    // Long-press context menu
    // =============================================================================================

    /**
     * Opens the menu the long press armed, beside the handler.
     *
     * Does nothing when the user has emptied the menu in settings — holding then simply moves the
     * bar, which is the behaviour that shipped before this menu existed.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun showContextMenu() {
        val wm = windowManager ?: return
        val params = handlerParams ?: return
        val currentFrame = frame ?: return
        if (contextMenuView != null) return

        val entries = HandlerActionCatalog.contextMenuEntries(preference.getContextMenuItems())
        if (entries.isEmpty()) return

        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val card = LinearLayout(this).apply {
            // WRAP_CONTENT explicitly. FrameLayout.generateDefaultLayoutParams() is MATCH_PARENT
            // on both axes, so a child added without params fills the window — which for a
            // full-screen host window means a menu the size of the screen.
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18 * density
                setColor(MENU_SURFACE)
                setStroke(dp(1f), MENU_STROKE)
            }
            elevation = 12 * density
            clipToOutline = true
        }

        entries.forEach { entry ->
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14f), dp(12f), dp(18f), dp(12f))
                isClickable = true
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 12 * density
                    setColor(Color.TRANSPARENT)
                }
                addView(ImageView(this@OverlayService).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(20f), dp(20f)).apply {
                        marginEnd = dp(14f)
                    }
                    setImageDrawable(
                        ContextCompat.getDrawable(this@OverlayService, entry.iconRes)
                            ?.mutate()
                            ?.let {
                                DrawableCompat.wrap(it)
                                    .apply { DrawableCompat.setTint(this, MENU_ON_SURFACE) }
                            }
                    )
                })
                addView(TextView(this@OverlayService).apply {
                    text = getString(entry.labelRes)
                    setTextColor(MENU_ON_SURFACE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    maxLines = 1
                })
                setOnClickListener {
                    hideContextMenu()
                    // Posted for the same reason the tap actions are: "Hide Handler" and
                    // "Open App" destroy windows, and doing that from inside a click dispatch
                    // tears down a view hierarchy that is still being walked.
                    mainHandler.post { handlerTapActions(entry.action) }
                }
            }
            card.addView(row)
        }

        // A full-screen root rather than a card-sized window: it is what catches the tap outside
        // the menu that dismisses it. The window is not focusable, so there is no back-button
        // route to close it and an outside tap is the only way out.
        val root = FrameLayout(this).apply {
            addView(card)
            setOnClickListener { hideContextMenu() }
        }

        val menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                setFitInsetsSides(
                    WindowInsets.Side.LEFT or WindowInsets.Side.TOP or
                            WindowInsets.Side.RIGHT or WindowInsets.Side.BOTTOM
                )
                isFitInsetsIgnoringVisibility = true
            }
        }

        try {
            wm.addView(root, menuParams)
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "context menu addView failed", e)
            return
        }
        contextMenuView = root

        // Placed after the first measure pass, when the card's real size is known. Guessing at it
        // beforehand puts a tall menu off the bottom of the screen on a bar dragged low.
        card.post {
            val gap = dp(10f)
            val lp = card.layoutParams as FrameLayout.LayoutParams
            val frameW = currentFrame.usableWidth
            val frameH = currentFrame.usableHeight

            // Prefer the side of the bar with room for the menu; fall back to the other, then to
            // whatever fits. A bar dragged to the middle of the screen has room on both sides, so
            // the nearer screen edge decides and the menu opens away from it.
            val spaceRight = frameW - (params.x + params.width)
            val spaceLeft = params.x
            val openRight = when {
                spaceRight >= card.width + gap * 2 && spaceLeft >= card.width + gap * 2 ->
                    params.x + params.width / 2 < frameW / 2
                spaceRight >= card.width + gap * 2 -> true
                spaceLeft >= card.width + gap * 2 -> false
                else -> spaceRight >= spaceLeft
            }

            lp.leftMargin = if (openRight) {
                params.x + params.width + gap
            } else {
                params.x - card.width - gap
            }.coerceIn(gap, (frameW - card.width - gap).coerceAtLeast(gap))

            // Centred on the bar vertically, then pulled back inside the frame.
            lp.topMargin = (params.y + params.height / 2 - card.height / 2)
                .coerceIn(gap, (frameH - card.height - gap).coerceAtLeast(gap))

            card.layoutParams = lp

            // Grow out of the bar rather than appearing whole: the pivot sits on the edge the
            // menu is anchored to, so it reads as belonging to the handler.
            card.pivotX = if (openRight) 0f else card.width.toFloat()
            card.pivotY = (card.height / 2).toFloat()
            card.scaleX = 0.85f
            card.scaleY = 0.85f
            card.alpha = 0f
            card.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(ANIM_DURATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun hideContextMenu() {
        contextMenuView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
                // Already gone.
            }
        }
        contextMenuView = null
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
            HandlerActions.LOCK -> {
                // Neither lock route granted. Silence here read as the bar being broken; the
                // permission can be revoked from system settings long after the action was set.
                if (lockScreenUtil?.lockScreen() != true) {
                    showIndicatorMessage(getString(R.string.lock_not_available))
                }
            }
            HandlerActions.HIDE_HANDLER -> {
                hideByUser()
                showIndicatorMessage(getString(R.string.handler_hidden_toast))
            }
            HandlerActions.STOP_SERVICE -> stopServiceEntirely()
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

    /** The user asked for the bar back: clear the hidden state, then show it. */
    private fun showByUser() {
        preference.setHandlerHidden(false)
        show()
        // The notification's first button swaps between Show and Hide, so it is reposted whenever
        // which one applies changes. Same id, so this replaces it rather than adding a second.
        startForegroundService()
    }

    /** The user put the bar away: it stays away until they say otherwise. */
    private fun hideByUser() {
        preference.setHandlerHidden(true)
        hide()
        startForegroundService()
    }

    private fun stopServiceEntirely() {
        shouldFinish = true
        preference.setRunning(false)
        // Stopping is not hiding: the bar should be there again the next time the service is
        // started, or the user would turn it on and get nothing.
        preference.setHandlerHidden(false)
        hideOverlayView()
        hideHandlerView()
        restoreAutoBrightnessIfOurs()
        stopForegroundAndSelf()
    }

    override fun update() {
        // Rebuild the handler so new appearance settings take effect.
        hideHandlerView()
        createOverlayHandler()
        // The notification's action buttons are a setting too, and startForeground on the same id
        // replaces the existing notification in place rather than posting a second one.
        startForegroundService()
    }

    // The service's own resources, not Resources.getSystem(): only these follow the current
    // display configuration, so only these give a correct density after a rotation.
    private fun dpToPx(dp: Float): Int =
        (dp * resources.displayMetrics.density).toInt()
}
