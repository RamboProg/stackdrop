"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
// End-to-end checks of the callable functions against the Firestore and Auth emulators. The
// handlers are called through `.run()`, which skips the HTTP layer (and so App Check), letting the
// tests act as several players. Run with `npm run test:integration`.
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const auth_1 = require("firebase-admin/auth");
const firestore_1 = require("firebase-admin/firestore");
const common_1 = require("../src/common");
const index_1 = require("../src/index");
function as(uid) {
    return (fn, data = {}) => fn.run({ data, auth: { uid, token: {} }, rawRequest: {} });
}
const run = (over = {}) => ({
    mode: "tower", score: 12, durationMs: 60_000, drops: 15, perfects: 4, bestCombo: 2, revived: false,
    runId: `run-${Math.random().toString(36).slice(2, 12)}`, ...over,
});
async function clearFirestore() {
    for (const c of await common_1.db.listCollections())
        await common_1.db.recursiveDelete(c);
}
async function code(uid) {
    return (await common_1.db.doc(`profiles/${uid}`).get()).get("friendCode");
}
/** submitScore allows one run per 5 s; tests move the clock back instead of waiting. */
async function forgetLastSubmit(uid) {
    await common_1.db.doc(`private/${uid}`).set({ lastSubmitAt: firestore_1.Timestamp.fromMillis(0) }, { merge: true });
}
(0, node_test_1.before)(async () => {
    strict_1.default.ok(process.env.FIRESTORE_EMULATOR_HOST, "run through `npm run test:integration`");
});
(0, node_test_1.beforeEach)(clearFirestore);
(0, node_test_1.after)(clearFirestore);
(0, node_test_1.test)("claimName creates a profile, keeps names unique and offers suggestions", async () => {
    const r = await as("alice")(index_1.claimName, { name: "Mo_92", avatar: 3 });
    strict_1.default.deepEqual(r, { ok: true });
    const profile = (await common_1.db.doc("profiles/alice").get()).data();
    strict_1.default.equal(profile.name, "Mo_92");
    strict_1.default.equal(profile.avatar, 3);
    strict_1.default.match(profile.friendCode, /^[0-9A-HJKMNP-TV-Z]{8}$/);
    strict_1.default.equal((await common_1.db.doc("usernames/mo_92").get()).get("uid"), "alice");
    const taken = await as("bob")(index_1.claimName, { name: "mo_92" });
    strict_1.default.equal(taken.ok, false);
    strict_1.default.ok(!taken.ok && taken.reason === "taken" && (taken.suggestions?.length ?? 0) > 0);
    await as("alice")(index_1.claimName, { name: "Alice" });
    strict_1.default.equal((await common_1.db.doc("usernames/mo_92").get()).exists, false, "old name released");
    await as("alice")(index_1.claimName, { name: "Alice2" });
    const limited = await as("alice")(index_1.claimName, { name: "Alice3" });
    strict_1.default.deepEqual(limited, { ok: false, reason: "rate-limited" });
    await as("alice")(index_1.setAvatar, { avatar: 7 });
    strict_1.default.equal((await common_1.db.doc("profiles/alice").get()).get("avatar"), 7);
});
(0, node_test_1.test)("friend requests by code, auto-accept, and removal on both sides", async () => {
    await as("alice")(index_1.claimName, { name: "Alice" });
    await as("bob")(index_1.claimName, { name: "Bob" });
    strict_1.default.deepEqual(await as("alice")(index_1.sendFriendRequest, { code: await code("alice") }), { ok: false, reason: "self" });
    strict_1.default.deepEqual(await as("alice")(index_1.sendFriendRequest, { code: "ZZZZ-ZZZZ" }), { ok: false, reason: "not-found" });
    const bobCode = (await code("bob")).toLowerCase();
    const sent = await as("alice")(index_1.sendFriendRequest, { code: `${bobCode.slice(0, 4)}-${bobCode.slice(4)}` });
    strict_1.default.deepEqual(sent, { ok: true, status: "sent", name: "Bob" });
    strict_1.default.equal((await common_1.db.doc("friendRequests/alice_bob").get()).get("fromName"), "Alice");
    strict_1.default.deepEqual(await as("alice")(index_1.sendFriendRequest, { uid: "bob" }), { ok: true, status: "already-sent", name: "Bob" });
    // Bob asking Alice back makes them friends straight away.
    strict_1.default.deepEqual(await as("bob")(index_1.sendFriendRequest, { uid: "alice" }), { ok: true, status: "friends", name: "Alice" });
    strict_1.default.equal((await common_1.db.doc("users/alice/friends/bob").get()).exists, true);
    strict_1.default.equal((await common_1.db.doc("users/bob/friends/alice").get()).exists, true);
    strict_1.default.equal((await common_1.db.doc("friendRequests/alice_bob").get()).exists, false);
    strict_1.default.deepEqual(await as("alice")(index_1.sendFriendRequest, { uid: "bob" }), { ok: false, reason: "already-friends" });
    await as("bob")(index_1.removeFriend, { uid: "alice" });
    strict_1.default.equal((await common_1.db.doc("users/alice/friends/bob").get()).exists, false);
    strict_1.default.equal((await common_1.db.doc("users/bob/friends/alice").get()).exists, false);
});
(0, node_test_1.test)("requests can be accepted or declined", async () => {
    for (const [uid, name] of [["alice", "Alice"], ["bob", "Bob"], ["cara", "Cara"]]) {
        await as(uid)(index_1.claimName, { name });
    }
    await as("bob")(index_1.sendFriendRequest, { uid: "alice" });
    await as("cara")(index_1.sendFriendRequest, { uid: "alice" });
    strict_1.default.deepEqual(await as("alice")(index_1.respondFriendRequest, { fromUid: "bob", accept: true }), { ok: true });
    strict_1.default.equal((await common_1.db.doc("users/alice/friends/bob").get()).exists, true);
    strict_1.default.deepEqual(await as("alice")(index_1.respondFriendRequest, { fromUid: "cara", accept: false }), { ok: true });
    strict_1.default.equal((await common_1.db.doc("users/alice/friends/cara").get()).exists, false);
    strict_1.default.equal((await common_1.db.doc("friendRequests/cara_alice").get()).exists, false);
    strict_1.default.deepEqual(await as("alice")(index_1.respondFriendRequest, { fromUid: "cara", accept: true }), { ok: false, reason: "not-found" });
});
(0, node_test_1.test)("submitScore validates, ranks, rate-limits and ignores repeats", async () => {
    await strict_1.default.rejects(as("alice")(index_1.submitScore, run()), { code: "failed-precondition" });
    await as("alice")(index_1.claimName, { name: "Alice" });
    await as("bob")(index_1.claimName, { name: "Bob" });
    const first = run({ score: 12 });
    const r1 = await as("alice")(index_1.submitScore, first);
    strict_1.default.equal(r1.newAllTime, true);
    strict_1.default.equal(r1.newWeekly, true);
    strict_1.default.equal(r1.weeklyRank, 1);
    const entry = (await common_1.db.doc("leaderboards/tower_all/entries/alice").get()).data();
    strict_1.default.equal(entry.score, 12);
    strict_1.default.equal(entry.name, "Alice");
    strict_1.default.equal((await common_1.db.doc("profiles/alice").get()).get("bests.tower"), 12);
    await strict_1.default.rejects(as("alice")(index_1.submitScore, run({ score: 13, drops: 16 })), { code: "resource-exhausted" });
    await forgetLastSubmit("alice");
    const repeat = await as("alice")(index_1.submitScore, first);
    strict_1.default.equal(repeat.duplicate, true);
    await forgetLastSubmit("alice");
    const lower = await as("alice")(index_1.submitScore, run({ score: 5 }));
    strict_1.default.equal(lower.newAllTime, false);
    strict_1.default.equal((await common_1.db.doc("leaderboards/tower_all/entries/alice").get()).get("score"), 12);
    await as("bob")(index_1.submitScore, run({ score: 20, drops: 24 }));
    await forgetLastSubmit("alice");
    const ranked = await as("alice")(index_1.submitScore, run({ score: 11 }));
    strict_1.default.equal(ranked.weeklyRank, 2);
    await forgetLastSubmit("alice");
    await strict_1.default.rejects(as("alice")(index_1.submitScore, run({ score: 30, drops: 30, durationMs: 1_000 })), { code: "invalid-argument" });
    await strict_1.default.rejects(as("alice")(index_1.submitScore, run({ score: 40, drops: 20 })), { code: "invalid-argument" });
    await forgetLastSubmit("alice");
    await as("alice")(index_1.submitScore, run({ score: 40, drops: 40, perfects: 40, bestCombo: 40, durationMs: 120_000 }));
    strict_1.default.equal((await common_1.db.doc("private/alice").get()).get("flags"), 1, "nearly perfect run is flagged");
});
(0, node_test_1.test)("deleteAccount removes everything the player owns", async () => {
    await (0, auth_1.getAuth)().createUser({ uid: "alice" });
    await as("alice")(index_1.claimName, { name: "Alice" });
    await as("bob")(index_1.claimName, { name: "Bob" });
    await as("cara")(index_1.claimName, { name: "Cara" });
    const aliceCode = await code("alice");
    await as("alice")(index_1.sendFriendRequest, { uid: "bob" });
    await as("bob")(index_1.sendFriendRequest, { uid: "alice" });
    await as("cara")(index_1.sendFriendRequest, { uid: "alice" });
    await as("alice")(index_1.submitScore, run());
    await common_1.db.doc("users/alice").set({ rev: 1, gems: 5 });
    strict_1.default.deepEqual(await as("alice")(index_1.deleteAccount), { ok: true });
    for (const path of [
        "profiles/alice", "usernames/alice", `friendCodes/${aliceCode}`, "users/alice", "users/alice/friends/bob",
        "users/bob/friends/alice", "friendRequests/cara_alice", "leaderboards/tower_all/entries/alice", "private/alice",
    ]) {
        strict_1.default.equal((await common_1.db.doc(path).get()).exists, false, `${path} should be gone`);
    }
    strict_1.default.equal((await common_1.db.doc("profiles/bob").get()).exists, true, "other players are untouched");
    await strict_1.default.rejects((0, auth_1.getAuth)().getUser("alice"), { code: "auth/user-not-found" });
});
//# sourceMappingURL=integration.spec.js.map