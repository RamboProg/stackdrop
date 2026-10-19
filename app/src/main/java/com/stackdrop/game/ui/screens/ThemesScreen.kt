package com.stackdrop.game.ui.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.stackdrop.game.game.Game
import com.stackdrop.game.game.Palette
import com.stackdrop.game.ui.Neon
import com.stackdrop.game.ui.Widgets
import kotlin.math.min

class ThemesScreen(private val game: Game, private val neon: Neon, private val widgets: Widgets) : Screen {

    override val tapsOnUp = true

    private val backButton = RectF()
    private val themeRows = List(Palette.themes.size) { RectF() }
    private val swatch = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    private var w = 0f
    private var h = 0f
    private var u = 1f

    override fun resize(w: Float, h: Float, u: Float) {
        this.w = w
        this.h = h
        this.u = u

        val row = h * 0.88f
        backButton.set(u * 30f, row, u * 70f, row + u * 11f)

        val rowH = min(h * 0.095f, u * 17f)
        val gap = u * 2.5f
        var y = h * 0.17f
        for (r in themeRows) {
            r.set(u * 7f, y, u * 93f, y + rowH)
            y += rowH + gap
        }
    }

    override fun onTap(x: Float, y: Float): Boolean {
        if (backButton.contains(x, y)) {
            game.press(backButton)
            game.backToMenu()
            return true
        }
        val row = themeRows.indexOfFirst { it.contains(x, y) }
        if (row >= 0) game.selectTheme(row)
        return true
    }

    override fun onBack(): Boolean {
        game.backToMenu()
        return true
    }

    override fun draw(canvas: Canvas) {
        fill.color = Color.BLACK
        fill.alpha = 200
        canvas.drawRect(0f, 0f, w, h, fill)

        val accent = Palette.accent(game.theme)
        neon.glowText(canvas, "THEMES", w / 2f, h * 0.11f, u * 10f, Color.WHITE, accent)
        widgets.gems(canvas, game.save.gems)

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

        widgets.pill(canvas, backButton, "BACK", accent)
    }
}
