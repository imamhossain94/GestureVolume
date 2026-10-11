// The Deck tutorial's settings steps: the Deck screen, Deck tiles (Coin toss on, Screenshot off)
// and Quick dial, then home again. Returns when the app is on screen, for hiding the bar.
import { clamp, Ease } from '../core.js';
import { homeAppScreen, HOME_ICONS, SCREEN_ICONS } from '../screens.js';
import { deckScreen, deckTilesScreen, quickDialScreen, DECKSETTINGS_ICONS } from '../decksettings.js';
import { DEFAULT_TILES } from '../deck.js';
import { live } from '../tutorial.js';

export const DECK_SETTINGS_ICONS = [...DECKSETTINGS_ICONS, ...HOME_ICONS, ...SCREEN_ICONS];
export const TILES_AFTER = DEFAULT_TILES.filter((id) => id !== 'screenshot').concat('coin');
const PEOPLE = [{ name: 'Mom', number: '+1 555 0100', via: 'call' }, { name: 'Alex Kim', number: '+1 555 0132', via: 'sms' }];
const sw = (t, at) => Math.round(Ease.fastOutSlowIn(clamp((t - at - 0.06) / 0.25)) * 20) / 20;

export async function deckSteps(T) {
  T.chapter('Choose what is in it');
  const sA = T.step('Open the Deck settings', 'In Gesture Volume, under Advanced features, tap Deck.', 6.6);
  T.cam({ at: sA.at + 0.2, cx: 206, cy: 457, s: 1.0, dur: 0.6 });
  const home = homeAppScreen({ running: true, advanced: true });
  T.hand({ at: sA.at + 0.3, enter: [T.iconRect.cx + 20, T.iconRect.cy + 70] });
  const launched = T.launch(sA.at + 0.9, home);
  const deckAt = sA.at + 3.2;
  T.tap(deckAt, home, 'feature-deck');
  const deck = deckScreen({ open: ['content'] });
  T.show(deckAt + 0.06, deck, 'push');

  const sB = T.step('Turn tiles on or off', 'Tap Deck tiles and switch on the ones you want. Coin toss and Dice start off; Screenshot can go.', 9.0);
  const tilesAt = sB.at + 0.9;
  T.tap(tilesAt, deck, 'row-tiles');
  let coinAt = 1e9, shotAt = 1e9, scrollAt = 1e9, scrollTo = 0;
  const tilesState = (t) => ({
    scroll: Math.round(t < scrollAt ? 0 : Ease.fastOutSlowIn(clamp((t - scrollAt) / 0.6)) * scrollTo),
    enabled: { ...Object.fromEntries(DEFAULT_TILES.map((id) => [id, 1])), screenshot: 1 - sw(t, shotAt), coin: sw(t, coinAt) },
  });
  const tiles = live((s) => deckTilesScreen(s), tilesState, { scroll: 0, enabled: DEFAULT_TILES });
  T.show(tilesAt + 0.06, tiles, 'push');
  // Screenshot is near the top; Coin toss further down the catalogue.
  shotAt = sB.at + 2.4;
  T.tap(shotAt, tiles, 'tile-screenshot');
  const coinRow = T.rect(tiles, 'tile-row-coin');
  scrollTo = Math.max(0, Math.round(coinRow.y - 520));
  scrollAt = sB.at + 3.6;
  coinAt = sB.at + 5.4;
  const coinSw = T.measure(deckTilesScreen({ scroll: scrollTo, enabled: DEFAULT_TILES }), 'tile-coin');
  T.hand({ at: coinAt, tap: [coinSw.cx, coinSw.cy] });

  const sC = T.step('Add people to call', 'Back on the Deck screen, Quick dial puts people in the strip: one tap calls them or opens a message.', 8.6);
  const backR = T.rect(tiles, 'back');
  T.hand({ at: sC.at + 0.6, tap: [backR.cx, backR.cy] });
  T.show(sC.at + 0.66, deck, 'pop');
  const dialAt = sC.at + 2.2;
  T.tap(dialAt, deck, 'row-quick-dial');
  const dial = quickDialScreen({ contacts: PEOPLE });
  T.show(dialAt + 0.06, dial, 'push');
  const c0 = T.rect(dial, 'contact-0'), c1 = T.rect(dial, 'contact-1');
  T.ring(dialAt + 0.8, 2.4, { x: c0.x, y: c0.y, w: c0.w, h: c1.y + c1.h - c0.y }, { pad: 4, radius: 18 });
  T.ring(dialAt + 3.0, 1.8, T.rect(dial, 'add'), { pad: 4, radius: 20 });
  const homeAt = T.goHome(sC.at + 6.6);

  T.on((t) => tiles.render(t));
  return { covers: (t) => t >= launched + 0.2 && t < homeAt + 0.2, tiles: TILES_AFTER };
}
