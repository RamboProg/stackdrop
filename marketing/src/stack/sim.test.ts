import assert from "node:assert/strict";
import { test } from "node:test";
import { THEMES } from "../game/palette.ts";
import { BASE, simulate, type SimConfig } from "./sim.ts";

const base: SimConfig = { theme: THEMES.synthwave, width: 1080, height: 1920, fps: 30, frames: 30 * 20, drops: [] };

const kinds = (config: SimConfig): string[] =>
  simulate(config).events.filter((e) => e.kind !== "grow" && e.kind !== "streakPiece").map((e) => e.kind);

test("scripted offsets land as perfect drops, cuts and a miss", () => {
  assert.deepEqual(kinds({ ...base, drops: [0, 2, 0.1, { miss: 1 }] }), ["perfect", "cut", "perfect", "miss"]);
});

test("a cut removes exactly the scripted offset from the slab", () => {
  const { tower } = simulate({ ...base, drops: [1.5, -2] });
  // Level 1 slides along x, level 2 along z.
  assert.equal(tower[1].w, BASE - 1.5);
  assert.equal(tower[2].d, BASE - 2);
});

test("five perfects in a row pay out a streak of five free levels", () => {
  const { events, frames } = simulate({ ...base, drops: [0, 0, 0, 0, 0] });
  assert.equal(events.filter((e) => e.kind === "streakPiece").length, 5);
  assert.equal(events.filter((e) => e.kind === "streakDone").length, 1);
  assert.equal(frames[frames.length - 1].score, 10);
});

test("pre-stacked levels end at the requested top size", () => {
  const { tower } = simulate({ ...base, frames: 1, startScore: 20, startSize: [4, 5] });
  assert.equal(tower.length, 21);
  assert.equal(tower[20].w, 4);
  assert.equal(tower[20].d, 5);
});

test("every render of the same script is identical", () => {
  const config: SimConfig = { ...base, drops: [0, 1, 0, -1, { miss: 0.5, bounces: 1 }] };
  assert.deepEqual(simulate(config).frames, simulate(config).frames);
});

test("a bounced drop happens later than a direct one", () => {
  const direct = simulate({ ...base, drops: [0] }).events[0].frame;
  const bounced = simulate({ ...base, drops: [{ offset: 0, bounces: 1 }] }).events[0].frame;
  assert.ok(bounced > direct);
});
