package com.newagedevs.gesturevolume.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
): State<PanelAnimation.Frame> {
    val id = PanelAnimation.sanitize(animation)
    val progress = remember { Animatable(0f) }
    val frame = remember { mutableStateOf(PanelAnimation.frameAt(id, 0f, towardLeft)) }

    LaunchedEffect(id, towardLeft, replayKey) {
        progress.snapTo(0f)
        frame.value = PanelAnimation.frameAt(id, 0f, towardLeft)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(PanelAnimation.durationMs(id), easing = LinearEasing),
        ) {
            frame.value = PanelAnimation.frameAt(id, value, towardLeft)
        }
        // Landed exactly, whatever the clock did on its last frame.
        frame.value = PanelAnimation.frameAt(id, 1f, towardLeft)
    }
    return frame
}

/**
 * Applies a frame to whatever it is attached to.
 *
 * Draw-layer properties and a clip, and deliberately nothing else: none of it changes a measured
 * size, so none of it can move the panel's rectangle away from the pane of blurred glass sitting
 * behind it. See the note on [PanelAnimation].
 */
fun Modifier.panelFrame(frame: PanelAnimation.Frame): Modifier = this
    .graphicsLayer {
        alpha = frame.alpha
        scaleX = frame.scaleX
        scaleY = frame.scaleY
        translationX = frame.translationX.dp.toPx()
        translationY = frame.translationY.dp.toPx()
        rotationZ = frame.rotationZ
        rotationX = frame.rotationX
        rotationY = frame.rotationY
        // Far enough back that a 60° turn reads as a turn rather than as a shear. The default is
        // eight times the density, which on a panel this wide is nearly flat.
        cameraDistance = 18f * density
        transformOrigin = TransformOrigin(frame.originX, frame.originY)
    }
    .then(
        if (frame.revealFrom <= 0f && frame.revealTo >= 1f) {
            Modifier
        } else {
            Modifier.drawWithContent {
                val top = size.height * frame.revealFrom
                val bottom = size.height * frame.revealTo
                if (bottom <= top) return@drawWithContent
                clipRect(top = top, bottom = bottom) { this@drawWithContent.drawContent() }
            }
        }
    )
