package com.stackdrop.game.game

import com.stackdrop.game.online.AccountSnapshot
import com.stackdrop.game.online.BoardSnapshot
import com.stackdrop.game.online.FriendsSnapshot
import com.stackdrop.game.platform.RewardPlacement

/** Results from the UI thread, network or ads, queued for the game thread to apply. */
sealed class GameEvent {
    data class TextEntered(val id: String, val value: String) : GameEvent()
    data class TextCancelled(val id: String) : GameEvent()
    data class AuthChanged(val account: AccountSnapshot) : GameEvent()
    data class AuthFailed(val reason: String) : GameEvent()
    data class BoardLoaded(val board: BoardSnapshot) : GameEvent()
    data class FriendsLoaded(val friends: FriendsSnapshot) : GameEvent()
    data class Toast(val message: String) : GameEvent()
    data class RewardEarned(val placement: RewardPlacement) : GameEvent()
    data class AdClosed(val placement: RewardPlacement?) : GameEvent()
    data class RankPassed(val friendName: String, val friendScore: Int) : GameEvent()
    data object Back : GameEvent()
}
