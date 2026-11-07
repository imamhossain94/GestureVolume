// Renders a scene page to a still PNG or, frame by frame, to an MP4.
//
//   node render.mjs still  <scene> <out.png> [--t 1.5] [--query k=v&k2=v2]
//   node render.mjs video  <scene> <out.mp4> [--fps 30] [--query ...] [--from 0] [--to 12.5] [--crf 18] [--tune animation]
//   node render.mjs frames <scene> <outdir>  [--fps 30] [--every 1]   (PNG per frame, for checking)
//   node render.mjs remix  <scene> <video.mp4> [--query ...]   (new sound under a finished render's picture)
//   ... [--novo]   (a narrated scene without its narrator: the scripted timing, music and effects only)
//
// A scene is scenes/<scene>.html. It sets `window.scene = { width, height, duration, seek(t) }`
// and resolves `window.sceneReady` once fonts, icons and images are in. Nothing on the page moves
// by the wall clock: every frame is drawn by seek(t), so a render is the same on every run and
// never drops a frame, however slow the machine.
//
// A narrated scene (the tutorials, the promo) also lists the lines it wants read, as
// scene.voiceLines. Before it renders one, this has vo.py speak them (with $VO_PYTHON, see vo.py
// for the setup), then opens the scene again with the clips' manifest as ?vo=, so the scene can
// time itself to the voice; still and frames do the same, so they show what the video shows.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { spawn, spawnSync } from 'node:child_process';
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
    if (!argv[i].startsWith('--')) out._.push(argv[i]);
    else if (argv[i + 1] === undefined || argv[i + 1].startsWith('--')) out[argv[i].slice(2)] = true;   // a flag
    else out[argv[i].slice(2)] = argv[i + 1], i++;
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

// The Python that runs vo.py (kokoro-onnx, soundfile, numpy): $VO_PYTHON, else the venv Sonary's
// video tools keep on this machine.
export const VO_PYTHON = process.env.VO_PYTHON || path.join(process.env.HOME || '', 'FlutterProjects/audio_ai/tool/store/video/.cache/venv/bin/python');

// Speaks a narrated scene's lines (vo.py, cached by text) and returns the query that hands the
// scene their manifest; a scene with nothing to say gets its query back as it was.
export async function narrate(browser, port, scene, query = '') {
  const { page } = await openScene(browser, port, scene, query);
  const lines = await page.evaluate(() => window.scene.voiceLines || []);
  await page.close();
  if (!lines.length) return query;
  const name = [scene, ...query.split('&').filter(Boolean).map((kv) => kv.replace(/[^\w-]+/g, '-'))].join('-');
  const dir = path.join(ROOT, 'build/vo');
  fs.mkdirSync(dir, { recursive: true });
  const src = path.join(dir, `${name}.lines.json`), manifest = path.join(dir, `${name}.json`);
  fs.writeFileSync(src, JSON.stringify(lines, null, 1));
  process.stderr.write(`${scene}: narrator, ${lines.length} lines\n`);
  await new Promise((res, rej) => {
    const p = spawn(VO_PYTHON, ['vo.py', 'lines', src, manifest], { stdio: 'inherit', cwd: ROOT });
    p.on('error', (e) => rej(new Error(`vo.py could not start (${VO_PYTHON}): ${e.message}; see the top of vo.py`)));
    p.on('close', (c) => (c === 0 ? res() : rej(new Error('vo.py ' + c))));
  });
  fs.rmSync(src);
  return `${query ? query + '&' : ''}vo=/build/vo/${name}.json`;
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
  const sound = await soundOf(page, from, end);
  await page.close();
  return { ...meta, ...sound, duration: end - from };
}

// What a scene says it sounds like, and its chapters and subtitles for the sidecars.
async function soundOf(page, from, end) {
  const sound = await page.evaluate(() => ({ cues: window.scene.cues || [], music: window.scene.music || null, musicGain: window.scene.musicGain ?? 0.55,
    voice: window.scene.voice || [], narrator: window.scene.narrator || null, chapters: window.scene.chapters || null, subtitles: window.scene.subtitles || null,
    steps: window.scene.steps || null, series: window.scene.series || null }));
  // A part render keeps the narrator's lines that start inside it, on its own clock.
  sound.voice = sound.voice.filter((v) => v.t >= from && v.t < end).map((v) => ({ ...v, t: v.t - from }));
  return sound;
}

