package com.newagedevs.gesturevolume.data.local

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * The expanding slider: what it controls, how it looks, and how hard it buzzes.
 *
 * The slider is the payload of a long horizontal swipe on the bar — the bar stretches into a
 * track and the same finger that opened it keeps setting the value. Everything about it is a
 * setting because the gesture is worth almost nothing if it adjusts the wrong quantity, and
 * because a slider that buzzes at every step is delightful to some people and intolerable to
 * others.
 *
 * The identifiers are a persistence format, like [com.newagedevs.gesturevolume.utils.HandlerActions]:
 * written verbatim into preferences, never renamed.
 */
class QuickSliderStore(private val prefs: SharedPreferences) {

    companion object {
        /** Which swipe opens the slider. */
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

        const val DEFAULT_LENGTH = 220f
        const val DEFAULT_THICKNESS = 44f
        /** Half the thickness: a track with fully round ends, matching the bar's pill shape. */
        const val DEFAULT_CORNER = 22f
        const val DEFAULT_TRACK_COLOR = 0xFF1C1C20.toInt()
        const val DEFAULT_FILL_COLOR = 0xFFFFFFFF.toInt()
    }

    /**
     * Which long swipe opens the slider.
     *
     * Defaults to inward — the stroke that starts at the edge and pulls toward the middle of the
     * screen, which is the one a thumb can complete without repositioning the hand. Outward from a
     * bar already flush to the edge has almost no travel before the finger leaves the screen, so it
     * is offered but not chosen for anyone.
     */
    fun getOpenWith(): String {
        val stored = prefs.getString(OPEN_WITH, OPEN_IN) ?: OPEN_IN
        return if (stored in ALL_OPENERS) stored else OPEN_IN
    }

    fun setOpenWith(value: String) = prefs.edit { putString(OPEN_WITH, value) }

    /** Whether a long swipe in this direction opens the slider. */
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

    fun getThicknessDp(): Float = prefs.getFloat(THICKNESS, DEFAULT_THICKNESS).coerceIn(24f, 72f)
    fun setThicknessDp(value: Float) = prefs.edit { putFloat(THICKNESS, value.coerceIn(24f, 72f)) }

    fun getCornerDp(): Float = prefs.getFloat(CORNER, DEFAULT_CORNER).coerceIn(0f, 40f)
    fun setCornerDp(value: Float) = prefs.edit { putFloat(CORNER, value.coerceIn(0f, 40f)) }

    fun getTrackColor(): Int = prefs.getInt(TRACK_COLOR, DEFAULT_TRACK_COLOR)
    fun setTrackColor(value: Int) = prefs.edit { putInt(TRACK_COLOR, value) }

    fun getFillColor(): Int = prefs.getInt(FILL_COLOR, DEFAULT_FILL_COLOR)
    fun setFillColor(value: Int) = prefs.edit { putInt(FILL_COLOR, value) }

    fun getHaptic(): String {
        val stored = prefs.getString(HAPTIC, HAPTIC_LIGHT) ?: HAPTIC_LIGHT
        return if (stored in ALL_HAPTICS) stored else HAPTIC_LIGHT
    }

    fun setHaptic(value: String) = prefs.edit { putString(HAPTIC, value) }

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
