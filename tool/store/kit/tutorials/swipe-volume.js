// Tutorial 2, "Volume with a swipe": the Simple style's round button (the Classic preset). Swipe it
// for volume, tap it for Android's own volume slider, hold it and drag it somewhere else.
import { clamp, lerp, Ease, icon } from '../core.js';
import { EdgeBar, PRESETS, loadDrawables } from '../quickpanel.js';
import { LongPressMenu, MENU_ICONS } from '../menu.js';
import { systemVolumePanel, SYSTEM_ICONS } from '../system.js';
import { stepped, prog, along, strokeSteps } from '../director.js';

export const ICONS = [...MENU_ICONS, ...SYSTEM_ICONS, 'round/arrow_downward', 'round/touch_app', 'round/open_with'];
export const WALLPAPER = 'ocean';
export const prepare = () => loadDrawables(['ic_vol_increase', 'ic_move', 'ic_mute', 'ic_visibility_hide', 'ic_power', 'ic_app_open']);

const pct = (v) => `${Math.round(v * 100)}%`;
const glyph = (name) => icon(name, 18, '#4F46E5');

export async function script(T) {
  const { phone } = T;
  const bar = new EdgeBar(phone, PRESETS.classic);
  const R0 = { ...bar.rect };
  const bx = R0.x + R0.w / 2, by = R0.y + R0.h / 2;
  const menu = new LongPressMenu(phone, bar, { entrance: 'pop' });
  // Android's volume slider, below the button so both stay in view.
  const sys = systemVolumePanel(0.4, { y: 300 });
  phone.overlay.appendChild(sys);
  const lvl = sys.querySelector('.lvl');
  const track = { x: 412 - 12 - 29, top: 300 + 12 + 22 + 10, h: 190 };

  // --- Meet the button ------------------------------------------------------------------------------
  T.chapter('Swipe for volume');
  const s0 = T.step('Meet the round button', 'In the Simple style, a small round button sits on the edge of your screen, on top of every app.', 5.4);
  T.cam({ at: s0.at + 1.1, cx: 300, cy: 300, s: 1.75, dur: 0.9 });
  T.ring(s0.at + 1.9, 2.7, R0, { pad: 7, radius: 22 });

  // --- Swipe for volume -----------------------------------------------------------------------------
  const s1 = T.step('Swipe up on it', 'Each bit you swipe turns the volume up a step. The level shows on the button, and Android’s volume slider comes up beside it.', 7.8);
  const up = { at: s1.at + 1.5, y0: by + 34, y1: by - 132, dur: 1.15 };
  T.hand({ at: s1.at + 0.75, enter: [bx, up.y0], tilt: -50 }, { at: up.at, swipe: [bx, up.y0], to: [bx, up.y1], dur: up.dur }, { at: s1.at + 4.2, leave: true });
  T.pill(up.at - 0.2, up.dur + 1.3, 'Swipe up', 236, 240, glyph('round/arrow_upward'));
  const upSteps = strokeSteps(up.at, up.y0, up.y1, up.dur);

  const s2 = T.step('Swipe down to turn it down', 'Swipe the other way and it steps back down just as quickly.', 6.4);
  const dn = { at: s2.at + 1.05, y0: by - 34, y1: by + 128, dur: 1.05 };
  T.hand({ at: s2.at + 0.4, enter: [bx, dn.y0], tilt: -50 }, { at: dn.at, swipe: [bx, dn.y0], to: [bx, dn.y1], dur: dn.dur }, { at: s2.at + 3.5, leave: true });
  T.pill(dn.at - 0.2, dn.dur + 1.3, 'Swipe down', 236, 240, glyph('round/arrow_downward'));
  const dnSteps = strokeSteps(dn.at, dn.y0, dn.y1, dn.dur);

  // --- Tap for the slider ------------------------------------------------------------------------------
  T.chapter('Tap it, hold it, move it');
  const s3 = T.step('Tap it for the volume slider', 'A tap brings up Android’s own volume slider, ready to drag.', 7.2);
  const tapAt = s3.at + 1.2;
  const lv0 = 6 + upSteps.length - dnSteps.length;            // the level the swipes left
  const dragAt = tapAt + 1.05, dragDur = 0.95;
  const yOf = (v) => track.top + track.h * (1 - v);
  T.hand({ at: s3.at + 0.55, enter: [bx, by + 24], tilt: -50 }, { at: tapAt, tap: [bx, by] },
    { at: dragAt, drag: [[track.x, yOf(lv0 / 15) + 6], [track.x, yOf(12 / 15)]], dur: dragDur, ease: Ease.fastOutSlowIn },
    { at: dragAt + dragDur + 0.55, leave: true });
  T.pill(tapAt - 0.25, 1.4, 'Tap', 300, 240, glyph('round/touch_app'));

  // --- Move it ----------------------------------------------------------------------------------------
  const s4 = T.step('Hold it to pick it up', 'Keep your finger on it until it dims and shows arrows. The long-press menu opens too, and steps aside as soon as you move.', 6.6);
  T.cam({ at: s4.at + 0.5, ...{ cx: 206, cy: 457, s: 1.0 }, dur: 0.9 });
  const holdAt = s4.at + 1.6;
  const s5 = T.step('Drag it anywhere, then let go', 'Up or down the edge, or across to the other side. Let go and it settles on the nearer edge.', 7.6);
  const still = s5.at + 0.5 - holdAt, mdur = 1.7;
  const path = [[bx, by], [318, 330], [190, 520], [64, 610]];
  T.hand({ at: holdAt - 0.65, enter: [bx, by] }, { at: holdAt, drag: path, holdFor: still, dur: still + mdur }, { at: holdAt + 0.2 + still + mdur + 0.5, leave: true });
  T.pill(holdAt + 0.45, still - 0.3, 'Hold', 230, 470, glyph('round/touch_app'));
  const cueAt = holdAt + 0.4;                                   // the long-press timeout
  const moveAt = holdAt + 0.11 + still;                         // the finger starts to travel
  const release = holdAt + 0.2 + still + mdur;
  const fingerAt = (t) => along(path, Ease.inOut(clamp((t - moveAt) / mdur)));
  const dropX = clamp(path[path.length - 1][0] - bx + R0.x, 0, 412 - R0.w);
  const dropY = path[path.length - 1][1] - by + R0.y;
  T.ring(release + 0.35, 2.2, { x: 0, y: dropY, w: R0.w, h: R0.h }, { pad: 7, radius: 22 });

  // --- Sound ------------------------------------------------------------------------------------------
  [...upSteps, ...dnSteps].forEach((t) => T.cue(t, 'tick', 0.5));
  T.cue(tapAt + 0.18, 'open', 0.35);
  T.cue(cueAt, 'open', 0.4);
  T.cue(release + 0.05, 'toggle', 0.4);

  // --- Every frame -------------------------------------------------------------------------------------
  const steps = [
    ...upSteps.map((t, i) => ({ at: t, v: (6 + i + 1) / 15 })),
    ...dnSteps.map((t, i) => ({ at: t, v: (6 + upSteps.length - i - 1) / 15 })),
  ];
  const lastUp = upSteps[upSteps.length - 1], lastDn = dnSteps[dnSteps.length - 1];
  const sysWindows = [[upSteps[0], lastDn + 3.0], [tapAt + 0.18, dragAt + dragDur + 3.0]];
  T.on((t) => {
    // The level: the swipes' steps, then the slider dragged by hand.
    let v = stepped(t, steps, 6 / 15);
    if (t >= dragAt + 0.11) {
      const k = Ease.fastOutSlowIn(clamp((t - dragAt - 0.11) / dragDur));
      v = lerp(lv0 / 15, 12 / 15, k);
    }
    // The button: the readout for 700 ms after each stroke's last step; dimmed with arrows while
    // held; carried by the finger; then it flies to the nearer edge in 180 ms.
    const readout = (t >= upSteps[0] && t < lastUp + 0.7) || (t >= dnSteps[0] && t < lastDn + 0.7) ? pct(Math.round(v * 15) / 15) : null;
    let cue = 0;
    if (t >= cueAt) cue = t < release ? clamp((t - cueAt) / 0.1) : 1 - clamp((t - release) / 0.1);
    let rect = R0;
    if (t >= moveAt && t < release) {
      const [fx, fy] = fingerAt(t);
      rect = { ...R0, x: clamp(R0.x + fx - bx, 0, 412 - R0.w), y: clamp(R0.y + fy - by, 42, 915 - R0.h) };
    } else if (t >= release) {
      const k = 1 - Math.pow(1 - clamp((t - release) / 0.18), 2);
      rect = { ...R0, x: lerp(dropX, 0, k), y: dropY };
    }
    bar.draw({ rect, readout, cue });
    // The long-press menu: Pop, 210 ms; it retracts the moment the finger moves.
    const mOpen = t < cueAt ? 0 : t < moveAt + 0.05 ? clamp((t - cueAt) / 0.21) : clamp(1 - (t - moveAt - 0.05) / 0.21);
    menu.set({ open: mOpen });
    // Android's slider.
    let a = 0;
    for (const [from, to] of sysWindows) if (t >= from && t < to + 0.25) a = Math.max(a, Math.min(clamp((t - from) / 0.15), 1 - clamp((t - to) / 0.25)));
    sys.style.opacity = a;
    sys.style.display = a > 0 ? 'flex' : 'none';
    sys.style.transform = `translateX(${(1 - Ease.emphasizedDecel(clamp(a))) * 24}px)`;
    lvl.style.height = `${Math.max(42, v * 190)}px`;
  });
}

export const THUMB = { t: 12.4, title: 'Swipe for volume', cam: { cx: 320, cy: 300, s: 2.0, deg: -5 } };
