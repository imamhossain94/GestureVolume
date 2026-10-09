// The app's screens, composed from m3.js to the specs read from the Compose code.
import { h, icon, drawableIcon, clamp } from './core.js';
import * as M from './m3.js';
import { tabPoints, outline, PRESETS, SHADER_FILLS, SURGE_FILLS, argb, miniPanel } from './quickpanel.js';
import { ShaderCanvas } from './agsl.js';
import { appIcon, APPS } from './phone.js';

const C = M.LIGHT;
export const SCREEN_ICONS = [
  ...M.M3_ICONS, 'filled/check_circle', 'filled/radio_button_unchecked', 'filled/music_note', 'filled/call',
  'filled/keyboard_double_arrow_up', 'filled/visibility_off', 'filled/push_pin', 'filled/screen_lock_portrait', 'filled/accessibility',
  'drawable/ic_vol_increase', 'drawable/ic_vol_decrease', 'drawable/ic_move', 'drawable/ic_nothing', 'drawable/ic_brightness_up',
];

// The bar as SVG at its true size (dp), for the preview stages: tab or rounded, fill and stroke.
export function barSvg(preset, { side = 'right' } = {}) {
  const P = preset;
  const w = P.w, hh = P.h;
  if (P.shape === 'tab') {
    const pts = tabPoints(w, hh, P.flare, side === 'left');
    const d = 'M' + pts.map((p) => p[0].toFixed(2) + ' ' + p[1].toFixed(2)).join(' L') + ' Z';
    return `<svg width="${w}" height="${hh}" style="display:block;overflow:visible"><path d="${d}" fill="${cssArgb(P.color)}"/></svg>`;
  }
  const s = P.stroke?.w ?? 0;
  return `<svg width="${w}" height="${hh}" style="display:block;overflow:visible"><rect x="${s / 2}" y="${s / 2}" width="${w - s}" height="${hh - s}" rx="${P.radii[0]}" ry="${P.radii[0]}"
      fill="${cssArgb(P.color)}" ${s ? `stroke="${cssArgb(P.stroke.color)}" stroke-width="${s}"` : ''}/></svg>`;
}
const cssArgb = (hex) => { const [a, r, g, b] = argb(hex); return `rgba(${r},${g},${b},${(a / 255).toFixed(3)})`; };

// --- Appearance -----------------------------------------------------------------------------------
export function appearanceScreen({ preset = 'dock', status = 32, open = 'size', scroll = 0 } = {}) {
  const P = PRESETS[preset];
  const W = 412, Ws = W - 32, Hs = clamp(0.27 * 915, 212, 320);
  const stage = M.previewStage(Ws, Hs, (g) => {
    const boxH = g.glass.h / g.scale;
    const bh = Math.min(P.h, 260);
    return `<div style="position:absolute;right:${P.edgeMargin}px;top:${boxH / 2 - bh / 2}px">${barSvg({ ...P, h: bh })}</div>`;
  });
  const chips = Object.entries(PRESETS).map(([id, pr]) => M.presetChip(pr.name, cssArgb(pr.color), id === preset)).join('');
  const sizeBody = `${M.sliderControl('Width', `${P.w}dp`, (P.w - 1) / 199)}${M.divider()}${M.sliderControl('Height', `${P.h}dp`, (P.h - 8) / 192)}${M.divider()}
      ${M.segmented(['Rounded', 'Tab'], P.shape === 'tab' ? 1 : 0)}
      ${M.bodySmall(P.shape === 'tab' ? 'Both ends sweep back into the side of the screen. Keep the bar flush to the edge, or the curves have nothing to meet.' : 'A rectangle with corners you set below.', C.onSurfaceVariant, 'padding-top:8px')}
      ${P.shape === 'tab' ? `${M.divider()}${M.sliderControl('End sweep', `${Math.round(P.flare * 100)}%`, (P.flare * 100 - 4) / 46)}
        ${M.bodySmall("How much of the bar's height each curve takes. At the top of the range the two meet and the bar becomes a leaf.", C.onSurfaceVariant, 'padding-top:8px')}` : ''}`;
  const contentBody = `${M.switchRow('Show icon', P.showIcon)}${M.divider()}${M.switchRow('Show volume percentage', true)}
      ${M.bodySmall('Display the level on the handler while you swipe.')}`;
  const sizeSummary = `${P.w} × ${P.h}dp · ${P.shape === 'tab' ? 'Tab' : 'Rounded'}`;
  const pct = (hex) => Math.floor((argb(hex)[0] / 255) * 100);
  const sections = [
    M.section({ glyph: 'filled/widgets', title: 'Content', summary: P.showIcon ? 'Show icon' : 'None', open: open === 'content', body: contentBody }),
    M.section({ glyph: 'filled/aspect_ratio', title: 'Size & shape', summary: sizeSummary, open: open === 'size', body: sizeBody }),
    M.section({ glyph: 'filled/palette', title: 'Colours', summary: `${pct(P.color)}% · ${P.stroke ? pct(P.stroke.color) : 78}%` }),
    M.section({ glyph: 'filled/tune', title: 'Behaviour' }),
    M.section({ glyph: 'filled/open_with', title: 'Position', summary: preset === 'classic' ? 'Right' : 'Dynamic position' }),
  ];
  return M.screen(`${M.topAppBar('Appearance', { status, actions: [{ icon: 'outlined/help_outline' }, { icon: 'filled/save', color: 'rgba(31,41,55,.3)' }] })}
      <div class="pinned" style="padding:4px 16px 12px">${stage.outerHTML}
        ${M.bodyMedium('Your handler as it looks on your screen. Tap ? above to watch each gesture play out with your own settings. Long press the live bar to move it.', C.onSurfaceVariant, 'padding:10px 4px 0')}</div>
      <div class="scroll" style="position:relative;overflow:hidden;height:600px"><div style="padding:16px;transform:translateY(${-scroll}px)">
        ${M.groupLabel('Quick presets')}<div style="display:flex;gap:8px">${chips}</div><div style="height:16px"></div>
        ${sections.join('<div style="height:12px"></div>')}</div></div>`);
}

