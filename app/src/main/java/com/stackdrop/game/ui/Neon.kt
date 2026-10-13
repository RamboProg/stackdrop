package com.stackdrop.game.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.stackdrop.game.game.Palette
import kotlin.math.sin

/** Glow drawing primitives shared by the HUD and both game modes. */
class Neon(font: Typeface) {

    var u = 1f

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape = Path()

    fun line(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, width: Float, alpha: Int = 255) {
        for (p in 0 until PASSES) {
            pass(p, color, width, alpha)
            canvas.drawLine(x0, y0, x1, y1, stroke)
        }
    }

    fun path(canvas: Canvas, path: Path, color: Int, width: Float, alpha: Int = 255) {
        for (p in 0 until PASSES) {
            pass(p, color, width, alpha)
            canvas.drawPath(path, stroke)
        }
    }

    fun roundRect(
        canvas: Canvas, r: RectF, radius: Float, color: Int, width: Float,
        alpha: Int = 255, fillAlpha: Int = 0,
    ) {
        if (fillAlpha > 0) {
            fill.color = color
            fill.alpha = fillAlpha
            canvas.drawRoundRect(r, radius, radius, fill)
        }
        for (p in 0 until PASSES) {
            pass(p, color, width, alpha)
            canvas.drawRoundRect(r, radius, radius, stroke)
        }
    }

    fun dot(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Int = 255) {
        fill.color = color
        fill.alpha = alpha / 5
        canvas.drawCircle(cx, cy, r * 2.4f, fill)
        fill.alpha = alpha / 2
        canvas.drawCircle(cx, cy, r * 1.5f, fill)
        fill.color = Palette.lighten(color, 0.6f)
        fill.alpha = alpha
        canvas.drawCircle(cx, cy, r, fill)
    }

    /** Dark glass backing so text stays readable over busy scenes. */
    fun panel(canvas: Canvas, r: RectF, radius: Float, alpha: Int) {
        fill.color = Color.BLACK
        fill.alpha = alpha
        canvas.drawRoundRect(r, radius, radius, fill)
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
        textPaint.textSize = size
        textPaint.textAlign = align
        textPaint.color = glow
        textPaint.alpha = a
        textPaint.setShadowLayer(size * 0.3f, 0f, 0f, Palette.withAlpha(glow, a))
        canvas.drawText(s, x, y, textPaint)
        textPaint.color = color
        textPaint.alpha = a
        textPaint.setShadowLayer(size * 0.08f, 0f, 0f, Palette.withAlpha(glow, a))
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

    /** Title text with a cyan/magenta split that occasionally glitches wider. */
    fun chroma(canvas: Canvas, s: String, x: Float, y: Float, size: Float, time: Float, glow: Int) {
        val spike = sin(time * 1.9f) * sin(time * 3.7f + 1f) > 0.8f
        val off = if (spike) u * 1.4f else u * 0.35f
        val jy = if (spike) u * 0.3f else 0f
        text(canvas, s, x - off, y - jy, size, CYAN, 150)
        text(canvas, s, x + off, y + jy, size, MAGENTA, 150)
        glowText(canvas, s, x, y, size, Color.WHITE, glow)
    }

    fun measure(s: String, size: Float): Float {
        textPaint.textSize = size
        return textPaint.measureText(s)
    }

    private fun pass(p: Int, color: Int, width: Float, alpha: Int) {
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
    }
}
