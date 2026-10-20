import { randomInt } from "node:crypto";
import { AVATAR_COUNT } from "./config";

/** Crockford base32: digits plus letters without I, L, O and U, so codes are easy to read aloud. */
export const CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
export const CODE_LENGTH = 8;

export function newFriendCode(): string {
  let code = "";
  for (let i = 0; i < CODE_LENGTH; i++) code += CODE_ALPHABET[randomInt(CODE_ALPHABET.length)];
  return code;
}

/**
 * Accepts what a player types: any case, with or without the dash or spaces, and the usual
 * Crockford look-alikes (O for 0, I or L for 1). Returns the stored form, or null if invalid.
 */
export function normalizeCode(input: string): string | null {
  const s = input.toUpperCase().replace(/[\s-]/g, "").replace(/O/g, "0").replace(/[IL]/g, "1");
  if (s.length !== CODE_LENGTH) return null;
  for (const ch of s) if (!CODE_ALPHABET.includes(ch)) return null;
  return s;
}

/** Display form, e.g. "K7Q2-M9XD". */
export function formatCode(code: string): string {
  return `${code.slice(0, 4)}-${code.slice(4)}`;
}

/** Same hash the app uses, so a guest keeps the same avatar after choosing a name. */
export function defaultAvatar(uid: string): number {
  let sum = 0;
  for (const ch of uid) sum += ch.charCodeAt(0);
  return sum % AVATAR_COUNT;
}
