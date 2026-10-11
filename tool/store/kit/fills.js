// The Quick panel's Canvas fills that the store art shows, ported from PixelArt/PixelFill,
// EffortArt/EffortFill and (from its spec) GlimmerArt. Lengths in dp; R is the panel rect.
import { clamp } from './core.js';

const TAU = Math.PI * 2;
const wrap = (x) => ((x % 1) + 1) % 1;
const ringDistance = (a, b) => { const d = Math.abs(wrap(a - b)); return Math.min(d, 1 - d); };
const falloff = (d, w) => Math.max(0, 1 - d / w);

export function hsv(hue, s, v) {
  const hh = (((hue % 360) + 360) % 360) / 60;
  const c = v * s, x = c * (1 - Math.abs((hh % 2) - 1)), m = v - c;
  const [r, g, b] = [[c, x, 0], [x, c, 0], [0, c, x], [0, x, c], [x, 0, c], [c, 0, x]][Math.min(5, Math.floor(hh))];
  return [Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255)];
}

// Pixels, Spectrum pattern (the default): 4 across, gap 18 %, roundness 35 %, glow 60 %, rest 10 %.
export function drawPixels(ctx, R, fillTop, phase, { columns = 4, gap = 0.18, roundness = 0.35, glow = 0.6, rest = 0.1 } = {}, alpha = 1) {
  const pitchX = R.w / columns;
  const rows = Math.max(1, Math.floor(R.h / pitchX));
  const pitchY = R.h / rows;
  const cells = [];
  for (let row = 0; row < rows; row++) {
    const bottom = R.y + R.h - row * pitchY;
    const lit = clamp((bottom - fillTop) / pitchY);
    const u = (row + 0.5) / rows;
    const level = Math.pow(falloff(ringDistance(u, wrap(phase)), 0.28), 2);
    const on = 0.35 + 0.65 * level;
    const color = hsv(u * 0.85 * 360, 0.72, 1);
    for (let col = 0; col < columns; col++) cells.push({ row, col, bottom, color, b: rest + (on - rest) * lit, g: on * lit * glow });
  }
  // The glow: the grid at one texel per pixel, stretched (bilinear) under the pixels.
  if (glow > 0.01) {
    const gc = document.createElement('canvas');
    gc.width = columns; gc.height = rows;
    const gx = gc.getContext('2d');
    const img = gx.createImageData(columns, rows);
    for (const c of cells) {
      const i = ((rows - 1 - c.row) * columns + c.col) * 4;
      img.data[i] = c.color[0]; img.data[i + 1] = c.color[1]; img.data[i + 2] = c.color[2]; img.data[i + 3] = Math.round(clamp(c.g) * 255);
    }
    gx.putImageData(img, 0, 0);
    ctx.save(); ctx.globalAlpha *= alpha; ctx.imageSmoothingEnabled = true; ctx.drawImage(gc, R.x, R.y, R.w, R.h); ctx.restore();
  }
  const ix = (pitchX * gap) / 2, iy = (pitchY * gap) / 2;
  const radius = (roundness * Math.min(pitchX - ix * 2, pitchY - iy * 2)) / 2;
  for (const c of cells) {
    ctx.save();
    ctx.globalAlpha *= clamp(c.b * alpha);
    ctx.fillStyle = `rgb(${c.color.join(',')})`;
    ctx.beginPath();
    ctx.roundRect(R.x + c.col * pitchX + ix, c.bottom - pitchY + iy, pitchX - ix * 2, pitchY - iy * 2, radius);
    ctx.fill();
    ctx.restore();
  }
}

// Effort, Steps look: five full-width stops lit in the level's colour, a sheen climbing them.
const EFFORT = ['#8E9AAF', '#4C8DFF', '#8B5CF6', '#FF7A45'];
const EFFORT_SPECTRUM = ['#EB5F57', '#F58B57', '#FAC35F', '#91C882', '#82AADC', '#9B82C8', '#C882B4'];
const SWEEP_S = [3.2, 2.5, 1.9, 1.4, 1.05], SWEEP_A = [0.22, 0.28, 0.34, 0.42, 0.5];
export const effortLevel = (v) => clamp(Math.ceil(clamp(v) * 5 - 1e-4) - 1, 0, 4);

