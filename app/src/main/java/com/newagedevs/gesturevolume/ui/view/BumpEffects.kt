package com.newagedevs.gesturevolume.ui.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import com.newagedevs.gesturevolume.utils.ButtonBump
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
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
 * The button animation's showpiece styles, each thrown out from the button that was pressed. The
 * shared parts are here; the effects themselves are here and in the BumpEffects* files beside it.
 *
 * Every one of them is drawn by [ButtonBumpView] in the bump's upright terms — x inward from the
 * glass, y along the edge towards the bottom of the phone — so each is written once and works on
 * every edge and in every rotation. What an effect knows of the bump is a [BumpScene]; what it does is
 * a [BumpEffect]: told of each press, stepped each frame, drawn behind and in front of the bump.
 */

/** What an effect knows of the bump, each frame, in its upright terms. */
internal class BumpScene {
    var density = 1f

    /** The bump as drawn this frame: its middle along the edge, its half-length and its depth. */
    var centre = 0f
    var half = 0f
    var depth = 0f

    /** The bump at its full size, whatever its spring is doing. */
    var restHalf = 0f
    var restDepth = 0f

    /** The window: how far it reaches into the screen, and how long it is along the edge. */
    var reach = 0f
    var span = 0f

    /** How far out the bump is: 0 in the glass, 1 out, a little past 1 as it overshoots. */
    var presence = 0f
    var level = 0.5f
    var color = Color.BLACK
    var accent = Color.WHITE
    var showLevel = true

    /** Along the edge towards the button that was pressed: -1 towards the top of the phone, +1 the bottom. */
    var towards = -1

    /** Which way is down on the screen, in upright terms: things fall that way and bubbles rise the other. */
    var gravityX = 0f
    var gravityY = 1f

    /** The bump's outline, open at the glass, for the effects that light it. */
    val rim = Path()

    /** The size the showpieces are drawn to, whatever the bump's own depth: so a 2dp bump still throws full-size ones. */
    var unit = 18f

    /** The whole screen, in upright terms: where things may fly, and past which they are gone. */
    var screenLeft = 0f
    var screenTop = 0f
    var screenRight = 0f
    var screenBottom = 0f

    fun offScreen(x: Float, y: Float, margin: Float): Boolean =
        x < screenLeft - margin || x > screenRight + margin || y < screenTop - margin || y > screenBottom + margin
}

/** One of the showpiece styles. */
internal abstract class BumpEffect(protected val random: Random) {

    /** Whether the plain bump, with the level in it, is drawn between [drawBehind] and [drawFront]. */
    open val drawsBump: Boolean get() = true

    /** Whether it keeps moving for as long as the bump is out, pressed or not. */
    open val loops: Boolean get() = false

    /** A button was pressed. */
    abstract fun press(scene: BumpScene, atLimit: Boolean)

    /** Moves on by [dt] seconds. Whether anything of the effect's own is still moving. */
    abstract fun step(scene: BumpScene, dt: Float): Boolean

    open fun drawBehind(canvas: Canvas, scene: BumpScene) {}
    open fun drawFront(canvas: Canvas, scene: BumpScene) {}

    /** Everything gone, at once. */
    abstract fun clear()

    /** How long after a press a gallery tile's frozen frame is taken. */
    protected open val stillAfterS: Float get() = 0.3f

    /** A frozen frame for a gallery tile: the same press every time, a moment after. */
    fun prime(scene: BumpScene) {
        random.setSeed(STILL_SEED)
        clear()
        press(scene, false)
        var t = 0f
        while (t < stillAfterS) {
            step(scene, FRAME_S)
            t += FRAME_S
        }
    }

    protected fun rnd(from: Float, to: Float): Float = from + (to - from) * random.nextFloat()

    companion object {
        const val FRAME_S = 1f / 60f
        private const val STILL_SEED = 1734L
    }
}

/** A thing in flight: a petal, a spark, a star of a firework. Pooled, so nothing is made per frame. */
internal class Particle {
    var alive = false
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var age = 0f
    var life = 1f
    var size = 1f
    var color = 0
    var angle = 0f
    var spin = 0f
    var phase = 0f
    var rate = 0f
    var kind = 0
    var mark = 0f
    var sx = 0f
    var sy = 0f
    var tx = 0f
    var ty = 0f
}

internal class ParticlePool(capacity: Int) {
    val items = Array(capacity) { Particle() }

