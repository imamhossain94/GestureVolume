// The Deck as DeckOverlay draws it: one capsule strip of 44 dp circles beside the bar (quick
// dial, pinned apps, a divider, tiles) and a 320 dp card opening toward the middle of the screen.
// Default Solid style: #EE15161A surface, white accent, chips white @ 14 %.
import { h, icon, clamp, lerp, Ease } from './core.js';
import { appIcon } from './phone.js';
import { entranceFrame, entranceCss } from './quickpanel.js';

export const TILES = {
  search: { label: 'Search', icon: 'filled/search' }, flashlight: { label: 'Flashlight', icon: 'filled/flashlight_on' },
  screenshot: { label: 'Screenshot', icon: 'filled/screenshot' }, rotation: { label: 'Auto-rotate', icon: 'filled/screen_rotation' },
  wifi: { label: 'Wi-Fi', icon: 'filled/wifi' }, bluetooth: { label: 'Bluetooth', icon: 'filled/bluetooth' },
  volume: { label: 'Volume', icon: 'filled/volume_up' }, brightness: { label: 'Brightness', icon: 'filled/brightness_6' },
  media: { label: 'Media', icon: 'filled/music_note' }, timer: { label: 'Timer', icon: 'filled/timer' },
  calculator: { label: 'Calculator', icon: 'filled/calculate' }, notes: { label: 'Notes', icon: 'filled/edit_note' },
  checklist: { label: 'Checklist', icon: 'filled/checklist' }, coin: { label: 'Coin toss', icon: 'filled/paid' },
  dice: { label: 'Dice', icon: 'filled/casino' }, dnd: { label: 'Do Not Disturb', icon: 'filled/do_not_disturb_on' },
  lock: { label: 'Lock screen', icon: 'filled/lock' }, qr: { label: 'Scan QR code', icon: 'filled/qr_code_scanner' },
};
export const DEFAULT_TILES = ['search', 'flashlight', 'screenshot', 'volume', 'media', 'timer', 'notes'];
export const DECK_ICONS = [
  ...Object.values(TILES).map((t) => t.icon), 'filled/close', 'filled/mic', 'filled/chat', 'filled/sms', 'filled/call',
  'filled/open_in_new', 'filled/skip_previous', 'filled/skip_next', 'filled/play_arrow', 'filled/pause', 'filled/refresh', 'filled/delete',
];

const PAL = {
  solid: { bg: 'rgba(21,22,26,.933)', on: '#FFFFFF', subtle: 'rgba(255,255,255,.62)', accent: '#FFFFFF', onAccent: '#111111', chip: 'rgba(255,255,255,.14)', avatar: 'rgba(255,255,255,.22)', divider: 'rgba(255,255,255,.18)' },
  paper: { bg: 'rgba(248,247,244,.969)', on: '#111111', subtle: 'rgba(17,17,17,.62)', accent: '#111111', onAccent: '#FFFFFF', chip: 'rgba(17,17,17,.10)', avatar: 'rgba(17,17,17,.22)', divider: 'rgba(17,17,17,.18)' },
  midnight: { bg: 'rgba(13,19,48,.961)', on: '#FFFFFF', subtle: 'rgba(255,255,255,.62)', accent: '#FFFFFF', onAccent: '#111111', chip: 'rgba(255,255,255,.14)', avatar: 'rgba(255,255,255,.22)', divider: 'rgba(255,255,255,.18)' },
  amoled: { bg: '#000000', on: '#FFFFFF', subtle: 'rgba(255,255,255,.62)', accent: '#FFFFFF', onAccent: '#111111', chip: 'rgba(255,255,255,.14)', avatar: 'rgba(255,255,255,.22)', divider: 'rgba(255,255,255,.18)' },
  frosted: { bg: 'rgba(21,22,26,.616)', blur: 15, on: '#FFFFFF', subtle: 'rgba(255,255,255,.62)', accent: '#FFFFFF', onAccent: '#111111', chip: 'rgba(255,255,255,.14)', avatar: 'rgba(255,255,255,.22)', divider: 'rgba(255,255,255,.18)' },
  glass: { bg: 'rgba(21,22,26,.317)', blur: 22, lit: true, on: '#FFFFFF', subtle: 'rgba(255,255,255,.62)', accent: '#FFFFFF', onAccent: '#111111', chip: 'rgba(255,255,255,.14)', avatar: 'rgba(255,255,255,.22)', divider: 'rgba(255,255,255,.18)' },
  aero: { bg: 'rgba(214,230,244,.549)', blur: 20, lit: true, pale: true, on: '#111111', subtle: 'rgba(17,17,17,.62)', accent: '#111111', onAccent: '#FFFFFF', chip: 'rgba(17,17,17,.10)', avatar: 'rgba(17,17,17,.22)', divider: 'rgba(17,17,17,.18)' },
  vibrant: { bg: 'rgba(247,247,250,.651)', blur: 24, pale: true, on: '#111111', subtle: 'rgba(17,17,17,.62)', accent: '#111111', onAccent: '#FFFFFF', chip: 'rgba(17,17,17,.10)', avatar: 'rgba(17,17,17,.22)', divider: 'rgba(17,17,17,.18)' },
  acrylic: { bg: 'rgba(43,36,48,.659)', blur: 18, on: '#FFFFFF', subtle: 'rgba(255,255,255,.62)', accent: '#FFFFFF', onAccent: '#111111', chip: 'rgba(255,255,255,.14)', avatar: 'rgba(255,255,255,.22)', divider: 'rgba(255,255,255,.18)' },
  amethyst: { bg: 'rgba(59,36,99,.42)', blur: 22, lit: true, on: '#FFFFFF', subtle: 'rgba(255,255,255,.62)', accent: '#FFFFFF', onAccent: '#111111', chip: 'rgba(255,255,255,.14)', avatar: 'rgba(255,255,255,.22)', divider: 'rgba(255,255,255,.18)' },
};

