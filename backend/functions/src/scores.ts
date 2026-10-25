import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { CALLABLE, db, payload, requireAuth } from "./common";
import { checkStartedRun, utcDay } from "./runs";
import { checkRun, parseRun } from "./validate";
import { isoWeekId } from "./week";

const MIN_GAP_MS = 5_000;
const RECENT_RUNS = 20;
/** Accepted runs per UTC day; well above real play. */
export const MAX_SUBMITS_PER_DAY = 200;

export interface SubmitResult {
  ok: true;
  duplicate: boolean;
  newAllTime: boolean;
  newWeekly: boolean;
  week: string;
  weeklyRank?: number;
}

/**
 * The only way scores reach the leaderboards. Validates the run against its server-side start (see
 * startRun), rate-limits, and in one transaction (all reads before writes) raises the all-time and
 * weekly entries and the public best when higher. Runs started offline have no server start and are
 * refused; they still count as personal bests on the phone.
 */
export const submitScore = onCall(CALLABLE, async (req): Promise<SubmitResult> => {
  const uid = requireAuth(req);
  const run = parseRun(payload(req));
  if (!run) throw new HttpsError("invalid-argument", "bad-run");
  const check = checkRun(run);
  if (!check.ok) throw new HttpsError("invalid-argument", check.reason);

  const profileRef = db.doc(`profiles/${uid}`);
  const privateRef = db.doc(`private/${uid}`);
  const runRef = db.doc(`runs/${run.runId}`);
  const allRef = db.doc(`leaderboards/${run.mode}_all/entries/${uid}`);

  const outcome = await db.runTransaction(async (tx) => {
    const [profile, priv, started, all] = await tx.getAll(profileRef, privateRef, runRef, allRef);
    if (!profile.exists) throw new HttpsError("failed-precondition", "profile-required");

    const recent = (priv.get("recentRunIds") as string[] | undefined) ?? [];
    if (recent.includes(run.runId)) {
      return { duplicate: true, newAllTime: false, newWeekly: false, weeklyScore: undefined, week: undefined };
    }
    const now = Date.now();
    const finishedAt = checkStartedRun(started, uid, run.mode, run.durationMs, now);
    const last = priv.get("lastSubmitAt") as Timestamp | undefined;
    if (last && now - last.toMillis() < MIN_GAP_MS) {
      throw new HttpsError("resource-exhausted", "too-soon");
    }
    const today = utcDay(now);
    const submitted = priv.get("submitsDay") === today ? ((priv.get("submitsToday") as number | undefined) ?? 0) : 0;
    if (submitted >= MAX_SUBMITS_PER_DAY) throw new HttpsError("resource-exhausted", "too-many-runs");

    // The week the run finished in, so a run sent late still counts where it was played.
    const week = isoWeekId(new Date(finishedAt));
    const weekRef = db.doc(`leaderboards/${run.mode}_${week}/entries/${uid}`);
    const weekly = await tx.get(weekRef);
    const weeklyScore = weekly.get("score") as number | undefined;

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

    tx.update(runRef, { used: true });
    const bookkeeping: Record<string, unknown> = {
      lastSubmitAt: Timestamp.fromMillis(now),
      recentRunIds: [...recent, run.runId].slice(-RECENT_RUNS),
      submitsDay: today,
      submitsToday: submitted + 1,
    };
    if (check.suspicious) {
      bookkeeping.flags = FieldValue.increment(1);
      bookkeeping.lastFlaggedRun = { ...run, at: Timestamp.now() };
    }
    tx.set(privateRef, bookkeeping, { merge: true });

    return { duplicate: false, newAllTime, newWeekly, weeklyScore: newWeekly ? run.score : weeklyScore, week };
  });

  const week = outcome.week ?? isoWeekId(new Date());
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
