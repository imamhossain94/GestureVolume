// node dev/dbg.mjs <scene> <query> <t> <expr> — seeks a scene and prints the value of a JS expression
import { chromium } from 'playwright';
import { serve, openScene, BROWSER_ARGS } from '../render.mjs';
const [,, scene, query, t, expr] = process.argv;
const server = await serve(); const port = server.address().port;
const browser = await chromium.launch({ args: BROWSER_ARGS });
const { page } = await openScene(browser, port, scene, query);
const r = await page.evaluate(async ([t, expr]) => { await window.scene.seek(+t); return eval(expr); }, [t, expr]);
console.log(typeof r === 'string' ? r : JSON.stringify(r, null, 1));
await browser.close(); server.close();
