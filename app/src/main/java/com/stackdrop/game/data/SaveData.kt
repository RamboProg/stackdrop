package com.stackdrop.game.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.stackdrop.game.audio.SoundManager
import com.stackdrop.game.game.Palette
import com.stackdrop.game.game.ThemeRules
import java.util.TimeZone
import kotlin.math.min

/**
 * Local save, and the source of truth; the cloud save is a best-effort copy. Every change to a value
 * that syncs bumps [rev] and marks the save [dirty] so CloudSave knows to push it.
 */
class SaveData(context: Context) {

    private val prefs = context.getSharedPreferences("stackdrop", Context.MODE_PRIVATE)

    init {
        migrateTheme()
    }

    /** Increments on every synced change; the higher rev wins spendable values in a merge. */
    val rev: Int
        get() = prefs.getInt(KEY_REV, 0)

    /** True when there are changes the cloud has not confirmed yet. */
    val dirty: Boolean
        get() = prefs.getBoolean(KEY_DIRTY, false)

    var gems: Int
        get() = prefs.getInt(KEY_GEMS, 0)
        set(v) = change { putInt(KEY_GEMS, v) }

    var gamesPlayed: Int
        get() = prefs.getInt(KEY_GAMES, 0)
        set(v) = change { putInt(KEY_GAMES, v) }

    /** The theme in use. May be today's free theme, which the player does not own. */
    var selectedTheme: String
        get() = prefs.getString(KEY_THEME, Palette.NEW_PLAYER_ID) ?: Palette.NEW_PLAYER_ID
        set(v) = change { putString(KEY_THEME, v) }

    /** The last owned theme the player picked, used when a free-today theme expires. Local only. */
    var ownedTheme: String
        get() = prefs.getString(KEY_OWNED_THEME, null) ?: selectedTheme
        set(v) = prefs.edit { putString(KEY_OWNED_THEME, v) }

    var lastMode: String
        get() = prefs.getString(KEY_MODE, "") ?: ""
        set(v) = prefs.edit { putString(KEY_MODE, v) }

    var soundOn: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(v) = change { putBoolean(KEY_SOUND, v) }

    /**
     * Volume step from 0 (off) to [SoundManager.MAX_LEVEL]. Only on/off is synced (as [soundOn]); the step
     * itself stays on this phone, and muting keeps it so unmuting returns to the same loudness.
     */
    var soundLevel: Int
        get() = if (!soundOn) 0 else lastSoundLevel
        set(v) {
            val level = v.coerceIn(0, SoundManager.MAX_LEVEL)
            if (level > 0) prefs.edit { putInt(KEY_SOUND_LEVEL, level) }
            if (soundOn != level > 0) soundOn = level > 0
        }

    /** The level unmuting returns to. */
    val lastSoundLevel: Int
        get() = prefs.getInt(KEY_SOUND_LEVEL, SoundManager.DEFAULT_LEVEL).coerceIn(1, SoundManager.MAX_LEVEL)

