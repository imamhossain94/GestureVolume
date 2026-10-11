// The edge bar and the Quick panel that grows out of it, drawn the way the app draws them:
// HandlerShape's tab outline, QuickSliderView's morph and double-drawn number and icon,
// ContentInk's contrast rule, LevelFeedbackArt's sheen, step flash and Burst, and the app's own
// AGSL fills through agsl.js. Lengths are dp in the phone's coordinates; canvases are drawn at
// 3 px per dp.
import { clamp, lerp } from './core.js';
import { ShaderCanvas, loadShaders, hexToVec4 } from './agsl.js';
import { drawPixels, drawEffort, drawGlimmer, effortLevel } from './fills.js';

export const DPR = 3;

// --- Colours (Android 0xAARRGGBB as '#AARRGGBB' or '#RRGGBB') ----------------------------------
export function argb(hex) {
  const v = hex.replace('#', '');
  if (v.length === 6) return [255, parseInt(v.slice(0, 2), 16), parseInt(v.slice(2, 4), 16), parseInt(v.slice(4, 6), 16)];
  return [parseInt(v.slice(0, 2), 16), parseInt(v.slice(2, 4), 16), parseInt(v.slice(4, 6), 16), parseInt(v.slice(6, 8), 16)];
}
export const css = ([a, r, g, b], mul = 1) => `rgba(${r},${g},${b},${(a / 255) * mul})`;
export const blendARGB = (x, y, t) => x.map((c, i) => c + (y[i] - c) * t);

function channel(c) { c /= 255; return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }
export const luminance = ([, r, g, b]) => 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
export const contrast = (a, b) => { const la = luminance(a), lb = luminance(b); return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05); };
const blendOpaque = (from, to, t) => [255, ...[1, 2, 3].map((i) => clamp(Math.floor(from[i] + (to[i] - from[i]) * t + 0.5), 0, 255))];
// ContentInk.pick: the preferred colour if it reads (4.5:1), else a light or dark shade of the background.
export function pickInk(bg, pref) {
  bg = [255, bg[1], bg[2], bg[3]]; pref = [255, pref[1], pref[2], pref[3]];
  if (contrast(pref, bg) >= 4.5) return pref;
  const light = blendOpaque(bg, [255, 255, 255, 255], 0.9), dark = blendOpaque(bg, [255, 0, 0, 0], 0.8);
  return contrast(light, bg) >= contrast(dark, bg) ? light : dark;
}

// --- Drawables, as drawing operations (vd2svg.py --all) -----------------------------------------
const drawables = new Map();
export async function loadDrawables(names) {
  await Promise.all(names.map(async (n) => {
    if (drawables.has(n)) return;
    const res = await fetch(new URL(`../build/drawable/${n}.json`, import.meta.url));
    if (!res.ok) throw new Error('drawable missing: ' + n);
    const d = await res.json();
    d.paths = d.ops.map((o) => ({ ...o, p: new Path2D(o.d) }));
    drawables.set(n, d);
  }));
}

// Draws drawable `name` into the box (x, y, size), tinted `color` (as DrawableCompat.setTint).
export function drawDrawable(ctx, name, x, y, size, color, alpha = 1) {
  const d = drawables.get(name);
  if (!d) throw new Error('drawable not loaded: ' + name);
  ctx.save();
  ctx.translate(x, y);
  ctx.scale(size / d.w, size / d.h);
  ctx.globalAlpha *= alpha;
  for (const o of d.paths) {
    ctx.save();
    ctx.transform(...o.m);
    if (o.fill) { ctx.fillStyle = color; ctx.globalAlpha *= o.fa; ctx.fill(o.p, o.evenOdd ? 'evenodd' : 'nonzero'); ctx.globalAlpha /= o.fa || 1; }
    if (o.stroke) {
      ctx.strokeStyle = color; ctx.lineWidth = o.sw; ctx.lineCap = o.cap; ctx.lineJoin = o.join;
      ctx.globalAlpha *= o.sa; ctx.stroke(o.p);
    }
    ctx.restore();
  }
  ctx.restore();
}

// --- Shapes -------------------------------------------------------------------------------------
// HandlerShape.tabProfile / tabOutline, as in the app.
export function tabProfile(t) {
  const p = clamp(t);
  const cubic = p * p * (3 - 2 * p);
  const quintic = p * p * p * (p * (6 * p - 15) + 10);
  return cubic * (1 - p) + quintic * p;
}

export function tabPoints(width, height, flare, edgeOnLeft, steps = 40) {
  const length = clamp(flare, 0.04, 0.5) * height;
  const edgeX = edgeOnLeft ? 0 : width, innerX = edgeOnLeft ? width : 0, span = innerX - edgeX;
  const pts = [];
  for (let s = 0; s <= steps; s++) { const t = s / steps; pts.push([edgeX + span * tabProfile(t), length * t]); }
  pts.push([innerX, height - length]);
  for (let s = steps; s >= 0; s--) { const t = s / steps; pts.push([edgeX + span * tabProfile(t), height - length * t]); }
  return pts;
}

