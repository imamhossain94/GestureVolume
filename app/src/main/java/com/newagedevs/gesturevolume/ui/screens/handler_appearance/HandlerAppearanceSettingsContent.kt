package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import android.view.Gravity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
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

@Composable
fun HandlerAppearanceSettingsContent(
    state: AppearanceStateHolder,
    onShowIconPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Position Section
        SectionTitle(stringResource(R.string.position_uppercase), Color(0xFF8B5CF6))
        CustomizationCard(borderColor = Color(0xFF8B5CF6)) {
            // No Left/Right picker. The bar goes where it is dragged, and which side it is
            // "on" is a consequence of that rather than a setting — a picker on top could only
            // ever disagree with where the bar actually is. The two controls below are the whole
            // of horizontal placement: whether it returns to a side, and how far in that side is.
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
                borderColor = Color(0xFF8B5CF6),
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
                borderColor = Color(0xFF8B5CF6),
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

        Spacer(modifier = Modifier.height(16.dp))

        // Dimensions Section
        SectionTitle(stringResource(R.string.dimensions_uppercase), Color(0xFF3B82F6))
        CustomizationCard(borderColor = Color(0xFF3B82F6)) {
            SliderControl(
                label = stringResource(R.string.width),
                value = state.width,
                valueRange = 10f..60f,
                valueDisplay = "${state.width.toInt()}dp",
                borderColor = Color(0xFF3B82F6),
                onValueChange = { state.width = it }
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            SliderControl(
                label = stringResource(R.string.height),
                value = state.height,
                valueRange = 30f..200f,
                valueDisplay = "${state.height.toInt()}dp",
                borderColor = Color(0xFF3B82F6),
                onValueChange = { state.height = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Background Section
        SectionTitle(stringResource(R.string.background_uppercase), Color(0xFF10B981))
        CustomizationCard(borderColor = Color(0xFF10B981)) {
            ColorPickerControl(
                label = stringResource(R.string.color),
                color = state.bgColor,
                borderColor = Color(0xFF10B981),
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
                borderColor = Color(0xFF10B981),
                onValueChange = { state.bgAlpha = it.toInt() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stroke Section
        SectionTitle(stringResource(R.string.stroke_uppercase), Color(0xFFF59E0B))
        CustomizationCard(borderColor = Color(0xFFF59E0B)) {
            ColorPickerControl(
                label = stringResource(R.string.color),
                color = state.strokeColor,
                borderColor = Color(0xFFF59E0B),
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
                borderColor = Color(0xFFF59E0B),
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
                borderColor = Color(0xFFF59E0B),
                onValueChange = { state.strokeAlpha = it.toInt() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Corner Radius Section
        SectionTitle(stringResource(R.string.corner_radius_uppercase), Color(0xFFEC4899))
        CustomizationCard(borderColor = Color(0xFFEC4899)) {
            SliderControl(
                label = stringResource(R.string.all_corners),
                value = state.cornerRadiusAll,
                valueRange = 0f..50f,
                valueDisplay = "${state.cornerRadiusAll.toInt()}dp",
                borderColor = Color(0xFFEC4899),
                onValueChange = { value ->
                    state.cornerRadiusAll = value
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
            SliderControl(
                label = stringResource(R.string.top_left),
                value = state.cornerTL,
                valueRange = 0f..50f,
                valueDisplay = "${state.cornerTL.toInt()}dp",
                borderColor = Color(0xFFEC4899),
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
                borderColor = Color(0xFFEC4899),
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
                borderColor = Color(0xFFEC4899),
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
                borderColor = Color(0xFFEC4899),
                onValueChange = { state.cornerBR = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Icon Section
        SectionTitle(stringResource(R.string.icon_settings), Color(0xFF06B6D4))
        CustomizationCard(borderColor = Color(0xFF06B6D4)) {
            SwitchControl(
                label = stringResource(R.string.show_icon),
                checked = state.showIcon,
                borderColor = Color(0xFF06B6D4),
                onCheckedChange = { state.showIcon = it }
            )

            if (state.showIcon) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
                IconPickerControl(
                    label = stringResource(R.string.icon),
                    selectedIconRes = state.iconRes,
                    borderColor = Color(0xFF06B6D4),
                    onClick = { onShowIconPicker() }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
                ColorPickerControl(
                    label = stringResource(R.string.icon_color),
                    color = state.iconColor,
                    borderColor = Color(0xFF06B6D4),
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
                    borderColor = Color(0xFF06B6D4),
                    onValueChange = { state.iconSize = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Behavior Section
        SectionTitle(stringResource(R.string.behavior_uppercase), Color(0xFFEF4444))
        CustomizationCard(borderColor = Color(0xFFEF4444)) {
            SwitchControl(
                label = stringResource(R.string.vibrate_on_click),
                checked = state.vibrate,
                borderColor = Color(0xFFEF4444),
                onCheckedChange = { state.vibrate = it }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
