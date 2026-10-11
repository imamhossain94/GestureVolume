// tour-scenes.js
// A line-by-line Canvas2D port of the app's walkthrough illustrations, for HTML recreation:
//   ui/screens/walkthrough/WalkthroughIllustration.kt  (scenes, StylePreview)
//   ui/components/DeviceArt.kt                          (the phone, wallpaper)
//   ui/components/PointingHand.kt                       (the hand)
//   ui/components/HandleOutline.kt + utils/HandlerShape.kt (the Dock tab outline)
//   ui/components/StageBackdrop.kt                      (the card's backdrop)
// Same names, same numbers, same order of drawing. Units are the scene's own (280 x 200).
//
// Usage (browser):
//   drawWalkScene(ctx, 'Intro', t, widthPx, heightPx)    // t in [0,1) = position in the loop
//   loop length: SCENES.Intro.cycleMs etc.; t = (ms % cycleMs) / cycleMs  (linear clock)
//   drawStageBackdrop(ctx, widthPx, heightPx, dark)
//   drawStylePreview(ctx, advanced, selected, bob, dpPx) // 64 x 88 dp canvas, bob in [-1,1]
//
// Fidelity notes:
// - Easing: FastOutSlowIn = cubic-bezier(.4,0,.2,1); Pop = cubic-bezier(.34,1.56,.64,1) (overshoots).
// - Color lerp is in Oklab, as Compose's lerp(Color, Color, f) is (switch track colour).
// - Gradients: canvas interpolates unpremultiplied, like Android. (CSS gradients do not.)
// - The hand, when alpha < 0.99, is drawn into a layer and composited at alpha (as saveLayer).

export const SCENES = {
  Intro: { cycleMs: 3600 },
  Simple: { cycleMs: 6600 },
  QuickSlider: { cycleMs: 4400 },
  Deck: { cycleMs: 4200 },
  LongPress: { cycleMs: 4400 },
  MoveButton: { cycleMs: 6000 },
  MoveTab: { cycleMs: 6000 },
  Permission: { cycleMs: 3600 },
  Notifications: { cycleMs: 5200 },
  Accessibility: { cycleMs: 5200 },
};
export const PERMISSION_SETTLED = 0.75;
export const NOTIFICATIONS_SETTLED = 0.75;
export const ACCESSIBILITY_SETTLED = 0.74;

// ---- DeviceArt -------------------------------------------------------------------------------
export const DeviceArt = {
  Frame: '#17171C',
  WallTop: '#D9CCFF',
  WallBottom: '#9DB2FA',
  SCENE_W: 280, SCENE_H: 200,
  SCREEN_L: 46, SCREEN_T: 20, SCREEN_R: 234, SCREEN_B: 236, SCREEN_CORNER: 24,
  SCREEN_W: 188,
  FINGER_W: 24,
  HAND_TILT: -28,
  FINGER_REST: { x: 266, y: 206 },
  scale: (w, h) => Math.min(w / 280, h / 200),
  origin: (w, h, s) => ({ x: (w - 280 * s) / 2, y: h - 200 * s }),
};
const { SCREEN_L, SCREEN_T, SCREEN_R, SCREEN_B } = DeviceArt;

// ---- constants (WalkthroughIllustration.kt) ----------------------------------------------------
const BAR_CY = 108;
const PILL_W = 18, PILL_H = 60;
const PILL_CX = SCREEN_R - 4 - PILL_W / 2;            // 221
const TAB_W = 9, TAB_H = 56, TAB_SWEEP = 10;
const DECK_W = 32, DECK_PAD = 5, DECK_GAP = 4, DECK_H = 141;
const DECK_TOP = BAR_CY - DECK_H / 2;                  // 37.5
const DECK_RIGHT = 219;
const PILL_CX_LEFT = SCREEN_L + 4 + PILL_W / 2;        // 59
const DRAG_CUE_ALPHA = 0.65;
const MOVE_DOWN = 44, MOVE_DROP_X = 88;
const NOTIF_ALLOW_Y = 116;
const FINGER_W = DeviceArt.FINGER_W;
const PULSE_MS = 900;
const DOCK_FLARE = 0.29;                               // HandlerPresets.DEFAULT.flare

const Indigo = [0x4f, 0x46, 0xe5, 1];
const SimpleFill = [0x4f, 0x46, 0xe5, 0.5];
const TabColour = [0x05, 0x05, 0x07, 1];
const FrameColour = [0x17, 0x17, 0x1c, 1];
const DeckPanel = [0x1d, 0x1b, 0x2b, 0xe6 / 255];
const MenuPanel = [0xff, 0xff, 0xff, 0xf7 / 255];
const MenuTile = [0xee, 0xf0, 0xff, 1];
const TextLine = [0xcb, 0xc7, 0xd8, 1];
const SwitchOff = [0xb9, 0xb5, 0xc6, 1];
const White = [255, 255, 255, 1];
const Black = [0, 0, 0, 1];
const TileColours = [
  [0x60, 0xa5, 0xfa, 1], [0x34, 0xd3, 0x99, 1], [0xfb, 0xbf, 0x24, 1],
  [0xf4, 0x72, 0xb6, 1], [0xa7, 0x8b, 0xfa, 1], [0xf8, 0x71, 0x71, 1],
  [0x22, 0xd3, 0xee, 1], [0x4f, 0x46, 0xe5, 1], [0xfb, 0x92, 0x3c, 1],
];
const FingerRest = DeviceArt.FINGER_REST;

// ---- easing -----------------------------------------------------------------------------------
export function cubicBezier(x1, y1, x2, y2) {
  const cx = 3 * x1, bx = 3 * (x2 - x1) - cx, ax = 1 - cx - bx;
  const cy = 3 * y1, by = 3 * (y2 - y1) - cy, ay = 1 - cy - by;
  const X = (s) => ((ax * s + bx) * s + cx) * s;
  const Y = (s) => ((ay * s + by) * s + cy) * s;
  const dX = (s) => (3 * ax * s + 2 * bx) * s + cx;
  return (x) => {
    if (x <= 0 || x >= 1) return x;
    let s = x;
    for (let i = 0; i < 8; i++) {
      const e = X(s) - x;
      if (Math.abs(e) < 1e-7) return Y(s);
      const d = dX(s);
      if (Math.abs(d) < 1e-6) break;
      s -= e / d;
    }
    let lo = 0, hi = 1;
    s = x;
    for (let i = 0; i < 60; i++) {
      const v = X(s);
      if (Math.abs(v - x) < 1e-7) break;
      if (x > v) lo = s; else hi = s;
      s = (lo + hi) / 2;
    }
    return Y(s);
  };
}
export const Smooth = cubicBezier(0.4, 0, 0.2, 1);   // FastOutSlowInEasing
export const Pop = cubicBezier(0.34, 1.56, 0.64, 1);
export const Linear = (x) => x;

