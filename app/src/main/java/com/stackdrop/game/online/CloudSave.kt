package com.stackdrop.game.online

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.stackdrop.game.data.SaveSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicReference

/**
 * Best-effort copy of the save in `users/{uid}`. Merging happens on the game thread (see
 * [com.stackdrop.game.data.SaveMerge]); this class only reads and writes documents.
 */
class CloudSave(
    private val db: FirebaseFirestore,
    private val auth: AuthRepo,
    private val scope: CoroutineScope,
    private val onPushed: (rev: Int) -> Unit,
) {

    private val pending = AtomicReference<SaveSnapshot?>(null)
    private var debounce: Job? = null

    /** The cloud copy, or null when the player has none yet. Throws when it cannot be read. */
    suspend fun pull(uid: String): SaveSnapshot? {
        val doc = db.collection(USERS).document(uid).get().await()
        return if (doc.exists()) fromDoc(doc) else null
    }

    /** Pushes [s] after a short quiet period, so a burst of changes becomes one write. */
    fun queuePush(s: SaveSnapshot) {
        pending.set(s)
        synchronized(this) {
            debounce?.cancel()
            debounce = scope.launch {
                delay(DEBOUNCE_MS)
                flush()
            }
        }
    }

    fun pushNow(s: SaveSnapshot) {
        pending.set(s)
        synchronized(this) {
            debounce?.cancel()
            debounce = scope.launch { flush() }
        }
    }

    private suspend fun flush() {
        val s = pending.getAndSet(null) ?: return
        val uid = auth.uid
        if (uid == null) {
            // Not signed in yet; the merge after sign-in pushes the latest save anyway.
            pending.compareAndSet(null, s)
            return
        }
        try {
            db.collection(USERS).document(uid).set(toMap(s)).await()
            onPushed(s.rev)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Firestore keeps the write queued offline; keep ours too so the next push retries it.
            Log.w(TAG, "Cloud save push failed", e)
            pending.compareAndSet(null, s)
        }
    }

    private fun toMap(s: SaveSnapshot): Map<String, Any> = mapOf(
        "rev" to s.rev,
        "gems" to s.gems,
        "unlockedThemes" to s.unlockedThemes.sorted(),
        "selectedTheme" to s.selectedTheme,
        "bests" to s.bests,
        "streak" to s.streak,
        "lastDaily" to s.lastDaily,
        "gamesPlayed" to s.gamesPlayed,
        "settings" to mapOf(
            "sound" to s.settings.sound,
            "vibration" to s.settings.vibration,
            "reduceMotion" to s.settings.reduceMotion,
        ),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    private fun fromDoc(doc: DocumentSnapshot): SaveSnapshot {
        val bests = (doc.get("bests") as? Map<*, *>).orEmpty()
            .mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Number)?.let { key to it.toInt() } } }
            .toMap()
        val themes = (doc.get("unlockedThemes") as? List<*>).orEmpty().filterIsInstance<String>().toSet()
        return SaveSnapshot(
            rev = doc.int("rev"),
            gems = doc.int("gems"),
            unlockedThemes = themes,
            selectedTheme = doc.getString("selectedTheme") ?: "",
            bests = bests,
            streak = doc.int("streak"),
            lastDaily = doc.getLong("lastDaily") ?: -1L,
            gamesPlayed = doc.int("gamesPlayed"),
            settings = SaveSnapshot.Settings(
                sound = doc.getBoolean("settings.sound") ?: true,
                vibration = doc.getBoolean("settings.vibration") ?: true,
                reduceMotion = doc.getBoolean("settings.reduceMotion") ?: false,
            ),
        )
    }

    private fun DocumentSnapshot.int(field: String) = getLong(field)?.toInt() ?: 0

    private companion object {
        const val TAG = "CloudSave"
        const val USERS = "users"
        const val DEBOUNCE_MS = 3_000L
    }
}
