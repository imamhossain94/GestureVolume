// The Quick panel's picture fills, ported from FillArt.kt (and QuickSliderView's Galaxy, Nebula,
// Ember, the tides' wave edge and the Stripes tint): each paints a scene over the fill, inside the
// fill's clip, on top of the plain fill colour. Importing this module registers them with
// panelfills.js, which draws them on the preview's panel and on every fill tile.
//
// Lengths are dp; Android's few whole-pixel truncations are taken at its 2.625 density. Colours are
// [a, r, g, b] 0..255. `phase` is the fill's 0..1 clock (SliderFill.cycleMs, paced by the level).
import { clamp } from './core.js';
import { argb } from './quickpanel.js';
import { PICTORIAL, pseudoRandom as seed, recolour } from './panelfills.js';

const TAU = Math.PI * 2;
const DENSITY = 2.625;
const col = (c, mul = 1) => `rgba(${c[1] | 0},${c[2] | 0},${c[3] | 0},${clamp((c[0] / 255) * mul)})`;
const paintOf = (c, alpha) => `rgba(${c[1] | 0},${c[2] | 0},${c[3] | 0},${clamp(alpha)})`;   // paint.color then .alpha
const fadeC = (c, f) => [c[0] * clamp(f), c[1], c[2], c[3]];
const clearC = (c) => [0, c[1], c[2], c[3]];
const blendC = (x, y, t) => x.map((v, i) => v + (y[i] - v) * t);
const WHITE = [255, 255, 255, 255];
const pal = (list) => list.map((h) => argb(h));
const at = (p, i) => p[i % p.length];
const life = (phase, s, off) => (phase * (1 + Math.floor(s * 2)) + off) % 1;
const circle = (ctx, x, y, r) => { ctx.beginPath(); ctx.arc(x, y, Math.max(0, r), 0, TAU); ctx.fill(); };

// SliderFill.palette
export const FILL_PALETTES = {
  'dot-matrix': pal(['#FFB8FBFF', '#FF3FB7D6', '#3389E8FF']), nebula: pal(['#8C7A5CFF', '#805CB8FF', '#66FF6FD8', '#59FFC46B']),
  cyberpunk: pal(['#FFFFE24B', '#FFFF9A3D', '#FFFF2E88', '#FF5B1A8C', '#FF1B0B3A', '#FF00F0FF']),
  'matrix-rain': pal(['#FFE2FFE8', '#FF3CFF74', '#FF0B6E2B']), rune: pal(['#FFFFE2B0', '#FFFFA43D', '#FF6B4122']),
  plasma: pal(['#FF2B0A5C', '#FFE0318F', '#FFFF9F43', '#FFFFE66B', '#FF33D1FF']), aurora: pal(['#FF5CFFB0', '#FF3DE0FF', '#FFB77CFF', '#FF9DFF7C']),
  hologram: pal(['#FF6FF7FF', '#FFB79CFF', '#FFFF9AD5']), ember: pal(['#FFFFE9A8', '#E6FF9D3D', '#99FF5A1E']),
  sonar: pal(['#FF8CFFD8', '#FF2ED6A0']), circuit: pal(['#FF9CFFE6', '#FF1F7A5A']),
  liquid: pal(['#FFA8F5FF', '#FF3CC6F2', '#FF4A5CF0', '#FFFFFFFF']), waveform: pal(['#FF4FE3FF', '#FFFF5CC8', '#FF9B7CFF']),
  sunrise: pal(['#FFFFE3A1', '#FFFF8A3D', '#FFC2386B', '#FF3A1450']),
  spectrum: pal(['#FFFF9AD5', '#FFB79CFF', '#FF8CD9FF', '#FF8CFFD1', '#FFFFE98C', '#FFFFB38C']),
  galaxy: pal(['#FF120A2E', '#FF34207A', '#FFFFFFFF', '#FFBFD0FF']), silk: pal(['#FFFF7AA8', '#FFFFB08A', '#FFC3A0FF']),
  fireflies: pal(['#FFF4FF7A', '#FFB6F24A', '#FFFFC857']), snowfall: pal(['#FFFFFFFF', '#FFD6ECFF', '#FF9CC4FF']),
  heartbeat: pal(['#FF9CFFC8', '#FF2EE68A', '#FFFF5C7A']), neon: pal(['#FFFF4FD8', '#FF4FE8FF', '#FFB36BFF']),
  ocean: pal(['#FFCFFFF8', '#CC3FD6D0', '#B31E8FD6', '#99255CC4']), gradient: pal(['#FFFFC46B', '#FFFF6B8B', '#FF7B6BFF']),
  confetti: pal(['#FFFFE066', '#FF7CFF8A', '#FF5CE1E6', '#FFFF6B9D', '#FF9B7BFF']), warp: pal(['#FFFFFFFF', '#FFA8C8FF', '#FF7A5CFF']),
  storm: pal(['#FFF2F4FF', '#FFA9B8FF', '#996F86C8']), fireworks: pal(['#FFFFE08A', '#FFFF5C8A', '#FF5CD6FF', '#FFB98CFF']),
};

// --- FillArt's toolkit ------------------------------------------------------------------------------
// A soft light: full at the middle, a third by halfway, gone at the radii.
function glow(ctx, cx, cy, rx, ry, c, strength) {
  if (strength <= 0.004 || rx <= 0.5 || ry <= 0.5) return;
  ctx.save(); ctx.translate(cx, cy); ctx.scale(rx, ry);
  const g = ctx.createRadialGradient(0, 0, 0, 0, 0, 1);
  g.addColorStop(0, col(c)); g.addColorStop(0.45, col(fadeC(c, 0.32))); g.addColorStop(1, col(clearC(c)));
  ctx.globalAlpha *= clamp(strength); ctx.fillStyle = g; ctx.fillRect(-1, -1, 2, 2); ctx.restore();
}
// A soft ring at `radius`, a twentieth of it wide.
function ring(ctx, cx, cy, radius, c, strength) {
  if (strength <= 0.004 || radius <= 0.5) return;
  const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, radius);
  g.addColorStop(0.8, col(clearC(c))); g.addColorStop(0.9, col(fadeC(c, 0.6))); g.addColorStop(0.95, col(c)); g.addColorStop(1, col(clearC(c)));
  ctx.save(); ctx.globalAlpha *= clamp(strength); ctx.fillStyle = g; ctx.fillRect(cx - radius, cy - radius, radius * 2, radius * 2); ctx.restore();
}
// A vertical gradient across the fill, `colors` from top down to bottom.
function ground(ctx, colors, stops, R, top, bottom, alpha) {
  const g = ctx.createLinearGradient(0, top, 0, top + Math.max(1, bottom - top));
  colors.forEach((c, i) => g.addColorStop(stops ? stops[i] : i / (colors.length - 1), col(c)));
  ctx.save(); ctx.globalAlpha *= clamp(alpha); ctx.fillStyle = g; ctx.fillRect(R.x, top, R.w, bottom - top); ctx.restore();
}
// Specks rising through the whole track, faded in and out, swaying as they go.
function motes(ctx, R, fillTop, phase, count, base, c, strength, sizeDp) {
  const bottom = R.y + R.h;
  for (let i = 0; i < count; i++) {
    const s = seed(base + i * 73), s2 = seed(base + i * 29 + 3);
    const lf = life(phase, s, s2);
    const y = bottom - R.h * lf;
    if (y < fillTop) continue;
    const x = R.x + R.w * (0.15 + 0.7 * s) + Math.sin(lf * 3 * TAU + s * 9) * R.w * 0.08;
    ctx.fillStyle = paintOf(c, strength * Math.max(0, Math.sin(lf * Math.PI)));
    circle(ctx, x, y, sizeDp * (0.6 + 0.8 * s2));
  }
}
const stroke = (ctx, c, alpha, width, path) => {
  ctx.save(); ctx.strokeStyle = paintOf(c, alpha); ctx.lineWidth = width; ctx.lineCap = 'round'; ctx.lineJoin = 'round';
  ctx.stroke(path); ctx.restore();
};
const segPath = (pts) => { const p = new Path2D(); for (let i = 0; i < pts.length; i += 4) { p.moveTo(pts[i], pts[i + 1]); p.lineTo(pts[i + 2], pts[i + 3]); } return p; };
// A repeating linear gradient (Shader.TileMode.REPEAT, or MIRROR) as a pattern: `stops` over one unit
// of x, mapped into place by `m` (a DOMMatrix in the Android matrix's order).
function stripePattern(ctx, colors, stops, mirror, m) {
  const n = 64, cv = document.createElement('canvas');
  cv.width = mirror ? n * 2 : n; cv.height = 1;
  const g2 = cv.getContext('2d');
  const gr = g2.createLinearGradient(0, 0, n, 0);
  colors.forEach((c, i) => gr.addColorStop(stops ? stops[i] : i / (colors.length - 1), col(c)));
  g2.fillStyle = gr; g2.fillRect(0, 0, n, 1);
  if (mirror) { g2.save(); g2.translate(n * 2, 0); g2.scale(-1, 1); g2.fillRect(0, 0, n, 1); g2.restore(); }
  const pat = ctx.createPattern(cv, 'repeat');
  pat.setTransform(m.scale(1 / n, 1));
  return pat;
}
const sideSheen = (ctx, R, fillTop, a) => {
  const g = ctx.createLinearGradient(R.x, 0, R.x + R.w, 0);
  [[0, 0.302], [0.35, 0], [0.72, 0], [1, 0.149]].forEach(([o, k]) => g.addColorStop(o, `rgba(255,255,255,${k})`));
  ctx.save(); ctx.globalAlpha *= a; ctx.fillStyle = g; ctx.fillRect(R.x, fillTop, R.w, R.y + R.h - fillTop); ctx.restore();
};

