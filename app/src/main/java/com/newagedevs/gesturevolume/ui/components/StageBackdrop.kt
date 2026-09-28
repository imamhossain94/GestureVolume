package com.newagedevs.gesturevolume.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max

/**
 * What the phone in a preview stands on — the preview cards and the walkthrough's — the way the
 * Panel style tiles stand their panels on two bright shapes: a cool wash, and three soft glows of
 * the app's colours in it, a coral, an indigo and a teal, blurred to light rather than drawn as
 * shapes, so the phone's black frame is the sharpest thing on the card.
 *
 * Deeper and dimmer in the dark theme, where the same glows at full strength would be the brightest
 * thing on the screen.
 */
@Composable
fun Modifier.stageBackdrop(): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val wash = if (dark) DarkWash else LightWash
    val strength = if (dark) 0.55f else 1f
    return drawBehind {
        drawRect(Brush.linearGradient(wash, Offset.Zero, Offset(size.width, size.height)))
        val reach = max(size.width, size.height)
        Glows.forEach { glow ->
            val centre = Offset(glow.x * size.width, glow.y * size.height)
            val radius = glow.radius * reach
            drawCircle(
                brush = Brush.radialGradient(
                    0f to glow.color.copy(alpha = glow.alpha * strength),
                    0.5f to glow.color.copy(alpha = glow.alpha * strength * 0.4f),
                    1f to glow.color.copy(alpha = 0f),
                    center = centre,
                    radius = radius,
                ),
                radius = radius,
                center = centre,
            )
        }
    }
}

private class Glow(val x: Float, val y: Float, val radius: Float, val color: Color, val alpha: Float)

/** Where the glows sit, as fractions of the card, and how far each reaches, of its longer side. */
private val Glows = listOf(
    Glow(0.08f, 0.12f, 0.55f, Color(0xFFFF9A76), 0.42f),
    Glow(0.92f, 0.08f, 0.45f, Color(0xFF7C83FF), 0.34f),
    Glow(0.9f, 0.95f, 0.6f, Color(0xFF34CFB6), 0.36f),
)

private val LightWash = listOf(Color(0xFFF3F2FF), Color(0xFFE7ECFF))
private val DarkWash = listOf(Color(0xFF1C2238), Color(0xFF121728))
