package com.stackdrop.game.ui.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.stackdrop.game.game.Family
import com.stackdrop.game.game.Game
import com.stackdrop.game.game.Palette
import com.stackdrop.game.game.Theme
import com.stackdrop.game.game.ThemeRules
import com.stackdrop.game.ui.Neon
import com.stackdrop.game.ui.Widgets
import kotlin.math.max
import kotlin.math.min

/** CALM | NEON tabs over a scrollable list; tapping a locked theme previews it live behind the list. */
class ThemesScreen(private val game: Game, private val neon: Neon, private val widgets: Widgets) : Screen {

    override val tapsOnUp = true

    private val backButton = RectF()
    private val tabRects = List(2) { RectF() }
    private val row = RectF()
    private val swatch = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hints = HashMap<String, String>()

    private var family = Family.CALM
    private var scroll = 0f

    private var w = 0f
    private var h = 0f
    private var u = 1f
    private var rowH = 0f
    private var gap = 0f
    private var listTop = 0f
    private var listBottom = 0f

    init {
        for (t in Palette.themes) t.milestone?.let { hints[t.id] = ThemeRules.milestoneHint(it) }
    }

    override fun resize(w: Float, h: Float, u: Float) {
        this.w = w
        this.h = h
        this.u = u
        rowH = min(h * 0.082f, u * 15f)
        gap = u * 1.6f
        listTop = h * 0.2f
        listBottom = h * 0.84f

        val tabTop = h * 0.135f
        tabRects[0].set(u * 20f, tabTop, u * 49f, tabTop + u * 7.5f)
        tabRects[1].set(u * 51f, tabTop, u * 80f, tabTop + u * 7.5f)

        val bottom = h * 0.88f
        backButton.set(u * 30f, bottom, u * 70f, bottom + u * 11f)
        scroll = scroll.coerceIn(0f, maxScroll())
    }

    override fun onEnter() {
        family = Family.CALM
        scroll = 0f
    }

    override fun onDrag(dy: Float) {
        scroll = (scroll - dy).coerceIn(0f, maxScroll())
    }

    override fun onTap(x: Float, y: Float): Boolean {
        if (backButton.contains(x, y)) {
            game.press(backButton)
            game.closeThemes()
            return true
        }
        for (i in tabRects.indices) {
            if (!tabRects[i].contains(x, y)) continue
            val f = if (i == 0) Family.CALM else Family.NEON
            if (f != family) {
                family = f
                scroll = 0f
                game.press(tabRects[i])
                game.click()
            }
            return true
        }
        if (y < listTop || y > listBottom || x < u * 7f || x > u * 93f) return true
        val pos = y - listTop + scroll
        val index = (pos / (rowH + gap)).toInt()
        val list = Palette.of(family)
        if (index in list.indices && pos - index * (rowH + gap) <= rowH) game.selectTheme(list[index])
        return true
    }

    override fun onBack(): Boolean {
        game.closeThemes()
        return true
    }

    override fun draw(canvas: Canvas) {
        val theme = game.theme
        // A light veil keeps rows readable while the previewed theme stays visible behind them.
        fill.color = theme.panel
        fill.alpha = VEIL
        canvas.drawRect(0f, 0f, w, h, fill)

        val accent = Palette.accent(theme)
        neon.glowText(canvas, "THEMES", w / 2f, h * 0.11f, u * 10f, neon.textColor, accent)
        widgets.gems(canvas, game.save.gems)
        widgets.tabs(canvas, tabRects, TAB_LABELS, if (family == Family.CALM) 0 else 1, accent)

        val list = Palette.of(family)
        canvas.save()
        canvas.clipRect(0f, listTop - u * 1.5f, w, listBottom + u * 1.5f)
        for (i in list.indices) {
            val top = listTop + i * (rowH + gap) - scroll
            if (top + rowH < listTop - u || top > listBottom + u) continue
            row.set(u * 7f, top, u * 93f, top + rowH)
            drawRow(canvas, list[i])
        }
        canvas.restore()

        widgets.pill(canvas, backButton, "BACK", accent)
    }

    private fun drawRow(canvas: Canvas, t: Theme) {
        val current = game.theme
        val shown = t.id == current.id
        val previewing = shown && game.previewing
        val inUse = shown && !previewing
        val owned = game.save.isUnlocked(t.id)
        val freeToday = !owned && game.freeToday?.id == t.id
        val text = neon.textColor
        val radius = u * 3f

        neon.panel(canvas, row, radius, 140)
        neon.roundRect(canvas, row, radius, t.accent, u * 0.35f, if (shown) 255 else 90, fillAlpha = if (shown) 30 else 0)

        val size = row.height() * 0.28f
        var sx = row.left + u * 3.5f
        val sy = row.centerY() - size / 2f
        for (k in 0 until 5) {
            swatch.set(sx, sy, sx + size, sy + size)
            neon.roundRect(canvas, swatch, u * 0.8f, Palette.blockColor(t, k * 3), u * 0.25f, 230, fillAlpha = 150)
            sx += size + u * 1f
        }

        val nameX = sx + u * 2f
        neon.glowText(canvas, t.name, nameX, row.centerY() + u * 0.2f, u * 4.8f, text, t.accent, align = Paint.Align.LEFT)
        if (previewing) {
            widgets.chip(canvas, "PREVIEW", nameX, row.centerY() + u * 3.4f, current.accent2)
        } else {
            neon.text(canvas, t.mood, nameX, row.centerY() + u * 4.4f, u * 3f, current.textDim, 230, Paint.Align.LEFT)
        }

        val right = row.right - u * 4f
        when {
            inUse -> neon.text(canvas, "IN USE", right, row.centerY() + u * 1.4f, u * 3.6f, text, 200, Paint.Align.RIGHT)
            owned -> neon.text(canvas, "OWNED", right, row.centerY() + u * 1.4f, u * 3.6f, text, 170, Paint.Align.RIGHT)
            freeToday -> widgets.chip(canvas, "FREE TODAY", right, row.centerY(), current.accent2, alignRight = true)
            else -> drawPrice(canvas, t, right)
        }
    }

    private fun drawPrice(canvas: Canvas, t: Theme, right: Float) {
        val affordable = game.save.gems >= t.cost
        val hint = hints[t.id]
        val y = if (hint != null) row.centerY() - u * 0.6f else row.centerY() + u * 1.6f
        val a = if (affordable) 255 else 150
        neon.text(canvas, "${t.cost}", right - u * 6f, y, u * 4.4f, if (affordable) Neon.GOLD else neon.textColor, a,
            Paint.Align.RIGHT)
        neon.gem(canvas, right - u * 2.5f, y - u * 1.5f, u * 2f, a)
        if (hint != null) {
            neon.text(canvas, hint, right, row.centerY() + u * 4.2f, u * 2.7f, game.theme.textDim, 220, Paint.Align.RIGHT)
        }
    }

    private fun maxScroll(): Float {
        val n = Palette.of(family).size
        return max(0f, n * (rowH + gap) - gap - (listBottom - listTop))
    }

    private companion object {
        const val VEIL = 120
        val TAB_LABELS = arrayOf("CALM", "NEON")
    }
}
