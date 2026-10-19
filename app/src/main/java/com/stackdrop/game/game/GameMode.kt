package com.stackdrop.game.game

import android.graphics.Canvas
import com.stackdrop.game.audio.Haptics
import com.stackdrop.game.audio.SoundManager
import com.stackdrop.game.ui.Hud
import com.stackdrop.game.ui.Neon
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** One playable ruleset. The [Game] shell owns menus, saving and rewards; a mode owns its world. */
interface GameMode {
    val id: String
    val title: String
    val score: Int
    val bestCombo: Int
    val gemsEarned: Int
    val endReason: String

    /** The run is lost and the ending animation is playing; taps are ignored. */
    val isOver: Boolean

    /** The ending animation is done and the game-over screen can appear. */
    val isFinished: Boolean

    fun resize(width: Int, height: Int)
    fun reset(theme: Theme)
    fun update(dt: Float)
    fun onTap()
    fun render(canvas: Canvas, time: Float)
    fun drawHud(canvas: Canvas, neon: Neon, u: Float, width: Float, height: Float)
}

class ModeHost(
    val sound: SoundManager,
    val haptics: Haptics,
    val backdrop: Backdrop,
    val neon: Neon,
    val hud: Hud,
) {
    /** The Reduce motion setting; modes skip camera shake while it is on. */
    var reduceMotion = false

    fun shakeScale(theme: Theme) = if (reduceMotion) 0f else theme.shake

    /** Particle count for this theme, never below one. */
    fun particles(theme: Theme, count: Int) = max(1, (count * theme.particles).toInt())
}

/** Semitone offsets of a major scale, so consecutive perfects climb a melody. */
private val PERFECT_SCALE = floatArrayOf(0f, 2f, 4f, 5f, 7f, 9f, 11f, 12f)

fun perfectPitch(combo: Int): Float =
    2f.pow(PERFECT_SCALE[min(combo - 1, PERFECT_SCALE.lastIndex).coerceAtLeast(0)] / 12f)
