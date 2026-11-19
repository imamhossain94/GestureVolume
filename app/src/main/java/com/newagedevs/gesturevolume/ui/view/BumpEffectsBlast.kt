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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The big ones: a volcano, a rocket, a gust of wind and a colour spray. For the volcano the
 * glass's edge is the ground, so it rises into the screen from the button, whichever edge it is on.
 */

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
        rumble = scene.power.coerceAtMost(1f)
        erupting = (if (atLimit) 0.25f else 0.6f) * scene.power
        force = scene.power
    }

    /** How hard the last press erupted: see [BumpScene.power]. */
    private var force = 1f

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
                val speed = rnd(220f, 470f) * d * (0.55f + 0.45f * force)
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

// ---- rocket launch ---------------------------------------------------------------------------------------

/**
 * A little rocket at every press: chubby and bright, a face at its porthole, it squashes down, lifts off
 * on a soft billow of smoke and zooms away into the screen — a different way every time, bending as it
 * goes — and leaves one long trail of cloud behind it, which spreads and slowly fades away.
 */
internal class RocketEffect(random: Random) : BumpEffect(random) {
    private class Ship {
        var alive = false
        var age = 0f
        var x = 0f
        var y = 0f
        var speed = 0f
        var heading = 0f

        /** The way it swings round to once it is off, and how much it bends after that, a second. */
        var turn = 0f
        var curve = 0f
        var launched = false

        /** Where its trail was last laid, so the trail is one line however fast it goes. */
        var trailX = 0f
        var trailY = 0f

        /** How hard it was launched: see [BumpScene.power]. */
        var power = 1f
    }

    private val ships = Array(4) { Ship() }

