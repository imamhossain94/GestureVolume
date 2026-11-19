package com.newagedevs.gesturevolume.ui.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Three more little creatures for the button animation: a frog that leaps out of the water at the
 * edge, a woodpecker drumming on a trunk along it, and a spider that swings across the screen on a
 * line of silk. All drawn from code, all of them the app's own.
 */

/** A soft round gradient in a unit circle, lit from up and to the left, for a creature's body. */
private fun bodyShade(light: Int, mid: Int, dark: Int): Shader = RadialGradient(
    -0.35f, -0.45f, 1.5f,
    intArrayOf(light, mid, dark),
    floatArrayOf(0f, 0.5f, 1f),
    Shader.TileMode.CLAMP,
)

// ---- frog jump -----------------------------------------------------------------------------------------

/**
 * A little frog in the water along the edge, only its eyes and the top of its head above the surface,
 * blinking. Every press it leaps out — legs stretched, then tucked — catches a fly with a flick of its
 * tongue at the top of the leap, and splashes back in, puffing its cheeks out twice as it croaks. The
 * louder the level, the higher it leaps; a press while it is in the air lines up the next leap.
 */
internal class FrogEffect(random: Random) : BumpEffect(random) {
    override val drawsBump: Boolean get() = false
    override val loops: Boolean get() = true

    private val water = WaterStrip(random)
    private var time = 0f

    /** Where it sits along the edge between leaps, in upright terms; NaN before the first press. */
    private var restY = Float.NaN
    private var leaping = false
    private var x = 0f
    private var y = 0f
    private var vx = 0f
    private var vy = 0f
    private var age = 0f
    private var pending = 0
    private var pendingIn = 0f
    private var landedAt = -10f

    /** The fly it is after: where it hovers, whether it is there, and when the tongue went for it. */
    private var flyX = 0f
    private var flyY = 0f
    private var flyAlive = false
    private var tongueAt = -10f

    private val fill = solidFill()
    private val ink = roundStroke()
    private val skin = solidFill().apply { shader = bodyShade(0xFFA5E26A.toInt(), 0xFF5DBB46.toInt(), 0xFF2F8A35.toInt()) }
    private val leapFrame = Matrix()
    private val mouth = FloatArray(2)
    private val sac = solidFill().apply { shader = bodyShade(0xFFFFFDE7.toInt(), 0xFFF0F4C3.toInt(), 0xFFC5D987.toInt()) }

