package com.newagedevs.gesturevolume.data.local

import android.content.SharedPreferences
import com.newagedevs.gesturevolume.utils.ButtonBump

/**
 * The button animation's settings: whether it plays, which style, where the buttons are, and how it
 * looks. Same preferences file as everything else, its own object, like [QuickSliderStore].
 *
 * Every getter clamps or sanitises on read, for the reason the rest of the file gives: `allowBackup`
 * means a value written by another build can arrive from a cloud restore.
 */
class ButtonBumpStore(private val prefs: SharedPreferences) {

    companion object {
        const val SIDE_LEFT = "left"
        const val SIDE_RIGHT = "right"

        const val HAPTIC_OFF = "off"
        const val HAPTIC_LIGHT = "light"
        const val HAPTIC_STRONG = "strong"
        val HAPTICS: List<String> = listOf(HAPTIC_OFF, HAPTIC_LIGHT, HAPTIC_STRONG)

        /**
         * Where most phones keep their volume buttons: on the right, about a third of the way down.
         * The screen offers to line it up with the user's own.
         */
        const val DEFAULT_SIDE = SIDE_RIGHT
        const val DEFAULT_POSITION = 0.3f

        const val MIN_LENGTH = 80f
        const val MAX_LENGTH = 300f
        const val DEFAULT_LENGTH = 170f

        const val MIN_DEPTH = 10f
        const val MAX_DEPTH = 44f
        const val DEFAULT_DEPTH = 20f

        const val MIN_HOLD_MS = 500
        const val MAX_HOLD_MS = 4000
        const val DEFAULT_HOLD_MS = 1400

        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2f

        const val DEFAULT_COLOR = 0xFF000000.toInt()
        const val DEFAULT_ACCENT = 0xFFFFFFFF.toInt()

        /** The colours the bump's body is offered in: the glass's black first. */
        val BODY_COLORS: List<Int> = listOf(
            0xFF000000.toInt(), 0xFF1C1C22.toInt(), 0xFF0B1A3A.toInt(), 0xFF2A0F3D.toInt(),
            0xFF06302A.toInt(), 0xFF3A0A12.toInt(), 0xFFF2F2F5.toInt(),
        )

        /** The colours the level is offered in. */
        val ACCENT_COLORS: List<Int> = listOf(
            0xFFFFFFFF.toInt(), 0xFFFFD60A.toInt(), 0xFF30D158.toInt(), 0xFF64D2FF.toInt(),
            0xFFBF5AF2.toInt(), 0xFFFF375F.toInt(), 0xFFFF9F0A.toInt(),
        )

        private const val ENABLED = "bumpEnabled"
        private const val STYLE = "bumpStyle"
        private const val SIDE = "bumpSide"
        private const val POSITION = "bumpPosition"
        private const val LENGTH = "bumpLengthDp"
        private const val DEPTH = "bumpDepthDp"
        private const val COLOR = "bumpColor"
        private const val ACCENT = "bumpAccent"
        private const val SHOW_LEVEL = "bumpShowLevel"
        private const val SHOW_PERCENT = "bumpShowPercent"
        private const val HAPTIC = "bumpHaptic"
        private const val HOLD_MS = "bumpHoldMs"
        private const val SPEED = "bumpSpeed"
    }

    /** Everything the overlay and the previews draw from, read in one go. */
    data class Settings(
        val style: String = ButtonBump.DEFAULT_STYLE,
        val left: Boolean = DEFAULT_SIDE == SIDE_LEFT,
        val position: Float = DEFAULT_POSITION,
        val lengthDp: Float = DEFAULT_LENGTH,
        val depthDp: Float = DEFAULT_DEPTH,
        val color: Int = DEFAULT_COLOR,
        val accent: Int = DEFAULT_ACCENT,
        val showLevel: Boolean = true,
        val showPercent: Boolean = false,
        val haptic: String = HAPTIC_LIGHT,
        val holdMs: Int = DEFAULT_HOLD_MS,
        val speed: Float = 1f,
    )

    /**
     * The settings as they stand, with the style the user may actually have: a Pro style without
     * Pro plays as the default. See [ButtonBump.effectiveStyle].
     */
    fun settings(pro: Boolean): Settings = Settings(
        style = ButtonBump.effectiveStyle(getStyle(), pro),
        left = getSide() == SIDE_LEFT,
        position = getPosition(),
        lengthDp = getLengthDp(),
        depthDp = getDepthDp(),
        color = getColor(),
        accent = getAccent(),
        showLevel = getShowLevel(),
        showPercent = getShowPercent(),
        haptic = getHaptic(),
        holdMs = getHoldMs(),
        speed = getSpeed(),
    )

