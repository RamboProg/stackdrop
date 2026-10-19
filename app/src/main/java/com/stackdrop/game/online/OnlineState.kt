package com.stackdrop.game.online

// Immutable snapshots, safe to hand from network callbacks to the game thread.

data class AccountSnapshot(
    val uid: String?,
    val anonymous: Boolean,
    val email: String?,
) {
    companion object {
        val SIGNED_OUT = AccountSnapshot(uid = null, anonymous = true, email = null)
    }
}

data class BoardEntry(val uid: String, val name: String, val avatar: Int, val score: Int)

data class BoardSnapshot(
    val id: String,
    val entries: List<BoardEntry>,
    val myRank: Int?,
    val fetchedAtMs: Long,
    val fromCache: Boolean,
)

data class Friend(val uid: String, val name: String, val avatar: Int, val bestStack: Int, val bestTower: Int)

data class FriendRequest(val fromUid: String, val fromName: String)

data class FriendsSnapshot(val friends: List<Friend>, val incoming: List<FriendRequest>)
