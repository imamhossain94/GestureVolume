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
import kotlin.math.pow
import kotlin.math.sin

/*
 * The ones with a punchline: a ninja's blade, a boxing glove, a thunder hammer, a cracked screen, a
 * snake, a flying kiss, a leaping fish and a mermaid.
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
        // A four-pointed throwing star, in a unit circle.
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
            p.life = 1.4f
        }
        // Sparks off the end of the slash.
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
                if (p.age >= p.life || p.x > scene.reach + 30f * scene.density) p.alive = false else alive = true
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
            // A crescent, thick in the middle and tapering to points, drawn out along the arc.
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
            // A blur of the star behind it, then the star.
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

// ---- boxing ---------------------------------------------------------------------------------------------

/** A boxing glove on a spring punches out of the bump at every press — left, right — with a POW at the end. */
internal class BoxingEffect(random: Random) : BumpEffect(random) {
    private class Punch {
        var alive = false
        var age = 0f
        var y = 0f
        var reach = 0f
    }

    private val punches = Array(2) { Punch() }
    private var next = 0
    private var side = 1f
    private val mitt = Path()
    private val thumb = Path()
    private val burst = Path()
    private val coil = Path()
    private val fill = solidFill()
    private val ink = roundStroke()

    init {
        // The glove facing +x in unit terms, its wrist at x = 0.
        mitt.moveTo(0.1f, -0.42f)
        mitt.cubicTo(0.55f, -0.54f, 1f, -0.44f, 1.02f, 0f)
        mitt.cubicTo(1f, 0.44f, 0.55f, 0.52f, 0.1f, 0.42f)
        mitt.close()
        thumb.moveTo(0.22f, -0.36f)
        thumb.cubicTo(0.26f, -0.64f, 0.66f, -0.64f, 0.7f, -0.38f)
        thumb.close()
        // A comic-book burst.
        for (k in 0 until 24) {
            val a = k * PI.toFloat() / 12f
            val r = if (k % 2 == 0) 1f else 0.56f
            if (k == 0) burst.moveTo(cos(a) * r, sin(a) * r) else burst.lineTo(cos(a) * r, sin(a) * r)
        }
        burst.close()
    }

    override val stillAfterS: Float get() = 0.14f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        val p = punches[next++ % punches.size]
        p.alive = true
        p.age = 0f
        p.y = scene.centre + side * scene.restHalf * 0.25f
        side = -side
        p.reach = min(scene.reach * 0.62f, scene.span * 0.5f) * if (atLimit) 0.7f else 1f
    }

    /** How far out the glove is, 0..~1.08: out fast, a little past, held, and back. */
    private fun extension(t: Float): Float = when {
        t < 0.11f -> easeOut(t / 0.11f) * 1.08f
        t < 0.17f -> 1.08f - 0.08f * ((t - 0.11f) / 0.06f)
        t < 0.28f -> 1f
        t < 0.5f -> 1f - easeIn((t - 0.28f) / 0.22f)
        else -> 0f
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        var alive = false
        for (p in punches) {
            if (!p.alive) continue
            p.age += dt
            if (p.age > 0.5f) p.alive = false else alive = true
        }
        return alive
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        val g = GLOVE_DP * d
        for (p in punches) {
            if (!p.alive) continue
            val e = extension(p.age)
            val x0 = scene.restDepth * 0.5f
            val gx = scene.restDepth * 0.6f + p.reach * e
            // The spring.
            coil.reset()
            coil.moveTo(x0, p.y)
            val turns = 9
            for (i in 1 until turns * 2) {
                val k = i / (turns * 2f)
                coil.lineTo(x0 + (gx - x0) * k, p.y + if (i % 2 == 0) -5f * d else 5f * d)
            }
            coil.lineTo(gx, p.y)
            ink.strokeWidth = 2.4f * d
            ink.color = 0xFF8D8D8D.toInt()
            canvas.drawPath(coil, ink)
            ink.strokeWidth = 0.8f * d
            ink.color = 0xFFE6E6E6.toInt()
            canvas.drawPath(coil, ink)
            // The glove.
            canvas.save()
            canvas.translate(gx, p.y)
            canvas.scale(g, g)
            fill.color = 0xFFF2F2F2.toInt()
            canvas.drawRoundRect(-0.28f, -0.3f, 0.14f, 0.3f, 0.08f, 0.08f, fill)
            fill.color = 0xFFBDBDBD.toInt()
            canvas.drawRect(-0.2f, -0.3f, -0.17f, 0.3f, fill)
            fill.color = 0xFFB71C1C.toInt()
            canvas.save()
            canvas.translate(0.02f, 0.05f)
            canvas.drawPath(mitt, fill)
            canvas.restore()
            fill.color = 0xFFE53935.toInt()
            canvas.drawPath(mitt, fill)
            fill.color = 0xFFD32F2F.toInt()
            canvas.drawPath(thumb, fill)
            fill.color = fade(0xFFFFFFFF.toInt(), 0.35f)
            canvas.drawOval(0.45f, -0.32f, 0.85f, -0.12f, fill)
            canvas.restore()
            // POW at the moment it lands.
            if (p.age in 0.09f..0.36f) {
                val k = (p.age - 0.09f) / 0.27f
                val pop = if (k < 0.3f) easeOut(k / 0.3f) else 1f
                val a = if (k < 0.6f) 1f else (1f - (k - 0.6f) / 0.4f)
                val r = 17f * d * pop
                canvas.save()
                canvas.translate(gx + g * 0.95f, p.y)
                canvas.rotate(p.age * 60f)
                canvas.scale(r, r)
                fill.color = fade(0xFFFFEB3B.toInt(), a)
                canvas.drawPath(burst, fill)
                ink.strokeWidth = 0.1f
                ink.color = fade(0xFFFF6F00.toInt(), a)
                canvas.drawPath(burst, ink)
                canvas.restore()
            }
        }
    }

    override fun clear() {
        for (p in punches) p.alive = false
    }

    private companion object {
        const val GLOVE_DP = 24f
    }
}