// The outline of a bar or panel at rect R (dp): a tab closed along the screen edge, or a rounded rect.
export function outline(R, { shape = 'tab', flare = 0.29, radii = [8, 8, 8, 8], edgeOnLeft = false, inset = 0 } = {}) {
  const p = new Path2D();
  if (shape === 'tab') {
    const pts = tabPoints(R.w, R.h, flare, edgeOnLeft);
    p.moveTo(R.x + pts[0][0], R.y + pts[0][1]);
    for (let i = 1; i < pts.length; i++) p.lineTo(R.x + pts[i][0], R.y + pts[i][1]);
    p.closePath();
  } else {
    const x = R.x + inset, y = R.y + inset, w = R.w - 2 * inset, h = R.h - 2 * inset;
    let [tl, tr, br, bl] = radii;
    const f = Math.min(1, w / Math.max(tl + tr, 1e-6), w / Math.max(bl + br, 1e-6), h / Math.max(tl + bl, 1e-6), h / Math.max(tr + br, 1e-6));
    tl *= f; tr *= f; br *= f; bl *= f;
    p.roundRect(x, y, w, h, [tl, tr, br, bl]);
  }
  return p;
}

// The tab's open outline (inner face and both sweeps, not the screen edge), for the drag cue's stroke.
export function tabStroke(R, flare, edgeOnLeft, inset = 1) {
  const pts = tabPoints(R.w, R.h, flare, edgeOnLeft);
  const p = new Path2D();
  const dx = edgeOnLeft ? -inset : inset;
  p.moveTo(R.x + pts[0][0] + dx * (1 - tabProfile(0)), R.y + pts[0][1]);
  for (let i = 1; i < pts.length; i++) {
    const [x, y] = pts[i];
    const k = edgeOnLeft ? x / R.w : 1 - x / R.w;
    p.lineTo(R.x + x + dx * Math.min(1, k * 4), R.y + y);
  }
  return p;
}

// --- Presets (HandlerPresets.kt) ----------------------------------------------------------------
export const PRESETS = {
  classic: { name: 'Classic', w: 30, h: 100, color: '#804F46E5', stroke: { w: 1, color: '#C8FFFFFF' }, shape: 'rounded', radii: [15, 15, 15, 15], icon: 'ic_vol_increase', iconSize: 18, showIcon: false, edgeMargin: 0 },
  dock: { name: 'Dock', w: 14, h: 120, color: '#FF000000', stroke: null, shape: 'tab', flare: 0.29, radii: [8, 8, 8, 8], icon: 'ic_vol_increase', iconSize: 16, showIcon: false, edgeMargin: 0 },
  edge: { name: 'Edge', w: 12, h: 95, color: '#FF000000', stroke: null, shape: 'rounded', radii: [10, 1, 1, 10], icon: 'ic_vol_increase', iconSize: 18, showIcon: false, edgeMargin: 0 },
  bold: { name: 'Bold', w: 46, h: 46, color: '#8C1F2937', stroke: { w: 1.5, color: '#5AFFFFFF' }, shape: 'rounded', radii: [23, 23, 23, 23], icon: 'ic_vol_increase', iconSize: 24, showIcon: true, edgeMargin: 8 },
};

// Where a bar sits: centre at `frac` of the usable frame, which is the display minus the cutout
// (overlays draw over the status and navigation bars, but not into a punch-hole's band).
export const FRAME_TOP = 32;
export function barRect(preset, { screenW = 412, screenH = 915, frac = 0.21, side = 'right', frameTop = FRAME_TOP } = {}) {
  const cy = frameTop + frac * (screenH - frameTop);
  const x = side === 'right' ? screenW - preset.w - preset.edgeMargin : preset.edgeMargin;
  return { x, y: cy - preset.h / 2, w: preset.w, h: preset.h };
}

// --- The edge bar (HandlerView) -----------------------------------------------------------------
export class EdgeBar {
  constructor(phone, preset = PRESETS.dock, opts = {}) {
    this.phone = phone;
    this.preset = { ...preset, ...opts.override };
    this.screenW = opts.screenW ?? 412;
    this.screenH = opts.screenH ?? 915;
    this.canvas = document.createElement('canvas');
    this.canvas.width = this.screenW * DPR;
    this.canvas.height = this.screenH * DPR;
    Object.assign(this.canvas.style, { position: 'absolute', left: 0, top: 0, width: this.screenW + 'px', height: this.screenH + 'px', pointerEvents: 'none' });
    phone.overlay.appendChild(this.canvas);
    this.frameTop = opts.frameTop ?? FRAME_TOP;
    this.rect = barRect(this.preset, { screenW: this.screenW, screenH: this.screenH, frac: opts.frac ?? 0.21, side: opts.side ?? 'right', frameTop: this.frameTop });
  }

