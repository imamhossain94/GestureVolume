// The walkthrough (ui/screens/walkthrough/WalkthroughScreen.kt), the accessibility disclosure
// (ui/components/AccessibilityDisclosureDialog.kt), the Permissions screen
// (ui/screens/permission/PermissionsScreen.kt + PermissionCard.kt), and the pages of Android's own
// that the app sends the user to: Display over other apps, the accessibility service page with its
// "full control" dialog, the notification permission request, and a lock screen.
//
// Every screen is a function of a state object and returns an element sized to the 412 x 915 dp
// screen (1 dp = 1 px). Every element a finger may tap carries data-target="<name>"; the names are
// listed above each function. The walkthrough's illustrations are canvases: build the page, put it
// in the document, then mountWalkthrough(el)(t) paints them for scene time t (seconds).
import { h, icon, clamp, lerp, spring, Ease } from './core.js';
import * as M from './m3.js';
import { gvIcon, SCREEN } from './phone.js';
import { drawWalkScene, drawStylePreview, SCENES, Smooth, PERMISSION_SETTLED, NOTIFICATIONS_SETTLED, ACCESSIBILITY_SETTLED } from './walkthrough-scenes.js';

const C = M.LIGHT;
const W = SCREEN.w, H = SCREEN.h, NAV = SCREEN.nav;

export const TOUR_ICONS = [
  'filled/arrow_back', 'filled/check_circle', 'filled/shield', 'filled/settings', 'filled/brightness_high', 'filled/accessibility',
  'filled/do_not_disturb_on', 'filled/notifications', 'filled/phone', 'filled/warning', 'filled/close',
  'outlined/info', 'outlined/visibility', 'outlined/touch_app', 'filled/lock', 'filled/fingerprint', 'filled/flashlight_on', 'filled/photo_camera',
];

// --- Type -------------------------------------------------------------------------------------------
// M3 roles at 1 dp = 1 px (spec §0.3). Text with no role inherits bodyLarge with its first and last
// leading trimmed: a one-line box is Roboto's own height, 1.172 x the size.
const T = {
  headlineSmall: 'font:500 24px/32px Roboto;letter-spacing:0',
  titleLarge: 'font:400 22px/28px Roboto;letter-spacing:0',
  titleMedium: 'font:600 16px/24px Roboto;letter-spacing:.15px',
  bodyMedium: 'font:400 14px/20px Roboto;letter-spacing:.25px',
  bodySmall: 'font:400 12px/16px Roboto;letter-spacing:.4px',
  labelLarge: 'font:500 14px/20px Roboto;letter-spacing:.1px',
  labelMedium: 'font:500 12px/16px Roboto;letter-spacing:.5px',
  chip: 'font:600 11px/16px Roboto;letter-spacing:1.2px',
  action: 'font:600 16px/20px Roboto;letter-spacing:.1px',
};
const WASH = '#E2E7FF';              // ScreenWash, light
const GRANTED = '#10B981';           // the walkthrough's granted chip
const EDGE_DOT = '#EF4444';          // "Active edge"

// --- The walkthrough's pages (strings_walkthrough.xml) -----------------------------------------------
const PAGES = {
  intro: {
    chip: 'GESTURE VOLUME', title: 'Volume at the edge of your screen',
    subtitle: 'A small bar sits on the side of your screen. Swipe it to change the volume, tap it for the volume panel.',
    scene: 'Intro', caption: 'Swipe up · Swipe down',
    points: ['Swipe up – louder', 'Swipe down – quieter', 'Tap – volume panel', 'Sits over any app'],
  },
  style: {
    chip: 'CHOOSE YOUR STYLE', title: 'How do you want it?', subtitle: 'You can change this any time from the home screen.',
    tourChip: 'TWO STYLES', tourTitle: 'Two ways to use it',
    tourSubtitle: {
      regular: 'You’re on Simple. Tap Advanced to see what it adds. Switch for real on the home screen.',
      advanced: 'You’re on Advanced. Tap Simple to see the other way. Switch for real on the home screen.',
    },
    points: {
      regular: ['Swipe – volume', 'Tap – volume panel', 'Hold – move it', 'Change it any time'],
      advanced: ['Quick slider', 'Deck of shortcuts', 'Long-press menu', 'Change it any time'],
    },
  },
  simple: {
    chip: 'HOW IT WORKS', title: 'Swipe, tap, hold',
    subtitle: 'Swipe up or down on the button to change the volume. Tap it for the volume panel. Hold it to move it anywhere.',
    scene: 'Simple', caption: 'Swipe · Tap · Hold',
    points: ['Swipe up – louder', 'Swipe down – quieter', 'Tap – volume panel', 'Hold – move it'],
  },
  quickslider: {
    chip: 'QUICK SLIDER', title: 'Pull the bar into a slider',
    subtitle: 'Swipe up or down on the bar and it grows into a full-height volume slider. Keep sliding to set the level.',
    scene: 'QuickSlider', caption: 'Swipe · Slide',
    points: ['Swipe up – louder', 'Swipe down – quieter', 'Slide – set the level', 'Full-height slider'],
  },
  deck: {
    chip: 'DECK', title: 'Your shortcuts, one swipe away',
    subtitle: 'Swipe inward from the bar to open the Deck: apps, toggles, quick dial and more.',
    scene: 'Deck', caption: 'Swipe inward',
    points: ['Swipe inward – open', 'Apps', 'Toggles', 'Quick dial'],
  },
  longpress: {
    chip: 'LONG-PRESS MENU', title: 'Hold for everything else',
    subtitle: 'Hold the bar to open a menu of actions: screenshot, lock, flashlight and more. Hold and drag to move the bar.',
    scene: 'LongPress', caption: 'Hold · Drag',
    points: ['Hold – open the menu', 'Hold and drag – move', 'Screenshot and lock', 'Flashlight and more'],
  },
  move: {
    chip: 'MOVE IT', title: 'Put it where your thumb is',
    subtitle: 'Hold the bar until it dims and shows arrows, then drag it anywhere: up, down, or across to the other side. Let go and it settles on the nearer edge.',
    scene: { regular: 'MoveButton', advanced: 'MoveTab' }, caption: 'Hold · Drag · Let go',
    points: ['Hold – pick it up', 'Drag – anywhere', 'Let go – it finds the edge', 'Stays where you leave it'],
  },
  permission: {
    chip: 'PERMISSION', title: 'Allow the bar on screen',
    subtitle: 'Android asks you to allow Gesture Volume to display over other apps, so the bar can sit on top of them.',
    scene: 'Permission', settled: PERMISSION_SETTLED,
    body: 'To show the volume handler on top of other apps, we need the \'Draw over other apps\' permission.',
    grantedLabel: 'Permission Granted ✓',
    points: ['Display over other apps', 'Keeps the bar on top', 'One switch in Settings', 'Off again any time'],
  },
  notifications: {
    chip: 'NOTIFICATIONS', title: 'Controls in your notifications',
    subtitle: 'Optional. Allow notifications and the bar\'s controls wait in your notification shade: hide or show the bar, open settings, or stop it, even with the app closed.',
    scene: 'Notifications', settled: NOTIFICATIONS_SETTLED,
    body: 'It is the only way back to a bar you hid while the app is closed. Gesture Volume posts no other notifications.',
    grantedLabel: 'Notifications on ✓',
    points: ['Hide or show the bar', 'Settings and Stop', 'Works with the app closed', 'Off again any time'],
  },
  accessibility: {
    chip: 'ACCESSIBILITY', title: 'Instant volume keys',
    subtitle: 'Optional. Turn on the Gesture Volume accessibility service so a volume key opens the Quick slider at once, and Lock screen and Screenshot work from the bar and the Deck.',
    scene: 'Accessibility', settled: ACCESSIBILITY_SETTLED,
    body: 'It is used only for the features you switch on, and nothing leaves your phone. Before Settings opens, you will see exactly what it does.',
    grantedLabel: 'Accessibility on ✓',
    points: ['Volume keys open the slider', 'Lock screen and Screenshot', 'Nothing leaves your phone', 'Off again any time'],
  },
};

