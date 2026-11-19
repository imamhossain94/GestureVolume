package com.newagedevs.gesturevolume.ui.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.newagedevs.gesturevolume.utils.ButtonBump
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The big ones: a mushroom cloud, a volcano, smoke, a dragon's fire, a rocket, a gust of wind and an
 * aerosol spray. For the cloud and the volcano the glass's edge is the ground, so they rise into the
 * screen from the button, whichever edge it is on.
 */

/** A soft round puff whose colour comes from [colour] and whose softness comes from a round mask: lit, and fading at its rim. */
internal fun litPuff(colour: Shader): Shader = ComposeShader(
    colour,
    RadialGradient(0f, 0f, 1f, intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt(), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP),
    PorterDuff.Mode.DST_IN,
)

// ---- atom bomb ------------------------------------------------------------------------------------

/**
 * A mushroom cloud at every press: a flash and a fireball at the button, a shockwave running out along
 * the ground, and a column of fire and smoke rising into the screen that rolls up into a cap — soft,
 * lit from above, glowing orange inside as it cools to grey.
 */
internal class AtomEffect(random: Random) : BumpEffect(random) {
    private class Blast {
        var alive = false
        var age = 0f
        var y = 0f
        var size = 1f
        var seed = 0f
    }

    private val blasts = Array(2) { Blast() }
    private var next = 0
    // Smoke lit from away from the ground, in a unit circle: pale on its lit side, shadowed on the other.
    private val smoke = solidFill().apply {
        shader = litPuff(RadialGradient(0.38f, -0.3f, 1.6f, intArrayOf(0xFFF6EDE4.toInt(), 0xFFC8B7A9.toInt(), 0xFF7A6B62.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP))
    }
    private val dust = solidFill().apply {
        shader = litPuff(RadialGradient(0.3f, -0.3f, 1.8f, intArrayOf(0xFFD9C4A8.toInt(), 0xFFB39880.toInt(), 0xFF8E7560.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP))
    }
    private val fire = solidFill().apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xFFFFF6CC.toInt(), 0xE6FFB347.toInt(), 0x99FF6A1A.toInt(), 0), floatArrayOf(0f, 0.3f, 0.62f, 1f), Shader.TileMode.CLAMP)
    }
    private val flash = solidFill().apply { shader = softPuff(0xFFFFF8E1.toInt()) }
    private val ring = roundStroke()

    override val stillAfterS: Float get() = 1.2f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        val b = blasts[next++ % blasts.size]
        b.alive = true
        b.age = 0f
        b.y = scene.centre + scene.restHalf * rnd(-0.12f, 0.12f)
        b.size = min(scene.reach, scene.span * 0.55f) * if (atLimit) 0.7f else 1f
        b.seed = rnd(0f, 6.28f)
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        var alive = false
        for (b in blasts) {
            if (!b.alive) continue
            b.age += dt
            if (b.age > LIFE) b.alive = false else alive = true
        }
        return alive
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        for (b in blasts) if (b.alive) drawBlast(canvas, scene, b)
    }

    private fun puff(canvas: Canvas, paint: Paint, x: Float, y: Float, r: Float, alpha: Float) {
        if (r <= 0.5f || alpha <= 0.01f) return
        paint.alpha = (alpha.coerceAtMost(1f) * 255f).roundToInt()
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(r, r)
        canvas.drawCircle(0f, 0f, 1f, paint)
        canvas.restore()
    }

    private fun drawBlast(canvas: Canvas, scene: BumpScene, b: Blast) {
        val d = scene.density
        val t = b.age
        val s = b.size
        val y = b.y
        val scale = (s / (180f * d)).coerceIn(0.45f, 1.3f)
        val heat = (1f - t / 1.9f).coerceIn(0f, 1f)
        val alpha = if (t < 2.2f) 1f else (1f - (t - 2.2f) / (LIFE - 2.2f)).coerceIn(0f, 1f)

        // The shockwave, a pale ring running out along the ground.
        if (t < 0.85f) {
            val k = t / 0.85f
            val r = s * 1.05f * easeOut(k)
            ring.strokeWidth = 7f * d
            ring.color = fade(0xFFFFF3E0.toInt(), 0.14f * (1f - k))
            canvas.drawCircle(0f, y, r, ring)
            ring.strokeWidth = 1.6f * d
            ring.color = fade(0xFFFFF3E0.toInt(), 0.6f * (1f - k))
            canvas.drawCircle(0f, y, r, ring)
        }

        // The cloud, one layer, so it fades as a whole.
        canvas.saveLayerAlpha(null, (alpha * 255f).roundToInt())
        // The base surge: dust rolling out along the ground both ways.
        val spread = s * 0.46f * easeOut(t / 1.4f)
        for (i in -9..9) {
            val u = i / 9f
            val grow = easeOut(min(1f, t * 2.5f))
            // Irregular, as dust rolling along the ground is: in and out, big and small.
            val lump = sin(i * 1.7f + b.seed)
            val x = (6f + 9f * (1f - abs(u)) + 4f * lump) * d
            puff(canvas, dust, x, y + u * spread, (11f + 7f * (1f - abs(u)) + 3f * lump) * d * scale * grow, 0.85f)
        }
        // The stem: soft puffs from the ground to the cap, narrowest in the middle.
        val top = s * 0.52f * easeOut(t / 1.25f)
        val steps = (top / (3.2f * d)).toInt().coerceAtLeast(1)
        for (i in 0..steps) {
            val k = i / steps.toFloat()
            val wobble = sin(k * 8f + t * 4f + b.seed) * 1.6f * d
            val r = (13f - 5f * sin(PI.toFloat() * k) + 4f * k + 1.5f * sin(k * 13f + b.seed)) * d * scale
            puff(canvas, smoke, top * k, y + wobble, r, 1f)
            puff(canvas, fire, top * k, y + wobble, r * 0.85f, heat * (1f - 0.6f * k))
        }
        // The cap: puffs rolling round a ring, round a full core, glowing inside while it is hot.
        val cap = s * 0.22f * easeOut(t / 1.35f)
        if (cap > 1f) {
            val cx = top + cap * 0.25f
            for (j in 0 until 8) {
                val a = j * 0.785f + b.seed
                puff(canvas, smoke, cx + cos(a) * cap * 0.3f, y + sin(a) * cap * 0.6f, cap * 0.6f, 1f)
            }
            for (i in 0 until 22) {
                val a = i * (2f * PI.toFloat() / 22) + t * 1.4f + b.seed
                val px = cx + cos(a) * cap * 0.62f
                val py = y + sin(a) * cap * 1.18f
                val r = cap * (0.4f + 0.06f * sin(a * 3f + t * 2.2f))
                puff(canvas, smoke, px, py, r, 1f)
            }
            puff(canvas, fire, cx - cap * 0.15f, y, cap * 1.05f, heat * 0.95f)
            puff(canvas, fire, cx - cap * 0.4f, y, cap * 0.6f, (heat * 1.3f).coerceAtMost(1f) * 0.8f)
        }
        // The fireball at the ground, early on.
        if (t < 0.9f) {
            val k = t / 0.9f
            val r = s * 0.2f * easeOut(t / 0.3f)
            for (j in 0 until 4) {
                val a = j * 1.5708f + t * 3f
                puff(canvas, fire, r * 0.4f + cos(a) * r * 0.25f, y + sin(a) * r * 0.3f, r * 0.8f, 1f - k)
            }
        }
        canvas.restore()

        // The flash.
        if (t < 0.4f) {
            val k = t / 0.4f
            puff(canvas, flash, 0f, y, s * (0.3f + 0.5f * k), (1f - k).pow(2))
        }
    }

    override fun clear() {
        for (b in blasts) b.alive = false
    }

    private companion object {
        const val LIFE = 3.2f
    }
}

