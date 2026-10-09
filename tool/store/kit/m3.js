// The app's own screens are Material 3 in the colours of ui/theme/Color.kt, with the shared pieces
// its screens are built from (GestureSlots, AppearanceSection, SliderControl, SwitchControl,
// PictureRow, PreviewStage, DemoCaptionPill, PermissionNote). Each returns HTML at 1 dp = 1 px.
//
// Text that sets only a size in Compose inherits the app's bodyLarge (24 sp line, 0.5 sp tracking)
// with LineHeightStyle.Default, which trims a single line to the font's own height: that is the
// `.inh` class (line-height 1.172) used all through.
import { icon, h } from './core.js';

export const LIGHT = {
  primary: '#4F46E5', onPrimary: '#FFFFFF', primaryContainer: '#E0E7FF', onPrimaryContainer: '#312E81',
  secondaryContainer: '#F3F4F6', onSecondaryContainer: '#1F2937', tertiary: '#14B8A6',
  error: '#EF4444', errorContainer: '#FEE2E2', onErrorContainer: '#7F1D1D',
  background: '#FFFFFF', onBackground: '#111827', surface: '#FFFFFF', onSurface: '#1F2937',
  surfaceVariant: '#F3F4F6', onSurfaceVariant: '#4B5563', outline: '#D1D5DB', outlineVariant: '#E5E7EB',
  surfaceContainerHigh: '#ECE6F0', surfaceContainerHighest: '#E6E0E9', surfaceContainerLow: '#F7F2FA',
  row: 'rgba(243,244,246,.65)',
};
const C = LIGHT;

export const M3_ICONS = [
  'filled/arrow_back', 'outlined/help_outline', 'filled/keyboard_arrow_right', 'filled/expand_more', 'filled/check',
  'filled/remove', 'filled/add', 'filled/edit', 'filled/warning', 'filled/save', 'filled/widgets', 'filled/aspect_ratio',
  'filled/palette', 'filled/animation', 'filled/tune', 'filled/open_with', 'filled/menu', 'filled/apps', 'filled/search',
  'filled/arrow_forward', 'filled/view_sidebar', 'filled/flashlight_on', 'filled/play_arrow', 'filled/close',
];

// --- Text ---------------------------------------------------------------------------------------
export const text = (s, { size = 16, weight = 400, color = C.onSurface, extra = '' } = {}) =>
  `<div style="font:${weight} ${size}px/1.172 Roboto;letter-spacing:.5px;color:${color};${extra}">${s}</div>`;
export const bodyMedium = (s, color = C.onSurfaceVariant, extra = '') =>
  `<div style="font:400 14px/20px Roboto;letter-spacing:.25px;color:${color};${extra}">${s}</div>`;
export const bodySmall = (s, color = C.onSurfaceVariant, extra = '') =>
  `<div style="font:400 12px/16px Roboto;letter-spacing:.4px;color:${color};${extra}">${s}</div>`;

// --- Chrome -------------------------------------------------------------------------------------
export function topAppBar(title, { actions = [], status = 32 } = {}) {
  return `<div style="height:${status + 64}px;padding-top:${status}px;display:flex;align-items:center;position:relative">
      <div style="width:56px;height:64px;display:flex;align-items:center;padding-left:16px">${icon('filled/arrow_back', 24, C.onSurface)}</div>
      <div style="flex:1;font:400 22px/28px Roboto;color:${C.onPrimaryContainer}">${title}</div>
      ${actions.map((a) => `<div style="width:48px;height:48px;display:grid;place-items:center">${icon(a.icon, 24, a.color || C.onSurfaceVariant)}</div>`).join('')}
      <div style="width:4px"></div></div>`;
}

export const groupLabel = (s) => `<div style="padding:0 0 10px 6px;font:600 14px/1.172 Roboto;letter-spacing:.5px;color:${C.primary}">${s}</div>`;

// segmentShape: outer corners 20, seams 6, rows 3 dp apart.
export function segRadius(i, n) {
  if (n === 1) return '20px';
  if (i === 0) return '20px 20px 6px 6px';
  if (i === n - 1) return '6px 6px 20px 20px';
  return '6px';
}
export const segments = (rows) => `<div style="display:flex;flex-direction:column;gap:3px">${rows.map((r, i) => r(segRadius(i, rows.length))).join('')}</div>`;