// The lit-glass lighting (liquidGlass) for Glass, Gloss and Amethyst, as CSS layers.
export function glassLayers(radius, pale = false) {
  const k = pale ? 0.45 : 1;
  const w = (a) => `rgba(255,255,255,${((a / 255) * k).toFixed(3)})`;
  return `<div style="position:absolute;inset:0;border-radius:${radius}px;pointer-events:none;
      background:linear-gradient(to bottom,${w(0x2e)} 0%,${w(0x08)} 45%,transparent 100%),linear-gradient(to bottom,transparent 82%,${w(0x1a)} 100%)"></div>
    <div style="position:absolute;inset:0;border-radius:${radius}px;pointer-events:none;padding:1.2px;
      background:linear-gradient(to bottom,${w(0xa6)} 0%,${w(0x3d)} 35%,${w(0x14)} 75%,${w(0x4d)} 100%);
      -webkit-mask:linear-gradient(#000 0 0) content-box,linear-gradient(#000 0 0);-webkit-mask-composite:xor;mask-composite:exclude"></div>`;
}

const initials = (name) => name.trim().split(/\s+/).slice(0, 2).map((w) => w[0].toUpperCase()).join('') || '#';

export class Deck {
  // opts: { tiles, apps: ['camera', ...], people: [{ name, via: 'call'|'sms'|'whatsapp' }], theme, bar (EdgeBar) }
  constructor(phone, bar, opts = {}) {
    this.phone = phone;
    this.bar = bar;
    this.o = { tiles: DEFAULT_TILES, apps: [], people: [], theme: 'solid', entrance: 'slide', ...opts };
    this.p = PAL[this.o.theme] || PAL.solid;
    const p = this.p;
    const frameW = bar.screenW, frameH = bar.screenH;
    const B = bar.rect;
    this.isLeft = B.x + B.w / 2 < frameW / 2;
    const n = this.o.people.length + this.o.apps.length + this.o.tiles.length;
    const divider = (this.o.people.length + this.o.apps.length) > 0 && this.o.tiles.length > 0;
    const content = 10 + n * 44 + (n - 1) * 6 + 10 + (divider ? 15 : 0);
    const top = bar.frameTop ?? 0;
    const cap = Math.floor((frameH - top) * 0.6);
    this.stripH = Math.min(content, cap);
    this.stripW = 64;
    this.stripX = this.isLeft ? 6 : frameW - 6 - this.stripW;
    this.stripY = clamp(B.y + B.h / 2 - this.stripH / 2, top + 8, Math.max(top + 8, frameH - this.stripH - 8));
    this.cardW = Math.min(320, frameW - this.stripW - 6 - 16);
    this.cardX = this.isLeft ? this.stripX + this.stripW + 8 : this.stripX - 8 - this.cardW;

    this.root = h(`<div class="deck" style="position:absolute;inset:0;pointer-events:none"></div>`);
    const blur = p.blur ? `backdrop-filter:blur(${p.blur}px);-webkit-backdrop-filter:blur(${p.blur}px);` : '';
    this.strip = h(`<div class="deck-strip" style="position:absolute;left:${this.stripX}px;top:${this.stripY}px;width:${this.stripW}px;height:${this.stripH}px;
        border-radius:30px;background:${p.bg};${blur}overflow:hidden">
        ${p.lit ? glassLayers(30, p.pale) : ''}
        <div class="items" style="position:absolute;left:0;right:0;top:0;padding:10px 0;display:flex;flex-direction:column;align-items:center;gap:6px"></div>
      </div>`);
    const items = this.strip.querySelector('.items');
    this.tileEls = {};
    for (const person of this.o.people) {
      const badge = person.via && person.via !== 'call'
        ? `<div style="position:absolute;right:0;bottom:0;width:16px;height:16px;border-radius:50%;background:${p.accent};display:grid;place-items:center">${icon(person.via === 'sms' ? 'filled/sms' : 'filled/chat', 10, p.onAccent)}</div>` : '';
      items.appendChild(h(`<div style="position:relative;width:44px;height:44px;flex:none">
          <div style="width:44px;height:44px;border-radius:50%;background:${p.avatar};display:grid;place-items:center;font:700 14px/1 Roboto;letter-spacing:.5px;color:${p.accent}">${initials(person.name)}</div>${badge}</div>`));
    }
    for (const a of this.o.apps) {
      items.appendChild(h(`<div style="width:44px;height:44px;display:grid;place-items:center;flex:none"><div style="width:38px;height:38px;border-radius:11px;overflow:hidden;display:grid;place-items:center">${appIcon(a, 38)}</div></div>`));
    }
    if (divider) items.appendChild(h(`<div style="width:22px;height:9px;display:grid;align-items:center;flex:none"><div style="height:1px;background:${p.divider}"></div></div>`));
    for (const id of this.o.tiles) {
      const el = h(`<div class="tile" style="width:44px;height:44px;border-radius:50%;background:${p.chip};display:grid;place-items:center;flex:none">${icon(TILES[id].icon, 22, p.accent)}</div>`);
      this.tileEls[id] = el;
      items.appendChild(el);
    }
    this.items = items;
    this.root.appendChild(this.strip);
    this.card = null;
    phone.overlay.appendChild(this.root);
  }

