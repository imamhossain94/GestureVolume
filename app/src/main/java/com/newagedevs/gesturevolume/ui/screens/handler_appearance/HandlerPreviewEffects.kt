package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.components.ActionIconImage
import com.newagedevs.gesturevolume.ui.components.DemoGesture
import com.newagedevs.gesturevolume.ui.components.GestureDemoState
import com.newagedevs.gesturevolume.ui.components.actionDisplayName
import com.newagedevs.gesturevolume.utils.GesturePreview
import com.newagedevs.gesturevolume.utils.GesturePreview.Effect
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog
import com.newagedevs.gesturevolume.utils.HandlerActions

/**
 * The actions the preview's gestures are set to: the ones saved on the Actions screen, or the ones
 * a preset chosen on this screen will save with it.
 */
@Immutable
internal data class PreviewGestures(
    val tap: String,
    val swipeUp: String,
    val swipeDown: String,
    /** How far one swipe moves the level. See `SharedPref.getSwipeStepPercent`. */
    val stepPercent: Int,
)

/**
 * What the preview shows the bar doing, moment by moment, as the finger taps and swipes it.
 *
 * Read off the demo's progress rather than kept in step with it, so the finger and what it does
 * can never drift apart, and derived, so a settings page is recomposed when a level or a panel
 * changes rather than on every frame of a swipe.
 */
@Stable
internal class PreviewEffects(private val demo: GestureDemoState, val gestures: PreviewGestures) {

    val tap: Effect = GesturePreview.tapEffect(gestures.tap)
    val up: Effect = GesturePreview.swipeEffect(gestures.swipeUp)
    val down: Effect = GesturePreview.swipeEffect(gestures.swipeDown)

    /** Set for a moment when the percentage is switched on, so the switch shows what it does. */
    var flashPercent by mutableStateOf(false)

    private fun started(gesture: DemoGesture, from: Float = STARTED): Boolean = demo.progressOf(gesture) > from

    private fun levelOf(upSteers: Boolean, downSteers: Boolean): Int = GesturePreview.levelAt(
        demo.progressOf(DemoGesture.SWIPE_UP), demo.progressOf(DemoGesture.SWIPE_DOWN),
        upSteers, downSteers, gestures.stepPercent,
    )

    /** The volume, in percent, as the swipes have left it. */
    val volume: Int by derivedStateOf { levelOf(up.isVolume, down.isVolume) }

    /** The brightness, in percent, likewise. The two are separate levels, as they are on the phone. */
    val brightness: Int by derivedStateOf { levelOf(up == Effect.BRIGHTNESS, down == Effect.BRIGHTNESS) }

    /**
     * Android's volume panel, up from the first gesture that brings it to the end of the pass. Not
     * while the demo winds back between passes, when the tap's progress runs backwards and would
     * take the panel away and bring it back just before the tap does: see [GestureDemoState.acting].
     */
    val panelShown: Boolean by derivedStateOf {
        demo.acting && (
            (tap.showsPanel && started(DemoGesture.TAP, TAP_LANDS)) ||
                (up.showsPanel && started(DemoGesture.SWIPE_UP)) ||
                (down.showsPanel && started(DemoGesture.SWIPE_DOWN))
            )
    }

    /** The brightness readout, from the first swipe that steers it. */
    val brightnessShown: Boolean by derivedStateOf {
        demo.acting && (
            (up == Effect.BRIGHTNESS && started(DemoGesture.SWIPE_UP)) ||
                (down == Effect.BRIGHTNESS && started(DemoGesture.SWIPE_DOWN))
            )
    }

    /**
     * The number the bar shows in place of its icon, or null for the icon: the level the latest
     * swipe steered, as the live bar puts it there while a swipe moves it. Only with [showPercent].
     */
    fun barLabel(showPercent: Boolean): Int? {
        if (!showPercent) return null
        if (flashPercent) return volume
        if (!demo.acting) return null
        val latest = when {
            down.steers && started(DemoGesture.SWIPE_DOWN) -> down
            up.steers && started(DemoGesture.SWIPE_UP) -> up
            else -> return null
        }
        return if (latest == Effect.BRIGHTNESS) brightness else volume
    }

    /** The action [gesture] is set to. */
    fun actionOf(gesture: DemoGesture): String = when (gesture) {
        DemoGesture.SWIPE_UP -> gestures.swipeUp
        DemoGesture.SWIPE_DOWN -> gestures.swipeDown
        else -> gestures.tap
    }

    private companion object {
        /** How far into a swipe its effect begins: its first step. */
        const val STARTED = 0.02f

        /** How far through a tap's ripple its action lands: as the finger lifts. */
        const val TAP_LANDS = 0.25f
    }
}

