// End-to-end checks of the callable functions against the Firestore and Auth emulators. The
// handlers are called through `.run()`, which skips the HTTP layer (and so App Check), letting the
// tests act as several players. Run with `npm run test:integration`.
import assert from "node:assert/strict";
import { after, before, beforeEach, test } from "node:test";
import { getAuth } from "firebase-admin/auth";
import { Timestamp } from "firebase-admin/firestore";
import type { CallableRequest } from "firebase-functions/v2/https";
import { db } from "../src/common";
import { isoWeekId } from "../src/week";
import {
  claimName, deleteAccount, deleteGuest, removeFriend, respondFriendRequest, sendFriendRequest, setAvatar, startRun,
  submitScore,
} from "../src/index";

type Callable<T> = { run: (req: CallableRequest<unknown>) => T };

function as(uid: string) {
  return <T>(fn: Callable<T>, data: Record<string, unknown> = {}): T =>
    fn.run({ data, auth: { uid, token: {} }, rawRequest: {} } as unknown as CallableRequest<unknown>);
}

const run = (over: Record<string, unknown> = {}) => ({
  mode: "tower", score: 12, durationMs: 60_000, drops: 15, perfects: 4, bestCombo: 2, revived: false,
  runId: `run-${Math.random().toString(36).slice(2, 12)}`, ...over,
});

type Run = ReturnType<typeof run>;

/** Records the run as started on the server long enough ago to cover its duration, then submits it. */
async function submit(uid: string, r: Run) {
  const ref = db.doc(`runs/${r.runId}`);
  if (!(await ref.get()).exists) {
    await ref.set({ uid, mode: r.mode, used: false, startedAt: Timestamp.fromMillis(Date.now() - r.durationMs - 1_000) });
  }
  return as(uid)(submitScore, r);
}

async function clearFirestore(): Promise<void> {
  for (const c of await db.listCollections()) await db.recursiveDelete(c);
}

async function code(uid: string): Promise<string> {
  return (await db.doc(`profiles/${uid}`).get()).get("friendCode") as string;
}

/** submitScore allows one run per 5 s; tests move the clock back instead of waiting. */
async function forgetLastSubmit(uid: string): Promise<void> {
  await db.doc(`private/${uid}`).set({ lastSubmitAt: Timestamp.fromMillis(0) }, { merge: true });
}

before(async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "run through `npm run test:integration`");
});

beforeEach(clearFirestore);
after(clearFirestore);

test("claimName creates a profile, keeps names unique and offers suggestions", async () => {
  const r = await as("alice")(claimName, { name: "Mo_92", avatar: 3 });
  assert.deepEqual(r, { ok: true });
  const profile = (await db.doc("profiles/alice").get()).data()!;
  assert.equal(profile.name, "Mo_92");
  assert.equal(profile.avatar, 3);
  assert.match(profile.friendCode, /^[0-9A-HJKMNP-TV-Z]{8}$/);
  assert.equal((await db.doc("usernames/mo_92").get()).get("uid"), "alice");

  const taken = await as("bob")(claimName, { name: "mo_92" });
  assert.equal(taken.ok, false);
  assert.ok(!taken.ok && taken.reason === "taken" && (taken.suggestions?.length ?? 0) > 0);

  await as("alice")(claimName, { name: "Alice" });
  assert.equal((await db.doc("usernames/mo_92").get()).exists, false, "old name released");
  await as("alice")(claimName, { name: "Alice2" });
  const limited = await as("alice")(claimName, { name: "Alice3" });
  assert.deepEqual(limited, { ok: false, reason: "rate-limited" });

  await as("alice")(setAvatar, { avatar: 7 });
  assert.equal((await db.doc("profiles/alice").get()).get("avatar"), 7);
});

test("friend requests by code, auto-accept, and removal on both sides", async () => {
  await as("alice")(claimName, { name: "Alice" });
  await as("bob")(claimName, { name: "Bob" });

  assert.deepEqual(await as("alice")(sendFriendRequest, { code: await code("alice") }), { ok: false, reason: "self" });
  assert.deepEqual(await as("alice")(sendFriendRequest, { code: "ZZZZ-ZZZZ" }), { ok: false, reason: "not-found" });

  const bobCode = (await code("bob")).toLowerCase();
  const sent = await as("alice")(sendFriendRequest, { code: `${bobCode.slice(0, 4)}-${bobCode.slice(4)}` });
  assert.deepEqual(sent, { ok: true, status: "sent", name: "Bob" });
  assert.equal((await db.doc("friendRequests/alice_bob").get()).get("fromName"), "Alice");
  assert.deepEqual(await as("alice")(sendFriendRequest, { uid: "bob" }), { ok: true, status: "already-sent", name: "Bob" });

  // Bob asking Alice back makes them friends straight away.
  assert.deepEqual(await as("bob")(sendFriendRequest, { uid: "alice" }), { ok: true, status: "friends", name: "Alice" });
  assert.equal((await db.doc("users/alice/friends/bob").get()).exists, true);
  assert.equal((await db.doc("users/bob/friends/alice").get()).exists, true);
  assert.equal((await db.doc("friendRequests/alice_bob").get()).exists, false);
  assert.deepEqual(await as("alice")(sendFriendRequest, { uid: "bob" }), { ok: false, reason: "already-friends" });

  await as("bob")(removeFriend, { uid: "alice" });
  assert.equal((await db.doc("users/alice/friends/bob").get()).exists, false);
  assert.equal((await db.doc("users/bob/friends/alice").get()).exists, false);
});

