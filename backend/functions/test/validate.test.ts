import assert from "node:assert/strict";
import { test } from "node:test";
import { checkRun, parseRun, RunInput } from "../src/validate";

const run = (over: Partial<RunInput> = {}): RunInput => ({
  mode: "tower", score: 20, durationMs: 60_000, drops: 24, perfects: 8, bestCombo: 3,
  revived: false, runId: "8f2c1a7e-1111-2222-3333-444455556666", ...over,
});

test("a normal run passes unflagged", () => {
  assert.deepEqual(checkRun(run()), { ok: true, suspicious: false });
});

test("impossible runs are rejected", () => {
  assert.equal(checkRun(run({ score: 5001, drops: 6000, durationMs: 9e9 })).ok, false);
  assert.equal(checkRun(run({ score: 30, drops: 24 })).ok, false);
  assert.equal(checkRun(run({ perfects: 25 })).ok, false);
  assert.equal(checkRun(run({ bestCombo: 9 })).ok, false);
  // Tower needs at least 650 ms per drop: 24 drops cannot happen in 10 seconds.
  assert.deepEqual(checkRun(run({ durationMs: 10_000 })), { ok: false, reason: "too-fast" });
  // Stack allows 220 ms per drop.
  assert.equal(checkRun(run({ mode: "stack", durationMs: 10_000, perfects: 3, bestCombo: 2 })).ok, true);
});

test("streaks add free stacks, but only as many as the perfects allow", () => {
  // 10 perfects can hold two streaks: 10 free stacks on top of 24 drops.
  assert.equal(checkRun(run({ score: 34, perfects: 10, bestCombo: 10 })).ok, true);
  assert.deepEqual(checkRun(run({ score: 35, perfects: 10, bestCombo: 10 })), { ok: false, reason: "score-exceeds-drops" });
  // 4 perfects can't hold a streak.
  assert.equal(checkRun(run({ score: 25, perfects: 4 })).ok, false);
});

test("unusual but possible runs are flagged, not rejected", () => {
  const nearlyAllPerfect = checkRun(run({ score: 40, drops: 40, perfects: 40, bestCombo: 40, durationMs: 120_000 }));
  assert.deepEqual(nearlyAllPerfect, { ok: true, suspicious: true });
  const veryFast = checkRun(run({ durationMs: 20_000 }));
  assert.deepEqual(veryFast, { ok: true, suspicious: true });
});

test("payloads with wrong types or shapes are refused", () => {
  assert.notEqual(parseRun({ ...run() }), null);
  assert.equal(parseRun({ ...run(), mode: "chess" }), null);
  assert.equal(parseRun({ ...run(), score: 1.5 }), null);
  assert.equal(parseRun({ ...run(), drops: -1 }), null);
  assert.equal(parseRun({ ...run(), revived: "no" }), null);
  assert.equal(parseRun({ ...run(), runId: "x" }), null);
  assert.equal(parseRun({ ...run(), runId: "../../etc" }), null);
});