    /** A free particle, reset, or null when every one is in flight. */
    fun spawn(): Particle? {
        for (p in items) {
            if (!p.alive) {
                p.alive = true
                p.age = 0f
                p.kind = 0
                p.mark = 0f
                p.angle = 0f
                p.spin = 0f
                return p
            }
        }
        return null
    }

    fun clear() {
        for (p in items) p.alive = false
    }
}

/** [colour] with its alpha multiplied by [a]. */
internal fun fade(colour: Int, a: Float): Int {
    val alpha = ((colour ushr 24) * a.coerceIn(0f, 1f)).roundToInt()
    return (alpha shl 24) or (colour and 0xFFFFFF)
}

/** Between two colours, channel by channel. */
internal fun blend(from: Int, to: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    fun ch(shift: Int): Int {
        val a = from ushr shift and 0xFF
        val b = to ushr shift and 0xFF
        return (a + (b - a) * k).roundToInt() and 0xFF
    }
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

internal object BumpEffects {
    /** The effect a style is drawn with, or null for the styles drawn by the view itself. */
    fun create(style: String, random: Random): BumpEffect? = when (style) {
        ButtonBump.STYLE_BLOSSOM -> BlossomEffect(random)
        ButtonBump.STYLE_FIREWORKS -> FireworksEffect(random)
        ButtonBump.STYLE_SOLAR -> SolarEffect(random)
        ButtonBump.STYLE_BUBBLES -> BubblesEffect(random)
        ButtonBump.STYLE_ATOM -> AtomEffect(random)
        ButtonBump.STYLE_VOLCANO -> VolcanoEffect(random)
        ButtonBump.STYLE_WIND -> WindEffect(random)
        ButtonBump.STYLE_NINJA -> NinjaEffect(random)
        ButtonBump.STYLE_DRAGON -> DragonEffect(random)
        ButtonBump.STYLE_BEES -> BeesEffect(random)
        ButtonBump.STYLE_ROCKET -> RocketEffect(random)
        ButtonBump.STYLE_SPRAY -> SprayEffect(random)
        ButtonBump.STYLE_CRACK -> CrackEffect(random)
        ButtonBump.STYLE_SMOKE -> SmokeEffect(random)
        ButtonBump.STYLE_CAT -> CatEffect(random)
        ButtonBump.STYLE_FISH -> FishEffect(random)
        ButtonBump.STYLE_MONKEY -> MonkeyEffect(random)
        ButtonBump.STYLE_ALIEN -> AlienEffect(random)
        ButtonBump.STYLE_UFO -> UfoEffect(random)
        else -> null
    }
}

// ---- cherry blossom ---------------------------------------------------------------------------------

/**
 * Every press shakes cherry petals loose from the bump: they flutter out into the screen, turning over
 * as they go, and drift down on the air.
 */
internal class BlossomEffect(random: Random) : BumpEffect(random) {
    private val petals = ParticlePool(120)
    private val petal = Path()
    private val petalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shaders: Array<Shader>

    init {
        // A cherry petal: its base at the origin, the notched tip at x = 1.
        petal.moveTo(0f, 0f)
        petal.cubicTo(0.2f, -0.44f, 0.78f, -0.48f, 1f, -0.17f)
        petal.lineTo(0.83f, 0f)
        petal.lineTo(1f, 0.17f)
        petal.cubicTo(0.78f, 0.48f, 0.2f, 0.44f, 0f, 0f)
        petal.close()
        // Deeper at the base, paler at the tip, in the unit petal's own coordinates.
        shaders = Array(PETALS.size) { i ->
            LinearGradient(0f, 0f, 1f, 0f, PETALS[i][0], PETALS[i][1], Shader.TileMode.CLAMP)
        }
    }

    override val stillAfterS: Float get() = 0.55f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        repeat(if (atLimit) 5 else 12) { spawn(scene) }
    }

    private fun spawn(scene: BumpScene) {
        val p = petals.spawn() ?: return
        val d = scene.density
        p.x = scene.restDepth * 0.9f + rnd(0f, 5f) * d
        p.y = scene.centre + scene.restHalf * rnd(-0.6f, 0.6f)
        val angle = rnd(-1.15f, 1.15f) + scene.towards * 0.25f
        val speed = rnd(80f, 210f) * d
        p.vx = cos(angle) * speed
        p.vy = sin(angle) * speed
        p.life = rnd(2.4f, 3.6f)
        p.size = rnd(6.5f, 10.5f) * d
        p.angle = rnd(0f, 360f)
        p.spin = rnd(-200f, 200f)
        p.phase = rnd(0f, 6.28f)
        p.rate = rnd(1.4f, 3.2f)
        p.mark = rnd(0f, 6.28f)
        p.kind = random.nextInt(PETALS.size)
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        val d = scene.density
        val drag = exp(-1.8f * dt)
        val acrossX = -scene.gravityY
        val acrossY = scene.gravityX
        var alive = false
        for (p in petals.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.life || scene.offScreen(p.x, p.y, 20f * d)) {
                p.alive = false
                continue
            }
            // Falling slowly, and swaying side to side as it does.
            val sway = sin(p.age * 2.3f + p.mark) * 95f
            p.vx = p.vx * drag + (scene.gravityX * FALL + acrossX * sway) * d * dt
            p.vy = p.vy * drag + (scene.gravityY * FALL + acrossY * sway) * d * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.angle += p.spin * dt
            p.phase += p.rate * 6.283f * dt
            alive = true
        }
        return alive
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        for (p in petals.items) {
            if (!p.alive) continue
            val t = p.age / p.life
            val a = if (t < 0.72f) min(1f, p.age / 0.12f) else (1f - t) / 0.28f
            val flip = cos(p.phase)
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.angle)
            canvas.scale(p.size, p.size * max(abs(flip), 0.16f))
            petalPaint.shader = shaders[p.kind]
            // The far side of a turning petal a little darker, as it is out of the light.
            petalPaint.alpha = ((if (flip < 0f) 0.82f else 0.97f) * a * 255f).roundToInt()
            canvas.drawPath(petal, petalPaint)
            canvas.restore()
        }
        petalPaint.shader = null
    }

    override fun clear() {
        petals.clear()
    }

    private companion object {
        /** How fast a petal falls, in dp a second each second, before the air slows it. */
        const val FALL = 70f

        /** Sakura pinks: the base of each petal and its tip. */
        val PETALS = arrayOf(
            intArrayOf(0xFFF27EA6.toInt(), 0xFFFFD6E3.toInt()),
            intArrayOf(0xFFF59ABB.toInt(), 0xFFFFE6EE.toInt()),
            intArrayOf(0xFFE8668F.toInt(), 0xFFFFC2D4.toInt()),
            intArrayOf(0xFFF7A8C4.toInt(), 0xFFFFF0F5.toInt()),
        )
    }
}

