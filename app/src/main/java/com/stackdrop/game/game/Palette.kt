package com.stackdrop.game.game

import android.graphics.Color
import kotlin.math.sin

data class Theme(
    val id: String,
    val name: String,
    val cost: Int,
    val baseHue: Float,
    /** Degrees per level when [hueRange] is 0, otherwise radians per level of the oscillation. */
    val hueStep: Float,
    val hueRange: Float,
    val sat: Float,
    val value: Float,
    val valueWave: Float,
    val bgHue: Float,
    val bgSat: Float,
    /** Grid, crane and UI glow color. */
    val accentHue: Float,
    /** Sun top and secondary highlights. */
    val accent2Hue: Float,
    val accentSat: Float,
)

object Palette {

    const val DEFAULT_ID = "classic"

    // Ids are kept from the first release so existing unlocks still apply.
    val themes = listOf(
        Theme(DEFAULT_ID, "Synthwave", 0, 300f, 9f, 0f, 0.75f, 1f, 0f, 265f, 0.75f, 315f, 40f, 0.85f),
        Theme("pastel", "Vapor", 50, 175f, 8f, 0f, 0.5f, 1f, 0f, 280f, 0.55f, 180f, 300f, 0.6f),
        Theme("neon", "Toxic", 120, 105f, 0.13f, 30f, 0.9f, 1f, 0f, 160f, 0.8f, 95f, 55f, 0.9f),
        Theme("sunset", "Inferno", 200, 18f, 0.15f, 22f, 0.9f, 1f, 0f, 350f, 0.85f, 15f, 50f, 0.95f),
        Theme("ocean", "Ice", 300, 195f, 0.1f, 25f, 0.65f, 1f, 0f, 225f, 0.8f, 190f, 235f, 0.7f),
        Theme("mono", "Chrome", 500, 0f, 0f, 0f, 0f, 0.95f, 0.4f, 230f, 0.15f, 210f, 0f, 0.05f),
    )

    private val hsvTmp = FloatArray(3)

    fun byId(id: String) = themes.firstOrNull { it.id == id } ?: themes[0]

    fun hueAt(t: Theme, level: Int): Float =
        if (t.hueRange > 0f) t.baseHue + sin(level * t.hueStep) * t.hueRange
        else t.baseHue + level * t.hueStep

    fun blockColor(t: Theme, level: Int): Int {
        val v = t.value - t.valueWave * (0.5f + 0.5f * sin(level * 0.45f))
        return hsv(hueAt(t, level), t.sat, v)
    }

    fun bgTop(t: Theme, drift: Float) = hsv(t.bgHue + drift, t.bgSat, 0.17f)

    fun bgBottom(t: Theme, drift: Float) = hsv(t.bgHue + drift + 30f, t.bgSat, 0.05f)

    fun accent(t: Theme) = hsv(t.accentHue, t.accentSat, 1f)

    fun accent2(t: Theme) = hsv(t.accent2Hue, t.accentSat, 1f)

    fun hsv(h: Float, s: Float, v: Float): Int {
        hsvTmp[0] = ((h % 360f) + 360f) % 360f
        hsvTmp[1] = s.coerceIn(0f, 1f)
        hsvTmp[2] = v.coerceIn(0f, 1f)
        return Color.HSVToColor(hsvTmp)
    }

    fun shade(color: Int, f: Float): Int = Color.rgb(
        (Color.red(color) * f).toInt().coerceIn(0, 255),
        (Color.green(color) * f).toInt().coerceIn(0, 255),
        (Color.blue(color) * f).toInt().coerceIn(0, 255),
    )

    fun lighten(color: Int, amount: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.rgb(
            (r + (255 - r) * amount).toInt(),
            (g + (255 - g) * amount).toInt(),
            (b + (255 - b) * amount).toInt(),
        )
    }

    fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}
