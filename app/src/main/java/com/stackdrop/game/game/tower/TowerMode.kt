package com.stackdrop.game.game.tower

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.stackdrop.game.audio.SoundManager
import com.stackdrop.game.game.Camera
import com.stackdrop.game.game.GameMode
import com.stackdrop.game.game.ModeHost
import com.stackdrop.game.game.Palette
import com.stackdrop.game.game.Particles
import com.stackdrop.game.game.Theme
import com.stackdrop.game.game.perfectPitch
import com.stackdrop.game.ui.Neon
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/**
 * Side-view crane mode. A module swings on a rope; tap to release it onto the tower.
 * Off-center drops shift the tower's center of mass, which makes it lean. Dropping the
 * next modules on the opposite side counterweights it; letting it lean too far collapses it.
 */
class TowerMode(private val host: ModeHost) : GameMode {

    /** A placed module in tower-local coordinates: [x] is its center, [y] its bottom. */
    private class Floor(val x: Float, val y: Float, val color: Int, val windows: Int, val lobby: Boolean) {
        /** Visual offset from where it actually landed, easing to zero so snaps never pop. */
        var settle = 0f
    }

    private class Tumble(
        var x: Float, var y: Float, val vx: Float, var vy: Float,
        val spin: Float, val color: Int, val windows: Int,
    ) {
        var rotation = 0f
        var age = 0f
    }

    private enum class Hook { LOWERING, READY, FALLING, EMPTY }

    override val id = "tower"
    override val title = "TOWER"

    override var score = 0
        private set
    override var bestCombo = 0
        private set
    override var isOver = false
        private set
    override var endReason = ""
        private set
    override val gemsEarned get() = perfects + score / 10
    override val isFinished get() = isOver && overTime > (if (collapsing) COLLAPSE_TIME else OVER_TIME)

    private val floors = ArrayList<Floor>()
    private val tumbles = ArrayList<Tumble>()
    private val camera = Camera()
    private val particles = Particles()
    private val skyline = Skyline()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var theme = Palette.themes[0]

    private var w = 0f
    private var h = 0f
    private var u = 1f
    private var scale = 1f
    private var sc = 1f
    private var anchorY = 0f
    private var pivotScreenY = 0f
    private var rope = 6f
    private var camMin = 0f

    private var hook = Hook.LOWERING
    private var hookTime = 0f
    private var phase = 0f
    private var amp = 0.3f
    private var omega = 2f

    private var fx = 0f
    private var fy = 0f
    private var fvx = 0f
    private var fvy = 0f
    private var nextColor = Color.WHITE
    private var nextWindows = 0

    /** Static lean and sway are fractions of the capped top shift, so tall towers never whip around. */
    private var lean = 0f
    private var leanTarget = 0f
    private var shownTop = 0f
    private var sway = 0f
    private var swayV = 0f
    private var balance = 0f
    private var shownBalance = 0f

    private var collapsing = false
    private var collapseAngle = 0f
    private var collapseV = 0f
    private var collapseDir = 1f

    private var lives = LIVES
    private var combo = 0
    private var perfects = 0
    private var overTime = 0f
    private var clock = 0f
    private var damageFlash = 0f

    override fun resize(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        u = w / 100f
        scale = w * 0.26f / W
        anchorY = h * 0.64f
        pivotScreenY = h * 0.25f
        rope = max(3f, (anchorY - pivotScreenY) / scale - H - HOOK_GAP - 4.5f)
        camMin = h * 0.2f / scale
        skyline.resize(width, height)
        updateDifficulty()
        if (floors.isEmpty() && !isOver) {
            camera.targetY = camMin
            camera.snap()
        }
    }

