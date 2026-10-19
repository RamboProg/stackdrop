package com.stackdrop.game.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DragTrackerTest {

    private val drag = DragTracker()

    @Before
    fun setUp() {
        drag.slop = 20f
    }

    @Test
    fun smallMovementStillCountsAsTap() {
        drag.down(100f, 100f)
        assertEquals(0f, drag.move(105f, 110f), 0f)
        assertTrue(drag.up())
    }

    @Test
    fun movementPastSlopScrollsAndIsNotATap() {
        drag.down(100f, 100f)
        drag.move(100f, 110f)
        assertEquals(30f, drag.move(100f, 140f), 0.001f)
        assertEquals(-10f, drag.move(100f, 130f), 0.001f)
        assertFalse(drag.up())
    }

    @Test
    fun releasedDragGlidesAndSlowsDown() {
        drag.down(0f, 0f)
        drag.move(0f, 30f)
        drag.update(STEP)
        drag.move(0f, 60f)
        drag.update(STEP)
        drag.up()

        val first = drag.update(STEP)
        val later = drag.update(STEP)
        assertTrue(first > 0f)
        assertTrue(later in 0f..first)
    }

    @Test
    fun glideStopsEventually() {
        drag.down(0f, 0f)
        drag.move(0f, 50f)
        drag.update(STEP)
        drag.up()
        repeat(60 * 10) { drag.update(STEP) }
        assertEquals(0f, drag.update(STEP), 0f)
    }

    @Test
    fun newPressStopsGlide() {
        drag.down(0f, 0f)
        drag.move(0f, 50f)
        drag.update(STEP)
        drag.up()
        drag.down(0f, 0f)
        assertEquals(0f, drag.update(STEP), 0f)
    }

    @Test
    fun cancelledPressIsNeitherTapNorGlide() {
        drag.down(0f, 0f)
        drag.cancel()
        assertFalse(drag.up())
        assertEquals(0f, drag.update(STEP), 0f)
    }

    private companion object {
        const val STEP = 1f / 60f
    }
}
