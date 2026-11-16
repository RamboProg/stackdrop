import { Audio } from "@remotion/media";
import { Sequence, staticFile, useVideoConfig } from "remotion";
import type { TowerEvent, TowerResult } from "./sim.ts";

/** Semitones of PERFECT_SCALE and STREAK_NOTES in game/GameMode.kt. */
const PERFECT_SCALE = [0, 2, 4, 5, 7, 9, 11, 12];
const STREAK_NOTES = [0, 2, 4, 7, 12];

type Sound = { file: string; volume: number };

/** The sounds TowerMode plays for each event, using the pre-pitched copies from prepare-assets. */
function soundsFor(e: TowerEvent): Sound[] {
  switch (e.kind) {
    case "release":
      return [{ file: "release", volume: 0.7 }];
    case "perfect":
      return [{ file: `perfect_${PERFECT_SCALE[Math.min(e.combo - 1, PERFECT_SCALE.length - 1)]}`, volume: 1 }];
    case "land":
      return [{ file: "drop_lo", volume: 0.8 }];
    case "warn":
      return [{ file: "warn", volume: 0.5 }];
    case "slip":
      return [{ file: "fail_hi", volume: 0.45 }];
    case "streakPiece":
      return [
        { file: `perfect_${STREAK_NOTES[Math.min(e.amount, STREAK_NOTES.length - 1)]}`, volume: 0.75 },
        { file: "drop_hi", volume: 0.3 },
      ];
    case "streakDone":
      return [{ file: "unlock", volume: 0.9 }];
    case "collapse":
      return [{ file: "fail_lo", volume: 0.7 }];
    case "break":
      return [{ file: "slice_lo", volume: 0.6 }];
    case "impact":
      return [{ file: "drop_thud", volume: 0.3 + 0.5 * e.amount }];
  }
}

type Props = { sim: TowerResult; startAt?: number; volume?: number };

export const TowerSfx: React.FC<Props> = ({ sim, startAt = 0, volume = 1 }) => {
  const { fps } = useVideoConfig();
  return (
    <>
      {sim.events
        .filter((e) => e.frame >= startAt)
        .flatMap((e, i) =>
          soundsFor(e).map((s, j) => (
            <Sequence key={`${i}-${j}`} from={e.frame - startAt} durationInFrames={Math.round(fps * 1.2)} layout="none">
              <Audio src={staticFile(`sfx/${s.file}.wav`)} volume={s.volume * volume} />
            </Sequence>
          )),
        )}
    </>
  );
};
