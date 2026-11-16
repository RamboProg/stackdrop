import { useLayoutEffect, useRef } from "react";
import { useCurrentFrame, useVideoConfig } from "remotion";
import type { Theme } from "../game/palette.ts";
import { FONT } from "../ui/fonts.ts";
import { useFontReady } from "../ui/useFontReady.ts";
import { renderFrame } from "./render.ts";
import type { SimResult } from "./sim.ts";

type Props = {
  sim: SimResult;
  theme: Theme;
  /** Show the in-game score and combo label. */
  hud?: boolean;
  /** Sim frame shown at this sequence's first frame, to start a clip mid-action. */
  startAt?: number;
};

/** Draws one frame of a simulated stack run exactly as the game would, onto a full-size canvas. */
export const StackScene: React.FC<Props> = ({ sim, theme, hud = true, startAt = 0 }) => {
  const frame = useCurrentFrame();
  const { width, height, fps } = useVideoConfig();
  const ref = useRef<HTMLCanvasElement>(null);
  const ready = useFontReady();

  useLayoutEffect(() => {
    const ctx = ref.current?.getContext("2d");
    if (!ctx || !ready) return;
    const index = Math.min(sim.frames.length - 1, frame + startAt);
    renderFrame(ctx, sim, sim.frames[index], theme, width, height, (frame + startAt) / fps, { hud, font: FONT });
  }, [frame, ready, sim, theme, width, height, fps, hud, startAt]);

  return <canvas ref={ref} width={width} height={height} style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }} />;
};
