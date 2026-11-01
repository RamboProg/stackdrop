package com.stackdrop.game.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
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
 * Shows an adaptive banner in the strip below the game, and keeps rewarded and interstitial ads
 * preloaded to show on request, each from a normal and a high-floor ad unit; never waits for a load.
 * A reward is reported as RewardEarned as soon as it is earned, and AdClosed follows when the ad is
 * gone, which is when the game applies it. Loading and showing happen on the UI thread; the
 * readiness flags are safe to read from the game thread.
 */
class AdsManager(private val activity: Activity) {

    var events: (GameEvent) -> Unit = {}

    private val started = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())

    /** Set when the activity is destroyed; late load callbacks then drop their ad and stop retrying. */
    @Volatile private var released = false
    private val appContext = activity.applicationContext

    private val rewarded = rewardedSlot("Rewarded ad", BuildConfig.AD_REWARDED_ID, RETRY_MS)
    private val highRewarded = rewardedSlot("High value rewarded ad", BuildConfig.AD_REWARDED_HIGH_ID, HIGH_RETRY_MS)
    private val interstitial = interstitialSlot("Interstitial", BuildConfig.AD_INTERSTITIAL_ID, RETRY_MS)
    private val highInterstitial =
        interstitialSlot("High value interstitial", BuildConfig.AD_INTERSTITIAL_HIGH_ID, HIGH_RETRY_MS)

    val rewardedReady: Boolean get() = rewarded.ready
    val highRewardedReady: Boolean get() = highRewarded.ready
    val interstitialReady: Boolean get() = interstitial.ready
    val highInterstitialReady: Boolean get() = highInterstitial.ready

    private var bannerSlot: FrameLayout? = null
    private var banner: AdView? = null
    private var sdkReady = false

    /** Premium players get no banner and no interstitials; rewarded ads stay available as an option. */
    @Volatile
    var premium = false
        set(value) {
            field = value
            handler.post {
                if (value) {
                    removeBanner()
                    interstitial.clear()
                    highInterstitial.clear()
                } else {
                    loadBanner()
                    interstitial.load()
                    highInterstitial.load()
                }
            }
        }

    /** Ad sound follows the game: none when the player turned sound off. */
    @Volatile
    var muted = false
        set(value) {
            field = value
            handler.post { if (sdkReady) MobileAds.setAppMuted(value) }
        }

    /** Called once consent allows ads. The SDK is initialized off the UI thread. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        Thread {
            MobileAds.initialize(appContext) {
                handler.post {
                    if (released) return@post
                    sdkReady = true
                    MobileAds.setAppMuted(muted)
                    rewarded.load()
                    highRewarded.load()
                    interstitial.load()
                    highInterstitial.load()
                    loadBanner()
                }
            }
        }.start()
    }

    /**
     * The strip at the bottom of the screen the banner lives in. Its height is reserved up front so
     * the game area never jumps when the banner arrives.
     */
    fun attachBannerSlot(slot: FrameLayout) {
        bannerSlot = slot
        slot.layoutParams.height = bannerSize().getHeightInPixels(activity)
        slot.visibility = if (premium) View.GONE else View.VISIBLE
    }

    fun pause() = banner?.pause()

    fun resume() = banner?.resume()

    private fun bannerSize(): AdSize {
        val metrics = activity.resources.displayMetrics
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, (metrics.widthPixels / metrics.density).toInt())
    }

    private fun loadBanner() {
        val slot = bannerSlot ?: return
        if (released || !sdkReady || premium || banner != null) return
        slot.visibility = View.VISIBLE
        val view = AdView(activity).apply {
            setAdSize(bannerSize())
            adUnitId = BuildConfig.AD_BANNER_ID
            adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    // The AdView retries on its own refresh cycle; the reserved strip just stays empty.
                    Log.i(TAG, "Banner not loaded: ${error.code} ${error.message}")
                }
            }
        }
        slot.addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER,
        ))
        banner = view
        view.loadAd(AdRequest.Builder().build())
    }

    private fun removeBanner() {
        banner?.destroy()
        banner = null
        bannerSlot?.let {
            it.removeAllViews()
            it.visibility = View.GONE
        }
    }

    fun showRewarded(placement: RewardPlacement, highValue: Boolean) = handler.post {
        val slot = if (highValue) highRewarded else rewarded
        val ad = slot.take()
        if (ad == null) {
            events(GameEvent.AdClosed(placement))
            slot.load()
            return@post
        }
        Log.i(TAG, "Showing: ${slot.label}")
        ad.fullScreenContentCallback = fullScreenCallback(placement) { slot.load() }
        ad.show(activity) { events(GameEvent.RewardEarned(placement)) }
    }

    fun showInterstitial(highValue: Boolean) = handler.post {
        val slot = if (highValue) highInterstitial else interstitial
        val ad = slot.take()
        if (ad == null) {
            events(GameEvent.AdClosed(null))
            slot.load()
            return@post
        }
        Log.i(TAG, "Showing: ${slot.label}")
        ad.fullScreenContentCallback = fullScreenCallback(null) { slot.load() }
        ad.show(activity)
    }

    private fun fullScreenCallback(placement: RewardPlacement?, reload: () -> Unit) = object : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() {
            events(GameEvent.AdShown(placement))
        }

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

    private fun rewardedSlot(label: String, unitId: String, retryMs: Long) =
        AdSlot<RewardedAd>(label, retryMs, allowed = { true }) { onLoaded, onFailed ->
            RewardedAd.load(appContext, unitId, AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) = onLoaded(ad)
                    override fun onAdFailedToLoad(error: LoadAdError) = onFailed(error)
                })
        }

    // Premium players never see interstitials, so none is loaded for them.
    private fun interstitialSlot(label: String, unitId: String, retryMs: Long) =
        AdSlot<InterstitialAd>(label, retryMs, allowed = { !premium }) { onLoaded, onFailed ->
            InterstitialAd.load(appContext, unitId, AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) = onLoaded(ad)
                    override fun onAdFailedToLoad(error: LoadAdError) = onFailed(error)
                })
        }

    /** One preloaded full-screen ad: replaced before it goes stale, retried after a failed load. */
    private inner class AdSlot<T : Any>(
        val label: String,
        private val retryMs: Long,
        private val allowed: () -> Boolean,
        private val request: (onLoaded: (T) -> Unit, onFailed: (LoadAdError) -> Unit) -> Unit,
    ) {
        @Volatile private var ad: T? = null
        @Volatile private var loadedAt = 0L
        private var loading = false

        val ready: Boolean get() = ad != null && fresh(loadedAt)

        /** Hands over the ad if it is still usable; the slot is empty afterwards either way. */
        fun take(): T? {
            val taken = ad?.takeIf { fresh(loadedAt) }
            ad = null
            return taken
        }

        fun clear() {
            ad = null
        }

        fun load() {
            if (released || !allowed() || !started.get() || loading || ready) return
            loading = true
            request(::onLoaded, ::onFailed)
        }

        private fun onLoaded(loaded: T) {
            loading = false
            if (released) return
            ad = loaded
            loadedAt = System.currentTimeMillis()
            scheduleRefresh { load() }
        }

        private fun onFailed(error: LoadAdError) {
            loading = false
            Log.i(TAG, "$label not loaded: ${error.code} ${error.message}")
            if (!released) handler.postDelayed({ load() }, retryMs)
        }
    }

    /** Loaded ads go stale after about an hour; replace them a little before that. */
    private fun scheduleRefresh(reload: () -> Unit) = handler.postDelayed(reload, MAX_AGE_MS + 1_000L)

    private fun fresh(loadedAt: Long) = System.currentTimeMillis() - loadedAt < MAX_AGE_MS

    fun release() {
        released = true
        rewarded.clear()
        highRewarded.clear()
        interstitial.clear()
        highInterstitial.clear()
        handler.removeCallbacksAndMessages(null)
        banner?.destroy()
        banner = null
    }

    private companion object {
        const val TAG = "Ads"
        const val MAX_AGE_MS = 55 * 60_000L
        const val RETRY_MS = 60_000L

        // High-floor units often have no ad at that price, so they are asked less often.
        const val HIGH_RETRY_MS = 5 * 60_000L
    }
}
