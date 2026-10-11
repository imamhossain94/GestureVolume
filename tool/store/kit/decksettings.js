// The Deck's settings screens (ui/screens/deck/*.kt: Deck, Deck tiles, App shortcuts, Quick dial
// with its add/edit dialog, Search, Notes) and the Deck cards kit/deck.js does not draw yet
// (overlay/deck/DeckCards.kt: Brightness, Calculator, Notes, Checklist, Dice).
//
// Every element a finger might tap carries data-target="<name>"; the names are listed above each
// function. A screen's scrolling column is a `.scroll` viewport holding a `.scroll-content` that
// is translated by -scroll px (its height minus the viewport's is the most it scrolls).
//
// Text that sets only a size inherits the app's bodyLarge (24 sp line, 0.5 sp tracking, trimmed
// to the font on the first and last line); M3's own styles (bodySmall, bodyMedium, labelLarge)
// keep their whole line height. Inside an AlertDialog's text slot the inherited style is
// bodyMedium instead (14/20, 0.25 tracking), which is why the dialog's chips are taller.
import { h, icon, clamp, lerp, Ease } from './core.js';
import * as M from './m3.js';
import { TILES, DEFAULT_TILES, DECK_PALETTES, glassLayers, deckSlider } from './deck.js';
import { APPS, APP_ICONS, appIcon } from './phone.js';
import { argb, luminance, entranceFrame, THEMES } from './quickpanel.js';

const C = M.LIGHT;
const NAV = 24;
const F = 'Roboto';

// --- Catalogues and strings ---------------------------------------------------------------------
// DeckTiles.ALL in canonical (fresh-install) order.
export const TILE_ORDER = ['search', 'flashlight', 'screenshot', 'rotation', 'wifi', 'bluetooth', 'volume', 'brightness', 'media',
  'timer', 'calculator', 'notes', 'checklist', 'coin', 'dice', 'dnd', 'lock', 'qr'];
export const TILE_DESCRIPTIONS = {
  search: 'Apps, contacts, sums and the web', flashlight: 'Switch the camera flash on and off',
  screenshot: 'Capture the screen with the bar and the Deck out of the picture', rotation: 'Switch screen rotation on and off',
  wifi: 'Open the Wi-Fi panel', bluetooth: 'Open Bluetooth settings', volume: 'Media, ring and alarm sliders',
  brightness: 'A brightness slider and the adaptive switch', media: 'Play, pause and skip whatever is playing',
  timer: 'A countdown that keeps running after the Deck closes', calculator: 'Quick sums, right in the panel',
  notes: 'Jot something down and keep it', checklist: 'Tasks and shopping lists', coin: 'Heads or tails', dice: 'Roll two dice',
  dnd: 'Switch Do Not Disturb on and off', lock: 'Lock the phone', qr: 'Scan a QR code or barcode',
};
const NEEDS_A11Y = ['screenshot', 'lock'];
const NOTE = {
  accessibility: 'Needs the accessibility service to work. Tap to turn it on from the Permissions screen.',
  writeSettings: 'Needs Modify system settings to change the brightness. Tap to allow it on the Permissions screen.',
  dnd: 'Needs Do Not Disturb access to work. Tap to allow it on the Permissions screen.',
  phone: 'Needs the Phone permission to call directly. Tap to allow it on the Permissions screen.',
};
const TILE_NOTE = { screenshot: NOTE.accessibility, lock: NOTE.accessibility, brightness: NOTE.writeSettings, rotation: NOTE.writeSettings, dnd: NOTE.dnd };

export const PANEL_THEMES = [['solid', 'Solid'], ['frosted', 'Frosted'], ['glass', 'Glass'], ['aero', 'Gloss'], ['vibrant', 'Clear'],
  ['paper', 'Paper'], ['midnight', 'Midnight'], ['amoled', 'AMOLED'], ['acrylic', 'Acrylic'], ['amethyst', 'Amethyst']];
export const PANEL_ANIMATIONS = [['fade', 'Fade', 170], ['pop', 'Pop', 210], ['spring', 'Spring', 330], ['zoom', 'Zoom', 280],
  ['unfold', 'Unfold', 260], ['expand', 'Expand', 210], ['rise', 'Rise', 280], ['drop', 'Drop', 330], ['slide', 'Slide', 210],
  ['swing', 'Swing', 330], ['flip', 'Flip', 280], ['tilt', 'Tilt', 280], ['blinds', 'Blinds', 260], ['tide', 'Tide', 260],
  ['iris', 'Iris', 260], ['settle', 'Settle', 330]];
const THEME_LABEL = Object.fromEntries(PANEL_THEMES);
const ANIM_LABEL = Object.fromEntries(PANEL_ANIMATIONS.map(([id, l]) => [id, l]));
const ANIM_MS = Object.fromEntries(PANEL_ANIMATIONS.map(([id, , ms]) => [id, ms]));

const PANEL_THEME_DESC = 'How the Deck, the Quick panel and the long-press menu are drawn. Gloss, Clear and Paper are pale with dark text on them. Frosted, Glass, Gloss, Clear, Acrylic and Amethyst blur the screen behind the panel, which needs Android 12 or newer; where the system will not blur, the panel stays translucent.';
const DECK_ANIM_DESC = 'How the Deck arrives. It leaves the same way, played backwards. Tap one to watch it open and close on the preview above. The long-press menu and the Quick panel open with the same choice.';
const QUICK_DIAL_INTRO = 'One-tap buttons in the Deck that call someone, or open your SMS, WhatsApp or Telegram chat with them. Pick a contact, or type a name and a number.';
const COUNTRY_HINT = 'WhatsApp and Telegram need the country code. Start the number with +, or set the country code below.';

// Package names under the made-up apps, for the App shortcuts list (the app itself is left out).
export const APP_PACKAGES = {
  browser: 'app.horizon.browser', calendar: 'com.android.calendar', camera: 'com.android.camera', clock: 'com.android.deskclock',
  fitness: 'app.stride.fitness', games: 'app.playland.games', mail: 'app.postbox.mail', maps: 'app.wayfinder.maps',
  messages: 'app.chitchat.messages', music: 'app.tunes.music', notes: 'app.jotter.notes', phone: 'com.android.dialer',
  photos: 'app.lumen.photos', podcasts: 'app.airwaves.podcasts', reader: 'app.pageturn.reader', settings: 'com.android.settings',
  video: 'app.streambox.video', weather: 'app.skyline.weather',
};

// Where a quick-dial button opens. 'call' (Deck's people.via) and 'dial' (the app's id) are the same.
const VIA = { call: 'Phone', sms: 'SMS', whatsapp: 'WhatsApp', telegram: 'Telegram' };
const normVia = (v) => (v === 'dial' || !VIA[v] ? 'call' : v);
const PROVIDERS = [['google', 'Google'], ['youtube', 'YouTube'], ['maps', 'Maps'], ['play', 'Play Store'], ['wikipedia', 'Wikipedia'],
  ['duckduckgo', 'DuckDuckGo'], ['amazon', 'Amazon']];
const NUMBER_ACTIONS = [['dial', 'Open the dialer'], ['sms', 'Write an SMS'], ['whatsapp', 'WhatsApp chat'], ['telegram', 'Telegram chat']];

export const DECKSETTINGS_ICONS = [
  ...M.M3_ICONS, ...Object.values(TILES).map((t) => t.icon), ...APP_ICONS,
  'filled/edit_note', 'filled/call', 'filled/keyboard_arrow_up', 'filled/keyboard_arrow_down', 'filled/accessibility', 'filled/delete',
  'filled/close', 'outlined/backspace',
];

