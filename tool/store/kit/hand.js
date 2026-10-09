// The app's pointing hand (ui/components/PointingHand.kt), the one its walkthrough and "How it
// works" demos use, as SVG; and the demo's touch rings (GestureDemo.drawTouchRings).
// Hand units: the finger is 20 wide, the fingertip pad at (0, 0), y down.
import { clamp, lerp } from './core.js';

const P = {
  outline: 'M -10 1 C -10 -6.5 -5.6 -11.2 0 -11.2 C 5.6 -11.2 10 -6.5 10 1 L 10.9 51 C 11.6 45.6 15.6 42.6 20.6 42.6 C 25.8 42.6 29.8 46 30.3 51.4 C 32.2 48.8 35.2 47.8 38.4 48 C 43.6 48.4 47.2 52.2 47.3 57.6 C 48.9 56.2 51.1 55.6 53.3 56 C 57.9 56.8 60.9 60.6 60.9 66 C 60.9 80 60.3 96 58.1 112 C 56.1 128 52.7 146 51.1 166 L 50.5 250 L -19 250 L -19.4 170 C -22 152 -30.6 134 -33.4 116 C -35 104 -34.4 92 -31.8 84 C -29.6 77.4 -25.4 74 -21.2 74.8 C -17.2 75.6 -14.6 79.4 -14.2 84.4 C -13.8 88 -12.4 90.8 -11.2 91.4 L -10 1 Z',
  thumb: 'M -19.4 170 C -22 152 -30.6 134 -33.4 116 C -35 104 -34.4 92 -31.8 84 C -29.6 77.4 -25.4 74 -21.2 74.8 C -17.2 75.6 -14.6 79.4 -14.2 84.4 C -13.4 96 -11.6 110 -7.6 126 C -5.8 140 -8.6 158 -19.4 170 Z',
  knuckles: 'M 10.9 51 C 11.6 45.6 15.6 42.6 20.6 42.6 C 25.8 42.6 29.8 46 30.3 51.4 C 30.5 56 29.8 60 28.6 63 L 12 66 Z M 30.3 51.4 C 32.2 48.8 35.2 47.8 38.4 48 C 43.6 48.4 47.2 52.2 47.3 57.6 C 47.3 62 46.6 66 45.4 69 L 29 64 Z M 47.3 57.6 C 48.9 56.2 51.1 55.6 53.3 56 C 57.9 56.8 60.9 60.6 60.9 66 C 60.9 70 60.3 73.6 59.4 76.4 L 45.6 70 Z',
  nail: 'M -6.4 -1.2 C -6.4 -6.6 -3.6 -8.9 0 -8.9 C 3.6 -8.9 6.4 -6.6 6.4 -1.2 L 6.1 10.4 C 6 12.6 3.6 14 0 14 C -3.6 14 -6 12.6 -6.1 10.4 Z',
  nailEdge: 'M -6.3 -1.8 C -6 -6.6 -3.4 -8.9 0 -8.9 C 3.4 -8.9 6 -6.6 6.3 -1.8 C 3.8 -3.6 -3.8 -3.6 -6.3 -1.8 Z',
  nailShine: 'M -3.2 1 Q -3.4 5 -2.2 9',
  thumbNail: 'M -30.6 88 C -30.2 82 -27 78.4 -23.4 78.6 C -20.4 78.8 -18.2 81.4 -18.2 85 C -20.2 88.6 -27 90.6 -30.6 88 Z',
  seams: 'M 10.9 51 C 11.3 57 11.5 62 11.4 67 M 30.3 51.4 C 30.5 55 30.1 58.6 29.4 61.4 M 47.3 57.6 C 47.4 61 47 64.4 46.2 67 M -14.2 84.4 C -13.4 96 -11.6 110 -8 124',
  creases: 'M -4.4 22.6 Q 0 24.8 4.4 22.6 M -6.4 45.4 Q 0 48.6 6.4 45.4 M -5.4 49.6 Q 0 52.2 5.4 49.6 M -3.6 53.4 Q 0 54.8 3.6 53.4 M 15.6 52.4 Q 20.4 54.4 25.2 52.4 M 34 56.4 Q 38.4 58.2 42.8 56.4 M 50 63.6 Q 53.6 65 57.2 63.6',
};

