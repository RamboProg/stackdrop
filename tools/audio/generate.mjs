// Synthesizes Stack Drop's sound effects and music loop as 16-bit mono WAV files in
// app/src/main/res/raw. Everything is generated from oscillators and filtered noise, so the
// audio is original and free of licensing questions. Run: node tools/audio/generate.mjs
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const RATE = 22050;
const OUT = join(dirname(fileURLToPath(import.meta.url)), "../../app/src/main/res/raw");
const TAU = Math.PI * 2;

// Deterministic noise so every run produces identical files.
let seed = 12345;
const noise = () => {
  seed = (seed * 1103515245 + 12345) & 0x7fffffff;
  return (seed / 0x3fffffff) - 1;
};

const midi = (n) => 440 * 2 ** ((n - 69) / 12);
const buffer = (seconds) => new Float32Array(Math.ceil(seconds * RATE));

/** A soft bell: a sine with two quiet harmonics and an exponential decay. */
function bell(out, start, freq, length, amp, decay = 5) {
  const s0 = Math.floor(start * RATE);
  const n = Math.floor(length * RATE);
  for (let i = 0; i < n && s0 + i < out.length; i++) {
    const t = i / RATE;
    const attack = Math.min(1, t / 0.004);
    const env = attack * Math.exp(-decay * t);
    const v = Math.sin(TAU * freq * t) + 0.3 * Math.sin(TAU * freq * 2 * t) * Math.exp(-3 * t) +
      0.12 * Math.sin(TAU * freq * 3.01 * t) * Math.exp(-6 * t);
    out[s0 + i] += amp * env * v;
  }
}

/** A sine whose pitch glides from [f0] to [f1]. */
function glide(out, start, f0, f1, length, amp, decay) {
  const s0 = Math.floor(start * RATE);
  const n = Math.floor(length * RATE);
  let phase = 0;
  for (let i = 0; i < n && s0 + i < out.length; i++) {
    const t = i / RATE;
    const f = f0 + (f1 - f0) * (i / n);
    phase += TAU * f / RATE;
    const env = Math.min(1, t / 0.003) * Math.exp(-decay * t);
    out[s0 + i] += amp * env * Math.sin(phase);
  }
}

/** Filtered noise; [cutoff] can move over time for a swish. */
function hiss(out, start, length, amp, cutoffAt, decay) {
  const s0 = Math.floor(start * RATE);
  const n = Math.floor(length * RATE);
  let lp = 0;
  for (let i = 0; i < n && s0 + i < out.length; i++) {
    const t = i / RATE;
    const a = Math.min(1, cutoffAt(i / n) / RATE * TAU);
    lp += a * (noise() - lp);
    const env = Math.min(1, t / 0.01) * Math.exp(-decay * t);
    out[s0 + i] += amp * env * lp;
  }
}

function normalize(buf, peak) {
  let max = 0;
  for (const v of buf) max = Math.max(max, Math.abs(v));
  if (max > 0) for (let i = 0; i < buf.length; i++) buf[i] *= peak / max;
  return buf;
}

/** Short fades at both ends so effects never click. */
function fadeEdges(buf, ms = 4) {
  const n = Math.floor(ms / 1000 * RATE);
  for (let i = 0; i < n && i < buf.length; i++) {
    buf[i] *= i / n;
    buf[buf.length - 1 - i] *= i / n;
  }
  return buf;
}

function writeWav(name, buf) {
  const data = Buffer.alloc(buf.length * 2);
  for (let i = 0; i < buf.length; i++) {
    data.writeInt16LE(Math.round(Math.max(-1, Math.min(1, buf[i])) * 32767), i * 2);
  }
  const h = Buffer.alloc(44);
  h.write("RIFF", 0);
  h.writeUInt32LE(36 + data.length, 4);
  h.write("WAVE", 8);
  h.write("fmt ", 12);
  h.writeUInt32LE(16, 16);
  h.writeUInt16LE(1, 20);
  h.writeUInt16LE(1, 22);
  h.writeUInt32LE(RATE, 24);
  h.writeUInt32LE(RATE * 2, 28);
  h.writeUInt16LE(2, 32);
  h.writeUInt16LE(16, 34);
  h.write("data", 36);
  h.writeUInt32LE(data.length, 40);
  writeFileSync(join(OUT, `${name}.wav`), Buffer.concat([h, data]));
  console.log(`${name}.wav  ${(buf.length / RATE).toFixed(2)} s`);
}

// --- Effects. Peaks stay well below full scale so nothing is harsh. ---

