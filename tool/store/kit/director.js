// Timeline tools for the videos: the hand that performs each gesture (timed like the app's own
// GestureDemo), values that step and glide the way the Quick panel's do, typing, and a camera.
import { clamp, lerp, Ease, invLerp } from './core.js';

const press = (dt, hold) => (dt < 0 ? 0 : dt < 0.11 ? Ease.fastOutSlowIn(dt / 0.11) : dt < hold ? 1 : Math.max(0, 1 - (dt - hold) / 0.17));

// The hand. Steps, by time (s), in the phone's dp:
//   { at, enter: [x, y] }                fades in (240 ms) gliding in from below-right
//   { at, tap: [x, y] }                  press 110 ms, ripple 520 ms, released after 180 ms
//   { at, hold: [x, y], dur }            press, ring fills over 950 ms, released after dur (1.33 s)
//   { at, swipe: [x0, y0], to: [x1, y1], dur }   press, slide (FastOutSlowIn), 90 ms, release
//   { at, drag: [[x, y], ...], dur, keep }  pressed along a path; keep = stay down at the end
//   { at, leave: true }                  fades out (280 ms), drifting back toward where it came from
// Between steps the hand travels to the next start in `lead` s (default .38) before it.
export class HandScript {
  constructor(steps) {
    this.steps = steps.slice().sort((a, b) => a.at - b.at);
  }

  start(s) { return s.tap || s.hold || s.swipe || (s.drag && s.drag[0]) || s.enter || null; }
  end(s, prev) { return s.lift || s.leave ? prev : s.to || (s.drag && s.drag[s.drag.length - 1]) || this.start(s); }
  span(s) {
    if (s.tap) return 0.36;
    if (s.hold) return (s.dur ?? 1.33) + 0.17;
    if (s.swipe) return 0.11 + (s.dur ?? 0.62) + 0.09 + 0.17;
    if (s.drag) return 0.11 + (s.dur ?? 0.62) + (s.keep ? 0 : 0.26);
    if (s.enter) return 0.24;
    if (s.leave) return 0.28;
    if (s.lift) return 0.17;
    return 0;
  }

  // { x, y, alpha, press, ripple, rippleAt, hold, down } at time t
  state(t) {
    let pos = null, alpha = 0, pr = 0, ripple = -1, rippleAt = null, hold = 0, side = 'right', tilt = null, fw = null;
    let prevEnd = null, prevEndT = -Infinity, visible = false;
    for (let i = 0; i < this.steps.length; i++) {
      const s = this.steps[i];
      const dt = t - s.at;
      if (dt < 0) {
        // Travelling toward this step from the previous one.
        const target = this.start(s);
        if (visible && prevEnd && target) {
          const lead = s.lead ?? 0.38;
          const k = Ease.fastOutSlowIn(clamp((t - Math.max(prevEndT, s.at - lead)) / Math.max(0.05, s.at - Math.max(prevEndT, s.at - lead))));
          pos = [lerp(prevEnd[0], target[0], k), lerp(prevEnd[1], target[1], k)];
        }
        break;
      }
      if (s.enter) {
        side = s.side || 'right'; tilt = s.tilt ?? null; fw = s.fw ?? null;
        const k = Ease.fastOutSlowIn(clamp(dt / 0.42));
        const from = [s.enter[0] + (side === 'left' ? -90 : 90), s.enter[1] + 220];
        pos = [lerp(from[0], s.enter[0], k), lerp(from[1], s.enter[1], k)];
        alpha = clamp(dt / 0.24); visible = true; pr = 0;
      } else if (s.leave) {
        const k = clamp(dt / 0.28);
        const p0 = pos || prevEnd || [0, 0];
        pos = [lerp(p0[0], p0[0] + (side === 'left' ? -54 : 54), Ease.fastOutSlowIn(k)), lerp(p0[1], p0[1] + 130, Ease.fastOutSlowIn(k))];
        alpha = 1 - k; visible = k < 1; pr = 0;
      } else if (s.tap) {
        pos = s.tap; alpha = 1; visible = true;
        pr = press(dt, 0.18);
        if (dt < 0.52) { ripple = Ease.linearOutSlowIn(dt / 0.52); rippleAt = s.tap; }
      } else if (s.hold) {
        pos = s.hold; alpha = 1; visible = true;
        const dur = s.dur ?? 1.33;
        pr = press(dt, dur);
        hold = dt < dur ? clamp((dt - 0.05) / 0.95) : 0;
      } else if (s.swipe) {
        const dur = s.dur ?? 0.62;
        const k = (s.ease || Ease.fastOutSlowIn)(clamp((dt - 0.11) / dur));
        pos = [lerp(s.swipe[0], s.to[0], k), lerp(s.swipe[1], s.to[1], k)];
        alpha = 1; visible = true;
        pr = press(dt, 0.11 + dur + 0.09);
      } else if (s.lift) {
        pos = prevEnd || pos; alpha = 1; visible = true;
        pr = 1 - clamp(dt / 0.17);
      } else if (s.drag) {
        // holdFor: stay put that long first, with the long-press ring filling, then move.
        const dur = s.dur ?? 0.62, still = s.holdFor ?? 0;
        const k = (s.ease || Ease.inOut)(clamp((dt - 0.11 - still) / Math.max(0.01, dur - still)));
        pos = along(s.drag, k);
        alpha = 1; visible = true;
        pr = s.keep ? (dt < 0.11 ? dt / 0.11 : 1) : press(dt, 0.11 + dur + 0.09);
        if (still && dt < still + 0.11) hold = clamp((dt - 0.05) / Math.min(0.95, still));
      }
      prevEnd = s.leave ? null : this.end(s, prevEnd);
      prevEndT = s.at + this.span(s);
      if (s.leave && dt >= 0.28) { pos = null; alpha = 0; visible = false; }
    }
    if (!pos) return { x: -999, y: -999, alpha: 0, press: 0, ripple: -1, hold: 0, side, tilt, fw };
    return { x: pos[0], y: pos[1], alpha, press: pr, ripple, rippleAt, hold, down: pr > 0.5, side, tilt, fw };
  }