test("requests can be accepted or declined", async () => {
  for (const [uid, name] of [["alice", "Alice"], ["bob", "Bob"], ["cara", "Cara"]]) {
    await as(uid)(claimName, { name });
  }
  await as("bob")(sendFriendRequest, { uid: "alice" });
  await as("cara")(sendFriendRequest, { uid: "alice" });

  assert.deepEqual(await as("alice")(respondFriendRequest, { fromUid: "bob", accept: true }), { ok: true });
  assert.equal((await db.doc("users/alice/friends/bob").get()).exists, true);
  assert.deepEqual(await as("alice")(respondFriendRequest, { fromUid: "cara", accept: false }), { ok: true });
  assert.equal((await db.doc("users/alice/friends/cara").get()).exists, false);
  assert.equal((await db.doc("friendRequests/cara_alice").get()).exists, false);
  assert.deepEqual(await as("alice")(respondFriendRequest, { fromUid: "cara", accept: true }), { ok: false, reason: "not-found" });
});

test("submitScore validates, ranks, rate-limits and ignores repeats", async () => {
  await assert.rejects(submit("alice", run()), { code: "failed-precondition" });
  await as("alice")(claimName, { name: "Alice" });
  await as("bob")(claimName, { name: "Bob" });

  const first = run({ score: 12 });
  const r1 = await submit("alice", first);
  assert.equal(r1.newAllTime, true);
  assert.equal(r1.newWeekly, true);
  assert.equal(r1.weeklyRank, 1);
  const entry = (await db.doc("leaderboards/tower_all/entries/alice").get()).data()!;
  assert.equal(entry.score, 12);
  assert.equal(entry.name, "Alice");
  assert.equal((await db.doc("profiles/alice").get()).get("bests.tower"), 12);

  await assert.rejects(submit("alice", run({ score: 13, drops: 16 })), { code: "resource-exhausted" });
  await forgetLastSubmit("alice");
  const repeat = await submit("alice", first);
  assert.equal(repeat.duplicate, true);

  await forgetLastSubmit("alice");
  const lower = await submit("alice", run({ score: 5 }));
  assert.equal(lower.newAllTime, false);
  assert.equal((await db.doc("leaderboards/tower_all/entries/alice").get()).get("score"), 12);

  await submit("bob", run({ score: 20, drops: 24 }));
  await forgetLastSubmit("alice");
  const ranked = await submit("alice", run({ score: 11 }));
  assert.equal(ranked.weeklyRank, 2);

  await forgetLastSubmit("alice");
  await assert.rejects(submit("alice", run({ score: 30, drops: 30, durationMs: 1_000 })), { code: "invalid-argument" });
  await assert.rejects(submit("alice", run({ score: 40, drops: 20 })), { code: "invalid-argument" });

  await forgetLastSubmit("alice");
  await submit("alice", run({ score: 40, drops: 40, perfects: 40, bestCombo: 40, durationMs: 120_000 }));
  assert.equal((await db.doc("private/alice").get()).get("flags"), 1, "nearly perfect run is flagged");
});

test("only runs started on the server, long enough ago, reach the boards", async () => {
  await as("alice")(claimName, { name: "Alice" });

  const offline = run({ score: 30, drops: 40 });
  await assert.rejects(as("alice")(submitScore, offline), { code: "invalid-argument", message: "unknown-run" });

  const live = run({ score: 30, drops: 40 });
  assert.deepEqual(await as("alice")(startRun, { runId: live.runId, mode: "tower" }), { ok: true });
  const started = (await db.doc(`runs/${live.runId}`).get()).data()!;
  assert.equal(started.uid, "alice");
  assert.equal(started.used, false);
  await assert.rejects(as("alice")(startRun, { runId: live.runId, mode: "tower" }), { code: "already-exists" });
  // Claims a minute of play a moment after starting: the server clock disagrees.
  await assert.rejects(as("alice")(submitScore, live), { code: "invalid-argument", message: "clock-mismatch" });

  const stolen = run({ score: 30, drops: 40 });
  await startedBy("bob", stolen);
  await assert.rejects(as("alice")(submitScore, stolen), { code: "invalid-argument", message: "unknown-run" });

  const once = run({ score: 30, drops: 40 });
  await submit("alice", once);
  await db.doc("private/alice").update({ recentRunIds: [], lastSubmitAt: Timestamp.fromMillis(0) });
  await assert.rejects(as("alice")(submitScore, once), { code: "invalid-argument", message: "run-used" });
});

