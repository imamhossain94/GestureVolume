// The engine under every scene: time, easing, springs, icons and a few DOM helpers.
// Nothing here reads the wall clock. A scene's seek(t) recomputes everything from t.

export const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
export const lerp = (a, b, t) => a + (b - a) * t;
export const invLerp = (a, b, v) => clamp((v - a) / (b - a));
export const mix = (a, b, t) => a + (b - a) * t;

// --- Easing ------------------------------------------------------------------------------------
// Compose's CubicBezierEasing, solved the same way (Newton then bisection on x).
export function cubicBezier(x1, y1, x2, y2) {
  const cx = 3 * x1, bx = 3 * (x2 - x1) - cx, ax = 1 - cx - bx;
  const cy = 3 * y1, by = 3 * (y2 - y1) - cy, ay = 1 - cy - by;
  const sx = (t) => ((ax * t + bx) * t + cx) * t;
  const sy = (t) => ((ay * t + by) * t + cy) * t;
  const dx = (t) => (3 * ax * t + 2 * bx) * t + cx;
  return (x) => {
    if (x <= 0) return 0;
    if (x >= 1) return 1;
    let t = x;
    for (let i = 0; i < 8; i++) {
      const e = sx(t) - x;
      if (Math.abs(e) < 1e-6) return sy(t);
      const d = dx(t);
      if (Math.abs(d) < 1e-6) break;
      t -= e / d;
    }
    let lo = 0, hi = 1; t = x;
    while (lo < hi) {
      const v = sx(t);
      if (Math.abs(v - x) < 1e-6) break;
      if (x > v) lo = t; else hi = t;
      t = (lo + hi) / 2;
      if (hi - lo < 1e-7) break;
    }
    return sy(t);
  };
}

export const Ease = {
  linear: (t) => t,
  fastOutSlowIn: cubicBezier(0.4, 0, 0.2, 1),       // Compose FastOutSlowInEasing
  linearOutSlowIn: cubicBezier(0, 0, 0.2, 1),       // Compose LinearOutSlowInEasing
  fastOutLinearIn: cubicBezier(0.4, 0, 1, 1),       // Compose FastOutLinearInEasing
  emphasizedDecel: cubicBezier(0.05, 0.7, 0.1, 1),  // M3 emphasized decelerate
  emphasizedAccel: cubicBezier(0.3, 0, 0.8, 0.15),  // M3 emphasized accelerate
  standard: cubicBezier(0.2, 0, 0, 1),              // M3 standard
  inOut: cubicBezier(0.65, 0, 0.35, 1),
  outCubic: (t) => 1 - Math.pow(1 - t, 3),
  inCubic: (t) => t * t * t,
  outQuint: (t) => 1 - Math.pow(1 - t, 5),
  inOutSine: (t) => -(Math.cos(Math.PI * t) - 1) / 2,
  outBack: (t, s = 1.70158) => 1 + (s + 1) * Math.pow(t - 1, 3) + s * Math.pow(t - 1, 2),
};

// --- Springs -----------------------------------------------------------------------------------
// The closed-form damped spring Compose's SpringSimulation integrates (mass 1), started at rest.
// Returns the fraction of the way from start to target at time t (s); overshoots when bouncy.
export const Stiffness = { high: 10000, medium: 1500, mediumLow: 400, low: 200, veryLow: 50 };
export const Damping = { highBouncy: 0.2, mediumBouncy: 0.5, lowBouncy: 0.75, noBouncy: 1 };

export function spring(t, { dampingRatio = 1, stiffness = Stiffness.medium, v0 = 0 } = {}) {
  if (t <= 0) return 0;
  const w0 = Math.sqrt(stiffness);
  const z = dampingRatio;
  const A = -1;
  let x;
  if (z < 1) {
    const wd = w0 * Math.sqrt(1 - z * z);
    const B = (z * w0 * A + v0) / wd;
    x = Math.exp(-z * w0 * t) * (A * Math.cos(wd * t) + B * Math.sin(wd * t));
  } else if (z === 1) {
    x = (A + (v0 + w0 * A) * t) * Math.exp(-w0 * t);
  } else {
    const s = Math.sqrt(z * z - 1);
    const r1 = -w0 * (z - s), r2 = -w0 * (z + s);
    const C2 = (v0 - r1 * A) / (r2 - r1);
    const C1 = A - C2;
    x = C1 * Math.exp(r1 * t) + C2 * Math.exp(r2 * t);
  }
  return 1 + x;
}

// --- Tracks ------------------------------------------------------------------------------------
// keys: [[time, value, easeIntoThisKey?], ...] with numbers or arrays of numbers.
export function track(keys) {
  return (t) => {
    if (t <= keys[0][0]) return keys[0][1];
    for (let i = 1; i < keys.length; i++) {
      const [t1, v1, ease] = keys[i];
      const [t0, v0] = keys[i - 1];
      if (t <= t1) {
        const f = (ease || Ease.fastOutSlowIn)(invLerp(t0, t1, t));
        return Array.isArray(v0) ? v0.map((a, j) => lerp(a, v1[j], f)) : lerp(v0, v1, f);
      }
    }
    return keys[keys.length - 1][1];
  };
}

