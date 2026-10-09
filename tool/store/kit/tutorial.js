// The tutorials: one short video per feature, 1920 x 1080 for YouTube. A tutorial is a script of
// steps (a title and a sentence or two, shown beside the phone) and what happens on the phone
// during each. This file does the rest: the title card, the captions and progress, screens that
// come and go the way the app moves them, the hand, the camera, the sound, the chapters and the
// subtitles.
import { h, icon, loadIcons, fontsReady, imagesReady, clamp, lerp, Ease, spring } from './core.js';
import { Phone, homeScreen, gvIcon, PHONE_ICONS } from './phone.js';
import { Backdrop, showBetween } from './stage.js';
import { HandScript, Camera, prog } from './director.js';
import { handSvg, setHand } from './hand.js';
import { M3_ICONS, demoCaption } from './m3.js';
import { thumbOverlay, thumbTransform } from './thumb.js';

export const SERIES = [
  { id: 'getting-started', title: 'Getting started', sub: 'Set it up and give it the one permission it needs' },
  { id: 'swipe-volume', title: 'Volume with a swipe', sub: 'The round button: swipe it, tap it, move it' },
  { id: 'quick-slider', title: 'The Quick slider', sub: 'A volume and brightness track that follows your finger' },
  { id: 'deck', title: 'The Deck', sub: 'Your apps, people and tools, one swipe in from the bar' },
  { id: 'search', title: 'Search in the Deck', sub: 'Apps, contacts, sums and the web, as you type' },
  { id: 'menu', title: 'The long-press menu', sub: 'Hold the bar for the actions you use most' },
  { id: 'actions', title: 'Choose what each gesture does', sub: 'Taps, holds and swipes, for every app or just one' },
  { id: 'appearance', title: 'Make the bar yours', sub: 'Presets, size, shape, colour and where it sits' },
  { id: 'visibility', title: 'Keep it out of the way', sub: 'Hide the bar in some apps, while you type, or until media plays' },
];

export const W = 1920, H = 1080;
export const INTRO = 3.9;            // steps start here
const OUTRO = 5.2;                   // the "Up next" card
const F = { x: 1392, y: 540 };       // where the camera's focus lands on the frame
export const BASE = { cx: 206, cy: 457, s: 1.0 };
const ACCENT = '#A5B4FC', SOFT = '#C9C2FF';

// Forward and back between the app's screens (ui/motion/ScreenTransitions.kt): the new screen
// slides in a fifth of the width on spring(0.8, 380) and fades in on spring(1, 1600); the old one
// slides a tenth the other way and fades out. Back is the mirror, with the leaving screen on top.
const offset = (dt) => spring(dt, { dampingRatio: 0.8, stiffness: 380 });
const fade = (dt) => clamp(spring(dt, { dampingRatio: 1, stiffness: 1600 }));
const PRESS_IN = (dt) => spring(dt, { dampingRatio: 0.9, stiffness: 1400 });
const RELEASE = (dt) => spring(dt, { dampingRatio: 0.32, stiffness: 420 });

// Where an element sits on the phone, in dp (measured while the frame is at scale 1).
export function rectOf(el, phoneEl) {
  const a = el.getBoundingClientRect(), p = phoneEl.getBoundingClientRect();
  return { x: a.left - p.left, y: a.top - p.top, w: a.width, h: a.height, cx: a.left - p.left + a.width / 2, cy: a.top - p.top + a.height / 2 };
}

class Timeline {
  constructor(phone, frame, layer) {
    this.phone = phone; this.frame = frame; this.layer = layer;
    this.t = INTRO + 0.25;
    this.steps = []; this.handSteps = []; this.camKeys = [{ at: 0, ...BASE }]; this.cueList = [];
    this.updates = []; this.screens = []; this.marks = []; this.squeezes = []; this.chapterName = null;
  }

