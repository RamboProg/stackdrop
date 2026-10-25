package com.stackdrop.game.data

/** Lifetime gems one device has earned and spent for an account. Both only ever grow. */
data class GemCount(val earned: Int, val spent: Int)

/**
 * Gems as a per-device tally (a counter that merges without a server): the balance is everything
 * earned minus everything spent, across devices. Merging keeps the larger count for each device,
 * so the same save can be merged any number of times, in any order, without adding or losing gems.
 */
object GemLedger {

    /** Holds a balance from before the ledger existed, so old saves keep their gems. */
    const val LEGACY = "legacy"

    fun total(ledger: Map<String, GemCount>): Int =
        maxOf(0, ledger.values.sumOf { it.earned.toLong() - it.spent }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())

    fun merge(a: Map<String, GemCount>, b: Map<String, GemCount>): Map<String, GemCount> =
        (a.keys + b.keys).associateWith { id ->
            val x = a[id] ?: GemCount(0, 0)
            val y = b[id] ?: GemCount(0, 0)
            GemCount(maxOf(x.earned, y.earned), maxOf(x.spent, y.spent))
        }

    /** Records a change of [delta] gems (positive earned, negative spent) on [device]. */
    fun add(ledger: Map<String, GemCount>, device: String, delta: Int): Map<String, GemCount> {
        if (delta == 0) return ledger
        val c = ledger[device] ?: GemCount(0, 0)
        val next = if (delta > 0) c.copy(earned = c.earned + delta) else c.copy(spent = c.spent - delta)
        return ledger + (device to next)
    }

    /** "id:earned:spent" entries joined by ";", for SharedPreferences. */
    fun encode(ledger: Map<String, GemCount>): String =
        ledger.entries.sortedBy { it.key }.joinToString(";") { (id, c) -> "$id:${c.earned}:${c.spent}" }

    fun decode(text: String): Map<String, GemCount> = text.split(';').mapNotNull { entry ->
        val parts = entry.split(':')
        if (parts.size != 3 || parts[0].isEmpty()) return@mapNotNull null
        val earned = parts[1].toIntOrNull() ?: return@mapNotNull null
        val spent = parts[2].toIntOrNull() ?: return@mapNotNull null
        parts[0] to GemCount(earned, spent)
    }.toMap()
}
