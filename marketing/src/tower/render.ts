import { css, hsv, lighten, mix, shade, type Rgb } from "../lib/color.ts";
import { drawBackdrop, drawOverlay, drawSkyline } from "../game/backdrop.ts";
import * as neon from "../game/neon.ts";
import { isCalm, type Theme } from "../game/palette.ts";
import {
  BONUS_DROP, DANGER, H, HOOK_GAP, LIVES, W, shiftAt, slopeAt, towerDrift,
  type Geometry, type TowerFrame,
} from "./sim.ts";

/** Canvas port of TowerMode.render, TowerMode.drawHud and the playing screen's score and combo. */

const WARM_WINDOW = 0xffe3a3;
const GLASS_TINT = 0xf4f8fa;
const NEON_FLASH = 28;
const CALM_FLASH = 18;
const LOWER_TIME = 0.45;

export type TowerRenderOptions = {
  /** Draw the score, combo, lives, balance meter and gem counter. */
  hud: boolean;
  font: string;
  /** Seconds, for ambient motion. */
  time: number;
  gems: number;
};

type View = {
  g: Geometry;
  f: TowerFrame;
  t: Theme;
  n: neon.Neon;
  sc: number;
  calm: boolean;
  windowLit: { color: Rgb; alpha: number };
};

const sx = (v: View, x: number): number => v.g.w / 2 + (x - v.f.camX) * v.sc + v.f.shakeX;
const sy = (v: View, y: number): number => v.g.anchorY - (y - v.f.camY) * v.sc + v.f.shakeY;
const shift = (v: View, y: number): number => shiftAt(v.f.bend, v.f.shownTop, y);
const deg = (r: number): number => (r * 180) / Math.PI;

function rotateAbout(ctx: CanvasRenderingContext2D, degrees: number, x: number, y: number): void {
  ctx.translate(x, y);
  ctx.rotate((degrees * Math.PI) / 180);
  ctx.translate(-x, -y);
}

export function renderTower(
  ctx: CanvasRenderingContext2D, g: Geometry, f: TowerFrame, theme: Theme, options: TowerRenderOptions,
): void {
  const calm = isCalm(theme);
  const v: View = {
    g, f, t: theme, calm,
    n: neon.createNeon(ctx, theme, g.w, options.font),
    sc: g.scale * f.zoom,
    windowLit: theme.light
      ? { color: mix(theme.skyTop, GLASS_TINT, 0.35), alpha: 90 }
      : { color: WARM_WINDOW, alpha: 130 },
  };
  const { w, h } = g;
  const horizon = h * 0.72 + (f.camY - g.camMin) * v.sc * 0.5;
  drawBackdrop(ctx, theme, towerDrift(theme, f.score), horizon, options.time, w, h);
  drawSkyline(ctx, theme, horizon, w, h);
  drawStreet(v);

  const fh = H * v.sc;
  // Before it breaks apart, the toppling part turns about its hinge as one piece.
  const toppling = f.collapsing && !f.broken ? f.hinge + 1 : f.floors.length;
  ctx.save();
  f.floors.forEach((fl, i) => {
    if (i === toppling) rotateAbout(ctx, deg(f.collapseAngle), sx(v, f.hingeX + shift(v, f.hingeY)), sy(v, f.hingeY));
    const bottom = sy(v, fl.y);
    if (!f.collapsing && (bottom < -fh * 2 || bottom - fh > h + fh * 2)) return;
    const mid = fl.y + H / 2;
    const cx = sx(v, fl.x + fl.settle + shift(v, mid));
    ctx.save();
    rotateAbout(ctx, deg(Math.atan(slopeAt(f.bend, f.shownTop, mid))), cx, bottom - fh / 2);
    drawFloor(v, cx, bottom, fl.color, fl.windows, fl.lobby, 255, false);
    ctx.restore();
  });
  ctx.restore();

  for (const t of f.tumbles) {
    const px = sx(v, t.x);
    const py = sy(v, t.y);
    const alpha = t.fades ? Math.max(0, Math.min(255, 255 * (1 - t.age / 3))) : 255;
    ctx.save();
    rotateAbout(ctx, t.rotation, px, py);
    drawFloor(v, px, py + fh / 2, t.color, t.windows, false, alpha, false);
    ctx.restore();
  }

  if (f.hook === "falling" && !f.isOver) {
    drawFloor(v, sx(v, f.fx), sy(v, f.fy), f.nextColor, f.nextWindows, f.floors.length === 0, 255, true);
  }
  if (f.streakActive) {
    const top = f.floors.length * H;
    const y = top + (1 - f.streakFall) * BONUS_DROP;
    drawFloor(v, sx(v, f.bonusX + shift(v, top + H / 2)), sy(v, y), f.nextColor, f.nextWindows, false, 255, true);
  }

  drawCrane(v);
  drawParticles(v);

  if (f.damageFlash > 0) {
    ctx.fillStyle = css(calm ? theme.danger : neon.RED, (calm ? CALM_FLASH : NEON_FLASH) * f.damageFlash);
    ctx.fillRect(0, 0, w, h);
  }
  if (options.hud) drawHud(v, options.gems);
  drawOverlay(ctx, theme, w, h);
}