// ---- thunder hammer -------------------------------------------------------------------------------------

/**
 * A war hammer swings out of the bump and strikes at every press — down from the top one time, up from
 * the bottom the next — and the strike throws lightning and a shockwave.
 */
internal class HammerEffect(random: Random) : BumpEffect(random) {
    private class Swing {
        var alive = false
        var age = 0f
        var side = 1f
        var struck = false
        var hx = 0f
        var hy = 0f
    }

    private val swings = Array(2) { Swing() }
    private var next = 0
    private var side = 1f
    private val bolts = Array(3) { FloatArray(2 * BOLT_POINTS) }
    private var boltAge = 10f
    private var boltX = 0f
    private var boltY = 0f
    private var jitterClock = 0f
    private val boltPath = Path()
    private val boltPaint = roundStroke()
    private val fill = solidFill()
    private val ink = roundStroke()
    private val head = solidFill().apply {
        shader = LinearGradient(0f, -0.5f, 0f, 0.5f, intArrayOf(0xFFE3E9EC.toInt(), 0xFF98A3A8.toInt(), 0xFF5D666B.toInt()), null, Shader.TileMode.CLAMP)
    }
    private val aura = solidFill().apply { shader = softPuff(0xFF82B1FF.toInt()) }

    override val stillAfterS: Float get() = 0.2f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        val s = swings[next++ % swings.size]
        s.alive = true
        s.age = 0f
        s.side = side
        side = -side
        s.struck = false
    }

    /** The hammer's angle, degrees from straight in: wound back along the edge, swung through, and back. */
    private fun angle(s: Swing): Float {
        val t = s.age
        val back = -100f * s.side
        return when {
            t < STRIKE -> back * (1f - easeIn(t / STRIKE))
            t < 0.24f -> 8f * s.side * sin(PI.toFloat() * (t - STRIKE) / 0.08f)
            t < 0.6f -> 0f
            t < 1f -> back * easeOut((t - 0.6f) / 0.4f)
            else -> back
        }
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        val d = scene.density
        var alive = false
        val handle = handleLength(scene)
        for (s in swings) {
            if (!s.alive) continue
            s.age += dt
            if (!s.struck && s.age >= STRIKE) {
                s.struck = true
                // Lightning from the head, outward.
                s.hx = scene.restDepth * 0.5f + handle + HEAD_DEPTH_DP * d * 0.5f
                s.hy = scene.centre
                boltX = s.hx
                boltY = s.hy
                boltAge = 0f
                jitter(scene)
            }
            if (s.age > 1f) s.alive = false else alive = true
        }
        if (boltAge < BOLT_S) {
            boltAge += dt
            jitterClock -= dt
            if (jitterClock <= 0f) jitter(scene)
            alive = true
        }
        return alive
    }

    private fun handleLength(scene: BumpScene) = min(scene.reach * 0.34f, 60f * scene.density)

    private fun jitter(scene: BumpScene) {
        jitterClock = 0.05f
        val length = min(scene.reach, scene.span * 0.5f)
        for ((i, pts) in bolts.withIndex()) {
            val a = Math.toRadians((-40.0 + 40.0 * i) + rnd(-12f, 12f)).toFloat()
            val l = length * rnd(0.35f, 0.6f)
            Bolts.jag(pts, BOLT_POINTS, boltX, boltY, boltX + cos(a) * l, boltY + sin(a) * l, 0.3f, random)
        }
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        val handle = handleLength(scene)
        val pivotX = scene.restDepth * 0.5f
        // Charged while out: a faint glow round where the head rests.
        for (s in swings) {
            if (!s.alive) continue
            canvas.save()
            canvas.translate(pivotX, scene.centre)
            canvas.rotate(angle(s))
            // The handle, leather-wrapped, and its strap.
            fill.color = 0xFF5D4037.toInt()
            canvas.drawRoundRect(0f, -3f * d, handle, 3f * d, 2f * d, 2f * d, fill)
            ink.strokeWidth = 1.2f * d
            ink.color = 0xFF3E2723.toInt()
            var x = 3f * d
            while (x < handle - 2f * d) {
                canvas.drawLine(x, -3f * d, x + 3f * d, 3f * d, ink)
                x += 5f * d
            }
            ink.strokeWidth = 1.4f * d
            canvas.drawCircle(-3f * d, 0f, 3f * d, ink)
            // The head: a heavy steel block, edges darkened, a knotwork line on its face.
            val hd = HEAD_DEPTH_DP * d
            val hw = HEAD_WIDTH_DP * d
            canvas.save()
            canvas.translate(handle + hd / 2f, 0f)
            if (s.struck && s.age < 0.6f) {
                aura.alpha = (200 * (1f - (s.age - STRIKE) / 0.45f).coerceIn(0f, 1f)).toInt()
                canvas.save()
                canvas.scale(hw, hw)
                canvas.drawCircle(0f, 0f, 1f, aura)
                canvas.restore()
            }
            canvas.save()
            canvas.scale(hd, hw)
            canvas.drawRoundRect(-0.5f, -0.5f, 0.5f, 0.5f, 0.12f, 0.08f, head)
            canvas.restore()
            fill.color = 0xFF4A5257.toInt()
            canvas.drawRect(-hd / 2f, -hw / 2f, hd / 2f, -hw / 2f + 3f * d, fill)
            canvas.drawRect(-hd / 2f, hw / 2f - 3f * d, hd / 2f, hw / 2f, fill)
            ink.strokeWidth = 0.9f * d
            ink.color = 0xFF3B4246.toInt()
            canvas.drawCircle(0f, 0f, hd * 0.28f, ink)
            canvas.drawLine(-hd * 0.35f, 0f, hd * 0.35f, 0f, ink)
            canvas.drawLine(0f, -hd * 0.35f, 0f, hd * 0.35f, ink)
            canvas.restore()
            canvas.restore()
        }
        if (boltAge < BOLT_S) {
            val k = boltAge / BOLT_S
            val a = (1f - k).pow(0.6f)
            ink.strokeWidth = (3f * (1f - k) + 0.6f) * d
            ink.color = fade(0xFFBBDEFB.toInt(), 0.8f * (1f - k))
            canvas.drawCircle(boltX, boltY, min(scene.reach, scene.span * 0.5f) * 0.55f * easeOut(k), ink)
            for (pts in bolts) Bolts.draw(canvas, boltPath, pts, BOLT_POINTS, boltPaint, 0xFF5A7BFF.toInt(), 1.3f * d, a)
        }
    }

    override fun clear() {
        for (s in swings) s.alive = false
        boltAge = 10f
    }

    private companion object {
        const val STRIKE = 0.16f
        const val BOLT_S = 0.4f
        const val BOLT_POINTS = 17
        const val HEAD_DEPTH_DP = 17f
        const val HEAD_WIDTH_DP = 30f
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
        // Rings: short jagged cracks joining neighbours at two distances out.
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
        // Shards by the point of impact.
        for (i in 0 until radial.size - 1 step 2) {
            val a = pointAt(radial[i], p.cx, p.cy, radius * 0.14f) ?: continue
            val b = pointAt(radial[i + 1], p.cx, p.cy, radius * 0.14f) ?: continue
            p.shards += floatArrayOf(p.cx, p.cy, a[0], a[1], b[0], b[1])
        }
    }

    /** A crack: [segments] steps from a point, wandering a little off its direction at each. */
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

    /** The first point of a crack at least [distance] from the centre. */
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