  // Centre of the item at index i (0-based in strip order), in phone dp, given the scroll.
  itemCentre(i, scroll = 0) {
    const ppl = this.o.people.length, apps = this.o.apps.length;
    const divider = (ppl + apps) > 0 && this.o.tiles.length > 0;
    let y = 10 + i * 50 + 22;
    if (divider && i >= ppl + apps) y += 15;
    return [this.stripX + 32, this.stripY + y - scroll];
  }
  tileCentre(id, scroll = 0) {
    const i = this.o.people.length + this.o.apps.length + this.o.tiles.indexOf(id);
    return this.itemCentre(i, scroll);
  }

  setActive(id, on) {
    const el = this.tileEls[id];
    if (!el) return;
    el.style.background = on ? this.p.accent : this.p.chip;
    el.querySelector('svg').style.color = on ? this.p.onAccent : this.p.accent;
    el.querySelector('svg').setAttribute('fill', on ? this.p.onAccent : this.p.accent);
  }

  // Opens a card with `content` (an element) under the header for tile `id`.
  showCard(id, content) {
    if (this.card) this.card.remove();
    const p = this.p;
    const blur = p.blur ? `backdrop-filter:blur(${p.blur}px);-webkit-backdrop-filter:blur(${p.blur}px);` : '';
    this.card = h(`<div class="deck-card" style="position:absolute;left:${this.cardX}px;top:${this.stripY}px;width:${this.cardW}px;
        border-radius:22px;background:${p.bg};${blur}overflow:hidden;color:${p.on};font-family:Roboto">
        ${p.lit ? glassLayers(22, p.pale) : ''}
        <div style="position:relative;padding:6px 8px 14px 16px">
          <div class="hdr" style="height:48px;display:flex;align-items:center">
            ${icon(TILES[id].icon, 20, p.accent)}
            <div style="width:10px"></div>
            <div style="flex:1;font:600 16px/1.172 Roboto;letter-spacing:.5px;color:${p.on}">${TILES[id].label}</div>
            <div style="width:48px;height:48px;display:grid;place-items:center">${icon('filled/close', 24, p.subtle)}</div>
          </div>
          <div class="content" style="padding-right:8px"></div>
        </div>
      </div>`);
    this.card.querySelector('.content').appendChild(content);
    this.root.appendChild(this.card);
    this.cardId = id;
    return this.card;
  }