  // state: { rect?, alpha=1, cue=0 (drag cue 0..1), readout=null|'47%', edgeOnLeft }
  draw(state = {}) {
    const ctx = this.canvas.getContext('2d');
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    const alpha = state.alpha ?? 1;
    if (alpha <= 0.001) return;
    const P = this.preset;
    const R = state.rect || this.rect;
    const edgeOnLeft = state.edgeOnLeft ?? (R.x + R.w / 2 < this.screenW / 2);
    ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
    const cue = state.cue ?? 0;
    ctx.globalAlpha = alpha * lerp(1, 0.65, cue);
    const sw = P.stroke?.w ?? 0;
    const path = outline(R, { shape: P.shape, flare: P.flare, radii: P.radii, edgeOnLeft, inset: sw / 2 });
    ctx.fillStyle = css(argb(P.color));
    ctx.fill(path);
    const strokeW = cue > 0 ? Math.max(sw, 2) : sw;
    if (strokeW > 0) {
      ctx.lineWidth = strokeW;
      ctx.strokeStyle = cue > 0 ? '#FFFFFF' : css(argb(P.stroke.color));
      if (P.shape === 'tab') ctx.stroke(tabStroke(R, P.flare, edgeOnLeft, 1));
      else ctx.stroke(outline(R, { shape: P.shape, radii: P.radii, inset: strokeW / 2 }));
    }
    const cx = R.x + R.w / 2, cy = R.y + R.h / 2;
    if (state.readout) {
      // "NN%": auto-sized 8-15 sp to the drawn width, white, Roboto Regular.
      let size = 15;
      ctx.font = `400 ${size}px Roboto`;
      while (size > 8 && ctx.measureText(state.readout).width > R.w - 2) { size -= 1; ctx.font = `400 ${size}px Roboto`; }
      ctx.fillStyle = '#FFFFFF';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.save(); ctx.beginPath(); ctx.rect(R.x, R.y, R.w, R.h); ctx.clip();
      ctx.fillText(state.readout, cx, cy + 0.5);
      ctx.restore();
    } else if (cue > 0 || P.showIcon) {
      const name = cue > 0 ? 'ic_move' : P.icon;
      const d = drawables.get(name);
      const intrinsic = name === 'ic_move' ? 32 : 24;
      const s = Math.min(P.iconSize, intrinsic);
      ctx.save(); ctx.beginPath(); ctx.rect(R.x, R.y, R.w, R.h); ctx.clip();
      if (d) drawDrawable(ctx, name, cx - s / 2, cy - s / 2, s, '#FFFFFF');
      ctx.restore();
    }
    ctx.globalAlpha = 1;
  }
}

// --- Panel styles (PanelTheme.kt) -----------------------------------------------------------------
export const THEMES = {
  solid: { surfaceAlpha: 1, lit: false }, frosted: { surfaceAlpha: 0.66, lit: false, blur: 28 },
  glass: { surfaceAlpha: 0.34, lit: true, blur: 40 }, aero: { forced: '#8CD6E6F4', lit: true, pale: true, blur: 36 },
  vibrant: { forced: '#A6F7F7FA', pale: true, blur: 44 }, paper: { forced: '#F7F8F7F4', pale: true },
  midnight: { forced: '#F50D1330' }, amoled: { forced: '#FF000000' }, acrylic: { forced: '#A82B2430', blur: 32 },
  amethyst: { forced: '#6B3B2463', lit: true, blur: 40 },
};

// Shader / Surge palettes and settings (ShaderFill.kt, SurgeFill.kt).
export const SHADER_FILLS = {
  'lava-lamp': ['#0C0418', '#7A2BE8', '#FF3D7A', '#FFB347'], 'liquid-chrome': ['#07060D', '#4A4E63', '#BFC4D8', '#FFFFFF'],
  molten: ['#120808', '#D92405', '#FF8410', '#FFF2B8'], aurora: ['#050A18', '#2BFF9A', '#2BD8FF', '#A36BFF'],
  'ink-smoke': ['#0B0416', '#2A44B0', '#A8307A', '#6FD8FF'], plasma: ['#3A0A05', '#C44A20', '#F08A3A', '#FFE0A0'],
  caustics: ['#021A33', '#0A5C8C', '#3FD0E8', '#E8FFFF'], 'soap-film': ['#FF7AC8', '#FFD66B', '#6BE8FF', '#9B7BFF'],
  mesh: ['#FF5E9C', '#FFB86B', '#5E8BFF', '#9B5CFF'], clouds: ['#0B1236', '#2E4FA8', '#9AA8E0', '#F6F4FF'],
  smoke: ['#0B0B12', '#6A6A8C', '#C4C4E0', '#F4F2FF'], starfield: ['#03030C', '#2A1F6B', '#FFFFFF', '#A9C4FF'],
};
export const SURGE_FILLS = {
  curve: ['#050A20', '#173AB4', '#4E8CFF', '#D9F1FF'], electric: ['#05081A', '#1C46C8', '#4FA6FF', '#D6F4FF'],
  honeycomb: ['#100A22', '#6A4DE6', '#A98BFF', '#EEDDFF'], streaks: ['#170812', '#7A2458', '#E35BAE', '#FFE3F5'],
  blocks: ['#0B0E17', '#2C3654', '#9CB5EC', '#F4FAFF'], flame: ['#150604', '#83260A', '#FF7A2C', '#FFE9A8'],
};

