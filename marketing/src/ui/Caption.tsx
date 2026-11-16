import { AbsoluteFill, interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { FONT } from "./fonts.ts";

export type Word = string | { text: string; color: string };

type Props = {
  /** Each inner array is one line; colored words stand out from the white ones. */
  lines: Word[][];
  /** Vertical center of the block in px. */
  y: number;
  size?: number;
  /** Degrees of the idle wobble; 0 keeps it still. */
  wobble?: number;
};

/**
 * Bold outlined caption in the style social feeds use. It is already readable on its first frame
 * (it settles from slightly too big rather than growing from nothing) so the thumbnail shows it.
 * Place it in a Sequence; it leaves during the Sequence's last frames.
 */
export const Caption: React.FC<Props> = ({ lines, y, size = 110, wobble = 1.5 }) => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames } = useVideoConfig();
  const enter = spring({ frame, fps, config: { damping: 11, stiffness: 220 } });
  const exitFrames = Math.round(fps * 0.2);
  const exit = interpolate(frame, [durationInFrames - exitFrames, durationInFrames], [1, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const scale = interpolate(enter, [0, 1], [1.35, 1]) * interpolate(exit, [0, 1], [0.6, 1]);
  const rotate = Math.sin((frame / fps) * 6.6) * wobble;

  return (
    <AbsoluteFill style={{ pointerEvents: "none" }}>
      <div
        style={{
          position: "absolute",
          left: 60,
          right: 60,
          top: y,
          transform: `translateY(-50%) scale(${scale}) rotate(${rotate}deg)`,
          opacity: exit,
          textAlign: "center",
          fontFamily: FONT,
          fontWeight: 700,
          fontSize: size,
          lineHeight: 1.02,
          color: "white",
          WebkitTextStroke: `${size * 0.13}px #0b0716`,
          paintOrder: "stroke fill",
          textShadow: `0 ${size * 0.08}px 0 rgba(0,0,0,0.45)`,
          letterSpacing: size * 0.01,
        }}
      >
        {lines.map((line, i) => (
          <div key={i}>
            {line.map((w, j) => (
              <span key={j} style={typeof w === "string" ? undefined : { color: w.color }}>
                {(j > 0 ? " " : "") + (typeof w === "string" ? w : w.text)}
              </span>
            ))}
          </div>
        ))}
      </div>
    </AbsoluteFill>
  );
};
