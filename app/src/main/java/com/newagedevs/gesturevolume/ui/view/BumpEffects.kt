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
 * The button animation's showpiece styles: petals, sparks, fireworks, a sonar, a solar system,
 * bubbles, lightning and confetti, each thrown out from the button that was pressed.
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
        ButtonBump.STYLE_SPARKS -> SparksEffect(random)
        ButtonBump.STYLE_FIREWORKS -> FireworksEffect(random)
        ButtonBump.STYLE_SONAR -> SonarEffect(random)
        ButtonBump.STYLE_SOLAR -> SolarEffect(random)
        ButtonBump.STYLE_BUBBLES -> BubblesEffect(random)
        ButtonBump.STYLE_LIGHTNING -> LightningEffect(random)
        ButtonBump.STYLE_CONFETTI -> ConfettiEffect(random)
        ButtonBump.STYLE_ATOM -> AtomEffect(random)
        ButtonBump.STYLE_VOLCANO -> VolcanoEffect(random)
        ButtonBump.STYLE_WIND -> WindEffect(random)
        ButtonBump.STYLE_NINJA -> NinjaEffect(random)
        ButtonBump.STYLE_DRAGON -> DragonEffect(random)
        ButtonBump.STYLE_BEES -> BeesEffect(random)
        ButtonBump.STYLE_ROCKET -> RocketEffect(random)
        ButtonBump.STYLE_PAPER -> PaperEffect(random)
        ButtonBump.STYLE_BOXING -> BoxingEffect(random)
        ButtonBump.STYLE_HAMMER -> HammerEffect(random)
        ButtonBump.STYLE_CRACK -> CrackEffect(random)
        ButtonBump.STYLE_SMOKE -> SmokeEffect(random)
        ButtonBump.STYLE_CAT -> CatEffect(random)
        ButtonBump.STYLE_FISH -> FishEffect(random)
        ButtonBump.STYLE_SNAKE -> SnakeEffect(random)
        ButtonBump.STYLE_MONKEY -> MonkeyEffect(random)
        ButtonBump.STYLE_ALIEN -> AlienEffect(random)
        ButtonBump.STYLE_UFO -> UfoEffect(random)
        ButtonBump.STYLE_KISS -> KissEffect(random)
        ButtonBump.STYLE_MERMAID -> MermaidEffect(random)
        else -> null
    }
}

// ---- cherry blossom ---------------------------------------------------------------------------------

/**
 * A cherry blossom opens on the bump, and every press shakes petals loose: they flutter out into the
 * screen, turning over as they go, and drift down on the air.
 */
internal class BlossomEffect(random: Random) : BumpEffect(random) {
    private val petals = ParticlePool(120)
    private val petal = Path()
    private val petalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shaders: Array<Shader>
    private var bloom = 0f
    private var bloomVelocity = 0f
    private var turn = 0f

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
        bloomVelocity += if (atLimit) 4f else 8f
        repeat(if (atLimit) 5 else 11) { spawn(scene) }
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
        p.life = rnd(2.1f, 3.3f)
        p.size = rnd(6.5f, 10.5f) * d
        p.angle = rnd(0f, 360f)
        p.spin = rnd(-200f, 200f)
        p.phase = rnd(0f, 6.28f)
        p.rate = rnd(1.4f, 3.2f)
        p.mark = rnd(0f, 6.28f)
        p.kind = random.nextInt(PETALS.size)
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        // The flower on a soft spring: open with the bump, and a little wider at each press.
        val k = 170f
        val c = 2f * 0.42f * sqrt(k)
        bloomVelocity += (-k * (bloom - scene.presence.coerceIn(0f, 1.2f)) - c * bloomVelocity) * dt
        bloom += bloomVelocity * dt
        turn += 22f * dt

