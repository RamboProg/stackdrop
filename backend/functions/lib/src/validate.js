"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.MIN_DROP_MS = exports.MAX_SCORE = void 0;
exports.parseRun = parseRun;
exports.checkRun = checkRun;
const config_1 = require("./config");
exports.MAX_SCORE = 5000;
/** Fastest believable time per drop, tuned from real play. */
exports.MIN_DROP_MS = { stack: 220, tower: 650 };
const RUN_ID = /^[A-Za-z0-9-]{8,64}$/;
/** Parses an untrusted payload; returns null when any field has the wrong type or shape. */
function parseRun(data) {
    const { mode, score, durationMs, drops, perfects, bestCombo, revived, runId } = data;
    const isInt = (v) => typeof v === "number" && Number.isInteger(v) && v >= 0;
    if (typeof mode !== "string" || !config_1.MODES.includes(mode))
        return null;
    if (!isInt(score) || !isInt(durationMs) || !isInt(drops) || !isInt(perfects) || !isInt(bestCombo))
        return null;
    if (typeof revived !== "boolean" || typeof runId !== "string" || !RUN_ID.test(runId))
        return null;
    return { mode: mode, score, durationMs, drops, perfects, bestCombo, revived, runId };
}
/**
 * Impossible runs are rejected. Runs that are possible but unusual are accepted and flagged, so they
 * can be reviewed later without punishing a genuinely great player.
 */
function checkRun(r) {
    if (r.score > exports.MAX_SCORE)
        return { ok: false, reason: "score-range" };
    if (r.score > r.drops)
        return { ok: false, reason: "score-exceeds-drops" };
    if (r.perfects > r.drops)
        return { ok: false, reason: "perfects-exceed-drops" };
    if (r.bestCombo > r.perfects)
        return { ok: false, reason: "combo-exceeds-perfects" };
    const minMs = r.drops * exports.MIN_DROP_MS[r.mode];
    if (r.durationMs < minMs)
        return { ok: false, reason: "too-fast" };
    const suspicious = (r.drops >= 30 && r.perfects / r.drops > 0.95) || r.durationMs < minMs * 1.5;
    return { ok: true, suspicious };
}
//# sourceMappingURL=validate.js.map