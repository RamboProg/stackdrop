import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { CALLABLE, db, optionalIntField, payload, requireAuth, stringField, intField } from "./common";
import { AVATAR_COUNT, MODES } from "./config";
import { defaultAvatar, newFriendCode } from "./codes";
import { checkName, suggestions } from "./names";
import { isoWeekId } from "./week";

const NAME_CHANGES_PER_DAY = 3;
const DAY_MS = 24 * 60 * 60 * 1000;
const CODE_ATTEMPTS = 4;

export type ClaimResult =
  | { ok: true }
  | { ok: false; reason: string; suggestions?: string[] };

/**
 * Sets the caller's display name, creating their public profile (with a friend code) on first use.
 * Name uniqueness is enforced in a transaction on usernames/{nameLower}; the old name is released.
 */
export const claimName = onCall(CALLABLE, async (req): Promise<ClaimResult> => {
  const uid = requireAuth(req);
  const data = payload(req);
  const check = checkName(stringField(data, "name"));
  if (!check.ok) return { ok: false, reason: check.reason };
  const avatar = optionalIntField(data, "avatar", 0, AVATAR_COUNT - 1) ?? defaultAvatar(uid);
  const alternatives = suggestions(check.name);

  // A fresh friend code can collide with an existing one; then the whole transaction is retried.
  for (let attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
    const code = newFriendCode();
    const result = await db.runTransaction(async (tx) => {
      const profileRef = db.doc(`profiles/${uid}`);
      const privateRef = db.doc(`private/${uid}`);
      const nameRef = db.doc(`usernames/${check.lower}`);
      const codeRef = db.doc(`friendCodes/${code}`);
      const [profile, priv, owner, codeOwner] = await tx.getAll(profileRef, privateRef, nameRef, codeRef);

      if (owner.exists && owner.get("uid") !== uid) {
        const refs = alternatives.map((s) => db.doc(`usernames/${s.toLowerCase()}`));
        const snaps = refs.length > 0 ? await tx.getAll(...refs) : [];
        const free = alternatives.filter((_, i) => !snaps[i].exists).slice(0, 2);
        return { ok: false, reason: "taken", suggestions: free } as const;
      }

      const now = Date.now();
      const recent = ((priv.get("nameChanges") as number[] | undefined) ?? []).filter((t) => t > now - DAY_MS);
      if (profile.exists && recent.length >= NAME_CHANGES_PER_DAY) {
        return { ok: false, reason: "rate-limited" } as const;
      }
      if (!profile.exists && codeOwner.exists) return "code-collision" as const;

      if (profile.exists) {
        const oldLower = profile.get("nameLower") as string | undefined;
        if (oldLower && oldLower !== check.lower) tx.delete(db.doc(`usernames/${oldLower}`));
        tx.update(profileRef, { name: check.name, nameLower: check.lower });
      } else {
        tx.set(profileRef, {
          name: check.name,
          nameLower: check.lower,
          avatar,
          friendCode: code,
          bests: { stack: 0, tower: 0 },
          createdAt: FieldValue.serverTimestamp(),
        });
        tx.set(codeRef, { uid });
      }
      tx.set(nameRef, { uid });
      tx.set(privateRef, { nameChanges: [...recent, now], updatedAt: Timestamp.now() }, { merge: true });
      return { ok: true } as const;
    });
    if (result === "code-collision") continue;
    if (result.ok) await updatePublicCopies(uid, { name: check.name });
    return result;
  }
  throw new HttpsError("internal", "friend-code-exhausted");
});

/** Changes the caller's preset avatar. */
export const setAvatar = onCall(CALLABLE, async (req): Promise<{ ok: true }> => {
  const uid = requireAuth(req);
  const avatar = intField(payload(req), "avatar", 0, AVATAR_COUNT - 1);
  const ref = db.doc(`profiles/${uid}`);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("failed-precondition", "profile-required");
  await ref.update({ avatar });
  await updatePublicCopies(uid, { avatar });
  return { ok: true };
});

/**
 * Leaderboard rows and pending friend requests copy the name and avatar. After a change, the boards
 * players can see (all-time and this week) and the requests still waiting show the new ones, so a
 * name that was changed for being offensive doesn't stay on display.
 */
async function updatePublicCopies(uid: string, fields: { name?: string; avatar?: number }): Promise<void> {
  const week = isoWeekId(new Date());
  const entries = MODES.flatMap((m) => [`${m}_all`, `${m}_${week}`])
    .map((board) => db.doc(`leaderboards/${board}/entries/${uid}`));
  const [rows, requests] = await Promise.all([
    db.getAll(...entries),
    fields.name === undefined ? null : db.collection("friendRequests").where("from", "==", uid).get(),
  ]);
  const batch = db.batch();
  for (const row of rows) if (row.exists) batch.update(row.ref, fields);
  for (const request of requests?.docs ?? []) batch.update(request.ref, { fromName: fields.name });
  await batch.commit();
}
