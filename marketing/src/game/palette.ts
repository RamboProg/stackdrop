import { hsv, type Rgb } from "../lib/color.ts";

/** Port of game/Palette.kt and game/CalmThemes.kt. */

export type Family = "calm" | "neon";
export type Scenery = "synth" | "hills" | "forest" | "lagoon" | "aurora" | "petals" | "dunes" | "slate";
export type Ambient = "stars" | "none" | "fog" | "bubbles" | "aurora" | "petals" | "motes";

export type Theme = {
  id: string;
  name: string;
  cost: number;
  family: Family;
  mood: string;
  baseHue: number;
  /** Degrees per level when [hueRange] is 0, otherwise radians per level of the oscillation. */
  hueStep: number;
  hueRange: number;
  sat: number;
  value: number;
  /** Neon: depth of the slow brightness wave. Calm: how much darker every other layer is. */
  valueWave: number;
  skyTop: Rgb;
  skyMid: Rgb;
  skyBottom: Rgb;
  ground: Rgb;
  scenery: Scenery;
  ambient: Ambient;
  accent: Rgb;
  accent2: Rgb;
  text: Rgb;
  textDim: Rgb;
  panel: Rgb;
  danger: Rgb;
  /** 1 is full neon; calm themes use 0.2. */
  glow: number;
  scanlines: boolean;
  vignette: number;
  shake: number;
  particles: number;
  /** Light sky: dark text. */
  light: boolean;
  milestone?: { mode: "stack" | "tower"; score: number };
  premiumOnly: boolean;
  /** Neon only: the synthwave sky derives from these. */
  bgHue: number;
  bgSat: number;
};

export const isCalm = (t: Theme): boolean => t.family === "calm";

const NEON_DIM = 0xb8b4cc;
const NEON_RED = 0xff3d6e;

function neon(
  id: string, name: string, cost: number,
  baseHue: number, hueStep: number, hueRange: number, sat: number, value: number, valueWave: number,
  bgHue: number, bgSat: number, accentHue: number, accent2Hue: number, accentSat: number, mood: string,
): Theme {
  return {
    id, name, cost, family: "neon", mood, baseHue, hueStep, hueRange, sat, value, valueWave,
    skyTop: hsv(bgHue, bgSat, 0.17), skyMid: hsv(bgHue + 15, bgSat, 0.11),
    skyBottom: hsv(bgHue + 30, bgSat, 0.05), ground: hsv(bgHue, bgSat, 0.035),
    scenery: "synth", ambient: "stars",
    accent: hsv(accentHue, accentSat, 1), accent2: hsv(accent2Hue, accentSat, 1),
    text: 0xffffff, textDim: NEON_DIM, panel: 0x000000, danger: NEON_RED,
    glow: 1, scanlines: true, vignette: 1, shake: 1, particles: 1, light: false, premiumOnly: false,
    bgHue, bgSat,
  };
}

type CalmSpec = {
  id: string; name: string; cost: number; mood: string;
  baseHue: number; hueRange: number; sat: number; value: number;
  skyTop: Rgb; skyMid: Rgb; skyBottom: Rgb; ground: Rgb; scenery: Scenery; ambient: Ambient;
  accent: Rgb; accent2: Rgb; text: Rgb; textDim: Rgb; panel: Rgb; danger: Rgb; light: boolean;
  milestone?: Theme["milestone"]; premiumOnly?: boolean;
};

function calm(s: CalmSpec): Theme {
  return {
    ...s, family: "calm", hueStep: 0.22, valueWave: 0.085,
    glow: 0.2, scanlines: false, vignette: 0.35, shake: 0.5, particles: 0.6,
    premiumOnly: s.premiumOnly ?? false, bgHue: 0, bgSat: 0,
  };
}