// Progress of a segment [start, start+dur] with easing; 0 before, 1 after.
export const seg = (t, start, dur, ease = Ease.fastOutSlowIn) => ease(invLerp(start, start + dur, t));
// A window that fades in over `fin` and out over `fout` around [a, b].
export const win = (t, a, b, fin = 0.3, fout = 0.3) =>
  Math.min(seg(t, a, fin, Ease.linear), 1 - seg(t, b - fout, fout, Ease.linear));

// Deterministic pseudo-random numbers, so "random" art is the same on every render.
export function rng(seed = 1) {
  let s = seed >>> 0;
  return () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let x = s;
    x = Math.imul(x ^ (x >>> 15), x | 1);
    x ^= x + Math.imul(x ^ (x >>> 7), x | 61);
    return ((x ^ (x >>> 14)) >>> 0) / 4294967296;
  };
}

// --- Icons -------------------------------------------------------------------------------------
// Material Icons (the set Compose's material-icons-extended is drawn from), by style and name:
// "round/volume_up", "outlined/tune". `drawable/x` is one of the app's own drawables, converted
// from its VectorDrawable by vd2svg.py into build/drawable/.
const iconCache = new Map();
const base = new URL('..', import.meta.url).href;

export async function loadIcons(names) {
  await Promise.all(names.map(async (n) => {
    if (iconCache.has(n)) return;
    const url = n.startsWith('drawable/')
      ? `${base}build/${n}.svg`
      : `${base}node_modules/@material-design-icons/svg/${n}.svg`;
    const res = await fetch(url);
    if (!res.ok) throw new Error('icon missing: ' + n);
    iconCache.set(n, await res.text());
  }));
}

// An icon as inline SVG markup, sized in px (dp at 1:1), coloured by `color` (currentColor).
export function icon(name, size = 24, color = 'currentColor', extra = '') {
  const svg = iconCache.get(name);
  if (!svg) throw new Error('icon not loaded: ' + name);
  const fill = name.startsWith('drawable/') ? '' : ` fill="${color}"`;
  return svg.replace('<svg ', `<svg style="width:${size}px;height:${size}px;color:${color};display:block;flex:none;${extra}"${fill} `)
    .replace(/ width="[\d.]+" height="[\d.]+"/, '');
}

// One of the app's drawables tinted like DrawableCompat.setTint (SRC_IN): every painted colour
// becomes `color`, alpha kept.
export function drawableIcon(name, size = 24, color = '#000', extra = '') {
  const key = name.startsWith('drawable/') ? name : 'drawable/' + name;
  const svg = iconCache.get(key);
  if (!svg) throw new Error('icon not loaded: ' + key);
  return svg
    .replace(/(fill|stroke|stop-color)="(#[0-9a-fA-F]{3,8}|currentColor)"/g, (m, attr) => `${attr}="${color}"`)
    .replace(/url\(#g\d+\)/g, color)
    .replace('<svg ', `<svg style="width:${size}px;height:${size}px;display:block;flex:none;${extra}" `)
    .replace(/ width="[\d.]+" height="[\d.]+"/, '');
}

// --- DOM ---------------------------------------------------------------------------------------
export function h(html) {
  const t = document.createElement('template');
  t.innerHTML = html.trim();
  return t.content.firstElementChild;
}

export function css(el, styles) {
  for (const [k, v] of Object.entries(styles)) {
    if (v === null || v === undefined) continue;
    if (k.startsWith('--')) el.style.setProperty(k, v);
    else el.style[k] = typeof v === 'number' && !['opacity', 'zIndex', 'flexGrow', 'scale'].includes(k) ? v + 'px' : v;
  }
  return el;
}

export async function fontsReady() {
  await document.fonts.ready;
  // Touch every face used so the first frame never falls back.
  const probes = ['300', '400', '500', '600', '700', '800'].flatMap((w) => [
    `${w} 16px Roboto`, `${w} 16px "Plus Jakarta Sans"`, `${w} 16px Outfit`,
  ]);
  await Promise.all(probes.map((p) => document.fonts.load(p).catch(() => null)));
}

export async function imagesReady(root = document) {
  await Promise.all([...root.querySelectorAll('img')].map((img) => img.decode().catch(() => null)));
}

// Hex colour helpers.
export function rgba(hex, a = 1) {
  const v = hex.replace('#', '');
  const n = parseInt(v.length === 3 ? v.split('').map((c) => c + c).join('') : v.slice(-6), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
}
export function hexToRgb(hex) {
  const v = hex.replace('#', '').slice(-6);
  const n = parseInt(v, 16);
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}
