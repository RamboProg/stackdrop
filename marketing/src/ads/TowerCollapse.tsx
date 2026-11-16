import { AbsoluteFill, interpolate, Sequence, useCurrentFrame } from "remotion";
import { THEMES } from "../game/palette.ts";
import { FORMAT, hex, sec } from "../lib/format.ts";
import { simulateTower, type TowerConfig } from "../tower/sim.ts";
import { TowerScene } from "../tower/TowerScene.tsx";
import { TowerSfx } from "../tower/TowerSfx.tsx";
import { Caption } from "../ui/Caption.tsx";
import { PUNCH, RED } from "../ui/colors.ts";
import { EndCard } from "../ui/EndCard.tsx";
import { MusicBed } from "../ui/MusicBed.tsx";
import { gameplayTaps, TapDots } from "../ui/TapDots.tsx";

/**
 * "Fail" ad: drops creep off-center, the balance meter turns amber ("LEANING RIGHT"), and the next
 * one tips the top of the tower over. The tipping and the pieces hitting the street play in slow motion.
 */

const theme = THEMES.inferno;
const HOOK_END = sec(1.6);

const base: TowerConfig = {
  theme,
  ...FORMAT,
  frames: sec(18),
  drops: [0, 0.6, 0.9, 1.1, 1.3],
  startFloors: 7,
  startOffsets: [0, 0.1, -0.1, 0.05, 0, 0.1, -0.05],
  seed: 9,
};

// First pass finds the collapse; the second slows time while it tips and breaks.
const collapseTime = simulateTower(base).events.find((e) => e.kind === "collapse")?.time;
if (collapseTime === undefined) throw new Error("TowerCollapse script never collapses; check the drops");
const SLOW_FROM = collapseTime - 0.15;
const SLOW_TO = collapseTime + 1.35;
const sim = simulateTower({ ...base, timeScale: (t) => (t >= SLOW_FROM && t <= SLOW_TO ? 0.35 : 1) });

const first = (kind: string): number => sim.events.find((e) => e.kind === kind)?.frame ?? 0;
const warnFrame = first("warn");
const collapseFrame = first("collapse");
const settleFrame = sim.frames.findIndex((f) => f.time >= SLOW_TO);
const taps = gameplayTaps(sim.events.filter((e) => e.kind === "release").map((e) => e.frame));

export const TOWER_COLLAPSE_END = settleFrame + sec(1.3);
export const TOWER_COLLAPSE_DURATION = TOWER_COLLAPSE_END + sec(2.6);

export const TowerCollapse: React.FC = () => {
  const frame = useCurrentFrame();
  const flash = interpolate(frame, [collapseFrame, collapseFrame + sec(0.06), collapseFrame + sec(0.3)], [0, 0.35, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  return (
    <AbsoluteFill style={{ background: "black" }}>
      <TowerScene sim={sim} theme={theme} hud={frame >= HOOK_END} />
      <TapDots taps={taps} />
      <AbsoluteFill style={{ background: RED, opacity: flash }} />

      <Sequence durationInFrames={HOOK_END}>
        <Caption y={215} size={104} lines={[["DON'T LET IT"], [{ text: "FALL", color: PUNCH }]]} />
      </Sequence>
      <Sequence from={warnFrame} durationInFrames={collapseFrame - warnFrame}>
        <Caption y={1420} size={100} wobble={3} lines={[["IT'S", { text: "LEANING...", color: PUNCH }]]} />
      </Sequence>
      <Sequence from={collapseFrame} durationInFrames={settleFrame - collapseFrame}>
        <Caption y={1420} size={150} wobble={4} lines={[[{ text: "NOOO!", color: RED }]]} />
      </Sequence>
      <Sequence from={settleFrame} durationInFrames={TOWER_COLLAPSE_END - settleFrame}>
        <Caption y={1420} size={92} lines={[["ONE BAD DROP..."]]} />
      </Sequence>

      <Sequence from={TOWER_COLLAPSE_END}>
        <EndCard accent={hex(theme.accent)} accent2={hex(theme.accent2)} headline="Can YOU keep it standing?" tagline="Swing. Drop. Stay balanced." />
      </Sequence>

      <TowerSfx sim={sim} />
      <MusicBed />
    </AbsoluteFill>
  );
};
