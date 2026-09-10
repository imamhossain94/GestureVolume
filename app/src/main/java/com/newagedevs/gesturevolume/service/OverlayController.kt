package com.newagedevs.gesturevolume.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
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
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.QuickSliderStore
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.overlay.ContextMenuOverlay
import com.newagedevs.gesturevolume.overlay.OverlayComposeHost
import com.newagedevs.gesturevolume.overlay.OverlayTheme
import com.newagedevs.gesturevolume.overlay.deck.AppShortcut
import com.newagedevs.gesturevolume.overlay.deck.DeckActions
import com.newagedevs.gesturevolume.overlay.deck.DeckConfig
import com.newagedevs.gesturevolume.overlay.deck.DeckEnvironment
import com.newagedevs.gesturevolume.overlay.deck.DeckModel
import com.newagedevs.gesturevolume.overlay.deck.DeckOverlay
import com.newagedevs.gesturevolume.overlay.deck.DeckRootView
import com.newagedevs.gesturevolume.overlay.deck.DeckState
import com.newagedevs.gesturevolume.overlay.deck.DeckTiles
import com.newagedevs.gesturevolume.data.local.QuickDialEntry
import com.newagedevs.gesturevolume.ui.activities.MainActivity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.newagedevs.gesturevolume.ui.view.HandlerGestureDetector
import com.newagedevs.gesturevolume.ui.view.HandlerView
import com.newagedevs.gesturevolume.ui.view.QuickSliderView
import com.newagedevs.gesturevolume.utils.AudioStreamCatalog
import com.newagedevs.gesturevolume.utils.AudioStreamResolver
import com.newagedevs.gesturevolume.utils.BrightnessController
import com.newagedevs.gesturevolume.utils.DeviceToggles
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog
import com.newagedevs.gesturevolume.utils.HandlerActions
import com.newagedevs.gesturevolume.utils.VolumeController
import com.newagedevs.gesturevolume.utils.safeDrawableIdOrDefault
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The overlay itself: the bar, its gestures, the long-press menu, the readouts, the music
 * overlay and the Deck. Everything that is drawn over other apps and everything that happens
 * when it is touched.
 *
 * Extracted from `OverlayService` in 1.4.0 so that two hosts can share it. The foreground service
 * owns a notification and the accessibility service owns nothing, but the bar they draw is the
 * same bar, and a second copy of fifteen hundred lines of gesture and geometry code is how the
 * two would drift apart by the second release. The only things that differ between the hosts
 * are the [windowType] their windows use and what happens when the user asks to stop — which is
 * exactly what the constructor takes.
 *
 * Every window this creates uses [windowType]: `TYPE_APPLICATION_OVERLAY` from the foreground
 * service, which needs the overlay permission, or `TYPE_ACCESSIBILITY_OVERLAY` from the
 * accessibility service, which does not.
 */
