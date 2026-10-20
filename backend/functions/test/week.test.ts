import assert from "node:assert/strict";
import { test } from "node:test";
import { isoWeekId } from "../src/week";

const at = (iso: string): Date => new Date(iso);

test("weeks start on Monday in UTC", () => {
  assert.equal(isoWeekId(at("2026-10-18T23:59:59Z")), "2026-W42");
  assert.equal(isoWeekId(at("2026-10-19T00:00:00Z")), "2026-W43");
});

test("year boundaries follow ISO-8601", () => {
  assert.equal(isoWeekId(at("2026-01-01T12:00:00Z")), "2026-W01");
  assert.equal(isoWeekId(at("2025-12-29T00:00:00Z")), "2026-W01");
  assert.equal(isoWeekId(at("2024-12-30T00:00:00Z")), "2025-W01");
  assert.equal(isoWeekId(at("2021-01-03T00:00:00Z")), "2020-W53");
  assert.equal(isoWeekId(at("2020-12-31T00:00:00Z")), "2020-W53");
});

test("uses UTC, not the server's local zone", () => {
  // 23:30 on Sunday in New York is already Monday in UTC.
  assert.equal(isoWeekId(at("2026-10-18T23:30:00-04:00")), "2026-W43");
});