        val d = scene.density
        val drag = exp(-1.8f * dt)
        val acrossX = -scene.gravityY
        val acrossY = scene.gravityX
        var alive = false
        for (p in petals.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.life) {
                p.alive = false
                continue
            }
            // Falling slowly, and swaying side to side as it does.
            val sway = sin(p.age * 2.3f + p.mark) * 95f * d
            p.vx = p.vx * drag + (scene.gravityX * FALL + acrossX * sway / d) * d * dt
            p.vy = p.vy * drag + (scene.gravityY * FALL + acrossY * sway / d) * d * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.angle += p.spin * dt
            p.phase += p.rate * 6.283f * dt
            alive = true
        }
        return alive || abs(bloomVelocity) > 0.02f || abs(bloom - scene.presence) > 0.01f
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        // The flower, on the rim of the bump.
        val r = FLOWER_DP * d * bloom.coerceIn(0f, 1.3f)
        if (r > 0.6f) {
            val fx = scene.depth + r * 0.45f
            val fy = scene.centre
            petalPaint.shader = shaders[0]
            petalPaint.alpha = 255
            for (i in 0 until 5) {
                canvas.save()
                canvas.translate(fx, fy)
                canvas.rotate(turn + i * 72f)
                canvas.scale(r, r * 0.92f)
                canvas.drawPath(petal, petalPaint)
                canvas.restore()
            }
            dotPaint.color = 0xFFFFE9A8.toInt()
            canvas.drawCircle(fx, fy, r * 0.24f, dotPaint)
            dotPaint.color = 0xFFE0607E.toInt()
            for (i in 0 until 6) {
                val a = Math.toRadians((turn * 1.5f + i * 60f).toDouble())
                canvas.drawCircle(fx + cos(a).toFloat() * r * 0.36f, fy + sin(a).toFloat() * r * 0.36f, r * 0.06f, dotPaint)
            }
        }
        // The petals in the air.
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
        bloom = 0f
        bloomVelocity = 0f
    }

    private companion object {
        const val FLOWER_DP = 8f
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

// ---- sparks -----------------------------------------------------------------------------------------

/**
 * A grinder's sparks: the rim of the bump glows hot at each press and a shower of sparks streaks
 * out of it, arcing down as they cool from white through gold to red, some splitting as they go.
 */
internal class SparksEffect(random: Random) : BumpEffect(random) {
    private val sparks = ParticlePool(260)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var heat = 0f

    override val stillAfterS: Float get() = 0.17f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        heat = 1f
        repeat(if (atLimit) 14 else 32) { spawn(scene) }
    }

    private fun spawn(scene: BumpScene) {
        val p = sparks.spawn() ?: return
        val d = scene.density
        p.x = scene.restDepth * 0.92f
        p.y = scene.centre + scene.restHalf * rnd(-0.35f, 0.35f)
        val angle = rnd(-0.95f, 0.95f) + scene.towards * 0.35f
        val speed = rnd(260f, 760f) * d
        p.vx = cos(angle) * speed
        p.vy = sin(angle) * speed
        p.life = rnd(0.35f, 0.95f)
        p.size = rnd(1.1f, 2.1f) * d
        p.kind = 0
        // Some split in two part of the way, which is what makes the shower crackle.
        p.mark = if (random.nextFloat() < 0.35f) rnd(0.25f, 0.6f) * p.life else -1f
    }

    private fun split(parent: Particle) {
        for (side in intArrayOf(-1, 1)) {
            val p = sparks.spawn() ?: return
            val a = side * rnd(0.3f, 0.6f)
            val ca = cos(a)
            val sa = sin(a)
            p.x = parent.x
            p.y = parent.y
            p.vx = (parent.vx * ca - parent.vy * sa) * 0.55f
            p.vy = (parent.vx * sa + parent.vy * ca) * 0.55f
            p.life = (parent.life - parent.age) * 0.8f
            p.size = parent.size * 0.7f
            p.kind = 1
            p.mark = -1f
        }
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        heat *= exp(-2.6f * dt)
        val g = 1100f * scene.density
        val drag = exp(-0.9f * dt)
        var alive = false
        for (p in sparks.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.life) {
                p.alive = false
                continue
            }
            p.vx = p.vx * drag + scene.gravityX * g * dt
            p.vy = p.vy * drag + scene.gravityY * g * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.mark > 0f && p.age >= p.mark) {
                p.mark = -1f
                split(p)
            }
            alive = true
        }
        return alive || heat > 0.02f
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        // The rim, glowing as it is struck.
        if (heat > 0.02f && scene.depth > 1f) {
            glow.strokeWidth = 6f * d
            glow.color = fade(0xFFFF7A1A.toInt(), 0.4f * heat)
            canvas.drawPath(scene.rim, glow)
            core.strokeWidth = 1.6f * d
            core.color = fade(0xFFFFE7A6.toInt(), heat)
            canvas.drawPath(scene.rim, core)
        }
        for (p in sparks.items) {
            if (!p.alive) continue
            val t = p.age / p.life
            val colour = heatColour(t)
            val a = 1f - t * t
            // A streak as long as the spark travels in a fiftieth of a second: what the eye sees.
            val x0 = p.x - p.vx * STREAK_S
            val y0 = p.y - p.vy * STREAK_S
            glow.strokeWidth = p.size * 3.4f
            glow.color = fade(colour, 0.3f * a)
            canvas.drawLine(x0, y0, p.x, p.y, glow)
            core.strokeWidth = p.size
            core.color = fade(blend(colour, Color.WHITE, 0.5f * (1f - t)), a)
            canvas.drawLine(x0, y0, p.x, p.y, core)
        }
    }

    /** White hot, then gold, orange and a dull red as a spark cools. */
    private fun heatColour(t: Float): Int = when {
        t < 0.15f -> blend(0xFFFFFFFF.toInt(), 0xFFFFE27A.toInt(), t / 0.15f)
        t < 0.5f -> blend(0xFFFFE27A.toInt(), 0xFFFF9330.toInt(), (t - 0.15f) / 0.35f)
        else -> blend(0xFFFF9330.toInt(), 0xFFD8341C.toInt(), (t - 0.5f) / 0.5f)
    }

    override fun clear() {
        sparks.clear()
        heat = 0f
    }

    private companion object {
        const val STREAK_S = 0.02f
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

// ---- sonar ------------------------------------------------------------------------------------------------

/**
 * A sonar scope opens out of the edge round the button: range rings, a beam sweeping back and forth,
 * a ping going out at every press, and echoes lighting up as the beam passes over them.
 */
internal class SonarEffect(random: Random) : BumpEffect(random) {
    override val loops: Boolean get() = true

    private var sweep = -1.2f
    private var sweepDirection = 1f
    private val pings = FloatArray(4) { -1f }
    private val blips = ParticlePool(16)
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val wedge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val wedgeMatrix = Matrix()
    private val unitDisc = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(0xD0062A1C.toInt(), 0xC4031C12.toInt(), 0xB0021208.toInt()),
        floatArrayOf(0f, 0.7f, 1f),
        Shader.TileMode.CLAMP,
    )
    private var wedgeShader: SweepGradient? = null
    private var wedgeCentre = Float.NaN

    override val stillAfterS: Float get() = 0.75f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        val slot = pings.indices.firstOrNull { pings[it] < 0f } ?: pings.indices.maxByOrNull { pings[it] } ?: 0
        pings[slot] = 0f
        repeat(if (atLimit) 1 else 3) {
            val b = blips.spawn() ?: return@repeat
            b.angle = rnd(-1.25f, 1.25f)
            b.mark = rnd(0.32f, 0.9f)
            b.kind = 0
            b.life = 2.4f
        }
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        val before = sweep
        sweep += sweepDirection * SWEEP_RATE * dt
        val limit = (PI / 2).toFloat()
        if (sweep > limit) {
            sweep = limit
            sweepDirection = -1f
        } else if (sweep < -limit) {
            sweep = -limit
            sweepDirection = 1f
        }
        var alive = false
        for (i in pings.indices) {
            if (pings[i] < 0f) continue
            pings[i] += dt
            if (pings[i] > PING_S) pings[i] = -1f else alive = true
        }
        for (b in blips.items) {
            if (!b.alive) continue
            b.age += dt
            if (b.kind == 0 && (before - b.angle) * (sweep - b.angle) <= 0f) {
                // The beam passes over it: it lights, and fades from there.
                b.kind = 1
                b.age = 0f
            }
            if (b.age > b.life + if (b.kind == 0) 2f else 0f) b.alive = false else alive = true
        }
        return alive
    }

    private fun radius(scene: BumpScene): Float =
        min(scene.reach * 0.88f, scene.span * 0.46f) * ButtonBump.smootherstep(scene.presence.coerceIn(0f, 1f))

    override fun drawBehind(canvas: Canvas, scene: BumpScene) {
        val r = radius(scene)
        if (r < 2f) return
        val d = scene.density
        val cy = scene.centre
        val p = scene.presence.coerceIn(0f, 1f)
        disc.shader = unitDisc
        canvas.save()
        canvas.translate(0f, cy)
        canvas.scale(r, r)
        canvas.drawCircle(0f, 0f, 1f, disc)
        canvas.restore()
        disc.shader = null
        ink.strokeWidth = 0.8f * d
        ink.color = fade(GREEN, 0.24f * p)
        for (k in 1..4) canvas.drawCircle(0f, cy, r * k / 4f, ink)
        ink.color = fade(GREEN, 0.14f * p)
        for (deg in intArrayOf(-60, -30, 0, 30, 60)) {
            val a = Math.toRadians(deg.toDouble())
            canvas.drawLine(0f, cy, (cos(a) * r).toFloat(), cy + (sin(a) * r).toFloat(), ink)
        }
        // The beam's afterglow, a wedge behind it, fading the further back it is.
        if (wedgeShader == null || wedgeCentre != cy) {
            wedgeShader = SweepGradient(
                0f, cy,
                intArrayOf(0, 0, fade(GREEN, 0.5f)),
                floatArrayOf(0f, 1f - TRAIL / 360f, 1f),
            )
            wedgeCentre = cy
        }
        wedgeMatrix.setScale(1f, sweepDirection, 0f, cy)
        wedgeMatrix.postRotate(Math.toDegrees(sweep.toDouble()).toFloat(), 0f, cy)
        wedgeShader!!.setLocalMatrix(wedgeMatrix)
        wedge.shader = wedgeShader
        wedge.alpha = (255 * p).roundToInt()
        canvas.drawCircle(0f, cy, r, wedge)
        val bx = cos(sweep) * r
        val by = cy + sin(sweep) * r
        ink.strokeWidth = 4f * d
        ink.color = fade(GREEN, 0.25f * p)
        canvas.drawLine(0f, cy, bx, by, ink)
        ink.strokeWidth = 1.4f * d
        ink.color = fade(GREEN, 0.95f * p)
        canvas.drawLine(0f, cy, bx, by, ink)
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val r = radius(scene)
        if (r < 2f) return
        val d = scene.density
        val cy = scene.centre
        for (age in pings) {
            if (age < 0f) continue
            val t = age / PING_S
            val ring = r * (1f - (1f - t).pow(3))
            ink.strokeWidth = 5f * d
            ink.color = fade(GREEN, 0.18f * (1f - t))
            canvas.drawCircle(0f, cy, ring, ink)
            ink.strokeWidth = 1.8f * d
            ink.color = fade(GREEN, 0.9f * (1f - t))
            canvas.drawCircle(0f, cy, ring, ink)
        }
        for (b in blips.items) {
            if (!b.alive || b.kind != 1) continue
            val a = (1f - b.age / b.life).coerceIn(0f, 1f)
            val x = cos(b.angle) * r * b.mark
            val y = cy + sin(b.angle) * r * b.mark
            dot.color = fade(GREEN, 0.3f * a)
            canvas.drawCircle(x, y, 5.5f * d, dot)
            dot.color = fade(blend(GREEN, Color.WHITE, 0.5f), a)
            canvas.drawCircle(x, y, 2.2f * d, dot)
        }
    }

    override fun clear() {
        pings.fill(-1f)
        blips.clear()
    }

    private companion object {
        val GREEN = 0xFF39FF7A.toInt()
        /** The beam's pace, in radians a second, and how far behind it its afterglow reaches, in degrees. */
        const val SWEEP_RATE = 2.4f
        const val TRAIL = 50f
        const val PING_S = 1.1f
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
        val full = max(scene.restDepth * 1.2f, scene.restHalf * 0.6f)
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
 * Soap bubbles blown out of the bump at every press, rising and wobbling with the colours of a soap
 * film swirling round their rims, and popping, in the end, into a ring and a spray of drops.
 */
internal class BubblesEffect(random: Random) : BumpEffect(random) {
    private val bubbles = ParticlePool(40)
    private val pops = ParticlePool(40)
    private val film = SweepGradient(
        0f, 0f,
        intArrayOf(
            0xFFFF6AD5.toInt(), 0xFF8C7BFF.toInt(), 0xFF4DE1FF.toInt(),
            0xFF7CFFB2.toInt(), 0xFFFFE36E.toInt(), 0xFFFF6AD5.toInt(),
        ),
        null,
    )
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val oval = RectF()

    override val stillAfterS: Float get() = 0.7f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        repeat(if (atLimit) 2 else 5) { spawn(scene) }
    }

    private fun spawn(scene: BumpScene) {
        val p = bubbles.spawn() ?: return
        val d = scene.density
        p.x = scene.restDepth * 0.9f
        p.y = scene.centre + scene.restHalf * rnd(-0.7f, 0.7f)
        val angle = rnd(-0.9f, 0.9f) + scene.towards * 0.2f
        val speed = rnd(60f, 150f) * d
        p.vx = cos(angle) * speed
        p.vy = sin(angle) * speed
        p.size = rnd(5f, 13f) * d
        p.life = rnd(2.2f, 3.6f)
        p.phase = rnd(0f, 6.28f)
        p.spin = rnd(-60f, 60f)
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        val d = scene.density
        val drag = exp(-1.3f * dt)
        val acrossX = -scene.gravityY
        val acrossY = scene.gravityX
        var alive = false
        for (p in bubbles.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.life) {
                pop(p, d)
                p.alive = false
                continue
            }
            // Rising, the opposite way to down, and drifting side to side.
            val wobble = sin(p.age * 3f + p.phase) * 40f * d
            p.vx = p.vx * drag + (-scene.gravityX * RISE * d + acrossX * wobble) * dt
            p.vy = p.vy * drag + (-scene.gravityY * RISE * d + acrossY * wobble) * dt
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
            ring.life = 0.22f
            ring.kind = 0
        }
        repeat(6) { i ->
            val drop = pops.spawn() ?: return
            val a = i * 1.047f + rnd(-0.3f, 0.3f)
            drop.x = bubble.x + cos(a) * bubble.size
            drop.y = bubble.y + sin(a) * bubble.size
            drop.vx = cos(a) * rnd(40f, 90f) * d
            drop.vy = sin(a) * rnd(40f, 90f) * d
            drop.size = rnd(0.8f, 1.4f) * d
            drop.life = rnd(0.2f, 0.32f)
            drop.kind = 1
        }
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        for (p in bubbles.items) {
            if (!p.alive) continue
            val a = min(1f, p.age / 0.15f)
            val r = p.size * (1f + 0.05f * sin(p.age * 9f + p.phase))
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.angle + p.age * 40f)
            fill.color = fade(Color.WHITE, 0.07f * a)
            canvas.drawCircle(0f, 0f, r, fill)
            rim.shader = film
            rim.strokeWidth = 1.3f * d
            rim.alpha = (200 * a).roundToInt()
            canvas.drawCircle(0f, 0f, r, rim)
            rim.shader = null
            canvas.restore()
            // The window's reflection, top left, the same on every bubble.
            oval.set(p.x - r * 0.72f, p.y - r * 0.72f, p.x + r * 0.72f, p.y + r * 0.72f)
            shine.strokeWidth = max(1.2f * d, r * 0.12f)
            shine.color = fade(Color.WHITE, 0.85f * a)
            canvas.drawArc(oval, 200f, 55f, false, shine)
            fill.color = fade(Color.WHITE, 0.9f * a)
            canvas.drawCircle(p.x + r * 0.42f, p.y + r * 0.46f, r * 0.08f, fill)
        }
        for (p in pops.items) {
            if (!p.alive) continue
            val t = p.age / p.life
            if (p.kind == 0) {
                rim.shader = null
                rim.strokeWidth = 1f * d
                rim.color = fade(Color.WHITE, 0.7f * (1f - t))
                canvas.drawCircle(p.x, p.y, p.size * (1f + 0.6f * t), rim)
            } else {
                fill.color = fade(Color.WHITE, 0.8f * (1f - t))
                canvas.drawCircle(p.x, p.y, p.size, fill)
            }
        }
    }

    override fun clear() {
        bubbles.clear()
        pops.clear()
    }

    private companion object {
        /** How hard a bubble rises, in dp a second each second, before the air slows it. */
        const val RISE = 55f
    }
}

