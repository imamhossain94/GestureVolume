package com.newagedevs.gesturevolume.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.utils.SliderFill

/**
 * Picks what the Quick panel's fill does while it sits there.
 *
 * The preview above this runs the real thing on a loop, so the chips are labels for something the
 * user is already watching rather than descriptions to be imagined.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SliderFillSelector(
    style: String,
    onStyleChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.slider_fill_style),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.slider_fill_style_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SliderFill.ALL.forEach { id ->
                FillChip(
                    label = stringResource(labelFor(id)),
                    selected = style == id,
                    onClick = { onStyleChange(id) },
                )
            }
        }
    }
}

@Composable
private fun FillChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        },
        label = "fillChipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "fillChipContent",
    )
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .background(container)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = content,
    )
}

private fun labelFor(id: String): Int = when (id) {
    SliderFill.TIDE_UP -> R.string.fill_tide_up
    SliderFill.TIDE_DOWN -> R.string.fill_tide_down
    SliderFill.PLASMA -> R.string.fill_plasma
    SliderFill.AURORA -> R.string.fill_aurora
    SliderFill.HOLOGRAM -> R.string.fill_hologram
    SliderFill.EMBER -> R.string.fill_ember
    SliderFill.SONAR -> R.string.fill_sonar
    SliderFill.CIRCUIT -> R.string.fill_circuit
    SliderFill.DOT_MATRIX -> R.string.fill_dot_matrix
    SliderFill.NEBULA -> R.string.fill_nebula
    SliderFill.CYBERPUNK -> R.string.fill_cyberpunk
    SliderFill.MATRIX_RAIN -> R.string.fill_matrix_rain
    SliderFill.RUNE -> R.string.fill_rune
    SliderFill.STRIPES -> R.string.fill_stripes
    else -> R.string.fill_solid
}
