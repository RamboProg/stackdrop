import { css, hsv, lighten, mix, type Rgb } from "../lib/color.ts";
import { createRng, type Rng } from "../lib/random.ts";
import { bgBottom, bgTop, floorColor, isCalm, type Theme } from "./palette.ts";

/**
 * Port of game/Backdrop.kt, game/scenery/CalmScenery.kt, game/scenery/AmbientLayer.kt and
 * game/tower/Skyline.kt. Neon themes get the synthwave sky, stars, striped sun and grid; calm themes
 * get a soft landscape with slow ambient motion. Silhouettes are pre-rendered once per theme and size.
 */

const TAU = Math.PI * 2;
const SOFT_WHITE = 0xf8f4ee;

type Layer = HTMLCanvasElement;

function canvas(w: number, h: number): Layer {
  const c = document.createElement("canvas");
  c.width = Math.max(1, Math.round(w));
  c.height = Math.max(1, Math.round(h));
  return c;
}

function get2d(c: Layer): CanvasRenderingContext2D {
  const ctx = c.getContext("2d");
  if (!ctx) throw new Error("2D canvas unavailable");
  return ctx;
}

const cache = new Map<string, unknown>();
function cached<T>(key: string, build: () => T): T {
  if (!cache.has(key)) cache.set(key, build());
  return cache.get(key) as T;
}

function hline(ctx: CanvasRenderingContext2D, x0: number, y0: number, x1: number, y1: number): void {
  ctx.beginPath();
  ctx.moveTo(x0, y0);
  ctx.lineTo(x1, y1);
  ctx.stroke();
}

// --- Entry points -----------------------------------------------------------------------------

/** [horizon] is the screen y of the horizon; it slides down as the camera climbs. */
export function drawBackdrop(
  ctx: CanvasRenderingContext2D, theme: Theme, drift: number, horizon: number, time: number, w: number, h: number,
): void {
  if (isCalm(theme)) {
    const rise = (horizon - h * 0.73) / 0.45;
    drawCalmSky(ctx, theme, rise, time, w, h);
    drawAmbientBack(ctx, theme, rise, time, w, h);
    drawCalmLayers(ctx, theme, rise, w, h);
    drawAmbientFront(ctx, theme, rise, time, w, h);
    return;
  }
  const sky = ctx.createLinearGradient(0, 0, 0, h);
  sky.addColorStop(0, css(bgTop(theme, drift)));
  sky.addColorStop(1, css(bgBottom(theme, drift)));
  ctx.fillStyle = sky;
  ctx.fillRect(0, 0, w, h);
  drawStars(ctx, horizon, time, w, h);
  drawSun(ctx, theme, horizon, w, h);
  if (horizon < h) drawGrid(ctx, theme, drift, horizon, time, w, h);
}

/** CRT scanlines (neon only) and the corner vignette, drawn over everything including the HUD. */
export function drawOverlay(ctx: CanvasRenderingContext2D, theme: Theme, w: number, h: number): void {
  if (theme.scanlines) {
    ctx.fillStyle = "rgba(0,0,0,0.157)";
    for (let y = 4; y < h; y += 5) ctx.fillRect(0, y, w, 1);
  }
  const v = ctx.createRadialGradient(w / 2, h * 0.45, 0, w / 2, h * 0.45, Math.hypot(w, h) * 0.62);
  v.addColorStop(0, "rgba(0,0,0,0)");
  v.addColorStop(0.55, "rgba(0,0,0,0)");
  v.addColorStop(1, css(0x000000, 175 * theme.vignette));
  ctx.fillStyle = v;
  ctx.fillRect(0, 0, w, h);
}

// --- Synthwave --------------------------------------------------------------------------------

const STARS = (() => {
  const rng = createRng(7);
  return Array.from({ length: 90 }, () => ({ x: rng(), y: rng(), size: 0.0015 + rng() * 0.003, phase: rng() * 6.28 }));
})();

