// Handler Actions (HandlerActionsScreen.kt + GestureTryPad.kt), the action picker (ActionPicker.kt)
// and Gestures for chosen apps (AppGesturesScreen.kt), at 1 dp = 1 px, to specs/actions_visibility.md
// §1–3, §5 and §6.2, in the colours of m3.js.
//
// Every element a finger might tap carries data-target="<name>" (listed above each screen); a video
// finds it with querySelector and getBoundingClientRect. No target carries a transform of its own, so a
// squeeze set on one (the tutorials' PressBounce) is safe. screenLayout(el) measures any screen: where
// each target sits at scroll 0 and how far the list scrolls; the picker's element also carries
// `el.layout` with the scroll that brings each group under its pinned search.
//
// Actions are named by the ids of ACTIONS ('open-deck', 'quick-panel', …); the app's stored
// identifiers ('Open quick panel') and English labels ('Quick panel') are accepted too, and
// 'launch-app:<APPS key>' is Launch app set to one of kit/phone.js's made-up apps.
import { icon, drawableIcon, h, clamp, lerp } from './core.js';
import * as M from './m3.js';
import { PRESETS } from './quickpanel.js';
import { barSvg } from './screens.js';
import { APPS, APP_ICONS, appIcon } from './phone.js';
import { handSvg, setHand } from './hand.js';

const C = M.LIGHT;
const SCREEN_W = 412;
const SCREEN_H = 915;
/** The gesture navigation inset: every route but Appearance is padded above it (MainNavigation.kt). */
const NAV = 24;
/** GestureGlyph's bar: onSurfaceVariant @ 0.3. */
const GLYPH_BAR = 'rgba(75,85,99,.3)';
/** The try pad: clamp(0.2 × the screen's longer side, 150, 220) — 183 dp on a 915 dp phone. */
export const PAD_H = clamp(0.2 * Math.max(SCREEN_W, SCREEN_H), 150, 220);

// --- The eight gestures ----------------------------------------------------------------------------
export const SLOTS = [
  { key: 'tap', name: 'Tap', hint: 'Tap the bar once.', target: 'slot-tap' },
  { key: 'doubleTap', name: 'Double tap', hint: 'Tap the bar twice, quickly.', target: 'slot-double-tap' },
  { key: 'tripleTap', name: 'Triple tap', hint: 'Tap the bar three times, quickly.', target: 'slot-triple-tap' },
  { key: 'longPress', name: 'Long press', hint: 'Press and hold the bar.', target: 'slot-long-press' },
  { key: 'swipeUp', name: 'Swipe up', hint: 'Slide your finger up along the bar.', target: 'slot-swipe-up' },
  { key: 'swipeDown', name: 'Swipe down', hint: 'Slide your finger down along the bar.', target: 'slot-swipe-down' },
  { key: 'swipeIn', name: 'Swipe in', hint: 'Swipe from the bar toward the middle of the screen', target: 'slot-swipe-in' },
  { key: 'swipeOut', name: 'Swipe out', hint: 'Swipe from the bar toward the screen edge', target: 'slot-swipe-out' },
];
const SLOT_BY_KEY = Object.fromEntries(SLOTS.map((s) => [s.key, s]));
const SLOT_ALIASES = { tap: 'tap', singletap: 'tap', single: 'tap', doubletap: 'doubleTap', double: 'doubleTap', tripletap: 'tripleTap',
  triple: 'tripleTap', longpress: 'longPress', hold: 'longPress', swipeup: 'swipeUp', swipedown: 'swipeDown', swipein: 'swipeIn',
  swipeinward: 'swipeIn', swipeout: 'swipeOut' };
/** 'singleTap', 'double-tap', 'Swipe in' … → the SLOTS key. */
export function slotKey(s) {
  const k = SLOT_ALIASES[String(s).toLowerCase().replace(/[^a-z]/g, '')];
  if (!k) throw new Error('unknown gesture slot: ' + s);
  return k;
}

// --- The action catalogue (HandlerActionCatalog.ALL, then the swipe-only entries) ------------------
// group: where the picker lists it; groups: every group it can be listed under (the Quick panel heads
// the swipe pickers' "Adjust as you swipe"); slots: the gestures it is offered for (all by default);
// needs: the permission it waits on ('writeSettings' | 'accessibility' | 'dnd').
export const GROUPS = [
  { id: 'swipe', label: 'Adjust as you swipe' },
  { id: 'handler', label: 'Handler' },
  { id: 'volume', label: 'Volume & brightness' },
  { id: 'deck', label: 'Deck & tools' },
  { id: 'device', label: 'Device' },
  { id: 'media', label: 'Media' },
  { id: 'system', label: 'System' },
];
const ALL_SLOTS = SLOTS.map((s) => s.key);
const A = (id, stored, label, iconId, group, extra = {}) => ({ id, stored, label, icon: iconId, group, groups: [group], slots: ALL_SLOTS, needs: null, ...extra });
export const ACTIONS = [
  A('none', 'None', 'None', 'drawable/ic_nothing', 'handler'),
  A('move-handler', 'Reposition handler', 'Move handler', 'drawable/ic_move', 'handler', { slots: ['longPress'] }),
  A('open-deck', 'Open deck', 'Open deck', 'filled/view_sidebar', 'deck'),
  A('open-menu', 'Open menu', 'Open menu', 'filled/menu', 'handler'),
  // Drawn with ic_brightness_up instead while the Quick slider drives the brightness.
  A('quick-panel', 'Open quick panel', 'Quick panel', 'drawable/ic_vol_increase', 'volume', { groups: ['volume', 'swipe'], iconBrightness: 'drawable/ic_brightness_up' }),
  A('open-volume-ui', 'Open volume UI', 'Open volume UI', 'drawable/ic_vol_increase', 'volume'),
  A('mute', 'Mute', 'Mute', 'drawable/ic_mute', 'volume'),
  A('mute-or-unmute', 'Mute or Unmute', 'Mute or Unmute', 'drawable/ic_mute', 'volume'),
  A('auto-brightness', 'Toggle auto brightness', 'Auto brightness on/off', 'drawable/ic_brightness_auto', 'device', { needs: 'writeSettings' }),
  A('music-overlay', 'Active Music Overlay', 'Active Music Overlay', 'drawable/ic_music_ui', 'volume'),
  A('search', 'Open search', 'Search', 'filled/search', 'deck'),
  A('timer', 'Open timer', 'Timer', 'filled/timer', 'deck'),
  A('calculator', 'Open calculator', 'Calculator', 'filled/calculate', 'deck'),
  A('notes', 'Open notes', 'Notes', 'filled/edit_note', 'deck'),
  A('media-controls', 'Open media controls', 'Media controls', 'filled/music_note', 'deck'),
  A('coin-toss', 'Coin toss', 'Coin toss', 'filled/paid', 'deck'),
  A('dice-roll', 'Dice roll', 'Dice roll', 'filled/casino', 'deck'),
  A('scan-qr', 'Scan QR code', 'Scan QR code', 'filled/qr_code_scanner', 'deck'),
  // Stored as "Launch app:<package>"; here 'launch-app:<APPS key>'.
  A('launch-app', 'Launch app', 'Launch app', 'filled/apps', 'deck'),
  A('flashlight', 'Toggle flashlight', 'Flashlight', 'filled/flashlight_on', 'device'),
  A('dnd', 'Toggle Do Not Disturb', 'Do Not Disturb', 'filled/do_not_disturb_on', 'device', { needs: 'dnd' }),
  A('auto-rotate', 'Toggle auto-rotate', 'Auto-rotate', 'filled/screen_rotation', 'device', { needs: 'writeSettings' }),
  A('ring-vibrate', 'Ring or vibrate', 'Ring / vibrate', 'filled/vibration', 'device'),
  A('wifi', 'Wi-Fi panel', 'Wi-Fi', 'filled/wifi', 'device'),
  A('bluetooth', 'Bluetooth settings', 'Bluetooth', 'filled/bluetooth', 'device'),
  A('internet', 'Internet panel', 'Internet', 'filled/network_cell', 'device'),
  A('camera', 'Open camera', 'Camera', 'filled/photo_camera', 'device'),
  A('voice-assistant', 'Voice assistant', 'Voice assistant', 'filled/keyboard_voice', 'device'),
  A('play-pause', 'Play or pause', 'Play / pause', 'filled/play_arrow', 'media'),
  A('next-track', 'Next track', 'Next track', 'filled/skip_next', 'media'),
  A('previous-track', 'Previous track', 'Previous track', 'filled/skip_previous', 'media'),
  A('lock', 'Lock', 'Lock screen', 'filled/lock', 'system', { needs: 'accessibility' }),
  A('screenshot', 'Screenshot', 'Screenshot', 'filled/screenshot', 'system', { needs: 'accessibility' }),
  A('back', 'Back', 'Back', 'filled/arrow_back', 'system', { needs: 'accessibility' }),
  A('home', 'Home', 'Home', 'filled/home', 'system', { needs: 'accessibility' }),
  A('recents', 'Recent apps', 'Recent apps', 'filled/view_carousel', 'system', { needs: 'accessibility' }),
  A('notifications', 'Open notifications', 'Notifications', 'filled/notifications', 'system', { needs: 'accessibility' }),
  A('quick-settings', 'Open quick settings', 'Quick settings', 'filled/tune', 'system', { needs: 'accessibility' }),
  A('power-menu', 'Power menu', 'Power menu', 'filled/power_settings_new', 'system', { needs: 'accessibility' }),
  A('hide-handler', 'Hide Handler', 'Hide Handler', 'drawable/ic_visibility_hide', 'handler'),
  A('stop-service', 'Stop service', 'Stop service', 'drawable/ic_power', 'handler'),
  A('open-app', 'Open App', 'Open App', 'drawable/ic_app_open', 'handler'),
  // Only on a vertical swipe: the stroke's length is the size of the change.
  A('increase-volume-ui', 'Increase volume and show UI', 'Increase volume and show UI', 'drawable/ic_vol_increase', 'swipe', { slots: ['swipeUp'] }),
  A('increase-volume', 'Increase volume', 'Increase volume', 'drawable/ic_vol_plus', 'swipe', { slots: ['swipeUp'] }),
  A('increase-brightness', 'Increase brightness', 'Increase brightness', 'drawable/ic_brightness_up', 'swipe', { slots: ['swipeUp'], needs: 'writeSettings' }),
  A('decrease-volume-ui', 'Decrease volume and show UI', 'Decrease volume and show UI', 'drawable/ic_vol_decrease', 'swipe', { slots: ['swipeDown'] }),
  A('decrease-volume', 'Decrease volume', 'Decrease volume', 'drawable/ic_vol_minus', 'swipe', { slots: ['swipeDown'] }),
  A('decrease-brightness', 'Decrease brightness', 'Decrease brightness', 'drawable/ic_brightness_down', 'swipe', { slots: ['swipeDown'], needs: 'writeSettings' }),
];
const BY_ID = new Map(ACTIONS.map((a) => [a.id, a]));
const BY_NAME = new Map();
for (const a of ACTIONS) for (const k of [a.id, a.stored, a.label]) if (!BY_NAME.has(k.toLowerCase())) BY_NAME.set(k.toLowerCase(), a);

