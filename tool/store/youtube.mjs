// The upload text for YouTube: the promo's, and for each rendered tutorial (its .json beside the
// .mp4) the title, a description with its chapters, tags and the files to upload, plus a voice-over
// script. Writes store/tutorials/youtube.md; gallery.mjs uses entries() for the same text.
//
//   node youtube.mjs
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
export const STORE = path.join(ROOT, '../../store');
const DIR = path.join(STORE, 'tutorials');
export const PLAY = 'https://play.google.com/store/apps/details?id=com.newagedevs.gesturevolume';
const BASE_TAGS = ['Gesture Volume', 'Android', 'volume control', 'edge bar', 'volume slider', 'brightness slider', 'Android tips', 'tutorial'];
const TAGS = {
  'getting-started': ['setup', 'display over other apps', 'permissions', 'first run'],
  'swipe-volume': ['volume button', 'floating volume button', 'swipe volume'],
  'quick-slider': ['quick slider', 'volume keys', 'slider animation', 'shaders'],
  deck: ['side panel', 'shortcuts', 'edge panel', 'timer', 'flashlight'],
  search: ['quick search', 'calculator', 'contacts', 'web search'],
  menu: ['long press menu', 'quick actions', 'glass panel'],
  actions: ['gestures', 'double tap', 'per-app gestures', 'custom actions'],
  appearance: ['customise', 'presets', 'colours', 'position'],
  visibility: ['hide in apps', 'keyboard', 'lock screen', 'media'],
};

export const clock = (t) => { const s = Math.floor(t); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; };

// The promo's own text: what the 30 seconds show, shot by shot.
export const PROMO = {
  title: 'Gesture Volume: volume, brightness and more from the edge of your screen',
  description: `A slim bar on the edge of your screen. Swipe it and the Quick slider follows your finger, swipe inward for the Deck of apps, people and tools, and hold it for a menu of your own.

0:02 Volume on the edge
0:06 Fills that move
0:10 Pull out the Deck
0:15 Answers as you type
0:19 Hold for more
0:23 Make it yours

Gesture Volume on Google Play: ${PLAY}

#GestureVolume #Android`,
  tags: [...BASE_TAGS.filter((t) => t !== 'tutorial'), 'Quick slider', 'Deck', 'app preview'],
  shots: [
    ['0:00', 'The icon and name come up, then move to the corner as the phone rises.', ''],
    ['0:02', 'A swipe up on the Dock: it grows into the Quick slider and steps from 40 to 73.', 'Volume on the edge'],
    ['0:06', 'Fills cut on the beat: plasma, flame, pixels, aurora; a drag to 100 % sets off the Burst.', 'Fills that move'],
    ['0:10', 'A swipe inward opens the Deck; Timer, then Start, and the countdown runs.', 'Pull out the Deck'],
    ['0:15', 'Search: 12*8+4 answers 100, then "al" finds Alex Kim.', 'Answers as you type'],
    ['0:19', 'A tap outside closes the Deck; a hold opens the glass menu; Flashlight on.', 'Hold for more'],
    ['0:23', 'The bar cycles through presets: Classic, Edge in amber, Bold, Dock in teal.', 'Make it yours'],
    ['0:25', 'End card: Gesture Volume, "Volume. Brightness. Everything else."', ''],
  ],
};

export function entries() {
  const files = fs.existsSync(DIR) ? fs.readdirSync(DIR).filter((f) => /^\d\d-.*\.json$/.test(f)).sort() : [];
  return files.map((f) => JSON.parse(fs.readFileSync(path.join(DIR, f), 'utf8'))).filter((d, i) => {
    if (!d.series) console.warn(`${files[i]}: no series data (rendered before it existed); render it again`);
    return d.series;
  }).map((d) => {
    const f = `${String(d.series.n).padStart(2, '0')}-${d.series.id}.json`;
    const base = f.replace(/\.json$/, '');
    const s = d.series;
    const title = `${s.title} | Gesture Volume tutorial ${s.n}`;
    const steps = d.steps.map((x, i) => `${i + 1}. ${x.title}: ${x.text}`).join('\n');
    const chapters = d.chapters.map((c) => `${clock(c.t)} ${c.title}`).join('\n');
    const description = `${s.sub}. Tutorial ${s.n} of ${s.of} in the Gesture Volume series.\n\nIn this video\n${steps}\n\nChapters\n${chapters}\n\n${s.next ? `Next in the series: ${s.next}\n\n` : ''}Gesture Volume on Google Play: ${PLAY}\n\n#GestureVolume #Android`;
    return { base, series: s, duration: d.duration, chapters: d.chapters, steps: d.steps, title, description, tags: [...BASE_TAGS, ...(TAGS[s.id] || [])] };
  });
}

function block(label, text, note = '') {
  return `**${label}**${note ? ` (${note})` : ''}\n\n\`\`\`\n${text}\n\`\`\`\n\n`;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const list = entries();
  let md = `# YouTube uploads\n\nOne entry per video: paste the title, description and tags, upload the captions file under\nSubtitles (English), and set the thumbnail. Chapters are already in each description: YouTube\nreads them from the timestamps (the first at 0:00, each 10 seconds or longer).\n\nAll videos: 1920 × 1080 (the vertical promo 1080 × 1920), 30 fps, H.264 and AAC, loudness about\n−14 LUFS. Put the tutorials in one playlist, in order, so each video's "Up next" card matches.\n\n`;
  md += `## The promo\n\n- Video: \`../video/promo-1080p.mp4\` (0:30), and \`../video/promo-vertical.mp4\` for Shorts\n- Thumbnail: \`../video/promo-thumbnail.png\`\n- Google Play: paste the video's YouTube link into Main store listing › Video (YouTube URL).\n\n`;
  md += block('Title', PROMO.title, `${PROMO.title.length} characters`) + block('Description', PROMO.description) + block('Tags', PROMO.tags.join(', '));
  for (const e of list) {
    md += `## ${e.series.n}. ${e.series.title}\n\n- Video: \`${e.base}.mp4\` (${clock(e.duration)})\n- Captions: \`${e.base}.srt\`\n- Thumbnail: \`thumbnails/${e.base}.png\`\n\n`;
    md += block('Title', e.title, `${e.title.length} characters`) + block('Description', e.description) + block('Tags', e.tags.join(', '), `${e.tags.join(',').length} characters`);
  }
  md += `---\n\n# Voice-over scripts\n\nThe videos carry music and sound effects but no voice. To add narration, read each step as its\ncaption appears; the times below are when each step starts.\n\n`;
  md += list.map((e) => `## ${e.series.n}. ${e.series.title}\n\n` + e.steps.map((x) => `[${clock(x.at)}] **${x.title}.** ${x.text}`).join('\n\n')).join('\n\n') + '\n';
  fs.writeFileSync(path.join(DIR, 'youtube.md'), md);
  console.log(`youtube.md: the promo and ${list.length} tutorials`);
}
