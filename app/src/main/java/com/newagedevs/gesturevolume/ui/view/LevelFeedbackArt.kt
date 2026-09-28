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
import com.newagedevs.gesturevolume.utils.SliderFill
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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

    /** A neon tube's colours, pink through violet to cyan and back, round once so its turn has no seam. */
    private val neon: SweepGradient by lazy {
        SweepGradient(0f, 0f, NEON_COLORS, null)
    }

    /** The spectrum's colours, for confetti. */
    private val confettiColors: IntArray by lazy {
        val p = EffortFill.palette()
        IntArray(EffortFill.SPECTRUM_COUNT) { p[EffortFill.SPECTRUM_FIRST + it].toInt() }
    }

    /** A four-pointed star, one unit from its middle to each point, made once and scaled into place. */
    private val star: Path by lazy {
        Path().apply {
            moveTo(0f, -1f)
            lineTo(STAR_WAIST, -STAR_WAIST)
            lineTo(1f, 0f)
            lineTo(STAR_WAIST, STAR_WAIST)
            lineTo(0f, 1f)
            lineTo(-STAR_WAIST, STAR_WAIST)
            lineTo(-1f, 0f)
            lineTo(-STAR_WAIST, -STAR_WAIST)
            close()
        }
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
        if (style.full && style.max == LevelFeedback.MAX_BURST && fullS >= 0f && fullS < FULL_WAVE_S) {
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
     * it is low, or at the bottom when it is off; and at the top, the flourish the style names —
     * see [LevelFeedback.MAX_STYLES].
     *
     * @param outline the panel's shape, for the rim to follow.
     * @param ink the light to paint the flourishes in that have no colours of their own: white over
     *   a dark fill, a near black over a pale one.
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
        ink: Int,
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
            val arrive = LevelFeedback.fade(fullS, LevelFeedback.FULL_BURST_S)
            when (style.max) {
                LevelFeedback.MAX_RIPPLE -> drawRipple(canvas, r, timeS, fullS, ink, alpha)
                LevelFeedback.MAX_SHINE -> drawShine(canvas, r, timeS, arrive, ink, alpha)
                LevelFeedback.MAX_SPARKLE -> drawSparkle(canvas, r, timeS, arrive, ink, alpha)
                LevelFeedback.MAX_CONFETTI -> drawConfetti(canvas, r, timeS, fullS, alpha)
                LevelFeedback.MAX_NEON -> drawNeon(canvas, outline, r, timeS, fullS, alpha)
                LevelFeedback.MAX_PULSE -> drawPulse(canvas, outline, r, timeS, arrive, ink, alpha)
                else -> drawBurst(canvas, outline, r, timeS, breath, arrive, alpha)
            }
        }
    }

    /** The rim of the spectrum turning round the edge, flaring and blossoming as the top is reached. */
    private fun drawBurst(canvas: Canvas, outline: Path, r: RectF, timeS: Float, breath: Float, burst: Float, alpha: Float) {
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

    /**
     * Rings going out from the top, where the finger arrived: three bright ones in quick succession
     * as it is reached, then one every [LevelFeedback.RIPPLE_EVERY_S], each widening and fading.
     * Centred above a narrow panel, they cross it as arcs running down it.
     */
    private fun drawRipple(canvas: Canvas, r: RectF, timeS: Float, fullS: Float, ink: Int, alpha: Float) {
        val cx = r.centerX()
        val cy = r.top + r.width() * 0.5f
        val reach = r.height() * 1.05f
        line.shader = null
        line.color = ink
        val life = LevelFeedback.RIPPLE_LIFE_S
        for (k in 0 until 3) {
            val p = (((timeS + k * LevelFeedback.RIPPLE_EVERY_S) % life) + life) % life / life
            val grow = 1f - (1f - p) * (1f - p)
            line.strokeWidth = (3f - 1.8f * p) * density
            line.alpha = a255(0.5f * (1f - p) * alpha)
            canvas.drawCircle(cx, cy, r.width() * 0.25f + reach * grow, line)
        }
        if (fullS < 0f) return
        for (k in 0 until 3) {
            val p = (fullS - k * 0.14f) / RIPPLE_ARRIVAL_S
            if (p < 0f || p >= 1f) continue
            val grow = 1f - (1f - p) * (1f - p)
            line.strokeWidth = (4.5f - 2.5f * p) * density
            line.alpha = a255(0.9f * (1f - p) * alpha)
            canvas.drawCircle(cx, cy, r.width() * 0.2f + reach * grow, line)
        }
    }

    /**
     * A flash as the top is reached, then a glint sweeping up the panel on a slant every
     * [LevelFeedback.SHINE_S], with a thinner one close behind it, as across a card tilted to the
     * light.
     */
    private fun drawShine(canvas: Canvas, r: RectF, timeS: Float, arrive: Float, ink: Int, alpha: Float) {
        val period = LevelFeedback.SHINE_S
        val q = ((((timeS % period) + period) % period) / period) / LevelFeedback.SHINE_SWEEP
        if (q < 1f) {
            val eased = q * q * (3f - 2f * q)
            val thick = r.width() * 0.9f
            val travel = r.height() + r.width() * 3f
            val centre = r.bottom + r.width() * 1.5f - travel * eased
            glint(canvas, ink, r, centre, thick, 0.6f * alpha)
            glint(canvas, ink, r, centre + thick * 0.95f, thick * 0.3f, 0.4f * alpha)
        }
        if (arrive > 0f) {
            paint.shader = null
            paint.color = ink
            paint.alpha = a255(0.3f * arrive * alpha)
            canvas.drawRect(r, paint)
        }
    }

    /** A band of [color] [thick] across, centred on [centre] up the panel and turned to the slant. */
    private fun glint(canvas: Canvas, color: Int, r: RectF, centre: Float, thick: Float, alpha: Float) {
        val shader = band(color)
        matrix.setScale(1f, thick)
        matrix.postTranslate(0f, centre - thick / 2f)
        matrix.postRotate(SHINE_SLANT, r.centerX(), centre)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        paint.alpha = a255(alpha)
        canvas.drawRect(r, paint)
        paint.shader = null
    }

    /**
     * Four-pointed stars over the panel, each twinkling on its own beat, with a soft glow round it;
     * all of them flaring at once, larger, as the top is reached.
     */
    private fun drawSparkle(canvas: Canvas, r: RectF, timeS: Float, arrive: Float, ink: Int, alpha: Float) {
        paint.shader = null
        paint.color = ink
        for (i in 0 until SPARKLES) {
            val twinkle = LevelFeedback.twinkle(i, timeS)
            val lit = maxOf(twinkle, arrive)
            if (lit <= 0.01f) continue
            val x = r.left + r.width() * (0.2f + 0.6f * SliderFill.pseudoRandom(i * 31 + 7))
            val y = r.top + r.height() * (0.07f + 0.86f * ((i + SliderFill.pseudoRandom(i * 17 + 3) * 0.8f) / SPARKLES))
            val size = (3.5f + 3.5f * SliderFill.pseudoRandom(i * 13 + 5)) * density * (0.45f + 0.55f * lit) * (1f + 0.5f * arrive)
            paint.alpha = a255(0.22f * lit * alpha)
            canvas.drawCircle(x, y, size * 0.9f, paint)
            paint.alpha = a255(lit * alpha)
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(45f * twinkle)
            canvas.scale(size, size)
            canvas.drawPath(star, paint)
            canvas.restore()
        }
    }

    /**
     * Confetti thrown up from the top as it is reached, spreading and falling through the panel
     * under its own weight, tumbling as it goes; then, while it stays, a light fall from above.
     */
    private fun drawConfetti(canvas: Canvas, r: RectF, timeS: Float, fullS: Float, alpha: Float) {
        paint.shader = null
        val w = r.width()
        val h = r.height()
        val piece = 2.2f * density
        if (fullS >= 0f && fullS < LevelFeedback.CONFETTI_BURST_S) {
            val t = fullS
            val fade = 1f - (t / LevelFeedback.CONFETTI_BURST_S).let { it * it * it }
            for (i in 0 until CONFETTI_BURST) {
                val a = SliderFill.pseudoRandom(i * 13 + 1)
                val b = SliderFill.pseudoRandom(i * 13 + 2)
                val c = SliderFill.pseudoRandom(i * 13 + 3)
                val x = r.centerX() + (a - 0.5f) * w * 1.4f * t + sin(t * 6f + c * 6.3f) * w * 0.08f
                val y = r.top + w * 0.4f - h * (0.12f + 0.16f * b) * t + 0.5f * h * 0.95f * t * t
                tumble(canvas, x, y, piece, t * (4f + 5f * c) + c * 6.3f, confettiColors[i % confettiColors.size], fade * alpha)
            }
        }
        for (i in 0 until CONFETTI_FALL) {
            val a = SliderFill.pseudoRandom(i * 29 + 11)
            val b = SliderFill.pseudoRandom(i * 29 + 12)
            val fall = ((timeS / CONFETTI_FALL_S + b) % 1f + 1f) % 1f
            val x = r.left + w * (0.12f + 0.76f * a) + sin(timeS * 2.5f + a * 6.3f) * w * 0.1f
            val y = r.top - piece * 2f + (h + piece * 4f) * fall
            tumble(canvas, x, y, piece, timeS * (3f + 3f * a) + b * 6.3f, confettiColors[(i + 3) % confettiColors.size], 0.85f * alpha)
        }
    }

    /** One piece of confetti at ([x], [y]), turning through [spin], narrowed as it tumbles edge-on. */
    private fun tumble(canvas: Canvas, x: Float, y: Float, size: Float, spin: Float, color: Int, alpha: Float) {
        paint.color = color
        paint.alpha = a255(alpha)
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(spin * 57.3f)
        canvas.scale(maxOf(abs(cos(spin * 1.7f)), 0.25f), 1f)
        canvas.drawRect(-size, -size * 0.55f, size, size * 0.55f, paint)
        canvas.restore()
    }

    /**
     * The panel's edge lit like a neon tube in pink, violet and cyan, its colours drifting round:
     * a stutter as it strikes when the top is reached, then a steady hum. A wide faint stroke and a
     * narrow bright one, centred on the outline and clipped to the panel, so the glow falls inside.
     */
    private fun drawNeon(canvas: Canvas, outline: Path, r: RectF, timeS: Float, fullS: Float, alpha: Float) {
        val on = LevelFeedback.neonStrike(fullS) * (0.88f + 0.12f * LevelFeedback.breath(timeS))
        matrix.setRotate((timeS / NEON_TURN_S) * 360f % 360f)
        matrix.postTranslate(r.centerX(), r.centerY())
        neon.setLocalMatrix(matrix)
        line.shader = neon
        line.strokeWidth = 16f * density
        line.alpha = a255(0.2f * on * alpha)
        canvas.drawPath(outline, line)
        line.strokeWidth = 8f * density
        line.alpha = a255(0.45f * on * alpha)
        canvas.drawPath(outline, line)
        line.strokeWidth = 3f * density
        line.alpha = a255(on * alpha)
        canvas.drawPath(outline, line)
        line.shader = null
    }

    /**
     * The panel beating like a heart: a soft glow welling in from its edge with each throb, and over
     * a dark fill its light swelling from the middle too; strongest as the top is reached. Soft
     * layers rather than one hard stroke, which read as the panel being selected, not beating.
     */
    private fun drawPulse(canvas: Canvas, outline: Path, r: RectF, timeS: Float, arrive: Float, ink: Int, alpha: Float) {
        val beat = (LevelFeedback.heartbeat(timeS) * 0.8f + arrive).coerceAtMost(1f)
        if (beat <= 0.01f) return
        if (ink == Color.WHITE) {
            val radius = r.height() * (0.35f + 0.3f * beat)
            matrix.setScale(radius, radius)
            matrix.postTranslate(r.centerX(), r.centerY())
            blossom.setLocalMatrix(matrix)
            paint.shader = blossom
            paint.alpha = a255(0.45f * beat * alpha)
            canvas.drawRect(r, paint)
            paint.shader = null
        }
        line.shader = null
        line.color = ink
        // Centred on the outline and clipped to the panel: each falls inside by half its width.
        for ((width, strength) in PULSE_LAYERS) {
            line.strokeWidth = width * (0.6f + 0.4f * beat) * density
            line.alpha = a255(strength * beat * alpha)
            canvas.drawPath(outline, line)
        }
    }

    private fun a255(f: Float): Int = (f * 255f).toInt().coerceIn(0, 255)

    private companion object {
        /** At or below this the panel is off rather than low. */
        const val OFF = 0.0005f

        /** How long the wave up the panel takes when the top is reached, in seconds. */
        const val FULL_WAVE_S = 0.75f

        val GOLD = Color.rgb(0xFA, 0xC3, 0x5F)

        /** How long each of the three rings takes to go out as the top is reached, in seconds. */
        const val RIPPLE_ARRIVAL_S = 0.9f

        /** The shine's slant, in degrees from across the panel. */
        const val SHINE_SLANT = -24f

        /** How many stars, and how narrow a star is at its waist, out of its reach. */
        const val SPARKLES = 7
        const val STAR_WAIST = 0.2f

        /** Pieces thrown as the top is reached, and falling while it stays; and one piece's fall. */
        const val CONFETTI_BURST = 26
        const val CONFETTI_FALL = 9
        const val CONFETTI_FALL_S = 2.4f

        /** The pulse's glow, widest and faintest first, as (width in dp, strength). */
        val PULSE_LAYERS = listOf(20f to 0.1f, 11f to 0.16f, 5f to 0.28f)

        /** One turn of the neon's colours round the panel, in seconds. */
        const val NEON_TURN_S = 6f

        val NEON_COLORS = intArrayOf(
            Color.rgb(0xFF, 0x3D, 0xB8), Color.rgb(0xA8, 0x5C, 0xFF), Color.rgb(0x2E, 0xE6, 0xFF),
            Color.rgb(0xA8, 0x5C, 0xFF), Color.rgb(0xFF, 0x3D, 0xB8),
        )
    }
}
