import { AbsoluteFill, Sequence, useCurrentFrame } from "remotion";
import { THEMES } from "../game/palette.ts";
import { FORMAT, hex, sec } from "../lib/format.ts";
import { simulateTower, type TowerConfig } from "../tower/sim.ts";
import { TowerScene } from "../tower/TowerScene.tsx";
import { TowerSfx } from "../tower/TowerSfx.tsx";
import { Caption } from "../ui/Caption.tsx";
import { PUNCH } from "../ui/colors.ts";
import { EndCard } from "../ui/EndCard.tsx";
import { MusicBed } from "../ui/MusicBed.tsx";
import { gameplayTaps, TapDots } from "../ui/TapDots.tsx";

/**
 * "Perfect streak" ad: a leaning tower, perfect drops climbing the scale, then five free floors and
 * the whole tower snapping straight ("BALANCED!"). Twice, then a pull-back over the tower.
 */

const theme = THEMES.synthwave;
const HOOK_END = sec(1.6);

const base: TowerConfig = {
  theme,
  ...FORMAT,
  frames: sec(17),
  drops: Array<number>(8).fill(0),
  // A tower that drifted right, so the straightening is easy to see.
  startFloors: 8,
  startOffsets: [0, 0.3, 0.25, 0.3, 0.25, 0.3, 0.2, 0.25],
  startCombo: 2,
  seed: 4,
};

// The crane stops shortly after the second "BALANCED!", so its caption gets a full second.
const lastBalanced = simulateTower(base).events.filter((e) => e.kind === "streakDone").at(-1)?.time;
if (lastBalanced === undefined) throw new Error("TowerStreak script never completes a streak");
const SHOWCASE_AT = lastBalanced + 0.9;
const sim = simulateTower({ ...base, showcaseAt: SHOWCASE_AT });

const at = (kind: string): number[] => sim.events.filter((e) => e.kind === kind).map((e) => e.frame);
const streakStarts = sim.events.filter((e) => e.kind === "perfect" && e.combo % 5 === 0).map((e) => e.frame);
const balanced = at("streakDone");
const showcaseFrame = sim.frames.findIndex((f) => f.time >= SHOWCASE_AT && f.hook === "empty" && !f.streakActive);
const finalScore = sim.frames[sim.frames.length - 1].score;
const taps = gameplayTaps(at("release"));

export const TOWER_STREAK_END = showcaseFrame + sec(1.1);
export const TOWER_STREAK_DURATION = TOWER_STREAK_END + sec(2.6);

export const TowerStreak: React.FC = () => {
  const frame = useCurrentFrame();
  return (
    <AbsoluteFill style={{ background: "black" }}>
      <TowerScene sim={sim} theme={theme} hud={frame >= HOOK_END && frame < showcaseFrame} />
      <TapDots taps={taps} />

      <Sequence durationInFrames={HOOK_END}>
        <Caption y={215} size={84} lines={[["5 PERFECTS IN A ROW"], ["=", { text: "FREE FLOORS", color: PUNCH }]]} />
      </Sequence>
      {streakStarts.map((f, i) => (
        <Sequence key={f} from={f} durationInFrames={Math.max(1, (balanced[i] ?? f + sec(1)) - f)}>
          <Caption y={1420} size={96} lines={[[{ text: i === 0 ? "+5 FREE FLOORS!" : "AGAIN!!", color: PUNCH }]]} />
        </Sequence>
      ))}
      {balanced.map((f) => (
        <Sequence key={f} from={f} durationInFrames={sec(1)}>
          <Caption y={1420} size={110} wobble={3} lines={[["TOWER"], [{ text: "STRAIGHTENED", color: PUNCH }]]} />
        </Sequence>
      ))}
      <Sequence from={showcaseFrame} durationInFrames={TOWER_STREAK_END - showcaseFrame}>
        <Caption y={215} size={96} lines={[[{ text: String(finalScore), color: PUNCH }, "FLOORS"], ["AND COUNTING"]]} />
      </Sequence>

      <Sequence from={TOWER_STREAK_END}>
        <EndCard accent={hex(theme.accent)} accent2={hex(theme.accent2)} headline="How tall can YOU build?" tagline="Swing. Drop. Stay balanced." />
      </Sequence>

      <TowerSfx sim={sim} />
      <MusicBed />
    </AbsoluteFill>
  );
};
