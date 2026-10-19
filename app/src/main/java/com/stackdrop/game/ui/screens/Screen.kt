package com.stackdrop.game.ui.screens

import android.graphics.Canvas

/** One full-screen canvas page. All calls happen on the game thread. */
interface Screen {
    /** List screens scroll on drag, so their taps fire on release instead of on press. */
    val tapsOnUp: Boolean get() = false

    fun resize(w: Float, h: Float, u: Float)

    /** Called each time the game switches to this screen. */
    fun onEnter() {}
    fun update(dt: Float) {}
    fun draw(canvas: Canvas)
    fun onTap(x: Float, y: Float): Boolean
    fun onDrag(dy: Float) {}

    /** Returns true when the screen handled back itself. */
    fun onBack(): Boolean
}
