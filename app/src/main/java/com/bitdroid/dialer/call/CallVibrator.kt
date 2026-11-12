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

    /**
     * Android only cancels a vibration through the same Vibrator that started it, so keep one
     * instance from the application context and use it for both start and stop.
     */
    private var shared: Vibrator? = null

    private fun vibrator(context: Context): Vibrator? {
        shared?.let { return it }
        return context.applicationContext.getSystemService(VibratorManager::class.java)?.defaultVibrator?.also { shared = it }
    }

    fun start(context: Context) {
        if (running) return
        if (!Prefs.vibrateCalls(context)) {
            Diag.log(context, "ring vibration skipped: turned off in Settings")
            return
        }
        val am = context.getSystemService(AudioManager::class.java)
        if (am?.ringerMode == AudioManager.RINGER_MODE_SILENT) {
            Diag.log(context, "ring vibration skipped: phone is on Silent")
            return
        }
        // Respect Do Not Disturb.
        val nm = context.getSystemService(NotificationManager::class.java)
        val filter = nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
        if (filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
            Diag.log(context, "ring vibration skipped: Do Not Disturb is on")
            return
        }
        val v = vibrator(context)
        if (v == null || !v.hasVibrator()) {
            Diag.log(context, "ring vibration skipped: no vibrator")
            return
        }
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

    /** Safe to call any time; always cancels, even if we lost track of the running state. */
    fun stop(context: Context) {
        vibrator(context)?.cancel()
        if (running) Diag.log(context, "ring vibration stopped")
        running = false
    }
}
