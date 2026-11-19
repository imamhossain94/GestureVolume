// Renders the button animation frame for frame, as the app draws it: ButtonBumpView, the very view
// the overlay puts on the glass, run on Robolectric's real Skia (native graphics) on a clock that
// moves 1/fps a frame. Frames are PNG with straight alpha, for the shorts and the tutorial to lay
// over a screen of their own; meta.json beside them lists the sounds the app would play, and when.
//
//   node bump.mjs <spec.json> [--app <checkout>]
//   node bump.mjs scene <scene> [query] [--app <checkout>]   the clips a scene plays (scene.bumpClips,
//       or scene.bump), timed on its narrator: e.g. node bump.mjs scene tutorial id=button-animation
//
// A spec is { width, height, fps, clips: [...] }: a clip is { name, style, duration, events } (key
// down / up and presses with their level; see bump/BumpFrameRender.kt), or { kind: "tiles" } for
// every style's gallery tile. Frames go to build/bump/<clip name>/00000.png ...
//
// Nothing in the app's own build changes. The harness is a copy of the app's sources in
// build/bump-harness (what is committed in --app, default this repository), with Robolectric added
// to its unit tests and bump/BumpFrameRender.kt among them; it is refreshed on every run. Needs a
// JDK 17+ (Android Studio's own is used when JAVA_HOME is not set), and Robolectric's Android 15
// runtime, which Gradle downloads on the first run.
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const HARNESS = path.join(ROOT, 'build/bump-harness');
const OUT = path.join(ROOT, 'build/bump');
const VIEW = 'app/src/main/java/com/newagedevs/gesturevolume/ui/view/ButtonBumpView.kt';
const ROBOLECTRIC = '4.18.1';

function run(cmd, args, opts = {}) {
  const r = spawnSync(cmd, args, { stdio: 'inherit', ...opts });
  if (r.status !== 0) throw new Error(`${cmd} ${args.join(' ')} -> ${r.status}`);
}

export function prepareHarness(app) {
  if (!fs.existsSync(path.join(app, VIEW))) throw new Error(`${app} has no ${VIEW}: pass --app <a checkout with the button animation>`);
  // The app's sources as committed in that checkout (its HEAD), so work in progress there, which
  // may not build, stays out of it. Gradle's own caches in the harness are kept between runs.
  for (const f of ['app', 'gradle']) fs.rmSync(path.join(HARNESS, f), { recursive: true, force: true });
  fs.mkdirSync(HARNESS, { recursive: true });
  const files = ['app', 'gradle', 'gradlew', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties'];
  const tar = spawnSync('sh', ['-c', `git -C "${app}" archive HEAD ${files.join(' ')} | tar -x -C "${HARNESS}"`], { stdio: 'inherit' });
  if (tar.status !== 0) throw new Error(`could not copy ${app}'s sources into the harness`);
  for (const f of ['local.properties', 'local.properties.example']) {
    if (fs.existsSync(path.join(app, f))) fs.copyFileSync(path.join(app, f), path.join(HARNESS, f));
  }
  const gradle = path.join(HARNESS, 'app/build.gradle.kts');
  let s = fs.readFileSync(gradle, 'utf8');
  s = s.replace(/(\n {4}buildFeatures \{[^}]*\}\n)/, `$1
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "4g"
                it.systemProperty("bump.spec", providers.gradleProperty("bumpSpec").getOrElse(""))
                it.systemProperty("bump.out", providers.gradleProperty("bumpOut").getOrElse(""))
                it.systemProperty("bump.ffmpeg", providers.gradleProperty("bumpFfmpeg").getOrElse("ffmpeg"))
                it.outputs.upToDateWhen { false }
                it.testLogging { showStandardStreams = true }
            }
        }
    }
`);
  s = s.replace('    testImplementation(libs.junit)\n', `    testImplementation(libs.junit)\n    testImplementation("org.robolectric:robolectric:${ROBOLECTRIC}")\n`);
  if (!s.includes('bump.spec') || !s.includes('org.robolectric')) throw new Error('could not add Robolectric to the harness build: app/build.gradle.kts has changed shape');
  fs.writeFileSync(gradle, s);
  const test = path.join(HARNESS, 'app/src/test/java/com/newagedevs/gesturevolume/render');
  fs.mkdirSync(test, { recursive: true });
  fs.copyFileSync(path.join(ROOT, 'bump/BumpFrameRender.kt'), path.join(test, 'BumpFrameRender.kt'));
}

export function renderBump(specPath, { app = path.resolve(ROOT, '../..') } = {}) {
  prepareHarness(app);
  const studio = '/Applications/Android Studio.app/Contents/jbr/Contents/Home';
  const env = { ...process.env };
  if (!env.JAVA_HOME && fs.existsSync(studio)) env.JAVA_HOME = studio;
  const ffmpeg = spawnSync('which', ['ffmpeg'], { encoding: 'utf8' }).stdout.trim() || 'ffmpeg';
  fs.mkdirSync(OUT, { recursive: true });
  run('./gradlew', [':app:testDebugUnitTest', '--tests', 'com.newagedevs.gesturevolume.render.BumpFrameRender', '--console=plain', '-q',
    `-PbumpSpec=${path.resolve(specPath)}`, `-PbumpOut=${OUT}`, `-PbumpFfmpeg=${ffmpeg}`], { cwd: HARNESS, env });
}

// The clips a scene plays, as it reports them once its narrator has been heard (the presses are
// placed on the narrator's timing), written as a spec for renderBump.
export async function sceneClips(scene, query = '') {
  const { chromium } = await import('playwright');
  const { serve, narrate, openScene, BROWSER_ARGS } = await import('./render.mjs');
  const server = await serve();
  const browser = await chromium.launch({ args: BROWSER_ARGS });
  try {
    const port = server.address().port;
    const q = await narrate(browser, port, scene, query);
    const { page } = await openScene(browser, port, scene, q);
    const clips = await page.evaluate(() => window.scene.bumpClips || (window.scene.bump ? [window.scene.bump] : []));
    await page.close();
    return clips;
  } finally {
    await browser.close();
    server.close();
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const argv = process.argv.slice(2);
  const i = argv.indexOf('--app');
  const app = i >= 0 ? path.resolve(argv[i + 1]) : undefined;
  if (i >= 0) argv.splice(i, 2);
  if (argv[0] === 'scene') {
    const [, scene, query = ''] = argv;
    const clips = await sceneClips(scene, query);
    if (!clips.length) throw new Error(`${scene} ${query} plays no button animation`);
    const spec = path.join(OUT, `${[scene, ...query.split('&').filter(Boolean).map((kv) => kv.replace(/[^\w-]+/g, '-'))].join('-')}.spec.json`);
    fs.mkdirSync(OUT, { recursive: true });
    fs.writeFileSync(spec, JSON.stringify({ width: 1080, height: 2400, fps: 30, clips }, null, 1));
    process.stderr.write(`${scene}: ${clips.length} clips, ${clips.reduce((n, c) => n + Math.ceil(c.duration * (c.fps || 30)), 0)} frames\n`);
    renderBump(spec, app ? { app } : {});
  } else {
    if (!argv[0]) throw new Error('usage: node bump.mjs <spec.json> | scene <scene> [query]  [--app <checkout>]');
    renderBump(argv[0], app ? { app } : {});
  }
}
