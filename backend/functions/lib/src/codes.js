"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.CODE_LENGTH = exports.CODE_ALPHABET = void 0;
exports.newFriendCode = newFriendCode;
exports.normalizeCode = normalizeCode;
exports.formatCode = formatCode;
exports.defaultAvatar = defaultAvatar;
const node_crypto_1 = require("node:crypto");
const config_1 = require("./config");
/** Crockford base32: digits plus letters without I, L, O and U, so codes are easy to read aloud. */
exports.CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
exports.CODE_LENGTH = 8;
function newFriendCode() {
    let code = "";
    for (let i = 0; i < exports.CODE_LENGTH; i++)
        code += exports.CODE_ALPHABET[(0, node_crypto_1.randomInt)(exports.CODE_ALPHABET.length)];
    return code;
}
/**
 * Accepts what a player types: any case, with or without the dash or spaces, and the usual
 * Crockford look-alikes (O for 0, I or L for 1). Returns the stored form, or null if invalid.
 */
function normalizeCode(input) {
    const s = input.toUpperCase().replace(/[\s-]/g, "").replace(/O/g, "0").replace(/[IL]/g, "1");
    if (s.length !== exports.CODE_LENGTH)
        return null;
    for (const ch of s)
        if (!exports.CODE_ALPHABET.includes(ch))
            return null;
    return s;
}
/** Display form, e.g. "K7Q2-M9XD". */
function formatCode(code) {
    return `${code.slice(0, 4)}-${code.slice(4)}`;
}
/** Same hash the app uses, so a guest keeps the same avatar after choosing a name. */
function defaultAvatar(uid) {
    let sum = 0;
    for (const ch of uid)
        sum += ch.charCodeAt(0);
    return sum % config_1.AVATAR_COUNT;
}
//# sourceMappingURL=codes.js.map