// WalkPage's enum order: it sets which way a page slides in.
export const WALK_ORDER = ['intro', 'style', 'simple', 'quickslider', 'deck', 'longpress', 'move', 'permission', 'notifications', 'accessibility'];
const ASKS = new Set(['permission', 'notifications', 'accessibility']);

// pagesFor(mode): the pages a first run shows, in order. `notifications` is Android 13 and later.
export function walkPages(mode = 'regular', { notifications = true } = {}) {
  const adv = mode === 'advanced';
  return ['intro', 'style', ...(adv ? ['quickslider', 'deck', 'longpress'] : ['simple']), 'move', 'permission',
    ...(notifications ? ['notifications'] : []), ...(adv ? ['accessibility'] : [])];
}

// The walkthrough's scene for a page in a mode ('Intro', 'MoveTab', ...), or null for the Style page.
export function walkScene(page, mode = 'regular') {
  const s = PAGES[page]?.scene;
  return s == null ? null : typeof s === 'string' ? s : s[mode === 'advanced' ? 'advanced' : 'regular'];
}

// --- Pieces ---------------------------------------------------------------------------------------
// StageBackdrop (§5.3) behind a card whose size is only known after layout: the card is a size
// container, so the glows' radii (a fraction of the longer side) and the diagonal wash's angle
// come out of container units.
function backdropLayer() {
  const glow = (rgb, a, x, y, k) => `radial-gradient(circle ${k * 100}cqmax at ${x}% ${y}%, rgba(${rgb},${a}) 0%, rgba(${rgb},${a * 0.4}) 50%, rgba(${rgb},0) 100%)`;
  return `<div class="stage-backdrop" style="position:absolute;inset:0;pointer-events:none;background:${glow('52,207,182', 0.36, 90, 95, 0.6)},${glow('124,131,255', 0.34, 92, 8, 0.45)},${glow('255,154,118', 0.42, 8, 12, 0.55)},
      linear-gradient(calc(90deg + atan2(100cqh, 100cqw)),#F3F2FF,#E7ECFF)"></div>`;
}

// A filled or outlined M3 button at the foot of a page: 56 dp, 18 dp corners, 16 sp SemiBold.
function actionButton(label, { target = null, outlined = false } = {}) {
  const look = outlined
    ? `background:transparent;box-shadow:inset 0 0 0 1px ${C.outlineVariant};color:${C.onSurfaceVariant}`
    : `background:${C.primary};color:${C.onPrimary}`;
  return `<div ${target ? `data-target="${target}"` : ''} style="min-height:56px;border-radius:18px;${look};display:flex;align-items:center;justify-content:center;
      padding:8px 24px;${T.action};text-align:center">${label}</div>`;
}

function pointGrid(points) {
  const rows = [];
  for (let i = 0; i < points.length; i += 2) rows.push(points.slice(i, i + 2));
  const chip = (p) => `<div style="flex:1 1 0;min-width:0;border-radius:14px;background:${C.surfaceContainerHigh};display:flex;align-items:center;padding:10px 12px">
      <div style="width:6px;height:6px;border-radius:50%;background:${C.primary};flex:none"></div><div style="width:8px;flex:none"></div>
      <div style="${T.labelMedium};color:${C.onSurface};display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">${p}</div></div>`;
  return `<div data-target="points" style="display:flex;flex-direction:column;gap:8px">${rows.map((r) => `<div style="display:flex;gap:8px;align-items:stretch">
      ${r.map(chip).join('')}${r.length < 2 ? '<div style="flex:1 1 0"></div>' : ''}</div>`).join('')}</div>`;
}

function header(chip, title, subtitle) {
  return `<div class="walk-header" style="flex:none">
      <div style="height:6px"></div>
      <div style="display:flex"><div style="border-radius:999px;background:${C.secondaryContainer};padding:6px 12px;${T.chip};color:${C.onSecondaryContainer};white-space:nowrap">${chip}</div></div>
      <div style="height:12px"></div>
      <div style="${T.headlineSmall};color:${C.onBackground}">${title}</div>
      <div style="height:6px"></div>
      <div style="${T.bodyMedium};color:${C.onSurfaceVariant}">${subtitle}</div>
      <div style="height:16px"></div></div>`;
}

function sceneBox(scene, settled = null) {
  return `<div class="walk-scene" style="position:relative;flex:1 1 0;min-height:0;margin-top:16px">
      <canvas class="walk-canvas" data-scene="${scene}" ${settled != null ? `data-settled="${settled}"` : ''} style="position:absolute;left:0;top:0;width:100%;height:100%;display:block"></canvas></div>`;
}

// IllustrationCard: the scene on the stage backdrop, the caption and the breathing "Active edge".
function illustrationCard(scene, caption) {
  return `<div class="walk-card" data-target="card" style="position:relative;flex:1 1 0;min-height:228px;border-radius:28px;overflow:hidden;container-type:size;display:flex;flex-direction:column">
      ${backdropLayer()}${sceneBox(scene)}
      <div style="position:relative;flex:none;display:flex;align-items:center;padding:14px 18px">
        <div style="flex:1;min-width:0;${T.labelLarge};color:${C.onSurfaceVariant}">${caption}</div>
        <div style="display:flex;align-items:center;flex:none"><div class="active-edge-dot" style="width:7px;height:7px;border-radius:50%;background:${EDGE_DOT}"></div>
          <div style="width:6px"></div><div style="${T.labelMedium};color:${C.onSurfaceVariant};white-space:nowrap">Active edge</div></div>
      </div></div>`;
}

