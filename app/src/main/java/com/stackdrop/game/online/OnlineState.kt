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

/** A public profile from `profiles/{uid}`. [friendCode] is stored without the dash. */
data class ProfileSnapshot(
    val uid: String,
    val name: String,
    val avatar: Int,
    val friendCode: String,
    val bests: Map<String, Int>,
) {
    /** "K7Q2-M9XD". */
    val displayCode: String
        get() = if (friendCode.length == 8) "${friendCode.take(4)}-${friendCode.drop(4)}" else friendCode
}

/** One leaderboard row. Equal scores share a [rank]. */
data class BoardEntry(val uid: String, val name: String, val avatar: Int, val score: Int, val rank: Int)

/**
 * A leaderboard as last seen. [myRank] and [myScore] are null when the player has no entry.
 * [fromCache] means it came from disk; [offline] means the latest refresh failed.
 */
data class BoardSnapshot(
    val id: String,
    val entries: List<BoardEntry>,
    val myRank: Int?,
    val myScore: Int?,
    val fetchedAtMs: Long,
    val fromCache: Boolean,
    val offline: Boolean,
) {
    companion object {
        fun offlineEmpty(id: String) = BoardSnapshot(id, emptyList(), null, null, 0L, fromCache = true, offline = true)
    }
}

object Boards {

    /** Board ids as written by submitScore: "tower_all" or "stack_2026-W43". */
    fun id(modeId: String, weekly: Boolean, nowMs: Long): String =
        if (weekly) "${modeId}_${IsoWeek.id(nowMs)}" else "${modeId}_all"

    /** Ranks for scores sorted high to low; equal scores share a rank (1, 2, 2, 4). */
    fun ranks(sortedScores: List<Int>): List<Int> {
        val out = ArrayList<Int>(sortedScores.size)
        for (i in sortedScores.indices) {
            out += if (i > 0 && sortedScores[i] == sortedScores[i - 1]) out[i - 1] else i + 1
        }
        return out
    }
}

data class Friend(val uid: String, val name: String, val avatar: Int, val bestStack: Int, val bestTower: Int)

data class FriendRequest(val fromUid: String, val fromName: String)

data class FriendsSnapshot(val friends: List<Friend>, val incoming: List<FriendRequest>)
