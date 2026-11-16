// Copies the game's own font, icon and synthesized sounds into public/, and writes pitched copies
// of the notes the game plays at runtime pitch (Remotion keeps pitch when changing playback rate).
// Run: pnpm assets
import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const APP = join(ROOT, "..");
const RAW = join(APP, "app/src/main/res/raw");
const PUBLIC = join(ROOT, "public");
const SFX = join(PUBLIC, "sfx");

mkdirSync(SFX, { recursive: true });
copyFileSync(join(APP, "app/src/main/assets/fonts/Fredoka.ttf"), join(PUBLIC, "Fredoka.ttf"));
copyFileSync(join(APP, "docs/store/play-icon-512.png"), join(PUBLIC, "icon.png"));
for (const name of ["drop", "fail", "music", "perfect", "release", "slice", "unlock", "warn", "button"]) {
  copyFileSync(join(RAW, `${name}.wav`), join(SFX, `${name}.wav`));
}

/** Reads a 16-bit PCM mono WAV with a plain 44-byte header, as tools/audio/generate.mjs writes them. */
function readWav(path) {
  const buf = readFileSync(path);
  if (buf.toString("ascii", 0, 4) !== "RIFF" || buf.readUInt16LE(34) !== 16 || buf.readUInt16LE(22) !== 1) {
    throw new Error(`${path}: expected a 16-bit mono PCM WAV`);
  }
  const rate = buf.readUInt32LE(24);
  const samples = new Int16Array((buf.length - 44) / 2);
  for (let i = 0; i < samples.length; i++) samples[i] = buf.readInt16LE(44 + i * 2);
  return { rate, samples };
}

function writeWav(path, rate, samples) {
  const buf = Buffer.alloc(44 + samples.length * 2);
  buf.write("RIFF", 0, "ascii");
  buf.writeUInt32LE(36 + samples.length * 2, 4);
  buf.write("WAVEfmt ", 8, "ascii");
  buf.writeUInt32LE(16, 16);
  buf.writeUInt16LE(1, 20);
  buf.writeUInt16LE(1, 22);
  buf.writeUInt32LE(rate, 24);
  buf.writeUInt32LE(rate * 2, 28);
  buf.writeUInt16LE(2, 32);
  buf.writeUInt16LE(16, 34);
  buf.write("data", 36, "ascii");
  buf.writeUInt32LE(samples.length * 2, 40);
  for (let i = 0; i < samples.length; i++) buf.writeInt16LE(samples[i], 44 + i * 2);
  writeFileSync(path, buf);
}

/** Resamples so the sound plays [ratio] times faster and higher, like Android SoundPool's rate. */
function pitched(name, ratio, outName) {
  const { rate, samples } = readWav(join(RAW, `${name}.wav`));
  const out = new Int16Array(Math.floor(samples.length / ratio));
  for (let i = 0; i < out.length; i++) {
    const p = i * ratio;
    const i0 = Math.floor(p);
    const i1 = Math.min(i0 + 1, samples.length - 1);
    out[i] = Math.round(samples[i0] + (samples[i1] - samples[i0]) * (p - i0));
  }
  writeWav(join(SFX, `${outName}.wav`), rate, out);
}

// Semitones of the major scale perfects climb (game/GameMode.kt PERFECT_SCALE) plus the streak's 7.
for (const semis of [0, 2, 4, 5, 7, 9, 11, 12]) pitched("perfect", 2 ** (semis / 12), `perfect_${semis}`);
pitched("unlock", 1.2, "unlock_hi");
pitched("drop", 1.3, "drop_hi");
pitched("slice", 1.15, "slice_hi");
// Tower mode rates (game/tower/TowerMode.kt).
pitched("drop", 0.8, "drop_lo");
pitched("drop", 0.65, "drop_thud");
pitched("fail", 1.1, "fail_hi");
pitched("fail", 0.8, "fail_lo");
pitched("slice", 0.6, "slice_lo");

console.log(`Assets ready in ${PUBLIC}`);
