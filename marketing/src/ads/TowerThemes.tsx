import { Audio } from "@remotion/media";
import { AbsoluteFill, interpolate, Sequence, staticFile, useCurrentFrame } from "remotion";
import { THEMES, type Family, type Theme } from "../game/palette.ts";
import { FORMAT, hex, sec } from "../lib/format.ts";
import { simulateTower, type TowerResult } from "../tower/sim.ts";
import { TowerScene } from "../tower/TowerScene.tsx";
import { TowerSfx } from "../tower/TowerSfx.tsx";
import { Caption } from "../ui/Caption.tsx";
import { PUNCH } from "../ui/colors.ts";
import { EndCard } from "../ui/EndCard.tsx";
import { MusicBed } from "../ui/MusicBed.tsx";
import { gameplayTaps, TapDots, type Tap } from "../ui/TapDots.tsx";
import { rowCenter, themesLayout } from "../ui/themesScreen.ts";
import { ThemesLayer } from "../ui/ThemesLayer.tsx";

/**
 * "Pick your vibe" ad: the Themes screen as in the game. Each tap restyles the screen and cross-fades
 * the tower behind it to the new theme, then the list closes on a short run in that theme.
 */

const GEMS = 1240;
const HOOK = sec(1.4);
/** Before the row tap: list open (plus a tab tap when the tab changes). */
const TAB_TAP = sec(0.3);
const ROW_TAP = sec(0.4);
const FADE = sec(0.5);
const LIST_CLOSE = sec(0.35);
const CLOSE_TIME = sec(0.15);
const PLAY = sec(1.7);
/** When the run's first perfect drop lands, counted from the list closing. */
const PERFECT_IN = sec(0.75);

type Shot = { theme: Theme; family: Family };
const SHOTS: Shot[] = [
  { theme: THEMES.sakura, family: "calm" },
  { theme: THEMES.aurora, family: "calm" },
  { theme: THEMES.toxic, family: "neon" },
  { theme: THEMES.ice, family: "neon" },
];
const HOOK_THEME = THEMES.synthwave;

type Segment = Shot & {
  sim: TowerResult;
  /** Sim frame shown at the segment's first frame. */
  startAt: number;
  from: number;
  tabTap: number | null;
  rowTap: number;
  close: number;
  length: number;
};

function run(theme: Theme, seed: number): TowerResult {
  return simulateTower({
    theme,
    ...FORMAT,
    frames: sec(14),
    drops: [{ offset: 0, swings: 1 }, 0, 0, 0],
    startFloors: 6 + seed,
    startOffsets: [0, 0.15, -0.1, 0.1, -0.05, 0.1],
    seed,
  });
}

const firstPerfect = (sim: TowerResult): number => {
  const f = sim.events.find((e) => e.kind === "perfect")?.frame;
  if (f === undefined) throw new Error("A TowerThemes run never lands a perfect drop");
  return f;
};

const hookSim = run(HOOK_THEME, 1);
const hookStart = Math.max(0, firstPerfect(hookSim) - sec(0.7));

const segments: Segment[] = [];
{
  let from = HOOK;
  let family: Family = "calm";
  SHOTS.forEach((shot, i) => {
    const tabTap = shot.family !== family ? TAB_TAP : null;
    const rowTap = (tabTap ?? 0) + ROW_TAP;
    const close = rowTap + FADE + LIST_CLOSE;
    const length = close + PLAY;
    const sim = run(shot.theme, i + 2);
    const startAt = firstPerfect(sim) - (close + PERFECT_IN);
    if (startAt < 0) throw new Error(`${shot.theme.name}: the first perfect drop comes too early for the list`);
    segments.push({ ...shot, sim, startAt, from, tabTap, rowTap, close, length });
    from += length;
    family = shot.family;
  });
}

export const TOWER_THEMES_END = segments[segments.length - 1].from + segments[segments.length - 1].length;
export const TOWER_THEMES_DURATION = TOWER_THEMES_END + sec(2.6);

const layout = themesLayout(FORMAT.width, FORMAT.height);
const tabCenter = (family: Family): { x: number; y: number } => {
  const r = layout.tabs[family === "calm" ? 0 : 1];
  return { x: (r.left + r.right) / 2, y: (r.top + r.bottom) / 2 };
};

