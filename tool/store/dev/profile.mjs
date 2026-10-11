// node dev/profile.mjs <scene> — times a few seeks of a scene, to find slow frames
import { chromium } from 'playwright';
import { serve, openScene } from '../render.mjs';
const server = await serve(); const port = server.address().port;
const flags = process.argv[2] === 'gpu' ? ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] : process.argv[2] === 'none' ? [] : ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--disable-gpu-compositing'];
const browser = await chromium.launch({ args: flags });
const { page } = await openScene(browser, port, 'test_video');
const tm = async (label, fn, n = 5) => { const t0 = Date.now(); for (let i = 0; i < n; i++) await fn(i); console.log(label, ((Date.now() - t0) / n).toFixed(0), 'ms'); };
await tm('seek', (i) => page.evaluate((t) => window.scene.seek(t), 1 + i * 0.1));
await tm('png', () => page.screenshot({ type: 'png' }));
await tm('jpeg', () => page.screenshot({ type: 'jpeg', quality: 95 }));
const cdp = await page.context().newCDPSession(page);
await tm('cdp-png', () => cdp.send('Page.captureScreenshot', { format: 'png' }));
await tm('cdp-jpeg', () => cdp.send('Page.captureScreenshot', { format: 'jpeg', quality: 95 }));
await tm('seek+cdp-jpeg', async (i) => { await page.evaluate((t) => window.scene.seek(t), 1 + i * 0.1); await cdp.send('Page.captureScreenshot', { format: 'jpeg', quality: 95 }); });
await browser.close(); server.close();