  // state: { open: 0..1 entrance progress (null = rest), scroll, card: 0..1 card enter progress, alpha }
  set(state = {}) {
    const box = { x: this.stripX, y: this.stripY, w: this.stripW, h: this.stripH };
    const f = entranceFrame(this.o.entrance, state.open ?? 1, this.isLeft);
    entranceCss(this.strip, f, box);
    this.items.style.transform = `translateY(${-(state.scroll || 0)}px)`;
    this.root.style.opacity = state.alpha ?? 1;
    this.root.style.visibility = (state.open ?? 1) <= 0 ? 'hidden' : 'visible';
    if (this.card) {
      const c = state.card ?? 1;
      const fa = Ease.fastOutSlowIn(clamp(c * (220 / 190)));
      const sc = lerp(0.94, 1, Ease.fastOutSlowIn(clamp(c)));
      this.card.style.opacity = fa * (state.cardAlpha ?? 1);
      this.card.style.transformOrigin = this.isLeft ? '0 50%' : '100% 50%';
      this.card.style.transform = `scale(${sc})`;
      this.card.style.visibility = c <= 0 ? 'hidden' : 'visible';
    }
  }
}

// --- Card contents --------------------------------------------------------------------------------
export function chip(text, selected, p = PAL.solid) {
  return `<span style="display:inline-block;border-radius:12px;padding:7px 12px;font:600 12px/1.172 Roboto;letter-spacing:.5px;
      background:${selected ? p.accent : p.chip};color:${selected ? p.onAccent : p.accent}">${text}</span>`;
}

export function button(text, { filled = false, grow = false, p = PAL.solid } = {}) {
  return `<div style="${grow ? 'flex:1;' : ''}border-radius:14px;padding:10px 16px;font:600 14px/1.172 Roboto;letter-spacing:.5px;text-align:center;white-space:nowrap;
      background:${filled ? p.accent : p.chip};color:${filled ? p.onAccent : p.accent}">${text}</div>`;
}

// Search: field (+ caret and typed text), mic, and the route's rows.
export function searchCard({ query = '', focused = true, answer = null, number = null, people = [], apps = [], providers = ['Google', 'YouTube', 'Maps'], caret = true, p = PAL.solid } = {}) {
  const field = `<div style="flex:1;height:56px;border-radius:14px;border:${focused ? `2px solid ${p.accent}` : `1px solid rgba(255,255,255,.25)`};
      display:flex;align-items:center;padding:0 ${focused ? 15 : 16}px;font:400 14px/1 Roboto;color:${query ? p.on : p.subtle}">
      <span class="q">${query || 'Search anything…'}</span>${focused && caret ? `<span class="caret" style="display:inline-block;width:2px;height:20px;background:${p.accent};margin-left:1px"></span>` : ''}</div>`;
  let rows = '';
  if (answer) rows += `<div style="margin-top:8px;border-radius:12px;background:${p.chip};padding:12px;display:flex;align-items:center">
      <span style="flex:1;font:600 20px/1.172 Roboto;letter-spacing:.5px;color:${p.accent}">= ${answer}</span>
      <span style="font:400 12px/1.172 Roboto;letter-spacing:.5px;color:${p.subtle}">Copy result</span></div>`;
  const row = (lead, title, sub) => `<div style="margin-top:4px;display:flex;align-items:center;padding:8px 4px">${lead}<div style="width:12px"></div>
      <div style="flex:1;min-width:0"><div style="font:400 15px/1.172 Roboto;letter-spacing:.5px;color:${p.on}">${title}</div>
      ${sub ? `<div style="font:400 12px/1.172 Roboto;letter-spacing:.5px;color:${p.subtle};margin-top:2px">${sub}</div>` : ''}</div>${icon('filled/open_in_new', 16, p.subtle)}</div>`;
  if (number) rows += row(icon('filled/call', 22, p.accent), number, 'Open the dialer');
  for (const a of apps) rows += row(`<div style="width:28px;height:28px;border-radius:8px;overflow:hidden">${appIcon(a.key, 28)}</div>`, a.label);
  for (const person of people) rows += row(icon('filled/call', 22, p.accent), person.name, person.sub);
  if (query) {
    rows += `<div style="margin-top:10px;font:400 12px/1.172 Roboto;letter-spacing:.5px;color:${p.subtle}">Search the web with</div>
      <div style="margin-top:6px;display:flex;gap:6px;flex-wrap:wrap">${providers.map((x, i) => chip(x, i === 0, p)).join('')}</div>`;
  } else {
    rows += `<div style="padding-top:6px;font:400 12px/16px Roboto;letter-spacing:.5px;color:${p.subtle}">Type an app or a contact to open it, a sum to work it out, a number to call it, or anything else to search the web.</div>`;
  }
  return h(`<div><div style="display:flex;align-items:center;gap:6px">${field}
      <div style="width:40px;height:40px;border-radius:50%;background:${p.chip};display:grid;place-items:center">${icon('filled/mic', 20, p.accent)}</div></div>${rows}</div>`);
}

