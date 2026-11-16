/**
 * The GrabStacks "G" mark as the studio splash draws it once settled (StudioSplash.kt): five flat
 * blocks on the 108-unit icon grid, each with a thin iso-style shade under and to the right.
 * Plain data and strings so both the Remotion stills and the SVG export script can use it.
 */

export const NAVY = "#1E2140";
export const CREAM = "#F6EFE8";
export const MUTED = "#C9BFC8";

type Slab = { top: string; right: string; left: string };
const CYAN: Slab = { top: "#7FD3F5", right: "#4FA8CC", left: "#3B7F9C" };
const VIOLET: Slab = { top: "#A99BF7", right: "#7C6CD6", left: "#5E51A3" };
export const PINK: Slab = { top: "#F7A0CF", right: "#D46BA6", left: "#A1507E" };

/** Top bar, left column, bottom bar, right stub, and the tab the claw places (drawn in this order). */
const BLOCKS: { x: number; y: number; w: number; h: number; slab: Slab }[] = [
  { x: 0, y: 0, w: 48, h: 14, slab: PINK },
  { x: 0, y: 14, w: 14, h: 28, slab: VIOLET },
  { x: 0, y: 42, w: 48, h: 14, slab: CYAN },
  { x: 34, y: 28, w: 14, h: 14, slab: VIOLET },
  { x: 22, y: 28, w: 12, h: 9, slab: PINK },
];
const ORIGIN = { x: 30, y: 26 };
const DEPTH = 2.5;

/** The mark's bounds on the icon grid, shade included. */
export const MARK_BOX = { x: 30, y: 26, w: 48 + DEPTH, h: 56 + DEPTH };

export type Shape = { points: string; fill: string };

const poly = (fill: string, ...xy: number[]): Shape => {
  const pairs: string[] = [];
  for (let i = 0; i < xy.length; i += 2) pairs.push(`${xy[i]},${xy[i + 1]}`);
  return { points: pairs.join(" "), fill };
};

/** Every face of the mark, back to front, in icon-grid units. */
export function markShapes(): Shape[] {
  return BLOCKS.flatMap(({ x: bx, y: by, w, h, slab }) => {
    const x = ORIGIN.x + bx;
    const y = ORIGIN.y + by;
    const d = DEPTH;
    return [
      poly(slab.left, x, y + h, x + d, y + h + d, x + w + d, y + h + d, x + w, y + h),
      poly(slab.right, x + w, y, x + w + d, y + d, x + w + d, y + h + d, x + w, y + h),
      poly(slab.top, x, y, x + w, y, x + w, y + h, x, y + h),
    ];
  });
}

/**
 * A standalone SVG of the mark. [fill] is the share of the square the mark's height takes, so a
 * circular profile-picture crop still shows all of it.
 */
export function markSvg(size: number, background: string | null, fill = 0.6): string {
  const side = MARK_BOX.h / fill;
  const vx = MARK_BOX.x + MARK_BOX.w / 2 - side / 2;
  const vy = MARK_BOX.y + MARK_BOX.h / 2 - side / 2;
  const bg = background === null ? "" : `<rect x="${vx}" y="${vy}" width="${side}" height="${side}" fill="${background}"/>`;
  const shapes = markShapes().map((s) => `<polygon points="${s.points}" fill="${s.fill}"/>`).join("");
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="${vx} ${vy} ${side} ${side}">${bg}${shapes}</svg>\n`;
}
