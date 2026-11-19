// A button-animation short: 1080 x 1920, for YouTube Shorts and TikTok. A real-looking phone (a metal
// frame, glass, side keys that go in), a finger pressing its volume keys, and on the screen the app's
// own animation, frame for frame: ButtonBumpView drawn by Robolectric (see shorts.mjs and
// tool/store/bump), laid over the home screen as the overlay window lies over it on a phone.
//
// The narrator (kit/shorts-data.js's SHORT_VOICE) reads four lines; captions follow the words. The
// camera starts on the whole phone, moves in on the keys as they are pressed, and pulls back for
// the end card. The sound is the app's: each press is a click of the key and the animation's own
// sound (res/raw/step_*.wav), plus any sound the animation asks for itself (the balloon's hiss and
// bang), at the frame the app would play it.
import { h, clamp, lerp, Ease, loadIcons, fontsReady, imagesReady } from './core.js';
import { Phone, homeScreen, gvIcon, PHONE_ICONS } from './phone.js';
import { SHORTS, SHORT_VOICE, KEYS, byId } from './shorts-data.js';

export const W = 1080, H = 1920, FPS = 60;
const SCREEN = { w: 412, h: 915 };
const K = 1.42;                                    // dp to px: the screen is 585 px wide
const BEZEL = 12, RIM = 7;                         // black glass round the screen, then the metal
const DEV = { w: Math.round(SCREEN.w * K) + 2 * (BEZEL + RIM), h: Math.round(SCREEN.h * K) + 2 * (BEZEL + RIM) };
const DX = Math.round(W / 2 - DEV.w / 2 - 26), DY = 396;   // a little left of centre: room for the finger
const KEY_OUT = 10, KEY_IN = 5;                    // how far a key stands out of the frame, and travels
const FINGER = 100;                                // the finger's thickness, px
const TAP = 0.1;                                   // a tap's time down

// --- The timeline ---------------------------------------------------------------------------------

// Lines placed, presses on their words, and the end: from the narrator's manifest when there is one
// (word times), else from the script's own times and words spread evenly over a guessed length.
export function resolveShort(id, narration = null, { burstAt = null } = {}) {
  const S = byId(id);
  const lines = S.lines.map((l) => ({ ...l, v: narration ? narration.line(`${id}-${l.key}`, l.text) : null }));
  const len = (l) => (l.v ? l.v.dur : l.text.split(' ').length * 0.32);
  const wordAt = (l, word, nth = 0) => {
    const words = l.v ? l.v.words : spread(l.text, len(l));
    const hits = words.filter(([w]) => w === word);
    if (!hits[nth]) throw new Error(`short ${id}: no "${word}" (#${nth}) in ${l.key}`);
    return l.at + hits[nth][1];
  };
  const byKey = Object.fromEntries(lines.map((l) => [l.key, l]));
  const presses = [];
  for (const p of S.presses) {
    if (p.on) {
      const [key, word, nth = 0] = p.on;
      const l = byKey[key];
      if (l.at == null) throw new Error(`short ${id}: a press on ${key}, which has no time`);
      presses.push({ t: wordAt(l, word, nth) + (p.dt || 0), hold: p.hold ?? TAP });
    } else presses.push({ t: p.at, hold: p.hold ?? TAP });
  }
  presses.sort((a, b) => a.t - b.t);
  let level = S.level;
  presses.forEach((p) => { level = Math.min(0.95, level + 1 / 15); p.level = Math.round(level * 1000) / 1000; p.t = Math.round(p.t * 1000) / 1000; });
  // The end: the card comes up at endAt and the last line is read over it; the balloon's last line
  // ("Pop!") is said as it bursts, BumpEffectsBalloon's FILL_S + SWELL_S into the hold.
  let endAt = S.endAt;
  const last = lines.find((l) => l.end);
  if (S.burstAfter) {
    const held = presses.find((p) => p.hold > TAP);
    // As the renderer reports it once the frames are made; until then, worked out the way it does.
    const burst = burstAt ?? Math.ceil((held.t + S.burstAfter) * FPS) / FPS;
    last.at = burst - 0.08;
    endAt = burst + 0.5;
  } else last.at = endAt + 0.55;
  for (let i = 1; i < lines.length; i++) {
    const a = lines[i - 1];
    if (a.at + len(a) > lines[i].at - 0.05) console.warn(`short ${id}: ${a.key} runs into ${lines[i].key}`);
  }
  const duration = Math.round(Math.max(last.at + len(last) + 1.5, endAt + 4.2) * FPS) / FPS;
  return { S, lines, presses, endAt, duration, len };
}

