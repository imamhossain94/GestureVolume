// The panels' settings screens, to LongPressMenuScreen.kt and QuickSliderScreen.kt: the Long-press
// menu screen with the real menu card on its preview, the whole Quick slider screen with the real
// panel on its preview and every picture row running live, and the two rows every panel screen
// shares, the panel styles (PanelThemeSelector) and the opening animations (PanelAnimationSelector).
//
// A screen is a pure function of a state object and returns an element (M.screen). What moves on it
// (the preview's panel or card, every live tile, the entrance tiles) is started by its mount
// function, whose draw({ t, ... }) the video calls every frame; nothing here reads a clock. Every
// tappable thing carries data-target="name" for the hand; the names are listed above each screen.
//
// Toggles take a number 0..1 (an animated M3 switch); selections take ids; sections take `open`:
// an id, a list of ids, or { id: k } with k the expansion 0..1 (sectionExpand(dt) gives the app's
// spring), so a section can be seen opening.
import { h, icon, drawableIcon, clamp, lerp, Ease, spring, loadIcons } from './core.js';
import * as M from './m3.js';
import { QuickPanel, SHADER_FILLS, SURGE_FILLS, entranceFrame, argb, css as argbCss, loadDrawables } from './quickpanel.js';
import { glassLayers } from './deck.js';
import { fillPanel, FLOURISHES, PIXEL_PATTERNS, PIXEL_CYCLE_MS, EFFORT_NAMES, pace, levelAt } from './panelfills.js';
import './pictorialfills.js';

const C = M.LIGHT;
const ON_BG = '#111827';                         // the Scaffold's content colour: onBackground
const W = 412, BODY_W = 348;                     // the screen; a section body's inner width
const STAGE_W = W - 32, STAGE_H = clamp(0.27 * 915, 212, 320);
const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;');
const fmtTimes = (v, min = 0) => {                // NumberFormat, at most 2 decimals (and at least `min`)
  const s = (Math.round(v * 100) / 100).toFixed(2).replace(/0+$/, '').replace(/\.$/, '');
  if (!min) return s;
  const [i, d = ''] = s.split('.');
  return `${i}.${d.padEnd(min, '0')}`;
};
const pct = (v) => `${Math.round(v * 100)}%`;
const on = (k) => clamp(+k || 0) >= 0.5;

// --- Catalogues (the app's ids, the exact English labels) ----------------------------------------
export const PANEL_THEMES = [
  ['solid', 'Solid'], ['frosted', 'Frosted'], ['glass', 'Glass'], ['aero', 'Gloss'], ['vibrant', 'Clear'],
  ['paper', 'Paper'], ['midnight', 'Midnight'], ['amoled', 'AMOLED'], ['acrylic', 'Acrylic'], ['amethyst', 'Amethyst'],
].map(([id, label]) => ({ id, label }));
export const PANEL_ANIMATIONS = [
  ['fade', 'Fade', 170], ['pop', 'Pop', 210], ['spring', 'Spring', 330], ['zoom', 'Zoom', 280], ['unfold', 'Unfold', 260],
  ['expand', 'Expand', 210], ['rise', 'Rise', 280], ['drop', 'Drop', 330], ['slide', 'Slide', 210], ['swing', 'Swing', 330],
  ['flip', 'Flip', 280], ['tilt', 'Tilt', 280], ['blinds', 'Blinds', 260], ['tide', 'Tide', 260], ['iris', 'Iris', 260],
  ['settle', 'Settle', 330],
].map(([id, label, ms]) => ({ id, label, ms }));
const THEME_LABEL = Object.fromEntries(PANEL_THEMES.map((x) => [x.id, x.label]));
const ANIM = Object.fromEntries(PANEL_ANIMATIONS.map((x) => [x.id, x]));
// PanelAnimation.scaledDurationMs, in seconds: how long an entrance runs at `speed`.
export const entranceSeconds = (id, speed = 1) => Math.max(90, (ANIM[id]?.ms ?? 220) / speed) / 1000;

// PanelTheme.menuPalette (0xAARRGGBB), the blur each material asks for, and which are lit / pale.
const MENU_PAL = {
  solid: ['#F41C1C20', '#F2FFFFFF', '#B8FFFFFF', '#1AFFFFFF', '#1FFFFFFF', '#1FFFFFFF', 0],
  frosted: ['#A815161B', '#F5FFFFFF', '#C2FFFFFF', '#24FFFFFF', '#1AFFFFFF', '#2EFFFFFF', 28],
  glass: ['#5E14141A', '#FFFFFFFF', '#CCFFFFFF', '#2EFFFFFF', '#24FFFFFF', '#33FFFFFF', 40],
  aero: ['#8CD6E6F4', '#F2101820', '#A6101820', '#59FFFFFF', '#1F000000', '#8CFFFFFF', 36],
  vibrant: ['#A6F7F7FA', '#F01B1B1F', '#9E1B1B1F', '#14000000', '#1A000000', '#66FFFFFF', 44],
  paper: ['#F7F8F7F4', '#F21A1A1E', '#9E1A1A1E', '#10000000', '#17000000', '#24000000', 0],
  midnight: ['#F50D1330', '#F5E8ECFF', '#B8C9D1F5', '#2E6C7CFF', '#248C9CFF', '#3D6C7CFF', 0],
  amoled: ['#FF000000', '#FFFFFFFF', '#B3FFFFFF', '#1FFFFFFF', '#1FFFFFFF', '#33FFFFFF', 0],
  acrylic: ['#A82B2430', '#F5FFF7F0', '#C2FFEFE3', '#26FFE9D6', '#1FFFE9D6', '#33FFE9D6', 32],
  amethyst: ['#6B3B2463', '#FFFFFFFF', '#D1F1E6FF', '#33E7D4FF', '#29E7D4FF', '#40E7D4FF', 40],
};
const LIT = new Set(['glass', 'aero', 'amethyst']);
const PALE = new Set(['aero', 'vibrant', 'paper']);
const lumOf = (hex) => { const [, r, g, b] = argb(hex); return 0.2126 * r / 255 + 0.7152 * g / 255 + 0.0722 * b / 255; };
function menuPalette(theme, surface = null) {
  const [s, on1, dim, chip, divider, border, blur] = MENU_PAL[theme] || MENU_PAL.solid;
  if (!surface) return { surface: s, on: on1, dim, chip, divider, border, blur, lit: LIT.has(theme), light: lumOf(s) > 0.5 };
  const light = lumOf(surface) > 0.5;
  return {
    surface, on: light ? '#F0101014' : '#F5FFFFFF', dim: light ? '#9E101014' : '#C2FFFFFF', chip: light ? '#14000000' : '#24FFFFFF',
    divider: light ? '#1A000000' : '#1AFFFFFF', border: light ? '#33000000' : '#2EFFFFFF', blur, lit: LIT.has(theme), light,
  };
}
const rgba = (hex) => argbCss(argb(hex));

// HandlerActionCatalog.CONTEXT_MENU_CANDIDATES, in catalogue order: [id, label, icon].
export const MENU_CATALOG = [
  ['open-deck', 'Open deck', 'filled/view_sidebar'], ['quick-panel', 'Quick panel', 'drawable/ic_vol_increase'],
  ['volume-ui', 'Open volume UI', 'drawable/ic_vol_increase'], ['mute', 'Mute', 'drawable/ic_mute'],
  ['mute-unmute', 'Mute or Unmute', 'drawable/ic_mute'], ['auto-brightness', 'Auto brightness on/off', 'drawable/ic_brightness_auto'],
  ['music-overlay', 'Active Music Overlay', 'drawable/ic_music_ui'], ['search', 'Search', 'filled/search'],
  ['timer', 'Timer', 'filled/timer'], ['calculator', 'Calculator', 'filled/calculate'], ['notes', 'Notes', 'filled/edit_note'],
  ['media', 'Media controls', 'filled/music_note'], ['coin', 'Coin toss', 'filled/paid'], ['dice', 'Dice roll', 'filled/casino'],
  ['qr', 'Scan QR code', 'filled/qr_code_scanner'], ['flashlight', 'Flashlight', 'filled/flashlight_on'],
  ['dnd', 'Do Not Disturb', 'filled/do_not_disturb_on'], ['auto-rotate', 'Auto-rotate', 'filled/screen_rotation'],
  ['ring-vibrate', 'Ring / vibrate', 'filled/vibration'], ['wifi', 'Wi-Fi', 'filled/wifi'], ['bluetooth', 'Bluetooth', 'filled/bluetooth'],
  ['internet', 'Internet', 'filled/network_cell'], ['camera', 'Camera', 'filled/photo_camera'],
  ['voice', 'Voice assistant', 'filled/keyboard_voice'], ['play-pause', 'Play / pause', 'filled/play_arrow'],
  ['next', 'Next track', 'filled/skip_next'], ['previous', 'Previous track', 'filled/skip_previous'], ['lock', 'Lock screen', 'filled/lock'],
  ['screenshot', 'Screenshot', 'filled/screenshot'], ['back', 'Back', 'filled/arrow_back'], ['home', 'Home', 'filled/home'],
  ['recents', 'Recent apps', 'filled/view_carousel'], ['notifications', 'Notifications', 'filled/notifications'],
  ['quick-settings', 'Quick settings', 'filled/tune'], ['power-menu', 'Power menu', 'filled/power_settings_new'],
  ['hide-handler', 'Hide Handler', 'drawable/ic_visibility_hide'], ['stop-service', 'Stop service', 'drawable/ic_power'],
  ['open-app', 'Open App', 'drawable/ic_app_open'],
].map(([id, label, glyph]) => ({ id, label, glyph }));
const ENTRY = Object.fromEntries(MENU_CATALOG.map((e) => [e.id, e]));
// HandlerActions.DEFAULT_CONTEXT_MENU, in catalogue order.
export const DEFAULT_MENU_ITEMS = ['open-deck', 'quick-panel', 'volume-ui', 'mute-unmute', 'flashlight', 'play-pause', 'hide-handler', 'stop-service', 'open-app'];
const PINNED = ['hide-handler'];

// SliderFill.ALL: [id, label]: every fill, each drawn as the app draws it (panelfills.js for the
// program fills, Pixels, Effort and Glimmer; pictorialfills.js for the pictures, tides and stripes).
export const FILLS = [
  ['solid', 'Solid'], ['pixels', 'Pixels'], ['shader', 'Shaders'], ['surge', 'Surge'], ['effort', 'Effort'], ['glimmer', 'Glimmer'],
  ['liquid', 'Liquid'], ['waveform', 'Waveform'], ['sunrise', 'Sunrise'], ['spectrum', 'Spectrum'], ['galaxy', 'Galaxy'], ['silk', 'Silk'],
  ['tide-up', 'Tide up'], ['aurora', 'Aurora'], ['plasma', 'Plasma'], ['hologram', 'Hologram'], ['nebula', 'Nebula'], ['ember', 'Ember'],
  ['sonar', 'Sonar'], ['circuit', 'Circuit'], ['dot-matrix', 'Dot matrix'], ['cyberpunk', 'Cyberpunk'], ['matrix-rain', 'Matrix rain'],
  ['rune', 'Rune'], ['tide-down', 'Tide down'], ['stripes', 'Stripes'], ['fireflies', 'Fireflies'], ['snowfall', 'Snowfall'],
  ['heartbeat', 'Heartbeat'], ['neon', 'Neon'], ['ocean', 'Ocean'], ['gradient', 'Gradient'], ['confetti', 'Confetti'], ['warp', 'Warp'],
  ['storm', 'Storm'], ['fireworks', 'Fireworks'],
].map(([id, label]) => ({ id, label }));
const FILL_LABEL = Object.fromEntries(FILLS.map((f) => [f.id, f.label]));
// The pictorial fills' cycles (SliderFill.cycleMs), for their clock.
const FILL_CYCLE_MS = { liquid: 6000, waveform: 2400, sunrise: 9000, spectrum: 7000, galaxy: 24000, silk: 7000, 'tide-up': 2600, aurora: 9000,
  plasma: 9000, hologram: 4000, nebula: 7000, ember: 3000, sonar: 3600, circuit: 3200, 'dot-matrix': 2000, cyberpunk: 2400,
  'matrix-rain': 3000, rune: 4200, 'tide-down': 2600, stripes: 2400, fireflies: 8000, snowfall: 12000, heartbeat: 2400, neon: 1600,
  ocean: 6000, gradient: 8000, confetti: 6000, warp: 3000, storm: 6000, fireworks: 4800 };
