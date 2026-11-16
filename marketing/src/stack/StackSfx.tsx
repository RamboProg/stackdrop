import { Audio } from "@remotion/media";
import { Sequence, staticFile } from "remotion";
import type { SimEvent, SimResult } from "./sim.ts";

/** Semitones of PERFECT_SCALE and STREAK_NOTES in game/GameMode.kt. */
const PERFECT_SCALE = [0, 2, 4, 5, 7, 9, 11, 12];
const STREAK_NOTES = [0, 2, 4, 7, 12];

type Sound = { file: string; volume: number };

/** The sounds StackMode plays for each event, using the pre-pitched copies from prepare-assets. */
function soundsFor(e: SimEvent): Sound[] {
  switch (e.kind) {
    case "perfect":
      return [{ file: `perfect_${PERFECT_SCALE[Math.min(e.combo - 1, PERFECT_SCALE.length - 1)]}`, volume: 1 }];
    case "grow":
      return [{ file: "unlock_hi", volume: 0.6 }];
    case "cut":
      return [{ file: "drop", volume: 0.8 }, { file: e.amount > 0.25 ? "slice_hi" : "slice", volume: 0.6 }];
    case "miss":
      return [{ file: "fail", volume: 0.7 }];
    case "streakPiece":
      return [{ file: `perfect_${STREAK_NOTES[Math.min(e.amount, STREAK_NOTES.length - 1)]}`, volume: 0.75 }, { file: "drop_hi", volume: 0.3 }];
    case "streakDone":
      return [{ file: "unlock", volume: 0.9 }];
  }
}

type Props = { sim: SimResult; startAt?: number; volume?: number };

export const SimSfx: React.FC<Props> = ({ sim, startAt = 0, volume = 1 }) => (
  <>
    {sim.events
      .filter((e) => e.frame >= startAt)
      .flatMap((e, i) =>
        soundsFor(e).map((s, j) => (
          <Sequence key={`${i}-${j}`} from={e.frame - startAt} durationInFrames={40} layout="none">
            <Audio src={staticFile(`sfx/${s.file}.wav`)} volume={s.volume * volume} />
          </Sequence>
        )),
      )}
  </>
);
