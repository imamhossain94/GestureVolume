package com.newagedevs.gesturevolume.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.app.KeyguardManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
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
import com.newagedevs.gesturevolume.utils.PanelAnimation
import com.newagedevs.gesturevolume.utils.PanelTheme
import com.newagedevs.gesturevolume.utils.HandlerActions
import com.newagedevs.gesturevolume.utils.VolumeController
import com.newagedevs.gesturevolume.utils.safeDrawableIdOrDefault
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import android.provider.Settings
import com.newagedevs.gesturevolume.utils.HandlerShape

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

        /** How long the bar takes to fade out of the Deck's way, and back in once it has gone. */
        private const val HANDLER_FADE_MS = 160L

        /**
         * The margins that make handing the bar to a panel and back an overlap rather than a gap:
         * how long a retracted panel stays over the bar after the bar is told to come back, and the
         * longest a new panel's first frame is waited for before the bar goes anyway. Two windows'
         * frames are not ordered against each other; see [retiringSliders].
         */
        private const val PANEL_HANDOFF_MS = 120L
        private const val BAR_HANDOFF_TIMEOUT_MS = 150L

        /** How often an open volume panel reads the level itself. See [volumeFollower]. */
        private const val VOLUME_FOLLOW_MS = 32L

        /**
         * Sent by the audio service on every volume change, with the stream that moved. The
         * constants are hidden in AudioManager; the broadcast is protected, so only the system
         * can send it.
         */
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"

        /** The stream whose level actually moved. The type above can name one of its aliases. */
        private const val EXTRA_VOLUME_STREAM_TYPE_ALIAS =
            "android.media.EXTRA_VOLUME_STREAM_TYPE_ALIAS"

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

        /** The most of an open panel's height one end sweep may take. See where it is used. */
        private const val PANEL_MAX_FLARE = 0.22f

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

    /** Set while the menu plays its exit. See [hideContextMenu]. */
    private var contextMenuClosing: androidx.compose.runtime.MutableState<Boolean>? = null
    private val removeContextMenuRunnable = Runnable { removeContextMenuNow() }

    /** Set while the Deck plays its exit. See [hideDeck]. */
    private var deckClosing: androidx.compose.runtime.MutableState<Boolean>? = null
    private val removeDeckRunnable = Runnable { removeDeckNow() }
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
        restoreTimer()
    }

    /**
     * Makes the bar answer the hardware volume keys, not just its own gestures.
     *
     * Two sources for one piece of news. The volume indices live in `Settings.System`, and the
     * system writes them there whenever anything moves one, so an observer on that table hears the
     * rocker, the system panel and another app's slider alike, but half a second after the fact.
     * The broadcast in [volumeReceiver] says the same thing and names the stream. Neither is quick
     * enough for a panel that is already open, which reads the level itself: see [volumeFollower].
     *
     * That breadth is also why it is filtered rather than trusted. The table changes for a great
     * many reasons that are not volume, so the handler only speaks up when the stream it would
     * itself be driving has actually landed on a different level.
     */
    private fun registerVolumeWatcher() {
        // The levels as they stand, so the first write to the settings table after start-up,
        // which is almost never about volume, is not taken for a change in it.
        runCatching {
            val media = volume.media()
            volume.percent(media)?.let { lastSeenVolume[media.stream] = it }
            val resolved = volume.resolve(preference.getVolumeStreamMode())
            volume.percent(resolved)?.let { lastSeenVolume[resolved.stream] = it }
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                volumeWatcher,
            )
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                volumeReceiver,
                IntentFilter(VOLUME_CHANGED_ACTION),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }
    }

    private val volumeWatcher = object : android.database.ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) = onVolumeChangedElsewhere()
    }

    /**
     * The same news from a second source: the broadcast the audio service sends on every change,
     * rocker included, naming the stream that moved.
     *
     * It is not faster than the observer. On Android 14 and later the system hands it to an app
     * that is not in the foreground about half a second after the change, the same delay the
     * settings table is written with; measured, delivery is scheduled 500ms after dispatch. It is
     * here because it does not depend on that table being written at all, and because it says
     * which stream moved. The speed a panel that is already open needs comes from
     * [volumeFollower] instead.
     *
     * Registered exported because the broadcast is a protected one: only the system can send it,
     * so exporting costs nothing, and some builds do not deliver it to a receiver that is not.
     * Both sources arriving for one press is harmless, because the second finds nothing it has not
     * already seen.
     */
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != VOLUME_CHANGED_ACTION) return
            // The alias, not the type. A change to one stream is announced once for it and once
            // for every stream that shares its level (on a phone, media shares with the assistant
            // and accessibility streams), all in one delivery group that keeps only the most
            // recent. So what arrives for a press of the rocker is usually an announcement about
            // accessibility volume, and only its alias says it was media that moved. Filtering on
            // the type turned every press away.
            val type = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
            val stream = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE_ALIAS, type)
            onVolumeChangedElsewhere(stream.takeIf { it >= 0 })
        }
    }

    /**
     * The level each stream was last seen at, as 0..100, so news that moved nothing stays quiet.
     *
     * Per stream, because the two sources know different things: the broadcast names the stream
     * that moved, and the observer knows only that something in its table did. One remembered
     * number, compared against whichever stream was asked about last, would mistake a swipe on
     * one stream for news about another.
     */
    private val lastSeenVolume = HashMap<Int, Int>()

    /*
     * Registered here, below the three properties it uses, and not in the init block further up.
     * Kotlin runs property initialisers and init blocks in the order they appear in the file, so
     * from up there the observer, the receiver and the map above were all still null. Registering
     * a null observer throws, the runCatching around it swallowed that, and the volume watcher was
     * never registered at all: the rocker reached neither the bar nor the panel.
     */
    init {
        registerVolumeWatcher()
        OverlayRuntime.activeController = this
    }

    /** True between the first and last step of a swipe that is driving the volume itself. */
    private var adjustingBySwipe = false

    /**
     * Something moved the volume. Follow it on an open panel, or show it on the bar, unless the
     * something was us.
     *
     * Our own writes come back through both sources, so every path here that moves the volume also
     * records where it left the level (see [stepVolume] and [applyQuickSlider]), and a change that
     * lands exactly there is recognised as ours. Without that, the last step of a swipe arriving
     * after the swipe had ended was read as a press of the rocker.
     *
     * @param stream the stream that moved, when the news says. Null from the settings observer,
     *   which only knows that something in its table changed.
     */
    private fun onVolumeChangedElsewhere(stream: Int? = null) {
        if (destroyed) return
        if (adjustingBySwipe) return

        // An open panel follows the level, however it was opened and whatever moved it. This used
        // to return here instead, which is why the rocker never moved a panel that was already up.
        if (sliderView != null) {
            followVolumeOnPanel(stream)
            return
        }
        // The Deck has volume controls of its own, and the bar is out of sight beneath it.
        if (deckHost != null) return

        /*
         * The panel, where the user has asked for it, and the readout on the bar otherwise.
         *
         * Opened on the volume target whatever the panel is configured for: a press of the volume
         * rocker that brought up a brightness slider would be answering a question nobody asked.
         * So media is also the stream listened to when media is what would open.
         */
        val openPanel = preference.slider.getOpenOnVolumeKey()
        val res = if (openPanel) volume.media() else volume.resolve(preference.getVolumeStreamMode())
        if (stream != null && stream != res.stream) return
        val percent = volume.percent(res) ?: return
        val seen = lastSeenVolume[res.stream]
        lastSeenVolume[res.stream] = percent
        if (seen == percent) return
        // The observer hears every write to its table, most of them nothing to do with volume, so
        // from it a stream seen for the first time is a baseline rather than news. The broadcast
        // fires only when a stream has actually moved, so from it that is news.
        if (seen == null && stream == null) return
        if (handlerView == null) return

        if (openPanel) {
            showQuickSliderPanel(QuickSliderStore.TARGET_MEDIA)
            return
        }
        showVolumePercentOnHandler(percent, res)
    }

    /**
     * Moves an open panel to wherever the volume just went, when the panel is showing a volume.
     *
     * One index at a time, because that is what one press of the rocker is, and animated, because
     * there is no finger on the track to explain the movement. Read from the audio service rather
     * than from the news that prompted it, so two presses in quick succession cannot leave the
     * panel on the first one's level.
     *
     * Every press also keeps the panel up. The rocker is how the user is using it just then, and a
     * panel that closed on its timer between the second and third press would close mid-sentence.
     */
    private fun followVolumeOnPanel(stream: Int?) {
        // Not under a finger. What the finger asked for and what the system allowed can differ —
        // the headphone safe-volume limit refuses the top of the range — and following the system
        // mid-drag would pull the fill back under the finger on every frame. It catches up the
        // moment the finger lifts.
        if (adjustingBySwipe || panelTouchActive) return
        // A write of the panel's own still on its way to the audio service: reading the level now
        // would find the old one and animate the fill back to it for a frame or two.
        if (volumeWritesInFlight.get() > 0) return
        val view = sliderView ?: return
        // A brightness panel: the volume moving is not news to it.
        val res = sliderResolution ?: return
        if (stream != null && stream != res.stream) return
        val index = volume.level(res) ?: return
        // From the index already in hand rather than a second call, because this runs every couple
        // of frames while a volume panel is open. The same arithmetic as VolumeController.percent.
        val span = (res.maxIndex - res.minIndex).coerceAtLeast(1)
        lastSeenVolume[res.stream] =
            ((index - res.minIndex) * 100f / span).roundToInt().coerceIn(0, 100)
        val step = (index - res.minIndex).coerceIn(0, sliderSteps)
        // The panel's own write coming back, or nothing that moved this stream.
        if (step == sliderLastStep) return
        sliderLastStep = step
        view.animateValue(step / sliderSteps.toFloat())
        if (sliderCommitted) {
            mainHandler.removeCallbacks(sliderCloseRunnable)
            restartQuickSliderIdleTimeout()
        }
    }

    /**
     * Reads the level every couple of frames while a volume panel is open, so the rocker moves it
     * at once.
     *
     * Both sources of news above arrive about half a second after the level moves. That is fine
     * for opening the panel, which the system's own slider has already beaten to the screen. It is
     * not fine for a panel that is already up: the user presses, sees the system's slider move,
     * and watches ours catch up half a second later, a lag on every press. So while one is open
     * the level is simply read. One call that returns an int, for the few seconds the panel is on
     * screen, and nothing at all otherwise; it stops itself when the panel goes.
     */
    /** Whether a finger is on the open panel right now. See [followVolumeOnPanel]. */
    private var panelTouchActive = false

    /*
     * Where the panel's volume writes go: off the main thread, latest only.
     *
     * A write is a synchronous call into the audio service, and on a phone it is slow enough that a
     * swipe driving the panel, which writes on nearly every frame, ran at 21ms a frame with the
     * fill waiting on the audio. The fill now moves on the frame the finger does and the level
     * follows on this thread. When the finger outruns it the writes in between are skipped, since
     * only where the level ends up matters.
     */
    private var audioThread: android.os.HandlerThread? = null
    private val pendingVolumeWrite =
        java.util.concurrent.atomic.AtomicReference<Pair<VolumeController.Resolution, Int>?>(null)

    /** Writes posted and not yet finished. The follower keeps its hands off until they have. */
    private val volumeWritesInFlight = java.util.concurrent.atomic.AtomicInteger(0)

    private val volumeWriter = Runnable {
        try {
            val job = pendingVolumeWrite.getAndSet(null)
            if (job != null) volume.setIndex(job.first, job.second, showUi = false)
        } finally {
            volumeWritesInFlight.decrementAndGet()
        }
    }

    private fun writeVolumeIndex(res: VolumeController.Resolution, index: Int) {
        val thread = audioThread ?: android.os.HandlerThread("GestureVolume-audio").also {
            it.start()
            audioThread = it
        }
        pendingVolumeWrite.set(res to index)
        volumeWritesInFlight.incrementAndGet()
        // One post per write, each finishing one count; any that find the job already taken by an
        // earlier one simply finish. That is the coalescing.
        Handler(thread.looper).post(volumeWriter)
    }

    private val volumeFollower = object : Runnable {
        override fun run() {
            if (destroyed || sliderView == null || sliderResolution == null) return
            followVolumeOnPanel(null)
            mainHandler.postDelayed(this, VOLUME_FOLLOW_MS)
        }
    }

    // ---- the volume keys, caught ---------------------------------------------------------------

    /** Volume keys whose press was taken, so their release is taken too, and nothing else's. */
    private val volumeKeysTaken = HashSet<Int>()

    /**
     * A volume key from the accessibility service's key filter, before the system has acted on it.
     *
     * This is what Instant means. Through [volumeReceiver] and the settings observer the panel
     * hears about a press half a second after the fact, which is as soon as the platform tells an
     * app that is not in the foreground anything about volume. The only way to be there at the
     * moment of the press is to be handed the key itself, and only an accessibility service can
     * ask for that. So the service asks while the setting says Instant, and passes the two volume
     * keys here.
     *
     * @return true when the panel took the key: the level has moved one index, the panel is up
     *   and showing it, and the system's own slider stays away. False hands it back to the system
     *   exactly as if nothing had looked.
     */
    fun onVolumeKey(event: KeyEvent): Boolean {
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> 1
            KeyEvent.KEYCODE_VOLUME_DOWN -> -1
            else -> return false
        }
        // A release belongs to whoever took its press. Taking one whose press went to the system
        // would leave the system holding a key that never comes up.
        if (event.action == KeyEvent.ACTION_UP) return volumeKeysTaken.remove(event.keyCode)
        if (event.action != KeyEvent.ACTION_DOWN) return event.keyCode in volumeKeysTaken
        // Held down, the key repeats as further presses, and each is a step, as it is for the
        // system's own slider.
        val taken = takesVolumeKeys() && stepVolumeFromKey(direction)
        if (taken) volumeKeysTaken += event.keyCode else volumeKeysTaken -= event.keyCode
        return taken
    }

    /**
     * Whether a volume key is the panel's to take right now.
     *
     * Only when the setting says so, and only where the keys mean what the panel shows. In a call,
     * or while the phone rings, they mean the call's own volume or silencing the ringer, and only
     * the system knows which. With the screen off they have always adjusted music in a pocket. On
     * the lock screen the panel may not even be drawn: the foreground-service host's windows sit
     * beneath it. And with the bar hidden or the Deck up there is nothing to open the panel from.
     */
    private fun takesVolumeKeys(): Boolean {
        if (destroyed) return false
        if (preference.slider.getVolumeKeyMode() != QuickSliderStore.VOLUME_KEYS_INSTANT) return false
        if (handlerView == null || deckHost != null) return false
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        if (audio.mode != AudioManager.MODE_NORMAL) return false
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        if (!power.isInteractive) return false
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isKeyguardLocked == true) return false
        return quickSliderIsWritable(QuickSliderStore.TARGET_MEDIA)
    }

    /**
     * Moves media one index for a key press and shows it on the panel.
     *
     * @return false when the system refused the step, so the key can go to the system instead.
     *   The usual refusal is the headphone safe-volume limit, and the warning the user has to
     *   accept before going louder is the system's to show, not the panel's to hide.
     */
    private fun stepVolumeFromKey(direction: Int): Boolean {
        val res = volume.media()
        val current = volume.level(res) ?: return false
        val target = (current + direction).coerceIn(res.minIndex, res.maxIndex)
        if (target != current) {
            volume.setIndex(res, target, showUi = false)
            if (volume.level(res) == current) return false
        }
        // Seen, so the same change arriving later through the watchers is known for this one.
        val span = (res.maxIndex - res.minIndex).coerceAtLeast(1)
        lastSeenVolume[res.stream] =
            ((target - res.minIndex) * 100f / span).roundToInt().coerceIn(0, 100)
        showPanelForVolumeKey(res)
        return true
    }

    /** Puts the panel up on [res] for a key press, or moves it there if it is already up. */
    private fun showPanelForVolumeKey(res: VolumeController.Resolution) {
        if (sliderView != null && sliderResolution?.stream == res.stream) {
            // Moved now rather than on the follower's next beat, and kept up for as long as the
            // keys are being pressed, including at the ends, where the level no longer moves.
            followVolumeOnPanel(null)
            if (sliderCommitted) {
                mainHandler.removeCallbacks(sliderCloseRunnable)
                restartQuickSliderIdleTimeout()
            }
            return
        }
        // A panel showing something else, brightness, makes way for the one the key is about.
        if (sliderView != null) hideQuickSlider()
        if (openQuickSliderWindow(QuickSliderStore.TARGET_MEDIA)) animateQuickSliderOpen()
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
        if (OverlayRuntime.activeController === this) OverlayRuntime.activeController = null
        hideOverlayView()
        hideHandlerView()
        hideIndicator()
        removeContextMenuNow()
        removeDeckNow()
        // After hideHandlerView, whose gestureDetector.cancel() is what asks a slider still under
        // the finger to collapse. This turns that collapse into an immediate removal.
        dismissQuickSliderNow()

        (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.unregisterDisplayListener(displayListener)
        runCatching { context.contentResolver.unregisterContentObserver(volumeWatcher) }
        runCatching { context.unregisterReceiver(volumeReceiver) }
        // Lets a write already queued land, then ends the thread.
        audioThread?.quitSafely()
        audioThread = null

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
        // Rebuilt while the Deck is up (a settings save lands here): out of sight, like the one
        // it replaces, until the Deck closes.
        if (deckHost != null) view.alpha = 0f

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
        strength: Float = 1f,
    ) {
        val f = frame
        setBounds(
            left + (f?.insetLeft ?: 0),
            top + (f?.insetTop ?: 0),
            width,
            height,
            cornerRadiusPx,
            strength,
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
        removeContextMenuNow()
        removeDeckNow()
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
        // Seen, so this step coming back through the watchers after the swipe has ended is known
        // for the swipe's and not taken for a press of the rocker.
        if (percent >= 0) lastSeenVolume[resolution.stream] = percent

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
    private fun showQuickSliderPanel(targetOverride: String? = null) {
        // Already up: treat a second trigger as "put it away", so whatever gesture opens the panel
        // also closes it and the user is never left hunting for a way out.
        if (sliderView != null) {
            hideQuickSlider()
            return
        }
        if (!openQuickSliderWindow(targetOverride)) return
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
    private fun openQuickSliderWindow(targetOverride: String? = null): Boolean {
        if (destroyed) return false
        val wm = windowManager ?: return false
        val barParams = handlerParams ?: return false
        val currentFrame = frame ?: return false
        // A retraction still running is left alone rather than cancelled: cancelling it would
        // freeze a view part-way out with nothing left to finish or remove it. It owns its own
        // teardown, and it checks whether this new window has taken its place before restoring
        // the bar — see the listener in hideQuickSlider.

        val settings = preference.slider

        // The override exists for the hardware volume keys. A panel configured for brightness
        // that opened on a volume press would be showing one thing while the user changed another.
        sliderTarget = targetOverride ?: settings.getTarget()
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
        /*
         * Two widths, and they are no longer the same number.
         *
         * `panelThicknessPx` is what gets painted, and it is whatever the user asked for, down to
         * ten. `windowThicknessPx` is the window it is painted in, and that keeps the old floor:
         * at least a thumb's worth, and never narrower than the bar — because the bar is the
         * collapsed end of the morph, and a first frame wider than its own window is a first frame
         * with its edge sliced off.
         *
         * The panel is drawn against the screen edge inside that window, exactly the way the bar
         * is drawn against the edge inside its own wider one.
         */
        val panelThicknessPx = dpToPx(settings.getThicknessDp())
        val thicknessPx = maxOf(
            panelThicknessPx,
            dpToPx(PANEL_MIN_THICKNESS_DP),
            drawnWidthPx,
        )
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
        val barShape = preference.getHandlerShape()
        val barFlare = preference.getHandlerShapeFlare()

        /*
         * Where it arrives.
         *
         * Following the bar by default, and "following" means *scaled*, not copied: the panel is
         * several times the bar's width, and a 10dp radius that reads as a soft corner on a 12dp
         * bar reads as a nearly square one on a 52dp panel. The radii are multiplied by the same
         * ratio the width grew by, so the shape is the bar's at the panel's size — which is what
         * "the handler, larger" actually means.
         */
        val followBar = settings.getFollowHandlerShape()
        val density = context.resources.displayMetrics.density
        val barWidthForShape = preference.getHandlerWidthDp().coerceAtLeast(1f)
        val widthRatio = if (drawnWidthPx > 0) {
            (panelThicknessPx.toFloat() / drawnWidthPx).coerceIn(0.25f, 4f)
        } else {
            1f
        }
        val panelCornerTL: Float
        val panelCornerTR: Float
        val panelCornerBL: Float
        val panelCornerBR: Float
        val panelShape: String
        val panelFlare: Float
        if (followBar) {
            panelCornerTL = barCornerTL * widthRatio
            panelCornerTR = barCornerTR * widthRatio
            panelCornerBL = barCornerBL * widthRatio
            panelCornerBR = barCornerBR * widthRatio
            panelShape = barShape
            /*
             * The sweep keeps the bar's *character*, not its fraction.
             *
             * A flare is a share of the height, and the panel is both taller and much wider than
             * the bar. Carried across unchanged it produces a sweep that is proportionally right
             * and visually wrong: on a bar 14dp wide a third of the height reads as a tapered tab,
             * and on a panel 52dp wide the same third reads as a leaf. What the eye is actually
             * reading is the sweep against the width, so that ratio is what is carried over — and
             * capped, because past a half the two sweeps meet and there is no straight section
             * left to be a panel.
             */
            val barSweepDp = barFlare * preference.getHandlerHeightDp()
            val wanted = (barSweepDp / barWidthForShape) * (panelThicknessPx / density)
            val character = wanted / (lengthPx / density).coerceAtLeast(1f)
            // The smaller of the three, and the ceiling is the load-bearing one. A bar four times
            // narrower than the panel wants a sweep longer than the panel is tall, which clamps to
            // a half — and at a half the two sweeps meet and the panel is a leaf with no straight
            // section at all. A tab has to have a middle; this is where that is guaranteed.
            //
            // Unless the user has said otherwise. Matching the bar's shape settles *which* shape
            // and where its corners are; how deep the sweep goes is still theirs to set, and the
            // figure above is only the starting point they are given.
            panelFlare = if (settings.hasShapeFlare()) {
                settings.getShapeFlare()
            } else {
                minOf(barFlare, character, PANEL_MAX_FLARE).coerceAtLeast(HandlerShape.MIN_FLARE)
            }
        } else {
            panelCornerTL = settings.getCornerTL()
            panelCornerTR = settings.getCornerTR()
            panelCornerBL = settings.getCornerBL()
            panelCornerBR = settings.getCornerBR()
            panelShape = settings.getShape()
            panelFlare = settings.getShapeFlare()
        }

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
            setExpandedCorners(panelCornerTL, panelCornerTR, panelCornerBL, panelCornerBR)
            setCollapsedAppearance(handlerColor, barCornerTL, barCornerTR, barCornerBL, barCornerBR)
            setShapes(panelShape, panelFlare, barShape, barFlare, isLeft)
            setDrawnThickness(panelThicknessPx.toFloat(), isLeft)
            setContentMargins(settings.getValueMarginDp(), settings.getIconMarginDp())
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

        // Before the panel's own window, so it stacks underneath: windows of a type stack in the
        // order they arrive.
        val backdrop = newPanelBackdrop()
        // Kept under the panel from here on by the panel itself, frame by frame: its shape through
        // the morph, its layer through the entrance, its strength with both. It used to be placed
        // once, at the size of the window, when the panel armed. So a panel retracting into the bar
        // left the blur standing at full size until the retraction ended, and a panel drawn
        // narrower than its window blurred the dead space beside it. See
        // QuickSliderView.GlassListener.
        //
        // This panel's own glass and window, captured here rather than read from the fields later,
        // because a second panel can open while this one is still retracting and the fields are
        // that panel's by then.
        view.glassListener = QuickSliderView.GlassListener { l, t, r, b, corner, strength ->
            backdrop?.setFrameBounds(
                params.x + l.roundToInt(),
                params.y + t.roundToInt(),
                (r - l).roundToInt(),
                (b - t).roundToInt(),
                corner,
                strength,
            )
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "quick panel addView failed", e)
            view.glassListener = null
            backdrop?.dismiss()
            return false
        }
        sliderView = view
        sliderBackdrop = backdrop
        // A volume panel follows the rocker from the moment it exists. A brightness one has
        // nothing to follow: its resolution is null, and the follower stops on its first run.
        mainHandler.removeCallbacks(volumeFollower)
        mainHandler.postDelayed(volumeFollower, VOLUME_FOLLOW_MS)
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
        // replaces the bar is a rectangle of the same size, colour and corner in the same place.
        // Only the icon differs, and the view fades that in.
        //
        // But not on this frame. The panel's window was only just added, and its first frame
        // reaches the screen whenever the compositor gets to it, which nothing orders against the
        // bar's next frame. Hidden here, the bar could go a frame or two before the panel arrived:
        // a blink at the edge at the start of every open. So it goes two frames after the panel
        // first draws, or after a timeout if the panel never does. Overlapping meanwhile shows
        // nothing, because frame zero of the panel is the bar.
        handlerView?.animate()?.cancel()
        view.onFirstFrame = {
            view.postOnAnimation { view.postOnAnimation { hideBarUnder(view) } }
        }
        mainHandler.postDelayed({ hideBarUnder(view) }, BAR_HANDOFF_TIMEOUT_MS)

        return true
    }

    /** Takes the bar out of sight under [view], if [view] is still the panel standing in for it. */
    private fun hideBarUnder(view: QuickSliderView) {
        if (sliderView !== view) return
        handlerView?.let { bar ->
            bar.animate().cancel()
            bar.alpha = 0f
        }
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
        view.playEntrance(
            preference.getPanelAnimation(),
            handlerIsLeft(),
            preference.getPanelAnimationSpeed(),
        )
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

        restartQuickSliderIdleTimeout()
    }

    /**
     * The panel's own touches, routed back into the same quantise-and-write path the swipe uses.
     */
    private val quickSliderListener = object : QuickSliderView.Listener {
        override fun onValuePicked(fraction: Float) {
            panelTouchActive = true
            mainHandler.removeCallbacks(sliderCloseRunnable)
            restartQuickSliderIdleTimeout()
            applyQuickSlider(fraction)
        }

        override fun onAdjustFinished() {
            panelTouchActive = false
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
            if (res != null) {
                writeVolumeIndex(res, res.minIndex + step)
                // Seen, so the change coming back through the watchers is known for this panel's
                // own rather than taken for a press of the rocker and animated back over the
                // finger. From the step itself: the write has not landed yet.
                val span = (res.maxIndex - res.minIndex).coerceAtLeast(1)
                lastSeenVolume[res.stream] = (step * 100f / span).roundToInt().coerceIn(0, 100)
            }
            res != null
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
        panelTouchActive = false
        // This panel's glass goes with it. Taken now rather than when the retraction ends,
        // because a second panel can open inside the retraction, and the field is that one's.
        val backdrop = sliderBackdrop
        sliderBackdrop = null
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
        // Out of the input stack too, not merely deaf. This window still covers the bar, so a
        // second swipe landing inside the retraction was swallowed by a panel that had stopped
        // listening, instead of reaching the bar and opening a new one.
        (view.layoutParams as? WindowManager.LayoutParams)?.let { lp ->
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { windowManager?.updateViewLayout(view, lp) }
        }
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
                    // And not while the Deck is up: it keeps the bar out of sight until it closes.
                    val bar = handlerView
                    if (sliderView == null && deckHost == null && bar != null) {
                        bar.animate().cancel()
                        bar.alpha = 1f
                        // Left over the bar a moment rather than taken down on this same frame.
                        retireQuickSliderView(view, backdrop)
                    } else {
                        removeQuickSliderView(view, backdrop)
                    }
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
        // Including any that finished retracting and are waiting out their handover.
        flushRetiringSliders()

        val view = sliderView ?: return
        panelTouchActive = false
        val backdrop = sliderBackdrop
        sliderBackdrop = null
        sliderView = null
        sliderResolution = null
        sliderLastStep = -1
        sliderCommitted = false
        sliderAutoBrightnessHandled = false
        view.listener = null
        view.setOnTouchOutside(null)
        view.setInteractive(false)
        if (deckHost == null) handlerView?.alpha = 1f
        removeQuickSliderView(view, backdrop)
    }

    private fun removeQuickSliderView(view: QuickSliderView, backdrop: PanelBackdrop?) {
        // Deaf first, so nothing the view does on its way out can move glass that is going.
        view.glassListener = null
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
            // Already gone — the service was torn down while the collapse was still running.
        }
        // With the panel, never after it. By now the retraction has faded it to nothing anyway, so
        // whichever of the two windows the compositor drops first, no frame has glass and no panel.
        backdrop?.dismiss()
    }

    /**
     * Retracted panels waiting to be taken down, each with its glass.
     *
     * A panel that has retracted stays on screen a moment after the bar has been told to come
     * back, rather than being taken down on the same frame. The two are separate windows and
     * nothing orders their frames, so taken down together the panel could leave before the bar
     * arrived, and the bar blinked out at the end of every close. Collapsed, the panel is the bar
     * pixel for pixel, so overlapping it for a few frames shows nothing.
     */
    private val retiringSliders = ArrayList<Pair<QuickSliderView, PanelBackdrop?>>()

    private fun retireQuickSliderView(view: QuickSliderView, backdrop: PanelBackdrop?) {
        val entry = view to backdrop
        retiringSliders += entry
        mainHandler.postDelayed({
            if (retiringSliders.remove(entry)) removeQuickSliderView(view, backdrop)
        }, PANEL_HANDOFF_MS)
    }

    /** Takes every retracted panel down now, for teardown, where there is no bar to hand over to. */
    private fun flushRetiringSliders() {
        if (retiringSliders.isEmpty()) return
        val all = ArrayList(retiringSliders)
        retiringSliders.clear()
        all.forEach { (view, backdrop) -> removeQuickSliderView(view, backdrop) }
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
        if (contextMenuHost != null) {
            // Already open, or still playing its exit; either way start again from scratch.
            if (contextMenuClosing?.value != true) return
            removeContextMenuNow()
        }
        removeDeckNow()

        val entries = HandlerActionCatalog.contextMenuEntries(preference.getContextMenuOrder())
        val grid = preference.getContextMenuLayout() == ContextMenuLayout.GRID
        if (entries.isEmpty()) return

        val anchor = IntRect(params.x, params.y, params.x + params.width, params.y + params.height)
        val frameSize = IntSize(currentFrame.usableWidth, currentFrame.usableHeight)

        val menuHost = OverlayComposeHost(context)
        val panelTheme = preference.getPanelTheme()
        val panelAnimation = preference.getPanelAnimation()
        val animationSpeed = preference.getPanelAnimationSpeed()
        val menuSurface = preference.getMenuSurface()
        // Flipped by hideContextMenu, read by the composition: the menu plays its entrance
        // backwards and the window is taken away when it has finished, rather than the window
        // vanishing out from under a panel that was still on screen.
        val closing = androidx.compose.runtime.mutableStateOf(false)
        contextMenuClosing = closing
        menuHost.setContent {
            // The pale materials carry dark ink, so the scheme underneath everything the panel
            // does not colour by hand has to flip with them. See OverlayTheme.
            OverlayTheme(
                light = menuSurface?.let { PanelTheme.luminanceOf(it) > 0.5 }
                    ?: PanelTheme.isLight(panelTheme)
            ) {
                ContextMenuOverlay(
                    entries = entries,
                    anchor = anchor,
                    frame = frameSize,
                    grid = grid,
                    theme = panelTheme,
                    animation = panelAnimation,
                    animationSpeed = animationSpeed,
                    closing = closing.value,
                    surfaceOverride = menuSurface,
                    onSelect = { entry ->
                        hideContextMenu()
                        // Posted for the same reason the tap actions are: "Hide Handler" and
                        // "Open App" destroy windows, and doing that from inside a click dispatch
                        // tears down a view hierarchy that is still being walked.
                        mainHandler.post { runAction(entry.action) }
                    },
                    onDismiss = { hideContextMenu() },
                    onCardBounds = { rect, cornerPx, strength ->
                        menuBackdrop?.setFrameBounds(
                            rect.left, rect.top, rect.width, rect.height, cornerPx, strength
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

    /**
     * Starts the menu's exit, and takes the window away when it has played.
     *
     * Not an immediate removal any more. The menu arrives with an animation the user chose, and a
     * panel that eases in and then blinks out is worse than one that does neither — the blink is
     * what the eye notices. Reversing the entrance is the whole exit: see the note in
     * `rememberPanelEntrance` on why there is no separate catalogue of them.
     */
    private fun hideContextMenu() {
        val closing = contextMenuClosing
        if (contextMenuHost == null) {
            removeContextMenuNow()
            return
        }
        if (closing == null || destroyed) {
            removeContextMenuNow()
            return
        }
        if (closing.value) return
        closing.value = true
        mainHandler.removeCallbacks(removeContextMenuRunnable)
        mainHandler.postDelayed(
            removeContextMenuRunnable,
            PanelAnimation.scaledDurationMs(
                preference.getPanelAnimation(),
                preference.getPanelAnimationSpeed(),
            ).toLong(),
        )
    }

    private fun removeContextMenuNow() {
        mainHandler.removeCallbacks(removeContextMenuRunnable)
        contextMenuClosing = null
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
        val deckClosing = androidx.compose.runtime.mutableStateOf(false)
        this.deckClosing = deckClosing
        val deckSpeed = preference.getPanelAnimationSpeed()
        host.setContent {
            OverlayTheme(light = PanelTheme.isLight(model.panelTheme)) {
                DeckOverlay(
                    closing = deckClosing.value,
                    animationSpeed = deckSpeed,
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
        // The bar steps out of the Deck's way while it is up and comes back once it has gone (see
        // removeDeckNow). Faded, not removed or made INVISIBLE, for the reason
        // openQuickSliderWindow gives: its window has to stay in the input stack, and the gesture
        // that opened the Deck can still be under the finger on it.
        fadeHandler(visible = false)
        restartDeckAutoClose()
    }

    /**
     * Starts the Deck's exit, and takes the window away when it has played.
     *
     * The same reasoning as the menu's: a panel that eases in and then blinks out is worse than
     * one that does neither, because the blink is the part the eye catches.
     */
    fun hideDeck() {
        mainHandler.removeCallbacks(deckAutoCloseRunnable)
        val closing = deckClosing
        if (deckRoot == null || closing == null || destroyed) {
            removeDeckNow()
            return
        }
        if (closing.value) return
        closing.value = true
        mainHandler.removeCallbacks(removeDeckRunnable)
        mainHandler.postDelayed(
            removeDeckRunnable,
            PanelAnimation.scaledDurationMs(
                preference.getPanelAnimation(),
                preference.getPanelAnimationSpeed(),
            ).toLong(),
        )
    }

    private fun removeDeckNow() {
        mainHandler.removeCallbacks(removeDeckRunnable)
        mainHandler.removeCallbacks(deckAutoCloseRunnable)
        deckClosing = null
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
        // The bar back, now there is nothing in front of it. Not while a Quick panel is standing
        // in for it: that puts the bar back itself when it retracts.
        if (sliderView == null && sliderCollapse == null) fadeHandler(visible = true)
    }

    /**
     * Fades the bar in or out of sight without touching its window.
     *
     * Cancelling whatever fade was running first, so a Deck closed while it is still opening does
     * not leave two animations fighting over the same alpha.
     */
    private fun fadeHandler(visible: Boolean) {
        val bar = handlerView ?: return
        bar.animate().cancel()
        bar.animate().alpha(if (visible) 1f else 0f).setDuration(HANDLER_FADE_MS).start()
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
            surfaces.strength,
        )
        val card = surfaces.card
        deckCardBackdrop?.setFrameBounds(
            card?.left ?: 0, card?.top ?: 0,
            card?.width ?: 0, card?.height ?: 0,
            surfaces.cardCornerPx,
            surfaces.strength,
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
        removeContextMenuNow()
        removeDeckNow()
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
