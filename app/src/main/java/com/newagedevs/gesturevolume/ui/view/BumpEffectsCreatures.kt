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

/** How a character feels: set by how many times in a row the button has been pressed. */
internal enum class Mood { CALM, HAPPY, SURPRISED, LAUGHING, LOVE, ANGRY, DIZZY }

/**
 * A character that peeks round the edge of the glass at the button: it slides out with the bump, holds
 * on with its hands, blinks and looks about, and answers each press with a feeling — happy at one,
 * surprised at two, laughing at three, in love at four, cross at five, dizzy after that — calming down
 * again a moment after the last. Its face is always the right way up on the screen.
 */
internal abstract class PeekEffect(random: Random) : BumpEffect(random) {
    override val drawsBump: Boolean get() = false
    override val loops: Boolean get() = true

    protected val peek = ButtonBump.Spring()
    protected var time = 0f
    private var blinkIn = 1.6f
    private var blink = 0f
    private var streak = 0
    private var sincePress = 10f
    private var look = 0f
    private var lookTarget = 0f
    private var lookIn = 1f

    /** 1 at a press, fading to 0: the pop of the head. */
    protected var pop = 0f
    protected var mood = Mood.CALM

    private val extras = ParticlePool(40)
    protected val fill = solidFill()
    protected val ink = roundStroke()
    protected val oval = RectF()
    protected val shape = Path()
    private val heart = Path().apply {
        moveTo(0f, 0.6f)
        cubicTo(-1.1f, -0.15f, -0.55f, -1f, 0f, -0.45f)
        cubicTo(0.55f, -1f, 1.1f, -0.15f, 0f, 0.6f)
        close()
    }
    private val starPath = Path().apply {
        for (k in 0 until 10) {
            val a = -PI.toFloat() / 2f + k * PI.toFloat() / 5f
            val r = if (k % 2 == 0) 1f else 0.45f
            if (k == 0) moveTo(cos(a) * r, sin(a) * r) else lineTo(cos(a) * r, sin(a) * r)
        }
        close()
    }
    private val steam = solidFill().apply { shader = softPuff(0xFFFFFFFF.toInt()) }