// --- The wave edge (Tide up, Tide down, Liquid) ---------------------------------------------------------
function waveAt(id, phase, x) {
  const dir = id === 'tide-down' ? -1 : 1;
  const travel = (((phase % 1) + 1) % 1) * TAU * dir;
  const v = (Math.sin(x * TAU + travel) + Math.sin(x * 3.7 * Math.PI - travel) * 0.45) / 1.45;
  return id === 'liquid' ? 0.5 * v : v;
}
const waveShape = (id) => (R, fillTop, phase) => {
  const p = new Path2D(), l = R.x, r = R.x + R.w, b = R.y + R.h, amp = 3.5, step = 6 / DENSITY;
  if (fillTop <= R.y) { p.rect(l - 1, fillTop - 1, R.w + 2, b - fillTop + 2); return p; }
  p.moveTo(l, b); p.lineTo(l, fillTop);
  for (let x = l; x <= r; x += step) p.lineTo(x, fillTop - waveAt(id, phase, (x - l) / Math.max(1, r - l)) * amp);
  p.lineTo(r, fillTop - waveAt(id, phase, 1) * amp); p.lineTo(r, b); p.closePath();
  return p;
};

// --- The fills -------------------------------------------------------------------------------------------
function liquid(ctx, p, R, fillTop, phase, value, a) {
  const crest = 3.5, top = fillTop - crest, w = R.w, b = R.y + R.h, hh = b - top, track = Math.max(1, R.h);
  ground(ctx, [at(p, 0), at(p, 1), at(p, 2)], [0, 0.22, 1], R, top, b, a);
  // The glass body across the width: the deep colour at the sides, clear between, a white streak.
  const deep = at(p, 2), g = ctx.createLinearGradient(R.x, 0, R.x + w, 0);
  [[0, fadeC(deep, 0.38)], [0.2, clearC(deep)], [0.3, argb('#2EFFFFFF')], [0.42, clearC(deep)], [0.78, clearC(deep)], [1, fadeC(deep, 0.38)]]
    .forEach(([o, c]) => g.addColorStop(o, col(c)));
  ctx.save(); ctx.globalAlpha *= a; ctx.fillStyle = g; ctx.fillRect(R.x, top, w, b - top); ctx.restore();
  for (let i = 0; i < 3; i++) {
    const s = seed(i * 41 + 7), turn = phase * TAU * (1 + (i % 2)) + s * TAU;
    glow(ctx, R.x + w * (0.5 + 0.45 * Math.sin(turn)), top + hh * (0.08 + 0.2 * s) + 2.5 * Math.cos(turn * 2), w * 0.7, 8, WHITE, 0.2 * a);
  }
  ground(ctx, [argb('#8CFFFFFF'), argb('#00FFFFFF')], null, R, top, top + 11, a);
  for (let i = 0; i < 12; i++) {
    const s = seed(i * 61 + 7), s2 = seed(i * 89 + 23), lf = life(phase, s, s2);
    const y = b - track * lf;
    if (y < fillTop + 2) continue;
    const x = R.x + w * (0.18 + s * 0.64) + Math.sin(lf * 4 * TAU + s * TAU) * w * 0.06;
    const rad = 1.2 + s2 * 2, f = Math.min(1, lf * 10) * clamp((y - fillTop) / 14) * a;
    ctx.fillStyle = paintOf(WHITE, 0.1 * f); circle(ctx, x, y, rad * 1.8);
    ctx.save(); ctx.strokeStyle = paintOf(WHITE, 0.55 * f); ctx.lineWidth = 0.8; ctx.beginPath(); ctx.arc(x, y, rad, 0, TAU); ctx.stroke(); ctx.restore();
    ctx.fillStyle = paintOf(WHITE, 0.85 * f); circle(ctx, x - rad * 0.35, y - rad * 0.35, rad * 0.32);
  }
}

function waveform(ctx, p, R, fillTop, phase, value, a) {
  const b = R.y + R.h, track = Math.max(1, R.h), hh = Math.max(1, b - fillTop);
  ground(ctx, [argb('#FF161B4A'), argb('#FF090D26')], null, R, fillTop, b, a);
  const cx = R.x + R.w / 2, swing = R.w * (0.12 + 0.26 * value), step = 3;
  for (let i = 0; i < 3; i++) {
    const path = new Path2D();
    const spatial = 17 + i * 6, speed = (i + 1) * TAU * (i === 1 ? -1 : 1);
    let first = true;
    for (let y = b; y >= fillTop - step; y -= step) {
      const k = (b - y) / track;
      const env = Math.max(0, Math.sin(clamp((b - y) / hh) * Math.PI));
      const x = cx + swing * env * Math.sin(k * spatial + phase * speed + i * 2.1);
      if (first) { path.moveTo(x, y); first = false; } else path.lineTo(x, y);
    }
    stroke(ctx, at(p, i), 0.22 * a, 6, path);
    stroke(ctx, at(p, i), 0.95 * a, 1.7, path);
  }
}

function sunrise(ctx, p, R, fillTop, phase, value, a) {
  const w = R.w, b = R.y + R.h;
  ground(ctx, [at(p, 0), at(p, 1), at(p, 2), at(p, 3)], [0, 0.2, 0.52, 1], R, fillTop, b, a);
  const cx = R.x + w / 2, sunY = fillTop + w * 0.55;
  // Ten rays, a sweep gradient turning one ray-spacing a cycle.
  const rays = ctx.createConicGradient(((phase * 360) / 10) * Math.PI / 180, cx, sunY);
  for (let i = 0; i < 10; i++) { rays.addColorStop(i / 10, 'rgba(255,241,201,0)'); rays.addColorStop((i + 0.5) / 10, 'rgba(255,241,201,0.4)'); }
  rays.addColorStop(1, 'rgba(255,241,201,0)');
  ctx.save(); ctx.globalAlpha *= 0.8 * a; ctx.fillStyle = rays; ctx.fillRect(R.x, fillTop, w, b - fillTop); ctx.restore();
  glow(ctx, cx, sunY, w * 1.7, w * 1.7, argb('#FFFFC46B'), 0.6 * a);
  glow(ctx, cx, sunY, w * 0.7, w * 0.7, argb('#FFFFE9B8'), a);
  glow(ctx, cx, sunY, w * 0.3, w * 0.3, argb('#FFFFFBF0'), a);
  motes(ctx, R, fillTop, phase, 10, 13, argb('#FFFFF1C9'), 0.7 * a, 0.75);
}

