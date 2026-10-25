/** Guest accounts unused for this long are deleted, with everything they own. */
export const GUEST_RETENTION_DAYS = 365;
const DAY_MS = 24 * 60 * 60 * 1000;

/** The parts of an Auth user record the retention rule looks at. */
export interface UserActivity {
  uid: string;
  /** Sign-in providers; empty for a guest (anonymous) account. */
  providers: string[];
  /** RFC 1123 times from Auth metadata; lastRefreshTime moves whenever the game is opened. */
  creationTime: string;
  lastSignInTime?: string | null;
  lastRefreshTime?: string | null;
}

/**
 * Guests whose most recent activity is older than the retention period. A guest has no email or
 * Google account, so after an uninstall nobody could ask for it to be deleted; this does it for them.
 */
export function inactiveGuests(users: UserActivity[], now: number): string[] {
  const cutoff = now - GUEST_RETENTION_DAYS * DAY_MS;
  return users
    .filter((u) => u.providers.length === 0)
    .filter((u) => lastActive(u) < cutoff)
    .map((u) => u.uid);
}

function lastActive(u: UserActivity): number {
  const times = [u.creationTime, u.lastSignInTime, u.lastRefreshTime]
    .filter((t): t is string => typeof t === "string" && t.length > 0)
    .map((t) => Date.parse(t))
    .filter((t) => !Number.isNaN(t));
  // No readable time at all: treat as active, so a malformed record is never deleted by mistake.
  return times.length > 0 ? Math.max(...times) : Number.POSITIVE_INFINITY;
}
