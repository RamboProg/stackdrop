import { getAuth } from "firebase-admin/auth";
import { BulkWriter } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { onCall } from "firebase-functions/v2/https";
import { CALLABLE, db, requireAuth } from "./common";

/**
 * Permanently deletes everything the caller owns, as Google Play requires: public profile, name and
 * friend-code indexes, friendships on both sides, friend requests, leaderboard entries, the cloud
 * save, private bookkeeping, and finally the Auth user itself.
 */
export const deleteAccount = onCall(CALLABLE, async (req): Promise<{ ok: true }> => {
  const uid = requireAuth(req);
  const writer = db.bulkWriter();

  const profileRef = db.doc(`profiles/${uid}`);
  const profile = await profileRef.get();
  if (profile.exists) {
    const nameLower = profile.get("nameLower") as string | undefined;
    const code = profile.get("friendCode") as string | undefined;
    if (nameLower) await deleteIfOwned(writer, `usernames/${nameLower}`, uid);
    if (code) await deleteIfOwned(writer, `friendCodes/${code}`, uid);
    writer.delete(profileRef);
  }

  const friends = await db.collection(`users/${uid}/friends`).listDocuments();
  for (const f of friends) writer.delete(db.doc(`users/${f.id}/friends/${uid}`));

  const [sent, received] = await Promise.all([
    db.collection("friendRequests").where("from", "==", uid).get(),
    db.collection("friendRequests").where("to", "==", uid).get(),
  ]);
  for (const r of [...sent.docs, ...received.docs]) writer.delete(r.ref);

  // Board documents may be "missing" (only their entries exist); listDocuments still returns them.
  const boards = await db.collection("leaderboards").listDocuments();
  for (const b of boards) writer.delete(b.collection("entries").doc(uid));

  writer.delete(db.doc(`private/${uid}`));
  await writer.close();

  // Removes users/{uid} together with its friends subcollection.
  await db.recursiveDelete(db.doc(`users/${uid}`));

  try {
    await getAuth().deleteUser(uid);
  } catch (e) {
    if ((e as { code?: string }).code !== "auth/user-not-found") throw e;
  }
  logger.info("Account deleted", { uid });
  return { ok: true };
});

async function deleteIfOwned(writer: BulkWriter, path: string, uid: string): Promise<void> {
  const snap = await db.doc(path).get();
  if (snap.exists && snap.get("uid") === uid) writer.delete(snap.ref);
}
