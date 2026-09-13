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
    val posXFraction: Float,
    /** Whether portrait and landscape share one position. See SharedPref.getHandlerSamePositionBothOrientations. */
    val samePosition: Boolean = false,
    /** The same two fractions for the orientation the phone is *not* held in right now. */
    val otherPositionFraction: Float = positionFraction,
    val otherPosXFraction: Float = posXFraction,
    /** Whether the bar follows the phone round its edges. See SharedPref.getHandlerDynamicPosition. */
    val dynamicPosition: Boolean = false,
    /**
     * The preset applied in this editing session and not yet saved, or null.
     *
     * Part of the state so that applying a preset counts as a change even when its look is the
     * one already on screen: the preset also carries behaviour, and Apply has to be reachable to
     * write it. See [AppearanceStateHolder.appliedPresetId].
     */
    val appliedPresetId: String? = null,
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
    initialPosXFraction: Float,
    initialSamePosition: Boolean = false,
    initialOtherPositionFraction: Float = initialPositionFraction,
    initialOtherPosXFraction: Float = initialPosXFraction,
    initialDynamicPosition: Boolean = false,
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
    var samePosition by mutableStateOf(initialSamePosition)
    var otherPositionFraction by mutableStateOf(initialOtherPositionFraction)
    var otherPosXFraction by mutableStateOf(initialOtherPosXFraction)
    var dynamicPosition by mutableStateOf(initialDynamicPosition)

    /**
     * The id of the last preset applied since the screen opened or was last saved, or null.
     *
     * What decides whether saving writes a preset's [HandlerPresets.Behaviour] — its gestures, the
     * Quick panel, the menu and the panel animation. Set only by [applyPreset], so a user who opens
     * the screen to change a colour saves a colour and keeps every action they chose elsewhere.
     */
    var appliedPresetId by mutableStateOf<String?>(null)

    fun toState(): AppearanceState = AppearanceState(
        gravity, width, height, bgColor.toArgb(), bgAlpha,
        strokeColor.toArgb(), strokeWidth, strokeAlpha,
        cornerTL, cornerTR, cornerBL, cornerBR, shape, flare,
        iconRes, iconSize, iconColor.toArgb(), showIcon, vibrate,
        edgeMargin, snapToEdge, positionFraction, posXFraction,
        samePosition, otherPositionFraction, otherPosXFraction, dynamicPosition,
        appliedPresetId,
    )
}

/**
 * Writes a preset onto the holder: its look, where it puts the bar, and a note that it was applied.
 *
 * Extracted so the deep link from the main screen's preset card and the quick-preset chips in the
 * settings sheet cannot drift apart — they were two copies of the same sixteen assignments.
 *
 * Every preset sets the bar's height on screen, [HandlerPresets.Preset.positionFraction], in both
 * orientations, and whether it follows the phone round ([AppearanceStateHolder.dynamicPosition]):
 * those live in the holder, so the preview shows them and a drag or a switch after applying still
 * wins. A preset with a [HandlerPresets.Placement] also sets the side and the snap; the bubble has
 * none and stays on whichever side the bar is.
 *
 * The rest of the preset's behaviour is not on this screen at all, so it is not copied here —
 * [AppearanceStateHolder.appliedPresetId] records the preset, and saving writes its behaviour.
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
    positionFraction = preset.positionFraction
    otherPositionFraction = preset.positionFraction
    dynamicPosition = preset.behaviour.dynamicPosition
    preset.placement?.let { placement ->
        gravity = placement.gravity
        posXFraction = placement.posXFraction
        snapToEdge = placement.snapToEdge
    }
    appliedPresetId = preset.id
}

/**
 * Whether the holder currently matches this preset, so a chip can show as selected.
 *
 * Compares the **look** only: the appearance fields [applyPreset] writes, plus the snap for a
 * preset with a [HandlerPresets.Placement]. Deliberately left out:
 *
 *  - Gravity and the position fractions, otherwise dragging the bar would silently deselect the
 *    preset whose colours are still on screen.
 *  - Dynamic position and every other part of [HandlerPresets.Behaviour]. The chip answers "is
 *    this the bar I am looking at?", which is a question about the picture in the preview. The
 *    behaviour is not shown on this screen and is changed on others; a chip that went dark because
 *    the user later moved swipe-out to Back, or flipped dynamic position, would be reporting
 *    something the user cannot see here and cannot fix from here.
 *
 * Corners are compared per corner, so a preset with asymmetric corners (the Edge) matches once
 * applied.
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
        state.cornerTL == topLeft &&
        state.cornerTR == topRight &&
        state.cornerBL == bottomLeft &&
        state.cornerBR == bottomRight &&
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