/** Records [r] as started by [uid] (long ago) without submitting it. */
async function startedBy(uid: string, r: Run): Promise<void> {
  await db.doc(`runs/${r.runId}`).set({ uid, mode: r.mode, used: false, startedAt: Timestamp.fromMillis(0) });
}

test("a run sent late counts in the week it was played", async () => {
  await as("alice")(claimName, { name: "Alice" });
  const r = run({ score: 9 });
  // Started two weeks ago and lasted a minute.
  const startedAt = Date.now() - 14 * 24 * 60 * 60 * 1000;
  await db.doc(`runs/${r.runId}`).set({ uid: "alice", mode: "tower", used: false, startedAt: Timestamp.fromMillis(startedAt) });
  const res = await as("alice")(submitScore, r);
  assert.equal(res.week, isoWeekId(new Date(startedAt + r.durationMs)));
  assert.notEqual(res.week, isoWeekId(new Date()));
});

test("deleteAccount removes everything the player owns", async () => {
  await getAuth().createUser({ uid: "alice" });
  await as("alice")(claimName, { name: "Alice" });
  await as("bob")(claimName, { name: "Bob" });
  await as("cara")(claimName, { name: "Cara" });
  const aliceCode = await code("alice");
  await as("alice")(sendFriendRequest, { uid: "bob" });
  await as("bob")(sendFriendRequest, { uid: "alice" });
  await as("cara")(sendFriendRequest, { uid: "alice" });
  await submit("alice", run());
  await db.doc("users/alice").set({ rev: 1, gems: 5 });

  assert.deepEqual(await as("alice")(deleteAccount), { ok: true });

  for (const path of [
    "profiles/alice", "usernames/alice", `friendCodes/${aliceCode}`, "users/alice", "users/alice/friends/bob",
    "users/bob/friends/alice", "friendRequests/cara_alice", "leaderboards/tower_all/entries/alice", "private/alice",
  ]) {
    assert.equal((await db.doc(path).get()).exists, false, `${path} should be gone`);
  }
  assert.equal((await db.doc("profiles/bob").get()).exists, true, "other players are untouched");
  await assert.rejects(getAuth().getUser("alice"), { code: "auth/user-not-found" });
  assert.equal((await db.doc("deleted/alice").get()).exists, true, "marker blocks late cloud-save writes");
  assert.equal((await db.collection("runs").where("uid", "==", "alice").get()).size, 0, "started runs are gone");
});

/** A real anonymous account on the Auth emulator, with the ID token the app would hold. */
async function guestAccount(): Promise<{ uid: string; idToken: string }> {
  const res = await fetch(
    `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=test`,
    { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ returnSecureToken: true }) },
  );
  const body = (await res.json()) as { localId: string; idToken: string };
  return { uid: body.localId, idToken: body.idToken };
}

test("deleteGuest removes the guest a Google sign-in left behind", async () => {
  const guest = await guestAccount();
  await as(guest.uid)(claimName, { name: "Guesty" });
  await db.doc(`users/${guest.uid}`).set({ rev: 1, gems: 5 });

  assert.deepEqual(await as("google-alice")(deleteGuest, { token: guest.idToken }), { ok: true });

  for (const path of [`profiles/${guest.uid}`, "usernames/guesty", `users/${guest.uid}`]) {
    assert.equal((await db.doc(path).get()).exists, false, `${path} should be gone`);
  }
  await assert.rejects(getAuth().getUser(guest.uid), { code: "auth/user-not-found" });
});

test("deleteGuest refuses bad tokens, non-guests and the caller itself", async () => {
  await assert.rejects(as("bob")(deleteGuest, { token: "not-a-token" }), { code: "permission-denied" });
  const guest = await guestAccount();
  await assert.rejects(as(guest.uid)(deleteGuest, { token: guest.idToken }), { code: "invalid-argument" });
  await getAuth().updateUser(guest.uid, { email: "real@example.com", password: "secret123" });
  const signIn = await fetch(
    `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=test`,
    {
      method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ email: "real@example.com", password: "secret123", returnSecureToken: true }),
    },
  );
  const { idToken } = (await signIn.json()) as { idToken: string };
  await assert.rejects(as("bob")(deleteGuest, { token: idToken }), { code: "permission-denied" });
  assert.ok(await getAuth().getUser(guest.uid), "a real account is never deleted this way");
});
