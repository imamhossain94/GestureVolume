package com.newagedevs.gesturevolume.ui.view

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.newagedevs.gesturevolume.utils.ButtonBump
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The characters: a cat, a monkey and an alien peeking round the edge of the glass, a flying saucer,
 * and a swarm of bees.
 */

// ---- peeking round the edge ---------------------------------------------------------------------------

/**
 * A character that peeks round the edge of the glass at the button: it slides out with the bump, holds
 * on with its hands, blinks now and then, and pops up, wide-eyed, at every press. Its face is always the
 * right way up on the screen, whichever edge it peeks round.
 */
internal abstract class PeekEffect(random: Random) : BumpEffect(random) {
    override val drawsBump: Boolean get() = false
    override val loops: Boolean get() = true

    protected val peek = ButtonBump.Spring()
    protected var time = 0f
    private var blinkIn = 1.6f
    private var blink = 0f

    /** 1 at a press, fading back to 0: how surprised it is. */
    protected var excite = 0f

    protected val fill = solidFill()
    protected val ink = roundStroke()
    protected val oval = RectF()

    override val stillAfterS: Float get() = 0.6f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        peek.velocity += if (atLimit) 2f else 4.5f
        excite = 1f
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        time += dt
        ButtonBump.stepSpring(peek, scene.presence.coerceIn(0f, 1f), 210f, 0.5f, dt)
        excite *= exp(-2.5f * dt)
        blinkIn -= dt
        if (blinkIn <= 0f) {
            blink = 1f
            blinkIn = rnd(1.8f, 3.4f)
        }
        blink = (blink - dt / 0.16f).coerceAtLeast(0f)
        return peek.value > 0.005f || abs(peek.velocity) > 0.01f || excite > 0.02f
    }

    protected open fun radius(scene: BumpScene) = max(scene.restHalf * 0.62f, scene.restDepth * 1.15f)

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val r = radius(scene)
        val pv = peek.value
        if (pv < 0.01f) return
        val cx = -r + pv * r * 1.6f
        val cy = scene.centre
        canvas.save()
        canvas.translate(cx, cy)
        // A little tilt of the head as it peeks, more when surprised.
        canvas.rotate(uprightDegrees(scene) + sin(time * 1.3f) * 5f + excite * 8f)
        drawHead(canvas, r, closed = blink > 0.3f && excite < 0.5f)
        canvas.restore()
        if (pv > 0.3f) {
            // Hands over the edge, either side of the head.
            for (side in intArrayOf(-1, 1)) drawHand(canvas, r * 0.1f * pv, cy + side * r * 0.95f, r * 0.3f)
        }
    }

    protected abstract fun drawHead(canvas: Canvas, r: Float, closed: Boolean)

    protected abstract fun drawHand(canvas: Canvas, x: Float, y: Float, r: Float)

    /** An eye shut in a happy curve. */
    protected fun closedEye(canvas: Canvas, x: Float, y: Float, w: Float, colour: Int, width: Float) {
        ink.strokeWidth = width
        ink.color = colour
        oval.set(x - w, y - w * 0.6f, x + w, y + w * 0.6f)
        canvas.drawArc(oval, 200f, 140f, false, ink)
    }

    override fun clear() {
        peek.snapTo(0f)
        excite = 0f
    }
}

/** A ginger cat peeking round the edge: ears, green eyes, whiskers, and paws on the frame. */
internal class CatEffect(random: Random) : PeekEffect(random) {
    private val ear = Path()