// ---- snake bite -----------------------------------------------------------------------------------------

/**
 * A snake's hole opens in the edge in place of the bump, its head peeking out and tasting the air;
 * every press, it strikes — lunging across the screen, fangs bared — and draws back.
 */
internal class SnakeEffect(random: Random) : BumpEffect(random) {
    override val drawsBump: Boolean get() = false
    override val loops: Boolean get() = true

    private var strike = -1f
    private var time = 0f
    private val upper = Path()
    private val lower = Path()
    private val fang = Path()
    private val fill = solidFill()
    private val ink = roundStroke()
    private val hole = solidFill().apply { shader = softPuff(0xFF120B08.toInt()) }

    init {
        // The head facing +x in unit terms, its hinge at (-0.4, 0).
        upper.moveTo(-0.6f, -0.42f)
        upper.cubicTo(0.1f, -0.66f, 0.8f, -0.46f, 1f, -0.04f)
        upper.lineTo(-0.6f, 0.04f)
        upper.close()
        lower.moveTo(-0.6f, 0.04f)
        lower.lineTo(0.95f, 0f)
        lower.cubicTo(0.7f, 0.38f, 0.1f, 0.48f, -0.6f, 0.4f)
        lower.close()
        fang.moveTo(0.5f, -0.02f)
        fang.lineTo(0.62f, -0.02f)
        fang.lineTo(0.56f, 0.22f)
        fang.close()
    }

