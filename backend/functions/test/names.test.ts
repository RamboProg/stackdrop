import assert from "node:assert/strict";
import { test } from "node:test";
import { checkName, isBanned, normalizeForFilter, suggestions } from "../src/names";

test("accepts normal names and trims them", () => {
  assert.deepEqual(checkName("  Mo_92 "), { ok: true, name: "Mo_92", lower: "mo_92" });
  assert.equal(checkName("Ali B").ok, true);
});

test("enforces length, characters and spacing", () => {
  assert.deepEqual(checkName("Mo"), { ok: false, reason: "length" });
  assert.deepEqual(checkName("A".repeat(17)), { ok: false, reason: "length" });
  assert.deepEqual(checkName("Mo-92"), { ok: false, reason: "characters" });
  assert.deepEqual(checkName("__ab__"), { ok: false, reason: "characters" });
  assert.equal(checkName("__ab").ok, true);
  assert.deepEqual(checkName("Mo😀92"), { ok: false, reason: "characters" });
  assert.deepEqual(checkName("Mo  92"), { ok: false, reason: "spaces" });
});

test("undoes leetspeak before filtering", () => {
  assert.equal(normalizeForFilter("Sh1t_$tack"), "shitstack");
  assert.equal(isBanned("Sh1t"), true);
  assert.equal(isBanned("F_U_C_K"), true);
  assert.equal(isBanned("fuuuuuck"), true);
});

test("whole-word list does not block innocent names", () => {
  assert.equal(isBanned("Cassie"), false);
  assert.equal(isBanned("Grape Ape"), false);
  assert.equal(isBanned("Dickens"), false);
  assert.equal(isBanned("Nazir"), false);
  assert.equal(isBanned("Big Ass"), true);
  assert.equal(isBanned("Admin"), true);
});

test("suggestions are valid, distinct and differ from the taken name", () => {
  const s = suggestions("Mo_92", () => 0);
  assert.deepEqual(s.slice(0, 2), ["Mo_921", "Mo_92x"]);
  for (const n of s) assert.equal(checkName(n).ok, true);
  assert.equal(new Set(s.map((n) => n.toLowerCase())).size, s.length);
});

test("suggestions fit within 16 characters", () => {
  for (const n of suggestions("SixteenCharsName", () => 0.99)) assert.ok(n.length <= 16, n);
});
