package com.stackdrop.game.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SaveMergeTest {

    private val base = SaveSnapshot(
        rev = 10,
        ledger = mapOf("a" to GemCount(150, 50)),
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
    fun newerRevWinsThemeAndSettings() {
        val local = base.copy(rev = 5, selectedTheme = "dusk")
        val remote = base.copy(rev = 12, selectedTheme = "dune", settings = base.settings.copy(sound = false))
        val merged = SaveMerge.merge(local, remote)
        assertEquals("dune", merged.selectedTheme)
        assertEquals(false, merged.settings.sound)
    }

    @Test
    fun localWinsTies() {
        val local = base.copy(settings = base.settings.copy(reduceMotion = true))
        val merged = SaveMerge.merge(local, base)
        assertEquals(true, merged.settings.reduceMotion)
    }

    @Test
    fun latestDailyClaimWinsWithItsStreak() {
        // The phone with more edits claimed earlier; the later claim must still win.
        val local = base.copy(rev = 50, streak = 6, lastDaily = 2000L)
        val remote = base.copy(rev = 11, streak = 1, lastDaily = 2005L)
        val merged = SaveMerge.merge(local, remote)
        assertEquals(2005L, merged.lastDaily)
        assertEquals(1, merged.streak)
    }

    @Test
    fun gemsEarnedOnBothPhonesAddUp() {
        val local = base.copy(rev = 50, ledger = base.ledger + ("b" to GemCount(30, 0)))
        val remote = base.copy(ledger = mapOf("a" to GemCount(4150, 50)))
        // 4100 from phone a (whatever its rev) plus 30 from phone b.
        assertEquals(4130, SaveMerge.merge(local, remote).gems)
    }

    @Test
    fun themeBoughtOnAnotherPhoneIsPaidFor() {
        // Phone b spent 80 on a theme while phone a earned 50.
        val local = base.copy(ledger = base.ledger + ("b" to GemCount(0, 80)), unlockedThemes = setOf("mist", "dune"))
        val remote = base.copy(ledger = mapOf("a" to GemCount(200, 50)))
        val merged = SaveMerge.merge(local, remote)
        assertEquals(setOf("mist", "dune"), merged.unlockedThemes)
        assertEquals(70, merged.gems)
    }

    @Test
    fun identicalSavesKeepTheirRev() {
        assertEquals(base, SaveMerge.merge(base, base))
    }

    @Test
    fun cloudCopyThatAlreadyHasEverythingIsReturnedAsIs() {
        val local = base.copy(rev = 4, ledger = mapOf("a" to GemCount(10, 0)), bests = mapOf("stack" to 1, "tower" to 0))
        assertEquals(base, SaveMerge.merge(local, base))
    }

    @Test
    fun resultTheCloudLacksGetsANewerRev() {
        val local = base.copy(ledger = mapOf("a" to GemCount(170, 50)))
        val merged = SaveMerge.merge(local, base)
        assertEquals(11, merged.rev)
        assertEquals(120, merged.gems)
    }

    @Test
    fun mergingAgainChangesNothing() {
        val local = base.copy(rev = 3, bests = mapOf("stack" to 99, "tower" to 0), ledger = mapOf("b" to GemCount(5, 0)))
        val once = SaveMerge.merge(local, base)
        assertEquals(once, SaveMerge.merge(once, once))
        assertEquals(once, SaveMerge.merge(base, once))
        assertEquals(once.gems, SaveMerge.merge(once, base).gems)
    }
}
