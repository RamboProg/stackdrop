package com.stackdrop.game.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

/** Loads whichever sound files exist in res/raw; missing ones are silently skipped. */
class SoundManager(context: Context) {

    @Volatile
    var muted = false

    private val pool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = HashMap<String, Int>()
    private var music: MediaPlayer? = null

    init {
        for (name in EFFECTS) {
            val res = rawId(context, name)
            if (res != 0) ids[name] = pool.load(context, res, 1)
        }
        val musicRes = rawId(context, MUSIC)
        if (musicRes != 0) {
            music = MediaPlayer.create(context, musicRes)?.apply {
                isLooping = true
                setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
            }
        }
    }

    fun play(name: String, rate: Float = 1f, volume: Float = 1f) {
        if (muted) return
        val id = ids[name] ?: return
        pool.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun resumeMusic() {
        if (!muted) music?.takeIf { !it.isPlaying }?.start()
    }

    fun pauseMusic() {
        music?.takeIf { it.isPlaying }?.pause()
    }

    fun release() {
        pool.release()
        music?.release()
        music = null
    }

    @SuppressLint("DiscouragedApi")
    private fun rawId(context: Context, name: String) =
        context.resources.getIdentifier(name, "raw", context.packageName)

    companion object {
        const val DROP = "drop"
        const val PERFECT = "perfect"
        const val SLICE = "slice"
        const val FAIL = "fail"
        const val BUTTON = "button"
        const val UNLOCK = "unlock"
        const val RELEASE = "release"
        const val WARN = "warn"
        private const val MUSIC = "music"
        private const val MUSIC_VOLUME = 0.35f
        private val EFFECTS = listOf(DROP, PERFECT, SLICE, FAIL, BUTTON, UNLOCK, RELEASE, WARN)
    }
}
