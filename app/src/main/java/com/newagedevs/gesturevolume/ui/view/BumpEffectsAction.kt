package com.newagedevs.gesturevolume.ui.view

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.newagedevs.gesturevolume.utils.ButtonBump
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/*
 * A ninja's blade, a cracked screen, and a fish leaping out of the water along the edge.
 */

// ---- ninja blade ------------------------------------------------------------------------------------

/** A blade's slash at every press, the other way each time, and throwing stars spinning out across the screen. */
internal class NinjaEffect(random: Random) : BumpEffect(random) {
    private class Slash {
        var alive = false
        var age = 0f
        var y = 0f
        var dir = 1f
        var radius = 0f
    }

    private val slashes = Array(3) { Slash() }
    private val stars = ParticlePool(8)
    private val sparks = ParticlePool(40)
    private var flip = 1f
    private var next = 0
    private val crescent = Path()
    private val star = Path()
    private val fill = solidFill()
    private val edge = roundStroke()

    init {
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4f
            val r = if (k % 2 == 0) 1f else 0.3f
            if (k == 0) star.moveTo(cos(a) * r, sin(a) * r) else star.lineTo(cos(a) * r, sin(a) * r)
        }
        star.close()
    }

    override val stillAfterS: Float get() = 0.12f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        val d = scene.density
        val s = slashes[next++ % slashes.size]
        s.alive = true
        s.age = 0f
        s.y = scene.centre + scene.restHalf * rnd(-0.2f, 0.2f)
        s.dir = flip
        flip = -flip
        s.radius = min(scene.reach * 0.42f, scene.span * 0.3f)
        repeat(if (atLimit) 1 else 2) {
            val p = stars.spawn() ?: return@repeat
            p.x = scene.restDepth
            p.y = scene.centre + scene.restHalf * rnd(-0.3f, 0.3f)
            val a = rnd(-0.35f, 0.35f)
            val speed = rnd(420f, 600f) * d
            p.vx = cos(a) * speed
            p.vy = sin(a) * speed
            p.spin = 1500f
            p.size = 8f * d
            p.life = 2f
        }
        val endAngle = Math.toRadians((70f * s.dir).toDouble()).toFloat()
        repeat(8) {
            val p = sparks.spawn() ?: return@repeat
            p.x = scene.restDepth * 0.3f + cos(endAngle) * s.radius
            p.y = s.y + sin(endAngle) * s.radius
            val a = endAngle + rnd(-0.8f, 0.8f)
            val speed = rnd(120f, 300f) * d
            p.vx = cos(a) * speed
            p.vy = sin(a) * speed
            p.life = rnd(0.2f, 0.35f)
        }
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        var alive = false
        for (s in slashes) {
            if (!s.alive) continue
            s.age += dt
            if (s.age > SLASH_S) s.alive = false else alive = true
        }
        for (pool in arrayOf(stars, sparks)) {
            for (p in pool.items) {
                if (!p.alive) continue
                p.age += dt
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.angle += p.spin * dt
                if (p.age >= p.life || scene.offScreen(p.x, p.y, 30f * scene.density)) p.alive = false else alive = true
            }
        }
        return alive
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        for (s in slashes) {
            if (!s.alive) continue
            val grow = easeOut(s.age / 0.11f)
            val a = if (s.age < 0.11f) 1f else (1f - (s.age - 0.11f) / (SLASH_S - 0.11f)).coerceIn(0f, 1f)
            val cx = scene.restDepth * 0.3f
            val start = -70f * s.dir
            val sweep = 140f * s.dir * grow
            crescent.reset()
            val n = 24
            for (i in 0..n) {
                val k = i / n.toFloat()
                val ang = Math.toRadians((start + sweep * k).toDouble()).toFloat()
                val px = cx + cos(ang) * s.radius
                val py = s.y + sin(ang) * s.radius
                if (i == 0) crescent.moveTo(px, py) else crescent.lineTo(px, py)
            }
            for (i in n downTo 0) {
                val k = i / n.toFloat()
                val ang = Math.toRadians((start + sweep * k).toDouble()).toFloat()
                val r = s.radius * (1f - 0.14f * sin(PI.toFloat() * k * grow.coerceAtLeast(0.01f)))
                crescent.lineTo(cx + cos(ang) * r, s.y + sin(ang) * r)
            }
            crescent.close()
            fill.color = fade(0xFFE1F5FE.toInt(), 0.85f * a)
            canvas.drawPath(crescent, fill)
            edge.strokeWidth = 4f * d
            edge.color = fade(0xFF4FC3F7.toInt(), 0.3f * a)
            canvas.drawPath(crescent, edge)
            edge.strokeWidth = 1.2f * d
            edge.color = fade(0xFFFFFFFF.toInt(), a)
            canvas.drawPath(crescent, edge)
        }
        for (p in sparks.items) {
            if (!p.alive) continue
            val a = 1f - p.age / p.life
            edge.strokeWidth = 1.2f * d
            edge.color = fade(0xFFFFFFFF.toInt(), a)
            canvas.drawLine(p.x - p.vx * 0.02f, p.y - p.vy * 0.02f, p.x, p.y, edge)
        }
        for (p in stars.items) {
            if (!p.alive) continue
            for (ghost in 2 downTo 0) {
                canvas.save()
                canvas.translate(p.x - p.vx * 0.018f * ghost, p.y - p.vy * 0.018f * ghost)
                canvas.rotate(p.angle - ghost * 25f)
                canvas.scale(p.size, p.size)
                fill.color = fade(0xFFB0BEC5.toInt(), if (ghost == 0) 1f else 0.25f)
                canvas.drawPath(star, fill)
                if (ghost == 0) {
                    fill.color = 0xFF546E7A.toInt()
                    canvas.drawCircle(0f, 0f, 0.2f, fill)
                    fill.color = 0xFF263238.toInt()
                    canvas.drawCircle(0f, 0f, 0.1f, fill)
                }
                canvas.restore()
            }
        }
    }

    override fun clear() {
        for (s in slashes) s.alive = false
        stars.clear()
        sparks.clear()
    }

    private companion object {
        const val SLASH_S = 0.36f
    }
}

