package com.stackdrop.game.game

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.stackdrop.game.online.OnlineApi
import com.stackdrop.game.platform.PlatformBridge

class GameView(context: Context, bridge: PlatformBridge, online: OnlineApi) : SurfaceView(context), SurfaceHolder.Callback {

    private val game = Game(context, bridge, online)
    private var loop: GameLoop? = null

    init {
        holder.addCallback(this)
        isFocusable = true
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            game.post(GameEvent.TopInset(insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top))
            insets
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) = Unit

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (loop == null) {
            // No game thread yet, so the game can be sized directly before it starts.
            game.resize(width, height)
            loop = GameLoop(holder, game).also { it.start() }
        } else {
            // The game thread may be mid-frame; it applies the new size before its next update.
            game.post(GameEvent.Resize(width, height))
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        loop?.stopLoop()
        loop = null
    }

    /** The finger that started the gesture; drags follow it alone, so a second finger can't make lists jump. */
    private var activePointer = MotionEvent.INVALID_POINTER_ID

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointer = event.getPointerId(0)
                game.onTouch(TouchQueue.DOWN, event.x, event.y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                game.onTouch(TouchQueue.EXTRA_DOWN, event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                val i = event.findPointerIndex(activePointer)
                if (i >= 0) game.onTouch(TouchQueue.MOVE, event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == activePointer) {
                // The dragging finger lifted while another stays down: end the drag there.
                game.onTouch(TouchQueue.CANCEL, event.getX(event.actionIndex), event.getY(event.actionIndex))
                activePointer = MotionEvent.INVALID_POINTER_ID
            }
            MotionEvent.ACTION_UP -> {
                if (event.getPointerId(0) == activePointer) game.onTouch(TouchQueue.UP, event.x, event.y)
                activePointer = MotionEvent.INVALID_POINTER_ID
            }
            MotionEvent.ACTION_CANCEL -> {
                game.onTouch(TouchQueue.CANCEL, event.x, event.y)
                activePointer = MotionEvent.INVALID_POINTER_ID
            }
        }
        return true
    }

    fun post(event: GameEvent) = game.post(event)

    fun onResumeGame() = game.onResume()

    fun onPauseGame() = game.onPause()

    /** Stops the game thread before releasing sound, so nothing plays on released players. */
    fun release() {
        loop?.stopLoop()
        loop = null
        game.release()
    }
}