const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
const span = (t, from, to, easing = Smooth) => easing(clamp((t - from) / (to - from), 0, 1));
const mix = (a, b, f) => a + (b - a) * f;
const mixP = (a, b, f) => ({ x: mix(a.x, b.x, f), y: mix(a.y, b.y, f) });
const P = (x, y) => ({ x, y });

// ---- colour -----------------------------------------------------------------------------------
const css = (c, a = 1) => `rgba(${c[0]},${c[1]},${c[2]},${clamp(c[3] * a, 0, 1)})`;
function toLinear(v) { v /= 255; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }
function fromLinear(v) { const s = v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055; return clamp(Math.round(s * 255), 0, 255); }
function toOklab(c) {
  const r = toLinear(c[0]), g = toLinear(c[1]), b = toLinear(c[2]);
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  return [0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
    1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
    0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s];
}
function fromOklab(L, A, B) {
  const l = Math.pow(L + 0.3963377774 * A + 0.2158037573 * B, 3);
  const m = Math.pow(L - 0.1055613458 * A - 0.0638541728 * B, 3);
  const s = Math.pow(L - 0.0894841775 * A - 1.291485548 * B, 3);
  return [fromLinear(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
    fromLinear(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
    fromLinear(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s)];
}
/** Compose's lerp(Color, Color, f): in Oklab, alpha linearly. */
export function lerpColor(a, b, f) {
  const p = toOklab(a), q = toOklab(b);
  const [r, g, bl] = fromOklab(mix(p[0], q[0], f), mix(p[1], q[1], f), mix(p[2], q[2], f));
  return [r, g, bl, mix(a[3], b[3], f)];
}

// ---- primitives (Compose DrawScope semantics) ---------------------------------------------------
function rr(ctx, c, x, y, w, h, r, alpha = 1, stroke = 0) {
  if (w <= 0 || h <= 0) return;
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, Math.max(0, r));
  if (stroke > 0) { ctx.lineWidth = stroke; ctx.strokeStyle = css(c, alpha); ctx.stroke(); }
  else { ctx.fillStyle = css(c, alpha); ctx.fill(); }
}
function circle(ctx, c, r, center, alpha = 1, stroke = 0) {
  if (r <= 0) return;
  ctx.beginPath();
  ctx.arc(center.x, center.y, r, 0, Math.PI * 2);
  if (stroke > 0) { ctx.lineWidth = stroke; ctx.strokeStyle = css(c, alpha); ctx.stroke(); }
  else { ctx.fillStyle = css(c, alpha); ctx.fill(); }
}
function line(ctx, c, a, b, width, alpha = 1) { // StrokeCap.Round
  ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y);
  ctx.lineWidth = width; ctx.lineCap = 'round'; ctx.strokeStyle = css(c, alpha); ctx.stroke(); ctx.lineCap = 'butt';
}
/** drawArc(useCenter = false, style = Stroke(w)) with butt caps. Angles in degrees, clockwise from +x. */
function arcStroke(ctx, c, startDeg, sweepDeg, cx, cy, r, width, alpha = 1, cap = 'butt') {
  ctx.beginPath();
  ctx.arc(cx, cy, r, startDeg * Math.PI / 180, (startDeg + sweepDeg) * Math.PI / 180, sweepDeg < 0);
  ctx.lineWidth = width; ctx.lineCap = cap; ctx.strokeStyle = css(c, alpha); ctx.stroke(); ctx.lineCap = 'butt';
}
function scaleAbout(ctx, sx, sy, px, py) { ctx.translate(px, py); ctx.scale(sx, sy); ctx.translate(-px, -py); }

const screenPath = () => { const p = new Path2D(); p.roundRect(SCREEN_L, SCREEN_T, SCREEN_R - SCREEN_L, SCREEN_B - SCREEN_T, DeviceArt.SCREEN_CORNER); return p; };
function clipScreen(ctx, body) { ctx.save(); ctx.clip(screenPath()); body(); ctx.restore(); }

// ---- the tab outline (HandlerShape.tabProfile / tabOutline, HandleOutline.setTabOutline) ----------
export function tabProfile(t) {
  const p = clamp(t, 0, 1);
  const cubic = p * p * (3 - 2 * p);
  const quintic = p * p * p * (p * (6 * p - 15) + 10);
  return cubic * (1 - p) + quintic * p;
}
export function tabOutline(width, height, flare, edgeOnLeft, steps = 40) {
  const n = Math.max(2, steps);
  const length = clamp(flare, 0.04, 0.5) * height;
  const edgeX = edgeOnLeft ? 0 : width;
  const innerX = edgeOnLeft ? width : 0;
  const spanX = innerX - edgeX;
  const pts = [];
  for (let s = 0; s <= n; s++) { const t = s / n; pts.push(edgeX + spanX * tabProfile(t), length * t); }
  pts.push(innerX, height - length);
  for (let s = n; s >= 0; s--) { const t = s / n; pts.push(edgeX + spanX * tabProfile(t), height - length * t); }
  return pts;
}
export function tabOutlinePath(edgeX, top, width, height, flare, edgeOnLeft) {
  const o = tabOutline(width, height, flare, edgeOnLeft);
  const left = edgeOnLeft ? edgeX : edgeX - width;
  const p = new Path2D();
  p.moveTo(left + o[0], top + o[1]);
  for (let i = 2; i < o.length; i += 2) p.lineTo(left + o[i], top + o[i + 1]);
  p.closePath();
  return p;
}
/** WalkthroughIllustration.tabPath: [height] of straight side plus a [sweep] each end is the total. */
function tabPath(edge, cy, width, height, sweep, edgeOnLeft = false) {
  const total = height + 2 * sweep;
  return tabOutlinePath(edge, cy - total / 2, width, total, DOCK_FLARE, edgeOnLeft);
}