function spectrum(ctx, p, R, fillTop, phase, value, a, env) {
  const track = Math.max(1, R.h), w = R.w, b = R.y + R.h;
  // The foil: the palette and back as a strip twice the track long, sliding up a period a cycle.
  const key = `foil${track}`;
  let strip = env.cache[key];
  if (!strip) {
    const rows = Math.max(2, Math.min(8192, Math.round(track * 2 * 3)));
    strip = document.createElement('canvas'); strip.width = 2; strip.height = rows;
    const g = strip.getContext('2d'), img = g.createImageData(2, rows);
    const keys = [...p, p[0]];
    for (let row = 0; row < rows; row++) {
      const f = row / 3 / track, gg = clamp(f <= 1 ? f : 2 - f) * (keys.length - 1);
      const i = Math.min(Math.floor(gg), keys.length - 2), c = blendC(keys[i], keys[i + 1], gg - i);
      for (let x = 0; x < 2; x++) { const o = (row * 2 + x) * 4; img.data[o] = c[1]; img.data[o + 1] = c[2]; img.data[o + 2] = c[3]; img.data[o + 3] = 255; }
    }
    g.putImageData(img, 0, 0);
    env.cache[key] = strip;
  }
  const pat = ctx.createPattern(strip, 'repeat');
  pat.setTransform(new DOMMatrix().translate(R.x, b - phase * track * 2).scale(1, -1).scale((w + 2) / 2, 1 / 3));
  ctx.save(); ctx.globalAlpha *= a; ctx.fillStyle = pat; ctx.fillRect(R.x, fillTop, w, b - fillTop); ctx.restore();
  // The glint: a 36 dp band crossing once a cycle, skewed.
  const band = 36, y = b + band - phase * (track + band * 2);
  ctx.save(); ctx.translate(R.x, y); ctx.transform(1, -0.7, 0, 1, 0, 0); ctx.translate(-R.x, -y);
  const gl = ctx.createLinearGradient(0, y - band / 2, 0, y + band / 2);
  gl.addColorStop(0, 'rgba(255,255,255,0)'); gl.addColorStop(0.5, 'rgba(255,255,255,0.451)'); gl.addColorStop(1, 'rgba(255,255,255,0)');
  ctx.globalAlpha *= a; ctx.fillStyle = gl; ctx.fillRect(R.x, y - band / 2, w, band); ctx.restore();
  for (let i = 0; i < 7; i++) {
    const s = seed(i * 17 + 1), s2 = seed(i * 43 + 5), yy = b - track * s2;
    if (yy < fillTop) continue;
    const tw = Math.sin(phase * TAU * (1 + Math.floor(s * 3)) + s2 * TAU);
    if (tw <= 0.2) continue;
    glow(ctx, R.x + w * (0.12 + 0.76 * s), yy, 3.5, 3.5, WHITE, ((tw - 0.2) / 0.8) * 0.9 * a);
  }
}

// Galaxy, Nebula and Ember, as QuickSliderView draws them (the last two over 82 % black).
function galaxy(ctx, p, R, fillTop, phase, value, a) {
  const b = R.y + R.h, w = R.w, track = Math.max(1, R.h);
  const g = ctx.createLinearGradient(0, b, 0, fillTop);
  g.addColorStop(0, col(p[0])); g.addColorStop(1, col(p[1]));
  ctx.save(); ctx.globalAlpha *= a; ctx.fillStyle = g; ctx.fillRect(R.x, fillTop, w, b - fillTop);
  const cx = R.x + w / 2, cy = (fillTop + b) / 2, rg = ctx.createRadialGradient(cx, cy, 0, cx, cy, w * 1.2);
  rg.addColorStop(0, 'rgba(169,139,255,0.251)'); rg.addColorStop(1, 'rgba(169,139,255,0)');
  ctx.fillStyle = rg; ctx.fillRect(R.x, fillTop, w, b - fillTop); ctx.restore();
  for (let layer = 0; layer < 3; layer++) {
    for (let i = 0; i < 16; i++) {
      const s = seed(layer * 997 + i * 37 + 1), s2 = seed(layer * 571 + i * 53 + 9);
      const pos = (s2 + phase * (layer + 1)) % 1, y = b - pos * track;
      if (y < fillTop) continue;
      const tw = 0.55 + 0.45 * Math.sin(phase * TAU * (1 + Math.floor(s * 3)) + s * 10);
      const env = clamp(Math.sin(pos * Math.PI));
      ctx.fillStyle = paintOf(s > 0.8 ? p[3] : p[2], tw * env * (0.45 + layer * 0.27) * a);
      circle(ctx, R.x + w * (0.06 + s * 0.88), y, 0.55 + layer * 0.45);
    }
  }
}
function blackGround(ctx, R, fillTop, a) { ctx.save(); ctx.fillStyle = `rgba(0,0,0,${0.82 * a})`; ctx.fillRect(R.x, fillTop, R.w, R.y + R.h - fillTop); ctx.restore(); }
function nebula(ctx, p, R, fillTop, phase, value, a, env) {
  blackGround(ctx, R, fillTop, a);
  const b = R.y + R.h, w = R.w, view = env.view;
  p.forEach((c, i) => {
    const s = seed(i * 17 + 3), drift = phase * TAU * (1 + Math.floor(s * 2)) + s * 6.28;
    const cx = R.x + w * (0.5 + 0.42 * Math.cos(drift)), cy = fillTop + view.h * (0.5 + 0.42 * Math.sin(drift * 2 + 1.1));
    const radius = view.w * (1.1 + s * 0.6), g = ctx.createRadialGradient(cx, cy, 0, cx, cy, radius);
    g.addColorStop(0, col(c)); g.addColorStop(1, col(clearC(c)));
    ctx.save(); ctx.globalAlpha *= a; ctx.fillStyle = g; ctx.fillRect(R.x, fillTop, w, b - fillTop); ctx.restore();
  });
}
function ember(ctx, p, R, fillTop, phase, value, a, env) {
  blackGround(ctx, R, fillTop, a);
  const b = R.y + R.h, hh = Math.max(1, b - fillTop), w = R.w;
  for (let i = 0; i < 22; i++) {
    const s = seed(i * 97 + 5), s2 = seed(i * 131 + 17);
    const lf = (phase * (1 + Math.floor(s * 2)) + s2) % 1, y = b - hh * lf;
    if (y < fillTop) continue;
    const x = R.x + w * (0.2 + s * 0.6) + Math.sin(lf * 9 + s * 6.28) * env.view.w * 0.16;
    const f = clamp(Math.sin(lf * 3.14159));
    ctx.fillStyle = paintOf(p[clamp(Math.floor(lf * (p.length - 1)), 0, p.length - 1)], f * f * a);
    circle(ctx, x, y, 1.1 + s2 * 1.6);
  }
}

// Silk: a satin ribbon per colour, a five-vertex-wide Gouraud mesh, drawn here band by band.
function silk(ctx, p, R, fillTop, phase, value, a) {
  ground(ctx, [argb('#FF3B1236'), argb('#FF170919')], null, R, fillTop, R.y + R.h, a);
  p.forEach((c, i) => satin(ctx, R, fillTop, phase, i * 2.2, c, a));
}
function satin(ctx, R, fillTop, phase, offset, c, a) {
  const track = Math.max(1, R.h), w = R.w, step = 5, turn = phase * TAU, b = R.y + R.h;
  const start = b + step, rows = clamp(Math.floor((start - (fillTop - step)) / step) + 1, 2, 260);
  const feather = 1.4, cx0 = R.x + w / 2;
  const cols = [clearC(c), fadeC(c, 0.5 * a), fadeC(blendC(c, WHITE, 0.6), 0.95 * a), fadeC(c, 0.5 * a), clearC(c)];
  const rowAt = (i) => {
    const y = start - i * step, k = (b - y) / track;
    const centre = cx0 + w * 0.3 * Math.sin(k * 4.2 + turn + offset);
    const half = w * (0.19 + 0.07 * Math.sin(k * 2.7 - turn + offset));
    const light = centre + half * 0.55 * Math.sin(k * 3.1 + turn * 2 + offset);
    return { y, xs: [centre - half - feather, centre - half, light, centre + half, centre + half + feather] };
  };
  // Each quad row is shaded in thin slices, its five vertices' x interpolated down the slice: the
  // colours along a row are the same at every row, so this is the mesh's Gouraud shading.
  // Slice edges are snapped to the canvas's pixel rows, so neighbouring slices meet without a seam.
  const SLICES = 5, lerpXs = (p0, p1, f) => p0.xs.map((x, j) => x + (p1.xs[j] - x) * f);
  const m = ctx.getTransform(), snap = (y) => (Math.round(y * m.d + m.f) - m.f) / m.d;
  let prev = rowAt(0);
  for (let i = 1; i < rows; i++) {
    const cur = rowAt(i);
    for (let k = 0; k < SLICES; k++) {
      const f0 = k / SLICES, f1 = (k + 1) / SLICES;
      const ya = snap(prev.y + (cur.y - prev.y) * f0), yb = snap(prev.y + (cur.y - prev.y) * f1);
      if (Math.abs(yb - ya) < 1e-6) continue;
      const xa = lerpXs(prev, cur, f0), xb = lerpXs(prev, cur, f1), xs = lerpXs(prev, cur, (f0 + f1) / 2);
      const x0 = xs[0], x1 = xs[4];
      if (x1 - x0 <= 0.01) continue;
      const g = ctx.createLinearGradient(x0, 0, x1, 0);
      xs.forEach((x, j) => g.addColorStop(clamp((x - x0) / (x1 - x0)), col(cols[j])));
      const band = new Path2D();
      band.moveTo(xa[0], ya); band.lineTo(xa[4], ya); band.lineTo(xb[4], yb); band.lineTo(xb[0], yb); band.closePath();
      ctx.save(); ctx.fillStyle = g; ctx.fill(band); ctx.restore();
    }
    prev = cur;
  }
}

