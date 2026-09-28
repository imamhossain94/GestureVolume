package com.newagedevs.gesturevolume.ui.view

import com.newagedevs.gesturevolume.utils.ShaderFill

/**
 * The Shaders fill's programs, in AGSL: one shared head and tail, and a body per effect.
 *
 * Every body is a function `float3 effect(float2 uv)` giving the colour at `uv`, where `uv` runs
 * 0..1 across the track and upward from its foot in the same units — so a track four times as tall
 * as it is wide runs 0..4 up it, and an effect's features stay round however tall the track is.
 * The tail does the rest the same way for all of them: brightness, grain, and lighting the effect
 * fully below the level and faintly above it.
 *
 * Each written for this track, after the effects of MetalForge's gallery; its own programs are not
 * public, and a bar standing on its end wants different ones anyway.
 *
 * AGSL is strict in ways GLSL is not, and the phone is where it finds out: every number here is a
 * float literal, every loop has constant bounds, and nothing converts between `half` and `float`
 * without being asked. A program that does not compile throws when it is made, so `ShaderArt`
 * catches that and the panel falls back to a plain fill; the instrumented test compiles them all.
 */
internal object ShaderSources {

    /** The uniforms every program declares, in the names `ShaderArt` sets. */
    val FLOAT_UNIFORMS = listOf("time", "level", "rest", "scale", "detail", "bright", "grain", "alpha")
    val FLOAT2_UNIFORMS = listOf("origin", "size")
    val COLOR_UNIFORMS = listOf("c0", "c1", "c2", "c3")

    fun source(effect: String): String = HEAD + HELPERS + body(effect) + TAIL

    private val HEAD = """
        uniform float2 origin;
        uniform float2 size;
        uniform float time;
        uniform float level;
        uniform float rest;
        uniform float scale;
        uniform float detail;
        uniform float bright;
        uniform float grain;
        uniform float alpha;
        layout(color) uniform half4 c0;
        layout(color) uniform half4 c1;
        layout(color) uniform half4 c2;
        layout(color) uniform half4 c3;

    """.trimIndent() + "\n"

    /**
     * The noise, the colours and the track's proportions every program shares. Also the Surge
     * fill's (see `SurgeSources`), whose programs declare `size` and the four colours too.
     */
    val HELPERS = """
        const float TAU = 6.2831853;

        // A hash without sine: sine loses its precision on some phones' chips long before this does.
        float hash(float2 p) {
            float3 q = fract(float3(p.x, p.y, p.x) * 0.1379);
            q += dot(q, q.yzx + 19.19);
            return fract((q.x + q.y) * q.z);
        }

        // A lattice corner's value. Each corner is reached from four cells, by four different sums,
        // and far from the origin those sums round differently in their last place; the hash exists
        // to magnify small differences, so the four disagreed and every cell edge showed as a seam.
        // Wrapped to an exact whole number first, all four hand it the same input.
        float corner(float2 i) { return hash(mod(i, 289.0)); }

        float noise(float2 p) {
            float2 i = floor(p);
            float2 f = p - i;
            // Quintic, not cubic: smooth in its second derivative too, so the lattice does not show.
            float2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
            float a = corner(i);
            float b = corner(i + float2(1.0, 0.0));
            float c = corner(i + float2(0.0, 1.0));
            float d = corner(i + float2(1.0, 1.0));
            return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
        }

        // Five octaves, each turned against the last so no grid shows through.
        float fbm(float2 p) {
            float v = 0.0;
            float a = 0.5;
            for (int i = 0; i < 5; i++) {
                v += a * noise(p);
                p = float2(1.6 * p.x + 1.2 * p.y, -1.2 * p.x + 1.6 * p.y) + 3.7;
                a *= 0.5;
            }
            return v;
        }

        float3 ink(half4 c) { return float3(c.rgb); }

        // How tall the track is, in widths.
        float tall() { return size.y / max(size.x, 1.0); }

        // Along the four colours, first to last.
        float3 ramp(float t) {
            float k = clamp(t, 0.0, 1.0) * 3.0;
            if (k < 1.0) { return mix(ink(c0), ink(c1), k); }
            if (k < 2.0) { return mix(ink(c1), ink(c2), k - 1.0); }
            return mix(ink(c2), ink(c3), k - 2.0);
        }

        // Round the four colours and back to the first.
        float3 cycle(float t) {
            float k = fract(t) * 4.0;
            if (k < 1.0) { return mix(ink(c0), ink(c1), k); }
            if (k < 2.0) { return mix(ink(c1), ink(c2), k - 1.0); }
            if (k < 3.0) { return mix(ink(c2), ink(c3), k - 2.0); }
            return mix(ink(c3), ink(c0), k - 3.0);
        }

    """.trimIndent() + "\n"