  // --- The script ---------------------------------------------------------------------------------
  chapter(title) { this.chapterName = title; return this; }
  step(title, text, dur) {
    const s = { i: this.steps.length, at: this.t, end: this.t + dur, title, text, chapter: this.chapterName };
    this.steps.push(s);
    this.t += dur;
    return s;
  }
  wait(d) { this.t += d; return this; }
  hand(...steps) { this.handSteps.push(...steps); return this; }
  cam(key) { this.camKeys.push(key); return this; }
  cue(t, sfx, gain = 0.6) { this.cueList.push({ t, sfx, gain }); return this; }
  on(fn) { this.updates.push(fn); return this; }

  // A screen on top from `at`: kind 'push' | 'pop' | 'fade' | 'cut' | 'launch' | 'home' (back to
  // the launcher; el = null). `from` is the launcher icon's rect for 'launch' and 'home'.
  show(at, el, kind = 'push', { from = null, dark = true } = {}) {
    if (el && !el.isConnected) { el.style.visibility = 'hidden'; this.phone.below.appendChild(el); }
    this.screens.push({ at, el, kind, from, dark });
    return el;
  }

  // The rect / centre of an element's [data-target=name] (or of el itself), in dp. `scroll` is
  // the amount its scroll column will have moved by then.
  rect(el, name, scroll = 0) {
    const tgt = name ? el.querySelector(`[data-target="${name}"]`) : el;
    if (!tgt) throw new Error(`no data-target "${name}"`);
    const hidden = el.style.visibility;
    const r = rectOf(tgt, this.phone.el);
    el.style.visibility = hidden;
    return { ...r, y: r.y - scroll, cy: r.cy - scroll };
  }
  at(el, name, scroll = 0) { const r = this.rect(el, name, scroll); return [r.cx, r.cy]; }

  // Measures a target in an element that is not on the phone yet (a dialog, a later state).
  measure(el, name, scroll = 0) {
    const attached = el.isConnected;
    if (!attached) { el.style.visibility = 'hidden'; this.phone.below.appendChild(el); }
    const r = this.rect(el, name, scroll);
    if (!attached) el.remove();
    return r;
  }

  // Opening the app from its launcher icon, and going home with the gesture pill.
  launch(at, el) {
    const r = this.iconRect;
    this.handSteps.push({ at, tap: [r.cx, r.cy] });
    this.show(at + 0.16, el, 'launch', { from: r });
    return at + 0.16;
  }
  goHome(at, from = this.iconRect) {
    this.handSteps.push({ at, swipe: [206, 902], to: [206, 760], dur: 0.32, sound: 'whoosh' });
    this.show(at + 0.3, null, 'home', { from });
    return at + 0.3;
  }
  // Another app's launcher icon, by its label on the home screen.
  appIconRect(label) {
    const span = [...this.launcher.querySelectorAll('span')].find((s) => s.textContent === label);
    return rectOf(span.parentElement.firstElementChild, this.phone.el);
  }

  // A tap on a target: the hand taps, the target squeezes and springs back (PressBounce).
  tap(at, el, name, { scroll = 0, sound = 'tap', dx = 0, dy = 0 } = {}) {
    const [x, y] = this.at(el, name, scroll);
    this.handSteps.push({ at, tap: [x + dx, y + dy], sound });
    this.squeezes.push({ at: at + 0.02, el, name });
    return [x + dx, y + dy];
  }

  // A soft ring around a target, to point at it; and the app's own caption pill.
  ring(at, dur, r, { pad = 6, radius = 18 } = {}) {
    const el = h(`<div style="position:absolute;left:${r.x - pad}px;top:${r.y - pad}px;width:${r.w + pad * 2}px;height:${r.h + pad * 2}px;border-radius:${radius}px;
        box-shadow:0 0 0 3px #8B7CFF,0 0 22px 6px rgba(139,124,255,.55);opacity:0"></div>`);
    this.layer.appendChild(el);
    this.marks.push({ el, at, dur, kind: 'ring' });
  }
  pill(at, dur, text, x, y, glyph = '') {
    const el = h(`<div style="position:absolute;left:${x}px;top:${y}px;opacity:0;white-space:nowrap">${demoCaption(text, glyph)}</div>`);
    this.layer.appendChild(el);
    this.marks.push({ el, at, dur, kind: 'pill' });
  }
}

