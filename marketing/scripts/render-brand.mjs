// Renders the GrabStacks logo files to out/brand/: PNGs on navy and transparent, plus a vector SVG
// of the mark (the full logo has text, so it stays PNG to keep the Fredoka lettering exact).
import { spawnSync } from "node:child_process";
import { mkdirSync, rmSync, writeFileSync } from "node:fs";
import { markSvg, NAVY } from "../src/brand/mark.ts";

const OUT = "out/brand";
mkdirSync(OUT, { recursive: true });

const stills = [
  ["GrabStacksLogo", "grabstacks-logo.png", true],
  ["GrabStacksLogo", "grabstacks-logo-transparent.png", false],
  ["GrabStacksMark", "grabstacks-mark.png", true],
  ["GrabStacksMark", "grabstacks-mark-transparent.png", false],
];

for (const [id, file, background] of stills) {
  // A props file avoids shell quoting differences between Windows and POSIX.
  const props = `${OUT}/.props.json`;
  writeFileSync(props, JSON.stringify({ background }));
  const args = ["exec", "remotion", "still", id, `${OUT}/${file}`, "--image-format=png", `--props=${props}`];
  const result = spawnSync("pnpm", args, { stdio: "inherit", shell: true });
  if (result.status !== 0) {
    console.error(`Rendering ${file} failed with exit code ${result.status}`);
    process.exit(result.status ?? 1);
  }
}

rmSync(`${OUT}/.props.json`, { force: true });
writeFileSync(`${OUT}/grabstacks-mark.svg`, markSvg(1024, NAVY));
writeFileSync(`${OUT}/grabstacks-mark-transparent.svg`, markSvg(1024, null));
console.log(`Logo files written to ${OUT}/`);
