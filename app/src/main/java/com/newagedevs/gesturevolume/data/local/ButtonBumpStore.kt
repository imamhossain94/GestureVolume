package com.newagedevs.gesturevolume.data.local

import android.content.SharedPreferences
import com.newagedevs.gesturevolume.utils.ButtonBump

/**
 * The button animation's settings: whether it plays, which style, where each physical button is,
 * and how it looks. Same preferences file as everything else, its own object, like [QuickSliderStore].
 *
 * The animation and the Quick slider are two separate things. A press of a button plays the
 * animation, with its own level; the Quick slider is the panel the bar opens, and whether it also
 * answers the press or pauses for the animation is the user's choice, kept with the slider's own
 * settings: see QuickSliderStore.WITH_BUMP_BOTH.
 *
 * Every getter clamps or sanitises on read, for the reason the rest of the file gives: `allowBackup`
 * means a value written by another build can arrive from a cloud restore.
 */
class ButtonBumpStore(private val prefs: SharedPreferences) {

    /**
     * A physical button the glass answers. Each has a place of its own on the frame, because they
     * are not where one another are: volume up above volume down, and the power button wherever the
     * maker put it — often on the other side.
     */
    enum class Button(internal val key: String) {
        VOLUME_UP("Up"),
        VOLUME_DOWN("Down"),
        POWER("Power"),
    }

    /** Where one button is on an upright phone: its side, its middle along that side, its length. */
    data class Place(val left: Boolean, val position: Float, val lengthDp: Float)

    companion object {
        const val SIDE_LEFT = "left"
        const val SIDE_RIGHT = "right"

        const val HAPTIC_OFF = "off"
        const val HAPTIC_LIGHT = "light"
        const val HAPTIC_STRONG = "strong"
        val HAPTICS: List<String> = listOf(HAPTIC_OFF, HAPTIC_LIGHT, HAPTIC_STRONG)

        /**
         * Where most phones keep their buttons, held upright: on the right, the volume rocker about
         * a quarter to a third of the way down and the power button just under it. The screen
         * offers to line each one up with the user's own.
         */
        fun defaultPlace(button: Button): Place = when (button) {
            Button.VOLUME_UP -> Place(left = false, position = 0.27f, lengthDp = 52f)
            Button.VOLUME_DOWN -> Place(left = false, position = 0.36f, lengthDp = 52f)
            Button.POWER -> Place(left = false, position = 0.47f, lengthDp = 48f)
        }

        const val MIN_LENGTH = 24f
        const val MAX_LENGTH = 220f

        const val MIN_DEPTH = 2f
        const val MAX_DEPTH = 44f
        const val DEFAULT_DEPTH = 18f

        const val MIN_HOLD_MS = 500
        const val MAX_HOLD_MS = 4000
        const val DEFAULT_HOLD_MS = 1400

        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2f

        const val DEFAULT_COLOR = 0xFF000000.toInt()
        const val DEFAULT_ACCENT = 0xFFFFFFFF.toInt()

        /** The colours the press is offered in: the glass's black first. */
        val BODY_COLORS: List<Int> = listOf(
            0xFF000000.toInt(), 0xFF1C1C22.toInt(), 0xFF0B1A3A.toInt(), 0xFF2A0F3D.toInt(),
            0xFF06302A.toInt(), 0xFF3A0A12.toInt(), 0xFFF2F2F5.toInt(),
        )

        /** The colours the level and the glows are offered in. */
        val ACCENT_COLORS: List<Int> = listOf(
            0xFFFFFFFF.toInt(), 0xFFFFD60A.toInt(), 0xFF30D158.toInt(), 0xFF64D2FF.toInt(),
            0xFFBF5AF2.toInt(), 0xFFFF375F.toInt(), 0xFFFF9F0A.toInt(),
        )

        private const val ENABLED = "bumpEnabled"
        private const val STYLE = "bumpStyle"
        private const val SIDE_PREFIX = "bumpSide"
        private const val POSITION_PREFIX = "bumpPosition"
        private const val LENGTH_PREFIX = "bumpLengthDp"
        private const val DEPTH = "bumpDepthDp"
        private const val COLOR = "bumpColor"
        private const val ACCENT = "bumpAccent"
        private const val SWEEP = "bumpSweep"
        private const val SOUND = "bumpSound"
        private const val SHOW_LEVEL = "bumpShowLevel"
        private const val SHOW_PERCENT = "bumpShowPercent"
        private const val HAPTIC = "bumpHaptic"
        private const val HOLD_MS = "bumpHoldMs"
        private const val SPEED = "bumpSpeed"
        private const val POWER_MENU = "bumpPowerMenu"
        private const val POWER_WAKE = "bumpPowerWake"
    }