    override val stillAfterS: Float get() = 0.36f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        if (restY.isNaN()) restY = scene.centre
        if (leaping) {
            pending = min(pending + 1, 2)
            return
        }
        leap(scene, atLimit)
    }

    private fun leap(scene: BumpScene, atLimit: Boolean) {
        val d = scene.density
        val power = scene.power * if (atLimit) 0.75f else 1f
        leaping = true
        age = 0f
        x = water.depth(scene) * 0.3f
        y = restY
        vx = rnd(330f, 390f) * d * (0.58f + 0.42f * power)
        // Along the edge towards the button pressed, and back the other way when the pond runs out.
        val limit = water.half(scene) * 0.7f
        val dir = if (scene.towards < 0) -1f else 1f
        var target = restY + dir * rnd(18f, 42f) * d
        if (abs(target - scene.centre) > limit) target = restY - dir * rnd(18f, 42f) * d
        val flight = 2f * vx / (GRAVITY * d)
        vy = (target - y) / flight
        // A fly where the top of the leap will be, just ahead of the frog's mouth.
        val top = x + vx * vx / (2f * GRAVITY * d)
        flyX = top + scene.unit * 1.25f
        flyY = y + vy * flight / 2f
        flyAlive = true
        tongueAt = -10f
        water.splash(scene, y, 0.7f * power)
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        time += dt
        val d = scene.density
        var alive = water.step(scene, dt)
        if (leaping) {
            age += dt
            vx -= GRAVITY * d * dt
            x += vx * dt
            y += vy * dt
            // At the top of the leap the tongue goes for the fly.
            if (flyAlive && tongueAt < 0f && vx <= 60f * d) tongueAt = time
            if (vx < 0f && x < water.depth(scene) * 0.4f) {
                leaping = false
                restY = y
                landedAt = time
                water.splash(scene, y, 0.9f * scene.power)
                if (pending > 0) pendingIn = 0.22f
            }
            alive = true
        } else if (pending > 0) {
            pendingIn -= dt
            if (pendingIn <= 0f) {
                pending--
                leap(scene, false)
            }
            alive = true
        }
        if (tongueAt >= 0f && time - tongueAt > TONGUE_S) flyAlive = false
        return alive || scene.presence > 0.01f
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        water.draw(canvas, scene)
        if (restY.isNaN()) restY = scene.centre
        val s = scene.unit * 1.05f
        // A quick blink every few seconds.
        val blinking = time % BLINK_S > BLINK_S - 0.12f
        // The fly, buzzing about where it waits.
        if (flyAlive) {
            val caught = if (tongueAt >= 0f) ((time - tongueAt) / TONGUE_S).coerceIn(0f, 1f) else 0f
            val mouthX = x + s * 1.2f
            var fx = flyX + sin(time * 13f) * s * 0.15f
            var fy = flyY + sin(time * 9f + 1f) * s * 0.2f
            if (caught > 0.5f) {
                // Caught: it comes back with the tongue.
                val back = (caught - 0.5f) / 0.5f
                fx += (mouthX - fx) * back
                fy += (y - fy) * back
            }
            if (caught < 0.95f) drawFly(canvas, fx, fy, s * 0.32f)
        }
        val depth = water.depth(scene)
        if (leaping) {
            // Along its path, as the fish flies; its back turned up the screen, as a frog's is.
            val angle = atan2(vy, vx)
            val backUp = sin(angle) * scene.upX - cos(angle) * scene.upY
            val flip = if (backUp < 0f) -1f else 1f
            leapFrame.reset()
            leapFrame.postScale(1f, flip)
            leapFrame.postRotate(Math.toDegrees(angle.toDouble()).toFloat())
            leapFrame.postTranslate(x, y)
            canvas.save()
            canvas.concat(leapFrame)
            val extend = (vx / (330f * scene.density)).coerceIn(0f, 1f)
            drawLeaper(canvas, s, extend, blinking)
            canvas.restore()
            // The tongue, flicking out from its mouth to the fly and back.
            if (tongueAt >= 0f) {
                val k = ((time - tongueAt) / TONGUE_S).coerceIn(0f, 1f)
                val reach = sin(PI.toFloat() * k)
                if (reach > 0.01f) {
                    mouth[0] = s * 1.18f
                    mouth[1] = s * 0.12f
                    leapFrame.mapPoints(mouth)
                    val mx = mouth[0]
                    val my = mouth[1]
                    val tx = mx + (flyX - mx) * reach
                    val ty = my + (flyY - my) * reach
                    ink.strokeWidth = s * 0.16f
                    ink.color = 0xFFF06292.toInt()
                    canvas.drawLine(mx, my, tx, ty, ink)
                    fill.color = 0xFFE91E63.toInt()
                    canvas.drawCircle(tx, ty, s * 0.13f, fill)
                }
            }
        } else if (depth > 1f) {
            // Peeking: eyes and the top of the head above the water, bobbing a little.
            val croakT = time - landedAt
            val croak = max(bump(croakT - 0.12f, 0.3f), bump(croakT - 0.5f, 0.3f))
            canvas.save()
            canvas.translate(depth - s * 0.12f + sin(time * 2.3f) * s * 0.05f, restY)
            canvas.rotate(90f)
            // Only what is above the water shows.
            canvas.clipRect(-s * 3f, -s * 3f, s * 3f, s * 0.08f)
            drawHead(canvas, s, croak, blinking)
            canvas.restore()
        }
    }

    /** 0..1 and back over [length] seconds, from [t] = 0: a puff of the cheeks. */
    private fun bump(t: Float, length: Float): Float = if (t < 0f || t > length) 0f else sin(PI.toFloat() * t / length)

    /** The head above the water, facing out of the glass (its up is -y), with its cheeks puffed by [croak]. */
    private fun drawHead(canvas: Canvas, s: Float, croak: Float, blinking: Boolean) {
        // The vocal sacs, behind the head, puffing out at its sides.
        if (croak > 0.02f) {
            val r = s * 0.5f * croak
            for (side in SIDES) {
                canvas.save()
                canvas.translate(side * s * 1.05f, -s * 0.12f)
                canvas.scale(r, r)
                canvas.drawCircle(0f, 0f, 1f, sac)
                canvas.restore()
            }
        }
        canvas.save()
        canvas.scale(s * 1.2f, s * 1.2f)
        canvas.drawOval(-0.95f, -0.78f, 0.95f, 0.45f, skin)
        canvas.drawCircle(-0.46f, -0.72f, 0.34f, skin)
        canvas.drawCircle(0.46f, -0.72f, 0.34f, skin)
        canvas.restore()
        drawEyes(canvas, s, -0.88f, blinking)
        // Cheeks, nostrils and a smile.
        fill.color = 0x66FF8FA3
        canvas.drawOval(-s * 0.92f, -s * 0.42f, -s * 0.5f, -s * 0.2f, fill)
        canvas.drawOval(s * 0.5f, -s * 0.42f, s * 0.92f, -s * 0.2f, fill)
        fill.color = 0xFF2E6B2F.toInt()
        canvas.drawCircle(-s * 0.13f, -s * 0.52f, s * 0.04f, fill)
        canvas.drawCircle(s * 0.13f, -s * 0.52f, s * 0.04f, fill)
        ink.strokeWidth = s * 0.08f
        ink.color = 0xFF2E6B2F.toInt()
        canvas.drawArc(-s * 0.55f, -s * 0.66f, s * 0.55f, -s * 0.12f, 30f, 120f, false, ink)
    }

    /** The two big eyes on top of the head, [atY] in units of the frog. */
    private fun drawEyes(canvas: Canvas, s: Float, atY: Float, blinking: Boolean) {
        for (side in SIDES) {
            val ex = side * s * 0.55f
            val ey = s * atY
            if (blinking) {
                ink.strokeWidth = s * 0.08f
                ink.color = 0xFF2E6B2F.toInt()
                canvas.drawArc(ex - s * 0.26f, ey - s * 0.16f, ex + s * 0.26f, ey + s * 0.16f, 15f, 150f, false, ink)
                continue
            }
            fill.color = Color.WHITE
            canvas.drawCircle(ex, ey, s * 0.34f, fill)
            fill.color = 0xFF1B1B2F.toInt()
            canvas.drawCircle(ex - side * s * 0.04f, ey + s * 0.04f, s * 0.22f, fill)
            fill.color = Color.WHITE
            canvas.drawCircle(ex - side * s * 0.11f, ey - s * 0.07f, s * 0.085f, fill)
            canvas.drawCircle(ex + side * s * 0.04f, ey + s * 0.12f, s * 0.04f, fill)
        }
    }

    /**
     * The frog in the air, side on: facing +x, its back up (-y), its long back legs stretched out
     * behind it by [extend] as it leaves the water and folding under it as it comes down.
     */
    private fun drawLeaper(canvas: Canvas, s: Float, extend: Float, blinking: Boolean) {
        canvas.save()
        canvas.scale(s, s)
        // The far back leg, a shade darker, then the near one over the body later.
        drawBackLeg(canvas, extend, 0xFF3F9A45.toInt(), -0.12f)
        // Body, tapering to the rear, and its pale belly.
        canvas.drawOval(-0.95f, -0.62f, 0.8f, 0.55f, skin)
        fill.color = 0xFFF1F8E9.toInt()
        canvas.drawOval(-0.55f, 0.08f, 0.7f, 0.56f, fill)
        // Spots along its back.
        fill.color = 0x553E8E2F
        canvas.drawCircle(-0.35f, -0.32f, 0.11f, fill)
        canvas.drawCircle(0.05f, -0.42f, 0.09f, fill)
        canvas.drawCircle(-0.68f, -0.12f, 0.08f, fill)
        // Head, and the eye bulging on top of it.
        canvas.drawOval(0.3f, -0.55f, 1.28f, 0.4f, skin)
        canvas.drawCircle(0.78f, -0.55f, 0.33f, skin)
        canvas.restore()
        // A big round eye, looking ahead.
        if (blinking) {
            ink.strokeWidth = s * 0.08f
            ink.color = 0xFF2E6B2F.toInt()
            canvas.drawArc(s * 0.55f, -s * 0.72f, s * 1.0f, -s * 0.42f, 15f, 150f, false, ink)
        } else {
            fill.color = Color.WHITE
            canvas.drawCircle(s * 0.8f, -s * 0.57f, s * 0.26f, fill)
            fill.color = 0xFF1B1B2F.toInt()
            canvas.drawCircle(s * 0.88f, -s * 0.55f, s * 0.16f, fill)
            fill.color = Color.WHITE
            canvas.drawCircle(s * 0.82f, -s * 0.64f, s * 0.065f, fill)
        }
        // Its smile along the front of the head, and a rosy cheek.
        ink.strokeWidth = s * 0.07f
        ink.color = 0xFF2E6B2F.toInt()
        canvas.drawArc(s * 0.55f, -s * 0.15f, s * 1.25f, s * 0.22f, 10f, 110f, false, ink)
        fill.color = 0x66FF8FA3
        canvas.drawOval(s * 0.62f, -s * 0.12f, s * 0.92f, s * 0.08f, fill)
        canvas.save()
        canvas.scale(s, s)
        // The front leg, reaching ahead as it lands, tucked back as it leaves.
        ink.strokeWidth = 0.2f
        ink.color = 0xFF4CAF50.toInt()
        val fx = 0.45f + 0.55f * (1f - extend)
        val fy = 0.55f + 0.35f * (1f - extend)
        canvas.drawLine(0.45f, 0.3f, fx, fy, ink)
        fill.color = 0xFF66BB6A.toInt()
        canvas.drawCircle(fx + 0.06f, fy + 0.04f, 0.11f, fill)
        drawBackLeg(canvas, extend, 0xFF4CAF50.toInt(), 0f)
        canvas.restore()
    }

    /** One back leg, in the frog's units: thigh, shin and webbed foot, stretched behind by [extend] or folded. */
    private fun drawBackLeg(canvas: Canvas, extend: Float, colour: Int, shift: Float) {
        val hipX = -0.55f
        val hipY = 0.2f + shift
        val kneeX = -0.25f - 1.0f * extend
        val kneeY = 0.75f - 0.4f * extend + shift
        val ankleX = -0.95f - 1.0f * extend
        val ankleY = 0.6f + 0.05f * extend + shift
        ink.color = colour
        ink.strokeWidth = 0.42f
        canvas.drawLine(hipX, hipY, kneeX, kneeY, ink)
        ink.strokeWidth = 0.26f
        canvas.drawLine(kneeX, kneeY, ankleX, ankleY, ink)
        // The foot, flat and webbed, pointing back as it pushes off.
        val toeX = ankleX - 0.42f * extend + 0.38f * (1f - extend)
        val toeY = ankleY + 0.18f
        ink.strokeWidth = 0.18f
        canvas.drawLine(ankleX, ankleY, toeX, toeY, ink)
        fill.color = colour
        for (t in 0 until 3) canvas.drawCircle(toeX + (t - 1) * 0.08f, toeY + (t - 1) * 0.07f, 0.09f, fill)
    }

    /** A fly: a dark little body, and wings blurring. */
    private fun drawFly(canvas: Canvas, x: Float, y: Float, r: Float) {
        val flap = 0.55f + 0.45f * sin(time * 70f)
        fill.color = 0x99E3F2FD.toInt()
        canvas.drawOval(x - r * 1.5f, y - r * (1.6f * flap), x - r * 0.1f, y, fill)
        canvas.drawOval(x + r * 0.1f, y - r * (1.6f * flap), x + r * 1.5f, y, fill)
        fill.color = 0xFF263238.toInt()
        canvas.drawOval(x - r * 0.55f, y - r * 0.4f, x + r * 0.55f, y + r * 0.7f, fill)
        fill.color = 0xFFB71C1C.toInt()
        canvas.drawCircle(x - r * 0.25f, y - r * 0.35f, r * 0.2f, fill)
        canvas.drawCircle(x + r * 0.25f, y - r * 0.35f, r * 0.2f, fill)
    }

    override fun clear() {
        water.clear()
        leaping = false
        pending = 0
        flyAlive = false
        restY = Float.NaN
    }

    private companion object {
        /** How hard the edge pulls it back, in dp a second each second: as the fish's. */
        const val GRAVITY = 700f
        const val TONGUE_S = 0.3f
        const val BLINK_S = 3.3f
        val SIDES = floatArrayOf(-1f, 1f)
    }
}

