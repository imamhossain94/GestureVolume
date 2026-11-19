package com.newagedevs.gesturevolume.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.newagedevs.gesturevolume.data.local.ButtonBumpStore
import com.newagedevs.gesturevolume.utils.ButtonBump
import com.newagedevs.gesturevolume.utils.WaterSurface
import kotlin.math.abs
import kotlin.math.ceil
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

    /** Told when the effect asks for a sound of its own: see [BumpScene.cue]. */
    var cueListener: ((String) -> Unit)? = null

    /** Whether a volume key is held down now: see [BumpScene.keyDown]. */
    private var keyDown = false

    /** A volume key went down, or came up. */
    fun setKeyDown(down: Boolean) {
        keyDown = down
        scene.keyDown = down
    }

    /** Hands on the sounds the effect asked for. */
    private fun takeCues() {
        if (scene.cues.isEmpty()) return
        val listener = cueListener
        for (c in scene.cues) listener?.invoke(c)
        scene.cues.clear()
    }

    private val density = context.resources.displayMetrics.density

    private var settings = ButtonBumpStore.Settings()
    private var spec = ButtonBump.spec(settings.style)

    /** How the bump's ends sweep into the glass: the user's end sweep when they have set one, the style's otherwise. */
    private val shoulder: Float
        get() = if (settings.sweep >= 0f) settings.sweep.coerceIn(0.05f, 1f) else spec.shoulder

    /**
     * Where the placement's window starts inside this view. The view is the whole screen on the glass —
     * so what an animation throws out is never cut off at the edge of a box — and the bump is drawn in
     * its place inside it.
     */
    private var originX = 0f
    private var originY = 0f
    private val toUpright = Matrix()
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

    /** A ring going out from a press, as far as that press's level sends it: see [powerAt]. */
    private class Ring(val bornMs: Long, val power: Float)
    private class Spark(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val bornMs: Long, val lifeMs: Long, val size: Float, val white: Boolean,
    )

    private val rings = ArrayList<Ring>()
    private val sparks = ArrayList<Spark>()
    private val random = java.util.Random()

    // ---- the water style's pool ------------------------------------------------------------------

    /** The water beside the buttons, while the style is Water. See [WaterSurface]. */
    private var water: WaterSurface? = null
    private var waterBitmap: Bitmap? = null
    private var waterPixels = IntArray(0)

    /** A cell's size in the window's pixels, inward and along the edge. */
    private var waterCellX = 1f
    private var waterCellY = 1f

    /**
     * Where the pool is, in upright terms: from the glass at x = 0 across [waterWidth], and from
     * [waterTop] along the edge for [waterHeight]. The whole screen, once the view knows it is the
     * whole screen; the bump's own window until then.
     */
    private var waterTop = 0f
    private var waterWidth = 1f
    private var waterHeight = 1f

    /** What the water keeps of its movement each step, worked out from [WATER_KEEP_PER_S] for the steps it takes. */
    private var waterDamping = WaterSurface.DEFAULT_DAMPING

    /** How many steps of the water make a second, for the ripples to run at [WATER_SPEED_DP] a second. */
    private var waterStepsPerS = 120f

    /** Time the water is owed, carried from frame to frame so it runs at its own pace whatever the frame rate. */
    private var waterClock = 0f

    /** Presses since the last step, each dropping a ripple at the next. */
    private var waterKick = 0f

    /** Whether anything is still moving on the water: it is drawn, and the frames go on, until not. */
    private var waterAlive = false

    /** Whether the surface has moved since it was last lit. */
    private var waterDirty = true

    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val waterRect = RectF()
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // ---- the showpiece styles: petals, sparks, fireworks and the rest ---------------------------

    /** The effect the style is drawn with, for the styles in [BumpEffects]; null for the rest. */
    private var effect: BumpEffect? = null
    private var effectStyle: String? = null
    private val scene = BumpScene()

    /** Whether anything the effect threw out is still in flight: drawn, and the frames go on, until not. */
    private var effectAlive = false

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
        if (settings.style == ButtonBump.STYLE_WATER) ensureWater(placement) else releaseWater()
        if (effectStyle != settings.style) {
            effect = BumpEffects.create(settings.style, random)
            effectStyle = settings.style
            effectAlive = false
        }
        // The shaders are sized to the bump, so a new length or colour makes new ones.
        auroraShader = null
        spectrumShader = null
        lavaShader = null
        glassShader = null
        updateMatrix()
        invalidate()
    }

    /**
     * A pool the size of the whole screen, in cells a few pixels across: fine enough that the light
     * on it is smooth once the bitmap is stretched over the screen, coarse enough to step and light
     * every frame without being felt. The screen's edges are the pool's walls. Kept as it is when the
     * screen has not changed size, so a press while the water is moving carries on with the same water.
     */
    private fun ensureWater(p: ButtonBump.Placement) {
        if (width > 0 && height > 0 || screenW > 0 && screenH > 0) {
            uprightScreen(waterBounds)
        } else {
            waterBounds.set(0f, 0f, p.reach.toFloat(), p.span.toFloat())
        }
        val across = max(waterBounds.right, 1f)
        val along = max(waterBounds.height(), 1f)
        waterTop = waterBounds.top
        waterWidth = across
        waterHeight = along
        var cell = max(WATER_CELL_DP * density, WATER_MIN_CELL_PX)
        var cols = ceil(across / cell).toInt().coerceAtLeast(3)
        var rows = ceil(along / cell).toInt().coerceAtLeast(3)
        while (cols * rows > WATER_MAX_CELLS) {
            cell *= 1.1f
            cols = ceil(across / cell).toInt().coerceAtLeast(3)
            rows = ceil(along / cell).toInt().coerceAtLeast(3)
        }
        waterCellX = across / cols.toFloat()
        waterCellY = along / rows.toFloat()
        // The ripples' speed on the glass, whatever the cells' size: the grid moves a wave at most
        // the square root of its speed squared, in cells, each step.
        val cellDp = (waterCellX + waterCellY) / 2f / density
        waterStepsPerS = (WATER_SPEED_DP / (kotlin.math.sqrt(WaterSurface.DEFAULT_SPEED2) * cellDp))
            .coerceIn(30f, 360f)
        // How much the water keeps each step, for it to keep [WATER_KEEP_PER_S] of its movement a
        // second however many steps a second it takes: so a ripple runs the whole screen.
        waterDamping = Math.pow(WATER_KEEP_PER_S.toDouble(), 1.0 / waterStepsPerS).toFloat()
        val current = water
        if (current != null && current.cols == cols && current.rows == rows) return
        water = WaterSurface(cols, rows, walls = true)
        waterBitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        waterPixels = IntArray(cols * rows)
        waterClock = 0f
        waterDirty = true
    }

    private val waterBounds = RectF()

    private fun releaseWater() {
        water = null
        waterBitmap = null
        waterPixels = IntArray(0)
        waterAlive = false
        waterKick = 0f
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
        // Which way is down on the screen, in upright terms: what the effects fall and rise by.
        val inverse = toUpright
        if (toWindow.invert(inverse)) {
            val down = floatArrayOf(0f, 1f)
            inverse.mapVectors(down)
            val length = kotlin.math.hypot(down[0], down[1]).takeIf { it > 0f } ?: 1f
            scene.gravityX = down[0] / length
            scene.gravityY = down[1] / length
        }
    }

    /** Freezes the view on one frame of the bump, out and part full: for a tile in the style gallery. */
    fun setStill(level: Float = 0.62f) {
        still = true
        running = false
        presence.snapTo(1f)
        presenceTarget = 1f
        this.level.snapTo(level)
        levelTarget = level
        // The water is drawn for a still as clean rings: see drawWaterStill.
        water?.clear()
        waterAlive = false
        effect?.let { fx ->
            syncScene()
            scene.target = level
            fx.prime(scene)
            effectAlive = true
        }
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
                rings += Ring(now, powerAt(levelTarget))
                while (rings.size > 6) rings.removeAt(0)
            }
            // A drop into the pool: a held key keeps dropping them, and their rings cross.
            ButtonBump.STYLE_WATER -> waterKick = min(waterKick + (if (atLimit) 0.55f else 1f) * powerAt(levelTarget), 2.6f)
            ButtonBump.STYLE_SPARKLE -> spawnSparks(now, ((if (atLimit) 8 else 16) * powerAt(levelTarget)).roundToInt().coerceAtLeast(3))
        }
        effect?.let { fx ->
            syncScene()
            // The level this press landed on, which the bump's own level is only now setting off for.
            scene.target = levelTarget
            fx.press(scene, atLimit)
            takeCues()
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

    /** Draws the placement's window at [x], [y] in this view. See [originX]. */
    /**
     * The size of the window this view fills, from whoever made it: known before the window has been
     * laid out, which the first press of all comes before. Without it that press sized everything
     * to the bump's own small strip instead of the screen.
     */
    fun setScreenSize(w: Int, h: Int) {
        if (w == screenW && h == screenH) return
        screenW = w
        screenH = h
        if (settings.style == ButtonBump.STYLE_WATER) ensureWater(placement)
    }

    private var screenW = 0
    private var screenH = 0

    /** How big this view is: laid out, or as its window will be, or as its bump's own window. */
    private fun viewWidth(): Float = when {
        width > 0 -> width.toFloat()
        screenW > 0 -> screenW.toFloat()
        else -> placement.width.toFloat()
    }

    private fun viewHeight(): Float = when {
        height > 0 -> height.toFloat()
        screenH > 0 -> screenH.toFloat()
        else -> placement.height.toFloat()
    }

    fun setOrigin(x: Int, y: Int) {
        originX = x.toFloat()
        originY = y.toFloat()
        // Where the screen is, from the button, has moved: the pool is laid over it again.
        if (settings.style == ButtonBump.STYLE_WATER) ensureWater(placement)
        readCutout()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (settings.style == ButtonBump.STYLE_WATER) ensureWater(placement)
        readCutout()
    }

    /** The camera hole's middle in the window's own pixels, or NaN when there is none known. */
    private var cutoutWindowX = Float.NaN
    private var cutoutWindowY = Float.NaN

    /** Reads where the camera hole is, for the effects that aim at it: see [BumpScene.cutoutX]. */
    private fun readCutout() {
        cutoutWindowX = Float.NaN
        cutoutWindowY = Float.NaN
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return
        val rects = rootWindowInsets?.displayCutout?.boundingRects ?: return
        // The one along the top of the screen as it stands, nearest its middle: the camera hole.
        val hole = rects.filter { it.top <= 0 || it.centerY() < height * 0.2f }
            .minByOrNull { kotlin.math.abs(it.exactCenterX() - width / 2f) } ?: return
        cutoutWindowX = hole.exactCenterX()
        cutoutWindowY = hole.exactCenterY()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Where the camera hole is, now there is a window to ask.
        readCutout()
        // Taken off the glass and put back — raised above a panel — it carries on where it was.
        if (!still && (isOut() || waterAlive || effectAlive)) start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
        removeCallbacks(wake)
    }

    /** Everything stopped and cleared, for good: the window is going. */
    fun release() {
        running = false
        removeCallbacks(wake)
        water?.clear()
        waterAlive = false
        waterKick = 0f
        effect?.clear()
        effectAlive = false
    }

    // ---- the frame ---------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        if (!still) advance(now)

        val p = presence.value
        if (p > 0.001f || rings.isNotEmpty() || sparks.isNotEmpty() || waterAlive || effectAlive) {
            canvas.save()
            canvas.translate(originX, originY)
            canvas.save()
            canvas.concat(toWindow)
            drawStyle(canvas, now, p.coerceAtLeast(0f))
            canvas.restore()
            if (settings.showPercent && p > 0.25f) drawPercent(canvas, p)
            canvas.restore()
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
        stepWater(dt)
        effectAlive = effect?.let { fx ->
            syncScene()
            fx.step(scene, dt).also { takeCues() }
        } ?: false

        val settled = presence.isSettled(presenceTarget) && level.isSettled(levelTarget) &&
            lean.isSettled(0f, 0.01f)
        val pulsing = now - pressAtMs < (ButtonBump.PRESS_S * 1000f / settings.speed.coerceAtLeast(0.1f)).toLong() + 600L
        // The loops — aurora drifting, spectrum turning — keep going for as long as the bump is out.
        val looping = presenceTarget > 0f && (settings.style in LOOPING_STYLES || effect?.loops == true)
        running = !settled || pulsing || looping || rings.isNotEmpty() || sparksAlive || waterAlive || effectAlive
        // Out and still: no frames until it is time to go back into the glass.
        removeCallbacks(wake)
        if (!running && presenceTarget > 0f && hideAtMs > now) postDelayed(wake, hideAtMs - now)
    }

    /**
     * Runs the water on by [dt]: the drop held at the height the bump has reached — so its rising,
     * its wobble and its sinking all push the water about — a ripple dropped for each press since
     * the last step, and the steps the time calls for, at the water's own pace.
     */
    private fun stepWater(dt: Float) {
        val pool = water ?: run {
            waterAlive = false
            return
        }
        waterClock += dt
        val stepS = 1f / waterStepsPerS
        val (row, half, deep) = waterSource()
        var steps = 0
        while (waterClock >= stepS && steps < WATER_MAX_STEPS) {
            // Let go as the drop sinks: water held flat where a drop has been would stand in the
            // ripples' way like a post.
            val drop = presence.value.coerceAtLeast(0f)
            pool.hold(row, half, deep, WATER_DROP * drop, WATER_HOLD * ButtonBump.smootherstep(drop * 3f))
            if (waterKick > 0f) {
                pool.impulse(row, half * WATER_KICK_SPREAD, deep * WATER_KICK_DEPTH, -WATER_KICK * waterKick)
                waterKick = 0f
            }
            pool.step(damping = waterDamping)
            waterClock -= stepS
            steps++
        }
        // Behind by more than a frame's worth, after a stall: let it go rather than race to catch up.
        if (steps == WATER_MAX_STEPS) waterClock = 0f
        if (steps > 0) waterDirty = true
        waterAlive = presence.value > 0.01f || waterKick > 0f || pool.energy() > WATER_REST
        if (!waterAlive) {
            pool.clear()
            waterClock = 0f
        }
    }

    /** Where the drop sits on the pool, in cells: its middle along the edge, its half-length, its depth. */
    private fun waterSource(): Triple<Float, Float, Float> {
        val row = (placement.centre - waterTop) / waterCellY
        val half = settings.lengthDp * density / 2f / waterCellY
        val deep = max(settings.depthDp, ButtonBump.EFFECT_DEPTH_DP) * density / waterCellX
        return Triple(row, half, deep)
    }

    /** Wakes a bump that has been sitting still, when its time on the glass is up. */
    private val wake = Runnable { start() }

    private fun sinceS(now: Long, then: Long): Float = (now - then) / 1000f * settings.speed

    // ---- the styles -----------------------------------------------------------------------------------

    /** How big an effect is at [level]: the scene's own rule, see [BumpScene.power]. */
    private fun powerAt(level: Float): Float =
        BumpScene.POWER_MIN + (BumpScene.POWER_MAX - BumpScene.POWER_MIN) * level.coerceIn(0f, 1f)

    private fun drawStyle(canvas: Canvas, now: Long, p: Float) {
        // The bump itself a little deeper the louder it is, as everything it throws is bigger.
        val depth = settings.depthDp * density * (0.7f + 0.3f * powerAt(level.value))
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
            ButtonBump.STYLE_WATER -> if (still) {
                drawWaterStill(canvas, centre, depth)
            } else {
                drawWater(canvas)
                drawDrop(canvas, p, press, centre, half, depth)
            }
            else -> if (effect != null) {
                drawEffect(canvas, p, press, centre, half, depth)
            } else {
                drawLiquid(canvas, p, press, centre, half, depth)
            }
        }
    }

    /** The scene as it stands, for the effect: the bump at rest and as it is, the window, the colours. */
    private fun syncScene() {
        scene.density = density
        scene.reach = placement.reach.toFloat()
        scene.span = placement.span.toFloat()
        scene.restHalf = settings.lengthDp * density / 2f
        scene.restDepth = settings.depthDp * density
        val out = presence.value.coerceAtLeast(0f)
        scene.presence = out
        scene.centre = placement.centre
        scene.depth = scene.restDepth * out
        scene.half = scene.restHalf * (0.9f + 0.1f * min(out, 1f))
        scene.level = level.value
        scene.color = settings.color
        scene.accent = settings.accent
        scene.showLevel = settings.showLevel
        scene.towards = -pressDirection
        scene.unit = ButtonBump.EFFECT_DEPTH_DP * density
        // The whole of this view — the screen, on the glass — in upright terms: where things may fly.
        uprightScreen(screenBounds)
        scene.screenLeft = screenBounds.left
        scene.screenRight = screenBounds.right
        scene.screenTop = screenBounds.top
        scene.screenBottom = screenBounds.bottom
        // And the way there from the window's own pixels, for an effect that aims at a place on the
        // screen as the user sees it — its top, or the camera hole.
        scene.fromWindow.set(toUpright)
        scene.fromWindow.preTranslate(-originX, -originY)
        scene.windowWidth = viewWidth()
        scene.windowHeight = viewHeight()
        if (cutoutWindowX.isNaN()) {
            scene.cutoutX = Float.NaN
            scene.cutoutY = Float.NaN
        } else {
            point[0] = cutoutWindowX
            point[1] = cutoutWindowY
            scene.fromWindow.mapPoints(point)
            scene.cutoutX = point[0]
            scene.cutoutY = point[1]
        }
    }

    private val corners = FloatArray(8)
    private val screenBounds = RectF()

    /** The whole of this view in upright terms, into [out]: the screen, when the view is the screen. */
    private fun uprightScreen(out: RectF) {
        val w = viewWidth()
        val h = viewHeight()
        corners[0] = -originX; corners[1] = -originY
        corners[2] = w - originX; corners[3] = -originY
        corners[4] = -originX; corners[5] = h - originY
        corners[6] = w - originX; corners[7] = h - originY
        toUpright.mapPoints(corners)
        out.set(
            minOf(minOf(corners[0], corners[2]), minOf(corners[4], corners[6])),
            minOf(minOf(corners[1], corners[3]), minOf(corners[5], corners[7])),
            maxOf(maxOf(corners[0], corners[2]), maxOf(corners[4], corners[6])),
            maxOf(maxOf(corners[1], corners[3]), maxOf(corners[5], corners[7])),
        )
    }

    /**
     * One of the showpiece styles: the effect behind, then the bump with its level in it — unless the
     * effect is the bump, as the sun is — then the effect in front.
     */
    private fun drawEffect(canvas: Canvas, p: Float, press: Float, centre: Float, half: Float, depth: Float) {
        val fx = effect ?: return
        syncScene()
        val d = depth * p * (1f + 0.1f * press)
        val h = half * (0.9f + 0.1f * min(p, 1f))
        val shifted = centre - pressDirection * half * 0.05f * press
        scene.depth = d
        scene.half = h
        scene.centre = shifted
        if (d > 0.5f) buildBump(scene.rim, shifted, h, d, shoulder, closed = false) else scene.rim.reset()
        fx.drawBehind(canvas, scene)
        if (fx.drawsBump && d > 0.5f) {
            drawSolidBump(canvas, shifted, h, d, settings.color)
            drawLevelTrack(canvas, shifted, h, d, p)
        }
        fx.drawFront(canvas, scene)
    }

    /**
     * The water, lit: light on one side of every ripple and dark on the other, as a wallpaper seen
     * through rippling water looks, drawn from the pool's own small bitmap stretched over the window.
     */
    private fun drawWater(canvas: Canvas) {
        val pool = water ?: return
        val bitmap = waterBitmap ?: return
        if (!waterAlive) return
        if (waterDirty) {
            pool.render(
                waterPixels,
                slope = WATER_SLOPE,
                highlight = settings.accent,
                shadow = settings.color,
                tint = settings.accent,
            )
            bitmap.setPixels(waterPixels, 0, pool.cols, 0, 0, pool.cols, pool.rows)
            waterDirty = false
        }
        waterRect.set(0f, waterTop, waterWidth, waterTop + waterHeight)
        canvas.drawBitmap(bitmap, null, waterRect, waterPaint)
    }

    /**
     * The water for a gallery tile: the rings a drop sends out, drawn clean rather than simulated —
     * at a tile's size the simulation's cells show, and its troughs read as a dark smudge. Each ring
     * a bright crest with a soft glow, a faint shadow just outside it, fainter the further out; and
     * where they start, a plain round half dot on the edge rather than the drop, which at a tile's
     * size reads as a pimple.
     */
    private fun drawWaterStill(canvas: Canvas, centre: Float, depth: Float) {
        val across = max(waterWidth, 1f)
        val cx = 0f
        strokePaint.shader = null
        for ((i, k) in WATER_STILL_RINGS.withIndex()) {
            val r = across * k
            val strength = 1f - i * 0.28f
            strokePaint.strokeWidth = 3.2f * density
            strokePaint.color = withAlpha(settings.color, 0.1f * strength)
            canvas.drawCircle(cx, centre, r + 2.6f * density, strokePaint)
            strokePaint.strokeWidth = 6.5f * density
            strokePaint.color = withAlpha(settings.accent, 0.16f * strength)
            canvas.drawCircle(cx, centre, r, strokePaint)
            strokePaint.strokeWidth = 1.9f * density
            strokePaint.color = withAlpha(settings.accent, 0.85f * strength)
            canvas.drawCircle(cx, centre, r, strokePaint)
        }
        val dot = max(depth * 0.66f, 6f * density)
        bodyPaint.shader = null
        bodyPaint.color = settings.color
        canvas.drawArc(cx - dot, centre - dot, cx + dot, centre + dot, -90f, 180f, true, bodyPaint)
    }

    /**
     * The drop at the button: the glass's black risen out of the edge like a bead of liquid, with a
     * wet shine along its rim and a glint where the light catches it, and the level in it.
     */
    private fun drawDrop(canvas: Canvas, p: Float, press: Float, centre: Float, half: Float, depth: Float) {
        // Fatter for a moment at each press, as a drop is when something lands in it.
        val d = depth * p * (1f + 0.16f * press)
        val h = half * (0.88f + 0.12f * min(p, 1f)) * (1f - 0.05f * press)
        if (d <= 0.5f) return
        drawSolidBump(canvas, centre, h, d, settings.color)
        val shine = min(p, 1f)
        // The wet rim: a thin light line just inside the edge of the drop.
        buildBump(rimPath, centre, h * 0.92f, d - 1.6f * density, shoulder, closed = false)
        strokePaint.shader = null
        strokePaint.strokeWidth = 1.4f * density
        strokePaint.color = withAlpha(settings.accent, 0.32f * shine)
        canvas.drawPath(rimPath, strokePaint)
        // The glint: where the light from above catches the drop, near its upper end.
        val gx = d * 0.55f
        val gy = centre - h * 0.42f
        val radius = max(d * 0.42f, 3f * density)
        glintPaint.shader = RadialGradient(
            gx, gy, radius,
            withAlpha(settings.accent, 0.55f * shine), withAlpha(settings.accent, 0f),
            Shader.TileMode.CLAMP,
        )
        canvas.save()
        canvas.clipPath(bodyPath)
        canvas.drawCircle(gx, gy, radius, glintPaint)
        canvas.restore()
        drawLevelTrack(canvas, centre, h, d, p)
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
        buildBump(bodyPath, centre, half, depth, shoulder)
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
        val trackHalf = half * (1f - shoulder) * 0.86f
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
        buildBump(bodyPath, centre, half, d, shoulder)
        bodyPaint.shader = null
        bodyPaint.color = withAlpha(settings.color, 0.55f * min(p, 1f))
        canvas.drawPath(bodyPath, bodyPaint)
        buildBump(rimPath, centre, half, d, shoulder, closed = false)
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
        buildBump(bodyPath, centre, h, d, shoulder)
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
        buildBump(rimPath, centre, h, d, shoulder, closed = false)
        glowStroke(canvas, rimPath, settings.accent, min(p, 1f) * flicker, 2.2f)
        if (settings.showLevel) {
            val trackHalf = h * (1f - shoulder) * 0.86f
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
        buildBump(bodyPath, centre, h, d, shoulder)
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
        buildBump(scratchPath, centre, h * 0.98f, d * 0.45f, shoulder)
        bodyPaint.color = withAlpha(settings.color, 0.45f * min(p, 1f))
        canvas.drawPath(scratchPath, bodyPaint)
        buildBump(rimPath, centre, h, d, shoulder, closed = false)
        strokePaint.shader = null
        strokePaint.strokeWidth = 1.4f * density
        strokePaint.color = withAlpha(Color.WHITE, 0.4f * min(p, 1f))
        canvas.drawPath(rimPath, strokePaint)
        drawLevelTrack(canvas, centre, h, d, p, fillColour = Color.WHITE)
    }

    /** Rings going out from the button into the screen, one for each press. */
    private fun drawRipples(canvas: Canvas, now: Long, centre: Float, half: Float, depth: Float) {
        val baseReach = depth * spec.reach * 0.92f
        for (ring in rings) {
            val reach = baseReach * ring.power
            val age = ((now - ring.bornMs) / RING_LIFE_MS.toFloat()).coerceIn(0f, 1f)
            val eased = 1f - (1f - age) * (1f - age)
            val d = depth + (reach - depth) * eased
            val h = half * (1f + 0.5f * eased)
            buildBump(rimPath, centre, h, d, shoulder, closed = false)
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
                x = depth * ButtonBump.profile(u, shoulder),
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
            val x = d * ButtonBump.profile(u, shoulder) + 2f * density
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
        buildBump(bodyPath, centre, h, d1, shoulder, second = drift, secondDepth = d2)
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
        buildBump(bodyPath, centre, h, d, shoulder)
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
        buildBump(rimPath, centre, h, d, shoulder, closed = false)
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
        buildBump(rimPath, centre, h, d, shoulder, closed = false)
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
        buildBump(rimPath, centre, h, d, shoulder, closed = false)
        glowStroke(canvas, rimPath, settings.accent, min(p, 1f) * (0.35f + 0.65f * beat), 2.2f)
        drawSolidBump(canvas, centre, h, d, settings.color)
        // The trace: a flat line with a beat in it, drawn across the flat of the bump as it beats.
        if (since in 0f..0.9f && d > 8f * density) {
            val x = d * 0.5f
            val run = h * (1f - shoulder) * 0.9f
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
        buildBump(bodyPath, centre, h, d, shoulder, warp = leanBy * 2.2f)
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
        val x = point[0].clampIn(w / 2f, width - w / 2f)
        val y = point[1].clampIn(h / 2f, height - h / 2f)
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

        // ---- the water style ----
        /** A cell of the pool, at least: a couple of dp, so the light on it is smooth. */
        const val WATER_CELL_DP = 2f
        const val WATER_MIN_CELL_PX = 4f
        /** The most cells a pool is made of, to keep a frame of it cheap on any phone. */
        const val WATER_MAX_CELLS = 22_000
        /** How fast the ripples run across the glass, in dp a second. */
        const val WATER_SPEED_DP = 420f

        /** How much of its movement the water keeps each second: enough for a ripple to cross the screen. */
        const val WATER_KEEP_PER_S = 0.42f
        /** The most steps one frame takes, so a stall does not turn into a burst of work. */
        const val WATER_MAX_STEPS = 8
        /** How high the drop stands in the water, how firmly it is held there, and how hard a press lands. */
        const val WATER_DROP = 1f
        const val WATER_HOLD = 0.3f
        const val WATER_KICK = 0.9f
        /** How much wider and deeper than the drop a press disturbs the water. */
        const val WATER_KICK_SPREAD = 1.25f
        const val WATER_KICK_DEPTH = 1.8f
        /** How steep the ripples look: how strongly they catch the light. */
        const val WATER_SLOPE = 2.8f
        /** Below this nothing on the water can be seen, and it is left still. */
        const val WATER_REST = 0.004f
        /** The steps run for a gallery tile's frozen frame: the first ring half way out. */
        /** The rings in a still of the water, as parts of the way across the screen. */
        val WATER_STILL_RINGS = floatArrayOf(0.3f, 0.56f, 0.84f)

        /** Width multiples and alphas of a glow's passes, faintest and widest first. */
        val GLOW = listOf(5.5f to 0.08f, 3.4f to 0.14f, 2f to 0.3f, 1f to 1f)

        val LOOPING_STYLES = setOf(
            ButtonBump.STYLE_AURORA, ButtonBump.STYLE_SPECTRUM, ButtonBump.STYLE_LAVA,
            ButtonBump.STYLE_NEON, ButtonBump.STYLE_SPARKLE,
        )
    }
}
