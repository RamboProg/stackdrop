import { useLayoutEffect, useRef } from "react";
import { useVideoConfig } from "remotion";
import type { Family, Theme } from "../game/palette.ts";
import { FONT } from "./fonts.ts";
import { drawThemesScreen } from "./themesScreen.ts";
import { useFontReady } from "./useFontReady.ts";

type Props = { theme: Theme; family: Family; inUse: string; gems: number; alpha: number };

/** The game's Themes screen as an overlay above a running scene. */
export const ThemesLayer: React.FC<Props> = ({ theme, family, inUse, gems, alpha }) => {
  const { width, height } = useVideoConfig();
  const ref = useRef<HTMLCanvasElement>(null);
  const ready = useFontReady();

  useLayoutEffect(() => {
    const ctx = ref.current?.getContext("2d");
    if (!ctx || !ready) return;
    ctx.clearRect(0, 0, width, height);
    if (alpha > 0) drawThemesScreen(ctx, { theme, family, inUse, gems, font: FONT, w: width, h: height, alpha });
  }, [ready, theme, family, inUse, gems, alpha, width, height]);

  return <canvas ref={ref} width={width} height={height} style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }} />;
};
