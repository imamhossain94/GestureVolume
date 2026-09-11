package com.newagedevs.gesturevolume.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.newagedevs.gesturevolume.utils.PanelAnimation

/**
 * Plays one of [PanelAnimation]'s entrances and reports where it has got to.
 *
 * The clock is a plain linear tween: all the shape of the motion — the easing, the overshoot — is
 * in [PanelAnimation.frameAt], so that a panel playing it and a settings preview scrubbing it get
 * the same curve rather than two curves that happen to have been tuned to look alike.
 *
 * @param replayKey change it to run the animation again from the start. The settings preview does
 *   this every time a different animation is picked; a live panel never does, because it is only
 *   ever composed once per opening.
 */
@Composable
fun rememberPanelEntrance(
    animation: String,
    towardLeft: Boolean,
    replayKey: Any? = null,
    /** Set to play the same animation backwards, for a panel on its way out. */
    closing: Boolean = false,
    /** Scales every duration. 1 is the catalogue's own timing. */
    speed: Float = 1f,
    /**
     * Holds the clock until the window's frames are coming quickly, for a panel that is composed
     * in a window of its own the moment it opens. See [awaitSmoothFrames].
     */
    settle: Boolean = false,
): State<PanelAnimation.Frame> {
    val id = PanelAnimation.sanitize(animation)
    val progress = remember { Animatable(0f) }
    val frame = remember { mutableStateOf(PanelAnimation.frameAt(id, 0f, towardLeft)) }
    val millis = PanelAnimation.scaledDurationMs(id, speed)

    LaunchedEffect(id, towardLeft, replayKey) {
        progress.snapTo(0f)
        frame.value = PanelAnimation.frameAt(id, 0f, towardLeft)
        if (settle) awaitSmoothFrames()
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(millis, easing = LinearEasing),
        ) {
            frame.value = PanelAnimation.frameAt(id, value, towardLeft)
        }
        // Landed exactly, whatever the clock did on its last frame.
        frame.value = PanelAnimation.frameAt(id, 1f, towardLeft)
    }

    /*
     * The way out is the way in, run backwards.
     *
     * A separate exit animation would double the catalogue and halve the care each one got, and
     * the pairing is what makes a panel feel like one object: whatever it did to arrive, it undoes
     * to leave. Started from wherever the entrance had got to, so dismissing a panel that is still
     * opening reverses from there rather than snapping to the end first.
     */
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(millis, easing = LinearEasing),
        ) {
            frame.value = PanelAnimation.frameAt(id, value, towardLeft)
        }
    }
    return frame
}

/**
 * Waits until two frames in a row have come in under [SETTLE_FRAME_NS] apart, or [SETTLE_MAX_NS]
 * has gone by.
 *
 * A panel with a window of its own is built from nothing the moment it opens, and its first few
 * frames are the building: measured on a phone, the Deck's first five took 110, 86, 52, 46 and
 * 28ms. An entrance whose clock started on the first of them spent a third of a second of its
 * motion inside those frames, so the panel arrived part-way through and stuttered on the way.
 * Started once the frames are quick, the whole of it plays at the display's rate. What it costs is
 * that moment of building before the panel appears, and the bar has already begun to fade by then,
 * so the gesture is answered at once either way.
 */
private suspend fun awaitSmoothFrames() {
    val start = withFrameNanos { it }
    var last = start
    var quick = 0
    while (last - start < SETTLE_MAX_NS) {
        val now = withFrameNanos { it }
        quick = if (now - last < SETTLE_FRAME_NS) quick + 1 else 0
        last = now
        if (quick >= 2) return
    }
}

/** A frame interval that is comfortably inside sixty frames a second. */
private const val SETTLE_FRAME_NS = 20_000_000L

/** The longest an entrance waits for the window to settle before it starts regardless. */
private const val SETTLE_MAX_NS = 300_000_000L

/**
 * Applies a frame to whatever it is attached to.
 *
 * Draw-layer properties and a clip, and deliberately nothing else: none of it changes a measured
 * size, so none of it can move the panel's rectangle away from the pane of blurred glass sitting
 * behind it. See the note on [PanelAnimation].
 *
 * Takes the frame as a function, read inside the layer and the draw, never in composition. Read in
 * composition, an animating frame recomposed and re-laid-out everything under it on every frame of
 * the animation: the Deck spent 6 to 18ms of each frame in layout for a strip whose layout never
 * changes, which is most of why it opened below sixty frames a second. Read here, a new frame is a
 * new set of layer properties and a redraw, and nothing else runs.
 */
fun Modifier.panelFrame(frame: () -> PanelAnimation.Frame): Modifier = this
    .graphicsLayer {
        val f = frame()
        alpha = f.alpha
        scaleX = f.scaleX
        scaleY = f.scaleY
        translationX = f.translationX.dp.toPx()
        translationY = f.translationY.dp.toPx()
        rotationZ = f.rotationZ
        rotationX = f.rotationX
        rotationY = f.rotationY
        // Far enough back that a 60° turn reads as a turn rather than as a shear. The default is
        // eight times the density, which on a panel this wide is nearly flat.
        cameraDistance = 18f * density
        transformOrigin = TransformOrigin(f.originX, f.originY)
    }
    .drawWithContent {
        val f = frame()
        if (f.revealFrom <= 0f && f.revealTo >= 1f) {
            drawContent()
        } else {
            val top = size.height * f.revealFrom
            val bottom = size.height * f.revealTo
            if (bottom > top) clipRect(top = top, bottom = bottom) { this@drawWithContent.drawContent() }
        }
    }

/** A frame that is not animating. Anything that is should pass the function form. */
fun Modifier.panelFrame(frame: PanelAnimation.Frame): Modifier = panelFrame { frame }
