package com.bitdroid.dialer.call

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * Telecom binds this while bitDialer is the default phone app and hands it every call.
 * It keeps [CallManager] in sync and shows the call screen or the incoming-call notification.
 */
class CallService : InCallService() {
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

    private fun sync() {
        CallManager.update(this, calls.toList())
        CallNotifier.refresh(this, CallManager.calls.value)
    }

    private fun openScreen() {
        try {
            startActivity(
                Intent(this, InCallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            )
        } catch (_: Exception) {
        }
    }
}
