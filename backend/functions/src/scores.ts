import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { CALLABLE, db, payload, requireAuth } from "./common";
import { checkRun, parseRun } from "./validate";
import { isoWeekId } from "./week";

const MIN_GAP_MS = 5_000;
const RECENT_RUNS = 20;

export interface SubmitResult {
  ok: true;
  duplicate: boolean;
  newAllTime: boolean;
  newWeekly: boolean;
  week: string;
  weeklyRank?: number;
}

/**
 * The only way scores reach the leaderboards. Validates the run, rate-limits, and in one transaction
 * (all reads before writes) raises the all-time and weekly entries and the public best when higher.
 */
export const submitScore = onCall(CALLABLE, async (req): Promise<SubmitResult> => {
  const uid = requireAuth(req);
  const run = parseRun(payload(req));
  if (!run) throw new HttpsError("invalid-argument", "bad-run");
  const check = checkRun(run);
  if (!check.ok) throw new HttpsError("invalid-argument", check.reason);

  const week = isoWeekId(new Date());
  const profileRef = db.doc(`profiles/${uid}`);
  const privateRef = db.doc(`private/${uid}`);
  const allRef = db.doc(`leaderboards/${run.mode}_all/entries/${uid}`);
  const weekRef = db.doc(`leaderboards/${run.mode}_${week}/entries/${uid}`);

  const outcome = await db.runTransaction(async (tx) => {
    const [profile, priv, all, weekly] = await tx.getAll(profileRef, privateRef, allRef, weekRef);
    if (!profile.exists) throw new HttpsError("failed-precondition", "profile-required");

    const recent = (priv.get("recentRunIds") as string[] | undefined) ?? [];
    const weeklyScore = weekly.get("score") as number | undefined;
    if (recent.includes(run.runId)) {
      return { duplicate: true, newAllTime: false, newWeekly: false, weeklyScore };
    }
    const last = priv.get("lastSubmitAt") as Timestamp | undefined;
    if (last && Date.now() - last.toMillis() < MIN_GAP_MS) {
      throw new HttpsError("resource-exhausted", "too-soon");
    }

    const entry = {
      score: run.score,
      name: profile.get("name") as string,
      avatar: profile.get("avatar") as number,
      updatedAt: FieldValue.serverTimestamp(),
    };
    const newAllTime = run.score > 0 && run.score > ((all.get("score") as number | undefined) ?? 0);
    const newWeekly = run.score > 0 && run.score > (weeklyScore ?? 0);
    if (newAllTime) tx.set(allRef, entry);
    if (newWeekly) tx.set(weekRef, entry);

    const bestField = `bests.${run.mode}`;
    if (run.score > ((profile.get(bestField) as number | undefined) ?? 0)) {
      tx.update(profileRef, { [bestField]: run.score });
    }

    const bookkeeping: Record<string, unknown> = {
      lastSubmitAt: Timestamp.now(),
      recentRunIds: [...recent, run.runId].slice(-RECENT_RUNS),
    };
    if (check.suspicious) {
      bookkeeping.flags = FieldValue.increment(1);
      bookkeeping.lastFlaggedRun = { ...run, at: Timestamp.now() };
    }
    tx.set(privateRef, bookkeeping, { merge: true });

    return { duplicate: false, newAllTime, newWeekly, weeklyScore: newWeekly ? run.score : weeklyScore };
  });

  let weeklyRank: number | undefined;
  if (outcome.weeklyScore !== undefined && outcome.weeklyScore > 0) {
    const higher = await db.collection(`leaderboards/${run.mode}_${week}/entries`)
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
