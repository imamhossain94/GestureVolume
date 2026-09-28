package com.newagedevs.gesturevolume

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Picture
import android.graphics.RectF
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newagedevs.gesturevolume.ui.view.GlimmerArt
import com.newagedevs.gesturevolume.ui.view.QuickSliderView
import com.newagedevs.gesturevolume.utils.GlimmerFill
import com.newagedevs.gesturevolume.utils.SliderFill
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The Glimmer fill drawn by this phone's GPU: lit up to the level, dark above it, the handle in the
 * fill colour, and the number written through the handle in the track's.
 *
 * Recorded and played back on the GPU, the way the panel is drawn. The frames are left in the app's
 * cache as PNGs (glimmer_*.png), to be looked at beside the slider they are after.
 */
@RunWith(AndroidJUnit4::class)
class GlimmerRenderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val density = context.resources.displayMetrics.density

    @Test
    fun theFieldIsLitToTheLevelAndTheHandleSitsOnIt() {
        val w = (28 * density).toInt()
        val h = (220 * density).toInt()
        val value = 0.6f
        val frames = listOf(3f, 3.3f, 3.6f, 3.9f, 4.2f, 4.5f).map { time ->
            record(w, h) { canvas ->
                canvas.drawColor(TRACK)
                GlimmerArt(density).draw(
                    canvas, RectF(0f, 0f, w.toFloat(), h.toFloat()), value, time, GlimmerFill.Style(),
                    GlimmerFill.paletteWith(null), Color.WHITE, corner = 14 * density, grabbed = false,
                    keepOut = FloatArray(4) { Float.NaN }, alpha = 1f,
                )
            }
        }
        frames.forEachIndexed { i, frame -> save(frame, "glimmer_frame_$i.png") }
        val frame = frames.first()
        val handleLength = GlimmerFill.handleLength(w.toFloat(), h.toFloat(), density)
        val centre = h - GlimmerFill.handleCentre(value, h.toFloat(), handleLength)
        // The handle, in the fill colour.
        assertEquals(Color.WHITE, frame.getPixel(w / 2, centre.toInt()))
        // Below it, lavender: some pixels well above the track and bluer than they are red.
        val below = pixels(frame, (centre + handleLength).toInt(), (centre + handleLength * 3).toInt())
        assertTrue("nothing lit below the handle", below.any { lum(it) > lum(TRACK) + 40 && Color.blue(it) > Color.red(it) })
        // Above it, only the faint dots and the stops.
        val above = pixels(frame, (h * 0.05f).toInt(), (centre - handleLength).toInt())
        assertTrue("lit above the handle", above.none { lum(it) > lum(TRACK) + 110 })
    }

    @Test
    fun thePanelWritesItsNumberAndIconThroughTheHandle() {
        val w = (28 * density).toInt()
        val h = (220 * density).toInt()
        // The number sits 35dp from the top and the icon 35dp from the bottom. These put the handle
        // right over each of them, and between.
        val values = listOf(0.15f, 0.35f, 0.6f, 0.86f, 1f)
        val shots = values.map { value ->
            val view = QuickSliderView(context).apply {
                setColors(TRACK, Color.WHITE)
                setCornerRadiusDp(22f)
                setGlimmerStyle(GlimmerFill.Style())
                setFillStyle(SliderFill.GLIMMER)
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
            record(w, h) { view.draw(it) }
        }
        shots.forEachIndexed { i, shot -> save(shot, "glimmer_panel_$i.png") }
        val handleLength = GlimmerFill.handleLength(w.toFloat(), h.toFloat(), density)
        // Over the icon (15) and over the number (86), whatever is on the handle is dark: the ink
        // is the track's, so the glyph shows on the white rather than vanishing into it.
        listOf(0, 3).forEach { i ->
            val centre = h - GlimmerFill.handleCentre(values[i], h.toFloat(), handleLength)
            val band = pixels(shots[i], (centre - handleLength * 0.3f).toInt(), (centre + handleLength * 0.3f).toInt())
            val middle = band.filterIndexed { k, _ -> k % w in (w / 4) until (w * 3 / 4) }
            assertTrue("nothing written on the handle at ${values[i]}", middle.count { lum(it) < 110 } > 12)
            assertTrue("the handle is not white at ${values[i]}", middle.count { it == Color.WHITE } > middle.size / 3)
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

    private fun assertEquals(expected: Int, actual: Int) =
        assertTrue("expected %08X, was %08X".format(expected, actual), expected == actual)

    private fun save(b: Bitmap, name: String) {
        File(context.cacheDir, name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        /** The panel's default track. */
        const val TRACK = 0xFF1C1C20.toInt()
    }
}
