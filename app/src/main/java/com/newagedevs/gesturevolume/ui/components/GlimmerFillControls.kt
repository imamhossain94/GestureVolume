package com.newagedevs.gesturevolume.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.screens.handler_action.SettingSwitchItem
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SliderControl
import com.newagedevs.gesturevolume.utils.GlimmerFill
import java.text.NumberFormat

/**
 * The Glimmer fill's own settings, shown under the fill chips while it is the one chosen: how fast
 * it twinkles, and whether it has a handle and stops.
 *
 * Every change is handed up at once, like the Effort fill's: the preview above runs the real panel.
 */
@Composable
fun GlimmerFillControls(
    style: GlimmerFill.Style,
    accent: Color,
    onChange: (GlimmerFill.Style) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val decimals = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.glimmer_settings_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        SliderControl(
            label = stringResource(R.string.glimmer_speed),
            value = style.speed,
            valueRange = GlimmerFill.MIN_SPEED..GlimmerFill.MAX_SPEED,
            valueDisplay = stringResource(R.string.pixel_speed_value, decimals.format(style.speed)),
            borderColor = accent,
            step = 0.25f,
            onValueChange = { onChange(style.copy(speed = it)) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchItem(
            title = stringResource(R.string.glimmer_handle),
            description = stringResource(R.string.glimmer_handle_desc),
            checked = style.handle,
            onCheckedChange = { onChange(style.copy(handle = it)) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchItem(
            title = stringResource(R.string.glimmer_stops),
            description = stringResource(R.string.glimmer_stops_desc),
            checked = style.stops,
            onCheckedChange = { onChange(style.copy(stops = it)) },
        )
    }
}
