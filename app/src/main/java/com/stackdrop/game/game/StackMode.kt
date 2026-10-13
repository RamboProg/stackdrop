package com.stackdrop.game.game

import android.graphics.Canvas
import com.stackdrop.game.audio.SoundManager
import com.stackdrop.game.ui.Neon
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

class StackMode(private val host: ModeHost) : GameMode {

    override val id = "stack"
    override val title = "STACK"
    override val endReason = "Just missed the stack"

    override var score = 0
        private set
    override var bestCombo = 0
        private set
    override var isOver = false
        private set
    override val gemsEarned get() = perfects + score / 10
    override val isFinished get() = isOver && overTime > FALL_TIME

    private val iso = IsoRenderer()
    private val camera = Camera()
    private val particles = Particles()

    private val tower = ArrayList<Block>()
    private val debris = ArrayList<Debris>()
    private var moving: Block? = null
    private var axisX = false
    private var direction = 1f
    private var speed = START_SPEED
    private var theme = Palette.themes[0]

    private var combo = 0
    private var perfects = 0
    private var overTime = 0f
    private var drift = 0f

    private var width = 0
    private var height = 0
    private var baseScale = 1f

    override fun resize(width: Int, height: Int) {
        this.width = width
        this.height = height
        baseScale = width * 0.6f / (BASE * 2f * IsoRenderer.COS30)
    }

    override fun reset(theme: Theme) {
        this.theme = theme
        tower.clear()
        debris.clear()
        particles.clear()
        score = 0
        combo = 0
        bestCombo = 0
        perfects = 0
        overTime = 0f
        isOver = false
        speed = START_SPEED
        axisX = false

        tower.add(Block(-BASE / 2, -BASE / 2, BASE, BASE, -PEDESTAL_H, PEDESTAL_H, Palette.blockColor(theme, 0)))
        spawnNext()

        camera.targetY = SLAB_H
        camera.targetZoom = 1f
        camera.snap()
        drift = 0f
    }

    override fun update(dt: Float) {
        if (isOver) overTime += dt else moveSlab(dt)

        val it = debris.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d.update(dt)
            if (!d.alive) it.remove()
        }

        particles.update(dt, width.toFloat())
        camera.update(dt)

