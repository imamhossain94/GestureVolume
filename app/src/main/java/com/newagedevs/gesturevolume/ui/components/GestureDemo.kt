package com.newagedevs.gesturevolume.ui.components

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Path
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import android.provider.Settings
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.newagedevs.gesturevolume.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The gestures a preview can act out on the bar.
 *
 * Named for what the finger does rather than for what it opens, because the same swipe opens
 * different things depending on how the user has set the bar up, and a demo that is honest about
 * the motion stays true whichever of them it is standing next to.
 */
enum class DemoGesture {
    TAP,
    SWIPE_UP,
    SWIPE_DOWN,

    /** Away from whichever edge the bar is on, towards the middle of the screen. */
    SWIPE_IN,

    /** Press and keep still until the ring closes. */
    HOLD,
}

/**
 * Where a "how it works" demo has got to, shared by the finger and the preview it is opening.
 *
 * Hoisted out of the preview on purpose: [PreviewSettingsLayout] puts the preview in a different
 * place in the tree depending on how the phone is held, and state remembered inside it would start
 * the tour over on every turn.
 *
 * The preview reads [progressOf] to move in step with the finger — the Quick panel grows as the
 * finger drags, the Deck slides in as it swipes. Read it from a draw or layer lambda where possible:
 * it changes every frame while the demo plays, and there is no reason to recompose a screen of
 * settings sixty times a second for it.
 */
@Stable
class GestureDemoState internal constructor(
    val steps: List<DemoGesture>,
    passes: Int,
) {
    /** Passes still to play. Zero is the resting state, where the preview is exactly as it was. */
    internal var passesLeft by mutableIntStateOf(passes)

    /** Bumped by [replay], so a tap on the button part-way through starts over rather than queues. */
    internal var generation by mutableIntStateOf(0)

    /** The step under way in the current pass, and how far through it the finger is. */
    internal var stepIndex by mutableIntStateOf(if (passes > 0) 0 else steps.lastIndex)
    internal var stepFraction by mutableFloatStateOf(if (passes > 0) 0f else 1f)

    val playing: Boolean get() = passesLeft > 0

    /** True while a pass is being wound back to its start, before the finger comes in. */
    internal var rewinding by mutableStateOf(false)

    /**
     * Whether the finger is acting a gesture out: playing, and not winding the last pass back.
     *
     * The wind-back runs the first step's progress from done to not begun, which a preview tied to
     * [progressOf] takes as that step being undone. For the Quick panel folding back into the bar
     * that is the point. For something a gesture merely set off — a panel a tap opened — it is the
     * thing appearing and vanishing again before the tap that opens it, a flicker; those follow
     * this instead of [playing].
     */
    val acting: Boolean get() = playing && !rewinding

    /**
     * The gesture the finger is making, or about to, while it [acting]s; null at rest and while it
     * winds back. Read it through `derivedStateOf`: it is worked out from progress that changes on
     * every frame of a gesture, and it changes only a few times a pass.
     */
    val currentStep: DemoGesture?
        get() = if (!acting) null else steps.firstOrNull { progressOf(it) < 1f } ?: steps.last()

    /** Plays one more pass, from the start. */
    fun replay() {
        passesLeft = 1
        generation++
        // From this frame, not from when the demo's coroutine gets round to winding back: until
        // then the last pass still reads as finished, and would show its end for a frame or two.
        rewinding = true
    }

    /**
     * How far [step] has got in the current pass: 0 before it starts, 1 once it is done, and in
     * between while the finger is doing it. Always 1 while the demo rests, so a preview tied to it
     * shows the finished, opened state — which is the state it showed before there was a demo.
     */
    fun progressOf(step: DemoGesture): Float {
        if (!playing) return 1f
        val index = steps.indexOf(step)
        return when {
            index < 0 -> 1f
            index < stepIndex -> 1f
            index > stepIndex -> 0f
            else -> stepFraction
        }
    }
}

/**
 * A demo that plays [autoPlays] times when the screen first opens and then keeps out of the way.
 *
 * Twice, because the first pass is usually half missed while the eye finds the preview, and no
 * more, because this is a settings screen and the user came to tune something. Not at all when the
 * phone's animations are switched off: a finger that jumps from pose to pose explains nothing, and
 * someone who turned motion off did not ask for a performance. The button still plays it on request.
 */
@Composable
fun rememberGestureDemoState(steps: List<DemoGesture>, autoPlays: Int = 2): GestureDemoState {
    val context = LocalContext.current
    // Saved, so turning the phone once the tour has run does not run it again.
    var autoPlayed by rememberSaveable { mutableStateOf(false) }
    val state = remember(steps) {
        val motionOn = Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) > 0f
        GestureDemoState(steps, if (autoPlayed || !motionOn) 0 else autoPlays)
    }
    LaunchedEffect(Unit) { autoPlayed = true }
    return state
}

