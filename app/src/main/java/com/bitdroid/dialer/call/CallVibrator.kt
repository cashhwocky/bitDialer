package com.bitdroid.dialer.call

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.bitdroid.dialer.data.Prefs

/**
 * Vibrates while a call is ringing. Android's own call ringer plays the ringtone, but doesn't
 * always vibrate for a non-Google dialer, so bitDialer does it itself.
 */
object CallVibrator {
    private var running = false

    private fun vibrator(context: Context): Vibrator? =
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator

    fun start(context: Context) {
        if (running) return
        if (!Prefs.vibrateCalls(context)) return
        val am = context.getSystemService(AudioManager::class.java)
        if (am?.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        // Respect Do Not Disturb.
        val nm = context.getSystemService(NotificationManager::class.java)
        val filter = nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
        if (filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) return
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        // Ring pattern: 1 s on, 1 s off, repeating from index 1.
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 1)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_RINGTONE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
            }
            running = true
            Diag.log(context, "ring vibration started")
        } catch (e: Exception) {
            Diag.log(context, "ring vibration failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun stop(context: Context) {
        if (!running) return
        vibrator(context)?.cancel()
        running = false
        Diag.log(context, "ring vibration stopped")
    }
}
