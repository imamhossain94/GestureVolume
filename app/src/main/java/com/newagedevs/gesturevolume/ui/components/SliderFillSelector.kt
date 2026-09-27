package com.newagedevs.gesturevolume.ui.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.QuickSliderStore
import com.newagedevs.gesturevolume.ui.view.QuickSliderView
import com.newagedevs.gesturevolume.utils.EffortFill
import com.newagedevs.gesturevolume.utils.GlimmerFill
import com.newagedevs.gesturevolume.utils.LevelFeedback
import com.newagedevs.gesturevolume.utils.PixelFill
import com.newagedevs.gesturevolume.utils.ShaderFill
import com.newagedevs.gesturevolume.utils.SliderFill
import com.newagedevs.gesturevolume.utils.SurgeFill

/**
 * Picks what the Quick panel's fill does while it sits there.
 *
 * One row of tiles, each a small panel running the fill for real — the Quick panel's own view, in
 * the user's own colours — over its name: a wall of forty names was a wall of words, and most of
 * them ("Silk", "Rune", "Sonar") do not say what they look like. The preview above runs the one
 * picked, full size. See [PictureRow].
 *
 * [look] carries the settings a fill has of its own, so a tile shows the Pixels grid, the shader
 * and the effort picker as they are set up, not as they would be out of the box.
 */
@Composable
fun SliderFillSelector(
    style: String,
    onStyleChange: (String) -> Unit,
    look: FillTileLook,
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
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        // Shaders and the surge only where the system runs an app's own: Android 13 and later.
        val fills = remember {
            SliderFill.ALL.filter {
                (it != SliderFill.SHADER && it != SliderFill.SURGE) || Build.VERSION.SDK_INT >= ShaderFill.MIN_SDK
            }
        }
        PictureRow(
            items = fills,
            selected = SliderFill.sanitize(style),
            onSelect = onStyleChange,
            label = { stringResource(sliderFillLabel(it)) },
            tileWidth = FILL_TILE_WIDTH,
        ) { id, _ -> FillTile(id, look) }
    }
}

/**
 * Everything a tile's little panel is dressed in: the panel's colours, the animation's, and every
 * fill's own settings — so a row of tiles can vary one of them, a pattern or an effect, and keep
 * the rest as the user has them.
 */
data class FillTileLook(
    val trackColor: Int = QuickSliderStore.DEFAULT_TRACK_COLOR,
    val fillColor: Int = QuickSliderStore.DEFAULT_FILL_COLOR,
    /** The user's animation colours, or null for each fill's own. */
    val fillColors: IntArray? = null,
    val pixelStyle: PixelFill.Style = PixelFill.Style(),
    val shaderStyle: ShaderFill.Style = ShaderFill.Style(),
    val surgeStyle: SurgeFill.Style = SurgeFill.Style(),
    val effortStyle: EffortFill.Style = EffortFill.Style(),
    val glimmerStyle: GlimmerFill.Style = GlimmerFill.Style(),
    val feedback: LevelFeedback.Style = LevelFeedback.Style(),
)

/**
 * A tile's picture: the wallpaper, and [id] running on a small panel over it in [look], filled to
 * [level]. [showcase], for a panel at the top that keeps reaching it: see [MiniFill].
 */
@Composable
internal fun BoxScope.FillTile(id: String, look: FillTileLook, level: Float = MINI_LEVEL, showcase: Boolean = false) {
    TileWallpaper()
    MiniFill(
        id = id,
        look = look,
        level = level,
        showcase = showcase,
        modifier = Modifier
            .align(Alignment.Center)
            .size(width = 24.dp, height = 70.dp),
    )
}

/**
 * [id] running on a small Quick panel: the real view, with no number and no icon, by default at a
 * level that shows both the lit part and the track above it.
 *
 * @param showcase the top reached again every few seconds, for a panel held at it: so a tile of the
 *   flourish at 100% shows it arriving as well as staying. See `QuickSliderView.setFeedbackShowcase`.
 */
@Composable
internal fun MiniFill(
    id: String,
    look: FillTileLook,
    modifier: Modifier = Modifier,
    level: Float = MINI_LEVEL,
    showcase: Boolean = false,
) {
    AndroidView(
        factory = { context ->
            QuickSliderView(context).apply {
                setShowValue(false)
                setIcon(null)
                setCornerRadiusDp(12f)
            }
        },
        update = { view ->
            view.setColors(look.trackColor, look.fillColor)
            // Before the style, as the panel does, so a Pixels fill starts at its own pace.
            view.setPixelStyle(look.pixelStyle)
            view.setShaderStyle(look.shaderStyle)
            view.setSurgeStyle(look.surgeStyle)
            view.setEffortStyle(look.effortStyle)
            view.setGlimmerStyle(look.glimmerStyle)
            view.setLevelFeedback(look.feedback)
            view.setFillStyle(id)
            view.setFillColors(look.fillColors)
            view.setExpansion(1f)
            view.setCommitted()
            view.setFeedbackShowcase(showcase)
            view.setValue(level)
        },
        modifier = modifier,
    )
}

/** Where a tile's panel is filled to: enough to see the fill, with some track left above it. */
private const val MINI_LEVEL = 0.62f

/** A fill's tile, narrower than the others: the panel in it is narrow, and there are forty of them. */
internal val FILL_TILE_WIDTH = 68.dp

@Composable
internal fun FillChip(label: String, selected: Boolean, onClick: () -> Unit) =
    ChoiceChip(label = label, selected = selected, onClick = onClick)

/** The translated name of a fill animation, for the summaries that name the one chosen. */
fun sliderFillLabel(id: String): Int = when (id) {
    SliderFill.LIQUID -> R.string.fill_liquid
    SliderFill.WAVEFORM -> R.string.fill_waveform
    SliderFill.SUNRISE -> R.string.fill_sunrise
    SliderFill.SPECTRUM -> R.string.fill_spectrum
    SliderFill.GALAXY -> R.string.fill_galaxy
    SliderFill.SILK -> R.string.fill_silk
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
    SliderFill.FIREFLIES -> R.string.fill_fireflies
    SliderFill.SNOWFALL -> R.string.fill_snowfall
    SliderFill.HEARTBEAT -> R.string.fill_heartbeat
    SliderFill.NEON -> R.string.fill_neon
    SliderFill.OCEAN -> R.string.fill_ocean
    SliderFill.GRADIENT -> R.string.fill_gradient
    SliderFill.CONFETTI -> R.string.fill_confetti
    SliderFill.WARP -> R.string.fill_warp
    SliderFill.STORM -> R.string.fill_storm
    SliderFill.FIREWORKS -> R.string.fill_fireworks
    SliderFill.PIXELS -> R.string.fill_pixels
    SliderFill.SHADER -> R.string.fill_shader
    SliderFill.SURGE -> R.string.fill_surge
    SliderFill.EFFORT -> R.string.fill_effort
    SliderFill.GLIMMER -> R.string.fill_glimmer
    else -> R.string.fill_solid
}