// ---- cracking display -------------------------------------------------------------------------------------

/**
 * The glass cracks at every press: a star of cracks shoots out from the button, branching, with rings
 * of cracks between them and shards catching the light. Gone again once the bump is.
 */
internal class CrackEffect(random: Random) : BumpEffect(random) {
    private class Pattern {
        var alive = false
        var age = 0f
        var fading = -1f
        var cx = 0f
        var cy = 0f
        val lines = ArrayList<FloatArray>()
        val shards = ArrayList<FloatArray>()
    }

    private val patterns = Array(3) { Pattern() }
    private var next = 0
    private val path = Path()
    private val shadow = roundStroke()
    private val shine = roundStroke()
    private val fill = solidFill()
    private val flash = solidFill().apply { shader = softPuff(0xFFFFFFFF.toInt()) }

    override val stillAfterS: Float get() = 0.3f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        val p = patterns[next++ % patterns.size]
        p.alive = true
        p.age = 0f
        p.fading = -1f
        p.cx = scene.restDepth * 0.75f
        p.cy = scene.centre + scene.restHalf * rnd(-0.25f, 0.25f)
        p.lines.clear()
        p.shards.clear()
        val radius = min(scene.reach * 0.95f, scene.span * 0.48f)
        val count = if (atLimit) 6 else 10
        val radial = ArrayList<FloatArray>()
        for (i in 0 until count) {
            val base = Math.toRadians(-85.0 + 170.0 * (i + 0.5) / count).toFloat() + rnd(-0.12f, 0.12f)
            val crack = jagged(p.cx, p.cy, base, radius * rnd(0.45f, 1f), 8)
            radial += crack
            p.lines += crack
            if (random.nextFloat() < 0.6f) {
                val k = 2 + random.nextInt(3)
                val side = if (random.nextBoolean()) 1f else -1f
                p.lines += jagged(crack[2 * k], crack[2 * k + 1], base + side * rnd(0.35f, 0.8f), radius * rnd(0.15f, 0.3f), 4)
            }
        }
        for (ring in floatArrayOf(0.22f, 0.45f)) {
            for (i in 0 until radial.size - 1) {
                if (random.nextFloat() > 0.7f) continue
                val a = pointAt(radial[i], p.cx, p.cy, radius * ring) ?: continue
                val b = pointAt(radial[i + 1], p.cx, p.cy, radius * ring) ?: continue
                val mx = (a[0] + b[0]) / 2f + rnd(-4f, 4f) * scene.density
                val my = (a[1] + b[1]) / 2f + rnd(-4f, 4f) * scene.density
                p.lines += floatArrayOf(a[0], a[1], mx, my, b[0], b[1])
            }
        }
        for (i in 0 until radial.size - 1 step 2) {
            val a = pointAt(radial[i], p.cx, p.cy, radius * 0.14f) ?: continue
            val b = pointAt(radial[i + 1], p.cx, p.cy, radius * 0.14f) ?: continue
            p.shards += floatArrayOf(p.cx, p.cy, a[0], a[1], b[0], b[1])
        }
    }

    private fun jagged(x0: Float, y0: Float, angle: Float, length: Float, segments: Int): FloatArray {
        val pts = FloatArray(2 * (segments + 1))
        pts[0] = x0
        pts[1] = y0
        var x = x0
        var y = y0
        val step = length / segments
        for (i in 1..segments) {
            val a = angle + rnd(-0.28f, 0.28f)
            x += cos(a) * step
            y += sin(a) * step
            pts[2 * i] = x
            pts[2 * i + 1] = y
        }
        return pts
    }

    private fun pointAt(crack: FloatArray, cx: Float, cy: Float, distance: Float): FloatArray? {
        for (i in 0 until crack.size / 2) {
            if (hypot(crack[2 * i] - cx, crack[2 * i + 1] - cy) >= distance) return floatArrayOf(crack[2 * i], crack[2 * i + 1])
        }
        return null
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        var alive = false
        for (p in patterns) {
            if (!p.alive) continue
            p.age += dt
            if (p.fading < 0f && scene.presence < 0.15f) p.fading = 0f
            if (p.fading >= 0f) p.fading += dt
            if (p.fading > FADE_S || p.age > 8f) p.alive = false else alive = true
        }
        return alive
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        for (p in patterns) {
            if (!p.alive) continue
            val a = if (p.fading < 0f) 1f else (1f - p.fading / FADE_S).coerceIn(0f, 1f)
            val grow = easeOut(p.age / 0.16f)
            for (s in p.shards) {
                path.reset()
                path.moveTo(s[0], s[1])
                path.lineTo(s[2], s[3])
                path.lineTo(s[4], s[5])
                path.close()
                fill.color = fade(0xFFFFFFFF.toInt(), 0.14f * a * grow)
                canvas.drawPath(path, fill)
            }
            for (line in p.lines) {
                path.reset()
                val n = line.size / 2
                val upTo = (n - 1) * grow
                path.moveTo(line[0], line[1])
                for (i in 1 until n) {
                    if (i <= upTo) {
                        path.lineTo(line[2 * i], line[2 * i + 1])
                    } else {
                        val f = upTo - (i - 1)
                        if (f > 0f) path.lineTo(line[2 * i - 2] + (line[2 * i] - line[2 * i - 2]) * f, line[2 * i - 1] + (line[2 * i + 1] - line[2 * i - 1]) * f)
                        break
                    }
                }
                canvas.save()
                canvas.translate(0.7f * d, 0.7f * d)
                shadow.strokeWidth = 2.2f * d
                shadow.color = fade(0xFF000000.toInt(), 0.45f * a)
                canvas.drawPath(path, shadow)
                canvas.restore()
                shine.strokeWidth = 0.9f * d
                shine.color = fade(0xFFFFFFFF.toInt(), 0.95f * a)
                canvas.drawPath(path, shine)
            }
            if (p.age < 0.3f) {
                val r = 26f * d
                flash.alpha = (230 * (1f - p.age / 0.3f)).toInt()
                canvas.save()
                canvas.translate(p.cx, p.cy)
                canvas.scale(r, r)
                canvas.drawCircle(0f, 0f, 1f, flash)
                canvas.restore()
            }
        }
    }

    override fun clear() {
        for (p in patterns) p.alive = false
    }

    private companion object {
        const val FADE_S = 0.5f
    }
}