    /** Whether the volume buttons play the animation. Off until the user turns it on. */
    fun isEnabled(): Boolean = prefs.getBoolean(ENABLED, false)
    fun setEnabled(value: Boolean) = prefs.edit().putBoolean(ENABLED, value).apply()

    /** The style chosen, which may be a Pro one: see [settings] for the one that plays. */
    fun getStyle(): String = ButtonBump.sanitizeStyle(prefs.getString(STYLE, ButtonBump.DEFAULT_STYLE))
    fun setStyle(value: String) = prefs.edit().putString(STYLE, ButtonBump.sanitizeStyle(value)).apply()

    /** Which side of the phone, held upright, the volume buttons are on. */
    fun getSide(): String = when (prefs.getString(SIDE, DEFAULT_SIDE)) {
        SIDE_LEFT -> SIDE_LEFT
        else -> SIDE_RIGHT
    }
    fun setSide(value: String) = prefs.edit().putString(SIDE, if (value == SIDE_LEFT) SIDE_LEFT else SIDE_RIGHT).apply()

    /** Where the middle of the buttons is along that side, 0 at the top of the phone and 1 at the bottom. */
    fun getPosition(): Float = prefs.getFloat(POSITION, DEFAULT_POSITION).coerceIn(0f, 1f)
    fun setPosition(value: Float) = prefs.edit().putFloat(POSITION, value.coerceIn(0f, 1f)).apply()

    /** How long the bump is along the edge, in dp. */
    fun getLengthDp(): Float = prefs.getFloat(LENGTH, DEFAULT_LENGTH).coerceIn(MIN_LENGTH, MAX_LENGTH)
    fun setLengthDp(value: Float) = prefs.edit().putFloat(LENGTH, value.coerceIn(MIN_LENGTH, MAX_LENGTH)).apply()

    /** How far it stands into the screen, in dp. */
    fun getDepthDp(): Float = prefs.getFloat(DEPTH, DEFAULT_DEPTH).coerceIn(MIN_DEPTH, MAX_DEPTH)
    fun setDepthDp(value: Float) = prefs.edit().putFloat(DEPTH, value.coerceIn(MIN_DEPTH, MAX_DEPTH)).apply()

    fun getColor(): Int = prefs.getInt(COLOR, DEFAULT_COLOR)
    fun setColor(value: Int) = prefs.edit().putInt(COLOR, value).apply()

    fun getAccent(): Int = prefs.getInt(ACCENT, DEFAULT_ACCENT)
    fun setAccent(value: Int) = prefs.edit().putInt(ACCENT, value).apply()

    /** Whether the level is drawn inside the bump. */
    fun getShowLevel(): Boolean = prefs.getBoolean(SHOW_LEVEL, true)
    fun setShowLevel(value: Boolean) = prefs.edit().putBoolean(SHOW_LEVEL, value).apply()

    /** Whether the level is written beside it as a number too. */
    fun getShowPercent(): Boolean = prefs.getBoolean(SHOW_PERCENT, false)
    fun setShowPercent(value: Boolean) = prefs.edit().putBoolean(SHOW_PERCENT, value).apply()

    fun getHaptic(): String = prefs.getString(HAPTIC, HAPTIC_LIGHT).takeIf { it in HAPTICS } ?: HAPTIC_LIGHT
    fun setHaptic(value: String) = prefs.edit().putString(HAPTIC, if (value in HAPTICS) value else HAPTIC_LIGHT).apply()

    /** How long it stays after the last press, in ms. */
    fun getHoldMs(): Int = prefs.getInt(HOLD_MS, DEFAULT_HOLD_MS).coerceIn(MIN_HOLD_MS, MAX_HOLD_MS)
    fun setHoldMs(value: Int) = prefs.edit().putInt(HOLD_MS, value.coerceIn(MIN_HOLD_MS, MAX_HOLD_MS)).apply()

    /** How fast it moves, as a multiple of each style's own pace. */
    fun getSpeed(): Float = prefs.getFloat(SPEED, 1f).coerceIn(MIN_SPEED, MAX_SPEED)
    fun setSpeed(value: Float) = prefs.edit().putFloat(SPEED, value.coerceIn(MIN_SPEED, MAX_SPEED)).apply()
}
