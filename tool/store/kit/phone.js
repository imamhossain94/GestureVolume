// The phone the app runs on: a screen of 412 x 915 dp (a 1080 x 2400 phone at 420 dpi), its
// status bar and gesture pill, a wallpaper, and a plain home screen of made-up apps, so no other
// company's icon or name ever appears in the store art.
import { h, css, icon, rng } from './core.js';

export const SCREEN = { w: 412, h: 915, status: 32, nav: 24 };

// Made-up apps for the home screen, the Deck's pinned apps and per-app settings.
export const APPS = {
  phone: { label: 'Phone', glyph: 'round/call', c: ['#34D399', '#059669'] },
  messages: { label: 'Messages', glyph: 'round/chat_bubble', c: ['#60A5FA', '#2563EB'] },
  camera: { label: 'Camera', glyph: 'round/photo_camera', c: ['#94A3B8', '#475569'] },
  photos: { label: 'Photos', glyph: 'round/photo', c: ['#FBBF24', '#EA580C'] },
  music: { label: 'Music', glyph: 'round/music_note', c: ['#FB7185', '#E11D48'] },
  video: { label: 'Video', glyph: 'round/movie', c: ['#A78BFA', '#7C3AED'] },
  maps: { label: 'Maps', glyph: 'round/place', c: ['#34D399', '#0F766E'] },
  mail: { label: 'Mail', glyph: 'round/mail', c: ['#38BDF8', '#0369A1'] },
  clock: { label: 'Clock', glyph: 'round/schedule', c: ['#475569', '#0F172A'] },
  calendar: { label: 'Calendar', glyph: 'round/calendar_today', c: ['#818CF8', '#4338CA'] },
  notes: { label: 'Notes', glyph: 'round/sticky_note_2', c: ['#FDE047', '#CA8A04'] },
  settings: { label: 'Settings', glyph: 'round/settings', c: ['#CBD5E1', '#64748B'] },
  browser: { label: 'Browser', glyph: 'round/public', c: ['#22D3EE', '#0E7490'] },
  podcasts: { label: 'Podcasts', glyph: 'round/podcasts', c: ['#E879F9', '#A21CAF'] },
  weather: { label: 'Weather', glyph: 'round/wb_sunny', c: ['#FDBA74', '#F97316'] },
  games: { label: 'Games', glyph: 'round/sports_esports', c: ['#4ADE80', '#16A34A'] },
  fitness: { label: 'Fitness', glyph: 'round/directions_run', c: ['#F87171', '#DC2626'] },
  reader: { label: 'Reader', glyph: 'round/menu_book', c: ['#FCD34D', '#B45309'] },
  gv: { label: 'Gesture Volume', glyph: null, c: ['#AA7BFF', '#2C209A'] },
};

export const APP_ICONS = Object.values(APPS).filter((a) => a.glyph).map((a) => a.glyph);
export const PHONE_ICONS = ['round/wifi', 'round/signal_cellular_alt', 'round/battery_full', 'round/search', 'round/mic', ...APP_ICONS];

// The app's launcher icon: its adaptive layers (ic_launcher_background + _foreground), masked to
// a circle like most launchers, or to a squircle for the store art.
export function gvIcon(size, shape = 'circle') {
  const r = shape === 'circle' ? '50%' : `${size * 0.3}px`;
  // The adaptive icon's 108 dp canvas is cropped to the middle 72 dp by the launcher mask.
  const fg = size * (108 / 72);
  return `<div class="gv-icon" style="width:${size}px;height:${size}px;border-radius:${r};overflow:hidden;position:relative;flex:none;
      background:linear-gradient(135deg,#AA7BFF,#2C209A)">
      <img src="../build/drawable/ic_launcher_foreground.svg" style="position:absolute;width:${fg}px;height:${fg}px;left:${(size - fg) / 2}px;top:${(size - fg) / 2}px">
    </div>`;
}

export function appIcon(key, size = 56, shape = 'circle') {
  const a = APPS[key];
  if (key === 'gv') return gvIcon(size, shape);
  const r = shape === 'circle' ? '50%' : `${size * 0.3}px`;
  return `<div style="width:${size}px;height:${size}px;border-radius:${r};flex:none;display:grid;place-items:center;
      background:linear-gradient(145deg,${a.c[0]},${a.c[1]});box-shadow:inset 0 1px 0 rgba(255,255,255,.25)">
      ${icon(a.glyph, size * 0.5, '#fff')}</div>`;
}

