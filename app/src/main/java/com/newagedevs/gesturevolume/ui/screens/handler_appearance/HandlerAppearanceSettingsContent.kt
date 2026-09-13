package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import android.view.Gravity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.utils.HandlerPresets
import com.newagedevs.gesturevolume.utils.HandlerShape
import kotlin.math.roundToInt

/**
 * Where "Reset position" puts the bar horizontally: flush with the default preset's side.
 *
 * Read from [HandlerPresets] rather than from the preference's own default, which is derived from
 * exactly the same place — one definition of "the side the app starts on", not two.
 */
private val DEFAULT_POS_X_FRACTION: Float =
    if (HandlerPresets.DEFAULT.gravity == Gravity.START) 0f else 1f

/**
 * The appearance controls.
 *
 * Every control this screen has ever had is still here; what changed is how much of it is on screen
 * at once. Each group is collapsed behind a header that summarises its current value, and the
 * preset row at the top makes the common case — "give me a look I like" — a single tap that never
 * opens a group at all.
 *
 * The groups follow the order the panel screens share — content, size and shape, colours,
 * behaviour — and each holds one kind of thing, so a setting is where it would be on any of them.
 * That splits what used to be an Icon group and a Stroke group: the icon's switch, size and colour
 * now sit under Content, Size & shape and Colours, and so do the stroke's width and colour. Position,
 * which only the bar has, comes last: it is four paragraphs of prose about drag behaviour and the
 * least likely reason anyone came here.
 */
