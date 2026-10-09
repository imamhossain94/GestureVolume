// YouTube thumbnails, 1280 x 720: the promo's (feature graphic A at 16:9) and one per rendered
// tutorial (its THUMB moment). Drawn at 1920 x 1080 and scaled down.
//
//   node render-thumbs.mjs [id ...]
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';
import { serve, still, BROWSER_ARGS } from './render.mjs';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const STORE = path.join(ROOT, '../../store');
const only = process.argv.slice(2);
const server = await serve();
const port = server.address().port;
const browser = await chromium.launch({ args: BROWSER_ARGS });
const shrink = (src, out) => {
  spawnSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', src, '-vf', 'scale=1280:720:flags=lanczos', out], { stdio: 'inherit' });
  fs.rmSync(src);
  console.log(path.relative(STORE, out), `${(fs.statSync(out).size / 1024).toFixed(0)} KB`);
};
try {
  if (!only.length || only.includes('promo')) {
    const big = path.join(STORE, 'video/promo-thumbnail.big.png');
    await still(browser, port, 'feature', big, { query: 'v=yt' });
    shrink(big, path.join(STORE, 'video/promo-thumbnail.png'));
  }
  const dir = path.join(STORE, 'tutorials');
  fs.mkdirSync(path.join(dir, 'thumbnails'), { recursive: true });
  for (const f of fs.readdirSync(dir).filter((x) => /^\d\d-.*\.json$/.test(x)).sort()) {
    const { series } = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
    if (!series) { console.warn(`${f}: no series data; render that tutorial again`); continue; }
    if (only.length && !only.includes(series.id)) continue;
    const base = f.replace(/\.json$/, '');
    const big = path.join(dir, 'thumbnails', base + '.big.png');
    await still(browser, port, 'tutorial', big, { query: `id=${series.id}&thumb=1` });
    shrink(big, path.join(dir, 'thumbnails', base + '.png'));
  }
} finally {
  await browser.close();
  server.close();
}