// --- Wallpapers --------------------------------------------------------------------------------
// Painted, not photographed: soft light fields over a base, plus fine grain, from a seed.
export const WALLPAPERS = {
  dusk: { base: ['#1B1440', '#3B1F6E'], blobs: [['#7C5CFF', 0.75, 0.18, 0.55, 0.9], ['#FF7EB3', 0.15, 0.62, 0.42, 0.55], ['#2DD4BF', 0.85, 0.85, 0.5, 0.45], ['#AA7BFF', 0.3, 0.2, 0.45, 0.6]] },
  ocean: { base: ['#06233B', '#0B3A5B'], blobs: [['#14B8A6', 0.2, 0.25, 0.6, 0.7], ['#3B82F6', 0.85, 0.5, 0.55, 0.65], ['#A5F3FC', 0.6, 0.95, 0.4, 0.35], ['#6366F1', 0.1, 0.85, 0.45, 0.5]] },
  sunset: { base: ['#2A1033', '#5A1F45'], blobs: [['#FB923C', 0.8, 0.15, 0.6, 0.8], ['#F43F5E', 0.2, 0.45, 0.55, 0.7], ['#FDE68A', 0.9, 0.7, 0.35, 0.5], ['#7C3AED', 0.25, 0.92, 0.6, 0.7]] },
  midnight: { base: ['#05060B', '#0B1020'], blobs: [['#312E81', 0.8, 0.2, 0.55, 0.8], ['#0F766E', 0.15, 0.8, 0.5, 0.6], ['#4F46E5', 0.5, 0.55, 0.35, 0.35]] },
  pastel: { base: ['#EDE9FE', '#E0F2FE'], blobs: [['#C4B5FD', 0.8, 0.2, 0.6, 0.9], ['#99F6E4', 0.15, 0.6, 0.55, 0.8], ['#FBCFE8', 0.85, 0.85, 0.5, 0.7], ['#BFDBFE', 0.3, 0.15, 0.45, 0.7]] },
  forest: { base: ['#0B1F17', '#12372A'], blobs: [['#34D399', 0.75, 0.2, 0.55, 0.6], ['#FDE68A', 0.2, 0.35, 0.4, 0.4], ['#0EA5E9', 0.8, 0.85, 0.5, 0.5], ['#065F46', 0.2, 0.9, 0.6, 0.8]] },
};

export function paintWallpaper(canvas, name = 'dusk', seed = 7) {
  const w = canvas.width, hgt = canvas.height;
  const ctx = canvas.getContext('2d');
  const wp = WALLPAPERS[name];
  const g = ctx.createLinearGradient(0, 0, w * 0.3, hgt);
  g.addColorStop(0, wp.base[0]);
  g.addColorStop(1, wp.base[1]);
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, w, hgt);
  ctx.globalCompositeOperation = 'screen';
  for (const [c, x, y, r, a] of wp.blobs) {
    const R = r * Math.max(w, hgt);
    const rg = ctx.createRadialGradient(x * w, y * hgt, 0, x * w, y * hgt, R);
    rg.addColorStop(0, hexA(c, a));
    rg.addColorStop(0.45, hexA(c, a * 0.45));
    rg.addColorStop(1, hexA(c, 0));
    ctx.fillStyle = rg;
    ctx.fillRect(0, 0, w, hgt);
  }
  ctx.globalCompositeOperation = 'source-over';
  // Grain, so the gradients don't band once a video encoder gets them.
  const img = ctx.getImageData(0, 0, w, hgt);
  const r = rng(seed);
  for (let i = 0; i < img.data.length; i += 4) {
    const n = (r() - 0.5) * 7;
    img.data[i] += n; img.data[i + 1] += n; img.data[i + 2] += n;
  }
  ctx.putImageData(img, 0, 0);
}

function hexA(hex, a) {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
}

// --- Status bar and gesture pill ----------------------------------------------------------------
export function statusBar({ dark = false, time = '9:30' } = {}) {
  const c = dark ? '#1F1F1F' : '#FFFFFF';
  return h(`<div class="statusbar" style="position:absolute;left:0;right:0;top:0;height:${SCREEN.status}px;display:flex;align-items:center;
      padding:0 22px 0 26px;color:${c};font:500 14px/1 Roboto;letter-spacing:.2px;z-index:50;pointer-events:none">
      <span>${time}</span><span style="flex:1"></span>
      <span style="position:absolute;left:50%;top:9px;width:14px;height:14px;margin-left:-7px;border-radius:50%;background:#0A0A0C;box-shadow:0 0 0 1.5px rgba(255,255,255,.06)"></span>
      <span style="display:flex;gap:3px;align-items:center">${icon('round/wifi', 16, c)}${icon('round/signal_cellular_alt', 16, c)}
      ${icon('round/battery_full', 16, c, 'transform:rotate(90deg)')}</span></div>`);
}

