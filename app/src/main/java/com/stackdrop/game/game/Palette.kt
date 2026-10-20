package com.stackdrop.game.game

import android.graphics.Color
import com.stackdrop.game.ui.Neon
import kotlin.math.sin

enum class Family { CALM, NEON }

enum class Scenery { SYNTH_GRID, HILLS, FOREST_MIST, LAGOON, AURORA_PEAKS, PETALS_GARDEN, DUNES, SLATE }

enum class Ambient { STARS, NONE, FOG, BUBBLES, AURORA, PETALS, MOTES }

/** A free way to unlock a theme: reach [score] in the mode [modeId]. */
data class Milestone(val modeId: String, val score: Int)

data class Theme(
    val id: String,
    val name: String,
    val cost: Int,
    val family: Family,
    /** One word shown on the Themes screen. */
    val mood: String,
    val baseHue: Float,
    /** Degrees per level when [hueRange] is 0, otherwise radians per level of the oscillation. */
    val hueStep: Float,
    val hueRange: Float,
    val sat: Float,
    val value: Float,
    /** Neon: depth of the slow brightness wave. Calm: how much darker every other layer is. */
    val valueWave: Float,
    val skyTop: Int,
    val skyMid: Int,
    val skyBottom: Int,
    val ground: Int,
    val scenery: Scenery,
    val ambient: Ambient,
    /** Grid, crane and UI glow color. */
    val accent: Int,
    /** Sun top and secondary highlights. */
    val accent2: Int,
    val text: Int,
    val textDim: Int,
    val panel: Int,
    val danger: Int,
    /** 1 is full neon; calm themes use 0.15–0.25. */
    val glow: Float,
    val scanlines: Boolean,
    val vignette: Float,
    val shake: Float,
    val particles: Float,
    /** Light sky: dark text and softer shadows. */
    val light: Boolean,
    val milestone: Milestone? = null,
    /** Only available to players who bought premium; it cannot be bought with gems. */
    val premiumOnly: Boolean = false,
    /** Neon only: the synthwave sky, skyline and street derive from these and drift with the tower hue. */
    val bgHue: Float = 0f,
    val bgSat: Float = 0f,
) {
    val calm get() = family == Family.CALM
}

object Palette {

    const val DEFAULT_ID = "classic"
    const val NEW_PLAYER_ID = "dusk"

    // Must be initialized before the neon themes, which are built with hsv().
    private val hsvTmp = FloatArray(3)

    // Ids are kept from the first release so existing unlocks still apply.
    val neon = listOf(
        neon(DEFAULT_ID, "Synthwave", 0, 300f, 9f, 0f, 0.75f, 1f, 0f, 265f, 0.75f, 315f, 40f, 0.85f, "electric"),
        neon("pastel", "Vapor", 50, 175f, 8f, 0f, 0.5f, 1f, 0f, 280f, 0.55f, 180f, 300f, 0.6f, "dreamy"),
        neon("neon", "Toxic", 120, 105f, 0.13f, 30f, 0.9f, 1f, 0f, 160f, 0.8f, 95f, 55f, 0.9f, "acid"),
        neon("sunset", "Inferno", 200, 18f, 0.15f, 22f, 0.9f, 1f, 0f, 350f, 0.85f, 15f, 50f, 0.95f, "fiery"),
        neon("ocean", "Ice", 300, 195f, 0.1f, 25f, 0.65f, 1f, 0f, 225f, 0.8f, 190f, 235f, 0.7f, "icy"),
        neon("mono", "Chrome", 500, 0f, 0f, 0f, 0f, 0.95f, 0.4f, 230f, 0.15f, 210f, 0f, 0.05f, "sleek"),
    )

    val calm = CalmThemes.all

    val themes = calm + neon

    fun find(id: String): Theme? = themes.firstOrNull { it.id == id }

    fun byId(id: String): Theme = find(id) ?: neon[0]

    fun of(family: Family) = if (family == Family.CALM) calm else neon

    fun hueAt(t: Theme, level: Int): Float =
        if (t.hueRange > 0f) t.baseHue + sin(level * t.hueStep) * t.hueRange
        else t.baseHue + level * t.hueStep

    fun blockColor(t: Theme, level: Int): Int {
        if (t.calm) return CalmThemes.blockColor(t, level)
        val v = t.value - t.valueWave * (0.5f + 0.5f * sin(level * 0.45f))
        return hsv(hueAt(t, level), t.sat, v)
    }

    fun bgTop(t: Theme, drift: Float) = hsv(t.bgHue + drift, t.bgSat, 0.17f)

    fun bgBottom(t: Theme, drift: Float) = hsv(t.bgHue + drift + 30f, t.bgSat, 0.05f)

    fun accent(t: Theme) = t.accent

    fun accent2(t: Theme) = t.accent2

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

    private fun neon(
        id: String, name: String, cost: Int,
        baseHue: Float, hueStep: Float, hueRange: Float, sat: Float, value: Float, valueWave: Float,
        bgHue: Float, bgSat: Float, accentHue: Float, accent2Hue: Float, accentSat: Float, mood: String,
    ) = Theme(
        id = id, name = name, cost = cost, family = Family.NEON, mood = mood,
        baseHue = baseHue, hueStep = hueStep, hueRange = hueRange, sat = sat, value = value, valueWave = valueWave,
        skyTop = hsv(bgHue, bgSat, 0.17f), skyMid = hsv(bgHue + 15f, bgSat, 0.11f),
        skyBottom = hsv(bgHue + 30f, bgSat, 0.05f), ground = hsv(bgHue, bgSat, 0.035f),
        scenery = Scenery.SYNTH_GRID, ambient = Ambient.STARS,
        accent = hsv(accentHue, accentSat, 1f), accent2 = hsv(accent2Hue, accentSat, 1f),
        text = Color.WHITE, textDim = NEON_DIM, panel = Color.BLACK, danger = Neon.RED,
        glow = 1f, scanlines = true, vignette = 1f, shake = 1f, particles = 1f, light = false,
        bgHue = bgHue, bgSat = bgSat,
    )

    private const val NEON_DIM = 0xFFB8B4CC.toInt()
}
