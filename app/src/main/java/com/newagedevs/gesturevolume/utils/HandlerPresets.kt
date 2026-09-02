package com.newagedevs.gesturevolume.utils

import android.view.Gravity
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.newagedevs.gesturevolume.R

/**
 * The five appearance presets, defined once.
 *
 * These used to live as a `when (presetId)` block inside the appearance screen's LaunchedEffect.
 * Moving them here gives the screen one place to read from, and keeps the identifiers that select
 * a preset separate from the names shown for it.
 *
 * The bar colours are taken from what the preset cards on the main screen preview, so choosing a
 * preset produces the handler the card showed. Only the fill colour and its alpha are shared;
 * width, corner radius, stroke and icon stay tuned for the handler itself, since the card's
 * swatch is a small decorative strip rather than a scale drawing of the bar.
 */
object HandlerPresets {

    /**
     * The two colours the preset cards preview with, as literals.
     *
     * These are `MaterialTheme.colorScheme.primary` and `onSurface` from the app's light palette
     * (see ui/theme/Color.kt). Copied rather than read from the theme because the handler is not
     * inside the app: it is drawn over whatever is on screen, by a Service with no Compose theme
     * to resolve against, and it must keep the same appearance whether or not the app is running
     * or which theme the app is set to.
     *
     * Alphas below are the cards' 0..1 preview values scaled to the 0..255 the handler stores:
     * 0.5 -> 128, 0.4 -> 102, 0.85 -> 217, 0.7 -> 179, 0.1 -> 26.
     */
    private val PREVIEW_PRIMARY = Color(0xFF4F46E5)
    private val PREVIEW_ON_SURFACE = Color(0xFF1F2937)

    data class Preset(
        val id: String,
        val nameRes: Int,
        val subtitleRes: Int,
        val gravity: Int,
        val width: Float,
        val height: Float,
        val bgColor: Color,
        val bgAlpha: Int,
        val strokeColor: Color,
        val strokeWidth: Float,
        val strokeAlpha: Int,
        val cornerRadius: Float,
        @param:DrawableRes val iconRes: Int,
        val iconSize: Float,
        val iconColor: Color,
        val showIcon: Boolean,
        val vibrate: Boolean,
        val edgeMargin: Float,
        val positionFraction: Float
    )

    val ALL: List<Preset> = listOf(
        Preset(
            id = "Default",
            nameRes = R.string.preset_default_title,
            subtitleRes = R.string.preset_default_subtitle,
            gravity = Gravity.END,
            width = 30f, height = 100f,
            bgColor = PREVIEW_PRIMARY, bgAlpha = 128,
            strokeColor = Color.White, strokeWidth = 1f, strokeAlpha = 200,
            cornerRadius = 15f,
            iconRes = R.drawable.ic_vol_increase, iconSize = 18f, iconColor = Color.White,
            showIcon = false, vibrate = false, edgeMargin = 0f, positionFraction = 0.12f
        ),
        Preset(
            id = "Minimal",
            nameRes = R.string.preset_minimal_title,
            subtitleRes = R.string.preset_minimal_subtitle,
            gravity = Gravity.END,
            width = 10f, height = 100f,
            bgColor = PREVIEW_ON_SURFACE, bgAlpha = 102,
            strokeColor = Color.White, strokeWidth = 1f, strokeAlpha = 200,
            cornerRadius = 5f,
            iconRes = R.drawable.ic_vol_increase, iconSize = 18f, iconColor = Color.White,
            showIcon = false, vibrate = false, edgeMargin = 0f, positionFraction = 0.12f
        ),
        Preset(
            id = "Bold",
            nameRes = R.string.preset_bold_title,
            subtitleRes = R.string.preset_bold_subtitle,
            gravity = Gravity.END,
            width = 40f, height = 100f,
            bgColor = PREVIEW_PRIMARY, bgAlpha = 217,
            strokeColor = Color.White, strokeWidth = 1f, strokeAlpha = 255,
            cornerRadius = 15f,
            iconRes = R.drawable.ic_move, iconSize = 32f, iconColor = Color.White,
            showIcon = true, vibrate = true, edgeMargin = 0f, positionFraction = 0.12f
        ),
        Preset(
            id = "Night",
            nameRes = R.string.preset_night_title,
            subtitleRes = R.string.preset_night_subtitle,
            gravity = Gravity.END,
            width = 30f, height = 100f,
            bgColor = PREVIEW_ON_SURFACE, bgAlpha = 179,
            strokeColor = Color(0xFF374151), strokeWidth = 1f, strokeAlpha = 200,
            cornerRadius = 15f,
            iconRes = R.drawable.ic_vol_increase, iconSize = 22f, iconColor = Color(0xFF9CA3AF),
            showIcon = true, vibrate = true, edgeMargin = 0f, positionFraction = 0.12f
        ),
        Preset(
            id = "Ghost",
            nameRes = R.string.preset_ghost_title,
            subtitleRes = R.string.preset_ghost_subtitle,
            gravity = Gravity.END,
            width = 20f, height = 100f,
            bgColor = PREVIEW_ON_SURFACE, bgAlpha = 26,
            strokeColor = Color.White, strokeWidth = 0.5f, strokeAlpha = 5,
            cornerRadius = 12f,
            iconRes = R.drawable.ic_vol_increase, iconSize = 16f, iconColor = Color.White,
            showIcon = false, vibrate = false, edgeMargin = 0f, positionFraction = 0.12f
        )
    )

    fun byId(id: String?): Preset? = ALL.firstOrNull { it.id == id }

    /**
     * The out-of-the-box handler.
     *
     * [com.newagedevs.gesturevolume.data.local.SharedPref] falls back to these values for every
     * unset appearance preference, so a fresh install already *is* the Default preset rather than
     * merely resembling it, and the appearance screen opens pre-populated with it.
     */
    val DEFAULT: Preset = ALL.first { it.id == "Default" }
}
