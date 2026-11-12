package com.bitdroid.dialer.call

import android.content.Context
import android.util.Log
import com.bitdroid.dialer.data.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small on-device event log for calls, shown in Settings → Call diagnostics so problems can be
 * seen without adb. Kept in device-protected storage so it works before first unlock.
 */
object Diag {
    private const val KEY = "diag_log"
    private const val MAX = 60
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun log(context: Context, event: String) {
        Log.i("bitDialer", event)
        try {
            val p = Prefs.get(context)
            val lines = (p.getString(KEY, "") ?: "").split('\n').filter { it.isNotBlank() }.takeLast(MAX - 1)
            p.edit().putString(KEY, (lines + "${fmt.format(Date())}  $event").joinToString("\n")).apply()
        } catch (_: Exception) {
        }
    }

    fun read(context: Context): List<String> =
        (Prefs.get(context).getString(KEY, "") ?: "").split('\n').filter { it.isNotBlank() }.reversed()

    fun clear(context: Context) = Prefs.get(context).edit().remove(KEY).apply()
}
