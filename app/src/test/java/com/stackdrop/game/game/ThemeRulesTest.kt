package com.stackdrop.game.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeRulesTest {

    private val themes = CalmThemes.all
    private fun id(id: String) = themes.first { it.id == id }

    @Test
    fun freeThemeRotatesDailyThroughLockedThemes() {
        val none: (Theme) -> Boolean = { false }
        assertEquals("mist", ThemeRules.freeToday(themes, 0, none)?.id)
        assertEquals("lagoon", ThemeRules.freeToday(themes, 1, none)?.id)
        assertEquals("mist", ThemeRules.freeToday(themes, 6, none)?.id)
    }

    @Test
    fun freeThemeSkipsOwnedAndFreeThemes() {
        val free = ThemeRules.freeToday(themes, 0) { it.id == "mist" }
        assertEquals("lagoon", free?.id)
    }

    @Test
    fun noFreeThemeWhenEverythingIsOwned() {
        assertNull(ThemeRules.freeToday(themes, 3) { true })
    }

    @Test
    fun milestoneUnlocksOnlyWhenCrossed() {
        assertEquals(listOf(id("aurora")), ThemeRules.newlyReached(themes, "tower", 20, 25))
        assertEquals(emptyList<Theme>(), ThemeRules.newlyReached(themes, "tower", 25, 30))
        assertEquals(emptyList<Theme>(), ThemeRules.newlyReached(themes, "tower", 10, 24))
        assertEquals(listOf(id("lagoon")), ThemeRules.newlyReached(themes, "stack", 39, 40))
        assertEquals(emptyList<Theme>(), ThemeRules.newlyReached(themes, "stack", 0, 30))
    }

    @Test
    fun premiumThemesFollowThePurchaseOnly() {
        val gilded = id("gilded")
        assertEquals(false, ThemeRules.isUnlocked(gilded, bought = false, milestoneBest = 0, premium = false))
        // Gems or a stray unlock record never open it; only premium does.
        assertEquals(false, ThemeRules.isUnlocked(gilded, bought = true, milestoneBest = 0, premium = false))
        assertEquals(true, ThemeRules.isUnlocked(gilded, bought = false, milestoneBest = 0, premium = true))
    }

    @Test
    fun otherThemesUnlockByPriceOrMilestone() {
        assertEquals(true, ThemeRules.isUnlocked(id("dusk"), false, 0, false))
        assertEquals(false, ThemeRules.isUnlocked(id("mist"), false, 0, false))
        assertEquals(true, ThemeRules.isUnlocked(id("mist"), true, 0, false))
        assertEquals(false, ThemeRules.isUnlocked(id("aurora"), false, 24, false))
        assertEquals(true, ThemeRules.isUnlocked(id("aurora"), false, 25, false))
    }

    @Test
    fun freeThemeIsNeverThePremiumOne() {
        for (day in 0L..20L) {
            val free = ThemeRules.freeToday(themes, day) { false }
            assertEquals(false, free?.premiumOnly)
        }
    }

    @Test
    fun milestoneHintNamesTheMode() {
        assertEquals("or reach 25 in Tower", ThemeRules.milestoneHint(Milestone("tower", 25)))
    }
}
