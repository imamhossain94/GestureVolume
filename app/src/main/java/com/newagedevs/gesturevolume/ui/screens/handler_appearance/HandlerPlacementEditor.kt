package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import android.app.Activity
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.service.HandlerGeometry
import com.newagedevs.gesturevolume.service.OverlayController
import com.newagedevs.gesturevolume.ui.components.PreviewBackdrop
import com.newagedevs.gesturevolume.ui.motion.Button
import com.newagedevs.gesturevolume.ui.motion.OutlinedButton
import com.newagedevs.gesturevolume.ui.view.HandlerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The whole screen, for putting the bar where it starts: the draft's bar at its true size and in
 * its true place, on a picture that runs under the status bar and the navigation bar, because
 * the live bar is drawn over both and a place near either is only judged honestly with them there.
 *
 * A long press picks the bar up, on it or anywhere else, and a drag carries it under the live
 * bar's own rules — the edge distance held on every frame, a side dressed for as it is crossed, a
 * snap to the nearer side on release — measured in the frame the overlay itself reads
 * ([HandlerGeometry.read]) with the same conversions. So where the bar is let go here is where the overlay puts it, to the pixel, which
 * is what the scale model in the dock could never promise and the sliders only promise in numbers.
 *
 * It edits the draft and nothing else. Done writes the place into [state] as the fractions the
 * sliders show; the screen's tick is still what saves it, so a new place is one more pending
 * change beside a new colour, and Back or Cancel leave the draft exactly as it was.
 */
