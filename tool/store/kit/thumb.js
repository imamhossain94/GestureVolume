// YouTube thumbnails (1280 x 720, drawn at 1920 x 1080 and scaled down), in the style of the Bleed
// screenshots: dark words on their light backdrop on the left, and the phone upright on the right,
// large and cropped at the bottom, the same size and in the same place in every one.
import { h } from './core.js';

export function thumbOverlay({ title, sub = null, badge = null }) {
  // A hyphenated word stays whole, so "Long-press" never breaks after its hyphen.
  title = title.replace(/\S+-\S+/g, (w) => `<span style="white-space:nowrap">${w}</span>`);
  return h(`<div class="thumb" style="position:absolute;left:120px;top:0;bottom:0;width:900px;z-index:50;pointer-events:none;display:flex;flex-direction:column;justify-content:center">
      <div style="font:700 32px/1 'Plus Jakarta Sans';letter-spacing:.16em;text-transform:uppercase;color:#4F46E5">Gesture Volume</div>
      <div style="margin-top:30px;font:800 150px/.98 'Plus Jakarta Sans';letter-spacing:-.04em;color:#1E1B4B;text-wrap:balance">${title}</div>
      ${sub ? `<div style="margin-top:36px;max-width:820px;font:500 46px/1.28 'Plus Jakarta Sans';color:#4F46E5;text-wrap:balance">${sub}</div>` : ''}
      ${badge ? `<div style="margin-top:48px;align-self:flex-start;border-radius:999px;background:#4F46E5;padding:16px 34px;font:800 40px/1 'Plus Jakarta Sans';color:#fff;white-space:nowrap">${badge}</div>` : ''}
    </div>`);
}

// Where the phone's frame (426 x 929 at scale 1) goes: scaled 1.42, its top 96 px down and its right
// edge 150 px in from the thumbnail's right edge, so about the top three quarters of the screen show.
export const thumbTransform = (W = 1920, s = 1.42) => `translate(${W - 150 - 426 * s}px,96px) scale(${s})`;
