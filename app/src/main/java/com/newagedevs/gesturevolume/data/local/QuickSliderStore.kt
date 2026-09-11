package com.newagedevs.gesturevolume.data.local

import android.content.SharedPreferences
import androidx.core.content.edit
import com.newagedevs.gesturevolume.utils.SliderFill

/**
 * The Quick panel: what it controls, how it looks, and how hard it buzzes.
 *
 * The panel is opened by `HandlerActions.OPEN_QUICK_SLIDER` from whichever gesture slot the user
 * put it in, and it then takes its own touches until it closes. It used to be the payload of a
 * long horizontal swipe, sharing that stroke with the Deck; the `sliderOpenWith` key that chose
 * which direction opened it is gone with the gesture, and is deliberately left unread rather than
 * migrated — there is no direction left for it to name.
 *
 * Everything else about it is a setting because the panel is worth almost nothing if it adjusts
 * the wrong quantity, and because one that buzzes at every step is delightful to some people and
 * intolerable to others.
 *
 * The identifiers are a persistence format, like [com.newagedevs.gesturevolume.utils.HandlerActions]:
 * written verbatim into preferences, never renamed.
 */
class QuickSliderStore(private val prefs: SharedPreferences) {

    companion object {
        /**
         * Which long swipe opens the panel, on top of whichever gesture slot it is bound to.
         *
         * The panel is an action first — it can go on a tap, a long press or the menu — and this
         * is the extra opener for people who want it on the stroke that made the feature. It is a
         * setting rather than a fixed behaviour because it is the one opener that shares a
         * direction with the Deck, and anyone who finds that ambiguous can switch it off here
         * without losing the panel.
         */
        const val OPEN_OFF = "off"
        const val OPEN_IN = "in"
        const val OPEN_OUT = "out"
        const val OPEN_BOTH = "both"
        val ALL_OPENERS = listOf(OPEN_IN, OPEN_OUT, OPEN_BOTH, OPEN_OFF)

        /** What the slider drives. */
        const val TARGET_BRIGHTNESS = "brightness"
        const val TARGET_MEDIA = "media"
        const val TARGET_RING = "ring"
        const val TARGET_ALARM = "alarm"
        val ALL_TARGETS = listOf(TARGET_BRIGHTNESS, TARGET_MEDIA, TARGET_RING, TARGET_ALARM)

        /** How hard each step buzzes. */
        const val HAPTIC_OFF = "off"
        const val HAPTIC_LIGHT = "light"
        const val HAPTIC_MEDIUM = "medium"
        const val HAPTIC_STRONG = "strong"
        val ALL_HAPTICS = listOf(HAPTIC_OFF, HAPTIC_LIGHT, HAPTIC_MEDIUM, HAPTIC_STRONG)

        private const val OPEN_WITH = "sliderOpenWith"
        private const val TARGET = "sliderTarget"
        private const val LENGTH = "sliderLengthDp"
        private const val THICKNESS = "sliderThicknessDp"
        private const val CORNER = "sliderCornerDp"
        private const val TRACK_COLOR = "sliderTrackColor"
        private const val FILL_COLOR = "sliderFillColor"
        private const val HAPTIC = "sliderHaptic"
        private const val SHOW_VALUE = "sliderShowValue"
        private const val SHOW_ICON = "sliderShowIcon"
        private const val AUTO_BRIGHTNESS_OFF = "sliderDisableAutoBrightness"
        private const val FILL_STYLE = "sliderFillStyle"

        const val DEFAULT_LENGTH = 220f
        /** Mirrors `OverlayController.PANEL_MIN_THICKNESS_DP`. See [getThicknessDp]. */
        const val MIN_THICKNESS = 48f
        const val DEFAULT_THICKNESS = 52f
        /** Half the thickness: a track with fully round ends, matching the bar's pill shape. */
        const val DEFAULT_CORNER = 22f
        const val DEFAULT_TRACK_COLOR = 0xFF1C1C20.toInt()
        const val DEFAULT_FILL_COLOR = 0xFFFFFFFF.toInt()
    }

    /**
     * Which long swipe opens the panel, if any. **Off unless the user asks for it.**
     *
     * The horizontal swipe belongs to the Deck. Sharing it meant the two were one stroke told
     * apart by a distance, and no arrangement of thresholds makes a distance visible to a thumb —
     * so the Deck swipe kept turning into the panel and the panel kept refusing to open when it
     * was wanted. The panel's home is the vertical swipe now, where nothing else lives, plus
     * whichever other slot the user binds `OPEN_QUICK_SLIDER` to.
     *
     * The setting survives because the gesture is genuinely nice when it is the only thing on that
     * direction — a bar with the Deck unbound, say. It is simply not something to hand anyone who
     * has not asked.
     */
    fun getOpenWith(): String {
        val stored = prefs.getString(OPEN_WITH, OPEN_OFF) ?: OPEN_OFF
        return if (stored in ALL_OPENERS) stored else OPEN_OFF
    }

