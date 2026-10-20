package com.stackdrop.game.ui.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.stackdrop.game.BuildConfig
import com.stackdrop.game.game.Game
import com.stackdrop.game.game.Palette
import com.stackdrop.game.ui.Links
import com.stackdrop.game.ui.Neon
import com.stackdrop.game.ui.Widgets
import kotlin.math.min

class SettingsScreen(private val game: Game, private val neon: Neon, private val widgets: Widgets) : Screen {

    private enum class Row(val label: String) {
        PREMIUM("Premium"),
        SOUND("Sound"),
        VIBRATION("Vibration"),
        REDUCE_MOTION("Reduce motion"),
        PRIVACY_OPTIONS("Privacy options"),
        PRIVACY_POLICY("Privacy policy"),
        DATA_DELETION("Delete my data"),
        VERSION("Version"),
    }

    private val allRows = Row.entries
    private val withoutOptions = Row.entries.filter { it != Row.PRIVACY_OPTIONS }

    /** Privacy options appear only where the consent rules require them. */
    private val rows get() = if (game.privacyOptionsRequired) allRows else withoutOptions
    private val rects = List(Row.entries.size) { RectF() }
    private val backButton = RectF()
    private val veil = Paint()

    private var w = 0f
    private var h = 0f
    private var u = 1f

    override fun resize(w: Float, h: Float, u: Float) {
        this.w = w
        this.h = h
        this.u = u
        val rowH = min(h * 0.07f, u * 12.5f)
        val gap = u * 2f
        var y = h * 0.2f
        for (r in rects) {
            r.set(u * 7f, y, u * 93f, y + rowH)
            y += rowH + gap
        }
        val row = h * 0.88f
        backButton.set(u * 30f, row, u * 70f, row + u * 11f)
    }

    override fun onTap(x: Float, y: Float): Boolean {
        if (backButton.contains(x, y)) {
            game.press(backButton)
            game.backToMenu()
            return true
        }
        val visible = rows
        val i = rects.indexOfFirst { it.contains(x, y) }
        if (i < 0 || i >= visible.size) return true
        val row = visible[i]
        if (row == Row.VERSION) return true
        if (row == Row.VIBRATION) widgets.press(rects[i]) else game.press(rects[i])
        when (row) {
            Row.PREMIUM -> game.buyPremium()
            Row.SOUND -> game.toggleSound()
            Row.VIBRATION -> game.toggleVibration()
            Row.REDUCE_MOTION -> game.toggleReduceMotion()
            Row.PRIVACY_OPTIONS -> game.showPrivacyOptions()
            Row.PRIVACY_POLICY -> game.bridge.openUrl(Links.PRIVACY_POLICY)
            Row.DATA_DELETION -> game.bridge.openUrl(Links.ACCOUNT_DELETION)
            Row.VERSION -> Unit
        }
        return true
    }

    override fun onBack(): Boolean {
        game.backToMenu()
        return true
    }

    override fun draw(canvas: Canvas) {
        val accent = Palette.accent(game.theme)
        veil.color = game.theme.panel
        veil.alpha = VEIL
        canvas.drawRect(0f, 0f, w, h, veil)
        neon.glowText(canvas, "SETTINGS", w / 2f, h * 0.11f, u * 10f, neon.textColor, accent)

        val radius = u * 3f
        for ((i, row) in rows.withIndex()) {
            val r = rects[i]
            canvas.save()
            val s = widgets.pressScale(r)
            canvas.scale(s, s, r.centerX(), r.centerY())
            neon.panel(canvas, r, radius, 150)
            neon.roundRect(canvas, r, radius, accent, u * 0.3f, 90)

            val baseline = r.centerY() + u * 1.6f
            if (row == Row.PREMIUM) {
                // What premium includes, under its name.
                neon.text(canvas, row.label, r.left + u * 5f, r.centerY() - u * 0.2f, u * 4.6f, neon.textColor, 235,
                    Paint.Align.LEFT)
                neon.text(canvas, PREMIUM_PERKS, r.left + u * 5f, r.centerY() + u * 3.6f, u * 2.8f,
                    game.theme.textDim, 230, Paint.Align.LEFT)
            } else {
                neon.text(canvas, row.label, r.left + u * 5f, baseline, u * 4.6f, neon.textColor, 235, Paint.Align.LEFT)
            }
            val value = when (row) {
                Row.PREMIUM -> if (game.premium) "ACTIVE" else game.premiumPrice ?: "GET"
                Row.SOUND -> onOff(game.save.soundOn)
                Row.VIBRATION -> onOff(game.save.vibrationOn)
                Row.REDUCE_MOTION -> onOff(game.reduceMotion)
                Row.PRIVACY_OPTIONS, Row.PRIVACY_POLICY, Row.DATA_DELETION -> "OPEN"
                Row.VERSION -> BuildConfig.VERSION_NAME
            }
            val on = value == ON || value == "OPEN" || row == Row.PREMIUM
            if (row == Row.VERSION) {
                neon.text(canvas, value, r.right - u * 5f, baseline, u * 4f, neon.textColor, 170, Paint.Align.RIGHT)
            } else {
                neon.glowText(canvas, value, r.right - u * 5f, baseline, u * 4f, if (on) accent else neon.textColor, accent,
                    if (on) 255 else 150, Paint.Align.RIGHT)
            }
            canvas.restore()
        }

        widgets.pill(canvas, backButton, "BACK", accent)
    }

    private fun onOff(v: Boolean) = if (v) ON else OFF

    private companion object {
        const val VEIL = 170
        const val PREMIUM_PERKS = "No ads · 2× gems · Gilded theme"
        const val ON = "ON"
        const val OFF = "OFF"
    }
}
