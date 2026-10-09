// Tutorial 9, "Keep it out of the way": when the bar shows, what it does while you type (moving
// above the keyboard in another app), the apps it hides in, and the lock screen.
import { clamp, lerp, Ease, h, icon } from '../core.js';
import { EdgeBar, PRESETS, loadDrawables } from '../quickpanel.js';
import { homeAppScreen, HOME_ICONS, SCREEN_ICONS } from '../screens.js';
import { visibilityScreenFull, APPEARANCE2_ICONS } from '../appearance2.js';
import { keyboard, videoFrame, SYSTEM_ICONS } from '../system.js';
import { lockScreen, TOUR_ICONS } from '../tour.js';
import { live } from '../tutorial.js';

export const WALLPAPER = 'forest';
export const ICONS = [...HOME_ICONS, ...SCREEN_ICONS, ...APPEARANCE2_ICONS, ...SYSTEM_ICONS, ...TOUR_ICONS, 'round/more_vert', 'round/thumb_up', 'round/share', 'round/arrow_back'];
export const prepare = () => loadDrawables(['ic_vol_increase']);

const q = (x) => Math.round(x * 20) / 20;
const ease = Ease.fastOutSlowIn;
// OvershootInterpolator(1.6), the Spring motion's curve.
const overshoot = (t, k = 1.6) => { const u = t - 1; return u * u * ((k + 1) * u + k) + 1; };

// Someone else's notes app, plainly drawn: a title, a text box, and a caret once it is tapped.
function notesApp({ focused = false } = {}) {
  return h(`<div class="app-screen" style="position:absolute;inset:0;background:#FFFBFE;font-family:Roboto;color:#1D1B20">
      <div style="height:96px;padding-top:32px;display:flex;align-items:center;gap:12px;padding-left:16px">${icon('round/arrow_back', 24, '#1D1B20')}
        <div style="font:400 22px/28px Roboto">New note</div></div>
      <div data-target="field" style="margin:8px 16px;min-height:260px;border-radius:16px;background:#F3EDF7;padding:16px">
        <div style="font:500 18px/24px Roboto">Shopping</div>
        <div style="margin-top:10px;font:400 16px/24px Roboto;color:#49454F">Milk, eggs, coffee${focused ? '<span style="display:inline-block;width:2px;height:20px;margin-left:1px;vertical-align:-4px;background:#6750A4"></span>' : ''}</div></div>
    </div>`);
}

// Someone else's video app: the picture on top, its title and buttons under it.
function videoApp() {
  const el = h(`<div class="app-screen" style="position:absolute;inset:0;background:#0F0F12;font-family:Roboto;color:#fff">
      <div class="pic" style="position:absolute;left:0;top:32px;width:412px;height:232px;overflow:hidden"></div>
      <div style="position:absolute;left:16px;right:16px;top:280px">
        <div style="font:500 18px/24px Roboto">Valley at dusk, in four minutes</div>
        <div style="margin-top:6px;font:400 13px/18px Roboto;color:rgba(255,255,255,.62)">Slow travel · 12K views</div>
        <div style="margin-top:16px;display:flex;gap:10px">${['round/thumb_up', 'round/share', 'round/more_vert'].map((g) => `<div style="height:36px;padding:0 14px;border-radius:18px;background:rgba(255,255,255,.12);display:grid;place-items:center">${icon(g, 20, '#fff')}</div>`).join('')}</div></div></div>`);
  el.querySelector('.pic').appendChild(videoFrame(412, 232, { progress: 0.42 }));
  return el;
}

