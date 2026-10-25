package com.stackdrop.game.ui.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.stackdrop.game.game.Game
import com.stackdrop.game.game.GameMode
import com.stackdrop.game.game.Palette
import com.stackdrop.game.ui.Avatars
import com.stackdrop.game.ui.Neon
import com.stackdrop.game.ui.Widgets
import kotlin.math.max
import kotlin.math.sin

class MenuScreen(
    private val game: Game,
    private val neon: Neon,
    private val widgets: Widgets,
    private val avatars: Avatars,
) : Screen {

    private val themesButton = RectF()
    private val ranksButton = RectF()
    private val friendsButton = RectF()
    private val giftButton = RectF()
    private val settingsButton = RectF()
    private val profileChip = RectF()
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
        val gapX = u * 2f
        val bw = (u * 92f - gapX * 3f) / 4f
        var bx = u * 4f
        for (b in listOf(themesButton, ranksButton, friendsButton, giftButton)) {
            b.set(bx, row, bx + bw, row + bh)
            bx += bw + gapX
        }

        val gearY = widgets.topRowY() - u * 1.6f
        settingsButton.set(w - u * 11f, gearY - u * 4.5f, w - u * 2f, gearY + u * 4.5f)
        val chipY = maxOf(h * 0.045f, widgets.topInset + u * 4.5f)
        profileChip.set(u * 3f, chipY - u * 3.5f, u * 34f, chipY + u * 3.5f)

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
            ranksButton.contains(x, y) -> {
                game.press(ranksButton)
                game.openLeaderboard()
            }
            friendsButton.contains(x, y) -> {
                game.press(friendsButton)
                game.openFriends()
            }
            widgets.hit(settingsButton, x, y) -> {
                game.press(settingsButton)
                game.openSettings()
            }
            widgets.hit(profileChip, x, y) -> {
                game.press(profileChip)
                game.openProfile()
            }
            giftButton.contains(x, y) -> {
                game.press(giftButton)
                game.claimDaily()
            }
            // A tap on the background starts a run, but one that just missed a button doesn't.
            widgets.near(x, y, u * Widgets.NEAR_MISS, themesButton, ranksButton, friendsButton, giftButton, settingsButton,
                profileChip, modeCards[0], modeCards[1]) -> Unit
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
        val pulse = if (game.reduceMotion) 0.5f else 0.5f + 0.5f * sin(t * 4f)
        // Reduce motion keeps the title still.
        val titleTime = if (game.reduceMotion) 0f else t

        // The title's top stays clear of the gem counter and gear row, however deep the cutout.
        val titleY = max(h * 0.12f + u * 3f, widgets.topRowY() + TITLE_GAP * u)
        neon.chroma(canvas, "STACK", w / 2f, titleY, u * 17f, titleTime, accent)
        neon.chroma(canvas, "DROP", w / 2f, titleY + u * 15f, u * 17f, titleTime + 0.7f, accent)
        neon.text(canvas, "ARCADE TOWERS", w / 2f, titleY + u * 22f, u * 3.6f, neon.textColor, 150)

        for ((i, m) in game.modes.withIndex()) drawModeCard(canvas, modeCards[i], m, i, m === game.mode, pulse)

        widgets.pill(canvas, themesButton, "THEMES", accent)
        widgets.pill(canvas, ranksButton, "RANKS", accent)
        widgets.pill(canvas, friendsButton, "FRIENDS", accent)
        if (game.hasFriendRequests) {
            neon.dot(canvas, friendsButton.right - u * 2f, friendsButton.top + u * 1.5f, u * 1.1f, accent2)
        }
        if (game.save.canClaimDaily()) {
            widgets.pill(canvas, giftButton, "GIFT!", accent2, highlight = true, breathe = 1f + 0.05f * pulse)
        } else {
            widgets.pill(canvas, giftButton, "DAY ${game.save.streak}", accent, dim = true)
        }
        widgets.gear(canvas, settingsButton, accent)
        widgets.gems(canvas, game.save.gems, inset = u * GEMS_INSET)
        drawProfileChip(canvas, accent2)
    }

    /** Avatar and name; "Guest" before a name is chosen, with a small dot inviting Google sign-in. */
    private fun drawProfileChip(canvas: Canvas, accent2: Int) {
        val r = profileChip
        canvas.save()
        val s = widgets.pressScale(r)
        canvas.scale(s, s, r.centerX(), r.centerY())
        neon.panel(canvas, r, r.height() / 2f, 130)
        val ar = r.height() * 0.4f
        val ax = r.left + r.height() / 2f
        avatars.draw(canvas, game.avatar, ax, r.centerY(), ar)
        val name = game.profile?.name ?: "Guest"
        val nameX = ax + ar + u * 1.8f
        neon.text(canvas, name, nameX, r.centerY() + u * 1.2f, neon.fit(name, u * 3.4f, r.right - u * 2f - nameX),
            neon.textColor, 230, Paint.Align.LEFT)
        if (game.onlineAvailable && game.account.anonymous) {
            neon.dot(canvas, ax + ar * 0.8f, r.centerY() - ar * 0.8f, u * 0.7f, accent2)
        }
        canvas.restore()
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

    private companion object {
        /** From the top row of controls down to the title's baseline: the title's cap height plus air. */
        const val TITLE_GAP = 19f
        /** How far left of the screen edge the gem counter sits, leaving room beside the gear. */
        const val GEMS_INSET = 15f
    }
}
