// Renders a scene page to a still PNG or, frame by frame, to an MP4.
//
//   node render.mjs still  <scene> <out.png> [--t 1.5] [--query k=v&k2=v2]
//   node render.mjs video  <scene> <out.mp4> [--fps 30] [--query ...] [--from 0] [--to 12.5] [--crf 18] [--tune animation]
//   node render.mjs frames <scene> <outdir>  [--fps 30] [--every 1]   (PNG per frame, for checking)
//
// A scene is scenes/<scene>.html. It sets `window.scene = { width, height, duration, seek(t) }`
// and resolves `window.sceneReady` once fonts, icons and images are in. Nothing on the page moves
// by the wall clock: every frame is drawn by seek(t), so a render is the same on every run and
// never drops a frame, however slow the machine.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
// WebGL through SwiftShader for the shaders; the page itself composited in software, which in a
// headless container is several times faster per frame than going through the GL compositor.
export const BROWSER_ARGS = ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--disable-gpu-compositing', '--font-render-hinting=none'];
const TYPES = {
  '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.css': 'text/css',
  '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.webp': 'image/webp',
  '.woff2': 'font/woff2', '.woff': 'font/woff', '.json': 'application/json', '.wav': 'audio/wav',
  '.glsl': 'text/plain', '.txt': 'text/plain',
};

export function serve() {
  return new Promise((resolve) => {
    const server = http.createServer((req, res) => {
      const url = new URL(req.url, 'http://x');
      const file = path.join(ROOT, decodeURIComponent(url.pathname));
      if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
        res.writeHead(404); res.end('not found: ' + url.pathname); return;
      }
      res.writeHead(200, { 'content-type': TYPES[path.extname(file)] || 'application/octet-stream', 'cache-control': 'no-store' });
      fs.createReadStream(file).pipe(res);
    });
    server.listen(0, '127.0.0.1', () => resolve(server));
  });
}

function args(argv) {
  const out = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i].startsWith('--')) out[argv[i].slice(2)] = argv[i + 1], i++;
    else out._.push(argv[i]);
  }
  return out;
}

export async function openScene(browser, port, scene, query = '') {
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
  page.on('console', (m) => { if ((m.type() === 'error' || m.type() === 'warning') && !/GPU stall due to ReadPixels/.test(m.text())) console.error(`[${scene}] ${m.type()}: ${m.text()}`); });
  page.on('pageerror', (e) => console.error(`[${scene}] pageerror: ${e.message}`));
  await page.goto(`http://127.0.0.1:${port}/scenes/${scene}.html${query ? '?' + query : ''}`);
  await page.waitForFunction(() => window.sceneReady !== undefined, null, { timeout: 30000 });
  await page.evaluate(() => window.sceneReady);
  const meta = await page.evaluate(() => ({ width: scene.width, height: scene.height, duration: scene.duration || 0 }));
  await page.setViewportSize({ width: meta.width, height: meta.height });
  return { page, meta };
}

async function seek(page, t) {
  await page.evaluate(async (t) => { await window.scene.seek(t); }, t);
}

export async function still(browser, port, scene, out, { t = 0, query = '' } = {}) {
  const { page } = await openScene(browser, port, scene, query);
  await seek(page, t);
  fs.mkdirSync(path.dirname(out), { recursive: true });
  const type = out.endsWith('.jpg') ? 'jpeg' : 'png';
  await page.screenshot({ path: out, type, ...(type === 'jpeg' ? { quality: 92 } : {}) });
  await page.close();
}

export async function video(browser, port, scene, out, { fps = 30, query = '', from = 0, to = null, crf = 18, tune = null } = {}) {
  const { page, meta } = await openScene(browser, port, scene, query);
  const end = to ?? meta.duration;
  const frames = Math.round((end - from) * fps);
  fs.mkdirSync(path.dirname(out), { recursive: true });
  // BT.709 throughout: what YouTube and Play expect for HD, tagged so players don't guess.
  const ff = spawn('ffmpeg', [
    '-y', '-loglevel', 'error', '-f', 'image2pipe', '-c:v', 'mjpeg', '-framerate', String(fps), '-i', '-',
    '-vf', 'scale=in_range=full:out_range=tv:out_color_matrix=bt709,format=yuv420p',
    '-c:v', 'libx264', '-preset', 'slow', '-crf', String(crf), ...(tune ? ['-tune', tune] : []), '-profile:v', 'high',
    '-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709', '-color_range', 'tv',
    '-movflags', '+faststart', '-r', String(fps), out,
  ], { stdio: ['pipe', 'inherit', 'inherit'] });
  const done = new Promise((res, rej) => ff.on('close', (c) => (c === 0 ? res() : rej(new Error('ffmpeg ' + c)))));
  const started = Date.now();
  for (let i = 0; i < frames; i++) {
    await seek(page, from + i / fps);
    // JPEG at 95 for the frames: PNG costs ten times as long to encode, and the H.264 pass that
    // follows is the lossy step that decides the quality anyway.
    const buf = await page.screenshot({ type: 'jpeg', quality: 95 });
    if (!ff.stdin.write(buf)) await new Promise((r) => ff.stdin.once('drain', r));
    if (i % (fps * 2) === 0) process.stderr.write(`\r${scene}: ${i}/${frames} frames, ${((Date.now() - started) / 1000).toFixed(0)}s   `);
  }
  ff.stdin.end();
  await done;
  process.stderr.write(`\r${scene}: ${frames}/${frames} frames, ${((Date.now() - started) / 1000).toFixed(0)}s   \n`);
  const sound = await page.evaluate(() => ({ cues: window.scene.cues || [], music: window.scene.music || null, musicGain: window.scene.musicGain ?? 0.55,
    chapters: window.scene.chapters || null, subtitles: window.scene.subtitles || null, steps: window.scene.steps || null, series: window.scene.series || null }));
  await page.close();
  return { ...meta, ...sound, duration: end - from };
}

