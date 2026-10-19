package com.stackdrop.game.game

import kotlin.math.sin

/**
 * The calm family: matte blocks, soft skies, slow scenery. Every token here is tuned for the comfort
 * rules (no pure black or white, ≥ 4.5:1 text contrast, 6–9% lightness between layers); the unit
 * tests check them.
 */
object CalmThemes {

    val all = listOf(
        calm(
            "dusk", "Dusk", 0, "warm", baseHue = 0f, hueRange = 15f, sat = 0.32f, value = 0.88f,
            skyTop = 0xFF2E2F4F, skyMid = 0xFF6B5B7B, skyBottom = 0xFFD9A3A0, ground = 0xFF3A2E44,
            scenery = Scenery.HILLS, ambient = Ambient.STARS,
            accent = 0xFFF2C9A6, accent2 = 0xFFE8A0A8, text = 0xFFF6EFE8, textDim = 0xFFC9BFC8,
            panel = 0xFF24253D, danger = 0xFFE58C8A, light = false,
        ),
        calm(
            "mist", "Mist", 60, "quiet", baseHue = 157f, hueRange = 17f, sat = 0.25f, value = 0.81f,
            skyTop = 0xFFE4ECE8, skyMid = 0xFFC9D8D2, skyBottom = 0xFFA9BFB8, ground = 0xFF6F8C83,
            scenery = Scenery.FOREST_MIST, ambient = Ambient.FOG,
            accent = 0xFF6E8F86, accent2 = 0xFF8FB3A0, text = 0xFF2F3B38, textDim = 0xFF4F625D,
            panel = 0xFFF1F5F3, danger = 0xFFC0605A, light = true,
        ),
        calm(
            "lagoon", "Lagoon", 120, "deep", baseHue = 185f, hueRange = 15f, sat = 0.38f, value = 0.82f,
            skyTop = 0xFF0F2A3A, skyMid = 0xFF174456, skyBottom = 0xFF1F5C66, ground = 0xFF12313C,
            scenery = Scenery.LAGOON, ambient = Ambient.BUBBLES,
            accent = 0xFF8FD3C8, accent2 = 0xFFF0C987, text = 0xFFE8F4F2, textDim = 0xFFA8C9C4,
            panel = 0xFF0B2230, danger = 0xFFF09A8A, light = false,
            milestone = Milestone("stack", 40),
        ),
        calm(
            "aurora", "Aurora", 200, "arctic", baseHue = 220f, hueRange = 25f, sat = 0.30f, value = 0.86f,
            skyTop = 0xFF0B1026, skyMid = 0xFF16224A, skyBottom = 0xFF24365E, ground = 0xFF1A2440,
            scenery = Scenery.AURORA_PEAKS, ambient = Ambient.AURORA,
            accent = 0xFF9FE3C9, accent2 = 0xFFB9A8F0, text = 0xFFEEF2FA, textDim = 0xFFB4BED4,
            panel = 0xFF0A0E22, danger = 0xFFF29AAE, light = false,
            milestone = Milestone("tower", 25),
        ),
        calm(
            "sakura", "Sakura", 300, "gentle", baseHue = 2f, hueRange = 17f, sat = 0.26f, value = 0.92f,
            skyTop = 0xFFFBEDEA, skyMid = 0xFFF7DCDD, skyBottom = 0xFFEEC7CC, ground = 0xFFC99AA3,
            scenery = Scenery.PETALS_GARDEN, ambient = Ambient.PETALS,
            accent = 0xFFD98C9C, accent2 = 0xFFE7A9B5, text = 0xFF4A3A40, textDim = 0xFF7A5F68,
            panel = 0xFFFDF5F3, danger = 0xFFC9606F, light = true,
        ),
        calm(
            "dune", "Dune", 450, "sunny", baseHue = 27f, hueRange = 12f, sat = 0.40f, value = 0.86f,
            skyTop = 0xFFF6E7CF, skyMid = 0xFFEED3AE, skyBottom = 0xFFE2B98F, ground = 0xFFC8925F,
            scenery = Scenery.DUNES, ambient = Ambient.NONE,
            accent = 0xFFC7774F, accent2 = 0xFFE3A765, text = 0xFF4A3528, textDim = 0xFF725545,
            panel = 0xFFFBF2E4, danger = 0xFFB9553A, light = true,
        ),
        calm(
            "slate", "Slate", 600, "minimal", baseHue = 220f, hueRange = 10f, sat = 0.22f, value = 0.81f,
            skyTop = 0xFF15171B, skyMid = 0xFF1A1D23, skyBottom = 0xFF1F232A, ground = 0xFF1B1E24,
            scenery = Scenery.SLATE, ambient = Ambient.MOTES,
            accent = 0xFFA9B8CC, accent2 = 0xFFC9D2DE, text = 0xFFE6E9EE, textDim = 0xFFA3ABB8,
            panel = 0xFF101216, danger = 0xFFE0868A, light = false,
        ),
    )

    /** Hue swings gently within the theme's band; every other layer is a little darker. */
    fun blockColor(t: Theme, level: Int): Int =
        ColorMath.hsv(t.baseHue + sin(level * t.hueStep) * t.hueRange, t.sat, t.value - t.valueWave * (level and 1))

    private fun calm(
        id: String, name: String, cost: Int, mood: String,
        baseHue: Float, hueRange: Float, sat: Float, value: Float,
        skyTop: Long, skyMid: Long, skyBottom: Long, ground: Long,
        scenery: Scenery, ambient: Ambient,
        accent: Long, accent2: Long, text: Long, textDim: Long, panel: Long, danger: Long,
        light: Boolean, milestone: Milestone? = null,
    ) = Theme(
        id = id, name = name, cost = cost, family = Family.CALM, mood = mood,
        baseHue = baseHue, hueStep = HUE_STEP, hueRange = hueRange, sat = sat, value = value, valueWave = LAYER_STEP,
        skyTop = skyTop.toInt(), skyMid = skyMid.toInt(), skyBottom = skyBottom.toInt(), ground = ground.toInt(),
        scenery = scenery, ambient = ambient,
        accent = accent.toInt(), accent2 = accent2.toInt(), text = text.toInt(), textDim = textDim.toInt(),
        panel = panel.toInt(), danger = danger.toInt(),
        glow = GLOW, scanlines = false, vignette = VIGNETTE, shake = SHAKE, particles = PARTICLES,
        light = light, milestone = milestone,
    )

    /** Radians per level: the hue swings through its band over about 28 floors. */
    private const val HUE_STEP = 0.22f
    /** Every other layer is this much darker in HSV value, about 6–8% in lightness. */
    private const val LAYER_STEP = 0.085f
    private const val GLOW = 0.2f
    private const val VIGNETTE = 0.35f
    private const val SHAKE = 0.5f
    private const val PARTICLES = 0.6f
}