// GestureRow / SlotRow: 46 dp glyph tile, name, action line, chevron. 70 dp tall.
export function gestureRow({ glyph, name, action, actionIcon, dimmed = false, footer = '' }) {
  return (radius) => `<div style="border-radius:${radius};background:${C.row};overflow:hidden">
      <div style="display:flex;align-items:center;padding:12px 14px">
        <div style="width:46px;height:46px;border-radius:14px;background:rgba(224,231,255,.6);display:grid;place-items:center">${glyph}</div>
        <div style="width:14px"></div>
        <div style="flex:1;min-width:0">
          ${text(name, { size: 16, weight: 600 })}
          <div style="display:flex;align-items:center;padding-top:3px;gap:6px">
            ${actionIcon ? actionIcon(16, dimmed ? C.onSurfaceVariant : C.primary) : ''}
            ${text(action, { size: 14, weight: dimmed ? 400 : 500, color: dimmed ? C.onSurfaceVariant : C.primary, extra: 'white-space:nowrap;overflow:hidden;text-overflow:ellipsis' })}
          </div>
        </div>
        ${icon('filled/keyboard_arrow_right', 22, C.outline)}
      </div>${footer}</div>`;
}

export function linkRow({ tileIcon, title, summary }) {
  return (radius) => `<div style="border-radius:${radius};background:${C.row};display:flex;align-items:center;padding:12px 14px">
      <div style="width:46px;height:46px;border-radius:14px;background:rgba(243,244,246,.8);display:grid;place-items:center">${tileIcon(24, C.onSecondaryContainer)}</div>
      <div style="width:14px"></div>
      <div style="flex:1;min-width:0">${text(title, { size: 16, weight: 600 })}${text(summary, { size: 14, color: C.onSurfaceVariant, extra: 'padding-top:2px' })}</div>
      ${icon('filled/keyboard_arrow_right', 22, C.outline)}</div>`;
}

export function settingCard({ title, description, body = '' }) {
  return (radius) => `<div style="border-radius:${radius};background:${C.row};padding:16px">
      ${text(title, { size: 16, weight: 600 })}${bodyMedium(description, C.onSurfaceVariant, 'padding:2px 0 12px')}${body}</div>`;
}

// --- Controls -----------------------------------------------------------------------------------
export function filterChip(label, selected) {
  return `<div style="height:32px;border-radius:12px;display:inline-flex;align-items:center;padding:0 16px 0 ${selected ? 8 : 16}px;gap:8px;
      ${selected ? `background:${C.secondaryContainer};` : `border:1px solid ${C.outlineVariant};`}font:500 14px/20px Roboto;letter-spacing:.1px;
      color:${selected ? C.onSecondaryContainer : C.onSurfaceVariant}">${selected ? icon('filled/check', 18, C.onSecondaryContainer) : ''}${label}</div>`;
}
export const chipRow = (chips) => `<div style="display:flex;flex-wrap:wrap;gap:16px 8px;margin-bottom:8px">${chips.join('')}</div>`;

export function m3switch(on, { dark = false } = {}) {
  if (on) return `<div style="position:relative;width:52px;height:32px;border-radius:16px;background:${C.primary};flex:none">
      <div style="position:absolute;width:24px;height:24px;border-radius:50%;background:#fff;left:24px;top:4px"></div></div>`;
  return `<div style="position:relative;width:52px;height:32px;border-radius:16px;background:${C.surfaceContainerHighest};border:2px solid ${C.outline};flex:none">
      <div style="position:absolute;width:16px;height:16px;border-radius:50%;background:${C.outline};left:6px;top:6px"></div></div>`;
}
// The switch thumb part-way (0 off .. 1 on), for animating a toggle.
export function m3switchAt(k) {
  const on = k >= 0.5;
  const size = 16 + 8 * k;
  const left = 8 + (28 - 8) * k;
  const track = on ? C.primary : C.surfaceContainerHighest;
  return `<div style="position:relative;width:52px;height:32px;border-radius:16px;background:${track};${on ? '' : `box-shadow:inset 0 0 0 2px ${C.outline};`}flex:none">
      <div style="position:absolute;width:${size}px;height:${size}px;border-radius:50%;background:${on ? '#fff' : C.outline};left:${left + 8 - size / 2}px;top:${16 - size / 2}px"></div></div>`;
}