@Composable
internal fun HandlerPlacementEditor(
    state: AppearanceStateHolder,
    /** Which of the draft's two positions is the one the phone is held in. */
    isPortrait: Boolean,
    backdrop: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val frame = remember(configuration) {
        HandlerGeometry.read(context, context.getSystemService(WindowManager::class.java))
    }
    if (frame == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val place = remember(frame) {
        Placement(
            frame = frame,
            widthDp = state.width,
            heightDp = state.height,
            edgeMarginDp = state.edgeMargin,
            dynamic = state.dynamicPosition,
            snaps = state.dynamicPosition || state.snapToEdge,
        )
    }
    val start = remember(place) { place.start(state, isPortrait) }
    var position by remember(place) { mutableStateOf(start) }
    var dragging by remember { mutableStateOf(false) }
    // Where this layer sits in the window, which the overlay's frame is measured from. The top
    // left corner on any phone; read rather than assumed, so a layer that ever moves stays right.
    var layerOrigin by remember { mutableStateOf(IntOffset.Zero) }
    val onLeft by remember(place) { derivedStateOf { place.onLeft(position) } }
    val onTop by remember(place) { derivedStateOf { place.onTop(position) } }

    val scope = rememberCoroutineScope()
    var settling by remember { mutableStateOf<Job?>(null) }
    val haptics = LocalHapticFeedback.current

    fun barTopLeft(): IntOffset = place.origin + position - layerOrigin
    fun barRect(): Rect = Rect(barTopLeft().toOffset(), Size(place.width.toFloat(), place.height.toFloat()))

    fun settle() {
        if (!dragging) return
        dragging = false
        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
        val target = place.rest(position)
        if (target == position) return
        // Eased rather than sprung past the edge: the live bar decelerates into its snap, and a bar
        // that bounced here would promise a motion the real one does not make.
        settling = scope.launch {
            animate(
                typeConverter = IntOffset.VectorConverter,
                initialValue = position,
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = IntOffset(1, 1),
                ),
            ) { value, _ -> position = value }
        }
    }

    fun finish() {
        settling?.cancel()
        // Where the bar is headed, if Done lands mid-snap. And nothing written unless it moved: the
        // fractions read back from a bar that stayed put can differ from the stored ones in the
        // last place, and a pending change nobody made would light the tick for nothing.
        val final = place.rest(position)
        if (final != start) place.commit(final, from = start, state = state)
        onDismiss()
    }

    BackHandler(onBack = onDismiss)
    LightSystemBarIcons()

    val chromeAlpha by animateFloatAsState(
        targetValue = if (dragging) 0f else 1f,
        animationSpec = tween(if (dragging) 140 else 260),
        label = "placementChrome",
    )
    val guideAlpha by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = tween(180),
        label = "placementGuide",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPlaced { layerOrigin = it.positionInWindow().round() }
            // A hold anywhere picks the bar up, and it then moves as far as the finger does. Not only
            // a hold on the bar: this window lies under the status bar and the navigation bar, and a
            // touch there belongs to them — a bar parked along the top edge, lying down on a phone
            // held sideways, could be seen here and never grabbed. The bar is often a sliver under a
            // thumb besides, and pushing it from beside it keeps it in sight.
            .pointerInput(place) {
                var from = IntOffset.Zero
                var travel = Offset.Zero
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        settling?.cancel()
                        from = position
                        travel = Offset.Zero
                        dragging = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        travel += amount
                        position = place.dragged(from, travel)
                    },
                    onDragEnd = { settle() },
                    onDragCancel = { settle() },
                )
            },
    ) {
        PreviewBackdrop(index = backdrop, modifier = Modifier.fillMaxSize())
        // Darker at the ends, so the white status bar icons above and the buttons below read on
        // whichever picture is showing.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.34f),
                        0.2f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.38f),
                    )
                ),
        )

        // A line through the middle of the bar while it moves, to line it up against what is on
        // the screen, and gone the moment it is put down.
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (guideAlpha <= 0f) return@Canvas
            val topLeft = barTopLeft()
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
            val stroke = 1.dp.toPx()
            if (place.lying) {
                val x = topLeft.x + place.width / 2f
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), stroke, pathEffect = dash, alpha = 0.7f * guideAlpha)
            } else {
                val y = topLeft.y + place.height / 2f
                drawLine(Color.White, Offset(0f, y), Offset(size.width, y), stroke, pathEffect = dash, alpha = 0.7f * guideAlpha)
            }
        }

        PlacementHint(
            note = when {
                place.dynamic -> stringResource(R.string.placement_dynamic_note)
                place.snaps -> stringResource(R.string.placement_snap_note)
                else -> null
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                // Clear of both edges, where the bar most often is.
                .padding(start = 56.dp, end = 56.dp, top = 20.dp)
                .graphicsLayer { alpha = chromeAlpha },
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .graphicsLayer { alpha = chromeAlpha },
        ) {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.3f),
                    contentColor = Color.White,
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
            ) {
                Text(stringResource(R.string.cancel))
            }
            Button(onClick = ::finish) {
                Text(stringResource(R.string.placement_done))
            }
        }

        // The bar last, over everything else here, so wherever it is put it is never behind the
        // chrome that is explaining it.
        AndroidView(
            factory = { ctx ->
                // Never takes a touch: the long press is this layer's, over the bar and around it.
                object : FrameLayout(ctx) {
                    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = false
                }.apply {
                    addView(HandlerView(ctx).apply { dressLike(state) })
                }
            },
            update = { container ->
                // Read here so only a change of side re-dresses the bar, not every frame of a drag.
                val bar = container.getChildAt(0) as HandlerView
                if (place.lying) {
                    bar.setLyingEdge(if (onTop) Gravity.TOP else Gravity.BOTTOM)
                } else {
                    bar.setLyingEdge(Gravity.NO_GRAVITY)
                    bar.setViewGravity(if (onLeft) Gravity.START else Gravity.END)
                }
                // The live bar's own sign that it has been picked up: dimmed, with the move icon.
                bar.setDragCue(dragging)
            },
            modifier = Modifier
                // Deferred to placement, so a drag moves the bar without recomposing anything.
                .offset { barTopLeft() }
                .layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints.fixed(place.width, place.height))
                    layout(place.width, place.height) { placeable.place(0, 0) }
                },
        )

        if (dragging) {
            val fromLeft = stringResource(R.string.position_from_left)
            val fromTop = stringResource(R.string.position_from_top)
            val format = stringResource(R.string.placement_readout)
            PlacementReadout(
                text = { place.readout(position, fromLeft, fromTop, format) },
                bar = ::barRect,
                below = place.lying && onTop,
                above = place.lying && !onTop,
                rightOf = !place.lying && onLeft,
            )
        }
    }
}