const EFFORT_SPECTRUM = ['#EB5F57', '#F58B57', '#FAC35F', '#91C882', '#82AADC', '#9B82C8', '#C882B4'];
const SHEEN_S = [3.4, 2.7, 2.1, 1.6, 1.2], SHEEN_A = [0.08, 0.12, 0.16, 0.2, 0.26];
const levelAt = (v) => clamp(Math.ceil(v * 5 - 1e-4) - 1, 0, 4);
const fadeAfter = (s, len) => (s >= 0 && s < len ? Math.pow(1 - s / len, 2) : 0);
const breathAt = (t) => 0.5 - 0.5 * Math.cos((t / 1.6) * 2 * Math.PI);

// PanelAnimation.frameAt for the entrances the store art uses (all 16 are in the spec).
export function entranceFrame(id, t, towardLeft = false) {
  const p = clamp(t);
  const ease = (x) => 1 - Math.pow(1 - clamp(x), 3);
  const away = towardLeft ? -1 : 1;
  const quickFade = ease(p / 0.6);
  const damped = (x, k) => { const d = Math.exp(-k * x * 2); return 1 - d * Math.cos(x * k * 3.2) - d * Math.sin(x * k * 3.2) * 0.25; };
  const overshoot = (x, k) => (x <= 0 ? 0 : x >= 1 ? 1 : damped(x, k) / damped(1, k));
  const f = { alpha: 1, sx: 1, sy: 1, tx: 0, ty: 0, rz: 0, ry: 0, rx: 0, ox: 0.5, oy: 0.5, revealFrom: 0, revealTo: 1 };
  switch (id) {
    case 'fade': f.alpha = ease(p); break;
    case 'pop': f.alpha = quickFade; f.sx = f.sy = lerp(0.94, 1, ease(p)); break;
    case 'spring': f.alpha = quickFade; f.sx = f.sy = lerp(0.86, 1, overshoot(p, 1.7)); break;
    case 'zoom': f.alpha = quickFade; f.sx = f.sy = lerp(0.55, 1, ease(p)); break;
    case 'unfold': f.alpha = quickFade; f.sy = lerp(0.35, 1, ease(p)); break;
    case 'expand': f.alpha = quickFade; f.sx = lerp(0.4, 1, ease(p)); f.ox = towardLeft ? 0 : 1; break;
    case 'rise': f.alpha = quickFade; f.ty = lerp(22, 0, ease(p)); break;
    case 'drop': f.alpha = quickFade; f.ty = lerp(-26, 0, overshoot(p, 1.4)); break;
    case 'slide': f.alpha = quickFade; f.tx = lerp(26 * away, 0, ease(p)); break;
    case 'swing': f.alpha = quickFade; f.rz = lerp(-7 * away, 0, overshoot(p, 1.5)); f.ox = towardLeft ? 0 : 1; break;
    case 'flip': f.alpha = quickFade; f.ry = lerp(62 * away, 0, ease(p)); f.ox = towardLeft ? 0 : 1; break;
    case 'tilt': f.alpha = quickFade; f.rx = lerp(-55, 0, ease(p)); f.oy = 1; break;
    case 'blinds': f.revealTo = ease(p); break;
    case 'tide': f.revealFrom = 1 - ease(p); break;
    case 'iris': f.revealFrom = 0.5 - ease(p) / 2; f.revealTo = 0.5 + ease(p) / 2; break;
    case 'settle': { const s = overshoot(p, 2.2); f.alpha = quickFade; f.ty = lerp(-18, 0, s); f.sy = lerp(0.96, 1, s); break; }
    default: f.alpha = ease(p);
  }
  return f;
}

// The frame as a CSS transform on an element whose box is `box` (for the Deck and the menu).
export function entranceCss(el, f, box) {
  const ox = box.x + box.w * f.ox, oy = box.y + box.h * f.oy;
  el.style.transformOrigin = `${ox - box.x}px ${oy - box.y}px`;
  el.style.transform = `perspective(1300px) translate(${f.tx}px,${f.ty}px) rotateX(${f.rx}deg) rotateY(${f.ry}deg) rotate(${f.rz}deg) scale(${f.sx},${f.sy})`;
  el.style.opacity = f.alpha;
  el.style.clipPath = f.revealFrom > 0 || f.revealTo < 1 ? `inset(${f.revealFrom * 100}% 0 ${(1 - f.revealTo) * 100}% 0)` : '';
}

