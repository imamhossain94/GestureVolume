// Tutorial 4, "The Deck": swipe it out, tiles that act and tiles that open cards, the timer,
// closing it, choosing what is in it, and a coin to toss.
import { clamp, lerp, Ease, icon } from '../core.js';
import { EdgeBar, PRESETS, loadDrawables } from '../quickpanel.js';
import { Deck, DECK_ICONS, volumeCard, timerCard, coinCard, coinAt } from '../deck.js';
import { indicator, SYSTEM_ICONS } from '../system.js';
import { prog } from '../director.js';
import { deckSteps, TILES_AFTER, DECK_SETTINGS_ICONS } from './deck-settings.js';

export const WALLPAPER = 'forest';
export const ICONS = [...DECK_ICONS, ...SYSTEM_ICONS, ...DECK_SETTINGS_ICONS, 'round/touch_app'];
export const prepare = () => loadDrawables(['ic_vol_increase']);

const PEOPLE = [{ name: 'Mom', via: 'call' }, { name: 'Alex Kim', via: 'sms' }];

// One Deck "visit": opened by a swipe at `openAt`, closed at `closeAt` (tap outside).
function visit(deck, openAt, closeAt) {
  return {
    openAt, closeAt,
    open: (t) => (t < openAt ? 0 : t < closeAt ? prog(t, openAt, 0.21) : 1 - prog(t, closeAt, 0.21)),
    up: (t) => t >= openAt && t < closeAt + 0.21,
    bar: (t) => (t < openAt - 0.1 ? 1 : t < closeAt + 0.21 ? 1 - prog(t, openAt - 0.1, 0.16) : prog(t, closeAt + 0.21, 0.16)),
  };
}