// ---- the phone (DeviceArt.drawScenePhone) ------------------------------------------------------
export function drawDeviceWallpaper(ctx, x, y, w, h, corner = 0) {
  const g = ctx.createLinearGradient(x, y, x + w, y + h);
  g.addColorStop(0, DeviceArt.WallTop); g.addColorStop(1, DeviceArt.WallBottom);
  ctx.beginPath(); ctx.roundRect(x, y, w, h, corner); ctx.fillStyle = g; ctx.fill();
  const gx = x + w * 0.2, gy = y + h * 0.17, gr = Math.max(w, h) * 0.51;
  const rg = ctx.createRadialGradient(gx, gy, 0, gx, gy, gr);
  rg.addColorStop(0, 'rgba(255,255,255,0.4)'); rg.addColorStop(1, 'rgba(0,0,0,0)'); // Color.Transparent is black @ 0
  ctx.beginPath(); ctx.roundRect(x, y, w, h, corner); ctx.fillStyle = rg; ctx.fill();
}
export function drawScenePhone(ctx) {
  rr(ctx, Black, 37, 16, 206, 240, 33, 0.10);
  rr(ctx, FrameColour, 40, 14, 200, 240, 30);
  clipScreen(ctx, () => {
    drawDeviceWallpaper(ctx, SCREEN_L, SCREEN_T, SCREEN_R - SCREEN_L, SCREEN_B - SCREEN_T);
    const ink = [255, 255, 255, 0.85];
    rr(ctx, ink, 62, 29, 18, 6, 3);
    rr(ctx, ink, 198, 29, 9, 6, 2);
    rr(ctx, ink, 210, 29, 14, 6, 2);
    for (let i = 0; i < 6; i++) rr(ctx, White, 66 + (i % 3) * 36, 58 + Math.floor(i / 3) * 40, 24, 24, 8, 0.22);
  });
  circle(ctx, FrameColour, 4.5, P(140, 32));
}

// ---- the hand (PointingHand.kt) ----------------------------------------------------------------
const Hand = {
  outline: 'M -10 1 C -10 -6.5 -5.6 -11.2 0 -11.2 C 5.6 -11.2 10 -6.5 10 1 L 10.9 51 ' +
    'C 11.6 45.6 15.6 42.6 20.6 42.6 C 25.8 42.6 29.8 46 30.3 51.4 ' +
    'C 32.2 48.8 35.2 47.8 38.4 48 C 43.6 48.4 47.2 52.2 47.3 57.6 ' +
    'C 48.9 56.2 51.1 55.6 53.3 56 C 57.9 56.8 60.9 60.6 60.9 66 ' +
    'C 60.9 80 60.3 96 58.1 112 C 56.1 128 52.7 146 51.1 166 L 50.5 250 L -19 250 L -19.4 170 ' +
    'C -22 152 -30.6 134 -33.4 116 C -35 104 -34.4 92 -31.8 84 ' +
    'C -29.6 77.4 -25.4 74 -21.2 74.8 C -17.2 75.6 -14.6 79.4 -14.2 84.4 ' +
    'C -13.8 88 -12.4 90.8 -11.2 91.4 L -10 1 Z',
  thumb: 'M -19.4 170 C -22 152 -30.6 134 -33.4 116 C -35 104 -34.4 92 -31.8 84 ' +
    'C -29.6 77.4 -25.4 74 -21.2 74.8 C -17.2 75.6 -14.6 79.4 -14.2 84.4 ' +
    'C -13.4 96 -11.6 110 -7.6 126 C -5.8 140 -8.6 158 -19.4 170 Z',
  knuckles: 'M 10.9 51 C 11.6 45.6 15.6 42.6 20.6 42.6 C 25.8 42.6 29.8 46 30.3 51.4 C 30.5 56 29.8 60 28.6 63 L 12 66 Z ' +
    'M 30.3 51.4 C 32.2 48.8 35.2 47.8 38.4 48 C 43.6 48.4 47.2 52.2 47.3 57.6 C 47.3 62 46.6 66 45.4 69 L 29 64 Z ' +
    'M 47.3 57.6 C 48.9 56.2 51.1 55.6 53.3 56 C 57.9 56.8 60.9 60.6 60.9 66 C 60.9 70 60.3 73.6 59.4 76.4 L 45.6 70 Z',
  nail: 'M -6.4 -1.2 C -6.4 -6.6 -3.6 -8.9 0 -8.9 C 3.6 -8.9 6.4 -6.6 6.4 -1.2 L 6.1 10.4 ' +
    'C 6 12.6 3.6 14 0 14 C -3.6 14 -6 12.6 -6.1 10.4 Z',
  nailEdge: 'M -6.3 -1.8 C -6 -6.6 -3.4 -8.9 0 -8.9 C 3.4 -8.9 6 -6.6 6.3 -1.8 C 3.8 -3.6 -3.8 -3.6 -6.3 -1.8 Z',
  nailShine: 'M -3.2 1 Q -3.4 5 -2.2 9',
  thumbNail: 'M -30.6 88 C -30.2 82 -27 78.4 -23.4 78.6 C -20.4 78.8 -18.2 81.4 -18.2 85 C -20.2 88.6 -27 90.6 -30.6 88 Z',
  seams: 'M 10.9 51 C 11.3 57 11.5 62 11.4 67 ' +
    'M 30.3 51.4 C 30.5 55 30.1 58.6 29.4 61.4 ' +
    'M 47.3 57.6 C 47.4 61 47 64.4 46.2 67 ' +
    'M -14.2 84.4 C -13.4 96 -11.6 110 -8 124',
  creases: 'M -4.4 22.6 Q 0 24.8 4.4 22.6 ' +
    'M -6.4 45.4 Q 0 48.6 6.4 45.4 M -5.4 49.6 Q 0 52.2 5.4 49.6 M -3.6 53.4 Q 0 54.8 3.6 53.4 ' +
    'M 15.6 52.4 Q 20.4 54.4 25.2 52.4 M 34 56.4 Q 38.4 58.2 42.8 56.4 M 50 63.6 Q 53.6 65 57.2 63.6',
  edge: '#A5643F',
};
let handPaths = null;
function hp() {
  if (!handPaths) handPaths = Object.fromEntries(Object.entries(Hand).filter(([k]) => k !== 'edge').map(([k, d]) => [k, new Path2D(d)]));
  return handPaths;
}
function lin(ctx, x0, y0, x1, y1, stops) {
  const g = ctx.createLinearGradient(x0, y0, x1, y1);
  for (const [o, c] of stops) g.addColorStop(o, c);
  return g;
}
function drawHandBody(ctx, press, mirrored) {
  const H = hp();
  // drawShadow: a soft shadow without a blur
  const dx = (3 + (1 - 3) * press) * (mirrored ? -1 : 1);
  const dy = 8 + (2.5 - 8) * press;
  ctx.save();
  ctx.translate(dx, dy);
  ctx.fillStyle = 'rgba(0,0,0,0.10)'; ctx.fill(H.outline);
  ctx.lineJoin = 'round';
  ctx.lineWidth = 5; ctx.strokeStyle = 'rgba(0,0,0,0.06)'; ctx.stroke(H.outline);
  ctx.lineWidth = 10; ctx.strokeStyle = 'rgba(0,0,0,0.035)'; ctx.stroke(H.outline);
  ctx.restore();
  ctx.lineJoin = 'miter';

  ctx.fillStyle = lin(ctx, -30, -10, 60, 170, [[0, '#F8D2B0'], [1, '#E3A77E']]); ctx.fill(H.outline);
  ctx.fillStyle = lin(ctx, -40, 80, -10, 120, [[0, '#E9B089'], [1, '#F2C29D']]); ctx.fill(H.thumb);
  ctx.fillStyle = lin(ctx, 0, 42, 0, 72, [[0, 'rgba(246,205,170,1)'], [1, 'rgba(230,173,132,0)']]); ctx.fill(H.knuckles);
  ctx.fillStyle = lin(ctx, -40, 0, 62, 0, [
    [0, 'rgba(138,79,46,0.180)'], [0.25, 'rgba(138,79,46,0)'], [0.7, 'rgba(138,79,46,0)'], [1, 'rgba(138,79,46,0.220)']]);
  ctx.fill(H.outline);
  ctx.lineCap = 'round';
  ctx.lineWidth = 1.1; ctx.strokeStyle = 'rgba(165,100,63,0.45)'; ctx.stroke(H.seams);
  ctx.lineWidth = 1; ctx.strokeStyle = 'rgba(165,100,63,0.42)'; ctx.stroke(H.creases);
  ctx.lineCap = 'butt';
  ctx.beginPath(); ctx.ellipse(-6 + 7, 90 + 4, 7, 4, 0, 0, Math.PI * 2); ctx.fillStyle = 'rgba(255,255,255,0.22)'; ctx.fill();
  const nailFill = lin(ctx, 0, -9, 0, 14, [[0, '#FCEAE0'], [1, '#F0C3B0']]);
  ctx.fillStyle = nailFill; ctx.fill(H.nail);
  ctx.lineWidth = 0.8; ctx.strokeStyle = 'rgba(165,100,63,0.35)'; ctx.stroke(H.nail);
  ctx.fillStyle = 'rgba(255,255,255,0.55)'; ctx.fill(H.nailEdge);
  ctx.lineCap = 'round'; ctx.lineWidth = 1.3; ctx.strokeStyle = 'rgba(255,255,255,0.7)'; ctx.stroke(H.nailShine); ctx.lineCap = 'butt';
  ctx.fillStyle = lin(ctx, 0, -9, 0, 14, [[0, '#FCEAE0'], [1, '#F0C3B0']]); ctx.fill(H.thumbNail);
  ctx.lineWidth = 0.8; ctx.strokeStyle = 'rgba(165,100,63,0.35)'; ctx.stroke(H.thumbNail);
  ctx.lineJoin = 'round'; ctx.lineWidth = 1.2; ctx.strokeStyle = 'rgba(165,100,63,0.75)'; ctx.stroke(H.outline); ctx.lineJoin = 'miter';
}
/** drawPointingHand: the pad of the index finger on [tip]. tilt in degrees clockwise. */
export function drawPointingHand(ctx, tip, fingerWidth, tilt, press, alpha = 1, mirrored = false) {
  if (alpha <= 0.01) return;
  const lift = 1.06 + (0.97 - 1.06) * press;
  const s = fingerWidth / 20 * lift;
  const layered = alpha < 0.99;
  let target = ctx;
  if (layered) {
    const c = ctx.canvas;
    const off = (drawPointingHand._off ||= document.createElement('canvas'));
    off.width = c.width; off.height = c.height;
    target = off.getContext('2d');
    target.setTransform(ctx.getTransform());
  }
  target.save();
  target.translate(tip.x, tip.y);
  target.rotate(tilt * Math.PI / 180);
  target.scale(mirrored ? -s : s, s);
  drawHandBody(target, press, mirrored);
  target.restore();
  if (layered) {
    ctx.save();
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.globalAlpha = alpha;
    ctx.drawImage(target.canvas, 0, 0);
    ctx.restore();
  }
}