    override val stillAfterS: Float get() = 0.2f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        strike = 0f
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        time += dt
        if (strike >= 0f) {
            strike += dt
            if (strike > 0.8f) strike = -1f
        }
        return strike >= 0f || scene.presence > 0.01f
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        val out = ButtonBump.smootherstep(scene.presence.coerceIn(0f, 1f))
        val far = min(scene.reach * 0.6f, scene.span * 0.45f)
        val idle = 12f * d * out
        val t = strike
        val hx = when {
            t < 0f -> idle
            t < 0.14f -> idle + (far - idle) * easeOut(t / 0.14f) * 1.05f
            t < 0.45f -> far + sin(t * 30f) * 1.5f * d
            else -> far + (idle - far) * easeIn((t - 0.45f) / 0.35f)
        }
        val open = if (t in 0.06f..0.5f) 1f else 0f
        if (out < 0.01f && t < 0f) return
        val cy = scene.centre
        // The hole.
        val holeH = scene.restHalf * 0.6f * max(out, 0.4f)
        canvas.save()
        canvas.translate(0f, cy)
        canvas.scale(10f * d, holeH)
        canvas.drawCircle(0f, 0f, 1f, hole)
        canvas.restore()
        // The body: a wave from the hole to the head, fading to straight at both ends, in bands.
        val x0 = -6f * d
        val len = hx - x0
        if (len > 2f * d) {
            val amp = 9f * d * (len / far).coerceIn(0.2f, 1f)
            val step = 2.4f * d
            var s = 0f
            while (s <= len) {
                val k = s / len
                val x = x0 + s
                val y = cy + amp * sin(PI.toFloat() * k) * sin(k * 9f - time * 8f)
                val r = (5.5f + 1.2f * sin(PI.toFloat() * k)) * d
                fill.color = if (((s / (7f * d)).toInt() % 2) == 0) 0xFF3C8D2F.toInt() else 0xFF2E6E24.toInt()
                canvas.drawCircle(x, y, r, fill)
                s += step
            }
        }
        // The head.
        val hs = 11f * d
        canvas.save()
        canvas.translate(hx, cy)
        canvas.scale(hs, hs)
        if (open > 0f) {
            fill.color = 0xFFF48FB1.toInt()
            canvas.drawCircle(0.1f, 0f, 0.38f, fill)
        }
        canvas.save()
        canvas.rotate(28f * open, -0.4f, 0f)
        fill.color = 0xFF3C8D2F.toInt()
        canvas.drawPath(lower, fill)
        canvas.restore()
        canvas.save()
        canvas.rotate(-28f * open, -0.4f, 0f)
        if (open > 0f) {
            fill.color = 0xFFFFFFFF.toInt()
            canvas.drawPath(fang, fill)
            canvas.save()
            canvas.translate(-0.22f, 0f)
            canvas.drawPath(fang, fill)
            canvas.restore()
        }
        fill.color = 0xFF4CAF50.toInt()
        canvas.drawPath(upper, fill)
        fill.color = 0xFFFFEB3B.toInt()
        canvas.drawCircle(0.25f, -0.3f, 0.13f, fill)
        fill.color = 0xFF000000.toInt()
        canvas.drawOval(0.23f, -0.41f, 0.27f, -0.19f, fill)
        canvas.restore()
        // Tasting the air when it is not striking.
        if (open == 0f && sin(time * 7f) > 0.3f) {
            ink.strokeWidth = 0.09f
            ink.color = 0xFFE53935.toInt()
            canvas.drawLine(0.95f, 0f, 1.45f, 0f, ink)
            canvas.drawLine(1.45f, 0f, 1.65f, -0.15f, ink)
            canvas.drawLine(1.45f, 0f, 1.65f, 0.15f, ink)
        }
        canvas.restore()
    }

    override fun clear() {
        strike = -1f
    }
}

