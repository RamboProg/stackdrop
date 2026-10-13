package com.stackdrop.game.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import kotlin.math.min

/**
 * Short, soft haptics. Uses crisp composed primitives where the device supports them
 * (Android 11+), otherwise brief low-amplitude pulses.
 */
class Haptics(context: Context) {

    @Volatile
    var enabled = true

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private val primitives: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator?.areAllPrimitivesSupported(
            CLICK, TICK, QUICK_FALL,
        ) == true

    /** Menu buttons. */
    fun ui() = play(intArrayOf(TICK), floatArrayOf(0.35f), intArrayOf(0), 8, 45)

    /** Any block that lands without being perfect. */
    fun land() = play(intArrayOf(CLICK), floatArrayOf(0.45f), intArrayOf(0), 12, 70)

    /** Perfect drop; gets a little crisper as the combo grows. */
    fun perfect(combo: Int) {
        val c = min(combo, 6)
        val strength = 0.6f + c * 0.06f
        if (combo >= 3) {
            play(intArrayOf(CLICK, TICK), floatArrayOf(strength, 0.4f), intArrayOf(0, 45), 16, 105 + c * 15)
        } else {
            play(intArrayOf(CLICK), floatArrayOf(strength), intArrayOf(0), 16, 105 + c * 15)
        }
    }

    /** A miss or a module slipping off: a gentle falling cue, never a long buzz. */
    fun soft() = play(intArrayOf(QUICK_FALL), floatArrayOf(0.5f), intArrayOf(0), 30, 80)

    /** Tower is close to tipping. */
    fun warn() = play(intArrayOf(TICK, TICK), floatArrayOf(0.45f, 0.45f), intArrayOf(0, 70), 10, 60)

    /** New best, unlocks and gifts: three rising taps. */
    fun celebrate() {
        if (!ready()) return
        val v = vibrator ?: return
        when {
            primitives && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                v.vibrate(composition(intArrayOf(CLICK, CLICK, CLICK), floatArrayOf(0.4f, 0.6f, 0.85f), intArrayOf(0, 70, 70)))
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                v.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 14, 70, 14, 70, 18),
                        intArrayOf(0, 80, 0, 120, 0, 160),
                        -1,
                    )
                )
            else -> {
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0, 14, 70, 14, 70, 18), -1)
            }
        }
    }

    private fun ready() = enabled && vibrator?.hasVibrator() == true

    private fun play(ids: IntArray, scales: FloatArray, delays: IntArray, ms: Long, amplitude: Int) {
        if (!ready()) return
        val v = vibrator ?: return
        when {
            primitives && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                v.vibrate(composition(ids, scales, delays))
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                v.vibrate(VibrationEffect.createOneShot(ms, amplitude))
            else -> {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun composition(ids: IntArray, scales: FloatArray, delays: IntArray): VibrationEffect {
        val c = VibrationEffect.startComposition()
        for (i in ids.indices) c.addPrimitive(ids[i], scales[i], delays[i])
        return c.compose()
    }

    private companion object {
        const val CLICK = VibrationEffect.Composition.PRIMITIVE_CLICK
        const val TICK = VibrationEffect.Composition.PRIMITIVE_TICK
        const val QUICK_FALL = VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
    }
}