    /**
     * Everything the overlay and the previews draw from, read in one go: the look, and the place of
     * the one button being pressed.
     */
    data class Settings(
        val style: String = ButtonBump.DEFAULT_STYLE,
        val left: Boolean = false,
        val position: Float = 0.3f,
        val lengthDp: Float = 52f,
        val depthDp: Float = DEFAULT_DEPTH,
        val color: Int = DEFAULT_COLOR,
        val accent: Int = DEFAULT_ACCENT,
        val showLevel: Boolean = true,
        val showPercent: Boolean = false,
        val haptic: String = HAPTIC_LIGHT,
        val holdMs: Int = DEFAULT_HOLD_MS,
        val speed: Float = 1f,
        /** How the bump's ends sweep into the glass, 0..1, or below 0 for the style's own. */
        val sweep: Float = -1f,
    )

    /**
     * The settings as they stand, placed at [button]. The power button has no level to show: it
     * moves nothing.
     */
    fun settings(button: Button): Settings {
        val place = getPlace(button)
        return Settings(
            style = getStyle(),
            left = place.left,
            position = place.position,
            lengthDp = place.lengthDp,
            depthDp = getDepthDp(),
            color = getColor(),
            accent = getAccent(),
            showLevel = getShowLevel() && button != Button.POWER,
            showPercent = getShowPercent() && button != Button.POWER,
            haptic = getHaptic(),
            holdMs = getHoldMs(),
            speed = getSpeed(),
            sweep = getSweep(),
        )
    }

    /** Whether the buttons play the animation. Off until the user turns it on. */
    fun isEnabled(): Boolean = prefs.getBoolean(ENABLED, false)
    fun setEnabled(value: Boolean) = prefs.edit().putBoolean(ENABLED, value).apply()

    /** How the glass answers a press. Every style is free; Pro takes the ads away and nothing else. */
    fun getStyle(): String = ButtonBump.sanitizeStyle(prefs.getString(STYLE, ButtonBump.DEFAULT_STYLE))
    fun setStyle(value: String) = prefs.edit().putString(STYLE, ButtonBump.sanitizeStyle(value)).apply()

    // ---- where each button is ------------------------------------------------------------------

    fun getPlace(button: Button): Place {
        val fallback = defaultPlace(button)
        val side = prefs.getString(SIDE_PREFIX + button.key, null)
        return Place(
            left = if (side == null) fallback.left else side == SIDE_LEFT,
            position = prefs.getFloat(POSITION_PREFIX + button.key, fallback.position).coerceIn(0f, 1f),
            lengthDp = prefs.getFloat(LENGTH_PREFIX + button.key, fallback.lengthDp).coerceIn(MIN_LENGTH, MAX_LENGTH),
        )
    }

    fun setSide(button: Button, left: Boolean) =
        prefs.edit().putString(SIDE_PREFIX + button.key, if (left) SIDE_LEFT else SIDE_RIGHT).apply()

    fun setPosition(button: Button, value: Float) =
        prefs.edit().putFloat(POSITION_PREFIX + button.key, value.coerceIn(0f, 1f)).apply()

    fun setLengthDp(button: Button, value: Float) =
        prefs.edit().putFloat(LENGTH_PREFIX + button.key, value.coerceIn(MIN_LENGTH, MAX_LENGTH)).apply()

    /** Every button back where most phones have it. */
    fun resetPlaces() {
        val edit = prefs.edit()
        Button.entries.forEach { b ->
            edit.remove(SIDE_PREFIX + b.key)
            edit.remove(POSITION_PREFIX + b.key)
            edit.remove(LENGTH_PREFIX + b.key)
        }
        edit.apply()
    }

