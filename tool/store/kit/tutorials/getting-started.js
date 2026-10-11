// Tutorial 1, "Getting started": the welcome tour (style, gestures, the overlay permission and
// notifications), then the service switched on and the round button on the home screen.
import { clamp, lerp, Ease } from '../core.js';
import { EdgeBar, PRESETS, loadDrawables } from '../quickpanel.js';
import { homeAppScreen, HOME_ICONS, SCREEN_ICONS } from '../screens.js';
import { walkthroughPage, mountWalkthrough, systemOverlaySettings, notificationPermissionDialog, TOUR_ICONS } from '../tour.js';
import { live } from '../tutorial.js';
import { prog } from '../director.js';

export const WALLPAPER = 'ocean';
export const ICONS = [...HOME_ICONS, ...SCREEN_ICONS, ...TOUR_ICONS];
export const prepare = () => loadDrawables(['ic_vol_increase']);

const q = (x) => Math.round(x * 20) / 20;           // states step in 5 % so a live screen rebuilds a few times, not every frame

export async function script(T) {
  const { phone } = T;
  const classic = new EdgeBar(phone, PRESETS.classic);

  // The walkthrough's timeline: page changes { at, to }, the Style page's highlight changes, and
  // the two permissions. Times are filled in as the steps are laid out.
  const moves = [];                                  // { at, from, to }
  const picks = [];                                  // { at, to }
  let grantOverlay = 1e9, grantNotif = 1e9, dialogAt = 1e9, dialogOk = 1e9;
  const pageAt = (t) => {
    let page = 'intro', from = null, k = 1, at = 0;
    for (const m of moves) if (t >= m.at) { from = m.from; page = m.to; at = m.at; k = clamp((t - m.at) / 0.5); }
    return { page, from: k < 1 ? from : null, k, at };
  };
  const highlightAt = (t) => {
    let hl = 'regular', pick = 1;
    for (const p of picks) if (t >= p.at) { hl = p.to; pick = clamp((t - p.at) / 0.3); }
    return { hl, pick };
  };
  const walkState = (t) => {
    const p = pageAt(t), h = highlightAt(t);
    const granted = p.page === 'permission' ? q(clamp((t - grantOverlay) / 0.6)) : p.page === 'notifications' ? q(clamp((t - grantNotif) / 0.6)) : false;
    const dialog = t < dialogAt ? 0 : t < dialogOk ? q(clamp((t - dialogAt) / 0.25)) : q(1 - clamp((t - dialogOk) / 0.15));
    return { page: p.page, from: p.from, k: q(p.k), mode: 'regular', highlighted: h.hl, pick: q(h.pick), granted, fromGranted: 1, dialog };
  };
  const makeWalk = (s) => {
    const el = walkthroughPage(s);
    if (s.dialog > 0) el.appendChild(notificationPermissionDialog({ k: s.dialog }));
    return el;
  };
  const walk = live(makeWalk, walkState, { page: 'intro', mode: 'regular', highlighted: 'regular', granted: false, k: 1, pick: 1, dialog: 0 });
  const paint = mountWalkthrough(walk);
  const target = (state, name) => T.measure(makeWalk({ mode: 'regular', highlighted: 'regular', granted: false, k: 1, pick: 1, dialog: 0, ...state }), name);
  const press = (at, state, name) => { const r = target(state, name); T.hand({ at, tap: [r.cx, r.cy] }); return r; };

  // --- The tour ----------------------------------------------------------------------------------------------
  T.chapter('The welcome tour');
  const s1 = T.step('Open Gesture Volume', 'The first time you open it, a short tour shows how the bar works, one page at a time.', 6.2);
  T.hand({ at: s1.at + 0.4, enter: [T.iconRect.cx + 20, T.iconRect.cy + 70] });
  const opened = T.launch(s1.at + 1.0, walk);

  const s2 = T.step('Pick a style', 'Simple puts a round button on the edge for volume. Advanced adds the Dock, the Quick slider, the Deck and a long-press menu. You can switch any time.', 10.2);
  press(s2.at + 0.6, { page: 'intro' }, 'next'); moves.push({ at: s2.at + 0.66, from: 'intro', to: 'style' });
  press(s2.at + 3.0, { page: 'style' }, 'style-advanced'); picks.push({ at: s2.at + 3.06, to: 'advanced' });
  press(s2.at + 5.6, { page: 'style', highlighted: 'advanced' }, 'style-regular'); picks.push({ at: s2.at + 5.66, to: 'regular' });
  press(s2.at + 8.4, { page: 'style' }, 'next'); moves.push({ at: s2.at + 8.46, from: 'style', to: 'simple' });

  const s3 = T.step('Learn the moves', 'The next pages play each gesture out: swipe for volume, tap for the volume panel, hold to move it.', 8.6);
  press(s3.at + 3.6, { page: 'simple' }, 'next'); moves.push({ at: s3.at + 3.66, from: 'simple', to: 'move' });
  press(s3.at + 7.4, { page: 'move' }, 'next'); moves.push({ at: s3.at + 7.46, from: 'move', to: 'permission' });

  // --- Permissions -----------------------------------------------------------------------------------------
  T.chapter('Permissions');
  const s4 = T.step('Allow the bar on screen', 'Tap Allow, switch on Allow display over other apps, and come back. That one switch lets the bar sit on top of your apps.', 10.6);
  press(s4.at + 1.2, { page: 'permission' }, 'grant');
  const toggleAt = s4.at + 3.4, backAt = s4.at + 5.2;
  const sys = live((s) => systemOverlaySettings({ k: s.k }), (t) => ({ k: q(Ease.fastOutSlowIn(clamp((t - toggleAt - 0.06) / 0.25))) }), { k: 0 });
  T.show(s4.at + 1.3, sys, 'push');
  T.tap(toggleAt, sys, 'toggle');
  T.tap(backAt, sys, 'back');
  T.hand({ at: backAt + 0.5, leave: true });
  T.show(backAt + 0.06, walk, 'pop');
  grantOverlay = backAt + 0.45;
  const cont = target({ page: 'permission', granted: 1 }, 'next');
  T.hand({ at: s4.at + 7.9, enter: [cont.cx + 40, cont.cy + 40] });
  press(s4.at + 8.6, { page: 'permission', granted: 1 }, 'next'); moves.push({ at: s4.at + 8.66, from: 'permission', to: 'notifications' });

  const s5 = T.step('Notifications, if you like', 'Optional: allow notifications and the bar’s controls wait in your notification shade, even with the app closed.', 8.8);
  press(s5.at + 1.0, { page: 'notifications' }, 'grant');
  dialogAt = s5.at + 1.1;
  const okR = T.measure(notificationPermissionDialog({ k: 1 }), 'allow');
  dialogOk = s5.at + 3.0;
  T.hand({ at: dialogOk, tap: [okR.cx, okR.cy] });
  grantNotif = dialogOk + 0.3;
  const startAt = s5.at + 6.2;
  press(startAt, { page: 'notifications', granted: 1 }, 'next');

  // --- Switch it on --------------------------------------------------------------------------------------------
  T.chapter('Switch it on');
  const s6 = T.step('Turn on the service', 'Tap Service inactive to switch the bar on. Go home, and the round button is waiting on the edge.', 9.0);
  const onAt = s6.at + 1.0;
  const main = live((s) => homeAppScreen({ running: s.k >= 0.5, k: s.k }), (t) => ({ k: q(Ease.fastOutSlowIn(clamp((t - onAt - 0.06) / 0.25))) }), { k: 0 });
  T.show(startAt + 0.06, main, 'push');
  T.tap(onAt, main, 'service');
  const home = T.goHome(s6.at + 3.4);
  T.hand({ at: s6.at + 4.1, leave: true });
  T.cam({ at: s6.at + 4.4, cx: 300, cy: 300, s: 1.7, dur: 0.8 });
  T.ring(s6.at + 5.3, 2.6, classic.rect, { pad: 7, radius: 22 });

  // --- Every frame -----------------------------------------------------------------------------------------
  T.on((t) => {
    walk.render(t);
    const p = pageAt(t);
    const done = moves.filter((m) => m.at <= t);
    paint(t - p.at, t - (done.length >= 2 ? done[done.length - 2].at : opened));
    sys.render(t);
    main.render(t);
    classic.draw({ alpha: t >= home ? clamp((t - home) / 0.2) : 0 });
  });
}

export const THUMB = { t: 13.0, title: 'Get started', cam: { cx: 206, cy: 380, s: 1.45, deg: -5 } };
