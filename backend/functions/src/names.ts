export const NAME_MIN = 3;
export const NAME_MAX = 16;

const NAME_CHARS = /^[A-Za-z0-9_ ]+$/;
/** Firestore reserves document IDs like this, and the lower-case name is a document ID. */
const RESERVED_ID = /^__.*__$/;

export type NameProblem = "length" | "characters" | "spaces" | "banned";

export type NameCheck =
  | { ok: true; name: string; lower: string }
  | { ok: false; reason: NameProblem };

/** 3–16 characters from letters, digits, underscore and space; trimmed; no double spaces; not offensive. */
export function checkName(raw: string): NameCheck {
  const name = raw.trim();
  if (name.length < NAME_MIN || name.length > NAME_MAX) return { ok: false, reason: "length" };
  if (!NAME_CHARS.test(name) || RESERVED_ID.test(name)) return { ok: false, reason: "characters" };
  if (name.includes("  ")) return { ok: false, reason: "spaces" };
  if (isBanned(name)) return { ok: false, reason: "banned" };
  return { ok: true, name, lower: name.toLowerCase() };
}

// A small starter list, not an exhaustive one; extend it as reports come in. Words in ANYWHERE are
// blocked inside other words too, so they must not be common substrings of innocent names.
// WHOLE words are only blocked as a separate word or the entire name ("Cassie" and "Grape" pass).
const ANYWHERE = [
  "fuck", "shit", "cunt", "bitch", "whore", "slut", "nigger", "nigga", "faggot", "retard",
  "hitler", "porn", "pussy", "penis", "vagina", "wank", "twat", "dildo", "bastard",
];
const WHOLE = [
  "ass", "arse", "rape", "fag", "dick", "cock", "cum", "sex", "tit", "tits", "spic", "chink", "kike",
  "nazi", "admin", "moderator", "official", "staff", "support", "stackdrop", "developer",
];

const LEET: Record<string, string> = {
  "0": "o", "1": "i", "!": "i", "3": "e", "4": "a", "@": "a", "5": "s", "$": "s", "7": "t", "8": "b", "9": "g",
};

/** Lower case, leetspeak undone, only letters kept, runs of three or more letters cut to two. */
export function normalizeForFilter(s: string): string {
  let out = "";
  for (const ch of s.toLowerCase()) {
    const c = LEET[ch] ?? ch;
    if (c >= "a" && c <= "z") out += c;
  }
  return out.replace(/(.)\1{2,}/g, "$1$1");
}

const collapse = (s: string): string => s.replace(/(.)\1+/g, "$1");

// Words without doubled letters can also be caught when a player stretches them ("fuuuck").
const ANYWHERE_SINGLE = ANYWHERE.filter((w) => collapse(w) === w);

export function isBanned(name: string): boolean {
  const flat = normalizeForFilter(name);
  const single = collapse(flat);
  if (ANYWHERE.some((w) => flat.includes(w))) return true;
  if (ANYWHERE_SINGLE.some((w) => single.includes(w))) return true;
  if (WHOLE.includes(flat)) return true;
  return name.split(/[\s_]+/).map(normalizeForFilter).some((t) => WHOLE.includes(t));
}

/** A few free-looking alternatives for a taken name, e.g. "Mo_92" -> "Mo_921", "Mo_92x". */
export function suggestions(name: string, rand: () => number = Math.random): string[] {
  const cut = (n: number): string => name.slice(0, n).trimEnd();
  const digit = 1 + Math.floor(rand() * 9);
  const pair = 10 + Math.floor(rand() * 90);
  const candidates = [
    `${cut(NAME_MAX - 1)}${digit}`,
    `${cut(NAME_MAX - 1)}x`,
    `${cut(NAME_MAX - 3)}_${pair}`,
    `${cut(NAME_MAX - 2)}${pair}`,
    `The ${cut(NAME_MAX - 4)}`,
  ];
  const seen = new Set<string>();
  return candidates.filter((c) => {
    const check = checkName(c);
    if (!check.ok || seen.has(check.lower) || check.lower === name.trim().toLowerCase()) return false;
    seen.add(check.lower);
    return true;
  });
}