    override fun reset(theme: Theme) {
        this.theme = theme
        floors.clear()
        tumbles.clear()
        particles.clear()
        score = 0
        bestCombo = 0
        combo = 0
        perfects = 0
        lives = LIVES
        isOver = false
        endReason = ""
        overTime = 0f
        collapsing = false
        collapseAngle = 0f
        collapseV = 0f
        lean = 0f
        leanTarget = 0f
        shownTop = 0f
        sway = 0f
        swayV = 0f
        balance = 0f
        shownBalance = 0f
        damageFlash = 0f

        camera.targetX = 0f
        camera.targetY = camMin
        camera.targetZoom = 1f
        camera.snap()
        updateDifficulty()
        spawn()
    }

    // region Update

    override fun update(dt: Float) {
        sc = scale * camera.zoom
        clock += dt
        phase += omega * dt
        hookTime += dt
        if (hook == Hook.LOWERING && hookTime >= LOWER_TIME) hook = Hook.READY
        if (hook == Hook.FALLING) updateFalling(dt)

        if (collapsing) {
            collapseV += collapseDir * (1.5f + abs(collapseAngle) * 7f) * dt
            collapseAngle = (collapseAngle + collapseV * dt).coerceIn(-1.6f, 1.6f)
        } else {
            lean += (leanTarget - lean) * (1f - exp(-2.5f * dt))
            swayV += (-SWAY_K * sway - SWAY_D * swayV) * dt
            sway = (sway + swayV * dt).coerceIn(-MAX_SWAY, MAX_SWAY)
        }
        shownBalance += (balance - shownBalance) * (1f - exp(-6f * dt))
        shownTop += (floors.size * H - shownTop) * (1f - exp(-6f * dt))
        val settleDecay = exp(-10f * dt)
        for (f in floors) f.settle *= settleDecay

        val it = tumbles.iterator()
        while (it.hasNext()) {
            val t = it.next()
            t.age += dt
            t.vy -= GRAVITY * dt
            t.x += t.vx * dt
            t.y += t.vy * dt
            t.rotation += t.spin * dt
            if (t.age > 3f) it.remove()
        }

        particles.update(dt, w)
        camera.update(dt)
        if (isOver) overTime += dt
        damageFlash = max(0f, damageFlash - dt * 2.2f)
    }

    override fun onTap() {
        if (isOver || hook != Hook.READY) return
        val theta = amp * sin(phase)
        val dTheta = amp * omega * cos(phase)
        fx = camera.x + rope * sin(theta)
        fy = pivotY() - rope * cos(theta) - HOOK_GAP - H
        fvx = rope * dTheta * cos(theta) * MOMENTUM
        fvy = rope * dTheta * sin(theta) * MOMENTUM
        hook = Hook.FALLING
        host.sound.play(SoundManager.RELEASE, 1f, 0.7f)
    }

    private fun updateFalling(dt: Float) {
        fvy -= GRAVITY * dt
        fx += fvx * dt
        fy += fvy * dt

        val topY = floors.size * H
        if (fy <= topY) land(fx - shiftAt(topY + H / 2f), topY)
    }

    private fun land(lx: Float, topY: Float) {
        val support = floors.lastOrNull()?.x ?: 0f
        val offset = lx - support
        if (abs(offset) >= FALL_OFF) {
            fallOff(offset)
            return
        }

        val perfect = abs(offset) <= PERFECT_TOLERANCE
        // A perfect drop also nudges toward the side that rebalances the tower.
        val assist = if (perfect) -sign(balance) * min(ASSIST, abs(balance) * ASSIST * 1.5f) else 0f
        val x = if (perfect) support + assist else lx
        floors.add(Floor(x, topY, nextColor, nextWindows, floors.isEmpty()).also { it.settle = lx - x })
        score = floors.size

        val px = sx(lx + shiftAt(topY + H / 2f))
        val py = sy(topY)
        val glow = Palette.lighten(nextColor, 0.4f)

        if (perfect) {
            combo++
            perfects++
            bestCombo = max(bestCombo, combo)
            swayV *= 0.3f
            sway *= 0.5f
            host.sound.play(SoundManager.PERFECT, perfectPitch(combo))
            host.haptics.perfect(combo)
            val label = if (combo == 1) "PERFECT" else "PERFECT x$combo"
            host.hud.showCombo(if (abs(assist) > 0.05f) "$label  +BALANCE" else label)
            particles.flares(px, py, glow, 18 + min(combo, 6) * 2, w * 0.5f)
        } else {
            combo = 0
            swayV += offset / W * SWAY_KICK + fvx * 0.01f
            host.sound.play(SoundManager.DROP, 0.8f, 0.8f)
            host.haptics.land()
            particles.flares(px, py, glow, 12, w * 0.4f)
            camera.shake(w * 0.003f, 0.1f)
        }
        host.hud.onScore()

        balance = stability()
        val r = min(1f, abs(balance))
        leanTarget = sign(balance) * r.pow(1.5f)

        if (abs(balance) > 1f) {
            collapse()
            return
        }
        if (abs(balance) > DANGER) {
            host.sound.play(SoundManager.WARN, 1f, 0.5f)
            host.haptics.warn()
        }

        val newTop = topY + H
        camera.targetY = max(camMin, newTop)
        camera.targetX = x + leanTarget * shiftScale(newTop)
        updateDifficulty()
        spawn()
    }

