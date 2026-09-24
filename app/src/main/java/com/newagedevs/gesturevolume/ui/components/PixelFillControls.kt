package com.newagedevs.gesturevolume.ui.components

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
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SliderControl
import com.newagedevs.gesturevolume.utils.PixelFill
import java.text.NumberFormat
import kotlin.math.roundToInt

/** The translated name of a Pixels pattern. */
fun pixelPatternLabel(id: String): Int = when (PixelFill.sanitize(id)) {
    PixelFill.STEADY -> R.string.pixel_pattern_steady
    PixelFill.BREATHE -> R.string.pixel_pattern_breathe
    PixelFill.SWEEP -> R.string.pixel_pattern_sweep
    PixelFill.PENDULUM -> R.string.pixel_pattern_pendulum
    PixelFill.SNAKE -> R.string.pixel_pattern_snake
    PixelFill.RAIN -> R.string.pixel_pattern_rain
    PixelFill.TWINKLE -> R.string.pixel_pattern_twinkle
    PixelFill.CHECKERS -> R.string.pixel_pattern_checkers
    PixelFill.METER -> R.string.pixel_pattern_meter
    PixelFill.RAINBOW -> R.string.pixel_pattern_rainbow
    PixelFill.AURORA -> R.string.pixel_pattern_aurora
    PixelFill.PLASMA -> R.string.pixel_pattern_plasma
    PixelFill.EMBERS -> R.string.pixel_pattern_embers
    PixelFill.THERMAL -> R.string.pixel_pattern_thermal
    PixelFill.CONFETTI -> R.string.pixel_pattern_confetti
    PixelFill.CHROMATIC -> R.string.pixel_pattern_chromatic
    PixelFill.CANDY -> R.string.pixel_pattern_candy
    PixelFill.RIPPLE -> R.string.pixel_pattern_ripple
    else -> R.string.pixel_pattern_spectrum
}

/**
 * The Pixels fill's own settings, shown under the fill chips while it is the one chosen: which
 * pattern runs through the grid, and six things about the grid itself.
 *
 * Every change is handed up at once — the preview above runs the real panel, so a slider is tuned
 * by watching it rather than by imagining it. Percentages on screen, fractions underneath.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PixelFillControls(
    style: PixelFill.Style,
    accent: Color,
    onChange: (PixelFill.Style) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val decimals = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.pixel_settings_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Text(
            text = stringResource(R.string.pixel_pattern),
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
            PixelFill.ALL.forEach { id ->
                FillChip(
                    label = stringResource(pixelPatternLabel(id)),
                    selected = style.pattern == id,
                    onClick = { onChange(style.copy(pattern = id)) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SliderControl(
            label = stringResource(R.string.pixel_speed),
            value = style.speed,
            valueRange = PixelFill.MIN_SPEED..PixelFill.MAX_SPEED,
            valueDisplay = stringResource(R.string.pixel_speed_value, decimals.format(style.speed)),
            borderColor = accent,
            step = 0.25f,
            onValueChange = { onChange(style.copy(speed = it)) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        SliderControl(
            label = stringResource(R.string.pixel_columns),
            value = style.columns.toFloat(),
            valueRange = PixelFill.MIN_COLUMNS.toFloat()..PixelFill.MAX_COLUMNS.toFloat(),
            valueDisplay = style.columns.toString(),
            borderColor = accent,
            onValueChange = { onChange(style.copy(columns = it.roundToInt())) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        PercentSlider(R.string.pixel_gap, style.gap, PixelFill.MAX_GAP, accent) { onChange(style.copy(gap = it)) }
        Spacer(modifier = Modifier.height(12.dp))
        PercentSlider(R.string.pixel_roundness, style.roundness, 1f, accent) { onChange(style.copy(roundness = it)) }
        Spacer(modifier = Modifier.height(12.dp))
        PercentSlider(R.string.pixel_glow, style.glow, 1f, accent) { onChange(style.copy(glow = it)) }
        Spacer(modifier = Modifier.height(12.dp))
        PercentSlider(R.string.pixel_rest, style.rest, PixelFill.MAX_REST, accent) { onChange(style.copy(rest = it)) }
    }
}

/** A 0..[max] fraction, shown and nudged as whole percentages. */
@Composable
private fun PercentSlider(label: Int, value: Float, max: Float, accent: Color, onChange: (Float) -> Unit) {
    val percent = (value * 100f).roundToInt()
    SliderControl(
        label = stringResource(label),
        value = value * 100f,
        valueRange = 0f..max * 100f,
        valueDisplay = stringResource(R.string.pixel_percent_value, percent),
        borderColor = accent,
        onValueChange = { onChange((it / 100f).coerceIn(0f, max)) },
    )
}