    private val TAIL = """

        half4 main(float2 fragCoord) {
            float2 q = fragCoord - origin;
            float w = max(size.x, 1.0);
            float2 uv = float2(q.x / w, (size.y - q.y) / w);
            float3 c = effect(uv) * bright;
            // Grain that changes with the frame: still grain reads as a dirty screen, moving grain as film.
            float n = hash(fragCoord + mod(floor(time * 24.0), 61.0) * 13.7) - 0.5;
            c = clamp(c + n * grain * 0.18, 0.0, 1.0);
            // Fully lit below the level, faint above it, with a pixel of softening between.
            float lit = smoothstep(level - 1.0, level + 1.0, fragCoord.y);
            float a = mix(rest, 1.0, lit) * alpha;
            return half4(half3(c * a), half(a));
        }
    """.trimIndent() + "\n"

    private fun body(effect: String): String = when (ShaderFill.sanitize(effect)) {
        ShaderFill.LAVA_LAMP -> LAVA_LAMP
        ShaderFill.LIQUID_CHROME -> LIQUID_CHROME
        ShaderFill.MOLTEN -> MOLTEN
        ShaderFill.AURORA -> AURORA
        ShaderFill.INK_SMOKE -> INK_SMOKE
        ShaderFill.PLASMA -> PLASMA
        ShaderFill.CAUSTICS -> CAUSTICS
        ShaderFill.SOAP_FILM -> SOAP_FILM
        ShaderFill.MESH -> MESH
        ShaderFill.CLOUDS -> CLOUDS
        ShaderFill.SMOKE -> SMOKE
        else -> STARFIELD
    }

    // Blobs on their own slow orbits, mostly up and down the lamp. Where their fields add past one
    // the wax is solid, with a soft skin; the bulb at the foot warms the wax and lights the liquid.
    private val LAVA_LAMP = """
        float3 effect(float2 uv) {
            float h = tall();
            float t = time * 0.3;
            float count = 3.0 + floor(detail * 5.0 + 0.5);
            float field = 0.0;
            for (int i = 0; i < 8; i++) {
                float fi = float(i);
                if (fi < count) {
                    float r1 = hash(float2(fi, 3.7));
                    float r2 = hash(float2(fi, 9.1));
                    float2 centre = float2(
                        0.5 + 0.22 * sin(t * (0.5 + 0.4 * r1) + fi * 1.9),
                        h * (0.5 + 0.45 * sin(t * (0.3 + 0.25 * r2) + fi * 2.4)));
                    float radius = (0.22 + 0.14 * r1) / scale;
                    float2 d = uv - centre;
                    field += radius * radius / max(dot(d, d), 0.0001);
                }
            }
            float y = clamp(uv.y / max(h, 0.001), 0.0, 1.0);
            float mass = smoothstep(0.9, 1.1, field);
            float deep = smoothstep(1.0, 3.5, field);
            float3 wax = mix(ink(c2), ink(c3), clamp((1.0 - y) * 0.7 + deep * 0.25, 0.0, 1.0));
            wax *= 0.8 + 0.2 * deep;
            float3 liquid = ink(c0) + ink(c1) * (0.12 + 0.3 * (1.0 - y)) + ink(c1) * 0.25 * smoothstep(0.3, 0.9, field);
            return mix(liquid, wax, mass);
        }
    """.trimIndent()