    override fun drawHead(canvas: Canvas, r: Float, closed: Boolean) {
        // Ears, twitching when it is surprised.
        for (side in intArrayOf(-1, 1)) {
            canvas.save()
            canvas.rotate(side * excite * 12f, side * 0.6f * r, -0.6f * r)
            ear.reset()
            ear.moveTo(side * 0.22f * r, -0.72f * r)
            ear.lineTo(side * 0.95f * r, -1.08f * r)
            ear.lineTo(side * 0.9f * r, -0.28f * r)
            ear.close()
            fill.color = 0xFFF6A447.toInt()
            canvas.drawPath(ear, fill)
            ear.reset()
            ear.moveTo(side * 0.38f * r, -0.68f * r)
            ear.lineTo(side * 0.86f * r, -0.93f * r)
            ear.lineTo(side * 0.82f * r, -0.42f * r)
            ear.close()
            fill.color = 0xFFFFAB91.toInt()
            canvas.drawPath(ear, fill)
            canvas.restore()
        }
        fill.color = 0xFFF6A447.toInt()
        canvas.drawOval(-1.05f * r, -0.92f * r, 1.05f * r, 0.95f * r, fill)
        // Tabby stripes.
        ink.strokeWidth = 0.07f * r
        ink.color = 0xFFD9822B.toInt()
        for (x in floatArrayOf(-0.22f, 0f, 0.22f)) canvas.drawLine(x * r, -0.86f * r, x * r * 0.8f, -0.6f * r, ink)
        // Muzzle and blush.
        fill.color = 0xFFFFF3E0.toInt()
        canvas.drawCircle(-0.17f * r, 0.33f * r, 0.22f * r, fill)
        canvas.drawCircle(0.17f * r, 0.33f * r, 0.22f * r, fill)
        canvas.drawCircle(0f, 0.48f * r, 0.15f * r, fill)
        fill.color = fade(0xFFFF8A80.toInt(), 0.45f)
        canvas.drawCircle(-0.64f * r, 0.24f * r, 0.13f * r, fill)
        canvas.drawCircle(0.64f * r, 0.24f * r, 0.13f * r, fill)
        // Eyes.
        for (side in intArrayOf(-1, 1)) {
            val ex = side * 0.38f * r
            val ey = -0.06f * r
            if (closed) {
                closedEye(canvas, ex, ey, 0.17f * r, 0xFF4E342E.toInt(), 0.06f * r)
            } else {
                val wide = 1f + excite * 0.2f
                fill.color = 0xFF8BC34A.toInt()
                canvas.drawOval(ex - 0.17f * r, ey - 0.21f * r * wide, ex + 0.17f * r, ey + 0.21f * r * wide, fill)
                val pupil = 0.045f * r * (1f + excite * 1.6f)
                fill.color = 0xFF1B1B1B.toInt()
                canvas.drawOval(ex - pupil, ey - 0.17f * r * wide, ex + pupil, ey + 0.17f * r * wide, fill)
                fill.color = 0xFFFFFFFF.toInt()
                canvas.drawCircle(ex + 0.05f * r, ey - 0.08f * r, 0.045f * r, fill)
            }
        }
        // Nose and mouth.
        ear.reset()
        ear.moveTo(-0.08f * r, 0.15f * r)
        ear.lineTo(0.08f * r, 0.15f * r)
        ear.lineTo(0f, 0.25f * r)
        ear.close()
        fill.color = 0xFFF48FB1.toInt()
        canvas.drawPath(ear, fill)
        ink.strokeWidth = 0.045f * r
        ink.color = 0xFF4E342E.toInt()
        oval.set(-0.16f * r, 0.18f * r, 0f, 0.36f * r)
        canvas.drawArc(oval, 20f, 140f, false, ink)
        oval.set(0f, 0.18f * r, 0.16f * r, 0.36f * r)
        canvas.drawArc(oval, 20f, 140f, false, ink)
        // Whiskers.
        ink.strokeWidth = 0.025f * r
        ink.color = fade(0xFF4E342E.toInt(), 0.8f)
        for (side in intArrayOf(-1, 1)) {
            for ((i, y) in floatArrayOf(0.18f, 0.32f, 0.46f).withIndex()) {
                canvas.drawLine(side * 0.32f * r, (0.28f + i * 0.04f) * r, side * 1.22f * r, y * r, ink)
            }
        }
    }

    override fun drawHand(canvas: Canvas, x: Float, y: Float, r: Float) {
        fill.color = 0xFFF6A447.toInt()
        canvas.drawCircle(x, y, r, fill)
        ink.strokeWidth = r * 0.12f
        ink.color = 0xFFD9822B.toInt()
        canvas.drawLine(x + r * 0.4f, y - r * 0.35f, x + r * 0.85f, y - r * 0.35f, ink)
        canvas.drawLine(x + r * 0.4f, y + r * 0.35f, x + r * 0.85f, y + r * 0.35f, ink)
    }
}

/** A monkey peeking round the edge, grinning, and shrieking with delight at every press. */
internal class MonkeyEffect(random: Random) : PeekEffect(random) {
    private val tuft = Path()