// ---- scene pieces ------------------------------------------------------------------------------
function drawPill(ctx, cy, lift = 0, cx = PILL_CX, alpha = 1) {
  const grow = 1 + 0.12 * lift;
  const w = PILL_W * grow, h = PILL_H * grow;
  const x = cx - w / 2, y = cy - h / 2;
  if (lift > 0.01) rr(ctx, Black, x + 2, y + 6, w, h, w / 2, 0.2 * clamp(lift, 0, 1) * alpha);
  rr(ctx, SimpleFill, x, y, w, h, w / 2, alpha);
  rr(ctx, White, x, y, w, h, w / 2, alpha, 1);
}
function drawTab(ctx, cy = BAR_CY, alpha = 1) {
  ctx.fillStyle = css(TabColour, alpha);
  ctx.fill(tabPath(SCREEN_R, cy, TAB_W, TAB_H, TAB_SWEEP));
}
function drawLevel(ctx, center, width, height, level, alpha, scale = 1) {
  if (alpha <= 0.01 || scale <= 0.01) return;
  const w = width * scale, h = height * scale;
  const left = center.x - w / 2, top = center.y - h / 2;
  rr(ctx, White, left, top, w, h, w / 2, 0.92 * alpha);
  const inset = w * 0.22;
  const innerW = w - inset * 2, innerH = h - inset * 2;
  const fill = Math.max(innerH * level, innerW);
  rr(ctx, Indigo, left + inset, top + inset + innerH - fill, innerW, fill, innerW / 2, alpha);
}
function drawTouch(ctx, at, press, pulse) {
  if (press <= 0.01) return;
  circle(ctx, White, 14, at, 0.5 * press);
  circle(ctx, White, 14 + 14 * pulse, at, 0.8 * press * (1 - pulse), 2);
}
function drawFinger(ctx, tip, press, alpha) {
  drawPointingHand(ctx, tip, FINGER_W, DeviceArt.HAND_TILT, press, alpha);
}
const fingerAt = (contact, arrive, leave) => (leave > 0 ? mixP(contact, FingerRest, leave) : mixP(FingerRest, contact, arrive));
function holdRing(ctx, tip, hold, press) {
  arcStroke(ctx, White, -90, 360 * hold, tip.x, tip.y, 21, 3, press);
}

// ---- scenes ------------------------------------------------------------------------------------
function drawIntro(ctx, t, pulse) {
  const low = BAR_CY + 16, high = BAR_CY - 16;
  const arrive = span(t, 0.02, 0.14);
  const up = span(t, 0.18, 0.42);
  const down = span(t, 0.52, 0.76);
  const leave = span(t, 0.82, 0.94);
  const press = span(t, 0.12, 0.17) * (1 - span(t, 0.78, 0.83));
  const alpha = span(t, 0, 0.06, Linear) * (1 - span(t, 0.86, 0.95, Linear));
  const travel = up - down;
  const tip = fingerAt(P(PILL_CX, mix(low, high, travel)), arrive, leave);
  const levelAlpha = span(t, 0.14, 0.2, Linear) * (1 - span(t, 0.84, 0.92, Linear));
  clipScreen(ctx, () => {
    drawLevel(ctx, P(PILL_CX - 21, BAR_CY), 7, 72, mix(0.3, 0.85, travel), levelAlpha);
    drawPill(ctx, BAR_CY);
    drawTouch(ctx, tip, press, pulse);
  });
  drawFinger(ctx, tip, press, alpha);
}