// ---- volcano ----------------------------------------------------------------------------------------------

/**
 * A volcano rises out of the edge in place of the bump, its crater glowing; every press erupts it — a
 * fountain of lava bombs and rocks that fall back to the ground, lava running down its sides, and a
 * plume of ash rising from the top.
 */
internal class VolcanoEffect(random: Random) : BumpEffect(random) {
    override val drawsBump: Boolean get() = false

    private val bombs = ParticlePool(110)
    private val ash = Puffs(48, softPuff(0xFF5A5560.toInt()))
    private var glow = 0f
    private var erupting = 0f
    private var emitClock = 0f
    private var ashClock = 0f
    private var rumble = 0f
    private var time = 0f
    private val cone = Path()
    private val fill = solidFill().apply {
        shader = LinearGradient(0f, 0f, 1f, 0f, intArrayOf(0xFF4A3429.toInt(), 0xFF7A5A45.toInt(), 0xFF9C7A5E.toInt()), null, Shader.TileMode.CLAMP)
    }
    private val outline = roundStroke()
    private val glowPaint = solidFill().apply { shader = softPuff(0xFFFF8A1E.toInt()) }
    private val lava = roundStroke()
    private val lavaPath = Path()
    private val dot = solidFill()

    init {
        // The mountain in unit terms: the ground at x = 0, the crater at x = 1, its base ±1 along the edge.
        cone.moveTo(-0.12f, -1f)
        cone.cubicTo(0.35f, -0.9f, 0.78f, -0.34f, 1f, -0.22f)
        cone.lineTo(0.93f, 0f)
        cone.lineTo(1f, 0.22f)
        cone.cubicTo(0.78f, 0.34f, 0.35f, 0.9f, -0.12f, 1f)
        cone.close()
    }

    override val stillAfterS: Float get() = 0.45f

    private fun height(scene: BumpScene) = scene.unit * 3.4f * ButtonBump.smootherstep(scene.presence.coerceIn(0f, 1f))
    private fun base(scene: BumpScene) = max(scene.restHalf, scene.unit * 1.45f) * 1.8f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        glow = 1f
        rumble = 1f
        erupting = if (atLimit) 0.25f else 0.6f
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        time += dt
        val d = scene.density
        val h = height(scene)
        val crater = base(scene) * 0.2f
        rumble *= exp(-4f * dt)
        if (erupting > 0f && h > 1f) {
            erupting -= dt
            glow = 1f
            emitClock -= dt
            while (emitClock <= 0f) {
                emitClock += 1f / 90f
                val p = bombs.spawn() ?: break
                p.x = h * 0.97f
                p.y = scene.centre + crater * rnd(-0.7f, 0.7f)
                val angle = rnd(-0.6f, 0.6f)
                val speed = rnd(220f, 470f) * d
                p.vx = cos(angle) * speed
                p.vy = sin(angle) * speed
                p.life = 1.8f
                p.size = rnd(1.8f, 3.2f) * d
                p.kind = if (random.nextFloat() < 0.2f) 1 else 0
            }
            ashClock -= dt
            if (ashClock <= 0f) {
                ashClock = 0.05f
                ash.emit(h, scene.centre + crater * rnd(-0.5f, 0.5f), rnd(60f, 130f) * d, rnd(-30f, 30f) * d, 6f * d, rnd(20f, 30f) * d, rnd(1.6f, 2.4f), 0.7f)
            }
        } else {
            glow *= exp(-1.1f * dt)
        }
        var alive = false
        val drag = exp(-0.4f * dt)
        for (p in bombs.items) {
            if (!p.alive) continue
            p.age += dt
            // Back down to the ground: the edge.
            p.vx = p.vx * drag - GRAVITY * d * dt
            p.vy *= drag
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.x < 0f || p.age > p.life || scene.offScreen(p.x, p.y, 10f * d)) p.alive = false else alive = true
        }
        alive = ash.step(dt, 1f, 55f * d, 0f) || alive
        return alive || glow > 0.02f || erupting > 0f
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        val h = height(scene)
        val b = base(scene)
        val crater = b * 0.2f
        val cy = scene.centre + sin(time * 60f) * rumble * 1.2f * d
        if (h > 1f) {
            canvas.save()
            canvas.translate(0f, cy)
            canvas.scale(h, b)
            canvas.drawPath(cone, fill)
            outline.strokeWidth = 1.4f * d / min(h, b)
            outline.color = 0xFF2B1D17.toInt()
            canvas.drawPath(cone, outline)
            canvas.restore()
            outline.strokeWidth = 2f * d
            outline.color = fade(0xFFFFB74D.toInt(), 0.5f + 0.5f * glow)
            canvas.drawLine(h, cy - crater, h * 0.93f, cy, outline)
            canvas.drawLine(h * 0.93f, cy, h, cy + crater, outline)
            val r = crater * (3f + 1.6f * glow)
            glowPaint.alpha = (255f * (0.35f + 0.6f * glow)).toInt()
            canvas.save()
            canvas.translate(h, cy)
            canvas.scale(r, r)
            canvas.drawCircle(0f, 0f, 1f, glowPaint)
            canvas.restore()
            if (glow > 0.08f) {
                for (side in intArrayOf(-1, 1)) {
                    val run = glow.coerceAtMost(1f)
                    lavaPath.reset()
                    lavaPath.moveTo(h * 0.97f, cy + side * crater * 0.6f)
                    for (k in 1..4) {
                        val f = k / 4f * run
                        lavaPath.lineTo(h * (0.97f - 0.55f * f), cy + side * (crater * 0.6f + (b * 0.6f - crater * 0.6f) * f) + sin(k * 2.1f + side) * 2.5f * d)
                    }
                    lava.strokeWidth = 6f * d
                    lava.color = fade(0xFFFF6D00.toInt(), 0.35f * glow)
                    canvas.drawPath(lavaPath, lava)
                    lava.strokeWidth = 2.2f * d
                    lava.color = fade(0xFFFFC14D.toInt(), glow)
                    canvas.drawPath(lavaPath, lava)
                }
            }
        }
        ash.draw(canvas)
        for (p in bombs.items) {
            if (!p.alive) continue
            if (p.kind == 1) {
                dot.color = 0xFF3A322E.toInt()
                canvas.drawCircle(p.x, p.y, p.size * 0.9f, dot)
            } else {
                lava.strokeWidth = p.size * 1.6f
                lava.color = fade(0xFFFF6D00.toInt(), 0.45f)
                canvas.drawLine(p.x - p.vx * 0.03f, p.y - p.vy * 0.03f, p.x, p.y, lava)
                dot.color = 0xFFFFD54F.toInt()
                canvas.drawCircle(p.x, p.y, p.size * 0.6f, dot)
            }
        }
    }

    override fun clear() {
        bombs.clear()
        ash.clear()
        glow = 0f
        erupting = 0f
    }

    private companion object {
        /** How hard the bombs fall back to the ground, in dp a second each second. */
        const val GRAVITY = 620f
    }
}

