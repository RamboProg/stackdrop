package com.stackdrop.game.game

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.stackdrop.game.online.OnlineApi
import com.stackdrop.game.platform.PlatformBridge

class GameView(context: Context, bridge: PlatformBridge, online: OnlineApi) : SurfaceView(context), SurfaceHolder.Callback {

    private val game = Game(context, bridge, online)
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
                game.onTouch(TouchQueue.DOWN, event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_MOVE -> game.onTouch(TouchQueue.MOVE, event.getX(0), event.getY(0))
            MotionEvent.ACTION_UP -> game.onTouch(TouchQueue.UP, event.x, event.y)
            MotionEvent.ACTION_CANCEL -> game.onTouch(TouchQueue.CANCEL, event.x, event.y)
        }
        return true
    }

    fun post(event: GameEvent) = game.post(event)

    fun onResumeGame() = game.onResume()

    fun onPauseGame() = game.onPause()

    fun release() = game.release()
}
