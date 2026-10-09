// Tutorial 6, "The long-press menu": hold the tab for the menu, run an action, hold and drag to
// move the bar, then choose the menu's actions, style and animation, and try it again.
import { clamp, lerp, Ease, icon } from '../core.js';
import { EdgeBar, PRESETS, loadDrawables } from '../quickpanel.js';
import { LongPressMenu, MENU_ICONS } from '../menu.js';
import { indicator, SYSTEM_ICONS } from '../system.js';
import { along } from '../director.js';
import { menuSteps, MENU_SETTINGS_ICONS } from './menu-settings.js';
import { preparePanelSettings } from '../panelsettings.js';

export const WALLPAPER = 'ocean';
export const ICONS = [...MENU_ICONS, ...SYSTEM_ICONS, ...MENU_SETTINGS_ICONS, 'round/touch_app', 'round/open_with'];
export async function prepare() {
  await loadDrawables(['ic_vol_increase', 'ic_move', 'ic_mute', 'ic_visibility_hide', 'ic_power', 'ic_app_open']);
  await preparePanelSettings({ shaders: false });
}

const glyph = (name) => icon(name, 18, '#4F46E5');

export async function script(T) {
  const { phone } = T;
  const dock = new EdgeBar(phone, PRESETS.dock);
  const R0 = { ...dock.rect };
  const bx = R0.x + R0.w / 2 - 1, by = R0.y + R0.h / 2;
  const menu = new LongPressMenu(phone, dock, { theme: 'solid', entrance: 'slide' });
  const toast = indicator('Flashlight on', icon('filled/flashlight_on', 20, '#fff'));
  phone.overlay.appendChild(toast);

  // --- Open it -------------------------------------------------------------------------------------------
  T.chapter('Open the menu');
  const s1 = T.step('Hold the tab', 'Keep your finger on the Dock until the ring closes. The menu opens beside it, and stays when you let go.', 7.2);
  T.cam({ at: s1.at + 0.6, cx: 250, cy: 250, s: 1.6, dur: 0.8 });
  const hold1 = s1.at + 1.6;
  T.hand({ at: s1.at + 0.9, enter: [bx, by], tilt: -40 }, { at: hold1, hold: [bx, by], dur: 1.25 });
  T.pill(hold1 + 0.1, 1.5, 'Hold', 300, by + 150, glyph('round/touch_app'));
  const cue1 = hold1 + 0.4, lift1 = hold1 + 1.25;

  const s2 = T.step('Tap an action', 'Each tile runs its action and the menu closes. Here, the flashlight comes on.', 6.6);
  const flash = menu.tileCentre(4);
  const tapAt = s2.at + 1.0;
  T.hand({ at: tapAt, tap: flash, sound: 'toggle' }, { at: tapAt + 0.7, leave: true });
  const menuClose1 = tapAt + 0.06;

  const s3 = T.step('Hold and drag to move it', 'Hold, then move instead of letting go: the menu steps aside and the bar follows your finger.', 7.8);
  T.cam({ at: s3.at + 0.4, cx: 230, cy: 400, s: 1.15, dur: 0.8 });
  const hold2 = s3.at + 1.3, still = 0.75, mdur = 1.3;
  const path = [[bx, by], [bx - 18, by + 160], [bx - 6, by + 300]];
  T.hand({ at: hold2 - 0.6, enter: [bx, by], tilt: -40 }, { at: hold2, drag: path, holdFor: still, dur: still + mdur }, { at: hold2 + 0.2 + still + mdur + 0.5, leave: true });
  const cue2 = hold2 + 0.4, move2 = hold2 + 0.11 + still, release2 = hold2 + 0.2 + still + mdur;
  const fingerAt = (t) => along(path, Ease.inOut(clamp((t - move2) / mdur)));
  const dropY = path[path.length - 1][1] - by + R0.y;
  const dropX = path[path.length - 1][0] - bx + R0.x;
  const R1 = { ...R0, y: dropY };

  // --- Settings (menu-settings.js) -------------------------------------------------------------------------
  const settings = await menuSteps(T);

  // --- Try it -------------------------------------------------------------------------------------------
  T.chapter('Try it');
  const s8 = T.step('Hold the tab again', 'Your actions, in the style and animation you picked.', 6.6);
  T.cam({ at: s8.at + 0.3, cx: 250, cy: R1.y + 60, s: 1.6, dur: 0.8 });
  const frac = (R1.y + R1.h / 2 - 32) / (915 - 32);
  const dock2 = new EdgeBar(phone, PRESETS.dock, { frac });
  const menu2 = new LongPressMenu(phone, dock2, { theme: settings.theme, entrance: settings.entrance, items: settings.items });
  const bx2 = dock2.rect.x + dock2.rect.w / 2 - 1, by2 = dock2.rect.y + dock2.rect.h / 2;
  const hold3 = s8.at + 1.3;
  T.hand({ at: s8.at + 0.6, enter: [bx2, by2], tilt: -40 }, { at: hold3, hold: [bx2, by2], dur: 1.25 }, { at: hold3 + 1.9, leave: true });
  const cue3 = hold3 + 0.4;

  // --- Sound ---------------------------------------------------------------------------------------------
  T.cue(cue1, 'open', 0.45); T.cue(cue2, 'open', 0.4); T.cue(cue3, 'open', 0.45); T.cue(release2 + 0.05, 'toggle', 0.35);

  // --- Every frame -----------------------------------------------------------------------------------------
  const exitOf = (t, at) => clamp(1 - (t - at) / 0.21);
  T.on((t) => {
    const inApp = settings.covers(t);
    // The bar: cue while held; carried while dragged; back to the edge in 180 ms when let go.
    let cue = 0, rect = R0;
    if (t >= cue1 && t < lift1) cue = clamp((t - cue1) / 0.1);
    else if (t >= lift1 && t < lift1 + 0.1) cue = 1 - clamp((t - lift1) / 0.1);
    if (t >= cue2) cue = t < release2 ? clamp((t - cue2) / 0.1) : 1 - clamp((t - release2) / 0.1);
    if (t >= move2 && t < release2) {
      const [fx, fy] = fingerAt(t);
      rect = { ...R0, x: clamp(R0.x + fx - bx, 0, 412 - R0.w), y: clamp(R0.y + fy - by, 42, 915 - R0.h) };
    } else if (t >= release2) {
      const k = 1 - Math.pow(1 - clamp((t - release2) / 0.18), 2);
      rect = { ...R1, x: lerp(clamp(dropX, 0, 412 - R0.w), R0.x, k) };
    }
    const second = t >= hold3 - 0.7;
    dock.draw({ alpha: inApp || second ? 0 : 1, rect, cue });
    let cue3k = 0;
    if (t >= cue3) cue3k = t < hold3 + 1.25 ? clamp((t - cue3) / 0.1) : 1 - clamp((t - hold3 - 1.25) / 0.1);
    dock2.draw({ alpha: second && !inApp ? 1 : 0, cue: cue3k });
    // The menus.
    let open1 = 0;
    if (t >= cue1 && t < menuClose1 + 0.21) open1 = t < menuClose1 ? clamp((t - cue1) / 0.21) : exitOf(t, menuClose1);
    if (t >= cue2 && t < move2 + 0.3) open1 = t < move2 + 0.05 ? clamp((t - cue2) / 0.21) : exitOf(t, move2 + 0.05);
    menu.set({ open: open1, pressed: t >= tapAt && t < menuClose1 + 0.21 ? 4 : -1, press: 1 });
    const dur2 = settings.entranceMs / 1000;
    menu2.set({ open: t < cue3 ? 0 : clamp((t - cue3) / dur2) });
    toast.style.display = t >= menuClose1 + 0.05 && t < menuClose1 + 0.95 ? 'flex' : 'none';
  });
}

export const THUMB = { t: 11.0, title: 'Long-press menu', cam: { cx: 260, cy: 230, s: 1.9, deg: -5 } };
