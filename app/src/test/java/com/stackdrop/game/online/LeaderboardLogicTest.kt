package com.stackdrop.game.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeaderboardLogicTest {

    private fun utc(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long {
        val c = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC"))
        c.clear()
        c.set(y, m - 1, d, h, min)
        return c.timeInMillis
    }

    @Test
    fun weekIdsMatchTheServer() {
        // Same cases as backend/functions/test/week.test.ts.
        assertEquals("2026-W42", IsoWeek.id(utc(2026, 10, 18, 23, 59)))
        assertEquals("2026-W43", IsoWeek.id(utc(2026, 10, 19)))
        assertEquals("2026-W01", IsoWeek.id(utc(2026, 1, 1, 12)))
        assertEquals("2026-W01", IsoWeek.id(utc(2025, 12, 29)))
        assertEquals("2025-W01", IsoWeek.id(utc(2024, 12, 30)))
        assertEquals("2020-W53", IsoWeek.id(utc(2021, 1, 3)))
    }

    @Test
    fun resetCountsDownToMondayMidnightUtc() {
        assertEquals(7 * 86_400_000L, IsoWeek.msUntilReset(utc(2026, 10, 19)))
        assertEquals(60_000L, IsoWeek.msUntilReset(utc(2026, 10, 18, 23, 59)))
        assertEquals("3d 4h", IsoWeek.countdown((3 * 24 + 4) * 3_600_000L + 59_000))
        assertEquals("5h 12m", IsoWeek.countdown((5 * 60 + 12) * 60_000L))
        assertEquals("1m", IsoWeek.countdown(5_000))
    }

    @Test
    fun boardIds() {
        assertEquals("tower_all", Boards.id("tower", weekly = false, nowMs = 0))
        assertEquals("stack_2026-W43", Boards.id("stack", weekly = true, nowMs = utc(2026, 10, 20)))
    }

    @Test
    fun equalScoresShareARank() {
        assertEquals(listOf(1, 2, 2, 4, 5), Boards.ranks(listOf(90, 70, 70, 60, 10)))
        assertEquals(emptyList<Int>(), Boards.ranks(emptyList()))
    }

    @Test
    fun boardsSurviveTheDiskCache() {
        val board = BoardSnapshot(
            id = "tower_all",
            entries = listOf(BoardEntry("u1", "Mo_92", 3, 41, 1), BoardEntry("u2", "Ali B", 0, 41, 1)),
            myRank = 128, myScore = 34, fetchedAtMs = 1234L, fromCache = false, offline = false,
        )
        assertEquals(board.copy(fromCache = true), BoardCodec.decode(BoardCodec.encode(board)))
        val empty = board.copy(entries = emptyList(), myRank = null, myScore = null)
        assertEquals(empty.copy(fromCache = true), BoardCodec.decode(BoardCodec.encode(empty)))
        assertNull(BoardCodec.decode("garbage"))
    }
}
