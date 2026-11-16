import { css, lighten, type Rgb } from "../lib/color.ts";
import type { Theme } from "./palette.ts";

/**
 * Port of ui/Neon.kt: glow strokes, panels and text. With a calm theme (glow < 0.5) strokes become one
 * soft pass plus a crisp line, and panels become frosted glass.
 */
export type Neon = {
  ctx: CanvasRenderingContext2D;
  u: number;
  glow: number;
  text: Rgb;
  panel: Rgb;
  frosted: boolean;
  font: string;
};

export const GEM = 0x4fe3ff;
export const GOLD = 0xffd54f;
export const RED = 0xff3d6e;
export const AMBER = 0xffb347;
const CYAN = 0x00f0ff;
const MAGENTA = 0xff2bd6;

export function createNeon(ctx: CanvasRenderingContext2D, theme: Theme, width: number, font: string): Neon {
  return { ctx, u: width / 100, glow: theme.glow, text: theme.text, panel: theme.panel, frosted: theme.family === "calm", font };
}

const soft = (n: Neon): boolean => n.glow < 0.5;

/** Strokes the current path once per glow pass. */
function strokePasses(n: Neon, color: Rgb, width: number, alpha: number): void {
  const { ctx } = n;
  ctx.lineCap = "round";
  ctx.lineJoin = "round";
  const pass = (c: Rgb, w: number, a: number): void => {
    ctx.strokeStyle = css(c, a);
    ctx.lineWidth = w;
    ctx.stroke();
  };
  if (soft(n)) {
    pass(color, width * 2, alpha * 0.4 * n.glow);
    pass(color, width, alpha);
    return;
  }
  pass(color, width * 4.5, alpha * 0.07);
  pass(color, width * 2.2, alpha * 0.22);
  pass(color, width, alpha);
  pass(lighten(color, 0.65), width * 0.4, alpha);
}

export function line(n: Neon, x0: number, y0: number, x1: number, y1: number, color: Rgb, width: number, alpha = 255): void {
  n.ctx.beginPath();
  n.ctx.moveTo(x0, y0);
  n.ctx.lineTo(x1, y1);
  strokePasses(n, color, width, alpha);
}

/** Strokes whatever path [build] traces. */
export function path(n: Neon, build: (ctx: CanvasRenderingContext2D) => void, color: Rgb, width: number, alpha = 255): void {
  n.ctx.beginPath();
  build(n.ctx);
  strokePasses(n, color, width, alpha);
}

export type Rect = { left: number; top: number; right: number; bottom: number };

export const rect = (left: number, top: number, right: number, bottom: number): Rect => ({ left, top, right, bottom });

function cornerRadius(n: Neon, r: Rect, radius: number): number {
  return n.frosted ? Math.min(radius * 1.3, Math.min(r.right - r.left, r.bottom - r.top) / 2) : radius;
}

function roundRectPath(ctx: CanvasRenderingContext2D, r: Rect, radius: number): void {
  ctx.beginPath();
  ctx.roundRect(r.left, r.top, r.right - r.left, r.bottom - r.top, Math.max(0, radius));
}

export function roundRect(n: Neon, r: Rect, radius: number, color: Rgb, width: number, alpha = 255, fillAlpha = 0): void {
  const rr = cornerRadius(n, r, radius);
  if (fillAlpha > 0) {
    roundRectPath(n.ctx, r, rr);
    n.ctx.fillStyle = css(color, fillAlpha);
    n.ctx.fill();
  }
  roundRectPath(n.ctx, r, rr);
  strokePasses(n, color, width, alpha);
}

function circle(ctx: CanvasRenderingContext2D, x: number, y: number, r: number, fill: string): void {
  ctx.beginPath();
  ctx.arc(x, y, Math.max(0, r), 0, Math.PI * 2);
  ctx.fillStyle = fill;
  ctx.fill();
}

export function dot(n: Neon, cx: number, cy: number, r: number, color: Rgb, alpha = 255): void {
  if (soft(n)) {
    circle(n.ctx, cx, cy, r * 1.8, css(color, alpha * 0.15));
    circle(n.ctx, cx, cy, r, css(color, alpha));
    return;
  }
  circle(n.ctx, cx, cy, r * 2.4, css(color, alpha / 5));
  circle(n.ctx, cx, cy, r * 1.5, css(color, alpha / 2));
  circle(n.ctx, cx, cy, r, css(lighten(color, 0.6), alpha));
}