function drawStars(ctx: CanvasRenderingContext2D, horizon: number, time: number, w: number, h: number): void {
  const band = h * 1.4;
  const shift = (horizon - h * 0.72) * 0.15;
  for (const s of STARS) {
    const y = (((s.y * band + shift) % band) + band) % band - h * 0.2;
    if (y > horizon - h * 0.02) continue;
    const twinkle = 0.55 + 0.45 * Math.sin(time * 1.7 + s.phase);
    ctx.fillStyle = css(0xffffff, 200 * twinkle);
    ctx.beginPath();
    ctx.arc(s.x * w, y, s.size * w, 0, TAU);
    ctx.fill();
  }
}

function drawSun(ctx: CanvasRenderingContext2D, theme: Theme, horizon: number, w: number, h: number): void {
  const r = w * 0.27;
  const cx = w / 2;
  const cy = horizon - r * 0.42;
  const c = r * 1.6;
  if (cy - c > h) return;
  ctx.save();
  ctx.beginPath();
  ctx.rect(0, 0, w, horizon);
  ctx.clip();

  const halo = ctx.createRadialGradient(cx, cy, 0, cx, cy, c);
  halo.addColorStop(0.3, css(theme.accent, 110));
  halo.addColorStop(0.6, css(theme.accent, 35));
  halo.addColorStop(1, css(theme.accent, 0));
  ctx.fillStyle = halo;
  ctx.beginPath();
  ctx.arc(cx, cy, c, 0, TAU);
  ctx.fill();

  // The disc with its stripes cut out, so the halo shows through the gaps as in the game's bitmap.
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, TAU);
  ctx.clip();
  const disc = ctx.createLinearGradient(0, cy - r, 0, cy + r);
  disc.addColorStop(0, css(theme.accent2));
  disc.addColorStop(1, css(theme.accent));
  ctx.fillStyle = disc;
  let y = cy - r;
  for (let k = 0; k <= 7; k++) {
    const stripeTop = k < 7 ? cy + r * (0.05 + k * 0.14) : cy + r;
    ctx.fillRect(cx - r, y, r * 2, stripeTop - y);
    if (k < 7) y = stripeTop + r * (0.015 + k * 0.016);
  }
  ctx.restore();
}

function drawGrid(
  ctx: CanvasRenderingContext2D, theme: Theme, drift: number, horizon: number, time: number, w: number, h: number,
): void {
  const accent = theme.accent;
  ctx.fillStyle = css(floorColor(theme, drift));
  ctx.fillRect(0, horizon, w, h - horizon);

  const depth = h - horizon;
  const spread = depth / (h * 0.28);
  const cx = w / 2;
  ctx.lineCap = "round";
  ctx.strokeStyle = css(accent, 70);
  ctx.lineWidth = w * 0.003;
  for (let i = -12; i <= 12; i++) {
    const top = cx + i * w * 0.018;
    hline(ctx, top, horizon, top + i * w * 0.11 * spread, h);
  }

  const phase = (time * 0.45) % 1;
  for (let k = 0; k < 10; k++) {
    const t = (k + phase) / 10;
    const y = horizon + depth * t * t;
    const a = 30 + 170 * t;
    const width = w * 0.0035 * (0.5 + t);
    ctx.strokeStyle = css(accent, a * 0.25);
    ctx.lineWidth = width * 3;
    hline(ctx, 0, y, w, y);
    ctx.strokeStyle = css(accent, a);
    ctx.lineWidth = width;
    hline(ctx, 0, y, w, y);
  }

  ctx.strokeStyle = css(accent, 70);
  ctx.lineWidth = w * 0.012;
  hline(ctx, 0, horizon, w, horizon);
  ctx.strokeStyle = css(lighten(accent, 0.6), 230);
  ctx.lineWidth = w * 0.003;
  hline(ctx, 0, horizon, w, horizon);
}

// --- Calm scenery -----------------------------------------------------------------------------

const LAYER_HEIGHT = [0.3, 0.24, 0.18];
const LAYER_BOTTOM = [0.8, 0.83, 0.87];
const LAYER_DEPTH = [0.35, 0.62, 0.88];
const PARALLAX = [0.08, 0.18, 0.32];
const RAY_X = [0.18, 0.42, 0.63, 0.85];
const RAY_ANGLE = [-12, -4, 5, 13];
const SCENERY_INDEX = { synth: 0, hills: 1, forest: 2, lagoon: 3, aurora: 4, petals: 5, dunes: 6, slate: 7 };

