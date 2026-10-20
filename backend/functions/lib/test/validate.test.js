"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const validate_1 = require("../src/validate");
const run = (over = {}) => ({
    mode: "tower", score: 20, durationMs: 60_000, drops: 24, perfects: 8, bestCombo: 3,
    revived: false, runId: "8f2c1a7e-1111-2222-3333-444455556666", ...over,
});
(0, node_test_1.test)("a normal run passes unflagged", () => {
    strict_1.default.deepEqual((0, validate_1.checkRun)(run()), { ok: true, suspicious: false });
});
(0, node_test_1.test)("impossible runs are rejected", () => {
    strict_1.default.equal((0, validate_1.checkRun)(run({ score: 5001, drops: 6000, durationMs: 9e9 })).ok, false);
    strict_1.default.equal((0, validate_1.checkRun)(run({ score: 30, drops: 24 })).ok, false);
    strict_1.default.equal((0, validate_1.checkRun)(run({ perfects: 25 })).ok, false);
    strict_1.default.equal((0, validate_1.checkRun)(run({ bestCombo: 9 })).ok, false);
    // Tower needs at least 650 ms per drop: 24 drops cannot happen in 10 seconds.
    strict_1.default.deepEqual((0, validate_1.checkRun)(run({ durationMs: 10_000 })), { ok: false, reason: "too-fast" });
    // Stack allows 220 ms per drop.
    strict_1.default.equal((0, validate_1.checkRun)(run({ mode: "stack", durationMs: 10_000, perfects: 3, bestCombo: 2 })).ok, true);
});
(0, node_test_1.test)("unusual but possible runs are flagged, not rejected", () => {
    const nearlyAllPerfect = (0, validate_1.checkRun)(run({ score: 40, drops: 40, perfects: 40, bestCombo: 40, durationMs: 120_000 }));
    strict_1.default.deepEqual(nearlyAllPerfect, { ok: true, suspicious: true });
    const veryFast = (0, validate_1.checkRun)(run({ durationMs: 20_000 }));
    strict_1.default.deepEqual(veryFast, { ok: true, suspicious: true });
});
(0, node_test_1.test)("payloads with wrong types or shapes are refused", () => {
    strict_1.default.notEqual((0, validate_1.parseRun)({ ...run() }), null);
    strict_1.default.equal((0, validate_1.parseRun)({ ...run(), mode: "chess" }), null);
    strict_1.default.equal((0, validate_1.parseRun)({ ...run(), score: 1.5 }), null);
    strict_1.default.equal((0, validate_1.parseRun)({ ...run(), drops: -1 }), null);
    strict_1.default.equal((0, validate_1.parseRun)({ ...run(), revived: "no" }), null);
    strict_1.default.equal((0, validate_1.parseRun)({ ...run(), runId: "x" }), null);
    strict_1.default.equal((0, validate_1.parseRun)({ ...run(), runId: "../../etc" }), null);
});
//# sourceMappingURL=validate.test.js.map