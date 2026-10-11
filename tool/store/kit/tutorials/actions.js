// Tutorial 7, "Choose what each gesture does": the Actions screen and its try pad, giving Double
// tap a job, and gestures of its own for one app.
import { clamp, lerp, Ease } from '../core.js';
import { homeAppScreen, HOME_ICONS, SCREEN_ICONS } from '../screens.js';
import { actionsScreen, actionPicker, appGesturesScreen, chooseAppScreen, appGestureEditor, padGeometry, ACTIONS_ICONS } from '../actions.js';
import { live } from '../tutorial.js';
import { prog } from '../director.js';

export const WALLPAPER = 'dusk';
export const ICONS = [...HOME_ICONS, ...SCREEN_ICONS, ...ACTIONS_ICONS];

const ease = Ease.fastOutSlowIn;
// A scroll that glides from a to b over d seconds starting at t0.
const glide = (t, t0, a, b, d = 0.6) => lerp(a, b, ease(clamp((t - t0) / d)));

export async function script(T) {
  const home = homeAppScreen({ running: true, advanced: true });
  // When things happen, filled in as the steps are laid out (the screens' states read them).
  let chipAt = 1e9, scrollAt = 1e9, appsScroll = 0, pickedAt = 1e9, brightAt = 1e9;
  const P = padGeometry({ status: 32, mode: 'advanced' });

  // --- Open Actions ---------------------------------------------------------------------------------------
  T.chapter('The Actions screen');
  const s1 = T.step('Open Actions', 'In Gesture Volume, tap Actions. Every tap, hold and swipe the bar understands is listed here.', 6.6);
  T.hand({ at: s1.at + 0.3, enter: [T.iconRect.cx + 20, T.iconRect.cy + 70] });
  T.launch(s1.at + 0.9, home);
  const actionsAt = s1.at + 3.0;
  T.tap(actionsAt, home, 'actions');

  // The Actions screen, whose state changes all through the video.
  const swipe = { at: 0, x: P.barPoint.x, y0: P.barPoint.y + 30, y1: P.barPoint.y - 38, dur: 0.5 };
  const s2 = T.step('Try a gesture on the pad', 'The pad at the top holds your real bar. Try a gesture on it, and the line below says what that gesture does.', 8.4);
  swipe.at = s2.at + 1.3;
  const swipeEnd = swipe.at + 0.11 + swipe.dur;
  const tapPadAt = s2.at + 4.6;
  T.hand({ at: swipe.at, swipe: [swipe.x, swipe.y0], to: [swipe.x, swipe.y1], dur: swipe.dur, lead: 0.5 }, { at: tapPadAt, tap: [P.barPoint.x, P.barPoint.y] }, { at: tapPadAt + 0.8, leave: true });
  const fingerY = (t) => lerp(swipe.y0, swipe.y1, ease(clamp((t - swipe.at - 0.11) / swipe.dur)));

  // --- Change an action -----------------------------------------------------------------------------------------
  T.chapter('Change an action');
  const s3 = T.step('Pick a gesture to change', 'Tap a gesture to change it. Double tap does nothing yet, so give it a job.', 5.8);
  const dblAt = s3.at + 1.1;
  const pick1Kind = (st) => actionPicker({ slot: 'doubleTap', scroll: st.scroll });
  const media = actionPicker({ slot: 'doubleTap' }).layout.groupScroll.media;
  const picker1 = live(pick1Kind, (t) => ({ scroll: Math.round(glide(t, chipAt + 0.06, 0, media, 0.55)) }));
  const actionsState = (t) => ({
    scroll: Math.round(t < scrollAt ? 0 : glide(t, scrollAt, 0, appsScroll, 0.7)),
    tried: t < swipeEnd ? null : t < tapPadAt + 0.18 ? { gesture: 'swipeUp', k: Math.round(prog(t, swipeEnd, 0.25) * 10) / 10 } : { gesture: 'tap', k: Math.round(prog(t, tapPadAt + 0.18, 0.25) * 10) / 10 },
    touch: t >= swipe.at && t < swipeEnd + 0.2 ? { x: Math.round(swipe.x - P.pad.x), y: Math.round(fingerY(t) - P.pad.y), alpha: Math.round(clamp(1 - (t - swipeEnd) / 0.2) * 10) / 10 }
      : t >= tapPadAt && t < tapPadAt + 0.35 ? { x: Math.round(P.barPoint.x - P.pad.x), y: Math.round(P.barPoint.y - P.pad.y), alpha: Math.round(clamp(1 - (t - tapPadAt - 0.15) / 0.2) * 10) / 10 } : null,
    slots: t >= pickedAt ? { doubleTap: 'play-pause' } : {},
  });
  const actionsEl = live((st) => actionsScreen(st), actionsState, { scroll: 0, tried: null, touch: null, slots: {} });
  T.show(actionsAt + 0.06, actionsEl, 'push');
  T.tap(dblAt, actionsEl, 'slot-double-tap');
  T.show(dblAt + 0.06, picker1, 'push');

  const s4 = T.step('Choose an action', 'Actions come in groups: the bar, volume, the Deck, your device, media and system. Tap one and you are done.', 8.6);
  chipAt = s4.at + 1.0;
  T.tap(chipAt, picker1, 'chip-media');
  const playR = T.measure(pick1Kind({ scroll: media }), 'action-play-pause');
  pickedAt = s4.at + 3.4;
  T.hand({ at: pickedAt, tap: [playR.cx, playR.cy] });
  T.show(pickedAt + 0.25, actionsEl, 'pop');
  const dblR = T.measure(actionsScreen({ slots: { doubleTap: 'play-pause' } }), 'slot-double-tap');
  T.ring(pickedAt + 0.8, 2.6, dblR, { pad: 3, radius: 22 });
  T.hand({ at: pickedAt + 0.9, leave: true });

  // --- Gestures for chosen apps ---------------------------------------------------------------------------
  T.chapter('Gestures for one app');
  const s5 = T.step('Give an app its own gestures', 'Under More, open Gestures for chosen apps, tap Add and pick the app.', 9.2);
  scrollAt = s5.at + 0.5;
  appsScroll = 1121 + 361 - 620;                              // brings the row to y 620
  const linkR = T.measure(actionsScreen({ scroll: appsScroll, slots: { doubleTap: 'play-pause' } }), 'link-app-gestures');
  const listAt = s5.at + 1.9;
  T.hand({ at: s5.at + 1.3, enter: [linkR.cx + 40, linkR.cy + 90] }, { at: listAt, tap: [linkR.cx, linkR.cy] });
  const list = appGesturesScreen({ apps: [] });
  T.show(listAt + 0.06, list, 'push');
  const addAt = s5.at + 3.8;
  T.tap(addAt, list, 'add');
  const chooser = chooseAppScreen({});
  T.show(addAt + 0.06, chooser, 'push');
  const videoAt = s5.at + 6.0;
  T.tap(videoAt, chooser, 'app-video');

  const s6 = T.step('Change a gesture just there', 'Set Swipe up to Increase brightness. In Video, swiping up now brightens the screen; everywhere else it works as before.', 9.0);
  const editState = (t) => ({ slots: t >= brightAt ? { swipeUp: 'increase-brightness' } : {} });
  const editor = live((st) => appGestureEditor({ app: 'video', slots: st.slots }), editState, { slots: {} });
  T.show(videoAt + 0.06, editor, 'push');
  const upAt = s6.at + 0.9;
  T.tap(upAt, editor, 'slot-swipe-up');
  const picker2 = actionPicker({ slot: 'swipeUp', app: 'video' });
  T.show(upAt + 0.06, picker2, 'push');
  brightAt = s6.at + 2.9;
  const brightR = T.rect(picker2, 'action-increase-brightness');
  T.ring(brightAt - 1.0, 1.1, brightR, { pad: 2, radius: 16 });
  T.hand({ at: brightAt, tap: [brightR.cx, brightR.cy] });
  T.show(brightAt + 0.25, editor, 'pop');
  const upR = T.measure(appGestureEditor({ app: 'video', slots: { swipeUp: 'increase-brightness' } }), 'slot-swipe-up');
  T.ring(brightAt + 0.8, 2.8, upR, { pad: 3, radius: 22 });
  T.hand({ at: brightAt + 0.9, leave: true });

  T.cue(swipe.at + 0.12, 'swipe', 0.4);
  T.on((t) => { actionsEl.render(t); picker1.render(t); editor.render(t); });
}

export const THUMB = { t: 33.5, title: 'Your gestures', cam: { cx: 206, cy: 330, s: 1.5, deg: -5 } };
