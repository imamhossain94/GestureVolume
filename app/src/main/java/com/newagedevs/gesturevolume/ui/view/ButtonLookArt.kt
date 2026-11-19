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
import com.newagedevs.gesturevolume.utils.ButtonBump
import com.newagedevs.gesturevolume.utils.ButtonLookFill
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws the Button looks fill: one of ten of the button animation's looks, worn by the Quick
 * panel, filled to the level. See [ButtonLookFill].
 *
 * Inside the panel's clip and over the whole track, like the other fills with settings of their
 * own: most of a look is below the level, but Ripple's rings travel up the empty track and
 * Spectrum turns round the whole rim. The looks that light the fill's edge stroke the panel's own
 * outline, cut to below the level, so the light follows any shape the panel has been given — a
 * rounded rectangle, a tab, a flared bar — exactly.
 *
 * Nothing is made per frame: each gradient is made once for the palette, at unit size, and moved
 * into place with a matrix; the paths are reused.
 */
internal class ButtonLookArt(private val density: Float) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val shape = Path()
    private val line = Path()
    private val rect = RectF()
    private val matrix = Matrix()

    // ---- the gradients, for the palette they were made with --------------------------------------

    private var madeFor: IntArray? = null
    private var madeLook = ""

    /** The body, top to bottom, over a unit height. */
    private var body: LinearGradient? = null

    /** A run of the look's colours, repeating, over a unit length: the spectrum and the sky. */
    private var run: LinearGradient? = null

    /** The spectrum round the rim, turning about the panel's middle. */
    private var sweep: SweepGradient? = null

    /** One lava blob, at unit radius. */
    private var blob: RadialGradient? = null

    /** The night under the aurora, deepest at the bottom, over a unit height. */
    private var night: LinearGradient? = null

    /** A curtain of the aurora's light, or the glass's frost, across a unit width. */
    private var band: LinearGradient? = null

    /** A glint's light, brightest in the middle of a unit height and gone at both ends. */
    private var glintLight: LinearGradient? = null

    // ---- the beats --------------------------------------------------------------------------------

    /** When the panel was first drawn, on the fill's clock: Neon strikes from then. */
    private var shownAt = Float.NaN

    private var beatAt = NO_BEAT
    private var beatDir = 1
    private val ringAt = FloatArray(ButtonLookFill.MAX_RINGS) { NO_BEAT }
    private var ringNext = 0

    /** Forgets the beats and the strike: the panel is opening afresh. */
    fun reset() {
        shownAt = Float.NaN
        beatAt = NO_BEAT
        ringAt.fill(NO_BEAT)
    }

    /**
     * A beat at [time] on the fill's clock, the level moving [direction]: the panel's press. Two
     * closer together than [ButtonLookFill.MIN_BEAT_GAP_S] are one, going the later way.
     */
    fun beat(time: Float, direction: Int) {
        val dir = if (direction < 0) -1 else 1
        if (time - beatAt in 0f..ButtonLookFill.MIN_BEAT_GAP_S) {
            beatDir = dir
            return
        }
        beatAt = time
        beatDir = dir
        ringAt[ringNext] = time
        ringNext = (ringNext + 1) % ringAt.size
    }

    /**
     * Paints [style]'s look over [r], filled to [value].
     *
     * @param outline the panel's own shape, the one the canvas is clipped to: the looks that light
     *   the fill's edge stroke it, so the light runs exactly along the panel's.
     * @param time the fill's own clock, in seconds, run at the style's speed.
     * @param p the look's colours, its own or the user's: see [ButtonLookFill.palette].
     * @param keepOut where the number and the icon are, as (top, bottom) pairs, NaN for one not
     *   shown: the lines drawn up the middle of the fill stop short of them.
     * @param alpha how much of the whole shows, 0..1: the panel fading its contents in and out.
     */
    fun draw(
        canvas: Canvas,
        r: RectF,
        outline: Path,
        value: Float,
        time: Float,
        style: ButtonLookFill.Style,
        p: IntArray,
        keepOut: FloatArray,
        alpha: Float,
    ) {
        if (r.width() <= 1f || r.height() <= 1f || alpha <= 0.004f || p.size < 4) return
        if (shownAt.isNaN() || time < shownAt) shownAt = time
        prepare(style.look, p)
        val look = frame
        look.canvas = canvas
        look.r = r
        look.outline = outline
        look.top = r.bottom - r.height() * value.coerceIn(0f, 1f)
        look.time = time
        look.since = if (beatAt == NO_BEAT || time < beatAt) -1f else time - beatAt
        look.bounce = style.bounce
        look.p = p
        look.keepOut = keepOut
        look.alpha = alpha
        when (style.look) {
            ButtonLookFill.NEON -> drawNeon(look)
            ButtonLookFill.RIPPLE -> drawRipple(look)
            ButtonLookFill.LAVA -> drawLava(look)
            ButtonLookFill.SPECTRUM -> drawSpectrum(look)
            ButtonLookFill.ELASTIC -> drawElastic(look)
            ButtonLookFill.HEARTBEAT -> drawHeartbeat(look)
            ButtonLookFill.GLASS -> drawGlass(look)
            ButtonLookFill.AURORA -> drawAurora(look)
            ButtonLookFill.JELLY -> drawJelly(look)
            else -> drawLine(look)
        }
        fill.shader = null
        stroke.shader = null
    }

    /** One frame's worth of what every look is drawn from: one of them, filled in each frame. */
    private class Look {
        lateinit var canvas: Canvas
        var r = RectF()
        var outline = Path()

        /** The level, as a height on the canvas. */
        var top = 0f
        var time = 0f

        /** Seconds since the last beat, or -1 before there has been one. */
        var since = -1f
        var bounce = 0f
        var p = IntArray(0)
        var keepOut = FloatArray(0)
        var alpha = 1f

        val lit: Float get() = r.bottom - top

        /** The press, as the animation plays it, at the strength asked for. */
        val press: Float get() = ButtonBump.pulse(since) * bounce
    }

    private val frame = Look()

    /** What [clearRun] found: the stretch's bottom and top. */
    private var runFrom = 0f
    private var runTo = 0f

    // ---- Line ---------------------------------------------------------------------------------------

    /** A lit line round the fill, as far as the level, a glint climbing it, and a bright bar across. */
    private fun drawLine(l: Look) {
        val c = l.canvas
        val r = l.r
        fill.color = withAlpha(l.p[1], 0.26f * l.alpha)
        c.drawRect(r.left, l.top, r.right, r.bottom, fill)
        // The rail above the level, unlit: the line is there, waiting.
        c.save()
        c.clipRect(r.left - 1f, r.top - 1f, r.right + 1f, l.top)
        stroke.strokeWidth = 2.4f * density
        stroke.color = withAlpha(l.p[2], 0.16f * l.alpha)
        c.drawPath(l.outline, stroke)
        c.restore()
        // Lit, round the fill: half of each stroke is outside the panel and cut off, so twice as wide.
        c.save()
        c.clipRect(r.left - 1f, l.top, r.right + 1f, r.bottom + 1f)
        glow(c, l.outline, l.p[2], l.alpha * (0.78f + 0.22f * l.press), 4.4f * density)
        c.restore()
        // A glint climbing both sides from the bottom to the level, again and again.
        if (l.lit > 10f * density) {
            val k = ((l.time % ButtonLookFill.GLINT_S) + ButtonLookFill.GLINT_S) % ButtonLookFill.GLINT_S /
                ButtonLookFill.GLINT_S
            c.save()
            c.clipRect(r.left - 1f, l.top, r.right + 1f, r.bottom + 1f)
            glint(c, l.outline, r.bottom - l.lit * k, 10f * density, l.alpha * sin(PI.toFloat() * k), 3f * density)
            c.restore()
        }
        // The level: a bright bar across.
        line.reset()
        line.moveTo(r.left, l.top)
        line.lineTo(r.right, l.top)
        glow(c, line, l.p[3], l.alpha * (0.85f + 0.15f * l.press), 1.6f * density)
    }

    // ---- Neon ---------------------------------------------------------------------------------------

    /** The fill dark, its outline a neon tube, a second tube up its middle. */
    private fun drawNeon(l: Look) {
        val c = l.canvas
        val r = l.r
        // Strikes like a tube: a few stutters as it comes on and at each step, a hum while it stays.
        val strike = l.time - shownAt
        val flicker = when {
            strike in 0f..0.3f -> if ((strike * 40f).toInt() % 3 == 0) 0.35f else 1f
            l.since in 0f..0.12f && l.bounce > 0f ->
                if ((l.since * 60f).toInt() % 2 == 0) 1f - 0.45f * l.bounce else 1f
            else -> 0.92f + 0.08f * sin(strike * 9f)
        }
        fill.color = withAlpha(l.p[0], 0.88f * l.alpha)
        c.drawRect(r.left, l.top, r.right, r.bottom, fill)
        c.save()
        c.clipRect(r.left - 1f, l.top, r.right + 1f, r.bottom + 1f)
        glow(c, l.outline, l.p[1], l.alpha * flicker, 4f * density)
        c.restore()
        line.reset()
        line.moveTo(r.left, l.top)
        line.lineTo(r.right, l.top)
        glow(c, line, l.p[1], l.alpha * flicker, 2f * density)
        // The core, up the middle to just under the level, clear of the number and the icon.
        val gap = 8f * density
        clearRun(l, r.bottom - gap, l.top + gap, gap)
        if (runFrom - runTo > gap) {
            line.reset()
            line.moveTo(r.centerX(), runFrom)
            line.lineTo(r.centerX(), runTo)
            glow(c, line, l.p[2], l.alpha * flicker * 0.9f, 1.6f * density)
        }
    }

    // ---- Ripple -------------------------------------------------------------------------------------

    /** Water, its surface rippling, a ring leaving the level at every step, up and down. */
    private fun drawRipple(l: Look) {
        val c = l.canvas
        val r = l.r
        val amp = (1.4f + 2.4f * l.press) * density
        surface(r, l.top, amp) { x -> ButtonLookFill.rippleSurface(x, l.time * 1.6f) }
        bodyShader(l.top - amp, r.bottom)
        fill.alpha = (255 * l.alpha).roundToInt()
        c.drawPath(shape, fill)
        fill.shader = null
        stroke.strokeWidth = 1.4f * density
        stroke.color = withAlpha(l.p[3], 0.6f * l.alpha)
        c.drawPath(line, stroke)
        // The rings: circles round the point the step landed on, cut to either side of the level.
        val reach = min(r.height() * 0.55f, 96f * density)
        for (born in ringAt) {
            if (born == NO_BEAT || l.time < born) continue
            val age = l.time - born
            if (age > ButtonLookFill.RING_LIFE_S) continue
            val radius = 4f * density + reach * ButtonLookFill.ringTravel(age)
            val fade = ButtonLookFill.ringFade(age) * (0.35f + 0.65f * l.bounce) * l.alpha
            stroke.strokeWidth = (3.2f - 2.2f * age / ButtonLookFill.RING_LIFE_S) * density
            c.save()
            c.clipRect(r.left - 1f, r.top - 1f, r.right + 1f, l.top)
            stroke.color = withAlpha(l.p[2], 0.65f * fade)
            c.drawCircle(r.centerX(), l.top, radius, stroke)
            c.restore()
            c.save()
            c.clipRect(r.left - 1f, l.top, r.right + 1f, r.bottom + 1f)
            stroke.color = withAlpha(l.p[3], 0.3f * fade)
            c.drawCircle(r.centerX(), l.top, radius * 0.7f, stroke)
            c.restore()
        }
    }

    // ---- Lava ---------------------------------------------------------------------------------------

    /** Molten rock: blobs running along a glowing surface, blobs of it rising inside, hottest on top. */
    private fun drawLava(l: Look) {
        val c = l.canvas
        val r = l.r
        val w = r.width()
        val amp = (4.5f + 3f * l.press) * density * (0.6f + 0.4f * (w / (32f * density)).coerceAtMost(1.4f))
        val base = l.top + amp * 0.35f
        surface(r, base, amp) { x -> ButtonLookFill.lavaSurface(x, l.time) }
        // The heat over it first, the colour of the hottest part, so the body is drawn over its root.
        stroke.strokeWidth = 9f * density
        stroke.color = withAlpha(l.p[2], 0.2f * l.alpha)
        c.drawPath(line, stroke)
        bodyShader(base - amp, r.bottom)
        fill.alpha = (255 * l.alpha).roundToInt()
        c.drawPath(shape, fill)
        fill.shader = null
        // The lamp inside: soft hot blobs rising through the rock and round again.
        val blobShader = blob ?: return
        c.save()
        c.clipPath(shape)
        for (k in 0 until 3) {
            val rise = ButtonLookFill.lavaRise(k, l.time)
            val radius = w * (0.3f + 0.06f * k)
            val y = r.bottom + radius - (l.lit + radius * 2f) * rise
            val x = r.centerX() + w * 0.22f * sin(l.time * (0.6f + 0.15f * k) + k * 2.1f)
            matrix.setScale(radius, radius)
            matrix.postTranslate(x, y)
            blobShader.setLocalMatrix(matrix)
            fill.shader = blobShader
            fill.alpha = (255 * l.alpha * 0.8f).roundToInt()
            c.drawCircle(x, y, radius, fill)
        }
        fill.shader = null
        c.restore()
    }

    // ---- Spectrum -----------------------------------------------------------------------------------

    /** The spectrum turning round the panel's rim, the level in rainbow, over a darkened track. */
    private fun drawSpectrum(l: Look) {
        val c = l.canvas
        val r = l.r
        fill.color = withAlpha(l.p[0], 0.5f * l.alpha)
        c.drawRect(r, fill)
        // The level in rainbow, the colours climbing it.
        val runShader = run ?: return
        val period = maxOf(r.height(), 120f * density)
        matrix.setScale(1f, period)
        matrix.postTranslate(0f, r.top - (l.time * 60f * density) % period)
        runShader.setLocalMatrix(matrix)
        fill.shader = runShader
        fill.alpha = (255 * l.alpha).roundToInt()
        c.drawRect(r.left, l.top, r.right, r.bottom, fill)
        fill.shader = null
        // Darker towards the bottom, so the light reads as rising out of it.
        night?.let { dark ->
            matrix.setScale(1f, l.lit.coerceAtLeast(1f))
            matrix.postTranslate(0f, l.top)
            dark.setLocalMatrix(matrix)
            fill.shader = dark
            fill.alpha = (255 * 0.7f * l.alpha).roundToInt()
            c.drawRect(r.left, l.top, r.right, r.bottom, fill)
            fill.shader = null
        }
        // The rim: the spectrum turning round the whole panel, in glow passes.
        val sweepShader = sweep ?: return
        matrix.setRotate((l.time * 90f) % 360f)
        matrix.postTranslate(r.centerX(), r.centerY())
        sweepShader.setLocalMatrix(matrix)
        val shine = (0.8f + 0.2f * l.press) * l.alpha
        for ((width, a) in ButtonLookFill.GLOW) {
            stroke.shader = sweepShader
            stroke.strokeWidth = width * 4.4f * density
            stroke.alpha = (255 * a * shine).roundToInt()
            c.drawPath(l.outline, stroke)
        }
        stroke.shader = null
        line.reset()
        line.moveTo(r.left, l.top)
        line.lineTo(r.right, l.top)
        glow(c, line, Color.WHITE, l.alpha * 0.85f, 1.4f * density)
    }

    // ---- Elastic ------------------------------------------------------------------------------------

    /** The level's surface stretched the way it moved at each step, springing back past flat. */
    private fun drawElastic(l: Look) {
        val c = l.canvas
        val r = l.r
        val stretch = ButtonLookFill.spring(l.since) * l.bounce * beatDir
        val pull = min(14f * density, r.width() * 0.45f) * stretch
        val lift = 3f * density * stretch
        // Upward for a step up, downward for a step down: a negative pull bows the other way.
        surface(r, l.top - lift, pull) { u -> ButtonLookFill.bow(u) }
        bodyShader(l.top - kotlin.math.abs(pull) - kotlin.math.abs(lift), r.bottom)
        fill.alpha = (255 * l.alpha).roundToInt()
        c.drawPath(shape, fill)
        fill.shader = null
        stroke.strokeWidth = 1.6f * density
        stroke.color = withAlpha(l.p[3], 0.75f * l.alpha)
        c.drawPath(line, stroke)
    }

    // ---- Heartbeat ----------------------------------------------------------------------------------

    /** Two quick throbs at every step, a glow at the level, a trace running up through the fill. */
    private fun drawHeartbeat(l: Look) {
        val c = l.canvas
        val r = l.r
        val beat = ButtonLookFill.throb(l.time, l.since, l.bounce)
        val top = (l.top - beat * 3f * density).coerceAtLeast(r.top)
        bodyShader(top, r.bottom)
        fill.alpha = (255 * l.alpha).roundToInt()
        c.drawRect(r.left, top, r.right, r.bottom, fill)
        fill.shader = null
        // Brighter on each throb.
        fill.color = withAlpha(l.p[2], 0.3f * beat * l.alpha)
        c.drawRect(r.left, top, r.right, r.bottom, fill)
        line.reset()
        line.moveTo(r.left, top)
        line.lineTo(r.right, top)
        glow(c, line, l.p[2], l.alpha * (0.4f + 0.6f * beat), 2.4f * density)
        // The trace: a flat line with a beat in it, drawn up the fill as the step beats; at rest, the line.
        val gap = 7f * density
        clearRun(l, r.bottom - gap, top + gap, gap)
        val from = runFrom
        val to = runTo
        val run = (from - to) / 2f
        if (run < 6f * density) return
        val x = r.centerX()
        val mid = (from + to) / 2f
        val amp = r.width() * 0.3f
        if (l.since in 0f..0.9f && l.bounce > 0f) {
            line.reset()
            line.moveTo(x, mid + run)
            line.lineTo(x, mid + run * 0.3f)
            line.lineTo(x + amp * 0.35f, mid + run * 0.18f)
            line.lineTo(x - amp, mid + run * 0.02f)
            line.lineTo(x + amp, mid - run * 0.12f)
            line.lineTo(x, mid - run * 0.26f)
            line.lineTo(x, mid - run)
            // Drawn as it beats: the part of it reached so far, then fading.
            val k = (l.since / 0.6f).coerceIn(0f, 1f)
            val fade = 1f - (l.since - 0.6f).coerceAtLeast(0f) / 0.3f
            c.save()
            c.clipRect(r.left - 1f, mid + run - (run * 2f + gap) * k, r.right + 1f, r.bottom + 1f)
            glow(c, line, l.p[3], l.alpha * fade * (0.5f + 0.5f * l.bounce), 1.6f * density)
            c.restore()
        } else {
            line.reset()
            line.moveTo(x, from)
            line.lineTo(x, to)
            stroke.strokeWidth = 1.2f * density
            stroke.color = withAlpha(l.p[3], 0.3f * l.alpha)
            c.drawPath(line, stroke)
        }
    }

    // ---- Glass --------------------------------------------------------------------------------------

    /** Frosted glass, a reflection rising through it, a bright rim, a glint running up the rim at a step. */
    private fun drawGlass(l: Look) {
        val c = l.canvas
        val r = l.r
        fill.color = withAlpha(l.p[0], 0.4f * l.alpha)
        c.drawRect(r.left, l.top, r.right, r.bottom, fill)
        val frost = band ?: return
        matrix.setScale(r.width(), 1f)
        matrix.postTranslate(r.left, 0f)
        frost.setLocalMatrix(matrix)
        fill.shader = frost
        fill.alpha = (255 * l.alpha).roundToInt()
        c.drawRect(r.left, l.top, r.right, r.bottom, fill)
        fill.shader = null
        fill.color = withAlpha(l.p[1], 0.22f * l.alpha)
        c.drawRect(r.left, l.top, r.right, r.bottom, fill)
        // The reflection: a slanted band of light rising through the glass.
        if (l.lit > 8f * density) {
            val k = ((l.time % ButtonLookFill.SHEEN_S) + ButtonLookFill.SHEEN_S) % ButtonLookFill.SHEEN_S /
                ButtonLookFill.SHEEN_S
            val slant = r.width() * 0.7f
            val y = r.bottom + slant - (l.lit + slant * 2f) * k
            val thick = 9f * density
            shape.reset()
            shape.moveTo(r.left, y + slant)
            shape.lineTo(r.right, y - slant)
            shape.lineTo(r.right, y - slant + thick)
            shape.lineTo(r.left, y + slant + thick)
            shape.close()
            c.save()
            c.clipRect(r.left, l.top, r.right, r.bottom)
            fill.color = withAlpha(l.p[3], 0.16f * sin(PI.toFloat() * k) * l.alpha)
            c.drawPath(shape, fill)
            c.restore()
        }
        // The rim, round the fill.
        c.save()
        c.clipRect(r.left - 1f, l.top, r.right + 1f, r.bottom + 1f)
        stroke.strokeWidth = 2.6f * density
        stroke.color = withAlpha(l.p[3], 0.6f * l.alpha)
        c.drawPath(l.outline, stroke)
        // The glint: a bright stretch of the rim running from the end the step came from.
        if (l.since in 0f..0.7f && l.bounce > 0f) {
            val k = l.since / 0.7f
            val y = if (beatDir > 0) r.bottom - l.lit * k else l.top + l.lit * k
            glint(c, l.outline, y, 14f * density, (1f - k) * l.bounce * l.alpha, 3f * density)
        }
        c.restore()
        // The meniscus at the level.
        line.reset()
        line.moveTo(r.left, l.top)
        line.lineTo(r.right, l.top)
        stroke.strokeWidth = 1.6f * density
        stroke.color = withAlpha(l.p[3], (0.75f + 0.25f * l.press) * l.alpha)
        c.drawPath(line, stroke)
    }

    // ---- Aurora -------------------------------------------------------------------------------------

    /** The lights of the sky drifting up through the fill, curtains swaying across it, the night below. */
    private fun drawAurora(l: Look) {
        val c = l.canvas
        val r = l.r
        val runShader = run ?: return
        // Mirrored, so one drift of a whole period up and back is seamless.
        val period = maxOf(r.height() * 0.9f, 110f * density)
        val drift = (l.time / ButtonLookFill.AURORA_DRIFT_S % 1f) * period * 2f
        matrix.setScale(1f, period)
        matrix.postTranslate(0f, r.top - drift)
        runShader.setLocalMatrix(matrix)
        fill.shader = runShader
        fill.alpha = (255 * l.alpha * (0.88f + 0.12f * l.press)).roundToInt()
        c.drawRect(r.left, l.top, r.right, r.bottom, fill)
        fill.shader = null
        // The curtains: soft upright bands of light, swaying.
        val curtain = band ?: return
        for (k in 0 until 3) {
            val width = r.width() * (0.34f + 0.08f * k)
            val x = r.centerX() + r.width() * 0.32f * sin(l.time * (0.55f + 0.17f * k) + k * 2.3f)
            matrix.setScale(width, 1f)
            matrix.postTranslate(x - width / 2f, 0f)
            curtain.setLocalMatrix(matrix)
            fill.shader = curtain
            fill.alpha = (255 * l.alpha * (0.55f + 0.45f * l.press)).roundToInt()
            c.drawRect(x - width / 2f, l.top, x + width / 2f, r.bottom, fill)
        }
        fill.shader = null
        // The night showing through at the bottom.
        night?.let { dark ->
            matrix.setScale(1f, l.lit.coerceAtLeast(1f))
            matrix.postTranslate(0f, l.top)
            dark.setLocalMatrix(matrix)
            fill.shader = dark
            fill.alpha = (255 * l.alpha).roundToInt()
            c.drawRect(r.left, l.top, r.right, r.bottom, fill)
            fill.shader = null
        }
        c.save()
        c.clipRect(r.left - 1f, l.top, r.right + 1f, r.bottom + 1f)
        stroke.strokeWidth = 2.8f * density
        stroke.color = withAlpha(Color.WHITE, 0.35f * l.alpha)
        c.drawPath(l.outline, stroke)
        c.restore()
        line.reset()
        line.moveTo(r.left, l.top)
        line.lineTo(r.right, l.top)
        glow(c, line, Color.WHITE, 0.6f * l.alpha, 1.2f * density)
    }

    // ---- Jelly --------------------------------------------------------------------------------------

    /** A block of jelly, its top rounded, a shine on it, wobbling at every step. */
    private fun drawJelly(l: Look) {
        val c = l.canvas
        val r = l.r
        val w = r.width()
        val wobble = ButtonBump.wobble(l.since) * l.bounce
        // Squashed lower and wider, then stretched taller and narrower, and settling: the animation's jiggle.
        val squash = min(7f * density, l.lit * 0.12f) * wobble
        val inset = (2.2f * density - 1.6f * density * wobble).coerceIn(0.4f * density, 4f * density)
        val top = (l.top + squash - l.press * 1.5f * density).coerceIn(r.top, r.bottom)
        val radius = min((w - inset * 2f) / 2f, 11f * density).coerceAtLeast(0f)
        rect.set(r.left + inset, top, r.right - inset, r.bottom + radius)
        bodyShader(top, r.bottom)
        fill.alpha = (255 * 0.95f * l.alpha).roundToInt()
        c.drawRoundRect(rect, radius, radius, fill)
        fill.shader = null
        stroke.strokeWidth = 1f * density
        stroke.color = withAlpha(l.p[3], 0.25f * l.alpha)
        c.drawRoundRect(rect, radius, radius, stroke)
        // The shine, as on a sweet: a soft streak down one side and a dot under it.
        val streak = min((r.bottom - top) * 0.45f, 46f * density)
        if (streak > 8f * density) {
            val x = r.left + inset + 2.5f * density
            rect.set(x, top + 4f * density, x + 3f * density, top + 4f * density + streak)
            fill.color = withAlpha(l.p[3], 0.45f * l.alpha)
            c.drawRoundRect(rect, 1.5f * density, 1.5f * density, fill)
            fill.color = withAlpha(l.p[3], 0.5f * l.alpha)
            c.drawCircle(x + 1.5f * density, rect.bottom + 4.5f * density, 1.4f * density, fill)
        }
    }

    // ---- small pieces -------------------------------------------------------------------------------

    /**
     * The fill's top edge as [surfaceAt] has it, 0..1 across, raised by up to [amp] from [base]:
     * [line] is the edge alone, and [shape] the edge closed down to the bottom of the panel.
     */
    private inline fun surface(r: RectF, base: Float, amp: Float, surfaceAt: (Float) -> Float) {
        shape.reset()
        line.reset()
        for (i in 0..SAMPLES) {
            val u = i / SAMPLES.toFloat()
            val x = r.left + r.width() * u
            val y = (base - surfaceAt(u) * amp).coerceIn(r.top, r.bottom)
            if (i == 0) {
                shape.moveTo(x, y)
                line.moveTo(x, y)
            } else {
                shape.lineTo(x, y)
                line.lineTo(x, y)
            }
        }
        shape.lineTo(r.right, r.bottom + 1f)
        shape.lineTo(r.left, r.bottom + 1f)
        shape.close()
    }

    /** Sets [fill]'s shader to the look's body, stretched from [from] down to [to]. */
    private fun bodyShader(from: Float, to: Float) {
        val shader = body ?: return
        matrix.setScale(1f, (to - from).coerceAtLeast(1f))
        matrix.postTranslate(0f, from)
        shader.setLocalMatrix(matrix)
        fill.shader = shader
    }

    /**
     * The stretch from [from] up to [to] that keeps [gap] clear of the number and the icon: cut short
     * at whichever of them it meets first going up, and starting above the icon when it starts on it.
     */
    private fun clearRun(l: Look, from: Float, to: Float, gap: Float) {
        var a = from
        var b = to
        for (i in 0 until l.keepOut.size / 2) {
            val kTop = l.keepOut[i * 2]
            val kBottom = l.keepOut[i * 2 + 1]
            if (kTop.isNaN() || kBottom.isNaN()) continue
            if (kBottom + gap <= b || kTop - gap >= a) continue
            // Overlapping: keep the longer of the two pieces either side.
            val below = a - (kBottom + gap)
            val above = (kTop - gap) - b
            if (below >= above) b = kBottom + gap else a = kTop - gap
        }
        runFrom = a
        runTo = b
    }

    /**
     * A stretch of [path] lit around the height [y], fading out [half] above and below it: a glow
     * whose light is a gradient up the canvas, so the stretch has soft ends wherever the path runs.
     */
    private fun glint(canvas: Canvas, path: Path, y: Float, half: Float, alpha: Float, core: Float) {
        val light = glintLight ?: return
        if (alpha <= 0.01f) return
        matrix.setScale(1f, half * 2f)
        matrix.postTranslate(0f, y - half)
        light.setLocalMatrix(matrix)
        stroke.shader = light
        for ((width, a) in ButtonLookFill.GLOW) {
            stroke.strokeWidth = width * core
            stroke.alpha = (255 * a * alpha.coerceIn(0f, 1f)).roundToInt()
            canvas.drawPath(path, stroke)
        }
        stroke.shader = null
    }

    /** A stroke with a glow: wide faint passes under a bright core, which every renderer draws. */
    private fun glow(canvas: Canvas, path: Path, colour: Int, alpha: Float, core: Float) {
        if (alpha <= 0.01f) return
        stroke.shader = null
        for ((width, a) in ButtonLookFill.GLOW) {
            stroke.strokeWidth = width * core
            stroke.color = withAlpha(colour, a * alpha.coerceIn(0f, 1f))
            canvas.drawPath(path, stroke)
        }
    }

    /** Makes the gradients again when the look or its colours have changed. */
    private fun prepare(look: String, p: IntArray) {
        if (look == madeLook && p.contentEquals(madeFor)) return
        madeLook = look
        madeFor = p.copyOf()
        val dark = p[0]
        body = when (look) {
            ButtonLookFill.LAVA -> LinearGradient(
                0f, 0f, 0f, 1f,
                intArrayOf(p[3], p[2], p[1], dark),
                floatArrayOf(0f, 0.16f, 0.5f, 1f), Shader.TileMode.CLAMP,
            )
            ButtonLookFill.HEARTBEAT, ButtonLookFill.RIPPLE -> LinearGradient(
                0f, 0f, 0f, 1f,
                intArrayOf(p[2], p[1], mix(p[1], dark, 0.55f)),
                floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP,
            )
            ButtonLookFill.JELLY -> LinearGradient(
                0f, 0f, 0f, 1f,
                intArrayOf(p[2], p[1], mix(p[1], dark, 0.4f)),
                floatArrayOf(0f, 0.25f, 1f), Shader.TileMode.CLAMP,
            )
            else -> LinearGradient(
                0f, 0f, 0f, 1f,
                intArrayOf(p[2], p[1], p[1], mix(p[1], dark, 0.45f)),
                floatArrayOf(0f, 0.12f, 0.45f, 1f), Shader.TileMode.CLAMP,
            )
        }
        // A run of the lights, back to the first, for the colours that cycle.
        val lights = p.copyOfRange(1, p.size)
        val cycle = IntArray(lights.size + 1) { lights[it % lights.size] }
        run = LinearGradient(
            0f, 0f, 0f, 1f, cycle, null,
            if (look == ButtonLookFill.AURORA) Shader.TileMode.MIRROR else Shader.TileMode.REPEAT,
        )
        sweep = SweepGradient(0f, 0f, cycle, null)
        blob = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(withAlpha(p[3], 0.9f), withAlpha(p[2], 0.5f), withAlpha(p[2], 0f)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        night = LinearGradient(
            0f, 0f, 0f, 1f,
            intArrayOf(withAlpha(dark, 0f), withAlpha(dark, 0f), withAlpha(dark, 0.62f)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        glintLight = LinearGradient(
            0f, 0f, 0f, 1f,
            intArrayOf(withAlpha(p[3], 0f), withAlpha(p[3], 1f), withAlpha(p[3], 0f)),
            null, Shader.TileMode.CLAMP,
        )
        band = if (look == ButtonLookFill.GLASS) {
            LinearGradient(
                0f, 0f, 1f, 0f,
                intArrayOf(withAlpha(Color.WHITE, 0.3f), withAlpha(Color.WHITE, 0.08f), withAlpha(Color.WHITE, 0.2f)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
        } else {
            LinearGradient(
                0f, 0f, 1f, 0f,
                intArrayOf(withAlpha(Color.WHITE, 0f), withAlpha(Color.WHITE, 0.26f), withAlpha(Color.WHITE, 0f)),
                null, Shader.TileMode.CLAMP,
            )
        }
    }

    private fun withAlpha(colour: Int, alpha: Float): Int =
        (colour and 0x00FFFFFF) or ((Color.alpha(colour) * alpha.coerceIn(0f, 1f)).roundToInt() shl 24)

    /** [from] moved [f] of the way to [to], alpha and all. */
    private fun mix(from: Int, to: Int, f: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from ushr shift) and 0xFF
            val b = (to ushr shift) and 0xFF
            return (a + (b - a) * f + 0.5f).toInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    companion object {
        /** Points along a surface: enough for a smooth curve across a narrow panel. */
        private const val SAMPLES = 16

        private const val NO_BEAT = -1_000_000f
    }
}
