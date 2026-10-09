// Builds the review page for everything in store/: build/gallery/index.html and web-sized copies
// of the art and videos beside it (JPEG stills, 720p videos), ready to publish as one page.
//
//   node gallery.mjs
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { entries, PROMO, clock, STORE } from './youtube.mjs';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(ROOT, 'build/gallery');
const MEDIA = path.join(OUT, 'media');
const REPO = 'https://github.com/imamhossain94/GestureVolume/tree/claude/sweet-meitner-a3s3e9';

const SHOTS = [
  ['01-swipe', 'Volume on the edge', 'Swipe the bar and the Quick slider follows your finger'],
  ['02-deck', 'Pull out the Deck', 'Your apps, people and tools, one swipe in from the bar'],
  ['03-rotate', 'Turns with your phone', 'On its side, the bar lies along the top edge'],
  ['04-fills', 'Fills that move', 'Live shaders, your colours and a flourish at 100%'],
  ['05-search', 'Answers as you type', 'Find apps and people, or work out a sum, right in the Deck'],
  ['06-appearance', 'Make it yours', 'Presets, size, shape, colour and icon, with a live preview'],
  ['07-menu', 'Hold for more', 'A long-press menu of your own, in glass or solid'],
  ['08-visibility', 'Out of the way', 'Hide the bar in the apps you choose, or while you type'],
  ['09-coin', 'Heads or tails', 'A coin, dice, a timer and notes, all in the Deck'],
];

const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const ff = (args) => { const r = spawnSync('ffmpeg', ['-y', '-loglevel', 'error', ...args], { stdio: 'inherit' }); if (r.status) throw new Error('ffmpeg ' + args.join(' ')); };
const fresh = (src, out) => !fs.existsSync(out) || fs.statSync(out).mtimeMs < fs.statSync(src).mtimeMs;
const still = (src, out, w) => { if (fresh(src, out)) ff(['-i', src, '-vf', `scale=${w}:-2:flags=lanczos`, '-q:v', '3', out]); };
const frame = (src, out, at, w) => { if (fresh(src, out)) ff(['-ss', String(at), '-i', src, '-frames:v', '1', '-vf', `scale=${w}:-2:flags=lanczos`, '-q:v', '3', out]); };
const clip = (src, out, scale) => {
  if (!fresh(src, out)) return;
  ff(['-i', src, '-vf', `scale=${scale}:flags=lanczos`, '-c:v', 'libx264', '-preset', 'medium', '-crf', '27', '-tune', 'animation', '-pix_fmt', 'yuv420p',
    '-c:a', 'aac', '-b:a', '96k', '-movflags', '+faststart', out]);
};

fs.mkdirSync(path.join(MEDIA, 'shots'), { recursive: true });
fs.mkdirSync(path.join(MEDIA, 'tut'), { recursive: true });
const G = path.join(STORE, 'graphics');
still(path.join(G, 'feature-graphic.png'), path.join(MEDIA, 'feature-a.jpg'), 1024);
still(path.join(G, 'extras/feature-graphic-b.png'), path.join(MEDIA, 'feature-b.jpg'), 1024);
for (const style of ['bleed', 'card']) {
  for (const [id] of SHOTS) {
    const src = path.join(G, id === '09-coin' ? 'extras' : 'screenshots', style, id + '.png');
    still(src, path.join(MEDIA, 'shots', `${style}-${id}.jpg`), 540);
  }
}
const V = path.join(STORE, 'video');
clip(path.join(V, 'promo-1080p.mp4'), path.join(MEDIA, 'promo.mp4'), '1280:720');
clip(path.join(V, 'promo-vertical.mp4'), path.join(MEDIA, 'promo-vertical.mp4'), '720:1280');
still(path.join(V, 'promo-thumbnail.png'), path.join(MEDIA, 'promo-thumb.jpg'), 1280);
frame(path.join(V, 'promo-vertical.mp4'), path.join(MEDIA, 'promo-vertical.jpg'), 12, 540);   // the Deck, whole phone in view
const tuts = entries();
for (const e of tuts) {
  clip(path.join(STORE, 'tutorials', e.base + '.mp4'), path.join(MEDIA, 'tut', e.base + '.mp4'), '1280:720');
  still(path.join(STORE, 'tutorials/thumbnails', e.base + '.png'), path.join(MEDIA, 'tut', e.base + '.jpg'), 640);
}
const icon = fs.readFileSync(path.join(ROOT, 'build/drawable/ic_launcher_foreground.svg'), 'utf8');
const iconUri = 'data:image/svg+xml;base64,' + Buffer.from(icon).toString('base64');

