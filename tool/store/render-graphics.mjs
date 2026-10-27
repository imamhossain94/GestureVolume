// Renders the Google Play graphics into store/graphics/: the feature graphic and the eight phone
// screenshots in the Bleed framing. finish.py then flattens them to the 24-bit PNG Play asks for.
//
//   node render-graphics.mjs [--only feature,swipe,deck]
import { chromium } from 'playwright';
import path from 'node:path';
import fs from 'node:fs';
import { serve, still, BROWSER_ARGS } from './render.mjs';

const OUT = path.resolve('../../store/graphics');
const only = process.argv.includes('--only') ? process.argv[process.argv.indexOf('--only') + 1].split(',') : null;
const SHOTS = ['swipe', 'deck', 'rotate', 'fills', 'search', 'appearance', 'menu', 'visibility'];

const server = await serve();
const port = server.address().port;
const browser = await chromium.launch({ args: BROWSER_ARGS });
try {
  if (!only || only.includes('feature')) await still(browser, port, 'feature', `${OUT}/feature-graphic.png`);
  for (const [i, id] of SHOTS.entries()) {
    if (only && !only.includes(id)) continue;
    const n = String(i + 1).padStart(2, '0');
    await still(browser, port, 'shot', `${OUT}/screenshots/bleed/${n}-${id}.png`, { query: `id=${id}&style=bleed`, t: 0 });
    process.stderr.write(`bleed/${n}-${id}\n`);
  }
} finally {
  await browser.close();
  server.close();
}
fs.mkdirSync(OUT, { recursive: true });