// ---- lightning ----------------------------------------------------------------------------------------------

/**
 * Electricity: every press throws a forked bolt from the bump out into the screen, flickering as it
 * crackles, and while the bump is out little arcs crawl along its rim.
 */
internal class LightningEffect(random: Random) : BumpEffect(random) {
    private class Bolt {
        val points = FloatArray(2 * (POINTS))
        var alive = false
        var sx = 0f
        var sy = 0f
        var tx = 0f
        var ty = 0f
        var age = 0f
        var life = 0f
        var nextJitter = 0f
        var brightness = 1f
        var width = 1f
    }

    private val bolts = Array(10) { Bolt() }
    private val path = Path()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var flash = 0f
    private var flashX = 0f
    private var flashY = 0f
    private var crawl = 0f

    override val stillAfterS: Float get() = 0.05f

    private fun freeBolt(): Bolt? = bolts.firstOrNull { !it.alive }

    override fun press(scene: BumpScene, atLimit: Boolean) {
        val main = freeBolt() ?: return
        main.alive = true
        main.age = 0f
        main.life = if (atLimit) 0.3f else 0.45f
        main.width = 1f
        main.sx = scene.restDepth * 0.92f
        main.sy = scene.centre + scene.restHalf * rnd(-0.25f, 0.25f)
        main.tx = scene.reach * rnd(0.55f, 0.9f)
        main.ty = (scene.centre + scene.span * rnd(-0.3f, 0.3f)).coerceIn(scene.span * 0.08f, scene.span * 0.92f)
        jitter(main)
        flash = 1f
        flashX = main.sx
        flashY = main.sy
        // Two forks, off points part of the way along it.
        repeat(if (atLimit) 1 else 2) {
            val fork = freeBolt() ?: return@repeat
            val k = (POINTS * rnd(0.3f, 0.65f)).toInt().coerceIn(1, POINTS - 2)
            fork.alive = true
            fork.age = 0f
            fork.life = main.life * 0.75f
            fork.width = 0.6f
            fork.sx = main.points[2 * k]
            fork.sy = main.points[2 * k + 1]
            val dx = main.tx - main.sx
            val dy = main.ty - main.sy
            val len = hypot(dx, dy) * rnd(0.25f, 0.4f)
            val a = kotlin.math.atan2(dy, dx) + (if (random.nextBoolean()) 1f else -1f) * rnd(0.45f, 0.95f)
            fork.tx = fork.sx + cos(a) * len
            fork.ty = fork.sy + sin(a) * len
            jitter(fork)
        }
    }