// The walkthrough's PermissionCard: the scene (still at its settled moment once granted), the
// reason, and the green chip that fades and expands in when the permission is on.
function permissionCard(d, g) {
  const p = Ease.fastOutSlowIn(clamp(g));
  return `<div class="walk-card" data-target="card" style="position:relative;flex:1 1 0;min-height:240px;border-radius:28px;overflow:hidden;container-type:size;display:flex;flex-direction:column">
      ${backdropLayer()}${sceneBox(d.scene, g > 0 ? d.settled : null)}
      <div style="position:relative;flex:none;padding:14px 18px">
        <div style="${T.bodySmall};color:${C.onSurfaceVariant}">${d.body}</div>
        ${g > 0 ? `<div style="height:${44.75 * p}px;overflow:hidden;opacity:${p};display:flex;flex-direction:column;justify-content:flex-end">
          <div style="display:flex;padding-top:10px"><div data-target="granted" style="border-radius:16px;background:rgba(16,185,129,.1);padding:8px 16px;
            font:700 16px/1.172 Roboto;letter-spacing:.5px;color:${GRANTED};white-space:nowrap">${d.grantedLabel}</div></div></div>` : ''}
      </div></div>`;
}

// StyleCard: the preview (a canvas, painted now and bobbing once mounted), the words, the radio.
// `c` 0..1: how far its colours have come to the selected look (they animate; the border's width
// switches at once).
function styleCard(mode, selected, c = 1) {
  const adv = mode === 'advanced';
  const title = adv ? 'Advanced' : 'Simple';
  const body = adv ? 'The Dock tab with the Quick slider, the Deck of shortcuts and a long-press menu.'
    : 'A round button. Swipe for volume, tap for the volume panel. Nothing else to learn.';
  const s = selected ? c : 1 - c;   // 1 = the selected look
  const mixc = (a, b) => `rgba(${[0, 1, 2].map((i) => Math.round(lerp(a[i], b[i], s))).join(',')},${lerp(a[3], b[3], s).toFixed(3)})`;
  const ring = mixc([75, 85, 99, 1], [79, 70, 229, 1]);          // onSurfaceVariant -> primary
  return `<div data-target="style-${mode}" class="style-card" style="flex:none;border-radius:24px;background:${mixc([243, 237, 247, 1], [224, 231, 255, 0.55])}; /* surfaceContainer -> primaryContainer@.55 */
      box-shadow:inset 0 0 0 ${selected ? 2 : 1}px ${mixc([229, 231, 235, 1], [79, 70, 229, 1])};display:flex;align-items:center;padding:14px">
      <div style="width:64px;height:88px;border-radius:16px;overflow:hidden;background:${C.surfaceContainerHighest};flex:none">
        <canvas class="style-preview" data-advanced="${adv ? 1 : 0}" data-selected="${selected ? 1 : 0}" width="192" height="264" style="width:64px;height:88px;display:block"></canvas></div>
      <div style="width:14px;flex:none"></div>
      <div style="flex:1;min-width:0"><div style="${T.titleMedium};color:${C.onSurface}">${title}</div><div style="height:4px"></div>
        <div style="${T.bodySmall};color:${C.onSurfaceVariant}">${body}</div></div>
      <div style="width:4px;flex:none"></div>
      <svg width="24" height="24" viewBox="0 0 24 24" style="display:block;flex:none"><circle cx="12" cy="12" r="9" fill="none" stroke="${ring}" stroke-width="2"/>
        ${s > 0 ? `<circle cx="12" cy="12" r="${(5 * s).toFixed(2)}" fill="${ring}"/>` : ''}</svg></div>`;
}

// One page below the top row: the words, the card, the points, then the buttons (PageContent).
// `x`, `alpha`: where a page change has it; `leaving`: the page going away, whose targets are
// renamed so that a lookup finds the arriving page's.
function pageLayer(page, o, { x = 0, alpha = 1, leaving = false } = {}) {
  const d = PAGES[page];
  const asks = ASKS.has(page);
  const g = asks ? o.granted : 0;
  const text = page === 'style' && o.tour
    ? { chip: d.tourChip, title: d.tourTitle, subtitle: d.tourSubtitle[o.tourMode] }
    : { chip: d.chip, title: d.title, subtitle: d.subtitle };
  let pointsHtml = pointGrid(page === 'style' ? d.points[o.highlighted] : d.points);
  let card;
  if (page === 'style') {
    // A new highlight: the cards' colours on animateColorAsState's spring, the points crossfading
    // over 300 ms; `pick` is 0..1 of that 0.3 s.
    const p = clamp(o.pick);
    const c = p < 1 ? clamp(spring(p * 0.3, { dampingRatio: 1, stiffness: 1500 })) : 1;
    card = `<div class="style-choice" style="flex:1 1 0;min-height:0;display:flex;flex-direction:column;gap:12px">
        ${styleCard('regular', o.highlighted === 'regular', c)}${styleCard('advanced', o.highlighted === 'advanced', c)}</div>`;
    if (p < 1) {
      const f = Ease.fastOutSlowIn(p);
      const was = o.highlighted === 'advanced' ? 'regular' : 'advanced';
      pointsHtml = `<div style="position:relative"><div style="position:absolute;left:0;right:0;top:0;opacity:${(1 - f).toFixed(3)}">${pointGrid(d.points[was]).replace('data-target=', 'data-was-target=')}</div>
          <div style="opacity:${f.toFixed(3)}">${pointsHtml}</div></div>`;
    }
  } else if (asks) card = permissionCard(d, g);
  else card = illustrationCard(walkScene(page, o.mode), d.caption);

  let actions;
  if (asks && !(g > 0)) {
    actions = `<div style="display:flex;gap:12px">
        <div style="flex:1 1 0;min-width:0">${actionButton('Skip for now', { target: 'skip', outlined: true })}</div>
        <div data-target="next" style="flex:1 1 0;min-width:0">${actionButton('Allow', { target: 'grant' })}</div></div>`;
  } else {
    const label = asks ? (o.isLast ? 'Start' : 'Continue') : (o.isLastTutorial ? 'Got it' : 'Continue');
    actions = actionButton(label, { target: 'next' });
  }
  const html = `<div class="walk-page${leaving ? ' leaving' : ''}" data-page="${page}" style="position:absolute;inset:0;display:flex;flex-direction:column;
      ${x ? `transform:translateX(${x.toFixed(2)}px);` : ''}${alpha < 1 ? `opacity:${alpha.toFixed(3)};` : ''}">
      <div class="walk-body" style="flex:1 1 0;min-height:0;display:flex;flex-direction:column">
        ${header(text.chip, text.title, text.subtitle)}${card}
        <div style="flex:none"><div style="height:12px"></div>${pointsHtml}</div>
      </div>
      <div style="height:16px;flex:none"></div><div style="flex:none">${actions}</div><div style="height:12px;flex:none"></div></div>`;
  return leaving ? html.replace(/data-target=/g, 'data-was-target=') : html;
}

// TopRow: Back (from the second page), the dots and n/N, and Skip on pages that ask for nothing.
function topRow({ count, index, fromIndex, dt, showSkip, skipLabel }) {
  const moving = fromIndex != null && fromIndex !== index;
  const dotK = moving ? spring(dt, { dampingRatio: 0.7, stiffness: 500 }) : 1;
  const colK = moving ? clamp(spring(dt, { dampingRatio: 1, stiffness: 1500 })) : 1;
  const faint = [31, 41, 55, 0.15], strong = [79, 70, 229, 1];
  const rgba = (a, b, k) => `rgba(${a.slice(0, 3).map((v, i) => Math.round(lerp(v, b[i], k))).join(',')},${lerp(a[3], b[3], k).toFixed(3)})`;
  const dots = Array.from({ length: count }, (_, i) => {
    const w1 = i === index ? 20 : 6;
    const w0 = moving ? (i === fromIndex ? 20 : 6) : w1;
    const c1 = i <= index ? strong : faint;
    const c0 = moving ? (i <= fromIndex ? strong : faint) : c1;
    return `<div style="width:${Math.max(0, lerp(w0, w1, dotK)).toFixed(2)}px;height:6px;border-radius:3px;background:${rgba(c0, c1, colK)};flex:none"></div>`;
  }).join('');
  // AnimatedVisibility(fadeIn + expandHorizontally): spring(1, 400) both ways.
  let back = index > 0 ? 1 : 0;
  if (moving && (fromIndex === 0) !== (index === 0)) {
    const e = clamp(spring(dt, { dampingRatio: 1, stiffness: 400 }));
    back = index > 0 ? e : 1 - e;
  }
  const skip = showSkip
    ? `<div data-target="skip" style="flex:none;max-width:186px;min-width:58px;height:40px;border-radius:20px;padding:0 12px;display:flex;align-items:center;justify-content:center">
        <span style="${T.labelLarge};color:${C.onSurfaceVariant};white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${skipLabel}</span></div>`
    : '';
  return `<div class="walk-top" style="height:52px;flex:none;display:flex;align-items:center">
      ${back > 0 ? `<div style="position:relative;width:${(48 * back).toFixed(2)}px;height:48px;flex:none;overflow:hidden;opacity:${back.toFixed(3)}">
        <div data-target="back" style="position:absolute;left:-8px;top:0;width:48px;height:48px;border-radius:50%;display:grid;place-items:center">${icon('filled/arrow_back', 24, C.onSurface)}</div></div>` : ''}
      <div data-target="progress" style="flex:1 1 0;min-width:0;height:16px;overflow:hidden;display:flex;align-items:center">
        <div style="display:flex;gap:5px;flex:none;align-items:center">${dots}</div><div style="width:10px;flex:none"></div>
        <div style="${T.labelMedium};color:${C.onSurfaceVariant};white-space:nowrap;flex:none">${index + 1}/${count}</div></div>
      ${skip}</div>`;
}

