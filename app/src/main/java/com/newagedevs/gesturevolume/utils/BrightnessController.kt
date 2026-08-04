package com.newagedevs.gesturevolume.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Screen-brightness control for the floating handler.
 *
 * Uses the system brightness setting rather than
 * [android.view.WindowManager.LayoutParams.screenBrightness]. A per-window override only applies
 * while that window is the topmost one deciding the display brightness, and the handler is a small,
 * mostly-transparent overlay sitting above whatever app the user is actually looking at — so a
 * window override would fight with that app and produce results the user cannot predict. The system
 * setting is what the user means by "brightness", and it persists after the bar is dismissed.
 *
 * The cost is [Settings.System.canWrite], which the user must grant. That is requested at the point
 * of use — when a brightness action is actually selected — never at startup.
 */
class BrightnessController(private val context: Context) {

    private companion object {
        /** Steps across the full range for one sweep of the bar. */
        const val STEPS = 24

        /** Never let the user drive the screen fully black and lose the ability to see the bar. */
        const val ABSOLUTE_MIN = 1
    }

    /** Number of discrete steps, for the gesture detector's step sizing. */
    val stepCount: Int get() = STEPS

    /**
     * The device's own maximum for [Settings.System.SCREEN_BRIGHTNESS]. This is *not* always 255 —
     * devices with finer-grained backlights report values such as 1023 or 4095, and assuming 255
     * on those makes the bar control only the bottom few percent of the range.
     */
    private val maxBrightness: Int by lazy {
        systemInt("config_screenBrightnessSettingMaximum", 255).coerceAtLeast(1)
    }

    private val minBrightness: Int by lazy {
        systemInt("config_screenBrightnessSettingMinimum", 0).coerceAtLeast(ABSOLUTE_MIN)
    }

    // Resources.getSystem() is right here: these are framework configuration integers, not
    // display-dependent dimensions, so the lack of a screen configuration does not matter.
    private fun systemInt(name: String, fallback: Int): Int = try {
        val system = android.content.res.Resources.getSystem()
        val id = system.getIdentifier(name, "integer", "android")
        if (id != 0) system.getInteger(id) else fallback
    } catch (_: Exception) {
        fallback
    }

    fun canWrite(): Boolean = Settings.System.canWrite(context)

    /** The intent that takes the user to the "Modify system settings" toggle for this app. */
    fun writeSettingsIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )

    /** Current brightness as a 0..1 fraction, or `null` if it cannot be read. */
    fun fraction(): Float? {
        val raw = rawBrightness() ?: return null
        val span = (maxBrightness - minBrightness).coerceAtLeast(1)
        return ((raw - minBrightness).toFloat() / span).coerceIn(0f, 1f)
    }

    private fun rawBrightness(): Int? = try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    } catch (_: Settings.SettingNotFoundException) {
        null
    } catch (_: Exception) {
        null
    }

    fun isAutoBrightnessOn(): Boolean = try {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    } catch (_: Exception) {
        false
    }

    /**
     * Adaptive brightness has to go while the user is driving brightness by hand — the light sensor
     * would otherwise overwrite every value we set, within a second or two.
     *
     * @return true if this call is what turned it off, so the caller can offer to hand it back.
     */
    fun disableAutoBrightnessIfNeeded(): Boolean {
        if (!canWrite()) return false
        if (!isAutoBrightnessOn()) return false
        return runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
        }.getOrDefault(false)
    }

    fun setAutoBrightness(enabled: Boolean): Boolean {
        if (!canWrite()) return false
        return runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (enabled) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
        }.getOrDefault(false)
    }

    /**
     * Moves brightness one step.
     *
     * @return the new 0..1 fraction, or `null` when the write failed or nothing changed because the
     *   value is already pinned at an end. A `null` tells the gesture detector to stop banking
     *   travel, so the user does not have to un-swipe before the control responds again.
     */
    fun step(direction: Int): Float? {
        if (!canWrite()) return null
        val current = rawBrightness() ?: return null
        val span = (maxBrightness - minBrightness).coerceAtLeast(1)
        val delta = (span.toFloat() / STEPS).coerceAtLeast(1f)

        val target = (current + direction * delta)
            .toInt()
            .coerceIn(minBrightness, maxBrightness)

        if (target == current) return null

        val written = runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                target
            )
        }.getOrDefault(false)

        // Some OEM ROMs report canWrite() == true and then silently drop the write.
        if (!written) return null

        return ((target - minBrightness).toFloat() / span).coerceIn(0f, 1f)
    }
}