function drawStreet(v: View): void {
  const { ctx } = v.n;
  const { w, h, u } = v.g;
  const street = sy(v, 0);
  if (street > h + 10) return;
  const accent = v.t.accent;
  ctx.fillStyle = css(v.calm ? v.t.ground : hsv(v.t.bgHue, v.t.bgSat, 0.03));
  ctx.fillRect(0, street, w, h - street);
  neon.line(v.n, 0, street, w, street, accent, u * 0.4, v.calm ? 160 : 220);

  const dashY = street + Math.min(h - street, u * 14) * 0.5;
  let x = ((-v.f.camX * v.sc) % (u * 12)) - u * 12;
  while (x < w) {
    neon.line(v.n, x, dashY, x + u * 5, dashY, accent, u * 0.25, v.calm ? 70 : 90);
    x += u * 12;
  }
  const r = neon.rect(sx(v, -W * 0.65), street, sx(v, W * 0.65), sy(v, -0.4));
  neon.roundRect(v.n, r, u, v.t.accent2, u * 0.3, 200, 40);
}

function drawFloor(
  v: View, cx: number, bottom: number, color: Rgb, windows: number, lobby: boolean, alpha: number, moving: boolean,
): void {
  const { ctx } = v.n;
  const fw = W * v.sc;
  const fh = H * v.sc;
  const left0 = cx - fw / 2;
  const top0 = bottom - fh;

  const padX = fw * 0.1;
  const gapX = fw * 0.08;
  const padTop = fh * 0.2;
  const padBottom = fh * 0.12;
  const gapY = fh * 0.12;
  const ww = (fw - padX * 2 - gapX * 2) / 3;
  const wh = (fh - padTop - padBottom - gapY) / 2;
  const box = (c: string, l: number, t: number, r: number, b: number): void => {
    ctx.fillStyle = c;
    ctx.fillRect(l, t, r - l, b - t);
  };

  if (v.calm) {
    box(css(color, alpha), left0, top0, left0 + fw, bottom);
    for (let r = 0; r <= 1; r++) {
      for (let c = 0; c <= 2; c++) {
        const left = left0 + padX + c * (ww + gapX);
        const top = top0 + padTop + r * (wh + gapY);
        if (lobby && r === 1 && c === 1) {
          box(css(shade(color, 0.72), alpha * 0.9), left, top - wh * 0.3, left + ww, bottom);
          continue;
        }
        const lit = (windows & (1 << (r * 3 + c))) !== 0;
        box(
          lit ? css(v.windowLit.color, (v.windowLit.alpha * alpha) / 255) : css(shade(color, 0.86), alpha * 0.6),
          left, top, left + ww, top + wh,
        );
      }
    }
    box(css(lighten(color, moving ? 0.25 : 0.2), (alpha * (moving ? 140 : 120)) / 255), left0, top0, left0 + fw, top0 + Math.max(1, v.sc * 0.03));
    return;
  }

  box(css(shade(color, 0.18), alpha), left0, top0, left0 + fw, bottom);
  const bleed = fw * 0.015;
  for (let r = 0; r <= 1; r++) {
    for (let c = 0; c <= 2; c++) {
      const left = left0 + padX + c * (ww + gapX);
      const top = top0 + padTop + r * (wh + gapY);
      if (lobby && r === 1 && c === 1) {
        box(css(lighten(color, 0.3), alpha * 0.85), left, top - wh * 0.3, left + ww, bottom);
        continue;
      }
      const lit = (windows & (1 << (r * 3 + c))) !== 0;
      if (lit) {
        box(css(color, alpha * 0.35), left - bleed, top - bleed, left + ww + bleed, top + wh + bleed);
        box(css(lighten(color, 0.6), alpha), left, top, left + ww, top + wh);
      } else {
        box(css(shade(color, 0.4), alpha * 0.6), left, top, left + ww, top + wh);
      }
    }
  }
  neon.roundRect(v.n, neon.rect(left0, top0, left0 + fw, bottom), fw * 0.03, color, Math.max(2, v.sc * 0.09), alpha);
  const roof = top0 + fh * 0.09;
  neon.line(v.n, left0, roof, left0 + fw, roof, color, Math.max(1, v.sc * 0.04), alpha * 0.6);
}