const layerColor = (t: Theme, i: number): Rgb => mix(t.skyBottom, t.ground, LAYER_DEPTH[i]);

function drawCalmSky(ctx: CanvasRenderingContext2D, t: Theme, rise: number, time: number, w: number, h: number): void {
  const sky = ctx.createLinearGradient(0, 0, 0, h);
  sky.addColorStop(0, css(t.skyTop));
  sky.addColorStop(0.5, css(t.skyMid));
  sky.addColorStop(1, css(t.skyBottom));
  ctx.fillStyle = sky;
  ctx.fillRect(0, 0, w, h);

  if (t.scenery === "hills") {
    const r = w * 0.24;
    const cx = w * 0.62;
    const cy = h * 0.6 + rise * 0.04;
    const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, r);
    g.addColorStop(0, css(t.accent, 70));
    g.addColorStop(0.7, css(t.accent, 45));
    g.addColorStop(1, css(t.accent, 0));
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, TAU);
    ctx.fill();
  } else if (t.scenery === "lagoon") {
    const len = h * 0.7;
    const light = mix(t.accent, SOFT_WHITE, 0.5);
    const sway = Math.sin(time * TAU * 0.1) * 4;
    for (let k = 0; k < RAY_X.length; k++) {
      ctx.save();
      ctx.translate(w * RAY_X[k], -h * 0.05);
      ctx.rotate(((RAY_ANGLE[k] + sway * (0.6 + 0.2 * k)) * Math.PI) / 180);
      const g = ctx.createLinearGradient(0, 0, 0, len);
      g.addColorStop(0, css(light, 20));
      g.addColorStop(1, css(light, 0));
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.moveTo(-w * 0.03, 0);
      ctx.lineTo(w * 0.03, 0);
      ctx.lineTo(w * 0.12, len);
      ctx.lineTo(-w * 0.12, len);
      ctx.closePath();
      ctx.fill();
      ctx.restore();
    }
  }
}

function drawCalmLayers(ctx: CanvasRenderingContext2D, t: Theme, rise: number, w: number, h: number): void {
  if (t.scenery === "slate") {
    const y = h * LAYER_BOTTOM[1] + rise * PARALLAX[1];
    if (y < h) {
      ctx.fillStyle = css(layerColor(t, 2));
      ctx.fillRect(0, y, w, h - y);
      ctx.strokeStyle = css(t.textDim, 60);
      ctx.lineWidth = Math.max(1, w * 0.0015);
      hline(ctx, 0, y, w, y);
    }
    return;
  }
  const layers = cached(`layers:${t.id}:${w}x${h}`, () => [0, 1, 2].map((i) => buildLayer(t, i, w, h)));
  for (let i = 0; i < 3; i++) {
    const bmp = layers[i];
    const bottom = h * LAYER_BOTTOM[i] + rise * PARALLAX[i];
    if (bottom - bmp.height > h) continue;
    ctx.drawImage(bmp, 0, bottom - bmp.height);
    // Solid fill under each layer so a faster near layer never uncovers the sky.
    if (bottom < h) {
      ctx.fillStyle = css(layerColor(t, i));
      ctx.fillRect(0, bottom - 1, w, h - bottom + 1);
    }
  }
}

function buildLayer(t: Theme, i: number, w: number, h: number): Layer {
  const hh = Math.max(1, Math.round(h * LAYER_HEIGHT[i]));
  const bmp = canvas(w, hh);
  const c = get2d(bmp);
  const rng = createRng(41 + i * 13 + SCENERY_INDEX[t.scenery] * 31);
  const color = layerColor(t, i);
  c.fillStyle = css(color);
  switch (t.scenery) {
    case "hills":
      ridge(c, w, hh, 0.45 + 0.1 * i, 0.22, 1.2 + 0.5 * i, rng);
      break;
    case "forest":
      ridge(c, w, hh, 0.8, 0.06, 1.5, rng);
      pines(c, w, hh, i, rng);
      break;
    case "lagoon":
      ridge(c, w, hh, 0.62 + 0.06 * i, 0.14, 2 + i, rng);
      if (i > 0) kelp(c, w, hh, rng, color);
      break;
    case "aurora":
      peaks(c, w, hh, i, rng, mix(color, t.text, 0.4 - 0.1 * i));
      break;
    case "petals":
      ridge(c, w, hh, 0.78, 0.08, 1.3, rng);
      canopies(c, w, hh, i, rng);
      break;
    case "dunes":
      if (i === 0) haze(c, w, hh, t);
      c.fillStyle = css(color);
      ridge(c, w, hh, 0.5 + 0.08 * i, 0.2, 0.9 + 0.4 * i, rng);
      break;
    default:
      break;
  }
  return bmp;
}