export function timerCard({ time = '05:00', running = false, selected = '5m', p = PAL.solid } = {}) {
  const chips = (list) => `<div style="display:flex;gap:6px;justify-content:center;margin-top:6px">${list.map((c) => chip(c, c === selected, p)).join('')}</div>`;
  return h(`<div style="display:flex;flex-direction:column;align-items:center">
      <div class="time" style="font:300 44px/1.172 Roboto;letter-spacing:.5px;color:${p.on}">${time}</div>
      <div style="height:8px"></div>
      ${running ? `<div style="width:100%">${button('Stop', { filled: true, p })}</div>` : `${chips(['1m', '5m', '10m'])}${chips(['15m', '30m', '60m'])}
      <div style="height:8px"></div><div style="display:flex;gap:8px;width:100%">${button('−1', { p })}${button('Start', { filled: true, grow: true, p })}${button('+1', { p })}</div>`}
      <div style="align-self:stretch;padding-top:6px;font:400 12px/16px Roboto;letter-spacing:.5px;color:${p.subtle}">A countdown that keeps running after the Deck closes</div>
    </div>`);
}

// M3 1.4 slider as the Deck draws it: 16 dp track, 4 x 44 dp thumb, 6 dp gaps, 4 dp stop dot.
export function deckSlider(value, { p = PAL.solid, width = 220 } = {}) {
  const x = 2 + (width - 4) * value;
  return `<div class="dslider" style="position:relative;height:48px;width:${width}px;flex:1">
      <div style="position:absolute;left:0;top:16px;height:16px;width:${Math.max(0, x - 6)}px;background:${p.accent};border-radius:8px 2px 2px 8px"></div>
      <div style="position:absolute;left:${x + 6}px;right:0;top:16px;height:16px;background:${p.accent === '#FFFFFF' ? 'rgba(255,255,255,.25)' : 'rgba(17,17,17,.25)'};border-radius:2px 8px 8px 2px"></div>
      <div style="position:absolute;right:6px;top:22px;width:4px;height:4px;border-radius:50%;background:${p.accent}"></div>
      <div style="position:absolute;left:${x - 2}px;top:2px;width:4px;height:44px;border-radius:2px;background:${p.accent}"></div></div>`;
}

export function volumeCard({ media = 0.6, ring = 0.8, alarm = 0.5, p = PAL.solid } = {}) {
  const row = (label, v) => `<div style="display:flex;align-items:center"><div style="width:56px;font:400 13px/1.172 Roboto;letter-spacing:.5px;color:${p.on}">${label}</div>${deckSlider(v, { p, width: 232 })}</div>`;
  return h(`<div>${row('Media', media)}${row('Ringtone', ring)}${row('Alarm', alarm)}
      <div style="display:flex;gap:8px;margin-top:4px">${button('Mute', { p })}${button('Open volume UI', { p })}</div></div>`);
}

