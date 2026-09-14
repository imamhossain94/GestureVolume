package com.newagedevs.gesturevolume.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The quiet pictures a preview is judged against.
 *
 * They replace photographs downloaded from the web, which were the wrong tool twice over. A photo
 * is busy exactly where the subject stands — a flower, a face, a skyline behind a 10dp bar — so the
 * thing being chosen was lost in it; and it needed a network, so offline the stage was blank.
 *
 * Every backdrop is a [Pattern] painted in a pair of tones of one colour, drawn rather than shipped:
 * nothing to download, sharp at any size and in either orientation. The list alternates a pattern
 * with a plain wash, so stepping through it goes shape, colour, shape, colour — a busy picture and a
 * calm one in turn, which is the pair of questions a translucent subject has to answer. The patterns
 * draw only in white and in the colour's own tones, so any of them can be painted in any colour —
 * which is what the random button does.
 */
object PreviewBackdrops {

    enum class Pattern { PLAIN, WAVES, HILLS, SUN, BEAM, BLOOMS, STRIPES, ARCS, BOKEH, PEAKS, CURVES, DOTS, ARCH }

    private class Backdrop(val pattern: Pattern, val light: Color, val deep: Color)

    private fun shape(pattern: Pattern, light: Long, deep: Long) = Backdrop(pattern, Color(light), Color(deep))
    private fun plain(light: Long, deep: Long) = Backdrop(Pattern.PLAIN, Color(light), Color(deep))

    private val backdrops = listOf(
        shape(Pattern.WAVES, 0xFF6FBBCB, 0xFF1E5470),   // tide
        plain(0xFFC4EAD9, 0xFF67AF92),                  // mint
        shape(Pattern.HILLS, 0xFFC9DBC7, 0xFF5F8069),   // sage hills
        plain(0xFFF4E6B0, 0xFFCFAA55),                  // butter
        shape(Pattern.SUN, 0xFFE6D2BA, 0xFFB88E69),     // dune
        plain(0xFF4C7C64, 0xFF1C352A),                  // forest
        shape(Pattern.BEAM, 0xFF59616D, 0xFF22262C),    // graphite
        plain(0xFFF5B0A3, 0xFFCF5E52),                  // coral
        shape(Pattern.BLOOMS, 0xFFF6CDAE, 0xFFDE8C6B),  // peach
        plain(0xFFD2D0A5, 0xFF878554),                  // olive
        shape(Pattern.STRIPES, 0xFF6ACBBF, 0xFF237570), // teal
        plain(0xFFDCD6CC, 0xFF9C9284),                  // stone
        shape(Pattern.ARCS, 0xFF34466A, 0xFF111827),    // midnight
        plain(0xFF7D9CCB, 0xFF2C4470),                  // denim
        shape(Pattern.BOKEH, 0xFFC3DCF3, 0xFF6597CA),   // sky
        plain(0xFFE9C46F, 0xFFA77A1F),                  // mustard
        shape(Pattern.PEAKS, 0xFF9DB0C3, 0xFF364657),   // slate peaks
        plain(0xFFA8DCCB, 0xFF4E9B87),                  // seafoam
        shape(Pattern.CURVES, 0xFFDF9A74, 0xFF964E33),  // terracotta
        plain(0xFFC9A493, 0xFF8A5E4E),                  // clay
        shape(Pattern.DOTS, 0xFFE3E6EB, 0xFF9AA3AF),    // mist
        plain(0xFF4E5D6C, 0xFF1F2831),                  // charcoal
        shape(Pattern.ARCH, 0xFFEBC1BF, 0xFFB0716F),    // dusty rose
        plain(0xFFF7C59F, 0xFFE08B4F),                  // apricot
    )

    /** How many there are. A stage cycles through them. */
    val COUNT = backdrops.size

    /**
     * Backdrop [index] — in its own colour, or, when [colorSeed] is set, the same pattern repainted
     * in a random colour. The seed rather than the colour is what a stage keeps, so the pattern and
     * the colour change independently and rotation redraws the same pair.
     */
    fun draw(scope: DrawScope, index: Int, colorSeed: Long? = null) {
        val b = backdrops[index.mod(COUNT)]
        if (colorSeed == null) {
            scope.paint(b.pattern, b.light, b.deep)
        } else {
            val (light, deep) = randomTones(colorSeed)
            scope.paint(b.pattern, light, deep)
        }
    }

    /**
     * A light and a deep tone of one random colour, the same every time for the same [seed]. Any
     * hue but the violets, never so pale or so dark that a white or a black bar disappears into it.
     */
    private fun randomTones(seed: Long): Pair<Color, Color> {
        val random = Random(seed)
        var hue = random.nextFloat() * 290f
        if (hue >= 255f) hue += 70f // skip 255°–325°, the purples
        val saturation = 0.3f + random.nextFloat() * 0.35f
        val lightness = 0.42f + random.nextFloat() * 0.2f
        val light = Color.hsl(hue % 360f, saturation, (lightness + 0.16f).coerceAtMost(0.86f))
        val deep = Color.hsl(hue % 360f, saturation, (lightness - 0.2f).coerceAtLeast(0.14f))
        return light to deep
    }