/** Smooth hill line from two sine waves, filled down to the bottom edge with the current fill. */
function ridge(c: CanvasRenderingContext2D, width: number, hh: number, base: number, amp: number, waves: number, rng: Rng): void {
  const p1 = rng() * TAU;
  const p2 = rng() * TAU;
  const samples = 24;
  c.beginPath();
  c.moveTo(0, hh);
  let px = 0;
  let py = 0;
  for (let i = 0; i <= samples; i++) {
    const t = i / samples;
    const x = width * t;
    const y = hh * (base - amp * (0.65 * Math.sin(t * waves * TAU + p1) + 0.35 * Math.sin(t * waves * 2.3 * TAU + p2)));
    if (i === 0) c.lineTo(x, y);
    else c.quadraticCurveTo(px, py, (px + x) / 2, (py + y) / 2);
    px = x;
    py = y;
  }
  c.lineTo(px, py);
  c.lineTo(width, hh);
  c.closePath();
  c.fill();
}

function pines(c: CanvasRenderingContext2D, width: number, hh: number, layer: number, rng: Rng): void {
  const bottom = hh * 0.86;
  let x = -rng() * width * 0.04;
  while (x < width + width * 0.04) {
    const ph = Math.min(hh * 0.95, hh * (0.4 + rng() * 0.4) * (0.7 + 0.15 * layer));
    const pw = ph * 0.38;
    c.beginPath();
    for (let k = 0; k < 3; k++) {
      const apex = bottom - ph + ph * 0.25 * k;
      const base = apex + ph * 0.45;
      const half = pw * (0.3 + 0.18 * k);
      c.moveTo(x, apex);
      c.lineTo(x + half, base);
      c.lineTo(x - half, base);
      c.closePath();
    }
    c.fill();
    c.fillRect(x - pw * 0.05, bottom - ph * 0.1, pw * 0.1, hh - bottom + ph * 0.1);
    x += pw * (0.6 + rng() * 0.9);
  }
}

function kelp(c: CanvasRenderingContext2D, width: number, hh: number, rng: Rng, color: Rgb): void {
  c.strokeStyle = css(color);
  c.lineCap = "round";
  c.lineWidth = width * 0.006;
  for (let k = 0; k < 10; k++) {
    const x = rng() * width;
    const top = hh * (0.2 + rng() * 0.35);
    const bend = width * (rng() - 0.5) * 0.05;
    c.beginPath();
    c.moveTo(x, hh);
    c.quadraticCurveTo(x + bend, (hh + top) / 2, x - bend * 0.5, top);
    c.stroke();
  }
}

function peaks(c: CanvasRenderingContext2D, width: number, hh: number, layer: number, rng: Rng, snow: Rgb): void {
  const n = 5 + layer * 2;
  const count = n * 2 + 1;
  const step = (width * 1.1) / (count - 1);
  const xs: number[] = [];
  const ys: number[] = [];
  for (let i = 0; i < count; i++) {
    xs.push(-width * 0.05 + i * step + (rng() - 0.5) * step * 0.5);
    ys.push(i % 2 === 1 ? hh * (0.12 + rng() * 0.3 + 0.08 * layer) : hh * (0.55 + rng() * 0.2));
  }
  c.beginPath();
  c.moveTo(xs[0], hh);
  for (let i = 0; i < count; i++) c.lineTo(xs[i], ys[i]);
  c.lineTo(xs[count - 1], hh);
  c.closePath();
  c.fill();

  c.fillStyle = css(snow);
  for (let i = 1; i < count - 1; i += 2) {
    const f = 0.28;
    const lx = xs[i] + (xs[i - 1] - xs[i]) * f;
    const ly = ys[i] + (ys[i - 1] - ys[i]) * f;
    const rx = xs[i] + (xs[i + 1] - xs[i]) * f;
    const ry = ys[i] + (ys[i + 1] - ys[i]) * f;
    c.beginPath();
    c.moveTo(xs[i], ys[i]);
    c.lineTo(rx, ry);
    c.lineTo(xs[i] + (rx - xs[i]) * 0.3, Math.min(ly, ry) * 0.9 + ys[i] * 0.1);
    c.lineTo(xs[i] - (xs[i] - lx) * 0.2, Math.max(ly, ry) * 0.85 + ys[i] * 0.15);
    c.lineTo(lx, ly);
    c.closePath();
    c.fill();
  }
}

