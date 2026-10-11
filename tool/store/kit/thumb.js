// YouTube thumbnails (1280 x 720, drawn at 1920 x 1080 and scaled down): the app's name, a title
// big enough to read on a phone, and the phone showing the moment that matters, tilted a little.
import { h } from './core.js';
import { gvIcon } from './phone.js';

export function thumbOverlay({ title, badge = null, sub = null }) {
  return h(`<div class="thumb" style="position:absolute;inset:0;z-index:50;pointer-events:none">
      <div style="position:absolute;inset:0;background:linear-gradient(90deg,rgba(16,11,51,.92) 0%,rgba(16,11,51,.78) 40%,rgba(16,11,51,0) 62%)"></div>
      <div style="position:absolute;left:110px;top:0;bottom:0;width:930px;display:flex;flex-direction:column;justify-content:center">
        <div style="display:flex;align-items:center;gap:24px">${gvIcon(92, 'squircle')}
          <div style="font:800 50px/1 'Plus Jakarta Sans';letter-spacing:-.01em;color:#fff;white-space:nowrap">Gesture Volume</div></div>
        <div style="margin-top:44px;font:800 164px/.96 'Plus Jakarta Sans';letter-spacing:-.045em;color:#fff;text-wrap:balance">${title}</div>
        ${sub ? `<div style="margin-top:40px;font:600 50px/1.22 'Plus Jakarta Sans';color:#C9C2FF;text-wrap:balance">${sub}</div>` : ''}
        ${badge ? `<div style="margin-top:48px;align-self:flex-start;border-radius:999px;background:#7C6CF6;padding:16px 36px;font:800 44px/1 'Plus Jakarta Sans';color:#fff;white-space:nowrap">${badge}</div>` : ''}
      </div></div>`);
}

// The frame's transform that puts phone point (cx, cy) at (fx, fy), scaled s and turned deg.
export const thumbTransform = ({ cx, cy, s, deg = -4, fx = 1440, fy = 560 }) =>
  `translate(${fx}px,${fy}px) rotate(${deg}deg) scale(${s}) translate(${-(cx + 7)}px,${-(cy + 7)}px)`;
