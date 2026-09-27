package com.newagedevs.gesturevolume.ui.screens.handler_action

import com.newagedevs.gesturevolume.ui.components.StageShape
import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.AppGestureStore.Slot
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.ui.components.ActionIconImage
import com.newagedevs.gesturevolume.ui.components.DeviceArt
import com.newagedevs.gesturevolume.ui.components.actionDisplayName
import com.newagedevs.gesturevolume.ui.motion.TextButton
import com.newagedevs.gesturevolume.ui.view.HandlerGestureDetector
import com.newagedevs.gesturevolume.ui.view.HandlerView
import com.newagedevs.gesturevolume.utils.ActionIcon
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog
import com.newagedevs.gesturevolume.utils.HandlerActions

/**
 * A strip of screen with the user's own bar on it, to try the gestures on: tap it, hold it, swipe
 * it, and it says which gesture that was and what it is set to do — without doing it.
 *
 * The bar's own gesture engine does the recognising, [HandlerGestureDetector], on the timings set
 * below, which take effect here as they are dragged: so taps too slow to make a double tap here are
 * too slow on the bar. That is the thing words could not do for the timings — "300 ms" means
 * nothing until it is tried. The one difference is that every tap count is listened for here, set
 * or not; see [TryPadView].
 *
 * @param actions what each gesture is set to now.
 * @param onChange opens the picker for the gesture just tried.
 * @param fillHeight the strip takes whatever height the pad is given, as it does beside the list
 *   on a phone on its side, instead of a height of its own.
 */
@Composable
fun GestureTryPad(
    preference: SharedPref,
    actions: Map<Slot, String>,
    doubleTapMs: Int,
    longPressMs: Int,
    onLeft: Boolean,
    onChange: (Slot) -> Unit,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
) {
    val context = LocalContext.current
    // The last gesture recognised, and a count so the same one twice still plays its arrival.
    var tried by remember { mutableStateOf<Slot?>(null) }
    var times by remember { mutableIntStateOf(0) }
    var touch by remember { mutableStateOf<Offset?>(null) }
    var lastTouch by remember { mutableStateOf(Offset.Zero) }
    val currentActions by rememberUpdatedState(actions)

    val pad = remember {
        TryPadView(context, onLeft) { slot ->
            tried = slot
            times++
        }
    }
    pad.onTouch = { at ->
        touch = at
        if (at != null) lastTouch = at
    }
    pad.actions = { currentActions }
    LaunchedEffect(doubleTapMs, longPressMs) { pad.setTimings(doubleTapMs, longPressMs) }
    DisposableEffect(pad) { onDispose { pad.cancel() } }

    val colours = MaterialTheme.colorScheme
    // Laid out as the previews are: the pad the one card, the words under it.
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.weight(1f).heightIn(min = 120.dp) else Modifier.height(172.dp))
                .clip(StageShape)
                .background(Brush.linearGradient(listOf(DeviceArt.WallTop, DeviceArt.WallBottom))),
        ) {
            AndroidView(
                factory = {
                    pad.also { it.dress(preference) }
                },
                modifier = Modifier.fillMaxSize(),
            )
            // Where the finger is, so the stroke that was recognised is the stroke that was seen.
            val shown by animateFloatAsState(if (touch != null) 1f else 0f, label = "tryPadTouch")
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (shown > 0.01f) {
                    val at = touch ?: lastTouch
                    drawCircle(Color.White.copy(alpha = 0.45f * shown), 22.dp.toPx() * (0.7f + 0.3f * shown), at)
                    drawCircle(
                        Color.White.copy(alpha = 0.9f * shown),
                        22.dp.toPx() * (0.7f + 0.3f * shown),
                        at,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
        AnimatedContent(
            targetState = tried to times,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.94f)) togetherWith fadeOut()
            },
            label = "tryPadResult",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .heightIn(min = 56.dp),
        ) { (slot, _) ->
            if (slot == null) {
                Text(
                    text = stringResource(R.string.actions_try_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colours.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            } else {
                TriedResult(
                    slot = slot,
                    action = actions[slot] ?: HandlerActions.NONE,
                    onLeft = onLeft,
                    onChange = { onChange(slot) },
                )
            }
        }
    }
}

