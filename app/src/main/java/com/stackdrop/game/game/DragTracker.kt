package com.stackdrop.game.game

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot

/** Tells taps from vertical drags, and keeps a released drag gliding with gentle friction. */
class DragTracker {

    /** Travel in px below which a press still counts as a tap; also the speed (px/s) where a glide stops. */
    var slop = 0f

    private var startX = 0f
    private var startY = 0f
    private var lastY = 0f
    private var active = false
    private var dragging = false
    private var pending = 0f
    private var velocity = 0f

    fun down(x: Float, y: Float) {
        startX = x
        startY = y
        lastY = y
        active = true
        dragging = false
        pending = 0f
        velocity = 0f
    }

    /** Returns the vertical scroll in px caused by this move, or 0 while it is still a tap. */
    fun move(x: Float, y: Float): Float {
        if (!active) return 0f
        if (!dragging && hypot(x - startX, y - startY) >= slop) dragging = true
        val dy = y - lastY
        lastY = y
        if (!dragging) return 0f
        pending += dy
        return dy
    }

    /** Returns true when the press ended as a tap. */
    fun up(): Boolean {
        if (!active) return false
        active = false
        return !dragging
    }

    fun cancel() {
        active = false
        dragging = false
        pending = 0f
        velocity = 0f
    }

    /** Returns the glide scroll in px for this step. */
    fun update(dt: Float): Float {
        if (active) {
            if (dragging && dt > 0f) velocity += (pending / dt - velocity) * SMOOTHING
            pending = 0f
            return 0f
        }
        if (abs(velocity) <= slop) {
            velocity = 0f
            return 0f
        }
        velocity *= exp(-FRICTION * dt)
        return velocity * dt
    }

    private companion object {
        const val FRICTION = 4f
        const val SMOOTHING = 0.5f
    }
}
