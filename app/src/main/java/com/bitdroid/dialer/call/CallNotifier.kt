package com.bitdroid.dialer.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.bitdroid.dialer.R
import com.bitdroid.dialer.data.Phone

object CallNotifier {
    private const val CH_INCOMING = "incoming_calls"
    private const val CH_ONGOING = "ongoing_calls"
    private const val ID = 4201
    const val ACTION_ANSWER = "com.bitdroid.dialer.ANSWER"
    const val ACTION_DECLINE = "com.bitdroid.dialer.DECLINE"
    const val ACTION_HANGUP = "com.bitdroid.dialer.HANGUP"
    private const val ACCENT = 0xFFA8C7FA.toInt()

    fun setupChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                // Telecom plays the ringtone itself.
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ONGOING, "Ongoing calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    fun canPost(context: Context) = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun screenIntent(context: Context, action: String? = null): PendingIntent =
        PendingIntent.getActivity(
            context, if (action == null) 0 else 1,
            Intent(context, InCallActivity::class.java).apply {
                this.action = action
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun broadcast(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, CallActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    const val NOTIFICATION_ID = ID
    private const val TEST_ID = 4299

    fun fullScreenAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 34 ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    /** Posts a fake incoming call through the same full-screen notification path as a real one. */
    fun postTest(context: Context) {
        val open = PendingIntent.getActivity(
            context, 9,
            Intent(context, InCallActivity::class.java).apply {
                putExtra(InCallActivity.EXTRA_TEST, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val person = Person.Builder().setName("Test call").setImportant(true).build()
        val n = NotificationCompat.Builder(context, CH_INCOMING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setColor(ACCENT)
            .setContentTitle("Test call")
            .setContentText("Incoming call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(30_000)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, open, open))
            .build()
        com.bitdroid.dialer.call.Diag.log(context, "test call posted (notifications: ${canPost(context)}, full-screen allowed: ${fullScreenAllowed(context)})")
        post(context, n, TEST_ID)
        // Vibrate like a real call so vibration can be checked too; stop after 30 s if untouched.
        CallVibrator.start(context)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ CallVibrator.stop(context) }, 30_000)
    }

    fun cancelTest(context: Context) {
        CallVibrator.stop(context)
        NotificationManagerCompat.from(context).cancel(TEST_ID)
    }

    /** Builds the call notification for the current calls, or null when there's nothing live. */
    fun build(context: Context, calls: List<UiCall>): Notification? {
        val p = CallManager.primary(calls) ?: return null
        if (calls.all { it.ended }) return null
        val who = p.contact.name ?: Phone.format(context, p.number).ifBlank { "Unknown" }
        val person = Person.Builder().setName(who).setImportant(true).build()
        return if (p.ringing) {
            NotificationCompat.Builder(context, CH_INCOMING)
                .setSmallIcon(R.drawable.ic_stat_call)
                .setColor(ACCENT)
                .setContentTitle(who)
                .setContentText("Incoming call")
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setFullScreenIntent(screenIntent(context), true)
                .setContentIntent(screenIntent(context))
                .setStyle(
                    NotificationCompat.CallStyle.forIncomingCall(
                        person,
                        broadcast(context, ACTION_DECLINE, 2),
                        screenIntent(context, ACTION_ANSWER),
                    ),
                )
                .build()
        } else {
            NotificationCompat.Builder(context, CH_ONGOING)
                .setSmallIcon(R.drawable.ic_stat_call)
                .setColor(ACCENT)
                .setContentTitle(who)
                .setContentText(
                    when {
                        p.dialing -> "Calling…"
                        p.holding -> "On hold"
                        else -> "On call"
                    },
                )
                .setUsesChronometer(p.active && p.connectTime > 0)
                .setWhen(if (p.connectTime > 0) p.connectTime else System.currentTimeMillis())
                .setShowWhen(p.active)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(screenIntent(context))
                // CallStyle gives the green call chip in the status bar; valid because CallService
                // runs as a phoneCall foreground service while a call is live.
                .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, broadcast(context, ACTION_HANGUP, 3)))
                .build()
        }
    }

    /** Plain fallback (no CallStyle) for when the foreground service can't start. */
    fun buildPlain(context: Context, calls: List<UiCall>): Notification? {
        val p = CallManager.primary(calls) ?: return null
        if (p.ringing) return build(context, calls)
        val who = p.contact.name ?: Phone.format(context, p.number).ifBlank { "Unknown" }
        return NotificationCompat.Builder(context, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setColor(ACCENT)
            .setContentTitle(who)
            .setContentText(if (p.dialing) "Calling…" else if (p.holding) "On hold" else "On call")
            .setUsesChronometer(p.active && p.connectTime > 0)
            .setWhen(if (p.connectTime > 0) p.connectTime else System.currentTimeMillis())
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(screenIntent(context))
            .addAction(R.drawable.ic_stat_call, "Hang up", broadcast(context, ACTION_HANGUP, 3))
            .build()
    }

    fun post(context: Context, n: Notification, id: Int = ID) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: Exception) {
            android.util.Log.e("bitDialer", "notify failed", e)
        }
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID)
    }
}

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val p = CallManager.primary() ?: return
        when (intent.action) {
            CallNotifier.ACTION_DECLINE -> CallManager.decline(p)
            CallNotifier.ACTION_HANGUP -> CallManager.hangup(p)
        }
    }
}
