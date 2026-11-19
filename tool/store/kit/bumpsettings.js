// The Button animation screen (ui/screens/button_feedback/ButtonFeedbackScreen.kt), whole, upright:
// the top bar with its ▶ (the screen's video tutorial), the preview stage pinned under it
// (PreviewSettingsLayout: only the settings under the stage scroll), then the settings column —
// the feature's switch, the Quick slider, the Animation gallery (a two-row LazyHorizontalGrid of
// StyleTiles), Where your buttons are, Power button, Look, Sound and Feel.
//
// The stage's glass is left empty: `.bump-glass` lies over it (inside the glass, so it is clipped to
// the glass's rounded top as the app clips the ButtonBumpView), for the caller to draw the real
// animation's frames in. bumpStageGlass() says where it is. The gallery's tiles are the app's own
// renders of each style (build/bump/tiles/<style>.png, an 80 x 92 dp stage at 2.625x on a clear
// background), laid on the wallpaper gradient the app paints under them.
//
// Every function takes a state object and returns an element at 1 dp = 1 px. Fonts must be loaded
// (fontsReady) before a screen is built: tile labels are fitted to their tile by measuring them, and
// the screen is laid out once off-screen to find how far its column scrolls. The element carries
// `el.layout` (see bumpSettingsScreen).
import { h, icon, clamp } from './core.js';
import * as M from './m3.js';

const C = M.LIGHT;
const SW = 412, SH = 915;
/** Gesture navigation: the settings column pads its end for it (navigationBarsPadding inside the scroll). */
const NAV = 24;
const COL_W = SW - 32;                            // the settings column and the stage: 380
const BODY_W = COL_W - 32;                        // inside a Card: 348
const STAGE_H = clamp(0.27 * SH, 212, 320);       // previewStageHeight() on a 915 dp phone
const PINNED_TOP = 4, PINNED_BOTTOM = 12;         // the stage's box: padding top 4, bottom 12, sides 16

// The gallery (LazyHorizontalGrid, rows = Fixed(2), 10 dp apart both ways, 2 dp content padding at
// the ends): each cell is (274 − 10) / 2 = 132 tall, and a StyleTile is 92 wide and fills it.
const GALLERY_H = 274, GRID_GAP = 10, GRID_PAD = 2, TILE_W = 92, TILE_PAD = 6;
const CELL_H = (GALLERY_H - GRID_GAP) / 2;
const TILE_STAGE_W = TILE_W - 2 * TILE_PAD, TILE_STAGE_H = 92;
const PITCH = TILE_W + GRID_GAP;
// Brush.linearGradient's default runs corner to corner: the CSS angle along the diagonal.
const WALL = (w, hh) => `linear-gradient(${(90 + (Math.atan2(hh, w) * 180) / Math.PI).toFixed(3)}deg,#D9CCFF,#9DB2FA)`;
const TILES_URL = new URL('../build/bump/tiles/', import.meta.url).href;

// --- What the screen offers ----------------------------------------------------------------------------
/** ButtonBump.STYLES, in the app's order, with the bump_style_* names. */
export const BUMP_STYLES = [
  { id: 'water', label: 'Water' }, { id: 'liquid', label: 'Liquid' }, { id: 'blossom', label: 'Cherry blossom' },
  { id: 'fireworks', label: 'Fireworks' }, { id: 'volcano', label: 'Volcano' }, { id: 'rocket', label: 'Rocket launch' },
  { id: 'fish', label: 'Fish jump' }, { id: 'frog', label: 'Frog jump' }, { id: 'swing', label: 'Swing women' },
  { id: 'cat', label: 'Cat' }, { id: 'monkey', label: 'Monkey' }, { id: 'bubbles', label: 'Bubbles' },
  { id: 'balloon', label: 'Balloons' }, { id: 'spray', label: 'Colour spray' }, { id: 'wind', label: 'Wind' },
  { id: 'ninja', label: 'Ninja blade' }, { id: 'crack', label: 'Cracked screen' }, { id: 'ufo', label: 'UFO' },
  { id: 'bees', label: 'Bees' }, { id: 'solar', label: 'Solar system' }, { id: 'capsule', label: 'Capsule' },
  { id: 'line', label: 'Line' }, { id: 'jelly', label: 'Jelly' }, { id: 'neon', label: 'Neon' },
  { id: 'aurora', label: 'Aurora' }, { id: 'ripple', label: 'Ripple' }, { id: 'sparkle', label: 'Sparkle' },
  { id: 'lava', label: 'Lava' }, { id: 'glass', label: 'Glass' }, { id: 'spectrum', label: 'Spectrum' },
  { id: 'heartbeat', label: 'Heartbeat' }, { id: 'elastic', label: 'Elastic' },
];
const STYLE_INDEX = Object.fromEntries(BUMP_STYLES.map((s, i) => [s.id, i]));
// ButtonBump.spec(style).shoulder: what End sweep shows until it is moved (sweep < 0).
const SHOULDER = {
  capsule: 0.5, line: 0.62, jelly: 0.42, neon: 0.36, aurora: 0.4, ripple: 0.36, sparkle: 0.36, lava: 0.46, glass: 0.36,
  spectrum: 0.36, heartbeat: 0.38, elastic: 0.4, water: 0.9, blossom: 0.5, fireworks: 0.4, solar: 0.5,
};
const shoulderOf = (id) => SHOULDER[id] ?? (id === 'liquid' ? 0.34 : 0.45);