// ---- flying kiss -----------------------------------------------------------------------------------------

/** Lips pucker by the bump at every press and blow a kiss: hearts floating off across the screen. */
internal class KissEffect(random: Random) : BumpEffect(random) {
    private val hearts = ParticlePool(50)
    private val pucker = ButtonBump.Spring()
    private var lipsAge = 10f
    private val heart = Path()
    private val upperLip = Path()
    private val lowerLip = Path()
    private val fill = solidFill()

    init {
        heart.moveTo(0f, 0.6f)
        heart.cubicTo(-1.1f, -0.15f, -0.55f, -1f, 0f, -0.45f)
        heart.cubicTo(0.55f, -1f, 1.1f, -0.15f, 0f, 0.6f)
        heart.close()
        upperLip.moveTo(-1f, 0f)
        upperLip.cubicTo(-0.6f, -0.25f, -0.35f, -0.55f, -0.12f, -0.42f)
        upperLip.quadTo(0f, -0.32f, 0.12f, -0.42f)
        upperLip.cubicTo(0.35f, -0.55f, 0.6f, -0.25f, 1f, 0f)
        upperLip.quadTo(0f, 0.1f, -1f, 0f)
        upperLip.close()
        lowerLip.moveTo(-1f, 0f)
        lowerLip.quadTo(0f, 0.1f, 1f, 0f)
        lowerLip.cubicTo(0.7f, 0.58f, -0.7f, 0.58f, -1f, 0f)
        lowerLip.close()
    }

