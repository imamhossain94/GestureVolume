package com.newagedevs.gesturevolume.ui.view

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.newagedevs.gesturevolume.utils.ShaderFill

/**
 * Draws the Shaders fill: one rectangle over the whole track, painted by the effect's program.
 * See [ShaderFill] and [ShaderSources].
 *
 * The program does all of it — the effect, the grain, and lighting it fully below the level and
 * faintly above — so a frame is one draw however busy the effect, and nothing is made per frame:
 * the program is compiled when the effect changes and only its numbers are set after that.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class ShaderArt {

    private val paint = Paint()
    private var shader: RuntimeShader? = null
    private var compiledFor: String? = null

    /** Effects this phone refused to compile, so a bad one is tried once rather than every frame. */
    private val refused = HashSet<String>()

    /**
     * Paints [style]'s effect over [r].
     *
     * @param fillTop where the level is, in the view's coordinates: below it the effect is lit.
     * @param time the effect's own clock, in seconds, already run at the style's speed.
     * @param palette the four colours, opaque, from [ShaderFill.paletteWith].
     * @param alpha how much of the whole shows, 0..1: the panel fading its contents in and out.
     * @return false when the effect cannot be drawn on this phone, having drawn nothing.
     */
    fun draw(
        canvas: Canvas,
        r: RectF,
        fillTop: Float,
        time: Float,
        style: ShaderFill.Style,
        palette: IntArray,
        alpha: Float,
    ): Boolean {
        if (r.width() <= 1f || r.height() <= 1f) return true
        val s = shaderFor(style.effect) ?: return false
        s.setFloatUniform("origin", r.left, r.top)
        s.setFloatUniform("size", r.width(), r.height())
        s.setFloatUniform("time", time)
        s.setFloatUniform("level", fillTop)
        s.setFloatUniform("rest", style.rest)
        s.setFloatUniform("scale", style.scale)
        s.setFloatUniform("detail", style.detail)
        s.setFloatUniform("bright", style.brightness)
        s.setFloatUniform("grain", style.grain)
        s.setFloatUniform("alpha", alpha.coerceIn(0f, 1f))
        for (i in ShaderSources.COLOR_UNIFORMS.indices) {
            s.setColorUniform(ShaderSources.COLOR_UNIFORMS[i], palette.getOrElse(i) { palette.last() })
        }
        // Set again after its numbers change: the paint hands the chip the shader as it was when set.
        paint.shader = s
        return try {
            canvas.drawRect(r, paint)
            true
        } catch (e: IllegalArgumentException) {
            // A software canvas — the view drawn into a plain bitmap — refuses a RuntimeShader
            // outright, before drawing anything. The panel's own window is drawn on the GPU; this
            // is for whatever else draws it.
            false
        }
    }

    private fun shaderFor(effect: String): RuntimeShader? {
        val id = ShaderFill.sanitize(effect)
        if (id == compiledFor) return shader
        if (id in refused) return null
        return try {
            RuntimeShader(ShaderSources.source(id)).also {
                shader = it
                compiledFor = id
            }
        } catch (e: IllegalArgumentException) {
            // A driver that reads AGSL more strictly than the ones it was tried on. A plain fill is
            // a better answer than a panel that throws on every frame.
            Log.w(TAG, "Shader effect $id did not compile; drawing a plain fill instead", e)
            refused += id
            null
        }
    }

    private companion object {
        const val TAG = "ShaderArt"
    }
}
