package com.stackdrop.game.ui

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Canvas widgets shared by every screen, plus the single press animation they all use. */
class Widgets(private val neon: Neon) {

    private val icon = Path()
    private val box = RectF()
    private val bannerRect = RectF()

    private var w = 0f
    private var h = 0f
    private var u = 1f

    private var pressed: RectF? = null
    private var pressTime = 0f

    /** The smallest comfortable touch target, 48dp. */
    private val minTouch = 48f * Resources.getSystem().displayMetrics.density

    fun resize(w: Float, h: Float, u: Float) {
        this.w = w
        this.h = h
        this.u = u
    }

    /**
     * Hit test for a small control with free space around it: [r] counts as at least a 48dp square
     * around its centre. Neighbouring controls must not be closer than that, or the first one tested wins.
     */
    fun hit(r: RectF, x: Float, y: Float): Boolean {
        val halfW = max(r.width(), minTouch) / 2f
        val halfH = max(r.height(), minTouch) / 2f
        return abs(x - r.centerX()) <= halfW && abs(y - r.centerY()) <= halfH
    }

    /** True when ([x], [y]) is on, or within [margin] of, any of [rects]: a near miss, not a tap on the background. */
    fun near(x: Float, y: Float, margin: Float, vararg rects: RectF): Boolean = rects.any {
        x >= it.left - margin && x <= it.right + margin && y >= it.top - margin && y <= it.bottom + margin
    }

    fun press(r: RectF) {
        pressed = r
        pressTime = PRESS_TIME
    }

    fun update(dt: Float) {
        pressTime = max(0f, pressTime - dt)
    }

    fun pressScale(r: RectF): Float {
        if (pressed !== r || pressTime <= 0f) return 1f
        val t = pressTime / PRESS_TIME
        return 1f - 0.08f * sin(t * Math.PI.toFloat())
    }

    fun pill(
        canvas: Canvas, r: RectF, title: String, color: Int,
        highlight: Boolean = false, dim: Boolean = false, breathe: Float = 1f, slashed: Boolean = false,
    ) {
        canvas.save()
        val s = pressScale(r) * breathe
        canvas.scale(s, s, r.centerX(), r.centerY())
        val radius = r.height() / 2f
        neon.panel(canvas, r, radius, 160)

        var size = u * 4.4f
        val room = r.width() - u * 4f
        val tw = neon.measure(title, size)
        if (tw > room) size *= room / tw
        val baseline = r.centerY() + size * 0.36f

        if (highlight) {
            neon.roundRect(canvas, r, radius, color, u * 0.45f, 255, fillAlpha = 210)
            neon.text(canvas, title, r.centerX(), baseline, size, DARK)
        } else {
            neon.roundRect(canvas, r, radius, color, u * 0.4f, if (dim) 110 else 230)
            neon.glowText(canvas, title, r.centerX(), baseline, size, neon.textColor, color, if (dim) 150 else 255)
        }
        if (slashed) {
            val half = neon.measure(title, size) / 2f + u
            neon.line(canvas, r.centerX() - half, r.centerY() + size * 0.1f, r.centerX() + half, r.centerY() - size * 0.25f,
                color, u * 0.25f, 170)
        }
        canvas.restore()
    }

    fun stackIcon(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        for (k in 0 until 3) {
            val y = cy + s * 0.5f - k * s * 0.42f
            val dx = if (k == 2) s * 0.25f else 0f
            icon.reset()
            icon.moveTo(cx - s + dx, y)
            icon.lineTo(cx + dx, y - s * 0.5f)
            icon.lineTo(cx + s + dx, y)
            icon.lineTo(cx + dx, y + s * 0.5f)
            icon.close()
            neon.path(canvas, icon, color, u * 0.35f, 230)
        }
    }

    fun craneIcon(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        val beam = cy - s * 0.95f
        neon.line(canvas, cx - s * 1.3f, beam, cx + s * 1.3f, beam, color, u * 0.35f, 230)
        neon.line(canvas, cx + s * 0.15f, beam, cx + s * 0.35f, cy - s * 0.15f, color, u * 0.25f, 200)
        box.set(cx - s * 0.2f, cy - s * 0.15f, cx + s * 0.9f, cy + s * 0.45f)
        neon.roundRect(canvas, box, u * 0.5f, color, u * 0.3f, 230, fillAlpha = 50)
        box.set(cx - s * 0.75f, cy + s * 0.5f, cx + s * 0.35f, cy + s * 1.1f)
        neon.roundRect(canvas, box, u * 0.5f, color, u * 0.3f, 160)
    }