// ---- smoke ------------------------------------------------------------------------------------------------

/**
 * Smoke as a cigarette makes it — fine strands curling up from the bump while it is out, twisting and
 * spreading as they rise — and at every press a vape's exhale: a dense white cloud rolling out of the
 * bump, slowing, billowing and drifting up.
 */
internal class SmokeEffect(random: Random) : BumpEffect(random) {
    override val loops: Boolean get() = true

    private class Strand {
        val x = FloatArray(POINTS)
        val y = FloatArray(POINTS)
        val vx = FloatArray(POINTS)
        val vy = FloatArray(POINTS)
        val age = FloatArray(POINTS)
        var head = 0
        var count = 0
        var clock = 0f
        var seed = 0f
        var offset = 0f
    }

    private val strands = Array(2) { Strand() }
    private val vapour = ParticlePool(90)
    private var exhale = 0f
    private var exhaleClock = 0f
    private var time = 0f
    private val puff = solidFill().apply { shader = softPuff(0xFFF1F1F4.toInt()) }
    private val line = roundStroke()

    init {
        strands.forEachIndexed { i, s ->
            s.seed = i * 2.7f + 0.4f
            s.offset = if (i == 0) -0.18f else 0.22f
        }
    }

    override val stillAfterS: Float get() = 0.55f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        exhale = if (atLimit) 0.18f else 0.5f
    }

    /** A slow swirl in the air, in pixels a second each second: what makes smoke curl. */
    private fun swirlX(x: Float, y: Float, seed: Float, d: Float): Float {
        val k = 1f / (34f * d)
        return (sin(y * k * 1.2f + time * 1.6f + seed) + 0.6f * sin(x * k * 2.3f - time * 1.1f + seed * 0.7f)) * 55f * d
    }

    private fun swirlY(x: Float, y: Float, seed: Float, d: Float): Float {
        val k = 1f / (34f * d)
        return (cos(x * k * 1.1f + time * 1.3f + seed) + 0.6f * cos(y * k * 1.9f + time * 0.8f)) * 55f * d
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        time += dt
        val d = scene.density
        val upX = -scene.gravityX
        val upY = -scene.gravityY
        var alive = false
        // The cigarette's strands: a new point at the bump every few hundredths of a second while it
        // smoulders, each point rising, swirled, and growing fainter.
        for (s in strands) {
            if (scene.presence > 0.4f) {
                s.clock -= dt
                while (s.clock <= 0f) {
                    s.clock += 0.028f
                    val i = s.head
                    s.x[i] = scene.depth * 0.85f + rnd(-0.5f, 0.5f) * d
                    s.y[i] = scene.centre + scene.half * s.offset
                    s.vx[i] = rnd(8f, 22f) * d
                    s.vy[i] = 0f
                    s.age[i] = 0f
                    s.head = (s.head + 1) % POINTS
                    s.count = min(s.count + 1, POINTS)
                }
            }
            val drag = exp(-0.8f * dt)
            for (n in 0 until s.count) {
                val i = (s.head - 1 - n + POINTS) % POINTS
                s.age[i] += dt
                s.vx[i] = s.vx[i] * drag + (upX * 70f * d + swirlX(s.x[i], s.y[i], s.seed, d) * s.age[i].coerceAtMost(1f)) * dt
                s.vy[i] = s.vy[i] * drag + (upY * 70f * d + swirlY(s.x[i], s.y[i], s.seed, d) * s.age[i].coerceAtMost(1f)) * dt
                s.x[i] += s.vx[i] * dt
                s.y[i] += s.vy[i] * dt
            }
            // The oldest points, faded out, fall off the end.
            while (s.count > 0 && s.age[(s.head - s.count + POINTS) % POINTS] > STRAND_LIFE) s.count--
            if (s.count > 0) alive = true
        }
        // The vape's exhale.
        if (exhale > 0f) {
            exhale -= dt
            exhaleClock -= dt
            while (exhaleClock <= 0f) {
                exhaleClock += 0.018f
                val p = vapour.spawn() ?: break
                val a = rnd(-0.38f, 0.38f)
                val speed = rnd(140f, 380f) * d
                p.x = scene.restDepth * 0.9f
                p.y = scene.centre + rnd(-3f, 3f) * d
                p.vx = cos(a) * speed
                p.vy = sin(a) * speed
                p.size = rnd(5f, 9f) * d
                p.tx = rnd(45f, 70f) * d
                p.life = rnd(2.6f, 3.4f)
                p.mark = rnd(0.045f, 0.075f)
                p.phase = rnd(0f, 6.28f)
            }
            alive = true
        }
        val drag = exp(-3.2f * dt)
        for (p in vapour.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.life) {
                p.alive = false
                continue
            }
            val curl = (p.age * 1.5f).coerceAtMost(1f)
            p.vx = p.vx * drag + (upX * 60f * d + swirlX(p.x, p.y, p.phase, d) * 1.3f * curl) * dt
            p.vy = p.vy * drag + (upY * 60f * d + swirlY(p.x, p.y, p.phase, d) * 1.3f * curl) * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            alive = true
        }
        return alive
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        // The exhale: many faint, overlapping puffs, each a few lumps, so its edge billows.
        for (p in vapour.items) {
            if (!p.alive) continue
            val t = p.age / p.life
            val r = p.size + (p.tx - p.size) * easeOut(t)
            val a = p.mark * (1f - t).pow(1.3f) * min(1f, p.age / 0.06f)
            puff.alpha = (a * 255f).roundToInt()
            for (lobe in 0 until 3) {
                val ang = p.phase + lobe * 2.094f + p.age * 0.6f
                canvas.save()
                canvas.translate(p.x + cos(ang) * r * 0.28f, p.y + sin(ang) * r * 0.28f)
                canvas.scale(r * 0.75f, r * 0.75f)
                canvas.drawCircle(0f, 0f, 1f, puff)
                canvas.restore()
            }
        }
        // The strands: thin and clear near the bump, wider and fainter as they rise.
        for (s in strands) {
            if (s.count < 2) continue
            var px = 0f
            var py = 0f
            for (n in 0 until s.count) {
                val i = (s.head - 1 - n + POINTS) % POINTS
                if (n > 0) {
                    val age = s.age[i]
                    val k = (age / STRAND_LIFE).coerceIn(0f, 1f)
                    line.strokeWidth = (1.2f + 6f * k) * d
                    line.color = fade(0xFFE9E9EE.toInt(), 0.62f * (1f - k).pow(1.3f))
                    canvas.drawLine(px, py, s.x[i], s.y[i], line)
                }
                px = s.x[i]
                py = s.y[i]
            }
        }
    }

    override fun clear() {
        for (s in strands) s.count = 0
        vapour.clear()
        exhale = 0f
    }

    private companion object {
        const val POINTS = 110
        const val STRAND_LIFE = 3f
    }
}