// Swaps a container's content when the state the script asks for changes: for screens rebuilt
// from a state object (a switch part-way, a typed query, a new selection). `make(state)` returns
// an element; `stateAt(t)` the state.
export function live(make, stateAt, init) {
  const box = h(`<div class="live" style="position:absolute;inset:0"></div>`);
  let key = null;
  box.render = (t) => {
    const s = stateAt(t);
    const k = JSON.stringify(s);
    if (k === key) return;
    key = k;
    box.replaceChildren(make(s));
  };
  box.replaceChildren(make(init ?? stateAt(0)));
  return box;
}

export async function buildTutorial(root, id, script, { icons = [], prepare = null, wallpaper = 'dusk', thumb = null } = {}) {
  const n = SERIES.findIndex((s) => s.id === id) + 1;
  const meta = SERIES[n - 1], next = SERIES[n] || null;
  await loadIcons([...new Set([...PHONE_ICONS, ...M3_ICONS, ...icons])]);
  if (prepare) await prepare();
  await fontsReady();
  root.style.cssText = `position:relative;width:${W}px;height:${H}px;overflow:hidden;background:#100B33`;

  const bd = new Backdrop(W, H, { seed: 40 + n });
  root.appendChild(bd.el);

  // --- The phone ----------------------------------------------------------------------------------
  const world = h(`<div style="position:absolute;inset:0"></div>`);
  root.appendChild(world);
  const frame = h(`<div style="position:absolute;left:0;top:0;transform-origin:0 0;padding:7px;border-radius:53px;background:rgba(255,255,255,.92);
      box-shadow:0 40px 90px rgba(8,5,30,.55),0 10px 30px rgba(8,5,30,.35)"></div>`);
  // Side keys, so a video can press them: power, then volume up and down, on the right.
  const key = (y, hgt) => h(`<div style="position:absolute;left:424px;top:${y}px;width:5px;height:${hgt}px;border-radius:0 3px 3px 0;background:rgba(232,230,245,.95);transform-origin:0 50%"></div>`);
  const keys = { power: key(190, 58), up: key(286, 84), down: key(376, 84) };
  Object.values(keys).forEach((k) => frame.appendChild(k));
  const phone = new Phone({ wallpaper, radius: 46 });
  frame.appendChild(phone.el);
  world.appendChild(frame);
  const launcher = homeScreen();
  phone.below.appendChild(launcher);
  const layer = h(`<div style="position:absolute;left:7px;top:7px;width:412px;height:915px;pointer-events:none;z-index:4"></div>`);
  const handWrap = h(`<div style="position:absolute;left:7px;top:7px;width:412px;height:915px;pointer-events:none;z-index:5">${handSvg(412, 915)}</div>`);
  frame.append(layer, handWrap);
  const hand = handWrap.querySelector('svg');

  // --- The script ---------------------------------------------------------------------------------
  const T = new Timeline(phone, frame, layer);
  T.keys = keys; T.launcher = launcher;
  T.iconRect = rectOf(launcher.querySelector('.gv-icon'), phone.el);
  await script(T);
  await imagesReady(root);
  const stepsEnd = T.t + 0.3;
  const duration = stepsEnd + OUTRO;
  const script_ = new HandScript(T.handSteps);
  const cam = new Camera([...T.camKeys].sort((a, b) => a.at - b.at));
  T.screens.sort((a, b) => a.at - b.at);

  // --- Words beside the phone -----------------------------------------------------------------------
  const header = h(`<div style="position:absolute;left:130px;top:64px;display:flex;align-items:center;gap:18px;opacity:0">
      ${gvIcon(54, 'squircle')}<div style="font:800 30px/1 'Plus Jakarta Sans';letter-spacing:-.01em;color:#fff;white-space:nowrap">Gesture Volume</div>
      <div style="font:500 26px/1 'Plus Jakarta Sans';color:${SOFT};white-space:nowrap">· ${meta.title}</div></div>`);
  root.appendChild(header);
  const N = T.steps.length;
  const caps = T.steps.map((s) => {
    const outer = h(`<div style="position:absolute;left:130px;width:700px;top:150px;bottom:150px;display:flex;align-items:center;pointer-events:none">
        <div class="in">
          <div style="font:700 24px/1 'Plus Jakarta Sans';letter-spacing:.12em;text-transform:uppercase;color:${ACCENT}">Step ${s.i + 1} of ${N}</div>
          <div style="margin-top:22px;font:800 66px/1.06 'Plus Jakarta Sans';letter-spacing:-.025em;color:#fff;text-wrap:balance">${s.title}</div>
          <div style="margin-top:26px;font:500 33px/1.42 'Plus Jakarta Sans';color:${SOFT};text-wrap:pretty">${s.text}</div>
        </div></div>`);
    root.appendChild(outer);
    return { el: outer.querySelector('.in'), a: s.at + 0.05, b: s.end - 0.1 };
  });
  const bar = h(`<div style="position:absolute;left:130px;top:968px;width:700px;display:flex;gap:8px;opacity:0">
      ${T.steps.map(() => `<div style="flex:1;height:6px;border-radius:3px;background:rgba(255,255,255,.14);overflow:hidden"><div class="f" style="height:6px;width:0;background:#8B7CFF;border-radius:3px"></div></div>`).join('')}</div>`);
  root.appendChild(bar);
  const fills = [...bar.querySelectorAll('.f')];

  // Title card and the "Up next" card.
  const title = h(`<div style="position:absolute;left:0;right:0;top:0;bottom:0;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center">
      <div class="i">${gvIcon(150, 'squircle')}</div>
      <div class="k" style="margin-top:38px;font:700 28px/1 'Plus Jakarta Sans';letter-spacing:.14em;text-transform:uppercase;color:${ACCENT}">Gesture Volume tutorial ${n} of ${SERIES.length}</div>
      <div class="n" style="margin-top:26px;font:800 110px/1.02 'Plus Jakarta Sans';letter-spacing:-.035em;color:#fff;padding:0 120px;text-wrap:balance">${meta.title}</div>
      <div class="g" style="margin-top:30px;font:500 42px/1.3 'Plus Jakarta Sans';color:${SOFT};padding:0 200px;text-wrap:balance">${meta.sub}</div></div>`);
  root.appendChild(title);
  const outro = h(`<div style="position:absolute;left:0;right:0;top:0;bottom:0;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;opacity:0">
      <div class="i">${gvIcon(120, 'squircle')}</div>
      <div class="k" style="margin-top:40px;font:700 28px/1 'Plus Jakarta Sans';letter-spacing:.14em;text-transform:uppercase;color:${ACCENT}">${next ? 'Up next' : 'That is the whole series'}</div>
      <div class="n" style="margin-top:26px;font:800 96px/1.04 'Plus Jakarta Sans';letter-spacing:-.03em;color:#fff;padding:0 160px;text-wrap:balance">${next ? next.title : 'Thanks for watching'}</div>
      <div class="g" style="margin-top:28px;font:500 40px/1.3 'Plus Jakarta Sans';color:${SOFT};padding:0 220px;text-wrap:balance">${next ? next.sub : 'Gesture Volume: volume, brightness and everything else, on the edge of your screen'}</div></div>`);
  root.appendChild(outro);

  // --- Sound ----------------------------------------------------------------------------------------
  const cues = [...script_.cues(), ...T.cueList, { t: INTRO - 0.75, sfx: 'whoosh', gain: 0.35 }, { t: stepsEnd + 0.1, sfx: 'whoosh', gain: 0.3 }];

  // --- Chapters and subtitles -------------------------------------------------------------------------
  const chapters = [];
  for (const s of T.steps) {
    const name = s.chapter || s.title;
    if (!chapters.length || chapters[chapters.length - 1].title !== name) chapters.push({ t: s.at, title: name });
  }
  chapters[0].t = 0;
  // YouTube wants each chapter 10 s or longer: fold a short one into its neighbour (the first one
  // takes in the next), and say so, so the script can be fixed.
  for (let guard = 0; guard < 20; guard++) {
    const len = (i) => (i + 1 < chapters.length ? chapters[i + 1].t : duration) - chapters[i].t;
    const i = chapters.findIndex((c, j) => len(j) < 10);
    if (i < 0 || chapters.length < 2) break;
    console.warn(`chapter "${chapters[i].title}" is ${len(i).toFixed(1)} s, folded into a neighbour`);
    chapters.splice(i === 0 ? 1 : i, 1);
  }
  const subtitles = splitCue(0.4, INTRO - 0.3, `${meta.title}. ${meta.sub}.`);
  for (const s of T.steps) subtitles.push(...splitCue(s.at + 0.15, s.end - 0.15, `${s.title}. ${s.text}`));
  subtitles.push(...splitCue(stepsEnd + 0.4, duration - 0.3, next ? `Up next: ${next.title}.` : 'Thanks for watching.'));

  // --- A frame ----------------------------------------------------------------------------------------
  function seek(t) {
    bd.draw(t);
    // Title card, then the phone rises in; at the end it sinks and the "Up next" card comes up.
    const pop = spring(t - 0.1, { dampingRatio: 0.55, stiffness: 260 });
    const out = Ease.emphasizedAccel(prog(t, INTRO - 1.0, 0.55));
    title.style.opacity = clamp(t / 0.2) * (1 - out);
    title.style.transform = `translateY(${-out * 60}px)`;
    title.querySelector('.i').style.transform = `scale(${Math.max(0, pop)})`;
    title.querySelector('.n').style.opacity = prog(t, 0.3, 0.4);
    title.querySelector('.n').style.transform = `translateY(${(1 - Ease.emphasizedDecel(prog(t, 0.3, 0.6))) * 40}px)`;
    title.querySelector('.g').style.opacity = prog(t, 0.7, 0.5);
    const enter = 1 - Ease.emphasizedDecel(prog(t, INTRO - 0.75, 0.8));
    const leave = Ease.emphasizedAccel(prog(t, stepsEnd, 0.55));
    const c = cam.at(t);
    frame.style.transform = `translate(${F.x - c.cx * c.s - 7 * c.s}px,${F.y - c.cy * c.s - 7 * c.s + enter * 1150 + leave * 900}px) scale(${c.s})`;
    frame.style.opacity = 1 - leave;
    header.style.opacity = prog(t, INTRO - 0.4, 0.4) * (1 - prog(t, stepsEnd, 0.4));
    bar.style.opacity = header.style.opacity;
    caps.forEach((k) => showBetween(k.el, t, k.a, k.b, { rise: 26 }));
    T.steps.forEach((s, i) => { fills[i].style.width = `${prog(t, s.at, s.end - s.at) * 100}%`; });
    const ep = t - stepsEnd - 0.35;
    outro.style.opacity = clamp(ep / 0.35);
    outro.querySelector('.i').style.transform = `scale(${Math.max(0, spring(ep, { dampingRatio: 0.55, stiffness: 260 }))})`;
    outro.querySelector('.n').style.transform = `translateY(${(1 - Ease.emphasizedDecel(prog(ep, 0.2, 0.5))) * 40}px)`;
    outro.querySelector('.n').style.opacity = prog(ep, 0.2, 0.4);
    outro.querySelector('.g').style.opacity = prog(ep, 0.55, 0.5);

    screensAt(t);
    for (const fn of T.updates) fn(t);
    squeezeAt(t);
    for (const m of T.marks) {
      const k = Math.min(prog(t, m.at, 0.22), 1 - prog(t, m.at + m.dur - 0.25, 0.25));
      m.el.style.opacity = k;
      m.el.style.display = k > 0 ? 'block' : 'none';
      if (m.kind === 'ring') m.el.style.transform = `scale(${1 + 0.025 * Math.sin((t - m.at) * 7)})`;
      else m.el.style.transform = `translate(-50%,-50%) scale(${lerp(0.85, 1, Ease.outBack(clamp(k)))})`;
    }
    const hs = script_.state(t);
    setHand(hand, { ...hs, fingerWidth: hs.fw ?? 40, mirror: hs.side === 'left', tilt: hs.tilt ?? (hs.side === 'left' ? 24 : -28) });
  }

  // Which screens show, and how far through their transition.
  function screensAt(t) {
    const S = T.screens;
    for (const s of S) if (s.el) s.el.style.visibility = 'hidden';
    let i = -1;
    for (let j = 0; j < S.length; j++) if (S[j].at <= t) i = j;
    let dark = false;
    if (i >= 0) {
      const cur = S[i], prev = S[i - 1] || null, dt = t - cur.at;
      const W0 = 412;
      const put = (el, x, a, z, extra = '') => { if (!el) return; el.style.visibility = 'visible'; el.style.transform = `translateX(${x}px)${extra}`; el.style.opacity = a; el.style.zIndex = z; el.style.clipPath = ''; el.style.borderRadius = ''; };
      if (cur.kind === 'push') {
        put(cur.el, (W0 / 5) * (1 - offset(dt)), fade(dt), 2);
        if (prev?.el && prev.el !== cur.el && dt < 0.6) put(prev.el, (-W0 / 10) * offset(dt), 1 - fade(dt), 1);
      } else if (cur.kind === 'pop') {
        put(cur.el, (-W0 / 10) * (1 - offset(dt)), fade(dt), 1);
        if (prev?.el && prev.el !== cur.el && dt < 0.6) put(prev.el, (W0 / 5) * offset(dt), 1 - fade(dt), 2);
      } else if (cur.kind === 'fade') {
        put(cur.el, 0, clamp(dt / 0.25), 2);
        if (prev?.el && prev.el !== cur.el && dt < 0.25) put(prev.el, 0, 1, 1);
      } else if (cur.kind === 'launch' || cur.kind === 'home') {
        // Out of (or back into) the launcher icon: the window grows from the icon's rect.
        const opening = cur.kind === 'launch';
        const el = opening ? cur.el : prev?.el;
        const k = opening ? Ease.emphasizedDecel(clamp(dt / 0.5)) : 1 - Ease.emphasizedAccel(clamp(dt / 0.38));
        const r = cur.from || { cx: 206, cy: 457, w: 56, h: 56 };
        if (el && k > 0) {
          el.style.visibility = 'visible'; el.style.zIndex = 2;
          const s = lerp(r.w / W0, 1, k);
          const tx = lerp(r.cx - 206, 0, k), ty = lerp(r.cy - 457.5, 0, k);
          el.style.transform = `translate(${tx}px,${ty}px) scale(${s})`;
          el.style.opacity = clamp(k * 2.2);
          el.style.clipPath = `inset(0 round ${lerp(200, 0, k)}px)`;
        }
      } else put(cur.el, 0, 1, 2);
      dark = cur.el ? (cur.dark && (cur.kind !== 'launch' || dt > 0.2)) : (prev?.dark && dt < 0.15);
      if (cur.kind === 'home') dark = dt < 0.2 && !!prev?.dark;
    }
    if (dark !== phone._dark) { phone.setDarkIcons(dark); phone._dark = dark; }
  }

  // PressBounce on the tapped targets: squeeze by 8 dp of the longer side (1.5–8 %), then let go.
  function squeezeAt(t) {
    for (const q of T.squeezes) {
      const dt = t - q.at;
      if (dt < 0 || dt > 1.3) continue;
      const tgt = q.el.querySelector(`[data-target="${q.name}"]`);
      if (!tgt) continue;
      const r = tgt.getBoundingClientRect();
      const m = clamp(8 / Math.max(1, Math.max(r.width, r.height) / (frame.getBoundingClientRect().width / 426)), 0.015, 0.08);
      const s = dt < 0.16 ? 1 - m * PRESS_IN(dt) : 1 - m + m * RELEASE(dt - 0.16);
      tgt.style.transform = dt > 1.25 ? '' : `scale(${s})`;
    }
  }

  const series = { n, of: SERIES.length, id, title: meta.title, sub: meta.sub, next: next ? next.title : null };
  if (thumb) {
    // A still for the thumbnail: the moment thumb.t, the phone where thumb.cam puts it, words over.
    for (const el of [header, bar, title, outro, ...caps.map((c) => c.el)]) el.style.display = 'none';
    root.appendChild(thumbOverlay({ title: thumb.title, badge: `Tutorial ${n}` }));
    const still = () => {
      seek(thumb.t);
      for (const el of [header, bar, title, outro, ...caps.map((c) => c.el)]) el.style.display = 'none';
      frame.style.transform = thumbTransform(thumb.cam);
      frame.style.opacity = 1;
    };
    return { width: W, height: H, duration: 0, seek: still };
  }
  // Screens rebuilt mid-video bring new <img>s (the launcher icon): wait for them to decode, so
  // no frame catches one blank.
  const seekReady = async (t) => {
    seek(t);
    const pending = [...root.querySelectorAll('img')].filter((i) => !i.complete || !i.naturalWidth);
    if (pending.length) await Promise.all(pending.map((i) => i.decode().catch(() => {})));
  };
  return { width: W, height: H, duration, cues, music: 'calm', musicGain: 0.42, seek: seekReady, chapters, subtitles, series, steps: T.steps.map(({ at, end, title, text, chapter }) => ({ at, end, title, text, chapter })) };
}

