package com.stackdrop.game.ui.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.stackdrop.game.game.Game
import com.stackdrop.game.game.Game.BoardTab
import com.stackdrop.game.game.Palette
import com.stackdrop.game.online.BoardEntry
import com.stackdrop.game.online.BoardSnapshot
import com.stackdrop.game.online.IsoWeek
import com.stackdrop.game.ui.Avatars
import com.stackdrop.game.ui.Neon
import com.stackdrop.game.ui.Widgets
import kotlin.math.max
import kotlin.math.min

/**
 * STACK | TOWER over WEEK | ALL TIME | FRIENDS, a scrolling top 50, and the player's own row pinned
 * below. Tapping someone opens a small card with their bests and ADD FRIEND.
 */
class LeaderboardScreen(
    private val game: Game,
    private val neon: Neon,
    private val widgets: Widgets,
    private val avatars: Avatars,
) : Screen {

    override val tapsOnUp = true

    private val modeTabs = List(2) { RectF() }
    private val periodTabs = List(3) { RectF() }
    private val row = RectF()
    private val pinned = RectF()
    private val emptyPill = RectF()
    private val backButton = RectF()
    private val card = RectF()
    private val addButton = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val veil = Paint()

    private var scroll = 0f

    private var w = 0f
    private var h = 0f
    private var u = 1f
    private var rowH = 0f
    private var gap = 0f
    private var infoY = 0f
    private var listTop = 0f
    private var listBottom = 0f

    override fun resize(w: Float, h: Float, u: Float) {
        this.w = w
        this.h = h
        this.u = u
        val modeTop = h * 0.1f
        modeTabs[0].set(u * 20f, modeTop, u * 49f, modeTop + u * 7f)
        modeTabs[1].set(u * 51f, modeTop, u * 80f, modeTop + u * 7f)
        val periodTop = modeTop + u * 9f
        periodTabs[0].set(u * 8f, periodTop, u * 33f, periodTop + u * 7f)
        periodTabs[1].set(u * 35f, periodTop, u * 65f, periodTop + u * 7f)
        periodTabs[2].set(u * 67f, periodTop, u * 92f, periodTop + u * 7f)
        infoY = periodTop + u * 12f
        listTop = infoY + u * 3f
        listBottom = h * 0.79f
        rowH = min(h * 0.065f, u * 12f)
        gap = u * 1.2f
        pinned.set(u * 7f, h * 0.8f, u * 93f, h * 0.8f + rowH)
        val mid = (listTop + listBottom) / 2f
        emptyPill.set(u * 15f, mid - u * 5f, u * 85f, mid + u * 5f)
        val bottom = h * 0.88f
        backButton.set(u * 30f, bottom, u * 70f, bottom + u * 11f)
        card.set(u * 12f, h * 0.32f, u * 88f, h * 0.32f + u * 52f)
        addButton.set(card.left + u * 10f, card.bottom - u * 14f, card.right - u * 10f, card.bottom - u * 4f)
    }

    override fun onEnter() {
        scroll = 0f
    }

    override fun onDrag(dy: Float) {
        if (game.popover == null) scroll = (scroll - dy).coerceIn(0f, maxScroll())
    }

    override fun onTap(x: Float, y: Float): Boolean {
        if (game.popover != null) {
            if (canAddFriend() && addButton.contains(x, y)) {
                game.press(addButton)
                game.addFriendFromPopover()
            } else if (!card.contains(x, y)) {
                game.closePopover()
            }
            return true
        }
        when {
            backButton.contains(x, y) -> {
                game.press(backButton)
                game.backToMenu()
            }
            modeTabs[0].contains(x, y) -> selectMode("stack", modeTabs[0])
            modeTabs[1].contains(x, y) -> selectMode("tower", modeTabs[1])
            periodTabs[0].contains(x, y) -> selectTab(BoardTab.WEEK)
            periodTabs[1].contains(x, y) -> selectTab(BoardTab.ALL_TIME)
            periodTabs[2].contains(x, y) -> selectTab(BoardTab.FRIENDS)
            pinned.contains(x, y) && game.profile == null && game.onlineAvailable -> {
                game.press(pinned)
                game.editName()
            }
            noFriends() && emptyPill.contains(x, y) -> {
                game.press(emptyPill)
                game.openFriends()
            }
            y in listTop..listBottom -> tapRow(y)
        }
        return true
    }

    private fun tapRow(y: Float) {
        val board = game.currentBoard ?: return
        val pos = y - listTop + scroll
        val index = (pos / (rowH + gap)).toInt()
        if (index !in board.entries.indices || pos - index * (rowH + gap) > rowH) return
        game.openPlayer(board.entries[index])
    }

    private fun selectMode(modeId: String, r: RectF) {
        if (modeId == game.boardMode) return
        game.press(r)
        scroll = 0f
        game.selectBoard(modeId, game.boardTab)
    }

    private fun selectTab(t: BoardTab) {
        if (t == game.boardTab) return
        game.press(periodTabs[t.ordinal])
        scroll = 0f
        game.selectBoard(game.boardMode, t)
    }

    override fun onBack(): Boolean {
        if (game.popover != null) game.closePopover() else game.backToMenu()
        return true
    }

    private fun noFriends() =
        game.boardTab == BoardTab.FRIENDS && game.profile != null && game.friends?.friends?.isEmpty() == true

    private fun canAddFriend(): Boolean {
        val p = game.popover ?: return false
        return game.profile != null && !game.isFriend(p.uid)
    }

    override fun draw(canvas: Canvas) {
        val theme = game.theme
        val accent = Palette.accent(theme)
        val accent2 = Palette.accent2(theme)
        val text = neon.textColor
        val dim = theme.textDim

        veil.color = theme.panel
        veil.alpha = VEIL
        canvas.drawRect(0f, 0f, w, h, veil)

        neon.glowText(canvas, "RANKS", w / 2f, h * 0.075f, u * 8f, text, accent)
        widgets.gems(canvas, game.save.gems)
        widgets.tabs(canvas, modeTabs, MODE_LABELS, if (game.boardMode == "stack") 0 else 1, accent)
        widgets.tabs(canvas, periodTabs, PERIOD_LABELS, game.boardTab.ordinal, accent2)

        if (!game.onlineAvailable) {
            message(canvas, "Rankings need an internet connection", dim)
            drawBack(canvas, accent)
            return
        }

        val board = game.currentBoard
        neon.text(canvas, info(board), w / 2f, infoY, u * 3f, dim, 230)

        when {
            noFriends() -> widgets.pill(canvas, emptyPill, "ADD FRIENDS TO COMPETE", accent2, highlight = true)
            board == null -> widgets.spinner(canvas, w / 2f, (listTop + listBottom) / 2f, u * 4f, accent, game.time)
            board.entries.isEmpty() ->
                message(canvas, if (board.offline) "You're offline" else "No scores yet. Be the first!", dim)
            else -> drawList(canvas, board, accent)
        }
        drawPinned(canvas, board, accent, accent2, text, dim)
        drawBack(canvas, accent)
        game.popover?.let { drawPopover(canvas, it, accent, accent2, text, dim) }
    }

    private fun info(board: BoardSnapshot?): String {
        val now = System.currentTimeMillis()
        val lead = when (game.boardTab) {
            BoardTab.WEEK -> "Resets in ${IsoWeek.countdown(IsoWeek.msUntilReset(now))}"
            BoardTab.FRIENDS -> "Best scores of all time"
            BoardTab.ALL_TIME -> ""
        }
        val status = when {
            board == null -> ""
            board.offline -> "You're offline, showing saved ranks"
            board.fromCache && board.fetchedAtMs > 0 -> "Updated ${max(1L, (now - board.fetchedAtMs) / 60_000)} min ago"
            else -> ""
        }
        return when {
            lead.isEmpty() -> status
            status.isEmpty() -> lead
            else -> "$lead · $status"
        }
    }

    private fun drawList(canvas: Canvas, board: BoardSnapshot, accent: Int) {
        val me = game.account.uid
        canvas.save()
        canvas.clipRect(0f, listTop - u, w, listBottom + u)
        for ((i, e) in board.entries.withIndex()) {
            val top = listTop + i * (rowH + gap) - scroll
            if (top + rowH < listTop - u || top > listBottom + u) continue
            row.set(u * 7f, top, u * 93f, top + rowH)
            drawRow(canvas, e, e.uid == me, accent)
        }
        canvas.restore()
    }

    private fun drawRow(canvas: Canvas, e: BoardEntry, mine: Boolean, accent: Int) {
        val radius = u * 2.5f
        neon.panel(canvas, row, radius, 140)
        val medal = when (e.rank) {
            1 -> GOLD
            2 -> SILVER
            3 -> BRONZE
            else -> 0
        }
        // The top three get a soft medal behind their rank and a matching hairline; no glow.
        if (mine) neon.roundRect(canvas, row, radius, accent, u * 0.3f, 220)
        else if (medal != 0) neon.roundRect(canvas, row, radius, medal, u * 0.25f, MEDAL_EDGE)

        val text = neon.textColor
        val baseline = row.centerY() + u * 1.4f
        val rankX = row.left + u * 6f
        if (medal != 0) {
            fill.color = medal
            fill.alpha = MEDAL_FILL
            canvas.drawCircle(rankX, row.centerY(), row.height() * 0.3f, fill)
            neon.text(canvas, "${e.rank}", rankX, baseline, u * 3.8f, MEDAL_TEXT, 255)
        } else {
            neon.text(canvas, "${e.rank}", rankX, baseline, u * 3.8f, text, 230)
        }
        avatars.draw(canvas, e.avatar, row.left + u * 14f, row.centerY(), row.height() * 0.33f)
        neon.text(canvas, e.name, row.left + u * 20f, baseline, u * 3.8f, text, 235, Paint.Align.LEFT)
        neon.text(canvas, "${e.score}", row.right - u * 4f, baseline, u * 4.2f, text, 255, Paint.Align.RIGHT)
    }

    /** The player's own place, kept visible when their row is not on screen. */
    private fun drawPinned(canvas: Canvas, board: BoardSnapshot?, accent: Int, accent2: Int, text: Int, dim: Int) {
        if (game.profile == null) {
            widgets.pill(canvas, pinned, "CHOOSE A NAME TO JOIN", accent2, highlight = true)
            return
        }
        if (board == null || noFriends()) return
        val rank = board.myRank
        val score = board.myScore
        if (rank == null || score == null) {
            neon.text(canvas, "Play a run to get ranked", w / 2f, pinned.centerY() + u * 1.2f, u * 3.4f, dim, 230)
            return
        }
        val index = board.entries.indexOfFirst { it.uid == game.account.uid }
        if (index >= 0) {
            val top = listTop + index * (rowH + gap) - scroll
            if (top >= listTop && top + rowH <= listBottom) return
        }
        neon.panel(canvas, pinned, u * 2.5f, 200)
        neon.roundRect(canvas, pinned, u * 2.5f, accent, u * 0.3f, 220)
        neon.text(canvas, "You · #$rank · $score", w / 2f, pinned.centerY() + u * 1.4f, u * 3.8f, text, 255)
    }

    private fun drawPopover(canvas: Canvas, entry: BoardEntry, accent: Int, accent2: Int, text: Int, dim: Int) {
        fill.color = game.theme.panel
        fill.alpha = 150
        canvas.drawRect(0f, 0f, w, h, fill)
        neon.panel(canvas, card, u * 4f, 240)
        neon.roundRect(canvas, card, u * 4f, accent, u * 0.35f, 230)

        val cx = card.centerX()
        avatars.draw(canvas, entry.avatar, cx, card.top + u * 11f, u * 7f)
        neon.glowText(canvas, entry.name, cx, card.top + u * 25f, u * 5.5f, text, accent)
        val profile = game.popoverProfile
        if (profile == null) {
            widgets.spinner(canvas, cx, card.top + u * 33f, u * 2.5f, accent, game.time)
        } else {
            val stack = profile.bests["stack"] ?: 0
            val tower = profile.bests["tower"] ?: 0
            neon.text(canvas, "STACK  $stack     TOWER  $tower", cx, card.top + u * 34f, u * 3.8f, text, 240)
        }
        when {
            game.isFriend(entry.uid) -> neon.text(canvas, "Friends", cx, addButton.centerY() + u * 1.2f, u * 3.6f, dim, 230)
            canAddFriend() -> widgets.pill(canvas, addButton, "ADD FRIEND", accent2, highlight = true)
        }
    }

    private fun message(canvas: Canvas, s: String, color: Int) {
        neon.text(canvas, s, w / 2f, (listTop + listBottom) / 2f, u * 3.8f, color, 230)
    }

    private fun drawBack(canvas: Canvas, accent: Int) = widgets.pill(canvas, backButton, "BACK", accent)

    private fun maxScroll(): Float {
        val n = game.currentBoard?.entries?.size ?: 0
        return max(0f, n * (rowH + gap) - gap - (listBottom - listTop))
    }

    private companion object {
        const val VEIL = 170
        const val MEDAL_FILL = 220
        const val MEDAL_EDGE = 120
        const val MEDAL_TEXT = 0xFF2A2420.toInt()
        const val GOLD = 0xFFE8C872.toInt()
        const val SILVER = 0xFFC8CDD6.toInt()
        const val BRONZE = 0xFFD4A07A.toInt()
        val MODE_LABELS = arrayOf("STACK", "TOWER")
        val PERIOD_LABELS = arrayOf("WEEK", "ALL TIME", "FRIENDS")
    }
}
