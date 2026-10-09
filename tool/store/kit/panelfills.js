// The Quick panel as the settings screens draw it: the preview's real panel and every live tile of
// their picture rows (FillTile / MiniFill). It is quickpanel.js's QuickPanel with what those screens
// show on top: every Pixels pattern (PixelArt + PixelFill), both Effort looks with the level's name
// (EffortArt), the Glimmer's four looks, all seven flourishes at the top (LevelFeedbackArt), the
// panel entrances played on the layer in 3D, and a window with no entrance room for the tiles.
// Lengths are dp in the panel's own box; canvases are drawn at 3 px per dp.
import { clamp, lerp } from './core.js';
import { QuickPanel, THEMES, SHADER_FILLS, SURGE_FILLS, argb, css, blendARGB, luminance, pickInk, outline, entranceFrame, DPR } from './quickpanel.js';

const TAU = Math.PI * 2;
const wrap = (x) => ((x % 1) + 1) % 1;
const hexA = (hex, a) => { const [, r, g, b] = argb(hex); return `rgba(${r},${g},${b},${a})`; };

// SliderFill.pseudoRandom: Kotlin's wrapping Int maths.
export function pseudoRandom(n) {
  let x = (Math.imul(n, 374761393) + 668265263) | 0;
  x = Math.imul(x ^ (x >> 13), 1274126177);
  return ((x ^ (x >> 16)) & 0x7fffffff) / 2147483647;
}

// SliderFill.recolour: palette slot i of n takes the colour i/(n-1) of the way along the user's
// colours (a linear RGB blend), keeping its own alpha; a slot darker than 0.12 (a ground) is kept.
// `base` and the result are [a, r, g, b]; `custom` is a list of '#RRGGBB'.
export function recolour(base, custom) {
  if (!custom || !custom.length) return base;
  const user = custom.map((h) => argb(h));
  const n = base.length;
  return base.map((c, i) => {
    if ((0.2126 * c[1] + 0.7152 * c[2] + 0.0722 * c[3]) / 255 < 0.12) return c;
    const t = n === 1 ? 0 : i / (n - 1), g = t * (user.length - 1);
    const k = clamp(Math.floor(g), 0, Math.max(user.length - 2, 0));
    const rgb = user.length === 1 ? user[0].slice(1)
      : [1, 2, 3].map((j) => clamp(Math.floor(user[k][j] + (user[k + 1][j] - user[k][j]) * clamp(g - k) + 0.5), 0, 255));
    return [c[0], ...rgb];
  });
}

// --- Level feedback (LevelFeedback) ----------------------------------------------------------------
export const levelAt = (v) => clamp(Math.ceil(clamp(v) * 5 - 1e-4) - 1, 0, 4);
export const pace = (v, follow = true) => (follow ? 0.55 + 1.05 * clamp(v) : 1);
export const breath = (t) => 0.5 - 0.5 * Math.cos((t / 1.6) * TAU);
const fade = (s, len) => (s >= 0 && s < len ? Math.pow(1 - s / len, 2) : 0);
const SHEEN_S = [3.4, 2.7, 2.1, 1.6, 1.2], SHEEN_A = [0.08, 0.12, 0.16, 0.2, 0.26];
export const FLOURISHES = ['burst', 'ripple', 'shine', 'sparkle', 'confetti', 'neon', 'pulse'];
const EFFORT_P = ['#8E9AAF', '#4C8DFF', '#8B5CF6', '#FF7A45', '#EB5F57', '#F58B57', '#FAC35F', '#91C882', '#82AADC', '#9B82C8', '#C882B4'];
const SPECTRUM = EFFORT_P.slice(4);
const NEON = ['#FF3DB8', '#A85CFF', '#2EE6FF', '#A85CFF', '#FF3DB8'];
const DARK_INK = '#14161B';
const twinkle = (i, t) => {
  const u = wrap(t / 1.8 + pseudoRandom(i * 7919 + 13));
  if (u >= 0.5) return 0;
  const s = Math.sin((Math.PI * u) / 0.5);
  return s * s;
};
const neonStrike = (s) => {
  if (s < 0 || s >= 0.7) return 1;
  const k = s / 0.7;
  return k < 0.08 ? 1 : k < 0.2 ? 0.1 : k < 0.3 ? 0.75 : k < 0.46 ? 0.2 : k < 0.56 ? 1 : k < 0.64 ? 0.35 : 1;
};
const heartbeat = (t) => {
  const x = ((t % 1.2) + 1.2) % 1.2;
  const throb = (at, w) => Math.exp(-(((x - at) / w) ** 2));
  return clamp(throb(0.12, 0.07) + 0.65 * throb(0.36, 0.08));
};
// The relative luminance ContentInk and ColorUtils use, of '#RRGGBB'.
const lum = (hex) => luminance(argb(hex));

// --- Pixels (PixelFill) ------------------------------------------------------------------------------
export const PIXEL_PATTERNS = ['spectrum', 'steady', 'breathe', 'sweep', 'pendulum', 'snake', 'twinkle', 'checkers',
  'meter', 'rain', 'rainbow', 'aurora', 'plasma', 'embers', 'thermal', 'confetti', 'chromatic', 'candy', 'ripple'];
export const PIXEL_CYCLE_MS = { spectrum: 2000, steady: 1, breathe: 3200, sweep: 1800, pendulum: 2400, snake: 6000, rain: 2400,
  twinkle: 4000, checkers: 2400, meter: 1600, rainbow: 4000, aurora: 8000, plasma: 6000, embers: 3000, thermal: 3000,
  confetti: 3000, chromatic: 2400, candy: 1800, ripple: 2400 };
const PIXEL_RAMPS = {
  meter: ['#3DE68A', '#FFC23D', '#FF4F61'], aurora: ['#2BFF88', '#2BD8FF', '#9B6BFF'],
  embers: ['#5A0E00', '#E0301E', '#FF8A1E', '#FFE27A'], thermal: ['#1A0633', '#7A1A8C', '#E0301E', '#FFB01E', '#FFF4D6'],
  candy: ['#FF4F8B', '#FFD23F', '#3FC4FF'],
};
const pixelInk = (id) => (['steady', 'breathe', 'sweep', 'pendulum', 'snake', 'twinkle', 'checkers'].includes(id) ? 'fill'
  : ['rain', 'spectrum', 'rainbow', 'plasma', 'confetti', 'ripple'].includes(id) ? 'hue'
    : ['meter', 'aurora', 'embers', 'thermal', 'candy'].includes(id) ? 'ramp' : 'rgb');
const ringDistance = (a, b) => { const d = Math.abs(wrap(a - b)); return Math.min(d, 1 - d); };
const falloff = (d, w) => Math.max(0, 1 - d / w);
const gaussian = (x, w) => Math.exp(-((x / w) ** 2));
const plasmaField = (u, v, p) => (Math.sin(TAU * (v + p)) + Math.sin(TAU * (u * 1.5 - p)) + Math.sin(TAU * ((u + v) * 0.8 + 2 * p))) / 3;
const heat = (col, row, u, p) => {
  const seed = pseudoRandom(col * 17 + row * 5);
  const beats = 1 + Math.floor(seed * 2);
  const flicker = 0.25 * (0.5 + 0.5 * Math.sin(TAU * (p * beats + seed)));
  return clamp(Math.pow(1 - u, 1.3) * 0.85 + flicker);
};
const channelHead = (u, p, off) => Math.pow(falloff(ringDistance(u, wrap(p - off)), 0.22), 2);
const rippleDistance = (col, row, cols, rows) => {
  const cx = (cols - 1) / 2, cy = (rows - 1) / 2;
  const far = Math.max(0.5, Math.hypot(cx, cy));
  return clamp(Math.hypot(col - cx, row - cy) / far);
};