    private fun fallOff(offset: Float) {
        val dir = if (offset >= 0f) 1f else -1f
        tumbles.add(Tumble(fx, fy + H / 2f, dir * 5f + fvx * 0.5f, 4f, dir * 220f, nextColor, nextWindows))
        lives--
        combo = 0
        damageFlash = 1f
        host.sound.play(SoundManager.FAIL, 1.1f, 0.45f)
        host.haptics.soft()
        host.hud.showCombo(if (lives > 0) "Slipped off" else "Last one slipped")
        camera.shake(w * 0.005f, 0.15f)
        if (lives <= 0) {
            endRun("All modules used")
        } else {
            spawn()
        }
    }

    private fun collapse() {
        collapsing = true
        collapseDir = if (balance >= 0f) 1f else -1f
        collapseV = collapseDir * 0.2f
        host.sound.play(SoundManager.FAIL, 0.8f, 0.55f)
        host.haptics.soft()
        camera.shake(w * 0.01f, 0.3f)
        endRun("The tower tipped over")
    }

    private fun endRun(reason: String) {
        hook = Hook.EMPTY
        isOver = true
        endReason = reason
        overTime = 0f
        val top = floors.size * H
        camera.targetZoom = (h * 0.5f / ((top + H * 3f) * scale)).coerceIn(MIN_ZOOM, 1f)
        camera.targetY = max(top * 0.5f, camMin * camera.targetZoom)
        camera.targetX = 0f
    }

    private fun spawn() {
        hook = Hook.LOWERING
        hookTime = 0f
        nextColor = Palette.blockColor(theme, floors.size + 1)
        var bits = 0
        for (i in 0 until 6) if (Random.nextFloat() < 0.6f) bits = bits or (1 shl i)
        nextWindows = bits
    }

    private fun updateDifficulty() {
        val sweep = W * min(1.05f, 0.42f + score * 0.022f)
        amp = asin(min(0.9f, sweep / rope))
        omega = min(3.3f, 1.9f + score * 0.04f)
    }

    /**
     * Worst signed overhang ratio in the tower. For every floor, the combined center of mass of
     * everything above it must stay within half its width; 1.0 means it is about to tip.
     */
    private fun stability(): Float {
        var sum = 0f
        var count = 0
        var worst = 0f
        for (i in floors.indices.reversed()) {
            if (count > 0) {
                val r = (sum / count - floors[i].x) / (W * 0.5f)
                if (abs(r) > abs(worst)) worst = r
            }
            sum += floors[i].x
            count++
        }
        if (count > 0) {
            val r = (sum / count) / (W * FOUNDATION_HALF)
            if (abs(r) > abs(worst)) worst = r
        }
        return worst
    }

    private fun bend() = lean + sway + sin(clock * 1.3f) * WOBBLE * min(score, 20) / 20f

