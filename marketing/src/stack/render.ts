import { css, lighten, shade, type Rgb } from "../lib/color.ts";
import { drawBackdrop, drawOverlay } from "../game/backdrop.ts";
import type { Theme } from "../game/palette.ts";
import { SLAB_H, type Block, type FrameState, type SimResult } from "./sim.ts";

/** Canvas port of IsoRenderer, Backdrop (synthwave scenery), Particles and the playing HUD. */

const COS30 = Math.cos(Math.PI / 6);
const SIN30 = 0.5;
const STARS = 90;

type Iso = { cx: number; cy: number; camY: number; scale: number };

const sx = (iso: Iso, x: number, z: number): number => iso.cx + (x - z) * COS30 * iso.scale;
const sy = (iso: Iso, x: number, y: number, z: number): number => iso.cy + ((x + z) * SIN30 - (y - iso.camY)) * iso.scale;

export type RenderOptions = {
  /** Draw the score and combo label like the game's playing screen. */
  hud: boolean;
  /** Font for the HUD text. */
  font: string;
};

export function renderFrame(
  ctx: CanvasRenderingContext2D, sim: SimResult, f: FrameState, theme: Theme,
  width: number, height: number, videoTime: number, options: RenderOptions,
): void {
  const rise = (f.camY - SLAB_H) * sim.baseScale * f.zoom;
  const horizon = height * 0.74 + rise * 0.45;
  drawBackdrop(ctx, theme, f.drift, horizon, videoTime, width, height);

  const iso: Iso = {
    cx: width / 2 + f.shakeX,
    cy: height * 0.45 + f.shakeY,
    camY: f.camY,
    scale: sim.baseScale * f.zoom,
  };
  const top = sim.tower[f.towerCount - 1];
  const depthRef = top.x + top.w / 2 + top.z + top.d / 2;
  const depth = (b: Block): number => b.x + b.w / 2 + b.z + b.d / 2;

  for (const d of f.debris) if (depth(d.block) < depthRef) drawDebris(ctx, iso, d.block, d.rotation, d.alpha);
  for (let i = 0; i < f.towerCount; i++) {
    const b = sim.tower[i];
    if (isVisible(iso, b, height)) drawBlock(ctx, iso, b, 255);
  }
  if (f.moving) drawBlock(ctx, iso, f.moving, 255);
  if (f.bonus) drawBlock(ctx, iso, f.bonus, 255);
  drawParticles(ctx, iso, f);
  for (const d of f.debris) if (depth(d.block) >= depthRef) drawDebris(ctx, iso, d.block, d.rotation, d.alpha);

  if (options.hud) drawHud(ctx, f, theme, width, height, options.font);
  drawOverlay(ctx, theme, width, height);
}

function isVisible(iso: Iso, b: Block, height: number): boolean {
  const topY = sy(iso, b.x, b.y + b.h, b.z);
  const bottomY = sy(iso, b.x + b.w, b.y, b.z + b.d);
  return topY < height && bottomY > 0;
}

function quad(ctx: CanvasRenderingContext2D, iso: Iso, pts: [number, number, number][]): void {
  ctx.beginPath();
  pts.forEach(([x, y, z], i) => (i === 0 ? ctx.moveTo(sx(iso, x, z), sy(iso, x, y, z)) : ctx.lineTo(sx(iso, x, z), sy(iso, x, y, z))));
  ctx.closePath();
}

function topRect(ctx: CanvasRenderingContext2D, iso: Iso, x: number, z: number, w: number, d: number, y: number): void {
  quad(ctx, iso, [[x, y, z], [x + w, y, z], [x + w, y, z + d], [x, y, z + d]]);
}

function line(ctx: CanvasRenderingContext2D, x0: number, y0: number, x1: number, y1: number): void {
  ctx.beginPath();
  ctx.moveTo(x0, y0);
  ctx.lineTo(x1, y1);
  ctx.stroke();
}

/** Two strokes: a wide faint halo in [color] and a crisp core, like IsoRenderer.glow. */
function glow(ctx: CanvasRenderingContext2D, color: Rgb, core: Rgb, width: number, alpha: number): void {
  ctx.strokeStyle = css(color, alpha * 0.2);
  ctx.lineWidth = width * 3.5;
  ctx.stroke();
  ctx.strokeStyle = css(core, alpha);
  ctx.lineWidth = width;
  ctx.stroke();
}