// ---- woodpecker ------------------------------------------------------------------------------------------

/**
 * A tree trunk along the edge, and a woodpecker clinging to it beside the buttons — red cap, a white
 * cheek, its stiff tail braced against the bark. Every press it drums: a rattle of quick pecks, each
 * knocking out wood chips and driving its hole a little deeper, more pecks the louder the level. In
 * between it looks about.
 */
internal class WoodpeckerEffect(random: Random) : BumpEffect(random) {
    override val drawsBump: Boolean get() = false
    override val loops: Boolean get() = true

    private var time = 0f
    private var pecks = 0
    private var peckClock = 0f
    private var struck = false
    private var strike = 0f
    private var lastImpact = -10f
    private var hole = 0f
    private var look = 0f
    private val chips = ParticlePool(90)

    private val bark = solidFill()
    private var barkShaderFor = -1f
    private val grain = Path()
    private var grainFor = 0L
    private val fill = solidFill()
    private val ink = roundStroke()
    private val point = FloatArray(2)
    private val local = Matrix()

    override val stillAfterS: Float get() = 0.33f

    private fun unitOf(scene: BumpScene) = scene.unit * 1.05f
    private fun trunkWidth(scene: BumpScene) = scene.unit * 1.6f * easeOut(scene.presence.coerceIn(0f, 1f))

