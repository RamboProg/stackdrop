package com.stackdrop.game.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.stackdrop.game.BuildConfig
import com.stackdrop.game.game.GameEvent
import com.stackdrop.game.platform.RewardPlacement
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps one rewarded and one interstitial ad preloaded and shows them on request; never waits for a
 * load. A reward is reported as RewardEarned as soon as it is earned, and AdClosed follows when the ad
 * is gone, which is when the game applies it. Loading and showing happen on the UI thread; the
 * readiness flags are safe to read from the game thread.
 */
class AdsManager(private val activity: Activity) {

    var events: (GameEvent) -> Unit = {}

    private val started = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var rewarded: RewardedAd? = null
    @Volatile private var rewardedAt = 0L
    @Volatile private var interstitial: InterstitialAd? = null
    @Volatile private var interstitialAt = 0L
    private var loadingRewarded = false
    private var loadingInterstitial = false

    val rewardedReady: Boolean get() = rewarded != null && fresh(rewardedAt)
    val interstitialReady: Boolean get() = interstitial != null && fresh(interstitialAt)

    /** Called once consent allows ads. The SDK is initialized off the UI thread. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        Thread {
            MobileAds.initialize(activity.applicationContext) {
                handler.post {
                    loadRewarded()
                    loadInterstitial()
                }
            }
        }.start()
    }

    fun showRewarded(placement: RewardPlacement) = handler.post {
        val ad = rewarded?.takeIf { fresh(rewardedAt) }
        rewarded = null
        if (ad == null) {
            events(GameEvent.AdClosed(placement))
            loadRewarded()
            return@post
        }
        ad.fullScreenContentCallback = closedCallback(placement) { loadRewarded() }
        ad.show(activity) { events(GameEvent.RewardEarned(placement)) }
    }

    fun showInterstitial() = handler.post {
        val ad = interstitial?.takeIf { fresh(interstitialAt) }
        interstitial = null
        if (ad == null) {
            events(GameEvent.AdClosed(null))
            loadInterstitial()
            return@post
        }
        ad.fullScreenContentCallback = closedCallback(null) { loadInterstitial() }
        ad.show(activity)
    }

    private fun closedCallback(placement: RewardPlacement?, reload: () -> Unit) = object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() {
            events(GameEvent.AdClosed(placement))
            reload()
        }

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            Log.w(TAG, "Ad failed to show: ${error.code} ${error.message}")
            events(GameEvent.AdClosed(placement))
            reload()
        }
    }

    private fun loadRewarded() {
        if (!started.get() || loadingRewarded || (rewarded != null && fresh(rewardedAt))) return
        loadingRewarded = true
        RewardedAd.load(activity, BuildConfig.AD_REWARDED_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loadingRewarded = false
                    rewarded = ad
                    rewardedAt = System.currentTimeMillis()
                    scheduleRefresh { loadRewarded() }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingRewarded = false
                    Log.i(TAG, "Rewarded ad not loaded: ${error.code} ${error.message}")
                    handler.postDelayed({ loadRewarded() }, RETRY_MS)
                }
            })
    }

    private fun loadInterstitial() {
        if (!started.get() || loadingInterstitial || (interstitial != null && fresh(interstitialAt))) return
        loadingInterstitial = true
        InterstitialAd.load(activity, BuildConfig.AD_INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadingInterstitial = false
                    interstitial = ad
                    interstitialAt = System.currentTimeMillis()
                    scheduleRefresh { loadInterstitial() }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingInterstitial = false
                    Log.i(TAG, "Interstitial not loaded: ${error.code} ${error.message}")
                    handler.postDelayed({ loadInterstitial() }, RETRY_MS)
                }
            })
    }

    /** Loaded ads go stale after about an hour; replace them a little before that. */
    private fun scheduleRefresh(reload: () -> Unit) = handler.postDelayed(reload, MAX_AGE_MS + 1_000L)

    private fun fresh(loadedAt: Long) = System.currentTimeMillis() - loadedAt < MAX_AGE_MS

    fun release() = handler.removeCallbacksAndMessages(null)

    private companion object {
        const val TAG = "Ads"
        const val MAX_AGE_MS = 55 * 60_000L
        const val RETRY_MS = 60_000L
    }
}