export function switchRow(label, on, { description = '', k = null } = {}) {
  return `<div><div style="display:flex;align-items:center;padding:4px 0;gap:12px;min-height:40px">
      ${text(label, { size: 15, weight: 500, extra: 'flex:1' })}${k == null ? m3switch(on) : m3switchAt(k)}</div>
      ${description ? bodySmall(description, C.onSurfaceVariant, 'margin-top:2px') : ''}</div>`;
}

// SettingSwitchItem (Quick slider screen): title 16 SemiBold, description bodySmall.
export function settingSwitch(title, description, on) {
  return `<div style="display:flex;align-items:center;gap:12px;padding:6px 0">
      <div style="flex:1">${text(title, { size: 16, weight: 600 })}${bodySmall(description, C.onSurfaceVariant, 'margin-top:2px')}</div>${m3switch(on)}</div>`;
}

export function sliderControl(label, value, fraction, { width = 348, dragging = false } = {}) {
  const tw = width - 4;
  const cx = 2 + (tw - 4) * fraction;
  const nudge = (g) => `<div style="width:28px;height:28px;border-radius:50%;background:rgba(79,70,229,.10);display:grid;place-items:center">${icon(g, 14, C.primary)}</div>`;
  return `<div style="height:56px">
      <div style="height:28px;display:flex;align-items:center;gap:2px">
        ${text(label, { size: 15, weight: 500, extra: 'flex:1' })}${nudge('filled/remove')}
        <div style="min-width:48px;text-align:center;font:700 12px/1 Roboto;letter-spacing:.5px;color:${C.primary}">${value}</div>${nudge('filled/add')}</div>
      <div style="position:relative;height:28px;width:${width}px;overflow:hidden;border-radius:14px">
        <div style="position:absolute;left:2px;right:2px;top:12px;height:4px;border-radius:2px;background:rgba(79,70,229,.18)"></div>
        <div style="position:absolute;left:2px;width:${cx - 2}px;top:12px;height:4px;border-radius:2px;background:${C.primary}"></div>
        <div style="position:absolute;left:${cx - (dragging ? 3 : 2)}px;top:${14 - (dragging ? 11.25 : 9)}px;width:${dragging ? 6 : 4}px;height:${dragging ? 22.5 : 18}px;border-radius:3px;background:${C.primary}"></div>
      </div></div>`;
}

export const divider = () => `<div style="padding:12px 0"><div style="height:1px;background:rgba(31,41,55,.10)"></div></div>`;

export function segmented(options, selected) {
  return `<div style="display:flex;gap:3px;padding:3px;border-radius:10px;background:rgba(31,41,55,.06)">
      ${options.map((o, i) => `<div style="flex:1;border-radius:8px;padding:9px 0;text-align:center;font:600 13px/1.172 Roboto;letter-spacing:.5px;
        background:${i === selected ? C.primary : 'transparent'};color:${i === selected ? C.onPrimary : C.onSurfaceVariant}">${o}</div>`).join('')}</div>`;
}

export function colorRow(label, hex, swatch = hex) {
  return `<div style="display:flex;align-items:center;padding:6px 0">
      <div style="width:46px;height:46px;border-radius:14px;background:${swatch};border:1px solid rgba(209,213,219,.35)"></div>
      <div style="width:14px"></div>
      <div style="flex:1">${text(label, { size: 16, weight: 600 })}${text(hex, { size: 14, weight: 500, color: C.onSurfaceVariant, extra: 'padding-top:2px' })}</div>
      ${icon('filled/edit', 20, C.outline)}</div>`;
}

// AppearanceSection: a 70 dp header (46 dp tile) and, when open, a body card 3 dp under it.
export function section({ glyph, title, summary = '', open = false, body = '' }) {
  const tile = open
    ? `<div style="width:46px;height:46px;border-radius:14px;background:${C.primary};display:grid;place-items:center">${icon(glyph, 24, C.onPrimary)}</div>`
    : `<div style="width:46px;height:46px;border-radius:14px;background:rgba(224,231,255,.6);display:grid;place-items:center">${icon(glyph, 24, C.primary)}</div>`;
  return `<div>
      <div style="display:flex;align-items:center;padding:12px 14px;background:${C.row};border-radius:${open ? '20px 20px 6px 6px' : '20px'}">
        ${tile}<div style="width:14px"></div>
        <div style="flex:1;min-width:0">${text(title, { size: 16, weight: 600 })}${summary ? text(summary, { size: 14, color: C.onSurfaceVariant, extra: 'padding-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis' }) : ''}</div>
        <div style="transform:rotate(${open ? 180 : 0}deg)">${icon('filled/expand_more', 24, C.outline)}</div>
      </div>
      ${open ? `<div style="margin-top:3px;background:${C.row};border-radius:6px 6px 20px 20px;padding:16px">${body}</div>` : ''}
    </div>`;
}