function pixelLevel(id, col, row, cols, rows, phase) {
  const p = wrap(phase), u = (row + 0.5) / rows, v = (col + 0.5) / cols;
  let value;
  switch (id) {
    case 'steady': case 'rainbow': case 'candy': value = 1; break;
    case 'breathe': value = 0.5 - 0.5 * Math.cos(TAU * p); break;
    case 'sweep': { const behind = wrap(p - u); value = behind < 0.35 ? (1 - behind / 0.35) ** 2 : 0; break; }
    case 'pendulum': value = gaussian(u - (0.5 - 0.5 * Math.cos(TAU * p)), 0.12); break;
    case 'snake': {
      const along = row % 2 === 0 ? col : cols - 1 - col;
      const index = row * cols + along, count = rows * cols;
      const behind = wrap((p * count - index) / count) * count;
      const tail = Math.max(3, count * 0.22);
      value = behind < tail ? 1 - behind / tail : 0;
      break;
    }
    case 'rain': {
      const seed = pseudoRandom(col);
      const head = wrap(p * (1 + Math.floor(seed * 2)) + seed);
      const behind = wrap(head - (1 - u));
      value = behind < 0.4 ? (1 - behind / 0.4) ** 1.5 : 0;
      break;
    }
    case 'twinkle': {
      const seed = pseudoRandom(col * 97 + row * 31);
      value = Math.sin(Math.PI * wrap(p * (1 + Math.floor(seed * 3)) + seed)) ** 8;
      break;
    }
    case 'checkers': { const swap = clamp(0.5 + 2 * Math.sin(TAU * p)); value = (col + row) % 2 === 0 ? swap : 1 - swap; break; }
    case 'meter': {
      const seed = pseudoRandom(col * 7 + 3), speed = 1 + Math.floor(seed * 2);
      const height = 0.15 + 0.85 * (0.5 + 0.35 * Math.sin(TAU * (p * speed + seed)) + 0.15 * Math.sin(TAU * (p * speed * 2 + seed * 1.7)));
      value = u <= height ? 1 : 0;
      break;
    }
    case 'spectrum': value = falloff(ringDistance(u, p), 0.28) ** 2; break;
    case 'aurora': value = 0.35 + 0.65 * (0.5 + 0.5 * Math.sin(TAU * (u * 2 + p) + v * 3)); break;
    case 'plasma': value = 0.55 + 0.45 * Math.sin(TAU * p + Math.PI * plasmaField(u, v, p)); break;
    case 'embers': value = clamp(heat(col, row, u, p) * 1.2); break;
    case 'thermal': value = 0.3 + 0.7 * (0.5 + 0.5 * Math.sin(TAU * (u * 1.2 - p))); break;
    case 'confetti': {
      const seed = pseudoRandom(col * 13 + row * 7);
      value = (1 - wrap(p * (1 + Math.floor(seed * 2)) + seed)) ** 3;
      break;
    }
    case 'chromatic': value = Math.max(channelHead(u, p, 0), channelHead(u, p, 0.08), channelHead(u, p, 0.16)); break;
    case 'ripple': value = Math.max(0, 1 - wrap(rippleDistance(col, row, cols, rows) - p) / 0.3) ** 2; break;
    default: value = 1;
  }
  return clamp(value);
}

function pixelTone(id, col, row, cols, rows, phase) {
  const p = wrap(phase), u = (row + 0.5) / rows, v = (col + 0.5) / cols;
  let value = 0;
  switch (id) {
    case 'rain': value = pseudoRandom(col); break;
    case 'spectrum': value = u * 0.85; break;
    case 'rainbow': value = wrap(u * 0.8 - p); break;
    case 'plasma': value = 0.5 + 0.5 * plasmaField(u, v, p); break;
    case 'confetti': value = pseudoRandom(col * 29 + row * 11 + 5); break;
    case 'ripple': value = rippleDistance(col, row, cols, rows); break;
    case 'meter': value = u; break;
    case 'aurora': value = 0.5 + 0.5 * (0.6 * Math.sin(TAU * (u * 1.3 - p) + v * 2.2) + 0.4 * Math.sin(TAU * (u * 0.7 + p) + 1.7 + v * 1.3)); break;
    case 'embers': value = heat(col, row, u, p); break;
    case 'thermal': value = 0.5 + 0.5 * Math.sin(TAU * (u * 1.2 - p)); break;
    case 'candy': value = ((((row + col + Math.floor(p * 3)) % 3) + 3) % 3) / 2; break;
    default: value = 0;
  }
  return clamp(value);
}

const rgbOf = (hex) => argb(hex).slice(1);
const mixRgb = (a, b, f) => a.map((c, i) => clamp(Math.floor(c + (b[i] - c) * f + 0.5), 0, 255));
function hsvRgb(hue, s, v) {
  const hh = (((hue % 360) + 360) % 360) / 60;
  const c = v * s, x = c * (1 - Math.abs((hh % 2) - 1)), m = v - c;
  const [r, g, b] = [[c, x, 0], [x, c, 0], [0, c, x], [0, x, c], [x, 0, c], [c, 0, x]][Math.min(5, Math.floor(hh))];
  return [r, g, b].map((k) => Math.floor(clamp(k + m) * 255 + 0.5));
}
function pixelColor(id, col, row, cols, rows, phase, fillRgb, own = null) {
  const ink = pixelInk(id);
  if (ink === 'fill') return fillRgb;
  if (ink === 'hue') {
    const t = pixelTone(id, col, row, cols, rows, phase);
    if (!own) return hsvRgb(t * 360, 0.72, 1);
    // Round the user's colours as a loop (PixelFill.cyclic).
    const g = wrap(t) * own.length, k = Math.floor(g) % own.length;
    return mixRgb(own[k], own[(k + 1) % own.length], g - Math.floor(g));
  }
  if (ink === 'ramp') {
    const ramp = own || PIXEL_RAMPS[id].map(rgbOf);
    const g = pixelTone(id, col, row, cols, rows, phase) * (ramp.length - 1);
    const k = Math.min(Math.floor(g), ramp.length - 2);
    return mixRgb(ramp[k], ramp[k + 1], g - k);
  }
  const u = (row + 0.5) / rows, p = wrap(phase);
  const base = 0.3;
  const k3 = [0, 0.08, 0.16].map((off) => base + (1 - base) * channelHead(u, p, off));
  if (own && own.length >= 3) return [0, 1, 2].map((j) => clamp(Math.floor(own[0][j] * k3[0] + own[1][j] * k3[1] + own[2][j] * k3[2]), 0, 255));
  return k3.map((k) => Math.floor(clamp(k) * 255 + 0.5));
}