    override val stillAfterS: Float get() = 0.6f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        peek.velocity += if (atLimit) 2f else 4.5f
        pop = 1f
        streak = if (sincePress < STREAK_S) streak + 1 else 1
        sincePress = 0f
        mood = when (streak) {
            1 -> Mood.HAPPY
            2 -> Mood.SURPRISED
            3 -> Mood.LAUGHING
            4 -> Mood.LOVE
            5 -> Mood.ANGRY
            else -> Mood.DIZZY
        }
        if (mood == Mood.LOVE) repeat(4) { heartOut(scene) }
    }

    private fun heartOut(scene: BumpScene) {
        val p = extras.spawn() ?: return
        val r = radius(scene)
        p.kind = 0
        val across = rnd(-0.7f, 0.7f) * r
        p.x = -r + peek.value * r * 1.6f - scene.gravityX * r * 1.05f - scene.gravityY * across
        p.y = scene.centre - scene.gravityY * r * 1.05f + scene.gravityX * across
        p.vx = rnd(10f, 40f) * scene.density
        p.vy = 0f
        p.size = rnd(3.5f, 6f) * scene.density
        p.life = rnd(1.2f, 1.8f)
        p.mark = rnd(0f, 6.28f)
        p.color = if (random.nextBoolean()) 0xFFFF4D8D.toInt() else 0xFFFF8FB4.toInt()
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        time += dt
        sincePress += dt
        val d = scene.density
        ButtonBump.stepSpring(peek, scene.presence.coerceIn(0f, 1f), 210f, 0.5f, dt)
        pop *= exp(-3f * dt)
        if (sincePress > CALM_S) mood = Mood.CALM
        blinkIn -= dt
        if (blinkIn <= 0f) {
            blink = 1f
            blinkIn = rnd(1.8f, 3.4f)
        }
        blink = (blink - dt / 0.16f).coerceAtLeast(0f)
        // Looking about now and then while calm.
        lookIn -= dt
        if (lookIn <= 0f) {
            lookIn = rnd(0.8f, 2f)
            lookTarget = rnd(-1f, 1f)
        }
        look += (lookTarget - look) * min(1f, 6f * dt)
        // Steam while cross; more hearts while in love.
        if (mood == Mood.ANGRY && random.nextFloat() < dt * 12f) {
            extras.spawn()?.let { p ->
                val r = radius(scene)
                p.kind = 1
                p.x = -r + peek.value * r * 1.6f + rnd(-0.2f, 0.6f) * r
                p.y = scene.centre + rnd(-0.9f, 0.9f) * r
                p.vx = -scene.gravityX * 40f * d
                p.vy = -scene.gravityY * 40f * d
                p.size = rnd(3f, 5f) * d
                p.life = 0.7f
            }
        }
        if (mood == Mood.LOVE && random.nextFloat() < dt * 3f) heartOut(scene)
        var alive = false
        for (p in extras.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.life) {
                p.alive = false
                continue
            }
            if (p.kind == 0) {
                // Hearts float up, swaying.
                p.x += (p.vx - scene.gravityX * 45f * d + -scene.gravityY * sin(p.age * 4f + p.mark) * 20f * d) * dt
                p.y += (p.vy - scene.gravityY * 45f * d + scene.gravityX * sin(p.age * 4f + p.mark) * 20f * d) * dt
            } else {
                p.x += p.vx * dt
                p.y += p.vy * dt
            }
            alive = true
        }
        return alive || peek.value > 0.005f || abs(peek.velocity) > 0.01f || pop > 0.02f
    }

    protected open fun radius(scene: BumpScene) = max(scene.restHalf * 0.75f, scene.unit * 1.6f)

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val r = radius(scene)
        val pv = peek.value
        if (pv < 0.01f) return
        val cx = -r + pv * r * 1.6f
        val cy = scene.centre
        val up = uprightDegrees(scene)
        canvas.save()
        canvas.translate(cx, cy)
        val tilt = when (mood) {
            Mood.DIZZY -> sin(time * 6f) * 12f
            Mood.LAUGHING -> sin(time * 22f) * 4f
            Mood.ANGRY -> sin(time * 40f) * 2f
            else -> sin(time * 1.3f) * 4f
        }
        canvas.rotate(up + tilt + pop * 6f)
        canvas.scale(1f + pop * 0.06f, 1f - pop * 0.05f)
        drawHead(canvas, r, closed = blink > 0.3f && mood == Mood.CALM, look = look)
        drawMoodExtras(canvas, r)
        canvas.restore()
        if (pv > 0.3f) for (side in intArrayOf(-1, 1)) drawHand(canvas, r * 0.1f * pv, cy + side * r * 0.95f, r * 0.3f)
        for (p in extras.items) {
            if (!p.alive) continue
            val t = p.age / p.life
            if (p.kind == 0) {
                canvas.save()
                canvas.translate(p.x, p.y)
                canvas.rotate(up + sin(p.age * 3f + p.mark) * 12f)
                canvas.scale(p.size, p.size)
                fill.color = fade(p.color, if (t < 0.7f) 1f else (1f - t) / 0.3f)
                canvas.drawPath(heart, fill)
                canvas.restore()
            } else {
                val rr = p.size * (1f + 1.5f * t)
                steam.alpha = (200 * (1f - t)).toInt()
                canvas.save()
                canvas.translate(p.x, p.y)
                canvas.scale(rr, rr)
                canvas.drawCircle(0f, 0f, 1f, steam)
                canvas.restore()
            }
        }
    }

    /** Over the head, in its own upright frame: an anger mark, circling stars, a sweat drop. */
    private fun drawMoodExtras(canvas: Canvas, r: Float) {
        when (mood) {
            Mood.ANGRY -> {
                ink.strokeWidth = 0.09f * r
                ink.color = 0xFFE53935.toInt()
                val x = 0.62f * r
                val y = -0.72f * r
                val k = 0.16f * r
                for ((dx, dy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
                    oval.set(x + dx * k * 1.1f - k, y + dy * k * 1.1f - k, x + dx * k * 1.1f + k, y + dy * k * 1.1f + k)
                    val start = when {
                        dx < 0 && dy < 0 -> 0f
                        dx > 0 && dy < 0 -> 90f
                        dx > 0 && dy > 0 -> 180f
                        else -> 270f
                    }
                    canvas.drawArc(oval, start, 90f, false, ink)
                }
            }
            Mood.DIZZY -> for (i in 0 until 3) {
                val a = time * 4f + i * 2.094f
                canvas.save()
                canvas.translate(cos(a) * 0.8f * r, -1.05f * r + sin(a) * 0.18f * r)
                canvas.rotate(time * 200f)
                canvas.scale(0.16f * r, 0.16f * r)
                fill.color = 0xFFFFD54F.toInt()
                canvas.drawPath(starPath, fill)
                canvas.restore()
            }
            Mood.SURPRISED -> {
                fill.color = 0xCC81D4FA.toInt()
                canvas.save()
                canvas.translate(-0.88f * r, -0.42f * r)
                canvas.scale(0.12f * r, 0.12f * r)
                shape.reset()
                shape.moveTo(0f, -1.6f)
                shape.quadTo(1f, -0.2f, 0.7f, 0.5f)
                shape.quadTo(0f, 1.2f, -0.7f, 0.5f)
                shape.quadTo(-1f, -0.2f, 0f, -1.6f)
                shape.close()
                canvas.drawPath(shape, fill)
                canvas.restore()
            }
            else -> Unit
        }
    }

    protected abstract fun drawHead(canvas: Canvas, r: Float, closed: Boolean, look: Float)

    protected abstract fun drawHand(canvas: Canvas, x: Float, y: Float, r: Float)

    /**
     * One eye, kawaii, as the mood has it, centred at [x], [y], [w] wide and [h] tall. [side] is -1 for
     * the left eye and 1 for the right, for the shapes that mirror; [skin] covers a cross eye's lid.
     */
    protected fun eye(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, side: Int, closed: Boolean, look: Float, iris: Int, skin: Int) {
        val line = max(w * 0.28f, 1f)
        ink.color = 0xFF2B1B17.toInt()
        ink.strokeWidth = line
        when {
            closed || mood == Mood.HAPPY -> {
                // Shut in a happy curve.
                oval.set(x - w, y - h * 0.5f, x + w, y + h * 0.9f)
                canvas.drawArc(oval, 200f, 140f, false, ink)
            }
            mood == Mood.LAUGHING -> {
                // > <, squeezed shut with laughing.
                val dir = -side.toFloat()
                canvas.drawLine(x - w * 0.8f * dir, y - h * 0.55f, x + w * 0.8f * dir, y, ink)
                canvas.drawLine(x + w * 0.8f * dir, y, x - w * 0.8f * dir, y + h * 0.55f, ink)
                // A tear.
                fill.color = 0xCC81D4FA.toInt()
                canvas.drawCircle(x + side * w * 1.1f, y + h * (0.7f + 0.15f * sin(time * 20f)), w * 0.32f, fill)
            }
            mood == Mood.LOVE -> {
                canvas.save()
                canvas.translate(x, y)
                val beat = 1f + 0.12f * sin(time * 10f)
                canvas.scale(w * 1.15f * beat, w * 1.15f * beat)
                fill.color = 0xFFFF3D7F.toInt()
                canvas.drawPath(heart, fill)
                fill.color = 0x99FFFFFF.toInt()
                canvas.drawCircle(-0.3f, -0.35f, 0.16f, fill)
                canvas.restore()
            }
            mood == Mood.DIZZY -> {
                // A spiral, turning.
                shape.reset()
                val turns = 2.2f
                val n = 28
                for (i in 0..n) {
                    val k = i / n.toFloat()
                    val a = k * turns * 2f * PI.toFloat() + time * 8f * side
                    val rr = w * 0.95f * k
                    val px = x + cos(a) * rr
                    val py = y + sin(a) * rr * (h / w)
                    if (i == 0) shape.moveTo(px, py) else shape.lineTo(px, py)
                }
                ink.strokeWidth = line * 0.7f
                canvas.drawPath(shape, ink)
            }
            else -> {
                val big = if (mood == Mood.SURPRISED) 1.18f else 1f
                val ew = w * big
                val eh = h * big
                if (mood == Mood.SURPRISED) {
                    fill.color = 0xFFFFFFFF.toInt()
                    canvas.drawOval(x - ew, y - eh, x + ew, y + eh, fill)
                    fill.color = iris
                    canvas.drawCircle(x, y, ew * 0.45f, fill)
                    fill.color = 0xFFFFFFFF.toInt()
                    canvas.drawCircle(x - ew * 0.15f, y - ew * 0.15f, ew * 0.14f, fill)
                    return
                }
                val lx = x + look * w * 0.18f
                fill.color = iris
                canvas.drawOval(lx - ew, y - eh, lx + ew, y + eh, fill)
                // The shine that makes it cute: one big, one small.
                fill.color = 0xFFFFFFFF.toInt()
                canvas.drawCircle(lx - ew * 0.32f, y - eh * 0.38f, ew * 0.38f, fill)
                canvas.drawCircle(lx + ew * 0.35f, y + eh * 0.42f, ew * 0.16f, fill)
                if (mood == Mood.ANGRY) {
                    // A cross lid, slanting down to the middle.
                    fill.color = skin
                    shape.reset()
                    shape.moveTo(x - ew * 1.3f, y - eh * 1.4f)
                    shape.lineTo(x + ew * 1.3f, y - eh * 1.4f)
                    shape.lineTo(x + ew * 1.3f, y - eh * if (side > 0) 0.6f else -0.1f)
                    shape.lineTo(x - ew * 1.3f, y - eh * if (side > 0) -0.1f else 0.6f)
                    shape.close()
                    canvas.drawPath(shape, fill)
                    ink.strokeWidth = line
                    canvas.drawLine(x - ew * 1.2f, y - eh * if (side > 0) -0.05f else 0.65f, x + ew * 1.2f, y - eh * if (side > 0) 0.65f else -0.05f, ink)
                }
            }
        }
    }

    /** The mouth, as the mood has it, centred at [x], [y], [w] wide. */
    protected fun mouth(canvas: Canvas, x: Float, y: Float, w: Float, calm: (Canvas) -> Unit) {
        ink.color = 0xFF5A2A20.toInt()
        ink.strokeWidth = max(w * 0.16f, 1f)
        when (mood) {
            Mood.CALM -> calm(canvas)
            Mood.HAPPY, Mood.LAUGHING -> {
                val k = if (mood == Mood.LAUGHING) 1.35f else 1f
                fill.color = 0xFF8E2A2A.toInt()
                oval.set(x - w * k, y - w * 0.55f * k, x + w * k, y + w * 1.1f * k)
                canvas.drawArc(oval, 0f, 180f, true, fill)
                fill.color = 0xFFFF8A9A.toInt()
                canvas.drawOval(x - w * 0.55f * k, y + w * 0.2f * k, x + w * 0.55f * k, y + w * 0.62f * k, fill)
            }
            Mood.SURPRISED -> {
                fill.color = 0xFF8E2A2A.toInt()
                canvas.drawOval(x - w * 0.4f, y - w * 0.15f, x + w * 0.4f, y + w * 0.65f, fill)
            }
            Mood.LOVE -> {
                oval.set(x - w * 0.7f, y - w * 0.5f, x + w * 0.7f, y + w * 0.5f)
                canvas.drawArc(oval, 20f, 140f, false, ink)
            }
            Mood.ANGRY -> {
                oval.set(x - w * 0.7f, y + w * 0.1f, x + w * 0.7f, y + w * 0.9f)
                canvas.drawArc(oval, 200f, 140f, false, ink)
            }
            Mood.DIZZY -> {
                shape.reset()
                for (i in 0..12) {
                    val k = i / 12f
                    val px = x - w * 0.8f + w * 1.6f * k
                    val py = y + w * 0.25f + sin(k * 3f * 2f * PI.toFloat() + time * 6f) * w * 0.14f
                    if (i == 0) shape.moveTo(px, py) else shape.lineTo(px, py)
                }
                canvas.drawPath(shape, ink)
            }
        }
    }

    /** Rosy cheeks, rosier when it is laughing or in love, red when it is cross. */
    protected fun cheeks(canvas: Canvas, x: Float, y: Float, r: Float) {
        val colour = when (mood) {
            Mood.ANGRY -> 0x99FF5252.toInt()
            Mood.LOVE, Mood.LAUGHING -> 0xA6FF7A9A.toInt()
            else -> 0x66FF8A9A
        }
        fill.color = colour
        canvas.drawOval(-x - r, y - r * 0.65f, -x + r, y + r * 0.65f, fill)
        canvas.drawOval(x - r, y - r * 0.65f, x + r, y + r * 0.65f, fill)
    }

    override fun clear() {
        peek.snapTo(0f)
        pop = 0f
        extras.clear()
        mood = Mood.CALM
        streak = 0
        sincePress = 10f
    }

    private companion object {
        /** Presses closer together than this are one run, and the feeling grows with it. */
        const val STREAK_S = 1.3f
        const val CALM_S = 2.5f
    }
}