/** What a fresh install's eight gestures do: Advanced = the Dock preset (also the bare fallback), Regular = Classic. */
export const DEFAULT_SLOTS = {
  advanced: { tap: 'open-volume-ui', doubleTap: 'none', tripleTap: 'none', longPress: 'move-handler', swipeUp: 'quick-panel', swipeDown: 'quick-panel', swipeIn: 'open-deck', swipeOut: 'none' },
  regular: { tap: 'open-volume-ui', doubleTap: 'none', tripleTap: 'none', longPress: 'move-handler', swipeUp: 'increase-volume-ui', swipeDown: 'decrease-volume-ui', swipeIn: 'none', swipeOut: 'none' },
};
const modeKey = (m) => (/^(regular|classic|simple)$/i.test(String(m)) ? 'regular' : 'advanced');

/** An action by id, stored identifier or label; 'launch-app:music' is Launch app set to Music. */
export function findAction(v) {
  if (v == null || v === '') return null;
  if (typeof v === 'object') return { ...(BY_ID.get(v.id) || {}), ...v };
  const s = String(v);
  const launch = s.match(/^launch[- ]app:(.+)$/i);
  if (launch) return { ...BY_ID.get('launch-app'), app: launch[1] };
  const a = BY_NAME.get(s.toLowerCase());
  if (!a) throw new Error('unknown action: ' + s);
  return a;
}
const isNone = (a) => !a || a.id === 'none';
/** actionDisplayName: an app's own label for Launch app, else the catalogue label. */
export const actionName = (a) => (a?.app ? APPS[a.app]?.label ?? 'Launch app' : a?.label ?? 'None');

/** The eight gestures' actions: the mode's defaults with `slots` over them. */
export function resolveSlots(mode = 'advanced', slots = {}) {
  const out = {};
  for (const s of SLOTS) out[s.key] = findAction(DEFAULT_SLOTS[modeKey(mode)][s.key]);
  for (const [k, v] of Object.entries(slots || {})) out[slotKey(k)] = findAction(v) ?? findAction('none');
  return out;
}

/** The picker's groups for a gesture (ActionPicker.pickerGroups), each { id, label, actions }. */
export function pickerGroups(slot) {
  const key = slotKey(slot);
  const catalogue = ACTIONS.filter((a) => a.group !== 'swipe' && a.slots.includes(key));
  const grouped = (drop) => GROUPS.filter((g) => g.id !== 'swipe')
    .map((g) => ({ ...g, actions: catalogue.filter((a) => a.group === g.id && a.id !== drop) }))
    .filter((g) => g.actions.length);
  if (key === 'swipeUp' || key === 'swipeDown') {
    const steered = [BY_ID.get('quick-panel'), ...ACTIONS.filter((a) => a.group === 'swipe' && a.slots.includes(key))];
    return [{ ...GROUPS[0], actions: steered }, ...grouped('quick-panel')];
  }
  return grouped(null);
}

// --- Icons -------------------------------------------------------------------------------------------
const DRAWABLES = ['ic_nothing', 'ic_move', 'ic_vol_increase', 'ic_vol_decrease', 'ic_vol_plus', 'ic_vol_minus', 'ic_mute', 'ic_brightness_auto',
  'ic_brightness_up', 'ic_brightness_down', 'ic_music_ui', 'ic_visibility_hide', 'ic_power', 'ic_app_open'];
export const ACTIONS_ICONS = [...new Set([
  ...ACTIONS.map((a) => a.icon), ...DRAWABLES.map((d) => 'drawable/' + d),
  'filled/arrow_back', 'outlined/help_outline', 'filled/keyboard_arrow_right', 'filled/arrow_forward', 'filled/search', 'filled/close',
  'filled/check', 'filled/check_circle', 'filled/accessibility', 'filled/add', 'filled/remove', 'filled/warning', 'filled/menu', 'filled/apps',
  ...APP_ICONS,
])];

// ActionIconImage: a drawable tinted SrcIn, a Material icon tinted, or an app's own icon untinted.
function actionIcon(a, size, color, { sliderTarget = 'adaptive' } = {}) {
  if (!a) return drawableIcon('ic_nothing', size, color);
  if (a.app && APPS[a.app]) return appIcon(a.app, size);
  const name = a.id === 'quick-panel' && sliderTarget === 'brightness' ? a.iconBrightness : a.icon;
  return name.startsWith('drawable/') ? drawableIcon(name, size, color) : icon(name, size, color);
}

// --- GestureGlyph (GestureSlots.kt): a gesture on a little bar, in a 40-unit square ---------------
export function gestureGlyph(slot, size = 36, { tint = C.primary, bar = GLYPH_BAR, onLeft = false } = {}) {
  const key = slotKey(slot);
  const horizontal = key === 'swipeIn' || key === 'swipeOut';
  const inward = onLeft ? 1 : -1;
  const bx = horizontal ? 20 - inward * 9 : 20;
  const f = (n) => +n.toFixed(2);
  let s = `<rect x="${bx - 2.5}" y="7" width="5" height="26" rx="2.5" fill="${bar}"/>`;
  const stroke = (fx, fy, tx, ty) => {
    const ang = Math.atan2(ty - fy, tx - fx);
    let d = `M${fx} ${fy}L${tx} ${ty}`;
    for (const side of [-1, 1]) {
      const a = ang + Math.PI + side * 0.7;
      d += `M${tx} ${ty}L${f(tx + 5 * Math.cos(a))} ${f(ty + 5 * Math.sin(a))}`;
    }
    return `<circle cx="${fx}" cy="${fy}" r="3" fill="${tint}"/><path d="${d}" fill="none" stroke="${tint}" stroke-width="2.2" stroke-linecap="round"/>`;
  };
  if (key === 'tap' || key === 'doubleTap' || key === 'tripleTap') {
    const rings = { tap: 1, doubleTap: 2, tripleTap: 3 }[key];
    s += `<circle cx="20" cy="20" r="3.4" fill="${tint}"/>`;
    for (let i = 1; i <= rings; i++) {
      s += `<circle cx="20" cy="20" r="${f(3.4 + 3.3 * i)}" fill="none" stroke="${tint}" stroke-opacity="${f(0.8 - 0.2 * (i - 1))}" stroke-width="1.5"/>`;
    }
  } else if (key === 'longPress') {
    s += `<circle cx="20" cy="20" r="3.6" fill="${tint}"/><circle cx="20" cy="20" r="9" fill="none" stroke="${tint}" stroke-opacity=".22" stroke-width="2"/>
      <path d="M20 11A9 9 0 1 1 11 20" fill="none" stroke="${tint}" stroke-width="2" stroke-linecap="round"/>`;
  } else if (key === 'swipeUp') s += stroke(bx, 29, bx, 10);
  else if (key === 'swipeDown') s += stroke(bx, 11, bx, 30);
  else if (key === 'swipeIn') s += stroke(bx, 20, bx + inward * 22, 20);
  else s += stroke(bx + inward * 20, 20, bx - inward * 5, 20);
  return `<svg width="${size}" height="${size}" viewBox="0 0 40 40" style="display:block;flex:none">${s}</svg>`;
}

