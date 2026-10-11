// Renders the Google Play graphics into store/graphics/: the feature graphic (and its
// alternative), and the nine phone screenshots in both framings. finish.py then flattens them to
// the 24-bit PNG Play asks for.
//
//   node render-graphics.mjs [--only swipe,deck]
import { chromium } from 'playwright';
import path from 'node:path';
import fs from 'node:fs';
import { serve, still, BROWSER_ARGS } from './render.mjs';

const OUT = path.resolve('../../store/graphics');
const only = process.argv.includes('--only') ? process.argv[process.argv.indexOf('--only') + 1].split(',') : null;
const SHOTS = ['swipe', 'deck', 'rotate', 'fills', 'search', 'appearance', 'menu', 'visibility', 'coin'];

const server = await serve();
const port = server.address().port;
const browser = await chromium.launch({ args: BROWSER_ARGS });
try {
  if (!only || only.includes('feature')) {
    await still(browser, port, 'feature', `${OUT}/feature-graphic.png`, { query: 'v=a' });
    await still(browser, port, 'feature', `${OUT}/extras/feature-graphic-b.png`, { query: 'v=b' });
  }
  for (const [i, id] of SHOTS.entries()) {
    if (only && !only.includes(id)) continue;
    const n = String(i + 1).padStart(2, '0');
    for (const style of ['bleed', 'card']) {
      const dir = i < 8 ? `${OUT}/screenshots/${style}` : `${OUT}/extras/${style}`;
      await still(browser, port, 'shot', `${dir}/${n}-${id}.png`, { query: `id=${id}&style=${style}`, t: 0 });
      process.stderr.write(`${style}/${n}-${id}\n`);
    }
  }
} finally {
  await browser.close();
  server.close();
}
fs.mkdirSync(OUT, { recursive: true });
