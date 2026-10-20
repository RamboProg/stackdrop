import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { CallableRequest, HttpsError } from "firebase-functions/v2/https";

initializeApp();

export const db = getFirestore();

/** Every callable uses these options: App Check is required, so only the genuine app can call. */
export const CALLABLE = { enforceAppCheck: true } as const;

export function requireAuth(req: CallableRequest<unknown>): string {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "sign-in-required");
  return uid;
}

/** The request payload as a plain object, or an invalid-argument error. */
export function payload(req: CallableRequest<unknown>): Record<string, unknown> {
  const data = req.data;
  if (typeof data !== "object" || data === null || Array.isArray(data)) {
    throw new HttpsError("invalid-argument", "bad-payload");
  }
  return data as Record<string, unknown>;
}

export function stringField(data: Record<string, unknown>, key: string): string {
  const v = data[key];
  if (typeof v !== "string") throw new HttpsError("invalid-argument", `bad-${key}`);
  return v;
}

export function intField(data: Record<string, unknown>, key: string, min: number, max: number): number {
  const v = data[key];
  if (typeof v !== "number" || !Number.isInteger(v) || v < min || v > max) {
    throw new HttpsError("invalid-argument", `bad-${key}`);
  }
  return v;
}

export function optionalIntField(
  data: Record<string, unknown>, key: string, min: number, max: number,
): number | undefined {
  return data[key] === undefined || data[key] === null ? undefined : intField(data, key, min, max);
}
