// The long-press menu (ContextMenuOverlay.ContextMenuCard): a 238 dp card of 74 x 88 dp tiles,
// 10 dp beside the bar's window, in the panel style's menu palette.
import { h, icon, drawableIcon } from './core.js';
import { glassLayers } from './deck.js';
import { entranceFrame, entranceCss } from './quickpanel.js';

export const MENU_PALETTES = {
  solid: { surface: 'rgba(28,28,32,.957)', on: 'rgba(255,255,255,.949)', dim: 'rgba(255,255,255,.722)', chip: 'rgba(255,255,255,.102)', border: 'rgba(255,255,255,.122)' },
  frosted: { surface: 'rgba(21,22,27,.659)', on: 'rgba(255,255,255,.961)', dim: 'rgba(255,255,255,.761)', chip: 'rgba(255,255,255,.141)', border: 'rgba(255,255,255,.180)', blur: 15 },
  glass: { surface: 'rgba(20,20,26,.369)', on: '#FFFFFF', dim: 'rgba(255,255,255,.8)', chip: 'rgba(255,255,255,.18)', lit: true, blur: 22 },
  aero: { surface: 'rgba(214,230,244,.549)', on: 'rgba(16,24,32,.949)', dim: 'rgba(16,24,32,.651)', chip: 'rgba(255,255,255,.349)', lit: true, pale: true, blur: 20 },
  vibrant: { surface: 'rgba(247,247,250,.651)', on: 'rgba(27,27,31,.941)', dim: 'rgba(27,27,31,.62)', chip: 'rgba(0,0,0,.078)', border: 'rgba(255,255,255,.4)', blur: 24 },
  paper: { surface: 'rgba(248,247,244,.969)', on: 'rgba(26,26,30,.949)', dim: 'rgba(26,26,30,.62)', chip: 'rgba(0,0,0,.063)', border: 'rgba(0,0,0,.141)' },
  midnight: { surface: 'rgba(13,19,48,.961)', on: 'rgba(232,236,255,.961)', dim: 'rgba(201,209,245,.722)', chip: 'rgba(108,124,255,.18)', border: 'rgba(108,124,255,.239)' },
  amoled: { surface: '#000000', on: '#FFFFFF', dim: 'rgba(255,255,255,.702)', chip: 'rgba(255,255,255,.122)', border: 'rgba(255,255,255,.2)' },
  acrylic: { surface: 'rgba(43,36,48,.659)', on: 'rgba(255,247,240,.961)', dim: 'rgba(255,239,227,.761)', chip: 'rgba(255,233,214,.149)', border: 'rgba(255,233,214,.2)', blur: 18 },
  amethyst: { surface: 'rgba(59,36,99,.42)', on: '#FFFFFF', dim: 'rgba(241,230,255,.82)', chip: 'rgba(231,212,255,.2)', lit: true, blur: 22 },
};

export const MENU_ITEMS = [
  { label: 'Open deck', icon: 'filled/view_sidebar' }, { label: 'Quick panel', drawable: 'ic_vol_increase' },
  { label: 'Open volume UI', drawable: 'ic_vol_increase' }, { label: 'Mute or Unmute', drawable: 'ic_mute' },
  { label: 'Flashlight', icon: 'filled/flashlight_on' }, { label: 'Play / pause', icon: 'filled/play_arrow' },
  { label: 'Hide Handler', drawable: 'ic_visibility_hide' }, { label: 'Stop service', drawable: 'ic_power' },
  { label: 'Open App', drawable: 'ic_app_open' },
];
export const MENU_ICONS = ['filled/view_sidebar', 'filled/flashlight_on', 'filled/play_arrow', 'filled/lock', 'filled/screenshot',
  'drawable/ic_vol_increase', 'drawable/ic_mute', 'drawable/ic_visibility_hide', 'drawable/ic_power', 'drawable/ic_app_open'];

export class LongPressMenu {
  constructor(phone, bar, { theme = 'solid', items = MENU_ITEMS, width = 238, entrance = 'slide' } = {}) {
    const p = MENU_PALETTES[theme] || MENU_PALETTES.solid;
    this.p = p;
    this.entrance = entrance;
    const cols = Math.max(2, Math.min(5, Math.floor((width - 16) / 64)));
    const tileW = (width - 16) / cols;
    const rows = Math.ceil(items.length / cols);
    const height = rows * 88 + 16;
    const win = { x: bar.rect.x + bar.rect.w - Math.max(bar.rect.w, 28), w: Math.max(bar.rect.w, 28) };
    const right = bar.rect.x + bar.rect.w / 2 > bar.screenW / 2;
    this.towardLeft = !right;
    const x = right ? win.x - width - 10 : bar.rect.x + bar.rect.w + 10;
    const y = Math.min(Math.max((bar.frameTop ?? 0) + 10, bar.rect.y + bar.rect.h / 2 - height / 2), bar.screenH - height - 10);
    this.box = { x, y, w: width, h: height };
    const blur = p.blur ? `backdrop-filter:blur(${p.blur}px);-webkit-backdrop-filter:blur(${p.blur}px);` : '';
    const glyph = (it) => (it.drawable ? drawableIcon(it.drawable, 21, p.on) : icon(it.icon, 21, p.on));
    this.el = h(`<div class="menu" style="position:absolute;left:${x}px;top:${y}px;width:${width}px;height:${height}px;border-radius:24px;
        background:${p.surface};${blur}overflow:hidden;font-family:Roboto">
        ${p.lit ? glassLayers(24, p.pale) : ''}
        <div style="position:relative;padding:8px;display:flex;flex-wrap:wrap;justify-content:center">
          ${items.map((it) => `<div class="tile" style="width:${tileW}px;height:88px;padding:8px 3px;display:flex;flex-direction:column;align-items:center">
              <div style="width:40px;height:40px;border-radius:14px;background:${p.chip};display:grid;place-items:center">${glyph(it)}</div>
              <div style="margin-top:6px;max-width:68px;text-align:center;font:500 10px/12px Roboto;letter-spacing:.5px;color:${p.dim}">${it.label}</div></div>`).join('')}
        </div>
        ${p.lit ? '' : `<div style="position:absolute;inset:0;border-radius:24px;box-shadow:inset 0 0 0 1px ${p.border}"></div>`}
      </div>`);
    phone.overlay.appendChild(this.el);
  }

  tileCentre(i) {
    const cols = 3;
    const tileW = (this.box.w - 16) / cols;
    return [this.box.x + 8 + tileW * (i % cols) + tileW / 2, this.box.y + 8 + 88 * Math.floor(i / cols) + 8 + 20];
  }

  // open: entrance progress 0..1 (runs backwards for the exit), pressed: index of a squeezed tile
  set({ open = 1, pressed = -1, press = 0 } = {}) {
    entranceCss(this.el, entranceFrame(this.entrance, open, this.towardLeft), this.box);
    this.el.style.visibility = open <= 0 ? 'hidden' : 'visible';
    this.el.querySelectorAll('.tile').forEach((t, i) => { t.style.transform = i === pressed ? `scale(${1 - 0.08 * press})` : ''; });
  }
}