    override val stillAfterS: Float get() = 0.5f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        lipsAge = 0f
        pucker.velocity += 8f
        val d = scene.density
        repeat(if (atLimit) 3 else 6) {
            val p = hearts.spawn() ?: return@repeat
            p.x = scene.restDepth + 16f * d
            p.y = scene.centre
            p.vx = rnd(80f, 170f) * d
            p.vy = rnd(-60f, 60f) * d
            p.size = rnd(5f, 9f) * d
            p.life = rnd(1.6f, 2.4f)
            p.mark = rnd(0f, 6.28f)
            p.color = HEARTS[random.nextInt(HEARTS.size)]
        }
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        val d = scene.density
        lipsAge += dt
        ButtonBump.stepSpring(pucker, if (lipsAge < 0.7f) 1f else 0f, 220f, 0.45f, dt)
        val drag = exp(-1f * dt)
        val acrossX = -scene.gravityY
        val acrossY = scene.gravityX
        var alive = false
        for (p in hearts.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.life) {
                p.alive = false
                continue
            }
            val sway = sin(p.age * 4f + p.mark) * 60f * d
            p.vx = p.vx * drag + (-scene.gravityX * 50f * d + acrossX * sway) * dt
            p.vy = p.vy * drag + (-scene.gravityY * 50f * d + acrossY * sway) * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            alive = true
        }
        return alive || pucker.value > 0.01f || abs(pucker.velocity) > 0.01f
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        val up = uprightDegrees(scene)
        val l = pucker.value.coerceIn(0f, 1.3f)
        if (l > 0.02f) {
            canvas.save()
            canvas.translate(scene.restDepth + 14f * d, scene.centre)
            canvas.rotate(up)
            // Puckered: narrower and fuller than at rest.
            canvas.scale(11f * d * l * 0.85f, 11f * d * l * 1.15f)
            fill.color = 0xFFE53950.toInt()
            canvas.drawPath(upperLip, fill)
            fill.color = 0xFFD81B60.toInt()
            canvas.drawPath(lowerLip, fill)
            fill.color = fade(0xFFFFFFFF.toInt(), 0.45f)
            canvas.drawOval(-0.35f, 0.14f, 0.1f, 0.3f, fill)
            canvas.restore()
        }
        for (p in hearts.items) {
            if (!p.alive) continue
            val t = p.age / p.life
            val a = if (t < 0.7f) min(1f, p.age / 0.12f) else (1f - t) / 0.3f
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(up + sin(p.age * 3f + p.mark) * 15f)
            val s = p.size * (1f + 0.25f * t)
            canvas.scale(s, s)
            fill.color = fade(p.color, a)
            canvas.drawPath(heart, fill)
            fill.color = fade(0xFFFFFFFF.toInt(), 0.5f * a)
            canvas.drawCircle(-0.4f, -0.35f, 0.14f, fill)
            canvas.restore()
        }
    }

    override fun clear() {
        hearts.clear()
        pucker.snapTo(0f)
        lipsAge = 10f
    }

    private companion object {
        val HEARTS = intArrayOf(0xFFFF4081.toInt(), 0xFFE91E63.toInt(), 0xFFFF80AB.toInt(), 0xFFF50057.toInt(), 0xFFFF5252.toInt())
    }
}

// ---- water, for the fish and the mermaid ------------------------------------------------------------

/** A strip of water along the edge, with a lapping surface, splashes and ripples: where the fish and the mermaid live. */
internal class WaterStrip(private val random: Random) {
    val drops = ParticlePool(90)
    private val ripples = ParticlePool(10)
    private val surface = Path()
    private val fill = solidFill()
    private val ink = roundStroke()
    private var shaderDepth = -1f
    private var time = 0f

    fun depth(scene: BumpScene) = scene.restDepth * 0.9f * ButtonBump.smootherstep(scene.presence.coerceIn(0f, 1f))
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
            // Back down into the water, at the edge.
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
                // The surface laps, and tapers to nothing at both ends.
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
            canvas.drawArc(RectF(p.x - r * 0.4f, p.y - r, p.x + r * 0.4f, p.y + r), -90f, 180f, false, ink)
        }
        for (p in drops.items) {
            if (!p.alive) continue
            fill.shader = null
            fill.color = fade(0xFFE1F5FE.toInt(), 0.9f)
            canvas.drawCircle(p.x, p.y, p.size, fill)
            fill.shader = null
        }
        shaderDepth = -1f
    }

    fun clear() {
        drops.clear()
        ripples.clear()
    }
}

// ---- fish jump -------------------------------------------------------------------------------------------

/** Water laps along the edge, and every press a clownfish leaps out of it, arcs across, and dives back in. */
internal class FishEffect(random: Random) : BumpEffect(random) {
    override val drawsBump: Boolean get() = false
    override val loops: Boolean get() = true

    private val water = WaterStrip(random)
    private val fish = ParticlePool(4)
    private var side = 1f
    private var time = 0f
    private val body = Path()
    private val tail = Path()
    private val fill = solidFill()
    private val ink = roundStroke()