// --- Small helpers ------------------------------------------------------------------------------
const esc = (s) => String(s).replace(/&(?![a-z]+;)/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const hexRgb = (hex) => { const [, r, g, b] = argb(hex); return [r, g, b]; };
// A colour at alpha a (the colour's own alpha replaced, as Color.copy(alpha = a) does).
const rgbA = (hex, a) => `rgba(${hexRgb(hex).join(',')},${+a.toFixed(4)})`;
// '#AARRGGBB' (or '#RRGGBB') as CSS, its alpha kept.
const cssA = (hex) => { const [a, r, g, b] = argb(hex); return `rgba(${r},${g},${b},${+(a / 255).toFixed(4)})`; };

// One line inheriting bodyLarge: trimmed to the font's height (1.172 em).
const t1 = (s, size, weight = 400, color = C.onSurface, extra = '') =>
  `<div style="font:${weight} ${size}px/1.172 ${F};letter-spacing:.5px;color:${color};${extra}">${s}</div>`;
// Wrapping text at `lh` pitch with bodyLarge's Trim.Both: the first top and last bottom trimmed.
const tw = (s, size, lh, weight = 400, color = C.onSurface, extra = '') => {
  const m = ((1.172 * size - lh) / 2).toFixed(2);
  return `<div style="font:${weight} ${size}px/${lh}px ${F};letter-spacing:.5px;color:${color};margin:${m}px 0;${extra}">${s}</div>`;
};
const gap = (px) => `<div style="height:${px}px;flex:none"></div>`;
const wgap = (px) => `<div style="width:${px}px;flex:none"></div>`;

// M3 small TopAppBar: transparent, 64 dp, title 22 sp onPrimaryContainer. data-targets: back, help.
function topBar(title, { status = 32, help = false, helpOn = false } = {}) {
  return `<div style="height:${status + 64}px;padding-top:${status}px;display:flex;align-items:center;flex:none">
      <div data-target="back" style="width:48px;height:48px;margin-left:4px;display:grid;place-items:center;flex:none">${icon('filled/arrow_back', 24, C.onSurface)}</div>
      <div style="flex:1;min-width:0;padding-left:4px;font:400 22px/28px ${F};color:${C.onPrimaryContainer};white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${title}</div>
      ${help ? `<div data-target="help" style="width:48px;height:48px;display:grid;place-items:center;flex:none">${icon('outlined/help_outline', 24, helpOn ? C.primary : C.onSurfaceVariant)}</div>` : ''}
      <div style="width:4px;flex:none"></div></div>`;
}

// The page: top bar, then a column (flex) that ends `bottom` dp above the screen's foot.
const page = (bar, body, { bottom = NAV, overlay = '' } = {}) => M.screen(`
    <div style="position:absolute;left:0;right:0;top:0;bottom:${bottom}px;display:flex;flex-direction:column">${bar}${body}</div>${overlay}`);
const scrollCol = (inner, scroll, pad = '16px') => `<div class="scroll" style="flex:1;min-height:0;position:relative;overflow:hidden">
    <div class="scroll-content" style="padding:${pad};transform:translateY(${-scroll}px)">${inner}</div></div>`;

const card = (inner, pad = '4px 0') => `<div style="border-radius:16px;background:${C.row};padding:${pad}">${inner}</div>`;
const rowDivider = () => `<div style="margin:0 16px;height:1px;background:rgba(209,213,219,.15)"></div>`;
const sep = () => M.divider(); // 12 · 1 dp onSurface@.1 · 12
const sectionTitle = (s) => `<div style="padding:0 0 16px 4px;font:700 13px/1.172 ${F};letter-spacing:1.2px;color:${C.primary}">${s}</div>`;

// M3 IconButton: 48 dp, 24 dp icon in onBackground (38 % when disabled).
const iconButton = (target, glyph, { enabled = true, color = C.onBackground, size = 24 } = {}) =>
  `<div data-target="${target}" style="width:48px;height:48px;display:grid;place-items:center;flex:none">${icon(glyph, size, enabled ? color : rgbA(color, 0.38))}</div>`;

// M3 Button / OutlinedButton with 12 dp corners: 40 dp tall in a 48 dp touch box, labelLarge.
function button(label, { target, filled = true, enabled = true, grow = false }) {
  const bg = filled ? (enabled ? C.primary : 'rgba(31,41,55,.12)') : 'transparent';
  const fg = enabled ? (filled ? C.onPrimary : C.primary) : 'rgba(31,41,55,.38)';
  const edge = filled ? '' : `box-shadow:inset 0 0 0 1px ${enabled ? C.outlineVariant : 'rgba(31,41,55,.12)'};`;
  return `<div data-target="${target}" style="${grow ? 'flex:1;min-width:0;' : 'flex:none;'}height:48px;display:flex;align-items:center">
      <div style="height:40px;width:100%;min-width:58px;padding:0 24px;border-radius:12px;background:${bg};${edge}display:flex;align-items:center;justify-content:center;
        font:500 14px/20px ${F};letter-spacing:.1px;color:${fg};white-space:nowrap">${label}</div></div>`;
}

// M3 Checkbox (20 dp box, 2 dp corners and outline) in its 48 dp touch box.
function checkbox(checked, { color = C.primary, mark = C.onPrimary, border = C.onSurfaceVariant, target = '' } = {}) {
  return `<div ${target ? `data-target="${target}" ` : ''}style="width:48px;height:48px;display:grid;place-items:center;flex:none">
      <svg width="20" height="20" viewBox="0 0 20 20" style="display:block">${checked
    ? `<rect width="20" height="20" rx="2" fill="${color}"/><path d="M4 10L8 14L16 6" fill="none" stroke="${mark}" stroke-width="2" stroke-linecap="square"/>`
    : `<rect x="1" y="1" width="18" height="18" rx="1" fill="none" stroke="${border}" stroke-width="2"/>`}</svg></div>`;
}

// A caret, 2 dp wide.
const caretBar = (color, hgt = 19) => `<span class="caret" style="display:inline-block;width:2px;height:${hgt}px;background:${color};margin-left:1px;vertical-align:middle"></span>`;

// M3 OutlinedTextField on a light page. A label floats onto the border (with its notch) once the
// field is focused or has text; with a label the field gets OutlinedTextField's 8 dp top padding.
// `textSize`/`textLh`/`textLs` are the input's style (LocalTextStyle: bodyLarge on a screen,
// bodyMedium inside a dialog).
function textField({ target, label = '', value = '', placeholder = '', leading = '', prefix = '', supporting = '', radius = 4,
  focused = false, caret = true, multiline = false, textSize = 16, textLh = 24, textLs = 0.5, trim = true }) {
  const floated = !!label && (focused || value !== '');
  const bw = focused ? 2 : 1;
  const bc = focused ? C.primary : C.outline;
  const padL = leading ? 52 : 16;
  const legend = `<legend style="margin:0;padding:0;height:16px;line-height:16px;font:400 12px/16px ${F};letter-spacing:.4px;white-space:nowrap;visibility:hidden;
      ${floated ? 'padding:0 4px;' : 'max-width:.01px;'}"><span>${floated ? label : ''}</span></legend>`;
  const border = `<fieldset style="position:absolute;left:0;right:0;top:-8px;bottom:0;margin:0;padding:0 0 0 ${12 - bw}px;min-width:0;pointer-events:none;
      border:${bw}px solid ${bc};border-radius:${radius}px">${legend}</fieldset>`;
  const lineM = trim ? ((1.172 * textSize - textLh) / 2).toFixed(2) : 0;
  const shown = value !== '' ? esc(value).replace(/\n/g, '<br>') : '';
  const ink = value !== '' ? C.onSurface : C.onSurfaceVariant;
  let content = '';
  if (value !== '' || (focused && !placeholder)) {
    content = `${prefix && floated ? `<span style="color:${C.onSurfaceVariant}">${prefix}</span><span style="display:inline-block;width:2px"></span>` : ''}${shown}${focused && caret ? caretBar(C.primary, Math.round(1.172 * textSize)) : ''}`;
  } else if (placeholder && (!label || focused)) {
    content = `${focused && caret ? caretBar(C.primary, Math.round(1.172 * textSize)) : ''}<span style="color:${C.onSurfaceVariant}">${placeholder}</span>`;
  }
  const textStyle = `font:400 ${textSize}px/${textLh}px ${F};letter-spacing:${textLs}px;color:${ink}`;
  const inner = multiline
    ? `<div style="padding:16px 16px 16px ${padL}px"><div style="${textStyle};margin:${lineM}px 0">${content || '&nbsp;'}</div></div>`
    : `<div style="position:absolute;left:${padL}px;right:16px;top:0;bottom:0;display:flex;align-items:center;white-space:nowrap;overflow:hidden"><div style="${textStyle}">${content}</div></div>`;
  const labelEl = !label ? '' : floated
    ? `<div style="position:absolute;left:16px;top:-8px;font:400 12px/16px ${F};letter-spacing:.4px;color:${focused ? C.primary : C.onSurfaceVariant};white-space:nowrap">${label}</div>`
    : `<div style="position:absolute;left:${padL}px;top:0;bottom:0;display:flex;align-items:center;font:400 16px/24px ${F};letter-spacing:.5px;color:${C.onSurfaceVariant}">${label}</div>`;
  return `<div data-target="${target}" style="padding-top:${label ? 8 : 0}px">
      <div style="position:relative;min-height:56px${multiline ? '' : ';height:56px'}">${border}
        ${leading ? `<div style="position:absolute;left:0;top:0;width:48px;height:56px;display:grid;place-items:center">${icon(leading, 24, C.onSurfaceVariant)}</div>` : ''}
        ${inner}${labelEl}</div>
      ${supporting ? `<div style="padding:4px 16px 0;font:400 12px/16px ${F};letter-spacing:.4px;color:${C.onSurfaceVariant}">${supporting}</div>` : ''}</div>`;
}

// AlertDialog (24 dp corners on surface, title titleLarge in primary), over the platform's 60 %
// dim, about 95 % of the screen wide (the platform's dialog_min_width_minor), fading in with k.
// Centred between the status bar and the navigation bar, or the top of a keyboard `keyboard` dp
// tall (the dialog window resizes for the IME). data-targets: cancel, save (the confirm button).
function alertDialog({ title, body, confirm, confirmEnabled = true, k = 1, status = 32, keyboard = 0 }) {
  const a = clamp(k);
  return `<div class="dialog" style="position:absolute;inset:0;z-index:40${a <= 0 ? ';visibility:hidden' : ''}">
      <div class="scrim" style="position:absolute;inset:0;background:rgba(0,0,0,${(0.6 * a).toFixed(3)})"></div>
      <div style="position:absolute;left:0;right:0;top:${status}px;bottom:${Math.max(NAV, keyboard)}px;display:flex;align-items:center;justify-content:center">
        <div class="dialog-card" style="width:391px;background:${C.surface};border-radius:24px;padding:24px;opacity:${a};transform:scale(${lerp(0.9, 1, Ease.fastOutSlowIn(a)).toFixed(4)})">
          <div style="font:400 22px/28px ${F};color:${C.primary};padding-bottom:16px">${title}</div>
          <div style="padding-bottom:24px">${body}</div>
          <div style="display:flex;justify-content:flex-end;gap:8px">${button('Cancel', { target: 'cancel', filled: false })}${button(confirm, { target: 'save', enabled: confirmEnabled })}</div>
        </div></div></div>`;
}

// A Surface chip (12 dp corners, primaryContainer when on) with M3's 48 dp touch height.
// `inDialog`: the text inherits bodyMedium (20 dp line) rather than the trimmed bodyLarge.
// A chip squeezed by the Row wraps its words onto a second line, at the inherited line's pitch.
function surfaceChip(target, label, on, { padX = 12, inDialog = false, last = false } = {}) {
  const font = inDialog ? `400 13px/20px ${F};letter-spacing:.25px` : `400 13px/24px ${F};letter-spacing:.5px`;
  const trim = inDialog ? '' : `margin:${((1.172 * 13 - 24) / 2).toFixed(2)}px 0;`;
  return `<div data-target="${target}" style="min-height:48px;display:flex;align-items:center;${last ? 'flex:0 1 auto;min-width:0' : 'flex:none'}">
      <div style="border-radius:12px;padding:8px ${padX}px;background:${on ? C.primaryContainer : C.surfaceVariant};color:${on ? C.onPrimaryContainer : C.onSurfaceVariant}">
        <div style="font:${font};${trim}">${label}</div></div></div>`;
}
// ChipRows: chunks of three in a Row (top-aligned, 8 dp apart, 8 dp under each); like Compose's
// Row, a chip that does not fit gets what is left and wraps.
const chipRows = (items, n = 3) => {
  const rows = [];
  for (let i = 0; i < items.length; i += n) rows.push(items.slice(i, i + n));
  return rows.map((r) => `<div style="display:flex;align-items:flex-start;gap:8px;padding-bottom:8px">${r.join('')}</div>`).join('');
};

// ActionSettingItem: 46 dp tile, label, value in primary, description, chevron.
function actionItem({ target, glyph, label, value, desc }) {
  return `<div data-target="${target}" style="display:flex;align-items:center;padding:6px 0;border-radius:14px">
      <div style="width:46px;height:46px;border-radius:14px;flex:none;background:rgba(224,231,255,.6);display:grid;place-items:center">${icon(glyph, 24, C.primary)}</div>
      ${wgap(14)}
      <div style="flex:1;min-width:0">${t1(label, 16, 600)}
        ${t1(value, 14, 500, C.primary, 'padding-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis')}
        ${desc ? M.bodySmall(desc, C.onSurfaceVariant, 'padding-top:2px') : ''}</div>
      ${wgap(8)}${icon('filled/keyboard_arrow_right', 22, C.outline)}</div>`;
}

// SettingSwitchItem: the whole row is the target; k is the switch (0 off .. 1 on).
function switchItem({ target, title, desc, k = 0 }) {
  return `<div data-target="${target}" style="display:flex;align-items:center;padding:6px 0;border-radius:14px">
      <div style="flex:1;min-width:0">${t1(title, 16, 600)}${M.bodySmall(desc, C.onSurfaceVariant, 'padding-top:2px')}</div>
      ${wgap(12)}${M.m3switchAt(clamp(k))}</div>`;
}

// AppearanceSection with a target on its header.
function section(target, { glyph, title, summary = '', open = false, body = '' }) {
  const tile = `<div style="width:46px;height:46px;border-radius:14px;flex:none;background:${open ? C.primary : 'rgba(224,231,255,.6)'};display:grid;place-items:center">${icon(glyph, 24, open ? C.onPrimary : C.primary)}</div>`;
  return `<div>
      <div data-target="${target}" style="display:flex;align-items:center;padding:12px 14px;background:${C.row};border-radius:${open ? '20px 20px 6px 6px' : '20px'}">
        ${tile}${wgap(14)}
        <div style="flex:1;min-width:0">${t1(title, 16, 600)}${summary ? t1(summary, 14, 400, C.onSurfaceVariant, 'padding-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis') : ''}</div>
        <div style="transform:rotate(${open ? 180 : 0}deg);flex:none">${icon('filled/expand_more', 24, C.outline)}</div>
      </div>
      ${open ? `<div class="section-body" style="margin-top:3px;background:${C.row};border-radius:6px 6px 20px 20px;padding:16px">${body}</div>` : ''}</div>`;
}

// SliderControl with targets: slider-<x> (the control), -minus, -plus, -track, -thumb. The track
// is the full width with the 4 dp thumb's centre kept 2 dp inside each end (SlimTrack).
function slider(name, label, value, fraction, { width = 348, atStart = false, atEnd = false, dragging = false } = {}) {
  const f = clamp(fraction);
  const cx = 2 + (width - 4) * f;
  const nudge = (g, on, t) => `<div data-target="slider-${name}-${t}" style="width:28px;height:28px;border-radius:50%;flex:none;background:${on ? 'rgba(79,70,229,.10)' : 'transparent'};display:grid;place-items:center">${icon(g, 14, on ? C.primary : 'rgba(79,70,229,.3)')}</div>`;
  const th = dragging ? 22.5 : 18;
  return `<div data-target="slider-${name}" style="height:56px">
      <div style="height:28px;display:flex;align-items:center;gap:2px">
        ${t1(label, 15, 500, C.onSurface, 'flex:1;min-width:0')}${nudge('filled/remove', !atStart, 'minus')}
        <div style="min-width:48px;text-align:center;font:700 12px/1.172 ${F};letter-spacing:.5px;color:${C.primary}">${value}</div>${nudge('filled/add', !atEnd, 'plus')}</div>
      <div data-target="slider-${name}-track" style="position:relative;height:28px;width:${width}px;overflow:hidden;border-radius:14px">
        <div style="position:absolute;left:0;right:0;top:12px;height:4px;border-radius:2px;background:rgba(79,70,229,.18)"></div>
        <div style="position:absolute;left:0;width:${cx.toFixed(2)}px;top:12px;height:4px;border-radius:2px;background:${C.primary}"></div>
        <div data-target="slider-${name}-thumb" style="position:absolute;left:${(cx - (dragging ? 3 : 2)).toFixed(2)}px;top:${14 - th / 2}px;width:${dragging ? 6 : 4}px;height:${th}px;border-radius:3px;background:${C.primary}"></div>
      </div></div>`;
}

// --- Picture rows (PanelThemeSelector, PanelAnimationSelector) ---------------------------------
// PictureTile: 76 x 88 picture, 18 dp corners, check badge when selected, label in a 34 dp box 6 dp under it.
function pictureTile(target, label, inner, selected, attrs = '') {
  return `<div data-target="${target}" ${attrs} style="width:76px;flex:none">
      <div style="position:relative;width:76px;height:88px;border-radius:18px;background:${C.surfaceContainerHigh};overflow:hidden">
        ${inner}
        <div style="position:absolute;inset:0;border-radius:18px;box-shadow:inset 0 0 0 ${selected ? 2 : 1}px ${selected ? C.primary : 'rgba(229,231,235,.6)'};pointer-events:none"></div>
        ${selected ? `<div style="position:absolute;right:6px;top:6px;width:18px;height:18px;border-radius:50%;background:${C.primary};display:grid;place-items:center">${icon('filled/check', 12, C.onPrimary)}</div>` : ''}
      </div>
      <div style="padding-top:6px"><div style="height:34px;text-align:center;font:${selected ? 600 : 500} 12px/16px ${F};letter-spacing:.5px;color:${selected ? C.primary : C.onSurfaceVariant}">${label}</div></div></div>`;
}

// A LazyRow of tiles 10 dp apart, scrolled `scroll` px; by default where the app opens it, with
// the tile before the chosen one at the start.
function pictureRow(tiles, selIndex, scroll, width = 348) {
  const total = tiles.length * 76 + (tiles.length - 1) * 10;
  const max = Math.max(0, total - width);
  const x = scroll == null ? clamp(Math.max(0, selIndex - 1) * 86, 0, max) : clamp(scroll, 0, max);
  return `<div class="picture-row" data-scroll="${x}" data-max-scroll="${max}" style="position:relative;overflow:hidden;width:100%">
      <div style="display:flex;gap:10px;transform:translateX(${-x}px)">${tiles.join('')}</div></div>`;
}

// ThemePicture: the wallpaper and its two blobs, a 66 % x 70 % panel in the menu palette's
// surface (blurred styles repaint the blobs as soft washes inside it, lit ones add the sheen),
// three rows of chip and bar, and the edge (lit rim or 1 dp border).
const THEME_PICS = {
  solid: { surface: '#F41C1C20', on: '#F2FFFFFF', dim: '#B8FFFFFF', chip: '#1AFFFFFF', border: '#1FFFFFFF', blur: 0 },
  frosted: { surface: '#A815161B', on: '#F5FFFFFF', dim: '#C2FFFFFF', chip: '#24FFFFFF', border: '#2EFFFFFF', blur: 28 },
  glass: { surface: '#5E14141A', on: '#FFFFFFFF', dim: '#CCFFFFFF', chip: '#2EFFFFFF', lit: true, blur: 40 },
  aero: { surface: '#8CD6E6F4', on: '#F2101820', dim: '#A6101820', chip: '#59FFFFFF', lit: true, light: true, blur: 36 },
  vibrant: { surface: '#A6F7F7FA', on: '#F01B1B1F', dim: '#9E1B1B1F', chip: '#14000000', border: '#66FFFFFF', light: true, blur: 44 },
  paper: { surface: '#F7F8F7F4', on: '#F21A1A1E', dim: '#9E1A1A1E', chip: '#10000000', border: '#24000000', light: true, blur: 0 },
  midnight: { surface: '#F50D1330', on: '#F5E8ECFF', dim: '#B8C9D1F5', chip: '#2E6C7CFF', border: '#3D6C7CFF', blur: 0 },
  amoled: { surface: '#FF000000', on: '#FFFFFFFF', dim: '#B3FFFFFF', chip: '#1FFFFFFF', border: '#33FFFFFF', blur: 0 },
  acrylic: { surface: '#A82B2430', on: '#F5FFF7F0', dim: '#C2FFEFE3', chip: '#26FFE9D6', border: '#33FFE9D6', blur: 32 },
  amethyst: { surface: '#6B3B2463', on: '#FFFFFFFF', dim: '#D1F1E6FF', chip: '#33E7D4FF', lit: true, blur: 40 },
};
let uid = 0;
export function themePicture(id) {
  const P = THEME_PICS[id] || THEME_PICS.solid;
  const u = `dsp${++uid}`;
  const w = 66, hh = 78, pw = w * 0.66, ph = hh * 0.7, L = (w - pw) / 2, T = (hh - ph) / 2;
  const k = P.light ? 0.45 : 1;
  const wa = (a) => `rgba(255,255,255,${+(a * k).toFixed(4)})`;
  const blobs = [[0.2, 0.22, 0.3, '#FF8A5B'], [0.84, 0.8, 0.34, '#26C6B0']];
  let defs = `<linearGradient id="${u}w" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="${w}" y2="${hh}"><stop offset="0" stop-color="#D9CCFF"/><stop offset="1" stop-color="#9DB2FA"/></linearGradient>
      <clipPath id="${u}b"><rect width="${w}" height="${hh}" rx="14"/></clipPath>
      <clipPath id="${u}p"><rect x="${L}" y="${T}" width="${pw}" height="${ph}" rx="10"/></clipPath>`;
  let panel = '';
  if (P.blur) {
    const soft = P.blur * 0.45;
    panel += `<rect width="${w}" height="${hh}" fill="url(#${u}w)"/>`;
    blobs.forEach(([x, y, r, c], i) => {
      const R = r * w + soft;
      defs += `<radialGradient id="${u}r${i}" gradientUnits="userSpaceOnUse" cx="${x * w}" cy="${y * hh}" r="${R}"><stop offset="0" stop-color="${c}" stop-opacity=".95"/><stop offset=".45" stop-color="${c}" stop-opacity=".6"/><stop offset="1" stop-color="${c}" stop-opacity="0"/></radialGradient>`;
      panel += `<circle cx="${x * w}" cy="${y * hh}" r="${R}" fill="url(#${u}r${i})"/>`;
    });
  }
  panel += `<rect x="${L}" y="${T}" width="${pw}" height="${ph}" fill="${cssA(P.surface)}"/>`;
  if (P.lit) {
    defs += `<linearGradient id="${u}s" gradientUnits="userSpaceOnUse" x1="0" y1="${T}" x2="0" y2="${T + ph * 0.5}"><stop offset="0" stop-color="${wa(0.18)}"/><stop offset=".55" stop-color="${wa(0.03)}"/><stop offset="1" stop-color="rgba(255,255,255,0)"/></linearGradient>
      <linearGradient id="${u}c" gradientUnits="userSpaceOnUse" x1="0" y1="${T + ph * 0.8}" x2="0" y2="${T + ph}"><stop offset="0" stop-color="rgba(255,255,255,0)"/><stop offset="1" stop-color="${wa(0.1)}"/></linearGradient>
      <linearGradient id="${u}e" gradientUnits="userSpaceOnUse" x1="0" y1="${T}" x2="0" y2="${T + ph}"><stop offset="0" stop-color="${wa(0.65)}"/><stop offset=".35" stop-color="${wa(0.24)}"/><stop offset=".75" stop-color="${wa(0.08)}"/><stop offset="1" stop-color="${wa(0.3)}"/></linearGradient>`;
    panel += `<rect width="${w}" height="${hh}" fill="url(#${u}s)"/><rect width="${w}" height="${hh}" fill="url(#${u}c)"/>`;
  }
  for (let row = 0; row < 3; row++) {
    const cy = T + ph * (0.26 + row * 0.24);
    const cx = L + 7 + 4.5;
    const start = cx + 4.5 + 4;
    const len = (L + pw - 6 - start) * (row === 1 ? 0.65 : 1);
    panel += `<circle cx="${cx}" cy="${cy}" r="4.5" fill="${cssA(P.chip)}"/><circle cx="${cx}" cy="${cy}" r="${4.5 * 0.42}" fill="${rgbA(P.on, 0.9)}"/>
      <rect x="${start}" y="${cy - 1.75}" width="${len}" height="3.5" rx="1.75" fill="${cssA(row === 0 ? P.on : P.dim)}"/>`;
  }
  const edge = P.lit
    ? `<rect x="${L}" y="${T}" width="${pw}" height="${ph}" rx="10" fill="none" stroke="url(#${u}e)" stroke-width="1.2"/>`
    : `<rect x="${L}" y="${T}" width="${pw}" height="${ph}" rx="10" fill="none" stroke="${cssA(P.border)}" stroke-width="1"/>`;
  return `<svg width="${w}" height="${hh}" viewBox="0 0 ${w} ${hh}" style="position:absolute;left:5px;top:5px;display:block"><defs>${defs}</defs>
      <g clip-path="url(#${u}b)"><rect width="${w}" height="${hh}" fill="url(#${u}w)"/>
        ${blobs.map(([x, y, r, c]) => `<circle cx="${x * w}" cy="${y * hh}" r="${r * w}" fill="${c}"/>`).join('')}
        <g clip-path="url(#${u}p)">${panel}</g>${edge}</g></svg>`;
}

// OpeningPicture: a 4 x 24 bar at the right edge and a 34 x 56 mini panel 13 dp in from it.
// Static at rest by default; `ms` (0..2400, the row's shared loop) poses it the way the app's
// loop does: the entrance 3x slower, travel x 0.35, and the last 260 ms faded out. The mini
// panel is `.anim-mini[data-anim=id]`, for anything that wants to animate it.
export function animationPicture(id, ms = null) {
  let pose = '';
  if (ms != null) {
    const f = entranceFrame(id, clamp(ms / ((ANIM_MS[id] || 210) * 3)), false);
    const fade = 1 - clamp((ms - (2400 - 260)) / 260);
    const clip = f.revealFrom > 0 || f.revealTo < 1 ? `clip-path:inset(${(f.revealFrom * 100).toFixed(2)}% 0 ${((1 - f.revealTo) * 100).toFixed(2)}% 0 round 9px);` : '';
    pose = `opacity:${(f.alpha * fade).toFixed(3)};transform-origin:${f.ox * 100}% ${f.oy * 100}%;${clip}
      transform:perspective(720px) translate(${(f.tx * 0.35).toFixed(2)}px,${(f.ty * 0.35).toFixed(2)}px) rotateX(${f.rx}deg) rotateY(${f.ry}deg) rotate(${f.rz}deg) scale(${f.sx},${f.sy});`;
  }
  return `${M.tileWallpaper()}
      <div style="position:absolute;left:67px;top:32px;width:4px;height:24px;background:#17171C;border-radius:2px 0 0 2px"></div>
      <div class="anim-mini" data-anim="${id}" style="position:absolute;left:29px;top:16px;width:34px;height:56px;border-radius:9px;background:rgba(29,27,43,.922);
          padding:7px 6px;display:flex;flex-direction:column;gap:5px;${pose}">
        ${[1, 0.7, 1, 0.7].map((x) => `<div style="height:6px;width:${x * 100}%;border-radius:3px;background:rgba(255,255,255,.26);flex:none"></div>`).join('')}</div>`;
}

// The panel-style picker row (static). data-targets: theme-<id> for the ten styles
// (solid, frosted, glass, aero = "Gloss", vibrant = "Clear", paper, midnight, amoled, acrylic, amethyst).
export function deckThemeRow(selected = 'solid', { scroll = null, width = 348 } = {}) {
  const tiles = PANEL_THEMES.map(([id, label]) => pictureTile(`theme-${id}`, label, themePicture(id), id === selected, `data-theme="${id}"`));
  return pictureRow(tiles, PANEL_THEMES.findIndex(([id]) => id === selected), scroll, width);
}

// The opening-animation picker row (static unless `ms` is given). data-targets: anim-<id> for
// fade, pop, spring, zoom, unfold, expand, rise, drop, slide, swing, flip, tilt, blinds, tide, iris, settle.
export function deckAnimationRow(selected = 'slide', { scroll = null, width = 348, ms = null } = {}) {
  const tiles = PANEL_ANIMATIONS.map(([id, label]) => pictureTile(`anim-${id}`, label, animationPicture(id, ms), id === selected, `data-anim="${id}"`));
  return pictureRow(tiles, PANEL_ANIMATIONS.findIndex(([id]) => id === selected), scroll, width);
}

// --- The Deck's palette (DeckPalette), for the preview strip ----------------------------------
export function deckPalette(theme = 'solid', { background = '#15161A', opacity = 238, accent = '#FFFFFF' } = {}) {
  const th = THEMES[theme] || THEMES.solid;
  const surf = th.forced ? argb(th.forced) : [opacity, ...hexRgb(background)];
  const alpha = (surf[0] / 255) * (th.forced ? 1 : th.surfaceAlpha);
  const light = luminance(surf) > 0.5;
  const on = light ? '#111111' : '#FFFFFF';
  const acc = light && luminance(argb(accent)) > 0.45 ? '#111111' : accent;
  const onAccent = luminance(argb(acc)) > 0.5 ? '#111111' : '#FFFFFF';
  return {
    bg: `rgba(${surf.slice(1).join(',')},${+alpha.toFixed(4)})`, on, subtle: rgbA(on, 0.62), accent: acc, onAccent,
    chip: rgbA(acc, light ? 0.1 : 0.14), avatar: rgbA(acc, 0.22), divider: rgbA(on, 0.18), lit: !!th.lit, pale: light,
  };
}

// =================================================================================================
// 9.1 Deck
// =================================================================================================
// data-targets: back, help, preview (the stage), preview-strip; section headers section-content,
// section-size, section-colours, section-animation, section-behaviour; Content: row-tiles, row-apps,
// row-quick-dial, row-search, row-notes, tools-first (the "Tools above shortcuts" switch row);
// Size & shape: slider-width, slider-height, slider-corner; Colours: theme-<id>, color-background,
// slider-opacity, color-accent; Animation: anim-<id>, slider-speed; Behaviour: autoclose-never,
// autoclose-5, autoclose-10, autoclose-20, autoclose-30. Every slider also has slider-<x>-minus,
// -plus, -track and -thumb.
//
// open: the sections expanded (Content is, on arrival). counts: { tiles, apps, contacts, notes }.
// tiles: the enabled tiles in order (the preview strip shows the first four). toolsFirst: 0..1.
// preview: the preview strip's entrance (0 hidden .. 1 at rest) for the picked animation.
// themeScroll / animScroll: the picture rows' sideways scroll in px (default: where the app opens them).
// themeRow / animRow: what draws the two picture rows, (selected, { scroll }) => HTML; the static
// deckThemeRow / deckAnimationRow by default, so animated versions can be dropped in.
// The app has no on/off switch for the Deck: it opens from any gesture bound to "Open deck".
export function deckScreen({
  status = 32, scroll = 0, open = ['content'], theme = 'solid', animation = 'slide', counts = {}, tiles = DEFAULT_TILES,
  toolsFirst = 0, tileNote = false, searchNote = false, width = 64, height = 0.6, corner = 30, background = '#15161A', opacity = 238,
  accent = '#FFFFFF', speed = 1, autoClose = 10, themeScroll = null, animScroll = null, preview = 1, demo = false,
  themeRow = deckThemeRow, animRow = deckAnimationRow,
} = {}) {
  const opened = new Set(Array.isArray(open) ? open : [open]);
  const n = { tiles: tiles.length, apps: 0, contacts: 0, notes: 0, ...counts };
  const tilesValue = `${n.tiles} of 18 on`;
  const pct = (x) => Math.floor(x * 100 + 1e-4);
  const opPct = Math.floor((opacity / 255) * 100 + 1e-4);
  const speedLabel = `${(Math.round(speed * 10) / 10).toFixed(1)}×`;

  // The pinned preview: the stage with the strip as the Deck draws it, tiles only, never lit.
  const pal = deckPalette(theme, { background, opacity, accent });
  const W = 412, Ws = W - 32, Hs = clamp(0.27 * 915, 212, 320);
  const stage = M.previewStage(Ws, Hs, (g) => {
    const boxH = g.glass.h / g.scale;
    const sh = Math.min(915 * height, boxH * 0.8);
    const count = Math.max(1, Math.floor((sh - 20 + 6) / 50));
    const f = entranceFrame(animation, clamp(preview), false);
    const pose = preview >= 1 ? '' : `opacity:${f.alpha.toFixed(3)};transform-origin:${f.ox * 100}% ${f.oy * 100}%;
        transform:perspective(1300px) translate(${f.tx.toFixed(2)}px,${f.ty.toFixed(2)}px) rotateX(${f.rx}deg) rotateY(${f.ry}deg) rotate(${f.rz}deg) scale(${f.sx},${f.sy});
        ${f.revealFrom > 0 || f.revealTo < 1 ? `clip-path:inset(${(f.revealFrom * 100).toFixed(2)}% 0 ${((1 - f.revealTo) * 100).toFixed(2)}% 0);` : ''}`;
    return `<div class="deck-preview-strip" data-target="preview-strip" style="position:absolute;right:14px;top:${((boxH - sh) / 2).toFixed(2)}px;width:${width}px;height:${sh.toFixed(2)}px;
        border-radius:${corner}px;background:${pal.bg};overflow:hidden;${pose}">${pal.lit ? glassLayers(corner, pal.pale) : ''}
        <div style="position:absolute;left:0;right:0;top:0;padding:10px 0;display:flex;flex-direction:column;align-items:center;gap:6px">
          ${tiles.slice(0, count).map((id) => `<div style="width:44px;height:44px;border-radius:50%;background:${pal.chip};display:grid;place-items:center;flex:none">${icon(TILES[id].icon, 22, pal.accent)}</div>`).join('')}
        </div></div>`;
  });
  stage.dataset.target = 'preview';
  const pinned = `<div class="pinned" style="padding:4px 16px 12px;flex:none">${stage.outerHTML}
      ${M.bodyMedium('A side panel of shortcuts, tools &amp; search', C.onSurfaceVariant, 'padding:10px 4px 0')}</div>`;

  const divider = () => `${gap(16)}<div style="height:1px;background:rgba(209,213,219,.2)"></div>${gap(16)}`;
  const contentBody = [
    actionItem({ target: 'row-tiles', glyph: 'filled/widgets', label: 'Tiles', value: tilesValue, desc: 'Switch tiles on or off, and put them in the order you reach for them.' }),
    tileNote ? M.permissionNote(NOTE.accessibility) : '', divider(),
    actionItem({ target: 'row-apps', glyph: 'filled/apps', label: 'App shortcuts', value: `${n.apps} pinned`, desc: 'Pin apps to the Deck. Tap an app to add or remove it.' }), divider(),
    actionItem({ target: 'row-quick-dial', glyph: 'filled/call', label: 'Quick dial', value: `${n.contacts} ${n.contacts === 1 ? 'contact' : 'contacts'}`, desc: QUICK_DIAL_INTRO }), divider(),
    actionItem({ target: 'row-search', glyph: 'filled/search', label: 'Search', value: 'Sources, providers and numbers', desc: 'Apps, contacts, sums and the web' }),
    searchNote ? M.permissionNote(NOTE.phone) : '', divider(),
    actionItem({ target: 'row-notes', glyph: 'filled/edit_note', label: 'Notes', value: `${n.notes} saved`, desc: 'Jot something down and keep it' }), divider(),
    switchItem({ target: 'tools-first', title: 'Tools above shortcuts', desc: 'Off: pinned apps and quick dial come first, tools below.', k: toolsFirst }),
  ].join('');
  const sizeBody = `${slider('width', 'Width', `${Math.trunc(width)}dp`, (width - 48) / 48, { atStart: width <= 48, atEnd: width >= 96 })}${M.divider()}
      ${slider('height', 'Height', `${pct(height)}%`, (height - 0.3) / 0.65, { atStart: height <= 0.3, atEnd: height >= 0.95 })}${M.divider()}
      ${slider('corner', 'Corner radius', `${Math.trunc(corner)}dp`, corner / 48, { atStart: corner <= 0, atEnd: corner >= 48 })}`;
  const hex = (x) => '#' + x.replace('#', '').slice(-6).toUpperCase();
  const coloursBody = `${t1('Panel style', 15)}${M.bodySmall(PANEL_THEME_DESC, C.onSurfaceVariant, 'padding:4px 0 12px')}
      ${themeRow(theme, { scroll: themeScroll })}${M.divider()}
      <div data-target="color-background">${M.colorRow('Background', hex(background), hex(background))}</div>${M.divider()}
      ${slider('opacity', 'Opacity', `${opPct}%`, (opacity - 60) / 195, { atStart: opacity <= 60, atEnd: opacity >= 255 })}${M.divider()}
      <div data-target="color-accent">${M.colorRow('Icon colour', hex(accent), hex(accent))}</div>`;
  const animBody = `${t1('Opening and closing animation', 15)}${M.bodySmall(DECK_ANIM_DESC, C.onSurfaceVariant, 'padding:4px 0 12px')}
      ${animRow(animation, { scroll: animScroll })}${gap(14)}
      ${slider('speed', 'Animation speed', speedLabel, (speed - 0.4) / 2.1, { atStart: speed <= 0.4, atEnd: speed >= 2.5 })}`;
  const chips = [0, 5, 10, 20, 30].map((s) => `<div data-target="autoclose-${s === 0 ? 'never' : s}" style="height:48px;display:flex;align-items:center">${M.filterChip(s === 0 ? 'Never' : `${s} s`, s === autoClose)}</div>`).join('');
  const behaviourBody = `${t1('Auto-close', 13, 400, 'rgba(31,41,55,.6)')}${M.bodySmall('Close the Deck by itself after this long without a touch.', C.onSurfaceVariant)}
      ${gap(8)}<div style="display:flex;flex-wrap:wrap;gap:8px">${chips}</div>`;

  const sections = [
    section('section-content', { glyph: 'filled/widgets', title: 'Content', summary: tilesValue, open: opened.has('content'), body: contentBody }),
    section('section-size', { glyph: 'filled/aspect_ratio', title: 'Size &amp; shape', summary: `${Math.trunc(width)}dp · ${pct(height)}% · ${Math.trunc(corner)}dp`, open: opened.has('size'), body: sizeBody }),
    section('section-colours', { glyph: 'filled/palette', title: 'Colours', summary: `${THEME_LABEL[theme] || 'Solid'} · ${opPct}%`, open: opened.has('colours'), body: coloursBody }),
    section('section-animation', { glyph: 'filled/animation', title: 'Animation', summary: `${ANIM_LABEL[animation] || 'Pop'} · ${speedLabel}`, open: opened.has('animation'), body: animBody }),
    section('section-behaviour', { glyph: 'filled/tune', title: 'Behaviour', summary: autoClose === 0 ? 'Never' : `${autoClose} s`, open: opened.has('behaviour'), body: behaviourBody }),
  ].join(gap(12));
  // The settings scroll to the screen's foot, padded for the navigation bar inside the scroll.
  return page(topBar('Deck', { status, help: true, helpOn: demo }), `${pinned}
      ${scrollCol(`${sections}${gap(24)}${gap(NAV)}`, scroll)}`, { bottom: 0 });
}

// =================================================================================================
// 9.2 Deck tiles
// =================================================================================================
// data-targets: back; per tile: tile-<id> (its switch), tile-row-<id> (the row), up-<id>, down-<id>.
// enabled: the ids switched on, or { id: k } with k 0..1 to animate a switch. order: the user's
// order. missing: ids whose permission is missing (an enabled one shows the PermissionNote).
export function deckTilesScreen({ status = 32, scroll = 0, enabled = DEFAULT_TILES, order = TILE_ORDER, missing = [] } = {}) {
  const kOf = (id) => (Array.isArray(enabled) ? (enabled.includes(id) ? 1 : 0) : clamp(enabled[id] ?? 0));
  const last = order.length - 1;
  const rows = order.map((id, i) => {
    const k = kOf(id);
    return `<div data-target="tile-row-${id}" style="display:flex;align-items:center;padding:6px 4px 6px 16px">
        ${icon(TILES[id].icon, 24, C.primary)}${wgap(14)}
        <div style="flex:1;min-width:0">
          <div style="display:flex;align-items:center">${t1(TILES[id].label, 15, 600)}${NEEDS_A11Y.includes(id) ? `${wgap(6)}${icon('filled/accessibility', 14, C.onSurfaceVariant)}` : ''}</div>
          ${tw(TILE_DESCRIPTIONS[id], 12, 16, 400, C.onSurfaceVariant)}</div>
        ${iconButton(`up-${id}`, 'filled/keyboard_arrow_up', { enabled: i > 0 })}${iconButton(`down-${id}`, 'filled/keyboard_arrow_down', { enabled: i < last })}
        <div data-target="tile-${id}" style="height:48px;display:flex;align-items:center;flex:none">${M.m3switchAt(k)}</div></div>
      ${k >= 0.5 && missing.includes(id) && TILE_NOTE[id] ? `<div style="padding:0 16px 8px">${M.permissionNote(TILE_NOTE[id])}</div>` : ''}
      ${i < last ? rowDivider() : ''}`;
  }).join('');
  return page(topBar('Deck tiles', { status }), scrollCol(`${M.bodyMedium('Switch tiles on or off, and put them in the order you reach for them.')}
      ${gap(16)}${card(rows)}${gap(24)}`, scroll));
}

// =================================================================================================
// 9.3 App shortcuts
// =================================================================================================
// data-targets: back, search (the field), app-<key> (each row; tapping it pins or unpins),
// app-check-<key> (its checkbox). pinned: APPS keys in pinned order; query filters by label or
// package. Only the list under the search field scrolls.
export function appShortcutsScreen({ status = 32, scroll = 0, pinned = [], query = '', focused = false } = {}) {
  const q = query.trim().toLowerCase();
  const all = Object.keys(APP_PACKAGES).sort((a, b) => APPS[a].label.toLowerCase().localeCompare(APPS[b].label.toLowerCase()))
    .filter((k) => !q || APPS[k].label.toLowerCase().includes(q) || APP_PACKAGES[k].includes(q));
  const pins = pinned.filter((k) => all.includes(k));
  const others = all.filter((k) => !pinned.includes(k));
  const label = (s) => `<div style="padding:12px 0 6px 20px;font:700 11px/1.172 ${F};letter-spacing:1.2px;color:rgba(31,41,55,.5)">${s}</div>`;
  const row = (k, on) => `<div data-target="app-${k}" style="display:flex;align-items:center;padding:8px 16px">
      <div style="width:40px;height:40px;border-radius:10px;overflow:hidden;flex:none">${appIcon(k, 40)}</div>${wgap(14)}
      <div style="flex:1;min-width:0">${t1(APPS[k].label, 15, 500)}${t1(APP_PACKAGES[k], 11, 400, C.onSurfaceVariant, 'white-space:nowrap;overflow:hidden;text-overflow:ellipsis')}</div>
      ${checkbox(on, { target: `app-check-${k}` })}</div>`;
  const list = `${pins.length ? `${label('PINNED')}${pins.map((k) => row(k, true)).join('')}` : ''}${label('ALL APPS')}${others.map((k) => row(k, false)).join('')}${gap(24)}`;
  return page(topBar('App shortcuts', { status }), `
      <div style="padding:0 16px;flex:none">${M.bodyMedium('Pin apps to the Deck. Tap an app to add or remove it.')}</div>${gap(12)}
      <div style="padding:0 16px;flex:none">${textField({ target: 'search', placeholder: 'Search apps…', value: query, leading: 'filled/search', radius: 14, focused })}</div>${gap(8)}
      ${scrollCol(list, scroll, '0')}`);
}

// =================================================================================================
// 9.4 Quick dial
// =================================================================================================
// data-targets: back, pick-contact, add, contact-<i> (a row; tap = edit), contact-delete-<i>,
// call-directly (the switch row), field-country-code; with the dialog open: field-name,
// field-number, via-call (Phone), via-sms, via-whatsapp, via-telegram, cancel, save.
// contacts: [{ name, number, via: 'call'|'sms'|'whatsapp'|'telegram' }] ('dial' = 'call').
// dialog: { edit, title, name, number, via, k, focus: 'name'|'number', keyboard } — new unless
// edit (title "New quick dial" / "Edit quick dial", confirm "Add" / "Save"); k fades it in;
// keyboard: the height of a keyboard shown under it, which lifts it.
export function quickDialScreen({ status = 32, scroll = 0, contacts = [], dialog = null, directCall = 0, prefix = '', phoneNote = false, focus = null } = {}) {
  const rows = contacts.length
    ? contacts.map((c, i) => {
      const via = normVia(c.via);
      const line = via === 'call' ? esc(c.number) : `${VIA[via]} · ${esc(c.number)}`;
      return `<div data-target="contact-${i}" style="display:flex;align-items:center;padding:6px 4px 6px 16px">
          <div style="flex:1;min-width:0">${t1(esc(c.name), 15, 600)}${t1(line, 13, 400, C.onSurfaceVariant)}</div>
          ${iconButton(`contact-delete-${i}`, 'filled/delete')}</div>${i < contacts.length - 1 ? rowDivider() : ''}`;
    }).join('')
    : `<div style="padding:16px">${M.bodyMedium('No quick-dial contacts yet.')}</div>`;
  const settings = card(`${M.bodySmall('Shared with the Deck’s search.')}
      ${switchItem({ target: 'call-directly', title: 'Call directly', desc: 'Place calls from quick dial and search without opening the dialer first. Needs the Phone permission.', k: directCall })}
      ${phoneNote && directCall >= 0.5 ? M.permissionNote(NOTE.phone) : ''}${sep()}
      ${textField({ target: 'field-country-code', label: 'Country code', value: prefix, prefix: '+', radius: 12, focused: focus === 'country-code',
    supporting: 'Added to numbers typed without one, for WhatsApp and Telegram links.' })}`, '16px');
  const body = `${M.bodyMedium(QUICK_DIAL_INTRO)}${gap(16)}
      <div style="display:flex;gap:12px">${button('Pick a contact', { target: 'pick-contact', grow: true })}${button('Add', { target: 'add', filled: false, grow: true })}</div>
      ${gap(16)}${card(rows)}${gap(24)}${sectionTitle('CALLS AND CHATS')}${settings}${gap(24)}`;
  let overlay = '';
  if (dialog) {
    const d = { name: '', number: '', via: 'call', k: 1, ...dialog };
    const edit = d.edit ?? d.title === 'Edit quick dial';
    const via = normVia(d.via);
    const needsCode = (via === 'whatsapp' || via === 'telegram') && !d.number.trim().startsWith('+') && !prefix;
    const dlgField = (target, label, value, f) => textField({ target, label, value, radius: 12, focused: f, textSize: 14, textLh: 20, textLs: 0.25, trim: false });
    const chips = [['call', 'sms'], ['whatsapp', 'telegram']].map((r) => `<div style="display:flex;gap:8px;padding-bottom:8px">${r.map((id) => surfaceChip(`via-${id}`, VIA[id], id === via, { padX: 14, inDialog: true })).join('')}</div>`).join('');
    const content = `${dlgField('field-name', 'Name', d.name, d.focus === 'name')}${gap(8)}${dlgField('field-number', 'Number', d.number, d.focus === 'number')}
        ${gap(14)}<div style="font:400 14px/20px ${F};letter-spacing:.25px;color:${C.onSurface}">Opens in</div>${gap(8)}${chips}
        ${needsCode ? M.bodySmall(COUNTRY_HINT, C.error) : ''}`;
    overlay = alertDialog({ title: d.title || (edit ? 'Edit quick dial' : 'New quick dial'), body: content, confirm: edit ? 'Save' : 'Add',
      confirmEnabled: !!(d.name.trim() && d.number.trim()), k: d.k, status, keyboard: d.keyboard || 0 });
  }
  return page(topBar('Quick dial', { status }), scrollCol(body, scroll), { overlay });
}

// =================================================================================================
// 9.5 Search
// =================================================================================================
// data-targets: back, switch-apps, switch-calculator, provider-<id> (google, youtube, maps, play,
// wikipedia, duckduckgo, amazon), default-<id> (one per enabled provider), number-<id> (dial, sms,
// whatsapp, telegram), call-directly, field-country-code.
// apps / calculator / directCall: switches 0..1. providers: the enabled ids. defaultProvider,
// numberAction: ids. prefix: the country code typed.
export function searchSettingsScreen({ status = 32, scroll = 0, apps = 1, calculator = 1, providers = ['google', 'youtube', 'maps'],
  defaultProvider = 'google', numberAction = 'dial', directCall = 0, prefix = '', phoneNote = false, focus = null } = {}) {
  const lastOfRow = (i, n) => i % 3 === 2 || i === n - 1;
  const provChips = PROVIDERS.map(([id, l], i) => surfaceChip(`provider-${id}`, l, providers.includes(id), { last: lastOfRow(i, PROVIDERS.length) }));
  const enabled = PROVIDERS.filter(([id]) => providers.includes(id));
  const defChips = enabled.map(([id, l], i) => surfaceChip(`default-${id}`, l, id === defaultProvider, { last: lastOfRow(i, enabled.length) }));
  const numChips = NUMBER_ACTIONS.map(([id, l], i) => surfaceChip(`number-${id}`, l, id === numberAction, { last: lastOfRow(i, NUMBER_ACTIONS.length) }));
  const body = `${sectionTitle('SOURCES')}
      ${card(`${switchItem({ target: 'switch-apps', title: 'Installed apps', desc: 'Find and launch apps by name.', k: apps })}${sep()}
        ${switchItem({ target: 'switch-calculator', title: 'Inline calculator', desc: 'Type a sum and see the answer as you type.', k: calculator })}`, '16px')}
      ${gap(24)}${sectionTitle('WEB SEARCH')}
      ${card(`${M.bodySmall('Where a query goes when nothing on the phone matches. Each enabled provider gets a button under the search field.')}${gap(10)}
        ${chipRows(provChips)}${sep()}${t1('Default provider', 15)}${gap(8)}${chipRows(defChips)}`, '16px')}
      ${gap(24)}${sectionTitle('PHONE NUMBERS')}
      ${card(`${t1('When you type a phone number', 15)}${gap(8)}${chipRows(numChips)}${sep()}
        ${switchItem({ target: 'call-directly', title: 'Call directly', desc: 'Place calls from quick dial and search without opening the dialer first. Needs the Phone permission.', k: directCall })}
        ${phoneNote && directCall >= 0.5 ? M.permissionNote(NOTE.phone) : ''}${sep()}
        ${textField({ target: 'field-country-code', label: 'Country code', value: prefix, prefix: '+', radius: 12, focused: focus === 'country-code',
    supporting: 'Added to numbers typed without one, for WhatsApp and Telegram links.' })}`, '16px')}${gap(24)}`;
  return page(topBar('Search', { status }), scrollCol(body, scroll));
}

// =================================================================================================
// 9.6 Notes
// =================================================================================================
// data-targets: back, field-note, save-note, note-<i> (tap = copy), note-edit-<i>, note-delete-<i>;
// with the edit dialog open: field-edit, cancel, save.
// notes: [{ text, time: '10/9/26, 3:45 PM' }] newest first. draft: the text being written.
// dialog: { text, k, focused, keyboard } for "Edit note".
export function notesScreen({ status = 32, scroll = 0, draft = '', focused = false, notes = [], dialog = null } = {}) {
  const rows = notes.length
    ? notes.map((n, i) => `<div data-target="note-${i}" style="display:flex;align-items:center;padding:8px 4px 8px 16px">
          <div style="flex:1;min-width:0">${tw(esc(n.text), 15, 24)}${t1(esc(n.time || ''), 11, 400, C.onSurfaceVariant)}</div>
          ${iconButton(`note-edit-${i}`, 'filled/edit')}${iconButton(`note-delete-${i}`, 'filled/delete')}</div>${i < notes.length - 1 ? rowDivider() : ''}`).join('')
    : `<div style="padding:16px">${M.bodyMedium('No notes yet.')}</div>`;
  const body = `${M.bodyMedium('Notes saved from the Deck. Tap one to copy it, or open it to edit.')}${gap(12)}
      ${textField({ target: 'field-note', placeholder: 'Write a note…', value: draft, radius: 14, focused, multiline: true })}${gap(8)}
      <div style="display:flex;justify-content:flex-end">${button('Save', { target: 'save-note', enabled: !!draft.trim() })}</div>
      ${gap(16)}${card(rows)}${gap(24)}`;
  let overlay = '';
  if (dialog) {
    const d = { text: '', k: 1, focused: true, ...dialog };
    overlay = alertDialog({ title: 'Edit note', confirm: 'Save', k: d.k, status, keyboard: d.keyboard || 0,
      body: textField({ target: 'field-edit', value: d.text, radius: 12, focused: d.focused, multiline: true, textSize: 14, textLh: 20, textLs: 0.25, trim: false }) });
  }
  return page(topBar('Notes', { status }), scrollCol(body, scroll), { overlay });
}

// =================================================================================================
// Deck cards (DeckCards.kt) not in deck.js: content for deck.showCard(id, el), 288 dp wide.
// =================================================================================================
const pal = (p) => p || DECK_PALETTES.solid;
// DeckButton: 14 dp corners, 16 x 10 padding, 14 sp SemiBold; filled = accent (40 % when disabled).
function dBtn(text, { p, target, filled = true, enabled = true, grow = false, full = false }) {
  const bg = filled ? (enabled ? p.accent : rgbA(p.accent, 0.4)) : p.chip;
  return `<div data-target="${target}" style="${grow ? 'flex:1;' : ''}${full ? 'align-self:stretch;' : ''}border-radius:14px;padding:10px 16px;font:600 14px/1.172 ${F};
      letter-spacing:.5px;text-align:center;white-space:nowrap;background:${bg};color:${filled ? p.onAccent : p.accent}">${text}</div>`;
}
// DeckHint: 12/16 sp in subtle, 6 dp above.
const dHint = (text, p) => tw(text, 12, 16, 400, p.subtle, 'padding-top:6px');
// DeckTextField: OutlinedTextField, 56 dp, 14 dp corners, 1 dp onBackground@.25 / 2 dp accent.
function dField(p, { target, value = '', placeholder = '', focused = false, caret = true, multiline = false }) {
  const border = focused ? `2px solid ${p.accent}` : `1px solid ${rgbA(p.on, 0.25)}`;
  const cur = focused && caret ? `<span class="caret" style="display:inline-block;width:2px;height:16px;background:${p.accent};margin-left:1px;vertical-align:-3px"></span>` : '';
  const text = value
    ? `<span style="font:400 14px/1.172 ${F};color:${p.on}">${esc(value).replace(/\n/g, '<br>')}</span>${cur}`
    : `${cur}<span style="font:400 14px/1.172 ${F};letter-spacing:.5px;color:${p.subtle}">${placeholder}</span>`;
  return `<div data-target="${target}" style="position:relative;${multiline ? 'min-height:56px;padding:16px' : 'height:56px;padding:0 16px;display:flex;align-items:center'};flex:1;min-width:0">
      <div style="position:absolute;inset:0;border:${border};border-radius:14px;pointer-events:none"></div>
      <div style="line-height:1.172;${multiline ? '' : 'white-space:nowrap;overflow:hidden'}">${text}</div></div>`;
}
// The M3 Switch under the overlay's dark scheme (light for the pale styles); checked = accent.
function dSwitch(k, p) {
  const on = k >= 0.5, pale = p.on === '#111111';
  const offInk = pale ? C.outline : '#475569', offTrack = pale ? '#E6E0E9' : '#36343B';
  const size = 16 + 8 * k, left = 8 + 20 * k;
  return `<div style="position:relative;width:52px;height:32px;border-radius:16px;background:${on ? p.accent : offTrack};${on ? '' : `box-shadow:inset 0 0 0 2px ${offInk};`}flex:none">
      <div style="position:absolute;width:${size}px;height:${size}px;border-radius:50%;background:${on ? p.onAccent : offInk};left:${left + 8 - size / 2}px;top:${16 - size / 2}px"></div></div>`;
}
const dIconButton = (target, glyph, p) => `<div data-target="${target}" style="width:48px;height:48px;display:grid;place-items:center;flex:none">${icon(glyph, 18, p.subtle)}</div>`;

// Brightness. data-targets: brightness-slider, adaptive (the switch); without "Modify system
// settings": open-settings. value 0..1, auto 0..1 (the Adaptive brightness switch).
export function brightnessCard({ value = 0.6, auto = 0, allowed = true, p } = {}) {
  p = pal(p);
  if (!allowed) {
    return h(`<div>${dHint('Allow Modify system settings to control brightness', p)}${gap(8)}
        <div style="display:flex">${dBtn('Open settings', { p, target: 'open-settings' })}</div></div>`);
  }
  return h(`<div>
      <div data-target="brightness-slider" style="display:flex">${deckSlider(value, { p, width: 288 })}</div>
      <div style="display:flex;align-items:center;height:48px">${t1('Adaptive brightness', 14, 400, p.on, 'flex:1')}
        <div data-target="adaptive" style="height:48px;display:flex;align-items:center">${dSwitch(clamp(auto), p)}</div></div></div>`);
}

// ExpressionEvaluator.evaluateToText: + - * / ^ %, parentheses, unary signs, sqrt / √; null when
// it does not parse or divides by zero. Integers without ".0", otherwise up to 10 decimals.
export function evaluateToText(input) {
  const s = String(input).trim().replace(/×/g, '*').replace(/÷/g, '/').replace(/√/g, 's').replace(/(?<=[0-9)])\s*[xX]\s*(?=[0-9(])/g, '*').replace(/,/g, '').replace(/\s+/g, '');
  if (!s) return null;
  let i = 0;
  const peek = () => s[i];
  const expr = () => { let v = term(); for (;;) { if (peek() === '+') { i++; v += term(); } else if (peek() === '-') { i++; v -= term(); } else return v; } };
  const term = () => {
    let v = factor();
    for (;;) {
      if (peek() === '*') { i++; v *= factor(); } else if (peek() === '/') { i++; const d = factor(); if (d === 0) throw new Error('div0'); v /= d; } else if (peek() === '%') { i++; v %= factor(); } else return v;
    }
  };
  const factor = () => { const b = unary(); if (peek() === '^') { i++; return Math.pow(b, factor()); } return b; };
  const unary = () => (peek() === '-' ? (i++, -unary()) : peek() === '+' ? (i++, unary()) : primary());
  const primary = () => {
    const c = peek();
    if (c === undefined) throw new Error('end');
    if (c === '(') { i++; const v = expr(); if (peek() !== ')') throw new Error(')'); i++; return v; }
    if (s.startsWith('sqrt', i)) { i += 4; return Math.sqrt(primary()); }
    if (c === 's') { i++; return Math.sqrt(unary()); }
    const st = i;
    while (i < s.length && /[0-9.]/.test(s[i])) i++;
    if (st === i) throw new Error('number');
    const v = Number(s.slice(st, i));
    if (Number.isNaN(v)) throw new Error('number');
    return v;
  };
  try {
    const v = expr();
    if (i < s.length || !Number.isFinite(v)) return null;
    if (v === Math.round(v) && Math.abs(v) < 1e15) return String(Math.trunc(v));
    return v.toFixed(10).replace(/0+$/, '').replace(/\.$/, '');
  } catch { return null; }
}

// Calculator. data-targets: key-c, key-lparen, key-rparen, key-back, key-0..key-9, key-dot, key-div,
// key-mul, key-minus, key-plus, key-eq, copy, calculator-app. input: what has been typed (ASCII
// operators, as the display shows them); pressed: a key's label to draw squeezed (0.92).
// The ⌫ key is drawn with the Material backspace glyph (the font fallback on a phone is close to it).
const KEY_ID = { C: 'c', '(': 'lparen', ')': 'rparen', '⌫': 'back', '÷': 'div', '×': 'mul', '−': 'minus', '+': 'plus', '=': 'eq', '.': 'dot' };
export function calculatorCard({ input = '', pressed = null, systemApp = true, p } = {}) {
  p = pal(p);
  const preview = input ? evaluateToText(input) : null;
  const keys = [['C', '(', ')', '⌫'], ['7', '8', '9', '÷'], ['4', '5', '6', '×'], ['1', '2', '3', '−'], ['0', '.', '=', '+']];
  const ops = ['÷', '×', '−', '+', '='];
  const key = (k) => {
    const ink = k === '=' ? p.onAccent : ops.includes(k) ? p.accent : p.on;
    const face = k === '⌫' ? icon('outlined/backspace', 18, ink) : `<span style="font:500 17px/1.172 ${F};letter-spacing:.5px;color:${ink}">${k}</span>`;
    return `<div data-target="key-${KEY_ID[k] || k}" style="flex:1;height:40px;border-radius:12px;background:${k === '=' ? p.accent : p.chip};display:grid;place-items:center;
        ${pressed === k ? 'transform:scale(.92);' : ''}">${face}</div>`;
  };
  return h(`<div>
      <div class="calc-display" style="border-radius:14px;background:${p.chip};padding:10px 12px;display:flex;flex-direction:column;align-items:flex-end">
        <div style="font:400 22px/24px ${F};letter-spacing:.5px;color:${p.on};text-align:right;margin:${((1.172 * 22 - 24) / 2).toFixed(2)}px 0;word-break:break-all;max-height:48px;overflow:hidden">${esc(input || '0')}</div>
        ${preview != null ? t1(`= ${preview}`, 16, 600, p.accent) : ''}</div>
      ${gap(8)}
      ${keys.map((r) => `<div style="display:flex;gap:6px;margin-bottom:6px">${r.map(key).join('')}</div>`).join('')}
      <div style="display:flex;gap:8px">${dBtn('Copy result', { p, target: 'copy', filled: false })}${systemApp ? dBtn('Calculator app', { p, target: 'calculator-app', filled: false }) : ''}</div></div>`);
}

// Notes. data-targets: note-field, note-save, all-notes, note-<i> (tap = copy), note-delete-<i>.
// draft: the text in the field; notes: up to six strings (or { text }), newest first.
export function notesCard({ draft = '', notes = [], focused = false, caret = true, p } = {}) {
  p = pal(p);
  const list = notes.slice(0, 6).map((n) => (typeof n === 'string' ? n : n.text));
  const rows = list.length
    ? list.map((t, i) => `<div data-target="note-${i}" style="display:flex;align-items:center;padding:6px 0;border-radius:12px">
        <div style="flex:1;min-width:0;font:400 14px/24px ${F};letter-spacing:.5px;color:${p.on};margin:${((1.172 * 14 - 24) / 2).toFixed(2)}px 0;
          display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">${esc(t)}</div>
        ${dIconButton(`note-delete-${i}`, 'filled/delete', p)}</div>`).join('')
    : dHint('No notes yet. Tap a note to copy it.', p);
  return h(`<div>
      <div style="display:flex">${dField(p, { target: 'note-field', value: draft, placeholder: 'Write a note…', focused, caret, multiline: true })}</div>${gap(8)}
      <div style="display:flex;gap:8px">${dBtn('Save', { p, target: 'note-save', enabled: !!draft.trim() })}${dBtn('All notes', { p, target: 'all-notes', filled: false })}</div>
      ${gap(10)}${rows}</div>`);
}

// Checklist. data-targets: item-field, item-add, item-<i> (the row; tap toggles), item-check-<i>,
// item-delete-<i>, clear-done. items: [{ text, done }] in the order added.
export function checklistCard({ draft = '', items = [], focused = false, caret = true, p } = {}) {
  p = pal(p);
  const rows = items.map((it, i) => `<div data-target="item-${i}" style="display:flex;align-items:center">
      ${checkbox(!!it.done, { color: p.accent, mark: p.onAccent, border: p.subtle, target: `item-check-${i}` })}
      <div style="flex:1;min-width:0;font:400 14px/24px ${F};letter-spacing:.5px;margin:${((1.172 * 14 - 24) / 2).toFixed(2)}px 0;color:${it.done ? p.subtle : p.on};
        ${it.done ? 'text-decoration:line-through;' : ''}display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">${esc(it.text)}</div>
      ${dIconButton(`item-delete-${i}`, 'filled/delete', p)}</div>`).join('');
  return h(`<div>
      <div style="display:flex;align-items:center;gap:8px">${dField(p, { target: 'item-field', value: draft, placeholder: 'Add an item…', focused, caret })}
        ${dBtn('Add', { p, target: 'item-add', enabled: !!draft.trim() })}</div>${gap(6)}
      ${items.length ? rows : dHint('Nothing on the list.', p)}
      ${items.some((it) => it.done) ? `<div style="display:flex">${dBtn('Clear done', { p, target: 'clear-done', filled: false })}</div>` : ''}</div>`);
}

// Dice. data-targets: die-1, die-2, roll. dice: [a, b] (null before the first roll: both show 1
// and there is no total). angle: the roll's spin in degrees, left die +angle, right die -angle
// (diceSpin(t) gives it for t s into a roll). The element's set({ dice, angle }) redraws in place.
export const diceSpin = (t) => 360 * Ease.fastOutSlowIn(clamp(t / 0.6));
const PIPS = (() => {
  const c = 36, o = 18;
  const tl = [c - o, c - o], tr = [c + o, c - o], ml = [c - o, c], mr = [c + o, c], bl = [c - o, c + o], br = [c + o, c + o], cc = [c, c];
  return { 1: [cc], 2: [tl, br], 3: [tl, cc, br], 4: [tl, tr, bl, br], 5: [tl, tr, cc, bl, br], 6: [tl, tr, ml, mr, bl, br] };
})();
export function diceCard({ dice = null, angle = 0, p } = {}) {
  p = pal(p);
  const face = (v) => `<svg width="72" height="72" style="display:block">${PIPS[clamp(v || 1, 1, 6)].map(([x, y]) => `<circle cx="${x}" cy="${y}" r="6" fill="${p.onAccent}"/>`).join('')}</svg>`;
  const die = (i, v, a) => `<div class="die" data-target="die-${i}" style="width:72px;height:72px;border-radius:16px;background:${p.accent};transform:rotate(${a}deg)">${face(v)}</div>`;
  const el = h(`<div style="display:flex;flex-direction:column;align-items:center">
      <div style="display:flex;gap:18px">${die(1, dice?.[0], angle)}${die(2, dice?.[1], -angle)}</div>${gap(10)}
      <div class="total" style="height:${(18 * 1.172).toFixed(2)}px;font:600 18px/1.172 ${F};letter-spacing:.5px;color:${p.on}">${dice ? `Total ${dice[0] + dice[1]}` : ''}</div>${gap(8)}
      ${dBtn('Roll', { p, target: 'roll', full: true })}</div>`);
  el.set = ({ dice: d = dice, angle: a = 0 } = {}) => {
    const dies = el.querySelectorAll('.die');
    dies[0].innerHTML = face(d?.[0]); dies[1].innerHTML = face(d?.[1]);
    dies[0].style.transform = `rotate(${a}deg)`; dies[1].style.transform = `rotate(${-a}deg)`;
    el.querySelector('.total').textContent = d ? `Total ${d[0] + d[1]}` : '';
  };
  return el;
}