    override fun press(scene: BumpScene, atLimit: Boolean) {
        pecks = min(pecks + count(scene, if (atLimit) 3 else 6), 14)
        if (pecks > 0 && peckClock <= 0f) peckClock = 0.0001f
    }

    /** Which way along the edge is up the screen: the bird climbs that way, its head up. */
    private fun headUp(scene: BumpScene): Float = if (scene.gravityY < -0.3f) 1f else -1f

    /** From the bird's own frame — the bark at x = 0, its head up — to upright terms. */
    private fun frame(scene: BumpScene) {
        local.reset()
        local.setScale(1f, -headUp(scene))
        local.postTranslate(trunkWidth(scene), scene.centre)
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        time += dt
        val d = scene.density
        val s = unitOf(scene)
        if (pecks > 0) {
            peckClock += dt
            val phase = peckClock / PECK_S
            strike = sin(PI.toFloat() * phase.coerceIn(0f, 1f))
            if (!struck && phase >= 0.5f) {
                struck = true
                impact(scene, s)
            }
            if (phase >= 1f) {
                peckClock = 0.0001f
                struck = false
                pecks--
                if (pecks == 0) peckClock = 0f
            }
        } else {
            strike *= kotlin.math.exp(-18f * dt)
        }
        // Looking about between drums.
        look = sin(time * 1.7f) * sin(time * 0.6f + 1f)
        val drag = kotlin.math.exp(-1.5f * dt)
        var alive = false
        for (p in chips.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.life || scene.offScreen(p.x, p.y, 20f * d)) {
                p.alive = false
                continue
            }
            p.vx = p.vx * drag + scene.gravityX * 900f * d * dt
            p.vy = p.vy * drag + scene.gravityY * 900f * d * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.angle += p.spin * dt
            alive = true
        }
        if (scene.presence < 0.02f) hole = 0f
        return alive || pecks > 0 || scene.presence > 0.01f
    }

    /** A peck landing: chips knocked out, and the hole a little deeper. */
    private fun impact(scene: BumpScene, s: Float) {
        val d = scene.density
        lastImpact = time
        hole = min(hole + 0.05f, 0.34f)
        frame(scene)
        point[0] = -s * 0.05f
        point[1] = BEAK_Y * s
        local.mapPoints(point)
        repeat(count(scene, 4)) {
            val p = chips.spawn() ?: return@repeat
            p.x = point[0]
            p.y = point[1]
            val speed = rnd(70f, 190f) * d
            val a = rnd(-1.1f, 1.1f)
            p.vx = cos(a) * speed
            p.vy = sin(a) * speed
            p.life = rnd(0.6f, 1f)
            p.size = rnd(1.4f, 2.8f) * d
            p.angle = rnd(0f, 360f)
            p.spin = rnd(-720f, 720f)
            p.color = if (random.nextInt(3) == 0) 0xFF6D4C41.toInt() else 0xFFD9B88F.toInt()
        }
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        val w = trunkWidth(scene)
        val s = unitOf(scene)
        if (w > 0.5f) {
            drawTrunk(canvas, scene, w)
            frame(scene)
            canvas.save()
            canvas.concat(local)
            // The hole, in the bark at the beak's height.
            if (hole > 0.01f) {
                val r = hole * s
                fill.color = 0xFFC9A27E.toInt()
                canvas.drawOval(-r * 0.9f - s * 0.08f, BEAK_Y * s - r * 1.15f, r * 0.4f, BEAK_Y * s + r * 1.15f, fill)
                fill.color = 0xFF2B1A12.toInt()
                canvas.drawOval(-r * 0.75f - s * 0.06f, BEAK_Y * s - r * 0.9f, r * 0.15f, BEAK_Y * s + r * 0.9f, fill)
            }
            drawBird(canvas, s)
            // A flick of lines where the beak hits.
            val since = time - lastImpact
            if (since < 0.09f) {
                ink.strokeWidth = 1.2f * d
                ink.color = fade(0xFFFFFFFF.toInt(), 0.8f * (1f - since / 0.09f))
                for (k in -1..1) {
                    val a = PI.toFloat() + k * 0.6f
                    canvas.drawLine(
                        cos(a) * s * 0.15f, BEAK_Y * s + sin(a) * s * 0.15f,
                        cos(a) * s * 0.45f, BEAK_Y * s + sin(a) * s * 0.45f, ink,
                    )
                }
            }
            canvas.restore()
        }
        for (p in chips.items) {
            if (!p.alive) continue
            val a = (1f - p.age / p.life).coerceIn(0f, 1f)
            fill.color = fade(p.color, a)
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.angle)
            canvas.drawRect(-p.size, -p.size * 0.45f, p.size, p.size * 0.45f, fill)
            canvas.restore()
        }
    }

    /** The trunk along the edge: bark lit across its round, grooved along its length, a knot or two. */
    private fun drawTrunk(canvas: Canvas, scene: BumpScene, w: Float) {
        if (abs(barkShaderFor - w) > 0.5f) {
            bark.shader = LinearGradient(
                0f, 0f, w, 0f,
                intArrayOf(0xFF3E2A22.toInt(), 0xFF6D4C41.toInt(), 0xFF8D6E63.toInt(), 0xFF4E342E.toInt()),
                floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP,
            )
            barkShaderFor = w
        }
        val top = scene.screenTop - 10f
        val bottom = scene.screenBottom + 10f
        canvas.drawRect(-4f, top, w, bottom, bark)
        // The grooves, made once for the trunk's size.
        val key = (w.roundToInt().toLong() shl 32) xor (bottom - top).roundToInt().toLong()
        if (key != grainFor) {
            grainFor = key
            grain.reset()
            val seeded = Random(91L)
            val lines = 7
            for (i in 0 until lines) {
                val gx = w * (0.12f + 0.78f * i / (lines - 1))
                var y = top
                grain.moveTo(gx, y)
                while (y < bottom) {
                    y += 18f * scene.density + seeded.nextFloat() * 22f * scene.density
                    grain.lineTo(gx + (seeded.nextFloat() - 0.5f) * w * 0.12f, y)
                }
            }
        }
        ink.strokeWidth = 1.3f * scene.density
        ink.color = 0x993B2620.toInt()
        canvas.drawPath(grain, ink)
        // A knot a little way off, along the trunk.
        for (k in floatArrayOf(-2.6f, 3.1f)) {
            val ky = scene.centre + k * scene.unit * 2.2f
            fill.color = 0xFF4E342E.toInt()
            canvas.drawOval(w * 0.25f, ky - w * 0.32f, w * 0.85f, ky + w * 0.32f, fill)
            fill.color = 0xFF8D6E63.toInt()
            canvas.drawOval(w * 0.38f, ky - w * 0.16f, w * 0.72f, ky + w * 0.16f, fill)
        }
    }

    /** The tail and the beak, in the bird's own units, made once. */
    private val tail = Path().apply {
        moveTo(0.55f, 0.75f)
        lineTo(0.1f, 1.75f)
        lineTo(0.32f, 1.8f)
        lineTo(0.8f, 0.85f)
        close()
    }
    private val beak = Path().apply {
        moveTo(-0.38f, -0.1f)
        lineTo(-0.95f, 0.04f)
        lineTo(-0.38f, 0.14f)
        close()
    }

    /** The woodpecker on the bark at x = 0, its beak to the bark and its head up (-y), [s] to a unit. */
    private fun drawBird(canvas: Canvas, s: Float) {
        canvas.save()
        canvas.scale(s, s)
        // Tail, braced against the bark below it.
        fill.color = 0xFF212121.toInt()
        canvas.drawPath(tail, fill)
        // Feet gripping the bark.
        ink.strokeWidth = 0.08f
        ink.color = 0xFF5D4037.toInt()
        canvas.drawLine(0.5f, 0.35f, 0.06f, 0.3f, ink)
        canvas.drawLine(0.52f, 0.6f, 0.06f, 0.62f, ink)
        // Body: black back, a white front towards the bark, the wing barred with white.
        fill.color = 0xFF222222.toInt()
        canvas.drawOval(0.38f, -0.75f, 1.38f, 1.0f, fill)
        fill.color = 0xFFF5F5F5.toInt()
        canvas.drawOval(0.3f, -0.5f, 0.82f, 0.85f, fill)
        fill.color = 0xFF111111.toInt()
        canvas.drawOval(0.78f, -0.55f, 1.34f, 0.85f, fill)
        fill.color = Color.WHITE
        for (row in 0 until 4) for (col in 0 until 2) {
            canvas.drawCircle(0.95f + col * 0.18f, -0.3f + row * 0.28f, 0.05f, fill)
        }
        // Head: forward with each peck, turning a little as it looks about.
        canvas.translate(-0.27f * strike, 0f)
        canvas.rotate(look * 6f - strike * 4f, 1.0f, -0.85f)
        canvas.translate(1.0f, -0.95f)
        fill.color = 0xFF222222.toInt()
        canvas.drawCircle(0f, 0f, 0.5f, fill)
        // The red cap on top and the white cheek stripe.
        fill.color = 0xFFE53935.toInt()
        canvas.drawOval(-0.18f, -0.56f, 0.5f, -0.08f, fill)
        fill.color = 0xFFF5F5F5.toInt()
        canvas.drawOval(-0.42f, 0.02f, 0.22f, 0.28f, fill)
        // The beak: long and grey, its tip at the bark at rest.
        fill.color = 0xFF616161.toInt()
        canvas.drawPath(beak, fill)
        // A bright eye.
        fill.color = Color.WHITE
        canvas.drawCircle(-0.12f, -0.12f, 0.15f, fill)
        fill.color = 0xFF111111.toInt()
        canvas.drawCircle(-0.15f, -0.11f, 0.09f, fill)
        fill.color = Color.WHITE
        canvas.drawCircle(-0.19f, -0.16f, 0.035f, fill)
        canvas.restore()
    }

    override fun clear() {
        chips.clear()
        pecks = 0
        hole = 0f
        strike = 0f
    }

    private companion object {
        /** One peck, in and out: a woodpecker drums at about fifteen a second. */
        const val PECK_S = 0.07f

        /** Where along the bird the beak strikes, in units of it: the head's height. */
        const val BEAK_Y = -0.9f
    }
}

