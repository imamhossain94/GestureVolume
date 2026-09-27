package com.newagedevs.gesturevolume.ui.components

import androidx.annotation.StringRes
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
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SliderControl
import com.newagedevs.gesturevolume.utils.SurgeFill
import java.text.NumberFormat
import kotlin.math.roundToInt

/** The translated name of a Surge look. */
fun surgeLookLabel(id: String): Int = when (SurgeFill.sanitize(id)) {
    SurgeFill.ELECTRIC -> R.string.surge_look_electric
    SurgeFill.HONEYCOMB -> R.string.surge_look_honeycomb
    SurgeFill.STREAKS -> R.string.surge_look_streaks
    SurgeFill.BLOCKS -> R.string.surge_look_blocks
    SurgeFill.FLAME -> R.string.surge_look_flame
    else -> R.string.surge_look_curve
}

/**
 * The Surge fill's own settings, shown under the fill row while it is the one chosen: which look,
 * as a row of live tiles, and six sliders every look answers to — its speed, the size of its
 * features, how uneven its front is, the glow on it, how far its light trails back, and how much of
 * it shows above the level. Its colours are the animation colours further down, as for every fill.
 *
 * Every change is handed up at once, as with the shader's: the preview above runs the real program,
 * so a slider is tuned by watching it.
 */
@Composable
fun SurgeFillControls(
    style: SurgeFill.Style,
    accent: Color,
    onChange: (SurgeFill.Style) -> Unit,
    /** How the tiles in its row are dressed: the panel as the user has it. */
    look: FillTileLook,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val decimals = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 2
    }

    Column(modifier = modifier) {
        Hint(R.string.surge_settings_desc)
        SurgeLookRow(style = style, look = look, onChange = onChange)

        Spacer(modifier = Modifier.height(16.dp))
        SliderControl(
            label = stringResource(R.string.surge_speed),
            value = style.speed,
            valueRange = 0f..SurgeFill.MAX_SPEED,
            valueDisplay = if (style.speed <= 0f) {
                stringResource(R.string.shader_speed_still)
            } else {
                stringResource(R.string.pixel_speed_value, decimals.format(style.speed))
            },
            borderColor = accent,
            step = 0.1f,
            onValueChange = { onChange(style.copy(speed = it)) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        SliderControl(
            label = stringResource(R.string.surge_size),
            value = style.size,
            valueRange = SurgeFill.MIN_SIZE..SurgeFill.MAX_SIZE,
            valueDisplay = stringResource(R.string.pixel_speed_value, decimals.format(style.size)),
            borderColor = accent,
            step = 0.1f,
            onValueChange = { onChange(style.copy(size = it)) },
        )
        Hint(R.string.surge_size_desc)
        Spacer(modifier = Modifier.height(12.dp))
        Percent(R.string.surge_edge, style.edge, 0f, 1f, accent) { onChange(style.copy(edge = it)) }
        Hint(R.string.surge_edge_desc)
        Spacer(modifier = Modifier.height(12.dp))
        Percent(R.string.surge_glow, style.glow, 0f, 1f, accent) { onChange(style.copy(glow = it)) }
        Spacer(modifier = Modifier.height(12.dp))
        Percent(R.string.surge_trail, style.trail, 0f, 1f, accent) { onChange(style.copy(trail = it)) }
        Hint(R.string.surge_trail_desc)
        Spacer(modifier = Modifier.height(12.dp))
        Percent(R.string.surge_rest, style.rest, 0f, SurgeFill.MAX_REST, accent) { onChange(style.copy(rest = it)) }
    }
}

/** A line saying what the control above it does. */
@Composable
private fun Hint(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
    )
}

/** A [min]..[max] fraction, shown and nudged as whole percentages. */
@Composable
private fun Percent(@StringRes label: Int, value: Float, min: Float, max: Float, accent: Color, onChange: (Float) -> Unit) {
    SliderControl(
        label = stringResource(label),
        value = value * 100f,
        valueRange = min * 100f..max * 100f,
        valueDisplay = stringResource(R.string.pixel_percent_value, (value * 100f).roundToInt()),
        borderColor = accent,
        onValueChange = { onChange((it / 100f).coerceIn(min, max)) },
    )
}