function canopies(c: CanvasRenderingContext2D, width: number, hh: number, layer: number, rng: Rng): void {
  const u = width / 100;
  const base = hh * 0.8;
  let x = rng() * u * 6;
  const blob = (cx: number, cy: number, r: number): void => {
    c.beginPath();
    c.arc(cx, cy, r, 0, TAU);
    c.fill();
  };
  while (x < width + u * 4) {
    const r = u * (4 + rng() * 3) * (0.8 + 0.25 * layer);
    const trunk = r * 1.1;
    c.fillRect(x - r * 0.12, base - trunk, r * 0.24, hh - base + trunk);
    blob(x, base - trunk - r * 0.4, r);
    blob(x - r * 0.7, base - trunk + r * 0.1, r * 0.7);
    blob(x + r * 0.7, base - trunk + r * 0.1, r * 0.75);
    x += r * (2.6 + rng() * 2);
  }
}

/** A faint light band above the far dunes. */
function haze(c: CanvasRenderingContext2D, width: number, hh: number, t: Theme): void {
  const light = mix(t.skyBottom, SOFT_WHITE, 0.4);
  const g = c.createLinearGradient(0, 0, 0, hh * 0.75);
  g.addColorStop(0, css(light, 0));
  g.addColorStop(0.75, css(light, 80));
  g.addColorStop(1, css(light, 0));
  c.fillStyle = g;
  c.fillRect(0, 0, width, hh * 0.75);
}

// --- Ambient motion ---------------------------------------------------------------------------

type Mote = { x: number; y: number; size: number; phase: number; speed: number };

const AMBIENT_COUNT = { stars: 16, bubbles: 20, aurora: 14, petals: 24, motes: 16, fog: 0, none: 0 };
const AMBIENT_INDEX = { stars: 0, none: 1, fog: 2, bubbles: 3, aurora: 4, petals: 5, motes: 6 };

function motes(t: Theme): Mote[] {
  return cached(`motes:${t.ambient}`, () => {
    const rng = createRng(AMBIENT_INDEX[t.ambient] * 17 + 3);
    return Array.from({ length: AMBIENT_COUNT[t.ambient] }, () => ({
      x: rng(), y: rng(), size: rng(), phase: rng() * TAU, speed: rng(),
    }));
  });
}

function drawAmbientBack(ctx: CanvasRenderingContext2D, t: Theme, rise: number, time: number, w: number, h: number): void {
  if (t.ambient === "stars") ambientStars(ctx, t, rise, time, w, h, 0.45);
  if (t.ambient === "aurora") {
    const bmp = cached(`ribbon:${t.id}:${w}x${h}`, () => ribbon(t, w, h));
    for (let k = 0; k < 2; k++) {
      const drift = Math.sin(time * TAU * 0.05 + k * 2.1);
      ctx.globalAlpha = 0.75 + 0.25 * Math.sin(time * TAU * 0.05 + k);
      ctx.drawImage(bmp, -w * 0.15 + drift * w * 0.08, h * (0.08 + 0.12 * k) + rise * 0.05);
    }
    ctx.globalAlpha = 1;
    ambientStars(ctx, t, rise, time, w, h, 0.4);
  }
}