// --- Quick slider -----------------------------------------------------------------------------------
// The settings screen with its pinned preview (the real panel drawn on the stage's phone) and the
// Animation section open on the Shaders block, every effect tile running its own shader.
export function quickSliderScreen({ status = 32, effect = 'aurora', scroll = 0 } = {}) {
  const W = 412, Ws = W - 32, Hs = clamp(0.27 * 915, 212, 320);
  const stage = M.previewStage(Ws, Hs, (g) => {
    const boxH = g.glass.h / g.scale;
    const ph = Math.min(220, boxH - 88);
    return `<div class="qs-host" data-h="${ph}" style="position:absolute;right:0;top:${44 + (boxH - 88 - ph) / 2}px;width:32px;height:${ph}px"></div>`;
  });
  const effects = Object.keys(SHADER_FILLS);
  const labels = { 'lava-lamp': 'Lava lamp', 'liquid-chrome': 'Liquid chrome', molten: 'Molten', aurora: 'Aurora', 'ink-smoke': 'Ink smoke', plasma: 'Plasma', caustics: 'Caustics', 'soap-film': 'Soap film', mesh: 'Mesh', clouds: 'Clouds', smoke: 'Smoke', starfield: 'Starfield' };
  const start = Math.max(0, effects.indexOf(effect) - 1);
  const tiles = effects.slice(start, start + 6).map((e) => M.pictureTile(labels[e], `${M.tileWallpaper()}<div class="fill-tile" data-effect="${e}" style="position:absolute;left:22px;top:5px;width:24px;height:70px"></div>`, { selected: e === effect })).join('<div style="width:10px;flex:none"></div>');
  const fillNames = ['Solid', 'Pixels', 'Shaders', 'Surge', 'Effort', 'Glimmer'];
  const body = `${M.label15('Fill animation')}${M.bodySmall('What the filled part of the track does while the panel is open. The preview above runs it.', C.onSurfaceVariant, 'padding:4px 0 12px')}
      <div style="display:flex;overflow:hidden">${fillNames.slice(1).map((n, i) => M.pictureTile(n, `${M.tileWallpaper()}<div class="fill-tile2" data-kind="${n}" style="position:absolute;left:22px;top:5px;width:24px;height:70px"></div>`, { selected: n === 'Shaders' })).join('<div style="width:10px;flex:none"></div>')}</div>
      ${M.divider()}${M.bodySmall('Effects drawn live by your phone\'s graphics chip, lit up to the level and faint above it. Pick one and tune it; the preview above shows it as you go.')}
      <div style="height:12px"></div>${M.label15('Effect')}<div style="display:flex;overflow:hidden">${tiles}</div>
      <div style="height:4px"></div>${M.sliderControl('Speed', '1.0×', 1 / 3)}<div style="height:12px"></div>${M.sliderControl('Scale', '1.0×', 0.25)}`;
  return M.screen(`${M.topAppBar('Quick slider', { status, actions: [{ icon: 'outlined/help_outline' }] })}
      <div class="pinned" style="padding:4px 16px 12px">${stage.outerHTML}
        ${M.bodyMedium('Swipe the bar up or down and the panel opens beside it. It stays there — touch the track to set the value, and it closes itself.', C.onSurfaceVariant, 'padding:10px 4px 0')}</div>
      <div class="scroll" style="position:relative;overflow:hidden;height:600px"><div style="padding:16px;transform:translateY(${-scroll}px)">
        ${M.section({ glyph: 'filled/animation', title: 'Animation', summary: 'Slide · Shaders', open: true, body })}
        <div style="height:12px"></div>${M.section({ glyph: 'filled/tune', title: 'Behaviour', summary: 'Off · Instant' })}</div></div>`);
}