const taps: Tap[] = [
  ...gameplayTaps(hookSim.events.filter((e) => e.kind === "release").map((e) => e.frame), hookStart).filter((t) => t.frame < HOOK),
  ...segments.flatMap((s) => [
    ...(s.tabTap === null ? [] : [{ frame: s.from + s.tabTap, ...tabCenter(s.family) }]),
    { frame: s.from + s.rowTap, ...rowCenter(layout, s.family, s.theme.id, FORMAT.width) },
    ...gameplayTaps(s.sim.events.filter((e) => e.kind === "release").map((e) => e.frame), s.startAt - s.from)
      .filter((t) => t.frame >= s.from + s.close && t.frame < s.from + s.length),
  ]),
];

const ThemeShot: React.FC<{ seg: Segment; prev: { theme: Theme; sim: TowerResult; startAt: number } }> = ({ seg, prev }) => {
  const frame = useCurrentFrame();
  const fade = interpolate(frame, [seg.rowTap, seg.rowTap + FADE], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const listAlpha = interpolate(frame, [seg.close - CLOSE_TIME, seg.close], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const family: Family = seg.tabTap !== null && frame < seg.tabTap ? (seg.family === "calm" ? "neon" : "calm") : seg.family;
  const selected = frame >= seg.rowTap;
  const onScreen = selected ? seg.theme : prev.theme;
  return (
    <AbsoluteFill>
      <TowerScene sim={seg.sim} theme={seg.theme} hud={frame >= seg.close} startAt={seg.startAt} gems={GEMS} />
      {fade > 0 && (
        <AbsoluteFill style={{ opacity: fade }}>
          <TowerScene sim={prev.sim} theme={prev.theme} hud={false} startAt={prev.startAt} gems={GEMS} />
        </AbsoluteFill>
      )}
      <ThemesLayer theme={onScreen} family={family} inUse={onScreen.id} gems={GEMS} alpha={listAlpha} />
      {seg.tabTap !== null && (
        <Sequence from={seg.tabTap} durationInFrames={sec(0.3)} layout="none">
          <Audio src={staticFile("sfx/button.wav")} volume={0.8} />
        </Sequence>
      )}
      <Sequence from={seg.rowTap} durationInFrames={sec(0.3)} layout="none">
        <Audio src={staticFile("sfx/button.wav")} volume={0.8} />
      </Sequence>
      <Sequence from={seg.close} layout="none">
        <TowerSfx sim={seg.sim} startAt={seg.startAt + seg.close} />
      </Sequence>
      <Sequence from={seg.close + sec(0.1)}>
        <Caption y={1420} size={120} wobble={2} lines={[[{ text: seg.theme.name.toUpperCase(), color: hex(seg.theme.accent) }]]} />
      </Sequence>
    </AbsoluteFill>
  );
};

export const TowerThemes: React.FC = () => (
  <AbsoluteFill style={{ background: "black" }}>
    <Sequence durationInFrames={HOOK}>
      <TowerScene sim={hookSim} theme={HOOK_THEME} hud={false} startAt={hookStart} gems={GEMS} />
      <TowerSfx sim={hookSim} startAt={hookStart} />
      <Caption y={215} size={104} lines={[["PICK YOUR"], [{ text: "VIBE", color: PUNCH }]]} />
    </Sequence>
    {segments.map((seg, i) => {
      const before = i === 0 ? { theme: HOOK_THEME, sim: hookSim, startAt: hookStart + HOOK } : segments[i - 1];
      const prev = i === 0 ? before : { theme: before.theme, sim: before.sim, startAt: before.startAt + segments[i - 1].length };
      return (
        <Sequence key={seg.theme.id} from={seg.from} durationInFrames={seg.length}>
          <ThemeShot seg={seg} prev={prev} />
        </Sequence>
      );
    })}
    <TapDots taps={taps} />
    <Sequence from={TOWER_THEMES_END}>
      <EndCard
        accent={hex(HOOK_THEME.accent)}
        accent2={hex(HOOK_THEME.accent2)}
        headline="14 themes. Which one is you?"
        tagline="Swing. Drop. Stay balanced."
      />
    </Sequence>
    <MusicBed />
  </AbsoluteFill>
);
