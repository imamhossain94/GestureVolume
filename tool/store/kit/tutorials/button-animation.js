// Tutorial 10, "Button animation": a press of a volume key answered on the glass, beside the key.
// Turn it on, press a key on the home screen, pick animations (the preview plays each), hold the key
// for the balloon, line the press up with the phone's own key, sound and feel, and the Quick slider.
//
// The animation is the app's own, frame for frame: ButtonBumpView drawn by bump.mjs (Robolectric),
// on the whole glass when a key is pressed and in the settings screen's preview stage, which plays
// itself the way BumpStage does. The script reports the presses as T.bumpClips; bump.mjs renders
// them (node bump.mjs scene tutorial id=button-animation) before the video is made.
import { clamp, lerp, Ease, h } from '../core.js';
import { EdgeBar, PRESETS, loadDrawables } from '../quickpanel.js';
import { homeAppScreen, HOME_ICONS, SCREEN_ICONS } from '../screens.js';
import { bumpSettingsScreen, bumpScrollFor, bumpGalleryScroll, bumpStageGlass, BUMP_ICONS } from '../bumpsettings.js';
import { live } from '../tutorial.js';

export const WALLPAPER = 'ocean';
export const ICONS = [...HOME_ICONS, ...SCREEN_ICONS, ...BUMP_ICONS];
export const prepare = () => loadDrawables(['ic_vol_increase']);

const FPS = 30;
const DENSITY = 2.625;                                   // 412 dp = 1080 px
// This phone's keys, down its right side: a little lower than where the app first expects them
// (ButtonBumpStore.defaultPlace: 0.27, 0.36, 0.47), which "Where your buttons are" puts right.
const KEY = { up: 0.30, down: 0.385, power: 0.49 };
const PLACE = { up: 0.27, down: 0.36 };                   // the app's defaults
const SOUND = { water: 'drop', fish: 'splash', frog: 'ribbit', swing: 'zip', cat: 'meow', balloon: 'balloon' };   // ButtonBump.sound
const ease = Ease.fastOutSlowIn;
const r3 = (x) => Math.round(x * 1000) / 1000;

// A value gliding between settled values: [[at, dur, to], ...], sorted, from `from`.
const moves = (from, list) => (t) => {
  let v = from;
  for (const [at, dur, to] of list) {
    if (t < at) break;
    const k = ease(clamp((t - at) / dur));
    v += (to - v) * k;
    if (k < 1) break;
  }
  return v;
};
// A value that changes at moments: [[at, value], ...], sorted.
const steps = (from, list) => (t) => { let v = from; for (const [at, x] of list) if (t >= at) v = x; return v; };

// BumpStage's own loop (ButtonFeedbackScreen.kt): 180 ms after any change a press, and then on its
// own: one press, then one, two, one, three... 260 ms apart with 1.9 s between runs, the fourth
// press of five a volume down; the level steps 0.07 from 0.45. The balloon is also held now and
// then, as a thumb would: 1.6 s, and later 3.6 s, until it bursts.
function stageLoop(style, t0, t1, pressed = 'up') {
  const ev = [];
  let level = 0.45, n = 0, t = t0 + 0.18;
  while (t < t1) {
    const hold = style !== 'balloon' ? 0 : n % 6 === 3 ? 1.6 : n % 6 === 5 ? 3.6 : 0;
    if (hold > 0) {
      level = clamp(level + 0.07, 0.05, 0.95);
      ev.push({ t, type: 'down' }, { t, type: 'press', button: 'up', level: r3(level) }, { t: t + hold, type: 'up' });
      n++;
      t += hold + 1.9;
      continue;
    }
    const run = n % 6 === 2 ? 2 : n % 6 === 4 ? 3 : 1;
    for (let i = 0; i < run; i++) {
      const b = n === 0 ? pressed : n % 5 === 3 ? 'down' : 'up';
      level = clamp(level + (b === 'down' ? -0.07 : 0.07), 0.05, 0.95);
      if (t < t1) ev.push({ t, type: 'press', button: b, level: r3(level) });
      t += 0.26;
    }
    n++;
    t += 1.9;
  }
  return ev;
}

