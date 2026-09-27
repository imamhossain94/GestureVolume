package com.newagedevs.gesturevolume.ui.screens.handler_action

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.AppGestureStore.Slot
import com.newagedevs.gesturevolume.ui.components.ActionIconImage
import com.newagedevs.gesturevolume.ui.viewmodels.MainEvent
import com.newagedevs.gesturevolume.ui.viewmodels.MainState
import com.newagedevs.gesturevolume.utils.ActionIcon
import kotlin.math.cos
import kotlin.math.sin

/*
 * The eight gestures the bar answers, as the Actions screen, its picker and an app's own gestures
 * show them: a short name, a line on how it is done, and a little picture of it on the bar.
 *
 * The slots are AppGestureStore's, which already name all eight for the per-app gestures; the
 * Actions screen's own settings are the same eight, so one list serves both.
 */

/** The gesture's name, short: "Double tap", not "Double tap action". */
@StringRes
fun gestureName(slot: Slot): Int = when (slot) {
    Slot.SINGLE_TAP -> R.string.gesture_name_single_tap
    Slot.DOUBLE_TAP -> R.string.gesture_name_double_tap
    Slot.TRIPLE_TAP -> R.string.gesture_name_triple_tap
    Slot.LONG_PRESS -> R.string.gesture_name_long_press
    Slot.SWIPE_UP -> R.string.gesture_name_swipe_up
    Slot.SWIPE_DOWN -> R.string.gesture_name_swipe_down
    Slot.SWIPE_IN -> R.string.gesture_name_swipe_in
    Slot.SWIPE_OUT -> R.string.gesture_name_swipe_out
}

/** How the gesture is done, in a line. */
@StringRes
fun gestureHint(slot: Slot): Int = when (slot) {
    Slot.SINGLE_TAP -> R.string.gesture_hint_single_tap
    Slot.DOUBLE_TAP -> R.string.gesture_hint_double_tap
    Slot.TRIPLE_TAP -> R.string.gesture_hint_triple_tap
    Slot.LONG_PRESS -> R.string.gesture_hint_long_press
    Slot.SWIPE_UP -> R.string.gesture_hint_swipe_up
    Slot.SWIPE_DOWN -> R.string.gesture_hint_swipe_down
    Slot.SWIPE_IN -> R.string.swipe_in_desc
    Slot.SWIPE_OUT -> R.string.swipe_out_desc
}

val TAP_SLOTS = listOf(Slot.SINGLE_TAP, Slot.DOUBLE_TAP, Slot.TRIPLE_TAP, Slot.LONG_PRESS)

/** What [slot] is set to everywhere, from the screen's state. */
fun actionFor(state: MainState, slot: Slot): String = when (slot) {
    Slot.SINGLE_TAP -> state.clickAction
    Slot.DOUBLE_TAP -> state.doubleClickAction
    Slot.TRIPLE_TAP -> state.tripleClickAction
    Slot.LONG_PRESS -> state.longClickAction
    Slot.SWIPE_UP -> state.swipeUpAction
    Slot.SWIPE_DOWN -> state.swipeDownAction
    Slot.SWIPE_IN -> state.swipeInAction
    Slot.SWIPE_OUT -> state.swipeOutAction
}

/** The icon [actionFor] shows, already looked up by the view model. */
fun actionIconFor(state: MainState, slot: Slot): ActionIcon = when (slot) {
    Slot.SINGLE_TAP -> state.clickActionIcon
    Slot.DOUBLE_TAP -> state.doubleClickActionIcon
    Slot.TRIPLE_TAP -> state.tripleClickActionIcon
    Slot.LONG_PRESS -> state.longClickActionIcon
    Slot.SWIPE_UP -> state.swipeUpActionIcon
    Slot.SWIPE_DOWN -> state.swipeDownActionIcon
    Slot.SWIPE_IN -> state.swipeInActionIcon
    Slot.SWIPE_OUT -> state.swipeOutActionIcon
}

/** The event that sets [slot] to [action] everywhere, with the prompts that may come with it. */
fun setActionEvent(slot: Slot, action: String, context: Context): MainEvent = when (slot) {
    Slot.SINGLE_TAP -> MainEvent.SetClickAction(action, context)
    Slot.DOUBLE_TAP -> MainEvent.SetDoubleClickAction(action, context)
    Slot.TRIPLE_TAP -> MainEvent.SetTripleClickAction(action, context)
    Slot.LONG_PRESS -> MainEvent.SetLongClickAction(action, context)
    Slot.SWIPE_UP -> MainEvent.SetSwipeUpAction(action, context)
    Slot.SWIPE_DOWN -> MainEvent.SetSwipeDownAction(action, context)
    Slot.SWIPE_IN -> MainEvent.SetSwipeInAction(action, context)
    Slot.SWIPE_OUT -> MainEvent.SetSwipeOutAction(action, context)
}

/**
 * [slot] drawn on a little bar: the finger and its rings for the taps, a held finger for the long
 * press, and the stroke and its direction for the swipes — inward and outward read from the side
 * the bar is on, [onLeft], so "Swipe in" points at the middle of the screen from wherever it is.
 *
 * Drawn rather than taken from the icon set, which has no double tap and no "toward the middle".
 */