/**
 * What the finger's gestures do, over the preview: the screen dimming and brightening with a
 * brightness swipe, Android's volume panel beside the bar where the action brings it up, the
 * brightness readout where the live one appears, and what the gesture under way is set to. The
 * number on the bar itself is the bar's own; see [PreviewEffects.barLabel]. What each gesture is set
 * to do is said in the row under the phone, not on it: see [GestureCaption].
 *
 * Draw-only: nothing in it takes a touch.
 *
 * @param barAtStart which side the bar is on, in start and end terms like the stage.
 * @param barReach from that edge to the bar's inner side, so the panel opens beside the bar.
 */
@Composable
internal fun HandlerPreviewEffects(
    effects: PreviewEffects,
    barAtStart: Boolean,
    barReach: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        // The screen itself, as brightness takes it: darker the lower it goes.
        val dim by animateFloatAsState(
            targetValue = if (effects.brightnessShown) (1f - effects.brightness / 100f) * MAX_DIM else 0f,
            label = "previewDim",
        )
        if (dim > 0.002f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = dim }
                    .background(Color.Black)
            )
        }

        // Out of the bar's side of the stage, whichever side that is on screen.
        val barOnLeft = barAtStart != (LocalLayoutDirection.current == LayoutDirection.Rtl)
        AnimatedVisibility(
            visible = effects.panelShown,
            enter = fadeIn() + scaleIn(initialScale = 0.8f, transformOrigin = TransformOrigin(if (barOnLeft) 0f else 1f, 0.5f)),
            exit = fadeOut() + scaleOut(targetScale = 0.8f, transformOrigin = TransformOrigin(if (barOnLeft) 0f else 1f, 0.5f)),
            modifier = Modifier
                .align(if (barAtStart) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(start = if (barAtStart) barReach + PANEL_GAP else 0.dp, end = if (barAtStart) 0.dp else barReach + PANEL_GAP),
        ) {
            VolumePanelStandIn(level = effects.volume)
        }

        AnimatedVisibility(
            visible = effects.brightnessShown,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            BrightnessReadout(level = effects.brightness)
        }
    }
}

/**
 * Android's volume panel, as it looks since Android 12: a dark rounded column with the level filled
 * from the bottom and the speaker under it. A stand-in, so it is plain, and the level is the point.
 */
@Composable
private fun VolumePanelStandIn(level: Int) {
    val fill by animateFloatAsState(
        targetValue = level / 100f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
        label = "panelLevel",
    )
    Surface(shape = RoundedCornerShape(22.dp), color = PanelColour, shadowElevation = 6.dp) {
        Column(
            modifier = Modifier
                .padding(6.dp)
                .width(34.dp)
                .height(142.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color.White.copy(alpha = 0.14f))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(fill.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(17.dp))
                        .background(PanelAccent)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(18.dp)
                    .padding(bottom = 2.dp),
            )
        }
    }
}

/** The live bar's brightness readout: a dark chip in the middle of the screen, sun and number. */
@Composable
private fun BrightnessReadout(level: Int) {
    Surface(shape = RoundedCornerShape(18.dp), color = ReadoutColour, contentColor = Color.White) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_brightness_up),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Text(text = stringResource(R.string.brightness_percent, level), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * "Swipe up · Increase volume": the gesture under way, and what it is set to do, in the row under
 * the phone, worded and coloured as the walkthrough's captions are.
 */
@Composable
internal fun GestureCaption(gesture: DemoGesture, action: String) {
    val off = HandlerActions.isDisabled(action)
    val entry = if (off) null else HandlerActionCatalog.displayEntryFor(action)
    val gestureName = stringResource(
        when (gesture) {
            DemoGesture.SWIPE_UP -> R.string.demo_swipe_up
            DemoGesture.SWIPE_DOWN -> R.string.demo_swipe_down
            else -> R.string.demo_tap
        }
    )
    val actionName = if (off) stringResource(R.string.demo_does_nothing) else actionDisplayName(action)
    val ink = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (entry != null) {
            ActionIconImage(icon = entry.icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = ink)
        }
        Text(
            text = stringResource(R.string.demo_caption, gestureName, actionName),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** How dark the stage goes at no brightness at all. */
private const val MAX_DIM = 0.55f

/** Between the bar and the volume panel that opens beside it. */
private val PANEL_GAP = 14.dp

private val PanelColour = Color(0xF01F1F24)
private val PanelAccent = Color(0xFFD0BCFF)
private val ReadoutColour = Color(0xDC181818)