function aurora(ctx, p, R, fillTop, phase, value, a) {
  const w = R.w, b = R.y + R.h, hh = Math.max(1, b - fillTop);
  ground(ctx, [argb('#FF0A1633'), argb('#FF041B1F')], null, R, fillTop, b, a);
  for (let i = 0; i < 14; i++) {
    const s = seed(i * 19 + 3), y = b - R.h * seed(i * 57 + 11);
    if (y < fillTop) continue;
    const tw = 0.55 + 0.45 * Math.sin(phase * TAU * (1 + Math.floor(s * 3)) + s * 9);
    ctx.fillStyle = paintOf(WHITE, 0.5 * tw * a);
    circle(ctx, R.x + w * (0.08 + 0.84 * s), y, 0.65);
  }
  const heights = [0.72, 0.5, 0.26, 0.88];
  p.forEach((c, i) => {
    const s = seed(i * 53 + 11), turn = phase * TAU * (1 + (i % 2)) + s * TAU;
    const cx = R.x + w * (0.5 + 0.36 * Math.sin(turn)), cy = fillTop + hh * (heights[i % 4] + 0.06 * Math.sin(turn * 2 + 1.3));
    const br = 0.6 + 0.4 * Math.sin(turn + 0.7);
    glow(ctx, cx, cy, w * (0.34 + 0.1 * s), hh * (0.3 + 0.08 * s), c, 0.75 * br * a);
    glow(ctx, cx, cy + hh * 0.1, w * 0.14, hh * 0.12, blendC(c, WHITE, 0.4), 0.4 * br * a);
  });
}

function plasma(ctx, p, R, fillTop, phase, value, a, env) {
  const COLS = 12, ROWS = 48, t = phase * TAU;
  let img = env.cache.plasma;
  if (!img) { img = document.createElement('canvas'); img.width = COLS; img.height = ROWS; env.cache.plasma = img; }
  const keys = [...p, p[0]];
  const lut = (v) => { const g = (clamp(Math.floor((v + 1) * 127.5), 0, 255) / 255) * (keys.length - 1); const k = Math.min(Math.floor(g), keys.length - 2); return blendC(keys[k], keys[k + 1], g - k); };
  const g2 = img.getContext('2d'), data = g2.createImageData(COLS, ROWS);
  for (let row = 0; row < ROWS; row++) {
    const y = row / ROWS, dy = (y - 0.5) * 3;
    for (let cc = 0; cc < COLS; cc++) {
      const x = cc / COLS, dx = x - 0.5;
      const v = (Math.sin(x * 4 + t) + Math.sin(y * 9 - t) + Math.sin(x * 3 + y * 6 + 2 * t) + Math.sin(Math.sqrt(dx * dx * 16 + dy * dy * 4) * 3 - t)) / 4;
      const c = lut(v), o = (row * COLS + cc) * 4;
      data.data[o] = c[1]; data.data[o + 1] = c[2]; data.data[o + 2] = c[3]; data.data[o + 3] = 255;
    }
  }
  g2.putImageData(data, 0, 0);
  ctx.save(); ctx.globalAlpha *= a; ctx.imageSmoothingEnabled = true; ctx.drawImage(img, R.x, R.y, R.w, R.h); ctx.restore();
  sideSheen(ctx, R, fillTop, a);
}

function hologram(ctx, p, R, fillTop, phase, value, a) {
  const w = R.w, b = R.y + R.h, track = Math.max(1, R.h);
  ground(ctx, [argb('#FF06414F'), argb('#FF021A22')], null, R, fillTop, b, a);
  const period = w * 1.8;
  const foil = stripePattern(ctx, [at(p, 0), at(p, 1), at(p, 2), at(p, 0)], null, true,
    new DOMMatrix().translate(R.x, b).rotate(-38).translate(phase * period * 2, 0).scale(period, 1));
  ctx.save(); ctx.globalAlpha *= 0.5 * a; ctx.fillStyle = foil; ctx.fillRect(R.x, fillTop, w, b - fillTop); ctx.restore();
  // Scan lines: one physical pixel every 3 dp (7 px), creeping up four pitches a cycle.
  const pitch = Math.max(3, Math.floor(3 * DENSITY)) / DENSITY, off = b - phase * pitch * 4;
  ctx.save(); ctx.fillStyle = `rgba(255,255,255,${0.349 * a})`;
  for (let y = off - Math.ceil((off - fillTop) / pitch) * pitch; y < b; y += pitch) if (y >= fillTop) ctx.fillRect(R.x, y, w, 1 / DENSITY);
  ctx.restore();
  const band = 30, y = b + band - phase * (track + band * 2);
  const beam = ctx.createLinearGradient(0, y - band / 2, 0, y + band / 2);
  [[0, clearC(WHITE)], [0.35, fadeC(at(p, 0), 0.55)], [0.5, argb('#B3FFFFFF')], [0.65, fadeC(at(p, 0), 0.55)], [1, clearC(WHITE)]].forEach(([o, c]) => beam.addColorStop(o, col(c)));
  ctx.save(); ctx.globalAlpha *= a; ctx.fillStyle = beam; ctx.fillRect(R.x, fillTop, w, b - fillTop); ctx.restore();
  for (let i = 0; i < 6; i++) {
    const s = seed(i * 23 + 9), lf = life(phase, s, seed(i * 67 + 1)), yy = b - track * lf;
    if (yy < fillTop) continue;
    glow(ctx, R.x + w * (0.15 + 0.7 * s), yy, 3, 3, at(p, 0), 0.9 * Math.max(0, Math.sin(lf * Math.PI)) * a);
  }
}

function sonar(ctx, p, R, fillTop, phase, value, a) {
  const w = R.w, b = R.y + R.h, hh = Math.max(1, b - fillTop), cx = R.x + w / 2;
  ground(ctx, [argb('#FF07343F'), argb('#FF04141F')], null, R, fillTop, b, a);
  glow(ctx, cx, fillTop, w * 1.1, w * 1.1, at(p, 1), 0.5 * a);
  for (let i = 0; i < 4; i++) {
    const lf = (phase + i / 4) % 1, radius = w * 0.18 + lf * (hh + w) * 0.95;
    ring(ctx, cx, fillTop, radius, at(p, 0), 0.95 * (1 - lf) * (1 - lf) * Math.min(1, lf * 7) * a);
  }
  ctx.fillStyle = paintOf(at(p, 0), 0.9 * a); circle(ctx, cx, fillTop, 2);
  motes(ctx, R, fillTop, phase, 8, 31, at(p, 0), 0.45 * a, 0.6);
}

function circuit(ctx, p, R, fillTop, phase, value, a, env) {
  const track = Math.max(1, R.h), b = R.y + R.h;
  ground(ctx, [argb('#FF06261E'), argb('#FF021410')], null, R, fillTop, b, a);
  const key = `board${R.w}x${track}`;
  let board = env.cache[key];
  if (!board) {
    const S = 3, W = R.w, H = track;
    const cv = document.createElement('canvas'); cv.width = Math.round(W * S); cv.height = Math.round(H * S);
    const g = cv.getContext('2d'); g.scale(S, S);
    const n = clamp(Math.floor(W / 9), 2, 5), lanes = Array.from({ length: n }, (_, i) => ((i + 0.5) * W) / n);
    g.strokeStyle = col(at(p, 1)); g.fillStyle = col(at(p, 1)); g.lineWidth = 1; g.lineCap = 'round';
    lanes.forEach((x, li) => {
      g.beginPath(); g.moveTo(x, 0); g.lineTo(x, H); g.stroke();
      let y = H - 10 * (1 + seed(li * 7)), k = 0;
      while (y > 0) {
        const s = seed(li * 131 + k * 17);
        if (s > 0.45) {
          const len = (W / n) * 0.42, ex = clamp(x + (s > 0.72 ? len : -len), 2, W - 2);
          g.beginPath(); g.moveTo(x, y); g.lineTo(ex, y - len * 0.6); g.stroke(); circle(g, ex, y - len * 0.6, 1.4);
        } else circle(g, x, y, 1.6);
        y -= 14 + s * 22; k++;
      }
    });
    board = env.cache[key] = { cv, lanes };
  }
  ctx.save(); ctx.globalAlpha *= a; ctx.drawImage(board.cv, R.x, R.y, R.w, R.h); ctx.restore();
  const margin = 8;
  for (let i = 0; i < 8; i++) {
    const s = seed(i * 37 + 5), lf = life(phase, s, seed(i * 11 + 2));
    const y = b + margin - lf * (track + margin * 2);
    if (y < fillTop - 6) continue;
    const x = R.x + board.lanes[i % board.lanes.length];
    for (let k = 1; k <= 3; k++) { ctx.fillStyle = paintOf(at(p, 0), ((0.3 * (4 - k)) / 3) * a); circle(ctx, x, y + k * 3.2, 1.1); }
    glow(ctx, x, y, 5, 5, at(p, 0), 0.75 * a);
    ctx.fillStyle = paintOf(WHITE, 0.95 * a); circle(ctx, x, y, 1.1);
  }
}

