"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const codes_1 = require("../src/codes");
(0, node_test_1.test)("friend codes use the Crockford alphabet", () => {
    strict_1.default.equal(codes_1.CODE_ALPHABET.length, 32);
    for (const banned of "ILOU")
        strict_1.default.equal(codes_1.CODE_ALPHABET.includes(banned), false);
    for (let i = 0; i < 200; i++) {
        const code = (0, codes_1.newFriendCode)();
        strict_1.default.equal(code.length, 8);
        for (const ch of code)
            strict_1.default.ok(codes_1.CODE_ALPHABET.includes(ch), code);
    }
});
(0, node_test_1.test)("typed codes are normalized", () => {
    strict_1.default.equal((0, codes_1.normalizeCode)("k7q2-m9xd"), "K7Q2M9XD");
    strict_1.default.equal((0, codes_1.normalizeCode)(" K7Q2 M9XD "), "K7Q2M9XD");
    strict_1.default.equal((0, codes_1.normalizeCode)("O0IL-1234"), "00111234");
    strict_1.default.equal((0, codes_1.normalizeCode)("K7Q2-M9X"), null);
    strict_1.default.equal((0, codes_1.normalizeCode)("K7Q2-M9XU"), null);
});
(0, node_test_1.test)("codes display with a dash", () => {
    strict_1.default.equal((0, codes_1.formatCode)("K7Q2M9XD"), "K7Q2-M9XD");
});
(0, node_test_1.test)("default avatar matches the app's hash", () => {
    // "abc" = 97 + 98 + 99 = 294, and 294 % 12 = 6. AvatarsTest in the app checks the same value.
    strict_1.default.equal((0, codes_1.defaultAvatar)("abc"), 6);
});
//# sourceMappingURL=codes.test.js.map