package com.stackdrop.game.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.stackdrop.game.game.Palette
import com.stackdrop.game.game.Theme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Glow drawing primitives shared by the HUD and both game modes. With a calm theme ([glow] < 0.5)
 * strokes become one soft pass plus a crisp line, and panels become frosted glass.
 */
class Neon(font: Typeface) {

    var u = 1f

    var glow = 1f
        private set

    /** Main text color of the active theme. */
    var textColor = Color.WHITE
        private set

    private var panelColor = Color.BLACK
    private var frosted = false
    private val soft get() = glow < SOFT_BELOW

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape = Path()

    fun applyTheme(t: Theme) {
        glow = t.glow
        textColor = t.text
        panelColor = t.panel
        frosted = t.calm
    }

    fun line(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, width: Float, alpha: Int = 255) {
        for (p in 0 until passes()) {
            pass(p, color, width, alpha)
            canvas.drawLine(x0, y0, x1, y1, stroke)
        }
    }

    fun path(canvas: Canvas, path: Path, color: Int, width: Float, alpha: Int = 255) {
        for (p in 0 until passes()) {
            pass(p, color, width, alpha)
            canvas.drawPath(path, stroke)
        }
    }

    fun roundRect(
        canvas: Canvas, r: RectF, radius: Float, color: Int, width: Float,
        alpha: Int = 255, fillAlpha: Int = 0,
    ) {
        val rr = cornerRadius(r, radius)
        if (fillAlpha > 0) {
            fill.color = color
            fill.alpha = fillAlpha
            canvas.drawRoundRect(r, rr, rr, fill)
        }
        for (p in 0 until passes()) {
            pass(p, color, width, alpha)
            canvas.drawRoundRect(r, rr, rr, stroke)
        }
    }

    fun dot(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Int = 255) {
        if (soft) {
            fill.color = color
            fill.alpha = (alpha * 0.15f).toInt()
            canvas.drawCircle(cx, cy, r * 1.8f, fill)
            fill.alpha = alpha
            canvas.drawCircle(cx, cy, r, fill)
            return
        }
        fill.color = color
        fill.alpha = alpha / 5
        canvas.drawCircle(cx, cy, r * 2.4f, fill)
        fill.alpha = alpha / 2
        canvas.drawCircle(cx, cy, r * 1.5f, fill)
        fill.color = Palette.lighten(color, 0.6f)
        fill.alpha = alpha
        canvas.drawCircle(cx, cy, r, fill)
    }

    /** Backing so text stays readable over busy scenes: dark glass for neon, frosted glass for calm. */
    fun panel(canvas: Canvas, r: RectF, radius: Float, alpha: Int) {
        if (!frosted) {
            fill.color = Color.BLACK
            fill.alpha = alpha
            canvas.drawRoundRect(r, radius, radius, fill)
            return
        }
        val rr = cornerRadius(r, radius)
        fill.color = panelColor
        fill.alpha = min(alpha, FROST_ALPHA)
        canvas.drawRoundRect(r, rr, rr, fill)
        setStroke(textColor, max(1f, u * 0.1f), min(FROST_BORDER, FROST_BORDER * alpha / FROST_ALPHA))
        canvas.drawRoundRect(r, rr, rr, stroke)
    }

    fun gem(canvas: Canvas, cx: Float, cy: Float, r: Float, alpha: Int = 255) {
        shape.reset()
        shape.moveTo(cx, cy - r)
        shape.lineTo(cx + r * 0.85f, cy - r * 0.2f)
        shape.lineTo(cx, cy + r)
        shape.lineTo(cx - r * 0.85f, cy - r * 0.2f)
        shape.close()
        fill.color = GEM
        fill.alpha = (alpha * 0.35f).toInt()
        canvas.drawPath(shape, fill)
        path(canvas, shape, GEM, r * 0.16f, alpha)
    }

