// The stage the phone stands on in the store art and videos: a moving backdrop, the finger
// that shows each touch, captions, and the cue sheet the sound is mixed from.
import { h, css, clamp, lerp, seg, Ease, rng } from './core.js';

export const BRAND = {
  violet: '#AA7BFF', indigo: '#2C209A', primary: '#4F46E5', primaryLight: '#818CF8',
  teal: '#2DD4BF', night: '#100B33', ink: '#F5F3FF', lavender: '#C4B5FD', rose: '#FB7185',
};

// --- Backdrop ----------------------------------------------------------------------------------
// Glows drifting slowly over a deep gradient; `light` gives the pale version.
export class Backdrop {
  // Drawn at a quarter of the size and scaled up: soft glows lose nothing, and a frame costs a
  // sixteenth. The grain is a fixed overlay the compositor lays on top.
  constructor(w, hgt, { light = false, seed = 3, glows = null, div = 4 } = {}) {
    this.w = w; this.h = hgt; this.light = light; this.div = div;
    this.el = h(`<div class="backdrop" style="position:absolute;left:0;top:0;width:${w}px;height:${hgt}px;overflow:hidden"></div>`);
    this.canvas = document.createElement('canvas');
    this.canvas.width = Math.ceil(w / div); this.canvas.height = Math.ceil(hgt / div);
    css(this.canvas, { position: 'absolute', left: 0, top: 0, width: w + 'px', height: hgt + 'px' });
    this.el.appendChild(this.canvas);
    this.el.appendChild(h(`<div style="position:absolute;inset:0;background-image:url(${grainUrl()});opacity:${light ? 0.5 : 0.7}"></div>`));
    const r = rng(seed);
    this.glows = (glows || (light
      ? [['#C4B5FD', 0.55], ['#99F6E4', 0.45], ['#A5B4FC', 0.5], ['#FBCFE8', 0.35]]
      : [['#7C5CFF', 0.55], ['#2DD4BF', 0.22], ['#4F46E5', 0.5], ['#AA7BFF', 0.35]]
    )).map(([c, a]) => ({ c, a, x: r(), y: r(), r: 0.35 + r() * 0.35, sx: (r() - 0.5) * 0.06, sy: (r() - 0.5) * 0.05, ph: r() * 6.28 }));
  }

  draw(t = 0) {
    const w = this.canvas.width, H = this.canvas.height;
    const ctx = this.canvas.getContext('2d');
    const g = ctx.createLinearGradient(0, 0, w * 0.4, H);
    if (this.light) { g.addColorStop(0, '#F5F3FF'); g.addColorStop(1, '#E0E7FF'); }
    else { g.addColorStop(0, '#1A1150'); g.addColorStop(1, '#0B0826'); }
    ctx.globalCompositeOperation = 'source-over';
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, w, H);
    ctx.globalCompositeOperation = this.light ? 'multiply' : 'screen';
    for (const b of this.glows) {
      const x = (b.x + Math.sin(t * 0.25 + b.ph) * 0.06 + b.sx * t * 0.1) * w;
      const y = (b.y + Math.cos(t * 0.2 + b.ph) * 0.05 + b.sy * t * 0.1) * H;
      const R = b.r * Math.max(w, H);
      const rg = ctx.createRadialGradient(x, y, 0, x, y, R);
      rg.addColorStop(0, hexA(b.c, b.a));
      rg.addColorStop(0.5, hexA(b.c, b.a * 0.35));
      rg.addColorStop(1, hexA(b.c, 0));
      ctx.fillStyle = rg;
      ctx.fillRect(0, 0, w, H);
    }
    ctx.globalCompositeOperation = 'source-over';
  }
}

let grain = null;
function grainUrl() {
  if (grain) return grain;
  const gc = document.createElement('canvas');
  gc.width = 256; gc.height = 256;
  const gctx = gc.getContext('2d');
  const img = gctx.createImageData(256, 256);
  const r = rng(11);
  for (let i = 0; i < img.data.length; i += 4) {
    const v = r() * 255;
    img.data[i] = img.data[i + 1] = img.data[i + 2] = v;
    img.data[i + 3] = 12;
  }
  gctx.putImageData(img, 0, 0);
  grain = gc.toDataURL('image/png');
  return grain;
}