export function navPill({ dark = false } = {}) {
  return h(`<div class="navpill" style="position:absolute;left:50%;bottom:8px;width:108px;height:4px;margin-left:-54px;border-radius:2px;
      background:${dark ? 'rgba(0,0,0,.82)' : 'rgba(255,255,255,.86)'};z-index:50;pointer-events:none"></div>`);
}

// --- The phone ---------------------------------------------------------------------------------
// A screen-sized box with layers: wallpaper, the app below (home or an app screen), overlays.
// `radius` rounds the screen's corners for the store art; there is no device frame.
export class Phone {
  constructor({ wallpaper = 'dusk', radius = 0, darkIcons = false, time = '9:30', scale = 1 } = {}) {
    this.el = h(`<div class="phone" style="position:relative;width:${SCREEN.w}px;height:${SCREEN.h}px;overflow:hidden;border-radius:${radius}px;
        background:#000;transform-origin:0 0;transform:scale(${scale});isolation:isolate"></div>`);
    this.wall = document.createElement('canvas');
    const dpr = 3;
    this.wall.width = SCREEN.w * dpr;
    this.wall.height = SCREEN.h * dpr;
    css(this.wall, { position: 'absolute', left: 0, top: 0, width: SCREEN.w + 'px', height: SCREEN.h + 'px' });
    if (wallpaper) paintWallpaper(this.wall, wallpaper);
    this.el.appendChild(this.wall);
    this.below = h('<div class="below" style="position:absolute;inset:0"></div>');
    this.overlay = h('<div class="overlays" style="position:absolute;inset:0;z-index:20"></div>');
    this.top = h('<div class="top" style="position:absolute;inset:0;z-index:60;pointer-events:none"></div>');
    this.el.append(this.below, this.overlay, this.top);
    this.status = statusBar({ dark: darkIcons, time });
    this.pill = navPill({ dark: darkIcons });
    this.el.append(this.status, this.pill);
  }

  setDarkIcons(dark) {
    const c = dark ? '#1F1F1F' : '#FFFFFF';
    this.status.style.color = c;
    this.status.querySelectorAll('svg').forEach((s) => { s.style.color = c; s.setAttribute('fill', c); });
    this.pill.style.background = dark ? 'rgba(0,0,0,.82)' : 'rgba(255,255,255,.86)';
  }
}

// A plain home screen: clock, two rows of apps, the dock and a search pill.
export function homeScreen({ rows = [['calendar', 'mail', 'photos', 'maps'], ['music', 'video', 'notes', 'gv']], dock = ['phone', 'messages', 'browser', 'camera'], date = 'Thursday, October 9', time = '9:30', labels = true } = {}) {
  const cell = (k) => `<div style="display:grid;justify-items:center;gap:7px;width:82px">${appIcon(k, 56)}
      ${labels ? `<span style="font:400 12px/14px Roboto;color:#fff;text-shadow:0 1px 3px rgba(0,0,0,.45);white-space:nowrap">${APPS[k].label}</span>` : ''}</div>`;
  return h(`<div class="home" style="position:absolute;inset:0;color:#fff">
      <div style="position:absolute;left:28px;top:70px">
        <div style="font:300 76px/1 'Plus Jakarta Sans';letter-spacing:-2px">${time}</div>
        <div style="font:500 16px/1.4 Roboto;margin-top:10px;opacity:.92">${date}</div>
      </div>
      <div style="position:absolute;left:0;right:0;bottom:214px;display:grid;gap:22px">
        ${rows.map((r) => `<div style="display:flex;justify-content:space-evenly;padding:0 8px">${r.map(cell).join('')}</div>`).join('')}
      </div>
      <div style="position:absolute;left:0;right:0;bottom:104px;display:flex;justify-content:space-evenly;padding:0 8px">
        ${dock.map((k) => `<div style="width:82px;display:grid;justify-items:center">${appIcon(k, 56)}</div>`).join('')}
      </div>
      <div style="position:absolute;left:22px;right:22px;bottom:38px;height:52px;border-radius:26px;background:rgba(255,255,255,.18);
          backdrop-filter:blur(8px);display:flex;align-items:center;padding:0 18px;gap:12px">
        ${icon('round/search', 24, 'rgba(255,255,255,.95)')}<span style="flex:1"></span>${icon('round/mic', 24, 'rgba(255,255,255,.95)')}
      </div>
    </div>`);
}
