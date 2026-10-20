import { DocumentReference, FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { normalizeCode } from "./codes";
import { CALLABLE, db, payload, requireAuth } from "./common";

export const MAX_FRIENDS = 100;
export const MAX_OUTGOING = 20;

export type FriendResult =
  | { ok: true; status: "sent" | "already-sent" | "friends"; name: string }
  | { ok: false; reason: "not-found" | "self" | "already-friends" | "too-many-friends" | "too-many-requests" };

const requestId = (from: string, to: string): string => `${from}_${to}`;
const friendRef = (owner: string, friend: string) => db.doc(`users/${owner}/friends/${friend}`);

/**
 * Sends a friend request by friend code or by uid (from a leaderboard row). If the other player has
 * already asked the caller, the two simply become friends.
 */
export const sendFriendRequest = onCall(CALLABLE, async (req): Promise<FriendResult> => {
  const uid = requireAuth(req);
  const data = payload(req);
  const target = await resolveTarget(data);
  if (!target) return { ok: false, reason: "not-found" };
  if (target === uid) return { ok: false, reason: "self" };

  const [me, them] = await Promise.all([db.doc(`profiles/${uid}`).get(), db.doc(`profiles/${target}`).get()]);
  if (!me.exists) throw new HttpsError("failed-precondition", "profile-required");
  if (!them.exists) return { ok: false, reason: "not-found" };
  const theirName = them.get("name") as string;

  if ((await friendRef(uid, target).get()).exists) return { ok: false, reason: "already-friends" };
  if (await friendCount(uid) >= MAX_FRIENDS || await friendCount(target) >= MAX_FRIENDS) {
    return { ok: false, reason: "too-many-friends" };
  }

  const reverse = db.doc(`friendRequests/${requestId(target, uid)}`);
  if ((await reverse.get()).exists) {
    await befriend(uid, target, reverse);
    return { ok: true, status: "friends", name: theirName };
  }

  const forward = db.doc(`friendRequests/${requestId(uid, target)}`);
  if ((await forward.get()).exists) return { ok: true, status: "already-sent", name: theirName };
  const outgoing = await db.collection("friendRequests").where("from", "==", uid).count().get();
  if (outgoing.data().count >= MAX_OUTGOING) return { ok: false, reason: "too-many-requests" };

  await forward.set({ from: uid, to: target, fromName: me.get("name") as string, createdAt: FieldValue.serverTimestamp() });
  return { ok: true, status: "sent", name: theirName };
});

/** Accepts or declines a request someone sent to the caller. */
export const respondFriendRequest = onCall(CALLABLE, async (req): Promise<{ ok: boolean; reason?: string }> => {
  const uid = requireAuth(req);
  const data = payload(req);
  const fromUid = data.fromUid;
  const accept = data.accept;
  if (typeof fromUid !== "string" || fromUid.length === 0 || typeof accept !== "boolean") {
    throw new HttpsError("invalid-argument", "bad-request");
  }
  const request = db.doc(`friendRequests/${requestId(fromUid, uid)}`);
  if (!(await request.get()).exists) return { ok: false, reason: "not-found" };
  if (!accept) {
    await request.delete();
    return { ok: true };
  }
  if (await friendCount(uid) >= MAX_FRIENDS || await friendCount(fromUid) >= MAX_FRIENDS) {
    return { ok: false, reason: "too-many-friends" };
  }
  await befriend(uid, fromUid, request);
  return { ok: true };
});

/** Ends a friendship on both sides, along with any requests still pending between the two. */
export const removeFriend = onCall(CALLABLE, async (req): Promise<{ ok: true }> => {
  const uid = requireAuth(req);
  const other = payload(req).uid;
  if (typeof other !== "string" || other.length === 0 || other === uid) {
    throw new HttpsError("invalid-argument", "bad-uid");
  }
  const batch = db.batch();
  batch.delete(friendRef(uid, other));
  batch.delete(friendRef(other, uid));
  batch.delete(db.doc(`friendRequests/${requestId(uid, other)}`));
  batch.delete(db.doc(`friendRequests/${requestId(other, uid)}`));
  await batch.commit();
  return { ok: true };
});

async function resolveTarget(data: Record<string, unknown>): Promise<string | null> {
  if (typeof data.uid === "string" && data.uid.length > 0) return data.uid;
  if (typeof data.code !== "string") throw new HttpsError("invalid-argument", "code-or-uid");
  const code = normalizeCode(data.code);
  if (!code) return null;
  const snap = await db.doc(`friendCodes/${code}`).get();
  return snap.exists ? (snap.get("uid") as string) : null;
}

async function friendCount(uid: string): Promise<number> {
  return (await db.collection(`users/${uid}/friends`).count().get()).data().count;
}

async function befriend(a: string, b: string, request: DocumentReference): Promise<void> {
  const batch = db.batch();
  const since = FieldValue.serverTimestamp();
  batch.set(friendRef(a, b), { since });
  batch.set(friendRef(b, a), { since });
  batch.delete(request);
  await batch.commit();
}