// ---- fireworks ----------------------------------------------------------------------------------------

/**
 * A firework for every press: a rocket climbs out of the bump trailing gold, and bursts into a
 * shell — a chrysanthemum, a ring with a core, a weeping willow, a two-tone peony that glitters as it
 * fades, or a heart — in the next of a set of hand-picked palettes.
 */
internal class FireworksEffect(random: Random) : BumpEffect(random) {
    private val rockets = ParticlePool(6)
    private val stars = ParticlePool(520)
    private val flashes = ParticlePool(6)
    private var nextShell = 0
    private var nextPalette = 0
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override val stillAfterS: Float get() = 0.78f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        val r = rockets.spawn() ?: return
        r.sx = scene.restDepth * 0.9f
        r.sy = scene.centre + scene.restHalf * rnd(-0.3f, 0.3f)
        r.tx = scene.reach * rnd(0.46f, 0.64f)
        r.ty = (scene.centre + scene.span * rnd(-0.28f, 0.28f)).coerceIn(scene.span * 0.2f, scene.span * 0.8f)
        r.x = r.sx
        r.y = r.sy
        r.life = rnd(0.36f, 0.48f)
        r.kind = nextShell++ % SHELLS
        r.color = nextPalette++ % PALETTES.size
        r.mark = if (atLimit) 0.6f else 1f
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        val d = scene.density
        var alive = false
        for (r in rockets.items) {
            if (!r.alive) continue
            r.age += dt
            val t = (r.age / r.life).coerceAtMost(1f)
            val e = 1f - (1f - t).pow(3)
            r.x = r.sx + (r.tx - r.sx) * e
            r.y = r.sy + (r.ty - r.sy) * e
            // A trail of gold sparkle behind it.
            repeat(2) {
                val s = stars.spawn() ?: return@repeat
                s.x = r.x + rnd(-1.5f, 1.5f) * d
                s.y = r.y + rnd(-1.5f, 1.5f) * d
                s.vx = rnd(-18f, 18f) * d
                s.vy = rnd(-18f, 18f) * d
                s.life = rnd(0.22f, 0.4f)
                s.size = rnd(0.9f, 1.5f) * d
                s.color = GOLD
                s.kind = TRAIL
            }
            if (r.age >= r.life) {
                burst(scene, r)
                r.alive = false
            }
            alive = true
        }
        val g = 150f * d
        for (s in stars.items) {
            if (!s.alive) continue
            s.age += dt
            if (s.age >= s.life) {
                s.alive = false
                continue
            }
            val drag = exp(-(if (s.kind == WILLOW) 2.8f else 2.05f) * dt)
            val fall = if (s.kind == WILLOW) g * 1.25f else if (s.kind == TRAIL) g * 0.3f else g
            s.vx = s.vx * drag + scene.gravityX * fall * dt
            s.vy = s.vy * drag + scene.gravityY * fall * dt
            s.x += s.vx * dt
            s.y += s.vy * dt
            alive = true
        }
        for (f in flashes.items) {
            if (!f.alive) continue
            f.age += dt
            if (f.age >= f.life) f.alive = false else alive = true
        }
        return alive
    }

    private fun burst(scene: BumpScene, r: Particle) {
        val d = scene.density
        val palette = PALETTES[r.color]
        val v0 = min(scene.reach, scene.span * 0.6f) * 0.88f * r.mark
        flashes.spawn()?.let { f ->
            f.x = r.x
            f.y = r.y
            f.life = 0.24f
            f.size = 30f * d * r.mark
            f.color = palette[0]
        }
        fun star(angle: Float, speed: Float, colour: Int, kind: Int, life: Float) {
            val s = stars.spawn() ?: return
            s.x = r.x
            s.y = r.y
            s.vx = cos(angle) * speed
            s.vy = sin(angle) * speed
            s.color = colour
            s.kind = kind
            s.life = life
            s.size = rnd(1.5f, 2.2f) * d
            s.phase = rnd(0f, 6.28f)
        }
        val tau = 2f * PI.toFloat()
        when (r.kind) {
            CHRYSANTHEMUM -> {
                val n = 56
                for (i in 0 until n) {
                    star(tau * i / n + rnd(-0.05f, 0.05f), v0 * rnd(0.85f, 1.08f), palette[0], STAR, rnd(1.1f, 1.5f))
                }
            }
            RING -> {
                val n = 40
                val tilt = rnd(0f, tau)
                for (i in 0 until n) star(tilt + tau * i / n, v0, palette[1], STAR, rnd(1.1f, 1.4f))
                for (i in 0 until 14) star(rnd(0f, tau), v0 * rnd(0.25f, 0.45f), palette[0], GLITTER, rnd(0.9f, 1.2f))
            }
            WILLOW -> {
                val n = 46
                for (i in 0 until n) {
                    star(tau * i / n + rnd(-0.06f, 0.06f), v0 * rnd(0.75f, 0.92f), GOLD, WILLOW, rnd(1.9f, 2.4f))
                }
            }
            PEONY -> {
                val n = 64
                for (i in 0 until n) {
                    val colour = if (i % 2 == 0) palette[0] else palette[1]
                    star(rnd(0f, tau), v0 * rnd(0.55f, 1.05f), colour, GLITTER, rnd(1.2f, 1.6f))
                }
            }
            else -> {
                // A heart, the right way up on the screen whichever edge the buttons are on.
                val upX = -scene.gravityX
                val upY = -scene.gravityY
                val rightX = scene.gravityY
                val rightY = -scene.gravityX
                val n = 52
                for (i in 0 until n) {
                    val t = tau * i / n
                    val hx = 16f * sin(t).pow(3)
                    val hy = 13f * cos(t) - 5f * cos(2f * t) - 2f * cos(3f * t) - cos(4f * t)
                    val vx = (hx * rightX + hy * upX) / 17f * v0
                    val vy = (hx * rightY + hy * upY) / 17f * v0
                    val s = stars.spawn() ?: break
                    s.x = r.x
                    s.y = r.y
                    s.vx = vx
                    s.vy = vy
                    s.color = palette[1]
                    s.kind = HEART
                    s.life = rnd(1.1f, 1.35f)
                    s.size = rnd(1.6f, 2.2f) * d
                }
            }
        }
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        for (f in flashes.items) {
            if (!f.alive) continue
            val t = f.age / f.life
            val radius = f.size * (0.6f + 0.8f * t)
            flashPaint.shader = RadialGradient(
                f.x, f.y, radius,
                intArrayOf(fade(Color.WHITE, 0.65f * (1f - t)), fade(f.color, 0.3f * (1f - t)), 0),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(f.x, f.y, radius, flashPaint)
        }
        flashPaint.shader = null
        for (s in stars.items) {
            if (!s.alive) continue
            val t = s.age / s.life
            var a = (1f - t).pow(1.3f)
            // Glitter: the star crackles on and off as it dies.
            if (s.kind == GLITTER && t > 0.55f) a *= if (sin(s.age * 47f + s.phase) > 0f) 1f else 0.2f
            val trail = when (s.kind) {
                WILLOW -> 0.11f
                TRAIL -> 0f
                else -> 0.05f
            }
            if (trail > 0f) {
                line.strokeWidth = s.size * 2.6f
                line.color = fade(s.color, 0.22f * a)
                canvas.drawLine(s.x - s.vx * trail, s.y - s.vy * trail, s.x, s.y, line)
                line.strokeWidth = s.size
                line.color = fade(blend(s.color, Color.WHITE, 0.35f * (1f - t)), a)
                canvas.drawLine(s.x - s.vx * trail * 0.6f, s.y - s.vy * trail * 0.6f, s.x, s.y, line)
            } else {
                dot.color = fade(s.color, a)
                canvas.drawCircle(s.x, s.y, s.size * 0.6f, dot)
            }
        }
        for (r in rockets.items) {
            if (!r.alive) continue
            dot.color = fade(GOLD, 0.35f)
            canvas.drawCircle(r.x, r.y, 4f * d, dot)
            dot.color = Color.WHITE
            canvas.drawCircle(r.x, r.y, 1.6f * d, dot)
        }
    }

    override fun clear() {
        rockets.clear()
        stars.clear()
        flashes.clear()
    }

    private companion object {
        const val SHELLS = 5
        const val CHRYSANTHEMUM = 0
        const val RING = 1
        const val WILLOW = 2
        const val PEONY = 3

        const val STAR = 0
        const val TRAIL = 1
        const val GLITTER = 2
        const val HEART = 3

        val GOLD = 0xFFFFD36B.toInt()

        /** Pairs that sit well together in a night sky. */
        val PALETTES = arrayOf(
            intArrayOf(0xFFFF5C8A.toInt(), 0xFFFFD36B.toInt()),
            intArrayOf(0xFF4DD0E1.toInt(), 0xFFE040FB.toInt()),
            intArrayOf(0xFFB2FF59.toInt(), 0xFF64FFDA.toInt()),
            intArrayOf(0xFFFF9E40.toInt(), 0xFFFF5252.toInt()),
            intArrayOf(0xFF8C7BFF.toInt(), 0xFFFFFFFF.toInt()),
            intArrayOf(0xFFFF6AD5.toInt(), 0xFF7CFFB2.toInt()),
        )
    }
}