// The walkthrough on one page, as WalkthroughScreen draws it on a phone held upright.
//   page: 'intro' | 'style' | 'simple' | 'quickslider' | 'deck' | 'longpress' | 'move' | 'permission'
//         | 'notifications' | 'accessibility'
//   mode: the style the pages after Style follow ('regular' = Simple, 'advanced'); highlighted:
//         the card highlighted on the Style page, which sets n/N there as the app does (defaults
//         to mode: the app keeps one value for both).
//   granted: a permission page's permission is on (true, or 0..1 while its chip fades and expands
//         in): the scene rests at its settled moment and Allow/Skip become Continue (Start on the
//         last page).
//   from, k: a page change in progress, from page `from` to `page`; k = 0..1 over its 0.5 s. The
//         new page slides in a quarter of the width from the side it sits on (ScreenOffset spring)
//         and fades in (ScreenFade); the old one leaves the other way; the dots and Back follow.
//         fromGranted: the old page's granted state, when it is a permission page.
//   pick: on the Style page, 0..1 over the 0.3 s after a card is tapped: the cards' colours move
//         to the new highlight and the points crossfade (1 = settled).
//   tour: the tour's words (Skip reads "Close", the Style page shows the two styles; tourMode is
//         the style the user is on); pages: an explicit page list (a tour leaves out permissions
//         already granted); notifications: false for Android 12 and older (no Notifications page).
// data-target: back, progress, skip (Skip for now / Close in the top row; on a permission page the
//   bottom Skip for now), next (the primary button: Continue / Got it / Allow / Start), grant (the
//   Allow button on a permission page that is not yet granted, inside `next`), style-regular,
//   style-advanced (the Style page's cards), card (the illustration or permission card), granted
//   (the green chip), points.
// The canvases are blank until mountWalkthrough(el)(t) paints them.
export function walkthroughPage({ page = 'intro', status = 32, mode = 'regular', highlighted = null, granted = false, k = 0,
  from = null, fromGranted = true, pick = 1, tour = false, tourMode = null, pages = null, notifications = true } = {}) {
  highlighted = highlighted || mode;
  const order = (mo) => pages || walkPages(mo, { notifications });
  // N follows the highlighted card on the Style page and the chosen style after it; a page the
  // style does not have (Simple with 'advanced') is counted in the other style's list.
  const listFor = (p) => {
    const m = p === 'style' ? highlighted : mode;
    const list = order(m);
    return list.includes(p) ? list : order(m === 'advanced' ? 'regular' : 'advanced');
  };
  const list = listFor(page);
  const index = Math.max(0, list.indexOf(page));
  const g = typeof granted === 'number' ? clamp(granted) : granted ? 1 : 0;
  const base = { mode, highlighted, pick, tour, tourMode: tourMode || mode };
  const opts = (p, gr) => {
    const l = listFor(p);
    const lastTutorial = [...l].reverse().find((x) => !ASKS.has(x));
    return { ...base, granted: gr, isLast: l.indexOf(p) === l.length - 1, isLastTutorial: p === lastTutorial };
  };
  const moving = from && from !== page && PAGES[from] && clamp(k) < 1;
  const dt = clamp(k) * 0.5;
  const fromIndex = moving ? Math.max(0, list.indexOf(from)) : null;

  let layers = pageLayer(page, opts(page, g));
  if (moving) {
    const dir = WALK_ORDER.indexOf(page) > WALK_ORDER.indexOf(from) ? 1 : -1;
    const off = spring(dt, { dampingRatio: 0.8, stiffness: 380 });
    const fade = clamp(spring(dt, { dampingRatio: 1, stiffness: 1600 }));
    const shift = (W - 40) / 4;
    const fg = typeof fromGranted === 'number' ? clamp(fromGranted) : fromGranted ? 1 : 0;
    layers = pageLayer(from, opts(from, ASKS.has(from) ? fg : 0), { x: -dir * shift * off, alpha: 1 - fade, leaving: true })
      + pageLayer(page, opts(page, g), { x: dir * shift * (1 - off), alpha: fade });
  }
  const el = M.screen(`
      <div style="position:absolute;left:0;right:0;top:0;height:${H - NAV}px;background:linear-gradient(${WASH},${C.background})"></div>
      <div class="walk-col" style="position:absolute;left:20px;width:${W - 40}px;top:${status}px;bottom:${NAV}px;display:flex;flex-direction:column">
        ${topRow({ count: list.length, index, fromIndex, dt, showSkip: !ASKS.has(page), skipLabel: tour ? 'Close' : 'Skip for now' })}
        <div class="walk-pages" style="position:relative;flex:1 1 0;min-height:0;overflow:hidden">${layers}</div>
      </div>`, { bg: C.background });
  el.classList.add('walkthrough');
  for (const cv of el.querySelectorAll('canvas.style-preview')) paintPreview(cv, 0);
  return el;
}