// ---- dragon fire -------------------------------------------------------------------------------------------

/**
 * A dragon's head comes out of the edge in place of the bump — scaled, horned, its eye smouldering,
 * smoke curling from its nostrils — and at every press it opens its jaws and breathes a roaring stream
 * of fire across the screen.
 */
internal class DragonEffect(random: Random) : BumpEffect(random) {
    override val drawsBump: Boolean get() = false
    override val loops: Boolean get() = true

    private val flames = ParticlePool(260)
    private val smoke = Puffs(30, softPuff(0xFF4A4146.toInt()))
    private val jaw = ButtonBump.Spring()
    private var breath = 0f
    private var emitClock = 0f
    private var smokeClock = 0f
    private var time = 0f

    // The head and the jaw, drawn once at their size into pictures, scales and all.
    private var art = 0f
    private var headArt: Bitmap? = null
    private var jawArt: Bitmap? = null
    private val artPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val hot = solidFill().apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xFFFFFBE6.toInt(), 0xF2FFD54F.toInt(), 0xB3FF8F00.toInt(), 0), floatArrayOf(0f, 0.25f, 0.6f, 1f), Shader.TileMode.CLAMP)
    }
    private val cool = solidFill().apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xE6FF7043.toInt(), 0x99D84315.toInt(), 0x00BF360C), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }
    private val soot = solidFill().apply { shader = softPuff(0xCC3E2723.toInt()) }
    private val fill = solidFill()
    private val ink = roundStroke()
    private val eyeGlow = solidFill().apply { shader = softPuff(0xFFFFB300.toInt()) }
    private val tooth = Path()
    private val mouth = Path()

    override val stillAfterS: Float get() = 0.35f

    private fun size(scene: BumpScene) = max(scene.unit * 2.8f, scene.restHalf * 1.1f)
    private fun out(scene: BumpScene) = ButtonBump.smootherstep(scene.presence.coerceIn(0f, 1f))

    override fun press(scene: BumpScene, atLimit: Boolean) {
        breath = if (atLimit) 0.3f else 0.75f
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        time += dt
        val d = scene.density
        val s = size(scene)
        val headX = (out(scene) - 1f) * s * 1.35f
        ButtonBump.stepSpring(jaw, if (breath > 0f) 1f else 0f, 260f, 0.6f, dt)
        if (breath > 0f) {
            breath -= dt
            emitClock -= dt
            while (emitClock <= 0f) {
                emitClock += 1f / 170f
                val p = flames.spawn() ?: break
                val angle = rnd(-0.17f, 0.17f) + jaw.value * 0.1f
                val speed = rnd(280f, 460f) * d
                p.x = headX + s * 1.12f
                p.y = scene.centre + s * 0.12f
                p.vx = cos(angle) * speed
                p.vy = sin(angle) * speed
                p.life = rnd(0.45f, 0.85f)
                p.size = rnd(2.5f, 3.5f) * d
                p.tx = rnd(12f, 18f) * d
                p.kind = if (random.nextFloat() < 0.1f) 1 else 0
            }
        }
        if (out(scene) > 0.6f) {
            smokeClock -= dt
            if (smokeClock <= 0f) {
                smokeClock = rnd(0.25f, 0.45f)
                smoke.emit(headX + s * 1.13f, scene.centre - s * 0.13f, rnd(20f, 40f) * d, rnd(-12f, 12f) * d, 2f * d, rnd(7f, 10f) * d, 1.1f, 0.45f)
            }
        }
        val drag = exp(-1.3f * dt)
        var alive = false
        for (p in flames.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.life) {
                p.alive = false
                continue
            }
            // Fire rises, and flickers as it does.
            p.vx = p.vx * drag + (-scene.gravityX * 170f + rnd(-60f, 60f)) * d * dt
            p.vy = p.vy * drag + (-scene.gravityY * 170f + rnd(-60f, 60f)) * d * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            alive = true
        }
        alive = smoke.step(dt, 1f, -scene.gravityX * 40f * d, -scene.gravityY * 40f * d) || alive
        return alive || breath > 0f || jaw.value > 0.01f
    }

    /** The head and jaw drawn at [s] pixels a unit, once. Bounds of the picture, in units: x -0.65..1.35, y -1.2..0.6. */
    private fun ensureArt(s: Float) {
        if (abs(art - s) < 0.5f && headArt != null) return
        art = s
        val w = (2f * s).roundToInt().coerceAtLeast(2)
        val h = (1.8f * s).roundToInt().coerceAtLeast(2)
        headArt = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { paintHead(Canvas(it), s) }
        jawArt = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { paintJaw(Canvas(it), s) }
    }

    private fun paintHead(c: Canvas, s: Float) {
        c.translate(0.65f * s, 1.2f * s)
        c.scale(s, s)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // A second horn behind, darker.
        val horn = Path().apply {
            moveTo(0.14f, -0.56f)
            cubicTo(0f, -0.86f, -0.26f, -1.04f, -0.62f, -1.1f)
            cubicTo(-0.3f, -0.92f, -0.05f, -0.75f, 0.34f, -0.57f)
            close()
        }
        c.save()
        c.translate(-0.12f, 0.04f)
        c.scale(0.85f, 0.85f)
        p.shader = LinearGradient(0.3f, -0.6f, -0.6f, -1.1f, 0xFF8C7B5E.toInt(), 0xFF4E4232.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(horn, p)
        c.restore()
        // Spines down the back of the neck.
        p.shader = null
        p.color = 0xFF4A0B0B.toInt()
        for ((x, y, k) in listOf(Triple(-0.5f, -0.44f, 0.16f), Triple(-0.3f, -0.5f, 0.2f), Triple(-0.08f, -0.58f, 0.18f))) {
            c.drawPath(Path().apply { moveTo(x - 0.08f, y + 0.04f); lineTo(x - 0.02f, y - k); lineTo(x + 0.09f, y + 0.02f); close() }, p)
        }
        // The skull and upper jaw.
        val head = Path().apply {
            moveTo(-0.65f, -0.4f)
            cubicTo(-0.25f, -0.5f, 0.05f, -0.62f, 0.3f, -0.58f)
            cubicTo(0.48f, -0.55f, 0.58f, -0.46f, 0.66f, -0.4f)
            cubicTo(0.8f, -0.36f, 0.98f, -0.3f, 1.12f, -0.2f)
            cubicTo(1.2f, -0.15f, 1.24f, -0.08f, 1.22f, -0.02f)
            lineTo(1.1f, 0.02f)
            cubicTo(0.9f, 0.03f, 0.65f, 0.05f, 0.4f, 0.08f)
            cubicTo(0.25f, 0.1f, 0.1f, 0.2f, -0.05f, 0.28f)
            lineTo(-0.65f, 0.36f)
            close()
        }
        p.shader = LinearGradient(0f, -0.62f, 0f, 0.36f, intArrayOf(0xFF420B0B.toInt(), 0xFF8E1B1B.toInt(), 0xFFC0492B.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(head, p)
        // Scales, row on row, over the whole head.
        c.save()
        c.clipPath(head)
        val scale = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.012f
            color = 0x55250404
        }
        var row = 0
        var y = -0.62f
        while (y < 0.4f) {
            var x = -0.65f + if (row % 2 == 0) 0f else 0.035f
            while (x < 1.25f) {
                c.drawArc(RectF(x - 0.035f, y - 0.035f, x + 0.035f, y + 0.035f), 10f, 160f, false, scale)
                x += 0.07f
            }
            y += 0.055f
            row++
        }
        // Light along the top of the snout, shadow under the brow.
        p.shader = LinearGradient(0f, -0.4f, 0f, -0.15f, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawPath(head, p)
        c.restore()
        // The main horn, ridged.
        p.shader = LinearGradient(0.3f, -0.57f, -0.6f, -1.1f, 0xFFF4E8CC.toInt(), 0xFF9C8A68.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(horn, p)
        val ridge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.018f
            color = 0x806B5B40.toInt()
        }
        for (k in 1..4) {
            val f = k / 5f
            c.drawLine(0.24f - 0.78f * f, -0.57f - 0.5f * f + 0.05f, 0.14f - 0.7f * f, -0.56f - 0.55f * f - 0.06f, ridge)
        }
        // The heavy brow, the cheek plate, the nostril.
        p.shader = null
        p.color = 0xFF2E0606.toInt()
        c.drawPath(Path().apply {
            moveTo(0.36f, -0.47f)
            quadTo(0.55f, -0.56f, 0.74f, -0.42f)
            quadTo(0.55f, -0.47f, 0.4f, -0.42f)
            close()
        }, p)
        p.color = 0x553A0606
        c.drawPath(Path().apply {
            moveTo(0.05f, -0.1f)
            quadTo(0.3f, -0.18f, 0.45f, -0.05f)
            quadTo(0.25f, 0.05f, 0.05f, 0.12f)
            close()
        }, p)
        p.color = 0xFF1A0303.toInt()
        c.save()
        c.rotate(-20f, 1.12f, -0.11f)
        c.drawOval(RectF(1.08f, -0.13f, 1.16f, -0.09f), p)
        c.restore()
        // Spikes on the cheek, sweeping back.
        p.color = 0xFF5C0E0E.toInt()
        for ((x, y) in listOf(0.0f to 0.24f, -0.18f to 0.3f)) {
            c.drawPath(Path().apply { moveTo(x + 0.06f, y - 0.04f); lineTo(x - 0.2f, y + 0.12f); lineTo(x + 0.08f, y + 0.05f); close() }, p)
        }
    }

    private fun paintJaw(c: Canvas, s: Float) {
        c.translate(0.65f * s, 1.2f * s)
        c.scale(s, s)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val jawPath = Path().apply {
            moveTo(0.32f, 0.08f)
            cubicTo(0.6f, 0.07f, 0.9f, 0.06f, 1.06f, 0.07f)
            cubicTo(1.08f, 0.12f, 1f, 0.2f, 0.85f, 0.24f)
            cubicTo(0.6f, 0.3f, 0.35f, 0.34f, 0.1f, 0.36f)
            lineTo(-0.65f, 0.45f)
            lineTo(-0.65f, 0.3f)
            close()
        }
        p.shader = LinearGradient(0f, 0.06f, 0f, 0.42f, 0xFF9A2A1A.toInt(), 0xFFE08A52.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(jawPath, p)
        // Belly scales under the jaw: broad bands.
        c.save()
        c.clipPath(jawPath)
        val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.014f
            color = 0x66631A0E
        }
        var x = -0.6f
        while (x < 1.05f) {
            c.drawLine(x, 0.12f, x - 0.05f, 0.45f, band)
            x += 0.09f
        }
        c.restore()
        // Its teeth, pointing up.
        p.shader = null
        p.color = 0xFFFFF8E7.toInt()
        for (tx in floatArrayOf(0.55f, 0.69f, 0.83f, 0.97f)) {
            c.drawPath(Path().apply { moveTo(tx - 0.03f, 0.08f); lineTo(tx + 0.03f, 0.08f); lineTo(tx, 0f); close() }, p)
        }
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        val s = size(scene)
        val o = out(scene)
        smoke.draw(canvas)
        if (o > 0.01f) {
            ensureArt(s)
            val open = jaw.value.coerceIn(0f, 1.2f) * 26f
            canvas.save()
            canvas.translate((o - 1f) * s * 1.35f, scene.centre)
            // The inside of the mouth, its tongue, and the glow of the fire in its throat.
            if (open > 1f) {
                canvas.save()
                canvas.scale(s, s)
                mouth.reset()
                mouth.moveTo(0.32f, 0.08f)
                mouth.lineTo(1.12f, 0.03f)
                val a = Math.toRadians(open.toDouble())
                mouth.lineTo(0.32f + (0.76f * cos(a)).toFloat(), 0.08f + (0.76f * sin(a)).toFloat())
                mouth.close()
                fill.color = 0xFF2A0505.toInt()
                canvas.drawPath(mouth, fill)
                eyeGlow.alpha = (255 * jaw.value.coerceIn(0f, 1f)).toInt()
                canvas.save()
                canvas.translate(0.7f, 0.14f)
                canvas.scale(0.45f, 0.3f)
                canvas.drawCircle(0f, 0f, 1f, eyeGlow)
                canvas.restore()
                canvas.restore()
            }
            canvas.save()
            canvas.rotate(open, 0.32f * s, 0.08f * s)
            canvas.drawBitmap(jawArt!!, -0.65f * s, -1.2f * s, artPaint)
            canvas.restore()
            canvas.drawBitmap(headArt!!, -0.65f * s, -1.2f * s, artPaint)
            // Its upper teeth, over the lip.
            canvas.save()
            canvas.scale(s, s)
            fill.color = 0xFFFFF8E7.toInt()
            for ((i, tx) in floatArrayOf(0.5f, 0.62f, 0.74f, 0.86f, 0.98f, 1.08f).withIndex()) {
                val len = if (i % 2 == 0) 0.085f else 0.06f
                tooth.reset()
                tooth.moveTo(tx - 0.028f, 0.03f)
                tooth.lineTo(tx + 0.028f, 0.03f)
                tooth.lineTo(tx, 0.03f + len)
                tooth.close()
                canvas.drawPath(tooth, fill)
            }
            // The eye, smouldering, brighter as it breathes.
            eyeGlow.alpha = (150 + 105 * jaw.value.coerceIn(0f, 1f)).toInt()
            canvas.save()
            canvas.translate(0.57f, -0.36f)
            canvas.scale(0.16f, 0.11f)
            canvas.drawCircle(0f, 0f, 1f, eyeGlow)
            canvas.restore()
            canvas.save()
            canvas.rotate(-12f, 0.57f, -0.36f)
            fill.color = 0xFFFFC107.toInt()
            canvas.drawOval(0.495f, -0.4f, 0.645f, -0.32f, fill)
            fill.color = 0xFF120202.toInt()
            canvas.drawOval(0.558f, -0.398f, 0.582f, -0.322f, fill)
            fill.color = 0xCCFFFFFF.toInt()
            canvas.drawCircle(0.6f, -0.375f, 0.012f, fill)
            canvas.restore()
            canvas.restore()
            canvas.restore()
        }
        // The fire: soft flames, white hot at the mouth, reddening, and soot at the end of it.
        for (p in flames.items) {
            if (!p.alive) continue
            val t = p.age / p.life
            val r = p.size + (p.tx - p.size) * easeOut(t)
            val paint = when {
                p.kind == 1 -> hot
                t < 0.45f -> hot
                t < 0.8f -> cool
                else -> soot
            }
            val a = when {
                p.kind == 1 -> 1f - t
                t < 0.45f -> 1f
                t < 0.8f -> 0.9f
                else -> 0.6f * (1f - t) / 0.2f
            }
            paint.alpha = (a.coerceIn(0f, 1f) * 255f).roundToInt()
            canvas.save()
            canvas.translate(p.x, p.y)
            val rr = if (p.kind == 1) r * 0.25f else r
            canvas.scale(rr, rr)
            canvas.drawCircle(0f, 0f, 1f, paint)
            canvas.restore()
        }
    }

    override fun clear() {
        flames.clear()
        smoke.clear()
        breath = 0f
        jaw.snapTo(0f)
    }
}

// ---- rocket launch ---------------------------------------------------------------------------------------

/**
 * A little rocket at every press: chubby and bright, a face at its porthole, it squashes down, bounces
 * up on a puff of cotton-wool cloud, and zooms away across the screen, curving upward, trailing round
 * puffs and twinkles.
 */
internal class RocketEffect(random: Random) : BumpEffect(random) {
    private class Ship {
        var alive = false
        var age = 0f
        var x = 0f
        var y = 0f
        var speed = 0f
        var heading = 0f
        var turn = 0f
        var puffClock = 0f
        var launched = false
    }

    private val ships = Array(3) { Ship() }
    private val clouds = ParticlePool(140)
    private val twinkles = ParticlePool(40)
    private val body = Path()
    private val nose = Path()
    private val fin = Path()
    private val flame = Path()
    private val star = Path()
    private val fill = solidFill()
    private val ink = roundStroke()
    private val bodyShade = solidFill().apply {
        shader = LinearGradient(0f, -0.24f, 0f, 0.24f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFF4E6.toInt(), 0xFFE9D6C2.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    private val flameShade = solidFill().apply {
        shader = LinearGradient(-0.5f, 0f, -1.1f, 0f, intArrayOf(0xFFFFF59D.toInt(), 0xFFFFB74D.toInt(), 0xFFFF7043.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }

    init {
        // A chubby rocket in unit terms, nose towards +x, about one long.
        body.moveTo(-0.46f, -0.2f)
        body.cubicTo(-0.1f, -0.25f, 0.25f, -0.23f, 0.42f, -0.1f)
        body.cubicTo(0.52f, -0.04f, 0.52f, 0.04f, 0.42f, 0.1f)
        body.cubicTo(0.25f, 0.23f, -0.1f, 0.25f, -0.46f, 0.2f)
        body.quadTo(-0.52f, 0f, -0.46f, -0.2f)
        body.close()
        nose.moveTo(0.24f, -0.2f)
        nose.cubicTo(0.36f, -0.16f, 0.45f, -0.1f, 0.5f, 0f)
        nose.cubicTo(0.45f, 0.1f, 0.36f, 0.16f, 0.24f, 0.2f)
        nose.quadTo(0.29f, 0f, 0.24f, -0.2f)
        nose.close()
        fin.moveTo(-0.28f, -0.2f)
        fin.quadTo(-0.42f, -0.46f, -0.6f, -0.42f)
        fin.quadTo(-0.6f, -0.24f, -0.46f, -0.14f)
        fin.close()
        flame.moveTo(-0.5f, -0.12f)
        flame.cubicTo(-0.75f, -0.14f, -0.95f, -0.06f, -1.1f, 0f)
        flame.cubicTo(-0.95f, 0.06f, -0.75f, 0.14f, -0.5f, 0.12f)
        flame.close()
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4f
            val r = if (k % 2 == 0) 1f else 0.35f
            if (k == 0) star.moveTo(cos(a) * r, sin(a) * r) else star.lineTo(cos(a) * r, sin(a) * r)
        }
        star.close()
    }

    override val stillAfterS: Float get() = 0.6f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        val s = ships.firstOrNull { !it.alive } ?: return
        s.alive = true
        s.age = 0f
        s.launched = false
        s.x = scene.restDepth * 0.3f + scene.unit * 0.4f
        s.y = scene.centre + scene.restHalf * rnd(-0.2f, 0.2f)
        s.heading = 0f
        s.speed = 30f * scene.density
        val up = atan2(-scene.gravityY, -scene.gravityX)
        s.turn = if (abs(up) > 3f) 0.6f else up.coerceIn(-0.6f, 0.6f)
    }

    private fun cloud(x: Float, y: Float, vx: Float, vy: Float, from: Float, to: Float, life: Float) {
        val p = clouds.spawn() ?: return
        p.x = x
        p.y = y
        p.vx = vx
        p.vy = vy
        p.size = from
        p.tx = to
        p.life = life
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        val d = scene.density
        val length = ROCKET_DP * d
        var alive = false
        for (s in ships) {
            if (!s.alive) continue
            s.age += dt
            if (s.age > IGNITION && !s.launched) {
                s.launched = true
                repeat(10) {
                    val side = if (it % 2 == 0) 1f else -1f
                    cloud(4f * d, s.y, rnd(10f, 40f) * d, side * rnd(50f, 150f) * d, 5f * d, rnd(11f, 17f) * d, rnd(1.0f, 1.5f))
                }
            }
            if (s.launched) {
                s.speed += 1000f * d * dt
                if (s.age > IGNITION + 0.1f) s.heading += (s.turn - s.heading) * min(1f, 1.6f * dt)
            }
            s.x += cos(s.heading) * s.speed * dt
            s.y += sin(s.heading) * s.speed * dt
            if (s.launched) {
                s.puffClock -= dt
                while (s.puffClock <= 0f) {
                    s.puffClock += 0.03f
                    val tx = s.x - cos(s.heading) * length * 0.8f
                    val ty = s.y - sin(s.heading) * length * 0.8f
                    cloud(tx, ty, rnd(-20f, 20f) * d, rnd(-20f, 20f) * d, 3f * d, rnd(7f, 11f) * d, rnd(0.8f, 1.2f))
                    if (random.nextFloat() < 0.35f) {
                        twinkles.spawn()?.let {
                            it.x = tx + rnd(-8f, 8f) * d
                            it.y = ty + rnd(-8f, 8f) * d
                            it.life = rnd(0.4f, 0.7f)
                            it.size = rnd(2f, 3.5f) * d
                            it.color = TWINKLES[random.nextInt(TWINKLES.size)]
                        }
                    }
                }
            }
            if (scene.offScreen(s.x, s.y, length * 2f) || s.age > 3.5f) s.alive = false else alive = true
        }
        val drag = exp(-2f * dt)
        for (pool in arrayOf(clouds, twinkles)) {
            for (p in pool.items) {
                if (!p.alive) continue
                p.age += dt
                if (p.age >= p.life) {
                    p.alive = false
                    continue
                }
                p.vx *= drag
                p.vy *= drag
                p.x += p.vx * dt
                p.y += p.vy * dt
                alive = true
            }
        }
        return alive
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        val length = ROCKET_DP * d
        // Cotton-wool clouds: round, shaded underneath, growing and then shrinking away.
        for (p in clouds.items) {
            if (!p.alive) continue
            val t = p.age / p.life
            val r = (p.size + (p.tx - p.size) * easeOut(t / 0.4f)) * if (t > 0.6f) (1f - (t - 0.6f) / 0.4f) else 1f
            if (r < 0.5f) continue
            fill.color = 0xFFDCDDE6.toInt()
            canvas.drawCircle(p.x, p.y + r * 0.12f, r, fill)
            fill.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(p.x - r * 0.08f, p.y - r * 0.06f, r * 0.9f, fill)
        }
        for (p in twinkles.items) {
            if (!p.alive) continue
            val t = p.age / p.life
            val k = sin(PI.toFloat() * t) * p.size
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(t * 90f)
            canvas.scale(k, k)
            fill.color = p.color
            canvas.drawPath(star, fill)
            canvas.restore()
        }
        for (s in ships) {
            if (!s.alive) continue
            // Squashed as it waits, stretched as it goes.
            val squash = when {
                s.age < IGNITION -> 1f - 0.12f * sin(PI.toFloat() * s.age / IGNITION)
                s.age < IGNITION + 0.25f -> 1f + 0.14f * sin(PI.toFloat() * (s.age - IGNITION) / 0.25f)
                else -> 1f
            }
            canvas.save()
            canvas.translate(s.x, s.y)
            canvas.rotate(Math.toDegrees(s.heading.toDouble()).toFloat())
            canvas.scale(length * squash, length / squash)
            if (s.age > 0.12f) {
                val f = 0.85f + 0.3f * random.nextFloat() + if (s.launched) 0.2f else 0f
                canvas.save()
                canvas.scale(f, 1f, -0.5f, 0f)
                canvas.drawPath(flame, flameShade)
                canvas.restore()
            }
            fill.color = 0xFF4ECDC4.toInt()
            canvas.drawPath(fin, fill)
            canvas.save()
            canvas.scale(1f, -1f)
            canvas.drawPath(fin, fill)
            canvas.restore()
            canvas.drawPath(body, bodyShade)
            fill.color = 0xFF4ECDC4.toInt()
            canvas.drawRect(-0.36f, -0.215f, -0.29f, 0.215f, fill)
            fill.color = 0xFFFF6B6B.toInt()
            canvas.drawPath(nose, fill)
            // The porthole, and a happy face in it.
            fill.color = 0xFFFFD166.toInt()
            canvas.drawCircle(0.04f, 0f, 0.125f, fill)
            fill.color = 0xFF1A535C.toInt()
            canvas.drawCircle(0.04f, 0f, 0.098f, fill)
            fill.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(0.015f, -0.03f, 0.016f, fill)
            canvas.drawCircle(0.015f, 0.03f, 0.016f, fill)
            ink.strokeWidth = 0.014f
            ink.color = 0xFFFFFFFF.toInt()
            canvas.drawArc(-0.01f, -0.04f, 0.11f, 0.04f, -60f, 120f, false, ink)
            fill.color = 0x66FFFFFF
            canvas.drawCircle(0.0f, -0.055f, 0.025f, fill)
            canvas.restore()
        }
    }

    override fun clear() {
        for (s in ships) s.alive = false
        clouds.clear()
        twinkles.clear()
    }

    private companion object {
        const val ROCKET_DP = 34f
        const val IGNITION = 0.28f
        val TWINKLES = intArrayOf(0xFFFFD166.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF9EC7.toInt(), 0xFF9BF6FF.toInt())
    }
}

// ---- wind ----------------------------------------------------------------------------------------------------

/** A gust at every press: streaks of wind whooshing out across the screen and curling, leaves tumbling in them. */
internal class WindEffect(random: Random) : BumpEffect(random) {
    private class Gust {
        var alive = false
        var age = 0f
        var life = 1f
        var width = 1f
        val path = Path()
    }

    private val gusts = Array(16) { Gust() }
    private val leaves = ParticlePool(44)
    private val measure = PathMeasure()
    private val segment = Path()
    private val leaf = Path()
    private val oval = RectF()
    private val stroke = roundStroke()
    private val fill = solidFill()

    init {
        leaf.moveTo(-1f, 0f)
        leaf.cubicTo(-0.5f, -0.55f, 0.45f, -0.5f, 1f, 0f)
        leaf.cubicTo(0.45f, 0.5f, -0.5f, 0.55f, -1f, 0f)
        leaf.close()
    }

    override val stillAfterS: Float get() = 0.28f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        repeat(if (atLimit) 3 else 6) { gust(scene) }
        repeat(if (atLimit) 2 else 5) { leafOut(scene) }
    }

    private fun gust(scene: BumpScene) {
        val g = gusts.firstOrNull { !it.alive } ?: return
        val d = scene.density
        g.alive = true
        g.age = 0f
        g.life = rnd(0.75f, 1.05f)
        g.width = rnd(0.9f, 1.6f) * d
        val x0 = scene.restDepth * 0.8f
        val y0 = scene.centre + scene.restHalf * rnd(-1.5f, 1.5f)
        val length = scene.reach * rnd(0.55f, 0.85f)
        val amp = rnd(8f, 18f) * d
        val waves = rnd(1.2f, 2.2f)
        val phase = rnd(0f, 6.28f)
        g.path.reset()
        g.path.moveTo(x0, y0)
        var x = x0
        var y = y0
        for (i in 1..24) {
            val k = i / 24f
            x = x0 + length * k
            y = y0 + amp * sin(2f * PI.toFloat() * waves * k + phase) * k
            g.path.lineTo(x, y)
        }
        val r = rnd(7f, 13f) * d
        val dir = if (random.nextBoolean()) 1f else -1f
        oval.set(x - r, y - r + dir * r, x + r, y + r + dir * r)
        g.path.arcTo(oval, -90f * dir, 300f * dir, false)
    }

    private fun leafOut(scene: BumpScene) {
        val p = leaves.spawn() ?: return
        val d = scene.density
        p.x = scene.restDepth * 0.9f
        p.y = scene.centre + scene.restHalf * rnd(-0.6f, 0.6f)
        val a = rnd(-0.5f, 0.5f)
        val speed = rnd(220f, 400f) * d
        p.vx = cos(a) * speed
        p.vy = sin(a) * speed
        p.spin = rnd(-540f, 540f)
        p.angle = rnd(0f, 360f)
        p.rate = rnd(2f, 4f)
        p.phase = rnd(0f, 6.28f)
        p.size = rnd(4f, 6.5f) * d
        p.color = LEAVES[random.nextInt(LEAVES.size)]
        p.life = rnd(1.4f, 2f)
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        val d = scene.density
        var alive = false
        for (g in gusts) {
            if (!g.alive) continue
            g.age += dt
            if (g.age >= g.life) g.alive = false else alive = true
        }
        val drag = exp(-1.6f * dt)
        for (p in leaves.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.life || scene.offScreen(p.x, p.y, 20f * d)) {
                p.alive = false
                continue
            }
            p.vx = p.vx * drag + scene.gravityX * 90f * d * dt
            p.vy = p.vy * drag + scene.gravityY * 90f * d * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.angle += p.spin * dt
            p.phase += p.rate * 6.283f * dt
            alive = true
        }
        return alive
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        for (g in gusts) {
            if (!g.alive) continue
            val t = g.age / g.life
            measure.setPath(g.path, false)
            val length = measure.length
            val head = length * easeOut(t)
            val tail = length * easeOut(((t - 0.18f) / 0.82f).coerceAtLeast(0f))
            segment.reset()
            if (!measure.getSegment(tail, head, segment, true)) continue
            val a = if (t < 0.8f) 1f else (1f - t) / 0.2f
            stroke.strokeWidth = g.width * 2.4f
            stroke.color = fade(0xFFFFFFFF.toInt(), 0.16f * a)
            canvas.drawPath(segment, stroke)
            stroke.strokeWidth = g.width
            stroke.color = fade(0xFFFFFFFF.toInt(), 0.85f * a)
            canvas.drawPath(segment, stroke)
        }
        for (p in leaves.items) {
            if (!p.alive) continue
            val t = p.age / p.life
            val a = if (t < 0.75f) 1f else (1f - t) / 0.25f
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.angle)
            canvas.scale(p.size, p.size * max(abs(cos(p.phase)), 0.2f))
            fill.color = fade(p.color, a)
            canvas.drawPath(leaf, fill)
            fill.color = fade(blend(p.color, 0xFF000000.toInt(), 0.3f), a)
            canvas.drawRect(-0.85f, -0.04f, 0.85f, 0.04f, fill)
            canvas.restore()
        }
    }

    override fun clear() {
        for (g in gusts) g.alive = false
        leaves.clear()
    }

    private companion object {
        val LEAVES = intArrayOf(0xFF7CB342.toInt(), 0xFF9CCC65.toInt(), 0xFFFFA726.toInt(), 0xFFFFCA28.toInt(), 0xFF8D6E63.toInt())
    }
}

// ---- aerosol spray ---------------------------------------------------------------------------------------------

/**
 * An aerosol can at every press: its cap pressed down and a fine jet hissing out of the nozzle — a cone
 * of tiny droplets and a soft cloud of coloured mist drifting from it — the next colour each time.
 */
internal class SprayEffect(random: Random) : BumpEffect(random) {
    private val droplets = ParticlePool(420)
    private val mist = ParticlePool(110)
    private var spraying = 0f
    private var dropClock = 0f
    private var mistClock = 0f
    private var colour = 0
    private var next = 0
    private val mistPaints = Array(PALETTE.size) { i -> solidFill().apply { shader = softPuff(PALETTE[i]) } }
    private val dot = solidFill()
    private val fill = solidFill()

    override val stillAfterS: Float get() = 0.3f

    private fun nozzleX(scene: BumpScene) = scene.depth + 4f * scene.density

    override fun press(scene: BumpScene, atLimit: Boolean) {
        spraying = if (atLimit) 0.22f else 0.55f
        colour = next++ % PALETTE.size
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        val d = scene.density
        val nx = nozzleX(scene)
        if (spraying > 0f) {
            spraying -= dt
            dropClock -= dt
            while (dropClock <= 0f) {
                dropClock += 1f / 480f
                val p = droplets.spawn() ?: break
                val a = rnd(-0.22f, 0.22f)
                val speed = rnd(400f, 760f) * d
                p.x = nx
                p.y = scene.centre
                p.vx = cos(a) * speed
                p.vy = sin(a) * speed
                p.life = rnd(0.4f, 0.8f)
                p.size = rnd(0.9f, 1.9f) * d
                p.color = PALETTE[colour]
            }
            mistClock -= dt
            while (mistClock <= 0f) {
                mistClock += 1f / 60f
                val p = mist.spawn() ?: break
                val a = rnd(-0.2f, 0.2f)
                val speed = rnd(150f, 280f) * d
                p.x = nx
                p.y = scene.centre
                p.vx = cos(a) * speed
                p.vy = sin(a) * speed
                p.size = 5f * d
                p.tx = rnd(40f, 62f) * d
                p.life = rnd(1.4f, 2.1f)
                p.mark = rnd(0.2f, 0.3f)
                p.kind = colour
            }
        }
        var alive = spraying > 0f
        val dropDrag = exp(-3f * dt)
        for (p in droplets.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.life || scene.offScreen(p.x, p.y, 4f * d)) {
                p.alive = false
                continue
            }
            p.vx = p.vx * dropDrag + scene.gravityX * 120f * d * dt
            p.vy = p.vy * dropDrag + scene.gravityY * 120f * d * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            alive = true
        }
        val mistDrag = exp(-2.2f * dt)
        for (p in mist.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.life) {
                p.alive = false
                continue
            }
            p.vx = p.vx * mistDrag + scene.gravityX * 15f * d * dt
            p.vy = p.vy * mistDrag + scene.gravityY * 15f * d * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            alive = true
        }
        return alive
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        for (p in mist.items) {
            if (!p.alive) continue
            val t = p.age / p.life
            val r = p.size + (p.tx - p.size) * easeOut(t)
            val paint = mistPaints[p.kind]
            paint.alpha = (p.mark * (1f - t).pow(1.2f) * 255f).roundToInt()
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.scale(r, r)
            canvas.drawCircle(0f, 0f, 1f, paint)
            canvas.restore()
        }
        for (p in droplets.items) {
            if (!p.alive) continue
            val t = p.age / p.life
            dot.color = fade(p.color, 0.9f * (1f - t))
            canvas.drawCircle(p.x, p.y, p.size, dot)
        }
        // The can's cap and nozzle, pressed while it sprays.
        if (scene.presence > 0.05f) {
            val press = if (spraying > 0f) 1.5f * d else 0f
            val x = scene.depth - press
            val h = 7f * d
            fill.color = 0xFFF5F5F5.toInt()
            canvas.drawRoundRect(x - 6f * d, scene.centre - h, x + 3f * d, scene.centre + h, 2.5f * d, 2.5f * d, fill)
            fill.color = 0xFFBDBDBD.toInt()
            canvas.drawRect(x - 6f * d, scene.centre - h, x - 4.5f * d, scene.centre + h, fill)
            fill.color = 0xFF424242.toInt()
            canvas.drawCircle(x + 2.2f * d, scene.centre, 1.2f * d, fill)
        }
    }

    override fun clear() {
        droplets.clear()
        mist.clear()
        spraying = 0f
    }

    private companion object {
        val PALETTE = intArrayOf(
            0xFF29B6F6.toInt(), 0xFFEC407A.toInt(), 0xFF66BB6A.toInt(), 0xFFFFCA28.toInt(),
            0xFFAB47BC.toInt(), 0xFFECEFF1.toInt(),
        )
    }
}
