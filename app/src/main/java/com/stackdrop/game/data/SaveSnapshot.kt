package com.stackdrop.game.data

/** Everything that syncs to the cloud save, as one immutable value that is safe to pass between threads. */
data class SaveSnapshot(
    val rev: Int,
    val ledger: Map<String, GemCount>,
    val unlockedThemes: Set<String>,
    val selectedTheme: String,
    val bests: Map<String, Int>,
    val streak: Int,
    val lastDaily: Long,
    val gamesPlayed: Int,
    val settings: Settings,
) {
    data class Settings(val sound: Boolean, val vibration: Boolean, val reduceMotion: Boolean)

    val gems: Int get() = GemLedger.total(ledger)
}

/**
 * Deterministic, prompt-free merge of the local save and the cloud save. Safe to repeat: merging a
 * result again with either side changes nothing.
 *
 * Progress that can only grow (bests, unlocked themes, games played, the gem ledger) is combined.
 * The daily gift day and its streak come from whichever side claimed most recently. Selected theme
 * and settings come from the side with the higher [SaveSnapshot.rev], local on a tie.
 */
object SaveMerge {

    fun merge(local: SaveSnapshot, remote: SaveSnapshot): SaveSnapshot {
        val newer = if (remote.rev > local.rev) remote else local
        val daily = when {
            remote.lastDaily > local.lastDaily -> remote
            remote.lastDaily < local.lastDaily -> local
            else -> if (remote.streak > local.streak) remote else local
        }
        val merged = newer.copy(
            rev = maxOf(local.rev, remote.rev),
            ledger = GemLedger.merge(local.ledger, remote.ledger),
            unlockedThemes = local.unlockedThemes + remote.unlockedThemes,
            bests = (local.bests.keys + remote.bests.keys).associateWith {
                maxOf(local.bests[it] ?: 0, remote.bests[it] ?: 0)
            },
            streak = daily.streak,
            lastDaily = daily.lastDaily,
            gamesPlayed = maxOf(local.gamesPlayed, remote.gamesPlayed),
        )
        // A result the cloud has not seen yet gets a fresh rev, so every device converges on it.
        return if (merged.copy(rev = remote.rev) == remote) merged else merged.copy(rev = merged.rev + 1)
    }
}
