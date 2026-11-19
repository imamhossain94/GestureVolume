package com.newagedevs.gesturevolume.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import com.newagedevs.gesturevolume.data.local.ButtonBumpStore
import com.newagedevs.gesturevolume.ui.view.ButtonBumpView
import com.newagedevs.gesturevolume.utils.ButtonBump
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.nio.ByteBuffer
import java.time.Duration
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Renders the button animation, frame by frame, exactly as ButtonBumpView draws it on the glass:
 * the real view, on Robolectric's real Skia, on a clock that moves 1/fps a frame. Frames are PNG
 * with straight alpha (the overlay window is transparent), for the store videos to lay over a
 * screen of their own.
 *
 * Driven by a JSON script (-PbumpSpec=...): clips of key events, each played into its own folder
 * under -PbumpOut=..., with meta.json beside the frames (the sounds the app would play, and when).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h914dp-port-420dpi")
class BumpFrameRender {

    private class Event(val t: Double, val json: JSONObject)

    @Test
    fun render() {
        val specPath = System.getProperty("bump.spec").orEmpty()
        if (specPath.isEmpty()) return
        val outRoot = File(System.getProperty("bump.out")!!).apply { mkdirs() }
        val spec = JSONObject(File(specPath).readText())
        val clips = spec.getJSONArray("clips")
        for (i in 0 until clips.length()) {
            val clip = clips.getJSONObject(i)
            val started = System.currentTimeMillis()
            when (clip.optString("kind", "clip")) {
                "still" -> renderStill(clip, outRoot)
                "tiles" -> renderTiles(clip, outRoot)
                else -> renderClip(clip, spec, outRoot)
            }
            println("bump: ${clip.getString("name")} in ${(System.currentTimeMillis() - started) / 1000.0}s")
        }
    }

    private fun button(name: String): ButtonBumpStore.Button = when (name) {
        "down" -> ButtonBumpStore.Button.VOLUME_DOWN
        "power" -> ButtonBumpStore.Button.POWER
        else -> ButtonBumpStore.Button.VOLUME_UP
    }

    /** The settings a press of [b] is drawn with: the clip's look, at that button's place. */
    private fun settingsFor(base: ButtonBumpStore.Settings, b: ButtonBumpStore.Button, places: JSONObject?): ButtonBumpStore.Settings {
        val def = ButtonBumpStore.defaultPlace(b)
        val o = places?.optJSONObject(b.key.lowercase())
        return base.copy(
            left = o?.optBoolean("left", def.left) ?: def.left,
            position = (o?.optDouble("position", def.position.toDouble()) ?: def.position.toDouble()).toFloat(),
            lengthDp = (o?.optDouble("lengthDp", def.lengthDp.toDouble()) ?: def.lengthDp.toDouble()).toFloat(),
            showLevel = base.showLevel && b != ButtonBumpStore.Button.POWER,
            showPercent = base.showPercent && b != ButtonBumpStore.Button.POWER,
        )
    }

    private fun baseSettings(clip: JSONObject): ButtonBumpStore.Settings {
        val o = clip.optJSONObject("settings") ?: JSONObject()
        val d = ButtonBumpStore.Settings()
        return d.copy(
            style = clip.getString("style"),
            depthDp = o.optDouble("depthDp", d.depthDp.toDouble()).toFloat(),
            color = if (o.has("color")) java.lang.Long.decode(o.getString("color")).toInt() else d.color,
            accent = if (o.has("accent")) java.lang.Long.decode(o.getString("accent")).toInt() else d.accent,
            showLevel = o.optBoolean("showLevel", d.showLevel),
            showPercent = o.optBoolean("showPercent", d.showPercent),
            holdMs = o.optInt("holdMs", d.holdMs),
            speed = o.optDouble("speed", d.speed.toDouble()).toFloat(),
            sweep = o.optDouble("sweep", d.sweep.toDouble()).toFloat(),
        )
    }

