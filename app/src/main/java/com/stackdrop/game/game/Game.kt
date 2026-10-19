package com.stackdrop.game.game

import android.annotation.SuppressLint
import android.content.Context
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

    val modes: List<GameMode>
    var mode: GameMode
        private set

    var state = State.MENU
        private set
    var stateTime = 0f
        private set
    var time = 0f
        private set
    var theme: Theme = Palette.byId(save.selectedTheme)
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

    init {
        sound.muted = !save.soundOn
        haptics.enabled = save.vibrationOn
        val host = ModeHost(sound, haptics, backdrop, neon, hud)
        modes = listOf(StackMode(host), TowerMode(host))
        mode = modes.firstOrNull { it.id == save.lastMode } ?: modes[0]
        modes.forEach { it.reset(theme) }
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

        mode.update(dt)
        if (state == State.PLAYING && mode.isFinished) endRun()

        confetti.update(dt, width.toFloat())
        hud.update(dt)
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

    private fun setState(s: State, withFade: Boolean = false) {
        state = s
        stateTime = 0f
        if (withFade) fade = 1f
        // A press that changed screens must not also count as a tap or drag on the new one.
        drag.cancel()
    }

    fun openThemes() {
        sound.play(SoundManager.BUTTON)
        setState(State.THEMES)
    }

    fun backToMenu() {
        sound.play(SoundManager.BUTTON)
        setState(State.MENU)
    }

    fun leaveGameOver() {
        sound.play(SoundManager.BUTTON)
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
        newBest = mode.score > 0 && save.submitScore(mode.id, mode.score)
        if (newBest) {
            confetti.confetti(width.toFloat(), 110)
            sound.play(SoundManager.UNLOCK)
            haptics.celebrate()
        }
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
        confetti.confetti(width.toFloat(), 60)
    }

    fun selectTheme(index: Int) {
        val t = Palette.themes[index]
        if (t.id == theme.id) return
        val bought = !save.isUnlocked(t.id)
        if (bought) {
            if (save.gems < t.cost) {
                hud.showBanner("${t.cost - save.gems} more gems to unlock")
                sound.play(SoundManager.BUTTON, 0.8f)
                return
            }
            save.gems -= t.cost
            save.unlock(t.id)
        }

        theme = t
        save.selectedTheme = t.id
        modes.forEach { it.reset(t) }

        if (bought) {
            sound.play(SoundManager.UNLOCK)
            haptics.celebrate()
            confetti.confetti(width.toFloat(), 70)
            hud.showBanner("${t.name} unlocked!")
        } else {
            sound.play(SoundManager.BUTTON)
            haptics.ui()
        }
    }

    fun render(canvas: Canvas) {
        if (width == 0) return
        mode.render(canvas, time)
        confetti.drawScreen(canvas)
        hud.draw(canvas)
        backdrop.drawOverlay(canvas)
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
    }
}
