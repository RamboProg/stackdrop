package com.stackdrop.game.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.stackdrop.game.audio.Haptics
import com.stackdrop.game.audio.SoundManager
import com.stackdrop.game.data.SaveData
import com.stackdrop.game.game.tower.TowerMode
import com.stackdrop.game.platform.PlatformBridge
import com.stackdrop.game.ui.Hud
import com.stackdrop.game.ui.Neon
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max

class Game(private val context: Context, val bridge: PlatformBridge) {

    enum class State { MENU, THEMES, PLAYING, GAME_OVER, PROFILE, LEADERBOARD, FRIENDS, SETTINGS }

    val save = SaveData(context)
    private val sound = SoundManager(context)
    private val haptics = Haptics(context)
    private val neon = Neon(loadFont())
    private val backdrop = Backdrop()
    val hud = Hud(this, neon)
    private val confetti = Particles()
    private val host = ModeHost(sound, haptics, backdrop, neon, hud)

    val modes: List<GameMode>
    var mode: GameMode
        private set

    var state = State.MENU
        private set
    var stateTime = 0f
        private set
    var time = 0f
        private set

    /** The theme on screen. While previewing on the Themes screen it differs from the saved one. */
    var theme: Theme = Palette.byId(Palette.NEW_PLAYER_ID)
        private set
    val previewing get() = theme.id != save.selectedTheme

    /** Today's free-to-use locked theme, refreshed when the Themes screen opens. */
    var freeToday: Theme? = null
        private set
    var reduceMotion = false
        private set
    var gemsEarned = 0
        private set
    var newBest = false
        private set

    private var width = 0
    private var height = 0
    private var fade = 0f
    private val fadePaint = Paint()
    private val touches = TouchQueue()
    private val touchSink = TouchSink(::handleTouch)
    private val drag = DragTracker()
    private val events = ConcurrentLinkedQueue<GameEvent>()

