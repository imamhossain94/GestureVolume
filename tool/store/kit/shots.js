// The Play Store phone screenshots: nine scenes from store/play-listing-en.md's shot list, each
// drawn by the kit and framed two ways at 1080 x 1920 — "card" (the whole screen under a
// caption) and "close" (closer in on the part that matters).
import { h, icon, loadIcons, fontsReady, imagesReady, clamp } from './core.js';
import { Phone, homeScreen, PHONE_ICONS, SCREEN, paintWallpaper } from './phone.js';
import { EdgeBar, QuickPanel, PRESETS, loadDrawables, drawDrawable, tabPoints, DPR } from './quickpanel.js';
import { Deck, DECK_ICONS, searchCard, mediaCard, coinCard, coinAt } from './deck.js';
import { LongPressMenu, MENU_ICONS } from './menu.js';
import { appearanceScreen, quickSliderScreen, visibilityScreen, mountQuickSlider, SCREEN_ICONS } from './screens.js';
import { keyboard, videoFrame, SYSTEM_ICONS } from './system.js';
import { handSvg, setHand } from './hand.js';
import { Backdrop, BRAND } from './stage.js';
import * as M from './m3.js';

export const ALL_ICONS = [...new Set([...PHONE_ICONS, ...DECK_ICONS, ...MENU_ICONS, ...SCREEN_ICONS, ...SYSTEM_ICONS])];
export const DRAWABLES = ['ic_vol_increase', 'ic_vol_decrease', 'ic_move', 'ic_brightness_up', 'ic_mute'];

export const HERO_FILL = { kind: 'shader', effect: 'plasma' };
const PEOPLE = [{ name: 'Mom', via: 'call' }, { name: 'Alex Kim', via: 'whatsapp' }];

function addHand(phone, pose) {
  const wrap = h(`<div style="position:absolute;inset:0;pointer-events:none">${handSvg(SCREEN.w, SCREEN.h)}</div>`);
  phone.top.appendChild(wrap);
  const svg = wrap.querySelector('svg');
  setHand(svg, { fingerWidth: 44, tilt: -28, ...pose });
  return svg;
}