// ---- water, for the fish ------------------------------------------------------------------------------

/** A strip of water along the edge, with a lapping surface, splashes and ripples: where the fish lives. */
internal class WaterStrip(private val random: Random) {
    private val drops = ParticlePool(90)
    private val ripples = ParticlePool(10)
    private val surface = Path()
    private val fill = solidFill()
    private val ink = roundStroke()
    private val oval = RectF()
    private var shaderDepth = -1f
    private var time = 0f

    fun depth(scene: BumpScene) = scene.unit * 0.9f * ButtonBump.smootherstep(scene.presence.coerceIn(0f, 1f))
    fun half(scene: BumpScene) = scene.span * 0.47f

    /** A splash where something breaks the surface at [y]. */
    fun splash(scene: BumpScene, y: Float, strength: Float) {
        val d = scene.density
        val x = depth(scene)
        repeat((12 * strength).toInt()) {
            val p = drops.spawn() ?: return@repeat
            p.x = x
            p.y = y + (random.nextFloat() - 0.5f) * 10f * d
            val a = (random.nextFloat() - 0.5f) * 1.6f
            val speed = (90f + random.nextFloat() * 200f) * d * strength
            p.vx = cos(a) * speed
            p.vy = sin(a) * speed
            p.size = (1.2f + random.nextFloat() * 1.6f) * d
            p.life = 1.2f
        }
        ripples.spawn()?.let {
            it.x = x
            it.y = y
            it.life = 0.8f
        }
    }