/** A ginger kitten peeking round the edge: soft ears, big shiny eyes, toe-beaned paws on the frame. */
internal class CatEffect(random: Random) : PeekEffect(random) {
    private val ear = Path()
    private var shadeR = 0f
    private val headShade = solidFill()

    override fun drawHead(canvas: Canvas, r: Float, closed: Boolean, look: Float) {
        if (shadeR != r) {
            shadeR = r
            headShade.shader = RadialGradient(-0.1f * r, -0.25f * r, 1.25f * r, intArrayOf(0xFFFFE0AE.toInt(), 0xFFFBB45C.toInt(), 0xFFEE9437.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        }
        // Ears, rounded, twitching at a press.
        for (side in intArrayOf(-1, 1)) {
            canvas.save()
            canvas.rotate(side * pop * 14f, side * 0.6f * r, -0.62f * r)
            ear.reset()
            ear.moveTo(side * 0.18f * r, -0.74f * r)
            ear.quadTo(side * 0.7f * r, -1.32f * r, side * 0.98f * r, -0.98f * r)
            ear.quadTo(side * 1.0f * r, -0.6f * r, side * 0.88f * r, -0.3f * r)
            ear.close()
            fill.color = 0xFFF2A043.toInt()
            canvas.drawPath(ear, fill)
            ear.reset()
            ear.moveTo(side * 0.36f * r, -0.7f * r)
            ear.quadTo(side * 0.7f * r, -1.12f * r, side * 0.86f * r, -0.88f * r)
            ear.quadTo(side * 0.86f * r, -0.62f * r, side * 0.78f * r, -0.42f * r)
            ear.close()
            fill.color = 0xFFFFB3B8.toInt()
            canvas.drawPath(ear, fill)
            canvas.restore()
        }
        canvas.drawOval(-1.08f * r, -0.92f * r, 1.08f * r, 0.9f * r, headShade)
        ink.strokeWidth = 0.045f * r
        ink.color = 0x55C66A1E
        canvas.drawOval(-1.08f * r, -0.92f * r, 1.08f * r, 0.9f * r, ink)
        // Tabby marks on the forehead.
        ink.strokeWidth = 0.075f * r
        ink.color = 0xFFE58A32.toInt()
        for (x in floatArrayOf(-0.2f, 0f, 0.2f)) canvas.drawLine(x * r, -0.86f * r, x * r * 0.75f, -0.66f * r, ink)
        // The muzzle.
        fill.color = 0xFFFFF5E8.toInt()
        canvas.drawCircle(-0.16f * r, 0.36f * r, 0.21f * r, fill)
        canvas.drawCircle(0.16f * r, 0.36f * r, 0.21f * r, fill)
        cheeks(canvas, 0.62f * r, 0.22f * r, 0.15f * r)
        for (side in intArrayOf(-1, 1)) {
            eye(canvas, side * 0.4f * r, -0.04f * r, 0.17f * r, 0.21f * r, side, closed, look, 0xFF3B2A23.toInt(), 0xFFFBB45C.toInt())
        }
        // Nose and whiskers.
        shape.reset()
        shape.moveTo(-0.08f * r, 0.17f * r)
        shape.quadTo(0f, 0.13f * r, 0.08f * r, 0.17f * r)
        shape.quadTo(0.02f * r, 0.27f * r, 0f, 0.27f * r)
        shape.quadTo(-0.02f * r, 0.27f * r, -0.08f * r, 0.17f * r)
        shape.close()
        fill.color = 0xFFFF8FA3.toInt()
        canvas.drawPath(shape, fill)
        mouth(canvas, 0f, 0.3f * r, 0.16f * r) { c ->
            // ω
            oval.set(-0.17f * r, 0.2f * r, 0f, 0.38f * r)
            c.drawArc(oval, 20f, 140f, false, ink)
            oval.set(0f, 0.2f * r, 0.17f * r, 0.38f * r)
            c.drawArc(oval, 20f, 140f, false, ink)
        }
        ink.strokeWidth = 0.022f * r
        ink.color = 0x995D4037.toInt()
        for (side in intArrayOf(-1, 1)) {
            canvas.drawLine(side * 0.36f * r, 0.3f * r, side * 1.18f * r, 0.2f * r, ink)
            canvas.drawLine(side * 0.36f * r, 0.36f * r, side * 1.2f * r, 0.38f * r, ink)
        }
    }

    override fun drawHand(canvas: Canvas, x: Float, y: Float, r: Float) {
        fill.color = 0xFFFBB45C.toInt()
        canvas.drawCircle(x, y, r, fill)
        fill.color = 0xFFFFB3B8.toInt()
        canvas.drawCircle(x + r * 0.15f, y, r * 0.35f, fill)
        for (k in -1..1) canvas.drawCircle(x + r * 0.62f, y + k * r * 0.45f, r * 0.15f, fill)
    }
}

/** A little monkey peeking round the edge: round ears, a heart-shaped face, big eyes, a cheeky grin. */
internal class MonkeyEffect(random: Random) : PeekEffect(random) {
    private var shadeR = 0f
    private val headShade = solidFill()

    override fun drawHead(canvas: Canvas, r: Float, closed: Boolean, look: Float) {
        if (shadeR != r) {
            shadeR = r
            headShade.shader = RadialGradient(-0.15f * r, -0.3f * r, 1.3f * r, intArrayOf(0xFFB98050.toInt(), 0xFF8B5530.toInt(), 0xFF6E4122.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        }
        val skin = 0xFFFFE0C2.toInt()
        for (side in intArrayOf(-1, 1)) {
            fill.color = 0xFF7E4A27.toInt()
            canvas.drawCircle(side * 0.98f * r, 0.02f * r, 0.34f * r, fill)
            fill.color = 0xFFFFC9A8.toInt()
            canvas.drawCircle(side * 0.98f * r, 0.02f * r, 0.21f * r, fill)
        }
        canvas.drawCircle(0f, 0f, r, headShade)
        // A tuft on top.
        ink.strokeWidth = 0.08f * r
        ink.color = 0xFF6E4122.toInt()
        oval.set(-0.12f * r, -1.18f * r, 0.16f * r, -0.9f * r)
        canvas.drawArc(oval, 140f, 220f, false, ink)
        // The face: round under each eye, a broad muzzle.
        fill.color = skin
        canvas.drawCircle(-0.3f * r, -0.1f * r, 0.4f * r, fill)
        canvas.drawCircle(0.3f * r, -0.1f * r, 0.4f * r, fill)
        canvas.drawOval(-0.62f * r, 0f, 0.62f * r, 0.8f * r, fill)
        cheeks(canvas, 0.5f * r, 0.32f * r, 0.13f * r)
        for (side in intArrayOf(-1, 1)) {
            eye(canvas, side * 0.3f * r, -0.1f * r, 0.15f * r, 0.19f * r, side, closed, look, 0xFF2B1B17.toInt(), skin)
        }
        fill.color = 0xFF7E4A27.toInt()
        canvas.drawCircle(-0.07f * r, 0.26f * r, 0.035f * r, fill)
        canvas.drawCircle(0.07f * r, 0.26f * r, 0.035f * r, fill)
        mouth(canvas, 0f, 0.45f * r, 0.24f * r) { c ->
            oval.set(-0.28f * r, 0.26f * r, 0.28f * r, 0.6f * r)
            c.drawArc(oval, 20f, 140f, false, ink)
        }
    }

    override fun drawHand(canvas: Canvas, x: Float, y: Float, r: Float) {
        fill.color = 0xFF8B5530.toInt()
        canvas.drawCircle(x, y, r, fill)
        fill.color = 0xFFFFE0C2.toInt()
        canvas.drawCircle(x + r * 0.25f, y, r * 0.55f, fill)
    }
}

/** A little green alien peeking round the edge: huge glossy eyes, antennae bobbing, rosy cheeks. */
internal class AlienEffect(random: Random) : PeekEffect(random) {
    private val head = Path()
    private var shadeR = 0f
    private val headShade = solidFill()
    private val glow = solidFill().apply { shader = softPuff(0xFFFFF59D.toInt()) }

    override fun drawHead(canvas: Canvas, r: Float, closed: Boolean, look: Float) {
        if (shadeR != r) {
            shadeR = r
            headShade.shader = RadialGradient(-0.2f * r, -0.4f * r, 1.4f * r, intArrayOf(0xFFC8F7B4.toInt(), 0xFF8BE07A.toInt(), 0xFF5DBF5A.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        for (side in intArrayOf(-1, 1)) {
            val sway = sin(time * 4f + side) * 0.12f * r
            val tx = side * 0.62f * r + sway
            val ty = -1.36f * r
            ink.strokeWidth = 0.06f * r
            ink.color = 0xFF58B653.toInt()
            canvas.drawLine(side * 0.3f * r, -0.82f * r, tx, ty, ink)
            glow.alpha = (140 + 115 * pop).toInt()
            canvas.save()
            canvas.translate(tx, ty)
            canvas.scale(0.24f * r, 0.24f * r)
            canvas.drawCircle(0f, 0f, 1f, glow)
            canvas.restore()
            fill.color = if (side < 0) 0xFFFF8FB4.toInt() else 0xFFFFE066.toInt()
            canvas.drawCircle(tx, ty, 0.11f * r, fill)
        }
        head.reset()
        head.moveTo(0f, -1f * r)
        head.cubicTo(1f * r, -1f * r, 1.08f * r, 0f, 0.55f * r, 0.62f * r)
        head.quadTo(0f, 1.02f * r, -0.55f * r, 0.62f * r)
        head.cubicTo(-1.08f * r, 0f, -1f * r, -1f * r, 0f, -1f * r)
        head.close()
        canvas.drawPath(head, headShade)
        ink.strokeWidth = 0.045f * r
        ink.color = 0x6640963E
        canvas.drawPath(head, ink)
        cheeks(canvas, 0.6f * r, 0.32f * r, 0.12f * r)
        for (side in intArrayOf(-1, 1)) {
            eye(canvas, side * 0.38f * r, 0.02f * r, 0.27f * r, 0.23f * r, side, closed, look, 0xFF14161F.toInt(), 0xFF8BE07A.toInt())
        }
        mouth(canvas, 0f, 0.55f * r, 0.13f * r) { c ->
            oval.set(-0.13f * r, 0.42f * r, 0.13f * r, 0.6f * r)
            c.drawArc(oval, 20f, 140f, false, ink)
        }
    }

    override fun drawHand(canvas: Canvas, x: Float, y: Float, r: Float) {
        ink.strokeWidth = r * 0.32f
        ink.color = 0xFF8BE07A.toInt()
        for (k in -1..1) canvas.drawLine(x - r * 0.2f, y + k * r * 0.45f, x + r * 0.9f, y + k * r * 0.5f, ink)
        fill.color = 0xFF8BE07A.toInt()
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
        val s = saucers.firstOrNull { !it.alive } ?: saucers.maxByOrNull { it.age } ?: return
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
                    s.vx += 900f * d * dt
                    s.vx -= scene.gravityX * 900f * d * dt
                    s.vy -= scene.gravityY * 900f * d * dt
                    s.x += s.vx * dt
                    s.y += s.vy * dt
                }
            }
            if (t > 3f || scene.offScreen(s.x, s.y, 40f * d)) s.alive = false else alive = true
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
            for (i in 0 until 5) {
                val lx = (-11f + 5.5f * i) * d
                val colour = when (((s.age * 8f).toInt() + i) % 3) {
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
