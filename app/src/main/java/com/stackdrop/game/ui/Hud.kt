package com.stackdrop.game.ui

import android.graphics.Canvas
import android.graphics.RectF
import com.stackdrop.game.game.Game
import com.stackdrop.game.game.Palette
import com.stackdrop.game.ui.screens.FriendsScreen
import com.stackdrop.game.ui.screens.GameOverScreen
import com.stackdrop.game.ui.screens.LeaderboardScreen
import com.stackdrop.game.ui.screens.MenuScreen
import com.stackdrop.game.ui.screens.PlayingScreen
import com.stackdrop.game.ui.screens.ProfileScreen
import com.stackdrop.game.ui.screens.Screen
import com.stackdrop.game.ui.screens.SettingsScreen
import com.stackdrop.game.ui.screens.ThemesScreen
import kotlin.math.max

/** Routes drawing and input to the active screen, and owns the feedback every screen shares. */
class Hud(private val game: Game, private val neon: Neon) {

    val widgets = Widgets(neon)
    private val avatars = Avatars()

    private val menu = MenuScreen(game, neon, widgets, avatars)
    private val profile = ProfileScreen(game, neon, widgets, avatars)
    private val leaderboard = LeaderboardScreen(game, neon, widgets, avatars)
    private val friendsScreen = FriendsScreen(game, neon, widgets, avatars)
    private val themes = ThemesScreen(game, neon, widgets)
    private val playing = PlayingScreen(game, this, neon, widgets)
    private val gameOver = GameOverScreen(game, neon, widgets)
    private val settings = SettingsScreen(game, neon, widgets)
    private val screens = listOf(menu, themes, playing, gameOver, settings, profile, leaderboard, friendsScreen)

    var scorePop = 0f
        private set
    var comboLabel = ""
        private set
    var comboTime = 0f
        private set
    private var banner = ""
    private var bannerTime = 0f

    private var ready = false

    val tapsOnUp get() = screen()?.tapsOnUp == true

    private var width = 0
    private var height = 0

    /** Lays the screens out again with the top controls below a camera cutout of [px]. */
    fun setTopInset(px: Float) {
        if (widgets.topInset == px) return
        widgets.topInset = px
        if (width > 0) resize(width, height)
    }

    fun resize(width: Int, height: Int) {
        this.width = width
        this.height = height
        val w = width.toFloat()
        val h = height.toFloat()
        val u = w / 100f
        neon.u = u
        widgets.resize(w, h, u)
        for (s in screens) s.resize(w, h, u)
        ready = w > 0f
    }

    fun press(r: RectF) = widgets.press(r)

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

    /** The game just switched state. */
    fun onEnter() {
        screen()?.onEnter()
    }

    fun onTap(x: Float, y: Float) {
        screen()?.onTap(x, y)
    }

    fun onDrag(dy: Float) {
        screen()?.onDrag(dy)
    }

    /** Returns false when no screen handled back, so the game falls back to the menu. */
    fun onBack(): Boolean = screen()?.onBack() ?: false

    fun update(dt: Float) {
        scorePop = max(0f, scorePop - dt * 4f)
        comboTime = max(0f, comboTime - dt)
        bannerTime = max(0f, bannerTime - dt)
        widgets.update(dt)
        screen()?.update(dt)
    }

    fun draw(canvas: Canvas) {
        if (!ready) return
        screen()?.draw(canvas)
        if (bannerTime > 0f) widgets.banner(canvas, banner, bannerTime / 0.4f, Palette.accent(game.theme))
    }

    private fun screen(): Screen? = when (game.state) {
        Game.State.MENU -> menu
        Game.State.THEMES -> themes
        Game.State.PLAYING -> playing
        Game.State.GAME_OVER -> gameOver
        Game.State.SETTINGS -> settings
        Game.State.PROFILE -> profile
        Game.State.LEADERBOARD -> leaderboard
        Game.State.FRIENDS -> friendsScreen
        else -> null
    }

    companion object {
        const val COMBO_TIME = 1f
        private const val BANNER_TIME = 2.5f
    }
}