// --- The files, for the table ------------------------------------------------------------------------
const rel = (f) => path.relative(path.join(STORE, '..'), f);
const files = [
  [path.join(G, 'feature-graphic.png'), 'Feature graphic A, 1024 × 500', 'Main store listing › Feature graphic'],
  [path.join(G, 'extras/feature-graphic-b.png'), 'Feature graphic B, the alternative', '—'],
  [path.join(G, 'screenshots/bleed'), '8 phone screenshots, 1080 × 1920, Bleed style', 'Phone screenshots, in order 01–08'],
  [path.join(G, 'screenshots/card'), 'The same 8 in the Card style', 'Instead of Bleed, if you prefer it'],
  [path.join(G, 'extras'), 'Coin toss, the 9th shot, in both styles', 'Spare: swap in for any of the 8'],
  [path.join(V, 'promo-1080p.mp4'), 'Promo, 0:30, 1920 × 1080', 'YouTube, then its link in Main store listing › Video'],
  [path.join(V, 'promo-vertical.mp4'), 'Promo, 0:30, 1080 × 1920', 'YouTube Shorts'],
  [path.join(V, 'promo-thumbnail.png'), 'Promo thumbnail, 1280 × 720', 'YouTube thumbnail'],
  [path.join(STORE, 'tutorials'), `${tuts.length} tutorials: .mp4, .srt captions, .json timings`, 'YouTube, one playlist'],
  [path.join(STORE, 'tutorials/thumbnails'), 'Tutorial thumbnails, 1280 × 720', 'YouTube thumbnails'],
  [path.join(STORE, 'tutorials/youtube.md'), 'Titles, descriptions, chapters, tags, voice-over scripts', 'Paste into YouTube Studio'],
  [path.join(ROOT), 'The renderer: kit, scenes and scripts', 'To make any of this again'],
];
const dirSize = (p) => (fs.statSync(p).isDirectory() ? fs.readdirSync(p).reduce((n, f) => n + (['node_modules', 'build'].includes(f) ? 0 : dirSize(path.join(p, f))), 0) : fs.statSync(p).size);
const fmt = (n) => (n > 1048576 ? `${(n / 1048576).toFixed(1)} MB` : `${Math.round(n / 1024)} KB`);

// --- The page -------------------------------------------------------------------------------------------
const total = tuts.reduce((n, e) => n + e.duration, 0);
const copyBlock = (id, label, text) => `<div class="copy"><div class="copy-head"><span>${label}</span><button type="button" data-copy="${id}" data-label="Copy">Copy</button></div><pre id="${id}">${esc(text)}</pre></div>`;
const shotRow = (style) => `<ol class="shots" data-style="${style}"${style === 'card' ? ' hidden' : ''}>${SHOTS.map(([id, t, s], i) => `
    <li${i === 8 ? ' class="spare"' : ''}><img src="media/shots/${style}-${id}.jpg" width="540" height="960" loading="lazy" alt="${esc(t)}: ${esc(s)}">
      <div class="cap"><span class="num">${i === 8 ? 'Spare' : String(i + 1).padStart(2, '0')}</span><strong>${esc(t)}</strong><span>${esc(s)}</span></div></li>`).join('')}</ol>`;