function drawSimple(ctx, t, pulse) {
  const a = clamp(t * 3, 0, 1), b = clamp(t * 3 - 1, 0, 1), c = clamp(t * 3 - 2, 0, 1);
  let tip = FingerRest, press = 0, alpha = 0, barCy = BAR_CY, lift = 0;
  if (t < 1 / 3) {
    const up = span(a, 0.28, 0.66);
    tip = fingerAt(P(PILL_CX, mix(BAR_CY + 16, BAR_CY - 16, up)), span(a, 0.02, 0.2), span(a, 0.76, 0.95));
    press = span(a, 0.2, 0.28) * (1 - span(a, 0.7, 0.78));
    alpha = span(a, 0, 0.1, Linear) * (1 - span(a, 0.82, 0.96, Linear));
    const levelAlpha = span(a, 0.22, 0.3, Linear) * (1 - span(a, 0.8, 0.92, Linear));
    clipScreen(ctx, () => drawLevel(ctx, P(PILL_CX - 21, BAR_CY), 7, 72, mix(0.3, 0.85, up), levelAlpha));
  } else if (t < 2 / 3) {
    tip = fingerAt(P(PILL_CX, BAR_CY), span(b, 0.02, 0.2), span(b, 0.4, 0.62));
    press = span(b, 0.22, 0.27) * (1 - span(b, 0.3, 0.36));
    alpha = span(b, 0, 0.1, Linear) * (1 - span(b, 0.5, 0.64, Linear));
    const open = span(b, 0.32, 0.48, Pop);
    const panelAlpha = span(b, 0.32, 0.4, Linear) * (1 - span(b, 0.84, 0.96, Linear));
    clipScreen(ctx, () => drawLevel(ctx, P(PILL_CX - 36, BAR_CY), 24, 96, 0.6, panelAlpha, open));
  } else {
    const move = span(c, 0.4, 0.66);
    const home = span(c, 0.86, 0.99);
    barCy = BAR_CY + 44 * (move - home);
    lift = span(c, 0.3, 0.4, Pop) * (1 - span(c, 0.7, 0.78));
    tip = fingerAt(P(PILL_CX, BAR_CY + 44 * move), span(c, 0.02, 0.18), span(c, 0.74, 0.9));
    press = span(c, 0.18, 0.24) * (1 - span(c, 0.7, 0.76));
    alpha = span(c, 0, 0.1, Linear) * (1 - span(c, 0.78, 0.92, Linear));
  }
  clipScreen(ctx, () => {
    drawPill(ctx, barCy, lift);
    drawTouch(ctx, tip, press, pulse);
  });
  drawFinger(ctx, tip, press, alpha);
}

function drawQuickSlider(ctx, t, pulse) {
  const arrive = span(t, 0.02, 0.12);
  const press = span(t, 0.12, 0.16) * (1 - span(t, 0.72, 0.77));
  const grow = span(t, 0.16, 0.3, Pop) * (1 - span(t, 0.76, 0.88));
  const leave = span(t, 0.78, 0.92);
  const alpha = span(t, 0, 0.06, Linear) * (1 - span(t, 0.82, 0.94, Linear));
  const y = BAR_CY + 8 - 58 * span(t, 0.3, 0.44) + 86 * span(t, 0.46, 0.6) - 30 * span(t, 0.62, 0.7);
  const left = mix(SCREEN_R - TAB_W, 190, grow);
  const right = mix(SCREEN_R + 6, 226, grow);
  const top = mix(BAR_CY - TAB_H / 2, 34, grow);
  const bottom = mix(BAR_CY + TAB_H / 2, 194, grow);
  const tip = fingerAt(P(mix(SCREEN_R - TAB_W / 2 - 1, (190 + 226) / 2, grow), y), arrive, leave);
  clipScreen(ctx, () => {
    const tabAlpha = clamp(1 - grow * 3, 0, 1);
    if (tabAlpha > 0) drawTab(ctx, BAR_CY, tabAlpha);
    if (grow > 0.01) {
      const g = clamp(grow, 0, 1);
      const radius = mix(TAB_W / 2, 16, g);
      rr(ctx, TabColour, left, top, right - left, bottom - top, radius);
      const inset = 4 * g;
      const fillTop = clamp(y, top + inset + 10, bottom - inset - 10);
      rr(ctx, White, left + inset, fillTop, right - left - inset * 2, bottom - inset - fillTop, Math.max(0, radius - inset), 0.95 * g);
    }
    drawTouch(ctx, tip, press, pulse);
  });
  drawFinger(ctx, tip, press, alpha);
}

const DeckItems = [['Dial', 22], ['App', 22], ['SecondApp', 22], ['Divider', 1], ['ToggleOn', 22], ['Toggle', 22]];
function drawDeckItem(ctx, item, center, appear) {
  const r = 11 * appear;
  switch (item) {
    case 'Dial':
      circle(ctx, White, r, center, 0.14);
      circle(ctx, TileColours[3], 4.5 * appear, center);
      break;
    case 'App': case 'SecondApp': {
      const side = 20 * appear;
      const colour = item === 'App' ? TileColours[0] : TileColours[1];
      rr(ctx, colour, center.x - side / 2, center.y - side / 2, side, side, 6 * appear);
      circle(ctx, White, 3.5 * appear, center, 0.85);
      break;
    }
    case 'Divider':
      rr(ctx, White, center.x - 7, center.y - 0.5, 14, 1, 0.5, 0.18 * appear);
      break;
    case 'ToggleOn':
      circle(ctx, Indigo, r, center);
      circle(ctx, White, 4 * appear, center);
      break;
    case 'Toggle':
      circle(ctx, White, r, center, 0.14);
      circle(ctx, TileColours[2], 4 * appear, center);
      break;
  }
}
function drawDeck(ctx, t, pulse) {
  const arrive = span(t, 0.02, 0.12);
  const press = span(t, 0.12, 0.16) * (1 - span(t, 0.38, 0.43));
  const swipe = span(t, 0.16, 0.38);
  const leave = span(t, 0.42, 0.56);
  const alpha = span(t, 0, 0.06, Linear) * (1 - span(t, 0.46, 0.58, Linear));
  const close = span(t, 0.8, 0.92);
  const open = swipe * (1 - close);
  const tip = fingerAt(P(mix(SCREEN_R - TAB_W / 2 - 1, 160, swipe), BAR_CY), arrive, leave);
  clipScreen(ctx, () => {
    drawTab(ctx);
    if (open > 0.001) {
      const left = mix(SCREEN_R + 4, DECK_RIGHT - DECK_W, open);
      rr(ctx, Black, left + 2, DECK_TOP + 6, DECK_W, DECK_H, DECK_W / 2, 0.18 * open);
      rr(ctx, DeckPanel, left, DECK_TOP, DECK_W, DECK_H, DECK_W / 2);
      const cx = left + DECK_W / 2;
      let y = DECK_TOP + DECK_PAD;
      DeckItems.forEach(([item, height], i) => {
        const appear = span(t, 0.24 + i * 0.03, 0.34 + i * 0.03, Pop) * (1 - close);
        const cy = y + height / 2;
        if (appear > 0.01) drawDeckItem(ctx, item, P(cx, cy), appear);
        y += height + DECK_GAP;
      });
    }
    drawTouch(ctx, tip, press, pulse);
  });
  drawFinger(ctx, tip, press, alpha);
}