    /** A new jagged path between the bolt's ends: the flicker. Halved again and again, each middle pushed aside. */
    private fun jitter(b: Bolt) {
        val pts = b.points
        pts[0] = b.sx
        pts[1] = b.sy
        pts[2 * (POINTS - 1)] = b.tx
        pts[2 * (POINTS - 1) + 1] = b.ty
        var step = POINTS - 1
        while (step > 1) {
            val half = step / 2
            var i = 0
            while (i + step < POINTS) {
                val ax = pts[2 * i]
                val ay = pts[2 * i + 1]
                val bx = pts[2 * (i + step)]
                val by = pts[2 * (i + step) + 1]
                val len = hypot(bx - ax, by - ay)
                val off = rnd(-1f, 1f) * len * 0.28f
                // Perpendicular to the segment.
                val nx = if (len > 0f) -(by - ay) / len else 0f
                val ny = if (len > 0f) (bx - ax) / len else 0f
                pts[2 * (i + half)] = (ax + bx) / 2f + nx * off
                pts[2 * (i + half) + 1] = (ay + by) / 2f + ny * off
                i += step
            }
            step = half
        }
        b.brightness = rnd(0.55f, 1f)
        b.nextJitter = rnd(0.035f, 0.065f)
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        flash *= exp(-9f * dt)
        var alive = false
        for (b in bolts) {
            if (!b.alive) continue
            b.age += dt
            if (b.age >= b.life) {
                b.alive = false
                continue
            }
            b.nextJitter -= dt
            if (b.nextJitter <= 0f) jitter(b)
            alive = true
        }
        // Little arcs crawling along the rim while the bump is out.
        if (scene.presence > 0.5f) {
            crawl -= dt
            if (crawl <= 0f) {
                crawl = rnd(0.07f, 0.15f)
                freeBolt()?.let { arc ->
                    val u1 = rnd(-0.8f, 0.6f)
                    val u2 = u1 + rnd(0.15f, 0.4f)
                    arc.alive = true
                    arc.age = 0f
                    arc.life = rnd(0.08f, 0.14f)
                    arc.width = 0.45f
                    arc.sx = scene.depth * ButtonBump.profile(u1, 0.38f) + 1.5f * scene.density
                    arc.sy = scene.centre + scene.half * u1
                    arc.tx = scene.depth * ButtonBump.profile(u2, 0.38f) + 1.5f * scene.density
                    arc.ty = scene.centre + scene.half * u2
                    jitter(arc)
                }
            }
            alive = true
        }
        return alive || flash > 0.02f
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        if (flash > 0.02f) {
            val r = min(scene.reach, scene.span * 0.5f) * 0.6f
            flashPaint.shader = RadialGradient(
                flashX, flashY, r,
                intArrayOf(fade(0xFF9C8CFF.toInt(), 0.45f * flash), 0),
                null,
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(flashX, flashY, r, flashPaint)
            flashPaint.shader = null
        }
        for (b in bolts) {
            if (!b.alive) continue
            path.reset()
            path.moveTo(b.points[0], b.points[1])
            for (i in 1 until POINTS) path.lineTo(b.points[2 * i], b.points[2 * i + 1])
            val a = b.brightness * sqrt((1f - b.age / b.life).coerceIn(0f, 1f))
            stroke.strokeWidth = 8f * d * b.width
            stroke.color = fade(0xFF5A6BFF.toInt(), 0.22f * a)
            canvas.drawPath(path, stroke)
            stroke.strokeWidth = 3.2f * d * b.width
            stroke.color = fade(0xFFA9C6FF.toInt(), 0.6f * a)
            canvas.drawPath(path, stroke)
            stroke.strokeWidth = max(1.2f * d * b.width, 0.8f * d)
            stroke.color = fade(Color.WHITE, a)
            canvas.drawPath(path, stroke)
        }
    }

    override fun clear() {
        for (b in bolts) b.alive = false
        flash = 0f
    }

    private companion object {
        /** Points along a bolt: one more than a power of two, for the halving. */
        const val POINTS = 33
    }
}

// ---- confetti -------------------------------------------------------------------------------------------------

/** A party popper at every press: confetti, streamers and dots bursting out and fluttering down. */
internal class ConfettiEffect(random: Random) : BumpEffect(random) {
    private val bits = ParticlePool(170)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ribbon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()

