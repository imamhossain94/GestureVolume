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
import com.newagedevs.gesturevolume.service.HandlerGeometry
import com.newagedevs.gesturevolume.utils.HandlerPresets

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
 * at once. Each group is now collapsed behind a header that summarises its current value, so the
 * screen opens as seven readable lines instead of a thirty-control wall, and the preset row at the
 * top makes the common case — "give me a look I like" — a single tap that never opens a group at
 * all.
 *
 * Section order is by how often a group is the reason someone opened this screen. Background leads
 * and opens by default; Position, which used to be first, is four paragraphs of prose about drag
 * behaviour and is the least likely reason anyone came here.
 */
@Composable
fun HandlerAppearanceSettingsContent(
    state: AppearanceStateHolder,
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

        // ---- Background ---------------------------------------------------------------------
        AppearanceSection(
            title = stringResource(R.string.background_uppercase),
            summary = "${((state.bgAlpha / 255f) * 100).toInt()}%",
            initiallyExpanded = true,
        ) {
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
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- Dimensions ---------------------------------------------------------------------
        AppearanceSection(
            title = stringResource(R.string.dimensions_uppercase),
            summary = "${state.width.toInt()} × ${state.height.toInt()}dp",
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
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- Corner radius ------------------------------------------------------------------
        // Five sliders became one plus an opt-in. Four of them were per-corner controls that
        // almost nobody wants and that the fifth silently overwrote.
        val cornersUniform = state.cornerTL == state.cornerTR &&
            state.cornerTR == state.cornerBL &&
            state.cornerBL == state.cornerBR
        val mixedLabel = stringResource(R.string.per_corner_mixed)

        AppearanceSection(
            title = stringResource(R.string.corner_radius_uppercase),
            summary = if (cornersUniform) "${state.cornerTL.toInt()}dp" else mixedLabel,
        ) {
            // Seeded open when the stored corners already differ, so an install that arrives with
            // four different values lands on the controls that explain what it is showing.
            var perCorner by rememberSaveable { mutableStateOf(!cornersUniform) }

            SliderControl(
                label = stringResource(R.string.all_corners),
                // Reads the real corner rather than a shadow field, so it can no longer claim a
                // value the bar does not have.
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

        Spacer(modifier = Modifier.height(12.dp))

        // ---- Stroke -------------------------------------------------------------------------
        AppearanceSection(
            title = stringResource(R.string.stroke_uppercase),
            summary = "${state.strokeWidth.toInt()}dp · ${((state.strokeAlpha / 255f) * 100).toInt()}%",
        ) {
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
                label = stringResource(R.string.width),
                value = state.strokeWidth,
                valueRange = 0f..8f,
                valueDisplay = "${state.strokeWidth.toInt()}dp",
                borderColor = MaterialTheme.colorScheme.primary,
                onValueChange = { state.strokeWidth = it }
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
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- Icon ---------------------------------------------------------------------------
        AppearanceSection(title = stringResource(R.string.icon_settings)) {
            SwitchControl(
                label = stringResource(R.string.show_icon),
                checked = state.showIcon,
                borderColor = MaterialTheme.colorScheme.primary,
                onCheckedChange = { state.showIcon = it }
            )

            // Was a bare `if`, which popped the four icon controls in instantly while the section
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

        // ---- Position -----------------------------------------------------------------------
        AppearanceSection(
            title = stringResource(R.string.position_uppercase),
            summary = stringResource(
                if (state.gravity == Gravity.START) R.string.side_left else R.string.side_right
            ),
        ) {
            // Which side the bar starts on. There *was* no picker here, on the reasoning that the
            // side is a consequence of where the bar was dragged rather than a setting, and that a
            // picker could only disagree with where the bar actually is. That reasoning depended
            // on this screen having a draggable preview, which it no longer does — so without this
            // there is no way to put a right-hand bar on the left except to long press the live
            // one and carry it across, which is a thing you have to already know.
            //
            // It writes the x fraction as well as the gravity, so it is a real move rather than a
            // change of dressing: flush left is 0, flush right is 1, and the edge distance below
            // does the rest.
            SideSelector(
                gravity = state.gravity,
                onGravityChange = {
                    state.gravity = it
                    state.posXFraction = if (it == Gravity.START) 0f else 1f
                },
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            Text(
                text = stringResource(R.string.drag_to_move_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            SwitchControl(
                label = stringResource(R.string.snap_to_edge),
                checked = state.snapToEdge,
                borderColor = MaterialTheme.colorScheme.primary,
                onCheckedChange = { state.snapToEdge = it }
            )
            Text(
                text = stringResource(R.string.snap_to_edge_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

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
            // A way back for anyone who drags the bar somewhere awkward. Both axes now — putting
            // the bar back in the middle of the height it is already lost behind is no rescue.
            TextButton(
                onClick = {
                    // The same corner a fresh install starts in, so "Reset" and "new install"
                    // cannot disagree about where the bar belongs.
                    state.positionFraction = HandlerGeometry.DEFAULT_POSITION_FRACTION
                    state.posXFraction = DEFAULT_POS_X_FRACTION
                    state.gravity = if (DEFAULT_POS_X_FRACTION < 0.5f) Gravity.START else Gravity.END
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.reset_position))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- Behaviour ----------------------------------------------------------------------
        AppearanceSection(title = stringResource(R.string.behavior_uppercase)) {
            SwitchControl(
                label = stringResource(R.string.vibrate_on_click),
                checked = state.vibrate,
                borderColor = MaterialTheme.colorScheme.primary,
                onCheckedChange = { state.vibrate = it }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * One preset in the quick row.
 *
 * Tapping it only writes the holder, so it stays inside the existing Apply/Discard contract: the
 * bar previews immediately, the tick lights, and nothing reaches the live overlay until Apply.
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
private fun SideSelectorHalf(
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
