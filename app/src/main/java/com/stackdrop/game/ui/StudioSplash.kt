package com.stackdrop.game.ui

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Build
import kotlin.math.min
import kotlin.math.pow

/**
 * The GrabStacks studio splash that opens the game (Claude Design "GrabStacks Splash", concept D):
 * blocks drop into a G and a claw sets its last piece, the studio name settles underneath, then the
 * studio slides out left while the Stack Drop logo and title slide in. It is drawn over the menu and
 * fades into it. Times are seconds from launch; positions are in the design's 1080×1920 stage,
 * scaled to fit the screen, and the logo art uses the 108-unit icon grid.
 */
class StudioSplash(assets: AssetManager) {

    var active = true
        private set

    private var t = 0f

    private val font = loadFont(assets)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font }
    private val metrics = Paint.FontMetrics()
    private val path = Path()

    // Results of landing(); fields rather than a returned object so drawing allocates nothing.
    private var landY = 0f
    private var landSquash = 1f

    fun update(dt: Float) {
        if (!active) return
        t += dt
        if (t >= END + FADE) active = false
    }

    /** A tap or Back jumps to the finished title, which then fades into the menu. */
    fun skip() {
        if (t < END) t = END
    }

    /** Ends at once, for Reduce motion. */
    fun finish() {
        active = false
    }

    fun render(canvas: Canvas, width: Float, height: Float) {
        if (!active || width <= 0f) return
        val alpha = if (t > END) 1f - (t - END) / FADE else 1f
        val saved = if (alpha < 1f) {
            canvas.saveLayerAlpha(0f, 0f, width, height, (255 * alpha.coerceIn(0f, 1f)).toInt())
        } else {
            canvas.save()
        }
        canvas.drawColor(NAVY)
        val s = min(width / STAGE_W, height / STAGE_H)
        canvas.translate((width - STAGE_W * s) / 2f, (height - STAGE_H * s) / 2f)
        canvas.scale(s, s)
        drawStudio(canvas)
        drawTitle(canvas)
        canvas.restoreToCount(saved)
    }

    // ── Studio: the G mark and the GRABSTACKS STUDIO wordmark ──────────────────────────────────

    private fun drawStudio(canvas: Canvas) {
        canvas.save()
        canvas.translate(drop(0f, -STAGE_W * 1.1f, HANDOFF, HANDOFF + 0.4f), 0f)

        canvas.save()
        val breathe = enter(0.97f, 1.02f, BUILD, HANDOFF)
        canvas.scale(breathe, breathe, 540f, 920f)
        canvas.translate(-160f, 80f)
        canvas.scale(1400f / 108f, 1400f / 108f)
        canvas.translate(54f, 52f)
        canvas.scale(0.78f, 0.78f)
        canvas.translate(-55.25f, -55.25f)
        drawG(canvas)
        canvas.restore()

        drawWordmark(canvas)
        canvas.restore()
    }

    private fun drawG(canvas: Canvas) {
        var clawY = 0f
        var clawOpen = 0f
        for (i in 0 until G_PIECES) {
            if (i == TAB) {
                // The claw lowers the last piece into the G's notch and lets go.
                val down = enter(-120f, -8f, BUILD + 0.2f, BUILD + 0.56f)
                val release = BUILD + 0.58f
                clawOpen = enter(0f, 1f, release, release + 0.1f)
                val up = enter(0f, -130f, release + 0.12f, release + 0.5f)
                val held = t < release
                landing(release, 0.1f, 8f)
                drawGBlock(canvas, i, if (held) down else landY, if (held) 1f else landSquash)
                clawY = (if (held) down else -8f + up) + GY + G_Y[i] - 2f
            } else {
                landing(BUILD + G_DROP[i] * 0.1f, 0.24f, 110f)
                drawGBlock(canvas, i, landY, landSquash)
            }
        }
        val p = TAB
        drawClaw(canvas, GX + G_X[p] + G_W[p] / 2f, G_W[p] / 2f + 1.5f, clawY, clawOpen, 12f)
    }

    /** A flat block of the G with a thin iso-style shade under and to the right of it. */
    private fun drawGBlock(canvas: Canvas, i: Int, dy: Float, squash: Float) {
        val x = GX + G_X[i]
        val y = GY + G_Y[i]
        val w = G_W[i]
        val h = G_H[i]
        val d = 2.5f
        val c = G_COLOR[i]
        canvas.save()
        canvas.translate(0f, dy)
        canvas.scale(1f, squash, x, y + h)
        quad(canvas, SLAB_LEFT[c], x, y + h, x + d, y + h + d, x + w + d, y + h + d, x + w, y + h)
        quad(canvas, SLAB_RIGHT[c], x + w, y, x + w + d, y + d, x + w + d, y + h + d, x + w, y + h)
        fill.color = SLAB_TOP[c]
        canvas.drawRect(x, y, x + w, y + h, fill)
        canvas.restore()
    }

    /** Cable, hub, crossbar and two prongs; [y] is the crossbar's top, [open] 0..1. */
    private fun drawClaw(canvas: Canvas, cx: Float, half: Float, y: Float, open: Float, len: Float) {
        val angle = 8f + open * 26f
        fill.color = DIM
        canvas.save()
        canvas.translate(0f, y)
        canvas.drawRect(cx - 0.6f, -300f, cx + 0.6f, 0f, fill)
        canvas.drawRoundRect(cx - 4f, -3f, cx + 4f, 0.4f, 1f, 1f, fill)
        canvas.drawRoundRect(cx - half - 1f, 0f, cx + half + 1f, 2f, 1f, 1f, fill)
        canvas.save()
        canvas.rotate(angle, cx - half, 1f)
        canvas.drawRoundRect(cx - half - 0.9f, 0f, cx - half + 0.9f, len, 0.9f, 0.9f, fill)
        canvas.restore()
        canvas.save()
        canvas.rotate(-angle, cx + half, 1f)
        canvas.drawRoundRect(cx + half - 0.9f, 0f, cx + half + 0.9f, len, 0.9f, 0.9f, fill)
        canvas.restore()
        canvas.restore()
    }

    private fun drawWordmark(canvas: Canvas) {
        val start = WORDMARK - 0.15f
        val nameAlpha = enter(0f, 1f, start, start + 0.3f)
        val rise = enter(30f, 0f, start, start + 0.35f)
        val track = enter(0.3f, 0.08f, start, start + 0.5f)
        val studioAlpha = enter(0f, 1f, start + 0.15f, start + 0.45f)
        val cx = STAGE_W / 2f

        // "Grab" in cream and "Stacks" in pink, centred together as one word.
        text.textAlign = Paint.Align.LEFT
        text.textSize = 120f
        text.letterSpacing = track * 0.5f
        val baseline = baseline(1210f + rise, 120f)
        val grab = text.measureText(GRAB)
        val left = cx - (grab + text.measureText(STACKS)) / 2f
        text.color = withAlpha(CREAM, nameAlpha)
        canvas.drawText(GRAB, left, baseline, text)
        text.color = withAlpha(SLAB_TOP[PINK], nameAlpha)
        canvas.drawText(STACKS, left + grab, baseline, text)

        text.textAlign = Paint.Align.CENTER
        text.textSize = 34f
        text.letterSpacing = 0.5f
        text.color = withAlpha(MUTED, studioAlpha)
        canvas.drawText(STUDIO, cx, baseline(1352f + rise, 34f), text)
    }

    // ── Hand-off: the Stack Drop logo and title ────────────────────────────────────────────────

    private fun drawTitle(canvas: Canvas) {
        if (t < HANDOFF) return
        drawGameLogo(canvas)

        val size = U * 14f
        text.textAlign = Paint.Align.CENTER
        text.textSize = size
        text.letterSpacing = 0.02f
        text.color = CREAM
        text.setShadowLayer(size * 0.1f, 0f, size * 0.02f, TITLE_GLOW)
        val slide = enter(STAGE_W * 1.1f, 0f, HANDOFF + 0.08f, HANDOFF + 0.48f)
        canvas.drawText(TITLE, STAGE_W / 2f + slide, baseline(TITLE_BASE - size * 0.74f, size), text)
        text.clearShadowLayer()

        text.textSize = U * 3.6f
        text.letterSpacing = 0.3f
        text.color = withAlpha(MUTED, enter(0f, 1f, HANDOFF + 0.5f, HANDOFF + 0.8f))
        canvas.drawText(TAGLINE, STAGE_W / 2f, baseline(TITLE_BASE + U * 5f, U * 3.6f), text)
    }

    /** The app logo: a staggered tower whose top slab lands and chips off its overhang. */
    private fun drawGameLogo(canvas: Canvas) {
        val slide = enter(STAGE_W * 1.1f, 0f, HANDOFF, HANDOFF + 0.4f)
        landing(HANDOFF + 0.4f, 0.18f, 30f)
        val topY = landY
        val cut = HANDOFF + 0.58f
        val chipX = enter(-9f, 0f, cut, cut + 0.35f)
        val chipY = enter(3.5f, 0f, cut, cut + 0.35f) + topY
        val chipTurn = enter(0f, 24f, cut, cut + 0.35f)
        val settle = pop(0.96f, 1f, cut, cut + 0.3f)

        canvas.save()
        canvas.translate(slide, 0f)
        canvas.scale(settle, settle, 540f, 716f)
        canvas.translate(160f, 260f)
        canvas.scale(760f / 108f, 760f / 108f)
        for (i in TOWER_X.indices) isoSlab(canvas, TOWER_COLOR[i], TOWER_X[i], TOWER_Y[i], 17f, 0f, 0f, 0f)
        isoSlab(canvas, PINK, 53f, 34f, 17f, 0f, topY, 0f)
        isoSlab(canvas, PINK, 80f, 36f, 5f, chipX, chipY, chipTurn)
        canvas.restore()
    }

    private fun isoSlab(canvas: Canvas, c: Int, cx: Float, y0: Float, hw: Float, x: Float, y: Float, turn: Float) {
        val hh = hw * 0.575f
        val th = 5f
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(turn, cx, y0 + hh)
        quad(canvas, SLAB_TOP[c], cx, y0, cx + hw, y0 + hh, cx, y0 + 2 * hh, cx - hw, y0 + hh)
        quad(canvas, SLAB_RIGHT[c], cx + hw, y0 + hh, cx, y0 + 2 * hh, cx, y0 + 2 * hh + th, cx + hw, y0 + hh + th)
        quad(canvas, SLAB_LEFT[c], cx - hw, y0 + hh, cx, y0 + 2 * hh, cx, y0 + 2 * hh + th, cx - hw, y0 + hh + th)
        canvas.restore()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────────

    private fun quad(
        canvas: Canvas, color: Int,
        x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float,
    ) {
        path.reset()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.lineTo(x4, y4)
        path.close()
        fill.color = color
        canvas.drawPath(path, fill)
    }

    /** Baseline of a single line of [size] text whose line box (line-height 1) starts at [top]. */
    private fun baseline(top: Float, size: Float): Float {
        text.textSize = size
        text.getFontMetrics(metrics)
        return top + (size - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent
    }

    /** Falls [height] units with ease-in, then squashes and springs back. Sets landY and landSquash. */
    private fun landing(start: Float, duration: Float, height: Float) {
        landY = drop(-height, 0f, start, start + duration)
        landSquash = if (t < start + duration) 1f else pop(0.82f, 1f, start + duration, start + duration + 0.16f)
    }

    private fun progress(start: Float, end: Float): Float = ((t - start) / (end - start)).coerceIn(0f, 1f)

    /** easeOutCubic. */
    private fun enter(from: Float, to: Float, start: Float, end: Float): Float {
        val p = progress(start, end) - 1f
        return from + (to - from) * (p * p * p + 1f)
    }

    /** easeInQuad. */
    private fun drop(from: Float, to: Float, start: Float, end: Float): Float {
        val p = progress(start, end)
        return from + (to - from) * p * p
    }

    /** easeOutBack. */
    private fun pop(from: Float, to: Float, start: Float, end: Float): Float {
        val p = progress(start, end) - 1f
        return from + (to - from) * (1f + BACK_C3 * p.pow(3) + BACK_C1 * p.pow(2))
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        ((255 * alpha.coerceIn(0f, 1f)).toInt() shl 24) or (color and 0xFFFFFF)

    private fun loadFont(assets: AssetManager): Typeface = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Typeface.Builder(assets, FONT_FILE).setFontVariationSettings("'wght' 500").build()
        } else {
            Typeface.createFromAsset(assets, FONT_FILE)
        }
    } catch (e: RuntimeException) {
        // A missing or unreadable font file must not stop the game from starting.
        android.util.Log.w("StudioSplash", "Using the default font: $FONT_FILE could not be loaded", e)
        Typeface.DEFAULT_BOLD
    }

    private companion object {
        const val STAGE_W = 1080f
        const val STAGE_H = 1920f
        const val U = STAGE_W / 100f

        // Scene starts from the design's OM_SCENES (Build 0.75s, Wordmark 0.75s, Handoff 1.1s).
        const val BUILD = 0f
        const val WORDMARK = 0.75f
        const val HANDOFF = 1.5f
        const val END = 2.6f
        const val FADE = 0.4f

        const val BACK_C1 = 1.70158f
        const val BACK_C3 = BACK_C1 + 1f

        const val FONT_FILE = "fonts/Fredoka.ttf"
        const val GRAB = "Grab"
        const val STACKS = "Stacks"
        const val STUDIO = "STUDIO"
        const val TITLE = "Stack Drop"
        const val TAGLINE = "ARCADE TOWERS"
        const val TITLE_BASE = 1150f

        const val NAVY = 0xFF1E2140.toInt()
        const val DIM = 0xFFB8B4CC.toInt()
        const val CREAM = 0xFFF6EFE8.toInt()
        const val MUTED = 0xFFC9BFC8.toInt()
        const val TITLE_GLOW = 0x38F7A0CF

        // Slab colours from the launcher icon: cyan (bottom), violet (middle), pink (top).
        const val CYAN = 0
        const val VIOLET = 1
        const val PINK = 2
        val SLAB_TOP = intArrayOf(0xFF7FD3F5.toInt(), 0xFFA99BF7.toInt(), 0xFFF7A0CF.toInt())
        val SLAB_RIGHT = intArrayOf(0xFF4FA8CC.toInt(), 0xFF7C6CD6.toInt(), 0xFFD46BA6.toInt())
        val SLAB_LEFT = intArrayOf(0xFF3B7F9C.toInt(), 0xFF5E51A3.toInt(), 0xFFA1507E.toInt())

        // The G: top bar, left column, bottom bar, right stub, and the tab the claw places.
        const val GX = 30f
        const val GY = 26f
        const val G_PIECES = 5
        const val TAB = 4
        val G_X = floatArrayOf(0f, 0f, 0f, 34f, 22f)
        val G_Y = floatArrayOf(0f, 14f, 42f, 28f, 28f)
        val G_W = floatArrayOf(48f, 14f, 48f, 14f, 12f)
        val G_H = floatArrayOf(14f, 28f, 14f, 14f, 9f)
        val G_COLOR = intArrayOf(PINK, VIOLET, CYAN, VIOLET, PINK)
        /** Landing order: bottom bar first, then the column, the stub and the top bar. */
        val G_DROP = intArrayOf(3, 1, 0, 2, 0)

        // The game logo's tower, bottom slab first.
        val TOWER_X = floatArrayOf(52f, 55f, 52f, 55f)
        val TOWER_Y = floatArrayOf(62f, 57f, 52f, 47f)
        val TOWER_COLOR = intArrayOf(CYAN, VIOLET, CYAN, VIOLET)
    }
}
