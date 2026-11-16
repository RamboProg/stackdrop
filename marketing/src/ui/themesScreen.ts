import { css, type Rgb } from "../lib/color.ts";
import * as neon from "../game/neon.ts";
import { blockColor, CALM_THEMES, NEON_THEMES, type Family, type Theme } from "../game/palette.ts";

/**
 * Port of ui/screens/ThemesScreen.kt: CALM | NEON tabs over the theme list, drawn over the live scene
 * of the theme on screen. Every theme shows as owned, so a tap applies it like the real screen does.
 */

const DARK = 0x14121f;
const VEIL = 120;

export type ThemesLayout = {
  rowH: number;
  gap: number;
  listTop: number;
  listBottom: number;
  tabs: [neon.Rect, neon.Rect];
  back: neon.Rect;
};

export function themesLayout(w: number, h: number): ThemesLayout {
  const u = w / 100;
  const tabTop = h * 0.135;
  const bottom = h * 0.88;
  return {
    rowH: Math.min(h * 0.082, u * 15),
    gap: u * 1.6,
    listTop: h * 0.2,
    listBottom: h * 0.84,
    tabs: [neon.rect(u * 20, tabTop, u * 49, tabTop + u * 7.5), neon.rect(u * 51, tabTop, u * 80, tabTop + u * 7.5)],
    back: neon.rect(u * 30, bottom, u * 70, bottom + u * 11),
  };
}

export const themesOf = (family: Family): Theme[] => (family === "calm" ? CALM_THEMES : NEON_THEMES);

/** Center of a theme's row, for placing a tap. */
export function rowCenter(l: ThemesLayout, family: Family, id: string, w: number): { x: number; y: number } {
  const i = themesOf(family).findIndex((t) => t.id === id);
  if (i < 0) throw new Error(`Theme ${id} is not in the ${family} tab`);
  return { x: w * 0.62, y: l.listTop + i * (l.rowH + l.gap) + l.rowH / 2 };
}

export type ThemesState = {
  /** The theme on screen; it styles the screen itself. */
  theme: Theme;
  family: Family;
  /** The selected theme's id. */
  inUse: string;
  gems: number;
  font: string;
  w: number;
  h: number;
  /** Fades the whole screen in and out, 0..1. */
  alpha: number;
};

export function drawThemesScreen(ctx: CanvasRenderingContext2D, s: ThemesState): void {
  const { theme, w, h } = s;
  const u = w / 100;
  const n = neon.createNeon(ctx, theme, w, s.font);
  const l = themesLayout(w, h);
  ctx.save();
  ctx.globalAlpha = s.alpha;

  ctx.fillStyle = css(theme.panel, VEIL);
  ctx.fillRect(0, 0, w, h);
  neon.glowText(n, "THEMES", w / 2, h * 0.11, u * 10, theme.text, theme.accent);
  const gy = Math.max(h * 0.06, u * 4.5);
  neon.gem(n, w - u * 7, gy - u * 1.6, u * 2.6);
  neon.glowText(n, s.gems.toLocaleString("en-US"), w - u * 11.5, gy, u * 5, theme.text, neon.GEM, 255, "right");

  pill(n, l.tabs[0], "CALM", theme.accent, s.family === "calm", s.family !== "calm");
  pill(n, l.tabs[1], "NEON", theme.accent, s.family === "neon", s.family !== "neon");

  ctx.save();
  ctx.beginPath();
  ctx.rect(0, l.listTop - u * 1.5, w, l.listBottom - l.listTop + u * 3);
  ctx.clip();
  themesOf(s.family).forEach((t, i) => {
    const top = l.listTop + i * (l.rowH + l.gap);
    if (top > l.listBottom + u) return;
    drawRow(n, s, t, neon.rect(u * 7, top, u * 93, top + l.rowH));
  });
  ctx.restore();

  pill(n, l.back, "BACK", theme.accent, false, false);
  ctx.restore();
}

function drawRow(n: neon.Neon, s: ThemesState, t: Theme, row: neon.Rect): void {
  const { u } = n;
  const current = s.theme;
  const shown = t.id === current.id;
  const radius = u * 3;
  const cy = (row.top + row.bottom) / 2;

  neon.panel(n, row, radius, 140);
  neon.roundRect(n, row, radius, t.accent, u * 0.35, shown ? 255 : 90, shown ? 30 : 0);

  const size = (row.bottom - row.top) * 0.28;
  let x = row.left + u * 3.5;
  for (let k = 0; k < 5; k++) {
    neon.roundRect(n, neon.rect(x, cy - size / 2, x + size, cy + size / 2), u * 0.8, blockColor(t, k * 3), u * 0.25, 230, 150);
    x += size + u;
  }
  const nameX = x + u * 2;
  neon.glowText(n, t.name, nameX, cy + u * 0.2, u * 4.8, current.text, t.accent, 255, "left");
  neon.text(n, t.mood, nameX, cy + u * 4.4, u * 3, current.textDim, 230, "left");

  const right = row.right - u * 4;
  if (t.premiumOnly) chip(n, "PREMIUM", right, cy, 0xe9c46a);
  else if (t.id === s.inUse) neon.text(n, "IN USE", right, cy + u * 1.4, u * 3.6, current.text, 200, "right");
  else neon.text(n, "OWNED", right, cy + u * 1.4, u * 3.6, current.text, 170, "right");
}

function pill(n: neon.Neon, r: neon.Rect, title: string, color: Rgb, highlight: boolean, dim: boolean): void {
  const { u } = n;
  const radius = (r.bottom - r.top) / 2;
  neon.panel(n, r, radius, 160);
  let size = u * 4.4;
  const room = r.right - r.left - u * 4;
  const tw = neon.measure(n, title, size);
  if (tw > room) size *= room / tw;
  const cx = (r.left + r.right) / 2;
  const baseline = (r.top + r.bottom) / 2 + size * 0.36;
  if (highlight) {
    neon.roundRect(n, r, radius, color, u * 0.45, 255, 210);
    neon.text(n, title, cx, baseline, size, DARK);
  } else {
    neon.roundRect(n, r, radius, color, u * 0.4, dim ? 110 : 230);
    neon.glowText(n, title, cx, baseline, size, n.text, color, dim ? 150 : 255);
  }
}

/** Small capsule label right-aligned at [x]. */
function chip(n: neon.Neon, label: string, x: number, cy: number, color: Rgb): void {
  const { u } = n;
  const size = u * 2.6;
  const tw = neon.measure(n, label, size);
  const left = x - tw - u * 3.6;
  const r = neon.rect(left, cy - u * 2.3, left + tw + u * 3.6, cy + u * 2.3);
  neon.roundRect(n, r, (r.bottom - r.top) / 2, color, u * 0.25, 220, 200);
  neon.text(n, label, (r.left + r.right) / 2, cy + size * 0.36, size, DARK);
}
