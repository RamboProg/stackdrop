package com.stackdrop.game.platform

/**
 * Activity-only work the game asks for. Every method may be called from the game thread;
 * results come back as [com.stackdrop.game.game.GameEvent]s.
 */
interface PlatformBridge {
    fun signInWithGoogle()
    fun signOut()
    fun deleteAccount()
    fun promptText(request: TextPrompt)
    fun share(text: String)
    fun openUrl(url: String)
    fun showRewarded(placement: RewardPlacement)
    fun maybeShowInterstitial()
    fun showPrivacyOptions()
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

enum class RewardPlacement { DOUBLE_GEMS, SECOND_CHANCE, FREE_GEMS }
