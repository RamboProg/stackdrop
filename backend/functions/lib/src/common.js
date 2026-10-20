"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.CALLABLE = exports.db = void 0;
exports.requireAuth = requireAuth;
exports.payload = payload;
exports.stringField = stringField;
exports.intField = intField;
exports.optionalIntField = optionalIntField;
const app_1 = require("firebase-admin/app");
const firestore_1 = require("firebase-admin/firestore");
const https_1 = require("firebase-functions/v2/https");
(0, app_1.initializeApp)();
exports.db = (0, firestore_1.getFirestore)();
/** Every callable uses these options: App Check is required, so only the genuine app can call. */
exports.CALLABLE = { enforceAppCheck: true };
function requireAuth(req) {
    const uid = req.auth?.uid;
    if (!uid)
        throw new https_1.HttpsError("unauthenticated", "sign-in-required");
    return uid;
}
/** The request payload as a plain object, or an invalid-argument error. */
function payload(req) {
    const data = req.data;
    if (typeof data !== "object" || data === null || Array.isArray(data)) {
        throw new https_1.HttpsError("invalid-argument", "bad-payload");
    }
    return data;
}
function stringField(data, key) {
    const v = data[key];
    if (typeof v !== "string")
        throw new https_1.HttpsError("invalid-argument", `bad-${key}`);
    return v;
}
function intField(data, key, min, max) {
    const v = data[key];
    if (typeof v !== "number" || !Number.isInteger(v) || v < min || v > max) {
        throw new https_1.HttpsError("invalid-argument", `bad-${key}`);
    }
    return v;
}
function optionalIntField(data, key, min, max) {
    return data[key] === undefined || data[key] === null ? undefined : intField(data, key, min, max);
}
//# sourceMappingURL=common.js.map