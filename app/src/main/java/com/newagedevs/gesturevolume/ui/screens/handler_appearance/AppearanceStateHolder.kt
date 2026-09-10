package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.newagedevs.gesturevolume.utils.HandlerPresets
import com.newagedevs.gesturevolume.utils.HandlerShape

data class AppearanceState(
    val gravity: Int,
    val width: Float,
    val height: Float,
    val bgColor: Int,
    val bgAlpha: Int,
    val strokeColor: Int,
    val strokeWidth: Float,
    val strokeAlpha: Int,
    val cornerTL: Float,
    val cornerTR: Float,
    val cornerBL: Float,
    val cornerBR: Float,
    /** The outline the bar is cut to. See [HandlerShape]. */
    val shape: String,
    /** How far a tab's ends sweep back into the screen edge, as a fraction of the height. */
    val flare: Float,
    val iconRes: Int,
    val iconSize: Float,
    val iconColor: Int,
    val showIcon: Boolean,
    val vibrate: Boolean,
    /** Inward nudge from the screen edge, for curved screens and gesture navigation. */
    val edgeMargin: Float,
    /** Whether the bar flies to the nearer side when released. See SharedPref.getHandlerSnapToEdge. */
    val snapToEdge: Boolean,
    /** Vertical position of the bar's centre, 0..1 of the usable height. */
    val positionFraction: Float,
    /**
     * Horizontal position of the bar's centre, 0..1 of the usable width.
     *
     * Here for the same reason the vertical one is: the preview lets the bar be dragged, and what
     * the user does there has to be a thing the Apply/Discard contract can carry. Without it the
     * preview drag was horizontally decorative — the bar followed the finger, snapped back to a
     * side on release, and the live overlay never heard about any of it.
     */
    val posXFraction: Float
)

class AppearanceStateHolder(
    initialGravity: Int,
    initialWidth: Float,
    initialHeight: Float,
    initialBgColor: Color,
    initialBgAlpha: Int,
    initialStrokeColor: Color,
    initialStrokeWidth: Float,
    initialStrokeAlpha: Int,
    initialCornerTL: Float,
    initialCornerTR: Float,
    initialCornerBL: Float,
    initialCornerBR: Float,
    initialShape: String,
    initialFlare: Float,
    initialIconRes: Int,
    initialIconSize: Float,
    initialIconColor: Color,
    initialShowIcon: Boolean,
    initialVibrate: Boolean,
    initialEdgeMargin: Float,
    initialSnapToEdge: Boolean,
    initialPositionFraction: Float,
    initialPosXFraction: Float
) {
    var gravity by mutableStateOf(initialGravity)
    var width by mutableStateOf(initialWidth)
    var height by mutableStateOf(initialHeight)
    var bgColor by mutableStateOf(initialBgColor)
    var bgAlpha by mutableStateOf(initialBgAlpha)
    var strokeColor by mutableStateOf(initialStrokeColor)
    var strokeWidth by mutableStateOf(initialStrokeWidth)
    var strokeAlpha by mutableStateOf(initialStrokeAlpha)
    
    var cornerTL by mutableStateOf(initialCornerTL)
    var cornerTR by mutableStateOf(initialCornerTR)
    var cornerBL by mutableStateOf(initialCornerBL)
    var cornerBR by mutableStateOf(initialCornerBR)

    var shape by mutableStateOf(HandlerShape.sanitize(initialShape))
    var flare by mutableStateOf(HandlerShape.sanitizeFlare(initialFlare))
    
    var iconRes by mutableStateOf(initialIconRes)
    var iconSize by mutableStateOf(initialIconSize)
    var iconColor by mutableStateOf(initialIconColor)
    var showIcon by mutableStateOf(initialShowIcon)
    var vibrate by mutableStateOf(initialVibrate)
    var edgeMargin by mutableStateOf(initialEdgeMargin)
    var snapToEdge by mutableStateOf(initialSnapToEdge)
    var positionFraction by mutableStateOf(initialPositionFraction)
    var posXFraction by mutableStateOf(initialPosXFraction)

    fun toState(): AppearanceState = AppearanceState(
        gravity, width, height, bgColor.toArgb(), bgAlpha,
        strokeColor.toArgb(), strokeWidth, strokeAlpha,
        cornerTL, cornerTR, cornerBL, cornerBR, shape, flare,
        iconRes, iconSize, iconColor.toArgb(), showIcon, vibrate,
        edgeMargin, snapToEdge, positionFraction, posXFraction
    )
}

/**
 * Writes a preset's appearance onto the holder.
 *
 * Extracted so the deep link from the main screen's preset card and the quick-preset chips in the
 * settings sheet cannot drift apart — they were two copies of the same sixteen assignments.
 *
 * A preset is an **appearance, not a placement** — with one stated exception. It leaves
 * [AppearanceStateHolder.gravity], [AppearanceStateHolder.positionFraction] and
 * [AppearanceStateHolder.posXFraction] alone, because the bar is dragged where the user wants it
 * and picking "Night" to change the colour should not throw that away. A preset that carries a
 * [HandlerPresets.Placement] is the exception, and carries one precisely because where it sits is
 * what it *is*: see the Notch preset.
 *
 * Writes only the holder, so applying a preset stays inside the Apply/Discard contract.
 */
fun AppearanceStateHolder.applyPreset(preset: HandlerPresets.Preset) {
    width = preset.width
    height = preset.height
    bgColor = preset.bgColor
    bgAlpha = preset.bgAlpha
    strokeColor = preset.strokeColor
    strokeWidth = preset.strokeWidth
    strokeAlpha = preset.strokeAlpha
    cornerTL = preset.topLeft
    cornerTR = preset.topRight
    cornerBL = preset.bottomLeft
    cornerBR = preset.bottomRight
    shape = preset.shape
    flare = preset.flare
    iconRes = preset.iconRes
    iconSize = preset.iconSize
    iconColor = preset.iconColor
    showIcon = preset.showIcon
    vibrate = preset.vibrate
    edgeMargin = preset.edgeMargin
    preset.placement?.let { placement ->
        gravity = placement.gravity
        posXFraction = placement.posXFraction
        positionFraction = placement.posYFraction
        snapToEdge = placement.snapToEdge
    }
}

/**
 * Whether the holder currently matches this preset, so a chip can show as selected.
 *
 * Compares exactly the fields [applyPreset] writes — gravity and the two position fractions are
 * excluded for the same reason it does not write them, otherwise dragging the bar would silently
 * deselect the preset whose colours are still on screen.
 */
fun HandlerPresets.Preset.matches(state: AppearanceStateHolder): Boolean =
    (placement == null || state.snapToEdge == placement.snapToEdge) &&
        state.width == width &&
        state.height == height &&
        state.bgColor == bgColor &&
        state.bgAlpha == bgAlpha &&
        state.strokeColor == strokeColor &&
        state.strokeWidth == strokeWidth &&
        state.strokeAlpha == strokeAlpha &&
        state.cornerTL == cornerRadius &&
        state.cornerTR == cornerRadius &&
        state.cornerBL == cornerRadius &&
        state.cornerBR == cornerRadius &&
        state.shape == shape &&
        // Only where the shape has ends to sweep. Comparing it on a rounded preset would let a
        // stale flare left over from the tab deselect a chip whose bar is identical on screen.
        (shape != HandlerShape.TAB || state.flare == flare) &&
        state.iconRes == iconRes &&
        state.iconSize == iconSize &&
        state.iconColor == iconColor &&
        state.showIcon == showIcon &&
        state.vibrate == vibrate &&
        state.edgeMargin == edgeMargin
