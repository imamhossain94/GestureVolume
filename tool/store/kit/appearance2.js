// The Appearance screen whole (HandlerAppearanceScreen + HandlerAppearanceSettingsContent), its
// dialogs (the discard AlertDialog, IconPickerDialog, the sheets ColorDialog, the "Appearance saved"
// toast), the full-screen placement editor (HandlerPlacementEditor), and the Visibility screen whole
// (VisibilityScreen + KeyboardMotionSelector), to specs/appearance.md and specs/actions_visibility.md
// §4, at 1 dp = 1 px in the colours of m3.js.
//
// Every function takes a state object and returns an element. Everything a finger might tap carries
// data-target="<name>" (listed above each function); a video finds it with querySelector and
// getBoundingClientRect. The two scrolling screens also carry `el.layout` (see screenLayout): where
// each target sits at scroll 0 inside the scrolling column, and how far the column can scroll.
// Fonts must be loaded (fontsReady) before a screen is built: it is laid out once off-screen.
import { h, icon, drawableIcon, clamp, lerp } from './core.js';
import * as M from './m3.js';
import { tabPoints } from './quickpanel.js';
import { APPS, APP_ICONS, appIcon, gvIcon } from './phone.js';

const C = M.LIGHT;
const SW = 412, SH = 915;
/** Gesture navigation: Appearance draws behind it, every other route is padded above it. */
const NAV = 24;
/** The platform dialog dim: Theme.Material.Light's backgroundDimAmount (the app's AppCompat theme inherits it). */
const DIM = 0.6;
/** A Compose Dialog with the platform default width: ViewRootImpl tries config_prefDialogWidth first. */
const DIALOG_W = 320;

// --- Small pieces ----------------------------------------------------------------------------------
const tgt = (name) => (name ? ` data-target="${name}"` : '');
const f32 = Math.fround;
const kOf = (v) => (typeof v === 'number' ? clamp(v) : v ? 1 : 0);
const on = (v) => kOf(v) >= 0.5;
const one = 'white-space:nowrap;overflow:hidden;text-overflow:ellipsis';

/** '#RRGGBB' or '#AARRGGBB' → [alpha 0..255 | null, '#RRGGBB']. */
function splitHex(hex) {
  const v = String(hex).replace('#', '').toUpperCase();
  if (v.length === 8) return [parseInt(v.slice(0, 2), 16), '#' + v.slice(2)];
  if (v.length === 3) return [null, '#' + v.split('').map((c) => c + c).join('')];
  return [null, '#' + v.slice(-6)];
}
function rgba(hex, a = 1) {
  const n = parseInt(splitHex(hex)[1].slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${+a.toFixed(4)})`;
}

// Text that only sets a size inherits the app's bodyLarge: 24 sp lines, 0.5 sp tracking, and the
// first and last lines trimmed to the font (LineHeightStyle.Default). One line is 1.172 × the size
// tall; wrapped lines are `lh` apart. flow-root keeps the negative margins inside the wrapper.
const inh = (s, size, weight = 400, color = C.onSurface, extra = '') =>
  `<div style="font:${weight} ${size}px/1.172 Roboto;letter-spacing:.5px;color:${color};${extra}">${s}</div>`;
const inhWrap = (s, size, weight = 400, color = C.onSurface, { lh = 24, extra = '', inner = '' } = {}) =>
  `<div style="display:flow-root;${extra}"><div style="font:${weight} ${size}px/${lh}px Roboto;letter-spacing:.5px;color:${color};margin:${(-(lh - 1.172 * size) / 2).toFixed(2)}px 0;${inner}">${s}</div></div>`;
const bodySmall = (s, color = C.onSurfaceVariant, extra = '') =>
  `<div style="font:400 12px/16px Roboto;letter-spacing:.4px;color:${color};${extra}">${s}</div>`;
const bodyMedium = (s, color = C.onSurfaceVariant, extra = '') =>
  `<div style="font:400 14px/20px Roboto;letter-spacing:.25px;color:${color};${extra}">${s}</div>`;
const labelLarge = 'font:500 14px/20px Roboto;letter-spacing:.1px';
const divider = () => `<div style="padding:12px 0"><div style="height:1px;background:rgba(31,41,55,.10)"></div></div>`;

let measureCtx = null;
function textWidth(s, font) {
  if (!measureCtx) measureCtx = document.createElement('canvas').getContext('2d');
  measureCtx.font = font;
  return measureCtx.measureText(s).width;
}

// --- Laying a screen out once, off-screen ------------------------------------------------------------
let host = null;
function withMounted(el, fn) {
  if (el.isConnected) return fn(el);
  if (!host) {
    host = document.createElement('div');
    host.style.cssText = `position:fixed;left:-30000px;top:0;width:${SW}px;height:${SH}px;visibility:hidden;pointer-events:none;contain:strict`;
  }
  if (!host.isConnected) document.body.appendChild(host);
  host.appendChild(el);
  try { return fn(el); } finally { host.removeChild(el); }
}

/**
 * Where everything with a data-target sits in a screen built here, in screen dp:
 * { targets: { name: {x, y, w, h} } as built (at its own scroll), content: { name: y } (top inside the
 * scrolling column, i.e. the screen y at scroll 0 minus viewportTop), viewportTop, viewportH,
 * contentH, maxScroll }. To bring a row whose content y is Y to screen y Ys: scroll = Y + viewportTop − Ys.
 */
export function screenLayout(el) {
  return withMounted(el, () => {
    const box = el.getBoundingClientRect();
    const out = { targets: {}, content: {}, viewportTop: 0, viewportH: 0, contentH: 0, maxScroll: 0 };
    const vp = el.querySelector('.scroll'), inner = el.querySelector('.scroll-content');
    if (vp && inner) {
      const r = vp.getBoundingClientRect();
      out.viewportTop = +(r.top - box.top).toFixed(1);
      out.viewportH = +r.height.toFixed(1);
      out.contentH = +inner.getBoundingClientRect().height.toFixed(1);
      out.maxScroll = +Math.max(0, out.contentH - out.viewportH).toFixed(1);
      const top0 = inner.getBoundingClientRect().top;
      for (const t of inner.querySelectorAll('[data-target]')) {
        // A pinned (sticky) piece reports where it would be in the column, not where it is pinned.
        const pin = t.closest('[data-pinned]');
        const shift = pin ? +pin.dataset.pinned : 0;
        out.content[t.dataset.target] = +(t.getBoundingClientRect().top - top0 - shift).toFixed(1);
      }
    }
    for (const t of el.querySelectorAll('[data-target]')) {
      const r = t.getBoundingClientRect();
      out.targets[t.dataset.target] = { x: +(r.left - box.left).toFixed(1), y: +(r.top - box.top).toFixed(1), w: +r.width.toFixed(1), h: +r.height.toFixed(1) };
    }
    return out;
  });
}

// --- Top bar (M3 small TopAppBar, transparent) ---------------------------------------------------------
// Back glyph at x 16–40; title from x 56 in onPrimaryContainer; actions 48 wide, 4 from the end.
function topBar(title, { status = 32, actions = '' } = {}) {
  return `<div style="height:${status + 64}px;padding-top:${status}px;display:flex;align-items:center;flex:none">
      <div style="width:4px;flex:none"></div>
      <div${tgt('back')} style="width:48px;height:48px;display:grid;place-items:center;flex:none">${icon('filled/arrow_back', 24, C.onSurface)}</div>
      <div style="flex:1;min-width:0;padding-left:4px;font:400 22px/28px Roboto;color:${C.onPrimaryContainer};${one}">${title}</div>
      ${actions}<div style="width:4px;flex:none"></div></div>`;
}

// --- Controls (SliderControl, SwitchControl, segmented halves, colour and icon rows) --------------------
/*
 * SliderControl: a 28 dp label row (label 15 Medium, −, the value 12 Bold primary in 48, +) over a
 * 28 dp track. The track canvas runs from x 2 to W−2 (M3's 4 dp handle inset) clipped to a stadium;
 * the 4 × 18 thumb's centre is 2 + (canvas − 4) × fraction in it, 6 × 22.5 while dragged. A nudge
 * button at the end of its range is disabled: no disc, its glyph primary @ 30%.
 */
function slider(name, label, display, frac, { min = false, max = false, dragging = false, width = 348 } = {}) {
  const nudge = (kind, off) => `<div${tgt(`${kind}-${name}`)} style="width:28px;height:28px;border-radius:50%;flex:none;display:grid;place-items:center;
      background:${off ? 'transparent' : 'rgba(79,70,229,.10)'}">${icon(kind === 'minus' ? 'filled/remove' : 'filled/add', 14, off ? 'rgba(79,70,229,.3)' : C.primary)}</div>`;
  const cw = width - 4;
  const cx = 2 + (cw - 4) * clamp(frac);
  const tw = dragging ? 6 : 4, th = dragging ? 22.5 : 18;
  return `<div style="height:56px">
      <div style="height:28px;display:flex;align-items:center;gap:2px">
        ${inh(label, 15, 500, C.onSurface, `flex:1;min-width:0;${one}`)}${nudge('minus', min)}
        <div style="min-width:48px;text-align:center;font:700 12px/1.172 Roboto;letter-spacing:.5px;color:${C.primary};white-space:nowrap">${display}</div>${nudge('plus', max)}</div>
      <div${tgt(`slider-${name}`)} style="position:relative;height:28px">
        <div style="position:absolute;left:2px;top:0;width:${cw}px;height:28px;border-radius:14px;overflow:hidden">
          <div style="position:absolute;left:0;right:0;top:12px;height:4px;border-radius:2px;background:rgba(79,70,229,.18)"></div>
          <div style="position:absolute;left:0;top:12px;width:${cx}px;height:4px;border-radius:2px;background:${C.primary}"></div>
          <div${tgt(`slider-${name}-thumb`)} style="position:absolute;left:${cx - tw / 2}px;top:${14 - th / 2}px;width:${tw}px;height:${th}px;border-radius:${tw / 2}px;background:${C.primary}"></div>
        </div></div></div>`;
}

/* SwitchControl: label 15 Medium (wraps on a 24 dp pitch), 12, the 52 × 32 switch; padding 4, so 40 tall.
   The whole row toggles (row-<name>); switch-<name> is the switch itself. k: 0 off .. 1 on. */
function switchRow(name, label, v) {
  return `<div${tgt(`row-${name}`)} style="display:flex;align-items:center;padding:4px 0;border-radius:14px">
      ${inhWrap(label, 15, 500, C.onSurface, { extra: 'flex:1;min-width:0' })}<div style="width:12px;flex:none"></div>
      <div${tgt(`switch-${name}`)} style="flex:none">${M.m3switchAt(kOf(v))}</div></div>`;
}

