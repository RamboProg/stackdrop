package com.stackdrop.game.ui.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.stackdrop.game.game.Game
import com.stackdrop.game.game.Palette
import com.stackdrop.game.online.Friend
import com.stackdrop.game.online.FriendRequest
import com.stackdrop.game.online.FriendsSnapshot
import com.stackdrop.game.ui.Avatars
import com.stackdrop.game.ui.Neon
import com.stackdrop.game.ui.Widgets
import kotlin.math.max
import kotlin.math.min

/** Friend count, ADD by code, MY CODE to share, incoming requests, and the friends list. */
class FriendsScreen(
    private val game: Game,
    private val neon: Neon,
    private val widgets: Widgets,
    private val avatars: Avatars,
) : Screen {

    private sealed class Item {
        data class Header(val title: String) : Item()
        data class Request(val request: FriendRequest) : Item()
        data class Person(val friend: Friend) : Item()
    }

    override val tapsOnUp = true

    private val addButton = RectF()
    private val codeButton = RectF()
    private val namePill = RectF()
    private val backButton = RectF()
    private val row = RectF()
    private val veil = Paint()

    private var items: List<Item> = emptyList()
    private var builtFor: FriendsSnapshot? = null
    private var scroll = 0f

    private var w = 0f
    private var h = 0f
    private var u = 1f
    private var rowH = 0f
    private var gap = 0f
    private var listTop = 0f
    private var listBottom = 0f

    override fun resize(w: Float, h: Float, u: Float) {
        this.w = w
        this.h = h
        this.u = u
        val top = h * 0.12f
        addButton.set(u * 8f, top, u * 40f, top + u * 9f)
        codeButton.set(u * 43f, top, u * 92f, top + u * 9f)
        val mid = h * 0.45f
        namePill.set(u * 15f, mid - u * 5f, u * 85f, mid + u * 5f)
        listTop = top + u * 14f
        listBottom = h * 0.85f
        rowH = min(h * 0.065f, u * 12f)
        gap = u * 1.2f
        val bottom = h * 0.88f
        backButton.set(u * 30f, bottom, u * 70f, bottom + u * 11f)
    }

    override fun onEnter() {
        scroll = 0f
    }

    override fun onDrag(dy: Float) {
        scroll = (scroll - dy).coerceIn(0f, maxScroll())
    }

    override fun onBack(): Boolean {
        game.backToMenu()
        return true
    }

    override fun onTap(x: Float, y: Float): Boolean {
        val profile = game.profile
        when {
            backButton.contains(x, y) -> {
                game.press(backButton)
                game.backToMenu()
            }
            !game.onlineAvailable -> Unit
            profile == null -> if (namePill.contains(x, y)) {
                game.press(namePill)
                game.editName()
            }
            widgets.hit(addButton, x, y) -> {
                game.press(addButton)
                game.promptFriendCode()
            }
            widgets.hit(codeButton, x, y) -> {
                game.press(codeButton)
                game.shareFriendCode()
            }
            game.friends == null && game.friendsFailed -> if (y in listTop..listBottom) game.retryFriends()
            y in listTop..listBottom -> tapItem(x, y)
        }
        return true
    }

    private fun tapItem(x: Float, y: Float) {
        val pos = y - listTop + scroll
        val index = (pos / (rowH + gap)).toInt()
        if (index !in items.indices || pos - index * (rowH + gap) > rowH) return
        when (val item = items[index]) {
            is Item.Request -> {
                // The two round buttons sit at the right end of the row.
                if (x > acceptX() - u * 5f && x < acceptX() + u * 5f) game.respondToRequest(item.request, true)
                else if (x > declineX() - u * 5f) game.respondToRequest(item.request, false)
            }
            is Item.Person -> game.confirmRemoveFriend(item.friend)
            is Item.Header -> Unit
        }
    }

    private fun acceptX() = u * 93f - u * 17f
    private fun declineX() = u * 93f - u * 6.5f

    override fun draw(canvas: Canvas) {
        val theme = game.theme
        val accent = Palette.accent(theme)
        val accent2 = Palette.accent2(theme)
        val text = neon.textColor
        val dim = theme.textDim

        veil.color = theme.panel
        veil.alpha = VEIL
        canvas.drawRect(0f, 0f, w, h, veil)

        val snapshot = game.friends
        val count = snapshot?.friends?.size ?: 0
        neon.glowText(canvas, "FRIENDS  $count/$MAX", w / 2f, h * 0.08f, u * 7f, text, accent)
        widgets.pill(canvas, backButton, "BACK", accent)

        val profile = game.profile
        when {
            !game.onlineAvailable -> centered(canvas, "Friends need an internet connection", dim)
            profile == null -> {
                neon.text(canvas, "Choose a name to add friends", w / 2f, namePill.top - u * 5f, u * 3.6f, dim, 230)
                widgets.pill(canvas, namePill, "CHOOSE A NAME", accent2, highlight = true)
            }
            else -> {
                widgets.pill(canvas, addButton, "ADD", accent2, highlight = true)
                widgets.pill(canvas, codeButton, "MY CODE  ${profile.displayCode}", accent)
                if (snapshot == null && game.friendsFailed) {
                    centered(canvas, "Couldn't load your friends. Tap to try again", dim)
                } else if (snapshot == null) {
                    widgets.spinner(canvas, w / 2f, (listTop + listBottom) / 2f, u * 4f, accent, game.time)
                } else {
                    rebuild(snapshot)
                    if (items.isEmpty()) {
                        centered(canvas, "Add friends by code to compete with them", dim)
                    } else {
                        drawItems(canvas, accent, accent2, text, dim)
                    }
                }
            }
        }
    }

    private fun rebuild(snapshot: FriendsSnapshot) {
        if (snapshot === builtFor) return
        builtFor = snapshot
        val list = ArrayList<Item>()
        if (snapshot.incoming.isNotEmpty()) {
            list += Item.Header("REQUESTS")
            snapshot.incoming.mapTo(list) { Item.Request(it) }
        }
        if (snapshot.friends.isNotEmpty()) {
            list += Item.Header("FRIENDS")
            snapshot.friends.mapTo(list) { Item.Person(it) }
        }
        items = list
        scroll = scroll.coerceIn(0f, maxScroll())
    }

    private fun drawItems(canvas: Canvas, accent: Int, accent2: Int, text: Int, dim: Int) {
        canvas.save()
        canvas.clipRect(0f, listTop - u, w, listBottom + u)
        for ((i, item) in items.withIndex()) {
            val top = listTop + i * (rowH + gap) - scroll
            if (top + rowH < listTop - u || top > listBottom + u) continue
            row.set(u * 7f, top, u * 93f, top + rowH)
            val baseline = row.centerY() + u * 1.4f
            when (item) {
                is Item.Header -> neon.text(canvas, item.title, row.left + u * 2f, baseline, u * 3.4f, dim, 230,
                    Paint.Align.LEFT)
                is Item.Request -> {
                    neon.panel(canvas, row, u * 2.5f, 140)
                    neon.roundRect(canvas, row, u * 2.5f, accent2, u * 0.3f, 160)
                    val nameSize = neon.fit(item.request.fromName, u * 3.8f, acceptX() - u * 6f - (row.left + u * 5f))
                    neon.text(canvas, item.request.fromName, row.left + u * 5f, baseline, nameSize, text, 240,
                        Paint.Align.LEFT)
                    check(canvas, acceptX(), row.centerY(), accent)
                    cross(canvas, declineX(), row.centerY(), dim)
                }
                is Item.Person -> {
                    val f = item.friend
                    neon.panel(canvas, row, u * 2.5f, 140)
                    avatars.draw(canvas, f.avatar, row.left + u * 6f, row.centerY(), row.height() * 0.33f)
                    // Room up to the scores on the right.
                    val nameSize = neon.fit(f.name, u * 3.8f, row.width() - u * 46f)
                    neon.text(canvas, f.name, row.left + u * 12f, baseline, nameSize, text, 240, Paint.Align.LEFT)
                    neon.text(canvas, "S ${f.bestStack} · T ${f.bestTower}", row.right - u * 4f, baseline, u * 3.4f,
                        dim, 230, Paint.Align.RIGHT)
                }
            }
        }
        canvas.restore()
    }

    private fun check(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        val r = u * 2.2f
        neon.line(canvas, cx - r, cy, cx - r * 0.3f, cy + r * 0.7f, color, u * 0.5f, 240)
        neon.line(canvas, cx - r * 0.3f, cy + r * 0.7f, cx + r, cy - r * 0.7f, color, u * 0.5f, 240)
    }

    private fun cross(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        val r = u * 1.8f
        neon.line(canvas, cx - r, cy - r, cx + r, cy + r, color, u * 0.45f, 230)
        neon.line(canvas, cx - r, cy + r, cx + r, cy - r, color, u * 0.45f, 230)
    }

    private fun centered(canvas: Canvas, s: String, color: Int) {
        neon.text(canvas, s, w / 2f, (listTop + listBottom) / 2f, u * 3.6f, color, 230)
    }

    private fun maxScroll(): Float = max(0f, items.size * (rowH + gap) - gap - (listBottom - listTop))

    private companion object {
        const val VEIL = 170
        const val MAX = 100
    }
}