// --- Painting -------------------------------------------------------------------------------------
const breath = (t) => {      // ActiveEdge: 0.35 <-> 1, tween(900) FastOutSlowIn, reversing
  const ph = (((t / 0.9) % 2) + 2) % 2;
  return ph < 1 ? lerp(0.35, 1, Smooth(ph)) : lerp(1, 0.35, Smooth(ph - 1));
};
const bobAt = (t) => {       // StylePreview: -1 <-> 1, tween(1400) FastOutSlowIn, reversing
  const ph = (((t / 1.4) % 2) + 2) % 2;
  return ph < 1 ? lerp(-1, 1, Smooth(ph)) : lerp(1, -1, Smooth(ph - 1));
};
function paintPreview(cv, t) {
  const ctx = cv.getContext('2d');
  const sel = cv.dataset.selected === '1';
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.clearRect(0, 0, cv.width, cv.height);
  drawStylePreview(ctx, cv.dataset.advanced === '1', sel, sel ? bobAt(t) : 0, cv.width / 64);
}

// Paints a walkthrough page's illustrations at scene time t (seconds since the page appeared; each
// scene loops on its own cycle) at 3x: the scene canvas (held at its settled moment on a granted
// permission page), the Style page's previews (the selected one bobbing) and the breathing
// "Active edge" dot. `el` is the page, or anything holding it: the canvases are looked up on every
// call, so a container whose page is rebuilt (live()) keeps working. `page` names the scene for a
// canvas without one. During a page change, `tLeaving` is the leaving page's own clock.
export function mountWalkthrough(el, page = null, { dpr = 3 } = {}) {
  return (t = 0, tLeaving = t) => {
    for (const cv of el.querySelectorAll('canvas.walk-canvas')) {
      const scene = cv.dataset.scene || walkScene(page);
      const tc = cv.closest('.walk-page.leaving') ? tLeaving : t;
      const w = cv.offsetWidth, hh = cv.offsetHeight;
      if (!scene || !w || !hh) continue;
      const bw = Math.round(w * dpr), bh = Math.round(hh * dpr);
      if (cv.width !== bw || cv.height !== bh) { cv.width = bw; cv.height = bh; }
      const ctx = cv.getContext('2d');
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.clearRect(0, 0, bw, bh);
      const cyc = SCENES[scene].cycleMs;
      const tn = cv.dataset.settled != null ? +cv.dataset.settled : ((((tc * 1000) % cyc) + cyc) % cyc) / cyc;
      drawWalkScene(ctx, scene, tn, bw, bh);
    }
    for (const cv of el.querySelectorAll('canvas.style-preview')) paintPreview(cv, t);
    for (const dot of el.querySelectorAll('.active-edge-dot')) dot.style.opacity = breath(t).toFixed(3);
  };
}

// --- Dialog plumbing --------------------------------------------------------------------------------
// A platform dialog window over the whole screen: the dim behind it and the window animation
// (alpha 0 -> 1, scale 0.9 -> 1, decelerating), k = 0..1 of it.
function dialogLayer(k, inner, { dim = 0.6, status = 32, cls = 'dialog-layer' } = {}) {
  const e = clamp(k);
  const s = 0.9 + 0.1 * Ease.outCubic(e);
  return h(`<div class="${cls}" style="position:absolute;inset:0;z-index:30;font-family:Roboto;${e <= 0 ? 'visibility:hidden;' : ''}">
      <div class="scrim" style="position:absolute;inset:0;background:rgba(0,0,0,${(dim * e).toFixed(3)})"></div>
      <div style="position:absolute;left:0;right:0;top:${status}px;bottom:${NAV}px;display:flex;align-items:center;justify-content:center;padding:24px 0">
        <div class="dialog-window" style="opacity:${e.toFixed(3)};transform:scale(${s.toFixed(4)});max-height:100%;display:flex;flex-direction:column">${inner}</div>
      </div></div>`);
}

// --- The accessibility disclosure (AccessibilityDisclosureDialog) ---------------------------------
const DISCLOSURE = 'GestureVolume uses Android’s accessibility service for four things, all optional:\n\n'
  + '• To perform the system actions you assign to the bar: Lock screen, Screenshot, Back, Home, Recent apps, Notifications, Quick settings and the Power menu.\n\n'
  + '• To look at the two volume keys, only if you set the volume keys to Instant: to open the Quick panel the moment you press one. Every other key passes straight on, and no key is recorded. While a Volume down press is waiting, it also notes whether the system’s screenshot has come up, so a screenshot taken with the buttons does not open the panel.\n\n'
  + '• To see which app is on screen, only if you choose apps to hide the bar in or give apps gestures of their own. It looks at the app’s name and nothing else.\n\n'
  + '• To draw the bar on the lock screen, only if you turn on Show on the lock screen: Android hides every app’s floating bar there. While the phone is locked, the bar does only what the lock screen already allows: the volume, the Quick panel and quick toggles.\n\n'
  + 'It does not read or collect anything else. Nothing leaves your device. You can turn it off at any time in Settings › Accessibility.';

// The in-app disclosure shown before the accessibility list opens: an M3 AlertDialog (24 dp
// corners, surface, 24 dp padding) over the dim. Its text scrolls (`scroll`, px); the buttons wrap
// as M3's AlertDialogFlowRow does, the confirming one above. k = 0..1 fades and scales it in.
// data-target: accept ("Open Accessibility settings"), decline ("Not now").
export function accessibilityDisclosure({ k = 1, scroll = 0, status = 32 } = {}) {
  const btn = (label, target, filled) => `<div data-target="${target}" style="min-height:40px;min-width:58px;border-radius:12px;padding:10px 24px;
      ${filled ? `background:${C.primary};color:${C.onPrimary}` : `box-shadow:inset 0 0 0 1px ${C.outlineVariant};color:${C.onSurfaceVariant}`};
      ${T.labelLarge};white-space:nowrap;display:flex;align-items:center;justify-content:center;flex:none">${label}</div>`;
  const inner = `<div class="dialog" data-target="dialog" style="width:${W - 48}px;flex:0 1 auto;min-height:0;display:flex;flex-direction:column;background:${C.surface};border-radius:24px;padding:24px">
      <div style="${T.titleLarge};color:${C.primary};padding-bottom:16px;flex:none">Turn on the accessibility service?</div>
      <div class="dialog-text" style="flex:0 1 auto;min-height:0;overflow:hidden;margin-bottom:24px"><div style="transform:translateY(${-scroll}px)">
        <div style="${T.bodyMedium};color:${C.onSurfaceVariant};white-space:pre-line">${DISCLOSURE}</div>
        <div style="height:12px"></div>
        <div style="${T.bodyMedium};font-weight:600;color:${C.onSurface}">In the list, open GestureVolume and switch it on.</div></div></div>
      <div style="flex:none;display:flex;flex-wrap:wrap-reverse;justify-content:flex-end;gap:12px 8px">
        ${btn('Not now', 'decline', false)}${btn('Open Accessibility settings', 'accept', true)}</div></div>`;
  return dialogLayer(k, inner, { status, cls: 'dialog-layer disclosure' });
}

