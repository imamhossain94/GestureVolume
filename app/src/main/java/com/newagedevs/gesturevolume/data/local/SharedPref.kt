package com.newagedevs.gesturevolume.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import com.newagedevs.gesturevolume.R
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
        const val HANDLER_ICON_RES = "handlerIconRes"
        const val HANDLER_ICON_NAME = "handlerIconName"
        const val HANDLER_ICON_SIZE = "handlerIconSize"
        const val HANDLER_ICON_COLOR = "handlerIconColor"
        const val HANDLER_SHOW_ICON = "handlerShowIcon"
        const val HANDLER_VIBRATE_ON_CLICK = "handlerVibrateOnClick"
        const val HANDLER_LOCK_POSITION = "handlerLockPosition"
        const val HANDLER_POSITION_FRACTION = "handlerPositionFraction"
        const val HANDLER_EDGE_MARGIN = "handlerEdgeMarginDp"
        const val BRIGHTNESS_AUTO_WAS_ON = "brightnessAutoWasOn"
        const val LEGACY_TRANSLATION_Y_DEFAULT = 260f
        const val DEFAULT_POSITION_FRACTION = 0.5f
        const val APP_LAUNCH_COUNT = "appLaunchCount"
        const val HAS_SHOWN_REVIEW = "hasShownReview"
        const val APP_OPEN_AD_PAUSED = "appOpenAdPaused"

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
        sharedPreferences.getString(HANDLER_POSITION, "Right") ?: "Right"

    fun setHandlerPosition(value: String) {
        sharedPreferences.edit { putString(HANDLER_POSITION, value) }
    }

    // Handler color
    fun getHandlerColor(): Int =
        sharedPreferences.getInt(HANDLER_COLOR, "#FFFFFF".toColorInt())

    fun setHandlerColor(value: Int) {
        sharedPreferences.edit { putInt(HANDLER_COLOR, value) }
    }

    // Handler width in dp
    fun getHandlerWidthDp(): Float =
        sharedPreferences.getFloat(HANDLER_WIDTH + "_dp", 30f)

    fun setHandlerWidthDp(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_WIDTH + "_dp", value) }
    }

    // Handler height in dp
    fun getHandlerHeightDp(): Float =
        sharedPreferences.getFloat(HANDLER_HEIGHT, 100f)

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
        sharedPreferences.getFloat(HANDLER_EDGE_MARGIN, 0f).coerceIn(0f, 48f)

    fun setHandlerEdgeMarginDp(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_EDGE_MARGIN, value.coerceIn(0f, 48f)) }
    }

    /** True when *we* turned adaptive brightness off, so we know it is ours to hand back. */
    fun getBrightnessAutoWasOn(): Boolean =
        sharedPreferences.getBoolean(BRIGHTNESS_AUTO_WAS_ON, false)

    fun setBrightnessAutoWasOn(value: Boolean) {
        sharedPreferences.edit { putBoolean(BRIGHTNESS_AUTO_WAS_ON, value) }
    }

    // Tap actions
    fun getHandlerSingleTapAction(): String =
        sharedPreferences.getString(HANDLER_SINGLE_TAP, "Open volume UI")
            ?: "Open volume UI"

    fun setHandlerSingleTapAction(value: String) {
        sharedPreferences.edit { putString(HANDLER_SINGLE_TAP, value) }
    }

    fun getHandlerDoubleTapAction(): String =
        sharedPreferences.getString(HANDLER_DOUBLE_TAP, "None") ?: "None"

    fun setHandlerDoubleTapAction(value: String) {
        sharedPreferences.edit { putString(HANDLER_DOUBLE_TAP, value) }
    }

    fun getHandlerLongTapAction(): String =
        sharedPreferences.getString(HANDLER_LONG_TAP, "None") ?: "None"

    fun setHandlerLongTapAction(value: String) {
        sharedPreferences.edit { putString(HANDLER_LONG_TAP, value) }
    }

    // Swipe actions
    fun getHandlerSwipeUpAction(): String =
        sharedPreferences.getString(HANDLER_SWIPE_UP, "Increase volume and show UI")
            ?: "Increase volume and show UI"

    fun setHandlerSwipeUpAction(value: String) {
        sharedPreferences.edit { putString(HANDLER_SWIPE_UP, value) }
    }

    // The default said "Increase..." for the swipe-DOWN slot, which showed the wrong row and the
    // wrong icon in the picker. Correcting it is behaviour-neutral because the direction of a swipe
    // comes from the gesture's sign, never from this string.
    fun getHandlerSwipeDownAction(): String =
        sharedPreferences.getString(HANDLER_SWIPE_DOWN, "Decrease volume and show UI")
            ?: "Decrease volume and show UI"

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
     * Check if interstitial ad should be shown
     * Returns true if:
     * 1. 90 seconds have passed since last interstitial ad
     * 2. 90 seconds have passed since any ad (app open or interstitial)
     */
    fun shouldShowInterstitialAd(): Boolean {
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
        sharedPreferences.getInt(HANDLER_BACKGROUND_ALPHA, 50)

    fun setHandlerBackgroundAlpha(value: Int) {
        sharedPreferences.edit { putInt(HANDLER_BACKGROUND_ALPHA, value) }
    }

    // Stroke settings
    fun getHandlerStrokeColor(): Int =
        sharedPreferences.getInt(HANDLER_STROKE_COLOR, 0xFFFFFFFF.toInt())

    fun setHandlerStrokeColor(value: Int) {
        sharedPreferences.edit { putInt(HANDLER_STROKE_COLOR, value) }
    }

    fun getHandlerStrokeWidth(): Float =
        sharedPreferences.getFloat(HANDLER_STROKE_WIDTH, 1f)

    fun setHandlerStrokeWidth(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_STROKE_WIDTH, value) }
    }

    fun getHandlerStrokeAlpha(): Int =
        sharedPreferences.getInt(HANDLER_STROKE_ALPHA, 200)

    fun setHandlerStrokeAlpha(value: Int) {
        sharedPreferences.edit { putInt(HANDLER_STROKE_ALPHA, value) }
    }

    // Corner radius settings
    fun getHandlerCornerRadiusTL(): Float =
        sharedPreferences.getFloat(HANDLER_CORNER_RADIUS_TL, 15f)

    fun setHandlerCornerRadiusTL(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_CORNER_RADIUS_TL, value) }
    }

    fun getHandlerCornerRadiusTR(): Float =
        sharedPreferences.getFloat(HANDLER_CORNER_RADIUS_TR, 15f)

    fun setHandlerCornerRadiusTR(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_CORNER_RADIUS_TR, value) }
    }

    fun getHandlerCornerRadiusBL(): Float =
        sharedPreferences.getFloat(HANDLER_CORNER_RADIUS_BL, 15f)

    fun setHandlerCornerRadiusBL(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_CORNER_RADIUS_BL, value) }
    }

    fun getHandlerCornerRadiusBR(): Float =
        sharedPreferences.getFloat(HANDLER_CORNER_RADIUS_BR, 15f)

    fun setHandlerCornerRadiusBR(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_CORNER_RADIUS_BR, value) }
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
            sharedPreferences.getInt(HANDLER_ICON_RES, R.drawable.ic_vol_increase)
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
        sharedPreferences.getFloat(HANDLER_ICON_SIZE, 16f)

    fun setHandlerIconSize(value: Float) {
        sharedPreferences.edit { putFloat(HANDLER_ICON_SIZE, value) }
    }

    fun getHandlerIconColor(): Int =
        sharedPreferences.getInt(HANDLER_ICON_COLOR, 0xFFFFFFFF.toInt())

    fun setHandlerIconColor(value: Int) {
        sharedPreferences.edit { putInt(HANDLER_ICON_COLOR, value) }
    }

    fun getHandlerShowIcon(): Boolean =
        sharedPreferences.getBoolean(HANDLER_SHOW_ICON, true)

    fun setHandlerShowIcon(value: Boolean) {
        sharedPreferences.edit { putBoolean(HANDLER_SHOW_ICON, value) }
    }

    // Behavior settings
    fun getHandlerVibrateOnClick(): Boolean =
        sharedPreferences.getBoolean(HANDLER_VIBRATE_ON_CLICK, true)

    fun setHandlerVibrateOnClick(value: Boolean) {
        sharedPreferences.edit { putBoolean(HANDLER_VIBRATE_ON_CLICK, value) }
    }

    fun getHandlerLockPosition(): Boolean =
        sharedPreferences.getBoolean(HANDLER_LOCK_POSITION, true)

    fun setHandlerLockPosition(value: Boolean) {
        sharedPreferences.edit { putBoolean(HANDLER_LOCK_POSITION, value) }
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
}