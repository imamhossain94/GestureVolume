package com.newagedevs.gesturevolume.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.newagedevs.gesturevolume.data.local.ButtonBumpStore
import com.newagedevs.gesturevolume.utils.ButtonBump
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The button animation, drawn: the bump that grows out of the edge of the glass beside the volume
 * buttons as one is pressed, with the level inside it, in whichever of [ButtonBump.STYLES] is chosen.
 *
 * One view for the overlay on the glass and for every preview on the settings screen, so what the
 * user picks is what they get. Everything is drawn in the bump's upright terms — `x` inward from the
 * glass, `y` along the edge towards the bottom of the phone, as on the left edge of an upright phone
 * — and one matrix turns that onto whichever edge the buttons are on now ([ButtonBump.toWindow]).
 *
 * It animates only while there is something to move: a press starts the frames, and once the bump
 * has gone back into the glass and the last spark has faded the view stops asking for frames and
 * tells [listener], so the window can be taken away.
 */
class ButtonBumpView(context: Context) : View(context) {

    /** Told once the bump has gone back into the glass and nothing is left moving. */
    fun interface Listener {
        fun onHidden()
    }

    var listener: Listener? = null

    private val density = context.resources.displayMetrics.density

    private var settings = ButtonBumpStore.Settings()
    private var spec = ButtonBump.spec(settings.style)
    private var placement = ButtonBump.placement(
        left = true, along = 0.5f, turns = 0,
        screenWidth = 100, screenHeight = 100, span = 100, reach = 20,
    )

    /** Canonical (upright, left edge) to the window's own pixels. See [ButtonBump.toWindow]. */
    private val toWindow = Matrix()

    // ---- motion state ---------------------------------------------------------------------------

    private val presence = ButtonBump.Spring()
    private var presenceTarget = 0f

    /** The level shown, eased towards [levelTarget] so a step slides rather than jumps. */
    private val level = ButtonBump.Spring()
    private var levelTarget = 0.5f

    /** The elastic style's lean towards the button pressed, -1..1, on a spring of its own. */
    private val lean = ButtonBump.Spring()

    private var lastFrameMs = 0L
    private var shownAtMs = 0L
    private var pressAtMs = -100_000L
    private var hideAtMs = 0L
    private var pressDirection = 1
    private var atLimitPress = false
    private var running = false
    private var hiddenReported = true

    /** A frozen frame for a gallery tile: the bump out, half full, nothing moving. */
    private var still = false

    // ---- effects that outlive a frame -----------------------------------------------------------

    private class Ring(val bornMs: Long)
    private class Spark(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val bornMs: Long, val lifeMs: Long, val size: Float, val white: Boolean,
    )

    private val rings = ArrayList<Ring>()
    private val sparks = ArrayList<Spark>()
    private val random = java.util.Random()

    // ---- drawing tools ---------------------------------------------------------------------------

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val bodyPath = Path()
    private val rimPath = Path()
    private val scratchPath = Path()
    private val segmentPath = Path()
    private val measure = PathMeasure()
    private val rect = RectF()
    private val point = FloatArray(2)

    /** Long gradients made once and slid along with a matrix, rather than made every frame. */
    private var auroraShader: LinearGradient? = null
    private var spectrumShader: LinearGradient? = null
    private var lavaShader: LinearGradient? = null
    private var glassShader: LinearGradient? = null
    private val shaderMatrix = Matrix()
    private var shaderKey = 0f

    // ---- configuration -------------------------------------------------------------------------

    /**
     * What to draw and where. Safe to call while the bump is out: a rotation mid-animation simply
     * carries on on the new edge.
     */
    fun configure(settings: ButtonBumpStore.Settings, placement: ButtonBump.Placement) {
        val styleChanged = settings.style != this.settings.style
        this.settings = settings
        this.spec = ButtonBump.spec(settings.style)
        this.placement = placement
        if (styleChanged) {
            rings.clear()
            sparks.clear()
        }
        // The shaders are sized to the bump, so a new length or colour makes new ones.
        auroraShader = null
        spectrumShader = null
        lavaShader = null
        glassShader = null
        updateMatrix()
        invalidate()
    }