// --- The Quick panel (QuickSliderView) ------------------------------------------------------------
export class QuickPanel {
  constructor(phone, bar, opts = {}) {
    this.phone = phone;
    this.bar = bar;                                    // an EdgeBar: its preset and rect are the collapsed state
    this.o = {
      thickness: 32, length: 220, edgeOffset: 0, valueMargin: 35, iconMargin: 35,
      track: '#FF1C1C20', fillColor: '#FFFFFFFF', fill: { kind: 'solid' }, theme: 'solid',
      icon: 'ic_vol_increase', showValue: true, showIcon: true, entrance: 'slide', matchShape: true,
      feedback: { follow: true, low: true, full: true, max: 'burst' }, ...opts,
    };
    const B = bar.rect;
    const cy = B.y + B.h / 2;
    const right = opts.side ? opts.side === 'right' : B.x + B.w / 2 > bar.screenW / 2;
    this.edgeOnLeft = !right;
    const x = right ? bar.screenW - this.o.thickness - this.o.edgeOffset : this.o.edgeOffset;
    this.panelRect = { x, y: clamp(cy - this.o.length / 2, bar.frameTop, bar.screenH - this.o.length), w: this.o.thickness, h: this.o.length };
    const P = bar.preset;
    this.panelShape = this.o.matchShape ? P.shape : (this.o.shape || 'rounded');
    const ratio = clamp(this.o.thickness / P.w, 0.25, 4);
    this.panelRadii = this.o.matchShape ? P.radii.map((r) => r * ratio) : (this.o.radii || [22, 22, 22, 22]);
    // Match the bar's shape: the panel's sweep keeps the bar's character, at most 22 %.
    const character = ((P.flare ?? 0.29) * P.h / P.w * this.o.thickness) / this.o.length;
    this.panelFlare = this.o.flare ?? Math.max(0.04, Math.min(P.flare ?? 0.29, character, 0.22));
    // The window: the panel plus 36 dp of entrance room, as OverlayController sizes it.
    const room = 40;
    this.win = { x: right ? this.panelRect.x - room - 40 : 0, y: this.panelRect.y - room, w: this.o.thickness + room + 40, h: this.o.length + room * 2 };
    this.canvas = document.createElement('canvas');
    this.canvas.width = Math.round(this.win.w * DPR);
    this.canvas.height = Math.round(this.win.h * DPR);
    Object.assign(this.canvas.style, { position: 'absolute', left: this.win.x + 'px', top: this.win.y + 'px', width: this.win.w + 'px', height: this.win.h + 'px', pointerEvents: 'none' });
    phone.overlay.appendChild(this.canvas);
    this.setFill(this.o.fill);
  }

  setFill(fill) {
    this.o.fill = fill;
    this.shader = null;
    if (fill.kind === 'shader' || fill.kind === 'surge') {
      const name = fill.kind === 'surge' ? 'surge-' + fill.look : fill.effect;
      this.shader = new ShaderCanvas(name, this.win.w, this.win.h, DPR);
      this.palette = (fill.kind === 'surge' ? SURGE_FILLS[fill.look] : SHADER_FILLS[fill.effect]).map(argb);
    }
  }

  static shadersFor(fills) {
    return loadShaders(fills.filter((f) => f.kind === 'shader' || f.kind === 'surge').map((f) => (f.kind === 'surge' ? 'surge-' + f.look : f.effect)));
  }