// ButtonBumpStore: the swatches, the defaults and the ranges.
const BODY_COLORS = ['#000000', '#1C1C22', '#0B1A3A', '#2A0F3D', '#06302A', '#3A0A12', '#F2F2F5'];
const ACCENT_COLORS = ['#FFFFFF', '#FFD60A', '#30D158', '#64D2FF', '#BF5AF2', '#FF375F', '#FF9F0A'];
const DEFAULT_PLACES = {
  up: { left: false, position: 0.27, lengthDp: 52 },
  down: { left: false, position: 0.36, lengthDp: 52 },
  power: { left: false, position: 0.47, lengthDp: 48 },
};
const LENGTH = [24, 220], DEPTH = [2, 44], SWEEP = [5, 100], HOLD = [500, 4000], SPEED = [0.5, 2];
const BUTTONS = [['up', 'Volume up'], ['down', 'Volume down'], ['power', 'Power']];
const HAPTICS = [['off', 'Off'], ['light', 'Light'], ['strong', 'Strong']];
const WITH_BUMP = [['both', 'Show both'], ['pause', 'Pause']];
const WITH_BUMP_HINT = {
  both: 'A press plays the button animation, and the Quick slider comes up too, as Volume keys says.',
  pause: 'A press plays the button animation alone. The Quick slider stays out of the volume keys and still opens from the bar.',
};

/** Every icon the screen draws, for loadIcons. */
export const BUMP_ICONS = [
  'filled/arrow_back', 'outlined/play_circle', 'filled/animation', 'filled/check', 'filled/remove', 'filled/add',
  'filled/warning', 'filled/info', 'filled/keyboard_arrow_right', 'filled/phone_android',
];

/** Decodes every gallery tile ahead of time (optional: a scene that waits on its <img>s needs none of it). */
const kept = [];
export async function loadBumpTiles() {
  await Promise.all(BUMP_STYLES.map(async ({ id }) => {
    const img = new Image();
    img.src = `${TILES_URL}${id}.png`;
    kept.push(img);
    await img.decode();
  }));
}

// --- Text ------------------------------------------------------------------------------------------------
// Text that sets only a size inherits the app's bodyLarge (24 sp lines, 0.5 sp tracking) with
// LineHeightStyle.Default, which trims the first and last line to the font: one line is 1.172 × the
// size tall, wrapped lines `lh` apart. The M3 type scale (bodySmall, labelLarge) keeps its full lines.
const inh = (s, size, weight = 400, color = C.onSurface, extra = '') =>
  `<div style="font:${weight} ${size}px/1.172 Roboto;letter-spacing:.5px;color:${color};${extra}">${s}</div>`;
const inhWrap = (s, size, weight = 400, color = C.onSurface, { lh = 24, extra = '' } = {}) =>
  `<div style="display:flow-root;${extra}"><div style="font:${weight} ${size}px/${lh}px Roboto;letter-spacing:.5px;color:${color};margin:${(-(lh - 1.172 * size) / 2).toFixed(2)}px 0">${s}</div></div>`;
const bodySmall = (s, color = C.onSurfaceVariant, extra = '') =>
  `<div style="font:400 12px/16px Roboto;letter-spacing:.4px;color:${color};${extra}">${s}</div>`;
const LABEL_LARGE = 'font:500 14px/20px Roboto;letter-spacing:.1px';
const gap = (n) => `<div style="height:${n}px;flex:none"></div>`;
const tgt = (name) => (name ? ` data-target="${name}"` : '');
const kOf = (v) => (typeof v === 'number' ? clamp(v) : v ? 1 : 0);
const on = (v) => kOf(v) >= 0.5;

// TextAutoSize.StepBased(9..13 sp, 0.5 sp): the largest size the name fits the tile's 80 dp at, on one line.
let measureCtx = null;
const fitted = new Map();
function labelSize(label, weight) {
  const key = `${weight}|${label}`;
  if (fitted.has(key)) return fitted.get(key);
  measureCtx ??= document.createElement('canvas').getContext('2d');
  let size = 9;
  for (let s = 13; s >= 9; s -= 0.5) {
    measureCtx.font = `${weight} ${s}px Roboto`;
    if (measureCtx.measureText(label).width + 0.5 * [...label].length <= TILE_STAGE_W + 0.01) { size = s; break; }
  }
  if (document.fonts.check(`${weight} 13px Roboto`)) fitted.set(key, size);
  return size;
}

