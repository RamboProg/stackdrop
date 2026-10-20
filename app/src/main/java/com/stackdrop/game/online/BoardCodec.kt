package com.stackdrop.game.online

/**
 * Plain-text form of a board for the disk cache. Names can only contain letters, digits, spaces and
 * underscores, and uids are alphanumeric, so the separators below can never appear inside a field.
 */
object BoardCodec {

    private const val LINE = "\n"
    private const val FIELD = "|"

    fun encode(b: BoardSnapshot): String = buildString {
        append(listOf(b.id, b.myRank ?: "", b.myScore ?: "", b.fetchedAtMs).joinToString(FIELD))
        for (e in b.entries) {
            append(LINE)
            append(listOf(e.uid, e.name, e.avatar, e.score, e.rank).joinToString(FIELD))
        }
    }

    /** Returns null for anything unreadable; the board is then simply fetched again. */
    fun decode(text: String): BoardSnapshot? {
        val lines = text.split(LINE)
        val head = lines.firstOrNull()?.split(FIELD) ?: return null
        if (head.size != 4) return null
        val entries = lines.drop(1).map { line ->
            val f = line.split(FIELD)
            if (f.size != 5) return null
            BoardEntry(
                uid = f[0],
                name = f[1],
                avatar = f[2].toIntOrNull() ?: return null,
                score = f[3].toIntOrNull() ?: return null,
                rank = f[4].toIntOrNull() ?: return null,
            )
        }
        return BoardSnapshot(
            id = head[0],
            entries = entries,
            myRank = head[1].toIntOrNull(),
            myScore = head[2].toIntOrNull(),
            fetchedAtMs = head[3].toLongOrNull() ?: return null,
            fromCache = true,
            offline = false,
        )
    }
}
