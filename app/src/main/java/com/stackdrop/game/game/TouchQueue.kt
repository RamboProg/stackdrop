package com.stackdrop.game.game

fun interface TouchSink {
    fun onTouch(action: Int, x: Float, y: Float)
}

/**
 * Fixed-size touch buffer filled on the UI thread and drained on the game thread without allocating.
 * Consecutive moves are merged, and the oldest event is dropped if the game thread falls behind.
 */
class TouchQueue {

    private val actions = IntArray(CAPACITY)
    private val xs = FloatArray(CAPACITY)
    private val ys = FloatArray(CAPACITY)
    private var head = 0
    private var size = 0

    @Synchronized
    fun push(action: Int, x: Float, y: Float) {
        if (action == MOVE && size > 0) {
            val last = (head + size - 1) % CAPACITY
            if (actions[last] == MOVE) {
                xs[last] = x
                ys[last] = y
                return
            }
        }
        if (size == CAPACITY) {
            head = (head + 1) % CAPACITY
            size--
        }
        val i = (head + size) % CAPACITY
        actions[i] = action
        xs[i] = x
        ys[i] = y
        size++
    }

    fun drain(sink: TouchSink) {
        var action = 0
        var x = 0f
        var y = 0f
        while (true) {
            synchronized(this) {
                if (size == 0) return
                action = actions[head]
                x = xs[head]
                y = ys[head]
                head = (head + 1) % CAPACITY
                size--
            }
            sink.onTouch(action, x, y)
        }
    }

    companion object {
        const val DOWN = 0
        const val MOVE = 1
        const val UP = 2
        const val CANCEL = 3

        /** A second finger touching down: a tap during play, ignored by scrolling lists. */
        const val EXTRA_DOWN = 4
        private const val CAPACITY = 64
    }
}