function drawBlock(ctx: CanvasRenderingContext2D, iso: Iso, b: Block, alpha: number): void {
  const x0 = b.x, z0 = b.z, y0 = b.y;
  const x1 = b.x + b.w, z1 = b.z + b.d, y1 = b.y + b.h;
  ctx.lineJoin = "round";
  ctx.lineCap = "round";

  const face = (color: Rgb, pts: [number, number, number][]): void => {
    quad(ctx, iso, pts);
    ctx.fillStyle = css(color, alpha);
    ctx.fill();
  };
  face(shade(b.color, 0.5), [[x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1]]);
  face(shade(b.color, 0.34), [[x1, y1, z0], [x1, y1, z1], [x1, y0, z1], [x1, y0, z0]]);
  face(shade(b.color, 0.22), [[x0, y1, z1], [x1, y1, z1], [x1, y0, z1], [x0, y0, z1]]);

  const e = Math.max(1.5, iso.scale * 0.08);
  const core = lighten(b.color, 0.45);
  topRect(ctx, iso, x0, z0, b.w, b.d, y1);
  glow(ctx, b.color, core, e, alpha);

  ctx.strokeStyle = css(core, alpha * 0.85);
  ctx.lineWidth = e * 0.8;
  line(ctx, sx(iso, x1, z1), sy(iso, x1, y1, z1), sx(iso, x1, z1), sy(iso, x1, y0, z1));

  ctx.strokeStyle = css(b.color, alpha * 0.5);
  ctx.lineWidth = e * 0.6;
  line(ctx, sx(iso, x0, z1), sy(iso, x0, y1, z1), sx(iso, x0, z1), sy(iso, x0, y0, z1));
  line(ctx, sx(iso, x1, z0), sy(iso, x1, y1, z0), sx(iso, x1, z0), sy(iso, x1, y0, z0));
  line(ctx, sx(iso, x0, z1), sy(iso, x0, y0, z1), sx(iso, x1, z1), sy(iso, x1, y0, z1));
  line(ctx, sx(iso, x1, z1), sy(iso, x1, y0, z1), sx(iso, x1, z0), sy(iso, x1, y0, z0));
}

function drawDebris(ctx: CanvasRenderingContext2D, iso: Iso, b: Block, rotation: number, alpha: number): void {
  const cx = b.x + b.w / 2;
  const cz = b.z + b.d / 2;
  const px = sx(iso, cx, cz);
  const py = sy(iso, cx, b.y + b.h / 2, cz);
  ctx.save();
  ctx.translate(px, py);
  ctx.rotate((rotation * Math.PI) / 180);
  ctx.translate(-px, -py);
  drawBlock(ctx, iso, b, alpha);
  ctx.restore();
}

function drawParticles(ctx: CanvasRenderingContext2D, iso: Iso, f: FrameState): void {
  for (const p of f.ripples) {
    if (p.age < 0) continue;
    const t = p.age / 0.6;
    const e = 0.3 + t * 2.8;
    const width = iso.scale * 0.18 * (1 - t) + 1;
    topRect(ctx, iso, p.x - e, p.z - e, p.w + e * 2, p.d + e * 2, p.y);
    glow(ctx, p.color, lighten(p.color, 0.5), width, (1 - t) * 240);
  }
  for (const p of f.sparks) {
    const x = sx(iso, p.x, p.z);
    const y = sy(iso, p.x, p.y, p.z);
    const r = p.size * iso.scale;
    ctx.fillStyle = css(p.color, p.alpha / 4);
    ctx.beginPath();
    ctx.arc(x, y, r * 2.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = css(lighten(p.color, 0.6), p.alpha);
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fill();
  }
}

// --- HUD ------------------------------------------------------------------------------------

function glowText(
  ctx: CanvasRenderingContext2D, s: string, x: number, y: number, size: number,
  color: string, glowColor: Rgb, alpha: number, font: string,
): void {
  ctx.font = `700 ${size}px ${font}`;
  ctx.textAlign = "center";
  ctx.textBaseline = "alphabetic";
  ctx.globalAlpha = alpha / 255;
  ctx.shadowColor = css(glowColor, alpha);
  ctx.shadowBlur = size * 0.3;
  ctx.fillStyle = css(glowColor, alpha);
  ctx.fillText(s, x, y);
  ctx.shadowBlur = size * 0.08;
  ctx.fillStyle = color;
  ctx.fillText(s, x, y);
  ctx.shadowBlur = 0;
  ctx.globalAlpha = 1;
}

function drawHud(ctx: CanvasRenderingContext2D, f: FrameState, theme: Theme, w: number, h: number, font: string): void {
  const u = w / 100;
  if (!f.isOver) {
    glowText(ctx, String(f.score), w / 2, h * 0.11, u * 15 * (1 + 0.25 * f.scorePop), "#ffffff", theme.accent, 255, font);
  }
  if (f.comboTime > 0) {
    const alpha = 255 * Math.min(1, f.comboTime / 0.4);
    const pop = 1 + (0.35 * Math.max(0, f.comboTime - 0.8)) / 0.2;
    glowText(ctx, f.comboLabel, w / 2, h * 0.215, u * 6 * pop, "#ffffff", theme.accent2, alpha, font);
  }
}