function drawLongPress(ctx, t, pulse) {
  const arrive = span(t, 0.02, 0.12);
  const press = span(t, 0.12, 0.16) * (1 - span(t, 0.5, 0.55));
  const hold = span(t, 0.16, 0.4, Linear);
  const menu = span(t, 0.4, 0.52, Pop) * (1 - span(t, 0.82, 0.92));
  const leave = span(t, 0.54, 0.68);
  const alpha = span(t, 0, 0.06, Linear) * (1 - span(t, 0.58, 0.7, Linear));
  const tip = fingerAt(P(SCREEN_R - TAB_W / 2 - 1, BAR_CY), arrive, leave);
  clipScreen(ctx, () => {
    drawTab(ctx);
    if (menu > 0.01) {
      const side = 106, right = 218;
      ctx.save();
      scaleAbout(ctx, menu, menu, right, BAR_CY);
      const left = right - side, top = BAR_CY - side / 2;
      rr(ctx, Black, left + 2, top + 6, side, side, 20, 0.16);
      rr(ctx, MenuPanel, left, top, side, side, 20);
      for (let i = 0; i < 9; i++) {
        const x = left + 8 + (i % 3) * 32, y = top + 8 + Math.floor(i / 3) * 32;
        rr(ctx, MenuTile, x, y, 26, 26, 8);
        circle(ctx, TileColours[i], 5, P(x + 13, y + 13));
      }
      ctx.restore();
    }
    if (press > 0.01 && hold > 0 && hold < 1) holdRing(ctx, tip, hold, press);
    drawTouch(ctx, tip, press, pulse);
  });
  drawFinger(ctx, tip, press, alpha);
}

function drawMoveGlyph(ctx, center, size, alpha) {
  if (alpha <= 0.01) return;
  const r = size / 2, head = r * 0.42, stroke = size * 0.13;
  line(ctx, White, P(center.x - r, center.y), P(center.x + r, center.y), stroke, alpha);
  line(ctx, White, P(center.x, center.y - r), P(center.x, center.y + r), stroke, alpha);
  for (let i = 0; i < 4; i++) {
    const ux = i === 0 ? 1 : i === 1 ? -1 : 0;
    const uy = i === 2 ? 1 : i === 3 ? -1 : 0;
    const tip = P(center.x + ux * r, center.y + uy * r);
    line(ctx, White, tip, P(tip.x - ux * head - uy * head, tip.y - uy * head + ux * head), stroke, alpha);
    line(ctx, White, tip, P(tip.x - ux * head + uy * head, tip.y - uy * head - ux * head), stroke, alpha);
  }
}
function drawMove(ctx, t, pulse, tab) {
  const arrive = span(t, 0.02, 0.12);
  const press = span(t, 0.12, 0.16) * (1 - span(t, 0.7, 0.74));
  const hold = span(t, 0.16, 0.34, Linear);
  const held = span(t, 0.33, 0.4, Pop) * (1 - span(t, 0.7, 0.76));
  const down = span(t, 0.4, 0.52);
  const across = span(t, 0.54, 0.68);
  const snap = span(t, 0.72, 0.82, Pop);
  const leave = span(t, 0.74, 0.88);
  const alpha = span(t, 0, 0.06, Linear) * (1 - span(t, 0.78, 0.9, Linear));
  const gone = span(t, 0.9, 0.94, Linear) * (1 - span(t, 0.95, 0.99, Linear));
  const home = t >= 0.945;
  const startX = tab ? SCREEN_R - TAB_W / 2 : PILL_CX;
  const restX = tab ? SCREEN_L + TAB_W / 2 : PILL_CX_LEFT;
  const heldX = mix(startX, MOVE_DROP_X, across);
  const x = home ? startX : mix(heldX, restX, snap);
  const y = home ? BAR_CY : BAR_CY + MOVE_DOWN * down;
  const tip = fingerAt(P(heldX, BAR_CY + MOVE_DOWN * down), arrive, leave);
  const lift = clamp(held, 0, 1);
  const barAlpha = (1 - gone) * (1 - (1 - DRAG_CUE_ALPHA) * lift);
  clipScreen(ctx, () => {
    if (tab) {
      const onLeft = x < (SCREEN_L + SCREEN_R) / 2;
      const edge = onLeft ? x - TAB_W / 2 : x + TAB_W / 2;
      ctx.save();
      scaleAbout(ctx, 1 + 0.12 * held, 1 + 0.12 * held, x, y);
      if (lift > 0.01) {
        ctx.fillStyle = css(Black, 0.2 * lift * (1 - gone));
        ctx.fill(tabPath(edge + 2, y + 6, TAB_W, TAB_H, TAB_SWEEP, onLeft));
      }
      ctx.fillStyle = css(TabColour, barAlpha);
      ctx.fill(tabPath(edge, y, TAB_W, TAB_H, TAB_SWEEP, onLeft));
      ctx.restore();
      drawMoveGlyph(ctx, P(x, y), 7, lift * (1 - gone));
    } else {
      drawPill(ctx, y, held, x, barAlpha);
      drawMoveGlyph(ctx, P(x, y), 10, lift * (1 - gone));
    }
    if (press > 0.01 && hold > 0 && hold < 1) holdRing(ctx, tip, hold, press);
    drawTouch(ctx, tip, press, pulse);
  });
  drawFinger(ctx, tip, press, alpha);
}