    // A smooth field warped by itself, folded into broad bands the way a sky folds in rippled
    // metal, with a glint on the brightest. Three octaves, not five: chrome is smooth.
    private val LIQUID_CHROME = """
        float soft(float2 p) {
            float v = 0.0;
            float a = 0.5;
            for (int i = 0; i < 3; i++) {
                v += a * noise(p);
                p = float2(1.6 * p.x + 1.2 * p.y, -1.2 * p.x + 1.6 * p.y) + 3.7;
                a *= 0.5;
            }
            return v / 0.875;
        }

        float3 effect(float2 uv) {
            float2 p = uv * (1.2 * scale);
            float t = time * 0.2;
            float w = 0.3 + 1.7 * detail;
            float2 a = float2(soft(p + float2(0.0, t)), soft(p + float2(5.2, 1.3 - t)));
            float f = soft(p + w * a + float2(t * 0.3, 0.0));
            float band = 0.5 + 0.5 * sin(TAU * (f * 1.6 + a.x * 0.6 + uv.y * 0.15));
            float metal = smoothstep(0.1, 0.9, band);
            float3 c = mix(ink(c0), ink(c1), smoothstep(0.0, 0.5, metal));
            c = mix(c, ink(c2), smoothstep(0.45, 0.9, metal));
            return c + ink(c3) * pow(smoothstep(0.85, 1.0, band), 6.0) * 0.9;
        }
    """.trimIndent()

    // Two drifting fields push the crust about; where the pushed field crosses its middle it is
    // cracked, wide veins and fine ones, and the heat in a crack is run from rock to white.
    private val MOLTEN = """
        float3 effect(float2 uv) {
            float2 p = uv * (2.2 * scale);
            float t = time * 0.18;
            float2 flow = float2(fbm(p + float2(t, 0.0)), fbm(p + float2(3.1, -t)));
            float2 w = p + (0.3 + 2.0 * detail) * (flow - 0.5);
            float wide = 1.0 - abs(fbm(w + float2(0.0, -t * 0.6)) * 2.0 - 1.0);
            float fine = 1.0 - abs(fbm(w * 2.7 + float2(7.0, t * 0.9)) * 2.0 - 1.0);
            float heat = pow(wide, 7.0) + 0.55 * pow(fine, 12.0);
            heat *= 0.75 + 0.5 * noise(p * 3.0 + float2(t * 2.0, -t));
            // Hotter low down, where lava pools.
            heat *= mix(1.2, 0.85, clamp(uv.y / 4.0, 0.0, 1.0));
            return ramp(heat) + float3(0.05, 0.04, 0.04) * fbm(p * 5.0);
        }
    """.trimIndent()

    // Three ribbons swaying up the track, fine rays climbing through each, green at the foot to
    // violet at the crown.
    private val AURORA = """
        float3 effect(float2 uv) {
            float h = tall();
            float y = clamp(uv.y / max(h, 0.001), 0.0, 1.0);
            float t = time * 0.3;
            float3 c = mix(ink(c0) * 1.8, ink(c0), y);
            float sway = 0.12 + 0.3 * detail;
            for (int i = 0; i < 3; i++) {
                float fi = float(i);
                float centre = 0.5 + sway * sin(uv.y * (1.1 + 0.4 * fi) * scale + t * (1.0 + 0.3 * fi) + fi * 2.1)
                    + 0.15 * (noise(float2(uv.y * 1.5 * scale + fi * 7.0, t * 0.7)) - 0.5);
                float d = (uv.x - centre) / (0.1 + 0.06 * fi);
                float ribbon = exp(-d * d);
                float rays = 0.55 + 0.45 * noise(float2(uv.x * 38.0 + fi * 11.0, uv.y * 1.2 * scale - time * 0.8));
                float shimmer = 0.7 + 0.3 * sin(uv.y * 5.0 - time * 1.5 + fi * 1.3);
                float3 tint = mix(ink(c1), ink(c2), clamp(y * 1.2 - 0.1 + 0.2 * fi, 0.0, 1.0));
                tint = mix(tint, ink(c3), clamp(y * 1.6 - 0.8, 0.0, 1.0));
                c += tint * ribbon * rays * shimmer * (1.0 - 0.2 * fi);
            }
            return c;
        }
    """.trimIndent()

    // A field warped through two of its own copies, so it curls; the inks are laid by how far each
    // copy pulled it, and the peaks catch a wisp of light.
    private val INK_SMOKE = """
        float3 effect(float2 uv) {
            float2 p = uv * (1.5 * scale);
            float t = time * 0.12;
            float w = 1.0 + 4.0 * detail;
            float2 a = float2(fbm(p + float2(0.0, -t)), fbm(p + float2(5.2, 1.3) + t));
            float2 b = float2(fbm(p + w * a + float2(1.7, 9.2) + 0.5 * t), fbm(p + w * a + float2(8.3, 2.8) - 0.4 * t));
            float f = fbm(p + w * b);
            float3 c = mix(ink(c0), ink(c1), clamp(f * f * 3.0, 0.0, 1.0));
            c = mix(c, ink(c2), clamp(length(a - 0.5) * 2.2, 0.0, 1.0));
            c = mix(c, ink(c3), clamp(pow(b.y, 3.0) * 1.6, 0.0, 1.0));
            return c + ink(c3) * smoothstep(0.62, 0.85, f) * 0.5;
        }
    """.trimIndent()

