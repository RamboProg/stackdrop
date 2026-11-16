import assert from "node:assert/strict";
import { test } from "node:test";
import { THEMES } from "../game/palette.ts";
import { simulateTower, type TowerConfig } from "./sim.ts";

const base: TowerConfig = { theme: THEMES.synthwave, width: 1080, height: 1920, fps: 60, frames: 60 * 20, drops: [] };

const kinds = (config: TowerConfig, keep: string[]): string[] =>
  simulateTower(config).events.filter((e) => keep.includes(e.kind)).map((e) => e.kind);

test("scripted offsets land as perfect drops and off-center landings", () => {
  assert.deepEqual(kinds({ ...base, drops: [0, 0.8, -0.6, 0] }, ["perfect", "land"]), ["perfect", "land", "land", "perfect"]);
});

test("off-center landings land where the script says, within the perfect tolerance", () => {
  const { frames } = simulateTower({ ...base, drops: [0, 1.0] });
  const floors = frames[frames.length - 1].floors;
  assert.ok(Math.abs(floors[1].x - floors[0].x - 1.0) < 0.28, `landed at ${floors[1].x - floors[0].x}`);
});

test("five perfects in a row drop five free floors and straighten the tower", () => {
  const { events, frames } = simulateTower({ ...base, drops: [0, 0, 0, 0, 0], startFloors: 4, startOffsets: [0, 0.3] });
  assert.equal(events.filter((e) => e.kind === "streakPiece").length, 5);
  assert.equal(events.filter((e) => e.kind === "streakDone").length, 1);
  const last = frames[frames.length - 1];
  assert.equal(last.score, 14);
  assert.ok(last.floors.every((f) => f.x === 0), "straightening moves every floor over the foundation");
});

test("drops creeping to one side tip the tower over and break it apart", () => {
  const config: TowerConfig = { ...base, drops: [0, 0.6, 0.9, 1.1, 1.3], startFloors: 7 };
  assert.deepEqual(kinds(config, ["warn", "collapse", "break"]), ["warn", "collapse", "break"]);
  const last = simulateTower(config).frames.at(-1);
  assert.ok(last?.isOver);
  assert.ok((last?.tumbles.length ?? 0) > 0, "the toppled floors become loose pieces");
});

test("a landing past half a module slips off and costs a life", () => {
  const { events, frames } = simulateTower({ ...base, drops: [2.15], startFloors: 20 });
  assert.equal(events.find((e) => e.kind !== "release")?.kind, "slip");
  assert.equal(frames[frames.length - 1].lives, 2);
});

test("every render of the same script is identical", () => {
  const config: TowerConfig = { ...base, frames: 600, drops: [0, 0.7, 0], startFloors: 5 };
  assert.deepEqual(simulateTower(config).frames, simulateTower(config).frames);
});
