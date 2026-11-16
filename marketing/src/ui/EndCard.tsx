import { Audio } from "@remotion/media";
import { AbsoluteFill, Img, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { CYAN, MAGENTA } from "./colors.ts";
import { FONT } from "./fonts.ts";

type Props = {
  /** Theme accents for the button gradient and glow. */
  accent: string;
  accent2: string;
  headline: string;
  tagline?: string;
};

/** Closing call to action over the dimmed, blurred gameplay. Place it in the final Sequence. */
export const EndCard: React.FC<Props> = ({ accent, accent2, headline, tagline = "One tap. Endless stacking." }) => {
  const { fps: videoFps } = useVideoConfig();
  // Timings below were tuned at 30 fps; this keeps them in seconds at any frame rate.
  const frame = (useCurrentFrame() * 30) / videoFps;
  const fps = 30;
  const dim = interpolate(frame, [0, 8], [0, 1], { extrapolateRight: "clamp" });
  const pop = (delay: number): number => spring({ frame: frame - delay, fps, config: { damping: 12, stiffness: 180 } });
  const pulse = 1 + 0.045 * Math.sin(frame * 0.32);
  const split = Math.sin(frame * 0.9) * Math.sin(frame * 1.7 + 1) > 0.75 ? 9 : 4;

  return (
    <AbsoluteFill
      style={{
        background: `rgba(6,3,16,${(0.78 * dim).toFixed(3)})`,
        backdropFilter: `blur(${14 * dim}px)`,
        alignItems: "center",
        justifyContent: "center",
        fontFamily: FONT,
        color: "white",
        textAlign: "center",
      }}
    >
      <Audio src={staticFile("sfx/unlock.wav")} volume={0.8} />
      <div style={{ transform: `scale(${pop(0)})`, fontSize: 78, fontWeight: 700, lineHeight: 1.05, padding: "0 70px", marginBottom: 70 }}>
        {headline}
      </div>
      <Img
        src={staticFile("icon.png")}
        style={{
          width: 300,
          height: 300,
          borderRadius: 66,
          transform: `scale(${pop(4)})`,
          boxShadow: `0 0 80px ${accent}aa, 0 0 0 6px ${accent}55`,
        }}
      />
      <div
        style={{
          marginTop: 56,
          fontSize: 150,
          fontWeight: 700,
          letterSpacing: 4,
          transform: `scale(${pop(8)})`,
          textShadow: `${-split}px 0 0 ${CYAN}99, ${split}px 0 0 ${MAGENTA}99, 0 0 40px ${accent}`,
        }}
      >
        STACK DROP
      </div>
      <div style={{ fontSize: 52, opacity: pop(12) * 0.85, marginTop: 6, fontWeight: 500 }}>{tagline}</div>
      <div
        style={{
          marginTop: 80,
          transform: `scale(${pop(16) * pulse})`,
          padding: "34px 110px",
          borderRadius: 999,
          fontSize: 84,
          fontWeight: 700,
          background: `linear-gradient(90deg, ${accent}, ${accent2})`,
          boxShadow: `0 0 60px ${accent}cc, inset 0 -8px 0 rgba(0,0,0,0.25)`,
          color: "#14081f",
        }}
      >
        PLAY FREE
      </div>
      <div style={{ marginTop: 40, fontSize: 44, opacity: pop(20) * 0.9, display: "flex", alignItems: "center", gap: 18 }}>
        <svg width="40" height="44" viewBox="0 0 40 44" aria-hidden>
          <path d="M4 3 L37 22 L4 41 Z" fill="white" />
        </svg>
        Free on Google Play
      </div>
    </AbsoluteFill>
  );
};