    // Four waves summed and read as a place on the colours, the plane bent under them first.
    private val PLASMA = """
        float3 effect(float2 uv) {
            float2 p = uv * (3.0 * scale);
            float t = time * 0.5;
            p += detail * 1.4 * float2(sin(p.y * 1.3 + t), cos(p.x * 1.7 - t * 0.8));
            float v = sin(p.x + t) + sin((p.y + t) * 0.8) + sin((p.x + p.y) * 0.6 + t * 1.3)
                + sin(length(p - float2(1.5 + sin(t * 0.5), 3.0 + cos(t * 0.4))) * 1.5 - t);
            return ramp(0.5 + 0.5 * sin(v * 1.3));
        }
    """.trimIndent()

    // The edges between wandering cells, where light through a moving surface gathers. Two nets at
    // different sizes over water that deepens toward the foot.
    private val CAUSTICS = """
        float net(float2 p, float t) {
            float2 cell = floor(p);
            float2 f = fract(p);
            float d1 = 8.0;
            float d2 = 8.0;
            for (int iy = -1; iy <= 1; iy++) {
                for (int ix = -1; ix <= 1; ix++) {
                    float2 g = float2(float(ix), float(iy));
                    // Wrapped for the reason noise's corners are: each point is reached from nine cells.
                    float2 k = mod(cell + g, 289.0);
                    float2 o = float2(hash(k), hash(k + 19.7));
                    o = 0.5 + 0.45 * sin(t + TAU * o);
                    float d = length(g + o - f);
                    if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) { d2 = d; }
                }
            }
            return d2 - d1;
        }

        float3 effect(float2 uv) {
            float y = clamp(uv.y / max(tall(), 0.001), 0.0, 1.0);
            float t = time * 0.8;
            float2 p = uv * (3.0 * scale);
            p += 0.35 * float2(noise(p * 0.8 + t * 0.3), noise(p * 0.8 - t * 0.3 + 4.0));
            float soft = 0.08 + 0.25 * detail;
            float e1 = 1.0 - smoothstep(0.0, soft, net(p, t));
            float e2 = 1.0 - smoothstep(0.0, soft, net(p * 1.7 + 3.3, t * 1.3));
            float light = e1 * e1 + 0.6 * e2 * e2;
            float3 water = mix(ink(c0), ink(c1), 0.35 + 0.65 * y);
            return water + mix(ink(c2), ink(c3), clamp(light * 0.6, 0.0, 1.0)) * light * 0.8;
        }
    """.trimIndent()

    // A film whose thickness is a warped, drifting field: its colour goes round the four as it
    // thickens, and a sheen slides over it.
    private val SOAP_FILM = """
        float3 effect(float2 uv) {
            float2 p = uv * (1.3 * scale);
            float t = time * 0.15;
            float2 a = float2(fbm(p + float2(0.0, t)), fbm(p + float2(4.3, -t)));
            float thick = fbm(p + (1.0 + 2.0 * detail) * a + float2(t * 0.5, 0.0)) * (1.5 + 2.5 * detail);
            thick += 0.15 * sin(uv.y * 2.0 - time * 0.4);
            float3 film = cycle(thick);
            float sheen = smoothstep(0.3, 0.9, fbm(p * 1.7 + a - float2(0.0, t * 0.6)));
            return film * (0.45 + 0.55 * sheen) + float3(0.9) * pow(sheen, 12.0) * 0.25;
        }
    """.trimIndent()

