package com.stackdrop.game.online

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/** ISO-8601 week in UTC, matching backend/functions/src/week.ts so the app reads the board the server writes. */
object IsoWeek {

    private const val DAY_MS = 86_400_000L

    /** e.g. "2026-W43". */
    fun id(epochMs: Long): String {
        val c = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
            timeInMillis = epochMs
        }
        return String.format(Locale.ROOT, "%d-W%02d", c.weekYear, c.get(Calendar.WEEK_OF_YEAR))
    }

    /** Time until the weekly boards reset, at Monday 00:00 UTC. */
    fun msUntilReset(epochMs: Long): Long {
        val day = Math.floorDiv(epochMs, DAY_MS)
        val weekday = Math.floorMod(day + 3, 7L) // 1970-01-01 was a Thursday; Monday is 0.
        return (day - weekday + 7) * DAY_MS - epochMs
    }

    /** "3d 4h", "5h 12m" or "12m". */
    fun countdown(ms: Long): String {
        val minutes = ms / 60_000
        val days = minutes / (24 * 60)
        val hours = minutes / 60 % 24
        val mins = minutes % 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${mins}m"
            else -> "${maxOf(mins, 1)}m"
        }
    }
}
