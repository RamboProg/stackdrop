package com.stackdrop.game.game.scenery

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.stackdrop.game.game.ColorMath
import com.stackdrop.game.game.Scenery
import com.stackdrop.game.game.Theme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Calm landscapes: a soft three-stop sky and three silhouette layers pre-rendered on resize or theme
 * change, drawn with parallax against the camera rise.
 */
class CalmScenery {

    private var w = 0
    private var h = 0
    private var builtFor: Theme? = null

    private val layers = arrayOfNulls<Bitmap>(LAYERS)
    private val layerFill = Array(LAYERS) { Paint() }
    private var sun: Bitmap? = null

    private val skyPaint = Paint()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ray = Path()

    fun resize(width: Int, height: Int) {
        w = width
        h = height
        builtFor = null
    }

    /** How far the camera has climbed, in px, recovered from the horizon line the modes pass in. */
    fun riseFor(horizon: Float) = (horizon - h * BASE_HORIZON) / HORIZON_RATE

    /** Sky, plus the sun disc or light rays some sceneries have. */
    fun drawSky(canvas: Canvas, theme: Theme, rise: Float, time: Float) {
        if (w == 0) return
        if (builtFor !== theme) build(theme)
        val fw = w.toFloat()
        val fh = h.toFloat()
        canvas.drawRect(0f, 0f, fw, fh, skyPaint)
        when (theme.scenery) {
            Scenery.HILLS -> sun?.let {
                canvas.drawBitmap(it, fw * 0.62f - it.width / 2f, fh * 0.6f + rise * 0.04f - it.height / 2f, bitmapPaint)
            }
            Scenery.LAGOON -> drawRays(canvas, fw, fh, time)
            else -> Unit
        }
    }

    fun drawLayers(canvas: Canvas, theme: Theme, rise: Float) {
        if (w == 0) return
        if (builtFor !== theme) build(theme)
        val fw = w.toFloat()
        val fh = h.toFloat()
        if (theme.scenery == Scenery.SLATE) {
            val y = fh * LAYER_BOTTOM[1] + rise * PARALLAX[1]
            if (y < fh) {
                canvas.drawRect(0f, y, fw, fh, layerFill[LAYERS - 1])
                canvas.drawLine(0f, y, fw, y, linePaint)
            }
            return
        }
        for (i in 0 until LAYERS) {
            val bmp = layers[i] ?: continue
            val bottom = fh * LAYER_BOTTOM[i] + rise * PARALLAX[i]
            if (bottom - bmp.height > fh) continue
            canvas.drawBitmap(bmp, 0f, bottom - bmp.height, bitmapPaint)
            // Solid fill under each layer so a faster near layer never uncovers the sky.
            if (bottom < fh) canvas.drawRect(0f, bottom - 1f, fw, fh, layerFill[i])
        }
    }

    private fun drawRays(canvas: Canvas, fw: Float, fh: Float, time: Float) {
        val sway = sin(time * TAU * RAY_HZ) * 4f
        for (k in RAY_X.indices) {
            canvas.save()
            canvas.translate(fw * RAY_X[k], -fh * 0.05f)
            canvas.rotate(RAY_ANGLE[k] + sway * (0.6f + 0.2f * k))
            canvas.drawPath(ray, rayPaint)
            canvas.restore()
        }
    }

    private fun build(theme: Theme) {
        builtFor = theme
        val fw = w.toFloat()
        val fh = h.toFloat()
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, fh,
            intArrayOf(theme.skyTop, theme.skyMid, theme.skyBottom), floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )

        for (i in 0 until LAYERS) {
            layers[i]?.recycle()
            layers[i] = null
            layerFill[i].color = ColorMath.mix(theme.skyBottom, theme.ground, LAYER_DEPTH[i])
        }
        sun?.recycle()
        sun = null

