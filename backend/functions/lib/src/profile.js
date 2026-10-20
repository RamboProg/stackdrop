"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.setAvatar = exports.claimName = void 0;
const firestore_1 = require("firebase-admin/firestore");
const https_1 = require("firebase-functions/v2/https");
const common_1 = require("./common");
const config_1 = require("./config");
const codes_1 = require("./codes");
const names_1 = require("./names");
const NAME_CHANGES_PER_DAY = 3;
const DAY_MS = 24 * 60 * 60 * 1000;
const CODE_ATTEMPTS = 4;
/**
 * Sets the caller's display name, creating their public profile (with a friend code) on first use.
 * Name uniqueness is enforced in a transaction on usernames/{nameLower}; the old name is released.
 */
exports.claimName = (0, https_1.onCall)(common_1.CALLABLE, async (req) => {
    const uid = (0, common_1.requireAuth)(req);
    const data = (0, common_1.payload)(req);
    const check = (0, names_1.checkName)((0, common_1.stringField)(data, "name"));
    if (!check.ok)
        return { ok: false, reason: check.reason };
    const avatar = (0, common_1.optionalIntField)(data, "avatar", 0, config_1.AVATAR_COUNT - 1) ?? (0, codes_1.defaultAvatar)(uid);
    const alternatives = (0, names_1.suggestions)(check.name);
    // A fresh friend code can collide with an existing one; then the whole transaction is retried.
    for (let attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
        const code = (0, codes_1.newFriendCode)();
        const result = await common_1.db.runTransaction(async (tx) => {
            const profileRef = common_1.db.doc(`profiles/${uid}`);
            const privateRef = common_1.db.doc(`private/${uid}`);
            const nameRef = common_1.db.doc(`usernames/${check.lower}`);
            const codeRef = common_1.db.doc(`friendCodes/${code}`);
            const [profile, priv, owner, codeOwner] = await tx.getAll(profileRef, privateRef, nameRef, codeRef);
            if (owner.exists && owner.get("uid") !== uid) {
                const refs = alternatives.map((s) => common_1.db.doc(`usernames/${s.toLowerCase()}`));
                const snaps = refs.length > 0 ? await tx.getAll(...refs) : [];
                const free = alternatives.filter((_, i) => !snaps[i].exists).slice(0, 2);
                return { ok: false, reason: "taken", suggestions: free };
            }
            const now = Date.now();
            const recent = (priv.get("nameChanges") ?? []).filter((t) => t > now - DAY_MS);
            if (profile.exists && recent.length >= NAME_CHANGES_PER_DAY) {
                return { ok: false, reason: "rate-limited" };
            }
            if (!profile.exists && codeOwner.exists)
                return "code-collision";
            if (profile.exists) {
                const oldLower = profile.get("nameLower");
                if (oldLower && oldLower !== check.lower)
                    tx.delete(common_1.db.doc(`usernames/${oldLower}`));
                tx.update(profileRef, { name: check.name, nameLower: check.lower });
            }
            else {
                tx.set(profileRef, {
                    name: check.name,
                    nameLower: check.lower,
                    avatar,
                    friendCode: code,
                    bests: { stack: 0, tower: 0 },
                    createdAt: firestore_1.FieldValue.serverTimestamp(),
                });
                tx.set(codeRef, { uid });
            }
            tx.set(nameRef, { uid });
            tx.set(privateRef, { nameChanges: [...recent, now], updatedAt: firestore_1.Timestamp.now() }, { merge: true });
            return { ok: true };
        });
        if (result !== "code-collision")
            return result;
    }
    throw new https_1.HttpsError("internal", "friend-code-exhausted");
});
/** Changes the caller's preset avatar. */
exports.setAvatar = (0, https_1.onCall)(common_1.CALLABLE, async (req) => {
    const uid = (0, common_1.requireAuth)(req);
    const avatar = (0, common_1.intField)((0, common_1.payload)(req), "avatar", 0, config_1.AVATAR_COUNT - 1);
    const ref = common_1.db.doc(`profiles/${uid}`);
    const snap = await ref.get();
    if (!snap.exists)
        throw new https_1.HttpsError("failed-precondition", "profile-required");
    await ref.update({ avatar });
    return { ok: true };
});
//# sourceMappingURL=profile.js.map