// --- The Permissions screen -------------------------------------------------------------------------
const PERMS = [
  { id: 'overlay', icon: 'filled/settings', title: 'Display over other apps', desc: 'Required to show the volume handler overlay on your screen' },
  { id: 'settings', icon: 'filled/brightness_high', title: 'Modify system settings', desc: 'Lets the bar control screen brightness. Only needed for brightness actions.' },
  { id: 'accessibility', icon: 'filled/accessibility', title: 'Accessibility service', desc: 'Optional: performs Lock screen, Screenshot, Back, Home, Recent apps, Notifications, Quick settings and Power menu.' },
  { id: 'dnd', icon: 'filled/do_not_disturb_on', title: 'Do Not Disturb access', desc: 'Optional: lets the bar and the Deck switch Do Not Disturb on and off.' },
  { id: 'notifications', icon: 'filled/notifications', title: 'Notifications', desc: 'Optional: shows the handler controls — Show, Settings and Stop — in the notification shade' },
  { id: 'phone', icon: 'filled/phone', title: 'Phone', desc: 'Optional: lets quick dial and search place calls directly, without the dialer.' },
];
const segShape = (i, n) => `${i === 0 ? 20 : 6}px ${i === 0 ? 20 : 6}px ${i === n - 1 ? 20 : 6}px ${i === n - 1 ? 20 : 6}px`;
const trimmed = (size, weight, color, extra = '') => `font:${weight} ${size}px/1.172 Roboto;letter-spacing:.5px;color:${color};${extra}`;

function iconTile(name, lit, size = 46) {
  return `<div style="width:${size}px;height:${size}px;border-radius:${size * 0.3}px;flex:none;display:grid;place-items:center;background:${lit ? C.primary : 'rgba(224,231,255,.6)'}">
      ${icon(name, size * 0.52, lit ? C.onPrimary : C.primary)}</div>`;
}

// One PermissionCard row. `flash` 0..1: the highlight (primary-container tint and a 2 dp border).
function permRow(p, { granted, warning, radius, flash = 0 }) {
  const f = clamp(flash);
  const bg = f > 0 ? `color-mix(in oklab, ${C.primaryContainer} ${(f * 100).toFixed(1)}%, rgba(243,244,246,.65))` : C.row;
  const btn = (glyph, label, target, outlined) => `<div data-target="${target}" style="margin-top:12px;min-height:40px;border-radius:14px;padding:8px 24px;display:flex;align-items:center;justify-content:center;
      ${outlined ? `box-shadow:inset 0 0 0 1px rgba(79,70,229,.6);color:${C.primary}` : `background:${C.primary};color:${C.onPrimary}`}">
      ${icon(glyph, 18, outlined ? C.primary : C.onPrimary)}<div style="width:8px"></div><div style="font:600 15px/20px Roboto;letter-spacing:.1px">${label}</div></div>`;
  return `<div data-target="perm-${p.id}" style="border-radius:${radius};background:${bg};${f > 0 ? `box-shadow:inset 0 0 0 2px rgba(79,70,229,${f.toFixed(3)});` : ''}padding:12px 14px">
      <div style="display:flex;align-items:center">${iconTile(p.icon, granted)}<div style="width:14px;flex:none"></div>
        <div style="flex:1;min-width:0"><div style="${trimmed(16, 600, C.onSurface)}">${p.title}</div><div style="${T.bodySmall};color:${C.onSurfaceVariant};padding-top:2px">${p.desc}</div></div>
        ${granted ? `<div style="width:10px;flex:none"></div>${icon('filled/check_circle', 24, C.primary)}` : ''}</div>
      ${!granted && warning ? `<div style="margin-top:12px;border-radius:14px;background:rgba(254,226,226,.6);padding:12px;display:flex;align-items:center">
        ${icon('filled/warning', 18, C.onErrorContainer)}<div style="width:10px;flex:none"></div>
        <div style="font:400 13px/18px Roboto;letter-spacing:.5px;color:${C.onErrorContainer};white-space:pre-line">Needed by what you have set up:${warning.map((w) => '\n• ' + w).join('')}</div></div>` : ''}
      ${!granted ? btn('filled/settings', 'Grant Permission', `grant-${p.id}`, false) : p.id === 'accessibility' ? btn('filled/close', 'Revoke Permission', 'revoke-accessibility', true) : ''}</div>`;
}

// PermissionsScreen (§4), Android 13+ (with the Notifications row).
//   granted: { overlay, settings, accessibility, dnd, notifications, phone } booleans.
//   controls: the Notification controls switch, 0..1 (on by default).
//   warnings: { <id>: ['Actions: Swipe up', ...] } the "Needed by what you have set up" lines.
//   highlight, flash: the row the user was sent here for and its highlight level (0..1; it rests
//   at 0.45 after the three pulses).
// data-target: back, hero, perm-overlay, perm-settings, perm-accessibility, perm-dnd,
//   perm-notifications, perm-controls, perm-phone (the rows); grant-<id> (each row's Grant Permission
//   button, while not granted); revoke-accessibility; controls-switch; controls-hint (when off).
export function permissionsScreen({ status = 32, scroll = 0, granted = {}, controls = 1, warnings = {}, highlight = null, flash = 0.45 } = {}) {
  const gr = { overlay: true, settings: false, accessibility: false, dnd: false, notifications: true, phone: false, ...granted };
  const row = (id, radius) => permRow(PERMS.find((p) => p.id === id), { granted: !!gr[id], warning: warnings[id], radius, flash: highlight === id ? flash : 0 });
  const heading = (s) => `<div style="padding:0 6px 10px;${trimmed(14, 600, C.primary, 'letter-spacing:.5px')}">${s}</div>`;
  const ck = clamp(controls);
  const controlsCard = `<div data-target="perm-controls" style="border-radius:${segShape(4, 6)};background:${C.row};padding:8px 14px">
      <div style="display:flex;align-items:center;padding:6px 0;border-radius:14px">
        <div style="flex:1;min-width:0"><div style="${trimmed(16, 600, C.onSurface)}">Notification controls</div>
          <div style="${T.bodySmall};color:${C.onSurfaceVariant};padding-top:2px">Keep Show, Settings and Stop in the notification shade. It is the only way to bring the handler back once you hide it without opening the app.</div></div>
        <div style="width:12px;flex:none"></div><div data-target="controls-switch" style="flex:none">${M.m3switchAt(ck)}</div></div>
      ${ck < 0.5 ? `<div data-target="controls-hint" style="margin-top:8px;min-height:40px;display:flex;align-items:center;font:500 12px/16px Roboto;letter-spacing:.1px;color:${C.primary}">Off: Android still requires a silent placeholder while the service runs. Tap to hide it completely in system settings.</div>` : ''}</div>`;
  const hero = `<div data-target="hero" style="border-radius:24px;background:rgba(224,231,255,.55);padding:16px;display:flex;align-items:center">
      <div style="width:56px;height:56px;border-radius:16.8px;background:${C.primary};display:grid;place-items:center;flex:none">${icon(gr.overlay ? 'filled/check_circle' : 'filled/shield', 29.12, C.onPrimary)}</div>
      <div style="width:16px;flex:none"></div>
      <div style="flex:1;min-width:0"><div style="${trimmed(17, 600, C.onSurface)}">${gr.overlay ? 'All set!' : 'Permissions'}</div>
        <div style="${T.bodyMedium};color:${C.onSurfaceVariant};padding-top:4px">${gr.overlay ? 'All required permissions are granted. You can now use the app.' : 'The app requires certain permissions to function properly. Grant permissions below.'}</div></div></div>`;
  const gap = '<div style="height:3px"></div>';
  return M.screen(`
      <div style="height:${status + 64}px;padding-top:${status}px;display:flex;align-items:center">
        <div data-target="back" style="width:48px;height:48px;margin-left:4px;border-radius:50%;display:grid;place-items:center;flex:none">${icon('filled/arrow_back', 24, C.onSurface)}</div>
        <div style="width:4px"></div><div style="flex:1;${T.titleLarge};color:${C.onPrimaryContainer}">Permissions</div></div>
      <div class="scroll" style="position:relative;overflow:hidden;height:${H - status - 64}px"><div style="padding:0 16px;transform:translateY(${-scroll}px)">
        ${hero}<div style="height:24px"></div>
        ${heading('Required')}${row('overlay', '20px')}<div style="height:24px"></div>
        ${heading('Optional')}${row('settings', segShape(0, 6))}${gap}${row('accessibility', segShape(1, 6))}${gap}${row('dnd', segShape(2, 6))}${gap}
        ${row('notifications', segShape(3, 6))}${gap}${controlsCard}${gap}${row('phone', segShape(5, 6))}
        <div style="height:56px"></div></div></div>`);
}

