/**
 * Vertical 9:16, the format TikTok, Reels, Shorts and story placements share. 60 fps: at 30 the
 * swinging crane and fast slabs move far enough between frames to look jittery.
 */
export const FORMAT = { width: 1080, height: 1920, fps: 60 } as const;

/** Frames for a duration in seconds at the ad frame rate. */
export const sec = (s: number): number => Math.round(s * FORMAT.fps);

/** "#rrggbb" for a 0xRRGGBB color, for CSS that appends its own alpha digits. */
export const hex = (c: number): string => `#${c.toString(16).padStart(6, "0")}`;