class OverlayController(
    private val context: Context,
    private val windowType: Int,
    private val preference: SharedPref,
    private val host: Host
) {

    interface Host {
        /**
         * Something the notification shows — the Show/Hide button, the hidden line — has changed.
         * The foreground service re-posts; the accessibility service has no notification.
         */
        fun onNotificationStateChanged()

        /** The user asked, from the bar, for the whole thing to stop. */
        fun onStopRequested()
    }

    companion object {
        private const val TAG = "OverlayController"
        private const val INDICATOR_VISIBLE_MS = 900L

        /** Long enough to read as movement, short enough not to delay the next gesture. */
        private const val ANIM_DURATION_MS = 180L

        /** How long the level lingers on the bar after the last step of a swipe. */
        private const val VOLUME_PERCENT_VISIBLE_MS = 700L

        /**
         * The narrowest the bar's *window* is allowed to be, however thin the bar is drawn.
         *
         * The Default bar is 10dp wide. A 10dp touch target is a fifth of Android's own 48dp
         * minimum, and on the Default bar it is also pressed against the screen edge, where the
         * curve of the glass and the system's own palm rejection eat into it further. Holding
         * still on it long enough for a long press, without drifting the 8dp of touch slop that
         * turns the hold into a swipe, is genuinely hard — which is what "hard to reposition with
         * long press drag and drop" was.
         *
         * So the window is widened inward and the bar is drawn at its configured width against the
         * outer edge of it. Nothing about the bar's appearance changes; there is simply more of it
         * to hit. 28dp is the compromise: nearly three times the Default bar's target, and a strip
         * narrow enough that it does not noticeably eat into the app underneath — the same trade
         * every edge launcher makes, and the reason they all feel easier to grab than they look.
         *
         * A bar already wider than this keeps its own width: the maximum, never a replacement.
         */
        private const val MIN_TOUCH_WIDTH_DP = 28f

        /** How long the bar is kept out of a screenshot before and after the shutter. */
        private const val SCREENSHOT_HIDE_BEFORE_MS = 250L
        private const val SCREENSHOT_HIDE_AFTER_MS = 1_000L
    }

    private val windowManager: WindowManager? =
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val vibratorService: Vibrator? = context.getSystemService(Vibrator::class.java)
    private val brightness = BrightnessController(context)
    private val volume = VolumeController(context).also { it.start() }

    /** The torch, Do Not Disturb, auto-rotate and the media keys. Shared with the Deck. */
    val toggles = DeviceToggles(context).also { it.start() }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: View? = null
    private var handlerView: HandlerView? = null
    private var handlerParams: WindowManager.LayoutParams? = null
    private var gestureDetector: HandlerGestureDetector? = null

    /** The display frame the handler was last placed into. Refreshed on every geometry pass. */
    private var frame: HandlerGeometry.Frame? = null

    private var destroyed = false

    // ---- swipe-to-adjust state, latched for the duration of one gesture --------------------

    private var adjustIsBrightness = false
    private var adjustShowsUi = false
    private var adjustEnabled = false

    /**
     * The stream this swipe is driving, resolved once when the gesture is latched.
     *
     * Held for the whole gesture rather than re-resolved per step: a track ending mid-swipe would
     * otherwise move the user's finger onto a different stream halfway through.
     */
    private var adjustResolution: VolumeController.Resolution? = null

    /** Direction the latched swipe settings belong to: `+1` up, `-1` down, `0` nothing resolved. */
    private var adjustDirection = 0

    // ---- drag-to-reposition state ----------------------------------------------------------

    /** Where the bar sat when the drag began, in the window's absolute (`Gravity.LEFT`) space. */
    private var dragStartX = 0
    private var dragStartY = 0

    /** The edge offset in pixels, read once per drag rather than on every move frame. */
    private var dragEdgeMarginPx = 0

    /** Whether this drag ends by flying to the nearer side. Read once, at the start of the drag. */
    private var dragSnapToEdge = true

    /**
     * The in-flight snap animation, so a second drag begun before the first has landed cancels it
     * rather than fighting it for the window's x.
     */
    private var snapAnimator: ValueAnimator? = null

    /**
     * Which side the bar is currently dressed for, or null when it has not been decided yet.
     *
     * Cached so the dressing is only re-applied when it actually changes. `setViewGravity`
     * rebuilds the background drawable and requests a layout pass; running it on every move frame
     * of a drag is a new GradientDrawable per frame.
     */
    private var handlerDressedLeft: Boolean? = null

    // ---- long-press context menu -----------------------------------------------------------

    private var contextMenuHost: OverlayComposeHost? = null

    // ---- the Deck --------------------------------------------------------------------------------

    private var deckHost: OverlayComposeHost? = null
    private var deckRoot: DeckRootView? = null
    private val deckState = DeckState()
    private val deckEnvironment: DeckEnvironment by lazy {
        DeckEnvironment(context, preference, toggles, volume, brightness, deckState)
    }

    /** Resolved icons for the pinned apps, keyed by the package list they were built from. */
    private var appShortcutCache: Pair<List<String>, List<AppShortcut>>? = null

    private val deckAutoCloseRunnable = Runnable { hideDeck() }
    private val timerFinishRunnable = Runnable { onTimerFinished() }

    // ---- the expanding quick slider ----------------------------------------------------------

    private var sliderView: QuickSliderView? = null

    /** What this pull is driving, latched when it opens. */
    private var sliderTarget: String = QuickSliderStore.TARGET_BRIGHTNESS
    private var sliderResolution: VolumeController.Resolution? = null

    /** The value the control had when the slider opened. Every update is measured from it. */
    private var sliderOpenValue = 0f

    /** How many discrete steps the range has, so the haptics and the readout agree. */
    private var sliderSteps = 1

    /** The last step a tick was fired for, so one buzz is emitted per boundary crossed. */
    private var sliderLastStep = -1

    private var sliderHapticMs = 0L
    private var sliderHapticAmplitude = 0

    // ---- indicator -------------------------------------------------------------------------

    private var indicatorView: View? = null
    private var indicatorLabel: TextView? = null
    private val hideIndicatorRunnable = Runnable { hideIndicator() }

    private val clearVolumePercentRunnable = Runnable { handlerView?.setVolumePercent(null) }

    // ---- music-overlay touch state (the separate full-screen overlay feature) ---------------

    private val touchMoveFactor: Long by lazy { (20 * context.resources.displayMetrics.density).toLong() }
    private val touchTimeFactor: Long = 300L
    private var minSwipeY: Float = 0f
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var actionDownPoint = PointF(0f, 0f)
    private var touchDownTime = 0L
    private var isActionMoveEventStored = false
    private var lastActionMoveEventBeforeUpX = 0f
    private var lastActionMoveEventBeforeUpY = 0f
    private var isLongPressHandlerActivated = false
    private val longPressHandler = Handler(Looper.getMainLooper())
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

    init {
        (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.registerDisplayListener(displayListener, mainHandler)
        restoreTimer()
    }

    // =============================================================================================
    // Lifecycle and commands
    // =============================================================================================

    fun onConfigurationChanged() {
        // The rotation fix: recompute placement instead of leaving a stale portrait `y` behind,
        // which is what used to slide the bar down onto the landscape navigation bar.
        applyHandlerGeometry()
    }

    /**
     * Takes every window down and releases every callback.
     *
     * @param restoreBrightness whether adaptive brightness this app switched off is handed back.
     *   False when another host is about to carry on drawing the bar.
     */
    fun destroy(restoreBrightness: Boolean = true) {
        if (destroyed) return
        destroyed = true
        hideOverlayView()
        hideHandlerView()
        hideIndicator()
        hideDeck()
        // After hideHandlerView, whose gestureDetector.cancel() is what asks a slider still under
        // the finger to collapse. This turns that collapse into an immediate removal.
        dismissQuickSliderNow()

        (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.unregisterDisplayListener(displayListener)

        longPressHandler.removeCallbacks(longPressedRunnable)
        mainHandler.removeCallbacksAndMessages(null)

        if (restoreBrightness) restoreAutoBrightnessIfOurs()

        // Unregistering is mandatory, not tidy: a live AudioPlaybackCallback is held by
        // AudioService and would outlive the host that created it.
        volume.stop()
        toggles.stop()
    }

    /**
     * The command vocabulary both hosts answer to.
     *
     * "show"/"hide" are the *transient* pair: the app sends them as it comes to the foreground and
     * leaves again, and the boot/update receiver sends "show" too. None of that is the user asking
     * for the bar, so neither touches the hidden state — otherwise closing the app would resurrect
     * a bar put away on purpose. "user_show"/"user_hide" are the deliberate pair, from the
     * notification's buttons and the app's own card. These are the user speaking, so they do
     * change it.
     */
    fun handleCommand(action: String) {
        when (action) {
            "show" -> show()
            "hide" -> hide()
            "user_show" -> showByUser()
            "user_hide" -> hideByUser()
            "update" -> update()
            "stop" -> host.onStopRequested()
        }
    }

    fun show() = createOverlayHandler()

    fun hide() {
        hideOverlayView()
        hideHandlerView()
        hideDeck()
    }

    /** The user asked for the bar back: clear the hidden state, then show it. */
    private fun showByUser() {
        preference.setHandlerHidden(false)
        show()
        // The notification's first button swaps between Show and Hide, so it is reposted whenever
        // which one applies changes.
        host.onNotificationStateChanged()
    }

    /** The user put the bar away: it stays away until they say otherwise. */
    private fun hideByUser() {
        preference.setHandlerHidden(true)
        hide()
        host.onNotificationStateChanged()
    }

    /** Rebuilds the handler so new appearance settings take effect. */
    fun update() {
        hideHandlerView()
        createOverlayHandler()
    }

    private fun now(): Long = SystemClock.elapsedRealtime()

    // =============================================================================================
    // Handler window
    // =============================================================================================

    /**
     * Builds the handler window, unless the user has put the bar away.
     *
     * The hidden state is a *preference*, not just the absence of a window, because almost
     * everything that touches the overlay ends up here: a settings save rebuilds the handler, a
     * `START_STICKY` relaunch recreates it, and so does the restart after the task is swiped out
     * of Recents. Without somewhere durable to record "the user hid this", every one of those
     * brought the bar back on its own — which is exactly the "it reappears by itself" report.
     * Only an explicit Show — from the app, from the notification, or from the service command —
     * clears it.
     */
    private fun createOverlayHandler() {
        if (destroyed) return
        if (preference.isHandlerHidden()) return
        if (handlerView != null) return

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

        // The window is at least MIN_TOUCH_WIDTH_DP wide even when the bar drawn inside it is
        // narrower. See the constant.
        val windowWidth = maxOf(handlerWidth, MIN_TOUCH_WIDTH_DP)
        val layoutParams = buildHandlerParams(windowWidth, handlerHeight)

        handlerDressedLeft = handlerPosition == "Left"
        val view = HandlerView(context).apply {
            setViewGravity(if (handlerPosition == "Left") Gravity.START else Gravity.END)
            setViewDimensionsDp(windowWidth, handlerHeight)
            // Everything drawn stays at the configured width, pushed against the outer edge; the
            // difference is dead space that only catches fingers.
            setInwardPaddingDp(windowWidth - handlerWidth)
            setTranslationYPosition(0f)

            setViewBackgroundColor(backgroundColor, backgroundAlpha)
            setStrokeProperties(strokeColor, strokeWidth, strokeAlpha)
            setCornerRadiiDp(cornerRadiusTL, cornerRadiusTR, cornerRadiusBL, cornerRadiusBR)

            // Resolve through the shared safe-resolver so a stale stored icon id (R.drawable
            // values shift across app updates) falls back to the default instead of throwing.
            val safeDrawable = ContextCompat.getDrawable(
                context,
                context.safeDrawableIdOrDefault(iconRes)
            )
            setCenterIcon(safeDrawable, iconSize, iconColor)
            setCenterIconColor(iconColor)
            setCenterIconVisible(showIcon)

            setVibrateOnClick(vibrateOnClick)
        }

        val detector = HandlerGestureDetector(context, gestureHost)
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
            android.util.Log.e(TAG, "addView failed", e)
            handlerView = null
            handlerParams = null
            gestureDetector = null
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
            windowType,
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
            fitUsableFrame(this)
        }

    /**
     * Shrinks a window's containing frame by the system bars and the cutout, so x = 0 means
     * "flush with the edge the user can actually touch" — in portrait, in landscape where the
     * navigation bar takes a side, and on cutout devices. This is what stops the bar from
     * landing on the navigation bar after a rotation.
     */
    private fun fitUsableFrame(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.setFitInsetsTypes(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            params.setFitInsetsSides(
                WindowInsets.Side.LEFT or WindowInsets.Side.TOP or
                    WindowInsets.Side.RIGHT or WindowInsets.Side.BOTTOM
            )
            params.isFitInsetsIgnoringVisibility = true
        }
    }

    /**
     * A window covering the whole usable frame, for the menu, the Deck and anything else that
     * has to catch a tap outside itself.
     *
     * @param focusable true for the Deck, whose search field needs the keyboard. Everything else
     *   stays non-focusable so it never takes input away from the app underneath.
     */
    private fun fullScreenParams(focusable: Boolean): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            fitUsableFrame(this)
            if (focusable) {
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
            }
        }

    /**
     * Flies the bar to the nearer side and parks it exactly on the edge offset.
     *
     * Animated rather than teleported, and the position is persisted only when it lands: a
     * fraction written per frame would put ~10 writes through SharedPreferences per drag, and one
     * written up front would be a lie for the length of the animation — which is precisely when a
     * rotation is most likely to arrive and read it.
     */
    private fun snapToNearestEdge() {
        val params = handlerParams ?: return
        val currentFrame = frame ?: return
        val target = HandlerGeometry.snapX(
            params.x,
            currentFrame.usableWidth,
            params.width,
            dragEdgeMarginPx
        )
        if (target == params.x) {
            persistPosition()
            return
        }

        cancelSnap()
        val from = params.x
        snapAnimator = ValueAnimator.ofInt(from, target).apply {
            duration = ANIM_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                // Re-read every frame: the window can be torn down mid-animation by a settings
                // save, a rotation, or "Hide handler" from the menu that opened on the same press.
                val live = handlerParams ?: return@addUpdateListener
                live.x = animation.animatedValue as Int
                updateHandlerLayout(live)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                // `cancel()` delivers onAnimationEnd as well, so without the flag an interrupted
                // snap would write the position it happened to have been passing through — either
                // as a new drag begins, or as the window is being torn down. Neither is a place
                // the user put the bar.
                override fun onAnimationEnd(animation: Animator) {
                    snapAnimator = null
                    if (!cancelled) persistPosition()
                }
            })
            start()
        }
    }

    private fun cancelSnap() {
        snapAnimator?.cancel()
        snapAnimator = null
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
        // The menu and the Deck are placed against the bar's x, y and the frame — all three of
        // which this function is about to rewrite. Left open across a rotation they would be
        // anchored to geometry that no longer exists, hanging in the wrong place over the user's
        // app. Closing them here rather than in the callers covers both routes in:
        // onConfigurationChanged and the DisplayManager listener, so an OEM build that drops one
        // of them still behaves.
        hideContextMenu()
        hideDeck()
        dismissQuickSliderNow()

        val params = handlerParams ?: return
        val currentFrame = HandlerGeometry.read(context, windowManager) ?: return
        frame = currentFrame

        val barHeightPx = dpToPx(preference.getHandlerHeightDp())
        // The window's width, not the bar's — every placement number here is about the window,
        // which is wider than the bar whenever the bar is narrower than a finger. Using the drawn
        // width would park the window's edge where the bar's edge should be and push the visible
        // bar off the side of the screen.
        val visualWidthDp = preference.getHandlerWidthDp()
        val barWidthPx = dpToPx(maxOf(visualWidthDp, MIN_TOUCH_WIDTH_DP))
        handlerView?.setInwardPaddingDp(maxOf(visualWidthDp, MIN_TOUCH_WIDTH_DP) - visualWidthDp)
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
        val edgeMarginPx = dpToPx(preference.getHandlerEdgeMarginDp())
        val storedX = HandlerGeometry.fractionToX(
            preference.getHandlerPosXFraction(isPortrait),
            currentFrame.usableWidth,
            barWidthPx
        )
        // With snapping on, a placement pass parks the bar rather than merely clamping it. The
        // stored fraction is still the source of truth for *which side* — this only removes the
        // drift a rotation would otherwise introduce, where a bar flush against a 1080px-wide
        // frame reappears a proportional distance into a 2400px-wide one.
        params.x = if (preference.getHandlerSnapToEdge()) {
            HandlerGeometry.snapX(storedX, currentFrame.usableWidth, barWidthPx, edgeMarginPx)
        } else {
            HandlerGeometry.clampX(storedX, currentFrame.usableWidth, barWidthPx, edgeMarginPx)
        }
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
                android.util.Log.e(TAG, "updateViewLayout failed", e)
            }
        }
    }

    private fun hideHandlerView() {
        // The menu is anchored to a bar that is about to stop existing, and the pending clear
        // would fire against a view that is gone. The snap animation is in the same position: its
        // frame callback writes into handlerParams, which is about to be null.
        cancelSnap()
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

    /** Whether the bar's centre is nearer the left edge, from live geometry rather than a stored side. */
    private fun handlerIsLeft(): Boolean {
        val params = handlerParams
        val currentFrame = frame
        return if (params != null && currentFrame != null) {
            HandlerGeometry.xToIsLeft(params.x, currentFrame.usableWidth, params.width)
        } else {
            handlerDressedLeft != false
        }
    }

    // =============================================================================================
    // Gestures
    // =============================================================================================

    private val gestureHost = object : HandlerGestureDetector.Host {

        override fun isLongPressReposition(): Boolean =
            preference.getHandlerLongTapAction() == HandlerActions.REPOSITION

        override fun isDoubleTapArmed(): Boolean =
            preference.getHandlerDoubleTapAction() != HandlerActions.NONE

        override fun isTripleTapArmed(): Boolean =
            preference.getHandlerTripleTapAction() != HandlerActions.NONE

        // Tap actions can tear the handler window down ("Hide Handler", "Open App", the music
        // overlay). Running that inside onTouchEvent would destroy the window from within input
        // dispatch, so every action is posted off the input stack.
        override fun onTap() {
            mainHandler.post { runAction(preference.getHandlerSingleTapAction()) }
        }

        override fun onDoubleTap() {
            mainHandler.post { runAction(preference.getHandlerDoubleTapAction()) }
        }

        override fun onTripleTap() {
            mainHandler.post { runAction(preference.getHandlerTripleTapAction()) }
        }

        override fun onLongPress() {
            mainHandler.post { runAction(preference.getHandlerLongTapAction()) }
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
            cancelSnap()
            val params = handlerParams
            dragStartX = params?.x ?: 0
            dragStartY = params?.y ?: 0
            dragEdgeMarginPx = dpToPx(preference.getHandlerEdgeMarginDp())
            dragSnapToEdge = preference.getHandlerSnapToEdge()
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
         * Where the bar comes to rest.
         *
         * Vertically it always stays exactly where it was let go. Horizontally that depends on one
         * setting: with "Snap to edge" on it flies to the nearer side and parks at the edge offset;
         * with it off it stays put, held only by the same offset that clamped every frame of the
         * drag. Either way the offset is the one number involved, which is what stops the two modes
         * from meaning different things by "the edge".
         */
        override fun onDragEnd(moved: Boolean) {
            if (!moved) return
            if (dragSnapToEdge) snapToNearestEdge() else persistPosition()
        }

        override fun edgeSwipeInwardSign(): Int {
            // The same expression applyHandlerGeometry, onDragUpdate and persistPosition already
            // use, so "which way is inward" and "which way does the flat edge face" are one
            // decision and cannot drift apart. Deliberately NOT getHandlerPosition(), which is
            // written only when a drag or a snap ends and is therefore stale mid-drag and after a
            // rotation. xToIsLeft decides by the bar's centre, so a bar parked mid-screen still
            // gets a definite answer, and it is the answer its visible dressing already gives.
            return if (handlerIsLeft()) 1 else -1
        }

        override fun isHorizontalSwipeArmed(inward: Boolean): Boolean =
            !HandlerActions.isDisabled(horizontalSwipeAction(inward))

        override fun onHorizontalSwipe(inward: Boolean) {
            // Posted for the reason the note above this host gives: adding a window from inside
            // the touch stream is what that warning is about.
            val action = horizontalSwipeAction(inward)
            mainHandler.post { runAction(action) }
        }

        override fun isQuickSliderArmed(inward: Boolean): Boolean {
            if (!preference.slider.opensOn(inward)) return false
            // A slider bound to a control this device will not let the app write is not "armed but
            // failing" — it never opens at all, so the long swipe stays inert and the short swipe
            // keeps its immediate timing rather than being deferred for a gesture that cannot work.
            return quickSliderIsWritable(preference.slider.getTarget())
        }

        override fun quickSliderSweepDp(): Float = preference.slider.getLengthDp()

        /**
         * Opened synchronously, unlike every action above.
         *
         * Those are posted because they can tear down the handler window from inside its own input
         * dispatch. This does the opposite: it *adds* a separate, untouchable window and leaves the
         * handler's own window in place, still receiving the gesture that is driving the slider.
         * Posting it would put a frame of nothing between the swipe qualifying and the bar
         * appearing to expand, which is the one moment the gesture has to feel immediate.
         */
        override fun onQuickSliderBegin(inward: Boolean) {
            showQuickSlider()
        }

        override fun onQuickSliderUpdate(fractionFromOpen: Float) {
            applyQuickSlider(fractionFromOpen)
        }

        override fun onQuickSliderEnd() {
            hideQuickSlider()
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

    private fun horizontalSwipeAction(inward: Boolean): String =
        if (inward) preference.getHandlerSwipeInAction() else preference.getHandlerSwipeOutAction()

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
            if (!brightness.canWrite()) {
                adjustEnabled = false
                mainHandler.post {
                    showIndicatorMessage(context.getString(R.string.brightness_needs_permission_short))
                }
                return
            }
            if (brightness.disableAutoBrightnessIfNeeded()) {
                preference.setBrightnessAutoWasOn(true)
            }
            gestureDetector?.setStepCount(brightness.stepCount)
            adjustResolution = null
        } else {
            // Resolved once, here, and held for the gesture. The step count comes from the stream
            // that was actually chosen: a call has far fewer indices than media, and sizing the
            // sweep to media's range would make a call swipe feel broken.
            val resolution = volume.resolve(preference.getVolumeStreamMode())
            adjustResolution = resolution
            gestureDetector?.setStepCount(resolution.stepCount)
        }
    }

    private fun stepVolume(direction: Int): Boolean {
        val resolution = adjustResolution ?: volume.media().also { adjustResolution = it }

        val percent = volume.step(resolution, direction, adjustShowsUi) ?: return false

        // A negative percentage is the opaque-route sentinel: the level moved, but its true value
        // is on a stream this app cannot read, so there is no honest number to show.
        if (percent >= 0) showVolumePercentOnHandler(percent, resolution)
        return true
    }

    /**
     * Puts the level on the bar itself while a swipe is moving it.
     *
     * Only while adjusting: the number is already in hand at this point, so it costs nothing,
     * whereas keeping it permanently accurate would mean listening for every volume change the
     * rest of the system makes — hardware keys, other apps — for a readout nobody is looking at.
     */
    private fun showVolumePercentOnHandler(percent: Int, resolution: VolumeController.Resolution) {
        if (!preference.getShowVolumePercent()) return
        val view = handlerView ?: return
        mainHandler.removeCallbacks(clearVolumePercentRunnable)
        view.setVolumePercent(percent)
        mainHandler.postDelayed(clearVolumePercentRunnable, VOLUME_PERCENT_VISIBLE_MS)

        // Naming the stream only when it is not the familiar one. Media is what the bar has always
        // done and what it still does most of the time, so labelling every swipe "Media" would be
        // noise; a swipe that lands on the call or alarm stream is the surprising case and is the
        // one worth explaining — especially when the system panel is switched off and this readout
        // is the only feedback there is.
        if (resolution.stream != AudioStreamResolver.STREAM_MUSIC) {
            showIndicatorMessage(context.getString(AudioStreamCatalog.labelFor(resolution.stream)))
        }
    }

    private fun stepBrightness(direction: Int): Boolean {
        val fraction = brightness.step(direction) ?: return false
        showIndicatorMessage(
            context.getString(R.string.brightness_percent, (fraction * 100).roundToInt())
        )
        return true
    }

    private fun restoreAutoBrightnessIfOurs() {
        if (!preference.getBrightnessAutoWasOn()) return
        // Only forget the flag once the setting actually went back, so a failed write (no
        // permission, OEM ROM silently dropping it) does not leave the user stuck on manual.
        if (brightness.setAutoBrightness(true)) {
            preference.setBrightnessAutoWasOn(false)
        }
    }

    // =============================================================================================
    // Indicator
    // =============================================================================================

    /**
     * A small transient readout, because unlike volume there is no system UI for brightness — the
     * user would otherwise be adjusting it blind. Also every "that needs a permission" and "torch
     * on" message the bar has to say.
     */
    fun showIndicatorMessage(text: String, durationMs: Long = INDICATOR_VISIBLE_MS) {
        if (destroyed) return
        val wm = windowManager ?: return
        mainHandler.removeCallbacks(hideIndicatorRunnable)

        if (indicatorView == null) {
            val density = context.resources.displayMetrics.density
            val padH = (20 * density).toInt()
            val padV = (14 * density).toInt()

            val label = TextView(context).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(padH, padV, padH, padV)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 18 * density
                    setColor(Color.argb(220, 24, 24, 24))
                }
            }
            val container = FrameLayout(context).apply { addView(label) }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
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
                android.util.Log.e(TAG, "indicator addView failed", e)
                return
            }
            indicatorView = container
            indicatorLabel = label
        }

        indicatorLabel?.text = text
        indicatorView?.visibility = View.VISIBLE
        mainHandler.postDelayed(hideIndicatorRunnable, durationMs)
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
    // The expanding quick slider
    // =============================================================================================

    /**
     * Whether the app can actually write the control this target names, right now.
     *
     * Checked before the gesture is armed rather than after it fires. A slider that opens, follows
     * the finger and changes nothing is worse than one that never opens: the user cannot tell
     * whether they missed the gesture or the app is broken, so they repeat it.
     */
    private fun quickSliderIsWritable(target: String): Boolean = when (target) {
        QuickSliderStore.TARGET_BRIGHTNESS -> brightness.canWrite()
        QuickSliderStore.TARGET_RING -> !volume.isVolumeFixed() && volume.canWriteRing()
        else -> !volume.isVolumeFixed()
    }

    private fun quickSliderResolution(target: String): VolumeController.Resolution? = when (target) {
        QuickSliderStore.TARGET_MEDIA -> volume.media()
        QuickSliderStore.TARGET_RING -> volume.forStream(AudioManager.STREAM_RING)
        QuickSliderStore.TARGET_ALARM -> volume.forStream(AudioManager.STREAM_ALARM)
        else -> null
    }

    @DrawableRes
    private fun quickSliderIcon(target: String): Int =
        if (target == QuickSliderStore.TARGET_BRIGHTNESS) {
            R.drawable.ic_brightness_up
        } else {
            R.drawable.ic_vol_increase
        }

    /** The control's current level as 0..1, or null when it cannot be read. */
    private fun quickSliderCurrentValue(): Float? =
        if (sliderTarget == QuickSliderStore.TARGET_BRIGHTNESS) {
            brightness.fraction()
        } else {
            sliderResolution?.let { res -> volume.percent(res)?.let { it / 100f } }
        }

    /**
     * Expands the bar into the slider.
     *
     * The handler's window is left in place and only its view is hidden. That window is the one
     * Android is dispatching this gesture to, and taking it away mid-stream would end the gesture
     * the slider exists to follow — the finger would be holding a control that had stopped
     * listening. The slider gets a window of its own, marked untouchable so it cannot intercept
     * anything, sitting exactly where the bar was.
     */
    private fun showQuickSlider() {
        if (destroyed || sliderView != null) return
        val wm = windowManager ?: return
        val barParams = handlerParams ?: return
        val currentFrame = frame ?: return
        val settings = preference.slider

        sliderTarget = settings.getTarget()
        sliderResolution = quickSliderResolution(sliderTarget)

        // Adaptive brightness has to go before the first write, not after: the light sensor
        // overwrites anything the app sets within a second or two, which reads as the slider
        // not working rather than as a setting fighting it. Restored by the same
        // restoreAutoBrightnessIfOurs the swipe gesture already uses, so the user gets it back.
        if (sliderTarget == QuickSliderStore.TARGET_BRIGHTNESS && settings.getDisableAutoBrightness()) {
            brightness.disableAutoBrightnessIfNeeded()
            if (brightness.autoDisabledByFraction) preference.setBrightnessAutoWasOn(true)
        }

        sliderSteps = if (sliderTarget == QuickSliderStore.TARGET_BRIGHTNESS) {
            brightness.stepCount
        } else {
            sliderResolution?.stepCount ?: 1
        }.coerceAtLeast(1)

        sliderOpenValue = quickSliderCurrentValue() ?: 0f
        sliderLastStep = (sliderOpenValue * sliderSteps).roundToInt()

        val haptic = settings.getHaptic()
        sliderHapticMs = when (haptic) {
            QuickSliderStore.HAPTIC_LIGHT -> 10L
            QuickSliderStore.HAPTIC_MEDIUM -> 16L
            QuickSliderStore.HAPTIC_STRONG -> 24L
            else -> 0L
        }
        sliderHapticAmplitude = when (haptic) {
            QuickSliderStore.HAPTIC_LIGHT -> 70
            QuickSliderStore.HAPTIC_MEDIUM -> 150
            QuickSliderStore.HAPTIC_STRONG -> 255
            else -> 0
        }

        val thicknessPx = dpToPx(settings.getThicknessDp())
        val lengthPx = dpToPx(settings.getLengthDp())
            .coerceAtMost(currentFrame.usableHeight.coerceAtLeast(1))
        val isLeft = handlerIsLeft()
        val edgeMarginPx = dpToPx(preference.getHandlerEdgeMarginDp())

        val view = QuickSliderView(context).apply {
            setColors(settings.getTrackColor(), settings.getFillColor())
            setCornerRadiusDp(settings.getCornerDp())
            setIcon(if (settings.getShowIcon()) quickSliderIcon(sliderTarget) else null)
            setShowValue(settings.getShowValue())
            setValue(sliderOpenValue)
            alpha = 0f
        }

        val params = WindowManager.LayoutParams(
            thicknessPx,
            lengthPx,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = if (isLeft) {
                edgeMarginPx
            } else {
                (currentFrame.usableWidth - thicknessPx - edgeMarginPx).coerceAtLeast(0)
            }
            // Centred on the bar rather than on the screen, so the track grows out of where the
            // user's finger already is instead of jumping to the middle.
            val barCenterY = barParams.y + barParams.height / 2
            y = (barCenterY - lengthPx / 2)
                .coerceIn(0, (currentFrame.usableHeight - lengthPx).coerceAtLeast(0))
            fitUsableFrame(this)
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "quick slider addView failed", e)
            return
        }
        sliderView = view

        // Faded out, NOT made INVISIBLE, and this is load-bearing rather than a style choice.
        //
        // This view is the root of its own window. ViewRootImpl reports the root's visibility to
        // WindowManagerService on the next traversal, and a window reported not-visible is dropped
        // from input dispatch — which cancels the very gesture whose finger is still driving the
        // slider. The bar would vanish and the slider would freeze at whatever value it opened on.
        // Alpha leaves the view VISIBLE to the window system and merely stops it being drawn.
        handlerView?.let { bar ->
            bar.animate().cancel()
            bar.alpha = 0f
        }

        // Grown from the bar's own dimensions, pivoting on the edge it is mounted against, so the
        // motion reads as the bar stretching rather than a panel fading in over it.
        view.pivotX = if (isLeft) 0f else thicknessPx.toFloat()
        view.pivotY = lengthPx / 2f
        view.scaleX = (barParams.width.toFloat() / thicknessPx.coerceAtLeast(1)).coerceIn(0.05f, 1f)
        view.scaleY = (barParams.height.toFloat() / lengthPx.coerceAtLeast(1)).coerceIn(0.05f, 1f)
        view.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(ANIM_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // The buzz that says the bar is now a slider. Distinct from the per-step ticks: longer,
        // so it cannot be mistaken for the first step having already been crossed.
        if (sliderHapticMs > 0L) vibrateQuick(28L, sliderHapticAmplitude)
    }

    /**
     * Applies one frame of the pull.
     *
     * Quantised to [sliderSteps] before anything else happens, so the number the user reads, the
     * value written to the system and the boundary the haptics fire on are all the same decision
     * made once. Reading them off three separate roundings is how a slider ends up buzzing without
     * moving, or showing 41% while the system holds 40%.
     */
    private fun applyQuickSlider(fractionFromOpen: Float) {
        val view = sliderView ?: return
        val target = (sliderOpenValue + fractionFromOpen).coerceIn(0f, 1f)
        val step = (target * sliderSteps).roundToInt().coerceIn(0, sliderSteps)
        val quantised = step / sliderSteps.toFloat()

        view.setValue(quantised)
        if (step == sliderLastStep) return
        sliderLastStep = step

        val applied = if (sliderTarget == QuickSliderStore.TARGET_BRIGHTNESS) {
            brightness.setFraction(quantised) != null
        } else {
            val res = sliderResolution
            res != null && volume.setPercent(res, (quantised * 100f).roundToInt(), showUi = false) != null
        }

        // No buzz for a step the system refused. The tick is feedback about the control moving,
        // and a control pinned at an end that keeps ticking tells the user it is still moving.
        if (applied && sliderHapticMs > 0L) vibrateQuick(sliderHapticMs, sliderHapticAmplitude)
    }

    /** Collapses the slider back into the bar. */
    private fun hideQuickSlider() {
        val view = sliderView ?: return
        sliderView = null
        sliderResolution = null
        sliderLastStep = -1

        handlerView?.alpha = 1f

        view.animate()
            .alpha(0f)
            .scaleY(0.2f)
            .setDuration(ANIM_DURATION_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { removeQuickSliderView(view) }
            .start()
    }

    /**
     * Takes the slider off screen at once, with no collapse animation.
     *
     * For the two cases where animating would be wrong rather than merely slower: the whole
     * overlay is being destroyed, and the display rotated. In both, the geometry the slider was
     * placed against has already stopped being true, so a graceful collapse would play out in the
     * wrong place.
     */
    private fun dismissQuickSliderNow() {
        val view = sliderView ?: return
        sliderView = null
        sliderResolution = null
        sliderLastStep = -1
        view.animate().cancel()
        handlerView?.alpha = 1f
        removeQuickSliderView(view)
    }

    private fun removeQuickSliderView(view: View) {
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
            // Already gone — the service was torn down while the collapse was still running.
        }
    }

    /**
     * One tick. Uses the vibrator rather than `View.performHapticFeedback` for the reason
     * `onDragCue` gives: view haptics are routinely dropped in overlay windows on OEM builds, and
     * on a slider the tick is the only thing telling the user a step went by.
     */
    private fun vibrateQuick(durationMs: Long, amplitude: Int) {
        val vibrator = vibratorService ?: return
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255))
        } else {
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        runCatching { vibrator.vibrate(effect) }
    }

    // =============================================================================================
    // Long-press context menu
    // =============================================================================================

    /**
     * Opens the menu beside the handler.
     *
     * Does nothing when the user has emptied the menu in settings — holding then simply moves the
     * bar, which is the behaviour that shipped before this menu existed.
     */
    private fun showContextMenu() {
        if (destroyed) return
        val wm = windowManager ?: return
        val params = handlerParams ?: return
        val currentFrame = frame ?: return
        if (contextMenuHost != null) return
        hideDeck()

        val entries = HandlerActionCatalog.contextMenuEntries(preference.getContextMenuItems())
        if (entries.isEmpty()) return

        val anchor = IntRect(params.x, params.y, params.x + params.width, params.y + params.height)
        val frameSize = IntSize(currentFrame.usableWidth, currentFrame.usableHeight)

        val menuHost = OverlayComposeHost(context)
        menuHost.setContent {
            OverlayTheme {
                ContextMenuOverlay(
                    entries = entries,
                    anchor = anchor,
                    frame = frameSize,
                    onSelect = { entry ->
                        hideContextMenu()
                        // Posted for the same reason the tap actions are: "Hide Handler" and
                        // "Open App" destroy windows, and doing that from inside a click dispatch
                        // tears down a view hierarchy that is still being walked.
                        mainHandler.post { runAction(entry.action) }
                    },
                    onDismiss = { hideContextMenu() }
                )
            }
        }

        // A full-screen root rather than a card-sized window: it is what catches the tap outside
        // the menu that dismisses it. The window is not focusable, so there is no back-button
        // route to close it and an outside tap is the only way out.
        try {
            wm.addView(menuHost.view, fullScreenParams(focusable = false))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "context menu addView failed", e)
            menuHost.destroy()
            return
        }
        contextMenuHost = menuHost
    }

    private fun hideContextMenu() {
        contextMenuHost?.let { menuHost ->
            try {
                windowManager?.removeView(menuHost.view)
            } catch (_: Exception) {
                // Already gone.
            }
            menuHost.destroy()
        }
        contextMenuHost = null
    }

    // =============================================================================================
    // The Deck
    // =============================================================================================

    /**
     * Opens the Deck beside the bar, straight onto [tile] when one is named.
     *
     * One window, focusable: the search field and the notes need the keyboard, and Android hands
     * the clipboard only to a focused window, which is what lets the history be brought up to
     * date the moment the Deck appears. The bar's own window stays where it is underneath.
     */
    fun showDeck(tile: String? = null) {
        if (destroyed) return
        val wm = windowManager ?: return
        hideContextMenu()

        if (deckHost != null) {
            // Already open: a gesture bound to a tile switches to it rather than reopening.
            if (tile != null) deckState.expandedTile = tile
            restartDeckAutoClose()
            return
        }

        val currentFrame = frame ?: HandlerGeometry.read(context, wm)?.also { frame = it } ?: return
        val params = handlerParams
        val barWidth = params?.width ?: dpToPx(preference.getHandlerWidthDp())
        val barHeight = params?.height ?: dpToPx(preference.getHandlerHeightDp())
        val barX = params?.x ?: HandlerGeometry.sideToX(
            isLeft = preference.getHandlerPosition() == "Left",
            usableWidth = currentFrame.usableWidth,
            barWidth = barWidth,
            edgeMarginPx = 0
        )
        val barY = params?.y ?: HandlerGeometry.fractionToY(
            preference.getHandlerPosYFraction(currentFrame.isPortrait),
            currentFrame.usableHeight,
            barHeight
        )
        val model = buildDeckModel(
            anchor = IntRect(barX, barY, barX + barWidth, barY + barHeight),
            frameSize = IntSize(currentFrame.usableWidth, currentFrame.usableHeight),
            isLeft = HandlerGeometry.xToIsLeft(barX, currentFrame.usableWidth, barWidth),
            initialTile = tile
        )
        deckState.expandedTile = tile

        val host = OverlayComposeHost(context)
        val root = DeckRootView(
            context = context,
            onBack = { onDeckBack() },
            onInteraction = { restartDeckAutoClose() }
        )
        host.setContent {
            OverlayTheme {
                DeckOverlay(model = model, actions = deckActions, onDismiss = { hideDeck() })
            }
        }
        // The wrapper is the window's root, and Compose resolves its recomposer from the root
        // rather than from the ComposeView — so the owners go on the wrapper too.
        host.attachTo(root)
        root.addView(
            host.view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        try {
            wm.addView(root, fullScreenParams(focusable = true))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "deck addView failed", e)
            host.destroy()
            return
        }
        deckHost = host
        deckRoot = root
        restartDeckAutoClose()
    }

    fun hideDeck() {
        mainHandler.removeCallbacks(deckAutoCloseRunnable)
        val root = deckRoot ?: return
        val host = deckHost
        deckRoot = null
        deckHost = null
        try {
            windowManager?.removeView(root)
        } catch (_: Exception) {
            // Already gone.
        }
        host?.destroy()
        // The next opening starts on the strip, unless a gesture names a tile.
        deckState.expandedTile = null
    }

    val isDeckOpen: Boolean get() = deckHost != null

    /** Back closes the open card first, then the Deck — the way a two-level screen behaves. */
    private fun onDeckBack() {
        if (deckState.expandedTile != null) {
            deckState.expandedTile = null
            restartDeckAutoClose()
        } else {
            hideDeck()
        }
    }

    private fun restartDeckAutoClose() {
        mainHandler.removeCallbacks(deckAutoCloseRunnable)
        val seconds = preference.deck.getAutoCloseSeconds()
        if (seconds > 0 && deckHost != null) {
            mainHandler.postDelayed(deckAutoCloseRunnable, seconds * 1000L)
        }
    }

    private fun buildDeckModel(
        anchor: IntRect,
        frameSize: IntSize,
        isLeft: Boolean,
        initialTile: String?
    ): DeckModel {
        val store = preference.deck
        val background = Color.argb(
            store.getBackgroundAlpha(),
            Color.red(store.getBackgroundColor()),
            Color.green(store.getBackgroundColor()),
            Color.blue(store.getBackgroundColor())
        )
        val config = DeckConfig(
            widthDp = store.getWidthDp(),
            heightFraction = store.getHeightFraction(),
            cornerDp = store.getCornerRadiusDp(),
            background = androidx.compose.ui.graphics.Color(background),
            accent = androidx.compose.ui.graphics.Color(store.getAccentColor()),
            autoCloseSeconds = store.getAutoCloseSeconds(),
            utilitiesFirst = store.getUtilitiesFirst()
        )
        val tiles = DeckTiles.visible(store.getTileOrder(), store.getEnabledTiles())
            .filter { tile ->
                // A tile for hardware the device lacks is noise, not a choice.
                when (tile.id) {
                    DeckTiles.FLASHLIGHT -> toggles.hasFlashlight()
                    DeckTiles.SCREENSHOT, DeckTiles.LOCK -> OverlayRuntime.supportsLockAndScreenshot
                    else -> true
                }
            }
        return DeckModel(
            config = config,
            tiles = tiles,
            apps = resolveAppShortcuts(),
            quickDial = store.getQuickDial(),
            anchor = anchor,
            frame = frameSize,
            isLeft = isLeft,
            initialTile = initialTile
        )
    }

    /**
     * Labels and icons for the pinned apps, cached against the package list.
     *
     * The icons are rasterised once per list rather than on every opening: a handful of
     * `getApplicationIcon` calls is cheap, but the Deck is opened with a flick and should not
     * spend its first frame on the package manager.
     */
    private fun resolveAppShortcuts(): List<AppShortcut> {
        val packages = preference.deck.getAppShortcuts()
        appShortcutCache?.let { (cachedFor, cached) -> if (cachedFor == packages) return cached }
        val pm = context.packageManager
        val sizePx = dpToPx(48f).coerceAtLeast(1)
        val resolved = packages.mapNotNull { pkg ->
            runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                AppShortcut(
                    packageName = pkg,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = pm.getApplicationIcon(info).toBitmap(sizePx, sizePx).asImageBitmap()
                )
            }.getOrNull()
        }
        appShortcutCache = packages to resolved
        return resolved
    }

    /**
     * Records what is on the clipboard into the history.
     *
     * Called when the clipboard card opens, and only then. Reading the clipboard needs a focused
     * window — which the Deck's is — but Android 12+ also posts its own "pasted from your
     * clipboard" notice on every read, so doing this on each Deck opening would put a system
     * toast on screen every time the user flicked the bar. Opening the clipboard card is the one
     * moment the user has asked for the clipboard, and the notice belongs there.
     */
    private fun captureClipboardIntoHistory() {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val text = runCatching {
            manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        }.getOrNull() ?: return
        if (text.isNotBlank()) preference.addClipboardEntry(text)
    }

    private val deckActions = object : DeckActions {
        override val env: DeckEnvironment get() = deckEnvironment

        override fun runAction(action: String) {
            this@OverlayController.runAction(action)
            deckState.toggleVersion++
        }

        override fun launchApp(packageName: String) {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            hideDeck()
            if (intent == null || !launchActivity(intent)) {
                showIndicatorMessage(context.getString(R.string.deck_app_unavailable))
            }
        }

        override fun dial(entry: QuickDialEntry) {
            hideDeck()
            val direct = preference.search.getDirectCall() &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED
            val intent = Intent(
                if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL,
                Uri.parse("tel:" + Uri.encode(entry.number))
            )
            if (!launchActivity(intent)) {
                showIndicatorMessage(context.getString(R.string.action_unavailable_on_device))
            }
        }

        override fun launch(intent: Intent): Boolean = launchActivity(intent)

        override fun openAppScreen(route: String) {
            hideDeck()
            launchActivity(
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_ROUTE, route)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }

        override fun copy(text: String, paste: Boolean) {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            runCatching { manager?.setPrimaryClip(ClipData.newPlainText("GestureVolume", text)) }
            val service = OverlayRuntime.accessibilityService
            if (paste && service != null) {
                // Focus has to return to the field under the Deck before the paste can land, so
                // the Deck closes first and the paste follows a beat later.
                hideDeck()
                mainHandler.postDelayed({
                    val pasted = service.pasteIntoFocusedField(text)
                    showIndicatorMessage(
                        context.getString(if (pasted) R.string.deck_pasted else R.string.deck_copied)
                    )
                }, 350L)
            } else {
                showIndicatorMessage(context.getString(R.string.deck_copied))
            }
        }

        override fun captureClipboard() = captureClipboardIntoHistory()

        override fun message(text: String) = showIndicatorMessage(text)

        override fun startTimer(seconds: Int) = this@OverlayController.startTimer(seconds)

        override fun stopTimer() = this@OverlayController.stopTimer()

        override fun onInteraction() = restartDeckAutoClose()

        override fun close() = hideDeck()
    }

    // ---- the countdown, which outlives the Deck window -------------------------------------------

    private fun startTimer(seconds: Int) {
        val safe = seconds.coerceIn(10, 24 * 3600)
        val endAt = now() + safe * 1000L
        deckState.timerSeconds = safe
        deckState.timerEndAt = endAt
        deckState.timerFinishedAt = 0L
        preference.deck.setTimerLastSeconds(safe)
        preference.deck.setTimerEndAt(endAt)
        mainHandler.removeCallbacks(timerFinishRunnable)
        mainHandler.postDelayed(timerFinishRunnable, safe * 1000L)
    }

    private fun stopTimer() {
        deckState.timerEndAt = 0L
        preference.deck.setTimerEndAt(0L)
        mainHandler.removeCallbacks(timerFinishRunnable)
    }

    /**
     * Picks a countdown back up after the host was rebuilt.
     *
     * Elapsed-realtime is zero at boot, so an end time from before a reboot is always in the
     * past and is simply cleared — a timer cannot survive a restart, and pretending it fired the
     * moment the phone came back would be worse than losing it.
     */
    private fun restoreTimer() {
        deckState.timerSeconds = preference.deck.getTimerLastSeconds()
        val endAt = preference.deck.getTimerEndAt()
        if (endAt <= 0L) return
        val remaining = endAt - now()
        if (remaining > 0L) {
            deckState.timerEndAt = endAt
            mainHandler.postDelayed(timerFinishRunnable, remaining)
        } else {
            preference.deck.setTimerEndAt(0L)
        }
    }

    private fun onTimerFinished() {
        deckState.timerEndAt = 0L
        deckState.timerFinishedAt = now()
        preference.deck.setTimerEndAt(0L)
        vibratorService?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250, 150, 400), -1)
        )
        runCatching {
            RingtoneManager.getRingtone(
                context,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            )?.play()
        }
        showIndicatorMessage(context.getString(R.string.deck_timer_finished), durationMs = 3_000L)
    }

    // =============================================================================================
    // Actions
    // =============================================================================================

    /**
     * Runs one action identifier, from whichever gesture or menu it came.
     *
     * Reposition is armed by the gesture engine itself and never runs as an action; reaching here
     * with it would only buzz the phone for nothing.
     */
    fun runAction(action: String) {
        if (destroyed) return
        if (action == HandlerActions.REPOSITION || action == HandlerActions.NONE) return
        if (preference.getHandlerVibrateOnClick()) {
            vibratorService?.vibrate(
                VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
        HandlerActions.deckTileFor(action)?.let {
            showDeck(it)
            return
        }
        when (action) {
            HandlerActions.OPEN_VOLUME_UI -> {
                volume.panel(volume.resolve(preference.getVolumeStreamMode()))
            }
            HandlerActions.MUTE -> {
                val resolution = volume.resolve(preference.getVolumeStreamMode())
                volume.mute(resolution)?.let { previous ->
                    preference.setPreMuteLevel(resolution.stream, previous)
                }
            }
            HandlerActions.MUTE_OR_UNMUTE -> {
                val resolution = volume.resolve(preference.getVolumeStreamMode())
                // Asking the controller whether it is muted, rather than comparing an index to
                // zero: several streams have a non-zero floor, so "index > 0" is not the same
                // question as "can the user hear it".
                if (volume.isMuted(resolution)) {
                    volume.unmute(resolution, preference.getPreMuteLevel(resolution.stream))
                } else {
                    volume.mute(resolution)?.let { previous ->
                        preference.setPreMuteLevel(resolution.stream, previous)
                    }
                }
            }
            HandlerActions.TOGGLE_AUTO_BRIGHTNESS -> toggleAutoBrightness()
            HandlerActions.ACTIVE_MUSIC_OVERLAY -> {
                hideHandlerView()
                createOverlayView()
            }
            HandlerActions.HIDE_HANDLER -> {
                hideByUser()
                showIndicatorMessage(context.getString(R.string.handler_hidden_toast))
            }
            HandlerActions.STOP_SERVICE -> host.onStopRequested()
            HandlerActions.OPEN_APP -> openApp()
            HandlerActions.OPEN_DECK -> showDeck()
            HandlerActions.OPEN_MENU -> showContextMenu()
            HandlerActions.TOGGLE_FLASHLIGHT -> toggleFlashlight()
            HandlerActions.TOGGLE_DND -> toggleDnd()
            HandlerActions.TOGGLE_AUTO_ROTATE -> toggleAutoRotate()
            HandlerActions.MEDIA_PLAY_PAUSE -> toggles.mediaPlayPause()
            HandlerActions.MEDIA_NEXT -> toggles.mediaNext()
            HandlerActions.MEDIA_PREVIOUS -> toggles.mediaPrevious()
            HandlerActions.SCAN_QR -> openQrScanner()
            HandlerActions.SONG_SEARCH -> songSearch()
            in HandlerActions.ACCESSIBILITY_ACTIONS -> performSystemAction(action)
        }
    }

    private fun toggleAutoBrightness() {
        if (!brightness.canWrite()) {
            showIndicatorMessage(context.getString(R.string.brightness_needs_permission_short))
            return
        }
        val turningOn = !brightness.isAutoBrightnessOn()
        if (brightness.setAutoBrightness(turningOn)) {
            // Once the user asks for adaptive brightness explicitly, it is theirs again.
            if (turningOn) preference.setBrightnessAutoWasOn(false)
            showIndicatorMessage(
                context.getString(
                    if (turningOn) R.string.auto_brightness_on else R.string.auto_brightness_off
                )
            )
        }
    }

    fun toggleFlashlight() {
        showIndicatorMessage(
            context.getString(
                when (toggles.toggleFlashlight()) {
                    true -> R.string.flashlight_on
                    false -> R.string.flashlight_off
                    null -> R.string.flashlight_unavailable
                }
            )
        )
    }

    fun toggleDnd() {
        if (!toggles.canToggleDnd()) {
            showIndicatorMessage(context.getString(R.string.dnd_needs_access))
            return
        }
        showIndicatorMessage(
            context.getString(
                when (toggles.toggleDnd()) {
                    true -> R.string.dnd_on
                    false -> R.string.dnd_off
                    null -> R.string.action_unavailable_on_device
                }
            )
        )
    }

    fun toggleAutoRotate() {
        if (!toggles.canWriteSettings()) {
            showIndicatorMessage(context.getString(R.string.auto_rotate_needs_permission))
            return
        }
        showIndicatorMessage(
            context.getString(
                when (toggles.toggleAutoRotate()) {
                    true -> R.string.auto_rotate_on
                    false -> R.string.auto_rotate_off
                    null -> R.string.action_unavailable_on_device
                }
            )
        )
    }

    /**
     * Hands a system action to the accessibility service.
     *
     * The service may be off — it is optional, and the user can switch it off in system settings
     * long after binding an action to a gesture — so the failure is said on the indicator rather
     * than swallowed. A gesture that does nothing with no explanation is the one report worse than
     * a gesture that does the wrong thing.
     */
    private fun performSystemAction(action: String) {
        val service = OverlayRuntime.accessibilityService
        if (service == null) {
            showIndicatorMessage(context.getString(R.string.accessibility_needed_short))
            return
        }
        if (action == HandlerActions.SCREENSHOT) {
            takeCleanScreenshot(service)
            return
        }
        if (!service.performSystemAction(action)) {
            showIndicatorMessage(context.getString(R.string.action_unavailable_on_device))
        }
    }

    /**
     * A screenshot with nothing of this app in it.
     *
     * The bar is made invisible — the window stays, so nothing is rebuilt — the menu and the Deck
     * are closed, and the shutter is pressed a quarter second later, once the compositor has had a
     * frame to drop them. The bar comes back after the system's own capture animation.
     */
    private fun takeCleanScreenshot(service: GestureAccessibilityService) {
        if (!OverlayRuntime.supportsLockAndScreenshot) {
            showIndicatorMessage(context.getString(R.string.action_unavailable_on_device))
            return
        }
        hideContextMenu()
        hideDeck()
        hideIndicator()
        handlerView?.visibility = View.INVISIBLE
        mainHandler.postDelayed({
            if (!service.performSystemAction(HandlerActions.SCREENSHOT)) {
                showIndicatorMessage(context.getString(R.string.action_unavailable_on_device))
            }
            mainHandler.postDelayed({ handlerView?.visibility = View.VISIBLE }, SCREENSHOT_HIDE_AFTER_MS)
        }, SCREENSHOT_HIDE_BEFORE_MS)
    }

    /**
     * Play services' code scanner, run by a small invisible Activity — an overlay window cannot
     * receive an Activity result, and the scanner's own UI is what the user should see.
     */
    private fun openQrScanner() {
        hideDeck()
        val intent = Intent(context, com.newagedevs.gesturevolume.ui.activities.QrScanActivity::class.java)
        if (!launchActivity(intent)) {
            showIndicatorMessage(context.getString(R.string.qr_failed))
        }
    }

    /**
     * "What's this song?" — the Google app's music search, which listens and names what is
     * playing nearby. A plain web search asking the same question when the Google app is absent.
     */
    private fun songSearch() {
        val direct = Intent("com.google.android.googlequicksearchbox.MUSIC_SEARCH")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchActivity(direct)) return
        val fallback = Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, context.getString(R.string.song_search_query))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!launchActivity(fallback)) {
            showIndicatorMessage(context.getString(R.string.song_search_unavailable))
        }
    }

    /**
     * Starts an Activity from the overlay. Always a new task, always guarded: the overlay is not
     * an Activity, and a target that no longer resolves must not take the bar down with it.
     */
    fun launchActivity(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun openApp() {
        val packageManager = context.applicationContext.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.let {
            hideHandlerView()
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.applicationContext.startActivity(it)
        }
    }

    // =============================================================================================
    // Full-screen music overlay (separate feature, unchanged behaviour)
    // =============================================================================================

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    @Suppress("DEPRECATION")
    private fun createOverlayView() {
        if (overlayView == null) {
            overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_layout, null).apply {
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
                windowType,
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
                android.util.Log.e(TAG, "music overlay addView failed", e)
                overlayView = null
            }
        }
    }

    private fun handleOverlayTouchEvent(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressHandler.postDelayed(longPressedRunnable, 500L)
                actionDownPoint = PointF(event.x, event.y)
                touchDownTime = now()
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
            // Any tap dismisses the overlay. That is the behaviour, stated plainly.
            hideOverlayView()
            createOverlayHandler()
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

    // The host's own resources, not Resources.getSystem(): only these follow the current display
    // configuration, so only these give a correct density after a rotation.
    private fun dpToPx(dp: Float): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
