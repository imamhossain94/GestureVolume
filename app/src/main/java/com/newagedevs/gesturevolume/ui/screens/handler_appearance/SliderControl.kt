package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.components.RepeatingIconButton
import kotlin.math.abs
import kotlin.math.round

/**
 * One labelled value with a slim track and two nudge buttons.
 *
 * **Why it is thin.** Almost every slider in this app sets a dp measurement on something the user
 * is looking at while they drag — the bar's width, its corner radius, the track's thickness. The
 * control is furniture around that; the subject is the preview. Material's default slider is 20dp
 * of track and a 20dp thumb, and half a dozen of them stacked reads as a machine console. Four dp
 * of track with a capsule thumb reads as a measurement, which is what it is.
 *
 * Slim is only about what is *drawn*. The row is [ROW_HEIGHT] tall and the whole of it is the
 * drag target, so nothing here is any harder to hit than it was.
 *
 * **Why the nudge buttons.** A 10..200dp range across a phone's width is roughly 2.5dp per pixel
 * of finger travel, so a slider alone cannot express "one more" — and the values people actually
 * hunt for are exact ones: 0dp corners, a 1dp stroke, 24dp to match an icon. Holding a button
 * repeats, so a long haul is still one gesture; tapping it moves exactly one [step]. Between them
 * the coarse and the fine adjustment stop competing for the same finger movement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderControl(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueDisplay: String,
    borderColor: Color,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** What one tap of a nudge button is worth, in the slider's own units. */
    step: Float = 1f,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val dragged by interactionSource.collectIsDraggedAsState()

    fun nudge(direction: Int) {
        // Snapped to the step grid before moving, so a value left on 23.4 by a drag lands on 24
        // and then 25 rather than carrying the fraction along for the rest of the session.
        val snapped = round(value / step) * step
        val next = if (abs(snapped - value) > step * 0.01f && (snapped - value) * direction > 0f) {
            snapped
        } else {
            snapped + step * direction
        }
        onValueChange(next.coerceIn(valueRange.start, valueRange.endInclusive))
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )

            RepeatingIconButton(
                onClick = { nudge(-1) },
                enabled = value > valueRange.start,
                contentDescription = stringResource(R.string.decrease_value, label),
                tint = borderColor,
            ) {
                Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
            }

            Text(
                text = valueDisplay,
                // Wide enough for the longest reading each control produces, so the number does
                // not shove the two buttons sideways every time it crosses a digit.
                modifier = Modifier.widthIn(min = 48.dp),
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = borderColor,
            )

            RepeatingIconButton(
                onClick = { nudge(1) },
                enabled = value < valueRange.endInclusive,
                contentDescription = stringResource(R.string.increase_value, label),
                tint = borderColor,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }

        val fraction = if (valueRange.endInclusive > valueRange.start) {
            ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
                .coerceIn(0f, 1f)
        } else {
            0f
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT),
            thumb = {
                // Drawn inside the track's own composable, from the same fraction, rather than
                // here — see SlimTrack. This slot is left empty rather than removed because
                // Slider still measures it, and an empty Box measures to nothing.
                Box(Modifier)
            },
            track = {
                SlimTrack(
                    fraction = fraction,
                    color = borderColor,
                    active = dragged,
                )
            },
        )
    }
}

/** The whole control's height, and therefore its drag target. Slim to look at, not to hit. */
private val ROW_HEIGHT = 28.dp

/** Thickness of the line. The measurement, not the machinery. */
private val TRACK_HEIGHT = 4.dp

/** The thumb: a capsule, so it reads as a position on a scale rather than a grabbable knob. */
private val THUMB_WIDTH = 4.dp
private val THUMB_HEIGHT = 18.dp

/**
 * Track and thumb in one canvas.
 *
 * They are drawn together because the thumb has to sit at exactly the fraction the fill ends at,
 * and Material's own layout reaches that agreement by insetting the track by half a thumb width
 * and offsetting the thumb by the same amount. With a 4dp thumb that inset is 2dp, which is not
 * worth two composables and a shared constant to get right — one canvas simply puts them in the
 * same coordinate space.
 */
@Composable
private fun SlimTrack(fraction: Float, color: Color, active: Boolean) {
    val inactive = color.copy(alpha = 0.18f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .clip(CircleShape)
    ) {
        val trackH = TRACK_HEIGHT.toPx()
        val thumbW = THUMB_WIDTH.toPx()
        // The thumb's centre never leaves the track, so the ends of the range are reachable and
        // the capsule does not hang over the edge at 0 and 100%.
        val usable = size.width - thumbW
        val cx = thumbW / 2f + usable * fraction
        val cy = size.height / 2f
        val radius = CornerRadius(trackH / 2f, trackH / 2f)

        drawRoundRect(
            color = inactive,
            topLeft = Offset(0f, cy - trackH / 2f),
            size = Size(size.width, trackH),
            cornerRadius = radius,
        )
        if (cx > 0f) {
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, cy - trackH / 2f),
                size = Size(cx, trackH),
                cornerRadius = radius,
            )
        }

        // Grows only while the finger is on it. A thumb that is always large is back to being a
        // knob; one that swells under the finger confirms the grab without adding any furniture
        // to the five sliders the user is not touching.
        val thumbH = (if (active) THUMB_HEIGHT * 1.25f else THUMB_HEIGHT).toPx()
        val thumbWidth = if (active) thumbW * 1.5f else thumbW
        drawRoundRect(
            color = color,
            topLeft = Offset(cx - thumbWidth / 2f, cy - thumbH / 2f),
            size = Size(thumbWidth, thumbH),
            cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f),
        )
    }
}