// Two halves of one pill (SideSelector, ShapeSelector, ListSelector): track onSurface @ 6%, padding
// and gap 3; each half 13 SemiBold on 9 dp padding, radius 8, primary when chosen.
function halves(items, radius = 10) {
  return `<div style="display:flex;gap:3px;padding:3px;border-radius:${radius}px;background:rgba(31,41,55,.06)">
      ${items.map((it) => `<div${tgt(it.target)} style="flex:1;min-width:0;border-radius:8px;padding:9px 0;text-align:center;font:600 13px/1.172 Roboto;letter-spacing:.5px;${one};
        background:${it.selected ? C.primary : 'transparent'};color:${it.selected ? C.onPrimary : C.onSurfaceVariant}">${it.label}</div>`).join('')}</div>`;
}

// ColorPickerControl: 58 tall; a 46 dp swatch (radius 14, 1 dp outline @ 35%), 14, the label (16
// SemiBold) over #RRGGBB (14 Medium), the pencil.
function colorRow(target, label, hex, swatch) {
  return `<div${tgt(target)} style="display:flex;align-items:center;padding:6px 0;border-radius:14px">
      <div style="width:46px;height:46px;border-radius:14px;flex:none;background:${swatch};box-shadow:inset 0 0 0 1px rgba(209,213,219,.35)"></div>
      <div style="width:14px;flex:none"></div>
      <div style="flex:1;min-width:0">${inh(label, 16, 600)}${inh(hex, 14, 500, C.onSurfaceVariant, 'padding-top:2px')}</div>
      ${icon('filled/edit', 20, C.outline)}</div>`;
}

// IconPickerControl: "Icon" 13 Medium @ 60%, 8, then a clear box (radius 12, 0.5 dp primary @ 10%,
// padding 14): the icon 24 dp in primary, 12, "Tap to change", and the pencil at the end.
function iconControl(L) {
  return `<div>${inh('Icon', 13, 500, 'rgba(31,41,55,.6)', 'padding-bottom:8px')}
      <div${tgt('icon-picker')} style="border-radius:12px;box-shadow:inset 0 0 0 .5px rgba(79,70,229,.1);padding:14px;display:flex;align-items:center;justify-content:space-between">
        <div style="display:flex;align-items:center">${drawableIcon(L.icon, 24, C.primary)}<div style="width:12px"></div>${inh('Tap to change', 14, 500)}</div>
        ${icon('filled/edit', 20, C.outline)}</div></div>`;
}

const subgroupLabel = (s, extra = '') => `<div style="padding-bottom:6px;font:600 14px/20px Roboto;letter-spacing:.1px;color:${C.primary};${extra}">${s}</div>`;

// expandVertically / shrinkVertically part-way: a clip k of its content's own height (fading in a
// little faster than it grows). The height is set once the screen is laid out (settle).
const expander = (inner, k) => `<div class="expander" data-k="${clamp(k)}" style="overflow:hidden;opacity:${clamp(k * 1.6)}">${inner}</div>`;
function settle(el) {
  const xs = [...el.querySelectorAll('.expander')].filter((x) => +x.dataset.k < 1);
  if (!xs.length) return;
  withMounted(el, () => {
    const hs = xs.map((x) => x.getBoundingClientRect().height);
    xs.forEach((x, i) => { x.style.height = `${(hs[i] * +x.dataset.k).toFixed(2)}px`; });
  });
}

// A Button laid out in its 48 dp touch box (4 dp clear above and below the 40 dp you see).
const touchBox = (inner, extra = '') => `<div style="height:48px;display:flex;align-items:center;${extra}">${inner}</div>`;

// --- The bar (HandlerView + HandlerShapeDrawable) --------------------------------------------------------
/** Rounded rect with corners [TL, TR, BR, BL], shrunk together when they do not fit (SkRRect). */
function rrectPath(x, y, w, hh, radii) {
  let [tl, tr, br, bl] = radii.map((r) => Math.max(0, r));
  const f = Math.min(1, w / Math.max(tl + tr, 1e-9), w / Math.max(bl + br, 1e-9), hh / Math.max(tl + bl, 1e-9), hh / Math.max(tr + br, 1e-9));
  tl *= f; tr *= f; br *= f; bl *= f;
  const n = (v) => +v.toFixed(3);
  return `M${n(x + tl)} ${n(y)}H${n(x + w - tr)}A${n(tr)} ${n(tr)} 0 0 1 ${n(x + w)} ${n(y + tr)}V${n(y + hh - br)}A${n(br)} ${n(br)} 0 0 1 ${n(x + w - br)} ${n(y + hh)}`
    + `H${n(x + bl)}A${n(bl)} ${n(bl)} 0 0 1 ${n(x)} ${n(y + hh - bl)}V${n(y + tl)}A${n(tl)} ${n(tl)} 0 0 1 ${n(x + tl)} ${n(y)}Z`;
}
/** The tab: inset by half the stroke on the three drawn sides; the fill closes along the screen edge. */
function tabPaths(w, hh, flare, edgeOnLeft, strokeW = 0) {
  const i = strokeW / 2, ox = edgeOnLeft ? 0 : i;
  const d = 'M' + tabPoints(w - i, hh - 2 * i, flare, edgeOnLeft).map(([x, y]) => (ox + x).toFixed(2) + ' ' + (i + y).toFixed(2)).join(' L');
  return { fill: d + ' Z', stroke: d };
}
const INTRINSIC = { ic_move: 32, ic_vol_plus: 25, ic_vol_minus: 25 };

/*
 * The bar at its true size: fill and outline (Paint defaults: miter joins, butt caps), and in the
 * middle either the icon (iconSize, never larger than the drawable — CENTER_INSIDE — tinted, clipped
 * to the bar) or the "NN%" readout (Roboto Regular, 15 sp shrinking to 8 to fit). cue 0..1 is the
 * drag cue: the whole bar at 65%, the outline at least 2 dp in the icon colour, ic_move in the middle.
 */
export function barHtml(L, { h: bh = L.h, edgeOnLeft = L.side === 'left', cue = 0, readout = null } = {}) {
  const w = L.w, cueOn = cue > 0;
  const sw = cueOn ? Math.max(L.strokeW, 2) : L.strokeW;
  const fill = rgba(L.fill, L.alpha / 255);
  const ink = rgba(L.iconColor, L.iconColorAlpha / 255);
  const line = cueOn ? ink : rgba(L.strokeColor, L.strokeAlpha / 255);
  let paths;
  if (L.shape === 'tab') {
    const p = tabPaths(w, bh, L.flare, edgeOnLeft, sw);
    paths = `<path d="${p.fill}" fill="${fill}"/>${sw > 0 ? `<path d="${p.stroke}" fill="none" stroke="${line}" stroke-width="${sw}"/>` : ''}`;
  } else {
    const d = rrectPath(sw / 2, sw / 2, w - sw, bh - sw, L.radii);
    paths = `<path d="${d}" fill="${fill}"/>${sw > 0 ? `<path d="${d}" fill="none" stroke="${line}" stroke-width="${sw}"/>` : ''}`;
  }
  let middle = '';
  if (readout != null && !cueOn) {
    let size = 15;
    while (size > 8 && textWidth(readout, `400 ${size}px Roboto`) > w - 2) size -= 1;
    middle = `<div style="position:absolute;inset:0;display:grid;place-items:center;font:400 ${size}px/1 Roboto;color:${ink};white-space:nowrap">${readout}</div>`;
  } else if (cueOn || on(L.showIcon)) {
    const name = cueOn ? 'ic_move' : L.icon;
    const s = Math.min(L.iconSize, INTRINSIC[name] ?? 24);
    middle = `<div style="position:absolute;left:${(w - s) / 2}px;top:${(bh - s) / 2}px">${drawableIcon(name, s, ink)}</div>`;
  }
  return `<div class="bar" style="position:relative;width:${w}px;height:${bh}px;opacity:${cueOn ? lerp(1, 0.65, clamp(cue)) : 1}">
      <svg width="${w}" height="${bh}" style="position:absolute;left:0;top:0;overflow:visible">${paths}</svg>
      <div style="position:absolute;inset:0;overflow:hidden">${middle}</div></div>`;
}

// --- Looks (utils/HandlerPresets.kt) ---------------------------------------------------------------
// fill and alpha are the app's bgColor and bgAlpha (the bar is drawn in fill at alpha); colorAlpha is
// the alpha a colour picked in the dialog carries, which only its swatch shows. The same for the
// outline (strokeColor, strokeW, strokeAlpha) and the icon (iconColor, iconColorAlpha).
const BASE_LOOK = {
  classic: { w: 30, h: 100, fill: '#4F46E5', alpha: 128, strokeColor: '#FFFFFF', strokeW: 1, strokeAlpha: 200, radii: [15, 15, 15, 15], shape: 'rounded', iconSize: 18, showIcon: false, vibrate: false, edgeMargin: 0, dynamic: false, placement: true },
  dock: { w: 14, h: 120, fill: '#000000', alpha: 255, strokeColor: '#FFFFFF', strokeW: 0, strokeAlpha: 200, radii: [8, 8, 8, 8], shape: 'tab', iconSize: 16, showIcon: false, vibrate: false, edgeMargin: 0, dynamic: true, placement: true },
  edge: { w: 12, h: 95, fill: '#000000', alpha: 255, strokeColor: '#FFFFFF', strokeW: 0, strokeAlpha: 200, radii: [10, 1, 1, 10], shape: 'rounded', iconSize: 18, showIcon: false, vibrate: false, edgeMargin: 0, dynamic: true, placement: true },
  bold: { w: 46, h: 46, fill: '#1F2937', alpha: 140, strokeColor: '#FFFFFF', strokeW: 1.5, strokeAlpha: 90, radii: [23, 23, 23, 23], shape: 'rounded', iconSize: 24, showIcon: true, vibrate: true, edgeMargin: 8, dynamic: false, placement: false },
};
const LOOK_DEFAULTS = {
  flare: 0.29, icon: 'ic_vol_increase', iconColor: '#FFFFFF', side: 'right', x: null, y: 0.21, landscapeX: null, landscapeY: 0.21,
  snap: true, same: false, showPercent: true, colorAlpha: 255, strokeColorAlpha: 255, iconColorAlpha: 255,
};
export const PRESET_IDS = ['classic', 'dock', 'edge', 'bold'];
const PRESET_NAMES = { classic: 'Classic', dock: 'Dock', edge: 'Edge', bold: 'Bold' };

/**
 * A preset's look with `look` laid over it. Besides the fields above, the shorthands of
 * quickpanel.js's PRESETS are understood: color '#AARRGGBB' (AA = the fill alpha), stroke { w, color }
 * or null. A field may be a number part-way for a switch (showIcon, vibrate, dynamic, same, snap,
 * showPercent: 0 off .. 1 on).
 */
