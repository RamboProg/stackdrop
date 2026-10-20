"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.removeFriend = exports.respondFriendRequest = exports.sendFriendRequest = exports.MAX_OUTGOING = exports.MAX_FRIENDS = void 0;
const firestore_1 = require("firebase-admin/firestore");
const https_1 = require("firebase-functions/v2/https");
const codes_1 = require("./codes");
const common_1 = require("./common");
exports.MAX_FRIENDS = 100;
exports.MAX_OUTGOING = 20;
const requestId = (from, to) => `${from}_${to}`;
const friendRef = (owner, friend) => common_1.db.doc(`users/${owner}/friends/${friend}`);
/**
 * Sends a friend request by friend code or by uid (from a leaderboard row). If the other player has
 * already asked the caller, the two simply become friends.
 */
exports.sendFriendRequest = (0, https_1.onCall)(common_1.CALLABLE, async (req) => {
    const uid = (0, common_1.requireAuth)(req);
    const data = (0, common_1.payload)(req);
    const target = await resolveTarget(data);
    if (!target)
        return { ok: false, reason: "not-found" };
    if (target === uid)
        return { ok: false, reason: "self" };
    const [me, them] = await Promise.all([common_1.db.doc(`profiles/${uid}`).get(), common_1.db.doc(`profiles/${target}`).get()]);
    if (!me.exists)
        throw new https_1.HttpsError("failed-precondition", "profile-required");
    if (!them.exists)
        return { ok: false, reason: "not-found" };
    const theirName = them.get("name");
    if ((await friendRef(uid, target).get()).exists)
        return { ok: false, reason: "already-friends" };
    if (await friendCount(uid) >= exports.MAX_FRIENDS || await friendCount(target) >= exports.MAX_FRIENDS) {
        return { ok: false, reason: "too-many-friends" };
    }
    const reverse = common_1.db.doc(`friendRequests/${requestId(target, uid)}`);
    if ((await reverse.get()).exists) {
        await befriend(uid, target, reverse);
        return { ok: true, status: "friends", name: theirName };
    }
    const forward = common_1.db.doc(`friendRequests/${requestId(uid, target)}`);
    if ((await forward.get()).exists)
        return { ok: true, status: "already-sent", name: theirName };
    const outgoing = await common_1.db.collection("friendRequests").where("from", "==", uid).count().get();
    if (outgoing.data().count >= exports.MAX_OUTGOING)
        return { ok: false, reason: "too-many-requests" };
    await forward.set({ from: uid, to: target, fromName: me.get("name"), createdAt: firestore_1.FieldValue.serverTimestamp() });
    return { ok: true, status: "sent", name: theirName };
});
/** Accepts or declines a request someone sent to the caller. */
exports.respondFriendRequest = (0, https_1.onCall)(common_1.CALLABLE, async (req) => {
    const uid = (0, common_1.requireAuth)(req);
    const data = (0, common_1.payload)(req);
    const fromUid = data.fromUid;
    const accept = data.accept;
    if (typeof fromUid !== "string" || fromUid.length === 0 || typeof accept !== "boolean") {
        throw new https_1.HttpsError("invalid-argument", "bad-request");
    }
    const request = common_1.db.doc(`friendRequests/${requestId(fromUid, uid)}`);
    if (!(await request.get()).exists)
        return { ok: false, reason: "not-found" };
    if (!accept) {
        await request.delete();
        return { ok: true };
    }
    if (await friendCount(uid) >= exports.MAX_FRIENDS || await friendCount(fromUid) >= exports.MAX_FRIENDS) {
        return { ok: false, reason: "too-many-friends" };
    }
    await befriend(uid, fromUid, request);
    return { ok: true };
});
/** Ends a friendship on both sides, along with any requests still pending between the two. */
exports.removeFriend = (0, https_1.onCall)(common_1.CALLABLE, async (req) => {
    const uid = (0, common_1.requireAuth)(req);
    const other = (0, common_1.payload)(req).uid;
    if (typeof other !== "string" || other.length === 0 || other === uid) {
        throw new https_1.HttpsError("invalid-argument", "bad-uid");
    }
    const batch = common_1.db.batch();
    batch.delete(friendRef(uid, other));
    batch.delete(friendRef(other, uid));
    batch.delete(common_1.db.doc(`friendRequests/${requestId(uid, other)}`));
    batch.delete(common_1.db.doc(`friendRequests/${requestId(other, uid)}`));
    await batch.commit();
    return { ok: true };
});
async function resolveTarget(data) {
    if (typeof data.uid === "string" && data.uid.length > 0)
        return data.uid;
    if (typeof data.code !== "string")
        throw new https_1.HttpsError("invalid-argument", "code-or-uid");
    const code = (0, codes_1.normalizeCode)(data.code);
    if (!code)
        return null;
    const snap = await common_1.db.doc(`friendCodes/${code}`).get();
    return snap.exists ? snap.get("uid") : null;
}
async function friendCount(uid) {
    return (await common_1.db.collection(`users/${uid}/friends`).count().get()).data().count;
}
async function befriend(a, b, request) {
    const batch = common_1.db.batch();
    const since = firestore_1.FieldValue.serverTimestamp();
    batch.set(friendRef(a, b), { since });
    batch.set(friendRef(b, a), { since });
    batch.delete(request);
    await batch.commit();
}
//# sourceMappingURL=friends.js.map