// PixelArt.draw: the grid over the whole track, lit below fillTop, the glow under it.
export function drawPixelGrid(ctx, R, fillTop, phase, style, fillRgb, alpha, glowCanvas, custom = null) {
  if (R.w <= 1 || R.h <= 1 || alpha <= 0.004) return;
  const id = PIXEL_PATTERNS.includes(style.pattern) ? style.pattern : 'spectrum';
  const own = custom && custom.length ? custom.map(rgbOf) : null;
  const cols = style.columns ?? 4, gap = style.gap ?? 0.18, roundness = style.roundness ?? 0.35, glow = style.glow ?? 0.6, rest = style.rest ?? 0.1;
  const pitchX = R.w / cols;
  const rows = Math.max(1, Math.floor(R.h / pitchX));
  const pitchY = R.h / rows;
  const cells = [];
  for (let row = 0; row < rows; row++) {
    const bottom = R.y + R.h - row * pitchY;
    const lit = clamp((bottom - fillTop) / pitchY);
    for (let col = 0; col < cols; col++) {
      const on = 0.35 + 0.65 * pixelLevel(id, col, row, cols, rows, phase);
      cells.push({ row, col, bottom, color: pixelColor(id, col, row, cols, rows, phase, fillRgb, own), b: rest + (on - rest) * lit, g: on * lit * glow });
    }
  }
  if (glow > 0.01 && glowCanvas) {
    glowCanvas.width = cols; glowCanvas.height = rows;
    const gx = glowCanvas.getContext('2d');
    const img = gx.createImageData(cols, rows);
    for (const c of cells) {
      const i = ((rows - 1 - c.row) * cols + c.col) * 4;
      img.data[i] = c.color[0]; img.data[i + 1] = c.color[1]; img.data[i + 2] = c.color[2]; img.data[i + 3] = Math.floor(clamp(c.g) * 255 + 0.5);
    }
    gx.putImageData(img, 0, 0);
    ctx.save(); ctx.globalAlpha *= alpha; ctx.imageSmoothingEnabled = true; ctx.drawImage(glowCanvas, R.x, R.y, R.w, R.h); ctx.restore();
  }
  const ix = (pitchX * gap) / 2, iy = (pitchY * gap) / 2;
  const radius = (roundness * Math.min(pitchX - ix * 2, pitchY - iy * 2)) / 2;
  for (const c of cells) {
    ctx.save();
    ctx.globalAlpha *= clamp(c.b * alpha);
    ctx.fillStyle = `rgb(${c.color.join(',')})`;
    ctx.beginPath();
    ctx.roundRect(R.x + c.col * pitchX + ix, c.bottom - pitchY + iy, pitchX - ix * 2, pitchY - iy * 2, radius);
    ctx.fill();
    ctx.restore();
  }
}

// --- Glimmer (GlimmerArt) ----------------------------------------------------------------------------
// fills.js's drawGlimmer, copied here so its three colours can be the user's (GlimmerFill.paletteWith):
// P = [the twinkle's peak, the lit dots at the level, the lit dots where the light begins].
const GLIMMER_P = ['#DCD4FF', '#9A89C4', '#8878BE'];
function drawGlimmer(ctx, R, fillTop, value, time, fillColor = '#FFFFFF', alpha = 1, { handle = true, stops = true } = {}, P = GLIMMER_P) {
  const pitch = 5.6;
  const cols = clamp(Math.round(R.w / pitch), 2, 10);
  const px = R.w / cols;
  const rows = Math.floor(R.h / pitch);
  const oy = (R.h - rows * pitch) / 2;
  const r = 0.25 * pitch;
  const energy = 0.6 + 0.4 * value;
  const peak = rgbOf(P[0]), litL = rgbOf(P[1]), lit0 = rgbOf(P[2]);
  const shimmerY = R.y + R.h - ((time % 2.4) / 2.4) * (R.h + 40) + 20;
  for (let row = 0; row < rows; row++) {
    const cy = R.y + oy + pitch * (row + 0.5);
    for (let col = 0; col < cols; col++) {
      const cx = R.x + px * (col + 0.5);
      let color, a;
      if (cy < fillTop) { color = [255, 255, 255]; a = 0.045; }
      else {
        const k = clamp((R.y + R.h - cy) / Math.max(1, R.y + R.h - fillTop));
        const sm = k * k * (3 - 2 * k);
        const seed = Math.abs(Math.sin((row * 13.1 + col * 7.7) * 12.9898) * 43758.5453) % 1;
        const tw = Math.max(0, Math.sin(TAU * ((time * (1 + Math.floor(seed * 3))) / 2.4 + seed)));
        const twinkle = tw > 0.68 ? (tw - 0.68) / 0.32 : 0;
        const shimmer = Math.exp(-Math.pow((cy - shimmerY) / (0.16 * R.h), 2));
        color = lit0.map((c, i) => clamp(Math.round(c + (litL[i] - c) * sm + (peak[i] - litL[i]) * twinkle * 0.8), 0, 255));
        a = (0.045 + (0.52 - 0.045) * sm + 0.3 * twinkle + 0.25 * shimmer * sm) * energy;
      }
      ctx.save(); ctx.globalAlpha *= clamp(a) * alpha; ctx.fillStyle = `rgb(${color.join(',')})`;
      ctx.beginPath(); ctx.arc(cx, cy, r, 0, TAU); ctx.fill(); ctx.restore();
    }
  }
  if (handle) {
    const hh = Math.min(clamp(0.8 * R.w, 10, 30), R.h / 3);
    const cy = clamp(fillTop, R.y + hh / 2, R.y + R.h - hh / 2);
    if (stops) {
      for (let i = 0; i <= 5; i++) {
        const sy = R.y + hh / 2 + (R.h - hh) * (1 - i / 5);
        if (Math.abs(sy - cy) < hh * 0.7) continue;
        ctx.save(); ctx.globalAlpha *= 0.3 * alpha; ctx.fillStyle = fillColor; ctx.beginPath(); ctx.arc(R.x + R.w / 2, sy, 0.375 * pitch, 0, TAU); ctx.fill(); ctx.restore();
      }
    }
    ctx.save(); ctx.globalAlpha *= alpha; ctx.fillStyle = fillColor;
    ctx.beginPath(); ctx.roundRect(R.x, cy - hh / 2, R.w, hh, Math.min(R.w, hh) / 2); ctx.fill(); ctx.restore();
  }
}

// --- Effort (EffortArt) --------------------------------------------------------------------------------
export const EFFORT_NAMES = ['Low', 'High', 'Extra high', 'Max', 'Ultra max'];
const EFFORT_SHORT = ['Low', 'High', 'Extra', 'Max', 'Ultra'];
const EFFORT_SWEEP_S = [3.2, 2.5, 1.9, 1.4, 1.05], EFFORT_SWEEP_A = [0.22, 0.28, 0.34, 0.42, 0.5];
// Effort's colours, the user's when they have set some (EffortFill.paletteWith): `P` is 11 '#RRGGBB'.
const effortInk = (level, P = EFFORT_P) => (level >= 4 ? DARK_INK : lum(P[level]) > 0.3 ? DARK_INK : '#FFFFFF');
function effortNameColour(level, time, P = EFFORT_P) {
  if (level < 4) return P[level];
  const SP = P.slice(4);
  const t = ((time % 2.4) / 2.4) * 7;
  const k = Math.floor(t) % 7;
  const a = rgbOf(SP[k]), b = rgbOf(SP[(k + 1) % 7]);
  return `rgb(${mixRgb(a, b, t - Math.floor(t)).join(',')})`;
}
// The level's colour, or at Ultra max the spectrum flowing up the track (a REPEAT gradient).
function effortPaint(ctx, level, R, time, P = EFFORT_P) {
  if (level < 4) return P[level];
  const SP = P.slice(4);
  const span = R.h * 0.9, flow = (time % 2.4) / 2.4;
  const y0 = R.y - flow * span - span, periods = 4;
  const g = ctx.createLinearGradient(0, y0, 0, y0 + span * periods);
  for (let p = 0; p < periods; p++) for (let i = 0; i <= 7; i++) g.addColorStop(clamp((p + i / 7) / periods), SP[i % 7]);
  return g;
}
function softBand(ctx, x0, x1, centre, height, colour, strength) {
  if (strength <= 0.004 || height <= 0) return;
  const g = ctx.createLinearGradient(0, centre - height / 2, 0, centre + height / 2);
  g.addColorStop(0, hexA(colour, 0)); g.addColorStop(0.5, hexA(colour, 1)); g.addColorStop(1, hexA(colour, 0));
  ctx.save(); ctx.globalAlpha *= strength; ctx.fillStyle = g; ctx.fillRect(x0, centre - height / 2, x1 - x0, height); ctx.restore();
}
function radialGlow(ctx, cx, cy, rx, ry, colour, strength) {
  if (strength <= 0.004 || rx <= 0.5 || ry <= 0.5) return;
  const [, r, g, b] = argb(colour.startsWith('#') ? colour : '#FFFFFF');
  ctx.save();
  ctx.translate(cx, cy); ctx.scale(rx, ry);
  const gr = ctx.createRadialGradient(0, 0, 0, 0, 0, 1);
  gr.addColorStop(0, `rgba(${r},${g},${b},1)`); gr.addColorStop(0.45, `rgba(${r},${g},${b},${82 / 255})`); gr.addColorStop(1, `rgba(${r},${g},${b},0)`);
  ctx.globalAlpha *= strength; ctx.fillStyle = gr; ctx.fillRect(-1, -1, 2, 2);
  ctx.restore();
}
function sparkPath() {
  const p = new Path2D();
  p.moveTo(0, -1); p.quadraticCurveTo(0.14, -0.14, 1, 0); p.quadraticCurveTo(0.14, 0.14, 0, 1);
  p.quadraticCurveTo(-0.14, 0.14, -1, 0); p.quadraticCurveTo(-0.14, -0.14, 0, -1); p.closePath();
  return p;
}
const SPARK = typeof Path2D !== 'undefined' ? sparkPath() : null;

