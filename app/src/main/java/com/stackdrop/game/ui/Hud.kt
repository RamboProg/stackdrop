package com.stackdrop.game.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.stackdrop.game.game.Game
import com.stackdrop.game.game.GameMode
import com.stackdrop.game.game.Palette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Draws every screen (menu, themes, in-game HUD, game over) straight onto the game canvas. */
class Hud(private val game: Game, private val neon: Neon) {

    val themesButton = RectF()
    val soundButton = RectF()
    val vibrationButton = RectF()
    val giftButton = RectF()
    val menuButton = RectF()
    val retryButton = RectF()
    val backButton = RectF()
    val modeCards = List(2) { RectF() }
    private val themeRows = List(Palette.themes.size) { RectF() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icon = Path()
    private val swatch = RectF()
    private val bannerRect = RectF()

    private var w = 0f
    private var h = 0f
    private var u = 1f

    private var scorePop = 0f
    private var comboLabel = ""
    private var comboTime = 0f
    private var banner = ""
    private var bannerTime = 0f
    private var pressed: RectF? = null
    private var pressTime = 0f

    fun resize(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        u = w / 100f
        neon.u = u

        val bh = u * 11f
        val row = h * 0.88f
        val bw = u * 21.5f
        val buttonGap = u * 2f
        var bx = u * 4f
        for (b in listOf(themesButton, soundButton, vibrationButton, giftButton)) {
            b.set(bx, row, bx + bw, row + bh)
            bx += bw + buttonGap
        }
        backButton.set(u * 30f, row, u * 70f, row + bh)

        val cardTop = h * 0.6f
        val cardH = u * 28f
        modeCards[0].set(u * 6f, cardTop, u * 48f, cardTop + cardH)
        modeCards[1].set(u * 52f, cardTop, u * 94f, cardTop + cardH)

        val over = h * 0.76f
        retryButton.set(u * 22f, over, u * 78f, over + u * 13f)
        menuButton.set(u * 32f, over + u * 16f, u * 68f, over + u * 26f)

        val rowH = min(h * 0.095f, u * 17f)
        val gap = u * 2.5f
        var y = h * 0.17f
        for (r in themeRows) {
            r.set(u * 7f, y, u * 93f, y + rowH)
            y += rowH + gap
        }
    }

    fun modeCardAt(x: Float, y: Float) = modeCards.indexOfFirst { it.contains(x, y) }

    fun themeRowAt(x: Float, y: Float) = themeRows.indexOfFirst { it.contains(x, y) }

    fun press(r: RectF) {
        pressed = r
        pressTime = PRESS_TIME
    }

    fun onScore() {
        scorePop = 1f
    }

    fun showCombo(label: String) {
        comboLabel = label
        comboTime = COMBO_TIME
    }

    fun showBanner(message: String) {
        banner = message
        bannerTime = BANNER_TIME
    }

    fun update(dt: Float) {
        scorePop = max(0f, scorePop - dt * 4f)
        comboTime = max(0f, comboTime - dt)
        bannerTime = max(0f, bannerTime - dt)
        pressTime = max(0f, pressTime - dt)
    }

    fun draw(canvas: Canvas) {
        if (w == 0f) return
        when (game.state) {
            Game.State.MENU -> drawMenu(canvas)
            Game.State.THEMES -> drawThemes(canvas)
            Game.State.PLAYING -> drawPlaying(canvas)
            Game.State.GAME_OVER -> drawGameOver(canvas)
        }
        drawBanner(canvas)
    }

    // region Screens

    private fun drawMenu(canvas: Canvas) {
        val accent = Palette.accent(game.theme)
        val accent2 = Palette.accent2(game.theme)
        val t = game.time
        val pulse = 0.5f + 0.5f * sin(t * 4f)

        val titleY = h * 0.12f
        neon.chroma(canvas, "STACK", w / 2f, titleY, u * 17f, t, accent)
        neon.chroma(canvas, "DROP", w / 2f, titleY + u * 15f, u * 17f, t + 0.7f, accent)
        neon.text(canvas, "ARCADE TOWERS", w / 2f, titleY + u * 22f, u * 3.6f, Color.WHITE, 150)

        for ((i, m) in game.modes.withIndex()) drawModeCard(canvas, modeCards[i], m, i, m === game.mode, pulse)

        pill(canvas, themesButton, "THEMES", accent)
        val soundOn = game.save.soundOn
        val vibrationOn = game.save.vibrationOn
        pill(canvas, soundButton, if (soundOn) "SOUND" else "MUTED", accent, dim = !soundOn, slashed = !soundOn)
        pill(canvas, vibrationButton, if (vibrationOn) "VIBRATE" else "NO VIBE", accent, dim = !vibrationOn, slashed = !vibrationOn)
        if (game.save.canClaimDaily()) {
            pill(canvas, giftButton, "GIFT!", accent2, highlight = true, breathe = 1f + 0.05f * pulse)
        } else {
            pill(canvas, giftButton, "DAY ${game.save.streak}", accent, dim = true)
        }
        drawGems(canvas)
    }

    private fun drawModeCard(canvas: Canvas, r: RectF, mode: GameMode, index: Int, selected: Boolean, pulse: Float) {
        val accent = Palette.accent(game.theme)
        val accent2 = Palette.accent2(game.theme)
        canvas.save()
        val s = pressScale(r) * (if (selected) 1f + 0.015f * pulse else 1f)
        canvas.scale(s, s, r.centerX(), r.centerY())

        val radius = u * 4f
        neon.panel(canvas, r, radius, 150)
        neon.roundRect(
            canvas, r, radius, if (selected) accent else accent2, u * 0.45f,
            if (selected) (190 + 65 * pulse).toInt() else 110,
            fillAlpha = if (selected) 35 else 0,
        )

        val iconY = r.top + r.height() * 0.36f
        if (index == 0) stackIcon(canvas, r.centerX(), iconY, u * 6f, accent2)
        else craneIcon(canvas, r.centerX(), iconY, u * 6f, accent2)

        neon.glowText(canvas, mode.title, r.centerX(), r.top + r.height() * 0.73f, u * 6.5f, Color.WHITE, accent)
        neon.text(canvas, "BEST ${game.save.best(mode.id)}", r.centerX(), r.top + r.height() * 0.89f, u * 3.6f, Color.WHITE, 170)
        canvas.restore()
    }

    private fun drawThemes(canvas: Canvas) {
        fill.color = Color.BLACK
        fill.alpha = 200
        canvas.drawRect(0f, 0f, w, h, fill)

        val accent = Palette.accent(game.theme)
        neon.glowText(canvas, "THEMES", w / 2f, h * 0.11f, u * 10f, Color.WHITE, accent)
        drawGems(canvas)

        val radius = u * 3f
        for ((i, theme) in Palette.themes.withIndex()) {
            val r = themeRows[i]
            val selected = theme.id == game.theme.id
            val owned = game.save.isUnlocked(theme.id)
            val rowAccent = Palette.accent(theme)

            neon.panel(canvas, r, radius, 130)
            neon.roundRect(canvas, r, radius, rowAccent, u * 0.35f, if (selected) 255 else 90,
                fillAlpha = if (selected) 30 else 0)

            val size = r.height() * 0.3f
            var sx = r.left + u * 4f
            val sy = r.centerY() - size / 2f
            for (k in 0 until 5) {
                swatch.set(sx, sy, sx + size, sy + size)
                neon.roundRect(canvas, swatch, u * 0.8f, Palette.blockColor(theme, k * 3), u * 0.25f, 230, fillAlpha = 90)
                sx += size + u * 1.2f
            }

            val baseline = r.centerY() + u * 1.8f
            neon.glowText(canvas, theme.name, sx + u * 2.5f, baseline, u * 5.2f, Color.WHITE, rowAccent,
                align = Paint.Align.LEFT)
            val status = when {
                selected -> "IN USE"
                owned -> "OWNED"
                else -> null
            }
            if (status != null) {
                neon.text(canvas, status, r.right - u * 4f, baseline, u * 3.8f, Color.WHITE, 190, Paint.Align.RIGHT)
            } else {
                val affordable = game.save.gems >= theme.cost
                neon.text(canvas, "${theme.cost}", r.right - u * 10f, baseline, u * 4.6f,
                    if (affordable) Neon.GOLD else Color.WHITE, if (affordable) 255 else 140, Paint.Align.RIGHT)
                neon.gem(canvas, r.right - u * 6.5f, r.centerY(), u * 2.2f, if (affordable) 255 else 140)
            }
        }

        pill(canvas, backButton, "BACK", accent)
    }

    private fun drawPlaying(canvas: Canvas) {
        val accent = Palette.accent(game.theme)
        val accent2 = Palette.accent2(game.theme)
        neon.glowText(canvas, "${game.mode.score}", w / 2f, h * 0.11f, u * 15f * (1f + 0.25f * scorePop), Color.WHITE, accent)

        if (comboTime > 0f) {
            val alpha = (255 * min(1f, comboTime / 0.4f)).toInt()
            val pop = 1f + 0.35f * max(0f, comboTime - (COMBO_TIME - 0.2f)) / 0.2f
            neon.glowText(canvas, comboLabel, w / 2f, h * 0.215f, u * 6f * pop, Color.WHITE, accent2, alpha)
        }
        game.mode.drawHud(canvas, neon, u, w, h)
        drawGems(canvas)
    }

    private fun drawGameOver(canvas: Canvas) {
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
        neon.chroma(canvas, headline, w / 2f, top, u * 9.5f, game.time, accent)
        neon.text(canvas, mode.endReason, w / 2f, top + u * 6.5f, u * 3.8f, Color.WHITE, 170)
        neon.glowText(canvas, "${mode.score}", w / 2f, top + u * 25f, u * 18f, Color.WHITE, accent)

        if (game.newBest) {
            neon.glowText(canvas, "NEW BEST!", w / 2f, top + u * 34f, u * 7f * (1f + 0.06f * pulse), Color.WHITE, Neon.GOLD)
        } else {
            neon.text(canvas, "BEST $best", w / 2f, top + u * 33f, u * 5f, Color.WHITE, 200)
        }

        val gems = "+${game.gemsEarned}"
        val tw = neon.measure(gems, u * 6f)
        neon.glowText(canvas, gems, w / 2f - u * 2.5f, top + u * 43f, u * 6f, Color.WHITE, Neon.GEM)
        neon.gem(canvas, w / 2f - u * 2.5f + tw / 2f + u * 4f, top + u * 41f, u * 2.6f)

        if (mode.bestCombo > 1) {
            neon.text(canvas, "BEST COMBO x${mode.bestCombo}", w / 2f, top + u * 50f, u * 4f, Color.WHITE, 180)
        }

        if (t > Game.RESTART_DELAY) {
            pill(canvas, retryButton, "RETRY", accent2, highlight = true, breathe = 1f + 0.04f * pulse)
            pill(canvas, menuButton, "MENU", accent)
        }
        drawGems(canvas)
    }

    private fun drawBanner(canvas: Canvas) {
        if (bannerTime <= 0f) return
        val a = min(1f, bannerTime / 0.4f)
        val size = u * 4.6f
        val tw = neon.measure(banner, size)
        val cy = h * 0.53f
        bannerRect.set(w / 2f - tw / 2f - u * 6f, cy - u * 6f, w / 2f + tw / 2f + u * 6f, cy + u * 6f)
        val accent = Palette.accent(game.theme)
        neon.panel(canvas, bannerRect, u * 6f, (200 * a).toInt())
        neon.roundRect(canvas, bannerRect, u * 6f, accent, u * 0.35f, (230 * a).toInt())
        neon.glowText(canvas, banner, w / 2f, cy + size * 0.36f, size, Color.WHITE, accent, (255 * a).toInt())
    }

    // endregion

    // region Widgets

    private fun pill(
        canvas: Canvas, r: RectF, title: String, color: Int,
        highlight: Boolean = false, dim: Boolean = false, breathe: Float = 1f, slashed: Boolean = false,
    ) {
        canvas.save()
        val s = pressScale(r) * breathe
        canvas.scale(s, s, r.centerX(), r.centerY())
        val radius = r.height() / 2f
        neon.panel(canvas, r, radius, 160)

        var size = u * 4.4f
        val room = r.width() - u * 4f
        val tw = neon.measure(title, size)
        if (tw > room) size *= room / tw
        val baseline = r.centerY() + size * 0.36f

        if (highlight) {
            neon.roundRect(canvas, r, radius, color, u * 0.45f, 255, fillAlpha = 210)
            neon.text(canvas, title, r.centerX(), baseline, size, DARK)
        } else {
            neon.roundRect(canvas, r, radius, color, u * 0.4f, if (dim) 110 else 230)
            neon.glowText(canvas, title, r.centerX(), baseline, size, Color.WHITE, color, if (dim) 150 else 255)
        }
        if (slashed) {
            val half = neon.measure(title, size) / 2f + u
            neon.line(canvas, r.centerX() - half, r.centerY() + size * 0.1f, r.centerX() + half, r.centerY() - size * 0.25f,
                color, u * 0.25f, 170)
        }
        canvas.restore()
    }

    private fun pressScale(r: RectF): Float {
        if (pressed !== r || pressTime <= 0f) return 1f
        val t = pressTime / PRESS_TIME
        return 1f - 0.08f * sin(t * Math.PI.toFloat())
    }

    private fun stackIcon(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        for (k in 0 until 3) {
            val y = cy + s * 0.5f - k * s * 0.42f
            val dx = if (k == 2) s * 0.25f else 0f
            icon.reset()
            icon.moveTo(cx - s + dx, y)
            icon.lineTo(cx + dx, y - s * 0.5f)
            icon.lineTo(cx + s + dx, y)
            icon.lineTo(cx + dx, y + s * 0.5f)
            icon.close()
            neon.path(canvas, icon, color, u * 0.35f, 230)
        }
    }

    private fun craneIcon(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        val beam = cy - s * 0.95f
        neon.line(canvas, cx - s * 1.3f, beam, cx + s * 1.3f, beam, color, u * 0.35f, 230)
        neon.line(canvas, cx + s * 0.15f, beam, cx + s * 0.35f, cy - s * 0.15f, color, u * 0.25f, 200)
        swatch.set(cx - s * 0.2f, cy - s * 0.15f, cx + s * 0.9f, cy + s * 0.45f)
        neon.roundRect(canvas, swatch, u * 0.5f, color, u * 0.3f, 230, fillAlpha = 50)
        swatch.set(cx - s * 0.75f, cy + s * 0.5f, cx + s * 0.35f, cy + s * 1.1f)
        neon.roundRect(canvas, swatch, u * 0.5f, color, u * 0.3f, 160)
    }

    private fun drawGems(canvas: Canvas) {
        val y = h * 0.06f
        neon.gem(canvas, w - u * 7f, y - u * 1.6f, u * 2.6f)
        neon.glowText(canvas, "${game.save.gems}", w - u * 11.5f, y, u * 5f, Color.WHITE, Neon.GEM,
            align = Paint.Align.RIGHT)
    }

    // endregion

    private companion object {
        const val COMBO_TIME = 1f
        const val BANNER_TIME = 2.5f
        const val PRESS_TIME = 0.18f
        const val DARK = 0xFF14121F.toInt()
    }
}
