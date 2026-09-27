package com.newagedevs.gesturevolume.ui.view

import com.newagedevs.gesturevolume.utils.SurgeFill

/**
 * The Surge fill's programs, in AGSL: a shared head, [ShaderSources.HELPERS] for the noise, a body
 * per look, and a shared tail.
 *
 * Every body is a function `float4 look(float2 uv, float lv, float frac)` giving the colour at `uv`
 * and how much of it covers the track, not yet multiplied through. `uv` runs 0..1 across the track
 * and upward from its foot in the same units, as the Shaders fill's does; `lv` is the level in
 * those units, and `frac` the level as a share of the track, 0..1.
 *
 * What the looks share is the front: its distance `d` above a pixel, in widths, is what every one
 * of them lights by — the long trail of light behind it ([trail]), the glow on it ([glow]), and the
 * halo just past it. [edge] is how far the front strays from the level, and [scale] how busy it is.
 *
 * Strict AGSL, as the Shaders programs are: float literals, constant loop bounds, no `pow` of a
 * number that can be negative. `SurgeArt` catches a program that will not compile, and the
 * instrumented test compiles them all.
 */
internal object SurgeSources {

    /** The uniforms every program declares, in the names `SurgeArt` sets. */
    val FLOAT_UNIFORMS = listOf("time", "level", "rest", "scale", "edge", "glow", "trail", "alpha")
    val FLOAT2_UNIFORMS = listOf("origin", "size")
    val COLOR_UNIFORMS = listOf("c0", "c1", "c2", "c3")

    fun source(look: String): String = HEAD + ShaderSources.HELPERS + FRONT + body(look) + TAIL

    private val HEAD = """
        uniform float2 origin;
        uniform float2 size;
        uniform float time;
        uniform float level;
        uniform float rest;
        uniform float scale;
        uniform float edge;
        uniform float glow;
        uniform float trail;
        uniform float alpha;
        layout(color) uniform half4 c0;
        layout(color) uniform half4 c1;
        layout(color) uniform half4 c2;
        layout(color) uniform half4 c3;

    """.trimIndent() + "\n"

    private val FRONT = """

        // A pixel, in widths: for edges one pixel soft, however wide the track.
        float px() { return 1.0 / max(size.x, 1.0); }

        // How far the light reaches behind the front, and how wide its glow is, in widths.
        float trailLength() { return mix(0.25, 5.0, trail * trail); }
        float glowWidth() { return mix(0.04, 0.3, glow); }

        // How far the front is moved off the level near the ends, for a front that strays [reach]
        // either side of it: all of it under the foot when empty, all of it over the top when full,
        // so an empty track is dark and a full one lit to its end.
        float lift(float reach, float frac) {
            return reach * (smoothstep(0.82, 1.0, frac) - smoothstep(0.18, 0.0, frac));
        }

        // The light d widths behind the front: the long trail out of the dark, the glow on the
        // front, and the hot line along it. Most of the body is the dark: the light is the front's.
        float3 behind(float d) {
            float g = glowWidth();
            float3 c = ink(c0);
            c += ink(c1) * exp(-d / trailLength()) * 0.55;
            c += ink(c2) * exp(-d / (g * 1.2)) * (0.5 + 0.7 * glow);
            c += ink(c3) * exp(-d / (g * 0.18)) * (0.4 + 0.6 * glow);
            return c;
        }

        // A smooth front at [front] widths up the track: lit behind it, and past it a halo of its
        // light on the track and, faintly, what [rest] leaves of it.
        float4 smoothFront(float2 uv, float front) {
            float d = front - uv.y;
            float inside = smoothstep(-px(), px(), d);
            float halo = exp(min(d, 0.0) / (glowWidth() * 0.45)) * glow * 0.55;
            float aboveA = clamp(halo + rest * 0.6, 0.0, 1.0);
            float3 above = mix(ink(c1), ink(c2), halo / max(aboveA, 0.001));
            return float4(mix(above, behind(max(d, 0.0)), inside), mix(aboveA, 1.0, inside));
        }

    """.trimIndent() + "\n"

    private val TAIL = """

        half4 main(float2 fragCoord) {
            float2 q = fragCoord - origin;
            float w = max(size.x, 1.0);
            float2 uv = float2(q.x / w, (size.y - q.y) / w);
            float lv = (size.y - (level - origin.y)) / w;
            float frac = clamp(lv / tall(), 0.0, 1.0);
            float4 c = look(uv, lv, frac);
            float a = clamp(c.a, 0.0, 1.0) * alpha;
            return half4(half3(clamp(c.rgb, 0.0, 1.0) * a), half(a));
        }
    """.trimIndent() + "\n"