function spread(text, dur) {
  const words = text.split(' ');
  const total = words.reduce((n, w) => n + w.length + 1, 0);
  let acc = 0;
  return words.map((w) => { const a = (acc / total) * dur; acc += w.length + 1; return [w, a, (acc / total) * dur]; });
}

// The frame renderer's script for a short: key down and up, and the press at each key down, with
// the level it leaves (shorts.mjs writes these into the Robolectric spec).
export function bumpClip(id, R) {
  const events = [];
  for (const p of R.presses) {
    events.push({ t: p.t, type: 'down' }, { t: p.t, type: 'press', button: 'up', level: p.level }, { t: Math.round((p.t + p.hold) * 1000) / 1000, type: 'up' });
  }
  return { name: `short-${id}`, style: R.S.style, duration: R.duration, fps: FPS, events };
}

// --- The scene ------------------------------------------------------------------------------------

export async function buildShort(root, id, { narration = null } = {}) {
  // What the renderer drew, and the sounds the app would have played with it.
  const clipDir = `../build/bump/short-${id}`;
  const meta = await fetch(`${clipDir}/meta.json`, { cache: 'no-store' }).then((r) => (r.ok ? r.json() : null)).catch(() => null);
  const burstAt = meta?.cues?.find((c) => c.cue === 'burst')?.t ?? null;
  const R = resolveShort(id, narration, { burstAt });
  const { S, lines, presses, endAt, duration } = R;
  await loadIcons([...new Set(PHONE_ICONS)]);
  await fontsReady();
  root.style.cssText = `position:relative;width:${W}px;height:${H}px;overflow:hidden;background:${S.base[1]}`;

  // --- Backdrop: the short's colours, soft glows drifting, a little grain ---------------------------
  const bg = document.createElement('canvas');
  bg.width = W / 4; bg.height = H / 4;
  bg.style.cssText = `position:absolute;left:0;top:0;width:${W}px;height:${H}px`;
  root.appendChild(bg);
  const glows = S.glows.map(([c, a], i) => ({ c, a, x: [0.2, 0.85, 0.5][i % 3], y: [0.18, 0.42, 0.85][i % 3], r: [0.55, 0.5, 0.6][i % 3], ph: i * 2.1 }));
  const drawBg = (t) => {
    const c = bg.getContext('2d'), w = bg.width, hh = bg.height;
    const g = c.createLinearGradient(0, 0, w * 0.4, hh);
    g.addColorStop(0, S.base[0]); g.addColorStop(1, S.base[1]);
    c.globalCompositeOperation = 'source-over'; c.fillStyle = g; c.fillRect(0, 0, w, hh);
    c.globalCompositeOperation = 'screen';
    for (const o of glows) {
      const x = (o.x + 0.05 * Math.sin(t * 0.35 + o.ph)) * w, y = (o.y + 0.04 * Math.cos(t * 0.3 + o.ph)) * hh, r = o.r * Math.max(w, hh);
      const rg = c.createRadialGradient(x, y, 0, x, y, r);
      rg.addColorStop(0, rgba(o.c, o.a)); rg.addColorStop(0.5, rgba(o.c, o.a * 0.35)); rg.addColorStop(1, rgba(o.c, 0));
      c.fillStyle = rg; c.fillRect(0, 0, w, hh);
    }
  };

  // --- The phone: the app's home screen, the animation's frames over it, in a metal frame ------------
  const world = h(`<div style="position:absolute;left:0;top:0;width:${W}px;height:${H}px;transform-origin:0 0"></div>`);
  root.appendChild(world);
  const floor = h(`<div style="position:absolute;left:${DX + 40}px;top:${DY + DEV.h - 40}px;width:${DEV.w - 80}px;height:80px;border-radius:50%;
      background:radial-gradient(closest-side,rgba(0,0,0,.55),rgba(0,0,0,0));filter:blur(18px)"></div>`);
  world.appendChild(floor);
  const device = h(`<div style="position:absolute;left:${DX}px;top:${DY}px;width:${DEV.w}px;height:${DEV.h}px"></div>`);
  world.appendChild(device);
  // Side keys first, so the frame's edge covers their inner end.
  const keyY = (k) => BEZEL + RIM + KEYS[k].position * SCREEN.h * K;
  const keyLen = (k) => KEYS[k].lengthDp * K;
  const keys = {};
  for (const k of ['up', 'down', 'power']) {
    const el = h(`<div style="position:absolute;left:${DEV.w - KEY_OUT - 2}px;top:${keyY(k) - keyLen(k) / 2}px;width:${KEY_OUT + 4}px;height:${keyLen(k)}px;
        border-radius:0 8px 8px 0;background:linear-gradient(90deg,#55585f 0%,#9da1a8 28%,#eef0f3 55%,#b4b8be 76%,#686b72 100%);
        box-shadow:3px 0 4px rgba(0,0,0,.4),inset 0 2px 1px rgba(255,255,255,.55),inset 0 -2px 1px rgba(0,0,0,.28)"></div>`);
    device.appendChild(el);
    keys[k] = el;
  }
  // The frame: a titanium band, lit along its curve, its edge catching the light; antenna lines.
  const body = h(`<div style="position:absolute;inset:0;border-radius:86px;
      background:linear-gradient(158deg,#eceef1 0%,#a6aab1 16%,#6f737b 34%,#c3c6cb 55%,#80848c 76%,#dcdfe3 100%);
      box-shadow:0 46px 90px rgba(0,0,0,.5),0 14px 34px rgba(0,0,0,.35),inset 0 0 0 1px rgba(255,255,255,.7),inset 0 0 0 3px rgba(0,0,0,.18)">
      <div style="position:absolute;left:${DEV.w - 3}px;top:205px;width:3px;height:6px;background:#3b3e44"></div>
      <div style="position:absolute;left:0;top:${DEV.h - 240}px;width:3px;height:6px;background:#3b3e44"></div>
      <div style="position:absolute;left:150px;top:${DEV.h - 3}px;width:6px;height:3px;background:#3b3e44"></div>
      <div style="position:absolute;left:${DEV.w - 155}px;top:0;width:6px;height:3px;background:#3b3e44"></div></div>`);
  device.appendChild(body);
  const bezel = h(`<div style="position:absolute;left:${RIM}px;top:${RIM}px;right:${RIM}px;bottom:${RIM}px;border-radius:${86 - RIM}px;background:#040405;
      box-shadow:0 0 0 1px rgba(0,0,0,.55),inset 0 0 0 1px rgba(255,255,255,.07)"></div>`);
  device.appendChild(bezel);
  const glass = h(`<div style="position:absolute;left:${BEZEL}px;top:${BEZEL}px;width:${SCREEN.w * K}px;height:${SCREEN.h * K}px;border-radius:66px;overflow:hidden;isolation:isolate"></div>`);
  bezel.appendChild(glass);
  const phone = new Phone({ wallpaper: S.wallpaper, darkIcons: !!S.darkIcons, scale: K });
  phone.below.appendChild(homeScreen({ date: 'Thursday, November 19', labels: true }));
  glass.appendChild(phone.el);
  // The overlay window: the whole screen, over everything on it, the status bar included.
  const fx = h(`<img alt="" style="position:absolute;left:0;top:0;width:${SCREEN.w}px;height:${SCREEN.h}px;z-index:80;pointer-events:none">`);
  phone.el.appendChild(fx);
  // The glass's own sheen, and the camera hole's lens.
  glass.appendChild(h(`<div style="position:absolute;inset:0;z-index:5;pointer-events:none;
      background:linear-gradient(115deg,rgba(255,255,255,.10) 0%,rgba(255,255,255,.03) 28%,rgba(255,255,255,0) 42%,rgba(255,255,255,0) 70%,rgba(255,255,255,.05) 100%)"></div>`));

  // The finger: an index finger from the right, seen from above, pressing the key with its tip.
  const finger = h(`<div style="position:absolute;left:0;top:0;width:0;height:0;z-index:10">${fingerSvg()}</div>`);
  world.appendChild(finger);
  const fingerEl = finger.firstElementChild;

  // --- Captions and the end card (not moved by the camera) ---------------------------------------------
  const scrim = h(`<div style="position:absolute;left:0;top:0;width:${W}px;height:470px;background:linear-gradient(180deg,rgba(0,0,0,.42),rgba(0,0,0,0));pointer-events:none"></div>`);
  root.appendChild(scrim);
  const capBox = h(`<div style="position:absolute;left:60px;right:60px;top:140px;height:240px;display:flex;align-items:center;justify-content:center;text-align:center"></div>`);
  root.appendChild(capBox);
  const caps = lines.filter((l) => !l.end).map((l) => {
    const words = l.v ? l.v.words : spread(l.text, R.len(l));
    const el = h(`<div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;opacity:0">
        <div style="padding:18px 30px 22px;border-radius:34px;background:rgba(10,8,30,.58);box-shadow:0 12px 40px rgba(0,0,0,.25);
            font:800 62px/1.14 'Plus Jakarta Sans';letter-spacing:-.02em;color:#fff;text-wrap:balance;text-shadow:0 3px 14px rgba(0,0,0,.35)">
        ${words.map(([w]) => `<span class="w" style="display:inline-block;margin:0 .12em">${w}</span>`).join(' ')}</div></div>`);
    capBox.appendChild(el);
    return { l, el, spans: [...el.querySelectorAll('.w')], words, a: l.at - 0.08, b: l.at + R.len(l) + 0.45 };
  });
  for (let i = 0; i + 1 < caps.length; i++) caps[i].b = Math.min(caps[i].b, caps[i + 1].a);

  const card = h(`<div style="position:absolute;left:0;right:0;top:210px;display:flex;flex-direction:column;align-items:center;text-align:center;opacity:0">
      <div class="i">${gvIcon(158, 'squircle')}</div>
      <div class="n" style="margin-top:34px;font:800 96px/1 'Plus Jakarta Sans';letter-spacing:-.035em;color:#fff">Gesture Volume</div>
      <div class="s" style="margin-top:22px;font:600 40px/1.25 'Plus Jakarta Sans';color:rgba(255,255,255,.86)">Button animation · <span style="color:${S.hl}">${S.name}</span></div>
      <div class="p" style="margin-top:34px;display:inline-flex;align-items:center;gap:14px;padding:20px 38px;border-radius:999px;background:#fff;
          font:800 38px/1 'Plus Jakarta Sans';color:#1A1150;box-shadow:0 14px 40px rgba(0,0,0,.35)">${playTriangle()}Free on Google Play</div></div>`);
  root.appendChild(card);
  const cardBits = ['.i', '.n', '.s', '.p'].map((q) => card.querySelector(q));

  // --- Motion ---------------------------------------------------------------------------------------------
  const first = presses[0].t;
  const fingerIn = first - 0.95, fingerOut = endAt - 0.1;
  const camIn = Math.max(0.5, first - 1.25);
  const C0 = { fx: 0.5, fy: 0.5, s: 1 };
  const C1 = { fx: S.cam.fx, fy: S.cam.fy, s: S.cam.s, px: 540, py: S.cam.py ?? 1030 };
  const C2 = { fx: 0.5, fy: 0.5, s: 0.6, px: 540, py: 1350 };
  const P0 = { px: DX + DEV.w * 0.5, py: DY + DEV.h * 0.5 };
  const camAt = (t) => {
    const a = Ease.inOut(clamp((t - camIn) / 1.15));
    const b = Ease.inOut(clamp((t - endAt) / 0.95));
    const st = {};
    for (const k of ['fx', 'fy', 's']) st[k] = lerp(lerp(C0[k], C1[k], a), C2[k], b);
    st.px = lerp(lerp(P0.px, C1.px, a), C2.px, b);
    st.py = lerp(lerp(P0.py, C1.py, a), C2.py, b);
    return st;
  };
  const upY = DY + keyY('up');
  const keyFace = DX + DEV.w + KEY_OUT - 2;          // where the key's outer face is, at rest
  // The fingertip's x: in from the right, a hover beside the key, down onto it at each press.
  const tipX = (t) => {
    const hover = keyFace + 9, away = keyFace + 640;
    let x = hover;
    if (t < fingerIn + 0.75) x = lerp(away, hover, Ease.emphasizedDecel(clamp((t - fingerIn) / 0.75)));
    if (t > fingerOut) x = lerp(hover, away + 100, Ease.emphasizedAccel(clamp((t - fingerOut) / 0.6)));
    for (const p of presses) {
      const d = t - p.t;
      if (d < -0.09 || d > p.hold + 0.2) continue;
      const press = d < 0 ? Ease.emphasizedAccel(clamp((d + 0.09) / 0.09)) * 9
        : d < p.hold ? 9 + KEY_IN * Ease.emphasizedDecel(clamp(d / 0.035))
          : (9 + KEY_IN) * (1 - Ease.emphasizedDecel(clamp((d - p.hold) / 0.2)));
      x = Math.min(x, hover - press);
    }
    return x;
  };
  // On the key's lower half, so its top is seen going in; and a gentle bob, as a hand never holds still.
  const tipY = (t) => upY + keyLen('up') * 0.36 + 3 * Math.sin(t * 2.3) + 1.5 * Math.sin(t * 5.1 + 1);

  // --- Sound -------------------------------------------------------------------------------------------
  const cues = [];
  const sound = meta?.sound || null;
  for (const p of presses) {
    cues.push({ t: p.t, sfx: 'click', gain: 0.9, pan: 0.25 });
    cues.push({ t: p.t + p.hold, sfx: 'clickup', gain: 0.55, pan: 0.25 });
    if (sound) cues.push({ t: p.t, sfx: `app:${sound}`, gain: 1.0 });
  }
  if (meta) {
    // The sounds the animation asked for itself: "inflate" is the hiss, stopped by "inflate_stop";
    // anything else is a sample of that name, once (OverlayController.playBumpCue).
    const cs = meta.cues || [];
    cs.forEach((c, i) => {
      if (c.cue === 'inflate_stop') return;
      if (c.cue === 'inflate') {
        const stop = cs.slice(i + 1).find((d) => d.cue === 'inflate_stop' || d.cue === 'burst');
        cues.push({ t: c.t, sfx: 'app:inflate', gain: 0.8, dur: stop ? stop.t - c.t : undefined });
      } else cues.push({ t: c.t, sfx: `app:${c.cue}`, gain: 1.0 });
    });
  }
  cues.push({ t: endAt + 0.05, sfx: 'whoosh', gain: 0.45 });

  const voice = lines.filter((l) => l.v).map((l) => ({ key: `${id}-${l.key}`, t: Math.round(l.at * 1000) / 1000, wav: l.v.wav, dur: l.v.dur }));

  // --- A frame ---------------------------------------------------------------------------------------------
  let shown = null;
  async function seek(t) {
    drawBg(t);
    const c = camAt(t);
    const Fx = DX + DEV.w * c.fx, Fy = DY + DEV.h * c.fy;
    world.style.transform = `translate(${c.px - Fx * c.s}px,${c.py - Fy * c.s}px) scale(${c.s})`;
    // Keys: in as far as the finger pushes them.
    const tx = tipX(t);
    for (const [k, el] of Object.entries(keys)) {
      const inBy = k === 'up' ? clamp(keyFace - tx, 0, KEY_IN) : 0;
      el.style.transform = `translateX(${-inBy}px)`;
      el.style.filter = inBy > 0.5 ? `brightness(${1 - 0.12 * inBy / KEY_IN})` : '';
    }
    finger.style.transform = `translate(${tx}px,${tipY(t)}px)`;
    finger.style.opacity = t < fingerIn || t > fingerOut + 0.7 ? 0 : 1;
    // The animation's frame for this moment.
    const n = Math.min(Math.round(t * FPS), (meta?.frames ?? Infinity) - 1);
    const src = meta ? `${clipDir}/${String(Math.max(0, n)).padStart(5, '0')}.png` : null;
    if (src !== shown) {
      shown = src;
      if (src) { fx.src = src; await fx.decode().catch(() => {}); fx.style.display = ''; } else fx.style.display = 'none';
    }
    // Captions: the line being read, its words lighting up as they are said.
    for (const cp of caps) {
      const k = Math.min(clamp((t - cp.a) / 0.18), 1 - clamp((t - (cp.b - 0.16)) / 0.16));
      cp.el.style.opacity = k;
      cp.el.style.transform = `translateY(${(1 - Ease.emphasizedDecel(clamp((t - cp.a) / 0.3))) * 26}px) scale(${lerp(0.94, 1, Ease.outBack(clamp((t - cp.a) / 0.3)))})`;
      if (k <= 0) continue;
      cp.spans.forEach((sp, i) => {
        const [, a, b] = cp.words[i];
        const at = cp.l.at + a, end = cp.l.at + b;
        const now = t >= at - 0.03 && t < Math.max(end, at + 0.12) + 0.05;
        sp.style.color = now ? S.hl : t >= at ? '#fff' : 'rgba(255,255,255,.5)';
        sp.style.transform = now ? 'scale(1.07)' : '';
      });
    }
    scrim.style.opacity = 1 - clamp((t - endAt) / 0.4) * 0.6;
    // The end card.
    const e = t - endAt;
    card.style.opacity = clamp((e - 0.45) / 0.3);
    cardBits[0].style.transform = `scale(${Math.max(0, springy(e - 0.45))})`;
    cardBits.slice(1).forEach((el, i) => {
      const d = e - 0.62 - i * 0.12;
      el.style.opacity = clamp(d / 0.3);
      el.style.transform = `translateY(${(1 - Ease.emphasizedDecel(clamp(d / 0.5))) * 34}px)`;
    });
  }

  await imagesReady(root);
  return {
    width: W, height: H, duration, cues, music: 'chill', musicGain: 0.5, seek,
    voiceLines: lines.map((l) => ({ key: `${id}-${l.key}`, text: l.text, ...SHORT_VOICE })),
    voice, narrator: narration ? narration.narrator : null,
    bump: bumpClip(id, R), presses, endAt,
  };
}

