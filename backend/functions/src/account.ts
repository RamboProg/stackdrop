import { getAuth } from "firebase-admin/auth";
import { DocumentReference, Timestamp } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { CALLABLE, db, payload, requireAuth, stringField } from "./common";
import { inactiveGuests, UserActivity } from "./retention";

/** How long the deletion marker blocks writes: longer than any ID token issued before deletion lives. */
const MARKER_TTL_MS = 2 * 60 * 60 * 1000;
/** Retries per document before a failed delete counts as a failure. */
const MAX_ATTEMPTS = 5;

/**
 * Permanently deletes everything the caller owns, as Google Play requires: public profile, name and
 * friend-code indexes, friendships on both sides, friend requests, leaderboard entries, the cloud
 * save, private bookkeeping, and finally the Auth user itself.
 */
export const deleteAccount = onCall(CALLABLE, async (req): Promise<{ ok: true }> => {
  const uid = requireAuth(req);
  await deleteUserData(uid);
  logger.info("Account deleted", { uid });
  return { ok: true };
});

/**
 * Deletes the guest account a Google sign-in switched away from. Its progress was merged into the
 * Google account on the phone; left alone, its name, friends and ranks would sit in an account
 * nobody can reach any more. The guest's own ID token proves the caller owned it.
 */
export const deleteGuest = onCall(CALLABLE, async (req): Promise<{ ok: true }> => {
  const caller = requireAuth(req);
  const token = stringField(payload(req), "token");
  let guest: string;
  try {
    const decoded = await getAuth().verifyIdToken(token);
    if (decoded.firebase.sign_in_provider !== "anonymous") throw new Error("not a guest account");
    guest = decoded.uid;
  } catch (e) {
    logger.warn("deleteGuest refused a token", { caller, reason: (e as Error).message });
    throw new HttpsError("permission-denied", "bad-token");
  }
  if (guest === caller) throw new HttpsError("invalid-argument", "same-account");
  await deleteUserData(guest);
  logger.info("Guest account deleted after switching to Google", { caller, guest });
  return { ok: true };
});

/**
 * Data deletion: once a day, guest accounts unused for a year are deleted with everything they own
 * (see retention.ts). Failures are logged and retried the next day.
 */
export const purgeInactiveGuests = onSchedule({ schedule: "every day 03:00", timeZone: "UTC" }, async () => {
  const now = Date.now();
  let pageToken: string | undefined;
  let deleted = 0;
  do {
    const page = await getAuth().listUsers(1000, pageToken);
    pageToken = page.pageToken;
    const users: UserActivity[] = page.users.map((u) => ({
      uid: u.uid,
      providers: u.providerData.map((p) => p.providerId),
      creationTime: u.metadata.creationTime,
      lastSignInTime: u.metadata.lastSignInTime,
      lastRefreshTime: u.metadata.lastRefreshTime,
    }));
    for (const uid of inactiveGuests(users, now)) {
      try {
        await deleteUserData(uid);
        deleted++;
      } catch (e) {
        logger.error("Inactive guest not deleted; will retry tomorrow", { uid, error: (e as Error).message });
      }
    }
  } while (pageToken);
  logger.info("Inactive guests deleted", { deleted });
});

/** Removes every document [uid] owns, then the Auth user. Throws if anything could not be deleted. */
export async function deleteUserData(uid: string): Promise<void> {
  // Firestore rules refuse cloud-save writes while this marker exists, so a save already in flight
  // (its ID token stays valid for up to an hour) can't recreate data after it is deleted. It holds
  // only the uid and expires through the TTL policy on `expireAt`.
  await db.doc(`deleted/${uid}`).set({ expireAt: Timestamp.fromMillis(Date.now() + MARKER_TTL_MS) });

  const writer = db.bulkWriter();
  const failed: string[] = [];
  writer.onWriteError((err) => {
    if (err.failedAttempts < MAX_ATTEMPTS) return true;
    failed.push(err.documentRef.path);
    return false;
  });
  // Each failure is recorded by onWriteError above; the per-write promise only repeats it.
  const remove = (ref: DocumentReference): void => {
    writer.delete(ref).catch(() => undefined);
  };

  const profileRef = db.doc(`profiles/${uid}`);
  const profile = await profileRef.get();
  if (profile.exists) {
    const nameLower = profile.get("nameLower") as string | undefined;
    const code = profile.get("friendCode") as string | undefined;
    if (nameLower) await removeIfOwned(remove, `usernames/${nameLower}`, uid);
    if (code) await removeIfOwned(remove, `friendCodes/${code}`, uid);
    remove(profileRef);
  }

  const friends = await db.collection(`users/${uid}/friends`).listDocuments();
  for (const f of friends) remove(db.doc(`users/${f.id}/friends/${uid}`));

  const [sent, received] = await Promise.all([
    db.collection("friendRequests").where("from", "==", uid).get(),
    db.collection("friendRequests").where("to", "==", uid).get(),
  ]);
  for (const r of [...sent.docs, ...received.docs]) remove(r.ref);

  // Board documents may be "missing" (only their entries exist); listDocuments still returns them.
  const [cooldownsFrom, cooldownsTo] = await Promise.all([
    db.collection("friendCooldowns").where("from", "==", uid).get(),
    db.collection("friendCooldowns").where("to", "==", uid).get(),
  ]);
  for (const c of [...cooldownsFrom.docs, ...cooldownsTo.docs]) remove(c.ref);

  const boards = await db.collection("leaderboards").listDocuments();
  for (const b of boards) remove(b.collection("entries").doc(uid));

  const runs = await db.collection("runs").where("uid", "==", uid).get();
  for (const r of runs.docs) remove(r.ref);
  remove(db.doc(`private/${uid}`));
  await writer.close();
  if (failed.length > 0) {
    logger.error("Account deletion incomplete", { uid, failed });
    throw new HttpsError("internal", "delete-incomplete");
  }

  // Removes users/{uid} together with its friends subcollection.
  await db.recursiveDelete(db.doc(`users/${uid}`));

  try {
    await getAuth().deleteUser(uid);
  } catch (e) {
    if ((e as { code?: string }).code !== "auth/user-not-found") throw e;
  }
}

async function removeIfOwned(remove: (ref: DocumentReference) => void, path: string, uid: string): Promise<void> {
  const snap = await db.doc(path).get();
  if (snap.exists && snap.get("uid") === uid) remove(snap.ref);
}