    init {
        body.moveTo(-1f, 0f)
        body.cubicTo(-0.6f, -0.62f, 0.5f, -0.64f, 1f, -0.05f)
        body.cubicTo(1.05f, 0f, 1.05f, 0.05f, 1f, 0.08f)
        body.cubicTo(0.5f, 0.62f, -0.6f, 0.6f, -1f, 0f)
        body.close()
        tail.moveTo(-0.92f, 0f)
        tail.lineTo(-1.5f, -0.5f)
        tail.quadTo(-1.32f, 0f, -1.5f, 0.5f)
        tail.close()
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
        f.mark = 0f
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
            } else if (f.age > f.life) {
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
            val s = 13f * d
            canvas.save()
            canvas.translate(f.x, f.y)
            canvas.rotate(Math.toDegrees(atan2(f.vy, f.vx).toDouble()).toFloat())
            canvas.scale(s, s)
            // The tail beats as it flies.
            canvas.save()
            canvas.rotate(sin(time * 22f) * 14f, -0.92f, 0f)
            fill.color = 0xFFFF8F00.toInt()
            canvas.drawPath(tail, fill)
            ink.strokeWidth = 0.08f
            ink.color = 0xFF212121.toInt()
            canvas.drawPath(tail, ink)
            canvas.restore()
            fill.color = 0xFFFF8F00.toInt()
            canvas.drawPath(body, fill)
            // White bands, edged in black.
            canvas.save()
            canvas.clipPath(body)
            for (bx in floatArrayOf(0.48f, -0.18f, -0.78f)) {
                fill.color = 0xFF212121.toInt()
                canvas.drawRect(bx - 0.14f, -1f, bx + 0.14f, 1f, fill)
                fill.color = 0xFFFFFFFF.toInt()
                canvas.drawRect(bx - 0.09f, -1f, bx + 0.09f, 1f, fill)
            }
            canvas.restore()
            ink.strokeWidth = 0.08f
            canvas.drawPath(body, ink)
            fill.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(0.66f, -0.16f, 0.13f, fill)
            fill.color = 0xFF000000.toInt()
            canvas.drawCircle(0.69f, -0.16f, 0.075f, fill)
            canvas.restore()
        }
    }

    override fun clear() {
        water.clear()
        fish.clear()
    }
}

// ---- mermaid -----------------------------------------------------------------------------------------

/**
 * Water laps along the edge, and every press a mermaid's tail rises out of it — scales shimmering teal
 * to violet — flicks its fin with a splash and a sparkle, and slips back under.
 */
internal class MermaidEffect(random: Random) : BumpEffect(random) {
    override val drawsBump: Boolean get() = false
    override val loops: Boolean get() = true

    private val water = WaterStrip(random)
    private val sparkles = ParticlePool(30)
    private var tailAge = -1f
    private var dived = true
    private var time = 0f
    private val tail = Path()
    private val fluke = Path()
    private val sparkle = Path()
    private val fill = solidFill()
    private val ink = roundStroke()
    private val xs = FloatArray(17)
    private val ys = FloatArray(17)

