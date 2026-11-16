/** Colors are 0xRRGGBB numbers, mirroring the game's packed Android colors (alpha kept separate). */
export type Rgb = number;

const red = (c: Rgb): number => (c >> 16) & 0xff;
const green = (c: Rgb): number => (c >> 8) & 0xff;
const blue = (c: Rgb): number => c & 0xff;
const pack = (r: number, g: number, b: number): Rgb =>
  (clampByte(r) << 16) | (clampByte(g) << 8) | clampByte(b);
const clampByte = (v: number): number => Math.max(0, Math.min(255, Math.trunc(v)));

/** Same contract as Android's Color.HSVToColor: hue in degrees, saturation and value 0..1. */
export function hsv(h: number, s: number, v: number): Rgb {
  const hue = ((h % 360) + 360) % 360;
  const sat = Math.max(0, Math.min(1, s));
  const val = Math.max(0, Math.min(1, v));
  const f = (n: number): number => {
    const k = (n + hue / 60) % 6;
    return val - val * sat * Math.max(0, Math.min(k, 4 - k, 1));
  };
  return pack(Math.round(f(5) * 255), Math.round(f(3) * 255), Math.round(f(1) * 255));
}

export function shade(c: Rgb, f: number): Rgb {
  return pack(red(c) * f, green(c) * f, blue(c) * f);
}

export function lighten(c: Rgb, amount: number): Rgb {
  const mix = (v: number): number => v + (255 - v) * amount;
  return pack(mix(red(c)), mix(green(c)), mix(blue(c)));
}

/** CSS color string; [alpha] is 0..255 like the game's Paint alpha. */
export function css(c: Rgb, alpha = 255): string {
  const a = Math.max(0, Math.min(255, alpha)) / 255;
  return `rgba(${red(c)},${green(c)},${blue(c)},${a.toFixed(3)})`;
}

/** Opaque blend from [a] (t = 0) to [b] (t = 1), like ColorMath.mix. */
export function mix(a: Rgb, b: Rgb, t: number): Rgb {
  const lerp = (x: number, y: number): number => x + (y - x) * t + 0.5;
  return pack(lerp(red(a), red(b)), lerp(green(a), green(b)), lerp(blue(a), blue(b)));
}