export const SHADER_EFFECTS = [
  ['lava-lamp', 'Lava lamp', 'Blobs'], ['liquid-chrome', 'Liquid chrome', 'Warp'], ['molten', 'Molten', 'Flow'], ['aurora', 'Aurora', 'Sway'],
  ['ink-smoke', 'Ink smoke', 'Warp'], ['plasma', 'Plasma', 'Distortion'], ['caustics', 'Caustics', 'Softness'], ['soap-film', 'Soap film', 'Thickness'],
  ['mesh', 'Mesh', 'Drift'], ['clouds', 'Clouds', 'Billow'], ['smoke', 'Smoke', 'Swirl'], ['starfield', 'Starfield', 'Density'],
].map(([id, label, detail]) => ({ id, label, detail }));
export const SURGE_LOOKS = [['curve', 'Curve'], ['electric', 'Electric'], ['honeycomb', 'Honeycomb'], ['streaks', 'Streaks'], ['blocks', 'Blocks'], ['flame', 'Flame']]
  .map(([id, label]) => ({ id, label }));
const PATTERN_LABEL = { spectrum: 'Spectrum', steady: 'Steady', breathe: 'Breathe', sweep: 'Sweep', pendulum: 'Pendulum', snake: 'Snake',
  twinkle: 'Twinkle', checkers: 'Checkers', meter: 'Meter', rain: 'Rain', rainbow: 'Rainbow', aurora: 'Aurora', plasma: 'Plasma',
  embers: 'Embers', thermal: 'Thermal', confetti: 'Confetti', chromatic: 'Chromatic', candy: 'Candy', ripple: 'Ripple' };
const EFFORT_LOOKS = [{ id: 'steps', label: 'Steps' }, { id: 'dots', label: 'Dots' }];
const GLIMMER_LOOKS = [{ id: 'both', label: 'Handle & stops', handle: true, stops: true }, { id: 'handle', label: 'Handle', handle: true, stops: false },
  { id: 'stops', label: 'Stops', handle: false, stops: true }, { id: 'plain', label: 'Dots only', handle: false, stops: false }];
const FLOURISH_LABEL = { burst: 'Burst', ripple: 'Ripple', shine: 'Shine', sparkle: 'Sparkle', confetti: 'Confetti', neon: 'Neon', pulse: 'Pulse' };
const TARGETS = [['brightness', 'Brightness'], ['adaptive', 'Adaptive volume'], ['media', 'Media volume'], ['ring', 'Ring volume'], ['alarm', 'Alarm volume']];
const OPENERS = [['in', 'Swipe in'], ['out', 'Swipe out'], ['both', 'Both'], ['off', 'Off']];
const HAPTICS = [['off', 'Off'], ['light', 'Light'], ['medium', 'Medium'], ['strong', 'Strong']];
const SOUNDS = [['off', 'Off'], ['click', 'Click'], ['adaptive', 'Adaptive']];
const KEYS = [['off', 'Off'], ['follow', 'With the system'], ['instant', 'Instant']];
const KEYS_HINT = {
  off: 'The keys work as usual and the panel stays closed.',
  follow: 'The panel opens beside the system\'s own volume slider, about half a second after the press. It also follows the system panel and other apps when they change the volume.',
  instant: 'The panel opens the moment a key is pressed and moves one step per press, in place of the system\'s volume slider. During a call, on the lock screen or with the screen off, the keys work as usual.',
};

// --- Text and controls (the app's components, with data-targets) --------------------------------------
// Text that sets only a size inherits bodyLarge (24 sp line, 0.5 sp tracking) trimmed to the font.
const tx = (s, size, weight = 400, color = C.onSurface, extra = '') =>
  `<div style="font:${weight} ${size}px/1.172 Roboto;letter-spacing:.5px;color:${color};${extra}">${esc(s)}</div>`;
const bodySmall = (s, color = C.onSurfaceVariant, extra = '') => `<div style="font:400 12px/16px Roboto;letter-spacing:.4px;color:${color};${extra}">${esc(s)}</div>`;
const bodyMedium = (s, color = ON_BG, extra = '') => `<div style="font:400 14px/20px Roboto;letter-spacing:.25px;color:${color};${extra}">${esc(s)}</div>`;
const gap = (n) => `<div style="height:${n}px;flex:none"></div>`;
const sep = () => `<div style="padding:12px 0"><div style="height:1px;background:rgba(31,41,55,.10)"></div></div>`;
const label15 = (s) => tx(s, 15, 400, C.onSurface, 'padding-bottom:4px');            // QuickSliderScreen.Label
const hint = (s, extra = 'padding-bottom:10px') => bodySmall(s, C.onSurfaceVariant, extra);   // .Hint
const heading15 = (s, extra = '') => tx(s, 15, 400, C.onSurface, extra);               // the rows' headings
const sectionLabel = (s) => `<div style="font:700 14px/20px Roboto;letter-spacing:.1px;color:${C.primary};padding-bottom:8px">${esc(s)}</div>`;
const glyph = (name, size, color) => (name.startsWith('drawable/') ? drawableIcon(name, size, color) : icon(name, size, color));

function topBar(title, status) {
  return `<div style="height:${status + 64}px;padding-top:${status}px;display:flex;align-items:center;flex:none">
      <div data-target="back" style="width:48px;height:48px;margin-left:4px;display:grid;place-items:center;flex:none">${icon('filled/arrow_back', 24, C.onSurface)}</div>
      <div style="flex:1;padding-left:4px;font:400 22px/28px Roboto;color:${C.onPrimaryContainer};white-space:nowrap">${esc(title)}</div>
      <div data-target="help" style="width:48px;height:48px;display:grid;place-items:center;flex:none">${icon('outlined/help_outline', 24, C.onSurfaceVariant)}</div>
      <div style="width:4px;flex:none"></div></div>`;
}

// `open` as the section's expansion 0..1 (the app's AppearanceSection).
function openK(open, id, def) {
  if (open == null) return id === def ? 1 : 0;
  if (typeof open === 'string') return open === id ? 1 : 0;
  if (Array.isArray(open)) return open.includes(id) ? 1 : 0;
  return clamp(+open[id] || 0, 0, 1.2);
}
// The expand / collapse springs (AppearanceMotion): k for dt seconds after the tap.
export const sectionExpand = (dt) => clamp(spring(dt, { dampingRatio: 0.75, stiffness: 400 }), 0, 1.2);
export const sectionCollapse = (dt) => 1 - clamp(spring(dt, { dampingRatio: 0.75, stiffness: 400 }), 0, 1);

function section(id, glyphName, title, summary, k, body) {
  const kk = clamp(k), seam = lerp(20, 6, kk), opened = kk >= 0.5;
  const tileBg = opened ? C.primary : 'rgba(224,231,255,.6)';
  const head = `<div data-target="section-${id}" style="display:flex;align-items:center;padding:12px 14px;background:${C.row};border-radius:20px 20px ${seam}px ${seam}px">
      <div style="width:46px;height:46px;border-radius:14px;background:${tileBg};display:grid;place-items:center;flex:none">${icon(glyphName, 24, opened ? C.onPrimary : C.primary)}</div>
      <div style="width:14px;flex:none"></div>
      <div style="flex:1;min-width:0">${tx(title, 16, 600)}${summary ? tx(summary, 14, 400, C.onSurfaceVariant, 'padding-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis') : ''}</div>
      <div style="transform:rotate(${180 * clamp(k, 0, 1.15)}deg);flex:none">${icon('filled/expand_more', 24, C.outline)}</div></div>`;
  if (kk <= 0) return `<div class="sec" data-section="${id}">${head}</div>`;
  // Opening: expandVertically (the body clipped to k of its height) with a quicker fade.
  const bodyHtml = `<div style="padding-top:3px"><div class="sec-body" style="background:${C.row};border-radius:6px 6px 20px 20px;padding:16px">${body}</div></div>`;
  const wrap = kk >= 1 ? bodyHtml : `<div style="height:calc-size(auto, size * ${kk.toFixed(4)});overflow:hidden;opacity:${clamp(kk * 1.8)}">${bodyHtml}</div>`;
  return `<div class="sec" data-section="${id}">${head}${wrap}</div>`;
}

// M3 FilterChip (ChoiceChip): 32 dp in its 48 dp touch slot.
function chip(target, label, selected) {
  const pad = selected ? `padding:0 16px 0 8px;background:${C.secondaryContainer}` : `padding:0 15px;border:1px solid ${C.outlineVariant}`;
  return `<div style="height:48px;display:flex;align-items:center;flex:none"><div data-target="${target}" style="height:32px;border-radius:12px;display:flex;align-items:center;gap:8px;${pad};
      font:500 14px/20px Roboto;letter-spacing:.1px;white-space:nowrap;color:${selected ? C.onSecondaryContainer : C.onSurfaceVariant}">
      ${selected ? icon('filled/check', 18, C.onSecondaryContainer) : ''}${esc(label)}</div></div>`;
}
const chipRow = (chips, extra = '') => `<div style="display:flex;flex-wrap:wrap;gap:8px;${extra}">${chips.join('')}</div>`;

// SettingSwitchItem: the row toggles; the switch is `name`, the row `name-row`.
function switchItem(name, title, desc, k) {
  return `<div data-target="${name}-row" style="display:flex;align-items:center;padding:6px 0;border-radius:14px">
      <div style="flex:1;min-width:0">${tx(title, 16, 600)}${bodySmall(desc, C.onSurfaceVariant, 'padding-top:2px')}</div>
      <div style="width:12px;flex:none"></div><div data-target="${name}" style="flex:none">${M.m3switchAt(clamp(+k || 0))}</div></div>`;
}