    // ---- what plays ------------------------------------------------------------------------------

    /** Whether holding the power button, when it brings up the power menu, plays the press. */
    fun getPowerMenu(): Boolean = prefs.getBoolean(POWER_MENU, true)
    fun setPowerMenu(value: Boolean) = prefs.edit().putBoolean(POWER_MENU, value).apply()

    /**
     * Whether waking the screen plays the press at the power button. Off by default: the screen
     * also wakes for a notification or a lift, which nothing tells apart from a press.
     */
    fun getPowerWake(): Boolean = prefs.getBoolean(POWER_WAKE, false)
    fun setPowerWake(value: Boolean) = prefs.edit().putBoolean(POWER_WAKE, value).apply()

    // ---- the look ----------------------------------------------------------------------------------

    /**
     * How the bump's ends sweep into the glass, 0..1 — small is a slab with sharp shoulders, 1 a soft
     * hill — or below 0 for each style's own sweep, which is where it starts.
     */
    fun getSweep(): Float = prefs.getFloat(SWEEP, -1f).let { if (it < 0f) -1f else it.coerceIn(0.05f, 1f) }
    fun setSweep(value: Float) = prefs.edit().putFloat(SWEEP, value.coerceIn(0.05f, 1f)).apply()

    /**
     * Whether each press plays its animation's own sound — a splash for the water, a meow for the cat —
     * at the volume being set. In place of the Quick slider's step sound, so a press never makes two.
     */
    fun getSound(): Boolean = prefs.getBoolean(SOUND, true)
    fun setSound(value: Boolean) = prefs.edit().putBoolean(SOUND, value).apply()

    /** How far the press stands into the screen, in dp. */
    fun getDepthDp(): Float = prefs.getFloat(DEPTH, DEFAULT_DEPTH).coerceIn(MIN_DEPTH, MAX_DEPTH)
    fun setDepthDp(value: Float) = prefs.edit().putFloat(DEPTH, value.coerceIn(MIN_DEPTH, MAX_DEPTH)).apply()

    fun getColor(): Int = prefs.getInt(COLOR, DEFAULT_COLOR)
    fun setColor(value: Int) = prefs.edit().putInt(COLOR, value).apply()

    fun getAccent(): Int = prefs.getInt(ACCENT, DEFAULT_ACCENT)
    fun setAccent(value: Int) = prefs.edit().putInt(ACCENT, value).apply()

    /** Whether the level is drawn inside the press, when there is no slider to show it. */
    fun getShowLevel(): Boolean = prefs.getBoolean(SHOW_LEVEL, true)
    fun setShowLevel(value: Boolean) = prefs.edit().putBoolean(SHOW_LEVEL, value).apply()

    /** Whether the level is written beside it as a number too, when there is no slider. */
    fun getShowPercent(): Boolean = prefs.getBoolean(SHOW_PERCENT, false)
    fun setShowPercent(value: Boolean) = prefs.edit().putBoolean(SHOW_PERCENT, value).apply()

    fun getHaptic(): String = prefs.getString(HAPTIC, HAPTIC_LIGHT).takeIf { it in HAPTICS } ?: HAPTIC_LIGHT
    fun setHaptic(value: String) = prefs.edit().putString(HAPTIC, if (value in HAPTICS) value else HAPTIC_LIGHT).apply()

    /** How long the press stays after the last one, in ms. */
    fun getHoldMs(): Int = prefs.getInt(HOLD_MS, DEFAULT_HOLD_MS).coerceIn(MIN_HOLD_MS, MAX_HOLD_MS)
    fun setHoldMs(value: Int) = prefs.edit().putInt(HOLD_MS, value.coerceIn(MIN_HOLD_MS, MAX_HOLD_MS)).apply()

    /** How fast it moves, as a multiple of each style's own pace. */
    fun getSpeed(): Float = prefs.getFloat(SPEED, 1f).coerceIn(MIN_SPEED, MAX_SPEED)
    fun setSpeed(value: Float) = prefs.edit().putFloat(SPEED, value.coerceIn(MIN_SPEED, MAX_SPEED)).apply()
}