// ---- the solar system -------------------------------------------------------------------------------

/**
 * The bump becomes the sun, and the planets come out round it — each on its own orbit, at its own
 * pace, disappearing behind the edge of the glass for half of it. A press flares the sun and sends
 * them all whirling faster for a moment. The level, when it is shown, is an arc round the sun.
 */
internal class SolarEffect(random: Random) : BumpEffect(random) {
    override val drawsBump: Boolean get() = false
    override val loops: Boolean get() = true

    private class Planet(
        val sizeDp: Float,
        val colour: Int,
        val shade: Int,
        val period: Float,
        val ring: Boolean = false,
        val moon: Boolean = false,
    ) {
        var angle = 0f
    }

    private val planets = listOf(
        Planet(2.0f, 0xFFC9C3BA.toInt(), 0xFF6E6A64.toInt(), 1.8f),
        Planet(3.0f, 0xFFF6DDA6.toInt(), 0xFF9E7A3A.toInt(), 2.6f),
        Planet(3.3f, 0xFF4F9BFF.toInt(), 0xFF173C7A.toInt(), 3.4f, moon = true),
        Planet(2.5f, 0xFFF0784A.toInt(), 0xFF7A2A12.toInt(), 4.6f),
        Planet(5.8f, 0xFFE8C49A.toInt(), 0xFF8A5A33.toInt(), 7f),
        Planet(4.7f, 0xFFF2DDA8.toInt(), 0xFF9A7B45.toInt(), 9f, ring = true),
        Planet(3.7f, 0xFF9BEDF0.toInt(), 0xFF2E7F86.toInt(), 12f),
        Planet(3.6f, 0xFF5C7CFF.toInt(), 0xFF1E2C80.toInt(), 15f),
    )
    private val starU = FloatArray(28)
    private val starV = FloatArray(28)
    private val starPhase = FloatArray(28)
    private var time = 0f
    private var boost = 0f
    private var flare = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val oval = RectF()

