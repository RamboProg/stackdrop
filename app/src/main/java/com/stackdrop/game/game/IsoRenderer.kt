package com.stackdrop.game.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

class IsoRenderer {

    var cx = 0f
    var cy = 0f
    var camY = 0f
    var scale = 1f

    /** Matte faces and hairline edges instead of neon glass. */
    var calm = false

    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    fun sx(x: Float, z: Float) = cx + (x - z) * COS30 * scale

    fun sy(x: Float, y: Float, z: Float) = cy + ((x + z) * SIN30 - (y - camY)) * scale

    fun isVisible(b: Block, screenHeight: Int): Boolean {
        val top = sy(b.x, b.y + b.h, b.z)
        val bottom = sy(b.x + b.w, b.y, b.z + b.d)
        return top < screenHeight && bottom > 0f
    }

    /**
     * Dark glassy faces with glowing neon edges in the block's color. In calm mode, [above] casts a
     * soft contact shadow on this block and [moving] gets a slightly brighter top edge.
     */
    fun drawBlock(canvas: Canvas, b: Block, alpha: Int = 255, above: Block? = null, moving: Boolean = false) {
        if (calm) {
            drawMatte(canvas, b, alpha, above, moving)
            return
        }
        val x0 = b.x
        val z0 = b.z
        val y0 = b.y
        val x1 = b.x + b.w
        val z1 = b.z + b.d
        val y1 = b.y + b.h

        face(canvas, Palette.shade(b.color, 0.5f), alpha, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1)
        face(canvas, Palette.shade(b.color, 0.34f), alpha, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0)
        face(canvas, Palette.shade(b.color, 0.22f), alpha, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1)

        val e = max(1.5f, scale * 0.08f)
        val core = Palette.lighten(b.color, 0.45f)

        rectPath(x0, z0, b.w, b.d, y1)
        glow(canvas, path, b.color, core, e, alpha)

        stroke.color = core
        stroke.strokeWidth = e * 0.8f
        stroke.alpha = (alpha * 0.85f).toInt()
        canvas.drawLine(sx(x1, z1), sy(x1, y1, z1), sx(x1, z1), sy(x1, y0, z1), stroke)

        stroke.color = b.color
        stroke.strokeWidth = e * 0.6f
        stroke.alpha = (alpha * 0.5f).toInt()
        canvas.drawLine(sx(x0, z1), sy(x0, y1, z1), sx(x0, z1), sy(x0, y0, z1), stroke)
        canvas.drawLine(sx(x1, z0), sy(x1, y1, z0), sx(x1, z0), sy(x1, y0, z0), stroke)
        canvas.drawLine(sx(x0, z1), sy(x0, y0, z1), sx(x1, z1), sy(x1, y0, z1), stroke)
        canvas.drawLine(sx(x1, z1), sy(x1, y0, z1), sx(x1, z0), sy(x1, y0, z0), stroke)
    }

    fun strokeRect(canvas: Canvas, x: Float, z: Float, w: Float, d: Float, y: Float, color: Int, alpha: Int, width: Float) {
        rectPath(x, z, w, d, y)
        if (calm) {
            stroke.color = color
            stroke.strokeWidth = width * 0.6f
            stroke.alpha = (alpha * 0.6f).toInt()
            canvas.drawPath(path, stroke)
            return
        }
        glow(canvas, path, color, Palette.lighten(color, 0.5f), width, alpha)
    }

    private fun drawMatte(canvas: Canvas, b: Block, alpha: Int, above: Block?, moving: Boolean) {
        val x0 = b.x
        val z0 = b.z
        val y0 = b.y
        val x1 = b.x + b.w
        val z1 = b.z + b.d
        val y1 = b.y + b.h

        face(canvas, b.color, alpha, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1)
        face(canvas, Palette.shade(b.color, 0.70f), alpha, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0)
        face(canvas, Palette.shade(b.color, 0.84f), alpha, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1)
        if (above != null) contactShadow(canvas, b, above, alpha)

        rectPath(x0, z0, b.w, b.d, y1)
        stroke.color = Palette.lighten(b.color, if (moving) 0.25f else 0.2f)
        stroke.strokeWidth = max(1f, scale * 0.03f)
        stroke.alpha = alpha * (if (moving) 140 else 120) / 255
        canvas.drawPath(path, stroke)
    }

    /** Darkens the top of [b]'s visible sides where [above] sits flush with or overhangs them. */
    private fun contactShadow(canvas: Canvas, b: Block, above: Block, alpha: Int) {
        val y1 = b.y + b.h
        val y0 = y1 - min(b.h, SHADOW_DEPTH)
        val a = alpha * SHADOW_ALPHA / 255
        val x1 = b.x + b.w
        val z1 = b.z + b.d

        if (above.x + above.w >= x1 - EDGE) {
            val za = max(b.z, above.z)
            val zb = min(z1, above.z + above.d)
            if (zb > za) face(canvas, Color.BLACK, a, x1, y1, za, x1, y1, zb, x1, y0, zb, x1, y0, za)
        }
        if (above.z + above.d >= z1 - EDGE) {
            val xa = max(b.x, above.x)
            val xb = min(x1, above.x + above.w)
            if (xb > xa) face(canvas, Color.BLACK, a, xa, y1, z1, xb, y1, z1, xb, y0, z1, xa, y0, z1)
        }
    }

    private fun rectPath(x: Float, z: Float, w: Float, d: Float, y: Float) {
        path.reset()
        path.moveTo(sx(x, z), sy(x, y, z))
        path.lineTo(sx(x + w, z), sy(x + w, y, z))
        path.lineTo(sx(x + w, z + d), sy(x + w, y, z + d))
        path.lineTo(sx(x, z + d), sy(x, y, z + d))
        path.close()
    }

    private fun glow(canvas: Canvas, p: Path, color: Int, core: Int, width: Float, alpha: Int) {
        stroke.color = color
        stroke.strokeWidth = width * 3.5f
        stroke.alpha = (alpha * 0.2f).toInt()
        canvas.drawPath(p, stroke)
        stroke.color = core
        stroke.strokeWidth = width
        stroke.alpha = alpha
        canvas.drawPath(p, stroke)
    }

    private fun face(
        canvas: Canvas, color: Int, alpha: Int,
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        qx: Float, qy: Float, qz: Float,
        dx: Float, dy: Float, dz: Float,
    ) {
        path.reset()
        path.moveTo(sx(ax, az), sy(ax, ay, az))
        path.lineTo(sx(bx, bz), sy(bx, by, bz))
        path.lineTo(sx(qx, qz), sy(qx, qy, qz))
        path.lineTo(sx(dx, dz), sy(dx, dy, dz))
        path.close()
        fill.color = color
        fill.alpha = alpha
        canvas.drawPath(path, fill)
    }

    companion object {
        val COS30 = cos(PI / 6).toFloat()
        const val SIN30 = 0.5f
        private const val SHADOW_DEPTH = 0.35f
        private const val SHADOW_ALPHA = 26
        private const val EDGE = 0.01f
    }
}
