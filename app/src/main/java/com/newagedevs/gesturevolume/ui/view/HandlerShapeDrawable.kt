package com.newagedevs.gesturevolume.ui.view

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import com.newagedevs.gesturevolume.utils.HandlerShape

/**
 * The bar itself: one filled outline, optionally stroked.
 *
 * This replaces the [android.graphics.drawable.GradientDrawable] the bar used to wear. That
 * drawable can only ever be a rectangle with corner radii, so a shape like [HandlerShape.TAB] —
 * whose ends sweep back into the screen edge — is not something it can be persuaded to draw. A
 * path can draw both, so both go through here rather than the bar having two rendering paths that
 * have to be kept looking alike.
 *
 * The rounded case is drawn to match what `GradientDrawable` did, deliberately: the stroke sits
 * *inside* the bounds, so a bar with a stroke is the same size as a bar without one. Anything else
 * and turning a stroke on would nudge the bar's edge off the side of the screen.
 */
class HandlerShapeDrawable : Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()

    /** Set whenever anything that changes the outline changes; cleared on the next draw. */
    private var pathDirty = true

    var shape: String = HandlerShape.ROUNDED
        set(value) {
            val next = HandlerShape.sanitize(value)
            if (field == next) return
            field = next
            invalidatePath()
        }

    var flare: Float = HandlerShape.DEFAULT_FLARE
        set(value) {
            val next = HandlerShape.sanitizeFlare(value)
            if (field == next) return
            field = next
            invalidatePath()
        }

    var edgeOnLeft: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidatePath()
        }

    /**
     * Corner radii in pixels, in [Path.addRoundRect] order: top-left x/y, top-right x/y,
     * bottom-right x/y, bottom-left x/y. Ignored by [HandlerShape.TAB], which has no corners.
     */
    private val cornerRadii = FloatArray(8)

    fun setCornerRadiiPx(topLeft: Float, topRight: Float, bottomLeft: Float, bottomRight: Float) {
        val next = floatArrayOf(
            topLeft, topLeft,
            topRight, topRight,
            bottomRight, bottomRight,
            bottomLeft, bottomLeft,
        )
        if (next.contentEquals(cornerRadii)) return
        next.copyInto(cornerRadii)
        invalidatePath()
    }

    fun setFillColor(color: Int) {
        if (fillPaint.color == color) return
        fillPaint.color = color
        invalidateSelf()
    }

    fun setStroke(widthPx: Float, color: Int) {
        val w = widthPx.coerceAtLeast(0f)
        // The stroke is inset by half its own width, so a change in width moves the outline.
        if (strokePaint.strokeWidth != w) invalidatePath()
        strokePaint.strokeWidth = w
        strokePaint.color = color
        invalidateSelf()
    }

    private fun invalidatePath() {
        pathDirty = true
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        pathDirty = true
    }

    override fun draw(canvas: Canvas) {
        if (pathDirty) rebuildPath()
        if (path.isEmpty) return
        canvas.drawPath(path, fillPaint)
        if (strokePaint.strokeWidth > 0f) canvas.drawPath(path, strokePaint)
    }

    private fun rebuildPath() {
        pathDirty = false
        path.reset()

        val b = bounds
        val inset = strokePaint.strokeWidth / 2f
        val left = b.left + inset
        val top = b.top + inset
        val right = b.right - inset
        val bottom = b.bottom - inset
        val width = right - left
        val height = bottom - top
        if (width <= 0f || height <= 0f) return

        if (shape == HandlerShape.TAB) {
            val outline = HandlerShape.tabOutline(width, height, flare, edgeOnLeft)
            path.moveTo(left + outline[0], top + outline[1])
            var i = 2
            while (i < outline.size) {
                path.lineTo(left + outline[i], top + outline[i + 1])
                i += 2
            }
            // Closes along the screen edge, which is the one straight side of a tab.
            path.close()
        } else {
            path.addRoundRect(left, top, right, bottom, cornerRadii, Path.Direction.CW)
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Drawable, still abstract.")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