// SliderControl: label, − value +, then the 28 dp slim track (thumb 4 x 18, 6 x 22.5 while dragged).
// Targets: slider-<name> (the track), slider-<name>-minus / -plus.
function slider(name, label, value, f, { atMin = false, atMax = false, drag = false } = {}) {
  const nudge = (g, off, end) => `<div data-target="slider-${name}-${end}" style="width:28px;height:28px;border-radius:50%;flex:none;background:${off ? 'transparent' : 'rgba(79,70,229,.10)'};display:grid;place-items:center">${icon(g, 14, off ? 'rgba(79,70,229,.3)' : C.primary)}</div>`;
  const cx = 2 + (BODY_W - 4) * clamp(f);
  const tw = drag ? 6 : 4, th = drag ? 22.5 : 18;
  return `<div class="slider" style="height:56px">
      <div style="height:28px;display:flex;align-items:center;gap:2px">${tx(label, 15, 500, C.onSurface, 'flex:1;min-width:0')}${nudge('filled/remove', atMin, 'minus')}
        <div style="min-width:48px;text-align:center;font:700 12px/1.172 Roboto;letter-spacing:.5px;color:${C.primary}">${esc(value)}</div>${nudge('filled/add', atMax, 'plus')}</div>
      <div data-target="slider-${name}" style="position:relative;height:28px;width:${BODY_W}px;overflow:hidden;border-radius:14px">
        <div style="position:absolute;left:0;right:0;top:12px;height:4px;border-radius:2px;background:rgba(79,70,229,.18)"></div>
        <div style="position:absolute;left:0;width:${cx}px;top:12px;height:4px;border-radius:2px;background:${C.primary}"></div>
        <div class="thumb" style="position:absolute;left:${cx - tw / 2}px;top:${14 - th / 2}px;width:${tw}px;height:${th}px;border-radius:${tw / 2}px;background:${C.primary}"></div>
      </div></div>`;
}
const sliderAt = (name, label, value, v, lo, hi, dragging, text) =>
  slider(name, label, text ?? value, (v - lo) / (hi - lo), { atMin: v <= lo + 1e-6, atMax: v >= hi - 1e-6, drag: dragging === name });

function colorRow(target, label, hex) {
  return `<div data-target="${target}" style="display:flex;align-items:center;padding:6px 0;border-radius:14px">
      <div style="width:46px;height:46px;border-radius:14px;background:${hex};box-shadow:inset 0 0 0 1px rgba(209,213,219,.35);flex:none"></div>
      <div style="width:14px;flex:none"></div>
      <div style="flex:1;min-width:0">${tx(label, 16, 600)}${tx(hex.slice(0, 7).toUpperCase(), 14, 500, C.onSurfaceVariant, 'padding-top:2px')}</div>
      ${icon('filled/edit', 20, C.outline)}</div>`;
}

function permissionNote(s, target) {
  return `<div data-target="${target}" style="margin-top:8px;border-radius:12px;background:rgba(254,226,226,.6);display:flex;align-items:center;padding:10px 12px">
      ${icon('filled/warning', 18, C.onErrorContainer)}<div style="width:10px;flex:none"></div><div style="flex:1;font:400 13px/18px Roboto;letter-spacing:.5px;color:${C.onErrorContainer}">${esc(s)}</div>
      ${icon('filled/keyboard_arrow_right', 18, C.onErrorContainer)}</div>`;
}

// Two halves of one pill (LayoutSelector / ShapeSelector): r10 track at onSurface 6 %, r8 halves.
function halves(options, selected, pad = 10) {
  return `<div style="display:flex;gap:3px;padding:3px;border-radius:10px;background:rgba(31,41,55,.06)">
      ${options.map(([target, label, id]) => `<div data-target="${target}" style="flex:1;border-radius:8px;padding:${pad}px 0;text-align:center;font:600 13px/1.172 Roboto;letter-spacing:.5px;
        background:${id === selected ? C.primary : 'transparent'};color:${id === selected ? C.onPrimary : C.onSurfaceVariant}">${esc(label)}</div>`).join('')}</div>`;
}

// --- Picture rows (PictureRow / PictureTile) -----------------------------------------------------------
const diag = (w, hh) => 90 + (Math.atan2(hh, w) * 180) / Math.PI;
const wallpaper = (w, hh) => `<div style="position:absolute;left:5px;top:5px;width:${w - 10}px;height:${hh - 10}px;border-radius:14px;background:linear-gradient(${diag(w - 10, hh - 10)}deg,#D9CCFF,#9DB2FA)"></div>`;

// The row opens scrolled to (selected - 1), never past its end; `scroll` (px) overrides that.
function rowOffset(n, selIndex, w, scroll) {
  const total = n * w + (n - 1) * 10;
  const max = Math.max(0, total - BODY_W);
  if (scroll != null) return clamp(scroll, 0, max);
  return Math.min(Math.max(0, selIndex - 1) * (w + 10), max);
}
function pictureRow(name, tiles, { w = 76, selected, scroll = null } = {}) {
  const sel = tiles.findIndex((x) => x.id === selected);
  const off = rowOffset(tiles.length, sel, w, scroll);
  const cells = tiles.map((x, i) => {
    const s = x.id === selected, left = i * (w + 10) - off;
    return `<div data-target="${x.target}" data-x="${left}" style="width:${w}px;flex:none">
        <div class="pic" style="position:relative;width:${w}px;height:${w + 12}px;border-radius:18px;background:${C.surfaceContainerHigh};overflow:hidden">
          ${x.picture}
          <div style="position:absolute;inset:0;border-radius:18px;box-shadow:inset 0 0 0 ${s ? 2 : 1}px ${s ? C.primary : 'rgba(229,231,235,.6)'};pointer-events:none"></div>
          ${s ? `<div style="position:absolute;right:6px;top:6px;width:18px;height:18px;border-radius:50%;background:${C.primary};display:grid;place-items:center">${icon('filled/check', 12, C.onPrimary)}</div>` : ''}
        </div>
        <div style="padding-top:6px;height:40px;text-align:center;font:${s ? 600 : 500} 12px/16px Roboto;letter-spacing:.5px;color:${s ? C.primary : C.onSurfaceVariant}">
          <div style="display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">${esc(x.label)}</div></div></div>`;
  });
  return `<div class="prow" data-row="${name}" style="position:relative;width:${BODY_W}px;overflow:hidden">
      <div style="display:flex;gap:10px;transform:translateX(${-off}px)">${cells.join('')}</div></div>`;
}
// An element that also prints as its HTML, so it can be appended or interpolated into a template.
function asElement(html) {
  const el = h(html);
  el.toString = () => el.outerHTML;
  return el;
}

