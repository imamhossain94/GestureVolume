package com.newagedevs.gesturevolume.ui.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
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
 * Three more characters for the button animation: a frog that leaps out of the water at the edge, a
 * woodpecker drumming on a piece of trunk beside it, and the Spider Queen, who swings across the
 * screen on a line of silk. All drawn from code, all of them the app's own.
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
 * louder the level, the higher it leaps; and as the fish do, quick presses bring more frogs leaping out
 * of the pond with it, a little smaller, each from a place of its own along the water.
 */
internal class FrogEffect(random: Random) : BumpEffect(random) {
    override val drawsBump: Boolean get() = false
    override val loops: Boolean get() = true

    /** A frog in the air: the one that lives here, or one of its friends from the pond. */
    private class Leaper {
        var alive = false
        var home = false
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var age = 0f
        var size = 1f
    }

    private val water = WaterStrip(random)
    private var time = 0f
    private val leapers = Array(5) { Leaper() }

    /** Where it sits along the edge between leaps, in upright terms; NaN before the first press. */
    private var restY = Float.NaN
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

    override val stillAfterS: Float get() = 0.16f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        if (restY.isNaN()) restY = scene.centre
        val homeOut = leapers.any { it.alive && it.home }
        val l = leapers.firstOrNull { !it.alive } ?: return
        leap(scene, l, atLimit, home = !homeOut)
    }

    private fun leap(scene: BumpScene, l: Leaper, atLimit: Boolean, home: Boolean) {
        val d = scene.density
        val power = scene.power * if (atLimit) 0.75f else 1f
        val limit = water.half(scene) * 0.7f
        l.alive = true
        l.home = home
        l.age = 0f
        l.size = if (home) 1f else rnd(0.78f, 0.92f)
        l.x = water.depth(scene) * 0.3f
        l.y = if (home) restY else spot(scene, limit)
        l.vx = rnd(330f, 390f) * d * (0.58f + 0.42f * power) * (if (home) 1f else rnd(0.82f, 1f))
        // Along the edge towards the button pressed, and back the other way when the pond runs out.
        val dir = if (home) {
            if (scene.towards < 0) -1f else 1f
        } else {
            if (random.nextBoolean()) 1f else -1f
        }
        var target = l.y + dir * rnd(18f, 42f) * d
        if (abs(target - scene.centre) > limit) target = l.y - dir * rnd(18f, 42f) * d
        val flight = 2f * l.vx / (GRAVITY * d)
        l.vy = (target - l.y) / flight
        if (home) {
            // A fly where the top of the leap will be, just ahead of the frog's mouth.
            val top = l.x + l.vx * l.vx / (2f * GRAVITY * d)
            flyX = top + scene.unit * 1.25f
            flyY = l.y + l.vy * flight / 2f
            flyAlive = true
            tongueAt = -10f
        }
        water.splash(scene, l.y, 0.7f * power * l.size)
    }

    /** A place along the pond for another frog to leap from, as far as it can be from the others. */
    private fun spot(scene: BumpScene, limit: Float): Float {
        var best = scene.centre
        var bestGap = -1f
        repeat(8) {
            val y = scene.centre + rnd(-limit, limit)
            var gap = abs(y - restY)
            for (o in leapers) if (o.alive) gap = min(gap, abs(y - o.y))
            if (gap > bestGap) {
                bestGap = gap
                best = y
            }
        }
        return best
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        time += dt
        val d = scene.density
        var alive = water.step(scene, dt)
        for (l in leapers) {
            if (!l.alive) continue
            l.age += dt
            l.vx -= GRAVITY * d * dt
            l.x += l.vx * dt
            l.y += l.vy * dt
            // At the top of the leap the tongue goes for the fly.
            if (l.home && flyAlive && tongueAt < 0f && l.vx <= 60f * d) tongueAt = time
            if (l.vx < 0f && l.x < water.depth(scene) * 0.4f || l.age > 4f) {
                l.alive = false
                water.splash(scene, l.y, 0.9f * scene.power * l.size)
                if (l.home) {
                    restY = l.y
                    landedAt = time
                    flyAlive = false
                }
            } else {
                alive = true
            }
        }
        if (tongueAt >= 0f && time - tongueAt > TONGUE_S) flyAlive = false
        return alive || scene.presence > 0.01f
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        water.draw(canvas, scene)
        if (restY.isNaN()) restY = scene.centre
        val unit = scene.unit * 1.05f
        // A quick blink every few seconds.
        val blinking = time % BLINK_S > BLINK_S - 0.12f
        val home = leapers.firstOrNull { it.alive && it.home }
        // The fly, buzzing about where it waits.
        if (flyAlive && home != null) {
            val caught = if (tongueAt >= 0f) ((time - tongueAt) / TONGUE_S).coerceIn(0f, 1f) else 0f
            val mouthX = home.x + unit * 1.2f
            var fx = flyX + sin(time * 13f) * unit * 0.15f
            var fy = flyY + sin(time * 9f + 1f) * unit * 0.2f
            if (caught > 0.5f) {
                // Caught: it comes back with the tongue.
                val back = (caught - 0.5f) / 0.5f
                fx += (mouthX - fx) * back
                fy += (home.y - fy) * back
            }
            if (caught < 0.95f) drawFly(canvas, fx, fy, unit * 0.32f)
        }
        for (l in leapers) {
            if (!l.alive) continue
            val s = unit * l.size
            // Along its path, as the fish flies; its back turned up the screen, as a frog's is.
            val angle = atan2(l.vy, l.vx)
            val backUp = sin(angle) * scene.upX - cos(angle) * scene.upY
            val flip = if (backUp < 0f) -1f else 1f
            leapFrame.reset()
            leapFrame.postScale(1f, flip)
            leapFrame.postRotate(Math.toDegrees(angle.toDouble()).toFloat())
            leapFrame.postTranslate(l.x, l.y)
            canvas.save()
            canvas.concat(leapFrame)
            val extend = (l.vx / (330f * scene.density)).coerceIn(0f, 1f)
            drawLeaper(canvas, s, extend, blinking)
            canvas.restore()
            // The tongue, flicking out from its mouth to the fly and back.
            if (l.home && tongueAt >= 0f) {
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
        }
        val depth = water.depth(scene)
        if (home == null && depth > 1f) {
            // Peeking: eyes and the top of the head above the water, bobbing a little.
            val s = unit
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
        for (l in leapers) l.alive = false
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
 * A piece of a tree trunk at the edge, and a woodpecker clinging to it beside the buttons — red cap, a white
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
    private val shadow = solidFill()
    private val ends = solidFill().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
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

    /**
     * A piece of the trunk, not the whole edge: as long as a hand, centred on the bird, its ends fading
     * into the screen. Bark lit across its round, grooved along its length, a knot above and below, and
     * a soft shadow where it stands off the glass.
     */
    private fun drawTrunk(canvas: Canvas, scene: BumpScene, w: Float) {
        val d = scene.density
        val tall = scene.screenBottom - scene.screenTop
        // A hand's length on a phone; on a gallery tile, most of the tile.
        val half = if (tall < scene.unit * 12f) tall * 0.42f else min(scene.unit * TRUNK_HALF_UNITS, tall * 0.32f)
        val top = scene.centre - half
        val length = half * 2f
        if (abs(barkShaderFor - w) > 0.5f) {
            bark.shader = LinearGradient(
                0f, 0f, w, 0f,
                intArrayOf(0xFF3E2A22.toInt(), 0xFF6D4C41.toInt(), 0xFF8D6E63.toInt(), 0xFF4E342E.toInt()),
                floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP,
            )
            shadow.shader = LinearGradient(w, 0f, w + 7f * d, 0f, 0x55000000, 0x00000000, Shader.TileMode.CLAMP)
            barkShaderFor = w
        }
        canvas.save()
        canvas.translate(0f, top)
        val right = w + 7f * d
        canvas.saveLayer(-4f, 0f, right, length, null)
        // Nothing past its faded ends: the grooves' round tips would show there as a dotted line.
        canvas.clipRect(-4f, 0f, right, length)
        canvas.drawRect(w, 0f, right, length, shadow)
        canvas.drawRect(-4f, 0f, w, length, bark)
        // The grooves, made once for the trunk's size.
        val key = (w.roundToInt().toLong() shl 32) xor length.roundToInt().toLong()
        if (key != grainFor) {
            grainFor = key
            grain.reset()
            val seeded = Random(91L)
            val lines = 7
            for (i in 0 until lines) {
                val gx = w * (0.12f + 0.78f * i / (lines - 1))
                var y = 0f
                grain.moveTo(gx, y)
                while (y < length) {
                    y += 18f * d + seeded.nextFloat() * 22f * d
                    grain.lineTo(gx + (seeded.nextFloat() - 0.5f) * w * 0.12f, y)
                }
            }
            ends.shader = LinearGradient(
                0f, 0f, 0f, length,
                intArrayOf(0x00000000, 0xFF000000.toInt(), 0xFF000000.toInt(), 0x00000000),
                floatArrayOf(0f, 0.2f, 0.8f, 1f), Shader.TileMode.CLAMP,
            )
        }
        ink.strokeWidth = 1.3f * d
        ink.color = 0x993B2620.toInt()
        canvas.drawPath(grain, ink)
        // A knot above the bird and one below it.
        for (k in floatArrayOf(-0.62f, 0.55f)) {
            val ky = half + k * half
            fill.color = 0xFF4E342E.toInt()
            canvas.drawOval(w * 0.25f, ky - w * 0.32f, w * 0.85f, ky + w * 0.32f, fill)
            fill.color = 0xFF8D6E63.toInt()
            canvas.drawOval(w * 0.38f, ky - w * 0.16f, w * 0.72f, ky + w * 0.16f, fill)
        }
        // Both ends faded away, so it is a glimpse of a tree rather than a post.
        canvas.drawRect(-4f, 0f, right, length, ends)
        canvas.restore()
        canvas.restore()
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
        /** Half the length of the piece of trunk that shows, in units of the bump's depth. */
        const val TRUNK_HALF_UNITS = 7.5f

        /** One peck, in and out: a woodpecker drums at about fifteen a second. */
        const val PECK_S = 0.07f

        /** Where along the bird the beak strikes, in units of it: the head's height. */
        const val BEAK_Y = -0.9f
    }
}

// ---- the Spider Queen -------------------------------------------------------------------------------------

/**
 * The Spider Queen, as a cute little hero of the app's own: a big-headed girl in a bodysuit and a
 * domino mask, a gold tiara, and the gold bracelets her silk shoots from — in a different suit each
 * time she comes: violet, teal, rose, emerald, black or orange.
 *
 * She comes out of the edge beside the buttons and crouches there, waving; or, the next time, drops
 * from the camera hole — the top of the screen where there is none — on a line, and hangs upside down,
 * bobbing and waving. Every press she shoots a line of silk to the camera hole or the top of the
 * screen and swings across on it to the other edge: the line curves as it flies out, twangs as it
 * catches, and bows as she swings. A press while she hangs flips her over into a swing. When she has
 * swung enough, or been left alone a while, she zips away up a line.
 *
 * After the Spider Queen of 1941 comics, a public-domain heroine whose bracelets shot a web: the idea
 * is hers, the drawing is new — the suit, mask, tiara and look are the app's own.
 *
 * She lives on the screen as the user sees it, so she is worked out in the window's own pixels — its
 * top at the top, falling down — and drawn through [BumpScene.fromWindow]. Her body is drawn in her
 * own units, from her hips, y down: her head is a big circle above them, her limbs two strokes each.
 */
internal class SpiderQueenEffect(random: Random) : BumpEffect(random) {
    override val drawsBump: Boolean get() = false
    override val loops: Boolean get() = true

    private enum class State { HIDDEN, ENTER, CLING, SHOOT, SWING, FLY, DROP, HANG, ZIP }

    private enum class Pose { CLING, SWING, HANG, FLY }

    private var state = State.HIDDEN
    private var pose = Pose.CLING
    private var age = 0f
    private var time = 0f

    /** Where she is held from, in window pixels: the hand on the line, her feet when she hangs, her middle in the air, her hips on the wall. */
    private var x = 0f
    private var y = 0f
    private var vx = 0f
    private var vy = 0f

    /** How she is turned, in radians, eased towards where she should be; and whether she is drawn mirrored. */
    private var tilt = 0f
    private var mirror = 1f

    /** Which side's wall she is on: −1 the left, +1 the right. */
    private var wall = 1f
    private var fromX = 0f
    private var toX = 0f
    private var restY = 0f

    /** The line's anchor, its length, and the swing on it. */
    private var pivotX = 0f
    private var pivotY = 0f
    private var length = 1f
    private var theta = 0f
    private var omega = 0f
    private var shot = 0f
    private var lineFade = 0f
    private var power = 1f

    /** The line's bow at its middle, as an offset in pixels, sprung so that it twangs when it catches. */
    private var bowX = 0f
    private var bowY = 0f
    private var bowVX = 0f
    private var bowVY = 0f

    private var swings = 0
    private var maxSwings = 3
    private var pending = 0
    private var idle = 0f
    private var visits = 0
    private var suit = 0

    private val j = FloatArray(24)
    private var headTilt = 0f
    private val toWindow = Matrix()
    private val point = FloatArray(2)
    private val silk = Path()
    private val bangs = Path()
    private val tiara = Path()
    private val tail = Path()
    private val fill = solidFill()
    private val ink = roundStroke()
    private val suitShades = Array(SUITS.size) { i ->
        LinearGradient(-0.45f, 0f, 0.45f, 0f, intArrayOf(SUITS[i][0], SUITS[i][1], SUITS[i][2]), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }
    private val torsoPaint = solidFill()
    private val torso = Path()
    private val locks = Path()

    /** The gold bands at her wrists and boot tops: square-ended, so they read as bands, not knobs. */
    private val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val hairShade = solidFill().apply { shader = bodyShade(HAIR_LIT, HAIR, HAIR_DARK) }

    init {
        // A fringe across the forehead, scalloped along its edge.
        bangs.moveTo(-0.8f, -1.66f)
        bangs.cubicTo(-0.84f, -2.32f, -0.42f, -2.62f, 0f, -2.62f)
        bangs.cubicTo(0.42f, -2.62f, 0.84f, -2.32f, 0.8f, -1.66f)
        bangs.quadTo(0.66f, -1.98f, 0.46f, -1.94f)
        bangs.quadTo(0.34f, -1.84f, 0.22f, -1.98f)
        bangs.quadTo(0f, -1.86f, -0.18f, -2f)
        bangs.quadTo(-0.36f, -1.86f, -0.48f, -1.95f)
        bangs.quadTo(-0.68f, -1.96f, -0.8f, -1.66f)
        bangs.close()
        // A little tiara: three points, the middle one tallest.
        tiara.moveTo(-0.36f, -2.47f)
        tiara.lineTo(-0.33f, -2.74f)
        tiara.lineTo(-0.17f, -2.6f)
        tiara.lineTo(0f, -2.88f)
        tiara.lineTo(0.17f, -2.6f)
        tiara.lineTo(0.33f, -2.74f)
        tiara.lineTo(0.36f, -2.47f)
        tiara.quadTo(0f, -2.55f, -0.36f, -2.47f)
        tiara.close()
        // Her body, in at the waist.
        torso.moveTo(-0.28f, -1.01f)
        torso.lineTo(0.28f, -1.01f)
        torso.quadTo(0.47f, -1.01f, 0.47f, -0.8f)
        torso.quadTo(0.35f, -0.45f, 0.39f, -0.12f)
        torso.quadTo(0.43f, 0.22f, 0.18f, 0.23f)
        torso.lineTo(-0.18f, 0.23f)
        torso.quadTo(-0.43f, 0.22f, -0.39f, -0.12f)
        torso.quadTo(-0.35f, -0.45f, -0.47f, -0.8f)
        torso.quadTo(-0.47f, -1.01f, -0.28f, -1.01f)
        torso.close()
        // Locks of hair down each side of her face.
        for (side in SIDES) {
            locks.moveTo(side * 0.62f, -2.2f)
            locks.quadTo(side * 0.92f, -1.7f, side * 0.74f, -1.12f)
            locks.quadTo(side * 0.66f, -1.5f, side * 0.5f, -1.86f)
            locks.close()
        }
    }

    override val stillAfterS: Float get() = 0.95f

    /** One of her units: her head is a little under one across. */
    private fun unit(scene: BumpScene) = scene.unit * 0.86f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        power = scene.power * if (atLimit) 0.8f else 1f
        idle = 0f
        when (state) {
            State.HIDDEN -> if (visits++ % 2 == 0) enter(scene) else drop(scene)
            State.CLING -> shoot(scene)
            State.HANG -> flip(scene)
            State.ZIP -> Unit
            else -> pending = min(pending + 1, 2)
        }
    }

    /** The edge beside the buttons, in window pixels: −1 the left, +1 the right. */
    private fun buttonSide(scene: BumpScene): Float {
        if (!scene.fromWindow.invert(toWindow)) return 1f
        point[0] = 0f
        point[1] = scene.centre
        toWindow.mapPoints(point)
        restY = point[1]
        return if (point[0] < scene.windowWidth / 2f) -1f else 1f
    }

    /** Out of the edge beside the buttons, onto the wall there, a new suit each time. */
    private fun enter(scene: BumpScene) {
        val u = unit(scene)
        wall = buttonSide(scene)
        suit = random.nextInt(SUITS.size)
        val w = scene.windowWidth
        val edge = if (wall > 0f) w else 0f
        toX = edge - wall * CLING_REACH * u
        fromX = edge + wall * 2.5f * u
        restY = restY.clampIn(u * 4f, scene.windowHeight - u * 2.5f)
        x = fromX
        y = restY
        pose = Pose.CLING
        mirror = wall
        tilt = 0f
        swings = 0
        maxSwings = 2 + random.nextInt(3)
        state = State.ENTER
        age = 0f
    }

    /** Down from the camera hole, or the top of the screen, on a line: to hang upside down. */
    private fun drop(scene: BumpScene) {
        val u = unit(scene)
        buttonSide(scene)
        suit = random.nextInt(SUITS.size)
        anchor(scene, preferHole = true)
        val h = scene.windowHeight
        restY = (pivotY + h * 0.26f).clampIn(pivotY + u * 4f, h - u * 5f)
        x = pivotX
        y = pivotY + u * 0.3f
        vy = 0f
        pose = Pose.HANG
        mirror = 1f
        tilt = PI.toFloat()
        swings = 0
        maxSwings = 2 + random.nextInt(2)
        shot = 1f
        lineFade = 1f
        bowX = 0f
        bowY = 0f
        state = State.DROP
        age = 0f
    }

    /** Where the next line catches: the camera hole, when it is near the top, or a place along the top. */
    private fun anchor(scene: BumpScene, preferHole: Boolean) {
        val w = scene.windowWidth
        pivotX = Float.NaN
        if (preferHole && !scene.cutoutX.isNaN() && scene.fromWindow.invert(toWindow)) {
            point[0] = scene.cutoutX
            point[1] = scene.cutoutY
            toWindow.mapPoints(point)
            if (point[1] < scene.windowHeight * 0.25f) {
                pivotX = point[0]
                pivotY = point[1]
            }
        }
        if (pivotX.isNaN()) {
            pivotX = w * (0.5f + rnd(-0.14f, 0.14f))
            pivotY = 0f
        }
    }

    /** From the wall: her free hand up, and a line of silk flying out to the camera hole or the top. */
    private fun shoot(scene: BumpScene) {
        val u = unit(scene)
        anchor(scene, preferHole = true)
        // Never a line too short to swing on.
        if (y - pivotY < u * 7f) pivotY = (y - u * 7f).coerceAtLeast(0f)
        shot = 0f
        lineFade = 1f
        state = State.SHOOT
        age = 0f
    }

    /** Hanging upside down, and pressed: over she flips onto her hands and off she swings. */
    private fun flip(scene: BumpScene) {
        val d = scene.density
        val g = GRAVITY * d
        pose = Pose.SWING
        val dir = if (x < scene.windowWidth / 2f) 1f else -1f
        mirror = -dir
        length = hypot(x - pivotX, y - pivotY).coerceAtLeast(unit(scene) * 3f)
        theta = atan2(x - pivotX, y - pivotY)
        omega = dir * (0.9f + 0.5f * power) * sqrt(g / length)
        state = State.SWING
        age = 0f
    }

    /** Off up a line, out of the top of the screen. */
    private fun zip(scene: BumpScene, fromHang: Boolean) {
        val u = unit(scene)
        if (!fromHang) {
            // The hand on the line where it is now, the line straight up from it.
            setPose(Pose.SWING)
            mirror = -wall
            hand(scene, u)
            pivotX = x
            pivotY = 0f
            shot = 0f
        } else {
            pose = Pose.SWING
            shot = 1f
        }
        lineFade = 1f
        state = State.ZIP
        age = 0f
    }

    /** Puts the hand on the line where the pose's own holding hand would be, keeping the body still. */
    private fun hand(scene: BumpScene, u: Float) {
        world(scene, HOLD_X, HOLD_Y, u)
        x = point[0]
        y = point[1]
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        time += dt
        age += dt
        val d = scene.density
        val u = unit(scene)
        val w = scene.windowWidth
        val h = scene.windowHeight
        val g = GRAVITY * d
        var targetTilt = 0f
        val lineHeld = state == State.SWING || state == State.HANG || state == State.DROP ||
            (state == State.ZIP && shot >= 1f) || (state == State.SHOOT)
        if (!lineHeld) lineFade = (lineFade - dt * 4f).coerceAtLeast(0f)
        when (state) {
            State.HIDDEN -> return false
            State.ENTER -> {
                x = fromX + (toX - fromX) * easeOut(age / 0.35f)
                if (age >= 0.35f) {
                    state = State.CLING
                    age = 0f
                    // Straight off across the screen, the first time.
                    shoot(scene)
                }
            }
            State.CLING -> {
                idle += dt
                x += (toX - x) * min(1f, 18f * dt)
                if (pending > 0 && age > 0.2f) {
                    pending--
                    shoot(scene)
                } else if (age > 0.5f && (swings >= maxSwings || idle > IDLE_S)) {
                    zip(scene, fromHang = false)
                }
            }
            State.SHOOT -> {
                x += (toX - x) * min(1f, 18f * dt)
                val dist = hypot(pivotX - x, pivotY - y)
                shot = (shot + dt * SHOOT_SPEED * d / dist.coerceAtLeast(1f)).coerceAtMost(1f)
                if (shot >= 1f) {
                    // Caught: the hand that threw it takes the line, and off she swings.
                    pose(scene, u)
                    x = lineX
                    y = lineY
                    pose = Pose.SWING
                    mirror = -wall
                    length = hypot(x - pivotX, y - pivotY).coerceAtLeast(u * 3f)
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
                val margin = u * 2.2f
                val nearEdge = x < margin || x > w - margin
                val turned = before != 0f && sign(before) != sign(omega)
                // Let go at the far edge, or at the top of the swing and leap the rest of the way.
                if ((nearEdge && age > 0.25f) || (turned && age > 0.2f)) {
                    vx = length * omega * cos(theta)
                    vy = -length * omega * sin(theta)
                    if (turned && !nearEdge) {
                        val toLeft = x < w / 2f
                        vx = (if (toLeft) -1f else 1f) * 560f * d
                        vy = -300f * d
                    }
                    // From the hand to her middle, which is what flies.
                    world(scene, 0f, COM_Y, u)
                    x = point[0]
                    y = point[1]
                    pose = Pose.FLY
                    state = State.FLY
                    age = 0f
                }
            }
            State.FLY -> {
                vy += g * dt
                x += vx * dt
                y += vy * dt
                val margin = u * 1.5f
                if (x < margin || x > w - margin) {
                    // Onto the wall, crouched, a hand and a foot on it.
                    wall = if (x < w / 2f) -1f else 1f
                    world(scene, 0f, 0f, u)
                    x = point[0]
                    y = point[1].clampIn(u * 4f, h - u * 2.5f)
                    toX = (if (wall > 0f) w else 0f) - wall * CLING_REACH * u
                    pose = Pose.CLING
                    mirror = wall
                    state = State.CLING
                    age = 0f
                    swings++
                } else if (y > h + u * 6f) {
                    state = State.HIDDEN
                }
            }
            State.DROP -> {
                // Down the line on a spring, and a little bounce at the end of it.
                vy += ((restY - y) * 90f - vy * 9f) * dt
                y += vy * dt
                x = pivotX
                if (age > 0.9f) {
                    state = State.HANG
                    age = 0f
                }
            }
            State.HANG -> {
                idle += dt
                y = restY + sin(time * 2.2f) * u * 0.12f
                x = pivotX + sin(time * 1.3f) * u * 0.18f
                if (pending > 0 && age > 0.25f) {
                    pending--
                    flip(scene)
                } else if (idle > HANG_IDLE_S) {
                    zip(scene, fromHang = true)
                }
            }
            State.ZIP -> {
                if (shot < 1f) {
                    shot = (shot + dt * 7f).coerceAtMost(1f)
                } else {
                    y -= (300f + 1100f * min(age, 1f)) * d * dt
                    x += (pivotX - x) * min(1f, 10f * dt)
                    if (y < -u * 6f) state = State.HIDDEN
                }
            }
        }
        if (state == State.HIDDEN) return false
        // How she should be turned: along the line she hangs from, along the way she flies, or upright.
        targetTilt = when (pose) {
            Pose.SWING -> lineTilt(HOLD_X, HOLD_Y)
            Pose.HANG -> lineTilt(0f, FEET_Y)
            Pose.FLY -> atan2(vx, -vy)
            Pose.CLING -> 0f
        }
        tilt += wrap(targetTilt - tilt) * min(1f, TILT_RATE * dt)
        stepBow(scene, dt, u)
        return true
    }

    /** The turn that hangs her from [ax], [ay] of herself straight down the line from the anchor. */
    private fun lineTilt(ax: Float, ay: Float): Float {
        val ux = pivotX - x
        val uy = pivotY - y
        val vxBody = (0f - ax) * mirror
        val vyBody = COM_Y - ay
        return atan2(-uy, -ux) - atan2(vyBody, vxBody)
    }

    /** The line's bow, sprung towards where it would hang now: thrown in a curve, swung in an arc. */
    private fun stepBow(scene: BumpScene, dt: Float, u: Float) {
        pose(scene, u)
        val sx = lineX
        val sy = lineY
        val ex = sx + (pivotX - sx) * shot
        val ey = sy + (pivotY - sy) * shot
        val len = hypot(ex - sx, ey - sy).coerceAtLeast(1f)
        // Across the line, on the side that is down.
        var nx = -(ey - sy) / len
        var ny = (ex - sx) / len
        if (ny < 0f) {
            nx = -nx
            ny = -ny
        }
        var tx = 0f
        var ty = 0f
        when (state) {
            State.SHOOT, State.ZIP -> {
                val k = len * (0.05f + 0.22f * (1f - shot))
                tx = nx * k
                ty = ny * k
            }
            State.SWING -> {
                // A little sag, and the middle lagging behind as she swings.
                val bx = length * omega * cos(theta)
                val by = -length * omega * sin(theta)
                tx = nx * len * 0.035f - bx * 0.028f
                ty = ny * len * 0.035f - by * 0.028f
                val m = hypot(tx, ty)
                val cap = len * 0.12f
                if (m > cap) {
                    tx *= cap / m
                    ty *= cap / m
                }
            }
            State.HANG, State.DROP -> {
                tx = sin(time * 2.1f + 0.6f) * len * 0.03f
                ty = 0f
            }
            else -> Unit
        }
        bowVX += ((tx - bowX) * 260f - bowVX * 16f) * dt
        bowVY += ((ty - bowY) * 260f - bowVY * 16f) * dt
        bowX += bowVX * dt
        bowY += bowVY * dt
    }

    /** Where the line leaves her, in window pixels, this frame. */
    private var lineX = 0f
    private var lineY = 0f

    /**
     * Her limbs for this frame, and where the line leaves her: the hand that holds it, the hand that
     * threw it, or her feet.
     */
    private fun pose(scene: BumpScene, u: Float) {
        setPose(pose)
        if (state == State.SHOOT) aim(scene, u)
        when (state) {
            State.SHOOT -> world(scene, j[LA + 4], j[LA + 5], u)
            State.HANG, State.DROP -> world(scene, 0f, FEET_Y, u)
            else -> world(scene, HOLD_X, HOLD_Y, u)
        }
        lineX = point[0]
        lineY = point[1]
    }

    /** [bx], [by] of her body, in her units, to window pixels as she stands now: into [point]. */
    private fun world(scene: BumpScene, bx: Float, by: Float, u: Float) {
        val (ax, ay) = attach()
        val lx = (bx - ax) * mirror * u
        val ly = (by - ay) * u
        val c = cos(tilt)
        val s = sin(tilt)
        point[0] = x + lx * c - ly * s
        point[1] = y + lx * s + ly * c
    }

    /** The point of her that [x], [y] is, in her own units. */
    private fun attach(): Pair<Float, Float> = when (state) {
        State.SWING, State.ZIP -> HOLD_X to HOLD_Y
        State.HANG, State.DROP -> 0f to FEET_Y
        State.FLY -> 0f to COM_Y
        else -> 0f to 0f
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        if (state == State.HIDDEN || scene.windowWidth <= 0f) return
        val d = scene.density
        val u = unit(scene)
        pose(scene, u)
        canvas.save()
        canvas.concat(scene.fromWindow)
        val showLine = state == State.SHOOT || state == State.SWING || state == State.HANG ||
            state == State.DROP || state == State.ZIP || lineFade > 0f
        if (showLine) {
            val alpha = if (state == State.FLY || state == State.CLING) lineFade else 1f
            val ex = lineX + (pivotX - lineX) * shot
            val ey = lineY + (pivotY - lineY) * shot
            drawSilk(canvas, lineX, lineY, ex, ey, alpha, d)
            if (shot >= 1f && alpha > 0.05f) drawSplat(canvas, pivotX, pivotY, u, alpha)
        }
        // Her, turned and placed.
        val (ax, ay) = attach()
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(Math.toDegrees(tilt.toDouble()).toFloat())
        canvas.scale(mirror * u, u)
        canvas.translate(-ax, -ay)
        drawQueen(canvas, scene)
        canvas.restore()
        canvas.restore()
    }

    /** A line of silk from [sx], [sy] to [ex], [ey], bowed by the sprung offset at its middle. */
    private fun drawSilk(canvas: Canvas, sx: Float, sy: Float, ex: Float, ey: Float, alpha: Float, d: Float) {
        silk.reset()
        silk.moveTo(sx, sy)
        // A quadratic's middle sits halfway to its control point: twice the bow there puts it at the bow.
        silk.quadTo((sx + ex) / 2f + bowX * 2f, (sy + ey) / 2f + bowY * 2f, ex, ey)
        ink.strokeWidth = 3.8f * d
        ink.color = fade(0x30FFFFFF, alpha)
        canvas.drawPath(silk, ink)
        ink.strokeWidth = 1.4f * d
        ink.color = fade(0xF0FFFFFF.toInt(), alpha)
        canvas.drawPath(silk, ink)
    }

    /** Where a line has caught: a little splat of web. */
    private fun drawSplat(canvas: Canvas, px: Float, py: Float, u: Float, alpha: Float) {
        ink.strokeWidth = u * 0.07f
        ink.color = fade(0xE6FFFFFF.toInt(), alpha)
        for (k in 0 until 6) {
            val a = k * PI.toFloat() / 3f + 0.3f
            canvas.drawLine(px, py, px + cos(a) * u * 0.42f, py + sin(a) * u * 0.42f, ink)
        }
        canvas.drawCircle(px, py, u * 0.2f, ink)
        fill.color = fade(0xFFFFFFFF.toInt(), alpha)
        canvas.drawCircle(px, py, u * 0.09f, fill)
    }

    /** Her free arm straight out at the line's anchor, throwing. */
    private fun aim(scene: BumpScene, u: Float) {
        world(scene, j[LA], j[LA + 1], u)
        val dx = pivotX - point[0]
        val dy = pivotY - point[1]
        val l = hypot(dx, dy).coerceAtLeast(0.001f)
        // Into her own frame: turned back, and mirrored back.
        val c = cos(-tilt)
        val s = sin(-tilt)
        var bx = (dx * c - dy * s) / l * mirror
        val by = (dx * s + dy * c) / l
        if (bx.isNaN()) bx = 0f
        j[LA + 2] = j[LA] + bx * 0.62f - by * 0.06f
        j[LA + 3] = j[LA + 1] + by * 0.62f + bx * 0.06f
        j[LA + 4] = j[LA] + bx * 1.28f
        j[LA + 5] = j[LA + 1] + by * 1.28f
    }

    /** Her limbs for a pose, into [j]: shoulder, elbow and hand, or hip, knee and foot, in her units. */
    private fun setPose(p: Pose) {
        when (p) {
            Pose.CLING -> {
                // Crouched against the wall at +x: a hand on it, the other waving, knees out.
                val wave = sin(time * 10f) * 0.14f * ((age - 0.25f) * 3f).coerceIn(0f, 1f)
                limb(RA, 0.38f, -0.84f, 0.86f, -0.62f, 1.2f, -0.8f)
                limb(LA, -0.38f, -0.84f, -0.98f, -1.14f, -1.26f + wave, -1.7f - wave * 0.4f)
                limb(RL, 0.2f, 0.05f, 0.74f, 0.24f, 1.04f, 0.84f)
                limb(LL, -0.2f, 0.05f, -0.62f, 0.42f, -0.42f, 1.02f)
                headTilt = -8f
            }
            Pose.SWING -> {
                // Hanging from one hand, the other out for balance, legs swinging with her.
                val kick = (omega * 0.07f).coerceIn(-0.22f, 0.22f)
                limb(RA, 0.4f, -0.85f, 0.86f, -1.46f, HOLD_X, HOLD_Y)
                limb(LA, -0.4f, -0.85f, -0.86f, -0.78f, -1.22f, -0.5f)
                limb(RL, 0.2f, 0.05f, 0.3f + kick, 0.6f, 0.14f + kick * 2f, 1.16f)
                limb(LL, -0.2f, 0.05f, -0.04f + kick, 0.62f, -0.24f + kick * 2f, 1.13f)
                headTilt = -10f
            }
            Pose.HANG -> {
                // Upside down by her feet: legs up the line, arms down past her head, one waving.
                val wave = sin(time * 8f) * 0.16f
                limb(RL, 0.2f, 0.05f, 0.11f, 0.66f, 0.05f, FEET_Y - 0.04f)
                limb(LL, -0.2f, 0.05f, -0.11f, 0.66f, -0.05f, FEET_Y - 0.04f)
                limb(RA, 0.4f, -0.85f, 0.74f, -1.28f, 0.94f, -1.8f)
                limb(LA, -0.4f, -0.85f, -0.84f, -1.12f, -1.24f + wave, -1.52f + wave)
                headTilt = 0f
            }
            Pose.FLY -> {
                // Leaping: arms up and out, legs together behind.
                limb(RA, 0.4f, -0.85f, 0.82f, -1.26f, 1.16f, -1.76f)
                limb(LA, -0.4f, -0.85f, -0.82f, -1.26f, -1.16f, -1.76f)
                limb(RL, 0.2f, 0.05f, 0.18f, 0.63f, 0.14f, 1.2f)
                limb(LL, -0.2f, 0.05f, -0.18f, 0.63f, -0.14f, 1.2f)
                headTilt = 0f
            }
        }
    }

    private fun limb(at: Int, sx: Float, sy: Float, ex: Float, ey: Float, hx: Float, hy: Float) {
        j[at] = sx
        j[at + 1] = sy
        j[at + 2] = ex
        j[at + 3] = ey
        j[at + 4] = hx
        j[at + 5] = hy
    }

    /** All of her, in her own units: hair, legs, body, arms, then her head with its face, mask and tiara. */
    private fun drawQueen(canvas: Canvas, scene: BumpScene) {
        val c = SUITS[suit]
        drawPonytail(canvas, scene)
        // Legs: the suit to the knee, boots below with a gold cuff.
        for (at in intArrayOf(LL, RL)) {
            ink.strokeWidth = 0.31f
            ink.color = c[1]
            canvas.drawLine(j[at], j[at + 1], j[at + 2], j[at + 3], ink)
            ink.color = c[2]
            canvas.drawLine(j[at + 2], j[at + 3], j[at + 4], j[at + 5], ink)
            band.strokeWidth = 0.34f
            band.color = GOLD
            segment(canvas, at + 2, 0.1f, 0.2f)
            fill.color = c[2]
            canvas.drawCircle(j[at + 4], j[at + 5], 0.17f, fill)
        }
        // The body: the suit lit from the left, a gold belt and collar.
        torsoPaint.shader = suitShades[suit]
        canvas.drawPath(torso, torsoPaint)
        fill.color = GOLD
        canvas.drawRect(-0.4f, -0.14f, 0.4f, -0.02f, fill)
        fill.color = GOLD_DARK
        canvas.drawCircle(0f, -0.075f, 0.075f, fill)
        ink.strokeWidth = 0.07f
        ink.color = GOLD
        canvas.drawLine(-0.24f, -0.96f, 0f, -0.74f, ink)
        canvas.drawLine(0f, -0.74f, 0.24f, -0.96f, ink)
        // Arms: the suit, then gloves, and the gold bracelets the silk comes from.
        for (at in intArrayOf(LA, RA)) {
            ink.strokeWidth = 0.25f
            ink.color = c[1]
            canvas.drawLine(j[at], j[at + 1], j[at + 2], j[at + 3], ink)
            ink.color = c[2]
            canvas.drawLine(j[at + 2], j[at + 3], j[at + 4], j[at + 5], ink)
            band.strokeWidth = 0.29f
            band.color = GOLD
            segment(canvas, at + 2, 0.66f, 0.8f)
            fill.color = c[2]
            canvas.drawCircle(j[at + 4], j[at + 5], 0.14f, fill)
        }
        // Her head, tipped a little at the neck.
        canvas.save()
        canvas.rotate(headTilt, 0f, -0.95f)
        canvas.save()
        canvas.translate(0f, -1.76f)
        canvas.scale(0.9f, 0.88f)
        canvas.drawCircle(0f, 0f, 1f, hairShade)
        canvas.restore()
        fill.color = SKIN
        canvas.drawOval(-0.74f, -2.32f, 0.74f, -0.94f, fill)
        canvas.drawPath(locks, hairShade)
        canvas.drawPath(bangs, hairShade)
        ink.strokeWidth = 0.06f
        ink.color = HAIR_LIT
        canvas.drawArc(-0.6f, -2.5f, 0.2f, -1.9f, 200f, 60f, false, ink)
        // The mask across her eyes, winged at the sides.
        fill.color = c[2]
        canvas.drawRoundRect(-0.68f, -1.88f, 0.68f, -1.5f, 0.19f, 0.19f, fill)
        tail.reset()
        tail.moveTo(-0.6f, -1.86f)
        tail.lineTo(-0.86f, -1.98f)
        tail.lineTo(-0.66f, -1.6f)
        tail.close()
        tail.moveTo(0.6f, -1.86f)
        tail.lineTo(0.86f, -1.98f)
        tail.lineTo(0.66f, -1.6f)
        tail.close()
        canvas.drawPath(tail, fill)
        // Big, bright eyes, blinking now and then.
        val blinking = time % BLINK_S > BLINK_S - 0.12f
        for (side in SIDES) {
            val ex = side * 0.3f
            val ey = -1.69f
            if (blinking) {
                ink.strokeWidth = 0.06f
                ink.color = EYE
                canvas.drawArc(ex - 0.16f, ey - 0.08f, ex + 0.16f, ey + 0.1f, 20f, 140f, false, ink)
                continue
            }
            fill.color = Color.WHITE
            canvas.drawOval(ex - 0.17f, ey - 0.2f, ex + 0.17f, ey + 0.19f, fill)
            fill.color = EYE
            canvas.drawCircle(ex + 0.015f, ey + 0.02f, 0.135f, fill)
            fill.color = Color.WHITE
            canvas.drawCircle(ex - 0.045f, ey - 0.06f, 0.05f, fill)
            canvas.drawCircle(ex + 0.06f, ey + 0.07f, 0.024f, fill)
        }
        fill.color = BLUSH
        canvas.drawOval(-0.6f, -1.47f, -0.36f, -1.35f, fill)
        canvas.drawOval(0.36f, -1.47f, 0.6f, -1.35f, fill)
        ink.strokeWidth = 0.055f
        ink.color = MOUTH
        if (pose == Pose.HANG) {
            canvas.drawArc(-0.1f, -1.42f, 0.1f, -1.28f, 200f, 140f, false, ink)
        } else {
            canvas.drawArc(-0.1f, -1.44f, 0.1f, -1.3f, 20f, 140f, false, ink)
        }
        // The tiara, and its jewel.
        fill.color = GOLD
        canvas.drawPath(tiara, fill)
        fill.color = GEM
        canvas.drawCircle(0f, -2.62f, 0.06f, fill)
        canvas.restore()
    }

    /** A stretch of the limb from [at] (its middle joint) to its end, from [a] to [b] of the way: a cuff. */
    private fun segment(canvas: Canvas, at: Int, a: Float, b: Float) {
        val mx = j[at]
        val my = j[at + 1]
        val ex = j[at + 2]
        val ey = j[at + 3]
        canvas.drawLine(mx + (ex - mx) * a, my + (ey - my) * a, mx + (ex - mx) * b, my + (ey - my) * b, band)
    }

    /** Her ponytail, from the back of her head, streaming the way the air and the fall take it. */
    private fun drawPonytail(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        // Down, and back against the way she is moving, in window terms.
        var wx = 0f
        var wy = 600f * d
        when (state) {
            State.SWING -> {
                wx -= length * omega * cos(theta) * 0.9f
                wy -= -length * omega * sin(theta) * 0.9f
            }
            State.FLY -> {
                wx -= vx * 0.9f
                wy -= vy * 0.9f
            }
            State.ZIP -> wy += 900f * d
            State.DROP -> wy -= vy * 0.9f
            else -> Unit
        }
        val l = hypot(wx, wy).coerceAtLeast(0.001f)
        // Into her own frame.
        val c = cos(-tilt)
        val s = sin(-tilt)
        val bx = (wx * c - wy * s) / l * mirror
        val by = (wx * s + wy * c) / l
        val rootX = 0.74f
        val rootY = -2.06f
        val tipX = rootX + bx * 1.25f
        val tipY = rootY + by * 1.25f
        val sway = sin(time * 5f) * 0.1f
        tail.reset()
        tail.moveTo(rootX - 0.2f, rootY)
        tail.quadTo((rootX + tipX) / 2f - by * (0.32f + sway), (rootY + tipY) / 2f + bx * (0.32f + sway), tipX, tipY)
        tail.quadTo((rootX + tipX) / 2f + by * 0.05f, (rootY + tipY) / 2f - bx * 0.05f, rootX + 0.2f, rootY)
        tail.close()
        canvas.drawPath(tail, hairShade)
        fill.color = GOLD
        canvas.drawCircle(rootX, rootY, 0.1f, fill)
    }

    /** An angle brought into −π..π, so a turn goes the short way round. */
    private fun wrap(a: Float): Float {
        var v = a
        val tau = 2f * PI.toFloat()
        while (v > PI) v -= tau
        while (v < -PI) v += tau
        return v
    }

    override fun clear() {
        state = State.HIDDEN
        pending = 0
        lineFade = 0f
    }

    private companion object {
        /** How hard she falls, in dp a second each second: snappier than real, for a lively swing. */
        const val GRAVITY = 2200f
        const val SHOOT_SPEED = 2600f
        const val IDLE_S = 2.8f
        const val HANG_IDLE_S = 3.4f
        const val TILT_RATE = 14f
        const val BLINK_S = 3.1f

        /** How far her hips sit from the wall she clings to, in her units: her hand and foot on it. */
        const val CLING_REACH = 1.36f

        /** Her hand on the line when she swings, her feet when she hangs, and her middle, in her units. */
        const val HOLD_X = 0.8f
        const val HOLD_Y = -2.3f
        const val FEET_Y = 1.27f
        const val COM_Y = -0.71f

        /** Where each limb's three points are kept in [j]: right arm, left arm, right leg, left leg. */
        const val RA = 0
        const val LA = 6
        const val RL = 12
        const val LL = 18

        val SIDES = floatArrayOf(-1f, 1f)

        /** Her suits: light, middle and dark. The trim is always gold. */
        val SUITS = arrayOf(
            intArrayOf(0xFFB39DDB.toInt(), 0xFF7E57C2.toInt(), 0xFF4527A0.toInt()),
            intArrayOf(0xFF80DEEA.toInt(), 0xFF00ACC1.toInt(), 0xFF006978.toInt()),
            intArrayOf(0xFFF8BBD0.toInt(), 0xFFEC407A.toInt(), 0xFFAD1457.toInt()),
            intArrayOf(0xFFA5D6A7.toInt(), 0xFF43A047.toInt(), 0xFF1B5E20.toInt()),
            intArrayOf(0xFF7A7A86.toInt(), 0xFF34343E.toInt(), 0xFF141419.toInt()),
            intArrayOf(0xFFFFCC80.toInt(), 0xFFFB8C00.toInt(), 0xFFBF4A00.toInt()),
        )
        val GOLD = 0xFFFFC94A.toInt()
        val GOLD_DARK = 0xFFC9952B.toInt()
        val GEM = 0xFFFF5FA2.toInt()
        val SKIN = 0xFFFFE2CC.toInt()
        val HAIR = 0xFF4A2F27.toInt()
        val HAIR_LIT = 0xFF7A5446.toInt()
        val HAIR_DARK = 0xFF2E1C17.toInt()
        val EYE = 0xFF2B1B3A.toInt()
        val MOUTH = 0xFFB0485A.toInt()
        val BLUSH = 0x66FF8FA3
    }
}
