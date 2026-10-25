package com.stackdrop.game.ui.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.stackdrop.game.game.Game
import com.stackdrop.game.game.Palette
import com.stackdrop.game.ui.Avatars
import com.stackdrop.game.ui.Neon
import com.stackdrop.game.ui.Widgets

/** Avatar, name, friend code, bests, account status, and account deletion. */
class ProfileScreen(
    private val game: Game,
    private val neon: Neon,
    private val widgets: Widgets,
    private val avatars: Avatars,
) : Screen {

    private val avatarRect = RectF()
    private val nameRect = RectF()
    private val choosePill = RectF()
    private val copyButton = RectF()
    private val shareButton = RectF()
    private val accountPanel = RectF()
    private val accountButton = RectF()
    private val deleteButton = RectF()
    private val backButton = RectF()
    private val gArc = RectF()
    private val veil = Paint()
    private val gPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private var w = 0f
    private var h = 0f
    private var u = 1f
    private var avatarY = 0f
    private var avatarR = 0f

    override fun resize(w: Float, h: Float, u: Float) {
        this.w = w
        this.h = h
        this.u = u
        avatarY = h * 0.19f
        avatarR = u * 11f
        avatarRect.set(w / 2f - avatarR, avatarY - avatarR, w / 2f + avatarR, avatarY + avatarR)
        val nameY = h * 0.3f
        // Starts below the avatar's "TAP TO CHANGE" label, so tapping the label never opens rename.
        val nameTop = maxOf(nameY - u * 7f, avatarY + avatarR + u * 5.5f)
        nameRect.set(u * 10f, nameTop, u * 90f, maxOf(nameY + u * 4f, nameTop + u * 8f))
        choosePill.set(u * 22f, nameRect.top, u * 78f, nameRect.bottom)
        val codeY = h * 0.385f
        copyButton.set(u * 20f, codeY + u * 9f, u * 48f, codeY + u * 18f)
        shareButton.set(u * 52f, codeY + u * 9f, u * 80f, codeY + u * 18f)
        val panelTop = h * 0.61f
        accountPanel.set(u * 7f, panelTop, u * 93f, panelTop + u * 27f)
        accountButton.set(u * 12f, panelTop + u * 14f, u * 88f, panelTop + u * 24f)
        val row = h * 0.88f
        // Below the account panel, and above BACK, even on short screens.
        val deleteY = minOf(maxOf(h * 0.8f, accountPanel.bottom + u * 2f), row - u * 10f)
        deleteButton.set(u * 28f, deleteY, u * 72f, deleteY + u * 8f)
        backButton.set(u * 30f, row, u * 70f, row + u * 11f)
    }

    override fun onTap(x: Float, y: Float): Boolean {
        val hasProfile = game.profile != null
        val online = game.onlineAvailable
        when {
            backButton.contains(x, y) -> {
                game.press(backButton)
                game.backToMenu()
            }
            avatarRect.contains(x, y) -> {
                game.press(avatarRect)
                game.cycleAvatar()
            }
            nameRect.contains(x, y) && online -> {
                game.press(if (hasProfile) nameRect else choosePill)
                game.editName()
            }
            hasProfile && widgets.hit(copyButton, x, y) -> {
                game.press(copyButton)
                game.copyFriendCode()
            }
            hasProfile && widgets.hit(shareButton, x, y) -> {
                game.press(shareButton)
                game.shareFriendCode()
            }
            online && accountButton.contains(x, y) -> {
                game.press(accountButton)
                if (game.account.anonymous) game.signInWithGoogle() else game.signOut()
            }
            online && game.account.uid != null && widgets.hit(deleteButton, x, y) -> {
                game.press(deleteButton)
                game.deleteAccount()
            }
        }
        return true
    }

    override fun onBack(): Boolean {
        game.backToMenu()
        return true
    }

    override fun draw(canvas: Canvas) {
        val theme = game.theme
        val accent = Palette.accent(theme)
        val accent2 = Palette.accent2(theme)
        val text = neon.textColor
        val dim = theme.textDim
        val profile = game.profile
        val online = game.onlineAvailable

        // Veil so the idle game scene behind never runs through the text.
        veil.color = theme.panel
        veil.alpha = VEIL
        canvas.drawRect(0f, 0f, w, h, veil)

        neon.glowText(canvas, "PROFILE", w / 2f, h * 0.085f, u * 8f, text, accent)

        canvas.save()
        val s = widgets.pressScale(avatarRect)
        canvas.scale(s, s, avatarRect.centerX(), avatarRect.centerY())
        avatars.draw(canvas, game.avatar, w / 2f, avatarY, avatarR)
        canvas.restore()
        neon.text(canvas, "TAP TO CHANGE", w / 2f, avatarY + avatarR + u * 4f, u * 2.8f, dim, 200)

        val nameY = nameRect.bottom - u * 4f
        when {
            game.nameBusy -> neon.text(canvas, "Saving…", w / 2f, nameY, u * 5f, dim, 230)
            profile != null -> {
                neon.glowText(canvas, profile.name, w / 2f, nameY, neon.fit(profile.name, u * 7f, w - u * 16f), text, accent)
                neon.text(canvas, "tap to edit", w / 2f, nameY + u * 4f, u * 2.8f, dim, 200)
            }
            online -> widgets.pill(canvas, choosePill, "CHOOSE A NAME", accent2, highlight = true)
            else -> neon.glowText(canvas, "Guest", w / 2f, nameY, u * 7f, text, accent)
        }

        val codeY = h * 0.385f
        if (profile != null) {
            neon.text(canvas, "FRIEND CODE", w / 2f, codeY, u * 3f, dim, 220)
            neon.glowText(canvas, profile.displayCode, w / 2f, codeY + u * 6.5f, u * 6f, text, accent2)
            widgets.pill(canvas, copyButton, "COPY", accent)
            widgets.pill(canvas, shareButton, "SHARE", accent)
        } else if (online) {
            neon.text(canvas, "Choose a name to get a friend code", w / 2f, codeY + u * 4f, u * 3.2f, dim, 220)
        }

        val bestsY = h * 0.565f
        neon.text(canvas, "STACK  ${game.save.best("stack")}", w * 0.3f, bestsY, u * 4.4f, text, 230)
        neon.text(canvas, "TOWER  ${game.save.best("tower")}", w * 0.7f, bestsY, u * 4.4f, text, 230)

        drawAccount(canvas, accent, accent2, text, dim, online)

        if (online && game.account.uid != null) {
            val danger = theme.danger
            canvas.save()
            val ds = widgets.pressScale(deleteButton)
            canvas.scale(ds, ds, deleteButton.centerX(), deleteButton.centerY())
            neon.roundRect(canvas, deleteButton, deleteButton.height() / 2f, danger, u * 0.3f, 110)
            neon.text(canvas, "DELETE ACCOUNT", deleteButton.centerX(), deleteButton.centerY() + u * 1.1f, u * 3.2f,
                danger, 190)
            canvas.restore()
        }

        widgets.pill(canvas, backButton, "BACK", accent)
        widgets.gems(canvas, game.save.gems)
    }

    private fun drawAccount(canvas: Canvas, accent: Int, accent2: Int, text: Int, dim: Int, online: Boolean) {
        val r = accountPanel
        neon.panel(canvas, r, u * 3f, 150)
        neon.roundRect(canvas, r, u * 3f, accent, u * 0.3f, 80)
        val left = r.left + u * 5f
        if (!online) {
            neon.text(canvas, "Offline", left, r.top + u * 6.5f, u * 4.2f, text, 235, Paint.Align.LEFT)
            neon.text(canvas, "Online features are not available", left, r.top + u * 11f, u * 3f, dim, 220,
                Paint.Align.LEFT)
            return
        }
        val account = game.account
        if (account.anonymous) {
            neon.text(canvas, "Guest account", left, r.top + u * 6.5f, u * 4.2f, text, 235, Paint.Align.LEFT)
            neon.text(canvas, "Progress is only on this phone", left, r.top + u * 11f, u * 3f, dim, 220, Paint.Align.LEFT)
            widgets.pill(canvas, accountButton, "    SIGN IN WITH GOOGLE", accent2, highlight = true)
            googleMark(canvas, accountButton.left + u * 7f, accountButton.centerY(), u * 2.6f)
        } else {
            neon.text(canvas, "Signed in with Google", left, r.top + u * 6.5f, u * 4.2f, text, 235, Paint.Align.LEFT)
            neon.text(canvas, account.email ?: "", left, r.top + u * 11f, u * 3f, dim, 200, Paint.Align.LEFT)
            widgets.pill(canvas, accountButton, "SIGN OUT", accent)
        }
    }

    /**
     * A drawn approximation of Google's four-color "G". For strict branding compliance replace it
     * with the official asset from Google's sign-in branding guidelines.
     */
    private fun googleMark(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        gPaint.strokeWidth = r * 0.42f
        gArc.set(cx - r, cy - r, cx + r, cy + r)
        gPaint.color = G_RED
        canvas.drawArc(gArc, 200f, 110f, false, gPaint)
        gPaint.color = G_YELLOW
        canvas.drawArc(gArc, 150f, 50f, false, gPaint)
        gPaint.color = G_GREEN
        canvas.drawArc(gArc, 40f, 110f, false, gPaint)
        gPaint.color = G_BLUE
        canvas.drawArc(gArc, -20f, 60f, false, gPaint)
        canvas.drawLine(cx, cy, cx + r * 1.2f, cy, gPaint)
    }

    private companion object {
        const val VEIL = 170
        const val G_BLUE = 0xFF4285F4.toInt()
        const val G_RED = 0xFFEA4335.toInt()
        const val G_YELLOW = 0xFFFBBC05.toInt()
        const val G_GREEN = 0xFF34A853.toInt()
    }
}