/** What the screen is for, and why the bar may not stay exactly where it is dropped. */
@Composable
private fun PlacementHint(note: String?, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.5f),
        contentColor = Color.White,
        modifier = modifier.widthIn(max = 360.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.OpenWith, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.position_set_initial),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(R.string.placement_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * The bar's place as the sliders will show it, beside the bar on its inward side while it moves:
 * the one moment the number is worth reading, and the one place the eye already is.
 */
@Composable
private fun PlacementReadout(
    /** Read in here, so a drag recomposes this label and nothing around it. */
    text: () -> String,
    bar: () -> Rect,
    below: Boolean,
    above: Boolean,
    rightOf: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.62f),
        contentColor = Color.White,
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val r = bar()
                val gap = 12.dp.roundToPx()
                val x: Int
                val y: Int
                when {
                    below || above -> {
                        x = (r.center.x - placeable.width / 2f).roundToInt()
                        y = if (below) r.bottom.roundToInt() + gap else r.top.roundToInt() - gap - placeable.height
                    }
                    else -> {
                        x = if (rightOf) r.right.roundToInt() + gap else r.left.roundToInt() - gap - placeable.width
                        y = (r.center.y - placeable.height / 2f).roundToInt()
                    }
                }
                placeable.place(
                    x.coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0)),
                    y.coerceIn(0, (constraints.maxHeight - placeable.height).coerceAtLeast(0)),
                )
            }
        },
    ) {
        Text(
            text = text(),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/**
 * White status bar and navigation bar icons while the editor is up, over a picture rather than the
 * app's surface, and whatever the app had put back when it goes.
 */
@Composable
private fun LightSystemBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (lightStatus != null) controller.isAppearanceLightStatusBars = lightStatus
            if (lightNav != null) controller.isAppearanceLightNavigationBars = lightNav
        }
    }
}

/** Dresses a bar for the editor: the draft's look, at its full length, in its touch-wide window. */
private fun HandlerView.dressLike(state: AppearanceStateHolder) {
    // As the overlay builds its bar: a window at least a finger wide, the bar drawn at its own
    // width against the outer edge of it.
    val windowWidth = maxOf(state.width, OverlayController.MIN_TOUCH_WIDTH_DP)
    setViewDimensionsDp(windowWidth, state.height)
    setInwardPaddingDp(windowWidth - state.width)
    wearDraft(state)
}

private fun IntOffset.toOffset() = Offset(x.toFloat(), y.toFloat())

/**
 * The editor's arithmetic, in the overlay's terms: a window [width] by [height] pixels, at a top
 * left corner measured from the usable frame's, moved and put down by [HandlerGeometry]'s own
 * functions. No conversion here is new; each is the one the overlay runs for the same step.
 */
