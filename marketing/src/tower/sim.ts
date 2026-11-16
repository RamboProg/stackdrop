import { lighten, type Rgb } from "../lib/color.ts";
import { createRng, type Rng } from "../lib/random.ts";
import { blockColor, hueAt, isCalm, type Theme } from "../game/palette.ts";

/**
 * A deterministic replay of the game's tower mode (game/tower/TowerMode.kt), stepped at a fixed rate
 * so Remotion can render any frame. A script says where each module should land; the crane swings
 * exactly as in the game, and the "tap" happens at the moment a release would land there.
 */

// Constants from TowerMode.kt, StreakBonus.kt, Particles.kt and Hud.kt.
export const W = 4;
export const H = 2.6;
const HOOK_GAP = 0.4;
const GRAVITY = 38;
const MOMENTUM = 0.55;
const LOWER_TIME = 0.45;
const PERFECT_TOLERANCE = 0.28;
const FALL_OFF = W * 0.5;
export const FOUNDATION_HALF = 0.6;
const MAX_SHIFT = W * 0.6;
const BEND_HEIGHT = H * 8;
const MAX_SWAY = 0.35;
const SWAY_K = 10;
const SWAY_D = 2.6;
const SWAY_KICK = 0.9;
const WOBBLE = 0.04;
const ASSIST = W * 0.12;
const BONUS_DROP = H * 2.5;
export const DANGER = 0.7;
export const LIVES = 3;
const BREAK_ANGLE = 0.45;
const BOUNCE = 0.25;
const IMPACT_SPEED = 4;
const GROUND_GRIP = 5;
const IMPACT_GAP = 0.07;
const STRAIGHTEN_DELAY = 0.15;
const WAVE_STEP = 0.03;
const WAVE_TIME = 0.6;
const MIN_ZOOM = 0.15;
const STREAK_LENGTH = 5;
const STREAK_STACKS = 5;
const STREAK_LEAD_IN = 0.22;
const COMBO_TIME = 1;
const RING_TIME = 0.8;
const FLARE_GRAVITY = 1.4;
const MOTE_FLOAT = 0.05;
const SUBSTEPS = 4;

const streakFallTime = (piece: number): number => Math.max(0.07, 0.16 - piece * 0.02);

/**
 * Where a module should land, measured from the center of the floor below (0 is perfect; past ±2 it
 * slips off). [swings] lets that landing spot pass by that many times first, so the player "waits".
 * [after] holds the tap until that many seconds after the module is ready.
 */
export type TowerDrop = number | { offset: number; swings?: number; after?: number };

export type TowerEventKind =
  | "release" | "perfect" | "land" | "warn" | "slip" | "streakPiece" | "streakDone"
  | "collapse" | "break" | "impact";

export type TowerEvent = {
  frame: number;
  time: number;
  kind: TowerEventKind;
  combo: number;
  score: number;
  /** Streak piece index, or impact strength 0..1. */
  amount: number;
};

export type Floor = { x: number; y: number; color: Rgb; windows: number; lobby: boolean; settle: number; hold: number };
export type Tumble = {
  x: number; y: number; vx: number; vy: number; spin: number; color: Rgb; windows: number;
  fades: boolean; rotation: number; age: number; landed: boolean;
};
export type Flare = { x: number; y: number; vx: number; vy: number; life: number; maxLife: number; size: number; color: Rgb; gravity: number };
export type Ring = { x: number; y: number; radius: number; age: number; color: Rgb };
type Hook = "lowering" | "ready" | "falling" | "empty";

