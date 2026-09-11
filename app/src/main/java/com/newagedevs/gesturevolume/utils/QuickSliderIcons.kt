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

    /** The drawable for a stored choice, or [automatic]'s for one this build no longer has. */
    @DrawableRes
    fun resolve(context: Context, name: String, target: String): Int {
        if (name == QuickSliderStore.ICON_AUTO) return automatic(target)
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        return if (id != 0) id else automatic(target)
    }

    /** The name a picked drawable is stored under. */
    fun nameOf(context: Context, @DrawableRes res: Int): String =
        runCatching { context.resources.getResourceEntryName(res) }.getOrDefault(QuickSliderStore.ICON_AUTO)
}
