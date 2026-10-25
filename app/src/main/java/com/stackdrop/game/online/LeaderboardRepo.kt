package com.stackdrop.game.online

import android.util.Log
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.stackdrop.game.data.PendingRun
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Reads boards (with a 60 s memory cache and a disk cache) and submits runs through submitScore. */
class LeaderboardRepo(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val cacheDir: File,
) {

    sealed class Submit {
        data class Accepted(val newAllTime: Boolean, val newWeekly: Boolean, val weeklyRank: Int?) : Submit()

        /** The server will never accept this run; drop it from the queue. */
        data class Rejected(val reason: String) : Submit()

        /** A temporary problem (offline, too soon, no profile yet); keep the run and try later. */
        data object Retry : Submit()
    }

    private val memory = ConcurrentHashMap<String, BoardSnapshot>()

    /** A board fetched less than a minute ago, if any. */
    fun fresh(id: String, nowMs: Long): BoardSnapshot? =
        memory[id]?.takeIf { !it.fromCache && nowMs - it.fetchedAtMs < FRESH_MS }

    /** The last copy saved on this device, shown while a fresh one loads. */
    fun cached(id: String): BoardSnapshot? = memory[id]?.copy(fromCache = true) ?: readDisk(id)

    fun invalidate(modeId: String) {
        memory.keys.removeAll { it.startsWith("${modeId}_") }
    }

    suspend fun fetch(id: String, uid: String?, nowMs: Long): BoardSnapshot {
        val entries = db.collection(BOARDS).document(id).collection(ENTRIES)
        val top = entries.orderBy("score", Query.Direction.DESCENDING)
            .orderBy("updatedAt", Query.Direction.ASCENDING)
            .limit(TOP)
            .get().await()
        val docs = top.documents
        val ranks = Boards.ranks(docs.map { it.getLong("score")?.toInt() ?: 0 })
        val list = docs.mapIndexed { i, d ->
            BoardEntry(
                uid = d.id,
                name = d.getString("name") ?: "",
                avatar = d.getLong("avatar")?.toInt() ?: 0,
                score = d.getLong("score")?.toInt() ?: 0,
                rank = ranks[i],
            )
        }

        var myScore: Int? = null
        var myRank: Int? = null
        if (uid != null) {
            val listed = list.firstOrNull { it.uid == uid }
            if (listed != null) {
                myScore = listed.score
                myRank = listed.rank
            } else {
                myScore = entries.document(uid).get().await().getLong("score")?.toInt()
                if (myScore != null) {
                    myRank = entries.whereGreaterThan("score", myScore).count()
                        .get(AggregateSource.SERVER).await().count.toInt() + 1
                }
            }
        }
        val board = BoardSnapshot(id, list, myRank, myScore, nowMs, fromCache = false, offline = false)
        memory[id] = board
        writeDisk(board)
        return board
    }

    /** Tells the server a run began, on its clock; only runs started this way can be ranked. */
    suspend fun startRun(runId: String, modeId: String) {
        functions.getHttpsCallable("startRun").call(mapOf("runId" to runId, "mode" to modeId)).await()
    }

    suspend fun submit(run: PendingRun): Submit {
        val s = run.stats
        val data = mapOf(
            "mode" to run.modeId, "runId" to run.runId, "score" to s.score, "durationMs" to s.durationMs,
            "drops" to s.drops, "perfects" to s.perfects, "bestCombo" to s.bestCombo, "revived" to s.revived,
        )
        return try {
            val result = functions.getHttpsCallable("submitScore").call(data).await().data as? Map<*, *>
                ?: return Submit.Retry
            invalidate(run.modeId)
            Submit.Accepted(
                newAllTime = result["newAllTime"] == true,
                newWeekly = result["newWeekly"] == true,
                weeklyRank = (result["weeklyRank"] as? Number)?.toInt(),
            )
        } catch (e: FirebaseFunctionsException) {
            if (e.code == FirebaseFunctionsException.Code.INVALID_ARGUMENT) {
                Log.w(TAG, "Run ${run.runId} rejected: ${e.message}")
                Submit.Rejected(e.message ?: "invalid")
            } else {
                Log.i(TAG, "Run ${run.runId} not sent yet (${e.code})")
                Submit.Retry
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i(TAG, "Run ${run.runId} not sent yet", e)
            Submit.Retry
        }
    }

    private fun file(id: String) = File(cacheDir, "$id.txt")

    private fun readDisk(id: String): BoardSnapshot? = try {
        file(id).takeIf { it.exists() }?.readText()?.let(BoardCodec::decode)
    } catch (e: IOException) {
        Log.w(TAG, "Could not read cached board $id", e)
        null
    }

    private fun writeDisk(board: BoardSnapshot) {
        try {
            cacheDir.mkdirs()
            file(board.id).writeText(BoardCodec.encode(board))
        } catch (e: IOException) {
            Log.w(TAG, "Could not cache board ${board.id}", e)
        }
    }

    private companion object {
        const val TAG = "Leaderboards"
        const val BOARDS = "leaderboards"
        const val ENTRIES = "entries"
        const val TOP = 50L
        const val FRESH_MS = 60_000L
    }
}
