import assert from "node:assert/strict";
import { test } from "node:test";
import { CODE_ALPHABET, defaultAvatar, formatCode, newFriendCode, normalizeCode } from "../src/codes";

test("friend codes use the Crockford alphabet", () => {
  assert.equal(CODE_ALPHABET.length, 32);
  for (const banned of "ILOU") assert.equal(CODE_ALPHABET.includes(banned), false);
  for (let i = 0; i < 200; i++) {
    const code = newFriendCode();
    assert.equal(code.length, 8);
    for (const ch of code) assert.ok(CODE_ALPHABET.includes(ch), code);
  }
});

test("typed codes are normalized", () => {
  assert.equal(normalizeCode("k7q2-m9xd"), "K7Q2M9XD");
  assert.equal(normalizeCode(" K7Q2 M9XD "), "K7Q2M9XD");
  assert.equal(normalizeCode("O0IL-1234"), "00111234");
  assert.equal(normalizeCode("K7Q2-M9X"), null);
  assert.equal(normalizeCode("K7Q2-M9XU"), null);
});

test("codes display with a dash", () => {
  assert.equal(formatCode("K7Q2M9XD"), "K7Q2-M9XD");
});

test("default avatar matches the app's hash", () => {
  // "abc" = 97 + 98 + 99 = 294, and 294 % 12 = 6. AvatarsTest in the app checks the same value.
  assert.equal(defaultAvatar("abc"), 6);
});
