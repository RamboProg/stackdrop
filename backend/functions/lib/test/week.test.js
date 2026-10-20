"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const week_1 = require("../src/week");
const at = (iso) => new Date(iso);
(0, node_test_1.test)("weeks start on Monday in UTC", () => {
    strict_1.default.equal((0, week_1.isoWeekId)(at("2026-10-18T23:59:59Z")), "2026-W42");
    strict_1.default.equal((0, week_1.isoWeekId)(at("2026-10-19T00:00:00Z")), "2026-W43");
});
(0, node_test_1.test)("year boundaries follow ISO-8601", () => {
    strict_1.default.equal((0, week_1.isoWeekId)(at("2026-01-01T12:00:00Z")), "2026-W01");
    strict_1.default.equal((0, week_1.isoWeekId)(at("2025-12-29T00:00:00Z")), "2026-W01");
    strict_1.default.equal((0, week_1.isoWeekId)(at("2024-12-30T00:00:00Z")), "2025-W01");
    strict_1.default.equal((0, week_1.isoWeekId)(at("2021-01-03T00:00:00Z")), "2020-W53");
    strict_1.default.equal((0, week_1.isoWeekId)(at("2020-12-31T00:00:00Z")), "2020-W53");
});
(0, node_test_1.test)("uses UTC, not the server's local zone", () => {
    // 23:30 on Sunday in New York is already Monday in UTC.
    strict_1.default.equal((0, week_1.isoWeekId)(at("2026-10-18T23:30:00-04:00")), "2026-W43");
});
//# sourceMappingURL=week.test.js.map