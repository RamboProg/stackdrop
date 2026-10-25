// Firestore security rules tests. They need the Firestore emulator, so run them with
// `npm run test:rules`, which starts it for the duration of the tests.
import { assertFails, assertSucceeds, initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, getDoc, serverTimestamp, setDoc, updateDoc } from "firebase/firestore";
import { readFileSync } from "node:fs";
import { after, before, beforeEach, test } from "node:test";

let env: RulesTestEnvironment;

const save = {
  rev: 1, gems: 10, unlockedThemes: [], selectedTheme: "dusk", bests: { stack: 0, tower: 0 },
  streak: 0, lastDaily: -1, gamesPlayed: 0,
  settings: { sound: true, vibration: true, reduceMotion: false }, updatedAt: serverTimestamp(),
};

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-stackdrop",
    firestore: { rules: readFileSync("../firestore.rules", "utf8"), host: "127.0.0.1", port: 8080 },
  });
});

beforeEach(async () => {
  await env.clearFirestore();
});

after(async () => {
  await env.cleanup();
});

const as = (uid: string) => env.authenticatedContext(uid).firestore();

test("a player can create and read only their own save", async () => {
  await assertSucceeds(setDoc(doc(as("alice"), "users/alice"), save));
  await assertSucceeds(getDoc(doc(as("alice"), "users/alice")));
  await assertFails(getDoc(doc(as("bob"), "users/alice")));
  await assertFails(setDoc(doc(as("bob"), "users/alice"), save));
});

test("the save rejects unknown fields and impossible gem counts", async () => {
  await assertFails(setDoc(doc(as("alice"), "users/alice"), { ...save, admin: true }));
  await assertSucceeds(setDoc(doc(as("alice"), "users/alice"), save));
  await assertFails(updateDoc(doc(as("alice"), "users/alice"), { gems: -1, rev: 2 }));
  await assertFails(updateDoc(doc(as("alice"), "users/alice"), { gems: 2_000_000, rev: 2 }));
  await assertSucceeds(updateDoc(doc(as("alice"), "users/alice"), { gems: 500, rev: 2 }));
});

test("a new save is checked like an update", async () => {
  await assertFails(setDoc(doc(as("alice"), "users/alice"), { ...save, gems: -5 }));
  await assertFails(setDoc(doc(as("alice"), "users/alice"), { ...save, gems: "lots" }));
  await assertFails(setDoc(doc(as("alice"), "users/alice"), { ...save, ledger: "x" }));
  await assertSucceeds(setDoc(doc(as("alice"), "users/alice"), { ...save, ledger: { d1: { e: 10, s: 0 } } }));
});

test("every write must carry a newer rev, so a stale phone can't overwrite the save", async () => {
  await assertSucceeds(setDoc(doc(as("alice"), "users/alice"), { ...save, rev: 5 }));
  await assertFails(updateDoc(doc(as("alice"), "users/alice"), { gems: 1, rev: 5 }));
  await assertFails(setDoc(doc(as("alice"), "users/alice"), { ...save, rev: 3 }));
  await assertSucceeds(updateDoc(doc(as("alice"), "users/alice"), { gems: 1, rev: 6 }));
});

test("a save being deleted can't be written again", async () => {
  await assertSucceeds(setDoc(doc(as("alice"), "users/alice"), save));
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "deleted/alice"), { expireAt: 1 });
  });
  await assertFails(updateDoc(doc(as("alice"), "users/alice"), { gems: 1, rev: 2 }));
  await assertFails(setDoc(doc(as("alice"), "users/alice"), { ...save, rev: 9 }));
  await assertFails(getDoc(doc(as("alice"), "deleted/alice")));
});

test("profiles are public to signed-in players but written only by functions", async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "profiles/alice"), { name: "Alice", avatar: 1 });
  });
  await assertSucceeds(getDoc(doc(as("bob"), "profiles/alice")));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "profiles/alice")));
  await assertFails(setDoc(doc(as("alice"), "profiles/alice"), { name: "Hacker" }));
});

test("indexes, private data and leaderboard writes are closed to clients", async () => {
  await assertFails(getDoc(doc(as("alice"), "usernames/alice")));
  await assertFails(getDoc(doc(as("alice"), "friendCodes/K7Q2M9XD")));
  await assertFails(getDoc(doc(as("alice"), "private/alice")));
  await assertFails(setDoc(doc(as("alice"), "leaderboards/tower_all/entries/alice"), { score: 9999 }));
  await assertSucceeds(getDoc(doc(as("alice"), "leaderboards/tower_all/entries/bob")));
  await assertFails(setDoc(doc(as("alice"), "users/alice/friends/bob"), { since: 1 }));
});
