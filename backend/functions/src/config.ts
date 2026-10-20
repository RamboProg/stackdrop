import { setGlobalOptions } from "firebase-functions/v2";

/** Must match OnlineServices.REGION in the Android app and the Firestore location. */
export const REGION = "us-central1";

/** Preset avatars drawn on the client; profiles store an index into them. */
export const AVATAR_COUNT = 12;

export const MODES = ["stack", "tower"] as const;
export type Mode = (typeof MODES)[number];

setGlobalOptions({ region: REGION, maxInstances: 10 });