export const SHOTS = [
  {
    id: 'swipe', title: 'Volume on the edge', sub: 'Swipe the bar and the Quick slider follows your finger',
    place: { card: { x: 64, s: 1.5 }, bleed: { x: -150, s: 1.96 } },
    magnify: {
      card: { region: { x: 332, y: 92, w: 80, h: 250 }, scale: 3.6, x: 712, y: 540, r: 40, join: [[0, 0], [0, 1]] },
      bleed: { region: { x: 332, y: 92, w: 80, h: 250 }, scale: 3.9, x: 740, y: 540, r: 40, join: [[0, 0], [0, 1]] },
    },
    build(phone) {
      phone.below.appendChild(homeScreen());
      const bar = new EdgeBar(phone, PRESETS.dock);
      const qp = new QuickPanel(phone, bar, { fill: window.SHOT_FILL || HERO_FILL });
      const v = 0.72;
      return (t) => { bar.draw({ alpha: 0 }); qp.draw({ e: 1, value: v, t: 2.2 + t, fillTime: 11 + t }); };
    },
    wallpaper: 'dusk',
  },
  {
    id: 'deck', title: 'Pull out the Deck', sub: 'Your apps, people and tools, one swipe in from the bar',
    focus: { x: 0, y: 36, w: 412, h: 600 },
    build(phone) {
      phone.below.appendChild(homeScreen());
      const bar = new EdgeBar(phone, PRESETS.dock);
      const d = new Deck(phone, bar, { people: PEOPLE, apps: ['camera', 'music', 'maps'] });
      d.showCard('media', mediaCard({ playing: true, volume: 0.62 }));
      d.setActive('media', true);
      return () => { bar.draw({ alpha: 0 }); d.set({}); };
    },
    wallpaper: 'ocean',
  },
  {
    id: 'rotate', title: 'Turns with your phone', sub: 'On its side, the bar lies along the top edge',
    landscape: true, focusX: 250,
    magnify: {
      card: { region: { x: 52, y: 0, w: 280, h: 54 }, scale: 3.2, x: 92, y: 1060, r: 30, join: [[0, 1], [1, 1]] },
    },
    cardY: { card: 1340 },
    build(phone) {
      phone.status.style.display = 'none';
      phone.pill.style.display = 'none';
      phone.below.appendChild(videoFrame(915, 412, { progress: 0.38 }));
      const c = document.createElement('canvas');
      c.width = 915 * DPR; c.height = 412 * DPR;
      Object.assign(c.style, { position: 'absolute', left: 0, top: 0, width: '915px', height: '412px' });
      phone.overlay.appendChild(c);
      return () => drawLyingPanel(c.getContext('2d'), { cx: 0.21 * 915, value: 0.6 });
    },
  },
  {
    id: 'fills', title: 'Fills that move', sub: 'Live shaders, your colours and a flourish at 100%',
    focus: { x: 0, y: 36, w: 412, h: 640 }, darkIcons: true,
    build(phone) {
      const el = quickSliderScreen({ effect: 'aurora', scroll: 0 });
      phone.below.appendChild(el);
      const draw = mountQuickSlider(el, { effect: 'aurora', value: 0.7 });
      return (t) => draw({ t: 3 + t, value: 0.7 });
    },
  },
  {
    id: 'search', title: 'Answers as you type', sub: 'Find apps and people, or work out a sum, right in the Deck',
    focus: { x: 0, y: 36, w: 412, h: 560 },
    build(phone) {
      phone.below.appendChild(homeScreen());
      const bar = new EdgeBar(phone, PRESETS.dock);
      const d = new Deck(phone, bar, { people: PEOPLE, apps: ['camera', 'music', 'maps'] });
      d.showCard('search', searchCard({ query: '12*8+4', answer: '100' }));
      d.setActive('search', true);
      phone.overlay.appendChild(keyboard({ layout: 'symbols', dark: true }));
      return () => { bar.draw({ alpha: 0 }); d.set({}); };
    },
    wallpaper: 'midnight',
  },
  {
    id: 'appearance', title: 'Make it yours', sub: 'Presets, size, shape, colour and icon, with a live preview',
    focus: { x: 0, y: 36, w: 412, h: 640 }, darkIcons: true,
    build(phone) {
      phone.below.appendChild(appearanceScreen({ preset: 'dock', open: 'size' }));
      return () => {};
    },
  },
  {
    id: 'menu', title: 'Hold for more', sub: 'A long-press menu of your own, in glass or solid',
    focus: { x: 96, y: 36, w: 316, h: 420 },
    build(phone) {
      phone.below.appendChild(homeScreen());
      const bar = new EdgeBar(phone, PRESETS.dock);
      const m = new LongPressMenu(phone, bar, { theme: 'glass' });
      return () => { bar.draw({ cue: 1 }); m.set({}); };
    },
    wallpaper: 'sunset',
  },
  {
    id: 'visibility', title: 'Out of the way', sub: 'Hide the bar in the apps you choose, or while you type',
    focus: { x: 0, y: 36, w: 412, h: 640 }, darkIcons: true,
    build(phone) {
      phone.below.appendChild(visibilityScreen({ tab: 'all', scroll: 286 }));
      return () => {};
    },
  },
  {
    id: 'coin', title: 'Heads or tails', sub: 'A coin, dice, a timer and notes, all in the Deck',
    focus: { x: 0, y: 36, w: 412, h: 560 },
    build(phone) {
      phone.below.appendChild(homeScreen());
      const bar = new EdgeBar(phone, PRESETS.dock);
      const d = new Deck(phone, bar, { tiles: ['search', 'flashlight', 'coin', 'dice', 'timer', 'calculator', 'notes'] });
      const cc = coinCard({ heads: 4, tails: 3 });
      d.showCard('coin', cc);
      d.setActive('coin', true);
      return () => { bar.draw({ alpha: 0 }); d.set({}); cc.drawCoin(coinAt(0.31, 0, 7)); };
    },
    wallpaper: 'forest',
  },
];

