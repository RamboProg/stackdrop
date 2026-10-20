"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.deleteAccount = void 0;
const auth_1 = require("firebase-admin/auth");
const logger = __importStar(require("firebase-functions/logger"));
const https_1 = require("firebase-functions/v2/https");
const common_1 = require("./common");
/**
 * Permanently deletes everything the caller owns, as Google Play requires: public profile, name and
 * friend-code indexes, friendships on both sides, friend requests, leaderboard entries, the cloud
 * save, private bookkeeping, and finally the Auth user itself.
 */
exports.deleteAccount = (0, https_1.onCall)(common_1.CALLABLE, async (req) => {
    const uid = (0, common_1.requireAuth)(req);
    const writer = common_1.db.bulkWriter();
    const profileRef = common_1.db.doc(`profiles/${uid}`);
    const profile = await profileRef.get();
    if (profile.exists) {
        const nameLower = profile.get("nameLower");
        const code = profile.get("friendCode");
        if (nameLower)
            await deleteIfOwned(writer, `usernames/${nameLower}`, uid);
        if (code)
            await deleteIfOwned(writer, `friendCodes/${code}`, uid);
        writer.delete(profileRef);
    }
    const friends = await common_1.db.collection(`users/${uid}/friends`).listDocuments();
    for (const f of friends)
        writer.delete(common_1.db.doc(`users/${f.id}/friends/${uid}`));
    const [sent, received] = await Promise.all([
        common_1.db.collection("friendRequests").where("from", "==", uid).get(),
        common_1.db.collection("friendRequests").where("to", "==", uid).get(),
    ]);
    for (const r of [...sent.docs, ...received.docs])
        writer.delete(r.ref);
    // Board documents may be "missing" (only their entries exist); listDocuments still returns them.
    const boards = await common_1.db.collection("leaderboards").listDocuments();
    for (const b of boards)
        writer.delete(b.collection("entries").doc(uid));
    writer.delete(common_1.db.doc(`private/${uid}`));
    await writer.close();
    // Removes users/{uid} together with its friends subcollection.
    await common_1.db.recursiveDelete(common_1.db.doc(`users/${uid}`));
    try {
        await (0, auth_1.getAuth)().deleteUser(uid);
    }
    catch (e) {
        if (e.code !== "auth/user-not-found")
            throw e;
    }
    logger.info("Account deleted", { uid });
    return { ok: true };
});
async function deleteIfOwned(writer, path, uid) {
    const snap = await common_1.db.doc(path).get();
    if (snap.exists && snap.get("uid") === uid)
        writer.delete(snap.ref);
}
//# sourceMappingURL=account.js.map