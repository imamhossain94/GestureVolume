package com.newagedevs.gesturevolume.utils

import android.content.Context
import androidx.annotation.DrawableRes
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.QuickSliderStore

/**
 * The icons the Quick panel can wear, and how a stored choice turns back into a drawable.
 *
 * Shared by the panel and its settings screen, so the preview and the real thing cannot disagree
 * about what "Automatic" means for the control that is picked.
 */
object QuickSliderIcons {

    /** What the picker offers, in order, each with the label shown under it. */
    val CHOICES: List<Pair<Int, Int>> = listOf(
        R.drawable.ic_vol_increase to R.string.icon_increase,
        R.drawable.ic_vol_decrease to R.string.icon_decrease,
        R.drawable.ic_vol_plus to R.string.icon_boost,
        R.drawable.ic_vol_minus to R.string.icon_reduce,
        R.drawable.ic_mute to R.string.action_mute,
        R.drawable.ic_music_ui to R.string.icon_music,
        R.drawable.ic_brightness_up to R.string.icon_sun,
        R.drawable.ic_brightness_down to R.string.icon_dim,
        R.drawable.ic_star to R.string.icon_star,
        R.drawable.ic_crown_2 to R.string.icon_crown,
        R.drawable.ic_power to R.string.icon_power,
        R.drawable.ic_lock to R.string.icon_lock,
        R.drawable.ic_check to R.string.icon_check,
        R.drawable.ic_color_palette to R.string.icon_palette,
    )

    /** The icon [QuickSliderStore.ICON_AUTO] stands for: what the panel is adjusting. */
    @DrawableRes
    fun automatic(target: String): Int =
        if (target == QuickSliderStore.TARGET_BRIGHTNESS) R.drawable.ic_brightness_up else R.drawable.ic_vol_increase

    /** The glyphs that say "volume". A brightness panel wearing one is saying the wrong thing. */
    private val VOLUME_GLYPHS = setOf(
        R.drawable.ic_vol_increase, R.drawable.ic_vol_decrease, R.drawable.ic_vol_plus,
        R.drawable.ic_vol_minus, R.drawable.ic_mute, R.drawable.ic_music_ui,
    )

    /** The glyphs that say "brightness", for the same mistake the other way round. */
    private val BRIGHTNESS_GLYPHS = setOf(R.drawable.ic_brightness_up, R.drawable.ic_brightness_down)

    /**
     * The drawable for a stored choice, or [automatic]'s for one this build no longer has.
     *
     * Also [automatic]'s for a choice that names the *other* control. The icon is picked once, but
     * the panel is opened on brightness and on volume alike — the volume keys open it on media
     * whatever it is set to, and switching the target keeps the icon — so a speaker picked for a
     * volume panel was left on a brightness one, which then showed no sun at all.
     */
    @DrawableRes
    fun resolve(context: Context, name: String, target: String): Int {
        if (name == QuickSliderStore.ICON_AUTO) return automatic(target)
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (id == 0) return automatic(target)
        val brightness = target == QuickSliderStore.TARGET_BRIGHTNESS
        if (brightness && id in VOLUME_GLYPHS) return automatic(target)
        if (!brightness && id in BRIGHTNESS_GLYPHS) return automatic(target)
        return id
    }

    /** The name a picked drawable is stored under. */
    fun nameOf(context: Context, @DrawableRes res: Int): String =
        runCatching { context.resources.getResourceEntryName(res) }.getOrDefault(QuickSliderStore.ICON_AUTO)
}