// --- Shared pieces -----------------------------------------------------------------------------------
const tgt = (name) => (name ? ` data-target="${name}"` : '');
/** Text into markup: labels are plain text ('Volume & brightness'). */
const esc = (str) => String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const gap = (w) => `<div style="width:${w}px;flex:none"></div>`;
const vgap = (hh) => `<div style="height:${hh}px;flex:none"></div>`;
const one = 'white-space:nowrap;overflow:hidden;text-overflow:ellipsis;min-width:0';
const chevron = (size = 22, color = C.outline) => icon('filled/keyboard_arrow_right', size, color);
// Inherited bodyLarge text across several lines: line spacing `lh`, with the first line's top and the
// last line's bottom leading trimmed as Compose's default LineHeightStyle does (§0.3).
const trimmed = (size, lh) => `line-height:${lh}px;margin:${-(0.79 * (lh - 1.172 * size)).toFixed(2)}px 0 ${-(0.21 * (lh - 1.172 * size)).toFixed(2)}px`;

// TopAppBar: transparent, 64 dp under the status bar; the nav button 48 dp from x 4, the title from 56.
function topBar(title, { status, subtitle = null, help = false, helpActive = false } = {}) {
  return `<div class="top-bar" style="position:relative;height:${status + 64}px">
      <div${tgt('back')} style="position:absolute;left:4px;top:${status + 8}px;width:48px;height:48px;display:grid;place-items:center">${icon('filled/arrow_back', 24, C.onSurface)}</div>
      <div style="position:absolute;left:56px;right:${help ? 56 : 16}px;top:${status}px;height:64px;display:flex;flex-direction:column;justify-content:center">
        <div style="font:400 22px/28px Roboto;color:${C.onPrimaryContainer};${one}">${title}</div>
        ${subtitle ? `<div style="font:400 12px/16px Roboto;letter-spacing:.4px;color:${C.onSurfaceVariant};${one}">${subtitle}</div>` : ''}
      </div>
      ${help ? `<div${tgt('help')} style="position:absolute;right:4px;top:${status + 8}px;width:48px;height:48px;display:grid;place-items:center">${icon('outlined/help_outline', 24, helpActive ? C.primary : C.onSurfaceVariant)}</div>` : ''}
    </div>`;
}

// PermissionNote: a red-tinted line with the way to the Permissions screen. In a row it is inset 12 dp.
export const NOTE_TEXT = {
  writeSettings: 'Needs Modify system settings to change the brightness. Tap to allow it on the Permissions screen.',
  accessibility: 'Needs the accessibility service to work. Tap to turn it on from the Permissions screen.',
  dnd: 'Needs Do Not Disturb access to work. Tap to allow it on the Permissions screen.',
};
const APPS_NOTE = 'This needs the accessibility service, which tells the app which app is open. Tap to turn it on.';
function permissionNote(str, target, { inRow = false } = {}) {
  return `<div style="padding:${inRow ? '8px 12px 12px' : '8px 0 0'}"><div${tgt(target)} style="border-radius:12px;background:rgba(254,226,226,.6);display:flex;align-items:center;padding:10px 12px">
      ${icon('filled/warning', 18, C.onErrorContainer)}${gap(10)}
      <div style="flex:1;min-width:0;font:400 13px Roboto;letter-spacing:.5px;color:${C.onErrorContainer};${trimmed(13, 18)}">${str}</div>
      ${chevron(18, C.onErrorContainer)}</div></div>`;
}
// The note an action shows under its row: the first of its permissions still missing.
function noteFor(a, missing, sliderTarget, target) {
  if (!a) return '';
  const needs = a.id === 'quick-panel' && sliderTarget === 'brightness' ? 'writeSettings' : a.needs;
  return needs && missing.includes(needs) ? permissionNote(NOTE_TEXT[needs], target, { inRow: true }) : '';
}

// GestureRow: 46 dp glyph tile, the gesture's name, its action under it, a chevron. 70 dp tall.
function gestureRow({ slot, radius, actionHtml, text, dimmed, target, footer = '', onLeft = false }) {
  const ink = dimmed ? C.onSurfaceVariant : C.primary;
  return `<div style="border-radius:${radius};background:${C.row};overflow:hidden">
      <div${tgt(target)} style="display:flex;align-items:center;padding:12px 14px">
        <div style="width:46px;height:46px;border-radius:14px;background:rgba(224,231,255,.6);display:grid;place-items:center;flex:none">${gestureGlyph(slot, 36, { onLeft })}</div>
        ${gap(14)}
        <div style="flex:1;min-width:0">
          ${M.text(SLOT_BY_KEY[slot].name, { size: 16, weight: 600 })}
          <div style="display:flex;align-items:center;padding-top:3px">${actionHtml(16, ink)}${gap(6)}
            ${M.text(text, { size: 14, weight: dimmed ? 400 : 500, color: ink, extra: one })}</div>
        </div>
        ${chevron()}
      </div>${footer}</div>`;
}

// LinkRow (HandlerActionsScreen.kt): as a GestureRow, with a grey tile and a summary line.
function linkRow({ iconHtml, title, summary, radius, target, footer = '' }) {
  return `<div style="border-radius:${radius};background:${C.row};overflow:hidden">
      <div${tgt(target)} style="display:flex;align-items:center;padding:12px 14px">
        <div style="width:46px;height:46px;border-radius:14px;background:rgba(243,244,246,.8);display:grid;place-items:center;flex:none">${iconHtml(24, C.onSecondaryContainer)}</div>
        ${gap(14)}
        <div style="flex:1;min-width:0">${M.text(title, { size: 16, weight: 600 })}${M.text(summary, { size: 14, color: C.onSurfaceVariant, extra: 'padding-top:2px;' + one })}</div>
        ${chevron()}
      </div>${footer}</div>`;
}

// FilterChip: 32 dp in a 48 dp slot, 12 dp corners; selected = secondaryContainer (+ a check where asked).
function filterChip(label, selected, { check = false, target = '', group = '' } = {}) {
  const lead = selected && check;
  return `<div${tgt(target)}${group ? ` data-chip="${group}"` : ''} style="height:48px;display:flex;align-items:center;flex:none">
      <div class="chip" style="height:32px;border-radius:12px;display:flex;align-items:center;white-space:nowrap;padding:0 16px 0 ${lead ? 8 : 16}px;
        ${chipColours(selected)}font:500 14px/20px Roboto;letter-spacing:.1px">${lead ? icon('filled/check', 18, C.onSecondaryContainer) + gap(8) : ''}${label}</div></div>`;
}
const chipColours = (selected) => (selected ? `background:${C.secondaryContainer};color:${C.onSecondaryContainer};` : `box-shadow:inset 0 0 0 1px ${C.outlineVariant};color:${C.onSurfaceVariant};`);

// OutlinedTextField: 56 dp, 1 dp outline (2 dp primary focused), icon 24 dp centred 24 dp in, text from 52 dp.
function searchField({ placeholder, query = '', radius = 16, focused = false, clear = false, target = 'search' }) {
  const border = focused ? `box-shadow:inset 0 0 0 2px ${C.primary}` : `box-shadow:inset 0 0 0 1px ${C.outline}`;
  const textHtml = query
    ? `<span style="color:${C.onSurface};white-space:pre">${esc(query)}</span>${focused ? `<span style="display:inline-block;width:2px;height:20px;background:${C.primary};vertical-align:-4px;margin-left:1px"></span>` : ''}`
    : `${focused ? `<span style="display:inline-block;width:2px;height:20px;background:${C.primary};vertical-align:-4px;margin-right:1px"></span>` : ''}<span style="color:${C.onSurfaceVariant}">${placeholder}</span>`;
  return `<div${tgt(target)} style="position:relative;height:56px;border-radius:${radius}px;${border}">
      <div style="position:absolute;left:12px;top:16px">${icon('filled/search', 24, C.onSurfaceVariant)}</div>
      <div style="position:absolute;left:52px;right:${clear ? 52 : 16}px;top:16px;font:400 16px/24px Roboto;letter-spacing:.5px;${one}">${textHtml}</div>
      ${clear ? `<div${tgt('search-clear')} style="position:absolute;right:0;top:4px;width:48px;height:48px;display:grid;place-items:center">${icon('filled/close', 24, C.onSurfaceVariant)}</div>` : ''}
    </div>`;
}

function radio(selected) {
  const c = selected ? C.primary : C.onSurfaceVariant;
  return `<svg width="24" height="24" viewBox="0 0 24 24" style="display:block;flex:none"><circle cx="12" cy="12" r="9" fill="none" stroke="${c}" stroke-width="2"/>${selected ? `<circle cx="12" cy="12" r="5" fill="${c}"/>` : ''}</svg>`;
}

// A filled M3 Button / OutlinedButton: 40 dp in a 48 dp slot, 12 dp corners.
const filledButton = (label, target, glyph = null) => `<div style="height:48px;display:flex;align-items:center">
    <div${tgt(target)} style="flex:1;height:40px;border-radius:12px;background:${C.primary};display:flex;align-items:center;justify-content:center;padding:0 24px">
      ${glyph ? icon(glyph, 18, C.onPrimary) + gap(8) : ''}<span style="font:500 14px/20px Roboto;letter-spacing:.1px;color:${C.onPrimary}">${label}</span></div></div>`;
const outlinedButton = (label, target) => `<div style="height:48px;display:flex;align-items:center">
    <div${tgt(target)} style="flex:1;height:40px;border-radius:12px;box-shadow:inset 0 0 0 1px ${C.outlineVariant};display:flex;align-items:center;justify-content:center;padding:0 24px">
      <span style="font:500 14px/20px Roboto;letter-spacing:.1px;color:${C.onSurfaceVariant}">${label}</span></div></div>`;
