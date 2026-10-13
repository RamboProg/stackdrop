package com.stackdrop.game.game

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val game = Game(context)
    private var loop: GameLoop? = null

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) = Unit

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        game.resize(width, height)
        if (loop == null) {
            loop = GameLoop(holder, game).also { it.start() }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        loop?.stopLoop()
        loop = null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                game.onTap(event.getX(i), event.getY(i))
            }
        }
        return true
    }

    fun onResumeGame() = game.onResume()

    fun onPauseGame() = game.onPause()

    fun release() = game.release()
}
