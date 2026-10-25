package com.stackdrop.game.platform

/**
 * Activity-only work the game asks for. Every method may be called from the game thread;
 * results come back as [com.stackdrop.game.game.GameEvent]s.
 */
interface PlatformBridge {
    fun signInWithGoogle()
    fun deleteAccount()
    fun promptText(request: TextPrompt)

    /** A yes/no dialog; a yes comes back as GameEvent.Confirmed with the same id. */
    fun confirm(request: ConfirmPrompt)
    fun share(text: String)
    fun copyToClipboard(text: String)
    fun openUrl(url: String)
    fun showRewarded(placement: RewardPlacement)

    /** Shows the preloaded interstitial; the game has already applied the pacing rules. */
    fun maybeShowInterstitial()
    fun showPrivacyOptions()

    /** Mutes ad audio when the game's sound is off. */
    fun setAdsMuted(muted: Boolean)

    /** Opens Google Play's purchase sheet for premium; the result arrives as PremiumChanged. */
    fun buyPremium()

    /** Safe to call every frame from the game thread. */
    fun rewardedReady(): Boolean
    fun interstitialReady(): Boolean
    fun privacyOptionsRequired(): Boolean
    fun exitToHome()
}

/** [allowed] filters typed characters one at a time; null accepts anything. */
data class TextPrompt(
    val id: String,
    val title: String,
    val hint: String,
    val initial: String,
    val maxLength: Int,
    val allowed: Regex? = null,
)

data class ConfirmPrompt(val id: String, val title: String, val message: String, val confirmLabel: String)

enum class RewardPlacement { DOUBLE_GEMS, SECOND_CHANCE, FREE_GEMS }
