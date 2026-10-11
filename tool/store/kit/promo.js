// The 30-second promo, cut to the promo bed's bars (112 bpm, 2.143 s a bar). One timeline, two
// framings: 1920 x 1080 for YouTube / Google Play and 1080 x 1920 for Shorts.
import { h, icon, loadIcons, fontsReady, imagesReady, clamp, lerp, Ease, spring } from './core.js';
import { Phone, homeScreen, gvIcon, PHONE_ICONS } from './phone.js';
import { EdgeBar, QuickPanel, PRESETS, loadDrawables, panelExpansion, entranceFrame } from './quickpanel.js';
import { Deck, DECK_ICONS, searchCard, timerCard, TILES } from './deck.js';
import { LongPressMenu, MENU_ICONS } from './menu.js';
import { keyboard, indicator, SYSTEM_ICONS } from './system.js';
import { handSvg, setHand } from './hand.js';
import { Backdrop, caption, showBetween } from './stage.js';
import { HandScript, stepped, typed, typingCues, Camera, prog } from './director.js';

const BAR = 60 / 112 * 4;
export const PROMO = { s1: BAR, s2: 3 * BAR, s3: 5 * BAR, s4: 7 * BAR, s5: 9 * BAR, s6: 11 * BAR, end: 12 * BAR, total: 30 };

// Where an element sits on the phone, in dp (measure while the phone is at scale 1).
function rectOf(el, phoneEl) {
  const a = el.getBoundingClientRect(), p = phoneEl.getBoundingClientRect();
  return { x: a.left - p.left, y: a.top - p.top, w: a.width, h: a.height, cx: a.left - p.left + a.width / 2, cy: a.top - p.top + a.height / 2 };
}