    fun step(scene: BumpScene, dt: Float): Boolean {
        time += dt
        val d = scene.density
        var alive = false
        val floor = depth(scene)
        for (p in drops.items) {
            if (!p.alive) continue
            p.age += dt
            p.vx -= 900f * d * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.x < floor * 0.6f || p.age > p.life) p.alive = false else alive = true
        }
        for (p in ripples.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age > p.life) p.alive = false else alive = true
        }
        return alive
    }

    fun draw(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        val x = depth(scene)
        if (x > 0.5f) {
            val h = half(scene)
            if (abs(shaderDepth - x) > 0.5f) {
                fill.shader = LinearGradient(0f, 0f, x, 0f, 0xD01565C0.toInt(), 0xA042A5F5.toInt(), Shader.TileMode.CLAMP)
                shaderDepth = x
            }
            surface.reset()
            surface.moveTo(-2f, scene.centre - h)
            val n = 28
            for (i in 0..n) {
                val k = i / n.toFloat()
                val y = scene.centre - h + 2f * h * k
                val taper = sin(PI.toFloat() * k)
                surface.lineTo(x * taper + sin(k * 14f + time * 5f) * 1.6f * d * taper, y)
            }
            surface.lineTo(-2f, scene.centre + h)
            surface.close()
            canvas.drawPath(surface, fill)
        }
        for (p in ripples.items) {
            if (!p.alive) continue
            val k = p.age / p.life
            ink.strokeWidth = 1.5f * d
            ink.color = fade(0xFFE3F2FD.toInt(), 0.8f * (1f - k))
            val r = 6f * d + 26f * d * easeOut(k)
            oval.set(p.x - r * 0.4f, p.y - r, p.x + r * 0.4f, p.y + r)
            canvas.drawArc(oval, -90f, 180f, false, ink)
        }
        val drop = solidFill()
        for (p in drops.items) {
            if (!p.alive) continue
            drop.color = fade(0xFFE1F5FE.toInt(), 0.9f)
            canvas.drawCircle(p.x, p.y, p.size, drop)
        }
    }

    fun clear() {
        drops.clear()
        ripples.clear()
    }
}

// ---- fish jump -------------------------------------------------------------------------------------------

/**
 * Water laps along the edge, and every press a chubby little goldfish leaps out of it — big shiny eyes,
 * rosy cheeks, its tail fluttering — arcs across with a happy wiggle, and dives back in with a splash.
 */
internal class FishEffect(random: Random) : BumpEffect(random) {
    override val drawsBump: Boolean get() = false
    override val loops: Boolean get() = true