        when (theme.scenery) {
            Scenery.HILLS -> sun = buildSun(theme, fw)
            Scenery.LAGOON -> buildRay(theme, fw, fh)
            Scenery.SLATE -> {
                linePaint.color = ColorMath.withAlpha(theme.textDim, 60)
                linePaint.strokeWidth = max(1f, fw * 0.0015f)
                return
            }
            else -> Unit
        }
        for (i in 0 until LAYERS) layers[i] = buildLayer(theme, i, fw, fh)
    }

    private fun buildLayer(theme: Theme, i: Int, fw: Float, fh: Float): Bitmap {
        val bh = (fh * LAYER_HEIGHT[i]).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Random(SEED + i * 13 + theme.scenery.ordinal * 31)
        val color = layerFill[i].color
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val hh = bh.toFloat()
        when (theme.scenery) {
            Scenery.HILLS -> c.drawPath(ridge(fw, hh, 0.45f + 0.1f * i, 0.22f, 1.2f + 0.5f * i, rnd), p)
            Scenery.FOREST_MIST -> {
                c.drawPath(ridge(fw, hh, 0.8f, 0.06f, 1.5f, rnd), p)
                pines(c, fw, hh, i, rnd, p)
            }
            Scenery.LAGOON -> {
                c.drawPath(ridge(fw, hh, 0.62f + 0.06f * i, 0.14f, 2f + i, rnd), p)
                if (i > 0) kelp(c, fw, hh, rnd, color)
            }
            Scenery.AURORA_PEAKS -> peaks(c, fw, hh, i, rnd, p, ColorMath.mix(color, theme.text, 0.4f - 0.1f * i))
            Scenery.PETALS_GARDEN -> {
                c.drawPath(ridge(fw, hh, 0.78f, 0.08f, 1.3f, rnd), p)
                canopies(c, fw, hh, i, rnd, p)
            }
            Scenery.DUNES -> {
                if (i == 0) haze(c, fw, hh, theme)
                c.drawPath(ridge(fw, hh, 0.5f + 0.08f * i, 0.2f, 0.9f + 0.4f * i, rnd), p)
            }
            Scenery.SYNTH_GRID, Scenery.SLATE -> Unit
        }
        return bmp
    }

    /** Smooth hill line from two sine waves, filled down to the bottom edge. */
    private fun ridge(width: Float, hh: Float, base: Float, amp: Float, waves: Float, rnd: Random): Path {
        val p1 = rnd.nextFloat() * TAU
        val p2 = rnd.nextFloat() * TAU
        val path = Path()
        path.moveTo(0f, hh)
        var px = 0f
        var py = 0f
        for (i in 0..SAMPLES) {
            val t = i / SAMPLES.toFloat()
            val x = width * t
            val y = hh * (base - amp * (0.65f * sin(t * waves * TAU + p1) + 0.35f * sin(t * waves * 2.3f * TAU + p2)))
            if (i == 0) path.lineTo(x, y) else path.quadTo(px, py, (px + x) / 2f, (py + y) / 2f)
            px = x
            py = y
        }
        path.lineTo(px, py)
        path.lineTo(width, hh)
        path.close()
        return path
    }

    private fun pines(c: Canvas, width: Float, hh: Float, layer: Int, rnd: Random, p: Paint) {
        val path = Path()
        val bottom = hh * 0.86f
        var x = -rnd.nextFloat() * width * 0.04f
        while (x < width + width * 0.04f) {
            val ph = min(hh * 0.95f, hh * (0.4f + rnd.nextFloat() * 0.4f) * (0.7f + 0.15f * layer))
            val pw = ph * 0.38f
            path.reset()
            for (k in 0 until 3) {
                val apex = bottom - ph + ph * 0.25f * k
                val base = apex + ph * 0.45f
                val half = pw * (0.3f + 0.18f * k)
                path.moveTo(x, apex)
                path.lineTo(x + half, base)
                path.lineTo(x - half, base)
                path.close()
            }
            c.drawPath(path, p)
            c.drawRect(x - pw * 0.05f, bottom - ph * 0.1f, x + pw * 0.05f, hh, p)
            x += pw * (0.6f + rnd.nextFloat() * 0.9f)
        }
    }

    private fun kelp(c: Canvas, width: Float, hh: Float, rnd: Random, color: Int) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = width * 0.006f
            this.color = color
        }
        val path = Path()
        repeat(10) {
            val x = rnd.nextFloat() * width
            val top = hh * (0.2f + rnd.nextFloat() * 0.35f)
            val bend = width * (rnd.nextFloat() - 0.5f) * 0.05f
            path.reset()
            path.moveTo(x, hh)
            path.quadTo(x + bend, (hh + top) / 2f, x - bend * 0.5f, top)
            c.drawPath(path, stroke)
        }
    }

    private fun peaks(c: Canvas, width: Float, hh: Float, layer: Int, rnd: Random, p: Paint, snowColor: Int) {
        val n = 5 + layer * 2
        val count = n * 2 + 1
        val xs = FloatArray(count)
        val ys = FloatArray(count)
        val step = width * 1.1f / (count - 1)
        for (i in 0 until count) {
            xs[i] = -width * 0.05f + i * step + (rnd.nextFloat() - 0.5f) * step * 0.5f
            ys[i] = if (i % 2 == 1) hh * (0.12f + rnd.nextFloat() * 0.3f + 0.08f * layer)
            else hh * (0.55f + rnd.nextFloat() * 0.2f)
        }
        val path = Path()
        path.moveTo(xs[0], hh)
        for (i in 0 until count) path.lineTo(xs[i], ys[i])
        path.lineTo(xs[count - 1], hh)
        path.close()
        c.drawPath(path, p)

        val snow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = snowColor }
        for (i in 1 until count - 1 step 2) {
            val f = 0.28f
            val lx = xs[i] + (xs[i - 1] - xs[i]) * f
            val ly = ys[i] + (ys[i - 1] - ys[i]) * f
            val rx = xs[i] + (xs[i + 1] - xs[i]) * f
            val ry = ys[i] + (ys[i + 1] - ys[i]) * f
            path.reset()
            path.moveTo(xs[i], ys[i])
            path.lineTo(rx, ry)
            path.lineTo(xs[i] + (rx - xs[i]) * 0.3f, min(ly, ry) * 0.9f + ys[i] * 0.1f)
            path.lineTo(xs[i] - (xs[i] - lx) * 0.2f, max(ly, ry) * 0.85f + ys[i] * 0.15f)
            path.lineTo(lx, ly)
            path.close()
            c.drawPath(path, snow)
        }
    }

    private fun canopies(c: Canvas, width: Float, hh: Float, layer: Int, rnd: Random, p: Paint) {
        val u = width / 100f
        val base = hh * 0.8f
        var x = rnd.nextFloat() * u * 6f
        while (x < width + u * 4f) {
            val r = u * (4f + rnd.nextFloat() * 3f) * (0.8f + 0.25f * layer)
            val trunk = r * 1.1f
            c.drawRect(x - r * 0.12f, base - trunk, x + r * 0.12f, hh, p)
            c.drawCircle(x, base - trunk - r * 0.4f, r, p)
            c.drawCircle(x - r * 0.7f, base - trunk + r * 0.1f, r * 0.7f, p)
            c.drawCircle(x + r * 0.7f, base - trunk + r * 0.1f, r * 0.75f, p)
            x += r * (2.6f + rnd.nextFloat() * 2f)
        }
    }

    /** A faint light band above the far dunes. */
    private fun haze(c: Canvas, width: Float, hh: Float, theme: Theme) {
        val light = ColorMath.mix(theme.skyBottom, SOFT_WHITE, 0.4f)
        val p = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, hh * 0.75f,
                intArrayOf(ColorMath.withAlpha(light, 0), ColorMath.withAlpha(light, 80), ColorMath.withAlpha(light, 0)),
                floatArrayOf(0f, 0.75f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, width, hh * 0.75f, p)
    }

    /** Large faint sun disc, no stripes. */
    private fun buildSun(theme: Theme, fw: Float): Bitmap {
        val r = fw * 0.24f
        val size = (r * 2f).toInt().coerceAtLeast(2)
        val c = size / 2f
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                c, c, c,
                intArrayOf(ColorMath.withAlpha(theme.accent, 70), ColorMath.withAlpha(theme.accent, 45), ColorMath.withAlpha(theme.accent, 0)),
                floatArrayOf(0f, 0.7f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        Canvas(bmp).drawCircle(c, c, c, p)
        return bmp
    }

    private fun buildRay(theme: Theme, fw: Float, fh: Float) {
        val len = fh * 0.7f
        ray.reset()
        ray.moveTo(-fw * 0.03f, 0f)
        ray.lineTo(fw * 0.03f, 0f)
        ray.lineTo(fw * 0.12f, len)
        ray.lineTo(-fw * 0.12f, len)
        ray.close()
        val light = ColorMath.mix(theme.accent, SOFT_WHITE, 0.5f)
        rayPaint.shader = LinearGradient(
            0f, 0f, 0f, len,
            ColorMath.withAlpha(light, 20), ColorMath.withAlpha(light, 0),
            Shader.TileMode.CLAMP,
        )
    }

    private companion object {
        const val LAYERS = 3
        const val SAMPLES = 24
        const val SEED = 41
        const val TAU = 6.2831855f
        const val RAY_HZ = 0.1f
        /** Matches how Backdrop's horizon follows the camera, so the rise can be recovered from it. */
        const val BASE_HORIZON = 0.73f
        const val HORIZON_RATE = 0.45f
        const val SOFT_WHITE = 0xFFF8F4EE.toInt()
        val LAYER_HEIGHT = floatArrayOf(0.30f, 0.24f, 0.18f)
        val LAYER_BOTTOM = floatArrayOf(0.80f, 0.83f, 0.87f)
        val LAYER_DEPTH = floatArrayOf(0.35f, 0.62f, 0.88f)
        val PARALLAX = floatArrayOf(0.08f, 0.18f, 0.32f)
        val RAY_X = floatArrayOf(0.18f, 0.42f, 0.63f, 0.85f)
        val RAY_ANGLE = floatArrayOf(-12f, -4f, 5f, 13f)
    }
}