export function resolveLook(preset = 'dock', look = {}) {
  const L = { ...LOOK_DEFAULTS, ...(BASE_LOOK[preset] ?? BASE_LOOK.dock) };
  const o = { ...look };
  const take = (key, rgbKey, alphaKey) => {
    if (o[key] == null) return;
    const [a, rgb] = splitHex(o[key]);
    L[rgbKey] = rgb;
    if (a != null) L[alphaKey] = a;
    delete o[key];
  };
  take('color', 'fill', 'alpha');
  if (o.fill != null) take('fill', 'fill', 'alpha');
  if (o.stroke !== undefined) {
    if (o.stroke === null) L.strokeW = 0;
    else {
      if (o.stroke.w != null) L.strokeW = o.stroke.w;
      if (o.stroke.color) { const [a, rgb] = splitHex(o.stroke.color); L.strokeColor = rgb; if (a != null) L.strokeAlpha = a; }
    }
    delete o.stroke;
  }
  take('strokeColor', 'strokeColor', 'strokeAlpha');
  take('iconColor', 'iconColor', 'iconColorAlpha');
  Object.assign(L, o);
  L.icon = String(L.icon).replace(/^drawable\//, '');
  L.radii = (L.radii ?? [8, 8, 8, 8]).slice(0, 4);
  if (L.x == null) L.x = L.side === 'left' ? 0 : 1;
  if (L.landscapeX == null) L.landscapeX = L.x;
  return L;
}

/** HandlerPresets.Preset.matches: the look only (plus the snap for a preset with a placement). */
function matchesPreset(id, L) {
  const P = resolveLook(id);
  const eq = (a, b) => Math.abs(a - b) < 1e-6;
  return (!P.placement || on(L.snap) === on(P.snap)) && eq(L.w, P.w) && eq(L.h, P.h)
    && L.fill.toUpperCase() === P.fill && L.colorAlpha === 255 && L.alpha === P.alpha
    && L.strokeColor.toUpperCase() === P.strokeColor && L.strokeColorAlpha === 255 && eq(L.strokeW, P.strokeW) && L.strokeAlpha === P.strokeAlpha
    && L.radii.every((r, i) => eq(r, P.radii[i])) && L.shape === P.shape && (P.shape !== 'tab' || eq(L.flare, P.flare))
    && L.icon === P.icon && eq(L.iconSize, P.iconSize) && L.iconColor.toUpperCase() === P.iconColor && L.iconColorAlpha === 255
    && on(L.showIcon) === P.showIcon && on(L.vibrate) === P.vibrate && eq(L.edgeMargin, P.edgeMargin);
}

const pct255 = (a) => Math.trunc(f32(f32(a / 255) * 100));              // ((alpha / 255f) * 100).toInt()
const trunc = (v) => Math.trunc(f32(v));                                 // a Float's toInt()
const pctRound = (fr) => Math.round(f32(fr) * 100);                      // (fraction * 100).roundToInt()

// --- The preview stage (PreviewStage + HandlerPreviewSurface + HandlerPreviewEffects) -------------------
const STAGE_H = clamp(0.27 * Math.max(SW, SH), 212, 320);

// Android's volume panel stand-in: radius 22, #1F1F24 @ 94%, elevation 6; padding 6 round a 34 × 142
// column: the track (radius 17, white 14%, #D0BCFF filling from the bottom), 8, the speaker (16 in
// an 18 dp box with 2 dp under it). Beside the bar's inner side, 14 dp off; it scales in from 0.8.
function volumePanel(L, k, level, boxH, left) {
  const reach = L.edgeMargin + L.w + 14;
  const fillH = 116 * clamp(level / 100);
  return `<div style="position:absolute;${left ? 'left' : 'right'}:${reach}px;top:${boxH / 2 - 77}px;width:46px;height:154px;padding:6px;border-radius:22px;
      background:rgba(31,31,36,.941);box-shadow:0 3px 6px rgba(0,0,0,.18),0 2px 4px rgba(0,0,0,.14);opacity:${clamp(k)};transform:scale(${lerp(0.8, 1, clamp(k))});transform-origin:${left ? '0' : '100%'} 50%">
      <div style="position:relative;width:34px;height:116px;border-radius:17px;background:rgba(255,255,255,.14);overflow:hidden">
        <div style="position:absolute;left:0;right:0;bottom:0;height:${fillH}px;border-radius:17px;background:#D0BCFF"></div></div>
      <div style="height:8px"></div><div style="height:18px;display:flex;justify-content:center">${icon('filled/volume_up', 16, '#fff')}</div></div>`;
}
// The brightness readout: radius 18, #181818 @ 86%, padding 16 × 10, the sun 18 and "Brightness NN%".
function brightnessChip(k, level, boxW, boxH) {
  return `<div style="position:absolute;left:0;top:0;width:${boxW}px;height:${boxH}px;display:grid;place-items:center;opacity:${clamp(k)}">
      <div style="transform:scale(${lerp(0.9, 1, clamp(k))});display:flex;align-items:center;gap:8px;padding:10px 16px;border-radius:18px;background:rgba(24,24,24,.863)">
        ${drawableIcon('ic_brightness_up', 18, '#FFFFFF')}${bodyMedium(`Brightness ${Math.round(level)}%`, '#fff', 'white-space:nowrap')}</div></div>`;
}

function stageHtml(L, demo) {
  const Ws = SW - 32, Hs = STAGE_H;
  const left = L.side === 'left';
  const stage = M.previewStage(Ws, Hs, (g) => {
    const boxW = g.glass.w / g.scale, boxH = g.glass.h / g.scale;
    const bh = Math.min(L.h, 260);
    let s = `<div${tgt('preview-bar')} style="position:absolute;${left ? 'left' : 'right'}:${L.edgeMargin}px;top:${boxH / 2 - bh / 2}px">${barHtml(L, { h: bh, edgeOnLeft: left, readout: demo.readout })}</div>`;
    if (demo.brightness > 0) s += `<div style="position:absolute;inset:0;background:#000;opacity:${(1 - demo.brightnessLevel / 100) * 0.55 * clamp(demo.brightness)}"></div>`;
    if (demo.panel > 0) s += volumePanel(L, demo.panel, demo.level, boxH, left);
    if (demo.brightness > 0) s += brightnessChip(demo.brightness, demo.brightnessLevel, boxW, boxH);
    return s;
  });
  stage.setAttribute('data-target', 'preview');
  if (demo.caption && demo.captionK > 0) {
    const glyph = demo.captionIcon ? (demo.captionIcon.startsWith('drawable/') ? drawableIcon(demo.captionIcon, 18, C.primary) : icon(demo.captionIcon, 18, C.primary)) : '';
    stage.insertAdjacentHTML('beforeend', `<div style="position:absolute;left:12px;right:12px;bottom:14px;display:flex;justify-content:center;opacity:${clamp(demo.captionK)}">${M.demoCaption(demo.caption, glyph)}</div>`);
  }
  return stage.outerHTML;
}

// --- Appearance ---------------------------------------------------------------------------------------
// AppearanceSection: a 70 dp header (tile 46, radius 14; primaryContainer @ 60% → primary when open),
// the title 16 SemiBold, the summary 14 on one line, the chevron turning 180°; open, a body card 3 dp
// under it (corners 6/20, padding 16). k: 0 shut .. 1 open — the body's height is k of its own.
function section(id, glyph, title, summary, k, body) {
  const kk = clamp(k);
  const join = lerp(20, 6, kk);
  const tile = `<div style="position:relative;width:46px;height:46px;border-radius:14px;flex:none;overflow:hidden;background:rgba(224,231,255,.6)">
      <div style="position:absolute;inset:0;background:${C.primary};opacity:${kk}"></div>
      <div style="position:absolute;inset:0;display:grid;place-items:center;opacity:${1 - kk}">${icon(glyph, 24, C.primary)}</div>
      <div style="position:absolute;inset:0;display:grid;place-items:center;opacity:${kk}">${icon(glyph, 24, C.onPrimary)}</div></div>`;
  const header = `<div${tgt(`section-${id}`)} style="display:flex;align-items:center;padding:12px 14px;background:${C.row};border-radius:20px 20px ${join}px ${join}px">
      ${tile}<div style="width:14px;flex:none"></div>
      <div style="flex:1;min-width:0">${inh(title, 16, 600)}${summary ? inh(summary, 14, 400, C.onSurfaceVariant, `padding-top:2px;${one}`) : ''}</div>
      <div style="transform:rotate(${180 * kk}deg);flex:none">${icon('filled/expand_more', 24, C.outline)}</div></div>`;
  const content = kk > 0 ? expander(`<div style="padding-top:3px"><div class="section-body" data-section="${id}" style="background:${C.row};border-radius:6px 6px 20px 20px;padding:16px">${body}</div></div>`, kk) : '';
  return `<div class="section" data-section="${id}">${header}${content}</div>`;
}

const SECTIONS = ['content', 'size', 'colours', 'behaviour', 'position'];
function openMap(open) {
  const m = {};
  if (open == null || open === false) return m;
  if (typeof open === 'string') m[open] = 1;
  else if (Array.isArray(open)) open.forEach((id) => { m[id] = 1; });
  else Object.assign(m, open);
  for (const id of Object.keys(m)) if (!SECTIONS.includes(id)) throw new Error('unknown section: ' + id);
  return m;
}

// The settings, group by group (HandlerAppearanceSettingsContent).
function contentBody(L) {
  return `${switchRow('show-icon', 'Show icon', L.showIcon)}
      ${on(L.showIcon) ? `${divider()}${iconControl(L)}` : ''}
      ${divider()}${switchRow('percent', 'Show volume percentage', L.showPercent)}
      ${bodySmall('Display the level on the handler while you swipe.')}`;
}

function sizeBody(L, { dragging, perCorner }) {
  const d = (n) => dragging === n;
  const sl = (name, label, v, lo, hi, display) => slider(name, label, display, (v - lo) / (hi - lo), { min: v <= lo, max: v >= hi, dragging: d(name) });
  const tab = L.shape === 'tab';
  const [tl, tr, br, bl] = L.radii;
  const uniform = tl === tr && tr === bl && bl === br;
  const pc = perCorner ?? !uniform;
  let s = `${sl('width', 'Width', L.w, 1, 200, `${trunc(L.w)}dp`)}${divider()}${sl('height', 'Height', L.h, 8, 200, `${trunc(L.h)}dp`)}${divider()}
      ${halves([{ target: 'shape-rounded', label: 'Rounded', selected: !tab }, { target: 'shape-tab', label: 'Tab', selected: tab }])}
      ${bodySmall(tab ? 'Both ends sweep back into the side of the screen. Keep the bar flush to the edge, or the curves have nothing to meet.' : 'A rectangle with corners you set below.', C.onSurfaceVariant, 'padding-top:8px')}`;
  if (tab) {
    const fl = L.flare * 100;
    s += `${divider()}${sl('sweep', 'End sweep', fl, 4, 50, `${trunc(f32(f32(L.flare) * 100))}%`)}
        ${bodySmall("How much of the bar's height each curve takes. At the top of the range the two meet and the bar becomes a leaf.", C.onSurfaceVariant, 'padding-top:8px')}`;
  } else {
    s += `${divider()}${sl('corners', 'All Corners', tl, 0, 50, uniform ? `${trunc(tl)}dp` : 'Mixed')}${divider()}${switchRow('per-corner', 'Set corners individually', pc)}`;
    if (on(pc)) {
      s += [['corner-tl', 'Top Left', tl], ['corner-tr', 'Top Right', tr], ['corner-bl', 'Bottom Left', bl], ['corner-br', 'Bottom Right', br]]
        .map(([n, label, v]) => `${divider()}${sl(n, label, v, 0, 50, `${trunc(v)}dp`)}`).join('');
    }
  }
  s += `${divider()}${subgroupLabel('Outline')}${sl('outline-width', 'Width', L.strokeW, 0, 8, `${trunc(L.strokeW)}dp`)}`;
  if (on(L.showIcon)) s += `${divider()}${sl('icon-size', 'Icon Size', L.iconSize, 16, 48, `${trunc(L.iconSize)}dp`)}`;
  return s;
}

function coloursBody(L, { dragging }) {
  const op = (name, a) => slider(name, 'Opacity', `${pct255(a)}%`, a / 255, { min: a <= 0, max: a >= 255, dragging: dragging === name });
  let s = `${subgroupLabel('Background')}${colorRow('color-fill', 'Color', L.fill.toUpperCase(), rgba(L.fill, L.colorAlpha / 255))}${divider()}${op('fill-opacity', L.alpha)}
      ${divider()}${subgroupLabel('Outline')}${colorRow('color-stroke', 'Color', L.strokeColor.toUpperCase(), rgba(L.strokeColor, L.strokeColorAlpha / 255))}${divider()}${op('stroke-opacity', L.strokeAlpha)}`;
  if (on(L.showIcon)) s += `${divider()}${colorRow('color-icon', 'Icon Color', L.iconColor.toUpperCase(), rgba(L.iconColor, L.iconColorAlpha / 255))}`;
  return s;
}

function positionBody(L, { dragging, snapNote }) {
  const sl = (name, label, v, lo, hi, display) => slider(name, label, display, (v - lo) / (hi - lo), { min: v <= lo, max: v >= hi, dragging: dragging === name });
  const group = (title, prefix, x, y) => `<div style="padding:14px 0 6px;font:600 14px/20px Roboto;letter-spacing:.1px;color:${C.primary}">${title}</div>
      ${sl(`${prefix}from-left`, 'From left', x * 100, 0, 100, `${pctRound(x)}%`)}${sl(`${prefix}from-top`, 'From top', y * 100, 0, 100, `${pctRound(y)}%`)}`;
  const button = touchBox(`<div${tgt('placement')} style="flex:1;height:40px;border-radius:12px;background:${C.secondaryContainer};display:flex;align-items:center;justify-content:center;gap:8px;
      ${labelLarge};color:${C.onSecondaryContainer}">${icon('filled/open_with', 18, C.onSecondaryContainer)}Set initial position</div>`);
  let s = `${button}${bodySmall('Opens your screen at full size with the bar where it starts. Hold anywhere, then drag to move it into place.', C.onSurfaceVariant, 'padding-top:6px')}
      ${divider()}${halves([{ target: 'side-left', label: 'Left', selected: L.side === 'left' }, { target: 'side-right', label: 'Right', selected: L.side !== 'left' }])}
      ${divider()}${switchRow('dynamic', 'Dynamic position', L.dynamic)}
      ${bodySmall('Moves the bar with the screen when you rotate the phone. It keeps to the same edge of the phone and lies along the top or bottom in landscape, always resting against the edge. Off, it stays upright on its side of the screen.')}
      ${divider()}`;
  if (on(L.dynamic)) {
    s += `${sl('along-edge', 'Position along edge', L.y, 0, 1, `${pctRound(L.y)}%`)}
        ${bodySmall('Where the middle of the bar sits along its edge, measured with the phone upright. Turned on its side, the bar keeps the same place on the glass.')}`;
  } else {
    s += `${switchRow('same-position', 'Same position in portrait and landscape', L.same)}
        ${bodySmall('Off, the bar keeps a place of its own for each way you hold the phone.')}
        ${on(L.same) ? group('Portrait and landscape', '', L.x, L.y) : `${group('Portrait · now', '', L.x, L.y)}${group('Landscape', 'landscape-', L.landscapeX, L.landscapeY)}`}
        ${bodySmall('Where the middle of the bar sits, as a share of the screen. You can also hold the bar and drag it; that position is saved for the way the phone is held at the time.', C.onSurfaceVariant, 'padding-top:8px')}
        ${divider()}${switchRow('snap', 'Snap to edge', L.snap)}
        ${snapNote && !on(L.snap)
          ? bodySmall('Away from an edge, so Snap to edge was switched off. Turn it back on to send the bar to the nearer side.', C.primary)
          : bodySmall('On: the bar flies to the nearer side when you let go and parks at the edge distance below. Off: it stays exactly where you dropped it. Height is always yours either way.')}`;
  }
  s += `${divider()}${sl('edge-distance', 'Edge distance', L.edgeMargin, 0, 48, `${trunc(L.edgeMargin)}dp`)}
      ${bodySmall('The smallest gap the bar keeps from the left and right edges — while you drag it, and where it parks when it snaps. Helps on curved screens and when gesture navigation is on.')}
      ${divider()}${touchBox(`<div${tgt('reset-position')} style="height:40px;padding:0 12px;border-radius:20px;display:flex;align-items:center;${labelLarge};color:${C.primary}">Reset position</div>`, 'justify-content:flex-end')}`;
  return s;
}

// The quick preset chips: radius 20, padding 12 × 8 (36 tall), a 14 dp swatch in the preset's fill at
// its alpha, 8, the name in labelLarge; primaryContainer when the look is the preset's.
function presetChip(id, selected) {
  const P = resolveLook(id);
  return `<div${tgt(`preset-${id}`)} style="height:36px;border-radius:20px;display:flex;align-items:center;padding:0 12px;flex:none;
      background:${selected ? C.primaryContainer : C.surfaceVariant};${labelLarge};color:${selected ? C.onPrimaryContainer : C.onSurfaceVariant}">
      <span style="width:14px;height:14px;border-radius:50%;background:${rgba(P.fill, P.alpha / 255)}"></span><span style="width:8px"></span>${PRESET_NAMES[id]}</div>`;
}

// Android's toast (SystemUI text_toast): the app icon 24, 10, the text 14/20; padding 16; on the
// surface colour with 28 dp corners and 2 dp of elevation, 48 dp above the navigation bar.
function toast(text, k) {
  return `<div style="position:absolute;left:0;right:0;bottom:${NAV + 48}px;display:flex;justify-content:center;opacity:${clamp(k)};pointer-events:none">
      <div style="display:flex;align-items:center;max-width:300px;padding:0 16px;border-radius:28px;background:#F3EDF7;
          box-shadow:0 1px 3px rgba(0,0,0,.18),0 1px 2px rgba(0,0,0,.12)">
        <div style="margin:10px 10px 10px 0">${gvIcon(24)}</div>
        <div style="padding:12px 0;font:400 14px/20px Roboto;color:#1D1B20;white-space:nowrap">${text}</div></div></div>`;
}

/*
 * The Appearance screen: the top bar (back, ?, save), the pinned preview stage and its line, then the
 * scrolling settings — "Quick presets" and their chips, then Content, Size & shape, Colours, Behaviour
 * and Position, 12 apart.
 *
 *   preset     'classic' | 'dock' | 'edge' | 'bold': the look the draft starts from
 *   look       overrides of it (see resolveLook): w, h, color, alpha, colorAlpha, stroke | strokeColor,
 *              strokeW, strokeAlpha, shape, flare, radii [TL, TR, BR, BL], showIcon, icon ('ic_star'),
 *              iconSize, iconColor, vibrate, side, edgeMargin, x, y, landscapeX, landscapeY, dynamic,
 *              same, snap, showPercent. The stage draws the bar with it; no chip is lit unless it is
 *              exactly a preset's look.
 *   scroll     the settings column's scroll, held at el.layout.maxScroll (below 0: the rubber band)
 *   open       'content' | 'size' | 'colours' | 'behaviour' | 'position' | null, an array of them, or
 *              { size: 0.4 } for a section part-way open (0..1), to animate it
 *   dirty      true (or 0..1 part-way): Save lit and full size; false: grey at 0.85
 *   saved      0..1: the "Appearance saved" toast
 *   demo       the "How it works" pass on the stage: { playing, panel 0..1, level 0..100, readout '47%',
 *              brightness 0..1, brightnessLevel, caption 'Tap · Open volume UI', captionIcon
 *              'drawable/ic_vol_increase', captionK 0..1 }
 *   dragging   the slider name under the finger (its thumb grows), e.g. 'width'
 *   perCorner  "Set corners individually" (default: on when the corners differ); 0..1 animates it
 *   snapNote   true: the primary note under Snap to edge that says it was switched off for you
 *
 * data-target: back, help, save, preview, preview-bar, preset-classic, preset-dock, preset-edge,
 *   preset-bold, section-content, section-size, section-colours, section-behaviour, section-position;
 *   Content: row-/switch-show-icon, icon-picker, row-/switch-percent; Size & shape: slider-width,
 *   slider-height, shape-rounded, shape-tab, slider-sweep (tab), slider-corners, row-/switch-per-corner,
 *   slider-corner-tl/-tr/-bl/-br (rounded), slider-outline-width, slider-icon-size (icon on); Colours:
 *   color-fill, slider-fill-opacity, color-stroke, slider-stroke-opacity, color-icon (icon on);
 *   Behaviour: row-/switch-vibrate; Position: placement, side-left, side-right, row-/switch-dynamic,
 *   slider-along-edge (dynamic) or row-/switch-same-position, slider-from-left, slider-from-top,
 *   slider-landscape-from-left, slider-landscape-from-top (same off), row-/switch-snap; then
 *   slider-edge-distance, reset-position. Every slider-<name> is its 28 dp track and has
 *   slider-<name>-thumb, minus-<name> and plus-<name>.
 * el.layout: see screenLayout.
 */
export function appearanceScreenFull({ status = 32, scroll = 0, preset = 'dock', look = {}, open = null, dirty = false, saved = 0, demo = null,
  dragging = null, perCorner = null, snapNote = false } = {}) {
  const L = resolveLook(preset, look);
  const D = { playing: false, panel: 0, level: 40, readout: null, brightness: 0, brightnessLevel: 40, caption: null, captionIcon: null, captionK: 1, ...(demo || {}) };
  const opens = openMap(open);
  const kd = kOf(dirty);
  const actions = `<div${tgt('help')} style="width:48px;height:48px;display:grid;place-items:center;flex:none">${icon('outlined/help_outline', 24, D.playing ? C.primary : C.onSurfaceVariant)}</div>
      <div${tgt('save')} style="position:relative;width:48px;height:48px;flex:none;transform:scale(${lerp(0.85, 1, kd)})">
        <div style="position:absolute;inset:0;display:grid;place-items:center;opacity:${1 - kd}">${icon('filled/save', 24, 'rgba(31,41,55,.3)')}</div>
        <div style="position:absolute;inset:0;display:grid;place-items:center;opacity:${kd}">${icon('filled/save', 24, C.primary)}</div></div>`;

  const tab = L.shape === 'tab';
  const vib = on(L.vibrate);
  const positionSummary = on(L.dynamic) ? 'Dynamic position'
    : on(L.same) || !on(L.snap) ? `${pctRound(L.x)}% · ${pctRound(L.y)}%` : L.side === 'left' ? 'Left' : 'Right';
  const sections = [
    section('content', 'filled/widgets', 'Content', on(L.showIcon) ? 'Show icon' : 'None', opens.content ?? 0, contentBody(L)),
    section('size', 'filled/aspect_ratio', 'Size &amp; shape', `${trunc(L.w)} × ${trunc(L.h)}dp · ${tab ? 'Tab' : 'Rounded'}`, opens.size ?? 0, sizeBody(L, { dragging, perCorner })),
    section('colours', 'filled/palette', 'Colours', `${pct255(L.alpha)}% · ${pct255(L.strokeAlpha)}%`, opens.colours ?? 0, coloursBody(L, { dragging })),
    section('behaviour', 'filled/tune', 'Behaviour', vib ? 'Vibrate on click' : '', opens.behaviour ?? 0, switchRow('vibrate', 'Vibrate on click', L.vibrate)),
    section('position', 'filled/open_with', 'Position', positionSummary, opens.position ?? 0, positionBody(L, { dragging, snapNote })),
  ];
  const chips = PRESET_IDS.map((id) => presetChip(id, matchesPreset(id, L))).join('<div style="width:8px;flex:none"></div>');
  const el = M.screen(`<div style="position:absolute;inset:0;display:flex;flex-direction:column">
      ${topBar('Appearance', { status, actions })}
      <div class="pinned" style="padding:4px 16px 12px;flex:none">${stageHtml(L, D)}
        ${bodyMedium('Your handler as it looks on your screen. Tap ? above to watch each gesture play out with your own settings. Long press the live bar to move it.', C.onSurfaceVariant, 'padding:10px 4px 0')}</div>
      <div class="scroll" style="position:relative;overflow:hidden;flex:1;min-height:0">
        <div class="scroll-content" style="padding:16px 16px ${16 + NAV}px">
          ${M.groupLabel('Quick presets')}<div style="display:flex;overflow:hidden">${chips}</div><div style="height:16px"></div>
          ${sections.join('<div style="height:12px"></div>')}<div style="height:56px"></div></div></div></div>
      ${saved > 0 ? toast('Appearance saved', saved) : ''}`);
  settle(el);
  // Held at the end of the column as a ScrollState is (below 0 is the rubber band at the top).
  let layout = screenLayout(el);
  const s = Math.min(scroll, layout.maxScroll);
  if (s !== 0) {
    el.querySelector('.scroll-content').style.transform = `translateY(${-s}px)`;
    layout = screenLayout(el);
  }
  el.layout = layout;
  return el;
}

// --- Dialogs ------------------------------------------------------------------------------------------
// A dialog window over the screen: the platform dim (black 60%) across the whole display, the card
// centred between the status bar and the navigation bar. k is the window animation
// (popup_enter_material: fade in while rising 20 dp); play it backwards to dismiss.
function dialogLayer(card, { k = 1, status = 32, width = DIALOG_W, radius = 24, cls = '' } = {}) {
  const kk = clamp(k);
  return h(`<div class="dialog-layer ${cls}" style="position:absolute;inset:0;z-index:30">
      <div style="position:absolute;inset:0;background:rgba(0,0,0,${(DIM * kk).toFixed(3)})"></div>
      <div style="position:absolute;left:0;right:0;top:${status}px;bottom:${NAV}px;display:flex;align-items:center;justify-content:center">
        <div class="dialog" style="width:${width}px;border-radius:${radius}px;background:${C.surface};overflow:hidden;opacity:${kk};transform:translateY(${(20 * (1 - kk)).toFixed(2)}px)">${card}</div>
      </div></div>`);
}
// M3 Buttons with the app's 12 dp corners (AlertDialog's confirm / dismiss), in their 48 dp boxes.
const filledButton = (label, target, radius = 12) => touchBox(`<div${tgt(target)} style="height:40px;border-radius:${radius}px;padding:0 24px;display:flex;align-items:center;
    background:${C.primary};${labelLarge};color:${C.onPrimary};white-space:nowrap">${label}</div>`);
const outlinedButton = (label, target, radius = 12) => touchBox(`<div${tgt(target)} style="height:40px;border-radius:${radius}px;padding:0 24px;display:flex;align-items:center;
    box-shadow:inset 0 0 0 1px ${C.outlineVariant};${labelLarge};color:${C.onSurfaceVariant};white-space:nowrap">${label}</div>`);
// AlertDialog: padding 24; the title (titleLarge, primary), 16, the text, 24, the buttons at the end, 8 apart.
const alertCard = (title, text, buttons) => `<div style="padding:24px">
    <div style="font:400 22px/28px Roboto;color:${C.primary}">${title}</div><div style="height:16px"></div>
    ${text}<div style="height:24px"></div>
    <div style="display:flex;justify-content:flex-end;gap:8px">${buttons}</div></div>`;

/*
 * Back with unsaved changes: "Unsaved Changes", the question, [Discard] [Apply].
 * data-target: dialog-discard, dialog-apply.
 */
export function unsavedChangesDialog({ k = 1, status = 32 } = {}) {
  return dialogLayer(alertCard('Unsaved Changes',
    bodyMedium('You have unsaved changes. Do you want to apply them before leaving?'),
    `${outlinedButton('Discard', 'dialog-discard')}${filledButton('Apply', 'dialog-apply')}`), { k, status, cls: 'unsaved-dialog' });
}

// The handler's icons, in IconPickerDialog's order, with their labels.
export const ICON_OPTIONS = [
  ['ic_vol_increase', 'Increase'], ['ic_vol_decrease', 'Decrease'], ['ic_vol_plus', 'Boost'], ['ic_vol_minus', 'Reduce'], ['ic_bug', 'Bug'],
  ['ic_check', 'Check'], ['ic_color_palette', 'Palette'], ['ic_crown_2', 'Crown'], ['ic_edit', 'Edit'], ['ic_feedback', 'Feedback'],
  ['ic_github', 'GitHub'], ['ic_lock', 'Lock'], ['ic_move', 'Move'], ['ic_music_ui', 'Music'], ['ic_nothing', 'None'], ['ic_plugin', 'Plugin'],
  ['ic_power', 'Power'], ['ic_share', 'Share'], ['ic_star', 'Star'], ['ic_visibility_hide', 'Hide'], ['ic_x_close', 'Close'],
].map(([d, name]) => ({ d, name, target: `icon-${name.toLowerCase()}` }));

/*
 * "Select Icon": 21 tiles three to a row (gap 8, 8 under each row) in a 400 dp column that scrolls,
 * and [Close]. A tile is square, radius 12, primaryContainer when chosen, else surfaceVariant: the
 * icon 32 dp, 4, its name at 9 sp on a 20 dp line.
 *   selected  'ic_vol_increase' or 'Increase'      scroll  0..253, the column's own scroll
 * data-target: icon-increase, icon-decrease, icon-boost, icon-reduce, icon-bug, icon-check,
 *   icon-palette, icon-crown, icon-edit, icon-feedback, icon-github, icon-lock, icon-move, icon-music,
 *   icon-none, icon-plugin, icon-power, icon-share, icon-star, icon-hide, icon-close; dialog-close.
 */
export function iconPickerDialog({ selected = 'ic_vol_increase', k = 1, scroll = 0, status = 32 } = {}) {
  const sel = String(selected).replace(/^drawable\//, '').toLowerCase();
  const tile = (o) => {
    const chosen = o.d === sel || o.name.toLowerCase() === sel;
    const ink = chosen ? C.onPrimaryContainer : C.onSurfaceVariant;
    return `<div${tgt(o.target)} style="flex:1;min-width:0;aspect-ratio:1;border-radius:12px;background:${chosen ? C.primaryContainer : C.surfaceVariant};
        display:flex;flex-direction:column;align-items:center;justify-content:center;padding:8px">
        ${drawableIcon(o.d, 32, ink)}<div style="height:4px"></div>
        <div style="font:${chosen ? 600 : 400} 9px/20px Roboto;letter-spacing:.25px;color:${ink};white-space:nowrap;overflow:hidden;max-width:100%">${o.name}</div></div>`;
  };
  const rows = [];
  for (let i = 0; i < ICON_OPTIONS.length; i += 3) {
    const row = ICON_OPTIONS.slice(i, i + 3).map(tile);
    while (row.length < 3) row.push('<div style="flex:1"></div>');
    rows.push(`<div style="display:flex;gap:8px">${row.join('')}</div><div style="height:8px"></div>`);
  }
  const card = `<div style="padding:24px">
      <div style="font:400 22px/28px Roboto;color:${C.primary}">Select Icon</div><div style="height:16px"></div>
      <div style="padding-top:8px"><div class="icon-scroll" style="height:400px;overflow:hidden"><div style="transform:translateY(${-scroll}px)">${rows.join('')}</div></div></div>
      <div style="height:24px"></div><div style="display:flex;justify-content:flex-end">${filledButton('Close', 'dialog-close')}</div></div>`;
  return dialogLayer(card, { k, status, cls: 'icon-dialog' });
}

// The colour dialog's 19 template colours, in ColorPickerDialog's order (Compose Color.copy(alpha).toArgb()).
export const TEMPLATE_COLORS = [
  ['red-10', '#1AFF0000'], ['red-30', '#4DFF0000'], ['red-50', '#80FF0000'], ['red', '#FFFF0000'], ['blue-30', '#4D0000FF'], ['blue-50', '#800000FF'],
  ['blue', '#FF0000FF'], ['green-30', '#4D00FF00'], ['green-50', '#8000FF00'], ['green', '#FF00FF00'], ['yellow', '#FFFFFF00'], ['cyan', '#FF00FFFF'],
  ['magenta', '#FFFF00FF'], ['white-30', '#4DFFFFFF'], ['white-50', '#80FFFFFF'], ['white', '#FFFFFFFF'], ['black-30', '#4D000000'], ['black-50', '#80000000'],
  ['black', '#FF000000'],
].map(([name, argb]) => ({ name, argb, target: `swatch-${name}` }));
const CHECKER = 'M0 0H3V3H0V0ZM6 3H3V6H0V9H3V12H0V15H3V12H6V15H9V12H12V15H15V12H12V9H15V6H12V3H15V0H12V3H9V0H6V3ZM6 6V3H9V6H6ZM6 9H3V6H6V9ZM9 9V6H12V9H9ZM9 9H6V12H9V9Z';

/*
 * The colour dialog (sheets-compose-dialogs 1.3.0 ColorDialog, template mode), as the three colour
 * rows open it: the header in 16 Bold, the "Custom color" text button, the 19 template colours (cells
 * of 54.4 dp, five across, in a 200 dp grid that scrolls and fades at both ends; a translucent one
 * over the transparency checks with its percentage in the corner), then [Cancel] [OK]. Nothing is
 * chosen when it opens, and OK waits for a choice. Surface radius 16 (shapes.large).
 *   title  the header (the app passes the same one for all three rows)
 *   hex    the colour tapped so far: '#AARRGGBB' / '#RRGGBB' of a template colour, or its name ('red-50')
 *   scroll the grid's own scroll (0..17.6)
 * data-target: swatch-red-10, swatch-red-30, swatch-red-50, swatch-red, swatch-blue-30, swatch-blue-50,
 *   swatch-blue, swatch-green-30, swatch-green-50, swatch-green, swatch-yellow, swatch-cyan,
 *   swatch-magenta, swatch-white-30, swatch-white-50, swatch-white, swatch-black-30, swatch-black-50,
 *   swatch-black; color-custom, color-cancel, color-ok.
 */
export function colorPickerDialog({ title = 'Select the color and transparency of the handler', hex = null, k = 1, scroll = 0, status = 32 } = {}) {
  let chosen = null;
  if (hex != null) {
    const v = String(hex).toLowerCase();
    const full = v.startsWith('#') ? (v.length === 7 ? '#ff' + v.slice(1) : v) : null;
    chosen = TEMPLATE_COLORS.find((c) => c.name === v || c.argb.toLowerCase() === full)?.name ?? null;
  }
  const cell = 54.4;
  const swatch = (c) => {
    const [a, rgb] = splitHex(c.argb);
    const sel = c.name === chosen;
    const label = !sel && a < 255 ? `<div style="position:absolute;left:0;top:0;padding:2px;border-radius:999px;background:${C.background};
        font:500 11px/16px Roboto;letter-spacing:.5px;color:${C.onSurface}">${Math.trunc((a / 255) * 100)}%</div>` : '';
    const check = sel ? `<div style="position:absolute;left:50%;top:50%;width:24px;height:24px;margin:-12px 0 0 -12px;border-radius:50%;background:${C.background};
        display:grid;place-items:center">${icon('round/check', 16, C.primary)}</div>` : '';
    return `<div style="width:${cell}px;height:${cell}px;padding:4px;flex:none">
        <div${tgt(c.target)} style="position:relative;width:100%;height:100%">
          <div style="position:absolute;inset:0;border-radius:12px;overflow:hidden;background:${C.surfaceContainerLow};box-shadow:0 1px 2px rgba(0,0,0,.3),0 1px 3px 1px rgba(0,0,0,.15)">
            <svg viewBox="0 0 15 15" preserveAspectRatio="xMidYMid meet" style="position:absolute;inset:0;width:100%;height:100%"><path d="${CHECKER}" fill="#000" fill-opacity=".25" fill-rule="evenodd"/></svg>
            <div style="position:absolute;inset:0;background:${rgba(rgb, a / 255)}"></div></div>
          ${check}${label}</div></div>`;
  };
  const textButton = (label, target, enabled = true, extra = '') => touchBox(`<div${tgt(target)} style="height:40px;padding:0 12px;border-radius:20px;display:flex;align-items:center;
      ${labelLarge};color:${enabled ? C.primary : 'rgba(31,41,55,.38)'};white-space:nowrap">${label}</div>`, extra);
  const card = `<div style="padding:20px 24px 0">${inhWrap(title, 16, 700, C.onSurface)}</div>
      <div style="padding:16px 24px 0">
        <div style="display:flex;padding-bottom:8px">${touchBox(`<div${tgt('color-custom')} style="height:40px;padding:0 8px;border-radius:20px;display:flex;align-items:center;
            ${labelLarge};color:${C.primary};white-space:nowrap">${icon('round/tune', 24, C.primary)}<span style="padding:0 8px">Custom color</span></div>`)}</div>
        <div style="height:200px;overflow:hidden;-webkit-mask-image:linear-gradient(transparent 0,#000 5%,#000 95%,transparent 100%);mask-image:linear-gradient(transparent 0,#000 5%,#000 95%,transparent 100%)">
          <div style="display:flex;flex-wrap:wrap;transform:translateY(${-scroll}px)">${TEMPLATE_COLORS.map(swatch).join('')}</div></div></div>
      <div style="display:flex;align-items:center;padding:24px">
        <div style="flex:1"></div>${textButton('Cancel', 'color-cancel', true, 'padding:0 16px')}${textButton('OK', 'color-ok', chosen != null)}</div>`;
  return dialogLayer(card, { k, status, radius: 16, cls: 'color-dialog' });
}

// --- The placement editor (HandlerPlacementEditor) -----------------------------------------------------
/*
 * The whole screen, for putting the bar where it starts: the walkthrough's wallpaper edge to edge, a
 * scrim at both ends, the hint card at the top, [Cancel] [Done] at the bottom, and the bar at its
 * true size and place — in its touch window, max(width, 28) dp wide, drawn at its own width against
 * the outer edge, its middle at y of the usable height (the display under the camera band, from
 * `status` down). While dragging: the bar dimmed with the move icon, a dashed guide through its
 * middle, the readout beside it, and the hint and buttons faded out. The status-bar icons are white
 * while it is up (Phone.setDarkIcons(false)).
 *   preset / look  the draft's look (resolveLook); dynamic, snap and edgeMargin decide the rules
 *   side, y        where it starts; x (0..1, the window's middle across the screen) defaults to the
 *                  side's edge. Not dragging, a snapping bar sits at the side nearer x.
 *   dragging       true or 0..1 (the chrome's fade, the guide's fade and the drag cue)
 *   k              the editor's own fade (in 220 ms, out 180 ms)
 * data-target: placement-surface (a long press anywhere picks the bar up), placement-bar,
 *   placement-cancel, placement-done.
 */
export function placementEditor({ status = 32, side = 'right', y = 0.21, x = undefined, dragging = false, look = {}, preset = 'dock', k = 1 } = {}) {
  const L = resolveLook(preset, { side, ...look });
  const d = kOf(dragging);
  const snaps = on(L.dynamic) || on(L.snap);
  const frameTop = status, usableW = SW, usableH = SH - frameTop;
  const thick = Math.max(L.w, 28), len = L.h, margin = L.edgeMargin;
  const fx = x ?? (L.side === 'left' ? 0 : 1);
  const maxX = Math.max(0, usableW - thick);
  let wx = clamp(Math.round(fx * usableW - thick / 2), 0, maxX);
  const clampX = (v) => (margin * 2 > maxX ? maxX / 2 : clamp(v, margin, maxX - margin));
  const isLeft = (v) => v + thick / 2 < usableW / 2;
  wx = snaps && d === 0 ? (isLeft(wx) ? clamp(margin, 0, maxX) : clamp(usableW - thick - margin, 0, maxX)) : clampX(wx);
  const wy = frameTop + clamp(Math.round(y * usableH - len / 2), 0, Math.max(0, usableH - len));
  const onLeft = isLeft(wx);
  const barX = onLeft ? wx : wx + thick - L.w;
  const cy = wy + len / 2;
  const fromTop = pctRound((wy - frameTop + len / 2) / usableH), fromLeft = pctRound((wx + thick / 2) / usableW);
  const readout = snaps ? `From top ${fromTop}%` : `From left ${fromLeft}% · From top ${fromTop}%`;
  const note = on(L.dynamic) ? 'Dynamic position is on, so it always rests against an edge.' : on(L.snap) ? 'Snap to edge is on, so it settles against the nearer side.' : '';
  const chrome = 1 - d;
  const pill = (label, target, filled) => touchBox(`<div${tgt(target)} style="height:40px;border-radius:20px;padding:0 24px;display:flex;align-items:center;${labelLarge};white-space:nowrap;
      ${filled ? `background:${C.primary};color:${C.onPrimary}` : 'background:rgba(0,0,0,.3);color:#fff;box-shadow:inset 0 0 0 1px rgba(255,255,255,.6)'}">${label}</div>`);
  const rw = textWidth(readout, '500 14px Roboto') + readout.length * 0.1 + 24;
  const rx = clamp(onLeft ? wx + thick + 12 : wx - 12 - rw, 0, SW - rw), ry = clamp(cy - 18, 0, SH - 36);
  const R = Math.max(SW, SH) * 0.51;
  return h(`<div class="placement-editor" style="position:absolute;inset:0;overflow:hidden;opacity:${clamp(k)};font-family:Roboto;
      background:radial-gradient(circle ${R}px at ${SW * 0.2}px ${SH * 0.17}px,rgba(255,255,255,.4),rgba(255,255,255,0)),linear-gradient(${90 + Math.atan2(SH, SW) * 180 / Math.PI}deg,#D9CCFF,#9DB2FA)">
      <div${tgt('placement-surface')} style="position:absolute;inset:0;background:linear-gradient(rgba(0,0,0,.34) 0%,rgba(0,0,0,0) 20%,rgba(0,0,0,0) 72%,rgba(0,0,0,.38) 100%)"></div>
      ${d > 0 ? `<div style="position:absolute;left:0;right:0;top:${cy - 0.5}px;height:1px;opacity:${0.7 * d};background:repeating-linear-gradient(90deg,#fff 0 6px,transparent 6px 12px)"></div>` : ''}
      <div style="position:absolute;left:56px;right:56px;top:${status + 20}px;display:flex;justify-content:center;opacity:${chrome}">
        <div style="max-width:360px;border-radius:20px;background:rgba(0,0,0,.5);padding:14px 18px;display:flex;flex-direction:column;gap:6px">
          <div style="display:flex;align-items:center;gap:8px">${icon('filled/open_with', 18, '#fff')}<div style="font:600 14px/20px Roboto;letter-spacing:.1px;color:#fff">Set initial position</div></div>
          ${bodyMedium('Hold anywhere until the bar lifts, then drag to move it where it should start.', 'rgba(255,255,255,.9)')}
          ${note ? bodySmall(note, 'rgba(255,255,255,.7)') : ''}</div></div>
      <div style="position:absolute;left:0;right:0;bottom:${NAV + 24}px;display:flex;justify-content:center;gap:12px;opacity:${chrome}">
        ${pill('Cancel', 'placement-cancel', false)}${pill('Done', 'placement-done', true)}</div>
      <div${tgt('placement-bar')} style="position:absolute;left:${wx}px;top:${wy}px;width:${thick}px;height:${len}px">
        <div style="position:absolute;left:${barX - wx}px;top:0">${barHtml(L, { edgeOnLeft: onLeft, cue: d })}</div></div>
      ${d > 0 ? `<div style="position:absolute;left:${rx}px;top:${ry}px;padding:8px 12px;border-radius:12px;background:rgba(0,0,0,.62);${labelLarge};color:#fff;white-space:nowrap">${readout}</div>` : ''}
    </div>`);
}

// --- Visibility -----------------------------------------------------------------------------------------
// The launchable apps (this one left out), alphabetical by label, with made-up packages.
export const VIS_APP_LIST = [
  ['browser', 'app.wideweb.browser'], ['calendar', 'com.android.calendar'], ['camera', 'com.android.camera'], ['clock', 'com.android.deskclock'],
  ['fitness', 'app.stride.fitness'], ['games', 'app.playland.games'], ['mail', 'app.postbox.mail'], ['maps', 'app.wayfinder.maps'],
  ['messages', 'com.android.messaging'], ['music', 'app.tunes.music'], ['notes', 'app.jotter.notes'], ['phone', 'com.android.dialer'],
  ['photos', 'com.android.gallery3d'], ['podcasts', 'app.castaway.podcasts'], ['reader', 'app.pageturn.reader'], ['settings', 'com.android.settings'],
  ['video', 'app.streambox.video'], ['weather', 'app.skyline.weather'],
].map(([key, pkg]) => ({ key, pkg, label: APPS[key].label }))
  .sort((a, b) => a.label.toLowerCase().localeCompare(b.label.toLowerCase()));

export const KEYBOARD_MODES = ['move', 'hide', 'stay'];
export const KEYBOARD_MOTIONS = ['glide', 'spring', 'bounce', 'quick', 'fade', 'instant'];
const MOTION_LABEL = { glide: 'Glide', spring: 'Spring', bounce: 'Bounce', quick: 'Quick', fade: 'Fade', instant: 'Instant' };
const MOTION_MS = { glide: 180, spring: 460, bounce: 700, quick: 110, fade: 300, instant: 0 };

// BarBehaviour.motionAt / motionAlphaAt.
function motionAt(m, t) {
  const x = clamp(t);
  if (m === 'spring') { const u = x - 1; return u * u * ((1.6 + 1) * u + 1.6) + 1; }
  if (m === 'bounce') {
    const b = (v) => v * v * 8, v = x * 1.1226;
    return v < 0.3535 ? b(v) : v < 0.7408 ? b(v - 0.54719) + 0.7 : v < 0.9644 ? b(v - 0.8526) + 0.9 : b(v - 1.0435) + 0.95;
  }
  if (m === 'fade') return x < 0.5 ? 0 : 1;
  if (m === 'instant') return 1;
  return 1 - (1 - x) * (1 - x);
}
const motionAlphaAt = (m, t) => (m === 'fade' ? Math.abs(1 - 2 * clamp(t)) : 1);

// A tile's picture (KeyboardMotionSelector.MotionPicture) at ms into the shared 3600 ms loop: two lines
// of an app, the keyboard rising at 300 ms and leaving at 1900 ms (260 ms, ease 1 − (1 − x)²), and the
// bar against the right edge getting out of its way as `m` moves it, 1.6 × slower than live.
function motionPicture(m, ms) {
  const w = 66, hh = 78;
  const ease = (x) => 1 - (1 - x) * (1 - x);
  const shown = ease(clamp((ms - 300) / 260)) - ease(clamp((ms - 1900) / 260));
  const keysTop = hh - 30 * shown;
  const r = (x, y, ww, hgt, rad, c) => `<div style="position:absolute;left:${x.toFixed(2)}px;top:${y.toFixed(2)}px;width:${ww.toFixed(2)}px;height:${hgt}px;border-radius:${rad}px;background:${c}"></div>`;
  let s = r(8, 10, w * 0.55, 4, 2, 'rgba(255,255,255,.2)') + r(8, 17, w * 0.38, 4, 2, 'rgba(255,255,255,.2)');
  if (keysTop < hh) {
    s += r(0, keysTop, w, 36, 6, '#DCD9E6');
    const gap = 2.5, kw = (w - gap * 7) / 6;
    for (let row = 0; row < 3; row++) {
      const yy = keysTop + 4 + row * 7.5;
      if (row === 2) s += r(gap, yy, kw, 5, 1.5, '#F8F7FB') + r(gap * 2 + kw, yy, w - (gap * 2 + kw) * 2, 5, 1.5, '#F8F7FB') + r(w - gap - kw, yy, kw, 5, 1.5, '#F8F7FB');
      else for (let i = 0; i < 6; i++) s += r(gap + i * (kw + gap), yy, kw, 5, 1.5, '#F8F7FB');
    }
  }
  const dur = MOTION_MS[m] * 1.6;
  const rising = ms < 1900, start = rising ? 300 : 1900;
  let along = 0, alpha = 1;
  if (ms >= start) {
    const t = dur <= 0 ? 1 : clamp((ms - start) / dur);
    const a = motionAt(m, t);
    along = rising ? a : 1 - a;
    alpha = motionAlphaAt(m, t);
  }
  const low = hh - 34, high = hh - 30 - 4 - 20;
  s += `<div style="position:absolute;left:${w - 4.5}px;top:${(low + (high - low) * along).toFixed(2)}px;width:9px;height:20px;border-radius:2.25px;background:rgba(23,23,28,${alpha.toFixed(3)})"></div>`;
  return `<div style="position:absolute;left:5px;top:5px;width:${w}px;height:${hh}px;border-radius:14px;overflow:hidden;background:linear-gradient(139.76deg,#D9CCFF,#9DB2FA)">${s}</div>`;
}

// PictureTile: a 76 × 88 picture (radius 18, surfaceContainerHigh, 1 dp outlineVariant @ 60% or 2 dp
// primary when chosen, with the tick badge), its name under it (labelMedium, 6 + 34 dp).
function pictureTile(target, label, picture, selected) {
  return `<div${tgt(target)} style="width:76px;flex:none">
      <div style="position:relative;width:76px;height:88px;border-radius:18px;overflow:hidden;background:${C.surfaceContainerHigh}">
        ${picture}<div style="position:absolute;inset:0;border-radius:18px;box-shadow:inset 0 0 0 ${selected ? 2 : 1}px ${selected ? C.primary : 'rgba(229,231,235,.6)'}"></div>
        ${selected ? `<div style="position:absolute;right:6px;top:6px;width:18px;height:18px;border-radius:50%;background:${C.primary};display:grid;place-items:center">${icon('filled/check', 12, C.onPrimary)}</div>` : ''}</div>
      <div style="height:34px;margin-top:6px;text-align:center;font:${selected ? 600 : 500} 12px/16px Roboto;letter-spacing:.5px;color:${selected ? C.primary : C.onSurfaceVariant}">${label}</div></div>`;
}

const visHeading = (title, hint, id) => `<div class="vis-heading" data-heading="${id}" style="padding:0 6px 12px">${inh(title, 14, 600, C.primary)}${bodySmall(hint, C.onSurfaceVariant, 'padding-top:4px')}</div>`;
const iconTile = (glyph, lit) => `<div style="width:46px;height:46px;border-radius:14px;flex:none;display:grid;place-items:center;background:${lit ? C.primary : 'rgba(224,231,255,.6)'}">${icon(glyph, 24, lit ? C.onPrimary : C.primary)}</div>`;
const tick = (sel) => icon(sel ? 'filled/check_circle' : 'filled/radio_button_unchecked', 24, sel ? C.primary : C.outline);

// ToggleRow: tile, 14, the title (16 SemiBold) over its description (bodySmall), 10, the switch.
function toggleRow(name, glyph, title, desc, v, radius) {
  return `<div${tgt(`row-${name}`)} style="display:flex;align-items:center;padding:12px 14px;border-radius:${radius};background:${C.row}">
      ${iconTile(glyph, on(v))}<div style="width:14px;flex:none"></div>
      <div style="flex:1;min-width:0">${inhWrap(title, 16, 600)}${bodySmall(desc, C.onSurfaceVariant, 'padding-top:2px')}</div>
      <div style="width:10px;flex:none"></div><div${tgt(`switch-${name}`)} style="flex:none">${M.m3switchAt(kOf(v))}</div></div>`;
}
// ChoiceRow: the same, tinted primaryContainer when chosen, its words @ 72%, a tick at the end.
function choiceRow(target, glyph, title, desc, selected, radius) {
  const ink = selected ? C.onPrimaryContainer : C.onSurface;
  return `<div${tgt(target)} style="display:flex;align-items:center;padding:12px 14px;border-radius:${radius};background:${selected ? C.primaryContainer : C.row}">
      ${iconTile(glyph, selected)}<div style="width:14px;flex:none"></div>
      <div style="flex:1;min-width:0">${inhWrap(title, 16, selected ? 600 : 500, ink)}${bodySmall(desc, rgba(ink, 0.72), 'padding-top:2px')}</div>
      <div style="width:10px;flex:none"></div>${tick(selected)}</div>`;
}
// PermissionNote on the page: errorContainer @ 60%, radius 12, padding 12 × 10, ⚠ 18, 10, 13 sp on an
// 18 sp pitch, › 18. It sits 8 under what is above it, and 8 over what follows.
const permissionNote = (s, target) => `<div${tgt(target)} style="margin:8px 0;border-radius:12px;background:rgba(254,226,226,.6);display:flex;align-items:center;padding:10px 12px">
    ${icon('filled/warning', 18, C.onErrorContainer)}<div style="width:10px;flex:none"></div>
    ${inhWrap(s, 13, 400, C.onErrorContainer, { lh: 18, extra: 'flex:1;min-width:0' })}${icon('filled/keyboard_arrow_right', 18, C.onErrorContainer)}</div>`;

// The search field (OutlinedTextField, radius 16, 56 tall): the magnifier in a 48 dp slot, the text
// from x 52 in bodyLarge, the ✕ once there is text; focused, a 2 dp primary outline and the caret.
function searchField(query, focused) {
  const border = focused ? `inset 0 0 0 2px ${C.primary}` : `inset 0 0 0 1px ${C.outline}`;
  const caret = focused ? `<span style="display:inline-block;width:2px;height:20px;margin-left:1px;vertical-align:-4px;background:${C.primary}"></span>` : '';
  const text = query
    ? `<span style="font:400 16px/24px Roboto;letter-spacing:.5px;color:${C.onSurface};white-space:pre">${query}</span>${caret}`
    : `${caret}<span style="font:400 16px/24px Roboto;letter-spacing:.5px;color:${C.onSurfaceVariant}">Search apps…</span>`;
  return `<div${tgt('search')} style="position:relative;height:56px;border-radius:16px;box-shadow:${border};display:flex;align-items:center">
      <div style="width:48px;height:48px;margin-left:0;display:grid;place-items:center;flex:none">${icon('filled/search', 24, C.onSurfaceVariant)}</div>
      <div style="flex:1;min-width:0;padding-left:4px;white-space:nowrap;overflow:hidden">${text}</div>
      ${query ? `<div${tgt('search-clear')} style="width:48px;height:48px;display:grid;place-items:center;flex:none">${icon('filled/close', 24, C.onSurfaceVariant)}</div>` : '<div style="width:16px;flex:none"></div>'}</div>`;
}

/*
 * The Visibility screen, top to bottom: "When it shows" (Only while media plays, Only during calls),
 * "While typing" (Move above the keyboard — with the Moving animation tiles under it while chosen —
 * Hide it, Leave it where it is), "On the lock screen" (Show on the lock screen), then "Hide in these
 * apps", the search and the "Hidden in (N) | All apps" switch (pinned to the top of the list once it
 * scrolls there), and the apps as one segmented card.
 *
 *   media, calls, lock   0 off .. 1 on (part-way animates the switch)
 *   keyboard             'move' | 'hide' | 'stay';   motion  'glide' | 'spring' | 'bounce' | 'quick' | 'fade' | 'instant'
 *   motionCard           null (shown with 'move') or 0..1, the card's expand/collapse when the choice changes
 *   motionScroll         the tiles' sideways scroll (0..158); 'auto' is where the row opens (the
 *                        chosen tile with one neighbour before it)
 *   tileMs               where the tiles' shared 3600 ms loop is (1500: keyboard up, bar lifted)
 *   tab                  'all' | 'hidden';  hidden  APPS keys the bar steps aside in;  query  the search text
 *   focused              the search field focused (2 dp outline, caret)
 *   accessibility        false: the two red notes (lock screen, apps) that ask for the service
 *   apps                 APPS keys to list instead of VIS_APP_LIST
 *   ime                  the keyboard's height (dp, nav bar included) while typing: the list ends above it
 * el.layout (see screenLayout) adds pinAt and headings { when, typing, lock, apps }: where each group's
 * heading sits in the column. The pinned search: el.layout.content gives where its targets sit in the
 * column; once scroll passes pinAt they stay at viewportTop + 4 on screen (read el.layout.targets of the
 * element built with that scroll). A scroll past maxScroll is held there; below 0 is the rubber band.
 * data-target: back, row-media/switch-media, row-calls/switch-calls, keyboard-move, keyboard-hide,
 *   keyboard-stay, motion-glide … motion-instant, row-lock/switch-lock, note-lock, note-apps (service
 *   off), search, search-clear (with a query), tab-hidden, tab-all, app-<key> per app row.
 */
export function visibilityScreenFull({ status = 32, scroll = 0, media = 0, calls = 0, keyboard = 'move', motion = 'glide', lock = 0, tab = 'all',
  hidden = ['games', 'reader', 'video'], query = '', focused = false, accessibility = true, motionCard = null, motionScroll = 0, tileMs = 1500, apps = null, ime = 0 } = {}) {
  if (!KEYBOARD_MODES.includes(keyboard)) throw new Error('unknown keyboard mode: ' + keyboard);
  if (!KEYBOARD_MOTIONS.includes(motion)) throw new Error('unknown keyboard motion: ' + motion);
  const moving = keyboard === 'move';
  const cardK = motionCard == null ? (moving ? 1 : 0) : clamp(motionCard);
  const count = 3 + (moving ? 1 : 0);
  const kbRows = [];
  let at = 0;
  const KB = {
    move: ['filled/keyboard_double_arrow_up', 'Move above the keyboard', 'Only when the keyboard would cover it, and only as far as it has to. It slides back when the keyboard closes.'],
    hide: ['filled/visibility_off', 'Hide it', 'Out of sight while you type, back when the keyboard closes.'],
    stay: ['filled/push_pin', 'Leave it where it is', 'As before: a handler low on the screen can sit over the keyboard’s edge keys.'],
  };
  for (const mode of KEYBOARD_MODES) {
    const [g, t, dsc] = KB[mode];
    kbRows.push(choiceRow(`keyboard-${mode}`, g, t, dsc, keyboard === mode, M.segRadius(at++, count)));
    if (mode === 'move' && cardK > 0) {
      const radius = M.segRadius(moving ? at : 0, count);
      if (moving) at++;
      const idx = KEYBOARD_MOTIONS.indexOf(motion);
      const maxScroll = Math.max(0, KEYBOARD_MOTIONS.length * 86 - 10 - 348);
      const ms = motionScroll === 'auto' ? clamp(Math.max(0, idx - 1) * 86, 0, maxScroll) : clamp(+motionScroll || 0, 0, maxScroll);
      const tiles = KEYBOARD_MOTIONS.map((m) => pictureTile(`motion-${m}`, MOTION_LABEL[m], motionPicture(m, ((tileMs % 3600) + 3600) % 3600), m === motion)).join('<div style="width:10px;flex:none"></div>');
      const card = `<div style="border-radius:${radius};background:${C.row};padding:16px">
          ${inh('Moving animation', 15, 400)}${bodySmall('How the bar moves out of the keyboard\'s way, and back again when it closes.', C.onSurfaceVariant, 'padding:4px 0 12px')}
          <div style="overflow:hidden"><div style="display:flex;transform:translateX(${-ms}px)">${tiles}</div></div></div>`;
      kbRows.push(cardK >= 1 ? card : expander(card, cardK));
    }
  }
  const column = (rows) => `<div style="display:flex;flex-direction:column;gap:3px">${rows.join('')}</div>`;
  const settings = `<div style="padding:8px 16px 24px">
      ${visHeading('When it shows', 'With either switch on, the handler is there only while that is happening. With both, while either is. With neither, it is always there.', 'when')}
      ${column([
        toggleRow('media', 'filled/music_note', 'Only while media plays', 'Steps aside when nothing is playing, and comes back as soon as something starts. It stays a few seconds after the sound stops, so it does not blink out between tracks.', media, M.segRadius(0, 2)),
        toggleRow('calls', 'filled/call', 'Only during calls', 'Shows while the phone rings and while a call is on, calls in apps included.', calls, M.segRadius(1, 2)),
      ])}
      <div style="height:24px"></div>${visHeading('While typing', 'What the handler does when the keyboard opens', 'typing')}${column(kbRows)}
      <div style="height:24px"></div>${visHeading('On the lock screen', 'Android keeps every app’s floating bar off the lock screen. The accessibility service can put this one there.', 'lock')}
      ${accessibility ? '' : permissionNote('Needs the accessibility service to work. Tap to turn it on from the Permissions screen.', 'note-lock')}
      ${column([toggleRow('lock', 'filled/screen_lock_portrait', 'Show on the lock screen', 'The volume, the Quick panel and quick toggles while the phone is locked. The Deck, the menu and opening apps wait until you unlock.', lock, M.segRadius(0, 1))])}</div>`;
  const appsHeader = `<div style="padding:0 16px">${visHeading('Hide in these apps', 'The handler steps aside while one of these apps is open, and comes back when you leave it.', 'apps')}
      ${accessibility ? '' : permissionNote('This needs the accessibility service, which tells the app which app is open. Tap to turn it on.', 'note-apps')}</div>`;
  const chosenSet = new Set(hidden);
  const sticky = `<div class="vis-sticky" style="position:relative;z-index:2;background:${C.background};padding:4px 16px 8px">${searchField(query, focused)}
      <div style="padding-top:8px">${halves([{ target: 'tab-hidden', label: `Hidden in (${chosenSet.size})`, selected: tab === 'hidden' }, { target: 'tab-all', label: 'All apps', selected: tab !== 'hidden' }], 12)}</div></div>`;
  const list = (apps ? apps.map((k) => VIS_APP_LIST.find((a) => a.key === k) ?? { key: k, pkg: `app.${k}`, label: APPS[k]?.label ?? k }) : VIS_APP_LIST);
  const q = query.trim().toLowerCase();
  const filtered = list.filter((a) => !q || a.label.toLowerCase().includes(q) || a.pkg.toLowerCase().includes(q));
  const shown = tab === 'hidden' ? filtered.filter((a) => chosenSet.has(a.key)) : filtered;
  const appRow = (a, i, n) => {
    const sel = chosenSet.has(a.key), ink = sel ? C.onPrimaryContainer : C.onSurface;
    return `<div style="padding:0 16px 3px"><div${tgt(`app-${a.key}`)} style="display:flex;align-items:center;padding:10px 14px;border-radius:${M.segRadius(i, n)};background:${sel ? C.primaryContainer : C.row}">
        <div style="width:40px;height:40px;border-radius:10px;overflow:hidden;flex:none">${appIcon(a.key, 40)}</div><div style="width:14px;flex:none"></div>
        <div style="flex:1;min-width:0">${inh(a.label, 15, sel ? 600 : 500, ink, one)}${inh(a.pkg, 11, 400, rgba(ink, 0.6), one)}</div>
        <div style="width:10px;flex:none"></div>${tick(sel)}</div></div>`;
  };
  const rows = tab === 'hidden' && chosenSet.size === 0
    ? bodyMedium('No apps chosen yet. Tick one in All apps and the handler steps aside while it is open.', C.onSurfaceVariant, 'padding:16px 24px')
    : shown.map((a, i) => appRow(a, i, shown.length)).join('');
  const el = M.screen(`<div style="position:absolute;left:0;right:0;top:0;bottom:${Math.max(NAV, ime)}px;display:flex;flex-direction:column">
      ${topBar('Visibility', { status })}
      <div class="scroll" style="position:relative;overflow:hidden;flex:1;min-height:0">
        <div class="scroll-content" style="position:relative;padding-bottom:24px">${settings}${appsHeader}${sticky}${rows}</div></div></div>`);
  // Lay it out once: where the pinned header sits in the column, and how far the column scrolls.
  settle(el);
  const content = el.querySelector('.scroll-content');
  const pin = el.querySelector('.vis-sticky');
  const { pinAt, maxScroll, headings } = withMounted(el, () => {
    const c = content.getBoundingClientRect();
    const headings = Object.fromEntries([...content.querySelectorAll('.vis-heading')].map((x) => [x.dataset.heading, +(x.getBoundingClientRect().top - c.top).toFixed(1)]));
    return { pinAt: +(pin.getBoundingClientRect().top - c.top).toFixed(2), maxScroll: Math.max(0, c.height - el.querySelector('.scroll').getBoundingClientRect().height), headings };
  });
  const s = Math.min(scroll, maxScroll);      // a pull past the top (scroll < 0) is the rubber band
  content.style.transform = `translateY(${-s}px)`;
  if (s > pinAt) {
    pin.style.transform = `translateY(${s - pinAt}px)`;
    pin.dataset.pinned = String(s - pinAt);
  }
  el.layout = { ...screenLayout(el), pinAt, headings };
  return el;
}

// --- Icons --------------------------------------------------------------------------------------------
export const APPEARANCE2_ICONS = [...new Set([
  'filled/arrow_back', 'outlined/help_outline', 'filled/save', 'filled/widgets', 'filled/aspect_ratio', 'filled/palette', 'filled/tune',
  'filled/open_with', 'filled/expand_more', 'filled/remove', 'filled/add', 'filled/edit', 'filled/check', 'filled/volume_up', 'round/tune',
  'round/check', 'filled/search', 'filled/close', 'filled/check_circle', 'filled/radio_button_unchecked', 'filled/music_note', 'filled/call',
  'filled/keyboard_double_arrow_up', 'filled/visibility_off', 'filled/push_pin', 'filled/screen_lock_portrait', 'filled/warning',
  'filled/keyboard_arrow_right', ...APP_ICONS, ...ICON_OPTIONS.map((o) => 'drawable/' + o.d), 'drawable/ic_brightness_up',
])];