    private val unitCorona = RadialGradient(
        0f, 0f, 2f,
        intArrayOf(0x70FFB13D, 0x30FF8A1E, 0),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val unitSun = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(0xFFFFFBE8.toInt(), 0xFFFFD45E.toInt(), 0xFFFF8A1E.toInt()),
        floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP,
    )

    /** Each planet lit from one side in its own unit circle; turned to face the sun as it is drawn. */
    private val unitPlanets = planets.map { planet ->
        RadialGradient(
            -0.5f, 0f, 1.7f,
            intArrayOf(blend(planet.colour, Color.WHITE, 0.35f), planet.colour, planet.shade),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    init {
        val seeded = Random(42)
        for (i in starU.indices) {
            starU[i] = seeded.nextFloat()
            starV[i] = seeded.nextFloat()
            starPhase[i] = seeded.nextFloat() * 6.28f
        }
        // Spread round their orbits, not lined up.
        planets.forEachIndexed { i, p -> p.angle = i * 2.39996f - 0.6f }
    }

    override val stillAfterS: Float get() = 0f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        boost = min(boost + if (atLimit) 0.5f else 1f, 2.5f)
        flare = 1f
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        time += dt
        boost *= exp(-1.3f * dt)
        flare *= exp(-2.4f * dt)
        val pace = 1f + boost * 3f
        for (p in planets) p.angle += (2f * PI.toFloat() / p.period) * pace * dt
        return flare > 0.02f || boost > 0.02f
    }

    override fun drawBehind(canvas: Canvas, scene: BumpScene) {
        val out = scene.presence.coerceIn(0f, 1.15f)
        if (out < 0.01f) return
        val d = scene.density
        val cy = scene.centre
        val full = max(scene.unit * 1.2f, scene.restHalf * 0.6f)
        val sun = full * out
        val opened = ButtonBump.smootherstep(min(out, 1f))
        val scale = (min(scene.reach, scene.span * 0.5f) / (150f * d)).coerceIn(0.4f, 1.2f)

        // A scatter of faint stars, twinkling.
        for (i in starU.indices) {
            val tw = 0.5f + 0.5f * sin(time * 2.2f + starPhase[i])
            fill.color = fade(Color.WHITE, 0.5f * opened * tw)
            canvas.drawCircle(starU[i] * scene.reach, starV[i] * scene.span, 0.9f * d, fill)
        }

        // The orbits.
        val first = full * 1.75f
        val last = min(scene.reach * 0.92f, scene.span * 0.46f)
        val gap = ((last - first) / (planets.size - 1)).coerceAtLeast(5f * d)
        ink.strokeWidth = 0.8f * d
        ink.color = fade(Color.WHITE, 0.15f * opened)
        for (i in planets.indices) canvas.drawCircle(0f, cy, (first + gap * i) * opened, ink)

        // The sun: its corona, its body, and a flare at each press.
        canvas.save()
        canvas.translate(0f, cy)
        canvas.scale(sun, sun)
        fill.shader = unitCorona
        canvas.drawCircle(0f, 0f, 2f, fill)
        fill.shader = unitSun
        canvas.drawCircle(0f, 0f, 1f, fill)
        canvas.restore()
        fill.shader = null
        if (flare > 0.02f) {
            ink.strokeWidth = 2.2f * d
            ink.color = fade(0xFFFFD45E.toInt(), 0.6f * flare)
            canvas.drawCircle(0f, cy, sun * (1.12f + 0.7f * (1f - flare)), ink)
        }

        // The level, round the sun from the top of the phone.
        if (scene.showLevel && opened > 0.2f) {
            val r = sun * 1.3f
            oval.set(-r, cy - r, r, cy + r)
            ink.strokeWidth = 2.6f * d
            ink.color = fade(scene.accent, 0.25f * opened)
            canvas.drawArc(oval, -90f, 180f, false, ink)
            ink.color = fade(scene.accent, opened)
            canvas.drawArc(oval, -90f, 180f * scene.level.coerceIn(0f, 1f), false, ink)
        }

        // The planets, lit from the sun.
        planets.forEachIndexed { i, planet ->
            val orbit = (first + gap * i) * opened
            val size = planet.sizeDp * d * scale * opened
            if (size < 0.5f) return@forEachIndexed
            val px = cos(planet.angle) * orbit
            val py = cy + sin(planet.angle) * orbit
            if (px < -size * 3f) return@forEachIndexed
            // Turned so its lit side, at -x in its own circle, faces the sun.
            canvas.save()
            canvas.translate(px, py)
            canvas.rotate(Math.toDegrees(planet.angle.toDouble()).toFloat())
            canvas.scale(size, size)
            fill.shader = unitPlanets[i]
            canvas.drawCircle(0f, 0f, 1f, fill)
            canvas.restore()
            fill.shader = null
            if (planet.ring) {
                canvas.save()
                canvas.rotate(-22f, px, py)
                oval.set(px - size * 2.1f, py - size * 0.62f, px + size * 2.1f, py + size * 0.62f)
                ink.strokeWidth = 1.1f * d * scale
                ink.color = fade(0xFFE3CB8F.toInt(), 0.85f * opened)
                canvas.drawOval(oval, ink)
                canvas.restore()
            }
            if (planet.moon) {
                val m = planet.angle * 5f
                fill.color = fade(0xFFD8D8D8.toInt(), opened)
                canvas.drawCircle(px + cos(m) * size * 2.4f, py + sin(m) * size * 2.4f, size * 0.36f, fill)
            }
        }
    }

    override fun clear() {
        boost = 0f
        flare = 0f
    }
}

// ---- bubbles ------------------------------------------------------------------------------------------------

/**
 * Soap bubbles: every press lets a stream of them out of the bump, as a wand does — many small, a few
 * large — drifting on the air, rising slowly, wobbling as they go with the colours of the film swirling
 * on them and the light catching them, and popping, one by one.
 */
internal class BubblesEffect(random: Random) : BumpEffect(random) {
    private val bubbles = ParticlePool(80)
    private val pops = ParticlePool(80)
    private var stream = 0f
    private var streamClock = 0f
    private var time = 0f
    private val film = SweepGradient(
        0f, 0f,
        intArrayOf(0x70FF8AD8, 0x708C9EFF, 0x7080DEEA, 0x70B9F6CA, 0x70FFF59D, 0x70FF8AD8),
        null,
    )
    private val glassy = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(0x00FFFFFF, 0x0AFFFFFF, 0x38FFFFFF, 0x00FFFFFF),
        floatArrayOf(0f, 0.72f, 0.94f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; shader = glassy }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; shader = film }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val oval = RectF()