export const subgroupLabel = (s) => `<div style="padding-bottom:6px;font:600 14px/20px Roboto;letter-spacing:.1px;color:${C.primary}">${s}</div>`;
export const label15 = (s) => text(s, { size: 15, weight: 400, extra: 'padding-bottom:4px' });

export function permissionNote(s) {
  return `<div style="margin-top:8px;border-radius:12px;background:rgba(254,226,226,.6);display:flex;align-items:center;padding:10px 12px;gap:10px">
      ${icon('filled/warning', 18, C.onErrorContainer)}<div style="flex:1;font:400 13px/18px Roboto;letter-spacing:.5px;color:${C.onErrorContainer}">${s}</div>
      ${icon('filled/keyboard_arrow_right', 18, C.onErrorContainer)}</div>`;
}

// PictureRow tile: W x (W + 12) picture, 18 dp corners, label under it.
export function pictureTile(label, inner, { w = 68, selected = false } = {}) {
  return `<div style="width:${w}px;flex:none">
      <div style="position:relative;width:${w}px;height:${w + 12}px;border-radius:18px;background:${C.surfaceContainerHigh};overflow:hidden;
          box-shadow:inset 0 0 0 ${selected ? 2 : 1}px ${selected ? C.primary : 'rgba(229,231,235,.6)'}">
        ${inner}
        ${selected ? `<div style="position:absolute;right:6px;top:6px;width:18px;height:18px;border-radius:50%;background:${C.primary};display:grid;place-items:center">${icon('filled/check', 12, '#fff')}</div>` : ''}
      </div>
      <div style="height:34px;padding-top:6px;text-align:center;font:${selected ? 600 : 500} 12px/16px Roboto;letter-spacing:.5px;color:${selected ? C.primary : C.onSurfaceVariant}">${label}</div></div>`;
}
export const tileWallpaper = (extra = '') => `<div style="position:absolute;inset:5px;border-radius:14px;background:linear-gradient(135deg,#D9CCFF,#9DB2FA);overflow:hidden">${extra}</div>`;

export function demoCaption(textHtml, glyphHtml = '') {
  return `<div style="display:inline-flex;align-items:center;gap:6px;border-radius:999px;background:rgba(255,255,255,.94);padding:7px 14px;
      box-shadow:0 1px 3px rgba(0,0,0,.18),0 1px 2px rgba(0,0,0,.12);font:600 14px/20px Roboto;letter-spacing:.1px;color:${C.primary}">${glyphHtml}${textHtml}</div>`;
}

export function presetChip(name, swatch, selected) {
  return `<div style="height:36px;border-radius:20px;display:inline-flex;align-items:center;gap:8px;padding:0 12px;flex:none;
      background:${selected ? C.primaryContainer : C.surfaceVariant};font:500 14px/20px Roboto;letter-spacing:.1px;color:${selected ? C.onPrimaryContainer : C.onSurfaceVariant}">
      <span style="width:14px;height:14px;border-radius:50%;background:${swatch}"></span>${name}</div>`;
}

// --- The stage: the walkthrough's drawn phone, its top half, on a pastel card ------------------------
export function stageGeometry(Ws, Hs, deviceMin = 412) {
  const u = Math.min((Ws * 0.8) / 188, (Hs - 12) / 109.4);
  const ox = (Ws - 280 * u) / 2, oy = 12 - 14 * u;
  const glass = { x: ox + 46 * u, y: oy + 20 * u, w: 188 * u, h: Hs - (oy + 20 * u), r: 24 * u };
  const scale = Math.min(1, Math.max(0.05, (188 * u) / deviceMin));
  return { u, ox, oy, glass, scale };
}