// --- Pieces (the screen's private composables, and the shared ones it uses) -------------------------------
// TopAppBar: back (48 at x 4), the title from x 56 in onPrimaryContainer, the ▶ (TutorialAction), 4 dp end.
function topBar(status) {
  return `<div style="height:${status + 64}px;padding-top:${status}px;display:flex;align-items:center;flex:none">
      <div style="width:4px;flex:none"></div>
      <div${tgt('back')} style="width:48px;height:48px;display:grid;place-items:center;flex:none">${icon('filled/arrow_back', 24, C.onSurface)}</div>
      <div style="flex:1;min-width:0;padding-left:4px;font:400 22px/28px Roboto;color:${C.onPrimaryContainer};white-space:nowrap;overflow:hidden;text-overflow:ellipsis">Button animation</div>
      <div${tgt('tutorial-action')} style="width:48px;height:48px;display:grid;place-items:center;flex:none">${icon('outlined/play_circle', 24, C.onSurfaceVariant)}</div>
      <div style="width:4px;flex:none"></div></div>`;
}

// PreviewStage (no hint under it on this screen): the walkthrough's phone on the pastel card, and over
// its glass the empty .bump-glass.
function stage() {
  const { g, svg } = M.scenePhoneSvg(COL_W, STAGE_H);
  const gl = g.glass;
  return `<div class="stage"${tgt('stage')} style="position:relative;width:${COL_W}px;height:${STAGE_H}px;overflow:hidden;${M.stageBackdrop(COL_W, STAGE_H)}">${svg}
      <div class="glass" style="position:absolute;left:${gl.x}px;top:${gl.y}px;width:${gl.w}px;height:${gl.h}px;overflow:hidden;border-radius:${gl.r}px ${gl.r}px 0 0">
        <div class="bump-glass" data-scale="${g.scale}" style="position:absolute;left:0;top:0;width:${gl.w}px;height:${gl.h}px"></div></div></div>`;
}

// Heading: 14 SemiBold primary, the hint (bodySmall) 4 under it; padding 6 at the sides, 12 below.
const heading = (name, title, hint) => `<div${tgt(`head-${name}`)} class="bump-heading" data-heading="${name}" style="padding:0 6px 12px">
    ${inhWrap(title, 14, 600, C.primary)}${bodySmall(hint, C.onSurfaceVariant, 'padding-top:4px')}</div>`;
// Segments: rows 3 dp apart, cut by segmentShape (M.segRadius).
const segments = (rows) => `<div style="display:flex;flex-direction:column;gap:3px">${rows.join('')}</div>`;
// Card: the rows' colour (surfaceVariant @ 65 %), padding 16.
const card = (radius, inner) => `<div style="border-radius:${radius};background:${C.row};padding:16px">${inner}</div>`;
// FlowRow of chips, 8 apart.
const flow = (items, { rowGap = 0, extra = '' } = {}) => `<div style="display:flex;flex-wrap:wrap;column-gap:8px;row-gap:${rowGap}px;${extra}">${items.join('')}</div>`;

// ChoiceChip (M3 FilterChip, 12 dp corners): 32 dp in its 48 dp touch box; ticked when chosen.
function chip(target, label, selected) {
  const look = selected ? `padding:0 16px 0 8px;background:${C.secondaryContainer}` : `padding:0 15px;border:1px solid ${C.outlineVariant}`;
  return `<div style="height:48px;display:flex;align-items:center;flex:none"><div${tgt(target)} style="height:32px;border-radius:12px;display:flex;align-items:center;gap:8px;${look};
      ${LABEL_LARGE};white-space:nowrap;color:${selected ? C.onSecondaryContainer : C.onSurfaceVariant}">${selected ? icon('filled/check', 18, C.onSecondaryContainer) : ''}${label}</div></div>`;
}

// ToggleRow: the feature's own switch — 46 dp tile, title 16 SemiBold, bodySmall under it, the switch.
// The whole row is the switch, and is lit (primaryContainer) while on.
function toggleRow(onState, k) {
  return `<div${tgt('enable')} style="display:flex;align-items:center;padding:12px 14px;border-radius:${M.segRadius(0, 1)};background:${onState ? C.primaryContainer : C.row}">
      <div style="width:46px;height:46px;border-radius:14px;flex:none;display:grid;place-items:center;background:${onState ? C.primary : 'rgba(224,231,255,.6)'}">${icon('filled/animation', 24, onState ? C.onPrimary : C.primary)}</div>
      <div style="width:14px;flex:none"></div>
      <div style="flex:1;min-width:0">${inhWrap('Animate the volume buttons', 16, 600)}${bodySmall('An animation plays beside the button you press, with the volume level, in place of the system’s slider.', C.onSurfaceVariant, 'padding-top:2px')}</div>
      <div style="width:10px;flex:none"></div><div${tgt('enable-switch')} style="flex:none">${M.m3switchAt(k)}</div></div>`;
}