function drawPermission(ctx, t, pulse) {
  const cardTop = 48, switchLeft = 176, switchTop = cardTop + 14;
  const arrive = span(t, 0.04, 0.18);
  const press = span(t, 0.2, 0.24) * (1 - span(t, 0.28, 0.33));
  const leave = span(t, 0.34, 0.5);
  const alpha = span(t, 0, 0.08, Linear) * (1 - span(t, 0.4, 0.52, Linear));
  const reset = 1 - span(t, 0.9, 0.98, Linear);
  const on = span(t, 0.24, 0.34) * reset;
  const bar = span(t, 0.42, 0.56, Pop) * reset;
  const tip = fingerAt(P(switchLeft + 17, switchTop + 9), arrive, leave);
  clipScreen(ctx, () => {
    rr(ctx, White, 58, cardTop, 164, 46, 14, 0.95);
    rr(ctx, TextLine, 70, cardTop + 13, 82, 7, 3.5);
    rr(ctx, TextLine, 70, cardTop + 26, 56, 6, 3, 0.6);
    rr(ctx, lerpColor(SwitchOff, Indigo, on), switchLeft, switchTop, 34, 18, 9);
    circle(ctx, White, 7, P(switchLeft + 9 + 16 * on, switchTop + 9));
    if (bar > 0.01) { ctx.save(); scaleAbout(ctx, bar, bar, PILL_CX, 140); drawPill(ctx, 140); ctx.restore(); }
    drawTouch(ctx, tip, press, pulse);
  });
  drawFinger(ctx, tip, press, alpha);
}

function drawBell(ctx, center, alpha) {
  circle(ctx, Indigo, 9, center, alpha);
  // drawArc(180, 180, useCenter = true) in the 8x8 oval at (-4,-5.5): the top half-disc
  ctx.beginPath();
  ctx.moveTo(center.x, center.y - 1.5);
  ctx.arc(center.x, center.y - 1.5, 4, Math.PI, 2 * Math.PI);
  ctx.closePath();
  ctx.fillStyle = css(White, alpha); ctx.fill();
  ctx.fillStyle = css(White, alpha); ctx.fillRect(center.x - 4, center.y - 1.6, 8, 3.6);
  rr(ctx, White, center.x - 5.6, center.y + 1.8, 11.2, 1.8, 0.9, alpha);
  circle(ctx, White, 1.3, P(center.x, center.y + 4.8), alpha);
}
function drawShade(ctx, open) {
  const dy = -(1 - open) * 118;
  rr(ctx, DeckPanel, SCREEN_L, SCREEN_T - 30 + dy, SCREEN_R - SCREEN_L, 140, 20);
  for (let i = 0; i < 4; i++) circle(ctx, White, 7, P(82 + i * 38, 46 + dy), i === 1 ? 0.9 : 0.18);
  const top = 62 + dy;
  rr(ctx, White, 58, top, 164, 56, 14, 0.97);
  circle(ctx, Indigo, 7, P(72, top + 14));
  rr(ctx, White, 70.8, top + 10, 2.4, 8, 1.2);
  rr(ctx, TextLine, 84, top + 9, 60, 6, 3);
  rr(ctx, TextLine, 84, top + 19, 84, 5, 2.5, 0.6);
  for (let i = 0; i < 3; i++) {
    const left = 70 + i * 48;
    rr(ctx, Indigo, left, top + 35, 42, 12, 6, 0.12);
    rr(ctx, Indigo, left + 10, top + 39.5, 22, 3, 1.5, 0.75);
  }
}
function drawNotifications(ctx, t, pulse) {
  const arrive = span(t, 0.04, 0.18);
  const press = span(t, 0.2, 0.24) * (1 - span(t, 0.28, 0.33));
  const leave = span(t, 0.34, 0.5);
  const alpha = span(t, 0, 0.08, Linear) * (1 - span(t, 0.4, 0.52, Linear));
  const asking = (1 - span(t, 0.3, 0.4)) + span(t, 0.93, 0.99, Linear);
  const shade = span(t, 0.42, 0.58) * (1 - span(t, 0.86, 0.93));
  const tip = fingerAt(P(140, NOTIF_ALLOW_Y), arrive, leave);
  clipScreen(ctx, () => {
    if (shade > 0.001) drawShade(ctx, shade);
    if (asking > 0.01) {
      ctx.fillStyle = css(Black, 0.22 * asking);
      ctx.fillRect(SCREEN_L, SCREEN_T, SCREEN_R - SCREEN_L, SCREEN_B - SCREEN_T);
      const grow = 0.92 + 0.08 * asking;
      ctx.save();
      scaleAbout(ctx, grow, grow, 140, 100);
      rr(ctx, White, 72, 50, 136, 100, 16, 0.97 * asking);
      drawBell(ctx, P(140, 67), asking);
      rr(ctx, TextLine, 100, 82, 80, 6, 3, asking);
      rr(ctx, TextLine, 112, 93, 56, 5, 2.5, 0.6 * asking);
      rr(ctx, Indigo, 84, NOTIF_ALLOW_Y - 7.5, 112, 15, 7.5, (0.14 + 0.3 * press) * asking);
      rr(ctx, Indigo, 122, NOTIF_ALLOW_Y - 2, 36, 4, 2, asking);
      rr(ctx, TextLine, 116, NOTIF_ALLOW_Y + 17, 48, 4, 2, asking);
      ctx.restore();
    }
    drawTouch(ctx, tip, press, pulse);
  });
  drawFinger(ctx, tip, press, alpha);
}