// EffortArt.draw. `time` is the picker's own clock (s); `arrival` 0..1 since the level was reached.
export function drawEffortArt(ctx, R, fillTop, value, time, { look = 'steps', labels = true } = {}, alpha = 1,
  { labelTop = R.y, labelBottom = R.y + R.h, ghost = '#FFFFFF', arrival = 1, palette = null } = {}) {
  if (R.w <= 1 || R.h <= 1 || alpha <= 0.004) return;
  const P = palette || EFFORT_P;
  const level = levelAt(value);
  const ink = effortInk(level, P);
  const sweep = (time / EFFORT_SWEEP_S[level]) % 1;
  const bottom = R.y + R.h, left = R.x, right = R.x + R.w, lit = bottom - fillTop;
  const segment = R.h / 5;
  const backGlow = () => {
    if (level < 3 || lit <= 1) return;
    const b = 0.5 + 0.5 * Math.sin((TAU * (time % 1.6)) / 1.6);
    const colour = level >= 4 ? effortNameColour(level, time, P) : P[level];
    const hex = colour.startsWith('#') ? colour : '#' + colour.match(/\d+/g).slice(0, 3).map((n) => (+n).toString(16).padStart(2, '0')).join('');
    radialGlow(ctx, (left + right) / 2, (fillTop + bottom) / 2, R.w * 0.9, lit / 2 + 12, hex, ((level >= 4 ? 0.3 : 0.22) + 0.22 * b) * alpha);
  };
  const edge = (x0, x1, y) => { ctx.save(); ctx.globalAlpha *= 0.9 * alpha; ctx.fillStyle = '#FFFFFF'; ctx.beginPath(); ctx.roundRect(x0, y, x1 - x0, 1.6, 0.8); ctx.fill(); ctx.restore(); };
  const sparks = () => {
    if (level < 4 || lit <= 6) return;
    ctx.save(); ctx.beginPath(); ctx.rect(left, fillTop, right - left, lit); ctx.clip(); ctx.fillStyle = '#FFFFFF';
    for (let i = 0; i < 6; i++) {
      const life = ((time + pseudoRandom(i * 13 + 1) * 1.6) % 1.6) / 1.6;
      const size = Math.sin(Math.PI * life) ** 2;
      if (size < 0.03) continue;
      const x = left + (right - left) * (0.2 + 0.6 * pseudoRandom(i * 7 + 3));
      const y = bottom - lit * (0.08 + 0.84 * pseudoRandom(i * 5 + 2));
      const radius = (2 + 1.8 * pseudoRandom(i * 3 + 4)) * size;
      ctx.save(); ctx.globalAlpha *= 0.9 * size * alpha; ctx.translate(x, y); ctx.rotate((life * 90 * Math.PI) / 180); ctx.scale(radius, radius); ctx.fill(SPARK); ctx.restore();
    }
    ctx.restore();
  };
  if (look === 'dots') {
    const round = (right - left) / 2;
    ctx.save(); ctx.globalAlpha *= 0.14 * alpha; ctx.fillStyle = ghost; ctx.beginPath(); ctx.roundRect(left, R.y, R.w, R.h, round); ctx.fill(); ctx.restore();
    backGlow();
    if (fillTop < bottom) {
      ctx.save(); ctx.beginPath(); ctx.rect(left, fillTop, R.w, bottom - fillTop); ctx.clip();
      ctx.save(); ctx.globalAlpha *= alpha; ctx.fillStyle = effortPaint(ctx, level, R, time, P); ctx.beginPath(); ctx.roundRect(left, R.y, R.w, R.h, round); ctx.fill(); ctx.restore();
      const bandH = segment * 1.2;
      softBand(ctx, left, right, bottom + bandH / 2 - (bottom - fillTop + bandH) * sweep, bandH, '#FFFFFF', EFFORT_SWEEP_A[level] * alpha);
      if (arrival < 1) softBand(ctx, left, right, fillTop, segment * 1.4, '#FFFFFF', 0.6 * (1 - arrival) ** 2 * alpha);
      ctx.restore();
    }
    for (let k = 1; k < 5; k++) {
      const y = bottom - k * segment, onLit = y > fillTop;
      ctx.save(); ctx.globalAlpha *= (onLit ? 0.32 : 0.3) * alpha; ctx.fillStyle = onLit ? ink : ghost;
      ctx.fillRect(left + round * 0.35, y - 0.5, R.w - round * 0.7, 1); ctx.restore();
    }
    if (fillTop > R.y + 2 && fillTop < bottom - 2) edge(left + round * 0.3, right - round * 0.3, fillTop);
    sparks();
  } else {
    const gap = clamp(segment * 0.1, 1.5, 3.5);
    const round = Math.min(3, (segment - gap) / 2);
    backGlow();
    const bandH = segment * 1.1;
    const bandC = bottom + bandH / 2 - (lit + bandH) * sweep;
    for (let k = 0; k < 5; k++) {
      const top = bottom - (k + 1) * segment + gap / 2, bot = bottom - k * segment - gap / 2;
      ctx.save(); ctx.globalAlpha *= 0.14 * alpha; ctx.fillStyle = ghost; ctx.beginPath(); ctx.roundRect(left, top, R.w, bot - top, round); ctx.fill(); ctx.restore();
      if (fillTop >= bot) continue;
      ctx.save(); ctx.beginPath(); ctx.rect(left, Math.max(top, fillTop), R.w, bot - Math.max(top, fillTop)); ctx.clip();
      ctx.save(); ctx.globalAlpha *= (k === level ? 1 : 0.74) * alpha; ctx.fillStyle = effortPaint(ctx, level, R, time, P); ctx.beginPath(); ctx.roundRect(left, top, R.w, bot - top, round); ctx.fill(); ctx.restore();
      softBand(ctx, left, right, bandC, bandH, '#FFFFFF', EFFORT_SWEEP_A[level] * alpha);
      if (k === level && arrival < 1) {
        ctx.save(); ctx.globalAlpha *= 0.55 * (1 - arrival) ** 2 * alpha; ctx.fillStyle = '#FFFFFF'; ctx.beginPath(); ctx.roundRect(left, top, R.w, bot - top, round); ctx.fill(); ctx.restore();
      }
      ctx.restore();
    }
    const stopTop = bottom - (level + 1) * segment + gap / 2, stopBottom = bottom - level * segment - gap / 2;
    if (fillTop > stopTop + 1.5 && fillTop < stopBottom - 1) edge(left + round * 0.4, right - round * 0.4, fillTop);
    sparks();
  }
  if (labels) effortReadout(ctx, R, fillTop, time, level, arrival, look === 'dots', labelTop, labelBottom, ink, alpha, P);
}