// SwitchCard: title 15 Medium, bodySmall under it, the switch; padding 16 × 12. row-<name> / switch-<name>.
function switchCard(name, title, desc, v, radius) {
  return `<div${tgt(`row-${name}`)} style="display:flex;align-items:center;padding:12px 16px;border-radius:${radius};background:${C.row}">
      <div style="flex:1;min-width:0">${inhWrap(title, 15, 500)}${bodySmall(desc, C.onSurfaceVariant, 'padding-top:2px')}</div>
      <div style="width:10px;flex:none"></div><div${tgt(`switch-${name}`)} style="flex:none">${M.m3switchAt(kOf(v))}</div></div>`;
}

// PermissionNote / InfoNote (NoteRow): 8 above, radius 12, 18 dp icon, 10, text 13 sp on 18 sp lines, chevron.
function noteRow(target, text, glyph, bg, ink) {
  return `<div${tgt(target)} style="margin-top:8px;border-radius:12px;background:${bg};display:flex;align-items:center;padding:10px 12px">
      ${icon(glyph, 18, ink)}<div style="width:10px;flex:none"></div>
      ${inhWrap(text, 13, 400, ink, { lh: 18, extra: 'flex:1;min-width:0' })}${icon('filled/keyboard_arrow_right', 18, ink)}</div>`;
}
const permissionNote = (target, text) => noteRow(target, text, 'filled/warning', 'rgba(254,226,226,.6)', C.onErrorContainer);
const infoNote = (target, text) => noteRow(target, text, 'filled/info', 'rgba(243,244,246,.7)', C.onSecondaryContainer);
// Note: a line of guidance in the accent, bodySmall, 10 above, 6 in at the sides.
const note = (target, text) => `<div${tgt(target)}>${bodySmall(text, C.primary, 'padding:10px 6px 0')}</div>`;

// SliderControl: a 28 dp label row (label 15 Medium, −, the value 12 Bold primary in 48, +) over a 28 dp
// track. The track runs from x 2 to W − 2; the 4 × 18 thumb (6 × 22.5 while dragged) sits at
// 2 + (canvas − 4) × fraction in it. A nudge at the end of its range is disabled: no disc, glyph @ 30 %.
// Targets: slider-<name> (the track), slider-<name>-thumb, slider-<name>-minus / -plus.
function slider(name, label, display, value, [lo, hi], dragging) {
  const frac = clamp((value - lo) / (hi - lo));
  const nudge = (end, glyph, off) => `<div${tgt(`slider-${name}-${end}`)} style="width:28px;height:28px;border-radius:50%;flex:none;display:grid;place-items:center;
      background:${off ? 'transparent' : 'rgba(79,70,229,.10)'}">${icon(glyph, 14, off ? 'rgba(79,70,229,.3)' : C.primary)}</div>`;
  const cw = BODY_W - 4, cx = 2 + (cw - 4) * frac;
  const drag = dragging === name, tw = drag ? 6 : 4, th = drag ? 22.5 : 18;
  return `<div style="height:56px">
      <div style="height:28px;display:flex;align-items:center;gap:2px">
        ${inh(label, 15, 500, C.onSurface, 'flex:1;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis')}${nudge('minus', 'filled/remove', value <= lo + 1e-6)}
        <div style="min-width:48px;text-align:center;font:700 12px/1.172 Roboto;letter-spacing:.5px;color:${C.primary};white-space:nowrap">${display}</div>${nudge('plus', 'filled/add', value >= hi - 1e-6)}</div>
      <div${tgt(`slider-${name}`)} style="position:relative;height:28px">
        <div style="position:absolute;left:2px;top:0;width:${cw}px;height:28px;border-radius:14px;overflow:hidden">
          <div style="position:absolute;left:0;right:0;top:12px;height:4px;border-radius:2px;background:rgba(79,70,229,.18)"></div>
          <div style="position:absolute;left:0;top:12px;width:${cx}px;height:4px;border-radius:2px;background:${C.primary}"></div>
          <div${tgt(`slider-${name}-thumb`)} style="position:absolute;left:${cx - tw / 2}px;top:${14 - th / 2}px;width:${tw}px;height:${th}px;border-radius:${tw / 2}px;background:${C.primary}"></div>
        </div></div></div>`;
}

// Swatches: the label (15 Medium, 12 above), then 34 dp circles 10 apart (8 above): chosen, a 3 dp
// primary ring and the colour 5 in; otherwise a 1 dp outlineVariant ring and the colour 2 in.
function swatches(prefix, label, colours, chosen) {
  const want = String(chosen).replace('#', '').slice(-6).toUpperCase();
  const dots = colours.map((hex) => {
    const sel = hex.slice(1).toUpperCase() === want;
    return `<div${tgt(`${prefix}-${hex.slice(1).toLowerCase()}`)} style="position:relative;width:34px;height:34px;border-radius:50%;flex:none;
        box-shadow:inset 0 0 0 ${sel ? 3 : 1}px ${sel ? C.primary : C.outlineVariant}"><div style="position:absolute;inset:${sel ? 5 : 2}px;border-radius:50%;background:${hex}"></div></div>`;
  }).join('');
  return `${inhWrap(label, 15, 500, C.onSurface, { extra: 'padding-top:12px' })}<div style="display:flex;flex-wrap:wrap;gap:10px;padding-top:8px">${dots}</div>`;
}