    private fun placementFor(s: ButtonBumpStore.Settings, width: Int, height: Int, density: Float): ButtonBump.Placement {
        val lengthPx = s.lengthDp * density
        val depthPx = s.depthDp * density
        return ButtonBump.placement(
            left = s.left,
            along = s.position,
            turns = 0,
            screenWidth = width,
            screenHeight = height,
            span = ButtonBump.windowSpan(s.style, lengthPx, density, depthPx),
            reach = ButtonBump.windowReach(s.style, depthPx, density, s.showPercent),
        )
    }

    private fun renderClip(clip: JSONObject, spec: JSONObject, outRoot: File) {
        val context = RuntimeEnvironment.getApplication()
        val density = context.resources.displayMetrics.density
        val width = clip.optInt("width", spec.optInt("width", 1080))
        val height = clip.optInt("height", spec.optInt("height", 2400))
        val fps = clip.optInt("fps", spec.optInt("fps", 60))
        val name = clip.getString("name")
        val dir = File(outRoot, name).apply { deleteRecursively(); mkdirs() }
        val base = baseSettings(clip)
        val places = clip.optJSONObject("places")

        val view = ButtonBumpView(context)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        view.setScreenSize(width, height)
        val first = settingsFor(base, ButtonBumpStore.Button.VOLUME_UP, places)
        val firstPlacement = placementFor(first, width, height, density)
        view.configure(first, firstPlacement)
        view.setOrigin(firstPlacement.x, firstPlacement.y)

        var nowT = 0.0
        val cues = JSONArray()
        view.cueListener = { cue -> cues.put(JSONObject().put("t", nowT).put("cue", cue)) }

        val list = clip.getJSONArray("events")
        val events = (0 until list.length()).map { list.getJSONObject(it) }.map { Event(it.getDouble("t"), it) }.sortedBy { it.t }
        val duration = clip.getDouble("duration")
        val frames = ceil(duration * fps).toInt()

        val startMs = SystemClock.uptimeMillis()
        fun advanceTo(t: Double) {
            val target = startMs + (t * 1000.0).roundToLong()
            val now = SystemClock.uptimeMillis()
            if (target > now) ShadowSystemClock.advanceBy(Duration.ofMillis(target - now))
        }

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val pixels = IntArray(width * height)
        val buf = ByteBuffer.allocate(width * height * 4)
        val ffmpeg = System.getProperty("bump.ffmpeg") ?: "ffmpeg"
        val ff = ProcessBuilder(
            ffmpeg, "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "argb",
            "-s", "${width}x$height", "-framerate", "$fps", "-i", "-",
            "-threads", "8", "-compression_level", "3", "-start_number", "0", File(dir, "%05d.png").path,
        ).redirectErrorStream(true).redirectOutput(File(dir, "ffmpeg.log")).start()
        val out = ff.outputStream.buffered(1 shl 22)

        val presses = JSONArray()
        var ei = 0
        for (f in 0 until frames) {
            val t = f.toDouble() / fps
            while (ei < events.size && events[ei].t <= t + 1e-9) {
                val e = events[ei].json
                advanceTo(events[ei].t)
                nowT = events[ei].t
                when (e.getString("type")) {
                    "down" -> view.setKeyDown(true)
                    "up" -> view.setKeyDown(false)
                    "press" -> {
                        val b = button(e.optString("button", "up"))
                        // A press may carry a place of its own: the settings screen replays the press
                        // on the glass as the Position slider moves.
                        val s = settingsFor(base, b, places).let {
                            if (e.has("position")) it.copy(position = e.getDouble("position").toFloat()) else it
                        }
                        val p = placementFor(s, width, height, density)
                        view.setScreenSize(width, height)
                        view.configure(s, p)
                        view.setOrigin(p.x, p.y)
                        val dirn = if (b == ButtonBumpStore.Button.VOLUME_DOWN) -1 else 1
                        view.press(dirn, e.getDouble("level").toFloat(), e.optBoolean("atLimit", false))
                        presses.put(JSONObject().put("t", nowT).put("button", e.optString("button", "up"))
                            .put("level", e.getDouble("level")).put("repeat", e.optBoolean("repeat", false))
                            .put("centre", p.centre))
                    }
                    "dismiss" -> view.dismiss()
                }
                ei++
            }
            advanceTo(t)
            nowT = t
            bmp.eraseColor(Color.TRANSPARENT)
            view.draw(canvas)
            bmp.getPixels(pixels, 0, width, 0, 0, width, height)
            buf.clear()
            buf.asIntBuffer().put(pixels)
            out.write(buf.array(), 0, width * height * 4)
        }
        out.close()
        val code = ff.waitFor()
        check(code == 0) { "ffmpeg exited $code for $name: " + File(dir, "ffmpeg.log").readText() }
        File(dir, "ffmpeg.log").delete()

        val meta = JSONObject()
            .put("name", name).put("style", base.style).put("fps", fps).put("frames", frames)
            .put("width", width).put("height", height).put("density", density.toDouble())
            .put("sound", ButtonBump.sound(base.style))
            .put("soundsWhileHeld", ButtonBump.soundsWhileHeld(base.style))
            .put("presses", presses).put("cues", cues)
        File(dir, "meta.json").writeText(meta.toString(1))
    }