function drawAmbientFront(ctx: CanvasRenderingContext2D, t: Theme, rise: number, time: number, w: number, h: number): void {
  const u = w / 100;
  const list = motes(t);
  switch (t.ambient) {
    case "fog": {
      const bmp = cached(`fog:${t.id}:${w}x${h}`, () => fogBand(t, w, h));
      for (let k = 0; k < 2; k++) {
        const offset = (time * 0.02 * w * (1 - 0.3 * k)) % w;
        const y = h * (0.58 + 0.12 * k) + rise * (0.12 + 0.08 * k);
        if (y > h) continue;
        ctx.drawImage(bmp, offset - w, y);
        ctx.drawImage(bmp, offset, y);
      }
      break;
    }
    case "bubbles": {
      const color = mix(t.text, t.accent, 0.5);
      const loop = h * 1.1;
      ctx.lineWidth = Math.max(1, u * 0.15);
      for (const m of list) {
        const y = h - ((m.y * loop + time * (0.02 + 0.03 * m.speed) * h) % loop) + h * 0.05;
        const x = m.x * w + Math.sin(time * TAU * 0.25 + m.phase) * u * 0.8;
        const r = u * (0.35 + 0.5 * m.size);
        ctx.strokeStyle = css(color, 70);
        ctx.beginPath();
        ctx.arc(x, y, r, 0, TAU);
        ctx.stroke();
        ctx.fillStyle = css(color, 60);
        ctx.beginPath();
        ctx.arc(x - r * 0.35, y - r * 0.35, r * 0.2, 0, TAU);
        ctx.fill();
      }
      break;
    }
    case "petals": {
      ctx.fillStyle = css(mix(t.accent2, SOFT_WHITE, 0.35), 190);
      const loop = h * 1.15;
      list.forEach((m, i) => {
        const y = ((m.y * loop + time * (0.03 + 0.03 * m.speed) * h) % loop) - h * 0.1;
        const x = m.x * w + Math.sin(time * TAU * (0.2 + 0.1 * m.size) + m.phase) * u * 3;
        const s = 0.7 + 0.6 * m.size;
        const spin = i % 2 === 0 ? 1 : -1;
        ctx.save();
        ctx.translate(x, y);
        ctx.rotate(((m.phase * 57 + spin * time * (20 + 20 * m.speed)) * Math.PI) / 180);
        ctx.beginPath();
        ctx.ellipse(0, 0, u * s, u * 0.55 * s, 0, 0, TAU);
        ctx.fill();
        ctx.restore();
      });
      break;
    }
    case "motes":
      for (const m of list) {
        const y = h - ((m.y * h + time * (0.01 + 0.01 * m.speed) * h) % h);
        const x = m.x * w + Math.sin(time * TAU * 0.15 + m.phase) * u * 2;
        ctx.fillStyle = css(t.textDim, 40 + 50 * m.size);
        ctx.beginPath();
        ctx.arc(x, y, u * (0.2 + 0.25 * m.size), 0, TAU);
        ctx.fill();
      }
      break;
    default:
      break;
  }
}

function ambientStars(ctx: CanvasRenderingContext2D, t: Theme, rise: number, time: number, w: number, h: number, band: number): void {
  const u = w / 100;
  for (const m of motes(t)) {
    const twinkle = 0.5 + 0.5 * Math.sin(time * TAU * 0.2 + m.phase);
    ctx.fillStyle = css(t.text, 70 + 60 * twinkle);
    ctx.beginPath();
    ctx.arc(m.x * w, m.y * h * band + rise * 0.05, u * (0.15 + 0.2 * m.size), 0, TAU);
    ctx.fill();
  }
}

/** A horizontally seamless strip of soft fog blobs. */
function fogBand(t: Theme, w: number, h: number): Layer {
  const bh = Math.max(1, Math.round(h * 0.14));
  const bmp = canvas(w, bh);
  const c = get2d(bmp);
  const color = mix(t.skyTop, SOFT_WHITE, 0.5);
  const rng = createRng(AMBIENT_INDEX.fog * 17 + 5);
  const cy = bh / 2;
  const r = bh / 2;
  for (let k = 0; k < 6; k++) {
    const cx = rng() * w;
    for (const dx of [-w, 0, w]) {
      const x = cx + dx;
      c.save();
      c.translate(x, cy);
      c.scale(3, 1);
      const g = c.createRadialGradient(0, 0, 0, 0, 0, r);
      g.addColorStop(0, css(color, 70));
      g.addColorStop(1, css(color, 0));
      c.fillStyle = g;
      c.beginPath();
      c.arc(0, 0, r, 0, TAU);
      c.fill();
      c.restore();
    }
  }
  return bmp;
}

