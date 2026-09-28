package com.newagedevs.gesturevolume.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SliderControl
import com.newagedevs.gesturevolume.utils.PanelAnimation

/**
 * Picks how the panels arrive, from any screen that shows one: the Quick panel's, the Deck's and the
 * long-press menu's, which all open with the same choice, and all pick it here, the same way.
 *
 * One row of tiles, each a little panel beside a bar playing its entrance, slowed so it can be seen
 * at that size, over and over: "Blinds" and "Tide" are not words anybody can picture, and a grid of
 * sixteen of them was a wall of text. The finger walks along the row and the preview above plays
 * the one picked, full size and at full speed. See [PictureRow].
 */
@Composable
fun PanelAnimationSelector(
    animation: String,
    onAnimationChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** How fast they run, as a multiple of the catalogue's own timing. */
    speed: Float = 1f,
    onSpeedChange: ((Float) -> Unit)? = null,
    /** The heading and the line under it, for a screen that says more about its own panel. */
    title: String = stringResource(R.string.panel_animation),
    description: String = stringResource(R.string.panel_animation_desc),
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        val clock = rememberTileClock(OPENING_LOOP_MS)
        PictureRow(
            items = PanelAnimation.ALL,
            selected = PanelAnimation.sanitize(animation),
            onSelect = onAnimationChange,
            label = { stringResource(panelAnimationLabel(it)) },
        ) { id, _ -> OpeningPicture(id, clock) }

        if (onSpeedChange != null) {
            Spacer(modifier = Modifier.height(14.dp))
            SliderControl(
                label = stringResource(R.string.panel_animation_speed),
                // Shown as a multiple rather than in milliseconds: the catalogue's entrances do
                // not all take the same time — a wipe has further to travel than a fade — so one
                // number of milliseconds would be a lie about fifteen of the sixteen.
                value = speed,
                valueRange = PanelAnimation.MIN_SPEED..PanelAnimation.MAX_SPEED,
                valueDisplay = panelAnimationSpeedLabel(speed),
                borderColor = MaterialTheme.colorScheme.primary,
                onValueChange = onSpeedChange,
            )
        }
    }
}

/**
 * A panel beside the bar at the edge of a sliver of phone, arriving the way [id] brings it, on the
 * row's [clock]: its entrance, slowed; a hold; and a fade before the next.
 *
 * The same frames the panels play (see [PanelAnimation.frameAt]), moved by the same amounts scaled
 * to the tile, and read in the draw phase, so the row redraws its pictures and recomposes nothing.
 */
@Composable
private fun BoxScope.OpeningPicture(id: String, clock: State<Float>) {
    TileWallpaper()
    // The bar the panel grows out of, against the right edge.
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 5.dp)
            .size(width = 4.dp, height = 24.dp)
            .background(DeviceArt.Frame, RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp)),
    )
    Column(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 13.dp)
            .size(width = 34.dp, height = 56.dp)
            .graphicsLayer {
                val ms = clock.value
                val f = PanelAnimation.frameAt(id, openingProgress(id, ms), towardLeft = false)
                alpha = f.alpha * openingFade(ms)
                scaleX = f.scaleX
                scaleY = f.scaleY
                translationX = (f.translationX * MINI_TRAVEL).dp.toPx()
                translationY = (f.translationY * MINI_TRAVEL).dp.toPx()
                rotationZ = f.rotationZ
                rotationX = f.rotationX
                rotationY = f.rotationY
                transformOrigin = TransformOrigin(f.originX, f.originY)
                cameraDistance = 10f * density
            }
            // A wipe shows a band of the panel rather than moving it, as the real ones do.
            .drawWithContent {
                val f = PanelAnimation.frameAt(id, openingProgress(id, clock.value), towardLeft = false)
                clipRect(top = size.height * f.revealFrom, bottom = size.height * f.revealTo) {
                    this@drawWithContent.drawContent()
                }
            }
            .clip(RoundedCornerShape(9.dp))
            .background(MiniPanel)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // A few rows of something, so it reads as a panel and not a slab.
        repeat(4) { i ->
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .fillMaxWidth(if (i % 2 == 0) 1f else 0.7f)
                    .background(Color.White.copy(alpha = 0.26f), RoundedCornerShape(3.dp)),
            )
        }
    }
}

/** How far into its entrance a tile is at [ms] into the row's loop, 0..1: slowed to be seen. */
private fun openingProgress(id: String, ms: Float): Float =
    (ms / (PanelAnimation.durationMs(id) * MINI_SLOWDOWN)).coerceIn(0f, 1f)

/** The fade at the end of each loop, so a tile starts its entrance again from nothing. */
private fun openingFade(ms: Float): Float =
    1f - ((ms - (OPENING_LOOP_MS - OPENING_FADE_MS)) / OPENING_FADE_MS).coerceIn(0f, 1f)

/** One turn of a tile: the slowest entrance, slowed, a hold, and the fade. */
private const val OPENING_LOOP_MS = 2400
private const val OPENING_FADE_MS = 260f

/** A tile's entrance is played this many times slower than the real one: at its size, full speed is a blink. */
private const val MINI_SLOWDOWN = 3f

/** A tile's panel is about a fifth of a real one, so it travels about a third as far. */
private const val MINI_TRAVEL = 0.35f

private val MiniPanel = Color(0xEB1D1B2B)

/** The translated name of an entrance, for the summaries that name the one chosen. */
fun panelAnimationLabel(id: String): Int = when (id) {
    PanelAnimation.FADE -> R.string.anim_fade
    PanelAnimation.POP -> R.string.anim_pop
    PanelAnimation.SPRING -> R.string.anim_spring
    PanelAnimation.ZOOM -> R.string.anim_zoom
    PanelAnimation.UNFOLD -> R.string.anim_unfold
    PanelAnimation.EXPAND -> R.string.anim_expand
    PanelAnimation.RISE -> R.string.anim_rise
    PanelAnimation.DROP -> R.string.anim_drop
    PanelAnimation.SLIDE -> R.string.anim_slide
    PanelAnimation.SWING -> R.string.anim_swing
    PanelAnimation.FLIP -> R.string.anim_flip
    PanelAnimation.TILT -> R.string.anim_tilt
    PanelAnimation.BLINDS -> R.string.anim_blinds
    PanelAnimation.TIDE -> R.string.anim_tide
    PanelAnimation.IRIS -> R.string.anim_iris
    else -> R.string.anim_settle
}

/** Unused here, but it keeps the "every id has a label" promise checkable. */
internal val PANEL_ANIMATION_LABELS: List<Int> = PanelAnimation.ALL.map(::panelAnimationLabel)

/** The speed as a multiple, the way the slider shows it and the group summaries repeat it. */
fun panelAnimationSpeedLabel(speed: Float): String = String.format(java.util.Locale.US, "%.1f×", speed)
