// Tutorial 3, "The Quick slider": turn on Advanced mode, then the Dock tab's slider: swipe it
// open, touch the track, the volume keys, and its fills and flourishes in the settings.
import { clamp, lerp, Ease, icon } from '../core.js';
import { EdgeBar, PRESETS, QuickPanel, loadDrawables, panelExpansion } from '../quickpanel.js';
import { homeAppScreen, HOME_ICONS, SCREEN_ICONS } from '../screens.js';
import { modeSwitchDialog } from '../appdialogs.js';
import { live } from '../tutorial.js';
import { quickSliderSettings, mountQuickSliderSettings, preparePanelSettings, PANELSETTINGS_ICONS } from '../panelsettings.js';
import { stepped, strokeSteps } from '../director.js';

export const WALLPAPER = 'dusk';
export const ICONS = [...HOME_ICONS, ...SCREEN_ICONS, ...PANELSETTINGS_ICONS, 'round/arrow_upward', 'round/touch_app', 'round/swipe_up'];
export async function prepare() {
  await loadDrawables(['ic_vol_increase', 'ic_move', 'ic_brightness_up']);
  await preparePanelSettings({ shaders: true });
}

const glyph = (name) => icon(name, 18, '#4F46E5');

export async function script(T) {
  const { phone } = T;
  const classic = new EdgeBar(phone, PRESETS.classic);
  const dock = new EdgeBar(phone, PRESETS.dock);
  const qp = new QuickPanel(phone, dock, { fill: { kind: 'solid' } });
  const D = dock.rect, PR = qp.panelRect;
  const bx = D.x + D.w / 2 - 1, by = D.y + D.h / 2;
  const yOf = (v) => PR.y + PR.h * (1 - v);
  const sessions = [];                 // each time the panel is up: { open, close, value(t), grabbed(t), committedAt }

  // --- Advanced mode ----------------------------------------------------------------------------------
  T.chapter('Turn on Advanced mode');
  const s1 = T.step('Turn on Advanced mode', 'The Quick slider comes with the Dock bar. Open Gesture Volume, switch on Advanced mode and confirm.', 9.4);
  const st = { flip: s1.at + 2.75, ok: s1.at + 5.35 };
  const appOf = (s) => {
    const el = homeAppScreen({ running: true, advanced: s.adv, advK: s.k });
    if (s.d > 0) el.appendChild(modeSwitchDialog({ to: 'advanced', k: s.d }));
    return el;
  };
  const app = live(appOf, (t) => {
    const d = t < st.flip + 0.12 ? 0 : t < st.ok + 0.1 ? clamp((t - st.flip - 0.12) / 0.2) : 1 - clamp((t - st.ok - 0.1) / 0.15);
    const k = t < st.ok + 0.12 ? 0 : Ease.fastOutSlowIn(clamp((t - st.ok - 0.12) / 0.25));
    return { adv: k >= 0.5, k: Math.round(k * 20) / 20, d: Math.round(d * 20) / 20 };
  });
  T.on((t) => app.render(t));
  const launched = T.launch(s1.at + 1.0, app);
  T.hand({ at: s1.at + 0.35, enter: [T.iconRect.cx + 20, T.iconRect.cy + 70] });
  const advRow = T.measure(appOf({ adv: false, k: 0, d: 0 }), 'advanced');
  const sw = [advRow.x + advRow.w - 14 - 26, advRow.cy];
  T.hand({ at: st.flip, tap: sw });
  const okBtn = T.measure(appOf({ adv: false, k: 0, d: 1 }), 'switch');
  T.hand({ at: st.ok, tap: [okBtn.cx, okBtn.cy] });
  T.ring(st.flip - 0.9, 1.1, advRow, { pad: 4, radius: 22 });

  const s2 = T.step('Meet the Dock', 'Back on your home screen, the round button is now the Dock: a slim tab on the edge.', 5.6);
  const home = T.goHome(s2.at + 0.35);
  T.hand({ at: s2.at + 1.0, leave: true });
  T.cam({ at: s2.at + 1.9, cx: 300, cy: 262, s: 1.85, dur: 0.9 });
  T.ring(s2.at + 2.4, 2.4, D, { pad: 6, radius: 12 });

  // --- Swipe it open ------------------------------------------------------------------------------------
  T.chapter('Open it');
  const s3 = T.step('Swipe the tab up or down', 'It grows into the Quick slider, and every bit you swipe moves the level one step.', 7.4);
  const sw1 = { at: s3.at + 1.25, y0: by + 40, y1: by - 112, dur: 1.15 };
  T.hand({ at: s3.at + 0.55, enter: [bx, sw1.y0], tilt: -50 }, { at: sw1.at, swipe: [bx, sw1.y0], to: [bx, sw1.y1], dur: sw1.dur }, { at: sw1.at + 2.0, leave: true });
  T.pill(sw1.at - 0.2, sw1.dur + 1.2, 'Swipe up', 250, 360, glyph('round/arrow_upward'));
  const steps1 = strokeSteps(sw1.at, sw1.y0, sw1.y1, sw1.dur);
  const lift1 = sw1.at + 0.11 + sw1.dur + 0.09;
  sessions.push({ open: steps1[0], close: lift1 + 0.55, value: (t) => stepped(t, steps1.map((a, i) => ({ at: a, v: (6 + i + 1) / 15 })), 6 / 15) });

  const s4 = T.step('Touch the track to set it', 'Touch anywhere on the open track: the level jumps to your finger and follows it. Let go and it tucks back in.', 8.6);
  const sw2 = { at: s4.at + 1.05, y0: by + 30, y1: by - 10, dur: 0.38 };
  const lift2 = sw2.at + 0.11 + sw2.dur + 0.09;
  const touch = { at: lift2 + 0.24, from: 0.3, to: 0.86, dur: 1.5 };
  T.hand({ at: s4.at + 0.4, enter: [bx, sw2.y0], tilt: -50 }, { at: sw2.at, swipe: [bx, sw2.y0], to: [bx, sw2.y1], dur: sw2.dur, ease: Ease.fastOutSlowIn },
    { at: touch.at, drag: [[PR.x + 16, yOf(touch.from)], [PR.x + 16, yOf(touch.from)], [PR.x + 16, yOf(touch.to)]], dur: touch.dur, lead: 0.2, ease: (k) => (k < 0.3 ? 0 : Ease.inOut((k - 0.3) / 0.7)) },
    { at: touch.at + touch.dur + 1.2, leave: true });
  const steps2 = strokeSteps(sw2.at, sw2.y0, sw2.y1, sw2.dur);
  const dragFrom = touch.at + 0.11, dragEnd = touch.at + 0.11 + touch.dur, lift3 = dragEnd + 0.09;
  T.pill(touch.at + 0.1, touch.dur + 0.6, 'Touch and drag', 250, 360, glyph('round/touch_app'));
  sessions.push({
    open: steps2[0], close: lift3 + 0.55,
    value: (t) => {
      if (t < dragFrom) return stepped(t, steps2.map((a, i) => ({ at: a, v: (11 + i + 1) / 15 })), 11 / 15);
      const k = clamp((t - dragFrom) / touch.dur);
      return lerp(touch.from, touch.to, k < 0.3 ? 0 : Ease.inOut((k - 0.3) / 0.7));
    },
    grabbed: (t) => t >= dragFrom && t < lift3,
  });

  // --- Volume keys --------------------------------------------------------------------------------------
  const s5 = T.step('Or press a volume key', 'With Volume keys on Instant, a key opens the slider on the spot and moves it one step per press. Instant needs the accessibility service.', 8.4);
  const key = T.keys.up;
  const kx = 419.5, ky = 279 + 42;                           // the volume-up key, in phone dp
  const presses = [s5.at + 1.3, s5.at + 1.85, s5.at + 2.4, s5.at + 2.95];
  T.hand({ at: s5.at + 0.6, enter: [kx + 4, ky], tilt: -78 }, ...presses.map((at) => ({ at, tap: [kx + 4, ky], sound: 'press' })), { at: presses[3] + 0.9, leave: true });
  T.cam({ at: s5.at + 0.8, cx: 318, cy: 262, s: 1.85, dur: 0.6 });
  const base5 = 0.86 * 15 | 0;                              // where the drag left it, in steps
  sessions.push({
    open: presses[0] + 0.05, close: presses[3] + 0.05 + 4.0, committedAt: presses[0] + 0.05 + 0.22,
    value: (t) => stepped(t, presses.map((a, i) => ({ at: a + 0.05, v: Math.min(15, base5 - 3 + i + 1) / 15 })), (base5 - 3) / 15),
  });
  T.on((t) => {
    let d = 0;
    for (const a of presses) if (t >= a && t < a + 0.3) d = Math.max(d, Math.sin(Math.PI * clamp((t - a) / 0.3)));
    key.style.transform = `translateX(${-2.5 * d}px)`;
    key.style.background = d > 0 ? `rgba(196,181,253,${0.95})` : 'rgba(232,230,245,.95)';
  });

  // --- Its settings: a fill, the flourish, and what it adjusts ------------------------------------------------
  T.chapter('Fills and what it adjusts');
  const s6 = T.step('Open its settings', 'In Gesture Volume, under Advanced features, tap Quick slider. The preview runs your slider as it is.', 6.6);
  T.cam({ at: s6.at + 0.2, cx: 206, cy: 457, s: 1.0, dur: 0.6 });
  const app2 = homeAppScreen({ running: true, advanced: true });
  T.hand({ at: s6.at + 0.3, enter: [T.iconRect.cx + 20, T.iconRect.cy + 70] });
  const launched2 = T.launch(s6.at + 0.9, app2);
  const qsAt = s6.at + 3.2;
  T.tap(qsAt, app2, 'feature-quick-slider');
  const tq = { anim: 1e9, shader: 1e9, aurora: 1e9, sparkle: 1e9, content: 1e9, bright: 1e9 };
  const scrolls = [];
  const scrollAt = (t) => { let v = 0; for (const x of scrolls) if (t >= x.at) v = v + (x.to - v) * Ease.fastOutSlowIn(clamp((t - x.at) / 0.6)); return Math.round(v); };
  const openAt = (t) => {
    const k = (a) => Math.round(Ease.fastOutSlowIn(clamp((t - a - 0.06) / 0.35)) * 20) / 20;
    if (t >= tq.content) { const x = k(tq.content); return x >= 1 ? 'content' : { animation: 1 - x, content: x }; }
    if (t >= tq.anim) { const x = k(tq.anim); return x >= 1 ? 'animation' : { content: 1 - x, animation: x }; }
    return 'content';
  };
  const fillAt = (t) => (t >= tq.aurora + 0.06 ? { kind: 'shader', effect: 'aurora' } : t >= tq.shader + 0.06 ? { kind: 'shader', effect: 'lava-lamp' } : { kind: 'solid' });
  // The picture rows keep the scroll they opened with when a tile is picked.
  const ROWS = { fill: 0, effect: 0, flourish: 0 };
  const qsState = (t) => ({ open: openAt(t), fill: fillAt(t), flourish: t >= tq.sparkle + 0.06 ? 'sparkle' : 'burst', target: t >= tq.bright + 0.06 ? 'brightness' : 'adaptive', scroll: scrollAt(t), rowScroll: ROWS });
  const qs = live((st) => quickSliderSettings(st), qsState, { open: 'content', fill: { kind: 'solid' }, flourish: 'burst', target: 'adaptive', scroll: 0, rowScroll: ROWS });
  const drawQs = mountQuickSliderSettings(qs);
  const qsMeasure = (st, name) => T.measure(quickSliderSettings({ open: 'content', fill: { kind: 'solid' }, flourish: 'burst', target: 'adaptive', scroll: 0, rowScroll: ROWS, ...st }), name);
  T.show(qsAt + 0.06, qs, 'push');

  const s7 = T.step('Pick a fill', 'Open Animation and choose a fill. Shaders and Surge are live effects; the preview at the top runs each one.', 10.2);
  const animHdr = qsMeasure({}, 'section-animation');
  tq.anim = s7.at + 0.9;
  T.hand({ at: tq.anim - 0.06, tap: [animHdr.cx, animHdr.cy] });
  const shaderTile = qsMeasure({ open: 'animation' }, 'fill-shader');
  const sA = Math.max(0, Math.round(shaderTile.cy - 560));
  scrolls.push({ at: s7.at + 1.8, to: sA });
  tq.shader = s7.at + 3.4;
  const shaderR = qsMeasure({ open: 'animation', scroll: sA }, 'fill-shader');
  T.hand({ at: tq.shader - 0.06, tap: [shaderR.cx, shaderR.cy] });
  const auroraR0 = qsMeasure({ open: 'animation', fill: { kind: 'shader', effect: 'lava-lamp' } }, 'effect-aurora');
  const sB = Math.max(sA, Math.round(auroraR0.cy - 600));
  scrolls.push({ at: s7.at + 4.6, to: sB });
  tq.aurora = s7.at + 6.4;
  const auroraR = qsMeasure({ open: 'animation', fill: { kind: 'shader', effect: 'lava-lamp' }, scroll: sB }, 'effect-aurora');
  T.hand({ at: tq.aurora - 0.06, tap: [auroraR.cx, auroraR.cy] });
  T.ring(s7.at + 7.6, 2.2, qsMeasure({}, 'preview'), { pad: 3, radius: 30 });

  const s8 = T.step('Celebrate at the top', 'With Celebrate at the top on, a flourish plays when the level reaches 100%. Pick the one you like.', 8.8);
  const fl0 = qsMeasure({ open: 'animation', fill: { kind: 'shader', effect: 'aurora' } }, 'flourish-sparkle');
  const sC = Math.max(sB, Math.round(fl0.cy - 620));
  scrolls.push({ at: s8.at + 0.5, to: sC });
  const flR = qsMeasure({ open: 'animation', fill: { kind: 'shader', effect: 'aurora' }, scroll: sC }, 'flourish-sparkle');
  T.ring(s8.at + 1.3, 1.6, qsMeasure({ open: 'animation', fill: { kind: 'shader', effect: 'aurora' }, scroll: sC }, 'celebrate'), { pad: 4, radius: 20 });
  tq.sparkle = s8.at + 3.2;
  T.hand({ at: tq.sparkle - 0.06, tap: [flR.cx, flR.cy] });

  const s9 = T.step('Choose what it adjusts', 'Under Content, pick Brightness or one volume. Adaptive follows whatever is playing: a call, an alarm, or media.', 8.8);
  scrolls.push({ at: s9.at + 0.4, to: 0 });
  const contentHdr = qsMeasure({ open: 'animation', fill: { kind: 'shader', effect: 'aurora' } }, 'section-content');
  tq.content = s9.at + 1.5;
  T.hand({ at: tq.content - 0.06, tap: [contentHdr.cx, contentHdr.cy] });
  const brightR = qsMeasure({ fill: { kind: 'shader', effect: 'aurora' } }, 'target-brightness');
  tq.bright = s9.at + 3.3;
  T.hand({ at: tq.bright - 0.06, tap: [brightR.cx, brightR.cy] }, { at: tq.bright + 0.8, leave: true });
  T.ring(s9.at + 4.4, 2.4, qsMeasure({ fill: { kind: 'shader', effect: 'aurora' }, target: 'brightness' }, 'preview'), { pad: 3, radius: 30 });
  T.on((t) => { qs.render(t); drawQs({ t }); });

  // --- Sound -------------------------------------------------------------------------------------------
  [...steps1, ...steps2].forEach((t) => T.cue(t, 'tick', 0.55));
  sessions.forEach((s) => T.cue(s.open, 'open', 0.35));
  presses.forEach((t) => T.cue(t + 0.06, 'tick', 0.5));

  // --- Every frame --------------------------------------------------------------------------------------
  T.on((t) => {
    // The bar: the round button until the switch, the Dock after; hidden while the app is open and
    // while the Quick slider is up.
    const inApp = (t >= launched + 0.2 && t < home) || t >= launched2 + 0.2;
    classic.draw({ alpha: t < launched + 0.2 ? 1 : 0 });
    let e = 0, cur = null;
    for (const s of sessions) if (t >= s.open && t < s.close + 0.4) { cur = s; e = panelExpansion(t, s.open, s.close); }
    const dockAlpha = t >= home && !inApp && !(cur && t >= cur.open + 0.07 && t < cur.close + 0.18) ? 1 : 0;
    dock.draw({ alpha: dockAlpha });
    qp.canvas.style.display = e > 0 ? 'block' : 'none';
    if (e > 0) {
      qp.draw({ e, value: cur.value(t), t, grabbed: cur.grabbed ? cur.grabbed(t) : false, committed: cur.committedAt ? t >= cur.committedAt : true,
        entrance: clamp((t - cur.open) / 0.21) });
    }
  });
}

export const THUMB = { t: 66.7, title: 'Quick slider', cam: { cx: 206, cy: 236, s: 1.9, deg: -5 } };