const effects = {
  // Menu buttons: a tiny soft tick.
  button() {
    const b = buffer(0.08);
    glide(b, 0, 1400, 900, 0.07, 1, 60);
    return normalize(b, 0.45);
  },
  // A slab landing: a low, warm thump with a breath of air.
  drop() {
    const b = buffer(0.3);
    glide(b, 0, 150, 70, 0.28, 1, 14);
    hiss(b, 0, 0.12, 0.35, () => 900, 40);
    return normalize(b, 0.7);
  },
  // Perfect drop: a clear chime. Combos raise its pitch through SoundPool's rate.
  perfect() {
    const b = buffer(0.9);
    bell(b, 0, midi(84), 0.9, 1, 5);
    bell(b, 0.004, midi(91), 0.6, 0.25, 7);
    return normalize(b, 0.6);
  },
  // Overhang cut off: a soft airy swish.
  slice() {
    const b = buffer(0.28);
    hiss(b, 0, 0.27, 1, (p) => 600 + 3400 * p, 9);
    return normalize(b, 0.5);
  },
  // Missed or slipped: a gentle falling two-note sigh, never a buzzer.
  fail() {
    const b = buffer(0.75);
    bell(b, 0, midi(76), 0.4, 0.8, 7);
    bell(b, 0.16, midi(72), 0.55, 0.9, 5);
    return normalize(b, 0.5);
  },
  // Unlocks, gifts and new bests: a rising arpeggio.
  unlock() {
    const b = buffer(1.1);
    [72, 76, 79, 84].forEach((n, i) => bell(b, i * 0.085, midi(n), 0.8, 0.8 + i * 0.1, 4.5));
    return normalize(b, 0.6);
  },
  // Crane release: a light clack.
  release() {
    const b = buffer(0.14);
    glide(b, 0, 700, 450, 0.12, 0.8, 35);
    hiss(b, 0, 0.05, 0.4, () => 2500, 60);
    return normalize(b, 0.45);
  },
  // Tower about to tip: two quiet ticks.
  warn() {
    const b = buffer(0.32);
    bell(b, 0, midi(81), 0.12, 1, 30);
    bell(b, 0.14, midi(81), 0.12, 1, 30);
    return normalize(b, 0.4);
  },
};

// --- Music: a calm, seamlessly looping piece (pads, soft bass, plucked arpeggio, echo). ---

function music() {
  const bpm = 76;
  const beat = 60 / bpm;
  const barsPerChord = 2;
  // Cmaj7 - Am7 - Fmaj7 - G(add9), voiced warm and low.
  const chords = [
    [48, 55, 59, 64], [45, 52, 55, 60], [41, 48, 52, 57], [43, 50, 55, 57],
    [48, 55, 59, 64], [45, 52, 55, 60], [41, 48, 52, 57], [43, 50, 53, 59],
  ];
  const chordLen = barsPerChord * 4 * beat;
  const total = chords.length * chordLen;
  const n = Math.round(total * RATE);
  const out = new Float32Array(n);
  // Writes wrap around the end, so tails of the last bar flow into the first: a seamless loop.
  const add = (i, v) => { out[((i % n) + n) % n] += v; };

  chords.forEach((chord, c) => {
    const t0 = c * chordLen;
    // Pads: three slightly detuned soft triangles per note, slow swell and release.
    for (const note of chord) {
      const f = midi(note + 12);
      const len = chordLen + 1.5;
      for (let i = 0; i < len * RATE; i++) {
        const t = i / RATE;
        const env = Math.min(1, t / 1.6) * Math.min(1, Math.max(0, (len - t) / 1.5));
        let v = 0;
        for (const d of [-0.0018, 0, 0.0021]) {
          const x = (f * (1 + d) * t) % 1;
          v += 1 - 4 * Math.abs(x - 0.5);
        }
        add(Math.floor((t0 + t) * RATE), 0.045 * env * v);
      }
    }
    // Bass: the root on the first beat of each bar.
    for (let bar = 0; bar < barsPerChord; bar++) {
      const start = t0 + bar * 4 * beat;
      const f = midi(chord[0] - 12);
      for (let i = 0; i < 2.6 * RATE; i++) {
        const t = i / RATE;
        add(Math.floor(start * RATE) + i, 0.16 * Math.min(1, t / 0.02) * Math.exp(-1.4 * t) * Math.sin(TAU * f * t));
      }
    }
    // Arpeggio: eighth notes walking the chord an octave up, with a few rests for air.
    const pattern = [0, 1, 2, 3, 2, 1, 3, -1];
    for (let k = 0; k < barsPerChord * 8; k++) {
      const step = pattern[k % pattern.length];
      if (step < 0) continue;
      const start = t0 + k * beat / 2;
      const f = midi(chord[step] + 24);
      for (let i = 0; i < 0.9 * RATE; i++) {
        const t = i / RATE;
        const env = Math.min(1, t / 0.005) * Math.exp(-4.2 * t);
        const v = Math.sin(TAU * f * t) + 0.2 * Math.sin(TAU * f * 2 * t) * Math.exp(-6 * t);
        add(Math.floor(start * RATE) + i, 0.07 * env * v);
      }
    }
  });

  // A soft echo (dotted eighth), also wrapping, then a gentle low-pass to round everything off.
  const delay = Math.round(beat * 0.75 * RATE);
  const wet = new Float32Array(n);
  for (let pass = 0, gain = 0.32; pass < 3; pass++, gain *= 0.45) {
    for (let i = 0; i < n; i++) wet[(i + delay * (pass + 1)) % n] += out[i] * gain;
  }
  for (let i = 0; i < n; i++) out[i] += wet[i];
  // The low-pass also runs circularly: it warms up on the loop's tail so the first sample
  // continues exactly from the last one.
  for (let pass = 0; pass < 2; pass++) {
    const src = out.slice();
    let lp = 0;
    for (let i = n - RATE; i < n; i++) lp += 0.35 * (src[i] - lp);
    for (let i = 0; i < n; i++) {
      lp += 0.35 * (src[i] - lp);
      out[i] = lp;
    }
  }
  return normalize(out, 0.75);
}

mkdirSync(OUT, { recursive: true });
for (const [name, make] of Object.entries(effects)) writeWav(name, fadeEdges(make()));
writeWav("music", music());