@Composable
fun HandlerAppearanceSettingsContent(
    state: AppearanceStateHolder,
    /** Which of the two positions is the one the phone is being held in. */
    isPortrait: Boolean,
    onShowIconPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {

        // ---- Quick presets ------------------------------------------------------------------
        // Above every group, because it is the one control that can finish the job on its own.
        SectionTitle(stringResource(R.string.quick_presets), MaterialTheme.colorScheme.primary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HandlerPresets.ALL.forEach { preset ->
                PresetChip(preset = preset, state = state)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Read by more than one group below. Corners are a property of a rectangle, and a tab has
        // none: showing four radius sliders that the bar on screen visibly ignores is worse than
        // showing nothing, so Size & shape swaps them for the sweep while the bar is a tab.
        val isTab = state.shape == HandlerShape.TAB
        val cornersUniform = state.cornerTL == state.cornerTR &&
            state.cornerTR == state.cornerBL &&
            state.cornerBL == state.cornerBR
        val mixedLabel = stringResource(R.string.per_corner_mixed)

        // ---- Content ------------------------------------------------------------------------
        // What the bar carries. The icon's size and colour are in the groups for sizes and
        // colours, each shown only while there is an icon for them to change.
        AppearanceSection(
            title = stringResource(R.string.group_content),
            summary = stringResource(if (state.showIcon) R.string.show_icon else R.string.icon_none),
            initiallyExpanded = true,
        ) {
            SwitchControl(
                label = stringResource(R.string.show_icon),
                checked = state.showIcon,
                borderColor = MaterialTheme.colorScheme.primary,
                onCheckedChange = { state.showIcon = it }
            )

            // Was a bare `if`, which popped the icon controls in instantly while the section
            // around them was still animating open. Same spec as the section, so a nested reveal
            // reads as one motion rather than two.
            AnimatedVisibility(
                visible = state.showIcon,
                enter = expandVertically(animationSpec = AppearanceMotion.ExpandSize) +
                    fadeIn(animationSpec = AppearanceMotion.Fade),
                exit = shrinkVertically(animationSpec = AppearanceMotion.ExpandSize) +
                    fadeOut(animationSpec = AppearanceMotion.Fade),
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    IconPickerControl(
                        label = stringResource(R.string.icon),
                        selectedIconRes = state.iconRes,
                        borderColor = MaterialTheme.colorScheme.primary,
                        onClick = { onShowIconPicker() }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- Size & shape -------------------------------------------------------------------
        AppearanceSection(
            title = stringResource(R.string.group_size_shape),
            summary = "${state.width.toInt()} × ${state.height.toInt()}dp · " +
                stringResource(if (isTab) R.string.shape_tab else R.string.shape_rounded),
        ) {
            SliderControl(
                label = stringResource(R.string.width),
                value = state.width,
                // Up to 200dp since 1.4.0: the Notch preset lays the bar across the top of the
                // screen, and a range that stopped at 60 could not express it — nor could a user
                // adjust one after applying it.
                valueRange = 10f..200f,
                valueDisplay = "${state.width.toInt()}dp",
                borderColor = MaterialTheme.colorScheme.primary,
                onValueChange = { state.width = it }
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            SliderControl(
                label = stringResource(R.string.height),
                value = state.height,
                // Down to 8dp for the same reason: a bar across the top is a thin one.
                valueRange = 8f..200f,
                valueDisplay = "${state.height.toInt()}dp",
                borderColor = MaterialTheme.colorScheme.primary,
                onValueChange = { state.height = it }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            ShapeSelector(
                shape = state.shape,
                onShapeChange = { state.shape = it },
            )
            AnimatedVisibility(
                visible = isTab,
                enter = expandVertically(AppearanceMotion.ExpandSize) +
                    fadeIn(AppearanceMotion.Fade),
                exit = shrinkVertically(AppearanceMotion.ExpandSize) +
                    fadeOut(AppearanceMotion.Fade),
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SliderControl(
                        label = stringResource(R.string.end_sweep),
                        // Shown as a percentage of the bar's height rather than as the fraction
                        // it is stored as: "14%" is a length someone can picture against the bar
                        // in the dock above, where "0.14" is a number about nothing.
                        value = state.flare * 100f,
                        valueRange = HandlerShape.MIN_FLARE * 100f..HandlerShape.MAX_FLARE * 100f,
                        valueDisplay = "${(state.flare * 100f).toInt()}%",
                        borderColor = MaterialTheme.colorScheme.primary,
                        onValueChange = { state.flare = it / 100f }
                    )
                    Text(
                        text = stringResource(R.string.end_sweep_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Corner radius, for the rounded shape only. Five sliders became one plus an opt-in:
            // four of them were per-corner controls that almost nobody wants and that the fifth
            // silently overwrote.
            AnimatedVisibility(
                visible = !isTab,
                enter = expandVertically(AppearanceMotion.ExpandSize) +
                    fadeIn(AppearanceMotion.Fade),
                exit = shrinkVertically(AppearanceMotion.ExpandSize) +
                    fadeOut(AppearanceMotion.Fade),
            ) {
                Column {
                    // Seeded open when the stored corners already differ, so an install that
                    // arrives with four different values lands on the controls that explain what
                    // it is showing.
                    var perCorner by rememberSaveable { mutableStateOf(!cornersUniform) }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SliderControl(
                        label = stringResource(R.string.all_corners),
                        // Reads the real corner rather than a shadow field, so it can no longer
                        // claim a value the bar does not have.
                        value = state.cornerTL,
                        valueRange = 0f..50f,
                        valueDisplay = if (cornersUniform) "${state.cornerTL.toInt()}dp" else mixedLabel,
                        borderColor = MaterialTheme.colorScheme.primary,
                        onValueChange = { value ->
                            state.cornerTL = value
                            state.cornerTR = value
                            state.cornerBL = value
                            state.cornerBR = value
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SwitchControl(
                        label = stringResource(R.string.per_corner),
                        checked = perCorner,
                        borderColor = MaterialTheme.colorScheme.primary,
                        onCheckedChange = { perCorner = it }
                    )

                    AnimatedVisibility(
                        visible = perCorner,
                        enter = expandVertically(animationSpec = AppearanceMotion.ExpandSize) +
                            fadeIn(animationSpec = AppearanceMotion.Fade),
                        exit = shrinkVertically(animationSpec = AppearanceMotion.ExpandSize) +
                            fadeOut(animationSpec = AppearanceMotion.Fade),
                    ) {
                        Column {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                            SliderControl(
                                label = stringResource(R.string.top_left),
                                value = state.cornerTL,
                                valueRange = 0f..50f,
                                valueDisplay = "${state.cornerTL.toInt()}dp",
                                borderColor = MaterialTheme.colorScheme.primary,
                                onValueChange = { state.cornerTL = it }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                            SliderControl(
                                label = stringResource(R.string.top_right),
                                value = state.cornerTR,
                                valueRange = 0f..50f,
                                valueDisplay = "${state.cornerTR.toInt()}dp",
                                borderColor = MaterialTheme.colorScheme.primary,
                                onValueChange = { state.cornerTR = it }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                            SliderControl(
                                label = stringResource(R.string.bottom_left),
                                value = state.cornerBL,
                                valueRange = 0f..50f,
                                valueDisplay = "${state.cornerBL.toInt()}dp",
                                borderColor = MaterialTheme.colorScheme.primary,
                                onValueChange = { state.cornerBL = it }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                            SliderControl(
                                label = stringResource(R.string.bottom_right),
                                value = state.cornerBR,
                                valueRange = 0f..50f,
                                valueDisplay = "${state.cornerBR.toInt()}dp",
                                borderColor = MaterialTheme.colorScheme.primary,
                                onValueChange = { state.cornerBR = it }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            // Headed, because "Width" a few rows under the bar's own width would read as the
            // same thing twice.
            SubgroupLabel(stringResource(R.string.stroke_uppercase))
            SliderControl(
                label = stringResource(R.string.width),
                value = state.strokeWidth,
                valueRange = 0f..8f,
                valueDisplay = "${state.strokeWidth.toInt()}dp",
                borderColor = MaterialTheme.colorScheme.primary,
                onValueChange = { state.strokeWidth = it }
            )

            AnimatedVisibility(
                visible = state.showIcon,
                enter = expandVertically(animationSpec = AppearanceMotion.ExpandSize) +
                    fadeIn(animationSpec = AppearanceMotion.Fade),
                exit = shrinkVertically(animationSpec = AppearanceMotion.ExpandSize) +
                    fadeOut(animationSpec = AppearanceMotion.Fade),
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SliderControl(
                        label = stringResource(R.string.icon_size),
                        value = state.iconSize,
                        valueRange = 16f..48f,
                        valueDisplay = "${state.iconSize.toInt()}dp",
                        borderColor = MaterialTheme.colorScheme.primary,
                        onValueChange = { state.iconSize = it }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- Colours ------------------------------------------------------------------------
        AppearanceSection(
            title = stringResource(R.string.group_colours),
            summary = "${((state.bgAlpha / 255f) * 100).toInt()}% · ${((state.strokeAlpha / 255f) * 100).toInt()}%",
        ) {
            // Headed, for the same reason as the stroke's width: two "Color" and two "Opacity"
            // rows in one group need to say which is which.
            SubgroupLabel(stringResource(R.string.background_uppercase))
            ColorPickerControl(
                label = stringResource(R.string.color),
                color = state.bgColor,
                borderColor = MaterialTheme.colorScheme.primary,
                onColorChange = { state.bgColor = it }
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            SliderControl(
                label = stringResource(R.string.opacity),
                value = state.bgAlpha.toFloat(),
                valueRange = 0f..255f,
                valueDisplay = "${((state.bgAlpha / 255f) * 100).toInt()}%",
                borderColor = MaterialTheme.colorScheme.primary,
                onValueChange = { state.bgAlpha = it.toInt() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            SubgroupLabel(stringResource(R.string.stroke_uppercase))
            ColorPickerControl(
                label = stringResource(R.string.color),
                color = state.strokeColor,
                borderColor = MaterialTheme.colorScheme.primary,
                onColorChange = { state.strokeColor = it }
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            SliderControl(
                label = stringResource(R.string.opacity),
                value = state.strokeAlpha.toFloat(),
                valueRange = 0f..255f,
                valueDisplay = "${((state.strokeAlpha / 255f) * 100).toInt()}%",
                borderColor = MaterialTheme.colorScheme.primary,
                onValueChange = { state.strokeAlpha = it.toInt() }
            )

            AnimatedVisibility(
                visible = state.showIcon,
                enter = expandVertically(animationSpec = AppearanceMotion.ExpandSize) +
                    fadeIn(animationSpec = AppearanceMotion.Fade),
                exit = shrinkVertically(animationSpec = AppearanceMotion.ExpandSize) +
                    fadeOut(animationSpec = AppearanceMotion.Fade),
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    ColorPickerControl(
                        label = stringResource(R.string.icon_color),
                        color = state.iconColor,
                        borderColor = MaterialTheme.colorScheme.primary,
                        onColorChange = { state.iconColor = it }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- Behaviour ----------------------------------------------------------------------
        AppearanceSection(
            title = stringResource(R.string.group_behaviour),
            // Named while it is on; off, the header is left to speak for itself.
            summary = if (state.vibrate) stringResource(R.string.vibrate_on_click) else null,
        ) {
            SwitchControl(
                label = stringResource(R.string.vibrate_on_click),
                checked = state.vibrate,
                borderColor = MaterialTheme.colorScheme.primary,
                onCheckedChange = { state.vibrate = it }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- Position -----------------------------------------------------------------------
        AppearanceSection(
            title = stringResource(R.string.position_uppercase),
            summary = if (state.dynamicPosition) {
                stringResource(R.string.position_dynamic)
            } else if (state.samePosition || !state.snapToEdge) {
                "${(state.posXFraction * 100).roundToInt()}% · ${(state.positionFraction * 100).roundToInt()}%"
            } else {
                stringResource(if (state.gravity == Gravity.START) R.string.side_left else R.string.side_right)
            },
        ) {
            // Set when a slider moved the bar off an edge and switched snapping off to let it stay
            // there, so the user is told why a switch they did not touch has changed.
            var snapTurnedOff by rememberSaveable { mutableStateOf(false) }

            // Which side, in both orientations at once. The quick answer for most people, and
            // the only way a right-hand bar reaches the left without being carried across.
            SideSelector(
                gravity = state.gravity,
                onGravityChange = {
                    val x = if (it == Gravity.START) 0f else 1f
                    state.gravity = it
                    state.posXFraction = x
                    state.otherPosXFraction = x
                },
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            // Edge Deck's Dynamic position: the bar keeps to the same edge of the phone, so turning
            // it on its side lays the bar along the top or the bottom of the screen.
            SwitchControl(
                label = stringResource(R.string.position_dynamic),
                checked = state.dynamicPosition,
                borderColor = MaterialTheme.colorScheme.primary,
                onCheckedChange = { state.dynamicPosition = it }
            )
            Text(
                text = stringResource(R.string.position_dynamic_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            if (state.dynamicPosition) {
                // One place, along the side chosen above, measured upright. Same-position and
                // snapping have nothing to say here: there is one position, always on an edge.
                val uprightY = if (isPortrait) state.positionFraction else state.otherPositionFraction
                SliderControl(
                    label = stringResource(R.string.position_along_edge),
                    value = uprightY,
                    valueRange = 0f..1f,
                    valueDisplay = "${(uprightY * 100).roundToInt()}%",
                    borderColor = MaterialTheme.colorScheme.primary,
                    onValueChange = {
                        state.positionFraction = it
                        state.otherPositionFraction = it
                    }
                )
                Text(
                    text = stringResource(R.string.position_along_edge_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            } else {
                SwitchControl(
                    label = stringResource(R.string.position_same_both),
                    checked = state.samePosition,
                    borderColor = MaterialTheme.colorScheme.primary,
                    onCheckedChange = { state.samePosition = it }
                )
                Text(
                    text = stringResource(R.string.position_same_both_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                // Where exactly, as two shares of the screen. What the example asks for — halfway
                // down the left edge upright, the middle of the top edge on its side — is two pairs of
                // numbers, and a drag can only ever set the pair for the way the phone is held.
                val moveX: (Float, Boolean) -> Unit = { x, current ->
                    if (current) {
                        state.posXFraction = x
                        state.gravity = if (x < 0.5f) Gravity.START else Gravity.END
                    } else {
                        state.otherPosXFraction = x
                    }
                    // Snapping would send a bar placed mid-screen straight back to the nearer side.
                    if (state.snapToEdge && x > SNAP_EDGE_BAND && x < 1f - SNAP_EDGE_BAND) {
                        state.snapToEdge = false
                        snapTurnedOff = true
                    }
                }
                if (state.samePosition) {
                    PositionSliders(
                        title = stringResource(R.string.position_both),
                        x = state.posXFraction,
                        y = state.positionFraction,
                        onX = { moveX(it, true) },
                        onY = { state.positionFraction = it },
                    )
                } else {
                    val now = stringResource(R.string.position_now)
                    val portrait = stringResource(R.string.position_portrait)
                    val landscape = stringResource(R.string.position_landscape)
                    PositionSliders(
                        title = if (isPortrait) "$portrait · $now" else portrait,
                        x = if (isPortrait) state.posXFraction else state.otherPosXFraction,
                        y = if (isPortrait) state.positionFraction else state.otherPositionFraction,
                        onX = { moveX(it, isPortrait) },
                        onY = { if (isPortrait) state.positionFraction = it else state.otherPositionFraction = it },
                    )
                    PositionSliders(
                        title = if (!isPortrait) "$landscape · $now" else landscape,
                        x = if (!isPortrait) state.posXFraction else state.otherPosXFraction,
                        y = if (!isPortrait) state.positionFraction else state.otherPositionFraction,
                        onX = { moveX(it, !isPortrait) },
                        onY = { if (!isPortrait) state.positionFraction = it else state.otherPositionFraction = it },
                    )
                }
                Text(
                    text = stringResource(R.string.position_sliders_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
                SwitchControl(
                    label = stringResource(R.string.snap_to_edge),
                    checked = state.snapToEdge,
                    borderColor = MaterialTheme.colorScheme.primary,
                    onCheckedChange = {
                        state.snapToEdge = it
                        snapTurnedOff = false
                    }
                )
                Text(
                    text = stringResource(
                        if (snapTurnedOff && !state.snapToEdge) R.string.position_snap_off_hint else R.string.snap_to_edge_desc
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (snapTurnedOff && !state.snapToEdge) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    lineHeight = 16.sp
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            SliderControl(
                label = stringResource(R.string.edge_offset),
                value = state.edgeMargin,
                valueRange = 0f..48f,
                valueDisplay = "${state.edgeMargin.toInt()}dp",
                borderColor = MaterialTheme.colorScheme.primary,
                onValueChange = { state.edgeMargin = it }
            )
            Text(
                text = stringResource(R.string.edge_offset_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            // A way back for anyone who drags the bar somewhere awkward. Both axes, and both
            // orientations — the bar the user cannot reach may not be the one they are looking at.
            TextButton(
                onClick = {
                    // The same corner a fresh install starts in, so "Reset" and "new install"
                    // cannot disagree about where the bar belongs.
                    state.positionFraction = HandlerPresets.DEFAULT.positionFraction
                    state.posXFraction = DEFAULT_POS_X_FRACTION
                    state.otherPositionFraction = HandlerPresets.DEFAULT.positionFraction
                    state.otherPosXFraction = DEFAULT_POS_X_FRACTION
                    state.gravity = if (DEFAULT_POS_X_FRACTION < 0.5f) Gravity.START else Gravity.END
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.reset_position))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * One preset in the quick row.
 *
 * Tapping it only writes the holder, so it stays inside the existing Apply/Discard contract: the
 * bar previews immediately, the tick lights, and nothing reaches the live overlay until Apply —
 * which then writes the preset's behaviour as well as its look. See [applyPreset].
 *
 * The press feedback is a `graphicsLayer` scale rather than a size change, so however far the
 * spring overshoots it cannot reflow the row around it.
 */
@Composable
private fun PresetChip(
    preset: HandlerPresets.Preset,
    state: AppearanceStateHolder,
) {
    val selected = preset.matches(state)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = AppearanceMotion.Pop,
        label = "presetChipScale",
    )
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = AppearanceMotion.Tint,
        label = "presetChipContainer",
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = container,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = { state.applyPreset(preset) },
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // The same swatch the main screen's preset card shows, so the two agree.
            Spacer(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(preset.bgColor.copy(alpha = preset.bgAlpha / 255f))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(preset.nameRes),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * A heading inside a group, over controls whose own label ("Color", "Width") only says which
 * thing it changes when it is read under one.
 */
@Composable
private fun SubgroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

/**
 * How close to a side a slider may leave the bar and still count as against it: the band where
 * Snap to edge is left alone, because it would put the bar exactly where it already nearly is.
 */
private const val SNAP_EDGE_BAND = 0.06f

/** One orientation's position: how far across and how far down the middle of the bar sits. */
@Composable
private fun PositionSliders(
    title: String,
    x: Float,
    y: Float,
    onX: (Float) -> Unit,
    onY: (Float) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
    )
    SliderControl(
        label = stringResource(R.string.position_from_left),
        value = x * 100f,
        valueRange = 0f..100f,
        valueDisplay = "${(x * 100f).roundToInt()}%",
        borderColor = MaterialTheme.colorScheme.primary,
        onValueChange = { onX((it / 100f).coerceIn(0f, 1f)) }
    )
    SliderControl(
        label = stringResource(R.string.position_from_top),
        value = y * 100f,
        valueRange = 0f..100f,
        valueDisplay = "${(y * 100f).roundToInt()}%",
        borderColor = MaterialTheme.colorScheme.primary,
        onValueChange = { onY((it / 100f).coerceIn(0f, 1f)) }
    )
}

/**
 * Rounded or tab, as two halves of one pill.
 *
 * The same segmented control the side picker uses, for the same reason: two states, neither of
 * them "off", and the result is visible in the dock above the moment it is tapped. It carries a
 * line of explanation where the side picker does not, because "tab" is a word for a shape that
 * only makes sense once you know it has to be touching the edge to look like anything.
 */
@Composable
fun ShapeSelector(
    shape: String,
    onShapeChange: (String) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(3.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            SideSelectorHalf(
                label = stringResource(R.string.shape_rounded),
                selected = shape != HandlerShape.TAB,
                onClick = { onShapeChange(HandlerShape.ROUNDED) },
                modifier = Modifier.weight(1f),
            )
            SideSelectorHalf(
                label = stringResource(R.string.shape_tab),
                selected = shape == HandlerShape.TAB,
                onClick = { onShapeChange(HandlerShape.TAB) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(
                if (shape == HandlerShape.TAB) R.string.shape_tab_desc else R.string.shape_rounded_desc
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * Left or right, as two halves of one pill.
 *
 * A segmented pair rather than a switch, because the two states are places and neither is "off";
 * and rather than a dropdown, because there are exactly two of them and they are the answer to a
 * question the user can see the result of immediately in the dock above.
 */
@Composable
private fun SideSelector(
    gravity: Int,
    onGravityChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        SideSelectorHalf(
            label = stringResource(R.string.side_left),
            selected = gravity == Gravity.START,
            onClick = { onGravityChange(Gravity.START) },
            modifier = Modifier.weight(1f),
        )
        SideSelectorHalf(
            label = stringResource(R.string.side_right),
            selected = gravity == Gravity.END,
            onClick = { onGravityChange(Gravity.END) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun SideSelectorHalf(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        animationSpec = AppearanceMotion.Tint,
        label = "sideSelectorBackground",
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = AppearanceMotion.Tint,
        label = "sideSelectorContent",
    )

    Text(
        text = label,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .background(background)
            .padding(vertical = 9.dp),
        textAlign = TextAlign.Center,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = content,
    )
}
