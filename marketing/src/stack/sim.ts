import { lighten, type Rgb } from "../lib/color.ts";
import { createRng, type Rng } from "../lib/random.ts";
import { blockColor, hueAt, type Theme } from "../game/palette.ts";

/**
 * A deterministic replay of the game's stack mode (game/StackMode.kt), stepped at a fixed rate so
 * Remotion can render any frame. Instead of tap times, a script says where each slab lands; the
 * slab slides exactly as in the game and the "tap" happens when it reaches that spot.
 */

/** Axis-aligned box in world units: x/z are the min corner, y is the bottom. */
export type Block = { x: number; z: number; w: number; d: number; y: number; h: number; color: Rgb };

/**
 * Where a slab should land, measured from the block below along the slab's travel axis.
 * - a number: the offset (0 is a perfect drop; a non-zero offset slices off that much)
 * - `{ miss }`: overshoot the block below by [miss] units (negative for the near side), ending the run
 * [bounces] lets the slab bounce off the end of its track that many times first, to build suspense.
 */
export type Drop = number | { offset: number; bounces?: number } | { miss: number; bounces?: number };

export type SimEventKind = "perfect" | "cut" | "miss" | "grow" | "streakPiece" | "streakDone";

export type SimEvent = {
  frame: number;
  kind: SimEventKind;
  combo: number;
  score: number;
  /** Simulated seconds when it happened. */
  time: number;
  /** Streak piece index, or how much of the slab a cut removed (0..1). */
  amount: number;
};

export type DebrisView = { block: Block; rotation: number; alpha: number };
export type SparkView = { x: number; y: number; z: number; size: number; color: Rgb; alpha: number };
export type RippleView = { x: number; z: number; w: number; d: number; y: number; age: number; color: Rgb };

export type FrameState = {
  towerCount: number;
  moving: Block | null;
  bonus: Block | null;
  debris: DebrisView[];
  sparks: SparkView[];
  ripples: RippleView[];
  camY: number;
  zoom: number;
  shakeX: number;
  shakeY: number;
  drift: number;
  score: number;
  scorePop: number;
  comboLabel: string;
  comboTime: number;
  isOver: boolean;
  /** Seconds of simulated time, which runs slower than video time during slow motion. */
  time: number;
};

export type SimConfig = {
  theme: Theme;
  width: number;
  height: number;
  fps: number;
  frames: number;
  drops: Drop[];
  seed?: number;
  /** Levels already stacked when the clip starts. */
  startScore?: number;
  /** Width and depth of the top pre-stacked level; levels below narrow evenly towards it. */
  startSize?: [number, number];
  /** Simulation speed at a simulated time: 1 is real time, 0.25 is slow motion. */
  timeScale?: (simTime: number) => number;
  /** Simulated seconds at which the slab stops and the camera pulls back to show the whole tower. */
  zoomOutAt?: number;
};

export type SimResult = {
  tower: Block[];
  frames: FrameState[];
  events: SimEvent[];
  baseScale: number;
};

// Constants from StackMode.kt, StreakBonus.kt, Particles.kt, Debris.kt and Hud.kt.
export const BASE = 10;
export const SLAB_H = 1;
const PEDESTAL_H = 60;
const RANGE = 14;
const PERFECT_TOLERANCE = 0.28;
const START_SPEED = 11;
const SPEED_STEP = 0.3;
const MAX_SPEED = 24;
const GROW_COMBO = 8;
const GROW_AMOUNT = 0.7;
const DEBRIS_PUSH = 3;
const BONUS_DROP = 3;
const MIN_ZOOM = 0.08;
const STREAK_LENGTH = 5;
const STREAK_STACKS = 5;
const STREAK_LEAD_IN = 0.22;
const DEBRIS_GRAVITY = 40;
const DEBRIS_LIFETIME = 2;
const SPARK_GRAVITY = 20;
const RIPPLE_TIME = 0.6;
const RING_DELAY = 0.12;
const COMBO_TIME = 1;
const SUBSTEPS = 2;

