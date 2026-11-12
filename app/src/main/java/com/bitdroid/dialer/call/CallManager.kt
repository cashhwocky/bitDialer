package com.bitdroid.dialer.call

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import com.bitdroid.dialer.data.Contact
import com.bitdroid.dialer.data.Contacts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One live call as the UI sees it. */
data class UiCall(
    val call: Call,
    val number: String,
    val contact: Contact,
    val state: Int,
    val connectTime: Long,
    val disconnectLabel: String?,
) {
    val ringing get() = state == Call.STATE_RINGING
    val active get() = state == Call.STATE_ACTIVE
    val holding get() = state == Call.STATE_HOLDING
    val dialing get() = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_NEW
    val selectingSim get() = state == Call.STATE_SELECT_PHONE_ACCOUNT
    val ended get() = state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING
}

/** Shared state between [CallService] (owned by Telecom) and the in-call screen. */
object CallManager {
    @Volatile
    var service: CallService? = null

    private val _calls = MutableStateFlow<List<UiCall>>(emptyList())
    val calls: StateFlow<List<UiCall>> = _calls

    private val _audio = MutableStateFlow<CallAudioState?>(null)
    val audio: StateFlow<CallAudioState?> = _audio

    fun setAudio(state: CallAudioState?) {
        _audio.value = state
    }

    fun numberOf(call: Call): String =
        call.details?.handle?.schemeSpecificPart
            ?: call.details?.gatewayInfo?.originalAddress?.schemeSpecificPart
            ?: ""

    fun update(context: Context, list: List<Call>) {
        _calls.value = list.map { c ->
            val number = numberOf(c)
            val name = c.details?.contactDisplayName?.toString()?.takeIf { it.isNotBlank() }
                ?: c.details?.callerDisplayName?.takeIf { it.isNotBlank() }
            val looked = Contacts.lookup(context, number)
            UiCall(
                call = c,
                number = number,
                contact = if (looked.name == null && name != null) looked.copy(name = name) else looked,
                state = c.details?.state ?: Call.STATE_NEW,
                connectTime = c.details?.connectTimeMillis ?: 0L,
                disconnectLabel = c.details?.disconnectCause?.label?.toString(),
            )
        }
    }

    /** The call the screen should be about: a ringing one first, then live, then anything. */
    fun primary(list: List<UiCall> = calls.value): UiCall? =
        list.firstOrNull { it.ringing }
            ?: list.firstOrNull { it.active || it.dialing || it.selectingSim }
            ?: list.firstOrNull { it.holding }
            ?: list.firstOrNull()

    fun answer(c: UiCall) {
        service?.let { CallVibrator.stop(it) }
        c.call.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun decline(c: UiCall) {
        service?.let { CallVibrator.stop(it) }
        c.call.reject(false, null)
    }
    fun hangup(c: UiCall) = c.call.disconnect()

    fun toggleHold(c: UiCall) {
        if (c.holding) c.call.unhold() else c.call.hold()
    }

    fun setMuted(on: Boolean) = service?.setMuted(on)

    fun setSpeaker(on: Boolean) {
        val a = _audio.value
        val route = when {
            on -> CallAudioState.ROUTE_SPEAKER
            a != null && a.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0 && a.route == CallAudioState.ROUTE_SPEAKER -> CallAudioState.ROUTE_WIRED_OR_EARPIECE
            else -> CallAudioState.ROUTE_WIRED_OR_EARPIECE
        }
        service?.setAudioRoute(route)
    }

    fun dtmf(c: UiCall, digit: Char) {
        c.call.playDtmfTone(digit)
        c.call.stopDtmfTone()
    }

    fun chooseSim(c: UiCall, handle: PhoneAccountHandle) = c.call.phoneAccountSelected(handle, false)

    fun swap(list: List<UiCall>) {
        list.firstOrNull { it.holding }?.call?.unhold()
    }

    fun merge(list: List<UiCall>) {
        val active = list.firstOrNull { it.active } ?: return
        val other = list.firstOrNull { it !== active && it.holding } ?: return
        active.call.conference(other.call)
    }

    fun silenceRinger(context: Context) {
        CallVibrator.stop(context)
        try {
            context.getSystemService(TelecomManager::class.java)?.silenceRinger()
        } catch (_: SecurityException) {
        }
    }

    /** Places a call through Telecom; it comes back to us via [CallService]. */
    fun placeCall(context: Context, number: String, account: PhoneAccountHandle? = null): Boolean {
        val tm = context.getSystemService(TelecomManager::class.java) ?: return false
        val extras = Bundle()
        account?.let { extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
        return try {
            val uri = if (number.startsWith("voicemail:")) Uri.parse(number) else Uri.fromParts("tel", number, null)
            tm.placeCall(uri, extras)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