function effortReadout(ctx, R, fillTop, time, level, arrival, withDots, labelTop, labelBottom, ink, a, P = EFFORT_P) {
  const margin = 7, bottom = R.y + R.h;
  const highest = Math.max(R.y + 4, labelTop), lowest = Math.min(bottom - 4, labelBottom);
  const insideTop = Math.max(fillTop + margin, highest);
  const roomInside = lowest - insideTop, roomAbove = Math.min(fillTop - margin, lowest) - highest;
  const base = clamp(R.w * 0.36, 8, 13);
  const font = (size) => `500 ${size}px Roboto`;
  const measure = (name, size, dotted) => {
    ctx.save(); ctx.font = font(size); ctx.letterSpacing = `${0.02 * size}px`;
    const w = ctx.measureText(name).width; ctx.restore();
    if (!dotted) return w;
    const dot = size * 0.19;
    return dot * 2.9 * 4 + dot * 2 + size * 0.5 + w;
  };
  let name = '', size = base, length = 0, dotted = withDots, inside = false, found = false;
  for (let c = 0; c < 5; c++) {
    name = c % 2 === 0 && c < 4 ? EFFORT_NAMES[level] : EFFORT_SHORT[level];
    size = c < 2 ? base : Math.max(base * 0.82, 7);
    dotted = withDots && c < 4;
    length = measure(name, size, dotted);
    if (length <= roomInside) { inside = true; found = true; break; }
    if (length <= roomAbove) { inside = false; found = true; break; }
  }
  if (!found) return;
  const start = inside ? insideTop + length : Math.min(fillTop - margin, lowest);
  const dot = size * 0.19, pitch = dot * 2.9;
  const dotsLength = dotted ? pitch * 4 + dot * 2 : 0, spacing = dotted ? size * 0.5 : 0;
  const colour = inside ? ink : effortNameColour(level, time, P);
  const shown = arrival * arrival * (3 - 2 * arrival);
  const slide = (1 - shown) * 5, f = (0.25 + 0.75 * shown) * a;
  const cx = R.x + R.w / 2;
  if (inside) {
    const across = Math.min(size * 1.5, R.w * 0.72), ends = size * 0.35;
    ctx.save(); ctx.globalAlpha *= a; ctx.fillStyle = effortPaint(ctx, level, R, time, P);
    ctx.beginPath(); ctx.roundRect(cx - across / 2, start + slide - length - ends, across, length + ends * 2, across / 2); ctx.fill(); ctx.restore();
  }
  ctx.save();
  ctx.translate(cx, start + slide); ctx.rotate(-Math.PI / 2);
  let x = 0;
  if (dotted) {
    for (let k = 0; k < 5; k++) {
      const dx = x + dot + k * pitch;
      if (k <= level) {
        const grow = k === level ? 1 + 0.7 * (1 - shown) : 1;
        ctx.save(); ctx.globalAlpha *= f; ctx.fillStyle = colour; ctx.beginPath(); ctx.arc(dx, 0, dot * grow, 0, TAU); ctx.fill(); ctx.restore();
      } else {
        ctx.save(); ctx.globalAlpha *= 0.5 * f; ctx.strokeStyle = colour; ctx.lineWidth = dot * 0.45; ctx.beginPath(); ctx.arc(dx, 0, dot * 0.8, 0, TAU); ctx.stroke(); ctx.restore();
      }
    }
    x += dotsLength + spacing;
  }
  ctx.font = font(size); ctx.letterSpacing = `${0.02 * size}px`;
  ctx.textAlign = 'left'; ctx.textBaseline = 'middle';
  ctx.globalAlpha *= f; ctx.fillStyle = colour;
  ctx.fillText(name, x, 0);
  ctx.restore();
}

// --- The panel ------------------------------------------------------------------------------------------
// fill: { kind: 'solid' | 'pixels' | 'shader' | 'surge' | 'effort' | 'glimmer' | <pictorial id>,
//   effect, look, pattern, columns, gap, roundness, glow, rest, speed, scale, detail, bright, grain,
//   edge, trail, labels, handle, stops }. Pictorial fills come from `PICTORIAL` when registered.
export const PICTORIAL = {};

export class FillPanel extends QuickPanel {
  constructor(phone, bar, opts = {}) {
    super(phone, bar, opts);
    if (opts.room === 0) {
      // A tile's panel: nothing in a tile moves out of its rect, so no room round it.
      this.win = { ...this.panelRect };
      this.canvas.width = Math.round(this.win.w * DPR);
      this.canvas.height = Math.round(this.win.h * DPR);
      Object.assign(this.canvas.style, { left: this.win.x + 'px', top: this.win.y + 'px', width: this.win.w + 'px', height: this.win.h + 'px' });
      this.setFill(this.o.fill);
    }
    this.perspective = opts.perspective ?? 1296;
    this.glowCanvas = document.createElement('canvas');
    this.picCache = {};
    // The user's animation colours, over the program fill's own four.
    if (this.o.custom && this.palette) this.palette = recolour(this.palette, this.o.custom);
    const hexOf = (c) => '#' + c.slice(1).map((n) => n.toString(16).padStart(2, '0')).join('').toUpperCase();
    this.effortPalette = this.o.custom ? recolour(EFFORT_P.map(argb), this.o.custom).map(hexOf) : null;
    this.glimmerPalette = this.o.custom ? recolour(GLIMMER_P.map(argb), this.o.custom).map(hexOf) : GLIMMER_P;
  }

  // The opening animation on the layer, as PanelEntrance does it: the canvas element transformed.
  layer(s) {
    const st = this.canvas.style;
    if (s.entrance == null || s.entrance >= 1) { st.transform = ''; st.opacity = ''; st.clipPath = ''; return; }
    const f = entranceFrame(s.entranceId ?? this.o.entrance, s.entrance, this.edgeOnLeft);
    const PR = this.panelRect, W = this.win;
    st.transformOrigin = `${PR.x - W.x + PR.w * f.ox}px ${PR.y - W.y + PR.h * f.oy}px`;
    st.transform = `perspective(${this.perspective}px) translate(${f.tx}px,${f.ty}px) rotateX(${f.rx}deg) rotateY(${f.ry}deg) rotate(${f.rz}deg) scale(${f.sx},${f.sy})`;
    st.opacity = f.alpha;
    st.clipPath = f.revealFrom > 0 || f.revealTo < 1
      ? `inset(${PR.y - W.y + PR.h * f.revealFrom}px 0 ${W.h - (PR.y - W.y + PR.h * f.revealTo)}px 0)` : '';
  }

  // What the lit part averages to, for the feedback's ink and the number's over the fill.
  backdrop(kind, fillC, trackNow) {
    if (kind === 'shader' || kind === 'surge') {
      const pl = this.palette;
      return kind === 'surge' ? [255, ...[1, 2, 3].map((i) => pl[0][i] * 0.65 + pl[1][i] * 0.35)]
        : [255, ...[1, 2, 3].map((i) => (pl[0][i] + pl[1][i] + pl[2][i] + pl[3][i]) / 4)];
    }
    if (kind === 'pixels' || kind === 'glimmer') return blendARGB(trackNow, [255, 150, 120, 200], 0.35);
    const pic = PICTORIAL[kind];
    if (pic?.backdrop) return argb(pic.backdrop);
    return fillC;
  }