private class Placement(
    frame: HandlerGeometry.Frame,
    widthDp: Float,
    heightDp: Float,
    edgeMarginDp: Float,
    /** Dynamic position: the bar follows the phone round and always rests on an edge. */
    val dynamic: Boolean,
    /** Whether a bar that is let go flies to the nearer side. */
    val snaps: Boolean,
) {
    private val density = frame.density

    // Truncated, as the overlay's dpToPx does, so the two agree on every size to the pixel.
    private fun px(dp: Float): Int = (dp * density).toInt()

    private val usableWidth = frame.usableWidth
    private val usableHeight = frame.usableHeight

    /** Across the bar: the window, which is wider than a bar thinner than a finger. */
    private val thickness = px(maxOf(widthDp, OverlayController.MIN_TOUCH_WIDTH_DP))

    /** Along the bar. */
    private val length = px(heightDp)
    private val margin = px(edgeMarginDp)
    private val turns = HandlerGeometry.quarterTurns(frame)

    /** Dynamic position with the phone on its side: the bar lies along the top or bottom edge. */
    val lying = dynamic && turns % 2 == 1
    val width = if (lying) length else thickness
    val height = if (lying) thickness else length

    /** The usable frame's top left corner in the window: the camera cutout is not usable. */
    val origin = IntOffset(frame.insetLeft, frame.insetTop)

    /** Where the overlay would put the draft's bar right now. */
    fun start(state: AppearanceStateHolder, isPortrait: Boolean): IntOffset {
        if (dynamic) {
            val along = if (isPortrait) state.positionFraction else state.otherPositionFraction
            val (edge, at) = HandlerGeometry.dynamicEdge(state.gravity == Gravity.START, along, turns)
            return if (edge.isHorizontal) {
                IntOffset(
                    HandlerGeometry.fractionToX(at, usableWidth, length),
                    HandlerGeometry.sideToX(edge == HandlerGeometry.Edge.TOP, usableHeight, thickness, margin),
                )
            } else {
                IntOffset(
                    HandlerGeometry.sideToX(edge == HandlerGeometry.Edge.LEFT, usableWidth, thickness, margin),
                    HandlerGeometry.fractionToY(at, usableHeight, length),
                )
            }
        }
        val x = HandlerGeometry.fractionToX(state.posXFraction, usableWidth, thickness)
        return IntOffset(
            if (snaps) {
                HandlerGeometry.snapX(x, usableWidth, thickness, margin)
            } else {
                HandlerGeometry.clampX(x, usableWidth, thickness, margin)
            },
            HandlerGeometry.fractionToY(state.positionFraction, usableHeight, length),
        )
    }

    /** Where a drag of [by] from [from] puts the bar: held off the edges as the live bar is. */
    fun dragged(from: IntOffset, by: Offset): IntOffset {
        val x = (from.x + by.x).roundToInt()
        val y = (from.y + by.y).roundToInt()
        return if (lying) {
            IntOffset(
                x.coerceIn(0, (usableWidth - length).coerceAtLeast(0)),
                HandlerGeometry.clampX(y, usableHeight, thickness, margin),
            )
        } else {
            IntOffset(
                HandlerGeometry.clampX(x, usableWidth, thickness, margin),
                y.coerceIn(0, (usableHeight - length).coerceAtLeast(0)),
            )
        }
    }

    /** Where a bar let go at [at] comes to rest. */
    fun rest(at: IntOffset): IntOffset = when {
        !snaps -> at
        lying -> IntOffset(at.x, HandlerGeometry.snapX(at.y, usableHeight, thickness, margin))
        else -> IntOffset(HandlerGeometry.snapX(at.x, usableWidth, thickness, margin), at.y)
    }

    /** Which side an upright bar at [at] is dressed for: whichever its middle is nearer. */
    fun onLeft(at: IntOffset): Boolean = HandlerGeometry.xToIsLeft(at.x, usableWidth, thickness)

    /** The same for a lying bar, down the screen instead of across it. */
    fun onTop(at: IntOffset): Boolean = HandlerGeometry.xToIsLeft(at.y, usableHeight, thickness)

    /** The place as the Position group writes it: a share of the screen, for each axis that moves. */
    fun readout(at: IntOffset, onLeft: String, onTop: String, format: String): String {
        fun pct(f: Float) = (f * 100f).roundToInt()
        val fromLeft = String.format(format, onLeft, pct(HandlerGeometry.xToFraction(at.x, usableWidth, width)))
        val fromTop = String.format(format, onTop, pct(HandlerGeometry.yToFraction(at.y, usableHeight, height)))
        return when {
            lying -> fromLeft
            snaps -> fromTop
            else -> "$fromLeft · $fromTop"
        }
    }

    /** Writes a bar resting at [at], moved from [from], into the draft as the fractions the overlay reads. */
    fun commit(at: IntOffset, from: IntOffset, state: AppearanceStateHolder) {
        if (dynamic) {
            // Stored upright, whichever way the phone is held: the side of the phone the bar is on,
            // and how far along it. See HandlerGeometry.dynamicEdge.
            val (edge, along) = if (lying) {
                (if (onTop(at)) HandlerGeometry.Edge.TOP else HandlerGeometry.Edge.BOTTOM) to
                    HandlerGeometry.xToFraction(at.x, usableWidth, length)
            } else {
                (if (onLeft(at)) HandlerGeometry.Edge.LEFT else HandlerGeometry.Edge.RIGHT) to
                    HandlerGeometry.yToFraction(at.y, usableHeight, length)
            }
            val (left, upright) = HandlerGeometry.uprightFromEdge(edge, along, turns) ?: return
            // As the side picker and the along-the-edge slider write them.
            val x = if (left) 0f else 1f
            state.gravity = if (left) Gravity.START else Gravity.END
            state.posXFraction = x
            state.otherPosXFraction = x
            state.positionFraction = upright
            state.otherPositionFraction = upright
            return
        }
        // The pair for the way the phone is held, as a drag of the live bar writes it — but only the
        // axes that moved. A snapping bar carried up the right edge is still on the right edge, and
        // its "From left" turning from 100% into 97% by being read back is a change nobody made.
        val moved = if (snaps) onLeft(at) != onLeft(from) else at.x != from.x
        if (moved) {
            state.posXFraction = HandlerGeometry.xToFraction(at.x, usableWidth, thickness)
            state.gravity = if (onLeft(at)) Gravity.START else Gravity.END
        }
        if (at.y != from.y) {
            state.positionFraction = HandlerGeometry.yToFraction(at.y, usableHeight, length)
        }
    }
}