export async function script(T) {
  const { phone } = T;
  const dock = new EdgeBar(phone, PRESETS.dock);
  const D = dock.rect;
  const bx = D.x + D.w / 2 - 1, by = D.y + D.h / 2;
  const deck1 = new Deck(phone, dock, { people: PEOPLE, apps: ['camera', 'music'] });
  const deck2 = new Deck(phone, dock, { people: PEOPLE, apps: ['camera', 'music'], tiles: TILES_AFTER });
  const toast = indicator('Flashlight on', icon('filled/flashlight_on', 20, '#fff'));
  phone.overlay.appendChild(toast);
  const swipeIn = (at, enterAt) => {
    T.hand({ at: enterAt, enter: [bx, by + 10], tilt: -40 }, { at, swipe: [bx, by + 10], to: [300, by + 16], dur: 0.32 });
    return at + 0.11 + 0.14;                                   // the Deck fires at 24 dp, mid-stroke
  };

  // --- Open it -------------------------------------------------------------------------------------------
  T.chapter('Open the Deck');
  const s1 = T.step('Swipe in from the tab', 'Swipe from the Dock toward the middle of the screen. Out slides the Deck: your people, your apps and your tools.', 7.4);
  T.cam({ at: s1.at + 0.7, cx: 250, cy: 330, s: 1.5, dur: 0.8 });
  const open1 = swipeIn(s1.at + 1.5, s1.at + 0.8);
  T.hand({ at: s1.at + 2.3, leave: true });
  const people = deck1.itemCentre(0), lastApp = deck1.itemCentre(3), firstTile = deck1.tileCentre('search'), lastTile = deck1.tileCentre('notes');
  T.ring(open1 + 0.9, 2.0, { x: deck1.stripX + 6, y: people[1] - 24, w: 52, h: lastApp[1] - people[1] + 48 }, { pad: 2, radius: 28 });
  T.ring(open1 + 2.9, 2.0, { x: deck1.stripX + 6, y: firstTile[1] - 24, w: 52, h: Math.min(deck1.stripY + deck1.stripH - 10, lastTile[1] + 24) - firstTile[1] + 24 }, { pad: 2, radius: 28 });

  const s2 = T.step('Tap a tile', 'Some tiles act at once. Flashlight switches the torch on, and its tile lights up.', 6.0);
  const flashAt = s2.at + 1.1;
  T.hand({ at: s2.at + 0.4, enter: [deck1.tileCentre('flashlight')[0] + 10, deck1.tileCentre('flashlight')[1] + 60] }, { at: flashAt, tap: deck1.tileCentre('flashlight'), sound: 'toggle' },
    { at: flashAt + 0.9, leave: true });

  const s3 = T.step('Or open a card', 'Others open a card beside the strip. Volume has sliders for media, ringtone and alarm.', 7.4);
  const volAt = s3.at + 0.9;
  T.hand({ at: volAt - 0.55, enter: [deck1.tileCentre('volume')[0] + 10, deck1.tileCentre('volume')[1] + 60] }, { at: volAt, tap: deck1.tileCentre('volume') });
  // The media slider: 56 dp label, then a 232 dp slider; drag it from 60 % to 85 %.
  const cardX = deck1.cardX + 16, sliderY = deck1.stripY + 6 + 48 + 24;
  const sx = (v) => cardX + 56 + 2 + 228 * v;
  const dragAt = volAt + 1.3;
  T.hand({ at: dragAt, drag: [[sx(0.6), sliderY], [sx(0.6), sliderY], [sx(0.86), sliderY]], dur: 1.2, ease: (k) => (k < 0.25 ? 0 : Ease.inOut((k - 0.25) / 0.75)) });
  const mediaAt = (t) => (t < dragAt + 0.11 + 0.3 ? 0.6 : lerp(0.6, 0.86, Ease.inOut(clamp((t - dragAt - 0.11 - 0.3) / 0.9))));

  const s4 = T.step('Start a timer', 'Tap Timer, pick a length and Start. It keeps counting after the Deck closes.', 7.4);
  const timerAt = s4.at + 0.7;
  T.hand({ at: timerAt, tap: deck1.tileCentre('timer') });
  const idle = timerCard({ time: '05:00' });
  deck1.showCard('timer', idle);
  const startEl = [...idle.querySelectorAll('div')].find((d) => d.textContent === 'Start');
  const startR = T.rect(startEl);
  const startAt = timerAt + 1.6;
  T.hand({ at: startAt, tap: [startR.cx, startR.cy] });
  const runFrom = startAt + 0.06;

  const s5 = T.step('Close it', 'Tap anywhere outside, or press Back. Left alone, it closes itself after 10 seconds.', 5.6);
  const closeAt = s5.at + 0.9;
  T.cam({ at: s5.at + 0.4, cx: 206, cy: 457, s: 1.0, dur: 0.7 });
  T.hand({ at: closeAt, tap: [130, 640], sound: 'close', lead: 0.5 }, { at: closeAt + 0.7, leave: true });
  const v1 = visit(deck1, open1, closeAt + 0.18);

  // --- Settings: Deck, tiles, apps and people (deck-settings.js) --------------------------------------------
  const settings = await deckSteps(T);

  // --- A coin ----------------------------------------------------------------------------------------------
  T.chapter('Heads or tails');
  const s8 = T.step('Toss a coin', 'Coin toss now waits at the end of the strip: scroll down to it, tap it, then tap the coin.', 10.2);
  T.cam({ at: s8.at + 0.4, cx: 230, cy: 340, s: 1.45, dur: 0.8 });
  const open2 = swipeIn(s8.at + 1.2, s8.at + 0.6);
  // The strip holds more than fits: drag it up to bring the last tiles in.
  const maxScroll = Math.max(0, (10 + 11 * 44 + 10 * 6 + 10 + 15) - deck2.stripH);
  const sx2 = deck2.stripX + 32, scrollAt = s8.at + 2.2;
  T.hand({ at: scrollAt, drag: [[sx2, deck2.stripY + 440], [sx2, deck2.stripY + 440 - maxScroll - 20]], dur: 0.5, lead: 0.45 });
  const stripScroll = (t) => maxScroll * Ease.fastOutSlowIn(clamp((t - scrollAt - 0.11) / 0.5));
  const coinTileAt = s8.at + 3.5;
  const coinItem = deck2.tileCentre('coin', maxScroll);
  T.hand({ at: coinTileAt, tap: coinItem });
  const coinEl = coinCard();
  deck2.showCard('coin', coinEl);
  const coinCanvas = coinEl.querySelector('canvas.coin');
  const coinR = T.rect(coinCanvas);
  const tossAt = coinTileAt + 1.4;
  T.hand({ at: tossAt, tap: [coinR.cx, coinR.y + coinR.h - 57], sound: 'coin' }, { at: tossAt + 0.7, leave: true });
  const landAt = tossAt + 0.06 + 1.1;
  const v2 = visit(deck2, open2, s8.end + 3.0);

  // --- Sound ------------------------------------------------------------------------------------------------
  T.cue(open1, 'open', 0.5); T.cue(open2, 'open', 0.5);
  T.cue(landAt, 'success', 0.35);

  // --- Every frame ------------------------------------------------------------------------------------------
  let cardKey = '';
  const card1 = (t) => (t < volAt + 0.06 ? null : t < timerAt + 0.06 ? 'volume' : 'timer');
  const timerRun = timerCard({ time: '05:00', running: true });
  let coinState = '';
  T.on((t) => {
    const inSettings = settings.covers(t);
    // Visit 1.
    deck1.root.style.display = v1.up(t) ? 'block' : 'none';
    if (v1.up(t)) {
      const which = card1(t);
      const k = which === 'volume' ? 'v' + Math.round(mediaAt(t) * 200) : which === 'timer' ? (t >= runFrom ? 'r' + Math.floor(t - runFrom) : 'i') : '';
      if (k !== cardKey) {
        cardKey = k;
        if (which === 'volume') deck1.showCard('volume', volumeCard({ media: mediaAt(t) }));
        else if (which === 'timer' && t < runFrom) deck1.showCard('timer', idle);
        else if (which === 'timer') {
          const left = 300 - Math.floor(t - runFrom);
          timerRun.querySelector('.time').textContent = `${String(Math.floor(left / 60)).padStart(2, '0')}:${String(left % 60).padStart(2, '0')}`;
          deck1.showCard('timer', timerRun);
        }
      }
      deck1.setActive('flashlight', t >= flashAt + 0.2);
      deck1.setActive('volume', which === 'volume');
      deck1.setActive('timer', which === 'timer');
      const cardIn = which === 'volume' ? prog(t, volAt + 0.06, 0.22) : which === 'timer' ? 1 : 0;
      deck1.set({ open: v1.open(t), card: cardIn });
      if (!which && deck1.card) deck1.card.style.visibility = 'hidden';
    }
    // Visit 2: the coin.
    deck2.root.style.display = v2.up(t) ? 'block' : 'none';
    if (v2.up(t)) {
      const shown = t >= coinTileAt + 0.06;
      deck2.setActive('coin', shown);
      deck2.set({ open: v2.open(t), card: shown ? prog(t, coinTileAt + 0.06, 0.22) : 0, scroll: stripScroll(t) });
      const landed = t >= landAt;
      const ks = landed ? 'landed' : 'idle';
      if (ks !== coinState) {
        coinState = ks;
        coinEl.replaceChildren(...coinCard(landed ? { result: 'Heads', streak: '', heads: 1, tails: 0 } : {}).childNodes);
      }
      // drawCoin looks its canvas up when called, so it paints whichever children are current.
      coinEl.drawCoin(coinAt(clamp((t - tossAt - 0.06) / 1.1), 0, 6));
    }
    dock.draw({ alpha: inSettings ? 0 : Math.min(v1.bar(t), v2.bar(t)) });
    toast.style.display = t >= flashAt + 0.18 && t < flashAt + 1.08 ? 'flex' : 'none';
  });
}

export const THUMB = { t: 28.5, title: 'The Deck', cam: { cx: 230, cy: 240, s: 2.0, deg: -5 } };
