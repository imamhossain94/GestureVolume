// The button-animation shorts (kit/shorts-data.js), 1080 x 1920 at 60 fps, for YouTube Shorts and TikTok.
//
//   node shorts.mjs frames [id ...] [--app <checkout>]   narrator, then the animation's frames (bump.mjs)
//   node shorts.mjs video  [id ...]                      store/shorts/<nn>-<id>.mp4, with sound
//   node shorts.mjs all    [id ...] [--app <checkout>]   both
//
// With no ids, every short. The frames come before the video because the presses are placed on the
// narrator's words: vo.py speaks the lines, the scene places the presses, and the frame renderer
// plays exactly those presses into ButtonBumpView. --app is a checkout with the button animation
// (see bump.mjs); the default is this repository.
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';
import { serve, narrate, openScene, BROWSER_ARGS } from './render.mjs';
import { renderBump } from './bump.mjs';
import { SHORTS } from './kit/shorts-data.js';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.resolve(ROOT, '../../store/shorts');

const argv = process.argv.slice(2);
const ai = argv.indexOf('--app');
const app = ai >= 0 ? path.resolve(argv[ai + 1]) : undefined;
if (ai >= 0) argv.splice(ai, 2);
const [mode, ...ids] = argv;
const todo = (ids.length ? ids : SHORTS.map((s) => s.id)).map((id) => {
  const n = SHORTS.findIndex((s) => s.id === id);
  if (n < 0) throw new Error(`no short "${id}"`);
  return { id, n: n + 1 };
});
if (!['frames', 'video', 'all'].includes(mode)) throw new Error('usage: node shorts.mjs frames|video|all [id ...] [--app <checkout>]');

if (mode === 'frames' || mode === 'all') {
  const server = await serve();
  const port = server.address().port;
  const browser = await chromium.launch({ args: BROWSER_ARGS });
  const clips = [];
  try {
    for (const { id } of todo) {
      const query = await narrate(browser, port, 'short', `id=${id}`);
      const { page } = await openScene(browser, port, 'short', query);
      clips.push(await page.evaluate(() => window.scene.bump));
      await page.close();
    }
  } finally {
    await browser.close();
    server.close();
  }
  const spec = path.join(ROOT, 'build/bump/shorts.spec.json');
  fs.mkdirSync(path.dirname(spec), { recursive: true });
  fs.writeFileSync(spec, JSON.stringify({ width: 1080, height: 2400, fps: 60, clips }, null, 1));
  renderBump(spec, app ? { app } : {});
}

if (mode === 'video' || mode === 'all') {
  fs.mkdirSync(OUT, { recursive: true });
  for (const { id, n } of todo) {
    const out = path.join(OUT, `${String(n).padStart(2, '0')}-${id}.mp4`);
    const r = spawnSync('node', ['render.mjs', 'video', 'short', out, '--query', `id=${id}`, '--fps', '60', '--crf', '18'], { cwd: ROOT, stdio: 'inherit' });
    if (r.status !== 0) throw new Error(`render of ${id} failed`);
  }
}
