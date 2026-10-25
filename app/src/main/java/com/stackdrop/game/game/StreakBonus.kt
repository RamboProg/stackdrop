package com.stackdrop.game.game

import kotlin.math.max

/**
 * Every [LENGTH] perfect drops in a row earn a streak: [STACKS] free pieces fall onto the top one
 * after another, each a little quicker than the last, and the run earns [GEMS] extra gems. Play waits
 * while they fall. This class only keeps time and counts; the mode places the pieces and plays the
 * effects.
 */
class StreakBonus {

    /** Streaks finished this run. */
    var count = 0
        private set

    /** Pieces still to land in the current streak. */
    var remaining = 0
        private set

    val active get() = remaining > 0

    /** Gems the finished streaks add to the run. */
    val gems get() = count * GEMS

    /** Index of the falling piece within the streak. */
    val piece get() = STACKS - remaining

    /** How far the falling piece has come: 0 at the top, 1 on landing. Speeds up like a real fall. */
    val fall: Float
        get() {
            if (!active || delay > 0f) return 0f
            val t = (elapsed / fallTime(piece)).coerceIn(0f, 1f)
            return t * t
        }

    private var elapsed = 0f
    private var delay = 0f

    fun reset() {
        count = 0
        cancel()
    }

    /** Stops the current streak without paying it out. */
    fun cancel() {
        remaining = 0
        elapsed = 0f
        delay = 0f
    }

    fun start() {
        remaining = STACKS
        elapsed = 0f
        delay = LEAD_IN
    }

    /** Advances the falling piece. Returns the index of the piece that landed this frame, or -1. */
    fun update(dt: Float): Int {
        if (!active) return -1
        if (delay > 0f) {
            delay -= dt
            return -1
        }
        elapsed += dt
        val i = piece
        if (elapsed < fallTime(i)) return -1
        elapsed = 0f
        remaining--
        if (remaining == 0) count++
        return i
    }

    companion object {
        const val LENGTH = 5
        const val STACKS = 5
        const val GEMS = 5

        /** Pause after the streak's own perfect drop, so its sound and buzz finish first. */
        const val LEAD_IN = 0.22f

        /** Whether this perfect drop, the [combo]th in a row, completes a streak. */
        fun completes(combo: Int) = combo > 0 && combo % LENGTH == 0

        /** The first piece takes the longest to fall; each next one lands a little sooner. */
        fun fallTime(piece: Int) = max(0.07f, 0.16f - piece * 0.02f)
    }
}
