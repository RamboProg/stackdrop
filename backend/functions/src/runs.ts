import { DocumentSnapshot, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { CALLABLE, db, payload, requireAuth, stringField } from "./common";
import { MODES, Mode } from "./config";
import { RUN_ID } from "./validate";

/** Runs a player may start per UTC day; far above real play, low enough to stop document spam. */
export const MAX_RUNS_PER_DAY = 500;
/** Started runs are kept this long, so a run finished offline can still be submitted for a while. */
const RUN_TTL_MS = 7 * 24 * 60 * 60 * 1000;
/** Clock slack between the phone's run timer and the server's, in the phone's favour. */
const CLOCK_SLACK = 0.9;

export const utcDay = (ms: number): number => Math.floor(ms / 86_400_000);

/**
 * Records, on the server's clock, that a run has started. submitScore only accepts runs started
 * here, and only once the server has seen at least as much time pass as the run claims to have
 * taken, so a made-up score costs as long to fake as it would to play.
 */
export const startRun = onCall(CALLABLE, async (req): Promise<{ ok: true }> => {
  const uid = requireAuth(req);
  const data = payload(req);
  const runId = stringField(data, "runId");
  const mode = stringField(data, "mode");
  if (!RUN_ID.test(runId)) throw new HttpsError("invalid-argument", "bad-runId");
  if (!(MODES as readonly string[]).includes(mode)) throw new HttpsError("invalid-argument", "bad-mode");

  const runRef = db.doc(`runs/${runId}`);
  const privateRef = db.doc(`private/${uid}`);
  await db.runTransaction(async (tx) => {
    const [run, priv] = await tx.getAll(runRef, privateRef);
    if (run.exists) throw new HttpsError("already-exists", "run-exists");
    const now = Date.now();
    const today = utcDay(now);
    const started = priv.get("runsDay") === today ? ((priv.get("runsToday") as number | undefined) ?? 0) : 0;
    if (started >= MAX_RUNS_PER_DAY) throw new HttpsError("resource-exhausted", "too-many-runs");
    tx.create(runRef, {
      uid, mode, startedAt: Timestamp.fromMillis(now), used: false, expireAt: Timestamp.fromMillis(now + RUN_TTL_MS),
    });
    tx.set(privateRef, { runsDay: today, runsToday: started + 1 }, { merge: true });
  });
  return { ok: true };
});

/**
 * Checks a submitted run against its server-side start. Returns when the run finished (start plus
 * its claimed duration, never later than now), which decides its leaderboard week.
 */
export function checkStartedRun(
  run: DocumentSnapshot, uid: string, mode: Mode, durationMs: number, now: number,
): number {
  if (!run.exists || run.get("uid") !== uid || run.get("mode") !== mode) {
    throw new HttpsError("invalid-argument", "unknown-run");
  }
  if (run.get("used") === true) throw new HttpsError("invalid-argument", "run-used");
  const startedAt = (run.get("startedAt") as Timestamp).toMillis();
  if (now - startedAt < durationMs * CLOCK_SLACK) throw new HttpsError("invalid-argument", "clock-mismatch");
  return Math.min(now, startedAt + durationMs);
}
