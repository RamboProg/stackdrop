package com.stackdrop.game.game.scenery

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.stackdrop.game.game.Ambient
import com.stackdrop.game.game.ColorMath
import com.stackdrop.game.game.Theme
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Slow ambient motion behind the tower: stars, fog, bubbles, aurora, petals or motes. Positions are
 * a pure function of time over a fixed seeded array, so nothing is allocated per frame. Every motion
 * and brightness change stays below 0.5 Hz.
 */
class AmbientLayer {

    private val px = FloatArray(MAX)
    private val py = FloatArray(MAX)
    private val size = FloatArray(MAX)
    private val phase = FloatArray(MAX)
    private val speed = FloatArray(MAX)
    private var count = 0

    private var w = 0f
    private var h = 0f
    private var u = 1f
    private var builtFor: Theme? = null
    private var band: Bitmap? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val oval = RectF()

    fun resize(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        u = w / 100f
        builtFor = null
    }

    /** Layers that sit behind the scenery silhouettes. */
    fun drawBack(canvas: Canvas, theme: Theme, rise: Float, time: Float, reduced: Boolean) {
        if (w == 0f) return
        if (builtFor !== theme) build(theme)
        val n = visible(reduced)
        when (theme.ambient) {
            Ambient.STARS -> stars(canvas, theme, rise, time, n, 0.45f)
            Ambient.AURORA -> {
                ribbons(canvas, rise, time)
                stars(canvas, theme, rise, time, n, 0.4f)
            }
            else -> Unit
        }
    }

    /** Layers that drift in front of the scenery but still behind the tower. */
    fun drawFront(canvas: Canvas, theme: Theme, rise: Float, time: Float, reduced: Boolean) {
        if (w == 0f) return
        if (builtFor !== theme) build(theme)
        val n = visible(reduced)
        when (theme.ambient) {
            Ambient.FOG -> fog(canvas, rise, time)
            Ambient.BUBBLES -> bubbles(canvas, theme, time, n)
            Ambient.PETALS -> petals(canvas, theme, time, n)
            Ambient.MOTES -> motes(canvas, theme, time, n)
            else -> Unit
        }
    }

    private fun visible(reduced: Boolean) = if (reduced) count / 2 else count

    private fun stars(canvas: Canvas, theme: Theme, rise: Float, time: Float, n: Int, band: Float) {
        paint.color = theme.text
        for (i in 0 until n) {
            val twinkle = 0.5f + 0.5f * sin(time * TAU * 0.2f + phase[i])
            paint.alpha = (70 + 60 * twinkle).toInt()
            canvas.drawCircle(px[i] * w, py[i] * h * band + rise * 0.05f, u * (0.15f + 0.2f * size[i]), paint)
        }
    }

    private fun ribbons(canvas: Canvas, rise: Float, time: Float) {
        val bmp = band ?: return
        for (k in 0..1) {
            val drift = sin(time * TAU * 0.05f + k * 2.1f)
            bitmapPaint.alpha = (255 * (0.75f + 0.25f * sin(time * TAU * 0.05f + k))).toInt()
            canvas.drawBitmap(bmp, -w * 0.15f + drift * w * 0.08f, h * (0.08f + 0.12f * k) + rise * 0.05f, bitmapPaint)
        }
    }

    private fun fog(canvas: Canvas, rise: Float, time: Float) {
        val bmp = band ?: return
        bitmapPaint.alpha = 255
        for (k in 0..1) {
            val offset = (time * 0.02f * w * (1f - 0.3f * k)) % w
            val y = h * (0.58f + 0.12f * k) + rise * (0.12f + 0.08f * k)
            if (y > h) continue
            canvas.drawBitmap(bmp, offset - w, y, bitmapPaint)
            canvas.drawBitmap(bmp, offset, y, bitmapPaint)
        }
    }