// New sound under a video already rendered from this scene, its picture kept as it is: for a
// change to the mix or the levels. Refuses if the scene's length no longer matches the video's.
export async function remix(browser, port, scene, video, { query = '' } = {}) {
  const { page, meta } = await openScene(browser, port, scene, query);
  const sound = await soundOf(page, 0, meta.duration);
  await page.close();
  const probe = spawnSync('ffprobe', ['-v', 'error', '-select_streams', 'v:0', '-show_entries', 'stream=duration', '-of', 'csv=p=0', video], { encoding: 'utf8' });
  const has = parseFloat(probe.stdout);
  if (!(Math.abs(has - meta.duration) < 0.1)) throw new Error(`${video} is ${has} s, the scene ${meta.duration.toFixed(3)} s: render it again instead`);
  const tmp = video.replace(/\.mp4$/, '.remix.mp4');
  await addSound(video, tmp, { ...meta, ...sound });
  fs.renameSync(tmp, video);
  return { ...meta, ...sound };
}

// Puts sound under a silent render: the scene's music bed (audio.py), its cue sheet's effects and
// the narrator's clips, with the music ducked under the voice.
export function addSound(silent, out, { duration, cues, music, musicGain, voice = [] }) {
  const run = (cmd, a) => new Promise((res, rej) => {
    const p = spawn(cmd, a, { stdio: 'inherit', cwd: ROOT });
    p.on('close', (c) => (c === 0 ? res() : rej(new Error(`${cmd} ${a.join(' ')} -> ${c}`))));
  });
  const bed = out + '.bed.wav';
  const cueFile = out + '.cues.json';
  fs.writeFileSync(cueFile, JSON.stringify({ duration, cues, musicGain, music: music || 'calm', voice }, null, 1));
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
export function writeSidecars(out, { subtitles, chapters, steps, duration, series, voice = [], narrator = null }) {
  const base = out.replace(/\.mp4$/, '');
  fs.writeFileSync(base + '.srt', subtitles.map((c, i) => `${i + 1}\n${stamp(c.a)} --> ${stamp(c.b)}\n${c.text}\n`).join('\n'));
  const narration = voice.length ? { narrator, lines: voice.map(({ key, t, dur }) => ({ key, at: t, end: Math.round((t + dur) * 1000) / 1000 })) } : null;
  fs.writeFileSync(base + '.json', JSON.stringify({ series, duration, chapters, steps, subtitles, narration }, null, 1));
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
    // --novo (a flag, no value) leaves the narrator out; a thumbnail never has one.
    const plain = a.query || '';
    const query = 'novo' in a || /(^|&)thumb=/.test(plain) ? plain : await narrate(browser, port, scene, plain);
    if (mode === 'still') await still(browser, port, scene, out, { t: +(a.t || 0), query });
    else if (mode === 'video') {
      const silent = a.silent ? out : out.replace(/\.mp4$/, '.silent.mp4');
      const info = await video(browser, port, scene, silent, { fps: +(a.fps || 30), query, from: +(a.from || 0), to: a.to ? +a.to : null, crf: +(a.crf || 18), tune: a.tune || null });
      if (!a.silent) { await addSound(silent, out, info); fs.rmSync(silent); }
      if (info.subtitles) writeSidecars(out, info);
    }
    else if (mode === 'remix') {
      const info = await remix(browser, port, scene, out, { query });
      if (info.subtitles) writeSidecars(out, info);
    }
    else if (mode === 'frames') await frameDump(browser, port, scene, out, { fps: +(a.fps || 30), every: +(a.every || 1), query });
    else throw new Error('mode must be still, video, remix or frames');
  } finally {
    await browser.close();
    server.close();
  }
}