let uid = 0;
// One hand as an SVG element (absolute, covers its parent); update with setHand().
export function handSvg(w, hgt) {
  const id = 'hand' + uid++;
  const svg = `<svg class="hand" width="${w}" height="${hgt}" viewBox="0 0 ${w} ${hgt}" style="position:absolute;left:0;top:0;overflow:visible;pointer-events:none">
    <defs>
      <linearGradient id="${id}s" gradientUnits="userSpaceOnUse" x1="-30" y1="-10" x2="60" y2="170"><stop offset="0" stop-color="#F8D2B0"/><stop offset="1" stop-color="#E3A77E"/></linearGradient>
      <linearGradient id="${id}t" gradientUnits="userSpaceOnUse" x1="-40" y1="80" x2="-10" y2="120"><stop offset="0" stop-color="#E9B089"/><stop offset="1" stop-color="#F2C29D"/></linearGradient>
      <linearGradient id="${id}k" gradientUnits="userSpaceOnUse" x1="0" y1="42" x2="0" y2="72"><stop offset="0" stop-color="#F6CDAA"/><stop offset="1" stop-color="#E6AD84" stop-opacity="0"/></linearGradient>
      <linearGradient id="${id}d" gradientUnits="userSpaceOnUse" x1="-40" y1="0" x2="62" y2="0"><stop offset="0" stop-color="#8A4F2E" stop-opacity=".18"/><stop offset=".25" stop-color="#8A4F2E" stop-opacity="0"/><stop offset=".7" stop-color="#8A4F2E" stop-opacity="0"/><stop offset="1" stop-color="#8A4F2E" stop-opacity=".22"/></linearGradient>
      <linearGradient id="${id}n" gradientUnits="userSpaceOnUse" x1="0" y1="-9" x2="0" y2="14"><stop offset="0" stop-color="#FCEAE0"/><stop offset="1" stop-color="#F0C3B0"/></linearGradient>
    </defs>
    <g class="rings"></g>
    <g class="hand-g">
      <g class="shadow"><path d="${P.outline}" fill="#000" fill-opacity=".10" stroke="#000" stroke-opacity=".06" stroke-width="5" stroke-linejoin="round"/>
        <path d="${P.outline}" fill="none" stroke="#000" stroke-opacity=".035" stroke-width="10" stroke-linejoin="round"/></g>
      <path d="${P.outline}" fill="url(#${id}s)"/><path d="${P.thumb}" fill="url(#${id}t)"/><path d="${P.knuckles}" fill="url(#${id}k)"/>
      <path d="${P.outline}" fill="url(#${id}d)"/>
      <path d="${P.seams}" fill="none" stroke="#A5643F" stroke-opacity=".45" stroke-width="1.1" stroke-linecap="round"/>
      <path d="${P.creases}" fill="none" stroke="#A5643F" stroke-opacity=".42" stroke-width="1" stroke-linecap="round"/>
      <ellipse cx="1" cy="94" rx="7" ry="4" fill="#fff" fill-opacity=".22"/>
      <path d="${P.nail}" fill="url(#${id}n)"/><path d="${P.nail}" fill="none" stroke="#A5643F" stroke-opacity=".35" stroke-width=".8"/>
      <path d="${P.nailEdge}" fill="#fff" fill-opacity=".55"/><path d="${P.nailShine}" fill="none" stroke="#fff" stroke-opacity=".7" stroke-width="1.3" stroke-linecap="round"/>
      <path d="${P.thumbNail}" fill="url(#${id}n)"/><path d="${P.thumbNail}" fill="none" stroke="#A5643F" stroke-opacity=".35" stroke-width=".8"/>
      <path d="${P.outline}" fill="none" stroke="#A5643F" stroke-opacity=".75" stroke-width="1.2" stroke-linejoin="round"/>
    </g></svg>`;
  return svg;
}

// pose: { x, y (fingertip, px in the svg), alpha, press 0..1, tilt deg (-28 for a right bar),
//         fingerWidth px, mirror, ripple 0..1 (tap ring progress), rippleAt [x,y], hold 0..1 }
export function setHand(svgEl, pose) {
  const g = svgEl.querySelector('.hand-g');
  const rings = svgEl.querySelector('.rings');
  const a = clamp(pose.alpha ?? 1);
  const s = ((pose.fingerWidth ?? 40) / 20) * lerp(1.06, 0.97, clamp(pose.press ?? 0));
  const tilt = pose.tilt ?? -28;
  const mx = pose.mirror ? -1 : 1;
  g.setAttribute('transform', `translate(${pose.x} ${pose.y}) rotate(${tilt}) scale(${s * mx} ${s})`);
  g.setAttribute('opacity', a);
  const press = clamp(pose.press ?? 0);
  const sh = g.querySelector('.shadow');
  sh.setAttribute('transform', `translate(${lerp(3, 1, press) * mx} ${lerp(8, 2.5, press)})`);
  // Touch rings, in svg px (the demo draws them in dp; the caller passes its own scale `k`).
  const k = pose.ringScale ?? 1;
  let html = '';
  if (pose.ripple > 0 && pose.ripple < 1) {
    const [rx, ry] = pose.rippleAt || [pose.x, pose.y];
    const r = lerp(10, 34, pose.ripple) * k, fade = 1 - pose.ripple;
    html += `<circle cx="${rx}" cy="${ry}" r="${r}" fill="#fff" fill-opacity="${0.22 * fade * a}"/>`;
    html += `<circle cx="${rx}" cy="${ry}" r="${r}" fill="none" stroke="#fff" stroke-opacity="${0.75 * fade * a}" stroke-width="${2 * k}"/>`;
  }
  if (pose.hold > 0) {
    const grown = lerp(12, 26, pose.hold) * k, pulse = (pose.hold * 3) % 1, arc = grown + 5 * k;
    html += `<circle cx="${pose.x}" cy="${pose.y}" r="${grown}" fill="#fff" fill-opacity="${0.22 * a}"/>`;
    html += `<circle cx="${pose.x}" cy="${pose.y}" r="${grown + pulse * 16 * k}" fill="none" stroke="#fff" stroke-opacity="${0.5 * (1 - pulse) * a}" stroke-width="${1.5 * k}"/>`;
    const sweep = Math.min(359.9, 360 * pose.hold) * Math.PI / 180;
    const x1 = pose.x, y1 = pose.y - arc, x2 = pose.x + arc * Math.sin(sweep), y2 = pose.y - arc * Math.cos(sweep);
    html += `<path d="M ${x1} ${y1} A ${arc} ${arc} 0 ${sweep > Math.PI ? 1 : 0} 1 ${x2} ${y2}" fill="none" stroke="#fff" stroke-opacity="${0.95 * a}" stroke-width="${3 * k}" stroke-linecap="round"/>`;
  }
  if (press > 0) html += `<circle cx="${pose.x}" cy="${pose.y}" r="${11 * k * (0.6 + 0.4 * press)}" fill="#fff" fill-opacity="${0.35 * press * a}"/>`;
  rings.innerHTML = html;
}