function drawCrane(v: View): void {
  const { f, g, t, n } = v;
  const { w, u } = g;
  const accent = v.calm ? t.textDim : t.accent;
  const accent2 = v.calm ? t.textDim : t.accent2;
  const ropeColor = v.calm ? t.textDim : lighten(accent2, 0.5);
  const a = v.calm ? 0.8 : 1;
  const thin = v.calm ? 0.7 : 1;
  const top = g.pivotScreenY - u * 5;
  const bottom = g.pivotScreenY - u * 1.2;

  neon.line(n, 0, top, w, top, accent, u * 0.4 * thin, 210 * a);
  neon.line(n, 0, bottom, w, bottom, accent, u * 0.4 * thin, 210 * a);
  const step = u * 7;
  const offset = (((f.camX * v.sc) % step) + step) % step;
  for (let x = -offset; x < w + step; x += step) {
    neon.line(n, x, bottom, x + step / 2, top, accent, u * 0.22 * thin, 130 * a);
    neon.line(n, x + step / 2, top, x + step, bottom, accent, u * 0.22 * thin, 130 * a);
  }

  const px = w / 2 + f.shakeX;
  const trolley = neon.rect(px - u * 5, bottom - u * 0.5, px + u * 5, bottom + u * 2.5);
  neon.roundRect(n, trolley, u, accent2, u * 0.35 * thin, 255 * a, 90 * a);
  const ropeTop = trolley.bottom;

  if (f.isOver || f.hook === "falling" || f.hook === "empty") {
    const hookY = ropeTop + u * 4;
    neon.line(n, px, ropeTop, px, hookY, ropeColor, u * 0.22 * thin, 200 * a);
    neon.dot(n, px, hookY, u * 0.8, accent2, 255 * a);
    return;
  }

  const lower = Math.min(1, Math.max(0, f.hookTime / LOWER_TIME));
  const len = f.hook === "lowering" ? g.rope * (1 - (1 - lower) ** 3) : g.rope;
  const theta = f.amp * Math.sin(f.phase);
  const pivotY = f.camY + (g.anchorY - g.pivotScreenY) / (g.scale * f.zoom);
  const hx = f.camX + len * Math.sin(theta);
  const hy = pivotY - len * Math.cos(theta);
  const hsx = sx(v, hx);
  const hsy = sy(v, hy);
  neon.line(n, px, ropeTop, hsx, hsy, ropeColor, u * 0.22 * thin, 220 * a);

  const moduleTop = sy(v, hy - HOOK_GAP);
  const half = (W * v.sc) / 2;
  neon.line(n, hsx, hsy, hsx - half * 0.8, moduleTop, accent2, u * 0.15 * thin, 160 * a);
  neon.line(n, hsx, hsy, hsx + half * 0.8, moduleTop, accent2, u * 0.15 * thin, 160 * a);
  neon.dot(n, hsx, hsy, u * 0.8, accent2, 255 * a);
  drawFloor(v, hsx, sy(v, hy - HOOK_GAP - H), f.nextColor, f.nextWindows, f.floors.length === 0, 255, true);
}

