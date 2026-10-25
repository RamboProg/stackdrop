package com.stackdrop.game.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakBonusTest {

    /** Runs the streak frame by frame and returns the pieces in the order they landed. */
    private fun play(streak: StreakBonus, dt: Float = 1f / 60f): List<Int> {
        val landed = ArrayList<Int>()
        var frames = 0
        while (streak.active && frames++ < 10_000) {
            val i = streak.update(dt)
            if (i >= 0) landed += i
        }
        return landed
    }

    @Test
    fun everyFifthPerfectInARowCompletesAStreak() {
        val streaks = (0..16).filter { StreakBonus.completes(it) }
        assertEquals(listOf(5, 10, 15), streaks)
    }

    @Test
    fun allFivePiecesLandInOrderThenTheStreakPays() {
        val streak = StreakBonus()
        streak.start()
        assertTrue(streak.active)
        assertEquals(0, streak.gems)

        assertEquals(listOf(0, 1, 2, 3, 4), play(streak))
        assertFalse(streak.active)
        assertEquals(1, streak.count)
        assertEquals(StreakBonus.GEMS, streak.gems)
    }

    @Test
    fun nothingLandsDuringTheLeadIn() {
        val streak = StreakBonus()
        streak.start()
        assertEquals(-1, streak.update(StreakBonus.LEAD_IN * 0.9f))
        assertEquals(0f, streak.fall, 0f)
        assertEquals(StreakBonus.STACKS, streak.remaining)
    }

    @Test
    fun piecesFallFasterAndTheWholeStreakStaysShort() {
        for (i in 1 until StreakBonus.STACKS) {
            assertTrue(StreakBonus.fallTime(i) < StreakBonus.fallTime(i - 1))
        }
        val total = StreakBonus.LEAD_IN + (0 until StreakBonus.STACKS).sumOf { StreakBonus.fallTime(it).toDouble() }
        assertTrue("streak takes ${total}s", total < 1.0)
    }

    @Test
    fun fallEasesInFromTopToLanding() {
        val streak = StreakBonus()
        streak.start()
        streak.update(StreakBonus.LEAD_IN + 0.01f)
        streak.update(StreakBonus.fallTime(0) * 0.5f)
        assertEquals(0.25f, streak.fall, 0.05f)
    }

    @Test
    fun cancelledStreakPaysNothingAndResetClearsTheRun() {
        val streak = StreakBonus()
        streak.start()
        play(streak)
        streak.start()
        streak.cancel()
        assertFalse(streak.active)
        assertEquals(1, streak.count)

        streak.reset()
        assertEquals(0, streak.count)
        assertEquals(0, streak.gems)
    }
}