    private fun body(look: String): String = when (SurgeFill.sanitize(look)) {
        SurgeFill.ELECTRIC -> ELECTRIC
        SurgeFill.HONEYCOMB -> HONEYCOMB
        SurgeFill.STREAKS -> STREAKS
        SurgeFill.BLOCKS -> BLOCKS
        SurgeFill.FLAME -> FLAME
        else -> CURVE
    }

    // An S of light across the track, its bend drifting from side to side and breathing.
    private val CURVE = """
        float4 look(float2 uv, float lv, float frac) {
            float reach = edge * 0.55;
            float bend = 0.5 + 0.18 * sin(time * 0.55);
            float s = clamp((uv.x - bend) * 1.5 * scale + 0.5, 0.0, 1.0);
            float breath = 0.8 + 0.2 * sin(time * 0.9);
            float front = lv + lift(reach, frac) + reach * (s * s * (3.0 - 2.0 * s) * 2.0 - 1.0) * breath;
            return smoothFront(uv, front);
        }
    """.trimIndent()

    // A front torn by turbulence and jittering fast, with threads of light behind it that follow
    // its shape, bent by turbulence of their own as they fall back.
    private val ELECTRIC = """
        float4 look(float2 uv, float lv, float frac) {
            float reach = edge * 0.5;
            float torn = (fbm(float2(uv.x * 2.4 * scale, time * 0.9)) - 0.5) * 2.2;
            float jitter = 0.22 * sin(uv.x * TAU * 2.6 * scale + time * 3.7);
            float front = lv + lift(reach * 1.3, frac) + reach * (torn + jitter);
            float4 c = smoothFront(uv, front);
            float d = front - uv.y;
            if (d > 0.0) {
                float warp = (fbm(float2(uv.x * 3.4 * scale + 5.0, uv.y * 1.2 - time * 0.7)) - 0.5) * 0.8;
                float band = abs(fract((d + warp) * 2.0 * scale) - 0.5);
                float thread = exp(-band * band / 0.0035) * exp(-d / (glowWidth() * 3.0 + trailLength() * 0.25)) * (0.3 + 0.7 * glow);
                c.rgb += ink(c2) * thread * 0.7 + ink(c3) * thread * 0.25;
            }
            return c;
        }
    """.trimIndent()

    // Hexagons, lit whole: a cell is on when its centre is under the front, which is ragged by a
    // cell's worth where the edge asks for it. Each lit cell its own shade between the deep and the
    // bright light, shimmering on its own beat; the ones near the front brightest. Unlit, a cell is
    // what the rest leaves of it; the gaps are the dark.
    private val HONEYCOMB = """
        float4 look(float2 uv, float lv, float frac) {
            float across = 3.2 * scale;
            float2 p = uv * across;
            float2 r = float2(1.0, 1.7320508);
            float2 h = r * 0.5;
            float2 a = mod(p, r) - h;
            float2 b = mod(p - h, r) - h;
            float2 g = dot(a, a) < dot(b, b) ? a : b;
            float2 id = p - g;
            float ragged = (hash(id + 3.1) - 0.5) * edge * 0.9;
            float d = lv + lift(edge * 0.45 + 0.3, frac) + ragged - id.y / across;
            float2 k = abs(g);
            float hex = max(dot(k, float2(0.5, 0.8660254)), k.x);
            float soft = across * px();
            float body = 1.0 - smoothstep(0.4 - soft, 0.4 + soft, hex);
            if (d > 0.0) {
                float beat = 0.5 + 0.5 * sin(time * (1.0 + 2.0 * hash(id + 1.7)) + hash(id) * TAU);
                float3 shade = mix(ink(c1), ink(c2), hash(id + 5.3));
                shade = mix(shade, ink(c3), beat * 0.35 * (0.4 + glow));
                float far = mix(0.4, 1.0, exp(-d / trailLength()));
                float near = exp(-d / glowWidth());
                float3 lit = shade * far + ink(c3) * near * glow * 0.6;
                return float4(mix(ink(c0), lit, body), 1.0);
            }
            return float4(ink(c1) * 0.5, body * rest * 1.6);
        }
    """.trimIndent()

