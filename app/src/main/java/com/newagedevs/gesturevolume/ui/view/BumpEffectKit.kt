package com.newagedevs.gesturevolume.ui.view

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import java.util.Random
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.pow

/*
 * The parts the showpiece effects share: smoke and fire puffs, lightning, easing, and the frame a
 * character is drawn in so it stands the right way up on the screen.
 */

/**
 * [this] held between [low] and [high], or their middle when the range is inside out: for bounds
 * worked out from the size of the view, which in a gallery's small tile can come out with the low
 * one above the high one — where Kotlin's own coerceIn throws, and took the screen down with it.
 */
internal fun Float.clampIn(low: Float, high: Float): Float =
    if (low <= high) coerceIn(low, high) else (low + high) / 2f

/** 0 to 1, fast then slow. */
internal fun easeOut(t: Float): Float = 1f - (1f - t.coerceIn(0f, 1f)).pow(3)

/** 0 to 1, slow then fast. */
internal fun easeIn(t: Float): Float = t.coerceIn(0f, 1f).pow(3)

/**
 * The rotation, in degrees, that stands a drawing up on the screen: what was up in the drawing — its
 * -y — points up on the glass, whichever edge the buttons are on and however the phone is turned.
 */
internal fun uprightDegrees(scene: BumpScene): Float =
    Math.toDegrees(atan2(-scene.gravityX, scene.gravityY).toDouble()).toFloat()

/** A soft round puff of [colour], from solid in the middle to nothing at its edge, in a unit circle. */
internal fun softPuff(colour: Int): Shader = RadialGradient(
    0f, 0f, 1f,
    intArrayOf(colour, fade(colour, 0.6f), fade(colour, 0f)),
    floatArrayOf(0f, 0.5f, 1f),
    Shader.TileMode.CLAMP,
)

/**
 * Puffs of smoke or steam: each grows from one size to another as it drifts, slowing in the air and
 * lifted one way, and fades as it goes. One shader for all of them, scaled to each, so nothing is made
 * per frame.
 */
internal class Puffs(capacity: Int, shader: Shader) {
    val pool = ParticlePool(capacity)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }

    /** A puff at [x], [y] moving at [vx], [vy], growing from radius [from] to [to] over [life] seconds. */
    fun emit(x: Float, y: Float, vx: Float, vy: Float, from: Float, to: Float, life: Float, alpha: Float): Particle? {
        val p = pool.spawn() ?: return null
        p.x = x
        p.y = y
        p.vx = vx
        p.vy = vy
        p.size = from
        p.tx = to
        p.life = life
        p.mark = alpha
        return p
    }

    /** Moves the puffs on; whether any are left. [liftX], [liftY] is an acceleration, in pixels a second each second. */
    fun step(dt: Float, drag: Float, liftX: Float, liftY: Float): Boolean {
        val keep = kotlin.math.exp(-drag * dt)
        var alive = false
        for (p in pool.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.life) {
                p.alive = false
                continue
            }
            p.vx = p.vx * keep + liftX * dt
            p.vy = p.vy * keep + liftY * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            alive = true
        }
        return alive
    }

    fun draw(canvas: Canvas, alpha: Float = 1f) {
        for (p in pool.items) {
            if (!p.alive) continue
            val t = p.age / p.life
            val r = p.size + (p.tx - p.size) * easeOut(t)
            val a = p.mark * alpha * (1f - t).pow(1.4f) * (p.age / 0.08f).coerceAtMost(1f)
            if (a <= 0.004f || r <= 0.3f) continue
            paint.alpha = (a * 255f).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.scale(r, r)
            canvas.drawCircle(0f, 0f, 1f, paint)
            canvas.restore()
        }
    }

    fun clear() = pool.clear()
}

/** Jagged lightning, shared by the styles that crackle. */
internal object Bolts {
    /**
     * Fills [points] with [count] points (one more than a power of two) of a jagged line from
     * [sx], [sy] to [tx], [ty]: the middle of each stretch pushed aside, again and again.
     */
    fun jag(points: FloatArray, count: Int, sx: Float, sy: Float, tx: Float, ty: Float, roughness: Float, random: Random) {
        points[0] = sx
        points[1] = sy
        points[2 * (count - 1)] = tx
        points[2 * (count - 1) + 1] = ty
        var step = count - 1
        while (step > 1) {
            val half = step / 2
            var i = 0
            while (i + step < count) {
                val ax = points[2 * i]
                val ay = points[2 * i + 1]
                val bx = points[2 * (i + step)]
                val by = points[2 * (i + step) + 1]
                val len = hypot(bx - ax, by - ay)
                val off = (random.nextFloat() * 2f - 1f) * len * roughness
                val nx = if (len > 0f) -(by - ay) / len else 0f
                val ny = if (len > 0f) (bx - ax) / len else 0f
                points[2 * (i + half)] = (ax + bx) / 2f + nx * off
                points[2 * (i + half) + 1] = (ay + by) / 2f + ny * off
                i += step
            }
            step = half
        }
    }

    /** A bolt along [points]: a wide coloured glow, a narrower one, and a white core. */
    fun draw(canvas: Canvas, path: Path, points: FloatArray, count: Int, paint: Paint, glow: Int, width: Float, alpha: Float) {
        path.reset()
        path.moveTo(points[0], points[1])
        for (i in 1 until count) path.lineTo(points[2 * i], points[2 * i + 1])
        paint.strokeWidth = width * 6f
        paint.color = fade(glow, 0.22f * alpha)
        canvas.drawPath(path, paint)
        paint.strokeWidth = width * 2.6f
        paint.color = fade(blend(glow, 0xFFFFFFFF.toInt(), 0.5f), 0.6f * alpha)
        canvas.drawPath(path, paint)
        paint.strokeWidth = width
        paint.color = fade(0xFFFFFFFF.toInt(), alpha)
        canvas.drawPath(path, paint)
    }
}

/** A stroke paint with round ends and joins, the way most of the effects draw their lines. */
internal fun roundStroke(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

internal fun solidFill(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