/** Everything the renderer needs for one frame. Arrays are copies. */
export type TowerFrame = {
  floors: Floor[];
  tumbles: Tumble[];
  flares: Flare[];
  rings: Ring[];
  camX: number;
  camY: number;
  zoom: number;
  shakeX: number;
  shakeY: number;
  hook: Hook;
  hookTime: number;
  phase: number;
  amp: number;
  fx: number;
  fy: number;
  nextColor: Rgb;
  nextWindows: number;
  streakActive: boolean;
  streakFall: number;
  bonusX: number;
  collapsing: boolean;
  broken: boolean;
  hinge: number;
  hingeX: number;
  hingeY: number;
  collapseAngle: number;
  bend: number;
  shownTop: number;
  shownBalance: number;
  sway: number;
  clock: number;
  score: number;
  scorePop: number;
  comboLabel: string;
  comboTime: number;
  lives: number;
  damageFlash: number;
  isOver: boolean;
  time: number;
};

export type TowerConfig = {
  theme: Theme;
  width: number;
  height: number;
  fps: number;
  frames: number;
  drops: TowerDrop[];
  seed?: number;
  /** Floors already built when the clip starts, with these landing offsets (cycled). */
  startFloors?: number;
  startOffsets?: number[];
  /** Perfect drops in a row before the clip starts. */
  startCombo?: number;
  /** Simulation speed at a simulated time: 1 is real time, 0.3 is slow motion. */
  timeScale?: (simTime: number) => number;
  /** Simulated seconds at which the crane stops and the camera pulls back over the whole tower. */
  showcaseAt?: number;
};

export type TowerResult = { frames: TowerFrame[]; events: TowerEvent[]; geometry: Geometry };

/** Screen layout from TowerMode.resize. */
export type Geometry = { w: number; h: number; u: number; scale: number; anchorY: number; pivotScreenY: number; rope: number; camMin: number };

export function geometry(width: number, height: number): Geometry {
  const scale = (width * 0.26) / W;
  const anchorY = height * 0.64;
  const pivotScreenY = height * 0.25;
  return {
    w: width, h: height, u: width / 100, scale, anchorY, pivotScreenY,
    rope: Math.max(3, (anchorY - pivotScreenY) / scale - H - HOOK_GAP - 4.5),
    camMin: (height * 0.2) / scale,
  };
}

/** Sideways offset of the tower at height [y]: a smooth curve that is largest at the top. */
export function shiftAt(bend: number, shownTop: number, y: number): number {
  const top = Math.max(shownTop, H);
  const t = y / top;
  return bend * shiftScale(top) * t * t;
}

export function slopeAt(bend: number, shownTop: number, y: number): number {
  const top = Math.max(shownTop, H);
  return (bend * shiftScale(top) * 2 * y) / (top * top);
}

const shiftScale = (top: number): number => MAX_SHIFT * Math.min(1, top / BEND_HEIGHT);

