package com.stackdrop.game.game.tower

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.stackdrop.game.game.ColorMath
import com.stackdrop.game.game.Palette
import com.stackdrop.game.game.Theme
import kotlin.random.Random

/** Two pre-rendered layers of neon city silhouettes sitting on the horizon. */
class Skyline {

    private var w = 0
    private var h = 0
    private var far: Bitmap? = null
    private var near: Bitmap? = null
    private var builtFor: Theme? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun resize(width: Int, height: Int) {
        w = width
        h = height
        builtFor = null
    }

    fun draw(canvas: Canvas, theme: Theme, horizon: Float) {
        if (w == 0) return
        if (builtFor !== theme || far == null || near == null) {
            far = build(theme, (h * 0.2f).toInt(), 11, 0.55f)
            near = build(theme, (h * 0.28f).toInt(), 23, 1f)
            builtFor = theme
        }
        val f = far ?: return
        val n = near ?: return
        if (horizon - n.height > h) return
        canvas.drawBitmap(f, 0f, horizon - f.height + 1f, paint)
        canvas.drawBitmap(n, 0f, horizon - n.height + h * 0.012f, paint)
    }

    private fun build(theme: Theme, height: Int, seed: Int, bright: Float): Bitmap {
        val bmp = Bitmap.createBitmap(w, height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Random(seed)
        val u = w / 100f
        if (theme.calm) {
            drawCalm(c, theme, height, rnd, u, bright)
            return bmp
        }
        val body = Palette.hsv(theme.bgHue, theme.bgSat * 0.8f, 0.05f + 0.06f * bright)
        val edge = Palette.accent(theme)
        val lit = Palette.accent2(theme)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = u * 0.25f
        }

        var x = -rnd.nextFloat() * u * 3f
        while (x < w) {
            val bw = u * (5f + rnd.nextFloat() * 9f)
            val bh = height * (0.3f + rnd.nextFloat() * 0.62f)
            val top = height - bh

            fill.color = body
            c.drawRect(x, top, x + bw, height.toFloat(), fill)

            stroke.color = edge
            stroke.alpha = (130 * bright).toInt()
            c.drawLine(x, top, x + bw, top, stroke)
            stroke.alpha = (50 * bright).toInt()
            c.drawLine(x, top, x, height.toFloat(), stroke)
            c.drawLine(x + bw, top, x + bw, height.toFloat(), stroke)

            fill.color = lit
            var wy = top + u * 1.6f
            while (wy < height - u * 1.5f) {
                var wx = x + u * 1.1f
                while (wx < x + bw - u * 1.6f) {
                    if (rnd.nextFloat() < 0.3f) {
                        fill.alpha = ((90 + rnd.nextInt(140)) * bright).toInt()
                        c.drawRect(wx, wy, wx + u * 0.75f, wy + u, fill)
                    }
                    wx += u * 1.7f
                }
                wy += u * 2.3f
            }

            if (rnd.nextFloat() < 0.3f && top > u * 4f) {
                stroke.alpha = (140 * bright).toInt()
                c.drawLine(x + bw / 2f, top, x + bw / 2f, top - u * 3f, stroke)
                fill.color = BEACON
                fill.alpha = (255 * bright).toInt()
                c.drawCircle(x + bw / 2f, top - u * 3f, u * 0.45f, fill)
            }

            x += bw + u * (0.2f + rnd.nextFloat() * 1.4f)
        }
        return bmp
    }

    /** Flat silhouettes tinted between the sky and the ground, with a few warm windows on dark skies. */
    private fun drawCalm(c: Canvas, theme: Theme, height: Int, rnd: Random, u: Float, bright: Float) {
        val body = ColorMath.mix(theme.skyBottom, theme.ground, 0.3f + 0.4f * bright)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        var x = -rnd.nextFloat() * u * 3f
        while (x < w) {
            val bw = u * (5f + rnd.nextFloat() * 9f)
            val bh = height * (0.3f + rnd.nextFloat() * 0.62f)
            val top = height - bh
            fill.color = body
            c.drawRect(x, top, x + bw, height.toFloat(), fill)

            if (!theme.light) {
                fill.color = CALM_WINDOW
                var wy = top + u * 1.6f
                while (wy < height - u * 1.5f) {
                    var wx = x + u * 1.1f
                    while (wx < x + bw - u * 1.6f) {
                        if (rnd.nextFloat() < 0.18f) {
                            fill.alpha = ((40 + rnd.nextInt(40)) * bright).toInt()
                            c.drawRect(wx, wy, wx + u * 0.75f, wy + u, fill)
                        }
                        wx += u * 1.7f
                    }
                    wy += u * 2.3f
                }
            }
            x += bw + u * (0.2f + rnd.nextFloat() * 1.4f)
        }
    }

    private companion object {
        const val BEACON = 0xFFFF4060.toInt()
        const val CALM_WINDOW = 0xFFFFE3A3.toInt()
    }
}