// --- Visibility -----------------------------------------------------------------------------------
export const VIS_APPS = [
  { key: 'calendar', pkg: 'com.android.calendar' }, { key: 'camera', pkg: 'com.android.camera' },
  { key: 'clock', pkg: 'com.android.deskclock' }, { key: 'games', pkg: 'app.playland.games', hidden: true },
  { key: 'mail', pkg: 'app.postbox.mail' }, { key: 'maps', pkg: 'app.wayfinder.maps' },
  { key: 'music', pkg: 'app.tunes.music' }, { key: 'reader', pkg: 'app.pageturn.reader', hidden: true },
  { key: 'video', pkg: 'app.streambox.video', hidden: true }, { key: 'weather', pkg: 'app.skyline.weather' },
];

export function visibilityScreen({ status = 32, tab = 'all', scroll = 0 } = {}) {
  const tick = (on) => icon(on ? 'filled/check_circle' : 'filled/radio_button_unchecked', 24, on ? C.primary : C.outline);
  const list = tab === 'all' ? VIS_APPS : VIS_APPS.filter((a) => a.hidden);
  const rows = list.map((a, i) => `<div style="border-radius:${M.segRadius(i, list.length)};background:${a.hidden ? C.primaryContainer : C.row};display:flex;align-items:center;padding:10px 14px">
      <div style="width:40px;height:40px;border-radius:10px;overflow:hidden">${appIcon(a.key, 40)}</div><div style="width:14px"></div>
      <div style="flex:1;min-width:0">${M.text(APPS[a.key].label, { size: 15, weight: a.hidden ? 600 : 500, color: a.hidden ? C.onPrimaryContainer : C.onSurface })}
        ${M.text(a.pkg, { size: 11, color: a.hidden ? 'rgba(49,46,129,.6)' : 'rgba(31,41,55,.6)', extra: 'margin-top:1px' })}</div>
      <div style="width:10px"></div>${tick(a.hidden)}</div>`).join('<div style="height:3px"></div>');
  const n = VIS_APPS.filter((a) => a.hidden).length;
  const heading = (t, hint) => `<div style="padding:0 6px 12px">${M.text(t, { size: 14, weight: 600, color: C.primary })}${M.bodySmall(hint, C.onSurfaceVariant, 'padding-top:4px')}</div>`;
  const toggleRow = (glyph, title, desc, on, radius) => `<div style="border-radius:${radius};background:${C.row};display:flex;align-items:center;padding:12px 14px">
      <div style="width:46px;height:46px;border-radius:14px;background:${on ? C.primary : 'rgba(224,231,255,.6)'};display:grid;place-items:center">${icon(glyph, 24, on ? '#fff' : C.primary)}</div>
      <div style="width:14px"></div><div style="flex:1">${M.text(title, { size: 16, weight: 600 })}${M.bodySmall(desc, C.onSurfaceVariant, 'padding-top:2px')}</div>
      <div style="width:10px"></div>${M.m3switch(on)}</div>`;
  return M.screen(`${M.topAppBar('Visibility', { status })}
      <div class="scroll" style="position:relative;overflow:hidden;height:${915 - status - 64}px"><div style="transform:translateY(${-scroll}px)">
        <div style="padding:8px 16px 24px">
          ${heading('When it shows', 'With either switch on, the handler is there only while that is happening. With both, while either is. With neither, it is always there.')}
          ${toggleRow('filled/music_note', 'Only while media plays', 'Steps aside when nothing is playing, and comes back as soon as something starts. It stays a few seconds after the sound stops, so it does not blink out between tracks.', false, '20px 20px 6px 6px')}
          <div style="height:3px"></div>
          ${toggleRow('filled/call', 'Only during calls', 'Shows while the phone rings and while a call is on, calls in apps included.', false, '6px 6px 20px 20px')}
        </div>
        <div style="padding:0 16px">${heading('Hide in these apps', 'The handler steps aside while one of these apps is open, and comes back when you leave it.')}</div>
        <div style="padding:4px 16px 8px">
          <div style="height:56px;border-radius:16px;border:1px solid ${C.outline};display:flex;align-items:center;padding:0 12px;gap:12px">${icon('filled/search', 24, C.onSurfaceVariant)}${M.text('Search apps…', { size: 16, color: C.onSurfaceVariant })}</div>
          <div style="margin-top:8px;display:flex;gap:3px;padding:3px;border-radius:12px;background:rgba(31,41,55,.06)">
            ${[`Hidden in (${n})`, 'All apps'].map((l, i) => `<div style="flex:1;border-radius:8px;padding:9px 0;text-align:center;font:600 13px/1.172 Roboto;letter-spacing:.5px;
              background:${(i === 1) === (tab === 'all') ? C.primary : 'transparent'};color:${(i === 1) === (tab === 'all') ? '#fff' : C.onSurfaceVariant}">${l}</div>`).join('')}</div>
        </div>
        <div style="padding:0 16px">${rows}</div>
      </div></div>`);
}

// Starts every live panel on a mounted Quick slider screen: the preview's real panel and each
// tile's MiniFill. Returns draw({ value, t }) for the frame.
export function mountQuickSlider(el, { effect = 'aurora', value = 0.7 } = {}) {
  const draws = [];
  const host = el.querySelector('.qs-host');
  if (host) {
    const d = miniPanel(host, { w: 32, h: +host.dataset.h, shape: 'tab', flare: 0.22, fill: { kind: 'shader', effect }, showValue: true, showIcon: true, valueMargin: 35, iconMargin: 35 });
    draws.push((st) => d({ value: st.value ?? value, t: st.t, fillTime: st.t + 7 }));
  }
  for (const tile of el.querySelectorAll('.fill-tile')) {
    const d = miniPanel(tile, { fill: { kind: 'shader', effect: tile.dataset.effect } });
    draws.push((st) => d({ value: 0.62, t: st.t, fillTime: st.t + 7 }));
  }
  const kinds = { Pixels: { kind: 'pixels' }, Shaders: { kind: 'shader', effect }, Surge: { kind: 'surge', look: 'curve' }, Effort: { kind: 'effort' }, Glimmer: { kind: 'glimmer' } };
  for (const tile of el.querySelectorAll('.fill-tile2')) {
    const d = miniPanel(tile, { fill: kinds[tile.dataset.kind] });
    draws.push((st) => d({ value: 0.62, t: st.t, fillTime: st.t + 7 }));
  }
  return (st) => draws.forEach((d) => d(st));
}

// --- Home -----------------------------------------------------------------------------------------
// MainScreen: the bar group (service, appearance, actions, permissions), Advanced features and the
// Quick presets, ad-free (Pro or the -PnoAds build), all permissions granted.
export const HOME_ICONS = ['filled/menu', 'outlined/new_releases', 'drawable/ic_crown_2', 'filled/power_settings_new', 'drawable/ic_color_palette',
  'drawable/ic_app_open', 'filled/shield', 'filled/check_circle', 'outlined/auto_awesome', 'drawable/ic_layer', 'drawable/ic_brightness_up',
  'drawable/ic_move', 'drawable/ic_visibility_hide', 'filled/expand_more', 'filled/keyboard_arrow_right', 'filled/error', 'filled/privacy_tip'];

// data-targets: service, appearance, actions, permissions, advanced (the Advanced mode row), feature-deck,
// feature-quick-slider, feature-long-press-menu, feature-visibility, show-advanced, preset-<id>.
export function homeAppScreen({ status = 32, running = true, advanced = false, expanded = false, scroll = 0, k = null, advK = null } = {}) {
  const tile = (glyph, on = false) => `<div style="width:46px;height:46px;border-radius:14px;flex:none;background:${on ? C.primary : 'rgba(224,231,255,.6)'};display:grid;place-items:center">
      ${glyph.startsWith('drawable/') ? drawableIcon(glyph, 24, on ? '#fff' : C.primary) : icon(glyph, 24, on ? '#fff' : C.primary)}</div>`;
  const chevron = icon('filled/keyboard_arrow_right', 22, C.outline);
  const row = (radius, inner, bg = C.row, extra = '') => `<div style="border-radius:${radius};background:${bg};padding:12px 14px;${extra}">${inner}</div>`;
  const linkRow = (radius, glyph, title, summary, footer = '', summaryColor = C.onSurfaceVariant) => row(radius, `
      <div style="display:flex;align-items:center">${tile(glyph)}<div style="width:14px"></div>
        <div style="flex:1;min-width:0">${M.text(title, { size: 16, weight: 600 })}${M.bodySmall(summary, summaryColor, 'padding-top:2px')}</div>
        <div style="width:6px"></div>${chevron}</div>${footer}`);
  const toggleRow = (radius, glyph, title, summary, on, lit, sw = null) => row(radius, `
      <div style="display:flex;align-items:center">${tile(glyph, on)}<div style="width:14px"></div>
        <div style="flex:1;min-width:0">${M.text(title, { size: 16, weight: 600 })}${M.bodySmall(summary, C.onSurfaceVariant, 'padding-top:2px')}</div>
        <div style="width:10px"></div>${sw ?? M.m3switch(on)}</div>`, lit && on ? C.primaryContainer : C.row);
  const heading = (t, hint) => `<div style="padding:0 6px 12px">${M.text(t, { size: 14, weight: 600, color: C.primary })}${M.bodySmall(hint, C.onSurfaceVariant, 'padding-top:4px')}</div>`;
  const chip = (label) => `<span style="display:inline-flex;align-items:center;gap:4px;height:22px;padding:0 9px 0 6px;border-radius:11px;background:rgba(79,70,229,.10);
      font:500 11px/1 Roboto;letter-spacing:.5px;color:${C.primary}">${icon('filled/check_circle', 14, C.primary)}${label}</span>`;
  const permFooter = `<div style="padding:10px 0 0 60px">
      <div style="font:400 12px/16px Roboto;letter-spacing:.5px;color:rgba(75,85,99,.7)">What the bar is allowed to do: draw over other<br>apps, perform system actions and change the br…</div>
      <div style="display:flex;flex-wrap:wrap;gap:6px;margin-top:8px">${chip('Draw over apps')}${chip('Accessibility')}${chip('Modify settings')}</div></div>`;
  const permRow = `<div data-target="permissions" style="border-radius:6px 6px 20px 20px;background:${C.row};padding:12px 14px">
      <div style="display:flex;align-items:center"><div style="width:46px;height:46px;border-radius:14px;background:rgba(79,70,229,.12);display:grid;place-items:center">${icon('filled/shield', 24, C.primary)}</div>
        <div style="width:14px"></div><div style="flex:1">${M.text('Permissions', { size: 16, weight: 600 })}${M.bodySmall('All permissions granted', C.primary, 'padding-top:2px')}</div>
        <div style="width:6px"></div>${chevron}</div>${permFooter}</div>`;
  const serviceSummary = running ? 'The bar is on your screen. Swipe it for volume, tap it for your actions, and tap here to turn it off.'
    : 'Tap to turn it on. Then make it look and act the way you like with Appearance and Actions below.';
  const adv = advanced ? 'The Dock bar: swipe for the Quick slider, swipe inward for the Deck, hold for the menu'
    : 'The round button: swipe for volume, tap for the volume panel. Turn on for the Dock and its panels.';
  const showRows = advanced || expanded;
  const featureRows = [
    ['drawable/ic_layer', 'Deck', 'A side panel of shortcuts, tools & search'],
    ['drawable/ic_brightness_up', 'Quick slider', 'The brightness & volume track the bar opens into'],
    ['drawable/ic_move', 'Long-press menu', 'The actions offered when you hold the bar'],
    ['drawable/ic_visibility_hide', 'Visibility', 'When the handler shows: media, calls, the keyboard and apps'],
  ];
  const advCount = 1 + (showRows ? 4 : 0) + (advanced ? 0 : 1);
  const advRows = [`<div data-target="advanced">${toggleRow(M.segRadius(0, advCount), 'outlined/auto_awesome', 'Advanced mode', adv, advK == null ? advanced : advK >= 0.5, false, advK == null ? null : M.m3switchAt(advK))}</div>`];
  if (showRows) featureRows.forEach(([g, t, s], i) => advRows.push(`<div class="feature" data-name="${t}" data-target="feature-${t.toLowerCase().replace(/ /g, '-')}">${linkRow(M.segRadius(i + 1, advCount), g, t, s)}</div>`));
  if (!advanced) advRows.push(`<div data-target="show-advanced" style="border-radius:${M.segRadius(advCount - 1, advCount)};background:${C.row};padding:14px;display:flex;align-items:center">
      ${M.text(expanded ? 'Hide advanced features' : 'Show advanced features', { size: 15, weight: 500, color: C.primary, extra: 'flex:1;padding-left:4px' })}
      <div style="transform:rotate(${expanded ? 180 : 0}deg)">${icon('filled/expand_more', 24, C.primary)}</div></div>`);
  const mini = (id) => {
    const geo = { classic: `<div style="width:13.2px;height:30.36px;border-radius:6.6px;background:rgba(79,70,229,.55)"></div>`,
      dock: barSvg({ ...PRESETS.dock, w: 8.4, h: 30.36, color: '#FF4F46E5' }),
      edge: `<div style="width:7.2px;height:30.36px;border-radius:3.6px 2.4px 2.4px 3.6px;background:${C.primary}"></div>`,
      bold: `<div style="width:20.4px;height:20.4px;border-radius:50%;background:rgba(79,70,229,.85);margin-right:5px"></div>` }[id];
    return `<div style="width:46px;height:46px;border-radius:14px;background:rgba(224,231,255,.6);display:flex;align-items:center;justify-content:flex-end;overflow:hidden;flex:none">${geo}</div>`;
  };
  const presetSummaries = { classic: 'The original round button: swipe for volume, tap for the panel', dock: 'A curved tab on the edge that opens into panels',
    edge: 'A slim pill, flush to the side and out of the way', bold: 'A round bubble floating off the edge, easy to find' };
  const presets = Object.keys(presetSummaries).map((id, i) => `<div data-target="preset-${id}">` + row(M.segRadius(i, 4), `<div style="display:flex;align-items:center">${mini(id)}<div style="width:14px"></div>
      <div style="flex:1">${M.text(PRESETS[id].name, { size: 16, weight: 600 })}${M.bodySmall(presetSummaries[id], C.onSurfaceVariant, 'padding-top:2px')}</div>${chevron}</div>`) + '</div>');
  return M.screen(`
      <div style="height:${status + 64}px;padding-top:${status}px;display:flex;align-items:center">
        <div style="width:56px;padding-left:16px">${icon('filled/menu', 24, C.onSurface)}</div>
        <div style="flex:1;font:600 22px/28px Roboto;color:${C.onSurface}">GestureVolume</div>
        <div style="width:48px;display:grid;place-items:center">${icon('outlined/new_releases', 24, C.onSurfaceVariant)}</div>
        <div style="width:48px;display:grid;place-items:center">${drawableIcon('ic_crown_2', 24, C.primary)}</div><div style="width:4px"></div></div>
      <div class="scroll" style="position:relative;overflow:hidden;height:${915 - status - 64}px"><div class="col" style="padding:0 16px;transform:translateY(${-scroll}px)">
        <div style="display:flex;flex-direction:column;gap:3px">
          <div class="service" data-target="service">${toggleRow('20px 20px 6px 6px', 'filled/power_settings_new', running ? 'Service active' : 'Service inactive', serviceSummary, running, true, k == null ? null : M.m3switchAt(k))}</div>
          <div class="appearance" data-target="appearance">${linkRow('6px', 'drawable/ic_color_palette', 'Appearance', 'Colour, shape, size and icon, with a live preview')}</div>
          <div class="actions" data-target="actions">${linkRow('6px', 'drawable/ic_app_open', 'Actions', 'What each tap, hold and swipe does, and a pad to try them')}</div>
          ${permRow}
        </div>
        <div style="height:24px"></div>${heading('Advanced features', 'Deck, Quick slider, long-press menu and visibility')}
        <div style="display:flex;flex-direction:column;gap:3px">${advRows.join('')}</div>
        <div style="height:24px"></div>${heading('Quick presets', 'Start from a ready-made bar, then tune it under Appearance.')}
        <div style="display:flex;flex-direction:column;gap:3px">${presets.join('')}</div><div style="height:16px"></div>
      </div></div>`);
}