  // state: { e=1, value, t (feedback clock s), fillTime (shader / surge / glimmer / effort clock s),
  //   phase (the fill phase 0..1 for Pixels and the pictorial fills), fullS, stepS, arrival,
  //   grabbed, entrance (0..1 or null), committed=true }
  draw(s) {
    const ctx = this.canvas.getContext('2d');
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    this.layer(s);
    // At 0 the panel is the bar it grows out of, drawn exactly (QuickSliderView's frame 0).
    const e = clamp(s.e ?? 1);
    if (s.hidden) return;
    const o = this.o, P = this.bar.preset, B = s.barRect || this.bar.rect, PR = this.panelRect;
    const R = { x: lerp(B.x, PR.x, e), y: lerp(B.y, PR.y, e), w: lerp(B.w, PR.w, e), h: lerp(B.h, PR.h, e) };
    const theme = THEMES[o.theme] || THEMES.solid;
    const shape = P.shape === 'tab' || this.panelShape === 'tab' ? (this.panelShape === 'tab' || e < 0.5 ? 'tab' : 'rounded') : 'rounded';
    const flare = lerp(P.flare ?? 0.29, this.panelFlare, e);
    const radii = P.radii.map((r, i) => Math.min(lerp(r, this.panelRadii[i], e), Math.min(R.w, R.h) / 2));
    const out = outline(R, { shape, flare, radii, edgeOnLeft: this.edgeOnLeft });
    ctx.setTransform(DPR, 0, 0, DPR, -this.win.x * DPR, -this.win.y * DPR);

    const trackC = theme.forced ? argb(theme.forced) : argb(o.track);
    let fillC = argb(o.fillColor);
    if (theme.forced && Math.abs(luminance(fillC) - luminance(trackC)) < 0.25) fillC = theme.pale ? argb('#FF15161A') : argb('#FFFFFFFF');
    const trackNow = blendARGB(argb(P.color), trackC, e);
    ctx.fillStyle = css(trackNow, theme.forced ? 1 : (theme.surfaceAlpha ?? 1));
    ctx.fill(out);

    const ca = clamp((e - 0.18) / (0.55 - 0.18));
    const value = clamp(s.value ?? 0);
    const fillTop = R.y + R.h - R.h * value;
    const kind = o.fill.kind;
    const ft = s.fillTime ?? s.t ?? 0;
    const committed = (s.committed ?? true) && ca > 0.01;
    const overTrack = pickInk(trackNow, fillC);
    const backdrop = this.backdrop(kind, fillC, trackNow);
    const ink = kind === 'glimmer' ? '#FFFFFF' : luminance(backdrop) > 0.6 ? DARK_INK : '#FFFFFF';
    const fb = o.feedback;
    const feedbackOn = kind !== 'effort' && (fb.follow || fb.low || fb.full);
    const whole = ['pixels', 'effort', 'glimmer', 'shader', 'surge'].includes(kind);

    ctx.save();
    ctx.clip(out);
    if (theme.lit) this.glass(ctx, R, theme.pale, e, 'under');
    if (whole) {
      if (!committed) {
        this.contents(ctx, R, value, overTrack, ca);
      } else {
        let overFill = pickInk(backdrop, trackNow);
        if (kind === 'shader' || kind === 'surge') this.programFill(ctx, R, fillTop, ft, ca);
        if (kind === 'pixels') drawPixelGrid(ctx, R, fillTop, s.phase ?? 0, o.fill, fillC.slice(1), ca, this.glowCanvas, o.custom);
        if (kind === 'effort') {
          const numberBottom = o.showValue ? R.y + o.valueMargin + clamp(o.thickness * 0.34, 9, 20) : R.y;
          const isz = clamp(R.w * 0.46, 12, 26);
          const iconTop = o.showIcon ? R.y + R.h - o.iconMargin - isz : R.y + R.h;
          drawEffortArt(ctx, R, fillTop, value, ft, o.fill, ca, { labelTop: numberBottom + 4, labelBottom: iconTop - 4, ghost: css(fillC), arrival: s.arrival ?? 1, palette: this.effortPalette });
          overFill = argb(effortInk(levelAt(value), this.effortPalette || EFFORT_P));
        }
        if (kind === 'glimmer') drawGlimmer(ctx, R, fillTop, value, ft, css(fillC), ca, { handle: o.fill.handle ?? true, stops: o.fill.stops ?? true }, this.glimmerPalette);
        if (feedbackOn) this.lit(ctx, R, fillTop, s, ink, ca, kind !== 'pixels' && kind !== 'glimmer', true);
        ctx.save(); ctx.beginPath(); ctx.rect(R.x - 1, R.y - 1, R.w + 2, fillTop - R.y + 1); ctx.clip();
        this.contents(ctx, R, value, overTrack, ca);
        ctx.restore();
        ctx.save(); ctx.beginPath(); ctx.rect(R.x - 1, fillTop, R.w + 2, R.y + R.h - fillTop + 1); ctx.clip();
        this.contents(ctx, R, value, kind === 'glimmer' ? overTrack : overFill, ca);
        ctx.restore();
        if (kind === 'glimmer' && (o.fill.handle ?? true)) {
          // Through the handle, which is the fill colour, the contents switch to the fill's ink.
          const hh = Math.min(clamp(0.8 * R.w, 10, 30), R.h / 3);
          const cy = clamp(fillTop, R.y + hh / 2, R.y + R.h - hh / 2);
          ctx.save(); ctx.beginPath(); ctx.roundRect(R.x, cy - hh / 2, R.w, hh, Math.min(R.w, hh) / 2); ctx.clip();
          this.contents(ctx, R, value, pickInk(fillC, trackNow), ca);
          ctx.restore();
        }
      }
      ctx.restore();
      if (committed && feedbackOn) this.panelFeedback(ctx, out, R, fillTop, s, overTrack, ink, ca);
      if (committed && s.grabbed && !(kind === 'glimmer' && (o.fill.handle ?? true))) this.lip(ctx, out, R, fillTop, fillC, ca);
      if (theme.lit) this.rim(ctx, out, R, theme.pale, e);
      return;
    }

    // Solid and the fills drawn inside the fill's shape.
    this.contents(ctx, R, value, overTrack, ca);
    if (committed) {
      const pic = PICTORIAL[kind];
      const fp = pic?.shape ? pic.shape(R, fillTop, s.phase ?? 0) : null;
      ctx.save();
      ctx.beginPath();
      if (fp) ctx.clip(fp); else { ctx.rect(R.x - 1, fillTop, R.w + 2, R.y + R.h - fillTop + 1); ctx.clip(); }
      ctx.save(); ctx.globalAlpha *= ca; ctx.fillStyle = css(fillC); ctx.fillRect(R.x - 1, Math.min(fillTop - 8, R.y), R.w + 2, R.h + 16); ctx.restore();
      if (pic) {
        const trackHex = '#FF' + trackNow.slice(1).map((c) => Math.round(c).toString(16).padStart(2, '0')).join('');
        pic.draw(ctx, R, fillTop, s.phase ?? 0, value, ca, { cache: this.picCache, view: this.o.view || { w: R.w, h: R.h }, trackHex, custom: o.custom });
      }
      if (feedbackOn) this.lit(ctx, R, fillTop, s, ink, ca, true, false);
      this.contents(ctx, R, value, pickInk(backdrop, trackNow), ca);
      ctx.restore();
      if (s.grabbed) this.lip(ctx, out, R, fillTop, fillC, ca);
    }
    ctx.restore();
    if (committed && feedbackOn) this.panelFeedback(ctx, out, R, fillTop, s, overTrack, ink, ca);
    if (theme.lit) this.rim(ctx, out, R, theme.pale, e);
  }

  // The app's own AGSL fill through agsl.js, over the whole rect inside the outline.
  programFill(ctx, R, fillTop, time, ca) {
    if (!this.shader) return;
    const f = this.o.fill;
    const u = {
      origin: [R.x - this.win.x, R.y - this.win.y], size: [R.w, R.h], time, level: fillTop - this.win.y,
      rest: f.rest ?? (f.kind === 'surge' ? 0.1 : 0.15), scale: f.scale ?? f.size ?? 1, alpha: ca,
      detail: f.detail ?? 0.5, bright: f.bright ?? 1, grain: f.grain ?? 0.15,
      edge: f.edge ?? 0.5, glow: f.glow ?? 0.6, trail: f.trail ?? 0.5,
    };
    this.palette.forEach((c, i) => { u['c' + i] = [c[1] / 255, c[2] / 255, c[3] / 255, 1]; });
    this.shader.draw(u);
    ctx.save();
    ctx.setTransform(DPR, 0, 0, DPR, -this.win.x * DPR, -this.win.y * DPR);
    ctx.drawImage(this.shader.canvas, this.win.x, this.win.y, this.win.w, this.win.h);
    ctx.restore();
  }

