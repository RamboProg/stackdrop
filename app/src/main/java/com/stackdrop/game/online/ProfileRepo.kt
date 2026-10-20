package com.stackdrop.game.online

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/** Public profiles. Reads come straight from Firestore; every write goes through a Cloud Function. */
class ProfileRepo(private val db: FirebaseFirestore, private val functions: FirebaseFunctions) {

    sealed class Claim {
        data object Ok : Claim()
        data class Refused(val reason: String, val suggestions: List<String>) : Claim()
    }

    suspend fun load(uid: String): ProfileSnapshot? {
        val doc = db.collection(PROFILES).document(uid).get().await()
        return if (doc.exists()) fromDoc(uid, doc) else null
    }

    suspend fun claimName(name: String, avatar: Int): Claim {
        val result = functions.getHttpsCallable("claimName")
            .call(mapOf("name" to name, "avatar" to avatar)).await()
        val data = result.data as? Map<*, *> ?: return Claim.Refused("unknown", emptyList())
        if (data["ok"] == true) return Claim.Ok
        val suggestions = (data["suggestions"] as? List<*>).orEmpty().filterIsInstance<String>()
        return Claim.Refused(data["reason"] as? String ?: "unknown", suggestions)
    }

    suspend fun setAvatar(avatar: Int) {
        functions.getHttpsCallable("setAvatar").call(mapOf("avatar" to avatar)).await()
    }

    suspend fun deleteAccount() {
        functions.getHttpsCallable("deleteAccount").call(emptyMap<String, Any>()).await()
    }

    private fun fromDoc(uid: String, doc: DocumentSnapshot): ProfileSnapshot {
        val bests = (doc.get("bests") as? Map<*, *>).orEmpty()
            .mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Number)?.let { key to it.toInt() } } }
            .toMap()
        return ProfileSnapshot(
            uid = uid,
            name = doc.getString("name") ?: "",
            avatar = doc.getLong("avatar")?.toInt() ?: 0,
            friendCode = doc.getString("friendCode") ?: "",
            bests = bests,
        )
    }

    private companion object {
        const val PROFILES = "profiles"
    }
}
