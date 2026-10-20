package com.stackdrop.game.data

/** Everything that syncs to the cloud save, as one immutable value that is safe to pass between threads. */
data class SaveSnapshot(
    val rev: Int,
    val gems: Int,
    val unlockedThemes: Set<String>,
    val selectedTheme: String,
    val bests: Map<String, Int>,
    val streak: Int,
    val lastDaily: Long,
    val gamesPlayed: Int,
    val settings: Settings,
) {
    data class Settings(val sound: Boolean, val vibration: Boolean, val reduceMotion: Boolean)
}

/**
 * Deterministic, prompt-free merge of the local save and the cloud save.
 *
 * Progress that can only grow (bests, unlocked themes, games played) is combined. Spendable or
 * day-based values (gems, streak, daily gift day, selected theme, settings) come from whichever side
 * changed more recently by [SaveSnapshot.rev], local on a tie, so gems are never added twice.
 */
object SaveMerge {

    fun merge(local: SaveSnapshot, remote: SaveSnapshot): SaveSnapshot {
        val newer = if (remote.rev > local.rev) remote else local
        val merged = newer.copy(
            rev = maxOf(local.rev, remote.rev),
            unlockedThemes = local.unlockedThemes + remote.unlockedThemes,
            bests = (local.bests.keys + remote.bests.keys).associateWith {
                maxOf(local.bests[it] ?: 0, remote.bests[it] ?: 0)
            },
            gamesPlayed = maxOf(local.gamesPlayed, remote.gamesPlayed),
        )
        // A result the cloud has not seen yet gets a fresh rev, so every device converges on it.
        return if (merged.copy(rev = remote.rev) == remote) merged else merged.copy(rev = merged.rev + 1)
    }
}