function hexA(hex, a) {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
}

// --- Finger ------------------------------------------------------------------------------------
// A soft disc for the fingertip, pressed a little while down, with a ring that spreads on each
// tap. Positioned in the phone's own dp, inside phone.top, so it scales with the screen.
export class Finger {
  constructor(parent) {
    this.el = h(`<div class="finger" style="position:absolute;left:0;top:0;width:0;height:0;pointer-events:none;z-index:70">
        <div class="ring" style="position:absolute;border-radius:50%;border:2.5px solid rgba(255,255,255,.9)"></div>
        <div class="dot" style="position:absolute;width:46px;height:46px;left:-23px;top:-23px;border-radius:50%;
          background:radial-gradient(circle at 40% 35%,rgba(255,255,255,.75),rgba(255,255,255,.42));
          border:2px solid rgba(255,255,255,.95);box-shadow:0 6px 18px rgba(0,0,0,.28),0 0 0 1px rgba(0,0,0,.06)"></div>
      </div>`);
    parent.appendChild(this.el);
    this.dot = this.el.querySelector('.dot');
    this.ring = this.el.querySelector('.ring');
  }

  // state: { x, y, visible (0..1), down (0..1), ring (0..1 progress of the last tap's ring) }
  set({ x = 0, y = 0, visible = 1, down = 0, ring = -1 }) {
    this.el.style.transform = `translate(${x}px,${y}px)`;
    this.el.style.opacity = visible;
    this.dot.style.transform = `scale(${lerp(1, 0.82, down)})`;
    this.dot.style.opacity = lerp(0.9, 1, down);
    if (ring >= 0 && ring < 1) {
      const d = lerp(30, 92, Ease.outCubic(ring));
      css(this.ring, { width: d, height: d, left: -d / 2, top: -d / 2, opacity: (1 - ring) * 0.8, display: 'block' });
    } else this.ring.style.display = 'none';
  }
}

// A script for the finger: a list of moves and taps on a timeline, evaluated at t.
//   { at, tap: [x, y] }                       a tap (press 0.12 s)
//   { at, press: [x, y], hold: 0.6 }          a long press
//   { at, drag: [[x0,y0],[x1,y1],...], dur }  a drag through points (eased)
//   { at, move: [x, y], dur }                 move while lifted
//   { at, hide: true } / { at, show: [x,y] }
export class FingerScript {
  constructor(steps) {
    this.steps = steps.slice().sort((a, b) => a.at - b.at);
  }

  state(t) {
    let x = 0, y = 0, visible = 0, down = 0, ring = -1;
    let lastPos = null;
    for (const s of this.steps) {
      if (s.at > t) {
        // Glide toward the next step's start while lifted, for the 0.35 s before it.
        const next = startPos(s);
        if (lastPos && next && visible > 0 && down === 0) {
          const k = Ease.fastOutSlowIn(clamp((t - (s.at - (s.lead ?? 0.4))) / (s.lead ?? 0.4)));
          x = lerp(lastPos[0], next[0], k); y = lerp(lastPos[1], next[1], k);
        } else if (next && visible === 0) {
          [x, y] = next;
          visible = clamp(1 - (s.at - t) / 0.25);
        }
        break;
      }
      const dt = t - s.at;
      if (s.show) { [x, y] = s.show; visible = clamp(dt / 0.25); lastPos = s.show; }
      else if (s.hide) { visible = 1 - clamp(dt / 0.25); down = 0; }
      else if (s.tap) {
        [x, y] = s.tap; visible = 1; lastPos = s.tap;
        down = dt < 0.06 ? dt / 0.06 : dt < 0.16 ? 1 : Math.max(0, 1 - (dt - 0.16) / 0.1);
        ring = dt < 0.5 ? dt / 0.5 : -1;
      } else if (s.press) {
        [x, y] = s.press; visible = 1; lastPos = s.press;
        const hold = s.hold ?? 0.6;
        down = dt < 0.06 ? dt / 0.06 : dt < hold ? 1 : Math.max(0, 1 - (dt - hold) / 0.12);
        ring = dt > hold * 0.6 && dt < hold * 0.6 + 0.5 ? (dt - hold * 0.6) / 0.5 : -1;
      } else if (s.drag) {
        const pts = s.drag;
        const dur = s.dur ?? 0.6;
        const k = (s.ease ?? Ease.inOut)(clamp(dt / dur));
        [x, y] = along(pts, k);
        visible = 1; lastPos = [x, y];
        const lift = s.lift ?? true;
        down = dt < 0.06 ? dt / 0.06 : dt < dur + 0.05 || !lift ? 1 : Math.max(0, 1 - (dt - dur - 0.05) / 0.12);
      } else if (s.move) {
        const from = lastPos || s.move;
        const k = Ease.fastOutSlowIn(clamp(dt / (s.dur ?? 0.4)));
        x = lerp(from[0], s.move[0], k); y = lerp(from[1], s.move[1], k);
        visible = 1; down = 0;
        if (k >= 1) lastPos = s.move;
      }
    }
    return { x, y, visible, down, ring };
  }
}