    private fun shiftScale(top: Float) = MAX_SHIFT * min(1f, top / BEND_HEIGHT)

    /** Sideways offset of the tower at height [y]: a smooth curve that is largest at the top. */
    private fun shiftAt(y: Float): Float {
        val top = max(shownTop, H)
        val t = y / top
        return bend() * shiftScale(top) * t * t
    }

    private fun slopeAt(y: Float): Float {
        val top = max(shownTop, H)
        return bend() * shiftScale(top) * 2f * y / (top * top)
    }

    private fun pivotY() = camera.y + (anchorY - pivotScreenY) / (scale * camera.zoom)

    private fun ropeLength(): Float {
        if (hook != Hook.LOWERING) return rope
        val t = (hookTime / LOWER_TIME).coerceIn(0f, 1f)
        return rope * (1f - (1f - t).pow(3))
    }

    private fun sx(x: Float) = w / 2f + (x - camera.x) * sc + camera.shakeX

    private fun sy(y: Float) = anchorY - (y - camera.y) * sc + camera.shakeY

    // endregion

    // region Render

    override fun render(canvas: Canvas, time: Float) {
        if (w == 0f) return
        sc = scale * camera.zoom
        val horizon = h * 0.72f + (camera.y - camMin) * sc * 0.5f
        val drift = (Palette.hueAt(theme, score) - theme.baseHue) * 0.3f
        host.backdrop.draw(canvas, theme, drift, horizon, time)
        skyline.draw(canvas, theme, horizon)
        drawStreet(canvas)

        val fh = H * sc
        canvas.save()
        if (collapsing) canvas.rotate(Math.toDegrees(collapseAngle.toDouble()).toFloat(), sx(0f), sy(0f))
        for (f in floors) {
            val bottom = sy(f.y)
            if (!collapsing && (bottom < -fh * 2f || bottom - fh > h + fh * 2f)) continue
            val mid = f.y + H / 2f
            val cx = sx(f.x + f.settle + shiftAt(mid))
            val tilt = Math.toDegrees(atan(slopeAt(mid)).toDouble()).toFloat()
            canvas.save()
            canvas.rotate(tilt, cx, bottom - fh / 2f)
            drawFloor(canvas, cx, bottom, f.color, f.windows, f.lobby, 255)
            canvas.restore()
        }
        canvas.restore()

        for (t in tumbles) {
            val px = sx(t.x)
            val py = sy(t.y)
            val alpha = (255 * (1f - t.age / 3f)).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.rotate(t.rotation, px, py)
            drawFloor(canvas, px, py + fh / 2f, t.color, t.windows, false, alpha)
            canvas.restore()
        }

        if (hook == Hook.FALLING && !isOver) {
            drawFloor(canvas, sx(fx), sy(fy), nextColor, nextWindows, floors.isEmpty(), 255)
        }

        drawCrane(canvas)
        particles.drawScreen(canvas)

        if (damageFlash > 0f) {
            fill.color = Neon.RED
            fill.alpha = (28 * damageFlash).toInt()
            canvas.drawRect(0f, 0f, w, h, fill)
        }
    }

    private fun drawStreet(canvas: Canvas) {
        val street = sy(0f)
        if (street > h + 10f) return
        val accent = Palette.accent(theme)
        val neon = host.neon

        fill.color = Palette.hsv(theme.bgHue, theme.bgSat, 0.03f)
        fill.alpha = 255
        canvas.drawRect(0f, street, w, h, fill)
        neon.line(canvas, 0f, street, w, street, accent, u * 0.4f, 220)

        val dashY = street + (h - street).coerceAtMost(u * 14f) * 0.5f
        var x = (-camera.x * sc) % (u * 12f) - u * 12f
        while (x < w) {
            neon.line(canvas, x, dashY, x + u * 5f, dashY, accent, u * 0.25f, 90)
            x += u * 12f
        }

        rect.set(sx(-W * 0.65f), street, sx(W * 0.65f), sy(-0.4f))
        neon.roundRect(canvas, rect, u, Palette.accent2(theme), u * 0.3f, 200, fillAlpha = 40)
    }