  lip(ctx, out, R, fillTop, fillC, ca) {
    ctx.save(); ctx.clip(out); ctx.globalAlpha *= ca; ctx.fillStyle = css(fillC); ctx.fillRect(R.x, fillTop - 1.5, R.w, 3); ctx.restore();
  }

  // LevelFeedbackArt.drawLit, clipped to the lit part: the sheen, the step flash, the Burst's wave.
  lit(ctx, R, fillTop, s, ink, alpha, sheen, clip) {
    const litH = R.y + R.h - fillTop;
    if (litH <= 0.5 || alpha <= 0.01) return;
    const fb = this.o.feedback, t = s.t ?? 0, level = levelAt(s.value);
    if (clip) { ctx.save(); ctx.beginPath(); ctx.rect(R.x, fillTop, R.w, litH); ctx.clip(); }
    const x0 = R.x, x1 = R.x + R.w;
    if (fb.follow) {
      if (sheen) {
        const p = (t / SHEEN_S[level]) % 1;
        const thick = Math.max(28, litH * 0.45);
        softBand(ctx, x0, x1, R.y + R.h + thick / 2 - (litH + thick) * p, thick, ink, SHEEN_A[level] * alpha);
      }
      const flash = fade(s.stepS ?? -1, 0.45);
      if (flash > 0) {
        ctx.save(); ctx.fillStyle = ink; ctx.globalAlpha *= 0.16 * flash * alpha; ctx.fillRect(x0, fillTop, R.w, litH); ctx.restore();
        ctx.save(); ctx.fillStyle = ink; ctx.globalAlpha *= 0.85 * flash * alpha; ctx.fillRect(x0, fillTop, R.w, 2.5); ctx.restore();
      }
    }
    const fullS = s.fullS ?? -1;
    if (fb.full && fb.max === 'burst' && fullS >= 0 && fullS < 0.75) {
      const p = fullS / 0.75, eased = 1 - (1 - p) * (1 - p);
      const thick = R.h * 0.4;
      softBand(ctx, x0, x1, R.y + R.h + thick / 2 - (R.h + thick) * eased, thick, ink, 0.6 * (1 - p) * alpha);
    }
    if (clip) ctx.restore();
  }

  // LevelFeedbackArt.drawPanel, clipped to the outline: the low glow and the flourish at the top.
  panelFeedback(ctx, out, R, fillTop, s, glow, ink, alpha) {
    if (alpha <= 0.01) return;
    const fb = this.o.feedback, t = s.t ?? 0, v = clamp(s.value ?? 0), b = breath(t);
    const glowHex = typeof glow === 'string' ? glow : '#' + glow.slice(1).map((c) => Math.round(c).toString(16).padStart(2, '0')).join('');
    ctx.save();
    ctx.clip(out);
    if (fb.low && v < 0.15) {
      if (v <= 0.0005) {
        const w = R.w * 0.42, hh = 3.5, cx = R.x + R.w / 2, bottom = R.y + R.h - 7;
        ctx.save(); ctx.fillStyle = glowHex; ctx.globalAlpha *= (0.3 + 0.6 * b) * alpha;
        ctx.beginPath(); ctx.roundRect(cx - w / 2, bottom - hh, w, hh, hh / 2); ctx.fill(); ctx.restore();
        softBand(ctx, R.x, R.x + R.w, bottom - 3, 26, glowHex, 0.35 * b * alpha);
      } else {
        softBand(ctx, R.x, R.x + R.w, fillTop, 28, glowHex, (0.3 + 0.55 * b) * (1 - (0.4 * v) / 0.15) * alpha);
      }
    }
    if (fb.full && v >= 0.995) {
      const fullS = s.fullS ?? -1, arrive = fade(fullS, 1.1);
      const f = { ripple: this.ripple, shine: this.shine, sparkle: this.sparkle, confetti: this.confetti, neon: this.neon, pulse: this.pulse }[fb.max] || this.burst;
      f.call(this, ctx, out, R, t, fullS, arrive, b, ink, alpha);
    }
    ctx.restore();
  }

  burst(ctx, out, R, t, fullS, burst, b, ink, alpha) {
    const cx = R.x + R.w / 2, cy = R.y + R.h / 2;
    const rim = ctx.createConicGradient((((t / 3.2) * 360) % 360) * Math.PI / 180, cx, cy);
    SPECTRUM.forEach((c, i) => rim.addColorStop(i / 7, c));
    rim.addColorStop(1, SPECTRUM[0]);
    ctx.save(); ctx.strokeStyle = rim; ctx.lineWidth = 5 + 7 * burst; ctx.lineJoin = 'round';
    ctx.globalAlpha *= ((0.6 + 0.3 * b) * (1 - burst) + burst) * alpha; ctx.stroke(out); ctx.restore();
    if (burst > 0) {
      const top = R.y + R.w * 0.5, grow = 1 - burst, radius = R.w * (0.5 + 1.3 * grow);
      const g = ctx.createRadialGradient(cx, top, 0, cx, top, radius);
      g.addColorStop(0, 'rgba(255,255,255,0.8)'); g.addColorStop(0.45, 'rgba(255,255,255,0.25)'); g.addColorStop(1, 'rgba(255,255,255,0)');
      ctx.save(); ctx.globalAlpha *= burst * alpha; ctx.fillStyle = g; ctx.fillRect(cx - radius, top - radius, radius * 2, radius * 2); ctx.restore();
      ctx.save(); ctx.globalAlpha *= burst * alpha; ctx.strokeStyle = '#FAC35F'; ctx.lineWidth = 2.5;
      ctx.beginPath(); ctx.arc(cx, top, R.w * (0.2 + 1.6 * grow), 0, TAU); ctx.stroke(); ctx.restore();
    }
  }

  ripple(ctx, out, R, t, fullS, arrive, b, ink, alpha) {
    const cx = R.x + R.w / 2, cy = R.y + R.w * 0.5, reach = R.h * 1.05;
    ctx.save(); ctx.strokeStyle = ink;
    for (let k = 0; k < 3; k++) {
      const p = ((((t + k * 1.2) % 3.6) + 3.6) % 3.6) / 3.6, grow = 1 - (1 - p) * (1 - p);
      ctx.lineWidth = 3 - 1.8 * p; ctx.globalAlpha = 0.5 * (1 - p) * alpha;
      ctx.beginPath(); ctx.arc(cx, cy, R.w * 0.25 + reach * grow, 0, TAU); ctx.stroke();
    }
    if (fullS >= 0) {
      for (let k = 0; k < 3; k++) {
        const p = (fullS - k * 0.14) / 0.9;
        if (p < 0 || p >= 1) continue;
        const grow = 1 - (1 - p) * (1 - p);
        ctx.lineWidth = 4.5 - 2.5 * p; ctx.globalAlpha = 0.9 * (1 - p) * alpha;
        ctx.beginPath(); ctx.arc(cx, cy, R.w * 0.2 + reach * grow, 0, TAU); ctx.stroke();
      }
    }
    ctx.restore();
  }

