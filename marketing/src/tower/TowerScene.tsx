import { useLayoutEffect, useRef } from "react";
import { useCurrentFrame, useVideoConfig } from "remotion";
import type { Theme } from "../game/palette.ts";
import { FONT } from "../ui/fonts.ts";
import { useFontReady } from "../ui/useFontReady.ts";
import { renderTower } from "./render.ts";
import type { TowerResult } from "./sim.ts";

type Props = {
  sim: TowerResult;
  theme: Theme;
  /** Show the score, combo, lives, balance meter and gems. */
  hud?: boolean;
  /** Sim frame shown at this sequence's first frame, to start a clip mid-action. */
  startAt?: number;
  gems?: number;
};

/** Draws one frame of a simulated tower run exactly as the game would. */
export const TowerScene: React.FC<Props> = ({ sim, theme, hud = true, startAt = 0, gems = 1240 }) => {
  const frame = useCurrentFrame();
  const { width, height, fps } = useVideoConfig();
  const ref = useRef<HTMLCanvasElement>(null);
  const ready = useFontReady();

  useLayoutEffect(() => {
    const ctx = ref.current?.getContext("2d");
    if (!ctx || !ready) return;
    const index = Math.max(0, Math.min(sim.frames.length - 1, frame + startAt));
    renderTower(ctx, sim.geometry, sim.frames[index], theme, { hud, font: FONT, time: (frame + startAt) / fps, gems });
  }, [frame, ready, sim, theme, hud, startAt, fps, gems]);

  return <canvas ref={ref} width={width} height={height} style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }} />;
};