    private fun drawFloor(canvas: Canvas, cx: Float, bottom: Float, color: Int, windows: Int, lobby: Boolean, alpha: Int) {
        val fw = W * sc
        val fh = H * sc
        rect.set(cx - fw / 2f, bottom - fh, cx + fw / 2f, bottom)

        fill.color = Palette.shade(color, 0.18f)
        fill.alpha = alpha
        canvas.drawRect(rect, fill)

        val padX = fw * 0.1f
        val gapX = fw * 0.08f
        val padTop = fh * 0.2f
        val padBottom = fh * 0.12f
        val gapY = fh * 0.12f
        val ww = (fw - padX * 2f - gapX * 2f) / 3f
        val wh = (fh - padTop - padBottom - gapY) / 2f
        val bleed = fw * 0.015f
        val left0 = rect.left
        val top0 = rect.top
        for (r in 0..1) {
            for (c in 0..2) {
                val left = left0 + padX + c * (ww + gapX)
                val top = top0 + padTop + r * (wh + gapY)
                if (lobby && r == 1 && c == 1) {
                    fill.color = Palette.lighten(color, 0.3f)
                    fill.alpha = (alpha * 0.85f).toInt()
                    canvas.drawRect(left, top - wh * 0.3f, left + ww, bottom, fill)
                    continue
                }
                val lit = (windows and (1 shl (r * 3 + c))) != 0
                if (lit) {
                    fill.color = color
                    fill.alpha = (alpha * 0.35f).toInt()
                    canvas.drawRect(left - bleed, top - bleed, left + ww + bleed, top + wh + bleed, fill)
                    fill.color = Palette.lighten(color, 0.6f)
                    fill.alpha = alpha
                } else {
                    fill.color = Palette.shade(color, 0.4f)
                    fill.alpha = (alpha * 0.6f).toInt()
                }
                canvas.drawRect(left, top, left + ww, top + wh, fill)
            }
        }

        host.neon.roundRect(canvas, rect, fw * 0.03f, color, max(2f, sc * 0.09f), alpha)
        val roof = top0 + fh * 0.09f
        host.neon.line(canvas, rect.left, roof, rect.right, roof, color, max(1f, sc * 0.04f), (alpha * 0.6f).toInt())
    }

    private fun drawCrane(canvas: Canvas) {
        val neon = host.neon
        val accent = Palette.accent(theme)
        val accent2 = Palette.accent2(theme)
        val top = pivotScreenY - u * 5f
        val bottom = pivotScreenY - u * 1.2f

        neon.line(canvas, 0f, top, w, top, accent, u * 0.4f, 210)
        neon.line(canvas, 0f, bottom, w, bottom, accent, u * 0.4f, 210)
        val step = u * 7f
        val offset = ((camera.x * sc) % step + step) % step
        var x = -offset
        while (x < w + step) {
            neon.line(canvas, x, bottom, x + step / 2f, top, accent, u * 0.22f, 130)
            neon.line(canvas, x + step / 2f, top, x + step, bottom, accent, u * 0.22f, 130)
            x += step
        }

        val px = w / 2f + camera.shakeX
        rect.set(px - u * 5f, bottom - u * 0.5f, px + u * 5f, bottom + u * 2.5f)
        neon.roundRect(canvas, rect, u, accent2, u * 0.35f, 255, fillAlpha = 90)
        val ropeTop = rect.bottom

        if (isOver || hook == Hook.FALLING || hook == Hook.EMPTY) {
            val hookY = ropeTop + u * 4f
            neon.line(canvas, px, ropeTop, px, hookY, Palette.lighten(accent2, 0.5f), u * 0.22f, 200)
            neon.dot(canvas, px, hookY, u * 0.8f, accent2)
            return
        }

        val len = ropeLength()
        val theta = amp * sin(phase)
        val hx = camera.x + len * sin(theta)
        val hy = pivotY() - len * cos(theta)
        val hsx = sx(hx)
        val hsy = sy(hy)
        neon.line(canvas, px, ropeTop, hsx, hsy, Palette.lighten(accent2, 0.5f), u * 0.22f, 220)

        val moduleTop = sy(hy - HOOK_GAP)
        val half = W * sc / 2f
        neon.line(canvas, hsx, hsy, hsx - half * 0.8f, moduleTop, accent2, u * 0.15f, 160)
        neon.line(canvas, hsx, hsy, hsx + half * 0.8f, moduleTop, accent2, u * 0.15f, 160)
        neon.dot(canvas, hsx, hsy, u * 0.8f, accent2)
        drawFloor(canvas, hsx, sy(hy - HOOK_GAP - H), nextColor, nextWindows, floors.isEmpty(), 255)
    }