export const CALM_THEMES: Theme[] = [
  calm({
    id: "dusk", name: "Dusk", cost: 0, mood: "warm", baseHue: 0, hueRange: 15, sat: 0.32, value: 0.88,
    skyTop: 0x2e2f4f, skyMid: 0x6b5b7b, skyBottom: 0xd9a3a0, ground: 0x3a2e44, scenery: "hills", ambient: "stars",
    accent: 0xf2c9a6, accent2: 0xe8a0a8, text: 0xf6efe8, textDim: 0xc9bfc8, panel: 0x24253d, danger: 0xe58c8a, light: false,
  }),
  calm({
    id: "mist", name: "Mist", cost: 60, mood: "quiet", baseHue: 157, hueRange: 17, sat: 0.25, value: 0.81,
    skyTop: 0xe4ece8, skyMid: 0xc9d8d2, skyBottom: 0xa9bfb8, ground: 0x6f8c83, scenery: "forest", ambient: "fog",
    accent: 0x6e8f86, accent2: 0x8fb3a0, text: 0x2f3b38, textDim: 0x4f625d, panel: 0xf1f5f3, danger: 0xc0605a, light: true,
  }),
  calm({
    id: "lagoon", name: "Lagoon", cost: 120, mood: "deep", baseHue: 185, hueRange: 15, sat: 0.38, value: 0.82,
    skyTop: 0x0f2a3a, skyMid: 0x174456, skyBottom: 0x1f5c66, ground: 0x12313c, scenery: "lagoon", ambient: "bubbles",
    accent: 0x8fd3c8, accent2: 0xf0c987, text: 0xe8f4f2, textDim: 0xa8c9c4, panel: 0x0b2230, danger: 0xf09a8a, light: false,
    milestone: { mode: "stack", score: 40 },
  }),
  calm({
    id: "aurora", name: "Aurora", cost: 200, mood: "arctic", baseHue: 220, hueRange: 25, sat: 0.3, value: 0.86,
    skyTop: 0x0b1026, skyMid: 0x16224a, skyBottom: 0x24365e, ground: 0x1a2440, scenery: "aurora", ambient: "aurora",
    accent: 0x9fe3c9, accent2: 0xb9a8f0, text: 0xeef2fa, textDim: 0xb4bed4, panel: 0x0a0e22, danger: 0xf29aae, light: false,
    milestone: { mode: "tower", score: 25 },
  }),
  calm({
    id: "sakura", name: "Sakura", cost: 300, mood: "gentle", baseHue: 2, hueRange: 17, sat: 0.26, value: 0.92,
    skyTop: 0xfbedea, skyMid: 0xf7dcdd, skyBottom: 0xeec7cc, ground: 0xc99aa3, scenery: "petals", ambient: "petals",
    accent: 0xd98c9c, accent2: 0xe7a9b5, text: 0x4a3a40, textDim: 0x7a5f68, panel: 0xfdf5f3, danger: 0xc9606f, light: true,
  }),
  calm({
    id: "dune", name: "Dune", cost: 450, mood: "sunny", baseHue: 27, hueRange: 12, sat: 0.4, value: 0.86,
    skyTop: 0xf6e7cf, skyMid: 0xeed3ae, skyBottom: 0xe2b98f, ground: 0xc8925f, scenery: "dunes", ambient: "none",
    accent: 0xc7774f, accent2: 0xe3a765, text: 0x4a3528, textDim: 0x725545, panel: 0xfbf2e4, danger: 0xb9553a, light: true,
  }),
  calm({
    id: "slate", name: "Slate", cost: 600, mood: "minimal", baseHue: 220, hueRange: 10, sat: 0.22, value: 0.81,
    skyTop: 0x15171b, skyMid: 0x1a1d23, skyBottom: 0x1f232a, ground: 0x1b1e24, scenery: "slate", ambient: "motes",
    accent: 0xa9b8cc, accent2: 0xc9d2de, text: 0xe6e9ee, textDim: 0xa3abb8, panel: 0x101216, danger: 0xe0868a, light: false,
  }),
  calm({
    id: "gilded", name: "Gilded", cost: 0, mood: "regal", baseHue: 42, hueRange: 10, sat: 0.4, value: 0.86,
    skyTop: 0x1c1530, skyMid: 0x2e2244, skyBottom: 0x4a3555, ground: 0x241a33, scenery: "hills", ambient: "motes",
    accent: 0xe9c46a, accent2: 0xf4d58d, text: 0xf7f0e1, textDim: 0xcfc3a8, panel: 0x17112a, danger: 0xe8897f, light: false,
    premiumOnly: true,
  }),
];

export const NEON_THEMES: Theme[] = [
  neon("classic", "Synthwave", 0, 300, 9, 0, 0.75, 1, 0, 265, 0.75, 315, 40, 0.85, "electric"),
  neon("pastel", "Vapor", 50, 175, 8, 0, 0.5, 1, 0, 280, 0.55, 180, 300, 0.6, "dreamy"),
  neon("neon", "Toxic", 120, 105, 0.13, 30, 0.9, 1, 0, 160, 0.8, 95, 55, 0.9, "acid"),
  neon("sunset", "Inferno", 200, 18, 0.15, 22, 0.9, 1, 0, 350, 0.85, 15, 50, 0.95, "fiery"),
  neon("ocean", "Ice", 300, 195, 0.1, 25, 0.65, 1, 0, 225, 0.8, 190, 235, 0.7, "icy"),
  neon("mono", "Chrome", 500, 0, 0, 0, 0, 0.95, 0.4, 230, 0.15, 210, 0, 0.05, "sleek"),
];

export const ALL_THEMES: Theme[] = [...CALM_THEMES, ...NEON_THEMES];

export function themeById(id: string): Theme {
  const t = ALL_THEMES.find((x) => x.id === id);
  if (!t) throw new Error(`Unknown theme id "${id}"`);
  return t;
}

/** The themes by name, for ad scripts. */
export const THEMES = {
  synthwave: themeById("classic"),
  vapor: themeById("pastel"),
  toxic: themeById("neon"),
  inferno: themeById("sunset"),
  ice: themeById("ocean"),
  chrome: themeById("mono"),
  dusk: themeById("dusk"),
  mist: themeById("mist"),
  lagoon: themeById("lagoon"),
  aurora: themeById("aurora"),
  sakura: themeById("sakura"),
  dune: themeById("dune"),
  slate: themeById("slate"),
  gilded: themeById("gilded"),
};

export function hueAt(t: Theme, level: number): number {
  return t.hueRange > 0 ? t.baseHue + Math.sin(level * t.hueStep) * t.hueRange : t.baseHue + level * t.hueStep;
}

export function blockColor(t: Theme, level: number): Rgb {
  if (isCalm(t)) return hsv(t.baseHue + Math.sin(level * t.hueStep) * t.hueRange, t.sat, t.value - t.valueWave * (level & 1));
  const v = t.value - t.valueWave * (0.5 + 0.5 * Math.sin(level * 0.45));
  return hsv(hueAt(t, level), t.sat, v);
}

export const bgTop = (t: Theme, drift: number): Rgb => hsv(t.bgHue + drift, t.bgSat, 0.17);
export const bgBottom = (t: Theme, drift: number): Rgb => hsv(t.bgHue + drift + 30, t.bgSat, 0.05);
export const floorColor = (t: Theme, drift: number): Rgb => hsv(t.bgHue + drift, t.bgSat, 0.035);