// StyleTile: 92 wide (the cell's 132 tall), padding 6, corners 18, a 1 dp outlineVariant border on
// surfaceVariant @ 65 % — chosen, a 2 dp primary border on primaryContainer @ 60 % and the check badge
// (18 dp, 5 in from the stage's bottom right). The 80 × 92 stage (corners 12) on the wallpaper; the
// name 6 under it (and 2 above the padding), 13 sp at most, SemiBold when chosen.
function styleTile({ id, label }, selected, x, y) {
  const weight = selected ? 600 : 500;
  const size = labelSize(label, weight);
  const badge = `<div style="position:absolute;right:5px;bottom:5px;width:18px;height:18px;border-radius:50%;background:${C.primary};display:grid;place-items:center">${icon('filled/check', 14, C.onPrimary)}</div>`;
  return `<div${tgt(`tile-${id}`)} class="bump-tile" data-style="${id}" style="position:absolute;left:${x}px;top:${y}px;width:${TILE_W}px;height:${CELL_H}px;padding:${TILE_PAD}px;border-radius:18px;overflow:hidden;
      background:${selected ? 'rgba(224,231,255,.6)' : C.row};box-shadow:inset 0 0 0 ${selected ? 2 : 1}px ${selected ? C.primary : C.outlineVariant}">
      <div style="position:relative;width:${TILE_STAGE_W}px;height:${TILE_STAGE_H}px;border-radius:12px;overflow:hidden;background:${WALL(TILE_STAGE_W, TILE_STAGE_H)}">
        <img src="${TILES_URL}${id}.png" alt="" draggable="false" style="position:absolute;left:0;top:0;width:${TILE_STAGE_W}px;height:${TILE_STAGE_H}px">${selected ? badge : ''}</div>
      <div style="padding:6px 0 2px;text-align:center;white-space:nowrap;font:${weight} ${size}px/1.172 Roboto;letter-spacing:.5px;color:${C.onSurface}">${label}</div></div>`;
}

// The gallery's columns: the items fill each column top to bottom, then the next.
const COLUMNS = Math.ceil(BUMP_STYLES.length / 2);
const GALLERY_CONTENT_W = 2 * GRID_PAD + COLUMNS * TILE_W + (COLUMNS - 1) * GRID_GAP;
const GALLERY_MAX = Math.max(0, GALLERY_CONTENT_W - COL_W);
function gallery(style, scroll) {
  const off = clamp(+scroll || 0, 0, GALLERY_MAX);
  const tiles = BUMP_STYLES.map((s, i) => styleTile(s, s.id === style, GRID_PAD + Math.floor(i / 2) * PITCH, (i % 2) * (CELL_H + GRID_GAP))).join('');
  return `<div${tgt('gallery')} class="bump-gallery" data-scroll="${off}" style="position:relative;height:${GALLERY_H}px;overflow:hidden">
      <div style="position:absolute;left:0;top:0;width:${GALLERY_CONTENT_W}px;height:${GALLERY_H}px;transform:translateX(${-off}px)">${tiles}</div></div>`;
}

/**
 * The gallery's sideways scroll (px) that puts a style's tile `align` of the way across the gallery
 * (0 its left edge at the left, 0.5 centred, 1 its right edge at the right), as far as the row goes.
 * The 380 dp gallery shows three columns and 72 dp of a fourth; it scrolls 0 … 1246.
 */
export function bumpGalleryScroll(style, align = 0.5) {
  const i = STYLE_INDEX[style];
  if (i == null) throw new Error('unknown button animation style: ' + style);
  const x = GRID_PAD + Math.floor(i / 2) * PITCH;
  return Math.round(clamp(x + TILE_W * align - COL_W * align, 0, GALLERY_MAX));
}

// --- State -----------------------------------------------------------------------------------------------
function normalize(st) {
  const s = {
    status: 32, scroll: 0, enabled: undefined, enableK: null, accessibility: true, running: true, withBump: 'pause',
    sliderKeysOff: false, style: 'water', galleryScroll: 0, placing: 'up', places: {}, powerMenu: true, powerWake: false,
    depth: 18, sweep: -1, color: '#000000', accent: '#FFFFFF', showLevel: true, showPercent: false, sound: true,
    haptic: 'light', holdMs: 1400, speed: 1, dragging: null,
    ...Object.fromEntries(Object.entries(st).filter(([, v]) => v !== undefined)),      // a field left undefined keeps its default
  };
  if (STYLE_INDEX[s.style] == null) throw new Error('unknown button animation style: ' + s.style);
  if (!DEFAULT_PLACES[s.placing]) throw new Error('unknown button: ' + s.placing);
  s.places = Object.fromEntries(Object.keys(DEFAULT_PLACES).map((b) => [b, { ...DEFAULT_PLACES[b], ...(st.places?.[b] || {}) }]));
  // The switch drawn part-way (enableK); the row, its tile and the notes follow `enabled`, or the
  // switch's side of the middle when only enableK is given.
  s.switchK = s.enableK != null ? clamp(+s.enableK) : kOf(s.enabled);
  s.on = s.enabled === undefined ? s.switchK >= 0.5 : on(s.enabled);
  return s;
}