    // Four colours on slow wandering paths, each weighing in by how near it is: a mesh gradient.
    private val MESH = """
        float3 effect(float2 uv) {
            float t = time * 0.3;
            float drift = 0.15 + 0.35 * detail;
            float2 n = float2(uv.x, uv.y / max(tall(), 0.001));
            n += 0.04 * float2(sin(n.y * 6.0 + t), cos(n.x * 5.0 - t));
            float2 p0 = float2(0.2 + drift * sin(t * 0.9), 0.15 + drift * cos(t * 0.7));
            float2 p1 = float2(0.8 + drift * cos(t * 0.8 + 1.0), 0.4 + drift * sin(t * 0.6 + 2.0));
            float2 p2 = float2(0.25 + drift * sin(t * 0.7 + 3.0), 0.65 + drift * cos(t * 0.9 + 1.5));
            float2 p3 = float2(0.75 + drift * cos(t * 0.6 + 4.0), 0.9 + drift * sin(t * 0.8 + 0.5));
            float k = 7.0 * scale;
            float w0 = exp(-k * dot(n - p0, n - p0));
            float w1 = exp(-k * dot(n - p1, n - p1));
            float w2 = exp(-k * dot(n - p2, n - p2));
            float w3 = exp(-k * dot(n - p3, n - p3));
            return (ink(c0) * w0 + ink(c1) * w1 + ink(c2) * w2 + ink(c3) * w3) / (w0 + w1 + w2 + w3 + 0.0001);
        }
    """.trimIndent()

    // A field warped by another, cut into cloud where it is thick, over a sky paler at the foot.
    private val CLOUDS = """
        float3 effect(float2 uv) {
            float y = clamp(uv.y / max(tall(), 0.001), 0.0, 1.0);
            float2 p = uv * (1.4 * scale) + float2(time * 0.05, time * 0.02);
            float2 q = float2(fbm(p + float2(0.0, time * 0.03)), fbm(p + float2(5.2, 1.3)));
            float f = fbm(p + (0.8 + 2.4 * detail) * q);
            float cover = smoothstep(0.38, 0.72, f);
            float3 sky = mix(ink(c1), ink(c0), y);
            float3 cloud = mix(ink(c2), ink(c3), smoothstep(0.5, 0.85, f));
            return mix(sky, cloud, cover);
        }
    """.trimIndent()

    // A plume rising: a field drifting up and stirred by another, thinning toward the top.
    private val SMOKE = """
        float3 effect(float2 uv) {
            float y = clamp(uv.y / max(tall(), 0.001), 0.0, 1.0);
            float t = time * 0.35;
            float2 q = float2(uv.x * 1.6, uv.y * 0.9) * scale + float2(0.0, -t);
            float swirl = 0.4 + 1.6 * detail;
            float n = fbm(q + swirl * float2(fbm(q * 1.3 + float2(t * 0.4, 0.0)), fbm(q * 1.3 + float2(4.1, -t * 0.3))));
            float dens = pow(smoothstep(0.28, 0.66, n) * mix(1.0, 0.55, y), 1.2);
            float3 smoke = mix(ink(c1), ink(c2), smoothstep(0.45, 0.8, n));
            float3 c = mix(ink(c0), smoke, dens);
            return c + ink(c3) * pow(smoothstep(0.55, 0.85, n), 3.0) * 0.45;
        }
    """.trimIndent()

    // Three layers of cells, a star in some of them, the nearer layers larger, brighter and rising
    // faster; each star twinkles on its own beat. A faint nebula behind.
    private val STARFIELD = """
        float3 effect(float2 uv) {
            float3 c = ink(c0) + ink(c1) * 0.6 * smoothstep(0.35, 0.8, fbm(uv * 1.2 + float2(0.0, -time * 0.02)));
            float density = 0.18 + 0.6 * detail;
            for (int i = 0; i < 3; i++) {
                float fi = float(i);
                float cells = (9.0 - 2.5 * fi) * scale;
                float2 p = uv * cells + float2(fi * 17.3, -time * (0.25 + 0.35 * fi));
                float2 cell = mod(floor(p), 289.0);
                float2 f = fract(p) - 0.5;
                float r = hash(cell + fi * 31.7);
                if (r < density) {
                    float2 off = float2(hash(cell + 7.1), hash(cell + 3.9)) - 0.5;
                    float d = length(f - off * 0.4);
                    float sz = 0.05 + 0.05 * fi + 0.03 * hash(cell + 1.3);
                    float tw = 0.65 + 0.35 * sin(time * (2.0 + 3.0 * r) + r * 40.0);
                    float star = (1.0 - smoothstep(0.0, sz, d)) + 0.25 * (1.0 - smoothstep(0.0, sz * 3.0, d));
                    c += mix(ink(c2), ink(c3), hash(cell + 5.5)) * star * tw * (0.6 + 0.2 * fi);
                }
            }
            return c;
        }
    """.trimIndent()
}
