package com.newagedevs.gesturevolume.ui.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import com.newagedevs.gesturevolume.utils.GlimmerFill
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Draws the Glimmer fill: the whole track as a field of fine dots, lit from nothing at the start
 * to a lavender glimmer at the level, a white handle on the level and six faint stops down the
 * track. See [GlimmerFill].
 *
 * Over the whole track rather than cut to the fill, like the Pixels grid: the dots above the level
 * and the stops are the track, and the handle is drawn across the level rather than under it.
 *
 * The haze between the lit dots is PixelArt's glow: the grid again, one texel to a dot, stretched
 * over the track with filtering and drawn underneath, so each lit dot bleeds softly into the gaps
 * around it the way the slider's do. Nothing is made per frame: the image and its array are remade
 * only when the grid changes size.
 */
internal class GlimmerArt(private val density: Float) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haze = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val source = Rect()
    private val grid = RectF()
    private val matrix = Matrix()

    private var hazeImage: Bitmap? = null
    private var texels = IntArray(0)

    /** Each dot's colour for this frame, alpha included, in the image's order: top row first. */
    private var colours = IntArray(0)

    /** The soft light round a held handle, at unit radius, moved into place by [matrix]. */
    private var halo: RadialGradient? = null
    private var haloColour = 0

    /**
     * The handle as last drawn, so the caller can write the number and the icon through it: dark on
     * the handle and light everywhere else. Empty when there is no handle.
     */
    val handle = RectF()

    /** [handle] with its corners, rebuilt only when it moves. */
    val handlePath = Path()
    private val builtHandle = RectF()
    private var builtRound = -1f

    /**
     * Paints the field over [r], lit up to [value].
     *
     * @param time the fill's own clock, in seconds: it runs at the style's speed.
     * @param p the three colours, the fill's own or the user's: the twinkle's peak, the lit dots at
     *   the level, and the lit dots where the light begins.
     * @param light the panel's fill colour, the one colour sure to show against its track: the
     *   handle is painted in it, and the unlit dots and the stops are faint shades of it.
     * @param corner the panel's own corner radius, which the handle's corners follow, as the
     *   slider's handle has its track's corners.
     * @param grabbed whether a finger is on the track: the handle glows while it is.
     * @param keepOut the stretches the stops keep clear of, as (top, bottom) pairs: the number and
     *   the icon, which would otherwise sit on them.
     * @param alpha how much of the whole shows, 0..1: the panel fading its contents in and out.
     */
    fun draw(
        canvas: Canvas,
        r: RectF,
        value: Float,
        time: Float,
        style: GlimmerFill.Style,
        p: IntArray,
        light: Int,
        corner: Float,
        grabbed: Boolean,
        keepOut: FloatArray,
        alpha: Float,
    ) {
        handle.setEmpty()
        val w = r.width()
        val h = r.height()
        if (p.size < 3 || w <= 1f || h <= 1f || alpha <= 0.004f) return
        val columns = GlimmerFill.columnsFor(w, density)
        val pitch = w / columns
        // As many rows as square spacings fit, the leftover split between the two ends.
        val rows = max(1, floor(h / pitch).toInt())
        val bottom = r.bottom - (h - rows * pitch) / 2f
        size(columns, rows)

        val handleLength = GlimmerFill.handleLength(w, h, density)
        if (style.handle) {
            val centre = r.bottom - GlimmerFill.handleCentre(value, h, handleLength)
            handle.set(r.left, centre - handleLength / 2f, r.right, centre + handleLength / 2f)
        }
        // Where the light ends: the handle's centre, or the level itself with no handle.
        val level = if (style.handle) handle.centerY() else r.bottom - h * value.coerceIn(0f, 1f)
        val lit = (r.bottom - level).coerceAtLeast(1f)
        val energy = GlimmerFill.energy(value)
        val dot = pitch * GlimmerFill.DOT

        // Each dot's colour worked out once, then drawn twice: as haze, and as itself.
        for (row in 0 until rows) {
            val cy = bottom - (row + 0.5f) * pitch
            val first = (rows - 1 - row) * columns
            // Under the handle nothing shows, so nothing is drawn.
            if (style.handle && cy > handle.top + dot && cy < handle.bottom - dot) {
                colours.fill(0, first, first + columns)
                texels.fill(0, first, first + columns)
                continue
            }
            // How far below the level the row is, in spacings: a row the level is passing lights by
            // that much, so the dots follow a finger smoothly rather than a row at a time.
            val on = ((cy - level) / pitch + 0.5f).coerceIn(0f, 1f)
            if (on <= 0f) {
                // Resting, and too faint to haze.
                colours.fill(withAlpha(light, GlimmerFill.GHOST * alpha), first, first + columns)
                texels.fill(0, first, first + columns)
                continue
            }
            val t = ((r.bottom - cy) / lit).coerceIn(0f, 1f)
            val ramp = GlimmerFill.ramp(t)
            // Grey where the light begins and lavender at the level: the colour arrives with the
            // brightness rather than ahead of it, as on the slider.
            val tinted = ColorUtils.blendARGB(light, ColorUtils.blendARGB(p[2], p[1], t), sqrt(ramp))
            val shimmer = GlimmerFill.shimmer(t, time)
            for (column in 0 until columns) {
                val twinkle = GlimmerFill.twinkle(column, row, time)
                val lifted = ColorUtils.blendARGB(tinted, p[0], min(1f, twinkle * 0.9f))
                val bright = min(
                    1f,
                    GlimmerFill.GHOST + ramp * (GlimmerFill.LIT - GlimmerFill.GHOST) +
                        ramp * energy * (GlimmerFill.TWINKLE * twinkle + GlimmerFill.SHIMMER * shimmer),
                )
                val colour = if (on >= 1f) lifted else ColorUtils.blendARGB(light, lifted, on)
                val a = GlimmerFill.GHOST + (bright - GlimmerFill.GHOST) * on
                colours[first + column] = withAlpha(colour, a * alpha)
                texels[first + column] = withAlpha(colour, a * on * GlimmerFill.HAZE)
            }
        }

        // Under the dots, so it lies in the gaps round them rather than over them.
        hazeImage?.let { image ->
            image.setPixels(texels, 0, columns, 0, 0, columns, rows)
            source.set(0, 0, columns, rows)
            grid.set(r.left, bottom - rows * pitch, r.right, bottom)
            haze.alpha = a255(alpha)
            canvas.drawBitmap(image, source, grid, haze)
        }
        for (row in 0 until rows) {
            val cy = bottom - (row + 0.5f) * pitch
            val first = (rows - 1 - row) * columns
            for (column in 0 until columns) {
                val colour = colours[first + column]
                if (colour ushr 24 == 0) continue
                paint.color = colour
                canvas.drawCircle(r.left + (column + 0.5f) * pitch, cy, dot, paint)
            }
        }

        if (style.stops) stops(canvas, r, h, handleLength, pitch, style.handle, light, keepOut, alpha)

        if (style.handle) {
            val round = min(corner, min(handleLength, w) / 2f).coerceAtLeast(0f)
            if (grabbed) glow(canvas, p[1], w, handleLength, alpha)
            paint.color = light
            paint.alpha = a255(Color.alpha(light) / 255f * alpha)
            canvas.drawRoundRect(handle, round, round, paint)
            if (handle != builtHandle || round != builtRound) {
                handlePath.reset()
                handlePath.addRoundRect(handle, round, round, Path.Direction.CW)
                builtHandle.set(handle)
                builtRound = round
            }
        }
    }

    /** The six stops down the middle, a shade brighter than the dots, clear of the number and the icon. */
    private fun stops(
        canvas: Canvas,
        r: RectF,
        h: Float,
        handleLength: Float,
        pitch: Float,
        withHandle: Boolean,
        light: Int,
        keepOut: FloatArray,
        alpha: Float,
    ) {
        val radius = pitch * GlimmerFill.STOP_DOT
        paint.color = withAlpha(light, GlimmerFill.STOP_ALPHA * alpha)
        stop@ for (k in 0 until GlimmerFill.STOPS) {
            val y = r.bottom - GlimmerFill.stopAt(k, h, handleLength)
            // The handle covers it.
            if (withHandle && y > handle.top - radius && y < handle.bottom + radius) continue
            var i = 0
            while (i + 1 < keepOut.size) {
                if (y > keepOut[i] - radius && y < keepOut[i + 1] + radius) continue@stop
                i += 2
            }
            canvas.drawCircle(r.centerX(), y, radius, paint)
        }
    }

    /** A soft light in the lit colour round the handle, while a finger holds it. */
    private fun glow(canvas: Canvas, colour: Int, w: Float, handleLength: Float, alpha: Float) {
        val shader = halo?.takeIf { haloColour == colour } ?: RadialGradient(
            0f, 0f, 1f,
            intArrayOf(colour, ColorUtils.setAlphaComponent(colour, 90), colour and 0xFFFFFF),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        ).also {
            halo = it
            haloColour = colour
        }
        val rx = w * 0.9f
        val ry = handleLength * 1.6f
        matrix.setScale(rx, ry)
        matrix.postTranslate(handle.centerX(), handle.centerY())
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        paint.alpha = a255(0.55f * alpha)
        canvas.drawRect(handle.centerX() - rx, handle.centerY() - ry, handle.centerX() + rx, handle.centerY() + ry, paint)
        paint.shader = null
    }

    private fun size(columns: Int, rows: Int) {
        val count = columns * rows
        if (texels.size != count) {
            texels = IntArray(count)
            colours = IntArray(count)
        }
        val current = hazeImage
        if (current == null || current.width != columns || current.height != rows) {
            // Replaced, never recycled: a frame already recorded can still be drawing the old one.
            hazeImage = Bitmap.createBitmap(columns, rows, Bitmap.Config.ARGB_8888)
        }
    }

    /** Lets the haze image go with the view that drew it. Dropped rather than recycled; see [size]. */
    fun release() {
        hazeImage = null
    }

    private fun withAlpha(colour: Int, alpha: Float): Int =
        ((alpha.coerceIn(0f, 1f) * (colour ushr 24) + 0.5f).toInt() shl 24) or (colour and 0xFFFFFF)

    private fun a255(f: Float): Int = (f * 255f).toInt().coerceIn(0, 255)
}
