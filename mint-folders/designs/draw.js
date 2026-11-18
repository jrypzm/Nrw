// yrs. folders: one hundred material folders, drawn as SVG and rendered in Chromium.
//   node draw.js <out-dir>         -> <out-dir>/001.png ... 100.png at 512 px
// Every design is a recipe: a material family, its colours and a seed.

const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

// ---------- small helpers ----------
const rng = (seed) => () => { seed |= 0; seed = (seed + 0x6D2B79F5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
const hex = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16) / 255);
// gradient map: grey -> list of colours, as feComponentTransfer tables
const gmap = (cols, type = 'table') => {
  const c = cols.map(hex);
  const t = (k) => c.map((x) => x[k].toFixed(3)).join(' ');
  return `<feComponentTransfer><feFuncR type="${type}" tableValues="${t(0)}"/><feFuncG type="${type}" tableValues="${t(1)}"/><feFuncB type="${type}" tableValues="${t(2)}"/><feFuncA type="linear" slope="0" intercept="1"/></feComponentTransfer>`;
};
const GREY = '<feColorMatrix type="matrix" values="0.33 0.33 0.33 0 0  0.33 0.33 0.33 0 0  0.33 0.33 0.33 0 0  0 0 0 0 1"/>';
// a stretched, contrasted noise in 0..1 grey
const noise = (fx, fy, oct, seed, type = 'fractalNoise', contrast = 1.6) =>
  `<feTurbulence type="${type}" baseFrequency="${fx} ${fy}" numOctaves="${oct}" seed="${seed}"/>${GREY}` +
  `<feComponentTransfer><feFuncR type="linear" slope="${contrast}" intercept="${(1 - contrast) / 2}"/><feFuncG type="linear" slope="${contrast}" intercept="${(1 - contrast) / 2}"/><feFuncB type="linear" slope="${contrast}" intercept="${(1 - contrast) / 2}"/></feComponentTransfer>`;
// bump lighting from a noise, returned as a grey shading layer to multiply
const bump = (fx, fy, oct, seed, scale, type = 'fractalNoise') =>
  `<feTurbulence type="${type}" baseFrequency="${fx} ${fy}" numOctaves="${oct}" seed="${seed}" result="n"/>` +
  `<feDiffuseLighting in="n" surfaceScale="${scale}" diffuseConstant="1.15" lighting-color="#fff"><feDistantLight azimuth="235" elevation="55"/></feDiffuseLighting>`;

// ---------- the folder ----------
const BACK = 'M 44 128 Q 44 98 74 98 L 178 98 Q 194 98 204 108 L 216 121 Q 224 130 238 130 L 438 130 Q 468 130 468 160 L 468 398 Q 468 426 440 426 L 72 426 Q 44 426 44 398 Z';
const FRONT = 'M 34 190 Q 34 160 64 160 L 448 160 Q 478 160 478 190 L 478 404 Q 478 434 448 434 L 64 434 Q 34 434 34 404 Z';
const FRONT_IN = (d) => `M ${34 + d} ${190} Q ${34 + d} ${160 + d} ${64} ${160 + d} L 448 ${160 + d} Q ${478 - d} ${160 + d} ${478 - d} 190 L ${478 - d} 404 Q ${478 - d} ${434 - d} 448 ${434 - d} L 64 ${434 - d} Q ${34 + d} ${434 - d} ${34 + d} 404 Z`;

// ---------- materials: each returns { defs, body } painting the full 512 square ----------
let uid = 0;
const F = (inner, extra = '') => { const id = 'f' + (uid++); return { id, def: `<filter id="${id}" x="0" y="0" width="512" height="512" filterUnits="userSpaceOnUse" color-interpolation-filters="sRGB" ${extra}>${inner}</filter>` }; };
const layer = (f, op = 1, blend = 'normal') => `<rect width="512" height="512" filter="url(#${f.id})" opacity="${op}" style="mix-blend-mode:${blend}"/>`;
const solid = (c) => `<rect width="512" height="512" fill="${c}"/>`;