// --- Android's own pages: AOSP Settings in its Material You light theme -------------------------
// A neutral blue scheme (the tonal-spot palette of a plain blue seed), Roboto throughout, the
// SettingsLib layouts: a collapsing toolbar shown expanded, the app's header, preference rows of
// 20 sp titles, footers led by an info icon.
const A = {
  bg: '#F3F3FA', text: '#191C20', text2: '#44474E', outline: '#74777F', outlineVariant: '#C4C6D0',
  primary: '#415F91', onPrimary: '#FFFFFF', primaryContainer: '#D6E3FF', onPrimaryContainer: '#001B3E',
  secondaryContainer: '#DAE2F9', onSecondaryContainer: '#131C2B', high: '#E7E8EE', highest: '#E2E2E9', dialog: '#F9F9FF',
};

// The SettingsLib switch, k = 0 (off) .. 1 (on): M3 geometry in the system's colours.
function sysSwitch(k) {
  const on = k >= 0.5;
  const size = 16 + 8 * k, left = 8 + 20 * k;
  return `<div style="position:relative;width:52px;height:32px;border-radius:16px;flex:none;background:${on ? A.primary : A.highest};${on ? '' : `box-shadow:inset 0 0 0 2px ${A.outline};`}">
      <div style="position:absolute;width:${size}px;height:${size}px;border-radius:50%;background:${on ? A.onPrimary : A.outline};left:${left + 8 - size / 2}px;top:${16 - size / 2}px"></div></div>`;
}

// The page under SettingsLib's collapsing toolbar, expanded: the back arrow in a 56 dp toolbar,
// then the title at 36 sp, 24 dp in from the sides, with room above it (the toolbar is 226 dp tall
// for one line of title, 44 dp more for each further line).
function sysPage(title, body, { status = 32 } = {}) {
  return h(`<div class="app-screen system-settings" style="position:absolute;inset:0;background:${A.bg};font-family:Roboto;overflow:hidden;color:${A.text}">
      <div style="height:${status + 56}px;padding-top:${status}px;display:flex;align-items:center">
        <div data-target="back" style="width:48px;height:48px;margin-left:4px;border-radius:50%;display:grid;place-items:center">${icon('filled/arrow_back', 24, A.text)}</div></div>
      <div style="padding:66px 24px 32px;font:400 36px/44px Roboto;color:${A.text}">${title}</div>
      ${body}</div>`);
}

function footer(textHtml) {
  return `<div style="padding:16px 24px">${icon('outlined/info', 24, A.text2)}
      <div style="padding-top:16px;font:400 14px/20px Roboto;letter-spacing:.25px;color:${A.text2}">${textHtml}</div></div>`;
}

// Settings › Apps › Special app access › Display over other apps › GestureVolume (DrawOverlayDetails):
// the app's header, "Allow display over other apps" and the footer. k = 0..1: the switch.
// data-target: back, app, toggle (the switch), toggle-row (the whole row, which also toggles it).
export function systemOverlaySettings({ status = 32, k = 0 } = {}) {
  return sysPage('Display over other apps', `
      <div data-target="app" style="display:flex;flex-direction:column;align-items:center;padding:8px 24px 24px">
        ${gvIcon(64)}<div style="margin-top:16px;font:400 24px/32px Roboto;color:${A.text}">GestureVolume</div>
        <div style="margin-top:2px;font:400 14px/20px Roboto;letter-spacing:.25px;color:${A.text2}">1.5.2</div></div>
      <div data-target="toggle-row" style="display:flex;align-items:center;min-height:72px;padding:12px 24px">
        <div style="flex:1;font:400 20px/26px Roboto;color:${A.text}">Allow display over other apps</div>
        <div style="width:16px"></div><div data-target="toggle" style="flex:none">${sysSwitch(clamp(k))}</div></div>
      ${footer('Allow this app to display on top of other apps you’re using. This app will be able to see where you tap or change what’s displayed on the screen.')}`,
  { status });
}

const SERVICE_DESCRIPTION = 'GestureVolume uses the accessibility service to perform the system actions you assign to the floating bar — lock the screen, take a screenshot, go Back or Home, open recent apps, the notification shade, quick settings and the power menu. If you choose, it also opens the Quick panel the moment you press a volume key, looking at the two volume keys only. If you choose, it also draws the bar on the lock screen, where Android hides every app’s floating bar, and while the phone is locked the bar does only what the lock screen already allows: the volume, the Quick panel and quick toggles. If you choose apps to hide the bar in, or give apps gestures of their own, it notes which app is on screen, and nothing else about it. It reads nothing else, collects nothing, and everything stays on this device. While a Volume down press is waiting, it also notes whether the system’s screenshot has come up, so a screenshot taken with the buttons does not open the panel.';

