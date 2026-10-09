// Dialogs on the app's home screen (home.md §5), as overlays to lay over a screen: a scrim and
// an M3 AlertDialog, faded and scaled in by k (0..1).
import { h } from './core.js';
import { LIGHT as C } from './m3.js';

function alert({ title, text, buttons, k = 1, titleColor = C.onSurface, titleFont = '400 24px/32px Roboto', bg = '#ECE6F0', radius = 28 }) {
  const btn = (b) => `<div data-target="${b.id}" style="height:40px;min-width:58px;padding:0 ${b.filled ? 24 : 12}px;border-radius:20px;display:grid;place-items:center;
      font:500 14px/20px Roboto;letter-spacing:.1px;${b.filled ? `background:${C.primary};color:#fff` : `color:${C.primary}`}">${b.label}</div>`;
  return h(`<div class="dialog" style="position:absolute;inset:0;z-index:30;pointer-events:none">
      <div style="position:absolute;inset:0;background:rgba(0,0,0,${0.5 * k})"></div>
      <div class="card" style="position:absolute;left:50%;top:50%;width:340px;transform:translate(-50%,-50%) scale(${0.92 + 0.08 * k});opacity:${Math.min(1, k * 1.4)};
          background:${bg};border-radius:${radius}px;padding:24px;box-shadow:0 6px 24px rgba(0,0,0,.18)">
        <div style="font:${titleFont};color:${titleColor}">${title}</div>
        <div style="margin-top:16px;font:400 14px/20px Roboto;letter-spacing:.25px;color:${C.onSurfaceVariant}">${text}</div>
        <div style="margin-top:24px;display:flex;justify-content:flex-end;gap:8px">${buttons.map(btn).join('')}</div>
      </div></div>`);
}

// Mode-switch confirmation (AdvancedFeaturesGroup.kt, M3 defaults). data-targets: cancel, switch.
export function modeSwitchDialog({ to = 'advanced', k = 1 } = {}) {
  const adv = to === 'advanced';
  return alert({
    k,
    title: adv ? 'Switch to Advanced?' : 'Switch to Simple?',
    text: adv
      ? 'Your bar becomes the Dock: swipe up or down for the Quick slider, swipe inward for the Deck, hold for the long-press menu. Its look and gestures are replaced; you can switch back any time.'
      : 'Your bar becomes the round button: swipe up or down for volume, tap for the volume panel, hold to move it. The Quick slider, the Deck and the long-press menu are turned off, the volume keys included. You can switch back any time.',
    buttons: [{ id: 'cancel', label: 'Cancel' }, { id: 'switch', label: 'Switch' }],
  });
}