const textButton = (label, target) => `<div${tgt(target)} style="height:48px;display:flex;align-items:center;flex:none">
    <div style="height:40px;min-width:58px;padding:0 12px;display:flex;align-items:center;justify-content:center;font:500 14px/20px Roboto;letter-spacing:.1px;color:${C.primary}">${label}</div></div>`;

const appTile = (key, size = 40) => `<div style="width:${size}px;height:${size}px;border-radius:10px;overflow:hidden;flex:none">${appIcon(key, size)}</div>`;
const segRadius = M.segRadius;
const column = (parts, spacing = 3) => `<div style="display:flex;flex-direction:column;gap:${spacing}px">${parts.join('')}</div>`;

// --- Measuring -----------------------------------------------------------------------------------------
// Screens are built unmounted; to know where things land (the picker's sticky search, the rows a video
// scrolls to) one is laid out once off-screen. Fonts must be loaded first (fontsReady).
let host = null;
function withMounted(el, fn) {
  if (el.isConnected) return fn(el);
  if (!host) {
    host = document.createElement('div');
    host.style.cssText = `position:fixed;left:-30000px;top:0;width:${SCREEN_W}px;height:${SCREEN_H}px;visibility:hidden;pointer-events:none;contain:strict`;
    document.body.appendChild(host);
  }
  host.appendChild(el);
  try { return fn(el); } finally { host.removeChild(el); }
}

/**
 * Where everything with a data-target sits in a screen built by this module, in screen dp:
 * { targets: { name: {x, y, w, h} } at the screen's own scroll, content: { name: y } (top inside the
 * scrolling content, i.e. its screen y at scroll 0 minus the viewport top), viewportTop, viewportH,
 * contentH, maxScroll }. To bring a row whose content y is Y to screen y Ys: scroll = Y + viewportTop − Ys.
 */
export function screenLayout(el) {
  return withMounted(el, () => {
    const box = el.getBoundingClientRect();
    // On a phone the camera has scaled, the rects come scaled: back to dp.
    const k = el.offsetWidth ? box.width / el.offsetWidth : 1;
    const dp = (v) => +(v / k).toFixed(1);
    const vp = el.querySelector('.scroll');
    const inner = el.querySelector('.scroll-content');
    const out = { targets: {}, content: {}, viewportTop: 0, viewportH: 0, contentH: 0, maxScroll: 0 };
    if (vp && inner) {
      const r = vp.getBoundingClientRect();
      const ri = inner.getBoundingClientRect();
      out.viewportTop = dp(r.top - box.top);
      out.viewportH = vp.offsetHeight;
      out.contentH = inner.offsetHeight;
      out.maxScroll = Math.max(0, out.contentH - out.viewportH);
      for (const t of inner.querySelectorAll('[data-target]')) out.content[t.dataset.target] = dp(t.getBoundingClientRect().top - ri.top);
    }
    for (const t of el.querySelectorAll('[data-target]')) {
      const r = t.getBoundingClientRect();
      out.targets[t.dataset.target] = { x: dp(r.left - box.left), y: dp(r.top - box.top), w: dp(r.width), h: dp(r.height) };
    }
    return out;
  });
}

// --- The try pad (GestureTryPad.kt) ------------------------------------------------------------------
const DEMO_WORDS = { tap: 'Tap', hold: 'Hold', longPress: 'Hold', swipeUp: 'Swipe up', swipeDown: 'Swipe down', swipeIn: 'Swipe inward' };
const DEMO_SLOT = { tap: 'tap', hold: 'longPress', longPress: 'longPress', swipeUp: 'swipeUp', swipeDown: 'swipeDown', swipeIn: 'swipeIn' };

/**
 * The pad's geometry in screen dp for a mode: the strip, the bar in it, and the point a finger
 * presses on the bar (the demo's bar point). With a TriedResult shown, nothing above the list moves.
 */
export function padGeometry({ status = 32, mode = 'advanced', side = 'right' } = {}) {
  const P = PAD_H, Wp = SCREEN_W - 32;
  const preset = modeKey(mode) === 'regular' ? PRESETS.classic : PRESETS.dock;
  const bh = Math.min(preset.h, 0.6 * P);
  const margin = Math.min(preset.edgeMargin, 16);
  const pad = { x: 16, y: status + 64 + 4, w: Wp, h: P };
  const bx = side === 'left' ? margin : Wp - margin - preset.w;
  const bar = { x: pad.x + bx, y: pad.y + (P - bh) / 2, w: preset.w, h: bh };
  return { pad, bar, barPoint: { x: bar.x + bar.w / 2, y: pad.y + P / 2 }, preset: { ...preset, h: bh } };
}

function tryPad({ mode, side, touch, demo, actions, sliderTarget }) {
  const P = PAD_H, Wp = SCREEN_W - 32;
  const g = padGeometry({ status: 0, mode, side });
  const bx = g.bar.x - g.pad.x, by = g.bar.y - g.pad.y;
  const layers = [];
  layers.push(`<div${tgt('pad-bar')} style="position:absolute;left:${bx}px;top:${by}px;width:${g.bar.w}px;height:${g.bar.h}px">${barSvg(g.preset, { side })}</div>`);
  if (touch && (touch.alpha ?? 1) > 0.01) {
    const s = clamp(touch.alpha ?? 1), r = 22 * (0.7 + 0.3 * s);
    layers.push(`<svg width="${Wp}" height="${P}" style="position:absolute;left:0;top:0;overflow:visible">
        <circle cx="${touch.x}" cy="${touch.y}" r="${r}" fill="#fff" fill-opacity="${0.45 * s}"/>
        <circle cx="${touch.x}" cy="${touch.y}" r="${r}" fill="none" stroke="#fff" stroke-opacity="${0.9 * s}" stroke-width="2"/></svg>`);
  }
  if (demo) {
    const slot = DEMO_SLOT[demo.gesture] || 'tap';
    const a = actions[slot];
    const words = `${DEMO_WORDS[demo.gesture] || 'Tap'} · ${isNone(a) ? 'Does nothing' : actionName(a)}`;
    const capA = clamp(demo.caption ?? 1);
    if (capA > 0) {
      layers.push(`<div style="position:absolute;left:12px;right:12px;bottom:14px;display:flex;justify-content:center;opacity:${capA}">
          <div style="display:flex;align-items:center;gap:6px;border-radius:999px;background:rgba(255,255,255,.94);padding:7px 14px;
            box-shadow:0 1px 3px rgba(0,0,0,.18),0 1px 2px rgba(0,0,0,.12)">
            ${isNone(a) ? '' : actionIcon(a, 18, C.primary, { sliderTarget })}
            <div style="font:600 14px/20px Roboto;letter-spacing:.1px;color:${C.primary};display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">${words}</div>
          </div></div>`);
    }
    if (demo.hand) layers.push(`<div class="demo-hand" style="position:absolute;inset:0">${handSvg(Wp, P)}</div>`);
  }
  // The strip: Brush.linearGradient from its top-left corner to its bottom-right one.
  const angle = 90 + (Math.atan2(P, Wp) * 180) / Math.PI;
  return `<div${tgt('pad')} style="position:relative;height:${P}px;border-radius:28px;overflow:hidden;background:linear-gradient(${angle.toFixed(2)}deg,#D9CCFF,#9DB2FA)">${layers.join('')}</div>`;
}

// "Double tap → Not set", and Change. 60 dp.
function triedResult(slot, a, { onLeft, sliderTarget }) {
  const nothing = isNone(a);
  const ink = nothing ? C.onSurfaceVariant : C.primary;
  return `<div class="tried" style="display:flex;align-items:center;height:60px;border-radius:16px;background:rgba(224,231,255,.45);padding:6px 4px 6px 8px">
      <div style="width:40px;height:40px;border-radius:12px;background:rgba(224,231,255,.6);display:grid;place-items:center;flex:none">${gestureGlyph(slot, 32, { onLeft })}</div>
      ${gap(10)}
      <div style="flex:1;min-width:0">
        ${M.text(SLOT_BY_KEY[slot].name, { size: 13, color: C.onSurfaceVariant, extra: one })}
        <div style="display:flex;align-items:center">${icon('filled/arrow_forward', 14, C.outline)}${gap(6)}${actionIcon(nothing ? null : a, 18, ink, { sliderTarget })}${gap(6)}
          ${M.text(nothing ? 'Not set' : actionName(a), { size: 15, weight: 600, color: ink, extra: one })}</div>
      </div>
      ${textButton('Change', 'tried-change')}
    </div>`;
}