// Subtitle cues: a sentence (or a clause of a long one) at a time, in one line or two balanced
// lines of at most 42 characters, spread over [a, b] by length.
function balance(str) {
  if (str.length <= 42) return str;
  const words = str.split(' ');
  let best = null;
  for (let i = 1; i < words.length; i++) {
    const l1 = words.slice(0, i).join(' '), l2 = words.slice(i).join(' ');
    if (l1.length > 42 || l2.length > 42) continue;
    const score = Math.abs(l1.length - l2.length);
    if (!best || score < best.score) best = { score, text: l1 + '\n' + l2 };
  }
  return best ? best.text : null;
}
function clauses(sen) {
  if (balance(sen)) return [sen];
  const mid = sen.length / 2;
  const marks = [...sen.matchAll(/[,;:] /g)].map((m) => m.index + 1).sort((x, y) => Math.abs(x - mid) - Math.abs(y - mid));
  for (const m of marks) {
    const x = sen.slice(0, m).trim(), y = sen.slice(m).trim();
    if (x.length > 8 && y.length > 8) return [...clauses(x), ...clauses(y)];
  }
  const words = sen.split(' ');
  const half = Math.ceil(words.length / 2);
  return [...clauses(words.slice(0, half).join(' ')), ...clauses(words.slice(half).join(' '))];
}
function splitCue(a, b, text) {
  const sentences = text.match(/[^.!?]+[.!?]+(\s|$)|[^.!?]+$/g).map((x) => x.trim()).filter(Boolean);
  const pieces = sentences.flatMap(clauses);
  const total = pieces.reduce((n, x) => n + x.length, 0);
  // A cue shorter than 1.2 s joins the one before when the two still fit in two lines.
  const merged = [];
  for (const x of pieces) {
    const prev = merged[merged.length - 1];
    if (prev && ((b - a) * x.length) / total < 1.2 && balance(prev + ' ' + x)) merged[merged.length - 1] = prev + ' ' + x;
    else merged.push(x);
  }
  const sum = merged.reduce((n, x) => n + x.length, 0);
  let t = a;
  return merged.map((x) => { const d = ((b - a) * x.length) / sum; const c = { a: t, b: t + d - 0.04, text: balance(x) || x }; t += d; return c; });
}
