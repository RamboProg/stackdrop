import { MODES, Mode } from "./config";

export const MAX_SCORE = 5000;

/** Fastest believable time per drop, tuned from real play. */
export const MIN_DROP_MS: Record<Mode, number> = { stack: 220, tower: 650 };

export const RUN_ID = /^[A-Za-z0-9-]{8,64}$/;

export interface RunInput {
  mode: Mode;
  score: number;
  durationMs: number;
  drops: number;
  perfects: number;
  bestCombo: number;
  revived: boolean;
  runId: string;
}

export type RunCheck =
  | { ok: true; suspicious: boolean }
  | { ok: false; reason: string };

/** Parses an untrusted payload; returns null when any field has the wrong type or shape. */
export function parseRun(data: Record<string, unknown>): RunInput | null {
  const { mode, score, durationMs, drops, perfects, bestCombo, revived, runId } = data;
  const isInt = (v: unknown): v is number => typeof v === "number" && Number.isInteger(v) && v >= 0;
  if (typeof mode !== "string" || !(MODES as readonly string[]).includes(mode)) return null;
  if (!isInt(score) || !isInt(durationMs) || !isInt(drops) || !isInt(perfects) || !isInt(bestCombo)) return null;
  if (typeof revived !== "boolean" || typeof runId !== "string" || !RUN_ID.test(runId)) return null;
  return { mode: mode as Mode, score, durationMs, drops, perfects, bestCombo, revived, runId };
}

/**
 * Impossible runs are rejected. Runs that are possible but unusual are accepted and flagged, so they
 * can be reviewed later without punishing a genuinely great player.
 */
export function checkRun(r: RunInput): RunCheck {
  if (r.score > MAX_SCORE) return { ok: false, reason: "score-range" };
  if (r.score > r.drops) return { ok: false, reason: "score-exceeds-drops" };
  if (r.perfects > r.drops) return { ok: false, reason: "perfects-exceed-drops" };
  if (r.bestCombo > r.perfects) return { ok: false, reason: "combo-exceeds-perfects" };
  const minMs = r.drops * MIN_DROP_MS[r.mode];
  if (r.durationMs < minMs) return { ok: false, reason: "too-fast" };
  const suspicious = (r.drops >= 30 && r.perfects / r.drops > 0.95) || r.durationMs < minMs * 1.5;
  return { ok: true, suspicious };
}