    fun heart(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, filled: Boolean, alpha: Int = 255) {
        shape.reset()
        shape.moveTo(cx, cy + r * 0.9f)
        shape.cubicTo(cx - r * 1.6f, cy - r * 0.2f, cx - r * 0.6f, cy - r * 1.3f, cx, cy - r * 0.45f)
        shape.cubicTo(cx + r * 0.6f, cy - r * 1.3f, cx + r * 1.6f, cy - r * 0.2f, cx, cy + r * 0.9f)
        shape.close()
        if (filled) {
            fill.color = color
            fill.alpha = (alpha * 0.55f).toInt()
            canvas.drawPath(shape, fill)
        }
        path(canvas, shape, color, r * 0.16f, if (filled) alpha else alpha / 2)
    }

    fun glowText(
        canvas: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
        glow: Int = color, alpha: Int = 255, align: Paint.Align = Paint.Align.CENTER,
    ) {
        val a = alpha.coerceIn(0, 255)
        val ga = (a * this.glow).toInt().coerceIn(0, 255)
        textPaint.textSize = size
        textPaint.textAlign = align
        textPaint.color = glow
        textPaint.alpha = a
        textPaint.setShadowLayer(size * 0.3f * this.glow, 0f, 0f, Palette.withAlpha(glow, ga))
        canvas.drawText(s, x, y, textPaint)
        textPaint.color = color
        textPaint.alpha = a
        textPaint.setShadowLayer(size * 0.08f * this.glow, 0f, 0f, Palette.withAlpha(glow, ga))
        canvas.drawText(s, x, y, textPaint)
        textPaint.clearShadowLayer()
    }

    fun text(
        canvas: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
        alpha: Int = 255, align: Paint.Align = Paint.Align.CENTER,
    ) {
        textPaint.textSize = size
        textPaint.textAlign = align
        textPaint.color = color
        textPaint.alpha = alpha.coerceIn(0, 255)
        canvas.drawText(s, x, y, textPaint)
    }

    /** Title text with a cyan/magenta split that occasionally glitches wider; calm themes get a soft glow instead. */
    fun chroma(canvas: Canvas, s: String, x: Float, y: Float, size: Float, time: Float, glow: Int) {
        if (soft) {
            glowText(canvas, s, x, y, size, textColor, glow)
            return
        }
        val spike = sin(time * 1.9f) * sin(time * 3.7f + 1f) > 0.8f
        val off = if (spike) u * 1.4f else u * 0.35f
        val jy = if (spike) u * 0.3f else 0f
        text(canvas, s, x - off, y - jy, size, CYAN, 150)
        text(canvas, s, x + off, y + jy, size, MAGENTA, 150)
        glowText(canvas, s, x, y, size, textColor, glow)
    }

    fun measure(s: String, size: Float): Float {
        textPaint.textSize = size
        return textPaint.measureText(s)
    }

    private fun passes() = if (soft) 2 else PASSES

    private fun cornerRadius(r: RectF, radius: Float) =
        if (frosted) min(radius * FROST_RADIUS, min(r.width(), r.height()) / 2f) else radius

    private fun pass(p: Int, color: Int, width: Float, alpha: Int) {
        if (soft) {
            if (p == 0) setStroke(color, width * 2f, alpha * 0.4f * glow)
            else setStroke(color, width, alpha.toFloat())
            return
        }
        when (p) {
            0 -> setStroke(color, width * 4.5f, alpha * 0.07f)
            1 -> setStroke(color, width * 2.2f, alpha * 0.22f)
            2 -> setStroke(color, width, alpha.toFloat())
            else -> setStroke(Palette.lighten(color, 0.65f), width * 0.4f, alpha.toFloat())
        }
    }

    private fun setStroke(color: Int, width: Float, alpha: Float) {
        stroke.color = color
        stroke.strokeWidth = width
        stroke.alpha = alpha.toInt().coerceIn(0, 255)
    }

    companion object {
        const val GEM = 0xFF4FE3FF.toInt()
        const val GOLD = 0xFFFFD54F.toInt()
        const val RED = 0xFFFF3D6E.toInt()
        const val AMBER = 0xFFFFB347.toInt()
        private const val CYAN = 0xFF00F0FF.toInt()
        private const val MAGENTA = 0xFFFF2BD6.toInt()
        private const val PASSES = 4
        private const val SOFT_BELOW = 0.5f
        private const val FROST_ALPHA = 170
        private const val FROST_BORDER = 60f
        private const val FROST_RADIUS = 1.3f
    }
}
