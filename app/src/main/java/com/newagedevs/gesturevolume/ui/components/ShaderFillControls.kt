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
import com.newagedevs.gesturevolume.utils.ShaderFill
import java.text.NumberFormat
import kotlin.math.roundToInt

/** The translated name of a Shaders effect. */
fun shaderEffectLabel(id: String): Int = when (ShaderFill.sanitize(id)) {
    ShaderFill.LIQUID_CHROME -> R.string.shader_effect_liquid_chrome
    ShaderFill.MOLTEN -> R.string.shader_effect_molten
    ShaderFill.AURORA -> R.string.shader_effect_aurora
    ShaderFill.INK_SMOKE -> R.string.shader_effect_ink_smoke
    ShaderFill.PLASMA -> R.string.shader_effect_plasma
    ShaderFill.CAUSTICS -> R.string.shader_effect_caustics
    ShaderFill.SOAP_FILM -> R.string.shader_effect_soap_film
    ShaderFill.MESH -> R.string.shader_effect_mesh
    ShaderFill.CLOUDS -> R.string.shader_effect_clouds
    ShaderFill.SMOKE -> R.string.shader_effect_smoke
    ShaderFill.STARFIELD -> R.string.shader_effect_starfield
    else -> R.string.shader_effect_lava_lamp
}

/** The name of the slider that tunes what is particular to an effect. */
private fun detailLabel(detail: ShaderFill.Detail): Int = when (detail) {
    ShaderFill.Detail.WARP -> R.string.shader_detail_warp
    ShaderFill.Detail.FLOW -> R.string.shader_detail_flow
    ShaderFill.Detail.SWAY -> R.string.shader_detail_sway
    ShaderFill.Detail.DISTORTION -> R.string.shader_detail_distortion
    ShaderFill.Detail.SOFTNESS -> R.string.shader_detail_softness
    ShaderFill.Detail.THICKNESS -> R.string.shader_detail_thickness
    ShaderFill.Detail.DRIFT -> R.string.shader_detail_drift
    ShaderFill.Detail.BILLOW -> R.string.shader_detail_billow
    ShaderFill.Detail.SWIRL -> R.string.shader_detail_swirl
    ShaderFill.Detail.DENSITY -> R.string.shader_detail_density
    ShaderFill.Detail.BLOBS -> R.string.shader_detail_blobs
}

/**
 * The Shaders fill's own settings, shown under the fill chips while it is the one chosen: which
 * effect, and six sliders every effect answers to — the third named for what it does in that one.
 *
 * Every change is handed up at once, as with the Pixels grid: the preview above runs the real
 * program, so a slider is tuned by watching it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShaderFillControls(
    style: ShaderFill.Style,
    accent: Color,
    onChange: (ShaderFill.Style) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val decimals = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 2
    }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.shader_settings_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Text(
            text = stringResource(R.string.shader_effect),
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
            ShaderFill.ALL.forEach { id ->
                FillChip(
                    label = stringResource(shaderEffectLabel(id)),
                    selected = style.effect == id,
                    onClick = { onChange(style.copy(effect = id)) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SliderControl(
            label = stringResource(R.string.shader_speed),
            value = style.speed,
            valueRange = 0f..ShaderFill.MAX_SPEED,
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
            label = stringResource(R.string.shader_scale),
            value = style.scale,
            valueRange = ShaderFill.MIN_SCALE..ShaderFill.MAX_SCALE,
            valueDisplay = stringResource(R.string.pixel_speed_value, decimals.format(style.scale)),
            borderColor = accent,
            step = 0.1f,
            onValueChange = { onChange(style.copy(scale = it)) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Percent(detailLabel(ShaderFill.detail(style.effect)), style.detail, 0f, 1f, accent) {
            onChange(style.copy(detail = it))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Percent(R.string.shader_brightness, style.brightness, ShaderFill.MIN_BRIGHTNESS, ShaderFill.MAX_BRIGHTNESS, accent) {
            onChange(style.copy(brightness = it))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Percent(R.string.shader_grain, style.grain, 0f, 1f, accent) { onChange(style.copy(grain = it)) }
        Spacer(modifier = Modifier.height(12.dp))
        Percent(R.string.shader_rest, style.rest, 0f, ShaderFill.MAX_REST, accent) { onChange(style.copy(rest = it)) }
    }
}

/** A [min]..[max] fraction, shown and nudged as whole percentages. */
@Composable
private fun Percent(label: Int, value: Float, min: Float, max: Float, accent: Color, onChange: (Float) -> Unit) {
    SliderControl(
        label = stringResource(label),
        value = value * 100f,
        valueRange = min * 100f..max * 100f,
        valueDisplay = stringResource(R.string.pixel_percent_value, (value * 100f).roundToInt()),
        borderColor = accent,
        onValueChange = { onChange((it / 100f).coerceIn(min, max)) },
    )
}
