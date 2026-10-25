import { DocumentReference, FieldValue, Timestamp, Transaction } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { normalizeCode } from "./codes";
import { CALLABLE, db, payload, requireAuth } from "./common";

export const MAX_FRIENDS = 100;
export const MAX_OUTGOING = 20;
/** After a decline, new requests from the same player are quietly dropped for this long. */
export const DECLINE_COOLDOWN_MS = 7 * 24 * 60 * 60 * 1000;
/** Firebase Auth uids; anything else would be read as a document path. */
const UID = /^[A-Za-z0-9]{1,128}$/;

export type FriendResult =
  | { ok: true; status: "sent" | "already-sent" | "friends"; name: string }
  | { ok: false; reason: "not-found" | "self" | "already-friends" | "too-many-friends" | "too-many-requests" };

const requestId = (from: string, to: string): string => `${from}_${to}`;
const friendRef = (owner: string, friend: string) => db.doc(`users/${owner}/friends/${friend}`);
const cooldownRef = (from: string, to: string) => db.doc(`friendCooldowns/${requestId(from, to)}`);

function checkUid(v: unknown, field: string): string {
  if (typeof v !== "string" || !UID.test(v)) throw new HttpsError("invalid-argument", `bad-${field}`);
  return v;
}

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

  // One transaction, so two players asking each other at once become friends instead of leaving
  // two crossed requests, and concurrent accepts can't pass the friend limit.
  return db.runTransaction(async (tx): Promise<FriendResult> => {
    const reverse = db.doc(`friendRequests/${requestId(target, uid)}`);
    const forward = db.doc(`friendRequests/${requestId(uid, target)}`);
    const [me, them, existing, reverseSnap, forwardSnap, cooldown] = await tx.getAll(
      db.doc(`profiles/${uid}`), db.doc(`profiles/${target}`), friendRef(uid, target), reverse, forward,
      cooldownRef(uid, target),
    );
    if (!me.exists) throw new HttpsError("failed-precondition", "profile-required");
    if (!them.exists) return { ok: false, reason: "not-found" };
    const theirName = them.get("name") as string;
    if (existing.exists) return { ok: false, reason: "already-friends" };
    if (await atFriendLimit(tx, uid, target)) return { ok: false, reason: "too-many-friends" };

    if (reverseSnap.exists) {
      befriend(tx, uid, target, reverse);
      return { ok: true, status: "friends", name: theirName };
    }
    if (forwardSnap.exists) return { ok: true, status: "already-sent", name: theirName };
    // Declined recently: look sent, but don't bother the other player again yet.
    const until = cooldown.get("until") as Timestamp | undefined;
    if (until && until.toMillis() > Date.now()) return { ok: true, status: "sent", name: theirName };
    const outgoing = await tx.get(db.collection("friendRequests").where("from", "==", uid).count());
    if (outgoing.data().count >= MAX_OUTGOING) return { ok: false, reason: "too-many-requests" };

    tx.set(forward, { from: uid, to: target, fromName: me.get("name") as string, createdAt: FieldValue.serverTimestamp() });
    return { ok: true, status: "sent", name: theirName };
  });
});

/** Accepts or declines a request someone sent to the caller. */
export const respondFriendRequest = onCall(CALLABLE, async (req): Promise<{ ok: boolean; reason?: string }> => {
  const uid = requireAuth(req);
  const data = payload(req);
  const fromUid = checkUid(data.fromUid, "request");
  const accept = data.accept;
  if (typeof accept !== "boolean") throw new HttpsError("invalid-argument", "bad-request");
  const request = db.doc(`friendRequests/${requestId(fromUid, uid)}`);
  return db.runTransaction(async (tx) => {
    if (!(await tx.get(request)).exists) return { ok: false, reason: "not-found" };
    if (!accept) {
      tx.delete(request);
      tx.set(cooldownRef(fromUid, uid), {
        from: fromUid, to: uid, until: Timestamp.fromMillis(Date.now() + DECLINE_COOLDOWN_MS),
        expireAt: Timestamp.fromMillis(Date.now() + DECLINE_COOLDOWN_MS),
      });
      return { ok: true };
    }
    if (await atFriendLimit(tx, uid, fromUid)) return { ok: false, reason: "too-many-friends" };
    befriend(tx, uid, fromUid, request);
    return { ok: true };
  });
});

/** Ends a friendship on both sides, along with any requests still pending between the two. */
export const removeFriend = onCall(CALLABLE, async (req): Promise<{ ok: true }> => {
  const uid = requireAuth(req);
  const other = checkUid(payload(req).uid, "uid");
  if (other === uid) throw new HttpsError("invalid-argument", "bad-uid");
  const batch = db.batch();
  batch.delete(friendRef(uid, other));
  batch.delete(friendRef(other, uid));
  batch.delete(db.doc(`friendRequests/${requestId(uid, other)}`));
  batch.delete(db.doc(`friendRequests/${requestId(other, uid)}`));
  await batch.commit();
  return { ok: true };
});

async function resolveTarget(data: Record<string, unknown>): Promise<string | null> {
  if (typeof data.uid === "string" && data.uid.length > 0) return checkUid(data.uid, "uid");
  if (typeof data.code !== "string") throw new HttpsError("invalid-argument", "code-or-uid");
  const code = normalizeCode(data.code);
  if (!code) return null;
  const snap = await db.doc(`friendCodes/${code}`).get();
  return snap.exists ? (snap.get("uid") as string) : null;
}

async function atFriendLimit(tx: Transaction, a: string, b: string): Promise<boolean> {
  const [x, y] = await Promise.all([
    tx.get(db.collection(`users/${a}/friends`).count()),
    tx.get(db.collection(`users/${b}/friends`).count()),
  ]);
  return x.data().count >= MAX_FRIENDS || y.data().count >= MAX_FRIENDS;
}

function befriend(tx: Transaction, a: string, b: string, request: DocumentReference): void {
  const since = FieldValue.serverTimestamp();
  tx.set(friendRef(a, b), { since });
  tx.set(friendRef(b, a), { since });
  tx.delete(request);
}
