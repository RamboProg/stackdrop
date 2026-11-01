package com.stackdrop.game.ads

/** The interstitial to show in front of the next run, if any. */
enum class BetweenRunsAd { NONE, NORMAL, HIGH_VALUE }

/**
 * Which between-runs interstitial the player owes, and whether it may appear yet. A run below
 * [SUCCESS_SCORE] owes one, and so does every [SUCCESSES_BETWEEN]th run in a row at or above it,
 * counted per mode. Skipping the second chance on a fresh new best owes a high value one and then
 * spares the next [GRACE_RUNS] runs below [SUCCESS_SCORE].
 *
 * An owed ad shows when the player starts a run, once there are [MIN_TOTAL_RUNS] runs ever, [GAP_MS]
 * since the last interstitial, no rewarded ad since the last run started, and an ad already loaded.
 */
class InterstitialPacing(private val clock: () -> Long) {

    /** Off for players who bought Remove Ads. */
    var enabled = true

    private val successStreaks = HashMap<String, Int>()
    private var adOwed = false
    private var highValueOwed = false
    private var graceLeft = 0
    private var rewardedSinceRunStart = false
    private var lastInterstitialAt: Long? = null

    fun onRunStarted() {
        rewardedSinceRunStart = false
    }

    fun onRunEnded(modeId: String, score: Int, skippedBestRevive: Boolean) {
        if (score >= SUCCESS_SCORE) {
            val streak = (successStreaks[modeId] ?: 0) + 1
            if (streak >= SUCCESSES_BETWEEN) adOwed = true
            successStreaks[modeId] = streak % SUCCESSES_BETWEEN
        } else {
            successStreaks[modeId] = 0
            if (graceLeft > 0) graceLeft-- else adOwed = true
        }
        if (skippedBestRevive) highValueOwed = true
    }

    fun onFullScreenShown(interstitial: Boolean) {
        if (interstitial) {
            lastInterstitialAt = clock()
            adOwed = false
        } else {
            rewardedSinceRunStart = true
        }
    }

    /** Called as a run is about to start. A high value ad is offered once; grace begins either way. */
    fun pick(totalRuns: Int, normalLoaded: Boolean, highLoaded: Boolean): BetweenRunsAd {
        val highValue = highValueOwed
        val owed = adOwed || highValue
        if (highValue) {
            highValueOwed = false
            adOwed = false
            graceLeft = GRACE_RUNS
        }
        val last = lastInterstitialAt
        val allowed = enabled &&
            owed &&
            !rewardedSinceRunStart &&
            totalRuns >= MIN_TOTAL_RUNS &&
            (last == null || clock() - last >= GAP_MS)
        return when {
            !allowed -> BetweenRunsAd.NONE
            highValue && highLoaded -> BetweenRunsAd.HIGH_VALUE
            normalLoaded -> BetweenRunsAd.NORMAL
            else -> BetweenRunsAd.NONE
        }
    }

    companion object {
        // Candidates for Remote Config later.
        const val SUCCESS_SCORE = 10
        const val SUCCESSES_BETWEEN = 5
        const val GRACE_RUNS = 3
        const val GAP_MS = 60_000L
        const val MIN_TOTAL_RUNS = 8
    }
}