const M = {
  denim({ c, s }) {
    const a = F(noise(0.9, 0.08, 2, s, 'fractalNoise', 2.2) + gmap(['#000000', '#ffffff']));
    const b = F(noise(0.02, 0.02, 3, s + 7, 'fractalNoise', 2) + gmap(['#000000', '#ffffff']));
    const twill = `<pattern id="tw${s}" width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(-38)"><rect width="7" height="3.2" fill="#fff" opacity=".16"/></pattern>`;
    return { defs: a.def + b.def + twill, body: solid(c) + layer(a, 0.38, 'overlay') + layer(b, 0.35, 'soft-light') + `<rect width="512" height="512" fill="url(#tw${s})"/>` };
  },
  leather({ c, s, croc }) {
    const sh = croc ? F(bump(0.045, 0.045, 1, s, 6, 'turbulence')) : F(bump(0.11, 0.11, 3, s, 1.6));
    const mot = F(noise(0.012, 0.012, 3, s + 3) + gmap(['#000000', '#ffffff']));
    return { defs: sh.def + mot.def, body: solid(c) + layer(sh, croc ? 0.75 : 0.55, 'multiply') + layer(mot, 0.25, 'soft-light') };
  },
  marble({ base, vein, cloud, s }) {
    // classic marble: turbulence ridges become thin veins over a soft cloud
    const cl = F(noise(0.005, 0.008, 5, s, 'fractalNoise', 1.6) + gmap(cloud));
    const v = F(`<feTurbulence type="turbulence" baseFrequency="0.0045 0.009" numOctaves="6" seed="${s + 11}"/>${GREY}` +
      `<feComponentTransfer><feFuncR type="table" tableValues="1 0.45 0.05 0 0 0 0 0"/><feFuncG type="table" tableValues="1 0.45 0.05 0 0 0 0 0"/><feFuncB type="table" tableValues="1 0.45 0.05 0 0 0 0 0"/></feComponentTransfer>` +
      `<feColorMatrix type="matrix" values="0 0 0 0 ${hex(vein)[0]}  0 0 0 0 ${hex(vein)[1]}  0 0 0 0 ${hex(vein)[2]}  0.9 0 0 0 0"/>`);
    const fine = F(`<feTurbulence type="turbulence" baseFrequency="0.012 0.02" numOctaves="4" seed="${s + 23}"/>${GREY}` +
      `<feComponentTransfer><feFuncR type="table" tableValues="0.9 0.1 0 0 0 0 0"/><feFuncG type="table" tableValues="0.9 0.1 0 0 0 0 0"/><feFuncB type="table" tableValues="0.9 0.1 0 0 0 0 0"/></feComponentTransfer>` +
      `<feColorMatrix type="matrix" values="0 0 0 0 ${hex(vein)[0]}  0 0 0 0 ${hex(vein)[1]}  0 0 0 0 ${hex(vein)[2]}  0.25 0 0 0 0"/>`);
    const sheen = `<linearGradient id="ms${s}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".18"/><stop offset=".4" stop-color="#fff" stop-opacity="0"/></linearGradient>`;
    return { defs: cl.def + v.def + fine.def + sheen, body: solid(base) + layer(cl, 1) + layer(fine, 1) + layer(v, 1) + `<rect width="512" height="512" fill="url(#ms${s})"/>` };
  },
  felt({ c, s }) {
    const g = F(noise(0.75, 0.75, 4, s, 'fractalNoise', 2.6) + gmap(['#000000', '#ffffff']));
    const m = F(noise(0.03, 0.03, 3, s + 5, 'fractalNoise', 1.8) + gmap(['#000000', '#ffffff']));
    return { defs: g.def + m.def, body: solid(c) + layer(g, 0.45, 'overlay') + layer(m, 0.3, 'soft-light') };
  },
  puffer({ c, s, dir = 'h', n = 4, gloss = 0.55 }) {
    // channels, each puffed: dark seams, a glossy ridge, crinkles
    const cr = F(bump(0.018, 0.05, 3, s, 5));
    let bands = '';
    const step = 280 / n;
    const gid = `pg${s}`;
    const grad = dir === 'h'
      ? `<linearGradient id="${gid}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#000" stop-opacity=".55"/><stop offset=".3" stop-color="#fff" stop-opacity="${gloss}"/><stop offset=".45" stop-color="#fff" stop-opacity="${gloss * 0.3}"/><stop offset=".85" stop-color="#000" stop-opacity=".25"/><stop offset="1" stop-color="#000" stop-opacity=".7"/></linearGradient>`
      : `<linearGradient id="${gid}" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#000" stop-opacity=".6"/><stop offset=".35" stop-color="#fff" stop-opacity="${gloss}"/><stop offset=".5" stop-color="#fff" stop-opacity="${gloss * 0.3}"/><stop offset="1" stop-color="#000" stop-opacity=".65"/></linearGradient>`;
    if (dir === 'h') for (let i = 0; i < n + 2; i++) bands += `<rect x="0" y="${154 + i * step}" width="512" height="${step}" fill="url(#${gid})"/>`;
    else for (let i = 0; i < 12; i++) bands += `<rect x="${34 + i * 74}" y="0" width="74" height="512" fill="url(#${gid})"/>`;
    return { defs: cr.def + grad, body: solid(c) + layer(cr, 0.5, 'multiply') + bands };
  },
  quilt({ c, s }) {
    // red geometric quilting, like a padded case
    const cr = F(bump(0.02, 0.02, 2, s, 3));
    const r = rng(s); let seams = '';
    for (let i = 0; i < 7; i++) { const x = 40 + r() * 430, y = 170 + r() * 250; seams += `<path d="M ${x - 80} ${y + 30} L ${x} ${y} L ${x + 90} ${y + 40}" stroke="#000" stroke-opacity=".45" stroke-width="5" fill="none"/><path d="M ${x - 80} ${y + 33} L ${x} ${y + 3} L ${x + 90} ${y + 43}" stroke="#fff" stroke-opacity=".25" stroke-width="2" fill="none"/>`; }
    const pr = `<linearGradient id="qg${s}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".28"/><stop offset=".5" stop-color="#fff" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".3"/></linearGradient>`;
    return { defs: cr.def + pr, body: solid(c) + layer(cr, 0.55, 'multiply') + seams + `<rect width="512" height="512" fill="url(#qg${s})"/>` };
  },
  wood({ cols, s, fx = 0.0035, fy = 0.05 }) {
    const g = F(`<feTurbulence type="fractalNoise" baseFrequency="${fx} ${fy}" numOctaves="4" seed="${s}"/>${GREY}` + gmap(cols.concat(cols.slice().reverse(), cols, cols.slice().reverse())));
    const grain = F(noise(0.004, 0.9, 2, s + 2, 'fractalNoise', 2) + gmap(['#000000', '#ffffff']));
    return { defs: g.def + grain.def, body: layer(g, 1) + layer(grain, 0.25, 'overlay') };
  },
  plastic({ c, s }) {
    // see-through tinted shell over a little circuit board
    const r = rng(s); let board = `<rect x="70" y="200" width="372" height="200" rx="10" fill="#0d3b2e" opacity=".55"/>`;
    for (let i = 0; i < 9; i++) board += `<rect x="${90 + r() * 300}" y="${215 + r() * 150}" width="${18 + r() * 50}" height="${12 + r() * 36}" rx="3" fill="#111" opacity=".55"/>`;
    for (let i = 0; i < 14; i++) { const y = 215 + r() * 170; board += `<path d="M ${80 + r() * 100} ${y} H ${200 + r() * 220}" stroke="#d9c27a" stroke-opacity=".35" stroke-width="2"/>`; }
    const gl = `<linearGradient id="pl${s}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".55"/><stop offset=".25" stop-color="#fff" stop-opacity=".08"/><stop offset="1" stop-color="#fff" stop-opacity=".18"/></linearGradient>`;
    return { defs: gl, body: `<rect width="512" height="512" fill="${c}" opacity=".6"/>` + `<g filter="url(#soft)">${board}</g>` + `<rect width="512" height="512" fill="${c}" opacity=".45"/><rect width="512" height="512" fill="url(#pl${s})"/>`, glass: true };
  },
  metal({ cols, s, brushed = true }) {
    const env = `<linearGradient id="mt${s}" x1="0" y1="0" x2="0.25" y2="1">${cols.map((c, i) => `<stop offset="${(i / (cols.length - 1)).toFixed(2)}" stop-color="${c}"/>`).join('')}</linearGradient>`;
    const br = F(noise(0.002, 0.7, 2, s, 'fractalNoise', 2.4) + gmap(['#000000', '#ffffff']));
    return { defs: env + br.def, body: `<rect width="512" height="512" fill="url(#mt${s})"/>` + (brushed ? layer(br, 0.35, 'overlay') : '') };
  },
  corduroy({ c, s }) {
    const p = `<pattern id="cd${s}" width="12" height="12" patternUnits="userSpaceOnUse"><rect width="12" height="12" fill="#000" opacity=".35"/><rect x="2" width="8" height="12" fill="#fff" opacity=".12"/><rect x="4" width="3" height="12" fill="#fff" opacity=".18"/></pattern>`;
    const n = F(noise(0.05, 0.6, 2, s, 'fractalNoise', 2) + gmap(['#000000', '#ffffff']));
    return { defs: p + n.def, body: solid(c) + `<rect width="512" height="512" fill="url(#cd${s})"/>` + layer(n, 0.25, 'overlay') };
  },
  canvas({ c, s }) {
    const a = F(noise(0.9, 0.06, 2, s, 'fractalNoise', 2.4) + gmap(['#000000', '#ffffff']));
    const b = F(noise(0.06, 0.9, 2, s + 1, 'fractalNoise', 2.4) + gmap(['#000000', '#ffffff']));
    return { defs: a.def + b.def, body: solid(c) + layer(a, 0.3, 'overlay') + layer(b, 0.3, 'overlay') };
  },
  cork({ s }) {
    const g = F(noise(0.12, 0.12, 4, s, 'fractalNoise', 2.2) + gmap(['#5a3a1e', '#8b5e34', '#b88a55', '#d9b27c', '#a0703f']));
    const d = F(noise(0.6, 0.6, 1, s + 4, 'turbulence', 3) + gmap(['#000000', '#ffffff']));
    return { defs: g.def + d.def, body: layer(g, 1) + layer(d, 0.2, 'multiply') };
  },
  terrazzo({ base, chips, s }) {
    const r = rng(s); let b = solid(base);
    for (let i = 0; i < 70; i++) {
      const x = r() * 512, y = 140 + r() * 320, k = 3 + r() * (i < 18 ? 16 : 7), n = 5 + Math.floor(r() * 3);
      let d = ''; for (let j = 0; j < n; j++) { const a = (j / n) * 6.283 + r() * 0.8, rr = k * (0.6 + r() * 0.6); d += (j ? 'L' : 'M') + (x + Math.cos(a) * rr).toFixed(1) + ' ' + (y + Math.sin(a) * rr).toFixed(1) + ' '; }
      b += `<path d="${d}Z" fill="${chips[Math.floor(r() * chips.length)]}"/>`;
    }
    const n = F(noise(0.8, 0.8, 2, s, 'fractalNoise', 2) + gmap(['#000000', '#ffffff']));
    return { defs: n.def, body: b + layer(n, 0.12, 'overlay') };
  },
  carbon({ c, s }) {
    const p = `<pattern id="cb${s}" width="16" height="16" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="16" height="16" fill="${c}"/><rect width="8" height="8" fill="#fff" opacity=".10"/><rect x="8" y="8" width="8" height="8" fill="#fff" opacity=".10"/><rect width="8" height="2" fill="#000" opacity=".4"/><rect x="8" y="8" width="2" height="8" fill="#000" opacity=".4"/></pattern>`;
    const sh = `<linearGradient id="cs${s}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".2"/><stop offset=".45" stop-color="#fff" stop-opacity="0"/><stop offset=".6" stop-color="#fff" stop-opacity=".12"/><stop offset="1" stop-color="#000" stop-opacity=".2"/></linearGradient>`;
    return { defs: p + sh, body: `<rect width="512" height="512" fill="url(#cb${s})"/><rect width="512" height="512" fill="url(#cs${s})"/>` };
  },
  velvet({ c, s }) {
    const g = F(noise(0.004, 0.006, 3, s, 'fractalNoise', 2) + gmap(['#000000', '#ffffff']));
    const fine = F(noise(0.9, 0.9, 1, s + 1, 'fractalNoise', 1.5) + gmap(['#000000', '#ffffff']));
    return { defs: g.def + fine.def, body: solid(c) + layer(g, 0.55, 'soft-light') + layer(g, 0.25, 'screen') + layer(fine, 0.12, 'overlay') };
  },
  scribble({ c, ink, s, words }) {
    const fib = F(noise(0.5, 0.05, 3, s, 'fractalNoise', 2) + gmap(['#000000', '#ffffff']));
    const r = rng(s); let sc = '';
    for (let i = 0; i < 5; i++) {
      let x = 70 + r() * 340, y = 200 + r() * 200, d = `M ${x} ${y}`;
      for (let j = 0; j < 7; j++) { const nx = x + (r() - 0.4) * 70, ny = y + (r() - 0.5) * 60; d += ` Q ${x + (r() - 0.5) * 60} ${y + (r() - 0.5) * 60} ${nx} ${ny}`; x = nx; y = ny; }
      sc += `<path d="${d}" fill="none" stroke="${ink}" stroke-width="${3 + r() * 5}" stroke-linecap="round" opacity=".85"/>`;
    }
    if (words) sc += `<text x="256" y="340" text-anchor="middle" font-family="Inter, sans-serif" font-weight="900" font-style="italic" font-size="84" fill="none" stroke="${ink}" stroke-width="7" stroke-linejoin="round" stroke-linecap="round" transform="rotate(-8 256 300) skewX(-12)" opacity=".92">${words}</text>`;
    return { defs: fib.def, body: solid(c) + layer(fib, 0.18, 'overlay') + sc };
  },
  foil({ s, cols }) {
    const sh = F(bump(0.012, 0.016, 4, s, 9, 'fractalNoise'));
    const g = F(`<feTurbulence type="fractalNoise" baseFrequency="0.01 0.014" numOctaves="4" seed="${s}"/>${GREY}` + gmap(cols));
    return { defs: sh.def + g.def, body: layer(g, 1) + layer(sh, 0.8, 'multiply') + layer(sh, 0.35, 'screen') };
  },
  concrete({ c, s }) {
    const g = F(noise(0.02, 0.02, 5, s, 'fractalNoise', 1.8) + gmap(['#000000', '#ffffff']));
    const pores = F(`<feTurbulence type="fractalNoise" baseFrequency="0.25" numOctaves="1" seed="${s + 9}"/>${GREY}<feComponentTransfer><feFuncR type="discrete" tableValues="0 1 1 1 1 1 1 1 1 1 1 1 1 1 1"/><feFuncG type="discrete" tableValues="0 1 1 1 1 1 1 1 1 1 1 1 1 1 1"/><feFuncB type="discrete" tableValues="0 1 1 1 1 1 1 1 1 1 1 1 1 1 1"/></feComponentTransfer>`);
    return { defs: g.def + pores.def, body: solid(c) + layer(g, 0.45, 'soft-light') + layer(pores, 0.5, 'multiply') };
  },
  tartan({ base, lines, s }) {
    let b = solid(base);
    const r = rng(s);
    for (let k = 0; k < 2; k++) for (let i = 0; i < 6; i++) {
      const pos = i * 96 + r() * 20, w = [8, 22, 40][i % 3], col = lines[i % lines.length];
      b += k ? `<rect x="${pos}" y="0" width="${w}" height="512" fill="${col}" opacity=".5"/>` : `<rect x="0" y="${pos + 40}" width="512" height="${w}" fill="${col}" opacity=".5"/>`;
    }
    const tw = `<pattern id="tt${s}" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="6" height="2.5" fill="#000" opacity=".12"/></pattern>`;
    return { defs: tw, body: b + `<rect width="512" height="512" fill="url(#tt${s})"/>` };
  },
  camo({ cols, s }) {
    const g = F(`<feTurbulence type="fractalNoise" baseFrequency="0.012" numOctaves="3" seed="${s}"/>${GREY}<feComponentTransfer><feFuncR type="linear" slope="2.6" intercept="-0.8"/><feFuncG type="linear" slope="2.6" intercept="-0.8"/><feFuncB type="linear" slope="2.6" intercept="-0.8"/></feComponentTransfer>` + gmap(cols, 'discrete'));
    const t = F(noise(0.7, 0.7, 2, s + 1, 'fractalNoise', 2) + gmap(['#000000', '#ffffff']));
    return { defs: g.def + t.def, body: layer(g, 1) + layer(t, 0.2, 'overlay') };
  },
  glitter({ c, s }) {
    const r = rng(s); let sp = '';
    for (let i = 0; i < 900; i++) {
      const x = r() * 512, y = 150 + r() * 300, k = r();
      sp += `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${(0.8 + k * 2.4).toFixed(1)}" fill="#fff" opacity="${(0.15 + r() * 0.85).toFixed(2)}"/>`;
    }
    for (let i = 0; i < 26; i++) {
      const x = r() * 512, y = 160 + r() * 280, k = 6 + r() * 8;
      sp += `<path d="M ${x} ${y - k} L ${x + 1.4} ${y} L ${x} ${y + k} L ${x - 1.4} ${y} Z M ${x - k} ${y} L ${x} ${y + 1.4} L ${x + k} ${y} L ${x} ${y - 1.4} Z" fill="#fff"/>`;
    }
    const g = F(noise(0.01, 0.01, 2, s + 3, 'fractalNoise', 2) + gmap(['#000000', '#ffffff']));
    return { defs: g.def, body: solid(c) + layer(g, 0.45, 'soft-light') + `<g style="mix-blend-mode:screen">${sp}</g>` };
  },
};