    fun setOpenWith(value: String) = prefs.edit { putString(OPEN_WITH, value) }

    /** Whether a long swipe in this direction opens the panel. */
    fun opensOn(inward: Boolean): Boolean = when (getOpenWith()) {
        OPEN_BOTH -> true
        OPEN_IN -> inward
        OPEN_OUT -> !inward
        else -> false
    }

    fun getTarget(): String {
        val stored = prefs.getString(TARGET, TARGET_BRIGHTNESS) ?: TARGET_BRIGHTNESS
        return if (stored in ALL_TARGETS) stored else TARGET_BRIGHTNESS
    }

    fun setTarget(value: String) = prefs.edit { putString(TARGET, value) }

    /** How far the track reaches in from the bar. Also the travel one full sweep costs. */
    fun getLengthDp(): Float = prefs.getFloat(LENGTH, DEFAULT_LENGTH).coerceIn(120f, 320f)
    fun setLengthDp(value: Float) = prefs.edit { putFloat(LENGTH, value.coerceIn(120f, 320f)) }

    /**
     * How wide the open panel is.
     *
     * The floor is 48dp and not the 24dp it used to be, because the panel is floored at 48dp when
     * it is built — see `OverlayController.PANEL_MIN_THICKNESS_DP`, which is there because this is
     * a control a thumb aims at. The store's range and the live floor disagreeing meant the bottom
     * half of the slider moved the preview and did nothing at all to the panel.
     */
    fun getThicknessDp(): Float = prefs.getFloat(THICKNESS, DEFAULT_THICKNESS).coerceIn(MIN_THICKNESS, 72f)
    fun setThicknessDp(value: Float) = prefs.edit { putFloat(THICKNESS, value.coerceIn(MIN_THICKNESS, 72f)) }

    /**
     * The radius the open panel's corners settle at.
     *
     * Its own, rather than the handler's, which is what it used to borrow. The panel still *starts*
     * at the bar's corners — that is what makes the opening read as the bar growing rather than as
     * a second object appearing over it — and travels to this one on the way out. The view was
     * already interpolating each corner from its collapsed value to its expanded one, so the only
     * thing that had changed was that both ends were being handed the same number.
     */
    fun getCornerDp(): Float = prefs.getFloat(CORNER, DEFAULT_CORNER).coerceIn(0f, 40f)
    fun setCornerDp(value: Float) = prefs.edit { putFloat(CORNER, value.coerceIn(0f, 40f)) }

    /**
     * The colour the open panel's track settles at.
     *
     * Like the corner radius, the panel begins at the bar's colour and blends to this one as it
     * grows — `QuickSliderView.refreshBlend` has always done that interpolation; it was simply
     * being given the bar's colour at both ends.
     */
    fun getTrackColor(): Int = prefs.getInt(TRACK_COLOR, DEFAULT_TRACK_COLOR)
    fun setTrackColor(value: Int) = prefs.edit { putInt(TRACK_COLOR, value) }

    fun getFillColor(): Int = prefs.getInt(FILL_COLOR, DEFAULT_FILL_COLOR)
    fun setFillColor(value: Int) = prefs.edit { putInt(FILL_COLOR, value) }

    fun getHaptic(): String {
        val stored = prefs.getString(HAPTIC, HAPTIC_LIGHT) ?: HAPTIC_LIGHT
        return if (stored in ALL_HAPTICS) stored else HAPTIC_LIGHT
    }

    fun setHaptic(value: String) = prefs.edit { putString(HAPTIC, value) }

    /** What the filled portion does while the panel is open. See [SliderFill]. */
    fun getFillStyle(): String = SliderFill.sanitize(prefs.getString(FILL_STYLE, null))
    fun setFillStyle(value: String) = prefs.edit { putString(FILL_STYLE, SliderFill.sanitize(value)) }

    fun getShowValue(): Boolean = prefs.getBoolean(SHOW_VALUE, true)
    fun setShowValue(value: Boolean) = prefs.edit { putBoolean(SHOW_VALUE, value) }

    fun getShowIcon(): Boolean = prefs.getBoolean(SHOW_ICON, true)
    fun setShowIcon(value: Boolean) = prefs.edit { putBoolean(SHOW_ICON, value) }

    /**
     * Whether opening the brightness slider switches adaptive brightness off.
     *
     * On by default, and the app hands it back when the service stops — the light sensor would
     * otherwise overwrite whatever the user set within a second or two, which reads as the
     * slider not working at all.
     */
    fun getDisableAutoBrightness(): Boolean = prefs.getBoolean(AUTO_BRIGHTNESS_OFF, true)
    fun setDisableAutoBrightness(value: Boolean) = prefs.edit { putBoolean(AUTO_BRIGHTNESS_OFF, value) }
}
