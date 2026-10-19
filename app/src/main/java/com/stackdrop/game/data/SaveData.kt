package com.stackdrop.game.data

import android.content.Context
import androidx.core.content.edit
import com.stackdrop.game.game.Palette
import java.util.TimeZone
import kotlin.math.min

class SaveData(context: Context) {

    private val prefs = context.getSharedPreferences("stackdrop", Context.MODE_PRIVATE)

    init {
        migrateTheme()
    }

    var gems: Int
        get() = prefs.getInt(KEY_GEMS, 0)
        set(v) = prefs.edit { putInt(KEY_GEMS, v) }

    var gamesPlayed: Int
        get() = prefs.getInt(KEY_GAMES, 0)
        set(v) = prefs.edit { putInt(KEY_GAMES, v) }

    /** The theme in use. May be today's free theme, which the player does not own. */
    var selectedTheme: String
        get() = prefs.getString(KEY_THEME, Palette.NEW_PLAYER_ID) ?: Palette.NEW_PLAYER_ID
        set(v) = prefs.edit { putString(KEY_THEME, v) }

    /** The last owned theme the player picked, used when a free-today theme expires. */
    var ownedTheme: String
        get() = prefs.getString(KEY_OWNED_THEME, null) ?: selectedTheme
        set(v) = prefs.edit { putString(KEY_OWNED_THEME, v) }

    var lastMode: String
        get() = prefs.getString(KEY_MODE, "") ?: ""
        set(v) = prefs.edit { putString(KEY_MODE, v) }

    var soundOn: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(v) = prefs.edit { putBoolean(KEY_SOUND, v) }

    var vibrationOn: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION, true)
        set(v) = prefs.edit { putBoolean(KEY_VIBRATION, v) }

    var reduceMotion: Boolean
        get() = prefs.getBoolean(KEY_REDUCE_MOTION, false)
        set(v) = prefs.edit { putBoolean(KEY_REDUCE_MOTION, v) }

    val streak: Int
        get() = prefs.getInt(KEY_STREAK, 0)

    fun best(modeId: String) = prefs.getInt(bestKey(modeId), 0)

    /** Returns true when [score] is a new personal best for that mode. */
    fun submitScore(modeId: String, score: Int): Boolean {
        if (score <= best(modeId)) return false
        prefs.edit { putInt(bestKey(modeId), score) }
        return true
    }

    /** Free themes, bought themes, and themes whose milestone the player has reached. */
    fun isUnlocked(id: String): Boolean {
        val theme = Palette.find(id) ?: return false
        if (theme.cost == 0 || id in unlocked()) return true
        val m = theme.milestone ?: return false
        return best(m.modeId) >= m.score
    }

    fun unlock(id: String) = prefs.edit { putStringSet(KEY_UNLOCKED, unlocked() + id) }

    private fun unlocked(): Set<String> = prefs.getStringSet(KEY_UNLOCKED, emptySet()) ?: emptySet()

    fun canClaimDaily() = prefs.getLong(KEY_LAST_DAILY, -1L) != today()

    /** Grants the daily gift and returns the gem reward, or 0 if already claimed today. */
    fun claimDaily(): Int {
        val today = today()
        val last = prefs.getLong(KEY_LAST_DAILY, -1L)
        if (last == today) return 0
        val newStreak = if (last == today - 1) streak + 1 else 1
        val reward = DAILY_BASE * min(newStreak, DAILY_MAX_STREAK)
        prefs.edit {
            putLong(KEY_LAST_DAILY, today)
            putInt(KEY_STREAK, newStreak)
            putInt(KEY_GEMS, gems + reward)
        }
        return reward
    }

    // The stack mode keeps the original key so earlier best scores carry over.
    private fun bestKey(modeId: String) = if (modeId == "stack") KEY_BEST else "${KEY_BEST}_$modeId"

    /** Local day number, the same one the daily gift uses. */
    fun today(): Long {
        val now = System.currentTimeMillis()
        return (now + TimeZone.getDefault().getOffset(now)) / DAY_MS
    }

    // Players from before the calm themes have no saved theme if they never changed it; they keep
    // the original neon look. Only brand-new installs start on the calm default.
    private fun migrateTheme() {
        if (prefs.contains(KEY_THEME)) return
        val returning = prefs.contains(KEY_GAMES) || prefs.contains(KEY_GEMS) || prefs.contains(KEY_BEST)
        val id = if (returning) Palette.DEFAULT_ID else Palette.NEW_PLAYER_ID
        prefs.edit { putString(KEY_THEME, id) }
    }

    private companion object {
        const val KEY_BEST = "best"
        const val KEY_GEMS = "gems"
        const val KEY_GAMES = "games"
        const val KEY_THEME = "theme"
        const val KEY_MODE = "mode"
        const val KEY_SOUND = "sound"
        const val KEY_VIBRATION = "vibration"
        const val KEY_STREAK = "streak"
        const val KEY_UNLOCKED = "unlocked"
        const val KEY_LAST_DAILY = "last_daily"
        const val KEY_OWNED_THEME = "theme_owned"
        const val KEY_REDUCE_MOTION = "reduce_motion"
        const val DAILY_BASE = 10
        const val DAILY_MAX_STREAK = 7
        const val DAY_MS = 86_400_000L
    }
}
