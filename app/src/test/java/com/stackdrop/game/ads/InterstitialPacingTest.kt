package com.stackdrop.game.ads

import org.junit.Assert.assertEquals
import org.junit.Test

class InterstitialPacingTest {

    private var now = 0L
    private val minute = 60_000L
    private val low = 9
    private val good = 10

    private fun pacing() = InterstitialPacing { now }

    private fun InterstitialPacing.ended(score: Int, mode: String = "stack", skippedBest: Boolean = false) =
        onRunEnded(mode, score, skippedBest)

    private fun InterstitialPacing.pickAll(totalRuns: Int = 20) = pick(totalRuns, normalLoaded = true, highLoaded = true)

    /** Starts the next run the way the game does: shows whatever was picked, then moves past the gap. */
    private fun InterstitialPacing.nextRun(): BetweenRunsAd {
        val ad = pickAll()
        if (ad != BetweenRunsAd.NONE) onFullScreenShown(interstitial = true)
        onRunStarted()
        now += 2 * minute
        return ad
    }

    @Test
    fun nothingOwedBeforeAnyRunEnds() {
        assertEquals(BetweenRunsAd.NONE, pacing().pickAll())
    }

    @Test
    fun aFailedRunOwesAnAd() {
        val p = pacing()
        p.ended(low)
        assertEquals(BetweenRunsAd.NORMAL, p.pickAll())
    }

    @Test
    fun fiveSuccessesInARowOweAnAdAndFourDoNot() {
        val p = pacing()
        repeat(4) {
            p.ended(good)
            assertEquals(BetweenRunsAd.NONE, p.nextRun())
        }
        p.ended(good)
        assertEquals(BetweenRunsAd.NORMAL, p.nextRun())
        repeat(4) {
            p.ended(good)
            assertEquals(BetweenRunsAd.NONE, p.nextRun())
        }
        p.ended(good)
        assertEquals(BetweenRunsAd.NORMAL, p.nextRun())
    }

    @Test
    fun streaksAreCountedPerMode() {
        val p = pacing()
        repeat(4) { p.ended(good, "stack") }
        repeat(4) { p.ended(good, "tower") }
        assertEquals(BetweenRunsAd.NONE, p.pickAll())
        p.ended(good, "tower")
        assertEquals(BetweenRunsAd.NORMAL, p.pickAll())
    }

    @Test
    fun aFailureResetsOnlyItsOwnMode() {
        val p = pacing()
        repeat(4) { p.ended(good, "stack") }
        repeat(4) { p.ended(good, "tower") }
        p.ended(low, "stack")
        assertEquals(BetweenRunsAd.NORMAL, p.nextRun())

        p.ended(good, "stack")
        assertEquals(BetweenRunsAd.NONE, p.nextRun())
        p.ended(good, "tower")
        assertEquals(BetweenRunsAd.NORMAL, p.nextRun())
    }

    @Test
    fun newPlayersSeeNoneUntilEightRuns() {
        val p = pacing()
        p.ended(low)
        assertEquals(BetweenRunsAd.NONE, p.pickAll(totalRuns = 7))
        assertEquals(BetweenRunsAd.NORMAL, p.pickAll(totalRuns = 8))
    }

    @Test
    fun anOwedAdWaitsForTheSixtySecondGap() {
        val p = pacing()
        p.ended(low)
        p.onFullScreenShown(interstitial = true)
        p.onRunStarted()
        p.ended(low)
        now += minute - 1
        assertEquals(BetweenRunsAd.NONE, p.pickAll())
        now += 1
        assertEquals(BetweenRunsAd.NORMAL, p.pickAll())
    }

    @Test
    fun aRewardedAdSparesTheNextInterstitialOnly() {
        val p = pacing()
        p.ended(low)
        p.onFullScreenShown(interstitial = false)
        assertEquals(BetweenRunsAd.NONE, p.pickAll())
        p.onRunStarted()
        assertEquals(BetweenRunsAd.NORMAL, p.pickAll())
    }

    @Test
    fun removeAdsTurnsThemOff() {
        val p = pacing()
        p.enabled = false
        p.ended(low)
        assertEquals(BetweenRunsAd.NONE, p.pickAll())
    }

    @Test
    fun neverWithoutALoadedAd() {
        val p = pacing()
        p.ended(low)
        assertEquals(BetweenRunsAd.NONE, p.pick(20, normalLoaded = false, highLoaded = true))
        assertEquals(BetweenRunsAd.NORMAL, p.pick(20, normalLoaded = true, highLoaded = false))
    }

    @Test
    fun skippingOnANewBestShowsTheHighValueAdThenThreeFailedRunsAreFree() {
        val p = pacing()
        p.ended(good, skippedBest = true)
        assertEquals(BetweenRunsAd.HIGH_VALUE, p.nextRun())
        repeat(3) {
            p.ended(low)
            assertEquals(BetweenRunsAd.NONE, p.nextRun())
        }
        p.ended(low)
        assertEquals(BetweenRunsAd.NORMAL, p.nextRun())
    }

    @Test
    fun theHighValueAdFallsBackToTheNormalOne() {
        val p = pacing()
        p.ended(good, skippedBest = true)
        assertEquals(BetweenRunsAd.NORMAL, p.pick(20, normalLoaded = true, highLoaded = false))
    }

    @Test
    fun graceStartsEvenWhenTheHighValueAdCannotShow() {
        val p = pacing()
        p.ended(good, skippedBest = true)
        p.onFullScreenShown(interstitial = false)
        assertEquals(BetweenRunsAd.NONE, p.nextRun())
        repeat(3) {
            p.ended(low)
            assertEquals(BetweenRunsAd.NONE, p.nextRun())
        }
        p.ended(low)
        assertEquals(BetweenRunsAd.NORMAL, p.nextRun())
    }

    @Test
    fun graceClearsAnAdThatWasStillOwed() {
        val p = pacing()
        p.ended(low)
        p.onFullScreenShown(interstitial = false)
        p.ended(good, skippedBest = true)
        assertEquals(BetweenRunsAd.NONE, p.nextRun())
        assertEquals(BetweenRunsAd.NONE, p.nextRun())
    }

    @Test
    fun successfulRunsDoNotUseUpGrace() {
        val p = pacing()
        p.ended(good, skippedBest = true)
        p.nextRun()
        repeat(2) {
            p.ended(good)
            p.nextRun()
        }
        repeat(3) {
            p.ended(low)
            assertEquals(BetweenRunsAd.NONE, p.nextRun())
        }
        p.ended(low)
        assertEquals(BetweenRunsAd.NORMAL, p.nextRun())
    }

    @Test
    fun theStreakAdStillShowsDuringGrace() {
        val p = pacing()
        // The skipped run scored well too, so it is the first of the five.
        p.ended(good, skippedBest = true)
        p.nextRun()
        repeat(3) {
            p.ended(good)
            assertEquals(BetweenRunsAd.NONE, p.nextRun())
        }
        p.ended(good)
        assertEquals(BetweenRunsAd.NORMAL, p.nextRun())
    }
}