    private fun updateMatrix() {
        val p = placement
        val span = p.span.toFloat()
        val w = p.width.toFloat()
        val h = p.height.toFloat()
        val values = when (p.edge) {
            ButtonBump.Edge.LEFT -> if (p.flipped) floatArrayOf(1f, 0f, 0f, 0f, -1f, span) else floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f)
            ButtonBump.Edge.RIGHT -> if (p.flipped) floatArrayOf(-1f, 0f, w, 0f, -1f, span) else floatArrayOf(-1f, 0f, w, 0f, 1f, 0f)
            ButtonBump.Edge.TOP -> if (p.flipped) floatArrayOf(0f, -1f, span, 1f, 0f, 0f) else floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f)
            ButtonBump.Edge.BOTTOM -> if (p.flipped) floatArrayOf(0f, -1f, span, -1f, 0f, h) else floatArrayOf(0f, 1f, 0f, -1f, 0f, h)
        }
        toWindow.setValues(floatArrayOf(values[0], values[1], values[2], values[3], values[4], values[5], 0f, 0f, 1f))
    }

    /** Freezes the view on one frame of the bump, out and part full: for a tile in the style gallery. */
    fun setStill(level: Float = 0.62f) {
        still = true
        running = false
        presence.snapTo(1f)
        presenceTarget = 1f
        this.level.snapTo(level)
        levelTarget = level
        invalidate()
    }

    // ---- the press -----------------------------------------------------------------------------

    /**
     * A volume button was pressed: the bump comes out, or stays out, and shows [newLevel].
     *
     * @param direction `+1` for volume up, `-1` for down.
     * @param atLimit the level could not move: already at an end, or held there by the system.
     *   The bump answers with a nudge rather than a step.
     */
    fun press(direction: Int, newLevel: Float, atLimit: Boolean = false) {
        still = false
        val now = SystemClock.uptimeMillis()
        if (presenceTarget == 0f && presence.value < 0.02f) {
            // Coming out of the glass afresh: the level starts where it is, not where it last was.
            shownAtMs = now
            level.snapTo(newLevel.coerceIn(0f, 1f))
        }
        pressDirection = if (direction >= 0) 1 else -1
        atLimitPress = atLimit
        pressAtMs = now
        levelTarget = newLevel.coerceIn(0f, 1f)
        presenceTarget = 1f
        hideAtMs = now + (settings.holdMs / settings.speed.coerceAtLeast(0.1f)).toLong()
        // Towards the button that was pressed: up is towards the top of the phone, which is -y.
        lean.velocity += -pressDirection * (if (atLimit) 6f else 9f)
        when (settings.style) {
            ButtonBump.STYLE_RIPPLE -> {
                rings += Ring(now)
                while (rings.size > 6) rings.removeAt(0)
            }
            ButtonBump.STYLE_SPARKLE -> spawnSparks(now, if (atLimit) 8 else 16)
        }
        // A push to the spring at every press, so a held key keeps the bump alive and lively.
        presence.velocity += if (atLimit) 1.2f else 2.4f
        hiddenReported = false
        start()
    }

    /** The level moved without a press of ours — the system put it right a moment later. */
    fun setLevel(newLevel: Float) {
        levelTarget = newLevel.coerceIn(0f, 1f)
        if (presenceTarget > 0f) start()
    }

    /** Puts the bump away now, rather than when it would have gone by itself. */
    fun dismiss() {
        hideAtMs = 0L
        presenceTarget = 0f
        start()
    }

    /** Whether the bump is on the glass, or on its way there. */
    fun isOut(): Boolean = presenceTarget > 0f || presence.value > 0.02f

    private fun start() {
        if (running) return
        running = true
        lastFrameMs = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
        removeCallbacks(wake)
    }

    // ---- the frame ---------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        if (!still) advance(now)

        val p = presence.value
        if (p > 0.001f || rings.isNotEmpty() || sparks.isNotEmpty()) {
            canvas.save()
            canvas.concat(toWindow)
            drawStyle(canvas, now, p.coerceAtLeast(0f))
            canvas.restore()
            if (settings.showPercent && p > 0.25f) drawPercent(canvas, p)
        }

        if (running) {
            postInvalidateOnAnimation()
        } else if (!still && !hiddenReported && !isOut()) {
            hiddenReported = true
            listener?.onHidden()
        }
    }

    /** Moves everything on by the time since the last frame, and decides whether to keep going. */
    private fun advance(now: Long) {
        val dt = ((now - lastFrameMs).coerceAtLeast(0L) / 1000f * settings.speed)
            .coerceAtMost(ButtonBump.MAX_FRAME_S)
        lastFrameMs = now
        if (presenceTarget > 0f && hideAtMs in 1..now) presenceTarget = 0f

        // Out on the style's own spring; back into the glass a little firmer, so it never wobbles
        // on the way out.
        val damping = if (presenceTarget == 0f) maxOf(spec.damping, 0.9f) else spec.damping
        ButtonBump.stepSpring(presence, presenceTarget, spec.stiffness, damping, dt)
        if (presenceTarget == 0f && presence.value < 0f) presence.snapTo(0f)
        ButtonBump.stepSpring(level, levelTarget, 260f, 0.85f, dt)
        ButtonBump.stepSpring(lean, 0f, 180f, 0.32f, dt)

        val sparksAlive = updateSparks(now, dt)
        rings.removeAll { now - it.bornMs > RING_LIFE_MS }

        val settled = presence.isSettled(presenceTarget) && level.isSettled(levelTarget) &&
            lean.isSettled(0f, 0.01f)
        val pulsing = now - pressAtMs < (ButtonBump.PRESS_S * 1000f / settings.speed.coerceAtLeast(0.1f)).toLong() + 600L
        // The loops — aurora drifting, spectrum turning — keep going for as long as the bump is out.
        val looping = presenceTarget > 0f && settings.style in LOOPING_STYLES
        running = !settled || pulsing || looping || rings.isNotEmpty() || sparksAlive
        // Out and still: no frames until it is time to go back into the glass.
        removeCallbacks(wake)
        if (!running && presenceTarget > 0f && hideAtMs > now) postDelayed(wake, hideAtMs - now)
    }

    /** Wakes a bump that has been sitting still, when its time on the glass is up. */
    private val wake = Runnable { start() }

    private fun sinceS(now: Long, then: Long): Float = (now - then) / 1000f * settings.speed

    // ---- the styles -----------------------------------------------------------------------------------

    private fun drawStyle(canvas: Canvas, now: Long, p: Float) {
        val depth = settings.depthDp * density
        val half = settings.lengthDp * density / 2f
        val centre = placement.centre
        val press = ButtonBump.pulse(sinceS(now, pressAtMs))
        val t = sinceS(now, shownAtMs)
        when (settings.style) {
            ButtonBump.STYLE_CAPSULE -> drawCapsule(canvas, p, press, centre, half, depth)
            ButtonBump.STYLE_LINE -> drawLine(canvas, p, press, centre, half, depth)
            ButtonBump.STYLE_JELLY -> {
                val w = ButtonBump.wobble(sinceS(now, pressAtMs))
                val d = depth * p * (1f - 0.14f * w + 0.08f * press)
                val h = half * (0.9f + 0.1f * min(p, 1f)) * (1f + 0.1f * w)
                drawSolidBump(canvas, centre, h, d, settings.color)
                drawLevelTrack(canvas, centre, h, d, p)
            }
            ButtonBump.STYLE_NEON -> drawNeon(canvas, now, p, press, centre, half, depth)
            ButtonBump.STYLE_AURORA -> drawAurora(canvas, t, p, press, centre, half, depth)
            ButtonBump.STYLE_RIPPLE -> {
                drawRipples(canvas, now, centre, half, depth)
                drawLiquid(canvas, p, press, centre, half, depth)
            }
            ButtonBump.STYLE_SPARKLE -> {
                drawLiquid(canvas, p, press, centre, half, depth)
                drawTwinkles(canvas, t, p, centre, half, depth)
                drawSparks(canvas, now)
            }
            ButtonBump.STYLE_LAVA -> drawLava(canvas, t, p, press, centre, half, depth)
            ButtonBump.STYLE_GLASS -> drawGlass(canvas, now, p, press, centre, half, depth)
            ButtonBump.STYLE_SPECTRUM -> drawSpectrum(canvas, t, p, press, centre, half, depth)
            ButtonBump.STYLE_HEARTBEAT -> drawHeartbeat(canvas, now, p, centre, half, depth)
            ButtonBump.STYLE_ELASTIC -> drawElastic(canvas, p, press, centre, half, depth)
            else -> drawLiquid(canvas, p, press, centre, half, depth)
        }
    }

    /** The bump itself, the glass's black growing out of the edge, and the level in it. */
    private fun drawLiquid(canvas: Canvas, p: Float, press: Float, centre: Float, half: Float, depth: Float) {
        // A press pushes it a little deeper and nudges it towards the button.
        val d = depth * p * (1f + 0.1f * press)
        val h = half * (0.9f + 0.1f * min(p, 1f))
        val shifted = centre - pressDirection * half * 0.05f * press
        drawSolidBump(canvas, shifted, h, d, settings.color)
        drawLevelTrack(canvas, shifted, h, d, p)
    }

    private fun drawSolidBump(canvas: Canvas, centre: Float, half: Float, depth: Float, colour: Int) {
        if (depth <= 0.5f) return
        buildBump(bodyPath, centre, half, depth, spec.shoulder)
        bodyPaint.shader = null
        bodyPaint.color = colour
        canvas.drawPath(bodyPath, bodyPaint)
    }

    /**
     * The bump's outline: out from the glass at one end, along the profile, back to the glass at the
     * other, and closed round behind the glass so the edge itself never shows a seam.
     */
    private fun buildBump(
        path: Path,
        centre: Float,
        half: Float,
        depth: Float,
        shoulder: Float,
        warp: Float = 0f,
        second: Float = 0f,
        secondDepth: Float = 0f,
        closed: Boolean = true,
    ) {
        path.reset()
        val behind = -depth - 4f * density
        if (closed) path.moveTo(behind, centre - half * 1.4f)
        for (i in 0..SAMPLES) {
            val u = -1f + 2f * i / SAMPLES
            // A lean warps where along the edge each point lands, without moving the ends.
            val along = centre + half * (u + warp * (1f - u * u))
            var x = depth * ButtonBump.profile(u, shoulder)
            if (second != 0f || secondDepth != 0f) {
                // Two blobs that drift apart and merge: the larger of two offset profiles.
                val a = ButtonBump.profile((u - second) / (1f - abs(second)).coerceAtLeast(0.2f), shoulder)
                val b = ButtonBump.profile((u + second) / (1f - abs(second)).coerceAtLeast(0.2f), shoulder)
                x = max(depth * a, secondDepth * b)
            }
            if (i == 0 && !closed) path.moveTo(x, along) else path.lineTo(x, along)
        }
        if (closed) {
            path.lineTo(behind, centre + half * 1.4f)
            path.close()
        }
    }

    /**
     * The level inside the bump: a thin capsule along the flat of it, filled from the end nearer
     * the bottom of the phone, the way the buttons read — up is louder.
     */
    private fun drawLevelTrack(
        canvas: Canvas,
        centre: Float,
        half: Float,
        depth: Float,
        p: Float,
        fillShader: Shader? = null,
        fillColour: Int = levelInk(),
    ) {
        if (!settings.showLevel) return
        val alpha = ((p - 0.35f) / 0.5f).coerceIn(0f, 1f)
        if (alpha <= 0f || depth < 6f * density) return
        val width = (depth * 0.24f).coerceIn(3f * density, 7f * density)
        val trackHalf = half * (1f - spec.shoulder) * 0.86f
        if (trackHalf < width) return
        val x = depth * 0.5f
        rect.set(x - width / 2f, centre - trackHalf, x + width / 2f, centre + trackHalf)
        trackPaint.color = withAlpha(fillColour, 0.26f * alpha)
        canvas.drawRoundRect(rect, width / 2f, width / 2f, trackPaint)
        val top = centre + trackHalf - 2f * trackHalf * level.value.coerceIn(0f, 1f)
        if (centre + trackHalf - top > 0.5f) {
            rect.set(x - width / 2f, top, x + width / 2f, centre + trackHalf)
            fillPaint.shader = fillShader
            fillPaint.color = if (fillShader != null) withAlpha(Color.WHITE, alpha) else withAlpha(fillColour, alpha)
            canvas.drawRoundRect(rect, width / 2f, width / 2f, fillPaint)
            fillPaint.shader = null
        }
    }

    /** A capsule that slides out of the edge, clear of it, its level filling it from the bottom. */
    private fun drawCapsule(canvas: Canvas, p: Float, press: Float, centre: Float, half: Float, depth: Float) {
        val thick = depth.coerceAtLeast(10f * density)
        val gap = 6f * density
        val x0 = -thick - 4f * density + (thick + 4f * density + gap) * p
        val len = half * 0.85f * (1f + 0.06f * press)
        // A soft shadow, in layers, since a blurred one is not drawn by every renderer.
        for (i in 3 downTo 1) {
            val spread = i * 2f * density
            rect.set(x0 - spread, centre - len - spread, x0 + thick + spread, centre + len + spread)
            pillPaint.color = withAlpha(Color.BLACK, 0.06f * min(p, 1f))
            canvas.drawRoundRect(rect, thick / 2f + spread, thick / 2f + spread, pillPaint)
        }
        rect.set(x0, centre - len, x0 + thick, centre + len)
        bodyPaint.shader = null
        bodyPaint.color = settings.color
        canvas.drawRoundRect(rect, thick / 2f, thick / 2f, bodyPaint)
        if (!settings.showLevel) return
        val inset = (thick * 0.18f).coerceAtLeast(2.5f * density)
        val inner = thick - inset * 2f
        val top = centre + len - inset - (2f * (len - inset)) * level.value.coerceIn(0f, 1f)
        if (centre + len - inset - top > inner * 0.2f) {
            rect.set(x0 + inset, top, x0 + inset + inner, centre + len - inset)
            fillPaint.color = levelInk()
            canvas.drawRoundRect(rect, inner / 2f, inner / 2f, fillPaint)
        }
    }

    /** A shallow rise drawn as a lit line, the level running along the line from the bottom. */
    private fun drawLine(canvas: Canvas, p: Float, press: Float, centre: Float, half: Float, depth: Float) {
        val d = depth * 0.55f * p
        if (d <= 0.5f) return
        buildBump(bodyPath, centre, half, d, spec.shoulder)
        bodyPaint.shader = null
        bodyPaint.color = withAlpha(settings.color, 0.55f * min(p, 1f))
        canvas.drawPath(bodyPath, bodyPaint)
        buildBump(rimPath, centre, half, d, spec.shoulder, closed = false)
        strokePaint.shader = null
        strokePaint.strokeWidth = 2.4f * density
        strokePaint.color = withAlpha(settings.accent, 0.3f * min(p, 1f))
        canvas.drawPath(rimPath, strokePaint)
        if (!settings.showLevel) return
        // The lit part, from the bottom end, as far along the rim as the level goes.
        measure.setPath(rimPath, false)
        val length = measure.length
        segmentPath.reset()
        measure.getSegment(length * (1f - level.value.coerceIn(0f, 1f)), length, segmentPath, true)
        glowStroke(canvas, segmentPath, settings.accent, min(p, 1f) * (0.8f + 0.2f * press), 2.6f)
    }

    /** An outline lit like a neon tube: a flicker as it strikes, a hum while it stays. */
    private fun drawNeon(canvas: Canvas, now: Long, p: Float, press: Float, centre: Float, half: Float, depth: Float) {
        val d = depth * p * (1f + 0.06f * press)
        if (d <= 0.5f) return
        val h = half * (0.9f + 0.1f * min(p, 1f))
        buildBump(bodyPath, centre, h, d, spec.shoulder)
        bodyPaint.shader = null
        bodyPaint.color = withAlpha(settings.color, 0.82f * min(p, 1f))
        canvas.drawPath(bodyPath, bodyPaint)
        // Strikes like a tube: a few stutters as it comes on and at each press.
        val strike = sinceS(now, shownAtMs)
        val sincePress = sinceS(now, pressAtMs)
        val flicker = when {
            strike < 0.3f -> if (((strike * 40f).toInt() % 3) == 0) 0.35f else 1f
            sincePress < 0.12f -> if (((sincePress * 60f).toInt() % 2) == 0) 0.55f else 1f
            else -> 0.92f + 0.08f * sin(strike * 9f)
        }
        buildBump(rimPath, centre, h, d, spec.shoulder, closed = false)
        glowStroke(canvas, rimPath, settings.accent, min(p, 1f) * flicker, 2.2f)
        if (settings.showLevel) {
            val trackHalf = h * (1f - spec.shoulder) * 0.86f
            val x = d * 0.5f
            val top = centre + trackHalf - 2f * trackHalf * level.value.coerceIn(0f, 1f)
            segmentPath.reset()
            segmentPath.moveTo(x, centre + trackHalf)
            segmentPath.lineTo(x, top)
            glowStroke(canvas, segmentPath, settings.accent, ((p - 0.35f) / 0.5f).coerceIn(0f, 1f) * flicker, 3f)
        }
    }

    /** The bump filled with northern lights drifting slowly along it. */
    private fun drawAurora(canvas: Canvas, t: Float, p: Float, press: Float, centre: Float, half: Float, depth: Float) {
        val d = depth * p * (1f + 0.1f * press)
        if (d <= 0.5f) return
        val h = half * (0.9f + 0.1f * min(p, 1f))
        buildBump(bodyPath, centre, h, d, spec.shoulder)
        val shader = auroraShader ?: LinearGradient(
            0f, 0f, 0f, half * 2.4f,
            intArrayOf(0xFF00E5A8.toInt(), 0xFF1F7BFF.toInt(), 0xFFB04BFF.toInt(), 0xFF00C2FF.toInt(), 0xFF00E5A8.toInt()),
            null, Shader.TileMode.MIRROR,
        ).also { auroraShader = it }
        shaderMatrix.setTranslate(0f, centre - half + t * 60f * density)
        shader.setLocalMatrix(shaderMatrix)
        bodyPaint.shader = shader
        bodyPaint.alpha = (255 * min(p, 1f)).roundToInt()
        canvas.drawPath(bodyPath, bodyPaint)
        bodyPaint.shader = null
        bodyPaint.alpha = 255
        // Deeper at the glass, so it reads as light coming out of the edge.
        buildBump(scratchPath, centre, h * 0.98f, d * 0.45f, spec.shoulder)
        bodyPaint.color = withAlpha(settings.color, 0.45f * min(p, 1f))
        canvas.drawPath(scratchPath, bodyPaint)
        buildBump(rimPath, centre, h, d, spec.shoulder, closed = false)
        strokePaint.shader = null
        strokePaint.strokeWidth = 1.4f * density
        strokePaint.color = withAlpha(Color.WHITE, 0.4f * min(p, 1f))
        canvas.drawPath(rimPath, strokePaint)
        drawLevelTrack(canvas, centre, h, d, p, fillColour = Color.WHITE)
    }

    /** Rings going out from the button into the screen, one for each press. */
    private fun drawRipples(canvas: Canvas, now: Long, centre: Float, half: Float, depth: Float) {
        val reach = depth * spec.reach * 0.92f
        for (ring in rings) {
            val age = ((now - ring.bornMs) / RING_LIFE_MS.toFloat()).coerceIn(0f, 1f)
            val eased = 1f - (1f - age) * (1f - age)
            val d = depth + (reach - depth) * eased
            val h = half * (1f + 0.5f * eased)
            buildBump(rimPath, centre, h, d, spec.shoulder, closed = false)
            strokePaint.shader = null
            strokePaint.strokeWidth = (3.2f - 2.2f * age) * density
            strokePaint.color = withAlpha(settings.accent, 0.55f * (1f - age) * (1f - age))
            canvas.drawPath(rimPath, strokePaint)
        }
    }

    private fun spawnSparks(now: Long, count: Int) {
        val depth = settings.depthDp * density
        val half = settings.lengthDp * density / 2f
        repeat(count) {
            val u = random.nextFloat() * 1.6f - 0.8f
            val speed = (120f + random.nextFloat() * 260f) * density
            val angle = (random.nextFloat() - 0.5f) * 1.6f
            sparks += Spark(
                x = depth * ButtonBump.profile(u, spec.shoulder),
                y = placement.centre + half * u,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - pressDirection * 40f * density,
                bornMs = now,
                lifeMs = 500L + random.nextInt(450),
                size = (2f + random.nextFloat() * 3.5f) * density,
                white = random.nextInt(3) == 0,
            )
        }
        while (sparks.size > 64) sparks.removeAt(0)
    }

    /** Moves the sparks on; whether any are left. */
    private fun updateSparks(now: Long, dt: Float): Boolean {
        if (sparks.isEmpty()) return false
        val drag = 1f - 2.6f * dt
        val it = sparks.iterator()
        while (it.hasNext()) {
            val s = it.next()
            if (now - s.bornMs > s.lifeMs) {
                it.remove()
                continue
            }
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.vx *= drag
            s.vy *= drag
        }
        return sparks.isNotEmpty()
    }

    private fun drawSparks(canvas: Canvas, now: Long) {
        for (s in sparks) {
            val life = ((now - s.bornMs).toFloat() / s.lifeMs).coerceIn(0f, 1f)
            val alpha = (1f - life) * (1f - life)
            star(scratchPath, s.x, s.y, s.size * (1f - 0.4f * life))
            fillPaint.shader = null
            fillPaint.color = withAlpha(if (s.white) Color.WHITE else settings.accent, alpha)
            canvas.drawPath(scratchPath, fillPaint)
        }
    }

    /** A few stars on the rim, each on its own beat, for as long as the bump is out. */
    private fun drawTwinkles(canvas: Canvas, t: Float, p: Float, centre: Float, half: Float, depth: Float) {
        val d = depth * p
        if (d <= 1f) return
        for (i in 0 until 5) {
            val u = -0.7f + i * 0.35f
            val beat = 0.5f + 0.5f * sin(t * (3.1f + i * 0.7f) + i * 1.9f)
            val x = d * ButtonBump.profile(u, spec.shoulder) + 2f * density
            star(scratchPath, x, centre + half * u, (1.6f + 2.4f * beat) * density)
            fillPaint.shader = null
            fillPaint.color = withAlpha(settings.accent, 0.85f * beat * min(p, 1f))
            canvas.drawPath(scratchPath, fillPaint)
        }
    }

    /** Molten: two warm blobs inside the bump that drift apart and run back together. */
    private fun drawLava(canvas: Canvas, t: Float, p: Float, press: Float, centre: Float, half: Float, depth: Float) {
        val d = depth * p * (1f + 0.12f * press)
        if (d <= 0.5f) return
        val h = half * (0.9f + 0.1f * min(p, 1f))
        val drift = 0.16f * sin(t * 1.3f)
        val d1 = d * (1f + 0.08f * sin(t * 2.1f))
        val d2 = d * (1f + 0.08f * cos(t * 1.7f))
        buildBump(bodyPath, centre, h, d1, spec.shoulder, second = drift, secondDepth = d2)
        val maxDepth = max(d1, d2)
        val shader = lavaShader ?: LinearGradient(
            0f, 0f, depth * 1.2f, 0f,
            intArrayOf(0xFF5A0700.toInt(), 0xFFD8320B.toInt(), 0xFFFF7A1A.toInt(), 0xFFFFC94A.toInt()),
            floatArrayOf(0f, 0.45f, 0.8f, 1f), Shader.TileMode.CLAMP,
        ).also { lavaShader = it }
        shaderMatrix.setScale(maxDepth / (depth * 1.2f), 1f)
        shader.setLocalMatrix(shaderMatrix)
        // A glow round it first, the colour of the hottest part.
        strokePaint.shader = null
        strokePaint.strokeWidth = 7f * density
        strokePaint.color = withAlpha(0xFFFF6A1A.toInt(), 0.18f * min(p, 1f))
        canvas.drawPath(bodyPath, strokePaint)
        bodyPaint.shader = shader
        bodyPaint.alpha = (255 * min(p, 1f)).roundToInt()
        canvas.drawPath(bodyPath, bodyPaint)
        bodyPaint.shader = null
        bodyPaint.alpha = 255
        drawLevelTrack(canvas, centre, h, maxDepth, p, fillColour = 0xFFFFF1C2.toInt())
    }

    /** Frosted glass with a bright rim, and a highlight that runs along the rim at each press. */
    private fun drawGlass(canvas: Canvas, now: Long, p: Float, press: Float, centre: Float, half: Float, depth: Float) {
        val d = depth * p * (1f + 0.08f * press)
        if (d <= 0.5f) return
        val h = half * (0.9f + 0.1f * min(p, 1f))
        buildBump(bodyPath, centre, h, d, spec.shoulder)
        val shader = glassShader ?: LinearGradient(
            0f, 0f, depth, 0f,
            intArrayOf(withAlpha(Color.WHITE, 0.08f), withAlpha(Color.WHITE, 0.34f)),
            null, Shader.TileMode.CLAMP,
        ).also { glassShader = it }
        shaderMatrix.setScale(d / depth, 1f)
        shader.setLocalMatrix(shaderMatrix)
        // A dark wash under the frost, tinted by the body colour, so white content does not wash it out.
        bodyPaint.shader = null
        bodyPaint.color = withAlpha(settings.color, 0.38f * min(p, 1f))
        canvas.drawPath(bodyPath, bodyPaint)
        bodyPaint.shader = shader
        bodyPaint.alpha = (255 * min(p, 1f)).roundToInt()
        canvas.drawPath(bodyPath, bodyPaint)
        bodyPaint.shader = null
        bodyPaint.alpha = 255
        buildBump(rimPath, centre, h, d, spec.shoulder, closed = false)
        strokePaint.shader = null
        strokePaint.strokeWidth = 1.3f * density
        strokePaint.color = withAlpha(Color.WHITE, 0.6f * min(p, 1f))
        canvas.drawPath(rimPath, strokePaint)
        // The highlight: a bright stretch of the rim, sweeping from the end the press came from.
        val since = sinceS(now, pressAtMs)
        if (since in 0f..0.7f) {
            measure.setPath(rimPath, false)
            val length = measure.length
            val k = since / 0.7f
            val at = if (pressDirection > 0) length * (1f - k) else length * k
            segmentPath.reset()
            measure.getSegment((at - length * 0.12f).coerceAtLeast(0f), (at + length * 0.12f).coerceAtMost(length), segmentPath, true)
            glowStroke(canvas, segmentPath, Color.WHITE, (1f - k) * min(p, 1f), 2.4f)
        }
        drawLevelTrack(canvas, centre, h, d, p, fillColour = Color.WHITE)
    }

    /** A black bump with the spectrum turning round its rim, and the level in rainbow. */
    private fun drawSpectrum(canvas: Canvas, t: Float, p: Float, press: Float, centre: Float, half: Float, depth: Float) {
        val d = depth * p * (1f + 0.1f * press)
        if (d <= 0.5f) return
        val h = half * (0.9f + 0.1f * min(p, 1f))
        drawSolidBump(canvas, centre, h, d, settings.color)
        val shader = spectrumShader ?: LinearGradient(
            0f, 0f, 0f, half * 2f,
            intArrayOf(
                0xFFFF3B30.toInt(), 0xFFFF9500.toInt(), 0xFFFFCC00.toInt(), 0xFF34C759.toInt(),
                0xFF5AC8FA.toInt(), 0xFF007AFF.toInt(), 0xFFAF52DE.toInt(), 0xFFFF3B30.toInt(),
            ),
            null, Shader.TileMode.REPEAT,
        ).also { spectrumShader = it }
        shaderMatrix.setTranslate(0f, centre - half - t * 90f * density)
        shader.setLocalMatrix(shaderMatrix)
        buildBump(rimPath, centre, h, d, spec.shoulder, closed = false)
        for ((passWidth, passAlpha) in GLOW) {
            strokePaint.shader = shader
            strokePaint.strokeWidth = passWidth * 2.2f * density
            strokePaint.alpha = (255 * passAlpha * min(p, 1f)).roundToInt()
            canvas.drawPath(rimPath, strokePaint)
        }
        strokePaint.shader = null
        strokePaint.alpha = 255
        drawLevelTrack(canvas, centre, h, d, p, fillShader = shader)
    }

    /** A beating heart: two quick throbs at every press, a glow, and a trace across it. */
    private fun drawHeartbeat(canvas: Canvas, now: Long, p: Float, centre: Float, half: Float, depth: Float) {
        val since = sinceS(now, pressAtMs)
        val beat = ButtonBump.heartbeat(since)
        val d = depth * p * (1f + 0.2f * beat)
        if (d <= 0.5f) return
        val h = half * (0.9f + 0.1f * min(p, 1f)) * (1f + 0.04f * beat)
        buildBump(rimPath, centre, h, d, spec.shoulder, closed = false)
        glowStroke(canvas, rimPath, settings.accent, min(p, 1f) * (0.35f + 0.65f * beat), 2.2f)
        drawSolidBump(canvas, centre, h, d, settings.color)
        // The trace: a flat line with a beat in it, drawn across the flat of the bump as it beats.
        if (since in 0f..0.9f && d > 8f * density) {
            val x = d * 0.5f
            val run = h * (1f - spec.shoulder) * 0.9f
            val amp = d * 0.32f
            segmentPath.reset()
            segmentPath.moveTo(x, centre + run)
            segmentPath.lineTo(x, centre + run * 0.3f)
            segmentPath.lineTo(x + amp * 0.35f, centre + run * 0.18f)
            segmentPath.lineTo(x - amp, centre + run * 0.02f)
            segmentPath.lineTo(x + amp, centre - run * 0.12f)
            segmentPath.lineTo(x, centre - run * 0.26f)
            segmentPath.lineTo(x, centre - run)
            measure.setPath(segmentPath, false)
            val length = measure.length
            val k = (since / 0.6f).coerceIn(0f, 1f)
            scratchPath.reset()
            measure.getSegment(0f, length * k, scratchPath, true)
            glowStroke(canvas, scratchPath, settings.accent, (1f - (since - 0.6f).coerceAtLeast(0f) / 0.3f) * min(p, 1f), 1.8f)
        } else {
            drawLevelTrack(canvas, centre, h, d, p)
        }
    }

    /** Stretched towards the button that was pressed, springing back past the middle and settling. */
    private fun drawElastic(canvas: Canvas, p: Float, press: Float, centre: Float, half: Float, depth: Float) {
        val leanBy = lean.value.coerceIn(-1.4f, 1.4f) * 0.12f
        val d = depth * p * (1f + 0.08f * press - 0.1f * abs(leanBy))
        if (d <= 0.5f) return
        val h = half * (0.9f + 0.1f * min(p, 1f)) * (1f + 0.6f * abs(leanBy))
        buildBump(bodyPath, centre, h, d, spec.shoulder, warp = leanBy * 2.2f)
        bodyPaint.shader = null
        bodyPaint.color = settings.color
        canvas.drawPath(bodyPath, bodyPaint)
        drawLevelTrack(canvas, centre + leanBy * h, h, d, p)
    }

    // ---- small pieces ----------------------------------------------------------------------------------

    /** A stroke with a glow: wide faint passes under a bright core, which every renderer draws. */
    private fun glowStroke(canvas: Canvas, path: Path, colour: Int, alpha: Float, coreDp: Float) {
        if (alpha <= 0.01f) return
        strokePaint.shader = null
        for ((passWidth, passAlpha) in GLOW) {
            strokePaint.strokeWidth = passWidth * coreDp * density
            strokePaint.color = withAlpha(colour, passAlpha * alpha.coerceIn(0f, 1f))
            canvas.drawPath(path, strokePaint)
        }
    }

    /** A four-pointed star, for the sparks and the twinkles. */
    private fun star(path: Path, x: Float, y: Float, r: Float) {
        val i = r * 0.28f
        path.reset()
        path.moveTo(x, y - r)
        path.quadTo(x + i, y - i, x + r, y)
        path.quadTo(x + i, y + i, x, y + r)
        path.quadTo(x - i, y + i, x - r, y)
        path.quadTo(x - i, y - i, x, y - r)
        path.close()
    }

    /** The level as a number beside the bump, upright on the screen whichever edge the bump is on. */
    private fun drawPercent(canvas: Canvas, p: Float) {
        val depth = settings.depthDp * density
        point[0] = depth * p + 22f * density
        point[1] = placement.centre
        toWindow.mapPoints(point)
        val percent = (level.value.coerceIn(0f, 1f) * 100f).roundToInt()
        textPaint.textSize = 13f * density
        textPaint.color = Color.WHITE
        val text = "$percent%"
        val w = textPaint.measureText(text) + 14f * density
        val h = 22f * density
        val x = point[0].coerceIn(w / 2f, width - w / 2f)
        val y = point[1].coerceIn(h / 2f, height - h / 2f)
        val alpha = ((p - 0.25f) / 0.5f).coerceIn(0f, 1f)
        rect.set(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f)
        pillPaint.color = withAlpha(0xFF101012.toInt(), 0.82f * alpha)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, pillPaint)
        textPaint.alpha = (255 * alpha).roundToInt()
        canvas.drawText(text, x, y - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
    }

    /**
     * The level's colour: the accent, unless it would vanish into a pale body — white on white — in
     * which case near-black, the way the Quick panel keeps its ink readable.
     */
    private fun levelInk(): Int {
        val pale = luminance(settings.color) > 0.6f
        return if (pale && luminance(settings.accent) > 0.6f) 0xFF15161A.toInt() else settings.accent
    }

    private fun luminance(colour: Int): Float =
        (0.2126f * Color.red(colour) + 0.7152f * Color.green(colour) + 0.0722f * Color.blue(colour)) / 255f

    private fun withAlpha(colour: Int, alpha: Float): Int =
        (colour and 0x00FFFFFF) or ((Color.alpha(colour) * alpha.coerceIn(0f, 1f)).roundToInt() shl 24)

    private companion object {
        /** Points along the bump's profile: enough that its curve never shows a corner. */
        const val SAMPLES = 72

        /** How long a ripple takes to go out and fade. */
        const val RING_LIFE_MS = 1100L

        /** Width multiples and alphas of a glow's passes, faintest and widest first. */
        val GLOW = listOf(5.5f to 0.08f, 3.4f to 0.14f, 2f to 0.3f, 1f to 1f)

        val LOOPING_STYLES = setOf(
            ButtonBump.STYLE_AURORA, ButtonBump.STYLE_SPECTRUM, ButtonBump.STYLE_LAVA,
            ButtonBump.STYLE_NEON, ButtonBump.STYLE_SPARKLE,
        )
    }
}
