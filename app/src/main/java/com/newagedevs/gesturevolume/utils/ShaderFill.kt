package com.newagedevs.gesturevolume.utils

/**
 * The Shaders fill: the Quick panel's track painted by a small program on the phone's graphics
 * chip — molten rock, liquid chrome, a lava lamp — lit up to the level and faint above it.
 *
 * After the shader effects of MetalForge's gallery, each written anew here from what it is meant to
 * look like (the gallery keeps its own programs to itself), for a track that stands on its end:
 * narrow, and several times as tall as it is wide. The programs are `ShaderSources`.
 *
 * Android 13 and later: that is the first release that lets an app hand the system a shader of its
 * own (AGSL, through `RuntimeShader`). Before it the chip is not offered, and a panel that somehow
 * has this fill — a backup restored onto an older phone — is drawn as a plain one.
 *
 * What the user tunes is [Style]: the effect, and six things every effect answers to. Plain values
 * here, so they can be checked without a device.
 *
 * Effect identifiers are a persistence format, never renamed.
 */
object ShaderFill {

    /** Wax rising and falling in a lamp, blobs merging and parting as they pass. */
    const val LAVA_LAMP = "lavaLamp"

    /** Rippled metal reflecting a bright sky, a glint where it catches. */
    const val LIQUID_CHROME = "liquidChrome"

    /** A dark crust cracked over flowing lava, hottest along the veins. */
    const val MOLTEN = "molten"

    /** Ribbons of light swaying up a night sky, fine rays climbing through them. */
    const val AURORA = "aurora"

    /** Ink curling through water in slow tendrils. */
    const val INK_SMOKE = "inkSmoke"

    /** Warm plasma, folding over itself. */
    const val PLASMA = "plasma"

    /** Light through a rippling surface, netted on the floor below. */
    const val CAUSTICS = "caustics"

    /** The colours of a soap bubble, flowing as the film thins and thickens. */
    const val SOAP_FILM = "soapFilm"

    /** Four colours wandering, blending into each other wherever they meet. */
    const val MESH = "mesh"

    /** Clouds billowing across a deepening sky. */
    const val CLOUDS = "clouds"

    /** A plume of smoke rising and thinning. */
    const val SMOKE = "smoke"

    /** Stars at three depths, rising past each other and twinkling. */
    const val STARFIELD = "starfield"

    /** In the order they are offered. */
    val ALL: List<String> = listOf(
        LAVA_LAMP, LIQUID_CHROME, MOLTEN, AURORA, INK_SMOKE, PLASMA,
        CAUSTICS, SOAP_FILM, MESH, CLOUDS, SMOKE, STARFIELD,
    )

    fun sanitize(id: String?): String = if (id in ALL) id!! else LAVA_LAMP

    /** The first Android release that runs an app's own shaders: 13, Tiramisu. */
    const val MIN_SDK = 33

    /**
     * The four colours an effect paints with, as `0xAARRGGBB`, in the order its program reads them.
     *
     * Where an effect has a night, a deep or a crust behind its light, that darkness is the first
     * colour, and dark enough to count as ground in [SliderFill.recolour] — so the user's colours
     * recolour the light and the dark it shines in stays dark.
     */
    fun palette(id: String): LongArray = when (sanitize(id)) {
        LAVA_LAMP -> longArrayOf(0xFF0C0418, 0xFF7A2BE8, 0xFFFF3D7A, 0xFFFFB347)
        LIQUID_CHROME -> longArrayOf(0xFF07060D, 0xFF4A4E63, 0xFFBFC4D8, 0xFFFFFFFF)
        MOLTEN -> longArrayOf(0xFF120808, 0xFFD92405, 0xFFFF8410, 0xFFFFF2B8)
        AURORA -> longArrayOf(0xFF050A18, 0xFF2BFF9A, 0xFF2BD8FF, 0xFFA36BFF)
        INK_SMOKE -> longArrayOf(0xFF0B0416, 0xFF2A44B0, 0xFFA8307A, 0xFF6FD8FF)
        PLASMA -> longArrayOf(0xFF3A0A05, 0xFFC44A20, 0xFFF08A3A, 0xFFFFE0A0)
        CAUSTICS -> longArrayOf(0xFF021A33, 0xFF0A5C8C, 0xFF3FD0E8, 0xFFE8FFFF)
        SOAP_FILM -> longArrayOf(0xFFFF7AC8, 0xFFFFD66B, 0xFF6BE8FF, 0xFF9B7BFF)
        MESH -> longArrayOf(0xFFFF5E9C, 0xFFFFB86B, 0xFF5E8BFF, 0xFF9B5CFF)
        CLOUDS -> longArrayOf(0xFF0B1236, 0xFF2E4FA8, 0xFF9AA8E0, 0xFFF6F4FF)
        SMOKE -> longArrayOf(0xFF0B0B12, 0xFF6A6A8C, 0xFFC4C4E0, 0xFFF4F2FF)
        STARFIELD -> longArrayOf(0xFF03030C, 0xFF2A1F6B, 0xFFFFFFFF, 0xFFA9C4FF)
        else -> longArrayOf(0xFF000000, 0xFF808080, 0xFFC0C0C0, 0xFFFFFFFF)
    }

