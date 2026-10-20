package com.stackdrop.game.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.stackdrop.game.game.Palette

/**
 * The 12 preset avatars, drawn procedurally: a small isometric stack or crane glyph on a soft circle
 * in calm palette colors. No images are downloaded and Google photos are never shown.
 */
class Avatars {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()

    fun draw(canvas: Canvas, index: Int, cx: Float, cy: Float, r: Float, alpha: Int = 255) {
        val i = Math.floorMod(index, COUNT)
        val bg = COLORS[i]
        fill.color = bg
        fill.alpha = alpha
        canvas.drawCircle(cx, cy, r, fill)
        val ink = Palette.shade(bg, 0.42f)
        val variant = i / 2
        if (i % 2 == 0) stack(canvas, cx, cy, r, ink, variant, alpha) else crane(canvas, cx, cy, r, ink, variant, alpha)
    }

    /** One to three slabs; the last three variants nudge the top slab sideways. */
    private fun stack(canvas: Canvas, cx: Float, cy: Float, r: Float, ink: Int, variant: Int, alpha: Int) {
        val slabs = 1 + variant % 3
        val s = r * 0.42f
        val h = r * 0.2f
        val base = cy + r * 0.05f + (slabs - 1) * h * 0.5f
        for (k in 0 until slabs) {
            val shift = if (variant >= 3 && k == slabs - 1) s * 0.3f else 0f
            box(canvas, cx + shift, base - k * h, s, h, ink, alpha)
        }
    }

    /** An isometric slab whose top face is centered on ([cx], [top]). */
    private fun box(canvas: Canvas, cx: Float, top: Float, s: Float, h: Float, ink: Int, alpha: Int) {
        val half = s * 0.5f
        face(canvas, cx - s, top, cx, top + half, cx, top + half + h, cx - s, top + h, ink, alpha)
        face(canvas, cx, top + half, cx + s, top, cx + s, top + h, cx, top + half + h, Palette.shade(ink, 0.8f), alpha)
        face(canvas, cx - s, top, cx, top - half, cx + s, top, cx, top + half, Palette.lighten(ink, 0.3f), alpha)
    }

    private fun face(
        canvas: Canvas,
        x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float,
        color: Int, alpha: Int,
    ) {
        path.reset()
        path.moveTo(x0, y0)
        path.lineTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.close()
        fill.color = color
        fill.alpha = alpha
        canvas.drawPath(path, fill)
    }

    /** Mast, jib and a hanging module; variants flip the jib and vary the load size. */
    private fun crane(canvas: Canvas, cx: Float, cy: Float, r: Float, ink: Int, variant: Int, alpha: Int) {
        val dir = if (variant % 2 == 0) 1f else -1f
        val load = r * (0.16f + 0.05f * (variant % 3))
        stroke.color = ink
        stroke.alpha = alpha
        stroke.strokeWidth = r * 0.09f
        val mastX = cx - dir * r * 0.32f
        canvas.drawLine(mastX, cy + r * 0.55f, mastX, cy - r * 0.5f, stroke)
        canvas.drawLine(mastX - dir * r * 0.15f, cy - r * 0.45f, mastX + dir * r * 0.8f, cy - r * 0.45f, stroke)
        val hookX = mastX + dir * r * 0.55f
        stroke.strokeWidth = r * 0.05f
        canvas.drawLine(hookX, cy - r * 0.45f, hookX, cy - r * 0.05f, stroke)
        fill.color = Palette.lighten(ink, 0.2f)
        fill.alpha = alpha
        canvas.drawRect(hookX - load, cy - r * 0.05f, hookX + load, cy - r * 0.05f + load * 1.3f, fill)
    }

    companion object {
        const val COUNT = 12

        /** Same hash as the backend (codes.ts), so guests keep their avatar after choosing a name. */
        fun defaultFor(uid: String?): Int = if (uid.isNullOrEmpty()) 0 else uid.sumOf { it.code } % COUNT

        private val COLORS = longArrayOf(
            0xFFF2C9A6, 0xFF9FE3C9, 0xFF8FD3C8, 0xFFB9A8F0, 0xFFE7A9B5, 0xFFE3A765,
            0xFFA9B8CC, 0xFFC9D8D2, 0xFFE8A0A8, 0xFF8FB3A0, 0xFFF0C987, 0xFFD98C9C,
        ).map { it.toInt() }.toIntArray()
    }
}
