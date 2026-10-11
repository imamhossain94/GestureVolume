// node dev/frames_at.mjs <scene> <query> <outdir> t1 t2 ...  — stills at chosen times
import { chromium } from 'playwright';
import { serve, openScene, BROWSER_ARGS } from '../render.mjs';
const [,, scene, query, out, ...times] = process.argv;
const server = await serve(); const port = server.address().port;
const browser = await chromium.launch({ args: BROWSER_ARGS });
const { page } = await openScene(browser, port, scene, query);
for (const t of times) {
  await page.evaluate(async (t) => { await window.scene.seek(+t); }, t);
  await page.screenshot({ path: `${out}/t${String(t).padStart(5, '0')}.png` });
}
await browser.close(); server.close();