    override fun drawHead(canvas: Canvas, r: Float, closed: Boolean) {
        val brown = 0xFF8D5524.toInt()
        val skin = 0xFFF5CBA7.toInt()
        for (side in intArrayOf(-1, 1)) {
            fill.color = brown
            canvas.drawCircle(side * 0.98f * r, 0f, 0.33f * r, fill)
            fill.color = skin
            canvas.drawCircle(side * 0.98f * r, 0f, 0.2f * r, fill)
        }
        fill.color = brown
        canvas.drawCircle(0f, 0f, r, fill)
        tuft.reset()
        tuft.moveTo(-0.2f * r, -0.92f * r)
        tuft.lineTo(-0.08f * r, -1.18f * r)
        tuft.lineTo(0f, -0.94f * r)
        tuft.lineTo(0.1f * r, -1.2f * r)
        tuft.lineTo(0.22f * r, -0.92f * r)
        tuft.close()
        canvas.drawPath(tuft, fill)
        // The face: round under the eyes, a broad muzzle.
        fill.color = skin
        canvas.drawCircle(-0.32f * r, -0.12f * r, 0.4f * r, fill)
        canvas.drawCircle(0.32f * r, -0.12f * r, 0.4f * r, fill)
        canvas.drawOval(-0.62f * r, -0.05f * r, 0.62f * r, 0.8f * r, fill)
        for (side in intArrayOf(-1, 1)) {
            val ex = side * 0.3f * r
            val ey = -0.12f * r
            if (closed) {
                closedEye(canvas, ex, ey, 0.13f * r, 0xFF3E2723.toInt(), 0.06f * r)
            } else {
                fill.color = 0xFF1B1B1B.toInt()
                canvas.drawCircle(ex, ey, (0.11f + 0.03f * excite) * r, fill)
                fill.color = 0xFFFFFFFF.toInt()
                canvas.drawCircle(ex + 0.04f * r, ey - 0.04f * r, 0.035f * r, fill)
            }
            // Brows, up when it is excited.
            ink.strokeWidth = 0.05f * r
            ink.color = 0xFF5D3A1A.toInt()
            canvas.drawLine(ex - 0.12f * r, ey - (0.24f + 0.08f * excite) * r, ex + 0.12f * r, ey - (0.28f + 0.08f * excite) * r, ink)
        }
        fill.color = 0xFF5D3A1A.toInt()
        canvas.drawCircle(-0.07f * r, 0.25f * r, 0.035f * r, fill)
        canvas.drawCircle(0.07f * r, 0.25f * r, 0.035f * r, fill)
        if (excite > 0.35f) {
            // Mouth wide open.
            fill.color = 0xFF7B1F1F.toInt()
            canvas.drawOval(-0.24f * r, 0.36f * r, 0.24f * r, 0.66f * r, fill)
            fill.color = 0xFFFFFFFF.toInt()
            canvas.drawRect(-0.16f * r, 0.37f * r, 0.16f * r, 0.43f * r, fill)
        } else {
            ink.strokeWidth = 0.06f * r
            ink.color = 0xFF5D3A1A.toInt()
            oval.set(-0.3f * r, 0.22f * r, 0.3f * r, 0.6f * r)
            canvas.drawArc(oval, 20f, 140f, false, ink)
        }
    }

    override fun drawHand(canvas: Canvas, x: Float, y: Float, r: Float) {
        fill.color = 0xFF8D5524.toInt()
        canvas.drawCircle(x, y, r, fill)
        fill.color = 0xFFF5CBA7.toInt()
        canvas.drawCircle(x + r * 0.25f, y, r * 0.55f, fill)
    }
}

/** A little green alien peeking round the edge: big black eyes, antennae bobbing, eyes glowing at every press. */
internal class AlienEffect(random: Random) : PeekEffect(random) {
    private val head = Path()
    private val eyeGlow = solidFill().apply { shader = softPuff(0xFF76FF03.toInt()) }