    /** Gem counter in the top-right corner, moved left by [inset] to make room for other buttons. */
    private val gemText = NumberText()

    fun gems(canvas: Canvas, count: Int, inset: Float = 0f) {
        val y = h * 0.06f
        neon.gem(canvas, w - u * 7f - inset, y - u * 1.6f, u * 2.6f)
        neon.glowText(canvas, gemText.of(count), w - u * 11.5f - inset, y, u * 5f, neon.textColor, Neon.GEM,
            align = Paint.Align.RIGHT)
    }

    /** Settings gear centered in [r]. */
    fun gear(canvas: Canvas, r: RectF, color: Int) {
        canvas.save()
        val s = pressScale(r)
        canvas.scale(s, s, r.centerX(), r.centerY())
        val cx = r.centerX()
        val cy = r.centerY()
        val outer = r.width() * 0.36f
        val inner = r.width() * 0.24f
        box.set(cx - inner, cy - inner, cx + inner, cy + inner)
        neon.roundRect(canvas, box, inner, color, u * 0.4f, 230)
        box.set(cx - inner * 0.35f, cy - inner * 0.35f, cx + inner * 0.35f, cy + inner * 0.35f)
        neon.roundRect(canvas, box, inner, color, u * 0.3f, 200)
        for (k in 0 until 8) {
            val a = k * (Math.PI / 4).toFloat()
            val ca = cos(a)
            val sa = sin(a)
            neon.line(canvas, cx + ca * inner, cy + sa * inner, cx + ca * outer, cy + sa * outer, color, u * 0.55f, 230)
        }
        canvas.restore()
    }

    /** Small capsule label; [x] is its left edge, or its right edge when [alignRight]. */
    fun chip(canvas: Canvas, label: String, x: Float, cy: Float, color: Int, alignRight: Boolean = false) {
        val size = u * 2.6f
        val tw = neon.measure(label, size)
        val left = if (alignRight) x - tw - u * 3.6f else x
        box.set(left, cy - u * 2.3f, left + tw + u * 3.6f, cy + u * 2.3f)
        neon.roundRect(canvas, box, box.height() / 2f, color, u * 0.25f, 220, fillAlpha = 200)
        neon.text(canvas, label, box.centerX(), cy + size * 0.36f, size, DARK)
    }

    /** A soft loading arc turning about once a second. */
    fun spinner(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, time: Float) {
        box.set(cx - r, cy - r, cx + r, cy + r)
        icon.reset()
        icon.addArc(box, (time * 360f) % 360f, 270f)
        neon.path(canvas, icon, color, u * 0.5f, 200)
    }

    /** Segmented tabs; the selected one is filled. */
    fun tabs(canvas: Canvas, rects: List<RectF>, labels: Array<String>, selected: Int, color: Int) {
        for (i in rects.indices) pill(canvas, rects[i], labels[i], color, highlight = i == selected, dim = i != selected)
    }

    /** Centered message capsule; [fade] runs from 0 (gone) to 1 (fully shown). */
    fun banner(canvas: Canvas, message: String, fade: Float, accent: Int) {
        val a = min(1f, fade)
        var size = u * 4.6f
        var tw = neon.measure(message, size)
        val room = w - u * 16f
        if (tw > room) {
            size *= room / tw
            tw = room
        }
        val cy = h * 0.53f
        bannerRect.set(w / 2f - tw / 2f - u * 6f, cy - u * 6f, w / 2f + tw / 2f + u * 6f, cy + u * 6f)
        neon.panel(canvas, bannerRect, u * 6f, (200 * a).toInt())
        neon.roundRect(canvas, bannerRect, u * 6f, accent, u * 0.35f, (230 * a).toInt())
        neon.glowText(canvas, message, w / 2f, cy + size * 0.36f, size, neon.textColor, accent, (255 * a).toInt())
    }

    companion object {
        /** How close (in layout units) a tap must land to a control to count as a near miss. */
        const val NEAR_MISS = 3f
        private const val PRESS_TIME = 0.18f
        private const val DARK = 0xFF14121F.toInt()
    }
}
