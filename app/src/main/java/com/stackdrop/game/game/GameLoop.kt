package com.stackdrop.game.game

import android.graphics.Canvas
import android.os.Build
import android.view.SurfaceHolder
import kotlin.math.min

class GameLoop(
    private val holder: SurfaceHolder,
    private val game: Game,
) : Thread("GameLoop") {

    @Volatile
    private var running = true

    override fun run() {
        var last = System.nanoTime()
        var accumulator = 0.0

        while (running) {
            val now = System.nanoTime()
            accumulator += min((now - last) / 1_000_000_000.0, MAX_FRAME)
            last = now

            while (accumulator >= STEP) {
                game.update(STEP.toFloat())
                accumulator -= STEP
            }

            val canvas = lock()
            if (canvas == null) {
                Thread.sleep(16)
                continue
            }
            try {
                game.render(canvas)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    private fun lock(): Canvas? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) holder.lockHardwareCanvas() else holder.lockCanvas()
    } catch (e: Exception) {
        null
    }

    fun stopLoop() {
        running = false
        while (true) {
            try {
                join()
                return
            } catch (e: InterruptedException) {
                // keep waiting for the render thread to finish
            }
        }
    }

    private companion object {
        const val STEP = 1.0 / 60.0
        const val MAX_FRAME = 0.25
    }
}
