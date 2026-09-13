package com.newagedevs.gesturevolume.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin

/**
 * The quiet pictures a preview is judged against.
 *
 * They replace photographs downloaded from the web, which were the wrong tool twice over. A photo
 * is busy exactly where the subject stands — a flower, a face, a skyline behind a 10dp bar — so the
 * thing being chosen was lost in it; and it needed a network, so offline the stage was blank.
 *
 * Each of these is one soft gradient and one or two large, simple shapes, drawn rather than
 * shipped: nothing to download, sharp at any size and in either orientation. They sit in the
 * middle of the brightness range on purpose, so a dark bar, a white one and a pale glass panel all
 * read against every one of them — and there is still enough going on behind a translucent panel to
 * see that it is translucent, which is the question the opacity slider asks.
 */
object PreviewBackdrops {

    /** How many there are. A stage cycles through them. */
    const val COUNT = 7

    fun draw(scope: DrawScope, index: Int) = with(scope) {
        // Nothing to draw into, and the curves below step across the width: at zero they would
        // never finish.
        if (size.width <= 0f || size.height <= 0f) return@with
        when (((index % COUNT) + COUNT) % COUNT) {
            0 -> dusk()
            1 -> sage()
            2 -> dune()
            3 -> tide()
            4 -> graphite()
            5 -> lilac()
            else -> mist()
        }
    }

    /** A sunset: indigo to rose, a low soft sun, and the horizon it is sinking toward. */
    private fun DrawScope.dusk() {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF34427A), Color(0xFF8C6A9A), Color(0xFFD99A8E))))
        val sun = Offset(size.width * 0.72f, size.height * 0.68f)
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFFD7A8), Color(0x66FFB38A), Color.Transparent),
                center = sun,
                radius = size.minDimension * 0.55f,
            ),
            radius = size.minDimension * 0.55f,
            center = sun,
        )
        drawRect(
            Color(0x33241A3A),
            topLeft = Offset(0f, size.height * 0.78f),
            size = Size(size.width, size.height * 0.22f),
        )
    }

    /** Green hills, one behind the other, under a pale sky. */
    private fun DrawScope.sage() {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFB9CDB8), Color(0xFF7E9C86))))
        hill(0.55f, 0.22f, Color(0xFF6A8B73))
        hill(0.72f, 0.18f, Color(0xFF4F705B), phase = 1.3f)
    }

    /** Sand, with a big clay-coloured sun and one fine ring around it. */
    private fun DrawScope.dune() {
        drawRect(Brush.linearGradient(listOf(Color(0xFFE6D2BA), Color(0xFFC09A76)), Offset.Zero, Offset(size.width, size.height)))
        val c = Offset(size.width * 0.3f, size.height * 0.42f)
        val r = size.minDimension * 0.34f
        drawCircle(Color(0xFFB27B55).copy(alpha = 0.55f), radius = r, center = c)
        drawCircle(Color(0xFF8E5A3A).copy(alpha = 0.35f), radius = r * 1.35f, center = c, style = Stroke(width = size.minDimension * 0.012f))
    }

    /** The sea: deep to shallow, three slow bands of lighter water. */
    private fun DrawScope.tide() {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF4FA8BB), Color(0xFF1E5470))))
        wave(0.35f, Color(0x2EFFFFFF), 0f)
        wave(0.55f, Color(0x24FFFFFF), 1.7f)
        wave(0.76f, Color(0x1FFFFFFF), 3.1f)
    }

    /** Charcoal, crossed by one wide shaft of soft light. */
    private fun DrawScope.graphite() {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF4A515C), Color(0xFF262A31))))
        val beam = Path().apply {
            moveTo(size.width * 0.35f, 0f)
            lineTo(size.width * 0.62f, 0f)
            lineTo(size.width * 0.9f, size.height)
            lineTo(size.width * 0.45f, size.height)
            close()
        }
        drawPath(beam, Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x05FFFFFF))))
    }

    /** Violet, with two blurred blooms of pink and blue drifting into each other. */
    private fun DrawScope.lilac() {
        drawRect(Brush.linearGradient(listOf(Color(0xFF7B67AE), Color(0xFFB9A2D6)), Offset(0f, size.height), Offset(size.width, 0f)))
        bloom(Offset(size.width * 0.25f, size.height * 0.3f), Color(0x88F5A9CF))
        bloom(Offset(size.width * 0.78f, size.height * 0.75f), Color(0x7796B7F2))
    }

    /** Pale grey mist, and a single thin circle: the lightest of them, for judging dark bars. */
    private fun DrawScope.mist() {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFE1E4E9), Color(0xFFAFB6C0))))
        drawCircle(
            Color(0x40FFFFFF),
            radius = size.minDimension * 0.42f,
            center = Offset(size.width * 0.62f, size.height * 0.45f),
        )
        drawCircle(
            Color(0x33586070),
            radius = size.minDimension * 0.42f,
            center = Offset(size.width * 0.62f, size.height * 0.45f),
            style = Stroke(width = size.minDimension * 0.008f),
        )
    }

    private fun DrawScope.hill(top: Float, amplitude: Float, color: Color, phase: Float = 0f) {
        val path = Path().apply {
            moveTo(0f, size.height)
            var x = 0f
            val step = size.width / 24f
            while (x <= size.width + step) {
                val y = size.height * (top - amplitude * 0.5f * sin(x / size.width * PI.toFloat() + phase))
                lineTo(x, y)
                x += step
            }
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, color)
    }

    private fun DrawScope.wave(at: Float, color: Color, phase: Float) {
        val band = size.height * 0.1f
        val path = Path().apply {
            val step = size.width / 24f
            moveTo(0f, size.height * at)
            var x = 0f
            while (x <= size.width + step) {
                lineTo(x, size.height * at + band * 0.4f * sin(x / size.width * 2f * PI.toFloat() + phase))
                x += step
            }
            x = size.width + step
            while (x >= -step) {
                lineTo(x, size.height * at + band + band * 0.4f * sin(x / size.width * 2f * PI.toFloat() + phase + 0.6f))
                x -= step
            }
            close()
        }
        drawPath(path, color)
    }

    private fun DrawScope.bloom(center: Offset, color: Color) {
        val radius = size.minDimension * 0.7f
        drawCircle(
            Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
            radius = radius,
            center = center,
        )
    }
}

/** One of [PreviewBackdrops], filling whatever it is given. */
@Composable
fun PreviewBackdrop(index: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) { PreviewBackdrops.draw(this, index) }
}
