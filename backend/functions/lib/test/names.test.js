"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const names_1 = require("../src/names");
(0, node_test_1.test)("accepts normal names and trims them", () => {
    strict_1.default.deepEqual((0, names_1.checkName)("  Mo_92 "), { ok: true, name: "Mo_92", lower: "mo_92" });
    strict_1.default.equal((0, names_1.checkName)("Ali B").ok, true);
});
(0, node_test_1.test)("enforces length, characters and spacing", () => {
    strict_1.default.deepEqual((0, names_1.checkName)("Mo"), { ok: false, reason: "length" });
    strict_1.default.deepEqual((0, names_1.checkName)("A".repeat(17)), { ok: false, reason: "length" });
    strict_1.default.deepEqual((0, names_1.checkName)("Mo-92"), { ok: false, reason: "characters" });
    strict_1.default.deepEqual((0, names_1.checkName)("Mo😀92"), { ok: false, reason: "characters" });
    strict_1.default.deepEqual((0, names_1.checkName)("Mo  92"), { ok: false, reason: "spaces" });
});
(0, node_test_1.test)("undoes leetspeak before filtering", () => {
    strict_1.default.equal((0, names_1.normalizeForFilter)("Sh1t_$tack"), "shitstack");
    strict_1.default.equal((0, names_1.isBanned)("Sh1t"), true);
    strict_1.default.equal((0, names_1.isBanned)("F_U_C_K"), true);
    strict_1.default.equal((0, names_1.isBanned)("fuuuuuck"), true);
});
(0, node_test_1.test)("whole-word list does not block innocent names", () => {
    strict_1.default.equal((0, names_1.isBanned)("Cassie"), false);
    strict_1.default.equal((0, names_1.isBanned)("Grape Ape"), false);
    strict_1.default.equal((0, names_1.isBanned)("Dickens"), false);
    strict_1.default.equal((0, names_1.isBanned)("Nazir"), false);
    strict_1.default.equal((0, names_1.isBanned)("Big Ass"), true);
    strict_1.default.equal((0, names_1.isBanned)("Admin"), true);
});
(0, node_test_1.test)("suggestions are valid, distinct and differ from the taken name", () => {
    const s = (0, names_1.suggestions)("Mo_92", () => 0);
    strict_1.default.deepEqual(s.slice(0, 2), ["Mo_921", "Mo_92x"]);
    for (const n of s)
        strict_1.default.equal((0, names_1.checkName)(n).ok, true);
    strict_1.default.equal(new Set(s.map((n) => n.toLowerCase())).size, s.length);
});
(0, node_test_1.test)("suggestions fit within 16 characters", () => {
    for (const n of (0, names_1.suggestions)("SixteenCharsName", () => 0.99))
        strict_1.default.ok(n.length <= 16, n);
});
//# sourceMappingURL=names.test.js.map