    private val water = WaterStrip(random)
    private val fish = ParticlePool(4)
    private var side = 1f
    private var time = 0f
    private val body = Path()
    private val tail = Path()
    private val fin = Path()
    private val fill = solidFill()
    private val ink = roundStroke()
    private val bodyShade = solidFill().apply {
        shader = RadialGradient(0.25f, -0.3f, 1.3f, intArrayOf(0xFFFFD08A.toInt(), 0xFFFF9A3C.toInt(), 0xFFF46B1E.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }
    private val tailShade = solidFill().apply {
        shader = LinearGradient(-0.85f, 0f, -1.6f, 0f, 0xFFFF9A3C.toInt(), 0xFFFFE0B2.toInt(), Shader.TileMode.CLAMP)
    }

    init {
        // A round little fish facing +x, about two long.
        body.moveTo(-0.9f, 0f)
        body.cubicTo(-0.75f, -0.78f, 0.55f, -0.82f, 0.95f, -0.12f)
        body.cubicTo(1.03f, 0f, 1.03f, 0.04f, 0.95f, 0.14f)
        body.cubicTo(0.55f, 0.8f, -0.75f, 0.76f, -0.9f, 0f)
        body.close()
        tail.moveTo(-0.82f, 0f)
        tail.cubicTo(-1.15f, -0.72f, -1.62f, -0.68f, -1.58f, -0.38f)
        tail.cubicTo(-1.42f, -0.12f, -1.42f, 0.12f, -1.58f, 0.38f)
        tail.cubicTo(-1.62f, 0.68f, -1.15f, 0.72f, -0.82f, 0f)
        tail.close()
        fin.moveTo(-0.3f, -0.62f)
        fin.quadTo(0f, -1.05f, 0.32f, -0.66f)
        fin.close()
    }

    override val stillAfterS: Float get() = 0.4f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        val d = scene.density
        val f = fish.spawn() ?: return
        f.x = water.depth(scene) * 0.5f
        f.y = scene.centre + side * scene.restHalf * rnd(0.2f, 0.6f)
        f.vx = rnd(300f, 380f) * d * if (atLimit) 0.8f else 1f
        f.vy = -side * rnd(60f, 140f) * d
        side = -side
        f.life = 3f
        f.phase = rnd(0f, 6.28f)
        water.splash(scene, f.y, 0.8f)
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        time += dt
        val d = scene.density
        var alive = water.step(scene, dt)
        for (f in fish.items) {
            if (!f.alive) continue
            f.age += dt
            f.vx -= 700f * d * dt
            f.x += f.vx * dt
            f.y += f.vy * dt
            if (f.vx < 0f && f.x < water.depth(scene) * 0.6f) {
                water.splash(scene, f.y, 1f)
                f.alive = false
            } else if (f.age > f.life || scene.offScreen(f.x, f.y, 40f * d)) {
                f.alive = false
            } else {
                alive = true
            }
        }
        return alive || scene.presence > 0.01f
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        water.draw(canvas, scene)
        for (f in fish.items) {
            if (!f.alive) continue
            val s = 17f * d
            // A happy wiggle as it flies, and a stretch as it leaves the water.
            val wiggle = sin(time * 18f + f.phase) * 8f
            val stretch = 1f + 0.12f * (1f - (f.age / 0.25f).coerceIn(0f, 1f))
            canvas.save()
            canvas.translate(f.x, f.y)
            canvas.rotate(Math.toDegrees(atan2(f.vy, f.vx).toDouble()).toFloat() + wiggle)
            canvas.scale(s * stretch, s / stretch)
            canvas.save()
            canvas.rotate(sin(time * 24f + f.phase) * 16f, -0.82f, 0f)
            canvas.drawPath(tail, tailShade)
            ink.strokeWidth = 0.05f
            ink.color = 0x66FFFFFF
            canvas.drawLine(-0.9f, 0f, -1.45f, -0.4f, ink)
            canvas.drawLine(-0.9f, 0f, -1.48f, 0f, ink)
            canvas.drawLine(-0.9f, 0f, -1.45f, 0.4f, ink)
            canvas.restore()
            fill.color = 0xFFFFB25E.toInt()
            canvas.drawPath(fin, fill)
            canvas.drawPath(body, bodyShade)
            // Belly, scales, cheek.
            fill.color = 0x99FFF3E0.toInt()
            canvas.drawOval(-0.55f, 0.18f, 0.55f, 0.62f, fill)
            ink.strokeWidth = 0.05f
            ink.color = 0x40FFFFFF
            for (x in floatArrayOf(-0.45f, -0.15f, 0.15f)) canvas.drawArc(x - 0.13f, -0.3f, x + 0.13f, 0.1f, -60f, 120f, false, ink)
            fill.color = 0x80FF7A9A.toInt()
            canvas.drawOval(0.36f, 0.08f, 0.62f, 0.26f, fill)
            // The side fin, paddling.
            canvas.save()
            canvas.rotate(sin(time * 20f) * 20f, 0.05f, 0.15f)
            fill.color = 0xFFFFC27A.toInt()
            canvas.drawOval(-0.25f, 0.1f, 0.12f, 0.36f, fill)
            canvas.restore()
            // A big, shiny eye.
            fill.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(0.5f, -0.2f, 0.25f, fill)
            fill.color = 0xFF1B1B2F.toInt()
            canvas.drawCircle(0.54f, -0.18f, 0.16f, fill)
            fill.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(0.47f, -0.27f, 0.065f, fill)
            canvas.drawCircle(0.6f, -0.11f, 0.028f, fill)
            // A little smile.
            ink.strokeWidth = 0.06f
            ink.color = 0xFF8A3A12.toInt()
            canvas.drawArc(0.72f, -0.02f, 0.92f, 0.16f, 20f, 120f, false, ink)
            canvas.restore()
        }
    }

    override fun clear() {
        water.clear()
        fish.clear()
    }
}