    private fun bubbles(canvas: Canvas, theme: Theme, time: Float, n: Int) {
        val color = ColorMath.mix(theme.text, theme.accent, 0.5f)
        ring.color = color
        ring.strokeWidth = max(1f, u * 0.15f)
        paint.color = color
        val loop = h * 1.1f
        for (i in 0 until n) {
            val y = h - (py[i] * loop + time * (0.02f + 0.03f * speed[i]) * h) % loop + h * 0.05f
            val x = px[i] * w + sin(time * TAU * 0.25f + phase[i]) * u * 0.8f
            val r = u * (0.35f + 0.5f * size[i])
            ring.alpha = 70
            canvas.drawCircle(x, y, r, ring)
            paint.alpha = 60
            canvas.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.2f, paint)
        }
    }

    private fun petals(canvas: Canvas, theme: Theme, time: Float, n: Int) {
        paint.color = ColorMath.mix(theme.accent2, SOFT_WHITE, 0.35f)
        paint.alpha = 190
        val loop = h * 1.15f
        for (i in 0 until n) {
            val y = (py[i] * loop + time * (0.03f + 0.03f * speed[i]) * h) % loop - h * 0.1f
            val x = px[i] * w + sin(time * TAU * (0.2f + 0.1f * size[i]) + phase[i]) * u * 3f
            val s = 0.7f + 0.6f * size[i]
            val spin = if (i % 2 == 0) 1f else -1f
            canvas.save()
            canvas.rotate(phase[i] * 57f + spin * time * (20f + 20f * speed[i]), x, y)
            oval.set(x - u * s, y - u * 0.55f * s, x + u * s, y + u * 0.55f * s)
            canvas.drawOval(oval, paint)
            canvas.restore()
        }
    }

    private fun motes(canvas: Canvas, theme: Theme, time: Float, n: Int) {
        paint.color = theme.textDim
        for (i in 0 until n) {
            val y = h - (py[i] * h + time * (0.01f + 0.01f * speed[i]) * h) % h
            val x = px[i] * w + sin(time * TAU * 0.15f + phase[i]) * u * 2f
            paint.alpha = (40 + 50 * size[i]).toInt()
            canvas.drawCircle(x, y, u * (0.2f + 0.25f * size[i]), paint)
        }
    }

    private fun build(theme: Theme) {
        builtFor = theme
        band?.recycle()
        band = null
        count = when (theme.ambient) {
            Ambient.STARS -> 16
            Ambient.BUBBLES -> 20
            Ambient.AURORA -> 14
            Ambient.PETALS -> 24
            Ambient.MOTES -> 16
            Ambient.FOG, Ambient.NONE -> 0
        }
        val rnd = Random(theme.ambient.ordinal * 17 + 3)
        for (i in 0 until count) {
            px[i] = rnd.nextFloat()
            py[i] = rnd.nextFloat()
            size[i] = rnd.nextFloat()
            phase[i] = rnd.nextFloat() * TAU
            speed[i] = rnd.nextFloat()
        }
        band = when (theme.ambient) {
            Ambient.FOG -> fogBand(theme, rnd)
            Ambient.AURORA -> ribbon(theme)
            else -> null
        }
    }

    /** A horizontally seamless strip of soft fog blobs. */
    private fun fogBand(theme: Theme, rnd: Random): Bitmap {
        val bw = w.toInt().coerceAtLeast(1)
        val bh = (h * 0.14f).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val color = ColorMath.mix(theme.skyTop, SOFT_WHITE, 0.5f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cy = bh / 2f
        val r = bh / 2f
        repeat(6) {
            val cx = rnd.nextFloat() * bw
            for (dx in floatArrayOf(-bw.toFloat(), 0f, bw.toFloat())) {
                val x = cx + dx
                p.shader = RadialGradient(
                    x, cy, r,
                    ColorMath.withAlpha(color, 70), ColorMath.withAlpha(color, 0),
                    Shader.TileMode.CLAMP,
                )
                c.save()
                c.scale(3f, 1f, x, cy)
                c.drawCircle(x, cy, r, p)
                c.restore()
            }
        }
        return bmp
    }

    /** One wavy aurora curtain, wider than the screen so its slow sideways drift never shows an edge. */
    private fun ribbon(theme: Theme): Bitmap {
        val bw = (w * 1.3f).toInt().coerceAtLeast(1)
        val bh = (h * 0.22f).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val path = Path()
        val steps = 40
        for (i in 0..steps) {
            val x = bw * i / steps.toFloat()
            val y = bh * (0.3f + 0.15f * sin(i / steps.toFloat() * TAU * 1.5f))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        for (i in steps downTo 0) {
            val x = bw * i / steps.toFloat()
            val y = bh * (0.65f + 0.15f * sin(i / steps.toFloat() * TAU * 1.5f + 0.6f))
            path.lineTo(x, y)
        }
        path.close()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, bh.toFloat(),
                intArrayOf(ColorMath.withAlpha(theme.accent, 0), ColorMath.withAlpha(theme.accent, 35), ColorMath.withAlpha(theme.accent2, 0)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        Canvas(bmp).drawPath(path, p)
        return bmp
    }

    private companion object {
        const val MAX = 40
        const val TAU = 6.2831855f
        const val SOFT_WHITE = 0xFFF8F4EE.toInt()
    }
}