    override fun drawHead(canvas: Canvas, r: Float, closed: Boolean) {
        val green = 0xFF8BE36A.toInt()
        // Antennae, swaying, their tips glowing.
        for (side in intArrayOf(-1, 1)) {
            val sway = sin(time * 4f + side) * 0.12f * r
            val tx = side * 0.62f * r + sway
            val ty = -1.38f * r
            ink.strokeWidth = 0.06f * r
            ink.color = 0xFF5FB843.toInt()
            canvas.drawLine(side * 0.32f * r, -0.8f * r, tx, ty, ink)
            fill.color = blend(0xFFCCFF90.toInt(), 0xFFFFFF00.toInt(), excite)
            canvas.drawCircle(tx, ty, 0.12f * r, fill)
        }
        head.reset()
        head.moveTo(0f, -1f * r)
        head.cubicTo(0.95f * r, -1f * r, 1.05f * r, 0f, 0.55f * r, 0.6f * r)
        head.quadTo(0f, 1.05f * r, -0.55f * r, 0.6f * r)
        head.cubicTo(-1.05f * r, 0f, -0.95f * r, -1f * r, 0f, -1f * r)
        head.close()
        fill.color = green
        canvas.drawPath(head, fill)
        fill.color = fade(0xFFFFFFFF.toInt(), 0.25f)
        canvas.drawOval(-0.55f * r, -0.8f * r, -0.15f * r, -0.45f * r, fill)
        for (side in intArrayOf(-1, 1)) {
            canvas.save()
            canvas.translate(side * 0.38f * r, 0.05f * r)
            canvas.rotate(side * -25f)
            if (excite > 0.05f) {
                eyeGlow.alpha = (200 * excite).toInt()
                canvas.save()
                canvas.scale(0.48f * r, 0.32f * r)
                canvas.drawCircle(0f, 0f, 1f, eyeGlow)
                canvas.restore()
            }
            val open = if (closed) 0.15f else 1f
            fill.color = 0xFF101418.toInt()
            canvas.drawOval(-0.3f * r, -0.17f * r * open, 0.3f * r, 0.17f * r * open, fill)
            if (!closed) {
                fill.color = fade(0xFFFFFFFF.toInt(), 0.85f)
                canvas.drawOval(-0.16f * r, -0.1f * r, -0.02f * r, -0.03f * r, fill)
            }
            canvas.restore()
        }
        ink.strokeWidth = 0.05f * r
        ink.color = 0xFF3E8E2A.toInt()
        canvas.drawLine(-0.09f * r, 0.58f * r, 0.09f * r, 0.58f * r, ink)
    }

    override fun drawHand(canvas: Canvas, x: Float, y: Float, r: Float) {
        // Three long fingers over the edge.
        ink.strokeWidth = r * 0.32f
        ink.color = 0xFF8BE36A.toInt()
        for (k in -1..1) canvas.drawLine(x - r * 0.2f, y + k * r * 0.45f, x + r * 0.9f, y + k * r * 0.5f, ink)
        fill.color = 0xFF8BE36A.toInt()
        canvas.drawCircle(x - r * 0.2f, y, r * 0.55f, fill)
    }
}

// ---- ufo ---------------------------------------------------------------------------------------------

/**
 * A flying saucer at every press: it slips out of the bump, swoops to a hover with its lights chasing
 * round the rim, shines its beam down for a moment, and zips away.
 */
internal class UfoEffect(random: Random) : BumpEffect(random) {
    private class Saucer {
        var alive = false
        var age = 0f
        var sx = 0f
        var sy = 0f
        var hx = 0f
        var hy = 0f
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
    }

    private val saucers = Array(2) { Saucer() }
    private var unit = 1f
    private val beam = Path()
    private val fill = solidFill()
    private val beamPaint = solidFill()
    private val body = solidFill()
    private val ink = roundStroke()

    override val stillAfterS: Float get() = 0.8f

    private fun shaders(d: Float) {
        if (unit == d) return
        unit = d
        beamPaint.shader = LinearGradient(0f, 4f * d, 0f, 70f * d, 0xA0C6FF9E.toInt(), 0x00C6FF9E, Shader.TileMode.CLAMP)
        body.shader = LinearGradient(0f, -4f * d, 0f, 5f * d, intArrayOf(0xFFF1F3F5.toInt(), 0xFFA9B2B8.toInt(), 0xFF6B757B.toInt()), null, Shader.TileMode.CLAMP)
    }