  // state: { e, value, committed=true, grabbed=false, t (feedback clock s), fillTime (shader clock s),
  //          fullS=-1, stepS=-1, entrance: 0..1 (progress of the opening animation) or null }
  draw(s) {
    const ctx = this.canvas.getContext('2d');
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    const e = clamp(s.e ?? 1);
    if (e <= 0 && !s.showCollapsed) return;
    const o = this.o, P = this.bar.preset, B = s.barRect || this.bar.rect, PR = this.panelRect;
    const R = { x: lerp(B.x, PR.x, e), y: lerp(B.y, PR.y, e), w: lerp(B.w, PR.w, e), h: lerp(B.h, PR.h, e) };
    const theme = THEMES[o.theme] || THEMES.solid;
    const shape = P.shape === 'tab' || this.panelShape === 'tab' ? (this.panelShape === 'tab' || e < 0.5 ? 'tab' : 'rounded') : 'rounded';
    const flare = lerp(P.flare ?? 0.29, this.panelFlare, e);
    const radii = P.radii.map((r, i) => Math.min(lerp(r, this.panelRadii[i], e), Math.min(R.w, R.h) / 2));
    const out = outline(R, { shape, flare, radii, edgeOnLeft: this.edgeOnLeft });

    ctx.setTransform(DPR, 0, 0, DPR, -this.win.x * DPR, -this.win.y * DPR);
    // The opening animation, on the whole layer.
    if (s.entrance != null && s.entrance < 1) {
      const f = entranceFrame(o.entrance, s.entrance, this.edgeOnLeft);
      const ox = PR.x + PR.w * f.ox, oy = PR.y + PR.h * f.oy;
      ctx.globalAlpha = f.alpha;
      ctx.translate(f.tx + ox, f.ty + oy);
      ctx.rotate((f.rz * Math.PI) / 180);
      ctx.scale(f.sx, f.sy);
      ctx.translate(-ox, -oy);
    }
    const base = ctx.globalAlpha;

    let trackC = theme.forced ? argb(theme.forced) : argb(o.track);
    let fillC = argb(o.fillColor);
    if (theme.forced && Math.abs(luminance(fillC) - luminance(trackC)) < 0.25) fillC = theme.pale ? argb('#FF15161A') : argb('#FFFFFFFF');
    const trackNow = blendARGB(argb(P.color), trackC, e);
    const surface = theme.forced ? 1 : theme.surfaceAlpha;
    ctx.fillStyle = css(trackNow, surface);
    ctx.fill(out);

    const ca = clamp((e - 0.18) / (0.55 - 0.18));
    ctx.save();
    ctx.clip(out);
    if (theme.lit) this.glass(ctx, R, theme.pale, e, 'under');
    // Number and icon over the track.
    const overTrack = pickInk(trackNow, fillC);
    this.contents(ctx, R, s.value, overTrack, ca * base);

    const committed = s.committed ?? true;
    const fillTop = R.y + R.h - R.h * clamp(s.value);
    let backdrop = fillC;
    if (this.shader) {
      const pl = this.palette;
      backdrop = this.o.fill.kind === 'surge'
        ? [255, ...[1, 2, 3].map((i) => pl[0][i] * 0.65 + pl[1][i] * 0.35)]
        : [255, ...[1, 2, 3].map((i) => (pl[0][i] + pl[1][i] + pl[2][i] + pl[3][i]) / 4)];
    }
    const kind = this.o.fill.kind;
    const canvasFill = kind === 'pixels' || kind === 'effort' || kind === 'glimmer';
    if (kind === 'pixels' || kind === 'glimmer') backdrop = trackNow;
    if (kind === 'effort') backdrop = argb(['#FF8E9AAF', '#FF4C8DFF', '#FF8B5CF6', '#FFFF7A45', '#FFFAC35F'][effortLevel(s.value)]);
    const ink = luminance(backdrop) > 0.6 ? argb('#FF14161B') : argb('#FFFFFFFF');
    if (committed && ca > 0.01) {
      if (this.shader) {
        const f = this.o.fill;
        const u = {
          origin: [R.x - this.win.x, R.y - this.win.y], size: [R.w, R.h], time: s.fillTime ?? s.t ?? 0, level: fillTop - this.win.y,
          rest: f.rest ?? (f.kind === 'surge' ? 0.1 : 0.15), scale: f.scale ?? 1, alpha: ca,
          detail: f.detail ?? 0.5, bright: f.bright ?? 1, grain: f.grain ?? 0.15,
          edge: f.edge ?? 0.5, glow: f.glow ?? 0.6, trail: f.trail ?? 0.5,
          c0: this.palette[0], c1: this.palette[1], c2: this.palette[2], c3: this.palette[3],
        };
        for (const k of ['c0', 'c1', 'c2', 'c3']) u[k] = [u[k][1] / 255, u[k][2] / 255, u[k][3] / 255, 1];
        this.shader.draw(u);
        ctx.save();
        ctx.globalAlpha = base;
        ctx.setTransform(DPR, 0, 0, DPR, -this.win.x * DPR, -this.win.y * DPR);
        ctx.drawImage(this.shader.canvas, this.win.x, this.win.y, this.win.w, this.win.h);
        ctx.restore();
      }
      if (canvasFill) {
        const ft = s.fillTime ?? s.t ?? 0;
        ctx.save();
        ctx.globalAlpha = base;
        if (kind === 'pixels') drawPixels(ctx, R, fillTop, ((ft / 2.0) * (0.55 + 1.05 * s.value)) % 1, this.o.fill, ca);
        if (kind === 'effort') drawEffort(ctx, R, fillTop, s.value, ft, css(fillC), ca);
        if (kind === 'glimmer') drawGlimmer(ctx, R, fillTop, s.value, ft, css(fillC), ca);
        ctx.restore();
      }
      ctx.save();
      const fp = new Path2D();
      fp.rect(R.x - 1, fillTop, R.w + 2, R.y + R.h - fillTop + 1);
      ctx.clip(fp);
      if (!this.shader && !canvasFill) {
        ctx.globalAlpha = base * ca;
        ctx.fillStyle = css(fillC);
        ctx.fillRect(R.x - 1, fillTop, R.w + 2, R.y + R.h - fillTop + 1);
        ctx.globalAlpha = base;
      }
      if (kind !== 'effort' && kind !== 'pixels' && kind !== 'glimmer') this.feedbackLit(ctx, R, fillTop, s, ink, ca * base);
      this.contents(ctx, R, s.value, pickInk(backdrop, trackNow), ca * base);
      ctx.restore();
      if (s.grabbed) {
        ctx.globalAlpha = base * ca;
        ctx.fillStyle = css(fillC);
        ctx.fillRect(R.x, fillTop - 1.5, R.w, 3);
        ctx.globalAlpha = base;
      }
      if (kind !== 'effort') this.feedbackPanel(ctx, out, R, fillTop, s, pickInk(trackNow, fillC), ink, ca * base);
    }
    if (theme.lit) this.glass(ctx, R, theme.pale, e, 'over');
    ctx.restore();
    if (theme.lit) this.rim(ctx, out, R, theme.pale, e);
  }