// ---------- extras ----------
const face = (kind, ink = '#111') => {
  const eyes = {
    happy: `<ellipse cx="236" cy="272" rx="11" ry="17" fill="${ink}"/><ellipse cx="276" cy="272" rx="11" ry="17" fill="${ink}"/><circle cx="239" cy="265" r="4" fill="#fff"/><circle cx="279" cy="265" r="4" fill="#fff"/><path d="M 238 305 Q 256 320 274 305" stroke="${ink}" stroke-width="5" fill="none" stroke-linecap="round"/>`,
    sleepy: `<path d="M 220 276 Q 234 286 248 276" stroke="${ink}" stroke-width="5" fill="none" stroke-linecap="round"/><path d="M 264 276 Q 278 286 292 276" stroke="${ink}" stroke-width="5" fill="none" stroke-linecap="round"/><path d="M 246 306 H 266" stroke="${ink}" stroke-width="5" stroke-linecap="round"/>`,
    wow: `<circle cx="232" cy="272" r="12" fill="${ink}"/><circle cx="280" cy="272" r="12" fill="${ink}"/><circle cx="235" cy="268" r="4" fill="#fff"/><circle cx="283" cy="268" r="4" fill="#fff"/><ellipse cx="256" cy="312" rx="9" ry="11" fill="${ink}"/>`,
    wink: `<ellipse cx="236" cy="272" rx="11" ry="16" fill="${ink}"/><circle cx="239" cy="265" r="4" fill="#fff"/><path d="M 264 276 Q 278 266 292 276" stroke="${ink}" stroke-width="5" fill="none" stroke-linecap="round"/><path d="M 236 302 Q 256 318 276 302" stroke="${ink}" stroke-width="5" fill="none" stroke-linecap="round"/>`,
    calm: `<path d="M 222 272 H 248" stroke="${ink}" stroke-width="6" stroke-linecap="round"/><path d="M 264 272 H 290" stroke="${ink}" stroke-width="6" stroke-linecap="round"/><path d="M 244 302 Q 256 310 268 302" stroke="${ink}" stroke-width="5" fill="none" stroke-linecap="round"/>`,
  };
  return `<g>${eyes[kind]}</g>`;
};
const label = (text, bg = '#f2efe6', ink = '#1a1a1a', x = 66, y = 178) =>
  `<g transform="translate(${x} ${y}) rotate(-2)"><rect width="104" height="46" rx="4" fill="${bg}"/><rect x="5" y="5" width="94" height="36" rx="2" fill="none" stroke="${ink}" stroke-opacity=".35" stroke-dasharray="4 3"/><text x="52" y="30" text-anchor="middle" font-family="Inter, sans-serif" font-weight="700" font-size="17" fill="${ink}">${text}</text></g>`;
