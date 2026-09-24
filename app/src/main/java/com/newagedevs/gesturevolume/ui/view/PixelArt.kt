package com.newagedevs.gesturevolume.ui.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.newagedevs.gesturevolume.utils.PixelFill
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the Pixels fill: the track as a grid of lights, lit up to the level, the pattern running
 * through the lit ones and the rest showing faintly. See [PixelFill].
 *
 * The glow is the grid again, one texel to a pixel, stretched over the track with filtering on and
 * drawn underneath — so each light bleeds softly into the gaps around it. One tiny image and one
 * draw, rather than a blur or a gradient per pixel: a blur is a pass over the whole panel on every
 * frame, and a gradient per pixel in its own colour is a new shader per pixel per frame.
 *
 * Nothing is made per frame: the image and the arrays are remade only when the grid changes size.
 */
internal class PixelArt {

    private val cell = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val source = Rect()

    private var glowImage: Bitmap? = null
    private var texels = IntArray(0)

    /** Each pixel's colour and brightness for this frame, worked out once and drawn twice. */
    private var colours = IntArray(0)
    private var brightness = FloatArray(0)

    /**
     * Paints the grid over [r].
     *
     * @param fillTop where the level is, in the view's coordinates: pixels below it are lit.
     * @param fillColor the panel's fill colour, for the patterns drawn in one colour.
     * @param custom the user's own animation colours, or null for the pattern's.
     * @param alpha how much of the whole shows, 0..1: the panel fading its contents in and out.
     */
    fun draw(
        canvas: Canvas,
        r: RectF,
        fillTop: Float,
        phase: Float,
        style: PixelFill.Style,
        fillColor: Int,
        custom: IntArray?,
        alpha: Float,
    ) {
        if (r.width() <= 1f || r.height() <= 1f || alpha <= 0.004f) return
        val columns = style.columns
        val pitchX = r.width() / columns
        // As many rows as square pixels fit, then stretched a hair to fill the length exactly, so
        // the grid meets both ends of the track rather than leaving a sliver at one.
        val rows = max(1, floor(r.height() / pitchX).toInt())
        val pitchY = r.height() / rows
        size(columns, rows)

        val glowing = style.glow > 0.01f
        for (row in 0 until rows) {
            val bottom = r.bottom - row * pitchY
            // How much of this pixel is below the level: a pixel the level passes through lights
            // by that much, so the grid follows a finger smoothly instead of a row at a time.
            val lit = ((bottom - fillTop) / pitchY).coerceIn(0f, 1f)
            for (column in 0 until columns) {
                val level = PixelFill.level(style.pattern, column, row, columns, rows, phase)
                val on = PixelFill.LIT_FLOOR + (1f - PixelFill.LIT_FLOOR) * level
                val index = row * columns + column
                val colour = PixelFill.color(style.pattern, column, row, columns, rows, phase, fillColor, custom)
                colours[index] = colour
                brightness[index] = style.rest + (on - style.rest) * lit
                // Only what is lit throws light; a resting pixel is too faint to.
                if (glowing) texels[(rows - 1 - row) * columns + column] = withAlpha(colour, on * lit * style.glow)
            }
        }

        if (glowing) {
            val image = glowImage ?: return
            image.setPixels(texels, 0, columns, 0, 0, columns, rows)
            source.set(0, 0, columns, rows)
            glow.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawBitmap(image, source, r, glow)
        }

        val insetX = pitchX * style.gap / 2f
        val insetY = pitchY * style.gap / 2f
        val radius = style.roundness * min(pitchX - insetX * 2f, pitchY - insetY * 2f) / 2f
        for (row in 0 until rows) {
            val bottom = r.bottom - row * pitchY
            val top = bottom - pitchY
            for (column in 0 until columns) {
                val index = row * columns + column
                val left = r.left + column * pitchX
                rect.set(left + insetX, top + insetY, left + pitchX - insetX, bottom - insetY)
                cell.color = withAlpha(colours[index], brightness[index] * alpha)
                canvas.drawRoundRect(rect, radius, radius, cell)
            }
        }
    }

    private fun size(columns: Int, rows: Int) {
        val count = columns * rows
        if (colours.size != count) {
            colours = IntArray(count)
            brightness = FloatArray(count)
            texels = IntArray(count)
        }
        val current = glowImage
        if (current == null || current.width != columns || current.height != rows) {
            // Replaced, never recycled: a frame already recorded can still be drawing the old one,
            // and it is a few hundred bytes.
            glowImage = Bitmap.createBitmap(columns, rows, Bitmap.Config.ARGB_8888)
        }
    }

    private fun withAlpha(colour: Int, alpha: Float): Int =
        ((alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 24) or (colour and 0xFFFFFF)

    /** Lets the glow image go with the view that drew it. Dropped rather than recycled; see [size]. */
    fun release() {
        glowImage = null
    }
}
