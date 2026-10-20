package com.stackdrop.game.online

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/** Friends, requests and the friends leaderboard. Every change goes through a Cloud Function. */
class FriendsRepo(private val db: FirebaseFirestore, private val functions: FirebaseFunctions) {

    suspend fun load(uid: String): FriendsSnapshot {
        val ids = db.collection("users").document(uid).collection("friends").get().await().documents.map { it.id }
        val friends = profiles(ids).map { p ->
            Friend(p.uid, p.name, p.avatar, p.bests["stack"] ?: 0, p.bests["tower"] ?: 0)
        }.sortedByDescending { it.bestTower }
        val incoming = db.collection("friendRequests").whereEqualTo("to", uid).get().await().documents.map {
            FriendRequest(it.getString("from") ?: "", it.getString("fromName") ?: "")
        }.filter { it.fromUid.isNotEmpty() }
        return FriendsSnapshot(friends, incoming)
    }

    /** One of [code] or [uid]; returns a message ready to show. */
    suspend fun sendRequest(code: String?, uid: String?): String {
        val data = if (uid != null) mapOf("uid" to uid) else mapOf("code" to (code ?: ""))
        val r = functions.getHttpsCallable("sendFriendRequest").call(data).await().data as? Map<*, *>
            ?: return GENERIC
        val name = r["name"] as? String ?: "them"
        return when (if (r["ok"] == true) r["status"] else r["reason"]) {
            "sent" -> "Request sent to $name"
            "already-sent" -> "Already asked $name"
            "friends" -> "You and $name are now friends"
            "not-found" -> "No player with that code"
            "self" -> "That's your own code"
            "already-friends" -> "You're already friends"
            "too-many-friends" -> "Friends list is full (100)"
            "too-many-requests" -> "Too many requests waiting"
            else -> GENERIC
        }
    }

    suspend fun respond(fromUid: String, accept: Boolean) {
        functions.getHttpsCallable("respondFriendRequest").call(mapOf("fromUid" to fromUid, "accept" to accept)).await()
    }

    suspend fun remove(uid: String) {
        functions.getHttpsCallable("removeFriend").call(mapOf("uid" to uid)).await()
    }

    /** The all-time board of [modeId] for the player and their friends, ranked on the device. */
    suspend fun board(modeId: String, uid: String, friendIds: List<String>, nowMs: Long): BoardSnapshot {
        val entries = db.collection("leaderboards").document("${modeId}_all").collection("entries")
        val unranked = (friendIds + uid).distinct().chunked(WHERE_IN_LIMIT).flatMap { chunk ->
            entries.whereIn(FieldPath.documentId(), chunk).get().await().documents.map { d ->
                BoardEntry(d.id, d.getString("name") ?: "", d.getLong("avatar")?.toInt() ?: 0,
                    d.getLong("score")?.toInt() ?: 0, rank = 0)
            }
        }.sortedWith(compareByDescending<BoardEntry> { it.score }.thenBy { it.name })
        val ranks = Boards.ranks(unranked.map { it.score })
        val list = unranked.mapIndexed { i, e -> e.copy(rank = ranks[i]) }
        val mine = list.firstOrNull { it.uid == uid }
        return BoardSnapshot(friendsBoardId(modeId), list, mine?.rank, mine?.score, nowMs, fromCache = false, offline = false)
    }

    private suspend fun profiles(ids: List<String>): List<ProfileSnapshot> =
        ids.chunked(WHERE_IN_LIMIT).flatMap { chunk ->
            db.collection("profiles").whereIn(FieldPath.documentId(), chunk).get().await().documents.map { d ->
                val bests = (d.get("bests") as? Map<*, *>).orEmpty()
                    .mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Number)?.let { key to it.toInt() } } }
                    .toMap()
                ProfileSnapshot(d.id, d.getString("name") ?: "", d.getLong("avatar")?.toInt() ?: 0,
                    d.getString("friendCode") ?: "", bests)
            }
        }

    companion object {
        /** Firestore allows at most 30 values in a whereIn. */
        private const val WHERE_IN_LIMIT = 30
        private const val GENERIC = "Couldn't send the request. Try again"

        fun friendsBoardId(modeId: String) = "friends_$modeId"
    }
}