export function drawEffort(ctx, R, fillTop, value, time, fillColor = '#FFFFFF', alpha = 1) {
  const level = effortLevel(value);
  const segment = R.h / 5;
  const gap = clamp(segment * 0.1, 1.5, 3.5);
  const round = Math.min(3, (segment - gap) / 2);
  const lit = R.y + R.h - fillTop;
  const sweep = (time / SWEEP_S[level]) % 1;
  if (level >= 3 && lit > 1) {
    const breath = 0.5 + 0.5 * Math.sin((TAU * (time % 1.6)) / 1.6);
    const colour = level >= 4 ? EFFORT_SPECTRUM[Math.floor(time / 2.4 * 7) % 7] : EFFORT[level];
    const cy = (fillTop + R.y + R.h) / 2;
    const g = ctx.createRadialGradient(R.x + R.w / 2, cy, 0, R.x + R.w / 2, cy, lit / 2 + 12);
    g.addColorStop(0, hexA(colour, ((level >= 4 ? 0.3 : 0.22) + 0.22 * breath) * alpha)); g.addColorStop(1, hexA(colour, 0));
    ctx.fillStyle = g; ctx.fillRect(R.x - R.w, fillTop - 12, R.w * 3, lit + 24);
  }
  const bandH = segment * 1.1;
  const bandC = R.y + R.h + bandH / 2 - (lit + bandH) * sweep;
  for (let k = 0; k < 5; k++) {
    const top = R.y + R.h - (k + 1) * segment + gap / 2, bottom = R.y + R.h - k * segment - gap / 2;
    ctx.save(); ctx.globalAlpha *= 0.14 * alpha; ctx.fillStyle = fillColor;
    ctx.beginPath(); ctx.roundRect(R.x, top, R.w, bottom - top, round); ctx.fill(); ctx.restore();
    if (fillTop >= bottom) continue;
    ctx.save();
    ctx.beginPath(); ctx.rect(R.x, Math.max(top, fillTop), R.w, bottom - Math.max(top, fillTop)); ctx.clip();
    if (level >= 4) {
      const span = R.h * 0.9, flow = (time % 2.4) / 2.4;
      const g = ctx.createLinearGradient(0, R.y - flow * span, 0, R.y - flow * span + span);
      EFFORT_SPECTRUM.forEach((c, i) => g.addColorStop(i / 7, c)); g.addColorStop(1, EFFORT_SPECTRUM[0]);
      ctx.fillStyle = g;
    } else ctx.fillStyle = EFFORT[level];
    ctx.globalAlpha *= (k === level ? 1 : 0.74) * alpha;
    ctx.beginPath(); ctx.roundRect(R.x, top, R.w, bottom - top, round); ctx.fill();
    ctx.globalAlpha = alpha * SWEEP_A[level];
    const sg = ctx.createLinearGradient(0, bandC - bandH / 2, 0, bandC + bandH / 2);
    sg.addColorStop(0, 'rgba(255,255,255,0)'); sg.addColorStop(0.5, 'rgba(255,255,255,1)'); sg.addColorStop(1, 'rgba(255,255,255,0)');
    ctx.fillStyle = sg; ctx.fillRect(R.x, bandC - bandH / 2, R.w, bandH);
    ctx.restore();
  }
  const stopTop = R.y + R.h - (level + 1) * segment + gap / 2, stopBottom = R.y + R.h - level * segment - gap / 2;
  if (fillTop > stopTop + 1.5 && fillTop < stopBottom - 1) {
    ctx.save(); ctx.globalAlpha *= 0.9 * alpha; ctx.fillStyle = '#fff';
    ctx.beginPath(); ctx.roundRect(R.x + round * 0.4, fillTop, R.w - round * 0.8, 1.6, 0.8); ctx.fill(); ctx.restore();
  }
}

// Glimmer (from its spec): fine dots brightening toward the level, a handle in the fill colour.
export function drawGlimmer(ctx, R, fillTop, value, time, fillColor = '#FFFFFF', alpha = 1, { handle = true, stops = true } = {}) {
  const pitch = 5.6;
  const cols = clamp(Math.round(R.w / pitch), 2, 10);
  const px = R.w / cols;
  const rows = Math.floor(R.h / pitch);
  const oy = (R.h - rows * pitch) / 2;
  const r = 0.25 * pitch;
  const energy = 0.6 + 0.4 * value;
  const lit0 = [0x88, 0x78, 0xbe], litL = [0x9a, 0x89, 0xc4], peak = [0xdc, 0xd4, 0xff];
  const shimmerY = R.y + R.h - ((time % 2.4) / 2.4) * (R.h + 40) + 20;
  for (let row = 0; row < rows; row++) {
    const cy = R.y + oy + pitch * (row + 0.5);
    for (let col = 0; col < cols; col++) {
      const cx = R.x + px * (col + 0.5);
      let color, a;
      if (cy < fillTop) { color = [255, 255, 255]; a = 0.045; }
      else {
        const k = clamp((R.y + R.h - cy) / Math.max(1, R.y + R.h - fillTop));
        const sm = k * k * (3 - 2 * k);
        const seed = Math.abs(Math.sin((row * 13.1 + col * 7.7) * 12.9898) * 43758.5453) % 1;
        const tw = Math.max(0, Math.sin(TAU * ((time * (1 + Math.floor(seed * 3))) / 2.4 + seed)));
        const twinkle = tw > 0.68 ? (tw - 0.68) / 0.32 : 0;
        const shimmer = Math.exp(-Math.pow((cy - shimmerY) / (0.16 * R.h), 2));
        color = lit0.map((c, i) => Math.round(c + (litL[i] - c) * sm + (peak[i] - litL[i]) * twinkle * 0.8));
        a = (0.045 + (0.52 - 0.045) * sm + 0.3 * twinkle + 0.25 * shimmer * sm) * energy;
      }
      ctx.save(); ctx.globalAlpha *= clamp(a) * alpha; ctx.fillStyle = `rgb(${color.join(',')})`;
      ctx.beginPath(); ctx.arc(cx, cy, r, 0, TAU); ctx.fill(); ctx.restore();
    }
  }
  if (handle) {
    const hh = Math.min(clamp(0.8 * R.w, 10, 30), R.h / 3);
    const cy = clamp(fillTop, R.y + hh / 2, R.y + R.h - hh / 2);
    if (stops) {
      for (let i = 0; i <= 5; i++) {
        const sy = R.y + hh / 2 + (R.h - hh) * (1 - i / 5);
        if (Math.abs(sy - cy) < hh * 0.7) continue;
        ctx.save(); ctx.globalAlpha *= 0.3 * alpha; ctx.fillStyle = fillColor; ctx.beginPath(); ctx.arc(R.x + R.w / 2, sy, 0.375 * pitch, 0, TAU); ctx.fill(); ctx.restore();
      }
    }
    ctx.save(); ctx.globalAlpha *= alpha; ctx.fillStyle = fillColor;
    ctx.beginPath(); ctx.roundRect(R.x, cy - hh / 2, R.w, hh, Math.min(R.w, hh) / 2); ctx.fill(); ctx.restore();
  }
}

function hexA(hex, a) { const n = parseInt(hex.slice(1), 16); return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`; }