/** "Double tap → Open Deck", and the way to change it. */
@Composable
private fun TriedResult(slot: Slot, action: String, onLeft: Boolean, onChange: () -> Unit) {
    val colours = MaterialTheme.colorScheme
    val entry = HandlerActionCatalog.displayEntryFor(action)
    val nothing = entry == null || HandlerActions.isDisabled(action)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colours.primaryContainer.copy(alpha = 0.45f))
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colours.primaryContainer.copy(alpha = 0.6f)),
        ) {
            GestureGlyph(slot = slot, onLeft = onLeft, modifier = Modifier.fillMaxSize().padding(4.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(gestureName(slot)),
                fontSize = 13.sp,
                color = colours.onSurfaceVariant,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = colours.outline,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                ActionIconImage(
                    icon = entry?.icon ?: ActionIcon.Res(R.drawable.ic_nothing),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (nothing) colours.onSurfaceVariant else colours.primary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (nothing) stringResource(R.string.actions_not_set) else actionDisplayName(action),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (nothing) colours.onSurfaceVariant else colours.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onChange) {
            Text(stringResource(R.string.actions_try_change))
        }
    }
}

/**
 * The strip itself: a frame holding the bar, taking every touch on it and handing it to the bar's
 * gesture engine, whose answers it reports as the gesture they are, and nothing more.
 *
 * The whole strip, not only the bar, takes the touch: the bar is a few dp wide, and missing it is
 * not what anyone came here to practise.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
private class TryPadView(
    context: Context,
    private val onLeft: Boolean,
    private val onGesture: (Slot) -> Unit,
) : FrameLayout(context) {

    var onTouch: (Offset?) -> Unit = {}
    var actions: () -> Map<Slot, String> = { emptyMap() }

    private fun action(slot: Slot): String = actions()[slot] ?: HandlerActions.NONE

    private fun report(slot: Slot) {
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onGesture(slot)
    }

    private val detector = HandlerGestureDetector(context, object : HandlerGestureDetector.Host {
        override fun isLongPressReposition() = action(Slot.LONG_PRESS) == HandlerActions.REPOSITION
        // Both counted here whatever they are set to, unlike on the bar. The bar only waits for a
        // second or third tap when one of them has an action — with neither set, every tap is a
        // single tap at once — so a pad that followed it could never say "Double tap" to someone
        // who has not set one yet, and that is who comes here to try it.
        override fun isDoubleTapArmed() = true
        override fun isTripleTapArmed() = true
        override fun onTap() = report(Slot.SINGLE_TAP)
        override fun onDoubleTap() = report(Slot.DOUBLE_TAP)
        override fun onTripleTap() = report(Slot.TRIPLE_TAP)
        override fun onLongPress() = report(Slot.LONG_PRESS)
        override fun onAdjustBegin(initialDirection: Int) =
            report(if (initialDirection > 0) Slot.SWIPE_UP else Slot.SWIPE_DOWN)
        override fun onAdjustStep(direction: Int) = true
        override fun onAdjustEnd() {}
        // Held with the bar's long press set to moving it: that is the long press, here.
        override fun onDragCue(active: Boolean) {
            if (active) report(Slot.LONG_PRESS)
        }
        override fun onContextMenuOpen() {}
        override fun onContextMenuDismiss() {}
        override fun onDragBegin() {}
        override fun onDragUpdate(offsetXPx: Float, offsetYPx: Float) {}
        override fun onDragEnd(moved: Boolean) {}
        override fun edgeSwipeInwardSign() = if (onLeft) 1 else -1
        // Armed either way, unlike the bar, so a sideways swipe with nothing set says so.
        override fun isHorizontalSwipeArmed(inward: Boolean) = true
        override fun onHorizontalSwipe(inward: Boolean) = report(if (inward) Slot.SWIPE_IN else Slot.SWIPE_OUT)
        override fun isQuickSliderArmed(inward: Boolean) = false
        override fun onEdgePullBegin(inward: Boolean) {}
        override fun onEdgePullUpdate(progress: Float) {}
        override fun onEdgePullCancel() {}
        override fun onQuickSliderBegin(inward: Boolean) {}
    })

    private val bar = HandlerView(context)

    init {
        addView(bar)
    }

    /** Puts the user's own bar in the strip, against the side it is on. */
    fun dress(preference: SharedPref) {
        bar.apply {
            setViewDimensionsDp(preference.getHandlerWidthDp(), minOf(preference.getHandlerHeightDp(), BAR_MAX_HEIGHT_DP))
            setViewGravity(if (onLeft) Gravity.START else Gravity.END)
            setEdgeMarginDp(preference.getHandlerEdgeMarginDp().coerceAtMost(BAR_MAX_MARGIN_DP))
            setViewBackgroundColor(preference.getHandlerColor(), preference.getHandlerBackgroundAlpha())
            setCornerRadiiDp(
                preference.getHandlerCornerRadiusTL(),
                preference.getHandlerCornerRadiusTR(),
                preference.getHandlerCornerRadiusBL(),
                preference.getHandlerCornerRadiusBR(),
            )
            setShapeStyle(preference.getHandlerShape(), preference.getHandlerShapeFlare())
            setStrokeProperties(
                preference.getHandlerStrokeColor(),
                preference.getHandlerStrokeWidth(),
                preference.getHandlerStrokeAlpha(),
            )
            setCenterIcon(preference.getHandlerIconRes(), preference.getHandlerIconSize(), preference.getHandlerIconColor())
            setCenterIconVisible(preference.getHandlerShowIcon())
            // Last: every setter above rebuilds the layout params, and would drop the centring.
            (layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.gravity = (if (onLeft) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
                layoutParams = lp
            }
        }
    }

    fun setTimings(doubleTapMs: Int, longPressMs: Int) =
        detector.setTimings(doubleTapMs.toLong(), longPressMs.toLong())

    fun cancel() = detector.cancel()

    // Every touch on the strip is the pad's, before the bar or anything else sees it.
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Or the page scrolls away under a swipe up.
                parent?.requestDisallowInterceptTouchEvent(true)
                onTouch(Offset(event.x, event.y))
            }
            MotionEvent.ACTION_MOVE -> onTouch(Offset(event.x, event.y))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onTouch(null)
        }
        detector.onTouchEvent(event)
        return true
    }

    private companion object {
        /** The strip is short; a long bar is shown at a length that fits it with room to swipe. */
        const val BAR_MAX_HEIGHT_DP = 112f
        const val BAR_MAX_MARGIN_DP = 16f
    }
}