const streakFallTime = (piece: number): number => Math.max(0.07, 0.16 - piece * 0.02);

type Debris = { block: Block; vx: number; vz: number; vy: number; rotation: number; spin: number; age: number };
type Spark = {
  x: number; y: number; z: number; vx: number; vy: number; vz: number;
  life: number; maxLife: number; size: number; color: Rgb;
};

const clone = (b: Block): Block => ({ ...b });

export function simulate(config: SimConfig): SimResult {
  const { theme, width, height, fps, drops } = config;
  const rng: Rng = createRng(config.seed ?? 1);
  const baseScale = (width * 0.6) / (BASE * 2 * Math.cos(Math.PI / 6));

  const tower: Block[] = [{ x: -BASE / 2, z: -BASE / 2, w: BASE, d: BASE, y: -PEDESTAL_H, h: PEDESTAL_H, color: blockColor(theme, 0) }];
  const debris: Debris[] = [];
  const sparks: Spark[] = [];
  const ripples: RippleView[] = [];
  const events: SimEvent[] = [];
  const frames: FrameState[] = [];

  let score = config.startScore ?? 0;
  prebuild(tower, theme, score, config.startSize ?? [BASE, BASE], rng);

  let combo = 0;
  let speed = Math.min(MAX_SPEED, START_SPEED + score * SPEED_STEP);
  let axisX = score % 2 === 1;
  let direction = 1;
  let moving: Block | null = null;
  let bonus: Block | null = null;
  let isOver = false;
  let stopped = false;
  let dropIndex = 0;
  let bouncesLeft = 0;
  let target = 0;
  let frame = 0;
  let time = 0;

  let streakRemaining = 0;
  let streakElapsed = 0;
  let streakDelay = 0;

  const top = (): Block => tower[tower.length - 1];
  const camera = { y: 0, targetY: 0, zoom: 1, targetZoom: 1, shakeX: 0, shakeY: 0, shakeTime: 0, shakeDur: 1, shakeMag: 0 };
  camera.targetY = top().y + top().h + SLAB_H;
  camera.y = camera.targetY;
  const driftTarget = (): number => (hueAt(theme, score) - theme.baseHue) * 0.3;
  let drift = driftTarget();
  let scorePop = 0;
  let comboLabel = "";
  let comboTime = 0;

  const emit = (kind: SimEventKind, amount = 0): void => {
    events.push({ frame: frame + 1, kind, combo, score, time, amount });
  };

  const shake = (magnitude: number, duration: number): void => {
    const current = camera.shakeTime > 0 ? (camera.shakeMag * camera.shakeTime) / camera.shakeDur : 0;
    if (magnitude < current) return;
    camera.shakeMag = magnitude;
    camera.shakeDur = duration;
    camera.shakeTime = duration;
  };

  const ripple = (b: Block, count: number, color: Rgb): void => {
    for (let i = 0; i < count; i++) ripples.push({ x: b.x, z: b.z, w: b.w, d: b.d, y: b.y + b.h, age: -i * RING_DELAY, color });
  };

  const burst = (b: Block, count: number, color: Rgb): void => {
    const cx = b.x + b.w / 2;
    const cz = b.z + b.d / 2;
    for (let i = 0; i < count; i++) {
      const t = rng();
      const side = Math.floor(rng() * 4);
      const px = side === 0 || side === 2 ? b.x + t * b.w : side === 1 ? b.x + b.w : b.x;
      const pz = side === 1 || side === 3 ? b.z + t * b.d : side === 2 ? b.z + b.d : b.z;
      const len = Math.max(0.01, Math.hypot(px - cx, pz - cz));
      const sp = 3 + rng() * 5;
      const life = 0.5 + rng() * 0.4;
      sparks.push({
        x: px, y: b.y + b.h, z: pz,
        vx: ((px - cx) / len) * sp, vy: 4 + rng() * 6, vz: ((pz - cz) / len) * sp,
        life, maxLife: life, size: 0.1 + rng() * 0.15, color,
      });
    }
  };

  const nextTarget = (): void => {
    const drop = drops[dropIndex];
    if (drop === undefined) {
      target = Number.NaN;
      return;
    }
    const size = axisX ? top().w : top().d;
    if (typeof drop === "number") {
      target = drop;
      bouncesLeft = 0;
    } else if ("miss" in drop) {
      target = Math.sign(drop.miss || 1) * (size + Math.abs(drop.miss));
      bouncesLeft = drop.bounces ?? 0;
    } else {
      target = drop.offset;
      bouncesLeft = drop.bounces ?? 0;
    }
    target = Math.max(-RANGE + 0.01, Math.min(RANGE - 0.01, target));
  };

  const spawnNext = (): void => {
    const t = top();
    axisX = !axisX;
    direction = 1;
    const b: Block = { x: t.x, z: t.z, w: t.w, d: t.d, y: t.y + t.h, h: SLAB_H, color: blockColor(theme, score + 1) };
    if (axisX) b.x = t.x - RANGE;
    else b.z = t.z - RANGE;
    moving = b;
    nextTarget();
  };

  const place = (b: Block): void => {
    tower.push(b);
    moving = null;
    score++;
    speed = Math.min(MAX_SPEED, START_SPEED + score * SPEED_STEP);
    camera.targetY = b.y + b.h + SLAB_H;
    scorePop = 1;
    if (streakRemaining > 0) {
      const t = top();
      bonus = { x: t.x, z: t.z, w: t.w, d: t.d, y: t.y + t.h + BONUS_DROP, h: SLAB_H, color: blockColor(theme, score + 1) };
    } else if (!stopped) {
      spawnNext();
    }
  };

  const showCombo = (label: string): void => {
    comboLabel = label;
    comboTime = COMBO_TIME;
  };

  const grow = (b: Block): boolean => {
    let grew = false;
    if (b.w < BASE) {
      const g = Math.min(GROW_AMOUNT, BASE - b.w);
      b.x -= g / 2;
      b.w += g;
      grew = true;
    }
    if (b.d < BASE) {
      const g = Math.min(GROW_AMOUNT, BASE - b.d);
      b.z -= g / 2;
      b.d += g;
      grew = true;
    }
    return grew;
  };

  const pullBack = (): void => {
    const towerTop = top().y + top().h;
    const towerPx = (towerTop + BASE * 1.2) * baseScale;
    camera.targetZoom = Math.max(MIN_ZOOM, Math.min(1, (height * 0.5) / towerPx));
    camera.targetY = towerTop * 0.5;
  };

  const miss = (b: Block, delta: number): void => {
    moving = null;
    combo = 0;
    const push = Math.sign(delta) * DEBRIS_PUSH;
    addDebris(b, axisX ? push : 0, axisX ? 0 : push);
    emit("miss");
    shake(width * 0.008, 0.2);
    pullBack();
    isOver = true;
  };

  const addDebris = (block: Block, vx: number, vz: number): void => {
    const spin = (vx > 0 || vz < 0 ? 1 : -1) * (50 + rng() * 80);
    debris.push({ block, vx, vz, vy: 0, rotation: 0, spin, age: 0 });
  };

  const drop = (): void => {
    const b = moving;
    if (!b) return;
    dropIndex++;
    const t = top();
    const delta = axisX ? b.x - t.x : b.z - t.z;
    const size = axisX ? t.w : t.d;

    if (Math.abs(delta) <= PERFECT_TOLERANCE) {
      if (axisX) b.x = t.x;
      else b.z = t.z;
      combo++;
      const grew = combo >= GROW_COMBO && grow(b);
      const streaking = combo % STREAK_LENGTH === 0;
      if (streaking) {
        streakRemaining = STREAK_STACKS;
        streakElapsed = 0;
        streakDelay = STREAK_LEAD_IN;
      }
      emit("perfect");
      if (grew) emit("grow");
      const glow = lighten(b.color, 0.4);
      ripple(b, 1 + Math.min(combo, 3), glow);
      burst(b, 14 + Math.min(combo, 10) * 2, glow);
      const label = combo === 1 ? "PERFECT" : `PERFECT x${combo}`;
      showCombo(streaking ? `${label}  STREAK!` : grew ? `${label}  GROW!` : label);
      place(b);
      return;
    }

    const overlap = size - Math.abs(delta);
    if (overlap <= 0) {
      miss(b, delta);
      return;
    }

    combo = 0;
    const cut = Math.abs(delta);
    const push = Math.sign(delta) * DEBRIS_PUSH;
    if (axisX) {
      const newX = Math.max(b.x, t.x);
      const piece: Block = delta > 0 ? { ...b, x: newX + overlap, w: cut } : { ...b, w: cut };
      b.x = newX;
      b.w = overlap;
      addDebris(piece, push, 0);
    } else {
      const newZ = Math.max(b.z, t.z);
      const piece: Block = delta > 0 ? { ...b, z: newZ + overlap, d: cut } : { ...b, d: cut };
      b.z = newZ;
      b.d = overlap;
      addDebris(piece, 0, push);
    }
    emit("cut", cut / size);
    shake(width * 0.003, 0.1);
    place(b);
  };

  const moveSlab = (dt: number): void => {
    const b = moving;
    if (!b) return;
    const t = top();
    const prev = axisX ? b.x - t.x : b.z - t.z;
    let offset = prev + direction * speed * dt;
    if (offset > RANGE) {
      offset = RANGE;
      direction = -1;
      bouncesLeft--;
    } else if (offset < -RANGE) {
      offset = -RANGE;
      direction = 1;
      bouncesLeft--;
    }
    const crossed = (prev - target) * (offset - target) <= 0;
    if (!Number.isNaN(target) && bouncesLeft <= 0 && crossed) offset = target;
    if (axisX) b.x = t.x + offset;
    else b.z = t.z + offset;
    if (!Number.isNaN(target) && bouncesLeft <= 0 && crossed) drop();
  };

  const updateStreak = (dt: number): void => {
    const b = bonus;
    if (!b) return;
    const rest = top().y + top().h;
    if (streakDelay > 0) {
      streakDelay -= dt;
      b.y = rest + BONUS_DROP;
      return;
    }
    streakElapsed += dt;
    const piece = STREAK_STACKS - streakRemaining;
    const fallTime = streakFallTime(piece);
    if (streakElapsed < fallTime) {
      const f = streakElapsed / fallTime;
      b.y = rest + (1 - f * f) * BONUS_DROP;
      return;
    }
    streakElapsed = 0;
    streakRemaining--;
    b.y = rest;
    bonus = null;
    const last = streakRemaining === 0;
    emit("streakPiece", piece);
    if (last) emit("streakDone");
    const glow = lighten(b.color, 0.4);
    ripple(b, last ? 4 : 2, glow);
    burst(b, last ? 36 : 10, glow);
    if (last) {
      showCombo(`+${STREAK_STACKS} STACKS  +5 GEMS`);
      shake(width * 0.005, 0.15);
    }
    place(b);
  };

  const step = (dt: number): void => {
    time += dt;
    if (!isOver) {
      if (config.zoomOutAt !== undefined && !stopped && time >= config.zoomOutAt && streakRemaining === 0) {
        stopped = true;
        moving = null;
        pullBack();
      }
      if (streakRemaining > 0) updateStreak(dt);
      else moveSlab(dt);
    }

    for (let i = debris.length - 1; i >= 0; i--) {
      const p = debris[i];
      p.age += dt;
      p.vy -= DEBRIS_GRAVITY * dt;
      p.block.x += p.vx * dt;
      p.block.z += p.vz * dt;
      p.block.y += p.vy * dt;
      p.rotation += p.spin * dt;
      if (p.age >= DEBRIS_LIFETIME) debris.splice(i, 1);
    }
    for (let i = sparks.length - 1; i >= 0; i--) {
      const p = sparks[i];
      p.life -= dt;
      if (p.life <= 0) {
        sparks.splice(i, 1);
        continue;
      }
      p.vy -= SPARK_GRAVITY * dt;
      p.x += p.vx * dt;
      p.y += p.vy * dt;
      p.z += p.vz * dt;
    }
    for (let i = ripples.length - 1; i >= 0; i--) {
      ripples[i].age += dt;
      if (ripples[i].age > RIPPLE_TIME) ripples.splice(i, 1);
    }

    camera.y += (camera.targetY - camera.y) * (1 - Math.exp(-7 * dt));
    camera.zoom += (camera.targetZoom - camera.zoom) * (1 - Math.exp(-2.5 * dt));
    if (camera.shakeTime > 0) {
      camera.shakeTime -= dt;
      const m = camera.shakeMag * Math.max(0, camera.shakeTime / camera.shakeDur);
      camera.shakeX = (rng() * 2 - 1) * m;
      camera.shakeY = (rng() * 2 - 1) * m;
    } else {
      camera.shakeX = 0;
      camera.shakeY = 0;
    }
    drift += (driftTarget() - drift) * (1 - Math.exp(-3 * dt));
    scorePop = Math.max(0, scorePop - dt * 4);
    comboTime = Math.max(0, comboTime - dt);
  };

  spawnNext();

  for (frame = 0; frame < config.frames; frame++) {
    frames.push({
      towerCount: tower.length,
      moving: moving ? clone(moving) : null,
      bonus: bonus ? clone(bonus) : null,
      debris: debris.map((p) => ({
        block: clone(p.block),
        rotation: p.rotation,
        alpha: Math.round(255 * Math.max(0, 1 - p.age / DEBRIS_LIFETIME)),
      })),
      sparks: sparks.map((p) => ({
        x: p.x, y: p.y, z: p.z, size: p.size, color: p.color,
        alpha: Math.round((255 * p.life) / p.maxLife),
      })),
      ripples: ripples.map((r) => ({ ...r })),
      camY: camera.y,
      zoom: camera.zoom,
      shakeX: camera.shakeX,
      shakeY: camera.shakeY,
      drift,
      score,
      scorePop,
      comboLabel,
      comboTime,
      isOver,
      time,
    });
    const scale = config.timeScale?.(time) ?? 1;
    const dt = scale / fps / SUBSTEPS;
    for (let s = 0; s < SUBSTEPS; s++) step(dt);
  }

  return { tower, frames, events, baseScale };
}

/** Stacks [levels] slabs that narrow towards [size], each sitting inside the one below like real cuts. */
function prebuild(tower: Block[], theme: Theme, levels: number, size: [number, number], rng: Rng): void {
  for (let i = 1; i <= levels; i++) {
    const below = tower[tower.length - 1];
    const f = i / levels;
    // Cuts come in bursts: some levels are perfect drops that keep the size.
    const perfect = rng() < 0.45;
    const w = perfect ? below.w : Math.max(size[0], Math.min(below.w, BASE + (size[0] - BASE) * f));
    const d = perfect ? below.d : Math.max(size[1], Math.min(below.d, BASE + (size[1] - BASE) * f));
    tower.push({
      x: below.x + rng() * (below.w - w),
      z: below.z + rng() * (below.d - d),
      w, d,
      y: below.y + below.h,
      h: SLAB_H,
      color: blockColor(theme, i),
    });
  }
  // Land exactly on the requested size so scripted offsets are predictable.
  const last = tower[tower.length - 1];
  if (levels > 0) {
    last.w = size[0];
    last.d = size[1];
  }
}