/**
 * The finger, drawn over a preview while its demo plays, and nothing at all otherwise.
 *
 * Draw only: no pointer input anywhere in it, so the preview underneath takes every touch exactly
 * as it did before, even mid-demo. And it leaves the composition the moment the demo finishes,
 * so a resting preview is the preview it always was, not the same preview under a transparent layer.
 *
 * @param barAtStart which side of the preview the bar is on, in the same start/end terms the
 *   previews align their subject with, so the finger lands where the preview draws the bar.
 * @param barInset from that edge to the middle of the bar.
 * @param showBar draw the bar at the edge, for previews of panels that do not show the bar
 *   themselves; a finger pressing on empty wallpaper does not explain where the gesture starts.
 * @param handle what that bar looks like: the user's own, so the demo shows the handle they have.
 */
@Composable
fun GestureDemoOverlay(
    state: GestureDemoState,
    barAtStart: Boolean,
    barInset: Dp,
    modifier: Modifier = Modifier,
    showBar: Boolean = false,
    handle: HandleLook = HandleLook.DOCK,
) {
    if (!state.playing) return
    val handlePath = remember { Path() }
    val density = LocalDensity.current
    // The bar is drawn smaller on a preview's glass than on the phone: see PreviewStage.
    val glassScale = LocalGlassScale.current
    val onLeft = barAtStart != (LocalLayoutDirection.current == LayoutDirection.Rtl)
    // Keyed on the side the bar is on. Moved to the other edge part-way through a pass, the bar
    // takes the hand with it at once: a fresh pose, hidden, and the pass started again from the new
    // side. Kept, the hand went on acting against the edge the bar had left until the pass ended,
    // and faded out there before coming round.
    val pose = remember(onLeft) { FingerPose() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(state.generation, onLeft) {
        val box = snapshotFlow { size }.first { it.width > 0 && it.height > 0 }
        density.runDemo(state, pose, box, onLeft, with(density) { barInset.toPx() } * glassScale)
    }
    Canvas(modifier = modifier.onSizeChanged { size = it }) {
        // On the glass, and only on it: a ring spreading from a touch at the edge stops at the edge.
        clipRect {
            if (showBar) drawStandInBar(pose, handle, onLeft, handlePath, glassScale)
            drawTouchRings(pose)
        }
        // The hand is not clipped: it reaches in over the frame, from beyond the phone.
        drawHand(pose, onLeft)
    }
}

/**
 * Replays the demo. In the row under the phone, on the card, where the walkthrough puts its words:
 * over the phone it sat on whatever the preview was showing in that corner.
 */
@Composable
fun HowItWorksButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.height(34.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 8.dp, end = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.how_it_works),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** A gesture's name, as a caption under the phone: the walkthrough's caption, on the card. */
@Composable
fun DemoGestureText(gesture: DemoGesture) {
    Text(
        text = stringResource(demoGestureName(gesture)),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The name of [gesture], for a caption. */
fun demoGestureName(gesture: DemoGesture): Int = when (gesture) {
    DemoGesture.TAP -> R.string.demo_tap
    DemoGesture.SWIPE_UP -> R.string.demo_swipe_up
    DemoGesture.SWIPE_DOWN -> R.string.demo_swipe_down
    DemoGesture.SWIPE_IN -> R.string.demo_swipe_in
    DemoGesture.HOLD -> R.string.demo_hold
}

// ---- the performance ------------------------------------------------------------------------

/** Everything the canvas draws, as state, so the animation invalidates the drawing and nothing else. */
private class FingerPose {
    var tip by mutableStateOf(Offset.Zero)
    var alpha by mutableFloatStateOf(0f)

    /** 0 hovering, 1 pressed against the glass. */
    var press by mutableFloatStateOf(0f)

    /** A touch ring spreading out from [rippleAt]; 0 when there is none. */
    var ripple by mutableFloatStateOf(0f)
    var rippleAt by mutableStateOf(Offset.Zero)

    /** How far a long press has got, 0..1. */
    var hold by mutableFloatStateOf(0f)

    var barAt by mutableStateOf(Offset.Zero)
    var barAlpha by mutableFloatStateOf(0f)
}

private suspend fun Density.runDemo(
    state: GestureDemoState,
    pose: FingerPose,
    box: IntSize,
    onLeft: Boolean,
    insetPx: Float,
) {
    val w = box.width.toFloat()
    val h = box.height.toFloat()
    val inward = if (onLeft) 1f else -1f
    val bar = Offset(if (onLeft) insetPx else w - insetPx, h / 2f)
    // Where the walkthrough's hand rests, off the phone below the bar's side, in this glass's terms:
    // the box is the glass, so its width is the scene's glass and gives the scene's unit.
    val unit = w / DeviceArt.SCREEN_W
    val restX = (DeviceArt.FINGER_REST.x - DeviceArt.SCREEN_L) * unit
    val rest = Offset(if (onLeft) w - restX else restX, (DeviceArt.FINGER_REST.y - DeviceArt.SCREEN_T) * unit)
    // Short enough to stay on the stage, long enough to read as a swipe rather than a nudge.
    val swipe = minOf(64.dp.toPx(), h * 0.3f)
    val reach = minOf(120.dp.toPx(), w * 0.42f)
    // A replay tapped part-way through: the finger already on screen goes before the new one
    // arrives, rather than jumping to the start.
    if (pose.alpha > 0f) tweenValue(pose.alpha, 0f, 160) { pose.alpha = it; pose.barAlpha = it }
    pose.hold = 0f
    pose.ripple = 0f
    pose.barAt = bar

    while (state.passesLeft > 0) {
        rewind(state)
        // In from off the phone, below the bar's side, as the walkthrough's hand comes.
        pose.tip = rest
        pose.press = 0f
        pose.ripple = 0f
        pose.hold = 0f
        val first = startOf(state.steps.first(), bar, swipe)
        coroutineScope {
            launch { tweenValue(0f, 1f, 240) { pose.alpha = it; pose.barAlpha = it } }
            moveTo(pose, first)
        }

        for ((index, step) in state.steps.withIndex()) {
            state.stepIndex = index
            state.stepFraction = 0f
            when (step) {
                DemoGesture.TAP -> {
                    moveTo(pose, bar)
                    press(pose, down = true)
                    coroutineScope {
                        launch { ripple(pose) { state.stepFraction = it } }
                        delay(70)
                        press(pose, down = false)
                    }
                    delay(180)
                }

                DemoGesture.SWIPE_UP, DemoGesture.SWIPE_DOWN, DemoGesture.SWIPE_IN -> {
                    val start = startOf(step, bar, swipe)
                    val end = when (step) {
                        DemoGesture.SWIPE_UP -> bar - Offset(0f, swipe)
                        DemoGesture.SWIPE_DOWN -> bar + Offset(0f, swipe)
                        else -> bar + Offset(inward * reach, 0f)
                    }
                    moveTo(pose, start)
                    press(pose, down = true)
                    // Eased at both ends, as a real drag is: a finger gathers speed and slows to a
                    // stop, and the preview tied to it should do the same rather than slide.
                    tweenValue(0f, 1f, 620, FastOutSlowInEasing) {
                        pose.tip = lerp(start, end, it)
                        state.stepFraction = it
                    }
                    delay(90)
                    press(pose, down = false)
                    delay(160)
                }

                DemoGesture.HOLD -> {
                    moveTo(pose, bar)
                    press(pose, down = true)
                    // Linear: the ring is a clock, and a clock that speeds up is not one.
                    tweenValue(0f, 1f, 950, LinearEasing) {
                        pose.hold = it
                        state.stepFraction = it
                    }
                    // Still pressed a moment after the menu appears, so cause and effect are on
                    // screen together.
                    delay(380)
                    pose.hold = 0f
                    coroutineScope {
                        launch { ripple(pose) {} }
                        press(pose, down = false)
                    }
                }
            }
            state.stepFraction = 1f
        }

        // Away, and a moment with the result on its own before the next pass or the rest.
        val leaveFrom = pose.tip
        coroutineScope {
            launch { moveTo(pose, lerp(leaveFrom, rest, 0.6f)) }
            tweenValue(1f, 0f, 280) { pose.alpha = it; pose.barAlpha = it }
        }
        delay(if (state.passesLeft > 1) 700L else 450L)
        state.passesLeft -= 1
    }
}

/**
 * Takes the preview back to where a pass starts — the Quick panel back into the bar, the Deck
 * back to the edge — smoothly, with no finger on screen, rather than snapping it shut.
 */
private suspend fun rewind(state: GestureDemoState) {
    if (state.stepIndex == 0 && state.stepFraction == 0f) {
        state.rewinding = false
        return
    }
    val from = if (state.stepIndex == 0) state.stepFraction else 1f
    state.rewinding = true
    try {
        state.stepIndex = 0
        tweenValue(from, 0f, 380, FastOutSlowInEasing) { state.stepFraction = it }
        delay(220)
    } finally {
        state.rewinding = false
    }
}

/** Where the finger first touches for [step]: a swipe starts a little behind the bar, to go through it. */
private fun startOf(step: DemoGesture, bar: Offset, swipe: Float): Offset = when (step) {
    DemoGesture.SWIPE_UP -> bar + Offset(0f, swipe * 0.2f)
    DemoGesture.SWIPE_DOWN -> bar - Offset(0f, swipe * 0.2f)
    else -> bar
}

private suspend fun moveTo(pose: FingerPose, target: Offset) {
    // A spring, not a curve: a hand moving between touches settles rather than stops dead.
    animate(
        typeConverter = Offset.VectorConverter,
        initialValue = pose.tip,
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 240f, visibilityThreshold = Offset(0.5f, 0.5f)),
    ) { value, _ -> pose.tip = value }
}

private suspend fun press(pose: FingerPose, down: Boolean) {
    tweenValue(pose.press, if (down) 1f else 0f, if (down) 110 else 170) { pose.press = it }
}

private suspend fun ripple(pose: FingerPose, onFraction: (Float) -> Unit) {
    pose.rippleAt = pose.tip
    tweenValue(0f, 1f, 520, LinearOutSlowInEasing) {
        pose.ripple = it
        onFraction(it)
    }
    pose.ripple = 0f
}

private suspend fun tweenValue(
    from: Float,
    to: Float,
    millis: Int,
    easing: Easing = FastOutSlowInEasing,
    onValue: (Float) -> Unit,
) {
    animate(from, to, animationSpec = tween(millis, easing = easing)) { value, _ -> onValue(value) }
}

// ---- the drawing ----------------------------------------------------------------------------

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * The user's own handle at the stage's edge, cut from the real bar's outline (see [drawHandle]),
 * so a demo shows the handle they will actually be touching. Scaled down only when the stage is
 * too short for it, and then evenly, so a tab keeps its sweeps rather than being squashed.
 */
private fun DrawScope.drawStandInBar(pose: FingerPose, handle: HandleLook, onLeft: Boolean, path: Path, glassScale: Float) {
    if (pose.barAlpha <= 0f) return
    val fullHeight = handle.heightDp * density * glassScale
    val scale = glassScale * if (fullHeight <= 0f) 1f else minOf(1f, size.height * 0.55f / fullHeight)
    drawHandle(
        look = handle,
        edgeX = if (onLeft) 0f else size.width,
        centerY = pose.barAt.y,
        edgeOnLeft = onLeft,
        path = path,
        alpha = pose.barAlpha,
        scale = scale,
    )
}

private fun DrawScope.drawTouchRings(pose: FingerPose) {
    val a = pose.alpha
    if (pose.ripple > 0f && pose.ripple < 1f) {
        val r = mix(10.dp.toPx(), 34.dp.toPx(), pose.ripple)
        val fade = 1f - pose.ripple
        drawCircle(Color.White.copy(alpha = 0.22f * fade * a), r, pose.rippleAt)
        drawCircle(Color.White.copy(alpha = 0.75f * fade * a), r, pose.rippleAt, style = Stroke(2.dp.toPx()))
    }
    if (pose.hold > 0f) {
        // A disc that grows while the press is held, a pulse running out of it so a still finger
        // visibly counts, and an arc closing round it that says how long is left.
        val grown = mix(12.dp.toPx(), 26.dp.toPx(), pose.hold)
        drawCircle(Color.White.copy(alpha = 0.22f * a), grown, pose.tip)
        val pulse = (pose.hold * 3f) % 1f
        drawCircle(
            Color.White.copy(alpha = 0.5f * (1f - pulse) * a),
            grown + pulse * 16.dp.toPx(),
            pose.tip,
            style = Stroke(1.5.dp.toPx()),
        )
        val arc = grown + 5.dp.toPx()
        drawArc(
            color = Color.White.copy(alpha = 0.95f * a),
            startAngle = -90f,
            sweepAngle = 360f * pose.hold,
            useCenter = false,
            topLeft = pose.tip - Offset(arc, arc),
            size = Size(arc * 2f, arc * 2f),
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
    if (pose.press > 0f) {
        // Where the pad meets the glass.
        drawCircle(Color.White.copy(alpha = 0.35f * pose.press * a), 11.dp.toPx() * (0.6f + 0.4f * pose.press), pose.tip)
    }
}

/**
 * The hand: the walkthrough's (see [drawPointingHand]), its fingertip on the touch point, as big as
 * the walkthrough draws it on a phone this size, and leaning in from the same side.
 *
 * The hand on the bar's side: a right hand, reaching in from the bottom right, for a bar on the
 * right, and for a bar on the left the left hand, its mirror, from the bottom left. The box is the
 * phone's glass, so its width is what the scene's unit is measured off.
 */
private fun DrawScope.drawHand(pose: FingerPose, onLeft: Boolean) {
    drawPointingHand(
        tip = pose.tip,
        fingerWidth = size.width / DeviceArt.SCREEN_W * DeviceArt.FINGER_W,
        tilt = if (onLeft) -DeviceArt.HAND_TILT else DeviceArt.HAND_TILT,
        press = pose.press,
        alpha = pose.alpha,
        mirrored = onLeft,
    )
}
