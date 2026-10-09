// Tutorial 8, "Make the bar yours": Appearance's presets, size and shape, a colour, the side, Save,
// and the result on the home screen.
import { clamp, lerp, Ease } from '../core.js';
import { EdgeBar, PRESETS, loadDrawables } from '../quickpanel.js';
import { homeAppScreen, HOME_ICONS, SCREEN_ICONS } from '../screens.js';
import { appearanceScreenFull, colorPickerDialog, APPEARANCE2_ICONS } from '../appearance2.js';
import { live } from '../tutorial.js';

export const WALLPAPER = 'sunset';
export const ICONS = [...HOME_ICONS, ...SCREEN_ICONS, ...APPEARANCE2_ICONS];
export const prepare = () => loadDrawables(['ic_vol_increase', 'ic_move']);

const CYAN = '#FF00FFFF';
const q = (x) => Math.round(x * 20) / 20;
const ease = Ease.fastOutSlowIn;

export async function script(T) {
  const { phone } = T;
  const home = homeAppScreen({ running: true, advanced: true });
  // Times, filled in as the steps are laid out; the screen's state reads them.
  const tm = { preset: [], sizeOpen: 1e9, sizeClose: 1e9, wDrag: 1e9, hDrag: 1e9, colOpen: 1e9, colClose: 1e9, picker: 1e9, swatch: 1e9, ok: 1e9,
    posScroll: 1e9, posOpen: 1e9, left: 1e9, save: 1e9 };
  const W0 = PRESETS.dock.w, H0 = PRESETS.dock.h, W1 = 30, H1 = 180, SCROLL = 330;
  const presetAt = (t) => { let p = 'dock'; for (const [at, id] of tm.preset) if (t >= at) p = id; return p; };
  const state = (t) => {
    const w = t < tm.wDrag + 0.11 ? W0 : Math.round(lerp(W0, W1, ease(clamp((t - tm.wDrag - 0.11) / 0.9))));
    const h = t < tm.hDrag + 0.11 ? H0 : Math.round(lerp(H0, H1, ease(clamp((t - tm.hDrag - 0.11) / 0.9))));
    const look = { w, h };
    if (t >= tm.ok) look.color = CYAN;
    if (t >= tm.left) look.side = 'left';
    const open = {};
    const sec = (a, b) => (t < a ? 0 : t < b ? q(ease(clamp((t - a) / 0.4))) : q(1 - ease(clamp((t - b) / 0.35))));
    open.size = sec(tm.sizeOpen, tm.sizeClose);
    open.colours = sec(tm.colOpen, tm.colClose);
    open.position = sec(tm.posOpen, 1e9);
    for (const k of Object.keys(open)) if (!open[k]) delete open[k];
    const preset = presetAt(t);
    return {
      preset, look: preset === 'dock' ? look : {}, open: Object.keys(open).length ? open : null,
      scroll: Math.round(t < tm.posScroll ? 0 : ease(clamp((t - tm.posScroll) / 0.6)) * SCROLL),
      dirty: t >= (tm.preset[0]?.[0] ?? 1e9), saved: t < tm.save + 0.1 ? 0 : q(Math.min(clamp((t - tm.save - 0.1) / 0.2), 1 - clamp((t - tm.save - 2.2) / 0.3))),
      dragging: (t >= tm.wDrag && t < tm.wDrag + 1.1) ? 'width' : (t >= tm.hDrag && t < tm.hDrag + 1.1) ? 'height' : null,
      picker: t < tm.picker + 0.06 ? 0 : t < tm.ok + 0.06 ? q(clamp((t - tm.picker - 0.06) / 0.25)) : q(1 - clamp((t - tm.ok - 0.06) / 0.18)),
      swatch: t >= tm.swatch + 0.05 ? 'cyan' : null,
    };
  };
  const make = (s) => {
    const el = appearanceScreenFull({ preset: s.preset, look: s.look, open: s.open, scroll: s.scroll, dirty: s.dirty, saved: s.saved, dragging: s.dragging });
    if (s.picker > 0) el.appendChild(colorPickerDialog({ title: 'Select the color and transparency of the handler', hex: s.swatch, k: s.picker }));
    return el;
  };
  const app = live(make, state, { preset: 'dock', look: {}, open: null, scroll: 0, dirty: false, saved: 0, dragging: null, picker: 0, swatch: null });
  const measure = (s, name) => T.measure(make({ preset: 'dock', look: {}, open: null, scroll: 0, dirty: true, saved: 0, dragging: null, picker: 0, swatch: null, ...s }), name);

  // --- Open it --------------------------------------------------------------------------------------------
  T.chapter('Presets');
  const s1 = T.step('Open Appearance', 'In Gesture Volume, tap Appearance. The preview at the top shows the bar on a phone.', 6.4);
  T.hand({ at: s1.at + 0.3, enter: [T.iconRect.cx + 20, T.iconRect.cy + 70] });
  T.launch(s1.at + 0.9, home);
  const openAt = s1.at + 3.0;
  T.tap(openAt, home, 'appearance');
  T.show(openAt + 0.06, app, 'push');

  const s2 = T.step('Start from a preset', 'Quick presets set the whole look at once: Classic, Dock, Edge and Bold.', 8.8);
  for (const [i, id] of ['classic', 'edge', 'bold', 'dock'].entries()) {
    const at = s2.at + 1.0 + i * 1.9;
    const r = measure({ preset: i ? ['classic', 'edge', 'bold'][i - 1] : 'dock' }, `preset-${id}`);
    T.hand({ at, tap: [r.cx, r.cy] });
    tm.preset.push([at + 0.06, id]);
  }
  T.ring(s2.at + 0.4, 1.4, measure({}, 'preview'), { pad: 3, radius: 30 });

  // --- Size, shape and colour -----------------------------------------------------------------------------------
  T.chapter('Size, shape and colour');
  const s3 = T.step('Size and shape', 'Open Size & shape and drag Width and Height. Rounded or Tab sets how the ends look.', 9.0);
  tm.sizeOpen = s3.at + 0.86;
  const sizeHdr = measure({}, 'section-size');
  T.hand({ at: s3.at + 0.8, tap: [sizeHdr.cx, sizeHdr.cy] });
  const wT0 = measure({ open: 'size' }, 'slider-width-thumb'), wT1 = measure({ open: 'size', look: { w: W1 } }, 'slider-width-thumb');
  const hT0 = measure({ open: 'size', look: { w: W1 } }, 'slider-height-thumb'), hT1 = measure({ open: 'size', look: { w: W1, h: H1 } }, 'slider-height-thumb');
  tm.wDrag = s3.at + 2.6; tm.hDrag = s3.at + 5.0;
  T.hand({ at: tm.wDrag, drag: [[wT0.cx, wT0.cy], [wT1.cx, wT1.cy]], dur: 0.9, ease }, { at: tm.hDrag, drag: [[hT0.cx, hT0.cy], [hT1.cx, hT1.cy]], dur: 0.9, ease });
  T.ring(s3.at + 7.2, 1.6, measure({ open: 'size' }, 'preview'), { pad: 3, radius: 30 });

  const s4 = T.step('Pick a colour', 'Under Colours, tap Fill and choose a colour. The preview takes it straight away.', 9.4);
  tm.sizeClose = s4.at + 0.66;
  T.hand({ at: s4.at + 0.6, tap: [sizeHdr.cx, sizeHdr.cy] });
  const colHdr = measure({ look: { w: W1, h: H1 } }, 'section-colours');
  tm.colOpen = s4.at + 1.86;
  T.hand({ at: s4.at + 1.8, tap: [colHdr.cx, colHdr.cy] });
  const fill = measure({ look: { w: W1, h: H1 }, open: 'colours' }, 'color-fill');
  tm.picker = s4.at + 3.4;
  T.hand({ at: tm.picker, tap: [fill.cx, fill.cy] });
  const sw = measure({ look: { w: W1, h: H1 }, open: 'colours', picker: 1 }, 'swatch-cyan');
  tm.swatch = s4.at + 5.2;
  T.hand({ at: tm.swatch, tap: [sw.cx, sw.cy] });
  const ok = measure({ look: { w: W1, h: H1 }, open: 'colours', picker: 1, swatch: 'cyan' }, 'color-ok');
  tm.ok = s4.at + 6.6;
  T.hand({ at: tm.ok, tap: [ok.cx, ok.cy] });

  // --- Where it sits ----------------------------------------------------------------------------------------
  T.chapter('Where it sits');
  const s5 = T.step('Choose a side', 'Under Position, put it on the left or the right, and set how far down the edge it sits.', 8.0);
  tm.colClose = s5.at + 0.56;
  const colHdrOpen = measure({ look: { w: W1, h: H1, color: CYAN }, open: 'colours' }, 'section-colours');
  T.hand({ at: s5.at + 0.5, tap: [colHdrOpen.cx, colHdrOpen.cy] });
  tm.posScroll = s5.at + 1.4;
  const posHdr = measure({ look: { w: W1, h: H1, color: CYAN }, scroll: SCROLL }, 'section-position');
  tm.posOpen = s5.at + 2.66;
  T.hand({ at: s5.at + 2.6, tap: [posHdr.cx, posHdr.cy] });
  const left = measure({ look: { w: W1, h: H1, color: CYAN }, scroll: SCROLL, open: 'position' }, 'side-left');
  tm.left = s5.at + 4.4;
  T.hand({ at: tm.left - 0.06, tap: [left.cx, left.cy] });
  T.ring(s5.at + 5.6, 1.8, measure({ look: { w: W1, h: H1, color: CYAN, side: 'left' }, scroll: SCROLL, open: 'position' }, 'preview'), { pad: 3, radius: 30 });

  const s6 = T.step('Save it', 'Tap Save. Go home, and your bar is on the edge just as you made it.', 8.2);
  const save = measure({}, 'save');
  tm.save = s6.at + 0.9;
  T.hand({ at: tm.save - 0.06, tap: [save.cx, save.cy] });
  const homeAt = T.goHome(s6.at + 3.4);
  T.hand({ at: s6.at + 4.1, leave: true });
  // The bar as saved: the Dock at 30 x 180, cyan, on the left.
  const mine = new EdgeBar(phone, { ...PRESETS.dock, w: W1, h: H1, color: CYAN }, { side: 'left' });
  T.cam({ at: s6.at + 4.3, cx: 120, cy: 300, s: 1.6, dur: 0.8 });
  T.ring(s6.at + 5.2, 2.4, mine.rect, { pad: 7, radius: 16 });

  T.on((t) => { app.render(t); mine.draw({ alpha: t >= homeAt ? 1 : 0 }); });
}

export const THUMB = { t: 35.0, title: 'Make it yours', cam: { cx: 206, cy: 430, s: 1.45, deg: -5 } };