// ---- spider ---------------------------------------------------------------------------------------------

/**
 * A cute little spider, a different colour each time it comes — red, black, blue, green, purple,
 * orange, pink or yellow — that crawls out beside the buttons, shoots a line of silk up to the camera
 * hole, or the top of the screen where there is none, and swings across on it to the other edge.
 * Every press sends it off again, swinging back the way it came; and every so often, when it likes,
 * it climbs away up its line or drops off the screen. The louder the level, the harder it swings.
 *
 * A spider of its own: round, big-eyed and smiling, with no costume, mask or web pattern on it.
 *
 * It lives on the screen as the user sees it, so it is worked out in the window's own pixels — its
 * top at the top, falling down — and drawn through [BumpScene.fromWindow].
 */
internal class SpiderEffect(random: Random) : BumpEffect(random) {
    override val drawsBump: Boolean get() = false
    override val loops: Boolean get() = true

    private enum class State { HIDDEN, ENTER, SHOOT, SWING, FLY, CLING, EXIT }

    private var state = State.HIDDEN
    private var age = 0f
    private var time = 0f
    private var x = 0f
    private var y = 0f
    private var vx = 0f
    private var vy = 0f
    private var fromX = 0f
    private var fromY = 0f
    private var toX = 0f
    private var toY = 0f