// SliderFill.cellGlow
function cellGlow(id, phase, column, row, columns, rows) {
  const p = ((phase % 1) + 1) % 1;
  if (id === 'dot-matrix') {
    const here = (column / columns) * 0.45 + (1 - row / rows) * 0.55;
    const d = Math.abs((((here - p) % 1) + 1) % 1);
    return Math.max(0, 1 - Math.min(d, 1 - d) / 0.22);
  }
  if (id === 'matrix-rain') {
    const s = seed(column), speed = 1 + Math.floor(s * 2), head = (p * speed + s) % 1, here = 1 - row / rows;
    const behind = (((head - here) % 1) + 1) % 1;
    return behind > 0.4 ? 0 : 1 - behind / 0.4;
  }
  if (id === 'rune') {
    if (seed(column * 31 + row * 7) < 0.72) return 0;
    const here = 1 - row / rows, d = Math.abs((((here - p) % 1) + 1) % 1);
    return Math.max(0, 1 - Math.min(d, 1 - d) / 0.26);
  }
  return 0;
}

function dotMatrix(ctx, p, R, fillTop, phase, value, a) {
  const w = R.w, track = Math.max(1, R.h), b = R.y + R.h;
  ground(ctx, [argb('#FF0B1116'), argb('#FF05080B')], null, R, fillTop, b, a);
  const cols = clamp(Math.floor(w / 5), 2, 10);
  const pitch = Math.max(2, Math.floor((w * DENSITY) / cols)) / DENSITY;
  const left = R.x + (w - cols * pitch) / 2, rows = Math.max(1, Math.floor(track / pitch));
  // The unlit LEDs: a repeating tile of one dot, from the foot.
  ctx.save(); ctx.beginPath(); ctx.rect(R.x, fillTop, w, b - fillTop); ctx.clip(); ctx.fillStyle = col(at(p, 2), a);
  for (let ry = b - rows * pitch - pitch; ry < b + pitch; ry += pitch) for (let cx = left - pitch; cx < R.x + w + pitch; cx += pitch) circle(ctx, cx + pitch / 2, ry + pitch / 2, pitch * 0.3);
  ctx.restore();
  const visible = Math.min(rows, Math.floor((b - fillTop) / pitch) + 1);
  for (let row = 0; row < visible; row++) {
    const cy = b - pitch * (row + 0.5);
    for (let c2 = 0; c2 < cols; c2++) {
      const g = cellGlow('dot-matrix', phase, c2, row, cols, rows);
      if (g <= 0.06) continue;
      const cx = left + pitch * (c2 + 0.5), cc = blendC(at(p, 1), at(p, 0), g);
      if (g > 0.5) { ctx.fillStyle = paintOf(cc, 0.22 * (g - 0.5) * 2 * a); circle(ctx, cx, cy, pitch * 0.55); }
      ctx.fillStyle = paintOf(cc, g * a); circle(ctx, cx, cy, pitch * 0.3);
    }
  }
}

function cyberpunk(ctx, p, R, fillTop, phase, value, a) {
  const w = R.w, b = R.y + R.h, hh = Math.max(1, b - fillTop);
  ground(ctx, [0, 1, 2, 3, 4].map((i) => at(p, i)), [0, 0.16, 0.42, 0.74, 1], R, fillTop, b, a);
  const start = fillTop + hh * 0.34, pitch = 9, span = Math.max(1, b - start);
  for (let y = start + phase * pitch - pitch; y < b; y += pitch) {
    const enter = clamp((y - start) / pitch);
    if (enter <= 0) continue;
    ctx.fillStyle = paintOf(at(p, 4), 0.9 * enter * a);
    ctx.fillRect(R.x, y, w, pitch * (0.1 + 0.55 * clamp((y - start) / span)));
  }
  glow(ctx, R.x + w / 2, fillTop, w * 0.95, 8, at(p, 5), 0.75 * a);
  ctx.fillStyle = paintOf(at(p, 5), a); ctx.fillRect(R.x, fillTop, w, 1.5);
}

function matrixRain(ctx, p, R, fillTop, phase, value, a) {
  const w = R.w, track = Math.max(1, R.h), b = R.y + R.h;
  ground(ctx, [argb('#FF001A0C'), argb('#FF000A04')], null, R, fillTop, b, a);
  const cols = clamp(Math.floor(w / 4), 3, 14), cw = w / cols, ch = 6.5, rows = Math.max(1, Math.floor(track / ch));
  for (let row = 0; row < rows; row++) {
    const cy = b - ch * (row + 0.5);
    if (cy < fillTop - ch) break;
    for (let c2 = 0; c2 < cols; c2++) {
      const g = cellGlow('matrix-rain', phase, c2, row, cols, rows);
      if (g <= 0.03) continue;
      const cx = R.x + cw * (c2 + 0.5), head = g > 0.93;
      const cc = head ? at(p, 0) : blendC(at(p, 2), at(p, 1), g);
      // A glyph: a stem and a tick, re-seeded six times a cycle.
      const s = seed(c2 * 131 + row * 17 + Math.floor(phase * 6) * 7);
      ctx.fillStyle = paintOf(cc, (head ? 1 : g * (0.4 + 0.6 * g)) * a);
      const st = Math.max(cw * 0.16, 1), top = cy - ch * 0.34, bot = cy + ch * 0.34, sx = cx + (s - 0.5) * cw * 0.3;
      ctx.fillRect(sx - st / 2, top, st, bot - top);
      const ty = top + (bot - top) * (0.2 + 0.6 * seed(c2 * 7 + row * 3 + 1));
      if (s > 0.5) ctx.fillRect(sx - cw * 0.26, ty - st / 2, cw * 0.26, st); else ctx.fillRect(sx, ty - st / 2, cw * 0.26, st);
      if (head) glow(ctx, cx, cy, cw * 1.3, ch * 1.4, at(p, 1), 0.55 * a);
    }
  }
}

const RUNES = [
  [[0.4, 0.18, 0.4, 0.82], [0.4, 0.32, 0.68, 0.2], [0.4, 0.5, 0.68, 0.38]],
  [[0.5, 0.18, 0.72, 0.5, 0.5, 0.82, 0.28, 0.5, 0.5, 0.18]],
  [[0.5, 0.82, 0.5, 0.18], [0.3, 0.38, 0.5, 0.18, 0.7, 0.38]],
  [[0.3, 0.22, 0.7, 0.78], [0.7, 0.22, 0.3, 0.78]],
  [[0.36, 0.18, 0.64, 0.4, 0.36, 0.6, 0.64, 0.82]],
  [[0.32, 0.18, 0.32, 0.82], [0.68, 0.18, 0.68, 0.82], [0.32, 0.24, 0.68, 0.52], [0.68, 0.24, 0.32, 0.52]],
];
function rune(ctx, p, R, fillTop, phase, value, a) {
  const w = R.w, track = Math.max(1, R.h), b = R.y + R.h;
  ground(ctx, [argb('#FF2E1A0C'), argb('#FF140A04')], null, R, fillTop, b, a);
  const cols = clamp(Math.floor(w / 11), 1, 5), cw = w / cols, rows = Math.max(1, Math.floor(track / cw)), ch = track / rows;
  for (let row = 0; row < rows; row++) {
    const top = b - ch * (row + 1);
    if (top + ch < fillTop) break;
    for (let c2 = 0; c2 < cols; c2++) {
      if (seed(c2 * 31 + row * 7) < 0.55) continue;
      const g = cellGlow('rune', phase, c2, row, cols, rows), left = R.x + cw * c2;
      if (g > 0.5) glow(ctx, left + cw / 2, top + ch / 2, cw * 0.8, ch * 0.8, at(p, 1), 0.8 * (g - 0.5) * a);
      const glyph = RUNES[clamp(Math.floor(seed(c2 * 13 + row * 29) * 6), 0, 5)];
      const path = new Path2D();
      for (const s of glyph) { path.moveTo(left + s[0] * cw, top + s[1] * ch); for (let k = 2; k + 1 < s.length; k += 2) path.lineTo(left + s[k] * cw, top + s[k + 1] * ch); }
      stroke(ctx, blendC(at(p, 2), at(p, 0), g), (0.35 + 0.65 * g) * a, 1.3, path);
    }
  }
  motes(ctx, R, fillTop, phase, 8, 57, at(p, 0), 0.6 * a, 0.6);
}