function drawVolumeKeys(ctx, pressed) {
  rr(ctx, FrameColour, 237, 52, 6 - 2 * pressed, 22, 2);
  rr(ctx, FrameColour, 237, 80, 6, 22, 2);
  if (pressed > 0.01) circle(ctx, Indigo, 8 + 6 * pressed, P(244, 63), 0.28 * pressed);
}
function drawAccessibilityGlyph(ctx, c) {
  circle(ctx, Indigo, 10, c);
  const s = 1.7;
  circle(ctx, White, 1.9, P(c.x, c.y - 4.6));
  line(ctx, White, P(c.x - 5, c.y - 1.4), P(c.x + 5, c.y - 1.4), s);
  line(ctx, White, P(c.x, c.y - 1.4), P(c.x, c.y + 2.2), s);
  line(ctx, White, P(c.x, c.y + 2.2), P(c.x - 2.8, c.y + 6.2), s);
  line(ctx, White, P(c.x, c.y + 2.2), P(c.x + 2.8, c.y + 6.2), s);
}
function drawAccessibility(ctx, t, pulse) {
  const cardTop = 146, switchLeft = 176, switchTop = cardTop + 14;
  const arrive = span(t, 0.04, 0.18);
  const press = span(t, 0.2, 0.24) * (1 - span(t, 0.28, 0.33));
  const leave = span(t, 0.34, 0.5);
  const alpha = span(t, 0, 0.08, Linear) * (1 - span(t, 0.4, 0.52, Linear));
  const reset = 1 - span(t, 0.9, 0.98, Linear);
  const on = span(t, 0.24, 0.34) * reset;
  const key = span(t, 0.5, 0.54) * (1 - span(t, 0.62, 0.66));
  const grow = span(t, 0.52, 0.64, Pop) * (1 - span(t, 0.84, 0.92));
  const level = mix(0.35, 0.7, span(t, 0.58, 0.72));
  const tip = fingerAt(P(switchLeft + 17, switchTop + 9), arrive, leave);
  drawVolumeKeys(ctx, key);
  clipScreen(ctx, () => {
    const tabAlpha = clamp(1 - grow * 3, 0, 1);
    if (tabAlpha > 0) drawTab(ctx, BAR_CY, tabAlpha);
    if (grow > 0.01) {
      const g = clamp(grow, 0, 1);
      const left = mix(SCREEN_R - TAB_W, 192, grow);
      const right = mix(SCREEN_R + 6, 226, grow);
      const top = mix(BAR_CY - TAB_H / 2, 42, grow);
      const bottom = mix(BAR_CY + TAB_H / 2, 138, grow);
      const radius = mix(TAB_W / 2, 16, g);
      rr(ctx, TabColour, left, top, right - left, bottom - top, radius);
      const inset = 4 * g;
      const fillTop = mix(bottom - inset - 10, top + inset + 10, level);
      rr(ctx, White, left + inset, fillTop, right - left - inset * 2, bottom - inset - fillTop, Math.max(0, radius - inset), 0.95 * g);
    }
    rr(ctx, White, 58, cardTop, 164, 46, 14, 0.95);
    drawAccessibilityGlyph(ctx, P(77, cardTop + 23));
    rr(ctx, TextLine, 94, cardTop + 13, 66, 7, 3.5);
    rr(ctx, TextLine, 94, cardTop + 26, 44, 6, 3, 0.6);
    rr(ctx, lerpColor(SwitchOff, Indigo, on), switchLeft, switchTop, 34, 18, 9);
    circle(ctx, White, 7, P(switchLeft + 9 + 16 * on, switchTop + 9));
    drawTouch(ctx, tip, press, pulse);
  });
  drawFinger(ctx, tip, press, alpha);
}

/**
 * The scene at [t] (0..1 of its loop) fitted into a w x h pixel box at the context's current
 * transform: scaled to fit, centred across, standing on the bottom (DeviceArt.scale/origin).
 * The caller should clip to the box (the Canvas is clipToBounds).
 */
export function drawWalkScene(ctx, scene, t, w, h) {
  const cycle = SCENES[scene].cycleMs;
  const pulse = (t * cycle / PULSE_MS) % 1;
  const s = DeviceArt.scale(w, h);
  const o = DeviceArt.origin(w, h, s);
  ctx.save();
  ctx.beginPath(); ctx.rect(0, 0, w, h); ctx.clip();
  ctx.translate(o.x, o.y);
  ctx.scale(s, s);
  drawScenePhone(ctx);
  switch (scene) {
    case 'Intro': drawIntro(ctx, t, pulse); break;
    case 'Simple': drawSimple(ctx, t, pulse); break;
    case 'QuickSlider': drawQuickSlider(ctx, t, pulse); break;
    case 'Deck': drawDeck(ctx, t, pulse); break;
    case 'LongPress': drawLongPress(ctx, t, pulse); break;
    case 'MoveButton': drawMove(ctx, t, pulse, false); break;
    case 'MoveTab': drawMove(ctx, t, pulse, true); break;
    case 'Permission': drawPermission(ctx, t, pulse); break;
    case 'Notifications': drawNotifications(ctx, t, pulse); break;
    case 'Accessibility': drawAccessibility(ctx, t, pulse); break;
  }
  ctx.restore();
}

// ---- StylePreview (the 64 x 88 dp picture on each style card) ------------------------------------
/** [d] = pixels per dp. [bob] in [-1, 1]: infinite 1400 ms FastOutSlowIn, reversing; 0 when not selected. */
export function drawStylePreview(ctx, advanced, selected, bob, d) {
  const W = 64 * d, H = 88 * d;
  ctx.save();
  ctx.beginPath(); ctx.rect(0, 0, W, H); ctx.clip();
  const edge = W - 8 * d;
  rr(ctx, FrameColour, -40 * d, 6 * d, edge + 48 * d, H + 40 * d, 20 * d);
  const g = ctx.createLinearGradient(0, 0, W, H);
  g.addColorStop(0, DeviceArt.WallTop); g.addColorStop(1, DeviceArt.WallBottom);
  ctx.beginPath(); ctx.roundRect(-40 * d, 10 * d, edge + 40 * d, H + 40 * d, 16 * d); ctx.fillStyle = g; ctx.fill();
  const cy = H / 2 + 6 * d + (selected ? bob * 4 * d : 0);
  if (advanced) {
    ctx.fillStyle = css(TabColour);
    const total = 34 * d + 2 * 6 * d;
    ctx.fill(tabOutlinePath(edge, cy - total / 2, 5 * d, total, DOCK_FLARE, false));
  } else {
    const w = 13 * d, h = 44 * d;
    const x = edge - 3 * d - w, y = cy - h / 2;
    rr(ctx, SimpleFill, x, y, w, h, w / 2);
    rr(ctx, White, x, y, w, h, w / 2, 1, 1 * d);
  }
  ctx.restore();
}

// ---- StageBackdrop (behind the phone on every illustration card) -----------------------------------
export function drawStageBackdrop(ctx, w, h, dark = false) {
  const wash = dark ? ['#1C2238', '#121728'] : ['#F3F2FF', '#E7ECFF'];
  const strength = dark ? 0.55 : 1;
  const g = ctx.createLinearGradient(0, 0, w, h);
  g.addColorStop(0, wash[0]); g.addColorStop(1, wash[1]);
  ctx.fillStyle = g; ctx.fillRect(0, 0, w, h);
  const reach = Math.max(w, h);
  const glows = [
    [0.08, 0.12, 0.55, [0xff, 0x9a, 0x76], 0.42],
    [0.92, 0.08, 0.45, [0x7c, 0x83, 0xff], 0.34],
    [0.9, 0.95, 0.6, [0x34, 0xcf, 0xb6], 0.36],
  ];
  for (const [gx, gy, gr, c, a] of glows) {
    const cx = gx * w, cy = gy * h, r = gr * reach;
    const rg = ctx.createRadialGradient(cx, cy, 0, cx, cy, r);
    rg.addColorStop(0, `rgba(${c},${a * strength})`);
    rg.addColorStop(0.5, `rgba(${c},${a * strength * 0.4})`);
    rg.addColorStop(1, `rgba(${c},0)`);
    ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2); ctx.fillStyle = rg; ctx.fill();
  }
}
