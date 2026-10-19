package com.stackdrop.game.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Checks every calm theme against the eye-comfort rules. */
class CalmThemesTest {

    private val themes = CalmThemes.all

    private fun tokens(t: Theme) = listOf(
        t.skyTop, t.skyMid, t.skyBottom, t.ground, t.accent, t.accent2, t.text, t.textDim, t.panel, t.danger,
    )

    @Test
    fun sevenThemesWithFreeDuskFirst() {
        assertEquals(7, themes.size)
        assertEquals("dusk", themes[0].id)
        assertEquals(0, themes[0].cost)
        assertTrue(themes.all { it.family == Family.CALM })
    }

    @Test
    fun noPureBlackOrWhite() {
        for (t in themes) for (c in tokens(t)) {
            val rgb = c and 0xFFFFFF
            assertTrue("${t.id} uses pure black", rgb != 0x000000)
            assertTrue("${t.id} uses pure white", rgb != 0xFFFFFF)
        }
    }

    @Test
    fun textIsReadableOnPanelsAndSky() {
        for (t in themes) {
            assertTrue("${t.id} text on panel", ColorMath.contrast(t.text, t.panel) >= 4.5)
            assertTrue("${t.id} dim text on panel", ColorMath.contrast(t.textDim, t.panel) >= 4.5)
            assertTrue("${t.id} text on sky", ColorMath.contrast(t.text, t.skyTop) >= 4.5)
            assertTrue("${t.id} dim text on sky", ColorMath.contrast(t.textDim, t.skyTop) >= 4.5)
        }
    }

    @Test
    fun lightThemesUseDarkText() {
        for (t in themes) {
            val darkText = ColorMath.luminance(t.text) < ColorMath.luminance(t.skyTop)
            assertEquals("${t.id} text vs sky", t.light, darkText)
        }
    }

    @Test
    fun blockColorsStayInComfortRange() {
        for (t in themes) {
            assertTrue("${t.id} saturation", t.sat in 0.22f..0.45f)
            assertTrue("${t.id} value", t.value in 0.72f..0.92f)
            assertTrue("${t.id} darker layer", t.value - t.valueWave in 0.72f..0.92f)
            assertTrue("${t.id} hue band", t.hueRange <= 25f)
        }
    }

    @Test
    fun neighbouringLayersDifferBySixToNinePercent() {
        for (t in themes) for (level in 0..60) {
            val a = ColorMath.lightness(CalmThemes.blockColor(t, level))
            val b = ColorMath.lightness(CalmThemes.blockColor(t, level + 1))
            val d = abs(a - b)
            assertTrue("${t.id} level $level differs by $d", d in 0.055f..0.095f)
        }
    }

    @Test
    fun feedbackIsScaledDown() {
        for (t in themes) {
            assertTrue(t.glow in 0.15f..0.25f)
            assertFalse(t.scanlines)
            assertEquals(0.35f, t.vignette, 0f)
            assertEquals(0.5f, t.shake, 0f)
            assertEquals(0.6f, t.particles, 0f)
        }
    }
}