// --- Dialogs raised on the Actions screen after a pick (§1.9) -------------------------------------
const DISCLOSURE = `GestureVolume uses Android’s accessibility service for four things, all optional:

• To perform the system actions you assign to the bar: Lock screen, Screenshot, Back, Home, Recent apps, Notifications, Quick settings and the Power menu.

• To look at the two volume keys, only if you set the volume keys to Instant: to open the Quick panel the moment you press one. Every other key passes straight on, and no key is recorded. While a Volume down press is waiting, it also notes whether the system’s screenshot has come up, so a screenshot taken with the buttons does not open the panel.

• To see which app is on screen, only if you choose apps to hide the bar in or give apps gestures of their own. It looks at the app’s name and nothing else.

• To draw the bar on the lock screen, only if you turn on Show on the lock screen: Android hides every app’s floating bar there. While the phone is locked, the bar does only what the lock screen already allows: the volume, the Quick panel and quick toggles.

It does not read or collect anything else. Nothing leaves your device. You can turn it off at any time in Settings › Accessibility.`;
export const DIALOGS = {
  brightness: { title: 'Allow brightness control?', dismiss: 'Cancel', confirm: 'Open settings',
    text: 'To change screen brightness, GestureVolume needs the Modify system settings permission. Adaptive brightness is turned off while you use it, and restored when you stop the service.' },
  accessibility: { title: 'Turn on the accessibility service?', dismiss: 'Not now', confirm: 'Open Accessibility settings', text: DISCLOSURE,
    after: 'In the list, open GestureVolume and switch it on.' },
  dnd: { title: 'Allow Do Not Disturb access?', dismiss: 'Not now', confirm: 'Open settings',
    text: 'To toggle Do Not Disturb, GestureVolume needs Do Not Disturb access. You will be taken to the system list where you can allow it.' },
};

// AlertDialog: 24 dp corners on surface, padded 24; the title, 16, the text (scrolling when long), 24,
// then [dismiss][confirm] at the end, 8 apart; when they do not fit on one line, the confirm button
// goes above the dismiss one, 12 apart (AlertDialogFlowRow). The scrim is the platform's dim. k is the
// entrance (0..1); scroll moves a long text (stopping at its end).
function alertDialog(kind, { k = 1, scroll = 0 } = {}) {
  const d = DIALOGS[kind];
  if (!d) throw new Error('unknown dialog: ' + kind);
  const btn = (label, filled, target) => `<div style="height:48px;display:flex;align-items:center;flex:none">
      <div${tgt(target)} style="height:40px;border-radius:12px;padding:0 24px;display:flex;align-items:center;font:500 14px/20px Roboto;letter-spacing:.1px;white-space:nowrap;
        ${filled ? `background:${C.primary};color:${C.onPrimary}` : `box-shadow:inset 0 0 0 1px ${C.outlineVariant};color:${C.onSurfaceVariant}`}">${label}</div></div>`;
  // The card's own box carries the target; its placement and entrance are on the wrapper, so a
  // squeeze set on a target's style.transform never fights them.
  return `<div class="dialog-layer" style="position:absolute;inset:0;z-index:30">
      <div style="position:absolute;inset:0;background:rgba(0,0,0,${(0.32 * k).toFixed(3)})"></div>
      <div style="position:absolute;left:24px;right:24px;top:50%;opacity:${k};transform:translateY(-50%) scale(${0.9 + 0.1 * k})">
       <div${tgt('dialog')} style="max-height:${SCREEN_H - 112}px;display:flex;flex-direction:column;border-radius:24px;background:${C.surface};padding:24px">
        <div style="padding-bottom:16px;font:400 22px/28px Roboto;color:${C.primary}">${d.title}</div>
        <div class="dialog-text" style="flex:0 1 auto;min-height:0;overflow:hidden;margin-bottom:24px"><div style="transform:translateY(${-scroll}px)">
          ${M.bodyMedium(d.text, C.onSurfaceVariant, 'white-space:pre-line')}
          ${d.after ? `${vgap(12)}<div style="font:600 14px/20px Roboto;letter-spacing:.25px;color:${C.onSurface}">${d.after}</div>` : ''}
        </div></div>
        <div style="display:flex;flex-wrap:wrap-reverse;justify-content:flex-end;gap:12px 8px">${btn(d.dismiss, false, 'dialog-dismiss')}${btn(d.confirm, true, 'dialog-confirm')}</div>
      </div></div></div>`;
}

/**
 * The same dialog as an element to lay over any screen (as kit/appdialogs.js's are):
 * kind 'brightness' | 'accessibility' | 'dnd', k the entrance 0..1, scroll a long text's scroll (px,
 * stopping at its end). data-target: dialog, dialog-dismiss, dialog-confirm.
 */
export function actionsDialog({ kind = 'brightness', k = 1, scroll = 0 } = {}) {
  const el = h(alertDialog(kind, { k, scroll }));
  if (scroll) clampDialogScroll(el, scroll);
  return el;
}
function clampDialogScroll(el, scroll) {
  withMounted(el, () => {
    const box = el.querySelector('.dialog-text');
    const max = Math.max(0, box.firstElementChild.offsetHeight - box.clientHeight);
    box.firstElementChild.style.transform = `translateY(${-Math.min(scroll, max)}px)`;
  });
}

// --- Handler Actions ---------------------------------------------------------------------------------
const SLIDER_TARGETS = { adaptive: 'Adaptive volume', media: 'Media volume', ring: 'Ring volume', alarm: 'Alarm volume', brightness: 'Brightness' };
const STREAMS = [
  { id: 'media', label: 'Media only', desc: 'The bar always changes media volume, even during a call.' },
  { id: 'follow', label: 'Follow playback', desc: 'Adapts to a call, a ringing phone or an alarm. Otherwise it changes media volume.' },
  { id: 'keys', label: 'Match volume keys', desc: 'Behaves like the volume buttons, including changing the ringtone volume when nothing is playing. The bar will not silence the ringtone completely.' },
];

// SettingCard: a title, the line under it and the control, padded 16 in a segment.
const settingCard = (title, desc, body, radius, target = '') => `<div${tgt(target)} style="border-radius:${radius};background:${C.row};padding:16px">
    ${M.text(title, { size: 16, weight: 600 })}${M.bodyMedium(desc, C.onSurfaceVariant, 'padding:2px 0 12px')}${body}</div>`;

function swipeAmountCard(step, radius) {
  const label = (p) => (p === 0 ? 'By how far you swipe' : `${p}% per swipe`);
  const chips = [0, 5, 10, 20].map((p) => filterChip(label(p), p === step, { check: true, target: p === 0 ? 'swipe-amount-length' : `swipe-amount-${p}` })).join('');
  const desc = step === 0
    ? 'A step for about every finger’s width you swipe, as the bar has always worked. A long swipe sweeps the whole range.'
    : `Each swipe moves it ${step}%, however long or short the swipe. A volume with only a few steps moves to the nearest one.`;
  return settingCard('Swipe amount', 'How far one swipe up or down moves the volume or brightness',
    `<div style="display:flex;flex-wrap:wrap;gap:8px">${chips}</div>${M.bodySmall(desc, C.onSurfaceVariant, 'padding-top:10px')}`, radius, 'swipe-amount');
}

function volumeStreamCard(stream, radius) {
  const rows = STREAMS.map((s) => {
    const sel = s.id === stream;
    return `<div${tgt('stream-' + s.id)} style="display:flex;align-items:flex-start;border-radius:14px;background:${sel ? 'rgba(224,231,255,.7)' : 'transparent'};padding:8px 4px">
        <div style="padding:0 8px;flex:none">${radio(sel)}</div>
        <div style="flex:1;min-width:0;padding-right:8px">${M.text(s.label, { size: 15, weight: sel ? 600 : 500 })}${M.bodySmall(s.desc, C.onSurfaceVariant, 'padding-top:2px')}</div></div>`;
  }).join('');
  return settingCard('Volume stream', 'Choose which volume the bar changes.', `<div>${rows}</div>`, radius, 'volume-stream');
}

// SliderControl (handler_appearance): label, − value +, and the slim track under it. 56 dp.
function timingSlider({ key, label, ms, auto, min, max, width = 348, dragging = false }) {
  const custom = ms > 0;
  const shown = custom ? ms : auto;
  const f = clamp((shown - min) / (max - min));
  const nudge = (glyph, enabled, target) => `<div${tgt(target)} style="width:28px;height:28px;border-radius:50%;flex:none;background:${enabled ? 'rgba(79,70,229,.10)' : 'transparent'};display:grid;place-items:center">
      ${icon(glyph, 14, enabled ? C.primary : 'rgba(79,70,229,.3)')}</div>`;
  const cx = 2 + (width - 4) * f;
  const tw = dragging ? 6 : 4, th = dragging ? 22.5 : 18;
  return `<div>
      <div style="height:28px;display:flex;align-items:center;gap:2px">
        ${M.text(label, { size: 15, weight: 500, extra: 'flex:1;' + one })}${nudge('filled/remove', shown > min, key + '-minus')}
        <div style="min-width:48px;text-align:center;font:700 12px/1.172 Roboto;letter-spacing:.5px;color:${C.primary};white-space:nowrap">${custom ? `${shown} ms` : `${shown} ms · automatic`}</div>
        ${nudge('filled/add', shown < max, key + '-plus')}</div>
      <div${tgt(key + '-slider')} style="position:relative;height:28px;width:${width}px;overflow:hidden;border-radius:14px">
        <div style="position:absolute;left:0;right:0;top:12px;height:4px;border-radius:2px;background:rgba(79,70,229,.18)"></div>
        <div style="position:absolute;left:0;width:${cx}px;top:12px;height:4px;border-radius:2px;background:${C.primary}"></div>
        <div${tgt(key + '-thumb')} style="position:absolute;left:${cx - tw / 2}px;top:${14 - th / 2}px;width:${tw}px;height:${th}px;border-radius:${tw / 2}px;background:${C.primary}"></div>
      </div>
      ${custom ? textButton('Use automatic', key + '-auto') : ''}
    </div>`;
}