export function stageBackdrop(Ws, Hs, radius = 28) {
  const R = Math.max(Ws, Hs);
  const glow = (c, a, x, y, k) => `radial-gradient(circle ${k * R}px at ${x}% ${y}%, ${hexA(c, a)} 0%, ${hexA(c, a * 0.4)} 50%, ${hexA(c, 0)} 100%)`;
  const angle = 90 + (Math.atan2(Hs, Ws) * 180) / Math.PI;
  return `background:${glow('#34CFB6', 0.36, 90, 95, 0.6)},${glow('#7C83FF', 0.34, 92, 8, 0.45)},${glow('#FF9A76', 0.42, 8, 12, 0.55)},linear-gradient(${angle}deg,#F3F2FF,#E7ECFF);border-radius:${radius}px`;
}
function hexA(hex, a) { const n = parseInt(hex.slice(1), 16); return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`; }

export function scenePhoneSvg(Ws, Hs) {
  const g = stageGeometry(Ws, Hs);
  const id = 'sp' + Math.round(Math.random() * 1e9);
  return { g, svg: `<svg width="${Ws}" height="${Hs}" style="position:absolute;left:0;top:0;overflow:visible">
    <defs>
      <clipPath id="${id}g"><rect x="46" y="20" width="188" height="216" rx="24"/></clipPath>
      <linearGradient id="${id}w" gradientUnits="userSpaceOnUse" x1="46" y1="20" x2="234" y2="236"><stop offset="0" stop-color="#D9CCFF"/><stop offset="1" stop-color="#9DB2FA"/></linearGradient>
      <radialGradient id="${id}r" gradientUnits="userSpaceOnUse" cx="83.6" cy="56.72" r="110.16"><stop offset="0" stop-color="#fff" stop-opacity=".4"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>
    </defs>
    <g transform="translate(${g.ox} ${g.oy}) scale(${g.u})">
      <rect x="37" y="16" width="206" height="240" rx="33" fill="#000" fill-opacity=".10"/>
      <rect x="40" y="14" width="200" height="240" rx="30" fill="#17171C"/>
      <g clip-path="url(#${id}g)">
        <rect x="46" y="20" width="188" height="216" fill="url(#${id}w)"/><rect x="46" y="20" width="188" height="216" fill="url(#${id}r)"/>
        <g fill="#fff" fill-opacity=".85"><rect x="62" y="29" width="18" height="6" rx="3"/><rect x="198" y="29" width="9" height="6" rx="2"/><rect x="210" y="29" width="14" height="6" rx="2"/></g>
        <g fill="#fff" fill-opacity=".22"><rect x="66" y="58" width="24" height="24" rx="8"/><rect x="102" y="58" width="24" height="24" rx="8"/><rect x="138" y="58" width="24" height="24" rx="8"/><rect x="66" y="98" width="24" height="24" rx="8"/><rect x="102" y="98" width="24" height="24" rx="8"/><rect x="138" y="98" width="24" height="24" rx="8"/></g>
      </g>
      <circle cx="140" cy="32" r="4.5" fill="#17171C"/>
    </g></svg>` };
}

// The PreviewStage card with the drawn phone; `glassContent(g)` returns HTML for the glass (phone dp,
// drawn at g.scale from the glass's top-left).
export function previewStage(Ws, Hs, glassContent = () => '') {
  const { g, svg } = scenePhoneSvg(Ws, Hs);
  return h(`<div class="stage" style="position:relative;width:${Ws}px;height:${Hs}px;overflow:hidden;${stageBackdrop(Ws, Hs)}">
      ${svg}
      <div class="glass" style="position:absolute;left:${g.glass.x}px;top:${g.glass.y}px;width:${g.glass.w}px;height:${g.glass.h}px;overflow:hidden;border-radius:${g.glass.r}px ${g.glass.r}px 0 0">
        <div class="mini" style="position:absolute;left:0;top:0;width:${g.glass.w / g.scale}px;height:${g.glass.h / g.scale}px;transform-origin:0 0;transform:scale(${g.scale})">${glassContent(g)}</div>
      </div></div>`);
}

// A whole app screen: page background, top bar, then content (pinned + scrolling).
export function screen(inner, { bg = C.background } = {}) {
  return h(`<div class="app-screen" style="position:absolute;inset:0;background:${bg};font-family:Roboto;overflow:hidden">${inner}</div>`);
}