// The Dock lying along the top edge (Dynamic position, phone on its side) with its Quick panel
// open: the upright panel turned so its length runs across and its fill grows to the right, the
// number and icon kept upright.
export function drawLyingPanel(ctx, { cx, value, thickness = 32, length = 220, flare = 0.22 }) {
  ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
  ctx.clearRect(0, 0, 915, 412);
  const x0 = cx - length / 2;
  const pts = tabPoints(thickness, length, flare, true);
  const path = new Path2D();
  pts.forEach(([px, py], i) => path[i ? 'lineTo' : 'moveTo'](x0 + py, px));
  path.closePath();
  ctx.fillStyle = '#1C1C20';
  ctx.fill(path);
  ctx.save();
  ctx.clip(path);
  const fillEnd = x0 + length * value;
  const size = 10.88;
  const numX = x0 + length - 35 - size * 0.5, iconX = x0 + 35 + 7.36;
  const drawContents = (ink) => {
    ctx.font = `700 ${size}px Roboto`; ctx.fillStyle = ink; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.fillText(String(Math.floor(value * 100)), numX, thickness / 2 + 0.5);
    drawDrawable(ctx, 'ic_vol_increase', iconX - 7.36, thickness / 2 - 7.36, 14.72, ink);
  };
  drawContents('#FFFFFF');
  ctx.beginPath(); ctx.rect(x0, 0, fillEnd - x0, thickness); ctx.clip();
  ctx.fillStyle = '#FFFFFF'; ctx.fillRect(x0, 0, fillEnd - x0, thickness);
  drawContents('#1C1C20');
  ctx.restore();
}

