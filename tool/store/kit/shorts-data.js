// The button-animation shorts: one per animation, 1080 x 1920 for YouTube Shorts and TikTok. Pure
// data (no DOM), so shorts.mjs can read it in Node to write the frame renderer's script.
//
// Each short is a few lines for a happy, laid-back narrator, and the presses of the phone's real
// volume keys the animation answers. A press is at a time `at`, or on a word the narrator says
// (`on: [lineKey, word]`, then `dt` from when that word starts), so the finger lands on "up" or on
// each "tap". `hold` keeps the key down that long (the balloon fills while it is held).

// The shorts' narrator: Kokoro's af_heart, warm and bright, where the tutorials have af_bella.
export const SHORT_VOICE = { voice: 'af_heart', speed: 1.0 };

// Where the volume keys are, down the right side of an upright phone (ButtonBumpStore.defaultPlace):
// the device in the shorts has its keys exactly there, so the press shows beside the key pressed.
export const KEYS = {
  up: { position: 0.27, lengthDp: 52 },
  down: { position: 0.36, lengthDp: 52 },
  power: { position: 0.47, lengthDp: 48 },
};

export const SHORTS = [
  {
    id: 'water', style: 'water', name: 'Water', wallpaper: 'ocean', level: 0.33,
    base: ['#05304A', '#081530'], glows: [['#22D3EE', 0.42], ['#3B82F6', 0.5], ['#818CF8', 0.3]], hl: '#67E8F9',
    lines: [
      { key: 'l1', at: 0.3, text: 'Okay, watch this. I just press volume up...' },
      { key: 'l2', at: 3.35, text: 'and my whole screen turns into water!' },
      { key: 'l3', at: 6.05, text: 'Every press drops a little ripple. So relaxing.' },
      { key: 'l4', end: true, text: 'Water, in Gesture Volume. Free on Google Play!' },
    ],
    presses: [
      { on: ['l1', 'up...'], dt: -0.04 }, { at: 4.25 }, { at: 4.75 },
      { on: ['l3', 'press'], dt: 0 }, { at: 7.05 }, { at: 7.65 }, { at: 8.25 }, { at: 9.0 }, { at: 9.25 },
    ],
    // Ripples cross the whole glass: a gentle push in, the upper half in view.
    cam: { fx: 0.6, fy: 0.36, s: 1.32 },
    endAt: 10.2,
  },
  {
    id: 'fish', style: 'fish', name: 'Fish jump', wallpaper: 'dusk', level: 0.4,
    base: ['#062C38', '#0A1630'], glows: [['#2DD4BF', 0.45], ['#FB923C', 0.32], ['#38BDF8', 0.4]], hl: '#FDBA74',
    lines: [
      { key: 'l1', at: 0.3, text: 'Psst. Press your volume button...' },
      { key: 'l2', at: 2.55, text: 'and a little fish jumps right out of the edge!' },
      { key: 'l3', at: 5.25, text: 'Tap, tap, tap, and the whole family comes out to play.' },
      { key: 'l4', end: true, text: 'Fish jump, in Gesture Volume. Free on Google Play!' },
    ],
    presses: [
      { on: ['l1', 'button...'], dt: 0.02 }, { on: ['l2', 'jumps'], dt: 0 },
      { on: ['l3', 'Tap,', 0], dt: -0.02 }, { on: ['l3', 'tap,', 0], dt: -0.02 }, { on: ['l3', 'tap,', 1], dt: -0.02 },
      { at: 7.35 }, { at: 7.6 }, { at: 7.85 },
    ],
    cam: { fx: 0.74, fy: 0.3, s: 2.2, py: 1110 },
    endAt: 9.4,
  },
  {
    id: 'frog', style: 'frog', name: 'Frog jump', wallpaper: 'sunset', level: 0.4,
    base: ['#07301F', '#0A1A16'], glows: [['#4ADE80', 0.42], ['#22D3EE', 0.25], ['#A3E635', 0.3]], hl: '#86EFAC',
    lines: [
      { key: 'l1', at: 0.3, text: 'Meet the frog that lives in my volume button.' },
      { key: 'l2', at: 3.15, text: 'One press, and hop! He snaps up a fly.' },
      { key: 'l3', at: 6.15, text: 'Press again, and his friends join in.' },
      { key: 'l4', end: true, text: 'Frog jump, in Gesture Volume. Free on Google Play!' },
    ],
    presses: [
      { on: ['l1', 'button.'], dt: -0.05 }, { on: ['l2', 'hop!'], dt: -0.06 },
      { on: ['l3', 'Press'], dt: 0 }, { at: 7.0 }, { at: 7.3 }, { at: 7.6 },
    ],
    cam: { fx: 0.74, fy: 0.3, s: 2.2, py: 1110 },
    endAt: 9.2,
  },
  {
    id: 'cat', style: 'cat', name: 'Cat', wallpaper: 'ocean', level: 0.33,
    base: ['#2E1609', '#1A0E16'], glows: [['#FDBA74', 0.45], ['#F472B6', 0.3], ['#FCD34D', 0.3]], hl: '#FDBA74',
    lines: [
      { key: 'l1', at: 0.3, text: 'Every time I turn the volume up...' },
      { key: 'l2', at: 2.4, text: 'this little cat peeks in to say hi!' },
      { key: 'l3', at: 4.75, text: 'Keep pressing, and watch her react.' },
      { key: 'l4', end: true, text: 'The cat, in Gesture Volume. Free on Google Play!' },
    ],
    presses: [
      { on: ['l1', 'up...'], dt: -0.03 }, { at: 3.55 },
      { at: 4.85 }, { at: 5.25 }, { at: 5.65 }, { at: 6.05 }, { at: 6.45 }, { at: 6.85 }, { at: 7.25 },
    ],
    cam: { fx: 0.8, fy: 0.29, s: 2.5, py: 1080 },
    endAt: 8.7,
  },
  {
    id: 'swing', style: 'swing', name: 'Swing women', wallpaper: 'dusk', level: 0.4,
    base: ['#211041', '#0E0A26'], glows: [['#A78BFA', 0.5], ['#F472B6', 0.35], ['#60A5FA', 0.3]], hl: '#F9A8D4',
    lines: [
      { key: 'l1', at: 0.3, text: 'My volume button has superheroes now.' },
      { key: 'l2', at: 3.0, text: 'Press it, and she swings across the screen on a line of silk!' },
      { key: 'l3', at: 6.25, text: 'Every press sends another one. Go, team!' },
      { key: 'l4', end: true, text: 'Swing women, in Gesture Volume. Free on Google Play!' },
    ],
    presses: [
      { on: ['l1', 'now.'], dt: -0.05 }, { on: ['l2', 'Press'], dt: 0 },
      { on: ['l3', 'press'], dt: 0 }, { at: 6.95 }, { at: 7.5 }, { at: 8.05 }, { at: 8.45 },
    ],
    // They swing across the top of the screen: the upper half in view.
    cam: { fx: 0.52, fy: 0.27, s: 1.68, py: 1010 },
    endAt: 9.7,
  },
  {
    id: 'balloon', style: 'balloon', name: 'Balloons', wallpaper: 'midnight', level: 0.4,
    base: ['#0F2247', '#0B1030'], glows: [['#93C5FD', 0.45], ['#F9A8D4', 0.35], ['#FDE68A', 0.3]], hl: '#FDE68A',
    lines: [
      { key: 'l1', at: 0.3, text: 'Want to see something fun? Press volume up.' },
      { key: 'l2', at: 3.2, text: 'A balloon floats away. Press a few times for a bunch!' },
      { key: 'l3', at: 6.7, text: 'Now hold the button. Bigger, bigger...' },
      // Said as it bursts: placed by the burst, which the renderer reports (BALLOON_BURST below).
      { key: 'l4', end: true, text: 'Pop! Balloons, in Gesture Volume. Free on Google Play!' },
    ],
    presses: [
      { on: ['l1', 'up.'], dt: -0.04 },
      { on: ['l2', 'Press'], dt: 0 }, { on: ['l2', 'Press'], dt: 0.25 }, { on: ['l2', 'Press'], dt: 0.5 }, { on: ['l2', 'Press'], dt: 0.75 },
      // Held until it bursts (FILL_S + SWELL_S = 3.28 s in BumpEffectsBalloon), let go just after.
      { on: ['l3', 'hold'], dt: -0.05, hold: 3.42 },
    ],
    cam: { fx: 0.68, fy: 0.25, s: 1.6, py: 1060 },
    burstAfter: 3.28,
  },
];