    /** [palette] for [id], recoloured with the user's [custom] colours when there are any, as opaque ints. */
    fun paletteWith(id: String, custom: IntArray?): IntArray =
        SliderFill.recolour(palette(id), custom).let { p -> IntArray(p.size) { p[it].toInt() } }

    /** What the third slider does in each effect: every effect has one thing about itself worth a slider. */
    enum class Detail { WARP, FLOW, SWAY, DISTORTION, SOFTNESS, THICKNESS, DRIFT, BILLOW, SWIRL, DENSITY, BLOBS }

    fun detail(id: String): Detail = when (sanitize(id)) {
        LAVA_LAMP -> Detail.BLOBS
        LIQUID_CHROME, INK_SMOKE -> Detail.WARP
        MOLTEN -> Detail.FLOW
        AURORA -> Detail.SWAY
        PLASMA -> Detail.DISTORTION
        CAUSTICS -> Detail.SOFTNESS
        SOAP_FILM -> Detail.THICKNESS
        MESH -> Detail.DRIFT
        CLOUDS -> Detail.BILLOW
        SMOKE -> Detail.SWIRL
        else -> Detail.DENSITY
    }

    /** Everything the user tunes about the Shaders fill, clamped to what can be drawn. */
    data class Style(
        val effect: String = LAVA_LAMP,
        /** How fast it moves: 1 is its own pace, 0 holds it still. */
        val speed: Float = DEFAULT_SPEED,
        /** How large its features are drawn: above 1 is finer, below is broader. */
        val scale: Float = DEFAULT_SCALE,
        /** The effect's own character, 0..1: see [Detail]. */
        val detail: Float = DEFAULT_DETAIL,
        /** A multiplier on its light. */
        val brightness: Float = DEFAULT_BRIGHTNESS,
        /** Film grain over it, 0..1. */
        val grain: Float = DEFAULT_GRAIN,
        /** How much of it shows above the level, 0..[MAX_REST]. */
        val rest: Float = DEFAULT_REST,
    ) {
        fun sanitized(): Style = Style(
            effect = sanitize(effect),
            speed = speed.clampOr(DEFAULT_SPEED, 0f, MAX_SPEED),
            scale = scale.clampOr(DEFAULT_SCALE, MIN_SCALE, MAX_SCALE),
            detail = detail.clampOr(DEFAULT_DETAIL, 0f, 1f),
            brightness = brightness.clampOr(DEFAULT_BRIGHTNESS, MIN_BRIGHTNESS, MAX_BRIGHTNESS),
            grain = grain.clampOr(DEFAULT_GRAIN, 0f, 1f),
            rest = rest.clampOr(DEFAULT_REST, 0f, MAX_REST),
        )

        /** Whether anything moves. A still one is drawn once rather than on every frame. */
        val isAnimated: Boolean get() = speed > 0f
    }

    const val DEFAULT_SPEED = 1f
    const val MAX_SPEED = 3f
    const val DEFAULT_SCALE = 1f
    const val MIN_SCALE = 0.5f
    const val MAX_SCALE = 2.5f
    const val DEFAULT_DETAIL = 0.5f
    const val DEFAULT_BRIGHTNESS = 1f
    const val MIN_BRIGHTNESS = 0.5f
    const val MAX_BRIGHTNESS = 1.5f
    const val DEFAULT_GRAIN = 0.15f
    const val DEFAULT_REST = 0.15f
    const val MAX_REST = 0.4f

    /**
     * Where a shader's clock wraps, in seconds of its own time. Its programs only ever see this
     * much, which keeps every number they work with well inside the precision a phone's graphics
     * chip carries — and an hour of a panel held open at one speed is the only way to see the wrap.
     */
    const val TIME_WRAP_S = 3600f

    private fun Float.clampOr(fallback: Float, low: Float, high: Float): Float =
        if (isNaN()) fallback else coerceIn(low, high)
}
