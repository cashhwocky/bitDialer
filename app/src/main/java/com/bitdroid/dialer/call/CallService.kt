package com.bitdroid.dialer.call

import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * Telecom binds this while bitDialer is the default phone app and hands it every call.
 * It keeps [CallManager] in sync and shows the call screen or the incoming-call notification.
 */
open class CallService : InCallService() {
    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = sync()
        override fun onDetailsChanged(call: Call, details: Call.Details) = sync()
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) = sync()
    }

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
        Diag.log(this, "call service connected")
    }

    override fun onDestroy() {
        if (CallManager.service === this) CallManager.service = null
        CallNotifier.cancelAll(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Diag.log(this, "call added (state ${call.details?.state}, ${if (call.details?.state == Call.STATE_RINGING) "incoming" else "outgoing"})")
        call.registerCallback(callback)
        sync()
        if (call.details?.state != Call.STATE_RINGING) {
            openScreen()
        } else if (deviceLockedOrOff()) {
            // Ringing on a locked or sleeping phone: go straight to the full-screen call screen
            // (the full-screen notification does this too; this is a backup if it's blocked).
            openScreen()
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callback)
        sync()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallManager.setAudio(audioState)
    }

    private var foreground = false

    private fun sync() {
        CallManager.update(this, calls.toList())
        val list = CallManager.calls.value
        val n = CallNotifier.build(this, list)
        if (n == null) {
            if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
            CallNotifier.cancelAll(this)
            return
        }
        try {
            // A phoneCall foreground service is what gives the status-bar call chip and keeps
            // the call alive while bitDialer is in the background.
            startForeground(CallNotifier.NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
            if (!foreground) Diag.log(this, "call notification shown (full-screen allowed: ${CallNotifier.fullScreenAllowed(this)})")
            foreground = true
        } catch (e: Exception) {
            Diag.log(this, "foreground service failed: ${e.javaClass.simpleName}: ${e.message}")
            CallNotifier.buildPlain(this, list)?.let { CallNotifier.post(this, it) }
        }
    }

    private fun deviceLockedOrOff(): Boolean {
        val km = getSystemService(android.app.KeyguardManager::class.java)
        val pm = getSystemService(android.os.PowerManager::class.java)
        return km?.isKeyguardLocked == true || pm?.isInteractive == false
    }

    private fun openScreen() {
        Diag.log(this, "opening call screen (locked/off: ${deviceLockedOrOff()})")
        try {
            startActivity(
                Intent(this, InCallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            )
        } catch (e: Exception) {
            Diag.log(this, "couldn't open call screen: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
