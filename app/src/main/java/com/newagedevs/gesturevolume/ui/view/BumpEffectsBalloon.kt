package com.newagedevs.gesturevolume.ui.view

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import java.util.Random
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Balloons for the button animation, blown up at the edge beside the buttons.
 */

/**
 * Balloons. A press blows one up out of the edge beside the buttons, and when the key is let go it
 * floats away, its string trailing; quick presses send up a bunch, each a different colour. Hold the
 * key and it keeps on filling, the gas hissing in, swelling and stretching paler as it goes: let go
 * and off it floats, as big as it got. Hold on long enough and it bursts with a bang, scraps of it
 * flying; if the key is still held, the next one starts to fill.
 *
 * Whether the key is held is [BumpScene.keyDown]; a press with no key down — the power button, the
 * preview, a Volume down that waited for its release — sends one straight off. The hiss and the bang
 * are asked for with [BumpScene.cue].
 */
internal class BalloonEffect(random: Random) : BumpEffect(random) {
    override val drawsBump: Boolean get() = false
    override val loops: Boolean get() = true

    private class Balloon {
        var alive = false

        /** Still on the edge, filling, while the key is held. */
        var held = false

        /** The middle of its body, in upright terms, and how it is moving. */
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f

        /** Its size against an ordinary balloon's, and the size it is filling out to once let go. */
        var size = 0f
        var grow = 1f

        /** An ordinary balloon's size, for the level it was blown up at. */
        var scale = 1f
        var colour = 0

        /** Degrees off upright, leaning with its drift. */
        var tilt = 0f

        /** Its own lift against an ordinary balloon's, and a breeze of its own across the screen. */
        var lift = 1f
        var breeze = 0f
        var age = 0f
        var heldFor = 0f
        var phase = 0f
    }

    private val balloons = Array(MAX) { Balloon() }
    private val scraps = ParticlePool(70)
    private var time = 0f
    private var nextColour = 0
    private var hissing = false

    /** After a burst with the key still held: how long until the next one starts filling, or −1. */
    private var refillIn = -1f
    private var bursts = 0
    private var flashAge = 10f
    private var flashX = 0f
    private var flashY = 0f
    private var flashR = 0f

