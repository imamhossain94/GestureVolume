// node dev/geom.mjs <scene> <query> — prints window.scene.geom (where things sit on the phone)
import { chromium } from 'playwright';
import { serve, openScene, BROWSER_ARGS } from '../render.mjs';
const [,, scene, query] = process.argv;
const server = await serve(); const port = server.address().port;
const browser = await chromium.launch({ args: BROWSER_ARGS });
const { page } = await openScene(browser, port, scene, query);
console.log(JSON.stringify(await page.evaluate(() => window.scene.geom), null, 1));
await browser.close(); server.close();