function tapTimingCard({ doubleTapMs, longPressMs, autoDoubleTap = 450, autoLongPress = 400, dragging = null }, radius) {
  const body = `${timingSlider({ key: 'double-tap', label: 'Double-tap window', ms: doubleTapMs, auto: autoDoubleTap, min: 200, max: 800, dragging: dragging === 'double-tap' })}
      ${vgap(8)}
      ${timingSlider({ key: 'long-press', label: 'Long-press delay', ms: longPressMs, auto: autoLongPress, min: 200, max: 1200, dragging: dragging === 'long-press' })}
      ${M.bodySmall('Try the new timing on the bar at the top of this screen.', C.primary, 'padding-top:8px')}`;
  return settingCard('Tap timing', 'How long the bar waits for a second tap, and how long a hold takes to become a long press. Longer is easier to hit; shorter makes a single tap answer sooner.', body, radius, 'tap-timing');
}

/*
 * The Handler Actions screen: the try pad pinned under the top bar, and the gesture groups scrolling
 * under it (Taps, Swipes, More, Behaviour).
 *
 *   slots       gesture → action over the mode's fresh-install defaults (DEFAULT_SLOTS[mode])
 *   tried       { gesture, action?, k = 1 }: the TriedResult under the pad (k 0..1 cross-fades it in
 *               over the hint, as AnimatedContent does; the area grows 56 → 60 dp). action defaults to
 *               the gesture's own.
 *   touch       { x, y, alpha }: the pad's finger indicator, in dp from the strip's top-left
 *               (padGeometry().barPoint − pad.{x,y} is the bar's centre).
 *   demo        { gesture: 'tap'|'hold'|'swipeUp'|'swipeDown'|'swipeIn', caption = 1, hand = null }:
 *               the "How it works" demo: the caption pill, the ? turned primary, and (with
 *               hand = { x, y, alpha, press, ripple, rippleAt, hold }, strip dp) the app's own
 *               pointing hand and touch rings, clipped to the strip.
 *   swipeStep   0 (by length) | 5 | 10 | 20;   stream 'media' | 'follow' | 'keys'
 *   doubleTapMs / longPressMs  0 = automatic (450 / 400 ms); dragging 'double-tap' | 'long-press'
 *   sliderTarget 'adaptive' | 'media' | 'ring' | 'alarm' | 'brightness';  menuCount (9);  appCount (0)
 *   missing     permissions not granted: ['writeSettings', 'accessibility', 'dnd'] → notes under rows
 *   side        'right' | 'left' (the bar's side: glyphs and the pad's bar follow it)
 *   dialog      'brightness' | 'accessibility' | 'dnd', or { kind, k = 1 (entrance), scroll = 0 }: the
 *               prompt the screen raises after a pick needing a permission (§1.9), over a dim scrim
 *
 * data-target: back, help, pad, pad-bar, tried-change, slot-tap, slot-double-tap, slot-triple-tap,
 *   slot-long-press, slot-swipe-up, slot-swipe-down, swipe-amount (the card), swipe-amount-length,
 *   swipe-amount-5, swipe-amount-10, swipe-amount-20, slot-swipe-in, slot-swipe-out,
 *   link-quick-slider, link-menu-items, link-app-gestures, volume-stream (card), stream-media,
 *   stream-follow, stream-keys, tap-timing (card), double-tap-minus, double-tap-plus,
 *   double-tap-slider, double-tap-thumb, long-press-minus, long-press-plus, long-press-slider,
 *   long-press-thumb, double-tap-auto / long-press-auto (only once moved), note-<slot target> and
 *   note-link-quick-slider (permission notes); with a dialog: dialog, dialog-dismiss, dialog-confirm.
 */
export function actionsScreen({ status = 32, scroll = 0, mode = 'advanced', slots = {}, tried = null, touch = null, demo = null,
  swipeStep = 0, stream = 'follow', doubleTapMs = 0, longPressMs = 0, dragging = null, sliderTarget = 'adaptive', menuCount = 9,
  appCount = 0, missing = [], side = 'right', dialog = null } = {}) {
  const onLeft = side === 'left';
  const acts = resolveSlots(mode, slots);
  const slotRow = (key, radius) => {
    const a = acts[key];
    const none = isNone(a);
    const target = SLOT_BY_KEY[key].target;
    return gestureRow({ slot: key, radius, onLeft, target, dimmed: none, text: none ? 'Not set' : actionName(a),
      actionHtml: (s, c) => actionIcon(none ? null : a, s, c, { sliderTarget }), footer: noteFor(a, missing, sliderTarget, 'note-' + target) });
  };
  const taps = ['tap', 'doubleTap', 'tripleTap', 'longPress'].map((k, i) => slotRow(k, segRadius(i, 4)));
  const swipes = [slotRow('swipeUp', segRadius(0, 5)), slotRow('swipeDown', segRadius(1, 5)), swipeAmountCard(swipeStep, segRadius(2, 5)),
    slotRow('swipeIn', segRadius(3, 5)), slotRow('swipeOut', segRadius(4, 5))];
  const quickNote = sliderTarget === 'brightness' && missing.includes('writeSettings') ? permissionNote(NOTE_TEXT.writeSettings, 'note-link-quick-slider', { inRow: true }) : '';
  const more = [
    linkRow({ iconHtml: (s, c) => drawableIcon(sliderTarget === 'brightness' ? 'ic_brightness_up' : 'ic_vol_increase', s, c), title: 'Quick slider',
      summary: SLIDER_TARGETS[sliderTarget] || SLIDER_TARGETS.adaptive, radius: segRadius(0, 3), target: 'link-quick-slider', footer: quickNote }),
    linkRow({ iconHtml: (s, c) => icon('filled/menu', s, c), title: 'Long-press menu items', summary: `${menuCount} selected`, radius: segRadius(1, 3), target: 'link-menu-items' }),
    linkRow({ iconHtml: (s, c) => icon('filled/apps', s, c), title: 'Gestures for chosen apps', summary: appCount === 0 ? 'None' : `${appCount} app${appCount === 1 ? '' : 's'}`,
      radius: segRadius(2, 3), target: 'link-app-gestures' }),
  ];
  const behaviour = [volumeStreamCard(stream, segRadius(0, 2)), tapTimingCard({ doubleTapMs, longPressMs, dragging }, segRadius(1, 2))];

  // Under the strip: the hint, or what was just tried (AnimatedContent, min 56 dp; 60 with a result).
  const k = tried ? clamp(tried.k ?? 1) : 0;
  const areaH = lerp(56, 60, k);
  let under = '';
  if (k < 1) under += `<div style="position:absolute;left:0;right:0;top:0;opacity:${1 - k}">${M.bodyMedium('Tap, hold or swipe the bar above to see what each gesture does. Nothing actually runs here.', C.onSurfaceVariant, 'padding:8px 4px')}</div>`;
  if (tried && k > 0) {
    const key = slotKey(tried.gesture);
    const a = tried.action != null ? findAction(tried.action) : acts[key];
    under += `<div style="position:absolute;left:0;right:0;top:0;opacity:${k};transform:scale(${0.94 + 0.06 * k})">${triedResult(key, a, { onLeft, sliderTarget })}</div>`;
  }
  const pinnedH = 4 + PAD_H + 10 + areaH + 12;
  const top = status + 64 + pinnedH;
  const el = M.screen(`${topBar('Handler Actions', { status, help: true, helpActive: !!demo })}
      <div class="pinned" style="padding:4px 16px 12px">
        ${tryPad({ mode, side, touch, demo, actions: acts, sliderTarget })}
        <div style="position:relative;margin-top:10px;height:${areaH}px">${under}</div>
      </div>
      <div class="scroll" style="position:relative;overflow:hidden;height:${SCREEN_H - NAV - top}px">
        <div class="scroll-content" style="padding:16px;transform:translateY(${-scroll}px)">
          ${M.groupLabel('Taps')}${column(taps)}${vgap(24)}
          ${M.groupLabel('Swipes')}${column(swipes)}${vgap(24)}
          ${M.groupLabel('More')}${column(more)}${vgap(24)}
          ${M.groupLabel('Behaviour')}${column(behaviour)}${vgap(32)}
        </div>
      </div>
      ${dialog ? alertDialog(dialog.kind ?? dialog, typeof dialog === 'object' ? dialog : {}) : ''}`);
  // A long text scrolls only as far as its end.
  if (dialog?.scroll) clampDialogScroll(el, dialog.scroll);
  if (demo?.hand) {
    const hd = demo.hand, g = padGeometry({ status: 0, mode, side });
    const bp = { x: g.barPoint.x - g.pad.x, y: g.barPoint.y - g.pad.y };
    setHand(el.querySelector('.demo-hand svg'), { x: hd.x ?? bp.x, y: hd.y ?? bp.y, alpha: hd.alpha ?? 1, press: hd.press ?? 0, ripple: hd.ripple ?? 0,
      rippleAt: hd.rippleAt, hold: hd.hold ?? 0, tilt: onLeft ? 28 : -28, mirror: onLeft, fingerWidth: ((SCREEN_W - 32) / 188) * 24, ringScale: 1 });
  }
  return el;
}