    var vibrationOn: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION, true)
        set(v) = change { putBoolean(KEY_VIBRATION, v) }

    var reduceMotion: Boolean
        get() = prefs.getBoolean(KEY_REDUCE_MOTION, false)
        set(v) = change { putBoolean(KEY_REDUCE_MOTION, v) }

    val streak: Int
        get() = prefs.getInt(KEY_STREAK, 0)

    /** Avatar a guest picked before choosing a name, or -1 for the uid-based default. Local only. */
    var avatar: Int
        get() = prefs.getInt(KEY_AVATAR, -1)
        set(v) = prefs.edit { putInt(KEY_AVATAR, v) }

    /** How many runs set a new personal best; drives the one-time Google sign-in nudge. Local only. */
    var newBestCount: Int
        get() = prefs.getInt(KEY_NEW_BESTS, 0)
        set(v) = prefs.edit { putInt(KEY_NEW_BESTS, v) }

    /** Runs not yet accepted by the leaderboard server. Local only. */
    var pendingRuns: List<PendingRun>
        get() = PendingRuns.decode(prefs.getString(KEY_PENDING_RUNS, "") ?: "")
        set(v) = prefs.edit { putString(KEY_PENDING_RUNS, PendingRuns.encode(v)) }

    /** Rewarded "+25 gems" ads left today. Local only. */
    fun freeGemsLeft(): Int =
        if (prefs.getLong(KEY_FREE_GEMS_DAY, -1L) == today()) FREE_GEMS_PER_DAY - prefs.getInt(KEY_FREE_GEMS_USED, 0)
        else FREE_GEMS_PER_DAY

    fun useFreeGems() {
        val used = FREE_GEMS_PER_DAY - freeGemsLeft() + 1
        prefs.edit {
            putLong(KEY_FREE_GEMS_DAY, today())
            putInt(KEY_FREE_GEMS_USED, used)
        }
    }

    /** Last answer from Google Play about the premium purchase. Local only; Play stays the source of truth. */
    var premium: Boolean
        get() = prefs.getBoolean(KEY_PREMIUM, false)
        set(v) = prefs.edit { putBoolean(KEY_PREMIUM, v) }

    var googleNudgeShown: Boolean
        get() = prefs.getBoolean(KEY_NUDGED, false)
        set(v) = prefs.edit { putBoolean(KEY_NUDGED, v) }

    fun best(modeId: String) = prefs.getInt(bestKey(modeId), 0)

    /** Returns true when [score] is a new personal best for that mode. */
    fun submitScore(modeId: String, score: Int): Boolean {
        if (score <= best(modeId)) return false
        change { putInt(bestKey(modeId), score) }
        return true
    }

    /** Free themes, bought themes, and themes whose milestone the player has reached. */
    fun isUnlocked(id: String): Boolean {
        val theme = Palette.find(id) ?: return false
        val milestoneBest = theme.milestone?.let { best(it.modeId) } ?: 0
        return ThemeRules.isUnlocked(theme, id in unlocked(), milestoneBest, premium)
    }

    fun unlock(id: String) = change { putStringSet(KEY_UNLOCKED, unlocked() + id) }

    private fun unlocked(): Set<String> = prefs.getStringSet(KEY_UNLOCKED, emptySet()) ?: emptySet()

    fun canClaimDaily() = prefs.getLong(KEY_LAST_DAILY, -1L) != today()

    /** Grants the daily gift and returns the gem reward, or 0 if already claimed today. */
    fun claimDaily(): Int {
        val today = today()
        val last = prefs.getLong(KEY_LAST_DAILY, -1L)
        if (last == today) return 0
        val newStreak = if (last == today - 1) streak + 1 else 1
        val reward = DAILY_BASE * min(newStreak, DAILY_MAX_STREAK)
        change {
            putLong(KEY_LAST_DAILY, today)
            putInt(KEY_STREAK, newStreak)
            putInt(KEY_GEMS, gems + reward)
        }
        return reward
    }

    /** Local day number, the same one the daily gift uses. */
    fun today(): Long {
        val now = System.currentTimeMillis()
        return (now + TimeZone.getDefault().getOffset(now)) / DAY_MS
    }

    fun snapshot(): SaveSnapshot {
        // Rev first: if a write lands mid-read, the snapshot carries newer values under an older rev,
        // which only means one extra push later.
        val rev = rev
        return SaveSnapshot(
            rev = rev,
            gems = gems,
            unlockedThemes = unlocked().toSet(),
            selectedTheme = selectedTheme,
            bests = MODES.associateWith { best(it) },
            streak = streak,
            lastDaily = prefs.getLong(KEY_LAST_DAILY, -1L),
            gamesPlayed = gamesPlayed,
            settings = SaveSnapshot.Settings(soundOn, vibrationOn, reduceMotion),
        )
    }

    /** Overwrites the synced values with a merge result. [dirty] says whether the cloud still needs it. */
    fun restore(s: SaveSnapshot, dirty: Boolean) = prefs.edit {
        putInt(KEY_REV, s.rev)
        putBoolean(KEY_DIRTY, dirty)
        putInt(KEY_GEMS, s.gems)
        putStringSet(KEY_UNLOCKED, s.unlockedThemes)
        putString(KEY_THEME, s.selectedTheme)
        for ((mode, score) in s.bests) putInt(bestKey(mode), score)
        putInt(KEY_STREAK, s.streak)
        putLong(KEY_LAST_DAILY, s.lastDaily)
        putInt(KEY_GAMES, s.gamesPlayed)
        putBoolean(KEY_SOUND, s.settings.sound)
        putBoolean(KEY_VIBRATION, s.settings.vibration)
        putBoolean(KEY_REDUCE_MOTION, s.settings.reduceMotion)
    }

    /** After account deletion: a fresh install's save, keeping only the player's settings. */
    fun resetKeepingSettings() {
        val sound = soundOn
        val level = lastSoundLevel
        val vibration = vibrationOn
        val reduce = reduceMotion
        // Premium was bought through Google Play, not the game account, so it survives too.
        val owned = premium
        prefs.edit {
            clear()
            putBoolean(KEY_PREMIUM, owned)
            putBoolean(KEY_SOUND, sound)
            putInt(KEY_SOUND_LEVEL, level)
            putBoolean(KEY_VIBRATION, vibration)
            putBoolean(KEY_REDUCE_MOTION, reduce)
            putString(KEY_THEME, Palette.NEW_PLAYER_ID)
            putInt(KEY_REV, 0)
            putBoolean(KEY_DIRTY, true)
        }
    }

    /** The cloud confirmed [pushedRev]; clears [dirty] unless something changed since. */
    fun markSynced(pushedRev: Int) {
        if (rev == pushedRev) prefs.edit { putBoolean(KEY_DIRTY, false) }
    }

    private inline fun change(crossinline block: SharedPreferences.Editor.() -> Unit) {
        val next = rev + 1
        prefs.edit {
            block()
            putInt(KEY_REV, next)
            putBoolean(KEY_DIRTY, true)
        }
    }

    // The stack mode keeps the original key so earlier best scores carry over.
    private fun bestKey(modeId: String) = if (modeId == "stack") KEY_BEST else "${KEY_BEST}_$modeId"

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
        /** Local only, like the avatar: the cloud keeps just [KEY_SOUND]. */
        const val KEY_SOUND_LEVEL = "sound_level"
        const val KEY_VIBRATION = "vibration"
        const val KEY_STREAK = "streak"
        const val KEY_UNLOCKED = "unlocked"
        const val KEY_LAST_DAILY = "last_daily"
        const val KEY_OWNED_THEME = "theme_owned"
        const val KEY_REDUCE_MOTION = "reduce_motion"
        const val KEY_REV = "rev"
        const val KEY_DIRTY = "dirty"
        const val KEY_AVATAR = "avatar"
        const val KEY_NEW_BESTS = "new_bests"
        const val KEY_NUDGED = "google_nudged"
        const val KEY_PENDING_RUNS = "pending_runs"
        const val KEY_PREMIUM = "premium"
        const val KEY_FREE_GEMS_DAY = "free_gems_day"
        const val KEY_FREE_GEMS_USED = "free_gems_used"
        const val FREE_GEMS_PER_DAY = 5
        const val DAILY_BASE = 10
        const val DAILY_MAX_STREAK = 7
        const val DAY_MS = 86_400_000L
        val MODES = listOf("stack", "tower")
    }
}