    private fun DrawScope.paint(pattern: Pattern, light: Color, deep: Color) {
        // Nothing to draw into, and the curves below step across the width: at zero they would
        // never finish.
        if (size.width <= 0f || size.height <= 0f) return
        clipRect {
            when (pattern) {
                Pattern.PLAIN -> plain(light, deep)
                Pattern.WAVES -> waves(light, deep)
                Pattern.HILLS -> hills(light, deep)
                Pattern.SUN -> sun(light, deep)
                Pattern.BEAM -> beam(light, deep)
                Pattern.BLOOMS -> blooms(light, deep)
                Pattern.STRIPES -> stripes(light, deep)
                Pattern.ARCS -> arcs(light, deep)
                Pattern.BOKEH -> bokeh(light, deep)
                Pattern.PEAKS -> peaks(light, deep)
                Pattern.CURVES -> curves(light, deep)
                Pattern.DOTS -> dots(light, deep)
                Pattern.ARCH -> arch(light, deep)
            }
        }
    }

    private val white = Color.White

    private fun DrawScope.diagonal(light: Color, deep: Color) =
        drawRect(Brush.linearGradient(listOf(light, deep), Offset.Zero, Offset(size.width, size.height)))

    private fun DrawScope.vertical(light: Color, deep: Color) =
        drawRect(Brush.verticalGradient(listOf(light, deep)))

    /** A soft diagonal wash, a pale glow in one corner and a deeper one opposite. */
    private fun DrawScope.plain(light: Color, deep: Color) {
        diagonal(light, deep)
        glow(Offset(size.width * 0.18f, size.height * 0.12f), white.copy(alpha = 0.22f), 0.75f)
        glow(Offset(size.width * 0.9f, size.height * 0.95f), deep.copy(alpha = 0.45f), 0.75f)
    }

    /** The sea: shallow to deep, three slow bands of lighter water. */
    private fun DrawScope.waves(light: Color, deep: Color) {
        vertical(light, deep)
        wave(0.3f, white.copy(alpha = 0.18f), 0f)
        wave(0.52f, white.copy(alpha = 0.14f), 1.7f)
        wave(0.74f, white.copy(alpha = 0.11f), 3.1f)
    }

    /** Hills, one behind the other, under a pale sky. */
    private fun DrawScope.hills(light: Color, deep: Color) {
        vertical(light, lerp(light, deep, 0.35f))
        hill(0.55f, 0.22f, lerp(light, deep, 0.6f))
        hill(0.74f, 0.18f, deep, phase = 1.3f)
    }

    /** A big sun in the colour's deep tone, and one fine ring around it. */
    private fun DrawScope.sun(light: Color, deep: Color) {
        diagonal(light, lerp(light, deep, 0.6f))
        val c = Offset(size.width * 0.3f, size.height * 0.42f)
        val r = size.minDimension * 0.34f
        drawCircle(deep.copy(alpha = 0.55f), radius = r, center = c)
        drawCircle(deep.copy(alpha = 0.4f), radius = r * 1.35f, center = c, style = Stroke(width = size.minDimension * 0.012f))
    }

    /** Dark, crossed by one wide shaft of soft light. */
    private fun DrawScope.beam(light: Color, deep: Color) {
        vertical(light, deep)
        val beam = Path().apply {
            moveTo(size.width * 0.35f, 0f)
            lineTo(size.width * 0.62f, 0f)
            lineTo(size.width * 0.9f, size.height)
            lineTo(size.width * 0.45f, size.height)
            close()
        }
        drawPath(beam, Brush.verticalGradient(listOf(white.copy(alpha = 0.22f), white.copy(alpha = 0.02f))))
    }

    /** Two blurred blooms, one bright and one deep, drifting into each other. */
    private fun DrawScope.blooms(light: Color, deep: Color) {
        diagonal(light, lerp(light, deep, 0.5f))
        glow(Offset(size.width * 0.25f, size.height * 0.3f), white.copy(alpha = 0.4f), 0.7f)
        glow(Offset(size.width * 0.78f, size.height * 0.75f), deep.copy(alpha = 0.7f), 0.7f)
    }

    /** Wide, faint diagonal bands. */
    private fun DrawScope.stripes(light: Color, deep: Color) {
        diagonal(light, deep)
        val slant = size.height * 0.6f
        val spacing = size.width * 0.26f
        var x = -slant
        var i = 0
        while (x < size.width + slant) {
            val band = Path().apply {
                moveTo(x, 0f)
                lineTo(x + spacing * 0.5f, 0f)
                lineTo(x + spacing * 0.5f - slant, size.height)
                lineTo(x - slant, size.height)
                close()
            }
            drawPath(band, white.copy(alpha = if (i % 2 == 0) 0.1f else 0.05f))
            x += spacing
            i++
        }
    }