function fireflies(ctx, p, R, fillTop, phase, value, a) {
  const w = R.w, b = R.y + R.h, hh = Math.max(1, b - fillTop), track = Math.max(1, R.h);
  ground(ctx, [argb('#FF0F2B23'), argb('#FF08170F'), argb('#FF030805')], [0, 0.55, 1], R, fillTop, b, a);
  glow(ctx, R.x + w / 2, b, w * 1.5, Math.min(hh, 70), at(p, 1), 0.2 * a);
  const turn = phase * TAU, reach = 16;
  for (let i = 0; i < 14; i++) {
    const s = seed(i * 47 + 5), s2 = seed(i * 83 + 19);
    const x = R.x + w * (0.22 + 0.56 * s) + Math.sin(turn * (1 + (i % 2)) + s2 * TAU) * w * 0.26;
    const y = b - track * (0.04 + 0.92 * s2) + Math.sin(turn * (1 + (i % 3)) + s * TAU) * reach;
    if (y < fillTop - 10) continue;
    const beat = Math.sin(turn * (1 + Math.floor(s * 2)) + s * 11);
    if (beat <= 0) continue;
    const f = beat * beat * beat, cc = at(p, i);
    glow(ctx, x, y, 10, 10, cc, 0.6 * f * a);
    glow(ctx, x, y, 2.6, 2.6, blendC(cc, WHITE, 0.55), f * a);
  }
}

function snowfall(ctx, p, R, fillTop, phase, value, a) {
  const w = R.w, track = Math.max(1, R.h), b = R.y + R.h;
  ground(ctx, [argb('#FF1D2D50'), argb('#FF0C1429')], null, R, fillTop, b, a);
  glow(ctx, R.x + w / 2, fillTop, w * 1.3, 46, at(p, 1), 0.22 * a);
  glow(ctx, R.x + w / 2, b + 6, w * 1.4, 26, at(p, 0), 0.4 * a);
  const margin = 6, span = track + margin * 2, turn = phase * TAU, per = [16, 11, 6];
  for (let layer = 0; layer < 3; layer++) {
    const cc = at(p, 2 - layer);
    for (let i = 0; i < per[layer]; i++) {
      const s = seed(layer * 1009 + i * 41 + 7), s2 = seed(layer * 613 + i * 67 + 13);
      const y = R.y - margin + ((s2 + phase * (layer + 1)) % 1) * span;
      if (y < fillTop - margin) continue;
      const x = R.x + w * (0.08 + 0.84 * s) + Math.sin(turn * (layer + 1) + s * TAU) * w * (0.05 + 0.04 * layer);
      if (layer === 0) { ctx.fillStyle = paintOf(cc, 0.45 * a); circle(ctx, x, y, 0.7); }
      else if (layer === 1) { ctx.fillStyle = paintOf(cc, 0.75 * a); circle(ctx, x, y, 1.15); }
      else glow(ctx, x, y, 3.6, 3.6, cc, 0.95 * a);
    }
  }
}

const ECG = [0, 0, 0.12, 0, 0.15, -0.14, 0.18, 0, 0.3, 0, 0.325, 0.16, 0.36, -1, 0.4, 0.42, 0.43, 0, 0.56, 0, 0.6, -0.2, 0.64, -0.28, 0.68, -0.2, 0.72, 0, 1, 0];
function ecgAt(u) {
  for (let k = 2; k < ECG.length; k += 2) {
    if (u <= ECG[k]) { const u0 = ECG[k - 2], f = clamp((u - u0) / Math.max(1e-4, ECG[k] - u0)); return ECG[k - 1] + (ECG[k + 1] - ECG[k - 1]) * f; }
  }
  return 0;
}
function heartbeat(ctx, p, R, fillTop, phase, value, a) {
  const w = R.w, b = R.y + R.h, hh = Math.max(1, b - fillTop), cx = R.x + w / 2;
  ground(ctx, [argb('#FF071A11'), argb('#FF020A06')], null, R, fillTop, b, a);
  // The monitor's grid: hairlines every 8 dp (21 px) from the foot.
  const pitch = Math.max(4, Math.floor(8 * DENSITY)) / DENSITY, hair = 1 / DENSITY;
  ctx.save(); ctx.fillStyle = col(fadeC(at(p, 1), 0.16), a);
  for (let y = b; y > fillTop - pitch; y -= pitch) if (y >= fillTop) ctx.fillRect(R.x, y, w, hair);
  for (let x = R.x; x < R.x + w; x += pitch) ctx.fillRect(x, fillTop, hair, b - fillTop);
  ctx.restore();
  const period = 64, amp = w * 0.36, trail = 72;
  const path = new Path2D(); path.moveTo(cx, b);
  for (let beat = 0; beat * period < R.h; beat++) for (let k = 2; k < ECG.length; k += 2) path.lineTo(cx + amp * ECG[k + 1], b - (beat + ECG[k]) * period);
  const head = b - phase * (hh + trail);
  const beats = (b - head) / period - 0.36, since = (beats - Math.floor(beats)) * period;
  const bloom = Math.max(0, 1 - since / (period * 0.5)) ** 2, spikeY = head + since;
  if (spikeY >= fillTop) glow(ctx, cx, spikeY, w * 1.3, 34, at(p, 2), 0.5 * bloom * a);
  const c0 = at(p, 0), tg = ctx.createLinearGradient(0, head - trail * 0.02, 0, head - trail * 0.02 + trail);
  [[0, clearC(c0)], [0.02, c0], [0.3, fadeC(c0, 0.4)], [1, clearC(c0)]].forEach(([o, c]) => tg.addColorStop(o, col(c)));
  ctx.save(); ctx.strokeStyle = tg; ctx.lineCap = 'round'; ctx.lineJoin = 'round';
  ctx.globalAlpha = 0.35 * a; ctx.lineWidth = 5; ctx.stroke(path);
  ctx.globalAlpha = a; ctx.lineWidth = 1.6; ctx.stroke(path); ctx.restore();
  const u = (b - head) / period, hx = cx + amp * ecgAt(u - Math.floor(u));
  glow(ctx, hx, head, 9, 9, c0, (0.55 + 0.45 * bloom) * a);
  glow(ctx, hx, head, 2.5, 2.5, WHITE, 0.9 * a);
}

function neon(ctx, p, R, fillTop, phase, value, a) {
  const w = R.w, b = R.y + R.h, cx = R.x + w / 2;
  ground(ctx, [argb('#FF1A0C26'), argb('#FF090410')], null, R, fillTop, b, a);
  const pitch = 13, hw = Math.min(w * 0.3, 16), hh = hw * 0.55;
  const rows = Math.min(Math.floor((b - fillTop) / pitch) + 1, 64), n = p.length;
  const chevron = (pts, y) => pts.push(cx - hw, y + hh / 2, cx, y - hh / 2, cx, y - hh / 2, cx + hw, y + hh / 2);
  for (let k = 0; k < Math.min(n, rows); k++) {
    const pts = [];
    for (let i = k; i < rows; i += n) chevron(pts, b - pitch * (i + 0.6));
    stroke(ctx, at(p, k), 0.16 * a, 1.5, segPath(pts));
  }
  for (let i = 0; i < rows; i++) {
    const behind = (((phase - i / 4) % 1) + 1) % 1;
    let g = 1 - behind / 0.6;
    if (g <= 0.02) continue;
    const s = seed(i * 7 + 3);
    if (s > 0.88 && Math.sin(phase * TAU * 23 + s * 40) > 0.55) g *= 0.3;
    const y = b - pitch * (i + 0.6), cc = at(p, i), pts = [];
    glow(ctx, cx, y, w * 0.95, pitch * 1.5, cc, 0.4 * g * a);
    chevron(pts, y);
    const path = segPath(pts);
    stroke(ctx, cc, 0.3 * g * a, 5, path);
    stroke(ctx, blendC(cc, WHITE, 0.6 * g), (0.3 + 0.7 * g) * a, 1.5, path);
  }
}

