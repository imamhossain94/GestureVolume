// What Android and other apps put on screen around Gesture Volume, drawn plainly and without
// anyone's branding: a keyboard, a video playing, the system volume panel, a lock screen.
import { h, icon } from './core.js';

export const SYSTEM_ICONS = ['round/backspace', 'round/keyboard_return', 'round/emoji_emotions', 'round/settings', 'round/volume_up',
  'round/notifications', 'round/vibration', 'round/more_horiz', 'round/play_arrow', 'round/pause', 'round/skip_next', 'round/skip_previous',
  'round/fullscreen_exit', 'round/lock', 'round/fingerprint', 'round/photo_camera', 'round/call', 'round/arrow_upward', 'round/keyboard_capslock'];

// A keyboard of the plain Android kind, 412 dp wide. layout: 'letters' | 'symbols'.
export function keyboard({ layout = 'letters', dark = true, pressed = null, enter = 'search' } = {}) {
  const bg = dark ? '#1B1C1F' : '#E8EAED', key = dark ? '#3C3D41' : '#FFFFFF', mod = dark ? '#2A2B2F' : '#C8CBD1';
  const ink = dark ? '#E8E8EA' : '#1F1F1F', accent = dark ? '#A8C7FA' : '#0B57D0';
  const rows = layout === 'symbols'
    ? [['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'], ['@', '#', '$', '_', '&', '-', '+', '(', ')', '/'], ['=\\<', '*', '"', "'", ':', ';', '!', '?', '⌫'], ['ABC', ',', ' ', '.', '⏎']]
    : [['q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'], ['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'], ['⇧', 'z', 'x', 'c', 'v', 'b', 'n', 'm', '⌫'], ['?123', ',', ' ', '.', '⏎']];
  const kw = 36.2, gap = 5, H = 46;
  const keyEl = (k) => {
    let w = kw, bgc = key, content = k, color = ink, size = 20;
    if (k === '⌫') { w = kw * 1.5 + gap / 2; bgc = mod; content = icon('round/backspace', 22, ink); }
    else if (k === '⇧') { w = kw * 1.5 + gap / 2; bgc = mod; content = icon('round/keyboard_capslock', 22, ink); }
    else if (k === '=\\<') { w = kw * 1.5 + gap / 2; bgc = mod; content = '=\\&lt;'; size = 15; }
    else if (k === '?123' || k === 'ABC') { w = kw * 1.5 + gap / 2; bgc = mod; size = 14; }
    else if (k === ' ') { w = kw * 5 + gap * 4; content = ''; }
    else if (k === '⏎') { w = kw * 1.5 + gap / 2; bgc = accent; content = icon(enter === 'search' ? 'round/search' : 'round/keyboard_return', 22, dark ? '#062E6F' : '#fff'); }
    const isPressed = pressed && pressed === k;
    return `<div style="width:${w}px;height:${H}px;border-radius:8px;background:${isPressed ? (dark ? '#5A5C61' : '#D3E3FD') : bgc};display:grid;place-items:center;
        font:400 ${size}px/1 Roboto;color:${color};flex:none">${content}</div>`;
  };
  return h(`<div class="keyboard" style="position:absolute;left:0;right:0;bottom:0;background:${bg};padding:8px 4px 34px">
      <div style="height:40px;display:flex;align-items:center;justify-content:space-between;padding:0 12px;margin-bottom:4px">
        ${icon('round/emoji_emotions', 22, dark ? '#C4C7C5' : '#444746')}${icon('round/settings', 22, dark ? '#C4C7C5' : '#444746')}${icon('round/more_horiz', 22, dark ? '#C4C7C5' : '#444746')}</div>
      ${rows.map((r) => `<div style="display:flex;justify-content:center;gap:${gap}px;margin-bottom:10px">${r.map(keyEl).join('')}</div>`).join('')}
    </div>`);
}