// --- Action picker -----------------------------------------------------------------------------------
// ActionOption: icon on a 40 dp circle, the label, a caption or the Accessibility badge, the tick. 60 dp.
function optionRow({ iconHtml, label, caption = null, a11y = false, selected, opensMore = false, radius, target, extra = '' }) {
  const ink = selected ? C.onPrimaryContainer : C.onSurface;
  const inkA = (a) => (selected ? `rgba(49,46,129,${a})` : `rgba(31,41,55,${a})`);
  return `<div${tgt(target)} style="display:flex;align-items:center;border-radius:${radius};background:${selected ? C.primaryContainer : C.row};padding:10px 14px;${extra}">
      <div style="width:40px;height:40px;border-radius:50%;background:${selected ? C.primary : C.surface};display:grid;place-items:center;flex:none">${iconHtml(22, selected ? C.onPrimary : C.primary)}</div>
      ${gap(14)}
      <div style="flex:1;min-width:0">
        ${M.text(label, { size: 15, weight: selected ? 600 : 500, color: ink, extra: 'display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden' })}
        ${caption ? M.text(caption, { size: 12, color: inkA(0.7), extra: one }) : ''}
        ${a11y ? `<div style="display:flex;align-items:center;padding-top:2px">${icon('filled/accessibility', 12, inkA(0.6))}${gap(4)}${M.text('Accessibility', { size: 12, color: inkA(0.6) })}</div>` : ''}
      </div>
      ${selected ? icon('filled/check_circle', 22, C.primary) : opensMore ? chevron() : ''}
    </div>`;
}

// PickerHeader: the gesture drawn big, how it is done, and what it does now.
function pickerHeader(slot, a, { follows, onLeft, sliderTarget }) {
  const name = actionName(a);
  return `<div style="display:flex;align-items:center;border-radius:24px;background:rgba(224,231,255,.55);padding:16px">
      <div style="width:64px;height:64px;border-radius:18px;background:rgba(255,255,255,.7);display:grid;place-items:center;flex:none">${gestureGlyph(slot, 48, { onLeft })}</div>
      ${gap(16)}
      <div style="flex:1;min-width:0">
        ${M.bodyMedium(SLOT_BY_KEY[slot].hint, C.onSurface)}
        ${vgap(8)}
        <div style="display:flex;align-items:center">${actionIcon(a, 18, C.primary, { sliderTarget })}${gap(6)}
          <div style="flex:1;min-width:0;font:600 14px Roboto;letter-spacing:.5px;color:${C.primary};${trimmed(14, 24)};display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">${follows ? `As everywhere: ${name}` : `Now: ${name}`}</div></div>
      </div>
    </div>`;
}

const pickerCache = new Map();

/*
 * The action picker for one gesture (a screen of its own): the header, the sticky search with one
 * chip per group, then every action of the slot's groups.
 *
 *   slot        the gesture (§2.2 decides its groups)
 *   selected    the action it is set to; null = the mode's default. With `app` (the per-app variant,
 *               §2.6): the app's own action, and null = "As everywhere".
 *   app         an APPS key: subtitle under the title and the "As everywhere" option first
 *   everywhere  with app: what the gesture does everywhere (default DEFAULT_SLOTS[mode][slot])
 *   query       text in the search field (filters by label or group name; chips hide)
 *   focused     the search field focused (2 dp primary outline and a caret)
 *   scroll      the list's scroll in px; the header scrolls away and the search sticks under the top
 *               bar. pickerLayout() gives the scroll that puts each group's heading under the search.
 *   chip        force the selected chip (a group id); by default it is the group in view: the last
 *               whose heading has reached the bottom of the search, as the app derives it from scroll
 *   chipScroll  the chip row's scroll in px; by default it follows the group in view, one chip of
 *               room before its chip, as the app's LazyRow does.
 *
 * data-target: back, search, search-clear (with a query), chip-<group id> (swipe, handler, volume,
 *   deck, device, media, system), action-everywhere (per-app), action-<action id> for every row.
 */
export function actionPicker({ status = 32, scroll = 0, slot = 'swipeIn', selected = null, app = null, mode = 'advanced', everywhere = null,
  query = '', focused = false, chip = null, chipScroll = null, sliderTarget = 'adaptive', side = 'right' } = {}) {
  const key = slotKey(slot);
  const onLeft = side === 'left';
  const own = selected == null || selected === 'everywhere' ? null : findAction(selected);
  const everywhereA = findAction(everywhere ?? DEFAULT_SLOTS[modeKey(mode)][key]);
  const follows = !!app && !own;
  const current = own ?? everywhereA;
  const chosen = follows ? null : current;
  const q = query.trim().toLowerCase();
  const shown = pickerGroups(key).map((g) => ({
    ...g,
    actions: q ? g.actions.filter((a) => a.label.toLowerCase().includes(q) || g.label.toLowerCase().includes(q)) : g.actions,
  })).filter((g) => g.actions.length);
  const isChosen = (a) => !!chosen && (a.id === chosen.id);
  const option = (a, i, n) => {
    const launch = a.id === 'launch-app' && chosen?.id === 'launch-app' && chosen.app;
    const shownA = launch ? chosen : a;
    return optionRow({ iconHtml: (s, c) => actionIcon(shownA, s, c, { sliderTarget }), label: a.label, caption: launch ? actionName(chosen) : null,
      a11y: a.needs === 'accessibility', selected: isChosen(a), opensMore: a.id === 'launch-app', radius: segRadius(i, n), target: 'action-' + a.id });
  };
  const list = [];
  if (app && !q) {
    list.push(`<div style="padding:8px 16px 4px">${optionRow({ iconHtml: (s, c) => actionIcon(isNone(everywhereA) ? null : everywhereA, s, c, { sliderTarget }),
      label: 'As everywhere', caption: actionName(everywhereA), selected: follows, radius: '20px', target: 'action-everywhere' })}</div>`);
  }
  if (!shown.length) list.push(M.bodyMedium(`No action matches “${esc(query.trim())}”.`, C.onSurfaceVariant, 'padding:32px 24px'));
  for (const g of shown) {
    list.push(`<div data-group="${g.id}" style="padding:18px 16px 0">${M.groupLabel(esc(g.label))}</div>`);
    list.push(`<div style="padding:0 16px;display:flex;flex-direction:column;gap:3px">${g.actions.map((a, i) => option(a, i, g.actions.length)).join('')}</div><div style="height:3px"></div>`);
  }
  const chips = !q && shown.length > 1;
  const stickyH = 56 + (chips ? 8 + 48 : 0) + 8;
  const vpTop = status + 64;
  const vpH = SCREEN_H - NAV - vpTop;
  const el = M.screen(`${topBar(SLOT_BY_KEY[key].name, { status, subtitle: app ? APPS[app]?.label ?? app : null })}
      <div class="scroll" style="position:relative;overflow:hidden;height:${vpH}px">
        <div class="scroll-content" style="padding-bottom:32px;transform:translateY(${-scroll}px)">
          <div class="picker-header" style="padding:4px 16px 12px">${pickerHeader(key, current, { follows, onLeft, sliderTarget })}</div>
          <div style="height:${stickyH}px"></div>
          ${list.join('')}
        </div>
        <div class="sticky" style="position:absolute;left:0;right:0;top:0;height:${stickyH}px;background:${C.background};z-index:2">
          <div style="padding:0 16px">${searchField({ placeholder: 'Search actions', query, radius: 16, focused, clear: !!query })}</div>
          ${chips ? `<div class="chip-row" style="margin-top:8px;overflow:hidden"><div class="chip-strip" style="display:flex;gap:8px;padding:0 16px;width:max-content">
              ${shown.map((g) => filterChip(esc(g.label), false, { target: 'chip-' + g.id, group: g.id })).join('')}</div></div>` : ''}
        </div>
      </div>`);
  // Laid out once per state (not per scroll): the header's height, each group's heading, the chips.
  const cacheKey = JSON.stringify([key, app, own?.id, own?.app, everywhereA?.id, q, onLeft, sliderTarget, status]);
  let L = pickerCache.get(cacheKey);
  if (!L) {
    L = withMounted(el, () => {
      const inner = el.querySelector('.scroll-content');
      const top0 = inner.getBoundingClientRect().top;
      const headerH = el.querySelector('.picker-header').offsetHeight;
      const groups = {};
      for (const g of el.querySelectorAll('[data-group]')) groups[g.dataset.group] = g.getBoundingClientRect().top - top0;
      const rows = {};
      for (const r of inner.querySelectorAll('[data-target]')) rows[r.dataset.target] = r.getBoundingClientRect().top - top0;
      const chipBoxes = [...el.querySelectorAll('[data-chip]')].map((c) => ({ id: c.dataset.chip, x: c.offsetLeft, w: c.offsetWidth }));
      const strip = el.querySelector('.chip-strip');
      return { headerH, groups, rows, chipBoxes, stripW: strip ? strip.offsetWidth : 0, contentH: inner.offsetHeight };
    });
    pickerCache.set(cacheKey, L);
  }
  const stickyTop = Math.max(0, L.headerH - scroll);
  el.querySelector('.sticky').style.top = stickyTop + 'px';
  if (chips) {
    // The group in view: the last whose heading has reached the bottom of the search.
    let inView = shown[0].id;
    for (const g of shown) if (L.groups[g.id] - scroll <= stickyTop + stickyH + 0.5) inView = g.id;
    if (chip && shown.some((g) => g.id === chip)) inView = chip;
    Object.assign(el.querySelector(`[data-chip="${inView}"] .chip`).style, { boxShadow: 'none', background: C.secondaryContainer, color: C.onSecondaryContainer });
    const at = Math.max(0, shown.findIndex((g) => g.id === inView) - 1);
    const maxX = Math.max(0, L.stripW - SCREEN_W);
    const x = chipScroll ?? Math.min(maxX, Math.max(0, L.chipBoxes[at].x - 16));
    el.querySelector('.chip-strip').style.transform = `translateX(${-x}px)`;
  }
  const maxScroll = Math.max(0, Math.round(L.contentH - vpH));
  el.layout = { headerH: L.headerH, stickyH, viewportTop: vpTop, viewportH: vpH, contentH: L.contentH, maxScroll,
    groups: { ...L.groups }, rows: { ...L.rows },
    // The scroll a chip's jump ends at: its heading just under the pinned search, or the end of the
    // list for the last groups, which cannot come up that far (the app's chip then stays on the
    // group above, as here).
    groupScroll: Object.fromEntries(Object.entries(L.groups).map(([id, y]) => [id, clamp(Math.round(y - stickyH), 0, maxScroll)])) };
  return el;
}

