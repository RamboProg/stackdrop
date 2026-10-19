package com.stackdrop.game.game

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Color math with no Android dependency, so calm themes and their comfort rules can be unit tested. */
object ColorMath {

    fun red(c: Int) = (c shr 16) and 0xFF

    fun green(c: Int) = (c shr 8) and 0xFF

    fun blue(c: Int) = c and 0xFF

    fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    fun withAlpha(c: Int, alpha: Int): Int = (c and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    /** Opaque blend from [a] (t = 0) to [b] (t = 1). */
    fun mix(a: Int, b: Int, t: Float): Int =
        rgb(lerp(red(a), red(b), t), lerp(green(a), green(b), t), lerp(blue(a), blue(b), t))

    /** Same rounding as Android's Color.HSVToColor, so calm and neon colors agree. */
    fun hsv(h: Float, s: Float, v: Float): Int {
        val hue = ((h % 360f) + 360f) % 360f
        val sat = s.coerceIn(0f, 1f)
        val value = v.coerceIn(0f, 1f)
        val vb = round(value * 255f)
        if (sat <= 0f) return rgb(vb, vb, vb)
        val hx = hue / 60f
        val sector = floor(hx)
        val f = hx - sector
        val p = round((1f - sat) * value * 255f)
        val q = round((1f - sat * f) * value * 255f)
        val t = round((1f - sat * (1f - f)) * value * 255f)
        return when (sector.toInt() % 6) {
            0 -> rgb(vb, t, p)
            1 -> rgb(q, vb, p)
            2 -> rgb(p, vb, t)
            3 -> rgb(p, q, vb)
            4 -> rgb(t, p, vb)
            else -> rgb(vb, p, q)
        }
    }

    /** HSL lightness in 0..1. */
    fun lightness(c: Int): Float {
        val hi = max(red(c), max(green(c), blue(c)))
        val lo = min(red(c), min(green(c), blue(c)))
        return (hi + lo) / 510f
    }

    /** WCAG relative luminance. */
    fun luminance(c: Int): Double =
        0.2126 * channel(red(c)) + 0.7152 * channel(green(c)) + 0.0722 * channel(blue(c))

    /** WCAG contrast ratio, 1..21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun channel(v: Int): Double {
        val s = v / 255.0
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t + 0.5f).toInt().coerceIn(0, 255)

    private fun round(x: Float) = (x + 0.5f).toInt().coerceIn(0, 255)
}
