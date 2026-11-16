import { loadFont } from "@remotion/fonts";
import { staticFile } from "remotion";

/** Fredoka is the game's own brand font (app/src/main/assets/fonts). */
export const FONT = "Fredoka";

/** Resolves once the font can be drawn on a canvas; loadFont also holds the render until then. */
export const fontReady: Promise<void> = loadFont({ family: FONT, url: staticFile("Fredoka.ttf"), weight: "300 700" });