function swell(layer, x, phase) {
  const t = phase * TAU * (layer % 2 === 0 ? 1 : -1);
  return (Math.sin(x * TAU * (0.8 + 0.3 * layer) + t * (1 + (layer % 2)) + layer * 1.7) + 0.4 * Math.sin(x * TAU * 1.9 - t * 2 + layer)) / 1.4;
}
function ocean(ctx, p, R, fillTop, phase, value, a) {
  const w = R.w, b = R.y + R.h, hh = Math.max(1, b - fillTop);
  ground(ctx, [argb('#FF0B4775'), argb('#FF05284F'), argb('#FF020D22')], [0, 0.3, 1], R, fillTop, b, a);
  glow(ctx, R.x + w / 2, fillTop, w * 1.4, Math.min(hh, 60), at(p, 0), 0.22 * a);
  const amp = 2.6, step = 3, depth = 38;
  for (let layer = 2; layer >= 0; layer--) {
    const top = fillTop + 4 + (2 - layer) * 9.5, cc = at(p, layer + 1);
    const body = new Path2D(), crest = new Path2D();
    body.moveTo(R.x, b);
    let x = R.x, first = true;
    for (;;) {
      const y = top + amp * swell(layer, clamp((x - R.x) / w), phase);
      if (first) { crest.moveTo(x, y); first = false; } else crest.lineTo(x, y);
      body.lineTo(x, y);
      if (x >= R.x + w) break;
      x = Math.min(x + step, R.x + w);
    }
    body.lineTo(R.x + w, b); body.closePath();
    const g = ctx.createLinearGradient(0, top - amp, 0, top - amp + depth);
    g.addColorStop(0, col(cc)); g.addColorStop(0.45, col(fadeC(cc, 0.25))); g.addColorStop(1, col(clearC(cc)));
    ctx.save(); ctx.globalAlpha *= a; ctx.fillStyle = g; ctx.fill(body); ctx.restore();
    const foam = (0.6 - 0.18 * layer) * a;
    stroke(ctx, at(p, 0), 0.25 * foam, 3, crest);
    stroke(ctx, at(p, 0), foam, 1, crest);
  }
  const front = fillTop + 23;
  for (let i = 0; i < 4; i++) {
    const s = seed(i * 29 + 17), tw = Math.sin(phase * TAU * (2 + (i % 2)) + s * TAU);
    if (tw <= 0.5) continue;
    const xa = 0.1 + 0.8 * s;
    glow(ctx, R.x + w * xa, front + amp * swell(0, xa, phase), 4, 2.5, WHITE, (tw - 0.5) * 1.6 * a);
  }
  motes(ctx, R, front + 6, phase, 8, 91, at(p, 0), 0.3 * a, 0.55);
}

function gradient(ctx, p, R, fillTop, phase, value, a) {
  const w = R.w, b = R.y + R.h, hh = Math.max(1, b - fillTop), track = Math.max(1, R.h), span = track * 0.55;
  const flow = stripePattern(ctx, p.map((c) => [255, c[1], c[2], c[3]]), null, true,
    new DOMMatrix().translate(R.x, b).rotate(-72).translate(phase * span * 2, 0).scale(span, 1));
  ctx.save(); ctx.globalAlpha *= a; ctx.fillStyle = flow; ctx.fillRect(R.x, fillTop, w, b - fillTop); ctx.restore();
  const turn = phase * TAU;
  glow(ctx, R.x + w / 2 + w * 0.35 * Math.sin(turn), fillTop + hh * (0.5 + 0.32 * Math.sin(turn * 2 + 1.2)), w * 1.1, Math.max(Math.min(hh * 0.45, 90), w * 0.6), WHITE, 0.2 * a);
  ground(ctx, [argb('#59FFFFFF'), argb('#00FFFFFF')], null, R, fillTop, fillTop + 12, a);
  sideSheen(ctx, R, fillTop, a);
}

function confetti(ctx, p, R, fillTop, phase, value, a) {
  const w = R.w, b = R.y + R.h, hh = Math.max(1, b - fillTop), track = Math.max(1, R.h);
  ground(ctx, [argb('#FF26174F'), argb('#FF0D0824')], null, R, fillTop, b, a);
  glow(ctx, R.x + w / 2, fillTop, w * 1.3, Math.min(hh, 70), at(p, 0), 0.16 * a);
  const margin = 8, span = track + margin * 2, turn = phase * TAU, length = 3.6;
  for (let i = 0; i < 26; i++) {
    const s = seed(i * 59 + 3), s2 = seed(i * 97 + 31), s3 = seed(i * 131 + 7), speed = 1 + Math.floor(s * 2);
    const y = R.y - margin + ((s2 + phase * speed) % 1) * span;
    if (y < fillTop - margin) continue;
    const x = R.x + w * (0.1 + 0.8 * s3) + Math.sin(turn * speed * 2 + s * TAU) * w * 0.12;
    const spin = s * TAU + turn * (1 + Math.floor(s2 * 3)) * (s3 > 0.5 ? 1 : -1);
    const flip = Math.abs(Math.cos(turn * (2 + Math.floor(s3 * 3)) + s2 * TAU));
    const half = (length * (0.15 + 0.85 * flip)) / 2, dx = Math.cos(spin) * half, dy = Math.sin(spin) * half;
    const cc = at(p, i), shine = flip ** 4;
    if (shine > 0.85) glow(ctx, x, y, 5, 5, cc, (0.35 * (shine - 0.85)) / 0.15 * a);
    ctx.save(); ctx.strokeStyle = paintOf(blendC(cc, WHITE, 0.55 * shine), (0.75 + 0.25 * shine) * a); ctx.lineWidth = 2; ctx.lineCap = 'butt';
    ctx.beginPath(); ctx.moveTo(x - dx, y - dy); ctx.lineTo(x + dx, y + dy); ctx.stroke(); ctx.restore();
  }
}

function warp(ctx, p, R, fillTop, phase, value, a) {
  const w = R.w, b = R.y + R.h, hh = Math.max(1, b - fillTop), cx = R.x + w / 2, vy = fillTop + hh * 0.38;
  ground(ctx, [argb('#FF080B24'), argb('#FF020309')], null, R, fillTop, b, a);
  const reach = Math.max(hh, w) * 1.3 + 16;
  glow(ctx, cx, vy, w * 1.1, Math.max(Math.min(hh * 0.5, 80), w * 0.6), at(p, 2), 0.5 * a);
  glow(ctx, cx, vy, w * 0.35, w * 0.35, at(p, 1), 0.7 * a);
  for (let i = 0; i < 34; i++) {
    const s = seed(i * 67 + 11), lf = life(phase, s, seed(i * 23 + 29));
    const f = Math.min(1, lf * 5) * Math.min(1, (1 - lf) * 6);
    if (f <= 0.01) continue;
    const angle = seed(i * 151 + 5) * TAU, ex = Math.cos(angle) * 0.55, ey = Math.sin(angle);
    const d = lf * lf * reach, tail = d * (1 - 0.45 * lf), y1 = vy + ey * tail, y2 = vy + ey * d;
    if (Math.max(y1, y2) < fillTop) continue;
    ctx.save(); ctx.strokeStyle = paintOf(at(p, i % 2), f * (0.35 + 0.65 * lf) * a); ctx.lineWidth = 0.6 + 1.4 * lf; ctx.lineCap = 'round';
    ctx.beginPath(); ctx.moveTo(cx + ex * tail, y1); ctx.lineTo(cx + ex * d, y2); ctx.stroke(); ctx.restore();
  }
}