function drawParticles(v: View): void {
  const { ctx } = v.n;
  for (const p of v.f.rings) {
    const t = p.age / 0.8;
    ctx.strokeStyle = css(p.color, (1 - t) * 120);
    ctx.lineWidth = p.radius * 0.06 * (1 - t) + 1;
    ctx.beginPath();
    ctx.arc(p.x, p.y, p.radius * (0.2 + 0.8 * t), 0, Math.PI * 2);
    ctx.stroke();
  }
  for (const p of v.f.flares) {
    const a = Math.max(0, Math.min(255, (255 * p.life) / p.maxLife));
    ctx.fillStyle = css(p.color, v.calm ? a / 10 : a / 4);
    ctx.beginPath();
    ctx.arc(p.x, p.y, p.size * 2.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = css(v.calm ? p.color : lighten(p.color, 0.6), a);
    ctx.beginPath();
    ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
    ctx.fill();
  }
}

function drawHud(v: View, gems: number): void {
  const { f, g, t, n } = v;
  const { w, h, u } = g;
  neon.glowText(n, String(f.score), w / 2, h * 0.11, u * 15 * (1 + 0.25 * f.scorePop), t.text, t.accent);
  if (f.comboTime > 0) {
    const alpha = 255 * Math.min(1, f.comboTime / 0.4);
    const pop = 1 + (0.35 * Math.max(0, f.comboTime - 0.8)) / 0.2;
    neon.glowText(n, f.comboLabel, w / 2, h * 0.215, u * 6 * pop, t.text, t.accent2, alpha);
  }

  const heartColor = v.calm ? t.danger : neon.RED;
  const lostColor = v.calm ? t.textDim : 0x888888;
  for (let i = 0; i < LIVES; i++) {
    const alive = i < f.lives;
    neon.heart(n, u * 7 + i * u * 8, h * 0.06 - u * 1.2, u * 2.6, alive ? heartColor : lostColor, alive);
  }

  const y = h * 0.155;
  const cx = w / 2;
  const half = u * 24;
  const warn = v.calm ? t.danger : neon.AMBER;
  neon.line(n, cx - half, y, cx + half, y, t.accent, u * 0.3, 110);
  neon.line(n, cx - half, y, cx - half * 0.72, y, warn, u * 0.4, 170);
  neon.line(n, cx + half * 0.72, y, cx + half, y, warn, u * 0.4, 170);
  neon.line(n, cx, y - u * 1.4, cx, y + u * 1.4, t.text, u * 0.25, 160);

  const b = Math.max(-1.15, Math.min(1.15, f.shownBalance + f.sway * 0.25));
  const danger = Math.min(1, Math.abs(b));
  const color = v.calm ? mix(t.accent, t.danger, danger) : hsv(140 - 100 * danger, 0.75, 1);
  const pulse = danger > DANGER ? 0.75 + 0.25 * Math.sin(f.clock * 6) : 1;
  neon.dot(n, cx + b * half, y, u * 1.5, color, 255 * pulse);
  if (danger > DANGER) {
    neon.glowText(n, b > 0 ? "LEANING RIGHT" : "LEANING LEFT", cx, y + u * 5, u * 3.2, t.text, warn, 255 * pulse);
  } else {
    neon.text(n, "BALANCE", cx, y + u * 5, u * 3.2, t.text, 160);
  }

  const gy = Math.max(h * 0.06, u * 4.5);
  neon.gem(n, w - u * 7, gy - u * 1.6, u * 2.6);
  neon.glowText(n, gems.toLocaleString("en-US"), w - u * 11.5, gy, u * 5, t.text, neon.GEM, 255, "right");
}
