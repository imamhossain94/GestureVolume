package com.newagedevs.gesturevolume.ui.view

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.core.graphics.ColorUtils
import com.newagedevs.gesturevolume.utils.SliderFill
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Quick panel's pictures: every fill style that paints a scene of its own, apart from the ones
 * that already looked right — Galaxy, Nebula and Ember stay in [QuickSliderView] as they were, and
 * the two tides are the fill's own outline.
 *
 * Three rules, each the reason the fills these replace looked cheap or ran slow.
 *
 *  1. **Light, not paint.** Whatever glows is a soft falloff — a radial gradient, a feathered mesh,
 *     a filtered image — never a hard-edged shape in a bright colour. A disc with an edge is a
 *     sticker; the same colour fading to nothing is a light.
 *  2. **Loops without a seam.** Every motion completes a whole number of turns of the fill's clock,
 *     so the last frame of a cycle is the first frame of the next. Anything at a fractional speed
 *     jumps once a cycle, and a jump reads as the animation breaking.
 *  3. **Nothing new per frame.** Gradients are made once, at unit size, and moved into place by a
 *     matrix; the few images are made once per size. A gradient built anew every frame is a texture
 *     uploaded every frame, and on a phone that alone held a fill under sixty frames a second.
 *
 * Everything is drawn in the view's coordinates, over the part of the track [QuickSliderView] has
 * already clipped to the fill.
 */
