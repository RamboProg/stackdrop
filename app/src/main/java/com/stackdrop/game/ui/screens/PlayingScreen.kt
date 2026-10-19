package com.stackdrop.game.ui.screens

import android.graphics.Canvas
import android.graphics.Color
import com.stackdrop.game.game.Game
import com.stackdrop.game.game.Palette
import com.stackdrop.game.ui.Hud
import com.stackdrop.game.ui.Neon
import com.stackdrop.game.ui.Widgets
import kotlin.math.max
import kotlin.math.min

class PlayingScreen(
    private val game: Game,
    private val hud: Hud,
    private val neon: Neon,
    private val widgets: Widgets,
) : Screen {

    private var w = 0f
    private var h = 0f
    private var u = 1f

    override fun resize(w: Float, h: Float, u: Float) {
        this.w = w
        this.h = h
        this.u = u
    }

    override fun onTap(x: Float, y: Float): Boolean {
        if (!game.mode.isOver) game.mode.onTap()
        return true
    }

    /** Back is ignored mid-run so a stray swipe never ends it. */
    override fun onBack() = true

    override fun draw(canvas: Canvas) {
        val accent = Palette.accent(game.theme)
        val accent2 = Palette.accent2(game.theme)
        neon.glowText(canvas, "${game.mode.score}", w / 2f, h * 0.11f, u * 15f * (1f + 0.25f * hud.scorePop),
            Color.WHITE, accent)

        val comboTime = hud.comboTime
        if (comboTime > 0f) {
            val alpha = (255 * min(1f, comboTime / 0.4f)).toInt()
            val pop = 1f + 0.35f * max(0f, comboTime - (Hud.COMBO_TIME - 0.2f)) / 0.2f
            neon.glowText(canvas, hud.comboLabel, w / 2f, h * 0.215f, u * 6f * pop, Color.WHITE, accent2, alpha)
        }
        game.mode.drawHud(canvas, neon, u, w, h)
        widgets.gems(canvas, game.save.gems)
    }
}
