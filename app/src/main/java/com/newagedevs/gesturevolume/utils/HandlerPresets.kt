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
        /**
         * The radius every corner takes unless one of the four below overrides it.
         *
         * Kept as the single source for the symmetric presets, which is most of them, so that
         * "this shape is a pill" stays one number rather than four that have to agree.
         */
        val cornerRadius: Float,
        @param:DrawableRes val iconRes: Int,
        val iconSize: Float,
        val iconColor: Color,
        val showIcon: Boolean,
        val vibrate: Boolean,
        val edgeMargin: Float,
        val positionFraction: Float,
        /** Non-null only for a preset that is a place as much as a look. See [Placement]. */
        val placement: Placement? = null,
        /**
         * Per-corner overrides, for the shapes that are not symmetric.
         *
         * A bar flush against a screen edge wants its outer corners square and its inner ones
         * rounded — that is what makes it read as something attached to the edge rather than
         * floating near it. Expressed as four nullable overrides rather than four required values
         * so that the symmetric presets stay one number.
         */
        val cornerTopLeft: Float? = null,
        val cornerTopRight: Float? = null,
        val cornerBottomLeft: Float? = null,
        val cornerBottomRight: Float? = null,
    ) {
        val topLeft: Float get() = cornerTopLeft ?: cornerRadius
        val topRight: Float get() = cornerTopRight ?: cornerRadius
        val bottomLeft: Float get() = cornerBottomLeft ?: cornerRadius
        val bottomRight: Float get() = cornerBottomRight ?: cornerRadius
    }

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
            width = 12f, height = 95f,
            bgColor = Color.Black, bgAlpha = 255,
            strokeColor = Color.White, strokeWidth = 0f, strokeAlpha = 200,
            cornerRadius = 10f,
            iconRes = R.drawable.ic_vol_increase, iconSize = 18f, iconColor = Color.White,
            showIcon = false, vibrate = false, edgeMargin = 0f, positionFraction = 0.5f,
            placement = Placement(
                gravity = Gravity.END,
                posXFraction = 1f,
                posYFraction = 0.5f,
                snapToEdge = true
            ),
            // Square where it meets the screen edge, rounded where it faces the app. The two
            // radii are given as left/right rather than inner/outer because the preset is written
            // for the right-hand edge it ships against; carried to the left edge by a drag, the
            // bar keeps these corners and the rounding ends up on the outside. Living with that
            // is the cost of corners being four plain numbers the user can also edit by hand.
            cornerTopLeft = 10f, cornerTopRight = 1f,
            cornerBottomLeft = 10f, cornerBottomRight = 1f
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
            /**
             * The floating bubble: a circle that sits near the edge rather than against it.
             *
             * The odd one out on purpose. Every other preset here is a bar — a tall thin thing
             * welded to the side of the screen — and this is the shape people reach for when they
             * want the opposite: something round, obviously draggable, and clearly *on top of* the
             * app rather than part of its frame. A circle is what an assistive on-screen button
             * has looked like on every platform that has one.
             *
             * Three numbers make it a bubble rather than a wide bar. Width and height are equal,
             * the radius is exactly half of them — anything less is a rounded square — and the
             * edge margin lifts it off the side, because a circle flush to the edge is a circle
             * with a slice missing. The alpha is low enough to see the app through it and high
             * enough to find it on a white screen.
             */
            id = "Bold",
            nameRes = R.string.preset_bold_title,
            subtitleRes = R.string.preset_bold_subtitle,
            gravity = Gravity.END,
            width = 46f, height = 46f,
            bgColor = PREVIEW_ON_SURFACE, bgAlpha = 140,
            strokeColor = Color.White, strokeWidth = 1.5f, strokeAlpha = 90,
            cornerRadius = 23f,
            // The volume glyph rather than the move one. A bubble is round and obviously
            // draggable already; what it cannot say for itself is what it is *for*.
            iconRes = R.drawable.ic_vol_increase, iconSize = 24f, iconColor = Color.White,
            showIcon = true, vibrate = true, edgeMargin = 6f, positionFraction = 0.55f
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
            // No icon. Night is the quiet preset — a dark bar meant to disappear into a dark
            // app — and a glyph on it is the one thing that would keep catching the eye.
            iconRes = R.drawable.ic_vol_increase, iconSize = 22f, iconColor = Color(0xFF9CA3AF),
            showIcon = false, vibrate = true, edgeMargin = 0f, positionFraction = 0.12f
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