    private val body = Path()
    private val knot = Path()
    private val string = Path()
    private val shades = Array(COLOURS.size) { i ->
        RadialGradient(-0.35f, -0.5f, 1.65f, intArrayOf(COLOURS[i][0], COLOURS[i][1], COLOURS[i][2]), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    private val skin = solidFill()
    private val fill = solidFill()
    private val ink = roundStroke()
    private val thread = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    init {
        // A balloon a unit across, its middle at the origin: round on top, narrowing to the knot.
        body.moveTo(0f, -1.18f)
        body.cubicTo(0.64f, -1.18f, 1f, -0.7f, 1f, -0.1f)
        body.cubicTo(1f, 0.52f, 0.44f, 1.02f, 0.07f, 1.13f)
        body.lineTo(-0.07f, 1.13f)
        body.cubicTo(-0.44f, 1.02f, -1f, 0.52f, -1f, -0.1f)
        body.cubicTo(-1f, -0.7f, -0.64f, -1.18f, 0f, -1.18f)
        body.close()
        knot.moveTo(-0.13f, 1.27f)
        knot.lineTo(0.13f, 1.27f)
        knot.lineTo(0f, 1.1f)
        knot.close()
    }

    override val stillAfterS: Float get() = 0.6f

    /** An ordinary balloon's radius, in pixels. */
    private fun radius(scene: BumpScene) = scene.unit * 1.05f

    override fun press(scene: BumpScene, atLimit: Boolean) {
        // The key's own repeats while it is held: the one on the edge is already filling.
        if (balloons.any { it.alive && it.held }) return
        val b = free() ?: return
        blowUp(scene, b)
        if (!scene.keyDown) letGo(scene, b)
    }

    /** A balloon not in use, or the one furthest on its way, to be used again. */
    private fun free(): Balloon? =
        balloons.firstOrNull { !it.alive } ?: balloons.filter { !it.held }.maxByOrNull { it.age }

    /** A new balloon on the edge at the bump, just starting to fill. */
    private fun blowUp(scene: BumpScene, b: Balloon) {
        b.alive = true
        b.held = true
        b.size = 0.05f
        b.grow = 1f
        b.scale = 0.8f + 0.22f * scene.power
        b.colour = nextColour++ % COLOURS.size
        b.tilt = 0f
        b.age = 0f
        b.heldFor = 0f
        b.phase = rnd(0f, 6.283f)
        b.lift = rnd(0.75f, 1.25f)
        b.breeze = rnd(-1f, 1f)
        b.vx = 0f
        b.vy = 0f
        placeHeld(scene, b)
    }

    /** Off it goes, as big as it got, and at least an ordinary size: out into the screen, a way of its own. */
    private fun letGo(scene: BumpScene, b: Balloon) {
        val d = scene.density
        b.held = false
        b.grow = maxOf(b.size, 1f)
        val (ax, ay) = axis(scene)
        val turn = rnd(-0.45f, 0.45f)
        val c = cos(turn)
        val sn = sin(turn)
        val push = rnd(130f, 280f) * d
        b.vx = (ax * c - ay * sn) * push
        b.vy = (ax * sn + ay * c) * push
        stopHiss(scene)
    }

    /** Out of the edge and a little up the screen: the way a balloon filling on the edge points. */
    private fun axis(scene: BumpScene): Pair<Float, Float> {
        val x = 1f + 0.8f * scene.upX
        val y = 0.8f * scene.upY
        val l = hypot(x, y).coerceAtLeast(0.001f)
        return x / l to y / l
    }

    /** A filling balloon's place: its knot on the edge at the bump, its body out along [axis]. */
    private fun placeHeld(scene: BumpScene, b: Balloon) {
        val r = radius(scene) * b.scale * b.size
        val (ax, ay) = axis(scene)
        val knotX = 2f * scene.density
        // All of it on the screen, however big it gets.
        b.x = maxOf(knotX + ax * r * 1.2f, r * 1.04f + knotX)
        b.y = scene.centre + ay * r * 1.2f
    }

    override fun step(scene: BumpScene, dt: Float): Boolean {
        time += dt
        val d = scene.density
        var alive = false
        val filling = balloons.firstOrNull { it.alive && it.held }
        if (filling != null) {
            alive = true
            if (!scene.keyDown) {
                letGo(scene, filling)
            } else {
                filling.heldFor += dt
                filling.age += dt
                val t = filling.heldFor
                // Quickly to an ordinary size, then on and on, swelling with the gas.
                filling.size = if (t < FILL_S) {
                    easeOut(t / FILL_S)
                } else {
                    val k = ((t - FILL_S) / SWELL_S).coerceIn(0f, 1f)
                    1f + (BURST_SIZE - 1f) * k * k * (3f - 2f * k)
                }
                if (!hissing && t > HISS_AFTER_S) {
                    hissing = true
                    scene.cue(CUE_HISS)
                }
                placeHeld(scene, filling)
                if (t >= FILL_S + SWELL_S) burst(scene, filling)
            }
        } else if (refillIn >= 0f) {
            alive = true
            if (!scene.keyDown) {
                refillIn = -1f
            } else {
                refillIn -= dt
                if (refillIn < 0f) free()?.let { blowUp(scene, it) }
            }
        }
        if (!scene.keyDown && filling == null) bursts = 0
        // The ones let go: buoyed up, swaying, leaning into their drift.
        val keep = exp(-DRAG * dt)
        val acrossX = -scene.upY
        val acrossY = scene.upX
        for (b in balloons) {
            if (!b.alive || b.held) continue
            b.age += dt
            b.size += (b.grow - b.size) * min(1f, 7f * dt)
            val lift = LIFT * d * b.lift * (0.85f + 0.2f * b.size)
            val sway = (sin(b.age * 1.7f + b.phase) + b.breeze * 0.6f) * SWAY * d
            b.vx = b.vx * keep + (scene.upX * lift + acrossX * sway) * dt
            b.vy = b.vy * keep + (scene.upY * lift + acrossY * sway) * dt
            b.x += b.vx * dt
            b.y += b.vy * dt
            val side = b.vx * acrossX + b.vy * acrossY
            b.tilt += ((side / (140f * d)) * 16f - b.tilt) * min(1f, 3f * dt)
            val r = radius(scene) * b.scale * b.size
            if (scene.offScreen(b.x, b.y, r * 4f) || b.age > 12f) b.alive = false else alive = true
        }
        // Scraps of a burst one, flung out and falling.
        val drag = exp(-3.2f * dt)
        for (p in scraps.items) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.life) {
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
        flashAge += dt
        if (flashAge < FLASH_S) alive = true
        return alive
    }

    /** Too much: it bursts, with a bang and a flash, into scraps of itself. */
    private fun burst(scene: BumpScene, b: Balloon) {
        val d = scene.density
        val r = radius(scene) * b.scale * b.size
        b.alive = false
        stopHiss(scene)
        scene.cue(CUE_BURST)
        flashAge = 0f
        flashX = b.x
        flashY = b.y
        flashR = r
        repeat(16) {
            val p = scraps.spawn() ?: return@repeat
            val a = rnd(0f, 6.283f)
            val from = rnd(0.3f, 0.9f) * r
            p.x = b.x + cos(a) * from
            p.y = b.y + sin(a) * from
            val speed = rnd(260f, 760f) * d
            p.vx = cos(a) * speed
            p.vy = sin(a) * speed
            p.size = r * rnd(0.16f, 0.3f)
            p.angle = rnd(0f, 360f)
            p.spin = rnd(-900f, 900f)
            p.life = rnd(0.7f, 1.1f)
            p.color = COLOURS[b.colour][if (it % 3 == 0) 2 else 1]
            p.kind = 0
        }
        // A few bright bits of confetti that were inside it.
        repeat(10) {
            val p = scraps.spawn() ?: return@repeat
            val a = rnd(0f, 6.283f)
            p.x = b.x
            p.y = b.y
            val speed = rnd(160f, 520f) * d
            p.vx = cos(a) * speed
            p.vy = sin(a) * speed
            p.size = rnd(1.6f, 2.6f) * d
            p.angle = rnd(0f, 360f)
            p.spin = rnd(-600f, 600f)
            p.life = rnd(0.8f, 1.2f)
            p.color = COLOURS[random.nextInt(COLOURS.size)][1]
            p.kind = 1
        }
        // Still held: another one, in a moment — but not for ever, if the key never comes up.
        bursts++
        refillIn = if (scene.keyDown && bursts < MAX_BURSTS) REFILL_S else -1f
    }

    private fun stopHiss(scene: BumpScene) {
        if (!hissing) return
        hissing = false
        scene.cue(CUE_HISS_STOP)
    }

    override fun drawFront(canvas: Canvas, scene: BumpScene) {
        val d = scene.density
        val upright = uprightDegrees(scene)
        // The ones floating away first, then the one on the edge over them.
        for (b in balloons) if (b.alive && !b.held) drawBalloon(canvas, scene, b, upright + b.tilt, d)
        for (b in balloons) {
            if (!b.alive || !b.held) continue
            val (ax, ay) = axis(scene)
            var turn = Math.toDegrees(atan2(ax, -ay).toDouble()).toFloat()
            // Straining, near the end: it trembles.
            if (b.size > 1.7f) turn += sin(time * 46f) * 2.4f * ((b.size - 1.7f) / (BURST_SIZE - 1.7f)).coerceIn(0f, 1f)
            drawGas(canvas, scene, b, ax, ay)
            drawBalloon(canvas, scene, b, turn, d)
        }
        for (p in scraps.items) {
            if (!p.alive) continue
            val a = (1f - p.age / p.life).coerceIn(0f, 1f)
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.angle)
            if (p.kind == 0) {
                // A curl of rubber.
                ink.strokeWidth = p.size * 0.5f
                ink.color = fade(p.color, a)
                canvas.drawArc(-p.size, -p.size * 0.55f, p.size, p.size * 0.55f, 200f, 140f, false, ink)
            } else {
                fill.color = fade(p.color, a)
                canvas.drawRect(-p.size, -p.size * 0.5f, p.size, p.size * 0.5f, fill)
            }
            canvas.restore()
        }
        // The bang: a quick white star and a ring, gone in a blink.
        if (flashAge < FLASH_S) {
            val k = flashAge / FLASH_S
            val a = 1f - k
            ink.strokeWidth = 2.4f * d
            ink.color = fade(0xFFFFFFFF.toInt(), 0.9f * a)
            val r = flashR * (0.9f + 1.1f * easeOut(k))
            for (i in 0 until 10) {
                val ang = i * PI.toFloat() / 5f + 0.2f
                canvas.drawLine(
                    flashX + cos(ang) * r * 0.7f, flashY + sin(ang) * r * 0.7f,
                    flashX + cos(ang) * r * 1.15f, flashY + sin(ang) * r * 1.15f, ink,
                )
            }
            ink.strokeWidth = 1.6f * d
            ink.color = fade(0xFFFFFFFF.toInt(), 0.5f * a)
            canvas.drawCircle(flashX, flashY, r * 0.85f, ink)
        }
    }

    /** One balloon, turned [turn] degrees: string, body, shine and knot. */
    private fun drawBalloon(canvas: Canvas, scene: BumpScene, b: Balloon, turn: Float, d: Float) {
        val r = radius(scene) * b.scale * b.size
        if (r < 0.5f) return
        canvas.save()
        canvas.translate(b.x, b.y)
        canvas.rotate(turn)
        // Its string, trailing below it and waving, once it is off the edge.
        if (!b.held) {
            val len = r * 2.4f
            string.reset()
            string.moveTo(0f, r * 1.25f)
            val wave = sin(time * 3.1f + b.phase) * r * 0.22f
            string.cubicTo(wave, r * 1.25f + len * 0.33f, -wave, r * 1.25f + len * 0.66f, wave * 0.6f, r * 1.25f + len)
            thread.strokeWidth = 1.1f * d
            thread.color = 0xCCFFFFFF.toInt()
            canvas.drawPath(string, thread)
        }
        canvas.scale(r, r)
        skin.shader = shades[b.colour]
        canvas.drawPath(body, skin)
        // Stretched thin past its ordinary size: paler, the light coming through.
        if (b.size > 1.25f) {
            fill.color = fade(0xFFFFFFFF.toInt(), 0.32f * ((b.size - 1.25f) / (BURST_SIZE - 1.25f)).coerceIn(0f, 1f))
            canvas.drawPath(body, fill)
        }
        // The shine: a soft streak up on its shoulder, and a dot of light.
        canvas.save()
        canvas.rotate(-32f, -0.42f, -0.5f)
        fill.color = 0x8CFFFFFF.toInt()
        canvas.drawOval(-0.54f, -0.86f, -0.3f, -0.18f, fill)
        canvas.restore()
        fill.color = 0xB3FFFFFF.toInt()
        canvas.drawCircle(-0.2f, -0.86f, 0.07f, fill)
        fill.color = COLOURS[b.colour][2]
        canvas.drawPath(knot, fill)
        canvas.restore()
    }

    /** The gas going in: a few streaks of air from the edge into the filling balloon. */
    private fun drawGas(canvas: Canvas, scene: BumpScene, b: Balloon, ax: Float, ay: Float) {
        val d = scene.density
        val r = radius(scene) * b.scale * b.size
        val sx = 2f * d
        val sy = scene.centre
        thread.strokeWidth = 1.4f * d
        for (k in 0 until 3) {
            val t = (time * 2.6f + k / 3f) % 1f
            val a = sin(PI.toFloat() * t) * 0.55f
            val along = (0.15f + 0.7f * t) * r
            val side = (k - 1) * 0.28f * r
            val x = sx + ax * along - ay * side
            val y = sy + ay * along + ax * side
            thread.color = fade(0xFFFFFFFF.toInt(), a)
            canvas.drawLine(x - ax * r * 0.18f, y - ay * r * 0.18f, x, y, thread)
        }
    }

    /** A frozen frame for a gallery tile: a little bunch of balloons, rising, three colours. */
    override fun prime(scene: BumpScene) {
        random.setSeed(29L)
        clear()
        val across = (scene.screenRight - maxOf(scene.screenLeft, 0f)).coerceAtLeast(1f)
        val along = (scene.screenBottom - scene.screenTop).coerceAtLeast(1f)
        for (i in 0 until 3) {
            val b = balloons[i]
            b.alive = true
            b.held = false
            b.size = STILL_SIZE[i]
            b.grow = b.size
            b.scale = 1f
            b.colour = STILL_COLOUR[i]
            b.tilt = STILL_TILT[i]
            b.age = 1f
            b.phase = i * 2.1f
            b.x = across * STILL_ACROSS[i]
            b.y = scene.centre + scene.upY * along * STILL_UP[i]
            b.x += scene.upX * along * STILL_UP[i]
        }
        time = 0.4f
    }

    override fun clear() {
        for (b in balloons) b.alive = false
        scraps.clear()
        hissing = false
        refillIn = -1f
        bursts = 0
        flashAge = 10f
    }

    private companion object {
        const val MAX = 8

        /** Filling: to an ordinary size, then on to bursting, in seconds; and the hiss, once it is a hold. */
        const val FILL_S = 0.28f
        const val SWELL_S = 3f
        const val HISS_AFTER_S = 0.4f

        /** How big it gets before it bursts, against an ordinary balloon. */
        const val BURST_SIZE = 2.3f
        const val REFILL_S = 0.45f
        const val MAX_BURSTS = 5
        const val FLASH_S = 0.16f

        /** How it floats: lift, in dp a second each second; the air's drag; the sideways sway. */
        const val LIFT = 230f
        const val DRAG = 1.5f
        const val SWAY = 55f

        const val CUE_HISS = "inflate"
        const val CUE_HISS_STOP = "inflate_stop"
        const val CUE_BURST = "burst"

        /** Each balloon's colours: light, middle and dark. */
        val COLOURS = arrayOf(
            intArrayOf(0xFFFF8A80.toInt(), 0xFFEF5350.toInt(), 0xFFB71C1C.toInt()),
            intArrayOf(0xFF90CAF9.toInt(), 0xFF42A5F5.toInt(), 0xFF1565C0.toInt()),
            intArrayOf(0xFFFFF59D.toInt(), 0xFFFFD54F.toInt(), 0xFFF57F17.toInt()),
            intArrayOf(0xFFA5D6A7.toInt(), 0xFF66BB6A.toInt(), 0xFF2E7D32.toInt()),
            intArrayOf(0xFFCE93D8.toInt(), 0xFFAB47BC.toInt(), 0xFF6A1B9A.toInt()),
            intArrayOf(0xFFFFCC80.toInt(), 0xFFFFA726.toInt(), 0xFFE65100.toInt()),
            intArrayOf(0xFFF48FB1.toInt(), 0xFFEC407A.toInt(), 0xFFAD1457.toInt()),
            intArrayOf(0xFF80DEEA.toInt(), 0xFF26C6DA.toInt(), 0xFF00838F.toInt()),
        )

        /** The gallery's bunch: sizes, colours, leans, and where each is, across and up the tile. */
        val STILL_SIZE = floatArrayOf(0.95f, 1.05f, 0.88f)
        val STILL_COLOUR = intArrayOf(0, 1, 2)
        val STILL_TILT = floatArrayOf(-9f, 6f, -2f)
        val STILL_ACROSS = floatArrayOf(0.3f, 0.66f, 0.5f)
        val STILL_UP = floatArrayOf(0.12f, 0.24f, -0.12f)
    }
}
