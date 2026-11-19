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
 * The big ones: a mushroom cloud, a volcano, smoke, a rocket, a gust of wind and an aerosol spray. For the cloud and the volcano the glass's edge is the ground, so they rise into the
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
 * A mushroom cloud at every press, as big as the level is loud.
 *
 * It rises the way one does — up the screen, whichever edge the buttons are on — from a point just
 * inside the glass beside them, placed so the whole cloud stays in sight. A flash lights the whole
 * screen for a moment and a shockwave runs out across all of it; a fireball climbs on a stem of fire
 * and smoke that flares at its foot, and rolls over into a wide cap: lumpy on top, darker beneath,
 * its two sides rolling as a real one's do. A ring of condensation hangs round the stem, dust surges
 * out along the ground, and the fire inside cools to smoke and drifts away.
 */
internal class AtomEffect(random: Random) : BumpEffect(random) {
    private class Blast {
        var alive = false
        var age = 0f
        /** The ground point, in upright terms, and the cloud's size at its full height. */
        var gx = 0f
        var gy = 0f
        var cap = 1f
        var height = 1f
        var power = 1f
        /** How far the shockwave runs: the farthest corner of the screen from the ground point. */
        var far = 1f

        /** Whether this one flashes: not when the last flash was only a moment ago. */
        var flash = true
        val capPuffs = FloatArray(CAP_PUFFS * 3)
        val domePuffs = FloatArray(DOME_PUFFS * 3)
        val stemPuffs = FloatArray(STEM_PUFFS * 3)
        val surgePuffs = FloatArray(SURGE_PUFFS * 3)
    }

    private val blasts = Array(2) { Blast() }
    private var next = 0
    private var lastFlashAge = 99f