    // Fine streaks up the track, each ending at its own length and reaching on and back on its own
    // beat, brightest at its tip. The dark between them runs up to where the front is on average.
    private val STREAKS = """
        float4 look(float2 uv, float lv, float frac) {
            float n = floor(12.0 * scale + 0.5);
            float col = floor(uv.x * n);
            float x = fract(uv.x * n);
            float r1 = hash(float2(col, 1.3));
            float r2 = hash(float2(col, 7.7));
            float reach = edge * 0.9;
            float base = lv + lift(reach * 0.6 + 0.05, frac);
            float wander = (r1 - 0.5) * 1.3 + 0.35 * sin(time * (0.8 + 1.6 * r2) + r1 * TAU);
            float d = base + wander * reach - uv.y;
            float soft = n * px();
            float near = exp(-max(d, 0.0) / glowWidth());
            float half_w = 0.26 + 0.08 * near;
            float line = 1.0 - smoothstep(half_w - soft, half_w + soft, abs(x - 0.5));
            float ground = smoothstep(-px(), px(), base - uv.y);
            if (d > 0.0) {
                float3 c = ink(c0) * 0.6 + ink(c1) * (0.3 + 0.7 * exp(-d / trailLength())) + ink(c2) * near * (0.5 + 0.7 * glow)
                    + ink(c3) * exp(-d / (glowWidth() * 0.3)) * (0.3 + 0.7 * glow);
                return float4(mix(ink(c0), c, line), max(line, ground));
            }
            float ghost = line * rest * 0.8;
            return float4(mix(ink(c1), ink(c0), 1.0 - ghost), max(ghost, ground));
        }
    """.trimIndent()

    // A few broad blocks side by side, each ending at its own length and stepping to a new one now
    // and then, easing there. A bright cap on each end, the light behind it long, a faint fluting
    // down each block, and a dark hairline between them.
    private val BLOCKS = """
        float4 look(float2 uv, float lv, float frac) {
            float n = floor(4.0 * scale + 0.5);
            float col = floor(uv.x * n);
            float x = fract(uv.x * n);
            float r = hash(float2(col, 2.9));
            float beat = time * (0.45 + 0.35 * r) + r * 5.0;
            float step0 = hash(float2(col, floor(beat)));
            float step1 = hash(float2(col, floor(beat) + 1.0));
            float s = fract(beat);
            float settle = smoothstep(0.7, 1.0, s);
            float jag = mix(step0, step1, settle) - 0.5;
            float reach = edge * 0.8;
            float d = lv + lift(reach * 0.5 + 0.05, frac) + jag * reach - uv.y;
            float soft = n * px();
            float hair = smoothstep(0.0, soft * 1.5, min(x, 1.0 - x));
            if (d > 0.0) {
                float g = glowWidth();
                float flute = noise(float2(x * 7.0 + col * 3.1, 0.5));
                float3 c = ink(c0) + ink(c1) * exp(-d / trailLength()) * 0.6
                    + ink(c2) * exp(-d / (g * 2.2)) * (0.5 + 0.6 * glow)
                    + ink(c3) * exp(-d / (g * 0.5)) * (0.5 + 0.5 * glow);
                c += ink(c2) * flute * flute * 0.25 * exp(-d / trailLength());
                return float4(mix(ink(c0), c, hair), 1.0);
            }
            return float4(ink(c1) * 0.6, hair * rest * 0.8);
        }
    """.trimIndent()

    // A front of flame: two noises across the track, squared so their peaks lick up as tongues and
    // their troughs flatten, moving at two paces. An inner ring of light follows the front a little
    // behind it, and the body flickers as it rises.
    private val FLAME = """
        float4 look(float2 uv, float lv, float frac) {
            float reach = edge * 0.85;
            float n1 = noise(float2(uv.x * 2.2 * scale, time * 1.1));
            float n2 = noise(float2(uv.x * 4.6 * scale + 7.3, time * 1.9));
            float tongue = n1 * 0.7 + n2 * 0.3;
            float front = lv + lift(reach * 1.4, frac) + reach * (tongue * tongue * 2.0 - 0.6);
            float4 c = smoothFront(uv, front);
            float d = front - uv.y;
            if (d > 0.0) {
                float g = glowWidth();
                float k = (d - g * 2.4) / (g * 0.9);
                float ring = exp(-k * k) * 0.35 * glow;
                float flicker = noise(float2(uv.x * 3.0 * scale, uv.y * 1.6 - time * 2.2)) - 0.5;
                c.rgb += ink(c2) * ring + ink(c1) * flicker * 0.3 * exp(-d / trailLength());
            }
            return c;
        }
    """.trimIndent()
}