// What each short is posted with: its YouTube Shorts title, and its TikTok caption. The name is
// always "Gesture Volume", two words; only the hashtag runs them together.
export const PLAY = 'https://play.google.com/store/apps/details?id=com.newagedevs.gesturevolume';
export const COPY = {
  water: { title: 'Press volume up and your screen turns into water 💧 | Gesture Volume', hook: 'Press volume up and your whole screen turns into water 💧' },
  fish: { title: 'A fish jumps out of my volume button 🐟 | Gesture Volume', hook: 'A little fish jumps out every time I press volume up 🐟' },
  frog: { title: 'The frog that lives in my volume button 🐸 | Gesture Volume', hook: 'One press and the frog hops out to snap up a fly 🐸' },
  cat: { title: 'A cat peeks in every time I turn the volume up 🐱 | Gesture Volume', hook: 'Turn the volume up and a little cat peeks in to say hi 🐱' },
  swing: { title: 'My volume button has superheroes now 🦸‍♀️ | Gesture Volume', hook: 'Every press sends a hero swinging across the screen on a line of silk 🦸‍♀️' },
  balloon: { title: 'Hold the volume button until the balloon pops 🎈 | Gesture Volume', hook: 'Tap for a balloon, hold the key and watch it grow until it pops 🎈' },
};
export const shortDescription = (S) => `${COPY[S.id].hook}

${S.name} is one of 32 button animations in Gesture Volume: press a volume key and the animation plays right beside it, with a sound of its own. Water, Fish jump, Frog jump, Cat, Swing women, Balloons and more, all free.

Gesture Volume on Google Play: ${PLAY}

#Shorts #Android #GestureVolume`;
export const shortTags = (S) => ['Gesture Volume', 'button animation', 'volume button', S.name, 'Android', 'Android tips', 'phone tricks', 'Shorts'];
export const tiktokCaption = (S) => `${COPY[S.id].hook} ${S.name} is one of 32 free button animations in Gesture Volume, on Google Play. #android #androidtips #phonetricks #volumebutton #GestureVolume`;

export const byId = (id) => {
  const s = SHORTS.find((x) => x.id === id);
  if (!s) throw new Error(`no short "${id}"`);
  return s;
};