/** Dark glass for neon, frosted glass for calm. */
export function panel(n: Neon, r: Rect, radius: number, alpha: number): void {
  const { ctx } = n;
  if (!n.frosted) {
    roundRectPath(ctx, r, radius);
    ctx.fillStyle = css(0x000000, alpha);
    ctx.fill();
    return;
  }
  const rr = cornerRadius(n, r, radius);
  roundRectPath(ctx, r, rr);
  ctx.fillStyle = css(n.panel, Math.min(alpha, 170));
  ctx.fill();
  ctx.strokeStyle = css(n.text, Math.min(60, (60 * alpha) / 170));
  ctx.lineWidth = Math.max(1, n.u * 0.1);
  ctx.stroke();
}

export function gem(n: Neon, cx: number, cy: number, r: number, alpha = 255): void {
  const shape = (ctx: CanvasRenderingContext2D): void => {
    ctx.moveTo(cx, cy - r);
    ctx.lineTo(cx + r * 0.85, cy - r * 0.2);
    ctx.lineTo(cx, cy + r);
    ctx.lineTo(cx - r * 0.85, cy - r * 0.2);
    ctx.closePath();
  };
  n.ctx.beginPath();
  shape(n.ctx);
  n.ctx.fillStyle = css(GEM, alpha * 0.35);
  n.ctx.fill();
  path(n, shape, GEM, r * 0.16, alpha);
}

export function heart(n: Neon, cx: number, cy: number, r: number, color: Rgb, filled: boolean, alpha = 255): void {
  const shape = (ctx: CanvasRenderingContext2D): void => {
    ctx.moveTo(cx, cy + r * 0.9);
    ctx.bezierCurveTo(cx - r * 1.6, cy - r * 0.2, cx - r * 0.6, cy - r * 1.3, cx, cy - r * 0.45);
    ctx.bezierCurveTo(cx + r * 0.6, cy - r * 1.3, cx + r * 1.6, cy - r * 0.2, cx, cy + r * 0.9);
    ctx.closePath();
  };
  if (filled) {
    n.ctx.beginPath();
    shape(n.ctx);
    n.ctx.fillStyle = css(color, alpha * 0.55);
    n.ctx.fill();
  }
  path(n, shape, color, r * 0.16, filled ? alpha : alpha / 2);
}

export type Align = "left" | "center" | "right";

export function text(n: Neon, s: string, x: number, y: number, size: number, color: Rgb, alpha = 255, align: Align = "center"): void {
  const { ctx } = n;
  ctx.font = `600 ${size}px ${n.font}`;
  ctx.textAlign = align;
  ctx.textBaseline = "alphabetic";
  ctx.fillStyle = css(color, alpha);
  ctx.fillText(s, x, y);
}

export function glowText(
  n: Neon, s: string, x: number, y: number, size: number, color: Rgb, glowColor: Rgb = color,
  alpha = 255, align: Align = "center",
): void {
  const { ctx } = n;
  const a = Math.max(0, Math.min(255, alpha));
  const ga = a * n.glow;
  ctx.font = `600 ${size}px ${n.font}`;
  ctx.textAlign = align;
  ctx.textBaseline = "alphabetic";
  ctx.shadowColor = css(glowColor, ga);
  ctx.shadowBlur = size * 0.3 * n.glow;
  ctx.fillStyle = css(glowColor, a);
  ctx.fillText(s, x, y);
  ctx.shadowBlur = size * 0.08 * n.glow;
  ctx.fillStyle = css(color, a);
  ctx.fillText(s, x, y);
  ctx.shadowBlur = 0;
  ctx.shadowColor = "transparent";
}

/** Title text with a cyan/magenta split that occasionally glitches wider; calm themes get a soft glow. */
export function chroma(n: Neon, s: string, x: number, y: number, size: number, time: number, glowColor: Rgb): void {
  if (soft(n)) {
    glowText(n, s, x, y, size, n.text, glowColor);
    return;
  }
  const spike = Math.sin(time * 1.9) * Math.sin(time * 3.7 + 1) > 0.8;
  const off = spike ? n.u * 1.4 : n.u * 0.35;
  const jy = spike ? n.u * 0.3 : 0;
  text(n, s, x - off, y - jy, size, CYAN, 150);
  text(n, s, x + off, y + jy, size, MAGENTA, 150);
  glowText(n, s, x, y, size, n.text, glowColor);
}

export function measure(n: Neon, s: string, size: number): number {
  n.ctx.font = `600 ${size}px ${n.font}`;
  return n.ctx.measureText(s).width;
}