const fmt1 = (v) => { const r = Math.round(v * 10) / 10; return Number.isInteger(r) ? String(r) : r.toFixed(1); };   // NumberFormat, ≤ 1 decimal

function columnHtml(s) {
  const pl = s.places[s.placing];
  const sweep = s.sweep >= 0 ? s.sweep : shoulderOf(s.style);
  const powerNote = s.on && (on(s.powerMenu) || on(s.powerWake)) && !s.accessibility;
  return [
    // ---- on or off
    segments([toggleRow(s.on, s.switchK)]),
    s.on && !s.accessibility ? permissionNote('note-accessibility', 'Needs the accessibility service to answer the buttons at once and hide the system’s slider. Without it, the animation follows the system’s slider a moment later.') : '',
    s.on && !s.running ? note('note-service', 'Turn on Gesture Volume on the home screen for the buttons to play it.') : '',
    gap(24),
    // ---- the Quick slider
    heading('quick', 'Quick slider', 'Whether the Quick slider also comes up when you press a volume button.'),
    card(M.segRadius(0, 1), flow(WITH_BUMP.map(([id, label]) => chip(`chip-${id}`, label, s.withBump === id)))
      + bodySmall(WITH_BUMP_HINT[s.withBump] ?? WITH_BUMP_HINT.pause, C.onSurfaceVariant, 'padding-top:8px')),
    s.withBump === 'both' && s.sliderKeysOff ? infoNote('note-keys-off', 'The Quick slider’s Volume keys setting is Off, so it won’t come up. Tap to change it.') : '',
    gap(24),
    // ---- the animations
    heading('animation', 'Animation', 'Every animation is free. Tap one to see it on the phone above.'),
    gallery(s.style, s.galleryScroll),
    gap(24),
    // ---- where each button is
    heading('place', 'Where your buttons are', 'Pick a button, then slide until the press on your screen sits right beside it. With this screen open, pressing the real button shows where it lands too.'),
    card(M.segRadius(0, 1), [
      flow(BUTTONS.map(([id, label]) => chip(`chip-${id}`, label, s.placing === id)), { rowGap: 8, extra: 'padding-bottom:12px' }),
      inhWrap('Side', 15, 500),
      flow([chip('side-left', 'Left', !!pl.left), chip('side-right', 'Right', !pl.left)], { extra: 'padding:6px 0 10px' }),
      slider('position', 'Position', `${Math.round(pl.position * 100)}%`, pl.position * 100, [0, 100], s.dragging),
      gap(8),
      slider('length', 'Length', `${Math.round(pl.lengthDp)} dp`, pl.lengthDp, LENGTH, s.dragging),
      // Show on my phone (OutlinedButton, 12 dp corners, the phone glyph) and Reset (TextButton) at the end.
      `<div style="display:flex;align-items:center;padding-top:10px">
        <div style="height:48px;display:flex;align-items:center;flex:none"><div${tgt('show-on-phone')} style="height:40px;border-radius:12px;padding:0 24px;display:flex;align-items:center;
          box-shadow:inset 0 0 0 1px ${C.outlineVariant};${LABEL_LARGE};color:${C.onSurfaceVariant};white-space:nowrap">${icon('filled/phone_android', 18, C.onSurfaceVariant)}<div style="width:8px;flex:none"></div>Show on my phone</div></div>
        <div style="flex:1"></div>
        <div style="height:48px;display:flex;align-items:center;flex:none"><div${tgt('reset')} style="height:40px;min-width:58px;padding:0 12px;border-radius:20px;display:flex;align-items:center;justify-content:center;
          ${LABEL_LARGE};color:${C.primary};white-space:nowrap">Reset</div></div></div>`,
    ].join('')),
    gap(24),
    // ---- the power button
    heading('power', 'Power button', 'Android doesn’t tell apps when the power button is pressed, so this plays when it opens the power menu or wakes the screen.'),
    segments([
      switchCard('power-menu', 'When it opens the power menu', 'Hold the power button and the edge answers beside it as the menu comes up.', s.powerMenu, M.segRadius(0, 2)),
      switchCard('power-wake', 'When it wakes the screen', 'Also plays when something else wakes the screen, such as a notification.', s.powerWake, M.segRadius(1, 2)),
    ]),
    powerNote ? note('note-power', 'The power button needs the accessibility service: it is how the app sees the power menu and the screen waking.') : '',
    gap(24),
    // ---- the look
    heading('look', 'Look', 'Its size, its colours and what it shows.'),
    segments([
      card(M.segRadius(0, 3), [
        slider('depth', 'Depth', `${Math.round(s.depth)} dp`, s.depth, DEPTH, s.dragging),
        slider('sweep', 'End sweep', `${Math.round(sweep * 100)}%`, sweep * 100, SWEEP, s.dragging),
        swatches('color', 'Colour', BODY_COLORS, s.color),
        swatches('accent', 'Volume colour', ACCENT_COLORS, s.accent),
      ].join('')),
      switchCard('show-level', 'Show the volume', 'A bar inside the bump that fills as it gets louder.', s.showLevel, M.segRadius(1, 3)),
      switchCard('show-percent', 'Show the number', 'The volume beside the bump, as a percentage.', s.showPercent, M.segRadius(2, 3)),
    ]),
    gap(24),
    // ---- the sound
    heading('sound', 'Sound', 'Every animation has a sound of its own, to go with it.'),
    segments([switchCard('sound', 'Play the animation’s sound', 'At each press, at the volume you’re setting.', s.sound, M.segRadius(0, 1))]),
    gap(24),
    // ---- the feel
    heading('feel', 'Feel', 'How it buzzes, how long it stays and how fast it moves.'),
    card(M.segRadius(0, 1), [
      inhWrap('Vibration', 15, 500),
      flow(HAPTICS.map(([id, label]) => chip(`haptic-${id}`, label, s.haptic === id)), { extra: 'padding:6px 0 10px' }),
      slider('hold', 'Stays for', `${fmt1(s.holdMs / 1000)} s`, s.holdMs, HOLD, s.dragging),
      gap(8),
      slider('speed', 'Speed', `${fmt1(s.speed)}×`, s.speed, SPEED, s.dragging),
    ].join('')),
    gap(32),
  ].join('');
}