    init {
        // A two-lobed fin reaching out along +x from its root, in unit terms.
        fluke.moveTo(0f, 0f)
        fluke.cubicTo(0.25f, -0.15f, 0.7f, -0.75f, 1.2f, -0.95f)
        fluke.cubicTo(0.95f, -0.45f, 0.95f, -0.15f, 0.72f, 0f)
        fluke.cubicTo(0.95f, 0.15f, 0.95f, 0.45f, 1.2f, 0.95f)
        fluke.cubicTo(0.7f, 0.75f, 0.25f, 0.15f, 0f, 0f)
        fluke.close()
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4f
            val r = if (k % 2 == 0) 1f else 0.3f
            if (k == 0) sparkle.moveTo(cos(a) * r, sin(a) * r) else sparkle.lineTo(cos(a) * r, sin(a) * r)
        }
        sparkle.close()
    }

    override val stillAfterS: Float get() = 0.6f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        tailAge = 0f
        dived = false
        water.splash(scene, scene.centre, 0.7f)
    }

    /** How far out of the water the tail is, 0..1, and its fin's flick in degrees. */
    private fun rise(t: Float): Float = when {
        t < 0f -> 0f
        t < 0.45f -> easeOut(t / 0.45f)
        t < 1f -> 1f
        t < 1.6f -> 1f - easeIn((t - 1f) / 0.6f)
        else -> 0f
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        time += dt
        val d = scene.density
        var alive = water.step(scene, dt)
        if (tailAge >= 0f) {
            tailAge += dt
            if (tailAge in 0.45f..1f && random.nextFloat() < dt * 25f) {
                val p = sparkles.spawn()
                if (p != null) {
                    val h = tailHeight(scene)
                    p.x = h * rnd(0.6f, 1.3f)
                    p.y = scene.centre + rnd(-1f, 1f) * 30f * d
                    p.life = rnd(0.4f, 0.8f)
                    p.size = rnd(2f, 4f) * d
                }
            }
            if (tailAge > 1.55f && !dived) {
                dived = true
                water.splash(scene, scene.centre, 1f)
            }
            if (tailAge > 1.8f) tailAge = -1f
            alive = true
        }
        for (p in sparkles.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age > p.life) p.alive = false else alive = true
        }
        return alive || scene.presence > 0.01f
    }

    private fun tailHeight(scene: BumpScene) = min(scene.reach * 0.62f, 84f * scene.density)

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        val u = rise(tailAge)
        if (u > 0.01f) {
            val h = tailHeight(scene) * u
            val cy = scene.centre
            // The tail's line: out of the water at the edge, curving, to the fin.
            val n = xs.size - 1
            for (i in 0..n) {
                val k = i / n.toFloat()
                val a = (1f - k) * (1f - k)
                val b = 2f * (1f - k) * k
                val c = k * k
                xs[i] = a * (-4f * d) + b * (h * 0.55f) + c * h
                ys[i] = a * cy + b * (cy - 28f * d) + c * (cy + 8f * d)
            }
            tail.reset()
            for (i in 0..n) {
                val k = i / n.toFloat()
                val w = (22f - 15f * k) * d * 0.5f
                val (nx, ny) = normal(i)
                if (i == 0) tail.moveTo(xs[i] + nx * w, ys[i] + ny * w) else tail.lineTo(xs[i] + nx * w, ys[i] + ny * w)
            }
            for (i in n downTo 0) {
                val k = i / n.toFloat()
                val w = (22f - 15f * k) * d * 0.5f
                val (nx, ny) = normal(i)
                tail.lineTo(xs[i] - nx * w, ys[i] - ny * w)
            }
            tail.close()
            fill.shader = LinearGradient(0f, 0f, h.coerceAtLeast(1f), 0f, 0xFF00BFA5.toInt(), 0xFF7C4DFF.toInt(), Shader.TileMode.CLAMP)
            canvas.drawPath(tail, fill)
            fill.shader = null
            // Scales.
            ink.strokeWidth = 0.9f * d
            ink.color = fade(0xFFFFFFFF.toInt(), 0.28f)
            for (i in 2 until n step 2) canvas.drawCircle(xs[i], ys[i], 2.6f * d, ink)
            // The fin, flicking.
            val flick = if (tailAge in 0.45f..1f) sin((tailAge - 0.45f) / 0.55f * 2f * PI.toFloat()) * 28f else 0f
            val ang = Math.toDegrees(atan2(ys[n] - ys[n - 1], xs[n] - xs[n - 1]).toDouble()).toFloat()
            canvas.save()
            canvas.translate(xs[n], ys[n])
            canvas.rotate(ang + flick)
            canvas.scale(20f * d, 20f * d)
            fill.shader = LinearGradient(0f, -1f, 0f, 1f, 0xF0FF80AB.toInt(), 0xF0B388FF.toInt(), Shader.TileMode.CLAMP)
            canvas.drawPath(fluke, fill)
            fill.shader = null
            ink.strokeWidth = 0.05f
            ink.color = fade(0xFFFFFFFF.toInt(), 0.4f)
            canvas.drawLine(0.1f, 0f, 1.05f, -0.75f, ink)
            canvas.drawLine(0.1f, 0f, 1.05f, 0.75f, ink)
            canvas.restore()
        }
        water.draw(canvas, scene)
        for (p in sparkles.items) {
            if (!p.alive) continue
            val k = p.age / p.life
            val s = p.size * sin(PI.toFloat() * k)
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.age * 90f)
            canvas.scale(s, s)
            fill.color = fade(0xFFFFFFFF.toInt(), 0.95f)
            canvas.drawPath(sparkle, fill)
            canvas.restore()
        }
    }

    /** The unit normal to the tail's line at point [i]. */
    private fun normal(i: Int): Pair<Float, Float> {
        val a = (i - 1).coerceAtLeast(0)
        val b = (i + 1).coerceAtMost(xs.size - 1)
        val dx = xs[b] - xs[a]
        val dy = ys[b] - ys[a]
        val len = hypot(dx, dy).coerceAtLeast(0.001f)
        return -dy / len to dx / len
    }

    override fun clear() {
        water.clear()
        sparkles.clear()
        tailAge = -1f
    }
}
