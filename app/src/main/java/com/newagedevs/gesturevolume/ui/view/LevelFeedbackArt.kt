package com.newagedevs.gesturevolume.ui.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import com.newagedevs.gesturevolume.utils.EffortFill
import com.newagedevs.gesturevolume.utils.LevelFeedback

/**
 * Draws [LevelFeedback] over a Quick panel fill: two passes, one over the lit part and one over the
 * whole panel, each clipped by [QuickSliderView] before it calls.
 *
 * Nothing is made per frame, as in [FillArt]: the gradients are made once at unit size and moved
 * into place by a matrix.
 */
internal class LevelFeedbackArt(private val density: Float) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val matrix = Matrix()
    private val rect = RectF()

    /** A soft band across the fill, clear at both ends and [color] in the middle, one unit tall. */
    private val bands = HashMap<Int, LinearGradient>()

    private fun band(color: Int): LinearGradient = bands.getOrPut(color) {
        LinearGradient(
            0f, 0f, 0f, 1f,
            intArrayOf(color and 0xFFFFFF, color, color and 0xFFFFFF),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    /** The Effort fill's spectrum, round once and back to its start, so the rim's turn has no seam. */
    private val rim: SweepGradient by lazy {
        val p = EffortFill.palette()
        val colors = IntArray(EffortFill.SPECTRUM_COUNT + 1) {
            p[EffortFill.SPECTRUM_FIRST + it % EffortFill.SPECTRUM_COUNT].toInt()
        }
        SweepGradient(0f, 0f, colors, null)
    }

    private val blossom: RadialGradient by lazy {
        RadialGradient(
            0f, 0f, 1f,
            intArrayOf(0xCCFFFFFF.toInt(), 0x40FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    /** Draws [shader], made at unit height, stretched over [top]..[bottom] across [r]. */
    private fun bandAt(canvas: Canvas, color: Int, r: RectF, top: Float, bottom: Float, alpha: Float) {
        if (alpha <= 0.004f || bottom <= top) return
        val shader = band(color)
        matrix.setScale(1f, bottom - top)
        matrix.postTranslate(0f, top)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        paint.alpha = a255(alpha)
        canvas.drawRect(r.left, top, r.right, bottom, paint)
        paint.shader = null
    }

    /**
     * Over the lit part, which the caller has clipped to: the sheen that sweeps up it, quicker and
     * brighter at each fifth; the flash as a fifth is passed; and the wave up the panel as it
     * reaches the top.
     *
     * @param ink the light to paint in: white over a dark fill, a near black over a pale one.
     * @param stepS seconds since a fifth was passed, or negative for none.
     * @param fullS seconds since the top was reached, or negative for none.
     * @param sheen false for the fills whose lit part is not solid — dots, a grid — where a band
     *   across it would paint the gaps as well.
     */
    fun drawLit(
        canvas: Canvas,
        r: RectF,
        fillTop: Float,
        value: Float,
        style: LevelFeedback.Style,
        timeS: Float,
        stepS: Float,
        fullS: Float,
        ink: Int,
        sheen: Boolean,
        alpha: Float,
    ) {
        val lit = r.bottom - fillTop
        if (lit <= 0.5f || alpha <= 0.01f) return
        val level = LevelFeedback.levelAt(value)
        if (style.follow) {
            if (sheen) {
                val period = LevelFeedback.sheenSeconds(level)
                val p = (timeS / period) % 1f
                val thick = maxOf(28f * density, lit * 0.45f)
                val centre = r.bottom + thick / 2f - (lit + thick) * p
                bandAt(canvas, ink, r, centre - thick / 2f, centre + thick / 2f, LevelFeedback.sheenStrength(level) * alpha)
            }
            val flash = LevelFeedback.fade(stepS, LevelFeedback.STEP_FLASH_S)
            if (flash > 0f) {
                paint.color = ink
                paint.alpha = a255(0.16f * flash * alpha)
                canvas.drawRect(r.left, fillTop, r.right, r.bottom, paint)
                paint.alpha = a255(0.85f * flash * alpha)
                canvas.drawRect(r.left, fillTop, r.right, fillTop + 2.5f * density, paint)
            }
        }
        if (style.full && fullS >= 0f && fullS < FULL_WAVE_S) {
            // The wave: from the bottom to the top of the panel in three quarters of a second.
            val p = fullS / FULL_WAVE_S
            val eased = 1f - (1f - p) * (1f - p)
            val thick = r.height() * 0.4f
            val centre = r.bottom + thick / 2f - (r.height() + thick) * eased
            bandAt(canvas, ink, r, centre - thick / 2f, centre + thick / 2f, 0.6f * (1f - p) * alpha)
        }
    }

    /**
     * Over the whole panel, which the caller has clipped to: a breath of [glow] at the level when
     * it is low, or at the bottom when it is off; and at the top, a rim of colour turning round
     * the panel, which flares and blossoms as the top is reached.
     *
     * @param outline the panel's shape, for the rim to follow.
     */
    fun drawPanel(
        canvas: Canvas,
        outline: Path,
        r: RectF,
        fillTop: Float,
        value: Float,
        style: LevelFeedback.Style,
        timeS: Float,
        fullS: Float,
        glow: Int,
        alpha: Float,
    ) {
        if (alpha <= 0.01f || r.isEmpty) return
        val breath = LevelFeedback.breath(timeS)
        if (style.low && LevelFeedback.isLow(value)) {
            if (value <= OFF) {
                // Off: an ember at the foot of the track, so an empty panel is still seen to be one.
                val w = r.width() * 0.42f
                val h = 3.5f * density
                val cx = r.centerX()
                val bottom = r.bottom - 7f * density
                paint.shader = null
                paint.color = glow
                paint.alpha = a255((0.3f + 0.6f * breath) * alpha)
                rect.set(cx - w / 2f, bottom - h, cx + w / 2f, bottom)
                canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
                bandAt(canvas, glow, r, bottom - 16f * density, bottom + 10f * density, 0.35f * breath * alpha)
            } else {
                // Low: the level line breathes, fainter the nearer it is to leaving the low stretch.
                val strength = (0.3f + 0.55f * breath) * (1f - 0.4f * value / LevelFeedback.LOW)
                bandAt(canvas, glow, r, fillTop - 14f * density, fillTop + 14f * density, strength * alpha)
            }
        }
        if (style.full && LevelFeedback.isFull(value)) {
            val burst = LevelFeedback.fade(fullS, LevelFeedback.FULL_BURST_S)
            val cx = r.centerX()
            val cy = r.centerY()
            // The rim: the spectrum turning round the edge, breathing, flaring as the top is reached.
            matrix.setRotate((timeS / LevelFeedback.RIM_TURN_S) * 360f % 360f)
            matrix.postTranslate(cx, cy)
            rim.setLocalMatrix(matrix)
            line.shader = rim
            // Centred on the outline and clipped to the panel, so half of it shows, inside.
            line.strokeWidth = (5f + 7f * burst) * density
            line.alpha = a255(((0.6f + 0.3f * breath) * (1f - burst) + burst) * alpha)
            canvas.drawPath(outline, line)
            line.shader = null
            if (burst > 0f) {
                // The blossom at the top, where the finger arrived, and a ring of gold going out.
                val top = r.top + r.width() * 0.5f
                val grow = 1f - burst
                val radius = r.width() * (0.5f + 1.3f * grow)
                matrix.setScale(radius, radius)
                matrix.postTranslate(cx, top)
                blossom.setLocalMatrix(matrix)
                paint.shader = blossom
                paint.alpha = a255(burst * alpha)
                canvas.drawRect(cx - radius, top - radius, cx + radius, top + radius, paint)
                paint.shader = null
                line.color = GOLD
                line.strokeWidth = 2.5f * density
                line.alpha = a255(burst * alpha)
                canvas.drawCircle(cx, top, r.width() * (0.2f + 1.6f * grow), line)
            }
        }
    }

    private fun a255(f: Float): Int = (f * 255f).toInt().coerceIn(0, 255)

    private companion object {
        /** At or below this the panel is off rather than low. */
        const val OFF = 0.0005f

        /** How long the wave up the panel takes when the top is reached, in seconds. */
        const val FULL_WAVE_S = 0.75f

        val GOLD = Color.rgb(0xFA, 0xC3, 0x5F)
    }
}
