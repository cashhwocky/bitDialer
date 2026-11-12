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
    }

    override fun onDestroy() {
        if (CallManager.service === this) CallManager.service = null
        CallNotifier.cancelAll(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.i("bitDialer", "call added, state=${call.details?.state}")
        call.registerCallback(callback)
        sync()
        if (call.details?.state != Call.STATE_RINGING) openScreen()
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
            foreground = true
        } catch (e: Exception) {
            Log.e("bitDialer", "startForeground failed, posting plain notification", e)
            CallNotifier.buildPlain(this, list)?.let { CallNotifier.post(this, it) }
        }
    }

    private fun openScreen() {
        try {
            startActivity(
                Intent(this, InCallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            )
        } catch (e: Exception) {
            Log.e("bitDialer", "couldn't open call screen", e)
        }
    }
}