export function simulateTower(config: TowerConfig): TowerResult {
  const { theme, fps } = config;
  const g = geometry(config.width, config.height);
  const { w, h, scale, anchorY, pivotScreenY, rope, camMin } = g;
  const rng: Rng = createRng(config.seed ?? 1);
  const calm = isCalm(theme);
  const count = (n: number): number => Math.max(1, Math.floor(n * theme.particles));

  const floors: Floor[] = [];
  const tumbles: Tumble[] = [];
  const flares: Flare[] = [];
  const rings: Ring[] = [];
  const events: TowerEvent[] = [];
  const frames: TowerFrame[] = [];

  const cam = { x: 0, targetX: 0, y: camMin, targetY: camMin, zoom: 1, targetZoom: 1, shakeX: 0, shakeY: 0, shakeTime: 0, shakeDur: 1, shakeMag: 0 };
  let sc = scale;
  let hook: Hook = "lowering";
  let hookTime = 0;
  let phase = 0;
  let amp = 0.3;
  let omega = 2;
  let fx = 0, fy = 0, fvx = 0, fvy = 0;
  let nextColor: Rgb = 0xffffff;
  let nextWindows = 0;
  let bonusX = 0;
  let lean = 0, leanTarget = 0, shownTop = 0, sway = 0, swayV = 0, balance = 0, shownBalance = 0;
  let collapsing = false, broken = false, hinge = -1, hingeX = 0, hingeY = 0;
  let impactGap = 0, straightenIn = 0, collapseAngle = 0, collapseV = 0, collapseDir = 1;
  let lives = LIVES, combo = config.startCombo ?? 0, score = 0, isOver = false, clock = 0, damageFlash = 0;
  let streakRemaining = 0, streakElapsed = 0, streakDelay = 0;
  let scorePop = 0, comboLabel = "", comboTime = 0;
  let frame = 0, time = 0;
  let stopped = false;

  // Script state.
  let dropIndex = 0;
  let target = Number.NaN;
  let swingsLeft = 0;
  let notBefore = 0;
  let lastPrediction = Number.NaN;

  const emit = (kind: TowerEventKind, amount = 0): void => {
    events.push({ frame: frame + 1, time, kind, combo, score, amount });
  };
  const sx = (x: number): number => w / 2 + (x - cam.x) * sc + cam.shakeX;
  const sy = (y: number): number => anchorY - (y - cam.y) * sc + cam.shakeY;
  const bend = (): number => lean + sway + Math.sin(clock * 1.3) * WOBBLE * Math.min(score, 20) / 20;
  const shift = (y: number): number => shiftAt(bend(), shownTop, y);
  const pivotY = (): number => cam.y + (anchorY - pivotScreenY) / (scale * cam.zoom);

  const shake = (magnitude: number, duration: number): void => {
    const m = magnitude * theme.shake;
    const current = cam.shakeTime > 0 ? (cam.shakeMag * cam.shakeTime) / cam.shakeDur : 0;
    if (m < current) return;
    cam.shakeMag = m;
    cam.shakeDur = duration;
    cam.shakeTime = duration;
  };

  const showCombo = (label: string): void => {
    comboLabel = label;
    comboTime = COMBO_TIME;
  };

  // --- Particles (screen space, Particles.kt) ---
  const addFlares = (x: number, y: number, color: Rgb, n: number, speed: number): void => {
    for (let i = 0; i < n; i++) {
      const a = Math.PI + rng() * Math.PI;
      const s = speed * (0.35 + rng() * 0.65);
      const life = 0.4 + rng() * 0.45;
      flares.push({ x, y, vx: Math.cos(a) * s, vy: Math.sin(a) * s, life, maxLife: life, size: speed * (0.006 + rng() * 0.008), color, gravity: FLARE_GRAVITY });
    }
  };
  const addMotes = (x: number, y: number, color: Rgb, n: number, spread: number): void => {
    for (let i = 0; i < n; i++) {
      const life = 1 + rng() * 0.5;
      flares.push({
        x: x + (rng() - 0.5) * spread, y,
        vx: (rng() - 0.5) * spread * 0.3, vy: -spread * (0.3 + rng() * 0.3),
        life, maxLife: life, size: spread * (0.03 + rng() * 0.02), color, gravity: MOTE_FLOAT,
      });
    }
  };
  const addRing = (x: number, y: number, color: Rgb, radius: number): void => {
    rings.push({ x, y, radius, age: 0, color });
  };

  // --- Rules ---
  const updateDifficulty = (): void => {
    const sweep = W * Math.min(1.05, 0.42 + score * 0.022);
    amp = Math.asin(Math.min(0.9, sweep / rope));
    omega = Math.min(3.3, 1.9 + score * 0.04);
  };

  const worstOverhang = (): { ratio: number; floor: number } => {
    let sum = 0, n = 0, worst = 0, at = -1;
    for (let i = floors.length - 1; i >= 0; i--) {
      if (n > 0) {
        const r = (sum / n - floors[i].x) / (W * 0.5);
        if (Math.abs(r) > Math.abs(worst)) {
          worst = r;
          at = i;
        }
      }
      sum += floors[i].x;
      n++;
    }
    if (n > 0) {
      const r = sum / n / (W * FOUNDATION_HALF);
      if (Math.abs(r) > Math.abs(worst)) {
        worst = r;
        at = -1;
      }
    }
    return { ratio: worst, floor: at };
  };

  const pickLook = (): void => {
    nextColor = blockColor(theme, floors.length + 1);
    let bits = 0;
    for (let i = 0; i < 6; i++) if (rng() < 0.6) bits |= 1 << i;
    nextWindows = bits;
  };

  const nextTarget = (): void => {
    const d = config.drops[dropIndex];
    lastPrediction = Number.NaN;
    if (d === undefined) {
      target = Number.NaN;
      return;
    }
    target = typeof d === "number" ? d : d.offset;
    swingsLeft = typeof d === "number" ? 0 : (d.swings ?? 0);
    notBefore = typeof d === "number" ? 0 : (d.after ?? 0);
  };

  const spawn = (): void => {
    if (stopped) {
      hook = "empty";
      return;
    }
    hook = "lowering";
    hookTime = 0;
    pickLook();
    nextTarget();
  };

  const follow = (x: number, newTop: number): void => {
    cam.targetY = Math.max(camMin, newTop);
    cam.targetX = x + leanTarget * shiftScale(newTop);
    updateDifficulty();
  };

  const release = (): void => {
    const theta = amp * Math.sin(phase);
    const dTheta = amp * omega * Math.cos(phase);
    fx = cam.x + rope * Math.sin(theta);
    fy = pivotY() - rope * Math.cos(theta) - HOOK_GAP - H;
    fvx = rope * dTheta * Math.cos(theta) * MOMENTUM;
    fvy = rope * dTheta * Math.sin(theta) * MOMENTUM;
    hook = "falling";
    dropIndex++;
    emit("release");
  };

  /**
   * Where a module released right now would land, relative to the floor below. Steps a copy of the
   * fall and of the tower's bend with the same integrator, so the prediction matches the real landing.
   */
  const predictLanding = (dt: number): number => {
    const theta = amp * Math.sin(phase);
    const dTheta = amp * omega * Math.cos(phase);
    let px = cam.x + rope * Math.sin(theta);
    let py = pivotY() - rope * Math.cos(theta) - HOOK_GAP - H;
    const vx = rope * dTheta * Math.cos(theta) * MOMENTUM;
    let vy = rope * dTheta * Math.sin(theta) * MOMENTUM;
    let pl = lean, ps = sway, psv = swayV, pc = clock, pt = shownTop;
    const topY = floors.length * H;
    for (let i = 0; i < 2000; i++) {
      pc += dt;
      vy -= GRAVITY * dt;
      px += vx * dt;
      py += vy * dt;
      if (py <= topY) {
        const b = pl + ps + Math.sin(pc * 1.3) * WOBBLE * Math.min(score, 20) / 20;
        return px - shiftAt(b, pt, topY + H / 2) - (floors[floors.length - 1]?.x ?? 0);
      }
      pl += (leanTarget - pl) * (1 - Math.exp(-2.5 * dt));
      psv += (-SWAY_K * ps - SWAY_D * psv) * dt;
      ps = Math.max(-MAX_SWAY, Math.min(MAX_SWAY, ps + psv * dt));
      pt += (floors.length * H - pt) * (1 - Math.exp(-6 * dt));
    }
    return Number.NaN;
  };

  const land = (lx: number, topY: number): void => {
    const support = floors[floors.length - 1]?.x ?? 0;
    const offset = lx - support;
    if (Math.abs(offset) >= FALL_OFF) {
      fallOff(offset);
      return;
    }
    const perfect = Math.abs(offset) <= PERFECT_TOLERANCE;
    const assist = perfect ? -Math.sign(balance) * Math.min(ASSIST, Math.abs(balance) * ASSIST * 1.5) : 0;
    const x = perfect ? support + assist : lx;
    floors.push({ x, y: topY, color: nextColor, windows: nextWindows, lobby: floors.length === 0, settle: lx - x, hold: 0 });
    score = floors.length;

    const px = sx(lx + shift(topY + H / 2));
    const py = sy(topY);
    const glow = lighten(nextColor, 0.4);
    if (perfect) {
      combo++;
      swayV *= 0.3;
      sway *= 0.5;
      const label = combo === 1 ? "PERFECT" : `PERFECT x${combo}`;
      const completes = combo % STREAK_LENGTH === 0;
      showCombo(completes ? `${label}  STREAK!` : Math.abs(assist) > 0.05 ? `${label}  +BALANCE` : label);
      emit("perfect");
      if (calm) {
        addRing(px, py, glow, w * 0.12);
        addMotes(px, py, glow, count(6), w * 0.08);
      } else {
        addFlares(px, py, glow, 18 + Math.min(combo, 6) * 2, w * 0.5);
      }
    } else {
      combo = 0;
      swayV += (offset / W) * SWAY_KICK + fvx * 0.01;
      emit("land");
      if (calm) addMotes(px, py, glow, count(5), w * 0.1);
      else addFlares(px, py, glow, 12, w * 0.4);
      shake(w * 0.003, 0.1);
    }
    scorePop = 1;

    balance = worstOverhang().ratio;
    leanTarget = Math.sign(balance) * Math.min(1, Math.abs(balance)) ** 1.5;
    if (Math.abs(balance) > 1) {
      collapse();
      return;
    }
    if (Math.abs(balance) > DANGER) emit("warn");
    follow(x, topY + H);
    if (perfect && combo % STREAK_LENGTH === 0) {
      streakRemaining = STREAK_STACKS;
      streakElapsed = 0;
      streakDelay = STREAK_LEAD_IN;
      hook = "empty";
      pickLook();
      bonusX = floors[floors.length - 1]?.x ?? 0;
    } else {
      spawn();
    }
  };

  const updateStreak = (dt: number): void => {
    if (streakDelay > 0) {
      streakDelay -= dt;
      return;
    }
    streakElapsed += dt;
    const piece = STREAK_STACKS - streakRemaining;
    if (streakElapsed < streakFallTime(piece)) return;
    streakElapsed = 0;
    streakRemaining--;
    const topY = floors.length * H;
    floors.push({ x: bonusX, y: topY, color: nextColor, windows: nextWindows, lobby: floors.length === 0, settle: 0, hold: 0 });
    score = floors.length;
    scorePop = 1;
    const last = streakRemaining === 0;
    emit("streakPiece", piece);

    const px = sx(bonusX + shift(topY + H / 2));
    const py = sy(topY);
    const glow = lighten(nextColor, 0.4);
    if (calm) {
      addRing(px, py, glow, w * (last ? 0.2 : 0.1));
      addMotes(px, py, glow, count(last ? 14 : 4), w * 0.08);
    } else {
      addFlares(px, py, glow, last ? 40 : 12, w * (last ? 0.6 : 0.35));
    }
    balance = worstOverhang().ratio;
    leanTarget = Math.sign(balance) * Math.min(1, Math.abs(balance)) ** 1.5;
    follow(bonusX, topY + H);
    if (last) straightenIn = STRAIGHTEN_DELAY;
    else {
      pickLook();
      bonusX = floors[floors.length - 1]?.x ?? 0;
    }
  };

  /** The streak's payoff: every floor slides back over the foundation, bottom to top. */
  const straighten = (): void => {
    const step = Math.min(WAVE_STEP, WAVE_TIME / Math.max(1, floors.length));
    floors.forEach((f, i) => {
      f.settle += f.x;
      f.x = 0;
      f.hold = i * step;
    });
    balance = 0;
    leanTarget = 0;
    swayV *= 0.3;
    emit("streakDone");
    showCombo("BALANCED!  +5 GEMS");
    const top = floors.length * H;
    const glow = lighten(theme.accent, 0.3);
    if (calm) {
      addRing(sx(0), sy(top), glow, w * 0.25);
      addMotes(sx(0), sy(top), glow, count(16), w * 0.12);
    } else {
      addFlares(sx(0), sy(top), glow, 40, w * 0.6);
    }
    shake(w * 0.004, 0.15);
    follow(0, top);
    spawn();
  };

  const fallOff = (offset: number): void => {
    const dir = offset >= 0 ? 1 : -1;
    tumbles.push({ x: fx, y: fy + H / 2, vx: dir * 5 + fvx * 0.5, vy: 4, spin: dir * 220, color: nextColor, windows: nextWindows, fades: true, rotation: 0, age: 0, landed: false });
    lives--;
    combo = 0;
    damageFlash = 1;
    emit("slip");
    showCombo(lives > 0 ? "Slipped off" : "Last one slipped");
    shake(w * 0.005, 0.15);
    if (lives <= 0) endRun();
    else spawn();
  };

  const collapse = (): void => {
    collapsing = true;
    broken = false;
    collapseDir = balance >= 0 ? 1 : -1;
    collapseV = collapseDir * 0.2;
    collapseAngle = 0;
    hinge = worstOverhang().floor;
    hingeY = (hinge + 1) * H;
    hingeX = hinge >= 0 ? floors[hinge].x + collapseDir * W * 0.5 : collapseDir * W * FOUNDATION_HALF;
    emit("collapse");
    shake(w * 0.01, 0.3);
    endRun();
  };

  const endRun = (): void => {
    hook = "empty";
    streakRemaining = 0;
    isOver = true;
    const top = floors.length * H;
    cam.targetZoom = Math.max(MIN_ZOOM, Math.min(1, (h * 0.5) / ((top + H * 3) * scale)));
    cam.targetY = Math.max(top * 0.5, camMin * cam.targetZoom);
    cam.targetX = 0;
  };

  const breakApart = (): void => {
    broken = true;
    const c = Math.cos(collapseAngle);
    const s = Math.sin(collapseAngle);
    const px = hingeX + shift(hingeY);
    const swing = (collapseAngle * 180) / Math.PI;
    const spin = (collapseV * 180) / Math.PI;
    while (floors.length > hinge + 1) {
      const f = floors.pop() as Floor;
      const mid = f.y + H / 2;
      const dx = f.x + f.settle + shift(mid) - px;
      const dy = mid - hingeY;
      const rx = dx * c + dy * s;
      const ry = -dx * s + dy * c;
      const tilt = (Math.atan(slopeAt(bend(), shownTop, mid)) * 180) / Math.PI;
      tumbles.push({
        x: px + rx, y: hingeY + ry,
        vx: collapseV * ry + (rng() - 0.5) * 4,
        vy: -collapseV * rx + rng() * 3,
        spin: spin * (0.4 + rng() * 1.2), color: f.color, windows: f.windows,
        fades: false, rotation: tilt + swing, age: 0, landed: false,
      });
    }
    emit("break");
    shake(w * 0.008, 0.2);
  };

  const fall = (t: Tumble, dt: number): void => {
    t.vy -= GRAVITY * dt;
    t.x += t.vx * dt;
    t.y += t.vy * dt;
    t.rotation += t.spin * dt;
    const a = (t.rotation * Math.PI) / 180;
    const reach = Math.abs((W / 2) * Math.sin(a)) + Math.abs((H / 2) * Math.cos(a));
    if (t.y - reach >= 0) return;
    t.y = reach;
    if (!t.fades && t.vy < -IMPACT_SPEED) impact(t);
    t.vy = t.vy < -IMPACT_SPEED ? -t.vy * BOUNCE : 0;
    t.landed = true;
    const grip = Math.exp(-GROUND_GRIP * dt);
    t.vx *= grip;
    t.spin *= grip;
    const flat = Math.round(t.rotation / 90) * 90;
    t.rotation += (flat - t.rotation) * (1 - Math.exp(-6 * dt));
  };

  const impact = (t: Tumble): void => {
    const strength = Math.min(1, -t.vy / 30);
    if (!t.landed) addMotes(sx(t.x), sy(0), lighten(t.color, 0.3), count(4), w * 0.06);
    if (impactGap > 0) return;
    impactGap = IMPACT_GAP;
    emit("impact", strength);
    shake(w * 0.004 * strength, 0.12);
  };

  const showcase = (): void => {
    stopped = true;
    hook = "empty";
    const top = floors.length * H;
    cam.targetZoom = Math.max(MIN_ZOOM, Math.min(1, (h * 0.5) / ((top + H * 3) * scale)));
    cam.targetY = Math.max(top * 0.5, camMin * cam.targetZoom);
    cam.targetX = 0;
  };

  const step = (dt: number): void => {
    time += dt;
    sc = scale * cam.zoom;
    clock += dt;
    phase += omega * dt;
    hookTime += dt;
    if (config.showcaseAt !== undefined && !stopped && !isOver && time >= config.showcaseAt && hook !== "falling" && streakRemaining === 0 && straightenIn <= 0) {
      showcase();
    }
    if (hook === "lowering" && hookTime >= LOWER_TIME) hook = "ready";
    if (hook === "falling") {
      fvy -= GRAVITY * dt;
      fx += fvx * dt;
      fy += fvy * dt;
      const topY = floors.length * H;
      if (fy <= topY) land(fx - shift(topY + H / 2), topY);
    }
    if (streakRemaining > 0 && !isOver) updateStreak(dt);

    if (collapsing) {
      if (!broken) {
        collapseV += collapseDir * (1.5 + Math.abs(collapseAngle) * 7) * dt;
        collapseAngle += collapseV * dt;
        if (Math.abs(collapseAngle) >= BREAK_ANGLE) breakApart();
      }
    } else {
      lean += (leanTarget - lean) * (1 - Math.exp(-2.5 * dt));
      swayV += (-SWAY_K * sway - SWAY_D * swayV) * dt;
      sway = Math.max(-MAX_SWAY, Math.min(MAX_SWAY, sway + swayV * dt));
    }
    shownBalance += (balance - shownBalance) * (1 - Math.exp(-6 * dt));
    shownTop += (floors.length * H - shownTop) * (1 - Math.exp(-6 * dt));
    const settleDecay = Math.exp(-10 * dt);
    for (const f of floors) {
      if (f.hold > 0) f.hold -= dt;
      else f.settle *= settleDecay;
    }
    if (straightenIn > 0) {
      straightenIn -= dt;
      if (straightenIn <= 0) straighten();
    }
    impactGap = Math.max(0, impactGap - dt);
    for (let i = tumbles.length - 1; i >= 0; i--) {
      const t = tumbles[i];
      t.age += dt;
      fall(t, dt);
      if (t.fades && t.age > 3) tumbles.splice(i, 1);
    }
    for (let i = flares.length - 1; i >= 0; i--) {
      const p = flares[i];
      p.life -= dt;
      if (p.life <= 0) {
        flares.splice(i, 1);
        continue;
      }
      p.vy += w * p.gravity * dt;
      p.x += p.vx * dt;
      p.y += p.vy * dt;
    }
    for (let i = rings.length - 1; i >= 0; i--) {
      rings[i].age += dt;
      if (rings[i].age > RING_TIME) rings.splice(i, 1);
    }

    cam.x += (cam.targetX - cam.x) * (1 - Math.exp(-3 * dt));
    cam.y += (cam.targetY - cam.y) * (1 - Math.exp(-7 * dt));
    cam.zoom += (cam.targetZoom - cam.zoom) * (1 - Math.exp(-2.5 * dt));
    if (cam.shakeTime > 0) {
      cam.shakeTime -= dt;
      const m = cam.shakeMag * Math.max(0, cam.shakeTime / cam.shakeDur);
      cam.shakeX = (rng() * 2 - 1) * m;
      cam.shakeY = (rng() * 2 - 1) * m;
    } else {
      cam.shakeX = 0;
      cam.shakeY = 0;
    }
    damageFlash = Math.max(0, damageFlash - dt * 2.2);
    scorePop = Math.max(0, scorePop - dt * 4);
    comboTime = Math.max(0, comboTime - dt);

    // The scripted "tap": release when the predicted landing passes the target.
    if (hook === "ready" && !isOver && !Number.isNaN(target) && hookTime - LOWER_TIME >= notBefore) {
      const p = predictLanding(dt);
      if (!Number.isNaN(lastPrediction) && (lastPrediction - target) * (p - target) <= 0) {
        if (swingsLeft > 0) swingsLeft--;
        else release();
      }
      lastPrediction = p;
    }
  };

  // Pre-built floors, placed like real landings so the lean and balance meter start where they would.
  const offsets = config.startOffsets ?? [0];
  for (let i = 0; i < (config.startFloors ?? 0); i++) {
    pickLook();
    const support = floors[floors.length - 1]?.x ?? 0;
    floors.push({ x: support + offsets[i % offsets.length], y: floors.length * H, color: nextColor, windows: nextWindows, lobby: i === 0, settle: 0, hold: 0 });
  }
  score = floors.length;
  balance = worstOverhang().ratio;
  leanTarget = Math.sign(balance) * Math.min(1, Math.abs(balance)) ** 1.5;
  lean = leanTarget;
  shownBalance = balance;
  shownTop = floors.length * H;
  if (Math.abs(balance) > 1) throw new Error(`startOffsets topple the tower (balance ${balance.toFixed(2)})`);
  const top = floors.length * H;
  cam.targetY = Math.max(camMin, top);
  cam.targetX = (floors[floors.length - 1]?.x ?? 0) + leanTarget * shiftScale(top);
  cam.x = cam.targetX;
  cam.y = cam.targetY;
  updateDifficulty();
  spawn();

  const substeps = Math.max(1, Math.round((SUBSTEPS * 60) / fps));
  for (frame = 0; frame < config.frames; frame++) {
    const b = bend();
    frames.push({
      floors: floors.map((f) => ({ ...f })),
      tumbles: tumbles.map((t) => ({ ...t })),
      flares: flares.map((f) => ({ ...f })),
      rings: rings.map((r) => ({ ...r })),
      camX: cam.x, camY: cam.y, zoom: cam.zoom, shakeX: cam.shakeX, shakeY: cam.shakeY,
      hook, hookTime, phase, amp, fx, fy, nextColor, nextWindows,
      streakActive: streakRemaining > 0 && !isOver,
      streakFall: streakRemaining > 0 && streakDelay <= 0 ? (Math.min(1, streakElapsed / streakFallTime(STREAK_STACKS - streakRemaining))) ** 2 : 0,
      bonusX, collapsing, broken, hinge, hingeX, hingeY, collapseAngle,
      bend: b, shownTop, shownBalance, sway, clock, score, scorePop, comboLabel, comboTime, lives, damageFlash, isOver, time,
    });
    const dt = (config.timeScale?.(time) ?? 1) / fps / substeps;
    for (let s = 0; s < substeps; s++) step(dt);
  }

  return { frames, events, geometry: g };
}

/** Hue drift of the synthwave sky as the tower grows, like TowerMode.render. */
export const towerDrift = (theme: Theme, score: number): number => (hueAt(theme, score) - theme.baseHue) * 0.3;

export { MIN_ZOOM, BONUS_DROP, HOOK_GAP };
