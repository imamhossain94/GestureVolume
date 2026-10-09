// The long-press menu tutorial's settings steps: the Long-press menu screen's Items (Stop service
// out, Screenshot in), a panel style (Glass) and an opening animation (Swing), then home again.
import { clamp, Ease } from '../core.js';
import { homeAppScreen, HOME_ICONS, SCREEN_ICONS } from '../screens.js';
import { longPressMenuScreen, mountLongPressMenu, entranceSeconds, PANELSETTINGS_ICONS, DEFAULT_MENU_ITEMS, MENU_CATALOG } from '../panelsettings.js';
import { live } from '../tutorial.js';

export const MENU_SETTINGS_ICONS = [...PANELSETTINGS_ICONS, ...HOME_ICONS, ...SCREEN_ICONS];
export const ITEMS_AFTER = DEFAULT_MENU_ITEMS.filter((id) => id !== 'stop-service').concat('screenshot');
// The same ids as kit/menu.js's LongPressMenu takes them: a label and an icon or a drawable.
export const menuItemsFor = (ids) => ids.map((id) => MENU_CATALOG.find((e) => e.id === id)).map((e) =>
  (e.glyph.startsWith('drawable/') ? { label: e.label, drawable: e.glyph.slice(9) } : { label: e.label, icon: e.glyph }));
const q = (x) => Math.round(x * 20) / 20;
const ease = Ease.fastOutSlowIn;
// The picture rows keep the scroll they opened with when a tile is picked (Slide, 9th, opens with Drop first).
const ROWS = { theme: 0, anim: 7 * 86 };