function screenHtml(s) {
  return `<div class="bump-screen" style="position:absolute;inset:0;display:flex;flex-direction:column">
      ${topBar(s.status)}
      <div class="pinned" style="padding:${PINNED_TOP}px 16px ${PINNED_BOTTOM}px;flex:none">${stage()}</div>
      <div class="scroll" style="position:relative;overflow:hidden;flex:1;min-height:0">
        <div class="scroll-content" style="padding:16px 16px ${16 + NAV}px">${columnHtml(s)}</div></div></div>`;
}

// --- Laying it out once, off-screen ---------------------------------------------------------------------
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

// What moves things up and down the column (the rest only changes what is drawn).
const layoutKey = (s) => JSON.stringify([s.status, s.on, s.accessibility, s.running, s.withBump, s.sliderKeysOff,
  on(s.powerMenu) || on(s.powerWake), s.placing, s.haptic, s.places[s.placing].left]);
const layouts = new Map();
function layoutOf(s, el = null) {
  const key = layoutKey(s);
  if (layouts.has(key)) return layouts.get(key);
  const L = withMounted(el ?? M.screen(screenHtml(s)), (e) => {
    const box = e.getBoundingClientRect(), k = box.width / SW || 1;
    const vp = e.querySelector('.scroll').getBoundingClientRect(), inner = e.querySelector('.scroll-content');
    const c = inner.getBoundingClientRect();
    const content = {}, headings = {};
    for (const t of inner.querySelectorAll('[data-target]')) content[t.dataset.target] = +((t.getBoundingClientRect().top - c.top) / k).toFixed(1);
    for (const t of inner.querySelectorAll('.bump-heading')) headings[t.dataset.heading] = content[`head-${t.dataset.heading}`];
    const viewportTop = +((vp.top - box.top) / k).toFixed(2), viewportH = +(vp.height / k).toFixed(2), contentH = +(c.height / k).toFixed(1);
    return { viewportTop, viewportH, contentH, maxScroll: +Math.max(0, contentH - viewportH).toFixed(1), content, headings };
  });
  if (document.fonts.check('400 16px Roboto') && document.fonts.check('600 16px Roboto')) layouts.set(key, L);   // never keep a fallback font's layout
  return L;
}

function targetsOf(el) {
  return withMounted(el, (e) => {
    const box = e.getBoundingClientRect(), k = box.width / SW || 1;
    const out = {};
    for (const t of e.querySelectorAll('[data-target]')) {
      const r = t.getBoundingClientRect();
      out[t.dataset.target] = { x: +((r.left - box.left) / k).toFixed(1), y: +((r.top - box.top) / k).toFixed(1), w: +(r.width / k).toFixed(1), h: +(r.height / k).toFixed(1) };
    }
    return out;
  });
}