    /** One soft cloud: puffs laid so close along the way that they run together. */
    private val smoke = Puffs(640, softPuff(SMOKE))
    private val embers = ParticlePool(60)
    private val body = Path()
    private val nose = Path()
    private val fin = Path()
    private val flame = Path()
    private val fill = solidFill()
    private val ink = roundStroke()
    private val bodyShade = solidFill().apply {
        shader = LinearGradient(0f, -0.24f, 0f, 0.24f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFF4E6.toInt(), 0xFFE9D6C2.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    private val flameShade = solidFill().apply {
        shader = LinearGradient(-0.5f, 0f, -1.1f, 0f, intArrayOf(0xFFFFF59D.toInt(), 0xFFFFB74D.toInt(), 0xFFFF7043.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }
    private val glow = solidFill().apply { shader = softPuff(0xFFFFC46B.toInt()) }

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
        s.power = scene.power
        // Anywhere into the screen: up it, down it, or straight across, and curving one way or the other.
        s.turn = rnd(-TURN_MAX, TURN_MAX)
        s.curve = rnd(-0.5f, 0.5f)
        s.trailX = s.x
        s.trailY = s.y
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
                billow(scene, s)
            }
            if (s.launched) {
                s.speed += 1000f * d * dt * (0.55f + 0.45f * s.power)
                if (s.age > IGNITION + 0.1f) {
                    s.heading += (s.turn - s.heading) * min(1f, 2.2f * dt)
                    s.turn = (s.turn + s.curve * dt).coerceIn(-HEADING_MAX, HEADING_MAX)
                }
            }
            s.x += cos(s.heading) * s.speed * dt
            s.y += sin(s.heading) * s.speed * dt
            if (s.launched) trail(scene, s, length)
            if (scene.offScreen(s.x, s.y, length * 2f) || s.age > 3.5f) s.alive = false else alive = true
        }
        // The smoke lingers and spreads, rising a little; nothing pushes it on but the air.
        if (smoke.step(dt, 1.6f, -scene.gravityX * 6f * d, -scene.gravityY * 6f * d)) alive = true
        val drag = exp(-3f * dt)
        for (p in embers.items) {
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
        return alive
    }

    /** The round cloud it lifts off on, rolling out of the edge where it stood. */
    private fun billow(scene: BumpScene, s: Ship) {
        val d = scene.density
        val big = 0.75f + 0.25f * s.power
        repeat(12) {
            val a = rnd(-1.25f, 1.25f)
            val speed = rnd(25f, 85f) * d
            smoke.emit(
                rnd(4f, 12f) * d, s.y + rnd(-6f, 6f) * d,
                cos(a) * speed, sin(a) * speed,
                rnd(7f, 10f) * d, rnd(20f, 30f) * d * big, rnd(2.2f, 3.2f), rnd(0.2f, 0.3f),
            )
        }
    }

    /**
     * Smoke laid all along the way it went since the last frame, a puff every few dp, so the trail is
     * one soft line rather than a string of beads however fast the rocket is going; and an ember or two.
     */
    private fun trail(scene: BumpScene, s: Ship, length: Float) {
        val d = scene.density
        val tx = s.x - cos(s.heading) * length * 0.62f
        val ty = s.y - sin(s.heading) * length * 0.62f
        val dx = tx - s.trailX
        val dy = ty - s.trailY
        val gap = hypot(dx, dy)
        val spacing = TRAIL_SPACING_DP * d
        if (gap < spacing) return
        val steps = (gap / spacing).toInt().coerceAtMost(24)
        val big = 0.8f + 0.2f * s.power
        val backX = -cos(s.heading)
        val backY = -sin(s.heading)
        for (i in 1..steps) {
            val k = i / steps.toFloat()
            val x = s.trailX + dx * k
            val y = s.trailY + dy * k
            val back = rnd(10f, 40f) * d
            val aside = rnd(-14f, 14f) * d
            smoke.emit(
                x, y,
                backX * back - backY * aside, backY * back + backX * aside,
                rnd(5.5f, 7f) * d, rnd(15f, 24f) * d * big, rnd(2.4f, 3.4f), rnd(0.18f, 0.26f),
            )
            if (random.nextFloat() < 0.1f) {
                embers.spawn()?.let {
                    it.x = x + rnd(-3f, 3f) * d
                    it.y = y + rnd(-3f, 3f) * d
                    it.vx = backX * rnd(40f, 120f) * d + rnd(-25f, 25f) * d
                    it.vy = backY * rnd(40f, 120f) * d + rnd(-25f, 25f) * d
                    it.life = rnd(0.25f, 0.5f)
                    it.size = rnd(1.1f, 2f) * d
                    it.color = EMBERS[random.nextInt(EMBERS.size)]
                }
            }
        }
        s.trailX = tx
        s.trailY = ty
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        val length = ROCKET_DP * d
        smoke.draw(canvas)
        for (p in embers.items) {
            if (!p.alive) continue
            val t = p.age / p.life
            val r = p.size * (1f - 0.5f * t)
            glow.alpha = ((1f - t) * 140f).roundToInt()
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.scale(r * 3f, r * 3f)
            canvas.drawCircle(0f, 0f, 1f, glow)
            canvas.restore()
            fill.color = fade(p.color, 1f - t)
            canvas.drawCircle(p.x, p.y, r, fill)
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
        smoke.clear()
        embers.clear()
    }

    private companion object {
        const val ROCKET_DP = 34f
        const val IGNITION = 0.28f

        /** How far round from straight across it may turn, at most, in radians, and may ever be headed. */
        const val TURN_MAX = 1.15f
        const val HEADING_MAX = 1.42f

        /** How far apart the trail's puffs are laid, in dp: close enough to run together. */
        const val TRAIL_SPACING_DP = 4f

        val SMOKE = 0xFFF2F3F7.toInt()
        val EMBERS = intArrayOf(0xFFFFE082.toInt(), 0xFFFFB74D.toInt(), 0xFFFFF3C4.toInt())
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
        repeat(count(scene, if (atLimit) 3 else 6)) { gust(scene) }
        repeat(count(scene, if (atLimit) 2 else 5)) { leafOut(scene) }
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
        val length = scene.reach * rnd(0.55f, 0.85f) * (0.6f + 0.4f * scene.power)
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

// ---- colour spray ----------------------------------------------------------------------------------------------

/**
 * A spray of colour at every press: the can's cap pressed down and a fine jet of paint hissing out of
 * the nozzle — a cone of tiny droplets and a soft cloud of coloured mist drifting from it — in the
 * next bright colour each time.
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
        spraying = (if (atLimit) 0.22f else 0.55f) * scene.power
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
        /** Paint colours, bright and clean, a different one each press. */
        val PALETTE = intArrayOf(
            0xFF29B6F6.toInt(), 0xFFEC407A.toInt(), 0xFF66BB6A.toInt(), 0xFFFFCA28.toInt(),
            0xFFAB47BC.toInt(), 0xFFFF7043.toInt(), 0xFF26C6DA.toInt(),
        )
    }
}