    override fun drawHud(canvas: Canvas, neon: Neon, u: Float, width: Float, height: Float) {
        for (i in 0 until LIVES) {
            val alive = i < lives
            neon.heart(canvas, u * 7f + i * u * 8f, height * 0.06f - u * 1.2f, u * 2.6f,
                if (alive) Neon.RED else Color.GRAY, alive)
        }

        val y = height * 0.155f
        val cx = width / 2f
        val half = u * 24f
        val accent = Palette.accent(theme)
        neon.line(canvas, cx - half, y, cx + half, y, accent, u * 0.3f, 110)
        neon.line(canvas, cx - half, y, cx - half * 0.72f, y, Neon.AMBER, u * 0.4f, 170)
        neon.line(canvas, cx + half * 0.72f, y, cx + half, y, Neon.AMBER, u * 0.4f, 170)
        neon.line(canvas, cx, y - u * 1.4f, cx, y + u * 1.4f, Color.WHITE, u * 0.25f, 160)

        val b = (shownBalance + sway * 0.25f).coerceIn(-1.15f, 1.15f)
        val danger = min(1f, abs(b))
        val color = Palette.hsv(140f - 100f * danger, 0.75f, 1f)
        val pulse = if (danger > DANGER) 0.75f + 0.25f * sin(clock * 6f) else 1f
        neon.dot(canvas, cx + b * half, y, u * 1.5f, color, (255 * pulse).toInt())
        if (danger > DANGER) {
            val side = if (b > 0f) "LEANING RIGHT" else "LEANING LEFT"
            neon.glowText(canvas, side, cx, y + u * 5f, u * 3.2f, Color.WHITE, Neon.AMBER, (255 * pulse).toInt())
        } else {
            neon.text(canvas, "BALANCE", cx, y + u * 5f, u * 3.2f, Color.WHITE, 160)
        }
    }

    // endregion

    private companion object {
        const val W = 4f
        const val H = 2.6f
        const val HOOK_GAP = 0.4f
        const val GRAVITY = 38f
        /** Share of the swing velocity a released module keeps; full momentum is too hard to aim. */
        const val MOMENTUM = 0.55f
        const val LOWER_TIME = 0.45f
        const val PERFECT_TOLERANCE = 0.28f
        /** Past half a module of overhang the module tips off the tower. */
        const val FALL_OFF = W * 0.5f
        const val FOUNDATION_HALF = 0.6f
        /** Most the top can ever shift sideways, however tall the tower is. */
        const val MAX_SHIFT = W * 0.6f
        /** Height at which the full lean is reached; shorter towers lean proportionally less. */
        const val BEND_HEIGHT = H * 8f
        const val MAX_SWAY = 0.35f
        const val SWAY_K = 10f
        const val SWAY_D = 2.6f
        const val SWAY_KICK = 0.9f
        const val WOBBLE = 0.04f
        const val ASSIST = W * 0.12f
        const val DANGER = 0.7f
        const val LIVES = 3
        const val OVER_TIME = 1.3f
        const val COLLAPSE_TIME = 2.2f
        const val MIN_ZOOM = 0.15f
    }
}