export async function menuSteps(T) {
  const tm = { items: 1e9, remove: 1e9, add: 1e9, colours: 1e9, glass: 1e9, anim: 1e9, spring: 1e9 };
  const scrolls = [];                                       // { at, to }: the column glides there over 0.6 s
  const scrollAt = (t) => { let v = 0; for (const s of scrolls) if (t >= s.at) v = v + (s.to - v) * ease(clamp((t - s.at) / 0.6)); return Math.round(v); };
  // Sections: Size & shape is open when the screen opens; each tap opens one and closes the last.
  const openAt = (t) => {
    const marks = [['size', -1e9], ['items', tm.items], ['colours', tm.colours], ['animation', tm.anim]];
    let cur = 'size', prev = null, at = -1e9;
    for (const [id, a] of marks) if (t >= a) { prev = cur === id ? null : cur; cur = id; at = a; }
    const k = q(clamp((t - at - 0.06) / 0.35));
    if (k >= 1 || !prev) return cur === 'size' && at < 0 ? null : cur;
    return { [prev]: q(1 - ease(k)), [cur]: q(ease(k)) };
  };
  const state = (t) => ({
    open: openAt(t),
    items: t < tm.remove + 0.06 ? DEFAULT_MENU_ITEMS : t < tm.add + 0.06 ? DEFAULT_MENU_ITEMS.filter((id) => id !== 'stop-service') : ITEMS_AFTER,
    theme: t >= tm.glass + 0.06 ? 'glass' : 'solid',
    animation: t >= tm.spring + 0.06 ? 'swing' : 'slide',
    scroll: scrollAt(t),
    rowScroll: ROWS,
  });
  const screen = live((s) => longPressMenuScreen(s), state, { open: null, items: DEFAULT_MENU_ITEMS, theme: 'solid', animation: 'slide', scroll: 0, rowScroll: ROWS });
  const mount = mountLongPressMenu(screen);
  const measure = (s, name) => T.measure(longPressMenuScreen({ open: null, items: DEFAULT_MENU_ITEMS, theme: 'solid', animation: 'slide', scroll: 0, rowScroll: ROWS, ...s }), name);

  T.chapter('Choose its actions');
  const sA = T.step('Open its settings', 'In Gesture Volume, under Advanced features, tap Long-press menu. The preview shows the menu as it is.', 6.6);
  T.cam({ at: sA.at + 0.2, cx: 206, cy: 457, s: 1.0, dur: 0.6 });
  const home = homeAppScreen({ running: true, advanced: true });
  T.hand({ at: sA.at + 0.3, enter: [T.iconRect.cx + 20, T.iconRect.cy + 70] });
  const launched = T.launch(sA.at + 0.9, home);
  const menuAt = sA.at + 3.2;
  T.tap(menuAt, home, 'feature-long-press-menu');
  T.show(menuAt + 0.06, screen, 'push');

  const sB = T.step('Choose the actions', 'Open Items: remove what you never use, and add what you want from Available. Here, Screenshot in place of Stop service.', 10.4);
  tm.items = sB.at + 0.9;
  const itemsHdr = measure({}, 'section-items');
  T.hand({ at: tm.items - 0.06, tap: [itemsHdr.cx, itemsHdr.cy] });
  // Bring Stop service's row up, take it out, then find Screenshot under Available and add it.
  const stopRow = measure({ open: 'items' }, 'item-stop-service');
  const scrollA = Math.max(0, Math.round(stopRow.cy - 560));
  scrolls.push({ at: sB.at + 1.8, to: scrollA });
  const rm = measure({ open: 'items', scroll: scrollA }, 'item-stop-service-remove');
  tm.remove = sB.at + 3.4;
  T.hand({ at: tm.remove - 0.06, tap: [rm.cx, rm.cy] });
  const shotRow = measure({ open: 'items', items: DEFAULT_MENU_ITEMS.filter((id) => id !== 'stop-service') }, 'item-screenshot');
  const scrollB = Math.max(scrollA, Math.round(shotRow.cy - 620));
  scrolls.push({ at: sB.at + 4.5, to: scrollB });
  const add = measure({ open: 'items', items: DEFAULT_MENU_ITEMS.filter((id) => id !== 'stop-service'), scroll: scrollB }, 'item-screenshot-add');
  tm.add = sB.at + 6.4;
  T.hand({ at: tm.add - 0.06, tap: [add.cx, add.cy] });
  T.ring(sB.at + 7.6, 2.2, measure({ open: 'items', items: ITEMS_AFTER }, 'preview'), { pad: 3, radius: 30 });

  const sC = T.step('Pick a panel style', 'Under Colours, choose how it is drawn. Glass blurs what is behind it; the Deck and the Quick slider use the same style.', 8.4);
  // Scroll back up so the Colours header is in reach, then open it.
  scrolls.push({ at: sC.at + 0.4, to: 0 });
  tm.colours = sC.at + 1.4;
  const colHdr = measure({ open: 'items', items: ITEMS_AFTER, scroll: 0 }, 'section-colours');
  T.hand({ at: tm.colours - 0.06, tap: [colHdr.cx, colHdr.cy] });
  const glass = measure({ open: 'colours', items: ITEMS_AFTER }, 'theme-glass');
  tm.glass = sC.at + 3.6;
  T.hand({ at: tm.glass - 0.06, tap: [glass.cx, glass.cy] });

  const sD = T.step('Choose how it opens', 'Under Animation, pick an opening animation. The preview plays it each time you pick one.', 8.0);
  tm.anim = sD.at + 0.9;
  const animHdr = measure({ open: 'colours', items: ITEMS_AFTER, theme: 'glass' }, 'section-animation');
  T.hand({ at: tm.anim - 0.06, tap: [animHdr.cx, animHdr.cy] });
  const spring = measure({ open: 'animation', items: ITEMS_AFTER, theme: 'glass' }, 'anim-swing');
  tm.spring = sD.at + 3.0;
  T.hand({ at: tm.spring - 0.06, tap: [spring.cx, spring.cy] });
  const homeAt = T.goHome(sD.at + 5.8);

  T.on((t) => {
    screen.render(t);
    mount({ t, entranceAt: t >= tm.spring + 0.06 ? tm.spring + 0.1 : null });
  });
  return { covers: (t) => t >= launched + 0.2 && t < homeAt + 0.2, theme: 'glass', entrance: 'swing', entranceMs: entranceSeconds('swing') * 1000, items: menuItemsFor(ITEMS_AFTER) };
}