const tag = (text, x = 392, y = 392) =>
  `<g transform="translate(${x} ${y})"><rect width="60" height="22" rx="3" fill="#111" opacity=".85"/><text x="30" y="15" text-anchor="middle" font-family="Inter, sans-serif" font-weight="600" font-size="10" fill="#ddd" letter-spacing="1">${text}</text></g>`;
const stitch = (col) => `<path d="${FRONT_IN(14)}" fill="none" stroke="${col}" stroke-width="3.2" stroke-dasharray="11 7" stroke-linecap="round" opacity=".9"/>`;
const kintsugi = (s) => { const r = rng(s); let p = ''; for (let k = 0; k < 4; k++) { let x = 34 + r() * 440, y = 160 + r() * 60, d = `M ${x} ${y}`; for (let j = 0; j < 9; j++) { x += (r() - 0.5) * 70; y += 18 + r() * 30; d += ` L ${x.toFixed(0)} ${y.toFixed(0)}`; } p += `<path d="${d}" stroke="#e7c35a" stroke-width="${2 + r() * 3}" fill="none" stroke-linejoin="round"/><path d="${d}" stroke="#fff6c8" stroke-width="1" fill="none" opacity=".7"/>`; } return p; };

// ---------- the hundred ----------
const D = [];
const add = (mat, p, ex = {}) => D.push({ mat, p: { s: D.length * 17 + 3, ...p }, ...ex });
// denim 8
add('denim', { c: '#3a5f95' }, { face: 'happy', stitchC: '#d9a24a' });
add('denim', { c: '#1f3357' }, { stitchC: '#d9a24a', tag: 'DENIM' });
add('denim', { c: '#6f8fb8' }, { stitchC: '#e8c27a' });
add('denim', { c: '#1d1d22' }, { stitchC: '#c9c9c9', label: 'Durable' });
add('denim', { c: '#555b66' }, { stitchC: '#e0b25c' });
add('denim', { c: '#8fb0d6' }, { face: 'wink', stitchC: '#f0d18a' });
add('denim', { c: '#2c4a7a' }, { stitchC: '#d9a24a', label: 'Raw' });
add('denim', { c: '#41506b' }, { stitchC: '#b9b9b9' });
// leather 8
add('leather', { c: '#e6ddcf' }, { tabC: '#8a4a22' });
add('leather', { c: '#a0612f' }, { stitchC: '#f0dcb4' });
add('leather', { c: '#1c1a19' }, { stitchC: '#8a8a8a' });
add('leather', { c: '#9b1c1f' }, { stitchC: '#f2d0b0' });
add('leather', { c: '#556b3a' });
add('leather', { c: '#4a1d26' }, { stitchC: '#d8b48a' });
add('leather', { c: '#2a6b4f', croc: true });
add('leather', { c: '#c9a46b', croc: true });
// marble 8
add('marble', { base: '#121212', vein: '#ffffff', cloud: ['#0b0b0b', '#262626', '#141414'] }, { gold: true });
add('marble', { base: '#efefec', vein: '#6f6f72', cloud: ['#e6e6e2', '#ffffff', '#d9d9d5'] });
add('marble', { base: '#0f2b22', vein: '#d6e8df', cloud: ['#0b231b', '#1b4637', '#0e2a20'] }, { gold: true });
add('marble', { base: '#ecd9d2', vein: '#9b7a70', cloud: ['#e8d0c8', '#f7ebe6', '#dcc0b6'] });
add('marble', { base: '#f1f1ee', vein: '#3c5f8a', cloud: ['#ebebe7', '#ffffff', '#dfe3e6'] });
add('marble', { base: '#1a1a1a', vein: '#d8d8d8', cloud: ['#121212', '#2a2a2a', '#161616'] });
add('marble', { base: '#d8c6a5', vein: '#8a6d45', cloud: ['#cdb894', '#e7d9bc', '#c4ad86'] });
add('marble', { base: '#2b1a12', vein: '#e2a85a', cloud: ['#24150e', '#3d2619', '#1e110b'] });
// felt 7
add('felt', { c: '#8e8f91' }, { face: 'sleepy' });
add('felt', { c: '#2f3033' });
add('felt', { c: '#d8a531' });
add('felt', { c: '#8fa38a' }, { face: 'calm' });
add('felt', { c: '#d99aa0' });
add('felt', { c: '#e9e1d0' }, { label: 'Wool' });
add('felt', { c: '#24324f' });
// puffer 8
add('puffer', { c: '#0d0d0e', n: 4, gloss: 0.6 });
add('quilt', { c: '#c4161c' });
add('puffer', { c: '#b8bcc2', n: 4, gloss: 0.7 });
add('puffer', { c: '#1b2440', n: 3, gloss: 0.5 });
add('puffer', { c: '#4f5a2e', n: 5, gloss: 0.45 });
add('puffer', { c: '#efe8da', n: 4, gloss: 0.35 });
add('puffer', { c: '#e2571d', dir: 'v', gloss: 0.5 });
add('puffer', { c: '#a99ad6', n: 3, gloss: 0.55 });
// wood 6
add('wood', { cols: ['#6b3f1f', '#a8693a', '#c98e55'] });
add('wood', { cols: ['#c29a63', '#dcb886', '#b0844e'] });
add('wood', { cols: ['#4a2b17', '#7a4a28', '#b07a45', '#5e3820'], fx: 0.012, fy: 0.02 });
add('wood', { cols: ['#141110', '#2a2420', '#3b322b'] });
add('wood', { cols: ['#e3c99c', '#f2dfba', '#d4b583'] });
add('wood', { cols: ['#7a2f1b', '#a5492a', '#8a3a21'] });
// plastic 8
add('plastic', { c: '#1aa6b7' }, { label: 'iMac', labelStyle: 'serif' });
add('plastic', { c: '#f07a1a' });
add('plastic', { c: '#6a3fb0' });
add('plastic', { c: '#7cc242' });
add('plastic', { c: '#e2364b' });
add('plastic', { c: '#4a4f57' });
add('plastic', { c: '#9fb9a4' });
add('plastic', { c: '#bfe3f2' });
// metal 7
add('metal', { cols: ['#f4f5f7', '#a9adb3', '#e9ebee', '#7d8188', '#d5d8dc'] });
add('metal', { cols: ['#ffffff', '#5d6168', '#f7f8fa', '#1e2024', '#c9ccd1'], brushed: false });
add('metal', { cols: ['#fff1b8', '#c9952f', '#f7da86', '#8a5d14', '#e8c063'] });
add('metal', { cols: ['#f7c8a3', '#b06a3b', '#f0b080', '#6e3a1b', '#d98c5a'] });
add('metal', { cols: ['#d9d6cf', '#7d786f', '#c2bdb3', '#4d4a45', '#aaa59b'] });
add('metal', { cols: ['#ffe1db', '#c98b80', '#f6c9bf', '#8a5249', '#e3aaa0'] });
add('metal', { cols: ['#8fa9d9', '#2c4a85', '#7d9ad0', '#142a57', '#5b7cbb'] });
// corduroy 5
add('corduroy', { c: '#8a5a2b' });
add('corduroy', { c: '#3d4a2a' });
add('corduroy', { c: '#c6852f' });
add('corduroy', { c: '#5a2433' }, { face: 'happy', faceInk: '#f2e6d0' });
add('corduroy', { c: '#d9cbb0' });
// canvas 3
add('canvas', { c: '#d9cfb8' }, { label: 'Canvas', stitchC: '#6a5a40' });
add('canvas', { c: '#3d4f3a' }, { stitchC: '#d9c48a' });
add('canvas', { c: '#2a2f3a' }, { tag: 'UTILITY' });
// cork 3
add('cork', {});
add('cork', {}, { face: 'wow' });
add('cork', {}, { label: 'Notes' });
// terrazzo 5
add('terrazzo', { base: '#efe9df', chips: ['#d96a4b', '#3c5f8a', '#e8b23a', '#2f2f2f', '#9fb9a4'] });
add('terrazzo', { base: '#1c1c1c', chips: ['#ffffff', '#bdbdbd', '#e8b23a', '#6f6f6f'] });
add('terrazzo', { base: '#f2d6cc', chips: ['#c0504d', '#ffffff', '#8a5a4f', '#f0a08a'] });
add('terrazzo', { base: '#cfd8d4', chips: ['#2f5d50', '#ffffff', '#86a89a', '#1d2b27'] });
add('terrazzo', { base: '#e9e4f2', chips: ['#6a5acd', '#f2c94c', '#2d2d2d', '#ffffff'] });
// carbon 3
add('carbon', { c: '#1a1b1e' });
add('carbon', { c: '#2a0f12' });
add('carbon', { c: '#0f1a2e' });
// velvet 3
add('velvet', { c: '#5a1030' });
add('velvet', { c: '#173a2e' });
add('velvet', { c: '#1b1f4a' });
// scribble paper 4
add('scribble', { c: '#e8e8ea', ink: '#111', words: 'FOLDER' });
add('scribble', { c: '#c4a57a', ink: '#1a1a1a' });
add('scribble', { c: '#f4f1e6', ink: '#d6342c', words: 'yrs.' });
add('scribble', { c: '#1d1d1f', ink: '#f2f2f2', words: 'stuff' });
// foil 2
add('foil', { cols: ['#8e9399', '#e9ecef', '#b5b9be', '#f8f9fa', '#7d828a'] });
add('foil', { cols: ['#a37b2c', '#f6dd8f', '#c99a3d', '#fff2c2', '#8a6420'] });
// concrete 2
add('concrete', { c: '#9a9894' });
add('concrete', { c: '#4a4a4a' }, { tag: 'BRUTAL' });
// tartan 3
add('tartan', { base: '#9c1b1b', lines: ['#0e2a1b', '#111', '#e8c547'] });
add('tartan', { base: '#173a2e', lines: ['#0e1a3a', '#111', '#c9a227'] });
add('tartan', { base: '#e9e4d8', lines: ['#2b2b2b', '#a33', '#556'] });
// camo 3
add('camo', { cols: ['#2f3a22', '#5b6b3a', '#8a7f55', '#1f1f17'] });
add('camo', { cols: ['#d8d8d8', '#9a9a9a', '#5a5a5a', '#2a2a2a'] });
add('camo', { cols: ['#c8a879', '#9b7b4f', '#6e5638', '#e3d1ad'] });
// glitter 2
add('glitter', { c: '#c9a54a' });
add('glitter', { c: '#2a2a33' });
// and to round it out: more felt and leather with faces
add('felt', { c: '#6aa0c8' }, { face: 'happy' });
add('leather', { c: '#e2c7a0' }, { face: 'calm', faceInk: '#4a2f1a' });