/** One wavy aurora curtain, wider than the screen so its sideways drift never shows an edge. */
function ribbon(t: Theme, w: number, h: number): Layer {
  const bw = Math.round(w * 1.3);
  const bh = Math.round(h * 0.22);
  const bmp = canvas(bw, bh);
  const c = get2d(bmp);
  const steps = 40;
  c.beginPath();
  for (let i = 0; i <= steps; i++) {
    const x = (bw * i) / steps;
    const y = bh * (0.3 + 0.15 * Math.sin((i / steps) * TAU * 1.5));
    if (i === 0) c.moveTo(x, y);
    else c.lineTo(x, y);
  }
  for (let i = steps; i >= 0; i--) {
    c.lineTo((bw * i) / steps, bh * (0.65 + 0.15 * Math.sin((i / steps) * TAU * 1.5 + 0.6)));
  }
  c.closePath();
  const g = c.createLinearGradient(0, 0, 0, bh);
  g.addColorStop(0, css(t.accent, 0));
  g.addColorStop(0.45, css(t.accent, 35));
  g.addColorStop(1, css(t.accent2, 0));
  c.fillStyle = g;
  c.fill();
  return bmp;
}

// --- Skyline (tower mode) ---------------------------------------------------------------------

const BEACON = 0xff4060;
const CALM_WINDOW = 0xffe3a3;

/** Two layers of city silhouettes sitting on the horizon. */
export function drawSkyline(ctx: CanvasRenderingContext2D, t: Theme, horizon: number, w: number, h: number): void {
  const [far, near] = cached(`skyline:${t.id}:${w}x${h}`, () => [
    buildSkyline(t, w, Math.round(h * 0.2), 11, 0.55),
    buildSkyline(t, w, Math.round(h * 0.28), 23, 1),
  ]);
  if (horizon - near.height > h) return;
  ctx.drawImage(far, 0, horizon - far.height + 1);
  ctx.drawImage(near, 0, horizon - near.height + h * 0.012);
}

function buildSkyline(t: Theme, w: number, height: number, seed: number, bright: number): Layer {
  const bmp = canvas(w, height);
  const c = get2d(bmp);
  const rng = createRng(seed);
  const u = w / 100;
  const calm = isCalm(t);
  const body = calm ? mix(t.skyBottom, t.ground, 0.3 + 0.4 * bright) : hsv(t.bgHue, t.bgSat * 0.8, 0.05 + 0.06 * bright);
  c.lineWidth = u * 0.25;

  let x = -rng() * u * 3;
  while (x < w) {
    const bw = u * (5 + rng() * 9);
    const bh = height * (0.3 + rng() * 0.62);
    const top = height - bh;
    c.fillStyle = css(body);
    c.fillRect(x, top, bw, bh);

    if (!calm) {
      c.strokeStyle = css(t.accent, 130 * bright);
      hline(c, x, top, x + bw, top);
      c.strokeStyle = css(t.accent, 50 * bright);
      hline(c, x, top, x, height);
      hline(c, x + bw, top, x + bw, height);
    }

    if (!calm || !t.light) {
      for (let wy = top + u * 1.6; wy < height - u * 1.5; wy += u * 2.3) {
        for (let wx = x + u * 1.1; wx < x + bw - u * 1.6; wx += u * 1.7) {
          if (rng() < (calm ? 0.18 : 0.3)) {
            const a = calm ? (40 + Math.floor(rng() * 40)) * bright : (90 + Math.floor(rng() * 140)) * bright;
            c.fillStyle = css(calm ? CALM_WINDOW : t.accent2, a);
            c.fillRect(wx, wy, u * 0.75, u);
          }
        }
      }
    }

    if (!calm && rng() < 0.3 && top > u * 4) {
      c.strokeStyle = css(t.accent, 140 * bright);
      hline(c, x + bw / 2, top, x + bw / 2, top - u * 3);
      c.fillStyle = css(BEACON, 255 * bright);
      c.beginPath();
      c.arc(x + bw / 2, top - u * 3, u * 0.45, 0, TAU);
      c.fill();
    }
    x += bw + u * (0.2 + rng() * 1.4);
  }
  return bmp;
}
