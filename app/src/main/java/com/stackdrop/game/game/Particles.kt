package com.stackdrop.game.game

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

class Particles {

    private class Spark(
        var x: Float, var y: Float, var z: Float,
        val vx: Float, var vy: Float, val vz: Float,
        var life: Float, val maxLife: Float, val size: Float, val color: Int,
        val gravity: Float,
    )

    private class Ripple(
        val x: Float, val z: Float, val w: Float, val d: Float, val y: Float,
        var age: Float, val color: Int,
    )

    /** Screen-space spark, used by the side-view tower mode. [gravity] is in screen widths per s². */
    private class Flare(
        var x: Float, var y: Float, val vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val size: Float, val color: Int,
        val gravity: Float,
    )

    /** Screen-space expanding ring, the calm perfect-hit cue in the tower mode. */
    private class Ring(val x: Float, val y: Float, val radius: Float, var age: Float, val color: Int)

    private class Confetti(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var rotation: Float, val spin: Float, var life: Float,
        val size: Float, val color: Int,
    )

    /** Softer halos and rings for calm themes. */
    var calm = false

    private val sparks = ArrayList<Spark>()
    private val ripples = ArrayList<Ripple>()
    private val flares = ArrayList<Flare>()
    private val rings = ArrayList<Ring>()
    private val confetti = ArrayList<Confetti>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    fun ripple(b: Block, rings: Int, color: Int) {
        for (i in 0 until rings) ripples.add(Ripple(b.x, b.z, b.w, b.d, b.y + b.h, -i * RING_DELAY, color))
    }

    fun burst(b: Block, count: Int, color: Int) {
        val cx = b.x + b.w / 2f
        val cz = b.z + b.d / 2f
        repeat(count) {
            val t = Random.nextFloat()
            val px: Float
            val pz: Float
            when (Random.nextInt(4)) {
                0 -> { px = b.x + t * b.w; pz = b.z }
                1 -> { px = b.x + b.w; pz = b.z + t * b.d }
                2 -> { px = b.x + t * b.w; pz = b.z + b.d }
                else -> { px = b.x; pz = b.z + t * b.d }
            }
            val dx = px - cx
            val dz = pz - cz
            val len = max(0.01f, hypot(dx, dz))
            val speed = 3f + Random.nextFloat() * 5f
            val life = 0.5f + Random.nextFloat() * 0.4f
            sparks.add(
                Spark(
                    px, b.y + b.h, pz,
                    dx / len * speed, 4f + Random.nextFloat() * 6f, dz / len * speed,
                    life, life, 0.1f + Random.nextFloat() * 0.15f, color, SPARK_GRAVITY,
                )
            )
        }
    }

    /** A few slow motes drifting up from the top of [b]. */
    fun motes(b: Block, count: Int, color: Int) {
        repeat(count) {
            val life = 1.1f + Random.nextFloat() * 0.6f
            sparks.add(
                Spark(
                    b.x + Random.nextFloat() * b.w, b.y + b.h, b.z + Random.nextFloat() * b.d,
                    (Random.nextFloat() - 0.5f) * 1.2f, 1.2f + Random.nextFloat(), (Random.nextFloat() - 0.5f) * 1.2f,
                    life, life, 0.08f + Random.nextFloat() * 0.06f, color, MOTE_GRAVITY,
                )
            )
        }
    }

    /** Upward fan of glowing sparks at a screen position. [speed] is in px/s. */
    fun flares(x: Float, y: Float, color: Int, count: Int, speed: Float) {
        repeat(count) {
            val a = PI + Random.nextDouble() * PI
            val s = speed * (0.35f + Random.nextFloat() * 0.65f)
            val life = 0.4f + Random.nextFloat() * 0.45f
            flares.add(
                Flare(
                    x, y, (cos(a) * s).toFloat(), (sin(a) * s).toFloat(),
                    life, life, speed * (0.006f + Random.nextFloat() * 0.008f), color, FLARE_GRAVITY,
                )
            )
        }
    }

    /** Slow screen-space motes that float up and fade. [spread] is in px. */
    fun screenMotes(x: Float, y: Float, color: Int, count: Int, spread: Float) {
        repeat(count) {
            val life = 1f + Random.nextFloat() * 0.5f
            flares.add(
                Flare(
                    x + (Random.nextFloat() - 0.5f) * spread, y,
                    (Random.nextFloat() - 0.5f) * spread * 0.3f, -spread * (0.3f + Random.nextFloat() * 0.3f),
                    life, life, spread * (0.03f + Random.nextFloat() * 0.02f), color, MOTE_FLOAT,
                )
            )
        }
    }

    fun ring(x: Float, y: Float, color: Int, radius: Float) {
        rings.add(Ring(x, y, radius, 0f, color))
    }

