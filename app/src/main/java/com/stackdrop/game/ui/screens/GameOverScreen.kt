package com.stackdrop.game.ui.screens

import android.graphics.Canvas
import android.graphics.RectF
import com.stackdrop.game.game.Game
import com.stackdrop.game.game.Palette
import com.stackdrop.game.ui.Neon
import com.stackdrop.game.ui.Widgets
import kotlin.math.sin

class GameOverScreen(private val game: Game, private val neon: Neon, private val widgets: Widgets) : Screen {

    private val menuButton = RectF()
    private val retryButton = RectF()

    private var w = 0f
    private var h = 0f
    private var u = 1f

    override fun resize(w: Float, h: Float, u: Float) {
        this.w = w
        this.h = h
        this.u = u
        val over = h * 0.76f
        retryButton.set(u * 22f, over, u * 78f, over + u * 13f)
        menuButton.set(u * 32f, over + u * 16f, u * 68f, over + u * 26f)
    }

    override fun onTap(x: Float, y: Float): Boolean {
        if (game.stateTime < Game.RESTART_DELAY) return true
        if (menuButton.contains(x, y)) {
            game.press(menuButton)
            game.leaveGameOver()
        } else {
            game.press(retryButton)
            game.startMode(game.mode)
        }
        return true
    }

    override fun onBack(): Boolean {
        game.leaveGameOver()
        return true
    }

    override fun draw(canvas: Canvas) {
        val accent = Palette.accent(game.theme)
        val accent2 = Palette.accent2(game.theme)
        val t = game.stateTime
        val pulse = 0.5f + 0.5f * sin(t * 5f)
        val mode = game.mode
        val top = h * 0.09f

        val best = game.save.best(mode.id)
        val headline = when {
            game.newBest -> "AMAZING"
            best - mode.score in 1..3 -> "SO CLOSE"
            else -> "NICE RUN"
        }
        val text = neon.textColor
        val still = game.reduceMotion
        neon.chroma(canvas, headline, w / 2f, top, u * 9.5f, if (still) 0f else game.time, accent)
        neon.text(canvas, mode.endReason, w / 2f, top + u * 6.5f, u * 3.8f, text, 170)
        neon.glowText(canvas, "${mode.score}", w / 2f, top + u * 25f, u * 18f, text, accent)

        if (game.newBest) {
            val breathe = if (still) 1f else 1f + 0.06f * pulse
            neon.glowText(canvas, "NEW BEST!", w / 2f, top + u * 34f, u * 7f * breathe, text, Neon.GOLD)
        } else {
            neon.text(canvas, "BEST $best", w / 2f, top + u * 33f, u * 5f, text, 200)
        }

        val gems = "+${game.gemsEarned}"
        val tw = neon.measure(gems, u * 6f)
        neon.glowText(canvas, gems, w / 2f - u * 2.5f, top + u * 43f, u * 6f, text, Neon.GEM)
        neon.gem(canvas, w / 2f - u * 2.5f + tw / 2f + u * 4f, top + u * 41f, u * 2.6f)

        if (mode.bestCombo > 1) {
            neon.text(canvas, "BEST COMBO x${mode.bestCombo}", w / 2f, top + u * 50f, u * 4f, text, 180)
        }

        if (t > Game.RESTART_DELAY) {
            widgets.pill(canvas, retryButton, "RETRY", accent2, highlight = true, breathe = 1f + 0.04f * pulse)
            widgets.pill(canvas, menuButton, "MENU", accent)
        }
        widgets.gems(canvas, game.save.gems)
    }
}