const spike = (ph, atv, rise, fall) => (ph < atv - rise || ph > atv + fall ? 0 : ph < atv ? 1 - (atv - ph) / rise : 1 - (ph - atv) / fall);
function storm(ctx, p, R, fillTop, phase, value, a) {
  const w = R.w, b = R.y + R.h, hh = Math.max(1, b - fillTop), track = Math.max(1, R.h);
  ground(ctx, [argb('#FF1E2538'), argb('#FF0C0F1A'), argb('#FF05060B')], [0, 0.45, 1], R, fillTop, b, a);
  const bolt = Math.max(spike(phase, 0.3, 0.004, 0.035), 0.75 * spike(phase, 0.345, 0.004, 0.05));
  const sheet = Math.max(bolt, 0.4 * spike(phase, 0.72, 0.02, 0.06)), turn = phase * TAU;
  for (let i = 0; i < 3; i++) {
    const s = seed(i * 71 + 13), x = R.x + w * (0.2 + 0.3 * i) + Math.sin(turn + s * TAU) * w * 0.12, y = fillTop + 8 + 6 * s;
    glow(ctx, x, y, w * 0.75, 20, argb('#FF394461'), 0.85 * a);
    glow(ctx, x, y + 6, w * 0.9, 30, at(p, 1), 0.6 * sheet * a);
  }
  if (sheet > 0.01) { ctx.fillStyle = paintOf(at(p, 0), 0.14 * sheet * a); ctx.fillRect(R.x, fillTop, w, b - fillTop); }
  if (bolt > 0.02) {
    const depth = Math.min(hh * 0.85, 160), top = fillTop + 2, path = new Path2D();
    let x = R.x + w * 0.55, forkX = x, forkY = top;
    path.moveTo(x, top);
    for (let k = 1; k <= 12; k++) {
      x = clamp(x + (seed(k * 31 + 7) - 0.5) * w * 0.45, R.x + w * 0.15, R.x + w - w * 0.15);
      const y = top + (depth * k) / 12;
      path.lineTo(x, y);
      if (k === 4) { forkX = x; forkY = y; }
    }
    path.moveTo(forkX, forkY);
    for (let k = 1; k <= 4; k++) { forkX = clamp(forkX - w * 0.07 + (seed(k * 53 + 3) - 0.5) * w * 0.12, R.x, R.x + w); path.lineTo(forkX, forkY + depth * 0.06 * k); }
    stroke(ctx, at(p, 1), 0.35 * bolt * a, 5, path);
    stroke(ctx, at(p, 0), bolt * a, 1.4, path);
  }
  const length = 7, slant = length * 0.28, span = track + length * 2, pts = [];
  for (let i = 0; i < 44; i++) {
    const s = seed(i * 43 + 1), y = R.y - length + ((seed(i * 79 + 17) + phase * (5 + Math.floor(s * 3))) % 1) * span;
    if (y < fillTop) continue;
    const x = R.x - slant + (w + slant * 2) * seed(i * 13 + 5);
    pts.push(x + slant, y - length, x, y);
  }
  if (pts.length) { const c2 = at(p, 2); ctx.save(); ctx.strokeStyle = col(c2, (0.55 + 0.45 * sheet) * a); ctx.lineWidth = 0.8; ctx.lineCap = 'round'; ctx.stroke(segPath(pts)); ctx.restore(); }
}

function fireworks(ctx, p, R, fillTop, phase, value, a) {
  const w = R.w, b = R.y + R.h, hh = Math.max(1, b - fillTop);
  ground(ctx, [argb('#FF0D1030'), argb('#FF040512')], null, R, fillTop, b, a);
  const blast = w * 0.5 + 10;
  for (let bi = 0; bi < 3; bi++) {
    const u = (((phase - bi / 3) % 1) + 1) % 1;
    if (u >= 0.62) continue;
    const cx = R.x + w * (0.3 + 0.4 * seed(bi * 11 + 1)), cy = fillTop + hh * (0.2 + 0.4 * seed(bi * 17 + 3));
    const cc = at(p, bi), accent = at(p, bi + 1);
    if (u < 0.14) {
      const k = u / 0.14, start = b + 8, y = start + (cy - start) * (1 - (1 - k) * (1 - k));
      ctx.save(); ctx.strokeStyle = paintOf(cc, 0.45 * a); ctx.lineWidth = 1.2; ctx.lineCap = 'round';
      ctx.beginPath(); ctx.moveTo(cx, y); ctx.lineTo(cx, y + 10 * (1 - k * 0.5)); ctx.stroke(); ctx.restore();
      glow(ctx, cx, y, 4, 4, cc, 0.9 * a);
      continue;
    }
    const e = (u - 0.14) / (0.62 - 0.14), left = 1 - e, radius = blast * (1 - left ** 3), dying = left * Math.sqrt(left), drop = e * e * 12;
    glow(ctx, cx, cy, blast * 1.4, blast * 1.4, cc, (0.55 * left * left + 0.15 * dying) * a);
    for (let j = 0; j < 14; j++) {
      const angle = (j * TAU) / 14 + seed(bi * 7 + j * 3) * 0.35, ca = Math.cos(angle), sa = Math.sin(angle);
      const ox = cx + ca * radius, oy = cy + sa * radius + drop, ink = j % 3 === 0 ? accent : cc;
      ctx.save(); ctx.strokeStyle = paintOf(ink, 0.55 * dying * a); ctx.lineWidth = 1.2; ctx.lineCap = 'round';
      ctx.beginPath(); ctx.moveTo(cx + ca * radius * 0.72, cy + sa * radius * 0.72 + drop * 0.7); ctx.lineTo(ox, oy); ctx.stroke(); ctx.restore();
      const glitter = e > 0.5 ? 0.55 + 0.45 * Math.sin(e * 60 + j * 2.3) : 1;
      ctx.fillStyle = paintOf(blendC(ink, WHITE, 0.6 * left), dying * glitter * a); circle(ctx, ox, oy, 1.1);
    }
  }
}

// Stripes: soft diagonal bands of the track colour drifting a band a cycle, a gloss at the level.
function stripes(ctx, p, R, fillTop, phase, value, a, env) {
  const ink = argb(env.trackHex || '#FF1C1C20'), period = 12;
  const pat = stripePattern(ctx, [clearC(ink), fadeC(ink, 0.2), fadeC(ink, 0.2), clearC(ink), clearC(ink)], [0, 0.18, 0.45, 0.63, 1], false,
    new DOMMatrix().translate(R.x, fillTop).rotate(-45).translate(phase * period, 0).scale(period, 1));
  ctx.save(); ctx.globalAlpha *= a; ctx.fillStyle = pat; ctx.fillRect(R.x, fillTop, R.w, R.y + R.h - fillTop); ctx.restore();
  ground(ctx, [argb('#40FFFFFF'), argb('#00FFFFFF')], null, R, fillTop, fillTop + 12, a);
}

// --- Registration ---------------------------------------------------------------------------------------
// backdrop: roughly what each picture averages to, for the feedback's ink and the number over it.
const FILLS = {
  liquid: [liquid, '#5A8AF0'], waveform: [waveform, '#121741'], sunrise: [sunrise, '#C06A5C'], spectrum: [spectrum, '#CCC7CA'],
  galaxy: [galaxy, '#24164E'], silk: [silk, '#4A1E3C'], aurora: [aurora, '#1E4650'], plasma: [plasma, '#A58385'],
  hologram: [hologram, '#2A6670'], nebula: [nebula, '#4A3A5A'], ember: [ember, '#3A2A24'], sonar: [sonar, '#0E3A44'],
  circuit: [circuit, '#0A2A22'], 'dot-matrix': [dotMatrix, '#0E1C22'], cyberpunk: [cyberpunk, '#B04A60'], 'matrix-rain': [matrixRain, '#04200E'],
  rune: [rune, '#2E1C0E'], fireflies: [fireflies, '#12261C'], snowfall: [snowfall, '#1C2A4A'], heartbeat: [heartbeat, '#0A2014'],
  neon: [neon, '#1E0C2A'], ocean: [ocean, '#0A3A60'], gradient: [gradient, '#E58AB2'], confetti: [confetti, '#22164A'],
  warp: [warp, '#0C1030'], storm: [storm, '#161C2C'], fireworks: [fireworks, '#0E1230'],
};
for (const [id, [draw, backdrop]] of Object.entries(FILLS)) {
  const palette = FILL_PALETTES[id];
  PICTORIAL[id] = {
    backdrop,
    draw: (ctx, R, fillTop, phase, value, a, env) => {
      // The user's animation colours, if on: the palette recoloured once and kept.
      let p = palette;
      if (env.custom?.length) { const key = 'pal' + env.custom.join(); p = env.cache[key] ??= recolour(palette, env.custom); }
      draw(ctx, p, R, fillTop, phase, value, a, env);
    },
  };
}
PICTORIAL['tide-up'] = { shape: waveShape('tide-up'), draw: () => {} };
PICTORIAL['tide-down'] = { shape: waveShape('tide-down'), draw: () => {} };
PICTORIAL.liquid.shape = waveShape('liquid');
PICTORIAL.stripes = { draw: (ctx, R, fillTop, phase, value, a, env) => stripes(ctx, null, R, fillTop, phase, value, a, env) };
