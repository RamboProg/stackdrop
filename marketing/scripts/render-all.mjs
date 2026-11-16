// Renders every ad to out/<id>.mp4. Pass ids to render only those: pnpm render TowerStreak
import { spawnSync } from "node:child_process";

const ALL = ["TowerStreak", "TowerCollapse", "TowerThemes"];
const ids = process.argv.slice(2).length > 0 ? process.argv.slice(2) : ALL;

for (const id of ids) {
  const result = spawnSync("pnpm", ["exec", "remotion", "render", id, `out/${id}.mp4`], { stdio: "inherit", shell: true });
  if (result.status !== 0) {
    console.error(`Rendering ${id} failed with exit code ${result.status}`);
    process.exit(result.status ?? 1);
  }
}