// A frame of video: an illustrated valley at dusk, w x h dp, and a little player chrome.
export function videoFrame(w, hh, { progress = 0.38, chrome = true, t = 0 } = {}) {
  const sunY = hh * 0.42;
  return h(`<div style="position:absolute;inset:0;overflow:hidden">
      <svg width="${w}" height="${hh}" viewBox="0 0 ${w} ${hh}" style="position:absolute;inset:0">
        <defs>
          <linearGradient id="vsky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2B1B54"/><stop offset=".45" stop-color="#A2457A"/><stop offset=".75" stop-color="#F59E6B"/><stop offset="1" stop-color="#FCD9A8"/></linearGradient>
          <radialGradient id="vsun" cx="${w * 0.62}" cy="${sunY}" r="${hh * 0.5}" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#FFF4D6"/><stop offset=".12" stop-color="#FFE3A3"/><stop offset=".35" stop-color="#FDBA74" stop-opacity=".6"/><stop offset="1" stop-color="#FDBA74" stop-opacity="0"/></radialGradient>
          <linearGradient id="vlake" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F3A472"/><stop offset="1" stop-color="#3A1F4F"/></linearGradient>
        </defs>
        <rect width="${w}" height="${hh}" fill="url(#vsky)"/>
        <rect width="${w}" height="${hh}" fill="url(#vsun)"/>
        <circle cx="${w * 0.62}" cy="${sunY}" r="${hh * 0.07}" fill="#FFF6DD"/>
        <path d="M0 ${hh * 0.6} L${w * 0.12} ${hh * 0.44} L${w * 0.22} ${hh * 0.53} L${w * 0.34} ${hh * 0.38} L${w * 0.48} ${hh * 0.55} L${w * 0.58} ${hh * 0.5} L${w * 0.72} ${hh * 0.6} L${w} ${hh * 0.47} L${w} ${hh * 0.7} L0 ${hh * 0.7} Z" fill="#7B3F74" fill-opacity=".75"/>
        <path d="M0 ${hh * 0.66} L${w * 0.16} ${hh * 0.56} L${w * 0.3} ${hh * 0.63} L${w * 0.45} ${hh * 0.54} L${w * 0.62} ${hh * 0.65} L${w * 0.8} ${hh * 0.57} L${w} ${hh * 0.64} L${w} ${hh * 0.72} L0 ${hh * 0.72} Z" fill="#4C2A5E"/>
        <rect y="${hh * 0.7}" width="${w}" height="${hh * 0.3}" fill="url(#vlake)"/>
        <rect x="${w * 0.6}" y="${hh * 0.71}" width="${w * 0.04}" height="${hh * 0.2}" fill="#FFE3A3" fill-opacity=".35"/>
        <path d="M0 ${hh} L0 ${hh * 0.82} Q${w * 0.1} ${hh * 0.76} ${w * 0.22} ${hh * 0.84} T${w * 0.45} ${hh} Z" fill="#24132F"/>
      </svg>
      ${chrome ? `<div style="position:absolute;left:0;right:0;bottom:0;height:70px;background:linear-gradient(transparent,rgba(0,0,0,.55))"></div>
        <div style="position:absolute;left:24px;right:24px;bottom:22px;height:4px;border-radius:2px;background:rgba(255,255,255,.35)">
          <div style="width:${progress * 100}%;height:4px;border-radius:2px;background:#fff"></div>
          <div style="position:absolute;left:${progress * 100}%;top:-4px;width:12px;height:12px;margin-left:-6px;border-radius:50%;background:#fff"></div></div>
        <div style="position:absolute;left:24px;bottom:34px;font:500 12px Roboto;color:rgba(255,255,255,.9)">4:12 / 10:57</div>` : ''}
    </div>`);
}

// The app's indicator message (OverlayController.showIndicatorMessage): white 16 sp on a dark
// 18 dp pill, centred, no fade.
export function indicator(text, glyphHtml = '') {
  return h(`<div class="toast" style="position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);display:flex;align-items:center;gap:10px;
      padding:14px 20px;border-radius:18px;background:rgba(24,24,24,.863);font:400 16px/1.25 Roboto;color:#fff;white-space:nowrap">${glyphHtml}${text}</div>`);
}

// Android's own volume panel (the stream slider a tap on the bar opens), at the right edge.
export function systemVolumePanel(level = 0.4, { y = 180 } = {}) {
  return h(`<div class="sysvol" style="position:absolute;right:12px;top:${y}px;width:58px;border-radius:30px;background:#2B2C30;padding:12px 8px 10px;
      display:flex;flex-direction:column;align-items:center;gap:10px;box-shadow:0 6px 18px rgba(0,0,0,.3)">
      ${icon('round/vibration', 22, '#C9C9CD')}
      <div style="position:relative;width:42px;height:190px;border-radius:21px;background:#45464C;overflow:hidden">
        <div class="lvl" style="position:absolute;left:0;right:0;bottom:0;height:${Math.max(42, level * 190)}px;border-radius:21px;background:#C7BFFF"></div>
        <div style="position:absolute;left:0;right:0;bottom:10px;display:grid;place-items:center">${icon('round/volume_up', 22, '#2B2C30')}</div>
      </div>
      ${icon('round/more_horiz', 22, '#C9C9CD')}
    </div>`);
}