    private val confettiColors = IntArray(5)
    private var crossFade = 0f
    private var fadeFrame: Bitmap? = null
    private var fadeCanvas: Canvas? = null
    private val fadeDst = RectF()
    private val crossPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        sound.muted = !save.soundOn
        haptics.enabled = save.vibrationOn
        applyComfort()
        modes = listOf(StackMode(host), TowerMode(host))
        mode = modes.firstOrNull { it.id == save.lastMode } ?: modes[0]
        refreshFreeToday()
        applyTheme(usableSavedTheme())
    }

    fun resize(w: Int, h: Int) {
        width = w
        height = h
        drag.slop = w * TAP_SLOP
        backdrop.resize(w, h)
        hud.resize(w, h)
        modes.forEach { it.resize(w, h) }
    }

    /** Called on the UI thread with a [TouchQueue] action. */
    fun onTouch(action: Int, x: Float, y: Float) = touches.push(action, x, y)

    /** Safe from any thread; applied at the start of the next update. */
    fun post(event: GameEvent) {
        events.add(event)
    }

    fun onResume() = sound.resumeMusic()

    fun onPause() = sound.pauseMusic()

    fun release() = sound.release()

    fun update(dt: Float) {
        while (true) {
            val event = events.poll() ?: break
            handleEvent(event)
        }
        touches.drain(touchSink)
        val glide = drag.update(dt)
        if (glide != 0f) hud.onDrag(glide)

        time += dt
        stateTime += dt
        fade = max(0f, fade - dt * 3f)
        crossFade = max(0f, crossFade - dt / CROSS_FADE_TIME)

        mode.update(dt)
        if (state == State.PLAYING && mode.isFinished) endRun()

        confetti.update(dt, width.toFloat())
        hud.update(dt)
        sound.update(dt)
    }

    private fun handleTouch(action: Int, x: Float, y: Float) {
        when (action) {
            TouchQueue.DOWN -> {
                drag.down(x, y)
                if (!hud.tapsOnUp) hud.onTap(x, y)
            }
            TouchQueue.MOVE -> {
                val dy = drag.move(x, y)
                if (dy != 0f) hud.onDrag(dy)
            }
            TouchQueue.UP -> if (drag.up() && hud.tapsOnUp) hud.onTap(x, y)
            TouchQueue.CANCEL -> drag.cancel()
        }
    }

    private fun handleEvent(event: GameEvent) {
        when (event) {
            GameEvent.Back -> if (!hud.onBack()) backToMenu()
            is GameEvent.Toast -> hud.showBanner(event.message)
            else -> Unit
        }
    }

    /** Button press feedback: the shared scale bounce plus a light tick. */
    fun press(r: RectF) {
        hud.press(r)
        haptics.ui()
    }

    fun click() = sound.play(SoundManager.BUTTON)

    private fun setState(s: State, withFade: Boolean = false) {
        state = s
        stateTime = 0f
        if (withFade) fade = 1f
        // A press that changed screens must not also count as a tap or drag on the new one.
        drag.cancel()
        hud.onEnter()
    }

    fun openThemes() {
        sound.play(SoundManager.BUTTON)
        refreshFreeToday()
        setState(State.THEMES)
    }

    /** Leaves the Themes screen, dropping any theme that was only being previewed. */
    fun closeThemes() {
        if (previewing) applyTheme(Palette.byId(save.selectedTheme))
        backToMenu()
    }

    fun openSettings() {
        sound.play(SoundManager.BUTTON)
        setState(State.SETTINGS)
    }

    fun backToMenu() {
        sound.play(SoundManager.BUTTON)
        expireFreeTheme()
        setState(State.MENU)
    }

    fun leaveGameOver() {
        sound.play(SoundManager.BUTTON)
        expireFreeTheme()
        mode.reset(theme)
        setState(State.MENU, withFade = true)
    }

    fun startMode(m: GameMode) {
        sound.play(SoundManager.BUTTON)
        mode = m
        save.lastMode = m.id
        m.reset(theme)
        confetti.clear()
        setState(State.PLAYING, withFade = true)
    }

    private fun endRun() {
        setState(State.GAME_OVER)
        gemsEarned = mode.gemsEarned
        save.gems += gemsEarned
        save.gamesPlayed++
        val previousBest = save.best(mode.id)
        newBest = mode.score > 0 && save.submitScore(mode.id, mode.score)
        if (newBest) {
            celebrate(110)
            sound.play(SoundManager.UNLOCK)
            haptics.celebrate()
        }
        val reached = ThemeRules.newlyReached(Palette.themes, mode.id, previousBest, mode.score)
        for (t in reached) save.unlock(t.id)
        reached.firstOrNull()?.let { hud.showBanner("${it.name} theme unlocked!") }
    }

    fun toggleVibration() {
        save.vibrationOn = !save.vibrationOn
        haptics.enabled = save.vibrationOn
        sound.play(SoundManager.BUTTON)
        if (save.vibrationOn) haptics.perfect(1)
    }

    fun toggleSound() {
        save.soundOn = !save.soundOn
        sound.muted = !save.soundOn
        if (save.soundOn) {
            sound.resumeMusic()
            sound.play(SoundManager.BUTTON)
        } else {
            sound.pauseMusic()
        }
    }

    fun toggleReduceMotion() {
        save.reduceMotion = !save.reduceMotion
        applyComfort()
        sound.play(SoundManager.BUTTON)
    }

    fun claimDaily() {
        if (!save.canClaimDaily()) {
            hud.showBanner("Next gift tomorrow")
            sound.play(SoundManager.BUTTON, 0.8f)
            return
        }
        val reward = save.claimDaily()
        hud.showBanner("+$reward gems   Day ${save.streak} streak")
        sound.play(SoundManager.UNLOCK)
        haptics.celebrate()
        celebrate(60)
    }

    /**
     * A usable theme is put in use. A locked one is previewed on the first tap and bought on the
     * second, if the player has the gems.
     */
    fun selectTheme(t: Theme) {
        when {
            canUse(t) -> {
                if (t.id == save.selectedTheme && !previewing) return
                commitTheme(t)
                sound.play(SoundManager.BUTTON)
                haptics.ui()
                if (!save.isUnlocked(t.id)) hud.showBanner("${t.name} is free today")
            }
            t.id != theme.id -> {
                applyTheme(t)
                sound.play(SoundManager.BUTTON)
                haptics.ui()
                hud.showBanner(if (save.gems >= t.cost) "Tap again to unlock" else gemsNeeded(t))
            }
            save.gems < t.cost -> {
                hud.showBanner(gemsNeeded(t))
                sound.play(SoundManager.BUTTON, 0.8f)
            }
            else -> {
                save.gems -= t.cost
                save.unlock(t.id)
                commitTheme(t)
                refreshFreeToday()
                sound.play(SoundManager.UNLOCK)
                haptics.celebrate()
                celebrate(70)
                hud.showBanner("${t.name} unlocked!")
            }
        }
    }

    private fun gemsNeeded(t: Theme) = "${t.cost - save.gems} more gems to unlock"

    private fun canUse(t: Theme) = save.isUnlocked(t.id) || t.id == freeToday?.id

    private fun commitTheme(t: Theme) {
        save.selectedTheme = t.id
        if (save.isUnlocked(t.id)) save.ownedTheme = t.id
        if (theme.id != t.id) applyTheme(t)
    }

    private fun applyTheme(t: Theme) {
        snapshotScene()
        theme = t
        neon.applyTheme(t)
        modes.forEach { it.reset(t) }
        sound.setAmbient(t.id)
        for (k in confettiColors.indices) confettiColors[k] = Palette.blockColor(t, k * 3)
    }

    private fun refreshFreeToday() {
        freeToday = ThemeRules.freeToday(Palette.themes, save.today()) { save.isUnlocked(it.id) }
    }

    /** The saved theme, unless it was yesterday's free theme; then the last owned one. */
    private fun usableSavedTheme(): Theme {
        val saved = Palette.byId(save.selectedTheme)
        if (canUse(saved)) return saved
        val owned = Palette.byId(save.ownedTheme)
        val fallback = if (save.isUnlocked(owned.id)) owned else Palette.byId(Palette.NEW_PLAYER_ID)
        save.selectedTheme = fallback.id
        return fallback
    }

    private fun expireFreeTheme() {
        refreshFreeToday()
        val usable = usableSavedTheme()
        if (usable.id != theme.id) applyTheme(usable)
    }

    private fun applyComfort() {
        reduceMotion = save.reduceMotion
        host.reduceMotion = reduceMotion
        backdrop.reduceMotion = reduceMotion
    }

    private fun celebrate(count: Int) {
        confetti.confetti(width.toFloat(), host.particles(theme, count), if (theme.calm) confettiColors else null)
    }

    /** Keeps a half-size copy of the current scene so a theme change can cross-fade from it. */
    private fun snapshotScene() {
        if (width == 0 || height == 0) return
        val bw = max(1, width / 2)
        val bh = max(1, height / 2)
        var bmp = fadeFrame
        if (bmp == null || bmp.width != bw || bmp.height != bh) {
            bmp?.recycle()
            bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            fadeFrame = bmp
            fadeCanvas = Canvas(bmp)
        }
        val c = fadeCanvas ?: return
        c.save()
        c.scale(bw / width.toFloat(), bh / height.toFloat())
        mode.render(c, time)
        c.restore()
        crossFade = 1f
    }

    fun render(canvas: Canvas) {
        if (width == 0) return
        mode.render(canvas, time)
        if (crossFade > 0f) {
            fadeFrame?.let {
                crossPaint.alpha = (255 * crossFade).toInt()
                fadeDst.set(0f, 0f, width.toFloat(), height.toFloat())
                canvas.drawBitmap(it, null, fadeDst, crossPaint)
            }
        }
        confetti.drawScreen(canvas)
        hud.draw(canvas)
        backdrop.drawOverlay(canvas, theme)
        if (fade > 0f) {
            fadePaint.color = Color.BLACK
            fadePaint.alpha = (255 * fade * fade).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun loadFont(): Typeface {
        val id = context.resources.getIdentifier("game_font", "font", context.packageName)
        if (id != 0) {
            try {
                ResourcesCompat.getFont(context, id)?.let { return it }
            } catch (e: Exception) {
                // fall back to the system font
            }
        }
        return Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    companion object {
        const val RESTART_DELAY = 0.5f
        /** A press that travels less than this share of the screen width is a tap, not a drag. */
        private const val TAP_SLOP = 0.02f
        private const val CROSS_FADE_TIME = 0.5f
    }
}