export function mediaCard({ playing = true, volume = 0.55, p = PAL.solid } = {}) {
  const c = (sz, glyph, filled) => `<div style="width:${sz}px;height:${sz}px;border-radius:50%;background:${filled ? p.accent : p.chip};display:grid;place-items:center">${icon(glyph, filled ? 30 : 22, filled ? p.onAccent : p.accent)}</div>`;
  return h(`<div style="display:flex;flex-direction:column;align-items:center">
      <div style="font:400 13px/1.172 Roboto;letter-spacing:.5px;color:${p.subtle}">${playing ? 'Playing' : 'Nothing is playing'}</div>
      <div style="height:8px"></div>
      <div style="display:flex;gap:14px;align-items:center">${c(44, 'filled/skip_previous')}${c(60, playing ? 'filled/pause' : 'filled/play_arrow', true)}${c(44, 'filled/skip_next')}</div>
      <div style="height:10px"></div>
      <div style="display:flex;align-items:center;align-self:stretch;gap:4px">${icon('filled/volume_up', 18, p.subtle)}${deckSlider(volume, { p, width: 262 })}</div>
      <div style="align-self:stretch;padding-top:6px;font:400 12px/16px Roboto;letter-spacing:.5px;color:${p.subtle}">Play, pause and skip work for any app that is playing.</div>
    </div>`);
}