const html = `<title>Gesture Volume Store Art</title>
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
<style>
/* One 1120 px column. Each deliverable is a section: what it is, the thing itself, then its files. */
:root {
  --bg: #F5F3FC; --surface: #FFFFFF; --ink: #1A1633; --muted: #5E5878; --line: #E2DDF2;
  --accent: #5546D8; --accent-ink: #FFFFFF; --soft: #EEEAFD; --note: #8A4B00; --note-bg: #FFF5E6;
  --display: 'Plus Jakarta Sans', 'Segoe UI', system-ui, sans-serif;
  --body: 'Plus Jakarta Sans', 'Segoe UI', system-ui, sans-serif;
  --mono: 'JetBrains Mono', ui-monospace, Menlo, Consolas, monospace;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
  --bg: #0E0B22; --surface: #17132E; --ink: #EFECFF; --muted: #A7A1C6; --line: #2B2650;
  --accent: #A99CFF; --accent-ink: #120E2C; --soft: #221C45; --note: #F5C27A; --note-bg: #2A2011; color-scheme: dark } }
:root[data-theme="dark"] {
  --bg: #0E0B22; --surface: #17132E; --ink: #EFECFF; --muted: #A7A1C6; --line: #2B2650;
  --accent: #A99CFF; --accent-ink: #120E2C; --soft: #221C45; --note: #F5C27A; --note-bg: #2A2011; color-scheme: dark }
* { box-sizing: border-box }
body { background: var(--bg); color: var(--ink); font: 400 16px/1.6 var(--body) }
.wrap { max-width: 1120px; margin: 0 auto; padding-inline: 20px; padding-block: 40px 96px; display: grid; gap: 64px }
a { color: var(--accent) }
a:focus-visible, button:focus-visible { outline: 3px solid var(--accent); outline-offset: 2px }
h1, h2, h3 { font-family: var(--display); text-wrap: balance; margin: 0 }
h1 { font-size: clamp(34px, 5vw, 52px); font-weight: 800; letter-spacing: -.03em; line-height: 1.05 }
h2 { font-size: clamp(24px, 3vw, 32px); font-weight: 800; letter-spacing: -.02em; line-height: 1.15 }
h3 { font-size: 18px; font-weight: 700; letter-spacing: -.01em }
p { margin: 0; max-width: 68ch }
code, .mono { font-family: var(--mono); font-size: .86em }
.eyebrow { font: 700 12px/1 var(--body); letter-spacing: .14em; text-transform: uppercase; color: var(--accent) }
section { display: grid; gap: 20px; min-width: 0 }
.lede { color: var(--muted); font-size: 18px }
.top { display: grid; grid-template-columns: auto 1fr; gap: 24px; align-items: start }
.mark { width: 84px; height: 84px; border-radius: 26px; background: linear-gradient(135deg,#AA7BFF,#2C209A); overflow: hidden; position: relative; box-shadow: 0 10px 30px rgba(44,32,154,.35) }
.mark img { position: absolute; width: 126px; height: 126px; left: -21px; top: -21px; max-width: none }
.top .text { display: grid; gap: 12px; min-width: 0 }
.facts { display: flex; flex-wrap: wrap; gap: 10px; margin: 4px 0 0; padding: 0 }
.facts div { background: var(--surface); border: 1px solid var(--line); border-radius: 12px; padding: 8px 14px }
.facts dt { font-size: 12px; color: var(--muted); font-weight: 600 }
.facts dd { margin: 0; font-weight: 700; font-variant-numeric: tabular-nums }
.note { background: var(--note-bg); border-radius: 18px; padding: 22px 24px; display: grid; gap: 12px }
.note h2 { font-size: 22px; color: var(--note) }
.note ul { margin: 0; padding-left: 20px; display: grid; gap: 8px }
.note li { max-width: 80ch }
.figs { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 20px }
figure { margin: 0; display: grid; gap: 10px; min-width: 0 }
figure img, figure video { width: 100%; height: auto; display: block; border-radius: 14px; background: #000 }
figcaption { color: var(--muted); font-size: 14px }
figcaption strong { color: var(--ink) }
.pill { display: inline-block; font: 700 11px/1 var(--body); letter-spacing: .08em; text-transform: uppercase; padding: 5px 9px; border-radius: 999px; background: var(--accent); color: var(--accent-ink); margin-right: 6px; vertical-align: 1px }
.switch { display: inline-flex; padding: 4px; border-radius: 12px; background: var(--soft); gap: 4px; justify-self: start }
.switch button { font: 600 14px/1 var(--body); border: 0; border-radius: 9px; padding: 10px 16px; background: transparent; color: var(--muted); cursor: pointer }
.switch button[aria-pressed="true"] { background: var(--surface); color: var(--ink); box-shadow: 0 1px 3px rgba(0,0,0,.12) }
.shots { list-style: none; margin: 0; padding: 0 0 12px; display: grid; grid-auto-flow: column; grid-auto-columns: minmax(180px, 220px); gap: 16px; overflow-x: auto; scroll-snap-type: x mandatory }
.shots li { scroll-snap-align: start; display: grid; gap: 10px; align-content: start }
.shots img { width: 100%; height: auto; border-radius: 12px; display: block; border: 1px solid var(--line) }
.shots .spare img { opacity: .75 }
.cap { display: grid; gap: 2px; font-size: 13px; color: var(--muted) }
.cap strong { color: var(--ink); font-size: 14px }
.num { font: 600 12px/1.4 var(--mono); color: var(--accent) }
.table { overflow-x: auto; border: 1px solid var(--line); border-radius: 14px; background: var(--surface) }
table { border-collapse: collapse; width: 100%; font-size: 14px }
th, td { text-align: left; padding: 10px 14px; border-bottom: 1px solid var(--line); vertical-align: top }
th { font-size: 12px; text-transform: uppercase; letter-spacing: .08em; color: var(--muted) }
tr:last-child td { border-bottom: 0 }
td.t { font-family: var(--mono); font-variant-numeric: tabular-nums; white-space: nowrap; color: var(--accent) }
.promo { display: grid; grid-template-columns: minmax(0, 3fr) minmax(0, 1fr); gap: 20px; align-items: start }
.tuts { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 20px }
.tut { background: var(--surface); border: 1px solid var(--line); border-radius: 18px; padding: 14px; display: grid; gap: 12px; align-content: start; min-width: 0 }
.tut video { width: 100%; height: auto; border-radius: 10px; background: #000; display: block }
.tut .meta { display: flex; justify-content: space-between; gap: 12px; align-items: baseline }
.tut .meta span { font: 500 13px/1 var(--mono); color: var(--muted); font-variant-numeric: tabular-nums }
.tut ol { margin: 0; padding-left: 0; list-style: none; font-size: 13px; color: var(--muted); display: grid; gap: 2px }
.tut ol b { font: 500 12px var(--mono); color: var(--accent); margin-right: 8px }
details { border-top: 1px solid var(--line); padding-top: 10px }
summary { cursor: pointer; font-weight: 600; font-size: 14px }
.copy { margin-top: 10px; border: 1px solid var(--line); border-radius: 10px; overflow: hidden }
.copy-head { display: flex; justify-content: space-between; align-items: center; padding: 6px 10px; background: var(--soft); font-size: 12px; font-weight: 700; color: var(--muted) }
.copy-head button { font: 600 12px var(--body); border: 1px solid var(--line); background: var(--surface); color: var(--ink); border-radius: 8px; padding: 4px 10px; cursor: pointer }
.copy pre { margin: 0; padding: 10px; font: 12px/1.5 var(--mono); white-space: pre-wrap; overflow-wrap: anywhere; max-height: 220px; overflow-y: auto }
pre.cmd { margin: 0; padding: 16px 18px; background: var(--surface); border: 1px solid var(--line); border-radius: 14px; font: 13px/1.7 var(--mono); overflow-x: auto }
@media (max-width: 760px) { .promo { grid-template-columns: 1fr } .top { grid-template-columns: 1fr } }
@media (prefers-reduced-motion: reduce) { * { scroll-behavior: auto } }
</style>

<main class="wrap">
  <header class="top">
    <div class="mark"><img src="${iconUri}" alt=""></div>
    <div class="text">
      <p class="eyebrow">Store kit · version 1.5.2</p>
      <h1>Gesture Volume Store Art</h1>
      <p class="lede">Google Play graphics, a 30-second promo in two cuts, and ${tuts.length} YouTube tutorials. Every frame is drawn from the app's own Compose code, shaders and drawables, and rendered in a browser. All of it is in the repository under <code>store/</code>.</p>
      <p><a href="${REPO}/store" target="_blank" rel="noopener">Open store/ on GitHub</a></p>
      <dl class="facts">
        <div><dt>Feature graphic</dt><dd>2 options</dd></div>
        <div><dt>Phone screenshots</dt><dd>8 + 1 spare, 2 styles</dd></div>
        <div><dt>Promo</dt><dd>0:30, 16:9 and 9:16</dd></div>
        <div><dt>Tutorials</dt><dd>${tuts.length} videos, ${clock(total)} in all</dd></div>
      </dl>
    </div>
  </header>

  <section class="note" aria-labelledby="confirm">
    <h2 id="confirm">Things to confirm before you upload</h2>
    <ul>
      <li>Everything is drawn from the code, not captured on a phone. Compare a few screens with your own device before you publish.</li>
      <li>Pick one screenshot style. I would use Bleed (light, the phone large and cropped); Card (dark, the whole phone) is the alternative.</li>
      <li>Google Play takes 8 phone screenshots and your listing brief lists nine, so the coin toss is kept as a spare.</li>
      <li>Play lays its play button over the middle of the feature graphic once the listing has a video. Both options keep the middle clear.</li>
      <li>The music is generated by a script and measured at −14 LUFS, but I could not listen to it. Give it a listen.</li>
      <li>The tutorials have on-screen captions and .srt subtitles but no voice. <code>youtube.md</code> has a voice-over script for each if you want to record one.</li>
      <li>Other companies' names appear only where the app itself prints them: the search providers (Google, YouTube, Maps) and the intro of Gestures for chosen apps. The apps, contacts and icons around the app are made up.</li>
      <li>Android's own screens (Display over other apps, the notification request, the lock screen) are plain AOSP-style drawings, not any phone maker's design.</li>
    </ul>
  </section>

  <section aria-labelledby="fg">
    <p class="eyebrow">Google Play · 1024 × 500</p>
    <h2 id="fg">Feature graphic</h2>
    <div class="figs">
      <figure><img src="media/feature-a.jpg" width="1024" height="500" alt="Feature graphic A: Volume. Brightness. Everything else, beside the Quick slider with a plasma fill"><figcaption><span class="pill">Use this</span><strong>A.</strong> Your brief: the bar grown into the Quick slider at the right edge, the line on the left third.</figcaption></figure>
      <figure><img src="media/feature-b.jpg" width="1024" height="500" alt="Feature graphic B: the icon and name beside a tilted phone with the Deck open"><figcaption><strong>B.</strong> The icon and name beside a phone with the Deck and its media card open.</figcaption></figure>
    </div>
  </section>

  <section aria-labelledby="ss">
    <p class="eyebrow">Google Play · 1080 × 1920, 24-bit PNG</p>
    <h2 id="ss">Phone screenshots</h2>
    <p>In your brief's order; the first three decide installs. Each caption is a title and one line, with no claims Play's metadata rules object to.</p>
    <div class="switch" role="group" aria-label="Screenshot style">
      <button type="button" aria-pressed="true" data-style="bleed">Bleed</button><button type="button" aria-pressed="false" data-style="card">Card</button>
    </div>
    ${shotRow('bleed')}${shotRow('card')}
  </section>

  <section aria-labelledby="promo">
    <p class="eyebrow">YouTube and Google Play · 0:30</p>
    <h2 id="promo">Promo video</h2>
    <p>Cut to the bars of its music (112 bpm). The landscape cut is the one Play shows; the vertical cut is for Shorts. The audio is mixed to −14 LUFS.</p>
    <div class="promo">
      <figure><video controls preload="none" playsinline poster="media/promo-thumb.jpg" src="media/promo.mp4" width="1280" height="720"></video><figcaption><strong>Landscape</strong>, 1920 × 1080 · <code>store/video/promo-1080p.mp4</code></figcaption></figure>
      <figure><video controls preload="none" playsinline poster="media/promo-vertical.jpg" src="media/promo-vertical.mp4" width="720" height="1280"></video><figcaption><strong>Vertical</strong>, 1080 × 1920 · <code>promo-vertical.mp4</code></figcaption></figure>
    </div>
    <div class="table"><table><thead><tr><th>At</th><th>On screen</th><th>Caption</th></tr></thead><tbody>
      ${PROMO.shots.map(([t, what, cap]) => `<tr><td class="t">${t}</td><td>${esc(what)}</td><td>${esc(cap) || '—'}</td></tr>`).join('')}
    </tbody></table></div>
    <div class="figs">
      <figure><img src="media/promo-thumb.jpg" width="1280" height="720" alt="Promo thumbnail"><figcaption><strong>Thumbnail</strong>, 1280 × 720 · <code>store/video/promo-thumbnail.png</code></figcaption></figure>
      <div>${copyBlock('promo-title', 'Title', PROMO.title)}${copyBlock('promo-desc', 'Description', PROMO.description)}${copyBlock('promo-tags', 'Tags', PROMO.tags.join(', '))}</div>
    </div>
  </section>

  <section aria-labelledby="tut">
    <p class="eyebrow">YouTube · 1920 × 1080</p>
    <h2 id="tut">Tutorials</h2>
    <p>One per feature, in order, each ending on the next one's title. Chapters are in each description; the .srt files carry the same words as the captions.</p>
    <div class="tuts">
      ${tuts.map((e) => `<article class="tut">
        <video controls preload="none" playsinline poster="media/tut/${e.base}.jpg" src="media/tut/${e.base}.mp4" width="1280" height="720"></video>
        <div class="meta"><h3>${e.series.n}. ${esc(e.series.title)}</h3><span>${clock(e.duration)}</span></div>
        <ol>${e.chapters.map((c) => `<li><b>${clock(c.t)}</b>${esc(c.title)}</li>`).join('')}</ol>
        <details><summary>Upload text</summary>${copyBlock(`t${e.series.n}-title`, 'Title', e.title)}${copyBlock(`t${e.series.n}-desc`, 'Description', e.description)}${copyBlock(`t${e.series.n}-tags`, 'Tags', e.tags.join(', '))}</details>
      </article>`).join('')}
    </div>
  </section>

  <section aria-labelledby="files">
    <h2 id="files">Files</h2>
    <div class="table"><table><thead><tr><th>Path</th><th>What</th><th>Size</th><th>Where it goes</th></tr></thead><tbody>
      ${files.filter(([f]) => fs.existsSync(f)).map(([f, what, where]) => `<tr><td><code>${esc(rel(f))}${fs.statSync(f).isDirectory() ? '/' : ''}</code></td><td>${esc(what)}</td><td class="t">${fmt(dirSize(f))}</td><td>${esc(where)}</td></tr>`).join('')}
    </tbody></table></div>
  </section>

  <section aria-labelledby="again">
    <h2 id="again">Making them again</h2>
    <p>Change the app, re-run the step, and the art follows: the shaders, drawables and strings come from the source. Run from <code>tool/store</code>; Node 22, Python 3 with NumPy and Pillow, and ffmpeg.</p>
    <pre class="cmd">npm install                                   # fonts, icons and Playwright
python3 vd2svg.py --all ../../app/src/main/res/drawable build/drawable
python3 extract_shaders.py                    # the Quick slider's fills, from the Kotlin
node render-graphics.mjs                      # feature graphics and screenshots
python3 finish.py                             # 24-bit PNG, checked against Play's limits
node render.mjs video promo ../../store/video/promo-1080p.mp4
node render.mjs video promo ../../store/video/promo-vertical.mp4 --query v=1
# each tutorial by its id in kit/tutorial.js, getting-started to visibility:
node render.mjs video tutorial ../../store/tutorials/03-quick-slider.mp4 --query id=quick-slider --crf 20 --tune animation
node render-thumbs.mjs                        # YouTube thumbnails
node youtube.mjs                              # titles, descriptions, chapters, tags
node gallery.mjs                              # this page</pre>
  </section>
</main>

<script>
document.querySelectorAll('.switch button').forEach((b) => b.addEventListener('click', () => {
  document.querySelectorAll('.switch button').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
  document.querySelectorAll('.shots').forEach((row) => { row.hidden = row.dataset.style !== b.dataset.style; });
}));
document.addEventListener('click', async (e) => {
  const b = e.target.closest('[data-copy]');
  if (!b) return;
  const src = document.getElementById(b.dataset.copy);
  try { await navigator.clipboard.writeText(src.textContent); b.textContent = 'Copied'; }
  catch { const r = document.createRange(); r.selectNodeContents(src); const s = getSelection(); s.removeAllRanges(); s.addRange(r); b.textContent = 'Selected'; }
  setTimeout(() => { b.textContent = b.dataset.label; }, 1600);
});
</script>
`;
fs.writeFileSync(path.join(OUT, 'index.html'), html);
const listing = [];
const walk = (d) => { for (const f of fs.readdirSync(d)) { const p = path.join(d, f); if (fs.statSync(p).isDirectory()) walk(p); else listing.push(p); } };
walk(MEDIA);
const bytes = listing.reduce((n, f) => n + fs.statSync(f).size, 0);
console.log(`gallery: index.html + ${listing.length} media files, ${fmt(bytes)}`);
