package com.stackdrop.game.ui.screens

import android.graphics.Canvas
import android.graphics.RectF
import com.stackdrop.game.game.Game
import com.stackdrop.game.game.GameMode
import com.stackdrop.game.game.Palette
import com.stackdrop.game.ui.Neon
import com.stackdrop.game.ui.Widgets
import kotlin.math.sin

class MenuScreen(private val game: Game, private val neon: Neon, private val widgets: Widgets) : Screen {

    private val themesButton = RectF()
    private val giftButton = RectF()
    private val settingsButton = RectF()
    private val modeCards = List(2) { RectF() }

    private var w = 0f
    private var h = 0f
    private var u = 1f

    override fun resize(w: Float, h: Float, u: Float) {
        this.w = w
        this.h = h
        this.u = u

        val row = h * 0.88f
        val bh = u * 11f
        themesButton.set(u * 6f, row, u * 48f, row + bh)
        giftButton.set(u * 52f, row, u * 94f, row + bh)

        val gearY = h * 0.06f - u * 1.6f
        settingsButton.set(w - u * 11f, gearY - u * 4.5f, w - u * 2f, gearY + u * 4.5f)

        val cardTop = h * 0.6f
        val cardH = u * 28f
        modeCards[0].set(u * 6f, cardTop, u * 48f, cardTop + cardH)
        modeCards[1].set(u * 52f, cardTop, u * 94f, cardTop + cardH)
    }

    override fun onTap(x: Float, y: Float): Boolean {
        val card = modeCards.indexOfFirst { it.contains(x, y) }
        when {
            card >= 0 -> {
                game.press(modeCards[card])
                game.startMode(game.modes[card])
            }
            themesButton.contains(x, y) -> {
                game.press(themesButton)
                game.openThemes()
            }
            settingsButton.contains(x, y) -> {
                game.press(settingsButton)
                game.openSettings()
            }
            giftButton.contains(x, y) -> {
                game.press(giftButton)
                game.claimDaily()
            }
            else -> game.startMode(game.mode)
        }
        return true
    }

    override fun onBack(): Boolean {
        game.bridge.exitToHome()
        return true
    }

    override fun draw(canvas: Canvas) {
        val accent = Palette.accent(game.theme)
        val accent2 = Palette.accent2(game.theme)
        val t = game.time
        val pulse = 0.5f + 0.5f * sin(t * 4f)
        // Reduce motion keeps the title still.
        val titleTime = if (game.reduceMotion) 0f else t

        val titleY = h * 0.12f
        neon.chroma(canvas, "STACK", w / 2f, titleY, u * 17f, titleTime, accent)
        neon.chroma(canvas, "DROP", w / 2f, titleY + u * 15f, u * 17f, titleTime + 0.7f, accent)
        neon.text(canvas, "ARCADE TOWERS", w / 2f, titleY + u * 22f, u * 3.6f, neon.textColor, 150)

        for ((i, m) in game.modes.withIndex()) drawModeCard(canvas, modeCards[i], m, i, m === game.mode, pulse)

        widgets.pill(canvas, themesButton, "THEMES", accent)
        if (game.save.canClaimDaily()) {
            widgets.pill(canvas, giftButton, "GIFT!", accent2, highlight = true, breathe = 1f + 0.05f * pulse)
        } else {
            widgets.pill(canvas, giftButton, "DAY ${game.save.streak}", accent, dim = true)
        }
        widgets.gear(canvas, settingsButton, accent)
        widgets.gems(canvas, game.save.gems, inset = u * 11f)
    }

    private fun drawModeCard(canvas: Canvas, r: RectF, mode: GameMode, index: Int, selected: Boolean, pulse: Float) {
        val accent = Palette.accent(game.theme)
        val accent2 = Palette.accent2(game.theme)
        canvas.save()
        val s = widgets.pressScale(r) * (if (selected) 1f + 0.015f * pulse else 1f)
        canvas.scale(s, s, r.centerX(), r.centerY())

        val radius = u * 4f
        neon.panel(canvas, r, radius, 150)
        neon.roundRect(
            canvas, r, radius, if (selected) accent else accent2, u * 0.45f,
            if (selected) (190 + 65 * pulse).toInt() else 110,
            fillAlpha = if (selected) 35 else 0,
        )

        val iconY = r.top + r.height() * 0.36f
        if (index == 0) widgets.stackIcon(canvas, r.centerX(), iconY, u * 6f, accent2)
        else widgets.craneIcon(canvas, r.centerX(), iconY, u * 6f, accent2)

        neon.glowText(canvas, mode.title, r.centerX(), r.top + r.height() * 0.73f, u * 6.5f, neon.textColor, accent)
        neon.text(canvas, "BEST ${game.save.best(mode.id)}", r.centerX(), r.top + r.height() * 0.89f, u * 3.6f,
            neon.textColor, 170)
        canvas.restore()
    }
}