  contents(ctx, R, value, ink, alpha) {
    if (alpha <= 0.004) return;
    const o = this.o;
    const color = css(ink);
    if (o.showValue) {
      const size = clamp(o.thickness * 0.34, 9, 20);
      ctx.save();
      ctx.globalAlpha *= alpha;
      ctx.font = `700 ${size}px Roboto`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'alphabetic';
      ctx.fillStyle = color;
      ctx.fillText(String(Math.floor(clamp(value) * 100 + 1e-6)), R.x + R.w / 2, R.y + o.valueMargin + 0.9277 * size);
      ctx.restore();
    }
    if (o.showIcon) {
      const size = clamp(R.w * 0.46, 12, 26);
      drawDrawable(ctx, o.icon, R.x + R.w / 2 - size / 2, R.y + R.h - o.iconMargin - size, size, color, alpha);
    }
  }

  band(ctx, color, R, top, bottom, alpha) {
    if (alpha <= 0.004 || bottom <= top) return;
    const g = ctx.createLinearGradient(0, top, 0, bottom);
    g.addColorStop(0, css(color, 0)); g.addColorStop(0.5, css(color)); g.addColorStop(1, css(color, 0));
    ctx.save(); ctx.globalAlpha *= alpha; ctx.fillStyle = g; ctx.fillRect(R.x, top, R.w, bottom - top); ctx.restore();
  }

  feedbackLit(ctx, R, fillTop, s, ink, alpha) {
    const lit = R.y + R.h - fillTop;
    if (lit <= 0.5 || alpha <= 0.01) return;
    const fb = this.o.feedback;
    const t = s.t ?? 0;
    const level = levelAt(s.value);
    if (fb.follow) {
      const p = (t / SHEEN_S[level]) % 1;
      const thick = Math.max(28, lit * 0.45);
      const centre = R.y + R.h + thick / 2 - (lit + thick) * p;
      this.band(ctx, ink, R, centre - thick / 2, centre + thick / 2, SHEEN_A[level] * alpha);
      const flash = fadeAfter(s.stepS ?? -1, 0.45);
      if (flash > 0) {
        ctx.save(); ctx.fillStyle = css(ink); ctx.globalAlpha *= 0.16 * flash * alpha;
        ctx.fillRect(R.x, fillTop, R.w, R.y + R.h - fillTop); ctx.restore();
        ctx.save(); ctx.fillStyle = css(ink); ctx.globalAlpha *= 0.85 * flash * alpha;
        ctx.fillRect(R.x, fillTop, R.w, 2.5); ctx.restore();
      }
    }
    const fullS = s.fullS ?? -1;
    if (fb.full && fb.max === 'burst' && fullS >= 0 && fullS < 0.75) {
      const p = fullS / 0.75, eased = 1 - (1 - p) * (1 - p);
      const thick = R.h * 0.4;
      const centre = R.y + R.h + thick / 2 - (R.h + thick) * eased;
      this.band(ctx, ink, R, centre - thick / 2, centre + thick / 2, 0.6 * (1 - p) * alpha);
    }
  }

  feedbackPanel(ctx, out, R, fillTop, s, glow, ink, alpha) {
    if (alpha <= 0.01) return;
    const fb = this.o.feedback;
    const t = s.t ?? 0;
    const breath = breathAt(t);
    const v = s.value;
    if (fb.low && v < 0.15) {
      if (v <= 0.0005) {
        const w = R.w * 0.42, hh = 3.5, cx = R.x + R.w / 2, bottom = R.y + R.h - 7;
        ctx.save(); ctx.fillStyle = css(glow); ctx.globalAlpha *= (0.3 + 0.6 * breath) * alpha;
        ctx.beginPath(); ctx.roundRect(cx - w / 2, bottom - hh, w, hh, hh / 2); ctx.fill(); ctx.restore();
        this.band(ctx, glow, R, bottom - 16, bottom + 10, 0.35 * breath * alpha);
      } else {
        const strength = (0.3 + 0.55 * breath) * (1 - (0.4 * v) / 0.15);
        this.band(ctx, glow, R, fillTop - 14, fillTop + 14, strength * alpha);
      }
    }
    if (fb.full && v >= 0.995 && fb.max === 'burst') {
      const fullS = s.fullS ?? -1;
      const burst = fadeAfter(fullS, 1.1);
      const cx = R.x + R.w / 2, cy = R.y + R.h / 2;
      const rim = ctx.createConicGradient((((t / 3.2) * 360) % 360) * Math.PI / 180, cx, cy);
      EFFORT_SPECTRUM.forEach((c, i) => rim.addColorStop(i / EFFORT_SPECTRUM.length, c));
      rim.addColorStop(1, EFFORT_SPECTRUM[0]);
      ctx.save();
      ctx.strokeStyle = rim;
      ctx.lineWidth = 5 + 7 * burst;
      ctx.lineJoin = 'round';
      ctx.globalAlpha *= ((0.6 + 0.3 * breath) * (1 - burst) + burst) * alpha;
      ctx.stroke(out);
      ctx.restore();
      if (burst > 0) {
        const top = R.y + R.w * 0.5, grow = 1 - burst;
        const radius = R.w * (0.5 + 1.3 * grow);
        const g = ctx.createRadialGradient(cx, top, 0, cx, top, radius);
        g.addColorStop(0, 'rgba(255,255,255,0.8)'); g.addColorStop(0.45, 'rgba(255,255,255,0.25)'); g.addColorStop(1, 'rgba(255,255,255,0)');
        ctx.save(); ctx.globalAlpha *= burst * alpha; ctx.fillStyle = g; ctx.fillRect(cx - radius, top - radius, radius * 2, radius * 2); ctx.restore();
        ctx.save(); ctx.globalAlpha *= burst * alpha; ctx.strokeStyle = '#FAC35F'; ctx.lineWidth = 2.5;
        ctx.beginPath(); ctx.arc(cx, top, R.w * (0.2 + 1.6 * grow), 0, Math.PI * 2); ctx.stroke(); ctx.restore();
      }
    }
  }