// Settings › Accessibility › GestureVolume (ToggleAccessibilityServicePreferenceFragment): the main
// switch bar "Use GestureVolume", the shortcut row and the service's description. k = 0..1: the
// main switch. dialog = 0..1: the "full control" confirmation over it (faded and scaled in).
// data-target: back, toggle (the main switch), toggle-row (the bar), shortcut, allow, deny (the
// dialog's buttons, while it shows).
export function systemAccessibilitySettings({ status = 32, k = 0, dialog = 0 } = {}) {
  const kk = clamp(k);
  const el = sysPage('GestureVolume', `
      <div data-target="toggle-row" style="margin:0 16px;min-height:72px;border-radius:36px;padding:12px 16px 12px 24px;display:flex;align-items:center;
          background:${kk >= 0.5 ? A.primaryContainer : A.high}">
        <div style="flex:1;font:400 20px/26px Roboto;color:${A.text}">Use GestureVolume</div><div style="width:16px"></div>
        <div data-target="toggle" style="flex:none">${sysSwitch(kk)}</div></div>
      <div style="height:16px"></div>
      <div data-target="shortcut" style="display:flex;align-items:center;min-height:72px;padding:12px 24px">
        <div style="flex:1;min-width:0"><div style="font:400 20px/26px Roboto;color:${A.text}">GestureVolume shortcut</div>
          <div style="font:400 14px/20px Roboto;letter-spacing:.25px;color:${A.text2};margin-top:2px">Off</div></div>
        <div style="width:1px;height:40px;background:${A.outlineVariant};margin:0 16px 0 12px"></div>${sysSwitch(0)}</div>
      ${footer(SERVICE_DESCRIPTION)}`, { status });
  if (dialog > 0) el.appendChild(fullControlDialog({ k: dialog, status }));
  return el;
}

// AOSP's AccessibilityServiceWarning: "Allow GestureVolume to have full control of your device?"
function fullControlDialog({ k = 1, status = 32 } = {}) {
  const item = (glyph, title, text) => `<div style="display:flex;gap:16px;margin-top:20px">${icon(glyph, 24, A.primary)}
      <div style="flex:1;min-width:0"><div style="font:500 16px/24px Roboto;letter-spacing:.15px;color:${A.text}">${title}</div>
        <div style="font:400 14px/20px Roboto;letter-spacing:.25px;color:${A.text2}">${text}</div></div></div>`;
  const pill = (label, target, filled) => `<div data-target="${target}" style="height:48px;border-radius:24px;display:flex;align-items:center;justify-content:center;
      font:500 16px/24px Roboto;letter-spacing:.1px;${filled ? `background:${A.primary};color:${A.onPrimary}` : `box-shadow:inset 0 0 0 1px ${A.outline};color:${A.primary}`}">${label}</div>`;
  const inner = `<div class="dialog" data-target="dialog" style="width:${W - 48}px;border-radius:28px;background:${A.dialog};padding:24px">
      <div style="display:flex;justify-content:center">${gvIcon(36)}</div>
      <div style="margin-top:16px;text-align:center;font:400 24px/32px Roboto;color:${A.text}">Allow GestureVolume to have full control of your device?</div>
      <div style="margin-top:16px;font:400 14px/20px Roboto;letter-spacing:.25px;color:${A.text2}">Full control is appropriate for apps that help you with accessibility needs, but not for most apps.</div>
      ${item('outlined/visibility', 'View and control screen', 'It can read all content on the screen and display content over other apps.')}
      ${item('outlined/touch_app', 'View and perform actions', 'It can track your interactions with an app or a hardware sensor, and interact with apps on your behalf.')}
      <div style="margin-top:24px;display:flex;flex-direction:column;gap:8px">${pill('Allow', 'allow', true)}${pill('Deny', 'deny', false)}</div></div>`;
  return dialogLayer(k, inner, { status, cls: 'dialog-layer full-control' });
}

// Android 13's runtime request (GrantPermissionsActivity): the bell, the question with the app's
// name in bold, and Allow / Don't allow stacked as one rounded group. An overlay with its own dim,
// to put over a screen; k = 0..1 fades and scales it in.
// data-target: allow, deny.
export function notificationPermissionDialog({ k = 1, status = 32 } = {}) {
  const b = (label, target, radius) => `<div data-target="${target}" style="min-height:56px;border-radius:${radius};background:${A.secondaryContainer};display:flex;align-items:center;justify-content:center;
      padding:8px 16px;font:500 16px/24px Roboto;letter-spacing:.1px;color:${A.onSecondaryContainer}">${label}</div>`;
  const inner = `<div class="dialog" data-target="dialog" style="width:${W - 48}px;border-radius:28px;background:${A.dialog};padding:24px">
      <div style="display:flex;justify-content:center">${icon('filled/notifications', 32, A.primary)}</div>
      <div style="margin:16px 8px 0;text-align:center;font:400 22px/28px Roboto;color:${A.text}">Allow <b style="font-weight:700">GestureVolume</b> to send you notifications?</div>
      <div style="margin-top:24px;display:flex;flex-direction:column;gap:4px">${b('Allow', 'allow', '20px 20px 4px 4px')}${b('Don’t allow', 'deny', '4px 4px 20px 20px')}</div></div>`;
  return dialogLayer(k, inner, { status, cls: 'dialog-layer notification-request' });
}

// --- A lock screen ------------------------------------------------------------------------------------
// A plain lock screen to lay over the Phone's wallpaper (no background of its own): the padlock,
// a big clock and the date, free space where notifications would be, the fingerprint glyph and the
// two corner shortcuts, and the hint at the foot.
// data-target: lock, clock, fingerprint, flashlight, camera.
export function lockScreen({ time = '9:30', date = 'Thursday, October 9', status = 32, hint = 'Swipe up to open' } = {}) {
  const shadow = 'text-shadow:0 1px 3px rgba(0,0,0,.28)';
  const corner = (glyph, target, side) => `<div data-target="${target}" style="position:absolute;${side}:32px;bottom:${NAV + 24}px;width:52px;height:52px;border-radius:50%;
      background:rgba(28,27,31,.42);display:grid;place-items:center">${icon(glyph, 24, '#fff')}</div>`;
  return h(`<div class="lockscreen" style="position:absolute;inset:0;font-family:Roboto;color:#fff;pointer-events:none">
      <div data-target="lock" style="position:absolute;left:${W / 2 - 12}px;top:${status + 20}px;width:24px;height:24px;filter:drop-shadow(0 1px 2px rgba(0,0,0,.3))">${icon('filled/lock', 24, '#fff')}</div>
      <div data-target="clock" style="position:absolute;left:0;right:0;top:${status + 76}px;text-align:center">
        <div style="font:300 96px/1 Roboto;letter-spacing:-1px;${shadow}">${time}</div>
        <div style="margin-top:14px;font:400 18px/24px Roboto;letter-spacing:.2px;${shadow}">${date}</div></div>
      <div data-target="fingerprint" style="position:absolute;left:${W / 2 - 24}px;top:${H - 170}px;width:48px;height:48px;display:grid;place-items:center;opacity:.92;filter:drop-shadow(0 1px 2px rgba(0,0,0,.3))">${icon('filled/fingerprint', 44, '#fff')}</div>
      ${corner('filled/flashlight_on', 'flashlight', 'left')}${corner('filled/photo_camera', 'camera', 'right')}
      ${hint ? `<div style="position:absolute;left:96px;right:96px;bottom:${NAV + 38}px;text-align:center;font:400 14px/20px Roboto;letter-spacing:.25px;opacity:.95;${shadow}">${hint}</div>` : ''}
    </div>`);
}
