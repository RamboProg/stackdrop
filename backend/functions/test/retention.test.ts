import assert from "node:assert/strict";
import { test } from "node:test";
import { inactiveGuests, UserActivity } from "../src/retention";

const now = Date.parse("2027-06-01T00:00:00Z");
const daysAgo = (d: number): string => new Date(now - d * 24 * 60 * 60 * 1000).toUTCString();

const guest = (uid: string, lastRefresh: number): UserActivity => ({
  uid, providers: [], creationTime: daysAgo(800), lastSignInTime: daysAgo(800), lastRefreshTime: daysAgo(lastRefresh),
});

test("guests unused for over a year are selected", () => {
  assert.deepEqual(inactiveGuests([guest("old", 400), guest("recent", 30), guest("edge", 364)], now), ["old"]);
});

test("Google accounts are never selected, however old", () => {
  const google: UserActivity = { ...guest("g", 900), providers: ["google.com"] };
  assert.deepEqual(inactiveGuests([google], now), []);
});

test("the latest of creation, sign-in and refresh counts", () => {
  const signedInLately: UserActivity = { ...guest("a", 900), lastSignInTime: daysAgo(10) };
  const newAccount: UserActivity = { uid: "b", providers: [], creationTime: daysAgo(5) };
  assert.deepEqual(inactiveGuests([signedInLately, newAccount], now), []);
});

test("a record without readable times is kept", () => {
  assert.deepEqual(inactiveGuests([{ uid: "x", providers: [], creationTime: "not a date" }], now), []);
});