    // Smoke lit from above, in a unit circle drawn the right way up: pale where the light falls, in
    // shadow beneath; three of them, for the cap's top, its body and its underside.
    private fun smoke(lit: Int, mid: Int, dark: Int) = solidFill().apply {
        shader = litPuff(RadialGradient(0.3f, -0.42f, 1.55f, intArrayOf(lit, mid, dark), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP))
    }
    private val smokeTop = smoke(0xFFF7EEE4.toInt(), 0xFFD9C8B8.toInt(), 0xFF8E7B6E.toInt())
    private val smokeBody = smoke(0xFFE7D7C8.toInt(), 0xFFB6A291.toInt(), 0xFF6F5F55.toInt())
    private val smokeUnder = smoke(0xFFB9A595.toInt(), 0xFF83705F.toInt(), 0xFF4E423B.toInt())
    private val dust = smoke(0xFFE6D2B8.toInt(), 0xFFC1A88E.toInt(), 0xFF8C7360.toInt())
    private val fire = solidFill().apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xFFFFF8D6.toInt(), 0xF2FFC04D.toInt(), 0xA6FF6A1A.toInt(), 0), floatArrayOf(0f, 0.28f, 0.62f, 1f), Shader.TileMode.CLAMP)
    }
    private val ember = solidFill().apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xCCFF8A2A.toInt(), 0x80D8461A.toInt(), 0), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }
    private val glow = solidFill().apply { shader = softPuff(0xFFFFB45C.toInt()) }
    private val flash = solidFill()
    private val ring = roundStroke()

    override val stillAfterS: Float get() = 1.5f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        val b = blasts[next++ % blasts.size]
        b.alive = true
        b.age = 0f
        b.power = scene.power * if (atLimit) 0.75f else 1f
        b.flash = lastFlashAge > FLASH_GAP_S
        if (b.flash) lastFlashAge = 0f
        val across = scene.screenRight - maxOf(scene.screenLeft, 0f)
        val along = scene.screenBottom - scene.screenTop
        val short = minOf(across, along).coerceAtLeast(scene.unit * 6f)
        // Half again as big at full volume as near silence's half: see BumpScene.power.
        val scale = (b.power / BumpScene.POWER_MAX).coerceIn(0.3f, 1f)
        b.cap = short * 0.3f * scale.pow(0.85f)
        b.height = b.cap * 1.75f
        // The ground point: beside the button, moved in from the glass and along it as far as the
        // cloud needs to be all on the screen, rising up the screen.
        val ux = scene.upX
        val uy = scene.upY
        val ax = abs(uy)
        val ay = abs(ux)
        val tall = b.height + b.cap * 0.55f
        val margin = scene.unit * 0.6f
        b.gx = margin + maxOf(0f, -ux * tall) + ax * b.cap * 1.05f
        val minY = minOf(0f, uy * tall) - ay * b.cap * 1.05f
        val maxY = maxOf(0f, uy * tall) + ay * b.cap * 1.05f
        b.gy = scene.centre.coerceIn(scene.screenTop - minY + margin, scene.screenBottom - maxY - margin)
        b.far = scene.farthest(b.gy) + hypot(b.gx, 0f)
        // Where each puff sits in the cloud, for this one blast: fixed, so the cloud keeps its shape.
        for (i in 0 until CAP_PUFFS) {
            b.capPuffs[i * 3] = rnd(0f, 6.283f)
            b.capPuffs[i * 3 + 1] = rnd(0.55f, 1f)
            b.capPuffs[i * 3 + 2] = rnd(0.24f, 0.36f)
        }
        for (i in 0 until DOME_PUFFS) {
            b.domePuffs[i * 3] = -1f + 2f * (i + rnd(0.1f, 0.9f)) / DOME_PUFFS
            b.domePuffs[i * 3 + 1] = rnd(0.72f, 1.05f)
            b.domePuffs[i * 3 + 2] = rnd(0.2f, 0.3f)
        }
        for (i in 0 until STEM_PUFFS) {
            b.stemPuffs[i * 3] = (i + rnd(0f, 1f)) / STEM_PUFFS
            b.stemPuffs[i * 3 + 1] = rnd(-0.5f, 0.5f)
            b.stemPuffs[i * 3 + 2] = rnd(0.9f, 1.15f)
        }
        for (i in 0 until SURGE_PUFFS) {
            b.surgePuffs[i * 3] = -1f + 2f * (i + rnd(0.15f, 0.85f)) / SURGE_PUFFS
            b.surgePuffs[i * 3 + 1] = rnd(0f, 1f)
            b.surgePuffs[i * 3 + 2] = rnd(0.75f, 1.2f)
        }
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        var alive = false
        lastFlashAge += dt
        for (b in blasts) {
            if (!b.alive) continue
            b.age += dt
            if (b.age > LIFE) b.alive = false else alive = true
        }
        return alive
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        // The oldest first, so a new cloud stands in front of the last.
        val first = blasts.filter { it.alive }.sortedByDescending { it.age }
        for (b in first) drawBlast(canvas, scene, b)
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
        val strength = b.power.coerceAtMost(1.15f) / 1.15f

        // The flash: the whole screen lit for a moment — not more often than every half second, and
        // never so bright it blinds.
        if (b.flash && t < FLASH_S) {
            val k = t / FLASH_S
            flash.color = fade(0xFFFFF4E2.toInt(), 0.42f * strength * (1f - k) * (1f - k))
            canvas.drawRect(scene.screenLeft, scene.screenTop, scene.screenRight, scene.screenBottom, flash)
        }
        // The shockwave: a ring running out across the whole screen, a soft wall of air behind its edge.
        if (t < SHOCK_S) {
            val k = t / SHOCK_S
            val r = b.far * (1f - (1f - k).pow(1.7f))
            val fadeOut = (1f - k).pow(1.3f) * (0.45f + 0.55f * strength)
            ring.strokeWidth = (26f - 16f * k) * d
            ring.color = fade(0xFFFFF3E0.toInt(), 0.13f * fadeOut)
            canvas.drawCircle(b.gx, b.gy, (r - 10f * d).coerceAtLeast(0f), ring)
            ring.strokeWidth = (3.2f - 1.6f * k) * d
            ring.color = fade(0xFFFFFFFF.toInt(), 0.75f * fadeOut)
            canvas.drawCircle(b.gx, b.gy, r, ring)
        }

        val alpha = if (t < FADE_FROM) 1f else (1f - (t - FADE_FROM) / (LIFE - FADE_FROM)).coerceIn(0f, 1f)
        if (alpha <= 0.01f) return
        val rise = easeOut(t / 1.7f)
        val grow = easeOut((t - 0.12f) / 1.9f)
        val heat = (1f - t / 2.3f).coerceIn(0f, 1f)
        val cap = b.cap * (0.32f + 0.68f * grow) * (1f + 0.06f * (t / LIFE))
        val capH = cap * 0.46f
        val h = b.height * rise + cap * 0.12f
        val drift = t * 6f * d

        canvas.save()
        canvas.translate(b.gx, b.gy)
        canvas.rotate(uprightDegrees(scene))
        // One layer, so the cloud fades out as a whole rather than puff by puff.
        val layered = alpha < 0.99f
        if (layered) {
            canvas.saveLayerAlpha(-cap * 2.2f, -h - capH * 2.5f, cap * 2.2f, cap * 0.6f, (alpha * 255f).roundToInt())
        }

        // The ground glowing where it went off, early on.
        if (t < 1.4f) {
            puff(canvas, glow, 0f, 0f, b.cap * (0.5f + 0.9f * easeOut(t / 0.5f)), 0.55f * (1f - t / 1.4f))
        }

        // Dust surging out along the ground, both ways: a low bank of it, thickest at the stem's foot
        // and thinning to wisps at its ends.
        val surge = b.cap * 1.2f * easeOut(t / 1.7f)
        val surgeIn = easeOut(t * 2.4f)
        for (i in 0 until SURGE_PUFFS) {
            val u = b.surgePuffs[i * 3]
            val jitter = b.surgePuffs[i * 3 + 1]
            val size = b.surgePuffs[i * 3 + 2]
            val thick = (1f - abs(u)).pow(1.4f)
            val r = b.cap * (0.1f + 0.2f * thick) * size * surgeIn
            val y = -r * (0.25f + 0.55f * jitter) - b.cap * 0.05f * thick * jitter
            puff(canvas, dust, u * surge, y, r, 0.95f - 0.4f * abs(u))
        }

        // The stem: flared at its foot, its smoke climbing up it, fire inside it while it is hot.
        val stemTop = h - capH * 0.35f
        for (i in 0 until STEM_PUFFS) {
            val k = ((b.stemPuffs[i * 3] + t * 0.14f) % 1f)
            val off = b.stemPuffs[i * 3 + 1]
            val size = b.stemPuffs[i * 3 + 2]
            // A column as wide as a fifth of the cap, swelling at its foot where it draws the dust in.
            val half = cap * 0.17f * (1f + 1.25f * (1f - k).pow(3f))
            val y = -stemTop * k
            val r = half * 1.2f * size
            puff(canvas, smokeBody, off * half * 0.6f, y, r, 1f)
            puff(canvas, ember, off * half * 0.4f, y, r * 0.75f, heat * (1f - 0.5f * k))
        }

        // The ring of condensation round the stem, below the cap, for a while.
        if (t > 0.45f && t < 2.3f) {
            val k = (t - 0.45f) / 1.85f
            val ringA = sin(PI.toFloat() * k) * 0.55f
            val ry = -stemTop * 0.62f
            ring.strokeWidth = cap * 0.07f
            ring.color = fade(0xFFFFFFFF.toInt(), ringA * 0.5f)
            canvas.drawOval(-cap * 0.62f, ry - cap * 0.08f, cap * 0.62f, ry + cap * 0.08f, ring)
            ring.strokeWidth = cap * 0.025f
            ring.color = fade(0xFFFFFFFF.toInt(), ringA)
            canvas.drawOval(-cap * 0.58f, ry - cap * 0.065f, cap * 0.58f, ry + cap * 0.065f, ring)
        }

        // The cap's underside and body: its two sides rolling, up the inside, over and down the outside.
        val coreY = -h + capH * 0.2f
        val roll = t * 1.15f
        for (i in 0 until CAP_PUFFS) {
            val side = if (i % 2 == 0) -1f else 1f
            val angle = b.capPuffs[i * 3] + side * roll
            val reach = b.capPuffs[i * 3 + 1]
            val size = b.capPuffs[i * 3 + 2]
            val cx = side * cap * 0.5f
            val x = cx + cos(angle) * cap * 0.42f * reach
            val y = coreY + sin(angle) * capH * 0.62f * reach - drift
            val under = sin(angle) > 0.2f
            puff(canvas, if (under) smokeUnder else smokeBody, x, y, cap * size, 1f)
        }
        // The top of the cap: lumpy, catching the light.
        for (i in 0 until DOME_PUFFS) {
            val a = b.domePuffs[i * 3]
            val up = b.domePuffs[i * 3 + 1]
            val size = b.domePuffs[i * 3 + 2]
            val x = a * cap * 0.92f
            val y = coreY - capH * sqrt((1f - a * a).coerceAtLeast(0f)) * up - drift
            puff(canvas, smokeTop, x, y, cap * size * (0.85f + 0.3f * (1f - abs(a))), 1f)
        }
        // The fire inside, glowing through it, cooling as it climbs.
        if (heat > 0f) {
            puff(canvas, fire, 0f, coreY - drift, cap * 0.85f, heat * heat)
            puff(canvas, ember, -cap * 0.5f, coreY + capH * 0.2f - drift, cap * 0.5f, heat)
            puff(canvas, ember, cap * 0.5f, coreY + capH * 0.2f - drift, cap * 0.5f, heat)
        }
        // The fireball, climbing out of the ground into the cap in the first moments.
        if (t < 0.8f) {
            val k = t / 0.8f
            val y = -h * easeOut(t / 0.35f).coerceAtMost(1f)
            puff(canvas, fire, 0f, y, b.cap * (0.25f + 0.45f * easeOut(t / 0.3f)), (1f - k).pow(1.5f))
        }

        if (layered) canvas.restore()
        canvas.restore()
    }

    override fun clear() {
        for (b in blasts) b.alive = false
    }

    private companion object {
        const val LIFE = 4.2f
        const val FADE_FROM = 2.6f
        const val FLASH_S = 0.35f
        const val FLASH_GAP_S = 0.5f
        const val SHOCK_S = 1.25f
        const val CAP_PUFFS = 34
        const val DOME_PUFFS = 16
        const val STEM_PUFFS = 34
        const val SURGE_PUFFS = 30
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
        exhale = (if (atLimit) 0.18f else 0.5f) * scene.power
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

        /** How hard it was launched: see [BumpScene.power]. */
        var power = 1f
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
        s.power = scene.power
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
                s.speed += 1000f * d * dt * (0.55f + 0.45f * s.power)
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
        val PALETTE = intArrayOf(
            0xFF29B6F6.toInt(), 0xFFEC407A.toInt(), 0xFF66BB6A.toInt(), 0xFFFFCA28.toInt(),
            0xFFAB47BC.toInt(), 0xFFECEFF1.toInt(),
        )
    }
}
