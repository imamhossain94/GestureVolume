package com.newagedevs.gesturevolume

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Picture
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newagedevs.gesturevolume.ui.view.QuickSliderView
import com.newagedevs.gesturevolume.ui.view.SurgeArt
import com.newagedevs.gesturevolume.ui.view.SurgeSources
import com.newagedevs.gesturevolume.utils.SliderFill
import com.newagedevs.gesturevolume.utils.SurgeFill
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Every Surge look compiled and drawn by this phone's GPU: lit below the level, nothing above it
 * when nothing is asked to show there, and the panel itself, with its number and icon, at a few
 * levels and moments. The frames are left in the app's cache as PNGs (surge_*.png).
 */
@RunWith(AndroidJUnit4::class)
class SurgeRenderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val density = context.resources.displayMetrics.density

    @Test
    fun everyLookCompilesAndTakesEveryUniform() {
        assumeTrue(Build.VERSION.SDK_INT >= SurgeFill.MIN_SDK)
        SurgeFill.LOOKS.forEach { id ->
            val shader = try {
                RuntimeShader(SurgeSources.source(id))
            } catch (e: IllegalArgumentException) {
                throw AssertionError("$id did not compile: ${e.message}", e)
            }
            SurgeSources.FLOAT_UNIFORMS.forEach { shader.setFloatUniform(it, 0.5f) }
            SurgeSources.FLOAT2_UNIFORMS.forEach { shader.setFloatUniform(it, 1f, 1f) }
            SurgeSources.COLOR_UNIFORMS.forEach { shader.setColorUniform(it, 0xFF808080.toInt()) }
        }
    }

    @Test
    fun everyLookLightsBelowTheLevelOnly() {
        assumeTrue(Build.VERSION.SDK_INT >= SurgeFill.MIN_SDK)
        val w = 60
        val h = 240
        val level = 80f
        SurgeFill.LOOKS.forEach { id ->
            var drawn = false
            val frame = record(w, h) { canvas ->
                // A straight front, no glow spilling past it, nothing kept above it.
                drawn = SurgeArt().draw(
                    canvas, RectF(0f, 0f, w.toFloat(), h.toFloat()), level, 12.3f,
                    SurgeFill.Style(look = id, edge = 0f, glow = 0f, rest = 0f),
                    SurgeFill.paletteWith(id, null), 1f,
                )
            }
            assertTrue("$id refused", drawn)
            val below = pixels(frame, level.toInt() + 4, h)
            assertTrue("$id painted nothing below the level", below.any { (it ushr 24) > 0 && (it and 0xFFFFFF) != 0 })
            // Cells are lit whole, so the honeycomb's reach a little past the level: kept clear of.
            val above = pixels(frame, 0, (level - w * 0.35f).toInt())
            assertTrue("$id showed above the level", above.all { (it ushr 24) == 0 })
        }
    }

    @Test
    fun theLooksOnThePanel() {
        assumeTrue(Build.VERSION.SDK_INT >= SurgeFill.MIN_SDK)
        // Wider than the panel's default, so the looks can be judged; and at the default.
        listOf(56 to "wide", 28 to "narrow").forEach { (widthDp, name) ->
            val w = (widthDp * density).toInt()
            val h = (220 * density).toInt()
            SurgeFill.LOOKS.forEach { id ->
                listOf(0.35f, 0.7f).forEachIndexed { i, value ->
                    val view = QuickSliderView(context).apply {
                        setColors(TRACK, Color.WHITE)
                        setCornerRadiusDp(16f)
                        setSurgeStyle(SurgeFill.Style(look = id))
                        setFillStyle(SliderFill.SURGE)
                        setContentMargins(35f, 35f)
                        setIcon(R.drawable.ic_vol_increase)
                        setShowValue(true)
                        measure(
                            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
                        )
                        layout(0, 0, w, h)
                        setExpansion(1f)
                        setCommitted()
                        setValue(value)
                    }
                    val shot = record(w, h) { view.draw(it) }
                    save(shot, "surge_${name}_${id}_$i.png")
                    // Below the level, something well lit; the track above it, dark.
                    val levelY = h - (h * value).toInt()
                    val lit = pixels(shot, levelY + 4, levelY + (h * 0.12f).toInt())
                    assertTrue("$id at $value: nothing lit under the front", lit.any { lum(it) > 120 })
                }
            }
        }
    }

    @Test
    fun theLooksMove() {
        assumeTrue(Build.VERSION.SDK_INT >= SurgeFill.MIN_SDK)
        val w = (56 * density).toInt()
        val h = (220 * density).toInt()
        SurgeFill.LOOKS.forEach { id ->
            val frames = listOf(2f, 2.4f, 2.8f).map { t ->
                record(w, h) { canvas ->
                    canvas.drawColor(TRACK)
                    SurgeArt().draw(
                        canvas, RectF(0f, 0f, w.toFloat(), h.toFloat()), h * 0.4f, t,
                        SurgeFill.Style(look = id), SurgeFill.paletteWith(id, null), 1f,
                    )
                }
            }
            frames.forEachIndexed { i, f -> save(f, "surge_motion_${id}_$i.png") }
            assertTrue("$id did not move", !frames[0].sameAs(frames[2]))
        }
    }

    private fun record(w: Int, h: Int, draw: (android.graphics.Canvas) -> Unit): Bitmap {
        val picture = Picture()
        draw(picture.beginRecording(w, h))
        picture.endRecording()
        // Rendered by the GPU, then copied out so its pixels can be read.
        return Bitmap.createBitmap(picture, w, h, Bitmap.Config.HARDWARE).copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun pixels(b: Bitmap, top: Int, bottom: Int): IntArray {
        val t = top.coerceIn(0, b.height - 1)
        val rows = (bottom.coerceIn(t + 1, b.height) - t)
        val out = IntArray(b.width * rows)
        b.getPixels(out, 0, b.width, 0, t, b.width, rows)
        return out
    }

    private fun lum(c: Int): Int = (Color.red(c) * 2126 + Color.green(c) * 7152 + Color.blue(c) * 722) / 10000

    private fun save(b: Bitmap, name: String) {
        File(context.cacheDir, name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        /** The panel's default track. */
        const val TRACK = 0xFF1C1C20.toInt()
    }
}