    override val stillAfterS: Float get() = 0.75f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        stream = min(stream + if (atLimit) 0.2f else 0.55f, 1.2f)
    }

    private fun spawn(scene: BumpScene) {
        val p = bubbles.spawn() ?: return
        val d = scene.density
        p.x = scene.restDepth * 0.95f
        p.y = scene.centre + scene.restHalf * rnd(-0.45f, 0.45f)
        val angle = rnd(-0.8f, 0.8f) + scene.towards * 0.15f
        val speed = rnd(60f, 160f) * d
        p.vx = cos(angle) * speed
        p.vy = sin(angle) * speed
        // Many small, a few large.
        val u = random.nextFloat()
        p.size = (3f + 12f * u * u) * d
        p.life = rnd(2.4f, 4.6f)
        p.phase = rnd(0f, 6.28f)
        p.spin = rnd(-50f, 50f)
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        time += dt
        val d = scene.density
        if (stream > 0f) {
            stream -= dt
            streamClock -= dt
            while (streamClock <= 0f) {
                streamClock += rnd(0.035f, 0.075f)
                spawn(scene)
            }
        }
        val drag = exp(-0.9f * dt)
        val k = 1f / (40f * d)
        var alive = stream > 0f
        for (p in bubbles.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.life || scene.offScreen(p.x, p.y, p.size)) {
                if (p.age >= p.life) pop(p, d)
                p.alive = false
                continue
            }
            // The air: a slow, swirling drift, and the bubble rising a little in it.
            val ax = (sin(p.y * k * 1.3f + time * 0.9f + p.phase) + 0.5f * sin(p.x * k * 2.1f - time * 1.3f)) * 26f * d
            val ay = (cos(p.x * k * 1.1f + time * 0.7f + p.phase) + 0.5f * cos(p.y * k * 1.7f + time)) * 26f * d
            p.vx = p.vx * drag + (ax - scene.gravityX * RISE * d) * dt
            p.vy = p.vy * drag + (ay - scene.gravityY * RISE * d) * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.angle += p.spin * dt
            alive = true
        }
        for (p in pops.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.life) {
                p.alive = false
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            alive = true
        }
        return alive
    }

    private fun pop(bubble: Particle, d: Float) {
        pops.spawn()?.let { ring ->
            ring.x = bubble.x
            ring.y = bubble.y
            ring.vx = 0f
            ring.vy = 0f
            ring.size = bubble.size
            ring.life = 0.2f
            ring.kind = 0
        }
        repeat(5) { i ->
            val drop = pops.spawn() ?: return
            val a = i * 1.257f + rnd(-0.3f, 0.3f)
            drop.x = bubble.x + cos(a) * bubble.size
            drop.y = bubble.y + sin(a) * bubble.size
            drop.vx = cos(a) * rnd(40f, 90f) * d
            drop.vy = sin(a) * rnd(40f, 90f) * d
            drop.size = rnd(0.7f, 1.2f) * d
            drop.life = rnd(0.18f, 0.3f)
            drop.kind = 1
        }
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        for (p in bubbles.items) {
            if (!p.alive) continue
            val a = min(1f, p.age / 0.12f)
            val r = p.size
            val wobble = 0.045f * sin(p.age * 7f + p.phase)
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.scale(r * (1f + wobble), r * (1f - wobble))
            // Clear in the middle, glassy towards the edge, the film's colours swirling on the rim.
            body.alpha = (255 * a).roundToInt()
            canvas.drawCircle(0f, 0f, 1f, body)
            canvas.save()
            canvas.rotate(p.angle + p.age * 35f)
            rim.strokeWidth = 1.4f * d / r
            rim.alpha = (255 * a).roundToInt()
            canvas.drawCircle(0f, 0f, 0.96f, rim)
            canvas.restore()
            edge.strokeWidth = 0.6f * d / r
            edge.color = fade(Color.WHITE, 0.4f * a)
            canvas.drawCircle(0f, 0f, 1f, edge)
            canvas.restore()
            // The light: a window's reflection top left, a glint opposite.
            oval.set(p.x - r * 0.7f, p.y - r * 0.7f, p.x + r * 0.7f, p.y + r * 0.7f)
            shine.strokeWidth = max(1.1f * d, r * 0.13f)
            shine.color = fade(Color.WHITE, 0.85f * a)
            canvas.drawArc(oval, 205f, 50f, false, shine)
            shine.strokeWidth = max(0.8f * d, r * 0.06f)
            shine.color = fade(Color.WHITE, 0.3f * a)
            canvas.drawArc(oval, 20f, 40f, false, shine)
            dot.color = fade(Color.WHITE, 0.85f * a)
            canvas.drawCircle(p.x + r * 0.42f, p.y + r * 0.48f, r * 0.07f, dot)
        }
        for (p in pops.items) {
            if (!p.alive) continue
            val t = p.age / p.life
            if (p.kind == 0) {
                edge.strokeWidth = 1f * d
                edge.color = fade(Color.WHITE, 0.7f * (1f - t))
                canvas.drawCircle(p.x, p.y, p.size * (1f + 0.6f * t), edge)
            } else {
                dot.color = fade(Color.WHITE, 0.8f * (1f - t))
                canvas.drawCircle(p.x, p.y, p.size, dot)
            }
        }
    }

    override fun clear() {
        bubbles.clear()
        pops.clear()
        stream = 0f
    }

    private companion object {
        /** How hard a bubble rises, in dp a second each second, before the air slows it. */
        const val RISE = 18f
    }
}

