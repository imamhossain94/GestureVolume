package com.newagedevs.gesturevolume.ui.screens.visibility

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.components.DeviceArt
import com.newagedevs.gesturevolume.ui.components.PictureRow
import com.newagedevs.gesturevolume.ui.components.TileWallpaper
import com.newagedevs.gesturevolume.ui.components.rememberTileClock
import com.newagedevs.gesturevolume.utils.BarBehaviour

/**
 * Picks how the bar moves out of the keyboard's way, and back, while [BarBehaviour.KEYBOARD_MOVE]
 * is on: one row of tiles, each a keyboard rising under the bar and the bar getting out of its way
 * the way that motion does, on the same curve the live bar follows. See [PictureRow].
 */
@Composable
internal fun KeyboardMotionSelector(
    motion: String,
    onMotionChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.keyboard_motion_title),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.keyboard_motion_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        val clock = rememberTileClock(MOTION_LOOP_MS)
        PictureRow(
            items = BarBehaviour.KEYBOARD_MOTIONS,
            selected = BarBehaviour.sanitizeMotion(motion),
            onSelect = onMotionChange,
            label = { stringResource(keyboardMotionLabel(it)) },
        ) { id, _ -> MotionPicture(id, clock) }
    }
}

/**
 * A sliver of phone with the bar at its right edge: a keyboard comes up over where the bar sat, the
 * bar moves above it as [id] moves it, and when the keyboard goes the bar goes back down.
 *
 * Drawn in the draw phase off the row's [clock], so the tiles redraw and nothing recomposes.
 */
@Composable
private fun BoxScope.MotionPicture(id: String, clock: State<Float>) {
    TileWallpaper()
    Box(
        modifier = Modifier
            .matchParentSize()
            .padding(5.dp)
            .clip(RoundedCornerShape(14.dp))
            .drawBehind {
                val ms = clock.value
                val w = size.width
                val h = size.height

                // A little of an app, so the phone is not empty: two lines of something at the top.
                val line = 4.dp.toPx()
                val inset = 8.dp.toPx()
                drawRoundRect(Content, Offset(inset, 10.dp.toPx()), Size(w * 0.55f, line), CornerRadius(line / 2f))
                drawRoundRect(Content, Offset(inset, 17.dp.toPx()), Size(w * 0.38f, line), CornerRadius(line / 2f))

                // The keyboard, up from the bottom edge.
                val keysHeight = 30.dp.toPx()
                val keysTop = h - keysHeight * keyboardShown(ms)
                if (keysTop < h) {
                    val round = 6.dp.toPx()
                    drawRoundRect(Keys, Offset(0f, keysTop), Size(w, keysHeight + round), CornerRadius(round))
                    val key = 5.dp.toPx()
                    val gap = 2.5.dp.toPx()
                    val across = 6
                    val keyWidth = (w - gap * (across + 1)) / across
                    for (row in 0 until 3) {
                        val y = keysTop + 4.dp.toPx() + row * (key + 2.5.dp.toPx())
                        if (row == 2) {
                            // The space bar, with a key either side.
                            drawRoundRect(Key, Offset(gap, y), Size(keyWidth, key), CornerRadius(1.5.dp.toPx()))
                            drawRoundRect(
                                Key, Offset(gap * 2 + keyWidth, y), Size(w - (gap * 2 + keyWidth) * 2, key),
                                CornerRadius(1.5.dp.toPx()),
                            )
                            drawRoundRect(Key, Offset(w - gap - keyWidth, y), Size(keyWidth, key), CornerRadius(1.5.dp.toPx()))
                        } else {
                            for (i in 0 until across) {
                                drawRoundRect(
                                    Key, Offset(gap + i * (keyWidth + gap), y), Size(keyWidth, key),
                                    CornerRadius(1.5.dp.toPx()),
                                )
                            }
                        }
                    }
                }

                // The bar, against the right edge: where it sits, and above the keyboard.
                val barWidth = 4.5.dp.toPx()
                val barHeight = 20.dp.toPx()
                val low = h - 34.dp.toPx()
                val high = h - keysHeight - 4.dp.toPx() - barHeight
                val (along, alpha) = barAt(id, ms)
                val top = low + (high - low) * along
                drawRoundRect(
                    color = DeviceArt.Frame.copy(alpha = alpha),
                    topLeft = Offset(w - barWidth, top),
                    // Past the edge, so only the corners facing the screen are round.
                    size = Size(barWidth * 2f, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            },
    )
}

/** How far up the keyboard is at [ms] into the loop, 0..1: up, a while, then down. */
private fun keyboardShown(ms: Float): Float {
    fun ease(x: Float) = 1f - (1f - x) * (1f - x)
    val up = ((ms - KEYS_UP_AT) / KEYS_MS).coerceIn(0f, 1f)
    val down = ((ms - KEYS_DOWN_AT) / KEYS_MS).coerceIn(0f, 1f)
    return ease(up) - ease(down)
}

/**
 * Where the bar is at [ms] into the loop, 0 where it sits and 1 above the keyboard, and how much of
 * it shows: [BarBehaviour.motionAt] up as the keyboard comes, and the same back down as it goes.
 */
private fun barAt(motion: String, ms: Float): Pair<Float, Float> {
    val duration = BarBehaviour.motionMs(motion) * MOTION_SLOWDOWN
    val rising = ms < KEYS_DOWN_AT
    val start = if (rising) KEYS_UP_AT else KEYS_DOWN_AT
    if (ms < start) return 0f to 1f
    val t = if (duration <= 0f) 1f else ((ms - start) / duration).coerceIn(0f, 1f)
    val along = BarBehaviour.motionAt(motion, t)
    return (if (rising) along else 1f - along) to BarBehaviour.motionAlphaAt(motion, t)
}

/** The translated name of a keyboard motion. */
internal fun keyboardMotionLabel(motion: String): Int = when (BarBehaviour.sanitizeMotion(motion)) {
    BarBehaviour.MOTION_SPRING -> R.string.motion_spring
    BarBehaviour.MOTION_BOUNCE -> R.string.motion_bounce
    BarBehaviour.MOTION_QUICK -> R.string.motion_quick
    BarBehaviour.MOTION_FADE -> R.string.motion_fade
    BarBehaviour.MOTION_INSTANT -> R.string.motion_instant
    else -> R.string.motion_glide
}

/** One turn of a tile: the keyboard up, a while, down, a while. */
private const val MOTION_LOOP_MS = 3600
private const val KEYS_UP_AT = 300f
private const val KEYS_DOWN_AT = 1900f
private const val KEYS_MS = 260f

/** A tile's motion is played this many times slower than the bar's: at its size, full speed is a blink. */
private const val MOTION_SLOWDOWN = 1.6f

private val Content = Color(0x33FFFFFF)
private val Keys = Color(0xFFDCD9E6)
private val Key = Color(0xFFF8F7FB)