// --- The screen --------------------------------------------------------------------------------------------
/**
 * The whole Button animation screen. State (defaults):
 *   status 32          the status bar's height
 *   scroll 0           the settings column's scroll (px), as translateY; held at el.layout.maxScroll
 *                      (below 0 is the rubber band). The stage above it is pinned and never moves.
 *   enabled false      the feature's switch: the row and its tile lit, and the notes under it
 *   enableK null       the switch drawn part-way (0 off .. 1 on, m3switchAt); with `enabled` left out,
 *                      the row follows which side of the middle it is on
 *   accessibility true false (and enabled): the red PermissionNote under the switch; with a power
 *                      switch on, the Power button's line about the service too
 *   running true       false (and enabled): the line asking for the service to be turned on
 *   withBump 'pause'   'both' | 'pause' (the app's default is 'pause'): the Quick slider's chip, and its hint
 *   sliderKeysOff false with withBump 'both': the InfoNote that the Quick slider's Volume keys is Off
 *   style 'water'      the chosen tile (BUMP_STYLES ids); also what End sweep shows while sweep < 0
 *   galleryScroll 0    the gallery's sideways scroll (px, 0 … 1246; see bumpGalleryScroll)
 *   placing 'up'       'up' | 'down' | 'power': the button being placed (its chip, side and sliders)
 *   places             { up: { left, position 0..1, lengthDp }, down, power }, each merged over the
 *                      app's defaults (right; 0.27 / 0.36 / 0.47; 52 / 52 / 48 dp)
 *   powerMenu true, powerWake false, showLevel true, showPercent false, sound true
 *                      switches: booleans, or 0..1 to draw one part-way
 *   depth 18           dp, 2 … 44
 *   sweep -1           End sweep 0.05 … 1; below 0, the style's own (water 90 %, most 45 %)
 *   color '#000000', accent '#FFFFFF'   the chosen swatches (Colour, Volume colour)
 *   haptic 'light'     'off' | 'light' | 'strong'
 *   holdMs 1400        500 … 4000;   speed 1   0.5 … 2
 *   dragging null      a slider's name ('position', 'length', 'depth', 'sweep', 'hold', 'speed'): its thumb grown
 * el.layout: { viewportTop, viewportH, contentH, maxScroll, scroll (as applied), content: { target: y in
 * the column at scroll 0 }, headings: { quick, animation, place, power, look, sound, feel: y }, glass
 * (bumpStageGlass), targets (getter: every target's {x, y, w, h} on the screen as built) }. A target
 * whose column y is Y is at screen y viewportTop + Y − scroll.
 * data-target: back, tutorial-action, stage, enable (the row) / enable-switch, note-accessibility,
 *   note-service, head-quick, chip-both, chip-pause, note-keys-off, head-animation, gallery,
 *   tile-<style> (all 32), head-place, chip-up, chip-down, chip-power, side-left, side-right,
 *   slider-position, slider-length, show-on-phone, reset, head-power, row-/switch-power-menu,
 *   row-/switch-power-wake, note-power, head-look, slider-depth, slider-sweep, color-<hex>, accent-<hex>,
 *   row-/switch-show-level, row-/switch-show-percent, head-sound, row-/switch-sound, head-feel,
 *   haptic-off, haptic-light, haptic-strong, slider-hold, slider-speed; every slider-<name> also has
 *   slider-<name>-thumb, -minus and -plus.
 */
export function bumpSettingsScreen(state = {}) {
  const s = normalize(state);
  const el = M.screen(screenHtml(s));
  const L = layoutOf(s, el);
  const scroll = Math.min(+s.scroll || 0, L.maxScroll);
  el.querySelector('.scroll-content').style.transform = `translateY(${-scroll}px)`;
  el.layout = { ...L, content: { ...L.content }, headings: { ...L.headings }, scroll, glass: bumpStageGlass(s) };
  Object.defineProperty(el.layout, 'targets', { get: () => targetsOf(el), enumerable: false });
  return el;
}

/**
 * The page scroll that puts a target in the settings column `at` dp under the top of the column's
 * viewport (the stage's foot), as far as the page scrolls. For a heading: bumpScrollFor('head-look', s).
 */
export function bumpScrollFor(name, state = {}, at = 8) {
  const L = layoutOf(normalize(state));
  const y = L.content[name];
  if (y == null) throw new Error(`no data-target "${name}" in the settings column`);
  return Math.round(clamp(y - at, 0, L.maxScroll));
}

/**
 * The stage's glass, where the animation plays, in dp from the screen's top left. Pinned, so the page
 * scroll never moves it. r: the radius of its two top corners (the bottom runs off the stage).
 * scale: the glass is the phone's screen drawn at this scale, so the app lays the animation out on a
 * w / scale × h / scale (phoneW × phoneH) screen and draws it this much smaller; the bump sits at the
 * middle of the glass's height, on the side the buttons are on.
 */
export function bumpStageGlass(state = {}) {
  const status = state.status ?? 32;
  const g = M.stageGeometry(COL_W, STAGE_H), gl = g.glass;
  const r3 = (v) => Math.round(v * 1000) / 1000;
  return { x: r3(16 + gl.x), y: r3(status + 64 + PINNED_TOP + gl.y), w: r3(gl.w), h: r3(gl.h), r: r3(gl.r), scale: +g.scale.toFixed(6),
    phoneW: r3(gl.w / g.scale), phoneH: r3(gl.h / g.scale) };
}
