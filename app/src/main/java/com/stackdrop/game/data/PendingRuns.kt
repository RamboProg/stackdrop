package com.stackdrop.game.data

import com.stackdrop.game.game.RunStats

/** A finished run waiting to be sent to the leaderboards. [runId] lets the server ignore repeats. */
data class PendingRun(val modeId: String, val runId: String, val stats: RunStats)

/**
 * The offline leaderboard queue: at most [MAX] runs, keeping only the highest run per mode, since a
 * lower run could never change a board. Stored as a compact string so it fits in SharedPreferences.
 */
object PendingRuns {

    const val MAX = 5

    fun add(queue: List<PendingRun>, run: PendingRun): List<PendingRun> =
        (queue + run)
            .groupBy { it.modeId }
            // On a tie keep the newest run, so the run that just ended is the one reported back.
            .map { (_, runs) -> runs.reversed().maxBy { it.stats.score } }
            .sortedByDescending { it.stats.score }
            .take(MAX)

    fun encode(queue: List<PendingRun>): String = queue.joinToString(RUN_SEP) { r ->
        val s = r.stats
        listOf(r.modeId, r.runId, s.score, s.durationMs, s.drops, s.perfects, s.bestCombo, s.revived)
            .joinToString(FIELD_SEP)
    }

    /** Unreadable entries are dropped rather than failing the whole queue. */
    fun decode(text: String): List<PendingRun> {
        if (text.isBlank()) return emptyList()
        return text.split(RUN_SEP).mapNotNull { line ->
            val f = line.split(FIELD_SEP)
            if (f.size != 8) return@mapNotNull null
            val stats = RunStats(
                score = f[2].toIntOrNull() ?: return@mapNotNull null,
                durationMs = f[3].toLongOrNull() ?: return@mapNotNull null,
                drops = f[4].toIntOrNull() ?: return@mapNotNull null,
                perfects = f[5].toIntOrNull() ?: return@mapNotNull null,
                bestCombo = f[6].toIntOrNull() ?: return@mapNotNull null,
                revived = f[7].toBooleanStrictOrNull() ?: return@mapNotNull null,
            )
            PendingRun(f[0], f[1], stats)
        }
    }

    private const val RUN_SEP = ";"
    private const val FIELD_SEP = "|"
}
