package com.stackdrop.game.game

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import com.stackdrop.game.game.scenery.AmbientLayer
import com.stackdrop.game.game.scenery.CalmScenery
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Scenery shared by both modes. Neon themes get the synthwave sky, stars, striped sun, scrolling grid
 * and CRT overlay; calm themes get a soft landscape with slow ambient motion.
 */
class Backdrop {

    /** Freezes ambient motion and halves it, for the Reduce motion setting. */
    var reduceMotion = false

    private val calm = CalmScenery()
    private val ambient = AmbientLayer()

    private var w = 0f
    private var h = 0f

    private val skyPaint = Paint()
    private val floorPaint = Paint()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val scanPaint = Paint()
    private val vignettePaint = Paint()

    private var skyTheme: Theme? = null
    private var skyDrift = Float.NaN
    private var sun: Bitmap? = null
    private var sunTheme: Theme? = null
    private var sunRadius = 0f

    private val starX = FloatArray(STARS)
    private val starY = FloatArray(STARS)
    private val starSize = FloatArray(STARS)
    private val starPhase = FloatArray(STARS)

    fun resize(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        sunRadius = w * 0.27f

        val rnd = Random(7)
        for (i in 0 until STARS) {
            starX[i] = rnd.nextFloat() * w
            starY[i] = rnd.nextFloat()
            starSize[i] = w * (0.0015f + rnd.nextFloat() * 0.003f)
            starPhase[i] = rnd.nextFloat() * 6.28f
        }

        val scan = Bitmap.createBitmap(1, SCAN_PERIOD, Bitmap.Config.ARGB_8888)
        scan.setPixel(0, SCAN_PERIOD - 1, Color.argb(40, 0, 0, 0))
        scanPaint.shader = BitmapShader(scan, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

        vignettePaint.shader = RadialGradient(
            w / 2f, h * 0.45f, hypot(w, h) * 0.62f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(175, 0, 0, 0)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )

        skyTheme = null
        sunTheme = null
        calm.resize(width, height)
        ambient.resize(width, height)
    }

    /** [horizon] is the screen y of the grid horizon; it slides down as the camera climbs. */
    fun draw(canvas: Canvas, theme: Theme, drift: Float, horizon: Float, time: Float) {
        if (w == 0f) return
        val t = if (reduceMotion) 0f else time
        if (theme.scenery != Scenery.SYNTH_GRID) {
            val rise = calm.riseFor(horizon)
            calm.drawSky(canvas, theme, rise, t)
            ambient.drawBack(canvas, theme, rise, t, reduceMotion)
            calm.drawLayers(canvas, theme, rise)
            ambient.drawFront(canvas, theme, rise, t, reduceMotion)
            return
        }
        if (skyTheme !== theme || skyDrift.isNaN() || abs(skyDrift - drift) > 0.5f) {
            skyPaint.shader = LinearGradient(
                0f, 0f, 0f, h,
                Palette.bgTop(theme, drift), Palette.bgBottom(theme, drift),
                Shader.TileMode.CLAMP,
            )
            floorPaint.color = Palette.hsv(theme.bgHue + drift, theme.bgSat, 0.035f)
            skyTheme = theme
            skyDrift = drift
        }
        canvas.drawRect(0f, 0f, w, h, skyPaint)
        drawStars(canvas, horizon, t)
        drawSun(canvas, theme, horizon)
        if (horizon < h) drawGrid(canvas, theme, horizon, t)
    }

    fun drawOverlay(canvas: Canvas, theme: Theme) {
        if (w == 0f) return
        if (theme.scanlines) canvas.drawRect(0f, 0f, w, h, scanPaint)
        vignettePaint.alpha = (255 * theme.vignette).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, w, h, vignettePaint)
    }

    private fun drawStars(canvas: Canvas, horizon: Float, time: Float) {
        val band = h * 1.4f
        val shift = (horizon - h * 0.72f) * 0.15f
        paint.color = Color.WHITE
        for (i in 0 until STARS) {
            val y = ((starY[i] * band + shift) % band + band) % band - h * 0.2f
            if (y > horizon - h * 0.02f) continue
            val twinkle = 0.55f + 0.45f * sin(time * 1.7f + starPhase[i])
            paint.alpha = (200 * twinkle).toInt()
            canvas.drawCircle(starX[i], y, starSize[i], paint)
        }
    }

    private fun drawSun(canvas: Canvas, theme: Theme, horizon: Float) {
        val cy = horizon - sunRadius * 0.42f
        val bmp = sunBitmap(theme)
        if (cy - bmp.height / 2f > h) return
        canvas.save()
        canvas.clipRect(0f, 0f, w, horizon)
        canvas.drawBitmap(bmp, w / 2f - bmp.width / 2f, cy - bmp.height / 2f, bitmapPaint)
        canvas.restore()
    }

    private fun sunBitmap(theme: Theme): Bitmap {
        val cached = sun
        if (cached != null && sunTheme === theme) return cached

        val r = sunRadius
        val size = (r * 3.2f).toInt().coerceAtLeast(2)
        val c = size / 2f
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val a1 = Palette.accent(theme)
        val a2 = Palette.accent2(theme)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.shader = RadialGradient(
            c, c, c,
            intArrayOf(Palette.withAlpha(a1, 110), Palette.withAlpha(a1, 35), Color.TRANSPARENT),
            floatArrayOf(0.3f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        cv.drawCircle(c, c, c, p)
        p.shader = LinearGradient(0f, c - r, 0f, c + r, a2, a1, Shader.TileMode.CLAMP)
        cv.drawCircle(c, c, r, p)

        p.shader = null
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        cv.save()
        cv.clipPath(Path().apply { addCircle(c, c, r, Path.Direction.CW) })
        for (k in 0 until 7) {
            val y = c + r * (0.05f + k * 0.14f)
            val thickness = r * (0.015f + k * 0.016f)
            cv.drawRect(c - r, y, c + r, y + thickness, p)
        }
        cv.restore()

        sun = bmp
        sunTheme = theme
        return bmp
    }

    private fun drawGrid(canvas: Canvas, theme: Theme, horizon: Float, time: Float) {
        val accent = Palette.accent(theme)
        canvas.drawRect(0f, horizon, w, h, floorPaint)

        val depth = h - horizon
        val spread = depth / (h * 0.28f)
        val cx = w / 2f
        line.color = accent
        line.strokeWidth = w * 0.003f
        line.alpha = 70
        for (i in -12..12) {
            val top = cx + i * w * 0.018f
            canvas.drawLine(top, horizon, top + i * w * 0.11f * spread, h, line)
        }

        val phase = (time * 0.45f) % 1f
        for (k in 0 until 10) {
            val t = (k + phase) / 10f
            val y = horizon + depth * t * t
            val a = 30 + 170 * t
            val width = w * 0.0035f * (0.5f + t)
            line.strokeWidth = width * 3f
            line.alpha = (a * 0.25f).toInt()
            canvas.drawLine(0f, y, w, y, line)
            line.strokeWidth = width
            line.alpha = a.toInt()
            canvas.drawLine(0f, y, w, y, line)
        }

        line.strokeWidth = w * 0.012f
        line.alpha = 70
        canvas.drawLine(0f, horizon, w, horizon, line)
        line.color = Palette.lighten(accent, 0.6f)
        line.strokeWidth = w * 0.003f
        line.alpha = 230
        canvas.drawLine(0f, horizon, w, horizon, line)
    }

    private companion object {
        const val STARS = 90
        const val SCAN_PERIOD = 5
    }
}
