import { Audio } from "@remotion/media";
import { interpolate, staticFile, useVideoConfig } from "remotion";

/** The game's own synthesized music loop, faded out over the last second. */
export const MusicBed: React.FC<{ volume?: number }> = ({ volume = 0.45 }) => {
  const { durationInFrames, fps } = useVideoConfig();
  return (
    <Audio
      src={staticFile("sfx/music.wav")}
      loop
      volume={(f) =>
        volume * interpolate(f, [durationInFrames - fps, durationInFrames], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" })
      }
    />
  );
};
