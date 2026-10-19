package com.stackdrop.game.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import kotlin.math.max
import kotlin.math.min

/** Loads whichever sound files exist in res/raw; missing ones are silently skipped. */
class SoundManager(context: Context) {

    @Volatile
    var muted = false

    private val appContext = context.applicationContext

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

    // Per-theme ambient loops cross-fade on theme change. Players are touched from both the UI thread
    // (resume/pause) and the game thread (theme change, fades), so those calls are synchronized.
    private var ambientId = ""
    private var ambient: MediaPlayer? = null
    private var ambientVolume = 0f
    private var fading: MediaPlayer? = null
    private var fadingVolume = 0f
    private var playing = false

    init {
        for (name in EFFECTS) {
            val res = rawId(name)
            if (res != 0) ids[name] = pool.load(appContext, res, 1)
        }
        val musicRes = rawId(MUSIC)
        if (musicRes != 0) {
            music = MediaPlayer.create(appContext, musicRes)?.apply {
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

    @Synchronized
    fun resumeMusic() {
        if (muted) return
        playing = true
        music?.takeIf { !it.isPlaying }?.start()
        ambient?.takeIf { !it.isPlaying }?.start()
    }

    @Synchronized
    fun pauseMusic() {
        playing = false
        music?.takeIf { it.isPlaying }?.pause()
        ambient?.takeIf { it.isPlaying }?.pause()
        fading?.release()
        fading = null
    }

    /** Switches to `res/raw/ambient_<themeId>` if it exists, cross-fading from the previous loop. */
    @Synchronized
    fun setAmbient(themeId: String) {
        if (themeId == ambientId) return
        ambientId = themeId
        fading?.release()
        fading = ambient
        fadingVolume = ambientVolume
        ambient = null
        ambientVolume = 0f
        val res = rawId("$AMBIENT_PREFIX$themeId")
        if (res == 0) return
        ambient = MediaPlayer.create(appContext, res)?.apply {
            isLooping = true
            setVolume(0f, 0f)
            if (playing) start()
        }
    }

    /** Advances the ambient cross-fade. Called every game update. */
    @Synchronized
    fun update(dt: Float) {
        val step = dt * AMBIENT_VOLUME / AMBIENT_FADE
        ambient?.let {
            if (ambientVolume < AMBIENT_VOLUME) {
                ambientVolume = min(AMBIENT_VOLUME, ambientVolume + step)
                it.setVolume(ambientVolume, ambientVolume)
            }
        }
        fading?.let {
            fadingVolume = max(0f, fadingVolume - step)
            if (fadingVolume <= 0f) {
                it.release()
                fading = null
            } else {
                it.setVolume(fadingVolume, fadingVolume)
            }
        }
    }

    @Synchronized
    fun release() {
        pool.release()
        music?.release()
        music = null
        ambient?.release()
        ambient = null
        fading?.release()
        fading = null
    }

    @SuppressLint("DiscouragedApi")
    private fun rawId(name: String) =
        appContext.resources.getIdentifier(name, "raw", appContext.packageName)

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
        private const val AMBIENT_PREFIX = "ambient_"
        private const val AMBIENT_VOLUME = 0.25f
        private const val AMBIENT_FADE = 1f
        private val EFFECTS = listOf(DROP, PERFECT, SLICE, FAIL, BUTTON, UNLOCK, RELEASE, WARN)
    }
}
