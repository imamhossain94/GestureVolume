package com.newagedevs.gesturevolume

import android.graphics.Bitmap
import android.graphics.Picture
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.newagedevs.gesturevolume.ui.view.ShaderArt
import com.newagedevs.gesturevolume.ui.view.ShaderSources
import com.newagedevs.gesturevolume.utils.ShaderFill
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every Shaders effect compiles on this phone, takes every uniform the renderer sets, and paints
 * something that is not black. The one place a program is really checked: AGSL is compiled by the
 * phone, and a JVM test can only read the source.
 */
@RunWith(AndroidJUnit4::class)
class ShaderSourcesCompileTest {

    @Test
    fun everyEffectCompilesAndTakesEveryUniform() {
        assumeTrue(Build.VERSION.SDK_INT >= ShaderFill.MIN_SDK)
        ShaderFill.ALL.forEach { id ->
            val shader = try {
                RuntimeShader(ShaderSources.source(id))
            } catch (e: IllegalArgumentException) {
                throw AssertionError("$id did not compile: ${e.message}", e)
            }
            ShaderSources.FLOAT_UNIFORMS.forEach { shader.setFloatUniform(it, 0.5f) }
            ShaderSources.FLOAT2_UNIFORMS.forEach { shader.setFloatUniform(it, 1f, 1f) }
            ShaderSources.COLOR_UNIFORMS.forEach { shader.setColorUniform(it, 0xFF808080.toInt()) }
        }
    }

    @Test
    fun aSoftwareCanvasIsDeclinedRatherThanThrown() {
        assumeTrue(Build.VERSION.SDK_INT >= ShaderFill.MIN_SDK)
        val bitmap = Bitmap.createBitmap(20, 80, Bitmap.Config.ARGB_8888)
        val drawn = ShaderArt().draw(
            android.graphics.Canvas(bitmap), RectF(0f, 0f, 20f, 80f), 40f, 1f,
            ShaderFill.Style(), ShaderFill.paletteWith(ShaderFill.LAVA_LAMP, null), 1f,
        )
        assertTrue("drew on a software canvas", !drawn)
    }

    @Test
    fun everyEffectPaintsTheLitPart() {
        assumeTrue(Build.VERSION.SDK_INT >= ShaderFill.MIN_SDK)
        ShaderFill.ALL.forEach { id ->
            // Recorded and played back on the GPU: a software canvas refuses RuntimeShader outright.
            val picture = Picture()
            val drawn = ShaderArt().draw(
                picture.beginRecording(60, 240),
                RectF(0f, 0f, 60f, 240f),
                fillTop = 80f,
                time = 12.3f,
                style = ShaderFill.Style(effect = id, rest = 0f),
                palette = ShaderFill.paletteWith(id, null),
                alpha = 1f,
            )
            picture.endRecording()
            assertTrue("$id refused", drawn)
            val bitmap = Bitmap.createBitmap(picture, 60, 240, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(60 * 160)
            bitmap.getPixels(pixels, 0, 60, 0, 80, 60, 160)
            assertTrue("$id painted nothing below the level", pixels.any { (it and 0xFFFFFF) != 0 })
            val above = IntArray(60 * 70)
            bitmap.getPixels(above, 0, 60, 0, 0, 60, 70)
            assertTrue("$id showed above the level with rest at 0", above.all { (it ushr 24) == 0 })
        }
    }
}
