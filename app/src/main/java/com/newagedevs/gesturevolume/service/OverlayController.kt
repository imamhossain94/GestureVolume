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
import com.newagedevs.gesturevolume.overlay.PanelBackdrop
import com.newagedevs.gesturevolume.overlay.deck.AppShortcut
import com.newagedevs.gesturevolume.overlay.deck.DeckActions
import com.newagedevs.gesturevolume.overlay.deck.DeckConfig
import com.newagedevs.gesturevolume.overlay.deck.DeckEnvironment
import com.newagedevs.gesturevolume.overlay.deck.DeckModel
import com.newagedevs.gesturevolume.overlay.deck.DeckOverlay
import com.newagedevs.gesturevolume.overlay.deck.DeckRootView
import com.newagedevs.gesturevolume.overlay.deck.DeckState
import com.newagedevs.gesturevolume.overlay.deck.DeckSurfaces
import com.newagedevs.gesturevolume.overlay.deck.DeckTiles
import com.newagedevs.gesturevolume.data.local.QuickDialEntry
import com.newagedevs.gesturevolume.ui.activities.MainActivity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import com.newagedevs.gesturevolume.ui.view.HandlerGestureDetector
import com.newagedevs.gesturevolume.ui.view.HandlerView
import com.newagedevs.gesturevolume.ui.view.QuickSliderView
import com.newagedevs.gesturevolume.utils.AudioStreamCatalog
import com.newagedevs.gesturevolume.utils.AudioStreamResolver
import com.newagedevs.gesturevolume.utils.BrightnessController
import com.newagedevs.gesturevolume.utils.DeviceToggles
import com.newagedevs.gesturevolume.utils.ContextMenuLayout
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog
import com.newagedevs.gesturevolume.utils.PanelTheme
import com.newagedevs.gesturevolume.utils.HandlerActions
import com.newagedevs.gesturevolume.utils.VolumeController
import com.newagedevs.gesturevolume.utils.safeDrawableIdOrDefault
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import android.provider.Settings

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

        /** How long the Quick panel takes to grow out of the bar. */
        private const val PANEL_OPEN_MS = 220L

        /** How long it stays after the finger lifts, so the value just set can be read. */
        private const val PANEL_LINGER_MS = 550L

        /** How long a panel nobody has touched waits before putting itself away. */
        private const val PANEL_IDLE_MS = 4000L

        /**
         * The narrowest the Quick panel may be drawn, whatever the thickness setting says.
         *
         * The setting was chosen when the panel was a readout beside a finger already committed to
         * a stroke, where 24dp was legible and nothing needed aiming at. It is a touch target now,
         * so it gets the platform's floor.
         */
        private const val PANEL_MIN_THICKNESS_DP = 48f

        /** The ink a pale panel writes in: dark enough to read on frosted glass, not pure black. */
        private const val PANEL_LIGHT_INK = 0xFF15161A.toInt()

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
    /**
     * A vertical-swipe binding that fires once rather than stepping, waiting to be run.
     *
     * Set by [resolveAdjustAction] and consumed by `onAdjustBegin`, which is the only caller
     * allowed to act on it. See the note where it is set.
     */
    private var adjustOneShot: String? = null

    /**
     * Whether the vertical swipe in progress is steering the Quick panel.
     *
     * The panel is opened by the same stroke that then sets its value, so for the length of that
     * stroke the swipe's steps go to the panel instead of straight to the volume. It is a separate
     * flag from `adjustEnabled` because the two answer different questions: that one asks whether
     * this swipe adjusts anything at all, this one asks *what*.
     */
    private var adjustDrivesPanel = false

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

    /**
     * The blur behind the Deck: one window under the strip, one under the expanded card.
     *
     * The Deck's own window has to be full-screen — it catches the tap that dismisses it and hosts
     * the keyboard for search — so it cannot carry the blur itself; see [PanelBackdrop] for why
     * the flag-based blur cannot be clipped to part of a window either. These sit underneath and
     * follow whatever the Deck reports from its layout pass.
     */
    private var deckStripBackdrop: PanelBackdrop? = null
    private var deckCardBackdrop: PanelBackdrop? = null

    /**
     * The blur behind the long-press menu and behind the Quick panel.
     *
     * Both used to ask for `FLAG_BLUR_BEHIND` instead, which blurs the *whole screen* — the
     * window's own size has nothing to do with it — and which a good many devices quietly decline
     * to honour for an overlay at all. The result was the worst of both: on the phones that did
     * honour it, everything behind a small card washed out; on the ones that did not, a panel
     * advertised as frosted came up as a flat grey rectangle with nothing behind it blurred. A
     * backdrop clipped to the panel is the thing that actually looks like glass, and it is the
     * mechanism the Deck was already using two hundred lines further down.
     */
    private var menuBackdrop: PanelBackdrop? = null
    private var sliderBackdrop: PanelBackdrop? = null
    private var deckRoot: DeckRootView? = null
    private val deckState = DeckState()
    private val deckEnvironment: DeckEnvironment by lazy {
        DeckEnvironment(context, preference, toggles, volume, brightness, deckState)
    }

    /** Resolved icons for the pinned apps, keyed by the package list they were built from. */
    private var appShortcutCache: Pair<List<String>, List<AppShortcut>>? = null

    private val deckAutoCloseRunnable = Runnable { hideDeck() }

    /** Closes the Quick panel a beat after the finger lifts. See `onAdjustFinished`. */
    private val sliderCloseRunnable = Runnable { hideQuickSlider() }

    /** Closes a Quick panel that has been sitting untouched. */
    private val sliderIdleRunnable = Runnable { hideQuickSlider() }
    private val timerFinishRunnable = Runnable { onTimerFinished() }

    // ---- the expanding quick slider ----------------------------------------------------------

    private var sliderView: QuickSliderView? = null

    /** The open panel's window rectangle, kept so the blur behind it can be sized to match. */
    private var sliderParams: WindowManager.LayoutParams? = null

    /**
     * The collapse animation, held so a pull that starts again mid-retract can take it over.
     *
     * Without this, swiping twice in quick succession — which is what a user does the moment the
     * first swipe does not do what they expected — leaves an animator still driving the expansion
     * of a view the new gesture is also driving, and the bar jitters between the two.
     */
    private var sliderCollapse: ValueAnimator? = null

    /**
     * Whether the open slider is live, or still the pre-commit stretch.
     *
     * The window exists for both. What separates them is that a stretch controls nothing: it has
     * latched no target, disabled no adaptive brightness and written no value, so abandoning it
     * costs the user nothing and leaves nothing to put back.
     */
    private var sliderCommitted = false

    /**
     * Whether this panel has already dealt with adaptive brightness.
     *
     * The switch-off is deferred to the first write rather than done when the panel opens, and
     * this is what keeps it to one attempt per panel. Opening is not using: a panel the user
     * summons and then dismisses untouched must leave their settings exactly as it found them,
     * and turning adaptive brightness off on the way in would silently cost them it every time
     * they changed their mind.
     */
    private var sliderAutoBrightnessHandled = false

    /** What this panel is driving, latched when it opens. */
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
        registerVolumeWatcher()
        restoreTimer()
    }

    /**
     * Makes the bar answer the hardware volume keys, not just its own gestures.
     *
     * There is no public callback for "the volume changed" — the broadcast everyone reaches for is
     * hidden API. What there is: the volume indices live in `Settings.System`, and the system
     * writes them there whenever anything moves one, so an observer on that table hears the rocker,
     * the system panel, and another app's slider alike.
     *
     * That breadth is also why it is filtered rather than trusted. The table changes for a great
     * many reasons that are not volume, so the handler only speaks up when the stream it would
     * itself be driving has actually landed on a different level.
     */
    private fun registerVolumeWatcher() {
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                volumeWatcher,
            )
        }
    }

    private val volumeWatcher = object : android.database.ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) = onVolumeChangedElsewhere()
    }

    /** The level the bar last showed, so a settings write that moved nothing stays quiet. */
    private var lastSeenVolumePercent: Int? = null

    /** True between the first and last step of a swipe that is driving the volume itself. */
    private var adjustingBySwipe = false

    /**
     * Something moved the volume. Show it on the bar, unless the something was us.
     *
     * The Quick panel and the swipe gesture both put the level on the bar themselves and both
     * write through `Settings.System` on the way, so without this guard every step of a swipe
     * would arrive back here and re-post the readout the swipe had just posted — twice the work
     * for the same number, and a readout that outstayed the gesture by its own full timeout.
     */
    private fun onVolumeChangedElsewhere() {
        if (destroyed) return
        if (sliderView != null) return
        if (adjustingBySwipe) return
        val res = volume.resolve(preference.getVolumeStreamMode())
        val percent = volume.percent(res) ?: return
        if (percent == lastSeenVolumePercent) return
        lastSeenVolumePercent = percent
        if (handlerView == null) return
        showVolumePercentOnHandler(percent, res)
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
        hideContextMenu()
        hideDeck()
        // After hideHandlerView, whose gestureDetector.cancel() is what asks a slider still under
        // the finger to collapse. This turns that collapse into an immediate removal.
        dismissQuickSliderNow()

        (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.unregisterDisplayListener(displayListener)
        runCatching { context.contentResolver.unregisterContentObserver(volumeWatcher) }

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
            // The transient pair, sent as the app comes to the foreground and leaves again.
            //
            // Neither touches `appInForeground`, and that is the fix for a bug this once had: the
            // activity is not the only sender. `repairServiceIfNeeded` starts the service with
            // "show" from `onStart`, one line after the activity has recorded that it *is* in the
            // foreground — so a "show" that wrote the flag immediately undid it, and the bar was
            // drawn over the app that had just asked for it to go away. The boot receiver and the
            // notification send "show" too, from further away still. Where the app is, is the
            // activity's own business; these commands only say what to do about it.
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
        // The bar is never drawn over the app's own UI. Checked here, at the one place that puts
        // it on screen, rather than trusted to arrive as a "hide" command: on a cold start the
        // activity's hide races the service coming up, and whichever order those two land in, the
        // check below gives the same answer. See SharedPref.isAppInForeground.
        if (preference.isAppInForeground()) return
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

        val shapeStyle = preference.getHandlerShape()
        val shapeFlare = preference.getHandlerShapeFlare()

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
            setShapeStyle(shapeStyle, shapeFlare)

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
     * Places a backdrop behind a rectangle a panel reported from its own layout pass.
     *
     * Every panel window here fits the system bars and the cutout, so what they measure and report
     * is relative to the usable frame. A [PanelBackdrop] is a dialog and cannot be made to share
     * that space — see its [PanelBackdrop.setBounds] — so the offset is added on the way in. One
     * helper rather than three call sites doing it, because the failure when one of them forgets
     * is a blurred rectangle floating a status bar away from the panel, which reads as a rendering
     * bug rather than as a missing addition.
     */
    private fun PanelBackdrop.setFrameBounds(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        cornerRadiusPx: Float,
    ) {
        val f = frame
        setBounds(
            left + (f?.insetLeft ?: 0),
            top + (f?.insetTop ?: 0),
            width,
            height,
            cornerRadiusPx,
        )
    }

    /**
     * A pane of blurred glass, sized later by whoever is going to sit on it.
     *
     * Returns null — and the panel simply goes unblurred — below Android 12, when the material
     * asks for no blur, and on any device where the system has turned cross-window blur off. It
     * does that in battery saver and on hardware that cannot afford it, so this is the common case
     * rather than an exotic one, and it is why [PanelTheme]'s alphas are chosen to look deliberate
     * without the blur too.
     *
     * Shown immediately, empty and 1x1 in the corner. It has to exist *before* the panel's own
     * window is added, because stacking among overlay windows follows the order they were added
     * and a backdrop created afterwards would blur the panel instead of the screen behind it. The
     * blur is switched on only when real bounds arrive, so nothing flashes in the wrong place.
     */
    private fun newPanelBackdrop(): PanelBackdrop? {
        if (destroyed) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val radiusDp = PanelTheme.blurRadiusDp(preference.getPanelTheme())
        if (radiusDp <= 0) return null
        val wm = windowManager ?: return null
        if (!wm.isCrossWindowBlurEnabled) return null
        return PanelBackdrop(context, windowType, dpToPx(radiusDp.toFloat())).apply { show() }
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
            adjustingBySwipe = true
            adjustDirection = 0
            adjustOneShot = null
            adjustDrivesPanel = false
            resolveAdjustAction(initialDirection)

            if (adjustOneShot == HandlerActions.OPEN_QUICK_SLIDER) {
                adjustOneShot = null
                // Opened *synchronously*, where every other action here is posted. Posting is for
                // actions that tear the handler's window down from inside its own input dispatch;
                // this adds a separate window and leaves the handler's in place, still receiving
                // the stroke. A posted open would arrive a frame after the swipe had already begun
                // producing steps, and those steps would have nothing to land on.
                if (openQuickSliderForSwipe()) {
                    adjustDrivesPanel = true
                    // The sweep is re-scaled to the panel's own range, so one full swipe covers
                    // the track once however many steps the chosen control happens to have.
                    gestureDetector?.setStepCount(sliderSteps)
                }
            } else {
                adjustOneShot?.let { action -> mainHandler.post { runAction(action) } }
            }
        }

        override fun onAdjustStep(direction: Int): Boolean {
            // Before the re-resolve below, and deliberately: this stroke has already committed to
            // the panel, and re-reading the slot on a reversal would try to open a second one.
            if (adjustDrivesPanel) return nudgeQuickSlider(direction)

            // Re-resolved per step, not latched at gesture start: swipe up and swipe down are two
            // independent settings, so reversing mid-gesture has to switch to the other one. Latching
            // meant a swipe that started upward kept driving the swipe-UP action on the way back
            // down — a "Decrease brightness" swipe-down would silently move the volume instead.
            resolveAdjustAction(direction)
            if (!adjustEnabled) return false
            return if (adjustIsBrightness) stepBrightness(direction) else stepVolume(direction)
        }

        override fun onAdjustEnd() {
            adjustingBySwipe = false
            if (adjustDrivesPanel) {
                adjustDrivesPanel = false
                // The panel outlives the swipe. It stays up for a beat so the value just set can
                // be read, and it is touchable throughout — so a stroke that overshot can be
                // corrected with a second touch rather than repeated from the bar.
                mainHandler.removeCallbacks(sliderCloseRunnable)
                mainHandler.postDelayed(sliderCloseRunnable, PANEL_LINGER_MS)
            }
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
            // A panel bound to a control this device will not let the app write is not "armed but
            // failing" — it never opens at all, so the long swipe stays inert and the short swipe
            // keeps its immediate timing rather than being deferred for a gesture that cannot work.
            return quickSliderIsWritable(preference.slider.getTarget())
        }

        /**
         * Opened synchronously, unlike the actions above.
         *
         * Those are posted because they can tear down the handler window from inside its own input
         * dispatch. This does the opposite: it adds a separate window and leaves the handler's own
         * in place, still receiving the gesture driving the stretch. Posting it would put a frame
         * of nothing between the swipe qualifying and the bar appearing to move, which is the one
         * moment this gesture has to feel immediate.
         */
        override fun onEdgePullBegin(inward: Boolean) {
            beginQuickSliderPull()
        }

        override fun onEdgePullUpdate(progress: Float) {
            updateQuickSliderPull(progress)
        }

        override fun onEdgePullCancel() {
            hideQuickSlider()
        }

        override fun onQuickSliderBegin(inward: Boolean) {
            commitQuickSliderPull()
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

        // A binding the finger triggers rather than steers — the Quick panel, the Deck, a torch.
        // Recorded, not run: `onAdjustBegin` fires it, and this function is also called on every
        // direction reversal inside a stroke, where firing would mean a wobbling thumb toggling
        // the panel open and shut. Stepping is switched off either way, so the rest of the stroke
        // does nothing.
        if (!HandlerActions.isAdjustSwipe(action)) {
            adjustEnabled = false
            adjustOneShot = action
            return
        }

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
     * Opens the Quick panel beside the bar and leaves it there.
     *
     * **Why it stays.** It used to be the payload of a long inward swipe: it appeared while the
     * finger was still down and the *same* finger set the value by moving vertically. Two things
     * were wrong with that. It shared the inward swipe with the Deck, which is the "they conflict"
     * report — one stroke, two outcomes, told apart by a distance nothing on screen reported. And
     * the finger that opened it was the only thing that could ever move it, so a user who let go
     * to look at what had appeared was left holding a control that had stopped listening. That is
     * the "opens but cannot increase or decrease" report, and both have the same cure: make it a
     * panel rather than a gesture payload. It is opened by an action from any slot, it takes its
     * own touches, and it closes itself once it has been used.
     *
     * The handler's own window is left in place and only its view is hidden. The panel grows out
     * of exactly where the bar was drawn, so the two must not be visible at once — and the window
     * must stay, because removing it is what would strand the gesture that asked for the panel.
     */
    private fun showQuickSliderPanel() {
        // Already up: treat a second trigger as "put it away", so whatever gesture opens the panel
        // also closes it and the user is never left hunting for a way out.
        if (sliderView != null) {
            hideQuickSlider()
            return
        }
        if (!openQuickSliderWindow()) return
        animateQuickSliderOpen()
    }

    /**
     * The long inward swipe reached the short threshold: start the bar stretching.
     *
     * Same window, same view, opened the same way — the only difference from [showQuickSliderPanel]
     * is who moves the expansion. Here it is the finger, one pixel for one pixel, and the panel is
     * not live until [armQuickSlider]. Everything with a side effect waits for that, because until
     * the far threshold this stroke is still equally on course for the Deck.
     */
    private fun beginQuickSliderPull() {
        if (sliderView != null) return
        openQuickSliderWindow()
    }

    /**
     * Opens the panel for a vertical swipe that is going to keep driving it.
     *
     * Unlike [showQuickSliderPanel] this never toggles: a swipe means "put the panel up and let me
     * set a value", and a second swipe while one is open means the same thing again. It also arms
     * the panel immediately instead of waiting for the grow animation to finish, because the
     * finger driving it is on the *handler's* window — the reason arming is deferred on the other
     * route is to stop a touch landing on a four-pixel-tall track, and there is no such touch here.
     *
     * @return true when there is a live panel to steer.
     */
    private fun openQuickSliderForSwipe(): Boolean {
        if (sliderView == null) {
            if (!openQuickSliderWindow()) return false
            animateQuickSliderOpen()
        }
        val view = sliderView ?: return false
        mainHandler.removeCallbacks(sliderCloseRunnable)
        armQuickSlider(view)
        return true
    }

    /**
     * Moves the panel one step, for the swipe that opened it.
     *
     * Steps rather than a raw fraction because that is the currency the gesture engine already
     * deals in, and because it puts the swipe and a touch on the track on the same quantisation —
     * the haptics, the number and the value written all come from one rounding either way.
     *
     * @return false at the ends of the range, which is what stops the detector banking travel the
     *   control cannot use.
     */
    private fun nudgeQuickSlider(direction: Int): Boolean {
        if (!sliderCommitted) return false
        val steps = sliderSteps.coerceAtLeast(1)
        val next = (sliderLastStep + direction).coerceIn(0, steps)
        if (next == sliderLastStep) return false
        restartQuickSliderIdleTimeout()
        // Animated, unlike a drag: there is no finger on the track to explain the movement, so
        // the fill has to travel the distance itself or the value appears to teleport.
        applyQuickSlider(next / steps.toFloat(), animated = true)
        return true
    }

    /** One frame of the stretch, straight from the finger. */
    private fun updateQuickSliderPull(progress: Float) {
        if (sliderCommitted) return
        sliderView?.setExpansion(progress)
    }

    /**
     * The pull passed the far threshold: the stretch becomes the panel.
     *
     * No animation, because the finger has already dragged the shape to full extension — that is
     * what committing means here. What changes is what the shape *is*.
     */
    private fun commitQuickSliderPull() {
        val view = sliderView ?: return
        view.setExpansion(1f)
        armQuickSlider(view)
    }

    /**
     * Creates the panel's window, collapsed onto the bar and not yet live.
     *
     * @return false when there is nothing to open — no bar to grow out of, or a target this
     *   device will not let the app write.
     */
    private fun openQuickSliderWindow(): Boolean {
        if (destroyed) return false
        val wm = windowManager ?: return false
        val barParams = handlerParams ?: return false
        val currentFrame = frame ?: return false
        // A retraction still running is left alone rather than cancelled: cancelling it would
        // freeze a view part-way out with nothing left to finish or remove it. It owns its own
        // teardown, and it checks whether this new window has taken its place before restoring
        // the bar — see the listener in hideQuickSlider.

        val settings = preference.slider

        sliderTarget = settings.getTarget()
        sliderResolution = quickSliderResolution(sliderTarget)

        // Nothing to control, so nothing to open. Reported rather than opened-and-inert: a panel
        // that appears and refuses every touch is indistinguishable from the bug this rework
        // exists to fix.
        if (!quickSliderIsWritable(sliderTarget)) {
            showIndicatorMessage(
                context.getString(
                    if (sliderTarget == QuickSliderStore.TARGET_BRIGHTNESS) {
                        R.string.brightness_needs_permission_short
                    } else {
                        R.string.action_not_available_msg
                    }
                )
            )
            return false
        }

        sliderSteps = if (sliderTarget == QuickSliderStore.TARGET_BRIGHTNESS) {
            brightness.stepCount
        } else {
            sliderResolution?.stepCount ?: 1
        }.coerceAtLeast(1)

        val openValue = quickSliderCurrentValue() ?: 0f
        sliderOpenValue = openValue
        sliderLastStep = (openValue * sliderSteps).roundToInt()

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

        // The bar as *drawn*, which is not the same as the window it lives in: that window is
        // widened to MIN_TOUCH_WIDTH_DP so there is something to aim at, and the bar is pushed
        // against its outer edge with the difference left as dead space. Morphing out of the
        // window instead of the bar is how a 10dp Edge preset would flick to 28dp wide on the
        // first frame.
        val drawnWidthPx = dpToPx(preference.getHandlerWidthDp()).coerceIn(1, barParams.width)

        // Floored at the bar's own size so the collapsed rect fits inside the window, and at
        // PANEL_MIN_THICKNESS_DP because this is now a control the user aims at with a thumb
        // rather than a readout beside a finger that is already committed to a stroke.
        val thicknessPx = dpToPx(settings.getThicknessDp())
            .coerceAtLeast(dpToPx(PANEL_MIN_THICKNESS_DP))
            .coerceAtLeast(drawnWidthPx)
        val lengthPx = dpToPx(settings.getLengthDp())
            .coerceAtMost(currentFrame.usableHeight.coerceAtLeast(1))
            .coerceAtLeast(barParams.height)
        val isLeft = handlerIsLeft()

        // Absolute screen coordinates of the drawn bar, from the window and which way it faces.
        val drawnLeft = if (isLeft) barParams.x else barParams.x + barParams.width - drawnWidthPx

        // The panel is the bar, grown — and it grows into its *own* shape and colour rather than
        // keeping the bar's.
        //
        // It used to keep them, on the reasoning that a morph with nothing to travel between is a
        // pure growth. What that actually produced was two settings on the Quick panel's own
        // screen that nothing anywhere read: a corner radius and a track colour the user could
        // set and never see. The view has interpolated both from the collapsed end to the
        // expanded end since it was written; it was simply being handed the same value twice. So
        // the bar's colour and corners stay the *starting* point, which is what keeps frame zero
        // an exact stand-in for the bar, and the panel's own settings are where it arrives.
        //
        // Read live, every time, so a change on either screen reaches the panel without the
        // service being restarted.
        val handlerColor = ColorUtils.setAlphaComponent(
            preference.getHandlerColor(),
            preference.getHandlerBackgroundAlpha().coerceIn(0, 255)
        )
        // Where the morph starts: the bar, exactly as it is drawn.
        val barCornerTL = preference.getHandlerCornerRadiusTL()
        val barCornerTR = preference.getHandlerCornerRadiusTR()
        val barCornerBL = preference.getHandlerCornerRadiusBL()
        val barCornerBR = preference.getHandlerCornerRadiusBR()

        // Where it arrives: one radius, all four corners, because a panel is a plain pill and the
        // asymmetry the bar has is about meeting a screen edge, which the open panel does not do.
        val panelCorner = settings.getCornerDp()

        val theme = preference.getPanelTheme()
        // A pale material supplies the track, and with it the ink: this panel writes its number and
        // its icon in exactly two colours — the fill over the empty half, the track over the filled
        // half — so a white fill on a white pane loses both at once, not just the fill.
        //
        // The *collapsed* colour stays the bar's either way. That is what the panel grows out of,
        // and the blend between the two is the morph; starting it anywhere else would make the
        // first frame jump to a colour the bar never had.
        val paleSurface = PanelTheme.panelSurface(theme)

        val view = QuickSliderView(context).apply {
            if (paleSurface != null) {
                // The user's fill colour still wins wherever it can be seen. Only one that would
                // vanish into a pale pane — white on frosted white, which is the default — is
                // replaced, the same rule the Deck applies to its accent.
                val chosenFill = settings.getFillColor()
                val fill = if (isTooPaleFor(chosenFill, paleSurface.toInt())) PANEL_LIGHT_INK else chosenFill
                setColors(paleSurface.toInt(), fill)
                // 1f, because the material's own alpha is already in that colour. Thinning it by
                // the multiplier as well would fade the pane twice.
                setPanelTheme(1f, PanelTheme.hasLitEdge(theme), light = true)
            } else {
                setColors(settings.getTrackColor(), settings.getFillColor())
                setPanelTheme(PanelTheme.surfaceAlpha(theme), PanelTheme.hasLitEdge(theme))
            }
            setExpandedCorners(panelCorner, panelCorner, panelCorner, panelCorner)
            setCollapsedAppearance(handlerColor, barCornerTL, barCornerTR, barCornerBL, barCornerBR)
            setIcon(if (settings.getShowIcon()) quickSliderIcon(sliderTarget) else null)
            setFillStyle(settings.getFillStyle())
            setShowValue(settings.getShowValue())
            setValue(openValue)
        }

        val params = WindowManager.LayoutParams(
            thicknessPx,
            lengthPx,
            windowType,
            // Touchable, unlike every other window this class puts up. FLAG_NOT_TOUCH_MODAL keeps
            // everything outside the panel working normally, and WATCH_OUTSIDE_TOUCH is what lets
            // a tap anywhere else dismiss it without that tap being swallowed.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            // Anchored on the bar, not on the screen edge. The bar can be parked anywhere — free
            // placement on both axes is a feature — and a window pinned to the edge would make the
            // first frame of the morph jump sideways to meet it. The window grows inward from the
            // bar's own side, then gets clamped into the frame; the collapsed rect below is
            // computed after that clamp, so the bar's position survives whatever the clamp did.
            val wantX = if (isLeft) drawnLeft else drawnLeft + drawnWidthPx - thicknessPx
            x = wantX.coerceIn(0, (currentFrame.usableWidth - thicknessPx).coerceAtLeast(0))
            val barCenterY = barParams.y + barParams.height / 2
            y = (barCenterY - lengthPx / 2)
                .coerceIn(0, (currentFrame.usableHeight - lengthPx).coerceAtLeast(0))
            fitUsableFrame(this)
        }

        // Window coordinates, from the two absolutes, so the rect lands on the bar even where the
        // clamps above moved the window off its preferred spot.
        view.setCollapsedRect(
            (drawnLeft - params.x).toFloat(),
            (barParams.y - params.y).toFloat(),
            (drawnLeft - params.x + drawnWidthPx).toFloat(),
            (barParams.y - params.y + barParams.height).toFloat()
        )
        view.setExpansion(0f)
        view.listener = quickSliderListener
        view.setOnTouchOutside { hideQuickSlider() }

        // Before the panel's own window, so it stacks underneath. Its bounds are set once the
        // morph has finished — see [armQuickSlider]. Blurring a rectangle the size of the open
        // panel while a bar-sized sliver is still growing into it is the exact glitch the Deck's
        // first attempt at this had.
        sliderBackdrop = newPanelBackdrop()

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "quick panel addView failed", e)
            sliderBackdrop?.dismiss()
            sliderBackdrop = null
            return false
        }
        sliderView = view
        sliderParams = params
        // Not live yet, on either route. The action route arms it when its open animation
        // finishes; the pull route when the finger passes the far threshold.
        sliderCommitted = false
        sliderAutoBrightnessHandled = false

        // Faded out, NOT made INVISIBLE, and this is load-bearing rather than a style choice.
        //
        // This view is the root of its own window. ViewRootImpl reports the root's visibility to
        // WindowManagerService on the next traversal, and a window reported not-visible is dropped
        // from input dispatch — which would take the bar out of the input stack entirely.
        // Alpha leaves the view VISIBLE to the window system and merely stops it being drawn.
        //
        // Instant rather than faded, and safe to be instant only because of the morph: what
        // replaces the bar on this frame is a rectangle of the same size, colour and corner in the
        // same place. Only the icon differs, and the view fades that in.
        handlerView?.let { bar ->
            bar.animate().cancel()
            bar.alpha = 0f
        }

        return true
    }

    /**
     * Grows the panel out of the bar on a clock, for the routes with no finger driving it.
     *
     * Arms it at the end rather than at the start: a track four pixels tall that already answers
     * touches turns the first frame of this animation into a brightness the user did not choose.
     */
    private fun animateQuickSliderOpen() {
        val view = sliderView ?: return
        sliderCollapse?.cancel()
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PANEL_OPEN_MS
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { view.setExpansion(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (sliderCollapse === animation) sliderCollapse = null
                    if (sliderView === view) armQuickSlider(view)
                }
            })
        }
        sliderCollapse = animator
        animator.start()
    }

    /**
     * Makes an open panel live: it answers touches, it buzzes, and it starts counting down.
     *
     * The one place a Quick panel becomes something the user can change a setting with, whichever
     * route opened it.
     */
    private fun armQuickSlider(view: QuickSliderView) {
        if (sliderCommitted) return
        sliderCommitted = true
        view.setCommitted()
        view.setInteractive(true)

        // The glass arrives with the open panel rather than during the morph. The window is the
        // panel — the track fills it once expanded — so its own rectangle is what to blur, and
        // the largest of the four corner radii is the one that keeps the blur inside the shape.
        sliderParams?.let { params ->
            // The panel's own radius, because by the time this runs the morph is over and the
            // panel is wearing it. Using the bar's here left the blur rounded to a different
            // shape than the glass it was sitting behind.
            val corner = preference.slider.getCornerDp()
            sliderBackdrop?.setFrameBounds(
                params.x, params.y, params.width, params.height, dpToPx(corner).toFloat()
            )
        }

        // The buzz that says the panel is live. Distinct from the per-step ticks: longer, so it
        // cannot be mistaken for a step having already been crossed.
        if (sliderHapticMs > 0L) vibrateQuick(28L, sliderHapticAmplitude)

        restartQuickSliderIdleTimeout()
    }

    /**
     * The panel's own touches, routed back into the same quantise-and-write path the swipe uses.
     */
    private val quickSliderListener = object : QuickSliderView.Listener {
        override fun onValuePicked(fraction: Float) {
            mainHandler.removeCallbacks(sliderCloseRunnable)
            restartQuickSliderIdleTimeout()
            applyQuickSlider(fraction)
        }

        override fun onAdjustFinished() {
            // "Adjust it and it closes itself." Not instant: the number the user just set is the
            // point of the whole interaction, and a panel that vanishes on the up-stroke never
            // lets them read it. One beat is enough to see the result and short enough that the
            // panel never feels like something they have to dismiss.
            mainHandler.removeCallbacks(sliderIdleRunnable)
            mainHandler.removeCallbacks(sliderCloseRunnable)
            mainHandler.postDelayed(sliderCloseRunnable, PANEL_LINGER_MS)
        }
    }

    /**
     * Closes a panel nobody touched.
     *
     * Longer than the linger above, because this is the timeout for a panel opened by mistake or
     * abandoned mid-thought, and the cost of being early is that the control disappears while the
     * user is still deciding what to do with it.
     */
    private fun restartQuickSliderIdleTimeout() {
        mainHandler.removeCallbacks(sliderIdleRunnable)
        mainHandler.postDelayed(sliderIdleRunnable, PANEL_IDLE_MS)
    }

    /**
     * Applies one touch on the panel.
     *
     * Takes the value the finger is *on*, not a distance it has moved from where the panel opened.
     * The two differ in more than arithmetic: a delta is only meaningful while one unbroken stroke
     * owns the control, which is exactly the assumption that left the old slider unadjustable once
     * its opening stroke had ended. An absolute fraction is meaningful on every touch, including
     * the fifth one, which is what makes this a control rather than a readout.
     *
     * Quantised to [sliderSteps] before anything else happens, so the number the user reads, the
     * value written to the system and the boundary the haptics fire on are all the same decision
     * made once. Reading them off three separate roundings is how a slider ends up buzzing without
     * moving, or showing 41% while the system holds 40%.
     */
    private fun applyQuickSlider(fraction: Float, animated: Boolean = false) {
        val view = sliderView ?: return
        if (!sliderCommitted) return
        val target = fraction.coerceIn(0f, 1f)

        /*
         * The bar goes exactly where the finger is. Only what is written underneath is quantised.
         *
         * It used to be drawn at the quantised value, and that is the whole of "the slider is not
         * smooth": a stream's volume is an integer index, and on the streams with few of them —
         * seven for ring and alarm on most phones — one index is fourteen points of the range. So
         * the fill sat still through most of a drag and then jumped a seventh of the track at
         * once, which looks like a control that is fighting the finger rather than following it.
         *
         * Every system volume slider does it this way: the bar is continuous, the audio is not.
         */
        if (animated) view.animateValue(target) else view.setValue(target)

        // `sliderSteps` is the stream's own index count, so this *is* the hardware index — one
        // apart is as fine a move as the platform can make.
        val step = (target * sliderSteps).roundToInt().coerceIn(0, sliderSteps)
        if (step == sliderLastStep) return
        sliderLastStep = step

        val applied = if (sliderTarget == QuickSliderStore.TARGET_BRIGHTNESS) {
            // Before the write, never after: the light sensor overwrites anything the app sets
            // within a second or two, which reads as the panel not working rather than as a
            // setting fighting it. Restored by the same restoreAutoBrightnessIfOurs the swipe
            // gesture already uses, so the user gets it back when the service stops.
            if (!sliderAutoBrightnessHandled) {
                sliderAutoBrightnessHandled = true
                if (preference.slider.getDisableAutoBrightness()) {
                    brightness.disableAutoBrightnessIfNeeded()
                    if (brightness.autoDisabledByFraction) preference.setBrightnessAutoWasOn(true)
                }
            }
            brightness.setFraction(step / sliderSteps.toFloat()) != null
        } else {
            val res = sliderResolution
            // By index rather than by percentage. Going through a whole-number percent on a
            // seven-step stream lands two consecutive indices on the same percent and makes
            // others unreachable, so a slow drag skipped levels and stuck on others.
            res != null && volume.setIndex(res, res.minIndex + step, showUi = false) != null
        }

        // No buzz for a step the system refused. The tick is feedback about the control moving,
        // and a control pinned at an end that keeps ticking tells the user it is still moving.
        if (applied && sliderHapticMs > 0L) vibrateQuick(sliderHapticMs, sliderHapticAmplitude)
    }

    /**
     * Retracts the track back into the bar, along the path the finger dragged it out on.
     *
     * The same interpolation the pull used, run backwards on a clock instead of a finger — so the
     * shape shrinks, the corner tightens and the colour returns to the bar's, all together, and
     * the last frame is the bar. That is the whole trick, and it is why the bar's own alpha is
     * restored at the *end* rather than at the start: for the length of the animation the thing on
     * screen is standing in for the bar, and putting the real one back underneath it would draw
     * both, at full size, through a shape that is no longer either.
     *
     * Eased out rather than in. Retraction is the tail of a gesture the user has finished with —
     * it should leave quickly and settle, not gather speed on its way out, which is what the
     * accelerating fade this replaces did.
     */
    private fun hideQuickSlider() {
        mainHandler.removeCallbacks(sliderCloseRunnable)
        mainHandler.removeCallbacks(sliderIdleRunnable)
        val view = sliderView ?: return
        sliderView = null
        sliderResolution = null
        sliderLastStep = -1
        sliderCommitted = false
        // Deaf from this instant, not from the end of the animation. It is still on screen and
        // still touchable for the length of the retraction, and a touch landing on a shape that is
        // shrinking would be read against a track whose geometry no longer means anything.
        view.listener = null
        view.setOnTouchOutside(null)
        view.setInteractive(false)
        sliderAutoBrightnessHandled = false

        // From wherever it actually is, not from 1. A pull abandoned early has barely opened, and
        // a fixed start would snap it out to full extension to begin retracting from there.
        val from = view.expansion()
        val animator = ValueAnimator.ofFloat(from, 0f).apply {
            // Scaled by how far there is to travel, so a stretch abandoned at a tenth of the way
            // out does not take as long to disappear as a full track. Floored so the shortest
            // ones are still a movement rather than a blink.
            duration = (ANIM_DURATION_MS * from).toLong().coerceIn(90L, ANIM_DURATION_MS)
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { view.setExpansion(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (sliderCollapse === animation) sliderCollapse = null
                    // Only when nothing has taken this window's place. A second swipe landing
                    // inside the ~200ms retraction opens a new stretch, and that stretch is
                    // standing in for the bar exactly as this one was — putting the bar back now
                    // would draw it alongside its own replacement until the new gesture ended.
                    if (sliderView == null) handlerView?.alpha = 1f
                    removeQuickSliderView(view)
                }
            })
        }
        sliderCollapse = animator
        animator.start()
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
        mainHandler.removeCallbacks(sliderCloseRunnable)
        mainHandler.removeCallbacks(sliderIdleRunnable)
        // First, because a retraction started a moment ago has already handed its view over to
        // the animator and cleared sliderView — so without this, the one case that most needs
        // taking off screen at once is the one this would walk straight past. `end` runs the
        // listener, which is what actually removes it.
        sliderCollapse?.end()
        sliderCollapse = null

        val view = sliderView ?: return
        sliderView = null
        sliderResolution = null
        sliderLastStep = -1
        sliderCommitted = false
        sliderAutoBrightnessHandled = false
        view.listener = null
        view.setOnTouchOutside(null)
        view.setInteractive(false)
        handlerView?.alpha = 1f
        removeQuickSliderView(view)
    }

    private fun removeQuickSliderView(view: View) {
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
            // Already gone — the service was torn down while the collapse was still running.
        }
        // With the panel, never after it: a blurred rectangle left on screen for even one frame
        // after the thing it was behind has gone reads as a smear rather than as a panel closing.
        sliderBackdrop?.dismiss()
        sliderBackdrop = null
        sliderParams = null
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

        val entries = HandlerActionCatalog.contextMenuEntries(preference.getContextMenuOrder())
        val grid = preference.getContextMenuLayout() == ContextMenuLayout.GRID
        if (entries.isEmpty()) return

        val anchor = IntRect(params.x, params.y, params.x + params.width, params.y + params.height)
        val frameSize = IntSize(currentFrame.usableWidth, currentFrame.usableHeight)

        val menuHost = OverlayComposeHost(context)
        val panelTheme = preference.getPanelTheme()
        val panelAnimation = preference.getPanelAnimation()
        menuHost.setContent {
            // The pale materials carry dark ink, so the scheme underneath everything the panel
            // does not colour by hand has to flip with them. See OverlayTheme.
            OverlayTheme(light = PanelTheme.isLight(panelTheme)) {
                ContextMenuOverlay(
                    entries = entries,
                    anchor = anchor,
                    frame = frameSize,
                    grid = grid,
                    theme = panelTheme,
                    animation = panelAnimation,
                    onSelect = { entry ->
                        hideContextMenu()
                        // Posted for the same reason the tap actions are: "Hide Handler" and
                        // "Open App" destroy windows, and doing that from inside a click dispatch
                        // tears down a view hierarchy that is still being walked.
                        mainHandler.post { runAction(entry.action) }
                    },
                    onDismiss = { hideContextMenu() },
                    onCardBounds = { rect, cornerPx ->
                        menuBackdrop?.setFrameBounds(
                            rect.left, rect.top, rect.width, rect.height, cornerPx
                        )
                    },
                )
            }
        }

        // A full-screen root rather than a card-sized window: it is what catches the tap outside
        // the menu that dismisses it. The window is not focusable, so there is no back-button
        // route to close it and an outside tap is the only way out.
        // Before the menu's own window, so it stacks underneath. See [newPanelBackdrop].
        menuBackdrop = newPanelBackdrop()

        try {
            wm.addView(menuHost.view, fullScreenParams(focusable = false))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "context menu addView failed", e)
            menuBackdrop?.dismiss()
            menuBackdrop = null
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
        menuBackdrop?.dismiss()
        menuBackdrop = null
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

        // Added *before* the Deck's own window so it sits underneath: windows of the same type
        // stack in the order they arrive.
        addDeckBlurWindow()

        val host = OverlayComposeHost(context)
        val root = DeckRootView(
            context = context,
            onBack = { onDeckBack() },
            onInteraction = { restartDeckAutoClose() }
        )
        host.setContent {
            OverlayTheme(light = PanelTheme.isLight(model.panelTheme)) {
                DeckOverlay(
                    model = model,
                    actions = deckActions,
                    onDismiss = { hideDeck() },
                    onSurfaces = ::updateDeckBlurBounds,
                )
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
            // Deliberately unblurred, where the menu and the Quick panel are not.
            //
            // Window blur covers the whole window, and this window has to be full-screen: it is
            // what catches the tap outside the Deck that dismisses it, and what hosts the keyboard
            // for the search field. Blurring it blurs the entire screen, which is not what the
            // theme is for — the theme dresses the Deck's own panel, and the panel is a strip down
            // one side. There is no way to clip a window's blur to part of itself, so the Deck
            // takes its frosted and glass treatment from its surfaces instead: the translucency in
            // DeckPalette and the lit edge below.
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
        removeDeckBlurWindow()
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

    /**
     * Puts up the blur window, off-screen and empty, for the Deck to size later.
     *
     * Starts at 1x1 in the corner rather than at a guessed rectangle: the first real bounds arrive
     * a frame later from the Deck's own layout, and a guess that was wrong would flash a blurred
     * rectangle in the wrong place before being corrected.
     */
    private fun addDeckBlurWindow() {
        if (destroyed || deckStripBackdrop != null) return
        // Both up front, even though the card usually has nothing to sit behind: a backdrop shown
        // later would be added after the Deck's window and land in front of it.
        deckStripBackdrop = newPanelBackdrop()
        deckCardBackdrop = newPanelBackdrop()
    }

    /** Lines each backdrop up with the surface it belongs behind. */
    private fun updateDeckBlurBounds(surfaces: DeckSurfaces) {
        deckStripBackdrop?.setFrameBounds(
            surfaces.strip.left, surfaces.strip.top,
            surfaces.strip.width, surfaces.strip.height,
            surfaces.stripCornerPx,
        )
        val card = surfaces.card
        deckCardBackdrop?.setFrameBounds(
            card?.left ?: 0, card?.top ?: 0,
            card?.width ?: 0, card?.height ?: 0,
            surfaces.cardCornerPx,
        )
    }

    private fun removeDeckBlurWindow() {
        deckStripBackdrop?.dismiss()
        deckStripBackdrop = null
        deckCardBackdrop?.dismiss()
        deckCardBackdrop = null
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
            panelTheme = preference.getPanelTheme(),
            animation = preference.getPanelAnimation(),
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
            HandlerActions.OPEN_QUICK_SLIDER -> showQuickSliderPanel()
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
    /**
     * Whether [colour] would disappear against [surface].
     *
     * Compares perceived brightness rather than the colours themselves: what makes ink vanish is
     * matching the surface's *lightness*, not its hue, and a saturated colour of the same
     * lightness still reads perfectly well against it.
     */
    private fun isTooPaleFor(colour: Int, surface: Int): Boolean =
        kotlin.math.abs(ColorUtils.calculateLuminance(colour) - ColorUtils.calculateLuminance(surface)) < 0.25

    private fun dpToPx(dp: Float): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
