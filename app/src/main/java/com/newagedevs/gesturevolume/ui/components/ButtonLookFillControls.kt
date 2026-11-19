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
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SliderControl
import com.newagedevs.gesturevolume.utils.ButtonLookFill
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * The translated name of one of the button looks: the button animation's own names for them, since
 * they are its looks.
 */
fun buttonLookLabel(id: String): Int = when (ButtonLookFill.sanitize(id)) {
    ButtonLookFill.NEON -> R.string.bump_style_neon
    ButtonLookFill.RIPPLE -> R.string.bump_style_ripple
    ButtonLookFill.LAVA -> R.string.bump_style_lava
    ButtonLookFill.SPECTRUM -> R.string.bump_style_spectrum
    ButtonLookFill.ELASTIC -> R.string.bump_style_elastic
    ButtonLookFill.HEARTBEAT -> R.string.bump_style_heartbeat
    ButtonLookFill.GLASS -> R.string.bump_style_glass
    ButtonLookFill.AURORA -> R.string.bump_style_aurora
    ButtonLookFill.JELLY -> R.string.bump_style_jelly
    else -> R.string.bump_style_line
}

/**
 * The Button looks fill's own settings, shown under the fill row while it is the one chosen: which
 * look, as a row of live tiles, how fast it moves, and how hard it answers each step of the level.
 * Its colours are the animation colours further down, as for every fill.
 *
 * Every change is handed up at once, like the glimmer's: the preview above runs the real panel,
 * stepping through the levels, so a look's answer to a step is seen as it is tuned.
 */
@Composable
fun ButtonLookFillControls(
    style: ButtonLookFill.Style,
    accent: Color,
    onChange: (ButtonLookFill.Style) -> Unit,
    /** How the tiles in its row are dressed: the panel as the user has it. */
    look: FillTileLook,
    modifier: Modifier = Modifier,
    /**
     * A change is complete — a slider let go, a look picked. Where a caller writes the setting, so
     * a drag is written once rather than every frame. See SliderControl.
     */
    onChangeFinished: (() -> Unit)? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    val decimals = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.button_look_settings_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        // A pick is complete as it is made; a slider's change only once it is let go.
        ButtonLookRow(style = style, look = look, onChange = { onChange(it); onChangeFinished?.invoke() })
        Spacer(modifier = Modifier.height(16.dp))
        SliderControl(
            label = stringResource(R.string.button_look_speed),
            value = style.speed,
            valueRange = ButtonLookFill.MIN_SPEED..ButtonLookFill.MAX_SPEED,
            valueDisplay = stringResource(R.string.pixel_speed_value, decimals.format(style.speed)),
            borderColor = accent,
            step = 0.25f,
            onValueChange = { onChange(style.copy(speed = it)) },
            onValueChangeFinished = onChangeFinished,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SliderControl(
            label = stringResource(R.string.button_look_bounce),
            value = style.bounce * 100f,
            valueRange = 0f..100f,
            valueDisplay = stringResource(R.string.pixel_percent_value, (style.bounce * 100f).roundToInt()),
            borderColor = accent,
            onValueChange = { onChange(style.copy(bounce = (it / 100f).coerceIn(0f, 1f))) },
            onValueChangeFinished = onChangeFinished,
        )
        Text(
            text = stringResource(R.string.button_look_bounce_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
