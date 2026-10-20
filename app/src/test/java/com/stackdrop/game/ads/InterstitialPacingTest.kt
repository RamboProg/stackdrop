package com.stackdrop.game.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialPacingTest {

    private var now = 0L
    private val minute = 60_000L

    private fun pacing() = InterstitialPacing { now }

    /** A pacing state where every rule passes, so each test can break exactly one. */
    private fun ready(): InterstitialPacing {
        val p = pacing()
        repeat(4) { p.onRunEnded() }
        now += 5 * minute
        return p
    }

    @Test
    fun showsWhenEveryRuleHolds() {
        assertTrue(ready().shouldShow(totalRuns = 20, lastRunWasNewBest = false, adLoaded = true))
    }

    @Test
    fun neverWithoutALoadedAd() {
        assertFalse(ready().shouldShow(20, false, adLoaded = false))
    }

    @Test
    fun neverAfterANewBest() {
        assertFalse(ready().shouldShow(20, lastRunWasNewBest = true, adLoaded = true))
    }

    @Test
    fun needsFourRunsSinceTheLastInterstitial() {
        val p = ready()
        p.onFullScreenShown(interstitial = true)
        now += 10 * minute
        repeat(3) { p.onRunEnded() }
        assertFalse(p.shouldShow(20, false, true))
        p.onRunEnded()
        assertTrue(p.shouldShow(20, false, true))
    }

    @Test
    fun waitsFourMinutesAfterAnyFullScreenAd() {
        val p = ready()
        p.onFullScreenShown(interstitial = false)
        now += 3 * minute
        assertFalse(p.shouldShow(20, false, true))
        now += minute
        assertTrue(p.shouldShow(20, false, true))
    }

    @Test
    fun newPlayersAndShortSessionsSeeNone() {
        assertFalse(ready().shouldShow(totalRuns = 7, lastRunWasNewBest = false, adLoaded = true))
        val p = pacing()
        repeat(10) { p.onRunEnded() }
        now += 2 * minute
        assertFalse(p.shouldShow(20, false, true))
    }

    @Test
    fun removeAdsTurnsThemOff() {
        val p = ready()
        p.enabled = false
        assertFalse(p.shouldShow(20, false, true))
    }
}