  shine(ctx, out, R, t, fullS, arrive, b, ink, alpha) {
    const q = (((t % 2.4) + 2.4) % 2.4) / 2.4 / 0.45;
    if (q < 1) {
      const eased = q * q * (3 - 2 * q), thick = R.w * 0.9;
      const centre = R.y + R.h + R.w * 1.5 - (R.h + R.w * 3) * eased;
      // A band `th` thick centred on `c`, its gradient turned by SHINE_SLANT (-24°) about (cx, c).
      const glint = (c, th, a) => {
        const ang = (-24 * Math.PI) / 180, cx = R.x + R.w / 2;
        const dx = (Math.sin(ang) * th) / 2, dy = (Math.cos(ang) * th) / 2;
        const g = ctx.createLinearGradient(cx + dx, c - dy, cx - dx, c + dy);
        g.addColorStop(0, hexA(ink, 0)); g.addColorStop(0.5, hexA(ink, 1)); g.addColorStop(1, hexA(ink, 0));
        ctx.save(); ctx.globalAlpha *= a; ctx.fillStyle = g; ctx.fillRect(R.x, R.y, R.w, R.h); ctx.restore();
      };
      glint(centre, thick, 0.6 * alpha);
      glint(centre + thick * 0.95, thick * 0.3, 0.4 * alpha);
    }
    if (arrive > 0) { ctx.save(); ctx.fillStyle = ink; ctx.globalAlpha *= 0.3 * arrive * alpha; ctx.fillRect(R.x, R.y, R.w, R.h); ctx.restore(); }
  }

  sparkle(ctx, out, R, t, fullS, arrive, b, ink, alpha) {
    const star = new Path2D();
    const w = 0.2;
    [[0, -1], [w, -w], [1, 0], [w, w], [0, 1], [-w, w], [-1, 0], [-w, -w]].forEach(([x, y], i) => (i ? star.lineTo(x, y) : star.moveTo(x, y)));
    star.closePath();
    ctx.save(); ctx.fillStyle = ink;
    for (let i = 0; i < 7; i++) {
      const tw = twinkle(i, t), litK = Math.max(tw, arrive);
      if (litK <= 0.01) continue;
      const x = R.x + R.w * (0.2 + 0.6 * pseudoRandom(i * 31 + 7));
      const y = R.y + R.h * (0.07 + 0.86 * ((i + pseudoRandom(i * 17 + 3) * 0.8) / 7));
      const size = (3.5 + 3.5 * pseudoRandom(i * 13 + 5)) * (0.45 + 0.55 * litK) * (1 + 0.5 * arrive);
      ctx.globalAlpha = 0.22 * litK * alpha; ctx.beginPath(); ctx.arc(x, y, size * 0.9, 0, TAU); ctx.fill();
      ctx.globalAlpha = litK * alpha;
      ctx.save(); ctx.translate(x, y); ctx.rotate((45 * tw * Math.PI) / 180); ctx.scale(size, size); ctx.fill(star); ctx.restore();
    }
    ctx.restore();
  }

  confetti(ctx, out, R, t, fullS, arrive, b, ink, alpha) {
    const w = R.w, hh = R.h, piece = 2.2;
    const tumble = (x, y, spin, colour, a) => {
      ctx.save(); ctx.globalAlpha = a; ctx.fillStyle = colour; ctx.translate(x, y); ctx.rotate(spin);
      ctx.scale(Math.max(Math.abs(Math.cos(spin * 1.7)), 0.25), 1); ctx.fillRect(-piece, -piece * 0.55, piece * 2, piece * 1.1); ctx.restore();
    };
    if (fullS >= 0 && fullS < 1.8) {
      const k = fullS, f = 1 - (k / 1.8) ** 3;
      for (let i = 0; i < 26; i++) {
        const a = pseudoRandom(i * 13 + 1), bb = pseudoRandom(i * 13 + 2), c = pseudoRandom(i * 13 + 3);
        const x = R.x + w / 2 + (a - 0.5) * w * 1.4 * k + Math.sin(k * 6 + c * 6.3) * w * 0.08;
        const y = R.y + w * 0.4 - hh * (0.12 + 0.16 * bb) * k + 0.5 * hh * 0.95 * k * k;
        tumble(x, y, k * (4 + 5 * c) + c * 6.3, SPECTRUM[i % 7], f * alpha);
      }
    }
    for (let i = 0; i < 9; i++) {
      const a = pseudoRandom(i * 29 + 11), bb = pseudoRandom(i * 29 + 12);
      const fall = wrap(t / 2.4 + bb);
      const x = R.x + w * (0.12 + 0.76 * a) + Math.sin(t * 2.5 + a * 6.3) * w * 0.1;
      const y = R.y - piece * 2 + (hh + piece * 4) * fall;
      tumble(x, y, t * (3 + 3 * a) + bb * 6.3, SPECTRUM[(i + 3) % 7], 0.85 * alpha);
    }
  }

  neon(ctx, out, R, t, fullS, arrive, b, ink, alpha) {
    const on = neonStrike(fullS) * (0.88 + 0.12 * b);
    const g = ctx.createConicGradient((((t / 6) * 360) % 360) * Math.PI / 180, R.x + R.w / 2, R.y + R.h / 2);
    NEON.forEach((c, i) => g.addColorStop(i / 4, c));
    ctx.save(); ctx.strokeStyle = g; ctx.lineJoin = 'round';
    [[16, 0.2], [8, 0.45], [3, 1]].forEach(([lw, k]) => { ctx.lineWidth = lw; ctx.globalAlpha = k * on * alpha; ctx.stroke(out); });
    ctx.restore();
  }

  pulse(ctx, out, R, t, fullS, arrive, b, ink, alpha) {
    const beat = Math.min(1, heartbeat(t) * 0.8 + arrive);
    if (beat <= 0.01) return;
    if (ink === '#FFFFFF') {
      const radius = R.h * (0.35 + 0.3 * beat), cx = R.x + R.w / 2, cy = R.y + R.h / 2;
      const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, radius);
      g.addColorStop(0, 'rgba(255,255,255,0.8)'); g.addColorStop(0.45, 'rgba(255,255,255,0.25)'); g.addColorStop(1, 'rgba(255,255,255,0)');
      ctx.save(); ctx.globalAlpha *= 0.45 * beat * alpha; ctx.fillStyle = g; ctx.fillRect(R.x, R.y, R.w, R.h); ctx.restore();
    }
    ctx.save(); ctx.strokeStyle = ink; ctx.lineJoin = 'round';
    [[20, 0.1], [11, 0.16], [5, 0.28]].forEach(([lw, k]) => { ctx.lineWidth = lw * (0.6 + 0.4 * beat); ctx.globalAlpha = k * beat * alpha; ctx.stroke(out); });
    ctx.restore();
  }
}

// A panel by itself in `container`, its window at (0, 0) of it: a settings tile's MiniFill
// (24 x 70, 12 dp corners, no number or icon) or the preview's real panel (`bar` set: the panel
// grows out of that collapsed bar). `custom`: the user's Animation colours ('#RRGGBB' x 3) or null,
// which recolour every fill that has colours of its own. Returns the FillPanel; call panel.draw(state).
export function fillPanel(container, { w = 24, h = 70, shape = 'rounded', radius = 12, flare = 0.22, fill = { kind: 'solid' }, showValue = false,
  showIcon = false, track = '#FF1C1C20', fillColor = '#FFFFFFFF', side = 'right', valueMargin = 35, iconMargin = 35, theme = 'solid',
  icon = 'ic_vol_increase', feedback = { follow: true, low: true, full: true, max: 'burst' }, entrance = 'slide', room = 0, bar = null,
  boxW = w, perspective = 1296, custom = null } = {}) {
  const preset = bar || { w, h, color: track, shape, flare, radii: [radius, radius, radius, radius], edgeMargin: 0 };
  const rect = bar ? { x: side === 'right' ? boxW - bar.w : 0, y: h / 2 - bar.h / 2, w: bar.w, h: bar.h } : { x: side === 'right' ? boxW - w : 0, y: 0, w, h };
  const fakePhone = { overlay: container };
  const fakeBar = { preset, rect, screenW: boxW, screenH: h, frameTop: 0 };
  return new FillPanel(fakePhone, fakeBar, {
    side, thickness: w, length: h, fill, showValue, showIcon, track, fillColor, matchShape: true, flare, theme, icon,
    valueMargin, iconMargin, entrance, feedback, room, perspective, view: { w: boxW, h }, custom,
  });
}

export { SHADER_FILLS, SURGE_FILLS };
