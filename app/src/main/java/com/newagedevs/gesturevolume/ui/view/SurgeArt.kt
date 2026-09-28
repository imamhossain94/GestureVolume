package com.newagedevs.gesturevolume.ui.view

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.newagedevs.gesturevolume.utils.SurgeFill

/**
 * Draws the Surge fill: one rectangle over the whole track, painted by the look's program. See
 * [SurgeFill] and [SurgeSources].
 *
 * As [ShaderArt] does for the Shaders fill: the program does all of it — the front, the light
 * behind and past it, what shows above the level — so a frame is one draw, and the program is
 * compiled when the look changes and only its numbers are set after that.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class SurgeArt {

    private val paint = Paint()
    private var shader: RuntimeShader? = null
    private var compiledFor: String? = null

    /** Looks this phone refused to compile, so a bad one is tried once rather than every frame. */
    private val refused = HashSet<String>()

    /**
     * Paints [style]'s look over [r].
     *
     * @param fillTop where the level is, in the view's coordinates: the front climbs to it.
     * @param time the look's own clock, in seconds, already run at the style's speed.
     * @param palette the four colours, opaque, from [SurgeFill.paletteWith].
     * @param alpha how much of the whole shows, 0..1: the panel fading its contents in and out.
     * @return false when the look cannot be drawn on this phone, having drawn nothing.
     */
    fun draw(
        canvas: Canvas,
        r: RectF,
        fillTop: Float,
        time: Float,
        style: SurgeFill.Style,
        palette: IntArray,
        alpha: Float,
    ): Boolean {
        if (r.width() <= 1f || r.height() <= 1f) return true
        val s = shaderFor(style.look) ?: return false
        s.setFloatUniform("origin", r.left, r.top)
        s.setFloatUniform("size", r.width(), r.height())
        s.setFloatUniform("time", time)
        s.setFloatUniform("level", fillTop)
        s.setFloatUniform("rest", style.rest)
        s.setFloatUniform("scale", style.size)
        s.setFloatUniform("edge", style.edge)
        s.setFloatUniform("glow", style.glow)
        s.setFloatUniform("trail", style.trail)
        s.setFloatUniform("alpha", alpha.coerceIn(0f, 1f))
        for (i in SurgeSources.COLOR_UNIFORMS.indices) {
            s.setColorUniform(SurgeSources.COLOR_UNIFORMS[i], palette.getOrElse(i) { palette.last() })
        }
        // Set again after its numbers change: the paint hands the chip the shader as it was when set.
        paint.shader = s
        return try {
            canvas.drawRect(r, paint)
            true
        } catch (e: IllegalArgumentException) {
            // A software canvas refuses a RuntimeShader outright: see ShaderArt.
            false
        }
    }

    private fun shaderFor(look: String): RuntimeShader? {
        val id = SurgeFill.sanitize(look)
        if (id == compiledFor) return shader
        if (id in refused) return null
        return try {
            RuntimeShader(SurgeSources.source(id)).also {
                shader = it
                compiledFor = id
            }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Surge look $id did not compile; drawing a plain fill instead", e)
            refused += id
            null
        }
    }

    private companion object {
        const val TAG = "SurgeArt"
    }
}