    /**
     * Every style's gallery tile, as ButtonFeedbackScreen's StyleTile draws it: a stage 80 x 92 dp,
     * the buttons on its left, the bump 72 % of its height (water's 40 %), 13 dp deep, frozen part full.
     */
    private fun renderTiles(clip: JSONObject, outRoot: File) {
        val context = RuntimeEnvironment.getApplication()
        val density = context.resources.displayMetrics.density
        val wDp = clip.optDouble("widthDp", 80.0).toFloat()
        val hDp = clip.optDouble("heightDp", 92.0).toFloat()
        val w = Math.round(wDp * density)
        val h = Math.round(hDp * density)
        val dir = File(outRoot, clip.getString("name")).apply { mkdirs() }
        val color = if (clip.has("color")) java.lang.Long.decode(clip.getString("color")).toInt() else ButtonBumpStore.DEFAULT_COLOR
        val accent = if (clip.has("accent")) java.lang.Long.decode(clip.getString("accent")).toInt() else ButtonBumpStore.DEFAULT_ACCENT
        for (style in ButtonBump.STYLES) {
            val settings = ButtonBumpStore.Settings(
                style = style,
                left = true,
                position = 0.5f,
                lengthDp = hDp * if (style == ButtonBump.STYLE_WATER) 0.4f else 0.72f,
                depthDp = 13f,
                color = color,
                accent = accent,
            )
            val view = ButtonBumpView(context)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, w, h)
            val placement = ButtonBump.placement(true, 0.5f, 0, w, h, span = h, reach = w)
            view.configure(settings, placement)
            view.setStill()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.TRANSPARENT)
            view.draw(Canvas(bmp))
            File(dir, "$style.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        File(dir, "styles.json").writeText(JSONArray(ButtonBump.STYLES).toString())
    }

    /** One frozen frame, as a gallery tile shows a style: the bump out, part full, nothing moving. */
    private fun renderStill(clip: JSONObject, outRoot: File) {
        val context = RuntimeEnvironment.getApplication()
        val density = context.resources.displayMetrics.density
        val width = clip.getInt("width")
        val height = clip.getInt("height")
        val base = baseSettings(clip)
        val o = clip.optJSONObject("place") ?: JSONObject()
        val s = base.copy(
            left = o.optBoolean("left", true),
            position = o.optDouble("position", 0.5).toFloat(),
            lengthDp = o.optDouble("lengthDp", 52.0).toFloat(),
        )
        val view = ButtonBumpView(context)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        view.setScreenSize(width, height)
        val p = placementFor(s, width, height, density)
        view.configure(s, p)
        view.setOrigin(p.x, p.y)
        view.setStill(clip.optDouble("level", 0.62).toFloat())
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        bmp.eraseColor(Color.TRANSPARENT)
        view.draw(canvas)
        File(outRoot, clip.getString("name") + ".png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