    /** Confetti in [colors] if given, otherwise in random bright hues. */
    fun confetti(screenWidth: Float, count: Int, colors: IntArray? = null) {
        repeat(count) {
            confetti.add(
                Confetti(
                    x = Random.nextFloat() * screenWidth,
                    y = -Random.nextFloat() * screenWidth * 0.4f,
                    vx = (Random.nextFloat() - 0.5f) * screenWidth * 0.3f,
                    vy = Random.nextFloat() * screenWidth * 0.2f,
                    rotation = Random.nextFloat() * 360f,
                    spin = (Random.nextFloat() - 0.5f) * 720f,
                    life = CONFETTI_LIFE,
                    size = screenWidth * (0.012f + Random.nextFloat() * 0.012f),
                    color = if (colors != null) colors[Random.nextInt(colors.size)]
                    else Palette.hsv(Random.nextFloat() * 360f, 0.7f, 1f),
                )
            )
        }
    }

    fun clear() {
        sparks.clear()
        ripples.clear()
        flares.clear()
        rings.clear()
        confetti.clear()
    }

    fun update(dt: Float, screenWidth: Float) {
        val s = sparks.iterator()
        while (s.hasNext()) {
            val p = s.next()
            p.life -= dt
            if (p.life <= 0f) { s.remove(); continue }
            p.vy -= p.gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt
        }

        val r = ripples.iterator()
        while (r.hasNext()) {
            val p = r.next()
            p.age += dt
            if (p.age > RIPPLE_TIME) r.remove()
        }

        val f = flares.iterator()
        while (f.hasNext()) {
            val p = f.next()
            p.life -= dt
            if (p.life <= 0f) { f.remove(); continue }
            p.vy += screenWidth * p.gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
        }

        val g = rings.iterator()
        while (g.hasNext()) {
            val p = g.next()
            p.age += dt
            if (p.age > RING_TIME) g.remove()
        }

        val gravity = screenWidth * 0.6f
        val c = confetti.iterator()
        while (c.hasNext()) {
            val p = c.next()
            p.life -= dt
            if (p.life <= 0f) { c.remove(); continue }
            p.vy += gravity * dt
            p.vx *= 1f - 1.2f * dt
            p.vy *= 1f - 1.2f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rotation += p.spin * dt
        }
    }

    fun drawWorld(canvas: Canvas, iso: IsoRenderer) {
        val ringAlpha = if (calm) 140f else 240f
        for (p in ripples) {
            if (p.age < 0f) continue
            val t = p.age / RIPPLE_TIME
            val e = 0.3f + t * 2.8f
            val alpha = ((1f - t) * ringAlpha).toInt()
            val width = iso.scale * 0.18f * (1f - t) + 1f
            iso.strokeRect(canvas, p.x - e, p.z - e, p.w + e * 2, p.d + e * 2, p.y, p.color, alpha, width)
        }

        for (p in sparks) {
            val a = (255 * p.life / p.maxLife).toInt().coerceIn(0, 255)
            glowDot(canvas, iso.sx(p.x, p.z), iso.sy(p.x, p.y, p.z), p.size * iso.scale, p.color, a)
        }
    }

    fun drawScreen(canvas: Canvas) {
        for (p in rings) {
            val t = p.age / RING_TIME
            ringPaint.color = p.color
            ringPaint.alpha = ((1f - t) * 120).toInt()
            ringPaint.strokeWidth = p.radius * 0.06f * (1f - t) + 1f
            canvas.drawCircle(p.x, p.y, p.radius * (0.2f + 0.8f * t), ringPaint)
        }
        for (p in flares) {
            val a = (255 * p.life / p.maxLife).toInt().coerceIn(0, 255)
            glowDot(canvas, p.x, p.y, p.size, p.color, a)
        }
        for (p in confetti) {
            paint.color = p.color
            paint.alpha = (255 * (p.life / 0.6f).coerceAtMost(1f)).toInt()
            canvas.save()
            canvas.rotate(p.rotation, p.x, p.y)
            canvas.drawRect(p.x - p.size / 2, p.y - p.size * 0.3f, p.x + p.size / 2, p.y + p.size * 0.3f, paint)
            canvas.restore()
        }
    }

    private fun glowDot(canvas: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Int) {
        paint.color = color
        paint.alpha = if (calm) alpha / 10 else alpha / 4
        canvas.drawCircle(x, y, r * 2.5f, paint)
        paint.color = if (calm) color else Palette.lighten(color, 0.6f)
        paint.alpha = alpha
        canvas.drawCircle(x, y, r, paint)
    }

    private companion object {
        const val RIPPLE_TIME = 0.6f
        const val RING_DELAY = 0.12f
        const val RING_TIME = 0.8f
        const val CONFETTI_LIFE = 3f
        const val SPARK_GRAVITY = 20f
        const val MOTE_GRAVITY = 0.4f
        const val FLARE_GRAVITY = 1.4f
        const val MOTE_FLOAT = 0.05f
    }
}
