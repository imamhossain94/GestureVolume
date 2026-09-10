package com.newagedevs.gesturevolume.utils

import android.view.Gravity
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.newagedevs.gesturevolume.R

/**
 * The appearance presets, defined once.
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

    /**
     * Where a preset puts the bar, for the few that are a *place* as much as a look.
     *
     * Presets are otherwise appearance only — see [com.newagedevs.gesturevolume.ui.screens.handler_appearance.applyPreset],
     * which deliberately leaves placement alone so that picking "Night" to change the colour does
     * not throw away a bar the user dragged where they wanted it. The exception is a preset whose
     * whole identity is where it sits: a bar across the top beside the camera cutout is not the
     * Default bar in a different colour, and applying it without moving it would produce a wide
     * flat pill stuck to the side, which is nothing anyone asked for.
     */
    data class Placement(
        val gravity: Int,
        val posXFraction: Float,
        val posYFraction: Float,
        val snapToEdge: Boolean
    )

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
        val positionFraction: Float,
        /** Non-null only for a preset that is a place as much as a look. See [Placement]. */
        val placement: Placement? = null
    )

    val ALL: List<Preset> = listOf(
        Preset(
            /**
             * The out-of-the-box handle: a slim black pill, flush to the right edge, centred.
             *
             * These numbers are a deliberate copy of the shape the edge-launcher category has
             * settled on — 10dp of width, a little under a hundred tall, fully opaque black, ends
             * rounded to a half-width radius, sitting at the vertical middle where a thumb rests
             * without reaching. It is the shape that reads as "grab here" while disappearing into
             * a dark app's chrome, which is why every app in this category converges on it.
             *
             * A geometric proportion is not anyone's property, and nothing here is copied from
             * another app's assets or code. The name stays generic for the same reason the Edge
             * and Notch presets do.
             *
             * Changing these values changes what an install with unset appearance preferences
             * looks like, because [com.newagedevs.gesturevolume.data.local.SharedPref] falls back
             * to this preset for each of them. An install that predates the change keeps the old
             * indigo bar: see `SharedPref.pinLegacyAppearanceDefaults`, which writes the previous
             * defaults out explicitly on first run after the update so that only genuinely fresh
             * installs pick up the new shape.
             */
            id = "Default",
            nameRes = R.string.preset_default_title,
            subtitleRes = R.string.preset_default_subtitle,
            gravity = Gravity.END,
            width = 10f, height = 95f,
            bgColor = Color.Black, bgAlpha = 255,
            strokeColor = Color.White, strokeWidth = 0f, strokeAlpha = 200,
            cornerRadius = 5f,
            iconRes = R.drawable.ic_vol_increase, iconSize = 18f, iconColor = Color.White,
            showIcon = false, vibrate = false, edgeMargin = 0f, positionFraction = 0.5f,
            placement = Placement(
                gravity = Gravity.END,
                posXFraction = 1f,
                posYFraction = 0.5f,
                snapToEdge = true
            )
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
            /**
             * The slim edge-handle look: a thin translucent pill, flush to the side.
             *
             * Deliberately NOT named after the OEM whose edge panel it resembles. The shape is not
             * anyone's property, but the brand name is, and a preset label is exactly the kind of
             * incidental trademark use that draws a complaint against a listing.
             *
             * Distinct from Minimal, which is the same 10dp width but a full 100dp tall, dark, and
             * carries a visible stroke. This one is shorter, lighter, and sits lower down the
             * screen where a thumb rests rather than up near the status bar. The corner radius is
             * exactly half the width, which is what makes it a true pill rather than a rounded
             * rectangle.
             *
             * It keeps a faint stroke despite the original having none: a white fill at this alpha
             * disappears entirely against a white app, and a handle you cannot find is not minimal,
             * it is broken.
             */
            id = "Edge",
            nameRes = R.string.preset_edge_title,
            subtitleRes = R.string.preset_edge_subtitle,
            gravity = Gravity.END,
            width = 10f, height = 70f,
            bgColor = Color.White, bgAlpha = 153,
            strokeColor = PREVIEW_ON_SURFACE, strokeWidth = 1f, strokeAlpha = 40,
            cornerRadius = 5f,
            iconRes = R.drawable.ic_vol_increase, iconSize = 16f, iconColor = Color.White,
            showIcon = false, vibrate = true, edgeMargin = 0f, positionFraction = 0.35f
        ),
        Preset(
            /**
             * A slim bar across the top of the screen, beside the camera cutout.
             *
             * The one preset that is a placement as much as an appearance: it is centred at the
             * very top with snapping off, because a wide flat pill that flew to the side would be
             * neither a notch bar nor a usable edge bar. Swipes still adjust volume from it —
             * the gesture engine measures vertical travel, not which edge the bar is on.
             *
             * Not named after any manufacturer's screen furniture. The shape is not anyone's
             * property; the brand name would be, and a preset label is exactly the kind of
             * incidental trademark use that draws a complaint against a listing.
             */
            id = "Notch",
            nameRes = R.string.preset_notch_title,
            subtitleRes = R.string.preset_notch_subtitle,
            gravity = Gravity.END,
            width = 150f, height = 14f,
            bgColor = Color.Black, bgAlpha = 235,
            strokeColor = Color.White, strokeWidth = 0.5f, strokeAlpha = 30,
            cornerRadius = 7f,
            iconRes = R.drawable.ic_vol_increase, iconSize = 16f, iconColor = Color.White,
            showIcon = false, vibrate = true, edgeMargin = 0f, positionFraction = 0f,
            placement = Placement(
                gravity = Gravity.END,
                posXFraction = 0.5f,
                posYFraction = 0f,
                snapToEdge = false
            )
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