if (D.length !== 100) { console.error('designs:', D.length); process.exit(1); }

function svg(d) {
  uid = 0;
  const m = M[d.mat](d.p);
  const glass = m.glass;
  const backTone = glass ? 0.15 : 0.38;
  const ex = (d.gold ? kintsugi(d.p.s) : '') + (d.stitchC ? stitch(d.stitchC) : '') +
    (d.label ? (d.labelStyle === 'serif' ? `<text x="440" y="408" text-anchor="end" font-family="Georgia, serif" font-size="30" fill="#fff" opacity=".85">${d.label}</text>` : label(d.label)) : '') +
    (d.tag ? tag(d.tag) : '') + (d.face ? face(d.face, d.faceInk || '#111') : '');
  const tab = d.tabC ? `<path d="${BACK}" fill="${d.tabC}"/>` : '';
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
<defs>
 <clipPath id="back"><path d="${BACK}"/></clipPath>
 <clipPath id="front"><path d="${FRONT}"/></clipPath>
 <filter id="soft"><feGaussianBlur stdDeviation="1"/></filter>
 <filter id="shadow" x="-20%" y="-20%" width="140%" height="160%"><feGaussianBlur stdDeviation="14"/></filter>
 <linearGradient id="light" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".22"/><stop offset=".12" stop-color="#fff" stop-opacity=".04"/><stop offset=".7" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".22"/></linearGradient>
 <linearGradient id="paper" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff"/><stop offset="1" stop-color="#e9e7e2"/></linearGradient>
 ${m.defs}
</defs>
<ellipse cx="256" cy="440" rx="200" ry="14" fill="#000" opacity=".35" filter="url(#shadow)"/>
<g clip-path="url(#back)">${tab || m.body}<rect width="512" height="512" fill="#000" opacity="${backTone}"/></g>
<path d="M 46 128 Q 46 100 74 100 L 178 100 Q 193 100 203 110" fill="none" stroke="#fff" stroke-opacity=".25" stroke-width="2"/>
<rect x="62" y="122" width="388" height="120" rx="8" fill="url(#paper)" transform="rotate(-1.2 256 180)"/>
<rect x="70" y="136" width="372" height="110" rx="8" fill="#f6f5f1"/>
<g clip-path="url(#front)">${m.body}<rect width="512" height="512" fill="url(#light)"/>${ex}</g>
<path d="${FRONT}" fill="none" stroke="#000" stroke-opacity=".28" stroke-width="2"/>
<path d="M 36 190 Q 36 162 64 162 L 448 162 Q 476 162 476 190" fill="none" stroke="#fff" stroke-opacity=".38" stroke-width="2.5"/>
</svg>`;
}

(async () => {
  const out = process.argv[2] || 'out';
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 512, height: 512 } });
  for (let i = 0; i < D.length; i++) {
    await page.setContent(`<html><body style="margin:0;background:transparent">${svg(D[i])}</body></html>`);
    await page.screenshot({ path: path.join(out, String(i + 1).padStart(3, '0') + '.png'), omitBackground: true, clip: { x: 0, y: 0, width: 512, height: 512 } });
  }
  fs.writeFileSync(path.join(out, 'designs.json'), JSON.stringify(D.map((d, i) => ({ n: i + 1, mat: d.mat }))));
  await browser.close();
  console.log('drew', D.length);
})();
