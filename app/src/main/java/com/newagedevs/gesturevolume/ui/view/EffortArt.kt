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
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import com.newagedevs.gesturevolume.utils.EffortFill
import com.newagedevs.gesturevolume.utils.SliderFill
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the Effort fill: the whole track as an effort picker, lit up to the level, the level named.
 * See [EffortFill].
 *
 * Over the whole track rather than cut to the fill, like the Pixels grid, because the stops above
 * the level are half of what a picker shows: how far there still is to go.
 *
 * Built to look like a control rather than a picture — flat stops, one colour for the level the
 * finger is on, a clean edge where it ends — with the motion on top of that saying how hard it is
 * working: a sheen that sweeps quicker at every level, a glow that breathes at Max, the spectrum
 * running up it at Ultra max, and a flash as each new level is reached. Nothing is made per frame.
 */
internal class EffortArt(private val density: Float) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.LEFT
        letterSpacing = 0.02f
    }
    private val metrics = Paint.FontMetrics()
    private val rect = RectF()
    private val matrix = Matrix()
    private val glows = HashMap<Int, RadialGradient>()

    /** A white band, soft at both ends, at unit height: the sheen, moved into place by a matrix. */
    private val sheen = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0x00FFFFFF, Color.WHITE, 0x00FFFFFF),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )

    /** The top level's colours as a repeating ramp up the track; rebuilt only when they change. */
    private var spectrum: LinearGradient? = null
    private var spectrumSource: IntArray? = null

    /** ✦ at unit radius, for the sparks at the top level. */
    private val spark = Path().apply {
        moveTo(0f, -1f)
        quadTo(0.14f, -0.14f, 1f, 0f)
        quadTo(0.14f, 0.14f, 0f, 1f)
        quadTo(-0.14f, 0.14f, -1f, 0f)
        quadTo(-0.14f, -0.14f, 0f, -1f)
        close()
    }

    // What carries over from one frame to the next: where the sheen has got to, and when the level
    // last changed, so a new level can announce itself.
    private var lastTime = Float.NaN
    private var sweep = 0f
    private var shownLevel = -1
    private var arrivedAt = NEVER

    /** What the number and the icon are written in over the lit part, for the level last drawn. */
    var ink: Int = Color.WHITE
        private set

    /**
     * Paints the picker over [r], lit below [fillTop].
     *
     * @param time the fill's own clock, in seconds: it runs at the style's speed.
     * @param p the palette, the fill's own or the user's colours spread over it.
     * @param labels the levels' names, lowest first, and [shortLabels] the same shortened, for a
     *   track with no room for the whole name: Extra for Extra high, as a picker's stops put it.
     * @param labelTop where the number ends, and [labelBottom] where the icon begins: the name is
     *   kept between the two, so the three never sit on each other.
     * @param ghost the colour the unlit stops are a faint shade of: the panel's fill colour, which is
     *   the one colour sure to show against its track.
     * @param alpha how much of the whole shows, 0..1: the panel fading its contents in and out.
     */
    fun draw(
        canvas: Canvas,
        r: RectF,
        fillTop: Float,
        value: Float,
        time: Float,
        style: EffortFill.Style,
        p: IntArray,
        labels: Array<String>,
        shortLabels: Array<String>,
        labelTop: Float,
        labelBottom: Float,
        ghost: Int,
        alpha: Float,
    ) {
        if (p.size <= EffortFill.SPECTRUM_FIRST || r.width() <= 1f || r.height() <= 1f || alpha <= 0.004f) return
        val level = EffortFill.levelAt(value)
        advance(time, level)
        ink = inkFor(p, level)
        val arrival = ((time - arrivedAt) / EffortFill.ARRIVAL_SECONDS).coerceIn(0f, 1f)
        val look = EffortFill.sanitize(style.look)
        val a = alpha
        if (look == EffortFill.DOTS) {
            dots(canvas, r, fillTop, time, level, arrival, p, ghost, a)
        } else {
            steps(canvas, r, fillTop, time, level, arrival, p, ghost, a)
        }
        if (style.labels && level < labels.size) {
            readout(
                canvas, r, fillTop, time, level, arrival, p, labels[level], shortLabels.getOrNull(level) ?: labels[level],
                withDots = look == EffortFill.DOTS, labelTop = labelTop, labelBottom = labelBottom, a = a,
            )
        }
        paint.shader = null
    }

    // ---- Steps ----------------------------------------------------------------------------------

    /** A segment for each level up the track: the empty ones faint, the lit ones in the level's colour. */
    private fun steps(canvas: Canvas, r: RectF, fillTop: Float, time: Float, level: Int, arrival: Float, p: IntArray, ghost: Int, a: Float) {
        val w = r.width()
        val segment = r.height() / EffortFill.LEVELS
        val pad = (w * 0.14f).coerceIn(dp(2f), dp(8f))
        val gap = (segment * 0.1f).coerceIn(dp(1.5f), dp(3.5f))
        val left = r.left + pad
        val right = r.right - pad
        val round = min(right - left, segment - gap) * 0.32f
        val lit = r.bottom - fillTop

        backGlow(canvas, r, fillTop, time, level, p, a)
        val bandHeight = segment * 1.1f
        val bandCentre = r.bottom + bandHeight / 2f - (lit + bandHeight) * sweep
        for (k in 0 until EffortFill.LEVELS) {
            val top = r.bottom - (k + 1) * segment + gap / 2f
            val bottom = r.bottom - k * segment - gap / 2f
            rect.set(left, top, right, bottom)
            paint.shader = null
            paint.color = ghost
            paint.alpha = a255(GHOST * a)
            canvas.drawRoundRect(rect, round, round, paint)
            if (fillTop >= bottom) continue

            canvas.save()
            canvas.clipRect(left, max(top, fillTop), right, bottom)
            // The stops below the level a shade quieter than the one it is on.
            litPaint(p, level, r, time, (if (k == level) 1f else 0.74f) * a)
            canvas.drawRoundRect(rect, round, round, paint)
            band(canvas, left, right, bandCentre, bandHeight, EffortFill.sweepStrength(level) * a)
            if (k == level && arrival < 1f) {
                paint.shader = null
                paint.color = Color.WHITE
                paint.alpha = a255(0.55f * (1f - arrival) * (1f - arrival) * a)
                canvas.drawRoundRect(rect, round, round, paint)
            }
            canvas.restore()
        }

        // A clean edge across the stop the level is on, where the finger is.
        val stopTop = r.bottom - (level + 1) * segment + gap / 2f
        val stopBottom = r.bottom - level * segment - gap / 2f
        if (fillTop > stopTop + dp(1.5f) && fillTop < stopBottom - dp(1f)) {
            edge(canvas, left + round * 0.4f, right - round * 0.4f, fillTop, a)
        }
        if (level == EffortFill.ULTRA) sparks(canvas, left, right, fillTop, r.bottom, time, a)
    }

    // ---- Dots -----------------------------------------------------------------------------------

    /** One bar lit to the level, a tick at each stop; the dots are in the readout. */
    private fun dots(canvas: Canvas, r: RectF, fillTop: Float, time: Float, level: Int, arrival: Float, p: IntArray, ghost: Int, a: Float) {
        val w = r.width()
        val segment = r.height() / EffortFill.LEVELS
        val pad = (w * 0.16f).coerceIn(dp(2f), dp(9f))
        val left = r.left + pad
        val right = r.right - pad
        val round = (right - left) / 2f
        val bottom = r.bottom - pad
        rect.set(left, r.top + pad, right, bottom)
        paint.shader = null
        paint.color = ghost
        paint.alpha = a255(GHOST * a)
        canvas.drawRoundRect(rect, round, round, paint)

        backGlow(canvas, r, fillTop, time, level, p, a)
        if (fillTop < bottom) {
            canvas.save()
            canvas.clipRect(left, fillTop, right, bottom)
            litPaint(p, level, r, time, a)
            canvas.drawRoundRect(rect, round, round, paint)
            val bandHeight = segment * 1.2f
            band(canvas, left, right, bottom + bandHeight / 2f - (bottom - fillTop + bandHeight) * sweep, bandHeight, EffortFill.sweepStrength(level) * a)
            if (arrival < 1f) {
                // The part just reached lights up, and fades back into the bar.
                paint.shader = sheen
                matrix.setScale(1f, segment * 1.4f)
                matrix.postTranslate(0f, fillTop - segment * 0.7f)
                sheen.setLocalMatrix(matrix)
                paint.alpha = a255(0.6f * (1f - arrival) * (1f - arrival) * a)
                canvas.drawRect(left, fillTop, right, fillTop + segment * 0.7f, paint)
                paint.shader = null
            }
            canvas.restore()
        }

        // A tick at each stop, so the bar still reads as a picker and not a plain level.
        for (k in 1 until EffortFill.LEVELS) {
            val y = r.bottom - k * segment
            val onLit = y > fillTop
            paint.shader = null
            paint.color = if (onLit) ink else ghost
            paint.alpha = a255((if (onLit) 0.32f else 0.3f) * a)
            canvas.drawRect(left + round * 0.35f, y - dp(0.5f), right - round * 0.35f, y + dp(0.5f), paint)
        }
        if (fillTop > r.top + pad + dp(2f) && fillTop < bottom - dp(2f)) {
            edge(canvas, left + round * 0.3f, right - round * 0.3f, fillTop, a)
        }
        if (level == EffortFill.ULTRA) sparks(canvas, left, right, fillTop, bottom, time, a)
    }

    // ---- the name of the level --------------------------------------------------------------------

    /**
     * The level's name — after its dots, for [EffortFill.DOTS] — laid up the track: inside the lit
     * part just under the level when it fits, and just above the level when it does not, always
     * between the number and the icon. The short name, then a smaller size, where the whole one will
     * not go on either side; nowhere at all if even that will not, where the lit stops say it alone.
     */
    private fun readout(
        canvas: Canvas,
        r: RectF,
        fillTop: Float,
        time: Float,
        level: Int,
        arrival: Float,
        p: IntArray,
        label: String,
        shortLabel: String,
        withDots: Boolean,
        labelTop: Float,
        labelBottom: Float,
        a: Float,
    ) {
        val margin = dp(7f)
        val highest = max(r.top + dp(4f), labelTop)
        val lowest = min(r.bottom - dp(4f), labelBottom)
        val insideTop = max(fillTop + margin, highest)
        val roomInside = lowest - insideTop
        val roomAbove = min(fillTop - margin, lowest) - highest
        val base = (r.width() * 0.36f).coerceIn(dp(8f), dp(13f))

        // The whole name at full size, then the short one, then each a size down, and last the short
        // name without its dots: the first that fits inside the lit part, or failing that above it.
        var name = label
        var size = base
        var length = 0f
        var dotted = withDots
        var inside = false
        var found = false
        loop@ for (candidate in 0 until 5) {
            name = if (candidate % 2 == 0 && candidate < 4) label else shortLabel
            size = if (candidate < 2) base else (base * 0.82f).coerceAtLeast(dp(7f))
            dotted = withDots && candidate < 4
            length = readoutLength(name, size, dotted)
            when {
                length <= roomInside -> {
                    inside = true
                    found = true
                    break@loop
                }
                length <= roomAbove -> {
                    inside = false
                    found = true
                    break@loop
                }
            }
        }
        if (!found) return
        val start = if (inside) insideTop + length else min(fillTop - margin, lowest)
        text.textSize = size
        text.getFontMetrics(metrics)
        val centre = -(metrics.ascent + metrics.descent) / 2f
        val dot = size * 0.19f
        val pitch = dot * 2.9f
        val dotsLength = if (dotted) pitch * (EffortFill.LEVELS - 1) + dot * 2f else 0f
        val spacing = if (dotted) size * 0.5f else 0f
        val colour = if (inside) ink else nameColour(p, level, time)

        // Arriving, it slides in a few pixels and brightens, the way a picker's label changes.
        val shown = arrival * arrival * (3f - 2f * arrival)
        val slide = (1f - shown) * dp(5f)
        val fade = (0.25f + 0.75f * shown) * a
        if (inside) {
            // On a chip of the level's own colour, so the gap between two stops never runs through
            // the letters. Drawn before the turn, in the track's own terms, so at the top level the
            // spectrum on the chip is the spectrum around it.
            val across = min(size * 1.5f, r.width() * 0.72f)
            val ends = size * 0.35f
            litPaint(p, level, r, time, a)
            rect.set(r.centerX() - across / 2f, start + slide - length - ends, r.centerX() + across / 2f, start + slide + ends)
            canvas.drawRoundRect(rect, across / 2f, across / 2f, paint)
            paint.shader = null
        }
        canvas.save()
        canvas.rotate(-90f, r.centerX(), start + slide)
        // Turned, x runs up the track from where the readout begins and y across it.
        val y = start + slide
        var x = r.centerX()
        if (dotted) {
            for (k in 0 until EffortFill.LEVELS) {
                val cx = x + dot + k * pitch
                if (k <= level) {
                    val grow = if (k == level) 1f + 0.7f * (1f - shown) else 1f
                    paint.shader = null
                    paint.color = colour
                    paint.alpha = a255(fade)
                    canvas.drawCircle(cx, y, dot * grow, paint)
                } else {
                    ring.color = colour
                    ring.alpha = a255(0.5f * fade)
                    ring.strokeWidth = dot * 0.45f
                    canvas.drawCircle(cx, y, dot * 0.8f, ring)
                }
            }
            x += dotsLength + spacing
        }
        text.color = colour
        text.alpha = a255(fade)
        canvas.drawText(name, x, y + centre, text)
        canvas.restore()
    }

    /** How long the readout is along the track: its dots, if it has them, and [name] at [size]. */
    private fun readoutLength(name: String, size: Float, withDots: Boolean): Float {
        text.textSize = size
        if (!withDots) return text.measureText(name)
        val dot = size * 0.19f
        return dot * 2.9f * (EffortFill.LEVELS - 1) + dot * 2f + size * 0.5f + text.measureText(name)
    }

    // ---- the toolkit --------------------------------------------------------------------------

    /** Moves the sheen on by the time since the last frame, at the level's pace, and notes a new level. */
    private fun advance(time: Float, level: Int) {
        if (level != shownLevel) {
            // Not on the very first frame: a panel opening is not a level being reached.
            if (shownLevel >= 0) arrivedAt = time
            shownLevel = level
        }
        if (time < arrivedAt) arrivedAt = NEVER
        val last = lastTime
        lastTime = time
        if (last.isNaN()) return
        var dt = time - last
        if (dt < 0f) dt += EffortFill.TIME_WRAP_S
        sweep = (sweep + dt.coerceIn(0f, 0.1f) / EffortFill.sweepSeconds(level)) % 1f
    }

    /** Sets [paint] to the level's colour, or to the spectrum flowing up the track at the top level. */
    private fun litPaint(p: IntArray, level: Int, r: RectF, time: Float, a: Float) {
        if (level >= EffortFill.ULTRA) {
            val shader = spectrumShader(p)
            val span = r.height() * 0.9f
            val flow = (time % EffortFill.SPECTRUM_SECONDS) / EffortFill.SPECTRUM_SECONDS
            matrix.setScale(1f, span)
            matrix.postTranslate(0f, r.top - flow * span)
            shader.setLocalMatrix(matrix)
            paint.shader = shader
            paint.color = Color.WHITE
        } else {
            paint.shader = null
            paint.color = p[level]
        }
        paint.alpha = a255(a)
    }

    private fun spectrumShader(p: IntArray): LinearGradient {
        spectrum?.takeIf { p.contentEquals(spectrumSource) }?.let { return it }
        // The seven colours and the first again, so the ramp repeats without a seam.
        val colours = IntArray(EffortFill.SPECTRUM_COUNT + 1) { i ->
            p[(EffortFill.SPECTRUM_FIRST + i % EffortFill.SPECTRUM_COUNT).coerceAtMost(p.size - 1)]
        }
        return LinearGradient(0f, 0f, 0f, 1f, colours, null, Shader.TileMode.REPEAT).also {
            spectrum = it
            spectrumSource = p.copyOf()
        }
    }

    /** The colour the name is written in over the track, above the level: the level's own. */
    private fun nameColour(p: IntArray, level: Int, time: Float): Int {
        if (level < EffortFill.ULTRA) return p[level]
        // At the top, round the spectrum with the flow, one colour after another.
        val t = (time % EffortFill.SPECTRUM_SECONDS) / EffortFill.SPECTRUM_SECONDS * EffortFill.SPECTRUM_COUNT
        val k = t.toInt() % EffortFill.SPECTRUM_COUNT
        val from = p[(EffortFill.SPECTRUM_FIRST + k).coerceAtMost(p.size - 1)]
        val to = p[(EffortFill.SPECTRUM_FIRST + (k + 1) % EffortFill.SPECTRUM_COUNT).coerceAtMost(p.size - 1)]
        return ColorUtils.blendARGB(from, to, t - t.toInt())
    }

    /**
     * Dark writing on the pale levels and white on the deep ones, decided by how light the level's
     * colour is, so the user's own colours get the same care as the picker's.
     */
    private fun inkFor(p: IntArray, level: Int): Int {
        if (level >= EffortFill.ULTRA) return DARK_INK
        return if (ColorUtils.calculateLuminance(p[level]) > 0.3) DARK_INK else Color.WHITE
    }

    /** The sheen: a soft white band [height] tall, centred on [centre], across [left]..[right]. */
    private fun band(canvas: Canvas, left: Float, right: Float, centre: Float, height: Float, strength: Float) {
        if (strength <= 0.004f) return
        matrix.setScale(1f, height)
        matrix.postTranslate(0f, centre - height / 2f)
        sheen.setLocalMatrix(matrix)
        paint.shader = sheen
        paint.alpha = a255(strength)
        canvas.drawRect(left, centre - height / 2f, right, centre + height / 2f, paint)
        paint.shader = null
    }

    /** The bright line where the lit part ends: the picker's thumb. */
    private fun edge(canvas: Canvas, left: Float, right: Float, y: Float, a: Float) {
        paint.shader = null
        paint.color = Color.WHITE
        paint.alpha = a255(0.9f * a)
        rect.set(left, y, right, y + dp(1.6f))
        canvas.drawRoundRect(rect, dp(0.8f), dp(0.8f), paint)
    }

    /** At Max and above, a soft light breathing behind the lit part. */
    private fun backGlow(canvas: Canvas, r: RectF, fillTop: Float, time: Float, level: Int, p: IntArray, a: Float) {
        if (!EffortFill.glows(level)) return
        val lit = r.bottom - fillTop
        if (lit <= 1f) return
        val breath = 0.5f + 0.5f * sin(TAU * (time % EffortFill.GLOW_BREATH_SECONDS) / EffortFill.GLOW_BREATH_SECONDS)
        val colour = if (level >= EffortFill.ULTRA) nameColour(p, level, time) else p[level]
        val strength = (if (level >= EffortFill.ULTRA) 0.3f else 0.22f) + 0.22f * breath
        glow(canvas, r.centerX(), (fillTop + r.bottom) / 2f, r.width() * 0.9f, lit / 2f + dp(12f), colour, strength * a)
    }

    /** A handful of sparks twinkling in the lit part, for the top level only. */
    private fun sparks(canvas: Canvas, left: Float, right: Float, fillTop: Float, bottom: Float, time: Float, a: Float) {
        val lit = bottom - fillTop
        if (lit <= dp(6f)) return
        canvas.save()
        canvas.clipRect(left, fillTop, right, bottom)
        paint.shader = null
        paint.color = Color.WHITE
        for (i in 0 until SPARKS) {
            val life = ((time + seed(i * 13 + 1) * SPARK_LIFE) % SPARK_LIFE) / SPARK_LIFE
            val size = sin(PI.toFloat() * life).let { it * it }
            if (size < 0.03f) continue
            val x = left + (right - left) * (0.2f + 0.6f * seed(i * 7 + 3))
            val y = bottom - lit * (0.08f + 0.84f * seed(i * 5 + 2))
            val radius = dp(2f + 1.8f * seed(i * 3 + 4)) * size
            paint.alpha = a255(0.9f * size * a)
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(life * 90f)
            canvas.scale(radius, radius)
            canvas.drawPath(spark, paint)
            canvas.restore()
        }
        canvas.restore()
    }

    /** A soft light of [color] centred on ([cx], [cy]). See FillArt's glow, which this is. */
    private fun glow(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int, strength: Float) {
        if (strength <= 0.004f || rx <= 0.5f || ry <= 0.5f) return
        val shader = glows.getOrPut(color) {
            RadialGradient(
                0f, 0f, 1f,
                intArrayOf(color, ColorUtils.setAlphaComponent(color, 82), color and 0xFFFFFF),
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

    private fun a255(f: Float): Int = (f * 255f).toInt().coerceIn(0, 255)

    private fun dp(v: Float): Float = v * density

    private fun seed(n: Int): Float = SliderFill.pseudoRandom(n)

    private companion object {
        const val TAU = 2f * PI.toFloat()

        /** How much of an empty stop shows. */
        const val GHOST = 0.14f

        /** The writing on the pale levels. */
        const val DARK_INK = 0xFF14161B.toInt()

        const val SPARKS = 6

        /** How long a spark lives, in seconds; it divides the clock's wrap. */
        const val SPARK_LIFE = 1.6f

        /** Before any level has been reached. */
        const val NEVER = -1000f
    }
}