    override val stillAfterS: Float get() = 0.14f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        repeat(if (atLimit) 12 else 30) { spawn(scene) }
    }

    private fun spawn(scene: BumpScene) {
        val p = bits.spawn() ?: return
        val d = scene.density
        p.x = scene.restDepth * 0.9f
        p.y = scene.centre + scene.restHalf * rnd(-0.5f, 0.5f)
        val angle = rnd(-1.15f, 1.15f) + scene.towards * 0.3f
        val speed = rnd(220f, 560f) * d
        p.vx = cos(angle) * speed
        p.vy = sin(angle) * speed
        p.kind = random.nextInt(3)
        p.size = rnd(3.2f, 5.6f) * d
        p.color = COLOURS[random.nextInt(COLOURS.size)]
        p.angle = rnd(0f, 360f)
        p.spin = rnd(-540f, 540f)
        p.rate = rnd(2f, 5f)
        p.phase = rnd(0f, 6.28f)
        p.mark = rnd(0f, 6.28f)
        p.life = rnd(1.8f, 2.8f)
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        val d = scene.density
        val drag = exp(-2.6f * dt)
        val acrossX = -scene.gravityY
        val acrossY = scene.gravityX
        var alive = false
        for (p in bits.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.life) {
                p.alive = false
                continue
            }
            val flutter = sin(p.age * 5f + p.mark) * 120f * d
            p.vx = p.vx * drag + (scene.gravityX * FALL * d + acrossX * flutter) * dt
            p.vy = p.vy * drag + (scene.gravityY * FALL * d + acrossY * flutter) * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.angle += p.spin * dt
            p.phase += p.rate * 6.283f * dt
            alive = true
        }
        return alive
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        for (p in bits.items) {
            if (!p.alive) continue
            val t = p.age / p.life
            val a = if (t < 0.75f) 1f else (1f - t) / 0.25f
            val turn = cos(p.phase)
            // The back of a piece is a shade darker: it shows which way it is turning.
            val colour = fade(if (turn < 0f) blend(p.color, Color.BLACK, 0.25f) else p.color, a)
            val s = p.size
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.angle)
            when (p.kind) {
                0 -> {
                    canvas.scale(1f, max(abs(turn), 0.12f))
                    fill.color = colour
                    canvas.drawRect(-s, -s * 0.55f, s, s * 0.55f, fill)
                }
                1 -> {
                    canvas.scale(max(abs(turn), 0.15f), 1f)
                    fill.color = colour
                    canvas.drawCircle(0f, 0f, s * 0.6f, fill)
                }
                else -> {
                    path.reset()
                    path.moveTo(-s * 1.2f, 0f)
                    path.quadTo(-s * 0.6f, -s * 0.9f * turn, 0f, 0f)
                    path.quadTo(s * 0.6f, s * 0.9f * turn, s * 1.2f, 0f)
                    ribbon.strokeWidth = s * 0.42f
                    ribbon.color = colour
                    canvas.drawPath(path, ribbon)
                }
            }
            canvas.restore()
        }
    }

    override fun clear() {
        bits.clear()
    }

    private companion object {
        const val FALL = 300f
        val COLOURS = intArrayOf(
            0xFFFF4D6D.toInt(), 0xFFFFC300.toInt(), 0xFF00D1B2.toInt(), 0xFF3A86FF.toInt(),
            0xFF8338EC.toInt(), 0xFFFB5607.toInt(), 0xFF06D6A0.toInt(), 0xFFFF70A6.toInt(),
        )
    }
}
