import { AbsoluteFill, useCurrentFrame, useVideoConfig } from "remotion";

export type Tap = { frame: number; x: number; y: number };

const BEFORE = 0.06;
const AFTER = 0.3;

/**
 * A touch dot like Android's "Show taps" option where the finger lands, so viewers see the game is
 * played with one tap.
 */
export const TapDots: React.FC<{ taps: Tap[] }> = ({ taps }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const visible = taps.filter((t) => frame >= t.frame - BEFORE * fps && frame <= t.frame + AFTER * fps);
  return (
    <AbsoluteFill style={{ pointerEvents: "none" }}>
      {visible.map((tap) => {
        const t = (frame - tap.frame) / fps;
        const dot = t < 0 ? 0.6 : Math.max(0, 1 - t / AFTER);
        const ring = Math.max(0, t) / AFTER;
        return (
          <div key={tap.frame}>
            <div style={circle(tap.x, tap.y, 52, `rgba(255,255,255,${(0.55 * dot).toFixed(3)})`, "none")} />
            {t >= 0 && (
              <div
                style={circle(
                  tap.x, tap.y, 52 + ring * 90, "transparent",
                  `${6 * (1 - ring)}px solid rgba(255,255,255,${(0.8 * (1 - ring)).toFixed(3)})`,
                )}
              />
            )}
          </div>
        );
      })}
    </AbsoluteFill>
  );
};

/** Gameplay taps: wherever the thumb rests in the lower middle of the screen. */
export function gameplayTaps(frames: number[], startAt = 0, y = 1500): Tap[] {
  return frames.map((f) => ({ frame: f - startAt, x: 540 + ((f * 37) % 90) - 45, y }));
}

function circle(x: number, y: number, r: number, background: string, border: string): React.CSSProperties {
  return {
    position: "absolute", left: x - r, top: y - r, width: r * 2, height: r * 2,
    borderRadius: "50%", background, border, boxSizing: "border-box",
  };
}
