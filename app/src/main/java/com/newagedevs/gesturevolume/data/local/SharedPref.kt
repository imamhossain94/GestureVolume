package com.newagedevs.gesturevolume.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.newagedevs.gesturevolume.R
import android.view.Gravity
import androidx.compose.ui.graphics.toArgb
import com.newagedevs.gesturevolume.utils.ContextMenuLayout
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog
import com.newagedevs.gesturevolume.utils.PanelTheme
import com.newagedevs.gesturevolume.utils.HandlerActions
import com.newagedevs.gesturevolume.utils.ClipboardEntry
import com.newagedevs.gesturevolume.utils.HandlerPresets
import com.newagedevs.gesturevolume.utils.HandlerShape
import com.newagedevs.gesturevolume.utils.OverlayHostMode
import org.json.JSONArray
import org.json.JSONObject
import com.newagedevs.gesturevolume.utils.VolumeStreamMode
import com.newagedevs.gesturevolume.utils.safeDrawableIdOrDefault
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPref @Inject constructor(
    context: Context
) {
    private val appContext: Context = context.applicationContext
    val sharedPreferences: SharedPreferences =
        appContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

    /** The Deck's own settings and contents. Same file, its own object. */
    val deck: DeckStore by lazy { DeckStore(sharedPreferences) }

    /** The universal search's settings. Same file, its own object. */
    val search: SearchStore by lazy { SearchStore(sharedPreferences) }

    /** The expanding quick slider's settings. Same file, its own object. */
    val slider: QuickSliderStore by lazy { QuickSliderStore(sharedPreferences) }

    init {
        pinLegacyAppearanceDefaults()
    }

    /**
     * Freezes the pre-1.4.0 appearance defaults onto an install that already existed, once.
     *
     * The bar's appearance preferences are all fallback-to-the-Default-preset: an install that
     * never opened the appearance screen has no colour, width or corner radius stored at all, and
     * reads [HandlerPresets.DEFAULT] every time. That is the right design until the Default preset
     * itself changes, at which point every one of those users would wake up to a different bar —
     * a slim black pill in place of the wide indigo one they have been tapping for months. Nobody
     * asked for that, and a handler that changed shape overnight reads as a bug.
     *
     * So: on the first run after the update, if this prefs file has anything in it at all, the old
     * values are written out explicitly for the keys that moved. A genuinely fresh install has an
     * empty file, gets nothing written, and falls through to the new Default. Either way the flag
     * is set so this never runs twice.
     *
     * Only unset keys are touched. A user who did open the appearance screen already has their own
     * values stored and none of this applies to them.
     *
     * Runs in `init` rather than from an activity because the overlay service can be the first
     * thing to read a preference after a reboot, and it must see the same bar the user last saw.
     */
    private fun pinLegacyAppearanceDefaults() {
        if (sharedPreferences.getBoolean(APPEARANCE_DEFAULTS_PINNED, false)) return

        val isExistingInstall = sharedPreferences.all.isNotEmpty()
        sharedPreferences.edit {
            putBoolean(APPEARANCE_DEFAULTS_PINNED, true)
            if (!isExistingInstall) return@edit

            fun pinInt(key: String, value: Int) {
                if (!sharedPreferences.contains(key)) putInt(key, value)
            }

            fun pinFloat(key: String, value: Float) {
                if (!sharedPreferences.contains(key)) putFloat(key, value)
            }

            pinInt(HANDLER_COLOR, LEGACY_BG_COLOR)
            pinInt(HANDLER_BACKGROUND_ALPHA, LEGACY_BG_ALPHA)
            pinFloat(HANDLER_STROKE_WIDTH, LEGACY_STROKE_WIDTH)
            pinFloat(HANDLER_CORNER_RADIUS_TL, LEGACY_CORNER_RADIUS)
            pinFloat(HANDLER_CORNER_RADIUS_TR, LEGACY_CORNER_RADIUS)
            pinFloat(HANDLER_CORNER_RADIUS_BL, LEGACY_CORNER_RADIUS)
            pinFloat(HANDLER_CORNER_RADIUS_BR, LEGACY_CORNER_RADIUS)
            pinFloat(HANDLER_WIDTH + "_dp", LEGACY_WIDTH_DP)
            pinFloat(HANDLER_HEIGHT, LEGACY_HEIGHT_DP)
            pinFloat(HANDLER_POSITION_FRACTION, LEGACY_POSITION_FRACTION)
            pinFloat(HANDLER_POS_Y_PORTRAIT, LEGACY_POSITION_FRACTION)
            pinFloat(HANDLER_POS_Y_LANDSCAPE, LEGACY_POSITION_FRACTION)
        }
    }

    private companion object {
        const val PRO_FEATURE_ACTIVATION = "proFeatureActivation"
        const val IS_RUNNING = "isRunning"
        const val HANDLER_POSITION = "handlerPosition"
        const val HANDLER_COLOR = "handlerColor"
        const val HANDLER_WIDTH = "handlerWidth"
        const val HANDLER_HEIGHT = "handlerHeight"
        const val HANDLER_TRANSLATION_Y = "handlerTranslationY"
        const val HANDLER_SINGLE_TAP = "handlerSingleTap"
        const val HANDLER_DOUBLE_TAP = "handlerDoubleTap"
        const val HANDLER_LONG_TAP = "handlerLongTap"
        const val HANDLER_SWIPE_UP = "handlerSwipeUp"
        const val HANDLER_SWIPE_DOWN = "handlerSwipeDown"
        const val FIRST_LAUNCH = "firstLaunch"

        // Ad timing keys
        const val FIRST_INSTALL_TIME = "firstInstallTime"
        const val LAST_APP_OPEN_AD_TIME = "lastAppOpenAdTime"
        const val LAST_INTERSTITIAL_AD_TIME = "lastInterstitialAdTime"
        const val LAST_ANY_AD_TIME = "lastAnyAdTime"

        // Cooldown periods in milliseconds
        // App-open dropped 60m → 25m: app-open earns ~$0.42 eCPM (≈40× banner), so more
        // qualified resumes is real lift. Interstitial 90s → 3m to stay retention-safe now
        // that interstitials also fire at screen transitions (it's the top earner, $3.10 eCPM).
        const val APP_OPEN_AD_COOLDOWN = 25 * 60 * 1000L // 25 minutes
        const val INTERSTITIAL_AD_COOLDOWN = 3 * 60 * 1000L // 3 minutes
        const val MIN_TIME_BETWEEN_ANY_ADS = 90 * 1000L // 90 seconds between any ad types

        // Post-install grace period. Setup is the one stretch where a full-screen ad is most
        // likely to cost us the user outright: they are bouncing in and out of system permission
        // screens, placing the handler, and trying gestures, and every one of those returns is an
        // ON_START that would otherwise qualify for an app-open ad. Two hours covers a setup
        // session plus the "come back and finish it later" pass without meaningfully denting
        // lifetime impressions.
        const val AD_GRACE_PERIOD = 2 * 60 * 60 * 1000L // 2 hours from first launch

        // Cap interstitials per app session (in-memory; resets on process restart) so the
        // added screen-transition triggers can't stack up within one sitting.
        const val MAX_INTERSTITIALS_PER_SESSION = 5

        // Appearance settings keys
        const val HANDLER_BACKGROUND_ALPHA = "handlerBackgroundAlpha"
        const val HANDLER_STROKE_COLOR = "handlerStrokeColor"
        const val HANDLER_STROKE_WIDTH = "handlerStrokeWidth"
        const val HANDLER_STROKE_ALPHA = "handlerStrokeAlpha"
        const val HANDLER_CORNER_RADIUS_TL = "handlerCornerRadiusTL"
        const val HANDLER_CORNER_RADIUS_TR = "handlerCornerRadiusTR"
        const val HANDLER_CORNER_RADIUS_BL = "handlerCornerRadiusBL"
        const val HANDLER_CORNER_RADIUS_BR = "handlerCornerRadiusBR"
        const val HANDLER_SHAPE = "handlerShape"
        const val HANDLER_SHAPE_FLARE = "handlerShapeFlare"
        const val HANDLER_ICON_RES = "handlerIconRes"
        const val HANDLER_ICON_NAME = "handlerIconName"
        const val HANDLER_ICON_SIZE = "handlerIconSize"
        const val HANDLER_ICON_COLOR = "handlerIconColor"
        const val HANDLER_SHOW_ICON = "handlerShowIcon"
        const val HANDLER_VIBRATE_ON_CLICK = "handlerVibrateOnClick"
        const val HANDLER_POSITION_FRACTION = "handlerPositionFraction"
        const val HANDLER_EDGE_MARGIN = "handlerEdgeMarginDp"
        const val BRIGHTNESS_AUTO_WAS_ON = "brightnessAutoWasOn"
        const val LEGACY_TRANSLATION_Y_DEFAULT = 260f
        /**
         * Where a fresh install puts the bar vertically, read from the Default preset.
         *
         * Paired with a horizontal default of 1f — flush right — the preset's 0.5 puts the bar
         * halfway down the right edge, which is where the thumb rests when the phone is held
         * normally and where every edge launcher in the category puts its handle. It used to sit
         * an eighth of the way down to stay clear of a video player's scrubber; being reachable
         * without a stretch turned out to matter more, and the bar can be dragged anywhere.
         *
         * An install that predates the change keeps whatever it had: see
         * [pinLegacyAppearanceDefaults].
         */
        val DEFAULT_POSITION_FRACTION = HandlerPresets.DEFAULT.positionFraction

        /**
         * The appearance defaults as they stood through 1.3.x, before the Default preset became
         * the slim black pill. Written out verbatim by [pinLegacyAppearanceDefaults].
         *
         * Frozen literals on purpose. They must not follow [HandlerPresets.DEFAULT] — the whole
         * point is to record what an existing install was already showing at the moment the
         * default moved out from under it.
         */
        const val LEGACY_BG_COLOR = 0xFF4F46E5.toInt()
        const val LEGACY_BG_ALPHA = 128
        const val LEGACY_STROKE_WIDTH = 1f
        const val LEGACY_CORNER_RADIUS = 15f
        const val LEGACY_WIDTH_DP = 30f
        const val LEGACY_HEIGHT_DP = 100f
        const val LEGACY_POSITION_FRACTION = 0.12f
        const val APPEARANCE_DEFAULTS_PINNED = "appearanceDefaultsPinned"

        /** The Default preset's side, as the string this preference stores. */
        val DEFAULT_SIDE: String =
            if (HandlerPresets.DEFAULT.gravity == Gravity.START) "Left" else "Right"
        const val APP_LAUNCH_COUNT = "appLaunchCount"
        const val HAS_SHOWN_REVIEW = "hasShownReview"
        const val REVIEW_ASK_COUNT = "reviewAskCount"
        const val REVIEW_LAST_ASK_TIME = "reviewLastAskTime"
        const val REVIEW_LAST_ASK_VERSION = "reviewLastAskVersion"
        const val APP_OPEN_AD_PAUSED = "appOpenAdPaused"

        // Free positioning. Stored per orientation: a single pair cannot serve both, because
        // the usable frame swaps its axes on rotation and a bar parked bottom-left in portrait has
        // no meaningful counterpart in landscape. Each orientation remembers where the user last
        // put the bar *in that orientation*.
        const val HANDLER_POS_X_PORTRAIT = "handlerPosXFractionPortrait"
        const val HANDLER_POS_Y_PORTRAIT = "handlerPosYFractionPortrait"
        const val HANDLER_POS_X_LANDSCAPE = "handlerPosXFractionLandscape"
        const val HANDLER_POS_Y_LANDSCAPE = "handlerPosYFractionLandscape"

        const val HANDLER_HIDDEN = "handlerHidden"
        const val APP_IN_FOREGROUND = "appInForeground"
        const val SHOW_VOLUME_PERCENT = "handlerShowVolumePercent"
        const val VOLUME_STREAM_MODE = "handlerVolumeStreamMode"
        const val HANDLER_EDGE_SWIPE_MENU = "handlerEdgeSwipeMenu"

        // 1.4.0: the two extra tap and swipe slots, and who draws the bar.
        const val HANDLER_TRIPLE_TAP = "handlerTripleTap"
        const val HANDLER_SWIPE_IN = "handlerSwipeIn"
        const val HANDLER_SWIPE_OUT = "handlerSwipeOut"
        const val OVERLAY_HOST_MODE = "overlayHostMode"
        const val ASKED_ACCESSIBILITY = "askedAccessibility"

        // 1.4.0: clipboard history.
        const val CLIPBOARD_ENTRIES = "clipboardEntries"
        const val CLIPBOARD_CAPTURE = "clipboardCapture"
        const val CLIPBOARD_AUTO_PASTE = "clipboardAutoPaste"
        const val CLIPBOARD_MAX_ITEMS = "clipboardMaxItems"
        const val DEFAULT_CLIPBOARD_MAX_ITEMS = 25
        const val CLIPBOARD_MAX_ITEMS_LIMIT = 100

        /**
         * Prefix for the pre-mute level, keyed per framework stream type.
         *
         * Per stream, because muting media and muting the ringer are different acts with different
         * levels to come back to.
         */
        const val PRE_MUTE_LEVEL_PREFIX = "handlerPreMuteLevel_"
        const val CONTEXT_MENU_ITEMS = "handlerContextMenuItems"
        const val CONTEXT_MENU_ORDER = "handlerContextMenuOrder"
        const val CONTEXT_MENU_LAYOUT = "handlerContextMenuLayout"
        const val PANEL_THEME = "panelTheme"

        /**
         * What separates one action from the next in [CONTEXT_MENU_ORDER].
         *
         * ASCII unit separator, and it has to be something like it: the identifiers stored here
         * are human-readable strings — "Open deck", "Mute or Unmute" — so every printable
         * character a person would reach for is already inside one of them.
         */
        const val ORDER_SEPARATOR = "\u001F"
        const val HANDLER_SNAP_TO_EDGE = "handlerSnapToEdge"
        const val SHOW_NOTIFICATION = "showServiceNotification"
        const val ASKED_NOTIFICATION_PERMISSION = "askedNotificationPermission"

        const val THEME = "app_theme"
        const val LANGUAGE = "app_language"
    }

    // App Open Ad Paused
    fun isAppOpenAdPaused(): Boolean = sharedPreferences.getBoolean(APP_OPEN_AD_PAUSED, false)
    
    fun setAppOpenAdPaused(value: Boolean) {
        sharedPreferences.edit { putBoolean(APP_OPEN_AD_PAUSED, value) }
    }

    // App Launch Count for Review
    fun getAppLaunchCount(): Int = sharedPreferences.getInt(APP_LAUNCH_COUNT, 0)
    
    fun incrementAppLaunchCount() {
        val count = getAppLaunchCount()
        sharedPreferences.edit { putInt(APP_LAUNCH_COUNT, count + 1) }
    }
    
    fun hasShownReview(): Boolean = sharedPreferences.getBoolean(HAS_SHOWN_REVIEW, false)

    // ---- in-app review pacing ------------------------------------------------------------------

    /** How many times the review flow has been requested, ever. */
    fun getReviewAskCount(): Int = sharedPreferences.getInt(REVIEW_ASK_COUNT, 0)

    /** When the review flow was last requested, as epoch millis. 0 when never. */
    fun getReviewLastAskTime(): Long = sharedPreferences.getLong(REVIEW_LAST_ASK_TIME, 0L)

    /** The `versionCode` the last ask happened on, so one release never asks twice. */
    fun getReviewLastAskVersion(): Int = sharedPreferences.getInt(REVIEW_LAST_ASK_VERSION, 0)

    fun recordReviewAsk(versionCode: Int, now: Long = System.currentTimeMillis()) {
        sharedPreferences.edit {
            putInt(REVIEW_ASK_COUNT, getReviewAskCount() + 1)
            putLong(REVIEW_LAST_ASK_TIME, now)
            putInt(REVIEW_LAST_ASK_VERSION, versionCode)
        }
    }

    /**
     * Folds the old single `hasShownReview` boolean into the counted scheme, once.
     *
     * Seeds the timestamp to *now* rather than to zero. Zero would read as "last asked in 1970",
     * which clears the spacing gate instantly and would prompt every upgrading user on their next
     * launch — the exact behaviour this rework exists to prevent.
     */
    fun migrateReviewState(now: Long = System.currentTimeMillis()) {
        if (sharedPreferences.contains(REVIEW_ASK_COUNT)) return
        if (!hasShownReview()) return
        sharedPreferences.edit {
            putInt(REVIEW_ASK_COUNT, 1)
            putLong(REVIEW_LAST_ASK_TIME, now)
            putInt(REVIEW_LAST_ASK_VERSION, 0)
        }
    }

    /** Epoch millis of first launch. 0 when the install-time stamp has not been written yet. */
    fun getFirstInstallTimeMillis(): Long = sharedPreferences.getLong(FIRST_INSTALL_TIME, 0L)

    /** Epoch millis of the most recent ad of any kind. 0 when no ad has been shown. */
    fun getLastAnyAdTimeMillis(): Long =
        sharedPreferences.getLong(LAST_ANY_AD_TIME, -1L).coerceAtLeast(0L)
    
    fun setHasShownReview(value: Boolean) {
        sharedPreferences.edit { putBoolean(HAS_SHOWN_REVIEW, value) }
    }

    // Pro feature
    fun isProFeatureActivated(): Boolean =
        sharedPreferences.getBoolean(PRO_FEATURE_ACTIVATION, false)

    fun setProFeatureActivated(value: Boolean) {
        sharedPreferences.edit { putBoolean(PRO_FEATURE_ACTIVATION, value) }
    }

    // App running state
    fun isRunning(): Boolean = sharedPreferences.getBoolean(IS_RUNNING, false)

    fun setRunning(isRunning: Boolean) {
        sharedPreferences.edit { putBoolean(IS_RUNNING, isRunning) }
    }

    // Handler position
    fun getHandlerPosition(): String =
        sharedPreferences.getString(HANDLER_POSITION, DEFAULT_SIDE) ?: DEFAULT_SIDE

    fun setHandlerPosition(value: String) {
        sharedPreferences.edit { putString(HANDLER_POSITION, value) }
    }

    // Handler color
    fun getHandlerColor(): Int =
        sharedPreferences.getInt(HANDLER_COLOR, HandlerPresets.DEFAULT.bgColor.toArgb())

    fun setHandlerColor(value: Int) {
        sharedPreferences.edit { putInt(HANDLER_COLOR, value) }
    }

    // Handler width in dp
    fun getHandlerWidthDp(): Float =
        sharedPreferences.getFloat(HANDLER_WIDTH + "_dp", HandlerPresets.DEFAULT.width)

    fun setHandlerWidthDp(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_WIDTH + "_dp", value) }
    }

    // Handler height in dp
    fun getHandlerHeightDp(): Float =
        sharedPreferences.getFloat(HANDLER_HEIGHT, HandlerPresets.DEFAULT.height)

    fun setHandlerHeightDp(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_HEIGHT, value) }
    }

    // Handler translation Y
    //
    // LEGACY. Superseded by handlerPositionFraction — a raw pixel offset cannot survive a rotation
    // or a different screen size. Kept (and still written by nothing) for one release so that a
    // rollback to 1.2.8 finds the old value intact.
    fun getHandlerTranslationY(): Float =
        sharedPreferences.getFloat(HANDLER_TRANSLATION_Y, LEGACY_TRANSLATION_Y_DEFAULT)

    fun setHandlerTranslationY(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_TRANSLATION_Y, value) }
    }

    /**
     * Vertical position of the bar's **centre**, as a fraction (0..1) of the usable screen height.
     *
     * Stored as a fraction rather than pixels so the bar lands in the same visual place after a
     * rotation, on a different screen size, and after the user changes the bar's height.
     */
    fun getHandlerPositionFraction(): Float =
        sharedPreferences.getFloat(HANDLER_POSITION_FRACTION, DEFAULT_POSITION_FRACTION)
            .coerceIn(0f, 1f)

    fun setHandlerPositionFraction(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_POSITION_FRACTION, value.coerceIn(0f, 1f)) }
    }

    fun hasHandlerPositionFraction(): Boolean =
        sharedPreferences.contains(HANDLER_POSITION_FRACTION)

    /**
     * One-shot migration from the legacy pixel offset. Idempotent — safe to call on every geometry
     * pass.
     *
     * The legacy value is converted for *every* upgrading install, not just those that had
     * explicitly customised it. `handlerTranslationY` was only ever written by the in-app expanded
     * preview, and only while the position was unlocked, so the overwhelming majority of users have
     * no stored value at all — yet their bar has always been drawn at the 260px default. Treating
     * "no stored value" as "new user, centre the bar" would move the bar for nearly everybody on
     * update. Converting the effective value instead means nobody's bar moves.
     *
     * @param topInsetPx the legacy value is in *display* coordinates (it was authored by a Compose
     *   dialog laid out edge to edge), while a window's `y` is relative to the usable frame, so the
     *   top inset has to come off first.
     */
    fun migrateHandlerPositionFraction(
        usableHeightPx: Int,
        topInsetPx: Int,
        barHeightPx: Int,
        isPortrait: Boolean
    ) {
        if (hasHandlerPositionFraction()) return
        if (usableHeightPx <= 0) return
        // Nothing to migrate: this install never wrote the legacy pixel offset, so it is either
        // brand new or one that has already been through this. Either way the answer is the
        // current default, and converting an unwritten 260px would silently override it.
        if (!sharedPreferences.contains(HANDLER_TRANSLATION_Y)) return
        // 260px was authored in portrait. Starting in landscape, leave the pref unwritten and let
        // the next portrait pass do the conversion.
        if (!isPortrait) return

        val legacyUsableY = getHandlerTranslationY() - topInsetPx
        setHandlerPositionFraction((legacyUsableY + barHeightPx / 2f) / usableHeightPx)
    }

    /**
     * Inward nudge, in dp, from the usable screen edge.
     *
     * Curved-edge phones palm-reject touches on the extreme edge, and gesture navigation claims the
     * left and right edges for the back gesture. Nudging the bar inward moves it clear of both.
     * Defaults to 0 so no existing user's bar shifts on update.
     */
    fun getHandlerEdgeMarginDp(): Float =
        sharedPreferences.getFloat(HANDLER_EDGE_MARGIN, HandlerPresets.DEFAULT.edgeMargin).coerceIn(0f, 48f)

    fun setHandlerEdgeMarginDp(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_EDGE_MARGIN, value.coerceIn(0f, 48f)) }
    }

    // ---- free positioning, per orientation --------------------------------------------------

    private fun posXKey(isPortrait: Boolean) =
        if (isPortrait) HANDLER_POS_X_PORTRAIT else HANDLER_POS_X_LANDSCAPE

    private fun posYKey(isPortrait: Boolean) =
        if (isPortrait) HANDLER_POS_Y_PORTRAIT else HANDLER_POS_Y_LANDSCAPE

    /**
     * Horizontal position of the bar's **centre**, as a fraction (0..1) of the usable width.
     *
     * 0f is flush with the left edge and 1f flush with the right, matching the clamping in
     * [com.newagedevs.gesturevolume.service.HandlerGeometry.fractionToX] — so the two values a
     * migrating user can have, "Left" and "Right", map exactly onto the ends of the range.
     */
    fun getHandlerPosXFraction(isPortrait: Boolean): Float =
        sharedPreferences.getFloat(posXKey(isPortrait), 1f).coerceIn(0f, 1f)

    fun setHandlerPosXFraction(isPortrait: Boolean, value: Float) {
        sharedPreferences.edit { putFloat(posXKey(isPortrait), value.coerceIn(0f, 1f)) }
    }

    fun getHandlerPosYFraction(isPortrait: Boolean): Float =
        sharedPreferences.getFloat(posYKey(isPortrait), DEFAULT_POSITION_FRACTION).coerceIn(0f, 1f)

    fun setHandlerPosYFraction(isPortrait: Boolean, value: Float) {
        sharedPreferences.edit { putFloat(posYKey(isPortrait), value.coerceIn(0f, 1f)) }
    }

    fun hasHandlerPosXFraction(isPortrait: Boolean): Boolean =
        sharedPreferences.contains(posXKey(isPortrait))

    fun hasHandlerPosYFraction(isPortrait: Boolean): Boolean =
        sharedPreferences.contains(posYKey(isPortrait))

    /**
     * Forgets the stored horizontal position in both orientations, so the next geometry pass
     * re-derives it from the default side and the edge offset.
     *
     * Reached only from "Reset position", which is the one route back for a bar dragged somewhere
     * awkward — behind a game's on-screen controls, or off under a rounded corner. Both
     * orientations, because the bar the user cannot reach may not be the one they are looking at.
     */
    fun clearHandlerPosXFraction() {
        sharedPreferences.edit {
            remove(HANDLER_POS_X_PORTRAIT)
            remove(HANDLER_POS_X_LANDSCAPE)
        }
    }

    /**
     * Seeds this orientation's free position from the pre-free-drag settings, once.
     *
     * Runs per orientation rather than once globally: a user who has only ever held the phone
     * upright must still get a sensible landscape placement the first time they turn it, and that
     * placement is derived from the same side setting rather than left to a default that could
     * drop the bar somewhere they never put it.
     *
     * @param xFraction where the old Left/Right side setting puts the bar's centre in this frame,
     *   edge margin included — computed by the caller, which is the only place that knows the
     *   frame and the bar's width.
     */
    fun migrateHandlerFreePosition(isPortrait: Boolean, xFraction: Float) {
        if (!hasHandlerPosXFraction(isPortrait)) setHandlerPosXFraction(isPortrait, xFraction)
        if (!hasHandlerPosYFraction(isPortrait)) {
            setHandlerPosYFraction(isPortrait, getHandlerPositionFraction())
        }
    }

    // ---- behaviour toggles --------------------------------------------------------------------

    /**
     * Whether the user has put the bar away with "Hide handler".
     *
     * Durable rather than "there is no handler window right now", because a rebuild is the normal
     * consequence of almost anything: saving a setting, a `START_STICKY` relaunch, a reboot, the
     * task being swiped out of Recents. Each of those used to bring a hidden bar back on its own.
     * Only an explicit Show clears this, and stopping the service clears it too — a service that
     * is off has nothing to hide, and the bar must be there when it is switched on again.
     */
    /**
     * Whether the app's own UI is on screen right now.
     *
     * In preferences rather than in memory, and that is the whole point of it. The activity tells
     * the service to hide the bar as it comes to the foreground, but a *cold* start races: the
     * command is sent while the service process is still coming up, so it can arrive before the
     * controller exists — or be processed before the controller has drawn the bar — and either way
     * the bar appears on top of the app that just asked for it to go away. A flag both sides can
     * read has no ordering to get wrong: whoever creates the bar checks it, whenever that happens.
     *
     * Written on every resume and pause, so it is also correct after the service is killed and
     * restarted by the system while the app sits in front of it.
     */
    fun isAppInForeground(): Boolean = sharedPreferences.getBoolean(APP_IN_FOREGROUND, false)

    fun setAppInForeground(value: Boolean) {
        sharedPreferences.edit { putBoolean(APP_IN_FOREGROUND, value) }
    }

    fun isHandlerHidden(): Boolean = sharedPreferences.getBoolean(HANDLER_HIDDEN, false)

    fun setHandlerHidden(value: Boolean) {
        sharedPreferences.edit { putBoolean(HANDLER_HIDDEN, value) }
    }

    /**
     * Whether the bar shows the volume level, as a percentage, while a swipe is adjusting it.
     *
     * On by default: the swipe changes something the user cannot otherwise see without the system
     * volume panel, and the readout is only on screen during the gesture that asked for it.
     */
    fun getShowVolumePercent(): Boolean =
        sharedPreferences.getBoolean(SHOW_VOLUME_PERCENT, true)

    fun setShowVolumePercent(value: Boolean) {
        sharedPreferences.edit { putBoolean(SHOW_VOLUME_PERCENT, value) }
    }

    /**
     * How hard the bar should try to follow the audio the device is actually playing.
     *
     * Defaults to [VolumeStreamMode.FOLLOW_PLAYBACK] rather than the strictly-compatible
     * [VolumeStreamMode.MEDIA_ONLY]: a swipe during a call moving media volume is plainly wrong and
     * worth fixing for everyone, while making an *idle* swipe change the ringer would silently
     * change what the bar does for every existing install — so that half stays opt-in.
     *
     * Sanitised on read like every other identifier-valued preference here, because `allowBackup`
     * means a value written by a future build can arrive from a cloud restore on an older one.
     */
    fun getVolumeStreamMode(): String = VolumeStreamMode.sanitize(
        sharedPreferences.getString(VOLUME_STREAM_MODE, VolumeStreamMode.FOLLOW_PLAYBACK)
    )

    fun setVolumeStreamMode(value: String) {
        sharedPreferences.edit { putString(VOLUME_STREAM_MODE, VolumeStreamMode.sanitize(value)) }
    }

    /**
     * What swiping from the bar toward the middle of the screen does.
     *
     * Three sources, in order. A value stored under the 1.4.0 key wins. Failing that, the 1.3.5
     * switch that bound this swipe to the long-press menu is honoured — on means the menu, off
     * means nothing, exactly what those users had. Failing both, the Deck: it is the reason the
     * swipe exists now, and an install that has never touched either setting gets its headline
     * feature on the gesture built for it.
     *
     * The bar asks the system not to treat its own bounds as the back-gesture zone, so a swipe that
     * starts on the bar is the bar's; a Back swipe that lands there was already a miss before this
     * did anything. Users who would rather keep that miss silent set this to None.
     */
    fun getHandlerSwipeInAction(): String {
        sharedPreferences.getString(HANDLER_SWIPE_IN, null)?.let { return HandlerActions.sanitize(it) }
        if (sharedPreferences.contains(HANDLER_EDGE_SWIPE_MENU)) {
            return if (sharedPreferences.getBoolean(HANDLER_EDGE_SWIPE_MENU, false)) {
                HandlerActions.OPEN_MENU
            } else {
                HandlerActions.NONE
            }
        }
        return HandlerActions.OPEN_DECK
    }

    fun setHandlerSwipeInAction(value: String) {
        sharedPreferences.edit { putString(HANDLER_SWIPE_IN, value) }
    }

    /**
     * What swiping from the bar toward the nearer screen edge does. Nothing by default: a bar flush
     * against the edge has nowhere to be swiped to, so this is only reachable with an edge distance
     * or a bar parked mid-screen.
     */
    fun getHandlerSwipeOutAction(): String =
        HandlerActions.sanitize(
            sharedPreferences.getString(HANDLER_SWIPE_OUT, HandlerActions.NONE) ?: HandlerActions.NONE
        )

    fun setHandlerSwipeOutAction(value: String) {
        sharedPreferences.edit { putString(HANDLER_SWIPE_OUT, value) }
    }

    fun getHandlerTripleTapAction(): String =
        HandlerActions.sanitize(
            sharedPreferences.getString(HANDLER_TRIPLE_TAP, HandlerActions.NONE) ?: HandlerActions.NONE
        )

    fun setHandlerTripleTapAction(value: String) {
        sharedPreferences.edit { putString(HANDLER_TRIPLE_TAP, value) }
    }

    /**
     * Which service draws the bar. See [OverlayHostMode].
     *
     * The accessibility route by default, and this default costs an install that has not granted
     * accessibility exactly nothing: `OverlayRuntime.effectiveHost` needs the service to be bound
     * as well as preferred, and falls back to the notification route when it is not. So this
     * decides only what happens once the user does grant it — and then it should be used, for
     * three reasons.
     *
     * It draws where the other route cannot. `TYPE_ACCESSIBILITY_OVERLAY` is shown over the
     * Settings app and the other privileged screens where the platform hides
     * `TYPE_APPLICATION_OVERLAY` outright — so with the notification route the bar simply vanishes
     * in Settings, which reads as the app crashing.
     *
     * It needs no overlay permission and no notification. Android will not run a foreground
     * service without an ongoing notification, and a permanent notification for a floating bar is
     * the single most common complaint about apps of this kind.
     *
     * And it removes the conflict. With both permissions granted and this defaulting the other
     * way, the app took the notification route while the user was looking at a granted
     * accessibility permission and an ongoing notification, and concluded — correctly — that the
     * two were fighting.
     */
    fun getOverlayHostMode(): OverlayHostMode =
        OverlayHostMode.fromStored(
            sharedPreferences.getString(OVERLAY_HOST_MODE, OverlayHostMode.ACCESSIBILITY.name)
        )

    fun setOverlayHostMode(mode: OverlayHostMode) {
        sharedPreferences.edit { putString(OVERLAY_HOST_MODE, mode.name) }
    }

    // ---- clipboard history ----------------------------------------------------------------------

    /**
     * Whether the accessibility service watches for copied text. Off by default: it is the one
     * thing the service does that reads anything, and the user opts in from the Clipboard screen.
     */
    fun getClipboardCaptureEnabled(): Boolean =
        sharedPreferences.getBoolean(CLIPBOARD_CAPTURE, false)

    fun setClipboardCaptureEnabled(value: Boolean) {
        sharedPreferences.edit { putBoolean(CLIPBOARD_CAPTURE, value) }
    }

    /** Whether tapping a clip in the Deck pastes it into the focused field, rather than only copying. */
    fun getClipboardAutoPaste(): Boolean =
        sharedPreferences.getBoolean(CLIPBOARD_AUTO_PASTE, false)

    fun setClipboardAutoPaste(value: Boolean) {
        sharedPreferences.edit { putBoolean(CLIPBOARD_AUTO_PASTE, value) }
    }

    fun getClipboardMaxItems(): Int =
        sharedPreferences.getInt(CLIPBOARD_MAX_ITEMS, DEFAULT_CLIPBOARD_MAX_ITEMS)
            .coerceIn(5, CLIPBOARD_MAX_ITEMS_LIMIT)

    fun setClipboardMaxItems(value: Int) {
        sharedPreferences.edit { putInt(CLIPBOARD_MAX_ITEMS, value.coerceIn(5, CLIPBOARD_MAX_ITEMS_LIMIT)) }
    }

    /** The history, newest first. Stored as one JSON array; a few dozen short strings at most. */
    fun getClipboardEntries(): List<ClipboardEntry> {
        val raw = sharedPreferences.getString(CLIPBOARD_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                ClipboardEntry(o.getLong("id"), o.getString("text"), o.optLong("time"))
            }
        }.getOrDefault(emptyList())
    }

    private fun putClipboardEntries(entries: List<ClipboardEntry>) {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(JSONObject().put("id", e.id).put("text", e.text).put("time", e.timeMillis))
        }
        sharedPreferences.edit { putString(CLIPBOARD_ENTRIES, array.toString()) }
    }

    /**
     * Records a clip at the top of the history.
     *
     * A text already in the list moves to the top rather than appearing twice, and the list is
     * trimmed to the configured size from the bottom — the oldest goes first.
     */
    fun addClipboardEntry(text: String): ClipboardEntry? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val now = System.currentTimeMillis()
        val entry = ClipboardEntry(id = now, text = trimmed, timeMillis = now)
        val rest = getClipboardEntries().filterNot { it.text == trimmed }
        putClipboardEntries((listOf(entry) + rest).take(getClipboardMaxItems()))
        return entry
    }

    fun removeClipboardEntry(id: Long) {
        putClipboardEntries(getClipboardEntries().filterNot { it.id == id })
    }

    fun clearClipboardEntries() {
        sharedPreferences.edit { remove(CLIPBOARD_ENTRIES) }
    }

    /** Whether the accessibility disclosure has been shown and accepted once. */
    fun hasAcceptedAccessibilityDisclosure(): Boolean =
        sharedPreferences.getBoolean(ASKED_ACCESSIBILITY, false)

    fun setAcceptedAccessibilityDisclosure(value: Boolean) {
        sharedPreferences.edit { putBoolean(ASKED_ACCESSIBILITY, value) }
    }

    /**
     * The level a stream was at before the bar muted it, or -1 when there is no memory.
     *
     * Persisted rather than held in the service, which is what it replaces. The old field was an
     * `Int` on [com.newagedevs.gesturevolume.service.OverlayService] initialised to 1, so it was
     * reset by every settings save — saving rebuilds the handler — and by every `START_STICKY`
     * relaunch. Unmuting then jumped to a level the user never chose.
     */
    fun getPreMuteLevel(stream: Int): Int =
        sharedPreferences.getInt(PRE_MUTE_LEVEL_PREFIX + stream, -1)

    fun setPreMuteLevel(stream: Int, level: Int) {
        sharedPreferences.edit { putInt(PRE_MUTE_LEVEL_PREFIX + stream, level) }
    }

    /**
     * Whether the bar flies to the nearer side when the finger lifts.
     *
     * On by default: a side bar that comes to rest against an edge is what almost everyone wants,
     * and the alternative leaves it stranded wherever the drag happened to end. Switching it off
     * gives free two-axis placement, and in *both* modes [getHandlerEdgeMarginDp] is the same
     * promise — the closest the bar may ever come to the edge, which is also exactly where it
     * parks when it snaps. One number, one meaning.
     */
    fun getHandlerSnapToEdge(): Boolean =
        sharedPreferences.getBoolean(HANDLER_SNAP_TO_EDGE, true)

    fun setHandlerSnapToEdge(value: Boolean) {
        sharedPreferences.edit { putBoolean(HANDLER_SNAP_TO_EDGE, value) }
    }

    /**
     * Whether the ongoing notification carries its Show/Hide, Settings and Stop buttons.
     *
     * On by default, and it is the only route back to a bar put away with "Hide handler" while
     * the app is closed. Android will not run a foreground service without *some* notification, so
     * switching this off does not delete it — it downgrades it to a silent minimum-importance row
     * with no buttons and no status-bar icon. See `OverlayService.startForegroundService`.
     */
    fun getShowNotification(): Boolean =
        sharedPreferences.getBoolean(SHOW_NOTIFICATION, true)

    fun setShowNotification(value: Boolean) {
        sharedPreferences.edit { putBoolean(SHOW_NOTIFICATION, value) }
    }

    /**
     * Whether POST_NOTIFICATIONS has already been asked for once, unprompted.
     *
     * Android 13+ stops showing the system dialog after two refusals, so asking on every service
     * start would just be a no-op that looks like a bug. Asked once when the user first switches
     * the service on; after that the Permissions screen is the deliberate route.
     */
    fun hasAskedNotificationPermission(): Boolean =
        sharedPreferences.getBoolean(ASKED_NOTIFICATION_PERMISSION, false)

    fun setAskedNotificationPermission(value: Boolean) {
        sharedPreferences.edit { putBoolean(ASKED_NOTIFICATION_PERMISSION, value) }
    }

    // ---- long-press context menu ---------------------------------------------------------------

    /**
     * Which actions the long-press menu offers, as [HandlerActions] identifiers.
     *
     * A set rather than an ordered list: the menu renders in a fixed canonical order so that an
     * entry does not move under the user's thumb between one long press and the next.
     */
    /**
     * The long-press menu, in the order it is drawn.
     *
     * A list, where this used to be a `Set`, because the user can now arrange it. That is not a
     * cosmetic difference: `putStringSet` gives no guarantee about iteration order at all, so the
     * old storage could not have held an arrangement even if something had wanted to write one —
     * the menu was ordered by the catalog and the preference only said which rows survived.
     *
     * Three sources, in order. The new key wins. Failing that, an install that predates
     * arrangement has its set migrated, sequenced by [HandlerActionCatalog.CONTEXT_MENU_CANDIDATES]
     * so it comes out in exactly the order that install was already drawing. Failing both, the
     * default menu. Nothing is written during any of this: the migration is a read, so a user who
     * never opens the picker keeps their old preference intact for an older build to read back.
     */
    fun getContextMenuOrder(): List<String> {
        val stored = sharedPreferences.getString(CONTEXT_MENU_ORDER, null)
        if (stored != null) {
            return stored.split(ORDER_SEPARATOR)
                .filter { it.isNotEmpty() }
                .filter { it in HandlerActions.KNOWN }
        }
        val legacy = sharedPreferences.getStringSet(CONTEXT_MENU_ITEMS, null)
            ?.let { HandlerActions.sanitize(it) }
            ?: HandlerActions.DEFAULT_CONTEXT_MENU
        return HandlerActionCatalog.CONTEXT_MENU_CANDIDATES
            .map { it.action }
            .filter { it in legacy }
    }

    fun setContextMenuOrder(value: List<String>) {
        sharedPreferences.edit {
            putString(CONTEXT_MENU_ORDER, value.joinToString(ORDER_SEPARATOR))
            // The old key is kept in step so that a downgrade, or any code still reading the set
            // form, sees the same menu — minus the arrangement, which it could not have used.
            putStringSet(CONTEXT_MENU_ITEMS, value.toSet())
        }
    }

    /** The set form, for callers that only ask whether something is in the menu. */
    fun getContextMenuItems(): Set<String> = getContextMenuOrder().toSet()

    /**
     * Whether the menu is drawn as a grid of icons or a list of labelled rows.
     *
     * A grid puts more within one thumb's reach and is read by shape; a list is read by name and
     * is the only one that works for someone who does not recognise the icons. Neither is right
     * for everyone, which is why it is a setting rather than a decision.
     */
    /**
     * How the floating panels are dressed. Shared by the menu, the Quick panel and the Deck.
     *
     * Solid by default, which is what they looked like before the setting existed — the blurred
     * options cost GPU work on every frame the panel is up, and are not something to hand someone
     * who has not asked for them.
     */
    fun getPanelTheme(): String =
        PanelTheme.sanitize(sharedPreferences.getString(PANEL_THEME, null))

    fun setPanelTheme(value: String) {
        sharedPreferences.edit { putString(PANEL_THEME, value) }
    }

    fun getContextMenuLayout(): String =
        ContextMenuLayout.sanitize(sharedPreferences.getString(CONTEXT_MENU_LAYOUT, null))

    fun setContextMenuLayout(value: String) {
        sharedPreferences.edit { putString(CONTEXT_MENU_LAYOUT, value) }
    }

    /** True when *we* turned adaptive brightness off, so we know it is ours to hand back. */
    fun getBrightnessAutoWasOn(): Boolean =
        sharedPreferences.getBoolean(BRIGHTNESS_AUTO_WAS_ON, false)

    fun setBrightnessAutoWasOn(value: Boolean) {
        sharedPreferences.edit { putBoolean(BRIGHTNESS_AUTO_WAS_ON, value) }
    }

    // Tap actions
    fun getHandlerSingleTapAction(): String =
        HandlerActions.sanitize(
            sharedPreferences.getString(HANDLER_SINGLE_TAP, "Open volume UI")
                ?: "Open volume UI"
        )

    fun setHandlerSingleTapAction(value: String) {
        sharedPreferences.edit { putString(HANDLER_SINGLE_TAP, value) }
    }

    /**
     * What a double tap does. The Quick panel, unless the user has said otherwise.
     *
     * The panel needs a slot of its own, and this is the one it can have without taking anything.
     * Long press is reposition — the only way to move the bar, and not worth trading. Single tap is
     * the gesture most likely to be hit by accident. A double tap was bound to nothing at all, and
     * costs nothing to give away: `isDoubleTapArmed` means a single tap now waits out the
     * double-tap timeout, but the single tap is `None` by default too, so on a fresh install there
     * is no action being delayed.
     *
     * Read through the same `sanitize` as every other slot, so an install that arrives from a
     * newer build with something unrecognised here falls back to None rather than to this default.
     */
    fun getHandlerDoubleTapAction(): String =
        HandlerActions.sanitize(
            sharedPreferences.getString(HANDLER_DOUBLE_TAP, HandlerActions.OPEN_QUICK_SLIDER)
                ?: HandlerActions.OPEN_QUICK_SLIDER
        )

    fun setHandlerDoubleTapAction(value: String) {
        sharedPreferences.edit { putString(HANDLER_DOUBLE_TAP, value) }
    }

    // Defaults to Reposition: moving the bar has to live on some gesture, and the long press is the
    // only one that cannot be triggered by accident. Users who want something else on long press
    // just pick it — that also switches repositioning off, which is the whole point of one setting
    // owning the gesture.
    fun getHandlerLongTapAction(): String =
        HandlerActions.sanitize(
            sharedPreferences.getString(HANDLER_LONG_TAP, HandlerActions.REPOSITION)
                ?: HandlerActions.REPOSITION
        )

    fun setHandlerLongTapAction(value: String) {
        sharedPreferences.edit { putString(HANDLER_LONG_TAP, value) }
    }

    // Swipe actions
    /**
     * What swiping up the bar does. The Quick panel, unless the user has said otherwise.
     *
     * It used to step the volume directly, and stepping is still what it does for anyone who has
     * chosen one of the volume or brightness bindings. The panel is the better default because a
     * swipe that opens it can be short and careless — the value is then set on a track that stays
     * put and can be corrected — whereas a swipe that *is* the adjustment has to be accurate on
     * the first attempt, on a target at the very edge of the screen.
     */
    fun getHandlerSwipeUpAction(): String =
        HandlerActions.sanitize(
            sharedPreferences.getString(HANDLER_SWIPE_UP, HandlerActions.OPEN_QUICK_SLIDER)
                ?: HandlerActions.OPEN_QUICK_SLIDER
        )

    fun setHandlerSwipeUpAction(value: String) {
        sharedPreferences.edit { putString(HANDLER_SWIPE_UP, value) }
    }

    // The default said "Increase..." for the swipe-DOWN slot, which showed the wrong row and the
    // wrong icon in the picker. Correcting it is behaviour-neutral because the direction of a swipe
    // comes from the gesture's sign, never from this string.
    /** The other half of the pair. See [getHandlerSwipeUpAction]. */
    fun getHandlerSwipeDownAction(): String =
        HandlerActions.sanitize(
            sharedPreferences.getString(HANDLER_SWIPE_DOWN, HandlerActions.OPEN_QUICK_SLIDER)
                ?: HandlerActions.OPEN_QUICK_SLIDER
        )

    fun setHandlerSwipeDownAction(value: String) {
        sharedPreferences.edit { putString(HANDLER_SWIPE_DOWN, value) }
    }

    // First launch
    fun isFirstLaunch(): Boolean {
        return sharedPreferences.getBoolean(FIRST_LAUNCH, true)
    }

    fun setFirstLaunchCompleted() {
        sharedPreferences.edit { putBoolean(FIRST_LAUNCH, false) }
    }

    // ========== AD MANAGEMENT WITH COOLDOWNS ==========

    // Number of interstitials shown this app session (process lifetime). Not persisted.
    private var sessionInterstitialCount = 0

    /**
     * Stamps the moment this install first ran, which is what the ad grace period counts from.
     * Idempotent — only the first call ever writes.
     *
     * Call this as early as possible in GestureApplication.onCreate, before anything can flip
     * [isFirstLaunch]: AppOpenManager.shouldShowAd clears the first-launch flag on the very
     * first ON_START, so a later stamp could not tell a new install from an upgrading one.
     *
     * Upgrading installs are stamped 0 rather than "now". They have already set the app up, so
     * handing them two silent hours on every update would cost impressions and buy nothing.
     */
    fun initInstallTimeIfNeeded() {
        if (sharedPreferences.contains(FIRST_INSTALL_TIME)) return
        val stamp = if (isFirstLaunch()) System.currentTimeMillis() else 0L
        sharedPreferences.edit { putLong(FIRST_INSTALL_TIME, stamp) }
    }

    /**
     * True while the install is still inside its post-install grace period.
     *
     * What the grace period actually protects is *setup*: the stretch where the user is bouncing
     * in and out of system permission screens, placing the bar and trying gestures. An ad that
     * takes the screen during that is the one most likely to cost us the user outright.
     *
     * So it blocks every ad that can take the screen: **app-open ads and interstitials alike,
     * with no exceptions**. Nothing full-screen runs in the first two hours of an install.
     *
     * Native placements are unaffected — they sit inline in the layout and never take the screen
     * away from anyone. During grace they are the only ads running, which is the point: revenue
     * continues, interruption does not.
     */
    fun isInAdGracePeriod(): Boolean {
        val installTime = sharedPreferences.getLong(FIRST_INSTALL_TIME, 0L)
        if (installTime <= 0L) return false
        val elapsed = System.currentTimeMillis() - installTime
        // A backwards clock change (manual set, timezone-driven reset) yields a negative elapsed.
        // Treat that as "still in grace" rather than trusting it, so the worst case is a few quiet
        // hours instead of an ad landing in the middle of setup.
        return elapsed < AD_GRACE_PERIOD
    }

    /** Milliseconds left in the grace period, or 0 once it has lapsed. For diagnostics. */
    fun getAdGraceRemainingMillis(): Long {
        val installTime = sharedPreferences.getLong(FIRST_INSTALL_TIME, 0L)
        if (installTime <= 0L) return 0L
        return (installTime + AD_GRACE_PERIOD - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /**
     * Save the time when an app open ad was shown
     * This also updates the "any ad" time to prevent interstitial ads immediately after
     */
    fun saveAppOpenAdTime() {
        val currentTime = System.currentTimeMillis()
        sharedPreferences.edit {
            putLong(LAST_APP_OPEN_AD_TIME, currentTime)
            putLong(LAST_ANY_AD_TIME, currentTime)
        }
    }

    /**
     * Save the time when an interstitial ad was shown
     * This also updates the "any ad" time to prevent app open ads immediately after
     */
    fun saveInterstitialAdTime() {
        val currentTime = System.currentTimeMillis()
        sessionInterstitialCount++
        sharedPreferences.edit {
            putLong(LAST_INTERSTITIAL_AD_TIME, currentTime)
            putLong(LAST_ANY_AD_TIME, currentTime)
        }
    }

    /**
     * Check if app open ad should be shown
     * Returns true if:
     * 1. 60 minutes have passed since last app open ad
     * 2. 90 seconds have passed since any ad (app open or interstitial)
     * 3. Not first launch
     */
    fun shouldShowAppOpenAd(): Boolean {
        if (isFirstLaunch()) {
            return false
        }

        // Nothing full-screen until the user has had a chance to finish setting up.
        if (isInAdGracePeriod()) {
            return false
        }

        val currentTime = System.currentTimeMillis()
        val lastAppOpenAdTime = sharedPreferences.getLong(LAST_APP_OPEN_AD_TIME, -1L)
        val lastAnyAdTime = sharedPreferences.getLong(LAST_ANY_AD_TIME, -1L)

        // Check if 60 minutes have passed since last app open ad
        val appOpenCooldownPassed = lastAppOpenAdTime == -1L ||
                (currentTime - lastAppOpenAdTime) >= APP_OPEN_AD_COOLDOWN

        // Check if 90 seconds have passed since any ad
        val anyAdCooldownPassed = lastAnyAdTime == -1L ||
                (currentTime - lastAnyAdTime) >= MIN_TIME_BETWEEN_ANY_ADS

        return appOpenCooldownPassed && anyAdCooldownPassed
    }

    /**
     * Whether an interstitial may be shown at all right now.
     *
     * Returns true when the install is out of its grace period, the per-session cap has room,
     * three minutes have passed since the last interstitial and ninety seconds since any ad.
     *
     * The grace period has no exceptions. It previously let the service-start interstitial
     * through on the reasoning that finishing setup is a natural stop rather than an
     * interruption — but the whole point of the first two hours is that nothing takes the screen,
     * and one full-screen ad is exactly what the user remembers from a first session. Native
     * placements carry the revenue during grace.
     */
    fun shouldShowInterstitialAd(): Boolean {
        // Nothing full-screen mid-setup. No exceptions.
        if (isInAdGracePeriod()) {
            return false
        }

        // Respect the per-session cap before anything else.
        if (sessionInterstitialCount >= MAX_INTERSTITIALS_PER_SESSION) {
            return false
        }

        val currentTime = System.currentTimeMillis()
        val lastInterstitialAdTime = sharedPreferences.getLong(LAST_INTERSTITIAL_AD_TIME, -1L)
        val lastAnyAdTime = sharedPreferences.getLong(LAST_ANY_AD_TIME, -1L)

        // Check if 90 seconds have passed since last interstitial ad
        val interstitialCooldownPassed = lastInterstitialAdTime == -1L ||
                (currentTime - lastInterstitialAdTime) >= INTERSTITIAL_AD_COOLDOWN

        // Check if 90 seconds have passed since any ad
        val anyAdCooldownPassed = lastAnyAdTime == -1L ||
                (currentTime - lastAnyAdTime) >= MIN_TIME_BETWEEN_ANY_ADS

        return interstitialCooldownPassed && anyAdCooldownPassed
    }

    /**
     * Get time remaining until next app open ad can be shown (in seconds)
     * Returns 0 if ad can be shown now
     */
    fun getAppOpenAdCooldownRemaining(): Long {
        val currentTime = System.currentTimeMillis()
        val lastAppOpenAdTime = sharedPreferences.getLong(LAST_APP_OPEN_AD_TIME, -1L)
        val lastAnyAdTime = sharedPreferences.getLong(LAST_ANY_AD_TIME, -1L)

        if (lastAppOpenAdTime == -1L && lastAnyAdTime == -1L) return 0

        val appOpenRemaining = if (lastAppOpenAdTime != -1L) {
            maxOf(0, APP_OPEN_AD_COOLDOWN - (currentTime - lastAppOpenAdTime))
        } else 0

        val anyAdRemaining = if (lastAnyAdTime != -1L) {
            maxOf(0, MIN_TIME_BETWEEN_ANY_ADS - (currentTime - lastAnyAdTime))
        } else 0

        return maxOf(appOpenRemaining, anyAdRemaining) / 1000 // Convert to seconds
    }

    /**
     * Get time remaining until next interstitial ad can be shown (in seconds)
     * Returns 0 if ad can be shown now
     */
    fun getInterstitialAdCooldownRemaining(): Long {
        val currentTime = System.currentTimeMillis()
        val lastInterstitialAdTime = sharedPreferences.getLong(LAST_INTERSTITIAL_AD_TIME, -1L)
        val lastAnyAdTime = sharedPreferences.getLong(LAST_ANY_AD_TIME, -1L)

        if (lastInterstitialAdTime == -1L && lastAnyAdTime == -1L) return 0

        val interstitialRemaining = if (lastInterstitialAdTime != -1L) {
            maxOf(0, INTERSTITIAL_AD_COOLDOWN - (currentTime - lastInterstitialAdTime))
        } else 0

        val anyAdRemaining = if (lastAnyAdTime != -1L) {
            maxOf(0, MIN_TIME_BETWEEN_ANY_ADS - (currentTime - lastAnyAdTime))
        } else 0

        return maxOf(interstitialRemaining, anyAdRemaining) / 1000 // Convert to seconds
    }

    // ========== APPEARANCE SETTINGS ==========

    // Background alpha
    fun getHandlerBackgroundAlpha(): Int =
        sharedPreferences.getInt(HANDLER_BACKGROUND_ALPHA, HandlerPresets.DEFAULT.bgAlpha)

    fun setHandlerBackgroundAlpha(value: Int) {
        sharedPreferences.edit { putInt(HANDLER_BACKGROUND_ALPHA, value) }
    }

    // Stroke settings
    fun getHandlerStrokeColor(): Int =
        sharedPreferences.getInt(HANDLER_STROKE_COLOR, HandlerPresets.DEFAULT.strokeColor.toArgb())

    fun setHandlerStrokeColor(value: Int) {
        sharedPreferences.edit { putInt(HANDLER_STROKE_COLOR, value) }
    }

    fun getHandlerStrokeWidth(): Float =
        sharedPreferences.getFloat(HANDLER_STROKE_WIDTH, HandlerPresets.DEFAULT.strokeWidth)

    fun setHandlerStrokeWidth(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_STROKE_WIDTH, value) }
    }

    fun getHandlerStrokeAlpha(): Int =
        sharedPreferences.getInt(HANDLER_STROKE_ALPHA, HandlerPresets.DEFAULT.strokeAlpha)

    fun setHandlerStrokeAlpha(value: Int) {
        sharedPreferences.edit { putInt(HANDLER_STROKE_ALPHA, value) }
    }

    // Corner radius settings
    fun getHandlerCornerRadiusTL(): Float =
        sharedPreferences.getFloat(HANDLER_CORNER_RADIUS_TL, HandlerPresets.DEFAULT.topLeft)

    fun setHandlerCornerRadiusTL(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_CORNER_RADIUS_TL, value) }
    }

    fun getHandlerCornerRadiusTR(): Float =
        sharedPreferences.getFloat(HANDLER_CORNER_RADIUS_TR, HandlerPresets.DEFAULT.topRight)

    fun setHandlerCornerRadiusTR(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_CORNER_RADIUS_TR, value) }
    }

    fun getHandlerCornerRadiusBL(): Float =
        sharedPreferences.getFloat(HANDLER_CORNER_RADIUS_BL, HandlerPresets.DEFAULT.bottomLeft)

    fun setHandlerCornerRadiusBL(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_CORNER_RADIUS_BL, value) }
    }

    fun getHandlerCornerRadiusBR(): Float =
        sharedPreferences.getFloat(HANDLER_CORNER_RADIUS_BR, HandlerPresets.DEFAULT.bottomRight)

    fun setHandlerCornerRadiusBR(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_CORNER_RADIUS_BR, value) }
    }

    /**
     * The outline the bar is cut to, and how far a tab's ends sweep. See [HandlerShape].
     *
     * Absent from an install that predates shapes, and the fallback for that is the Default
     * preset's — a rounded rectangle — so nobody's bar changes shape on update. That is also why
     * these two keys are *not* in [pinLegacyAppearanceDefaults]: there is nothing to pin when the
     * new default and the old behaviour are the same thing.
     */
    fun getHandlerShape(): String =
        HandlerShape.sanitize(
            sharedPreferences.getString(HANDLER_SHAPE, HandlerPresets.DEFAULT.shape)
        )

    fun setHandlerShape(value: String) {
        sharedPreferences.edit { putString(HANDLER_SHAPE, HandlerShape.sanitize(value)) }
    }

    fun getHandlerShapeFlare(): Float =
        HandlerShape.sanitizeFlare(
            sharedPreferences.getFloat(HANDLER_SHAPE_FLARE, HandlerPresets.DEFAULT.flare)
        )

    fun setHandlerShapeFlare(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_SHAPE_FLARE, HandlerShape.sanitizeFlare(value)) }
    }

    fun setAllCornerRadii(value: Float) {
        sharedPreferences.edit {
            putFloat(HANDLER_CORNER_RADIUS_TL, value)
            putFloat(HANDLER_CORNER_RADIUS_TR, value)
            putFloat(HANDLER_CORNER_RADIUS_BL, value)
            putFloat(HANDLER_CORNER_RADIUS_BR, value)
        }
    }

    // Icon settings
    //
    // The handler icon is persisted by its stable resource ENTRY NAME (e.g. "ic_vol_increase")
    // rather than the raw R.drawable Int. Resource IDs are not stable across app builds, so a
    // previously stored Int can dangle after an update and crash painterResource()/getDrawable()
    // with Resources$NotFoundException. Names always resolve to a valid id in the current build,
    // or fall back to the default when the icon no longer exists.
    fun getHandlerIconRes(): Int {
        val name = sharedPreferences.getString(HANDLER_ICON_NAME, null)
        if (name != null) {
            val id = appContext.resources.getIdentifier(name, "drawable", appContext.packageName)
            return if (id != 0) id else R.drawable.ic_vol_increase
        }
        // Migrate legacy Int-based storage to the name-based format (one-time, on first read).
        val legacyId = appContext.safeDrawableIdOrDefault(
            sharedPreferences.getInt(HANDLER_ICON_RES, HandlerPresets.DEFAULT.iconRes)
        )
        setHandlerIconRes(legacyId)
        return legacyId
    }

    fun setHandlerIconRes(value: Int) {
        val safeId = appContext.safeDrawableIdOrDefault(value)
        val name = try {
            appContext.resources.getResourceEntryName(safeId)
        } catch (_: Exception) {
            appContext.resources.getResourceEntryName(R.drawable.ic_vol_increase)
        }
        sharedPreferences.edit { putString(HANDLER_ICON_NAME, name) }
    }

    fun getHandlerIconSize(): Float =
        sharedPreferences.getFloat(HANDLER_ICON_SIZE, HandlerPresets.DEFAULT.iconSize)

    fun setHandlerIconSize(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_ICON_SIZE, value) }
    }

    fun getHandlerIconColor(): Int =
        sharedPreferences.getInt(HANDLER_ICON_COLOR, HandlerPresets.DEFAULT.iconColor.toArgb())

    fun setHandlerIconColor(value: Int) {
        sharedPreferences.edit { putInt(HANDLER_ICON_COLOR, value) }
    }

    // Off by default: the bar reads as a cleaner slab without a glyph in it, and the icon is
    // decorative — nothing about the gestures depends on it. Users who want it turn it on.
    fun getHandlerShowIcon(): Boolean =
        sharedPreferences.getBoolean(HANDLER_SHOW_ICON, HandlerPresets.DEFAULT.showIcon)

    fun setHandlerShowIcon(value: Boolean) {
        sharedPreferences.edit { putBoolean(HANDLER_SHOW_ICON, value) }
    }

    // Behavior settings
    fun getHandlerVibrateOnClick(): Boolean =
        sharedPreferences.getBoolean(HANDLER_VIBRATE_ON_CLICK, HandlerPresets.DEFAULT.vibrate)

    fun setHandlerVibrateOnClick(value: Boolean) {
        sharedPreferences.edit { putBoolean(HANDLER_VIBRATE_ON_CLICK, value) }
    }

    // Theme and Language
    fun getTheme(): Int = sharedPreferences.getInt(THEME, 0)
    fun setTheme(theme: Int) {
        sharedPreferences.edit { putInt(THEME, theme) }
    }

    fun getLanguage(): String = sharedPreferences.getString(LANGUAGE, "en") ?: "en"
    fun setLanguage(language: String) {
        sharedPreferences.edit { putString(LANGUAGE, language) }
    }

    /**
     * Puts every setting back to its factory default.
     *
     * Two things deliberately survive, because neither is a *setting* the user chose and losing
     * either would be a defect rather than a reset:
     *
     *  - **The Pro purchase.** A receipt is not a preference. Billing does re-sync from Play on
     *    the next launch, but clearing it would drop the user into an ad-supported app they have
     *    already paid to be rid of for however long that takes — and offline, indefinitely.
     *  - **The first-install timestamp.** It only gates the post-install ad grace period, so
     *    clearing it would hand out a fresh two ad-free hours for every tap of Reset.
     *
     * Everything else goes, first-launch flag included: the walkthrough is part of what a factory
     * state looks like.
     */
    fun resetAll() {
        val keptPro = isProFeatureActivated()
        val keptInstallTime = getFirstInstallTimeMillis()
        sharedPreferences.edit {
            clear()
            putBoolean(PRO_FEATURE_ACTIVATION, keptPro)
            if (keptInstallTime > 0L) putLong(FIRST_INSTALL_TIME, keptInstallTime)
        }
    }
}