// Puts sound under a silent render: the scene's music bed (audio.py) and its cue sheet's effects.
export function addSound(silent, out, { duration, cues, music, musicGain }) {
  const run = (cmd, a) => new Promise((res, rej) => {
    const p = spawn(cmd, a, { stdio: 'inherit', cwd: ROOT });
    p.on('close', (c) => (c === 0 ? res() : rej(new Error(`${cmd} ${a.join(' ')} -> ${c}`))));
  });
  const bed = out + '.bed.wav';
  const cueFile = out + '.cues.json';
  fs.writeFileSync(cueFile, JSON.stringify({ duration, cues, musicGain }, null, 1));
  return run('python3', ['audio.py', 'bed', music || 'calm', String(duration + 0.5), bed])
    .then(() => run('python3', ['audio.py', 'mix', silent, cueFile, bed, out]))
    .finally(() => { for (const f of [bed, cueFile]) fs.rmSync(f, { force: true }); });
}

// Beside a tutorial: its subtitles (.srt) and its chapters, steps and subtitles as JSON, which
// youtube.mjs turns into the upload text.
const stamp = (t) => {
  const ms = Math.round(t * 1000), hh = Math.floor(ms / 3600000), mm = Math.floor(ms / 60000) % 60, ss = Math.floor(ms / 1000) % 60;
  return `${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}:${String(ss).padStart(2, '0')},${String(ms % 1000).padStart(3, '0')}`;
};
export function writeSidecars(out, { subtitles, chapters, steps, duration, series }) {
  const base = out.replace(/\.mp4$/, '');
  fs.writeFileSync(base + '.srt', subtitles.map((c, i) => `${i + 1}\n${stamp(c.a)} --> ${stamp(c.b)}\n${c.text}\n`).join('\n'));
  fs.writeFileSync(base + '.json', JSON.stringify({ series, duration, chapters, steps, subtitles }, null, 1));
}

export async function frameDump(browser, port, scene, outDir, { fps = 30, every = 1, query = '' } = {}) {
  const { page, meta } = await openScene(browser, port, scene, query);
  fs.mkdirSync(outDir, { recursive: true });
  const frames = Math.round(meta.duration * fps);
  for (let i = 0; i < frames; i += every) {
    await seek(page, i / fps);
    await page.screenshot({ path: path.join(outDir, `f${String(i).padStart(5, '0')}.png`) });
  }
  await page.close();
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const a = args(process.argv.slice(2));
  const [mode, scene, out] = a._;
  const server = await serve();
  const port = server.address().port;
  const browser = await chromium.launch({ args: BROWSER_ARGS });
  try {
    if (mode === 'still') await still(browser, port, scene, out, { t: +(a.t || 0), query: a.query || '' });
    else if (mode === 'video') {
      const silent = a.silent ? out : out.replace(/\.mp4$/, '.silent.mp4');
      const info = await video(browser, port, scene, silent, { fps: +(a.fps || 30), query: a.query || '', from: +(a.from || 0), to: a.to ? +a.to : null, crf: +(a.crf || 18), tune: a.tune || null });
      if (!a.silent) { await addSound(silent, out, info); fs.rmSync(silent); }
      if (info.subtitles) writeSidecars(out, info);
    }
    else if (mode === 'frames') await frameDump(browser, port, scene, out, { fps: +(a.fps || 30), every: +(a.every || 1), query: a.query || '' });
    else throw new Error('mode must be still, video or frames');
  } finally {
    await browser.close();
    server.close();
  }
}