function startPos(s) {
  return s.tap || s.press || (s.drag && s.drag[0]) || s.show || s.move || null;
}

// A point a fraction k along a polyline.
export function along(pts, k) {
  if (pts.length === 1) return pts[0];
  const lens = [];
  let total = 0;
  for (let i = 1; i < pts.length; i++) {
    const l = Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]);
    lens.push(l); total += l;
  }
  let d = k * total;
  for (let i = 0; i < lens.length; i++) {
    if (d <= lens[i] || i === lens.length - 1) {
      const f = lens[i] ? clamp(d / lens[i]) : 1;
      return [lerp(pts[i][0], pts[i + 1][0], f), lerp(pts[i][1], pts[i + 1][1], f)];
    }
    d -= lens[i];
  }
  return pts[pts.length - 1];
}

// --- Captions ----------------------------------------------------------------------------------
// Text that rises into place and fades out; words are wrapped in spans so lines can be balanced.
export function caption(text, { size = 64, weight = 800, color = BRAND.ink, font = "'Plus Jakarta Sans'", lh = 1.08, ls = -0.02, align = 'left', maxWidth = null } = {}) {
  return h(`<div class="caption" style="font:${weight} ${size}px/${lh} ${font};letter-spacing:${ls}em;color:${color};text-align:${align};
      ${maxWidth ? `max-width:${maxWidth}px;` : ''}text-wrap:balance">${text}</div>`);
}

// Shows `el` between a and b: rises `rise` px and fades in, then fades out.
export function showBetween(el, t, a, b, { rise = 24, fin = 0.45, fout = 0.3, scaleIn = 1 } = {}) {
  const pin = seg(t, a, fin, Ease.emphasizedDecel);
  const pout = seg(t, b - fout, fout, Ease.fastOutLinearIn);
  const vis = t >= a && t <= b ? Math.min(pin, 1 - pout) : 0;
  el.style.opacity = vis;
  const y = (1 - pin) * rise - pout * rise * 0.5;
  const s = lerp(scaleIn, 1, pin);
  el.style.transform = `translateY(${y}px) scale(${s})`;
  el.style.visibility = vis > 0.001 ? 'visible' : 'hidden';
  return vis;
}

// --- Sound cues --------------------------------------------------------------------------------
// Scenes list their sounds as they script their touches; render.mjs writes them beside the video.
export class Cues {
  constructor() { this.list = []; }
  add(t, sfx, gain = 1, pan = 0) { this.list.push({ t: +t.toFixed(3), sfx, gain, pan }); return this; }
  fromFinger(script, { tap = 'tap', press = 'press', drag = null } = {}) {
    for (const s of script.steps) {
      if (s.tap && tap) this.add(s.at + 0.02, tap, 0.8);
      if (s.press && press) this.add(s.at + (s.hold ?? 0.6) * 0.6, press, 0.7);
      if (s.drag && drag) this.add(s.at + 0.02, drag, 0.5);
    }
    return this;
  }
}
