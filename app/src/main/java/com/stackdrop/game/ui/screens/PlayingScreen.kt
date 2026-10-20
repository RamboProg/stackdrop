package com.stackdrop.game.ui.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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

    private val keepGoing = RectF()
    private val noThanks = RectF()
    private val ring = RectF()
    private val ringPath = Path()
    private val play = Path()
    private val veil = Paint()

    private var w = 0f
    private var h = 0f
    private var u = 1f

    override fun resize(w: Float, h: Float, u: Float) {
        this.w = w
        this.h = h
        this.u = u
        val cy = h * 0.42f
        val r = u * 13f
        ring.set(w / 2f - r, cy - r, w / 2f + r, cy + r)
        keepGoing.set(u * 20f, h * 0.55f, u * 80f, h * 0.55f + u * 12f)
        noThanks.set(u * 30f, h * 0.55f + u * 16f, u * 70f, h * 0.55f + u * 26f)
        play.reset()
        val s = u * 4.5f
        play.moveTo(w / 2f - s * 0.6f, cy - s)
        play.lineTo(w / 2f + s, cy)
        play.lineTo(w / 2f - s * 0.6f, cy + s)
        play.close()
    }

    override fun onTap(x: Float, y: Float): Boolean {
        if (game.reviveOffered) {
            when {
                keepGoing.contains(x, y) || ring.contains(x, y) -> {
                    game.press(keepGoing)
                    game.acceptRevive()
                }
                noThanks.contains(x, y) -> {
                    game.press(noThanks)
                    game.declineRevive()
                }
            }
            return true
        }
        if (!game.mode.isOver) game.mode.onTap()
        return true
    }

    /** Back is ignored mid-run so a stray swipe never ends it; on the second-chance offer it declines. */
    override fun onBack(): Boolean {
        if (game.reviveOffered) game.declineRevive()
        return true
    }

    override fun draw(canvas: Canvas) {
        val accent = Palette.accent(game.theme)
        val accent2 = Palette.accent2(game.theme)
        val still = game.reduceMotion
        val scorePop = if (still) 0f else hud.scorePop
        neon.glowText(canvas, "${game.mode.score}", w / 2f, h * 0.11f, u * 15f * (1f + 0.25f * scorePop),
            neon.textColor, accent)

        val comboTime = hud.comboTime
        if (comboTime > 0f) {
            val alpha = (255 * min(1f, comboTime / 0.4f)).toInt()
            val pop = if (still) 1f else 1f + 0.35f * max(0f, comboTime - (Hud.COMBO_TIME - 0.2f)) / 0.2f
            neon.glowText(canvas, hud.comboLabel, w / 2f, h * 0.215f, u * 6f * pop, neon.textColor, accent2, alpha)
        }
        game.mode.drawHud(canvas, neon, u, w, h)
        widgets.gems(canvas, game.save.gems)
        if (game.reviveOffered) drawSecondChance(canvas, accent, accent2)
    }

    /** "Keep going?" with a soft ring that empties over five seconds. */
    private fun drawSecondChance(canvas: Canvas, accent: Int, accent2: Int) {
        veil.color = game.theme.panel
        veil.alpha = 150
        canvas.drawRect(0f, 0f, w, h, veil)
        neon.glowText(canvas, "Keep going?", w / 2f, ring.top - u * 8f, u * 8f, neon.textColor, accent)

        neon.roundRect(canvas, ring, ring.width() / 2f, accent, u * 0.35f, 70)
        ringPath.reset()
        ringPath.addArc(ring, -90f, 360f * game.reviveProgress)
        neon.path(canvas, ringPath, accent2, u * 0.7f, 230)
        neon.path(canvas, play, accent2, u * 0.5f, 255)

        widgets.pill(canvas, keepGoing, "▶  WATCH AD", accent2, highlight = true)
        widgets.pill(canvas, noThanks, "No thanks", accent, dim = true)
    }
}
