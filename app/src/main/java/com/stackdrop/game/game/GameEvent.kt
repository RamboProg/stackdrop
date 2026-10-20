package com.stackdrop.game.game

import com.stackdrop.game.data.SaveSnapshot
import com.stackdrop.game.online.AccountSnapshot
import com.stackdrop.game.online.BoardSnapshot
import com.stackdrop.game.online.FriendsSnapshot
import com.stackdrop.game.online.ProfileSnapshot
import com.stackdrop.game.platform.RewardPlacement

/** Results from the UI thread, network or ads, queued for the game thread to apply. */
sealed class GameEvent {
    data class TextEntered(val id: String, val value: String) : GameEvent()
    data class TextCancelled(val id: String) : GameEvent()
    data class Confirmed(val id: String) : GameEvent()

    /** Another player's public profile, for the leaderboard popover. */
    data class PlayerLoaded(val profile: ProfileSnapshot) : GameEvent()
    data class AuthChanged(val account: AccountSnapshot) : GameEvent()
    data class AuthFailed(val reason: String) : GameEvent()

    /** The cloud save was read; null when this account has none yet. */
    data class CloudLoaded(val remote: SaveSnapshot?) : GameEvent()

    /** The cloud confirmed the save at [rev]. */
    data class CloudPushed(val rev: Int) : GameEvent()

    /** The signed-in player's public profile; null when they have not chosen a name yet. */
    data class ProfileLoaded(val profile: ProfileSnapshot?) : GameEvent()

    /** A name change was refused; [message] is ready to show. */
    data class NameRejected(val message: String) : GameEvent()

    /** The account and its cloud data are gone; the local save must be reset. */
    data object AccountDeleted : GameEvent()

    /** The server took the run; [weeklyRank] is the player's place on this week's board. */
    data class RunSubmitted(
        val runId: String,
        val newAllTime: Boolean,
        val newWeekly: Boolean,
        val weeklyRank: Int?,
    ) : GameEvent()

    /** The server refused the run for good; it leaves the queue. */
    data class RunDropped(val runId: String) : GameEvent()
    data class BoardLoaded(val board: BoardSnapshot) : GameEvent()
    data class FriendsLoaded(val friends: FriendsSnapshot) : GameEvent()
    data class Toast(val message: String) : GameEvent()
    data class RewardEarned(val placement: RewardPlacement) : GameEvent()
    data class AdClosed(val placement: RewardPlacement?) : GameEvent()
    data class RankPassed(val friendName: String, val friendScore: Int) : GameEvent()

    /** Google Play says whether this player owns premium. */
    data class PremiumChanged(val owned: Boolean) : GameEvent()

    /** Localized premium price, or null when Play cannot sell it right now. */
    data class PremiumPrice(val price: String?) : GameEvent()
    data object Back : GameEvent()
}
