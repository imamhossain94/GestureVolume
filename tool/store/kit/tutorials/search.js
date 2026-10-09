// Tutorial 5, "Search in the Deck": sums, apps, people, numbers and the web, as you type.
import { clamp, lerp, Ease, icon } from '../core.js';
import { EdgeBar, PRESETS, loadDrawables } from '../quickpanel.js';
import { Deck, DECK_ICONS, searchCard } from '../deck.js';
import { keyboard, indicator, SYSTEM_ICONS } from '../system.js';
import { prog } from '../director.js';

export const WALLPAPER = 'sunset';
export const ICONS = [...DECK_ICONS, ...SYSTEM_ICONS, 'round/arrow_back', 'round/touch_app', 'filled/content_copy'];
export const prepare = () => loadDrawables(['ic_vol_increase']);

const PEOPLE = [{ name: 'Mom', via: 'call' }, { name: 'Alex Kim', via: 'sms' }];
const EVERY = 0.15, ERASE = 0.055;

export async function script(T) {
  const { phone } = T;
  const dock = new EdgeBar(phone, PRESETS.dock);
  const deck = new Deck(phone, dock, { people: PEOPLE, apps: ['camera', 'music'] });
  const D = dock.rect;
  const bx = D.x + D.w / 2 - 1, by = D.y + D.h / 2;
  const card = deck.showCard('search', searchCard({}));
  const content = card.querySelector('.content');
  const kb = { letters: keyboard({ layout: 'letters' }), symbols: keyboard({ layout: 'symbols' }) };
  const kbHost = document.createElement('div');
  kbHost.style.cssText = 'position:absolute;left:0;right:0;bottom:0;height:330px';
  phone.overlay.appendChild(kbHost);
  const toast = indicator('Copied', icon('filled/content_copy', 20, '#fff'));
  phone.overlay.appendChild(toast);

  // --- Open it ------------------------------------------------------------------------------------------
  T.chapter('Open search');
  const s1 = T.step('Open the Deck, tap Search', 'Swipe in from the tab, then tap Search. The keyboard comes up, ready for you to type.', 7.0);
  const swipeAt = s1.at + 1.2;
  const deckOpen = swipeAt + 0.11 + 0.14;                     // fires at 24 dp, while the finger moves
  T.hand({ at: s1.at + 0.5, enter: [bx, by + 10], tilt: -40 }, { at: swipeAt, swipe: [bx, by + 10], to: [300, by + 16], dur: 0.32 });
  const searchTile = deck.tileCentre('search');
  const tapSearch = swipeAt + 1.15;
  T.hand({ at: tapSearch, tap: searchTile }, { at: tapSearch + 0.55, leave: true });
  const searchOpen = tapSearch + 0.06;
  T.cam({ at: s1.at + 0.9, cx: 230, cy: 440, s: 1.12, dur: 0.8 });

  // The queries: each erased (backspace) and then typed, with its result 140 ms after the last key.
  const Q = [];
  let at = s1.end - 0.4;
  const query = (text, layout, step, { pause = 1.0 } = {}) => {
    const prev = Q.length ? Q[Q.length - 1].text : '';
    const clearAt = step.at + 0.35;
    const typeAt = clearAt + prev.length * ERASE + (prev ? 0.2 : 0);
    const doneAt = typeAt + (text.length - 1) * EVERY;
    Q.push({ text, layout, clearAt, typeAt, doneAt, resultAt: doneAt + 0.14, prev });
    return Q[Q.length - 1];
  };

  // --- Sums, apps and people ------------------------------------------------------------------------------
  T.chapter('Sums, apps and people');
  const s2 = T.step('Work out a sum', 'Type a sum and the answer appears as you type. Tap Copy result to copy it.', 7.6);
  const q1 = query('12*8+4', 'symbols', s2);
  T.cam({ at: q1.typeAt - 0.1, cx: 196, cy: 190, s: 1.95, dur: 0.6 });
  const copyAt = q1.resultAt + 1.3;
  const answerState = searchCard({ query: q1.text, answer: '100' });
  content.replaceChildren(answerState);
  const copyEl = [...answerState.querySelectorAll('span')].find((x) => x.textContent === 'Copy result');
  const copyR = T.rect(copyEl);
  content.replaceChildren(searchCard({}));
  T.hand({ at: copyAt - 0.6, enter: [copyR.cx + 20, copyR.cy + 60] }, { at: copyAt, tap: [copyR.cx, copyR.cy] }, { at: copyAt + 0.6, leave: true });
  T.ring(q1.resultAt + 0.1, 1.2, copyR, { pad: 6, radius: 12 });

  const s3 = T.step('Find an app', 'Type part of an app’s name and it shows up. One tap opens it.', 6.2);
  const q2 = query('ca', 'letters', s3);
  const s4 = T.step('Find a contact', 'Your quick-dial people show up too, ready to call or message.', 6.0);
  const q3 = query('al', 'letters', s4);

  // --- Numbers and the web --------------------------------------------------------------------------------
  T.chapter('Numbers and the web');
  const s5 = T.step('Call a number', 'Type a phone number and one tap opens the dialer with it.', 6.4);
  const q4 = query('5550132', 'symbols', s5);
  const s6 = T.step('Search the web', 'Anything else goes to the web. Pick a provider, or press Search on the keyboard for your default.', 7.6);
  const q5 = query('weather tomorrow', 'letters', s6);
  const webState = searchCard({ query: q5.text });
  content.replaceChildren(webState);
  const chipsRow = webState.lastElementChild;
  const chipsR = T.rect(chipsRow);
  content.replaceChildren(searchCard({}));
  T.ring(q5.resultAt + 0.3, 2.4, chipsR, { pad: 6, radius: 14 });

  const s7 = T.step('Close it', 'Tap anywhere outside, or press Back. The Deck slides away and the tab comes back.', 5.4);
  const closeAt = s7.at + 1.1;
  T.cam({ at: s7.at + 0.5, cx: 206, cy: 457, s: 1.0, dur: 0.7 });
  T.hand({ at: s7.at + 0.45, enter: [140, 560] }, { at: closeAt, tap: [140, 560], sound: 'close' }, { at: closeAt + 0.6, leave: true });
  const deckClose = closeAt + 0.18;

  // --- Sound -------------------------------------------------------------------------------------------
  T.cue(deckOpen, 'open', 0.5);
  for (const q of Q) {
    for (let i = 0; i < q.prev.length; i++) T.cue(q.clearAt + i * ERASE, 'tap', 0.18);
    for (let i = 0; i < q.text.length; i++) T.cue(q.typeAt + i * EVERY, 'tap', 0.3);
  }
  T.cue(q1.resultAt, 'success', 0.45);

  // --- Every frame --------------------------------------------------------------------------------------
  const stateAt = (t) => {
    let text = '', key = null, layout = 'letters', result = null;
    for (const q of Q) {
      if (t < q.clearAt) break;
      layout = q.layout;
      if (t < q.typeAt) {
        const n = q.prev.length - Math.floor((t - q.clearAt) / ERASE) - 1;
        text = q.prev.slice(0, Math.max(0, n));
        key = '⌫';
        result = null;
      } else {
        const n = Math.min(q.text.length, Math.floor((t - q.typeAt) / EVERY) + 1);
        text = q.text.slice(0, n);
        key = t - (q.typeAt + (n - 1) * EVERY) < 0.11 ? keyFor(q.text[n - 1]) : null;
        result = t >= q.resultAt ? q : null;
      }
    }
    return { text, key, layout, result };
  };
  let contentKey = '';
  const kbCache = {};
  T.on((t) => {
    const deckUp = t >= deckOpen && t < deckClose + 0.21;
    const open = t < deckOpen ? 0 : t < deckClose ? prog(t, deckOpen, 0.21) : 1 - prog(t, deckClose, 0.21);
    const barA = t < deckClose + 0.21 ? 1 - prog(t, deckOpen - 0.1, 0.16) : prog(t, deckClose + 0.21, 0.16);
    dock.draw({ alpha: barA });
    const s = stateAt(t);
    const caret = Math.floor(t * 2) % 2 === 0;
    const r = s.result;
    const k = [s.text, caret, r ? r.text : ''].join('|');
    if (k !== contentKey) {
      contentKey = k;
      const extra = !r ? {} : r === q1 ? { answer: '100' } : r === q2 ? { apps: [{ key: 'calendar', label: 'Calendar' }, { key: 'camera', label: 'Camera' }] }
        : r === q3 ? { people: [{ name: 'Alex Kim', sub: 'SMS · +1 555 0132' }] } : r === q4 ? { number: '5550132', people: [{ name: 'Alex Kim', sub: 'SMS · +1 555 0132' }] } : {};
      content.replaceChildren(searchCard({ query: s.text, caret, ...extra }));
    }
    deck.setActive('search', t >= searchOpen);
    deck.set({ open, card: t < searchOpen ? 0 : prog(t, searchOpen, 0.22), alpha: deckUp ? 1 : 0 });
    // The keyboard rises with the search and goes with the Deck.
    const up = t < searchOpen ? 0 : t < deckClose ? Ease.fastOutSlowIn(prog(t, searchOpen + 0.05, 0.25)) : 1 - Ease.fastOutSlowIn(prog(t, deckClose, 0.22));
    kbHost.style.display = up > 0 ? 'block' : 'none';
    kbHost.style.transform = `translateY(${(1 - up) * 330}px)`;
    const kk = s.layout + '|' + s.key;
    if (up > 0 && kbHost.dataset.k !== kk) {
      kbHost.dataset.k = kk;
      kbCache[kk] = kbCache[kk] || keyboard({ layout: s.layout, pressed: s.key });
      kbHost.replaceChildren(kbCache[kk]);
    }
    toast.style.display = t >= copyAt + 0.18 && t < copyAt + 1.08 ? 'flex' : 'none';
  });
}

function keyFor(ch) {
  if (ch === ' ') return ' ';
  return ch;
}

export const THUMB = { t: 13.0, title: 'Search in the Deck', cam: { cx: 200, cy: 200, s: 2.2, deg: -4, fx: 1470 } };
