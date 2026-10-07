package com.maxijones.tracerf

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class FeedbackEngine(context: Context) {
    private val appContext = context.applicationContext
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 35)
    private var lastAt = 0L

    fun pulse(rssi: Double, vibration: Boolean, sound: Boolean) {
        if (!vibration && !sound) return
        val now = System.currentTimeMillis()
        val interval = when {
            rssi >= -50 -> 280L
            rssi >= -60 -> 600L
            rssi >= -70 -> 1_100L
            rssi >= -80 -> 1_800L
            else -> 2_800L
        }
        if (now - lastAt < interval) return
        lastAt = now
        if (vibration) {
            runCatching {
                vibrator?.vibrate(VibrationEffect.createOneShot(if (rssi >= -60) 55L else 35L, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
        if (sound) runCatching { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 70) }
    }

    fun release() = tone.release()
}