// The coin (CoinTossCard.drawCoin), on a canvas: angle in degrees about the horizontal axis.
export function drawCoin(ctx, cx, cy, r, angle, colors = COIN_DEFAULT) {
  const a = ((angle % 360) + 360) % 360;
  const facing = Math.abs(Math.cos((a * Math.PI) / 180));
  const tails = a >= 90 && a < 270;
  ctx.save();
  ctx.translate(cx, cy);
  ctx.scale(1, Math.max(facing, 0.02));
  const rim = ctx.createLinearGradient(-r, -r, r, r);
  rim.addColorStop(0, colors.faceLight); rim.addColorStop(0.45, colors.rim); rim.addColorStop(1, colors.rimShade);
  ctx.fillStyle = rim; ctx.beginPath(); ctx.arc(0, 0, r, 0, Math.PI * 2); ctx.fill();
  ctx.strokeStyle = colors.groove; ctx.lineWidth = 0.035 * r; ctx.beginPath(); ctx.arc(0, 0, 0.86 * r, 0, Math.PI * 2); ctx.stroke();
  const face = ctx.createRadialGradient(-0.35 * r, -0.4 * r, 0, -0.35 * r, -0.4 * r, 1.3 * r);
  face.addColorStop(0, colors.faceLight); face.addColorStop(0.55, colors.base); face.addColorStop(1, colors.faceShade);
  ctx.fillStyle = face; ctx.beginPath(); ctx.arc(0, 0, 0.84 * r, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = colors.inkSoft;
  for (let i = 0; i < 32; i++) {
    const t = (i * 11.25 * Math.PI) / 180;
    ctx.beginPath(); ctx.arc(Math.cos(t) * 0.73 * r, Math.sin(t) * 0.73 * r, 0.026 * r, 0, Math.PI * 2); ctx.fill();
  }
  const emblem = (dy, color) => {
    ctx.save(); ctx.translate(0, dy);
    if (!tails) {
      ctx.fillStyle = color; ctx.beginPath();
      for (let i = 0; i < 10; i++) {
        const rr = i % 2 ? 0.17 * r : 0.4 * r, t = -Math.PI / 2 + (i * Math.PI) / 5;
        ctx[i ? 'lineTo' : 'moveTo'](Math.cos(t) * rr, Math.sin(t) * rr);
      }
      ctx.closePath(); ctx.fill();
    } else {
      ctx.strokeStyle = colors.inkSoft; ctx.lineWidth = 0.025 * r; ctx.beginPath(); ctx.arc(0, 0, 0.56 * r, 0, Math.PI * 2); ctx.stroke();
      ctx.fillStyle = color; ctx.font = `900 ${0.84 * r}px Roboto`; ctx.textAlign = 'center'; ctx.textBaseline = 'alphabetic';
      ctx.fillText('1', 0, 0.36 * 0.84 * r);
    }
    ctx.restore();
  };
  emblem(0.03 * r, colors.emboss);
  emblem(0, colors.ink);
  const spec = ctx.createRadialGradient(-0.38 * r, -0.45 * r, 0, -0.38 * r, -0.45 * r, 0.62 * r);
  spec.addColorStop(0, `rgba(255,255,255,${0.42 * facing})`); spec.addColorStop(1, 'rgba(255,255,255,0)');
  ctx.fillStyle = spec; ctx.beginPath(); ctx.arc(0, 0, 0.84 * r, 0, Math.PI * 2); ctx.fill();
  if (facing < 1) { ctx.fillStyle = `rgba(0,0,0,${(1 - facing) * 0.38})`; ctx.beginPath(); ctx.arc(0, 0, r, 0, Math.PI * 2); ctx.fill(); }
  ctx.restore();
}
export const COIN_DEFAULT = { base: '#FFFFFF', rim: '#A4A4A4', rimShade: '#717171', groove: 'rgba(113,113,113,.6)', faceLight: '#FFFFFF', faceShade: '#CACACA', ink: 'rgba(17,17,17,.9)', inkSoft: 'rgba(17,17,17,.32)', emboss: 'rgba(113,113,113,.45)' };

// Coin toss angle/lift at progress p of a toss from `fromDeg` with `halfTurns`.
export function coinAt(p, fromDeg = 0, halfTurns = 7) {
  const k = 1 - Math.pow(1 - clamp(p), 2.5);
  const lift = 4 * clamp(p) * (1 - clamp(p));
  return { angle: fromDeg + halfTurns * 180 * k, dy: -22 * lift, scale: 1 + 0.12 * lift, shadow: 1 - 0.4 * lift };
}

export function coinCard({ result = '', streak = 'Tap the coin to toss it', heads = 0, tails = 0, p = PAL.solid } = {}) {
  const pill = (label, n, hi) => `<div style="flex:1;border-radius:12px;padding:7px 10px;display:flex;align-items:center;background:${hi ? 'rgba(255,255,255,.26)' : p.chip}">
      <span style="flex:1;font:400 12px/1.172 Roboto;letter-spacing:.5px;color:${hi ? p.on : p.subtle}">${label}</span>
      <span style="font:700 15px/1.172 Roboto;color:${p.accent}">${n}</span></div>`;
  const total = heads + tails;
  const el = h(`<div style="display:flex;flex-direction:column;align-items:center">
      <div style="position:relative;width:100%;height:136px"><canvas class="coin" width="${288 * 3}" height="${136 * 3}" style="position:absolute;inset:0;width:288px;height:136px"></canvas></div>
      <div style="height:6px"></div>
      <div class="result" style="height:30px;font:600 22px/30px Roboto;letter-spacing:.5px;color:${p.on}">${result}</div>
      <div class="streak" style="height:18px;font:${streak.includes('×') ? 600 : 400} 12px/18px Roboto;letter-spacing:.5px;color:${streak.includes('×') ? p.accent : p.subtle}">${streak}</div>
      <div style="height:10px"></div>
      <div style="display:flex;gap:8px;align-self:stretch;align-items:center">${pill('Heads', heads, result === 'Heads')}${pill('Tails', tails, result === 'Tails')}
        <div style="width:36px;height:36px;border-radius:50%;background:${p.chip};display:grid;place-items:center;opacity:${total ? 1 : 0.35}">${icon('filled/refresh', 18, p.accent)}</div></div>
      <div style="align-self:stretch;padding-top:8px">${total ? `<div style="height:4px;border-radius:2px;background:rgba(255,255,255,.18);overflow:hidden"><div style="height:4px;width:${(heads / total) * 100}%;background:${p.accent}"></div></div>` : '<div style="height:4px"></div>'}</div>
      <div style="height:12px"></div>
      <div style="align-self:stretch">${button('Toss', { filled: true, p })}</div>
    </div>`);
  el.drawCoin = (state) => {
    const c = el.querySelector('canvas.coin');
    const ctx = c.getContext('2d');
    ctx.setTransform(3, 0, 0, 3, 0, 0);
    ctx.clearRect(0, 0, 288, 136);
    const sw = 86 * state.shadow, cyS = 136 - 7;
    const g = ctx.createRadialGradient(144, cyS, 0, 144, cyS, sw / 2);
    g.addColorStop(0, `rgba(0,0,0,${0.5 * state.shadow})`); g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.save(); ctx.translate(144, cyS); ctx.scale(1, 14 / sw); ctx.translate(-144, -cyS); ctx.fillStyle = g;
    ctx.beginPath(); ctx.arc(144, cyS, sw / 2, 0, Math.PI * 2); ctx.fill(); ctx.restore();
    ctx.save(); ctx.translate(144, 136 - 7 - 50 + state.dy); ctx.scale(state.scale, state.scale);
    drawCoin(ctx, 0, 0, 50, state.angle); ctx.restore();
  };
  return el;
}

export { PAL as DECK_PALETTES };