    /** The line's anchor, its length, and the swing on it. */
    private var pivotX = 0f
    private var pivotY = 0f
    private var length = 1f
    private var theta = 0f
    private var omega = 0f
    private var shot = 0f
    private var lineFade = 0f
    private var power = 1f

    private var swings = 0
    private var maxSwings = 3
    private var pending = 0
    private var idle = 0f
    private var climb = false
    private var colour = 0

    private val toWindow = Matrix()
    private val point = FloatArray(2)
    private val fill = solidFill()
    private val ink = roundStroke()
    private val shades = Array(COLOURS.size) { i -> bodyShade(COLOURS[i][0], COLOURS[i][1], COLOURS[i][2]) }
    private val bodyPaint = solidFill()

    override val stillAfterS: Float get() = 0.95f

    private fun radius(scene: BumpScene) = scene.unit * 0.68f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        power = scene.power * if (atLimit) 0.8f else 1f
        idle = 0f
        when (state) {
            State.HIDDEN -> enter(scene)
            State.CLING -> shoot(scene)
            else -> pending = min(pending + 1, 2)
        }
    }

    /** Out of the edge beside the buttons, a new colour each time. */
    private fun enter(scene: BumpScene) {
        if (!scene.fromWindow.invert(toWindow)) return
        point[0] = 0f
        point[1] = scene.centre
        toWindow.mapPoints(point)
        val w = scene.windowWidth
        val r = radius(scene)
        // On whichever side of the window the buttons are.
        val left = point[0] < w / 2f
        fromX = if (left) -r * 2f else w + r * 2f
        fromY = point[1].coerceIn(r * 6f, scene.windowHeight - r * 3f)
        toX = if (left) r * 2.2f else w - r * 2.2f
        toY = fromY
        x = fromX
        y = fromY
        colour = random.nextInt(COLOURS.size)
        swings = 0
        maxSwings = 2 + random.nextInt(3)
        state = State.ENTER
        age = 0f
    }

    /** A line of silk to the camera hole, or the top of the screen. */
    private fun shoot(scene: BumpScene) {
        val w = scene.windowWidth
        val r = radius(scene)
        var ax = Float.NaN
        var ay = Float.NaN
        if (!scene.cutoutX.isNaN() && scene.fromWindow.invert(toWindow)) {
            point[0] = scene.cutoutX
            point[1] = scene.cutoutY
            toWindow.mapPoints(point)
            if (point[1] < scene.windowHeight * 0.25f) {
                ax = point[0]
                ay = point[1]
            }
        }
        if (ax.isNaN()) {
            ax = w * (0.5f + rnd(-0.14f, 0.14f))
            ay = 0f
        }
        // Never a line too short to swing on.
        if (y - ay < r * 8f) ay = (y - r * 8f).coerceAtLeast(0f)
        pivotX = ax
        pivotY = ay
        shot = 0f
        lineFade = 1f
        state = State.SHOOT
        age = 0f
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        time += dt
        age += dt
        val d = scene.density
        val w = scene.windowWidth
        val h = scene.windowHeight
        val r = radius(scene)
        val g = GRAVITY * d
        if (state != State.SWING && state != State.SHOOT) lineFade = (lineFade - dt * 4f).coerceAtLeast(0f)
        when (state) {
            State.HIDDEN -> return false
            State.ENTER -> {
                val k = easeOut(age / 0.4f)
                x = fromX + (toX - fromX) * k
                y = fromY
                if (age >= 0.4f) shoot(scene)
            }
            State.SHOOT -> {
                val dist = hypot(pivotX - x, pivotY - y)
                shot = (shot + dt * SHOOT_SPEED * d / dist.coerceAtLeast(1f)).coerceAtMost(1f)
                if (shot >= 1f) {
                    length = dist.coerceAtLeast(r * 4f)
                    theta = atan2(x - pivotX, y - pivotY)
                    // A push across, harder the louder.
                    omega = -sign(theta) * (0.5f + 0.7f * power) * sqrt(g / length) * 0.35f
                    state = State.SWING
                    age = 0f
                }
            }
            State.SWING -> {
                val before = omega
                omega += -(g / length) * sin(theta) * dt
                theta += omega * dt
                x = pivotX + length * sin(theta)
                y = pivotY + length * cos(theta)
                val nearEdge = x < r * 2.5f || x > w - r * 2.5f
                val turned = before != 0f && sign(before) != sign(omega)
                // Let go at the far edge, or at the top of the swing and leap the rest of the way.
                if ((nearEdge && age > 0.25f) || turned) {
                    vx = length * omega * cos(theta)
                    vy = -length * omega * sin(theta)
                    if (turned && !nearEdge) {
                        val toLeft = x < w / 2f
                        vx = (if (toLeft) -1f else 1f) * 520f * d
                        vy = -260f * d
                    }
                    state = State.FLY
                    age = 0f
                }
            }
            State.FLY -> {
                vy += g * dt
                x += vx * dt
                y += vy * dt
                if (x < r * 1.6f || x > w - r * 1.6f) {
                    x = x.coerceIn(r * 1.6f, w - r * 1.6f)
                    state = State.CLING
                    age = 0f
                    swings++
                } else if (y > h + r * 4f) {
                    state = State.HIDDEN
                }
            }
            State.CLING -> {
                idle += dt
                // A little crawl along the edge as it settles.
                y += sin(age * 6f) * 6f * d * dt
                if (pending > 0 && age > 0.2f) {
                    pending--
                    shoot(scene)
                } else if (swings >= maxSwings || idle > IDLE_S || (age > 0.6f && swings >= 2 && random.nextFloat() < dt * 0.6f)) {
                    climb = random.nextBoolean()
                    pivotX = x
                    pivotY = 0f
                    lineFade = if (climb) 1f else 0f
                    if (!climb) {
                        vx = (if (x < w / 2f) -1f else 1f) * 160f * d
                        vy = -200f * d
                    }
                    state = State.EXIT
                    age = 0f
                }
            }
            State.EXIT -> {
                if (climb) {
                    // Up its line, off the top.
                    y -= (260f + 600f * min(age, 1f)) * d * dt
                    if (y < -r * 4f) state = State.HIDDEN
                } else {
                    // A hop off the edge, and gone.
                    vy += g * dt
                    x += vx * dt
                    y += vy * dt
                    if (y > h + r * 4f || x < -r * 4f || x > w + r * 4f) state = State.HIDDEN
                }
            }
        }
        return state != State.HIDDEN
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        if (state == State.HIDDEN || scene.windowWidth <= 0f) return
        val d = scene.density
        val r = radius(scene)
        canvas.save()
        canvas.concat(scene.fromWindow)
        // The silk.
        val lineVisible = state == State.SHOOT || state == State.SWING || (state == State.EXIT && climb) || lineFade > 0f
        if (lineVisible) {
            val reach = if (state == State.SHOOT) shot else 1f
            val alpha = if (state == State.SHOOT || state == State.SWING || (state == State.EXIT && climb)) 1f else lineFade
            val ex = x + (pivotX - x) * reach
            val ey = y + (pivotY - y) * reach
            ink.strokeWidth = 1.3f * d
            ink.color = fade(0xEEFFFFFF.toInt(), alpha)
            canvas.drawLine(x, y - r * 0.4f, ex, ey, ink)
            ink.strokeWidth = 3.5f * d
            ink.color = fade(0x33FFFFFF, alpha)
            canvas.drawLine(x, y - r * 0.4f, ex, ey, ink)
        }
        val hanging = state == State.SWING || state == State.SHOOT || (state == State.EXIT && climb)
        val rotation = if (hanging) Math.toDegrees(atan2(pivotX - x, y - pivotY).toDouble()).toFloat() else when (state) {
            State.FLY -> (vx * 0.02f / d).coerceIn(-30f, 30f)
            else -> 0f
        }
        val walking = state == State.ENTER || state == State.CLING
        drawSpider(canvas, x, y, r, rotation, walking, splay = hanging || state == State.FLY)
        canvas.restore()
    }

    /** The spider at [cx], [cy], its head up the screen turned by [rotation] degrees. */
    private fun drawSpider(canvas: Canvas, cx: Float, cy: Float, r: Float, rotation: Float, walking: Boolean, splay: Boolean) {
        val c = COLOURS[colour]
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rotation)
        // Eight legs, four a side, bent at the knee; stepping when it walks, spread when it swings.
        ink.color = c[3]
        ink.strokeWidth = r * 0.17f
        for (side in intArrayOf(-1, 1)) {
            for (i in 0 until 4) {
                val step = if (walking) sin(time * 16f + i * 1.6f + side) * 0.18f else 0f
                val spread = if (splay) 0.25f else 0f
                val bx = side * r * 0.32f
                val by = -r * 0.2f + (i - 1.5f) * r * 0.24f
                val kx = bx + side * r * (0.95f + spread)
                val ky = by - r * (0.62f - i * 0.38f) + step * r
                val fx = kx + side * r * (0.5f + spread * 0.6f)
                val fy = ky + r * (0.85f - spread * 0.4f)
                canvas.drawLine(bx, by, kx, ky, ink)
                canvas.drawLine(kx, ky, fx, fy, ink)
            }
        }
        // The body, round and glossy, and the head in front of it.
        bodyPaint.shader = shades[colour]
        canvas.save()
        canvas.translate(0f, r * 0.45f)
        canvas.scale(r, r * 1.05f)
        canvas.drawCircle(0f, 0f, 1f, bodyPaint)
        canvas.restore()
        canvas.save()
        canvas.translate(0f, -r * 0.55f)
        canvas.scale(r * 0.66f, r * 0.62f)
        canvas.drawCircle(0f, 0f, 1f, bodyPaint)
        canvas.restore()
        // A few spots on its back.
        fill.color = fade(c[4], 0.55f)
        canvas.drawCircle(-r * 0.35f, r * 0.35f, r * 0.12f, fill)
        canvas.drawCircle(r * 0.3f, r * 0.62f, r * 0.1f, fill)
        canvas.drawCircle(r * 0.05f, r * 0.95f, r * 0.08f, fill)
        // A big-eyed, smiling face.
        for (side in intArrayOf(-1, 1)) {
            val ex = side * r * 0.26f
            val ey = -r * 0.62f
            fill.color = Color.WHITE
            canvas.drawCircle(ex, ey, r * 0.22f, fill)
            fill.color = 0xFF14141C.toInt()
            canvas.drawCircle(ex + side * r * 0.02f, ey + r * 0.03f, r * 0.13f, fill)
            fill.color = Color.WHITE
            canvas.drawCircle(ex - r * 0.04f, ey - r * 0.05f, r * 0.05f, fill)
        }
        fill.color = 0x66FF8FA3
        canvas.drawOval(-r * 0.56f, -r * 0.45f, -r * 0.3f, -r * 0.32f, fill)
        canvas.drawOval(r * 0.3f, -r * 0.45f, r * 0.56f, -r * 0.32f, fill)
        ink.strokeWidth = r * 0.08f
        ink.color = c[5]
        canvas.drawArc(-r * 0.2f, -r * 0.5f, r * 0.2f, -r * 0.26f, 20f, 140f, false, ink)
        canvas.restore()
    }

    override fun clear() {
        state = State.HIDDEN
        pending = 0
        lineFade = 0f
    }

    private companion object {
        /** How hard it falls, in dp a second each second: snappier than real, for a lively swing. */
        const val GRAVITY = 2200f
        const val SHOOT_SPEED = 2600f
        const val IDLE_S = 2.6f

        /** Each spider's colours: light, middle and dark of its body, its legs, its spots and its smile. */
        val COLOURS = arrayOf(
            intArrayOf(0xFFFF8A80.toInt(), 0xFFE53935.toInt(), 0xFF9A1B1B.toInt(), 0xFF7A1414.toInt(), 0xFFFFCDD2.toInt(), 0xFF5A0F0F.toInt()),
            intArrayOf(0xFF6B6B78.toInt(), 0xFF2A2A33.toInt(), 0xFF0E0E14.toInt(), 0xFF101016.toInt(), 0xFF9E9EAE.toInt(), 0xFFFFFFFF.toInt()),
            intArrayOf(0xFF90CAF9.toInt(), 0xFF1E88E5.toInt(), 0xFF0D47A1.toInt(), 0xFF0B3C86.toInt(), 0xFFE3F2FD.toInt(), 0xFF0A2F66.toInt()),
            intArrayOf(0xFFA5D6A7.toInt(), 0xFF43A047.toInt(), 0xFF1B5E20.toInt(), 0xFF174F1B.toInt(), 0xFFE8F5E9.toInt(), 0xFF103D14.toInt()),
            intArrayOf(0xFFCE93D8.toInt(), 0xFF8E24AA.toInt(), 0xFF4A148C.toInt(), 0xFF3E1077.toInt(), 0xFFF3E5F5.toInt(), 0xFF2E0B59.toInt()),
            intArrayOf(0xFFFFCC80.toInt(), 0xFFFB8C00.toInt(), 0xFFBF360C.toInt(), 0xFF8F2A0A.toInt(), 0xFFFFF3E0.toInt(), 0xFF6B1F07.toInt()),
            intArrayOf(0xFFF8BBD0.toInt(), 0xFFEC407A.toInt(), 0xFF880E4F.toInt(), 0xFF6F0B40.toInt(), 0xFFFCE4EC.toInt(), 0xFF5A0934.toInt()),
            intArrayOf(0xFFFFF59D.toInt(), 0xFFFDD835.toInt(), 0xFFF57F17.toInt(), 0xFF8D6E10.toInt(), 0xFFFFFDE7.toInt(), 0xFF6D4C00.toInt()),
        )
    }
}
