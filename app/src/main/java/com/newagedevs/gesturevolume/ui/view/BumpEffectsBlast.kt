package com.newagedevs.gesturevolume.ui.view

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.PathMeasure
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
import kotlin.math.sin

/*
 * The big ones: a mushroom cloud, a volcano, smoke, a dragon's fire, a rocket, a gust of wind and a
 * spray of paper. For the first two the glass's edge is the ground, so they rise into the screen from
 * the button, whichever edge it is on.
 */

// ---- atom bomb ------------------------------------------------------------------------------------

/**
 * A mushroom cloud at every press: a flash at the button, a shockwave running out along the ground, and
 * a column of fire rising into the screen that rolls up into a cap and cools from white heat to smoke.
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
    private val fill = solidFill()
    private val ring = roundStroke()
    private val flashPaint = solidFill().apply { shader = softPuff(0xFFFFF4D6.toInt()) }

    override val stillAfterS: Float get() = 1.05f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        val b = blasts[next++ % blasts.size]
        b.alive = true
        b.age = 0f
        b.y = scene.centre + scene.restHalf * rnd(-0.15f, 0.15f)
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

    private fun drawBlast(canvas: Canvas, scene: BumpScene, b: Blast) {
        val d = scene.density
        val t = b.age
        val s = b.size
        val y = b.y
        val heat = (1f - t / 1.7f).coerceIn(0f, 1f)
        val alpha = if (t < 2f) 1f else (1f - (t - 2f) / (LIFE - 2f)).coerceIn(0f, 1f)
        val scale = (s / (180f * d)).coerceIn(0.45f, 1.3f)

        // The shockwave, running out along the ground.
        if (t < 0.9f) {
            val k = t / 0.9f
            ring.strokeWidth = (3f * (1f - k) + 0.6f) * d
            ring.color = fade(0xFFFFE9C4.toInt(), 0.75f * (1f - k))
            canvas.drawCircle(0f, y, s * 0.95f * easeOut(k), ring)
        }

        // The cloud as one layer, so it fades as a whole rather than puff by puff.
        canvas.saveLayerAlpha(null, (alpha * 255f).toInt())
        val spread = s * 0.42f * easeOut(t / 1.3f)
        for (i in -5..5) {
            val u = i / 5f
            val grow = min(1f, t * 3f)
            puff(canvas, (4f + 6f * (1f - abs(u))) * d * min(1f, t * 4f), y + u * spread, (7f + 5f * (1f - abs(u))) * d * scale * grow, heat * 0.6f)
        }
        val top = s * 0.5f * easeOut(t / 1.1f)
        val steps = (top / (6f * d)).toInt().coerceAtLeast(1)
        for (i in 0..steps) {
            val k = i / steps.toFloat()
            val wobble = sin(k * 9f + t * 5f + b.seed) * 1.5f * d
            puff(canvas, top * k, y + wobble, (6f + 4f * k) * d * scale, heat * (0.5f + 0.5f * k))
        }
        val cap = s * 0.2f * easeOut(t / 1.2f)
        if (cap > 1f) {
            val cx = top + cap * 0.35f
            puff(canvas, cx, y, cap * 0.85f, heat)
            for (i in 0 until 12) {
                // Puffs rolling round a squashed ring: the cap of the mushroom, turning over.
                val a = i * (2f * PI.toFloat() / 12) + t * 1.6f + b.seed
                puff(
                    canvas,
                    cx + cos(a) * cap * 0.55f,
                    y + sin(a) * cap * 1.15f,
                    cap * (0.42f + 0.08f * sin(a * 3f + t * 2f)),
                    heat * (0.75f + 0.25f * cos(a)),
                )
            }
        }
        canvas.restore()

        // The flash.
        if (t < 0.45f) {
            val k = t / 0.45f
            val r = s * (0.25f + 0.45f * k)
            flashPaint.alpha = (255f * (1f - k).pow(2)).toInt()
            canvas.save()
            canvas.translate(0f, y)
            canvas.scale(r, r)
            canvas.drawCircle(0f, 0f, 1f, flashPaint)
            canvas.restore()
        }
    }

    /** One puff of the cloud in three flat tones: a shadow towards the ground, the body, a highlight. */
    private fun puff(canvas: Canvas, x: Float, y: Float, r: Float, heat: Float) {
        if (r <= 0.5f) return
        val body = cloudColour(heat)
        fill.color = blend(body, 0xFF000000.toInt(), 0.32f)
        canvas.drawCircle(x - r * 0.12f, y + r * 0.08f, r, fill)
        fill.color = body
        canvas.drawCircle(x, y, r * 0.92f, fill)
        fill.color = blend(body, 0xFFFFFFFF.toInt(), 0.35f)
        canvas.drawCircle(x + r * 0.28f, y - r * 0.2f, r * 0.45f, fill)
    }

    /** White heat, through orange and a burnt red, to grey smoke. */
    private fun cloudColour(heat: Float): Int = when {
        heat > 0.75f -> blend(0xFFFFB13D.toInt(), 0xFFFFF3C4.toInt(), (heat - 0.75f) / 0.25f)
        heat > 0.45f -> blend(0xFFE0582B.toInt(), 0xFFFFB13D.toInt(), (heat - 0.45f) / 0.3f)
        heat > 0.15f -> blend(0xFF8A6A5C.toInt(), 0xFFE0582B.toInt(), (heat - 0.15f) / 0.3f)
        else -> blend(0xFF9E9EA6.toInt(), 0xFF8A6A5C.toInt(), heat / 0.15f)
    }

    override fun clear() {
        for (b in blasts) b.alive = false
    }

    private companion object {
        const val LIFE = 2.8f
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

    private fun height(scene: BumpScene) = scene.restDepth * 3.4f * ButtonBump.smootherstep(scene.presence.coerceIn(0f, 1f))
    private fun base(scene: BumpScene) = scene.restHalf * 1.8f

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
            if (p.x < 0f || p.age > p.life) p.alive = false else alive = true
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
            canvas.restore()
            // An outline to stand it off any wallpaper, and the crater's lip lit from inside.
            canvas.save()
            canvas.translate(0f, cy)
            canvas.scale(h, b)
            outline.strokeWidth = 1.4f * d / min(h, b)
            outline.color = 0xFF2B1D17.toInt()
            canvas.drawPath(cone, outline)
            canvas.restore()
            outline.strokeWidth = 2f * d
            outline.color = fade(0xFFFFB74D.toInt(), 0.5f + 0.5f * glow)
            canvas.drawLine(h, cy - crater, h * 0.93f, cy, outline)
            canvas.drawLine(h * 0.93f, cy, h, cy + crater, outline)
            // The crater glowing, brighter as it erupts.
            val r = crater * (3f + 1.6f * glow)
            glowPaint.alpha = (255f * (0.35f + 0.6f * glow)).toInt()
            canvas.save()
            canvas.translate(h, cy)
            canvas.scale(r, r)
            canvas.drawCircle(0f, 0f, 1f, glowPaint)
            canvas.restore()
            // Lava running down its sides.
            if (glow > 0.08f) {
                for (side in intArrayOf(-1, 1)) {
                    val run = glow.coerceAtMost(1f)
                    // Down the slope, winding a little.
                    val path = lavaPath
                    path.reset()
                    path.moveTo(h * 0.97f, cy + side * crater * 0.6f)
                    for (k in 1..4) {
                        val f = k / 4f * run
                        val x = h * (0.97f - 0.55f * f)
                        val y = cy + side * (crater * 0.6f + (b * 0.6f - crater * 0.6f) * f) + sin(k * 2.1f + side) * 2.5f * d
                        path.lineTo(x, y)
                    }
                    lava.strokeWidth = 6f * d
                    lava.color = fade(0xFFFF6D00.toInt(), 0.35f * glow)
                    canvas.drawPath(path, lava)
                    lava.strokeWidth = 2.2f * d
                    lava.color = fade(0xFFFFC14D.toInt(), glow)
                    canvas.drawPath(path, lava)
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

/** The bump smoulders while it is out, and every press sends a billow of smoke rolling out and up. */
internal class SmokeEffect(random: Random) : BumpEffect(random) {
    override val loops: Boolean get() = true

    private val smoke = Puffs(80, softPuff(0xFFCFCFD8.toInt()))
    private var wisp = 0f

    override val stillAfterS: Float get() = 0.45f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        val d = scene.density
        repeat(if (atLimit) 4 else 9) {
            smoke.emit(
                scene.restDepth * 0.9f + rnd(0f, 3f) * d,
                scene.centre + scene.restHalf * rnd(-0.5f, 0.5f),
                rnd(40f, 130f) * d, rnd(-35f, 35f) * d,
                rnd(5f, 8f) * d, rnd(22f, 38f) * d, rnd(1.8f, 2.6f), rnd(0.55f, 0.75f),
            )
        }
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        val d = scene.density
        if (scene.presence > 0.5f) {
            wisp -= dt
            if (wisp <= 0f) {
                wisp = 0.14f
                smoke.emit(scene.depth * 0.9f, scene.centre + scene.half * rnd(-0.4f, 0.4f), rnd(20f, 50f) * d, rnd(-10f, 10f) * d, 3f * d, rnd(12f, 18f) * d, rnd(1.2f, 1.8f), 0.35f)
            }
        }
        // Rising: the opposite way to down, and drifting a little further in.
        return smoke.step(dt, 1.1f, (-scene.gravityX * 45f + 10f) * d, -scene.gravityY * 45f * d)
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) = smoke.draw(canvas)

    override fun clear() = smoke.clear()
}

// ---- dragon fire -------------------------------------------------------------------------------------------

/**
 * A dragon's head comes out of the edge in place of the bump, smoke curling from its nostrils; every
 * press, it opens its jaws and breathes a roaring stream of fire across the screen.
 */
internal class DragonEffect(random: Random) : BumpEffect(random) {
    override val drawsBump: Boolean get() = false
    override val loops: Boolean get() = true

    private val flames = ParticlePool(240)
    private val smoke = Puffs(30, softPuff(0xFF4A4146.toInt()))
    private val jaw = ButtonBump.Spring()
    private var breath = 0f
    private var emitClock = 0f
    private var smokeClock = 0f
    private var time = 0f
    private val skull = Path()
    private val lowerJaw = Path()
    private val horn = Path()
    private val spike = Path()
    private val mouth = Path()
    private val fill = solidFill()
    private val glow = solidFill().apply { shader = softPuff(0xFFFF9100.toInt()) }

    init {
        // The head facing +x in unit terms: the neck at the edge, the snout's tip at x = 1.12.
        skull.moveTo(-0.4f, -0.42f)
        skull.cubicTo(0.05f, -0.62f, 0.42f, -0.58f, 0.62f, -0.36f)
        skull.cubicTo(0.8f, -0.3f, 1f, -0.2f, 1.12f, -0.06f)
        skull.lineTo(1.12f, 0.04f)
        skull.lineTo(0.4f, 0.06f)
        skull.lineTo(-0.4f, 0.32f)
        skull.close()
        lowerJaw.moveTo(0.35f, 0.06f)
        lowerJaw.lineTo(1f, 0.1f)
        lowerJaw.cubicTo(0.95f, 0.24f, 0.6f, 0.32f, 0.3f, 0.34f)
        lowerJaw.lineTo(-0.4f, 0.44f)
        lowerJaw.lineTo(-0.4f, 0.2f)
        lowerJaw.close()
        horn.moveTo(0f, -0.48f)
        horn.quadTo(-0.2f, -0.92f, -0.5f, -0.98f)
        horn.quadTo(-0.2f, -0.78f, 0.2f, -0.5f)
        horn.close()
        spike.moveTo(-0.08f, 0f)
        spike.lineTo(0f, -0.18f)
        spike.lineTo(0.08f, 0f)
        spike.close()
    }

    override val stillAfterS: Float get() = 0.35f

    private fun size(scene: BumpScene) = max(scene.restDepth * 2.1f, scene.restHalf * 0.85f)
    private fun out(scene: BumpScene) = ButtonBump.smootherstep(scene.presence.coerceIn(0f, 1f))

    override fun press(scene: BumpScene, atLimit: Boolean) {
        breath = if (atLimit) 0.3f else 0.75f
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        time += dt
        val d = scene.density
        val s = size(scene)
        val headX = (out(scene) - 1f) * s * 1.3f
        ButtonBump.stepSpring(jaw, if (breath > 0f) 1f else 0f, 260f, 0.6f, dt)
        if (breath > 0f) {
            breath -= dt
            emitClock -= dt
            while (emitClock <= 0f) {
                emitClock += 1f / 150f
                val p = flames.spawn() ?: break
                val angle = rnd(-0.2f, 0.2f) + jaw.value * 0.08f
                val speed = rnd(260f, 440f) * d
                p.x = headX + s * 1.0f
                p.y = scene.centre + s * 0.1f
                p.vx = cos(angle) * speed
                p.vy = sin(angle) * speed
                p.life = rnd(0.45f, 0.8f)
                p.size = rnd(2.2f, 3.2f) * d
                p.tx = rnd(11f, 16f) * d
                p.kind = if (random.nextFloat() < 0.12f) 1 else 0
            }
        }
        if (out(scene) > 0.6f) {
            smokeClock -= dt
            if (smokeClock <= 0f) {
                smokeClock = rnd(0.25f, 0.45f)
                smoke.emit(headX + s * 1.02f, scene.centre - s * 0.08f, rnd(20f, 40f) * d, rnd(-12f, 12f) * d, 2f * d, rnd(7f, 10f) * d, 1.1f, 0.45f)
            }
        }
        val drag = exp(-1.4f * dt)
        var alive = false
        for (p in flames.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.life) {
                p.alive = false
                continue
            }
            // Fire rises.
            p.vx = p.vx * drag - scene.gravityX * 160f * d * dt
            p.vy = p.vy * drag - scene.gravityY * 160f * d * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            alive = true
        }
        alive = smoke.step(dt, 1f, -scene.gravityX * 40f * d, -scene.gravityY * 40f * d) || alive
        return alive || breath > 0f || jaw.value > 0.01f
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val s = size(scene)
        val o = out(scene)
        smoke.draw(canvas)
        if (o > 0.01f) {
            canvas.save()
            canvas.translate((o - 1f) * s * 1.3f, scene.centre)
            canvas.scale(s, s)
            val open = jaw.value.coerceIn(0f, 1.2f) * 24f
            // The inside of the mouth, glowing as it breathes.
            if (open > 1f) {
                mouth.reset()
                mouth.moveTo(0.35f, 0.06f)
                mouth.lineTo(1.1f, 0.04f)
                val a = Math.toRadians(open.toDouble())
                mouth.lineTo(0.35f + (0.65f * cos(a)).toFloat(), 0.06f + (0.65f * sin(a)).toFloat())
                mouth.close()
                fill.color = 0xFF3E0A0A.toInt()
                canvas.drawPath(mouth, fill)
                glow.alpha = (255 * jaw.value.coerceIn(0f, 1f)).toInt()
                canvas.save()
                canvas.translate(0.9f, 0.1f)
                canvas.scale(0.35f, 0.35f)
                canvas.drawCircle(0f, 0f, 1f, glow)
                canvas.restore()
            }
            canvas.save()
            canvas.rotate(open, 0.35f, 0.06f)
            fill.color = 0xFF8E1414.toInt()
            canvas.drawPath(lowerJaw, fill)
            canvas.restore()
            fill.color = 0xFFC62828.toInt()
            canvas.drawPath(skull, fill)
            // Teeth along the upper jaw.
            fill.color = 0xFFFFF8E1.toInt()
            for (x in floatArrayOf(0.62f, 0.78f, 0.94f)) {
                mouth.reset()
                mouth.moveTo(x - 0.04f, 0.05f)
                mouth.lineTo(x + 0.04f, 0.05f)
                mouth.lineTo(x, 0.15f)
                mouth.close()
                canvas.drawPath(mouth, fill)
            }
            // Spikes down the back, the horns, the eye and the nostril.
            fill.color = 0xFF7F1010.toInt()
            for ((x, y) in listOf(-0.3f to -0.47f, -0.1f to -0.53f, 0.12f to -0.56f)) {
                canvas.save()
                canvas.translate(x, y)
                canvas.drawPath(spike, fill)
                canvas.restore()
            }
            fill.color = 0xFFEDE0C4.toInt()
            canvas.drawPath(horn, fill)
            fill.color = 0xFFFFD54F.toInt()
            canvas.drawOval(RectF(0.42f, -0.4f, 0.62f, -0.26f), fill)
            fill.color = 0xFF1A1A1A.toInt()
            canvas.drawOval(RectF(0.5f, -0.39f, 0.54f, -0.27f), fill)
            canvas.drawOval(RectF(0.98f, -0.1f, 1.06f, -0.04f), fill)
            canvas.restore()
        }
        for (p in flames.items) {
            if (!p.alive) continue
            val t = p.age / p.life
            val r = p.size + (p.tx - p.size) * easeOut(t)
            val a = (1f - t).pow(0.8f)
            val colour = when {
                p.kind == 1 -> 0xFFFFE082.toInt()
                t < 0.12f -> blend(0xFFFFF7D1.toInt(), 0xFFFFD54F.toInt(), t / 0.12f)
                t < 0.4f -> blend(0xFFFFD54F.toInt(), 0xFFFF9800.toInt(), (t - 0.12f) / 0.28f)
                t < 0.7f -> blend(0xFFFF9800.toInt(), 0xFFE64A19.toInt(), (t - 0.4f) / 0.3f)
                else -> blend(0xFFE64A19.toInt(), 0xFF5D4037.toInt(), (t - 0.7f) / 0.3f)
            }
            fill.color = fade(colour, 0.28f * a)
            canvas.drawCircle(p.x, p.y, r * 1.6f, fill)
            fill.color = fade(colour, 0.9f * a)
            canvas.drawCircle(p.x, p.y, if (p.kind == 1) r * 0.25f else r, fill)
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
 * A rocket at every press: it noses out of the bump, ignites in a burst of smoke along the ground, and
 * climbs away across the screen, curving upward, on a flickering flame and a trail of smoke.
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
    private val smoke = Puffs(160, softPuff(0xFFE6E6EC.toInt()))
    private val body = Path()
    private val nose = Path()
    private val fin = Path()
    private val flame = Path()
    private val fill = solidFill()

    init {
        // The rocket in unit terms, nose towards +x, one long.
        body.moveTo(-0.5f, -0.13f)
        body.lineTo(0.18f, -0.13f)
        body.cubicTo(0.34f, -0.13f, 0.46f, -0.07f, 0.52f, 0f)
        body.cubicTo(0.46f, 0.07f, 0.34f, 0.13f, 0.18f, 0.13f)
        body.lineTo(-0.5f, 0.13f)
        body.close()
        nose.moveTo(0.26f, -0.115f)
        nose.cubicTo(0.37f, -0.11f, 0.46f, -0.06f, 0.52f, 0f)
        nose.cubicTo(0.46f, 0.06f, 0.37f, 0.11f, 0.26f, 0.115f)
        nose.close()
        fin.moveTo(-0.2f, -0.12f)
        fin.lineTo(-0.5f, -0.12f)
        fin.lineTo(-0.62f, -0.32f)
        fin.close()
    }

    override val stillAfterS: Float get() = 0.55f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        val s = ships.firstOrNull { !it.alive } ?: return
        s.alive = true
        s.age = 0f
        s.launched = false
        s.x = scene.restDepth * 0.2f
        s.y = scene.centre + scene.restHalf * rnd(-0.2f, 0.2f)
        s.heading = 0f
        s.speed = 40f * scene.density
        // Curving up the screen as it climbs, but never more than a third of a turn off straight in.
        val up = atan2(-scene.gravityY, -scene.gravityX)
        s.turn = up.coerceIn(-0.6f, 0.6f).let { if (abs(up) > 3f) 0.6f else it }
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
                // The launch: smoke billowing out along the ground.
                repeat(12) {
                    val side = if (it % 2 == 0) 1f else -1f
                    smoke.emit(4f * d, s.y, rnd(10f, 40f) * d, side * rnd(60f, 170f) * d, 6f * d, rnd(16f, 26f) * d, rnd(1.2f, 1.7f), 0.75f)
                }
            }
            if (s.launched) {
                s.speed += 1100f * d * dt
                if (s.age > IGNITION + 0.1f) s.heading += (s.turn - s.heading) * min(1f, 1.6f * dt)
            }
            s.x += cos(s.heading) * s.speed * dt
            s.y += sin(s.heading) * s.speed * dt
            if (s.age > 0.15f) {
                s.puffClock -= dt
                while (s.puffClock <= 0f) {
                    s.puffClock += 0.016f
                    val tx = s.x - cos(s.heading) * length * 0.62f
                    val ty = s.y - sin(s.heading) * length * 0.62f
                    smoke.emit(tx, ty, -cos(s.heading) * rnd(20f, 50f) * d + rnd(-15f, 15f) * d, -sin(s.heading) * rnd(20f, 50f) * d + rnd(-15f, 15f) * d, 3f * d, rnd(12f, 20f) * d, rnd(1.0f, 1.5f), 0.6f)
                }
            }
            if (s.x > scene.reach + 60f * d || s.y < -60f * d || s.y > scene.span + 60f * d || s.age > 3f) {
                s.alive = false
            } else {
                alive = true
            }
        }
        alive = smoke.step(dt, 1.3f, -scene.gravityX * 25f * d, -scene.gravityY * 25f * d) || alive
        return alive
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        val length = ROCKET_DP * d
        smoke.draw(canvas)
        for (s in ships) {
            if (!s.alive) continue
            canvas.save()
            // A shiver as it waits to go.
            val shake = if (!s.launched) sin(s.age * 90f) * 0.6f * d else 0f
            canvas.translate(s.x, s.y + shake)
            canvas.rotate(Math.toDegrees(s.heading.toDouble()).toFloat())
            canvas.scale(length, length)
            if (s.age > 0.15f) {
                val f = 0.3f + 0.18f * random.nextFloat() + if (s.launched) 0.15f else 0f
                flame.reset()
                flame.moveTo(-0.56f, -0.085f)
                flame.quadTo(-0.56f - f * 0.6f, -0.1f, -0.56f - f, 0f)
                flame.quadTo(-0.56f - f * 0.6f, 0.1f, -0.56f, 0.085f)
                flame.close()
                fill.color = 0xFFFF7A1A.toInt()
                canvas.drawPath(flame, fill)
                canvas.save()
                canvas.scale(0.6f, 0.55f, -0.56f, 0f)
                fill.color = 0xFFFFE57F.toInt()
                canvas.drawPath(flame, fill)
                canvas.restore()
            }
            fill.color = 0xFFE53935.toInt()
            canvas.drawPath(fin, fill)
            canvas.save()
            canvas.scale(1f, -1f)
            canvas.drawPath(fin, fill)
            canvas.restore()
            fill.color = 0xFF616161.toInt()
            canvas.drawRect(-0.57f, -0.08f, -0.49f, 0.08f, fill)
            fill.color = 0xFFF5F5F7.toInt()
            canvas.drawPath(body, fill)
            fill.color = 0xFFD5D5DB.toInt()
            canvas.drawRect(-0.5f, 0.05f, 0.2f, 0.13f, fill)
            fill.color = 0xFFE53935.toInt()
            canvas.drawPath(nose, fill)
            fill.color = 0xFFB0BEC5.toInt()
            canvas.drawCircle(0.05f, 0f, 0.075f, fill)
            fill.color = 0xFF29B6F6.toInt()
            canvas.drawCircle(0.05f, 0f, 0.055f, fill)
            canvas.restore()
        }
    }

    override fun clear() {
        for (s in ships) s.alive = false
        smoke.clear()
    }

    private companion object {
        const val ROCKET_DP = 30f
        const val IGNITION = 0.25f
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
        // A curl at the end, either way.
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
            if (p.age >= p.life) {
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

// ---- paper spray ---------------------------------------------------------------------------------------------

/** A spray of paper at every press: lined sheets and scraps flying out, turning over and see-sawing down. */
internal class PaperEffect(random: Random) : BumpEffect(random) {
    private val sheets = ParticlePool(64)
    private val fill = solidFill()
    private val line = solidFill()

    override val stillAfterS: Float get() = 0.3f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        val d = scene.density
        repeat(if (atLimit) 5 else 12) {
            val p = sheets.spawn() ?: return
            p.x = scene.restDepth * 0.9f
            p.y = scene.centre + scene.restHalf * rnd(-0.5f, 0.5f)
            val a = rnd(-1f, 1f) + scene.towards * 0.25f
            val speed = rnd(180f, 420f) * d
            p.vx = cos(a) * speed
            p.vy = sin(a) * speed
            p.kind = if (random.nextFloat() < 0.3f) 1 else 0
            p.size = (if (p.kind == 1) rnd(4f, 6f) else rnd(9f, 13f)) * d
            p.angle = rnd(0f, 360f)
            p.spin = rnd(-300f, 300f)
            p.rate = rnd(1.2f, 2.6f)
            p.phase = rnd(0f, 6.28f)
            p.mark = rnd(0f, 6.28f)
            p.life = rnd(2.2f, 3f)
        }
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        val d = scene.density
        val drag = exp(-2.2f * dt)
        val acrossX = -scene.gravityY
        val acrossY = scene.gravityX
        var alive = false
        for (p in sheets.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.life) {
                p.alive = false
                continue
            }
            // Falling the way paper does: see-sawing side to side.
            val sway = sin(p.age * 3f + p.mark) * 110f * d
            p.vx = p.vx * drag + (scene.gravityX * 140f * d + acrossX * sway) * dt
            p.vy = p.vy * drag + (scene.gravityY * 140f * d + acrossY * sway) * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.angle += p.spin * dt
            p.phase += p.rate * 6.283f * dt
            alive = true
        }
        return alive
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        for (p in sheets.items) {
            if (!p.alive) continue
            val t = p.age / p.life
            val a = if (t < 0.75f) 1f else (1f - t) / 0.25f
            val turn = cos(p.phase)
            val w = p.size * 0.5f
            val h = p.size * 0.65f
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.angle)
            canvas.scale(max(abs(turn), 0.12f), 1f)
            fill.color = fade(0xFF000000.toInt(), 0.14f * a)
            canvas.drawRect(-w + 1.5f * d, -h + 1.5f * d, w + 1.5f * d, h + 1.5f * d, fill)
            fill.color = fade(if (turn >= 0f) 0xFFFAFAF7.toInt() else 0xFFE4E4DE.toInt(), a)
            canvas.drawRect(-w, -h, w, h, fill)
            if (p.kind == 0 && turn >= 0f) {
                // Ruled lines and a margin.
                line.color = fade(0xFF90CAF9.toInt(), 0.9f * a)
                val gap = h * 2f / 7f
                for (i in 1..6) canvas.drawRect(-w, -h + gap * i - 0.25f * d, w, -h + gap * i + 0.25f * d, line)
                line.color = fade(0xFFEF9A9A.toInt(), 0.9f * a)
                canvas.drawRect(-w * 0.62f - 0.3f * d, -h, -w * 0.62f + 0.3f * d, h, line)
            }
            canvas.restore()
        }
    }

    override fun clear() = sheets.clear()
}
