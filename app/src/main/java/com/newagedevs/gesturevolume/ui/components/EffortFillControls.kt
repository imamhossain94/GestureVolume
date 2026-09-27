package com.newagedevs.gesturevolume.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.screens.handler_action.SettingSwitchItem
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SliderControl
import com.newagedevs.gesturevolume.utils.EffortFill
import java.text.NumberFormat

/** The translated name of an Effort look. */
fun effortLookLabel(id: String): Int = when (EffortFill.sanitize(id)) {
    EffortFill.DOTS -> R.string.effort_look_dots
    else -> R.string.effort_look_steps
}

private fun effortLookDescription(id: String): Int = when (EffortFill.sanitize(id)) {
    EffortFill.DOTS -> R.string.effort_look_dots_desc
    else -> R.string.effort_look_steps_desc
}

/**
 * The Effort fill's own settings, shown under the fill chips while it is the one chosen: its look,
 * how fast it moves, and whether the level is named on the track.
 *
 * Every change is handed up at once, like the Pixels grid's: the preview above runs the real panel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EffortFillControls(
    style: EffortFill.Style,
    accent: Color,
    onChange: (EffortFill.Style) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val decimals = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.effort_settings_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Text(
            text = stringResource(R.string.effort_look),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EffortFill.LOOKS.forEach { id ->
                FillChip(
                    label = stringResource(effortLookLabel(id)),
                    selected = style.look == id,
                    onClick = { onChange(style.copy(look = id)) },
                )
            }
        }
        AnimatedContent(
            targetState = style.look,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "effortLookDescription",
        ) { look ->
            Text(
                text = stringResource(effortLookDescription(look)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        SliderControl(
            label = stringResource(R.string.effort_speed),
            value = style.speed,
            valueRange = EffortFill.MIN_SPEED..EffortFill.MAX_SPEED,
            valueDisplay = stringResource(R.string.pixel_speed_value, decimals.format(style.speed)),
            borderColor = accent,
            step = 0.25f,
            onValueChange = { onChange(style.copy(speed = it)) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchItem(
            title = stringResource(R.string.effort_labels),
            description = stringResource(R.string.effort_labels_desc),
            checked = style.labels,
            onCheckedChange = { onChange(style.copy(labels = it)) },
        )
    }
}
