"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.submitScore = void 0;
const firestore_1 = require("firebase-admin/firestore");
const https_1 = require("firebase-functions/v2/https");
const common_1 = require("./common");
const validate_1 = require("./validate");
const week_1 = require("./week");
const MIN_GAP_MS = 5_000;
const RECENT_RUNS = 20;
/**
 * The only way scores reach the leaderboards. Validates the run, rate-limits, and in one transaction
 * (all reads before writes) raises the all-time and weekly entries and the public best when higher.
 */
exports.submitScore = (0, https_1.onCall)(common_1.CALLABLE, async (req) => {
    const uid = (0, common_1.requireAuth)(req);
    const run = (0, validate_1.parseRun)((0, common_1.payload)(req));
    if (!run)
        throw new https_1.HttpsError("invalid-argument", "bad-run");
    const check = (0, validate_1.checkRun)(run);
    if (!check.ok)
        throw new https_1.HttpsError("invalid-argument", check.reason);
    const week = (0, week_1.isoWeekId)(new Date());
    const profileRef = common_1.db.doc(`profiles/${uid}`);
    const privateRef = common_1.db.doc(`private/${uid}`);
    const allRef = common_1.db.doc(`leaderboards/${run.mode}_all/entries/${uid}`);
    const weekRef = common_1.db.doc(`leaderboards/${run.mode}_${week}/entries/${uid}`);
    const outcome = await common_1.db.runTransaction(async (tx) => {
        const [profile, priv, all, weekly] = await tx.getAll(profileRef, privateRef, allRef, weekRef);
        if (!profile.exists)
            throw new https_1.HttpsError("failed-precondition", "profile-required");
        const recent = priv.get("recentRunIds") ?? [];
        const weeklyScore = weekly.get("score");
        if (recent.includes(run.runId)) {
            return { duplicate: true, newAllTime: false, newWeekly: false, weeklyScore };
        }
        const last = priv.get("lastSubmitAt");
        if (last && Date.now() - last.toMillis() < MIN_GAP_MS) {
            throw new https_1.HttpsError("resource-exhausted", "too-soon");
        }
        const entry = {
            score: run.score,
            name: profile.get("name"),
            avatar: profile.get("avatar"),
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        };
        const newAllTime = run.score > 0 && run.score > (all.get("score") ?? 0);
        const newWeekly = run.score > 0 && run.score > (weeklyScore ?? 0);
        if (newAllTime)
            tx.set(allRef, entry);
        if (newWeekly)
            tx.set(weekRef, entry);
        const bestField = `bests.${run.mode}`;
        if (run.score > (profile.get(bestField) ?? 0)) {
            tx.update(profileRef, { [bestField]: run.score });
        }
        const bookkeeping = {
            lastSubmitAt: firestore_1.Timestamp.now(),
            recentRunIds: [...recent, run.runId].slice(-RECENT_RUNS),
        };
        if (check.suspicious) {
            bookkeeping.flags = firestore_1.FieldValue.increment(1);
            bookkeeping.lastFlaggedRun = { ...run, at: firestore_1.Timestamp.now() };
        }
        tx.set(privateRef, bookkeeping, { merge: true });
        return { duplicate: false, newAllTime, newWeekly, weeklyScore: newWeekly ? run.score : weeklyScore };
    });
    let weeklyRank;
    if (outcome.weeklyScore !== undefined && outcome.weeklyScore > 0) {
        const higher = await common_1.db.collection(`leaderboards/${run.mode}_${week}/entries`)
            .where("score", ">", outcome.weeklyScore).count().get();
        weeklyRank = higher.data().count + 1;
    }
    return {
        ok: true,
        duplicate: outcome.duplicate,
        newAllTime: outcome.newAllTime,
        newWeekly: outcome.newWeekly,
        week,
        weeklyRank,
    };
});
//# sourceMappingURL=scores.js.map