@Composable
fun GestureGlyph(
    slot: Slot,
    onLeft: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    bar: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
) {
    Canvas(modifier = modifier) {
        // Laid out in a 40-unit square, whatever size it is drawn at.
        val u = size.minDimension / 40f
        val c = center
        val horizontal = slot == Slot.SWIPE_IN || slot == Slot.SWIPE_OUT
        // Toward the middle of the screen, from the bar: rightward for a bar on the left.
        val inward = if (onLeft) 1f else -1f
        // Off to its own side for the sideways swipes, so the stroke has room; centred otherwise.
        val barX = if (horizontal) c.x - inward * 9f * u else c.x
        drawRoundRect(
            color = bar,
            topLeft = Offset(barX - 2.5f * u, c.y - 13f * u),
            size = Size(5f * u, 26f * u),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * u),
        )
        val finger = Offset(barX, c.y)
        when (slot) {
            Slot.SINGLE_TAP, Slot.DOUBLE_TAP, Slot.TRIPLE_TAP -> {
                val rings = when (slot) {
                    Slot.SINGLE_TAP -> 1
                    Slot.DOUBLE_TAP -> 2
                    else -> 3
                }
                drawCircle(tint, 3.4f * u, finger)
                for (i in 1..rings) {
                    drawCircle(
                        color = tint.copy(alpha = 0.8f - 0.2f * (i - 1)),
                        radius = 3.4f * u + 3.3f * u * i,
                        center = finger,
                        style = Stroke(width = 1.5f * u),
                    )
                }
            }
            Slot.LONG_PRESS -> {
                drawCircle(tint, 3.6f * u, finger)
                // A timer run most of the way round: holding, not tapping.
                val r = 9f * u
                drawCircle(tint.copy(alpha = 0.22f), r, finger, style = Stroke(width = 2f * u))
                drawArc(
                    color = tint,
                    startAngle = -90f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(finger.x - r, finger.y - r),
                    size = Size(2f * r, 2f * r),
                    style = Stroke(width = 2f * u, cap = StrokeCap.Round),
                )
            }
            Slot.SWIPE_UP -> stroke(tint, u, Offset(barX, c.y + 9f * u), Offset(barX, c.y - 10f * u))
            Slot.SWIPE_DOWN -> stroke(tint, u, Offset(barX, c.y - 9f * u), Offset(barX, c.y + 10f * u))
            Slot.SWIPE_IN -> stroke(tint, u, finger, Offset(barX + inward * 22f * u, c.y))
            Slot.SWIPE_OUT -> stroke(tint, u, Offset(barX + inward * 20f * u, c.y), Offset(barX - inward * 5f * u, c.y))
        }
    }
}

/** A finger's stroke from [from] to [to]: where it went down, the trail, and the way it went. */
private fun DrawScope.stroke(tint: Color, u: Float, from: Offset, to: Offset) {
    drawCircle(tint, 3f * u, from)
    drawLine(tint, from, to, strokeWidth = 2.2f * u, cap = StrokeCap.Round)
    val angle = kotlin.math.atan2(to.y - from.y, to.x - from.x)
    val head = 5f * u
    for (side in floatArrayOf(-1f, 1f)) {
        val a = angle + Math.PI.toFloat() + side * 0.7f
        drawLine(
            color = tint,
            start = to,
            end = Offset(to.x + head * cos(a), to.y + head * sin(a)),
            strokeWidth = 2.2f * u,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * One gesture and what it does: its picture, its name, and the action under it with its icon —
 * the whole row the thing to tap. [footer] is for a note that belongs to the row, such as a
 * permission the action is still waiting for.
 *
 * @param dimmed for a slot that follows another setting rather than holding its own action.
 */
@Composable
fun GestureRow(
    slot: Slot,
    onLeft: Boolean,
    actionIcon: ActionIcon,
    actionText: String,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val colours = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colours.surfaceVariant.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colours.primaryContainer.copy(alpha = 0.6f)),
            ) {
                GestureGlyph(
                    slot = slot,
                    onLeft = onLeft,
                    tint = colours.primary,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(gestureName(slot)),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colours.onSurface,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 3.dp),
                ) {
                    val ink = if (dimmed) colours.onSurfaceVariant else colours.primary
                    ActionIconImage(
                        icon = actionIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = ink,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = actionText,
                        fontSize = 14.sp,
                        fontWeight = if (dimmed) FontWeight.Normal else FontWeight.Medium,
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colours.outline,
                modifier = Modifier.size(22.dp),
            )
        }
        footer()
    }
}

/**
 * The shape of the [index]th of [count] rows grouped into one card: round where the group starts
 * and ends, nearly square where two rows meet, so the rows read as one piece and each as its own.
 */
fun segmentShape(index: Int, count: Int): Shape {
    val outer = 20.dp
    val inner = 6.dp
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** The heading over a group of rows. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 6.dp, bottom = 10.dp),
    )
}
