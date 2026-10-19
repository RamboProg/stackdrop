package com.stackdrop.game.game

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorMathTest {

    @Test
    fun hsvPrimaries() {
        assertEquals(0xFFFF0000.toInt(), ColorMath.hsv(0f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), ColorMath.hsv(120f, 1f, 1f))
        assertEquals(0xFF0000FF.toInt(), ColorMath.hsv(240f, 1f, 1f))
    }

    @Test
    fun hsvWrapsHueAndRounds() {
        assertEquals(ColorMath.hsv(10f, 0.5f, 0.8f), ColorMath.hsv(370f, 0.5f, 0.8f))
        assertEquals(ColorMath.hsv(350f, 0.5f, 0.8f), ColorMath.hsv(-10f, 0.5f, 0.8f))
        assertEquals(0xFF808080.toInt(), ColorMath.hsv(0f, 0f, 0.5f))
        assertEquals(0xFF8080FF.toInt(), ColorMath.hsv(240f, 0.5f, 1f))
    }

    @Test
    fun contrastExtremes() {
        assertEquals(21.0, ColorMath.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, ColorMath.contrast(0xFF336699.toInt(), 0xFF336699.toInt()), 0.0001)
    }

    @Test
    fun mixAndAlpha() {
        assertEquals(0xFF808080.toInt(), ColorMath.mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f))
        assertEquals(0x40123456, ColorMath.withAlpha(0xFF123456.toInt(), 0x40))
    }
}