// --- Panel styles (PanelThemeSelector) -----------------------------------------------------------------
let svgSeq = 0;
// ThemePicture: two bright shapes under a small panel in the material's menu palette, its blur drawn
// as soft washes, lit-edge materials with their sheen and rim. SVG in the 66 x 78 picture box.
export function themePicture(id) {
  const p = menuPalette(id), u = `pt${++svgSeq}`;
  const w = 66, hh = 78, pw = w * 0.66, ph = hh * 0.7, left = (w - pw) / 2, top = (hh - ph) / 2;
  const blobs = [[0.2, 0.22, 0.3, '#FF8A5B'], [0.84, 0.8, 0.34, '#26C6B0']];
  const k = PALE.has(id) ? 0.45 : 1, lit = LIT.has(id);
  const white = (a) => `rgba(255,255,255,${(a * k).toFixed(3)})`;
  const soft = p.blur * 0.45;
  const washes = p.blur > 0 ? blobs.map(([x, y, r, c], i) => {
    const [, rr, gg, bb] = argb(c);
    return `<radialGradient id="${u}w${i}" gradientUnits="userSpaceOnUse" cx="${x * w}" cy="${y * hh}" r="${r * w + soft}">
        <stop offset="0" stop-color="rgb(${rr},${gg},${bb})" stop-opacity=".95"/><stop offset=".45" stop-color="rgb(${rr},${gg},${bb})" stop-opacity=".6"/>
        <stop offset="1" stop-color="rgb(${rr},${gg},${bb})" stop-opacity="0"/></radialGradient>`;
  }).join('') : '';
  const onWithAlpha = (hex, a) => { const [, r, g, b] = argb(hex); return `rgba(${r},${g},${b},${a})`; };
  const rows = [0, 1, 2].map((row) => {
    const cy = top + ph * (0.26 + row * 0.24), cx = left + 7 + 4.5;
    const start = cx + 4.5 + 4, len = (left + pw - 6 - start) * (row === 1 ? 0.65 : 1);
    return `<circle cx="${cx}" cy="${cy}" r="4.5" fill="${rgba(p.chip)}"/><circle cx="${cx}" cy="${cy}" r="${4.5 * 0.42}" fill="${onWithAlpha(p.on, 0.9)}"/>
        <rect x="${start}" y="${cy - 1.75}" width="${len}" height="3.5" rx="1.75" fill="${rgba(row === 0 ? p.on : p.dim)}"/>`;
  }).join('');
  return `<svg width="${w}" height="${hh}" viewBox="0 0 ${w} ${hh}" style="position:absolute;left:5px;top:5px;overflow:hidden">
      <defs>
        <clipPath id="${u}b"><rect width="${w}" height="${hh}" rx="14"/></clipPath>
        <clipPath id="${u}p"><rect x="${left}" y="${top}" width="${pw}" height="${ph}" rx="10"/></clipPath>
        <linearGradient id="${u}g" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="${w}" y2="${hh}"><stop offset="0" stop-color="#D9CCFF"/><stop offset="1" stop-color="#9DB2FA"/></linearGradient>
        ${washes}
        <linearGradient id="${u}s" gradientUnits="userSpaceOnUse" x1="0" y1="${top}" x2="0" y2="${top + ph * 0.5}"><stop offset="0" stop-color="${white(0.18)}"/><stop offset=".55" stop-color="${white(0.03)}"/><stop offset="1" stop-color="rgba(255,255,255,0)"/></linearGradient>
        <linearGradient id="${u}c" gradientUnits="userSpaceOnUse" x1="0" y1="${top + ph * 0.8}" x2="0" y2="${top + ph}"><stop offset="0" stop-color="rgba(255,255,255,0)"/><stop offset="1" stop-color="${white(0.1)}"/></linearGradient>
        <linearGradient id="${u}r" gradientUnits="userSpaceOnUse" x1="0" y1="${top}" x2="0" y2="${top + ph}"><stop offset="0" stop-color="${white(0.65)}"/><stop offset=".35" stop-color="${white(0.24)}"/><stop offset=".75" stop-color="${white(0.08)}"/><stop offset="1" stop-color="${white(0.3)}"/></linearGradient>
      </defs>
      <g clip-path="url(#${u}b)">
        <rect width="${w}" height="${hh}" fill="url(#${u}g)"/>
        ${blobs.map(([x, y, r, c]) => `<circle cx="${x * w}" cy="${y * hh}" r="${r * w}" fill="${c}"/>`).join('')}
        <g clip-path="url(#${u}p)">
          ${p.blur > 0 ? `<rect width="${w}" height="${hh}" fill="url(#${u}g)"/>${blobs.map(([x, y, r], i) => `<circle cx="${x * w}" cy="${y * hh}" r="${r * w + soft}" fill="url(#${u}w${i})"/>`).join('')}` : ''}
          <rect width="${w}" height="${hh}" fill="${rgba(p.surface)}"/>
          ${lit ? `<rect width="${w}" height="${hh}" fill="url(#${u}s)"/><rect width="${w}" height="${hh}" fill="url(#${u}c)"/>` : ''}
          ${rows}
        </g>
        <rect x="${left}" y="${top}" width="${pw}" height="${ph}" rx="10" fill="none" stroke="${lit ? `url(#${u}r)` : rgba(p.border)}" stroke-width="${lit ? 1.2 : 1}"/>
      </g></svg>`;
}

// The ten panel styles as a row of 76 dp tiles, data-target="theme-<id>". `scroll` (px) overrides the
// row's opening position. Returns an element that also prints as HTML.
export function panelThemeRow({ selected = 'solid', scroll = null } = {}) {
  return asElement(themeRowHtml(selected, scroll));
}
const themeRowHtml = (selected, scroll) => pictureRow('theme', PANEL_THEMES.map((x) => ({
  id: x.id, label: x.label, target: `theme-${x.id}`, picture: themePicture(x.id),
})), { w: 76, selected, scroll });
const themeSelector = (selected, scroll) => `${heading15('Panel style')}${bodySmall('How the Deck, the Quick panel and the long-press menu are drawn. Gloss, Clear and Paper are pale with dark text on them. Frosted, Glass, Gloss, Clear, Acrylic and Amethyst blur the screen behind the panel, which needs Android 12 or newer; where the system will not blur, the panel stays translucent.', C.onSurfaceVariant, 'padding:4px 0 12px')}${themeRowHtml(selected, scroll)}`;

// --- Opening animations (PanelAnimationSelector) -----------------------------------------------------
// OpeningPicture: the bar at the right edge and a mini panel beside it that plays its entrance.
function animPicture(id) {
  const rows = [1, 0.7, 1, 0.7].map((f) => `<div style="height:6px;width:${f * 100}%;border-radius:3px;background:rgba(255,255,255,.26);flex:none"></div>`).join('');
  return `${wallpaper(76, 88)}
      <div style="position:absolute;right:5px;top:32px;width:4px;height:24px;background:#17171C;border-radius:2px 0 0 2px"></div>
      <div class="pa-panel" data-anim="${id}" style="position:absolute;right:13px;top:16px;width:34px;height:56px;border-radius:9px;background:rgba(29,27,43,.922);
        padding:7px 6px;display:flex;flex-direction:column;gap:5px;overflow:hidden">${rows}</div>`;
}
// The sixteen entrances as a row of 76 dp tiles, data-target="anim-<id>"; mountPanelRows plays them.
export function panelAnimationRow({ selected = 'slide', scroll = null } = {}) {
  return asElement(animRowHtml(selected, scroll));
}
const animRowHtml = (selected, scroll) => pictureRow('anim', PANEL_ANIMATIONS.map((x) => ({
  id: x.id, label: x.label, target: `anim-${x.id}`, picture: animPicture(x.id),
})), { w: 76, selected, scroll });
function animationSelector(selected, speed, scroll, dragging, desc = 'How the Deck and the long-press menu arrive. Tap one to watch it on the preview above; tap it again to watch it twice.') {
  return `${heading15('Opening animation')}${bodySmall(desc, C.onSurfaceVariant, 'padding:4px 0 12px')}${animRowHtml(selected, scroll)}${gap(14)}
      ${sliderAt('animation-speed', 'Animation speed', '', speed, 0.4, 2.5, dragging, `${speed.toFixed(1)}×`)}`;
}

// One frame of an entrance tile at t (s): the entrance 3x slower, travel x 0.35, on one 2400 ms loop
// with a 260 ms fade at its end; camera distance 10 x density (perspective ~720 dp).
export function applyEntranceTile(el, id, t) {
  const ms = (((t * 1000) % 2400) + 2400) % 2400;
  const f = entranceFrame(id, clamp(ms / ((ANIM[id]?.ms ?? 220) * 3)), false);
  const out = 1 - clamp((ms - (2400 - 260)) / 260);
  el.style.transformOrigin = `${f.ox * 100}% ${f.oy * 100}%`;
  el.style.transform = `perspective(720px) translate(${f.tx * 0.35}px,${f.ty * 0.35}px) rotateX(${f.rx}deg) rotateY(${f.ry}deg) rotate(${f.rz}deg) scale(${f.sx},${f.sy})`;
  el.style.opacity = f.alpha * out;
  el.style.clipPath = f.revealFrom > 0 || f.revealTo < 1 ? `inset(${f.revealFrom * 100}% 0 ${(1 - f.revealTo) * 100}% 0)` : '';
}

// Starts the rows in `el`: every opening-animation tile plays its entrance on the shared loop. The
// panel-style tiles are painted when built (SVG), so they need nothing per frame.
export function mountPanelRows(el) {
  const tiles = [...el.querySelectorAll('.pa-panel')];
  return ({ t = 0 } = {}) => { for (const p of tiles) applyEntranceTile(p, p.dataset.anim, t); };
}

// --- The preview stage (PreviewStage) ----------------------------------------------------------------------
// `natural`: the glass drawn at the phone's own size (the menu as a list), else at the glass's scale.
function stage(glassHtml, { natural = false } = {}) {
  const { g, svg } = M.scenePhoneSvg(STAGE_W, STAGE_H);
  const sc = natural ? 1 : g.scale;
  const box = { w: g.glass.w / sc, h: g.glass.h / sc };
  return `<div class="stage" data-target="preview" style="position:relative;width:${STAGE_W}px;height:${STAGE_H}px;overflow:hidden;${M.stageBackdrop(STAGE_W, STAGE_H)}">${svg}
      <div class="glass" style="position:absolute;left:${g.glass.x}px;top:${g.glass.y}px;width:${g.glass.w}px;height:${g.glass.h}px;overflow:hidden;border-radius:${g.glass.r}px ${g.glass.r}px 0 0">
        <div class="mini" style="position:absolute;left:0;top:0;width:${box.w}px;height:${box.h}px;transform-origin:0 0;transform:scale(${sc})">${glassHtml(box)}</div>
      </div></div>`;
}
function screenShell(title, status, pinnedHtml, desc, scrollHtml, scroll, stateAttr) {
  return M.screen(`<div class="ps-screen" data-ps='${stateAttr}' style="display:flex;flex-direction:column;height:100%">
      ${topBar(title, status)}
      <div class="pinned" style="padding:4px 16px 12px;flex:none">${pinnedHtml}${bodyMedium(desc, C.onSurfaceVariant, 'padding:10px 4px 0')}</div>
      <div class="scroll" style="position:relative;overflow:hidden;flex:1;min-height:0">
        <div class="col" style="padding:16px 16px 40px;transform:translateY(${-scroll}px)">${scrollHtml}</div></div></div>`);
}
const attr = (o) => esc(JSON.stringify(o)).replace(/'/g, '&#39;');

// --- The long-press menu card (ContextMenuCard) ---------------------------------------------------------
const GRID_LINES = ['none', 'horizontal', 'vertical', 'grid'], LIST_LINES = ['none', 'horizontal'];
const LINES_LABEL = { none: 'None', horizontal: 'Horizontal', vertical: 'Vertical', grid: 'Grid' };
const PER_PAGE = [0, 4, 6, 8, 9, 12];
const columnsFor = (width) => clamp(Math.floor((width - 16) / 64), 2, 5);
function linesFor(stored, grid) {
  if (!stored || !GRID_LINES.includes(stored)) return grid ? 'none' : 'horizontal';
  if (grid) return stored;
  return stored === 'grid' ? 'horizontal' : stored === 'vertical' ? 'none' : stored;
}
// HandlerActionCatalog.contextMenuEntries: the order given, Hide Handler added if missing.
const menuEntries = (items) => [...items.filter((id) => ENTRY[id]), ...PINNED.filter((p) => !items.includes(p))].map((id) => ENTRY[id]);

// The card at its own size: { html, w, h }. Grid tiles carry data-target="menu-<id>".
function menuCard({ items, layout, theme, width, height, lines, perPage, surface }) {
  const p = menuPalette(theme, surface);
  const grid = layout !== 'list';
  const entries = menuEntries(items);
  const ln = linesFor(lines, grid);
  const hair = rgba(p.divider);
  const chipHtml = (e) => `<div style="width:40px;height:40px;border-radius:14px;background:${rgba(p.chip)};display:grid;place-items:center;flex:none">${glyph(e.glyph, 21, rgba(p.on))}</div>`;
  let body = '', contentH = 0;
  if (grid) {
    const cols = columnsFor(width), tileW = (width - 16) / cols;
    const paged = perPage !== 0 && entries.length > perPage;
    const shown = paged ? entries.slice(0, perPage) : entries;
    const rows = [];
    for (let i = 0; i < shown.length; i += cols) rows.push(shown.slice(i, i + cols));
    const rowsPerPage = paged ? Math.ceil(perPage / cols) : 0;
    const across = ln === 'horizontal' || ln === 'grid', down = ln === 'vertical' || ln === 'grid';
    const tile = (e) => `<div data-target="menu-${e.id}" style="width:${tileW}px;height:88px;padding:8px 3px;display:flex;flex-direction:column;align-items:center;border-radius:16px;flex:none">
        ${chipHtml(e)}<div style="height:6px;flex:none"></div>
        <div style="max-width:100%;text-align:center;font:500 10px/12px Roboto;letter-spacing:.5px;color:${rgba(p.dim)};display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">${esc(e.label)}</div></div>`;
    for (let r = 0; r < Math.max(rows.length, rowsPerPage); r++) {
      const row = rows[r];
      if (!row) { body += `<div style="height:88px"></div>`; contentH += 88; continue; }
      if (r > 0 && across) body += `<div style="padding:0 6px"><div style="height:.4px;background:${hair}"></div></div>`;
      body += `<div style="display:flex;justify-content:center;align-items:center">${row.map((e, i) => `${i > 0 && down ? `<div style="width:.4px;height:68px;background:${hair};flex:none"></div>` : ''}${tile(e)}`).join('')}</div>`;
      contentH += 88;
    }
    if (paged) {
      const pages = Math.ceil(entries.length / perPage);
      body += `<div style="display:flex;justify-content:center;align-items:center;padding:6px 0 2px">${Array.from({ length: pages }, (_, i) => `<div style="margin:0 3px;width:${i ? 5 : 7}px;height:${i ? 5 : 7}px;border-radius:50%;background:${rgba(p.on)};opacity:${i ? 0.35 / (argb(p.on)[0] / 255) : 0.9 / (argb(p.on)[0] / 255)}"></div>`).join('')}</div>`;
      contentH += 15;
    }
  } else {
    entries.forEach((e, i) => {
      if (i > 0 && ln === 'horizontal') body += `<div style="padding:0 8px 0 60px"><div style="height:.4px;background:${hair}"></div></div>`;
      body += `<div data-target="menu-${e.id}" style="display:flex;align-items:center;padding:7px 8px;border-radius:14px">${chipHtml(e)}<div style="width:12px;flex:none"></div>
          <div style="flex:1;min-width:0;font:500 14px/24px Roboto;letter-spacing:.5px;color:${rgba(p.on)};margin:-3.8px 0;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">${esc(e.label)}</div></div>`;
      contentH += 54;
    });
  }
  const hgt = Math.min(contentH + 16, height);
  const edge = p.lit ? glassLayers(24, p.light) : `<div style="position:absolute;inset:0;border-radius:24px;box-shadow:inset 0 0 0 1px ${rgba(p.border)};pointer-events:none"></div>`;
  const html = `<div class="lp-card" data-target="menu-card" style="position:relative;width:${width}px;height:${hgt}px;border-radius:24px;background:${rgba(p.surface)};overflow:hidden;font-family:Roboto">
      ${p.lit ? edge : ''}<div style="position:relative;padding:8px">${body}</div>${p.lit ? '' : edge}</div>`;
  return { html, w: width, h: hgt };
}

// --- Long-press menu screen ------------------------------------------------------------------------------
// data-targets: back, help, preview (the stage), menu-card, menu-<id> (the card's tiles or rows);
//   section-size, section-items, section-colours, section-animation;
//   Size & shape: layout-grid, layout-list, slider-width(-minus/-plus), slider-height(-minus/-plus),
//     lines-none, lines-horizontal, lines-vertical, lines-grid, perpage-all, perpage-4/6/8/9/12;
//   Items: item-<id> (a row of "In the menu", and of "Available"), item-<id>-up, item-<id>-down,
//     item-<id>-remove (In the menu), item-<id>-add (Available), ids from MENU_CATALOG;
//   Colours: theme-<id>, own-colour (switch) / own-colour-row, colour, slider-opacity(-minus/-plus);
//   Animation: anim-<id>, slider-animation-speed(-minus/-plus).
// State: open — null for the app's default (Size & shape open), an id, a list, or { id: k };
//   items — the menu's ids in order; layout 'grid' | 'list'; theme; animation; width 180–360;
//   height 200–680; lines null | 'none' | 'horizontal' | 'vertical' | 'grid'; perPage 0 (All) | 4 |
//   6 | 8 | 9 | 12; ownColour 0..1 (switch); colour '#RRGGBB' (own colour, default the material's);
//   opacity 40–255; speed 0.4–2.5; rowScroll { theme, anim } (px); dragging: a slider name.
export function longPressMenuScreen({ status = 32, scroll = 0, open = null, items = DEFAULT_MENU_ITEMS, layout = 'grid', theme = 'solid',
  animation = 'slide', width = 238, height = 520, lines = null, perPage = 9, ownColour = 0, colour = null, opacity = 235, speed = 1,
  rowScroll = {}, dragging = null } = {}) {
  const grid = layout !== 'list';
  const own = on(ownColour);
  const baseHex = '#' + MENU_PAL[theme in MENU_PAL ? theme : 'solid'][0].slice(3);
  const ownHex = (colour || baseHex).slice(0, 7);
  const surface = own ? `#${Math.round(opacity).toString(16).padStart(2, '0')}${ownHex.slice(1)}` : null;
  const card = menuCard({ items, layout, theme, width, height, lines, perPage, surface });

  // The real card on the glass: a grid scaled to 0.8 (or smaller to fit) and centred under the status
  // bar; a list at its own size from 30 dp down, running off the bottom (scaleToFit).
  const pinned = stage((box) => {
    if (grid) {
      const s = Math.max(0.05, Math.min(0.8, (box.w - 24) / card.w, (box.h - 56) / card.h));
      const lw = card.w * s + 24, lh = card.h * s + 56;
      const x = (box.w - lw) / 2 + 12, y = (box.h - lh) / 2 + 44;
      return `<div class="lp-fit" style="position:absolute;left:${x}px;top:${y}px;width:${card.w}px;height:${card.h}px;transform-origin:0 0;transform:scale(${s})">${card.html}</div>`;
    }
    const s = Math.max(0.05, Math.min(1, (box.w - 24) / card.w));
    return `<div class="lp-fit" style="position:absolute;left:${(box.w - card.w * s) / 2}px;top:30px;width:${card.w}px;height:${card.h}px;transform-origin:0 0;transform:scale(${s})">${card.html}</div>`;
  }, { natural: !grid });

  const k = (id) => openK(open, id, 'size');
  const linesSel = linesFor(lines, grid);
  const sizeBody = [
    sectionLabel('Layout'),
    halves([['layout-grid', 'Grid', 'grid'], ['layout-list', 'List', 'list']], grid ? 'grid' : 'list'),
    sep(),
    sliderAt('width', 'Width', `${Math.trunc(width)}dp`, width, 180, 360, dragging),
    grid ? bodySmall(`${columnsFor(width)} columns at this width`, C.onSurfaceVariant, 'padding-top:4px') : '',
    gap(12),
    sliderAt('height', 'Height', `${Math.trunc(height)}dp`, height, 200, 680, dragging),
    bodySmall('The tallest the menu grows. Anything longer scrolls inside it, or turns the page.', C.onSurfaceVariant, 'padding-top:4px'),
    sep(),
    sectionLabel('Lines'),
    chipRow((grid ? GRID_LINES : LIST_LINES).map((l) => chip(`lines-${l}`, LINES_LABEL[l], l === linesSel))),
    grid ? `${sep()}${sectionLabel('Items per page')}${chipRow(PER_PAGE.map((n) => chip(`perpage-${n || 'all'}`, n ? String(n) : 'All', n === perPage)))}
      ${bodySmall('With more than this, the grid becomes pages you swipe between.', C.onSurfaceVariant, 'padding-top:8px')}` : '',
  ].join('');

  // In the menu: the stored order with Hide Handler seeded in; Available: every other candidate.
  const shown = [...items.filter((id) => ENTRY[id]), ...PINNED.filter((p) => !items.includes(p))];
  const available = MENU_CATALOG.filter((e) => !shown.includes(e.id));
  const iconBtn = (target, g, size, color) => `<div data-target="${target}" style="width:36px;height:36px;display:grid;place-items:center;flex:none">${icon(g, size, color)}</div>`;
  const dimmed = 'rgba(17,24,39,.38)';
  const shownRows = shown.map((id, i) => {
    const e = ENTRY[id], pinnedRow = PINNED.includes(id);
    return `<div data-target="item-${id}" style="display:flex;align-items:center;padding:3px 0">
        <div style="width:20px;flex:none;font:700 12px/1.172 Roboto;letter-spacing:.5px;color:rgba(75,85,99,.5)">${i + 1}</div>
        <div style="width:34px;height:34px;border-radius:11px;background:rgba(79,70,229,.10);display:grid;place-items:center;flex:none">${glyph(e.glyph, 18, C.primary)}</div>
        <div style="width:12px;flex:none"></div>
        <div style="flex:1;min-width:0">${bodyMedium(e.label, ON_BG, 'white-space:nowrap;overflow:hidden;text-overflow:ellipsis')}${pinnedRow ? bodySmall('Always shown') : ''}</div>
        ${iconBtn(`item-${id}-up`, 'filled/keyboard_arrow_up', 22, i > 0 ? ON_BG : dimmed)}
        ${iconBtn(`item-${id}-down`, 'filled/keyboard_arrow_down', 22, i < shown.length - 1 ? ON_BG : dimmed)}
        ${iconBtn(`item-${id}-remove`, 'filled/close', 18, pinnedRow ? C.outlineVariant : C.error)}</div>`;
  }).join('');
  const hiddenRows = available.map((e) => `<div data-target="item-${e.id}" style="display:flex;align-items:center;padding:10px 2px;border-radius:10px">
        <div style="width:34px;height:34px;border-radius:11px;background:rgba(31,41,55,.05);display:grid;place-items:center;flex:none">${glyph(e.glyph, 18, C.onSurfaceVariant)}</div>
        <div style="width:12px;flex:none"></div>
        ${bodyMedium(e.label, C.onSurfaceVariant, 'flex:1;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis')}
        <div data-target="item-${e.id}-add" style="flex:none">${icon('filled/add', 18, C.primary)}</div></div>`).join('');
  const itemsBody = `${sectionLabel('In the menu')}${shown.length ? '' : bodySmall('Nothing else selected. Press and hold will move the handler and offer Hide handler.')}${shownRows}
      ${available.length ? `${sep()}${sectionLabel('Available')}${hiddenRows}` : ''}`;

  const coloursBody = `${themeSelector(theme, rowScroll.theme)}${sep()}
      ${switchItem('own-colour', 'Use my own colour', 'Off, the menu takes its colours from the panel style. On, it takes them from the colour below, and its text and dividers follow whether that colour is light or dark.', ownColour)}
      ${own ? `${gap(12)}${colorRow('colour', 'Color', ownHex)}${gap(12)}${sliderAt('opacity', 'Opacity', `${Math.trunc((opacity / 255) * 100)}%`, opacity, 40, 255, dragging)}` : ''}`;
  const animationBody = animationSelector(animation, speed, rowScroll.anim, dragging);

  const n = menuEntries(items).length;
  const summaryColours = own ? `${THEME_LABEL[theme]} · ${Math.trunc((opacity / 255) * 100)}%` : THEME_LABEL[theme];
  const sections = [
    section('size', 'filled/aspect_ratio', 'Size & shape', `${Math.trunc(width)} × ${Math.trunc(height)}dp`, k('size'), sizeBody),
    section('items', 'filled/list', 'Items', `${n} selected`, k('items'), itemsBody),
    section('colours', 'filled/palette', 'Colours', summaryColours, k('colours'), coloursBody),
    section('animation', 'filled/animation', 'Animation', `${ANIM[animation]?.label ?? 'Slide'} · ${speed.toFixed(1)}×`, k('animation'), animationBody),
  ].join(gap(12)) + gap(28);
  return screenShell('Long-press menu', status, pinned, 'Choose what appears when you press and hold the handler.', sections, scroll,
    attr({ screen: 'menu', animation, speed }));
}

// The menu's items are chosen in the Items section of the same screen (the app has no separate
// chooser): that screen with Items open and the rest closed. `selected` is the menu's ids in order.
export function menuItemsScreen({ selected = DEFAULT_MENU_ITEMS, ...rest } = {}) {
  return longPressMenuScreen({ open: 'items', ...rest, items: selected });
}

// Starts a Long-press menu screen: the opening-animation tiles, and the preview card's entrance when
// one is replayed. draw({ t, entrance }) — entrance: the card's entrance progress 0..1 (null: at
// rest), or entranceAt: the time (s) it was picked, played at the screen's animation and speed;
// pop: the demo's pop-in 0..1 (alpha, scale 0.82 → 1).
export function mountLongPressMenu(el, opts = {}) {
  let root, rows, card, st;
  const setup = () => {
    root = el.matches?.('.ps-screen') ? el : el.querySelector('.ps-screen') || el;
    st = { ...JSON.parse(root.dataset?.ps || '{}'), ...opts };
    rows = mountPanelRows(root);
    card = root.querySelector('.lp-card');
  };
  setup();
  return ({ t = 0, entrance = null, entranceAt = null, pop = null } = {}) => {
    if (!root.isConnected && el.isConnected) setup();
    rows({ t });
    if (!card) return;
    let p = entrance;
    if (p == null && entranceAt != null) p = clamp((t - entranceAt) / entranceSeconds(st.animation || 'slide', st.speed || 1));
    const grow = pop == null ? 1 : 0.82 + 0.18 * pop;
    if (p == null || p >= 1) {
      card.style.transform = grow === 1 ? '' : `scale(${grow})`;
      card.style.opacity = pop == null ? '' : clamp(pop);
      card.style.clipPath = '';
      return;
    }
    const f = entranceFrame(st.animation || 'slide', p, false);
    card.style.transformOrigin = `${f.ox * 100}% ${f.oy * 100}%`;
    card.style.transform = `perspective(1296px) translate(${f.tx}px,${f.ty}px) rotateX(${f.rx}deg) rotateY(${f.ry}deg) rotate(${f.rz}deg) scale(${f.sx * grow},${f.sy * grow})`;
    card.style.opacity = f.alpha * (pop == null ? 1 : clamp(pop));
    card.style.clipPath = f.revealFrom > 0 || f.revealTo < 1 ? `inset(${f.revealFrom * 100}% 0 ${(1 - f.revealTo) * 100}% 0)` : '';
  };
}

// --- Quick slider screen --------------------------------------------------------------------------------
// A fill as the screens store it: { kind, ...its own settings } (kind: a FILLS id; shader: effect,
// speed, scale, detail, bright, grain, rest; surge: look, speed, size, edge, glow, trail, rest;
// pixels: pattern, speed, columns, gap, roundness, glow, rest; effort: look, speed, labels;
// glimmer: look ('both' | 'handle' | 'stops' | 'plain'), speed).
const FILL_DEFAULTS = {
  shader: { effect: 'lava-lamp', speed: 1, scale: 1, detail: 0.5, bright: 1, grain: 0.15, rest: 0.15 },
  surge: { look: 'curve', speed: 1, size: 1, edge: 0.5, glow: 0.6, trail: 0.5, rest: 0.1 },
  pixels: { pattern: 'spectrum', speed: 1, columns: 4, gap: 0.18, roundness: 0.35, glow: 0.6, rest: 0.1 },
  effort: { look: 'steps', speed: 1, labels: true },
  glimmer: { look: 'both', speed: 1 },
};
export function normalizeFill(fill) {
  const f = typeof fill === 'string' ? { kind: fill } : { ...(fill || { kind: 'solid' }) };
  if (f.kind === 'shaders') f.kind = 'shader';
  Object.assign(f, { ...FILL_DEFAULTS[f.kind], ...f });
  if (f.kind === 'glimmer') { const l = GLIMMER_LOOKS.find((x) => x.id === f.look) || GLIMMER_LOOKS[0]; f.handle = l.handle; f.stops = l.stops; }
  if (f.kind === 'surge') f.scale = f.size;
  return f;
}
// The style a tile of fill `kind` shows: the user's own when it is the chosen one, else its defaults.
const fillFor = (kind, chosen) => normalizeFill(chosen.kind === kind ? chosen : { kind });

// The three Animation colours the store starts with (QuickSliderStore's defaults, as the pickers show them).
const ANIM_COLOURS = ['#4FE3FF', '#A77CFF', '#FF5CA8'];

// A live tile: 24 x 70 panel centred in a 68 dp picture (data read by mountQuickSliderSettings).
function liveTile(fill, { level = 0.62, showcase = false, max = null } = {}) {
  return `${wallpaper(68, 80)}<div class="ps-tile" data-fill='${attr(fill)}' data-level="${level}" data-showcase="${showcase ? 1 : 0}"${max ? ` data-max="${max}"` : ''}
      style="position:absolute;left:22px;top:5px;width:24px;height:70px"></div>`;
}
function tileRow(name, list, selected, scroll) {
  return pictureRow(name, list, { w: 68, selected, scroll });
}

// data-targets: back, help, preview (the stage), panel (the preview's panel);
//   section-content, section-size, section-colours, section-animation, section-behaviour;
//   Content: target-brightness / -adaptive / -media / -ring / -alarm, show-value, show-icon (+ -row),
//     icon-picker, permission-brightness;
//   Size & shape: slider-length, slider-thickness, slider-edge-distance, match-shape, slider-end-sweep,
//     shape-rounded, shape-tab, slider-top-left / -top-right / -bottom-left / -bottom-right,
//     slider-number-top-padding, slider-icon-bottom-padding (each slider also -minus / -plus);
//   Colours: theme-<id>, track-colour, fill-colour, own-colours, value-colour, icon-colour;
//   Animation: anim-<id>, slider-animation-speed, fill-<id> (FILLS), then for the chosen fill:
//     pattern-<id> + slider-speed / -pixels-across / -gap / -roundness / -glow / -unlit-pixels (Pixels),
//     effect-<id> + slider-speed / -scale / -<detail> / -brightness / -grain / -above-the-level (Shaders),
//     look-<id> + slider-speed / -size / -edge / -glow / -trail / -above-the-level (Surge),
//     look-steps / look-dots + slider-speed, level-names (Effort), look-both / -handle / -stops / -plain
//     + slider-speed (Glimmer); slider-feedback-speed, livelier, glow-low, celebrate, flourish-<id>,
//     anim-colours, colour-1..3;
//   Behaviour: opener-in / -out / -both / -off, haptics-off / -light / -medium / -strong,
//     sound-off / -click / -adaptive, keys-off / -follow / -instant, permission-keys,
//     auto-brightness, icon-opens-panel (switches also -row).
// State: open — 'content' | 'size' | 'colours' | 'animation' | 'behaviour', a list, or { id: k };
//   target; fill (see normalizeFill); flourish; celebrate, showValue, showIcon, matchShape,
//   ownColours, livelier, glowLow, animColours, autoBrightness, iconOpensPanel: 0..1 switches;
//   volumeKeys 'off' | 'follow' | 'instant'; opener; haptics; sound; theme; animation; speed;
//   length, thickness, edge (dp); sweep (%); panelShape 'rounded' | 'tab' and corners (match off);
//   valueMargin, iconMargin (dp); feedbackSpeed; accessibility, canWriteSettings (permission notes);
//   animPalette: the three Animation colours ('#RRGGBB', default cyan / violet / rose), which recolour
//   the preview and the tiles while animColours is on;
//   rowScroll { theme, anim, fill, pattern, effect, look, flourish } (px); dragging: a slider name.
export function quickSliderSettings({ status = 32, scroll = 0, open = 'content', target = 'adaptive', fill = { kind: 'shader', effect: 'aurora' },
  flourish = 'burst', celebrate = 1, volumeKeys = 'instant', theme = 'solid', showValue = 1, showIcon = 1, length = 220, thickness = 32,
  edge = 0, matchShape = 1, sweep = 22, panelShape = 'rounded', corners = [22, 22, 22, 22], valueMargin = 35, iconMargin = 35,
  ownColours = 0, animation = 'slide', speed = 1, livelier = 1, glowLow = 1, feedbackSpeed = 1, animColours = 0, opener = 'off',
  haptics = 'light', sound = 'off', autoBrightness = 1, iconOpensPanel = 1, accessibility = true, canWriteSettings = true,
  animPalette = ANIM_COLOURS, rowScroll = {}, dragging = null } = {}) {
  const f = normalizeFill(fill);
  const keys = volumeKeys === 'system' ? 'follow' : volumeKeys;
  const k = (id) => openK(open, id, 'content');
  const feedback = { follow: on(livelier), low: on(glowLow), full: on(celebrate), max: flourish, speed: feedbackSpeed };

  // The preview: the real panel against the glass's right edge, between the status bar and as far below
  // the middle (44 dp each), at the window's 48 dp floor.
  const pinned = stage((box) => {
    const ph = Math.min(length, box.h - 88), winW = Math.max(thickness, 48);
    return `<div class="ps-panel" data-target="panel" data-h="${ph}" data-w="${winW}" style="position:absolute;right:${edge}px;top:${44 + (box.h - 88 - ph) / 2}px;width:${winW}px;height:${ph}px"></div>`;
  });

  // --- Content
  const contentBody = [
    label15('Adjusts'),
    chipRow(TARGETS.map(([id, l]) => chip(`target-${id}`, l, id === target)), 'padding-bottom:8px'),
    target === 'adaptive' ? bodySmall('Call volume during a call, the ringer while it rings, otherwise whatever is playing. Media when nothing is.') : '',
    target === 'brightness' && !canWriteSettings ? permissionNote('Needs Modify system settings to change the brightness. Tap to allow it on the Permissions screen.', 'permission-brightness') : '',
    sep(), switchItem('show-value', 'Show the value', 'The percentage, printed inside the track.', showValue),
    sep(), switchItem('show-icon', 'Show an icon', 'A small mark at the foot of the track for what it adjusts.', showIcon),
    on(showIcon) ? `${gap(12)}${tx('Icon', 13, 500, 'rgba(31,41,55,.6)', 'padding-bottom:8px')}
      <div data-target="icon-picker" style="border-radius:12px;box-shadow:inset 0 0 0 .5px rgba(79,70,229,.1);padding:14px;display:flex;align-items:center;justify-content:space-between">
        <div style="display:flex;align-items:center">${drawableIcon(target === 'brightness' ? 'ic_brightness_up' : 'ic_vol_increase', 24, C.primary)}<div style="width:12px"></div>${tx('Automatic', 14, 500, C.onSurface)}</div>
        ${icon('filled/edit', 20, C.outline)}</div>` : '',
  ].join('');

  // --- Size & shape
  const sizeBody = [
    sliderAt('length', 'Length', `${Math.trunc(length)}dp`, length, 120, 320, dragging),
    hint('Also the travel: running your finger the length of the track covers the whole range.'),
    sep(), sliderAt('thickness', 'Thickness', `${Math.trunc(thickness)}dp`, thickness, 10, 72, dragging),
    sep(), sliderAt('edge-distance', 'Edge distance', `${Math.trunc(edge)}dp`, edge, 0, 48, dragging),
    hint('Stands the open panel in from the screen edge, clear of a curved edge or the back gesture. It still grows out of the bar.'),
    sep(), switchItem('match-shape', 'Match the bar\'s shape', 'On, the panel opens into the bar\'s own outline, scaled up to the panel\'s width. Off, you set its corners and shape yourself.', matchShape),
    on(matchShape) ? `${gap(12)}${sliderAt('end-sweep', 'End sweep', `${Math.trunc(sweep)}%`, sweep, 4, 50, dragging)}` : [
      sep(), halves([['shape-rounded', 'Rounded', 'rounded'], ['shape-tab', 'Tab', 'tab']], panelShape === 'tab' ? 'tab' : 'rounded', 9),
      bodySmall(panelShape === 'tab' ? 'Both ends sweep back into the side of the screen. Keep the bar flush to the edge, or the curves have nothing to meet.' : 'A rectangle with corners you set below.', C.onSurfaceVariant, 'padding-top:8px'),
      gap(12),
      panelShape === 'tab' ? sliderAt('end-sweep', 'End sweep', `${Math.trunc(sweep)}%`, sweep, 4, 50, dragging)
        : [['top-left', 'Top Left', 0], ['top-right', 'Top Right', 1], ['bottom-left', 'Bottom Left', 2], ['bottom-right', 'Bottom Right', 3]]
          .map(([n, l, i]) => sliderAt(n, l, `${Math.trunc(corners[i])}dp`, corners[i], 0, 60, dragging)).join(''),
    ].join(''),
    on(showValue) ? `${sep()}${sliderAt('number-top-padding', 'Number top padding', `${Math.trunc(valueMargin)}dp`, valueMargin, 0, 140, dragging)}` : '',
    on(showIcon) ? `${sep()}${sliderAt('icon-bottom-padding', 'Icon bottom padding', `${Math.trunc(iconMargin)}dp`, iconMargin, 0, 140, dragging)}` : '',
  ].join('');

  // --- Colours
  const coloursBody = [
    themeSelector(theme, rowScroll.theme),
    sep(), colorRow('track-colour', 'Track colour', '#1C1C20'),
    sep(), colorRow('fill-colour', 'Fill colour', '#FFFFFF'),
    sep(), switchItem('own-colours', 'Own icon & value colours', 'Off, the icon and the number change colour on their own, light or dark, to stand out from the panel and the animation under them.', ownColours),
    on(ownColours) ? `${gap(12)}${colorRow('value-colour', 'Value colour', '#FFFFFF')}${gap(12)}${colorRow('icon-colour', 'Icon colour', '#FFFFFF')}` : '',
  ].join('');

  // --- Animation
  const tile = (fl, opts) => liveTile({ ...fl, feedback: opts?.feedback ?? feedback }, opts);
  const fillRow = tileRow('fill', FILLS.map((x) => ({ id: x.id, label: x.label, target: `fill-${x.id}`, picture: tile(fillFor(x.id, f)) })), f.kind, rowScroll.fill);
  const fillSelector = `${heading15('Fill animation')}${bodySmall('What the filled part of the track does while the panel is open. The preview above runs it.', C.onSurfaceVariant, 'padding:4px 0 12px')}${fillRow}`;
  const optionRow = (title, row) => `${heading15(title, 'padding-bottom:10px')}${row}`;
  const speedText = (v, min) => `${fmtTimes(v, min)}×`;
  let fillBlock = '';
  if (f.kind === 'pixels') {
    const row = tileRow('pattern', PIXEL_PATTERNS.map((id) => ({ id, label: PATTERN_LABEL[id], target: `pattern-${id}`, picture: tile({ ...f, pattern: id }) })), f.pattern, rowScroll.pattern);
    fillBlock = [sep(), bodySmall('A grid of lights, lit up to the level. Pick a pattern and tune the grid; the preview above shows it as you go.', C.onSurfaceVariant, 'padding-bottom:10px'),
      optionRow('Pattern', row), gap(16),
      sliderAt('speed', 'Speed', speedText(f.speed), f.speed, 0.25, 3, dragging), gap(12),
      sliderAt('pixels-across', 'Pixels across', String(f.columns), f.columns, 2, 6, dragging), gap(12),
      sliderAt('gap', 'Gap', pct(f.gap), f.gap, 0, 0.45, dragging), gap(12),
      sliderAt('roundness', 'Roundness', pct(f.roundness), f.roundness, 0, 1, dragging), gap(12),
      sliderAt('glow', 'Glow', pct(f.glow), f.glow, 0, 1, dragging), gap(12),
      sliderAt('unlit-pixels', 'Unlit pixels', pct(f.rest), f.rest, 0, 0.4, dragging)].join('');
  } else if (f.kind === 'shader') {
    const row = tileRow('effect', SHADER_EFFECTS.map((x) => ({ id: x.id, label: x.label, target: `effect-${x.id}`, picture: tile({ ...f, effect: x.id }) })), f.effect, rowScroll.effect);
    const detail = SHADER_EFFECTS.find((x) => x.id === f.effect)?.detail ?? 'Blobs';
    fillBlock = [sep(), bodySmall('Effects drawn live by your phone\'s graphics chip, lit up to the level and faint above it. Pick one and tune it; the preview above shows it as you go.', C.onSurfaceVariant, 'padding-bottom:10px'),
      optionRow('Effect', row), gap(16),
      sliderAt('speed', 'Speed', f.speed <= 0 ? 'Still' : speedText(f.speed, 1), f.speed, 0, 3, dragging), gap(12),
      sliderAt('scale', 'Scale', speedText(f.scale, 1), f.scale, 0.5, 2.5, dragging), gap(12),
      sliderAt(detail.toLowerCase(), detail, pct(f.detail), f.detail, 0, 1, dragging), gap(12),
      sliderAt('brightness', 'Brightness', pct(f.bright), f.bright, 0.5, 1.5, dragging), gap(12),
      sliderAt('grain', 'Grain', pct(f.grain), f.grain, 0, 1, dragging), gap(12),
      sliderAt('above-the-level', 'Above the level', pct(f.rest), f.rest, 0, 0.4, dragging)].join('');
  } else if (f.kind === 'surge') {
    const row = tileRow('look', SURGE_LOOKS.map((x) => ({ id: x.id, label: x.label, target: `look-${x.id}`, picture: tile({ ...f, look: x.id }) })), f.look, rowScroll.look);
    const sHint = (s) => hint(s, 'padding:4px 0 6px');
    fillBlock = [sep(), sHint('The level as a glowing front, the light running back from it into the dark, drawn live by your phone\'s graphics chip. Pick a look and tune it; the preview above shows it as you go.'),
      optionRow('Look', row), gap(16),
      sliderAt('speed', 'Speed', f.speed <= 0 ? 'Still' : speedText(f.speed, 1), f.speed, 0, 3, dragging), gap(12),
      sliderAt('size', 'Size', speedText(f.size, 1), f.size, 0.5, 2, dragging),
      sHint('Above 1, more and smaller: more streaks and blocks, smaller cells, a busier edge.'), gap(12),
      sliderAt('edge', 'Edge', pct(f.edge), f.edge, 0, 1, dragging),
      sHint('How far the front strays from the level: from a straight line to deep curves, tongues and steps.'), gap(12),
      sliderAt('glow', 'Glow', pct(f.glow), f.glow, 0, 1, dragging), gap(12),
      sliderAt('trail', 'Trail', pct(f.trail), f.trail, 0, 1, dragging),
      sHint('How far the light reaches back from the front before it fades into the dark.'), gap(12),
      sliderAt('above-the-level', 'Above the level', pct(f.rest), f.rest, 0, 0.4, dragging)].join('');
  } else if (f.kind === 'effort') {
    const row = tileRow('look', EFFORT_LOOKS.map((x) => ({ id: x.id, label: x.label, target: `look-${x.id}`, picture: tile({ ...f, look: x.id }) })), f.look, rowScroll.look);
    fillBlock = [sep(), bodySmall('The track as an effort picker, like the ones AI coding tools show: it lights up through Low, High, Extra high, Max and Ultra max as the level rises, and works harder the higher it goes. The preview above runs it.', C.onSurfaceVariant, 'padding-bottom:10px'),
      optionRow('Look', row),
      bodySmall(f.look === 'dots' ? 'One bar lit to the level, the level counted in dots beside its name.' : 'A stop for each level, stacked up the track like a slider\'s stops, lit to the one you are on.', C.onSurfaceVariant, 'padding-top:8px'),
      gap(16), sliderAt('speed', 'Speed', speedText(f.speed), f.speed, 0.25, 3, dragging), gap(12),
      switchItem('level-names', 'Level names', 'Name the level on the track as well as lighting it.', f.labels === false ? 0 : (f.labels === true ? 1 : f.labels))].join('');
  } else if (f.kind === 'glimmer') {
    const row = tileRow('look', GLIMMER_LOOKS.map((x) => ({ id: x.id, label: x.label, target: `look-${x.id}`, picture: tile({ ...f, look: x.id, handle: x.handle, stops: x.stops }) })), f.look, rowScroll.look);
    fillBlock = [sep(), bodySmall('Fine dots that brighten from nothing into a glimmer as they near the level, twinkling as they go, after the dotted effort slider in AI coding tools. The preview above runs it.', C.onSurfaceVariant, 'padding-bottom:10px'),
      optionRow('Look', row), gap(16), sliderAt('speed', 'Speed', speedText(f.speed), f.speed, 0.25, 3, dragging)].join('');
  }
  const ownSpeed = ['pixels', 'shader', 'surge', 'glimmer'].includes(f.kind);
  const flourishRow = tileRow('flourish', FLOURISHES.map((id) => ({ id, label: FLOURISH_LABEL[id], target: `flourish-${id}`,
    picture: tile(f, { level: 1, showcase: true, max: id, feedback: { ...feedback, full: true, max: id } }) })), flourish, rowScroll.flourish);
  const feedbackBlock = f.kind === 'effort' ? '' : [sep(), heading15('Level feedback'),
    bodySmall('How the animation answers the level: calm when it is low, livelier as it rises, and a flourish at the top.', C.onSurfaceVariant, 'padding:4px 0 12px'),
    ownSpeed ? '' : `${sliderAt('feedback-speed', 'Speed', speedText(feedbackSpeed), feedbackSpeed, 0.25, 3, dragging)}${gap(12)}`,
    switchItem('livelier', 'Livelier as it fills', 'Moves faster and shines brighter the higher the level, with a flash as it passes each fifth.', livelier), gap(12),
    switchItem('glow-low', 'Glow when low', 'A soft light breathes at the level when it is low, and at the bottom when it is off.', glowLow), gap(12),
    switchItem('celebrate', 'Celebrate at the top', 'A flourish when it reaches 100%, and while it stays there. Pick one below.', celebrate),
    on(celebrate) ? `${gap(16)}${optionRow('At the top', flourishRow)}` : ''].join('');
  const colourSupport = !['solid', 'tide-up', 'tide-down', 'stripes'].includes(f.kind) && !(f.kind === 'pixels' && ['steady', 'breathe', 'sweep', 'pendulum', 'snake', 'twinkle', 'checkers'].includes(f.pattern));
  const animColoursBlock = on(animColours) ? (colourSupport
    ? animPalette.map((hex, i) => `${gap(12)}${colorRow(`colour-${i + 1}`, `Colour ${i + 1}`, hex)}`).join('')
    : hint(f.kind === 'pixels' ? 'This pattern is drawn in the fill colour. The colourful ones — Rain, Meter, Spectrum and the rest — take these colours.' : 'This animation is drawn in the fill colour, so it has no colours of its own to change.')) : '';
  const animationBody = [
    animationSelector(animation, speed, rowScroll.anim, dragging), sep(), fillSelector, fillBlock, feedbackBlock, gap(12),
    switchItem('anim-colours', 'Animation colours', 'Paint the animation in three colours of your own instead of its own.', animColours), animColoursBlock,
  ].join('');

  // --- Behaviour
  const chips = (prefix, list, sel) => chipRow(list.map(([id, l]) => chip(`${prefix}-${id}`, l, id === sel)), 'padding-bottom:8px');
  const behaviourBody = [
    label15('How it opens'), hint('Swiping the bar up or down opens the panel — set that in Actions. This is an extra opener for a long horizontal swipe, which the Deck also uses, so it is off unless you want it.'),
    chips('opener', OPENERS, opener), sep(),
    label15('Haptics'), hint('One buzz per step, so you can feel the value change without looking.'), chips('haptics', HAPTICS, haptics), sep(),
    label15('Step sound'), hint('A soft tick on every step as you swipe or drag. Adaptive rises in pitch with the level and, on a volume panel, plays at the volume you are setting.'),
    chips('sound', SOUNDS, sound), sep(),
    label15('Volume keys'), hint('What pressing a volume key does to the panel. It opens on the media volume, wherever you are.'), chips('keys', KEYS, keys),
    bodySmall(KEYS_HINT[keys] || KEYS_HINT.instant),
    keys === 'instant' && !accessibility ? permissionNote('Instant needs GestureVolume\'s accessibility service, which is handed the key before the system acts on it. Until it is on, the panel opens with the system. Tap to turn it on.', 'permission-keys') : '',
    sep(), switchItem('auto-brightness', 'Turn off adaptive brightness', 'While the brightness slider is open. Without this the light sensor undoes what you set within a second or two. It is handed back when the service stops.', autoBrightness),
    on(showIcon) && target !== 'brightness' ? `${sep()}${switchItem('icon-opens-panel', 'Icon opens the volume panel', 'Tap the icon for the system\'s full volume panel, with media, call, ring and alarm volumes on one sheet.', iconOpensPanel)}` : '',
  ].join('');

  const sections = [
    section('content', 'filled/widgets', 'Content', TARGETS.find((x) => x[0] === target)?.[1] ?? 'Adaptive volume', k('content'), contentBody),
    section('size', 'filled/aspect_ratio', 'Size & shape', `${Math.trunc(length)} × ${Math.trunc(thickness)}dp`, k('size'), sizeBody),
    section('colours', 'filled/palette', 'Colours', THEME_LABEL[theme] ?? 'Solid', k('colours'), coloursBody),
    section('animation', 'filled/animation', 'Animation', `${ANIM[animation]?.label ?? 'Slide'} · ${FILL_LABEL[f.kind] ?? 'Solid'}`, k('animation'), animationBody),
    section('behaviour', 'filled/tune', 'Behaviour', `${OPENERS.find((x) => x[0] === opener)?.[1] ?? 'Off'} · ${KEYS.find((x) => x[0] === keys)?.[1] ?? 'Instant'}`, k('behaviour'), behaviourBody),
  ].join(gap(12)) + gap(24);
  const state = { screen: 'quick', target, fill: f, theme, showValue: on(showValue), showIcon: on(showIcon), length, thickness, edge,
    matchShape: on(matchShape), sweep, panelShape, corners, valueMargin, iconMargin, animation, speed, feedback,
    // Animation colours on: the preview and every tile take them, each fill as far as it can.
    custom: on(animColours) ? animPalette : null };
  return screenShell('Quick slider', status, pinned, 'Swipe the bar up or down and the panel opens beside it. It stays there — touch the track to set the value, and it closes itself.',
    sections, scroll, attr(state));
}

// --- The preview's level and clocks (SliderPreview) ---------------------------------------------------
// The value steps 0.05 → 0.3 → 0.5 → 0.7 → 1 → 0.7 → 0.5 → 0.3 → 0.05 …, holding 1300 ms and moving
// 520 ms (FastOutSlowIn), from t = 0.
const STOPS = [0.05, 0.3, 0.5, 0.7, 1, 0.7, 0.5, 0.3];
const HOLD = 1.3, MOVE = 0.52, STEP = HOLD + MOVE;
export function previewValue(t) {
  if (t <= 0) return STOPS[0];
  const k = Math.floor(t / STEP), w = t - k * STEP;
  const from = STOPS[k % 8], to = STOPS[(k + 1) % 8];
  return w < HOLD ? from : lerp(from, to, Ease.fastOutSlowIn((w - HOLD) / MOVE));
}
// ∫ ease over a move, for the clocks that run at the level's pace (pace is linear in the value).
const EASE_MEAN = (() => { let s = 0; const n = 400; for (let i = 0; i < n; i++) s += Ease.fastOutSlowIn((i + 0.5) / n); return s / n; })();
// ∫0..t pace(value) dτ for the preview's cycle: seconds of a clock that runs at the level's pace.
function pacedSeconds(t, follow) {
  if (!follow) return t;
  let acc = 0, at = 0, k = 0;
  while (at < t) {
    const from = STOPS[k % 8], to = STOPS[(k + 1) % 8];
    const hold = Math.min(HOLD, t - at);
    acc += hold * pace(from);
    at += HOLD;
    if (at >= t) break;
    const mv = Math.min(MOVE, t - at);
    if (mv >= MOVE) acc += MOVE * pace(from + (to - from) * EASE_MEAN);
    else { const n = 24; for (let i = 0; i < n; i++) acc += (mv / n) * pace(previewValue(at + (i + 0.5) * mv / n)); }
    at += MOVE;
    k++;
  }
  return acc;
}
// Seconds since the level last moved into another fifth (-1: not since it opened), and since it last
// reached the top (-1: not full), looking back as far as either can still show.
function sinceStep(t, valueAt) {
  const lv = levelAt(valueAt(t));
  for (let d = 0.004; d <= 0.5; d += 0.004) { if (t - d < 0) return -1; if (levelAt(valueAt(t - d)) !== lv) return d; }
  return -1;
}
function sinceFull(t, valueAt) {
  if (valueAt(t) < 0.995) return -1;
  for (let d = 0.005; d <= 2.5; d += 0.005) { if (t - d < 0) return 2.5; if (valueAt(t - d) < 0.995) return d; }
  return 2.5;
}

// --- Mounting the Quick slider screen --------------------------------------------------------------------
// The preview's panel: the user's, dressed as the screen says, growing out of the 14 dp Dock bar.
function previewPanel(host, st) {
  const ph = +host.dataset.h, winW = +host.dataset.w;
  const half = Math.min(ph * 0.22, 36);
  const bar = { w: 14, h: half * 2, color: '#FF000000', shape: 'tab', flare: 0.29, radii: [8, 8, 8, 8], edgeMargin: 0 };
  const panel = fillPanel(host, {
    w: st.thickness, h: ph, boxW: winW, bar: st.matchShape ? bar : { ...bar, shape: st.panelShape === 'tab' ? 'tab' : 'rounded' },
    flare: (st.sweep ?? 22) / 100, fill: st.fill, showValue: st.showValue, showIcon: st.showIcon,
    icon: st.target === 'brightness' ? 'ic_brightness_up' : 'ic_vol_increase', valueMargin: st.valueMargin, iconMargin: st.iconMargin,
    theme: st.theme, feedback: st.feedback, entrance: st.animation, room: 0, custom: st.custom || null,
  });
  if (!st.matchShape && st.panelShape !== 'tab') {
    // Its own corners: the rounded panel the user set (Match the bar's shape off).
    panel.panelShape = 'rounded';
    panel.panelRadii = [st.corners[0], st.corners[1], st.corners[3], st.corners[2]];
  }
  return panel;
}

// The clocks a fill runs on at time t for a panel at value(t): its own (shader/surge/glimmer/effort
// seconds, Pixels and pictorial phase), and the feedback's.
function clocksFor(fill, feedback, t, paced) {
  const sp = fill.speed ?? 1;
  const own = ['pixels', 'shader', 'surge', 'glimmer', 'effort'].includes(fill.kind);
  const fbT = t * (own ? 1 : feedback.speed ?? 1);
  let fillTime = t, phase = 0;
  if (fill.kind === 'shader' || fill.kind === 'surge' || fill.kind === 'glimmer') fillTime = 7 + paced * sp;
  else if (fill.kind === 'effort') fillTime = 7 + t * sp;
  if (fill.kind === 'pixels') phase = ((paced * sp) / ((PIXEL_CYCLE_MS[fill.pattern] ?? 2000) / 1000)) % 1;
  else if (FILL_CYCLE_MS[fill.kind]) phase = ((paced * (feedback.speed ?? 1)) / (FILL_CYCLE_MS[fill.kind] / 1000)) % 1;
  return { fbT, fillTime, phase };
}

// Starts every live thing on a Quick slider screen in `el` (the screen, or a container holding it —
// pass the container and a rebuilt screen is picked up by itself): the preview's real panel, every
// tile of the picture rows (each a real panel running its fill), and the opening-animation tiles.
// draw({ t, value, e, entrance, entranceAt, grabbed, stepS, fullS }):
//   t — seconds; value — the preview's level (default: the app's own sweep through the levels, with
//   its step flashes and the flourish at the top); e — the panel's expansion out of the bar (the
//   demo); entrance — the opening animation's progress 0..1, or entranceAt — when it was picked (s);
//   grabbed — a finger on the track (the lip); stepS / fullS — override the flash / flourish clocks.
export function mountQuickSliderSettings(el, opts = {}) {
  let root, st, panel, tiles, rows, viewport;
  const setup = () => {
    root = el.matches?.('.ps-screen') ? el : el.querySelector('.ps-screen') || el;
    st = { ...JSON.parse(root.dataset?.ps || '{}'), ...opts };
    st.fill = normalizeFill(st.fill);
    st.feedback = st.feedback || { follow: true, low: true, full: true, max: 'burst', speed: 1 };
    const host = root.querySelector('.ps-panel');
    panel = host ? previewPanel(host, st) : null;
    tiles = [...root.querySelectorAll('.ps-tile')].filter((tl) => {
      const x = +(tl.closest('[data-x]')?.dataset.x ?? 0);
      return x + 68 > 0 && x < BODY_W;                     // only the tiles the row shows
    }).map((tl) => {
      const fill = normalizeFill(JSON.parse(tl.dataset.fill));
      const feedback = fill.feedback || st.feedback;
      const p = fillPanel(tl, { fill, feedback, room: 0, custom: st.custom || null });
      return { el: tl, p, fill, feedback, level: +tl.dataset.level, showcase: tl.dataset.showcase === '1' };
    });
    viewport = root.querySelector('.scroll');
    rows = mountPanelRows(root);
  };
  setup();
  return ({ t = 0, value = null, e = 1, entrance = null, entranceAt = null, grabbed = false, stepS = null, fullS = null } = {}) => {
    if (!root.isConnected && el.isConnected) setup();
    rows({ t });
    if (panel) {
      const auto = value == null;
      const v = auto ? previewValue(t) : clamp(value);
      const valueAt = auto ? previewValue : () => v;
      const follow = st.feedback.follow !== false;
      const paced = auto ? pacedSeconds(t, follow) : t * pace(v, follow);
      const { fbT, fillTime, phase } = clocksFor(st.fill, st.feedback, t, paced);
      let p = entrance;
      if (p == null && entranceAt != null) p = clamp((t - entranceAt) / entranceSeconds(st.animation || 'slide', st.speed || 1));
      const step = stepS ?? sinceStep(t, valueAt);
      panel.draw({
        e, value: v, t: fbT, fillTime, phase, grabbed, entrance: p,
        stepS: step, fullS: fullS ?? (auto ? sinceFull(t, valueAt) : (v >= 0.995 ? 2.5 : -1)),
        arrival: step < 0 ? 1 : clamp((step * (st.fill.speed ?? 1)) / 0.45),
      });
    }
    // Only the tiles inside the scrolling viewport are drawn: the rest are not seen this frame.
    const vr = viewport?.getBoundingClientRect();
    for (const tl of tiles) {
      if (vr && vr.height > 0) {
        const r = tl.el.getBoundingClientRect();
        if (r.bottom < vr.top || r.top > vr.bottom) continue;
      }
      const sp = tl.fill.speed ?? 1;
      const paced = t * pace(tl.level, tl.feedback.follow !== false);
      const { fbT, fillTime, phase } = clocksFor(tl.fill, tl.feedback, t, paced);
      tl.p.draw({ value: tl.level, t: fbT, fillTime, phase, fullS: tl.showcase ? fbT % 3.6 : -1, stepS: -1, arrival: 1, sp });
    }
  };
}

// --- Loading -----------------------------------------------------------------------------------------------
export const PANELSETTINGS_ICONS = [...new Set([
  'filled/arrow_back', 'outlined/help_outline', 'filled/expand_more', 'filled/check', 'filled/remove', 'filled/add', 'filled/edit',
  'filled/warning', 'filled/keyboard_arrow_right', 'filled/keyboard_arrow_up', 'filled/keyboard_arrow_down', 'filled/close',
  'filled/aspect_ratio', 'filled/list', 'filled/palette', 'filled/animation', 'filled/widgets', 'filled/tune',
  'drawable/ic_vol_increase', 'drawable/ic_brightness_up', ...MENU_CATALOG.map((e) => e.glyph),
])];
// The drawables the panels draw on their canvases (the number's companion icon).
export const PANELSETTINGS_DRAWABLES = ['ic_vol_increase', 'ic_brightness_up'];
// Every program fill the screens can show, for QuickPanel.shadersFor.
export const PANELSETTINGS_SHADERS = [
  ...Object.keys(SHADER_FILLS).map((effect) => ({ kind: 'shader', effect })),
  ...Object.keys(SURGE_FILLS).map((look) => ({ kind: 'surge', look })),
];

// Loads what these screens draw with: icons, drawables and (unless `shaders` is false) the AGSL fills.
export async function preparePanelSettings({ shaders = true } = {}) {
  await loadIcons(PANELSETTINGS_ICONS);
  await loadDrawables(PANELSETTINGS_DRAWABLES);
  if (shaders) await QuickPanel.shadersFor(PANELSETTINGS_SHADERS);
}

export { EFFORT_NAMES };
