package com.stackdrop.game.ads

/**
 * When a between-runs interstitial may appear. All rules must hold: the player is starting a new run
 * after a finished one, at least [RUNS_BETWEEN] runs and [GAP_MS] since the last full-screen ad of
 * any kind (so never soon after a rewarded ad), [MIN_TOTAL_RUNS] runs ever, a session of at least
 * [MIN_SESSION_MS], the last run was not a new best, and an ad is already loaded.
 */
class InterstitialPacing(private val clock: () -> Long) {

    /** Off for players who bought Remove Ads. */
    var enabled = true

    private val sessionStart = clock()
    private var runsSinceAd = 0
    private var lastFullScreenAt: Long? = null

    fun onRunEnded() {
        runsSinceAd++
    }

    fun onFullScreenShown(interstitial: Boolean) {
        lastFullScreenAt = clock()
        if (interstitial) runsSinceAd = 0
    }

    fun shouldShow(totalRuns: Int, lastRunWasNewBest: Boolean, adLoaded: Boolean): Boolean {
        val now = clock()
        val last = lastFullScreenAt
        return enabled &&
            adLoaded &&
            !lastRunWasNewBest &&
            runsSinceAd >= RUNS_BETWEEN &&
            totalRuns >= MIN_TOTAL_RUNS &&
            now - sessionStart >= MIN_SESSION_MS &&
            (last == null || now - last >= GAP_MS)
    }

    companion object {
        // Candidates for Remote Config later.
        const val RUNS_BETWEEN = 4
        const val GAP_MS = 4 * 60_000L
        const val MIN_TOTAL_RUNS = 8
        const val MIN_SESSION_MS = 3 * 60_000L
    }
}
