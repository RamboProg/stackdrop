package com.stackdrop.game.game

import kotlin.math.exp
import kotlin.random.Random

class Camera {

    var x = 0f
    var targetX = 0f
    var y = 0f
    var targetY = 0f
    var zoom = 1f
    var targetZoom = 1f

    var shakeX = 0f
        private set
    var shakeY = 0f
        private set

    private var shakeTime = 0f
    private var shakeDuration = 1f
    private var shakeMagnitude = 0f

    fun update(dt: Float) {
        x += (targetX - x) * (1f - exp(-3f * dt))
        y += (targetY - y) * (1f - exp(-7f * dt))
        zoom += (targetZoom - zoom) * (1f - exp(-2.5f * dt))

        if (shakeTime > 0f) {
            shakeTime -= dt
            val m = shakeMagnitude * (shakeTime / shakeDuration).coerceAtLeast(0f)
            shakeX = (Random.nextFloat() * 2f - 1f) * m
            shakeY = (Random.nextFloat() * 2f - 1f) * m
        } else {
            shakeX = 0f
            shakeY = 0f
        }
    }

    fun shake(magnitude: Float, duration: Float) {
        val current = if (shakeTime > 0f) shakeMagnitude * shakeTime / shakeDuration else 0f
        if (magnitude < current) return
        shakeMagnitude = magnitude
        shakeDuration = duration
        shakeTime = duration
    }

    fun snap() {
        x = targetX
        y = targetY
        zoom = targetZoom
    }
}