internal class FillArt(private val density: Float) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val matrix = Matrix()
    private val path = Path()

    private val glows = HashMap<Int, RadialGradient>()
    private val rings = HashMap<Int, RadialGradient>()
    private val grounds = HashMap<String, LinearGradient>()

    /**
     * Paints [style] over the fill in [p], its palette: the style's own, or the user's colours
     * spread over it by [SliderFill.paletteWith].
     *
     * Handed in rather than looked up, so that the caller decides whose colours these are and
     * works that out once rather than on every frame. The palette is baked into the gradients,
     * tables and images kept here, so one of these is for one set of colours: a caller whose
     * colours change makes a new one rather than handing this one a different palette.
     */
    fun draw(
        canvas: Canvas,
        style: String,
        p: LongArray,
        r: RectF,
        fillTop: Float,
        phase: Float,
        value: Float,
        alpha: Int,
    ) {
        if (p.isEmpty() || r.width() <= 0f || r.bottom - fillTop <= 0f) return
        val a = alpha / 255f
        when (style) {
            SliderFill.LIQUID -> liquid(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.VU_METER -> meter(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.WAVEFORM -> waveform(canvas, p, r, fillTop, phase, value, alpha, a)
            SliderFill.SUNRISE -> sunrise(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.SPECTRUM -> spectrum(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.SILK -> silk(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.AURORA -> aurora(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.PLASMA -> plasma(canvas, p, r, fillTop, phase, alpha)
            SliderFill.HOLOGRAM -> hologram(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.SONAR -> sonar(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.CIRCUIT -> circuit(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.DOT_MATRIX -> dotMatrix(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.CYBERPUNK -> cyberpunk(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.MATRIX_RAIN -> matrixRain(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.RUNE -> rune(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.FIREFLIES -> fireflies(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.SNOWFALL -> snowfall(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.HEARTBEAT -> heartbeat(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.NEON -> neon(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.OCEAN -> ocean(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.GRADIENT -> gradient(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.CONFETTI -> confetti(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.WARP -> warp(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.STORM -> storm(canvas, p, r, fillTop, phase, alpha, a)
            SliderFill.FIREWORKS -> fireworks(canvas, p, r, fillTop, phase, alpha, a)
        }
        paint.shader = null
    }

    // ---- the toolkit --------------------------------------------------------------------------

    private fun c(p: LongArray, i: Int): Int = p[i % p.size].toInt()

    /** [color] with its own alpha scaled by [f]. */
    private fun fade(color: Int, f: Float): Int =
        ((((color ushr 24) and 0xFF) * f.coerceIn(0f, 1f)).toInt() shl 24) or (color and 0xFFFFFF)

    private fun a255(f: Float): Int = (f * 255f).toInt().coerceIn(0, 255)

    private fun seed(n: Int): Float = SliderFill.pseudoRandom(n)

    /** Where a seeded particle is in its life, 0..1, living a whole number of lives a cycle. */
    private fun life(phase: Float, seed: Float, offset: Float): Float =
        (phase * (1f + (seed * 2f).toInt()) + offset) % 1f

    /**
     * A soft light of [color] centred on ([cx], [cy]): full at the middle, a third by halfway, gone
     * at the radii. The falloff is what makes it read as light, and the knee is what gives it a core.
     */
    private fun glow(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int, strength: Float) {
        if (strength <= 0.004f || rx <= 0.5f || ry <= 0.5f) return
        val shader = glows.getOrPut(color) {
            RadialGradient(
                0f, 0f, 1f,
                intArrayOf(color, fade(color, 0.32f), color and 0xFFFFFF),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        matrix.setScale(rx, ry)
        matrix.postTranslate(cx, cy)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        paint.alpha = a255(strength)
        canvas.drawRect(cx - rx, cy - ry, cx + rx, cy + ry, paint)
        paint.shader = null
    }

    /** A soft ring of [color] at [radius], as wide as a twentieth of it. */
    private fun ring(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, strength: Float) {
        if (strength <= 0.004f || radius <= 0.5f) return
        val shader = rings.getOrPut(color) {
            RadialGradient(
                0f, 0f, 1f,
                intArrayOf(color and 0xFFFFFF, fade(color, 0.6f), color, color and 0xFFFFFF),
                floatArrayOf(0.8f, 0.9f, 0.95f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        matrix.setScale(radius, radius)
        matrix.postTranslate(cx, cy)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        paint.alpha = a255(strength)
        canvas.drawRect(cx - radius, cy - radius, cx + radius, cy + radius, paint)
        paint.shader = null
    }

    /** A vertical gradient across the fill, [colors] from [top] down to [bottom]. */
    private fun ground(
        canvas: Canvas,
        key: String,
        colors: IntArray,
        stops: FloatArray?,
        r: RectF,
        top: Float,
        bottom: Float,
        alpha: Int,
    ) {
        val shader = grounds.getOrPut(key) { LinearGradient(0f, 0f, 0f, 1f, colors, stops, Shader.TileMode.CLAMP) }
        matrix.setScale(1f, (bottom - top).coerceAtLeast(1f))
        matrix.postTranslate(0f, top)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        paint.alpha = alpha
        canvas.drawRect(r.left, top, r.right, bottom, paint)
        paint.shader = null
    }

    /** Specks rising through the whole track, faded in and out, swaying as they go. */
    private fun motes(
        canvas: Canvas,
        r: RectF,
        fillTop: Float,
        phase: Float,
        count: Int,
        base: Int,
        color: Int,
        strength: Float,
        sizeDp: Float,
    ) {
        val track = r.height()
        val w = r.width()
        paint.color = color
        for (i in 0 until count) {
            val s = seed(base + i * 73)
            val s2 = seed(base + i * 29 + 3)
            val lf = life(phase, s, s2)
            val y = r.bottom - track * lf
            if (y < fillTop) continue
            val x = r.left + w * (0.15f + 0.7f * s) + sin(lf * 3f * TAU + s * 9f) * w * 0.08f
            paint.alpha = a255(strength * sin(lf * PI.toFloat()).coerceAtLeast(0f))
            canvas.drawCircle(x, y, (sizeDp * (0.6f + 0.8f * s2)) * density, paint)
        }
    }

    // ---- Liquid ---------------------------------------------------------------------------------

    private fun liquid(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val crest = SliderFill.WAVE_AMPLITUDE_DP * density
        val top = fillTop - crest
        val w = r.width()
        val h = r.bottom - top
        val track = r.height().coerceAtLeast(1f)
        ground(canvas, "liquid", intArrayOf(c(p, 0), c(p, 1), c(p, 2)), floatArrayOf(0f, 0.3f, 1f), r, top, r.bottom, alpha)
        // Caustics: the pools of light a rippling surface throws down into the water under it.
        for (i in 0 until 3) {
            val s = seed(i * 41 + 7)
            val turn = phase * TAU * (1 + i % 2) + s * TAU
            val cx = r.left + w * (0.5f + 0.45f * sin(turn))
            val cy = top + h * (0.08f + 0.2f * s) + 2.5f * density * cos(turn * 2f)
            glow(canvas, cx, cy, w * 0.7f, 8f * density, Color.WHITE, 0.2f * a)
        }
        // The meniscus, where the light catches the surface.
        ground(canvas, "liquidLip", intArrayOf(0x8CFFFFFF.toInt(), 0x00FFFFFF), null, r, top, top + 11f * density, alpha)
        // Bubbles, rising through the whole track, so a low level has as many as a high one.
        line.strokeWidth = 0.8f * density
        line.color = Color.WHITE
        for (i in 0 until 12) {
            val s = seed(i * 61 + 7)
            val s2 = seed(i * 89 + 23)
            val lf = life(phase, s, s2)
            val y = r.bottom - track * lf
            if (y < fillTop + 2f * density) continue
            val x = r.left + w * (0.18f + s * 0.64f) + sin(lf * 4f * TAU + s * TAU) * w * 0.06f
            val rad = (1.2f + s2 * 2f) * density
            // Born out of nothing at the bottom, gone to nothing under the surface.
            val f = (lf * 10f).coerceAtMost(1f) * ((y - fillTop) / (14f * density)).coerceIn(0f, 1f) * a
            paint.color = Color.WHITE
            paint.alpha = a255(0.1f * f)
            canvas.drawCircle(x, y, rad * 1.8f, paint)
            line.alpha = a255(0.55f * f)
            canvas.drawCircle(x, y, rad, line)
            paint.color = Color.WHITE
            paint.alpha = a255(0.85f * f)
            canvas.drawCircle(x - rad * 0.35f, y - rad * 0.35f, rad * 0.32f, paint)
        }
    }

    // ---- Level meter ----------------------------------------------------------------------------

    private var meterShader: LinearGradient? = null

    private fun meter(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val w = r.width()
        val track = r.height().coerceAtLeast(1f)
        ground(canvas, "meterGround", intArrayOf(0xFF10151C.toInt(), 0xFF06080B.toInt()), null, r, fillTop, r.bottom, alpha)
        // One gradient for the whole track, green at the foot to red at the head, so every segment
        // takes its colour from how high it sits and a quiet level never reaches the red.
        val shader = meterShader ?: LinearGradient(
            0f, 0f, 0f, 1f,
            intArrayOf(c(p, 2), c(p, 2), c(p, 1), c(p, 0), c(p, 0)),
            floatArrayOf(0f, 0.1f, 0.28f, 0.52f, 1f),
            Shader.TileMode.CLAMP,
        ).also { meterShader = it }
        matrix.setScale(1f, track)
        matrix.postTranslate(0f, r.top)
        shader.setLocalMatrix(matrix)
        // The backlight: the meter's own colours glowing behind the segments.
        paint.shader = shader
        paint.alpha = a255(0.1f * a)
        canvas.drawRect(r.left, fillTop, r.right, r.bottom, paint)
        val pitch = 4.5f * density
        val seg = pitch * 0.62f
        val inset = w * 0.17f
        var y = r.bottom - pitch * 0.85f
        var n = 0
        while (y + seg > fillTop) {
            // A pulse running up the stack, twice a cycle: a meter standing still looks switched off.
            paint.alpha = a255((0.72f + 0.28f * sin(n * 0.55f - phase * 2f * TAU)) * a)
            canvas.drawRoundRect(r.left + inset, y, r.right - inset, y + seg, seg / 2f, seg / 2f, paint)
            y -= pitch
            n++
        }
        paint.shader = null
        // The peak hold, floating just over the level and settling back.
        val bob = (sin(phase * TAU) + 1f) / 2f
        val peakY = fillTop + pitch * (0.4f + bob * 1.4f)
        glow(canvas, r.centerX(), peakY + seg * 0.35f, w * 0.5f, seg * 1.6f, c(p, 3), 0.35f * a)
        paint.color = c(p, 3)
        paint.alpha = alpha
        canvas.drawRoundRect(r.left + inset, peakY, r.right - inset, peakY + seg * 0.7f, seg / 2f, seg / 2f, paint)
    }

    // ---- Waveform -------------------------------------------------------------------------------

    private fun waveform(
        canvas: Canvas,
        p: LongArray,
        r: RectF,
        fillTop: Float,
        phase: Float,
        value: Float,
        alpha: Int,
        a: Float,
    ) {
        val track = r.height().coerceAtLeast(1f)
        val h = (r.bottom - fillTop).coerceAtLeast(1f)
        ground(canvas, "waveGround", intArrayOf(0xFF161B4A.toInt(), 0xFF090D26.toInt()), null, r, fillTop, r.bottom, alpha)
        val cx = r.centerX()
        // Louder is wider, as on any signal.
        val swing = r.width() * (0.12f + 0.26f * value)
        val step = 3f * density
        for (i in 0 until 3) {
            path.reset()
            var y = r.bottom
            var first = true
            val spatial = 17f + i * 6f
            // One, two and three turns a cycle, the middle one the other way: whole turns loop.
            val speed = (i + 1) * TAU * (if (i == 1) -1f else 1f)
            while (y >= fillTop - step) {
                val k = (r.bottom - y) / track
                // Tapered to nothing at both ends of the fill, so it reads as a signal, not a stripe.
                val envelope = sin(((r.bottom - y) / h).coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f)
                val x = cx + swing * envelope * sin(k * spatial + phase * speed + i * 2.1f)
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                y -= step
            }
            val color = c(p, i)
            // Blended normally. Added light looked the same and was a disaster to draw: an
            // antialiased stroke in an additive mode made the renderer read back what was under
            // every edge, and three traces ran at a quarter of the frame rate.
            line.color = color
            line.alpha = a255(0.22f * a)
            line.strokeWidth = 6f * density
            canvas.drawPath(path, line)
            line.alpha = a255(0.95f * a)
            line.strokeWidth = 1.7f * density
            canvas.drawPath(path, line)
        }
    }

    // ---- Sunrise --------------------------------------------------------------------------------

    private var raysShader: SweepGradient? = null

    private fun sunrise(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val w = r.width()
        ground(
            canvas, "sunSky", intArrayOf(c(p, 0), c(p, 1), c(p, 2), c(p, 3)), floatArrayOf(0f, 0.2f, 0.52f, 1f),
            r, fillTop, r.bottom, alpha,
        )
        val cx = r.centerX()
        // The sun just under the level, whole. Sat on the line it was cut in half, and the half that
        // was left read as a smudge rather than a sun.
        val sunY = fillTop + w * 0.55f
        // Beams fanning out from the sun, turning one beam's spacing a cycle.
        val rays = raysShader ?: buildRays().also { raysShader = it }
        matrix.setRotate(phase * 360f / RAYS)
        matrix.postTranslate(cx, sunY)
        rays.setLocalMatrix(matrix)
        paint.shader = rays
        paint.alpha = a255(0.8f * a)
        canvas.drawRect(r.left, fillTop, r.right, r.bottom, paint)
        paint.shader = null
        // The haze, the glow, and a core bright enough to be the thing giving the light.
        glow(canvas, cx, sunY, w * 1.7f, w * 1.7f, 0xFFFFC46B.toInt(), 0.6f * a)
        glow(canvas, cx, sunY, w * 0.7f, w * 0.7f, 0xFFFFE9B8.toInt(), a)
        glow(canvas, cx, sunY, w * 0.3f, w * 0.3f, 0xFFFFFBF0.toInt(), a)
        motes(canvas, r, fillTop, phase, 10, 13, 0xFFFFF1C9.toInt(), 0.7f * a, 0.75f)
    }

    private fun buildRays(): SweepGradient {
        val colors = IntArray(RAYS * 2 + 1)
        val stops = FloatArray(RAYS * 2 + 1)
        for (i in 0 until RAYS) {
            colors[i * 2] = 0x00FFF1C9
            stops[i * 2] = i.toFloat() / RAYS
            colors[i * 2 + 1] = 0x66FFF1C9
            stops[i * 2 + 1] = (i + 0.5f) / RAYS
        }
        colors[RAYS * 2] = 0x00FFF1C9
        stops[RAYS * 2] = 1f
        return SweepGradient(0f, 0f, colors, stops)
    }

    // ---- Spectrum: iridescent foil --------------------------------------------------------------

    private var foilStrip: BitmapShader? = null
    private var foilTrack = -1f
    private var glintShader: LinearGradient? = null

    private fun spectrum(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val track = r.height().coerceAtLeast(1f)
        val w = r.width()
        // One mirrored period of the palette in a strip a pixel wide, built once per track and slid
        // upward two spans a cycle, which is exactly one period.
        val strip = foilStrip?.takeIf { foilTrack == track } ?: buildFoil(p, track)
        matrix.setScale(1f, -1f)
        matrix.postTranslate(r.left, r.bottom - phase * track * 2f)
        strip.setLocalMatrix(matrix)
        paint.shader = strip
        paint.alpha = alpha
        canvas.drawRect(r.left, fillTop, r.right, r.bottom, paint)
        // A glint crossing once a cycle, from under the fill to over the track, so it is never cut off.
        val glint = glintShader ?: LinearGradient(
            0f, 0f, 0f, 1f,
            intArrayOf(0x00FFFFFF, 0x73FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        ).also { glintShader = it }
        val band = 36f * density
        val y = r.bottom + band - phase * (track + band * 2f)
        matrix.setScale(1f, band)
        matrix.postTranslate(0f, y - band / 2f)
        matrix.postSkew(0f, -0.7f, r.left, y)
        glint.setLocalMatrix(matrix)
        paint.shader = glint
        canvas.drawRect(r.left, fillTop, r.right, r.bottom, paint)
        paint.shader = null
        // Pin-points catching the light.
        for (i in 0 until 7) {
            val s = seed(i * 17 + 1)
            val s2 = seed(i * 43 + 5)
            val yy = r.bottom - track * s2
            if (yy < fillTop) continue
            val twinkle = sin(phase * TAU * (1 + (s * 3f).toInt()) + s2 * TAU)
            if (twinkle <= 0.2f) continue
            glow(canvas, r.left + w * (0.12f + 0.76f * s), yy, 3.5f * density, 3.5f * density, Color.WHITE, (twinkle - 0.2f) / 0.8f * 0.9f * a)
        }
    }

    private fun buildFoil(p: LongArray, track: Float): BitmapShader {
        val period = (track * 2f).toInt().coerceIn(2, 8192)
        // The palette as a loop, ending back on its first colour, then mirrored: no seam anywhere.
        val keys = IntArray(p.size + 1) { c(p, it % p.size) }
        val pixels = IntArray(period) { row ->
            val f = row / track
            val g = (if (f <= 1f) f else 2f - f).coerceIn(0f, 1f) * (keys.size - 1)
            val i = g.toInt().coerceAtMost(keys.size - 2)
            ColorUtils.blendARGB(keys[i], keys[i + 1], g - i)
        }
        val strip = Bitmap.createBitmap(pixels, 1, period, Bitmap.Config.ARGB_8888)
        return BitmapShader(strip, Shader.TileMode.CLAMP, Shader.TileMode.REPEAT).also {
            foilStrip = it
            foilTrack = track
        }
    }

    // ---- Silk: satin ribbons --------------------------------------------------------------------

    private val satinVerts = FloatArray(SATIN_MAX_ROWS * 10)
    private val satinColors = IntArray(SATIN_MAX_ROWS * 5)
    private val satinPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun silk(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        ground(canvas, "silkGround", intArrayOf(0xFF3B1236.toInt(), 0xFF170919.toInt()), null, r, fillTop, r.bottom, alpha)
        for (i in p.indices) satinRibbon(canvas, r, fillTop, phase, i * 2.2f, c(p, i), a)
    }

    /**
     * One ribbon as a mesh, five vertices across at every sample: a clear feather, the ribbon's
     * shaded edge, a highlight, the other edge, another feather. The highlight slides from edge to
     * edge as the ribbon turns, which is what satin does and a flat stripe of colour does not.
     */
    private fun satinRibbon(canvas: Canvas, r: RectF, fillTop: Float, phase: Float, offset: Float, color: Int, a: Float) {
        val track = r.height().coerceAtLeast(1f)
        val w = r.width()
        val step = 5f * density
        val turn = phase * TAU
        val start = r.bottom + step
        val rows = (((start - (fillTop - step)) / step).toInt() + 1).coerceIn(2, SATIN_MAX_ROWS)
        val feather = 1.4f * density
        val clear = color and 0xFFFFFF
        val edge = fade(color, 0.5f * a)
        val sheen = fade(ColorUtils.blendARGB(color, Color.WHITE, 0.6f), 0.95f * a)
        for (i in 0 until rows) {
            val y = start - i * step
            val k = (r.bottom - y) / track
            val centre = r.centerX() + w * 0.3f * sin(k * 4.2f + turn + offset)
            val half = w * (0.19f + 0.07f * sin(k * 2.7f - turn + offset))
            val light = centre + half * 0.55f * sin(k * 3.1f + turn * 2f + offset)
            val v = i * 10
            satinVerts[v] = centre - half - feather
            satinVerts[v + 1] = y
            satinVerts[v + 2] = centre - half
            satinVerts[v + 3] = y
            satinVerts[v + 4] = light
            satinVerts[v + 5] = y
            satinVerts[v + 6] = centre + half
            satinVerts[v + 7] = y
            satinVerts[v + 8] = centre + half + feather
            satinVerts[v + 9] = y
            val ci = i * 5
            satinColors[ci] = clear
            satinColors[ci + 1] = edge
            satinColors[ci + 2] = sheen
            satinColors[ci + 3] = edge
            satinColors[ci + 4] = clear
        }
        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES, rows * 10, satinVerts, 0, null, 0, satinColors, 0,
            SATIN_INDICES, 0, (rows - 1) * 24, satinPaint,
        )
    }

    // ---- Aurora ---------------------------------------------------------------------------------

    private fun aurora(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val w = r.width()
        val h = (r.bottom - fillTop).coerceAtLeast(1f)
        ground(canvas, "auroraSky", intArrayOf(0xFF0A1633.toInt(), 0xFF041B1F.toInt()), null, r, fillTop, r.bottom, alpha)
        // Stars, still, behind everything.
        paint.color = Color.WHITE
        for (i in 0 until 14) {
            val s = seed(i * 19 + 3)
            val y = r.bottom - r.height() * seed(i * 57 + 11)
            if (y < fillTop) continue
            val twinkle = 0.55f + 0.45f * sin(phase * TAU * (1 + (s * 3f).toInt()) + s * 9f)
            paint.alpha = a255(0.5f * twinkle * a)
            canvas.drawCircle(r.left + w * (0.08f + 0.84f * s), y, 0.65f * density, paint)
        }
        // The curtains: tall soft lights swaying, each with a brighter hem where it is densest.
        for (i in p.indices) {
            val s = seed(i * 53 + 11)
            val turn = phase * TAU * (1 + i % 2) + s * TAU
            val cx = r.left + w * (0.5f + 0.36f * sin(turn))
            // Each colour at its own height, the way the real thing layers by altitude. All at one
            // height, in a panel this narrow, they added up to a single cyan column.
            val cy = fillTop + h * (AURORA_HEIGHTS[i % AURORA_HEIGHTS.size] + 0.06f * sin(turn * 2f + 1.3f))
            val breathe = 0.6f + 0.4f * sin(turn + 0.7f)
            glow(canvas, cx, cy, w * (0.34f + 0.1f * s), h * (0.3f + 0.08f * s), c(p, i), 0.75f * breathe * a)
            glow(
                canvas, cx, cy + h * 0.1f, w * 0.14f, h * 0.12f,
                ColorUtils.blendARGB(c(p, i), Color.WHITE, 0.4f), 0.4f * breathe * a,
            )
        }
    }

    // ---- Plasma: a lava lamp --------------------------------------------------------------------

    private val plasmaImage: Bitmap by lazy { Bitmap.createBitmap(PLASMA_COLS, PLASMA_ROWS, Bitmap.Config.ARGB_8888) }
    private val plasmaPixels = IntArray(PLASMA_COLS * PLASMA_ROWS)
    private var plasmaLut: IntArray? = null
    private val plasmaSrc = Rect(0, 0, PLASMA_COLS, PLASMA_ROWS)
    private val plasmaDst = RectF()

    /** Smoothed as it is stretched: the field is a few dozen samples, and the filter is the lamp. */
    private val plasmaPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var sheenShader: LinearGradient? = null

    private fun plasma(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int) {
        val lut = plasmaLut ?: buildLut(p).also { plasmaLut = it }
        val t = phase * TAU
        for (row in 0 until PLASMA_ROWS) {
            val y = row.toFloat() / PLASMA_ROWS
            val dy = (y - 0.5f) * 3f
            for (col in 0 until PLASMA_COLS) {
                val x = col.toFloat() / PLASMA_COLS
                val dx = x - 0.5f
                val v = (
                    sin(x * 4f + t) +
                        sin(y * 9f - t) +
                        sin(x * 3f + y * 6f + 2f * t) +
                        sin(sqrt(dx * dx * 16f + dy * dy * 4f) * 3f - t)
                    ) / 4f
                plasmaPixels[row * PLASMA_COLS + col] = lut[((v + 1f) * 127.5f).toInt().coerceIn(0, 255)]
            }
        }
        plasmaImage.setPixels(plasmaPixels, 0, PLASMA_COLS, 0, 0, PLASMA_COLS, PLASMA_ROWS)
        // Over the whole track, so the field stays put as the level moves and the fill uncovers it.
        plasmaDst.set(r.left, r.top, r.right, r.bottom)
        plasmaPaint.alpha = alpha
        canvas.drawBitmap(plasmaImage, plasmaSrc, plasmaDst, plasmaPaint)
        sideSheen(canvas, r, fillTop, alpha)
    }

    /** The palette as 256 steps round a loop, so the field's highs and lows meet in the same colour. */
    private fun buildLut(p: LongArray): IntArray {
        val keys = IntArray(p.size + 1) { c(p, it % p.size) }
        return IntArray(256) { i ->
            val g = i / 255f * (keys.size - 1)
            val k = g.toInt().coerceAtMost(keys.size - 2)
            ColorUtils.blendARGB(keys[k], keys[k + 1], g - k) or (0xFF shl 24)
        }
    }

    /** Glass down one side, which is most of what makes a picture read as a surface. */
    private fun sideSheen(canvas: Canvas, r: RectF, fillTop: Float, alpha: Int) {
        val shader = sheenShader ?: LinearGradient(
            0f, 0f, 1f, 0f,
            intArrayOf(0x4DFFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x26FFFFFF),
            floatArrayOf(0f, 0.35f, 0.72f, 1f),
            Shader.TileMode.CLAMP,
        ).also { sheenShader = it }
        matrix.setScale(r.width(), 1f)
        matrix.postTranslate(r.left, 0f)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        paint.alpha = alpha
        canvas.drawRect(r.left, fillTop, r.right, r.bottom, paint)
        paint.shader = null
    }

    // ---- Hologram -------------------------------------------------------------------------------

    private var foilShader: LinearGradient? = null
    private var scanShader: BitmapShader? = null
    private var scanPitch = 0
    private var beamShader: LinearGradient? = null

    private fun hologram(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val w = r.width()
        val track = r.height().coerceAtLeast(1f)
        ground(canvas, "holoGround", intArrayOf(0xFF06414F.toInt(), 0xFF021A22.toInt()), null, r, fillTop, r.bottom, alpha)
        // The foil: pastel bands drifting diagonally, the sheen a hologram sticker has. Mirrored,
        // so one period is two spans, and it moves exactly one period a cycle.
        val foil = foilShader ?: LinearGradient(
            0f, 0f, 1f, 0f,
            intArrayOf(c(p, 0), c(p, 1), c(p, 2), c(p, 0)),
            null,
            Shader.TileMode.MIRROR,
        ).also { foilShader = it }
        val period = w * 1.8f
        matrix.setScale(period, 1f)
        matrix.postTranslate(phase * period * 2f, 0f)
        matrix.postRotate(-38f)
        matrix.postTranslate(r.left, r.bottom)
        foil.setLocalMatrix(matrix)
        paint.shader = foil
        paint.alpha = a255(0.5f * a)
        canvas.drawRect(r.left, fillTop, r.right, r.bottom, paint)
        // Scan lines, fine and faint, creeping upward four lines a cycle.
        val pitch = (3f * density).toInt().coerceAtLeast(3)
        val scan = scanShader?.takeIf { scanPitch == pitch } ?: buildScan(pitch)
        matrix.setTranslate(r.left, r.bottom - phase * pitch * 4f)
        scan.setLocalMatrix(matrix)
        paint.shader = scan
        paint.alpha = alpha
        canvas.drawRect(r.left, fillTop, r.right, r.bottom, paint)
        // A beam sweeping up once a cycle, from under the fill to over the track.
        val beam = beamShader ?: LinearGradient(
            0f, 0f, 0f, 1f,
            intArrayOf(0x00FFFFFF, fade(c(p, 0), 0.55f), 0xB3FFFFFF.toInt(), fade(c(p, 0), 0.55f), 0x00FFFFFF),
            floatArrayOf(0f, 0.35f, 0.5f, 0.65f, 1f),
            Shader.TileMode.CLAMP,
        ).also { beamShader = it }
        val band = 30f * density
        val y = r.bottom + band - phase * (track + band * 2f)
        matrix.setScale(1f, band)
        matrix.postTranslate(0f, y - band / 2f)
        beam.setLocalMatrix(matrix)
        paint.shader = beam
        canvas.drawRect(r.left, fillTop, r.right, r.bottom, paint)
        paint.shader = null
        for (i in 0 until 6) {
            val s = seed(i * 23 + 9)
            val lf = life(phase, s, seed(i * 67 + 1))
            val yy = r.bottom - track * lf
            if (yy < fillTop) continue
            val f = sin(lf * PI.toFloat()).coerceAtLeast(0f)
            glow(canvas, r.left + w * (0.15f + 0.7f * s), yy, 3f * density, 3f * density, c(p, 0), 0.9f * f * a)
        }
    }

    private fun buildScan(pitch: Int): BitmapShader {
        val tile = Bitmap.createBitmap(1, pitch, Bitmap.Config.ARGB_8888)
        tile.setPixel(0, 0, 0x59FFFFFF)
        return BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).also {
            scanShader = it
            scanPitch = pitch
        }
    }

    // ---- Sonar ----------------------------------------------------------------------------------

    private fun sonar(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val w = r.width()
        val h = (r.bottom - fillTop).coerceAtLeast(1f)
        ground(canvas, "sonarGround", intArrayOf(0xFF07343F.toInt(), 0xFF04141F.toInt()), null, r, fillTop, r.bottom, alpha)
        val cx = r.centerX()
        glow(canvas, cx, fillTop, w * 1.1f, w * 1.1f, c(p, 1), 0.5f * a)
        // Four rings a quarter of a cycle apart, each leaving once a cycle: always one arriving.
        for (i in 0 until 4) {
            val lf = (phase + i / 4f) % 1f
            val radius = w * 0.18f + lf * (h + w) * 0.95f
            val f = (1f - lf) * (1f - lf) * (lf * 7f).coerceAtMost(1f)
            ring(canvas, cx, fillTop, radius, c(p, 0), 0.95f * f * a)
        }
        paint.color = c(p, 0)
        paint.alpha = a255(0.9f * a)
        canvas.drawCircle(cx, fillTop, 2f * density, paint)
        motes(canvas, r, fillTop, phase, 8, 31, c(p, 0), 0.45f * a, 0.6f)
    }

    // ---- Circuit --------------------------------------------------------------------------------

    private var board: Bitmap? = null
    private val boardDst = RectF()
    private var lanes = FloatArray(0)

    private fun circuit(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val track = r.height().coerceAtLeast(1f)
        ground(canvas, "pcb", intArrayOf(0xFF06261E.toInt(), 0xFF021410.toInt()), null, r, fillTop, r.bottom, alpha)
        val traces = boardFor(r.width().toInt().coerceAtLeast(1), track.toInt().coerceAtLeast(1), c(p, 1))
        paint.alpha = alpha
        boardDst.set(r.left, r.top, r.right, r.bottom)
        canvas.drawBitmap(traces, null, boardDst, paint)
        // The lanes were laid out on the image, which may be stretched a little to fit.
        val laneScale = r.width() / traces.width
        // Pulses running up the traces, each with a fading tail. They enter below the track and
        // leave above it, so no pulse is ever cut off by the loop.
        val margin = 8f * density
        for (i in 0 until 8) {
            val s = seed(i * 37 + 5)
            val lf = life(phase, s, seed(i * 11 + 2))
            val y = r.bottom + margin - lf * (track + margin * 2f)
            if (y < fillTop - 6f * density) continue
            val x = r.left + lanes[i % lanes.size] * laneScale
            paint.color = c(p, 0)
            for (t in 1..3) {
                paint.alpha = a255(0.3f * (4 - t) / 3f * a)
                canvas.drawCircle(x, y + t * 3.2f * density, 1.1f * density, paint)
            }
            glow(canvas, x, y, 5f * density, 5f * density, c(p, 0), 0.75f * a)
            paint.color = Color.WHITE
            paint.alpha = a255(0.95f * a)
            canvas.drawCircle(x, y, 1.1f * density, paint)
        }
    }

    /**
     * The board's traces, drawn once into an image and reused, stretched to the track.
     *
     * Kept while the track is within a quarter of the size it was drawn for. The panel changes
     * size on every frame of its opening, and a board redrawn on each of those is a fresh image a
     * frame that nobody sees.
     */
    private fun boardFor(w: Int, h: Int, trace: Int): Bitmap {
        board?.let { if (abs(it.width - w) <= it.width / 4 && abs(it.height - h) <= it.height / 4) return it }
        val image = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(image)
        val n = (w / (9f * density)).toInt().coerceIn(2, 5)
        lanes = FloatArray(n) { (it + 0.5f) * w / n }
        val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
            strokeCap = Paint.Cap.ROUND
            color = trace
        }
        val pad = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trace }
        for ((li, x) in lanes.withIndex()) {
            cv.drawLine(x, 0f, x, h.toFloat(), pen)
            var y = h - 10f * density * (1f + seed(li * 7))
            var k = 0
            while (y > 0f) {
                val s = seed(li * 131 + k * 17)
                if (s > 0.45f) {
                    // A branch off to one side, ending in a pad.
                    val len = w / n * 0.42f
                    val ex = (x + (if (s > 0.72f) len else -len)).coerceIn(2f * density, w - 2f * density)
                    cv.drawLine(x, y, ex, y - len * 0.6f, pen)
                    cv.drawCircle(ex, y - len * 0.6f, 1.4f * density, pad)
                } else {
                    cv.drawCircle(x, y, 1.6f * density, pad)
                }
                y -= (14f + s * 22f) * density
                k++
            }
        }
        board = image
        return image
    }

    // ---- Dot matrix: an LED panel ---------------------------------------------------------------

    private var ledTile: BitmapShader? = null
    private var ledPitch = -1

    private fun dotMatrix(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val w = r.width()
        val track = r.height().coerceAtLeast(1f)
        ground(canvas, "ledGround", intArrayOf(0xFF0B1116.toInt(), 0xFF05080B.toInt()), null, r, fillTop, r.bottom, alpha)
        val cols = (w / (5f * density)).toInt().coerceIn(2, 10)
        // A whole number of pixels, so the tiled panel and the lit dots over it never drift apart.
        val pitch = (w / cols).toInt().coerceAtLeast(2)
        val left = r.left + (w - cols * pitch) / 2f
        val rows = (track / pitch).toInt().coerceAtLeast(1)
        // Every LED, off: a faint dot at each position, in one pass from a tile.
        val tile = ledTile?.takeIf { ledPitch == pitch } ?: buildLedTile(pitch, c(p, 2))
        matrix.setTranslate(left, r.bottom - rows * pitch)
        tile.setLocalMatrix(matrix)
        paint.shader = tile
        paint.alpha = alpha
        canvas.drawRect(r.left, fillTop, r.right, r.bottom, paint)
        paint.shader = null
        // The lit ones, and a bloom round the brightest.
        val dot = pitch * 0.3f
        val visible = min(rows, ((r.bottom - fillTop) / pitch).toInt() + 1)
        for (row in 0 until visible) {
            val cy = r.bottom - pitch * (row + 0.5f)
            for (col in 0 until cols) {
                val g = SliderFill.cellGlow(SliderFill.DOT_MATRIX, phase, col, row, cols, rows)
                if (g <= 0.06f) continue
                val cx = left + pitch * (col + 0.5f)
                paint.color = ColorUtils.blendARGB(c(p, 1), c(p, 0), g)
                // The bloom is a wider dot at low strength, not a gradient: a gradient per lit LED
                // is a separate draw with its own shader for each, and held the panel under sixty.
                if (g > 0.5f) {
                    paint.alpha = a255(0.22f * (g - 0.5f) * 2f * a)
                    canvas.drawCircle(cx, cy, pitch * 0.55f, paint)
                }
                paint.alpha = a255(g * a)
                canvas.drawCircle(cx, cy, dot, paint)
            }
        }
    }

    private fun buildLedTile(pitch: Int, color: Int): BitmapShader {
        val tile = Bitmap.createBitmap(pitch, pitch, Bitmap.Config.ARGB_8888)
        Canvas(tile).drawCircle(pitch / 2f, pitch / 2f, pitch * 0.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        return BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).also {
            ledTile = it
            ledPitch = pitch
        }
    }

    // ---- Cyberpunk: a synthwave sun -------------------------------------------------------------

    private fun cyberpunk(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val w = r.width()
        val h = (r.bottom - fillTop).coerceAtLeast(1f)
        ground(
            canvas, "synth", intArrayOf(c(p, 0), c(p, 1), c(p, 2), c(p, 3), c(p, 4)),
            floatArrayOf(0f, 0.16f, 0.42f, 0.74f, 1f), r, fillTop, r.bottom, alpha,
        )
        // The cuts across the sun's lower half, thicker toward the bottom and sliding down one
        // spacing a cycle. Thickness follows position alone, so the loop has no seam, and each cut
        // fades in over its first spacing rather than appearing.
        val start = fillTop + h * 0.34f
        val pitch = 9f * density
        val span = (r.bottom - start).coerceAtLeast(1f)
        paint.color = c(p, 4)
        var y = start + phase * pitch - pitch
        while (y < r.bottom) {
            val enter = ((y - start) / pitch).coerceIn(0f, 1f)
            if (enter > 0f) {
                val thick = pitch * (0.1f + 0.55f * ((y - start) / span).coerceIn(0f, 1f))
                paint.alpha = a255(0.9f * enter * a)
                canvas.drawRect(r.left, y, r.right, y + thick, paint)
            }
            y += pitch
        }
        // The neon rim along the level.
        glow(canvas, r.centerX(), fillTop, w * 0.95f, 8f * density, c(p, 5), 0.75f * a)
        paint.color = c(p, 5)
        paint.alpha = alpha
        canvas.drawRect(r.left, fillTop, r.right, fillTop + 1.5f * density, paint)
    }

    // ---- Matrix rain ----------------------------------------------------------------------------

    private fun matrixRain(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val w = r.width()
        val track = r.height().coerceAtLeast(1f)
        ground(canvas, "matrixGround", intArrayOf(0xFF001A0C.toInt(), 0xFF000A04.toInt()), null, r, fillTop, r.bottom, alpha)
        val cols = (w / (4f * density)).toInt().coerceIn(3, 14)
        val cw = w / cols
        val ch = 6.5f * density
        val rows = (track / ch).toInt().coerceAtLeast(1)
        for (row in 0 until rows) {
            val cy = r.bottom - ch * (row + 0.5f)
            if (cy < fillTop - ch) break
            for (col in 0 until cols) {
                val g = SliderFill.cellGlow(SliderFill.MATRIX_RAIN, phase, col, row, cols, rows)
                if (g <= 0.03f) continue
                val cx = r.left + cw * (col + 0.5f)
                val head = g > 0.93f
                val color = if (head) c(p, 0) else ColorUtils.blendARGB(c(p, 2), c(p, 1), g)
                glyph(canvas, cx, cy, cw, ch, col, row, phase, color, (if (head) 1f else g * (0.4f + 0.6f * g)) * a)
                if (head) glow(canvas, cx, cy, cw * 1.3f, ch * 1.4f, c(p, 1), 0.55f * a)
            }
        }
    }

    /** Something like a character: a stem and a tick, varying by cell and changing now and then. */
    private fun glyph(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        cw: Float,
        ch: Float,
        col: Int,
        row: Int,
        phase: Float,
        color: Int,
        strength: Float,
    ) {
        val s = seed(col * 131 + row * 17 + (phase * 6f).toInt() * 7)
        paint.color = color
        paint.alpha = a255(strength)
        val stroke = (cw * 0.16f).coerceAtLeast(1f * density)
        val top = cy - ch * 0.34f
        val bottom = cy + ch * 0.34f
        val sx = cx + (s - 0.5f) * cw * 0.3f
        canvas.drawRect(sx - stroke / 2f, top, sx + stroke / 2f, bottom, paint)
        val ty = top + (bottom - top) * (0.2f + 0.6f * seed(col * 7 + row * 3 + 1))
        if (s > 0.5f) {
            canvas.drawRect(sx - cw * 0.26f, ty - stroke / 2f, sx, ty + stroke / 2f, paint)
        } else {
            canvas.drawRect(sx, ty - stroke / 2f, sx + cw * 0.26f, ty + stroke / 2f, paint)
        }
    }

    // ---- Rune -----------------------------------------------------------------------------------

    private val runeGlyphs = arrayOfNulls<Path>(6)
    private var runeKey = -1f

    private fun rune(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val w = r.width()
        val track = r.height().coerceAtLeast(1f)
        ground(canvas, "stone", intArrayOf(0xFF2E1A0C.toInt(), 0xFF140A04.toInt()), null, r, fillTop, r.bottom, alpha)
        val cols = (w / (11f * density)).toInt().coerceIn(1, 5)
        val cw = w / cols
        val rows = (track / cw).toInt().coerceAtLeast(1)
        val ch = track / rows
        buildRunes(cw, ch)
        line.strokeWidth = 1.3f * density
        for (row in 0 until rows) {
            val top = r.bottom - ch * (row + 1)
            if (top + ch < fillTop) break
            for (col in 0 until cols) {
                // Most of the stone is blank; the carvings are the few cells that are not.
                if (seed(col * 31 + row * 7) < 0.55f) continue
                val g = SliderFill.cellGlow(SliderFill.RUNE, phase, col, row, cols, rows)
                val left = r.left + cw * col
                if (g > 0.5f) glow(canvas, left + cw / 2f, top + ch / 2f, cw * 0.8f, ch * 0.8f, c(p, 1), 0.8f * (g - 0.5f) * a)
                // Always faintly there as a carving; lit, gold.
                line.color = ColorUtils.blendARGB(c(p, 2), c(p, 0), g)
                line.alpha = a255((0.35f + 0.65f * g) * a)
                val glyph = runeGlyphs[(seed(col * 13 + row * 29) * 6f).toInt().coerceIn(0, 5)] ?: continue
                canvas.save()
                canvas.translate(left, top)
                canvas.drawPath(glyph, line)
                canvas.restore()
            }
        }
        motes(canvas, r, fillTop, phase, 8, 57, c(p, 0), 0.6f * a, 0.6f)
    }

    /** The six marks, as paths the size of a cell, built again only when the cell changes size. */
    private fun buildRunes(cw: Float, ch: Float) {
        val key = cw * 4099f + ch
        if (runeKey == key && runeGlyphs[0] != null) return
        runeKey = key
        fun mark(vararg strokes: FloatArray): Path = Path().apply {
            for (s in strokes) {
                moveTo(s[0] * cw, s[1] * ch)
                var k = 2
                while (k + 1 < s.size) {
                    lineTo(s[k] * cw, s[k + 1] * ch)
                    k += 2
                }
            }
        }
        runeGlyphs[0] = mark(
            floatArrayOf(0.4f, 0.18f, 0.4f, 0.82f),
            floatArrayOf(0.4f, 0.32f, 0.68f, 0.2f),
            floatArrayOf(0.4f, 0.5f, 0.68f, 0.38f),
        )
        runeGlyphs[1] = mark(floatArrayOf(0.5f, 0.18f, 0.72f, 0.5f, 0.5f, 0.82f, 0.28f, 0.5f, 0.5f, 0.18f))
        runeGlyphs[2] = mark(floatArrayOf(0.5f, 0.82f, 0.5f, 0.18f), floatArrayOf(0.3f, 0.38f, 0.5f, 0.18f, 0.7f, 0.38f))
        runeGlyphs[3] = mark(floatArrayOf(0.3f, 0.22f, 0.7f, 0.78f), floatArrayOf(0.7f, 0.22f, 0.3f, 0.78f))
        runeGlyphs[4] = mark(floatArrayOf(0.36f, 0.18f, 0.64f, 0.4f, 0.36f, 0.6f, 0.64f, 0.82f))
        runeGlyphs[5] = mark(
            floatArrayOf(0.32f, 0.18f, 0.32f, 0.82f),
            floatArrayOf(0.68f, 0.18f, 0.68f, 0.82f),
            floatArrayOf(0.32f, 0.24f, 0.68f, 0.52f),
            floatArrayOf(0.68f, 0.24f, 0.32f, 0.52f),
        )
    }

    // ---- Fireflies ------------------------------------------------------------------------------

    private fun fireflies(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val w = r.width()
        val h = (r.bottom - fillTop).coerceAtLeast(1f)
        val track = r.height().coerceAtLeast(1f)
        ground(
            canvas, "fireflyDusk", intArrayOf(0xFF0F2B23.toInt(), 0xFF08170F.toInt(), 0xFF030805.toInt()),
            floatArrayOf(0f, 0.55f, 1f), r, fillTop, r.bottom, alpha,
        )
        // Mist lying over the grass, lit faintly by what flies above it.
        glow(canvas, r.centerX(), r.bottom, w * 1.5f, min(h, 70f * density), c(p, 1), 0.2f * a)
        val turn = phase * TAU
        val reach = 16f * density
        for (i in 0 until FIREFLY_COUNT) {
            val s = seed(i * 47 + 5)
            val s2 = seed(i * 83 + 19)
            // Each wanders a loop of whole turns, and a different figure from its neighbour's, so
            // no two of them fly in formation. Placed on the track, not the fill, so they stay put
            // while the level moves.
            val x = r.left + w * (0.22f + 0.56f * s) + sin(turn * (1 + i % 2) + s2 * TAU) * w * 0.26f
            val y = r.bottom - track * (0.04f + 0.92f * s2) + sin(turn * (1 + i % 3) + s * TAU) * reach
            if (y < fillTop - 10f * density) continue
            // A blink or two a cycle, dark in between. Cubed, so each one glows up and goes out
            // slowly instead of switching on like a bulb.
            val beat = sin(turn * (1 + (s * 2f).toInt()) + s * 11f)
            if (beat <= 0f) continue
            val f = beat * beat * beat
            val color = c(p, i)
            glow(canvas, x, y, 10f * density, 10f * density, color, 0.6f * f * a)
            glow(canvas, x, y, 2.6f * density, 2.6f * density, ColorUtils.blendARGB(color, Color.WHITE, 0.55f), f * a)
        }
    }

    // ---- Snowfall -------------------------------------------------------------------------------

    private fun snowfall(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val w = r.width()
        val track = r.height().coerceAtLeast(1f)
        ground(canvas, "snowNight", intArrayOf(0xFF1D2D50.toInt(), 0xFF0C1429.toInt()), null, r, fillTop, r.bottom, alpha)
        // Moonlight in the air at the level, and the lying snow catching it at the foot.
        glow(canvas, r.centerX(), fillTop, w * 1.3f, 46f * density, c(p, 1), 0.22f * a)
        glow(canvas, r.centerX(), r.bottom + 6f * density, w * 1.4f, 26f * density, c(p, 0), 0.4f * a)
        val margin = 6f * density
        val span = track + margin * 2f
        val turn = phase * TAU
        for (layer in 0 until 3) {
            // The far flakes fall once a cycle and the near ones three times: whole passes, so the
            // loop has no seam, and the difference in speed is what reads as depth.
            val speed = (layer + 1).toFloat()
            val color = c(p, 2 - layer)
            for (i in 0 until SNOW_PER_LAYER[layer]) {
                val s = seed(layer * 1009 + i * 41 + 7)
                val s2 = seed(layer * 613 + i * 67 + 13)
                // Born above the track and gone below it, so no flake appears or vanishes in view.
                val y = r.top - margin + ((s2 + phase * speed) % 1f) * span
                if (y < fillTop - margin) continue
                val x = r.left + w * (0.08f + 0.84f * s) + sin(turn * (layer + 1) + s * TAU) * w * (0.05f + 0.04f * layer)
                when (layer) {
                    0 -> {
                        paint.color = color
                        paint.alpha = a255(0.45f * a)
                        canvas.drawCircle(x, y, 0.7f * density, paint)
                    }

                    1 -> {
                        paint.color = color
                        paint.alpha = a255(0.75f * a)
                        canvas.drawCircle(x, y, 1.15f * density, paint)
                    }

                    // The near ones out of focus: a soft light, not a dot.
                    else -> glow(canvas, x, y, 3.6f * density, 3.6f * density, color, 0.95f * a)
                }
            }
        }
    }

    // ---- Heartbeat: a heart monitor -------------------------------------------------------------

    private var ecgGrid: BitmapShader? = null
    private var ecgGridPitch = -1
    private var ecgTrail: LinearGradient? = null
    private val ecgPath = Path()
    private val ecgRect = RectF()
    private var ecgBuilt = false

    private fun heartbeat(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val w = r.width()
        val h = (r.bottom - fillTop).coerceAtLeast(1f)
        ground(canvas, "ecgGround", intArrayOf(0xFF071A11.toInt(), 0xFF020A06.toInt()), null, r, fillTop, r.bottom, alpha)
        // The graph paper, from a tile, pinned to the foot of the track so it never slides.
        val pitch = (8f * density).toInt().coerceAtLeast(4)
        val grid = ecgGrid?.takeIf { ecgGridPitch == pitch } ?: buildEcgGrid(pitch, c(p, 1))
        matrix.setTranslate(r.left, r.bottom)
        grid.setLocalMatrix(matrix)
        paint.shader = grid
        paint.alpha = alpha
        canvas.drawRect(r.left, fillTop, r.right, r.bottom, paint)
        paint.shader = null

        val period = ECG_BEAT_DP * density
        val amp = w * 0.36f
        // The whole trace is one path, built once for the track; what moves is the light along it.
        if (!ecgBuilt || ecgRect != r) buildEcg(r, period, amp)
        // The sweep runs from the foot to a trail's length past the level, so at both ends of the
        // cycle the lit part of the trace is out of sight and the loop has nowhere to show a seam.
        val trail = ECG_TRAIL_DP * density
        val head = r.bottom - phase * (h + trail)

        // The beat just drawn blooms, and dies away over half a beat.
        val beats = (r.bottom - head) / period - ECG_SPIKE
        val since = (beats - floor(beats)) * period
        val bloom = (1f - since / (period * 0.5f)).coerceAtLeast(0f).let { it * it }
        val spikeY = head + since
        if (spikeY >= fillTop) glow(canvas, r.centerX(), spikeY, w * 1.3f, 34f * density, c(p, 2), 0.5f * bloom * a)

        val trace = ecgTrail ?: LinearGradient(
            0f, 0f, 0f, 1f,
            intArrayOf(c(p, 0) and 0xFFFFFF, c(p, 0), fade(c(p, 0), 0.4f), c(p, 0) and 0xFFFFFF),
            floatArrayOf(0f, 0.02f, 0.3f, 1f),
            Shader.TileMode.CLAMP,
        ).also { ecgTrail = it }
        matrix.setScale(1f, trail)
        matrix.postTranslate(0f, head - trail * 0.02f)
        trace.setLocalMatrix(matrix)
        line.color = Color.WHITE
        line.shader = trace
        line.alpha = a255(0.35f * a)
        line.strokeWidth = 5f * density
        canvas.drawPath(ecgPath, line)
        line.alpha = a255(a)
        line.strokeWidth = 1.6f * density
        canvas.drawPath(ecgPath, line)
        line.shader = null

        val at = (r.bottom - head) / period
        val hx = r.centerX() + amp * ecgAt(at - floor(at))
        glow(canvas, hx, head, 9f * density, 9f * density, c(p, 0), (0.55f + 0.45f * bloom) * a)
        glow(canvas, hx, head, 2.5f * density, 2.5f * density, Color.WHITE, 0.9f * a)
    }

    /** The trace's swing at [u] through a beat, read off [ECG_KEYS]. */
    private fun ecgAt(u: Float): Float {
        var k = 2
        while (k < ECG_KEYS.size) {
            if (u <= ECG_KEYS[k]) {
                val u0 = ECG_KEYS[k - 2]
                val span = (ECG_KEYS[k] - u0).coerceAtLeast(1e-4f)
                val f = ((u - u0) / span).coerceIn(0f, 1f)
                return ECG_KEYS[k - 1] + (ECG_KEYS[k + 1] - ECG_KEYS[k - 1]) * f
            }
            k += 2
        }
        return 0f
    }

    private fun buildEcg(r: RectF, period: Float, amp: Float) {
        ecgPath.reset()
        val cx = r.centerX()
        ecgPath.moveTo(cx, r.bottom)
        var b = 0
        while (b * period < r.height()) {
            var k = 2
            while (k < ECG_KEYS.size) {
                ecgPath.lineTo(cx + amp * ECG_KEYS[k + 1], r.bottom - (b + ECG_KEYS[k]) * period)
                k += 2
            }
            b++
        }
        ecgRect.set(r)
        ecgBuilt = true
    }

    private fun buildEcgGrid(pitch: Int, color: Int): BitmapShader {
        val ink = fade(color, 0.16f)
        val pixels = IntArray(pitch * pitch) { if (it < pitch || it % pitch == 0) ink else 0 }
        val tile = Bitmap.createBitmap(pixels, pitch, pitch, Bitmap.Config.ARGB_8888)
        return BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).also {
            ecgGrid = it
            ecgGridPitch = pitch
        }
    }

    // ---- Neon: an arrow sign --------------------------------------------------------------------

    private val neonPts = FloatArray(NEON_MAX * 8)

    private fun neon(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val w = r.width()
        ground(canvas, "neonWall", intArrayOf(0xFF1A0C26.toInt(), 0xFF090410.toInt()), null, r, fillTop, r.bottom, alpha)
        val pitch = NEON_PITCH_DP * density
        val hw = min(w * 0.3f, 16f * density)
        val hh = hw * 0.55f
        val cx = r.centerX()
        val rows = min(((r.bottom - fillTop) / pitch).toInt() + 1, NEON_MAX)
        val n = p.size
        // The glass, unlit: every tube faintly there in its own colour. Lines rather than paths,
        // and one batch a colour, because a tube that is off is not worth a draw of its own.
        line.strokeWidth = 1.5f * density
        for (k in 0 until min(n, rows)) {
            var count = 0
            var i = k
            while (i < rows) {
                count = chevron(neonPts, count, cx, r.bottom - pitch * (i + 0.6f), hw, hh)
                i += n
            }
            line.color = c(p, k)
            line.alpha = a255(0.16f * a)
            canvas.drawLines(neonPts, 0, count, line)
        }
        // The chase: in every group of four, one tube at full brightness and the two below it
        // dying away, climbing a group a cycle.
        for (i in 0 until rows) {
            val behind = ((phase - i / NEON_GROUP) % 1f + 1f) % 1f
            var g = 1f - behind / 0.6f
            if (g <= 0.02f) continue
            // The odd tube with a tired starter, stuttering as it lights. Whole turns, like the rest.
            val s = seed(i * 7 + 3)
            if (s > 0.88f && sin(phase * TAU * 23f + s * 40f) > 0.55f) g *= 0.3f
            val y = r.bottom - pitch * (i + 0.6f)
            val color = c(p, i)
            // Light thrown on the wall, a halo round the glass, and a core gone nearly white with heat.
            glow(canvas, cx, y, w * 0.95f, pitch * 1.5f, color, 0.4f * g * a)
            val count = chevron(neonPts, 0, cx, y, hw, hh)
            line.color = color
            line.alpha = a255(0.3f * g * a)
            line.strokeWidth = 5f * density
            canvas.drawLines(neonPts, 0, count, line)
            line.color = ColorUtils.blendARGB(color, Color.WHITE, 0.6f * g)
            line.alpha = a255((0.3f + 0.7f * g) * a)
            line.strokeWidth = 1.5f * density
            canvas.drawLines(neonPts, 0, count, line)
        }
    }

    /** One arrow's two strokes, written into [pts] from [at]. Returns where the next one starts. */
    private fun chevron(pts: FloatArray, at: Int, cx: Float, cy: Float, hw: Float, hh: Float): Int {
        val tip = cy - hh / 2f
        val foot = cy + hh / 2f
        pts[at] = cx - hw
        pts[at + 1] = foot
        pts[at + 2] = cx
        pts[at + 3] = tip
        pts[at + 4] = cx
        pts[at + 5] = tip
        pts[at + 6] = cx + hw
        pts[at + 7] = foot
        return at + 8
    }

    // ---- Ocean ----------------------------------------------------------------------------------

    private val swellShaders = arrayOfNulls<LinearGradient>(3)
    private val crest = Path()

    private fun ocean(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val w = r.width()
        val h = (r.bottom - fillTop).coerceAtLeast(1f)
        ground(
            canvas, "oceanDeep", intArrayOf(0xFF0B4775.toInt(), 0xFF05284F.toInt(), 0xFF020D22.toInt()),
            floatArrayOf(0f, 0.3f, 1f), r, fillTop, r.bottom, alpha,
        )
        // Daylight coming down through the surface.
        glow(canvas, r.centerX(), fillTop, w * 1.4f, min(h, 60f * density), c(p, 0), 0.22f * a)
        val amp = 2.6f * density
        val step = 3f * density
        val depth = 38f * density
        // Back to front: each swell lower, nearer and brighter than the one behind it. Translucent,
        // and fading to nothing below its crest, so the ones behind show through the ones in front.
        for (layer in 2 downTo 0) {
            val top = fillTop + (4f + (2 - layer) * 9.5f) * density
            val color = c(p, layer + 1)
            path.reset()
            crest.reset()
            path.moveTo(r.left, r.bottom)
            var x = r.left
            var first = true
            while (true) {
                val y = top + amp * swell(layer, ((x - r.left) / w).coerceIn(0f, 1f), phase)
                if (first) {
                    crest.moveTo(x, y)
                    first = false
                } else {
                    crest.lineTo(x, y)
                }
                path.lineTo(x, y)
                if (x >= r.right) break
                x = min(x + step, r.right)
            }
            path.lineTo(r.right, r.bottom)
            path.close()
            val shader = swellShaders[layer] ?: LinearGradient(
                0f, 0f, 0f, 1f,
                intArrayOf(color, fade(color, 0.25f), color and 0xFFFFFF),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            ).also { swellShaders[layer] = it }
            matrix.setScale(1f, depth)
            matrix.postTranslate(0f, top - amp)
            shader.setLocalMatrix(matrix)
            paint.shader = shader
            paint.alpha = alpha
            canvas.drawPath(path, paint)
            paint.shader = null
            // Foam along the crest, brightest on the nearest swell.
            val foam = (0.6f - 0.18f * layer) * a
            line.color = c(p, 0)
            line.alpha = a255(0.25f * foam)
            line.strokeWidth = 3f * density
            canvas.drawPath(crest, line)
            line.alpha = a255(foam)
            line.strokeWidth = 1f * density
            canvas.drawPath(crest, line)
        }
        // The sun glittering on the nearest crest.
        val front = fillTop + 23f * density
        for (i in 0 until 4) {
            val s = seed(i * 29 + 17)
            val twinkle = sin(phase * TAU * (2 + i % 2) + s * TAU)
            if (twinkle <= 0.5f) continue
            val at = 0.1f + 0.8f * s
            glow(
                canvas, r.left + w * at, front + amp * swell(0, at, phase), 4f * density, 2.5f * density,
                Color.WHITE, (twinkle - 0.5f) * 1.6f * a,
            )
        }
        motes(canvas, r, front + 6f * density, phase, 8, 91, c(p, 0), 0.3f * a, 0.55f)
    }

    /**
     * A swell's surface at [at] across the track, in units of its amplitude. Two waves, as with the
     * tides, each travelling a whole number of wavelengths a cycle; neighbouring swells run opposite
     * ways, which is what keeps three of them from reading as one surface drawn three times.
     */
    private fun swell(layer: Int, at: Float, phase: Float): Float {
        val t = phase * TAU * (if (layer % 2 == 0) 1f else -1f)
        return (
            sin(at * TAU * (0.8f + 0.3f * layer) + t * (1 + layer % 2) + layer * 1.7f) +
                0.4f * sin(at * TAU * 1.9f - t * 2f + layer)
            ) / 1.4f
    }

    // ---- Gradient -------------------------------------------------------------------------------

    private var flowShader: LinearGradient? = null

    private fun gradient(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val w = r.width()
        val h = (r.bottom - fillTop).coerceAtLeast(1f)
        val track = r.height().coerceAtLeast(1f)
        // The palette as one gradient, mirrored so a period is two spans and has no seam, sliding
        // one period a cycle on a steep diagonal. Sized by the track, so the colours stay where they
        // are as the level moves over them.
        val flow = flowShader ?: LinearGradient(
            0f, 0f, 1f, 0f,
            IntArray(max(2, p.size)) { c(p, it) or (0xFF shl 24) },
            null,
            Shader.TileMode.MIRROR,
        ).also { flowShader = it }
        val span = track * 0.55f
        matrix.setScale(span, 1f)
        matrix.postTranslate(phase * span * 2f, 0f)
        matrix.postRotate(-72f)
        matrix.postTranslate(r.left, r.bottom)
        flow.setLocalMatrix(matrix)
        paint.shader = flow
        paint.alpha = alpha
        canvas.drawRect(r.left, fillTop, r.right, r.bottom, paint)
        paint.shader = null
        // A soft light wandering over it, so the colours read as lit rather than printed.
        val turn = phase * TAU
        glow(
            canvas, r.centerX() + w * 0.35f * sin(turn), fillTop + h * (0.5f + 0.32f * sin(turn * 2f + 1.2f)),
            w * 1.1f, min(h * 0.45f, 90f * density).coerceAtLeast(w * 0.6f), Color.WHITE, 0.2f * a,
        )
        ground(canvas, "gradientLip", intArrayOf(0x59FFFFFF, 0x00FFFFFF), null, r, fillTop, fillTop + 12f * density, alpha)
        sideSheen(canvas, r, fillTop, alpha)
    }

    // ---- Confetti -------------------------------------------------------------------------------

    /** Square-ended: a piece of paper with round ends is a grain of rice. */
    private val confettiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private fun confetti(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val w = r.width()
        val h = (r.bottom - fillTop).coerceAtLeast(1f)
        val track = r.height().coerceAtLeast(1f)
        ground(canvas, "confettiNight", intArrayOf(0xFF26174F.toInt(), 0xFF0D0824.toInt()), null, r, fillTop, r.bottom, alpha)
        // A spotlight on the level, for the paper to catch.
        glow(canvas, r.centerX(), fillTop, w * 1.3f, min(h, 70f * density), c(p, 0), 0.16f * a)
        val margin = 8f * density
        val span = track + margin * 2f
        val turn = phase * TAU
        val length = 3.6f * density
        confettiPaint.strokeWidth = 2f * density
        for (i in 0 until CONFETTI_COUNT) {
            val s = seed(i * 59 + 3)
            val s2 = seed(i * 97 + 31)
            val s3 = seed(i * 131 + 7)
            val speed = 1 + (s * 2f).toInt()
            // Born above the track and gone below it, so nothing appears in view.
            val y = r.top - margin + ((s2 + phase * speed) % 1f) * span
            if (y < fillTop - margin) continue
            val x = r.left + w * (0.1f + 0.8f * s3) + sin(turn * speed * 2f + s * TAU) * w * 0.12f
            // Spinning flat and flipping over, both whole turns a cycle. The flip is what makes it
            // paper: edge-on it is a sliver, face-on it is at its longest and catches the light.
            val spin = s * TAU + turn * (1 + (s2 * 3f).toInt()) * (if (s3 > 0.5f) 1f else -1f)
            val flip = abs(cos(turn * (2 + (s3 * 3f).toInt()) + s2 * TAU))
            val half = length * (0.15f + 0.85f * flip) / 2f
            val dx = cos(spin) * half
            val dy = sin(spin) * half
            val color = c(p, i)
            val shine = flip * flip * flip * flip
            if (shine > 0.85f) glow(canvas, x, y, 5f * density, 5f * density, color, 0.35f * (shine - 0.85f) / 0.15f * a)
            confettiPaint.color = ColorUtils.blendARGB(color, Color.WHITE, 0.55f * shine)
            confettiPaint.alpha = a255((0.75f + 0.25f * shine) * a)
            canvas.drawLine(x - dx, y - dy, x + dx, y + dy, confettiPaint)
        }
    }

    // ---- Warp: hyperspace -----------------------------------------------------------------------

    private fun warp(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val w = r.width()
        val h = (r.bottom - fillTop).coerceAtLeast(1f)
        ground(canvas, "warpVoid", intArrayOf(0xFF080B24.toInt(), 0xFF020309.toInt()), null, r, fillTop, r.bottom, alpha)
        val cx = r.centerX()
        val vy = fillTop + h * 0.38f
        val reach = max(h, w) * 1.3f + 16f * density
        // Where everything is coming from: a haze, and a brighter heart to it.
        glow(canvas, cx, vy, w * 1.1f, min(h * 0.5f, 80f * density).coerceAtLeast(w * 0.6f), c(p, 2), 0.5f * a)
        glow(canvas, cx, vy, w * 0.35f, w * 0.35f, c(p, 1), 0.7f * a)
        for (i in 0 until WARP_COUNT) {
            val s = seed(i * 67 + 11)
            val lf = life(phase, s, seed(i * 23 + 29))
            // Faded in from the point and out at the far end, so a star finishing its run and the
            // next one starting are both invisible, whenever in the cycle that falls.
            val f = min(1f, lf * 5f) * min(1f, (1f - lf) * 6f)
            if (f <= 0.01f) continue
            val angle = seed(i * 151 + 5) * TAU
            // Squeezed across: in a panel this narrow, the stars heading sideways would be gone in
            // a frame, and the ones heading up and down are the ones there is room to watch.
            val ex = cos(angle) * 0.55f
            val ey = sin(angle)
            // Squared, so a star creeps out of the point and rushes past the edge: approach.
            val d = lf * lf * reach
            val tail = d * (1f - 0.45f * lf)
            val y1 = vy + ey * tail
            val y2 = vy + ey * d
            if (max(y1, y2) < fillTop) continue
            line.color = c(p, i % 2)
            line.alpha = a255(f * (0.35f + 0.65f * lf) * a)
            line.strokeWidth = (0.6f + 1.4f * lf) * density
            canvas.drawLine(cx + ex * tail, y1, cx + ex * d, y2, line)
        }
    }

    // ---- Storm ----------------------------------------------------------------------------------

    private val rainPts = FloatArray(RAIN_COUNT * 4)
    private val boltPath = Path()
    private val boltRect = RectF()
    private var boltTop = Float.NaN

    private fun storm(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val w = r.width()
        val h = (r.bottom - fillTop).coerceAtLeast(1f)
        val track = r.height().coerceAtLeast(1f)
        ground(
            canvas, "stormSky", intArrayOf(0xFF1E2538.toInt(), 0xFF0C0F1A.toInt(), 0xFF05060B.toInt()),
            floatArrayOf(0f, 0.45f, 1f), r, fillTop, r.bottom, alpha,
        )
        // A strike and its restrike, a little after a third of the way in, and a far flash with no
        // bolt later on. None of them is near the wrap, so the cycle ends as dark as it began.
        val bolt = max(spike(phase, 0.3f, 0.004f, 0.035f), 0.75f * spike(phase, 0.345f, 0.004f, 0.05f))
        val sheet = max(bolt, 0.4f * spike(phase, 0.72f, 0.02f, 0.06f))
        val turn = phase * TAU
        // The clouds along the level: dark, and lit from inside when the sky flashes.
        for (i in 0 until 3) {
            val s = seed(i * 71 + 13)
            val x = r.left + w * (0.2f + 0.3f * i) + sin(turn + s * TAU) * w * 0.12f
            val y = fillTop + (8f + 6f * s) * density
            glow(canvas, x, y, w * 0.75f, 20f * density, 0xFF394461.toInt(), 0.85f * a)
            glow(canvas, x, y + 6f * density, w * 0.9f, 30f * density, c(p, 1), 0.6f * sheet * a)
        }
        if (sheet > 0.01f) {
            paint.color = c(p, 0)
            paint.alpha = a255(0.14f * sheet * a)
            canvas.drawRect(r.left, fillTop, r.right, r.bottom, paint)
        }
        if (bolt > 0.02f) {
            buildBolt(r, fillTop, h)
            line.color = c(p, 1)
            line.alpha = a255(0.35f * bolt * a)
            line.strokeWidth = 5f * density
            canvas.drawPath(boltPath, line)
            line.color = c(p, 0)
            line.alpha = a255(bolt * a)
            line.strokeWidth = 1.4f * density
            canvas.drawPath(boltPath, line)
        }
        // Rain, in one batch: a streak is not worth a draw of its own. Several whole passes a
        // cycle, each drop born above the track and gone below it.
        val length = 7f * density
        val slant = length * 0.28f
        val span = track + length * 2f
        var n = 0
        for (i in 0 until RAIN_COUNT) {
            val s = seed(i * 43 + 1)
            val y = r.top - length + ((seed(i * 79 + 17) + phase * (5 + (s * 3f).toInt())) % 1f) * span
            if (y < fillTop) continue
            val x = r.left - slant + (w + slant * 2f) * seed(i * 13 + 5)
            rainPts[n] = x + slant
            rainPts[n + 1] = y - length
            rainPts[n + 2] = x
            rainPts[n + 3] = y
            n += 4
        }
        if (n > 0) {
            line.color = fade(c(p, 2), (0.55f + 0.45f * sheet) * a)
            line.strokeWidth = 0.8f * density
            canvas.drawLines(rainPts, 0, n, line)
        }
    }

    /** 0..1: rising to full at [at] over [rise] of a cycle, then dying over [fall]. */
    private fun spike(phase: Float, at: Float, rise: Float, fall: Float): Float = when {
        phase < at - rise || phase > at + fall -> 0f
        phase < at -> 1f - (at - phase) / rise
        else -> 1f - (phase - at) / fall
    }

    /**
     * The bolt, jagged down from the clouds with a fork off one side. The same bolt every time,
     * from seeds, and built again only when the fill it hangs from moves.
     */
    private fun buildBolt(r: RectF, fillTop: Float, h: Float) {
        if (boltTop == fillTop && boltRect == r) return
        boltRect.set(r)
        boltTop = fillTop
        boltPath.reset()
        val w = r.width()
        val depth = min(h * 0.85f, 160f * density)
        val top = fillTop + 2f * density
        var x = r.left + w * 0.55f
        boltPath.moveTo(x, top)
        var forkX = x
        var forkY = top
        for (k in 1..12) {
            x = (x + (seed(k * 31 + 7) - 0.5f) * w * 0.45f).coerceIn(r.left + w * 0.15f, r.right - w * 0.15f)
            val y = top + depth * k / 12f
            boltPath.lineTo(x, y)
            if (k == 4) {
                forkX = x
                forkY = y
            }
        }
        boltPath.moveTo(forkX, forkY)
        for (k in 1..4) {
            forkX = (forkX - w * 0.07f + (seed(k * 53 + 3) - 0.5f) * w * 0.12f).coerceIn(r.left, r.right)
            boltPath.lineTo(forkX, forkY + depth * 0.06f * k)
        }
    }

    // ---- Fireworks ------------------------------------------------------------------------------

    private fun fireworks(canvas: Canvas, p: LongArray, r: RectF, fillTop: Float, phase: Float, alpha: Int, a: Float) {
        val w = r.width()
        val h = (r.bottom - fillTop).coerceAtLeast(1f)
        ground(canvas, "fireworkSky", intArrayOf(0xFF0D1030.toInt(), 0xFF040512.toInt()), null, r, fillTop, r.bottom, alpha)
        val blast = w * 0.5f + 10f * density
        line.strokeWidth = 1.2f * density
        for (b in 0 until FIREWORK_BURSTS) {
            // Every burst on the same clock, each a third of a cycle behind the last, and each over
            // well before its own third comes round again: nothing is lit when a burst's clock wraps.
            val u = ((phase - b.toFloat() / FIREWORK_BURSTS) % 1f + 1f) % 1f
            if (u >= FIREWORK_LIFE) continue
            val cx = r.left + w * (0.3f + 0.4f * seed(b * 11 + 1))
            val cy = fillTop + h * (0.2f + 0.4f * seed(b * 17 + 3))
            val color = c(p, b)
            val accent = c(p, b + 1)
            if (u < FIREWORK_CLIMB) {
                // The rocket, from under the track, slowing as it reaches the top of its climb.
                val k = u / FIREWORK_CLIMB
                val start = r.bottom + 8f * density
                val y = start + (cy - start) * (1f - (1f - k) * (1f - k))
                line.color = color
                line.alpha = a255(0.45f * a)
                canvas.drawLine(cx, y, cx, y + 10f * density * (1f - k * 0.5f), line)
                glow(canvas, cx, y, 4f * density, 4f * density, color, 0.9f * a)
                continue
            }
            val e = (u - FIREWORK_CLIMB) / (FIREWORK_LIFE - FIREWORK_CLIMB)
            val left = 1f - e
            val radius = blast * (1f - left * left * left)
            val dying = left * sqrt(left)
            // Sparks sag as they slow.
            val drop = e * e * 12f * density
            // The flash of the break, then the sparks' light on the smoke round them.
            glow(canvas, cx, cy, blast * 1.4f, blast * 1.4f, color, (0.55f * left * left + 0.15f * dying) * a)
            for (j in 0 until FIREWORK_SPARKS) {
                val angle = j * TAU / FIREWORK_SPARKS + seed(b * 7 + j * 3) * 0.35f
                val ca = cos(angle)
                val sa = sin(angle)
                val ox = cx + ca * radius
                val oy = cy + sa * radius + drop
                val ink = if (j % 3 == 0) accent else color
                line.color = ink
                line.alpha = a255(0.55f * dying * a)
                canvas.drawLine(cx + ca * radius * 0.72f, cy + sa * radius * 0.72f + drop * 0.7f, ox, oy, line)
                // The tips, white-hot at the break, glittering as they go out.
                val glitter = if (e > 0.5f) 0.55f + 0.45f * sin(e * 60f + j * 2.3f) else 1f
                paint.color = ColorUtils.blendARGB(ink, Color.WHITE, 0.6f * left)
                paint.alpha = a255(dying * glitter * a)
                canvas.drawCircle(ox, oy, 1.1f * density, paint)
            }
        }
    }

    // ---- Stripes, the one style that tints the fill rather than painting over it ----------------

    private var stripeShader: LinearGradient? = null
    private var stripeInk = 0
    private var glossShader: LinearGradient? = null

    /**
     * Soft diagonal bands in [ink] — the track's colour, the one that is sure to show on the fill —
     * drifting one band a cycle, under a gloss along the level.
     */
    fun drawStripes(canvas: Canvas, r: RectF, fillTop: Float, phase: Float, ink: Int, alpha: Int) {
        val period = 12f * density
        val shader = stripeShader?.takeIf { stripeInk == ink } ?: LinearGradient(
            0f, 0f, 1f, 0f,
            intArrayOf(ink and 0xFFFFFF, fade(ink, 0.2f), fade(ink, 0.2f), ink and 0xFFFFFF, ink and 0xFFFFFF),
            floatArrayOf(0f, 0.18f, 0.45f, 0.63f, 1f),
            Shader.TileMode.REPEAT,
        ).also {
            stripeShader = it
            stripeInk = ink
        }
        matrix.setScale(period, 1f)
        matrix.postTranslate(phase * period, 0f)
        matrix.postRotate(-45f)
        matrix.postTranslate(r.left, fillTop)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        paint.alpha = alpha
        canvas.drawRect(r.left, fillTop, r.right, r.bottom, paint)
        val gloss = glossShader ?: LinearGradient(
            0f, 0f, 0f, 1f, intArrayOf(0x40FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP,
        ).also { glossShader = it }
        matrix.setScale(1f, 12f * density)
        matrix.postTranslate(0f, fillTop)
        gloss.setLocalMatrix(matrix)
        paint.shader = gloss
        canvas.drawRect(r.left, fillTop, r.right, fillTop + 12f * density, paint)
        paint.shader = null
    }

    companion object {
        private const val TAU = 6.2831855f

        /** Where each aurora colour hangs, as a fraction down the fill: green low, violet high. */
        private val AURORA_HEIGHTS = floatArrayOf(0.72f, 0.5f, 0.26f, 0.88f)

        /** How many beams the sunrise fans out. */
        private const val RAYS = 10

        /** The plasma field's samples: few, because the filter does the rest. */
        private const val PLASMA_COLS = 12
        private const val PLASMA_ROWS = 48

        private const val SATIN_MAX_ROWS = 260

        /** Four quads a row between five vertices a row, two triangles a quad. */
        private val SATIN_INDICES: ShortArray = ShortArray((SATIN_MAX_ROWS - 1) * 24).also { idx ->
            var n = 0
            for (i in 0 until SATIN_MAX_ROWS - 1) {
                for (j in 0 until 4) {
                    val tl = i * 5 + j
                    val tr = tl + 1
                    val bl = tl + 5
                    val br = bl + 1
                    idx[n++] = tl.toShort()
                    idx[n++] = tr.toShort()
                    idx[n++] = bl.toShort()
                    idx[n++] = tr.toShort()
                    idx[n++] = br.toShort()
                    idx[n++] = bl.toShort()
                }
            }
        }

        /** How many fireflies are out. A dozen and a bit: enough for a meadow, few enough to count. */
        private const val FIREFLY_COUNT = 14

        /** How many flakes fall at each depth, far to near. The near ones are few, as they are. */
        private val SNOW_PER_LAYER = intArrayOf(16, 11, 6)

        /** The length of track one heartbeat's trace takes, in dp. */
        private const val ECG_BEAT_DP = 64f

        /** How far behind the monitor's sweep its trace takes to fade, in dp. */
        private const val ECG_TRAIL_DP = 72f

        /** Where the tall spike sits in a beat, as a fraction of it. */
        private const val ECG_SPIKE = 0.36f

        /**
         * One beat of the trace as (fraction up the beat, swing across) pairs: the small wave before,
         * the dip, the spike and its overshoot, the broad wave after, and flat between. Swing is in
         * units of the trace's amplitude.
         */
        private val ECG_KEYS = floatArrayOf(
            0f, 0f, 0.12f, 0f, 0.15f, -0.14f, 0.18f, 0f,
            0.3f, 0f, 0.325f, 0.16f, ECG_SPIKE, -1f, 0.4f, 0.42f, 0.43f, 0f,
            0.56f, 0f, 0.6f, -0.2f, 0.64f, -0.28f, 0.68f, -0.2f, 0.72f, 0f, 1f, 0f,
        )

        /** The spacing of the neon arrows, in dp, and how many there can ever be. */
        private const val NEON_PITCH_DP = 13f
        private const val NEON_MAX = 64

        /** The chase lights one arrow in this many. */
        private const val NEON_GROUP = 4f

        private const val CONFETTI_COUNT = 26
        private const val WARP_COUNT = 34
        private const val RAIN_COUNT = 44

        /** Bursts a cycle, and how much of their third of it each is climbing and then open. */
        private const val FIREWORK_BURSTS = 3
        private const val FIREWORK_CLIMB = 0.14f
        private const val FIREWORK_LIFE = 0.62f
        private const val FIREWORK_SPARKS = 14

        private val STYLES = setOf(
            SliderFill.LIQUID, SliderFill.VU_METER, SliderFill.WAVEFORM, SliderFill.SUNRISE,
            SliderFill.SPECTRUM, SliderFill.SILK, SliderFill.AURORA, SliderFill.PLASMA,
            SliderFill.HOLOGRAM, SliderFill.SONAR, SliderFill.CIRCUIT, SliderFill.DOT_MATRIX,
            SliderFill.CYBERPUNK, SliderFill.MATRIX_RAIN, SliderFill.RUNE,
            SliderFill.FIREFLIES, SliderFill.SNOWFALL, SliderFill.HEARTBEAT, SliderFill.NEON,
            SliderFill.OCEAN, SliderFill.GRADIENT, SliderFill.CONFETTI, SliderFill.WARP,
            SliderFill.STORM, SliderFill.FIREWORKS,
        )

        /** Whether [style] is painted here, ground and all, rather than by the view itself. */
        fun handles(style: String): Boolean = style in STYLES
    }
}