    /** Rings spreading out from the lower corner. */
    private fun DrawScope.arcs(light: Color, deep: Color) {
        diagonal(light, deep)
        val corner = Offset(0f, size.height)
        for (k in 0 until 6) {
            drawCircle(
                white.copy(alpha = 0.16f - k * 0.02f),
                radius = size.maxDimension * (0.2f + 0.17f * k),
                center = corner,
                style = Stroke(width = size.minDimension * 0.05f),
            )
        }
    }

    /** Out-of-focus lights of different sizes. */
    private fun DrawScope.bokeh(light: Color, deep: Color) {
        diagonal(light, deep)
        val spots = listOf(
            floatArrayOf(0.15f, 0.25f, 0.16f, 0.16f),
            floatArrayOf(0.8f, 0.2f, 0.26f, 0.1f),
            floatArrayOf(0.55f, 0.62f, 0.12f, 0.18f),
            floatArrayOf(0.28f, 0.88f, 0.22f, 0.09f),
            floatArrayOf(0.92f, 0.78f, 0.14f, 0.14f),
            floatArrayOf(0.45f, 0.14f, 0.07f, 0.2f),
        )
        for ((fx, fy, fr, a) in spots) {
            drawCircle(white.copy(alpha = a), radius = size.minDimension * fr, center = Offset(size.width * fx, size.height * fy))
        }
    }

    /** Two ranges of angular mountains and a small pale moon. */
    private fun DrawScope.peaks(light: Color, deep: Color) {
        vertical(light, lerp(light, deep, 0.3f))
        drawCircle(white.copy(alpha = 0.4f), radius = size.minDimension * 0.08f, center = Offset(size.width * 0.76f, size.height * 0.2f))
        ridge(floatArrayOf(0f, 0.62f, 0.22f, 0.4f, 0.4f, 0.58f, 0.63f, 0.34f, 0.85f, 0.55f, 1f, 0.45f), lerp(light, deep, 0.6f))
        ridge(floatArrayOf(0f, 0.8f, 0.18f, 0.6f, 0.38f, 0.78f, 0.58f, 0.56f, 0.8f, 0.76f, 1f, 0.62f), deep)
    }

    /** Parallel flowing lines. */
    private fun DrawScope.curves(light: Color, deep: Color) {
        diagonal(light, deep)
        for (k in 0 until 7) {
            val base = size.height * (0.12f + k * 0.13f)
            val path = Path().apply {
                val step = size.width / 24f
                var x = 0f
                moveTo(0f, base)
                while (x <= size.width + step) {
                    lineTo(x, base + size.height * 0.07f * sin(x / size.width * 2f * PI.toFloat() + k * 0.45f))
                    x += step
                }
            }
            drawPath(path, white.copy(alpha = 0.17f), style = Stroke(width = size.minDimension * 0.014f))
        }
    }

    /** A fine grid of dots over a wash, with one soft glow. */
    private fun DrawScope.dots(light: Color, deep: Color) {
        diagonal(light, deep)
        glow(Offset(size.width * 0.7f, size.height * 0.35f), white.copy(alpha = 0.3f), 0.6f)
        val spacing = size.minDimension * 0.09f
        val r = spacing * 0.11f
        var row = 0
        var y = spacing * 0.5f
        while (y < size.height) {
            var x = if (row % 2 == 0) spacing * 0.5f else spacing
            while (x < size.width) {
                drawCircle(deep.copy(alpha = 0.35f), radius = r, center = Offset(x, y))
                x += spacing
            }
            y += spacing
            row++
        }
    }

    /** A tall arch in the deep tone, and a small pale circle beside it. */
    private fun DrawScope.arch(light: Color, deep: Color) {
        diagonal(light, lerp(light, deep, 0.45f))
        val r = size.width * 0.17f
        val cx = size.width * 0.64f
        val top = size.height * 0.28f
        val path = Path().apply {
            moveTo(cx - r, size.height)
            lineTo(cx - r, top + r)
            arcTo(Rect(cx - r, top, cx + r, top + 2 * r), 180f, 180f, false)
            lineTo(cx + r, size.height)
            close()
        }
        drawPath(path, deep.copy(alpha = 0.55f))
        drawCircle(white.copy(alpha = 0.45f), radius = size.minDimension * 0.1f, center = Offset(size.width * 0.28f, size.height * 0.32f))
    }

    private fun DrawScope.ridge(points: FloatArray, color: Color) {
        val path = Path().apply {
            moveTo(0f, size.height)
            for (i in points.indices step 2) lineTo(size.width * points[i], size.height * points[i + 1])
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, color)
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

    private fun DrawScope.glow(center: Offset, color: Color, reach: Float) {
        val radius = size.maxDimension * reach
        drawCircle(
            Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
            radius = radius,
            center = center,
        )
    }
}

/**
 * One of [PreviewBackdrops], filling whatever it is given — repainted in a random colour when
 * [colorSeed] is set.
 */
@Composable
fun PreviewBackdrop(index: Int, modifier: Modifier = Modifier, colorSeed: Long? = null) {
    Canvas(modifier = modifier) { PreviewBackdrops.draw(this, index, colorSeed) }
}
