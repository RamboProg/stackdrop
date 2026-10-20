package com.stackdrop.game.data

import com.stackdrop.game.game.RunStats
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingRunsTest {

    private fun run(mode: String, id: String, score: Int) =
        PendingRun(mode, id, RunStats(score, 60_000L, score + 2, 1, 1, revived = false))

    @Test
    fun keepsOnlyTheBestRunPerMode() {
        var q = PendingRuns.add(emptyList(), run("tower", "a", 10))
        q = PendingRuns.add(q, run("tower", "b", 25))
        q = PendingRuns.add(q, run("tower", "c", 12))
        q = PendingRuns.add(q, run("stack", "d", 40))
        assertEquals(listOf("d", "b"), q.map { it.runId })
    }

    @Test
    fun aTieKeepsTheNewestRun() {
        val q = PendingRuns.add(listOf(run("stack", "old", 7)), run("stack", "new", 7))
        assertEquals(listOf("new"), q.map { it.runId })
    }

    @Test
    fun neverHoldsMoreThanFive() {
        var q = emptyList<PendingRun>()
        for (i in 0 until 8) q = PendingRuns.add(q, run("mode$i", "r$i", i))
        assertEquals(PendingRuns.MAX, q.size)
        assertEquals("r7", q.first().runId)
    }

    @Test
    fun roundTripsThroughStorage() {
        val q = listOf(run("tower", "8f2c-1a7e", 25), run("stack", "x-y-z", 40).copy(stats = RunStats(40, 9L, 41, 2, 2, true)))
        assertEquals(q, PendingRuns.decode(PendingRuns.encode(q)))
        assertEquals(emptyList<PendingRun>(), PendingRuns.decode(""))
        assertEquals(emptyList<PendingRun>(), PendingRuns.decode("broken|line"))
    }
}