export async function script(T) {
  const { phone } = T;
  const dock = new EdgeBar(phone, PRESETS.dock, { frac: 0.75 });
  const R0 = { ...dock.rect };
  const home = homeAppScreen({ running: true, advanced: true });
  const tm = { spring: 1e9, scroll: 1e9, video: 1e9, scrollUp: 1e9, lock: 1e9 };
  const APPS_SCROLL = 1060, LOCK_SCROLL = 560;
  const visState = (t) => ({
    motion: t >= tm.spring + 0.06 ? 'spring' : 'glide',
    tileMs: Math.round(((t * 1000) % 3600) / 100) * 100,
    scroll: Math.round(t < tm.scroll ? 0 : t < tm.scrollUp ? ease(clamp((t - tm.scroll) / 0.7)) * APPS_SCROLL : lerp(APPS_SCROLL, LOCK_SCROLL, ease(clamp((t - tm.scrollUp) / 0.6)))),
    hidden: t >= tm.video + 0.06 ? ['video'] : [],
    lock: t < tm.lock + 0.06 ? 0 : q(ease(clamp((t - tm.lock - 0.06) / 0.25))),
  });
  const makeVis = (s) => visibilityScreenFull({ ...s, motionScroll: 'auto' });
  const vis = live(makeVis, visState, { motion: 'glide', tileMs: 0, scroll: 0, hidden: [], lock: 0 });
  const measure = (s, name) => T.measure(makeVis({ motion: 'glide', tileMs: 1500, scroll: 0, hidden: [], lock: 0, ...s }), name);
  const inApp = [];                                           // [from, to]: Gesture Volume on screen (the bar steps aside)

  // --- Open it ----------------------------------------------------------------------------------------------
  T.chapter('When it shows');
  const s1 = T.step('Open Visibility', 'In Gesture Volume, under Advanced features, tap Visibility.', 6.4);
  T.hand({ at: s1.at + 0.3, enter: [T.iconRect.cx + 20, T.iconRect.cy + 70] });
  const open1 = T.launch(s1.at + 0.9, home);
  const visAt = s1.at + 3.1;
  T.tap(visAt, home, 'feature-visibility');
  T.show(visAt + 0.06, vis, 'push');

  const s2 = T.step('Choose when it shows', 'Two switches: only while media plays, or only during calls. Leave both off and it is always there.', 7.2);
  const m = measure({}, 'row-media'), c = measure({}, 'row-calls');
  T.ring(s2.at + 0.8, 4.6, { x: m.x, y: m.y, w: m.w, h: c.y + c.h - m.y }, { pad: 4, radius: 22 });
  T.hand({ at: s2.at + 0.6, leave: true });

  // --- While typing ----------------------------------------------------------------------------------------
  T.chapter('While you type');
  const s3 = T.step('Pick what happens as you type', 'Move above the keyboard, hide, or stay put. Moving, it can glide, spring or bounce out of the way.', 8.4);
  const springR = measure({}, 'motion-spring');
  tm.spring = s3.at + 2.6;
  T.hand({ at: s3.at + 1.9, enter: [springR.cx + 30, springR.cy + 80] }, { at: tm.spring, tap: [springR.cx, springR.cy] });
  T.ring(s3.at + 0.6, 1.8, measure({}, 'keyboard-move'), { pad: 3, radius: 18 });

  const s4 = T.step('Type in another app', 'In any app, as the keyboard comes up the bar springs above it, and goes back when you are done.', 9.0);
  const home1 = T.goHome(s4.at + 0.3);
  inApp.push([open1, home1]);
  const notesIcon = T.appIconRect('Notes');
  const notesAt = s4.at + 1.5;
  T.hand({ at: notesAt, tap: [notesIcon.cx, notesIcon.cy] });
  const notes = live((s) => notesApp(s), (t) => ({ focused: t >= fieldAt + 0.06 }), { focused: false });
  T.show(notesAt + 0.16, notes, 'launch', { from: notesIcon });
  const fieldR = T.measure(notesApp(), 'field');
  const fieldAt = s4.at + 3.2;
  T.hand({ at: fieldAt, tap: [fieldR.cx, fieldR.y + 60] }, { at: fieldAt + 0.6, leave: true });
  const kb = keyboard({ layout: 'letters', dark: false, enter: 'return' });
  phone.overlay.appendChild(kb);
  const kbUp = fieldAt + 0.1, kbDown = s4.end - 0.6;
  const KB_H = 310, lifted = 915 - KB_H - 12 - R0.h;         // the bar's bottom 12 dp over the keyboard

  // --- Apps it hides in ---------------------------------------------------------------------------------------
  T.chapter('Apps and the lock screen');
  const s5 = T.step('Pick apps to hide it in', 'Back in Visibility, tick the apps where it should step aside. Here, Video.', 8.4);
  const home2 = T.goHome(s5.at + 0.3, notesIcon);
  T.hand({ at: s5.at + 0.9, enter: [T.iconRect.cx + 20, T.iconRect.cy + 70] });
  const open2 = T.launch(s5.at + 1.4, vis);
  tm.scroll = s5.at + 2.6;
  const videoRow = measure({ scroll: APPS_SCROLL, motion: 'spring' }, 'app-video');
  tm.video = s5.at + 4.6;
  T.hand({ at: tm.video, tap: [videoRow.cx, videoRow.cy] });

  const s6 = T.step('There, it steps aside', 'Open Video and the bar is gone. Leave it, and the bar is back.', 7.2);
  const home3 = T.goHome(s6.at + 0.3);
  inApp.push([open2, home3]);
  const videoIcon = T.appIconRect('Video');
  const videoAt = s6.at + 1.5;
  T.hand({ at: videoAt, tap: [videoIcon.cx, videoIcon.cy] }, { at: videoAt + 0.6, leave: true });
  const video = videoApp();
  T.show(videoAt + 0.16, video, 'launch', { from: videoIcon, dark: false });
  const home4 = T.goHome(s6.at + 4.6, videoIcon);
  const inVideo = [videoAt + 0.16, home4];

  const s7 = T.step('Show it on the lock screen', 'Turn on Show on the lock screen and the bar is there too, for volume and quick toggles.', 9.4);
  T.hand({ at: s7.at + 0.3, enter: [T.iconRect.cx + 20, T.iconRect.cy + 70] });
  const open3 = T.launch(s7.at + 0.8, vis);
  tm.scrollUp = s7.at + 1.8;
  const lockSw = measure({ scroll: LOCK_SCROLL, motion: 'spring', hidden: ['video'] }, 'switch-lock');
  tm.lock = s7.at + 3.3;
  T.hand({ at: tm.lock, tap: [lockSw.cx, lockSw.cy] }, { at: tm.lock + 0.6, leave: true });
  // The power key: the screen goes dark, and wakes on the lock screen with the bar on it.
  const powerAt = s7.at + 5.0;
  T.hand({ at: powerAt - 0.6, enter: [423, 212], tilt: -78 }, { at: powerAt, tap: [423, 212], sound: 'press' }, { at: powerAt + 0.6, leave: true });
  T.cam({ at: powerAt - 0.4, cx: 230, cy: 457, s: 1.0, dur: 0.4 });
  const lockEl = lockScreen({});
  const off = h(`<div style="position:absolute;inset:0;background:#000;z-index:200;opacity:0;pointer-events:none"></div>`);
  phone.el.appendChild(off);
  const wake = powerAt + 0.9;
  T.show(wake, lockEl, 'cut', { dark: false });
  inApp.push([open3, wake]);
  T.ring(wake + 0.8, 2.6, R0, { pad: 6, radius: 14 });

  // --- Every frame --------------------------------------------------------------------------------------------
  T.on((t) => {
    vis.render(t);
    notes.render(t);
    // The keyboard and the bar riding over it (Spring: 460 ms, overshoot 1.6).
    const up = t < kbUp ? 0 : t < kbDown ? ease(clamp((t - kbUp) / 0.25)) : 1 - ease(clamp((t - kbDown) / 0.22));
    kb.style.display = up > 0 ? 'block' : 'none';
    kb.style.transform = `translateY(${(1 - up) * KB_H}px)`;
    let y = R0.y;
    if (t >= kbUp + 0.12 && t < kbDown) y = lerp(R0.y, lifted, overshoot(clamp((t - kbUp - 0.12) / 0.46)));
    else if (t >= kbDown) y = lerp(lifted, R0.y, overshoot(clamp((t - kbDown) / 0.46)));
    // Hidden while Gesture Volume or Video is on screen, and while the screen is off.
    const hidden = inApp.some(([a, b]) => t >= a + 0.2 && t < b) || (t >= inVideo[0] + 0.2 && t < inVideo[1]) || (t >= powerAt + 0.05 && t < wake);
    dock.draw({ alpha: hidden ? 0 : 1, rect: { ...R0, y } });
    off.style.opacity = t < powerAt + 0.05 ? 0 : t < wake ? 1 : 1 - clamp((t - wake) / 0.25);
    // On the lock screen only the wallpaper is behind it, and the status bar drops its clock.
    const locked = t >= wake;
    T.launcher.style.visibility = locked ? 'hidden' : '';
    phone.status.firstElementChild.style.visibility = locked ? 'hidden' : '';
  });
}

export const THUMB = { t: 58.6, title: 'Out of the way', cam: { cx: 236, cy: 470, s: 1.3, deg: -5 } };