/** The picker's layout for a state (see actionPicker's el.layout): heading tops, rows, groupScroll. */
export const pickerLayout = (opts) => actionPicker({ ...opts, scroll: 0 }).layout;

/*
 * Launch app's chooser (ActionPicker.AppChooser): "Choose an app", a search field and every app as one
 * segmented card; the chosen one tinted and ticked.
 *
 * data-target: back, search, app-<key>.
 */
export function launchAppChooser({ status = 32, scroll = 0, query = '', selected = null, focused = false } = {}) {
  const q = query.trim().toLowerCase();
  const keys = appKeys().filter((k) => !q || APPS[k].label.toLowerCase().includes(q));
  const rows = keys.map((k, i) => {
    const sel = k === selected;
    return `<div${tgt('app-' + k)} style="display:flex;align-items:center;border-radius:${segRadius(i, keys.length)};background:${sel ? C.primaryContainer : C.row};padding:10px 14px">
        ${appTile(k)}${gap(14)}${M.text(APPS[k].label, { size: 15, weight: sel ? 600 : 500, color: sel ? C.onPrimaryContainer : C.onSurface, extra: 'flex:1;' + one })}
        ${sel ? icon('filled/check_circle', 22, C.primary) : ''}</div>`;
  });
  const top = status + 64 + 56 + 8;
  return M.screen(`${topBar('Choose an app', { status })}
      <div style="padding:0 16px">${searchField({ placeholder: 'Search apps…', query, radius: 16, focused })}</div>${vgap(8)}
      <div class="scroll" style="position:relative;overflow:hidden;height:${SCREEN_H - NAV - top}px">
        <div class="scroll-content" style="padding:4px 16px 32px;transform:translateY(${-scroll}px)">${column(rows)}</div></div>`);
}

// --- Gestures for chosen apps ------------------------------------------------------------------------
/** The made-up apps a list offers, alphabetical by label (this app left out). */
const appKeys = () => Object.keys(APPS).filter((k) => k !== 'gv').sort((a, b) => APPS[a].label.toLowerCase().localeCompare(APPS[b].label.toLowerCase()));

// AppLine: icon 40 dp (10 dp corners), 14, label 15 SemiBold and an optional subtitle. 60 dp.
const appLine = (key, subtitle = null) => `<div${tgt('app-' + key)} style="display:flex;align-items:center;padding:10px 16px">
    ${appTile(key)}${gap(14)}
    <div style="flex:1;min-width:0">${M.text(APPS[key]?.label ?? key, { size: 15, weight: 600, extra: one })}${subtitle ? M.text(subtitle, { size: 13, color: C.onSurfaceVariant }) : ''}</div></div>`;

const changedLine = (n) => (n === 0 ? 'Nothing changed yet' : `${n} gesture${n === 1 ? '' : 's'} changed`);

/*
 * The list (title "Gestures for chosen apps"): the intro, the accessibility note while the service is
 * off, "Add an app", and the apps that have gestures of their own.
 *
 *   apps           ['video', { key: 'music', slots: { swipeUp: 'increase-brightness' } }, { key, changed: 2 }]
 *                  (listed alphabetically; the subtitle counts the gestures changed)
 *   accessibility  false → the note "This needs the accessibility service…" under the intro
 *
 * data-target: back, add, app-<key> per app, note-accessibility.
 */
export function appGesturesScreen({ status = 32, apps = [], scroll = 0, accessibility = true } = {}) {
  const items = apps.map((a) => (typeof a === 'string' ? { key: a, changed: 0 } : { key: a.key, changed: a.changed ?? Object.keys(a.slots || {}).length }))
    .sort((a, b) => (APPS[a.key]?.label ?? a.key).toLowerCase().localeCompare((APPS[b.key]?.label ?? b.key).toLowerCase()));
  const divider = `<div style="padding:0 16px"><div style="height:1px;background:rgba(209,213,219,.15)"></div></div>`;
  const card = items.length
    ? items.map((a) => appLine(a.key, changedLine(a.changed))).join(divider)
    : M.bodyMedium('No apps yet. Add one, then change the gestures you want to work differently there.', C.onSurfaceVariant, 'padding:16px');
  const top = status + 64;
  return M.screen(`${topBar('Gestures for chosen apps', { status })}
      <div class="scroll" style="position:relative;overflow:hidden;height:${SCREEN_H - NAV - top}px">
        <div class="scroll-content" style="padding:16px;transform:translateY(${-scroll}px)">
          ${M.bodyMedium('Give the bar different gestures in the apps you choose — in YouTube, say, swipe up could set the brightness instead of opening the Quick panel. A gesture you leave alone does what it does in every other app.')}
          ${accessibility ? '' : permissionNote(APPS_NOTE, 'note-accessibility')}
          ${vgap(16)}${filledButton('Add an app', 'add', 'filled/add')}${vgap(16)}
          <div style="border-radius:16px;background:${C.row};padding:4px 0">${card}</div>
          ${vgap(24)}
        </div></div>`);
}

/*
 * Add-app picker (title "Choose an app"): a search field (14 dp corners) and every app not yet listed,
 * plain on the page, alphabetical.
 *
 *   query    filters by label;  exclude  APPS keys already added;  focused  the field focused
 *
 * data-target: back, search, app-<key>.
 */
export function chooseAppScreen({ status = 32, scroll = 0, query = '', exclude = [], focused = false } = {}) {
  const q = query.trim().toLowerCase();
  // The app matches labels and package names; the made-up apps have only labels.
  const keys = appKeys().filter((k) => !exclude.includes(k) && (!q || APPS[k].label.toLowerCase().includes(q)));
  const top = status + 64;
  return M.screen(`${topBar('Choose an app', { status })}
      <div class="scroll" style="position:relative;overflow:hidden;height:${SCREEN_H - NAV - top}px">
        <div class="scroll-content" style="padding-bottom:24px;transform:translateY(${-scroll}px)">
          <div style="padding:8px 16px">${searchField({ placeholder: 'Search apps…', query, radius: 14, focused })}</div>
          ${keys.map((k) => appLine(k)).join('')}
        </div></div>`);
}

/*
 * One app's gestures (title = the app's label): its icon and name, then the eight gestures, each
 * its own action (primary) or "As everywhere: …" (dimmed), and "Remove this app".
 *
 *   app         an APPS key;  slots  the app's own actions (unset = as everywhere)
 *   mode / everywhere  what the gestures do everywhere: DEFAULT_SLOTS[mode] with `everywhere` over it
 *   accessibility false → the accessibility note;  missing  permissions not granted (notes under own rows)
 *
 * data-target: back, note-accessibility, slot-tap … slot-swipe-out, note-<slot target>, remove.
 */
export function appGestureEditor({ status = 32, app = 'video', slots = {}, scroll = 0, mode = 'advanced', everywhere = {}, accessibility = true,
  missing = [], sliderTarget = 'adaptive', side = 'right' } = {}) {
  const onLeft = side === 'left';
  const global = resolveSlots(mode, everywhere);
  const own = {};
  for (const [k, v] of Object.entries(slots || {})) if (v != null) own[slotKey(k)] = findAction(v);
  const rows = SLOTS.map((s, i) => {
    const mine = own[s.key];
    const a = mine ?? global[s.key];
    const name = actionName(a);
    return gestureRow({ slot: s.key, radius: segRadius(i, SLOTS.length), onLeft, target: s.target, dimmed: !mine,
      text: mine ? name : `As everywhere: ${name}`, actionHtml: (sz, c) => actionIcon(a, sz, c, { sliderTarget }),
      footer: mine ? noteFor(mine, missing, sliderTarget, 'note-' + s.target) : '' });
  });
  const label = APPS[app]?.label ?? app;
  const top = status + 64;
  return M.screen(`${topBar(label, { status })}
      <div class="scroll" style="position:relative;overflow:hidden;height:${SCREEN_H - NAV - top}px">
        <div class="scroll-content" style="padding:16px;transform:translateY(${-scroll}px)">
          <div style="display:flex;align-items:center">${appTile(app)}${gap(12)}${M.text(label, { size: 18, weight: 600 })}</div>${vgap(8)}
          ${accessibility ? '' : permissionNote(APPS_NOTE, 'note-accessibility')}${vgap(8)}
          ${column(rows)}${vgap(16)}${outlinedButton('Remove this app', 'remove')}${vgap(24)}
        </div></div>`);
}
