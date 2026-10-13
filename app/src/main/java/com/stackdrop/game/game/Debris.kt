package com.stackdrop.game.game

import android.graphics.Canvas
import kotlin.random.Random

class Debris(
    private val block: Block,
    private val vx: Float,
    private val vz: Float,
) {
    private var vy = 0f
    private var rotation = 0f
    private var age = 0f

    // On screen, +x pieces fall to the right and +z pieces fall to the left.
    private val spin = (if (vx > 0f || vz < 0f) 1f else -1f) * (50f + Random.nextFloat() * 80f)

    val alive get() = age < LIFETIME
    val depth get() = block.x + block.w / 2f + block.z + block.d / 2f

    fun update(dt: Float) {
        age += dt
        vy -= GRAVITY * dt
        block.x += vx * dt
        block.z += vz * dt
        block.y += vy * dt
        rotation += spin * dt
    }

    fun draw(canvas: Canvas, iso: IsoRenderer) {
        val cx = block.x + block.w / 2f
        val cz = block.z + block.d / 2f
        val px = iso.sx(cx, cz)
        val py = iso.sy(cx, block.y + block.h / 2f, cz)
        val alpha = (255 * (1f - age / LIFETIME)).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.rotate(rotation, px, py)
        iso.drawBlock(canvas, block, alpha)
        canvas.restore()
    }

    private companion object {
        const val GRAVITY = 40f
        const val LIFETIME = 2f
    }
}
