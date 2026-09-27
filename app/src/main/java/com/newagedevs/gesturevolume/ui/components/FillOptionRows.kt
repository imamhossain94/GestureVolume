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
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.screens.handler_action.SettingSwitchItem
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SliderControl
import com.newagedevs.gesturevolume.utils.EffortFill
import com.newagedevs.gesturevolume.utils.GlimmerFill
import com.newagedevs.gesturevolume.utils.LevelFeedback
import com.newagedevs.gesturevolume.utils.PixelFill
import com.newagedevs.gesturevolume.utils.ShaderFill
import com.newagedevs.gesturevolume.utils.SliderFill
import com.newagedevs.gesturevolume.utils.SurgeFill
import java.text.NumberFormat

/*
 * The choices inside the fills that have them — the Pixels grid's pattern, the shader's effect, the
 * surge's look, the Effort picker's look, the Glimmer's handle and stops — as rows of the same live tiles as the
 * fills themselves: each tile the fill running with that one choice changed and everything else as
 * the user has it. A pattern's name says as little as a fill's did.
 */

/** A row's heading, and the row under it. */
@Composable
private fun OptionRow(@StringRes title: Int, content: @Composable () -> Unit) {
    Column {
        Text(
            text = stringResource(title),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        content()
    }
}

/** Which pattern runs through the Pixels grid. */
@Composable
fun PixelPatternRow(style: PixelFill.Style, look: FillTileLook, onChange: (PixelFill.Style) -> Unit) {
    OptionRow(R.string.pixel_pattern) {
        PictureRow(
            items = PixelFill.ALL,
            selected = PixelFill.sanitize(style.pattern),
            onSelect = { onChange(style.copy(pattern = it)) },
            label = { stringResource(pixelPatternLabel(it)) },
            tileWidth = FILL_TILE_WIDTH,
        ) { id, _ -> FillTile(SliderFill.PIXELS, look.copy(pixelStyle = style.copy(pattern = id))) }
    }
}

/** Which effect the shader runs. */
@Composable
fun ShaderEffectRow(style: ShaderFill.Style, look: FillTileLook, onChange: (ShaderFill.Style) -> Unit) {
    OptionRow(R.string.shader_effect) {
        PictureRow(
            items = ShaderFill.ALL,
            selected = ShaderFill.sanitize(style.effect),
            onSelect = { onChange(style.copy(effect = it)) },
            label = { stringResource(shaderEffectLabel(it)) },
            tileWidth = FILL_TILE_WIDTH,
        ) { id, _ -> FillTile(SliderFill.SHADER, look.copy(shaderStyle = style.copy(effect = id))) }
    }
}

/** Which look the surge's front has. */
@Composable
fun SurgeLookRow(style: SurgeFill.Style, look: FillTileLook, onChange: (SurgeFill.Style) -> Unit) {
    OptionRow(R.string.surge_look) {
        PictureRow(
            items = SurgeFill.LOOKS,
            selected = SurgeFill.sanitize(style.look),
            onSelect = { onChange(style.copy(look = it)) },
            label = { stringResource(surgeLookLabel(it)) },
            tileWidth = FILL_TILE_WIDTH,
        ) { id, _ -> FillTile(SliderFill.SURGE, look.copy(surgeStyle = style.copy(look = id))) }
    }
}

/** Steps or dots, for the Effort picker. */
@Composable
fun EffortLookRow(style: EffortFill.Style, look: FillTileLook, onChange: (EffortFill.Style) -> Unit) {
    OptionRow(R.string.effort_look) {
        PictureRow(
            items = EffortFill.LOOKS,
            selected = EffortFill.sanitize(style.look),
            onSelect = { onChange(style.copy(look = it)) },
            label = { stringResource(effortLookLabel(it)) },
            tileWidth = FILL_TILE_WIDTH,
        ) { id, _ -> FillTile(SliderFill.EFFORT, look.copy(effortStyle = style.copy(look = id))) }
    }
}

/** The Glimmer's two switches, as the four looks they make between them. */
enum class GlimmerLook(val handle: Boolean, val stops: Boolean, @StringRes val label: Int) {
    BOTH(true, true, R.string.glimmer_look_both),
    HANDLE(true, false, R.string.glimmer_look_handle),
    STOPS(false, true, R.string.glimmer_look_stops),
    PLAIN(false, false, R.string.glimmer_look_plain);

    companion object {
        fun of(style: GlimmerFill.Style): GlimmerLook =
            entries.first { it.handle == style.handle && it.stops == style.stops }
    }
}

/** With a handle, with stops, with both or with neither, for the Glimmer. */
@Composable
fun GlimmerLookRow(style: GlimmerFill.Style, look: FillTileLook, onChange: (GlimmerFill.Style) -> Unit) {
    OptionRow(R.string.glimmer_look) {
        PictureRow(
            items = GlimmerLook.entries,
            selected = GlimmerLook.of(style),
            onSelect = { onChange(style.copy(handle = it.handle, stops = it.stops)) },
            label = { stringResource(it.label) },
            tileWidth = FILL_TILE_WIDTH,
        ) { choice, _ ->
            FillTile(SliderFill.GLIMMER, look.copy(glimmerStyle = style.copy(handle = choice.handle, stops = choice.stops)))
        }
    }
}

/**
 * How the fill answers the level — see [LevelFeedback] — for every fill but the Effort picker,
 * which answers it in its own way: livelier as it rises, a glow when low, a flourish at the top.
 *
 * @param showSpeed for the fills without a speed of their own; the Pixels grid, the shaders and the
 *   glimmer keep theirs, above.
 */
@Composable
fun LevelFeedbackControls(
    style: LevelFeedback.Style,
    accent: Color,
    showSpeed: Boolean,
    onChange: (LevelFeedback.Style) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val decimals = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.level_feedback_title),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.level_feedback_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        if (showSpeed) {
            SliderControl(
                label = stringResource(R.string.pixel_speed),
                value = style.speed,
                valueRange = LevelFeedback.MIN_SPEED..LevelFeedback.MAX_SPEED,
                valueDisplay = stringResource(R.string.pixel_speed_value, decimals.format(style.speed)),
                borderColor = accent,
                step = 0.25f,
                onValueChange = { onChange(style.copy(speed = it)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        SettingSwitchItem(
            title = stringResource(R.string.level_feedback_follow),
            description = stringResource(R.string.level_feedback_follow_desc),
            checked = style.follow,
            onCheckedChange = { onChange(style.copy(follow = it)) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchItem(
            title = stringResource(R.string.level_feedback_low),
            description = stringResource(R.string.level_feedback_low_desc),
            checked = style.low,
            onCheckedChange = { onChange(style.copy(low = it)) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchItem(
            title = stringResource(R.string.level_feedback_full),
            description = stringResource(R.string.level_feedback_full_desc),
            checked = style.full,
            onCheckedChange = { onChange(style.copy(full = it)) },
        )
    }
}