export async function script(T) {
  const { phone } = T;
  T.bumpClips = [];

  // --- The phone's keys and the glass ---------------------------------------------------------------
  const setKey = (el, pos, len) => { el.style.top = `${7 + pos * 915 - len / 2}px`; el.style.height = `${len}px`; };
  setKey(T.keys.up, KEY.up, 52);
  setKey(T.keys.down, KEY.down, 52);
  setKey(T.keys.power, KEY.power, 48);
  const kx = 423.5, ky = KEY.up * 915;                     // where a fingertip meets volume up, in phone dp
  const dock = new EdgeBar(phone, PRESETS.dock, { frac: 0.75 });
  const R0 = { ...dock.rect };
  const glassImg = h(`<img alt="" style="position:absolute;left:0;top:0;width:412px;height:915px;z-index:80;pointer-events:none;display:none">`);
  phone.el.appendChild(glassImg);
  const stageImg = h(`<img alt="" style="position:absolute;inset:0;width:100%;height:100%;pointer-events:none;display:none">`);

  // The clips the frames come from; their presses are placed below, in the scene's time.
  const glassClips = [], stageClips = [];
  const clip = (name, style, t0, t1, events, stage = false) => {
    const c = { name: `tut-${name}`, style, duration: r3(t1 - t0), fps: FPS,
      events: events.filter((e) => e.t >= t0 && e.t < t1).map((e) => ({ ...e, t: r3(e.t - t0) })) };
    if (stage) {
      const g = bumpStageGlass();
      Object.assign(c, { width: Math.round(g.w * DENSITY), height: Math.round(g.h * DENSITY), places: { up: { position: 0.5 }, down: { position: 0.5 } } });
    }
    T.bumpClips.push(c);
    const placed = { ...c, t0, t1 };
    (stage ? stageClips : glassClips).push(placed);
    return placed;
  };
  const frameOf = (c, t) => {
    const n = Math.min(Math.round((t - c.t0) * FPS), Math.ceil((c.t1 - c.t0) * FPS) - 1);
    return `../build/bump/${c.name}/${String(Math.max(0, n)).padStart(5, '0')}.png`;
  };
  // The level each press on the glass leaves: the media volume, a step (1/15) a press.
  let level = 0.4;
  const glassPress = (t, extra = {}) => { level = Math.min(0.95, level + 1 / 15); return { t: r3(t), type: 'press', button: 'up', level: r3(level), ...extra }; };

  // --- The screens -----------------------------------------------------------------------------------
  const tm = { toggle: 1e9 };
  let homeScroll = () => 0;
  const app = live((s) => homeAppScreen(s), (t) => ({ running: true, advanced: true, bump: t >= tm.toggle + 0.05, scroll: Math.round(homeScroll(t)) }),
    { running: true, advanced: true, bump: false, scroll: 0 });
  const BASE = { running: true, accessibility: true, withBump: 'pause', sound: true, places: { up: { position: PLACE.up } } };
  const makeBump = (s) => bumpSettingsScreen({ ...BASE, ...s });
  let bumpState = () => ({});
  const bump = live((s) => makeBump(s), (t) => bumpState(t), {});
  const m = (s, name) => T.measure(makeBump(s), name);
  const tapOn = (at, box, rect, name, sound = 'tap') => {
    T.hand({ at, tap: [rect.cx, rect.cy], sound });
    if (name) T.squeezes.push({ at: at + 0.02, el: box, name });
  };
  const inApp = [];

  // --- Turn it on ------------------------------------------------------------------------------------
  T.chapter('Turn it on');
  const s1 = T.step('Open Button animation', 'In Gesture Volume, under Advanced features, tap Button animation.', 7.2);
  T.hand({ at: s1.at + 0.3, enter: [T.iconRect.cx + 20, T.iconRect.cy + 70] });
  const open1 = T.launch(s1.at + 0.9, app);
  const homeSwipe = s1.at + 2.6;
  T.hand({ at: homeSwipe, swipe: [220, 760], to: [220, 560], dur: 0.5 });
  homeScroll = moves(0, [[homeSwipe + 0.14, 0.55, 200]]);
  const row = T.measure(homeAppScreen({ running: true, advanced: true, scroll: 200 }), 'feature-button-animation');
  const openAt = s1.at + 4.3;
  tapOn(openAt, app, row, 'feature-button-animation');
  T.show(openAt + 0.06, bump, 'push');

  const s2 = T.step('Turn it on', 'Switch on Animate the volume buttons. The preview at the top plays it, with nothing to press.', 6.8);
  const en = m({}, 'enable');
  tm.toggle = s2.at + 1.3;
  T.hand({ at: tm.toggle, tap: [en.cx, en.cy] }, { at: tm.toggle + 0.75, leave: true });
  T.cue(tm.toggle + 0.06, 'toggle', 0.45);
  T.ring(s2.at + 2.4, 3.0, m({}, 'stage'), { pad: 3, radius: 30 });

  const s3 = T.step('Press a volume key', 'Go home and press volume up: water ripples out from the very key you pressed.', 8.6);
  const home1 = T.goHome(s3.at + 0.3);
  inApp.push([open1, home1]);
  const p3 = [s3.at + 1.7, s3.at + 2.75, s3.at + 3.3, s3.at + 4.7];
  T.hand({ at: s3.at + 1.0, enter: [kx + 4, ky], tilt: -78 }, ...p3.map((at) => ({ at, tap: [kx + 4, ky], sound: 'click' })), { at: p3[3] + 1.0, leave: true });
  T.cam({ at: s3.at + 1.55, cx: 292, cy: 345, s: 1.5, dur: 0.75 });
  T.cam({ at: s3.end - 0.15, cx: 206, cy: 457, s: 1.0, dur: 0.75 });

  // --- Pick an animation ----------------------------------------------------------------------------------
  T.chapter('Pick an animation');
  const s4 = T.step('Pick one', 'Back in Button animation, scroll down to Animation and tap one. Every animation is free.', 7.8);
  const water1 = clip('glass-water', 'water', p3[0] - 0.1, s4.at + 1.4, p3.map((t) => glassPress(t + 0.06)));
  T.hand({ at: s4.at + 0.3, enter: [T.iconRect.cx + 20, T.iconRect.cy + 70] });
  const open2 = T.launch(s4.at + 0.8, bump);
  const ON = { enabled: true };
  const scA = bumpScrollFor('head-animation', ON);
  const pageSwipe1 = s4.at + 2.0;
  T.hand({ at: pageSwipe1, swipe: [200, 800], to: [200, 480], dur: 0.6 });
  const gal = m({ ...ON, scroll: scA }, 'gallery');
  const g1 = bumpGalleryScroll('fish');
  const galSwipe1 = s4.at + 3.0;
  T.hand({ at: galSwipe1, swipe: [330, gal.y + 70], to: [170, gal.y + 70], dur: 0.45 });
  const fishAt = s4.at + 4.0;
  tapOn(fishAt, bump, m({ ...ON, scroll: scA, galleryScroll: g1 }, 'tile-fish'), 'tile-fish');

  const s5 = T.step('Each with a sound of its own', 'Frog jump, Swing women, Cat, Balloons: tap one, and the preview plays it with its sound.', 9.0);
  const frogAt = s5.at + 0.9, swingAt = s5.at + 2.5, catAt = s5.at + 3.9;
  const g2 = bumpGalleryScroll('balloon');
  const galSwipe2 = s5.at + 5.0, balloonAt = s5.at + 6.2;
  const at1 = { ...ON, scroll: scA, galleryScroll: g1 };
  tapOn(frogAt, bump, m({ ...at1, style: 'fish' }, 'tile-frog'), 'tile-frog');
  tapOn(swingAt, bump, m({ ...at1, style: 'frog' }, 'tile-swing'), 'tile-swing');
  tapOn(catAt, bump, m({ ...at1, style: 'swing' }, 'tile-cat'), 'tile-cat');
  T.hand({ at: galSwipe2, swipe: [330, gal.y + 70], to: [140, gal.y + 70], dur: 0.45 });
  tapOn(balloonAt, bump, m({ ...ON, scroll: scA, galleryScroll: g2, style: 'cat' }, 'tile-balloon'), 'tile-balloon');
  T.hand({ at: balloonAt + 0.8, leave: true });
  const picks = [[fishAt, 'fish'], [frogAt, 'frog'], [swingAt, 'swing'], [catAt, 'cat'], [balloonAt, 'balloon']];
  for (const [at, style] of picks) T.cue(at + 0.06, `app:${SOUND[style]}`, 0.75);

  const s6 = T.step('Hold for a big one', 'On the glass, a press lets a balloon float away. Hold the key, and it fills until it bursts.', 9.6);
  const home2 = T.goHome(s6.at + 0.3);
  inApp.push([open2, home2]);
  const tap6 = s6.at + 1.7, hold6 = s6.at + 3.2, held = 3.45;
  T.hand({ at: s6.at + 1.0, enter: [kx + 4, ky], tilt: -78 }, { at: tap6, tap: [kx + 4, ky], sound: 'click' },
    { at: hold6, drag: [[kx + 4, ky], [kx + 4, ky]], dur: held - 0.2 }, { at: hold6 + held + 0.7, leave: true });
  T.cue(hold6 + 0.03, 'click', 0.8);
  T.cue(hold6 + held, 'clickup', 0.6);
  T.cam({ at: s6.at + 1.5, cx: 280, cy: 300, s: 1.45, dur: 0.75 });
  T.cam({ at: s6.end - 0.15, cx: 206, cy: 457, s: 1.0, dur: 0.75 });

  // --- Make it fit -------------------------------------------------------------------------------------------
  T.chapter('Make it fit');
  const s7 = T.step('Line it up with your keys', 'Under Where your buttons are, pick a key and slide Position. As you slide, the press shows on the glass, right beside it.', 9.4);
  const balloon1 = clip('glass-balloon', 'balloon', tap6 - 0.1, s7.at + 1.4, [
    glassPress(tap6 + 0.06),
    { t: r3(hold6 + 0.06), type: 'down' }, glassPress(hold6 + 0.06), { t: r3(hold6 + held), type: 'up' },
  ]);
  T.hand({ at: s7.at + 0.3, enter: [T.iconRect.cx + 20, T.iconRect.cy + 70] });
  const open3 = T.launch(s7.at + 0.8, bump);
  const scP = bumpScrollFor('head-place', ON);
  const pageSwipe2 = s7.at + 1.9;
  T.hand({ at: pageSwipe2, swipe: [200, 820], to: [200, 470], dur: 0.6 });
  const atP = { ...ON, scroll: scP, style: 'balloon' };
  const upChip = m(atP, 'chip-up');
  const chipAt = s7.at + 3.0;
  tapOn(chipAt, bump, upChip, 'chip-up');
  // The slider: its thumb from the app's 27 % to this phone's 30 %, held a moment, let go.
  const track = m(atP, 'slider-position');
  const thumbX = (f) => track.x + 2 + (track.w - 8) * f + 2;
  const dragAt = s7.at + 4.1, dragDur = 1.5;
  T.hand({ at: dragAt, drag: [[thumbX(PLACE.up), track.cy], [thumbX(KEY.up), track.cy]], dur: dragDur });
  T.hand({ at: dragAt + dragDur + 0.9, leave: true });
  const posAt = (t) => lerp(PLACE.up, KEY.up, Ease.inOut(clamp((t - dragAt - 0.11) / (dragDur - 0.11))));
  // The press on the glass: once for the chip, then every 140 ms as the slider moves, and once more
  // when it is let go (GLASS_PREVIEW_GAP_MS, showOnPhone): all at the place being set.
  const glassP = [glassPress(chipAt + 0.08, { position: PLACE.up })];
  for (let t = dragAt + 0.25; t < dragAt + dragDur; t += 0.28) glassP.push(glassPress(t, { position: r3(posAt(t)) }));
  glassP.push(glassPress(dragAt + dragDur + 0.1, { position: KEY.up }));
  T.cam({ at: dragAt - 0.1, cx: 300, cy: 380, s: 1.35, dur: 0.7 });
  T.cam({ at: s7.end - 0.1, cx: 206, cy: 457, s: 1.0, dur: 0.7 });

  const s8 = T.step('Sound and feel', 'Each animation plays its own sound at every press. Then pick how it buzzes, how long it stays and how fast it moves.', 8.6);
  const place1 = clip('glass-place', 'balloon', chipAt, s8.at + 3.0, glassP);
  const scS = bumpScrollFor('head-sound', ON);
  const pageSwipe3 = s8.at + 0.6;
  T.hand({ at: pageSwipe3, swipe: [200, 840], to: [200, 380], dur: 0.6 });
  const atS = { ...ON, scroll: scS, style: 'balloon', places: { up: { position: KEY.up } } };
  T.ring(s8.at + 1.6, 2.4, m(atS, 'row-sound'), { pad: 3, radius: 20 });
  const strongAt = s8.at + 4.6;
  tapOn(strongAt, bump, m(atS, 'haptic-strong'), 'haptic-strong');
  T.hand({ at: strongAt + 0.8, leave: true });

  const s9 = T.step('With the Quick slider, or alone', 'Pause plays the animation alone. Show both brings up the Quick slider with it.', 7.6);
  const scQ = bumpScrollFor('head-quick', ON);
  const pageSwipe4 = s9.at + 0.5;
  T.hand({ at: pageSwipe4, swipe: [200, 420], to: [200, 860], dur: 0.6 });
  const atQ = { ...ON, scroll: scQ, style: 'balloon', haptic: 'strong', places: { up: { position: KEY.up } } };
  const both = m(atQ, 'chip-both');
  const bothAt = s9.at + 2.0;
  tapOn(bothAt, bump, both, 'chip-both');
  T.hand({ at: bothAt + 0.9, leave: true });
  T.ring(s9.at + 2.6, 3.2, { x: both.x, y: both.y, w: m(atQ, 'chip-pause').x + m(atQ, 'chip-pause').w - both.x, h: both.h }, { pad: 6, radius: 18 });
  inApp.push([open3, 1e9]);

  // --- The settings screen, as the script moves it ------------------------------------------------------------
  const pageScroll = moves(0, [[pageSwipe1 + 0.12, 0.6, scA], [open3, 0.001, scA], [pageSwipe2 + 0.12, 0.6, scP], [pageSwipe3 + 0.12, 0.6, scS], [pageSwipe4 + 0.12, 0.6, scQ]]);
  const galleryScroll = moves(0, [[galSwipe1 + 0.12, 0.45, g1], [galSwipe2 + 0.12, 0.45, g2]]);
  const styleAt = steps('water', picks.map(([at, s]) => [at + 0.05, s]));
  bumpState = (t) => {
    const k = clamp((t - tm.toggle - 0.04) / 0.22);
    return {
      ...(t >= tm.toggle && k < 1 ? { enableK: Math.round(Ease.fastOutSlowIn(k) * 20) / 20 } : { enabled: t >= tm.toggle }),
      scroll: Math.round(pageScroll(t)),
      galleryScroll: Math.round(galleryScroll(t)),
      style: styleAt(t),
      places: { up: { position: Math.round((t < dragAt ? PLACE.up : posAt(t)) * 100) / 100 } },
      dragging: t >= dragAt && t < dragAt + dragDur + 0.1 ? 'position' : null,
      haptic: t >= strongAt + 0.05 ? 'strong' : 'light',
      withBump: t >= bothAt + 0.05 ? 'both' : 'pause',
    };
  };

  // The preview stage: BumpStage's loop, started over at every change that replays it.
  const restarts = [[openAt + 0.06, 'water'], [tm.toggle, 'water'], [open2, 'water'], ...picks.map(([at, s]) => [at + 0.05, s]),
    [open3, 'balloon'], [chipAt + 0.05, 'balloon'], [dragAt + dragDur + 0.05, 'balloon']].sort((a, b) => a[0] - b[0]);
  const hidden = [[home1, open2], [home2, open3]];
  restarts.forEach(([at, style], i) => {
    let end = i + 1 < restarts.length ? restarts[i + 1][0] : 1e9;
    const gap = hidden.find(([a, b]) => a > at && a < end);
    if (gap) end = gap[0] + 0.4;
    if (end > 1e8) end = s9.end + 6;
    clip(`stage-${i}-${style}`, style, at, end, stageLoop(style, at, end), true);
  });

  // Sounds the glass presses make: the animation's own at each, and any it asks for (the balloon's
  // hiss and bang), from what the renderer reported once the frames are made.
  for (const c of [water1, balloon1]) {
    for (const e of c.events) if (e.type === 'press') T.cue(c.t0 + e.t + 0.01, `app:${SOUND[c.style]}`, 0.85);
    const meta = await fetch(`../build/bump/${c.name}/meta.json`, { cache: 'no-store' }).then((r) => (r.ok ? r.json() : null)).catch(() => null);
    const cs = meta?.cues || [];
    cs.forEach((x, i) => {
      if (x.cue === 'inflate') {
        const stop = cs.slice(i + 1).find((d) => d.cue === 'inflate_stop' || d.cue === 'burst');
        T.cueList.push({ t: c.t0 + x.t, sfx: 'app:inflate', gain: 0.75, ...(stop ? { dur: stop.t - x.t } : {}) });
      } else if (x.cue !== 'inflate_stop') T.cue(c.t0 + x.t, `app:${x.cue}`, 0.95);
    });
  }

  // --- Every frame ---------------------------------------------------------------------------------------------------
  const keyPresses = [...p3, tap6].map((at) => [at, at + 0.3]).concat([[hold6, hold6 + held]]);
  let glassShown = null, stageShown = null;
  T.on((t) => {
    app.render(t);
    bump.render(t);
    // The stage's frames, in the preview's glass of whichever screen is built now.
    const box = bump.querySelector('.bump-glass');
    if (box && stageImg.parentNode !== box) box.appendChild(stageImg);
    const sc = stageClips.find((c) => t >= c.t0 && t < c.t1);
    const ss = sc ? frameOf(sc, t) : null;
    if (ss !== stageShown) { stageShown = ss; if (ss) stageImg.src = ss; stageImg.style.display = ss ? '' : 'none'; }
    // The overlay on the glass, over whatever is on screen.
    const gc = glassClips.find((c) => t >= c.t0 && t < c.t1);
    const gs = gc ? frameOf(gc, t) : null;
    if (gs !== glassShown) { glassShown = gs; if (gs) glassImg.src = gs; glassImg.style.display = gs ? '' : 'none'; }
    // Volume up going in under the finger.
    let d = 0;
    for (const [a, b] of keyPresses) if (t >= a && t < b + 0.15) d = Math.max(d, t < a + 0.08 ? clamp((t - a) / 0.08) : t < b ? 1 : 1 - clamp((t - b) / 0.15));
    T.keys.up.style.transform = `translateX(${-2.5 * d}px)`;
    T.keys.up.style.background = d > 0 ? 'rgba(196,181,253,.95)' : 'rgba(232,230,245,.95)';
    // The bar steps aside while Gesture Volume's own screens are in front.
    const inside = inApp.some(([a, b]) => t >= a + 0.2 && t < b);
    dock.draw({ alpha: inside ? 0 : 1, rect: R0 });
  });
}

export const THUMB = { t: 21.35, title: 'Button animation', hand: false };