  glass(ctx, R, pale, e, pass) {
    const k = (pale ? 0.45 : 1) * e;
    const w = (a) => `rgba(255,255,255,${clamp((a / 255) * k)})`;
    if (pass === 'under') {
      const g = ctx.createLinearGradient(0, R.y, 0, R.y + R.h * 0.5);
      g.addColorStop(0, w(0x2e)); g.addColorStop(0.55, w(0x08)); g.addColorStop(1, 'rgba(255,255,255,0)');
      ctx.fillStyle = g; ctx.fillRect(R.x, R.y, R.w, R.h);
      const c = ctx.createLinearGradient(0, R.y + R.h * 0.8, 0, R.y + R.h);
      c.addColorStop(0, 'rgba(255,255,255,0)'); c.addColorStop(1, w(0x1a));
      ctx.fillStyle = c; ctx.fillRect(R.x, R.y, R.w, R.h);
    }
  }

  rim(ctx, out, R, pale, e) {
    const k = (pale ? 0.45 : 1) * e;
    const w = (a) => `rgba(255,255,255,${clamp((a / 255) * k)})`;
    const g = ctx.createLinearGradient(0, R.y, 0, R.y + R.h);
    g.addColorStop(0, w(0xa6)); g.addColorStop(0.35, w(0x3d)); g.addColorStop(0.75, w(0x14)); g.addColorStop(1, w(0x4d));
    ctx.save(); ctx.clip(out); ctx.strokeStyle = g; ctx.lineWidth = 2.4; ctx.stroke(out); ctx.restore();
  }
}

// The default swipe: grow 220 ms (1-(1-t)^3.6) with the entrance, steps as the finger travels,
// 550 ms after lift, retract 180 ms (1-(1-t)^3.2). Returns e for time t given open/close times.
export function panelExpansion(t, openAt, closeAt = Infinity) {
  if (t < openAt) return 0;
  const grow = 1 - Math.pow(1 - clamp((t - openAt) / 0.22), 3.6);
  if (t < closeAt) return grow;
  const eAtClose = 1 - Math.pow(1 - clamp((closeAt - openAt) / 0.22), 3.6);
  const dur = clamp(0.18 * eAtClose, 0.09, 0.18);
  return eAtClose * (1 - (1 - Math.pow(1 - clamp((t - closeAt) / dur), 3.2)));
}

// A panel drawn by itself in `container` (a tile or a preview stage), w x h dp, at expansion 1:
// the settings screens' MiniFill tiles (24 x 70, 12 dp corners, no number or icon) and the
// pinned preview's SliderPreview. Returns draw({ value, t, fillTime, fullS, stepS }).
export function miniPanel(container, { w = 24, h = 70, shape = 'rounded', radius = 12, flare = 0.22, fill = { kind: 'solid' },
  showValue = false, showIcon = false, track = '#FF1C1C20', fillColor = '#FFFFFFFF', side = 'right', valueMargin = 35, iconMargin = 35, theme = 'solid' } = {}) {
  const preset = { w, h, color: track, shape, flare, radii: [radius, radius, radius, radius], edgeMargin: 0 };
  const fakePhone = { overlay: container };
  const fakeBar = { preset, rect: { x: 0, y: 0, w, h }, screenW: w, screenH: h, frameTop: 0 };
  const qp = new QuickPanel(fakePhone, fakeBar, {
    side,
    thickness: w, length: h, fill, showValue, showIcon, track, fillColor, matchShape: true, flare, theme,
    valueMargin, iconMargin, entrance: 'none',
  });
  return (s) => qp.draw({ e: 1, committed: true, ...s });
}