const springy = (t) => (t <= 0 ? 0 : 1 - Math.exp(-7 * t) * Math.cos(9 * t));
function rgba(hex, a) { const n = parseInt(hex.slice(1), 16); return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`; }
const playTriangle = () => `<svg width="30" height="30" viewBox="0 0 24 24"><path d="M7 4.5v15l12.5-7.5z" fill="#1A1150"/></svg>`;

// An index finger seen from above, pointing left, its tip at (0, 0): skin lit across its curve, the
// nail with its free edge and half-moon, the creases over the joints. FINGER px thick.
function fingerSvg() {
  const u = FINGER / 100;
  const shape = 'M 780,-62 C 600,-60 300,-56 150,-52 C 100,-51 60,-50 44,-49 C 18,-48 0,-26 0,0 C 0,26 18,48 44,49 C 60,50 100,52 150,54 C 300,58 600,62 780,64 Z';
  return `<svg width="${780 * u}" height="${150 * u}" viewBox="0 -75 780 150" style="position:absolute;left:0;top:${-75 * u}px;overflow:visible;
      filter:drop-shadow(0 22px 24px rgba(0,0,0,.36)) drop-shadow(0 5px 7px rgba(0,0,0,.22))">
    <defs>
      <linearGradient id="fgSkin" x1="0" y1="-60" x2="0" y2="62" gradientUnits="userSpaceOnUse">
        <stop offset="0" stop-color="#A86B52"/><stop offset=".12" stop-color="#CF977A"/><stop offset=".36" stop-color="#EDC2A8"/>
        <stop offset=".5" stop-color="#F2CAB1"/><stop offset=".7" stop-color="#E2AD90"/><stop offset=".9" stop-color="#BF8467"/><stop offset="1" stop-color="#9B6149"/></linearGradient>
      <radialGradient id="fgTip" cx="14" cy="0" r="62" gradientUnits="userSpaceOnUse">
        <stop offset="0" stop-color="#E58C82" stop-opacity=".42"/><stop offset="1" stop-color="#E58C82" stop-opacity="0"/></radialGradient>
      <linearGradient id="fgNail" x1="0" y1="-30" x2="0" y2="30" gradientUnits="userSpaceOnUse">
        <stop offset="0" stop-color="#D9A496"/><stop offset=".35" stop-color="#F3D3C9"/><stop offset=".55" stop-color="#F6DAD0"/><stop offset="1" stop-color="#D59D8E"/></linearGradient>
      <linearGradient id="fgHand" x1="520" y1="0" x2="780" y2="0" gradientUnits="userSpaceOnUse">
        <stop offset="0" stop-color="#6E3F2C" stop-opacity="0"/><stop offset="1" stop-color="#6E3F2C" stop-opacity=".3"/></linearGradient>
      <linearGradient id="fgSheen" x1="0" y1="-40" x2="0" y2="10" gradientUnits="userSpaceOnUse">
        <stop offset="0" stop-color="#fff" stop-opacity="0"/><stop offset=".5" stop-color="#fff" stop-opacity=".16"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>
    </defs>
    <path d="${shape}" fill="url(#fgSkin)"/>
    <path d="${shape}" fill="url(#fgTip)"/>
    <path d="${shape}" fill="url(#fgHand)"/>
    <path d="M 150,-34 C 330,-38 560,-42 780,-44 L 780,-20 C 560,-18 330,-16 150,-14 Z" fill="url(#fgSheen)"/>
    <path d="M 58,-29 L 24,-29 C 13,-29 8,-17 8,0 C 8,17 13,29 24,29 L 58,29 C 64,29 66,20 66,0 C 66,-20 64,-29 58,-29 Z" fill="url(#fgNail)"/>
    <path d="M 58,-29 L 24,-29 C 13,-29 8,-17 8,0 C 8,17 13,29 24,29 L 58,29 C 64,29 66,20 66,0 C 66,-20 64,-29 58,-29 Z" fill="none" stroke="#9E6550" stroke-opacity=".42" stroke-width="1.4"/>
    <path d="M 18,-25 C 12,-18 11,-9 11,0 C 11,9 12,18 18,25 C 16,16 15.5,8 15.5,0 C 15.5,-8 16,-16 18,-25 Z" fill="#FFF7F3" fill-opacity=".92"/>
    <path d="M 62,-14 C 57,-9 56,-4 56,0 C 56,4 57,9 62,14 C 63.5,8 63.5,-8 62,-14 Z" fill="#FDEFEA" fill-opacity=".75"/>
    <path d="M 24,-13 C 33,-16 46,-16 55,-13" fill="none" stroke="#fff" stroke-opacity=".62" stroke-width="3" stroke-linecap="round"/>
    <path d="M 70,-35 C 74,-26 75,-14 74,-5 M 75,6 C 76,16 74,26 70,35" fill="none" stroke="#A36B55" stroke-opacity=".38" stroke-width="1.5" stroke-linecap="round"/>
    <g fill="none" stroke="#93593F" stroke-opacity=".34" stroke-width="1.8" stroke-linecap="round">
      <path d="M 122,-42 C 129,-30 130,-16 125,-5"/><path d="M 131,-41 C 139,-28 140,-13 134,-1"/><path d="M 127,8 C 132,18 131,30 125,42"/>
      <path d="M 268,-48 C 279,-32 281,-14 274,0"/><path d="M 281,-48 C 293,-30 296,-10 287,6"/><path d="M 294,-47 C 306,-28 309,-8 300,9"/>
      <path d="M 276,12 C 285,24 285,38 277,52"/><path d="M 291,15 C 299,27 299,41 291,55"/></g>
    <path d="M 44,-49 C 100,-51 300,-56 780,-62" fill="none" stroke="#fff" stroke-opacity=".1" stroke-width="2"/>
  </svg>`;
}
