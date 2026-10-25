package com.stackdrop.game.game

import android.graphics.Canvas
import android.os.Build
import android.util.Log
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

    // The surface can go away between frames (app leaving the screen); the loop then idles until
    // it is stopped. Logged once per loop so a real drawing problem still shows up.
    private fun lock(): Canvas? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) holder.lockHardwareCanvas() else holder.lockCanvas()
    } catch (e: Exception) {
        if (!lockFailureLogged) {
            lockFailureLogged = true
            Log.w(TAG, "Couldn't lock the surface; skipping frames", e)
        }
        null
    }

    private var lockFailureLogged = false

    fun stopLoop() {
        running = false
        var interrupted = false
        while (true) {
            try {
                join()
                break
            } catch (e: InterruptedException) {
                // Keep waiting for the render thread to finish, then pass the interrupt on.
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    private companion object {
        const val STEP = 1.0 / 60.0
        const val MAX_FRAME = 0.25
        const val TAG = "GameLoop"
    }
}
