package com.newagedevs.gesturevolume.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

/**
 * The track the handler expands into: a vertical bar that fills from the bottom.
 *
 * Drawn rather than composed. It is created and destroyed inside a single touch gesture, in a
 * Service-owned window, and it has to redraw every frame the finger moves — a Compose window with
 * its own recomposer and lifecycle owners would be several milliseconds of setup at the exact
 * moment the user is expecting the bar to snap open. The Deck can afford that because it opens
 * once and stays; this cannot.
 *
 * **Why everything is drawn twice.** The value label and the icon sit inside the track, which is
 * one colour below the fill line and another above it. Picking a single colour for them means
 * choosing which half of the range they disappear in. So each is drawn once in the colour that
 * contrasts the track, then again clipped to the filled region in the colour that contrasts the
 * fill. The result inverts exactly at the fill line and is legible at every value, which is the
 * only property that matters for something the user reads while their finger is moving.
 */
class QuickSliderView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val trackRect = RectF()
    private val trackPath = Path()

    private var cornerPx = 22f * density
    private var trackColor = Color.BLACK
    private var fillColor = Color.WHITE

    private var icon: Drawable? = null
    private var showValue = true

    /** 0..1. The only thing that changes while the finger moves. */
    private var value = 0f

    fun setColors(track: Int, fill: Int) {
        trackColor = track
        fillColor = fill
        trackPaint.color = track
        fillPaint.color = fill
        invalidate()
    }

    fun setCornerRadiusDp(dp: Float) {
        cornerPx = dp * density
        rebuildPath()
        invalidate()
    }

    fun setIcon(@DrawableRes res: Int?) {
        icon = res?.let { ContextCompat.getDrawable(context, it)?.mutate() }
        invalidate()
    }

    fun setShowValue(show: Boolean) {
        showValue = show
        invalidate()
    }

    fun setValue(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        if (clamped == value) return
        value = clamped
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        trackRect.set(0f, 0f, w.toFloat(), h.toFloat())
        // Never more than half the shorter side, or the corners overlap and the round-rect
        // degenerates into a shape the caller did not ask for.
        textPaint.textSize = (w * 0.34f).coerceIn(10f * density, 20f * density)
        rebuildPath()
    }

    private fun rebuildPath() {
        val limit = minOf(trackRect.width(), trackRect.height()) / 2f
        val r = cornerPx.coerceAtMost(limit).coerceAtLeast(0f)
        trackPath.reset()
        if (trackRect.isEmpty) return
        trackPath.addRoundRect(trackRect, r, r, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        if (trackRect.isEmpty) return

        // Everything is inside the pill. Clipping once here is what lets the fill be a plain
        // rectangle and still come out with rounded ends.
        canvas.save()
        canvas.clipPath(trackPath)

        canvas.drawRect(trackRect, trackPaint)

        val fillTop = trackRect.bottom - trackRect.height() * value

        drawContent(canvas, overFill = false)

        // Pass two: the same content, clipped to the filled region, in the inverted colour.
        canvas.save()
        canvas.clipRect(trackRect.left, fillTop, trackRect.right, trackRect.bottom)
        canvas.drawRect(trackRect.left, fillTop, trackRect.right, trackRect.bottom, fillPaint)
        drawContent(canvas, overFill = true)
        canvas.restore()

        canvas.restore()
    }

    private fun drawContent(canvas: Canvas, overFill: Boolean) {
        val ink = if (overFill) trackColor else fillColor

        if (showValue) {
            textPaint.color = ink
            val label = "${(value * 100f).toInt()}"
            // Baseline placed by the font's own metrics rather than a guessed offset, so the
            // number sits the same distance from the top on every device font scale.
            val y = trackRect.top + 14f * density - textPaint.fontMetrics.ascent
            canvas.drawText(label, trackRect.centerX(), y, textPaint)
        }

        icon?.let { drawable ->
            val size = (trackRect.width() * 0.46f).coerceIn(12f * density, 26f * density)
            val cx = trackRect.centerX()
            val cy = trackRect.bottom - 16f * density - size / 2f
            val wrapped = DrawableCompat.wrap(drawable)
            DrawableCompat.setTint(wrapped, ink)
            wrapped.setBounds(
                (cx - size / 2f).toInt(),
                (cy - size / 2f).toInt(),
                (cx + size / 2f).toInt(),
                (cy + size / 2f).toInt()
            )
            wrapped.draw(canvas)
        }
    }
}
