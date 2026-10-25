package com.stackdrop.game.online

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.stackdrop.game.data.GemCount
import com.stackdrop.game.data.GemLedger
import com.stackdrop.game.data.SaveMerge
import com.stackdrop.game.data.SaveSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/** A save to write to [uid]'s cloud copy. The uid is fixed when the save is taken, never at write time. */
data class CloudPush(val uid: String, val save: SaveSnapshot)

/**
 * Best-effort copy of the save in `users/{uid}`. Every write merges with the cloud copy inside a
 * transaction (see [SaveMerge]), so a device that is behind can never overwrite newer progress.
 */
class CloudSave(
    private val db: FirebaseFirestore,
    private val auth: AuthRepo,
    private val scope: CoroutineScope,
    private val onPushed: (uid: String, written: SaveSnapshot) -> Unit,
) {

    private val pending = AtomicReference<CloudPush?>(null)
    private var debounce: Job? = null
    private val writing = Mutex()

    /** Accounts being deleted or left behind; nothing more is written for them. */
    private val forgotten = ConcurrentHashMap.newKeySet<String>()

    /** The cloud copy, or null when the player has none yet. Throws when the server can't be reached. */
    suspend fun pull(uid: String): SaveSnapshot? {
        // From the server only: a cached copy would look like a successful sync while offline.
        val doc = db.collection(USERS).document(uid).get(Source.SERVER).await()
        return if (doc.exists()) fromDoc(doc) else null
    }

    /** Pushes [p] after a short quiet period, so a burst of changes becomes one write. */
    fun queuePush(p: CloudPush) {
        pending.set(p)
        synchronized(this) {
            debounce?.cancel()
            debounce = scope.launch {
                delay(DEBOUNCE_MS)
                flush()
            }
        }
    }

    fun pushNow(p: CloudPush) {
        pending.set(p)
        synchronized(this) {
            debounce?.cancel()
            debounce = scope.launch { flush() }
        }
    }

    /** Writes [p] right away and returns the merged result. Throws when it can't be written. */
    suspend fun write(p: CloudPush): SaveSnapshot = writing.withLock {
        check(auth.uid == p.uid && p.uid !in forgotten) { "Save belongs to an account that is no longer signed in" }
        val ref = db.collection(USERS).document(p.uid)
        val written = db.runTransaction { tx ->
            val doc = tx.get(ref)
            val remote = if (doc.exists()) fromDoc(doc) else null
            val merged = remote?.let { SaveMerge.merge(p.save, it) } ?: p.save
            if (merged != remote) tx.set(ref, toMap(merged))
            merged
        }.await()
        onPushed(p.uid, written)
        written
    }

    /** Stops all writes for [uid], including any queued one. */
    fun forget(uid: String) {
        forgotten.add(uid)
        synchronized(this) { debounce?.cancel() }
        pending.updateAndGet { if (it?.uid == uid) null else it }
    }

    /** Undoes [forget] when deleting the account failed and it stays in use. */
    fun remember(uid: String) {
        forgotten.remove(uid)
    }

    private suspend fun flush() {
        val p = pending.getAndSet(null) ?: return
        // Signed in as someone else by now: that save is not theirs. The merge after sign-in
        // pushes whatever the new account needs.
        if (auth.uid != p.uid || p.uid in forgotten) return
        try {
            write(p)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep it (unless something newer arrived) so the next push retries it.
            Log.w(TAG, "Cloud save push failed", e)
            pending.compareAndSet(null, p)
        }
    }

    private fun toMap(s: SaveSnapshot): Map<String, Any> = mapOf(
        "rev" to s.rev,
        "gems" to s.gems,
        "ledger" to s.ledger.mapValues { (_, c) -> mapOf("e" to c.earned, "s" to c.spent) },
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
            ledger = ledgerOf(doc),
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

    // Saves written before the ledger only have a balance; it becomes the shared legacy entry.
    private fun ledgerOf(doc: DocumentSnapshot): Map<String, GemCount> {
        val raw = doc.get("ledger") as? Map<*, *> ?: return mapOf(GemLedger.LEGACY to GemCount(doc.int("gems"), 0))
        return raw.mapNotNull { (k, v) ->
            val id = k as? String ?: return@mapNotNull null
            val c = v as? Map<*, *> ?: return@mapNotNull null
            id to GemCount((c["e"] as? Number)?.toInt() ?: 0, (c["s"] as? Number)?.toInt() ?: 0)
        }.toMap()
    }

    private fun DocumentSnapshot.int(field: String) = getLong(field)?.toInt() ?: 0

    private companion object {
        const val TAG = "CloudSave"
        const val USERS = "users"
        const val DEBOUNCE_MS = 3_000L
    }
}