export async function buildPromo(root, { vertical = false } = {}) {
  const W = vertical ? 1080 : 1920, H = vertical ? 1920 : 1080;
  await loadIcons([...new Set([...PHONE_ICONS, ...DECK_ICONS, ...MENU_ICONS, ...SYSTEM_ICONS, 'filled/flashlight_on'])]);
  await loadDrawables(['ic_vol_increase', 'ic_move', 'ic_brightness_up']);
  const FILLS = { plasma: { kind: 'shader', effect: 'plasma' }, flame: { kind: 'surge', look: 'flame' }, pixels: { kind: 'pixels' }, aurora: { kind: 'shader', effect: 'aurora' } };
  await QuickPanel.shadersFor(Object.values(FILLS));
  await fontsReady();
  root.style.cssText = `position:relative;width:${W}px;height:${H}px;overflow:hidden;background:#100B33`;

  const bd = new Backdrop(W, H, { seed: 21 });
  root.appendChild(bd.el);

  // --- The phone and everything on it -------------------------------------------------------------
  const world = h(`<div style="position:absolute;inset:0"></div>`);
  root.appendChild(world);
  const frame = h(`<div style="position:absolute;left:0;top:0;transform-origin:0 0;padding:7px;border-radius:53px;background:rgba(255,255,255,.92);
      box-shadow:0 40px 90px rgba(8,5,30,.55),0 10px 30px rgba(8,5,30,.35)"></div>`);
  const phone = new Phone({ wallpaper: 'dusk', radius: 46 });
  frame.appendChild(phone.el);
  world.appendChild(frame);
  phone.below.appendChild(homeScreen());

  const dock = new EdgeBar(phone, PRESETS.dock);
  const variants = [['classic'], ['edge', '#FFF59E0B'], ['bold']].map(([id, color]) => new EdgeBar(phone, PRESETS[id], color ? { override: { color } } : {}));
  const teal = new EdgeBar(phone, PRESETS.dock, { override: { color: '#FF14B8A6' } });
  const qpSolid = new QuickPanel(phone, dock, { fill: { kind: 'solid' } });
  const qps = Object.fromEntries(Object.entries(FILLS).map(([k, f]) => [k, new QuickPanel(phone, dock, { fill: f })]));
  const deck = new Deck(phone, dock, { people: [{ name: 'Mom', via: 'call' }, { name: 'Alex Kim', via: 'sms' }], apps: ['camera', 'music'],
    tiles: ['search', 'timer', 'flashlight', 'media', 'volume', 'notes'] });
  const timerIdle = timerCard({ time: '05:00' });
  const timerRun = timerCard({ time: '05:00', running: true });
  const search = searchCard({ query: '', focused: true });
  const cardEl = deck.showCard('timer', timerIdle);
  const cardContent = cardEl.querySelector('.content');
  const cardHeader = cardEl.querySelector('.hdr');
  const kbSym = keyboard({ layout: 'symbols' }), kbLet = keyboard({ layout: 'letters' });
  phone.overlay.append(kbSym, kbLet);
  const menu = new LongPressMenu(phone, dock, { theme: 'glass' });
  const toast = indicator('Flashlight on', icon('filled/flashlight_on', 20, '#fff'));
  phone.overlay.appendChild(toast);
  // The hand lives on the frame, not in the screen, so it can reach in over the phone's edge.
  const handWrap = h(`<div style="position:absolute;left:7px;top:7px;width:412px;height:915px;pointer-events:none;z-index:5">${handSvg(412, 915)}</div>`);
  frame.appendChild(handWrap);
  const hand = handWrap.querySelector('svg');

  await imagesReady(root);
  // Measure what the hand will touch, with everything laid out at rest.
  deck.set({});
  const tile = (id) => deck.tileCentre(id);
  const startBtn = rectOf([...timerIdle.querySelectorAll('div')].find((d) => d.textContent === 'Start'), phone.el);
  const flashTile = menu.tileCentre(4);

  // --- The script -----------------------------------------------------------------------------------
  const P = PROMO;
  const D = dock.rect, PR = qpSolid.panelRect;
  const barX = D.x + D.w / 2 - 1;
  // S1: a swipe up the bar opens the Quick panel and steps the volume.
  const s1 = { swipeAt: P.s1 + 1.05, from: 268, to: 118, dur: 1.0 };
  const s2 = { at: P.s2 + 0.35 };
  const ypos = (v) => PR.y + PR.h * (1 - v);
  const script = new HandScript([
    { at: P.s1 + 0.55, enter: [barX, s1.from], fw: 32 },
    { at: s1.swipeAt, swipe: [barX, s1.from], to: [barX, s1.to], dur: s1.dur, ease: Ease.inOut },
    { at: P.s1 + 2.65, leave: true },
    // S2: open again, take the track, and run it to the top.
    { at: s2.at, enter: [barX, 262], fw: 32 },
    { at: s2.at + 0.3, swipe: [barX, 262], to: [barX, 232], dur: 0.32 },
    { at: s2.at + 0.95, drag: [[PR.x + 16, ypos(0.8)], [PR.x + 16, ypos(0.8)], [PR.x + 16, PR.y - 4]], dur: 2.15, keep: true, ease: (k) => (k < 0.66 ? 0 : Ease.inOut((k - 0.66) / 0.34)) },
    { at: s2.at + 3.35, lift: true },
    { at: s2.at + 3.6, leave: true },
    // S3: swipe in for the Deck, start a timer.
    { at: P.s3 + 0.45, enter: [barX, 236] },
    { at: P.s3 + 0.75, swipe: [barX, 236], to: [300, 242], dur: 0.32 },
    { at: P.s3 + 1.6, tap: [tile('timer')[0], tile('timer')[1]] },
    { at: P.s3 + 2.55, tap: [startBtn.cx, startBtn.cy] },
    { at: P.s3 + 3.05, leave: true },
    // S4: search.
    { at: P.s4 - 0.15, enter: [tile('search')[0] - 20, tile('search')[1] + 40] },
    { at: P.s4 + 0.3, tap: [tile('search')[0], tile('search')[1]] },
    { at: P.s4 + 0.85, leave: true },
    // S5: close the Deck, hold the bar for the menu, turn the flashlight on.
    { at: P.s5 - 0.25, enter: [200, 520] },
    { at: P.s5 + 0.1, tap: [200, 520], sound: 'close' },
    { at: P.s5 + 0.75, hold: [D.x + D.w / 2, D.y + D.h / 2], dur: 1.25 },
    { at: P.s5 + 2.7, tap: flashTile, sound: 'toggle' },
    { at: P.s5 + 2.98, leave: true },
  ]);

  // When the swipe crosses the touch slop the panel opens; every 32 dp after that is a step.
  const swipeY = (t) => s1.from - (s1.from - s1.to) * Ease.inOut(clamp((t - s1.swipeAt - 0.11) / s1.dur));
  const s1Events = [];
  let s1Open = null;
  for (let t = s1.swipeAt; t < s1.swipeAt + 1.3; t += 0.001) {
    const travel = s1.from - swipeY(t);
    if (travel >= 8 && s1Open == null) s1Open = t;
    const steps = travel >= 8 ? 1 + Math.floor((travel - 8) / 32) : 0;
    if (steps > s1Events.length) s1Events.push({ at: t, v: 0.4 + steps / 15 });
  }
  const s1Close = s1.swipeAt + 0.11 + s1.dur + 0.09 + 0.55;
  const s2Open = s2.at + 0.3 + 0.16;
  const dragStep = script.steps.find((s) => s.drag);
  const handAt = (t) => script.state(t);
  let fullAt = null;
  for (let t = dragStep.at; t < dragStep.at + 2.4; t += 0.002) { if (1 - (handAt(t).y - PR.y) / PR.h >= 0.995) { fullAt = t; break; } }
  const s2Release = s2.at + 3.35;
  const s2Close = s2Release + 0.55;
  const fillCuts = [[s2Open, 'plasma'], [s2.at + 1.25, 'flame'], [s2.at + 1.9, 'pixels'], [s2.at + 2.5, 'aurora']];
  const deckOpen = P.s3 + 0.75 + 0.11 + 0.17;
  const timerOpen = P.s3 + 1.66, runFrom = P.s3 + 2.6;
  const searchOpen = P.s4 + 0.36;
  const sum = { at: P.s4 + 0.9, text: '12*8+4' }, name = { at: P.s4 + 2.95, text: 'al' };
  const deckClose = P.s5 + 0.15;
  const holdAt = P.s5 + 0.75, cueAt = holdAt + 0.4, releaseHold = holdAt + 1.25;
  const menuClose = P.s5 + 2.76;
  const BEAT = BAR / 4;
  const presetCuts = [[P.s6 + 0.1, variants[0]], [P.s6 + BEAT, variants[1]], [P.s6 + 2 * BEAT, variants[2]], [P.s6 + 3 * BEAT, teal]];

  // --- Camera ---------------------------------------------------------------------------------------
  const F = vertical ? { x: 540, y: 1200 } : { x: 1360, y: 540 };
  const base = vertical ? 1.42 : 1.04;
  const cam = new Camera(vertical ? [
    { at: 0, cx: 206, cy: 457, s: base },
    { at: P.s1 + 0.95, cx: 300, cy: 217, s: 2.6, dur: 0.75 },
    { at: P.s2 + 0.3, cx: 330, cy: 217, s: 3.0, dur: 0.6 },
    { at: P.s3 + 0.55, cx: 206, cy: 330, s: 1.75, dur: 0.6 },
    { at: P.s4 + 0.5, cx: 206, cy: 470, s: 1.42, dur: 0.5 },
    { at: P.s4 + 1.15, cx: 196, cy: 175, s: 2.2, dur: 0.55 },
    { at: P.s5 + 0.65, cx: 255, cy: 230, s: 2.2, dur: 0.55 },
    { at: P.s6 + 0.3, cx: 340, cy: 217, s: 3.0, dur: 0.5 },
    { at: P.end + 0.4, cx: 206, cy: 457, s: 1.0, dur: 0.6 },
  ] : [
    { at: 0, cx: 206, cy: 457, s: base },
    { at: P.s1 + 0.95, cx: 330, cy: 217, s: 2.2, dur: 0.75 },
    { at: P.s2 + 0.3, cx: 352, cy: 217, s: 2.75, dur: 0.6 },
    { at: P.s3 + 0.55, cx: 210, cy: 250, s: 1.7, dur: 0.6 },
    { at: P.s4 + 0.5, cx: 206, cy: 470, s: 1.12, dur: 0.5 },
    { at: P.s4 + 1.15, cx: 200, cy: 190, s: 1.9, dur: 0.55 },
    { at: P.s5 + 0.65, cx: 290, cy: 230, s: 2.0, dur: 0.55 },
    { at: P.s6 + 0.3, cx: 352, cy: 217, s: 2.4, dur: 0.5 },
    { at: P.end + 0.4, cx: 206, cy: 457, s: 0.8, dur: 0.6 },
  ]);

  // --- Words ----------------------------------------------------------------------------------------
  const veil = h(`<div style="position:absolute;inset:0;pointer-events:none;background:${vertical
    ? 'linear-gradient(180deg,rgba(16,11,51,.96) 0%,rgba(16,11,51,.9) 24%,rgba(16,11,51,0) 34%)'
    : 'linear-gradient(90deg,rgba(16,11,51,.95) 0%,rgba(16,11,51,.88) 38%,rgba(16,11,51,0) 56%)'}"></div>`);
  root.appendChild(veil);
  const CAPS = [
    [P.s1, P.s2, 'Volume on the edge', 'Swipe the bar and the Quick slider follows your finger'],
    [P.s2, P.s3, 'Fills that move', 'Live shaders, your colours and a flourish at 100%'],
    [P.s3, P.s4, 'Pull out the Deck', 'Apps, people and tools, one swipe in'],
    [P.s4, P.s5, 'Answers as you type', 'Apps, people, sums and the web'],
    [P.s5, P.s6, 'Hold for more', 'A long-press menu of your own'],
    [P.s6, P.end, 'Make it yours', 'Presets, sizes, shapes and colours'],
  ].map(([a, b, title, sub]) => {
    const el = h(`<div style="position:absolute;${vertical ? 'left:80px;right:80px;top:250px;text-align:center' : 'left:150px;width:760px;top:50%;margin-top:-120px'}">
        <div style="font:800 ${vertical ? 92 : 96}px/1.02 'Plus Jakarta Sans';letter-spacing:-.03em;color:#fff;text-wrap:balance">${title}</div>
        <div style="margin-top:26px;font:500 ${vertical ? 42 : 40}px/1.3 'Plus Jakarta Sans';color:#C9C2FF;text-wrap:balance">${sub}</div></div>`);
    root.appendChild(el);
    return { el, a: a + 0.15, b: b - 0.12 };
  });
  const brand = h(`<div style="position:absolute;left:0;top:0;display:flex;align-items:center;gap:22px;transform-origin:0 0">
      ${gvIcon(120, 'squircle')}<div style="font:800 64px/1 'Plus Jakarta Sans';letter-spacing:-.02em;color:#fff;white-space:nowrap">Gesture Volume</div></div>`);
  root.appendChild(brand);
  const brandW = brand.getBoundingClientRect().width;
  const endCard = h(`<div style="position:absolute;left:0;right:0;top:${vertical ? 640 : 250}px;display:flex;flex-direction:column;align-items:center;text-align:center">
      <div class="i">${gvIcon(vertical ? 220 : 190, 'squircle')}</div>
      <div class="n" style="margin-top:40px;font:800 ${vertical ? 104 : 96}px/1 'Plus Jakarta Sans';letter-spacing:-.03em;color:#fff">Gesture Volume</div>
      <div class="g" style="margin-top:28px;font:600 ${vertical ? 46 : 44}px/1.25 'Plus Jakarta Sans';color:#C9C2FF;padding:0 60px">Volume. Brightness. Everything else.</div></div>`);
  root.appendChild(endCard);

  // --- Sound ----------------------------------------------------------------------------------------
  const cues = [...script.cues()];
  s1Events.forEach((e) => cues.push({ t: e.at, sfx: 'tick', gain: 0.9 }));
  cues.push({ t: s1Open, sfx: 'open', gain: 0.45 });
  cues.push({ t: s2Open, sfx: 'open', gain: 0.45 });
  fillCuts.slice(1).forEach(([t]) => cues.push({ t, sfx: 'whoosh', gain: 0.35 }));
  if (fullAt) cues.push({ t: fullAt, sfx: 'sparkle', gain: 0.9 });
  cues.push({ t: deckOpen, sfx: 'open', gain: 0.6 });
  cues.push(...typingCues(sum.text, sum.at), ...typingCues(name.text, name.at));
  cues.push({ t: sum.at + sum.text.length * 0.13 + 0.12, sfx: 'success', gain: 0.6 });
  cues.push({ t: cueAt, sfx: 'open', gain: 0.4 });
  presetCuts.forEach(([t]) => cues.push({ t, sfx: 'toggle', gain: 0.45 }));
  cues.push({ t: 1.55, sfx: 'whoosh', gain: 0.5 }, { t: P.end, sfx: 'whoosh', gain: 0.4 });

  // --- A frame --------------------------------------------------------------------------------------
  function seek(t) {
    bd.draw(t);
    // Intro: the icon and name, then up to the corner as the phone rises.
    const fly = Ease.inOut(prog(t, 1.45, 0.6));
    const pop = spring(t - 0.12, { dampingRatio: 0.55, stiffness: 300 });
    const bigX = (W - brandW) / 2, bigY = H / 2 - 60;
    const smallX = vertical ? (W - 0.5 * brandW) / 2 : 150, smallY = vertical ? 110 : 92;
    const bs = lerp(1, 0.5, fly);
    brand.style.transform = `translate(${lerp(bigX, smallX, fly)}px,${lerp(bigY, smallY, fly)}px) scale(${bs * (t < 1.45 ? clamp(pop, 0, 1.2) : 1)})`;
    brand.style.opacity = clamp(t / 0.15) * (1 - prog(t, P.end - 0.4, 0.35));
    // Camera and the phone's rise.
    const c = cam.at(t);
    const rise = vertical ? 1500 : 1150;
    const enter = 1 - Ease.emphasizedDecel(prog(t, 1.7, 0.75));
    const leave = Ease.fastOutLinearIn(prog(t, P.end - 0.1, 0.6));
    frame.style.transform = `translate(${F.x - c.cx * c.s - 7 * c.s}px,${F.y - c.cy * c.s - 7 * c.s + enter * rise + leave * 260}px) scale(${c.s})`;
    frame.style.opacity = 1 - leave;
    veil.style.opacity = t < P.s1 ? 0 : 1 - leave;
    CAPS.forEach((k) => showBetween(k.el, t, k.a, k.b, { rise: 30 }));

    // The bar(s).
    let barAlpha = 1, cue = 0;
    const s1Ret = s1Close + 0.18, s2Ret = s2Close + 0.18;
    if ((t >= s1Open + 0.07 && t < s1Ret) || (t >= s2Open + 0.07 && t < s2Ret)) barAlpha = 0;
    if (t >= deckOpen && t < deckClose + 0.21 + 0.16) barAlpha = t < deckClose + 0.21 ? 1 - prog(t, deckOpen, 0.16) : prog(t, deckClose + 0.21, 0.16);
    if (t >= cueAt) cue = t < releaseHold ? prog(t, cueAt, 0.1) : 1 - prog(t, releaseHold, 0.1);
    let variant = null;
    for (const [at, v] of presetCuts) if (t >= at && t < P.end + 0.6) variant = { at, v };
    if (variant) barAlpha = 0;
    dock.draw({ alpha: barAlpha * (t < P.end + 0.6 ? 1 : 0), cue });
    for (const v of [...variants, teal]) {
      const on = variant && variant.v === v;
      v.canvas.style.display = on ? 'block' : 'none';
      if (on) {
        const k = spring(t - variant.at, { dampingRatio: 0.5, stiffness: 600 });
        const cx = v.rect.x + v.rect.w, cy = v.rect.y + v.rect.h / 2;
        v.canvas.style.transformOrigin = `${cx}px ${cy}px`;
        v.canvas.style.transform = `scale(${lerp(0.6, 1, k)})`;
        v.draw({ alpha: clamp(k * 1.5) });
      }
    }

    // The Quick panel: S1 solid, S2 the fills.
    const e1 = panelExpansion(t, s1Open ?? 1e9, s1Close);
    qpSolid.canvas.style.display = e1 > 0 ? 'block' : 'none';
    if (e1 > 0) qpSolid.draw({ e: e1, value: stepped(t, s1Events, 0.4), t, entrance: prog(t, s1Open, 0.21) });
    const e2 = panelExpansion(t, s2Open, s2Close);
    let fillNow = 'plasma', cutAt = s2Open;
    for (const [at, f] of fillCuts) if (t >= at) { fillNow = f; cutAt = at; }
    const st = handAt(t);
    const onTrack = t >= dragStep.at + 0.05 && t < s2Release;
    const v2 = onTrack ? clamp(1 - (st.y - PR.y) / PR.h) : t >= s2Release ? 1 : stepped(t, [{ at: s2Open, v: 0.4 + 6 / 15 }], 0.4 + 5 / 15);
    for (const [k, qp] of Object.entries(qps)) {
      const on = e2 > 0 && k === fillNow;
      qp.canvas.style.display = on ? 'block' : 'none';
      if (on) qp.draw({ e: e2, value: v2, t, fillTime: t + 9, grabbed: onTrack, entrance: prog(t, s2Open, 0.21), stepS: cutAt > s2Open ? t - cutAt : -1, fullS: fullAt && t >= fullAt ? t - fullAt : -1 });
    }

    // The Deck: open on the swipe, cards for the timer and search, closed by a tap outside.
    const open = t < deckOpen + 0.03 ? 0 : t < deckClose ? prog(t, deckOpen + 0.03, 0.21) : 1 - prog(t, deckClose, 0.21);
    let card = 0;
    if (t >= timerOpen) card = prog(t, timerOpen, 0.22);
    if (t >= searchOpen) card = 1;
    if (t >= deckClose + 0.21) card = 0;
    // Card content: idle timer, the running timer, then search.
    const want = t >= searchOpen ? search : t >= runFrom ? timerRun : timerIdle;
    if (cardContent.firstChild !== want) { cardContent.innerHTML = ''; cardContent.appendChild(want); swapHeader(cardHeader, want === search ? 'search' : 'timer'); }
    if (want === timerRun) {
      const left = 300 - Math.floor(t - runFrom);
      timerRun.querySelector('.time').textContent = `${String(Math.floor(left / 60)).padStart(2, '0')}:${String(left % 60).padStart(2, '0')}`;
    }
    if (want === search) {
      const q1 = typed(t, sum.text, sum.at), q2 = typed(t, name.text, name.at);
      const q = t >= name.at - 0.15 ? q2 : q1;
      const showSum = q === sum.text && t >= sum.at + sum.text.length * 0.13 + 0.12;
      const showName = q === name.text && t >= name.at + 0.4;
      const key = q + '|' + showSum + '|' + showName + '|' + (Math.floor(t * 2) % 2);
      if (search.dataset.key !== key) {
        search.dataset.key = key;
        const fresh = searchCard({ query: q, focused: true, caret: Math.floor(t * 2) % 2 === 0, answer: showSum ? '100' : null,
          people: showName ? [{ name: 'Alex Kim', sub: 'SMS · +1 555 0132' }] : [] });
        search.innerHTML = fresh.innerHTML;
      }
    }
    deck.setActive('timer', t >= timerOpen);
    deck.setActive('search', t >= searchOpen);
    deck.set({ open, card, alpha: t >= deckOpen ? 1 : 0 });
    if (t < deckOpen) deck.root.style.visibility = 'hidden';
    // Keyboard: up with the search, symbols for the sum, letters for the name.
    const kbUp = t < searchOpen ? 0 : t < deckClose ? Ease.fastOutSlowIn(prog(t, searchOpen, 0.25)) : 1 - Ease.fastOutSlowIn(prog(t, deckClose, 0.25));
    const letters = t >= name.at - 0.15;
    kbSym.style.transform = kbLet.style.transform = `translateY(${(1 - kbUp) * 330}px)`;
    kbSym.style.display = kbUp > 0 && !letters ? 'block' : 'none';
    kbLet.style.display = kbUp > 0 && letters ? 'block' : 'none';

    // The menu, then the flashlight's indicator.
    const mOpen = t < cueAt + 0.03 ? 0 : t < menuClose ? prog(t, cueAt + 0.03, 0.21) : 1 - prog(t, menuClose, 0.21);
    menu.set({ open: mOpen, pressed: t >= P.s5 + 2.7 && t < menuClose + 0.1 ? 4 : -1, press: 1 });
    toast.style.display = t >= menuClose + 0.05 && t < menuClose + 0.95 ? 'flex' : 'none';

    // The hand.
    const hs = handAt(t);
    setHand(hand, { ...hs, fingerWidth: hs.fw ?? 46, mirror: hs.side === 'left', tilt: hs.tilt ?? (hs.side === 'left' ? 24 : -28) });

    // End card.
    const ep = t - P.end - 0.15;
    endCard.style.opacity = clamp(ep / 0.3);
    endCard.querySelector('.i').style.transform = `scale(${Math.max(0, spring(ep, { dampingRatio: 0.55, stiffness: 260 }))})`;
    endCard.querySelector('.n').style.transform = `translateY(${(1 - Ease.emphasizedDecel(prog(ep, 0.3, 0.5))) * 40}px)`;
    endCard.querySelector('.n').style.opacity = prog(ep, 0.3, 0.4);
    endCard.querySelector('.g').style.opacity = prog(ep, 0.65, 0.5);
  }

  function swapHeader(header, id) {
    if (header.dataset.id === id) return;
    header.dataset.id = id;
    header.innerHTML = `${icon(TILES[id].icon, 20, '#FFFFFF')}<div style="width:10px"></div>
      <div style="flex:1;font:600 16px/1.172 Roboto;letter-spacing:.5px;color:#fff">${TILES[id].label}</div>
      <div style="width:48px;height:48px;display:grid;place-items:center">${icon('filled/close', 24, 'rgba(255,255,255,.62)')}</div>`;
  }

  const geom = { D, PR, card: { x: deck.cardX, y: deck.stripY, w: deck.cardW }, strip: { x: deck.stripX, y: deck.stripY, w: deck.stripW, h: deck.stripH }, menu: menu.box, flashTile, startBtn };
  return { width: W, height: H, duration: P.total, cues, music: 'promo:1:12', musicGain: 0.6, seek, geom };
}