    override fun press(scene: BumpScene, atLimit: Boolean) {
        val s = saucers.firstOrNull { !it.alive } ?: saucers.minByOrNull { -it.age } ?: return
        s.alive = true
        s.age = 0f
        s.sx = scene.restDepth * 0.5f
        s.sy = scene.centre
        s.hx = scene.reach * rnd(0.42f, 0.6f)
        s.hy = scene.centre + scene.span * rnd(-0.18f, 0.18f)
        s.x = s.sx
        s.y = s.sy
        s.vx = 0f
        s.vy = 0f
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        val d = scene.density
        var alive = false
        for (s in saucers) {
            if (!s.alive) continue
            s.age += dt
            val t = s.age
            when {
                t < 0.5f -> {
                    val k = easeOut(t / 0.5f)
                    s.x = s.sx + (s.hx - s.sx) * k
                    s.y = s.sy + (s.hy - s.sy) * k
                }
                t < 1.5f -> {
                    s.x = s.hx + sin(t * 3f) * 3f * d
                    s.y = s.hy + sin(t * 5f) * 2.5f * d
                }
                else -> {
                    // Away: up the screen and further in, faster and faster.
                    s.vx += 900f * d * dt
                    s.vx -= scene.gravityX * 900f * d * dt
                    s.vy -= scene.gravityY * 900f * d * dt
                    s.x += s.vx * dt
                    s.y += s.vy * dt
                }
            }
            if (t > 2.4f || s.x > scene.reach + 60f * d || s.y < -60f * d || s.y > scene.span + 60f * d) s.alive = false else alive = true
        }
        return alive
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        shaders(d)
        for (s in saucers) {
            if (!s.alive) continue
            canvas.save()
            canvas.translate(s.x, s.y)
            canvas.rotate(uprightDegrees(scene) + if (s.age < 0.5f) -12f * (1f - s.age / 0.5f) else 0f)
            if (s.age in 0.55f..1.5f) {
                beam.reset()
                beam.moveTo(-5f * d, 4f * d)
                beam.lineTo(5f * d, 4f * d)
                beam.lineTo(24f * d, 70f * d)
                beam.lineTo(-24f * d, 70f * d)
                beam.close()
                beamPaint.alpha = (255 * (0.75f + 0.25f * sin(s.age * 40f))).toInt()
                canvas.drawPath(beam, beamPaint)
            }
            fill.color = 0xD080DEEA.toInt()
            canvas.drawArc(-7f * d, -10f * d, 7f * d, 4f * d, 180f, 180f, true, fill)
            fill.color = fade(0xFFFFFFFF.toInt(), 0.6f)
            canvas.drawOval(-4.5f * d, -8f * d, -1.5f * d, -5f * d, fill)
            canvas.drawOval(-15f * d, -4f * d, 15f * d, 5f * d, body)
            ink.strokeWidth = 0.8f * d
            ink.color = 0xFF4E585E.toInt()
            canvas.drawOval(-15f * d, -4f * d, 15f * d, 5f * d, ink)
            // Lights chasing round the rim.
            for (i in 0 until 5) {
                val lx = (-11f + 5.5f * i) * d
                val on = ((s.age * 8f).toInt() + i) % 3
                val colour = when (on) {
                    0 -> 0xFFFFEB3B.toInt()
                    1 -> 0xFFFF5252.toInt()
                    else -> 0xFF18FFFF.toInt()
                }
                fill.color = fade(colour, 0.35f)
                canvas.drawCircle(lx, 1.6f * d, 2.6f * d, fill)
                fill.color = colour
                canvas.drawCircle(lx, 1.6f * d, 1.3f * d, fill)
            }
            canvas.restore()
        }
    }

    override fun clear() {
        for (s in saucers) s.alive = false
    }
}

// ---- bees ---------------------------------------------------------------------------------------------

/** Bees buzz out of the bump at every press, looping about the screen on dotted trails, and head home. */
internal class BeesEffect(random: Random) : BumpEffect(random) {
    private class Bee {
        var alive = false
        var age = 0f
        var life = 0f
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var tx = 0f
        var ty = 0f
        var retarget = 0f
        var phase = 0f
        val trail = FloatArray(2 * TRAIL)
        var trailHead = 0
        var trailCount = 0
        var trailClock = 0f
    }

    private val bees = Array(10) { Bee() }
    private var time = 0f
    private val body = RectF(-0.9f, -0.55f, 0.75f, 0.55f)
    private val bodyPath = Path().apply { addOval(RectF(-0.9f, -0.55f, 0.75f, 0.55f), Path.Direction.CW) }
    private val stinger = Path().apply {
        moveTo(-0.85f, -0.12f)
        lineTo(-1.15f, 0f)
        lineTo(-0.85f, 0.12f)
        close()
    }
    private val fill = solidFill()
    private val ink = roundStroke()

