package com.stackdrop.game.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SaveMergeTest {

    private val base = SaveSnapshot(
        rev = 10,
        gems = 100,
        unlockedThemes = setOf("mist"),
        selectedTheme = "mist",
        bests = mapOf("stack" to 20, "tower" to 5),
        streak = 3,
        lastDaily = 2000L,
        gamesPlayed = 40,
        settings = SaveSnapshot.Settings(sound = true, vibration = true, reduceMotion = false),
    )

    @Test
    fun progressIsCombined() {
        val local = base.copy(bests = mapOf("stack" to 30, "tower" to 5), unlockedThemes = setOf("mist"), gamesPlayed = 50)
        val remote = base.copy(bests = mapOf("stack" to 25, "tower" to 12), unlockedThemes = setOf("dune"), gamesPlayed = 45)
        val merged = SaveMerge.merge(local, remote)
        assertEquals(mapOf("stack" to 30, "tower" to 12), merged.bests)
        assertEquals(setOf("mist", "dune"), merged.unlockedThemes)
        assertEquals(50, merged.gamesPlayed)
    }

    @Test
    fun newerRevWinsSpendableValues() {
        val local = base.copy(rev = 5, gems = 999, streak = 1, selectedTheme = "dusk")
        val remote = base.copy(rev = 12, gems = 40, streak = 6, selectedTheme = "dune", lastDaily = 2100L)
        val merged = SaveMerge.merge(local, remote)
        assertEquals(40, merged.gems)
        assertEquals(6, merged.streak)
        assertEquals(2100L, merged.lastDaily)
        assertEquals("dune", merged.selectedTheme)
    }

    @Test
    fun localWinsTiesAndGemsAreNeverAdded() {
        val local = base.copy(gems = 70, settings = base.settings.copy(reduceMotion = true))
        val remote = base.copy(gems = 90)
        val merged = SaveMerge.merge(local, remote)
        assertEquals(70, merged.gems)
        assertEquals(true, merged.settings.reduceMotion)
    }

    @Test
    fun identicalSavesKeepTheirRev() {
        assertEquals(base, SaveMerge.merge(base, base))
    }

    @Test
    fun cloudCopyThatAlreadyHasEverythingIsReturnedAsIs() {
        val local = base.copy(rev = 4, gems = 10, bests = mapOf("stack" to 1, "tower" to 0))
        assertEquals(base, SaveMerge.merge(local, base))
    }

    @Test
    fun resultTheCloudLacksGetsANewerRev() {
        val local = base.copy(gems = 70)
        val merged = SaveMerge.merge(local, base)
        assertEquals(11, merged.rev)
        assertEquals(70, merged.gems)
    }

    @Test
    fun mergingTwiceIsStable() {
        val local = base.copy(rev = 3, bests = mapOf("stack" to 99, "tower" to 0))
        val once = SaveMerge.merge(local, base)
        assertEquals(once, SaveMerge.merge(once, once))
        assertEquals(once, SaveMerge.merge(base, once))
    }
}