        val target = (Palette.hueAt(theme, score) - theme.baseHue) * 0.3f
        drift += (target - drift) * (1f - exp(-3f * dt))
    }

    override fun onTap() {
        if (!isOver) drop()
    }

    private fun moveSlab(dt: Float) {
        val b = moving ?: return
        val top = tower.last()
        var offset = if (axisX) b.x - top.x else b.z - top.z
        offset += direction * speed * dt
        if (offset > RANGE) {
            offset = RANGE
            direction = -1f
        } else if (offset < -RANGE) {
            offset = -RANGE
            direction = 1f
        }
        if (axisX) b.x = top.x + offset else b.z = top.z + offset
    }

    private fun spawnNext() {
        val top = tower.last()
        axisX = !axisX
        direction = 1f
        val b = Block(top.x, top.z, top.w, top.d, top.y + top.h, SLAB_H, Palette.blockColor(theme, score + 1))
        if (axisX) b.x = top.x - RANGE else b.z = top.z - RANGE
        moving = b
    }

    private fun drop() {
        val b = moving ?: return
        val top = tower.last()
        val delta = if (axisX) b.x - top.x else b.z - top.z
        val size = if (axisX) top.w else top.d

        if (abs(delta) <= PERFECT_TOLERANCE) {
            if (axisX) b.x = top.x else b.z = top.z
            combo++
            perfects++
            bestCombo = max(bestCombo, combo)
            val grew = combo >= GROW_COMBO && grow(b)

            host.sound.play(SoundManager.PERFECT, perfectPitch(combo))
            if (grew) host.sound.play(SoundManager.UNLOCK, 1.2f, 0.6f)
            host.haptics.perfect(combo)
            val glow = Palette.lighten(b.color, 0.4f)
            particles.ripple(b, 1 + min(combo, 3), glow)
            particles.burst(b, 14 + min(combo, 10) * 2, glow)

            val label = if (combo == 1) "PERFECT" else "PERFECT x$combo"
            host.hud.showCombo(if (grew) "$label  GROW!" else label)
            place(b)
            return
        }

        val overlap = size - abs(delta)
        if (overlap <= 0f) {
            miss(b, delta)
            return
        }

        combo = 0
        val cut = abs(delta)
        if (axisX) {
            val newX = max(b.x, top.x)
            val piece = if (delta > 0) Block(newX + overlap, b.z, cut, b.d, b.y, b.h, b.color)
            else Block(b.x, b.z, cut, b.d, b.y, b.h, b.color)
            b.x = newX
            b.w = overlap
            debris.add(Debris(piece, sign(delta) * DEBRIS_PUSH, 0f))
        } else {
            val newZ = max(b.z, top.z)
            val piece = if (delta > 0) Block(b.x, newZ + overlap, b.w, cut, b.y, b.h, b.color)
            else Block(b.x, b.z, b.w, cut, b.y, b.h, b.color)
            b.z = newZ
            b.d = overlap
            debris.add(Debris(piece, 0f, sign(delta) * DEBRIS_PUSH))
        }

        host.sound.play(SoundManager.DROP, 1f, 0.8f)
        host.sound.play(SoundManager.SLICE, 0.9f + cut / size * 0.4f, 0.6f)
        host.haptics.land()
        camera.shake(width * 0.003f, 0.1f)
        place(b)
    }

    private fun grow(b: Block): Boolean {
        var grew = false
        if (b.w < BASE) {
            val g = min(GROW_AMOUNT, BASE - b.w)
            b.x -= g / 2f
            b.w += g
            grew = true
        }
        if (b.d < BASE) {
            val g = min(GROW_AMOUNT, BASE - b.d)
            b.z -= g / 2f
            b.d += g
            grew = true
        }
        return grew
    }

    private fun place(b: Block) {
        tower.add(b)
        moving = null
        score++
        speed = min(MAX_SPEED, START_SPEED + score * SPEED_STEP)
        camera.targetY = b.y + b.h + SLAB_H
        host.hud.onScore()
        spawnNext()
    }

    private fun miss(b: Block, delta: Float) {
        moving = null
        combo = 0
        if (axisX) debris.add(Debris(b, sign(delta) * DEBRIS_PUSH, 0f))
        else debris.add(Debris(b, 0f, sign(delta) * DEBRIS_PUSH))

        host.sound.play(SoundManager.FAIL, 1f, 0.5f)
        host.haptics.soft()
        camera.shake(width * 0.008f, 0.2f)

        val towerTop = tower.last().let { it.y + it.h }
        val towerPx = (towerTop + BASE * 1.2f) * baseScale
        camera.targetZoom = (height * 0.5f / towerPx).coerceIn(MIN_ZOOM, 1f)
        camera.targetY = towerTop * 0.5f
        isOver = true
        overTime = 0f
    }

    override fun render(canvas: Canvas, time: Float) {
        if (width == 0) return
        val rise = (camera.y - SLAB_H) * baseScale * camera.zoom
        host.backdrop.draw(canvas, theme, drift, height * 0.74f + rise * 0.45f, time)

        iso.cx = width / 2f + camera.shakeX
        iso.cy = height * 0.45f + camera.shakeY
        iso.camY = camera.y
        iso.scale = baseScale * camera.zoom

        val top = tower.last()
        val depthRef = top.x + top.w / 2f + top.z + top.d / 2f

        for (d in debris) if (d.depth < depthRef) d.draw(canvas, iso)
        for (b in tower) if (iso.isVisible(b, height)) iso.drawBlock(canvas, b)
        moving?.let { iso.drawBlock(canvas, it) }
        particles.drawWorld(canvas, iso)
        for (d in debris) if (d.depth >= depthRef) d.draw(canvas, iso)
        particles.drawScreen(canvas)
    }

    override fun drawHud(canvas: Canvas, neon: Neon, u: Float, width: Float, height: Float) = Unit

    private companion object {
        const val BASE = 10f
        const val SLAB_H = 1f
        const val PEDESTAL_H = 60f
        const val RANGE = 14f
        const val PERFECT_TOLERANCE = 0.28f
        const val START_SPEED = 11f
        const val SPEED_STEP = 0.3f
        const val MAX_SPEED = 24f
        const val GROW_COMBO = 8
        const val GROW_AMOUNT = 0.7f
        const val DEBRIS_PUSH = 3f
        const val FALL_TIME = 1.1f
        const val MIN_ZOOM = 0.08f
    }
}