// --- Framing ------------------------------------------------------------------------------------
export async function composeShot(root, def, style, { W = 1080, H = 1920 } = {}) {
  await loadIcons(ALL_ICONS);
  await loadDrawables(DRAWABLES);
  await QuickPanel.shadersFor([{ kind: 'shader', effect: 'aurora' }, ...['curve', 'electric', 'honeycomb', 'streaks', 'blocks', 'flame'].map((l) => ({ kind: 'surge', look: l })), ...['lava-lamp', 'liquid-chrome', 'molten', 'ink-smoke', 'plasma', 'caustics', 'soap-film', 'mesh', 'clouds', 'smoke', 'starfield'].map((e) => ({ kind: 'shader', effect: e }))]);
  await fontsReady();
  const light = style === 'bleed';
  root.style.cssText = `position:relative;width:${W}px;height:${H}px;overflow:hidden`;
  const bd = new Backdrop(W, H, { light, seed: 5 + SHOTS.indexOf(def) });
  root.appendChild(bd.el);
  bd.draw(SHOTS.indexOf(def) * 3.1);
  const cap = h(`<div style="position:absolute;left:60px;right:60px;top:${light ? 104 : 112}px;text-align:center">
      <div style="font:800 ${light ? 82 : 84}px/1.05 'Plus Jakarta Sans';letter-spacing:-.025em;color:${light ? '#1E1B4B' : '#FFFFFF'};text-wrap:balance">${def.title}</div>
      <div style="margin:22px auto 0;max-width:860px;font:500 36px/1.3 'Plus Jakarta Sans';letter-spacing:-.005em;color:${light ? '#4F46E5' : '#C9C2FF'};text-wrap:balance">${def.sub}</div></div>`);
  root.appendChild(cap);
  const sw = def.landscape ? 915 : SCREEN.w, sh = def.landscape ? 412 : SCREEN.h;
  const makePhone = () => {
    const ph = new Phone({ wallpaper: def.wallpaper || null, radius: 46, darkIcons: !!def.darkIcons });
    if (def.landscape) { ph.el.style.width = sw + 'px'; ph.el.style.height = sh + 'px'; }
    if (!def.wallpaper) ph.wall.style.display = 'none';
    return ph;
  };
  const phone = makePhone();
  const draws = [def.build(phone)];
  const top = light ? 440 : 452;
  // Where the screen goes: x, y of its top-left and its scale, unless the shot says otherwise.
  let place;
  if (style === 'card') {
    const availH = H - top - 70, availW = W - 120;
    const sc = def.landscape ? Math.min(availW / sw, 1.12) : Math.min(availH / sh, availW / sw);
    place = { x: (W - sw * sc) / 2, y: def.landscape ? top + 40 : top, s: sc };
  } else {
    const sc = def.landscape ? 1.62 : 2.02;
    place = { x: def.landscape ? W / 2 - (def.focusX ?? 240) * sc : (W - sw * sc) / 2, y: def.landscape ? top + 60 : top + 8, s: sc };
  }
  Object.assign(place, def.place?.[style] || {});
  const rim = light ? 8 : 7;
  const frame = h(`<div style="position:absolute;left:${place.x - rim}px;top:${place.y - rim}px;padding:${rim}px;border-radius:${46 * place.s + rim}px;
      background:${light ? '#FFFFFF' : 'rgba(255,255,255,.92)'};box-shadow:${light ? '0 30px 80px rgba(49,46,129,.28),0 8px 24px rgba(49,46,129,.18)' : '0 40px 90px rgba(8,5,30,.55),0 10px 30px rgba(8,5,30,.35)'}"></div>`);
  const holder = h(`<div style="width:${sw * place.s}px;height:${sh * place.s}px;border-radius:${46 * place.s}px;overflow:hidden;position:relative"></div>`);
  phone.el.style.transform = `scale(${place.s})`;
  holder.appendChild(phone.el);
  frame.appendChild(holder);
  root.appendChild(frame);

  // A magnifier: the same scene built again and shown larger in a window of its own, joined to
  // the part of the screen it enlarges.
  const mg = def.magnify?.[style];
  if (mg) {
    const r = mg.region;
    const ph2 = makePhone();
    draws.push(def.build(ph2));
    const bw = r.w * mg.scale, bh = r.h * mg.scale;
    const src = { x: place.x + r.x * place.s, y: place.y + r.y * place.s, w: r.w * place.s, h: r.h * place.s };
    const line = light ? 'rgba(79,70,229,.55)' : 'rgba(255,255,255,.55)';
    const corners = mg.join || [[0, 0], [1, 0], [1, 1], [0, 1]];
    root.appendChild(h(`<svg width="${W}" height="${H}" style="position:absolute;left:0;top:0;pointer-events:none">
        ${corners.map(([a, b]) => `<line x1="${src.x + src.w * a}" y1="${src.y + src.h * b}" x2="${mg.x + bw * a}" y2="${mg.y + bh * b}" stroke="${line}" stroke-width="3" stroke-dasharray="2 9" stroke-linecap="round"/>`).join('')}
        <rect x="${src.x}" y="${src.y}" width="${src.w}" height="${src.h}" rx="${Math.min(14, src.w / 4)}" fill="none" stroke="${light ? '#4F46E5' : '#FFFFFF'}" stroke-width="4"/></svg>`));
    const box = h(`<div style="position:absolute;left:${mg.x - 7}px;top:${mg.y - 7}px;padding:7px;border-radius:${(mg.r ?? 34) + 7}px;background:${light ? '#FFFFFF' : 'rgba(255,255,255,.92)'};
        box-shadow:${light ? '0 30px 70px rgba(49,46,129,.32)' : '0 30px 80px rgba(8,5,30,.6)'}">
        <div class="mag" style="position:relative;width:${bw}px;height:${bh}px;border-radius:${mg.r ?? 34}px;overflow:hidden"></div></div>`);
    ph2.el.style.transform = `translate(${-r.x * mg.scale}px,${-r.y * mg.scale}px) scale(${mg.scale})`;
    box.querySelector('.mag').appendChild(ph2.el);
    root.appendChild(box);
  }
  if (def.landscape) root.appendChild(dynamicCard(W, def.cardY?.[style] ?? place.y + sh * place.s + 100, light));
  const draw = (t) => draws.forEach((d) => d(t));
  await imagesReady(root);
  return draw;
}

// The Position group's "Dynamic position" switch, floated under the landscape phone.
function dynamicCard(W, y, light = false) {
  const w = 760;
  return h(`<div style="position:absolute;left:${(W - w) / 2}px;top:${y}px;width:${w}px">
      <div style="transform:scale(1.85);transform-origin:0 0;width:${w / 1.85}px;border-radius:20px;background:#FFFFFF;padding:16px 18px;box-shadow:0 10px 30px ${light ? 'rgba(49,46,129,.22)' : 'rgba(8,5,30,.35)'}">
        ${M.switchRow('Dynamic position', true)}
        ${M.bodySmall('Moves the bar with the screen when you rotate the phone. It keeps to the same edge of the phone and lies along the top or bottom in landscape.', '#4B5563', 'margin-top:4px')}
      </div></div>`);
}