  // Sound cues for the steps: taps, holds and swipes.
  cues() {
    const out = [];
    for (const s of this.steps) {
      if (s.tap) out.push({ t: s.at + 0.03, sfx: s.sound ?? 'tap', gain: 0.8 });
      if (s.hold) out.push({ t: s.at + 0.45, sfx: 'press', gain: 0.7 });
      if (s.swipe) out.push({ t: s.at + 0.12, sfx: s.sound ?? 'swipe', gain: 0.5 });
      if (s.drag && s.holdFor) out.push({ t: s.at + 0.45, sfx: 'press', gain: 0.7 });
    }
    return out.filter((c) => c.sfx);
  }
}

export function along(pts, k) {
  if (pts.length === 1) return pts[0];
  const lens = [];
  let total = 0;
  for (let i = 1; i < pts.length; i++) { const l = Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]); lens.push(l); total += l; }
  let d = k * total;
  for (let i = 0; i < lens.length; i++) {
    if (d <= lens[i] || i === lens.length - 1) { const f = lens[i] ? clamp(d / lens[i]) : 1; return [lerp(pts[i][0], pts[i + 1][0], f), lerp(pts[i][1], pts[i + 1][1], f)]; }
    d -= lens[i];
  }
  return pts[pts.length - 1];
}

// A value that steps: each event { at, v } glides from where the value was to v over 130 ms,
// DecelerateInterpolator(1.6), as QuickSliderView.animateValue does. { at, v, instant } jumps.
export function stepped(t, events, start = 0) {
  let v = start;
  for (const e of events) {
    if (t < e.at) break;
    const from = v;
    const k = e.instant ? 1 : 1 - Math.pow(1 - clamp((t - e.at) / (e.glide ?? 0.13)), 3.2);
    v = lerp(from, e.v, k);
  }
  return v;
}

// Volume steps of a 15-step stream, as the number shows them.
export const VOLUME_STEPS = Array.from({ length: 16 }, (_, i) => i / 15);

// The text typed so far: chars from `at`, one every `every` seconds.
export function typed(t, text, at, every = 0.13) {
  if (t < at) return '';
  return text.slice(0, Math.min(text.length, Math.floor((t - at) / every) + 1));
}
export const typingCues = (text, at, every = 0.13, gain = 0.32) => [...text].map((_, i) => ({ t: at + i * every, sfx: 'tap', gain }));

// The camera: keyframes { at, cx, cy, s } — phone point (cx, cy) placed at the frame's focus at
// scale s — eased between with an in-out curve over each key's `dur` (default .7 s) before it.
export class Camera {
  constructor(keys) { this.keys = keys; }
  at(t) {
    let cur = this.keys[0];
    for (let i = 1; i < this.keys.length; i++) {
      const k = this.keys[i];
      const dur = k.dur ?? 0.7;
      if (t < k.at - dur) break;
      const p = Ease.inOut(clamp((t - (k.at - dur)) / dur));
      cur = { cx: lerp(cur.cx, k.cx, p), cy: lerp(cur.cy, k.cy, p), s: lerp(cur.s, k.s, p) };
      if (p < 1) break;
    }
    return cur;
  }
}

// 0..1 for an animation that starts at `at` and lasts `dur` (linear), clamped.
export const prog = (t, at, dur) => clamp((t - at) / dur);
export const between = (t, a, b) => t >= a && t < b;

// The step times of a stroke along y (a swipe step's from/to, start and duration, with the hand's
// own easing): the first as it crosses the 8 dp touch slop, then one per further 32 dp
// (HandlerGestureDetector: max(150 dp / steps, 32 dp)).
export function strokeSteps(at, y0, y1, dur, ease = Ease.fastOutSlowIn, { slop = 8, per = 32 } = {}) {
  const out = [];
  let n = 0;
  for (let t = at; t <= at + 0.11 + dur + 0.004; t += 0.002) {
    const y = lerp(y0, y1, ease(clamp((t - at - 0.11) / dur)));
    const travel = Math.abs(y - y0);
    const steps = travel >= slop ? 1 + Math.floor((travel - slop) / per) : 0;
    while (steps > n) { n++; out.push(t); }
  }
  return out;
}