    override val stillAfterS: Float get() = 0.7f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        val d = scene.density
        repeat(if (atLimit) 1 else 3) {
            val b = bees.firstOrNull { !it.alive } ?: return@repeat
            b.alive = true
            b.age = 0f
            b.life = rnd(2.6f, 3.6f)
            b.x = scene.restDepth
            b.y = scene.centre + scene.restHalf * rnd(-0.3f, 0.3f)
            b.vx = 120f * d
            b.vy = rnd(-60f, 60f) * d
            b.phase = rnd(0f, 6.28f)
            b.trailCount = 0
            b.trailHead = 0
            retarget(scene, b)
        }
    }

    private fun retarget(scene: BumpScene, b: Bee) {
        b.tx = scene.reach * rnd(0.25f, 0.85f)
        b.ty = scene.centre + scene.span * rnd(-0.35f, 0.35f)
        b.retarget = rnd(0.5f, 0.9f)
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        time += dt
        val d = scene.density
        var alive = false
        for (b in bees) {
            if (!b.alive) continue
            b.age += dt
            if (b.age >= b.life) {
                b.alive = false
                continue
            }
            if (b.age > b.life - 0.9f) {
                // Home, to the bump.
                b.tx = scene.restDepth * 0.4f
                b.ty = scene.centre
            } else {
                b.retarget -= dt
                if (b.retarget <= 0f || hypot(b.tx - b.x, b.ty - b.y) < 12f * d) retarget(scene, b)
            }
            val dx = b.tx - b.x
            val dy = b.ty - b.y
            val dist = hypot(dx, dy).coerceAtLeast(1f)
            val speed = 160f * d
            val steer = min(1f, 3f * dt)
            b.vx += (dx / dist * speed - b.vx) * steer
            b.vy += (dy / dist * speed - b.vy) * steer
            // The wobble of a bee's flight, across its way.
            val v = hypot(b.vx, b.vy).coerceAtLeast(1f)
            val wobble = sin(b.age * 9f + b.phase) * 140f * d * dt
            b.vx += -b.vy / v * wobble
            b.vy += b.vx / v * wobble
            b.x += b.vx * dt
            b.y += b.vy * dt
            b.trailClock -= dt
            if (b.trailClock <= 0f) {
                b.trailClock = 0.03f
                b.trail[2 * b.trailHead] = b.x
                b.trail[2 * b.trailHead + 1] = b.y
                b.trailHead = (b.trailHead + 1) % TRAIL
                b.trailCount = min(b.trailCount + 1, TRAIL)
            }
            alive = true
        }
        return alive
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        for (b in bees) {
            if (!b.alive) continue
            val fadeIn = min(1f, b.age / 0.15f)
            val fadeOut = ((b.life - b.age) / 0.25f).coerceIn(0f, 1f)
            val a = fadeIn * fadeOut
            // The dotted trail.
            for (i in 0 until b.trailCount) {
                val idx = (b.trailHead - 1 - i + TRAIL) % TRAIL
                fill.color = fade(0xFF5D4037.toInt(), 0.35f * a * (1f - i / TRAIL.toFloat()))
                canvas.drawCircle(b.trail[2 * idx], b.trail[2 * idx + 1], 1.1f * d, fill)
            }
            canvas.save()
            canvas.translate(b.x, b.y)
            canvas.rotate(Math.toDegrees(atan2(b.vy, b.vx).toDouble()).toFloat())
            val s = 6.5f * d
            canvas.scale(s, s)
            // Wings, a blur of beats.
            val flap = 0.35f + 0.65f * abs(sin(time * 60f + b.phase))
            fill.color = fade(0xFFFFFFFF.toInt(), 0.7f * a)
            canvas.drawOval(-0.5f, -0.15f - 1.0f * flap, 0.2f, -0.15f, fill)
            canvas.drawOval(-0.85f, -0.15f - 0.8f * flap, -0.2f, -0.15f, fill)
            fill.color = fade(0xFF3E2723.toInt(), a)
            canvas.drawPath(stinger, fill)
            fill.color = fade(0xFFFFC107.toInt(), a)
            canvas.drawOval(body, fill)
            canvas.save()
            canvas.clipPath(bodyPath)
            fill.color = fade(0xFF212121.toInt(), a)
            canvas.drawRect(-0.5f, -1f, -0.28f, 1f, fill)
            canvas.drawRect(-0.08f, -1f, 0.14f, 1f, fill)
            canvas.restore()
            fill.color = fade(0xFF212121.toInt(), a)
            canvas.drawCircle(0.82f, 0f, 0.36f, fill)
            fill.color = fade(0xFFFFFFFF.toInt(), a)
            canvas.drawCircle(0.95f, -0.12f, 0.1f, fill)
            ink.strokeWidth = 0.08f
            ink.color = fade(0xFF212121.toInt(), a)
            canvas.drawLine(0.95f, -0.25f, 1.25f, -0.6f, ink)
            canvas.drawLine(0.95f, 0.25f, 1.25f, 0.6f, ink)
            canvas.restore()
        }
    }

    override fun clear() {
        for (b in bees) b.alive = false
    }

    private companion object {
        const val TRAIL = 14
    }
}
