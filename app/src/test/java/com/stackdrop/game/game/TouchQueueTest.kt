package com.stackdrop.game.game

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchQueueTest {

    private val queue = TouchQueue()

    private fun drainAll(): List<Triple<Int, Float, Float>> {
        val out = ArrayList<Triple<Int, Float, Float>>()
        queue.drain { a, x, y -> out.add(Triple(a, x, y)) }
        return out
    }

    @Test
    fun drainsInOrder() {
        queue.push(TouchQueue.DOWN, 1f, 2f)
        queue.push(TouchQueue.UP, 3f, 4f)
        assertEquals(
            listOf(Triple(TouchQueue.DOWN, 1f, 2f), Triple(TouchQueue.UP, 3f, 4f)),
            drainAll(),
        )
        assertEquals(emptyList<Triple<Int, Float, Float>>(), drainAll())
    }

    @Test
    fun consecutiveMovesMergeIntoLatest() {
        queue.push(TouchQueue.DOWN, 0f, 0f)
        queue.push(TouchQueue.MOVE, 1f, 1f)
        queue.push(TouchQueue.MOVE, 2f, 5f)
        queue.push(TouchQueue.UP, 2f, 5f)
        assertEquals(
            listOf(
                Triple(TouchQueue.DOWN, 0f, 0f),
                Triple(TouchQueue.MOVE, 2f, 5f),
                Triple(TouchQueue.UP, 2f, 5f),
            ),
            drainAll(),
        )
    }

    @Test
    fun overflowDropsOldest() {
        repeat(70) { queue.push(TouchQueue.DOWN, it.toFloat(), 0f) }
        val drained = drainAll()
        assertEquals(64, drained.size)
        assertEquals(6f, drained.first().second, 0f)
        assertEquals(69f, drained.last().second, 0f)
    }
}
